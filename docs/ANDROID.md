# App Android — ambiente, emuladores e build

Guia do ambiente de desenvolvimento do app (`/android`). A stack de UI está definida em
[`ADR-004`](ADR-004-android-ui.md): Jetpack Compose + Compose for TV, sem Leanback.

## 1. Ferramentas exigidas

| Ferramenta | Versão usada | Onde |
|---|---|---|
| JDK | 21 (JBR do Android Studio) | `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr` |
| Android SDK | plataformas `android-36` e `android-36.1` | `%LOCALAPPDATA%\Android\Sdk` |
| Build tools | 36.1.0 (37.0.0 também instalado) | idem |
| Command-line tools | 1.0 (`sdkmanager`, `avdmanager`) | `cmdline-tools\latest\bin` |
| Emulator | 37.1.11 | `emulator\emulator.exe` |
| Gradle | 9.7.1 (via wrapper do projeto) | `android/gradlew` |

O `java` do `PATH` desta máquina é o JDK 8; **o Gradle usa o `JAVA_HOME`**, que aponta para o
JDK 21. Não é preciso mexer no `PATH`.

```bash
export ANDROID_HOME="$LOCALAPPDATA/Android/Sdk"
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
```

## 2. Armadilha do Windows: `;` nos nomes de pacote

`sdkmanager.bat` e `avdmanager.bat` **quebram os argumentos nos pontos e vírgulas** quando
chamados pelo Git Bash ou pelo PowerShell. `sdkmanager "system-images;android-36;android-tv;x86_64"`
resulta em `Package system-images not found`.

Duas saídas que funcionam:

```bash
# sdkmanager: use um arquivo de pacotes (um por linha)
echo "system-images;android-36;android-tv;x86_64" > packages.txt
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager.bat" --package_file=packages.txt
```

```powershell
# avdmanager: monte a linha e passe pelo cmd, que preserva as aspas
$bat = "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\latest\bin\avdmanager.bat"
$cmd = '"' + $bat + '" create avd -n bix_tv_api36 -k "system-images;android-36;android-tv;x86_64" -d tv_1080p --force'
cmd /c "echo no | $cmd"
```

## 3. Imagens e AVDs

Só existe uma imagem de Android TV x86_64 no canal estável: **API 36**. Não há API 34 nem 35
para TV neste SDK.

```bash
# listar o que há de TV
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager.bat" --list | grep -i "android-tv"
```

AVDs deste projeto:

| AVD | Perfil | Imagem | Uso |
|---|---|---|---|
| `bix_tv_api36` | `tv_1080p` (1920×1080, 320 dpi) | `android-36;android-tv;x86_64` | Android TV |
| `Pixel_10_Pro_XL` | celular | `android-37.0;google_apis_playstore_ps16k` | celular (M4) |

Ajustes aplicados no `~/.android/avd/bix_tv_api36.avd/config.ini`:

```ini
hw.ramSize=2048
vm.heapSize=512
disk.dataPartition.size=6G
hw.keyboard=yes
hw.dPad=yes
hw.lcd.width=1920
hw.lcd.height=1080
hw.lcd.density=320
```

## 4. Rodar o emulador

```bash
"$ANDROID_HOME/emulator/emulator.exe" -list-avds
"$ANDROID_HOME/emulator/emulator.exe" -avd bix_tv_api36 -no-snapshot -no-boot-anim
"$ANDROID_HOME/platform-tools/adb.exe" wait-for-device
"$ANDROID_HOME/platform-tools/adb.exe" shell getprop sys.boot_completed   # 1 = pronto
```

No Git Bash, `nohup ... &` **não desacopla** o emulador: quando o shell termina, o processo
morre. Lance pelo PowerShell:

```powershell
Start-Process "$env:LOCALAPPDATA\Android\Sdk\emulator\emulator.exe" `
  -ArgumentList "-avd","bix_tv_api36","-no-snapshot","-no-boot-anim" -WindowStyle Minimized
