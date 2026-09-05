package pro.bixplayer.player.ui.screens.boot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import pro.bixplayer.player.BuildConfig
import pro.bixplayer.player.data.repository.ConfigRepository
import pro.bixplayer.player.domain.model.AppConfig
import pro.bixplayer.player.domain.model.ConfigState
import timber.log.Timber

/** Where the splash screen should send the user once the configuration is known. */
sealed interface BootDestination {
    data object Pending : BootDestination

    data class Go(val route: String) : BootDestination

    /** No config and no cache: the screen shows the error with a retry button. */
    data class Error(val message: String) : BootDestination
}

@HiltViewModel
class BootViewModel @Inject constructor(
    private val repository: ConfigRepository,
) : ViewModel() {

    private val _destination = MutableStateFlow<BootDestination>(BootDestination.Pending)
    val destination: StateFlow<BootDestination> = _destination.asStateFlow()

    val configState: StateFlow<ConfigState> = repository.state

    init {
        boot()
    }

    fun boot() {
        _destination.value = BootDestination.Pending
        viewModelScope.launch {
            // Show whatever we knew before touching the network, so a slow link does not
            // leave the user staring at a spinner.
            repository.primeFromCache()
            when (val state = repository.refresh()) {
                is ConfigState.Ready -> _destination.value = BootDestination.Go(routeFor(state.config))
                is ConfigState.Failed -> _destination.value = BootDestination.Error(state.message)
                ConfigState.Loading -> Unit
            }
        }
    }

    private fun routeFor(config: AppConfig): String =
        BootRouting.routeFor(config, BuildConfig.VERSION_NAME).also {
            Timber.d("boot route: %s (status=%s, playlists=%d)", it, config.status, config.playlists.size)
        }
}
