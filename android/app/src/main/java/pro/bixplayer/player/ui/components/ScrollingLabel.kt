package pro.bixplayer.player.ui.components

import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Label that scrolls sideways on one line, like a marquee, when [active] and the text does not
 * fit, so a long folder name ("CANAIS | GLOBOS CAPITAIS") can be read in full. Text that fits
 * never moves. At rest it wraps onto up to [restingLines] lines and then ends in "…" — but only
 * when the first word fits the width: otherwise the first line would hold half a word
 * ("Document / ários"), and there "Documen…" reads better. A wide word further on just ends the
 * last line in "…".
 */
@Composable
fun ScrollingLabel(
    text: String,
    active: Boolean,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    restingLines: Int = 1,
) {
    BoxWithConstraints(modifier = modifier) {
        val measurer = rememberTextMeasurer()
        val firstWordPx = remember(text, style, measurer) {
            measurer.measure(text.trim().substringBefore(' '), style).size.width
        }
        val lines = when {
            active -> 1
            restingLines > 1 && firstWordPx <= constraints.maxWidth -> restingLines
            else -> 1
        }
        Text(
            text = text,
            style = style,
            color = color,
            maxLines = lines,
            softWrap = lines > 1,
            overflow = if (active) TextOverflow.Clip else TextOverflow.Ellipsis,
            modifier = if (active) {
                Modifier.basicMarquee(
                    iterations = Int.MAX_VALUE,
                    animationMode = MarqueeAnimationMode.Immediately,
                    initialDelayMillis = 900,
                    repeatDelayMillis = 1_500,
                    velocity = 40.dp,
                )
            } else {
                Modifier
            },
        )
    }
}
