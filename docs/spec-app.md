# Especificação — App Android (Pop Player Pro / `com.pro.popplayer`)

Engenharia reversa do app para reconstrução de um equivalente. Complementa [`../spec-painel.md`](../spec-painel.md) — juntos formam o par **painel ↔ app**.

- **Pacote (loja):** `com.pro.popplayer` · versionName **3.9** · versionCode **10**
- **Pacote interno (código):** `com.atvapps.ibo` · BuildConfig versionName **3.8** / versionCode **38**
- **Origem:** reskin de um **IBO/ATV Player** feito pelo serviço de rebrand **"bentorebrands"** (chaves de cripto revelam isso). O `com.pro.popplayer` é um wrapper **PAIRIP** (proteção/licença da Play Store) sobre o `com.atvapps.ibo`.
- **Alvos:** Android 6+ (minSdk 23), targetSdk 36. Entradas: telefone (`MainActivity`/LAUNCHER) e TV (`MainTVActivity`/LEANBACK_LAUNCHER).
- **Método:** APK v3.9 (idêntico ao instalado no emulador — versionCode 10 confere) baixado da Play via mirror; descompilado com jadx; duas camadas de ofuscação de strings **decifradas** (script Java replicando os algoritmos reais). Device no emulador confirmado por `dumpsys`.
- **Fonte:** código em [`src/jadx/`](src/jadx/); manifesto efetivo em [`src/dumpsys_package.txt`](src/dumpsys_package.txt).

> **Convenção:** `[OBS]` = visto no código/dump. `[INF]` = inferido. `[DEC]` = valor obtido decifrando ofuscação (com o algoritmo real, alta confiança).

