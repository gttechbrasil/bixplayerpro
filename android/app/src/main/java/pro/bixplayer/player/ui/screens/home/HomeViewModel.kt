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
import pro.bixplayer.player.data.db.MovieEntity
import pro.bixplayer.player.data.db.SeriesEntity
import pro.bixplayer.player.data.db.WatchProgressEntity
import pro.bixplayer.player.util.DeviceClass

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
    /** Recently added titles, for the artwork layouts (F2-002). Empty on the menu layouts. */
    val recentMovies: List<MovieEntity> = emptyList(),
    val recentSeries: List<SeriesEntity> = emptyList(),
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
        // One query each instead of one per layout: the rows and the covers share the result,
        // and a 1 GB box only ever holds `ROW_LIMIT` rows in memory (M5-018).
        val limit = if (DeviceClass.lowRam) ROW_LIMIT_LOW_RAM else ROW_LIMIT
        combine(
            syncDao.observe(id),
            movieDao.observeCount(id),
            seriesDao.observeCount(id),
            favoriteDao.observeCount(id),
            progressDao.observeContinueWatching(id, CONTINUE_LIMIT),
            store.layoutOverride,
        ) { values ->
            val recentMovies = movieDao.recent(id, limit)
            val recentSeries = seriesDao.recent(id, limit)
            @Suppress("UNCHECKED_CAST")
            HomeUiState(
                playlistId = id,
                channelCount = (values[0] as pro.bixplayer.player.data.db.PlaylistSyncEntity?)?.channelCount ?: 0,
                movieCount = values[1] as Int,
                seriesCount = values[2] as Int,
                favoriteCount = values[3] as Int,
                continueWatching = values[4] as List<WatchProgressEntity>,
                layoutOverride = values[5] as String?,
                movieCover = recentMovies.firstOrNull()?.posterUrl,
                seriesCover = recentSeries.firstOrNull()?.coverUrl,
                recentMovies = recentMovies,
                recentSeries = recentSeries,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    companion object {
        const val CONTINUE_LIMIT = 12
        const val ROW_LIMIT = 12
        const val ROW_LIMIT_LOW_RAM = 6
    }
}
