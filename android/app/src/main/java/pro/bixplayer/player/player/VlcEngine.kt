package pro.bixplayer.player.player

import androidx.media3.common.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * libVLC fallback. **Stub in the M3**: the structure exists so the session can switch engines,
 * but it reports every stream as unplayable. The real implementation (libvlc-all, its own
 * surface) lands in the M4, where streams Media3 rejects are routed here.
 */
class VlcEngine(private val messages: PlayerMessages) : PlayerEngine {

    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _tracks = MutableStateFlow<List<TrackOption>>(emptyList())
    override val tracks: StateFlow<List<TrackOption>> = _tracks.asStateFlow()

    override var currentUrl: String? = null
        private set

    override val platformPlayer: Player? = null

    override fun prepare(url: String) {
        currentUrl = url
        Timber.w("vlc engine requested for %s but it is not bundled yet", UrlRedactor.redact(url))
        _state.value = PlaybackState.Error(messages.vlcUnavailable)
    }

    override fun play() = Unit

    override fun pause() = Unit

    override fun stop() {
        currentUrl = null
        _state.value = PlaybackState.Idle
    }

    override fun selectTrack(option: TrackOption) = Unit

    override fun disableSubtitles() = Unit

    override fun release() = stop()
}
