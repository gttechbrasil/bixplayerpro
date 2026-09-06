package pro.bixplayer.player.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * ExoPlayer-backed engine. Tuned for IPTV: tolerant TS extractor, cross-protocol redirects,
 * a configurable User-Agent (many providers whitelist by it) and a live target offset that
 * trades a few seconds of latency for fewer rebuffers.
 */
@OptIn(UnstableApi::class)
class Media3Engine(
    private val context: Context,
    private val messages: PlayerMessages,
    private val userAgent: String = DEFAULT_USER_AGENT,
) : PlayerEngine {

    private val _state = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    override val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _tracks = MutableStateFlow<List<TrackOption>>(emptyList())
    override val tracks: StateFlow<List<TrackOption>> = _tracks.asStateFlow()

    override var currentUrl: String? = null
        private set

    private var player: ExoPlayer? = null

    override val platformPlayer: Player?
        get() = player

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) = publishState()

        override fun onIsPlayingChanged(isPlaying: Boolean) = publishState()

        override fun onPlayerError(error: PlaybackException) {
            Timber.w(
                "media3 error %s (%d) on %s",
                error.errorCodeName, error.errorCode, UrlRedactor.redact(currentUrl),
            )
            _state.value = PlaybackState.Error(messageFor(error), error.errorCode, error)
        }

        override fun onTracksChanged(tracks: Tracks) {
            _tracks.value = tracks.toOptions()
        }
    }

    private fun ensurePlayer(): ExoPlayer {
        player?.let { return it }

        val http: HttpDataSource.Factory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(READ_TIMEOUT_MS)
        val dataSource = DefaultDataSource.Factory(context, http)

        // Providers mux TS carelessly: keyframes that are not IDR, PMT changes mid-stream,
        // timestamps far from the start. These flags make the extractor accept them.
        val extractors = DefaultExtractorsFactory()
            .setTsExtractorFlags(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                    DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
                    DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS,
            )
            .setTsExtractorMode(TsExtractor.MODE_SINGLE_PMT)
            .setTsExtractorTimestampSearchBytes(TsExtractor.DEFAULT_TIMESTAMP_SEARCH_BYTES * 3)
            .setConstantBitrateSeekingEnabled(true)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS,
            )
            .build()

        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource, extractors))
            .setLoadControl(loadControl)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .also {
                it.addListener(listener)
                player = it
            }
    }

    override fun prepare(url: String) {
        val exo = ensurePlayer()
        if (url == currentUrl && _state.value !is PlaybackState.Error && exo.playbackState != Player.STATE_IDLE) {
            exo.playWhenReady = true
            return
        }
        currentUrl = url
        _tracks.value = emptyList()
        _state.value = PlaybackState.Buffering
        Timber.d("media3 prepare %s", UrlRedactor.redact(url))

        val item = MediaItem.Builder()
            .setUri(url)
            .setLiveConfiguration(
                MediaItem.LiveConfiguration.Builder()
                    .setTargetOffsetMs(LIVE_TARGET_OFFSET_MS)
                    .build(),
            )
            .build()
        exo.setMediaItem(item)
        exo.prepare()
        exo.playWhenReady = true
    }

    override fun play() {
        player?.playWhenReady = true
    }

    override fun pause() {
        player?.playWhenReady = false
    }

    override fun stop() {
        player?.let {
            it.stop()
            it.clearMediaItems()
        }
        currentUrl = null
        _tracks.value = emptyList()
        _state.value = PlaybackState.Idle
    }

    override val positionMs: Long
        get() = player?.currentPosition?.coerceAtLeast(0L) ?: 0L

    override val durationMs: Long
        get() = player?.duration?.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L

    override val isSeekable: Boolean
        get() = player?.let { it.isCurrentMediaItemSeekable && !it.isCurrentMediaItemLive && durationMs > 0 } ?: false

    override fun seekTo(positionMs: Long) {
        val exo = player ?: return
        val max = durationMs.takeIf { it > 0 } ?: Long.MAX_VALUE
        exo.seekTo(positionMs.coerceIn(0L, max))
    }

    override fun selectTrack(option: TrackOption) {
        val exo = player ?: return
        val (groupIndex, trackIndex) = option.id.split(':').map { it.toInt() }
        val group = exo.currentTracks.groups.getOrNull(groupIndex) ?: return
        val trackType = if (option.type == TrackType.AUDIO) C.TRACK_TYPE_AUDIO else C.TRACK_TYPE_TEXT
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(trackType, false)
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
            .build()
    }

    override fun disableSubtitles() {
        val exo = player ?: return
        exo.trackSelectionParameters = exo.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    override fun release() {
        player?.let {
            it.removeListener(listener)
            it.release()
        }
        player = null
        _tracks.value = emptyList()
        _state.value = PlaybackState.Idle
    }

    private fun publishState() {
        val exo = player ?: return
        if (_state.value is PlaybackState.Error) return
        _state.value = when (exo.playbackState) {
            Player.STATE_BUFFERING -> PlaybackState.Buffering
            Player.STATE_READY -> if (exo.playWhenReady) PlaybackState.Playing else PlaybackState.Paused
            Player.STATE_ENDED -> PlaybackState.Ended
            else -> PlaybackState.Idle
        }
    }

    private fun messageFor(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
        -> messages.network

        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
            val status = (error.cause as? HttpDataSource.InvalidResponseCodeException)?.responseCode ?: 0
            messages.httpStatus(status)
        }

        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        -> messages.unsupported

        else -> messages.generic
    }

    private fun Tracks.toOptions(): List<TrackOption> = buildList {
        groups.forEachIndexed { groupIndex, group ->
            val type = when (group.type) {
                C.TRACK_TYPE_AUDIO -> TrackType.AUDIO
                C.TRACK_TYPE_TEXT -> TrackType.SUBTITLE
                else -> return@forEachIndexed
            }
            for (i in 0 until group.length) {
                if (!group.isTrackSupported(i)) continue
                val format = group.getTrackFormat(i)
                val language = format.language?.let { Locale.forLanguageTag(it).displayLanguage }
                    ?.takeIf { it.isNotBlank() }
                val label = format.label ?: language ?: "${type.name.lowercase()} ${size + 1}"
                add(
                    TrackOption(
                        id = "$groupIndex:$i",
                        type = type,
                        label = label.replaceFirstChar { it.uppercase() },
                        selected = group.isTrackSelected(i),
                    ),
                )
            }
        }
    }

    companion object {
        /** Many panels only serve the "smart TV" agents; this one is accepted everywhere. */
        const val DEFAULT_USER_AGENT = "BixPlayer/1.0 (Android TV; Media3)"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 15_000
        private const val LIVE_TARGET_OFFSET_MS = 6_000L
        private const val MIN_BUFFER_MS = 15_000
        private const val MAX_BUFFER_MS = 50_000
        private const val BUFFER_FOR_PLAYBACK_MS = 1_500
        private const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 3_000
    }
}
