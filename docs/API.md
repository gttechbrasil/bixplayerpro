# API REST — referência

_Gerado automaticamente por `backend/scripts/gen_api_docs.py` a partir do OpenAPI. Não edite à mão; rode `make docs` no diretório `backend`._

Base: `/api/v1`. Todas as respostas são JSON. Documentação interativa (fora de produção): `/api/docs`.

## Autenticação

| Ator | Como autenticar |
|---|---|
| Admin | `POST /auth/admin/login` grava cookie httpOnly `admin_session` (JWT) e cookie `csrf_token`. Mutations exigem header `X-CSRF-Token` igual ao cookie. |
| Revenda | `POST /auth/reseller/login` grava cookie httpOnly `reseller_session` + `csrf_token`. Revenda bloqueada ou vencida recebe **403**. |
| Dispositivo | `POST /device/register` devolve um token opaco (mostrado uma única vez). Enviar em `Authorization: Bearer <token>`. |

## Erros

Erros seguem o formato `{"detail": {"message": "<texto em português>", "code": "<código>"}}`. Erros de validação (422) seguem o formato padrão do FastAPI (`detail` é uma lista).

| Status | Significado |
|---|---|
| 400 | Regra de negócio violada (ex.: `insufficient_credits`, `invalid_playlist_url`) |
| 401 | Não autenticado / token inválido |
| 403 | Sem permissão, CSRF inválido, revenda bloqueada/vencida, dispositivo não cadastrado |
| 404 | Registro não encontrado |
| 409 | Conflito (ex.: `username_taken`) |
| 422 | Corpo/parâmetros inválidos |
| 429 | Muitas tentativas de login (`rate_limited`) |

## Paginação

Listagens aceitam `page` (≥1), `per_page` (1–100, padrão 25) e `search`, e devolvem `{items, total, page, per_page}`.

## Rotas

### health

#### `GET /api/v1/health`

Verifica API, banco e Redis

**Autenticação:** Sem autenticação.

**Response 200**

```json
{
  "status": "active",
  "database": "ok",
  "redis": "ok"
}
```

### auth

#### `POST /api/v1/auth/admin/login`

Login do administrador

**Autenticação:** Sem autenticação.

**Request**

```json
{
  "username": "revenda01",
  "password": "senha-forte-123"
}
```

**Response 200**

```json
{
  "role": "admin",
  "user": {
    "id": 12,
    "username": "revenda01"
  },
  "csrf_token": "Y2xpZW50LWNzcmYtdG9rZW4"
}
```

#### `POST /api/v1/auth/reseller/login`

Login da revenda

**Autenticação:** Sem autenticação.

**Request**

```json
{
  "username": "revenda01",
  "password": "senha-forte-123"
}
```

**Response 200**

```json
{
  "role": "admin",
  "user": {
    "id": 12,
    "username": "revenda01",
    "name": "Revenda 01",
    "credits": 10,
    "expires_at": "2027-01-31",
    "is_blocked": false,
    "logo_url": "https://cdn.exemplo.com/logo.png",
    "bg_url": "https://cdn.exemplo.com/fundo.jpg",
    "qr_content": "https://wa.me/5511999999999",
    "theme": "theme_d",
    "auto_ads": false
  },
  "csrf_token": "Y2xpZW50LWNzcmYtdG9rZW4"
}
```

#### `POST /api/v1/auth/logout`

Encerra a sessão

**Autenticação:** Sem autenticação.

**Response 200**

```json
{
  "message": "Operação realizada."
}
```

#### `GET /api/v1/auth/me`

Ator autenticado na sessão atual

Returns the logged-in actor. Checks the admin cookie first, then the reseller one.

**Autenticação:** Sem autenticação.

**Response 200**

```json
{
  "role": "admin",
  "user": {
    "id": 12,
    "username": "revenda01"
  }
}
```

### device

#### `POST /api/v1/device/register`

Registra o aparelho e devolve MAC + token

**Autenticação:** Sem autenticação.

**Request**

```json
{
  "device_id": "9774d56d682e549c",
  "app_type": "tv",
  "app_version": "1.0.0"
}
```

