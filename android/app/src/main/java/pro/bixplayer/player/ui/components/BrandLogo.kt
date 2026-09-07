package pro.bixplayer.player.ui.components

import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import pro.bixplayer.player.R

/**
 * Logo shown on the splash and home screens: the reseller's upload when there is one,
 * otherwise the platform lock-up bundled with the app (`drawable/brand_logo.png`).
 */
@Composable
fun BrandLogo(url: String?, contentDescription: String?, modifier: Modifier = Modifier) {
    if (!url.isNullOrBlank()) {
        AsyncImage(
            model = url,
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
    } else {
        Image(
            painter = painterResource(R.drawable.brand_logo),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            modifier = modifier,
        )
    }
}
