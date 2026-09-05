package pro.bixplayer.player.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import pro.bixplayer.player.ui.theme.BixBackground
import pro.bixplayer.player.ui.theme.BixTvTheme

/** Entry point on Android TV (LEANBACK_LAUNCHER). Hosts Compose, never Leanback (ADR-004). */
@AndroidEntryPoint
class TvActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BixTvTheme {
                Surface(modifier = Modifier.fillMaxSize().background(BixBackground)) {
                    BixNavHost()
                }
            }
        }
    }
}
