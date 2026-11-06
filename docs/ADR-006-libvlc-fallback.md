# ADR-006 — libVLC como motor de compatibilidade

**Status:** aceito — 2026-09-06; **VLC virou o motor padrão em 2026-11-05** (ver "Revisão" no fim)

## Contexto

O CLAUDE.md prevê libVLC como fallback do player. No M3 o `VlcEngine` era um stub; o M4 exige a
implementação real: provedores IPTV entregam áudio AC-3/E-AC-3, vídeo MPEG-2 e contêineres que
o Media3 rejeita em muitos aparelhos (sem decoder de plataforma). O plano também pede regra de
fallback automática, escolha manual por playlist e avaliação do impacto no tamanho do APK.

## Decisão

1. **Dependência:** `org.videolan.android:libvlc-all:3.7.5` — a última versão estável da série
   3.x no Maven Central (a 4.0 ainda é *eap*). Fica no catálogo (`libs.libvlc`).
2. **Mesma interface:** `VlcEngine` implementa `PlayerEngine` (estado, faixas, seek, posição).
   O `PlayerSession` continua sendo o único dono do motor ativo; as telas só passam
   `session.player` (Media3) ou `session.vlcPlayer` (VLC) para o `VideoSurface`, que escolhe entre
   `PlayerView` e `VLCVideoLayout`.
3. **Regra de fallback:** só quando o Media3 esgota as duas tentativas com erro **de fonte ou
   decodificação** (códigos 3000–5999: parsing, decoder, faixa de áudio). Erros de rede/HTTP
   (2xxx) não trocam de motor — trocar não resolveria e esconderia a causa. A troca acontece no
   mesmo item, sem sair da tela, com o aviso discreto "Modo de compatibilidade".
4. **Preferência por playlist** em Configurações → Player: *Automático* (padrão até a 1.12.0), *Media3* ou
   *VLC*, guardada no DataStore (`engine_<playlistId>`). *Media3* desliga o fallback; *VLC*
   abre tudo direto no libVLC.
5. **Tamanho:** o AAR traz `armeabi-v7a`, `arm64-v8a`, `x86` e `x86_64`. Habilitamos `splits.abi`
   (v7a, arm64, x86_64 + universal). O link `/downloads/app.apk` continua sendo o **universal**
   — TV boxes chegam com qualquer arquitetura e um único link é o que o revendedor consegue
   repassar; os APKs por ABI ficam em `outputs/apk/release/` para quem quiser distribuir menor.
   Os tamanhos medidos estão em `docs/ANDROID.md`.
6. **Teste:** `net.sf.kxml:kxml2` entra **só em `testImplementation`** para o parser XMLTV rodar na
   JVM (o `android.jar` de testes devolve `null` para `XmlPullParserFactory`). Não vai para o APK.

## Consequências

- O APK universal cresce (libVLC ≈ 4 ABIs de bibliotecas nativas); o split por ABI mantém os
  instaláveis individuais menores.
- `-keep class org.videolan.libvlc.**` no ProGuard: o JNI resolve callbacks por nome.
- A prévia da TV ao vivo também pode cair para VLC (mesmo `PlayerSession`), então a troca de
  superfície é transparente para o usuário.
- Fixture: o canal 7 aponta para um arquivo AC-3 (`uploads/fixture/sample.ac3`) justamente para
  reproduzir o caminho Media3 → erro de decoder → VLC no emulador.

## Revisão (2026-11-05, F2-019): VLC como padrão

O cliente testou em aparelhos reais e pediu VLC em vez de *Automático*: "ficou mais compatível
para abertura dos conteúdos". Junto veio o relato de canais só com áudio.

- **Padrão:** sem escolha gravada, o app usa VLC, já na primeira prévia. Quem escolheu *Automático*
  ou *Media3* em Configurações → Player mantém a escolha. A ordem do botão passou a ser VLC,
  Automático, Media3.
- **Rede de segurança no sentido contrário:** se o VLC falhar depois das tentativas, o item é aberto
  uma vez no Media3, com o mesmo aviso "Modo de compatibilidade". Cada item troca de motor no
  máximo uma vez, então canal fora do ar termina na mensagem de erro, sem laço. *Tentar novamente*
  volta ao motor preferido.
- **Só áudio:** o Media3 não avisa quando só falta o decoder de vídeo: toca o áudio sobre a tela
  preta, sem erro, e o fallback nunca disparava. Agora `onTracksChanged` trata faixa de vídeo ou
  áudio sem decoder como erro de formato, e o fallback age na hora. Em *Media3* forçado aparece a
  mensagem de formato não suportado em vez da tela preta.
- **Preferência aplicada antes da prévia:** `session.preference` só era lida ao abrir o player em
  tela cheia, então a primeira prévia sempre rodava no Media3 e o mesmo canal continuava nele
  depois. `applyEnginePreference` roda também antes da prévia e na volta à tela.
- **Volta do player:** sair da tela cheia liberava o motor, porque o `PlayerScreen` observava a
  entrada de navegação, que também para ao sair. A prévia ficava sem imagem até trocar de canal.
  Agora observa a Activity, e `play()` reabre o item se o motor tiver sido liberado.
- **Superfície:** só o `VLCVideoLayout` dono da superfície pode soltá-la. Antes, na passagem prévia
  → tela cheia, a saída da prévia soltava a superfície que a tela cheia acabava de pegar.
- **Tela cheia:** o ramo VLC do `VideoSurface` ignorava o `resizeMode`, então o vídeo nunca
  preenchia. Agora usa `SURFACE_FIT_SCREEN` em *Tela cheia* e `SURFACE_BEST_FIT` em *Tamanho
  original*. O zoom é calculado a partir da lista de faixas do `Media`, que o libVLC lê uma vez e
  guarda. Em stream TS essa lista ainda não tem o tamanho no início: lida cedo, fica 0x0 para sempre
  e o zoom sai 1,0, o vídeo no tamanho nativo, pequeno no meio da tela. Por isso `applyFit` só lê a
  lista 1 s depois de o vídeo aparecer (`Vout` ou `Playing`) e até lá mostra a imagem inteira. Stream
  que nunca informar o tamanho fica com a imagem inteira, sem zoom. Tentar esvaziar o cache com
  `parseAsync` não funciona: o `Media` em reprodução recusa.

Consequências: o caminho mais testado até aqui (Media3) deixa de ser o principal. Ao voltar para a
prévia, o VLC leva alguns segundos para retomar a imagem (recria a saída de vídeo), mais que o
Media3. No emulador, sem decodificação por hardware, o HLS público multi-bitrate em alta resolução
congelou no VLC e rodou no Media3, enquanto HLS, TS H.264 e TS 1080p locais rodaram bem no VLC: vale
observar em aparelho real. Memória na box de 1 GB: `docs/ANDROID.md`.

