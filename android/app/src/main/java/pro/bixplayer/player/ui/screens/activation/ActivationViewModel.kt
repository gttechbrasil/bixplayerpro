package pro.bixplayer.player.ui.screens.activation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pro.bixplayer.player.data.repository.ConfigRepository
import pro.bixplayer.player.domain.model.AppConfig
import pro.bixplayer.player.domain.model.ConfigState
import pro.bixplayer.player.domain.model.DeviceStatus

/** What the activation screen shows at any moment. */
data class ActivationUiState(
    val macAddress: String = "",
    val checking: Boolean = false,
    val addingPlaylist: Boolean = false,
    /** Message to show the user; already translated. */
    val notice: String? = null,
    val showPlaylistForm: Boolean = false,
    /** Set when the device became usable and the screen should navigate away. */
    val activated: Boolean = false,
)

/** Errors the screen can raise on its own, without going to the network. */
enum class ActivationError { EMPTY_FIELDS, INVALID_URL }

@HiltViewModel
class ActivationViewModel @Inject constructor(
    private val repository: ConfigRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ActivationUiState())
    val uiState: StateFlow<ActivationUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val config = repository.cached()
            if (config != null) _uiState.value = _uiState.value.copy(macAddress = config.macAddress)
        }
    }

    fun setMacFrom(config: AppConfig) {
        _uiState.value = _uiState.value.copy(macAddress = config.macAddress)
    }

    /** "Já cadastrei — verificar": re-reads the config and decides whether the device is in. */
    fun check(notYetMessage: String) {
        if (_uiState.value.checking) return
        _uiState.value = _uiState.value.copy(checking = true, notice = null)
        viewModelScope.launch {
            val state = repository.refresh()
            _uiState.value = when (state) {
                is ConfigState.Ready -> {
                    val config = state.config
                    val usable = config.status == DeviceStatus.ACTIVE && config.hasPlaylists
                    _uiState.value.copy(
                        checking = false,
                        macAddress = config.macAddress,
                        activated = usable,
                        notice = if (usable) null else notYetMessage,
                    )
                }

                is ConfigState.Failed -> _uiState.value.copy(checking = false, notice = state.message)
                ConfigState.Loading -> _uiState.value.copy(checking = false)
            }
        }
    }

    fun togglePlaylistForm() {
        _uiState.value = _uiState.value.copy(
            showPlaylistForm = !_uiState.value.showPlaylistForm,
            notice = null,
        )
    }

    /**
     * Adds a playlist through the platform API. Validation happens here so the screen stays
     * free of rules; [onError] maps the enum to a localised string.
     */
    fun addPlaylist(
        name: String,
        url: String,
        successMessage: String,
        onError: (ActivationError) -> String,
    ) {
        if (name.isBlank() || url.isBlank()) {
            _uiState.value = _uiState.value.copy(notice = onError(ActivationError.EMPTY_FIELDS))
            return
        }
        if (!isValidUrl(url)) {
            _uiState.value = _uiState.value.copy(notice = onError(ActivationError.INVALID_URL))
            return
        }
        _uiState.value = _uiState.value.copy(addingPlaylist = true, notice = null)
        viewModelScope.launch {
            val result = repository.addPlaylist(name, url)
            val config = (repository.state.value as? ConfigState.Ready)?.config
            _uiState.value = if (result.isSuccess) {
                _uiState.value.copy(
                    addingPlaylist = false,
                    showPlaylistForm = false,
                    notice = successMessage,
                    activated = config?.canWatch == true,
                )
            } else {
                // The API validates the URL server-side and answers in Portuguese; showing its
                // message beats guessing (it may be "device not registered", not a bad URL).
                _uiState.value.copy(
                    addingPlaylist = false,
                    notice = repository.messageFor(result.exceptionOrNull()),
                )
            }
        }
    }

    fun dismissNotice() {
        _uiState.value = _uiState.value.copy(notice = null)
    }

    companion object {
        /** Mirrors the server-side rule: only absolute http(s) URLs are accepted. */
        fun isValidUrl(value: String): Boolean {
            val trimmed = value.trim()
            if (!trimmed.startsWith("http://", true) && !trimmed.startsWith("https://", true)) {
                return false
            }
            val rest = trimmed.substringAfter("://")
            val host = rest.substringBefore("/").substringBefore("?")
            return host.isNotBlank() && host.contains(".")
        }
    }
}
