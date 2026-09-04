# Plataforma de Player IPTV White-Label

Monorepo com backend (FastAPI), painel web (SvelteKit: `/admin` e `/painel`), app Android e
arquivos de implantação. Regras do projeto em [`CLAUDE.md`](CLAUDE.md); escopo contratual em
[`docs/Anexo-I-Especificacao-Funcional.md`](docs/Anexo-I-Especificacao-Funcional.md).

| Pasta | Conteúdo |
|---|---|
| `backend/` | API REST (`/api/v1`), modelos, migrações Alembic, testes (`make test`) |
| `web/` | Painel admin e dashboard da revenda (SvelteKit + Tailwind) |
| `android/` | App player (M3+) |
| `deploy/` | `docker-compose.yml`, `Caddyfile`, `.env.example` |
| `docs/` | Specs, planos por marco (`PLANO-M*.md`), decisões (`ADR-*.md`), [`API.md`](docs/API.md), [`DEPLOY.md`](docs/DEPLOY.md) |

## Início rápido (desenvolvimento)

Pré-requisitos: Python 3.12 + [uv](https://docs.astral.sh/uv/), Node 22, PostgreSQL 16 e Redis
(veja `docs/DEPLOY.md` §7 para o setup sem Docker).

```bash
# backend
cd backend && cp ../deploy/.env.example .env   # ajuste DATABASE_URL/REDIS_URL/SECRET_KEY/FERNET_KEY
make install && make migrate && make seed && make dev      # http://localhost:8000/api/docs

# web (outro terminal)
cd web && npm install && npm run dev                        # http://localhost:5173/admin
```

Login padrão do seed: admin `admin` / senha do `ADMIN_PASSWORD`; revenda de teste `revenda` / `revenda123`.

## Produção

`cd deploy && cp .env.example .env && docker compose up -d --build` — detalhes em
[`docs/DEPLOY.md`](docs/DEPLOY.md).
