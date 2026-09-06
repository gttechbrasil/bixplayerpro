package pro.bixplayer.player.data.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE playlistId = :playlistId AND kind = :kind ORDER BY position, name")
    fun observeByPlaylist(playlistId: Long, kind: String = ContentKind.LIVE): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE playlistId = :playlistId AND kind = :kind ORDER BY position, name")
    suspend fun byPlaylist(playlistId: Long, kind: String = ContentKind.LIVE): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE playlistId = :playlistId ORDER BY kind, position, name")
    suspend fun allByPlaylist(playlistId: Long): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CategoryEntity>)

    @Query("DELETE FROM categories WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: Long)

    @Query("SELECT COUNT(*) FROM categories WHERE playlistId = :playlistId AND kind = :kind")
    suspend fun countByPlaylist(playlistId: Long, kind: String = ContentKind.LIVE): Int
}

/**
 * Channel queries take the user's category rules into account: hidden categories never show
 * up, in any list, search or zapping scope.
 */
@Dao
interface ChannelDao {
    @Query(
        """
        SELECT * FROM channels c
        WHERE c.playlistId = :playlistId
          AND (:categoryRemoteId IS NULL OR c.categoryRemoteId = :categoryRemoteId)
          AND NOT EXISTS(SELECT 1 FROM category_rules r WHERE r.playlistId = c.playlistId
                AND r.kind = 'live' AND r.remoteId = c.categoryRemoteId AND r.hidden = 1)
        ORDER BY c.position, c.name
        """
    )
    fun pagingByCategory(playlistId: Long, categoryRemoteId: String?): PagingSource<Int, ChannelEntity>

    @Query(
        """
        SELECT c.* FROM channels c
        INNER JOIN favorites f
            ON f.playlistId = c.playlistId AND f.kind = 'live' AND f.itemRemoteId = c.remoteId
        WHERE c.playlistId = :playlistId
          AND NOT EXISTS(SELECT 1 FROM category_rules r WHERE r.playlistId = c.playlistId
                AND r.kind = 'live' AND r.remoteId = c.categoryRemoteId AND r.hidden = 1)
        ORDER BY c.position, c.name
        """
    )
    fun pagingFavorites(playlistId: Long): PagingSource<Int, ChannelEntity>

    @Query(
        """
        SELECT * FROM channels c
        WHERE c.playlistId = :playlistId AND c.name LIKE '%' || :query || '%'
          AND NOT EXISTS(SELECT 1 FROM category_rules r WHERE r.playlistId = c.playlistId
                AND r.kind = 'live' AND r.remoteId = c.categoryRemoteId AND r.hidden = 1)
        ORDER BY c.position, c.name
        """
    )
    fun pagingSearch(playlistId: Long, query: String): PagingSource<Int, ChannelEntity>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND remoteId = :remoteId LIMIT 1")
    suspend fun byRemoteId(playlistId: Long, remoteId: String): ChannelEntity?

