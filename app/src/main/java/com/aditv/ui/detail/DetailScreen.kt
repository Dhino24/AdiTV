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
import com.aditv.R
import com.aditv.ui.design.Badge
import com.aditv.ui.design.ComposedArt
import com.aditv.ui.design.SideBackdrop
import com.aditv.ui.design.DisplayTitle
import com.aditv.ui.design.FilterChip
import com.aditv.ui.design.Kicker
import com.aditv.ui.design.MetaLine
import com.aditv.ui.design.PillButton
import com.aditv.ui.design.ResumeBar
import com.aditv.ui.design.RoundButton
import com.aditv.ui.design.RowTitle
import com.aditv.ui.design.SheetOption
import com.aditv.ui.design.SideSheet
import com.aditv.ui.design.VariantBadges
import com.aditv.ui.design.WideCard
import com.aditv.ui.design.CardProgress
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.tv.material3.Icon
import com.aditv.ui.theme.Outfit
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
    onTrailer: (String) -> Unit = {},
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
    val inList by remember(id) { c.db.library().inWatchlist(com.aditv.data.profile.Profiles.key(accountId), id) }.collectAsStateWithLifecycle(false)
    val progress by remember(id) { c.db.library().observeProgress(com.aditv.data.profile.Profiles.key(accountId), id) }.collectAsStateWithLifecycle(null)
    val episodes by remember(id) { c.db.library().episodes(id) }.collectAsStateWithLifecycle(emptyList())
    val seriesProgress by remember(id) { c.db.library().progressForSeries(com.aditv.data.profile.Profiles.key(accountId), id) }.collectAsStateWithLifecycle(emptyList())
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
    val favIds by remember(accountId) { c.db.live().favoriteIds(com.aditv.data.profile.Profiles.key(accountId)) }.collectAsStateWithLifecycle(emptyList())
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
    var versionsOpen by remember { mutableStateOf(false) }
    var sheetWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(versionsOpen) {
        if (versionsOpen) sheetWasOpen = true
        else if (sheetWasOpen) { sheetWasOpen = false; repeat(10) { androidx.compose.runtime.withFrameNanos { }; if (runCatching { primary.requestFocus() }.getOrDefault(false)) return@LaunchedEffect } }
    }
    val backdrop = tmdbImage(meta?.backdropPath, com.aditv.core.DeviceProfile.backdropSize)
    val poster = tmdbImage(meta?.posterPath, "w500") ?: it.imageUrl
    val chosen = siblings.firstOrNull { s -> s.id == playId }
    Box(Modifier.fillMaxSize()) {
        // Fond : visuel TMDB plein écran ; sinon l'affiche (ou le logo de la chaîne) à droite, fondue dans l'anthracite.
        when {
            com.aditv.ui.common.VideoHole.backgroundPlaying -> Unit
            backdrop != null -> RemoteImage(backdrop, title, Modifier.fillMaxSize(), fallbackTitle = false)
            it.type == "LIVE" -> Box(Modifier.fillMaxHeight().fillMaxWidth(0.45f).align(Alignment.TopEnd).padding(top = 70.dp, end = 60.dp, bottom = 170.dp), contentAlignment = Alignment.Center) {
                RemoteImage(it.imageUrl, title, Modifier.fillMaxSize(), ContentScale.Fit, fallbackTitle = false)
            }
            else -> SideBackdrop(0.62f) { ComposedArt(title, poster, Modifier.fillMaxSize(), showTitle = false) }
        }
        // Cube : bande-annonce muette en fond après 3 s sur la fiche.
        if (it.type != "LIVE") com.aditv.ui.design.TrailerBackground(meta?.trailerKey, it.id)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Aditv.Bg, 0.26f to Aditv.Bg.copy(alpha = 0.9f), 0.55f to Aditv.Bg.copy(alpha = 0.35f), 0.75f to Aditv.Bg.copy(alpha = 0.1f))))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.3f to Color.Transparent, 0.62f to Aditv.Bg.copy(alpha = 0.6f), 0.98f to Aditv.Bg)))

        LazyColumn(contentPadding = PaddingValues(top = 0.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            item {
                Column(Modifier.padding(start = 48.dp, top = 84.dp).width(460.dp)) {
                    Kicker(typeLabelSingular(it.type) + if (it.available) "" else " · retiré par le fournisseur")
                    Spacer(Modifier.height(8.dp))
                    DisplayTitle(title, tmdbImage(meta?.logoPath, "w500"), size = 44.sp)
                    Spacer(Modifier.height(10.dp))
                    DetailMeta(it.year, meta, it.genre, if (it.type == "SERIES") meta?.seasonCount else null) { VariantBadges(chosen?.lang ?: it.lang, chosen?.quality ?: it.quality) }
                    val overview = meta?.overview ?: it.plot
                    if (overview != null) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            overview + if (meta?.overviewLanguage == "en-US") "  (résumé disponible en anglais uniquement)" else "",
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = 12.sp, lineHeight = 18.sp), color = Aditv.Text.copy(alpha = 0.92f),
                            maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(410.dp),
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(54.dp)) {
                        val first = Modifier.focusRequester(primary).onFocusChanged { f -> primaryFocused = f.isFocused }
                        when {
                            !canPlay -> PillButton("Lecture indisponible (démo)", {}, first, icon = null)
                            it.type == "LIVE" -> PillButton("Regarder", { onPlay(playId, false) }, first)
                            it.type == "MOVIE" && resumeAt != null && playId == id -> {
                                PillButton("Reprendre", { onPlay(id, false) }, first, secondary = fmt(resumeAt))
                                PillButton("Depuis le début", { onPlay(id, true) }, icon = R.drawable.ic_restart, primary = false)
                            }
                            it.type == "MOVIE" -> PillButton("Lecture", { onPlay(playId, false) }, first)
                            else -> resumeEpisode?.let { (ep, at) ->
                                PillButton((if (at != null) "Reprendre " else "Lecture ") + "S${ep.season} É${ep.number ?: "?"}", { onPlay(ep.id, at == null) }, first)
                            } ?: PillButton(if (episodesError != null) "Épisodes indisponibles" else "Chargement…", {}, first, icon = null)
                        }
                        if (it.type != "LIVE") {
                            if (meta?.trailerKey != null) PillButton("Bande-annonce", { onTrailer(meta!!.trailerKey!!) }, icon = R.drawable.ic_trailer, primary = false)
                            RoundButton(if (inList) R.drawable.ic_check else R.drawable.ic_plus, if (inList) "Dans Ma liste" else "Ajouter à Ma liste",
                                { scope.launch { c.library.toggleWatchlist(accountId, id, !inList) } }, active = inList)
                            if (siblings.size > 1) RoundButton(R.drawable.ic_layers, "Versions", { versionsOpen = true })
                            RoundButton(R.drawable.ic_link, "Correspondance", { onCorrect(id) })
                        } else {
                            RoundButton(if (inFav) R.drawable.ic_star else R.drawable.ic_star_outline, if (inFav) "Chaîne favorite" else "Ajouter aux favoris", {
                                scope.launch {
                                    if (inFav) (siblings.map { s -> s.id } + id).forEach { x -> c.db.live().removeFavorite(com.aditv.data.profile.Profiles.key(accountId), x) }
                                    else c.db.live().addFavorite(com.aditv.data.db.FavoriteChannelEntity(com.aditv.data.profile.Profiles.key(accountId), playId, System.currentTimeMillis()))
                                }
                            }, active = inFav)
                            if (siblings.size > 1) RoundButton(R.drawable.ic_layers, "Versions", { versionsOpen = true })
                            RoundButton(R.drawable.ic_list, "Associer un guide", { onMapGuide(id) })
                        }
                    }
                    if (it.type == "MOVIE" && resumeAt != null && progress != null && playId == id) {
                        Spacer(Modifier.height(4.dp))
                        val p = progress!!
                        val left = ((p.durationMs - p.positionMs).coerceAtLeast(0) / 60_000)
                        ResumeBar(ProgressPolicy.fraction(p.positionMs, p.durationMs), if (left >= 60) "${left / 60} h ${"%02d".format(left % 60)} restantes" else "$left min restantes")
                    }
                    if (siblings.size > 1) {
                        Spacer(Modifier.height(14.dp))
                        VersionsLine(siblings.map { s -> (com.aditv.data.variants.variantBadge(s.lang, s.quality) ?: "Version") to (s.id == playId) })
                    }
                    if (it.type != "LIVE") { Spacer(Modifier.height(8.dp)); MatchLine(match, tmdbProblem) }
                    if (it.type == "LIVE" && nowNext.isNotEmpty()) { Spacer(Modifier.height(12.dp)); NowNextLines(nowNext) }
                }
            }
            if (it.type == "SERIES") item {
                SeasonsAndEpisodes(episodes, seriesProgress.associateBy { p -> p.contentId }, episodesError, canPlay, meta, onPlay)
            }
            val castLines = meta?.cast?.lines()?.filter { l -> l.isNotBlank() }.orEmpty()
            if (castLines.isNotEmpty()) item {
                Column {
                    RowTitle("Distribution", "selon TMDB", Modifier.padding(start = 48.dp, bottom = 10.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 48.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        items(castLines.take(10)) { l -> Person(l.substringBefore('|'), l.substringAfter('|', "").ifBlank { null }) }
                    }
                }
            }
            if (related.isNotEmpty()) item {
                Column {
                    RowTitle("Vous aimerez aussi", "disponibles dans votre abonnement · similaires selon TMDB", Modifier.padding(start = 48.dp, bottom = 3.dp))
                    LazyRow(Modifier.focusRestorer(), contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(related.distinctBy { it.id }, key = { r -> r.id }) { r ->
                            WideCard(r.title, listOfNotNull(r.genre?.substringBefore(',')?.trim(), r.year?.toString()).joinToString(" · ").ifEmpty { null },
                                tmdbImage(r.tmdbBackdrop, "w780"), tmdbImage(r.tmdbPoster, "w342") ?: r.imageUrl, { onOpen(r.id) }, logoUrl = tmdbImage(r.tmdbLogo, "w300"))
                        }
                    }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 48.dp)) {
                    RowTitle("À propos", modifier = Modifier.padding(bottom = 10.dp))
                    AboutPanel {
                        if (it.type == "LIVE") {
                            val fmtT = java.text.SimpleDateFormat("HH:mm", java.util.Locale.FRANCE)
                            Fact("Programme", if (nowNext.isEmpty()) "guide indisponible pour cette chaîne" else nowNext.joinToString(" · ") { p -> fmtT.format(java.util.Date(p.startUtc)) + " " + p.title })
                            Fact("ID guide", when {
                                mapping?.disabled == true -> "désactivé (choix manuel)"
                                mapping?.epgChannelId != null -> "${mapping!!.epgChannelId} (association manuelle)"
                                it.epgChannelId != null -> "${it.epgChannelId} (fournisseur)"
                                else -> "non fourni — « Associer un guide »"
                            })
                            mapping?.offsetMinutes?.takeIf { o -> o != 0 }?.let { o -> Fact("Décalage", "${if (o > 0) "+" else ""}$o min (manuel)") }
                            Fact("Replay déclaré", it.catchupDays?.let { d -> "$d jours (expérimental, depuis le guide)" } ?: "non")
                        } else {
                            meta?.crew?.let { cr -> Fact(if (it.type == "SERIES") "Création" else "Réalisation", cr) }
                            if (castLines.isNotEmpty()) Fact("Avec", castLines.take(4).joinToString(", ") { l -> l.substringBefore('|') })
                            Fact("Qualité annoncée", siblings.ifEmpty { listOf(it) }.mapNotNull { s -> com.aditv.data.variants.variantBadge(s.lang, s.quality) }.distinct().joinToString(" · ").ifEmpty { "non précisée" })
                        }
                        Fact("Catégorie", categoryName ?: "—")
                        it.addedAt?.let { a -> Fact("Ajouté (déclaré)", formatEpochDate(a)) }
                        Fact("Titre fournisseur", it.rawTitle)
                    }
                    Text(
                        "Langue et qualité « annoncées » par le fournisseur, non mesurées." + if (meta != null) "  " + TmdbClient.ATTRIBUTION else "",
                        style = MaterialTheme.typography.bodySmall, color = Aditv.TextTertiary, modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        if (versionsOpen) {
            androidx.activity.compose.BackHandler { versionsOpen = false }
            val firstV = remember { FocusRequester() }
            InitialFocus(firstV)
            Box(Modifier.fillMaxSize().background(Color(0x9E030508)))
            SideSheet("Versions", "Regroupées automatiquement (même titre, même année). Langue et qualité annoncées par le fournisseur ; la meilleure selon vos préférences est en tête.",
                Modifier.align(Alignment.CenterEnd)) {
                siblings.forEachIndexed { i, s ->
                    SheetOption(com.aditv.data.variants.variantBadge(s.lang, s.quality) ?: s.rawTitle, { chosenVersion = s.id; versionsOpen = false },
                        if (i == 0) Modifier.focusRequester(firstV) else Modifier, checked = s.id == playId, detail = if (i == 0) "préférée" else null)
                }
            }
        }
    }
}

private fun typeLabelSingular(type: String) = when (type) { "LIVE" -> "Chaîne"; "MOVIE" -> "Film"; else -> "Série" }

@Composable
private fun DetailMeta(year: Int?, meta: TmdbMetadataEntity?, providerGenre: String?, seasons: Int?, badges: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val parts = listOfNotNull(
        meta?.releaseDate?.take(4) ?: year?.toString(),
        (meta?.genres ?: providerGenre)?.split(", ")?.take(2)?.joinToString(", "),
        meta?.runtimeMin?.takeIf { it > 0 }?.let { r -> if (r >= 60) "${r / 60} h ${"%02d".format(r % 60)}" else "$r min" },
        seasons?.let { s -> "$s saison${if (s > 1) "s" else ""}" },
        // Note contextualisée : source et nombre de votes toujours affichés.
        meta?.voteAverage?.takeIf { (meta.voteCount ?: 0) > 0 }?.let { v -> "★ ${"%.1f".format(v).replace('.', ',')} TMDB (${meta.voteCount} votes)" },
    )
    MetaLine(parts, badges = badges)
}

@Composable
private fun VersionsLine(versions: List<Pair<String, Boolean>>) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Versions annoncées :  ", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 9.sp), color = Aditv.TextSecondary)
        versions.forEachIndexed { i, (label, chosen) ->
            if (i > 0) Text("  ·  ", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 9.sp), color = Aditv.TextTertiary)
            Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 9.sp, fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal), color = if (chosen) Aditv.Text else Aditv.TextSecondary)
        }
    }
}

