# Marco 1 — Backend + API + Painel Admin

Execute em ordem. Cada bloco termina com testes verdes e commit. Não avance para o M2.

## 1. Bootstrap
- [x] Criar estrutura do monorepo conforme `CLAUDE.md`
- [x] `/deploy/docker-compose.yml` com api, web, db (postgres:16), redis, caddy; `.env.example` com todas as variáveis
- [x] `/backend`: pyproject (uv), FastAPI app factory, config via pydantic-settings, SQLAlchemy async engine, Alembic configurado, `Makefile` com `dev`, `test`, `migrate`, `lint` (ruff)
- [x] Healthcheck `GET /api/v1/health`
- [x] `docs/ADR-001-data-model.md` com o modelo completo (tabelas, colunas, índices, relações) baseado no núcleo do `CLAUDE.md` e nas specs

## 2. Modelo + migrações
- [x] Models SQLAlchemy de todas as tabelas do ADR-001
- [x] Migração inicial Alembic
- [x] Seed de desenvolvimento: 1 admin (`admin`/senha do .env), 1 reseller de teste, settings padrão (preço mensal 35.00, pacotes vazios)

## 3. Autenticação
- [x] `POST /api/v1/auth/admin/login`, `POST /api/v1/auth/reseller/login` → JWT em cookie httpOnly + CSRF token
- [x] `POST /api/v1/auth/logout`
- [x] Dependências `current_admin`, `current_reseller`, `current_device` (Bearer token opaco)
- [x] Bloqueio de reseller com `is_blocked` ou `expires_at` vencido → 403 com mensagem em português
- [x] Rate limit em login (Redis)

## 4. API do dispositivo (é o que faz o app funcionar — prioridade)
- [x] `POST /api/v1/device/register` — body `{device_id, app_type, app_version}`; cria device sem reseller se não existir, gera MAC único no formato `XX:XX:XX:XX:XX:XX` (prefixo fixo da plataforma + 3 bytes aleatórios), devolve `{mac_address, token}`
- [x] `GET /api/v1/device/config` (Bearer) — devolve o equivalente ao `AppInfoModel` da spec-app §3.2 em JSON limpo: `registered, mac_address, status (active|expired|unregistered), license_expires_at, playlists[], theme, logo_url, bg_url, qr_content, banners[], pin, min_app_version, apk_url`
- [x] `POST /api/v1/device/playlists` e `DELETE /api/v1/device/playlists/{id}` — auto-cadastro de playlist pelo app (apenas se device registrado)
- [x] Parser de URL Xtream: extrair `host`, `username`, `password` de `get.php?username=&password=`; senha criptografada em repouso (Fernet com chave do .env)
- [x] Atualizar `last_seen_at` a cada `config`
- [x] Testes cobrindo: device novo, device cadastrado, expirado, reseller bloqueado, playlist add/delete

## 5. API do admin
- [ ] CRUD `resellers` (listar com busca/paginação, criar, editar, bloquear, resetar senha)
- [ ] `POST /resellers/{id}/credits` — ajuste manual com motivo → `credit_ledger` + `audit_log`
- [ ] `PATCH /resellers/{id}/expiration`
- [ ] `GET/PUT /settings` — preço mensal, pacotes promocionais `[{months, price}]`, versão mínima do app, apk_url
- [ ] `GET /dashboard` — totais: resellers, devices ativos, pagamentos do mês
- [ ] `GET /payments`, `GET /audit-log` com filtros e paginação

## 6. Painel admin (SvelteKit)
- [ ] Bootstrap `/web` com SvelteKit, Tailwind, layout com sidebar, tema claro/escuro persistido
- [ ] Login admin
- [ ] Telas: Dashboard, Revendedores (lista, criar, editar, créditos, vencimento, bloquear), Pagamentos, Auditoria, Configurações
- [ ] Componentes base reutilizáveis para o M2: tabela com busca/paginação, modal, toast, formulário
- [ ] Textos em português

## 7. Fechamento do M1
- [ ] `docs/API.md` gerado a partir do OpenAPI com exemplos de request/response para todas as rotas
- [ ] `docs/DEPLOY.md`: subir no VPS do zero, variáveis, backup do Postgres
- [ ] `docker compose up` do zero funciona e o admin loga
- [ ] Cobertura de testes das rotas ≥ 80%

## Ao concluir
Me apresente: lista do que foi entregue, o que ficou pendente e as decisões registradas em ADRs. Então aguarde para iniciar o M2 (`docs/PLANO-M2.md`, ainda a criar).
