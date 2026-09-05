package pro.bixplayer.player.ui.screens.live

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import pro.bixplayer.player.R
import pro.bixplayer.player.data.db.ChannelEntity
import pro.bixplayer.player.player.SessionState
import pro.bixplayer.player.ui.components.BixTextField
import pro.bixplayer.player.ui.components.VideoSurface
import pro.bixplayer.player.ui.theme.BixFocus
import pro.bixplayer.player.ui.theme.BixScrim
import pro.bixplayer.player.ui.theme.bixFocusable

/**
 * Live TV, layout `default`: categories · channels · preview. The focused channel plays in the
 * preview panel through the app's single player session; OK opens it full screen and MENU
 * toggles it as a favourite.
 */
@Composable
fun LiveScreen(
    onOpenChannel: (channel: ChannelEntity, scopeArg: String) -> Unit,
    onBack: () -> Unit,
    viewModel: LiveViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val channels = viewModel.channels.collectAsLazyPagingItems()

    val channelColumnRequester = remember { FocusRequester() }
    val categoryRequester = remember { FocusRequester() }
    // One requester per composed channel row; used to land focus on the remembered row.
    val rowRequesters = remember { mutableStateMapOf<Int, FocusRequester>() }

    DisposableEffect(Unit) {
        viewModel.onScreenResumed()
        onDispose { viewModel.onScreenLeft() }
    }

    // First focus: the remembered channel row when the list has content, else the categories.
    var focusedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(channels.itemCount, state.categories.size) {
        if (focusedOnce) return@LaunchedEffect
        if (channels.itemCount > 0) {
            delay(80)
            runCatching { channelColumnRequester.requestFocus() }
            focusedOnce = true
        } else if (state.categories.isNotEmpty()) {
            delay(80)
            runCatching { categoryRequester.requestFocus() }
            focusedOnce = true
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CategoryColumn(
            state = state,
            firstRequester = categoryRequester,
            onSelect = viewModel::selectCategory,
            modifier = Modifier.width(200.dp).fillMaxHeight(),
        )

        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            SearchRow(
                query = state.query,
                onQueryChange = viewModel::setQuery,
                onDone = { runCatching { channelColumnRequester.requestFocus() } },
            )
            Spacer(Modifier.height(12.dp))

            if (channels.itemCount == 0) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (state.selectedKey == ChannelScope.KEY_FAVORITES && state.query.isBlank()) {
                            stringResource(R.string.live_no_favorites)
                        } else {
                            stringResource(R.string.live_no_channels)
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val listState = rememberLazyListState(
                    initialFirstVisibleItemIndex = state.channelIndex.coerceAtLeast(0),
                )
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(channelColumnRequester)
                        .focusProperties {
                            onEnter = {
                                rowRequesters[state.channelIndex]?.let { requester ->
                                    runCatching { requester.requestFocus() }
                                }
                            }
                        },
                ) {
                    items(
                        count = channels.itemCount,
                        key = { index -> channels.peek(index)?.id ?: -index.toLong() },
                    ) { index ->
                        val channel = channels[index] ?: return@items
                        val requester = remember { FocusRequester() }
                        DisposableEffect(index) {
                            rowRequesters[index] = requester
                            onDispose { if (rowRequesters[index] === requester) rowRequesters.remove(index) }
                        }
                        ChannelRow(
                            channel = channel,
                            favorite = channel.remoteId in state.favoriteIds,
                            focusRequester = requester,
                            onFocused = { viewModel.onChannelFocused(index, channel) },
                            onOpen = {
                                viewModel.onOpenPlayer()
                                onOpenChannel(channel, viewModel.currentScopeArg())
                            },
                            onToggleFavorite = { viewModel.toggleFavorite(channel) },
                        )
                    }
                }
            }
        }

        PreviewPanel(
            state = state,
            modifier = Modifier.width(340.dp).fillMaxHeight(),
            viewModel = viewModel,
        )
    }
}

/**
 * Search in two states: a focusable row that only opens the text field (and therefore the
 * keyboard) on OK. Landing on a plain text field with the D-pad would pop the keyboard every
 * time the user overshoots the top of the list.
 */
