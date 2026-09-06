package pro.bixplayer.player.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pro.bixplayer.player.data.datastore.DeviceStore
import pro.bixplayer.player.data.db.ContentKind
import pro.bixplayer.player.data.db.EpisodeDao
import pro.bixplayer.player.data.db.FavoriteDao
import pro.bixplayer.player.data.db.MovieDao
import pro.bixplayer.player.data.db.PlaylistSyncDao
import pro.bixplayer.player.data.db.SeriesDao
import pro.bixplayer.player.data.db.WatchProgressDao
import pro.bixplayer.player.data.db.WatchProgressEntity

data class HomeUiState(
    val playlistId: Long? = null,
    val channelCount: Int = 0,
    val movieCount: Int = 0,
    val seriesCount: Int = 0,
    val favoriteCount: Int = 0,
    val continueWatching: List<WatchProgressEntity> = emptyList(),
    /** `default` | `grid` chosen on this device, or null to follow the panel. */
    val layoutOverride: String? = null,
    val movieCover: String? = null,
    val seriesCover: String? = null,
)

/** Counts and "continue watching" for both home layouts. Sync itself stays in PlaylistViewModel. */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    store: DeviceStore,
    syncDao: PlaylistSyncDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val episodeDao: EpisodeDao,
    favoriteDao: FavoriteDao,
    progressDao: WatchProgressDao,
) : ViewModel() {

    /** A progress row only knows the provider id; the player route needs the Room id. */
    fun resolve(progress: WatchProgressEntity, onResolved: (kind: String, id: Long) -> Unit) {
        viewModelScope.launch {
            val id = when (progress.kind) {
                ContentKind.MOVIE -> movieDao.byRemoteId(progress.playlistId, progress.itemRemoteId)?.id
                ContentKind.EPISODE -> episodeDao.byRemoteId(progress.playlistId, progress.itemRemoteId)?.id
                else -> null
            } ?: return@launch
            onResolved(progress.kind, id)
        }
    }

    val uiState: StateFlow<HomeUiState> = store.activePlaylistId.flatMapLatest { id ->
        if (id == null) return@flatMapLatest flowOf(HomeUiState())
        combine(
            syncDao.observe(id),
            movieDao.observeCount(id),
            seriesDao.observeCount(id),
            favoriteDao.observeCount(id),
            progressDao.observeContinueWatching(id, CONTINUE_LIMIT),
            store.layoutOverride,
        ) { values ->
            @Suppress("UNCHECKED_CAST")
            HomeUiState(
                playlistId = id,
                channelCount = (values[0] as pro.bixplayer.player.data.db.PlaylistSyncEntity?)?.channelCount ?: 0,
                movieCount = values[1] as Int,
                seriesCount = values[2] as Int,
                favoriteCount = values[3] as Int,
                continueWatching = values[4] as List<WatchProgressEntity>,
                layoutOverride = values[5] as String?,
                movieCover = movieDao.recent(id, 1).firstOrNull()?.posterUrl,
                seriesCover = seriesDao.recent(id, 1).firstOrNull()?.coverUrl,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    companion object {
        const val CONTINUE_LIMIT = 12
    }
}
