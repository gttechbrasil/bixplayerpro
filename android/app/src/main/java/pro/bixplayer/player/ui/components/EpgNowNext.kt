package pro.bixplayer.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import pro.bixplayer.player.R
import pro.bixplayer.player.data.db.EpgProgramEntity

/**
 * Current programme with its progress, then the next one; a hint when the guide is empty.
 * Used by the channel preview and by the player overlay, so both say the same thing.
 */
@Composable
fun EpgNowNext(now: EpgProgramEntity?, next: EpgProgramEntity?, modifier: Modifier = Modifier) {
    val timeFormat = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val zone = remember { ZoneId.systemDefault() }
    fun hhmm(ms: Long) = Instant.ofEpochMilli(ms).atZone(zone).format(timeFormat)
    Column(modifier = modifier) {
        if (now == null && next == null) {
            Text(
                text = stringResource(R.string.live_epg_slot),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        now?.let { programme ->
            Text(
                text = stringResource(R.string.live_now, hhmm(programme.startAt), programme.title),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val total = (programme.endAt - programme.startAt).coerceAtLeast(1L)
            val fraction = ((System.currentTimeMillis() - programme.startAt).toFloat() / total).coerceIn(0f, 1f)
            Spacer(Modifier.height(6.dp))
            Box(modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.2f))) {
                Box(modifier = Modifier.fillMaxWidth(fraction).height(4.dp).background(MaterialTheme.colorScheme.primary))
            }
        }
        next?.let { programme ->
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.live_next, hhmm(programme.startAt), programme.title),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