```

### Comandos úteis

```bash
ADB="$ANDROID_HOME/platform-tools/adb.exe"
$ADB devices -l
$ADB install -r android/app/build/outputs/apk/debug/app-debug.apk
$ADB shell am start -n <applicationId>/.ui.TvActivity
$ADB exec-out screencap -p > tela.png          # screenshot (funciona headless)
$ADB shell input keyevent DPAD_DOWN            # navegação por D-pad
$ADB logcat -s BixPlayer:V                     # logs do app
$ADB emu kill                                  # encerra o emulador
```

## 5. Emulador — histórico do bloqueio (resolvido em 05/09/2026)

> **Resolvido.** Bastou **reiniciar o Windows** depois de adicionar a conta ao grupo
> `Administradores do Hyper-V`: o driver do hipervisor só lê a associação no boot, e um
> logoff/logon não é suficiente. Depois disso o WHPX passou a funcionar sem elevação.
>
> Um segundo problema apareceu na sequência: o AVD ficava `offline` no `adb` indefinidamente,
> porque o `userdata` havia sido corrompido pelas tentativas anteriores em modo software.
> A saída foi subir uma vez com **`-wipe-data`**:
>
> ```powershell
> emulator -avd bix_tv_api36 -no-window -no-boot-anim -no-audio -wipe-data -no-snapshot
> ```
>
> O registro abaixo fica como diagnóstico caso o sintoma volte.

### Histórico do diagnóstico

**Sintoma:** qualquer AVD (inclusive o `Pixel_10_Pro_XL`, que é anterior a este projeto) sobe o
processo `qemu-system-x86_64` e morre sem abrir a porta de console; o `adb` nunca vê o
dispositivo.

**Causa raiz**, capturada com `emulator -avd bix_tv_api36 -accel on -verbose`:

```
qemu-system-x86_64-headless.exe: WHPX: Failed to setup partition, hr=80070005
qemu-system-x86_64-headless.exe: failed to initialize WHPX: Invalid argument
```

`0x80070005` é `E_ACCESSDENIED`. O `emulator -accel-check` responde "WHPX is installed and
usable" porque só consulta a capacidade; a criação da partição é que é negada.

Contexto da máquina: Hyper-V ativo (`vmms`, `vmcompute`, `HvHost` rodando), WSL2 em uso, VBS
ligado com HVCI desligado, e o processo do desenvolvedor **não elevado** — a conta é
administradora, mas o UAC filtra o token, e ela **não pertence ao grupo
`Hyper-V Administrators`**.

**Já testado e descartado (05/09/2026):**

| Hipótese | Verificação | Resultado |
|---|---|---|
| Conta fora do grupo `Hyper-V Administrators` | usuário adicionado e relogado; `whoami /groups` mostra `BUILTIN\Administradores do Hyper-V` no token do processo | **não resolveu** |
| Feature *Windows Hypervisor Platform* desligada | `Get-CimInstance Win32_OptionalFeature`: `HypervisorPlatform`, `Microsoft-Hyper-V-All` e `VirtualMachinePlatform` = **habilitadas** | descartado |
| Hipervisor não está rodando | `HvHost`, `vmcompute` e `vmms` rodando; WSL2 funciona | descartado |
| Conflito com hipervisor de terceiros | nenhum processo/driver de VirtualBox, VMware ou anti-cheat | descartado |
| Emulador desatualizado | atualizado de 36.5.11 para **37.1.11** (canal estável) | **não resolveu** |
| Alternativa sem aceleração (`-accel off`) | inicia mas o boot morre: a imagem API 36 exige AVX/F16C, que o TCG não emula | inviável |
| HVCI / Memory Integrity ligado | `HypervisorEnforcedCodeIntegrity.Enabled = 0`, `SecurityServicesRunning = 0` | descartado |

Fato que resta: o processo do emulador roda **sem elevação** (a conta é administradora, mas o UAC
filtra o token) e o VBS está ativo (`VirtualizationBasedSecurityStatus = 2`).

**Próximos passos, na ordem:**

1. **Testar elevado.** Em um PowerShell **como administrador**:
   ```powershell
   $sdk = "$env:LOCALAPPDATA\Android\Sdk"
   & "$sdk\emulator\emulator.exe" -avd bix_tv_api36 -no-window -no-snapshot -no-boot-anim
   ```
   Se subir, o problema é privilégio e a solução é rodar o emulador (ou o Android Studio) elevado.
2. **Desligar o VBS e reiniciar.** É a correção definitiva mais relatada para `hr=80070005`:
   *Segurança do Windows → Segurança do dispositivo → Isolamento do núcleo* e desligue tudo; ou,
   em um PowerShell como administrador:
   ```powershell
   bcdedit /set hypervisorlaunchtype auto
   ```
   seguido de **reinicialização**. Atenção: desligar o VBS afeta o WSL2 e o Docker Desktop.
3. **Reiniciar o Windows.** A associação ao grupo `Hyper-V Administrators` só é lida pelo driver do
   hipervisor no boot; um logoff/logon pode não bastar.

**Correções originalmente sugeridas (mantidas para histórico):**

1. Adicionar a conta ao grupo `Hyper-V Administrators` e **sair e entrar de novo** no Windows:
   ```powershell
   # PowerShell COMO ADMINISTRADOR
   Add-LocalGroupMember -Group "Hyper-V Administrators" -Member "$env:COMPUTERNAME\$env:USERNAME"
   ```
2. Confirmar que a feature *Windows Hypervisor Platform* está ligada e **reiniciar**:
   ```powershell
   # PowerShell COMO ADMINISTRADOR
   Enable-WindowsOptionalFeature -Online -FeatureName HypervisorPlatform -All
   ```
3. Se ainda falhar, rodar o Android Studio (ou o emulador) como administrador, para isolar se o
   problema é só de privilégio.

**Sem aceleração** (`-accel off`) o emulador chega a iniciar, mas não conclui o boot da imagem
de API 36 em tempo útil — não serve como alternativa de trabalho.

**Modo com janela** falha à parte por falta de `opengl32sw.dll` nas bibliotecas Qt do emulador
(`Critical: Failed to load opengl32sw`). Enquanto isso não for resolvido, use `-no-window` e
tire screenshots com `adb exec-out screencap -p`.

## 6. Backend local visto pelo emulador

O app usa `http://10.0.2.2:8000` no build `debug` (o `10.0.2.2` é o host da máquina visto de
dentro do emulador) e `https://bixplayer.pro` no `release`.

