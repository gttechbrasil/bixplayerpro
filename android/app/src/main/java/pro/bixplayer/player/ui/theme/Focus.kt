package pro.bixplayer.player.ui.theme

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape

/** Focus tokens. Keeping them here stops each screen from inventing its own highlight. */
object BixFocus {
    /** How much a focused card grows. Subtle: large jumps make long lists nauseating. */
    const val SCALE = 1.08f
    const val SCALE_SMALL = 1.04f
    const val ANIMATION_MS = 140
    val BORDER_WIDTH = 3.dp
    val CARD_SHAPE: Shape = RoundedCornerShape(12.dp)
}

@Composable
fun MutableInteractionSource.isFocusedState(): State<Boolean> = collectIsFocusedAsState()

/**
 * Standard focus treatment: a small scale plus a brand-coloured border.
 * Applied through a modifier so TV and phone share exactly the same visual language.
 */
@Composable
fun Modifier.bixFocusable(
    focused: Boolean,
    scale: Float = BixFocus.SCALE,
    shape: Shape = BixFocus.CARD_SHAPE,
    borderColor: Color = BixBlue,
): Modifier {
    val animatedScale by animateFloatAsState(
        targetValue = if (focused) scale else 1f,
        animationSpec = tween(BixFocus.ANIMATION_MS),
        label = "focusScale",
    )
    return this
        .scale(animatedScale)
        .border(
            width = if (focused) BixFocus.BORDER_WIDTH else 0.dp,
            color = if (focused) borderColor else Color.Transparent,
            shape = shape,
        )
}
