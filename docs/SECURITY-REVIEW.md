# Revisão de segurança e carga — M5 (06/09/2026)

Escopo: todas as rotas de `backend/app/api/v1`, os serviços que elas chamam, o proxy (Caddy)
e a operação do VPS. Método: leitura rota a rota conferindo (1) dependência de autenticação,
(2) escopo por revenda/dispositivo, (3) validação de entrada, (4) efeitos colaterais auditados.
Achados e correções estão numerados em [`M5-ISSUES.md`](M5-ISSUES.md).

## 1. Modelo de autenticação (referência: ADR-002)

| Ator | Credencial | Transporte | Proteções |
|---|---|---|---|
| admin | usuário + senha (argon2) | cookie `admin_session` httpOnly + JWT HS256, 12 h | CSRF double-submit (`csrf_token` cookie = header `X-CSRF-Token`) em métodos não seguros; rate limit de login por IP+usuário (10 / 5 min) |
| reseller | idem, cookie `reseller_session` | idem | idem; `is_blocked` e vencimento conferidos **a cada requisição** (`current_reseller`); rotas de renovação/perfil aceitam revenda vencida (`current_reseller_allow_expired`), nunca bloqueada |
| device | token opaco de 32 bytes, guardado como SHA-256 | `Authorization: Bearer` | rotaciona a cada `register`; `device_id` (ANDROID_ID) é armazenado como hash; rate limit por IP e por dispositivo (M5-002) |

O identificador da revenda vem **sempre** da sessão; nenhuma rota aceita `reseller_id` no
corpo. Sessões admin e reseller usam cookies distintos, então um navegador pode ter as duas
sem que uma escale para a outra (`/auth/me` confere o papel dentro do JWT).

## 2. Rotas

Legenda: **Auth** = dependência usada; **Escopo** = como o acesso é restrito ao dono;
**Entrada** = validação relevante; **Aud.** = grava `audit_log`.

### Públicas

| Rota | Auth | Escopo / Entrada | Aud. | Observação |
|---|---|---|---|---|
| `GET /health` | — | — | — | Expõe só `ok/degraded` por componente |
| `POST /auth/admin/login`, `POST /auth/reseller/login` | — | `LoginRequest` (≤ 64 / ≤ 128); rate limit por IP+usuário; revenda bloqueada recebe 403 | — | Falhas de senha não distinguem usuário inexistente |
| `POST /auth/logout` | — | apaga os três cookies | — | |
| `GET /auth/me` | cookie | papel conferido no JWT | — | |
| `POST /device/register` | — | `device_id` 4–256, `app_type` enum, `app_version` ≤ 32; **rate limit** (M5-002) | — | Cria linha em `devices`; MAC gerado no prefixo local, colisão tratada |
| `POST /webhooks/mercadopago` | assinatura HMAC (`x-signature`) | só `type=payment`; pagamento **sempre** reconsultado no gateway antes de aprovar | sim (via `sync_payment`) | Sem `MERCADOPAGO_WEBHOOK_SECRET` a assinatura não é conferida — em produção o segredo está configurado (DEPLOY §11.8) |

### Dispositivo (`Bearer`)

| Rota | Auth | Escopo / Entrada | Aud. |
|---|---|---|---|
| `GET /device/config` | `device_with_rate_limit` | só o próprio registro; playlists e banners só quando `status=active`; devolve `pin` do próprio aparelho | — (atualiza `last_seen_at`) |
| `POST /device/playlists` | `CurrentDevice` | exige `is_registered`; ≤ 20 playlists; URL http(s) validada; senha Xtream cifrada (Fernet) | sim |
| `DELETE /device/playlists/{id}` | `CurrentDevice` | `WHERE device_id = device.id` | sim |

### Revenda (cookie `reseller_session` + CSRF)

