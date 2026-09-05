# ADR-004 — UI do app Android: Jetpack Compose + Compose for TV

**Status:** aceito · **Data:** 2026-09-05

## Contexto

O `CLAUDE.md` fixava **AndroidX Leanback** para a interface de TV, seguindo o app original
analisado em `docs/spec-app.md` (views clássicas + Leanback, §7). O plano do M3 muda essa
decisão para **Jetpack Compose** com **Compose for TV**.

Fatos que pesaram:

- O M4 pede a versão celular das mesmas telas (Anexo I §2.3). Com Leanback + views para TV e
  outra árvore de views para celular, cada tela seria escrita duas vezes.
- Leanback está em manutenção: o Google direciona apps novos de TV para Compose for TV
  (`androidx.tv:tv-material`), que já entrega os componentes de foco, listas imersivas e
  navegação por D-pad que antes vinham do Leanback.
- O restante da stack do app (Media3, Hilt, Room, Retrofit, Coil) é indiferente à camada de UI,
  então a troca não contamina as outras camadas.

## Decisão

- **UI 100% em Jetpack Compose.** Para TV, `androidx.tv:tv-material` e `androidx.tv:tv-foundation`;
  para celular (M4), Material 3 comum. Componentes de domínio (card de canal, grade de
  categorias, overlay do player) ficam em `ui/components` e são compartilhados; só o *chrome*
  (tamanhos, densidade, comportamento de foco) diverge entre TV e celular.
- **Leanback sai por completo.** Nenhuma dependência `androidx.leanback`. As duas activities
  (`TvActivity` com `LEANBACK_LAUNCHER` e `MobileActivity` com `LAUNCHER`) continuam existindo,
  mas ambas hospedam Compose.
- **Media3 (ExoPlayer) permanece** como player, com `PlayerView` embutido via `AndroidView`
  quando necessário. A abstração `PlayerEngine` isola o motor, mantendo o caminho para o
  fallback libVLC previsto no M4.
- **Navegação** com Navigation Compose, estado por tela em `ViewModel` + `StateFlow` (MVVM).
- **Foco** é cidadão de primeira classe: todo componente interativo declara `focusRequester`
  e ordem de foco explícita. A escala e o realce de foco vivem em `ui/theme`, não espalhados
  pelas telas.

## Consequências

- `docs/spec-app.md` §7 descreve a stack do app **original** (views + Leanback). Ele continua
  válido como engenharia reversa, mas **não** como especificação da nossa implementação. Este
  ADR prevalece.
- O `minSdk` permanece 23, mas Compose for TV exige APIs mais novas em alguns componentes;
  onde isso aparecer, o componente é degradado graciosamente em vez de subir o `minSdk`.
- Ganha-se reuso para o M4 e perde-se a documentação farta de exemplos de Leanback. Onde
  Compose for TV ainda não cobre um caso (por exemplo, comportamento fino de foco em listas
  muito longas), a saída é `AndroidView` pontual, nunca voltar para Leanback.
- Testes de UI, quando entrarem, usarão `createComposeRule` em vez de Espresso sobre views.

## Alternativas descartadas

| Alternativa | Motivo da recusa |
|---|---|
| Manter Leanback e escrever o celular em Compose | Duas implementações de cada tela, o dobro de manutenção a partir do M4 |
| Leanback para tudo, inclusive celular | Leanback é desenhado para 10 pés; a experiência em celular ficaria estranha |
| Compose puro (Material 3) também na TV | Sem `tv-material` seria preciso reimplementar foco, `ImmersiveList` e estilos de 10 pés à mão |
