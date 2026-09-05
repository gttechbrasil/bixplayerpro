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
| Emulator | 36.5.11 | `emulator\emulator.exe` |
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
