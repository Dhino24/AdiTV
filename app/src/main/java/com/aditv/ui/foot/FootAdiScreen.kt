package com.aditv.ui.foot

import com.aditv.data.football.Competition
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.verticalScroll
import com.aditv.ui.design.liquidGlass
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.aditv.R
import com.aditv.data.Session
import com.aditv.data.football.Fixture
import com.aditv.data.football.FootballError
import com.aditv.data.football.FootballRepository
import com.aditv.data.football.Goal
import com.aditv.di.AppContainer
import com.aditv.ui.LocalContainer
import com.aditv.ui.common.RemoteImage
import com.aditv.ui.common.navSound
import com.aditv.ui.design.FilterChip
import com.aditv.ui.design.LiveTag
import com.aditv.ui.design.PillButton
import com.aditv.ui.design.StateBox
import com.aditv.ui.theme.Aditv
import com.aditv.ui.theme.Outfit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Jour choisi (décalage par rapport à aujourd'hui) ; null = « Mes équipes » (aujourd'hui et les 3 prochains jours). */
class FootViewModel(private val c: AppContainer, private val session: Session.Active) : ViewModel() {
    private val accountId = session.account.id
    var day by mutableStateOf<Int?>(0); private set
    var fixtures by mutableStateOf<List<Fixture>?>(null); private set
    var error by mutableStateOf<String?>(null); private set
    var broadcasts by mutableStateOf<Map<Long, List<Broadcast>>>(emptyMap()); private set
    var goals by mutableStateOf<Map<Long, List<Goal>>>(emptyMap()); private set
    var lineups by mutableStateOf<Map<Long, List<com.aditv.data.football.Lineup>>>(emptyMap()); private set
    val hasKey get() = session.isDemo || c.footballKey.get() != null
    private var job: kotlinx.coroutines.Job? = null
    /** Requêtes API restantes aujourd'hui (affichées en bas de l'écran). */
    var quota by mutableStateOf<Pair<Int, Int>?>(null); private set

    init {
        load()
    }

    private fun updateQuota() { val r = c.football.remaining; val l = c.football.dailyLimit; quota = if (r != null && l != null) r to l else null }

    /** Appelé toutes les 5 min tant que l'écran FootAdi est affiché (pas pendant qu'on regarde la chaîne). */
    fun refreshIfLive() { if (!session.isDemo && fixtures?.any { it.live } == true && !c.football.saving) load(quiet = true) }

    fun select(d: Int?) { if (d == day && !standingsMode) return; standingsMode = false; day = d; fixtures = null; load() }

    // Classements : une compétition à la fois (1 requête, gardée 6 h).
    var standingsMode by mutableStateOf(false); private set
    var league by mutableStateOf(FootballRepository.COMPETITIONS.keys.first { it == 61L }); private set
    var table by mutableStateOf<List<com.aditv.data.football.StandingRow>?>(null); private set
    var tableError by mutableStateOf<String?>(null); private set
    private var tableJob: kotlinx.coroutines.Job? = null

    private var tableSeason: Int = c.football.currentSeason()
    fun seasonLabel() = "$tableSeason-${(tableSeason + 1) % 100}"

    fun openStandings(leagueId: Long = league, season: Int? = null) {
        tableSeason = season ?: c.football.currentSeason()
        standingsMode = true
        if (leagueId == league && table != null) return
        league = leagueId; table = null; tableError = null
        tableJob?.cancel()
        tableJob = viewModelScope.launch {
            try {
                table = if (session.isDemo) c.football.demoStandings(leagueId)
                    else withContext(Dispatchers.Default) { c.football.standings(leagueId, season ?: c.football.currentSeason()) }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: FootballError) { tableError = e.message; table = emptyList() }
            catch (e: Exception) { tableError = "Classement indisponible (${e.javaClass.simpleName})."; table = emptyList() }
            updateQuota()
        }
    }

    private suspend fun forDay(offset: Int) = if (session.isDemo) c.football.demo(offset) else c.football.fixtures(offset)

