package pro.bixplayer.player.ui.screens.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import pro.bixplayer.player.ui.components.BrandLogo
import pro.bixplayer.player.R
import pro.bixplayer.player.ui.components.BixButton

/**
 * First screen. Shows the reseller logo while the configuration is fetched, and turns into an
 * error state with a retry button when there is neither network nor cache.
 */
@Composable
fun SplashScreen(
    logoUrl: String?,
    platformName: String?,
    error: String?,
    onRetry: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(error) {
        if (error != null) runCatching { focus.requestFocus() }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(48.dp),
        ) {
            BrandLogo(
                url = logoUrl,
                contentDescription = platformName?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.app_name),
                modifier = Modifier.heightIn(max = 160.dp),
            )

            Spacer(Modifier.height(32.dp))

            if (error == null) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Text(
                    text = stringResource(R.string.loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            } else {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))
                BixButton(
                    text = stringResource(R.string.retry),
                    onClick = onRetry,
                    focusRequester = focus,
                )
            }
        }
    }
}
