package pro.bixplayer.player.ui.demo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import pro.bixplayer.player.R
import pro.bixplayer.player.ui.components.BixButton
import pro.bixplayer.player.ui.components.onSelect
import pro.bixplayer.player.ui.components.requestFocusWithRetry
import pro.bixplayer.player.ui.theme.BixFocus
import pro.bixplayer.player.ui.theme.LocalIsTv
import pro.bixplayer.player.ui.theme.bixFocusable

/** Which catalogue the showcase stands in for; only changes the title and the card shape. */
enum class DemoKind { LIVE, MOVIES, SERIES, GUIDE }

/**
 * Placeholder catalogue shown while the device has no playlist (F2-001): a notice with the
 * MAC and a button to the Playlist screen, then a grid of generic "landscape" cards drawn on
 * the fly (no bitmaps, nothing to download — a 1 GB box renders it for free). Selecting any
 * card also opens the Playlist screen: whatever the user presses leads to activation.
 */
@Composable
fun DemoShowcase(kind: DemoKind) {
    val demo = LocalDemoState.current
    val compact = !LocalIsTv.current
    val buttonFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        buttonFocus.requestFocusWithRetry()
    }

    val title = stringResource(
        when (kind) {
            DemoKind.LIVE -> R.string.home_live
            DemoKind.MOVIES -> R.string.home_movies
            DemoKind.SERIES -> R.string.home_series
            DemoKind.GUIDE -> R.string.epg_title
        },
    )
    val cardLabel = stringResource(R.string.demo_card)
    val columns = when {
        compact -> 3
        kind == DemoKind.LIVE || kind == DemoKind.GUIDE -> 4
        else -> 6
    }
    val ratio = if (kind == DemoKind.MOVIES || kind == DemoKind.SERIES) 2f / 3f else 16f / 9f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = if (compact) 16.dp else 48.dp, vertical = if (compact) 16.dp else 28.dp),
    ) {
        Text(
            text = title,
            style = if (compact) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Spacer(Modifier.height(12.dp))
        DemoNotice(macAddress = demo.macAddress, compact = compact, focusRequester = buttonFocus, onOpen = demo.openPlaylist)
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(DEMO_CARDS, key = { it }) { index ->
                DemoCard(index = index, ratio = ratio, label = cardLabel, onOpen = demo.openPlaylist)
            }
        }
    }
}

@Composable
private fun DemoNotice(macAddress: String, compact: Boolean, focusRequester: FocusRequester, onOpen: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val button: @Composable () -> Unit = {
        BixButton(
            text = stringResource(R.string.demo_open_playlist),
            onClick = onOpen,
            focusRequester = focusRequester,
            modifier = if (compact) Modifier.fillMaxWidth() else Modifier,
        )
    }
    if (compact) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, shape).padding(16.dp),
        ) {
            NoticeText(macAddress = macAddress, compact = true)
            button()
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, shape).padding(20.dp),
        ) {
            NoticeText(macAddress = macAddress, compact = false, modifier = Modifier.weight(1f))
            button()
        }
    }
}

@Composable
private fun NoticeText(macAddress: String, compact: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = stringResource(R.string.demo_notice),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (macAddress.isNotBlank()) {
            Text(
                text = macAddress,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/** A "landscape" poster: sky gradient, sun, two mountain ridges and water, hue varied per card. */
@Composable
private fun DemoCard(index: Int, ratio: Float, label: String, onOpen: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    val palette = remember(index) { DemoPalette.forIndex(index) }

    Column(
        modifier = Modifier
            .bixFocusable(focused, scale = BixFocus.SCALE_SMALL, shape = shape)
            .clip(shape)
            .background(if (focused) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface)
            .focusable(interactionSource = interaction)
            .onSelect { onOpen() },
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(ratio)) {
            val w = size.width
            val h = size.height
            drawRect(Brush.verticalGradient(listOf(palette.skyTop, palette.skyBottom)))
            drawCircle(color = palette.sun, radius = h * 0.12f, center = Offset(w * palette.sunX, h * 0.30f))
            val far = Path().apply {
                moveTo(0f, h * 0.62f)
                lineTo(w * 0.25f, h * 0.38f)
                lineTo(w * 0.45f, h * 0.58f)
                lineTo(w * 0.70f, h * 0.34f)
                lineTo(w, h * 0.60f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(far, palette.farRidge)
            val near = Path().apply {
                moveTo(0f, h * 0.78f)
                lineTo(w * 0.20f, h * 0.58f)
                lineTo(w * 0.42f, h * 0.74f)
                lineTo(w * 0.62f, h * 0.52f)
                lineTo(w * 0.85f, h * 0.72f)
                lineTo(w, h * 0.66f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(near, palette.nearRidge)
            drawRect(
                Brush.verticalGradient(listOf(palette.water, palette.water.copy(alpha = 0.7f))),
                topLeft = Offset(0f, h * 0.84f),
                size = androidx.compose.ui.geometry.Size(w, h * 0.16f),
            )
        }
        Text(
            text = "$label ${index + 1}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

private val DEMO_CARDS = (0 until 18).toList()

/** Six colour moods, cycled; every value chosen to keep the cards calm next to the orange accent. */
internal data class DemoPalette(
    val skyTop: Color,
    val skyBottom: Color,
    val sun: Color,
    val sunX: Float,
    val farRidge: Color,
    val nearRidge: Color,
    val water: Color,
) {
    companion object {
        private val MOODS = listOf(
            DemoPalette(Color(0xFF1B3A5C), Color(0xFFE08A4E), Color(0xFFFFD27A), 0.70f, Color(0xFF2E4A6B), Color(0xFF1C2E44), Color(0xFF244B6E)),
            DemoPalette(Color(0xFF0F2A3F), Color(0xFF6FA8DC), Color(0xFFFFF3C4), 0.30f, Color(0xFF3E6B8C), Color(0xFF244A66), Color(0xFF1E4D6B)),
            DemoPalette(Color(0xFF3A1C4E), Color(0xFFE2647A), Color(0xFFFFC98B), 0.55f, Color(0xFF4C2F5E), Color(0xFF2A1A36), Color(0xFF5B2E5E)),
            DemoPalette(Color(0xFF12321F), Color(0xFF9CC77A), Color(0xFFFFF0A8), 0.20f, Color(0xFF2F6B3E), Color(0xFF1B4426), Color(0xFF285C4C)),
            DemoPalette(Color(0xFF2B2B4A), Color(0xFF8E7CC3), Color(0xFFFFE4B5), 0.80f, Color(0xFF3F3F6E), Color(0xFF26264A), Color(0xFF34345C)),
            DemoPalette(Color(0xFF4A2A12), Color(0xFFE7A45B), Color(0xFFFFE8A3), 0.45f, Color(0xFF7A4A24), Color(0xFF4E2F17), Color(0xFF6B4A2A)),
        )

        fun forIndex(index: Int): DemoPalette = MOODS[index % MOODS.size]
    }
}
