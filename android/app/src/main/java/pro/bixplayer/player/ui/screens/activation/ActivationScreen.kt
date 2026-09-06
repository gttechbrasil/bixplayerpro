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
import androidx.compose.foundation.layout.imePadding
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pro.bixplayer.player.R
import pro.bixplayer.player.ui.components.BixButton
import pro.bixplayer.player.ui.components.BixTextField
import pro.bixplayer.player.util.QrCode
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import android.content.Intent
import android.widget.Toast
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import pro.bixplayer.player.ui.theme.LocalIsTv

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
    val compact = !LocalIsTv.current

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
                .padding(horizontal = if (compact) 20.dp else 64.dp, vertical = if (compact) 24.dp else 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.activation_title),
                style = if (compact) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.activation_instruction),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp),
            )

            Spacer(Modifier.height(if (compact) 20.dp else 32.dp))

            if (compact) {
                // Portrait phone: MAC above the QR, both full width, smaller type.
                MacAddressPanel(mac = state.macAddress, compact = true, modifier = Modifier.fillMaxWidth())
                if (state.macAddress.isNotBlank()) {
                    Spacer(Modifier.height(20.dp))
                    QrPanel(content = state.macAddress, compact = true)
                }
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(48.dp),
                ) {
                    MacAddressPanel(mac = state.macAddress, compact = false, modifier = Modifier.weight(1f))
                    if (state.macAddress.isNotBlank()) {
                        QrPanel(content = state.macAddress, compact = false)
                    }
                }
            }

            Spacer(Modifier.height(if (compact) 20.dp else 32.dp))

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

            ActionButtons(compact = compact) {
                BixButton(
                    text = if (state.checking) stringResource(R.string.activation_checking)
                    else stringResource(R.string.activation_check),
                    enabled = !state.checking,
                    onClick = { viewModel.check(notYet) },
                    focusRequester = checkFocus,
                    modifier = if (compact) Modifier.fillMaxWidth() else Modifier,
                )
                BixButton(
                    text = stringResource(R.string.activation_add_playlist),
                    primary = false,
                    onClick = { viewModel.togglePlaylistForm() },
                    modifier = if (compact) Modifier.fillMaxWidth() else Modifier,
                )
            }

            if (!LocalIsTv.current && state.macAddress.isNotBlank()) {
                // On a phone the MAC goes to the reseller by clipboard or WhatsApp, not by dictation.
                val context = LocalContext.current
                val clipboard = LocalClipboardManager.current
                val copied = stringResource(R.string.activation_copied)
                val shareText = stringResource(R.string.activation_instruction) + ": " + state.macAddress
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    BixButton(
                        text = stringResource(R.string.activation_copy_mac),
                        primary = false,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            clipboard.setText(AnnotatedString(state.macAddress))
                            Toast.makeText(context, copied, Toast.LENGTH_SHORT).show()
                        },
                    )
                    BixButton(
                        text = stringResource(R.string.activation_share),
                        primary = false,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            runCatching { context.startActivity(Intent.createChooser(intent, null)) }
                        },
                    )
                }
            }

            if (state.showPlaylistForm) {
                Spacer(Modifier.height(if (compact) 20.dp else 32.dp))
                PlaylistForm(
                    compact = compact,
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

/** Two buttons side by side on TV, stacked full-width on a phone. */
@Composable
private fun ActionButtons(compact: Boolean, content: @Composable () -> Unit) {
    if (compact) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) { content() }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { content() }
    }
}

@Composable
private fun MacAddressPanel(mac: String, compact: Boolean, modifier: Modifier = Modifier) {
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
            fontSize = if (compact) 26.sp else 52.sp,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier
                .then(if (compact) Modifier.fillMaxWidth() else Modifier)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(horizontal = if (compact) 16.dp else 32.dp, vertical = if (compact) 14.dp else 20.dp),
        )
    }
}

@Composable
private fun QrPanel(content: String, compact: Boolean) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    // Cap the QR at a third of the screen height on TV so it stays sharp on 1080p and on 720p;
    // on a phone it follows the width instead so it never spills past the column.
    val sidePx = remember(content, compact, configuration.screenHeightDp, configuration.screenWidthDp) {
        val side = if (compact) {
            minOf(configuration.screenWidthDp * 0.55f, configuration.screenHeightDp * 0.28f).dp
        } else {
            configuration.screenHeightDp.dp * 0.32f
        }
        with(density) { side.roundToPx() }
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
    compact: Boolean,
    submitting: Boolean,
    onSubmit: (name: String, url: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    val urlFocus = remember { FocusRequester() }
    val submitFocus = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxWidth(if (compact) 1f else 0.7f)
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(if (compact) 16.dp else 24.dp)
            // While the on-screen keyboard is open it owns the D-pad, so the only way out of a
            // field is the IME action; padding keeps the button reachable once it closes.
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BixTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.playlist_name),
            imeAction = ImeAction.Next,
            // "Next" on the virtual keyboard must land on the URL field: with the IME open the
            // D-pad moves the keyboard cursor, not the form focus.
            onImeAction = { runCatching { urlFocus.requestFocus() } },
        )
        BixTextField(
            value = url,
            onValueChange = { url = it },
            label = stringResource(R.string.playlist_url),
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Done,
            focusRequester = urlFocus,
            onImeAction = {
                runCatching { submitFocus.requestFocus() }
                if (!submitting) onSubmit(name, url)
            },
        )
        BixButton(
            text = stringResource(R.string.playlist_add),
            enabled = !submitting,
            onClick = { onSubmit(name, url) },
            modifier = Modifier.align(Alignment.CenterHorizontally),
            focusRequester = submitFocus,
        )
    }
}
