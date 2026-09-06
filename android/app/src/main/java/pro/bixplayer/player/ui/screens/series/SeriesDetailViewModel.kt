package pro.bixplayer.player.ui.screens.series

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pro.bixplayer.player.data.db.ContentKind
import pro.bixplayer.player.data.db.EpisodeDao
import pro.bixplayer.player.data.db.EpisodeEntity
import pro.bixplayer.player.data.db.FavoriteDao
import pro.bixplayer.player.data.db.FavoriteEntity
import pro.bixplayer.player.data.db.SeriesDao
import pro.bixplayer.player.data.db.SeriesEntity
import pro.bixplayer.player.data.db.WatchProgressDao
import pro.bixplayer.player.data.db.WatchProgressEntity
import pro.bixplayer.player.data.playlist.XtreamClient
import pro.bixplayer.player.data.playlist.XtreamCredentials
import pro.bixplayer.player.data.repository.ConfigRepository
import pro.bixplayer.player.domain.model.ConfigState
import pro.bixplayer.player.domain.model.PlaylistType
import timber.log.Timber

data class SeriesDetailUiState(
    val series: SeriesEntity? = null,
    val seasons: List<Int> = emptyList(),
    val selectedSeason: Int? = null,
    val episodes: List<EpisodeEntity> = emptyList(),
    /** Progress per episode remote id. */
    val progress: Map<String, WatchProgressEntity> = emptyMap(),
    /** Episode "Continuar" should open: the last one touched, or the first if none. */
    val continueEpisode: EpisodeEntity? = null,
    val favorite: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
)

/**
 * Series detail: seasons, episodes with progress and the "continue" shortcut. Xtream episodes
 * are fetched the first time the series is opened (and cached in Room); M3U ones arrived with
 * the playlist.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SeriesDetailViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val seriesDao: SeriesDao,
    private val episodeDao: EpisodeDao,
    private val favoriteDao: FavoriteDao,
    private val progressDao: WatchProgressDao,
    private val repository: ConfigRepository,
    private val xtream: XtreamClient,
    private val io: CoroutineDispatcher,
) : ViewModel() {

    private val seriesId: Long = checkNotNull(savedState.get<Long>(ARG_SERIES_ID))
    private val selectedSeason = MutableStateFlow<Int?>(savedState.get<Int>(KEY_SEASON))
    private val loading = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SeriesDetailUiState> = seriesDao.observeById(seriesId).filterNotNull().flatMapLatest { show ->
        combine(
            episodeDao.observeBySeries(show.playlistId, show.remoteId),
            progressDao.observeBySeries(show.playlistId, show.remoteId),
            favoriteDao.observeIsFavorite(show.playlistId, ContentKind.SERIES, show.remoteId),
            selectedSeason,
            combine(loading, error) { l, e -> l to e },
        ) { episodes, progress, fav, season, (busy, err) ->
            val seasons = episodes.map { it.season }.distinct().sorted()
            val current = season?.takeIf { it in seasons } ?: seasons.firstOrNull()
            val byId = progress.associateBy { it.itemRemoteId }
            val latest = progress.maxByOrNull { it.updatedAt }
            val continueEpisode = latest?.let { p ->
                val ep = episodes.firstOrNull { it.remoteId == p.itemRemoteId }
                // Finished the last one watched: continue means the next episode.
                if (ep != null && p.finished) episodes.nextAfter(ep) ?: ep else ep
            } ?: episodes.firstOrNull()
            SeriesDetailUiState(
                series = show,
                seasons = seasons,
                selectedSeason = current,
                episodes = episodes.filter { it.season == current },
                progress = byId,
                continueEpisode = continueEpisode,
                favorite = fav,
                loading = busy,
                error = err,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SeriesDetailUiState())

    init {
        viewModelScope.launch { fetchEpisodesIfNeeded() }
    }

    private fun List<EpisodeEntity>.nextAfter(ep: EpisodeEntity): EpisodeEntity? =
        firstOrNull { it.season > ep.season || (it.season == ep.season && it.episode > ep.episode) }

    private suspend fun fetchEpisodesIfNeeded(force: Boolean = false) {
        val show = seriesDao.byId(seriesId) ?: return
        if (show.episodesFetchedAt != null && !force) return
        val config = (repository.state.value as? ConfigState.Ready)?.config ?: repository.cached() ?: return
        val playlist = config.playlists.firstOrNull { it.id == show.playlistId } ?: return
        if (playlist.type != PlaylistType.XTREAM) return
        val credentials = XtreamCredentials.from(playlist.url) ?: return
        val remoteId = show.remoteId.toLongOrNull() ?: return

        loading.value = true
        error.value = null
        try {
            val info = withContext(io) { xtream.seriesInfo(credentials, remoteId) }
            val episodes = info?.episodes.orEmpty().flatMap { (seasonKey, list) ->
                val seasonNumber = seasonKey.toIntOrNull() ?: 0
                list.mapNotNull { ep ->
                    val id = XtreamClient.text(ep.id) ?: return@mapNotNull null
                    EpisodeEntity(
                        playlistId = show.playlistId,
                        seriesRemoteId = show.remoteId,
                        remoteId = id,
                        season = XtreamClient.int(ep.season) ?: seasonNumber,
                        episode = XtreamClient.int(ep.episodeNum) ?: 0,
                        title = ep.title?.takeIf { it.isNotBlank() } ?: "Episódio ${XtreamClient.int(ep.episodeNum) ?: 0}",
                        streamUrl = xtream.episodeStreamUrl(credentials, id, ep.containerExtension),
                        plot = ep.info?.plot?.takeIf { it.isNotBlank() },
                        thumbUrl = ep.info?.movieImage?.takeIf { it.isNotBlank() },
                        durationSecs = XtreamClient.int(ep.info?.durationSecs),
                    )
                }
            }
            episodeDao.deleteBySeries(show.playlistId, show.remoteId)
            episodeDao.insertAll(episodes)
            seriesDao.updateDetails(
                id = seriesId,
                plot = info?.info?.plot?.takeIf { it.isNotBlank() },
                actors = info?.info?.cast?.takeIf { it.isNotBlank() },
                genre = info?.info?.genre?.takeIf { it.isNotBlank() },
                year = (info?.info?.releaseDate ?: info?.info?.releaseDateAlt)?.take(4)?.takeIf { it.length == 4 },
                coverUrl = info?.info?.cover?.takeIf { it.isNotBlank() },
                fetchedAt = System.currentTimeMillis(),
            )
        } catch (e: Exception) {
            Timber.w(e, "series info failed for %d", seriesId)
            error.value = e.message
        } finally {
            loading.value = false
        }
    }

    fun selectSeason(season: Int) {
        selectedSeason.value = season
    }

    fun toggleFavorite() {
        val show = uiState.value.series ?: return
        viewModelScope.launch {
            if (uiState.value.favorite) favoriteDao.remove(show.playlistId, ContentKind.SERIES, show.remoteId)
            else favoriteDao.add(FavoriteEntity(show.playlistId, ContentKind.SERIES, show.remoteId))
        }
    }

    fun refreshEpisodes() {
        viewModelScope.launch { fetchEpisodesIfNeeded(force = true) }
    }

    companion object {
        const val ARG_SERIES_ID = "seriesId"
        private const val KEY_SEASON = "season"
    }
}