@Composable
private fun NowNextLines(list: List<com.aditv.data.epg.Programme>) {
    val now = System.currentTimeMillis()
    val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.FRANCE)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        list.forEach { p ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (p.isLive(now)) "MAINTENANT" else fmt.format(java.util.Date(p.startUtc)), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp), color = if (p.isLive(now)) Aditv.Accent else Aditv.TextTertiary, modifier = Modifier.width(80.dp))
                Text(p.title, style = MaterialTheme.typography.bodyLarge, color = Aditv.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Personne de la distribution : initiales (TMDB ne fournit pas ici de portrait mis en cache). */
@Composable
private fun Person(name: String, role: String?) {
    Column(Modifier.width(75.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Aditv.Surface3, Aditv.Surface1))), contentAlignment = Alignment.Center) {
            Text(name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = Aditv.TextSecondary)
        }
        Spacer(Modifier.height(7.dp))
        Text(name, style = MaterialTheme.typography.titleMedium.copy(fontSize = 9.sp), color = Aditv.Text, maxLines = 2, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis)
        role?.let { Text(it, style = MaterialTheme.typography.bodySmall.copy(fontSize = 8.sp), color = Aditv.TextTertiary, maxLines = 1, textAlign = TextAlign.Center, overflow = TextOverflow.Ellipsis) }
    }
}

