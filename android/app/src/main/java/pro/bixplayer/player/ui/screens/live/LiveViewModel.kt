package pro.bixplayer.player.ui.screens.live

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pro.bixplayer.player.data.datastore.DeviceStore
import pro.bixplayer.player.data.db.CategoryDao
import pro.bixplayer.player.data.db.ChannelDao
import pro.bixplayer.player.data.db.ChannelEntity
import pro.bixplayer.player.data.db.ContentKind
import pro.bixplayer.player.data.db.FavoriteDao
import pro.bixplayer.player.data.db.FavoriteEntity
import pro.bixplayer.player.player.PlayerSession
import pro.bixplayer.player.player.SessionState

/** Which channels the middle column lists. */
sealed interface ChannelScope {
    data object All : ChannelScope

    data object Favorites : ChannelScope

    data class Category(val remoteId: String, val name: String) : ChannelScope

    data class Search(val query: String) : ChannelScope

    /** Stable key used by the UI to highlight the selected category. */
    val key: String
        get() = when (this) {
            All -> KEY_ALL
            Favorites -> KEY_FAVORITES
            is Category -> "cat:$remoteId"
            is Search -> "search"
        }

    companion object {
        const val KEY_ALL = "all"
        const val KEY_FAVORITES = "fav"
    }
}

data class CategoryItem(
    val scope: ChannelScope,
    val name: String,
    val count: Int,
)

data class LiveUiState(
    val playlistId: Long? = null,
    val categories: List<CategoryItem> = emptyList(),
    val selectedKey: String = ChannelScope.KEY_ALL,
    val favoriteIds: Set<String> = emptySet(),
    val focusedChannel: ChannelEntity? = null,
    val query: String = "",
    val preview: SessionState = SessionState.Idle,
    /** Index of the channel row that should take focus when the list gets it. */
    val channelIndex: Int = 0,
)

