# Roteiro de homologação — v1.0

Roteiro para a sessão de homologação com o cliente, cobrindo cada item da **Seção 2 do
Anexo I** (Especificação Funcional). Preencha a coluna **Resultado** com `OK`, `Falha` ou
`N/A` e use **Observação** para qualquer desvio. Toda `Falha` vira uma issue em
[`M5-ISSUES.md`](M5-ISSUES.md); pedidos que não constam do Anexo I vão para a lista de
[fase 2](FASE-2.md), separada.

> Preparo: ambiente de produção no ar (`https://<dominio>`), release do app instalado em uma
> Android TV/TV box e em um celular (`https://<dominio>/downloads/app.apk`), uma playlist de
> teste válida fornecida pela CONTRATANTE (Xtream **e** M3U, se possível) e o gateway Pix
> configurado. Anote a versão do app exibida em **Configurações → Versão**.

| Campo | Valor |
|---|---|
| Data / hora | |
| Participantes | |
| Domínio homologado | |
| Versão do app | |
| Aparelhos usados | |

---

## A. Painel administrativo (Anexo I, 2.1)

Acesse `https://<dominio>/admin`.

| # | Passo | Resultado esperado | Resultado | Observação |
|---|---|---|---|---|
| A1 | Entrar com usuário e senha de administrador | Login aceito; dashboard aberto | | |
| A2 | Errar a senha 10 vezes seguidas | Bloqueio temporário (mensagem "Muitas tentativas") | | |
| A3 | Dashboard: conferir totais | Total de revendedores, dispositivos ativos e pagamentos do mês visíveis e coerentes | | |
| A4 | **Revendedores → Nova revenda**: usuário, nome, senha, vencimento | Revenda criada e listada | | |
| A5 | Editar a revenda (nome, usuário) | Alteração salva e refletida na lista | | |
| A6 | Definir/alterar a **data de vencimento** da revenda | Data salva; status muda para "vencida" quando a data é no passado | | |
| A7 | **Créditos**: ativar o sistema de créditos em Configurações e fazer um ajuste manual (+5) com motivo | Saldo atualizado; movimento aparece no histórico de créditos | | |
| A8 | Bloquear a revenda e tentar entrar no `/painel` com ela | Login recusado com "Revenda bloqueada"; desbloquear em seguida | | |
| A9 | Excluir uma revenda de teste | Revenda some da lista; dispositivos dela ficam desvinculados | | |
| A10 | **Configurações**: nome da plataforma, preço mensal, pacotes (meses × valor) | Salvos; nome aparece no título do painel e no app | | |
| A11 | Configurações: **versão mínima do app** e **link de atualização (APK)** | Salvos; app abaixo da versão mínima mostra a tela de atualização (ver C20) | | |
| A12 | Configurações: **gateway** exibe token mascarado e tipo (teste/produção) | Somente leitura, token nunca aparece inteiro | | |
| A13 | **Pagamentos**: histórico com filtros (status, revenda, período) | Lista coerente com os Pix gerados na seção B | | |
| A14 | **Auditoria**: filtrar por ação (`device.create`, `dns.migrate`, `payment.`) | Registros presentes com ator, alvo, IP e data | | |
| A15 | Sair e tentar abrir uma página do admin | Redirecionado ao login | | |

## B. Dashboard do revendedor (Anexo I, 2.2)

Acesse `https://<dominio>/painel` com a revenda criada em A4.

