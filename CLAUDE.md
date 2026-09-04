# Plataforma de Player IPTV White-Label — Guia do Projeto

## O que é
Plataforma composta por: **backend/API**, **painel admin** (dono da plataforma), **dashboard do revendedor** e **app player Android/Android TV**. Referência funcional: `docs/spec-painel.md`, `docs/app/spec-app.md` e o escopo fechado em `docs/Anexo-I-Especificacao-Funcional.md`. O anexo define o que entra na v1 — **não implemente nada da Seção 3 (fora do escopo) sem eu pedir**.

A plataforma é neutra: gerencia dispositivos, playlists e personalização. Nunca hospeda, lista ou intermedia conteúdo.

## Stack (decidida — não trocar)
- **Backend:** Python 3.12, FastAPI, SQLAlchemy 2 (async), Alembic, Pydantic v2, PostgreSQL 16, Redis (cache/rate limit)
- **Frontend (admin + dashboard):** SvelteKit + TypeScript + Tailwind, uma única aplicação com rotas `/admin` e `/painel`, autenticação por cookie httpOnly com JWT
- **App:** Kotlin, Jetpack Media3 (ExoPlayer), AndroidX Leanback para TV, Room, Retrofit + Moshi, Coil. Fallback de player: libVLC. Um único módulo com duas activities de entrada (mobile e TV)
- **Infra:** Docker Compose (api, web, db, redis, caddy). Deploy no VPS por `docker compose up -d`
- **Pagamento:** Pix via Mercado Pago (webhook). Abstrair em `services/payments/` para trocar de provedor

## Estrutura do monorepo
```
/backend       FastAPI  (app/api, app/models, app/schemas, app/services, app/core, alembic/)
/web           SvelteKit (src/routes/admin, src/routes/painel, src/lib)
/android       projeto Gradle (app/)
/docs          specs, anexo, decisões (ADR-NNN.md)
/deploy        docker-compose.yml, Caddyfile, .env.example
```

## Regras de código
- API REST em JSON, versionada em `/api/v1`. Nunca reproduzir a ofuscação do app original (envelope Base64/AES): TLS + JSON limpo + token
- Toda rota com dependência de auth explícita. Três atores: `admin`, `reseller`, `device`. Um device só enxerga o próprio registro; um reseller só enxerga os próprios devices
- O identificador de revendedor **sempre vem da sessão**, nunca do corpo da requisição
- Senhas com argon2. Tokens de device opacos (random 32 bytes) armazenados como hash
- Exclusão sempre por `DELETE`/`POST`, nunca `GET`
- Preços e regras de crédito **só no servidor**
- Toda operação de crédito, vencimento, migração de DNS e pagamento gera linha em `audit_log`
- Migrações Alembic para qualquer mudança de schema; nunca `create_all` em produção
- Testes com pytest + httpx para toda rota; rodar `make test` antes de dar uma tarefa por concluída
- Mensagens de UI e erros de API voltados ao usuário em **português**. Código, comentários e commits em inglês
- Commits pequenos, mensagem `tipo(escopo): descrição` (feat, fix, chore, docs, test)

## Modelo de dados (núcleo — detalhar em `docs/ADR-001-data-model.md`)
- `admins`
- `resellers` (username, name, password_hash, credits, expires_at, is_blocked, logo_url, bg_url, qr_content, theme, auto_ads)
- `devices` (reseller_id, mac_address, device_id ANDROID_ID hash, client_name, token_hash, license_expires_at, last_seen_at)
- `playlists` (device_id, name, url, type xtream|m3u, host, username, password_enc, is_protected)
- `banners` (reseller_id, title, url, is_active)
- `payments` (reseller_id, provider, provider_id, months, amount, status, qr_code, qr_base64, paid_at)
- `credit_ledger` (reseller_id, delta, reason, ref)
- `audit_log` (actor_type, actor_id, action, target, payload, created_at)
- `settings` (chave/valor global: preço mensal, pacotes, versão mínima do app, apk_url)

## Como trabalhar
- Antes de começar uma tarefa, leia o plano do marco atual em `docs/PLANO-M*.md` e marque itens concluídos com `[x]`
- Se uma decisão de arquitetura não estiver coberta aqui, escreva um `docs/ADR-NNN-titulo.md` curto (contexto, decisão, consequências) e siga
- Não instale dependências fora das listadas sem justificar no ADR
- Ao terminar cada tarefa: testes verdes, migração aplicada, plano atualizado, commit
- Nunca comitar `.env`, chaves de gateway ou senhas de teste
