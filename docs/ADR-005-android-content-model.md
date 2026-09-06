# ADR-005 — Modelo de conteúdo local do app (M4)

**Status:** aceito — 2026-09-06

## Contexto

O M4 acrescenta filmes, séries, EPG, controle parental e "continuar assistindo" ao app. O
`PLANO-M4.md` lista as tabelas por bloco (`movies`, `movie_categories`, `watch_progress`,
`series`, `series_categories`, `episodes`, `epg_programs`). Implementar o schema bloco a bloco
significaria três bumps de versão do Room com migração destrutiva, e três formas ligeiramente
diferentes de guardar "categoria", "favorito" e "regra de categoria".

## Decisão

1. **Schema único do M4 na versão 2 do Room**, criado no bloco 1 e consumido pelos blocos
   seguintes. Migração destrutiva continua (`fallbackToDestructiveMigration`): o banco é um
   cache das listas do provedor; só favoritos, progresso e regras são estado do usuário e, nesta
   fase (sem app publicado para clientes), aceitamos perdê-los em upgrade. Antes do M5 entra
   uma migração real.
2. **Uma tabela `categories` com coluna `kind`** (`live` | `movie` | `series`) no lugar de
   `movie_categories`/`series_categories`. Mesmo formato, um DAO, índice único
   `(playlistId, kind, remoteId)`.
3. **`favorites` e `category_rules` também são por `kind`**; `category_rules` guarda tanto
   *oculta* quanto *bloqueada por PIN* por categoria, por playlist.
4. **`watch_progress` é desnormalizada** (título, subtítulo, capa, série de origem) para a home
   renderizar "continuar assistindo" sem *joins*; a chave é `(playlistId, kind, itemRemoteId)`.
5. **Episódios**: no Xtream são carregados sob demanda (`get_series_info`) ao abrir a série; no
   M3U chegam com a lista (agrupados por `Nome SxxExx`) e são gravados no sync.
6. **Identidade M3U**: filmes e episódios usam `M3uRemoteIds` (hash de nome+URL com sufixo de
   ocorrência); séries usam o hash do nome normalizado, que é o que agrupa os episódios.
7. **EPG** vive em `epg_programs` numa janela deslizante (−6 h a +48 h); `playlist_sync` guarda a
   URL XMLTV (`xmltv.php` no Xtream, `url-tvg` no M3U) e quando foi sincronizada.
8. **Classificação M3U** (`M3uClassifier`): extensão de vídeo (`mp4/mkv/avi/…`) → filme;
   `SxxExx`/`1x02`/`T01E02` no nome com URL de vídeo ou grupo de séries → episódio; grupo com
   "filmes/movies/vod" sem URL de live → filme; o resto é canal. `.ts`/`.m3u8`/`/live/` nunca
   viram VOD.

## Consequências

- Todas as consultas de listas filtram `category_rules.hidden` no SQL, então uma categoria
  oculta some de listas, busca, zapping e guia sem código extra nas telas.
- Contagens por categoria vêm do sync (`channelCount`), não de `COUNT(*)` em tempo real.
- Um provedor Xtream sem VOD/séries não quebra o sync de TV ao vivo: essas chamadas são
  opcionais e falham em silêncio (log), gravando zero itens.
- O `PlaylistSyncUseCase` cresceu para cobrir os quatro tipos numa única transação
  (`SyncDao.replacePlaylist`), o que mantém a garantia de nunca expor uma lista pela metade.
