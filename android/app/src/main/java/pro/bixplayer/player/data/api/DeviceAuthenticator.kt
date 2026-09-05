package pro.bixplayer.player.data.api

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import pro.bixplayer.player.data.api.dto.RegisterRequest
import pro.bixplayer.player.data.datastore.DeviceStore
import timber.log.Timber

/**
 * Adds the opaque device token to every call and heals a dead token.
 *
 * The platform rotates the token whenever the app re-registers, so a 401 is not fatal: the
 * device registers again with the same `device_id` (the endpoint is idempotent, it returns the
 * same MAC) and the original request is replayed exactly once.
 */
class DeviceAuthInterceptor(
    private val prefs: DeviceStore,
    private val registrar: DeviceRegistrar,
) : Interceptor {

    private val mutex = Mutex()

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        // The register call is the one request that must not carry a token.
        if (original.url.encodedPath.endsWith("/device/register")) {
            return chain.proceed(original)
        }

        val token = runBlocking { prefs.currentToken() }
        val response = chain.proceed(original.withToken(token))
        if (response.code != 401) return response

        Timber.w("device token rejected, re-registering")
        response.close()
        val fresh = runBlocking {
            mutex.withLock {
                // Another thread may have refreshed it while this one waited.
                val latest = prefs.currentToken()
                if (latest != null && latest != token) latest else registrar.reRegister()
            }
        }
        if (fresh == null) {
            Timber.e("re-registration failed; giving up on this request")
            return chain.proceed(original.withToken(token))
        }
        return chain.proceed(original.withToken(fresh))
    }

    private fun Request.withToken(token: String?): Request =
        if (token.isNullOrBlank()) this
        else newBuilder().header("Authorization", "Bearer $token").build()
}

/**
 * Registers the device and persists the credentials. Kept apart from the interceptor so that
 * it can be used directly on first boot and exercised in tests.
 */
class DeviceRegistrar(
    private val apiProvider: () -> DeviceApi,
    private val prefs: DeviceStore,
    private val deviceIdProvider: () -> String,
    private val appType: String,
    private val appVersion: String,
) {
    suspend fun register(): RegistrationResult {
        val response = apiProvider().register(
            RegisterRequest(
                deviceId = deviceIdProvider(),
                appType = appType,
                appVersion = appVersion,
            )
        )
        prefs.saveCredentials(response.token, response.macAddress)
        return RegistrationResult(response.macAddress, response.token)
    }

    /** Returns the new token, or null when the platform is unreachable. */
    suspend fun reRegister(): String? = runCatching { register().token }
        .onFailure { Timber.e(it, "re-registration failed") }
        .getOrNull()
}

data class RegistrationResult(val macAddress: String, val token: String)
