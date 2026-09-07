package pro.bixplayer.player.ui.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import timber.log.Timber

/**
 * "Activate this element": OK/ENTER on a remote (fires on KEY UP, whether or not the DOWN
 * reached this node — M5-014) and a tap on a touchscreen. Both paths live on the same node
 * that owns `focusable()`, so the highlighted item is always the one that receives the key,
 * and the phone UI stays usable with a D-pad on an AOSP box (M5-017). Unlike `clickable`,
 * no second focus target is created and the platform never sees a stray DPAD_CENTER.
 */
@Composable
fun Modifier.onSelect(action: () -> Unit): Modifier = this
    .semantics { role = Role.Button; onClick { action(); true } }
    .onKeyEvent { event ->
        val select = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
        if (!select) return@onKeyEvent false
        if (event.type == KeyEventType.KeyUp) {
            Timber.d("select: %s up -> click", event.key)
            action()
        }
        true
    }
    .pointerInput(Unit) { detectTapGestures(onTap = { action() }) }

/** Touch-only tap for elements that already handle several remote keys themselves. */
@Composable
fun Modifier.tap(action: () -> Unit): Modifier =
    pointerInput(Unit) { detectTapGestures(onTap = { action() }) }
