"""Generate docs/API.md from the FastAPI OpenAPI schema.

    uv run python scripts/gen_api_docs.py            # writes ../docs/API.md
    uv run python scripts/gen_api_docs.py --check    # exits 1 when the file is stale

Examples are derived from the JSON schema plus the curated values below, so the
document stays in sync with the code.
"""

from __future__ import annotations

import json
import os
import sys
from typing import Any

os.environ.setdefault("APP_ENV", "development")
os.environ.setdefault("SECRET_KEY", "docs-generation-secret-key-0123456789")
os.environ.setdefault("FERNET_KEY", "b1oJ7MZ0HSNd1jU7GdvUpcs7f2FZlNPlCzEDGB1tsQE=")

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app.main import app  # noqa: E402

OUT = os.path.join(
    os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))), "docs", "API.md"
)

# Curated example values by field name (fallback: derived from the schema type).
EXAMPLES: dict[str, Any] = {
    "id": 12,
    "username": "revenda01",
    "password": "senha-forte-123",
    "name": "Revenda 01",
    "credits": 10,
    "delta": 5,
    "note": "Compra de pacote de 5 créditos",
    "expires_at": "2027-01-31",
    "is_blocked": False,
    "logo_url": "https://cdn.exemplo.com/logo.png",
    "bg_url": "https://cdn.exemplo.com/fundo.jpg",
    "qr_content": "https://wa.me/5511999999999",
    "theme": "theme_d",
    "auto_ads": False,
    "devices_count": 3,
    "created_at": "2026-09-04T12:00:00Z",
    "updated_at": "2026-09-04T12:00:00Z",
    "paid_at": "2026-09-04T12:05:00Z",
    "last_seen_at": "2026-09-04T12:00:00Z",
    "device_id": "9774d56d682e549c",
    "app_type": "tv",
    "app_version": "1.0.0",
    "mac_address": "02:50:50:A1:B2:C3",
    "token": "f3a9c1…(64 hex)",
    "csrf_token": "Y2xpZW50LWNzcmYtdG9rZW4",
    "registered": True,
    "status": "active",
    "client_name": "João Silva",
    "license_expires_at": None,
    "pin": "0000",
    "min_app_version": "1.0.0",
    "apk_url": "https://cdn.exemplo.com/app.apk",
    "platform_name": "Minha Plataforma",
    "url": "http://servidor.exemplo.com/get.php?username=u1&password=p1&type=m3u_plus",
    "type": "xtream",
    "is_protected": False,
    "title": "Promoção de setembro",
    "months": 3,
    "price": "100.00",
    "amount": "105.00",
    "monthly_price": "35.00",
    "provider": "mercadopago",
    "provider_id": "123456789",
    "reseller_id": 12,
    "reseller_username": "revenda01",
    "previous_expires_at": "2026-10-01",
    "new_expires_at": "2027-01-01",
    "balance_after": 15,
    "reason": "admin_adjustment",
    "ref": None,
    "actor_type": "admin",
    "actor_id": 1,
    "action": "credits.adjust",
    "target": "reseller:12",
    "payload": {"delta": 5, "balance_after": 15},
    "ip": "203.0.113.10",
    "message": "Operação realizada.",
    "role": "admin",
    "total": 1,
    "page": 1,
    "per_page": 25,
    "qr_code": "00020126…",
    "qr_base64": "iVBORw0KGgo…",
    "database": "ok",
    "redis": "ok",
}


def resolve(schema: dict[str, Any], components: dict[str, Any]) -> dict[str, Any]:
    if "$ref" in schema:
        name = schema["$ref"].split("/")[-1]
        return resolve(components[name], components)
    return schema


def example_for(
    name: str | None, schema: dict[str, Any], components: dict[str, Any], depth: int = 0
) -> Any:
    schema = resolve(schema, components)
    if name in EXAMPLES:
        return EXAMPLES[name]
    if "default" in schema and name not in ("items",):
        return schema["default"]
    if "enum" in schema:
        return schema["enum"][0]
    if "anyOf" in schema:
        options = [o for o in schema["anyOf"] if o.get("type") != "null"]
        return example_for(name, options[0], components, depth) if options else None
    if "allOf" in schema:
        return example_for(name, schema["allOf"][0], components, depth)
    t = schema.get("type")
    if t == "object" or "properties" in schema:
        if depth > 4:
            return {}
        return {
            key: example_for(key, prop, components, depth + 1)
            for key, prop in schema.get("properties", {}).items()
        }
    if t == "array":
        return [example_for(name, schema.get("items", {}), components, depth + 1)]
    if t == "integer":
        return 1
    if t == "number":
        return 1.0
    if t == "boolean":
        return True
    if t == "string":
        fmt = schema.get("format")
        if fmt == "date":
            return "2026-12-31"
        if fmt == "date-time":
            return "2026-09-04T12:00:00Z"
        return "texto"
    return None


def fence(obj: Any) -> str:
    return "```json\n" + json.dumps(obj, ensure_ascii=False, indent=2) + "\n```"


AUTH_NOTES = {
    "admin": "Cookie `admin_session` (login do admin) + header `X-CSRF-Token` em métodos que alteram dados.",
    "reseller": "Cookie `reseller_session` (login da revenda) + header `X-CSRF-Token` em métodos que alteram dados.",
    "device": "Header `Authorization: Bearer <token>` (token devolvido pelo `POST /device/register`).",
    "public": "Sem autenticação.",
}