```bash
cd backend && uv run uvicorn app.main:app --host 0.0.0.0 --port 8000
# de dentro do emulador:
adb shell curl -s http://10.0.2.2:8000/api/v1/health
```

O `--host 0.0.0.0` é obrigatório: com o padrão `127.0.0.1` o emulador não alcança a API.

## 7. Build e assinatura

```bash
cd android
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
./gradlew :app:assembleDebug      # app/build/outputs/apk/debug/app-<abi>-debug.apk
./gradlew :app:assembleRelease    # app/build/outputs/apk/release/app-<abi>-release.apk
./gradlew :app:testDebugUnitTest :app:lintDebug
```

Desde o M4 (libVLC, [`ADR-006`](ADR-006-libvlc-fallback.md)) o build sai em **APKs por ABI** mais
um universal. O `release` só empacota ARM (`ndk.abiFilters` no build type) e comprime as
bibliotecas nativas (`jniLibs.useLegacyPackaging = true`) — sem isso o `libvlc.so`, 46 MB por ABI
armazenado sem compressão, levava o universal a 216 MB.

| APK (release 1.1.0) | Tamanho | Uso |
|---|---|---|
| `app-universal-release.apk` | 50 MB | link `/downloads/app.apk` (qualquer TV box ARM) |
| `app-arm64-v8a-release.apk` | 29 MB | boxes 64 bits, se quiser oferecer download menor |
| `app-armeabi-v7a-release.apk` | 25 MB | boxes antigos com userland 32 bits |
| `app-x86_64-debug.apk` | 51 MB | só emulador |

Para apontar o `debug` a outro backend sem editar o projeto (o emulador vê a máquina como
`10.0.2.2`): `./gradlew :app:assembleDebug -Pbix.apiBaseUrl.debug=http://10.0.2.2:8001/`.

| Build | applicationId | API | Assinatura |
|---|---|---|---|
| `debug` | `<bix.applicationId>.debug` | `http://10.0.2.2:8000` | keystore de debug do SDK (automática) |
| `release` | `<bix.applicationId>` | `https://bixplayer.pro` | `android/keystore.properties` → `.jks` (R8 + shrink de recursos) |

O release só sai **assinado** se existir `android/keystore.properties` (gitignored, assim como
qualquer `*.jks`); sem ele o Gradle gera `app-release-unsigned.apk`. Formato do arquivo:

```properties
storeFile=keystore/bixplayer-release.jks
storePassword=...
keyAlias=bixplayer
keyPassword=...
```

Para criar um keystore novo (uma vez por marca):

