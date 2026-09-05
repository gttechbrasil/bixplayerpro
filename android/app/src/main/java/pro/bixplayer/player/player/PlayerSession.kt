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
    private val vlc = VlcEngine(messages)

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
            else -> SessionState.Idle
        }
    }.stateIn(scope, SharingStarted.Eagerly, SessionState.Idle)

    /** URL the user asked for; survives a background release so [onForeground] can resume. */
    private val _currentUrl = MutableStateFlow<String?>(null)
    val currentUrl: StateFlow<String?> = _currentUrl.asStateFlow()

    private var attempt = 0
    private var retryJob: Job? = null

    init {
        scope.launch {
            engineState.collect { state ->
                if (state is PlaybackState.Error) onEngineError(state)
            }
        }
    }

    fun play(url: String) {
        if (url == _currentUrl.value && state.value !is SessionState.Failed && _retrying.value == null) {
            _engine.value.play()
            return
        }
        cancelRetry()
        attempt = 0
        _currentUrl.value = url
        _engine.value.prepare(url)
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

    /** Engine switch for the M4 (libVLC fallback). Kept here so the screens never care. */
    fun useEngine(kind: EngineKind) {
        val next = if (kind == EngineKind.VLC) vlc else media3
        if (next === _engine.value) return
        val url = _currentUrl.value
        _engine.value.release()
        _engine.value = next
        Timber.i("player engine -> %s", kind)
        if (url != null) next.prepare(url)
    }

    private fun onEngineError(error: PlaybackState.Error) {
        val url = _currentUrl.value ?: return
        if (attempt >= MAX_RETRIES) {
            _retrying.value = null
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
        const val MAX_RETRIES = 2
        const val RETRY_BASE_MS = 1_500L
    }
}
