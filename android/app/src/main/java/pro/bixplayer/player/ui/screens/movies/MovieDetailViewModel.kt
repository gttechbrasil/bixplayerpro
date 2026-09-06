package pro.bixplayer.player.ui.screens.movies

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pro.bixplayer.player.data.db.ContentKind
import pro.bixplayer.player.data.db.FavoriteDao
import pro.bixplayer.player.data.db.FavoriteEntity
import pro.bixplayer.player.data.db.MovieDao
import pro.bixplayer.player.data.db.MovieEntity
import pro.bixplayer.player.data.db.WatchProgressDao
import pro.bixplayer.player.data.db.WatchProgressEntity
import pro.bixplayer.player.data.playlist.XtreamClient
import pro.bixplayer.player.data.playlist.XtreamCredentials
import pro.bixplayer.player.data.repository.ConfigRepository
import pro.bixplayer.player.domain.model.ConfigState
import pro.bixplayer.player.domain.model.PlaylistType
import timber.log.Timber

data class MovieDetailUiState(
    val movie: MovieEntity? = null,
    val progress: WatchProgressEntity? = null,
    val favorite: Boolean = false,
    val loadingDetails: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MovieDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val movieDao: MovieDao,
    private val favoriteDao: FavoriteDao,
    private val progressDao: WatchProgressDao,
    private val repository: ConfigRepository,
    private val xtream: XtreamClient,
    private val io: CoroutineDispatcher,
) : ViewModel() {

    private val movieId: Long = checkNotNull(savedState.get<Long>(ARG_MOVIE_ID))

    private val movie = movieDao.observeById(movieId)
    private val loading = kotlinx.coroutines.flow.MutableStateFlow(false)

    val uiState: StateFlow<MovieDetailUiState> = movie.filterNotNull().flatMapLatest { m ->
        combine(
            favoriteDao.observeIsFavorite(m.playlistId, ContentKind.MOVIE, m.remoteId),
            progressDao.observe(m.playlistId, ContentKind.MOVIE, m.remoteId),
            loading,
        ) { fav, progress, busy -> MovieDetailUiState(m, progress, fav, busy) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MovieDetailUiState())

    init {
        viewModelScope.launch { fetchDetailsIfNeeded() }
    }

    /** Xtream carries plot/cast/duration in `get_vod_info`; fetched once per movie and cached. */
    private suspend fun fetchDetailsIfNeeded() {
        val m = movieDao.byId(movieId) ?: return
        if (m.detailsFetchedAt != null) return
        val config = (repository.state.value as? ConfigState.Ready)?.config ?: repository.cached() ?: return
        val playlist = config.playlists.firstOrNull { it.id == m.playlistId } ?: return
        if (playlist.type != PlaylistType.XTREAM) return
        val credentials = XtreamCredentials.from(playlist.url) ?: return
        val streamId = m.remoteId.toLongOrNull() ?: return

        loading.value = true
        try {
            val info = withContext(io) { xtream.vodInfo(credentials, streamId) }
            val backdrop = (info?.backdropPath as? List<*>)?.firstOrNull()?.toString()
                ?: (info?.backdropPath as? String)
            movieDao.updateDetails(
                id = movieId,
                plot = info?.plot?.takeIf { it.isNotBlank() },
                actors = info?.cast?.takeIf { it.isNotBlank() },
                director = info?.director?.takeIf { it.isNotBlank() },
                genre = info?.genre?.takeIf { it.isNotBlank() },
                durationSecs = XtreamClient.int(info?.durationSecs) ?: parseDuration(info?.duration),
                backdropUrl = backdrop?.takeIf { it.isNotBlank() },
                year = (info?.releaseDate ?: info?.releaseDateAlt)?.take(4)?.takeIf { it.length == 4 },
                posterUrl = info?.movieImage?.takeIf { it.isNotBlank() },
                fetchedAt = System.currentTimeMillis(),
            )
        } catch (error: Exception) {
            Timber.w(error, "vod info failed for movie %d", movieId)
        } finally {
            loading.value = false
        }
    }

    fun toggleFavorite() {
        val m = uiState.value.movie ?: return
        viewModelScope.launch {
            if (uiState.value.favorite) favoriteDao.remove(m.playlistId, ContentKind.MOVIE, m.remoteId)
            else favoriteDao.add(FavoriteEntity(m.playlistId, ContentKind.MOVIE, m.remoteId))
        }
    }

    /** "Assistir do início" also forgets the saved position. */
    fun restart() {
        val m = uiState.value.movie ?: return
        viewModelScope.launch { progressDao.delete(m.playlistId, ContentKind.MOVIE, m.remoteId) }
    }

    companion object {
        const val ARG_MOVIE_ID = "movieId"

        /** `"01:42:10"` → seconds; providers that lack `duration_secs` still send this. */
        fun parseDuration(text: String?): Int? {
            val parts = text?.trim()?.split(':')?.mapNotNull { it.toIntOrNull() } ?: return null
            return when (parts.size) {
                3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
                2 -> parts[0] * 60 + parts[1]
                else -> null
            }?.takeIf { it > 0 }
        }
    }
}
