package pro.bixplayer.player.data.repository

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pro.bixplayer.player.data.api.DeviceApi
import pro.bixplayer.player.data.api.DeviceRegistrar
import pro.bixplayer.player.data.api.dto.ApiErrorEnvelope
import pro.bixplayer.player.data.api.dto.DeviceConfigDto
import pro.bixplayer.player.data.api.dto.PlaylistCreateRequest
import pro.bixplayer.player.data.datastore.DeviceStore
import pro.bixplayer.player.domain.model.AppConfig
import pro.bixplayer.player.domain.model.ConfigState
import retrofit2.HttpException
import timber.log.Timber

/**
 * Owns the device configuration: fetches it, caches it and exposes it as state.
 *
 * The contract the rest of the app relies on: [state] never goes back to Loading once it has
 * produced a config, and a failed refresh keeps serving the cached value (marked
 * `fromCache = true`) so the app still opens without network.
 */
@Singleton
class ConfigRepository @Inject constructor(
    private val api: DeviceApi,
    private val prefs: DeviceStore,
    private val registrar: DeviceRegistrar,
    private val configAdapter: JsonAdapter<DeviceConfigDto>,
    private val messages: ErrorMessages,
) {
    private val _state = MutableStateFlow<ConfigState>(ConfigState.Loading)
    val state: StateFlow<ConfigState> = _state.asStateFlow()

    /**
     * Fetches the configuration, registering the device first when there are no credentials.
     * Returns the config in use, whether it came from the network or the cache.
     */
    suspend fun refresh(): ConfigState {
        if (prefs.currentToken().isNullOrBlank()) {
            val registered = runCatching { registrar.register() }
                .onFailure { Timber.w(it, "initial registration failed") }
            if (registered.isFailure) {
                return fallbackToCache(registered.exceptionOrNull())
            }
        }

        return try {
            val dto = api.config()
            prefs.saveConfig(configAdapter.toJson(dto))
            val config = AppConfig.from(dto)
            reconcileActivePlaylist(config)
            ConfigState.Ready(config).also { _state.value = it }
        } catch (error: Throwable) {
            Timber.w(error, "config refresh failed")
            fallbackToCache(error)
        }
    }

    /** Reads the cached configuration without touching the network. */
    suspend fun cached(): AppConfig? {
        val json = prefs.currentConfigJson() ?: return null
        return runCatching { configAdapter.fromJson(json) }
            .onFailure { Timber.w(it, "cached config is corrupt; discarding") }
            .getOrNull()
            ?.let { AppConfig.from(it, fromCache = true) }
    }

    /** Loads the cache into [state] on cold start so the UI has something before the network. */
    suspend fun primeFromCache(): AppConfig? = cached()?.also {
        if (_state.value is ConfigState.Loading) _state.value = ConfigState.Ready(it)
    }

    suspend fun addPlaylist(name: String, url: String): Result<Unit> = runCatching {
        api.addPlaylist(PlaylistCreateRequest(name = name.trim(), url = url.trim()))
        refresh()
        Unit
    }.onFailure { Timber.w(it, "addPlaylist failed") }

    suspend fun deletePlaylist(id: Long): Result<Unit> = runCatching {
        api.deletePlaylist(id)
        refresh()
        Unit
    }.onFailure { Timber.w(it, "deletePlaylist failed") }

    suspend fun setActivePlaylist(id: Long) = prefs.setActivePlaylistId(id)

    /** Turns a failure from this repository into a message the user can act on. */
    fun messageFor(error: Throwable?): String = messages.forThrowable(error)

    /** Keeps the stored active playlist pointing at something that still exists. */
    private suspend fun reconcileActivePlaylist(config: AppConfig) {
        val current = prefs.currentActivePlaylistId()
        val stillThere = config.playlists.any { it.id == current }
        if (!stillThere) {
            prefs.setActivePlaylistId(config.playlists.firstOrNull()?.id)
        }
    }

    private suspend fun fallbackToCache(error: Throwable?): ConfigState {
        val cached = cached()
        val next = if (cached != null) {
            ConfigState.Ready(cached)
        } else {
            ConfigState.Failed(messages.forThrowable(error), error)
        }
        _state.value = next
        return next
    }
}

/** Translates transport failures into messages the user can act on (always in Portuguese). */
interface ErrorMessages {
    fun forThrowable(error: Throwable?): String
}

/**
 * Default implementation backed by string resources.
 *
 * The API already answers in Portuguese with `{"detail": {"message": ...}}`, and that text is
 * far more useful than a generic "server error" — for instance "Dispositivo não cadastrado.
 * Informe o MAC ao seu revendedor." So it is preferred whenever the body carries one.
 */
class DefaultErrorMessages(
    private val network: String,
    private val server: String,
    private val unknown: String,
    private val moshi: Moshi = Moshi.Builder().build(),
) : ErrorMessages {

    private val envelopeAdapter by lazy { moshi.adapter(ApiErrorEnvelope::class.java) }

    override fun forThrowable(error: Throwable?): String = when (error) {
        is IOException -> network
        is HttpException -> serverMessage(error) ?: server
        null -> unknown
        else -> unknown
    }

    private fun serverMessage(error: HttpException): String? = runCatching {
        error.response()?.errorBody()?.string()
            ?.let { envelopeAdapter.fromJson(it) }
            ?.detail?.message
            ?.takeIf { it.isNotBlank() }
    }.getOrNull()
}