    fun load(quiet: Boolean = false) {
        if (!hasKey) { fixtures = emptyList(); return }
        job?.cancel()
        job = viewModelScope.launch {
            if (!quiet) error = null
            try {
                val list = withContext(Dispatchers.Default) {
                    day?.let { forDay(it) } ?: (0..3).flatMap { forDay(it) }.filter(FootballRepository::isFollowed)
                }
                fixtures = list
                updateQuota()
                // Chaînes qui diffusent : guide de l'abonnement d'abord, puis diffuseur habituel (en arrière-plan).
                broadcasts = withContext(Dispatchers.Default) {
                    if (session.isDemo) {
                        val demo = com.aditv.ui.live.LiveCatalog.placed(c, accountId).take(2)
                            .map { Broadcast(it.first, com.aditv.data.live.ChannelFamilies.cleanName(it.first.title), true, true) }
                        list.associate { it.id to demo }
                    } else runCatching { Broadcasts.findAll(c, accountId, list) }.getOrDefault(emptyMap())
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: FootballError) { error = e.message; if (fixtures == null) fixtures = emptyList() }
            catch (e: Exception) { error = "Service football indisponible (${e.javaClass.simpleName})."; if (fixtures == null) fixtures = emptyList() }
        }
    }

    /** Buts + compositions (une requête groupée pour les matchs du jour ; compositions ~1 h avant le coup d'envoi). */
    fun loadGoals(f: Fixture) {
        val soon = f.kickoff - System.currentTimeMillis() in 0..70 * 60_000L
        if (!f.live && !f.finished && !soon) return
        if (session.isDemo) { if (!lineups.containsKey(f.id)) lineups = lineups + (f.id to c.football.demoLineups(f)); return }
        viewModelScope.launch {
            runCatching { c.football.details(f, fixtures.orEmpty()) }.getOrNull()?.let {
                goals = goals + (f.id to it.goals); lineups = lineups + (f.id to it.lineups)
            }
            updateQuota()
        }
    }

    fun remind(f: Fixture, b: Broadcast?) = viewModelScope.launch {
        val ch = b?.channel ?: return@launch
        c.sport.toggleReminder(accountId, "${f.home.name} - ${f.away.name}", f.kickoff, f.kickoff + 2 * 3_600_000L, ch.id, b.name)
    }
}

private val timeFmt = SimpleDateFormat("HH:mm", Locale.FRANCE)
private val dayFmt = SimpleDateFormat("EEE d", Locale.FRANCE)

@Composable
fun FootAdiScreen(session: Session.Active, onPlayLive: (String, String?) -> Unit, onSettings: () -> Unit) {
    val c = LocalContainer.current
    val vm: FootViewModel = viewModel(key = "foot-${session.account.id}") { FootViewModel(c, session) }
    var focused by remember { mutableStateOf<Fixture?>(null) }
    val todayChip = remember { FocusRequester() }
    val listFocus = remember { FocusRequester() }
    val selChip = remember { FocusRequester() }
    // ↓ depuis les jours : directement sur le premier match (sinon la recherche 2D peut viser le panneau de droite).
    val hasRows = vm.standingsMode || (!vm.fixtures.isNullOrEmpty() && vm.hasKey)
    val toList = Modifier.focusProperties { if (hasRows) down = listFocus }
    val entry = com.aditv.ui.LocalScreenEntry.current
    com.aditv.ui.common.InitialFocus(todayChip)
    val list = vm.fixtures
    val shown = list?.firstOrNull { it.id == focused?.id } ?: list?.firstOrNull()
    // Buteurs : seulement si l'on reste sur le match (1 requête couvre tous les matchs commencés du jour).
    LaunchedEffect(shown?.id) { shown?.let { delay(if (session.isDemo) 0 else 1_200); vm.loadGoals(it) } }
    LaunchedEffect(Unit) { while (true) { delay(5 * 60_000L); vm.refreshIfLive() } }

    var sheet by remember { mutableStateOf<Fixture?>(null) }
    val rowFocus = remember { mutableMapOf<Long, FocusRequester>() }
    // Compositions fermées : retour sur la ligne du match (le bouton du panneau a disparu du focus pendant la fenêtre).
    var back by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(back) {
        val id = back ?: return@LaunchedEffect
        repeat(20) { delay(16); runCatching { rowFocus[id]?.requestFocus() } }
        back = null
    }
    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 84.dp)) {
        // Jours, puis « Mes équipes ».
        Row(Modifier.focusRestorer(todayChip).padding(vertical = 4.dp, horizontal = 2.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(-2, -1, 0, 1, 2, 3).forEach { d ->
                val label = when (d) { -1 -> "Hier"; 0 -> "Aujourd'hui"; 1 -> "Demain"; else -> dayFmt.format(Date(System.currentTimeMillis() + d * 86_400_000L)).replaceFirstChar { it.uppercase() } }
                FilterChip(label, vm.day == d && !vm.standingsMode, { vm.select(d) }, (if (d == 0) Modifier.focusRequester(todayChip).focusRequester(entry) else Modifier).then(if (vm.day == d && !vm.standingsMode) Modifier.focusRequester(selChip) else Modifier).then(toList))
            }
            FilterChip("★ Mes équipes", vm.day == null && !vm.standingsMode, { vm.select(null) }, (if (vm.day == null && !vm.standingsMode) Modifier.focusRequester(selChip) else Modifier).then(toList))
            FilterChip("🏆 Classements", vm.standingsMode, { vm.openStandings() }, (if (vm.standingsMode) Modifier.focusRequester(selChip) else Modifier).then(toList))
        }
        Spacer(Modifier.height(12.dp))
        if (vm.standingsMode) StandingsView(vm, listFocus, selChip)
        else Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(560.dp).fillMaxHeight()) {
                when {
                    !vm.hasKey -> StateBox(R.drawable.ic_info, "Ajoutez votre clé API-Football",
                        "Créez un compte gratuit sur api-football.com, puis saisissez la clé dans Réglages → FootAdi (ou depuis votre téléphone par QR code). Les chaînes qui diffusent sont retrouvées dans votre guide TV.") {
                        PillButton("Ouvrir les réglages", onSettings, icon = R.drawable.ic_settings, primary = false)
                    }
                    list == null -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { repeat(5) { Box(Modifier.fillMaxWidth().height(52.dp).background(Aditv.Surface1, RoundedCornerShape(12.dp))) } }
                    list.isEmpty() -> StateBox(R.drawable.ic_info, vm.error?.let { "Matchs indisponibles" } ?: "Aucun match ce jour-là",
                        vm.error ?: "Aucun match des compétitions suivies (Ligue des champions, grands championnats, Ligue 2, CAN, Sénégal, France).")
                    else -> LazyColumn(Modifier.fillMaxSize().focusRestorer(), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 30.dp, start = 3.dp, end = 3.dp, top = 3.dp)) {
                        var lastComp: Long? = null
                        list.forEach { f ->
                            if (f.competition.id != lastComp) {
                                lastComp = f.competition.id
                                item(key = "c${f.competition.id}-${f.id}") { CompetitionHeader(f) }
                            }
                            item(key = f.id) {
                                val rf = remember { FocusRequester() }
                                androidx.compose.runtime.DisposableEffect(f.id) { rowFocus[f.id] = rf; onDispose { rowFocus.remove(f.id) } }
                                FixtureRow(f, vm.broadcasts[f.id].orEmpty().firstOrNull(), (if (f.id == list.first().id) Modifier.focusRequester(listFocus).focusProperties { up = selChip } else Modifier).focusRequester(rf), onFocus = { focused = f }, onClick = {
                                vm.broadcasts[f.id]?.firstOrNull()?.let { b -> onPlayLive(b.channel.id, "fam:SPORT_FR") }
                            }) }
                        }
                        vm.error?.let { e -> item(key = "err") { Text(e, style = MaterialTheme.typography.bodySmall, color = Aditv.Warn, modifier = Modifier.padding(6.dp)) } }
                        vm.quota?.let { (left, limit) -> item(key = "quota") {
                            Text("Requêtes API-Football restantes aujourd'hui : $left / $limit" + if (left < FootballRepository.SAVE_BELOW) " · mode économie (scores non rafraîchis)" else "",
                                style = MaterialTheme.typography.bodySmall, color = if (left < FootballRepository.SAVE_BELOW) Aditv.Warn else Aditv.TextTertiary, modifier = Modifier.padding(start = 6.dp, top = 10.dp))
                        } }
                    }
                }
            }
            Spacer(Modifier.width(24.dp))
            Box(Modifier.weight(1f).fillMaxHeight().padding(bottom = 18.dp)) {
                shown?.let { f -> MatchPanel(f, vm.broadcasts[f.id].orEmpty(), vm.goals[f.id].orEmpty(), vm.lineups[f.id].orEmpty(),
                    onWatch = { b -> onPlayLive(b.channel.id, "fam:SPORT_FR") }, onRemind = { vm.remind(f, vm.broadcasts[f.id]?.firstOrNull()) },
                    onLineups = { sheet = f },
                    onStandings = if (f.competition.id in FootballRepository.standingsLeagues) ({ vm.openStandings(f.competition.id, f.competition.season) }) else null) }
            }
        }
    }
    sheet?.let { f -> LineupSheet(f, vm.lineups[f.id].orEmpty(), onClose = { back = f.id; sheet = null }) }
    }
}

