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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.annotation.DrawableRes
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import pro.bixplayer.player.ui.components.BrandLogo
import kotlinx.coroutines.delay
import pro.bixplayer.player.R
import pro.bixplayer.player.domain.model.AppConfig
import pro.bixplayer.player.ui.theme.BixFocus
import pro.bixplayer.player.ui.theme.BixScrim
import pro.bixplayer.player.ui.theme.bixFocusable
import pro.bixplayer.player.ui.components.onSelect
import pro.bixplayer.player.ui.components.requestFocusWithRetry

/** One tile of the grid home. */
data class GridTile(
    val title: String,
    val subtitle: String,
    /** Vector icon (F2-003): emoji rendered differently on every box and looked amateur. */
    @DrawableRes val icon: Int,
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
    /** Index of the tile that takes the first focus (the Playlist tile in demo mode). */
    firstFocus: Int = 0,
) {
    val firstRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60)
        firstRequester.requestFocusWithRetry()
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val backgroundUrl = config?.backgroundUrl
        if (!backgroundUrl.isNullOrBlank()) {
            AsyncImage(model = backgroundUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(BixScrim, Color(0x99000000), BixScrim))))

        // A phone in landscape is ~390 dp tall against 540 on a TV: three rows of three tiles
        // left each tile shorter than its own text (client, 28/09/2026). Two rows of four fit.
        val short = LocalConfiguration.current.screenHeightDp < 500
        val perRow = if (short) 4 else 3
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = if (short) 24.dp else 48.dp, vertical = if (short) 12.dp else 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                BrandLogo(
                    url = config?.logoUrl,
                    contentDescription = config?.platformName?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.app_name),
                    modifier = Modifier.heightIn(max = 48.dp).width(200.dp),
                )
                Spacer(Modifier.weight(1f))
                config?.qrContent?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            notice?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
            }
            Spacer(Modifier.height(if (short) 10.dp else 16.dp))

            tiles.chunked(perRow).forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(if (short) 10.dp else 16.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
                    row.forEachIndexed { colIndex, tile ->
                        GridTileCard(
                            tile = tile,
                            focusRequester = if (rowIndex * perRow + colIndex == firstFocus) firstRequester else null,
                            modifier = Modifier.weight(1f).fillMaxSize(),
                        )
                    }
                    // Keep the last row's tiles the same width as the others.
                    repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
                }
                if (rowIndex < (tiles.size - 1) / perRow) Spacer(Modifier.height(if (short) 10.dp else 16.dp))
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
        val short = LocalConfiguration.current.screenHeightDp < 500
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(if (short) 14.dp else 20.dp)) {
            Icon(
                painter = painterResource(tile.icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(if (short) 26.dp else 34.dp),
            )
            Spacer(Modifier.height(if (short) 4.dp else 6.dp))
            Text(
                text = tile.title,
                style = if (short) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
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
    // Banners are 16:9 frames (the platform's ready-made ones are, F2-012): shown whole, side by
    // side, instead of cropped to a band that lost their text.
    val short = LocalConfiguration.current.screenHeightDp < 500
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().height(if (short) 72.dp else 96.dp)) {
        banners.take(3).forEach { (url, title) ->
            Box(modifier = Modifier.fillMaxHeight().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surface)) {
                AsyncImage(model = url, contentDescription = title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
    }
}
