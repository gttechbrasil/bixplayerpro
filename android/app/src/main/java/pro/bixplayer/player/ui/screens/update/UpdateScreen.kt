package pro.bixplayer.player.ui.screens.update

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pro.bixplayer.player.R
import pro.bixplayer.player.ui.components.BixButton
import pro.bixplayer.player.util.QrCode

/**
 * Forced update. There is no in-app updater on Android TV outside the Play Store, so the screen
 * shows the download link plus a QR the user can scan with a phone.
 */
@Composable
fun UpdateScreen(
    currentVersion: String,
    minimumVersion: String,
    apkUrl: String,
    checking: Boolean,
    onCheck: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val qr = remember(apkUrl) { if (apkUrl.isNotBlank()) QrCode.encode(apkUrl, 420) else null }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(48.dp),
        ) {
            Text(
                text = stringResource(R.string.update_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.update_message),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp),
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.update_current_version, currentVersion),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.update_required_version, minimumVersion),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (apkUrl.isNotBlank()) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = stringResource(R.string.update_link, apkUrl),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )
                qr?.let {
                    Spacer(Modifier.height(16.dp))
                    Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = apkUrl,
                        modifier = Modifier
                            .size(210.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .padding(8.dp),
                    )
                }
            }

            Spacer(Modifier.height(32.dp))
            BixButton(
                text = if (checking) stringResource(R.string.activation_checking)
                else stringResource(R.string.update_check_again),
                enabled = !checking,
                onClick = onCheck,
                focusRequester = focus,
            )
        }
    }
}
