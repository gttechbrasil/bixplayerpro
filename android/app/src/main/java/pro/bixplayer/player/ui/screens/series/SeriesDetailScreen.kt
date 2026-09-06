package pro.bixplayer.player.ui.screens.series

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import pro.bixplayer.player.R
import pro.bixplayer.player.data.db.EpisodeEntity
import pro.bixplayer.player.data.db.WatchProgressEntity
import pro.bixplayer.player.ui.components.BixButton
import pro.bixplayer.player.ui.theme.BixFocus
import pro.bixplayer.player.ui.theme.bixFocusable
import pro.bixplayer.player.util.TimeFormat
import pro.bixplayer.player.ui.components.onSelect

/** Series detail: cover and synopsis on the left, season chips and the episode list on the right. */
@Composable
fun SeriesDetailScreen(
    onPlayEpisode: (EpisodeEntity) -> Unit,
    viewModel: SeriesDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val show = state.series ?: return
    val continueRequester = remember { FocusRequester() }
    LaunchedEffect(show.id, state.continueEpisode?.id) {
        delay(80)
        runCatching { continueRequester.requestFocus() }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 48.dp, vertical = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(32.dp),
    ) {
        Column(modifier = Modifier.width(360.dp).fillMaxHeight()) {
            Box(
                modifier = Modifier
                    .width(200.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                if (!show.coverUrl.isNullOrBlank()) {
                    AsyncImage(model = show.coverUrl, contentDescription = show.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Text(text = show.name.take(1).uppercase(), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = show.name,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = listOfNotNull(show.year, show.genre, show.rating?.let { "★ $it" }).joinToString("  ·  ")
            if (meta.isNotBlank()) {
                Text(text = meta, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
            }
            Spacer(Modifier.height(16.dp))
            val continueEp = state.continueEpisode
            if (continueEp != null) {
                val saved = state.progress[continueEp.remoteId]
                BixButton(
                    text = if (saved?.resumable == true) {
                        stringResource(R.string.series_continue_at, continueEp.season, continueEp.episode, TimeFormat.clock(saved.positionMs))
                    } else {
                        stringResource(R.string.series_continue, continueEp.season, continueEp.episode)
                    },
                    onClick = { onPlayEpisode(continueEp) },
                    focusRequester = continueRequester,
                )
                Spacer(Modifier.height(10.dp))
            }
            BixButton(
                text = stringResource(if (state.favorite) R.string.detail_favorite_remove else R.string.detail_favorite_add),
                primary = false,
                onClick = viewModel::toggleFavorite,
            )
            Spacer(Modifier.height(16.dp))
            if (!show.plot.isNullOrBlank()) {
                Text(
                    text = show.plot,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            if (state.seasons.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.seasons, key = { it }) { season ->
                        SeasonChip(
                            label = stringResource(R.string.series_season, season),
                            selected = season == state.selectedSeason,
                            onSelect = { viewModel.selectSeason(season) },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
            when {
                state.loading && state.episodes.isEmpty() -> Text(
                    text = stringResource(R.string.loading),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                state.episodes.isEmpty() -> Text(
                    text = state.error ?: stringResource(R.string.series_no_episodes),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxSize()) {
                    items(state.episodes, key = { it.id }) { episode ->
                        EpisodeRow(
                            episode = episode,
                            progress = state.progress[episode.remoteId],
                            onPlay = { onPlayEpisode(episode) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonChip(label: String, selected: Boolean, onSelect: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(20.dp)
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .bixFocusable(focused, scale = 1f, shape = shape)
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, shape)
            .focusable(interactionSource = interaction)
            .onSelect { onSelect() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

@Composable
private fun EpisodeRow(episode: EpisodeEntity, progress: WatchProgressEntity?, onPlay: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    val fraction = progress?.let { if (it.durationMs > 0) (it.positionMs.toFloat() / it.durationMs).coerceIn(0f, 1f) else 0f } ?: 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bixFocusable(focused, scale = BixFocus.SCALE_SMALL, shape = shape)
            .clip(shape)
            .background(if (focused) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
            .focusable(interactionSource = interaction)
            .onSelect { onPlay() },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "E${episode.episode}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(56.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = episode.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val info = listOfNotNull(
                    TimeFormat.duration(episode.durationSecs),
                    progress?.takeIf { it.finished }?.let { stringResource(R.string.series_watched) },
                    progress?.takeIf { it.resumable }?.let { TimeFormat.clock(it.positionMs) },
                ).joinToString("  ·  ")
                if (info.isNotBlank()) {
                    Text(text = info, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (fraction > 0f) {
            Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.15f))) {
                Box(modifier = Modifier.fillMaxWidth(fraction).height(3.dp).background(MaterialTheme.colorScheme.primary))
            }
        }
    }
}
