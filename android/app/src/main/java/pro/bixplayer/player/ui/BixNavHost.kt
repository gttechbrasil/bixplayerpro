package pro.bixplayer.player.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import pro.bixplayer.player.ui.screens.splash.SplashScreen

/** Routes of the app. Kept as constants so the nav graph and the tests agree on the strings. */
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
    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) { SplashScreen() }
    }
}
