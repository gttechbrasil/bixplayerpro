package pro.bixplayer.player.data.playlist

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.util.concurrent.TimeUnit
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber

/** Credentials and base URL extracted from an Xtream `get.php` link. */
data class XtreamCredentials(
    val baseUrl: HttpUrl,
    val username: String,
    val password: String,
) {
    companion object {
        /**
         * Parses `http://host:port/get.php?username=U&password=P&type=m3u_plus`.
         * Returns null when the link is not an Xtream one, which means it is a plain M3U.
         */
        fun from(url: String): XtreamCredentials? {
            val parsed = url.trim().toHttpUrlOrNull() ?: return null
            val user = parsed.queryParameter("username") ?: return null
            val pass = parsed.queryParameter("password") ?: return null
            if (user.isBlank() || pass.isBlank()) return null
            val base = HttpUrl.Builder()
                .scheme(parsed.scheme)
                .host(parsed.host)
                .port(parsed.port)
                .build()
            return XtreamCredentials(base, user, pass)
        }
    }
}

@JsonClass(generateAdapter = true)
data class XtreamUserInfo(
    @Json(name = "auth") val auth: Int = 0,
    @Json(name = "status") val status: String? = null,
    @Json(name = "exp_date") val expDate: String? = null,
    @Json(name = "max_connections") val maxConnections: String? = null,
)

@JsonClass(generateAdapter = true)
data class XtreamLoginResponse(
    @Json(name = "user_info") val userInfo: XtreamUserInfo? = null,
)

@JsonClass(generateAdapter = true)
data class XtreamCategory(
    @Json(name = "category_id") val categoryId: String? = null,
    @Json(name = "category_name") val categoryName: String? = null,
)

@JsonClass(generateAdapter = true)
data class XtreamLiveStream(
    @Json(name = "stream_id") val streamId: Long? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "stream_icon") val streamIcon: String? = null,
    @Json(name = "category_id") val categoryId: String? = null,
    @Json(name = "epg_channel_id") val epgChannelId: String? = null,
    @Json(name = "num") val num: Int? = null,
    @Json(name = "container_extension") val containerExtension: String? = null,
)

class XtreamException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Minimal Xtream Codes client: only what live TV needs in the M3. Movies, series and EPG
 * arrive in the M4 and reuse this same client.
 *
 * The base URL is per playlist, so the client takes credentials on every call instead of being
 * built by DI with a fixed host.
 */
class XtreamClient(
    private val client: OkHttpClient,
    moshi: Moshi,
) {
    private val loginAdapter = moshi.adapter(XtreamLoginResponse::class.java)
    private val categoriesAdapter =
        moshi.adapter<List<XtreamCategory>>(
            Types.newParameterizedType(List::class.java, XtreamCategory::class.java)
        )
    private val streamsAdapter =
        moshi.adapter<List<XtreamLiveStream>>(
            Types.newParameterizedType(List::class.java, XtreamLiveStream::class.java)
        )

    /** Returns the account info, or throws [XtreamException] when the server rejects it. */
    fun login(credentials: XtreamCredentials): XtreamUserInfo {
        val body = get(credentials, emptyMap())
        val parsed = runCatching { loginAdapter.fromJson(body) }.getOrNull()
            ?: throw XtreamException("Resposta inválida do servidor da playlist.")
        val info = parsed.userInfo ?: throw XtreamException("Servidor não retornou os dados da conta.")
        if (info.auth == 0 || info.status.equals("Disabled", ignoreCase = true)) {
            throw XtreamException("Usuário ou senha da playlist recusados pelo servidor.")
        }
        return info
    }

    fun liveCategories(credentials: XtreamCredentials): List<XtreamCategory> {
        val body = get(credentials, mapOf("action" to "get_live_categories"))
        return runCatching { categoriesAdapter.fromJson(body) }.getOrNull().orEmpty()
            .filter { !it.categoryId.isNullOrBlank() }
    }

    fun liveStreams(credentials: XtreamCredentials, categoryId: String? = null): List<XtreamLiveStream> {
        val params = buildMap {
            put("action", "get_live_streams")
            if (!categoryId.isNullOrBlank()) put("category_id", categoryId)
        }
        val body = get(credentials, params)
        return runCatching { streamsAdapter.fromJson(body) }.getOrNull().orEmpty()
            .filter { it.streamId != null && !it.name.isNullOrBlank() }
    }

    /**
     * Playback URL of a live stream: `http://host:port/live/user/pass/<id>.<ext>`.
     * `ts` is the safe default; providers that only serve HLS answer a redirect to the m3u8.
     */
    fun liveStreamUrl(
        credentials: XtreamCredentials,
        streamId: Long,
        extension: String = "ts",
    ): String = credentials.baseUrl.newBuilder()
        .addPathSegment("live")
        .addPathSegment(credentials.username)
        .addPathSegment(credentials.password)
        .addPathSegment("$streamId.${extension.ifBlank { "ts" }}")
        .build()
        .toString()

    private fun get(credentials: XtreamCredentials, params: Map<String, String>): String {
        val url = credentials.baseUrl.newBuilder()
            .addPathSegment("player_api.php")
            .addQueryParameter("username", credentials.username)
            .addQueryParameter("password", credentials.password)
            .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()

        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        return try {
            client.newBuilder()
                .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
                .newCall(request)
                .execute()
                .use { response ->
                    if (!response.isSuccessful) {
                        throw XtreamException("Servidor da playlist respondeu ${response.code}.")
                    }
                    response.body?.string().orEmpty()
                }
        } catch (error: XtreamException) {
            throw error
        } catch (error: Exception) {
            // Never log the URL: it carries the subscriber's credentials.
            Timber.w(error, "xtream request failed for host %s", credentials.baseUrl.host)
            throw XtreamException("Não foi possível falar com o servidor da playlist.", error)
        }
    }

    companion object {
        const val TIMEOUT_SECONDS = 30L

        /** Some providers block unknown agents; this is the one the reference apps use. */
        const val USER_AGENT = "smart-tv"
    }
}
