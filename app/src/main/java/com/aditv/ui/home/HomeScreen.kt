package com.aditv.ui.home

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.aditv.R
import com.aditv.data.db.CatalogCard
import com.aditv.data.db.ResumeRow
import com.aditv.data.library.ProgressPolicy
import com.aditv.data.provider.MediaType
import com.aditv.data.sync.SyncProgress
import com.aditv.data.variants.collapsed
import com.aditv.data.variants.variantBadge
import com.aditv.di.AppContainer
import com.aditv.ui.LocalContainer
import com.aditv.ui.LocalScreenEntry
import com.aditv.ui.common.InitialFocus
import com.aditv.ui.common.RemoteImage
import com.aditv.ui.common.StatusDot
import com.aditv.ui.common.tmdbImage
import com.aditv.ui.design.ChannelTile
import com.aditv.ui.design.LandscapeCard
import com.aditv.ui.design.PageDots
import com.aditv.ui.design.PillButton
import com.aditv.ui.design.RoundIconButton
import com.aditv.ui.design.RowTitle
import com.aditv.ui.design.TitleArt
import com.aditv.ui.formatMillisDateTime
import com.aditv.ui.theme.Aditv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class HomeRow(val title: String, val subtitle: String?, val type: MediaType, val cards: List<Pair<CatalogCard, Int>>, val liveList: String? = null)

/** Élément du carrousel principal. */
data class HeroItem(
    val itemId: String,
    /** Contenu à lancer (épisode en cours pour une série). */
    val playId: String,
    val fromStart: Boolean,
    val title: String,
    val logo: String?,
    val backdrop: String?,
    val poster: String?,
    val meta: String,
    val overview: String?,
    val resumeLabel: String?,
    val kicker: String,
)