| Rota | Escopo / Entrada | Aud. |
|---|---|---|
| `GET/POST /reseller/devices`, `GET/PUT/DELETE /reseller/devices/{id}`, `POST …/batch-delete` | toda query filtra `Device.reseller_id = reseller.id` (`get_own_device` → 404 para id alheio); MAC normalizado e validado; MAC de outra revenda → 409 `mac_taken`; MAC avulso (app já aberto) é reivindicado — comportamento previsto; débito de crédito atômico com o cadastro; batch ≤ 500 ids | create/update/delete: sim |
| `GET /reseller/dns`, `POST /reseller/dns/migrate` | join por `Device.reseller_id`; hosts normalizados; origem = destino → 422 | sim |
| `GET/PUT /reseller/branding` | altera só o próprio registro; `logo_url`/`bg_url` **http(s) apenas** (M5-003); `theme` enum | sim |
| `POST /reseller/branding/upload` | 2 MB, tipo por *magic bytes* (PNG/JPG/WebP), nome aleatório, servido pelo Caddy como arquivo estático | sim |
| `GET/POST/PATCH/DELETE /reseller/branding/banners` | `_own_banner` (404 para id alheio); ≤ 10; URL http(s) | sim |
| `GET /reseller/profile`, `PUT /reseller/profile/password` | senha atual obrigatória; nova ≥ 6 | sim |
| `GET /reseller/billing/plans`, `POST …/pix`, `GET …/pix/{id}`, `GET …/history` | `Payment.reseller_id = reseller.id`; `months` 1–`MAX_MONTHS` **ou** `package_id` (preço vem das configurações, nunca do corpo); revenda vencida pode renovar, bloqueada não | pix: sim |

### Admin (cookie `admin_session` + CSRF)

| Rota | Escopo / Entrada | Aud. |
|---|---|---|
| `GET /admin/dashboard`, `GET /admin/payments`, `GET /admin/audit-log` | somente leitura; filtros tipados (`Literal`, `date`); `search` ≤ 120 | — |
| `GET/POST /admin/resellers`, `GET/PATCH/DELETE …/{id}` | usuário `^[a-zA-Z0-9_.-]+$` 3–64, senha ≥ 6; `username` único (409); tema enum; `logo_url`/`bg_url` http(s) (M5-003) | create/update/delete: sim |
| `POST …/{id}/block`, `POST …/{id}/password`, `POST …/{id}/credits`, `PATCH …/{id}/expiration` | ajuste de crédito exige motivo (3–500) e créditos ativos; grava `credit_ledger` | sim |
| `GET …/{id}/devices`, `GET …/{id}/credits`, `GET …/{id}/payments` | leitura; **URL da playlist omitida** para o admin | — |
| `GET/PUT /admin/settings` | `min_app_version` `^\d+(\.\d+){0,3}$`; preços `Decimal(10,2) > 0`; pacotes 1–60 meses | sim |
| `GET /admin/settings/gateway` | token mascarado, nunca completo | — |

Nenhuma rota faz exclusão por `GET`. Nenhuma rota devolve `password_hash`, `token_hash`,
`password_enc` ou o token do gateway (conferido nos schemas `*Out`).

## 3. Achados

| Id | Gravidade | Situação |
|---|---|---|
| M5-001 `openapi.json` em produção | baixa | corrigida |
| M5-002 endpoints públicos do app sem rate limit | média | corrigida (por IP e por dispositivo, `Retry-After`) |
| M5-003 `logo_url`/`bg_url` com esquema livre | baixa | corrigida |
| M5-004 JWT sem revogação ao trocar senha | baixa | aceita (bloqueio é imediato; expiração 12 h) |
| M5-005 dispositivos ficam órfãos ao excluir revenda | baixa | aceita (intencional, documentado) |
| M5-006 cabeçalhos de segurança / limite de corpo no proxy | média | corrigida |

Pontos conferidos e **sem achado**: injeção SQL (só SQLAlchemy parametrizado; `ilike` com
padrão do usuário não é injeção); SSRF (o servidor nunca busca URLs de playlist/banner, só as
armazena); *path traversal* em uploads (nome gerado, extensão pelo conteúdo); *mass assignment*
(`model_dump(exclude_unset=True)` sobre schemas fechados); enumeração de usuários no login;
exposição de segredos em logs (o cliente Xtream do app redige credenciais; a API não loga
corpos); IDOR nas rotas por id (todas filtram pelo dono); CORS (só origens listadas,
vazio em produção porque painel e API compartilham o domínio); `X-Forwarded-For` (a API só é
alcançável pelo Caddy — `--forwarded-allow-ips=*` é seguro nesse desenho, mas não exponha a
porta 8000 do container).