```bash
"$JAVA_HOME/bin/keytool" -genkeypair -v -keystore keystore/bixplayer-release.jks -storetype PKCS12   -alias bixplayer -keyalg RSA -keysize 4096 -validity 10950   -dname "CN=Bix Player, OU=Mobile, O=Bix Player, L=Sao Paulo, ST=SP, C=BR"
"$ANDROID_HOME/build-tools/36.1.0/apksigner" verify --print-certs app/build/outputs/apk/release/app-release.apk
```

> Guarde o `.jks` e as senhas fora do repositório (cofre de senhas). Perder o keystore significa
> não conseguir atualizar o app já instalado nas TVs: o Android exige a mesma assinatura.

## 8. White label: trocar nome, pacote e ícone

Tudo o que identifica a marca está em `android/gradle.properties`; o código não muda:

```properties
bix.applicationId=pro.bixplayer.app      # pacote (um por marca; define a identidade do app na TV)
bix.appName=Bix Player Pro               # nome mostrado no launcher
bix.versionName=1.2.0
bix.versionCode=3
bix.apiBaseUrl.release=https://bixplayer.pro/
bix.apiBaseUrl.debug=http://10.0.2.2:8000/
```

- **Ícone, banner e logo padrão**: gerados a partir de `docs/brand/logo-original.png` pelo
  roteiro do fechamento do M5 (Pillow): `mipmap-*/ic_launcher_foreground.png` (adaptive icon,
  fundo `@color/ic_launcher_background` = `#050404`), `mipmap-*/ic_launcher*.png` (API < 26),
  `drawable-xhdpi/app_banner.png` (320×180 dp, launcher da TV) e `drawable-xhdpi/brand_logo.png`
  (lock-up mostrado na splash e na home quando a revenda não enviou logo — `BrandLogo`).
- **Cores**: `app/src/main/java/pro/bixplayer/player/ui/theme/Color.kt` (`BixAccent` `#FF8A00`
  é a cor de foco e dos botões; fundo `#050404`). Texto sobre o acento é escuro: branco sobre
  `#FF8A00` fica em 2,4:1 e reprova no WCAG AA.
- **Logo, fundo, banners e nome da plataforma** *não* são do build: vêm do painel, por revenda,
  em `GET /api/v1/device/config`, e mudam sem republicar o APK.
- Cada marca deve ter o próprio keystore (seção 7) e o próprio `applicationId`; dois APKs com o
  mesmo pacote e assinaturas diferentes não coexistem no mesmo aparelho.
- Idiomas: `values/` (pt-BR, padrão), `values-en/`, `values-es/`. O idioma é escolhido no app
  (Configurações → Idioma) e aplicado em tempo real por `BixLocale`/`AppLocale`.

## 9. Testar com a playlist de fixture local

O backend serve uma lista de 1.200 canais gerada por script, com streams reais para exercitar o
player sem depender de um provedor:

```bash
cd backend
uv run python scripts/make_fixture.py --download-sample
# uploads/fixture.m3u         1200 canais, 2000 filmes, 50 séries, 2 entradas inválidas (o parser pula)
# uploads/epg.xml             XMLTV de -6 h a +48 h para os 200 primeiros canais (url-tvg no header)
# uploads/fixture/sample.ts   ~2 MB de MPEG-TS (4 segmentos do stream público da Mux)
# uploads/fixture/sample.mp4  filmes e episódios (VOD com seek e progresso)
# uploads/fixture/compat.ts   bytes WMV servidos como .ts: o Media3 falha e o app cai no libVLC
uv run python scripts/make_fixture.py --movies 20000 --series 500   # fixture de estresse do M4
uv run uvicorn app.main:app --host 0.0.0.0 --port 8000
```

| Canais | URL | Exercita |
|---|---|---|
| 1–3 | `https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8` | HLS público, multi-bitrate |
| 4–6 | `http://10.0.2.2:8000/uploads/fixture/sample.ts` | TS local (extractor tolerante) |
| 7 | `http://10.0.2.2:8000/uploads/fixture/compat.ts` | fallback para libVLC ("Modo de compatibilidade") |
| 8+ | `http://10.0.2.2:8000/fake/stream/N.ts` (404) | retry automático 2× + botão "Tentar novamente" |

Medido no AVD de TV (x86_64, 05-06/09/2026) com a fixture de estresse: sync completo de 1.200
canais + 20.000 filmes + 500 séries (12.000 episódios) em **4,2 s**, EPG de 10.832 programas em
**1,2 s** logo depois, sem ANR — bem abaixo do limite de 60 s do plano.

