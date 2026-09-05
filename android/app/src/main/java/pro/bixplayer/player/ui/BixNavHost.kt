package pro.bixplayer.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import pro.bixplayer.player.BuildConfig
import pro.bixplayer.player.domain.model.ConfigState
import pro.bixplayer.player.ui.locale.BixLocale
import pro.bixplayer.player.ui.screens.activation.ActivationScreen
import pro.bixplayer.player.ui.screens.boot.BootDestination
import pro.bixplayer.player.ui.screens.boot.BootViewModel
import pro.bixplayer.player.ui.screens.expired.ExpiredScreen
import pro.bixplayer.player.ui.screens.home.HomeScreen
import pro.bixplayer.player.ui.screens.live.LiveScreen
import pro.bixplayer.player.ui.screens.player.PlayerScreen
import pro.bixplayer.player.ui.screens.player.PlayerViewModel
import pro.bixplayer.player.ui.screens.player.ZapScope
import pro.bixplayer.player.ui.screens.playlists.ChangePlaylistScreen
import pro.bixplayer.player.ui.screens.settings.SettingsScreen
import pro.bixplayer.player.ui.screens.splash.SplashScreen
import pro.bixplayer.player.ui.screens.update.UpdateScreen

/** Routes of the app. Constants so the graph and the tests agree on the strings. */
object Routes {
    const val SPLASH = "splash"
    const val ACTIVATION = "activation"
    const val EXPIRED = "expired"
    const val UPDATE = "update"
    const val HOME = "home"
    const val LIVE = "live"
    const val PLAYER = "player"
    const val SETTINGS = "settings"
    const val CHANGE_PLAYLIST = "change_playlist"

    const val PLAYER_PATTERN = "$PLAYER/{${PlayerViewModel.ARG_CHANNEL_ID}}?${PlayerViewModel.ARG_SCOPE}={${PlayerViewModel.ARG_SCOPE}}"

    fun player(channelId: Long, scopeKey: String): String =
        "$PLAYER/$channelId?${PlayerViewModel.ARG_SCOPE}=${ZapScope.encode(scopeKey)}"
}

@Composable
fun BixNavHost(navController: NavHostController = rememberNavController()) {
    // One BootViewModel for the whole graph: the config is a single source of truth and the
    // activation, expired and update screens all need to re-check it.
    val bootViewModel: BootViewModel = hiltViewModel()
    val destination by bootViewModel.destination.collectAsStateWithLifecycle()
    val configState by bootViewModel.configState.collectAsStateWithLifecycle()
    val language by bootViewModel.language.collectAsStateWithLifecycle()
    val config = (configState as? ConfigState.Ready)?.config

    LaunchedEffect(destination) {
        val target = (destination as? BootDestination.Go)?.route ?: return@LaunchedEffect
        if (navController.currentDestination?.route != target) {
            navController.navigate(target) {
                popUpTo(Routes.SPLASH) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    BixLocale(languageTag = language) {
        NavHost(navController = navController, startDestination = Routes.SPLASH) {
            composable(Routes.SPLASH) {
                SplashScreen(
                    logoUrl = config?.logoUrl,
                    platformName = config?.platformName,
                    error = (destination as? BootDestination.Error)?.message,
                    onRetry = bootViewModel::boot,
                )
            }

            composable(Routes.ACTIVATION) {
                ActivationScreen(
                    onActivated = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ACTIVATION) { inclusive = true }
                            launchSingleTop = true
                        }
                    },
                )
            }

            composable(Routes.EXPIRED) {
                ExpiredScreen(
                    macAddress = config?.macAddress.orEmpty(),
                    expiresAt = config?.licenseExpiresAt,
                    checking = destination is BootDestination.Pending,
                    onCheck = bootViewModel::boot,
                )
            }

            composable(Routes.UPDATE) {
                UpdateScreen(
                    currentVersion = BuildConfig.VERSION_NAME,
                    minimumVersion = config?.minAppVersion.orEmpty(),
                    apkUrl = config?.apkUrl.orEmpty(),
                    checking = destination is BootDestination.Pending,
                    onCheck = bootViewModel::boot,
                )
            }

            composable(Routes.HOME) {
                HomeScreen(
                    config = config,
                    onLive = { navController.navigate(Routes.LIVE) { launchSingleTop = true } },
                    onSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                )
            }

            composable(Routes.LIVE) {
                LiveScreen(
                    onOpenChannel = { channel, scopeKey ->
                        navController.navigate(Routes.player(channel.id, scopeKey)) { launchSingleTop = true }
                    },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.PLAYER_PATTERN,
                arguments = listOf(
                    navArgument(PlayerViewModel.ARG_CHANNEL_ID) { type = NavType.LongType },
                    navArgument(PlayerViewModel.ARG_SCOPE) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = ZapScope.ALL
                    },
                ),
            ) {
                PlayerScreen(onExit = { navController.popBackStack() })
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onChangePlaylist = { navController.navigate(Routes.CHANGE_PLAYLIST) },
                    onLoggedOut = {
                        // Everything local is gone: boot again, which re-registers the device and
                        // lands on the activation screen (or home, if the platform still knows it).
                        navController.navigate(Routes.SPLASH) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                        }
                        bootViewModel.boot()
                    },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.CHANGE_PLAYLIST) {
                ChangePlaylistScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}
