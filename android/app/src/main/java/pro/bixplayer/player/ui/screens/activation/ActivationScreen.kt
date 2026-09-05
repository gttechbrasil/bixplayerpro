package pro.bixplayer.player.ui.screens.activation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pro.bixplayer.player.R
import pro.bixplayer.player.ui.components.BixButton
import pro.bixplayer.player.ui.components.BixTextField
import pro.bixplayer.player.util.QrCode
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType

/**
 * Shown when the device is not linked to a reseller yet, or is linked but has no playlist.
 * The MAC is the only thing the user needs to read out loud, so it dominates the screen.
 */
@Composable
fun ActivationScreen(
    onActivated: () -> Unit,
    viewModel: ActivationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val notYet = stringResource(R.string.activation_not_yet)
    val addedMessage = stringResource(R.string.playlist_added)
    val invalidUrl = stringResource(R.string.playlist_invalid_url)
    val emptyFields = stringResource(R.string.playlist_required_fields)

    val checkFocus = remember { FocusRequester() }

    LaunchedEffect(state.activated) {
        if (state.activated) onActivated()
    }
    LaunchedEffect(Unit) {
        runCatching { checkFocus.requestFocus() }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 64.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.activation_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.activation_instruction),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            Spacer(Modifier.height(32.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(48.dp),
            ) {
                MacAddressPanel(mac = state.macAddress, modifier = Modifier.weight(1f))
                if (state.macAddress.isNotBlank()) {
                    QrPanel(content = state.macAddress)
                }
            }

            Spacer(Modifier.height(32.dp))

            state.notice?.let { notice ->
                Text(
                    text = notice,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(10.dp),
                        )
                        .padding(16.dp),
                )
                Spacer(Modifier.height(24.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BixButton(
                    text = if (state.checking) stringResource(R.string.activation_checking)
                    else stringResource(R.string.activation_check),
                    enabled = !state.checking,
                    onClick = { viewModel.check(notYet) },
                    focusRequester = checkFocus,
                )
                BixButton(
                    text = stringResource(R.string.activation_add_playlist),
                    primary = false,
                    onClick = { viewModel.togglePlaylistForm() },
                )
            }

            if (state.showPlaylistForm) {
                Spacer(Modifier.height(32.dp))
                PlaylistForm(
                    submitting = state.addingPlaylist,
                    onSubmit = { name, url ->
                        viewModel.addPlaylist(name, url, addedMessage) { error ->
                            when (error) {
                                ActivationError.EMPTY_FIELDS -> emptyFields
                                ActivationError.INVALID_URL -> invalidUrl
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun MacAddressPanel(mac: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = stringResource(R.string.activation_mac_label),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            // Monospace and very large: this is read out loud over the phone.
            text = mac.ifBlank { "…" },
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 52.sp,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(horizontal = 32.dp, vertical = 20.dp),
        )
    }
}

@Composable
private fun QrPanel(content: String) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    // Cap the QR at a quarter of the screen height so it stays sharp on 1080p and on 720p.
    val sidePx = remember(content, configuration.screenHeightDp) {
        with(density) { (configuration.screenHeightDp.dp * 0.32f).roundToPx() }
    }
    val bitmap = remember(content, sidePx) { QrCode.encode(content, sidePx) }
    if (bitmap == null) return

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = stringResource(R.string.activation_qr_hint),
            modifier = Modifier
                .size(with(density) { sidePx.toDp() })
                .clip(RoundedCornerShape(12.dp))
                .background(androidx.compose.ui.graphics.Color.White)
                .padding(8.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.activation_qr_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(with(density) { sidePx.toDp() }),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PlaylistForm(
    submitting: Boolean,
    onSubmit: (name: String, url: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth(0.7f)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BixTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.playlist_name),
        )
        BixTextField(
            value = url,
            onValueChange = { url = it },
            label = stringResource(R.string.playlist_url),
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Done,
            onImeAction = { if (!submitting) onSubmit(name, url) },
        )
        BixButton(
            text = stringResource(R.string.playlist_add),
            enabled = !submitting,
            onClick = { onSubmit(name, url) },
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}
