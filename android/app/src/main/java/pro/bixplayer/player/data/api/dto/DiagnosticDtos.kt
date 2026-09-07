package pro.bixplayer.player.data.api.dto

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/** Body of `POST /api/v1/device/diagnostics` (M5-013). */
@JsonClass(generateAdapter = true)
data class DiagnosticRequest(
    @Json(name = "kind") val kind: String,
    @Json(name = "app_version") val appVersion: String,
    @Json(name = "device_info") val deviceInfo: Map<String, String>,
    @Json(name = "log") val log: String,
)

@JsonClass(generateAdapter = true)
data class DiagnosticResponse(
    @Json(name = "id") val id: Long,
    @Json(name = "message") val message: String,
)