**Response 200**

```json
{
  "mac_address": "02:50:50:A1:B2:C3",
  "token": "f3a9c1…(64 hex)"
}
```

#### `GET /api/v1/device/config`

Configuração completa para o app (playlists, tema, status)

**Autenticação:** Header `Authorization: Bearer <token>` (token devolvido pelo `POST /device/register`).

**Response 200**

```json
{
  "registered": true,
  "mac_address": "02:50:50:A1:B2:C3",
  "status": "active",
  "client_name": "João Silva",
  "license_expires_at": null,
  "playlists": [
    {
      "id": 12,
      "name": "Revenda 01",
      "url": "http://servidor.exemplo.com/get.php?username=u1&password=p1&type=m3u_plus",
      "type": "xtream",
      "is_protected": false
    }
  ],
  "theme": "theme_d",
  "logo_url": "https://cdn.exemplo.com/logo.png",
  "bg_url": "https://cdn.exemplo.com/fundo.jpg",
  "qr_content": "https://wa.me/5511999999999",
  "banners": [
    {
      "id": 12,
      "title": "Promoção de setembro",
      "url": "http://servidor.exemplo.com/get.php?username=u1&password=p1&type=m3u_plus"
    }
  ],
  "auto_ads": false,
  "pin": "0000",
  "min_app_version": "1.0.0",
  "apk_url": "https://cdn.exemplo.com/app.apk",
  "platform_name": "Minha Plataforma"
}
```

#### `POST /api/v1/device/playlists`

Adiciona playlist ao dispositivo (auto-cadastro pelo app)

**Autenticação:** Header `Authorization: Bearer <token>` (token devolvido pelo `POST /device/register`).

**Request**

```json
{
  "name": "Revenda 01",
  "url": "http://servidor.exemplo.com/get.php?username=u1&password=p1&type=m3u_plus",
  "is_protected": false
}
```

**Response 201**

```json
{
  "id": 12,
  "name": "Revenda 01",
  "url": "http://servidor.exemplo.com/get.php?username=u1&password=p1&type=m3u_plus",
  "type": "xtream",
  "is_protected": false
}
```

#### `DELETE /api/v1/device/playlists/{playlist_id}`

Remove playlist do dispositivo

**Autenticação:** Header `Authorization: Bearer <token>` (token devolvido pelo `POST /device/register`).

**Parâmetros**

| Nome | Em | Obrigatório | Tipo |
|---|---|---|---|
| `playlist_id` | path | sim | integer |

**Response 200**

```json
{
  "message": "Operação realizada."
}
```

### admin: resellers

#### `GET /api/v1/admin/resellers`

Lista revendas com busca, paginação e filtro de status

**Autenticação:** Cookie `admin_session` (login do admin) + header `X-CSRF-Token` em métodos que alteram dados.

**Parâmetros**

| Nome | Em | Obrigatório | Tipo |
|---|---|---|---|
| `status` | query | não | string |
| `page` | query | não | integer |
| `per_page` | query | não | integer |
| `search` | query | não | string |

**Response 200**

```json
{
  "items": [
    {
      "id": 12,
      "username": "revenda01",
      "name": "Revenda 01",
      "credits": 10,
      "expires_at": "2027-01-31",
      "is_blocked": false,
      "logo_url": "https://cdn.exemplo.com/logo.png",
      "bg_url": "https://cdn.exemplo.com/fundo.jpg",
      "qr_content": "https://wa.me/5511999999999",
      "theme": "theme_d",
      "auto_ads": false,
      "created_at": "2026-09-04T12:00:00Z",
      "updated_at": "2026-09-04T12:00:00Z",
      "devices_count": 3
    }
  ],
  "total": 1,
  "page": 1,
  "per_page": 25
}
```

#### `POST /api/v1/admin/resellers`

Cria revenda

**Autenticação:** Cookie `admin_session` (login do admin) + header `X-CSRF-Token` em métodos que alteram dados.

**Request**

```json
{
  "username": "revenda01",
  "name": "Revenda 01",
  "password": "senha-forte-123",
  "credits": 10,
  "expires_at": "2027-01-31"
}
```

