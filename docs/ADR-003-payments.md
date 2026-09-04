# ADR-003 — Pagamentos Pix (renovação da revenda)

**Status:** aceito · **Data:** 2026-09-04

## Contexto

A renovação da revenda é paga por Pix (Anexo I §2.2 e §2.4). O `CLAUDE.md` fixa o Mercado
Pago como provedor inicial, abstraído em `services/payments/` para permitir troca, e exige que
preços e regras fiquem só no servidor.

## Decisões

### Abstração de provedor
- `services/payments/base.py` define o protocolo `PaymentProvider` (`create_pix`,
  `get_payment`, `verify_webhook`) e os dataclasses `PixCharge`/`ProviderPayment` com o
  status normalizado `pending | approved | cancelled | expired`.
- Implementações: `mercadopago.py` (API de Pagamentos, Pix) e `fake.py` (memória; usado nos
  testes e em desenvolvimento com `PAYMENT_PROVIDER=fake`). O provedor é escolhido pelo
  `.env`; trocar de gateway é adicionar um módulo e alterar a variável.
- HTTP com `httpx` (já usado nos testes; passa a ser dependência de runtime). Não usamos o
  SDK oficial do Mercado Pago para manter a superfície pequena e testável com
  `httpx.MockTransport`.

### Fluxo
1. `POST /reseller/billing/pix` recebe **apenas** `months` ou `package_id`. O valor é
   calculado no servidor a partir de `settings.monthly_price` / `settings.packages`.
2. Uma linha em `payments` é criada `pending` com `expires_at` (validade do QR,
   `PIX_EXPIRATION_MINUTES`, padrão 30) e só então a cobrança é criada no provedor. Se o
   provedor falhar, a linha é removida e a API responde 502 `provider_error`.
3. O painel faz polling em `GET /reseller/billing/pix/{id}` a cada 4 s. O polling consulta o
   provedor (`sync_payment`) — assim a aprovação funciona mesmo sem webhook (útil em
   desenvolvimento local, onde o Mercado Pago não alcança a máquina).
4. `POST /webhooks/mercadopago` valida o header `x-signature` (HMAC-SHA256 sobre
   `id:<data.id>;request-id:<x-request-id>;ts:<ts>;`) quando `MERCADOPAGO_WEBHOOK_SECRET`
   está configurado e **sempre** confirma o pagamento com `GET /v1/payments/{id}` antes de
   aprovar. Nunca aprova com base no corpo da notificação.
5. Aprovação (`approve_payment`) é idempotente: a segunda notificação não estende de novo.
   O vencimento é estendido a partir do vencimento atual se ele estiver no futuro, senão a
   partir de hoje; `previous_expires_at`/`new_expires_at` ficam gravados no pagamento e no
   `audit_log` (`payment.approved`, ator `system`).

### Regras de acesso
- Revenda **vencida** loga e acessa só `billing/*` e `profile/*` (dependência
  `current_reseller_allow_expired`); o restante responde 403 `reseller_expired`.
- Revenda **sem vencimento** (`expires_at = NULL`) não renova: 422 `no_expiration`.
  Só o admin define vencimento para ela.
- Revenda **bloqueada** não loga nem renova.

### E-mail do pagador
O Mercado Pago exige `payer.email`. A plataforma não coleta e-mail da revenda; é enviado
`revenda-<id>@<domínio da plataforma>`. Se o gateway passar a exigir e-mail real, adiciona-se
a coluna em `resellers` (fora do escopo do M2).

## Consequências

- O webhook precisa ser cadastrado no painel do Mercado Pago apontando para
  `https://DOMINIO/api/v1/webhooks/mercadopago` (evento *Pagamentos*); a assinatura secreta
  exibida ali vai em `MERCADOPAGO_WEBHOOK_SECRET`. Ver `docs/DEPLOY.md`.
- Pagamentos aprovados pelo Mercado Pago depois de o QR ter sido marcado `expired` localmente
  não são reprocessados automaticamente (o sync só atua em `pending`); tratar manualmente pelo
  painel admin (ajuste de vencimento) se ocorrer.
