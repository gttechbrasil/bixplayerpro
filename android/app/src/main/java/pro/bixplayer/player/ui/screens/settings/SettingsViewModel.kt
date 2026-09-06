package pro.bixplayer.player.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import coil3.SingletonImageLoader
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pro.bixplayer.player.BuildConfig
import pro.bixplayer.player.data.datastore.DeviceStore
import pro.bixplayer.player.data.db.BixDatabase
import pro.bixplayer.player.data.repository.ConfigRepository
import pro.bixplayer.player.data.work.ConfigRefreshWorker
import pro.bixplayer.player.data.work.EpgSyncWorker
import pro.bixplayer.player.domain.model.ConfigState
import pro.bixplayer.player.domain.usecase.PlaylistSyncUseCase
import pro.bixplayer.player.domain.usecase.SyncResult
import pro.bixplayer.player.player.PlayerSession
import pro.bixplayer.player.ui.locale.AppLanguages
import timber.log.Timber

data class SettingsUiState(
    val refreshHours: Long = 6,
    val language: String = AppLanguages.PT_BR,
    /** Effective home layout name (`default` | `grid`). */
    val layout: String = "default",
    val layoutFromPanel: Boolean = true,
    val macAddress: String = "",
    val version: String = BuildConfig.VERSION_NAME,
    val syncing: Boolean = false,
    val busy: Boolean = false,
    val confirmLogout: Boolean = false,
    /** Already translated; the screen picks the string and the VM only stores it. */
    val notice: String? = null,
    /** Set once the local data is wiped; the screen navigates back to the boot flow. */
    val loggedOut: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val store: DeviceStore,
    private val repository: ConfigRepository,
    private val syncUseCase: PlaylistSyncUseCase,
    private val database: BixDatabase,
    private val session: PlayerSession,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        combine(store.refreshHours, store.language, store.macAddress, store.layoutOverride, repository.state) { values ->
            val override = values[3] as String?
            val panel = ((values[4] as? ConfigState.Ready)?.config?.layout ?: pro.bixplayer.player.domain.model.AppLayout.DEFAULT).name.lowercase()
            _uiState.value = _uiState.value.copy(
                refreshHours = values[0] as Long,
                language = values[1] as String,
                macAddress = (values[2] as String?).orEmpty(),
                layout = override ?: panel,
                layoutFromPanel = override == null,
            )
        }.launchIn(viewModelScope)
    }

    /** "Atualizar listas": full resync of the active playlist. */
    fun refreshLists(onDone: (channels: Int) -> String) {
        if (_uiState.value.syncing) return
        viewModelScope.launch {
            val config = (repository.state.value as? ConfigState.Ready)?.config ?: repository.cached()
            val activeId = store.currentActivePlaylistId() ?: config?.playlists?.firstOrNull()?.id
            val playlist = config?.playlists?.firstOrNull { it.id == activeId } ?: return@launch
            _uiState.value = _uiState.value.copy(syncing = true, notice = null)
            val notice = when (val result = syncUseCase.sync(playlist)) {
                is SyncResult.Success -> {
                    EpgSyncWorker.syncNow(context, playlist.id, force = true)
                    onDone(result.channels)
                }
                is SyncResult.Failure -> result.message
            }
            _uiState.value = _uiState.value.copy(syncing = false, notice = notice)
        }
    }

    fun cycleRefreshPeriod() {
        val current = _uiState.value.refreshHours
        val next = REFRESH_OPTIONS.firstOrNull { it > current } ?: REFRESH_OPTIONS.first()
        viewModelScope.launch {
            store.setRefreshHours(next)
            ConfigRefreshWorker.schedule(context, store)
        }
    }

    /** Local layout switch; the panel's choice returns on the next config refresh. */
    fun toggleLayout() {
        val next = if (_uiState.value.layout == "grid") "default" else "grid"
        viewModelScope.launch { store.setLayoutOverride(next) }
    }

    fun cycleLanguage() {
        viewModelScope.launch { store.setLanguage(AppLanguages.next(_uiState.value.language)) }
    }

    /** Drops images and the local copy of the lists; the next visit to the home resyncs. */
    fun clearCache(doneMessage: String) {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true, notice = null)
            session.stop()
            withContext(ioDispatcher) {
                database.clearAllTables()
                val loader = SingletonImageLoader.get(context)
                loader.memoryCache?.clear()
                loader.diskCache?.clear()
            }
            _uiState.value = _uiState.value.copy(busy = false, notice = doneMessage)
        }
    }

    fun askLogout() {
        _uiState.value = _uiState.value.copy(confirmLogout = true, notice = null)
    }

    fun cancelLogout() {
        _uiState.value = _uiState.value.copy(confirmLogout = false)
    }

    /** Wipes DataStore and Room, cancels the background refresh and sends the user to the boot. */
    fun logout() {
        if (_uiState.value.busy) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(busy = true)
            session.stop()
            WorkManager.getInstance(context).cancelUniqueWork(ConfigRefreshWorker.NAME)
            withContext(ioDispatcher) { database.clearAllTables() }
            store.clear()
            Timber.i("logout: local data wiped")
            _uiState.value = _uiState.value.copy(busy = false, confirmLogout = false, loggedOut = true)
        }
    }

    fun dismissNotice() {
        _uiState.value = _uiState.value.copy(notice = null)
    }

    companion object {
        val REFRESH_OPTIONS = listOf(1L, 3L, 6L, 12L, 24L)
    }
}
