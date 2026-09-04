# ADR-002 — Autenticação, sessões e segurança

**Status:** aceito · **Data:** 2026-09-04

## Contexto

O `CLAUDE.md` fixa: JWT em cookie httpOnly para admin e revenda, token opaco para o
dispositivo, argon2 para senhas, Fernet para segredos em repouso, rate limit em Redis e
proteção CSRF (Anexo I §2.4). Este ADR registra como isso foi implementado e as bibliotecas
adicionadas para tanto.

## Decisões

### Sessões de admin e revenda
- **Cookies separados**: `admin_session` e `reseller_session`, ambos httpOnly, `SameSite=Lax`,
  `Secure` quando `COOKIE_SECURE=true`. Assim o dono da plataforma pode estar logado como admin
  e como uma revenda de teste no mesmo navegador, e uma dependência nunca aceita o cookie do
  outro papel (o JWT carrega `role`).
- **JWT HS256** assinado com `SECRET_KEY` (≥ 32 caracteres, validado na configuração), com
  `sub`, `role`, `iat` e `exp` (`JWT_EXPIRE_MINUTES`, padrão 12 h). Sem refresh token no M1:
  ao expirar, o usuário loga de novo.
- A dependência `current_reseller` reavalia `is_blocked`/`expires_at` **em toda requisição**;
  bloquear ou vencer uma revenda invalida a sessão imediatamente (403 com mensagem em português).

### CSRF
- **Double-submit cookie**: no login a API grava um cookie `csrf_token` legível por JavaScript
  e devolve o mesmo valor no JSON. Toda requisição autenticada por cookie com método diferente
  de `GET/HEAD/OPTIONS` precisa enviar `X-CSRF-Token` igual ao cookie; caso contrário, 403
  `csrf`. `SameSite=Lax` é a segunda camada.
- O painel lê o cookie no momento de cada chamada (`web/src/lib/api.ts`), então trocar de
  sessão em outra aba não deixa um token velho em memória.

### Dispositivos
- `POST /device/register` gera um token de 32 bytes aleatórios (hex), devolve-o **uma única
  vez** e guarda apenas o SHA-256 (`devices.token_hash`). Rechamar `register` com o mesmo
  `device_id` rotaciona o token (reinstalação do app) e mantém o MAC.
- O `device_id` enviado pelo app (ANDROID_ID) é armazenado como SHA-256, não em claro.
- Autenticação por `Authorization: Bearer <token>`; sem cookie, sem CSRF.

### Senhas e segredos
- **argon2id** (`argon2-cffi`) para admins e revendas. A senha da revenda nunca é devolvida
  (correção do risco §10 da `spec-painel.md`).
- **Fernet** (`cryptography`) com `FERNET_KEY` para a senha das playlists Xtream. A URL é
  armazenada sem o parâmetro `password` e remontada só na entrega ao app.

### Rate limit de login
- Chave Redis `login:<papel>:<ip>:<usuário>`, incrementada a cada falha com TTL
  `LOGIN_RATE_WINDOW`; ao atingir `LOGIN_RATE_LIMIT` responde 429 antes de consultar o banco.
  Sucesso zera o contador. O IP vem de `X-Forwarded-For` (Caddy) ou do socket.

### Bibliotecas adicionadas (além da stack do `CLAUDE.md`)
| Pacote | Motivo |
|---|---|
| `pyjwt` | Implementação mínima e mantida de JWT; evita `python-jose` (sem manutenção ativa) |
| `argon2-cffi` | Exigido pelo `CLAUDE.md` (argon2) |
| `cryptography` | Fernet, exigido pelo plano (M1 §4) |
| `python-multipart` | Dependência do FastAPI para formulários; usada pelo webhook do M2 |
| `@types/node` (web) | Tipos do Node para `vite.config.ts` (proxy de dev) |

## Consequências

- O painel e a API precisam estar no **mesmo domínio** (Caddy roteia `/api`). Para um
  domínio separado seria necessário `CORS_ORIGINS` e `SameSite=None`; não é o cenário previsto.
- Trocar `SECRET_KEY` derruba todas as sessões; trocar `FERNET_KEY` exige migrar os dados.
- O token do dispositivo não expira; a revogação é feita pelo vínculo (excluir o device ou
  a revenda) ou por um novo `register`.
