package pro.bixplayer.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme
import androidx.tv.material3.MaterialTheme as TvMaterialTheme

/**
 * Whether the current screen is being drawn for a 10-foot (TV) experience.
 * Shared components read this to pick sizes and focus behaviour instead of duplicating screens.
 */
val LocalIsTv = staticCompositionLocalOf { false }

private val DarkColors = androidx.compose.material3.darkColorScheme(
    primary = BixBlue,
    onPrimary = Color.White,
    secondary = BixBlueLight,
    background = BixBackground,
    onBackground = BixOnSurface,
    surface = BixSurface,
    onSurface = BixOnSurface,
    surfaceVariant = BixSurfaceVariant,
    onSurfaceVariant = BixOnSurfaceVariant,
    error = BixError,
)

private val TvColors = tvDarkColorScheme(
    primary = BixBlue,
    onPrimary = Color.White,
    secondary = BixBlueLight,
    background = BixBackground,
    onBackground = BixOnSurface,
    surface = BixSurface,
    onSurface = BixOnSurface,
    surfaceVariant = BixSurfaceVariant,
    onSurfaceVariant = BixOnSurfaceVariant,
    error = BixError,
)

/**
 * Theme for the TV activity. Uses tv-material so that focus, indication and component sizes
 * follow the 10-foot guidelines; also installs the Material 3 theme because the shared
 * components (text fields, dialogs) come from there.
 */
@Composable
fun BixTvTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalIsTv provides true) {
        TvMaterialTheme(colorScheme = TvColors, typography = TvTypography) {
            MaterialTheme(colorScheme = DarkColors, typography = TvTypographyM3, content = content)
        }
    }
}

/** Theme for the phone/tablet activity. Same tokens, smaller type scale. */
@Composable
fun BixMobileTheme(content: @Composable () -> Unit) {
    // The app is dark-only by design (it is a video player); the parameter is read so that a
    // future light theme is a one-line change.
    @Suppress("UNUSED_VARIABLE")
    val systemDark = isSystemInDarkTheme()
    CompositionLocalProvider(LocalIsTv provides false) {
        MaterialTheme(colorScheme = DarkColors, typography = MobileTypography, content = content)
    }
}
