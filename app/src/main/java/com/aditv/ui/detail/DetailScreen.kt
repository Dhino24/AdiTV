package com.aditv.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.aditv.data.Session
import com.aditv.data.db.CatalogCard
import com.aditv.data.db.EpisodeEntity
import com.aditv.data.db.MetadataMatchEntity
import com.aditv.data.db.TmdbMetadataEntity
import com.aditv.data.library.ProgressPolicy
import com.aditv.data.tmdb.TmdbClient
import com.aditv.data.tmdb.TmdbError
import com.aditv.ui.LocalContainer
import com.aditv.ui.common.AditvButton
import com.aditv.ui.common.InitialFocus
import com.aditv.ui.common.PosterCard
import com.aditv.ui.common.ProgressCard
import com.aditv.ui.common.RemoteImage
import com.aditv.ui.common.Tag
import com.aditv.ui.common.displayImage
import com.aditv.ui.common.tmdbImage
import com.aditv.ui.formatEpochDate
import com.aditv.ui.home.typeLabel
import com.aditv.ui.theme.Aditv
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Fiche film / série / chaîne. Le fournisseur dit ce qui est lisible ; TMDB habille (titre FR, résumé,
 * distribution, note contextualisée) uniquement si la correspondance est confirmée.
 */
@Composable
fun DetailScreen(
    session: Session.Active,
    id: String,
    onPlay: (contentId: String, fromStart: Boolean) -> Unit,
    onOpen: (String) -> Unit,
    onCorrect: (String) -> Unit,
    onMapGuide: (String) -> Unit = {},
) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val accountId = session.account.id
    val item by remember(id) { c.db.catalog().observeItem(id) }.collectAsStateWithLifecycle(null)
    val match by remember(id) { c.db.metadata().observeMatch(id) }.collectAsStateWithLifecycle(null)
    val meta by remember(match?.tmdbId, match?.status) {
        val m = match
        if (m?.status == "confirmed" && m.tmdbId != null) c.db.metadata().observeMetadata(m.mediaType, m.tmdbId) else flowOf(null)
    }.collectAsStateWithLifecycle(null)
    val inList by remember(id) { c.db.library().inWatchlist(accountId, id) }.collectAsStateWithLifecycle(false)
    val progress by remember(id) { c.db.library().observeProgress(accountId, id) }.collectAsStateWithLifecycle(null)
    val episodes by remember(id) { c.db.library().episodes(id) }.collectAsStateWithLifecycle(emptyList())
    val seriesProgress by remember(id) { c.db.library().progressForSeries(accountId, id) }.collectAsStateWithLifecycle(emptyList())
    var tmdbProblem by remember { mutableStateOf<String?>(null) }
    var episodesError by remember { mutableStateOf<String?>(null) }
    var categoryName by remember { mutableStateOf<String?>(null) }
    var related by remember { mutableStateOf<List<CatalogCard>>(emptyList()) }
    var resumeEpisode by remember { mutableStateOf<Pair<EpisodeEntity, Long?>?>(null) }
    val primary = remember { FocusRequester() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val canTrailer = remember { com.aditv.ui.common.Trailer.canOpen(context) }
    val prefs by c.prefs.variantPrefs.collectAsStateWithLifecycle(com.aditv.data.variants.VariantPrefs())
    var siblings by remember { mutableStateOf<List<com.aditv.data.db.CatalogItemEntity>>(emptyList()) }
    var chosenVersion by remember { mutableStateOf<String?>(null) }
    val favIds by remember(accountId) { c.db.live().favoriteIds(accountId) }.collectAsStateWithLifecycle(emptyList())
    var nowNext by remember { mutableStateOf<List<com.aditv.data.epg.Programme>>(emptyList()) }
    val mapping by remember(id) { c.db.live().observeMapping(id) }.collectAsStateWithLifecycle(null)
    var primaryFocused by remember { mutableStateOf(false) }

    LaunchedEffect(id, item?.type) {
        val it = item ?: return@LaunchedEffect
        categoryName = it.categoryId?.let { cid -> c.db.catalog().categoryName(cid) }
        if (it.type == "SERIES") launch {
            episodesError = c.library.refreshEpisodes(accountId, id, session.adapter).exceptionOrNull()?.let { e -> e.message ?: "Épisodes indisponibles." }
        }
        if (it.type != "LIVE") {
            tmdbProblem = when (val e = c.metadata.ensure(id)) {
                null -> null
                is TmdbError.NoKey -> "Ajoutez votre clé TMDB dans Paramètres pour enrichir les fiches."
                else -> e.message
            }
        }
    }
    // Versions du même contenu (VF / VOSTFR / qualités) : la meilleure selon les préférences est choisie par défaut.
    LaunchedEffect(item?.variantKey, prefs) {
        val it = item ?: return@LaunchedEffect
        siblings = it.variantKey?.let { k -> c.db.catalog().variantSiblings(accountId, it.type, k) }.orEmpty()
            .sortedBy { s -> com.aditv.data.variants.VariantRanking.cost(s.lang, s.quality, prefs) }
        if (chosenVersion == null) chosenVersion = siblings.firstOrNull()?.id ?: id
    }
    LaunchedEffect(item?.id, mapping) { item?.takeIf { it.type == "LIVE" }?.let { nowNext = c.epg.nowNext(accountId, it) } }
    LaunchedEffect(meta?.relatedIds) {
        val ids = meta?.relatedIds?.split(',')?.mapNotNull { it.toIntOrNull() }.orEmpty()
        related = if (ids.isEmpty()) emptyList() else c.db.metadata().availableAmong(accountId, meta!!.mediaType, ids, id)
    }
    LaunchedEffect(episodes, seriesProgress) { if (item?.type == "SERIES") resumeEpisode = c.library.episodeToResume(accountId, id) }
    InitialFocus(primary, key = item != null, enabled = item != null, isFocused = { primaryFocused })

    val it = item ?: return
    val canPlay = !session.isDemo
    val title = meta?.title ?: it.title
    val playId = chosenVersion ?: id
    val resumeAt = progress?.let { p -> ProgressPolicy.resumePosition(p.positionMs, p.durationMs, p.completed) }

    Box(Modifier.fillMaxSize()) {
        tmdbImage(meta?.backdropPath, "w1280")?.let { url ->
            RemoteImage(url, title, Modifier.fillMaxHeight().fillMaxWidth(0.66f).align(Alignment.TopEnd), fallbackTitle = false)
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0.3f to Aditv.Bg, 0.55f to Aditv.Bg.copy(alpha = 0.75f), 1f to Aditv.Bg.copy(alpha = 0.2f))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Aditv.Bg.copy(alpha = 0f), 0.9f to Aditv.Bg)))
        }
        LazyColumn(contentPadding = PaddingValues(start = 40.dp, top = 36.dp, end = 40.dp, bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row {
                    if (it.type != "LIVE" && meta?.backdropPath == null) {
                        Box(Modifier.width(170.dp).height(255.dp).background(Aditv.Surface1, androidx.compose.foundation.shape.RoundedCornerShape(14.dp))) {
                            RemoteImage(tmdbImage(meta?.posterPath, "w342") ?: it.imageUrl, title, Modifier.fillMaxSize())
                        }
                        Spacer(Modifier.width(28.dp))
                    }
                    Column(Modifier.width(820.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(typeLabel(it.type).uppercase() + if (it.available) " · DANS VOTRE ABONNEMENT" else " · RETIRÉ PAR LE FOURNISSEUR",
                            style = MaterialTheme.typography.labelSmall, color = Aditv.Accent)
                        Text(title, style = MaterialTheme.typography.displaySmall, color = Aditv.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val sub = listOfNotNull(meta?.originalTitle?.takeIf { o -> o != title }, "fournisseur : « ${it.rawTitle} »")
                        Text(sub.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        MetaRow(it.year, meta, it.genre, if (it.type == "SERIES") meta?.seasonCount else null)
                        val overview = meta?.overview ?: it.plot
                        if (overview != null) Text(
                            overview + if (meta?.overviewLanguage == "en-US") "  (résumé disponible en anglais uniquement)" else "",
                            style = MaterialTheme.typography.bodyLarge, color = Aditv.Text, maxLines = 5, overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            val first = Modifier.focusRequester(primary).onFocusChanged { f -> primaryFocused = f.isFocused }
                            when {
                                !canPlay -> AditvButton("Lecture indisponible (démo)", {}, first, enabled = true)
                                it.type == "LIVE" -> AditvButton("Regarder", { onPlay(playId, false) }, first)
                                it.type == "MOVIE" && resumeAt != null && playId == id -> {
                                    AditvButton("Reprendre", { onPlay(id, false) }, first, secondaryText = "· " + fmt(resumeAt))
                                    AditvButton("Depuis le début", { onPlay(id, true) })
                                }
                                it.type == "MOVIE" -> AditvButton("Lire", { onPlay(playId, false) }, first)
                                else -> resumeEpisode?.let { (ep, at) ->
                                    AditvButton(if (at != null) "Reprendre" else "Lire", { onPlay(ep.id, at == null) }, first, secondaryText = "· S${ep.season} É${ep.number ?: "?"}")
                                } ?: AditvButton(if (episodesError != null) "Épisodes indisponibles" else "Chargement des épisodes…", {}, first)
                            }
                            if (canTrailer && meta?.trailerKey != null) AditvButton("Bande-annonce", { com.aditv.ui.common.Trailer.open(context, meta!!.trailerKey!!) })
                            if (it.type != "LIVE") {
                                AditvButton(if (inList) "✓ Dans ma liste" else "+ Ma liste", { scope.launch { c.library.toggleWatchlist(accountId, id, !inList) } })
                                AditvButton("Correspondance", { onCorrect(id) })
                            } else {
                                val fav = siblings.any { s -> s.id in favIds } || id in favIds
                                AditvButton(if (fav) "★ Favori" else "☆ Favori", {
                                    scope.launch {
                                        if (fav) (siblings.map { s -> s.id } + id).forEach { x -> c.db.live().removeFavorite(accountId, x) }
                                        else c.db.live().addFavorite(com.aditv.data.db.FavoriteChannelEntity(accountId, playId, System.currentTimeMillis()))
                                    }
                                })
                                AditvButton("Guide", { onMapGuide(id) })
                            }
                        }
                        if (siblings.size > 1) {
                            Text("Versions disponibles (annoncées par le fournisseur)", style = MaterialTheme.typography.labelMedium, color = Aditv.TextSecondary)
                            LazyRow(Modifier.focusRestorer(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                items(siblings, key = { s -> s.id }) { s ->
                                    val label = com.aditv.data.variants.variantBadge(s.lang, s.quality) ?: "Version"
                                    AditvButton(label, { chosenVersion = s.id }, secondaryText = if (s.id == playId) "●" else null)
                                }
                            }
                        }
                        MatchLine(match, tmdbProblem)
                    }
                }
            }
            if (it.type == "LIVE") item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Fact("Catégorie", categoryName ?: "—")
                    val fmtT = java.text.SimpleDateFormat("HH:mm", java.util.Locale.FRANCE)
                    if (nowNext.isEmpty()) Fact("Programme", "guide indisponible pour cette chaîne")
                    nowNext.forEach { p -> Fact(fmtT.format(java.util.Date(p.startUtc)) + "–" + fmtT.format(java.util.Date(p.endUtc)), p.title) }
                    Fact("ID guide", when {
                        mapping?.disabled == true -> "désactivé (choix manuel)"
                        mapping?.epgChannelId != null -> "${mapping!!.epgChannelId} (association manuelle)"
                        it.epgChannelId != null -> "${it.epgChannelId} (fournisseur)"
                        else -> "non fourni — « Guide » pour associer"
                    })
                    mapping?.offsetMinutes?.takeIf { o -> o != 0 }?.let { o -> Fact("Décalage", "${if (o > 0) "+" else ""}$o min (manuel)") }
                    Fact("Replay déclaré", it.catchupDays?.let { d -> "$d jours (expérimental, depuis le guide)" } ?: "non")
                }
            }
            if (it.type == "MOVIE") item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    meta?.crew?.let { cr -> Fact("Réalisation", cr) }
                    meta?.cast?.let { cast -> Fact("Avec", cast.lines().take(6).joinToString(", ") { l -> l.substringBefore('|') }) }
                    Fact("Catégorie", categoryName ?: "—")
                    it.addedAt?.let { a -> Fact("Ajouté (déclaré)", formatEpochDate(a)) }
                }
            }
            if (it.type == "SERIES") item {
                SeasonsAndEpisodes(episodes, seriesProgress.associateBy { p -> p.contentId }, episodesError, canPlay, meta, onPlay)
            }
            if (related.isNotEmpty()) item {
                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("Disponibles dans votre abonnement", style = MaterialTheme.typography.headlineSmall, color = Aditv.Text)
                        Spacer(Modifier.width(12.dp))
                        Text("titres similaires selon TMDB, présents dans votre catalogue", style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary)
                    }
                    Spacer(Modifier.height(12.dp))
                    LazyRow(Modifier.focusRestorer(), horizontalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(related, key = { r -> r.id }) { r -> PosterCard(r.title, r.displayImage(), r.year?.toString(), { onOpen(r.id) }) }
                    }
                }
            }
            if (meta != null) item {
                Text(TmdbClient.ATTRIBUTION, style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary.copy(alpha = 0.7f))
            }
        }
    }
}

