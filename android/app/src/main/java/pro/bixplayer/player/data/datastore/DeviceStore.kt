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

    suspend fun currentToken(): String?
    suspend fun currentMacAddress(): String?
    suspend fun currentConfigJson(): String?
    suspend fun currentActivePlaylistId(): Long?
    suspend fun currentRefreshHours(): Long

    suspend fun saveCredentials(token: String, macAddress: String)
    suspend fun saveConfig(json: String, updatedAt: Long = System.currentTimeMillis())
    suspend fun setActivePlaylistId(id: Long?)
    suspend fun setRefreshHours(hours: Long)
    suspend fun setLanguage(tag: String)
    suspend fun clear()
}
