package com.aditv.playback.engine

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.aditv.BuildConfig
import com.aditv.core.AppLog
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

/**
 * Moteur LibVLC (ARMv7). Démarre quand la surface vidéo est attachée. Les mesures sont celles de VLC
 * (statistiques du média) : elles ne sont pas comparables terme à terme avec celles de Media3.
 */
class VlcEngine(
    context: Context,
    url: String,
    startMs: Long?,
    private val isLive: Boolean,
    policy: BufferPolicy,
    private val events: EngineEvents?,
) : PlaybackEngine {
    override val kind = EngineKind.VLC
    @Volatile private var audioDelayUs = 0L
    override fun setAudioDelayMs(ms: Int): Boolean {
        audioDelayUs = ms * 1000L
        if (!released && started) runCatching { mp.audioDelay = audioDelayUs }
        return true
    }
    private val main = Handler(Looper.getMainLooper())
    private val libVlc = LibVLC(context.applicationContext, arrayListOf(
        "--network-caching=${policy.vlcNetworkCachingMs}",
        "--live-caching=${policy.vlcNetworkCachingMs}",
        "--http-reconnect",
        "--audio-time-stretch",
        "-v",
    ))
    private val mp = MediaPlayer(libVlc)
    private val media = Media(libVlc, Uri.parse(url)).apply {
        setHWDecoderEnabled(true, false)
        addOption(":http-user-agent=ADITV/${BuildConfig.VERSION_NAME}")
        if (startMs != null && startMs > 0 && !isLive) addOption(":start-time=${startMs / 1000}")
    }
    private val openedAt = System.currentTimeMillis()
    private var started = false
    private var buffering = true
    private var attached = false
    private var wantPlay = true
    /** Après release(), plus aucun appel natif : un objet VLC libéré peut faire planter le processus. */
    @Volatile private var released = false
    @Volatile private var fpsReported = false
    override var stalls = 0; private set
    override var startupMs: Long? = null; private set

    init {
        mp.media = media
        mp.setEventListener { e ->
            when (e.type) {
                MediaPlayer.Event.Playing -> {
                    if (audioDelayUs != 0L && !released) runCatching { mp.audioDelay = audioDelayUs }
                    buffering = false
                    if (!started) { started = true; startupMs = System.currentTimeMillis() - openedAt }
                    events?.onPlayingChanged(true)
                    // Cadence déclarée par VLC (piste vidéo), pour adapter la fréquence de l'écran.
                    if (!fpsReported) main.postDelayed({
                        if (released || fpsReported) return@postDelayed
                        val v = runCatching { mp.currentVideoTrack }.getOrNull()
                        v?.takeIf { it.frameRateDen > 0 }?.let { com.aditv.playback.FrameRateMatch.normalize(it.frameRateNum.toFloat() / it.frameRateDen) }
                            ?.let { fpsReported = true; events?.onFrameRate(it) }
                    }, 1_500)
                }
                MediaPlayer.Event.Paused -> events?.onPlayingChanged(false)
                MediaPlayer.Event.Buffering -> {
                    val wasBuffering = buffering
                    buffering = e.buffering < 100f
                    if (buffering && !wasBuffering && started) stalls++
                }
                MediaPlayer.Event.EndReached -> main.post { if (!released) events?.onEnded() }
                MediaPlayer.Event.EncounteredError -> main.post { if (!released) events?.onError("VLC n'a pas pu lire ce flux (format, réseau ou serveur).", true, false) }
            }
        }
    }

    override val isPlaying get() = !released && runCatching { mp.isPlaying }.getOrDefault(false)
    override val isBuffering get() = buffering
    override val positionMs get() = if (released) 0L else runCatching { mp.time }.getOrDefault(0L).coerceAtLeast(0)
    override val durationMs get() = if (isLive || released) 0L else runCatching { mp.length }.getOrDefault(0L).coerceAtLeast(0)
    /** VLC n'expose pas la position tampon : la réserve est indiquée « N/D ». */
    override val bufferedMs get() = positionMs
    override val seekable get() = !isLive && !released && runCatching { mp.isSeekable }.getOrDefault(false)
    override val droppedFrames: Int? get() = if (released) null else runCatching { media.stats?.lostPictures }.getOrNull()

    override fun play() { wantPlay = true; if (attached && !released) mp.play() }
    override fun pause() { wantPlay = false; if (!released) runCatching { mp.pause() } }
    override fun seekTo(ms: Long) { if (!released) runCatching { mp.setTime(ms.coerceAtLeast(0)) } }
    override fun holdPaused() { wantPlay = false }

    private fun tracks(list: Array<MediaPlayer.TrackDescription>?, current: Int, group: Int) =
        list.orEmpty().filter { it.id >= 0 }.map { EngineTrack(group, it.id, it.name ?: "Piste ${it.id}", it.id == current) }

    override fun audioTracks() = if (released) emptyList() else tracks(runCatching { mp.audioTracks }.getOrNull(), runCatching { mp.audioTrack }.getOrDefault(-1), 0)
    override fun textTracks() = if (released) emptyList() else tracks(runCatching { mp.spuTracks }.getOrNull(), runCatching { mp.spuTrack }.getOrDefault(-1), 1)
    override val textDisabled get() = released || runCatching { mp.spuTrack }.getOrDefault(-1) < 0
    override fun selectAudio(t: EngineTrack) { if (!released) runCatching { mp.audioTrack = t.id } }
    override fun selectText(t: EngineTrack?) { if (!released) runCatching { mp.spuTrack = t?.id ?: -1 } }

    override fun stats(): List<StatRow> {
        if (released) return emptyList()
        val s = runCatching { media.stats }.getOrNull()
        val v = runCatching { mp.currentVideoTrack }.getOrNull()
        val fps = v?.takeIf { it.frameRateDen > 0 }?.let { it.frameRateNum.toFloat() / it.frameRateDen }
        // Débit d'entrée VLC (octets/ms) → Mb/s.
        val inMbps = s?.inputBitrate?.takeIf { it > 0f }?.let { it * 8f }
        return listOf(
            StatRow("Moteur", "VLC (LibVLC 3)", Nature.DECLARED),
            StatRow("Démarrage", startupMs?.let { "${it} ms" } ?: "N/D", if (startupMs != null) Nature.MEASURED else Nature.NA),
            StatRow("Image", v?.takeIf { it.width > 0 }?.let { "${it.width} × ${it.height}" } ?: "N/D", if ((v?.width ?: 0) > 0) Nature.DECLARED else Nature.NA),
            StatRow("Codec vidéo", v?.codec ?: "N/D", if (v?.codec != null) Nature.DECLARED else Nature.NA),
            StatRow("Images / s", fps?.let { "%.0f".format(it) } ?: "N/D", if (fps != null) Nature.DECLARED else Nature.NA),
            StatRow("Débit reçu", inMbps?.let { "%.1f".format(it).replace('.', ',') + " Mb/s" } ?: "N/D", if (inMbps != null) Nature.MEASURED else Nature.NA),
            StatRow("Bande passante", "N/D", Nature.NA),
            StatRow("Réserve", "N/D", Nature.NA),
            StatRow("Images affichées", s?.displayedPictures?.toString() ?: "N/D", if (s != null) Nature.MEASURED else Nature.NA),
            StatRow("Images perdues", s?.lostPictures?.toString() ?: "N/D", if (s != null) Nature.MEASURED else Nature.NA),
            StatRow("Interruptions", stalls.toString(), Nature.MEASURED),
            StatRow("Audio perdu", s?.lostAbuffers?.let { "$it tampons" } ?: "N/D", if (s != null) Nature.MEASURED else Nature.NA),
            StatRow("Sortie HDMI", "N/D", Nature.NA),
        )
    }

    override fun release() {
        if (released) return
        released = true
        main.removeCallbacksAndMessages(null)
        runCatching { mp.setEventListener(null) }
        runCatching { mp.stop() }
        runCatching { mp.detachViews() }
        runCatching { media.release() }
        runCatching { mp.release() }
        runCatching { libVlc.release() }
        AppLog.d("Session VLC libérée")
    }

    @Composable
    override fun VideoSurface(modifier: Modifier) {
        AndroidView(
            factory = { ctx ->
                VLCVideoLayout(ctx).also { layout ->
                    layout.keepScreenOn = true
                    if (released) return@also
                    mp.attachViews(layout, null, false, false)
                    attached = true
                    if (wantPlay) mp.play()
                }
            },
            modifier = modifier,
        )
        DisposableEffect(Unit) { onDispose { attached = false; if (!released) runCatching { mp.detachViews() } } }
    }
}
