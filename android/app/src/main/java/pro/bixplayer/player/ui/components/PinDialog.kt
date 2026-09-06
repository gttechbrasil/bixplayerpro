package pro.bixplayer.player.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import pro.bixplayer.player.R
import pro.bixplayer.player.ui.theme.BixScrim
import pro.bixplayer.player.ui.theme.bixFocusable
import pro.bixplayer.player.ui.components.onSelect

/**
 * Four-digit PIN entry over a scrim. Digits come from the remote's number keys or from the
 * on-screen keypad (D-pad navigable). [onSubmit] returns true when the PIN is accepted; a
 * wrong one clears the boxes and shows the error.
 */
@Composable
fun PinDialog(
    title: String,
    subtitle: String? = null,
    onSubmit: (String) -> Boolean,
    onCancel: () -> Unit,
) {
    var digits by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    val firstKey = remember { FocusRequester() }
    val wrongPin = stringResource(R.string.pin_wrong)

    BackHandler { onCancel() }
    LaunchedEffect(Unit) {
        delay(60)
        runCatching { firstKey.requestFocus() }
    }

    fun push(digit: Char) {
        if (digits.length >= 4) return
        error = false
        digits += digit
        if (digits.length == 4) {
            if (onSubmit(digits)) return
            error = true
            digits = ""
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BixScrim)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onKeyEvent false
                val ch = event.utf16CodePoint.toChar()
                when {
                    ch in '0'..'9' -> { push(ch); true }
                    event.key == Key.Backspace || event.key == Key.Delete -> { digits = digits.dropLast(1); true }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(horizontal = 40.dp, vertical = 28.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .size(48.dp, 56.dp)
                            .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = if (index < digits.length) "●" else "",
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Text(
                text = if (error) wrongPin else " ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 10.dp),
            )
            Spacer(Modifier.height(8.dp))
            val rows = listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9'), listOf('⌫', '0', '✕'))
            rows.forEachIndexed { rowIndex, row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEachIndexed { colIndex, key ->
                        KeypadKey(
                            label = key.toString(),
                            focusRequester = if (rowIndex == 0 && colIndex == 0) firstKey else null,
                            onPress = {
                                when (key) {
                                    '⌫' -> digits = digits.dropLast(1)
                                    '✕' -> onCancel()
                                    else -> push(key)
                                }
                            },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun KeypadKey(label: String, focusRequester: FocusRequester?, onPress: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .width(72.dp)
            .height(52.dp)
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .bixFocusable(focused, scale = 1.06f, shape = shape)
            .background(if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, shape)
            .focusable(interactionSource = interaction)
            .onSelect { onPress() },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = if (focused) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        )
    }
}