@Composable
private fun CompetitionHeader(f: Fixture) = Row(Modifier.padding(start = 6.dp, top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
    val logo = f.competition.logo ?: f.competition.flag
    if (logo != null) { RemoteImage(logo, f.competition.name, Modifier.size(16.dp), ContentScale.Fit, fallbackTitle = false); Spacer(Modifier.width(8.dp)) }
    val name = FootballRepository.COMPETITIONS[f.competition.id] ?: f.competition.name
    Text((if (f.competition.country.isNotBlank() && f.competition.country != "World") f.competition.country.let(::countryFr) + " · " else "") + name,
        style = MaterialTheme.typography.titleMedium.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), color = Aditv.TextSecondary)
}

private fun countryFr(c: String) = when (c) {
    "France" -> "France"; "England" -> "Angleterre"; "Spain" -> "Espagne"; "Italy" -> "Italie"; "Germany" -> "Allemagne"; "Senegal" -> "Sénégal"
    "Africa" -> "Afrique"; "Europe" -> "Europe"; else -> c
}

@Composable
private fun Crest(url: String?, name: String, size: Dp) = Box(Modifier.size(size).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
    if (url != null) RemoteImage(url, name, Modifier.fillMaxSize().padding(size / 8), ContentScale.Fit, fallbackTitle = false)
    else Text(name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1) }.uppercase(), fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = (size.value / 3).sp, color = Aditv.Text)
}

