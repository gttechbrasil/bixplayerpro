package pro.bixplayer.player.ui.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import pro.bixplayer.player.util.localized

/** Languages the settings screen offers. Tags match the `values-*` resource folders. */
object AppLanguages {
    const val PT_BR = "pt-BR"
    const val EN = "en"
    const val ES = "es"
    val all = listOf(PT_BR, EN, ES)

    fun next(current: String): String {
        val index = all.indexOf(current)
        return all[(index + 1).mod(all.size)]
    }
}

/**
 * Applies the language chosen in the settings screen to everything below it.
 *
 * `stringResource` reads from `LocalContext`, so providing a context configured with the
 * chosen locale switches every string live, without restarting the activity and without an
 * AppCompat dependency.
 */
@Composable
fun BixLocale(languageTag: String, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val localized = remember(base, languageTag) { base.localized(languageTag) }
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalResources provides localized.resources,
        LocalConfiguration provides localized.resources.configuration,
        content = content,
    )
}
