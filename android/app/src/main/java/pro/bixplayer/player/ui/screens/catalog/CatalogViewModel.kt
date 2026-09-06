package pro.bixplayer.player.ui.screens.catalog

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.map
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pro.bixplayer.player.data.datastore.DeviceStore
import pro.bixplayer.player.data.db.CategoryDao
import pro.bixplayer.player.data.db.CategoryRuleDao
import pro.bixplayer.player.data.db.ContentKind
import pro.bixplayer.player.data.db.FavoriteDao
import pro.bixplayer.player.data.db.FavoriteEntity
import pro.bixplayer.player.data.db.MovieDao
import pro.bixplayer.player.data.db.SeriesDao

/** One cover in the grid; movies and series share the screen, so they share this shape. */
data class CatalogItem(
    val id: Long,
    val remoteId: String,
    val name: String,
    val posterUrl: String?,
    val year: String?,
    val categoryRemoteId: String?,
)

data class CatalogCategory(
    /** `all`, `fav` or `cat:<remoteId>`. */
    val key: String,
    val name: String,
    val count: Int,
    val remoteId: String? = null,
    val locked: Boolean = false,
)

enum class CatalogSort { PROVIDER, RECENT, AZ }

data class CatalogUiState(
    val kind: String = ContentKind.MOVIE,
    val playlistId: Long? = null,
    val categories: List<CatalogCategory> = emptyList(),
    val selectedKey: String = KEY_ALL,
    val query: String = "",
    val sort: CatalogSort = CatalogSort.PROVIDER,
    val favoriteIds: Set<String> = emptySet(),
    /** Index of the grid cell that should take focus when the grid gets it. */
    val focusIndex: Int = 0,
) {
    companion object {
        const val KEY_ALL = "all"
        const val KEY_FAVORITES = "fav"
    }
}

/**
 * Movies and series catalogue: categories on the left, a paged grid of covers on the right.
 * The kind comes from the route so the same screen serves both; only the detail screen differs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val savedState: SavedStateHandle,
    store: DeviceStore,
    categoryDao: CategoryDao,
    ruleDao: CategoryRuleDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val favoriteDao: FavoriteDao,
) : ViewModel() {

    val kind: String = savedState.get<String>(ARG_KIND) ?: ContentKind.MOVIE

    private val playlistId: StateFlow<Long?> = store.activePlaylistId
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val selectedKey = MutableStateFlow(savedState.get<String>(KEY_SELECTED) ?: CatalogUiState.KEY_ALL)
    private val query = MutableStateFlow(savedState.get<String>(KEY_QUERY).orEmpty())
    private val sort = MutableStateFlow(CatalogSort.entries[savedState.get<Int>(KEY_SORT) ?: 0])
    private val focusIndex = MutableStateFlow(savedState.get<Int>(KEY_FOCUS) ?: 0)

    private val favoriteIds: Flow<Set<String>> = playlistId.filterNotNull()
        .flatMapLatest { favoriteDao.observeIds(it, kind) }
        .map { it.toSet() }

    private val categories: Flow<List<CatalogCategory>> = playlistId.filterNotNull()
        .flatMapLatest { id ->
            combine(categoryDao.observeByPlaylist(id, kind), ruleDao.observeByPlaylist(id), favoriteIds) { cats, rules, favs ->
                val byId = rules.filter { it.kind == kind }.associateBy { it.remoteId }
                buildList {
                    add(CatalogCategory(CatalogUiState.KEY_ALL, "", cats.sumOf { it.channelCount }))
                    add(CatalogCategory(CatalogUiState.KEY_FAVORITES, "", favs.size))
                    cats.filter { byId[it.remoteId]?.hidden != true }.forEach {
                        add(CatalogCategory("cat:${it.remoteId}", it.name, it.channelCount, it.remoteId, byId[it.remoteId]?.locked == true))
                    }
                }
            }
        }

    val uiState: StateFlow<CatalogUiState> = combine(
        playlistId, categories, selectedKey, query, sort, favoriteIds, focusIndex,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        CatalogUiState(
            kind = kind,
            playlistId = values[0] as Long?,
            categories = values[1] as List<CatalogCategory>,
            selectedKey = values[2] as String,
            query = values[3] as String,
            sort = values[4] as CatalogSort,
            favoriteIds = values[5] as Set<String>,
            focusIndex = values[6] as Int,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CatalogUiState(kind = kind))

    val items: Flow<PagingData<CatalogItem>> = combine(playlistId, selectedKey, query, sort) { id, key, q, s ->
        Query(id, key, q.trim(), s)
    }.flatMapLatest { (id, key, q, s) ->
        if (id == null) return@flatMapLatest flowOf(PagingData.empty())
        val category = key.removePrefix("cat:").takeIf { key.startsWith("cat:") }
        val favorites = if (key == CatalogUiState.KEY_FAVORITES) 1 else 0
        Pager(PagingConfig(pageSize = PAGE_SIZE, prefetchDistance = PAGE_SIZE, enablePlaceholders = false)) {
            if (kind == ContentKind.SERIES) seriesDao.paging(id, category, q, favorites, s.ordinal)
            else movieDao.paging(id, category, q, favorites, s.ordinal)
        }.flow.map { paging ->
            if (kind == ContentKind.SERIES) {
                @Suppress("UNCHECKED_CAST")
                (paging as PagingData<pro.bixplayer.player.data.db.SeriesEntity>).map {
                    CatalogItem(it.id, it.remoteId, it.name, it.coverUrl, it.year, it.categoryRemoteId)
                }
            } else {
                @Suppress("UNCHECKED_CAST")
                (paging as PagingData<pro.bixplayer.player.data.db.MovieEntity>).map {
                    CatalogItem(it.id, it.remoteId, it.name, it.posterUrl, it.year, it.categoryRemoteId)
                }
            }
        }
    }.cachedIn(viewModelScope)

    private data class Query(val playlistId: Long?, val key: String, val query: String, val sort: CatalogSort)

    fun selectCategory(category: CatalogCategory) {
        if (category.key == selectedKey.value) return
        selectedKey.value = category.key
        savedState[KEY_SELECTED] = category.key
        setFocusIndex(0)
    }

    fun setQuery(text: String) {
        query.value = text
        savedState[KEY_QUERY] = text
        if (text.isNotBlank()) setFocusIndex(0)
    }

    fun cycleSort() {
        val next = CatalogSort.entries[(sort.value.ordinal + 1) % CatalogSort.entries.size]
        sort.value = next
        savedState[KEY_SORT] = next.ordinal
        setFocusIndex(0)
    }

    fun onItemFocused(index: Int) = setFocusIndex(index)

    fun toggleFavorite(item: CatalogItem) {
        val id = playlistId.value ?: return
        viewModelScope.launch {
            if (favoriteDao.isFavorite(id, kind, item.remoteId)) favoriteDao.remove(id, kind, item.remoteId)
            else favoriteDao.add(FavoriteEntity(id, kind, item.remoteId))
        }
    }

    private fun setFocusIndex(index: Int) {
        focusIndex.value = index
        savedState[KEY_FOCUS] = index
    }

    companion object {
        const val ARG_KIND = "kind"
        const val PAGE_SIZE = 60
        private const val KEY_SELECTED = "selected"
        private const val KEY_QUERY = "query"
        private const val KEY_SORT = "sort"
        private const val KEY_FOCUS = "focus"
    }
}