@Composable
private fun FixtureRow(f: Fixture, b: Broadcast?, extra: Modifier, onFocus: () -> Unit, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        modifier = extra.fillMaxWidth().height(52.dp).liquidGlass(shape, strength = 0.5f).onFocusChanged { if (it.isFocused) onFocus() }.navSound(),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, contentColor = Aditv.Text, focusedContainerColor = Color.White.copy(alpha = 0.16f), focusedContentColor = Aditv.Text),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(1.5.dp, Aditv.Accent), shape = shape)),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                Text(f.home.name, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.End, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(8.dp)); Crest(f.home.logo, f.home.name, 24.dp)
            }
            Box(Modifier.width(70.dp), contentAlignment = Alignment.Center) { ScoreOrTime(f, 13.sp) }
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Crest(f.away.logo, f.away.name, 24.dp); Spacer(Modifier.width(8.dp))
                Text(f.away.name, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 12.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(8.dp))
            // Nom de chaîne en entier : colonne plus large, 2 lignes si besoin.
            Text(b?.name ?: "—", style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.SemiBold), maxLines = 2, overflow = TextOverflow.Ellipsis,
                color = if (b != null) Aditv.Text else Aditv.TextTertiary, textAlign = TextAlign.End,
                modifier = Modifier.width(150.dp).padding(start = 4.dp))
        }
    }
}

@Composable
private fun ScoreOrTime(f: Fixture, size: androidx.compose.ui.unit.TextUnit) {
    when {
        f.live -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("● ${f.elapsed ?: ""}'", fontSize = (size.value * 0.62f).sp, fontWeight = FontWeight.Bold, color = Aditv.Live)
            Text("${f.homeGoals ?: 0} – ${f.awayGoals ?: 0}", fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = size, color = Aditv.Text)
        }
        f.finished -> Text("${f.homeGoals ?: 0} – ${f.awayGoals ?: 0}", fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = size, color = Aditv.TextSecondary)
        f.status in setOf("PST", "CANC", "ABD", "SUSP") -> Text("Reporté", fontSize = (size.value * 0.7f).sp, color = Aditv.Warn)
        else -> Text(timeFmt.format(Date(f.kickoff)), fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = size, color = Aditv.Text)
    }
}

