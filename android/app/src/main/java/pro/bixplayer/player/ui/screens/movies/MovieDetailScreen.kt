package pro.bixplayer.player.ui.screens.movies

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import pro.bixplayer.player.R
import pro.bixplayer.player.data.db.MovieEntity
import pro.bixplayer.player.ui.components.BixButton
import pro.bixplayer.player.util.TimeFormat

/**
 * Movie detail: cover, metadata and synopsis when the provider has them, plus the actions.
 * "Continuar de HH:MM" appears when there is a resumable position; "Assistir do início" then
 * sits next to it.
 */
@Composable
fun MovieDetailScreen(
    onPlay: (movie: MovieEntity, resume: Boolean) -> Unit,
    viewModel: MovieDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val movie = state.movie ?: return
    val primaryRequester = remember { FocusRequester() }
    LaunchedEffect(movie.id) {
        delay(80)
        runCatching { primaryRequester.requestFocus() }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val backdrop = movie.backdropUrl ?: movie.posterUrl
        if (!backdrop.isNullOrBlank()) {
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.35f,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.background, Color.Transparent))),
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 56.dp, vertical = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(40.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(260.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                if (!movie.posterUrl.isNullOrBlank()) {
                    AsyncImage(model = movie.posterUrl, contentDescription = movie.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Text(text = movie.name.take(1).uppercase(), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text(
                    text = movie.name,
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta = listOfNotNull(
                    movie.year,
                    movie.genre,
                    TimeFormat.duration(movie.durationSecs),
                    movie.rating?.let { "★ $it" },
                ).joinToString("  ·  ")
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }

                Spacer(Modifier.height(24.dp))

                val progress = state.progress
                val resumable = progress?.resumable == true
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    BixButton(
                        text = if (resumable) {
                            stringResource(R.string.movie_continue, TimeFormat.clock(progress!!.positionMs))
                        } else {
                            stringResource(R.string.movie_watch)
                        },
                        onClick = { onPlay(movie, resumable) },
                        focusRequester = primaryRequester,
                    )
                    if (resumable) {
                        BixButton(
                            text = stringResource(R.string.movie_restart),
                            primary = false,
                            onClick = {
                                viewModel.restart()
                                onPlay(movie, false)
                            },
                        )
                    }
                    BixButton(
                        text = stringResource(if (state.favorite) R.string.detail_favorite_remove else R.string.detail_favorite_add),
                        primary = false,
                        onClick = viewModel::toggleFavorite,
                    )
                }

                Spacer(Modifier.height(28.dp))

                when {
                    !movie.plot.isNullOrBlank() -> Text(
                        text = movie.plot,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 8,
                        overflow = TextOverflow.Ellipsis,
                    )
                    state.loadingDetails -> Text(
                        text = stringResource(R.string.loading),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> Text(
                        text = stringResource(R.string.movie_no_details),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                movie.director?.let {
                    Spacer(Modifier.height(16.dp))
                    MetaLine(label = stringResource(R.string.movie_director), value = it)
                }
                movie.actors?.let {
                    Spacer(Modifier.height(8.dp))
                    MetaLine(label = stringResource(R.string.movie_cast), value = it)
                }
            }
        }
    }
}

@Composable
private fun MetaLine(label: String, value: String) {
    Row {
        Text(text = "$label: ", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
