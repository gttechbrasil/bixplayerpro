package pro.bixplayer.player.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Local cache of the provider's lists plus the user's own state (favourites, progress, rules).
 * Content tables are rebuilt on every sync; the schema is destructive-migrated because
 * nothing here is the source of truth (see docs/ADR-005-android-content-model.md).
 */
@Database(
    entities = [
        CategoryEntity::class,
        ChannelEntity::class,
        MovieEntity::class,
        SeriesEntity::class,
        EpisodeEntity::class,
        FavoriteEntity::class,
        WatchProgressEntity::class,
        EpgProgramEntity::class,
        CategoryRuleEntity::class,
        PlaylistSyncEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class BixDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun channelDao(): ChannelDao
    abstract fun movieDao(): MovieDao
    abstract fun seriesDao(): SeriesDao
    abstract fun episodeDao(): EpisodeDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun watchProgressDao(): WatchProgressDao
    abstract fun epgDao(): EpgDao
    abstract fun categoryRuleDao(): CategoryRuleDao
    abstract fun playlistSyncDao(): PlaylistSyncDao
    abstract fun syncDao(): SyncDao

    companion object {
        const val NAME = "bix.db"
    }
}
