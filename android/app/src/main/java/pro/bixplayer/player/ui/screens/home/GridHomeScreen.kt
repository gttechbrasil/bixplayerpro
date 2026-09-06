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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import pro.bixplayer.player.R
import pro.bixplayer.player.domain.model.AppConfig
import pro.bixplayer.player.ui.theme.BixFocus
import pro.bixplayer.player.ui.theme.BixScrim
import pro.bixplayer.player.ui.theme.bixFocusable
import pro.bixplayer.player.ui.components.onSelect

/** One tile of the grid home. */
data class GridTile(
    val title: String,
    val subtitle: String,
    val icon: String,
    val coverUrl: String?,
    val enabled: Boolean,
    val onClick: () -> Unit,
)

/**
 * Home, layout `grid`: no side menu, six big tiles (live, movies, series, favourites, guide,
 * settings) with counts and a highlight cover, banners underneath. Same branding rules as the
 * default layout: logo, background, banners and QR come from the panel.
 */
@Composable
fun GridHomeScreen(
    config: AppConfig?,
    tiles: List<GridTile>,
    banners: List<Pair<String, String>>,
    notice: String?,
) {
    val firstRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60)
        runCatching { firstRequester.requestFocus() }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val backgroundUrl = config?.backgroundUrl
        if (!backgroundUrl.isNullOrBlank()) {
            AsyncImage(model = backgroundUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(BixScrim, Color(0x99000000), BixScrim))))

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                val logoUrl = config?.logoUrl
                if (!logoUrl.isNullOrBlank()) {
                    AsyncImage(model = logoUrl, contentDescription = config.platformName, contentScale = ContentScale.Fit, modifier = Modifier.heightIn(max = 48.dp).width(160.dp))
                } else {
                    Text(
                        text = config?.platformName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Spacer(Modifier.weight(1f))
                config?.qrContent?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            notice?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(16.dp))

            tiles.chunked(3).forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
                    row.forEachIndexed { colIndex, tile ->
                        GridTileCard(
                            tile = tile,
                            focusRequester = if (rowIndex == 0 && colIndex == 0) firstRequester else null,
                            modifier = Modifier.weight(1f).fillMaxSize(),
                        )
                    }
                }
                if (rowIndex == 0) Spacer(Modifier.height(16.dp))
            }

            if (banners.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                BannerStrip(banners = banners)
            }
        }
    }
}

@Composable
private fun GridTileCard(tile: GridTile, focusRequester: FocusRequester?, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .bixFocusable(focused, scale = BixFocus.SCALE_SMALL, shape = shape)
            .clip(shape)
            .background(if (focused) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
            .focusable(interactionSource = interaction)
            .onSelect { if (tile.enabled) tile.onClick() },
    ) {
        if (!tile.coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = tile.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = if (focused) 0.5f else 0.35f,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(20.dp)) {
            Text(text = tile.icon, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(
                text = tile.title,
                style = MaterialTheme.typography.titleLarge,
                color = if (tile.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = tile.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BannerStrip(banners: List<Pair<String, String>>) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().height(90.dp)) {
        banners.take(3).forEach { (url, title) ->
            Box(modifier = Modifier.weight(1f).fillMaxSize().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface)) {
                AsyncImage(model = url, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
