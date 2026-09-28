package pro.bixplayer.player.ui

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import pro.bixplayer.player.data.datastore.DeviceStore
import pro.bixplayer.player.player.PlayerSession
import pro.bixplayer.player.ui.theme.BixBackground
import pro.bixplayer.player.ui.theme.BixMobileTheme
import pro.bixplayer.player.ui.theme.BixTvTheme
import pro.bixplayer.player.util.UiMode

/**
 * Entry point on phones and tablets. Since 1.7.0 it draws the same interface as the TV box, with
 * touch on top (F2-011): the client compares the app with players that look identical on both,
 * and a phone in landscape (~1000x450 dp) is as roomy as a 1080p TV (960x540 dp). The compact
 * phone layout is still there behind "Modo de interface → Compacto" in Ajustes. Picture-in-picture
 * when the user leaves the app while something plays.
 */
@AndroidEntryPoint
class MobileActivity : ComponentActivity() {

    @Inject lateinit var session: PlayerSession
    @Inject lateinit var store: DeviceStore

    private var compact = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        compact = UiMode.parse(runBlocking { store.currentUiMode() }) == UiMode.MOBILE
        if (!compact) hideStatusBar()
        setContent {
            if (compact) {
                BixMobileTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        BixNavHost()
                    }
                }
            } else {
                BixTvTheme(touch = true) {
                    Surface(modifier = Modifier.fillMaxSize().background(BixBackground)) {
                        BixNavHost()
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && !compact) hideStatusBar()
    }

    /**
     * The TV interface was drawn for a screen with no status bar; on a phone that bar would take
     * 35 dp from a layout that is already 90 dp shorter than a TV. The navigation bar stays, so
     * the back gesture keeps working on the first swipe. The player hides both while it plays.
     */
    private fun hideStatusBar() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.statusBars())
    }

    @Deprecated("Deprecated in Java")
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        maybeEnterPip()
    }

    private fun maybeEnterPip() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!session.inPlayerScreen || !session.isPlaying) return
        if (!packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) return
        val params = PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
        runCatching { enterPictureInPictureMode(params) }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        session.inPictureInPicture = isInPictureInPictureMode
    }
}
