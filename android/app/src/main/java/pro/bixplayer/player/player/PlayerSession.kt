package pro.bixplayer.player.player

import android.content.Context
import androidx.media3.common.Player
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

/** What the screens render. Derived from the engine state plus the retry bookkeeping. */
sealed interface SessionState {
    data object Idle : SessionState

    data object Loading : SessionState

    data object Playing : SessionState

    data object Paused : SessionState

    /** Automatic retry in progress; the overlay shows "reconnecting (attempt/max)". */
    data class Retrying(val attempt: Int, val max: Int) : SessionState

    /** Retries exhausted; the screen offers a manual retry button. */
    data class Failed(val message: String) : SessionState

    /** A VOD item reached its end. */
    data object Ended : SessionState
}

/**
 * The single playback session of the app.
 *
 * There is exactly one engine instance alive: the live-TV preview and the full-screen player
 * both bind to it, so opening a channel from the preview never re-buffers, and there is never
 * a second decoder fighting for the hardware. It also owns the error policy: two automatic
 * retries with backoff, then a manual retry.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class PlayerSession @Inject constructor(
    @ApplicationContext context: Context,
    messages: PlayerMessages,
) {
    enum class EngineKind { MEDIA3, VLC }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val media3 = Media3Engine(context, messages)
    private val vlc = VlcEngine(context, messages)

    /** `null` = automatic (Media3 first, VLC when Media3 cannot decode); else forced. */
    @Volatile
    var preference: EngineKind? = null

    /** True while the current item plays on VLC because Media3 gave up ("Modo de compatibilidade"). */
    private val _compatibilityMode = MutableStateFlow(false)
    val compatibilityMode: StateFlow<Boolean> = _compatibilityMode.asStateFlow()

    /** libVLC player of the active engine, for the VLC surface; null while Media3 is active. */
    val vlcPlayer: org.videolan.libvlc.MediaPlayer?
        get() = (_engine.value as? VlcEngine)?.vlcPlayer

    private val _engine = MutableStateFlow<PlayerEngine>(media3)

    val engineKind: EngineKind
        get() = if (_engine.value === vlc) EngineKind.VLC else EngineKind.MEDIA3

    /** Player to bind a surface to. Changes when the engine changes, so read it in composition. */
    val player: Player?
        get() = _engine.value.platformPlayer

    val engineState: StateFlow<PlaybackState> = _engine
        .flatMapLatest { it.state }
        .stateIn(scope, SharingStarted.Eagerly, PlaybackState.Idle)

    val tracks: StateFlow<List<TrackOption>> = _engine
        .flatMapLatest { it.tracks }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _retrying = MutableStateFlow<SessionState.Retrying?>(null)

    val state: StateFlow<SessionState> = combine(engineState, _retrying) { engine, retrying ->
        when {
            retrying != null -> retrying
            engine is PlaybackState.Error -> SessionState.Failed(engine.message)
            engine is PlaybackState.Buffering -> SessionState.Loading
            engine is PlaybackState.Playing -> SessionState.Playing
            engine is PlaybackState.Paused -> SessionState.Paused
            engine is PlaybackState.Ended -> SessionState.Ended
            else -> SessionState.Idle
        }
    }.stateIn(scope, SharingStarted.Eagerly, SessionState.Idle)

    /** URL the user asked for; survives a background release so [onForeground] can resume. */
    private val _currentUrl = MutableStateFlow<String?>(null)
    val currentUrl: StateFlow<String?> = _currentUrl.asStateFlow()

    /** Room id of the live channel the player tuned last; the live list re-focuses it on return. */
    val currentChannelId = MutableStateFlow<Long?>(null)

    /** Position/duration of the current item, refreshed twice a second while something plays. */
    data class Progress(val positionMs: Long, val durationMs: Long, val seekable: Boolean) {
        val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    }

    private val _progress = MutableStateFlow(Progress(0L, 0L, false))
    val progress: StateFlow<Progress> = _progress.asStateFlow()

    private var attempt = 0
    private var retryJob: Job? = null

    init {
        scope.launch {
            engineState.collect { state ->
                if (state is PlaybackState.Error) onEngineError(state)
            }
        }
        scope.launch {
            while (true) {
                val engine = _engine.value
                val next = Progress(engine.positionMs, engine.durationMs, engine.isSeekable)
                if (next != _progress.value) _progress.value = next
                delay(PROGRESS_TICK_MS)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        _engine.value.seekTo(positionMs)
        _progress.value = Progress(positionMs, _engine.value.durationMs, _engine.value.isSeekable)
    }

    fun seekBy(deltaMs: Long) = seekTo(_engine.value.positionMs + deltaMs)

    val isPlaying: Boolean get() = engineState.value is PlaybackState.Playing

    fun play(url: String) {
        if (url == _currentUrl.value && state.value !is SessionState.Failed && _retrying.value == null) {
            _engine.value.play()
            return
        }
        cancelRetry()
        attempt = 0
        _currentUrl.value = url
        // A new item starts on the preferred engine; the fallback is decided per item.
        _compatibilityMode.value = false
        switchEngine(if (preference == EngineKind.VLC) vlc else media3)
        _engine.value.prepare(url)
    }

    private fun switchEngine(next: PlayerEngine) {
        if (next === _engine.value) return
        _engine.value.release()
        _engine.value = next
        Timber.i("player engine -> %s", if (next === vlc) EngineKind.VLC else EngineKind.MEDIA3)
    }

    /** "Tentar novamente": restarts the automatic cycle for the same URL. */
    fun retry() {
        val url = _currentUrl.value ?: return
        cancelRetry()
        attempt = 0
        _engine.value.prepare(url)
    }

    fun pause() = _engine.value.pause()

    fun resume() = _engine.value.play()

    fun togglePause() {
        if (engineState.value is PlaybackState.Playing) pause() else resume()
    }

    fun stop() {
        cancelRetry()
        _currentUrl.value = null
        _engine.value.stop()
    }

    fun selectTrack(option: TrackOption) = _engine.value.selectTrack(option)

    fun disableSubtitles() = _engine.value.disableSubtitles()

    /** App went to the background: free the decoder but remember what was on. */
    fun onBackground() {
        cancelRetry()
        _engine.value.release()
    }

    /** Back in the foreground: pick up where the user left. */
    fun onForeground() {
        val url = _currentUrl.value ?: return
        attempt = 0
        _engine.value.prepare(url)
    }

    /** Manual engine switch, keeping the current item. */
    fun useEngine(kind: EngineKind) {
        val next = if (kind == EngineKind.VLC) vlc else media3
        if (next === _engine.value) return
        val url = _currentUrl.value
        cancelRetry()
        attempt = 0
        switchEngine(next)
        if (url != null) next.prepare(url)
    }

    private fun onEngineError(error: PlaybackState.Error) {
        val url = _currentUrl.value ?: return
        if (attempt >= MAX_RETRIES) {
            _retrying.value = null
            if (_engine.value === media3 && preference != EngineKind.MEDIA3 && isDecodeOrSourceError(error.code)) {
                // Media3 cannot handle the stream itself (not the network): hand it to libVLC.
                Timber.i("media3 gave up with %d, falling back to vlc for %s", error.code, UrlRedactor.redact(url))
                _compatibilityMode.value = true
                switchEngine(vlc)
                vlc.prepare(url)
                return
            }
            Timber.w("playback failed after %d retries: %s", MAX_RETRIES, error.message)
            return
        }
        attempt++
        val backoff = RETRY_BASE_MS * attempt
        _retrying.value = SessionState.Retrying(attempt, MAX_RETRIES)
        Timber.i("retry %d/%d in %d ms for %s", attempt, MAX_RETRIES, backoff, UrlRedactor.redact(url))
        retryJob = scope.launch {
            delay(backoff)
            _retrying.value = null
            _engine.value.prepare(url)
        }
    }

    private fun cancelRetry() {
        retryJob?.cancel()
        retryJob = null
        _retrying.value = null
    }

    companion object {
        /**
         * Media3 error codes: 2xxx are I/O (network, HTTP status), 3xxx parsing, 4xxx decoding,
         * 5xxx audio track, 6xxx DRM. Only what the engine itself rejects justifies a fallback.
         */
        fun isDecodeOrSourceError(code: Int): Boolean = code in 3000..5999

        const val MAX_RETRIES = 2
        const val RETRY_BASE_MS = 1_500L
        const val PROGRESS_TICK_MS = 500L
    }
}