@Composable
private fun MatchPanel(
    f: Fixture, broadcasts: List<Broadcast>, goals: List<Goal>, lineups: List<com.aditv.data.football.Lineup>,
    onWatch: (Broadcast) -> Unit, onRemind: () -> Unit, onLineups: () -> Unit, onStandings: (() -> Unit)?,
) {
    Column(
        Modifier.fillMaxSize().liquidGlass(RoundedCornerShape(22.dp), strength = 1.2f)
            .verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(22.dp),
    ) {
        Text(((FootballRepository.COMPETITIONS[f.competition.id] ?: f.competition.name) + (f.round?.let { " · $it" } ?: "")).uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.5.sp, fontSize = 9.sp), color = Aditv.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Crest(f.home.logo, f.home.name, 54.dp); Spacer(Modifier.height(6.dp)); Text(f.home.name, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium), color = Aditv.Text, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) }
            Column(Modifier.width(100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                ScoreOrTime(f, 26.sp)
                if (!f.live && !f.finished) {
                    val left = (f.kickoff - System.currentTimeMillis()) / 60_000
                    if (left in 1..(48 * 60)) Text(if (left >= 60) "dans ${left / 60} h ${"%02d".format(left % 60)}" else "dans $left min", style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary)
                }
            }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) { Crest(f.away.logo, f.away.name, 54.dp); Spacer(Modifier.height(6.dp)); Text(f.away.name, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium), color = Aditv.Text, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center) }
        }
        Spacer(Modifier.height(16.dp))
        Text("OÙ REGARDER DANS VOTRE ABONNEMENT", style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.5.sp, fontSize = 8.5.sp), color = Aditv.TextTertiary)
        Spacer(Modifier.height(8.dp))
        if (broadcasts.isEmpty()) Text("Aucune chaîne trouvée dans votre guide pour ce match.", style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary)
        // Une chaîne par ligne, pleine largeur (la première en blanc).
        else Column(Modifier.padding(horizontal = 2.dp, vertical = 2.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            broadcasts.take(5).forEach { b ->
                PillButton(b.name + if (b.fromGuide) "" else " · habituel", { onWatch(b) }, Modifier.fillMaxWidth(), icon = R.drawable.ic_play, primary = b == broadcasts.first(), small = true)
            }
        }
        if (!f.live && !f.finished && broadcasts.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            PillButton("Me rappeler le match", onRemind, icon = R.drawable.ic_bell, primary = false, small = true)
        }
        if (goals.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("BUTS", style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.5.sp, fontSize = 8.5.sp), color = Aditv.TextTertiary)
            Spacer(Modifier.height(4.dp))
            goals.forEach { g ->
                Text("${g.minute ?: "?"}'  ${g.player}" + (if (g.detail?.contains("Penalty", true) == true) " (pén.)" else if (g.detail?.contains("Own", true) == true) " (csc)" else "") +
                    "  · " + (if (g.teamId == f.home.id) f.home.name else f.away.name), style = MaterialTheme.typography.bodyMedium, color = Aditv.Text, maxLines = 1)
            }
        }
        // Compositions : publiées environ 1 h avant le coup d'envoi.
        Spacer(Modifier.height(14.dp))
        Text("COMPOSITIONS", style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.5.sp, fontSize = 8.5.sp), color = Aditv.TextTertiary)
        Spacer(Modifier.height(5.dp))
        if (lineups.size >= 2) {
            Text(lineups.joinToString("   ·   ") { "${it.team} ${it.formation ?: ""}".trim() }, style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary, maxLines = 2)
            Spacer(Modifier.height(7.dp))
            PillButton("Voir les compositions", onLineups, icon = R.drawable.ic_list, primary = false, small = true)
        } else Text(
            if (f.live || f.finished) "Compositions non communiquées pour ce match."
            else "Annoncées vers ${timeFmt.format(Date(f.kickoff - 3_600_000L))} (environ 1 h avant le match).",
            style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary,
        )
        onStandings?.let { Spacer(Modifier.height(10.dp)); PillButton("Classement", it, icon = R.drawable.ic_chart, primary = false, small = true) }
        if (f.live) { Spacer(Modifier.height(10.dp)); LiveTag() }
    }
}

