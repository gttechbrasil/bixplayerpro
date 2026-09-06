package pro.bixplayer.player.ui.screens.settings

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import pro.bixplayer.player.R
import pro.bixplayer.player.ui.components.BixButton
import pro.bixplayer.player.ui.components.PinGateDialog
import pro.bixplayer.player.ui.components.rememberPinGate
import pro.bixplayer.player.ui.locale.AppLanguages
import pro.bixplayer.player.ui.theme.BixFocus
import pro.bixplayer.player.ui.theme.bixFocusable

/** Settings, M3 minimum. Every row is a focusable line: OK acts, the value shows on the right. */
@Composable
fun SettingsScreen(
    onChangePlaylist: () -> Unit,
    onParental: () -> Unit,
    onLoggedOut: () -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val gate = rememberPinGate()
    val channelsUpdated = stringResource(R.string.playlist_channel_count)
    val cacheCleared = stringResource(R.string.settings_cache_cleared)

    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) onLoggedOut()
    }

    val firstRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(60)
        runCatching { firstRequester.requestFocus() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 64.dp, vertical = 40.dp),
    ) {
        Text(
            text = stringResource(R.string.home_settings),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = stringResource(R.string.settings_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )

        state.notice?.let { notice ->
            Spacer(Modifier.height(16.dp))
            Text(
                text = notice,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                    .padding(16.dp),
            )
        }

        Spacer(Modifier.height(24.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
        ) {
            SettingRow(
                title = stringResource(R.string.settings_playlist),
                value = "›",
                focusRequester = firstRequester,
                onClick = onChangePlaylist,
            )
            SettingRow(
                title = stringResource(R.string.settings_refresh),
                value = if (state.syncing) stringResource(R.string.playlist_syncing) else "↻",
                enabled = !state.syncing,
                onClick = { viewModel.refreshLists { count -> channelsUpdated.format(count) } },
            )
            SettingRow(
                title = stringResource(R.string.settings_refresh_period),
                value = stringResource(R.string.settings_refresh_hours, state.refreshHours),
                onClick = viewModel::cycleRefreshPeriod,
            )
            SettingRow(
                title = stringResource(R.string.settings_layout),
                value = stringResource(if (state.layout == "grid") R.string.settings_layout_grid else R.string.settings_layout_default),
                onClick = viewModel::toggleLayout,
            )
            SettingRow(
                title = stringResource(R.string.settings_engine),
                value = stringResource(
                    when (state.engine) {
                        "vlc" -> R.string.settings_engine_vlc
                        "media3" -> R.string.settings_engine_media3
                        else -> R.string.settings_engine_auto
                    },
                ),
                onClick = viewModel::cycleEngine,
            )
            SettingRow(
                title = stringResource(R.string.settings_language),
                value = languageName(state.language),
                onClick = viewModel::cycleLanguage,
            )
            SettingRow(
                title = stringResource(R.string.settings_parental),
                value = "🔒",
                onClick = { gate.require(true, null, onParental) },
            )
            SettingRow(
                title = stringResource(R.string.settings_clear_cache),
                value = if (state.busy) stringResource(R.string.loading) else "",
                enabled = !state.busy,
                onClick = { viewModel.clearCache(cacheCleared) },
            )
            SettingRow(
                title = stringResource(R.string.settings_mac),
                value = state.macAddress,
                enabled = false,
                onClick = {},
            )
            SettingRow(
                title = stringResource(R.string.settings_version),
                value = state.version,
                enabled = false,
                onClick = {},
            )

            if (state.confirmLogout) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .padding(20.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_logout_confirm),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        BixButton(
                            text = stringResource(R.string.settings_logout_yes),
                            enabled = !state.busy,
                            onClick = viewModel::logout,
                        )
                        BixButton(
                            text = stringResource(R.string.cancel),
                            primary = false,
                            onClick = viewModel::cancelLogout,
                        )
                    }
                }
            } else {
                SettingRow(
                    title = stringResource(R.string.settings_logout),
                    value = "",
                    danger = true,
                    onClick = viewModel::askLogout,
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        BixButton(text = stringResource(R.string.close), primary = false, onClick = onBack)
    }
    PinGateDialog(gate)
    }
}

@Composable
private fun languageName(tag: String): String = when (tag) {
    AppLanguages.EN -> stringResource(R.string.settings_language_en)
    AppLanguages.ES -> stringResource(R.string.settings_language_es)
    else -> stringResource(R.string.settings_language_pt)
}

@Composable
private fun SettingRow(
    title: String,
    value: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    danger: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(10.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .bixFocusable(focused, scale = BixFocus.SCALE_SMALL, shape = shape)
            .background(
                if (focused) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
                shape,
            )
            .focusable(enabled = enabled, interactionSource = interaction)
            .onKeyEvent { event ->
                val select = event.key == Key.DirectionCenter || event.key == Key.Enter || event.key == Key.NumPadEnter
                if (enabled && select && event.type == KeyEventType.KeyUp) {
                    onClick(); true
                } else {
                    false
                }
            }
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = when {
                danger -> MaterialTheme.colorScheme.error
                enabled -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
