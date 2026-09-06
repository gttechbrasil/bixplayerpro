package pro.bixplayer.player.ui.components

import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * Video output for [player]. Controls are ours (Compose overlays), so the platform controller
 * is disabled. Rebinding the same player to a new surface (preview → full screen) is what
 * makes the transition seamless: the decoder keeps running while the view changes.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoSurface(
    player: Player?,
    modifier: Modifier = Modifier,
    resizeMode: Int = AspectRatioFrameLayout.RESIZE_MODE_FIT,
    vlcPlayer: org.videolan.libvlc.MediaPlayer? = null,
) {
    if (vlcPlayer != null) {
        // libVLC draws into its own layout; attach on the way in, detach on the way out.
        AndroidView(
            modifier = modifier,
            factory = { context ->
                org.videolan.libvlc.util.VLCVideoLayout(context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { layout ->
                if (!vlcPlayer.vlcVout.areViewsAttached()) vlcPlayer.attachViews(layout, null, false, false)
            },
            onRelease = { runCatching { vlcPlayer.detachViews() } },
        )
        return
    }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            PlayerView(context).apply {
                useController = false
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                setKeepContentOnPlayerReset(false)
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        update = { view ->
            view.resizeMode = resizeMode
            if (view.player !== player) view.player = player
        },
        onRelease = { view -> view.player = null },
    )
}
