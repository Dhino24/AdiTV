package com.aditv.ui.live

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.aditv.R
import com.aditv.core.DeviceProfile
import com.aditv.data.Session
import com.aditv.data.db.CatalogCard
import com.aditv.data.epg.Programme
import com.aditv.data.variants.collapsed
import com.aditv.di.AppContainer
import com.aditv.ui.LocalContainer
import com.aditv.ui.common.RemoteImage
import com.aditv.ui.common.navSound
import com.aditv.ui.design.FilterChip
import com.aditv.ui.design.LiveTag
import com.aditv.ui.design.PillButton
import com.aditv.ui.design.StateBox
import com.aditv.ui.guide.GuideViewModel
import com.aditv.ui.theme.Aditv
import com.aditv.ui.theme.Outfit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LiveFilter(val key: String, val label: String)
data class LiveRow(val channel: CatalogCard, val now: Programme?, val next: Programme?)

/**
 * En direct (maquette validée) : liste des chaînes à gauche (programme en cours et sa progression),
 * aperçu du programme à droite avec « Regarder » et « Guide TV ». Le filtre choisi est partagé avec le guide.
 */
class LiveViewModel(private val c: AppContainer, private val accountId: String) : ViewModel() {
    var filters by mutableStateOf<List<LiveFilter>>(emptyList()); private set
    var filter by mutableStateOf<LiveFilter?>(null); private set
    var rows by mutableStateOf<List<LiveRow>?>(null); private set
    var favorites by mutableStateOf<Set<String>>(emptySet()); private set
    var truncated by mutableStateOf<Int?>(null); private set
    private var loadJob: kotlinx.coroutines.Job? = null

    init {
        viewModelScope.launch {
            val favs = c.db.live().favorites(accountId).first()
            val cats = c.db.catalog().categories(accountId, "LIVE").first()
            filters = buildList {
                if (favs.isNotEmpty()) add(LiveFilter("fav", "Favoris"))
                add(LiveFilter("sport", "Sport"))
                add(LiveFilter("all", "Toutes"))
                cats.forEach { add(LiveFilter("cat:${it.id}", it.name)) }
            }
            val last = c.prefs.guideFilter(accountId).first()
            // Jamais « Toutes » à l'ouverture (trop lourd sur la Stick) : dernier filtre, sinon Favoris, sinon catégorie française.
            filter = filters.firstOrNull { it.key == last && it.key != "all" }
                ?: filters.firstOrNull { it.key == "fav" }
                ?: cats.firstOrNull { GuideViewModel.isFrenchCategory(it.name) }?.let { f -> filters.first { it.key == "cat:${f.id}" } }
                ?: cats.firstOrNull()?.let { f -> filters.first { it.key == "cat:${f.id}" } }
                ?: filters.first { it.key == "sport" }
            reload()
        }
        viewModelScope.launch { c.db.live().favoriteIds(accountId).collect { favorites = it.toSet() } }
        // Programmes en cours rafraîchis au début de chaque minute.
        viewModelScope.launch { while (true) { delay(60_000 - System.currentTimeMillis() % 60_000 + 500); if (rows != null) reload() } }
    }

    fun select(f: LiveFilter) {
        if (f == filter) return
        filter = f; rows = null
        viewModelScope.launch { c.prefs.setGuideFilter(accountId, f.key) }
        reload()
    }

    private fun reload() {
        val f = filter ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val (list, cut) = withContext(Dispatchers.Default) { compute(f) }
            rows = list; truncated = cut
        }
    }

    private suspend fun compute(f: LiveFilter): Pair<List<LiveRow>, Int?> {
        val prefs = c.prefs.variantPrefs.first()
        val raw = when {
            f.key == "fav" -> c.db.live().favorites(accountId).first()
            f.key == "sport" -> {
                val names = c.db.catalog().categories(accountId, "LIVE").first().associate { it.id to it.name }
                c.db.catalog().allChannels(accountId).filter { ch -> com.aditv.data.sport.SportMatcher.isSportChannel(ch.title, ch.categoryId?.let(names::get)) }
                    .sortedBy { ch -> if (ch.lang == "fr") 0 else 1 }
            }
            f.key.startsWith("cat:") -> c.db.catalog().cardsInCategory(accountId, "LIVE", f.key.removePrefix("cat:")).first()
            else -> c.db.catalog().allChannels(accountId)
        }
        val all = raw.collapsed(prefs).map { it.first }.distinctBy { it.id }
        val max = DeviceProfile.guideAllMax
        val channels = if (f.key == "all") all.take(max) else all
        val now = System.currentTimeMillis()
        val progs = c.epg.programmesFor(accountId, channels.map { it.id to it.epgChannelId }, now, now + 6 * 3_600_000L)
        return channels.map { ch ->
            val list = progs[ch.id].orEmpty()
            val cur = list.firstOrNull { it.isLive(now) }
            LiveRow(ch, cur, list.firstOrNull { it.startUtc >= (cur?.endUtc ?: now) })
        } to (if (f.key == "all" && all.size > max) all.size else null)
    }

    fun toggleFavorite(id: String) = viewModelScope.launch {
        if (id in favorites) c.db.live().removeFavorite(accountId, id)
        else c.db.live().addFavorite(com.aditv.data.db.FavoriteChannelEntity(accountId, id, System.currentTimeMillis()))
    }

    val listKey: String? get() = filter?.key
}