> Se outro projeto ocupar `127.0.0.1:8000` na máquina, o emulador (que chega pelo loopback do
> host) cai nele e o app mostra "O servidor não respondeu". Saída: subir a API em outra porta
> (`--port 8001`), gerar a fixture com `--host http://10.0.2.2:8001`, migrar as playlists no
> `/painel` (Hosts/DNS) e compilar o debug com `-Pbix.apiBaseUrl.debug=http://10.0.2.2:8001/`.

Cadastre a playlist no `/painel` (URL `http://10.0.2.2:8000/uploads/fixture.m3u`, tipo M3U) para
o dispositivo do emulador, ou adicione pelo próprio app na tela Playlist (o MAC está lá desde a
1.3.0; antes era a tela de ativação). Roteiro validado no
M3 (capturas em `docs/screens/android/m3/`): ativação → cadastro → sync (1200 canais) → TV ao vivo
→ prévia → tocar canal 1 (HLS) → zapping até o 4 (TS) → digitar 7 (erro/retry) → MENU (faixas) →
←/→ (lista rápida) → favoritar → busca → trocar playlist → idioma → sair.

Sequência de teclas útil para reproduzir pelo `adb`:

```bash
adb shell input keyevent KEYCODE_DPAD_CENTER    # OK
adb shell input keyevent KEYCODE_MENU           # favoritar (lista) / faixas (player)
adb shell input keyevent KEYCODE_7              # sintoniza o canal 7 no player
adb shell input text "Canal%s12"                # digita na busca (%s = espaço)
adb exec-out screencap -p > docs/screens/android/m3/xx.png
```

## 10. Publicar o APK para as TVs

```bash
cd android && ./gradlew :app:assembleRelease && cd ..
./deploy/deploy.sh --apk android/app/build/outputs/apk/release/app-universal-release.apk
```

Publicado no fechamento do M4: `1.1.0` (versionCode 2), universal ARM de 50 MB, com
`min_app_version=1.1.0` no admin — instalações 1.0.0 recebem a tela de atualização no boot.

Histórico do M5: `1.2.0` (3, marca), `1.2.1` (4, entrada única + diagnóstico; `min_app_version`
forçada para 1.2.1), `1.2.2` (5, medidas de memória/HEVC), `1.2.3` (6, RAM via `/proc/meminfo` e
`X-App-Version` em toda chamada), `1.2.4` (7, cor base `#050404`), `1.3.0` (8, home antes da
ativação — F2-001). A partir da 1.2.2 as publicações **não** mexem em `min_app_version` (segue
1.2.1): o cliente atualiza quando quiser, e o admin passa a mostrar a versão real do device na
primeira chamada de `config` feita pela 1.2.3 (M5-022).

O `deploy.sh` copia o arquivo para `deploy/downloads/app.apk` no servidor (bind mount lido pelo
Caddy) e ele fica em `https://bixplayer.pro/downloads/app.apk`. Depois, em **Admin →
Configurações**, aponte `apk_url` para essa URL e ajuste `min_app_version` quando quiser forçar a
atualização: o app compara com o próprio `versionName` no boot e mostra a tela de atualização.

## 11. Decisões que valem lembrar