@Composable
private fun MetaRow(year: Int?, meta: TmdbMetadataEntity?, providerGenre: String?, seasons: Int?) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        (meta?.releaseDate?.take(4) ?: year?.toString())?.let { y -> Text(y, style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary) }
        (meta?.genres ?: providerGenre)?.let { g -> Text(g, style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary, maxLines = 1) }
        meta?.runtimeMin?.takeIf { it > 0 }?.let { r -> Text(if (r >= 60) "${r / 60} h ${"%02d".format(r % 60)}" else "$r min", style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary) }
        seasons?.let { s -> Text("$s saison${if (s > 1) "s" else ""} (TMDB)", style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary) }
        // Note contextualisée : source et nombre de votes toujours affichés.
        if (meta?.voteAverage != null && (meta.voteCount ?: 0) > 0) {
            Text("★ ${"%.1f".format(meta.voteAverage).replace('.', ',')}", style = MaterialTheme.typography.bodyLarge, color = Aditv.Text)
            Text("TMDB · ${meta.voteCount} votes", style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary)
        }
    }
}

@Composable
private fun MatchLine(m: MetadataMatchEntity?, problem: String?) {
    val text = when {
        problem != null -> problem
        m == null -> "TMDB : recherche de correspondance…"
        m.status == "confirmed" && m.origin == "manual" -> "TMDB : correspondance choisie manuellement (verrouillée)"
        m.status == "confirmed" -> "TMDB : correspondance confirmée (${if (m.origin == "provider") "ID fourni et vérifié" else "automatique"} · confiance ${"%.2f".format(m.confidence).replace('.', ',')})"
        m.status == "ambiguous" -> "TMDB : plusieurs titres possibles — « Correspondance » pour choisir"
        m.locked -> "TMDB : aucune correspondance (choix manuel)"
        else -> "TMDB : aucune correspondance trouvée — « Correspondance » pour chercher"
    }
    val warn = problem != null || m?.status == "ambiguous"
    Text(text, style = MaterialTheme.typography.bodySmall, color = if (warn) Aditv.Warn else Aditv.TextSecondary)
}

