package pro.bixplayer.player.ui.screens.parental

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import pro.bixplayer.player.R
import pro.bixplayer.player.data.db.ContentKind
import pro.bixplayer.player.ui.components.BixButton
import pro.bixplayer.player.ui.components.PinDialog
import pro.bixplayer.player.ui.theme.BixFocus
import pro.bixplayer.player.ui.theme.bixFocusable
import pro.bixplayer.player.ui.components.onSelect
import pro.bixplayer.player.ui.components.tap

/**
 * Parental control: change the PIN and, per kind, hide categories (they vanish from lists,
 * search and guide) or lock them behind the PIN. OK toggles "oculta", MENU toggles "bloqueada".
 */
@Composable
fun ParentalScreen(
    onBack: () -> Unit,
    viewModel: ParentalViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val changed = stringResource(R.string.parental_pin_changed)
    val mismatch = stringResource(R.string.parental_pin_mismatch)
    val firstRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(80)
        runCatching { firstRequester.requestFocus() }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 36.dp)) {
            Text(
                text = stringResource(R.string.parental_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.parental_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            state.notice?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                BixButton(text = stringResource(R.string.parental_change_pin), onClick = viewModel::startPinChange, focusRequester = firstRequester)
                Spacer(Modifier.width(12.dp))
                KindChip(stringResource(R.string.home_live), state.kind == ContentKind.LIVE) { viewModel.setKind(ContentKind.LIVE) }
                KindChip(stringResource(R.string.home_movies), state.kind == ContentKind.MOVIE) { viewModel.setKind(ContentKind.MOVIE) }
                KindChip(stringResource(R.string.home_series), state.kind == ContentKind.SERIES) { viewModel.setKind(ContentKind.SERIES) }
            }
            Spacer(Modifier.height(16.dp))

            if (state.categories.isEmpty()) {
                Text(
                    text = stringResource(R.string.catalog_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                    items(state.categories, key = { it.kind + it.remoteId }) { category ->
                        CategoryRuleRow(
                            category = category,
                            onToggleHidden = { viewModel.toggleHidden(category) },
                            onToggleLocked = { viewModel.toggleLocked(category) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            BixButton(text = stringResource(R.string.close), primary = false, onClick = onBack)
        }

        if (state.pinStep != PinStep.NONE) {
            PinDialog(
                title = stringResource(
                    when (state.pinStep) {
                        PinStep.CURRENT -> R.string.pin_enter_current
                        PinStep.NEW -> R.string.pin_enter_new
                        else -> R.string.pin_confirm_new
                    },
                ),
                onSubmit = { pin -> runBlocking { viewModel.submitPin(pin, changed, mismatch) } },
                onCancel = viewModel::cancelPinChange,
            )
        }
    }
}

@Composable
private fun KindChip(label: String, selected: Boolean, onSelect: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(20.dp)
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .bixFocusable(focused, scale = 1f, shape = shape)
            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, shape)
            .focusable(interactionSource = interaction)
            .onSelect { onSelect() }
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

@Composable
private fun CategoryRuleRow(category: ParentalCategory, onToggleHidden: () -> Unit, onToggleLocked: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .bixFocusable(focused, scale = BixFocus.SCALE_SMALL, shape = shape)
            .background(if (focused) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface, shape)
            .focusable(interactionSource = interaction)
            .tap(onToggleHidden)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> { onToggleHidden(); true }
                    Key.Menu -> { onToggleLocked(); true }
                    else -> false
                }
            }
            .padding(horizontal = 18.dp, vertical = 12.dp),
    ) {
        Text(
            text = category.name,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(text = category.count.toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(20.dp))
        StateTag(text = stringResource(R.string.parental_hidden), active = category.hidden)
        Spacer(Modifier.width(8.dp))
        StateTag(text = stringResource(R.string.parental_locked), active = category.locked)
    }
}

@Composable
private fun StateTag(text: String, active: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