/** Classements : compétitions à gauche, tableau à droite (rang, équipe, J G N P, différence, points, forme). */
@Composable
private fun StandingsView(vm: FootViewModel, firstFocus: FocusRequester, upTo: FocusRequester) {
    Row(Modifier.fillMaxSize().padding(bottom = 18.dp)) {
        Column(Modifier.width(230.dp).fillMaxHeight().verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(2.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            FootballRepository.standingsLeagues.forEachIndexed { i, id ->
                val name = FootballRepository.COMPETITIONS[id] ?: id.toString()
                PillButton(name, { vm.openStandings(id) }, (if (i == 0) Modifier.focusRequester(firstFocus).focusProperties { up = upTo } else Modifier).fillMaxWidth(),
                    icon = null, primary = vm.league == id, small = true)
            }
        }
        Spacer(Modifier.width(22.dp))
        Column(Modifier.weight(1f).fillMaxHeight().liquidGlass(RoundedCornerShape(22.dp), strength = 1.2f).padding(horizontal = 18.dp, vertical = 14.dp)) {
            Text((FootballRepository.COMPETITIONS[vm.league] ?: "") + " · saison ${FootballRepository.zone.let { _ -> vm.seasonLabel() }}",
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 15.sp), color = Aditv.Text)
            Spacer(Modifier.height(8.dp))
            val t = vm.table
            when {
                t == null -> Text("Chargement du classement…", style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary)
                t.isEmpty() -> Text(vm.tableError ?: "Classement non disponible pour cette compétition.", style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary)
                else -> {
                    StandingHeader()
                    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        var lastGroup: String? = null
                        t.forEach { r ->
                            if (t.map { it.group }.distinct().size > 1 && r.group != lastGroup) {
                                lastGroup = r.group
                                item(key = "g${r.group}") { Text(r.group.orEmpty(), style = MaterialTheme.typography.labelLarge.copy(fontSize = 9.sp), color = Aditv.TextSecondary, modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)) }
                            }
                            item(key = "${r.group}-${r.team.id}") { StandingLine(r) }
                        }
                    }
                }
            }
        }
    }
}

private val numW = 30.dp

@Composable
private fun StandingHeader() = Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
    val st = MaterialTheme.typography.labelLarge.copy(fontSize = 8.5.sp, letterSpacing = 1.sp)
    Text("#", style = st, color = Aditv.TextTertiary, modifier = Modifier.width(numW))
    Text("ÉQUIPE", style = st, color = Aditv.TextTertiary, modifier = Modifier.weight(1f))
    listOf("J", "G", "N", "P", "DIFF", "PTS").forEach { Text(it, style = st, color = Aditv.TextTertiary, textAlign = TextAlign.Center, modifier = Modifier.width(if (it.length > 1) 44.dp else numW)) }
    Text("FORME", style = st, color = Aditv.TextTertiary, textAlign = TextAlign.Center, modifier = Modifier.width(86.dp))
}

@Composable
private fun StandingLine(r: com.aditv.data.football.StandingRow) {
    val zone = r.description.orEmpty().lowercase()
    val mark = when {
        "relegation" in zone || "relégation" in zone -> Aditv.Live
        "champions" in zone -> Aditv.Accent
        "europa" in zone -> Color(0xFFFF9F43)
        "conference" in zone -> Color(0xFF34C759)
        zone.isNotBlank() -> Color.White.copy(alpha = 0.35f)
        else -> Color.Transparent
    }
    val mine = FootballRepository.isFollowed(Fixture(0, Competition(0, "", "", null, null), null, 0, "NS", null, r.team, r.team, null, null))
    val shape = RoundedCornerShape(9.dp)
    // Ligne focalisable : ↑/↓ font défiler le tableau à la télécommande.
    Surface(
        onClick = {}, modifier = Modifier.fillMaxWidth().height(30.dp),
        shape = ClickableSurfaceDefaults.shape(shape), scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
        colors = ClickableSurfaceDefaults.colors(containerColor = if (mine) Aditv.Accent.copy(alpha = 0.12f) else Color.Transparent, contentColor = Aditv.Text,
            focusedContainerColor = Color.White.copy(alpha = 0.16f), focusedContentColor = Aditv.Text),
    ) {
        Row(Modifier.fillMaxSize().padding(end = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(3.dp).fillMaxHeight().padding(vertical = 5.dp).background(mark, RoundedCornerShape(2.dp)))
            Spacer(Modifier.width(7.dp))
            Text(r.rank.toString(), fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, modifier = Modifier.width(numW - 10.dp))
            Crest(r.team.logo, r.team.name, 18.dp); Spacer(Modifier.width(8.dp))
            Text(r.team.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (mine) FontWeight.SemiBold else FontWeight.Normal), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            listOf(r.played, r.win, r.draw, r.lose).forEach { Text(it.toString(), style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.width(numW)) }
            Text((if (r.goalsDiff > 0) "+" else "") + r.goalsDiff, style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary, textAlign = TextAlign.Center, modifier = Modifier.width(44.dp))
            Text(r.points.toString(), fontFamily = Outfit, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.width(44.dp))
            Row(Modifier.width(86.dp), horizontalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterHorizontally)) {
                r.form.orEmpty().takeLast(5).forEach { ch ->
                    val (bg, txt) = when (ch) { 'W' -> Color(0xFF2FB36A) to "V"; 'D' -> Color(0xFF6B7280) to "N"; 'L' -> Color(0xFFE5484D) to "D"; else -> Color.Transparent to "" }
                    Box(Modifier.size(14.dp).background(bg, RoundedCornerShape(3.dp)), contentAlignment = Alignment.Center) { Text(txt, fontSize = 7.sp, fontWeight = FontWeight.Bold, color = Color.White) }
                }
            }
        }
    }
}

