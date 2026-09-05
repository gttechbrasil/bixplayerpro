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
    @Query("SELECT * FROM categories WHERE playlistId = :playlistId ORDER BY position, name")
    fun observeByPlaylist(playlistId: Long): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories WHERE playlistId = :playlistId ORDER BY position, name")
    suspend fun byPlaylist(playlistId: Long): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<CategoryEntity>)

    @Query("DELETE FROM categories WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: Long)

    @Query("SELECT COUNT(*) FROM categories WHERE playlistId = :playlistId")
    suspend fun countByPlaylist(playlistId: Long): Int
}

@Dao
interface ChannelDao {
    @Query(
        """
        SELECT * FROM channels
        WHERE playlistId = :playlistId
          AND (:categoryRemoteId IS NULL OR categoryRemoteId = :categoryRemoteId)
        ORDER BY position, name
        """
    )
    fun pagingByCategory(playlistId: Long, categoryRemoteId: String?): PagingSource<Int, ChannelEntity>

    @Query(
        """
        SELECT c.* FROM channels c
        INNER JOIN favorites f
            ON f.playlistId = c.playlistId AND f.channelRemoteId = c.remoteId
        WHERE c.playlistId = :playlistId
        ORDER BY c.position, c.name
        """
    )
    fun pagingFavorites(playlistId: Long): PagingSource<Int, ChannelEntity>

    @Query(
        """
        SELECT * FROM channels
        WHERE playlistId = :playlistId AND name LIKE '%' || :query || '%'
        ORDER BY position, name
        """
    )
    fun pagingSearch(playlistId: Long, query: String): PagingSource<Int, ChannelEntity>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId ORDER BY position, name LIMIT :limit OFFSET :offset")
    suspend fun page(playlistId: Long, limit: Int, offset: Int): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND remoteId = :remoteId LIMIT 1")
    suspend fun byRemoteId(playlistId: Long, remoteId: String): ChannelEntity?

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND number = :number LIMIT 1")
    suspend fun byNumber(playlistId: Long, number: Int): ChannelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ChannelEntity>)

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun deleteByPlaylist(playlistId: Long)

    @Query("SELECT COUNT(*) FROM channels WHERE playlistId = :playlistId")
    suspend fun countByPlaylist(playlistId: Long): Int
}

@Dao
interface FavoriteDao {
    @Query("SELECT channelRemoteId FROM favorites WHERE playlistId = :playlistId")
    fun observeIds(playlistId: Long): Flow<List<String>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE playlistId = :playlistId AND channelRemoteId = :remoteId)")
    suspend fun isFavorite(playlistId: Long, remoteId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(item: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE playlistId = :playlistId AND channelRemoteId = :remoteId")
    suspend fun remove(playlistId: Long, remoteId: String)

    @Query("DELETE FROM favorites WHERE playlistId = :playlistId")
    suspend fun clear(playlistId: Long)
}

@Dao
interface PlaylistSyncDao {
    @Query("SELECT * FROM playlist_sync WHERE playlistId = :playlistId")
    suspend fun get(playlistId: Long): PlaylistSyncEntity?

    @Query("SELECT * FROM playlist_sync")
    fun observeAll(): Flow<List<PlaylistSyncEntity>>

    @Upsert
    suspend fun upsert(item: PlaylistSyncEntity)

    @Query("DELETE FROM playlist_sync WHERE playlistId = :playlistId")
    suspend fun delete(playlistId: Long)
}

/** Writes a whole playlist in one transaction so the UI never sees a half-synced list. */
@Dao
interface SyncDao {
    @Transaction
    suspend fun replacePlaylist(
        playlistId: Long,
        categories: List<CategoryEntity>,
        channels: List<ChannelEntity>,
        categoryDao: CategoryDao,
        channelDao: ChannelDao,
        syncDao: PlaylistSyncDao,
    ) {
        categoryDao.deleteByPlaylist(playlistId)
        channelDao.deleteByPlaylist(playlistId)
        categories.chunked(BATCH).forEach { categoryDao.insertAll(it) }
        channels.chunked(BATCH).forEach { channelDao.insertAll(it) }
        syncDao.upsert(
            PlaylistSyncEntity(
                playlistId = playlistId,
                lastSyncAt = System.currentTimeMillis(),
                channelCount = channels.size,
                categoryCount = categories.size,
            )
        )
    }

    companion object {
        /** Insert in batches: a single 5.000-row statement blows past SQLite's variable limit. */
        const val BATCH = 500
    }
}
