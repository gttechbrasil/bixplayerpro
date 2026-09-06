# Marco 5 — Testes integrados, homologação, documentação e entrega

Blocos marcados **[Gustavo]** dependem de mim; os demais são seus. Nada de funcionalidade nova neste marco — só correção, endurecimento e entrega. Correções entram por issue em `docs/M5-ISSUES.md` (id, origem, gravidade, status).

## 0. Limpeza
- [ ] Apagar o device não vinculado de produção (`02:50:50:07:22:08`) e qualquer outro resíduo de teste no banco de produção; listar o que foi removido — **pendente de você**: levantamento feito (06/09/2026): 2 devices avulsos sem revenda e sem playlist (`02:50:50:2B:BC:8A` id 2, `02:50:50:07:22:08` id 3); resíduos de demonstração (`revenda01` "Revenda Demonstração", device 1 `AA:BB:CC:00:11:22` "Cliente Demo" com playlist `http://servidor.exemplo`, 2 Pix pendentes do sandbox de R$ 35,00) ficam para o bloco 3 junto com a revenda de teste. A sessão do Claude Code não tem permissão para escrever no banco de produção; rode no VPS:
  ```bash
  cd /home/deploy/app && docker compose -f deploy/docker-compose.yml --env-file deploy/.env exec -T db \
    sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "delete from devices where id in (2,3) and reseller_id is null returning id, mac_address"'
  ```
- [x] Remover `AGENTS.md` da raiz (é cópia; o `CLAUDE.md` é a fonte). Registrar em `.gitignore` se algo o recriar — removido e adicionado ao `.gitignore`
- [x] Revisar `.env.example` e `docs/DEPLOY.md`: toda variável documentada, nenhuma sobrando — conferido variável a variável contra `app/core/config.py`; acrescentadas as `DEVICE_*`

## 1. Validação em hardware real **[Gustavo]**
- [ ] Instalar o release 1.1.0 em: 1 TV box de entrada (Android 9–11, 2 GB), 1 Android TV (Google TV/Chromecast ou TV com Android) e 1 celular Android
- [ ] Roteiro em `docs/HOMOLOGACAO.md` (bloco 4) executado em cada aparelho; anotar: tempo de sync da lista real, canais que caíram no VLC, travamentos, foco perdido, textos cortados
- [ ] Enviar ao Claude Code o `adb logcat` de qualquer falha; ele abre issue e corrige

## 2. Endurecimento do backend
- [x] Rate limit em `/device/register` e `/device/config` (por IP e por device) — o endpoint é público — `app/core/ratelimit.py`, por IP e por dispositivo, 429 com `Retry-After`, 5 testes
- [x] Headers de segurança no Caddy (HSTS, X-Content-Type-Options, X-Frame-Options, CSP mínima para o web) — HSTS, `Permissions-Policy`, CSP no painel, corpo ≤ 8 MB em `/api`
- [x] Teste de carga: 2.000 devices fazendo `config` em 60 s (locust ou k6) — p95 < 300 ms; corrigir gargalos (índices, N+1) — `scripts/loadtest_device_config.py`: p50 12 ms, **p95 16 ms**, p99 17 ms, 0 erros; sem gargalo a corrigir (`SECURITY-REVIEW.md` §4)
- [x] Revisão de segurança: enumerar cada rota e conferir auth, escopo por reseller, validação de entrada; corrigir o que encontrar e listar em `docs/SECURITY-REVIEW.md` — `docs/SECURITY-REVIEW.md`; achados M5-001…006 em `M5-ISSUES.md`
- [x] Monitoramento mínimo: `docker compose` com restart policy, healthcheck da API, alerta por e-mail/Telegram quando o health falhar (cron simples no VPS), rotação de logs confirmada — `deploy/healthcheck-alert.sh` instalado no cron (5 min); alerta por Telegram/e-mail assim que você criar `/home/deploy/alert.env` (token do bot e chat id) — até lá só grava em `healthcheck.log`
- [x] Backup: testar a **restauração** do pg_dump em um banco vazio e documentar — ensaio feito em banco temporário no VPS em 06/09/2026, contagens conferidas (`deploy/restore-drill.sh`, `DEPLOY.md` §6)

## 3. Produção de verdade **[Gustavo + Claude Code]**
- [ ] Trocar o Mercado Pago para credenciais de produção: aplicação ativada, token `APP_USR` de produção, webhook recadastrado em modo produtivo, nova assinatura no `.env`, um Pix real de R$ 1 (preço temporário nas configurações) aprovado de ponta a ponta, preço restaurado
- [ ] Domínio e marca definitivos do cliente: nome da plataforma, logo, cores, `applicationId` e nome do app via `gradle.properties`; novo release assinado com o **mesmo keystore**
- [ ] Contas: admin do cliente criado, senha do admin de desenvolvimento trocada, revenda de teste removida
- [ ] Deploy final no VPS definitivo (se diferente do atual): `deploy.sh` do zero, migrações, DNS, TLS, webhook, backup e monitoramento ativos

## 4. Homologação com o cliente **[Gustavo]**
- [x] Escrever `docs/HOMOLOGACAO.md`: roteiro passo a passo cobrindo cada item da Seção 2 do Anexo I (admin → criar revenda → revenda cadastra device → app ativa → TV/filmes/séries/guia → PIN → personalização reflete no app → migrador de DNS → Pix → renovação), com campo "OK / Falha / Observação" por item — escrito (A1–A15 admin, B1–B22 revenda, C1–C25 app, D1–D8 backend, E encerramento)
- [ ] Sessão de homologação com o cliente usando o roteiro; falhas viram issues; itens fora do Anexo I viram lista de "fase 2" separada
- [ ] Aceite por escrito (e-mail ou assinatura no roteiro) — dispara a garantia de 60 dias

## 5. Documentação de entrega
- [x] `docs/MANUAL-ADMIN.md` — operar a plataforma: criar revendas, créditos, vencimentos, preços, gateway, publicar nova versão do app (`deploy.sh --apk`), backup/restauração, o que fazer quando o webhook falhar
- [x] `docs/MANUAL-REVENDA.md` — para os revendedores do cliente: cadastrar dispositivo, migrar DNS, personalizar, renovar; com capturas de tela — capturas em `docs/screens/web/`
- [x] `docs/MANUAL-APP.md` — instalação do APK na TV (Downloader/QR), ativação, uso básico; 1 página, para o cliente final
- [x] `README.md` do repositório: visão geral, como rodar local, como fazer deploy, onde está cada documento
- [x] Revisar `docs/API.md`, `ANDROID.md`, `DEPLOY.md` contra o estado final — API: 429 nos endpoints do app; DEPLOY: `DEVICE_*`, monitoramento, ensaio de restauração, `deploy.sh`; ANDROID revisado no fechamento do M4

## 6. Entrega
- [ ] Repositório transferido ou acesso concedido conforme o contrato; tags `v1.0.0` (web/backend) e `app-v1.1.x` (Android)
- [ ] Pacote de entrega: APKs assinados (universal + por ABI), keystore e `keystore.properties` entregues ao cliente por canal seguro **[Gustavo]**, credenciais de admin, `.env` de produção (sem o de dev)
- [ ] `docs/FASE-2.md`: tudo que ficou fora (Seção 3 do Anexo I + pedidos surgidos na homologação) com estimativa por item, para a próxima proposta

## Ao concluir
Apresente o relatório final: issues abertas e fechadas no M5, resultado do teste de carga, revisão de segurança, e o estado do checklist de entrega.