**Response 201**

```json
{
  "id": 12,
  "username": "revenda01",
  "name": "Revenda 01",
  "credits": 10,
  "expires_at": "2027-01-31",
  "is_blocked": false,
  "logo_url": "https://cdn.exemplo.com/logo.png",
  "bg_url": "https://cdn.exemplo.com/fundo.jpg",
  "qr_content": "https://wa.me/5511999999999",
  "theme": "theme_d",
  "auto_ads": false,
  "created_at": "2026-09-04T12:00:00Z",
  "updated_at": "2026-09-04T12:00:00Z",
  "devices_count": 3
}
```

#### `GET /api/v1/admin/resellers/{reseller_id}`

Detalhe da revenda

**Autenticação:** Cookie `admin_session` (login do admin) + header `X-CSRF-Token` em métodos que alteram dados.

**Parâmetros**

| Nome | Em | Obrigatório | Tipo |
|---|---|---|---|
| `reseller_id` | path | sim | integer |

**Response 200**

```json
{
  "id": 12,
  "username": "revenda01",
  "name": "Revenda 01",
  "credits": 10,
  "expires_at": "2027-01-31",
  "is_blocked": false,
  "logo_url": "https://cdn.exemplo.com/logo.png",
  "bg_url": "https://cdn.exemplo.com/fundo.jpg",
  "qr_content": "https://wa.me/5511999999999",
  "theme": "theme_d",
  "auto_ads": false,
  "created_at": "2026-09-04T12:00:00Z",
  "updated_at": "2026-09-04T12:00:00Z",
  "devices_count": 3
}
```

#### `PATCH /api/v1/admin/resellers/{reseller_id}`

Edita dados da revenda

**Autenticação:** Cookie `admin_session` (login do admin) + header `X-CSRF-Token` em métodos que alteram dados.

**Parâmetros**

| Nome | Em | Obrigatório | Tipo |
|---|---|---|---|
| `reseller_id` | path | sim | integer |

**Request**

```json
{
  "name": "Revenda 01",
  "username": "revenda01",
  "theme": "theme_d",
  "logo_url": "https://cdn.exemplo.com/logo.png",
  "bg_url": "https://cdn.exemplo.com/fundo.jpg",
  "qr_content": "https://wa.me/5511999999999",
  "auto_ads": false
}
```

**Response 200**

```json
{
  "id": 12,
  "username": "revenda01",
  "name": "Revenda 01",
  "credits": 10,
  "expires_at": "2027-01-31",
  "is_blocked": false,
  "logo_url": "https://cdn.exemplo.com/logo.png",
  "bg_url": "https://cdn.exemplo.com/fundo.jpg",
  "qr_content": "https://wa.me/5511999999999",
  "theme": "theme_d",
  "auto_ads": false,
  "created_at": "2026-09-04T12:00:00Z",
  "updated_at": "2026-09-04T12:00:00Z",
  "devices_count": 3
}
```

#### `DELETE /api/v1/admin/resellers/{reseller_id}`

Exclui a revenda

**Autenticação:** Cookie `admin_session` (login do admin) + header `X-CSRF-Token` em métodos que alteram dados.

**Parâmetros**

| Nome | Em | Obrigatório | Tipo |
|---|---|---|---|
| `reseller_id` | path | sim | integer |

**Response 200**

```json
{
  "message": "Operação realizada."
}
```

#### `POST /api/v1/admin/resellers/{reseller_id}/block`

Bloqueia ou desbloqueia a revenda

**Autenticação:** Cookie `admin_session` (login do admin) + header `X-CSRF-Token` em métodos que alteram dados.

**Parâmetros**

| Nome | Em | Obrigatório | Tipo |
|---|---|---|---|
| `reseller_id` | path | sim | integer |

**Request**

```json
{
  "is_blocked": false
}
```

**Response 200**

