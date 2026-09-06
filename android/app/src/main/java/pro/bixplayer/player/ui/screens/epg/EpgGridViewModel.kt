package pro.bixplayer.player.ui.screens.epg

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pro.bixplayer.player.data.datastore.DeviceStore
import pro.bixplayer.player.data.db.ChannelDao
import pro.bixplayer.player.data.db.ChannelEntity
import pro.bixplayer.player.data.db.EpgDao
import pro.bixplayer.player.data.db.EpgProgramEntity

data class EpgRow(val channel: ChannelEntity, val programmes: List<EpgProgramEntity>)

data class EpgGridUiState(
    val playlistId: Long? = null,
    /** Start of the visible 3-hour window, aligned to the half hour. */
    val windowStart: Long = 0L,
    val windowEnd: Long = 0L,
    val now: Long = System.currentTimeMillis(),
    val rows: List<EpgRow> = emptyList(),
    val loading: Boolean = true,
    val hasMore: Boolean = false,
    /** Row the grid should focus first: the channel the user came from, when known. */
    val initialRow: Int = 0,
)

/**
 * Guide grid: one row per channel that has an EPG id, a 3-hour window of programmes per row.
 * Channels come in pages of [PAGE]; the window moves in 3-hour steps.
 */
@HiltViewModel
class EpgGridViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val store: DeviceStore,
    private val channelDao: ChannelDao,
    private val epgDao: EpgDao,
) : ViewModel() {

    private val focusChannelId: Long = savedState.get<Long>(ARG_CHANNEL_ID) ?: -1L

    private val _uiState = MutableStateFlow(EpgGridUiState(windowStart = alignedNow(), windowEnd = alignedNow() + WINDOW_MS))
    val uiState: StateFlow<EpgGridUiState> = _uiState.asStateFlow()

    private var channels: List<ChannelEntity> = emptyList()
    private var loadedChannels = 0

    init {
        viewModelScope.launch {
            val playlistId = store.currentActivePlaylistId() ?: return@launch
            _uiState.value = _uiState.value.copy(playlistId = playlistId)
            loadMoreChannels()
        }
    }

    fun loadMoreChannels() {
        val playlistId = _uiState.value.playlistId ?: return
        viewModelScope.launch {
            val page = channelDao.withEpg(playlistId, PAGE, loadedChannels)
            loadedChannels += page.size
            channels = channels + page
            val total = channelDao.countWithEpg(playlistId)
            _uiState.value = _uiState.value.copy(hasMore = loadedChannels < total)
            refresh()
        }
    }

    fun shiftWindow(hours: Int) {
        val start = _uiState.value.windowStart + hours * 3_600_000L
        _uiState.value = _uiState.value.copy(windowStart = start, windowEnd = start + WINDOW_MS)
        refresh()
    }

    fun jumpToNow() {
        val start = alignedNow()
        _uiState.value = _uiState.value.copy(windowStart = start, windowEnd = start + WINDOW_MS)
        refresh()
    }

    private fun refresh() {
        val playlistId = _uiState.value.playlistId ?: return
        val state = _uiState.value
        viewModelScope.launch {
            val ids = channels.mapNotNull { it.epgChannelId }.distinct()
            val programmes = ids.chunked(400).flatMap { chunk ->
                epgDao.window(playlistId, chunk, state.windowStart, state.windowEnd)
            }.groupBy { it.channelEpgId }
            val rows = channels.map { EpgRow(it, programmes[it.epgChannelId].orEmpty()) }
            val initialRow = rows.indexOfFirst { it.channel.id == focusChannelId }.coerceAtLeast(0)
            _uiState.value = _uiState.value.copy(
                rows = rows,
                loading = false,
                now = System.currentTimeMillis(),
                initialRow = initialRow,
            )
        }
    }

    companion object {
        const val ARG_CHANNEL_ID = "channelId"
        const val WINDOW_MS = 3 * 3_600_000L
        const val PAGE = 60

        /** Start of the current half hour, so the timeline ticks line up. */
        fun alignedNow(now: Long = System.currentTimeMillis()): Long = now - now % (30 * 60_000L)
    }
}
