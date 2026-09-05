package pro.bixplayer.player.util

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

/**
 * Stable identity of this hardware.
 *
 * ANDROID_ID survives reinstalls and only changes on a factory reset, which is exactly the
 * lifetime a licence should have. It is hashed before leaving the device so the platform never
 * stores the raw identifier (see docs/ADR-002).
 */
object DeviceIdentity {

    @SuppressLint("HardwareIds")
    fun rawAndroidId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID).orEmpty()

    /** SHA-256 of ANDROID_ID, lowercase hex. Falls back to a stable string when unavailable. */
    fun deviceId(context: Context): String {
        val raw = rawAndroidId(context).ifBlank { "unknown-android-id" }
        return sha256(raw)
    }

    fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
