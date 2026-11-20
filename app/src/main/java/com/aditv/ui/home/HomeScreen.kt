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
import com.aditv.ui.design.ComposedArt
import com.aditv.ui.design.SideBackdrop
import com.aditv.ui.design.DisplayTitle
import com.aditv.ui.design.Kicker
import com.aditv.ui.design.LocalReducedMotion
import com.aditv.ui.design.MetaLine
import com.aditv.ui.design.PageDots
import com.aditv.ui.design.PillButton
import com.aditv.ui.design.PosterTile
import com.aditv.ui.design.ResumeBar
import com.aditv.ui.design.RoundButton
import com.aditv.ui.design.RowTitle
import com.aditv.ui.design.SkeletonCard
import com.aditv.ui.design.StateBox
import com.aditv.ui.design.VariantBadges
import com.aditv.ui.design.WideCard
import com.aditv.ui.shell.LocalNavChrome
import com.aditv.ui.sport.MatchCard
import com.aditv.ui.sport.MatchDialog
import com.aditv.ui.sport.RankedTile
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.sp
import com.aditv.ui.formatMillisDateTime
import com.aditv.ui.theme.Aditv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import com.aditv.core.throttleLatest
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
    val metaParts: List<String> = emptyList(),
    val lang: String? = null,
    val quality: Int? = null,
    val progress: Float? = null,
    val remaining: String? = null,
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
        // 200 premiers films seulement (repli quand le fournisseur ne date pas ses ajouts) : jamais tout le catalogue.
        dao.firstItems(accountId, MediaType.MOVIE.name, 200),
        c.db.library().watchlist(accountId),
    ) { recentMovies, recentSeries, channels, anyMovies, myList -> listOf(recentMovies, recentSeries, channels, anyMovies, myList) }
        .throttleLatest(3_000)

    private val catalogRows = combine(rawRows, c.db.live().favorites(accountId).throttleLatest(3_000), c.prefs.variantPrefs) { lists, favorites, prefs ->
        val (recentMovies, recentSeries, channels, anyMovies, myList) = lists.map { it.collapsed(prefs).take(24) }
        val rows = buildList {
            // « Ajoutés récemment » = date d'ajout déclarée par le fournisseur, pas des sorties récentes.
            if (recentMovies.isNotEmpty()) add(HomeRow("Votre soirée commence ici", "films ajoutés récemment par le fournisseur", MediaType.MOVIE, recentMovies))
            else if (anyMovies.isNotEmpty()) add(HomeRow("Films", null, MediaType.MOVIE, anyMovies))
            if (favorites.isNotEmpty()) add(HomeRow("En direct maintenant", "vos chaînes favorites · ↑/↓ dans le lecteur pour zapper", MediaType.LIVE, favorites.collapsed(prefs), liveList = "fav"))
            else if (channels.isNotEmpty()) add(HomeRow("En direct maintenant", null, MediaType.LIVE, channels, liveList = "all"))
            if (myList.isNotEmpty()) add(HomeRow("Ma liste", null, MediaType.MOVIE, myList))
            if (recentSeries.isNotEmpty()) add(HomeRow("Séries à suivre", "ajoutées récemment par le fournisseur", MediaType.SERIES, recentSeries))
        }
        // Carrousel : d'abord les titres avec un visuel paysage TMDB, puis les autres.
        val candidates = (recentMovies + recentSeries + anyMovies).map { it.first }.distinctBy { it.id }
        Triple(rows, candidates.sortedByDescending { it.tmdbBackdrop != null }.take(6), myList.map { it.first.id }.toSet())
    }

    val state: StateFlow<HomeState> = combine(
        catalogRows, c.db.library().resumeRows(accountId, 20), dao.observeSyncStates(accountId).throttleLatest(3_000),
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
            meta = metaLine, overview = m?.overview, resumeLabel = null, kicker = "Nouveau dans votre abonnement",
            metaParts = listOfNotNull(
                (m?.releaseDate?.take(4) ?: card.year?.toString()),
                (if (card.type == "SERIES") "Série · " else "") + (m?.genres?.split(", ")?.firstOrNull() ?: card.genre ?: if (card.type == "SERIES") "" else "Film").trimEnd(' ', '·'),
                m?.runtimeMin?.takeIf { it > 0 }?.let { if (it >= 60) "${it / 60} h ${"%02d".format(it % 60)}" else "$it min" },
            ).filter { it.isNotBlank() },
            lang = card.lang, quality = card.quality,
        )
    }

    private suspend fun resumeHero(r: ResumeRow): HeroItem {
        val type = if (r.kind == "EPISODE") "SERIES" else "MOVIE"
        val m = meta(r.itemId, type)
        val left = (r.durationMs - r.positionMs).coerceAtLeast(0) / 60_000
        return HeroItem(
            itemId = r.itemId, playId = r.contentId, fromStart = false, title = m?.title ?: r.title,
            logo = tmdbImage(m?.logoPath, "w500"), backdrop = tmdbImage(r.tmdbBackdrop, "w1280"), poster = tmdbImage(r.tmdbPoster, "w500") ?: r.imageUrl,
            meta = listOfNotNull(r.episodeLabel, "reste $left min").joinToString(" · "), overview = m?.overview, resumeLabel = "Reprendre", kicker = "Reprendre la lecture",
            metaParts = listOfNotNull(m?.releaseDate?.take(4), m?.genres?.split(", ")?.firstOrNull(), r.episodeLabel),
            progress = ProgressPolicy.fraction(r.positionMs, r.durationMs),
            remaining = listOfNotNull(r.episodeLabel, if (left >= 60) "${left / 60} h ${"%02d".format(left % 60)} restantes" else "$left min restantes").joinToString(" · "),
        )
    }

    // ---------- sport et Top 10 ----------
    val sportChannels: StateFlow<List<CatalogCard>> = dao.observeSyncStates(accountId).throttleLatest(3_000)
        .mapLatest { c.sport.frenchSportChannels(accountId) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val matches: StateFlow<List<com.aditv.data.sport.UpcomingMatch>> = c.sport.matches
    val reminders = c.sport.upcomingReminders(accountId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val leadMinutes = c.prefs.reminderLeadMinutes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 5)
    private fun top(type: String) = combine(c.db.sport().observeTrending(type), c.prefs.variantPrefs, dao.observeSyncStates(accountId)) { t, p, _ -> t to p }
        .mapLatest { (t, p) -> c.trending.top(accountId, type, t, p) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val topMovies = top("movie")
    val topSeries = top("tv")

    fun toggleReminder(m: com.aditv.data.sport.UpcomingMatch) = viewModelScope.launch {
        c.sport.toggleReminder(accountId, m.title, m.startUtc, m.endUtc, m.channelItemId, m.channelName)
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
    val chrome = LocalNavChrome.current
    LaunchedEffect(atTop) { chrome.scrolled = !atTop }
    var heroIndex by rememberSaveable { mutableIntStateOf(0) }
    var heroFocused by remember { mutableStateOf(false) }
    val reduced = LocalReducedMotion.current
    val sportChannels by vm.sportChannels.collectAsStateWithLifecycle()
    val allMatches by vm.matches.collectAsStateWithLifecycle()
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val leadMinutes by vm.leadMinutes.collectAsStateWithLifecycle()
    val topMovies by vm.topMovies.collectAsStateWithLifecycle()
    val topSeries by vm.topSeries.collectAsStateWithLifecycle()
    val remindedKeys = remember(reminders) { reminders.map { it.matchKey }.toSet() }
    val matches = remember(allMatches) { val now = System.currentTimeMillis(); allMatches.filter { it.endUtc > now }.take(20) }
    var openMatch by remember { mutableStateOf<com.aditv.data.sport.UpcomingMatch?>(null) }
    val primary = remember { FocusRequester() }
    val entry = LocalScreenEntry.current
    val hero = state.hero.getOrNull(heroIndex.coerceAtMost((state.hero.size - 1).coerceAtLeast(0)))

    InitialFocus(primary, key = state.hero.isNotEmpty(), enabled = state.hero.isNotEmpty())
    // Le carrousel se montre toujours en entier quand ses boutons ont le focus.
    LaunchedEffect(heroFocused) { if (heroFocused && listState.firstVisibleItemScrollOffset > 0) listState.animateScrollToItem(0) }
    // Carrousel : défilement automatique en haut de l'accueil, jamais pendant qu'on manipule ses boutons.
    LaunchedEffect(heroIndex, atTop, heroFocused, state.hero.size, reduced) {
        if (state.hero.size > 1 && atTop && !heroFocused && !reduced) { delay(9_000); heroIndex = (heroIndex + 1) % state.hero.size }
    }

    Box(Modifier.fillMaxSize()) {
        HeroBackground(hero, dim = !atTop)

        if (state.loaded && state.hero.isEmpty() && state.rows.isEmpty() && state.resume.isEmpty()) {
            EmptyCatalog(progress, state.syncErrors)
        } else if (!state.loaded) {
            HomeSkeleton()
        } else {
            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 30.dp)) {
                item(key = "hero") {
                    if (hero != null) HeroBlock(
                        hero, heroIndex, state.hero.size, inList = hero.itemId in state.watchlist,
                        primaryModifier = Modifier.focusRequester(primary).focusRequester(entry),
                        onPlay = { onPlay(hero.playId, hero.fromStart) },
                        onToggleList = { vm.toggleList(hero.itemId, hero.itemId in state.watchlist) },
                        onInfo = { onOpen(hero.itemId) },
                        onPage = { d -> if (state.hero.size > 1) heroIndex = (heroIndex + d + state.hero.size) % state.hero.size },
                        modifier = Modifier.onFocusChanged { heroFocused = it.hasFocus },
                    ) else Spacer(Modifier.height(80.dp))
                }
                // Demandé par Adama : les chaînes de sport françaises tout de suite à l'écran.
                if (sportChannels.isNotEmpty()) item(key = "sportfr") { SportChannelsRow(accountId, sportChannels) { onPlayLive(it, "sport") } }
                if (matches.isNotEmpty()) item(key = "matches") {
                    Column(Modifier.padding(top = 10.dp)) {
                        RowTitle("Matchs à venir", "vos équipes · d'après le guide TV", Modifier.padding(start = 48.dp, bottom = 3.dp))
                        LazyRow(Modifier.focusRestorer(), contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(matches.distinctBy { it.key }, key = { it.key }) { m ->
                                MatchCard(m, m.key in remindedKeys, { if (m.isLive(System.currentTimeMillis())) onPlayLive(m.channelItemId, "sport") else openMatch = m })
                            }
                        }
                    }
                }
                if (state.resume.isNotEmpty()) item(key = "resume") { ResumeRowView(state.resume, onPlay) }
                if (topMovies.isNotEmpty()) item(key = "top-movies") { TopRow("Top 10 des films du moment", topMovies, onOpen) }
                if (topSeries.isNotEmpty()) item(key = "top-series") { TopRow("Top 10 des séries du moment", topSeries, onOpen) }
                items(state.rows.distinctBy { it.title }, key = { it.title }) { row -> ContentRow(accountId, row, onOpen, onPlayLive) }
                if (state.syncErrors.isNotEmpty()) item(key = "sync") {
                    Row(Modifier.padding(start = 48.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        StatusDot(Aditv.Warn); Spacer(Modifier.width(6.dp))
                        Text("Synchronisation incomplète : voir Réglages → Catalogue local", style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary)
                    }
                }
            }
        }
        openMatch?.let { m ->
            MatchDialog(m, m.key in remindedKeys, leadMinutes,
                onToggleReminder = { vm.toggleReminder(m); openMatch = null },
                onWatch = { openMatch = null; onPlayLive(m.channelItemId, "sport") },
                onClose = { openMatch = null })
        }
    }
}

@Composable
private fun HeroBackground(hero: HeroItem?, dim: Boolean) {
    val dimAlpha by animateFloatAsState(if (dim) 0.55f else 0f, tween(400), label = "dim")
    Box(Modifier.fillMaxWidth().height(465.dp)) {
        when {
            hero?.backdrop != null -> RemoteImage(hero.backdrop, hero.title, Modifier.fillMaxSize(), fallbackTitle = false)
            hero?.poster != null -> SideBackdrop(0.5f) { RemoteImage(hero.poster, hero.title, Modifier.fillMaxSize(), ContentScale.Crop, fallbackTitle = false) }
            hero != null -> SideBackdrop(0.6f) { ComposedArt(hero.title, null, Modifier.fillMaxSize(), showTitle = false) }
        }
        // Lisibilité : voile à gauche (texte) et fondu vers l'anthracite en bas.
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Aditv.Bg, 0.22f to Aditv.Bg.copy(alpha = 0.86f), 0.48f to Aditv.Bg.copy(alpha = 0.35f), 0.7f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.48f to Color.Transparent, 0.78f to Aditv.Bg.copy(alpha = 0.75f), 1f to Aditv.Bg)))
        Box(Modifier.fillMaxSize().background(Aditv.Bg.copy(alpha = dimAlpha)))
    }
}

