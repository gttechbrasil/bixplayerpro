package pro.bixplayer.player.util

import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/**
 * The TV/phone decision (M5-017) as a pure function of the hardware signals. The emulator
 * cannot reproduce an AOSP box — its system image always registers virtio touch devices even
 * with `hw.touchScreen=no` — so the combinations seen on real boxes are pinned here.
 */
class UiModeDecisionTest {

    private fun signals(
        uiModeTv: Boolean = false,
        leanback: Boolean = false,
        tvFeature: Boolean = false,
        touchFeature: Boolean = true,
        touchInputDevice: Boolean = true,
        characteristics: String = "default",
    ) = UiModeDecider.Signals(uiModeTv, leanback, tvFeature, touchFeature, touchInputDevice, characteristics)

    @Test
    fun `android tv with leanback is tv`() {
        assertTrue(signals(uiModeTv = true, leanback = true, tvFeature = true, touchFeature = false, touchInputDevice = false, characteristics = "tv").isTv)
    }

    @Test
    fun `phone is mobile`() {
        assertFalse(signals().isTv)
    }

    @Test
    fun `tablet with touch is mobile`() {
        assertFalse(signals(characteristics = "tablet").isTv)
    }

    @Test
    fun `aosp box without leanback but with no touch input device is tv`() {
        // Declares the touchscreen feature (handheld_core_hardware.xml) yet has only a remote.
        assertTrue(signals(touchFeature = true, touchInputDevice = false).isTv)
    }

    @Test
    fun `aosp box that says tv in build characteristics is tv even with a mouse`() {
        assertTrue(signals(touchInputDevice = true, characteristics = "tv,nosdcard").isTv)
    }

    @Test
    fun `box without the touchscreen feature is tv`() {
        assertTrue(signals(touchFeature = false).isTv)
    }

    @Test
    fun `override wins over hardware`() {
        assertEquals(UiMode.TV, UiMode.parse("tv"))
        assertEquals(UiMode.MOBILE, UiMode.parse("MOBILE"))
        assertEquals(UiMode.AUTO, UiMode.parse(null))
        assertEquals(UiMode.AUTO, UiMode.parse("anything"))
    }
}
