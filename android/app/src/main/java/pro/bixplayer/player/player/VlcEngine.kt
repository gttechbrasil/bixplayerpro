package pro.bixplayer.player.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import timber.log.Timber

/**
 * libVLC-backed engine, the compatibility fallback for streams Media3 refuses (AC-3/E-AC-3
 * without a platform decoder, MPEG-2 video, odd containers). Same contract as [Media3Engine];
 * the surface is attached through [vlcPlayer] by the VLC branch of `VideoSurface`.
 */
class VlcEngine(
    private val context: Context,
    private val messages: PlayerMessages,
    private val userAgent: String = Media3Engine.DEFAULT_USER_AGENT,
) : PlayerEngine {

    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _tracks = MutableStateFlow<List<TrackOption>>(emptyList())
    override val tracks: StateFlow<List<TrackOption>> = _tracks.asStateFlow()

    override var currentUrl: String? = null
        private set

    private var libVlc: LibVLC? = null
    private var player: MediaPlayer? = null

    /** Media3 has nothing to bind here; the VLC layout binds to [vlcPlayer] instead. */
    override val platformPlayer: Player? = null

    val vlcPlayer: MediaPlayer?
        get() = player

    private val listener = MediaPlayer.EventListener { event ->
        when (event.type) {
            MediaPlayer.Event.Opening -> _state.value = PlaybackState.Buffering
            MediaPlayer.Event.Buffering -> if (event.buffering < 100f && _state.value !is PlaybackState.Playing) {
                _state.value = PlaybackState.Buffering
            }
            MediaPlayer.Event.Playing -> _state.value = PlaybackState.Playing
            MediaPlayer.Event.Paused -> _state.value = PlaybackState.Paused
            MediaPlayer.Event.Stopped -> if (currentUrl == null) _state.value = PlaybackState.Idle
            MediaPlayer.Event.EndReached -> _state.value = PlaybackState.Ended
            MediaPlayer.Event.EncounteredError -> {
                Timber.w("vlc error on %s", UrlRedactor.redact(currentUrl))
                _state.value = PlaybackState.Error(messages.generic, VLC_ERROR_CODE)
            }
            MediaPlayer.Event.ESAdded, MediaPlayer.Event.ESDeleted, MediaPlayer.Event.ESSelected -> refreshTracks()
        }
    }

    private fun ensurePlayer(): MediaPlayer {
        player?.let { return it }
        val options = arrayListOf(
            "--http-reconnect",
            "--network-caching=$NETWORK_CACHING_MS",
            "--live-caching=$NETWORK_CACHING_MS",
            "--http-user-agent=$userAgent",
            "--no-video-title-show",
            "-vv".takeIf { false } ?: "--quiet",
        )
        val lib = LibVLC(context, options).also { libVlc = it }
        return MediaPlayer(lib).also {
            it.setEventListener(listener)
            player = it
        }
    }

    override fun prepare(url: String) {
        val mp = ensurePlayer()
        if (url == currentUrl && _state.value !is PlaybackState.Error && mp.isPlaying) return
        currentUrl = url
        _tracks.value = emptyList()
        _state.value = PlaybackState.Buffering
        Timber.d("vlc prepare %s", UrlRedactor.redact(url))
        val media = Media(libVlc, Uri.parse(url)).apply {
            setHWDecoderEnabled(true, false)
            addOption(":network-caching=$NETWORK_CACHING_MS")
        }
        mp.media = media
        media.release()
        mp.play()
    }

    override fun play() {
        player?.let { if (!it.isPlaying) it.play() }
    }

    override fun pause() {
        player?.pause()
    }

    override fun stop() {
        currentUrl = null
        player?.stop()
        _tracks.value = emptyList()
        _state.value = PlaybackState.Idle
    }

    override val positionMs: Long
        get() = player?.time?.coerceAtLeast(0L) ?: 0L

    override val durationMs: Long
        get() = player?.length?.takeIf { it > 0 } ?: 0L

    override val isSeekable: Boolean
        get() = player?.let { it.isSeekable && durationMs > 0 } ?: false

    override fun seekTo(positionMs: Long) {
        val mp = player ?: return
        val max = durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE
        mp.time = positionMs.coerceIn(0L, max)
    }

    override fun selectTrack(option: TrackOption) {
        val mp = player ?: return
        val id = option.id.removePrefix("vlc:").toIntOrNull() ?: return
        if (option.type == TrackType.AUDIO) mp.setAudioTrack(id) else mp.setSpuTrack(id)
        refreshTracks()
    }

    override fun disableSubtitles() {
        player?.setSpuTrack(-1)
        refreshTracks()
    }

    override fun release() {
        player?.let {
            it.setEventListener(null)
            it.stop()
            it.detachViews()
            it.release()
        }
        player = null
        libVlc?.release()
        libVlc = null
        _tracks.value = emptyList()
        _state.value = PlaybackState.Idle
    }

    private fun refreshTracks() {
        val mp = player ?: return
        val audio = mp.audioTracks.orEmpty().filter { it.id >= 0 }.map {
            TrackOption("vlc:${it.id}", TrackType.AUDIO, it.name, it.id == mp.audioTrack)
        }
        val subtitles = mp.spuTracks.orEmpty().filter { it.id >= 0 }.map {
            TrackOption("vlc:${it.id}", TrackType.SUBTITLE, it.name, it.id == mp.spuTrack)
        }
        _tracks.value = audio + subtitles
    }

    companion object {
        const val VLC_ERROR_CODE = 9001
        private const val NETWORK_CACHING_MS = 1500
    }
}