/**
 * Live TV: categories on the left, channels in the middle (paged from Room), preview on the
 * right. The category and the focused row are kept in the saved state so coming back from the
 * player lands exactly where the user was.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class LiveViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    store: DeviceStore,
    categoryDao: CategoryDao,
    private val channelDao: ChannelDao,
    private val favoriteDao: FavoriteDao,
    val session: PlayerSession,
) : ViewModel() {

    private val playlistId: StateFlow<Long?> = store.activePlaylistId
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val scope = MutableStateFlow<ChannelScope>(restoreScope())
    private val query = MutableStateFlow(savedState.get<String>(KEY_QUERY).orEmpty())
    private val focusedChannel = MutableStateFlow<ChannelEntity?>(null)
    private val channelIndex = MutableStateFlow(savedState.get<Int>(KEY_INDEX) ?: 0)

    private val favoriteIds: Flow<Set<String>> = playlistId.filterNotNull()
        .flatMapLatest { favoriteDao.observeIds(it) }
        .map { it.toSet() }

    private val categories: Flow<List<CategoryItem>> = playlistId.filterNotNull()
        .flatMapLatest { id -> combine(categoryDao.observeByPlaylist(id), favoriteIds) { cats, favs -> cats to favs } }
        .map { (cats, favs) ->
            buildList {
                add(CategoryItem(ChannelScope.All, "", cats.sumOf { it.channelCount }))
                add(CategoryItem(ChannelScope.Favorites, "", favs.size))
                cats.forEach { add(CategoryItem(ChannelScope.Category(it.remoteId, it.name), it.name, it.channelCount)) }
            }
        }

    val uiState: StateFlow<LiveUiState> = combine(
        playlistId, categories, scope, favoriteIds, focusedChannel, query, session.state, channelIndex,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        LiveUiState(
            playlistId = values[0] as Long?,
            categories = values[1] as List<CategoryItem>,
            selectedKey = (values[2] as ChannelScope).key,
            favoriteIds = values[3] as Set<String>,
            focusedChannel = values[4] as ChannelEntity?,
            query = values[5] as String,
            preview = values[6] as SessionState,
            channelIndex = values[7] as Int,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveUiState())

    /** Paged channels of the current scope. Search wins over the category while it has text. */
    val channels: Flow<PagingData<ChannelEntity>> = combine(playlistId, scope, query) { id, s, q ->
        Triple(id, s, q)
    }.flatMapLatest { (id, s, q) ->
        if (id == null) return@flatMapLatest flowOf(PagingData.empty())
        val effective = if (q.isNotBlank()) ChannelScope.Search(q) else s
        Pager(PagingConfig(pageSize = PAGE_SIZE, prefetchDistance = PAGE_SIZE, enablePlaceholders = false)) {
            when (effective) {
                ChannelScope.All -> channelDao.pagingByCategory(id, null)
                ChannelScope.Favorites -> channelDao.pagingFavorites(id)
                is ChannelScope.Category -> channelDao.pagingByCategory(id, effective.remoteId)
                is ChannelScope.Search -> channelDao.pagingSearch(id, effective.query.trim())
            }
        }.flow
    }.cachedIn(viewModelScope)

    /** Whether the user left towards the player; then the preview must keep playing. */
    private var openingPlayer = false

    /** Row index the screen must scroll to and focus (after zapping inside the player). */
    private val _focusRequests = MutableSharedFlow<Int>(extraBufferCapacity = 1)
    val focusRequests: SharedFlow<Int> = _focusRequests.asSharedFlow()

    init {
        // The preview follows the focused row, with a short delay so fast scrolling does not
        // start one connection per channel.
        viewModelScope.launch {
            focusedChannel
                .debounce(PREVIEW_DEBOUNCE_MS)
                .map { it?.streamUrl }
                .distinctUntilChanged()
                .collect { url -> if (url != null) session.play(url) }
        }
    }

    fun selectCategory(item: CategoryItem) {
        if (item.scope.key == scope.value.key) return
        scope.value = item.scope
        query.value = ""
        setChannelIndex(0)
        savedState[KEY_SCOPE] = item.scope.key
        savedState[KEY_SCOPE_NAME] = (item.scope as? ChannelScope.Category)?.name
    }

    fun setQuery(text: String) {
        query.value = text
        savedState[KEY_QUERY] = text
        if (text.isNotBlank()) setChannelIndex(0)
    }

    fun onChannelFocused(index: Int, channel: ChannelEntity) {
        focusedChannel.value = channel
        setChannelIndex(index)
    }

    fun toggleFavorite(channel: ChannelEntity) {
        viewModelScope.launch {
            if (favoriteDao.isFavorite(channel.playlistId, ContentKind.LIVE, channel.remoteId)) {
                favoriteDao.remove(channel.playlistId, ContentKind.LIVE, channel.remoteId)
            } else {
                favoriteDao.add(FavoriteEntity(channel.playlistId, ContentKind.LIVE, channel.remoteId))
            }
        }
    }

    /** Scope string handed to the player route so zapping stays inside the current list. */
    fun currentScopeArg(): String {
        val q = query.value
        if (q.isNotBlank()) return ChannelScope.KEY_ALL
        return scope.value.key
    }

    fun onOpenPlayer() {
        openingPlayer = true
    }

    fun onScreenResumed() {
        openingPlayer = false
        viewModelScope.launch {
            followPlayerChannel()
            focusedChannel.value?.let { session.play(it.streamUrl) }
        }
    }

    /**
     * Zapping inside the player changes the channel without touching this list; when the user
     * comes back, the row of the channel that is actually playing takes the focus.
     */
    private suspend fun followPlayerChannel() {
        val playingId = session.currentChannelId.value ?: return
        val current = focusedChannel.value
        if (current?.id == playingId || query.value.isNotBlank()) return
        val channel = channelDao.byId(playingId) ?: return
        val s = scope.value
        val category = (s as? ChannelScope.Category)?.remoteId
        if (category != null && channel.categoryRemoteId != category) return
        val favoritesOnly = if (s is ChannelScope.Favorites) 1 else 0
        val index = channelDao.indexInScope(channel.playlistId, category, favoritesOnly, channel.position)
        focusedChannel.value = channel
        setChannelIndex(index)
        _focusRequests.tryEmit(index)
    }

    /** Leaving to anywhere but the player stops the preview so the decoder is not left running. */
    fun onScreenLeft() {
        if (!openingPlayer) session.stop()
    }

    private fun setChannelIndex(index: Int) {
        channelIndex.value = index
        savedState[KEY_INDEX] = index
    }

    private fun restoreScope(): ChannelScope {
        val key = savedState.get<String>(KEY_SCOPE) ?: return ChannelScope.All
        return when {
            key == ChannelScope.KEY_FAVORITES -> ChannelScope.Favorites
            key.startsWith("cat:") -> ChannelScope.Category(
                remoteId = key.removePrefix("cat:"),
                name = savedState.get<String>(KEY_SCOPE_NAME).orEmpty(),
            )
            else -> ChannelScope.All
        }
    }

    companion object {
        const val PAGE_SIZE = 60
        const val PREVIEW_DEBOUNCE_MS = 700L
        private const val KEY_SCOPE = "scope"
        private const val KEY_SCOPE_NAME = "scope_name"
        private const val KEY_QUERY = "query"
        private const val KEY_INDEX = "channel_index"
    }
}
