package pro.bixplayer.player.ui

import android.app.PictureInPictureParams
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import pro.bixplayer.player.player.PlayerSession
import pro.bixplayer.player.ui.theme.BixMobileTheme

/**
 * Entry point on phones and tablets (LAUNCHER). Same graph as the TV, touch-first layouts, and
 * picture-in-picture when the user leaves the app while something plays.
 */
@AndroidEntryPoint
class MobileActivity : ComponentActivity() {

    @Inject lateinit var session: PlayerSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BixMobileTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BixNavHost()
                }
            }
        }
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