def auth_kind(path: str) -> str:
    if path.startswith("/api/v1/admin/"):
        return "admin"
    if path.startswith("/api/v1/painel/") or path.startswith("/api/v1/reseller/"):
        return "reseller"
    if path.startswith("/api/v1/device/") and not path.endswith("/register"):
        return "device"
    return "public"


def render() -> str:
    spec = app.openapi()
    components = spec.get("components", {}).get("schemas", {})
    lines: list[str] = []
    lines.append("# API REST — referência\n")
    lines.append(
        "_Gerado automaticamente por `backend/scripts/gen_api_docs.py` a partir do OpenAPI. "
        "Não edite à mão; rode `make docs` no diretório `backend`._\n"
    )
    lines.append(
        "Base: `/api/v1`. Todas as respostas são JSON. Documentação interativa (fora de produção): `/api/docs`.\n"
    )
    lines.append("## Autenticação\n")
    lines.append("| Ator | Como autenticar |\n|---|---|")
    lines.append(
        "| Admin | `POST /auth/admin/login` grava cookie httpOnly `admin_session` (JWT) e cookie `csrf_token`. Mutations exigem header `X-CSRF-Token` igual ao cookie. |"
    )
    lines.append(
        "| Revenda | `POST /auth/reseller/login` grava cookie httpOnly `reseller_session` + `csrf_token`. Revenda bloqueada ou vencida recebe **403**. |"
    )
    lines.append(
        "| Dispositivo | `POST /device/register` devolve um token opaco (mostrado uma única vez). Enviar em `Authorization: Bearer <token>`. |\n"
    )
    lines.append("## Erros\n")
    lines.append(
        'Erros seguem o formato `{"detail": {"message": "<texto em português>", "code": "<código>"}}`. '
        "Erros de validação (422) seguem o formato padrão do FastAPI (`detail` é uma lista).\n"
    )
    lines.append("| Status | Significado |\n|---|---|")
    lines.append(
        "| 400 | Regra de negócio violada (ex.: `insufficient_credits`, `invalid_playlist_url`) |"
    )
    lines.append("| 401 | Não autenticado / token inválido |")
    lines.append(
        "| 403 | Sem permissão, CSRF inválido, revenda bloqueada/vencida, dispositivo não cadastrado |"
    )
    lines.append("| 404 | Registro não encontrado |")
    lines.append("| 409 | Conflito (ex.: `username_taken`) |")
    lines.append("| 422 | Corpo/parâmetros inválidos |")
    lines.append("| 429 | Muitas tentativas de login (`rate_limited`) |\n")
    lines.append("## Paginação\n")
    lines.append(
        "Listagens aceitam `page` (≥1), `per_page` (1–100, padrão 25) e `search`, e devolvem "
        "`{items, total, page, per_page}`.\n"
    )

    by_tag: dict[str, list[tuple[str, str, dict[str, Any]]]] = {}
    for path, methods in spec["paths"].items():
        for method, op in methods.items():
            tag = (op.get("tags") or ["outros"])[0]
            by_tag.setdefault(tag, []).append((method.upper(), path, op))

    lines.append("## Rotas\n")
    for tag, ops in by_tag.items():
        lines.append(f"### {tag}\n")
        for method, path, op in ops:
            lines.append(f"#### `{method} {path}`\n")
            if op.get("summary"):
                lines.append(op["summary"] + "\n")
            if op.get("description"):
                lines.append(op["description"].strip() + "\n")
            lines.append(f"**Autenticação:** {AUTH_NOTES[auth_kind(path)]}\n")
            params = op.get("parameters", [])
            if params:
                lines.append("**Parâmetros**\n")
                lines.append("| Nome | Em | Obrigatório | Tipo |\n|---|---|---|---|")
                for p in params:
                    sch = p.get("schema", {})
                    t = (
                        sch.get("type")
                        or "/".join(
                            o.get("type", "?")
                            for o in sch.get("anyOf", [])
                            if o.get("type") != "null"
                        )
                        or "string"
                    )
                    if "enum" in sch:
                        t += f" ({', '.join(map(str, sch['enum']))})"
                    lines.append(
                        f"| `{p['name']}` | {p['in']} | {'sim' if p.get('required') else 'não'} | {t} |"
                    )
                lines.append("")
            body = (
                op.get("requestBody", {})
                .get("content", {})
                .get("application/json", {})
                .get("schema")
            )
            if body:
                lines.append("**Request**\n")
                lines.append(fence(example_for(None, body, components)) + "\n")
            responses = op.get("responses", {})
            for code, resp in responses.items():
                if code == "422":
                    continue
                schema = resp.get("content", {}).get("application/json", {}).get("schema")
                lines.append(f"**Response {code}**\n")
                if schema:
                    lines.append(fence(example_for(None, schema, components)) + "\n")
                else:
                    lines.append("_sem corpo_\n")
    return "\n".join(lines).rstrip() + "\n"


def main() -> None:
    content = render()
    if "--check" in sys.argv:
        current = open(OUT, encoding="utf-8").read() if os.path.exists(OUT) else ""
        if current != content:
            print("docs/API.md is stale; run `make docs`")
            sys.exit(1)
        print("docs/API.md up to date")
        return
    with open(OUT, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(content)
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main()
