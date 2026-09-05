package pro.bixplayer.player.data.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Body of `POST /api/v1/device/register`. */
@JsonClass(generateAdapter = true)
data class RegisterRequest(
    @Json(name = "device_id") val deviceId: String,
    @Json(name = "app_type") val appType: String,
    @Json(name = "app_version") val appVersion: String,
)

/** Response of `POST /api/v1/device/register`. The token is only ever shown once. */
@JsonClass(generateAdapter = true)
data class RegisterResponse(
    @Json(name = "mac_address") val macAddress: String,
    @Json(name = "token") val token: String,
)

/** One playlist as delivered to the device. */
@JsonClass(generateAdapter = true)
data class PlaylistDto(
    @Json(name = "id") val id: Long,
    @Json(name = "name") val name: String,
    @Json(name = "url") val url: String,
    @Json(name = "type") val type: String,
    @Json(name = "is_protected") val isProtected: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class BannerDto(
    @Json(name = "id") val id: Long,
    @Json(name = "title") val title: String,
    @Json(name = "url") val url: String,
)

/**
 * Response of `GET /api/v1/device/config` — everything the app needs to render itself.
 * Mirrors `DeviceConfig` in the backend (see docs/API.md).
 */
@JsonClass(generateAdapter = true)
data class DeviceConfigDto(
    @Json(name = "registered") val registered: Boolean,
    @Json(name = "mac_address") val macAddress: String,
    @Json(name = "status") val status: String,
    @Json(name = "client_name") val clientName: String? = null,
    @Json(name = "license_expires_at") val licenseExpiresAt: String? = null,
    @Json(name = "playlists") val playlists: List<PlaylistDto> = emptyList(),
    @Json(name = "theme") val theme: String = "default",
    @Json(name = "logo_url") val logoUrl: String? = null,
    @Json(name = "bg_url") val bgUrl: String? = null,
    @Json(name = "qr_content") val qrContent: String? = null,
    @Json(name = "banners") val banners: List<BannerDto> = emptyList(),
    @Json(name = "auto_ads") val autoAds: Boolean = false,
    @Json(name = "pin") val pin: String = "0000",
    @Json(name = "min_app_version") val minAppVersion: String = "",
    @Json(name = "apk_url") val apkUrl: String = "",
    @Json(name = "platform_name") val platformName: String = "",
)

/** Body of `POST /api/v1/device/playlists`. */
@JsonClass(generateAdapter = true)
data class PlaylistCreateRequest(
    @Json(name = "name") val name: String,
    @Json(name = "url") val url: String,
    @Json(name = "is_protected") val isProtected: Boolean = false,
)

/** Generic `{"message": "..."}` reply. */
@JsonClass(generateAdapter = true)
data class MessageResponse(
    @Json(name = "message") val message: String,
)

/** Error envelope: `{"detail": {"message": "...", "code": "..."}}`. */
@JsonClass(generateAdapter = true)
data class ApiErrorEnvelope(
    @Json(name = "detail") val detail: ApiErrorDetail? = null,
)

@JsonClass(generateAdapter = true)
data class ApiErrorDetail(
    @Json(name = "message") val message: String? = null,
    @Json(name = "code") val code: String? = null,
)
