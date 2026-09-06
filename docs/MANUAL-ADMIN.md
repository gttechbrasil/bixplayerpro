# Manual do administrador

Para quem opera a plataforma: dono do negócio ou a pessoa técnica designada. O painel fica em
`https://<dominio>/admin`. Capturas de tela em [`screens/web/`](screens/web/).

## 1. Acesso

- Usuário e senha do administrador foram definidos na instalação (`ADMIN_USERNAME` /
  `ADMIN_PASSWORD` no `.env` do servidor). Para trocar a senha, altere `ADMIN_PASSWORD` no
  `.env` e rode o seed de novo (§8) — o seed **não** altera um admin já existente, então a
  troca é feita direto no banco (ver §8, "Trocar a senha do admin").
- Depois de 10 tentativas erradas no mesmo usuário o login fica bloqueado por 5 minutos.
- A sessão dura 12 horas. **Sair** encerra na hora.

## 2. Dashboard

Totais de revendedores (ativos, bloqueados, vencidos), dispositivos (cadastrados, ativos,
vistos nas últimas 24 h) e pagamentos do mês (quantidade, valor, pendentes).

## 3. Revendedores

**Revendedores → Nova revenda**: usuário (login), nome, senha inicial, vencimento e, se o
sistema de créditos estiver ativo, créditos iniciais. O revendedor recebe usuário e senha de
você e entra em `https://<dominio>/painel`.

Na ficha de cada revenda:

| Ação | O que faz |
|---|---|
| **Editar** | Nome, usuário, tema, logo/fundo/QR (também editáveis pela própria revenda) |
| **Vencimento** | Define ou remove a data. Revenda vencida só consegue entrar no painel para **renovar**; os apps dos clientes dela mostram "Expirado" e param de reproduzir |
| **Bloquear / Desbloquear** | Bloqueio é imediato: a revenda não entra e os apps dela mostram "Expirado". Use para inadimplência grave ou suspeita de conta comprometida |
| **Redefinir senha** | Nova senha para a revenda (ela pode trocar depois em *Perfil*) |
| **Créditos** | Ajuste manual (+/−) com motivo obrigatório. Cada linha vai para o histórico de créditos e para a auditoria. Só aparece com créditos ativos (§5) |
| **Dispositivos** | Lista somente leitura dos aparelhos da revenda (a URL da playlist **não** é exibida ao admin) |
| **Pagamentos** | Histórico de Pix da revenda |
| **Excluir** | Remove a revenda. Os dispositivos dela **ficam** no banco, desvinculados, e podem ser cadastrados de novo por outra revenda com o mesmo MAC. Pagamentos e auditoria são preservados |

## 4. Créditos, vencimentos e cobrança

Dois modelos, escolhidos em **Configurações**:

- **Sem créditos** (padrão): a revenda paga uma mensalidade (Pix) que estende o **vencimento**
  dela; cadastra quantos dispositivos quiser enquanto estiver em dia.
- **Com créditos**: além do vencimento, cada dispositivo cadastrado consome 1 crédito. Você
  vende créditos por fora e lança o ajuste manual na ficha da revenda.

A renovação por Pix é feita pela própria revenda em **Renovar**: escolhe 1 mês (preço mensal)
ou um pacote; o QR Code vale 30 minutos; ao ser pago, o gateway avisa a plataforma (webhook) e
o vencimento é estendido na hora, a partir da data atual de vencimento (ou de hoje, se já
venceu). Tudo isso fica em **Pagamentos** e em **Auditoria**.

## 5. Configurações

| Campo | Efeito |
|---|---|
| **Nome da plataforma** | Título do painel e do app |
| **Sistema de créditos** | Liga/desliga o modelo com créditos (§4) |
| **Preço mensal** | Valor de 1 mês na tela *Renovar* |
| **Pacotes** | Combinações meses × valor com desconto (ex.: 3 meses por R$ 90) |
| **Versão mínima do app** | Apps com versão menor mostram a tela "Atualização disponível" no boot |
| **Link do APK** | Endereço do botão "Atualizar" (normalmente `https://<dominio>/downloads/app.apk`) |
| **Gateway** (somente leitura) | Provedor, token mascarado e se é de **teste** ou **produção**, URL do webhook. Os segredos ficam só no `.env` do servidor (§8) |

## 6. Pagamentos e auditoria

- **Pagamentos**: todos os Pix, com filtro por status (pendente, aprovado, cancelado,
  expirado), revenda e período. Um Pix pendente que o cliente diz ter pago: abra a linha e
  confira o `provider_id`; a consulta ao gateway acontece automaticamente quando a revenda
  abre a tela de renovação (polling) ou quando o webhook chega (§9).
