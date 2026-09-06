package pro.bixplayer.player.ui.screens.player

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pro.bixplayer.player.data.db.CategoryDao
import pro.bixplayer.player.data.datastore.DeviceStore
import pro.bixplayer.player.data.db.ChannelDao
import pro.bixplayer.player.data.db.ChannelEntity
import pro.bixplayer.player.data.db.ContentKind
import pro.bixplayer.player.data.db.EpisodeDao
import pro.bixplayer.player.data.db.EpisodeEntity
import pro.bixplayer.player.data.db.MovieDao
import pro.bixplayer.player.data.db.MovieEntity
import pro.bixplayer.player.data.db.SeriesDao
import pro.bixplayer.player.data.db.SeriesEntity
import pro.bixplayer.player.data.db.WatchProgressDao
import pro.bixplayer.player.data.db.WatchProgressEntity
import pro.bixplayer.player.player.PlayerSession
import pro.bixplayer.player.player.SessionState
import pro.bixplayer.player.player.TrackOption
import pro.bixplayer.player.player.TrackType
import timber.log.Timber

/** The list the user was browsing when they pressed OK; zapping stays inside it. */
data class ZapScope(val categoryRemoteId: String?, val favoritesOnly: Boolean) {
    companion object {
        const val ALL = "all"
        const val FAVORITES = "fav"

        fun encode(scopeKey: String): String = Uri.encode(scopeKey)

        fun parse(arg: String?): ZapScope = when {
            arg.isNullOrBlank() || arg == ALL -> ZapScope(null, false)
            arg == FAVORITES -> ZapScope(null, true)
            arg.startsWith("cat:") -> ZapScope(arg.removePrefix("cat:"), false)
            else -> ZapScope(null, false)
        }
    }
}

/** What the player is showing: a live channel, a movie or an episode. */
sealed interface PlaybackItem {
    val title: String
    val subtitle: String?
    val streamUrl: String
    val isLive: Boolean get() = this is Live

    data class Live(val channel: ChannelEntity, val categoryName: String?) : PlaybackItem {
        override val title get() = channel.name
        override val subtitle get() = categoryName
        override val streamUrl get() = channel.streamUrl
    }

    data class Movie(val movie: MovieEntity) : PlaybackItem {
        override val title get() = movie.name
        override val subtitle get() = listOfNotNull(movie.year, movie.genre).joinToString("  ·  ").ifBlank { null }
        override val streamUrl get() = movie.streamUrl
    }

    data class Episode(val episode: EpisodeEntity, val series: SeriesEntity?) : PlaybackItem {
        override val title get() = series?.name ?: episode.title
        override val subtitle get() = "T${episode.season} E${episode.episode}  ·  ${episode.title}"
        override val streamUrl get() = episode.streamUrl
    }
}

