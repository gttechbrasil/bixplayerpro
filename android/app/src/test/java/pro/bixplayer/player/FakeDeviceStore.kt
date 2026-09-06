package pro.bixplayer.player

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import pro.bixplayer.player.data.datastore.DeviceStore

/** In-memory [DeviceStore] so the data layer can be tested without Android. */
class FakeDeviceStore(
    initialToken: String? = null,
    initialMac: String? = null,
    initialConfigJson: String? = null,
) : DeviceStore {

    private val _token = MutableStateFlow(initialToken)
    private val _mac = MutableStateFlow(initialMac)
    private val _config = MutableStateFlow(initialConfigJson)
    private val _activePlaylist = MutableStateFlow<Long?>(null)
    private val _refreshHours = MutableStateFlow(6L)
    private val _language = MutableStateFlow("pt-BR")
    private val _pin = MutableStateFlow<String?>(null)
    private val _layout = MutableStateFlow<String?>(null)
    private val _engines = MutableStateFlow<Map<Long, String>>(emptyMap())

    var saveCredentialsCalls = 0
        private set
    var savedConfigJson: String? = initialConfigJson
        private set

    override val token: Flow<String?> = _token
    override val macAddress: Flow<String?> = _mac
    override val configJson: Flow<String?> = _config
    override val activePlaylistId: Flow<Long?> = _activePlaylist
    override val refreshHours: Flow<Long> = _refreshHours
    override val language: Flow<String> = _language
    override val pin: Flow<String?> = _pin
    override val layoutOverride: Flow<String?> = _layout

    override suspend fun currentToken(): String? = _token.value
    override suspend fun currentMacAddress(): String? = _mac.value
    override suspend fun currentConfigJson(): String? = _config.value
    override suspend fun currentActivePlaylistId(): Long? = _activePlaylist.value
    override suspend fun currentRefreshHours(): Long = _refreshHours.value
    override suspend fun currentPin(): String? = _pin.value
    override suspend fun currentLayoutOverride(): String? = _layout.value
    override fun playerEngine(playlistId: Long): Flow<String> = kotlinx.coroutines.flow.map(_engines) { it[playlistId] ?: "auto" }
    override suspend fun currentPlayerEngine(playlistId: Long): String = _engines.value[playlistId] ?: "auto"

    override suspend fun saveCredentials(token: String, macAddress: String) {
        saveCredentialsCalls++
        _token.value = token
        _mac.value = macAddress
    }

    override suspend fun saveConfig(json: String, updatedAt: Long) {
        savedConfigJson = json
        _config.value = json
    }

    override suspend fun setActivePlaylistId(id: Long?) {
        _activePlaylist.value = id
    }

    override suspend fun setRefreshHours(hours: Long) {
        _refreshHours.value = hours
    }

    override suspend fun setLanguage(tag: String) {
        _language.value = tag
    }

    override suspend fun setPin(pin: String?) {
        _pin.value = pin
    }

    override suspend fun setLayoutOverride(layout: String?) {
        _layout.value = layout
    }

    override suspend fun setPlayerEngine(playlistId: Long, engine: String) {
        _engines.value = _engines.value + (playlistId to engine)
    }

    override suspend fun clear() {
        _token.value = null
        _mac.value = null
        _config.value = null
        _activePlaylist.value = null
    }
}
