package pro.bixplayer.player.ui.screens.epg

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import pro.bixplayer.player.R
import pro.bixplayer.player.data.db.ChannelEntity
import pro.bixplayer.player.data.db.EpgProgramEntity
import pro.bixplayer.player.ui.theme.BixFocus
import pro.bixplayer.player.ui.theme.bixFocusable

/**
 * Programme guide: channels down the left, a 3-hour timeline across. Each programme is a
 * focusable cell sized by its duration; OK plays the channel. The header chips move the
 * window and jump back to now; a line marks the current time.
 */
@Composable
fun EpgGridScreen(
    onPlayChannel: (ChannelEntity) -> Unit,
    viewModel: EpgGridViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val firstRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    LaunchedEffect(state.loading, state.initialRow) {
        if (!state.loading && state.rows.isNotEmpty()) {
            listState.scrollToItem(state.initialRow)
            delay(80)
            runCatching { firstRequester.requestFocus() }
        }
    }

    val zone = remember { ZoneId.systemDefault() }
    val hourFormat = remember { DateTimeFormatter.ofPattern("HH:mm") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 32.dp, vertical = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.epg_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.weight(1f),
            )
            HeaderChip(text = "◀ 3h", onClick = { viewModel.shiftWindow(-3) })
            HeaderChip(text = stringResource(R.string.epg_now), onClick = viewModel::jumpToNow)
            HeaderChip(text = "3h ▶", onClick = { viewModel.shiftWindow(3) })
        }
        Spacer(Modifier.height(12.dp))

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val timelineWidth = maxWidth - CHANNEL_COLUMN - 8.dp
            val dpPerMs = timelineWidth.value / EpgGridViewModel.WINDOW_MS.toFloat()

            Column {
                // Half-hour ticks
                Row(modifier = Modifier.fillMaxWidth().height(28.dp)) {
                    Spacer(Modifier.width(CHANNEL_COLUMN + 8.dp))
                    Box(modifier = Modifier.width(timelineWidth).fillMaxHeight()) {
                        var t = state.windowStart
                        while (t < state.windowEnd) {
                            val x = ((t - state.windowStart) * dpPerMs).dp
                            Text(
                                text = Instant.ofEpochMilli(t).atZone(zone).format(hourFormat),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.offset(x = x),
                            )
                            t += 30 * 60_000L
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        state.loading -> Text(
                            text = stringResource(R.string.loading),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center),
                        )
                        state.rows.isEmpty() -> Text(
                            text = stringResource(R.string.epg_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.align(Alignment.Center),
                        )
                        else -> LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            itemsIndexed(state.rows, key = { _, row -> row.channel.id }) { index, row ->
                                EpgChannelRow(
                                    row = row,
                                    windowStart = state.windowStart,
                                    windowEnd = state.windowEnd,
                                    now = state.now,
                                    dpPerMs = dpPerMs,
                                    timelineWidth = timelineWidth,
                                    firstRequester = if (index == state.initialRow) firstRequester else null,
                                    onPlay = { onPlayChannel(row.channel) },
                                )
                                if (index == state.rows.lastIndex && state.hasMore) {
                                    LaunchedEffect(index) { viewModel.loadMoreChannels() }
                                }
                            }
                        }
                    }

                    // "Now" marker
                    if (state.now in state.windowStart..state.windowEnd && state.rows.isNotEmpty()) {
                        val x = CHANNEL_COLUMN + 8.dp + ((state.now - state.windowStart) * dpPerMs).dp
                        Box(
                            modifier = Modifier
                                .offset(x = x)
                                .width(2.dp)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderChip(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .bixFocusable(focused, scale = 1f, shape = shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .focusable(interactionSource = interaction)
            .onKeyEvent { event ->
                val select = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                if (select && event.type == KeyEventType.KeyUp) {
                    onClick(); true
                } else {
                    false
                }
            }
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

@Composable
private fun EpgChannelRow(
    row: EpgRow,
    windowStart: Long,
    windowEnd: Long,
    now: Long,
    dpPerMs: Float,
    timelineWidth: Dp,
    firstRequester: FocusRequester?,
    onPlay: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth().height(ROW_HEIGHT)) {
        Text(
            text = "${row.channel.number}  ${row.channel.name}",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .width(CHANNEL_COLUMN)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
        Spacer(Modifier.width(8.dp))
        Box(modifier = Modifier.width(timelineWidth).fillMaxHeight()) {
            if (row.programmes.isEmpty()) {
                ProgrammeCell(
                    title = stringResource(R.string.epg_no_data),
                    startX = 0.dp,
                    width = timelineWidth,
                    current = false,
                    focusRequester = firstRequester,
                    onPlay = onPlay,
                )
            } else {
                row.programmes.forEachIndexed { index, programme ->
                    val start = programme.startAt.coerceAtLeast(windowStart)
                    val end = programme.endAt.coerceAtMost(windowEnd)
                    if (end <= start) return@forEachIndexed
                    ProgrammeCell(
                        title = programme.title,
                        startX = ((start - windowStart) * dpPerMs).dp,
                        width = ((end - start) * dpPerMs).dp - 3.dp,
                        current = now in programme.startAt until programme.endAt,
                        focusRequester = if (index == 0) firstRequester else null,
                        onPlay = onPlay,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgrammeCell(
    title: String,
    startX: Dp,
    width: Dp,
    current: Boolean,
    focusRequester: FocusRequester?,
    onPlay: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .offset(x = startX)
            .width(width.coerceAtLeast(24.dp))
            .fillMaxHeight()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .bixFocusable(focused, scale = 1f, shape = shape)
            .clip(shape)
            .background(
                when {
                    focused -> MaterialTheme.colorScheme.surfaceVariant
                    current -> MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                    else -> MaterialTheme.colorScheme.surface
                },
            )
            .focusable(interactionSource = interaction)
            .onKeyEvent { event ->
                val select = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                if (select && event.type == KeyEventType.KeyUp) {
                    onPlay(); true
                } else {
                    false
                }
            }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (current || focused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val CHANNEL_COLUMN = 220.dp
private val ROW_HEIGHT = 64.dp

