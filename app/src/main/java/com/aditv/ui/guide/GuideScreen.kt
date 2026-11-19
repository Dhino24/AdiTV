package com.aditv.ui.guide

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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.aditv.data.Session
import com.aditv.data.db.CatalogCard
import com.aditv.data.db.CategoryEntity
import com.aditv.data.epg.Programme
import com.aditv.data.variants.collapsed
import com.aditv.di.AppContainer
import com.aditv.ui.LocalContainer
import com.aditv.ui.LocalScreenEntry
import com.aditv.ui.common.AditvButton
import com.aditv.ui.common.InitialFocus
import com.aditv.ui.theme.Aditv
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val HOUR = 3_600_000L
private const val WINDOW = 12 * HOUR
private val DP_PER_MIN = 4.dp
private val CHANNEL_COL = 190.dp
private val ROW_H = 64.dp

/** Filtre du guide : favoris, toutes les chaînes ou une catégorie du fournisseur. */
data class GuideFilter(val key: String, val label: String)

data class GuideRow(val channel: CatalogCard, val programmes: List<Programme>)

class GuideViewModel(private val c: AppContainer, private val accountId: String) : ViewModel() {
    var filters by mutableStateOf<List<GuideFilter>>(emptyList()); private set
    var filter by mutableStateOf<GuideFilter?>(null); private set
    var windowStart by mutableStateOf(alignedNow()); private set
    var rows by mutableStateOf<List<GuideRow>?>(null); private set
    var coverageText by mutableStateOf<String?>(null); private set
    var catchupLookup: Map<String, Int> = emptyMap(); private set

    init {
        viewModelScope.launch {
            val favs = c.db.live().favorites(accountId).first()
            val cats: List<CategoryEntity> = c.db.catalog().categories(accountId, "LIVE").first()
            filters = buildList {
                if (favs.isNotEmpty()) add(GuideFilter("fav", "★ Favoris"))
                add(GuideFilter("all", "Toutes"))
                cats.forEach { add(GuideFilter("cat:${it.id}", it.name)) }
            }
            filter = filters.first()
            load()
        }
    }

    fun select(f: GuideFilter) { filter = f; viewModelScope.launch { load() } }
    fun shift(deltaMs: Long) {
        val min = alignedNow() - 24 * HOUR
        val max = alignedNow() + 7 * 24 * HOUR - WINDOW
        windowStart = (windowStart + deltaMs).coerceIn(min, max); viewModelScope.launch { load() }
    }
    fun goNow() { windowStart = alignedNow(); viewModelScope.launch { load() } }

    private suspend fun load() {
        val f = filter ?: return
        val prefs = c.prefs.variantPrefs.first()
        val raw = when {
            f.key == "fav" -> c.db.live().favorites(accountId).first()
            f.key.startsWith("cat:") -> c.db.catalog().cardsInCategory(accountId, "LIVE", f.key.removePrefix("cat:")).first()
            else -> c.db.catalog().allChannels(accountId)
        }
        val channels = raw.collapsed(prefs).map { it.first }
        val programmes = c.epg.programmesFor(accountId, channels.map { it.id to it.epgChannelId }, windowStart, windowStart + WINDOW)
        rows = channels.map { GuideRow(it, programmes[it.id].orEmpty()) }
        val cov = c.epg.coverage(accountId)
        coverageText = "Couverture : ${cov.covered} / ${cov.channels} chaînes avec programmes à venir" +
            if (cov.withGuideId > cov.covered) " · ${cov.withGuideId - cov.covered} avec un ID de guide sans données" else ""
        catchupLookup = channels.associate { it.id to (c.db.catalog().observeItem(it.id).first()?.catchupDays ?: 0) }
    }

    companion object {
        /** Début de fenêtre : demi-heure pleine précédant « maintenant − 30 min ». */
        fun alignedNow(): Long { val n = System.currentTimeMillis() - 30 * 60_000; return n - n % (30 * 60_000) }
    }
}

