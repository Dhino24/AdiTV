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
import com.aditv.R
import com.aditv.ui.design.FilterChip
import com.aditv.ui.design.PillButton
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
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
private val CHANNEL_COL = 142.dp
private val ROW_H = 38.dp

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
    var preview by remember { mutableStateOf<Pair<CatalogCard, Programme?>?>(null) }
    var scrollPx by remember { mutableFloatStateOf(0f) }
    var viewportPx by remember { mutableFloatStateOf(1f) }
    val density = LocalDensity.current
    val pxPerMin = with(density) { DP_PER_MIN.toPx() }
    val first = remember { FocusRequester() }
    val gridFocus = remember { FocusRequester() }
    var panelWasOpen by remember { mutableStateOf(false) }
    // Panneau fermé : le focus revient sur la case du programme (pas sur la barre de navigation).
    LaunchedEffect(selected) {
        if (selected != null) panelWasOpen = true
        else if (panelWasOpen) { panelWasOpen = false; repeat(10) { androidx.compose.runtime.withFrameNanos { }; if (runCatching { gridFocus.requestFocus() }.getOrDefault(false)) return@LaunchedEffect } }
    }
    val entry = LocalScreenEntry.current
    val dayFmt = remember { SimpleDateFormat("EEEE d MMMM", Locale.FRANCE) }
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.FRANCE) }
    val favIds by remember { c.db.live().favoriteIds(session.account.id) }.collectAsStateWithLifecycle(emptyList())
    LaunchedEffect(vm.windowStart) { scrollPx = ((System.currentTimeMillis() - vm.windowStart) / 60_000f * pxPerMin - 60 * pxPerMin).coerceAtLeast(0f) }
    InitialFocus(first, key = vm.rows != null, enabled = vm.rows != null)
    LaunchedEffect(vm.rows) {
        if (preview == null) vm.rows?.firstOrNull()?.let { r -> val now = System.currentTimeMillis(); preview = r.channel to r.programmes.firstOrNull { it.isLive(now) } }
    }

    /** Garde la cellule focalisée visible : toutes les rangées défilent ensemble (même axe horaire). */
    fun reveal(startMin: Float, widthMin: Float) {
        val x = startMin * pxPerMin; val w = widthMin * pxPerMin
        if (x < scrollPx) scrollPx = (x - 20 * pxPerMin).coerceAtLeast(0f)
        else if (x + minOf(w, viewportPx * 0.6f) > scrollPx + viewportPx) scrollPx = x + minOf(w, viewportPx * 0.6f) - viewportPx + 20 * pxPerMin
    }

    Column(Modifier.fillMaxSize().padding(top = 66.dp)) {
        // Aperçu de la chaîne / du programme sélectionné.
        Box(Modifier.padding(horizontal = 48.dp).height(150.dp)) { preview?.let { (ch, p) -> LivePreview(ch, p, ch.id in favIds, timeFmt) } }
        Row(Modifier.padding(start = 48.dp, end = 48.dp, top = 6.dp).height(34.dp), verticalAlignment = Alignment.CenterVertically) {
            LazyRow(Modifier.weight(1f).focusRestorer(), horizontalArrangement = Arrangement.spacedBy(5.dp), contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp)) {
                items(vm.filters, key = { it.key }) { f -> FilterChip(f.label, f == vm.filter, { vm.select(f) }) }
            }
            Spacer(Modifier.width(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip("Maintenant", false, { vm.goNow() })
                FilterChip("− 6 h", false, { vm.shift(-6 * HOUR) })
                FilterChip("+ 6 h", false, { vm.shift(6 * HOUR) })
                FilterChip("Jour suivant", false, { vm.shift(24 * HOUR) })
            }
        }
        Row(Modifier.padding(start = 48.dp, end = 48.dp, top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(dayFmt.format(Date(vm.windowStart)).replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall, color = Aditv.TextTertiary)
            Spacer(Modifier.width(10.dp))
            vm.coverageText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Aditv.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        // Axe horaire
        Row(Modifier.padding(start = 48.dp, end = 48.dp)) {
            Spacer(Modifier.width(CHANNEL_COL))
            Box(Modifier.fillMaxWidth().height(20.dp).clipToBounds().onSizeChanged { viewportPx = it.width.toFloat() }) {
                for (h in 0 until 24) {
                    val t = vm.windowStart + h * 30 * 60_000L
                    Text(timeFmt.format(Date(t)), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), color = Aditv.TextTertiary,
                        modifier = Modifier.padding(top = 4.dp).offset(x = with(density) { (h * 30 * pxPerMin - scrollPx).toDp() }))
                }
                val nowX = (System.currentTimeMillis() - vm.windowStart) / 60_000f * pxPerMin - scrollPx
                if (nowX in 0f..viewportPx) Text(timeFmt.format(Date()), fontSize = 7.5.sp, fontWeight = FontWeight.Bold, color = Color(0xFF041012),
                    modifier = Modifier.offset(x = with(density) { nowX.toDp() } - 14.dp, y = 3.dp).background(Aditv.Accent, RoundedCornerShape(50)).padding(horizontal = 5.dp, vertical = 1.dp))
            }
        }
        val rows = vm.rows
        when {
            rows == null -> Column(Modifier.padding(start = 48.dp, top = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(5) { Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { com.aditv.ui.design.SkeletonCard(CHANNEL_COL - 8.dp, (CHANNEL_COL - 8.dp) / ROW_H); com.aditv.ui.design.SkeletonCard(560.dp, 560f / ROW_H.value) } }
            }
            rows.isEmpty() -> Box(Modifier.fillMaxSize().padding(top = 20.dp), contentAlignment = Alignment.TopCenter) {
                com.aditv.ui.design.StateBox(R.drawable.ic_star_outline, "Aucune chaîne dans ce filtre", "Ajoutez une chaîne aux favoris depuis le lecteur ou sa fiche, ou choisissez « Toutes ».")
            }
            else -> Box(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize().focusRequester(gridFocus).focusRestorer(), verticalArrangement = Arrangement.spacedBy(5.dp), contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 4.dp, bottom = 30.dp)) {
                    items(rows, key = { it.channel.id }) { row ->
                        val isFirst = row === rows.first()
                        val now = System.currentTimeMillis()
                        Row(Modifier.height(ROW_H)) {
                            Cell(
                                modifier = Modifier.width(CHANNEL_COL - 8.dp).fillMaxHeight().onFocusChanged { if (it.isFocused) preview = row.channel to row.programmes.firstOrNull { p -> p.isLive(now) } },
                                onClick = { onPlayLive(row.channel.id, vm.filter?.key) }, kind = CellKind.CHANNEL,
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (row.channel.imageUrl != null) { com.aditv.ui.common.RemoteImage(row.channel.imageUrl, row.channel.title, Modifier.size(width = 26.dp, height = 18.dp), androidx.compose.ui.layout.ContentScale.Fit, fallbackTitle = false); Spacer(Modifier.width(7.dp)) }
                                    Text(row.channel.title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 9.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    if (row.channel.id in favIds) Icon(painterResource(R.drawable.ic_star), contentDescription = "Favori", tint = Aditv.Accent, modifier = Modifier.size(9.dp))
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            Box(Modifier.fillMaxWidth().fillMaxHeight().clipToBounds()) {
                                if (row.programmes.isEmpty()) {
                                    Cell(
                                        Modifier.offset(x = with(density) { (-scrollPx).toDp() }).width(DP_PER_MIN * (WINDOW / 60_000f)).fillMaxHeight()
                                            .then(if (isFirst) Modifier.focusRequester(first).focusRequester(entry) else Modifier)
                                            .onFocusChanged { if (it.isFocused) preview = row.channel to null },
                                        onClick = { onPlayLive(row.channel.id, vm.filter?.key) }, kind = CellKind.NONE,
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.offset(x = with(density) { scrollPx.toDp() })) {
                                            Icon(painterResource(R.drawable.ic_warn), contentDescription = null, modifier = Modifier.size(11.dp))
                                            Spacer(Modifier.width(7.dp))
                                            Text("Aucun guide pour cette chaîne — OK pour regarder", style = MaterialTheme.typography.bodyMedium.copy(fontSize = 9.5.sp))
                                        }
                                    }
                                }
                                val entryIndex = row.programmes.indexOfFirst { it.isLive(now) }.coerceAtLeast(0)
                                row.programmes.forEachIndexed { i, p ->
                                    // Chevauchement : la fin affichée est rognée au début du suivant, sans modifier les données.
                                    val end = minOf(p.endUtc, row.programmes.getOrNull(i + 1)?.startUtc?.takeIf { it > p.startUtc } ?: p.endUtc)
                                    val startMin = ((maxOf(p.startUtc, vm.windowStart) - vm.windowStart) / 60_000f)
                                    val widthMin = ((minOf(end, vm.windowStart + WINDOW) - maxOf(p.startUtc, vm.windowStart)) / 60_000f).coerceAtLeast(5f)
                                    val live = p.isLive(now)
                                    Cell(
                                        Modifier.offset(x = with(density) { (startMin * pxPerMin - scrollPx).toDp() })
                                            .width(DP_PER_MIN * widthMin - 3.dp).fillMaxHeight()
                                            .then(if (isFirst && i == entryIndex) Modifier.focusRequester(first).focusRequester(entry) else Modifier)
                                            .onFocusChanged { if (it.isFocused) { reveal(startMin, widthMin); preview = row.channel to p } },
                                        onClick = { selected = row.channel to p },
                                        kind = if (live) CellKind.NOW else if (p.endUtc < now) CellKind.PAST else CellKind.NEXT,
                                        progress = if (live) ((now - p.startUtc).toFloat() / (p.endUtc - p.startUtc)) else null,
                                    ) {
                                        Column {
                                            Text(p.title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 9.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(timeFmt.format(Date(p.startUtc)) + " – " + timeFmt.format(Date(p.endUtc)), style = MaterialTheme.typography.bodySmall.copy(fontSize = 7.5.sp), maxLines = 1)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                // Repère « maintenant » sur toute la hauteur de la grille.
                val nowX = (System.currentTimeMillis() - vm.windowStart) / 60_000f * pxPerMin - scrollPx
                if (nowX in 0f..viewportPx) Box(Modifier.padding(start = 48.dp + CHANNEL_COL).offset(x = with(density) { nowX.toDp() }).width(1.dp).fillMaxHeight().background(Aditv.Accent))
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
private fun LivePreview(ch: CatalogCard, p: Programme?, fav: Boolean, timeFmt: SimpleDateFormat) {
    val now = System.currentTimeMillis()
    Row {
        Box(Modifier.width(250.dp).height(141.dp).clip(RoundedCornerShape(8.dp)).background(Brush.radialGradient(listOf(Color(0xFF22303E), Color(0xFF10161D)))), contentAlignment = Alignment.Center) {
            if (ch.imageUrl != null) com.aditv.ui.common.RemoteImage(ch.imageUrl, ch.title, Modifier.fillMaxSize().padding(28.dp), androidx.compose.ui.layout.ContentScale.Fit, fallbackTitle = false)
            else Text(ch.title.uppercase(), fontFamily = com.aditv.ui.theme.Outfit, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, letterSpacing = 1.5.sp, color = Aditv.Text.copy(alpha = 0.85f), maxLines = 2)
            if (p != null && p.isLive(now)) {
                com.aditv.ui.design.LiveTag(Modifier.align(Alignment.TopStart).padding(8.dp))
                Box(Modifier.align(Alignment.BottomCenter)) { com.aditv.ui.design.CardProgressBox(((now - p.startUtc).toFloat() / (p.endUtc - p.startUtc))) }
            }
        }
        Spacer(Modifier.width(24.dp))
        Column(Modifier.padding(top = 2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ch.title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 10.sp, letterSpacing = 0.4.sp), color = Aditv.TextSecondary)
                if (fav) { Spacer(Modifier.width(6.dp)); Icon(painterResource(R.drawable.ic_star), contentDescription = "Favori", tint = Aditv.Accent, modifier = Modifier.size(10.dp)) }
            }
            Spacer(Modifier.height(7.dp))
            if (p == null) {
                Text("Programme non communiqué", style = MaterialTheme.typography.headlineMedium, color = Aditv.Text)
                Spacer(Modifier.height(6.dp))
                Text("Pas de guide pour cette chaîne : OK pour la regarder, ou associez une source depuis sa fiche.", style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary)
            } else {
                Text(p.title, style = MaterialTheme.typography.headlineMedium, color = Aditv.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (p.isLive(now)) { Box(Modifier.size(5.dp).background(Aditv.Live, CircleShape)); Spacer(Modifier.width(4.dp)); Text("EN DIRECT", fontSize = 7.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp, color = Color.White); Spacer(Modifier.width(9.dp)) }
                    Text((if (p.endUtc < now) "Terminé · " else if (!p.isLive(now)) "À venir · " else "") + timeFmt.format(Date(p.startUtc)) + " – " + timeFmt.format(Date(p.endUtc)) + (p.category?.let { "  ·  $it" } ?: ""),
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = Aditv.TextSecondary)
                }
                p.description?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 7.dp).widthIn(max = 470.dp)) }
            }
        }
    }
}

private enum class CellKind { CHANNEL, NOW, PAST, NEXT, NONE }

@Composable
private fun Cell(modifier: Modifier, onClick: () -> Unit, kind: CellKind, progress: Float? = null, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(5.dp)
    Surface(
        onClick = onClick, modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = if (kind == CellKind.CHANNEL) 1.04f else 1f),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = androidx.tv.material3.Glow(Aditv.Accent.copy(alpha = 0.3f), 8.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = when (kind) { CellKind.NOW -> Aditv.Surface3; CellKind.PAST -> Color(0xFF0F141A); CellKind.CHANNEL -> Aditv.Surface1; else -> Aditv.Surface2 },
            contentColor = if (kind == CellKind.PAST || kind == CellKind.NONE) Aditv.TextSecondary else Aditv.Text,
            focusedContainerColor = Aditv.Text, focusedContentColor = Aditv.OnLight,
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(0.5.dp, if (kind == CellKind.CHANNEL) Aditv.Line else Color.White.copy(alpha = 0.05f)), shape = shape),
        ),
    ) {
        Box(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().padding(horizontal = 9.dp), contentAlignment = Alignment.CenterStart) { content() }
            if (progress != null) Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(progress.coerceIn(0f, 1f)).height(1.5.dp).background(Aditv.Accent.copy(alpha = 0.8f)))
        }
    }
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
    Box(Modifier.fillMaxSize().background(Color(0x9E030508)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 470.dp).background(Color(0xEB10151C), RoundedCornerShape(14.dp)).padding(horizontal = 28.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            com.aditv.ui.design.Kicker(ch.title)
            Text(p.title, style = MaterialTheme.typography.headlineMedium, color = Aditv.Text)
            Text(fmt.format(Date(p.startUtc)).replaceFirstChar { it.uppercase() } + " – " + SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(p.endUtc)) +
                (p.category?.let { " · $it" } ?: ""), style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary)
            p.description?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = Aditv.Text, maxLines = 4, overflow = TextOverflow.Ellipsis) }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                when {
                    p.isLive(now) -> PillButton("Regarder en direct", onWatch, Modifier.focusRequester(first))
                    replay -> PillButton("Replay (expérimental)", onReplay, Modifier.focusRequester(first))
                    else -> PillButton(if (p.startUtc > now) "À venir · regarder la chaîne" else "Regarder la chaîne", onWatch, Modifier.focusRequester(first))
                }
                PillButton("Fiche de la chaîne", onChannel, icon = R.drawable.ic_info, primary = false)
                PillButton("Fermer", onClose, icon = null, primary = false)
            }
        }
    }
}
