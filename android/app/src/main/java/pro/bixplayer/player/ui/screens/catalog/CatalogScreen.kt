package pro.bixplayer.player.ui.screens.catalog

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import pro.bixplayer.player.R
import pro.bixplayer.player.data.db.ContentKind
import pro.bixplayer.player.ui.components.SearchRow
import pro.bixplayer.player.ui.components.PinGateDialog
import pro.bixplayer.player.ui.components.rememberPinGate
import pro.bixplayer.player.ui.theme.BixFocus
import pro.bixplayer.player.ui.theme.bixFocusable

/**
 * Movies / series catalogue: categories on the left, a paged 6-column grid of covers on the
 * right, search and sort on top. OK opens the detail screen; MENU toggles favourite.
 */
@Composable
fun CatalogScreen(
    onOpen: (CatalogItem) -> Unit,
    viewModel: CatalogViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val items = viewModel.items.collectAsLazyPagingItems()
    val gate = rememberPinGate()

    val gridRequester = remember { FocusRequester() }
    val categoryRequester = remember { FocusRequester() }
    val cellRequesters = remember { mutableStateMapOf<Int, FocusRequester>() }

    var focusedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(items.itemCount, state.categories.size) {
        if (focusedOnce) return@LaunchedEffect
        if (items.itemCount > 0) {
            delay(80)
            runCatching { gridRequester.requestFocus() }
            focusedOnce = true
        } else if (state.categories.isNotEmpty()) {
            delay(80)
            runCatching { categoryRequester.requestFocus() }
            focusedOnce = true
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 32.dp, end = 32.dp, top = 24.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CatalogCategoryColumn(
            state = state,
            firstRequester = categoryRequester,
            onSelect = { category ->
                gate.require(category.locked, R.string.pin_locked_category) { viewModel.selectCategory(category) }
            },
            modifier = Modifier.width(220.dp).fillMaxHeight(),
        )

        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    SearchRow(
                        query = state.query,
                        onQueryChange = viewModel::setQuery,
                        placeholder = stringResource(
                            if (state.kind == ContentKind.SERIES) R.string.catalog_search_series else R.string.catalog_search_movies,
                        ),
                        onDone = { runCatching { gridRequester.requestFocus() } },
                    )
                }
                SortChip(sort = state.sort, onClick = viewModel::cycleSort)
            }
            Spacer(Modifier.height(12.dp))

            if (items.itemCount == 0) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(
                            if (state.selectedKey == CatalogUiState.KEY_FAVORITES && state.query.isBlank()) R.string.catalog_no_favorites
                            else R.string.catalog_empty,
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                val gridState = rememberLazyGridState(
                    initialFirstVisibleItemIndex = (state.focusIndex / COLUMNS * COLUMNS).coerceAtLeast(0),
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(COLUMNS),
                    state = gridState,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRequester(gridRequester)
                        .focusProperties {
                            onEnter = {
                                cellRequesters[state.focusIndex]?.let { runCatching { it.requestFocus() } }
                            }
                        },
                ) {
                    items(
                        count = items.itemCount,
                        key = { index -> items.peek(index)?.id ?: -index.toLong() },
                    ) { index ->
                        val item = items[index] ?: return@items
                        val requester = remember { FocusRequester() }
                        DisposableEffect(index) {
                            cellRequesters[index] = requester
                            onDispose { if (cellRequesters[index] === requester) cellRequesters.remove(index) }
                        }
                        PosterCard(
                            item = item,
                            favorite = item.remoteId in state.favoriteIds,
                            focusRequester = requester,
                            onFocused = { viewModel.onItemFocused(index) },
                            onOpen = { onOpen(item) },
                            onToggleFavorite = { viewModel.toggleFavorite(item) },
                        )
                    }
                }
            }
        }
    }
    PinGateDialog(gate)
    }
}

@Composable
private fun SortChip(sort: CatalogSort, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    Text(
        text = stringResource(
            when (sort) {
                CatalogSort.PROVIDER -> R.string.catalog_sort_provider
                CatalogSort.RECENT -> R.string.catalog_sort_recent
                CatalogSort.AZ -> R.string.catalog_sort_az
            },
        ),
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
            .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}

@Composable
private fun CatalogCategoryColumn(
    state: CatalogUiState,
    firstRequester: FocusRequester,
    onSelect: (CatalogCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    val all = stringResource(R.string.live_all)
    val favorites = stringResource(R.string.live_favorites)
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        itemsIndexed(state.categories, key = { _, c -> c.key }) { index, category ->
            val label = when (category.key) {
                CatalogUiState.KEY_ALL -> all
                CatalogUiState.KEY_FAVORITES -> favorites
                else -> category.name
            }
            CategoryRow(
                label = if (category.locked) "🔒 $label" else label,
                count = category.count,
                selected = category.key == state.selectedKey,
                focusRequester = if (index == 0) firstRequester else null,
                onSelect = { onSelect(category) },
                selectOnFocus = !category.locked,
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
    onSelect: () -> Unit,
    selectOnFocus: Boolean,
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
            .onFocusChanged { if (it.isFocused && selectOnFocus) onSelect() }
            .focusable(interactionSource = interaction)
            .onKeyEvent { event ->
                val select = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                if (select && event.type == KeyEventType.KeyUp) {
                    onSelect(); true
                } else {
                    false
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected || focused) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(text = count.toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 2:3 cover with the title over a gradient at the bottom; a placeholder with initials otherwise. */
@Composable
fun PosterCard(
    item: CatalogItem,
    favorite: Boolean,
    focusRequester: FocusRequester?,
    onFocused: () -> Unit,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)

    Box(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .bixFocusable(focused, scale = BixFocus.SCALE, shape = shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .onFocusChanged { if (it.isFocused) onFocused() }
            .focusable(interactionSource = interaction)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { onOpen(); true }
                    Key.Menu -> { onToggleFavorite(); true }
                    else -> false
                }
            },
    ) {
        var imageFailed by remember(item.posterUrl) { mutableStateOf(item.posterUrl.isNullOrBlank()) }
        if (imageFailed) {
            Text(
                text = item.name.split(' ').take(2).joinToString("") { it.take(1).uppercase() },
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        } else {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.name,
                contentScale = ContentScale.Crop,
                onError = { imageFailed = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                .padding(horizontal = 8.dp, vertical = 8.dp),
        ) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
            )
            item.year?.let {
                Text(text = it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (favorite) {
            Text(
                text = "★",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
            )
        }
    }
}

private const val COLUMNS = 6
