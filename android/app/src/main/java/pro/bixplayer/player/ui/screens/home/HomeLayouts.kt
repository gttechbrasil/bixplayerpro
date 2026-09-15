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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import pro.bixplayer.player.R
import pro.bixplayer.player.domain.model.AppConfig
import pro.bixplayer.player.ui.components.BrandLogo
import pro.bixplayer.player.ui.components.onSelect
import pro.bixplayer.player.ui.components.requestFocusWithRetry
import pro.bixplayer.player.ui.theme.BixFocus
import pro.bixplayer.player.ui.theme.BixScrim
import pro.bixplayer.player.ui.theme.bixFocusable

/**
 * One poster in a "recently added" row: the cover the provider gave us, the title underneath.
 * Opening it goes to the catalogue rather than the title itself — the home is a menu, not a
 * detail screen, and a wrong guess on a remote is expensive.
 */
data class HomePoster(val title: String, val subtitle: String?, val imageUrl: String?, val onClick: () -> Unit)

/** What the artwork layouts (cinema, rail, mosaic) need beyond the menu tiles. */
data class HomeArtwork(
    val featuredTitle: String?,
    val featuredSubtitle: String?,
    val featuredImage: String?,
    val movies: List<HomePoster>,
    val series: List<HomePoster>,
)

// ---------------------------------------------------------------------------------------------
// Cinema: full-bleed artwork with the menu as a chip row. The closest thing to a streaming app.
// ---------------------------------------------------------------------------------------------