data class HomeState(
    val loaded: Boolean = false,
    val hero: List<HeroItem> = emptyList(),
    val resume: List<ResumeRow> = emptyList(),
    val rows: List<HomeRow> = emptyList(),
    val watchlist: Set<String> = emptySet(),
    val lastSyncAt: Long? = null,
    val syncErrors: List<String> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(private val c: AppContainer, private val accountId: String) : ViewModel() {
    private val dao = c.db.catalog()
    val progress: StateFlow<SyncProgress> = c.sync.progress

    private val rawRows = combine(
        dao.recentlyAdded(accountId, MediaType.MOVIE.name, 80),
        dao.recentlyAdded(accountId, MediaType.SERIES.name, 80),
        dao.firstItems(accountId, MediaType.LIVE.name, 80),
        dao.allCards(accountId, MediaType.MOVIE.name),
        c.db.library().watchlist(accountId),
    ) { recentMovies, recentSeries, channels, anyMovies, myList -> listOf(recentMovies, recentSeries, channels, anyMovies.take(200), myList) }

    private val catalogRows = combine(rawRows, c.db.live().favorites(accountId), c.prefs.variantPrefs) { lists, favorites, prefs ->
        val (recentMovies, recentSeries, channels, anyMovies, myList) = lists.map { it.collapsed(prefs).take(24) }
        val rows = buildList {
            if (favorites.isNotEmpty()) add(HomeRow("Chaînes favorites", "↑/↓ dans le lecteur pour zapper", MediaType.LIVE, favorites.collapsed(prefs), liveList = "fav"))
            if (myList.isNotEmpty()) add(HomeRow("Ma liste", null, MediaType.MOVIE, myList))
            // « Ajouts récents » = date d'ajout déclarée par le fournisseur, pas des sorties récentes.
            if (recentMovies.isNotEmpty()) add(HomeRow("Nouveaux films", "ajoutés par le fournisseur", MediaType.MOVIE, recentMovies))
            else if (anyMovies.isNotEmpty()) add(HomeRow("Films", null, MediaType.MOVIE, anyMovies))
            if (recentSeries.isNotEmpty()) add(HomeRow("Nouvelles séries", "ajoutées par le fournisseur", MediaType.SERIES, recentSeries))
            if (favorites.isEmpty() && channels.isNotEmpty()) add(HomeRow("Chaînes", null, MediaType.LIVE, channels, liveList = "all"))
        }
        // Carrousel : d'abord les titres avec un visuel paysage TMDB, puis les autres.
        val candidates = (recentMovies + recentSeries + anyMovies).map { it.first }.distinctBy { it.id }
        Triple(rows, candidates.sortedByDescending { it.tmdbBackdrop != null }.take(6), myList.map { it.first.id }.toSet())
    }

    val state: StateFlow<HomeState> = combine(
        catalogRows, c.db.library().resumeRows(accountId, 20), dao.observeSyncStates(accountId),
    ) { rowsAndHero, resume, syncStates -> Triple(rowsAndHero, resume, syncStates) }
        .mapLatest { (rowsAndHero, resume, syncStates) ->
            val (rows, heroCards, myList) = rowsAndHero
            val hero = buildList {
                resume.firstOrNull()?.let { r -> add(resumeHero(r)) }
                heroCards.forEach { add(cardHero(it)) }
            }.distinctBy { it.itemId }.take(6)
            HomeState(
                loaded = true, hero = hero, resume = resume, rows = rows, watchlist = myList,
                lastSyncAt = syncStates.mapNotNull { it.lastSuccessAt }.maxOrNull(),
                syncErrors = syncStates.mapNotNull { s -> s.lastError?.let { "${typeLabel(s.type)} : $it" } },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    private suspend fun meta(itemId: String, type: String) = c.db.metadata().match(itemId)?.takeIf { it.status == "confirmed" }?.tmdbId
        ?.let { c.db.metadata().metadata(if (type == "SERIES") "tv" else "movie", it) }

    private suspend fun cardHero(card: CatalogCard): HeroItem {
        val m = meta(card.id, card.type)
        val metaLine = listOfNotNull(
            if (card.type == "SERIES") "Série" else "Film",
            m?.genres?.split(", ")?.firstOrNull() ?: card.genre,
            (m?.releaseDate?.take(4) ?: card.year?.toString()),
            m?.runtimeMin?.takeIf { it > 0 }?.let { if (it >= 60) "${it / 60} h ${"%02d".format(it % 60)}" else "$it min" },
            variantBadge(card.lang, card.quality),
            m?.voteAverage?.takeIf { (m.voteCount ?: 0) > 0 }?.let { "★ " + "%.1f".format(it).replace('.', ',') + " TMDB" },
        ).joinToString(" · ")
        return HeroItem(
            itemId = card.id, playId = card.id, fromStart = false, title = m?.title ?: card.title,
            logo = tmdbImage(m?.logoPath, "w500"), backdrop = tmdbImage(m?.backdropPath, "w1280"), poster = tmdbImage(card.tmdbPoster, "w500") ?: card.imageUrl,
            meta = metaLine, overview = m?.overview, resumeLabel = null, kicker = "NOUVEAU DANS VOTRE ABONNEMENT",
        )
    }

    private suspend fun resumeHero(r: ResumeRow): HeroItem {
        val type = if (r.kind == "EPISODE") "SERIES" else "MOVIE"
        val m = meta(r.itemId, type)
        val left = (r.durationMs - r.positionMs).coerceAtLeast(0) / 60_000
        return HeroItem(
            itemId = r.itemId, playId = r.contentId, fromStart = false, title = m?.title ?: r.title,
            logo = tmdbImage(m?.logoPath, "w500"), backdrop = tmdbImage(r.tmdbBackdrop, "w1280"), poster = tmdbImage(r.tmdbPoster, "w500") ?: r.imageUrl,
            meta = listOfNotNull(r.episodeLabel, "reste $left min").joinToString(" · "), overview = m?.overview, resumeLabel = "Reprendre", kicker = "CONTINUER",
        )
    }

    fun toggleList(itemId: String, inList: Boolean) = viewModelScope.launch { c.library.toggleWatchlist(accountId, itemId, !inList) }
}

fun typeLabel(type: String) = when (type) { "LIVE" -> "Direct"; "MOVIE" -> "Films"; else -> "Séries" }

@Composable
fun HomeScreen(accountId: String, isDemo: Boolean, onOpen: (String) -> Unit, onPlay: (String, Boolean) -> Unit, onPlayLive: (String, String?) -> Unit) {
    val c = LocalContainer.current
    val vm: HomeViewModel = viewModel(key = "home-$accountId") { HomeViewModel(c, accountId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val atTop by remember { derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset < 40 } }
    var heroIndex by remember { mutableIntStateOf(0) }
    var heroFocused by remember { mutableStateOf(false) }
    val primary = remember { FocusRequester() }
    val entry = LocalScreenEntry.current
    val hero = state.hero.getOrNull(heroIndex.coerceAtMost((state.hero.size - 1).coerceAtLeast(0)))

    InitialFocus(primary, key = state.hero.isNotEmpty(), enabled = state.hero.isNotEmpty())
    // Défilement automatique du carrousel tant que l'on est en haut de l'accueil.
    LaunchedEffect(heroIndex, atTop, state.hero.size) {
        if (state.hero.size > 1 && atTop) { delay(9_000); heroIndex = (heroIndex + 1) % state.hero.size }
    }

    Box(Modifier.fillMaxSize()) {
        // Fond plein écran du carrousel, assombri dès que l'on descend dans les rangées.
        HeroBackground(hero, dim = !atTop)
        if (state.loaded && state.hero.isEmpty() && state.rows.isEmpty()) {
            EmptyCatalog(progress, state.syncErrors)
        } else {
            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 60.dp), verticalArrangement = Arrangement.spacedBy(26.dp)) {
                item(key = "hero") {
                    if (hero != null) HeroBlock(
                        hero, heroIndex, state.hero.size, inList = hero.itemId in state.watchlist,
                        primaryModifier = Modifier.focusRequester(primary).focusRequester(entry),
                        onPlay = { onPlay(hero.playId, hero.fromStart) },
                        onToggleList = { vm.toggleList(hero.itemId, hero.itemId in state.watchlist) },
                        onInfo = { onOpen(hero.itemId) },
                        onNext = { heroIndex = (heroIndex + 1) % state.hero.size },
                        modifier = Modifier.onFocusChanged { heroFocused = it.hasFocus },
                    ) else Spacer(Modifier.height(120.dp))
                }
                if (state.resume.isNotEmpty()) item(key = "resume") { ResumeRowView(state.resume, onPlay) }
                items(state.rows, key = { it.title }) { row -> ContentRow(accountId, row, onOpen, onPlayLive) }
            }
        }
        TopBar(isDemo, progress, state.lastSyncAt, state.syncErrors.isNotEmpty(), visible = atTop)
    }
}

@Composable
private fun HeroBackground(hero: HeroItem?, dim: Boolean) {
    Box(Modifier.fillMaxSize()) {
        when {
            hero?.backdrop != null -> RemoteImage(hero.backdrop, hero.title, Modifier.fillMaxSize(), fallbackTitle = false)
            hero?.poster != null -> RemoteImage(hero.poster, hero.title, Modifier.fillMaxHeight().fillMaxWidth(0.45f).align(Alignment.TopEnd), ContentScale.Crop, fallbackTitle = false)
            hero != null -> com.aditv.ui.common.GeneratedArt(hero.title, Modifier.fillMaxHeight().fillMaxWidth(0.6f).align(Alignment.TopEnd), showTitle = false)
        }
        // Lisibilité : voile à gauche et en bas ; plus sombre hors du haut de page.
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Aditv.Bg.copy(alpha = 0.92f), 0.45f to Aditv.Bg.copy(alpha = 0.55f), 0.8f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 0.75f to Aditv.Bg.copy(alpha = 0.85f), 1f to Aditv.Bg)))
        if (dim) Box(Modifier.fillMaxSize().background(Aditv.Bg.copy(alpha = 0.78f)))
    }
}