    @Query("SELECT * FROM channels WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): ChannelEntity?

    /**
     * Neighbours inside the scope the user was browsing (all / one category / favourites),
     * so zapping in the player follows the list on screen. [favoritesOnly] is an Int because
     * Room binds booleans as 0/1 and SQLite has no boolean type.
     */
    @Query(
        """
        SELECT * FROM channels c
        WHERE c.playlistId = :playlistId
          AND (:categoryRemoteId IS NULL OR c.categoryRemoteId = :categoryRemoteId)
          AND (:favoritesOnly = 0 OR EXISTS(
                SELECT 1 FROM favorites f
                WHERE f.playlistId = c.playlistId AND f.kind = 'live' AND f.itemRemoteId = c.remoteId))
          AND NOT EXISTS(SELECT 1 FROM category_rules r WHERE r.playlistId = c.playlistId
                AND r.kind = 'live' AND r.remoteId = c.categoryRemoteId AND r.hidden = 1)
          AND c.position > :position
        ORDER BY c.position ASC LIMIT 1
        """
    )
    suspend fun nextInScope(playlistId: Long, categoryRemoteId: String?, favoritesOnly: Int, position: Int): ChannelEntity?

    @Query(
        """
        SELECT * FROM channels c
        WHERE c.playlistId = :playlistId
          AND (:categoryRemoteId IS NULL OR c.categoryRemoteId = :categoryRemoteId)
          AND (:favoritesOnly = 0 OR EXISTS(
                SELECT 1 FROM favorites f
                WHERE f.playlistId = c.playlistId AND f.kind = 'live' AND f.itemRemoteId = c.remoteId))
          AND NOT EXISTS(SELECT 1 FROM category_rules r WHERE r.playlistId = c.playlistId
                AND r.kind = 'live' AND r.remoteId = c.categoryRemoteId AND r.hidden = 1)
          AND c.position < :position
        ORDER BY c.position DESC LIMIT 1
        """
    )
    suspend fun previousInScope(playlistId: Long, categoryRemoteId: String?, favoritesOnly: Int, position: Int): ChannelEntity?

    @Query(
        """
        SELECT * FROM channels c
        WHERE c.playlistId = :playlistId
          AND (:categoryRemoteId IS NULL OR c.categoryRemoteId = :categoryRemoteId)
          AND (:favoritesOnly = 0 OR EXISTS(
                SELECT 1 FROM favorites f
                WHERE f.playlistId = c.playlistId AND f.kind = 'live' AND f.itemRemoteId = c.remoteId))
          AND NOT EXISTS(SELECT 1 FROM category_rules r WHERE r.playlistId = c.playlistId
                AND r.kind = 'live' AND r.remoteId = c.categoryRemoteId AND r.hidden = 1)
        ORDER BY c.position ASC LIMIT 1
        """
    )
    suspend fun firstInScope(playlistId: Long, categoryRemoteId: String?, favoritesOnly: Int): ChannelEntity?

    @Query(
        """
        SELECT * FROM channels c
        WHERE c.playlistId = :playlistId
          AND (:categoryRemoteId IS NULL OR c.categoryRemoteId = :categoryRemoteId)
          AND (:favoritesOnly = 0 OR EXISTS(
                SELECT 1 FROM favorites f
                WHERE f.playlistId = c.playlistId AND f.kind = 'live' AND f.itemRemoteId = c.remoteId))
          AND NOT EXISTS(SELECT 1 FROM category_rules r WHERE r.playlistId = c.playlistId
                AND r.kind = 'live' AND r.remoteId = c.categoryRemoteId AND r.hidden = 1)
        ORDER BY c.position DESC LIMIT 1
        """
    )
    suspend fun lastInScope(playlistId: Long, categoryRemoteId: String?, favoritesOnly: Int): ChannelEntity?

    /** Row index of [position] inside the scope: how many scope channels come before it. */
    @Query(
        """
        SELECT COUNT(*) FROM channels c
        WHERE c.playlistId = :playlistId
          AND (:categoryRemoteId IS NULL OR c.categoryRemoteId = :categoryRemoteId)
          AND (:favoritesOnly = 0 OR EXISTS(
                SELECT 1 FROM favorites f
                WHERE f.playlistId = c.playlistId AND f.kind = 'live' AND f.itemRemoteId = c.remoteId))
          AND NOT EXISTS(SELECT 1 FROM category_rules r WHERE r.playlistId = c.playlistId
                AND r.kind = 'live' AND r.remoteId = c.categoryRemoteId AND r.hidden = 1)
          AND c.position < :position
        """
    )
    suspend fun indexInScope(playlistId: Long, categoryRemoteId: String?, favoritesOnly: Int, position: Int): Int

    /** Quick list shown by ←/→ in the player. */
    @Query(
        """
        SELECT * FROM channels c
        WHERE c.playlistId = :playlistId
          AND (:categoryRemoteId IS NULL OR c.categoryRemoteId = :categoryRemoteId)
          AND (:favoritesOnly = 0 OR EXISTS(
                SELECT 1 FROM favorites f
                WHERE f.playlistId = c.playlistId AND f.kind = 'live' AND f.itemRemoteId = c.remoteId))
          AND NOT EXISTS(SELECT 1 FROM category_rules r WHERE r.playlistId = c.playlistId
                AND r.kind = 'live' AND r.remoteId = c.categoryRemoteId AND r.hidden = 1)
        ORDER BY c.position ASC LIMIT :limit
        """
    )
    suspend fun listInScope(playlistId: Long, categoryRemoteId: String?, favoritesOnly: Int, limit: Int): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND number = :number LIMIT 1")
    suspend fun byNumber(playlistId: Long, number: Int): ChannelEntity?

    /** Channels that have an EPG id, for the guide grid. */
    @Query(
        """
        SELECT * FROM channels c
        WHERE c.playlistId = :playlistId AND c.epgChannelId IS NOT NULL
          AND NOT EXISTS(SELECT 1 FROM category_rules r WHERE r.playlistId = c.playlistId
                AND r.kind = 'live' AND r.remoteId = c.categoryRemoteId AND r.hidden = 1)
        ORDER BY c.position LIMIT :limit OFFSET :offset
        """
    )
    suspend fun withEpg(playlistId: Long, limit: Int, offset: Int): List<ChannelEntity>

    @Query("SELECT COUNT(*) FROM channels WHERE playlistId = :playlistId AND epgChannelId IS NOT NULL")
    suspend fun countWithEpg(playlistId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ChannelEntity>)

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: Long)