- **Auditoria**: quem fez o quê, quando e de que IP — criação/exclusão de revendas e
  dispositivos, ajustes de crédito, mudanças de vencimento, migrações de DNS, pagamentos,
  alterações de configuração e de personalização.

## 7. Publicar uma nova versão do app

1. Gere o release assinado (`docs/ANDROID.md` §7) — sempre com o **mesmo keystore**.
2. Publique: `./deploy/deploy.sh --apk android/app/build/outputs/apk/release/app-universal-release.apk`.
   O arquivo passa a valer em `https://<dominio>/downloads/app.apk`.
3. Em **Configurações**, atualize a **versão mínima** só quando quiser forçar todos a
   atualizar (quem estiver abaixo verá a tela de atualização e não usará o app até instalar).

## 8. Servidor: operação do dia a dia

Comandos no VPS (usuário `deploy`, pasta `/home/deploy/app`). Detalhes em
[`DEPLOY.md`](DEPLOY.md).

```bash
# estado e logs
docker compose -f deploy/docker-compose.yml --env-file deploy/.env ps
docker compose -f deploy/docker-compose.yml --env-file deploy/.env logs -f --tail=100 api web caddy

# atualizar a plataforma (a partir da sua máquina, com o repositório)
./deploy/deploy.sh

# backup manual e onde ficam os automáticos (diários, 03:30, 7 dias)
/home/deploy/backup.sh && ls -lh /home/deploy/backups

# reexecutar o seed (idempotente: cria admin e configurações padrão se faltarem)
docker compose -f deploy/docker-compose.yml --env-file deploy/.env exec api python -m app.db.seed
```

**Trocar a senha do admin** (o seed não sobrescreve um admin existente):

```bash
docker compose -f deploy/docker-compose.yml --env-file deploy/.env exec api python -c \
 "from app.core.security import hash_password; print(hash_password('NOVA-SENHA'))"
# copie o hash e grave no banco:
docker compose -f deploy/docker-compose.yml --env-file deploy/.env exec -T db \
 sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
 -c "update admins set password_hash='<hash>' where username='admin';"
```

Atualize também `ADMIN_PASSWORD` no `.env` para o seed de uma instalação futura.

**Restaurar um backup**: procedimento e teste em `DEPLOY.md` §6 (restauração ensaiada em
06/09/2026 em banco temporário).

**Monitoramento**: a cada 5 minutos o VPS confere `https://<dominio>/api/v1/health`. Falhas
e recuperações vão para `/home/deploy/healthcheck.log` e, se `/home/deploy/alert.env` tiver
`TELEGRAM_BOT_TOKEN`/`TELEGRAM_CHAT_ID` (ou `ALERT_EMAIL`), chegam por Telegram/e-mail.

## 9. Quando o webhook do Pix falhar

Sintoma: a revenda pagou, o Mercado Pago mostra "aprovado", mas o painel continua
"pendente".

1. A revenda abre **Renovar** de novo: a tela consulta o gateway (polling) e aprova sozinha se
   o pagamento estiver aprovado lá. Isso resolve a maioria dos casos.
2. Se não resolver, confira no painel do Mercado Pago (**Suas integrações → Webhooks**) se a
   URL é `https://<dominio>/api/v1/webhooks/mercadopago`, se o evento *Pagamentos* está
   marcado e se a **assinatura secreta** é a mesma de `MERCADOPAGO_WEBHOOK_SECRET` no `.env`.
   Erros 403 `invalid_signature` nos logs da API indicam segredo divergente.
3. Reenvie a notificação pelo painel do Mercado Pago ou peça à revenda para abrir *Renovar*.
4. Se o pagamento expirou no gateway antes de ser pago, ele aparece como **expirado**; a
   revenda gera um novo Pix.

Nunca aprove um pagamento "na mão" no banco: o vencimento é calculado pela plataforma a
partir do registro do gateway.

## 10. Segurança básica

- Nunca compartilhe o `.env` do servidor; ele contém as chaves do gateway e a chave que
  cifra as senhas das playlists (`FERNET_KEY` — se perdida, as playlists Xtream precisam ser
  recadastradas).
- Troque a senha do admin de desenvolvimento antes de entregar acessos (§8).
- Revenda com comportamento suspeito: **bloqueie** primeiro, investigue depois (o bloqueio
  derruba as sessões abertas dela na próxima requisição).
- Mantenha o servidor atualizado: `docker compose pull && ./deploy/deploy.sh` de tempos em tempos.
