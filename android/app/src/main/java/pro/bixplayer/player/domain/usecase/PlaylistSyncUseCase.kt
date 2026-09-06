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
import pro.bixplayer.player.data.db.ContentKind
import pro.bixplayer.player.data.db.EpisodeDao
import pro.bixplayer.player.data.db.EpisodeEntity
import pro.bixplayer.player.data.db.MovieDao
import pro.bixplayer.player.data.db.MovieEntity
import pro.bixplayer.player.data.db.PlaylistSyncDao
import pro.bixplayer.player.data.db.PlaylistSyncEntity
import pro.bixplayer.player.data.db.SeriesDao
import pro.bixplayer.player.data.db.SeriesEntity
import pro.bixplayer.player.data.db.SyncDao
import pro.bixplayer.player.data.playlist.M3uClassifier
import pro.bixplayer.player.data.playlist.M3uKind
import pro.bixplayer.player.data.playlist.M3uParser
import pro.bixplayer.player.data.playlist.M3uRemoteIds
import pro.bixplayer.player.data.playlist.XtreamClient
import pro.bixplayer.player.data.playlist.XtreamCredentials
import pro.bixplayer.player.data.playlist.XtreamException
import pro.bixplayer.player.domain.model.Playlist
import pro.bixplayer.player.domain.model.PlaylistType
import timber.log.Timber

/** Outcome of a sync, as shown in the settings screen. */
sealed interface SyncResult {
    data class Success(val channels: Int, val categories: Int, val movies: Int = 0, val series: Int = 0) : SyncResult

    /** [message] is already translated for the user. */
    data class Failure(val message: String) : SyncResult
}

/**
 * Fills the local database with everything a playlist offers: live channels, movies, series
 * (and, for M3U, their episodes) plus the categories of each.
 *
 * Everything is written in a single transaction so the UI never observes a half-synced list.
 * The whole thing runs on the IO dispatcher: a 20.000-movie list is parsed and inserted
 * without touching the main thread.
 */
