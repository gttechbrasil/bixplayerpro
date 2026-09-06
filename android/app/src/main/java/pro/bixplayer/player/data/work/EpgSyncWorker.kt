package pro.bixplayer.player.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit
import pro.bixplayer.player.data.datastore.DeviceStore
import pro.bixplayer.player.domain.usecase.EpgSyncUseCase
import timber.log.Timber

/** Refreshes the EPG window of the active playlist every 12 h, and on demand after a sync. */
@HiltWorker
class EpgSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val epgSync: EpgSyncUseCase,
    private val store: DeviceStore,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val playlistId = inputData.getLong(KEY_PLAYLIST, -1L).takeIf { it > 0 }
            ?: store.currentActivePlaylistId()
            ?: return Result.success()
        val force = inputData.getBoolean(KEY_FORCE, false)
        return when (val result = epgSync.sync(playlistId, force)) {
            is EpgSyncUseCase.Result.Success -> Result.success()
            EpgSyncUseCase.Result.NoSource -> Result.success()
            is EpgSyncUseCase.Result.Failure -> {
                Timber.w("epg worker: %s", result.message)
                if (runAttemptCount < 3) Result.retry() else Result.failure()
            }
        }
    }

    companion object {
        const val PERIODIC_NAME = "epg-refresh"
        const val ONCE_NAME = "epg-now"
        private const val KEY_PLAYLIST = "playlist"
        private const val KEY_FORCE = "force"

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<EpgSyncWorker>(12, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Right after a playlist sync, or from "Atualizar guia" in the settings. */
        fun syncNow(context: Context, playlistId: Long, force: Boolean) {
            val request = OneTimeWorkRequestBuilder<EpgSyncWorker>()
                .setInputData(workDataOf(KEY_PLAYLIST to playlistId, KEY_FORCE to force))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(ONCE_NAME, ExistingWorkPolicy.REPLACE, request)
        }
    }
}