data class PlayerUiState(
    val item: PlaybackItem? = null,
    val playback: SessionState = SessionState.Idle,
    val progress: PlayerSession.Progress = PlayerSession.Progress(0L, 0L, false),
    val audioTracks: List<TrackOption> = emptyList(),
    val subtitleTracks: List<TrackOption> = emptyList(),
    val overlayVisible: Boolean = true,
    val quickListVisible: Boolean = false,
    val quickList: List<ChannelEntity> = emptyList(),
    val tracksVisible: Boolean = false,
    /** Digits typed on the remote, shown while the user is still typing. */
    val typedNumber: String = "",
    /** Transient message (channel not found); already translated by the screen. */
    val notice: String? = null,
    /** Seek target while ←/→ is being held, before it is applied. */
    val seekTargetMs: Long? = null,
    /** Next episode offered when the current one ends, with the seconds left on the countdown. */
    val nextEpisode: EpisodeEntity? = null,
    val nextCountdown: Int? = null,
    /** The movie ended: the screen leaves on its own. */
    val finished: Boolean = false,
    /** Playing on libVLC after Media3 gave up on the stream. */
    val compatibilityMode: Boolean = false,
) {
    val isLive: Boolean get() = item?.isLive != false
    val channel: ChannelEntity? get() = (item as? PlaybackItem.Live)?.channel
}

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val channelDao: ChannelDao,
    private val categoryDao: CategoryDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val episodeDao: EpisodeDao,
    private val progressDao: WatchProgressDao,
    private val store: DeviceStore,
    val session: PlayerSession,
) : ViewModel() {

    private val kind: String = savedState.get<String>(ARG_KIND) ?: ContentKind.LIVE
    private val itemId: Long = checkNotNull(savedState.get<Long>(ARG_ID))
    private val scope: ZapScope = ZapScope.parse(savedState.get<String>(ARG_SCOPE))
    private val resume: Boolean = savedState.get<String>(ARG_RESUME) == "1"

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var categoryNames: Map<String, String> = emptyMap()
    private var overlayJob: Job? = null
    private var numberJob: Job? = null
    private var noticeJob: Job? = null
    private var seekJob: Job? = null
    private var countdownJob: Job? = null
    private var pendingSeekMs: Long? = null
    private var lastSavedPositionMs = -1L
    private var endedHandled = false

    init {
        combine(session.state, session.tracks, session.progress, session.compatibilityMode) { playback, tracks, progress, compat ->
            Quad(playback, tracks, progress, compat)
        }.onEach { (playback, tracks, progress, compat) ->
            _uiState.value = _uiState.value.copy(
                playback = playback,
                progress = progress,
                compatibilityMode = compat,
                audioTracks = tracks.filter { it.type == TrackType.AUDIO },
                subtitleTracks = tracks.filter { it.type == TrackType.SUBTITLE },
            )
            if (playback is SessionState.Playing) applyPendingSeek()
            if (playback is SessionState.Ended) onEnded()
        }.launchIn(viewModelScope)

        viewModelScope.launch {
            applyEnginePreference()
            when (kind) {
                ContentKind.MOVIE -> movieDao.byId(itemId)?.let { start(PlaybackItem.Movie(it)) }
                ContentKind.EPISODE -> episodeDao.byId(itemId)?.let { ep ->
                    start(PlaybackItem.Episode(ep, seriesDao.byRemoteId(ep.playlistId, ep.seriesRemoteId)))
                }
                else -> channelDao.byId(itemId)?.let { channel ->
                    categoryNames = categoryDao.byPlaylist(channel.playlistId).associate { it.remoteId to it.name }
                    tune(channel)
                }
            }
        }

        // VOD position is saved every 10 s while playing, and once more when the screen goes.
        viewModelScope.launch {
            while (true) {
                delay(PROGRESS_SAVE_MS)
                if (!_uiState.value.isLive && session.isPlaying) saveProgress()
            }
        }
    }

    /** Settings → Player: automático / Media3 / VLC, per playlist. */
    private suspend fun applyEnginePreference() {
        val playlistId = store.currentActivePlaylistId() ?: return
        session.preference = when (store.currentPlayerEngine(playlistId)) {
            "vlc" -> PlayerSession.EngineKind.VLC
            "media3" -> PlayerSession.EngineKind.MEDIA3
            else -> null
        }
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    // ---- live ------------------------------------------------------------------------------

    private fun tune(channel: ChannelEntity) {
        Timber.d("tune %d %s", channel.number, channel.name)
        _uiState.value = _uiState.value.copy(
            item = PlaybackItem.Live(channel, channel.categoryRemoteId?.let { categoryNames[it] }),
            typedNumber = "",
        )
        session.currentChannelId.value = channel.id
        session.play(channel.streamUrl)
        showOverlay()
    }

    fun next() = zap(forward = true)

    fun previous() = zap(forward = false)

    private fun zap(forward: Boolean) {
        val current = _uiState.value.channel ?: return
        viewModelScope.launch {
            val playlistId = current.playlistId
            val cat = scope.categoryRemoteId
            val fav = if (scope.favoritesOnly) 1 else 0
            val target = if (forward) {
                channelDao.nextInScope(playlistId, cat, fav, current.position)
                    ?: channelDao.firstInScope(playlistId, cat, fav)
            } else {
                channelDao.previousInScope(playlistId, cat, fav, current.position)
                    ?: channelDao.lastInScope(playlistId, cat, fav)
            }
            if (target != null && target.id != current.id) tune(target)
        }
    }

    fun openQuickList() {
        val current = _uiState.value.channel ?: return
        viewModelScope.launch {
            val list = channelDao.listInScope(
                current.playlistId,
                scope.categoryRemoteId,
                if (scope.favoritesOnly) 1 else 0,
                QUICK_LIST_LIMIT,
            )
            _uiState.value = _uiState.value.copy(quickList = list, quickListVisible = true, tracksVisible = false)
        }
    }

    fun closeQuickList() {
        _uiState.value = _uiState.value.copy(quickListVisible = false)
    }

    fun select(channel: ChannelEntity) {
        closeQuickList()
        if (channel.id != _uiState.value.channel?.id) tune(channel)
    }

    /** Digits typed on the remote; after a short pause the number is looked up. */
    fun typeDigit(digit: Char, notFound: (Int) -> String) {
        if (!_uiState.value.isLive) return
        val typed = (_uiState.value.typedNumber + digit).takeLast(MAX_NUMBER_DIGITS)
        _uiState.value = _uiState.value.copy(typedNumber = typed, notice = null)
        numberJob?.cancel()
        numberJob = viewModelScope.launch {
            delay(NUMBER_TIMEOUT_MS)
            val number = typed.toIntOrNull() ?: return@launch
            val playlistId = _uiState.value.channel?.playlistId ?: return@launch
            val target = channelDao.byNumber(playlistId, number)
            if (target == null) {
                _uiState.value = _uiState.value.copy(typedNumber = "")
                notice(notFound(number))
            } else {
                tune(target)
            }
        }
    }

    // ---- VOD -------------------------------------------------------------------------------

    private suspend fun start(item: PlaybackItem) {
        endedHandled = false
        _uiState.value = _uiState.value.copy(item = item, nextEpisode = null, nextCountdown = null, finished = false)
        if (resume || item is PlaybackItem.Episode) {
            val saved = progressFor(item)
            if (saved?.resumable == true && (resume || item is PlaybackItem.Episode)) pendingSeekMs = saved.positionMs
        }
        session.currentChannelId.value = null
        session.play(item.streamUrl)
        showOverlay()
    }

    private fun applyPendingSeek() {
        val target = pendingSeekMs ?: return
        pendingSeekMs = null
        session.seekTo(target)
    }

    private var seekStreak = 0
    private var lastSeekAt = 0L

    /**
     * ←/→: 10 s per press, growing while the key is held. Key repeats arrive every ~50 ms, so
     * presses closer than [SEEK_STREAK_GAP_MS] count as one streak and the step grows with it.
     */
    fun seek(forward: Boolean) {
        if (_uiState.value.isLive || !_uiState.value.progress.seekable) return
        val now = System.currentTimeMillis()
        seekStreak = if (now - lastSeekAt < SEEK_STREAK_GAP_MS) seekStreak + 1 else 0
        lastSeekAt = now
        val step = SEEK_STEP_MS * (1 + seekStreak / 4).coerceAtMost(8)
        val base = _uiState.value.seekTargetMs ?: session.progress.value.positionMs
        val duration = _uiState.value.progress.durationMs
        val target = (base + if (forward) step else -step).coerceIn(0L, duration)
        _uiState.value = _uiState.value.copy(seekTargetMs = target)
        showOverlay()
        // Apply once the key settles so a held key does not issue a seek per repeat event.
        seekJob?.cancel()
        seekJob = viewModelScope.launch {
            delay(SEEK_APPLY_DELAY_MS)
            session.seekTo(target)
            _uiState.value = _uiState.value.copy(seekTargetMs = null)
        }
    }

    fun togglePause() {
        if (_uiState.value.isLive) return
        session.togglePause()
        showOverlay()
    }

    /** Touch scrubbing: accumulate while the finger moves, seek once when it lifts. */
    fun dragSeekBy(deltaMs: Long) {
        if (_uiState.value.isLive || !_uiState.value.progress.seekable) return
        val base = _uiState.value.seekTargetMs ?: session.progress.value.positionMs
        val target = (base + deltaMs).coerceIn(0L, _uiState.value.progress.durationMs)
        seekJob?.cancel()
        _uiState.value = _uiState.value.copy(seekTargetMs = target)
        showOverlay()
    }

    fun commitDragSeek() {
        val target = _uiState.value.seekTargetMs ?: return
        session.seekTo(target)
        _uiState.value = _uiState.value.copy(seekTargetMs = null)
    }

    private fun onEnded() {
        if (endedHandled) return
        endedHandled = true
        val item = _uiState.value.item ?: return
        viewModelScope.launch {
            markFinished(item)
            when (item) {
                is PlaybackItem.Episode -> {
                    val nextEp = episodeDao.next(item.episode.playlistId, item.episode.seriesRemoteId, item.episode.season, item.episode.episode)
                    if (nextEp == null) {
                        _uiState.value = _uiState.value.copy(finished = true)
                    } else {
                        startCountdown(nextEp)
                    }
                }
                is PlaybackItem.Movie -> _uiState.value = _uiState.value.copy(finished = true)
                is PlaybackItem.Live -> Unit
            }
        }
    }

    private fun startCountdown(nextEp: EpisodeEntity) {
        countdownJob?.cancel()
        _uiState.value = _uiState.value.copy(nextEpisode = nextEp, nextCountdown = NEXT_COUNTDOWN_S, overlayVisible = false)
        countdownJob = viewModelScope.launch {
            for (left in NEXT_COUNTDOWN_S - 1 downTo 0) {
                delay(1_000)
                _uiState.value = _uiState.value.copy(nextCountdown = left)
            }
            playNextEpisode()
        }
    }

    fun playNextEpisode() {
        countdownJob?.cancel()
        val nextEp = _uiState.value.nextEpisode ?: return
        viewModelScope.launch {
            val series = (_uiState.value.item as? PlaybackItem.Episode)?.series
                ?: seriesDao.byRemoteId(nextEp.playlistId, nextEp.seriesRemoteId)
            start(PlaybackItem.Episode(nextEp, series))
        }
    }

    fun cancelNextEpisode() {
        countdownJob?.cancel()
        _uiState.value = _uiState.value.copy(nextEpisode = null, nextCountdown = null, finished = true)
    }

    private suspend fun progressFor(item: PlaybackItem): WatchProgressEntity? = when (item) {
        is PlaybackItem.Movie -> progressDao.get(item.movie.playlistId, ContentKind.MOVIE, item.movie.remoteId)
        is PlaybackItem.Episode -> progressDao.get(item.episode.playlistId, ContentKind.EPISODE, item.episode.remoteId)
        is PlaybackItem.Live -> null
    }

    private fun progressRow(item: PlaybackItem, positionMs: Long, durationMs: Long): WatchProgressEntity? = when (item) {
        is PlaybackItem.Movie -> WatchProgressEntity(
            playlistId = item.movie.playlistId,
            kind = ContentKind.MOVIE,
            itemRemoteId = item.movie.remoteId,
            positionMs = positionMs,
            durationMs = durationMs,
            updatedAt = System.currentTimeMillis(),
            title = item.movie.name,
            subtitle = item.subtitle,
            posterUrl = item.movie.posterUrl,
        )
        is PlaybackItem.Episode -> WatchProgressEntity(
            playlistId = item.episode.playlistId,
            kind = ContentKind.EPISODE,
            itemRemoteId = item.episode.remoteId,
            positionMs = positionMs,
            durationMs = durationMs,
            updatedAt = System.currentTimeMillis(),
            title = item.title,
            subtitle = item.subtitle,
            posterUrl = item.series?.coverUrl ?: item.episode.thumbUrl,
            seriesRemoteId = item.episode.seriesRemoteId,
        )
        is PlaybackItem.Live -> null
    }

    private suspend fun saveProgress() {
        val item = _uiState.value.item ?: return
        val progress = session.progress.value
        if (progress.durationMs <= 0 || progress.positionMs == lastSavedPositionMs) return
        lastSavedPositionMs = progress.positionMs
        progressRow(item, progress.positionMs, progress.durationMs)?.let { progressDao.upsert(it) }
    }

    private suspend fun markFinished(item: PlaybackItem) {
        val duration = session.progress.value.durationMs.takeIf { it > 0 } ?: return
        progressRow(item, duration, duration)?.let { progressDao.upsert(it) }
    }

    // ---- shared ----------------------------------------------------------------------------

    fun toggleOverlay() {
        if (_uiState.value.overlayVisible) hideOverlay() else showOverlay()
    }

    fun showOverlay() {
        overlayJob?.cancel()
        _uiState.value = _uiState.value.copy(overlayVisible = true)
        overlayJob = viewModelScope.launch {
            delay(OVERLAY_TIMEOUT_MS)
            // Keep the overlay while something needs the user's attention.
            if (_uiState.value.playback is SessionState.Playing) hideOverlay()
        }
    }

    fun hideOverlay() {
        overlayJob?.cancel()
        _uiState.value = _uiState.value.copy(overlayVisible = false)
    }

    fun openTracks() {
        _uiState.value = _uiState.value.copy(tracksVisible = true, quickListVisible = false)
    }

    fun closeTracks() {
        _uiState.value = _uiState.value.copy(tracksVisible = false)
    }

    fun selectTrack(option: TrackOption) {
        session.selectTrack(option)
        closeTracks()
    }

    fun disableSubtitles() {
        session.disableSubtitles()
        closeTracks()
    }

    fun retry() {
        session.retry()
        showOverlay()
    }

    fun onBackground() {
        viewModelScope.launch { if (!_uiState.value.isLive) saveProgress() }
        session.onBackground()
    }

    fun onForeground() = session.onForeground()

    /** Leaving the screen: VOD saves its position and stops; live hands the session to the preview. */
    fun onExit() {
        countdownJob?.cancel()
        if (_uiState.value.isLive) return
        val item = _uiState.value.item
        val progress = session.progress.value
        session.stop()
        if (item == null || progress.durationMs <= 0) return
        viewModelScope.launch {
            withContext(NonCancellable) {
                progressRow(item, progress.positionMs, progress.durationMs)?.let { progressDao.upsert(it) }
            }
        }
    }

    private fun notice(message: String) {
        noticeJob?.cancel()
        _uiState.value = _uiState.value.copy(notice = message)
        noticeJob = viewModelScope.launch {
            delay(NOTICE_TIMEOUT_MS)
            _uiState.value = _uiState.value.copy(notice = null)
        }
    }

    companion object {
        const val ARG_KIND = "kind"
        const val ARG_ID = "id"
        const val ARG_SCOPE = "scope"
        const val ARG_RESUME = "resume"
        const val OVERLAY_TIMEOUT_MS = 4_000L
        const val NUMBER_TIMEOUT_MS = 1_500L
        const val NOTICE_TIMEOUT_MS = 2_500L
        const val MAX_NUMBER_DIGITS = 5
        const val QUICK_LIST_LIMIT = 500
        const val SEEK_STEP_MS = 10_000L
        const val SEEK_APPLY_DELAY_MS = 220L
        const val SEEK_STREAK_GAP_MS = 400L
        const val PROGRESS_SAVE_MS = 10_000L
        const val NEXT_COUNTDOWN_S = 10
    }
}
