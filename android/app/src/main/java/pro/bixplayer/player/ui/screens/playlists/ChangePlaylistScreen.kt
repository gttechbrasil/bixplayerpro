package pro.bixplayer.player.ui.screens.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import pro.bixplayer.player.R
import pro.bixplayer.player.domain.model.Playlist
import pro.bixplayer.player.domain.model.PlaylistType
import pro.bixplayer.player.ui.components.BixButton
import pro.bixplayer.player.ui.theme.BixFocus
import pro.bixplayer.player.ui.theme.bixFocusable

/**
 * Lists the playlists the reseller assigned to this device, marks the active one and lets the
 * user switch, remove or add. Switching triggers a full resync of the local database.
 */
@Composable
fun ChangePlaylistScreen(
    onBack: () -> Unit,
    viewModel: PlaylistViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val invalidUrl = stringResource(R.string.playlist_invalid_url)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 64.dp, vertical = 40.dp),
    ) {
        Text(
            text = stringResource(R.string.settings_playlist),
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = if (state.channelCount > 0) {
                stringResource(R.string.playlist_channel_count, state.channelCount)
            } else {
                stringResource(R.string.playlist_not_synced)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )

        state.notice?.let { notice ->
            Spacer(Modifier.height(16.dp))
            Text(
                text = notice,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                    .padding(16.dp),
            )
        }

        Spacer(Modifier.height(24.dp))

        if (state.playlists.isEmpty()) {
            Text(
                text = stringResource(R.string.playlist_none),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(state.playlists, key = { it.id }) { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        active = playlist.id == state.activeId,
                        busy = state.syncing && playlist.id == state.activeId ||
                            state.removingId == playlist.id,
                        onSelect = { viewModel.select(playlist) },
                        onRemove = { viewModel.remove(playlist) },
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            BixButton(
                text = stringResource(R.string.settings_refresh),
                enabled = !state.syncing && state.activeId != null,
                onClick = { viewModel.syncActive(force = true) },
            )
            BixButton(text = stringResource(R.string.close), primary = false, onClick = onBack)
        }
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    active: Boolean,
    busy: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val shape = RoundedCornerShape(12.dp)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .bixFocusable(focused, scale = BixFocus.SCALE_SMALL, shape = shape)
            .background(MaterialTheme.colorScheme.surface, shape)
            .focusable(interactionSource = interaction)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyUp) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        onSelect(); true
                    }
                    // The remote has no delete key: the menu button removes the playlist.
                    Key.Menu -> {
                        onRemove(); true
                    }
                    else -> false
                }
            }
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            text = if (active) "●" else "○",
            style = MaterialTheme.typography.titleMedium,
            color = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.padding(horizontal = 8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when (playlist.type) {
                    PlaylistType.XTREAM -> "Xtream"
                    PlaylistType.M3U -> "M3U"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (busy) {
            Text(
                text = stringResource(R.string.loading),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        } else if (focused) {
            Text(
                text = stringResource(R.string.playlist_remove_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
