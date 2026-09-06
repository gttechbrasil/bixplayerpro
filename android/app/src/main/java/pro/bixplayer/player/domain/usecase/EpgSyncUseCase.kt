package pro.bixplayer.player.domain.usecase

import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import pro.bixplayer.player.data.db.EpgDao
import pro.bixplayer.player.data.db.EpgProgramEntity
import pro.bixplayer.player.data.db.PlaylistSyncDao
import pro.bixplayer.player.data.epg.XmltvParser
import pro.bixplayer.player.data.playlist.XtreamClient
import pro.bixplayer.player.player.UrlRedactor
import timber.log.Timber

/**
 * Downloads the XMLTV guide of a playlist and keeps a sliding window of it in Room.
 *
 * The source is whatever the playlist sync recorded: `xmltv.php` for Xtream, the `url-tvg`
 * header for M3U. Only programmes inside −6 h..+48 h are stored; older ones are trimmed on
 * every run so the table never grows past a couple of days.
 */
@Singleton
class EpgSyncUseCase @Inject constructor(
    private val httpClient: OkHttpClient,
    private val epgDao: EpgDao,
    private val syncDao: PlaylistSyncDao,
    private val io: CoroutineDispatcher,
) {
    sealed interface Result {
        data class Success(val programmes: Int) : Result
        data object NoSource : Result
        data class Failure(val message: String) : Result
    }

    suspend fun sync(playlistId: Long, force: Boolean = false): Result = withContext(io) {
        val record = syncDao.get(playlistId) ?: return@withContext Result.NoSource
        val url = record.epgUrl ?: return@withContext Result.NoSource
        val now = System.currentTimeMillis()
        if (!force && record.epgSyncedAt != null && now - record.epgSyncedAt < MIN_INTERVAL_MS) {
            return@withContext Result.Success(epgDao.countByPlaylist(playlistId))
        }
        val from = now - PAST_WINDOW_MS
        val to = now + FUTURE_WINDOW_MS

        try {
            val request = Request.Builder().url(url).header("User-Agent", XtreamClient.USER_AGENT).build()
            val client = httpClient.newBuilder()
                .connectTimeout(XtreamClient.TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
            val batch = ArrayList<EpgProgramEntity>(BATCH)
            var total = 0
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext Result.Failure("O servidor do guia respondeu ${response.code}.")
                // Replace atomically enough for the UI: old rows go first, new ones stream in.
                epgDao.deleteByPlaylist(playlistId)
                response.body.byteStream().use { stream ->
                    XmltvParser.parse(stream, from, to) { programme ->
                        batch.add(
                            EpgProgramEntity(
                                playlistId = playlistId,
                                channelEpgId = programme.channelId,
                                startAt = programme.startAt,
                                endAt = programme.endAt,
                                title = programme.title,
                                description = programme.description,
                            ),
                        )
                        if (batch.size >= BATCH) {
                            kotlinx.coroutines.runBlocking { epgDao.insertAll(batch) }
                            total += batch.size
                            batch.clear()
                        }
                        total + batch.size < MAX_PROGRAMMES
                    }
                }
            }
            if (batch.isNotEmpty()) {
                epgDao.insertAll(batch)
                total += batch.size
            }
            epgDao.trim(playlistId, from, to)
            syncDao.markEpgSynced(playlistId, now)
            Timber.i("epg %d: %d programas de %s", playlistId, total, UrlRedactor.redact(url))
            Result.Success(total)
        } catch (error: Exception) {
            Timber.w(error, "epg sync failed for playlist %d", playlistId)
            Result.Failure("Não foi possível baixar o guia de programação.")
        }
    }

    companion object {
        const val PAST_WINDOW_MS = 6 * 3_600_000L
        const val FUTURE_WINDOW_MS = 48 * 3_600_000L
        const val MIN_INTERVAL_MS = 12 * 3_600_000L
        const val READ_TIMEOUT_SECONDS = 120L
        const val BATCH = 500
        const val MAX_PROGRAMMES = 400_000
    }
}
