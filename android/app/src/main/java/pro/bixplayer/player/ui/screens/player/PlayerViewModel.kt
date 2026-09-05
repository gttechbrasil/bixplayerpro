package pro.bixplayer.player.ui.screens.player

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import pro.bixplayer.player.data.db.CategoryDao
import pro.bixplayer.player.data.db.ChannelDao
import pro.bixplayer.player.data.db.ChannelEntity
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

data class PlayerUiState(
    val channel: ChannelEntity? = null,
    val categoryName: String? = null,
    val playback: SessionState = SessionState.Idle,
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
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val channelDao: ChannelDao,
    private val categoryDao: CategoryDao,
    val session: PlayerSession,
) : ViewModel() {

    private val channelId: Long = checkNotNull(savedState.get<Long>(ARG_CHANNEL_ID))
    private val scope: ZapScope = ZapScope.parse(savedState.get<String>(ARG_SCOPE))

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var categoryNames: Map<String, String> = emptyMap()
    private var overlayJob: Job? = null
    private var numberJob: Job? = null
    private var noticeJob: Job? = null

    init {
        combine(session.state, session.tracks) { playback, tracks -> playback to tracks }
            .onEach { (playback, tracks) ->
                _uiState.value = _uiState.value.copy(
                    playback = playback,
                    audioTracks = tracks.filter { it.type == TrackType.AUDIO },
                    subtitleTracks = tracks.filter { it.type == TrackType.SUBTITLE },
                )
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            val channel = channelDao.byId(channelId) ?: return@launch
            categoryNames = categoryDao.byPlaylist(channel.playlistId).associate { it.remoteId to it.name }
            tune(channel)
        }
    }

    private fun tune(channel: ChannelEntity) {
        Timber.d("tune %d %s", channel.number, channel.name)
        _uiState.value = _uiState.value.copy(
            channel = channel,
            categoryName = channel.categoryRemoteId?.let { categoryNames[it] },
            typedNumber = "",
        )
        session.play(channel.streamUrl)
        showOverlay()
    }

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

    /** Digits typed on the remote; after a short pause the number is looked up. */
    fun typeDigit(digit: Char, notFound: (Int) -> String) {
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

    fun retry() {
        session.retry()
        showOverlay()
    }

    fun onBackground() = session.onBackground()

    fun onForeground() = session.onForeground()

    /** Leaving the screen for the live list: the preview takes over the same session. */
    fun onExit() = Unit

    private fun notice(message: String) {
        noticeJob?.cancel()
        _uiState.value = _uiState.value.copy(notice = message)
        noticeJob = viewModelScope.launch {
            delay(NOTICE_TIMEOUT_MS)
            _uiState.value = _uiState.value.copy(notice = null)
        }
    }

    companion object {
        const val ARG_CHANNEL_ID = "channelId"
        const val ARG_SCOPE = "scope"
        const val OVERLAY_TIMEOUT_MS = 4_000L
        const val NUMBER_TIMEOUT_MS = 1_500L
        const val NOTICE_TIMEOUT_MS = 2_500L
        const val MAX_NUMBER_DIGITS = 5
        const val QUICK_LIST_LIMIT = 500
    }
}
