package pro.bixplayer.player.player

import androidx.media3.common.Player
import kotlinx.coroutines.flow.StateFlow
import pro.bixplayer.player.R
import pro.bixplayer.player.util.AppLocale

/** What the engine is doing right now, as far as the UI cares. */
sealed interface PlaybackState {
    data object Idle : PlaybackState

    data object Buffering : PlaybackState

    data object Playing : PlaybackState

    data object Paused : PlaybackState

    data object Ended : PlaybackState

    /** [message] is already translated; [code] is the engine's own code, for the log. */
    data class Error(val message: String, val code: Int = 0, val cause: Throwable? = null) : PlaybackState
}

enum class TrackType { AUDIO, SUBTITLE }

/** One selectable audio or subtitle track embedded in the stream. */
data class TrackOption(
    val id: String,
    val type: TrackType,
    val label: String,
    val selected: Boolean,
)

/** Messages the engines surface to the user, resolved in the language chosen in the settings. */
class PlayerMessages(private val locale: AppLocale) {
    val generic: String get() = locale.string(R.string.player_error)
    val network: String get() = locale.string(R.string.player_error_network)
    val unsupported: String get() = locale.string(R.string.player_error_unsupported)
    val vlcUnavailable: String get() = locale.string(R.string.player_error_vlc)

    fun httpStatus(status: Int): String = locale.string(R.string.player_error_http, status)
}

/**
 * The playback engine behind the player screen and the live preview.
 *
 * Media3 is the engine of the M3; libVLC arrives in the M4 for streams ExoPlayer refuses.
 * The interface is deliberately small: the screens talk to [PlayerSession], which owns the
 * retry policy and the single engine instance, and never to an engine directly.
 */
interface PlayerEngine {
    val state: StateFlow<PlaybackState>
    val tracks: StateFlow<List<TrackOption>>

    /** URL currently loaded, or null when idle/released. Never log it raw: it has credentials. */
    val currentUrl: String?

    /** Loads [url] and starts playing. Calling it again with the same URL is a no-op. */
    fun prepare(url: String)

    fun play()

    fun pause()

    /** Stops and unloads the media, keeping the engine ready for the next [prepare]. */
    fun stop()

    fun selectTrack(option: TrackOption)

    fun disableSubtitles()

    /** Frees the native resources. The engine can be used again after this: it re-creates them. */
    fun release()

    /** Media3 player to bind a surface to; null for engines that draw on their own surface. */
    val platformPlayer: Player?
}
