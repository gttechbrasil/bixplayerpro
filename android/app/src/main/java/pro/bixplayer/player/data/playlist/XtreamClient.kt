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

/**
 * Xtream panels are loose with types: `rating`, `added` and `duration_secs` arrive as numbers
 * on some and as strings on others. Those fields are typed `Any?` and normalised by hand.
 */
@JsonClass(generateAdapter = true)
data class XtreamVodStream(
    @Json(name = "stream_id") val streamId: Long? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "stream_icon") val streamIcon: String? = null,
    @Json(name = "category_id") val categoryId: String? = null,
    @Json(name = "added") val added: Any? = null,
    @Json(name = "rating") val rating: Any? = null,
    @Json(name = "year") val year: Any? = null,
    @Json(name = "container_extension") val containerExtension: String? = null,
)

@JsonClass(generateAdapter = true)
data class XtreamVodInfoDetails(
    @Json(name = "name") val name: String? = null,
    @Json(name = "plot") val plot: String? = null,
    @Json(name = "cast") val cast: String? = null,
    @Json(name = "director") val director: String? = null,
    @Json(name = "genre") val genre: String? = null,
    @Json(name = "releasedate") val releaseDate: String? = null,
    @Json(name = "release_date") val releaseDateAlt: String? = null,
    @Json(name = "duration_secs") val durationSecs: Any? = null,
    @Json(name = "duration") val duration: String? = null,
    @Json(name = "movie_image") val movieImage: String? = null,
    @Json(name = "backdrop_path") val backdropPath: Any? = null,
    @Json(name = "rating") val rating: Any? = null,
)

@JsonClass(generateAdapter = true)
data class XtreamVodInfoResponse(
    @Json(name = "info") val info: XtreamVodInfoDetails? = null,
)

@JsonClass(generateAdapter = true)
data class XtreamSeries(
    @Json(name = "series_id") val seriesId: Long? = null,
    @Json(name = "name") val name: String? = null,
    @Json(name = "cover") val cover: String? = null,
    @Json(name = "category_id") val categoryId: String? = null,
    @Json(name = "last_modified") val lastModified: Any? = null,
    @Json(name = "rating") val rating: Any? = null,
    @Json(name = "plot") val plot: String? = null,
    @Json(name = "cast") val cast: String? = null,
    @Json(name = "genre") val genre: String? = null,
    @Json(name = "releaseDate") val releaseDate: String? = null,
    @Json(name = "release_date") val releaseDateAlt: String? = null,
    @Json(name = "year") val year: Any? = null,
)

@JsonClass(generateAdapter = true)
data class XtreamEpisodeInfo(
    @Json(name = "plot") val plot: String? = null,
    @Json(name = "duration_secs") val durationSecs: Any? = null,
    @Json(name = "movie_image") val movieImage: String? = null,
)

@JsonClass(generateAdapter = true)
data class XtreamEpisode(
    @Json(name = "id") val id: Any? = null,
    @Json(name = "episode_num") val episodeNum: Any? = null,
    @Json(name = "title") val title: String? = null,
    @Json(name = "container_extension") val containerExtension: String? = null,
    @Json(name = "season") val season: Any? = null,
    @Json(name = "info") val info: XtreamEpisodeInfo? = null,
)

@JsonClass(generateAdapter = true)
data class XtreamSeriesInfoResponse(
    @Json(name = "info") val info: XtreamSeries? = null,
    /** Keyed by season number ("1", "2"…). */
    @Json(name = "episodes") val episodes: Map<String, List<XtreamEpisode>>? = null,
)

@JsonClass(generateAdapter = true)
data class XtreamEpgListing(
    @Json(name = "title") val title: String? = null,
    @Json(name = "description") val description: String? = null,
    @Json(name = "start_timestamp") val startTimestamp: Any? = null,
    @Json(name = "stop_timestamp") val stopTimestamp: Any? = null,
)

@JsonClass(generateAdapter = true)
data class XtreamShortEpgResponse(
    @Json(name = "epg_listings") val listings: List<XtreamEpgListing>? = null,
)

class XtreamException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Xtream Codes client: live, VOD, series and EPG.
 *
 * The base URL is per playlist, so the client takes credentials on every call instead of being
 * built by DI with a fixed host.
 */
