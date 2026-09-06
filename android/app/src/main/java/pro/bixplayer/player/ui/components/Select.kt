package pro.bixplayer.player.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import pro.bixplayer.player.ui.theme.LocalIsTv

/**
 * "Activate this element": OK/Enter on a TV remote, a tap on a phone. The two paths are
 * exclusive on purpose — `clickable` also reacts to DPAD_CENTER, so using both on a TV would
 * fire the action twice.
 */
@Composable
fun Modifier.onSelect(action: () -> Unit): Modifier =
    if (LocalIsTv.current) {
        onKeyEvent { event ->
            val select = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
            if (select && event.type == KeyEventType.KeyUp) {
                action(); true
            } else {
                false
            }
        }
    } else {
        clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = action)
    }

/** Touch-only tap for elements that already handle several remote keys themselves. */
@Composable
fun Modifier.tap(action: () -> Unit): Modifier =
    if (LocalIsTv.current) this
    else clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = action)