@Composable
private fun HeroBlock(
    hero: HeroItem, index: Int, count: Int, inList: Boolean, primaryModifier: Modifier,
    onPlay: () -> Unit, onToggleList: () -> Unit, onInfo: () -> Unit, onPage: (Int) -> Unit, modifier: Modifier,
) {
    var firstFocused by remember { mutableStateOf(false) }
    var lastFocused by remember { mutableStateOf(false) }
    Box(modifier.fillMaxWidth().height(370.dp)) {
        Column(Modifier.padding(start = 48.dp, top = 88.dp).width(440.dp)) {
            Kicker(hero.kicker)
            Spacer(Modifier.height(8.dp))
            DisplayTitle(hero.title, hero.logo)
            Spacer(Modifier.height(9.dp))
            MetaLine(hero.metaParts.ifEmpty { listOf(hero.meta) }) { VariantBadges(hero.lang, hero.quality) }
            hero.overview?.let {
                Spacer(Modifier.height(7.dp))
                Text(it, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 12.sp, lineHeight = 17.sp), color = Aditv.Text.copy(alpha = 0.92f), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(400.dp))
            }
            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically,
                // ← sur le premier bouton / → sur le dernier : page précédente / suivante du carrousel.
                modifier = Modifier.onPreviewKeyEvent { ev ->
                    if (ev.type != KeyEventType.KeyDown || count < 2) false
                    else if (ev.key == Key.DirectionLeft && firstFocused) { onPage(-1); true }
                    else if (ev.key == Key.DirectionRight && lastFocused) { onPage(1); true }
                    else false
                },
            ) {
                PillButton(hero.resumeLabel ?: "Lecture", onPlay, primaryModifier.onFocusChanged { firstFocused = it.isFocused })
                PillButton("Plus d'infos", onInfo, icon = R.drawable.ic_info, primary = false)
                RoundButton(if (inList) R.drawable.ic_check else R.drawable.ic_plus, if (inList) "Dans Ma liste" else "Ajouter à Ma liste", onToggleList,
                    Modifier.onFocusChanged { lastFocused = it.isFocused }, active = inList)
            }
            Spacer(Modifier.height(20.dp))
            if (hero.progress != null) ResumeBar(hero.progress, hero.remaining) else Spacer(Modifier.height(4.dp))
            Spacer(Modifier.height(12.dp))
            PageDots(count, index)
        }
    }
}

