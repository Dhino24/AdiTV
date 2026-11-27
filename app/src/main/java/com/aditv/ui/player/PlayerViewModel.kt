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
    /** Cadence de la vidéo en cours (images/s), pour adapter la fréquence de l'écran. */
    var videoFps by mutableStateOf<Float?>(null); private set
    var turbo by mutableStateOf(false); private set
    var versions by mutableStateOf<List<VersionOption>>(emptyList()); private set
    var isFavorite by mutableStateOf(false); private set
    var nowNext by mutableStateOf<List<Programme>>(emptyList()); private set
    /** Bandeau de chaîne affiché au zapping (position dans la liste). */
    var channelPosition by mutableStateOf<String?>(null); private set
    var catchupAvailable by mutableStateOf(false); private set
    /** Décalage du son de ce contenu / cette chaîne (ms, positif = son retardé). */
    var audioDelayMs by mutableStateOf(0); private set
    /** Chaîne regardée juste avant (touche ⏪ en direct). */
    var previousChannelTitle by mutableStateOf<String?>(null); private set
    /** Générique de la série (début, fin en ms), marqué une fois et appliqué à tous ses épisodes. */
    var introWindow by mutableStateOf<Pair<Long, Long>?>(null); private set
    var introStartMark by mutableStateOf<Long?>(null); private set

    private var request: PlaybackRequest? = null
    private var currentItem: CatalogItemEntity? = null
        set(v) {
            field = v
            badges = listOfNotNull(com.aditv.data.variants.VariantParser.qualityLabel(v?.quality), com.aditv.data.variants.VariantParser.languageLabel(v?.lang))
        }
    /** Qualité et langue annoncées par le fournisseur (badges en haut à droite du lecteur). */
    var badges by mutableStateOf<List<String>>(emptyList()); private set
    private var channels: List<CatalogCard> = emptyList()
    /** Multiplex : chaînes proposées pour le 2e écran (liste de zapping, sans la chaîne en cours). */
    fun multiplexCandidates(): List<CatalogCard> = channels.filter { it.id != currentItem?.id }.take(80)
    val currentChannelId: String? get() = currentItem?.id
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
    /** Nouveaux essais automatiques quand le serveur limite les connexions (remis à zéro à chaque chaîne). */
    private var busyRetries = 0
    private val choiceKey get() = currentItem?.variantKey ?: request?.contentId ?: contentId

    private val events = object : EngineEvents {
        override fun onFrameRate(fps: Float) {
            videoFps = fps
            com.aditv.core.CrashReporter.event("Cadence vidéo : ${"%.3f".format(fps).trimEnd('0').trimEnd('.')} i/s")
        }
        override fun onError(message: String, retryable: Boolean, formatProblem: Boolean) {
            val current = engine?.kind ?: EngineKind.MEDIA3
            lastError = message
            com.aditv.core.CrashReporter.event("Erreur de lecture (${current.label}) : $message")
            // « Auto » : une seule bascule vers VLC, uniquement si Media3 refuse le format.
            if (EnginePolicy.shouldFallback(engineChoice, current, formatProblem, fellBack)) {
                fellBack = true
                engineNote = "Media3 ne lit pas ce format : bascule automatique vers VLC."
                val r = request ?: return
                val pos = engine?.positionMs?.takeIf { !r.isLive && it > 0 } ?: r.startPositionMs
                play(r, resumeAtMs = pos, kind = EngineKind.VLC)
                return
            }
            // Serveur qui limite les connexions (HTTP 458, 429, 509, 503) : l'ancienne connexion (aperçu, chaîne précédente)
            // met souvent quelques secondes à se libérer → jusqu'à 3 nouveaux essais automatiques (2 s, 4 s, 6 s).
            if (com.aditv.playback.PlaybackErrors.serverBusy(message) && busyRetries < 3) {
                busyRetries++
                val r = request ?: return
                engineNote = "Serveur occupé : nouvel essai ($busyRetries/3)…"
                viewModelScope.launch {
                    c.playback.release()
                    kotlinx.coroutines.delay(2_000L * busyRetries)
                    play(r, resumeAtMs = engine?.positionMs?.takeIf { !r.isLive && it > 0 } ?: r.startPositionMs, kind = engine?.kind)
                }
                return
            }
            // Direct : avant d'afficher l'erreur, on essaie une autre version de la même chaîne (secours automatique).
            if (isLive) {
                viewModelScope.launch {
                    if (!tryBackup("Flux coupé")) {
                        error = withConnectionHint(message)
                        canRetry = retryable || retries < 1 || com.aditv.playback.PlaybackErrors.serverBusy(message)
                    }
                }
                return
            }
            error = withConnectionHint(message)
            canRetry = retryable || retries < 1 || com.aditv.playback.PlaybackErrors.serverBusy(message)
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
        if (resumeAtMs == null || req.isLive) guardStart()
        error = null
        engineChoice = store.choiceFor(choiceKey) ?: store.defaultChoice
        if (kind == null) { fellBack = false; engineNote = null }
        val delay = store.audioDelayFor(choiceKey)
        // Son à avancer : seul VLC sait le faire.
        val k = kind ?: if (delay < 0) EngineKind.VLC else EnginePolicy.initial(engineChoice)
        com.aditv.playback.engine.AudioDelay.processor.delayMs = delay.coerceAtLeast(0)
        val e = c.playback.openEngine(effective, trackPrefs(currentItem?.lang), k, BufferPolicy(turbo), events)
        engine = e
        audioDelayMs = delay
        e.setAudioDelayMs(delay)
        if (req.isLive && req.kind == "LIVE") currentItem?.let { ChannelHistory.record(it.id, it.variantKey, it.title) }
        previousChannelTitle = ChannelHistory.previous?.title?.let { com.aditv.data.live.ChannelFamilies.cleanName(it) }
        introWindow = req.seriesItemId?.let { store.introFor(it) }
        introStartMark = null
        sessionOpenedAt = System.currentTimeMillis(); lastError = null
        saver?.cancel()
        saver = viewModelScope.launch { while (isActive) { delay(10_000); if (e.isPlaying) persistSnapshot() } }
        refreshLiveInfo()
        currentItem?.id?.let { tried += it }
        if (req.isLive) watch(e) else watchdog?.cancel()
        // Dernière chaîne regardée (démarrage direct sur la dernière chaîne, option des Réglages).
        if (req.isLive) currentItem?.id?.let { id -> viewModelScope.launch { c.prefs.setLastLive(com.aditv.data.profile.Profiles.key(accountId), id, liveList) } }
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
        // Familles du Direct (et Ligue des champions) : même ordre que l'écran En direct.
        if (liveList != null && (liveList.startsWith("fam:") || liveList == com.aditv.ui.live.LiveCatalog.UCL || liveList == com.aditv.ui.live.LiveCatalog.FAV)) {
            channels = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { com.aditv.ui.live.LiveCatalog.channels(c, accountId, liveList).map { it.first } }
            updatePosition(); return
        }
        val raw = when {
            liveList == "fav" -> c.db.live().favorites(com.aditv.data.profile.Profiles.key(accountId)).first()
            // Chaînes de sport françaises (rangée « Sport en direct ») : zapping entre elles.
            liveList == "sport" -> c.sport.frenchSportChannels(accountId)
            liveList?.startsWith("cat:") == true -> c.db.catalog().cardsInCategory(accountId, "LIVE", liveList.removePrefix("cat:")).first()
            else -> c.db.catalog().allChannels(accountId)
        }
        channels = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { raw.collapsed(prefs).map { it.first } }
        updatePosition()
    }

    /** Serveur saturé : rappelle le nombre de connexions simultanées autorisées par l'abonnement. */
    private fun withConnectionHint(message: String): String {
        val max = session.account.maxConnections ?: return message
        if (!com.aditv.playback.PlaybackErrors.serverBusy(message)) return message
        return "$message\nVotre abonnement autorise $max connexion${if (max > 1) "s" else ""} simultanée${if (max > 1) "s" else ""} : arrêtez la lecture sur l'autre appareil (ou le Multiplex)."
    }

    // ---------- décalage du son ----------

    fun setAudioDelay(ms: Int) {
        store.setAudioDelay(choiceKey, ms)
        audioDelayMs = ms
        val e = engine ?: return
        if (!e.setAudioDelayMs(ms)) {
            engineNote = "Son avancé : passage au moteur VLC (Media3 sait seulement retarder le son)."
            restartHere(EngineKind.VLC)
        }
    }

    // ---------- chaîne précédente ----------

    fun previousChannel() {
        val p = ChannelHistory.previous ?: return
        busyRetries = 0; tried.clear()
        persistSnapshot()
        viewModelScope.launch {
            error = null
            runCatching { resolve(p.id, false, preferBestVariant = false) }.onSuccess { play(it); updatePosition() }.onFailure { error = it.message }
        }
    }

    // ---------- générique des séries ----------

    fun markIntroStart(posMs: Long) { introStartMark = posMs }
    fun markIntroEnd(posMs: Long) {
        val series = request?.seriesItemId ?: return
        val start = introStartMark ?: introWindow?.first ?: 0L
        if (posMs <= start + 5_000) return
        store.setIntro(series, start, posMs)
        introWindow = start to posMs
        introStartMark = null
    }
    fun clearIntro() { request?.seriesItemId?.let { store.setIntro(it, null, null) }; introWindow = null; introStartMark = null }
    fun skipIntro() { introWindow?.let { (_, end) -> engine?.seekTo(end) } }
    val isEpisode: Boolean get() = request?.kind == "EPISODE"

    // ---------- secours automatique (direct) ----------

    /** Versions de la chaîne déjà essayées (remises à zéro au zapping ou à un choix manuel). */
    private val tried = mutableSetOf<String>()
    private var watchdog: Job? = null

    /**
     * Bascule sur une autre version de la même chaîne (HD, FHD, backup…) non encore essayée, la meilleure selon les
     * préférences. Annoncée à l'écran, jamais silencieuse. Faux s'il n'en reste aucune.
     */
    private suspend fun tryBackup(reason: String): Boolean {
        val item = currentItem ?: return false
        if (item.type != "LIVE" || request?.kind == "REPLAY") return false
        val key = item.variantKey ?: return false
        val next = c.db.catalog().variantSiblings(accountId, "LIVE", key).filter { it.id !in tried }
            .minByOrNull { VariantRanking.cost(it.lang, it.quality, prefs) } ?: return false
        tried += next.id
        val req = runCatching { resolve(next.id, false, preferBestVariant = false) }.getOrNull() ?: return false
        com.aditv.core.CrashReporter.event("Secours automatique : $reason → autre version")
        busyRetries = 0
        c.playback.release()
        play(req)
        engineNote = "$reason : bascule automatique sur ${variantBadge(next.lang, next.quality) ?: "une autre source"} de la chaîne."
        updatePosition()
        return true
    }

    /** Surveille le direct : pas d'image après 20 s, ou chargement continu de plus de 15 s → secours. */
    private fun watch(e: PlaybackEngine) {
        watchdog?.cancel()
        watchdog = viewModelScope.launch {
            val opened = System.currentTimeMillis()
            var stuckSince = 0L
            while (isActive) {
                delay(1_000)
                if (engine !== e) return@launch
                if (error != null || userPaused || stopJob?.isActive == true || busyRetries > 0) { stuckSince = 0; continue }
                val now = System.currentTimeMillis()
                val stuck = if (e.startupMs == null) now - opened > 20_000 else e.isBuffering
                if (!stuck) { stuckSince = 0; continue }
                if (stuckSince == 0L) stuckSince = now
                if (e.startupMs == null || now - stuckSince > 15_000) {
                    stuckSince = 0
                    if (tryBackup("Flux bloqué")) return@launch
                }
            }
        }
    }

    private fun updatePosition() {
        val key = currentItem?.variantKey
        val i = channels.indexOfFirst { it.id == currentItem?.id || (key != null && it.variantKey == key) }
        channelPosition = if (i >= 0) "${i + 1} / ${channels.size}" else null
    }

    /** ↑ / ↓ en direct : chaîne précédente / suivante de la liste d'origine (une seule session à la fois). */
    fun zap(delta: Int) {
        if (!isLive || channels.isEmpty()) return
        busyRetries = 0; tried.clear()
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
        tried.clear()
        val pos = engine?.positionMs?.takeIf { !isLive && it > 0 }
        persistSnapshot()
        viewModelScope.launch {
            runCatching { resolve(itemId, fromStart = false, preferBestVariant = false) }
                .onSuccess { play(it, resumeAtMs = pos ?: it.startPositionMs) }
                .onFailure { error = it.message }
        }
    }

    private suspend fun isFav(item: CatalogItemEntity): Boolean = c.db.live().favoriteIds(com.aditv.data.profile.Profiles.key(accountId)).first().let { ids ->
        item.id in ids || (item.variantKey != null && c.db.catalog().variantSiblings(accountId, "LIVE", item.variantKey).any { it.id in ids })
    }

    fun toggleFavorite() {
        val item = currentItem ?: return
        viewModelScope.launch {
            val siblings = item.variantKey?.let { c.db.catalog().variantSiblings(accountId, "LIVE", it) }.orEmpty() + item
            if (isFavorite) siblings.forEach { c.db.live().removeFavorite(com.aditv.data.profile.Profiles.key(accountId), it.id) }
            else c.db.live().addFavorite(FavoriteChannelEntity(com.aditv.data.profile.Profiles.key(accountId), item.id, System.currentTimeMillis()))
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
        retries++; busyRetries = 0; tried.clear()
        val r = request ?: run { start(); return }
        error = null
        play(r, resumeAtMs = engine?.positionMs?.takeIf { !r.isLive && it > 0 } ?: r.startPositionMs, kind = engine?.kind)
    }

    private var stopJob: kotlinx.coroutines.Job? = null
    /** Pause demandée à la télécommande : seule pause respectée pendant le démarrage. */
    var userPaused = false
    private var startGuard: kotlinx.coroutines.Job? = null

    /** 4 premières secondes : toute pause non demandée (système, focus audio, touche OK parasite) est annulée. */
    private fun guardStart() {
        userPaused = false
        startGuard?.cancel()
        startGuard = viewModelScope.launch {
            repeat(8) {
                kotlinx.coroutines.delay(500)
                val e = engine ?: return@repeat
                if (!userPaused && error == null && stopJob?.isActive != true && !e.isPlaying) e.play()
            }
        }
    }
    private var playingBeforeStop = false

    /**
     * L'activité s'arrête. Souvent bref : la Fire TV change la fréquence de l'écran au lancement d'une vidéo, ou affiche
     * un écran système → on met en pause et on attend 5 s avant de libérer la session (pas de reconnexion inutile).
     */
    fun onStop() {
        if (stopJob?.isActive == true) return
        playingBeforeStop = engine?.isPlaying == true || (engine != null && request?.isLive == true)
        engine?.pause()
        stopJob = viewModelScope.launch { kotlinx.coroutines.delay(5_000); suspendSession() }
    }

    /** Retour : arrêt bref → la lecture reprend d'elle-même ; long arrêt → réouverture (direct : en lecture, VOD : en pause). */
    fun onStart() {
        if (stopJob?.isActive == true) {
            stopJob?.cancel(); stopJob = null
            if (playingBeforeStop) engine?.play()
            return
        }
        resumeSession()
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
        // Direct : on reprend en lecture (une chaîne en pause n'a pas de sens) ; film/série : en pause à la même position.
        if (!r.isLive) engine?.holdPaused()
    }

    override fun onCleared() = close()

    /** Sauvegarde finale puis libération, appelée en quittant l'écran. */
    fun close() {
        persistSnapshot()
        logSession()
        if (c.playback.currentEngine === engine) c.playback.release()
        engine = null
        epgJob?.cancel(); watchdog?.cancel()
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

/** Historique des chaînes (pour la processus) : dernière et précédente, une version de chaîne comptant comme la même chaîne. */
internal object ChannelHistory {
    data class Entry(val id: String, val key: String?, val title: String)
    var last: Entry? = null; private set
    var previous: Entry? = null; private set
    fun record(id: String, key: String?, title: String) {
        val l = last
        val same = l != null && (l.id == id || (key != null && l.key == key))
        if (!same) previous = l
        last = Entry(id, key, title)
    }
    fun reset() { last = null; previous = null }
}
