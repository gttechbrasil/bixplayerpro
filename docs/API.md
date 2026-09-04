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
  "csrf_token": "Y2xpZW50LWNzcmYtdG9rZW4",
  "platform": {
    "name": "Revenda 01",
    "credits_enabled": true
  }
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
    "is_expired": false,
    "logo_url": "https://cdn.exemplo.com/logo.png",
    "bg_url": "https://cdn.exemplo.com/fundo.jpg",
    "qr_content": "https://wa.me/5511999999999",
    "theme": "theme_d",
    "auto_ads": false
  },
  "csrf_token": "Y2xpZW50LWNzcmYtdG9rZW4",
  "platform": {
    "name": "Revenda 01",
    "credits_enabled": true
  }
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
  },
  "platform": {
    "name": "Revenda 01",
    "credits_enabled": true
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

#### `GET /api/v1/admin/resellers/{reseller_id}/devices`

Dispositivos da revenda (somente leitura)

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
      "mac_address": "02:50:50:A1:B2:C3",
      "client_name": "João Silva",
      "license_expires_at": null,
      "playlist_name": "texto",
      "playlist_url": "texto",
      "playlist_host": "texto",
      "playlists_count": 0,
      "app_type": "tv",
      "app_version": "1.0.0",
      "last_seen_at": "2026-09-04T12:00:00Z",
      "connected": false,
      "status": "active",
      "created_at": "2026-09-04T12:00:00Z"
    }
  ],
  "total": 1,
  "page": 1,
  "per_page": 25
}
```

#### `GET /api/v1/admin/resellers/{reseller_id}/payments`

Histórico de pagamentos da revenda

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

### admin: settings

#### `GET /api/v1/admin/settings`

Configurações globais

**Autenticação:** Cookie `admin_session` (login do admin) + header `X-CSRF-Token` em métodos que alteram dados.

**Response 200**

```json
{
  "credits_enabled": true,
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
  "credits_enabled": true,
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
  "credits_enabled": true,
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

#### `GET /api/v1/admin/settings/gateway`

Configuração do gateway de pagamento (mascarada)

**Autenticação:** Cookie `admin_session` (login do admin) + header `X-CSRF-Token` em métodos que alteram dados.

**Response 200**

```json
{
  "provider": "mercadopago",
  "access_token_masked": "texto",
  "access_token_kind": "texto",
  "webhook_secret_configured": true,
  "webhook_url": "texto",
  "pix_expiration_minutes": 1
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

### reseller: devices

#### `GET /api/v1/reseller/devices`

Lista os dispositivos da revenda

**Autenticação:** Cookie `reseller_session` (login da revenda) + header `X-CSRF-Token` em métodos que alteram dados.

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
      "mac_address": "02:50:50:A1:B2:C3",
      "client_name": "João Silva",
      "license_expires_at": null,
      "playlist_name": "texto",
      "playlist_url": "texto",
      "playlist_host": "texto",
      "playlists_count": 0,
      "app_type": "tv",
      "app_version": "1.0.0",
      "last_seen_at": "2026-09-04T12:00:00Z",
      "connected": false,
      "status": "active",
      "created_at": "2026-09-04T12:00:00Z"
    }
  ],
  "total": 1,
  "page": 1,
  "per_page": 25
}
```

#### `POST /api/v1/reseller/devices`

Cadastra (ou reivindica) um dispositivo pelo MAC

**Autenticação:** Cookie `reseller_session` (login da revenda) + header `X-CSRF-Token` em métodos que alteram dados.

**Request**

```json
{
  "mac_address": "02:50:50:A1:B2:C3",
  "client_name": "João Silva",
  "playlist_name": "texto",
  "playlist_url": "texto",
  "license_expires_at": null
}
```

**Response 201**

```json
{
  "id": 12,
  "mac_address": "02:50:50:A1:B2:C3",
  "client_name": "João Silva",
  "license_expires_at": null,
  "playlist_name": "texto",
  "playlist_url": "texto",
  "playlist_host": "texto",
  "playlists_count": 0,
  "app_type": "tv",
  "app_version": "1.0.0",
  "last_seen_at": "2026-09-04T12:00:00Z",
  "connected": false,
  "status": "active",
  "created_at": "2026-09-04T12:00:00Z"
}
```

#### `GET /api/v1/reseller/devices/{device_id}`

Detalhe do dispositivo

**Autenticação:** Cookie `reseller_session` (login da revenda) + header `X-CSRF-Token` em métodos que alteram dados.

**Parâmetros**

| Nome | Em | Obrigatório | Tipo |
|---|---|---|---|
| `device_id` | path | sim | integer |

**Response 200**

```json
{
  "id": 12,
  "mac_address": "02:50:50:A1:B2:C3",
  "client_name": "João Silva",
  "license_expires_at": null,
  "playlist_name": "texto",
  "playlist_url": "texto",
  "playlist_host": "texto",
  "playlists_count": 0,
  "app_type": "tv",
  "app_version": "1.0.0",
  "last_seen_at": "2026-09-04T12:00:00Z",
  "connected": false,
  "status": "active",
  "created_at": "2026-09-04T12:00:00Z"
}
```

#### `PUT /api/v1/reseller/devices/{device_id}`

Edita o dispositivo

**Autenticação:** Cookie `reseller_session` (login da revenda) + header `X-CSRF-Token` em métodos que alteram dados.

**Parâmetros**

| Nome | Em | Obrigatório | Tipo |
|---|---|---|---|
| `device_id` | path | sim | integer |

**Request**

```json
{
  "client_name": "João Silva",
  "playlist_name": "texto",
  "playlist_url": "texto",
  "license_expires_at": null
}
```

**Response 200**

```json
{
  "id": 12,
  "mac_address": "02:50:50:A1:B2:C3",
  "client_name": "João Silva",
  "license_expires_at": null,
  "playlist_name": "texto",
  "playlist_url": "texto",
  "playlist_host": "texto",
  "playlists_count": 0,
  "app_type": "tv",
  "app_version": "1.0.0",
  "last_seen_at": "2026-09-04T12:00:00Z",
  "connected": false,
  "status": "active",
  "created_at": "2026-09-04T12:00:00Z"
}
```

#### `DELETE /api/v1/reseller/devices/{device_id}`

Exclui o dispositivo

**Autenticação:** Cookie `reseller_session` (login da revenda) + header `X-CSRF-Token` em métodos que alteram dados.

**Parâmetros**

| Nome | Em | Obrigatório | Tipo |
|---|---|---|---|
| `device_id` | path | sim | integer |

**Response 200**

```json
{
  "message": "Operação realizada."
}
```

#### `POST /api/v1/reseller/devices/batch-delete`

Exclui vários dispositivos

**Autenticação:** Cookie `reseller_session` (login da revenda) + header `X-CSRF-Token` em métodos que alteram dados.

**Request**

```json
{
  "ids": [
    1
  ]
}
```

**Response 200**

```json
{
  "deleted": 1,
  "message": "Operação realizada."
}
```

### reseller: dns

#### `GET /api/v1/reseller/dns`

Hosts (DNS) em uso nas playlists da revenda

**Autenticação:** Cookie `reseller_session` (login da revenda) + header `X-CSRF-Token` em métodos que alteram dados.

**Response 200**

```json
[
  {
    "host": "texto",
    "playlists": 1
  }
]
```

#### `POST /api/v1/reseller/dns/migrate`

Substitui o host em todas as playlists

**Autenticação:** Cookie `reseller_session` (login da revenda) + header `X-CSRF-Token` em métodos que alteram dados.

**Request**

```json
{
  "from_host": "texto",
  "to_host": "texto"
}
```

**Response 200**

```json
{
  "from_host": "texto",
  "to_host": "texto",
  "affected": 1,
  "message": "Operação realizada."
}
```

### reseller: branding

#### `GET /api/v1/reseller/branding`

Personalização do app (logo, fundo, QR, layout)

**Autenticação:** Cookie `reseller_session` (login da revenda) + header `X-CSRF-Token` em métodos que alteram dados.

**Response 200**

```json
{
  "logo_url": "https://cdn.exemplo.com/logo.png",
  "bg_url": "https://cdn.exemplo.com/fundo.jpg",
  "qr_content": "https://wa.me/5511999999999",
  "theme": "theme_d",
  "auto_ads": false
}
```

#### `PUT /api/v1/reseller/branding`

Atualiza a personalização

**Autenticação:** Cookie `reseller_session` (login da revenda) + header `X-CSRF-Token` em métodos que alteram dados.

**Request**

```json
{
  "logo_url": "https://cdn.exemplo.com/logo.png",
  "bg_url": "https://cdn.exemplo.com/fundo.jpg",
  "qr_content": "https://wa.me/5511999999999",
  "theme": "theme_d",
  "auto_ads": false
}
```

**Response 200**

```json
{
  "logo_url": "https://cdn.exemplo.com/logo.png",
  "bg_url": "https://cdn.exemplo.com/fundo.jpg",
  "qr_content": "https://wa.me/5511999999999",
  "theme": "theme_d",
  "auto_ads": false
}
```

#### `POST /api/v1/reseller/branding/upload`

Envia imagem de logo ou fundo (PNG/JPG/WebP, até 2 MB)

**Autenticação:** Cookie `reseller_session` (login da revenda) + header `X-CSRF-Token` em métodos que alteram dados.

**Parâmetros**

| Nome | Em | Obrigatório | Tipo |
|---|---|---|---|
| `kind` | query | não | string (logo, bg) |

**Response 200**

```json
{
  "url": "http://servidor.exemplo.com/get.php?username=u1&password=p1&type=m3u_plus",
  "kind": "logo"
}
```

#### `GET /api/v1/reseller/branding/banners`

Lista banners

**Autenticação:** Cookie `reseller_session` (login da revenda) + header `X-CSRF-Token` em métodos que alteram dados.

**Response 200**

```json
[
  {
    "id": 12,
    "title": "Promoção de setembro",
    "url": "http://servidor.exemplo.com/get.php?username=u1&password=p1&type=m3u_plus",
    "is_active": true,
    "created_at": "2026-09-04T12:00:00Z"
  }
]
```

#### `POST /api/v1/reseller/branding/banners`

Cria banner

**Autenticação:** Cookie `reseller_session` (login da revenda) + header `X-CSRF-Token` em métodos que alteram dados.

**Request**

```json
{
  "title": "Promoção de setembro",
  "url": "http://servidor.exemplo.com/get.php?username=u1&password=p1&type=m3u_plus",
  "is_active": true
}
```

**Response 201**

```json
{
  "id": 12,
  "title": "Promoção de setembro",
  "url": "http://servidor.exemplo.com/get.php?username=u1&password=p1&type=m3u_plus",
  "is_active": true,
  "created_at": "2026-09-04T12:00:00Z"
}
```

#### `PATCH /api/v1/reseller/branding/banners/{banner_id}`

Edita banner (título, URL, ativo)

**Autenticação:** Cookie `reseller_session` (login da revenda) + header `X-CSRF-Token` em métodos que alteram dados.

**Parâmetros**

| Nome | Em | Obrigatório | Tipo |
|---|---|---|---|
| `banner_id` | path | sim | integer |

**Request**

```json
{
  "title": "Promoção de setembro",
  "url": "http://servidor.exemplo.com/get.php?username=u1&password=p1&type=m3u_plus",
  "is_active": true
}
```

**Response 200**

```json
{
  "id": 12,
  "title": "Promoção de setembro",
  "url": "http://servidor.exemplo.com/get.php?username=u1&password=p1&type=m3u_plus",
  "is_active": true,
  "created_at": "2026-09-04T12:00:00Z"
}
```

#### `DELETE /api/v1/reseller/branding/banners/{banner_id}`

Exclui banner

**Autenticação:** Cookie `reseller_session` (login da revenda) + header `X-CSRF-Token` em métodos que alteram dados.

**Parâmetros**

| Nome | Em | Obrigatório | Tipo |
|---|---|---|---|
| `banner_id` | path | sim | integer |

**Response 200**

```json
{
  "message": "Operação realizada."
}
```

### reseller: profile

#### `GET /api/v1/reseller/profile`

Perfil da revenda

**Autenticação:** Cookie `reseller_session` (login da revenda) + header `X-CSRF-Token` em métodos que alteram dados.

**Response 200**

```json
{
  "id": 12,
  "username": "revenda01",
  "name": "Revenda 01",
  "expires_at": "2027-01-31",
  "credits": 10,
  "created_at": "2026-09-04T12:00:00Z"
}
```

#### `PUT /api/v1/reseller/profile/password`

Troca a própria senha

**Autenticação:** Cookie `reseller_session` (login da revenda) + header `X-CSRF-Token` em métodos que alteram dados.

**Request**

```json
{
  "current_password": "texto",
  "new_password": "texto"
}
```

**Response 200**

```json
{
  "message": "Operação realizada."
}
```

### reseller: billing

#### `GET /api/v1/reseller/billing/plans`

Preço mensal e pacotes promocionais

**Autenticação:** Cookie `reseller_session` (login da revenda) + header `X-CSRF-Token` em métodos que alteram dados.

**Response 200**

```json
{
  "monthly_price": "35.00",
  "max_months": 1,
  "packages": [
    {
      "id": 12,
      "months": 3,
      "price": "100.00"
    }
  ],
  "can_renew": true,
  "expires_at": "2027-01-31"
}
```

#### `POST /api/v1/reseller/billing/pix`

Gera cobrança Pix para renovar a revenda

**Autenticação:** Cookie `reseller_session` (login da revenda) + header `X-CSRF-Token` em métodos que alteram dados.

**Request**

```json
{
  "months": 3,
  "package_id": 1
}
```

**Response 201**

```json
{
  "payment_id": 1,
  "status": "active",
  "months": 3,
  "amount": "105.00",
  "qr_code": "00020126…",
  "qr_base64": "iVBORw0KGgo…",
  "expires_at": "2027-01-31",
  "paid_at": "2026-09-04T12:05:00Z",
  "new_expires_at": "2027-01-01",
  "projected_expires_at": "texto"
}
```

#### `GET /api/v1/reseller/billing/pix/{payment_id}`

Status da cobrança (polling)

**Autenticação:** Cookie `reseller_session` (login da revenda) + header `X-CSRF-Token` em métodos que alteram dados.

**Parâmetros**

| Nome | Em | Obrigatório | Tipo |
|---|---|---|---|
| `payment_id` | path | sim | integer |

**Response 200**

```json
{
  "payment_id": 1,
  "status": "active",
  "months": 3,
  "amount": "105.00",
  "qr_code": "00020126…",
  "qr_base64": "iVBORw0KGgo…",
  "expires_at": "2027-01-31",
  "paid_at": "2026-09-04T12:05:00Z",
  "new_expires_at": "2027-01-01",
  "projected_expires_at": "texto"
}
```

#### `GET /api/v1/reseller/billing/history`

Histórico de pagamentos da revenda

**Autenticação:** Cookie `reseller_session` (login da revenda) + header `X-CSRF-Token` em métodos que alteram dados.

**Parâmetros**

| Nome | Em | Obrigatório | Tipo |
|---|---|---|---|
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

### webhooks

#### `POST /api/v1/webhooks/mercadopago`

Notificação de pagamento do Mercado Pago

Mercado Pago sends `{type: "payment", data: {id}}` (plus `data.id` in the query
string). The signature is validated when a secret is configured and the payment is
always re-fetched from the provider before anything is approved.

**Autenticação:** Sem autenticação.

**Response 200**

```json
{
  "processed": true,
  "status": "active"
}
```