/** Carte ou affiche selon les visuels réellement disponibles dans la rangée (jamais d'image étirée). */
private fun preferWide(cards: List<CatalogCard>) = cards.count { it.tmdbBackdrop != null } * 2 >= cards.size

@Composable
private fun ContentRow(accountId: String, row: HomeRow, onOpen: (String) -> Unit, onPlayLive: (String, String?) -> Unit) {
    val nowPlaying = if (row.type == MediaType.LIVE) com.aditv.ui.common.rememberNowPlaying(accountId, row.cards.map { it.first }) else emptyMap()
    val wide = remember(row.cards) { preferWide(row.cards.map { it.first }) }
    Column(Modifier.padding(top = 10.dp)) {
        RowTitle(row.title, row.subtitle, Modifier.padding(start = 48.dp, bottom = 3.dp))
        LazyRow(Modifier.focusRestorer(), contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(row.cards.distinctBy { it.first.id }, key = { it.first.id }) { (card, versions) ->
                val badge = variantBadge(card.lang, card.quality)
                val extra = if (versions > 1) "$versions versions" else null
                when {
                    card.type == "LIVE" -> ChannelTile(card.title, card.imageUrl, nowPlaying[card.id] ?: listOfNotNull(badge, extra).joinToString(" · ").ifEmpty { null }, { onPlayLive(card.id, row.liveList) })
                    wide -> WideCard(
                        card.title, listOfNotNull(card.genre?.substringBefore(',')?.trim(), card.year?.toString(), badge).joinToString(" · ").ifEmpty { null },
                        tmdbImage(card.tmdbBackdrop, "w780"), tmdbImage(card.tmdbPoster, "w342") ?: card.imageUrl, { onOpen(card.id) },
                        logoUrl = tmdbImage(card.tmdbLogo, "w300"),
                    )
                    else -> PosterTile(card.title, listOfNotNull(card.year?.toString(), badge).joinToString(" · ").ifEmpty { null }, tmdbImage(card.tmdbPoster, "w342") ?: card.imageUrl, { onOpen(card.id) })
                }
            }
        }
    }
}

