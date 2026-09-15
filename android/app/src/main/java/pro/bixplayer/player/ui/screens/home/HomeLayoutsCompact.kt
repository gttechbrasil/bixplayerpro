package pro.bixplayer.player.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import pro.bixplayer.player.R
import pro.bixplayer.player.data.db.WatchProgressEntity
import pro.bixplayer.player.domain.model.AppConfig
import pro.bixplayer.player.domain.model.AppLayout
import pro.bixplayer.player.ui.components.BrandLogo
import pro.bixplayer.player.ui.components.onSelect
import pro.bixplayer.player.util.TimeFormat

/**
 * The home on a phone (M5-029). It used to be skipped entirely — phones jumped straight to live
 * TV — so a reseller who picked a layout saw no difference on mobile. Each layout keeps its
 * character here, adapted to one hand and a portrait screen: everything scrolls vertically,
 * rows of covers scroll sideways, and the tiles are two per row instead of a wide strip.
 */
@Composable
fun CompactHomeScreen(
    layout: AppLayout,
    config: AppConfig?,
    tiles: List<GridTile>,
    artwork: HomeArtwork,
    notice: String?,
    continueWatching: List<WatchProgressEntity>,
    onResume: (WatchProgressEntity) -> Unit,
    demo: Boolean,
) {
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val backdrop = artwork.featuredImage ?: config?.backgroundUrl
        if (layout != AppLayout.GRID && !backdrop.isNullOrBlank()) {
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.25f,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background))),
            )
        }

        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CompactHeader(config = config, demo = demo, notice = notice)

            when (layout) {
                AppLayout.CINEMA -> {
                    CompactHero(artwork)
                    CompactChips(tiles)
                }

                AppLayout.RAIL -> tiles.forEach { CompactMenuRow(it) }

                AppLayout.MOSAIC -> {
                    tiles.firstOrNull()?.let { CompactBigTile(it, artwork.featuredImage) }
                    CompactTileGrid(tiles.drop(1).take(2))
                    CompactChips(tiles.drop(3))
                }

                else -> CompactTileGrid(tiles)
            }

            if (continueWatching.isNotEmpty()) {
                CompactSection(stringResource(R.string.home_continue_watching)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(continueWatching, key = { it.kind + it.itemRemoteId }) { progress ->
                            CompactProgressCard(progress) { onResume(progress) }
                        }
                    }
                }
            }
            if (artwork.movies.isNotEmpty()) {
                CompactSection(stringResource(R.string.home_recent_movies)) {
                    CompactPosterRow(artwork.movies)
                }
            }
            if (artwork.series.isNotEmpty()) {
                CompactSection(stringResource(R.string.home_recent_series)) {
                    CompactPosterRow(artwork.series)
                }
            }
            val banners = config?.banners.orEmpty()
            if (banners.isNotEmpty()) {
                CompactSection(stringResource(R.string.home_banners)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(banners, key = { it.id }) { banner ->
                            AsyncImage(
                                model = banner.url,
                                contentDescription = banner.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(260.dp)
                                    .height(96.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(MaterialTheme.colorScheme.surface),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun CompactHeader(config: AppConfig?, demo: Boolean, notice: String?) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            BrandLogo(
                url = config?.logoUrl,
                contentDescription = config?.platformName?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.app_name),
                modifier = Modifier.heightIn(max = 34.dp).width(150.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(if (demo) R.string.home_awaiting_activation else R.string.status_active),
                style = MaterialTheme.typography.bodyMedium,
                color = if (demo) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        notice?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun CompactSection(title: String, content: @Composable () -> Unit) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        content()
    }
}

/** Cinema: the newest title as a 16:9 card with its name over the artwork. */
@Composable
private fun CompactHero(artwork: HomeArtwork) {
    val title = artwork.featuredTitle ?: return
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface),
    ) {
        artwork.featuredImage?.let {
            AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000)))))
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Text(
                text = stringResource(R.string.home_featured),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            artwork.featuredSubtitle?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
            }
        }
    }
}

/** A horizontal strip of small menu buttons; what the chip row is on TV. */
@Composable
private fun CompactChips(tiles: List<GridTile>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(tiles, key = { it.title }) { tile ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .focusable()
                    .onSelect { if (tile.enabled) tile.onClick() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Icon(
                    painter = painterResource(tile.icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = tile.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Rail: one full-width row per entry, the vertical menu the layout is named after. */
@Composable
private fun CompactMenuRow(tile: GridTile) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .focusable()
                    .onSelect { if (tile.enabled) tile.onClick() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(
            painter = painterResource(tile.icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(text = tile.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            if (tile.subtitle.isNotBlank()) {
                Text(
                    text = tile.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = if (tile.subtitle.startsWith("02:50:50")) FontFamily.Monospace else null,
                )
            }
        }
        Text(text = "›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Mosaic: the first entry as a wide cover card. */
@Composable
private fun CompactBigTile(tile: GridTile, fallbackCover: String?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .focusable()
                    .onSelect { if (tile.enabled) tile.onClick() },
    ) {
        (tile.coverUrl?.takeIf { it.isNotBlank() } ?: fallbackCover)?.let {
            AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, alpha = 0.55f, modifier = Modifier.fillMaxSize())
        }
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Icon(
                painter = painterResource(tile.icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(30.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(text = tile.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
            Text(text = tile.subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f), maxLines = 1)
        }
    }
}

/** Two tiles per row: the widest a finger-friendly card can be in portrait. */
@Composable
private fun CompactTileGrid(tiles: List<GridTile>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { tile -> CompactTile(tile, Modifier.weight(1f)) }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CompactTile(tile: GridTile, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(118.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .focusable()
                    .onSelect { if (tile.enabled) tile.onClick() },
    ) {
        tile.coverUrl?.takeIf { it.isNotBlank() }?.let {
            AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, alpha = 0.35f, modifier = Modifier.fillMaxSize())
        }
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(14.dp)) {
            Icon(
                painter = painterResource(tile.icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(text = tile.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = tile.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontFamily = if (tile.subtitle.startsWith("02:50:50")) FontFamily.Monospace else null,
            )
        }
    }
}

@Composable
private fun CompactPosterRow(posters: List<HomePoster>) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(posters, key = { it.title + (it.imageUrl ?: "") }) { poster ->
            Column(
                modifier = Modifier
                    .width(104.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .focusable()
                    .onSelect { poster.onClick() },
            ) {
                Box(modifier = Modifier.fillMaxWidth().height(148.dp).background(MaterialTheme.colorScheme.background)) {
                    if (!poster.imageUrl.isNullOrBlank()) {
                        AsyncImage(model = poster.imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Text(
                            text = poster.title.take(2).uppercase(),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
                Text(
                    text = poster.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp).padding(top = 6.dp, bottom = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun CompactProgressCard(progress: WatchProgressEntity, onOpen: () -> Unit) {
    Column(
        modifier = Modifier
            .width(168.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surface)
            .focusable()
            .onSelect(onOpen),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(94.dp).background(MaterialTheme.colorScheme.background)) {
            if (!progress.posterUrl.isNullOrBlank()) {
                AsyncImage(model = progress.posterUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            val fraction = if (progress.durationMs > 0) (progress.positionMs.toFloat() / progress.durationMs).coerceIn(0f, 1f) else 0f
            Box(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.25f)))
            Box(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(fraction).height(3.dp).background(MaterialTheme.colorScheme.primary))
        }
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(text = progress.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = progress.subtitle ?: TimeFormat.clock(progress.positionMs),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
