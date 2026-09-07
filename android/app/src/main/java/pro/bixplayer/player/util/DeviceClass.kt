package pro.bixplayer.player.util

import android.app.ActivityManager
import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import androidx.core.content.getSystemService
import timber.log.Timber

/**
 * Coarse device class used to size caches, paging and the player (M5-018).
 *
 * The reference minimum device is an MXQ Pro 4K 5G class box: 1 GB of RAM, Allwinner H3 or
 * RK3228A, hardware decoding limited to H.264, and a `build.prop` that lies about the Android
 * version — so decisions here never look at `Build.VERSION.RELEASE`, only at `SDK_INT`,
 * memory figures and the codec list.
 */
object DeviceClass {

    /** True when total RAM is below [LOW_RAM_BYTES] or the system flags itself as low-RAM. */
    @Volatile var lowRam: Boolean = false
        private set

    @Volatile var totalRamMb: Long = 0
        private set

    fun init(context: Context) {
        val am = context.getSystemService<ActivityManager>()
        val mem = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        totalRamMb = mem.totalMem / (1024 * 1024)
        lowRam = (am?.isLowRamDevice == true) || mem.totalMem in 1..LOW_RAM_BYTES
        Timber.i("device class: ram=%d MB lowRam=%s sdk=%d hw=%s", totalRamMb, lowRam, Build.VERSION.SDK_INT, Build.HARDWARE)
    }

    /** Paging: fewer, smaller pages on a 1 GB box so a 20k-title catalogue never sits in RAM. */
    val pageSize: Int get() = if (lowRam) 24 else 60
    val pagingMaxSize: Int get() = pageSize * 5

    /** Coil memory cache as a fraction of the app heap. */
    val imageCachePercent: Double get() = if (lowRam) 0.05 else 0.12

    /** True when a hardware (non-software) decoder exists for [mime], e.g. `video/hevc`. */
    fun hasHardwareDecoder(mime: String): Boolean = decoders(mime).any { it.isHardware }

    data class Decoder(val name: String, val isHardware: Boolean)

    fun decoders(mime: String): List<Decoder> = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { !it.isEncoder && it.supportedTypes.any { t -> t.equals(mime, ignoreCase = true) } }
            .map { Decoder(it.name, isHardwareCodec(it)) }
    }.getOrDefault(emptyList())

    /** One line per common video mime type: which decoders exist and whether they are hardware. */
    fun videoDecoderSummary(): String = VIDEO_MIMES.joinToString("; ") { mime ->
        val list = decoders(mime)
        val short = mime.removePrefix("video/")
        if (list.isEmpty()) "$short=none"
        else "$short=" + list.joinToString(",") { (if (it.isHardware) "HW:" else "SW:") + it.name }
    }

    private fun isHardwareCodec(info: MediaCodecInfo): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return info.isHardwareAccelerated
        val name = info.name.lowercase()
        return !(name.startsWith("omx.google.") || name.startsWith("c2.android.") || name.contains(".sw."))
    }

    private const val LOW_RAM_BYTES = 1536L * 1024 * 1024
    private val VIDEO_MIMES = listOf("video/avc", "video/hevc", "video/x-vnd.on2.vp9", "video/av01", "video/mpeg2")
}
