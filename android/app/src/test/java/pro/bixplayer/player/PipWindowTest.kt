package pro.bixplayer.player

import androidx.lifecycle.Lifecycle
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import pro.bixplayer.player.ui.screens.player.pipWindowClosed

/** M5-047: closing the PiP window must stop playback; expanding it back must not. */
class PipWindowTest {

    @Test
    fun `leaving pip while stopped is the window being closed`() {
        assertThat(pipWindowClosed(inPictureInPicture = false, state = Lifecycle.State.CREATED)).isTrue()
    }

    @Test
    fun `leaving pip while on screen is the window being expanded`() {
        assertThat(pipWindowClosed(inPictureInPicture = false, state = Lifecycle.State.STARTED)).isFalse()
        assertThat(pipWindowClosed(inPictureInPicture = false, state = Lifecycle.State.RESUMED)).isFalse()
    }

    @Test
    fun `entering pip never stops playback`() {
        assertThat(pipWindowClosed(inPictureInPicture = true, state = Lifecycle.State.CREATED)).isFalse()
        assertThat(pipWindowClosed(inPictureInPicture = true, state = Lifecycle.State.STARTED)).isFalse()
    }
}