@Composable
private fun HeroBlock(
    hero: HeroItem, index: Int, count: Int, inList: Boolean, primaryModifier: Modifier,
    onPlay: () -> Unit, onToggleList: () -> Unit, onInfo: () -> Unit, onNext: () -> Unit, modifier: Modifier,
) {
    Box(modifier.fillMaxWidth().height(430.dp)) {
        Column(Modifier.align(Alignment.BottomStart).padding(start = 48.dp, bottom = 8.dp).width(520.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(hero.kicker, style = MaterialTheme.typography.labelSmall, color = Aditv.Accent)
            TitleArt(hero.title, hero.logo)
            Text(hero.meta, style = MaterialTheme.typography.bodyMedium, color = Aditv.Text.copy(alpha = 0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            hero.overview?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Aditv.Text.copy(alpha = 0.8f), maxLines = 3, overflow = TextOverflow.Ellipsis) }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top, modifier = Modifier.height(78.dp)) {
                PillButton(hero.resumeLabel ?: "Lire", onPlay, primaryModifier)
                RoundIconButton(if (inList) "✓" else "+", if (inList) "Dans ma liste" else "Ma liste", onToggleList, active = inList)
                RoundIconButton("i", "Fiche", onInfo)
                if (count > 1) RoundIconButton("›", "Suivant", onNext)
            }
            PageDots(count, index)
        }
    }
}