private val timeFmt = SimpleDateFormat("HH:mm", Locale.FRANCE)
private fun progressOf(p: Programme?, now: Long) = p?.takeIf { it.isLive(now) }?.let { ((now - it.startUtc).toFloat() / (it.endUtc - it.startUtc)).coerceIn(0f, 1f) }

@Composable
fun LiveScreen(session: Session.Active, onPlayLive: (String, String?) -> Unit, onGuide: () -> Unit) {
    val c = LocalContainer.current
    val vm: LiveViewModel = viewModel(key = "live-${session.account.id}") { LiveViewModel(c, session.account.id) }
    var selected by remember { mutableStateOf<LiveRow?>(null) }
    val firstChannel = remember { FocusRequester() }
    val entry = com.aditv.ui.LocalScreenEntry.current
    val rows = vm.rows
    // Aperçu : chaîne sélectionnée, sinon la première ; mis à jour avec les programmes de la minute.
    val shown = rows?.firstOrNull { it.channel.id == selected?.channel?.id } ?: rows?.firstOrNull()
    com.aditv.ui.common.InitialFocus(firstChannel, key = rows?.isNotEmpty() == true, enabled = rows?.isNotEmpty() == true)

    Row(Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 62.dp, bottom = 16.dp)) {
        // ---------- colonne des chaînes ----------
        Column(Modifier.width(300.dp).fillMaxHeight()) {
            Text("En direct", fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, color = Aditv.Text)
            Spacer(Modifier.height(10.dp))
            LazyRow(Modifier.focusRestorer(), horizontalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp)) {
                items(vm.filters.distinctBy { it.key }, key = { it.key }) { f -> FilterChip(f.label, f == vm.filter, { vm.select(f) }) }
            }
            Spacer(Modifier.height(10.dp))
            when {
                rows == null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { repeat(5) { Box(Modifier.fillMaxWidth().height(58.dp).background(Aditv.Surface1, RoundedCornerShape(8.dp))) } }
                rows.isEmpty() -> StateBox(R.drawable.ic_star_outline, "Aucune chaîne", "Choisissez un autre filtre, ou ajoutez des favoris (☆ sur une chaîne).")
                else -> LazyColumn(
                    Modifier.fillMaxSize().focusRestorer(firstChannel), verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 4.dp, horizontal = 3.dp),
                ) {
                    items(rows, key = { it.channel.id }) { r ->
                        ChannelRow(
                            r, r.channel.id in vm.favorites,
                            onFocus = { selected = r },
                            onClick = { onPlayLive(r.channel.id, vm.listKey) },
                            modifier = if (r === rows.first()) Modifier.focusRequester(firstChannel).focusRequester(entry) else Modifier,
                        )
                    }
                    vm.truncated?.let { n -> item(key = "cut") { Text("${DeviceProfile.guideAllMax} premières chaînes sur $n : choisissez une catégorie pour les autres.", style = MaterialTheme.typography.bodySmall, color = Aditv.TextTertiary, modifier = Modifier.padding(4.dp)) } }
                }
            }
        }
        Spacer(Modifier.width(24.dp))
        // ---------- aperçu ----------
        Column(Modifier.weight(1f).fillMaxHeight().padding(top = 4.dp)) {
            if (shown != null) Preview(shown, shown.channel.id in vm.favorites,
                onWatch = { onPlayLive(shown.channel.id, vm.listKey) }, onGuide = onGuide, onFavorite = { vm.toggleFavorite(shown.channel.id) })
        }
    }
}

