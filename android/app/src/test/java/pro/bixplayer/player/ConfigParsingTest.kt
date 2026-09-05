package pro.bixplayer.player

import com.google.common.truth.Truth.assertThat
import com.squareup.moshi.Moshi
import org.junit.Test
import pro.bixplayer.player.data.api.dto.DeviceConfigDto
import pro.bixplayer.player.domain.model.AppLayout
import pro.bixplayer.player.domain.model.AppConfig
import pro.bixplayer.player.domain.model.DeviceStatus
import pro.bixplayer.player.domain.model.PlaylistType

/** Parsing of `GET /api/v1/device/config` and the mapping into the domain model. */
class ConfigParsingTest {

    private val adapter = Moshi.Builder().build().adapter(DeviceConfigDto::class.java)

    @Test
    fun `parses an active device with an xtream playlist`() {
        val json = """
            {
              "registered": true,
              "mac_address": "02:50:50:A1:B2:C3",
              "status": "active",
              "client_name": "João Silva",
              "license_expires_at": "2050-01-01",
              "playlists": [
                {"id": 12, "name": "Lista 1",
                 "url": "http://serv.tv/get.php?username=u&password=p&type=m3u_plus",
                 "type": "xtream", "is_protected": false}
              ],
              "theme": "grid",
              "logo_url": "https://cdn/logo.png",
              "bg_url": "https://cdn/bg.jpg",
              "qr_content": "https://wa.me/5511999999999",
              "banners": [{"id": 3, "title": "Promo", "url": "https://cdn/b.jpg"}],
              "auto_ads": true,
              "pin": "1234",
              "min_app_version": "1.0.0",
              "apk_url": "https://cdn/app.apk",
              "platform_name": "Bix Player"
            }
        """.trimIndent()

        val config = AppConfig.from(adapter.fromJson(json)!!)

        assertThat(config.registered).isTrue()
        assertThat(config.status).isEqualTo(DeviceStatus.ACTIVE)
        assertThat(config.macAddress).isEqualTo("02:50:50:A1:B2:C3")
        assertThat(config.clientName).isEqualTo("João Silva")
        assertThat(config.layout).isEqualTo(AppLayout.GRID)
        assertThat(config.playlists).hasSize(1)
        assertThat(config.playlists.first().type).isEqualTo(PlaylistType.XTREAM)
        assertThat(config.playlists.first().url).contains("password=p")
        assertThat(config.banners.single().title).isEqualTo("Promo")
        assertThat(config.autoAds).isTrue()
        assertThat(config.parentalPin).isEqualTo("1234")
        assertThat(config.canWatch).isTrue()
        assertThat(config.fromCache).isFalse()
    }

    @Test
    fun `parses an unregistered device with the minimum payload`() {
        val json = """
            {"registered": false, "mac_address": "02:50:50:00:00:01", "status": "unregistered",
             "playlists": [], "banners": []}
        """.trimIndent()

        val config = AppConfig.from(adapter.fromJson(json)!!)

        assertThat(config.status).isEqualTo(DeviceStatus.UNREGISTERED)
        assertThat(config.hasPlaylists).isFalse()
        assertThat(config.canWatch).isFalse()
        // Defaults must be sane so the UI never has to null-check.
        assertThat(config.layout).isEqualTo(AppLayout.DEFAULT)
        assertThat(config.parentalPin).isEqualTo("0000")
        assertThat(config.logoUrl).isNull()
    }

    @Test
    fun `expired device keeps its playlists out of reach`() {
        val json = """
            {"registered": true, "mac_address": "02:50:50:00:00:02", "status": "expired",
             "license_expires_at": "2020-01-01", "playlists": [], "banners": []}
        """.trimIndent()

        val config = AppConfig.from(adapter.fromJson(json)!!)

        assertThat(config.status).isEqualTo(DeviceStatus.EXPIRED)
        assertThat(config.canWatch).isFalse()
        assertThat(config.licenseExpiresAt).isEqualTo("2020-01-01")
    }

    @Test
    fun `drops placeholder playlists and blank urls`() {
        // The legacy panel signals "nothing to play" with an id of 0; ours must ignore it.
        val json = """
            {"registered": true, "mac_address": "02:50:50:00:00:03", "status": "active",
             "playlists": [
               {"id": 0, "name": "vazio", "url": "", "type": "m3u"},
               {"id": 7, "name": "Boa", "url": "http://a.tv/l.m3u", "type": "m3u"}
             ], "banners": []}
        """.trimIndent()

        val config = AppConfig.from(adapter.fromJson(json)!!)

        assertThat(config.playlists.map { it.id }).containsExactly(7L)
        assertThat(config.playlists.single().type).isEqualTo(PlaylistType.M3U)
    }

    @Test
    fun `unknown status and theme fall back instead of crashing`() {
        val json = """
            {"registered": true, "mac_address": "02:50:50:00:00:04", "status": "something_new",
             "theme": "theme_8", "playlists": [], "banners": []}
        """.trimIndent()

        val config = AppConfig.from(adapter.fromJson(json)!!)

        assertThat(config.status).isEqualTo(DeviceStatus.UNREGISTERED)
        assertThat(config.layout).isEqualTo(AppLayout.DEFAULT)
    }
}