- **Home antes da ativação (F2-001, 1.3.0)**: não existe mais a "parede" do MAC. Sem lista
  utilizável (`AppConfig.canWatch == false`, regra em `ui/demo/DemoMode`) o `BootRouting` manda
  para `HOME` mesmo assim e `LocalDemoState` liga o **modo demonstração**: os cartões da home
  abrem e TV ao vivo / Filmes / Séries / Guia mostram a `DemoShowcase` (cartazes de paisagem
  desenhados em Canvas, sem bitmaps) com o MAC e um botão para a tela **Playlist**. A tela
  Playlist (`Routes.PLAYLIST`, `PlaylistScreen`) é a antiga tela de ativação (MAC, QR, "já
  cadastrei", playlist manual, agora com "Voltar") enquanto não dá para assistir, e o gerenciador
  de playlists depois. Na TV o cartão Playlist recebe o primeiro foco e carrega o MAC; no celular
  uma faixa laranja acima da barra ("Sem lista ativa…") abre a mesma tela. O `BootViewModel`
  consulta `GET /device/config` a cada 20 s enquanto o device não pode assistir (3/min contra
  um orçamento de 20/min por device): quando a revenda cadastra o MAC a home vira a real sozinha
  e o sync começa. `EXPIRED` e atualização obrigatória continuam sendo paredes. Validado nos três
  AVDs (capturas em `docs/screens/android/f2/`): ativação automática em 13 s (TV), 8 s (celular)
  e 16 s (box 1 GB, 116 MB de PSS após o sync).
- **Entrada única (M5-017)**: `LaunchActivity` recebe `LAUNCHER` e `LEANBACK_LAUNCHER` e escolhe
  em runtime entre `TvActivity` e `MobileActivity` (`UiModeDecider`: `UiModeManager` = TV, feature
  `leanback`/`television` ou ausência de touchscreen → TV). TV boxes AOSP não têm leanback e
  disparam `LAUNCHER`; por isso a decisão não pode vir do manifesto. Override em Configurações →
  *Modo de interface* (`ui_mode` no DataStore). A UI de celular continua navegável por D-pad:
  `Modifier.onSelect` é um só para as duas famílias (OK/ENTER no KEY UP + toque no mesmo nó).
- **Diagnóstico (M5-013)**: `util/Diagnostics` planta uma `Timber.Tree` com arquivo rotativo
  (`files/diagnostics/app.log`), captura exceções não tratadas (`crash-*.txt`) e, no boot
  seguinte, lê `ApplicationExitInfo` (Android 11+) para crashes nativos/OOM/ANR (`exit-*.txt`).
  *Configurações → Enviar diagnóstico* e o envio automático após crash chamam
  `POST /device/diagnostics`; o admin lê no detalhe do dispositivo.
- **Foco inicial (M5-014)**: `FocusRequester.requestFocusWithRetry()` insiste até o foco ser
  concedido; em boxes lentas a primeira tentativa acontece antes de a janela ter foco e a tela
  ficava sem item focado (o primeiro OK só "acordava" o foco).
- **BACK**: com `targetSdk 36` o sistema entrega o voltar por `OnBackInvokedCallback`; tratar
  `Key.Back` em `onKeyEvent` faz o NavHost e a tela "voltarem" duas vezes e esvazia o grafo. Use
  `BackHandler` (o `PlayerScreen` fecha painéis antes de sair).
- **Um player só**: `PlayerSession` é um singleton com um `Media3Engine`; a prévia da TV ao vivo e o
  player em tela cheia trocam de superfície (`VideoSurface`), nunca de instância. Desde o M4 a
  sessão pode trocar o motor para `VlcEngine` (libVLC real) quando o Media3 falha com erro de
  fonte/decodificação após os retries — `ADR-006`.
- **Toque × controle remoto**: `Modifier.onSelect` (`ui/components/Select.kt`) é OK/ENTER na TV e
  `clickable` no celular, nunca os dois — `clickable` também reage ao DPAD_CENTER e dispararia a
  ação duas vezes. `Modifier.tap` é só toque, para linhas que já tratam várias teclas.
- **Celular sem tela inicial**: `MobileActivity` abre direto em TV ao vivo com a bottom bar
  (TV / Filmes / Séries / Guia / Mais); a rota `home` redireciona e por isso o primeiro sync da
  playlist é disparado pelo `BixNavHost`, não pela `HomeScreen`. Cada aba faz `popUpTo(live)`
  para que Filmes → Séries recrie a tela de catálogo (e sua ViewModel) em vez de reaproveitar.
- **Player no celular**: trava em paisagem, esconde as barras (`WindowInsetsControllerCompat`),
  desenha sob o recorte da câmera (`LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`) e o `Scaffold`
  zera os insets nessa rota; `configChanges` no manifesto evita recriar a activity ao girar. PiP
  entra em `onUserLeaveHint` enquanto algo toca.
- **`cast` é palavra reservada no SQLite**: a coluna de elenco chama-se `actors` (Room quebrava em
  `COALESCE(:cast, cast)`).
- **IME na TV**: um campo de texto focado abre o teclado; por isso a busca é uma linha focável que
  só vira campo ao pressionar OK (`SearchRow`).
- **Ids de canal M3U**: hash de nome+URL com sufixo de ocorrência (`M3uRemoteIds`); só a URL não
  serve porque provedores repetem o mesmo stream em várias categorias.
- **Windows/Git Bash**: comandos que passam caminhos remotos ao `adb`/`ssh` precisam de
  `MSYS_NO_PATHCONV=1`; para copiar o banco do emulador use `adb exec-out run-as ... cat` (o
  `adb shell` converte quebras de linha e corrompe o SQLite).

## 12. Validação do M4 (AVDs)

Capturas em `docs/screens/android/m4/`: `00–24` TV (foco no zapping, filmes, continuar
assistindo, séries com próximo episódio, EPG, PIN, layout em grade, fallback VLC), `30–45`
celular (ativação com Copiar MAC/Compartilhar, bottom bar, grade de 3 colunas, teclado nativo na
busca, player em paisagem, PiP, retorno de background, rotação) e `50–59` passagem final na TV
com o build 1.1.0. Pendente de hardware real: TV box física (item 2 do bloco 0), decodificação
libVLC em ARM, PiP em aparelhos que o restringem e o comportamento do recorte da câmera em
celulares com notch.

## 13. Dispositivo de referência mínimo e hardware real (M5-016)

**Referência mínima: MXQ Pro 4K 5G** (box do cliente). Dados **reais**, lidos do diagnóstico
enviado pelo app em 08/09/2026 (a suposição anterior de H3/RK3228A e "só H.264" estava errada):

| Item | Valor real |
|---|---|
| `Build.VERSION.SDK_INT` | **29 (Android 10)** — `release_reported` diz "11.1", patch 2019-12-05: o `build.prop` mente na string, não na API |
| SoC / hardware | **Allwinner H616** (`hardware=sun50iw9p1`, `device=titan-p1/exdroid`), fabricante declarado "Google", modelo "TV BOX" |
| ABI | `armeabi-v7a,armeabi` (userland 32 bits, mesmo o SoC sendo 64 bits) |
| Decodificadores de vídeo | **HW**: `OMX.allwinner.video.decoder.avc`, `.hevc`, `.vp9`, `.mpeg2`; SW: `c2.android.*`/`OMX.google.*`; AV1: nenhum. O log mostra `setPortMode … DynamicANWBuffer failed -1010` no decodificador AVC (ruído do vendor; a reprodução seguiu) |
| RAM | `ActivityManager.totalMem` devolve **16 MB** (bug do firmware, M5-021); `availMem` 202 MB no momento; heap do app 43/128 MB, `largeMemoryClass` 256 MB. Até a 1.2.2 o `lowRam` ficou `true` **por acidente** (16 MB ≤ 1,5 GB); a partir da 1.2.3 vale o `MemTotal` de `/proc/meminfo`. Se a box tiver os 1 GB anunciados (H616 com 1 GB é a configuração comum do MXQ Pro 4K 5G; `availMem` de 202 MB é coerente com isso), `lowRam` continua `true` pelo caminho correto; se o kernel reportar ≥ 1,5 GB, o app sai do modo econômico. **Confirmar com o primeiro diagnóstico da 1.2.3**: campo `meminfo_total_mb` e a linha `device class: ram=… (am=…, kernel=…) lowRam=…` |
| Sinais de UI | `uiModeTv=true leanback=true tvFeature=true touchFeature=false touchInput=false` → **TV**. A box **tem** leanback; a 1.2.0 abria a UI de celular porque o launcher dela dispara `LAUNCHER`, não `LEANBACK_LAUNCHER` |

Consequências no código:

- **Nunca decidir por `Build.VERSION.RELEASE`**; só `SDK_INT`, memória e lista de codecs. O
  diagnóstico grava `release_reported` apenas como informação. Também não confiar em
  `ActivityManager.MemoryInfo.totalMem` sozinho: `DeviceClass` usa `/proc/meminfo` quando o
  valor é implausível (M5-021).
- **Memória** (`util/DeviceClass`, `lowRam` = RAM ≤ 1,5 GB ou `isLowRamDevice`): Coil com cache
  de 5 % do heap, RGB_565 e 48 MB em disco; Paging com página 24 e `maxSize` 120; cache de
  posters esvaziado ao abrir o player e em `onTrimMemory`; `largeHeap=false`.
- **Vídeo**: sem decodificador HEVC de hardware o Media3 prefere H.264 nas playlists com
  variantes; HEVC puro cai no VLC pelo fallback normal; em `lowRam` o vídeo é limitado a 1080p e
  os buffers a 20 s / 12 MB.

### AVD de baixo recurso (`bix_box_lowend`)

```bash
# imagem Android 9 x86 (o ';' precisa chegar inteiro ao sdkmanager — ver §2)
cmd /c "echo y| sdkmanager.bat --install \"system-images;android-28;google_apis;x86\""
avdmanager create avd -n bix_box_lowend -k "system-images;android-28;google_apis;x86" -d "tv_1080p" --force
# config.ini: hw.ramSize=1024, hw.gpu.enabled=no (vídeo por software), hw.keyboard=yes, disk.dataPartition.size=4G
emulator -avd bix_box_lowend -no-snapshot -gpu swiftshader_indirect -port 5558
```

Smoke test de reprodução nesse AVD: instalar o `app-universal-debug.apk` (a imagem é x86 32
bits), ativar contra a API local, sincronizar a fixture de 20k filmes, abrir a grade de filmes,
reproduzir um filme e um canal, e ler `adb shell dumpsys meminfo <pacote>` em cada etapa.

Esse AVD reproduz o ponto central da box do cliente: **não tem dispositivo de entrada de toque**
(o perfil `tv_1080p` sobre uma imagem de celular), então `touchInput=false` e a entrada única
abre a UI de TV sem leanback e sem `UiModeManager` de TV. Os números da tabela abaixo vieram
dele (`dumpsys meminfo`, TOTAL PSS). A referência de conforto numa box de 1 GB é manter o app
abaixo de ~150 MB de PSS com o player aberto; o LMK do Android 9 começa a matar apps de
segundo plano bem antes disso, mas o app em primeiro plano só cai perto do esgotamento total.

### Resultados

| Aparelho | Android | Chip / ABI | Release | Resultado |
|---|---|---|---|---|
| **MXQ Pro 4K 5G** (cliente) | SDK 29 (Android 10; string "11.1" falsa) | Allwinner H616 `sun50iw9p1`, armeabi-v7a, RAM total mal reportada (16 MB) | 1.2.0 | Abria a UI de celular (M5-017: launcher dispara `LAUNCHER`); D-pad só em modo mouse; fechamentos ao reproduzir filme (M5-013) |
| **MXQ Pro 4K 5G** (cliente) | idem | idem | **1.2.2** | Cliente relata funcionamento normal. Diagnóstico manual de 08/09/2026: entrada única abriu a UI de TV pelo automático; 3 reproduções com o decodificador AVC de hardware; sem crash Java, sem evidência de morte do processo (o `ApplicationExitInfo` não existe no SDK 29, então uma morte por memória só apareceria como ausência de crash file); memória do app 43 MB de heap. M5-013 fica em observação |
| **AVD `bix_box_lowend`** (referência) | 9 (SDK 28), imagem `google_apis` x86, perfil `tv_1080p` | x86, 1 GB (`hw.ramSize=1024`; o kernel reporta 1,46 GB), GPU por software | 1.2.2 (debug universal) | Sem dispositivo de toque → `UiModeDecider` escolhe **TV** mesmo sem leanback e com a feature de touchscreen declarada: é a simulação da box AOSP. Sync de 1.200 canais + 20.000 filmes + 500 séries em **15,4 s**. PSS: home 72–75 MB, grade de filmes após 20–40 linhas 87–106 MB, detalhe 86 MB, **filme tocando 93–97 MB**, lista de TV com prévia 100 MB, **canal HLS em tela cheia 111 MB**, canal TS 119 MB, fallback VLC 118 MB; memória livre do sistema nunca abaixo de 750 MB; nenhum `am_kill`/crash em ~20 min de uso |

| Aparelho | Android | Chip / ABI | Release | Resultado |
|---|---|---|---|---|
| AVD `bix_tv_api36` | 16 (SDK 36) Android TV | x86_64 (+arm64 por tradução) | 1.2.1 | Entrada única abre a UI de TV; 1 OK toca filme e episódio; override Celular/Automático pelo controle; diagnóstico enviado e visível no admin |
| AVD `Pixel_10_Pro_XL` | 16 (SDK 36) | x86_64 (+arm64) | 1.2.1 | Entrada única abre a UI de celular; D-pad navega a lista e toca canal com 1 OK; `hw.touchScreen=no` não remove o toque da imagem (ver M5-017) |

Ao receber um diagnóstico da box (`Configurações → Enviar diagnóstico`), completar a linha com
`sdk_int`, `hardware`, `video_decoders`, tempo de sync da lista real, canais que caíram no VLC e
o pico de memória.
