# ADR-007 — Versão da Google Play

**Status:** aceito — 2026-11-17

## Contexto

O Anexo I (seção 4) prevê a submissão à Google Play quando pedida, com a distribuição principal
seguindo pelo APK do link. A conta de organização foi criada em 2026-11-17 (titular: a empresa do
Lizandro, D-U-N-S 627514143). O app como está fere quatro regras da loja:

1. **Atualização fora da Play.** A tela de atualização obrigatória mostra o link e o QR do APK, e a
   política de abuso de dispositivo proíbe atualizar o app por outro meio.
2. **Pagamento fora da Play.** O QR do revendedor em Configurações → Playlist leva direto a quem
   vende a licença. A política de pagamentos aceita informar que há outras formas de compra, mas não
   um link direto para elas.
3. **Política de privacidade.** É obrigatória no Console e dentro do app. Não existia.
4. **Acesso do revisor.** Sem revendedor, o aparelho só mostra a tela de ativação com o MAC, e o
   Google exige um caminho para avaliar o app.

## Decisão

1. **Uma flag, não um flavor.** `BuildConfig.PLAY_STORE` vale `true` em todo build de bundle (o AAB
   só existe para a loja) ou com `-Pbix.store=play`. Os APKs do link continuam iguais, e o caminho
   de saída do `deploy/publicar.cmd` não muda.
2. **Atualização pela loja.** Com a flag, a tela de atualização obrigatória abre a página do app na
   Play Store (`market://details`, com a URL https como reserva) e não mostra link nem QR do APK.
3. **QR do revendedor escondido** na versão da loja. O texto "fale com seu revendedor" fica.
4. **Política de privacidade** pública em `https://bixplayer.pro/privacidade` (página do SvelteKit,
   sem login), com o endereço visível em Configurações nas duas versões. No celular o endereço abre
   o navegador; na TV só é exibido, porque a TV não deve abrir navegador.
5. **Código de revisão.** `POST /api/v1/device/review-code` recebe um código digitado na tela de
   ativação da versão da loja. Se ele bater com `play_review_code` (configuração do admin, comparação
   em tempo constante), o servidor vincula o aparelho à revenda `play_review_reseller` por 14 dias,
   com a lista `play_review_playlist_url`, **sem consumir crédito**, e grava `device.review_activate`
   no `audit_log`. Há limite de tentativas por aparelho e por IP. Com o código vazio, a rota recusa
   tudo. O código vai só nas instruções de acesso do Console.
6. **Lista de demonstração** em `web/static/demo/lista-revisao.m3u`, só com streams públicos de teste
   (Apple, Mux/Blender CC-BY). É a única lista que a plataforma serve, e existe só para a revisão.
   Ela aponta para variantes de **resolução fixa**, com áudio no mesmo arquivo. Com o stream
   adaptativo, que troca de resolução no meio, o zoom de "tela cheia" do VLC fica calculado para a
   primeira resolução (M5-045) e o revisor veria a imagem ampliada.
   A regra de neutralidade continua valendo para todo o resto: nenhum conteúdo de provedor é
   hospedado nem listado.
7. **Assinatura.** No Console, a chave de assinatura do app é a nossa
   (`android/keystore/bixplayer-release.jks`), não a gerada pelo Google. Assim a versão da loja e a
   do link atualizam uma à outra. A escolha só pode ser trocada antes do primeiro lançamento em teste
   aberto ou produção.

## Consequências

- A versão da loja e a do link compartilham pacote, assinatura e `versionCode`, e diferem só pela
  flag. Toda versão nova sobe com o mesmo número nos dois canais.
- O revisor ativa o aparelho com o código e assiste aos streams de teste. Um código que vaze dá no
  máximo 14 dias de uma lista de teste, sem crédito nem conteúdo real, e o admin troca o código
  quando quiser.
- A aprovação continua sendo decisão do Google (Anexo I, seção 4). Players de IPTV com revenda são
  uma categoria de risco; o APK do link segue como canal principal.
