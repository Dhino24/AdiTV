package com.aditv.playback.engine

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.aditv.playback.PlaybackErrors
import java.util.Locale

/** Moteur Media3 (ExoPlayer). Mesures lues dans le lecteur lui-même. */
@OptIn(UnstableApi::class)
class Media3Engine(private val context: Context, val player: ExoPlayer, private val events: EngineEvents?) : PlaybackEngine {
    override val kind = EngineKind.MEDIA3
    private val openedAt = System.currentTimeMillis()
    private var started = false
    override var stalls = 0; private set
    override var startupMs: Long? = null; private set

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_READY && !started) { started = true; startupMs = System.currentTimeMillis() - openedAt }
            else if (state == Player.STATE_BUFFERING && started) stalls++
            if (state == Player.STATE_ENDED) events?.onEnded()
        }
        override fun onPlayerError(e: PlaybackException) {
            events?.onError(PlaybackErrors.message(e), PlaybackErrors.retryable(e), PlaybackErrors.formatProblem(e))
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) { events?.onPlayingChanged(isPlaying) }
    }
    init { player.addListener(listener) }

    override val isPlaying get() = player.isPlaying
    override val isBuffering get() = player.playbackState == Player.STATE_BUFFERING
    override val positionMs get() = player.currentPosition
    override val durationMs get() = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
    override val bufferedMs get() = player.bufferedPosition
    override val seekable get() = player.isCurrentMediaItemSeekable
    override val droppedFrames get() = player.videoDecoderCounters?.also { it.ensureUpdated() }?.droppedBufferCount

    override fun play() = player.play()
    override fun pause() = player.pause()
    override fun seekTo(ms: Long) = player.seekTo(ms)
    override fun holdPaused() { player.playWhenReady = false }

    private fun tracks(type: Int): List<EngineTrack> = buildList {
        player.currentTracks.groups.forEachIndexed { gi, g ->
            if (g.type != type) return@forEachIndexed
            for (ti in 0 until g.length) {
                if (!g.isTrackSupported(ti)) continue
                val f = g.getTrackFormat(ti)
                val lang = f.language?.let { runCatching { Locale.forLanguageTag(it).getDisplayLanguage(Locale.FRENCH) }.getOrNull() }?.ifBlank { null }
                val parts = listOfNotNull(
                    f.label ?: lang?.replaceFirstChar { it.uppercase() } ?: "Piste ${size + 1}",
                    lang?.takeIf { f.label != null }?.replaceFirstChar { it.uppercase() },
                    f.channelCount.takeIf { it > 0 && type == C.TRACK_TYPE_AUDIO }?.let { if (it >= 6) "5.1" else if (it == 2) "stéréo" else "$it can." },
                    f.sampleMimeType?.substringAfter('/')?.uppercase()?.takeIf { type == C.TRACK_TYPE_AUDIO },
                )
                add(EngineTrack(gi, ti, parts.joinToString(" · "), g.isTrackSelected(ti)))
            }
        }
    }

    override fun audioTracks() = tracks(C.TRACK_TYPE_AUDIO)
    override fun textTracks() = tracks(C.TRACK_TYPE_TEXT)
    override val textDisabled get() = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)

    private fun select(type: Int, t: EngineTrack) {
        val group = player.currentTracks.groups[t.group].mediaTrackGroup
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(type, false).setOverrideForType(TrackSelectionOverride(group, t.id)).build()
    }
    override fun selectAudio(t: EngineTrack) = select(C.TRACK_TYPE_AUDIO, t)
    override fun selectText(t: EngineTrack?) {
        if (t == null) player.trackSelectionParameters = player.trackSelectionParameters.buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
        else select(C.TRACK_TYPE_TEXT, t)
    }

    override fun stats(): List<StatRow> {
        val vf = player.videoFormat
        val af = player.audioFormat
        val size = player.videoSize
        val bandwidth = DefaultBandwidthMeter.getSingletonInstance(context).bitrateEstimate
        val reserve = ((player.bufferedPosition - player.currentPosition).coerceAtLeast(0) / 1000)
        fun mbps(b: Long) = "%.1f".format(b / 1_000_000f).replace('.', ',') + " Mb/s"
        val hdr = when (vf?.colorInfo?.colorTransfer) { C.COLOR_TRANSFER_ST2084 -> "HDR10 (PQ)"; C.COLOR_TRANSFER_HLG -> "HLG"; null -> null; else -> "SDR" }
        return listOf(
            StatRow("Moteur", "Media3", Nature.DECLARED),
            StatRow("Démarrage", startupMs?.let { "${it} ms" } ?: "N/D", if (startupMs != null) Nature.MEASURED else Nature.NA),
            StatRow("Image décodée", if (size.width > 0) "${size.width} × ${size.height}" else "N/D", if (size.width > 0) Nature.MEASURED else Nature.NA),
            StatRow("Codec vidéo", vf?.codecs ?: vf?.sampleMimeType?.substringAfter('/') ?: "N/D", if (vf != null) Nature.DECLARED else Nature.NA),
            StatRow("Images / s", vf?.frameRate?.takeIf { it > 0 }?.let { "%.0f".format(it) } ?: "N/D", if ((vf?.frameRate ?: 0f) > 0) Nature.DECLARED else Nature.NA),
            StatRow("HDR", hdr ?: "N/D", if (hdr != null) Nature.DECLARED else Nature.NA),
            StatRow("Débit vidéo", vf?.bitrate?.takeIf { it > 0 }?.let { mbps(it.toLong()) } ?: "N/D", if ((vf?.bitrate ?: 0) > 0) Nature.DECLARED else Nature.NA),
            StatRow("Bande passante", if (bandwidth > 0) "≈ " + mbps(bandwidth) else "N/D", if (bandwidth > 0) Nature.ESTIMATED else Nature.NA),
            StatRow("Réserve", "$reserve s", Nature.MEASURED),
            StatRow("Images perdues", droppedFrames?.toString() ?: "N/D", if (droppedFrames != null) Nature.MEASURED else Nature.NA),
            StatRow("Interruptions", stalls.toString(), Nature.MEASURED),
            StatRow("Audio", af?.let { listOfNotNull(it.sampleMimeType?.substringAfter('/')?.uppercase(), it.channelCount.takeIf { c -> c > 0 }?.let { c -> if (c >= 6) "5.1" else if (c == 2) "stéréo" else "$c can." }).joinToString(" · ") } ?: "N/D", if (af != null) Nature.DECLARED else Nature.NA),
            StatRow("Sortie HDMI", "N/D", Nature.NA),
        )
    }

    override fun release() { player.removeListener(listener) }

    @Composable
    override fun VideoSurface(modifier: Modifier) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                    keepScreenOn = true
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { it.player = player },
            modifier = modifier,
        )
    }
}
