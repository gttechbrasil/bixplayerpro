# Implantação

Guia para subir a plataforma em um VPS do zero, variáveis de ambiente, backup do PostgreSQL
e ambiente de desenvolvimento local.

## 1. Requisitos do VPS

- Linux x86-64 (Ubuntu 22.04/24.04 ou Debian 12), 2 vCPU, 2 GB RAM, 20 GB de disco
- Docker Engine 24+ e plugin Docker Compose v2 (`docker compose version`)
- Um domínio apontando para o IP do VPS (registro A). O Caddy emite o certificado TLS
  automaticamente pelo Let's Encrypt.
- Portas 80 e 443 liberadas no firewall

```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER && newgrp docker
```

## 2. Primeira instalação

```bash
git clone <repositório> /opt/iptv && cd /opt/iptv/deploy
cp .env.example .env
nano .env          # preencha conforme a tabela abaixo
docker compose up -d --build
docker compose logs -f api   # aguarde "Application startup complete"
```

Na subida, o container `api` roda `alembic upgrade head` (migrações) e o seed idempotente
(`python -m app.db.seed`), que cria o admin definido em `ADMIN_USERNAME`/`ADMIN_PASSWORD` e
as configurações padrão. Depois disso acesse `https://SEU_DOMINIO/admin` e faça login.

> Troque a senha do admin após o primeiro acesso alterando `ADMIN_PASSWORD` no `.env` **antes**
> do primeiro `up`, pois o seed só cria o usuário quando ele ainda não existe.

## 3. Variáveis de ambiente (`deploy/.env`)

| Variável | Obrigatória | Descrição |
|---|---|---|
| `DOMAIN` | sim | Domínio público (ex.: `player.exemplo.com.br`). Usado pelo Caddy para TLS |
| `PUBLIC_BASE_URL` | sim | `https://` + domínio. Usado em links gerados pela API |
| `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` | sim | Banco criado pelo container `db` |
| `DATABASE_URL` | local | URL completa (`postgresql+asyncpg://…`). No compose é montada automaticamente; só é lida quando a API roda fora do Docker |
| `REDIS_URL` | local | Idem, para o Redis |
| `SECRET_KEY` | sim | ≥ 32 caracteres. Assina os JWT das sessões. Gere com `python -c "import secrets; print(secrets.token_urlsafe(48))"` |
| `FERNET_KEY` | sim | Chave que cifra senhas de playlists Xtream em repouso. Gere com `python -c "from cryptography.fernet import Fernet; print(Fernet.generate_key().decode())"`. **Não troque depois de ter dados** |
| `JWT_EXPIRE_MINUTES` | não | Duração da sessão admin/revenda (padrão 720 = 12 h) |
| `COOKIE_SECURE` | sim | `true` em produção (HTTPS). `false` só para dev em HTTP |
| `CORS_ORIGINS` | não | Origens extras permitidas. Vazio quando painel e API estão no mesmo domínio (padrão com Caddy) |
| `ADMIN_USERNAME`, `ADMIN_PASSWORD` | sim | Primeiro administrador (criado pelo seed) |
| `PLATFORM_NAME` | sim | Nome exibido no painel e enviado ao app (também editável em Configurações) |
| `MAC_PREFIX` | não | 3 bytes iniciais dos MACs gerados (padrão `02:50:50`, faixa "localmente administrada") |
| `LOGIN_RATE_LIMIT`, `LOGIN_RATE_WINDOW` | não | Tentativas de login por IP+usuário e janela em segundos (padrão 10 / 300) |
| `PAYMENT_PROVIDER` | M2 | `mercadopago` |
| `MERCADOPAGO_ACCESS_TOKEN`, `MERCADOPAGO_WEBHOOK_SECRET` | M2 | Credenciais do Pix. Ficam **apenas** no `.env` |
| `APP_ENV` | sim | `production` no VPS (desativa `/api/docs` e o seed da revenda de teste) |
| `LOG_LEVEL` | não | `info` (padrão) ou `debug` |

Nunca comite o `.env`. Ele está no `.gitignore`.

## 4. Arquitetura dos containers

