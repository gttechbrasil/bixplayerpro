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
import pro.bixplayer.player.ui.screens.epg.EpgGridScreen
import pro.bixplayer.player.ui.screens.epg.EpgGridViewModel
import pro.bixplayer.player.ui.screens.expired.ExpiredScreen
import pro.bixplayer.player.ui.screens.home.HomeScreen
import pro.bixplayer.player.ui.screens.live.LiveScreen
import pro.bixplayer.player.ui.screens.movies.MovieDetailScreen
import pro.bixplayer.player.ui.screens.movies.MovieDetailViewModel
import pro.bixplayer.player.ui.screens.player.PlayerScreen
import pro.bixplayer.player.ui.screens.parental.ParentalScreen
import pro.bixplayer.player.ui.screens.player.PlayerViewModel
import pro.bixplayer.player.ui.screens.player.ZapScope
import pro.bixplayer.player.ui.screens.playlists.ChangePlaylistScreen
import pro.bixplayer.player.ui.screens.series.SeriesDetailScreen
import pro.bixplayer.player.ui.screens.series.SeriesDetailViewModel
import pro.bixplayer.player.ui.screens.settings.SettingsScreen
import pro.bixplayer.player.ui.screens.splash.SplashScreen
import pro.bixplayer.player.ui.screens.update.UpdateScreen
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pro.bixplayer.player.ui.screens.playlists.PlaylistViewModel
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.compose.currentBackStackEntryAsState
import pro.bixplayer.player.R
import pro.bixplayer.player.ui.theme.LocalIsTv

/** Routes of the app. Constants so the graph and the tests agree on the strings. */
object Routes {
    const val SPLASH = "splash"
    const val ACTIVATION = "activation"
    const val EXPIRED = "expired"
    const val UPDATE = "update"
    const val HOME = "home"
    const val LIVE = "live"
    const val LIVE_PATTERN = "live?scope={scope}"

    fun live(scope: String? = null): String = if (scope == null) LIVE else "$LIVE?scope=$scope"
    const val PLAYER = "player"
    const val SETTINGS = "settings"
    const val CHANGE_PLAYLIST = "change_playlist"
    const val CATALOG = "catalog"
    const val MOVIE = "movie"
    const val SERIES = "series"
    const val EPG = "epg"
    const val PARENTAL = "parental"

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

    const val EPG_PATTERN = "$EPG?${EpgGridViewModel.ARG_CHANNEL_ID}={${EpgGridViewModel.ARG_CHANNEL_ID}}"

    fun epg(channelId: Long?): String = "$EPG?${EpgGridViewModel.ARG_CHANNEL_ID}=${channelId ?: -1L}"
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

