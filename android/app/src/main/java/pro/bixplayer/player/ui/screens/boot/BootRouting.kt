package pro.bixplayer.player.ui.screens.boot

import pro.bixplayer.player.domain.model.AppConfig
import pro.bixplayer.player.domain.model.DeviceStatus
import pro.bixplayer.player.ui.Routes
import pro.bixplayer.player.util.AppVersion

/**
 * Where the app goes after reading the configuration.
 *
 * A pure function on purpose: this is the rule that decides whether a customer sees their
 * channels or a wall, so it is worth testing without a ViewModel or Android around it.
 */
object BootRouting {

    fun routeFor(config: AppConfig, currentVersion: String): String {
        // A forced update outranks everything: an old build may not understand newer payloads.
        if (AppVersion.isOutdated(currentVersion, config.minAppVersion)) return Routes.UPDATE

        return when {
            config.status == DeviceStatus.EXPIRED -> Routes.EXPIRED
            !config.registered -> Routes.ACTIVATION
            // Registered but with nothing to play: the reseller still has to add a playlist,
            // or the user can add one from the activation screen.
            !config.hasPlaylists -> Routes.ACTIVATION
            else -> Routes.HOME
        }
    }
}
