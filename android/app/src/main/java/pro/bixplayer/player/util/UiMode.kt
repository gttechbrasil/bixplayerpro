package pro.bixplayer.player.util

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.annotation.SuppressLint
import android.content.res.Configuration
import android.view.InputDevice
import androidx.core.content.getSystemService
import timber.log.Timber

/** User override for the interface family, persisted in the device store (M5-017). */
enum class UiMode {
    AUTO, TV, MOBILE;

    companion object {
        fun parse(value: String?): UiMode = when (value?.lowercase()) {
            "tv" -> TV
            "mobile" -> MOBILE
            else -> AUTO
        }
    }
}

/**
 * Decides which UI family to open. Many TV boxes run plain AOSP with a regular launcher:
 * they have no leanback feature and their launcher fires the LAUNCHER intent, so a manifest
 * split by category picks the phone UI on a device driven by a remote. The decision is
 * therefore taken at runtime from the hardware, with the settings override on top.
 */
object UiModeDecider {

    fun isTvHardware(context: Context): Boolean = signals(context).isTv

    /** Every signal that goes into the decision; also written to the diagnostics bundle. */
    data class Signals(
        val uiModeTv: Boolean,
        val leanback: Boolean,
        val tvFeature: Boolean,
        val touchFeature: Boolean,
        val touchInputDevice: Boolean,
        val characteristics: String,
    ) {
        /** AOSP boxes often *declare* the touchscreen feature; what they never have is a real
         *  touch input device. `ro.build.characteristics` says "tv" on most of them too. */
        val isTv: Boolean
            get() = uiModeTv || leanback || tvFeature || !touchFeature || !touchInputDevice ||
                characteristics.split(',').any { it.trim() == "tv" }

        override fun toString(): String =
            "uiModeTv=$uiModeTv leanback=$leanback tvFeature=$tvFeature touchFeature=$touchFeature " +
                "touchInput=$touchInputDevice characteristics=$characteristics -> ${if (isTv) "TV" else "MOBILE"}"
    }

    fun signals(context: Context): Signals {
        val pm = context.packageManager
        val uiMode = context.getSystemService<UiModeManager>()?.currentModeType
        val signals = Signals(
            uiModeTv = uiMode == Configuration.UI_MODE_TYPE_TELEVISION,
            leanback = pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK),
            tvFeature = pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION),
            touchFeature = pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN),
            touchInputDevice = hasTouchInputDevice(),
            characteristics = buildCharacteristics(),
        )
        Timber.i("ui mode: %s", signals)
        return signals
    }

    private fun hasTouchInputDevice(): Boolean = runCatching {
        InputDevice.getDeviceIds().any { id ->
            val device = InputDevice.getDevice(id) ?: return@any false
            !device.isVirtual && device.sources and InputDevice.SOURCE_TOUCHSCREEN == InputDevice.SOURCE_TOUCHSCREEN
        }
    }.getOrDefault(true)

    @SuppressLint("PrivateApi")
    private fun buildCharacteristics(): String = runCatching {
        val clazz = Class.forName("android.os.SystemProperties")
        clazz.getMethod("get", String::class.java, String::class.java)
            .invoke(null, "ro.build.characteristics", "") as String
    }.getOrDefault("")

    fun useTvUi(context: Context, override: UiMode): Boolean = when (override) {
        UiMode.TV -> true
        UiMode.MOBILE -> false
        UiMode.AUTO -> isTvHardware(context)
    }
}
