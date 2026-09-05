package pro.bixplayer.player.domain.usecase

import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import pro.bixplayer.player.data.db.CategoryDao
import pro.bixplayer.player.data.db.CategoryEntity
import pro.bixplayer.player.data.db.ChannelDao
import pro.bixplayer.player.data.db.ChannelEntity
import pro.bixplayer.player.data.db.PlaylistSyncDao
import pro.bixplayer.player.data.db.PlaylistSyncEntity
import pro.bixplayer.player.data.playlist.M3uParser
import pro.bixplayer.player.data.playlist.XtreamClient
import pro.bixplayer.player.data.playlist.XtreamCredentials
import pro.bixplayer.player.data.playlist.XtreamException
import pro.bixplayer.player.domain.model.Playlist
import pro.bixplayer.player.domain.model.PlaylistType
import timber.log.Timber

/** Outcome of a sync, as shown in the settings screen. */
sealed interface SyncResult {
    data class Success(val channels: Int, val categories: Int) : SyncResult

    /** [message] is already translated for the user. */
    data class Failure(val message: String) : SyncResult
}

/**
 * Fills the local database with the channels and categories of one playlist.
 *
 * Everything is written in a single transaction so the UI never observes a half-synced list,
 * and channels are inserted in batches because a 5.000-row statement would blow past SQLite's
 * variable limit.
 */
@Singleton
class PlaylistSyncUseCase @Inject constructor(
    private val httpClient: OkHttpClient,
    private val xtream: XtreamClient,
    private val categoryDao: CategoryDao,
    private val channelDao: ChannelDao,
    private val syncDao: PlaylistSyncDao,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun sync(playlist: Playlist): SyncResult = withContext(io) {
        try {
            val data = when (playlist.type) {
                PlaylistType.XTREAM -> loadXtream(playlist)
                PlaylistType.M3U -> loadM3u(playlist)
            }
            if (data.channels.isEmpty()) {
                val message = "A playlist não retornou nenhum canal."
                syncDao.upsert(failureRecord(playlist.id, message))
                return@withContext SyncResult.Failure(message)
            }
            persist(playlist.id, data)
            SyncResult.Success(channels = data.channels.size, categories = data.categories.size)
        } catch (error: XtreamException) {
            Timber.w(error, "xtream sync failed for playlist %d", playlist.id)
            syncDao.upsert(failureRecord(playlist.id, error.message.orEmpty()))
            SyncResult.Failure(error.message ?: "Não foi possível sincronizar a playlist.")
        } catch (error: Exception) {
            Timber.w(error, "sync failed for playlist %d", playlist.id)
            val message = "Não foi possível baixar a playlist. Verifique a URL e a conexão."
            syncDao.upsert(failureRecord(playlist.id, message))
            SyncResult.Failure(message)
        }
    }

    private data class PlaylistData(
        val categories: List<CategoryEntity>,
        val channels: List<ChannelEntity>,
    )

    private fun loadXtream(playlist: Playlist): PlaylistData {
        val credentials = XtreamCredentials.from(playlist.url)
            ?: throw XtreamException("A URL da playlist não tem usuário e senha.")
        xtream.login(credentials)

        val categories = xtream.liveCategories(credentials)
        val streams = xtream.liveStreams(credentials)

        val categoryEntities = categories.mapIndexed { index, category ->
            CategoryEntity(
                playlistId = playlist.id,
                remoteId = category.categoryId.orEmpty(),
                name = category.categoryName?.takeIf { it.isNotBlank() } ?: "Sem categoria",
                position = index,
                channelCount = streams.count { it.categoryId == category.categoryId },
            )
        }

        val channelEntities = streams.mapIndexed { index, stream ->
            ChannelEntity(
                playlistId = playlist.id,
                remoteId = stream.streamId.toString(),
                name = stream.name.orEmpty(),
                streamUrl = xtream.liveStreamUrl(
                    credentials,
                    stream.streamId!!,
                    stream.containerExtension.orEmpty(),
                ),
                logoUrl = stream.streamIcon?.takeIf { it.isNotBlank() },
                categoryRemoteId = stream.categoryId,
                number = stream.num ?: (index + 1),
                epgChannelId = stream.epgChannelId?.takeIf { it.isNotBlank() },
                position = index,
            )
        }
        return PlaylistData(categoryEntities, channelEntities)
    }

    private fun loadM3u(playlist: Playlist): PlaylistData {
        val request = Request.Builder()
            .url(playlist.url)
            .header("User-Agent", XtreamClient.USER_AGENT)
            .build()
        val client = httpClient.newBuilder()
            .connectTimeout(XtreamClient.TIMEOUT_SECONDS, TimeUnit.SECONDS)
            // A big list takes a while to stream; the read timeout applies between chunks.
            .readTimeout(XtreamClient.TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .build()

        val channels = ArrayList<ChannelEntity>()
        val categoryOrder = LinkedHashMap<String, Int>()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw XtreamException("O servidor da playlist respondeu ${response.code}.")
            }
            val body = response.body ?: throw XtreamException("A playlist veio vazia.")
            body.byteStream().use { stream ->
                var index = 0
                M3uParser.parse(stream) { entry ->
                    val group = entry.groupTitle ?: DEFAULT_GROUP
                    categoryOrder.getOrPut(group) { categoryOrder.size }
                    channels.add(
                        ChannelEntity(
                            playlistId = playlist.id,
                            // M3U has no ids: the URL is what uniquely identifies the channel.
                            remoteId = entry.url.hashCode().toString(),
                            name = entry.name,
                            streamUrl = entry.url,
                            logoUrl = entry.logoUrl,
                            categoryRemoteId = group,
                            number = entry.number ?: (index + 1),
                            epgChannelId = entry.tvgId,
                            position = index,
                        )
                    )
                    index++
                    index < MAX_CHANNELS
                }
            }
        }

        val categories = categoryOrder.map { (name, position) ->
            CategoryEntity(
                playlistId = playlist.id,
                remoteId = name,
                name = name,
                position = position,
                channelCount = channels.count { it.categoryRemoteId == name },
            )
        }
        return PlaylistData(categories, channels)
    }

    /** Replaces the playlist content atomically. */
    private suspend fun persist(playlistId: Long, data: PlaylistData) {
        categoryDao.deleteByPlaylist(playlistId)
        channelDao.deleteByPlaylist(playlistId)
        data.categories.chunked(BATCH_SIZE).forEach { categoryDao.insertAll(it) }
        data.channels.chunked(BATCH_SIZE).forEach { channelDao.insertAll(it) }
        syncDao.upsert(
            PlaylistSyncEntity(
                playlistId = playlistId,
                lastSyncAt = System.currentTimeMillis(),
                channelCount = data.channels.size,
                categoryCount = data.categories.size,
                lastError = null,
            )
        )
    }

    private fun failureRecord(playlistId: Long, message: String) = PlaylistSyncEntity(
        playlistId = playlistId,
        lastSyncAt = System.currentTimeMillis(),
        channelCount = 0,
        categoryCount = 0,
        lastError = message,
    )

    companion object {
        const val BATCH_SIZE = 500

        /** Guard against a runaway list; well beyond the 5.000 the plan asks for. */
        const val MAX_CHANNELS = 50_000

        const val DEFAULT_GROUP = "Sem categoria"
    }
}