```json
{
  "id": 12,
  "username": "revenda01",
  "name": "Revenda 01",
  "credits": 10,
  "expires_at": "2027-01-31",
  "is_blocked": false,
  "logo_url": "https://cdn.exemplo.com/logo.png",
  "bg_url": "https://cdn.exemplo.com/fundo.jpg",
  "qr_content": "https://wa.me/5511999999999",
  "theme": "theme_d",
  "auto_ads": false,
  "created_at": "2026-09-04T12:00:00Z",
  "updated_at": "2026-09-04T12:00:00Z",
  "devices_count": 3
}
```

#### `POST /api/v1/admin/resellers/{reseller_id}/password`

Redefine a senha da revenda

**Autenticação:** Cookie `admin_session` (login do admin) + header `X-CSRF-Token` em métodos que alteram dados.

**Parâmetros**

| Nome | Em | Obrigatório | Tipo |
|---|---|---|---|
| `reseller_id` | path | sim | integer |

**Request**

```json
{
  "password": "senha-forte-123"
}
```

**Response 200**

```json
{
  "message": "Operação realizada."
}
```

#### `POST /api/v1/admin/resellers/{reseller_id}/credits`

Ajuste manual de créditos (gera ledger + auditoria)

**Autenticação:** Cookie `admin_session` (login do admin) + header `X-CSRF-Token` em métodos que alteram dados.

**Parâmetros**

| Nome | Em | Obrigatório | Tipo |
|---|---|---|---|
| `reseller_id` | path | sim | integer |

**Request**

```json
{
  "delta": 5,
  "note": "Compra de pacote de 5 créditos"
}
```

**Response 200**

```json
{
  "id": 12,
  "username": "revenda01",
  "name": "Revenda 01",
  "credits": 10,
  "expires_at": "2027-01-31",
  "is_blocked": false,
  "logo_url": "https://cdn.exemplo.com/logo.png",
  "bg_url": "https://cdn.exemplo.com/fundo.jpg",
  "qr_content": "https://wa.me/5511999999999",
  "theme": "theme_d",
  "auto_ads": false,
  "created_at": "2026-09-04T12:00:00Z",
  "updated_at": "2026-09-04T12:00:00Z",
  "devices_count": 3
}
```

#### `GET /api/v1/admin/resellers/{reseller_id}/credits`

Histórico de movimentação de créditos

**Autenticação:** Cookie `admin_session` (login do admin) + header `X-CSRF-Token` em métodos que alteram dados.

**Parâmetros**

| Nome | Em | Obrigatório | Tipo |
|---|---|---|---|
| `reseller_id` | path | sim | integer |
| `page` | query | não | integer |
| `per_page` | query | não | integer |
| `search` | query | não | string |

**Response 200**

```json
{
  "items": [
    {
      "id": 12,
      "reseller_id": 12,
      "delta": 5,
      "balance_after": 15,
      "reason": "admin_adjustment",
      "note": "Compra de pacote de 5 créditos",
      "ref": null,
      "actor_type": "admin",
      "actor_id": 1,
      "created_at": "2026-09-04T12:00:00Z"
    }
  ],
  "total": 1,
  "page": 1,
  "per_page": 25
}
```

#### `PATCH /api/v1/admin/resellers/{reseller_id}/expiration`

Define o vencimento da revenda

**Autenticação:** Cookie `admin_session` (login do admin) + header `X-CSRF-Token` em métodos que alteram dados.

**Parâmetros**

| Nome | Em | Obrigatório | Tipo |
|---|---|---|---|
| `reseller_id` | path | sim | integer |

**Request**

```json
{
  "expires_at": "2027-01-31"
}
```

**Response 200**

```json
{
  "id": 12,
  "username": "revenda01",
  "name": "Revenda 01",
  "credits": 10,
  "expires_at": "2027-01-31",
  "is_blocked": false,
  "logo_url": "https://cdn.exemplo.com/logo.png",
  "bg_url": "https://cdn.exemplo.com/fundo.jpg",
  "qr_content": "https://wa.me/5511999999999",
  "theme": "theme_d",
  "auto_ads": false,
  "created_at": "2026-09-04T12:00:00Z",
  "updated_at": "2026-09-04T12:00:00Z",
  "devices_count": 3
}
```

### admin: settings

#### `GET /api/v1/admin/settings`

Configurações globais