@Singleton
class PlaylistSyncUseCase @Inject constructor(
    private val httpClient: OkHttpClient,
    private val xtream: XtreamClient,
    private val categoryDao: CategoryDao,
    private val channelDao: ChannelDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val episodeDao: EpisodeDao,
    private val syncDao: PlaylistSyncDao,
    private val transaction: SyncDao,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {

    suspend fun sync(playlist: Playlist): SyncResult = withContext(io) {
        try {
            val started = System.currentTimeMillis()
            val data = when (playlist.type) {
                PlaylistType.XTREAM -> loadXtream(playlist)
                PlaylistType.M3U -> loadM3u(playlist)
            }
            if (data.channels.isEmpty() && data.movies.isEmpty() && data.series.isEmpty()) {
                val message = "A playlist não retornou nenhum conteúdo."
                syncDao.upsert(failureRecord(playlist.id, message))
                return@withContext SyncResult.Failure(message)
            }
            persist(playlist.id, data)
            Timber.i(
                "sync %d: %d canais, %d filmes, %d séries, %d episódios em %d ms",
                playlist.id, data.channels.size, data.movies.size, data.series.size, data.episodes.size,
                System.currentTimeMillis() - started,
            )
            SyncResult.Success(
                channels = data.channels.size,
                categories = data.categories.size,
                movies = data.movies.size,
                series = data.series.size,
            )
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

    internal data class PlaylistData(
        val categories: List<CategoryEntity>,
        val channels: List<ChannelEntity>,
        val movies: List<MovieEntity> = emptyList(),
        val series: List<SeriesEntity> = emptyList(),
        val episodes: List<EpisodeEntity> = emptyList(),
        val epgUrl: String? = null,
    )

    private fun loadXtream(playlist: Playlist): PlaylistData {
        val credentials = XtreamCredentials.from(playlist.url)
            ?: throw XtreamException("A URL da playlist não tem usuário e senha.")
        xtream.login(credentials)

        val liveCategories = xtream.liveCategories(credentials)
        val streams = xtream.liveStreams(credentials)
        val channels = streams.mapIndexed { index, stream ->
            ChannelEntity(
                playlistId = playlist.id,
                remoteId = stream.streamId.toString(),
                name = stream.name.orEmpty(),
                streamUrl = xtream.liveStreamUrl(credentials, stream.streamId!!, stream.containerExtension.orEmpty()),
                logoUrl = stream.streamIcon?.takeIf { it.isNotBlank() },
                categoryRemoteId = stream.categoryId,
                number = stream.num ?: (index + 1),
                epgChannelId = stream.epgChannelId?.takeIf { it.isNotBlank() },
                position = index,
            )
        }

        // Movies and series are optional on a panel; a failure there must not kill live TV.
        val vodCategories = runCatching { xtream.vodCategories(credentials) }.getOrDefault(emptyList())
        val vod = runCatching { xtream.vodStreams(credentials) }.getOrDefault(emptyList())
        val movies = vod.mapIndexed { index, item ->
            MovieEntity(
                playlistId = playlist.id,
                remoteId = item.streamId.toString(),
                name = item.name.orEmpty(),
                streamUrl = xtream.vodStreamUrl(credentials, item.streamId!!, item.containerExtension),
                posterUrl = item.streamIcon?.takeIf { it.isNotBlank() },
                categoryRemoteId = item.categoryId,
                addedAt = XtreamClient.long(item.added) ?: 0L,
                year = XtreamClient.text(item.year),
                rating = XtreamClient.text(item.rating),
                position = index,
            )
        }

        val seriesCategories = runCatching { xtream.seriesCategories(credentials) }.getOrDefault(emptyList())
        val shows = runCatching { xtream.series(credentials) }.getOrDefault(emptyList())
        val series = shows.mapIndexed { index, item ->
            SeriesEntity(
                playlistId = playlist.id,
                remoteId = item.seriesId.toString(),
                name = item.name.orEmpty(),
                coverUrl = item.cover?.takeIf { it.isNotBlank() },
                categoryRemoteId = item.categoryId,
                addedAt = XtreamClient.long(item.lastModified) ?: 0L,
                year = XtreamClient.text(item.year) ?: (item.releaseDate ?: item.releaseDateAlt)?.take(4),
                rating = XtreamClient.text(item.rating),
                plot = item.plot?.takeIf { it.isNotBlank() },
                actors = item.cast?.takeIf { it.isNotBlank() },
                genre = item.genre?.takeIf { it.isNotBlank() },
                position = index,
            )
        }

        val categories = buildList {
            addAll(toCategories(playlist.id, ContentKind.LIVE, liveCategories) { id -> channels.count { it.categoryRemoteId == id } })
            addAll(toCategories(playlist.id, ContentKind.MOVIE, vodCategories) { id -> movies.count { it.categoryRemoteId == id } })
            addAll(toCategories(playlist.id, ContentKind.SERIES, seriesCategories) { id -> series.count { it.categoryRemoteId == id } })
        }
        return PlaylistData(categories, channels, movies, series, epgUrl = xtream.xmltvUrl(credentials))
    }

    private fun toCategories(
        playlistId: Long,
        kind: String,
        source: List<pro.bixplayer.player.data.playlist.XtreamCategory>,
        count: (String) -> Int,
    ): List<CategoryEntity> {
        val counts = HashMap<String, Int>()
        return source.mapIndexed { index, category ->
            val id = category.categoryId.orEmpty()
            CategoryEntity(
                playlistId = playlistId,
                kind = kind,
                remoteId = id,
                name = category.categoryName?.takeIf { it.isNotBlank() } ?: DEFAULT_GROUP,
                position = index,
                channelCount = counts.getOrPut(id) { count(id) },
            )
        }
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

        val collector = M3uCollector(playlist.id)
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw XtreamException("O servidor da playlist respondeu ${response.code}.")
            }
            response.body.byteStream().use { stream ->
                M3uParser.parse(stream, onHeader = { collector.epgUrl = it.epgUrl }) { entry ->
                    collector.add(entry)
                    collector.total < MAX_ENTRIES
                }
            }
        }
        return collector.build()
    }

    /**
     * Routes M3U entries into channels, movies and series/episodes while the file streams by.
     * Kept as a class (not a lambda with six lists) so it can be unit-tested on its own.
     */
    internal class M3uCollector(private val playlistId: Long) {
        var epgUrl: String? = null
        var total = 0
            private set

        private val ids = M3uRemoteIds()
        private val channels = ArrayList<ChannelEntity>()
        private val movies = ArrayList<MovieEntity>()
        private val episodes = ArrayList<EpisodeEntity>()
        private val seriesByKey = LinkedHashMap<String, SeriesEntity>()
        private val groups = mapOf(
            ContentKind.LIVE to LinkedHashMap<String, Int>(),
            ContentKind.MOVIE to LinkedHashMap<String, Int>(),
            ContentKind.SERIES to LinkedHashMap<String, Int>(),
        )
        private val counts = HashMap<Pair<String, String>, Int>()

        fun add(entry: pro.bixplayer.player.data.playlist.M3uEntry) {
            total++
            val group = entry.groupTitle ?: DEFAULT_GROUP
            when (M3uClassifier.classify(entry)) {
                M3uKind.LIVE -> {
                    register(ContentKind.LIVE, group)
                    channels.add(
                        ChannelEntity(
                            playlistId = playlistId,
                            remoteId = ids.next(entry.name, entry.url),
                            name = entry.name,
                            streamUrl = entry.url,
                            logoUrl = entry.logoUrl,
                            categoryRemoteId = group,
                            number = entry.number ?: (channels.size + 1),
                            epgChannelId = entry.tvgId,
                            position = channels.size,
                        ),
                    )
                }

                M3uKind.MOVIE -> {
                    register(ContentKind.MOVIE, group)
                    movies.add(
                        MovieEntity(
                            playlistId = playlistId,
                            remoteId = ids.next(entry.name, entry.url),
                            name = entry.name,
                            streamUrl = entry.url,
                            posterUrl = entry.logoUrl,
                            categoryRemoteId = group,
                            // M3U has no dates: file order is the best "recent" we have.
                            addedAt = (Int.MAX_VALUE - movies.size).toLong(),
                            position = movies.size,
                        ),
                    )
                }

                M3uKind.SERIES -> {
                    val parsed = M3uClassifier.parseEpisode(entry.name) ?: return
                    val seriesKey = parsed.series.lowercase()
                    val show = seriesByKey.getOrPut(seriesKey) {
                        register(ContentKind.SERIES, group)
                        SeriesEntity(
                            playlistId = playlistId,
                            remoteId = "s:" + seriesKey.hashCode().toUInt().toString(16),
                            name = parsed.series,
                            coverUrl = entry.logoUrl,
                            categoryRemoteId = group,
                            addedAt = (Int.MAX_VALUE - seriesByKey.size).toLong(),
                            position = seriesByKey.size,
                            episodesFetchedAt = 0L,
                        )
                    }
                    episodes.add(
                        EpisodeEntity(
                            playlistId = playlistId,
                            seriesRemoteId = show.remoteId,
                            remoteId = ids.next(entry.name, entry.url),
                            season = parsed.season,
                            episode = parsed.episode,
                            title = parsed.title ?: "Episódio ${parsed.episode}",
                            streamUrl = entry.url,
                            thumbUrl = entry.logoUrl,
                        ),
                    )
                }
            }
        }

        private fun register(kind: String, group: String) {
            groups.getValue(kind).getOrPut(group) { groups.getValue(kind).size }
            counts.merge(kind to group, 1, Int::plus)
        }

        fun build(): PlaylistData {
            val categories = groups.flatMap { (kind, order) ->
                order.map { (name, position) ->
                    CategoryEntity(
                        playlistId = playlistId,
                        kind = kind,
                        remoteId = name,
                        name = name,
                        position = position,
                        channelCount = counts[kind to name] ?: 0,
                    )
                }
            }
            return PlaylistData(categories, channels, movies, seriesByKey.values.toList(), episodes, epgUrl)
        }
    }

    /** Replaces the playlist content atomically. */
    private suspend fun persist(playlistId: Long, data: PlaylistData) {
        val previous = syncDao.get(playlistId)
        transaction.replacePlaylist(
            playlistId = playlistId,
            categories = data.categories,
            channels = data.channels,
            movies = data.movies,
            series = data.series,
            episodes = data.episodes,
            record = PlaylistSyncEntity(
                playlistId = playlistId,
                lastSyncAt = System.currentTimeMillis(),
                channelCount = data.channels.size,
                categoryCount = data.categories.count { it.kind == ContentKind.LIVE },
                movieCount = data.movies.size,
                seriesCount = data.series.size,
                epgUrl = data.epgUrl,
                epgSyncedAt = previous?.epgSyncedAt,
                lastError = null,
            ),
            categoryDao = categoryDao,
            channelDao = channelDao,
            movieDao = movieDao,
            seriesDao = seriesDao,
            episodeDao = episodeDao,
            syncDao = syncDao,
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
        /** Guard against a runaway list; channels + movies + episodes together. */
        const val MAX_ENTRIES = 100_000

        const val DEFAULT_GROUP = "Sem categoria"
    }
}