class XtreamClient(
    private val client: OkHttpClient,
    moshi: Moshi,
) {
    private val loginAdapter = moshi.adapter(XtreamLoginResponse::class.java)
    private val categoriesAdapter = moshi.adapter<List<XtreamCategory>>(listOf(XtreamCategory::class.java))
    private val liveAdapter = moshi.adapter<List<XtreamLiveStream>>(listOf(XtreamLiveStream::class.java))
    private val vodAdapter = moshi.adapter<List<XtreamVodStream>>(listOf(XtreamVodStream::class.java))
    private val vodInfoAdapter = moshi.adapter(XtreamVodInfoResponse::class.java)
    private val seriesAdapter = moshi.adapter<List<XtreamSeries>>(listOf(XtreamSeries::class.java))
    private val seriesInfoAdapter = moshi.adapter(XtreamSeriesInfoResponse::class.java)
    private val shortEpgAdapter = moshi.adapter(XtreamShortEpgResponse::class.java)

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

    // ---- live -------------------------------------------------------------------------------

    fun liveCategories(credentials: XtreamCredentials): List<XtreamCategory> =
        categories(credentials, "get_live_categories")

    fun liveStreams(credentials: XtreamCredentials, categoryId: String? = null): List<XtreamLiveStream> {
        val body = get(credentials, action("get_live_streams", categoryId))
        return runCatching { liveAdapter.fromJson(body) }.getOrNull().orEmpty()
            .filter { it.streamId != null && !it.name.isNullOrBlank() }
    }

    /**
     * Playback URL of a live stream: `http://host:port/live/user/pass/<id>.<ext>`.
     * `ts` is the safe default; providers that only serve HLS answer a redirect to the m3u8.
     */
    fun liveStreamUrl(credentials: XtreamCredentials, streamId: Long, extension: String = "ts"): String =
        streamUrl(credentials, "live", streamId.toString(), extension.ifBlank { "ts" })

    // ---- VOD ---------------------------------------------------------------------------------

    fun vodCategories(credentials: XtreamCredentials): List<XtreamCategory> =
        categories(credentials, "get_vod_categories")

    fun vodStreams(credentials: XtreamCredentials, categoryId: String? = null): List<XtreamVodStream> {
        val body = get(credentials, action("get_vod_streams", categoryId))
        return runCatching { vodAdapter.fromJson(body) }.getOrNull().orEmpty()
            .filter { it.streamId != null && !it.name.isNullOrBlank() }
    }

    fun vodInfo(credentials: XtreamCredentials, streamId: Long): XtreamVodInfoDetails? {
        val body = get(credentials, mapOf("action" to "get_vod_info", "vod_id" to streamId.toString()))
        return runCatching { vodInfoAdapter.fromJson(body) }.getOrNull()?.info
    }

    fun vodStreamUrl(credentials: XtreamCredentials, streamId: Long, extension: String?): String =
        streamUrl(credentials, "movie", streamId.toString(), extension?.ifBlank { null } ?: "mp4")

    // ---- series ------------------------------------------------------------------------------

    fun seriesCategories(credentials: XtreamCredentials): List<XtreamCategory> =
        categories(credentials, "get_series_categories")

    fun series(credentials: XtreamCredentials, categoryId: String? = null): List<XtreamSeries> {
        val body = get(credentials, action("get_series", categoryId))
        return runCatching { seriesAdapter.fromJson(body) }.getOrNull().orEmpty()
            .filter { it.seriesId != null && !it.name.isNullOrBlank() }
    }

    fun seriesInfo(credentials: XtreamCredentials, seriesId: Long): XtreamSeriesInfoResponse? {
        val body = get(credentials, mapOf("action" to "get_series_info", "series_id" to seriesId.toString()))
        return runCatching { seriesInfoAdapter.fromJson(body) }.getOrNull()
    }

    fun episodeStreamUrl(credentials: XtreamCredentials, episodeId: String, extension: String?): String =
        streamUrl(credentials, "series", episodeId, extension?.ifBlank { null } ?: "mp4")

    // ---- EPG ---------------------------------------------------------------------------------

    /** Now/next for one channel; the full guide comes from [xmltvUrl]. */
    fun shortEpg(credentials: XtreamCredentials, streamId: Long, limit: Int = 4): List<XtreamEpgListing> {
        val body = get(
            credentials,
            mapOf("action" to "get_short_epg", "stream_id" to streamId.toString(), "limit" to limit.toString()),
        )
        return runCatching { shortEpgAdapter.fromJson(body) }.getOrNull()?.listings.orEmpty()
    }

    /** XMLTV export of the whole guide. */
    fun xmltvUrl(credentials: XtreamCredentials): String = credentials.baseUrl.newBuilder()
        .addPathSegment("xmltv.php")
        .addQueryParameter("username", credentials.username)
        .addQueryParameter("password", credentials.password)
        .build()
        .toString()

    // ---- plumbing ----------------------------------------------------------------------------

    private fun categories(credentials: XtreamCredentials, action: String): List<XtreamCategory> {
        val body = get(credentials, mapOf("action" to action))
        return runCatching { categoriesAdapter.fromJson(body) }.getOrNull().orEmpty()
            .filter { !it.categoryId.isNullOrBlank() }
    }

    private fun action(name: String, categoryId: String?): Map<String, String> = buildMap {
        put("action", name)
        if (!categoryId.isNullOrBlank()) put("category_id", categoryId)
    }

    private fun streamUrl(credentials: XtreamCredentials, kind: String, id: String, extension: String): String =
        credentials.baseUrl.newBuilder()
            .addPathSegment(kind)
            .addPathSegment(credentials.username)
            .addPathSegment(credentials.password)
            .addPathSegment("$id.$extension")
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
                    response.body.string()
                }
        } catch (error: XtreamException) {
            throw error
        } catch (error: Exception) {
            // Never log the URL: it carries the subscriber's credentials.
            Timber.w(error, "xtream request failed for host %s", credentials.baseUrl.host)
            throw XtreamException("Não foi possível falar com o servidor da playlist.", error)
        }
    }

    private fun <T> Moshi.adapter(type: List<Class<*>>): com.squareup.moshi.JsonAdapter<T> =
        adapter(Types.newParameterizedType(List::class.java, type.single()))

    companion object {
        const val TIMEOUT_SECONDS = 30L

        /** Some providers block unknown agents; this is the one the reference apps use. */
        const val USER_AGENT = "smart-tv"

        /** Loose Xtream scalars (number or string) as text; blank/"0"/"null" become null. */
        fun text(value: Any?): String? = when (value) {
            null -> null
            is Number -> if (value.toDouble() % 1.0 == 0.0) value.toLong().toString() else value.toString()
            else -> value.toString()
        }?.trim()?.takeIf { it.isNotEmpty() && it != "0" && !it.equals("null", ignoreCase = true) }

        fun long(value: Any?): Long? = when (value) {
            is Number -> value.toLong()
            is String -> value.trim().toDoubleOrNull()?.toLong()
            else -> null
        }

        fun int(value: Any?): Int? = long(value)?.toInt()
    }
}
