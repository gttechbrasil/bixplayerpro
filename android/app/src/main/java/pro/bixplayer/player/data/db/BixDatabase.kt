package pro.bixplayer.player.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        CategoryEntity::class,
        ChannelEntity::class,
        FavoriteEntity::class,
        PlaylistSyncEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class BixDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun channelDao(): ChannelDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playlistSyncDao(): PlaylistSyncDao

    companion object {
        const val NAME = "bix.db"
    }
}
