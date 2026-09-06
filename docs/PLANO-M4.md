# Marco 4 — App: filmes, séries, EPG, PIN, layout 2, celular, VLC

Execute em ordem. Cada bloco termina com build verde, testes unitários verdes, validação no AVD de TV e commit. Não avance para o M5.

## 0. Ajustes herdados do M3
- [x] Zapping no player deve atualizar o foco lembrado da lista de canais
- [ ] Registrar em `docs/ANDROID.md` os resultados do teste em TV box física quando eu os enviar (codecs, desempenho, quadro de transição HLS→TS)

## 1. Filmes (VOD)
- [x] Cliente Xtream: `get_vod_categories`, `get_vod_streams`, `get_vod_info`; M3U: entradas cujo `group-title` ou URL indiquem filme (extensão `.mp4/.mkv/.avi`) vão para VOD
- [x] Room: `movies`, `movie_categories`, `watch_progress` (por playlist, item, posição, duração, atualizado_em)
- [x] `MoviesScreen`: categorias à esquerda, grade de capas (Coil, placeholder, 6 colunas em 1080p) com Paging 3; foco lembrado; ordenação (recentes / A–Z); busca por nome
- [x] `MovieDetailScreen`: capa, título, ano, gênero, duração, sinopse e elenco quando o provedor fornecer; "Assistir" / "Continuar de HH:MM"; favoritar
- [x] Player VOD: seek com ←→ (10 s, acelerando ao segurar), barra de progresso no overlay, salva posição a cada 10 s e ao sair, "continuar assistindo" na home
- [x] Sync de 20.000 filmes sem travar a UI (teste com fixture gerada)

## 2. Séries
- [x] Cliente Xtream: `get_series_categories`, `get_series`, `get_series_info` (temporadas/episódios); M3U: agrupar por padrão `Nome SxxExx`
- [x] Room: `series`, `series_categories`, `episodes` (carregados sob demanda ao abrir a série)
- [x] `SeriesScreen` igual à de filmes; `SeriesDetailScreen`: seletor de temporada, lista de episódios com progresso, "Continuar" no último episódio assistido
- [x] Player: ao terminar um episódio, oferecer o próximo (contagem regressiva 10 s, cancelável)

## 3. EPG
- [x] Xtream: `get_short_epg` para o painel de prévia (agora/próximo) e `xmltv.php` para a grade; M3U: `url-tvg` do cabeçalho `#EXTM3U` quando presente
- [x] Parser XMLTV em streaming (SAX/XmlPullParser), gravando em Room `epg_programs` com janela de −6 h a +48 h; descarte do que sair da janela; sync em WorkManager a cada 12 h e sob demanda
- [x] Preencher o slot de EPG da tela de TV ao vivo: programa atual, próximo, barra de progresso
- [x] `EpgGridScreen`: grade horizontal por canal (linha do tempo de 3 h visível), navegação por D-pad, tocar ao apertar OK; acessível pelo botão "Guia" na TV ao vivo e por tecla GUIDE do controle quando existir
- [x] Casamento canal↔programa por `tvg-id` (M3U) ou `epg_channel_id` (Xtream)

## 4. Controle parental (PIN)
- [x] PIN de 4 dígitos vindo do config (`pin`); padrão `0000`; alterável em Configurações com confirmação do PIN atual (salvo localmente; não há endpoint no painel para isso na v1)
- [x] Bloqueio por playlist (`is_protected` do config) e por categoria (lista local escolhida pelo usuário em Configurações → Controle parental)
- [x] Diálogo de PIN com teclado numérico navegável por D-pad; desbloqueio válido pela sessão do app
- [x] Ocultar categorias (Configurações) — persistido por playlist; some das listas, EPG e busca

## 5. Layout 2 (`grid`)
- [x] Home alternativa: sem menu lateral; blocos grandes em grade (TV ao vivo, Filmes, Séries, Favoritos, Guia, Configurações) com contagem e capa de destaque em cada bloco; banners embaixo
- [x] Mesmas telas internas; só a home muda. Troca por `theme` do config e por "Alterar layout" em Configurações (a escolha do painel vence a local no próximo `config`)
- [x] Ambos os layouts respeitam `logo_url`, `bg_url`, banners e QR

## 6. Versão celular (`MobileActivity`)
- [x] Mesmas ViewModels e repositórios; UI adaptada ao toque e ao retrato: bottom navigation (TV / Filmes / Séries / Guia / Mais), listas verticais, grade de 3 colunas, busca com teclado nativo
- [x] Ativação com botão "Copiar MAC" e "Compartilhar" (WhatsApp para o revendedor)
- [x] Player: gestos (toque mostra controles, arrastar para seek, duplo toque ±10 s), rotação para paisagem em tela cheia, PiP ao sair do app quando o dispositivo suportar
- [x] Validar no AVD de celular; rotação, retorno de background e PiP

## 7. libVLC real (fallback)
- [x] `VlcEngine` com `libvlc-all` (versão estável mais recente), mesma interface `PlayerEngine`; suporte a faixas de áudio/legenda
- [x] Regra de fallback: Media3 falha com erro de fonte/decodificação após os 2 retries → troca para VLC no mesmo canal, sem sair da tela, com aviso discreto "Modo de compatibilidade"
- [x] Configurações → Player: Automático (padrão) / Media3 / VLC, por playlist
- [x] Testar com um TS com codec não suportado pelo Media3 no emulador (gerar fixture com áudio AC-3 ou vídeo MPEG-2)
- [ ] Impacto no tamanho do APK documentado; se passar de 40 MB, avaliar ABI splits (armeabi-v7a, arm64-v8a, x86_64)

## 8. Fechamento do M4
- [ ] Fluxo completo nos dois AVDs: ativação → TV → filme com continuar assistindo → série com próximo episódio → guia → PIN → troca de layout → fallback VLC. Capturas em `docs/screens/android/m4/`
- [ ] Sync completo com fixture grande (1.200 canais + 20.000 filmes + 500 séries + EPG de 48 h) em menos de 60 s no AVD, sem ANR
- [ ] Testes unitários verdes (parsers XMLTV e SxxExx, regras de fallback, PIN); lint sem erros; release assinado e publicado em `/downloads/app.apk` com `min_app_version` atualizado no admin
- [ ] `docs/ANDROID.md` atualizado; `docs/API.md` sem mudanças (nenhum endpoint novo no painel neste marco — se precisar de algum, pare e me consulte)

## Ao concluir
Apresente o que foi entregue, pendências, decisões em ADR e o que precisa ser validado em hardware real. Então aguarde `docs/PLANO-M5.md` (testes integrados, homologação com o cliente, documentação e entrega).