@Composable
private fun ChannelRow(r: LiveRow, favorite: Boolean, onFocus: () -> Unit, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(8.dp)
    val now = System.currentTimeMillis()
    Surface(
        onClick = onClick,
        modifier = (modifier.fillMaxWidth().height(58.dp).onFocusChanged { if (it.isFocused) onFocus() }).navSound(),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(containerColor = Aditv.Surface1, contentColor = Aditv.Text, focusedContainerColor = Aditv.Surface2, focusedContentColor = Aditv.Text),
        border = ClickableSurfaceDefaults.border(
            border = Border(BorderStroke(0.75.dp, Color.White.copy(alpha = 0.10f)), shape = shape),
            focusedBorder = Border(BorderStroke(1.5.dp, Aditv.Accent), shape = shape),
        ),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(58.dp).fillMaxHeight().padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                if (r.channel.imageUrl != null) RemoteImage(r.channel.imageUrl, r.channel.title, Modifier.fillMaxSize(), ContentScale.Fit, fallbackTitle = false)
                else Text(r.channel.title.take(4).uppercase(), fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Aditv.Text, maxLines = 1)
            }
            Box(Modifier.padding(horizontal = 9.dp).width(0.75.dp).height(30.dp).background(Color.White.copy(alpha = 0.14f)))
            Column(Modifier.weight(1f)) {
                Text(r.channel.title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(r.now?.title ?: "Programme non communiqué", style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = Aditv.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                progressOf(r.now, now)?.let { f ->
                    Box(Modifier.padding(top = 5.dp).width(120.dp).height(2.dp).background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(1.dp))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(Aditv.Accent, RoundedCornerShape(1.dp)))
                    }
                }
            }
            Icon(painterResource(if (favorite) R.drawable.ic_star else R.drawable.ic_star_outline), contentDescription = if (favorite) "Favori" else null,
                tint = if (favorite) Aditv.Accent else Aditv.TextTertiary, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun Preview(r: LiveRow, favorite: Boolean, onWatch: () -> Unit, onGuide: () -> Unit, onFavorite: () -> Unit) {
    val now = System.currentTimeMillis()
    val p = r.now
    // Visuel : logo de la chaîne sur un fond sombre (aucune image du programme n'est fournie par le guide).
    Box(
        Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp))
            .background(Brush.radialGradient(listOf(Color(0xFF243241), Color(0xFF0E141B)))),
        contentAlignment = Alignment.Center,
    ) {
        if (r.channel.imageUrl != null) RemoteImage(r.channel.imageUrl, r.channel.title, Modifier.fillMaxWidth(0.42f).fillMaxHeight(0.42f), ContentScale.Fit, fallbackTitle = false)
        else Text(r.channel.title.uppercase(), fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, letterSpacing = 2.sp, color = Aditv.Text.copy(alpha = 0.85f), maxLines = 2)
        if (p != null) LiveTag(Modifier.align(Alignment.TopStart).padding(12.dp))
    }
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(r.channel.title, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = Aditv.Text, maxLines = 1)
        if (p != null) {
            Box(Modifier.padding(horizontal = 10.dp).width(1.dp).height(16.dp).background(Color.White.copy(alpha = 0.35f)))
            Text(p.title, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, color = Aditv.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        }
    }
    if (p != null) {
        Spacer(Modifier.height(4.dp))
        Text(timeFmt.format(Date(p.startUtc)) + " – " + timeFmt.format(Date(p.endUtc)) + (p.category?.let { "  ·  $it" } ?: ""), style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary)
        progressOf(p, now)?.let { f ->
            Box(Modifier.padding(top = 7.dp).fillMaxWidth(0.62f).height(3.dp).background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(2.dp))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(Aditv.Accent, RoundedCornerShape(2.dp)))
            }
        }
        r.next?.let { n ->
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("À suivre  " + timeFmt.format(Date(n.startUtc)), style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary)
                Box(Modifier.padding(horizontal = 7.dp).size(3.dp).background(Aditv.TextTertiary, CircleShape))
                Text(n.title, style = MaterialTheme.typography.bodyLarge, color = Aditv.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        p.description?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp)) }
    } else {
        Spacer(Modifier.height(4.dp))
        Text("Programme non communiqué par le guide.", style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary)
    }
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        PillButton("Regarder", onWatch)
        PillButton("Guide TV", onGuide, icon = R.drawable.ic_guide, primary = false)
        PillButton(if (favorite) "Favori" else "Ajouter aux favoris", onFavorite, icon = if (favorite) R.drawable.ic_star else R.drawable.ic_star_outline, primary = false)
    }
}