## 4. Teste de carga — `GET /device/config`

Cenário do plano: 2.000 dispositivos fazendo `config` em 60 s (≈ 33 req/s, um *boot* por
aparelho), 50 conexões simultâneas no máximo. Ferramenta:
`backend/scripts/loadtest_device_config.py` (httpx assíncrono, sem dependência nova), dados
de `backend/scripts/seed_load_devices.py` (revenda `carga`, 2.000 devices com playlist e token).

Ambiente: máquina de desenvolvimento Windows, 1 worker uvicorn, PostgreSQL 16 e Redis locais
(o VPS de produção tem a mesma topologia de 1 worker; não há 2.000 tokens em produção para
repetir lá sem semear dados).

| Rodada | Requisições | Erros | p50 | p95 | p99 | máx |
|---|---|---|---|---|---|---|
| 2.000 em 60 s, concorrência 50 (máquina ociosa) | 2.000 | 0 | 12 ms | **16 ms** | 17 ms | 67 ms |
| 700 em 21 s, concorrência 10 | 700 | 0 | 12 ms | 16 ms | 17 ms | 41 ms |
| 700 em 21 s, concorrência 50 | 700 | 0 | 13 ms | 17 ms | 18 ms | 41 ms |
| 2.000 em 60 s com o `vite` compilando na mesma máquina | 2.000 | 0 | 14 ms | 618 ms | 8,1 s | 10 s |

Meta do plano: **p95 < 300 ms — atingida** (16 ms). A última linha ficou registrada como
lição: os picos vieram de outro processo saturando a CPU da máquina de teste, não da API — o
cenário não se repetiu em nenhuma rodada isolada. Perfil de `config`: 6 consultas (token,
device + playlists + reseller via `selectinload`, settings, banners, `UPDATE last_seen_at`),
todas por chave única ou índice (`token_hash`, `device_id`, `reseller_id`); nenhum N+1.
Nenhum ajuste de índice foi necessário. Pool de conexões: 10 + 20 de *overflow*, suficiente
para 50 simultâneos.

Como repetir:

```bash
cd backend
uv run python scripts/seed_load_devices.py --count 2000 --out /tmp/tokens.txt   # recusa em produção
DEVICE_RATE_LIMIT_IP=0 uv run uvicorn app.main:app --port 8002 &                # o teste sai de um único IP
uv run python scripts/loadtest_device_config.py --base http://127.0.0.1:8002 --tokens /tmp/tokens.txt --duration 60 --concurrency 50
uv run python scripts/seed_load_devices.py --wipe
```

## 5. Proxy e servidor

- Caddy: TLS automático, HTTP → HTTPS, `www` → apex; cabeçalhos HSTS (1 ano, subdomínios),
  `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy`,
  `Permissions-Policy`, CSP no painel (`default-src 'self'`; imagens de qualquer origem
  http(s) porque banners/logos são URLs digitadas pela revenda; `frame-ancestors 'none'`);
  corpo limitado a 8 MB em `/api/*`; `Server` removido.
- Containers: `restart: unless-stopped`, healthchecks em `db`/`redis`/`api`, logs
  `json-file` 10 MB × 5, `logrotate` diário.
- Host: SSH só por chave, `ufw` 22/80/443, `fail2ban`, `unattended-upgrades` (DEPLOY §11).
- Backup diário com retenção de 7 dias; restauração ensaiada em banco temporário (DEPLOY §6).
- Monitor de health a cada 5 min com alerta Telegram/e-mail (DEPLOY §5).

## 6. Recomendações fora desta entrega (fase 2)

- Revogação de sessão por versão de senha (M5-004).
- Segundo worker uvicorn ou `--workers 2` quando a base passar de alguns milhares de
  aparelhos; o teste mostra folga ampla até lá.
- Cópia dos backups para fora do VPS (rclone/S3).
