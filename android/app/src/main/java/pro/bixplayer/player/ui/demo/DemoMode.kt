package pro.bixplayer.player.ui.demo

import androidx.compose.runtime.staticCompositionLocalOf
import pro.bixplayer.player.domain.model.AppConfig

/**
 * "Demo mode" (F2-001): the app no longer opens on the activation wall. Without a usable
 * playlist it shows the normal home and catalogues filled with placeholder artwork, and the
 * MAC lives in the *Playlist* destination. This object is the single rule that says when.
 */
object DemoMode {

    /** True when there is nothing real to show: no config yet, not linked, or no playlist. */
    fun isDemo(config: AppConfig?): Boolean = config == null || !config.canWatch
}

/** What the catalogue screens need to render the demo showcase instead of real content. */
data class DemoState(
    val active: Boolean,
    val macAddress: String,
    /** Opens the Playlist screen (MAC, QR, manual playlist). */
    val openPlaylist: () -> Unit,
)

val LocalDemoState = staticCompositionLocalOf { DemoState(active = false, macAddress = "", openPlaylist = {}) }
