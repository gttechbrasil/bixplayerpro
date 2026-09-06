package pro.bixplayer.player.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import pro.bixplayer.player.R
import pro.bixplayer.player.domain.parental.ParentalGate

/** Thin view model so any screen can ask the parental gate without owning its own logic. */
@HiltViewModel
class PinGateViewModel @Inject constructor(private val gate: ParentalGate) : ViewModel() {
    val unlocked: StateFlow<Boolean> = gate.unlocked

    /** The PIN check is a DataStore read; blocking the dialog callback for it is fine. */
    fun tryUnlock(pin: String): Boolean = runBlocking { gate.tryUnlock(pin) }

    fun playlistProtected(playlistId: Long?): Boolean = gate.playlistProtected(playlistId)
}

/**
 * Per-screen PIN gate. Call [require] from anywhere in the screen: the action runs at once
 * when nothing is locked or the session is already unlocked, otherwise after [PinGateDialog]
 * accepts the PIN.
 */
class PinGateState internal constructor(private val viewModel: PinGateViewModel) {
    internal var pending by mutableStateOf<(() -> Unit)?>(null)
    internal var reason by mutableStateOf<Int?>(null)

    fun require(locked: Boolean, reasonRes: Int? = null, action: () -> Unit) {
        if (!locked || viewModel.unlocked.value) {
            action()
        } else {
            reason = reasonRes
            pending = action
        }
    }

    fun playlistProtected(playlistId: Long?): Boolean = viewModel.playlistProtected(playlistId)

    internal fun tryUnlock(pin: String): Boolean = viewModel.tryUnlock(pin)
}

@Composable
fun rememberPinGate(viewModel: PinGateViewModel = hiltViewModel()): PinGateState =
    remember(viewModel) { PinGateState(viewModel) }

/** Place once at the end of the screen's root Box so the dialog draws over everything. */
@Composable
fun PinGateDialog(gate: PinGateState) {
    val action = gate.pending ?: return
    PinDialog(
        title = stringResource(R.string.pin_enter),
        subtitle = gate.reason?.let { stringResource(it) },
        onSubmit = { pin ->
            val ok = gate.tryUnlock(pin)
            if (ok) {
                gate.pending = null
                action()
            }
            ok
        },
        onCancel = { gate.pending = null },
    )
}
