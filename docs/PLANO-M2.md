# Marco 2 — Dashboard do Revendedor + Pix

Execute em ordem. Cada bloco termina com testes verdes e commit. Reutilize os componentes do painel admin; não duplique. Não avance para o M3.

## 0. Ajuste de escopo: créditos opcionais
- [x] Adicionar `credits_enabled` (bool, padrão `false`) em `settings`, editável no painel admin
- [x] Com `credits_enabled=false`: cadastro de device não consome crédito, saldo não é exibido nem bloqueia, ledger não é escrito. Com `true`: comportamento atual do ADR-001
- [x] Atualizar ADR-001 com a decisão; testes cobrindo os dois modos

## 1. API do revendedor — dispositivos
- [ ] `GET /api/v1/reseller/devices` — busca por MAC ou nome, paginação (10/25/50/100), ordenação por criação desc
- [ ] `POST /api/v1/reseller/devices` — vincula um MAC já registrado pelo app **ou** cria um device manual com MAC informado. Campos: `mac_address`, `client_name`, `playlist_name`, `playlist_url`, `license_expires_at` (padrão 2050-01-01). Valida formato do MAC e da URL; parser Xtream do M1
- [ ] `GET/PUT /api/v1/reseller/devices/{id}` — edição dos mesmos campos
- [ ] `DELETE /api/v1/reseller/devices/{id}` e `POST /api/v1/reseller/devices/batch-delete` (`ids[]`)
- [ ] Regra: um MAC pertence a um único revendedor; tentativa de cadastrar MAC de outro revendedor → 409 com mensagem em português
- [ ] Todo cadastro/edição/exclusão em `audit_log`

## 2. API do revendedor — migrador de DNS
- [ ] `GET /api/v1/reseller/dns` — hosts distintos em uso nas playlists do revendedor, com contagem de playlists por host
- [ ] `POST /api/v1/reseller/dns/migrate` — `{from_host, to_host}`; substitui o host em todas as playlists do revendedor; retorna quantidade afetada; `from == to` → 422; auditoria com contagem

## 3. API do revendedor — personalização
- [ ] `GET/PUT /api/v1/reseller/branding` — `logo_url`, `bg_url`, `qr_content`, `theme` (enum dos 2 layouts da v1: `default`, `grid`), `auto_ads`
- [ ] Upload de imagem: `POST /api/v1/reseller/branding/upload` (logo ou bg), PNG/JPG/WebP até 2 MB, salvo em volume local `/uploads` servido pelo Caddy; URL externa continua aceita
- [ ] CRUD `banners` (`title`, `url`, `is_active`), máximo 10 por revendedor
- [ ] `GET/PUT /api/v1/reseller/profile` — nome (readonly), username (readonly), troca de senha com senha atual

## 4. Pagamentos Pix
- [ ] `services/payments/base.py` (interface) + `mercadopago.py`; provedor escolhido por env
- [ ] `GET /api/v1/reseller/billing/plans` — preço mensal e pacotes de `settings`
- [ ] `POST /api/v1/reseller/billing/pix` — `{months}` ou `{package_id}`; valor calculado **no servidor**; cria `payments` pendente; retorna `payment_id`, `qr_code` (copia-e-cola), `qr_base64`, `expires_at`
- [ ] `GET /api/v1/reseller/billing/pix/{payment_id}` — status para polling
- [ ] `POST /api/v1/webhooks/mercadopago` — valida assinatura, idempotente por `provider_id`; ao aprovar: marca pago, estende `expires_at` (a partir do vencimento atual se futuro, senão de hoje), auditoria
- [ ] `GET /api/v1/reseller/billing/history`
- [ ] Modo sandbox documentado em `docs/DEPLOY.md`; teste unitário do webhook com payload de exemplo do Mercado Pago

## 5. Dashboard do revendedor (SvelteKit `/painel`)
- [ ] Login do revendedor; layout com sidebar: card de vencimento (estados ok / vence em ≤7 dias / vencido), Dispositivos, Migrador de DNS, Logomarca, Background, Layout, Banners, QR Code, Perfil, Sair; header com créditos (só se `credits_enabled`) e tema
- [ ] **Dispositivos:** DataTable com busca, paginação, seleção em lote, criar/editar em página própria, exclusão com ConfirmDialog, exclusão em lote
- [ ] **Migrador de DNS:** select de origem populado da API + destino + resultado com contagem
- [ ] **Personalização:** logo e background com pré-visualização e upload ou URL; layout com cards de pré-visualização dos 2 temas e selo "Ativo"; banners com tabela, modal de novo, toggle ativo, exclusão, toggle "banners automáticos"; QR Code
- [ ] **Renovação:** modal aberto pelo card de vencimento — escolha de meses (1–60) ou pacote, total, botão gerar; exibe QR + copia-e-cola com botão copiar; polling a cada 4 s; sucesso mostra novo vencimento e recarrega. Fechar o modal não cancela
- [ ] **Perfil:** troca de senha
- [ ] Revendedor vencido: acesso somente ao modal de renovação e ao perfil; demais rotas redirecionam com aviso

## 6. Painel admin — complementos
- [ ] Configurações: toggle `credits_enabled`, credenciais do gateway (mascaradas), pacotes promocionais (CRUD inline)
- [ ] Detalhe da revenda: lista dos devices dela (somente leitura) e histórico de pagamentos

## 7. Fechamento do M2
- [ ] `docs/API.md` regenerado; `docs/DEPLOY.md` com configuração do Mercado Pago e do volume de uploads
- [ ] Testes: fluxo completo revendedor (login → cadastrar device → app `config` retorna a playlist → migrar DNS → `config` reflete o novo host → gerar Pix → webhook aprova → `expires_at` estendido)
- [ ] Cobertura ≥ 80%; `svelte-check` sem erros; build OK

## Ao concluir
Apresente o que foi entregue, pendências e ADRs novos. Então aguarde `docs/PLANO-M3.md` (app Android TV).
