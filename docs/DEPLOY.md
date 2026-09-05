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

> **Instalação em produção já feita.** A seção [§11](#11-instalação-atual-bixplayerpro) descreve
> exatamente o que foi provisionado no VPS em 05/09/2026, com os caminhos e comandos reais.

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
| `PAYMENT_PROVIDER` | sim | `mercadopago` (padrão) ou `fake` (desenvolvimento) |
| `MERCADOPAGO_ACCESS_TOKEN`, `MERCADOPAGO_WEBHOOK_SECRET` | sim | Credenciais do Pix (§9). Ficam **apenas** no `.env` |
| `PIX_EXPIRATION_MINUTES` | não | Validade do QR Pix (padrão 30) |
| `UPLOAD_DIR` | não | Pasta das imagens enviadas (padrão `./uploads`; no compose `/app/uploads`) |
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
- SSH somente por chave (`PasswordAuthentication no`), root sem senha (`prohibit-password`)
- `ufw` com política padrão *deny* e apenas 22/80/443 liberados
- `fail2ban` ativo na jail `sshd` (5 tentativas, ban de 1 h)
- `unattended-upgrades` habilitado para correções de segurança
- Backups testados (restaure em um banco temporário periodicamente)
- Atualize as imagens base de tempos em tempos: `docker compose pull && docker compose up -d --build`

## 9. Mercado Pago (Pix)

1. Crie uma aplicação em https://www.mercadopago.com.br/developers (tipo *Pagamentos online*,
   integração *Checkout Transparente*).
2. **Credenciais de teste** (sandbox): em *Credenciais de teste* copie o *Access Token* para
   `MERCADOPAGO_ACCESS_TOKEN`. Pagamentos criados com ele não movimentam dinheiro; para
   aprovar um Pix de teste use a conta de *Usuário de teste comprador* ou aprove pela API de
   sandbox. Em produção troque pelo *Access Token de produção*.
3. **Webhook**: em *Webhooks* configure a URL `https://SEU_DOMINIO/api/v1/webhooks/mercadopago`
   para o evento *Pagamentos* e copie a **assinatura secreta** exibida para
   `MERCADOPAGO_WEBHOOK_SECRET`. Sem esse valor a assinatura não é validada, mas o pagamento
   sempre é confirmado consultando a API antes de aprovar.
4. **Sandbox**: com o *Access Token de teste* (`TEST-…`) o Mercado Pago **recusa** e-mails de usuário
   de teste (`@testuser.com`) como pagador (403 `Payer email forbidden`) e exige um e-mail comum com
   domínio válido. Defina `MERCADOPAGO_TEST_PAYER_EMAIL=comprador-teste@exemplo.com` (qualquer
   endereço com formato válido). Deixe **vazio em produção** (a API usa `revenda-<id>@<domínio>`).
   Validado em 2026-09-05: `POST /reseller/billing/pix` devolveu `qr_code` e `qr_base64` reais e o
   polling consultou `GET /v1/payments/{id}`. Observação: `GET /v1/payment_methods` com credenciais de
   teste pode não listar `pix` mesmo com o Pix funcionando.
5. `PAYMENT_PROVIDER=mercadopago` (padrão). Para desenvolvimento sem gateway use
   `PAYMENT_PROVIDER=fake`: o Pix é fictício e o pagamento fica pendente até ser aprovado por
   código (usado nos testes).
6. `PIX_EXPIRATION_MINUTES` (padrão 30) define a validade do QR.

O painel da revenda consulta o status a cada 4 s; em desenvolvimento local, sem webhook
acessível pela internet, é essa consulta que detecta a aprovação.

## 10. Uploads (logo e fundo)

As imagens enviadas pela revenda ficam no volume `uploads` (`UPLOAD_DIR=/app/uploads` na API)
e são servidas pelo Caddy em `https://SEU_DOMINIO/uploads/...`. Inclua o volume no backup:

```bash
docker run --rm -v iptv-platform_uploads:/data -v $PWD/backups:/backup alpine   tar czf /backup/uploads-$(date +%F).tgz -C /data .
```

---

## 11. Instalação atual (bixplayer.pro)

Provisionamento executado em **05/09/2026**. Esta seção descreve o servidor em produção.

### 11.1 Servidor

| Item | Valor |
|---|---|
| Sistema | Ubuntu 26.04.1 LTS (x86-64), atualizado no provisionamento |
| Recursos | 4 vCPU · 16 GB RAM · 193 GB de disco |
| Docker | 29.8.0 com Compose v5.5.1 (repositório oficial da Docker) |
| Domínio | `bixplayer.pro` (o `www` redireciona 301 para o apex) |
| TLS | Let's Encrypt, emitido e renovado automaticamente pelo Caddy |

### 11.2 Contas e acesso SSH

- **root**: apenas por chave (`PermitRootLogin prohibit-password`).
- **deploy**: dono da aplicação, nos grupos `sudo` e `docker`, com `sudo` sem senha
  (`/etc/sudoers.d/90-deploy`) para o script de deploy funcionar sem interação.
- Login por senha está **desativado**. O drop-in `/etc/ssh/sshd_config.d/00-hardening.conf`
  é lido **antes** do `50-cloud-init.conf` (que trazia `PasswordAuthentication yes`), porque no
  `sshd_config` vale o primeiro valor encontrado para cada diretiva.
