package pro.bixplayer.player.domain.parental

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import pro.bixplayer.player.data.datastore.DeviceStore
import pro.bixplayer.player.data.repository.ConfigRepository
import pro.bixplayer.player.domain.model.ConfigState

/**
 * Parental control rules and the session unlock.
 *
 * The PIN comes from the panel (`pin` in the device config, default `0000`) unless the user
 * changed it locally; the local one wins because there is no endpoint to push it back in v1.
 * Once the right PIN is typed, everything stays unlocked until the app process ends.
 */
@Singleton
class ParentalGate @Inject constructor(
    private val store: DeviceStore,
    private val repository: ConfigRepository,
) {
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    /** Effective PIN: the local override, else the panel's, else the default. */
    val pin: Flow<String> = combine(store.pin, repository.state) { local, state ->
        local ?: (state as? ConfigState.Ready)?.config?.parentalPin ?: DEFAULT_PIN
    }

    suspend fun currentPin(): String =
        store.currentPin() ?: (repository.state.value as? ConfigState.Ready)?.config?.parentalPin
            ?: repository.cached()?.parentalPin ?: DEFAULT_PIN

    /** Checks [attempt]; a match unlocks the session. */
    suspend fun tryUnlock(attempt: String): Boolean {
        val ok = attempt == currentPin()
        if (ok) _unlocked.value = true
        return ok
    }

    suspend fun changePin(current: String, new: String): Boolean {
        if (current != currentPin() || !isValidPin(new)) return false
        store.setPin(new)
        _unlocked.value = true
        return true
    }

    fun lock() {
        _unlocked.value = false
    }

    /** Whether the active playlist is marked protected on the panel. */
    fun playlistProtected(playlistId: Long?): Boolean {
        val config = (repository.state.value as? ConfigState.Ready)?.config ?: return false
        return config.playlists.firstOrNull { it.id == playlistId }?.isProtected == true
    }

    companion object {
        const val DEFAULT_PIN = "0000"

        fun isValidPin(pin: String): Boolean = pin.length == 4 && pin.all { it.isDigit() }
    }
}
