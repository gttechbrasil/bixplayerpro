# Fase 2 — pedidos fora do Anexo I

Lista viva dos itens que ficaram fora da v1 (Seção 3 do Anexo I) e dos pedidos surgidos na
homologação. Cada item traz origem, o que muda em relação ao contratado e uma estimativa
grosseira para a próxima proposta. Nada daqui entra no M5.

| Id | Item | Origem | O que muda | Estimativa |
|---|---|---|---|---|
| F2-001 ✅ **entregue na 1.3.0 (11/09/2026)** | **Home antes da ativação.** Ao abrir sem playlist, o app entra direto na tela inicial (TV ao vivo, Filmes, Séries) com conteúdo de demonstração (cartazes genéricos), em vez da tela do MAC. O MAC e o QR passam para um item de menu "Playlist" (TV e celular). A ativação continua automática: assim que a revenda cadastra o MAC, a home carrega a lista de verdade. | Cliente, 11/09/2026 (referência: comportamento do "Pop Play") | Contraria o Anexo I §Ativação ("ao abrir, o app exibe o MAC"). Toca `LaunchActivity`/`BootScreen`, `HomeScreen` das duas UIs, novo destino "Playlist" com o conteúdo da `ActivationScreen`, estado "sem lista" nos catálogos, polling de ativação em segundo plano e textos. Não muda backend nem painel. Implementação: `ui/demo/DemoMode`, `DemoShowcase`, `PlaylistScreen`, polling de 20 s no `BootViewModel`; detalhes em `ANDROID.md` §11. | Feito em 1 dia; reteste na MXQ pendente (cliente) |

| F2-002 ✅ **entregue na 1.4.0 (15/09/2026)** | **Mais layouts de tela inicial.** O Anexo I previa 2; o cliente pediu algo mais próximo do "Pop Play", que oferece 5. | Cliente, 15/09/2026 (via WhatsApp, antes de distribuir para os primeiros testadores) | Três layouts novos (Cinema, Menu lateral, Mosaico) que usam as capas da lista do cliente, seleção no painel e override por aparelho. Backend: só a lista de valores aceitos em `theme`. | Feito em 1 dia |

| F2-003 ✅ **entregue na 1.5.0 (16/09/2026)** | **Ícones de filmes e séries** (e de todo o menu) no lugar dos emoji. | Cliente, 16/09/2026 ("modelo Pop Player Pro") | 12 ícones vetoriais em `res/drawable/ic_tile_*.xml`; `GridTile.icon` passou de emoji para `@DrawableRes`. Emoji renderizavam diferente em cada box e não davam para colorir. | Feito |
| F2-004 ✅ **entregue na 1.5.0** | **Botão Atualizar na tela inicial** no lugar da entrada Playlist, em todos os layouts. | Cliente, 16/09/2026 | Com o aparelho ativo o quarto cartão vira *Atualizar* e dispara o sync da lista, mostrando "N canais sincronizados". Em modo demonstração ele continua sendo *Playlist*, que é onde está o MAC (F2-001). O gerenciador de listas segue em Configurações → Playlist. | Feito |
| F2-005 ✅ **entregue na 1.5.0** | **Ajuste de tela no player**: tela cheia ou tamanho original. | Cliente, 16/09/2026 | Botão no canto inferior direito do overlay (toque) e as mesmas opções no painel do **MENU**, porque no controle remoto as setas são usadas para trocar de canal e nunca chegariam ao canto. A escolha fica salva no aparelho. | Feito |
| F2-006 ✅ **entregue na 1.5.0** | **Menu lateral como padrão** assim que o app é baixado. | Cliente, 16/09/2026 | Novas revendas nascem com `theme=rail` (migração `a4e71c92b8d5` muda só o padrão da coluna), o `GET /device/config` de aparelho sem revenda responde `rail`, e o app cai em `RAIL` quando ainda não tem configuração. Revendas existentes mantêm o que escolheram. | Feito |
| F2-007 ✅ **entregue na 1.5.0** | **Prévia real dos layouts no painel** no lugar dos desenhos esquemáticos. | Cliente, 16/09/2026 | As capturas saem do próprio app (`docs/screens/android/layouts/`), entram no painel como JPEG de 960 px e abrem ampliadas em um modal. | Feito |

## Observação sobre a motivação do F2-001

O cliente entende que a home de demonstração faz o app parecer "100 % legal" para o Google.
Isso não procede tecnicamente: o Play Protect não avalia a primeira tela, e sim assinatura,
pacote e denúncias de comportamento; o ciclo "cair → renomear → reaprovar" do Pop Play vem
de o app/certificado ser sinalizado, não do layout inicial. O Bix Player Pro já é neutro por
projeto (não embarca nem lista conteúdo; a lista vem do cadastro da revenda), é distribuído
fora da Play Store e assinado com chave própria. O F2-001 vale como decisão de produto (UX de
primeiro uso), não como proteção contra bloqueio.
