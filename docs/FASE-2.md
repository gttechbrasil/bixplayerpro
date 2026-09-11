# Fase 2 — pedidos fora do Anexo I

Lista viva dos itens que ficaram fora da v1 (Seção 3 do Anexo I) e dos pedidos surgidos na
homologação. Cada item traz origem, o que muda em relação ao contratado e uma estimativa
grosseira para a próxima proposta. Nada daqui entra no M5.

| Id | Item | Origem | O que muda | Estimativa |
|---|---|---|---|---|
| F2-001 ✅ **entregue na 1.3.0 (11/09/2026)** | **Home antes da ativação.** Ao abrir sem playlist, o app entra direto na tela inicial (TV ao vivo, Filmes, Séries) com conteúdo de demonstração (cartazes genéricos), em vez da tela do MAC. O MAC e o QR passam para um item de menu "Playlist" (TV e celular). A ativação continua automática: assim que a revenda cadastra o MAC, a home carrega a lista de verdade. | Cliente, 11/09/2026 (referência: comportamento do "Pop Play") | Contraria o Anexo I §Ativação ("ao abrir, o app exibe o MAC"). Toca `LaunchActivity`/`BootScreen`, `HomeScreen` das duas UIs, novo destino "Playlist" com o conteúdo da `ActivationScreen`, estado "sem lista" nos catálogos, polling de ativação em segundo plano e textos. Não muda backend nem painel. Implementação: `ui/demo/DemoMode`, `DemoShowcase`, `PlaylistScreen`, polling de 20 s no `BootViewModel`; detalhes em `ANDROID.md` §11. | Feito em 1 dia; reteste na MXQ pendente (cliente) |

## Observação sobre a motivação do F2-001

O cliente entende que a home de demonstração faz o app parecer "100 % legal" para o Google.
Isso não procede tecnicamente: o Play Protect não avalia a primeira tela, e sim assinatura,
pacote e denúncias de comportamento; o ciclo "cair → renomear → reaprovar" do Pop Play vem
de o app/certificado ser sinalizado, não do layout inicial. O Bix Player Pro já é neutro por
projeto (não embarca nem lista conteúdo; a lista vem do cadastro da revenda), é distribuído
fora da Play Store e assinado com chave própria. O F2-001 vale como decisão de produto (UX de
primeiro uso), não como proteção contra bloqueio.