/** Compositions plein écran : terrain horizontal (domicile à gauche, extérieur à droite), photos, numéros, remplaçants. Retour ferme. */
@Composable
private fun LineupSheet(f: Fixture, lineups: List<com.aditv.data.football.Lineup>, onClose: () -> Unit) {
    androidx.activity.compose.BackHandler { onClose() }
    val close = remember { FocusRequester() }
    com.aditv.ui.common.InitialFocus(close)
    val home = lineups.firstOrNull { it.teamId == f.home.id } ?: lineups.getOrNull(0)
    val away = lineups.firstOrNull { it.teamId == f.away.id && it !== home } ?: lineups.getOrNull(1)
    Box(Modifier.fillMaxSize().background(Color(0xE0030508)), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = 62.dp, bottom = 18.dp).liquidGlass(RoundedCornerShape(24.dp), strength = 1.85f).padding(horizontal = 20.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamHead(f.home, home, Modifier.weight(1f), end = false)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    ScoreOrTime(f, 18.sp)
                }
                TeamHead(f.away, away, Modifier.weight(1f), end = true)
                Spacer(Modifier.width(14.dp))
                PillButton("Fermer", onClose, Modifier.focusRequester(close), icon = null, primary = false, small = true)
            }
            Spacer(Modifier.height(10.dp))
            Pitch(home, away, Modifier.fillMaxWidth().weight(1f))
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                listOf(home, away).forEach { l ->
                    Text(
                        if (l == null) "Composition non communiquée."
                        else listOfNotNull(l.coach?.let { "Entr. $it" }, l.substitutes.takeIf { it.isNotEmpty() }?.joinToString(", ", prefix = "Remplaçants : ") { (it.number?.let { n -> "$n " } ?: "") + it.name }).joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 8.5.sp, lineHeight = 11.sp), color = Aditv.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun TeamHead(t: com.aditv.data.football.Team, l: com.aditv.data.football.Lineup?, modifier: Modifier, end: Boolean) =
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = if (end) Arrangement.End else Arrangement.Start) {
        val items: List<@Composable () -> Unit> = listOf(
            { Crest(t.logo, t.name, 28.dp) },
            { Spacer(Modifier.width(9.dp)) },
            { Text(t.name, style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold), color = Aditv.Text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            { Spacer(Modifier.width(9.dp)) },
            { l?.formation?.let { Text(it, style = MaterialTheme.typography.labelLarge.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold), color = Aditv.OnLight, modifier = Modifier.background(Aditv.Accent, RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 2.dp)) } },
        )
        (if (end) items.reversed() else items).forEach { it() }
        if (!end) Spacer(Modifier.width(12.dp))
    }

/** Rangée (1 = gardien) et colonne de chaque titulaire : « grid » de l'API, sinon déduites de la formation et de l'ordre. */
private fun placeOnPitch(l: com.aditv.data.football.Lineup): List<Triple<com.aditv.data.football.LineupPlayer, Int, Int>> {
    val fromGrid = l.startXI.mapNotNull { p -> p.grid?.split(':')?.let { g -> g.getOrNull(0)?.toIntOrNull()?.let { r -> g.getOrNull(1)?.toIntOrNull()?.let { c -> Triple(p, r, c) } } } }
    if (fromGrid.size == l.startXI.size) return fromGrid
    val rows = listOf(1) + (l.formation?.split('-')?.mapNotNull { it.toIntOrNull() } ?: listOf(4, 4, 2))
    var i = 0
    return rows.flatMapIndexed { r, n -> (1..n).mapNotNull { c -> l.startXI.getOrNull(i++)?.let { Triple(it, r + 1, c) } } }
}

