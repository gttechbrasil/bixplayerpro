# Marco 3 — App Android TV: ativação, playlists, TV ao vivo, player, layout 1

Execute em ordem. Cada bloco termina com build verde, testes unitários verdes e commit. Não avance para o M4.

**Mudança de stack (registrar em `docs/ADR-004-android-ui.md` e atualizar `CLAUDE.md`):** UI em **Jetpack Compose** com **Compose for TV** (`androidx.tv:tv-material`, `androidx.tv:tv-foundation`) no lugar de Leanback. Motivo: reuso entre TV e celular no M4, produtividade e manutenção. Media3 (ExoPlayer) continua como player.

## 0. Ambiente
- [x] Verificar Android SDK (API 36), Java 17, Gradle. Se faltar, listar exatamente o que instalar e parar — **inventário feito em 05/09/2026; bloqueado**: faltam `cmdline-tools` (sem `sdkmanager`/`avdmanager`) e a imagem de sistema Android TV. Detalhes abaixo
- [x] Criar um AVD **Android TV** (API 34, 1080p) e um AVD celular; documentar em `docs/ANDROID.md` como criar e rodar via linha de comando (`avdmanager`, `emulator`, `adb`) — AVD `bix_tv_api36` criado (perfil `tv_1080p`, 1920×1080); **API 36 e não 34**, porque é a única imagem de Android TV x86_64 disponível no SDK. AVD de celular `Pixel_10_Pro_XL` já existia. Documentado em `docs/ANDROID.md`
- [ ] Confirmar que o backend está rodando localmente e alcançável do emulador (`http://10.0.2.2:8000`) — **bloqueado**: o emulador não conclui o boot nesta máquina (WHPX negado, `hr=80070005`). Ver `docs/ANDROID.md` §5

> **Bloqueio do bloco 0 — atualizado em 05/09/2026 após instalar o que faltava.**
> As command-line tools foram instaladas, a imagem `system-images;android-36;android-tv;x86_64`
> foi baixada e o AVD `bix_tv_api36` criado. Restou **um bloqueio de ambiente**: o emulador não
> inicializa a aceleração WHPX (`Failed to setup partition, hr=80070005`, acesso negado), o que
> derruba qualquer AVD, inclusive o de celular que já existia. A correção exige elevação e novo
> login no Windows — ver `docs/ANDROID.md` §5. Registro do inventário original:
>
> **Inventário de 05/09/2026 (antes das instalações).** Presentes: JDK 21 (JBR do Android Studio, via `JAVA_HOME`),
> plataformas `android-36` e `android-36.1`, build-tools 35/36/36.1/37, `emulator` 36.5.11 com
> aceleração WHPX funcional, `adb` 1.0.41, licença `android-sdk-license` aceita e um AVD de celular
> (`Pixel_10_Pro_XL`). Faltam: **Android SDK Command-line Tools** (a pasta `cmdline-tools` está vazia,
> então não há `sdkmanager` nem `avdmanager`) e **uma imagem de sistema Android TV** (só existe
> `android-37.0/google_apis_playstore_ps16k`, que é de celular). Sem isso não dá para criar o AVD de
> TV nem documentar o fluxo por linha de comando pedido neste bloco.

## 1. Bootstrap do projeto (`/android`)
- [x] Projeto Gradle Kotlin DSL, módulo único `app`, minSdk 23, targetSdk 36, `applicationId` configurável por `gradle.properties` (white label muda depois) — Gradle 9.7.1 + AGP 9.4.0; `compileSdk 37` (exigido por várias AndroidX), `targetSdk 36`, `minSdk 23`
- [x] Dependências: Compose BOM, tv-material, tv-foundation, Navigation Compose, Hilt, Retrofit + Moshi (codegen), OkHttp + logging, Room, DataStore, Coil, Media3 (exoplayer, exoplayer-hls, ui, session), kotlinx-coroutines, Timber — em `gradle/libs.versions.toml`; Navigation fixada em 2.9.8 porque a 2.10 exige minSdk 24
- [x] Duas activities: `TvActivity` (LEANBACK_LAUNCHER, `android.software.leanback` required=false) e `MobileActivity` (LAUNCHER). No M3 a `MobileActivity` só abre a mesma tela de ativação
- [x] Arquitetura: `data/` (api, db, repository), `domain/` (models, usecases), `ui/` (screens, components, theme), `player/`. MVVM com StateFlow
- [x] Build types `debug` (base URL local, log de rede) e `release` (minify, R8, sem log). Assinatura release por keystore em `keystore.properties` (fora do git)
- [x] `ui/theme`: tokens de cor/tipografia próprios, fonte legível a 3 m, escala de foco padrão

## 2. Núcleo: identidade, API da plataforma e persistência
- [x] `DeviceIdentity`: `ANDROID_ID` com hash SHA-256 → `device_id`
- [x] Cliente Retrofit da API da plataforma (`/api/v1/device/*`): `register`, `config`, `playlists add/delete`. Interceptor Bearer com token do DataStore; em 401, re-registra e repete uma vez
- [x] DataStore: token, mac_address, último `config` serializado, playlist ativa, período de atualização (padrão 6 h), idioma — atrás da interface `DeviceStore`, para os testes rodarem sem Android
- [x] Room: `channels`, `categories`, `favorites` (por playlist), `playlist_sync` — com Paging 3 e inserção em lotes de 500 (última sincronização por playlist)
- [x] `ConfigRepository`: `refresh()` chama `config`, persiste, expõe `StateFlow<AppConfig>`; se a rede falhar, usa o cache. Job em `WorkManager` respeita o período de atualização
- [x] Testes unitários: parser de resposta, fallback de cache, re-registro em 401 — 19 testes verdes

