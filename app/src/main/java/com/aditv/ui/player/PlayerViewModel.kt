package com.aditv.ui.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aditv.playback.engine.BufferPolicy
import com.aditv.playback.engine.EngineChoice
import com.aditv.playback.engine.EngineEvents
import com.aditv.playback.engine.EngineKind
import com.aditv.playback.engine.EnginePolicy
import com.aditv.playback.engine.EngineStore
import com.aditv.playback.engine.PlaybackEngine
import com.aditv.data.Session
import com.aditv.data.db.CatalogCard
import com.aditv.data.db.CatalogItemEntity
import com.aditv.data.db.EpisodeEntity
import com.aditv.data.db.FavoriteChannelEntity
import com.aditv.data.epg.Programme
import com.aditv.data.library.ProgressPolicy
import com.aditv.data.provider.StreamTarget
import com.aditv.data.variants.VariantPrefs
import com.aditv.data.variants.VariantRanking
import com.aditv.data.variants.collapsed
import com.aditv.data.variants.variantBadge
import com.aditv.di.AppContainer
import com.aditv.playback.PlaybackController
import com.aditv.playback.PlaybackErrors
import com.aditv.playback.PlaybackRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class NextUp(val episode: EpisodeEntity, val secondsLeft: Int)

/** Version disponible du contenu en cours (langue / qualité annoncées par le fournisseur). */
data class VersionOption(val itemId: String, val label: String, val selected: Boolean)

/**
 * Lecture d'un contenu : résolution (film, épisode, chaîne), URL construite à la demande,
 * progression enregistrée toutes les 10 s et à chaque pause / sortie. En direct : zapping dans la
 * liste d'origine, favoris, maintenant / ensuite. Versions : la meilleure selon les préférences, changeable.
 */
