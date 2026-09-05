package pro.bixplayer.player.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.os.ConfigurationCompat
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import pro.bixplayer.player.R
import pro.bixplayer.player.domain.model.AppConfig
import pro.bixplayer.player.ui.screens.playlists.PlaylistViewModel
import pro.bixplayer.player.ui.theme.BixFocus
import pro.bixplayer.player.ui.theme.BixScrim
import pro.bixplayer.player.ui.theme.BixSuccess
import pro.bixplayer.player.ui.theme.bixFocusable

/**
 * Home, layout `default`: reseller branding on top (logo, status, clock), the four sections in
 * the middle and the banner carousel at the bottom. Movies and series are visible but disabled
 * in the M3 so the user learns the layout before the M4 fills them.
 */
@Composable
fun HomeScreen(
    config: AppConfig?,
    onLive: () -> Unit,
    onSettings: () -> Unit,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // First sync happens as soon as the device is usable; it is a no-op when already synced.
    LaunchedEffect(state.activeId) {
        if (state.activeId != null) viewModel.syncActive()
    }

    val liveRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60)
        runCatching { liveRequester.requestFocus() }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val backgroundUrl = config?.backgroundUrl
        if (!backgroundUrl.isNullOrBlank()) {
            AsyncImage(
                model = backgroundUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Scrim so the menu stays legible over any background the reseller uploads.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(BixScrim, Color(0x66000000), BixScrim),
                    ),
                ),
        )

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 32.dp)) {
            TopBar(config = config, syncing = state.syncing, channelCount = state.channelCount)

            Spacer(Modifier.weight(1f))

            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                MenuCard(
                    title = stringResource(R.string.home_live),
                    icon = "▶",
                    subtitle = when {
                        state.syncing -> stringResource(R.string.playlist_syncing)
                        state.channelCount > 0 -> stringResource(R.string.live_channels_count, state.channelCount)
                        else -> stringResource(R.string.playlist_not_synced)
                    },
                    enabled = true,
                    focusRequester = liveRequester,
                    onClick = onLive,
                    modifier = Modifier.weight(1f),
                )
                MenuCard(
                    title = stringResource(R.string.home_movies),
                    icon = "🎬",
                    subtitle = stringResource(R.string.home_coming_soon),
                    enabled = false,
                    onClick = {},
                    modifier = Modifier.weight(1f),
                )
                MenuCard(
                    title = stringResource(R.string.home_series),
                    icon = "📺",
                    subtitle = stringResource(R.string.home_coming_soon),
                    enabled = false,
                    onClick = {},
                    modifier = Modifier.weight(1f),
                )
                MenuCard(
                    title = stringResource(R.string.home_settings),
                    icon = "⚙",
                    subtitle = config?.macAddress.orEmpty(),
                    enabled = true,
                    onClick = onSettings,
                    modifier = Modifier.weight(1f),
                )
            }

            state.notice?.let { notice ->
                Spacer(Modifier.height(16.dp))
                Text(
                    text = notice,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.weight(1f))

            val banners = config?.banners.orEmpty()
            if (banners.isNotEmpty() && (config?.autoAds == true || banners.isNotEmpty())) {
                BannerCarousel(banners = banners.map { it.url to it.title })
            }
        }
    }
}

@Composable
private fun TopBar(config: AppConfig?, syncing: Boolean, channelCount: Int) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalTime.now()
            delay(15_000)
        }
    }
    // The date speaks the language chosen in the settings, not the system one.
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.forLanguageTag("pt-BR")
    val clock = remember(now) { now.format(DateTimeFormatter.ofPattern("HH:mm")) }
    val date = remember(now.hour, locale) {
        LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, dd MMM", locale))
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        val logoUrl = config?.logoUrl
        if (!logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = logoUrl,
                contentDescription = config.platformName,
                contentScale = ContentScale.Fit,
                modifier = Modifier.heightIn(max = 56.dp).width(180.dp),
            )
        } else {
            Text(
                text = config?.platformName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Spacer(Modifier.weight(1f))

        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(BixSuccess))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.status_active),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                config?.licenseExpiresAt?.take(10)?.let { iso ->
                    Text(
                        text = "  ·  " + stringResource(R.string.status_expires_on, formatDate(iso)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = "$date  ·  $clock",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    @Suppress("UNUSED_EXPRESSION") syncing
    @Suppress("UNUSED_EXPRESSION") channelCount
}

/** `2026-12-31` → `31/12/2026`; anything else is shown as received. */
internal fun formatDate(iso: String): String {
    val parts = iso.split('-')
    return if (parts.size == 3 && parts.all { it.isNotEmpty() }) "${parts[2]}/${parts[1]}/${parts[0]}" else iso
}

@Composable
private fun MenuCard(
    title: String,
    icon: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(16.dp)

    Column(
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .height(170.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .bixFocusable(focused, scale = BixFocus.SCALE_SMALL, shape = shape)
            .background(
                if (focused) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                shape,
            )
            .alpha(if (enabled) 1f else 0.55f)
            .focusable(interactionSource = interaction)
            .onKeyEvent { event ->
                val select = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                if (enabled && select && event.type == KeyEventType.KeyUp) {
                    onClick(); true
                } else {
                    false
                }
            }
            .padding(horizontal = 20.dp, vertical = 24.dp),
    ) {
        Text(text = icon, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Auto-advancing banner strip. Images come straight from the reseller's URLs. */
@Composable
private fun BannerCarousel(banners: List<Pair<String, String>>) {
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(banners.size) {
        if (banners.size < 2) return@LaunchedEffect
        while (true) {
            delay(BANNER_INTERVAL_MS)
            index = (index + 1) % banners.size
        }
    }
    val (url, title) = banners[index.coerceIn(0, banners.lastIndex)]

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        AsyncImage(
            model = url,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (banners.size > 1) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            ) {
                banners.indices.forEach { i ->
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (i == index) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.5f)),
                    )
                }
            }
        }
    }
}

private const val BANNER_INTERVAL_MS = 6_000L