@Composable
private fun AboutPanel(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.035f), RoundedCornerShape(9.dp)).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
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
    Text(text, style = MaterialTheme.typography.bodySmall, color = if (warn) Aditv.Warn else Aditv.TextTertiary)
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
    Column {
        when {
            episodes.isEmpty() && error != null -> Text("Épisodes indisponibles : $error", style = MaterialTheme.typography.bodyLarge, color = Aditv.Warn, modifier = Modifier.padding(start = 48.dp))
            episodes.isEmpty() -> Row(Modifier.padding(start = 48.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { repeat(4) { com.aditv.ui.design.SkeletonCard(202.dp, 16f / 9f) } }
        }
        if (seasons.isNotEmpty()) {
            val list = episodes.filter { e -> e.season == season }
            LazyRow(Modifier.focusRestorer(), contentPadding = PaddingValues(horizontal = 48.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                items(seasons.distinct(), key = { s -> s }) { s -> FilterChip(if (s == 0) "Spéciaux" else "Saison $s", s == season, { season = s }) }
                item { Text("${list.size} épisode${if (list.size > 1) "s" else ""}", style = MaterialTheme.typography.bodyMedium, color = Aditv.TextTertiary, modifier = Modifier.padding(start = 9.dp)) }
            }
            LazyRow(Modifier.focusRestorer(), contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 12.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(list.distinctBy { it.id }, key = { e -> e.id }) { e ->
                    val p = progress[e.id]
                    val frac = p?.let { if (it.completed) null else ProgressPolicy.fraction(it.positionMs, it.durationMs) }
                    EpisodeCard(e, e.imageUrl ?: tmdbImage(meta?.backdropPath, "w780"), seen = p?.completed == true, progress = frac) { if (canPlay) onPlay(e.id, p?.completed == true) }
                }
            }
            if (!canPlay) Text("Lecture indisponible en mode démo.", style = MaterialTheme.typography.bodySmall, color = Aditv.TextTertiary, modifier = Modifier.padding(start = 48.dp))
        }
    }
}

@Composable
private fun EpisodeCard(e: EpisodeEntity, image: String?, seen: Boolean, progress: Float?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(Modifier.width(202.dp)) {
        com.aditv.ui.design.CardFrame(onClick, Modifier.onFocusChanged { focused = it.isFocused }, width = 202.dp, aspect = 16f / 9f) {
            if (image != null) RemoteImage(image, e.title, Modifier.fillMaxSize(), fallbackTitle = false) else ComposedArt(e.title, null, Modifier.fillMaxSize(), showTitle = false)
            if (seen) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(16.dp).background(Color(0xB80A0C10), CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_check), contentDescription = "Vu", tint = Aditv.Accent, modifier = Modifier.size(10.dp))
            }
            e.durationSec?.let { d -> Text("${d / 60} min", fontSize = 7.sp, fontWeight = FontWeight.SemiBold, color = Color.White, modifier = Modifier.align(Alignment.BottomEnd).padding(end = 6.dp, bottom = if (progress != null) 13.dp else 6.dp).background(Color(0xB30A0C10), RoundedCornerShape(3.dp)).padding(horizontal = 4.dp, vertical = 1.5.dp)) }
            if (progress != null) CardProgress(progress)
        }
        Column(Modifier.padding(start = 2.dp, top = if (focused) 11.dp else 7.dp)) {
            Row {
                Text("É${e.number ?: "?"}", style = MaterialTheme.typography.titleMedium.copy(fontSize = 10.sp), color = Aditv.Accent)
                Text(" · " + e.title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 10.sp), color = Aditv.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            e.plot?.let { Text(it, style = MaterialTheme.typography.bodySmall.copy(fontSize = 8.sp, lineHeight = 11.5.sp), color = if (focused) Aditv.TextSecondary else Aditv.TextTertiary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp)) }
        }
    }
}

@Composable
private fun Fact(k: String, v: String) = Row {
    Text(k.uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp), color = Aditv.TextTertiary, modifier = Modifier.width(150.dp).padding(top = 2.dp))
    Text(v, style = MaterialTheme.typography.bodyLarge, color = Aditv.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

private fun fmt(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}
