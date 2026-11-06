package pro.bixplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pro.bixplayer.player.player.PlayerSession
import pro.bixplayer.player.player.PlayerSession.EngineKind

class EnginePreferenceTest {

    @Test
    fun `vlc is the default`() {
        assertEquals(EngineKind.VLC, PlayerSession.preferenceFrom(null))
        assertEquals(EngineKind.VLC, PlayerSession.preferenceFrom("vlc"))
    }

    @Test
    fun `an explicit choice is kept`() {
        assertEquals(EngineKind.MEDIA3, PlayerSession.preferenceFrom("media3"))
        assertNull(PlayerSession.preferenceFrom("auto"))
    }
}
