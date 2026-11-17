package pro.bixplayer.player.ui.components

import java.lang.ref.WeakReference
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import timber.log.Timber
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
                val fill = resizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                // Preview -> full screen: the new surface arrives while the old one is attached,
                // and the old one's release then left libVLC with none until some state happened
                // to change. The surface is handed over here, and only its owner may detach it.
                if (vlcSurfaceOwner?.get() !== layout || !vlcPlayer.vlcVout.areViewsAttached()) {
                    attachVlc(vlcPlayer, layout)
                }
                SurfaceWatcher.on(layout).also {
                    it.player = vlcPlayer
                    it.onFill = { onVlcFill(fill) }
                }
                // "Tela cheia" and "Tamanho original" must mean the same as on Media3 (M5-032).
                onVlcFill(fill)
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

private fun attachVlc(player: MediaPlayer, layout: VLCVideoLayout) {
    // detachViews also drops the helper of an output libVLC already let go by itself
    player.detachViews()
    player.attachViews(layout, null, false, false)
    vlcSurfaceOwner = WeakReference(layout)
}

/**
 * When the video surface is destroyed (screen off with the PiP window open, and on some phones
 * the PiP transition itself), libVLC detaches by itself and never comes back when the surface
 * does: sound with a black window (M5-047). Nothing in the composition changes at that point, so
 * the layout's own surface callback attaches again, for the current owner only.
 */
private class SurfaceWatcher(private val layout: VLCVideoLayout) : SurfaceHolder.Callback {
    var player: MediaPlayer? = null
    var onFill: () -> Unit = {}
    private var watching: SurfaceView? = null

    private fun watch() {
        val surface = layout.findSurfaceView() ?: return
        if (surface === watching) return
        watching?.holder?.removeCallback(this)
        surface.holder.addCallback(this)
        watching = surface
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        layout.post {
            val mp = player ?: return@post
            if (mp.isReleased || vlcSurfaceOwner?.get() !== layout || mp.vlcVout.areViewsAttached()) return@post
            Timber.d("vlc surface is back: attaching again")
            attachVlc(mp, layout)
            onFill()
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) = Unit

    override fun surfaceDestroyed(holder: SurfaceHolder) = Unit

    companion object {
        /**
         * Kept in the layout's tag so it lives and dies with the view. The layout inflates its
         * SurfaceView on the first attach, so it is looked up again every time.
         */
        fun on(layout: VLCVideoLayout): SurfaceWatcher {
            val watcher = layout.tag as? SurfaceWatcher ?: SurfaceWatcher(layout).also { layout.tag = it }
            watcher.watch()
            return watcher
        }
    }
}

private fun View.findSurfaceView(): SurfaceView? = when (this) {
    is SurfaceView -> this
    is ViewGroup -> (0 until childCount).firstNotNullOfOrNull { getChildAt(it).findSurfaceView() }
    else -> null
}
