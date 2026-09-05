package pro.bixplayer.player.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pro.bixplayer.player.R
import pro.bixplayer.player.domain.model.AppConfig
import pro.bixplayer.player.ui.components.BixButton
import pro.bixplayer.player.ui.screens.playlists.PlaylistViewModel

/**
 * Placeholder home. Block 5 replaces it with the real layout; for now it proves the boot flow
 * reached an activated device and it kicks off the first playlist sync.
 */
@Composable
fun HomeScreen(
    config: AppConfig?,
    onChangePlaylist: () -> Unit,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // First sync happens as soon as the device is usable; it is a no-op when already synced.
    LaunchedEffect(state.activeId) {
        if (state.activeId != null) viewModel.syncActive()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(48.dp),
        ) {
            Text(
                text = config?.platformName?.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.status_active),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            config?.let {
                Text(
                    text = "${it.macAddress} · ${it.playlists.size} playlist(s)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = when {
                    state.syncing -> stringResource(R.string.playlist_syncing)
                    state.channelCount > 0 ->
                        stringResource(R.string.playlist_channel_count, state.channelCount)
                    else -> stringResource(R.string.playlist_not_synced)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.notice?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(16.dp))
            BixButton(
                text = stringResource(R.string.settings_playlist),
                onClick = onChangePlaylist,
            )
            Text(
                text = stringResource(R.string.home_coming_soon),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