@Composable
private fun ResumeRowView(rows: List<ResumeRow>, onPlay: (String, Boolean) -> Unit) {
    Column(Modifier.padding(top = 4.dp)) {
        RowTitle("Reprendre la lecture", modifier = Modifier.padding(start = 48.dp, bottom = 3.dp))
        LazyRow(Modifier.focusRestorer(), contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(rows.distinctBy { it.contentId }, key = { it.contentId }) { r ->
                val left = (r.durationMs - r.positionMs).coerceAtLeast(0) / 60_000
                val leftLabel = if (left >= 60) "${left / 60} h ${"%02d".format(left % 60)} restantes" else "$left min restantes"
                WideCard(
                    r.title, listOfNotNull(r.episodeLabel, leftLabel).joinToString(" · "),
                    tmdbImage(r.tmdbBackdrop, "w780") ?: r.imageUrl?.takeIf { r.kind == "EPISODE" },
                    tmdbImage(r.tmdbPoster, "w342") ?: r.imageUrl, { onPlay(r.contentId, false) },
                    progress = ProgressPolicy.fraction(r.positionMs, r.durationMs),
                )
            }
        }
    }
}

@Composable
private fun HomeSkeleton() {
    Column(Modifier.fillMaxSize().padding(start = 48.dp, top = 300.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { repeat(4) { SkeletonCard(207.dp) } }
    }
}

@Composable
private fun EmptyCatalog(progress: SyncProgress, errors: List<String>) {
    Box(Modifier.fillMaxSize().padding(top = 60.dp), contentAlignment = Alignment.Center) {
        when (progress) {
            is SyncProgress.Running -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { repeat(4) { SkeletonCard(150.dp) } }
                Spacer(Modifier.height(22.dp))
                Text("Import du catalogue en cours", style = MaterialTheme.typography.headlineSmall, color = Aditv.Text)
                Spacer(Modifier.height(6.dp))
                Text("${typeLabel(progress.type.name)} · ${progress.stage} · ${progress.count} reçus. Le catalogue s'affiche au fur et à mesure.",
                    style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary)
            }
            else -> StateBox(
                if (errors.isEmpty()) R.drawable.ic_film else R.drawable.ic_cloud_off,
                if (errors.isEmpty()) "Catalogue vide" else "La synchronisation a échoué",
                if (errors.isEmpty()) "Aucun élément reçu pour le moment. Lancez une synchronisation depuis Réglages." else errors.joinToString("\n"),
                error = errors.isNotEmpty(),
            )
        }
    }
}