@Composable
private fun SearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    var editing by remember { mutableStateOf(query.isNotEmpty()) }
    val fieldRequester = remember { FocusRequester() }
    LaunchedEffect(editing) {
        if (editing) {
            delay(50)
            runCatching { fieldRequester.requestFocus() }
        }
    }

    if (editing) {
        BixTextField(
            value = query,
            onValueChange = onQueryChange,
            label = stringResource(R.string.live_search),
            imeAction = ImeAction.Done,
            focusRequester = fieldRequester,
            onImeAction = {
                if (query.isEmpty()) editing = false
                onDone()
            },
        )
    } else {
        val interaction = remember { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        val shape = RoundedCornerShape(10.dp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .bixFocusable(focused, scale = 1f, shape = shape)
                .background(MaterialTheme.colorScheme.surface, shape)
                .focusable(interactionSource = interaction)
                .onKeyEvent { event ->
                    val select = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                    if (select && event.type == KeyEventType.KeyUp) {
                        editing = true; true
                    } else {
                        false
                    }
                }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                text = "🔍  " + query.ifEmpty { stringResource(R.string.live_search) },
                style = MaterialTheme.typography.bodyLarge,
                color = if (query.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CategoryColumn(
    state: LiveUiState,
    firstRequester: FocusRequester,
    onSelect: (CategoryItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val all = stringResource(R.string.live_all)
    val favorites = stringResource(R.string.live_favorites)

    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        itemsIndexed(state.categories, key = { _, item -> item.scope.key }) { index, item ->
            val label = when (item.scope) {
                ChannelScope.All -> all
                ChannelScope.Favorites -> favorites
                else -> item.name
            }
            CategoryRow(
                label = label,
                count = item.count,
                selected = item.scope.key == state.selectedKey,
                focusRequester = if (index == 0) firstRequester else null,
                onFocused = { onSelect(item) },
            )
        }
    }
}

@Composable
private fun CategoryRow(
    label: String,
    count: Int,
    selected: Boolean,
    focusRequester: FocusRequester?,
    onFocused: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    val container = when {
        focused -> MaterialTheme.colorScheme.surfaceVariant
        selected -> MaterialTheme.colorScheme.surface
        else -> Color.Transparent
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .bixFocusable(focused, scale = 1f, shape = shape)
            .background(container, shape)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .focusable(interactionSource = interaction)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected || focused) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChannelRow(
    channel: ChannelEntity,
    favorite: Boolean,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .bixFocusable(focused, scale = BixFocus.SCALE_SMALL, shape = shape)
            .background(
                if (focused) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                shape,
            )
            .onFocusChanged { if (it.isFocused) onFocused() }
            .focusable(interactionSource = interaction)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        onOpen(); true
                    }
                    Key.Menu -> {
                        onToggleFavorite(); true
                    }
                    else -> false
                }
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = channel.number.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(44.dp),
        )
        ChannelLogo(url = channel.logoUrl, name = channel.name, size = 40.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            text = channel.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (favorite) {
            Text(
                text = "★",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ChannelLogo(url: String?, name: String, size: Dp) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNullOrBlank()) {
            Text(
                text = name.take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(4.dp),
            )
        }
    }
}

@Composable
private fun PreviewPanel(
    state: LiveUiState,
    viewModel: LiveViewModel,
    modifier: Modifier = Modifier,
) {
    val channel = state.focusedChannel
    val categoryName = state.categories
        .firstOrNull { (it.scope as? ChannelScope.Category)?.remoteId == channel?.categoryRemoteId }
        ?.name

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            VideoSurface(player = viewModel.session.player, modifier = Modifier.fillMaxSize())
            when (val preview = state.preview) {
                SessionState.Loading -> CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                is SessionState.Retrying -> PreviewNotice(
                    stringResource(R.string.player_retrying, preview.attempt, preview.max),
                )
                is SessionState.Failed -> PreviewNotice(preview.message)
                else -> Unit
            }
        }

        Spacer(Modifier.height(16.dp))
        if (channel != null) {
            Text(
                text = "${channel.number}  ${channel.name}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!categoryName.isNullOrBlank()) {
                Text(
                    text = categoryName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            // EPG slot: the M4 fills it with the current programme.
            Text(
                text = stringResource(R.string.live_epg_slot),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(R.string.live_hints),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PreviewNotice(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .background(BixScrim, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
