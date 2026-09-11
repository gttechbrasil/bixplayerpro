package pro.bixplayer.player.ui.screens.playlists

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import pro.bixplayer.player.domain.model.AppConfig
import pro.bixplayer.player.ui.screens.activation.ActivationScreen

/**
 * The *Playlist* destination (F2-001). Reachable from the home, the settings and every demo
 * screen. Before activation it is the old activation screen (MAC, QR, "já cadastrei",
 * manual playlist); once the device can watch it is the playlist manager.
 *
 * The choice is made once per visit: when the reseller registers the MAC while this screen
 * is open, the activation screen itself reports it and [onActivated] takes the user home,
 * instead of the content silently swapping under the focus.
 */
@Composable
fun PlaylistScreen(
    config: AppConfig?,
    onActivated: () -> Unit,
    onBack: () -> Unit,
) {
    val canWatch = config?.canWatch == true
    val manage = remember { canWatch }
    if (manage) {
        ChangePlaylistScreen(macAddress = config?.macAddress.orEmpty(), onBack = onBack)
    } else {
        ActivationScreen(onActivated = onActivated, onBack = onBack)
    }
}