@Composable
fun GuideScreen(session: Session.Active, onPlayLive: (String, String?) -> Unit, onPlay: (String, Boolean) -> Unit, onOpen: (String) -> Unit) {
    val c = LocalContainer.current
    val vm: GuideViewModel = viewModel(key = "guide-${session.account.id}") { GuideViewModel(c, session.account.id) }
    var selected by remember { mutableStateOf<Pair<CatalogCard, Programme>?>(null) }
    var scrollPx by remember { mutableFloatStateOf(0f) }
    var viewportPx by remember { mutableFloatStateOf(1f) }
    val density = LocalDensity.current
    val pxPerMin = with(density) { DP_PER_MIN.toPx() }
    val first = remember { FocusRequester() }
    val entry = LocalScreenEntry.current
    val dayFmt = remember { SimpleDateFormat("EEEE d MMMM", Locale.FRANCE) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.FRANCE) }
    LaunchedEffect(vm.windowStart) { scrollPx = ((System.currentTimeMillis() - vm.windowStart) / 60_000f * pxPerMin - 60 * pxPerMin).coerceAtLeast(0f) }
    InitialFocus(first, key = vm.rows != null, enabled = vm.rows != null)

    /** Garde la cellule focalisée visible : toutes les rangées défilent ensemble (même axe horaire). */
    fun reveal(startMin: Float, widthMin: Float) {
        val x = startMin * pxPerMin; val w = widthMin * pxPerMin
        if (x < scrollPx) scrollPx = (x - 20 * pxPerMin).coerceAtLeast(0f)
        else if (x + minOf(w, viewportPx * 0.6f) > scrollPx + viewportPx) scrollPx = x + minOf(w, viewportPx * 0.6f) - viewportPx + 20 * pxPerMin
    }

    Column(Modifier.fillMaxSize().padding(start = 40.dp, top = 28.dp, end = 24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Guide TV", style = MaterialTheme.typography.headlineMedium, color = Aditv.Text)
            Spacer(Modifier.width(20.dp))
            Text(dayFmt.format(Date(vm.windowStart)).replaceFirstChar { it.uppercase() } + " · " + timeFmt.format(Date(vm.windowStart)) + " → " + timeFmt.format(Date(vm.windowStart + WINDOW)),
                style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AditvButton("Maintenant", { vm.goNow() })
            AditvButton("− 6 h", { vm.shift(-6 * HOUR) })
            AditvButton("+ 6 h", { vm.shift(6 * HOUR) })
            AditvButton("Jour suivant", { vm.shift(24 * HOUR) })
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(Modifier.focusRestorer(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(vm.filters, key = { it.key }) { f -> AditvButton(f.label, { vm.select(f) }, secondaryText = if (f == vm.filter) "●" else null) }
        }
        vm.coverageText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary, modifier = Modifier.padding(top = 6.dp)) }
        Spacer(Modifier.height(8.dp))
        // Axe horaire
        Row {
            Spacer(Modifier.width(CHANNEL_COL))
            Box(Modifier.fillMaxWidth().height(24.dp).clipToBounds().onSizeChanged { viewportPx = it.width.toFloat() }) {
                for (h in 0 until 24) {
                    val t = vm.windowStart + h * 30 * 60_000L
                    Text(timeFmt.format(Date(t)), style = MaterialTheme.typography.labelMedium, color = Aditv.TextSecondary,
                        modifier = Modifier.offset(x = with(density) { (h * 30 * pxPerMin - scrollPx).toDp() }))
                }
            }
        }
        val rows = vm.rows
        when {
            rows == null -> Text("Chargement du guide…", style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary)
            rows.isEmpty() -> Text("Aucune chaîne dans ce filtre.", style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary)
            else -> Box(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize().focusRestorer(), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
                    items(rows, key = { it.channel.id }) { row ->
                        val isFirst = row === rows.first()
                        Row(Modifier.height(ROW_H)) {
                            Cell(
                                modifier = Modifier.width(CHANNEL_COL - 8.dp).fillMaxHeight(),
                                onClick = { onPlayLive(row.channel.id, vm.filter?.key) },
                                highlight = false,
                            ) {
                                Text(row.channel.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.fillMaxWidth().fillMaxHeight().clipToBounds()) {
                                val now = System.currentTimeMillis()
                                if (row.programmes.isEmpty()) {
                                    Cell(
                                        Modifier.offset(x = with(density) { (-scrollPx).toDp() }).width(DP_PER_MIN * (WINDOW / 60_000f)).fillMaxHeight()
                                            .then(if (isFirst) Modifier.focusRequester(first).focusRequester(entry) else Modifier),
                                        onClick = { onPlayLive(row.channel.id, vm.filter?.key) }, highlight = false, dim = true,
                                    ) { Text("Aucun guide pour cette chaîne — OK pour regarder", style = MaterialTheme.typography.bodyMedium) }
                                }
                                val entryIndex = row.programmes.indexOfFirst { it.isLive(now) }.coerceAtLeast(0)
                                row.programmes.forEachIndexed { i, p ->
                                    // Chevauchement : la fin affichée est rognée au début du suivant, sans modifier les données.
                                    val end = minOf(p.endUtc, row.programmes.getOrNull(i + 1)?.startUtc?.takeIf { it > p.startUtc } ?: p.endUtc)
                                    val startMin = ((maxOf(p.startUtc, vm.windowStart) - vm.windowStart) / 60_000f)
                                    val widthMin = ((minOf(end, vm.windowStart + WINDOW) - maxOf(p.startUtc, vm.windowStart)) / 60_000f).coerceAtLeast(5f)
                                    Cell(
                                        Modifier.offset(x = with(density) { (startMin * pxPerMin - scrollPx).toDp() })
                                            .width(DP_PER_MIN * widthMin - 4.dp).fillMaxHeight()
                                            .then(if (isFirst && i == entryIndex) Modifier.focusRequester(first).focusRequester(entry) else Modifier)
                                            .onFocusChanged { if (it.isFocused) reveal(startMin, widthMin) },
                                        onClick = { selected = row.channel to p },
                                        highlight = p.isLive(now), dim = p.endUtc < now,
                                    ) {
                                        Column {
                                            Text(p.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(timeFmt.format(Date(p.startUtc)) + "–" + timeFmt.format(Date(p.endUtc)), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                        }
                                    }
                                }
                                // Repère « maintenant »
                                val nowX = (now - vm.windowStart) / 60_000f * pxPerMin - scrollPx
                                if (nowX in 0f..viewportPx) Box(Modifier.offset(x = with(density) { nowX.toDp() }).width(2.dp).fillMaxHeight().background(Aditv.Accent))
                            }
                        }
                    }
                }
                selected?.let { (ch, p) ->
                    ProgrammePanel(ch, p, vm.catchupLookup[ch.id] ?: 0, session.isDemo,
                        onWatch = { selected = null; onPlayLive(ch.id, vm.filter?.key) },
                        onReplay = { selected = null; onPlay("CATCHUP|${ch.id}|${p.startUtc}|${((p.endUtc - p.startUtc) / 60_000).toInt()}", false) },
                        onChannel = { selected = null; onOpen(ch.id) },
                        onClose = { selected = null },
                        modifier = Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }
}

@Composable
private fun Cell(modifier: Modifier, onClick: () -> Unit, highlight: Boolean, dim: Boolean = false, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Surface(
        onClick = onClick, modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (highlight) Aditv.Surface2 else Aditv.Surface1,
            contentColor = if (dim) Aditv.TextSecondary else Aditv.Text,
            focusedContainerColor = Aditv.Text, focusedContentColor = Aditv.OnLight,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(if (highlight) 2.dp else 1.dp, if (highlight) Aditv.Accent else Aditv.Line), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, Aditv.Accent), shape = shape),
        ),
    ) { Box(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp), contentAlignment = Alignment.CenterStart) { content() } }
}

@Composable
private fun ProgrammePanel(
    ch: CatalogCard, p: Programme, catchupDays: Int, isDemo: Boolean,
    onWatch: () -> Unit, onReplay: () -> Unit, onChannel: () -> Unit, onClose: () -> Unit, modifier: Modifier,
) {
    val now = System.currentTimeMillis()
    val fmt = remember { SimpleDateFormat("EEEE d MMMM · HH:mm", Locale.FRANCE) }
    val first = remember { FocusRequester() }
    InitialFocus(first, key = p)
    androidx.activity.compose.BackHandler { onClose() }
    // Replay seulement si le fournisseur déclare une archive couvrant l'émission (jamais déduit du seul guide).
    val replay = !isDemo && catchupDays > 0 && p.endUtc < now && now - p.startUtc < catchupDays * 24 * HOUR
    Column(
        modifier.fillMaxWidth().widthIn(max = 1100.dp).background(Aditv.Surface1, RoundedCornerShape(16.dp)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(ch.title.uppercase(), style = MaterialTheme.typography.labelSmall, color = Aditv.Accent)
        Text(p.title, style = MaterialTheme.typography.headlineSmall, color = Aditv.Text)
        Text(fmt.format(Date(p.startUtc)).replaceFirstChar { it.uppercase() } + " – " + SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(p.endUtc)) +
            (p.category?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary)
        p.description?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = Aditv.Text, maxLines = 4, overflow = TextOverflow.Ellipsis) }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            when {
                p.isLive(now) -> AditvButton("Regarder en direct", onWatch, Modifier.focusRequester(first))
                replay -> AditvButton("Replay (expérimental)", onReplay, Modifier.focusRequester(first))
                else -> AditvButton(if (p.startUtc > now) "À venir · regarder la chaîne" else "Regarder la chaîne", onWatch, Modifier.focusRequester(first))
            }
            AditvButton("Fiche de la chaîne", onChannel)
            AditvButton("Fermer", onClose)
        }
    }
}
