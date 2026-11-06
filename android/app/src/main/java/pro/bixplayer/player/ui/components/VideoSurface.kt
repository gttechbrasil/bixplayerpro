package pro.bixplayer.player.ui.components

import java.lang.ref.WeakReference
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/** The layout libVLC draws into right now. There is one player at a time; main thread only. */
private var vlcSurfaceOwner: WeakReference<VLCVideoLayout>? = null

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
    vlcPlayer: MediaPlayer? = null,
    onVlcFill: (Boolean) -> Unit = {},
) {
    if (vlcPlayer != null) {
        // libVLC draws into its own layout; attach on the way in, detach on the way out.
        AndroidView(
            modifier = modifier,
            factory = { context ->
                VLCVideoLayout(context).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { layout ->
                // Preview -> full screen: the new surface arrives while the old one is attached,
                // and the old one's release then left libVLC with none until some state happened
                // to change. The surface is handed over here, and only its owner may detach it.
                if (vlcSurfaceOwner?.get() !== layout || !vlcPlayer.vlcVout.areViewsAttached()) {
                    if (vlcPlayer.vlcVout.areViewsAttached()) vlcPlayer.detachViews()
                    vlcPlayer.attachViews(layout, null, false, false)
                    vlcSurfaceOwner = WeakReference(layout)
                }
                // "Tela cheia" and "Tamanho original" must mean the same as on Media3 (M5-032).
                onVlcFill(resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
            },
            onRelease = { layout ->
                if (vlcSurfaceOwner?.get() === layout) {
                    vlcSurfaceOwner = null
                    runCatching { vlcPlayer.detachViews() }
                }
            },
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