## 3. Boot e ativação
- [x] `SplashScreen`: logo (do config, senão padrão), chama `refresh()`, decide rota
- [x] `ActivationScreen` (device `unregistered` ou sem playlists): MAC em fonte grande, instrução "Informe este MAC ao seu revendedor", QR Code com o MAC (ZXing), botão "Já cadastrei — verificar" e "Adicionar playlist manualmente" (nome + URL → `playlists add`)
- [x] `ExpiredScreen` (status `expired`): mensagem, vencimento, MAC, botão verificar
- [x] Atualização obrigatória: se `app_version` < `min_app_version`, tela com link `apk_url` — com QR do APK para escanear pelo celular
- [ ] Navegação por D-pad em todos os botões e campos; teclado virtual funcional no campo de URL — **código pronto** (`BixButton`/`BixTextField` tratam DPAD_CENTER/ENTER e foco); **falta validar em execução**, o emulador não sobe (ver `docs/ANDROID.md` §5)

## 4. Playlists: Xtream e M3U
- [ ] Cliente Xtream (`player_api.php`): login, `get_live_categories`, `get_live_streams`; `baseUrl` dinâmica por playlist. Timeout 30 s
- [ ] Parser M3U/M3U8 em streaming (não carregar o arquivo inteiro em memória): `#EXTINF` com `tvg-id`, `tvg-name`, `tvg-logo`, `group-title`; linhas de URL; ignorar entradas inválidas
- [ ] `PlaylistSyncUseCase`: sincroniza categorias e canais para o Room em transação; marca `playlist_sync`; roda na primeira abertura e quando o usuário pedir "atualizar". Deve suportar 5.000 canais sem travar (teste com fixture gerada)
- [ ] `ChangePlaylistScreen`: lista de playlists do config, marcação da ativa, troca → resync, "Adicionar" e "Remover" (chamam a API da plataforma)
- [ ] Testes unitários: parser M3U (fixtures reais e malformadas), mapeamento Xtream → domínio

## 5. TV ao vivo — layout 1 (`default`)
- [ ] `HomeScreen` layout `default`: barra superior com logo, status ("Ativo" / vencimento), relógio; menu TV ao vivo / Filmes / Séries / Configurações (Filmes e Séries desabilitados no M3 com aviso "em breve"); fundo do config; banners ativos em carrossel (imagem por URL) se `auto_ads` ou banners cadastrados
- [ ] `LiveScreen` em 3 colunas: categorias (com "Todos" e "Favoritos") · canais da categoria (logo, nome, número) · painel de preview com o canal focado tocando em miniatura + nome do programa se disponível (EPG é M4 — deixar o slot)
- [ ] Listas com `TvLazyColumn`/`LazyColumn` paginadas do Room (Paging 3), foco lembrado ao voltar
- [ ] Favoritar/desfavoritar com botão do controle (tecla de menu ou botão na tela)
- [ ] Busca de canal por nome com teclado virtual (campo simples; busca global é M4)
- [ ] `SettingsScreen` (mínimo do M3): trocar playlist, atualizar listas, período de atualização, idioma pt-BR/en/es (strings via `strings.xml`), limpar cache, exibir MAC e versão, sair (limpa DataStore e volta à ativação)

## 6. Player
- [ ] `PlayerScreen` com Media3: HLS, TS (`DefaultExtractorsFactory` com `TsExtractor` tolerante), MP4, HTTP com redirect; `User-Agent` configurável (padrão do app); buffer ajustado para live (`LiveConfiguration` target offset)
- [ ] Controles com D-pad: OK mostra/oculta overlay (nome do canal, categoria, relógio, número); ↑/↓ troca de canal (zapping); ←/→ abre lista rápida de canais; Voltar sai; digitar número seleciona canal
- [ ] Seleção de faixa de áudio e legenda embutidas (`TrackSelectionDialog` próprio em Compose)
- [ ] Tratamento de erro: mensagem em português, tentativa automática 2× com backoff, depois botão "Tentar novamente". Registrar o erro no Timber com URL sem credenciais
- [ ] Fallback libVLC: **estrutura pronta** (interface `PlayerEngine` com `Media3Engine` implementado e `VlcEngine` stub) — implementação real fica no M4
- [ ] Tela permanece ligada durante reprodução; libera o player em background; retoma ao voltar
- [ ] Miniatura no preview do `LiveScreen` reutiliza a mesma instância do player (não criar dois players)

## 7. Fechamento do M3
- [ ] Rodar no AVD Android TV: fluxo completo ativação → cadastro no /painel → verificar → sync → TV ao vivo → tocar canal → zapping → favoritar → trocar playlist → sair. Gravar screenshots em `docs/screens/android/m3/`
- [ ] Testar com playlist de fixture local (servidor HTTP de teste com M3U e alguns streams HLS públicos de teste) — documentar em `docs/ANDROID.md`
- [ ] APK debug e release assinados em `android/app/build/outputs/`, e o `apk_url` do admin apontando para o release hospedado no Caddy (`/downloads/app.apk`)
- [ ] `docs/ANDROID.md`: build, assinatura, como trocar applicationId/nome/ícone para white label, como rodar nos AVDs
- [ ] Testes unitários verdes; `./gradlew lint` sem erros; sem warnings de R8 em release

## Ao concluir
Apresente o que foi entregue, o que ficou pendente, as decisões em ADR-004 e quaisquer limitações encontradas no emulador (o ambiente de TV costuma ter surpresas). Então aguarde `docs/PLANO-M4.md` (filmes, séries, EPG, PIN, layout 2, celular).