- A chave privada fica em `deploy/id_deploy` (gitignorada, junto com `deploy/.vps.env`).

```bash
ssh -i deploy/id_deploy deploy@<IP_DA_VPS>
```

> Se a chave for perdida, o acesso só volta pelo console do provedor. Guarde uma cópia de
> `deploy/id_deploy` e de `deploy/.vps.env` fora da máquina de desenvolvimento.

### 11.3 Firewall e proteção contra força bruta

```
ufw: default deny (incoming) / allow (outgoing)
     22/tcp (SSH) · 80/tcp (HTTP) · 443/tcp + 443/udp (HTTPS e HTTP/3)
fail2ban: jail sshd, maxretry 5, findtime 10 min, bantime 1 h, backend systemd
```

### 11.4 Aplicação

- Código em `/home/deploy/app`, clone de `https://github.com/gttechbrasil/bixplayerpro.git`.
- Configuração em `/home/deploy/app/deploy/.env` (permissão `600`, dono `deploy`), gerada no
  provisionamento com `POSTGRES_PASSWORD`, `SECRET_KEY`, `FERNET_KEY` e `ADMIN_PASSWORD`
  aleatórios. **Esse arquivo é a única cópia desses segredos** — inclua-o no backup externo.
- Stack: `docker compose -f deploy/docker-compose.yml --env-file deploy/.env`.

```bash
ssh -i deploy/id_deploy deploy@<IP_DA_VPS>
cd /home/deploy/app
docker compose -f deploy/docker-compose.yml --env-file deploy/.env ps
docker compose -f deploy/docker-compose.yml --env-file deploy/.env logs -f api
```

### 11.5 Deploy de novas versões

Um comando, a partir da máquina de desenvolvimento:

```bash
./deploy/deploy.sh            # push + pull no servidor + build + migrações + healthcheck
./deploy/deploy.sh --no-push  # só atualiza o servidor com o que já está no remoto
./deploy/deploy.sh --logs     # acompanha os logs ao final
```

O script faz `git push`, atualiza `/home/deploy/app` com `git reset --hard origin/<branch>`,
recria os containers, aplica `alembic upgrade head`, remove imagens órfãs e valida
`https://bixplayer.pro/api/v1/health`.

### 11.6 Backup

- Script versionado em `deploy/backup.sh`, instalado em `/home/deploy/backup.sh`.
- `cron` em `/etc/cron.d/iptv-backup` roda **todo dia às 03:30** como `deploy`.
- Saída em `/home/deploy/backups`: `db-AAAA-MM-DD-HHMM.sql.gz` (dump lógico) e
  `uploads-AAAA-MM-DD-HHMM.tgz` (volume de imagens). **Retenção de 7 dias.**
- O log de cada execução vai para `/home/deploy/backups/backup.log`.
- As credenciais do banco são lidas do ambiente do próprio container, e não do `.env`
  (valores com espaço, como `PLATFORM_NAME`, quebram um `source` do arquivo).

```bash
# rodar sob demanda
ssh -i deploy/id_deploy deploy@<IP_DA_VPS> /home/deploy/backup.sh

# trazer o backup mais recente para a máquina local
scp -i deploy/id_deploy deploy@<IP_DA_VPS>:/home/deploy/backups/db-*.sql.gz .
```

Restauração: veja a §6.

### 11.7 Rotação de logs

- `/etc/docker/daemon.json` limita cada container a **10 MB por arquivo e 5 arquivos**
  (`json-file`), o que impede o disco de encher com log de container.
- `/etc/logrotate.d/docker-containers` roda diariamente sobre os arquivos já existentes,
  com compressão e 5 gerações.

### 11.8 URLs

| Recurso | URL |
|---|---|
| Painel administrativo | https://bixplayer.pro/admin |
| Dashboard do revendedor | https://bixplayer.pro/painel |
| Healthcheck | https://bixplayer.pro/api/v1/health |
| Imagens enviadas | https://bixplayer.pro/uploads/… |
| Webhook do Mercado Pago | https://bixplayer.pro/api/v1/webhooks/mercadopago |

O usuário administrador é `admin`; a senha está em `ADMIN_PASSWORD` no
`/home/deploy/app/deploy/.env` do servidor.

### 11.9 Verificação pós-deploy

Executada e aprovada em 05/09/2026 (23 verificações):

- HTTPS com certificado válido do Let's Encrypt; `www` e HTTP redirecionando.
- `GET /api/v1/health` devolvendo `{"status":"ok","database":"ok","redis":"ok"}`.
- `/api/docs` respondendo 404 (desativado por `APP_ENV=production`).
- Login do admin, dashboard e leitura do gateway (token de teste mascarado).
- Criação de revenda pelo admin, login no `/painel` e cadastro de dispositivo.
- Upload de imagem gravado no volume e servido pelo Caddy em `/uploads` com cache de 1 dia.
- Geração de Pix real no sandbox do Mercado Pago (QR `br.gov.bcb.pix` + PNG base64) e polling
  consultando `GET /v1/payments/{id}`.
- `POST /device/register` gerando MAC no prefixo `02:50:50` e `GET /device/config` respondendo.

