package pro.bixplayer.player.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "bix_device")

/**
 * Local device state: credentials, the last known configuration and user preferences.
 *
 * The serialised config is kept here (not in Room) because it is a single document that is
 * always read as a whole, and it is what lets the app open offline.
 */
@Singleton
class DevicePreferences @Inject constructor(
    private val context: Context,
) : DeviceStore {
    private object Keys {
        val TOKEN = stringPreferencesKey("token")
        val MAC_ADDRESS = stringPreferencesKey("mac_address")
        val CONFIG_JSON = stringPreferencesKey("config_json")
        val CONFIG_UPDATED_AT = longPreferencesKey("config_updated_at")
        val ACTIVE_PLAYLIST_ID = longPreferencesKey("active_playlist_id")
        val REFRESH_HOURS = longPreferencesKey("refresh_hours")
        val LANGUAGE = stringPreferencesKey("language")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val PIN = stringPreferencesKey("parental_pin")
        val LAYOUT = stringPreferencesKey("layout_override")
        fun engine(playlistId: Long) = stringPreferencesKey("engine_$playlistId")
    }

    companion object {
        /** Default period of the background configuration refresh. */
        const val DEFAULT_REFRESH_HOURS = 6L
        const val DEFAULT_LANGUAGE = "pt-BR"
        const val ENGINE_AUTO = "auto"
    }

    /** DataStore surfaces read errors as IOException; treat them as "no data yet". */
    private val prefs: Flow<Preferences> = context.dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }

    override val token: Flow<String?> = prefs.map { it[Keys.TOKEN] }
    override val macAddress: Flow<String?> = prefs.map { it[Keys.MAC_ADDRESS] }
    override val configJson: Flow<String?> = prefs.map { it[Keys.CONFIG_JSON] }
    val configUpdatedAt: Flow<Long> = prefs.map { it[Keys.CONFIG_UPDATED_AT] ?: 0L }
    override val activePlaylistId: Flow<Long?> = prefs.map { it[Keys.ACTIVE_PLAYLIST_ID] }
    override val refreshHours: Flow<Long> = prefs.map { it[Keys.REFRESH_HOURS] ?: DEFAULT_REFRESH_HOURS }
    override val language: Flow<String> = prefs.map { it[Keys.LANGUAGE] ?: DEFAULT_LANGUAGE }
    val onboarded: Flow<Boolean> = prefs.map { it[Keys.ONBOARDED] ?: false }
    override val pin: Flow<String?> = prefs.map { it[Keys.PIN] }
    override val layoutOverride: Flow<String?> = prefs.map { it[Keys.LAYOUT] }

    override suspend fun currentToken(): String? = token.first()
    override suspend fun currentMacAddress(): String? = macAddress.first()
    override suspend fun currentConfigJson(): String? = configJson.first()
    override suspend fun currentActivePlaylistId(): Long? = activePlaylistId.first()
    override suspend fun currentRefreshHours(): Long = refreshHours.first()
    override suspend fun currentPin(): String? = pin.first()
    override suspend fun currentLayoutOverride(): String? = layoutOverride.first()

    override fun playerEngine(playlistId: Long): Flow<String> = prefs.map { it[Keys.engine(playlistId)] ?: ENGINE_AUTO }
    override suspend fun currentPlayerEngine(playlistId: Long): String = playerEngine(playlistId).first()

    override suspend fun saveCredentials(token: String, macAddress: String) {
        context.dataStore.edit {
            it[Keys.TOKEN] = token
            it[Keys.MAC_ADDRESS] = macAddress
        }
    }

    override suspend fun saveConfig(json: String, updatedAt: Long) {
        context.dataStore.edit {
            it[Keys.CONFIG_JSON] = json
            it[Keys.CONFIG_UPDATED_AT] = updatedAt
        }
    }

    override suspend fun setActivePlaylistId(id: Long?) {
        context.dataStore.edit {
            if (id == null) it.remove(Keys.ACTIVE_PLAYLIST_ID) else it[Keys.ACTIVE_PLAYLIST_ID] = id
        }
    }

    override suspend fun setRefreshHours(hours: Long) {
        context.dataStore.edit { it[Keys.REFRESH_HOURS] = hours.coerceIn(1L, 168L) }
    }

    override suspend fun setLanguage(tag: String) {
        context.dataStore.edit { it[Keys.LANGUAGE] = tag }
    }

    override suspend fun setPin(pin: String?) {
        context.dataStore.edit { if (pin == null) it.remove(Keys.PIN) else it[Keys.PIN] = pin }
    }

    override suspend fun setLayoutOverride(layout: String?) {
        context.dataStore.edit { if (layout == null) it.remove(Keys.LAYOUT) else it[Keys.LAYOUT] = layout }
    }

    override suspend fun setPlayerEngine(playlistId: Long, engine: String) {
        context.dataStore.edit { it[Keys.engine(playlistId)] = engine }
    }

    suspend fun setOnboarded(value: Boolean) {
        context.dataStore.edit { it[Keys.ONBOARDED] = value }
    }

    /** Wipes everything. Used by "sair" in the settings screen. */
    override suspend fun clear() {
        context.dataStore.edit { it.clear() }
    }
}
