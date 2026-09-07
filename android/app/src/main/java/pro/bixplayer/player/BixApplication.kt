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
import timber.log.Timber

@HiltAndroidApp
class BixApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var diagnostics: Diagnostics

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(if (BuildConfig.DEBUG) android.util.Log.DEBUG else android.util.Log.ERROR)
            .build()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        // Crash capture (M5-013): file log, uncaught-exception handler and last-exit reasons.
        diagnostics.install()
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