| # | Passo | Resultado esperado | Resultado | Observação |
|---|---|---|---|---|
| B1 | Login da revenda | Dashboard aberto com créditos e vencimento visíveis | | |
| B2 | **Perfil → alterar a própria senha**; sair e entrar com a nova | Nova senha aceita; a antiga recusada | | |
| B3 | **Dispositivos → Novo**: MAC exibido pelo app (seção C2), nome do cliente, nome e URL da playlist (Xtream) | Dispositivo criado; 1 crédito debitado se créditos ativos | | |
| B4 | Repetir com uma playlist **M3U** em outro dispositivo (ou celular) | Tipo detectado como M3U | | |
| B5 | Cadastrar o mesmo MAC de novo | Recusado ("já está cadastrado na sua conta") | | |
| B6 | Buscar por MAC e por nome; paginar | Busca e paginação funcionam | | |
| B7 | Editar o dispositivo (nome do cliente, playlist, vencimento) | Alterações salvas; app recebe a nova playlist ao atualizar | | |
| B8 | Selecionar 2+ dispositivos e **excluir em lote**; excluir 1 individualmente | Excluídos; somem da lista e o app volta para a tela de ativação | | |
| B9 | **Migrador de DNS**: listar hosts em uso; migrar `http://servidor-antigo` → `http://servidor-novo` | Contagem de playlists afetadas correta; URLs atualizadas; registro na auditoria | | |
| B10 | Migrar informando origem = destino | Recusado com mensagem clara | | |
| B11 | **Personalização → Logomarca**: enviar PNG/JPG (até 2 MB) | Logo salva e exibida no painel | | |
| B12 | Enviar um arquivo que não é imagem ou > 2 MB | Recusado com mensagem clara | | |
| B13 | **Background**: enviar imagem de fundo | Salva | | |
| B14 | **QR Code**: informar conteúdo (link/WhatsApp) | Salvo | | |
| B15 | **Banners**: cadastrar por URL, ativar/desativar, editar, excluir | Lista reflete cada ação; máximo de 10 | | |
| B16 | **Layout**: alternar entre os 2 layouts da tela inicial | Salvo (efeito no app em C19) | | |
| B17 | Alternar **tema claro/escuro** e recarregar a página | Preferência mantida | | |
| B18 | **Renovar**: ver preço mensal e pacotes; gerar Pix para 1 mês | QR Code e código copia-e-cola exibidos, com validade | | |
| B19 | Pagar o Pix (valor real, preço temporário definido em A10) | Status muda para "aprovado" sem recarregar; **vencimento estendido** em 1 mês; e-mail/comprovante do gateway recebido | | |
| B20 | Histórico de pagamentos da revenda | Pix aparece como aprovado; também em A13 | | |
| B21 | Revenda **vencida** (vencimento no passado, definido em A6) entra no painel | Consegue entrar **somente** para renovar/perfil; demais telas bloqueadas; app mostra "expirado" (C4) | | |
| B22 | Revenda **bloqueada** (A8) | Não entra | | |

## C. Aplicativo (Anexo I, 2.3)

Instale o APK (`https://<dominio>/downloads/app.apk`) em uma Android TV/TV box **e** em um
celular. Repita C1–C4 e C6–C9 nos dois; o restante pode ser feito só na TV, salvo indicação.

