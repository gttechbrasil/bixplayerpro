package pro.bixplayer.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import pro.bixplayer.player.ui.theme.bixFocusable
import pro.bixplayer.player.ui.components.onSelect

/**
 * Search in two states: a focusable row that only opens the text field (and therefore the
 * keyboard) on OK. Landing on a plain text field with the D-pad would pop the keyboard every
 * time the user overshoots the top of a list.
 */
@Composable
fun SearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf(query.isNotEmpty()) }
    val fieldRequester = remember { FocusRequester() }
    LaunchedEffect(editing) {
        if (editing) {
            delay(50)
            runCatching { fieldRequester.requestFocus() }
        }
    }

    if (editing) {
        BixTextField(
            value = query,
            onValueChange = onQueryChange,
            label = placeholder,
            imeAction = ImeAction.Done,
            focusRequester = fieldRequester,
            modifier = modifier,
            onImeAction = {
                if (query.isEmpty()) editing = false
                onDone()
            },
        )
    } else {
        val interaction = remember { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        val shape = RoundedCornerShape(10.dp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = modifier
                .fillMaxWidth()
                .bixFocusable(focused, scale = 1f, shape = shape)
                .background(MaterialTheme.colorScheme.surface, shape)
                .focusable(interactionSource = interaction)
                .onSelect { editing = true }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                text = "🔍  " + query.ifEmpty { placeholder },
                style = MaterialTheme.typography.bodyLarge,
                color = if (query.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
