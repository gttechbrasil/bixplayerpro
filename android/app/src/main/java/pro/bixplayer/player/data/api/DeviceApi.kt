package pro.bixplayer.player.data.api

import pro.bixplayer.player.data.api.dto.DeviceConfigDto
import pro.bixplayer.player.data.api.dto.MessageResponse
import pro.bixplayer.player.data.api.dto.PlaylistCreateRequest
import pro.bixplayer.player.data.api.dto.PlaylistDto
import pro.bixplayer.player.data.api.dto.RegisterRequest
import pro.bixplayer.player.data.api.dto.RegisterResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/** The platform API consumed by the device. See docs/API.md. */
interface DeviceApi {

    @POST("api/v1/device/register")
    suspend fun register(@Body body: RegisterRequest): RegisterResponse

    @GET("api/v1/device/config")
    suspend fun config(): DeviceConfigDto

    @POST("api/v1/device/playlists")
    suspend fun addPlaylist(@Body body: PlaylistCreateRequest): PlaylistDto

    @DELETE("api/v1/device/playlists/{id}")
    suspend fun deletePlaylist(@Path("id") id: Long): MessageResponse
}
