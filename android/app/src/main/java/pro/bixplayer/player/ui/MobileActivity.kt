package pro.bixplayer.player.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import pro.bixplayer.player.ui.theme.BixMobileTheme

/**
 * Entry point on phones and tablets (LAUNCHER). In the M3 it shows the same activation flow as
 * the TV; the full mobile experience arrives in the M4.
 */
@AndroidEntryPoint
class MobileActivity : ComponentActivity() {
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
}
