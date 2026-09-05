package pro.bixplayer.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import pro.bixplayer.player.domain.model.AppConfig
import pro.bixplayer.player.domain.model.AppLayout
import pro.bixplayer.player.domain.model.DeviceStatus
import pro.bixplayer.player.domain.model.Playlist
import pro.bixplayer.player.domain.model.PlaylistType
import pro.bixplayer.player.ui.Routes
import pro.bixplayer.player.ui.screens.activation.ActivationViewModel
import pro.bixplayer.player.ui.screens.boot.BootRouting

/** The rule that decides whether a paying customer sees channels or a wall. */
class BootRoutingTest {

    private fun config(
        registered: Boolean = true,
        status: DeviceStatus = DeviceStatus.ACTIVE,
        playlists: List<Playlist> = listOf(
            Playlist(1, "Lista", "http://a.tv/l.m3u", PlaylistType.M3U, false)
        ),
        minAppVersion: String = "1.0.0",
    ) = AppConfig(
        registered = registered,
        macAddress = "02:50:50:AA:BB:CC",
        status = status,
        clientName = null,
        licenseExpiresAt = null,
        playlists = playlists,
        layout = AppLayout.DEFAULT,
        logoUrl = null,
        backgroundUrl = null,
        qrContent = null,
        banners = emptyList(),
        autoAds = false,
        parentalPin = "0000",
        minAppVersion = minAppVersion,
        apkUrl = "",
        platformName = "Bix",
    )

    @Test
    fun `an active device with playlists goes home`() {
        assertThat(BootRouting.routeFor(config(), "1.0.0")).isEqualTo(Routes.HOME)
    }

    @Test
    fun `an unregistered device goes to activation`() {
        val route = BootRouting.routeFor(
            config(registered = false, status = DeviceStatus.UNREGISTERED, playlists = emptyList()),
            "1.0.0",
        )
        assertThat(route).isEqualTo(Routes.ACTIVATION)
    }

    @Test
    fun `registered but without playlists still goes to activation`() {
        val route = BootRouting.routeFor(config(playlists = emptyList()), "1.0.0")
        assertThat(route).isEqualTo(Routes.ACTIVATION)
    }

    @Test
    fun `an expired device goes to the expired screen`() {
        val route = BootRouting.routeFor(config(status = DeviceStatus.EXPIRED), "1.0.0")
        assertThat(route).isEqualTo(Routes.EXPIRED)
    }

    @Test
    fun `a forced update outranks every other state`() {
        // even an expired, unregistered device must be told to update first
        val route = BootRouting.routeFor(
            config(registered = false, status = DeviceStatus.EXPIRED, minAppVersion = "2.0.0"),
            "1.0.0",
        )
        assertThat(route).isEqualTo(Routes.UPDATE)
    }

    @Test
    fun `a blank minimum version never forces an update`() {
        assertThat(BootRouting.routeFor(config(minAppVersion = ""), "1.0.0")).isEqualTo(Routes.HOME)
    }
}

/** URL validation mirrors the server rule so the user gets the error before the round trip. */
class ActivationUrlValidationTest {

    @Test
    fun `accepts absolute http and https urls`() {
        assertThat(ActivationViewModel.isValidUrl("http://servidor.tv/get.php?u=1")).isTrue()
        assertThat(ActivationViewModel.isValidUrl("https://servidor.tv:8080/lista.m3u8")).isTrue()
        assertThat(ActivationViewModel.isValidUrl("  http://a.b/c  ")).isTrue()
    }

    @Test
    fun `rejects anything that is not an absolute web url`() {
        assertThat(ActivationViewModel.isValidUrl("servidor.tv/lista.m3u")).isFalse()
        assertThat(ActivationViewModel.isValidUrl("ftp://servidor.tv/lista")).isFalse()
        assertThat(ActivationViewModel.isValidUrl("http://")).isFalse()
        assertThat(ActivationViewModel.isValidUrl("http://semponto/lista")).isFalse()
        assertThat(ActivationViewModel.isValidUrl("")).isFalse()
    }
}
