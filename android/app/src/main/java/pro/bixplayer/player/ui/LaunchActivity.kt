package pro.bixplayer.player.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import pro.bixplayer.player.data.datastore.DeviceStore
import pro.bixplayer.player.util.UiMode
import pro.bixplayer.player.util.UiModeDecider
import timber.log.Timber
import javax.inject.Inject

/**
 * Single entry point for both launcher categories (M5-017). Picks the TV or the phone UI at
 * runtime — hardware capabilities plus the "Modo de interface" override — and hands off to
 * the matching activity, which is no longer exported on its own.
 */
@AndroidEntryPoint
class LaunchActivity : ComponentActivity() {

    @Inject lateinit var store: DeviceStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val override = UiMode.parse(runBlocking { store.currentUiMode() })
        val tv = UiModeDecider.useTvUi(this, override)
        Timber.i("launch: override=%s -> %s", override, if (tv) "TvActivity" else "MobileActivity")
        val target = if (tv) TvActivity::class.java else MobileActivity::class.java
        startActivity(
            Intent(this, target)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
