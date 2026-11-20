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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import com.aditv.ui.design.LandscapeCard
import com.aditv.ui.design.PillButton
import com.aditv.ui.design.RoundIconButton
import com.aditv.ui.design.RowTitle
import com.aditv.ui.design.TitleArt
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
                is TmdbError.NoKey -> "Ajoutez votre clé TMDB dans Réglages pour enrichir les fiches."
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

    val inFav = it.type == "LIVE" && (siblings.any { s -> s.id in favIds } || id in favIds)
    Box(Modifier.fillMaxSize()) {
        // Fond plein écran : visuel TMDB, sinon l'affiche du fournisseur à droite.
        val backdrop = tmdbImage(meta?.backdropPath, "w1280")
        if (backdrop != null) RemoteImage(backdrop, title, Modifier.fillMaxSize(), fallbackTitle = false)
        else RemoteImage(tmdbImage(meta?.posterPath, "w500") ?: it.imageUrl, title, Modifier.fillMaxHeight().fillMaxWidth(0.42f).align(Alignment.TopEnd),
            if (it.type == "LIVE") ContentScale.Fit else ContentScale.Crop, fallbackTitle = false)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Aditv.Bg.copy(alpha = 0.95f), 0.5f to Aditv.Bg.copy(alpha = 0.6f), 0.85f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.4f to Color.Transparent, 0.8f to Aditv.Bg.copy(alpha = 0.9f), 1f to Aditv.Bg)))

        LazyColumn(contentPadding = PaddingValues(start = 48.dp, top = 40.dp, end = 48.dp, bottom = 56.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            item {
                Column(Modifier.width(620.dp).padding(top = 120.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(typeLabel(it.type).uppercase() + if (it.available) " · DANS VOTRE ABONNEMENT" else " · RETIRÉ PAR LE FOURNISSEUR",
                        style = MaterialTheme.typography.labelSmall, color = Aditv.Accent)
                    TitleArt(title, tmdbImage(meta?.logoPath, "w500"))
                    MetaRow(it.year, meta, it.genre, if (it.type == "SERIES") meta?.seasonCount else null, com.aditv.data.variants.variantBadge(it.lang, it.quality))
                    val overview = meta?.overview ?: it.plot
                    if (overview != null) Text(
                        overview + if (meta?.overviewLanguage == "en-US") "  (résumé disponible en anglais uniquement)" else "",
                        style = MaterialTheme.typography.bodyMedium, color = Aditv.Text.copy(alpha = 0.85f), maxLines = 4, overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top, modifier = Modifier.height(84.dp)) {
                        val first = Modifier.focusRequester(primary).onFocusChanged { f -> primaryFocused = f.isFocused }
                        when {
                            !canPlay -> PillButton("Lecture indisponible (démo)", {}, first, icon = null)
                            it.type == "LIVE" -> PillButton("Regarder", { onPlay(playId, false) }, first)
                            it.type == "MOVIE" && resumeAt != null && playId == id -> {
                                PillButton("Reprendre", { onPlay(id, false) }, first, secondary = fmt(resumeAt))
                                PillButton("Depuis le début", { onPlay(id, true) }, icon = "↺", primary = false)
                            }
                            it.type == "MOVIE" -> PillButton("Lire", { onPlay(playId, false) }, first)
                            else -> resumeEpisode?.let { (ep, at) ->
                                PillButton(if (at != null) "Reprendre" else "Lire", { onPlay(ep.id, at == null) }, first, secondary = "S${ep.season} É${ep.number ?: "?"}")
                            } ?: PillButton(if (episodesError != null) "Épisodes indisponibles" else "Chargement…", {}, first, icon = null)
                        }
                        if (it.type != "LIVE") {
                            RoundIconButton(if (inList) "✓" else "+", if (inList) "Dans ma liste" else "Ma liste", { scope.launch { c.library.toggleWatchlist(accountId, id, !inList) } }, active = inList)
                            if (canTrailer && meta?.trailerKey != null) RoundIconButton("▶", "Bande-annonce", { com.aditv.ui.common.Trailer.open(context, meta!!.trailerKey!!) })
                            RoundIconButton("⋯", "Correspondance", { onCorrect(id) })
                        } else {
                            RoundIconButton(if (inFav) "★" else "☆", if (inFav) "Favori" else "Ajouter aux favoris", {
                                scope.launch {
                                    if (inFav) (siblings.map { s -> s.id } + id).forEach { x -> c.db.live().removeFavorite(accountId, x) }
                                    else c.db.live().addFavorite(com.aditv.data.db.FavoriteChannelEntity(accountId, playId, System.currentTimeMillis()))
                                }
                            }, active = inFav)
                            RoundIconButton("≡", "Guide", { onMapGuide(id) })
                        }
                    }
                    MatchLine(match, tmdbProblem)
                }
            }
            if (siblings.size > 1) item {
                Column {
                    RowTitle("Versions disponibles", "annoncées par le fournisseur", Modifier.padding(bottom = 10.dp))
                    LazyRow(Modifier.focusRestorer(), horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                        items(siblings, key = { s -> s.id }) { s ->
                            PillButton(com.aditv.data.variants.variantBadge(s.lang, s.quality) ?: "Version", { chosenVersion = s.id },
                                icon = if (s.id == playId) "●" else null, primary = false)
                        }
                    }
                }
            }
            if (it.type == "SERIES") item {
                SeasonsAndEpisodes(episodes, seriesProgress.associateBy { p -> p.contentId }, episodesError, canPlay, meta, onPlay)
            }
            if (related.isNotEmpty()) item {
                Column {
                    RowTitle("Disponibles dans votre abonnement", "titres similaires selon TMDB", Modifier.padding(bottom = 10.dp))
                    LazyRow(Modifier.focusRestorer(), horizontalArrangement = Arrangement.spacedBy(22.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
                        items(related, key = { r -> r.id }) { r ->
                            LandscapeCard(r.title, tmdbImage(r.tmdbBackdrop, "w780"), tmdbImage(r.tmdbLogo, "w300"), tmdbImage(r.tmdbPoster, "w342") ?: r.imageUrl,
                                r.year?.toString(), { onOpen(r.id) })
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    RowTitle("Informations", modifier = Modifier.padding(bottom = 4.dp))
                    if (it.type == "LIVE") {
                        val fmtT = java.text.SimpleDateFormat("HH:mm", java.util.Locale.FRANCE)
                        if (nowNext.isEmpty()) Fact("Programme", "guide indisponible pour cette chaîne")
                        nowNext.forEach { p -> Fact(fmtT.format(java.util.Date(p.startUtc)) + "–" + fmtT.format(java.util.Date(p.endUtc)), p.title) }
                        Fact("ID guide", when {
                            mapping?.disabled == true -> "désactivé (choix manuel)"
                            mapping?.epgChannelId != null -> "${mapping!!.epgChannelId} (association manuelle)"
                            it.epgChannelId != null -> "${it.epgChannelId} (fournisseur)"
                            else -> "non fourni — bouton ≡ Guide pour associer"
                        })
                        mapping?.offsetMinutes?.takeIf { o -> o != 0 }?.let { o -> Fact("Décalage", "${if (o > 0) "+" else ""}$o min (manuel)") }
                        Fact("Replay déclaré", it.catchupDays?.let { d -> "$d jours (expérimental, depuis le guide)" } ?: "non")
                    } else {
                        meta?.crew?.let { cr -> Fact(if (it.type == "SERIES") "Création" else "Réalisation", cr) }
                        meta?.cast?.let { cast -> Fact("Avec", cast.lines().take(6).joinToString(", ") { l -> l.substringBefore('|') }) }
                    }
                    Fact("Catégorie", categoryName ?: "—")
                    it.addedAt?.let { a -> Fact("Ajouté (déclaré)", formatEpochDate(a)) }
                    Fact("Titre fournisseur", it.rawTitle)
                    if (meta != null) Text(TmdbClient.ATTRIBUTION, style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary.copy(alpha = 0.7f), modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

@Composable
private fun MetaRow(year: Int?, meta: TmdbMetadataEntity?, providerGenre: String?, seasons: Int?, badge: String? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        (meta?.releaseDate?.take(4) ?: year?.toString())?.let { y -> Text(y, style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary) }
        (meta?.genres ?: providerGenre)?.let { g -> Text(g, style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary, maxLines = 1) }
        meta?.runtimeMin?.takeIf { it > 0 }?.let { r -> Text(if (r >= 60) "${r / 60} h ${"%02d".format(r % 60)}" else "$r min", style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary) }
        seasons?.let { s -> Text("$s saison${if (s > 1) "s" else ""} (TMDB)", style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary) }
        badge?.let { b -> Tag(b) }
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
                    PillButton(if (s == 0) "Spéciaux" else "Saison $s", { season = s }, icon = if (s == season) "●" else null, primary = false)
                }
            }
            val list = episodes.filter { e -> e.season == season }
            LazyRow(Modifier.focusRestorer(), horizontalArrangement = Arrangement.spacedBy(22.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
                items(list, key = { e -> e.id }) { e ->
                    val p = progress[e.id]
                    val frac = p?.let { if (it.completed) 1f else ProgressPolicy.fraction(it.positionMs, it.durationMs) }
                    LandscapeCard(
                        "É${e.number ?: "?"} · ${e.title}", e.imageUrl ?: tmdbImage(meta?.backdropPath, "w780"), null, null,
                        listOfNotNull(e.durationSec?.let { d -> "${d / 60} min" }, if (p?.completed == true) "vu" else null).joinToString(" · ").ifEmpty { null },
                        { if (canPlay) onPlay(e.id, p?.completed == true) }, progress = frac,
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