@Composable
private fun SeasonsAndEpisodes(
    episodes: List<EpisodeEntity>,
    progress: Map<String, com.aditv.data.db.WatchProgressEntity>,
    error: String?,
    canPlay: Boolean,
    meta: TmdbMetadataEntity?,
    onPlay: (String, Boolean) -> Unit,
) {
    val seasons = episodes.map { it.season }.distinct().sorted()
    var season by remember(seasons) { mutableStateOf(seasons.firstOrNull()) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            episodes.isEmpty() && error != null -> Text("Épisodes indisponibles : $error", style = MaterialTheme.typography.bodyLarge, color = Aditv.Warn)
            episodes.isEmpty() -> Text("Chargement des épisodes du fournisseur…", style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary)
        }
        if (seasons.isNotEmpty()) {
            LazyRow(Modifier.focusRestorer(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(seasons, key = { s -> s }) { s ->
                    AditvButton(if (s == 0) "Spéciaux" else "Saison $s", { season = s }, secondaryText = if (s == season) "●" else null)
                }
            }
            val list = episodes.filter { e -> e.season == season }
            LazyRow(Modifier.focusRestorer(), horizontalArrangement = Arrangement.spacedBy(18.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(list, key = { e -> e.id }) { e ->
                    val p = progress[e.id]
                    val frac = p?.let { if (it.completed) 1f else ProgressPolicy.fraction(it.positionMs, it.durationMs) }
                    ProgressCard(
                        title = "É${e.number ?: "?"} · ${e.title}",
                        subtitle = listOfNotNull(e.durationSec?.let { d -> "${d / 60} min" }, if (p?.completed == true) "vu" else null).joinToString(" · ").ifEmpty { null },
                        imageUrl = e.imageUrl ?: tmdbImage(meta?.backdropPath, "w780"),
                        progress = frac,
                        onClick = { if (canPlay) onPlay(e.id, p?.completed == true) },
                    )
                }
            }
            if (!canPlay) Text("Lecture indisponible en mode démo.", style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary)
        }
    }
}

@Composable
private fun Fact(k: String, v: String) = Row {
    Text(k, style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary, modifier = Modifier.width(200.dp))
    Text(v, style = MaterialTheme.typography.bodyMedium, color = Aditv.Text)
}

private fun fmt(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
