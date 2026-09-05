package pro.bixplayer.player.ui.locale

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import java.util.Locale

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
    val localized = remember(base, languageTag) { localizedContext(base, languageTag) }
    CompositionLocalProvider(
        LocalContext provides localized,
        LocalResources provides localized.resources,
        LocalConfiguration provides localized.resources.configuration,
        content = content,
    )
}

/**
 * A wrapper over the activity context whose resources speak [languageTag]. It has to stay a
 * `ContextWrapper` around the activity: Hilt's view-model factory walks the wrapper chain to
 * find the activity, and a bare `createConfigurationContext` result would break it.
 */
private fun localizedContext(base: Context, languageTag: String): Context {
    val locale = Locale.forLanguageTag(languageTag)
    val configuration = Configuration(base.resources.configuration).apply { setLocale(locale) }
    val resources = base.createConfigurationContext(configuration).resources
    return object : ContextWrapper(base) {
        override fun getResources(): Resources = resources
    }
}
