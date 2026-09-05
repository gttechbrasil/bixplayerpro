package pro.bixplayer.player.ui.screens.player

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import pro.bixplayer.player.R
import pro.bixplayer.player.data.db.ChannelEntity
import pro.bixplayer.player.player.SessionState
import pro.bixplayer.player.player.TrackOption
import pro.bixplayer.player.ui.components.BixButton
import pro.bixplayer.player.ui.components.VideoSurface
import pro.bixplayer.player.ui.theme.BixFocus
import pro.bixplayer.player.ui.theme.BixScrim
import pro.bixplayer.player.ui.theme.bixFocusable

/**
 * Full-screen player. All input is the D-pad: OK toggles the info overlay, ↑/↓ zap inside the
 * list the user came from, ←/→ open the quick channel list, MENU opens audio/subtitle tracks,
 * digits tune by number and BACK closes whatever is open before leaving the screen.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    onExit: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val notFound = stringResource(R.string.player_channel_not_found)
    val rootRequester = remember { FocusRequester() }

    // The screen must stay on while a channel plays, and the decoder must not run in the
    // background: release on stop, resume with the same channel on start.
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        view.keepScreenOn = true
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.onBackground()
                Lifecycle.Event.ON_START -> viewModel.onForeground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            view.keepScreenOn = false
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onExit()
        }
    }

    // BACK closes whatever is open, then the overlay, and only then leaves the screen.
    BackHandler {
        when {
            state.quickListVisible -> viewModel.closeQuickList()
            state.tracksVisible -> viewModel.closeTracks()
            state.overlayVisible && state.playback is SessionState.Playing -> viewModel.hideOverlay()
            else -> onExit()
        }
    }

    val anyPanel = state.quickListVisible || state.tracksVisible || state.playback is SessionState.Failed
    LaunchedEffect(anyPanel) {
        if (!anyPanel) {
            delay(50)
            runCatching { rootRequester.requestFocus() }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onPreviewKeyEvent false
                // Panels handle their own keys; the root only acts when nothing is open.
                if (state.quickListVisible) return@onPreviewKeyEvent false
                if (state.tracksVisible) {
                    return@onPreviewKeyEvent if (event.key == Key.Menu) {
                        viewModel.closeTracks(); true
                    } else {
                        false
                    }
                }
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (state.playback is SessionState.Failed) false else { viewModel.toggleOverlay(); true }
                    }
                    Key.DirectionUp, Key.ChannelUp -> { viewModel.previous(); true }
                    Key.DirectionDown, Key.ChannelDown -> { viewModel.next(); true }
                    Key.DirectionLeft, Key.DirectionRight -> { viewModel.openQuickList(); true }
                    Key.Menu, Key.Captions -> { viewModel.openTracks(); true }
                    else -> {
                        val ch = event.utf16CodePoint.toChar()
                        if (ch in '0'..'9') {
                            viewModel.typeDigit(ch) { number -> notFound.format(number) }
                            true
                        } else {
                            false
                        }
                    }
                }
            },
    ) {
        VideoSurface(
            player = viewModel.session.player,
            modifier = Modifier.fillMaxSize(),
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT,
        )

        when (val playback = state.playback) {
            SessionState.Loading -> CircularProgressIndicator(
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.Center),
            )
            is SessionState.Retrying -> Text(
                text = stringResource(R.string.player_retrying, playback.attempt, playback.max),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(BixScrim, RoundedCornerShape(10.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )
            is SessionState.Failed -> ErrorPanel(
                message = playback.message,
                onRetry = viewModel::retry,
                onExit = onExit,
                modifier = Modifier.align(Alignment.Center),
            )
            else -> Unit
        }

        if (state.typedNumber.isNotEmpty()) {
            Text(
                text = stringResource(R.string.player_typing_channel, state.typedNumber),
                style = MaterialTheme.typography.displayMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(40.dp)
                    .background(BixScrim, RoundedCornerShape(12.dp))
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }

        state.notice?.let { notice ->
            Text(
                text = notice,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(40.dp)
                    .background(BixScrim, RoundedCornerShape(10.dp))
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }

        AnimatedVisibility(
            visible = state.overlayVisible && !state.quickListVisible && !state.tracksVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            InfoOverlay(state = state)
        }

        AnimatedVisibility(
            visible = state.quickListVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            QuickList(
                channels = state.quickList,
                currentId = state.channel?.id,
                onSelect = viewModel::select,
            )
        }

        AnimatedVisibility(
            visible = state.tracksVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            TracksPanel(
                audio = state.audioTracks,
                subtitles = state.subtitleTracks,
                onSelect = viewModel::selectTrack,
                onSubtitlesOff = viewModel::disableSubtitles,
            )
        }
    }
}

@Composable
private fun InfoOverlay(state: PlayerUiState) {
    var now by remember { mutableStateOf(LocalTime.now()) }
    LaunchedEffect(state.overlayVisible) {
        while (true) {
            now = LocalTime.now()
            delay(10_000)
        }
    }
    val channel = state.channel

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
            .padding(horizontal = 48.dp, vertical = 32.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = channel?.number?.toString().orEmpty(),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel?.name.orEmpty(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        state.categoryName?.takeIf { it.isNotBlank() },
                        stringResource(R.string.live_epg_slot),
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = now.format(DateTimeFormatter.ofPattern("HH:mm")),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.player_hints),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ErrorPanel(
    message: String,
    onRetry: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val retryRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(50)
        runCatching { retryRequester.requestFocus() }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .background(BixScrim, RoundedCornerShape(16.dp))
            .padding(32.dp),
    ) {
        Text(
            text = stringResource(R.string.player_error),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BixButton(text = stringResource(R.string.retry), onClick = onRetry, focusRequester = retryRequester)
            BixButton(text = stringResource(R.string.close), primary = false, onClick = onExit)
        }
    }
}

@Composable
private fun QuickList(
    channels: List<ChannelEntity>,
    currentId: Long?,
    onSelect: (ChannelEntity) -> Unit,
) {
    val currentIndex = channels.indexOfFirst { it.id == currentId }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentIndex - 3).coerceAtLeast(0))
    val currentRequester = remember { FocusRequester() }
    LaunchedEffect(channels) {
        delay(60)
        runCatching { currentRequester.requestFocus() }
    }

    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxHeight()
            .width(420.dp)
            .background(Color(0xF0101524))
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        itemsIndexed(channels, key = { _, c -> c.id }) { index, channel ->
            QuickRow(
                channel = channel,
                current = channel.id == currentId,
                focusRequester = if (index == currentIndex) currentRequester else null,
                onSelect = { onSelect(channel) },
            )
        }
    }
}

@Composable
private fun QuickRow(
    channel: ChannelEntity,
    current: Boolean,
    focusRequester: FocusRequester?,
    onSelect: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(8.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .bixFocusable(focused, scale = 1f, shape = shape)
            .background(if (focused) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent, shape)
            .focusable(interactionSource = interaction)
            .onPreviewKeyEvent { event ->
                val select = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                if (select && event.type == KeyEventType.KeyUp) {
                    onSelect(); true
                } else {
                    false
                }
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = channel.number.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp),
        )
        Text(
            text = channel.name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (current) MaterialTheme.colorScheme.primary else Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TracksPanel(
    audio: List<TrackOption>,
    subtitles: List<TrackOption>,
    onSelect: (TrackOption) -> Unit,
    onSubtitlesOff: () -> Unit,
) {
    val firstRequester = remember { FocusRequester() }
    LaunchedEffect(audio, subtitles) {
        delay(60)
        runCatching { firstRequester.requestFocus() }
    }
    Column(
        modifier = Modifier
            .width(480.dp)
            .background(Color(0xF0101524), RoundedCornerShape(16.dp))
            .padding(24.dp),
    ) {
        Text(
            text = stringResource(R.string.player_tracks),
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
        )
        Spacer(Modifier.height(16.dp))
        if (audio.isEmpty() && subtitles.isEmpty()) {
            Text(
                text = stringResource(R.string.player_no_tracks),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (audio.isNotEmpty()) {
            SectionLabel(stringResource(R.string.player_audio_track))
            audio.forEachIndexed { index, option ->
                TrackRow(
                    label = option.label,
                    selected = option.selected,
                    focusRequester = if (index == 0) firstRequester else null,
                    onSelect = { onSelect(option) },
                )
            }
        }
        if (subtitles.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SectionLabel(stringResource(R.string.player_subtitle_track))
            TrackRow(
                label = stringResource(R.string.player_subtitle_off),
                selected = subtitles.none { it.selected },
                focusRequester = if (audio.isEmpty()) firstRequester else null,
                onSelect = onSubtitlesOff,
            )
            subtitles.forEach { option ->
                TrackRow(label = option.label, selected = option.selected, focusRequester = null, onSelect = { onSelect(option) })
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun TrackRow(
    label: String,
    selected: Boolean,
    focusRequester: FocusRequester?,
    onSelect: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(8.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .bixFocusable(focused, scale = BixFocus.SCALE_SMALL, shape = shape)
            .background(if (focused) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent, shape)
            .focusable(interactionSource = interaction)
            .onPreviewKeyEvent { event ->
                val select = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                if (select && event.type == KeyEventType.KeyUp) {
                    onSelect(); true
                } else {
                    false
                }
            }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = if (selected) "●" else "○",
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = Color.White)
    }
}
