package pro.bixplayer.player.data.datastore

import kotlinx.coroutines.flow.Flow

/**
 * The slice of local state the data layer depends on.
 *
 * Having it as an interface keeps DataStore (and therefore a real Context) out of the unit
 * tests, which can then use a plain in-memory fake.
 */
interface DeviceStore {
    val token: Flow<String?>
    val macAddress: Flow<String?>
    val configJson: Flow<String?>
    val activePlaylistId: Flow<Long?>
    val refreshHours: Flow<Long>
    val language: Flow<String>

    /** Local parental PIN; null means "use the one from the panel". */
    val pin: Flow<String?>

    /** Home layout chosen on the device (`default` | `grid`); null follows the panel. */
    val layoutOverride: Flow<String?>

    suspend fun currentToken(): String?
    suspend fun currentMacAddress(): String?
    suspend fun currentConfigJson(): String?
    suspend fun currentActivePlaylistId(): Long?
    suspend fun currentRefreshHours(): Long
    suspend fun currentPin(): String?
    suspend fun currentLayoutOverride(): String?

    /** Player engine preference per playlist: `auto` | `media3` | `vlc`. */
    fun playerEngine(playlistId: Long): Flow<String>
    suspend fun currentPlayerEngine(playlistId: Long): String

    suspend fun saveCredentials(token: String, macAddress: String)
    suspend fun saveConfig(json: String, updatedAt: Long = System.currentTimeMillis())
    suspend fun setActivePlaylistId(id: Long?)
    suspend fun setRefreshHours(hours: Long)
    suspend fun setLanguage(tag: String)
    suspend fun setPin(pin: String?)
    suspend fun setLayoutOverride(layout: String?)
    suspend fun setPlayerEngine(playlistId: Long, engine: String)
    suspend fun clear()
}