**Autenticação:** Cookie `admin_session` (login do admin) + header `X-CSRF-Token` em métodos que alteram dados.

**Response 200**

```json
{
  "monthly_price": "35.00",
  "packages": [
    {
      "months": 3,
      "price": "100.00"
    }
  ],
  "min_app_version": "1.0.0",
  "apk_url": "https://cdn.exemplo.com/app.apk",
  "platform_name": "Minha Plataforma"
}
```

#### `PUT /api/v1/admin/settings`

Atualiza configurações globais

**Autenticação:** Cookie `admin_session` (login do admin) + header `X-CSRF-Token` em métodos que alteram dados.

**Request**

```json
{
  "monthly_price": "35.00",
  "packages": [
    {
      "months": 3,
      "price": "100.00"
    }
  ],
  "min_app_version": "1.0.0",
  "apk_url": "https://cdn.exemplo.com/app.apk",
  "platform_name": "Minha Plataforma"
}
```

**Response 200**

```json
{
  "monthly_price": "35.00",
  "packages": [
    {
      "months": 3,
      "price": "100.00"
    }
  ],
  "min_app_version": "1.0.0",
  "apk_url": "https://cdn.exemplo.com/app.apk",
  "platform_name": "Minha Plataforma"
}
```

### admin: dashboard

#### `GET /api/v1/admin/dashboard`

Totais para o dashboard

**Autenticação:** Cookie `admin_session` (login do admin) + header `X-CSRF-Token` em métodos que alteram dados.

**Response 200**

```json
{
  "resellers_total": 1,
  "resellers_active": 1,
  "resellers_blocked": 1,
  "resellers_expired": 1,
  "devices_total": 1,
  "devices_registered": 1,
  "devices_active": 1,
  "devices_seen_24h": 1,
  "payments_month_count": 1,
  "payments_month_amount": "texto",
  "payments_pending": 1
}
```

### admin: payments

#### `GET /api/v1/admin/payments`

Lista pagamentos com filtros

**Autenticação:** Cookie `admin_session` (login do admin) + header `X-CSRF-Token` em métodos que alteram dados.

**Parâmetros**

| Nome | Em | Obrigatório | Tipo |
|---|---|---|---|
| `status` | query | não | string |
| `reseller_id` | query | não | integer |
| `from` | query | não | string |
| `to` | query | não | string |
| `page` | query | não | integer |
| `per_page` | query | não | integer |
| `search` | query | não | string |

**Response 200**

```json
{
  "items": [
    {
      "id": 12,
      "reseller_id": 12,
      "reseller_username": "revenda01",
      "provider": "mercadopago",
      "provider_id": "123456789",
      "months": 3,
      "amount": "105.00",
      "status": "active",
      "paid_at": "2026-09-04T12:05:00Z",
      "previous_expires_at": "2026-10-01",
      "new_expires_at": "2027-01-01",
      "created_at": "2026-09-04T12:00:00Z"
    }
  ],
  "total": 1,
  "page": 1,
  "per_page": 25
}
```

### admin: audit

#### `GET /api/v1/admin/audit-log`

Lista o log de auditoria com filtros

**Autenticação:** Cookie `admin_session` (login do admin) + header `X-CSRF-Token` em métodos que alteram dados.

**Parâmetros**

| Nome | Em | Obrigatório | Tipo |
|---|---|---|---|
| `action` | query | não | string |
| `actor_type` | query | não | string |
| `actor_id` | query | não | integer |
| `target` | query | não | string |
| `from` | query | não | string |
| `to` | query | não | string |
| `page` | query | não | integer |
| `per_page` | query | não | integer |
| `search` | query | não | string |

**Response 200**

```json
{
  "items": [
    {
      "id": 12,
      "actor_type": "admin",
      "actor_id": 1,
      "action": "credits.adjust",
      "target": "reseller:12",
      "payload": {
        "delta": 5,
        "balance_after": 15
      },
      "ip": "203.0.113.10",
      "created_at": "2026-09-04T12:00:00Z"
    }
  ],
  "total": 1,
  "page": 1,
  "per_page": 25
}
```
