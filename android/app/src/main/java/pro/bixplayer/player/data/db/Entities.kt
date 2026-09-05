package pro.bixplayer.player.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A category as delivered by a playlist. Scoped by playlist because two playlists may use the
 * same category id for different things.
 */
@Entity(
    tableName = "categories",
    indices = [Index(value = ["playlistId", "remoteId"], unique = true), Index("playlistId")],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    /** Category id in the source (Xtream `category_id`, or the group title for M3U). */
    val remoteId: String,
    val name: String,
    val position: Int = 0,
    val channelCount: Int = 0,
)

/** A live channel. Movies and series arrive in the M4. */
@Entity(
    tableName = "channels",
    indices = [
        Index(value = ["playlistId", "remoteId"], unique = true),
        Index("playlistId"),
        Index("categoryRemoteId"),
        Index("name"),
    ],
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    /** Stream id in the source (Xtream `stream_id`, or the URL hash for M3U). */
    val remoteId: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val categoryRemoteId: String? = null,
    /** Channel number shown on screen and typed on the remote. */
    val number: Int = 0,
    /** `tvg-id`, used to match EPG entries in the M4. */
    val epgChannelId: String? = null,
    val position: Int = 0,
)

/** Favourites are per playlist: the same channel in another playlist is a different entry. */
@Entity(
    tableName = "favorites",
    primaryKeys = ["playlistId", "channelRemoteId"],
    indices = [Index("playlistId")],
)
data class FavoriteEntity(
    val playlistId: Long,
    val channelRemoteId: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Bookkeeping for the incremental sync of each playlist. */
@Entity(tableName = "playlist_sync")
data class PlaylistSyncEntity(
    @PrimaryKey val playlistId: Long,
    val lastSyncAt: Long,
    val channelCount: Int,
    val categoryCount: Int,
    /** Non-null when the last attempt failed; shown in the settings screen. */
    val lastError: String? = null,
)
