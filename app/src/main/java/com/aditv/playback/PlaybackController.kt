package com.aditv.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.aditv.BuildConfig
import com.aditv.core.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import okhttp3.OkHttpClient

/**
 * Une seule session de lecture active dans toute l'application : toute ouverture libère d'abord
 * la précédente (contrainte fournisseur sur le nombre de connexions). Moteur : Media3 (LibVLC en phase 4).
 */
@OptIn(UnstableApi::class)
class PlaybackController(private val context: Context, private val streamClient: OkHttpClient) {
    private var player: ExoPlayer? = null
    private val _sessions = MutableStateFlow(0)
    /** Nombre de sessions ouvertes (0 ou 1) : vérifié par les tests. */
    val activeSessions: StateFlow<Int> = _sessions

    val current: ExoPlayer? get() = player
    var request: PlaybackRequest? = null; private set

    /** Préférences de pistes et de qualité appliquées avant la préparation. */
    data class TrackPrefs(val audioLanguages: List<String>, val subtitlesFr: Boolean, val maxQuality: Int, val forceHighest: Boolean)

    fun open(req: PlaybackRequest, prefs: TrackPrefs? = null): ExoPlayer {
        release()
        val dataSource = OkHttpDataSource.Factory(streamClient).setUserAgent("ADITV/${BuildConfig.VERSION_NAME}")
        val p = ExoPlayer.Builder(context)
            .setRenderersFactory(DefaultRenderersFactory(context).setEnableDecoderFallback(true))
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSource))
            .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
        val item = MediaItem.Builder().setUri(req.url).apply {
            mimeTypeFor(req.url)?.let { setMimeType(it) }
            if (req.isLive) setLiveConfiguration(MediaItem.LiveConfiguration.Builder().build())
        }.build()
        if (prefs != null) p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().apply {
            setPreferredAudioLanguages(*prefs.audioLanguages.toTypedArray())
            if (prefs.subtitlesFr) setPreferredTextLanguages("fr") // VOSTFR : sous-titres français si le flux en contient
            when (prefs.maxQuality) {
                3 -> setMaxVideoSize(1920, 1080)
                2 -> setMaxVideoSize(1280, 720)
                1 -> setMaxVideoSize(854, 576)
                else -> clearVideoSizeConstraints()
            }
            // Flux multi-débits : débit le plus élevé supporté plutôt qu'adaptation au réseau (réglable).
            setForceHighestSupportedBitrate(prefs.forceHighest)
        }.build()
        if (req.startPositionMs != null && !req.isLive) p.setMediaItem(item, req.startPositionMs) else p.setMediaItem(item)
        p.prepare()
        p.playWhenReady = true
        player = p
        request = req
        _sessions.value = 1
        AppLog.d("Lecture ouverte : $req")
        return p
    }

    fun release() {
        player?.let {
            it.stop()
            it.release()
            AppLog.d("Session de lecture libérée")
        }
        player = null
        request = null
        _sessions.value = 0
    }

    companion object {
        fun mimeTypeFor(url: String): String? {
            val path = url.substringBefore('?').lowercase()
            return when {
                path.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
                path.endsWith(".ts") -> MimeTypes.VIDEO_MP2T
                else -> null // mp4, mkv… : détection par le contenu
            }
        }
    }
}