> ⚠️ **Fase 3 (tráfego mitmproxy) bloqueada** pelo adbd corrompido deste BlueStacks (shell/toybox/sync mortos — só `screencap`/`dumpsys` nativos funcionam). **Fase 4 (navegação) feita parcialmente** via captura por `screencap` enquanto o usuário navegava (não consigo `am start`/`input`, mas capturo a tela). Screenshots em [`screens/app-*.png`](screens/). O grosso da análise é **estática** e cobre o que a Fase 3 confirmaria. Ver [§10 Lacunas](#10-lacunas) e [§15 Fase 4](#15-fase-4--telas-reais-do-app).

---

## 1. Identidade do dispositivo (o "MAC")

**Achado central:** o app **não lê MAC de hardware**. A identidade é o **ANDROID_ID**, e o **MAC é atribuído pelo painel**.

- `Utils.getDeviceId(ctx)` = `Base64( Settings.Secure.ANDROID_ID )` → enviado ao painel como `app_device_id`. `[OBS]` ([`Utils.java:211`](src/jadx/sources/com/atvapps/ibo/utils/Utils.java))
- No boot, o app faz POST ao painel com esse `app_device_id`; o painel responde um `AppInfoModel` contendo **`mac_address`** (ex.: `21:7A:E6:7F:81:E5`) e `device_key`. O app **guarda** esse MAC: `preferenceHelper.setSharedPreferenceMacAddress(appInfoModel.getMac_address())`. `[OBS]` ([`MainTVActivity.java:320`](src/jadx/sources/com/atvapps/ibo/MainTVActivity.java))
- Todas as telas exibem `getSharedPreferenceMacAddress()`; ao criar/excluir playlist o app envia esse MAC (lowercased) ao painel. `[OBS]`
- `Utils.getSamsungMac(hex)` formata uma string hex em `XX:XX:...` — usado só quando o usuário **digita** um MAC manualmente (`PayForTvDlgFragment`). `[OBS]`

**Implicação para reconstrução:** o servidor é a autoridade do MAC. Fluxo: `device_id (ANDROID_ID) → servidor gera/mapeia um mac_address → app o exibe → revenda cadastra esse MAC → servidor passa a devolver as playlists`. `[INF]`

> No emulador, `ANDROID_ID` é fixo por instância → device_id estável. Em TVs reais idem (ANDROID_ID persiste até factory reset).

---

## 2. Endpoints HTTP

### 2.1 Painel (popplayer.pro) — via Volley `[DEC]`

URLs ofuscadas em `com.android.volley.Async2Cache` (hex AES-128-CBC, key `atvbentorebrands`, IV `meubentorebrands`), decifradas:

| Constante | URL | Uso |
|---|---|---|
| `response_url` / `second_response_url` (`Async2Cache.a`) | `https://popplayer.pro/meuplayer/a/` | **Ativação / buscar config** (AppInfoModel) |
| `Async2Cache.b` | `https://popplayer.pro/meuplayer/api/` | base da API |
| `create_url` / `delete_url` (`Async2Cache.c`) | `https://popplayer.pro/meuplayer/api/playlist` | **Criar / excluir playlist** (do próprio app) |

- Transporte: `GetDataRequest` → Volley `JsonObjectRequest` **POST**, corpo `{"data":"<ofuscado>"}`, header **`User-Agent: smart-tv`**, retry 20s. `[OBS]` ([`GetDataRequest.java`](src/jadx/sources/com/atvapps/ibo/remote/GetDataRequest.java))

### 2.2 Servidor IPTV (Xtream Codes) — via Retrofit `[OBS]`

Interface [`APIService.java`](src/jadx/sources/com/atvapps/ibo/remote/APIService.java); baseUrl = o host da playlist do usuário (ex.: `http://cnplay.click`). Padrão **Xtream Codes API**:

| Método | Rota | Retorno |
|---|---|---|
| GET | `/player_api.php?` (`username`,`password`) | `LoginResponse` (auth + user_info) |
| GET | `/player_api.php?action=get_live_categories` | categorias live |
| GET | `/player_api.php?action=get_live_streams[&category_id=*]` | canais |
| GET | `/player_api.php?action=get_vod_categories` / `get_vod_streams` | filmes |
| GET | `/player_api.php?action=get_series_categories` / `get_series` / `get_series_info` | séries |
| GET | `/player_api.php?action=get_short_epg` / `get_simple_data_table` | EPG |
| GET | `/xmltv.php` (`username`,`password`) | EPG XMLTV (streaming) |

`RetroClass`: OkHttp timeouts 60s, `HttpLoggingInterceptor(BODY)`, conversores Scalars+Gson. `[OBS]`

### 2.3 Terceiros `[OBS]`

- **TMDB** (metadados de filmes/séries): `Constants.IMDB_API/IMDB_API_SERIES/PERSON_API` + `IMDB_KEY = d96abf17668601f56b3d7b8336a61933` (chave TMDB em texto puro). Imagens via `IMDB_IMAGE_PREF`.
- **OpenSubtitles** (`SUBTITLE_LOGIN/SEARCH/DOWNLOAD` + `USERNAME`/`PASSWORD`/`API_KEY`) — strings ofuscadas via `EnigmaUtils` (AES-256-CBC, IV zero, key `ugQ7!6DL1QMH07NxRGa4lKQGoJiH?4$J`); decodificáveis pelo mesmo método se necessário.

---

## 3. Contrato de resposta app↔painel

### 3.1 Requisição (envelope ofuscado) `[OBS]`

`Security.get*Data(...)` monta um JSON, faz Base64, **insere uma chave-lixo aleatória** (comprimento aleatório 0–19) numa **posição aleatória** (máx 42) e anexa **2 chars marcadores** de posição. `Security.getDecodedString` reverte. Payloads:

| Origem | JSON (antes de ofuscar) | Endpoint |
|---|---|---|
| `getStringData(deviceId, version, is_paid, appType)` | `{app_device_id, app_type:"tv", version:"3.8", is_paid:false}` | POST `/a/` |
| `getAddData(mac, plId, plName, plUrl, plType)` | `{mac_address, playlist_id, playlist_name, playlist_url, playlist_type, app_type:"android"}` | POST `/api/playlist` |
| `getDeleteData(mac, plId)` | `{mac_address, playlist_id, app_type:"android"}` | POST `/api/playlist` |
| `getActivateData(mac, planId)` | `{mac_address, plan_id, payment_type:"google_pay", app_type:"android"}` | (ativação in-app) |

Envio final: `{"data": "<envelope>"}`. `[OBS]`

### 3.2 Resposta — `AppInfoModel` `[OBS]`

Resposta = `{status, data}`; `data` é ofuscado (revertido por `getDecodedString`) e desserializado em [`AppInfoModel`](src/jadx/sources/com/atvapps/ibo/models/AppInfoModel.java):

| Campo JSON | Tipo | Significado | Equivale no painel |
|---|---|---|---|
| `mac_registered` | bool | MAC está cadastrado? | ativação |
| `mac_address` | string | MAC atribuído | coluna MAC do usuário |
| `device_key` | string | chave do dispositivo | — |
| `expire_date` | string | vencimento | vencimento |
| `is_trial` | int | trial (2 = ativado) | — |
| `urls` | `[UrlModel]` | **playlists** | os "usuários" da revenda |
| `app_theme` | string (`theme_d`..`theme_8`) | tema ativo | Layouts |
| `logo_url` | string | logomarca | Logomarca |
| `bg_url` | string | fundo | Background |
| `qr_url` | string | QR code | QR Code |
| `ads_data` | string | banners | Banners |
| `pin` / `lock` | string/int | PIN parental (default `0000`) | — |
| `plan_id` / `price` / `is_google_pay` | string/bool | ativação in-app (painel usa **Pix**, não isto) | Renovar Revenda |
| `apk_link` / `app_version` | string | atualização forçada | — |
| `languages` | `[LanguageModel]` | idiomas | — |

**`UrlModel`** (cada playlist): `id`, `name`, `url` (M3U/Xtream), `type` (`playlist_type`), `is_protected` (`"0"`/`"1"` → exige PIN). `[OBS]`

> A tela "Meus Usuários" do painel = a lista `urls[]`. O painel chama de "usuário/cliente" o que o app trata como **playlist** amarrada ao MAC.

---

## 4. Parsing de playlist

- **Xtream Codes** (`type` xtream): baseUrl = host; autentica em `player_api.php`; usa `username`/`password` extraídos da URL `get.php`. `[OBS]`
- **M3U cru** (`type` m3u): `net/FetchM3uItemsTask` + `LoadM3UItemsCommand` baixam e fazem parse de `#EXTINF` (tvg-id, tvg-logo, group-title → categoria). `[OBS]`
- Categorias/canais/VOD/séries persistidos localmente em **Realm** (`helper/RealmController`). `[OBS]`
- `Utils.getUserId(url)` deriva um id removendo `: / . ? = &` da URL (fallback `"m3u"`). `[OBS]`

---

## 5. Temas

`app_theme` do painel mapeia direto para o app (default `theme_d`). Mesmo catálogo do painel (§3.6 da spec-painel): `theme_d` (Padrão), `theme_1..theme_5`, `theme_6` (HTV), `theme_7` (XC), `theme_8` (P2P). Renderização em `Themes/dashtheme.java` + `HomeActivity`. `[OBS]` Banners/QR exibidos por WebViews (`web/AtvAdsView`, `web/AtvQrView`, `Task/mAtvView*`). `[OBS]`

---

## 6. Player

- **ExoPlayer2** (Google) — player principal (1271 referências no código). `[OBS]`
- **libVLC** (`org.videolan`) — player alternativo/fallback. `[OBS]`
- Player externo suportado (`ExternalPlayerDlgFragment` — MX Player/VLC via intents; `Utils.getMXPackageInfo`/`getVlcPackageInfo`). `[OBS]`
- Legendas próprias (`view/SubtitleView`, OpenSubtitles), faixas de áudio/legenda (`AudioTrackDlgFragment`, `SubtitleTrackDlgFragment`). `[OBS]`

---

## 7. UI stack

- **Views clássicas + AndroidX Leanback** para TV (D-pad); telas mobile separadas em `activities/mobile/`. `[OBS]`
- Duas entradas: `MainActivity` (LAUNCHER/telefone) e `MainTVActivity` (LEANBACK_LAUNCHER/TV). `[OBS]`
- Componentes (Manifest): 27 activities (Home, Live, LiveChannel, Movie/Series + Info/Player, CatchUp, Search, Setting, ChangePlaylist, XplaySports, mobile/*). `[OBS]` Lista em [`dumpsys_package.txt`](src/dumpsys_package.txt).
- Libs de UI: Glide (imagens), BlurView (`eightbitlab`), RoundedImageView (`makeramen`), sdp (`intuit`). `[OBS]`

---

## 8. Segurança (do app original — anotado para NÃO replicar cegamente)

| Item | Achado |
|---|---|
| **TLS** | `UnsafeOkHttpClient`: TrustManager aceita qualquer cert + `hostnameVerifier` sempre `true` → **sem validação, sem pinning**. MITM trivial. `[OBS]` |
| **Cleartext** | `usesCleartextTraffic="true"` no Manifest → HTTP liberado (servidores IPTV são HTTP). `[OBS]` |
| **Ofuscação de strings** | 2 camadas: StringFog "ScKit" (AES-128-ECB, key=MD5(k)[8:24]) para chaves internas; `EnigmaUtils` (AES-256-CBC IV-zero) para URLs de terceiros; URLs do painel em AES-128-CBC (`atvbentorebrands`/`meubentorebrands`). **Todas decifradas.** `[DEC]` |
| **Ofuscação de payload** | envelope Base64 + chave-lixo aleatória + marcadores de posição (§3.1) — segurança por obscuridade, reversível. `[OBS]` |
| **Anti-tamper** | wrapper **PAIRIP** (`com.pairip`) + `com.android.vending.CHECK_LICENSE` → checagem de licença da Play. `[OBS]` |
| **Segredos em texto puro** | chave TMDB `d96abf17668601f56b3d7b8336a61933`. `[OBS]` |
| **Permissões** | enxutas: INTERNET, ACCESS_NETWORK/WIFI_STATE, RECEIVE_BOOT_COMPLETED, READ/WRITE_EXTERNAL_STORAGE, WAKE_LOCK, WRITE_SETTINGS, FOREGROUND_SERVICE, DISABLE_KEYGUARD. Sem SMS/contatos/localização. `[OBS]` |

**Consequência para a Fase 3 (quando o emulador funcionar):** como não há pinning, o mitmproxy captura tudo sem Frida — basta proxy + cert. `[INF]`

---

## 9. Bibliotecas (identificadas)

Realm (DB local) · Volley (painel) · Retrofit2 + OkHttp3 + logging (Xtream/TMDB) · Gson · Glide · ExoPlayer2 · libVLC · Google Play Services + PAIRIP · AndroidX (Leanback, AppCompat, Fragment, RecyclerView, Lifecycle, Startup, Multidex) · Kotlin stdlib · BlurView, RoundedImageView, sdp, rateme. `[OBS]`

---

## 10. Sequência de boot (diagrama)

```
Launcher (MainTVActivity / MainActivity)
   │  getMacAddress(): defaults (idioma, tamanho legenda), versionCheck, loadVersion
   ▼
getUserInfoModel()
   │  payload = getStringData( Base64(ANDROID_ID), "3.8", is_paid=false, "tv" )
   │  POST https://popplayer.pro/meuplayer/a/   body {"data":"…"}   UA: smart-tv
   ▼
OnGetResponseResult(json)
   │  status? e json.has("data")?
   ├─ não → checkLocalStorageAccount() (usa cache salvo em disco/Prefs)
   └─ sim → AppInfoModel = Gson( getDecodedString(json.data) )
            salva appInfo, mac_address, device_key ; baixa logo/bg/imagens
            checkAppInfoModel() → loadingData()
   ▼
loadingData()
   ├─ urls[] vazio  OU  urls[0].id == "0"   → ChangePlaylistActivity  (sem playlist: cadastrar MAC / adicionar)
   └─ tem playlist → autentica Xtream (player_api.php) → doNextTask()
                        ├─ ok    → HomeActivity  (live/filmes/séries/EPG)
                        └─ falha → ChangePlaylistActivity ("playlist não funciona")
```

**Recarregamento de config:** o app relê o painel a cada boot (`getUserInfoModel`) e guarda cache local (`setSharedPreferenceAppInfo` + `Utils.saveToFile`). Trocar tema/logo/playlist no painel reflete **no próximo boot** (ou ao reentrar), não em tempo real. `[INF]`

---

## 11. Modelo de dados (lado do app)

```
AppInfoModel (resposta do painel, cacheada em Prefs + arquivo)
├─ mac_address, device_key, mac_registered
├─ expire_date, is_trial, plan_id, price, is_google_pay
├─ app_theme, logo_url, bg_url, qr_url, ads_data, pin, lock
├─ languages[]
└─ urls[] : UrlModel { id, name, url, type, is_protected }   ← playlists

LoginResponse (Xtream player_api.php)  — user_info: exp_date, max_connections, status, auth
CategoryModel / EPGChannel / MovieModel / SeriesModel / Episode  — conteúdo (persistido em Realm)
Prefs (SharedPreferences)  — mac_address, device_key, playlist_position, idioma, tamanho legenda, first_launch, last_playlist_date
WordModels  — strings de UI/i18n vindas do painel (RTXSetting/PanalData via JsonParserTask)
```

Chave de identidade: **ANDROID_ID → device_id (enviado)** ; **mac_address (recebido/cacheado)**. `[OBS]`

---

## 12. Contrato definitivo painel↔app (para reconstruir o backend)

O backend novo precisa expor, para o app:

1. **`POST /a/`** — recebe `{"data": <envelope(app_device_id, app_type, version, is_paid)>}`, UA `smart-tv`. Responde `{status, data:<envelope(AppInfoModel)>}` com playlists do MAC, personalização, vencimento, flags. Se o MAC não existe: `mac_registered=false` e `urls=[]` (ou `urls[0].id="0"`).
2. **`POST /api/playlist`** — `getAddData` cria e `getDeleteData` remove uma playlist para aquele MAC (o app permite auto-cadastro de playlist).
3. Mesma **ofuscação** (StringFog/AES) nos dois sentidos, ou — recomendado na reconstrução — **substituir por TLS real + JSON limpo + auth por token** (a ofuscação aqui é só obscuridade).
4. O conteúdo (live/VOD/séries/EPG) **não passa pelo painel**: o app fala direto com o servidor **Xtream** da URL da playlist. O painel só entrega *quais* playlists o MAC tem.

---

## 13. O que construir — priorizado (lado app)

### P0
1. **Endpoint `/a/` (ativação/config)** no backend novo, devolvendo o equivalente ao `AppInfoModel` (playlists do MAC + personalização + vencimento). É o que faz o app funcionar.
2. **Registro por MAC/device_id**: mapear `device_id (ANDROID_ID)` → MAC → conta; devolver `mac_registered`, `mac_address`, `device_key`.
3. **App player** (ou reuso de um IBO/ATV base): boot → consulta config → carrega playlist Xtream → Home (live/VOD/séries/EPG) com ExoPlayer.
4. **Parser Xtream + M3U** no app (já é padrão; reaproveitar).

### P1
5. **`/api/playlist` add/delete** (auto-cadastro de playlist pelo app).
6. **Personalização** aplicada no app: `logo_url`, `bg_url`, `qr_url`, `app_theme`, `ads_data`.
7. **Vencimento/estado**: expire_date, is_trial, telas de expiração/ativação.
8. **PIN parental** (`pin`, `lock`, `is_protected` por playlist).

### P2
9. **Ativação in-app** (`plan_id`/`price`/google_pay) — no painel é Pix; decidir se o app terá compra in-app.
10. **Atualização forçada** (`apk_link`/`app_version`).
11. **i18n** via WordModels (RTXSetting/PanalData) ou recursos locais.
12. **EPG XMLTV**, catch-up, legendas/OpenSubtitles, player externo — conforme escopo.

---

## 14. Lacunas (não fechadas)

Bloqueadas pelo adbd corrompido do BlueStacks (shell/toybox/sync mortos; só `screencap`/`dumpsys` nativos):

1. **Fase 3 — tráfego real (mitmproxy):** não capturado. Precisaria de proxy + cert, que exigem `settings put`/instalar cert (shell). O contrato foi recuperado estaticamente; falta apenas **ver os corpos reais** de resposta de `/a/` e `/api/playlist` (nomes de campo confirmados, formato de `ads_data`, exemplos de `expire_date`).
2. **Fase 4 — parcial:** telas capturadas (§15), mas **sem** a tela de ativação do zero (app já estava ativado; `pm clear` exige shell) e **sem** o player em reprodução (não cheguei a abrir um stream durante a captura). Rodou em **modo telefone** (`MainActivity`/mobile), não TV/Leanback.
3. **`ads_data`** — formato exato (HTML? JSON? lista de URLs) não confirmado; é renderizado em WebView.
4. **Strings OpenSubtitles/TMDB** (`USERNAME`/`PASSWORD`/`API_KEY` do Enigma) — não decodificadas (secundário; mesmo método AES-256/IV-zero resolve).

**Como fechar:** com o adbd são (instance novo do BlueStacks ou outro emulador), rodar a Fase 3 — proxy + cert (sem Frida, pois não há pinning) — e completar a Fase 4 (ativação do zero via `pm clear`, player, e modo TV).

---

## 15. Fase 4 — telas reais do app

Capturadas via `screencap` (modo telefone). Confirmam a análise estática:

| Tela | Arquivo | O que confirma |
|---|---|---|
| Splash/loading | [app-01-splash.png](screens/app-01-splash.png) | `MainActivity` — logo POP PLAYER + spinner; é o boot que busca config em `/a/` |
| Home | [app-02-home.png](screens/app-02-home.png) | barra **USUÁRIO ATIVO** · **VENCIMENTO 24/09/2026** · Suporte · Sair; nav TV/Filmes/Séries/Esportes; carrossel de destaque — renderiza campos do `AppInfoModel` |
| TV ao Vivo | [app-03-tv-ao-vivo.png](screens/app-03-tv-ao-vivo.png) | 3 colunas: categorias (**2809 canais**, Favoritos, grupos), lista de canais, painel player/EPG (botões EPG / Adicionar aos favoritos / Procurar) — Xtream live |
| Filmes (VOD) | [app-04-filmes.png](screens/app-04-filmes.png) | **20.823** títulos; categorias (Lançamentos 2026/2025, 4K, Netflix/Disney+/HBOmax); grade de pôsteres; ordenação — Xtream VOD |
| Séries | [app-05-series.png](screens/app-05-series.png) | grade de séries análoga a Filmes — Xtream series |
| Configurações | [app-06-configuracoes.png](screens/app-06-configuracoes.png) | **endereço MAC: `21:7A:E6:7F:81:E5`** (== MAC do painel!) + opções: adicionar playlist, Controle dos Pais, Playlist, mudar idioma, **Alterar layout** (tema), Ocultar Categorias, Limpar histórico, Live Stream Format, **Player externo**, **Período de Atualização**, Config. de legenda, Select Device Type, atualize agora |

**Confirmações-chave das telas:**
- O MAC exibido nas Configurações **é idêntico** ao MAC do usuário "Gustavo" no painel (`21:7A:E6:7F:81:E5`) → prova o vínculo device_id→MAC→playlists (§1, §3).
- Home mostra **status ("ATIVO")** e **vencimento** vindos do `AppInfoModel` (§3.2).
- "Alterar layout" (tema) e "Período de Atualização" na UI confirmam `app_theme` e o recarregamento periódico da config (§5, §10).
- Conteúdo (canais/VOD/séries) vem do **Xtream**, não do painel (§2.2) — os números (2809/20823) são do servidor da playlist.

---

*Análise estática de `com.pro.popplayer` v3.9 (interno `com.atvapps.ibo` v3.8) + Fase 4 parcial (screenshots), 04/09/2026. Código em `docs/app/src/jadx/`, telas em `docs/app/screens/`.*
