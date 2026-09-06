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
| `docs/` | Specs, planos por marco (`PLANO-M*.md`), decisões (`ADR-*.md`), referência e manuais (tabela abaixo) |

## Documentos

| Para quem | Documento |
|---|---|
| Quem opera a plataforma | [`docs/MANUAL-ADMIN.md`](docs/MANUAL-ADMIN.md) |
| Revendedores | [`docs/MANUAL-REVENDA.md`](docs/MANUAL-REVENDA.md) |
| Cliente final (TV/celular) | [`docs/MANUAL-APP.md`](docs/MANUAL-APP.md) |
| Quem implanta ou mantém o servidor | [`docs/DEPLOY.md`](docs/DEPLOY.md), [`docs/SECURITY-REVIEW.md`](docs/SECURITY-REVIEW.md) |
| Quem integra com a API | [`docs/API.md`](docs/API.md) |
| Quem compila o app | [`docs/ANDROID.md`](docs/ANDROID.md) |
| Homologação e entrega | [`docs/HOMOLOGACAO.md`](docs/HOMOLOGACAO.md), [`docs/M5-ISSUES.md`](docs/M5-ISSUES.md), [`docs/FASE-2.md`](docs/FASE-2.md) |
| Escopo contratual e decisões | [`docs/Anexo-I-Especificacao-Funcional.md`](docs/Anexo-I-Especificacao-Funcional.md), `docs/ADR-00*.md` |

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

Login padrão do seed: admin `admin` / senha do `ADMIN_PASSWORD` em `/admin`; revenda de teste `revenda` / `revenda123` em `/painel`.

## Produção

Primeira instalação: `cd deploy && cp .env.example .env && docker compose up -d --build`.
Atualizações a partir da máquina de desenvolvimento: `./deploy/deploy.sh` (e
`./deploy/deploy.sh --apk <apk>` para publicar um novo release do app em `/downloads/app.apk`).
Detalhes, backup, monitoramento e checklist de segurança em [`docs/DEPLOY.md`](docs/DEPLOY.md).

## Testes

```bash
cd backend && make test          # pytest + cobertura mínima de 80 %
cd backend && make lint          # ruff
cd android && ./gradlew :app:testDebugUnitTest :app:lintDebug
cd backend && uv run python scripts/loadtest_device_config.py --help   # teste de carga (ver SECURITY-REVIEW.md)
```
