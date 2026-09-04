# ADR-001 — Modelo de dados

**Status:** aceito · **Data:** 2026-09-04 · **Atualizado:** 2026-09-04 (créditos opcionais, M2)

## Contexto

O núcleo do modelo está definido em `CLAUDE.md`. Este ADR detalha tabelas, colunas,
índices e relações, e registra as decisões que os documentos de referência
(`docs/spec-painel.md`, `docs/spec-app.md`, `docs/Anexo-I-Especificacao-Funcional.md`)
deixam em aberto ou contradizem entre si.

Conflito relevante: a `spec-painel.md` §0 declara "não haverá sistema de créditos", mas o
`CLAUDE.md` (coluna `credits`, tabela `credit_ledger`, "regras de crédito só no servidor"),
o Anexo I (§2.1 "ajuste manual de créditos", §2.2 "cada dispositivo cadastrado consome
1 crédito") e o `PLANO-M1.md` (`POST /resellers/{id}/credits`) incluem créditos. **Prevalece o
CLAUDE.md + Anexo I**: créditos existem. A `spec-painel.md` é tratada como referência de UI/UX
do original, não como escopo.

## Decisão

Todas as chaves primárias são `BIGINT IDENTITY`. Datas de auditoria são `TIMESTAMPTZ`;
vencimentos são `DATE` (semântica "válido até o fim do dia"). Tabelas com
`created_at`/`updated_at` usam `TimestampMixin`.

### `admins`
| coluna | tipo | obs |
|---|---|---|
| id | bigint PK | |
| username | varchar(64) **unique** | |
| password_hash | varchar(255) | argon2id |
| created_at, updated_at | timestamptz | |

### `resellers`
| coluna | tipo | obs |
|---|---|---|
| id | bigint PK | |
| username | varchar(64) **unique** | login |
| name | varchar(120) | |
| password_hash | varchar(255) | argon2id |
| credits | integer, default 0, `CHECK (credits >= 0)` | saldo atual (derivável do ledger; materializado para leitura rápida) |
| expires_at | date, **nullable** | vencimento da revenda. `NULL` = sem vencimento |
| is_blocked | boolean, default false | bloqueio manual pelo admin |
| logo_url, bg_url | text, nullable | personalização (URL externa) |
| qr_content | text, nullable | conteúdo do QR exibido no app |
| theme | varchar(16), default `theme_d` | catálogo fixo `theme_d`, `theme_1`..`theme_8` |
| auto_ads | boolean, default false | banners automáticos |
| created_at, updated_at | timestamptz | |

Índices: `ix_resellers_expires_at`.

Regras: revenda com `is_blocked = true` não loga. Revenda com `expires_at < hoje` **loga** mas só
acessa renovação (Pix) e perfil; as demais rotas respondem 403 `reseller_expired`. Em ambos os
casos seus dispositivos recebem `status = expired` no app. Revenda com `expires_at = NULL` não
vence, **não renova** (sem card de vencimento no painel; `POST /reseller/billing/pix` responde 422
`no_expiration`) e só o admin define um vencimento para ela (decisão do M2, 2026-09-04). Exclusão de revenda é física; `devices.reseller_id` vai
para `NULL` (dispositivo volta a "não cadastrado"); `payments` e `credit_ledger` mantêm o
histórico com `reseller_id = NULL`.

### `devices`
| coluna | tipo | obs |
|---|---|---|
| id | bigint PK | |
| reseller_id | bigint FK → resellers **ON DELETE SET NULL**, nullable | `NULL` = registrado pelo app mas ainda não cadastrado por nenhuma revenda |
| mac_address | char(17) **unique** | `XX:XX:XX:XX:XX:XX` maiúsculo; gerado pelo servidor (`MAC_PREFIX` + 3 bytes aleatórios) ou digitado pela revenda |
| device_id | varchar(64) **unique**, nullable | SHA-256 hex do identificador enviado pelo app (ANDROID_ID). `NULL` quando o dispositivo foi criado pelo painel antes de o app conectar |
| client_name | varchar(120), nullable | nome do cliente final |
| token_hash | varchar(64) **unique**, nullable | SHA-256 do token opaco (32 bytes) devolvido só no `register` |
| app_type | varchar(16), nullable | `tv` / `mobile` |
| app_version | varchar(32), nullable | |
| license_expires_at | date, nullable | `NULL` = vitalícia |
| last_seen_at | timestamptz, nullable | atualizado em cada `GET /device/config` |
| pin | varchar(8), default `0000` | PIN parental inicial entregue ao app |
| created_at, updated_at | timestamptz | |

Índices: `ix_devices_reseller_id` e os uniques acima.

Regras:
- `POST /device/register` é idempotente por `device_id`: repetir devolve o mesmo MAC e **rotaciona** o token (caso de reinstalação do app).
- Cadastro pela revenda (M2) informa o MAC exibido no app. Se já existe um device com esse MAC e `reseller_id IS NULL`, ele é reivindicado; se não existe, é criado com `device_id = NULL`. MAC já pertencente a outra revenda → 409.
- **Créditos são opcionais** (decisão do M2, bloco 0): a configuração global `credits_enabled` (padrão `false`) liga o sistema. Com `true`, cada cadastro pela revenda consome 1 crédito (linha em `credit_ledger`, `delta = -1`), saldo zerado bloqueia o cadastro (400 `insufficient_credits`) e o admin ajusta saldos. Com `false`, nada é consumido, o ledger não é escrito, o ajuste manual responde 400 `credits_disabled` e os painéis não exibem saldo. Exclusão de dispositivo **não devolve** crédito (o Anexo não prevê estorno).
- Status para o app: `unregistered` (sem revenda) · `expired` (licença vencida, revenda bloqueada ou revenda vencida) · `active`.

### `playlists`
| coluna | tipo | obs |
|---|---|---|
| id | bigint PK | |
| device_id | bigint FK → devices **ON DELETE CASCADE** | |
| name | varchar(120) | |
| url | text | URL da playlist. Para Xtream, **sem** o parâmetro `password` (fica em `password_enc`); para M3U, a URL original |
| type | varchar(8) | `xtream` / `m3u` |
| host | varchar(255), nullable | `scheme://host[:port]` — base para o migrador de DNS |
| username | varchar(255), nullable | extraído da querystring Xtream |
| password_enc | text, nullable | senha Xtream cifrada com Fernet (`FERNET_KEY`) |
| is_protected | boolean, default false | exige PIN no app |
| position | integer, default 0 | ordem de exibição |
| created_at, updated_at | timestamptz | |

Índices: `ix_playlists_device_id`, `ix_playlists_host`.

A senha nunca é devolvida para painel/admin. Para o app a URL é remontada com a senha
decifrada (o app precisa dela para autenticar no Xtream); o transporte é TLS.

### `banners`
| coluna | tipo |
|---|---|
| id | bigint PK |
| reseller_id | bigint FK → resellers ON DELETE CASCADE |
| title | varchar(120) |
| url | text |
| is_active | boolean, default true |
| created_at, updated_at | timestamptz |

Índice: `ix_banners_reseller_id`.

### `payments`
| coluna | tipo | obs |
|---|---|---|
| id | bigint PK | |
| reseller_id | bigint FK → resellers ON DELETE SET NULL, nullable | |
| provider | varchar(32) | `mercadopago` |
| provider_id | varchar(128), nullable | id da cobrança no provedor |
| months | integer | 1..60 |
| amount | numeric(10,2) | calculado **no servidor** (preço × meses ou pacote) |
| status | varchar(16) | `pending` / `approved` / `cancelled` / `expired` |
| qr_code | text, nullable | Pix copia-e-cola |
| qr_base64 | text, nullable | PNG base64 |
| paid_at | timestamptz, nullable | |
| expires_at | timestamptz, nullable | validade do QR Pix (`PIX_EXPIRATION_MINUTES`, padrão 30) |
| previous_expires_at, new_expires_at | date, nullable | vencimento antes/depois da aprovação. Aprovação estende a partir do vencimento atual se futuro, senão de hoje |
| created_at, updated_at | timestamptz | |

Índices: `ix_payments_reseller_id`, `ix_payments_status`, `ix_payments_created_at`,
unique `(provider, provider_id)`.

### `credit_ledger`
| coluna | tipo | obs |
|---|---|---|
| id | bigint PK | |
| reseller_id | bigint FK → resellers ON DELETE SET NULL, nullable | |
| delta | integer | positivo = crédito, negativo = débito |
| balance_after | integer | saldo após a operação |
| reason | varchar(32) | `admin_adjustment`, `device_registration`, … |
| note | text, nullable | motivo digitado pelo admin |
| ref | varchar(64), nullable | ex.: `device:123` |
| actor_type | varchar(16) | `admin` / `reseller` / `system` |
| actor_id | bigint, nullable | |
| created_at | timestamptz | |

Índices: `ix_credit_ledger_reseller_id`, `ix_credit_ledger_created_at`.

### `audit_log`
| coluna | tipo | obs |
|---|---|---|
| id | bigint PK | |
| actor_type | varchar(16) | `admin` / `reseller` / `device` / `system` |
| actor_id | bigint, nullable | |
| action | varchar(64) | ex.: `reseller.create`, `credits.adjust`, `device.delete`, `dns.migrate`, `payment.approved` |
| target | varchar(64), nullable | ex.: `reseller:12` |
| payload | jsonb, nullable | dados relevantes (sem segredos) |
| ip | varchar(45), nullable | |
| created_at | timestamptz | |

Índices: `ix_audit_log_created_at`, `ix_audit_log_actor`, `ix_audit_log_action`.

Sem FK: o log sobrevive à exclusão de qualquer entidade.

### `settings`
| coluna | tipo |
|---|---|
| key | varchar(64) PK |
| value | jsonb |
| updated_at | timestamptz |

Chaves e valores padrão (seed):

| key | valor padrão |
|---|---|
| `monthly_price` | `35.00` |
| `packages` | `[]` — lista de `{months, price}` |
| `min_app_version` | `"1.0.0"` |
| `apk_url` | `""` |
| `platform_name` | `PLATFORM_NAME` do `.env` |
| `credits_enabled` | `false` — liga o sistema de créditos (M2 §0) |

Chaves do gateway Pix ficam **somente no `.env`**, nunca no banco.

## Diagrama

```
admins                       settings (key/value)      audit_log (sem FK)

resellers 1 ──< devices 1 ──< playlists
    │
    ├──< banners
    ├──< payments
    └──< credit_ledger
```

## Consequências

- O saldo de créditos existe em dois lugares (coluna + ledger). A escrita é sempre feita
  em uma única transação que grava o ledger e atualiza a coluna com
  `UPDATE … SET credits = credits + delta`; o `CHECK (credits >= 0)` impede saldo negativo
  mesmo sob concorrência.
- Dispositivos criados pelo painel antes de o app conectar não têm `device_id`; o app só
  passa a enxergar esse registro se o MAC gerado no `register` coincidir, o que só acontece
  quando a revenda digitou exatamente o MAC exibido pelo app. Esse é o fluxo previsto
  (app primeiro, painel depois).
- A senha Xtream cifrada em repouso exige `FERNET_KEY` estável; trocar a chave exige
  migração de dados.