@Composable
private fun SportChannelsRow(accountId: String, channels: List<CatalogCard>, onPlay: (String) -> Unit) {
    val nowPlaying = com.aditv.ui.common.rememberNowPlaying(accountId, channels)
    Column(Modifier.padding(top = 6.dp)) {
        RowTitle("Sport en direct", "chaînes françaises · ↑/↓ dans le lecteur pour passer de l'une à l'autre", Modifier.padding(start = 48.dp, bottom = 3.dp))
        LazyRow(Modifier.focusRestorer(), contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(channels.distinctBy { it.id }, key = { it.id }) { ch -> ChannelTile(ch.title, ch.imageUrl, nowPlaying[ch.id] ?: "Programme non communiqué", { onPlay(ch.id) }) }
        }
    }
}

@Composable
private fun TopRow(title: String, entries: List<com.aditv.data.tmdb.TopEntry>, onOpen: (String) -> Unit) {
    Column(Modifier.padding(top = 10.dp)) {
        RowTitle(title, "tendances TMDB de la semaine, disponibles dans votre abonnement", Modifier.padding(start = 48.dp, bottom = 3.dp))
        LazyRow(Modifier.focusRestorer(), contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(entries.distinctBy { it.card.id }, key = { it.card.id }) { e ->
                RankedTile(e.rank, e.card.title, listOfNotNull(e.card.year?.toString(), variantBadge(e.card.lang, e.card.quality)).joinToString(" · ").ifEmpty { null },
                    tmdbImage(e.poster, "w342") ?: tmdbImage(e.card.tmdbPoster, "w342") ?: e.card.imageUrl, { onOpen(e.card.id) })
            }
        }
    }
}