@Composable
private fun TopBar(isDemo: Boolean, progress: SyncProgress, lastSyncAt: Long?, hasError: Boolean, visible: Boolean) {
    if (!visible) return
    Row(Modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.logo_full), contentDescription = "ADITV", modifier = Modifier.height(26.dp))
        if (isDemo) { Spacer(Modifier.width(10.dp)); Text("DÉMO · CONTENU FICTIF", style = MaterialTheme.typography.labelSmall, color = Aditv.Accent) }
        Spacer(Modifier.width(16.dp))
        Text("‹ Menu", style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary)
        Spacer(Modifier.weight(1f))
        Row(Modifier.background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            when {
                progress is SyncProgress.Running -> { StatusDot(Aditv.Accent); Spacer(Modifier.width(8.dp)); Text("Mise à jour · ${typeLabel(progress.type.name)} ${progress.count}", style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary) }
                hasError -> { StatusDot(Aditv.Warn); Spacer(Modifier.width(8.dp)); Text("Synchronisation incomplète · voir Réglages", style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary) }
                lastSyncAt != null -> { StatusDot(Aditv.Ok); Spacer(Modifier.width(8.dp)); Text("Catalogue du ${formatMillisDateTime(lastSyncAt)}", style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary) }
            }
        }
    }
}

@Composable
private fun ContentRow(accountId: String, row: HomeRow, onOpen: (String) -> Unit, onPlayLive: (String, String?) -> Unit) {
    val nowPlaying = if (row.type == MediaType.LIVE) com.aditv.ui.common.rememberNowPlaying(accountId, row.cards.map { it.first }) else emptyMap()
    Column {
        RowTitle(row.title, row.subtitle, Modifier.padding(start = 48.dp, bottom = 12.dp))
        LazyRow(Modifier.focusRestorer(), contentPadding = PaddingValues(horizontal = 48.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            items(row.cards, key = { it.first.id }) { (card, versions) ->
                val badge = variantBadge(card.lang, card.quality)
                if (card.type == "LIVE") ChannelTile(card.title, card.imageUrl, nowPlaying[card.id] ?: listOfNotNull(badge, if (versions > 1) "$versions versions" else null).joinToString(" · ").ifEmpty { null }, { onPlayLive(card.id, row.liveList) })
                else LandscapeCard(
                    card.title, tmdbImage(card.tmdbBackdrop, "w780"), tmdbImage(card.tmdbLogo, "w300"),
                    tmdbImage(card.tmdbPoster, "w342") ?: card.imageUrl,
                    listOfNotNull(card.year?.toString(), badge).joinToString(" · ").ifEmpty { null }, { onOpen(card.id) },
                )
            }
        }
    }
}

@Composable
private fun ResumeRowView(rows: List<ResumeRow>, onPlay: (String, Boolean) -> Unit) {
    Column {
        RowTitle("Continuer à regarder", modifier = Modifier.padding(start = 48.dp, bottom = 12.dp))
        LazyRow(Modifier.focusRestorer(), contentPadding = PaddingValues(horizontal = 48.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            items(rows, key = { it.contentId }) { r ->
                LandscapeCard(
                    r.title, tmdbImage(r.tmdbBackdrop, "w780") ?: r.imageUrl?.takeIf { r.kind == "EPISODE" }, null,
                    tmdbImage(r.tmdbPoster, "w342") ?: r.imageUrl, r.episodeLabel, { onPlay(r.contentId, false) },
                    progress = ProgressPolicy.fraction(r.positionMs, r.durationMs),
                )
            }
        }
    }
}

@Composable
private fun EmptyCatalog(progress: SyncProgress, errors: List<String>) {
    Column(Modifier.fillMaxSize().padding(48.dp), verticalArrangement = Arrangement.Center) {
        when (progress) {
            is SyncProgress.Running -> {
                Text("Import du catalogue en cours", style = MaterialTheme.typography.headlineMedium, color = Aditv.Text)
                Spacer(Modifier.height(10.dp))
                Text("${typeLabel(progress.type.name)} · ${progress.stage} · ${progress.count} reçus", style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary)
                Text("Le catalogue s'affiche au fur et à mesure ; vous pouvez déjà naviguer.", style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary)
            }
            else -> {
                Text("Catalogue vide", style = MaterialTheme.typography.headlineMedium, color = Aditv.Text)
                Spacer(Modifier.height(10.dp))
                Text(if (errors.isEmpty()) "Aucun élément reçu pour le moment. Lancez une synchronisation depuis Réglages." else "La dernière synchronisation a échoué :",
                    style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary)
                errors.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = Aditv.Error) }
            }
        }
    }
}
