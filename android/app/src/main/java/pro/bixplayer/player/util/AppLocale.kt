package pro.bixplayer.player.util

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import pro.bixplayer.player.data.datastore.DevicePreferences
import pro.bixplayer.player.data.datastore.DeviceStore

/**
 * A context whose resources speak [languageTag], wrapped around [this] so that anything that
 * walks the wrapper chain (Hilt's view-model factory looks for the activity) still works.
 */
fun Context.localized(languageTag: String): Context {
    val locale = Locale.forLanguageTag(languageTag)
    val configuration = Configuration(resources.configuration).apply { setLocale(locale) }
    val localizedResources = createConfigurationContext(configuration).resources
    return object : ContextWrapper(this) {
        override fun getResources(): Resources = localizedResources
    }
}

/**
 * The language chosen in the settings, for code that needs strings outside composition:
 * player errors, API error messages. Composables get the same language from `BixLocale`.
 */
@Singleton
class AppLocale @Inject constructor(
    @ApplicationContext private val context: Context,
    store: DeviceStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @Volatile
    var languageTag: String = DevicePreferences.DEFAULT_LANGUAGE
        private set

    @Volatile
    private var cache: Pair<String, Resources>? = null

    init {
        scope.launch { store.language.collect { languageTag = it } }
    }

    val resources: Resources
        get() {
            val tag = languageTag
            cache?.takeIf { it.first == tag }?.let { return it.second }
            return context.localized(tag).resources.also { cache = tag to it }
        }

    fun string(@StringRes id: Int, vararg args: Any): String = resources.getString(id, *args)
}
