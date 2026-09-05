package pro.bixplayer.player.ui.screens.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pro.bixplayer.player.data.datastore.DeviceStore
import pro.bixplayer.player.data.db.PlaylistSyncDao
import pro.bixplayer.player.data.repository.ConfigRepository
import pro.bixplayer.player.domain.model.ConfigState
import pro.bixplayer.player.domain.model.Playlist
import pro.bixplayer.player.domain.usecase.PlaylistSyncUseCase
import pro.bixplayer.player.domain.usecase.SyncResult
import timber.log.Timber

data class PlaylistUiState(
    val playlists: List<Playlist> = emptyList(),
    val activeId: Long? = null,
    val syncing: Boolean = false,
    val notice: String? = null,
    /** Channel count of the active playlist, from the last successful sync. */
    val channelCount: Int = 0,
    val removingId: Long? = null,
)

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val repository: ConfigRepository,
    private val syncUseCase: PlaylistSyncUseCase,
    private val syncDao: PlaylistSyncDao,
    private val store: DeviceStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlaylistUiState())
    val uiState: StateFlow<PlaylistUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        val config = (repository.state.value as? ConfigState.Ready)?.config ?: repository.cached()
        val active = store.currentActivePlaylistId()
        val count = active?.let { syncDao.get(it)?.channelCount } ?: 0
        _uiState.value = _uiState.value.copy(
            playlists = config?.playlists.orEmpty(),
            activeId = active ?: config?.playlists?.firstOrNull()?.id,
            channelCount = count,
        )
    }

    /**
     * Syncs the active playlist when it has never been synced, or when [force] is set
     * ("Atualizar listas" in the settings screen).
     */
    fun syncActive(force: Boolean = false) {
        val state = _uiState.value
        val playlist = state.playlists.firstOrNull { it.id == state.activeId } ?: return
        if (state.syncing) return

        viewModelScope.launch {
            val record = syncDao.get(playlist.id)
            if (!force && record != null && record.channelCount > 0) {
                _uiState.value = _uiState.value.copy(channelCount = record.channelCount)
                return@launch
            }
            _uiState.value = _uiState.value.copy(syncing = true, notice = null)
            when (val result = syncUseCase.sync(playlist)) {
                is SyncResult.Success -> {
                    Timber.i("sync ok: %d canais, %d categorias", result.channels, result.categories)
                    _uiState.value = _uiState.value.copy(
                        syncing = false,
                        channelCount = result.channels,
                        notice = null,
                    )
                }

                is SyncResult.Failure -> _uiState.value = _uiState.value.copy(
                    syncing = false,
                    notice = result.message,
                )
            }
        }
    }

    /** Switching playlist always resyncs: the two lists have nothing in common. */
    fun select(playlist: Playlist) {
        if (playlist.id == _uiState.value.activeId) return
        viewModelScope.launch {
            repository.setActivePlaylist(playlist.id)
            _uiState.value = _uiState.value.copy(activeId = playlist.id, channelCount = 0)
            syncActive(force = true)
        }
    }

    fun remove(playlist: Playlist) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(removingId = playlist.id, notice = null)
            val result = repository.deletePlaylist(playlist.id)
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(
                    removingId = null,
                    notice = repository.messageFor(result.exceptionOrNull()),
                )
                return@launch
            }
            syncDao.delete(playlist.id)
            _uiState.value = _uiState.value.copy(removingId = null)
            reload()
        }
    }

    fun add(name: String, url: String, onInvalid: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(notice = null)
            val result = repository.addPlaylist(name, url)
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(
                    notice = repository.messageFor(result.exceptionOrNull()).ifBlank { onInvalid },
                )
            } else {
                reload()
            }
        }
    }

    fun dismissNotice() {
        _uiState.value = _uiState.value.copy(notice = null)
    }
}