    @Query("SELECT COUNT(*) FROM channels WHERE playlistId = :playlistId")
    suspend fun countByPlaylist(playlistId: Long): Int
}

@Dao
interface MovieDao {
    /** [sort]: 0 = provider order, 1 = recent first, 2 = A–Z. Room cannot bind ORDER BY, hence the CASEs. */
    @Query(
        """
        SELECT * FROM movies m
        WHERE m.playlistId = :playlistId
          AND (:categoryRemoteId IS NULL OR m.categoryRemoteId = :categoryRemoteId)
          AND (:query = '' OR m.name LIKE '%' || :query || '%')
          AND (:favoritesOnly = 0 OR EXISTS(SELECT 1 FROM favorites f
                WHERE f.playlistId = m.playlistId AND f.kind = 'movie' AND f.itemRemoteId = m.remoteId))
          AND NOT EXISTS(SELECT 1 FROM category_rules r WHERE r.playlistId = m.playlistId
                AND r.kind = 'movie' AND r.remoteId = m.categoryRemoteId AND r.hidden = 1)
        ORDER BY
          CASE WHEN :sort = 1 THEN m.addedAt END DESC,
          CASE WHEN :sort = 2 THEN m.name END COLLATE NOCASE ASC,
          m.position ASC
        """
    )
    fun paging(playlistId: Long, categoryRemoteId: String?, query: String, favoritesOnly: Int, sort: Int): PagingSource<Int, MovieEntity>

