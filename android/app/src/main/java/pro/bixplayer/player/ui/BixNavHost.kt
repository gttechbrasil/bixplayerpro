package pro.bixplayer.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import pro.bixplayer.player.BuildConfig
import pro.bixplayer.player.domain.model.ConfigState
import pro.bixplayer.player.ui.screens.activation.ActivationScreen
import pro.bixplayer.player.ui.screens.boot.BootDestination
import pro.bixplayer.player.ui.screens.boot.BootViewModel
import pro.bixplayer.player.ui.screens.expired.ExpiredScreen
import pro.bixplayer.player.ui.screens.home.HomeScreen
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
}

@Composable
fun BixNavHost(navController: NavHostController = rememberNavController()) {
    // One BootViewModel for the whole graph: the config is a single source of truth and the
    // activation, expired and update screens all need to re-check it.
    val bootViewModel: BootViewModel = hiltViewModel()
    val destination by bootViewModel.destination.collectAsStateWithLifecycle()
    val configState by bootViewModel.configState.collectAsStateWithLifecycle()
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
            // The real home lands in block 5; this keeps the boot flow verifiable end to end.
            HomeScreen(config = config)
        }
    }
}