@Composable
fun CinemaHomeScreen(
    config: AppConfig?,
    tiles: List<GridTile>,
    artwork: HomeArtwork,
    notice: String?,
    firstFocus: Int = 0,
) {
    val firstRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60)
        firstRequester.requestFocusWithRetry()
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val hero = artwork.featuredImage ?: config?.backgroundUrl
        if (!hero.isNullOrBlank()) {
            AsyncImage(
                model = hero,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Two gradients: one so the top bar reads over artwork, one so the menu row does.
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(BixScrim, Color(0x55000000), Color(0xE6000000))),
            ),
        )

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 48.dp, vertical = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                BrandLogo(
                    url = config?.logoUrl,
                    contentDescription = config?.platformName?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.app_name),
                    modifier = Modifier.heightIn(max = 44.dp).width(190.dp),
                )
                Spacer(Modifier.weight(1f))
                notice?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(Modifier.weight(1f))

            artwork.featuredTitle?.let { title ->
                Text(
                    text = stringResource(R.string.home_featured),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.6f),
                )
                artwork.featuredSubtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(0.55f),
                    )
                }
                Spacer(Modifier.height(20.dp))
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                itemsIndexed(tiles, key = { _, tile -> tile.title }) { index, tile ->
                    ActionChip(
                        tile = tile,
                        focusRequester = if (index == firstFocus) firstRequester else null,
                    )
                }
            }

            if (artwork.movies.isNotEmpty()) {
                Spacer(Modifier.height(22.dp))
                PosterRow(
                    title = stringResource(R.string.home_recent_movies),
                    posters = artwork.movies,
                    posterHeight = 148.dp,
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Rail: vertical menu on the left, artwork on the right. The classic IPTV box look.
// ---------------------------------------------------------------------------------------------

@Composable
fun RailHomeScreen(
    config: AppConfig?,
    tiles: List<GridTile>,
    artwork: HomeArtwork,
    notice: String?,
    firstFocus: Int = 0,
) {
    val firstRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60)
        firstRequester.requestFocusWithRetry()
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val backdrop = artwork.featuredImage ?: config?.backgroundUrl
        if (!backdrop.isNullOrBlank()) {
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Color(0xF2000000), Color(0xCC000000), Color(0x66000000))),
            ),
        )

        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .width(300.dp)
                    .fillMaxHeight()
                    .background(Color(0xCC0A0A0A))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 28.dp),
            ) {
                BrandLogo(
                    url = config?.logoUrl,
                    contentDescription = config?.platformName?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.app_name),
                    modifier = Modifier.heightIn(max = 40.dp).width(180.dp),
                )
                Spacer(Modifier.height(12.dp))
                tiles.forEachIndexed { index, tile ->
                    RailItem(
                        tile = tile,
                        focusRequester = if (index == firstFocus) firstRequester else null,
                    )
                }
                notice?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }

            Column(modifier = Modifier.weight(1f).fillMaxHeight().padding(32.dp)) {
                Spacer(Modifier.weight(1f))
                artwork.featuredTitle?.let { title ->
                    Text(
                        text = stringResource(R.string.home_featured),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(18.dp))
                }
                if (artwork.series.isNotEmpty()) {
                    PosterRow(title = stringResource(R.string.home_recent_series), posters = artwork.series)
                    Spacer(Modifier.height(18.dp))
                }
                if (artwork.movies.isNotEmpty()) {
                    PosterRow(title = stringResource(R.string.home_recent_movies), posters = artwork.movies)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Mosaic: one tall tile plus a stack of wide ones, all wearing real covers.
// ---------------------------------------------------------------------------------------------

@Composable
fun MosaicHomeScreen(
    config: AppConfig?,
    tiles: List<GridTile>,
    artwork: HomeArtwork,
    notice: String?,
    firstFocus: Int = 0,
) {
    val firstRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60)
        firstRequester.requestFocusWithRetry()
    }
    // The first tile owns the tall panel; the rest stack on the right, then wrap to the strip.
    val hero = tiles.firstOrNull()
    val side = tiles.drop(1).take(2)
    val strip = tiles.drop(3)

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val backdrop = artwork.featuredImage ?: config?.backgroundUrl
        if (!backdrop.isNullOrBlank()) {
            AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.35f,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(BixScrim, Color(0xD9000000)))))

        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                BrandLogo(
                    url = config?.logoUrl,
                    contentDescription = config?.platformName?.takeIf { it.isNotBlank() }
                        ?: stringResource(R.string.app_name),
                    modifier = Modifier.heightIn(max = 42.dp).width(180.dp),
                )
                Spacer(Modifier.weight(1f))
                notice?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth().weight(1f)) {
                hero?.let {
                    MosaicPanel(
                        tile = it,
                        fallbackCover = artwork.featuredImage,
                        tall = true,
                        focusRequester = if (firstFocus == 0) firstRequester else null,
                        modifier = Modifier.weight(1.4f).fillMaxHeight(),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.weight(1f).fillMaxHeight()) {
                    side.forEachIndexed { index, tile ->
                        MosaicPanel(
                            tile = tile,
                            tall = false,
                            focusRequester = if (firstFocus == index + 1) firstRequester else null,
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                    }
                }
            }

            if (strip.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    strip.forEachIndexed { index, tile ->
                        ActionChip(
                            tile = tile,
                            focusRequester = if (firstFocus == index + 3) firstRequester else null,
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------------------------

@Composable
private fun ActionChip(tile: GridTile, focusRequester: FocusRequester?) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .bixFocusable(focused, scale = BixFocus.SCALE_SMALL, shape = shape)
            .clip(shape)
            .background(if (focused) MaterialTheme.colorScheme.primary else Color(0x99161616))
            .focusable(interactionSource = interaction)
            .onSelect { if (tile.enabled) tile.onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Icon(
            painter = painterResource(tile.icon),
            contentDescription = null,
            tint = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Column {
            Text(
                text = tile.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (focused) MaterialTheme.colorScheme.onPrimary else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (tile.subtitle.isNotBlank()) {
                Text(
                    text = tile.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RailItem(tile: GridTile, focusRequester: FocusRequester?) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .bixFocusable(focused, scale = BixFocus.SCALE_SMALL, shape = shape)
            .clip(shape)
            .background(if (focused) MaterialTheme.colorScheme.primary else Color.Transparent)
            .focusable(interactionSource = interaction)
            .onSelect { if (tile.enabled) tile.onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(
            painter = painterResource(tile.icon),
            contentDescription = null,
            tint = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tile.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (focused) MaterialTheme.colorScheme.onPrimary else Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (tile.subtitle.isNotBlank()) {
                Text(
                    text = tile.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MosaicPanel(
    tile: GridTile,
    tall: Boolean,
    focusRequester: FocusRequester?,
    modifier: Modifier = Modifier,
    fallbackCover: String? = null,
) {
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
        val cover = tile.coverUrl?.takeIf { it.isNotBlank() } ?: fallbackCover
        if (!cover.isNullOrBlank()) {
            AsyncImage(
                model = cover,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = if (focused) 0.65f else 0.45f,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000)))))
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(if (tall) 26.dp else 18.dp)) {
            Icon(
                painter = painterResource(tile.icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(if (tall) 44.dp else 30.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = tile.title,
                style = if (tall) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
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

/** A horizontal strip of covers. Focusable so the D-pad can walk it; opens the catalogue. */
@Composable
private fun PosterRow(title: String, posters: List<HomePoster>, posterHeight: Dp = 180.dp) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
        Spacer(Modifier.height(10.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(posters, key = { it.title + (it.imageUrl ?: "") }) { poster ->
                PosterCard(poster, posterHeight)
            }
        }
    }
}

@Composable
private fun PosterCard(poster: HomePoster, posterHeight: Dp) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = Modifier
            .width(126.dp)
            .bixFocusable(focused, scale = BixFocus.SCALE_SMALL, shape = shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .focusable(interactionSource = interaction)
            .onSelect { poster.onClick() },
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(posterHeight).background(MaterialTheme.colorScheme.background)) {
            if (!poster.imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = poster.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = poster.title.take(2).uppercase(),
                    style = MaterialTheme.typography.headlineSmall,
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
            modifier = Modifier.padding(horizontal = 8.dp).padding(top = 6.dp),
        )
        Text(
            text = poster.subtitle.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp),
        )
    }
}