| # | Passo | Resultado esperado | Resultado | Observação |
|---|---|---|---|---|
| C1 | Abrir o app pela primeira vez | Tela de ativação com **MAC** grande e QR Code | | |
| C2 | (Celular) **Copiar MAC** e **Compartilhar** | MAC vai para a área de transferência / WhatsApp | | |
| C3 | Cadastrar o MAC no painel (B3) e tocar em **"Já cadastrei — verificar"** | App carrega a playlist automaticamente e vai para a tela inicial (TV) ou TV ao vivo (celular). Anotar o **tempo do sync** da lista real: ____ s | | |
| C4 | Tela inicial: **status** (Ativo) e **data de vencimento** | Exibidos; com a revenda vencida (B21) aparece "Expirado" e a reprodução é bloqueada | | |
| C5 | **Logo, fundo, banners e QR Code** do revendedor (B11–B15) | Todos exibidos conforme configurado | | |
| C6 | **TV ao vivo**: categorias, lista, prévia, reprodução em tela cheia (OK) | Reproduz; zapping com ↑/↓; dígitos sintonizam pelo número | | |
| C7 | **Favoritos** (MENU na lista) e categoria "Favoritos" | Favorito marcado/desmarcado; lista de favoritos correta | | |
| C8 | **Busca** de canal (TV: OK na linha de busca; celular: teclado nativo) | Resultados filtrados enquanto digita | | |
| C9 | **Faixas**: MENU no player em um canal com mais de um áudio/legenda | Seleção aplicada | | |
| C10 | **Filmes**: categorias, grade com capas, busca, ordenação | Grade preenchida; capa ou inicial quando sem imagem | | |
| C11 | Detalhe do filme → **Assistir**; sair no meio; voltar | Botão vira "Continuar de HH:MM"; **Continuar assistindo** aparece na tela inicial | | |
| C12 | Avançar/voltar com ←/→ (TV) e arrastar/duplo toque (celular) | Seek responde; barra de progresso correta | | |
| C13 | **Séries**: temporadas, episódios, reproduzir; deixar terminar | Contagem de 10 s e **próximo episódio** automático; progresso por episódio | | |
| C14 | **EPG**: canal com guia (playlist Xtream ou M3U com `url-tvg`) | "Agora/Depois" na lista; **Guia** (grade) abre pelo botão; OK no programa atual toca o canal | | |
| C15 | **Formatos**: um canal HLS (`.m3u8`), um TS e um filme MP4 | Todos reproduzem | | |
| C16 | **Fallback**: canal que o player principal não reproduz | Após as tentativas, troca para o player secundário com aviso "Modo de compatibilidade"; anotar quais canais caíram no fallback | | |
| C17 | **Múltiplas playlists**: Configurações → Trocar playlist (adicionar uma segunda e alternar) | Lista trocada e ressincronizada | | |
| C18 | **Controle parental**: Configurações → Controle parental → definir PIN; bloquear uma categoria; ocultar outra | Categoria bloqueada pede PIN; oculta some das listas, busca e guia; playlist protegida pede PIN ao abrir | | |
| C19 | **Layout**: trocar no painel (B16) e atualizar o app (Configurações → Atualizar listas ou reabrir) | Tela inicial muda para o outro layout | | |
| C20 | **Idioma**: alternar PT/EN/ES | Textos trocam imediatamente | | |
| C21 | Configurações: **Limpar cache**, **Período de atualização automática** | Executam sem erro | | |
| C22 | **Atualização**: definir versão mínima acima da instalada (A11) e reabrir o app | Tela "Atualização disponível" com botão que abre o link do APK; restaurar a versão mínima depois | | |
| C23 | (Celular) girar a tela, sair para a tela inicial durante a reprodução, voltar | Player em paisagem tela cheia; **PiP** ao sair; volta reproduzindo | | |
| C24 | (TV) navegar todas as telas só com o **controle remoto** | Nenhum foco perdido; nenhum texto cortado; VOLTAR sempre sai da tela atual | | |
| C25 | Deixar o app 30 min em um canal | Sem travamento; horário/EPG atualizados | | |

## D. Backend e operação (Anexo I, 2.4)

| # | Passo | Resultado esperado | Resultado | Observação |
|---|---|---|---|---|
| D1 | Abrir `https://<dominio>` e `https://www.<dominio>` | HTTPS válido; `www` redireciona | | |
| D2 | `https://<dominio>/api/v1/health` | `{"status":"ok","database":"ok","redis":"ok"}` | | |
| D3 | Chamar uma rota do painel sem login (ex.: `/api/v1/admin/dashboard`) | 401 | | |
| D4 | Com a revenda A logada, tentar acessar um dispositivo da revenda B pelo id | 404 (nunca vaza dado de outra revenda) | | |
| D5 | Auditoria contém: criação/exclusão de dispositivo, migração de DNS, pagamento aprovado | Registros presentes (A14) | | |
| D6 | Webhook: o Pix de B19 foi confirmado **sem** recarregar a página e consta em Auditoria | Confirmado via webhook (não só polling) | | |
| D7 | Backup: existe `db-*.sql.gz` do dia em `/home/deploy/backups` | Arquivo presente; restauração testada (ver `DEPLOY.md`) | | |
| D8 | Monitoramento: derrubar o container da API por 1 min | Alerta recebido (Telegram/e-mail) e alerta de recuperação | | |

## E. Encerramento

| Item | |
|---|---|
| Falhas registradas como issues (ids) | |
| Pedidos fora do Anexo I (levados para `FASE-2.md`) | |
| **Aceite** (nome, data, assinatura ou e-mail de confirmação) | |

O aceite por escrito encerra o M5 e inicia a **garantia de 60 dias** prevista no Anexo I.
