package pro.bixplayer.player.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** What a category, favourite or watch-progress row refers to. Stored as plain strings. */
object ContentKind {
    const val LIVE = "live"
    const val MOVIE = "movie"
    const val SERIES = "series"
    const val EPISODE = "episode"
}

/**
 * A category as delivered by a playlist, for live TV, movies or series ([kind]). Scoped by
 * playlist because two playlists may use the same category id for different things.
 */
@Entity(
    tableName = "categories",
    indices = [Index(value = ["playlistId", "kind", "remoteId"], unique = true), Index("playlistId")],
)
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val kind: String = ContentKind.LIVE,
    /** Category id in the source (Xtream `category_id`, or the group title for M3U). */
    val remoteId: String,
    val name: String,
    val position: Int = 0,
    val channelCount: Int = 0,
)

/** A live channel. */
@Entity(
    tableName = "channels",
    indices = [
        Index(value = ["playlistId", "remoteId"], unique = true),
        Index("playlistId"),
        Index("categoryRemoteId"),
        Index("name"),
        Index(value = ["playlistId", "epgChannelId"]),
    ],
)
data class ChannelEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    /** Stream id in the source (Xtream `stream_id`, or a name+url hash for M3U). */
    val remoteId: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val categoryRemoteId: String? = null,
    /** Channel number shown on screen and typed on the remote. */
    val number: Int = 0,
    /** `tvg-id` (M3U) or `epg_channel_id` (Xtream), used to match EPG entries. */
    val epgChannelId: String? = null,
    val position: Int = 0,
)

/** A movie (VOD). Details (plot, cast…) are filled on demand when the provider has them. */
@Entity(
    tableName = "movies",
    indices = [
        Index(value = ["playlistId", "remoteId"], unique = true),
        Index("playlistId"),
        Index(value = ["playlistId", "categoryRemoteId"]),
        Index("name"),
        Index("addedAt"),
    ],
)
data class MovieEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val remoteId: String,
    val name: String,
    val streamUrl: String,
    val posterUrl: String? = null,
    val categoryRemoteId: String? = null,
    /** Epoch seconds the provider added the title; drives the "recent" sort. */
    val addedAt: Long = 0,
    val year: String? = null,
    val rating: String? = null,
    val position: Int = 0,
    // On-demand details
    val plot: String? = null,
    /** Named `actors` because `cast` is an SQL keyword and breaks COALESCE(:cast, cast). */
    val actors: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val durationSecs: Int? = null,
    val backdropUrl: String? = null,
    val detailsFetchedAt: Long? = null,
)

/** A series (the show, not the episodes). Episodes load when the user opens it. */
@Entity(
    tableName = "series",
    indices = [
        Index(value = ["playlistId", "remoteId"], unique = true),
        Index("playlistId"),
        Index(value = ["playlistId", "categoryRemoteId"]),
        Index("name"),
        Index("addedAt"),
    ],
)
data class SeriesEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val remoteId: String,
    val name: String,
    val coverUrl: String? = null,
    val categoryRemoteId: String? = null,
    val addedAt: Long = 0,
    val year: String? = null,
    val rating: String? = null,
    val plot: String? = null,
    /** Named `actors` because `cast` is an SQL keyword and breaks COALESCE(:cast, cast). */
    val actors: String? = null,
    val genre: String? = null,
    val position: Int = 0,
    /** Null until the episodes were fetched at least once (Xtream) — M3U series arrive complete. */
    val episodesFetchedAt: Long? = null,
)

@Entity(
    tableName = "episodes",
    indices = [
        Index(value = ["playlistId", "remoteId"], unique = true),
        Index(value = ["playlistId", "seriesRemoteId", "season", "episode"]),
    ],
)
data class EpisodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    val seriesRemoteId: String,
    val remoteId: String,
    val season: Int,
    val episode: Int,
    val title: String,
    val streamUrl: String,
    val plot: String? = null,
    val thumbUrl: String? = null,
    val durationSecs: Int? = null,
)

/** Favourites are per playlist and per kind (live channel, movie or series). */
@Entity(
    tableName = "favorites",
    primaryKeys = ["playlistId", "kind", "itemRemoteId"],
    indices = [Index("playlistId")],
)
data class FavoriteEntity(
    val playlistId: Long,
    val kind: String,
    val itemRemoteId: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Where the user stopped in a movie or episode. Denormalised title/poster so the home can
 * render "continue watching" without joining three tables.
 */
@Entity(
    tableName = "watch_progress",
    primaryKeys = ["playlistId", "kind", "itemRemoteId"],
    indices = [Index(value = ["playlistId", "updatedAt"])],
)
data class WatchProgressEntity(
    val playlistId: Long,
    /** [ContentKind.MOVIE] or [ContentKind.EPISODE]. */
    val kind: String,
    val itemRemoteId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val title: String,
    val subtitle: String? = null,
    val posterUrl: String? = null,
    /** For episodes: the series they belong to, so "continue" can find the next one. */
    val seriesRemoteId: String? = null,
) {
    /** Watched to (almost) the end: treated as finished, not resumable. */
    val finished: Boolean get() = durationMs > 0 && positionMs >= durationMs * FINISHED_RATIO

    /** Resumable after a minute, or after 10% of a short item, and before the end. */
    val resumable: Boolean
        get() = durationMs > 0 && positionMs >= minOf(MIN_RESUME_MS, (durationMs * MIN_RESUME_RATIO).toLong()) && !finished

    companion object {
        const val FINISHED_RATIO = 0.95
        const val MIN_RESUME_MS = 60_000L
        const val MIN_RESUME_RATIO = 0.10
    }
}

/** One EPG programme. Kept inside a sliding window (see EpgSyncUseCase). */
@Entity(
    tableName = "epg_programs",
    indices = [Index(value = ["playlistId", "channelEpgId", "startAt"]), Index("endAt")],
)
data class EpgProgramEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: Long,
    /** XMLTV channel id, matched to `ChannelEntity.epgChannelId`. */
    val channelEpgId: String,
    /** Epoch millis. */
    val startAt: Long,
    val endAt: Long,
    val title: String,
    val description: String? = null,
)

/** Categories the user hid or locked behind the PIN, per playlist and kind. */
@Entity(tableName = "category_rules", primaryKeys = ["playlistId", "kind", "remoteId"])
data class CategoryRuleEntity(
    val playlistId: Long,
    val kind: String,
    val remoteId: String,
    val hidden: Boolean = false,
    val locked: Boolean = false,
)

/** Bookkeeping for the sync of each playlist. */
@Entity(tableName = "playlist_sync")
data class PlaylistSyncEntity(
    @PrimaryKey val playlistId: Long,
    val lastSyncAt: Long,
    val channelCount: Int,
    val categoryCount: Int,
    val movieCount: Int = 0,
    val seriesCount: Int = 0,
    /** XMLTV source: `xmltv.php` for Xtream, `url-tvg` header for M3U; null when unknown. */
    val epgUrl: String? = null,
    val epgSyncedAt: Long? = null,
    /** Non-null when the last attempt failed; shown in the settings screen. */
    val lastError: String? = null,
)
