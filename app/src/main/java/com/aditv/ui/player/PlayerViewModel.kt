package com.aditv.ui.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.aditv.data.Session
import com.aditv.data.db.EpisodeEntity
import com.aditv.data.library.ProgressPolicy
import com.aditv.data.provider.StreamTarget
import com.aditv.di.AppContainer
import com.aditv.playback.PlaybackErrors
import com.aditv.playback.PlaybackRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class NextUp(val episode: EpisodeEntity, val secondsLeft: Int)

/**
 * Lecture d'un contenu : résolution (film, épisode, chaîne), URL construite à la demande,
 * progression enregistrée toutes les 10 s et à chaque pause / sortie.
 */
class PlayerViewModel(
    private val c: AppContainer,
    private val session: Session.Active,
    private val contentId: String,
    private val fromStart: Boolean,
) : ViewModel() {
    var title by mutableStateOf(""); private set
    var subtitle by mutableStateOf<String?>(null); private set
    var isLive by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var canRetry by mutableStateOf(false); private set
    var nextUp by mutableStateOf<NextUp?>(null); private set
    var player by mutableStateOf<ExoPlayer?>(null); private set

    private var request: PlaybackRequest? = null
    private var saver: Job? = null
    private var nextJob: Job? = null
    private var retries = 0
    private val accountId get() = session.account.id

    private val listener = object : Player.Listener {
        override fun onPlayerError(e: PlaybackException) {
            error = PlaybackErrors.message(e)
            canRetry = PlaybackErrors.retryable(e) || retries < 1
            viewModelScope.launch { saveNow() }
        }
        override fun onPlaybackStateChanged(state: Int) {
            if (state == Player.STATE_ENDED) viewModelScope.launch { saveNow(); onEnded() }
        }
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (!isPlaying) viewModelScope.launch { saveNow() }
        }
    }

    fun start() = viewModelScope.launch {
        error = null
        val req = runCatching { resolve(contentId, fromStart) }.getOrElse { error = it.message ?: "Lecture impossible."; canRetry = false; return@launch }
        play(req)
    }

    private fun play(req: PlaybackRequest, resumeAtMs: Long? = null) {
        val effective = if (resumeAtMs != null) PlaybackRequest(req.url, req.title, req.subtitle, req.isLive, resumeAtMs, req.contentId, req.kind, req.seriesItemId) else req
        request = effective
        title = req.title; subtitle = req.subtitle; isLive = req.isLive
        player?.removeListener(listener)
        val p = c.playback.open(effective)
        p.addListener(listener)
        player = p
        saver?.cancel()
        saver = viewModelScope.launch { while (isActive) { delay(10_000); if (p.isPlaying) saveNow() } }
    }

    /** Résout un identifiant de contenu en demande de lecture. */
    private suspend fun resolve(id: String, fromStart: Boolean): PlaybackRequest {
        val adapter = session.adapter
        if (id.contains("|EPISODE|")) {
            val ep = c.db.library().episode(id) ?: error("Épisode introuvable dans le cache.")
            return episodeRequest(ep, fromStart)
        }
        val item = c.db.catalog().observeItem(id).first() ?: error("Contenu introuvable.")
        val meta = c.db.metadata().match(id)?.takeIf { it.status == "confirmed" }?.tmdbId?.let { c.db.metadata().metadata(if (item.type == "SERIES") "tv" else "movie", it) }
        return when (item.type) {
            "LIVE" -> PlaybackRequest(
                adapter.streamUrl(StreamTarget.Live(item.providerId)) ?: error(DEMO), item.title, "DIRECT", true, null, null, "LIVE", null,
            )
            "MOVIE" -> {
                val saved = c.library.progress(accountId, id)
                val start = if (fromStart) null else saved?.let { ProgressPolicy.resumePosition(it.positionMs, it.durationMs, it.completed) }
                PlaybackRequest(
                    adapter.streamUrl(StreamTarget.Movie(item.providerId, item.containerExt)) ?: error(DEMO),
                    meta?.title ?: item.title, item.year?.toString(), false, start, id, "MOVIE", null,
                )
            }
            else -> { // SERIES : épisode en cours ou suivant
                if (c.db.library().episodesNow(id).isEmpty()) c.library.refreshEpisodes(accountId, id, adapter)
                val (ep, _) = c.library.episodeToResume(accountId, id) ?: error("Aucun épisode disponible pour cette série.")
                episodeRequest(ep, fromStart)
            }
        }
    }

    private suspend fun episodeRequest(ep: EpisodeEntity, fromStart: Boolean): PlaybackRequest {
        val series = c.db.catalog().observeItem(ep.seriesItemId).first()
        val meta = c.db.metadata().match(ep.seriesItemId)?.takeIf { it.status == "confirmed" }?.tmdbId?.let { c.db.metadata().metadata("tv", it) }
        val saved = c.library.progress(accountId, ep.id)
        val start = if (fromStart) null else saved?.let { ProgressPolicy.resumePosition(it.positionMs, it.durationMs, it.completed) }
        return PlaybackRequest(
            session.adapter.streamUrl(StreamTarget.Episode(ep.providerId, ep.containerExt)) ?: error(DEMO),
            meta?.title ?: series?.title ?: "Série", "S${ep.season} É${ep.number ?: "?"} · ${ep.title}", false, start, ep.id, "EPISODE", ep.seriesItemId,
        )
    }

    suspend fun saveNow() {
        val r = request ?: return
        val p = player ?: return
        val id = r.contentId ?: return
        val dur = p.duration.takeIf { it > 0 } ?: return
        c.library.saveProgress(accountId, id, r.kind, r.seriesItemId, p.currentPosition, dur)
    }

    private suspend fun onEnded() {
        val r = request ?: return
        if (r.kind != "EPISODE" || r.seriesItemId == null || r.contentId == null) return
        val next = c.library.nextEpisode(r.seriesItemId, r.contentId) ?: return
        nextJob?.cancel()
        nextJob = viewModelScope.launch {
            for (s in 10 downTo 1) { nextUp = NextUp(next, s); delay(1_000) }
            playNext()
        }
    }

    fun playNext() {
        val n = nextUp?.episode ?: return
        nextJob?.cancel(); nextUp = null
        viewModelScope.launch { runCatching { play(episodeRequest(n, fromStart = false)) }.onFailure { error = it.message } }
    }

    fun cancelNext() { nextJob?.cancel(); nextUp = null }

    fun retry() {
        retries++
        val r = request ?: return start().let { }
        error = null
        play(r, resumeAtMs = player?.currentPosition?.takeIf { !r.isLive && it > 0 } ?: r.startPositionMs)
    }

    /** Mise en veille / sortie de l'app : on enregistre puis on libère la session (aucune seconde session au réveil). */
    fun suspendSession() {
        persistSnapshot()
        val pos = player?.currentPosition
        val r = request
        player?.removeListener(listener)
        c.playback.release()
        player = null
        suspendedAt = if (r != null && !r.isLive) pos else null
    }

    private var suspendedAt: Long? = null

    /** Retour au premier plan : réouverture en pause à la même position. */
    fun resumeSession() {
        val r = request ?: return
        if (player != null) return
        play(r, resumeAtMs = suspendedAt)
        player?.playWhenReady = false
    }

    override fun onCleared() = close()

    /** Sauvegarde finale puis libération, appelée en quittant l'écran. */
    fun close() {
        persistSnapshot()
        player?.removeListener(listener)
        if (c.playback.current === player) c.playback.release()
        player = null
    }

    /** Lit la position sur le thread principal, puis écrit hors de la portée du ViewModel (qui peut être détruit). */
    private fun persistSnapshot() {
        val r = request ?: return
        val p = player ?: return
        val id = r.contentId ?: return
        val dur = p.duration.takeIf { it > 0 } ?: return
        val pos = p.currentPosition
        c.appScope.launch { c.library.saveProgress(accountId, id, r.kind, r.seriesItemId, pos, dur) }
    }

    private companion object { const val DEMO = "Lecture indisponible en mode démo : aucun flux n'est associé aux contenus fictifs." }
}
