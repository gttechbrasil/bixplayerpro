# ADR-006 — libVLC como motor de compatibilidade

**Status:** aceito — 2026-09-06

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
4. **Preferência por playlist** em Configurações → Player: *Automático* (padrão), *Media3* ou
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
