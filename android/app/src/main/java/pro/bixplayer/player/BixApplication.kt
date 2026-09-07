package pro.bixplayer.player

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import pro.bixplayer.player.data.work.EpgSyncWorker
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pro.bixplayer.player.util.Diagnostics
import okio.Path.Companion.toOkioPath
import timber.log.Timber
import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.bitmapConfig
import coil3.request.crossfade
import pro.bixplayer.player.util.DeviceClass

@HiltAndroidApp
class BixApplication : Application(), Configuration.Provider, SingletonImageLoader.Factory {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var diagnostics: Diagnostics

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.ERROR)
            .build()

    /**
     * Coil image loader (M5-018): small memory cache on low-RAM boxes, RGB_565 bitmaps (half
     * the memory of ARGB_8888 for posters), bounded disk cache. Composables give Coil their
     * measured size, so a 6-column grid never decodes posters larger than the cell.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .memoryCache {
            MemoryCache.Builder().maxSizePercent(context, DeviceClass.imageCachePercent).build()
        }
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("images").toOkioPath())
                .maxSizeBytes(DISK_IMAGE_CACHE_BYTES)
                .build()
        }
        .apply { if (DeviceClass.lowRam) bitmapConfig(Bitmap.Config.RGB_565) }
        .crossfade(false)
        .build()

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Posters are the cheapest thing to drop when the system asks for memory.
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            runCatching { SingletonImageLoader.get(this).memoryCache?.clear() }
            Timber.i("trim memory level=%d: image cache cleared", level)
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // Crash capture (M5-013): file log, uncaught-exception handler and last-exit reasons.
        diagnostics.install()
        // Sizes caches, paging and the player for 1 GB boxes (M5-018).
        DeviceClass.init(this)
        EpgSyncWorker.schedulePeriodic(this)
        // Evidence from a previous crash goes to the panel by itself, best effort, once the
        // network stack had time to come up; the manual button in Settings covers the rest.
        if (diagnostics.hasPendingEvidence()) {
            scope.launch {
                delay(AUTO_SEND_DELAY_MS)
                diagnostics.send("crash")
            }
        }
    }
}

private const val AUTO_SEND_DELAY_MS = 8_000L
private const val DISK_IMAGE_CACHE_BYTES = 48L * 1024 * 1024
