package pro.bixplayer.player.domain.model

import pro.bixplayer.player.data.api.dto.DeviceConfigDto

/** Activation state of this device as decided by the platform. */
enum class DeviceStatus {
    /** Known to the platform but not linked to any reseller yet. */
    UNREGISTERED,

    /** Linked, but the licence or the reseller expired. */
    EXPIRED,

    /** Linked and paid for. */
    ACTIVE,

    ;

    companion object {
        fun from(value: String): DeviceStatus = when (value.lowercase()) {
            "active" -> ACTIVE
            "expired" -> EXPIRED
            else -> UNREGISTERED
        }
    }
}

/**
 * Home-screen layout chosen by the reseller (`theme` in the panel). The Anexo I asked for two;
 * three more were added for the client in 1.4.0 (F2-002). Unknown values fall back to DEFAULT so
 * an old build never lands on a blank screen when the panel gains a sixth.
 */
enum class AppLayout {
    /** Row of menu cards over the reseller's background. */
    DEFAULT,

    /** Six big tiles, remote-friendly. */
    GRID,

    /** Full-bleed artwork with the menu as a chip row. */
    CINEMA,

    /** Vertical menu on the left, artwork and poster rows on the right. */
    RAIL,

    /** One tall panel plus stacked ones, all wearing real covers. */
    MOSAIC,

    ;

    /** The value stored in the panel and sent in `theme`. */
    val slug: String get() = name.lowercase()

    companion object {
        val SELECTABLE = entries.toList()

        fun from(value: String): AppLayout =
            entries.firstOrNull { it.slug.equals(value.trim(), ignoreCase = true) } ?: DEFAULT
    }
}

enum class PlaylistType {
    XTREAM,
    M3U,

    ;

    companion object {
        fun from(value: String): PlaylistType =
            if (value.equals("xtream", ignoreCase = true)) XTREAM else M3U
    }
}

data class Playlist(
    val id: Long,
    val name: String,
    val url: String,
    val type: PlaylistType,
    val isProtected: Boolean,
)

data class Banner(
    val id: Long,
    val title: String,
    val url: String,
)

/**
 * Everything the app needs to render itself, already normalised from the wire format.
 * This is the single source of truth handed to the UI layer.
 */
data class AppConfig(
    val registered: Boolean,
    val macAddress: String,
    val status: DeviceStatus,
    val clientName: String?,
    val licenseExpiresAt: String?,
    val playlists: List<Playlist>,
    val layout: AppLayout,
    val logoUrl: String?,
    val backgroundUrl: String?,
    val qrContent: String?,
    val banners: List<Banner>,
    val autoAds: Boolean,
    val parentalPin: String,
    val minAppVersion: String,
    val apkUrl: String,
    val platformName: String,
    /** True when this instance came from the local cache instead of the network. */
    val fromCache: Boolean = false,
) {
    val hasPlaylists: Boolean get() = playlists.isNotEmpty()

    /** The device can watch when it is active and there is something to play. */
    val canWatch: Boolean get() = status == DeviceStatus.ACTIVE && hasPlaylists

    companion object {
        fun from(dto: DeviceConfigDto, fromCache: Boolean = false): AppConfig = AppConfig(
            registered = dto.registered,
            macAddress = dto.macAddress,
            status = DeviceStatus.from(dto.status),
            clientName = dto.clientName,
            licenseExpiresAt = dto.licenseExpiresAt,
            // The backend delivers `{"id": 0}` when there is nothing to play; drop those.
            playlists = dto.playlists.filter { it.id > 0 && it.url.isNotBlank() }.map {
                Playlist(
                    id = it.id,
                    name = it.name,
                    url = it.url,
                    type = PlaylistType.from(it.type),
                    isProtected = it.isProtected,
                )
            },
            layout = AppLayout.from(dto.theme),
            logoUrl = dto.logoUrl?.takeIf { it.isNotBlank() },
            backgroundUrl = dto.bgUrl?.takeIf { it.isNotBlank() },
            qrContent = dto.qrContent?.takeIf { it.isNotBlank() },
            banners = dto.banners.map { Banner(it.id, it.title, it.url) },
            autoAds = dto.autoAds,
            parentalPin = dto.pin.ifBlank { "0000" },
            minAppVersion = dto.minAppVersion,
            apkUrl = dto.apkUrl,
            platformName = dto.platformName,
            fromCache = fromCache,
        )
    }
}
