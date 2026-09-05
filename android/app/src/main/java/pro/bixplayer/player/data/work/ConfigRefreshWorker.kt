package pro.bixplayer.player.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import pro.bixplayer.player.data.datastore.DeviceStore
import pro.bixplayer.player.data.repository.ConfigRepository
import pro.bixplayer.player.domain.model.ConfigState
import timber.log.Timber

/**
 * Periodic configuration refresh. The reseller changes branding, playlists and the licence on
 * the panel; without this the device would only notice on the next cold start.
 */
@HiltWorker
class ConfigRefreshWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: ConfigRepository,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Timber.d("periodic config refresh")
        return when (repository.refresh()) {
            is ConfigState.Ready -> Result.success()
            // Retry with WorkManager's backoff; the cache keeps the app usable meanwhile.
            is ConfigState.Failed -> Result.retry()
            ConfigState.Loading -> Result.retry()
        }
    }

    companion object {
        const val NAME = "config-refresh"

        /** (Re)schedules the job with the period the user chose in the settings screen. */
        suspend fun schedule(context: Context, prefs: DeviceStore) {
            val hours = prefs.currentRefreshHours()
            val request = PeriodicWorkRequestBuilder<ConfigRefreshWorker>(hours, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                // UPDATE keeps the existing job when the period did not change.
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