    val isTv = LocalIsTv.current
    BixLocale(languageTag = language) {
        val backStack by navController.currentBackStackEntryAsState()
        val currentRoute = backStack?.destination?.route
        val tabRoutes = listOf(Routes.LIVE_PATTERN, Routes.CATALOG_PATTERN, Routes.EPG_PATTERN, Routes.SETTINGS)
        val showBar = !isTv && currentRoute in tabRoutes
        // On a phone there is no home screen, so the first sync of the active playlist is
        // owned here (activity scope) instead of by HomeScreen; a strip above the bar reports it.
        val phoneSync: PlaylistViewModel? = if (isTv) null else hiltViewModel()
        val phoneSyncState = phoneSync?.uiState?.collectAsStateWithLifecycle()?.value
        if (phoneSync != null) {
            LaunchedEffect(phoneSyncState?.activeId, destination) {
                if (phoneSyncState?.activeId != null && destination is BootDestination.Go) phoneSync.syncActive()
            }
        }
        val inPlayer = currentRoute?.startsWith(Routes.PLAYER) == true
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            // The player draws edge to edge, under the bars and the camera cutout.
            contentWindowInsets = if (inPlayer) WindowInsets(0) else ScaffoldDefaults.contentWindowInsets,
            bottomBar = {
                if (showBar) {
                    Column {
                        val strip = when {
                            phoneSyncState?.syncing == true -> stringResource(R.string.playlist_syncing)
                            else -> phoneSyncState?.notice
                        }
                        if (strip != null) {
                            Text(
                                text = strip,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(vertical = 6.dp, horizontal = 16.dp),
                            )
                        }
                    MobileBottomBar(
                        current = currentRoute,
                        currentKind = backStack?.arguments?.getString(CatalogViewModel.ARG_KIND),
                        onNavigate = { route ->
                            navController.navigate(route) {
                                // Live TV is the phone root: every tab replaces whatever sits
                                // above it, so Filmes -> Séries never reuses the movies entry.
                                popUpTo(Routes.LIVE_PATTERN) { inclusive = route == Routes.LIVE }
                                launchSingleTop = true
                            }
                        },
                    )
                    }
                }
            },
        ) { padding ->
        NavHost(navController = navController, startDestination = Routes.SPLASH, modifier = Modifier.padding(padding)) {
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
                if (!isTv) {
                    // Phones land on live TV with the bottom bar; the TV home is a menu.
                    LaunchedEffect(Unit) {
                        navController.navigate(Routes.LIVE) {
                            popUpTo(Routes.HOME) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                    return@composable
                }
                HomeScreen(
                    config = config,
                    onLive = { navController.navigate(Routes.LIVE) { launchSingleTop = true } },
                    onFavorites = { navController.navigate(Routes.live(ZapScope.FAVORITES)) { launchSingleTop = true } },
                    onMovies = { navController.navigate(Routes.catalog(ContentKind.MOVIE)) { launchSingleTop = true } },
                    onSeries = { navController.navigate(Routes.catalog(ContentKind.SERIES)) { launchSingleTop = true } },
                    onGuide = { navController.navigate(Routes.epg(null)) { launchSingleTop = true } },
                    onSettings = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                    onResume = { kind, id -> navController.navigate(Routes.playerVod(kind, id, resume = true)) },
                )
            }

            composable(
                route = Routes.LIVE_PATTERN,
                arguments = listOf(navArgument("scope") { type = NavType.StringType; nullable = true; defaultValue = null }),
            ) {
                LiveScreen(
                    onOpenChannel = { channel, scopeKey ->
                        navController.navigate(Routes.player(channel.id, scopeKey)) { launchSingleTop = true }
                    },
                    onOpenGuide = { channel -> navController.navigate(Routes.epg(channel?.id)) { launchSingleTop = true } },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(
                route = Routes.EPG_PATTERN,
                arguments = listOf(navArgument(EpgGridViewModel.ARG_CHANNEL_ID) { type = NavType.LongType; defaultValue = -1L }),
            ) {
                EpgGridScreen(
                    onPlayChannel = { channel ->
                        navController.navigate(Routes.player(channel.id, ZapScope.ALL)) { launchSingleTop = true }
                    },
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
                    onParental = { navController.navigate(Routes.PARENTAL) },
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

            composable(Routes.PARENTAL) {
                ParentalScreen(onBack = { navController.popBackStack() })
            }
        }
        }
    }
}

/** TV / Filmes / Séries / Guia / Mais. */
@Composable
private fun MobileBottomBar(current: String?, currentKind: String?, onNavigate: (String) -> Unit) {
    data class Tab(val label: String, val icon: String, val route: String, val selected: Boolean)
    val tabs = listOf(
        Tab(stringResource(R.string.home_live), "▶", Routes.LIVE, current == Routes.LIVE_PATTERN),
        Tab(stringResource(R.string.home_movies), "🎬", Routes.catalog(ContentKind.MOVIE), current == Routes.CATALOG_PATTERN && currentKind == ContentKind.MOVIE),
        Tab(stringResource(R.string.home_series), "📺", Routes.catalog(ContentKind.SERIES), current == Routes.CATALOG_PATTERN && currentKind == ContentKind.SERIES),
        Tab(stringResource(R.string.live_guide), "▦", Routes.epg(null), current == Routes.EPG_PATTERN),
        Tab(stringResource(R.string.mobile_more), "⋯", Routes.SETTINGS, current == Routes.SETTINGS),
    )
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        tabs.forEach { tab ->
            NavigationBarItem(
                selected = tab.selected,
                onClick = { if (!tab.selected) onNavigate(tab.route) },
                icon = { Text(tab.icon) },
                label = { Text(tab.label, maxLines = 1) },
            )
        }
    }
}