class PlayerViewModel(
    private val c: AppContainer,
    private val session: Session.Active,
    private val contentId: String,
    private val fromStart: Boolean,
    private val liveList: String?,
) : ViewModel() {
    var title by mutableStateOf(""); private set
    var subtitle by mutableStateOf<String?>(null); private set
    var isLive by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null); private set
    var canRetry by mutableStateOf(false); private set
    var nextUp by mutableStateOf<NextUp?>(null); private set
    var engine by mutableStateOf<PlaybackEngine?>(null); private set
    /** Choix pour ce contenu (Auto par défaut) et moteur réellement utilisé. */
    var engineChoice by mutableStateOf(EngineChoice.AUTO); private set
    /** Bascule automatique effectuée (affichée, jamais silencieuse). */
    var engineNote by mutableStateOf<String?>(null); private set
    var turbo by mutableStateOf(false); private set
    var versions by mutableStateOf<List<VersionOption>>(emptyList()); private set
    var isFavorite by mutableStateOf(false); private set
    var nowNext by mutableStateOf<List<Programme>>(emptyList()); private set
    /** Bandeau de chaîne affiché au zapping (position dans la liste). */
    var channelPosition by mutableStateOf<String?>(null); private set
    var catchupAvailable by mutableStateOf(false); private set

    private var request: PlaybackRequest? = null
    private var currentItem: CatalogItemEntity? = null
    private var channels: List<CatalogCard> = emptyList()
    private var prefs = VariantPrefs()
    private var forceHighest = true
    private var saver: Job? = null
    private var nextJob: Job? = null
    private var epgJob: Job? = null
    private var retries = 0
    private var suspendedAt: Long? = null
    private val accountId get() = session.account.id
    private val store = c.engineStore
    private var fellBack = false
    private var sessionOpenedAt = 0L
    private var lastError: String? = null
    private val choiceKey get() = currentItem?.variantKey ?: request?.contentId ?: contentId

    private val events = object : EngineEvents {
        override fun onError(message: String, retryable: Boolean, formatProblem: Boolean) {
            val current = engine?.kind ?: EngineKind.MEDIA3
            lastError = message
            // « Auto » : une seule bascule vers VLC, uniquement si Media3 refuse le format.
            if (EnginePolicy.shouldFallback(engineChoice, current, formatProblem, fellBack)) {
                fellBack = true
                engineNote = "Media3 ne lit pas ce format : bascule automatique vers VLC."
                val r = request ?: return
                val pos = engine?.positionMs?.takeIf { !r.isLive && it > 0 } ?: r.startPositionMs
                play(r, resumeAtMs = pos, kind = EngineKind.VLC)
                return
            }
            error = message
            canRetry = retryable || retries < 1
            persistSnapshot()
        }
        override fun onEnded() { viewModelScope.launch { persistSnapshot(); onEnded() } }
        override fun onPlayingChanged(playing: Boolean) { if (!playing) persistSnapshot() }
    }

    fun start() = viewModelScope.launch {
        error = null
        prefs = c.prefs.variantPrefs.first()
        forceHighest = c.prefs.forceHighestBitrate.first()
        turbo = store.turboDefault
        val req = runCatching { resolve(contentId, fromStart, preferBestVariant = true) }
            .getOrElse { error = it.message ?: "Lecture impossible."; canRetry = false; return@launch }
        play(req)
        if (isLive) loadChannelList()
    }

    private fun trackPrefs(lang: String?) = PlaybackController.TrackPrefs(
        audioLanguages = (prefs.languages.flatMap { if (it == "vostfr" || it == "multi") emptyList() else listOf(it) } + listOf("fr")).distinct(),
        subtitlesFr = lang == "vostfr",
        maxQuality = prefs.maxQuality,
        forceHighest = forceHighest,
    )

    private fun play(req: PlaybackRequest, resumeAtMs: Long? = null, kind: EngineKind? = null) {
        val effective = if (resumeAtMs != null) PlaybackRequest(req.url, req.title, req.subtitle, req.isLive, resumeAtMs, req.contentId, req.kind, req.seriesItemId) else req
        logSession()
        request = effective
        title = req.title; subtitle = req.subtitle; isLive = req.isLive
        error = null
        engineChoice = store.choiceFor(choiceKey) ?: store.defaultChoice
        if (kind == null) { fellBack = false; engineNote = null }
        val k = kind ?: EnginePolicy.initial(engineChoice)
        val e = c.playback.openEngine(effective, trackPrefs(currentItem?.lang), k, BufferPolicy(turbo), events)
        engine = e
        sessionOpenedAt = System.currentTimeMillis(); lastError = null
        saver?.cancel()
        saver = viewModelScope.launch { while (isActive) { delay(10_000); if (e.isPlaying) persistSnapshot() } }
        refreshLiveInfo()
    }

    /** Choix du moteur pour ce contenu (mémorisé), appliqué tout de suite à la même position. */
    fun chooseEngine(choice: EngineChoice) {
        store.setChoice(choiceKey, choice)
        engineChoice = choice
        restartHere(EnginePolicy.initial(choice))
    }

    /** Turbo : réserve étendue et plafond mémoire, tâches de fond en attente ; relance à la même position. */
    fun toggleTurbo(on: Boolean) {
        turbo = on
        store.turboDefault = on
        restartHere(engine?.kind)
    }

    private fun restartHere(kind: EngineKind?) {
        val r = request ?: return
        val pos = engine?.positionMs?.takeIf { !r.isLive && it > 0 } ?: r.startPositionMs
        persistSnapshot()
        play(r, resumeAtMs = pos, kind = kind)
    }

    /** Résumé de la session terminée (comparaison des moteurs dans Réglages). */
    private fun logSession() {
        val e = engine ?: return
        val r = request ?: return
        if (sessionOpenedAt == 0L) return
        store.record(EngineStore.SessionLog(
            at = sessionOpenedAt, title = r.title, engine = e.kind.label, turbo = turbo, startupMs = e.startupMs, stalls = e.stalls,
            dropped = e.droppedFrames, watchedSec = (System.currentTimeMillis() - sessionOpenedAt) / 1000, error = lastError, fallback = fellBack,
        ))
        sessionOpenedAt = 0L
    }

    /** Résout un identifiant de contenu en demande de lecture ; choisit la meilleure version si demandé. */
    private suspend fun resolve(id: String, fromStart: Boolean, preferBestVariant: Boolean): PlaybackRequest {
        val adapter = session.adapter
        if (id.startsWith("CATCHUP|")) {
            // CATCHUP|<itemId>|<débutUtc>|<durée min> : replay d'une émission passée, si l'archive est déclarée.
            val body = id.removePrefix("CATCHUP|")
            val dur = body.substringAfterLast('|')
            val rest = body.substringBeforeLast('|')
            val start = rest.substringAfterLast('|')
            val itemId = rest.substringBeforeLast('|')
            val item = c.db.catalog().observeItem(itemId).first() ?: error("Chaîne introuvable.")
            currentItem = item; versions = emptyList(); catchupAvailable = false
            return PlaybackRequest(
                adapter.streamUrl(StreamTarget.Catchup(item.providerId, start.toLong(), dur.toInt(), session.account.serverTimezone)) ?: error(DEMO),
                item.title, "REPLAY (expérimental)", false, null, null, "REPLAY", null,
            )
        }
        if (id.contains("|EPISODE|")) {
            val ep = c.db.library().episode(id) ?: error("Épisode introuvable dans le cache.")
            currentItem = c.db.catalog().observeItem(ep.seriesItemId).first()
            versions = emptyList()
            return episodeRequest(ep, fromStart)
        }
        var item = c.db.catalog().observeItem(id).first() ?: error("Contenu introuvable.")
        val siblings = item.variantKey?.let { c.db.catalog().variantSiblings(accountId, item.type, it) }.orEmpty().ifEmpty { listOf(item) }
        if (preferBestVariant && item.type == "LIVE") item = siblings.minBy { VariantRanking.cost(it.lang, it.quality, prefs) }
        currentItem = item
        versions = siblings.sortedBy { VariantRanking.cost(it.lang, it.quality, prefs) }.map { s ->
            VersionOption(s.id, variantBadge(s.lang, s.quality)?.let { "$it (annoncé)" } ?: s.rawTitle, s.id == item.id)
        }.takeIf { it.size > 1 }.orEmpty()
        isFavorite = item.type == "LIVE" && isFav(item)
        catchupAvailable = item.type == "LIVE" && (item.catchupDays ?: 0) > 0
        val meta = c.db.metadata().match(item.id)?.takeIf { it.status == "confirmed" }?.tmdbId
            ?.let { c.db.metadata().metadata(if (item.type == "SERIES") "tv" else "movie", it) }
        return when (item.type) {
            "LIVE" -> PlaybackRequest(adapter.streamUrl(StreamTarget.Live(item.providerId)) ?: error(DEMO), item.title, "DIRECT", true, null, null, "LIVE", null)
            "MOVIE" -> {
                val saved = c.library.progress(accountId, item.id)
                val start = if (fromStart) null else saved?.let { ProgressPolicy.resumePosition(it.positionMs, it.durationMs, it.completed) }
                PlaybackRequest(
                    adapter.streamUrl(StreamTarget.Movie(item.providerId, item.containerExt)) ?: error(DEMO),
                    meta?.title ?: item.title, listOfNotNull(item.year?.toString(), variantBadge(item.lang, item.quality)).joinToString(" · ").ifEmpty { null },
                    false, start, item.id, "MOVIE", null,
                )
            }
            else -> {
                if (c.db.library().episodesNow(item.id).isEmpty()) c.library.refreshEpisodes(accountId, item.id, adapter)
                val (ep, _) = c.library.episodeToResume(accountId, item.id) ?: error("Aucun épisode disponible pour cette série.")
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

    // ---------- direct : liste de zapping, favoris, guide ----------

    private suspend fun loadChannelList() {
        val raw = when {
            liveList == "fav" -> c.db.live().favorites(accountId).first()
            // Chaînes de sport françaises (rangée « Sport en direct ») : zapping entre elles.
            liveList == "sport" -> c.sport.frenchSportChannels(accountId)
            liveList?.startsWith("cat:") == true -> c.db.catalog().cardsInCategory(accountId, "LIVE", liveList.removePrefix("cat:")).first()
            else -> c.db.catalog().allChannels(accountId)
        }
        channels = raw.collapsed(prefs).map { it.first }
        updatePosition()
    }

    private fun updatePosition() {
        val key = currentItem?.variantKey
        val i = channels.indexOfFirst { it.id == currentItem?.id || (key != null && it.variantKey == key) }
        channelPosition = if (i >= 0) "${i + 1} / ${channels.size}" else null
    }

    /** ↑ / ↓ en direct : chaîne précédente / suivante de la liste d'origine (une seule session à la fois). */
    fun zap(delta: Int) {
        if (!isLive || channels.isEmpty()) return
        val key = currentItem?.variantKey
        val i = channels.indexOfFirst { it.id == currentItem?.id || (key != null && it.variantKey == key) }.coerceAtLeast(0)
        val next = channels[(i + delta + channels.size) % channels.size]
        persistSnapshot()
        viewModelScope.launch {
            error = null
            runCatching { resolve(next.id, false, preferBestVariant = true) }.onSuccess { play(it); updatePosition() }.onFailure { error = it.message }
        }
    }

    fun switchVersion(itemId: String) {
        val pos = engine?.positionMs?.takeIf { !isLive && it > 0 }
        persistSnapshot()
        viewModelScope.launch {
            runCatching { resolve(itemId, fromStart = false, preferBestVariant = false) }
                .onSuccess { play(it, resumeAtMs = pos ?: it.startPositionMs) }
                .onFailure { error = it.message }
        }
    }

    private suspend fun isFav(item: CatalogItemEntity): Boolean = c.db.live().favoriteIds(accountId).first().let { ids ->
        item.id in ids || (item.variantKey != null && c.db.catalog().variantSiblings(accountId, "LIVE", item.variantKey).any { it.id in ids })
    }

    fun toggleFavorite() {
        val item = currentItem ?: return
        viewModelScope.launch {
            val siblings = item.variantKey?.let { c.db.catalog().variantSiblings(accountId, "LIVE", it) }.orEmpty() + item
            if (isFavorite) siblings.forEach { c.db.live().removeFavorite(accountId, it.id) }
            else c.db.live().addFavorite(FavoriteChannelEntity(accountId, item.id, System.currentTimeMillis()))
            isFavorite = !isFavorite
        }
    }

    private fun refreshLiveInfo() {
        epgJob?.cancel()
        val item = currentItem ?: return
        if (item.type != "LIVE") { nowNext = emptyList(); return }
        epgJob = viewModelScope.launch {
            while (isActive) {
                val fromGuide = c.epg.nowNext(accountId, item)
                nowNext = fromGuide.ifEmpty {
                    // Repli : API courte du fournisseur (maintenant / ensuite), si disponible.
                    runCatching { session.adapter.shortEpg(item.providerId) }.getOrDefault(emptyList())
                        .filter { it.endUtc > System.currentTimeMillis() }.take(2)
                        .map { Programme(it.title, it.description, null, it.startUtc, it.endUtc) }
                }
                delay(60_000)
            }
        }
    }

    // ---------- fin, reprise, cycle de vie ----------

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
        val r = request ?: run { start(); return }
        error = null
        play(r, resumeAtMs = engine?.positionMs?.takeIf { !r.isLive && it > 0 } ?: r.startPositionMs, kind = engine?.kind)
    }

    /** Mise en veille / sortie de l'app : on enregistre puis on libère la session (aucune seconde session au réveil). */
    fun suspendSession() {
        persistSnapshot()
        val pos = engine?.positionMs
        val r = request
        logSession()
        c.playback.release()
        engine = null
        epgJob?.cancel()
        suspendedAt = if (r != null && !r.isLive) pos else null
    }

    /** Retour au premier plan : réouverture en pause à la même position. */
    fun resumeSession() {
        val r = request ?: return
        if (engine != null) return
        play(r, resumeAtMs = suspendedAt, kind = if (fellBack) EngineKind.VLC else null)
        engine?.holdPaused()
    }

    override fun onCleared() = close()

    /** Sauvegarde finale puis libération, appelée en quittant l'écran. */
    fun close() {
        persistSnapshot()
        logSession()
        if (c.playback.currentEngine === engine) c.playback.release()
        engine = null
        epgJob?.cancel()
    }

    /** Lit la position sur le thread principal, puis écrit hors de la portée du ViewModel (qui peut être détruit). */
    private fun persistSnapshot() {
        val r = request ?: return
        val p = engine ?: return
        val id = r.contentId ?: return
        val dur = p.durationMs.takeIf { it > 0 } ?: return
        val pos = p.positionMs
        c.appScope.launch { c.library.saveProgress(accountId, id, r.kind, r.seriesItemId, pos, dur) }
    }

    private companion object { const val DEMO = "Lecture indisponible en mode démo : aucun flux n'est associé aux contenus fictifs." }
}