private val PitchDark = Color(0xFF0C2A22)
private val PitchLight = Color(0xFF113629)

@Composable
private fun Pitch(home: com.aditv.data.football.Lineup?, away: com.aditv.data.football.Lineup?, modifier: Modifier) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.clip(RoundedCornerShape(16.dp)).background(PitchDark)) {
        val w = maxWidth; val h = maxHeight
        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            // Bandes de tonte, puis lignes blanches discrètes.
            val bands = 12
            for (b in 0 until bands) if (b % 2 == 0) drawRect(PitchLight, topLeft = androidx.compose.ui.geometry.Offset(size.width * b / bands, 0f), size = androidx.compose.ui.geometry.Size(size.width / bands, size.height))
            val line = Color.White.copy(alpha = 0.22f); val sw = 1.5.dp.toPx(); val m = 8.dp.toPx()
            val st = androidx.compose.ui.graphics.drawscope.Stroke(sw)
            drawRect(line, androidx.compose.ui.geometry.Offset(m, m), androidx.compose.ui.geometry.Size(size.width - 2 * m, size.height - 2 * m), style = st)
            drawLine(line, androidx.compose.ui.geometry.Offset(size.width / 2, m), androidx.compose.ui.geometry.Offset(size.width / 2, size.height - m), sw)
            drawCircle(line, size.height * 0.16f, center, style = st)
            drawCircle(line, 2.5.dp.toPx(), center)
            listOf(false, true).forEach { right ->
                fun box(depth: Float, height: Float) {
                    val x = if (right) size.width - m - depth else m
                    drawRect(line, androidx.compose.ui.geometry.Offset(x, (size.height - height) / 2), androidx.compose.ui.geometry.Size(depth, height), style = st)
                }
                box(size.width * 0.12f, size.height * 0.56f); box(size.width * 0.045f, size.height * 0.28f)
            }
            // Léger halo turquoise au centre (identité ADI TV).
            drawCircle(Brush.radialGradient(listOf(Aditv.Accent.copy(alpha = 0.10f), Color.Transparent), center, size.height * 0.6f), size.height * 0.6f, center)
        }
        listOf(home to false, away to true).forEach { (l, right) ->
            l ?: return@forEach
            val spots = placeOnPitch(l)
            val rows = spots.maxOfOrNull { it.second } ?: 1
            spots.forEach { (p, r, c) ->
                val inRow = spots.count { it.second == r }
                val fx = 0.05f + (r - 1).toFloat() / (rows - 1).coerceAtLeast(1) * 0.39f
                val fy = c.toFloat() / (inRow + 1)
                val x = if (right) 1f - fx else fx
                val y = if (right) 1f - fy else fy
                PitchPlayer(p, l.shirt, l.shirtNumber, Modifier.offset(x = w * x - 55.dp, y = h * y - 19.dp))
            }
        }
    }
}

@Composable
private fun PitchPlayer(p: com.aditv.data.football.LineupPlayer, shirt: Long?, shirtNumber: Long?, modifier: Modifier) {
    val ring = shirt?.let { Color(0xFF000000 or it) } ?: Aditv.Accent
    Column(modifier.width(110.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(ring).padding(1.5.dp).clip(CircleShape).background(Color(0xFF1B2430)), contentAlignment = Alignment.Center) {
            Text(p.number?.toString() ?: "", fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 10.sp, color = Aditv.Text)
            p.photo?.let { RemoteImage(it, p.name, Modifier.fillMaxSize(), ContentScale.Crop, fallbackTitle = false) }
        }
        Spacer(Modifier.height(2.dp))
        Text((p.number?.takeIf { p.photo != null }?.let { "$it  " } ?: "") + shortName(p.name), style = MaterialTheme.typography.bodySmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Medium,
            shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = 0.7f), blurRadius = 6f)), color = Aditv.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

/** « Caoimhin Kelleher » → « C. Kelleher » quand le nom est long (le terrain est étroit). */
private fun shortName(n: String): String {
    if (n.length <= 14) return n
    val parts = n.trim().split(' ').filter { it.isNotBlank() }
    return if (parts.size >= 2 && !parts[0].endsWith(".")) parts[0].first() + ". " + parts.drop(1).joinToString(" ") else n
}