| Serviço | Imagem | Função |
|---|---|---|
| `caddy` | `caddy:2-alpine` | Porta 80/443, TLS automático, roteia `/api/*` para `api:8000` e o resto para `web:3000` |
| `api` | build de `backend/` | FastAPI + Uvicorn. Migra e semeia o banco ao iniciar |
| `web` | build de `web/` | SvelteKit (adapter-node). Renderiza no servidor chamando `api` pela rede interna |
| `db` | `postgres:16-alpine` | Dados em volume `db_data` |
| `redis` | `redis:7-alpine` | Rate limit de login / cache |

Somente o Caddy expõe portas. `db` e `redis` não são acessíveis de fora.

## 5. Operação

```bash
cd /opt/iptv/deploy
docker compose ps                       # status
docker compose logs -f api web caddy    # logs
docker compose restart api              # reiniciar um serviço
docker compose exec api python -m app.db.seed   # reexecutar o seed (idempotente)
```

### Atualizar a versão

```bash
cd /opt/iptv && git pull
cd deploy && docker compose up -d --build
```

As migrações pendentes são aplicadas automaticamente na subida da `api`.

## 6. Backup e restauração do PostgreSQL

Backup lógico diário (mantém 14 dias). Salve como `/opt/iptv/deploy/backup.sh`:

```bash
#!/usr/bin/env bash
set -euo pipefail
cd /opt/iptv/deploy
source .env
mkdir -p backups
FILE="backups/iptv-$(date +%F-%H%M).sql.gz"
docker compose exec -T db pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --no-owner | gzip > "$FILE"
find backups -name 'iptv-*.sql.gz' -mtime +14 -delete
echo "backup salvo em $FILE"
```

```bash
chmod +x backup.sh
(crontab -l 2>/dev/null; echo "30 3 * * * /opt/iptv/deploy/backup.sh >> /opt/iptv/deploy/backups/backup.log 2>&1") | crontab -
```

Copie a pasta `backups/` para fora do VPS (rclone, S3, etc.). Guarde também o `.env`:
sem `FERNET_KEY` as senhas de playlist não podem ser decifradas.

### Restaurar

```bash
cd /opt/iptv/deploy && source .env
docker compose stop api web
gunzip -c backups/iptv-AAAA-MM-DD-HHMM.sql.gz | docker compose exec -T db psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"
docker compose start api web
```

Para restaurar em um banco limpo, recrie o volume antes: `docker compose down -v db` e `docker compose up -d db`.

## 7. Desenvolvimento local sem Docker (Windows/Linux)

Ambiente usado durante o M1 nesta máquina:

1. **PostgreSQL 16** instalado nativamente (`winget install PostgreSQL.PostgreSQL.16` ou `apt install postgresql-16`). Crie o papel e os bancos:
   ```sql
   CREATE ROLE iptv LOGIN PASSWORD 'iptv';
   CREATE DATABASE iptv OWNER iptv;
   CREATE DATABASE iptv_test OWNER iptv;   -- usado pelos testes
   ```
2. **Redis 7**: no Windows, dentro do WSL (`apt install redis-server`) e iniciado com
   `redis-server --daemonize yes --bind 0.0.0.0 --protected-mode no`; no Linux, `apt install redis-server`.
3. **Backend** (`backend/`): copie `deploy/.env.example` para `backend/.env` ajustando `DATABASE_URL`,
   `REDIS_URL`, `COOKIE_SECURE=false`, `APP_ENV=development` e gere `SECRET_KEY`/`FERNET_KEY`.
   ```bash
   make install    # uv sync --all-groups
   make migrate    # alembic upgrade head
   make seed       # admin + revenda de teste (revenda / revenda123)
   make dev        # http://localhost:8000 (docs em /api/docs)
   make test       # pytest com cobertura mínima de 80 %
   make docs       # regenera docs/API.md
   ```
   Sem `make` no Windows, rode os comandos equivalentes do `Makefile` com `uv run …`.
4. **Web** (`web/`): `npm install` e `npm run dev` (http://localhost:5173). O Vite faz proxy de `/api`
   para `http://localhost:8000`; para outra porta defina `API_INTERNAL_URL`.

Com Docker disponível, `docker compose -f docker-compose.yml -f docker-compose.dev.yml up -d db redis`
sobe só o banco e o Redis nas portas locais.

## 8. Checklist de segurança

- `COOKIE_SECURE=true` e `APP_ENV=production` no VPS
- `SECRET_KEY` e `FERNET_KEY` únicos por instalação e fora do git
- Backups testados (restaure em um banco temporário periodicamente)
- Atualize as imagens base de tempos em tempos: `docker compose pull && docker compose up -d --build`
