package pro.bixplayer.player.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import pro.bixplayer.player.BuildConfig
import pro.bixplayer.player.data.db.ContentKind
import pro.bixplayer.player.domain.model.ConfigState
import pro.bixplayer.player.ui.locale.BixLocale
import pro.bixplayer.player.ui.screens.activation.ActivationScreen
import pro.bixplayer.player.ui.screens.boot.BootDestination
import pro.bixplayer.player.ui.screens.boot.BootViewModel
import pro.bixplayer.player.ui.screens.catalog.CatalogScreen
import pro.bixplayer.player.ui.screens.catalog.CatalogViewModel
import pro.bixplayer.player.ui.screens.expired.ExpiredScreen
import pro.bixplayer.player.ui.screens.home.HomeScreen
import pro.bixplayer.player.ui.screens.live.LiveScreen
import pro.bixplayer.player.ui.screens.movies.MovieDetailScreen
import pro.bixplayer.player.ui.screens.movies.MovieDetailViewModel
import pro.bixplayer.player.ui.screens.player.PlayerScreen
import pro.bixplayer.player.ui.screens.player.PlayerViewModel
import pro.bixplayer.player.ui.screens.player.ZapScope
import pro.bixplayer.player.ui.screens.playlists.ChangePlaylistScreen
import pro.bixplayer.player.ui.screens.series.SeriesDetailScreen
import pro.bixplayer.player.ui.screens.series.SeriesDetailViewModel
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
    const val CATALOG = "catalog"
    const val MOVIE = "movie"
    const val SERIES = "series"

    const val PLAYER_PATTERN =
        "$PLAYER/{${PlayerViewModel.ARG_KIND}}/{${PlayerViewModel.ARG_ID}}" +
            "?${PlayerViewModel.ARG_SCOPE}={${PlayerViewModel.ARG_SCOPE}}&${PlayerViewModel.ARG_RESUME}={${PlayerViewModel.ARG_RESUME}}"
    const val CATALOG_PATTERN = "$CATALOG/{${CatalogViewModel.ARG_KIND}}"
    const val MOVIE_PATTERN = "$MOVIE/{${MovieDetailViewModel.ARG_MOVIE_ID}}"
    const val SERIES_PATTERN = "$SERIES/{${SeriesDetailViewModel.ARG_SERIES_ID}}"

    fun player(channelId: Long, scopeKey: String): String =
        "$PLAYER/${ContentKind.LIVE}/$channelId?${PlayerViewModel.ARG_SCOPE}=${ZapScope.encode(scopeKey)}"

    fun playerVod(kind: String, id: Long, resume: Boolean): String =
        "$PLAYER/$kind/$id?${PlayerViewModel.ARG_RESUME}=${if (resume) "1" else "0"}"

    fun catalog(kind: String): String = "$CATALOG/$kind"

    fun movie(id: Long): String = "$MOVIE/$id"

    fun series(id: Long): String = "$SERIES/$id"
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
                    onMovies = { navController.navigate(Routes.catalog(ContentKind.MOVIE)) { launchSingleTop = true } },
                    onSeries = { navController.navigate(Routes.catalog(ContentKind.SERIES)) { launchSingleTop = true } },
                    onSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                    onResume = { kind, id -> navController.navigate(Routes.playerVod(kind, id, resume = true)) },
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
                route = Routes.CATALOG_PATTERN,
                arguments = listOf(navArgument(CatalogViewModel.ARG_KIND) { type = NavType.StringType }),
            ) { entry ->
                val kind = entry.arguments?.getString(CatalogViewModel.ARG_KIND) ?: ContentKind.MOVIE
                CatalogScreen(
                    onOpen = { item ->
                        navController.navigate(if (kind == ContentKind.SERIES) Routes.series(item.id) else Routes.movie(item.id))
                    },
                )
            }

            composable(
                route = Routes.MOVIE_PATTERN,
                arguments = listOf(navArgument(MovieDetailViewModel.ARG_MOVIE_ID) { type = NavType.LongType }),
            ) {
                MovieDetailScreen(
                    onPlay = { movie, resume ->
                        navController.navigate(Routes.playerVod(ContentKind.MOVIE, movie.id, resume))
                    },
                )
            }

            composable(
                route = Routes.SERIES_PATTERN,
                arguments = listOf(navArgument(SeriesDetailViewModel.ARG_SERIES_ID) { type = NavType.LongType }),
            ) {
                SeriesDetailScreen(
                    onPlayEpisode = { episode ->
                        navController.navigate(Routes.playerVod(ContentKind.EPISODE, episode.id, resume = true))
                    },
                )
            }

            composable(
                route = Routes.PLAYER_PATTERN,
                arguments = listOf(
                    navArgument(PlayerViewModel.ARG_KIND) { type = NavType.StringType },
                    navArgument(PlayerViewModel.ARG_ID) { type = NavType.LongType },
                    navArgument(PlayerViewModel.ARG_SCOPE) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = ZapScope.ALL
                    },
                    navArgument(PlayerViewModel.ARG_RESUME) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = "0"
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