    @Query("SELECT * FROM movies WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): MovieEntity?

    @Query("SELECT * FROM movies WHERE playlistId = :playlistId AND remoteId = :remoteId LIMIT 1")
    suspend fun byRemoteId(playlistId: Long, remoteId: String): MovieEntity?

    @Query("SELECT * FROM movies WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<MovieEntity?>

    @Query(
        """
        UPDATE movies SET plot = :plot, actors = :actors, director = :director, genre = :genre,
            durationSecs = :durationSecs, backdropUrl = :backdropUrl, year = COALESCE(:year, year),
            posterUrl = COALESCE(:posterUrl, posterUrl), detailsFetchedAt = :fetchedAt
        WHERE id = :id
        """
    )
    suspend fun updateDetails(
        id: Long, plot: String?, actors: String?, director: String?, genre: String?,
        durationSecs: Int?, backdropUrl: String?, year: String?, posterUrl: String?, fetchedAt: Long,
    )

    /** Movies recently added by the provider, for the grid home's highlight cover. */
    @Query("SELECT * FROM movies WHERE playlistId = :playlistId ORDER BY addedAt DESC, position ASC LIMIT :limit")
    suspend fun recent(playlistId: Long, limit: Int): List<MovieEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MovieEntity>)

    @Query("DELETE FROM movies WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: Long)

    @Query("SELECT COUNT(*) FROM movies WHERE playlistId = :playlistId")
    suspend fun countByPlaylist(playlistId: Long): Int

    @Query("SELECT COUNT(*) FROM movies WHERE playlistId = :playlistId")
    fun observeCount(playlistId: Long): Flow<Int>
}

@Dao
interface SeriesDao {
    @Query(
        """
        SELECT * FROM series s
        WHERE s.playlistId = :playlistId
          AND (:categoryRemoteId IS NULL OR s.categoryRemoteId = :categoryRemoteId)
          AND (:query = '' OR s.name LIKE '%' || :query || '%')
          AND (:favoritesOnly = 0 OR EXISTS(SELECT 1 FROM favorites f
                WHERE f.playlistId = s.playlistId AND f.kind = 'series' AND f.itemRemoteId = s.remoteId))
          AND NOT EXISTS(SELECT 1 FROM category_rules r WHERE r.playlistId = s.playlistId
                AND r.kind = 'series' AND r.remoteId = s.categoryRemoteId AND r.hidden = 1)
        ORDER BY
          CASE WHEN :sort = 1 THEN s.addedAt END DESC,
          CASE WHEN :sort = 2 THEN s.name END COLLATE NOCASE ASC,
          s.position ASC
        """
    )
    fun paging(playlistId: Long, categoryRemoteId: String?, query: String, favoritesOnly: Int, sort: Int): PagingSource<Int, SeriesEntity>

    @Query("SELECT * FROM series WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): SeriesEntity?

    @Query("SELECT * FROM series WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<SeriesEntity?>

    @Query("SELECT * FROM series WHERE playlistId = :playlistId AND remoteId = :remoteId LIMIT 1")
    suspend fun byRemoteId(playlistId: Long, remoteId: String): SeriesEntity?

    @Query(
        """
        UPDATE series SET plot = COALESCE(:plot, plot), actors = COALESCE(:actors, actors),
            genre = COALESCE(:genre, genre), year = COALESCE(:year, year),
            coverUrl = COALESCE(:coverUrl, coverUrl), episodesFetchedAt = :fetchedAt
        WHERE id = :id
        """
    )
    suspend fun updateDetails(id: Long, plot: String?, actors: String?, genre: String?, year: String?, coverUrl: String?, fetchedAt: Long)

    @Query("SELECT * FROM series WHERE playlistId = :playlistId ORDER BY addedAt DESC, position ASC LIMIT :limit")
    suspend fun recent(playlistId: Long, limit: Int): List<SeriesEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<SeriesEntity>)

    @Query("DELETE FROM series WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: Long)

    @Query("SELECT COUNT(*) FROM series WHERE playlistId = :playlistId")
    suspend fun countByPlaylist(playlistId: Long): Int

    @Query("SELECT COUNT(*) FROM series WHERE playlistId = :playlistId")
    fun observeCount(playlistId: Long): Flow<Int>
}

@Dao
interface EpisodeDao {
    @Query("SELECT * FROM episodes WHERE playlistId = :playlistId AND seriesRemoteId = :seriesRemoteId ORDER BY season, episode")
    fun observeBySeries(playlistId: Long, seriesRemoteId: String): Flow<List<EpisodeEntity>>

    @Query("SELECT * FROM episodes WHERE playlistId = :playlistId AND seriesRemoteId = :seriesRemoteId ORDER BY season, episode")
    suspend fun bySeries(playlistId: Long, seriesRemoteId: String): List<EpisodeEntity>

    @Query("SELECT * FROM episodes WHERE id = :id LIMIT 1")
    suspend fun byId(id: Long): EpisodeEntity?

    @Query("SELECT * FROM episodes WHERE playlistId = :playlistId AND remoteId = :remoteId LIMIT 1")
    suspend fun byRemoteId(playlistId: Long, remoteId: String): EpisodeEntity?

    /** The episode that follows [season]/[episode] in the same series, if any. */
    @Query(
        """
        SELECT * FROM episodes WHERE playlistId = :playlistId AND seriesRemoteId = :seriesRemoteId
          AND (season > :season OR (season = :season AND episode > :episode))
        ORDER BY season, episode LIMIT 1
        """
    )
    suspend fun next(playlistId: Long, seriesRemoteId: String, season: Int, episode: Int): EpisodeEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<EpisodeEntity>)

    @Query("DELETE FROM episodes WHERE playlistId = :playlistId AND seriesRemoteId = :seriesRemoteId")
    suspend fun deleteBySeries(playlistId: Long, seriesRemoteId: String)

    @Query("DELETE FROM episodes WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: Long)
}

@Dao
interface FavoriteDao {
    @Query("SELECT itemRemoteId FROM favorites WHERE playlistId = :playlistId AND kind = :kind")
    fun observeIds(playlistId: Long, kind: String = ContentKind.LIVE): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE playlistId = :playlistId AND kind = :kind AND itemRemoteId = :remoteId)")
    suspend fun isFavorite(playlistId: Long, kind: String, remoteId: String): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE playlistId = :playlistId AND kind = :kind AND itemRemoteId = :remoteId)")
    fun observeIsFavorite(playlistId: Long, kind: String, remoteId: String): Flow<Boolean>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(item: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE playlistId = :playlistId AND kind = :kind AND itemRemoteId = :remoteId")
    suspend fun remove(playlistId: Long, kind: String, remoteId: String)

    @Query("SELECT COUNT(*) FROM favorites WHERE playlistId = :playlistId")
    fun observeCount(playlistId: Long): Flow<Int>
}

@Dao
interface WatchProgressDao {
    @Query("SELECT * FROM watch_progress WHERE playlistId = :playlistId AND kind = :kind AND itemRemoteId = :remoteId")
    suspend fun get(playlistId: Long, kind: String, remoteId: String): WatchProgressEntity?

    @Query("SELECT * FROM watch_progress WHERE playlistId = :playlistId AND kind = :kind AND itemRemoteId = :remoteId")
    fun observe(playlistId: Long, kind: String, remoteId: String): Flow<WatchProgressEntity?>

    /** Everything in progress for a series, keyed by episode id. */
    @Query("SELECT * FROM watch_progress WHERE playlistId = :playlistId AND seriesRemoteId = :seriesRemoteId")
    fun observeBySeries(playlistId: Long, seriesRemoteId: String): Flow<List<WatchProgressEntity>>

    @Query("SELECT * FROM watch_progress WHERE playlistId = :playlistId AND seriesRemoteId = :seriesRemoteId ORDER BY updatedAt DESC LIMIT 1")
    suspend fun latestOfSeries(playlistId: Long, seriesRemoteId: String): WatchProgressEntity?

    /** "Continue watching": most recent, unfinished, one row per movie or series. */
    @Query(
        """
        SELECT * FROM watch_progress w
        WHERE w.playlistId = :playlistId
          AND w.durationMs > 0
          AND w.positionMs >= MIN(60000, w.durationMs * 0.10)
          AND w.positionMs < w.durationMs * 0.95
          AND (w.seriesRemoteId IS NULL OR w.updatedAt = (
                SELECT MAX(w2.updatedAt) FROM watch_progress w2
                WHERE w2.playlistId = w.playlistId AND w2.seriesRemoteId = w.seriesRemoteId))
        ORDER BY w.updatedAt DESC LIMIT :limit
        """
    )
    fun observeContinueWatching(playlistId: Long, limit: Int): Flow<List<WatchProgressEntity>>

    @Upsert
    suspend fun upsert(item: WatchProgressEntity)

    @Query("DELETE FROM watch_progress WHERE playlistId = :playlistId AND kind = :kind AND itemRemoteId = :remoteId")
    suspend fun delete(playlistId: Long, kind: String, remoteId: String)
}

@Dao
interface EpgDao {
    @Query(
        """
        SELECT * FROM epg_programs WHERE playlistId = :playlistId AND channelEpgId = :channelEpgId
          AND endAt > :now ORDER BY startAt LIMIT :limit
        """
    )
    fun observeUpcoming(playlistId: Long, channelEpgId: String, now: Long, limit: Int): Flow<List<EpgProgramEntity>>

    @Query(
        """
        SELECT * FROM epg_programs WHERE playlistId = :playlistId AND channelEpgId IN (:channelEpgIds)
          AND endAt > :from AND startAt < :to ORDER BY channelEpgId, startAt
        """
    )
    suspend fun window(playlistId: Long, channelEpgIds: List<String>, from: Long, to: Long): List<EpgProgramEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<EpgProgramEntity>)

    @Query("DELETE FROM epg_programs WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: Long)

    @Query("DELETE FROM epg_programs WHERE playlistId = :playlistId AND (endAt < :from OR startAt > :to)")
    suspend fun trim(playlistId: Long, from: Long, to: Long)

    @Query("SELECT COUNT(*) FROM epg_programs WHERE playlistId = :playlistId")
    suspend fun countByPlaylist(playlistId: Long): Int
}

@Dao
interface CategoryRuleDao {
    @Query("SELECT * FROM category_rules WHERE playlistId = :playlistId")
    fun observeByPlaylist(playlistId: Long): Flow<List<CategoryRuleEntity>>

    @Query("SELECT * FROM category_rules WHERE playlistId = :playlistId AND kind = :kind AND remoteId = :remoteId")
    suspend fun get(playlistId: Long, kind: String, remoteId: String): CategoryRuleEntity?

    @Query("SELECT remoteId FROM category_rules WHERE playlistId = :playlistId AND kind = :kind AND locked = 1")
    fun observeLocked(playlistId: Long, kind: String): Flow<List<String>>

    @Upsert
    suspend fun upsert(rule: CategoryRuleEntity)

    @Query("DELETE FROM category_rules WHERE playlistId = :playlistId AND kind = :kind AND remoteId = :remoteId")
    suspend fun delete(playlistId: Long, kind: String, remoteId: String)
}

@Dao
interface PlaylistSyncDao {
    @Query("SELECT * FROM playlist_sync WHERE playlistId = :playlistId")
    suspend fun get(playlistId: Long): PlaylistSyncEntity?

    @Query("SELECT * FROM playlist_sync WHERE playlistId = :playlistId")
    fun observe(playlistId: Long): Flow<PlaylistSyncEntity?>

    @Query("SELECT * FROM playlist_sync")
    fun observeAll(): Flow<List<PlaylistSyncEntity>>

    @Upsert
    suspend fun upsert(item: PlaylistSyncEntity)

    @Query("UPDATE playlist_sync SET epgSyncedAt = :at WHERE playlistId = :playlistId")
    suspend fun markEpgSynced(playlistId: Long, at: Long)

    @Query("DELETE FROM playlist_sync WHERE playlistId = :playlistId")
    suspend fun delete(playlistId: Long)
}

/** Writes a whole playlist in one transaction so the UI never sees a half-synced list. */
@Dao
abstract class SyncDao {
    @Transaction
    open suspend fun replacePlaylist(
        playlistId: Long,
        categories: List<CategoryEntity>,
        channels: List<ChannelEntity>,
        movies: List<MovieEntity>,
        series: List<SeriesEntity>,
        episodes: List<EpisodeEntity>,
        record: PlaylistSyncEntity,
        categoryDao: CategoryDao,
        channelDao: ChannelDao,
        movieDao: MovieDao,
        seriesDao: SeriesDao,
        episodeDao: EpisodeDao,
        syncDao: PlaylistSyncDao,
    ) {
        categoryDao.deleteByPlaylist(playlistId)
        channelDao.deleteByPlaylist(playlistId)
        movieDao.deleteByPlaylist(playlistId)
        seriesDao.deleteByPlaylist(playlistId)
        // Xtream episodes are fetched on demand; only M3U delivers them with the list.
        if (episodes.isNotEmpty()) episodeDao.deleteByPlaylist(playlistId)
        categories.chunked(BATCH).forEach { categoryDao.insertAll(it) }
        channels.chunked(BATCH).forEach { channelDao.insertAll(it) }
        movies.chunked(BATCH).forEach { movieDao.insertAll(it) }
        series.chunked(BATCH).forEach { seriesDao.insertAll(it) }
        episodes.chunked(BATCH).forEach { episodeDao.insertAll(it) }
        syncDao.upsert(record)
    }

    companion object {
        /** Insert in batches: a single 5.000-row statement blows past SQLite's variable limit. */
        const val BATCH = 500
    }
}
