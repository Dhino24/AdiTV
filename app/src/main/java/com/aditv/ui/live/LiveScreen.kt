package com.aditv.ui.live

import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
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
import androidx.compose.ui.graphics.graphicsLayer
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

data class LiveRow(val channel: CatalogCard, val now: Programme?, val next: Programme?)

/** Élément de la liste centrale : intertitre de diffuseur, chaîne, ou match de Ligue des champions. */
sealed interface LiveItem {
    val key: String
    data class Header(val text: String) : LiveItem { override val key = "h:$text" }
    data class Channel(val row: LiveRow) : LiveItem { override val key = row.channel.id }
    data class Match(val match: ChampionsMatch) : LiveItem { override val key = "m:${match.channel.id}:${match.startUtc}" }
}

/**
 * En direct : familles à gauche (Favoris, Ligue des champions, Sport France, Cinéma France, Divertissement France…,
 * Sénégal, puis le reste du monde), chaînes au centre groupées par diffuseur, programme en grand à droite.
 */
class LiveViewModel(private val c: AppContainer, private val accountId: String) : ViewModel() {
    var families by mutableStateOf<List<FamilyEntry>>(emptyList()); private set
    var family by mutableStateOf<FamilyEntry?>(null); private set
    var items by mutableStateOf<List<LiveItem>?>(null); private set
    var favorites by mutableStateOf<Set<String>>(emptySet()); private set
    private var loadJob: kotlinx.coroutines.Job? = null

    var error by mutableStateOf<String?>(null); private set

    init {
        viewModelScope.launch {
            families = try { withContext(Dispatchers.Default) { LiveCatalog.families(c, accountId) } }
                catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Throwable) { android.util.Log.w("AdiTV", "Direct : familles", e); error = "Chaînes indisponibles (${e.javaClass.simpleName})."; items = emptyList(); return@launch }
            val last = c.prefs.guideFilter(accountId).first()
            family = families.firstOrNull { it.key == last } ?: families.firstOrNull { it.key == "fam:SPORT_FR" } ?: families.firstOrNull()
            reload()
        }
        viewModelScope.launch { c.db.live().favoriteIds(accountId).collect { favorites = it.toSet() } }
        viewModelScope.launch { while (true) { delay(60_000 - System.currentTimeMillis() % 60_000 + 500); if (items != null) reload() } }
    }

    fun select(f: FamilyEntry) {
        if (f == family) return
        family = f; items = null
        viewModelScope.launch { c.prefs.setGuideFilter(accountId, f.key) }
        reload()
    }

    fun reload(refreshFamilies: Boolean = false) {
        val f = family ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            try {
                if (refreshFamilies) families = withContext(Dispatchers.Default) { LiveCatalog.families(c, accountId) }
                items = withContext(Dispatchers.Default) { compute(f.key) }
                error = null
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Throwable) {
                // Jamais de chargement sans fin : on montre l'erreur (et ce qui était déjà affiché reste).
                android.util.Log.w("AdiTV", "Direct : chargement", e)
                error = "Chaînes indisponibles (${e.javaClass.simpleName})."
                if (items == null) items = emptyList()
            }
        }
    }

    private suspend fun compute(key: String): List<LiveItem> {
        val placed = LiveCatalog.channels(c, accountId, key).take(if (DeviceProfile.highEnd) 800 else 300)
        val now = System.currentTimeMillis()
        val progs = c.epg.programmesFor(accountId, placed.map { it.first.id to it.first.epgChannelId }, now, now + 6 * 3_600_000L)
        return buildList {
            if (key == LiveCatalog.UCL) {
                val matches = LiveCatalog.championsMatches(c, accountId)
                if (matches.isNotEmpty()) { add(LiveItem.Header("Matchs")); matches.forEach { add(LiveItem.Match(it)) } }
            }
            var group: String? = null
            placed.forEach { (ch, place) ->
                val g = if (place.family == com.aditv.data.live.Family.MONDE) "${place.country ?: "Autres"} · ${place.broadcaster}" else place.broadcaster
                if (g != group) { group = g; add(LiveItem.Header(g)) }
                val list = progs[ch.id].orEmpty()
                val cur = list.firstOrNull { it.isLive(now) }
                add(LiveItem.Channel(LiveRow(ch, cur, list.firstOrNull { it.startUtc >= (cur?.endUtc ?: now) })))
            }
        }
    }

    fun toggleFavorite(id: String) = viewModelScope.launch {
        if (id in favorites) c.db.live().removeFavorite(accountId, id)
        else c.db.live().addFavorite(com.aditv.data.db.FavoriteChannelEntity(accountId, id, System.currentTimeMillis()))
        LiveCatalog.invalidate(); reload(refreshFamilies = true)
    }

    /** Correction manuelle : la chaîne change de famille (mémorisé). */
    fun moveTo(id: String, f: com.aditv.data.live.Family) = viewModelScope.launch {
        c.prefs.setChannelFamily(id, f.name); LiveCatalog.invalidate(); reload(refreshFamilies = true)
    }

    fun remindOrNull(m: ChampionsMatch) = viewModelScope.launch {
        c.sport.toggleReminder(accountId, m.title, m.startUtc, m.endUtc, m.channel.id, m.channel.title)
    }

    val listKey: String? get() = family?.key
}

private val timeFmt = SimpleDateFormat("HH:mm", Locale.FRANCE)
private val dayFmt = SimpleDateFormat("EEE d MMM", Locale.FRANCE)
private fun progressOf(p: Programme?, now: Long) = p?.takeIf { it.isLive(now) }?.let { ((now - it.startUtc).toFloat() / (it.endUtc - it.startUtc)).coerceIn(0f, 1f) }

@Composable
fun LiveScreen(session: Session.Active, onPlayLive: (String, String?) -> Unit, onGuide: () -> Unit) {
    val c = LocalContainer.current
    val vm: LiveViewModel = viewModel(key = "live-${session.account.id}") { LiveViewModel(c, session.account.id) }
    var selected by remember { mutableStateOf<LiveRow?>(null) }
    var moving by remember { mutableStateOf<CatalogCard?>(null) }
    val firstChannel = remember { FocusRequester() }
    val railFocus = remember { FocusRequester() }
    val entry = com.aditv.ui.LocalScreenEntry.current
    val items = vm.items
    val rows = items?.filterIsInstance<LiveItem.Channel>()?.map { it.row }
    val shown = rows?.firstOrNull { it.channel.id == selected?.channel?.id } ?: rows?.firstOrNull()
    com.aditv.ui.common.InitialFocus(railFocus, key = vm.families.isNotEmpty(), enabled = vm.families.isNotEmpty())

    Box(Modifier.fillMaxSize()) {
        shown?.let { LiveBackground(it.channel, session) }
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Aditv.Bg.copy(alpha = 0.97f), 0.45f to Aditv.Bg.copy(alpha = 0.9f), 0.66f to Aditv.Bg.copy(alpha = 0.25f), 0.88f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.5f to Color.Transparent, 1f to Aditv.Bg.copy(alpha = 0.92f))))

        Row(Modifier.fillMaxSize().padding(start = 40.dp, end = 40.dp, top = 84.dp, bottom = 16.dp)) {
            // ---------- familles ----------
            Column(Modifier.width(196.dp).fillMaxHeight()) {
                Text("En direct", fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = Aditv.Text)
                Spacer(Modifier.height(8.dp))
                LazyColumn(Modifier.fillMaxSize().focusRestorer(railFocus), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 4.dp, horizontal = 3.dp)) {
                    items(vm.families, key = { it.key }) { f ->
                        FamilyItem(f, f == vm.family, { vm.select(f) },
                            if (f == vm.families.first()) Modifier.focusRequester(railFocus).focusRequester(entry) else Modifier)
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            // ---------- chaînes groupées par diffuseur ----------
            Column(Modifier.width(300.dp).fillMaxHeight().padding(top = 36.dp)) {
                when {
                    items == null -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { repeat(5) { Box(Modifier.fillMaxWidth().height(56.dp).background(Aditv.Surface1, RoundedCornerShape(10.dp))) } }
                    items.isEmpty() -> StateBox(R.drawable.ic_star_outline, if (vm.error != null) "Chaînes indisponibles" else "Aucune chaîne", vm.error ?: "Rien dans cette famille pour l'instant.")
                    else -> LazyColumn(Modifier.fillMaxSize().focusRestorer(), verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp)) {
                        items(items, key = { it.key }) { it ->
                            when (it) {
                                is LiveItem.Header -> Text(it.text.uppercase(), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp, fontSize = 9.sp),
                                    color = Aditv.TextTertiary, modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 2.dp))
                                is LiveItem.Channel -> ChannelRow(
                                    it.row, it.row.channel.id in vm.favorites,
                                    onFocus = { selected = it.row },
                                    onClick = { onPlayLive(it.row.channel.id, vm.listKey) },
                                    onMenu = { moving = it.row.channel },
                                    modifier = Modifier,
                                )
                                is LiveItem.Match -> MatchRow(it.match, onClick = {
                                    if (System.currentTimeMillis() >= it.match.startUtc) onPlayLive(it.match.channel.id, vm.listKey) else vm.remindOrNull(it.match)
                                })
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Column(Modifier.width(400.dp).fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
                if (shown != null) ProgrammeInfo(shown, shown.channel.id in vm.favorites,
                    onWatch = { onPlayLive(shown.channel.id, vm.listKey) }, onGuide = onGuide, onFavorite = { vm.toggleFavorite(shown.channel.id) })
            }
        }
        moving?.let { ch -> MoveDialog(ch, onPick = { vm.moveTo(ch.id, it); moving = null }, onClose = { moving = null }) }
    }
}

@Composable
private fun FamilyItem(f: FamilyEntry, current: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Surface(
        onClick = onClick,
        modifier = (modifier.fillMaxWidth().height(40.dp)).navSound(),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (current) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.04f), contentColor = if (current) Aditv.Text else Aditv.TextSecondary,
            focusedContainerColor = Color.White, focusedContentColor = Aditv.OnLight,
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(1.5.dp, Aditv.Accent), shape = shape, inset = (-2).dp)),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(f.label, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(f.count.toString(), style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), modifier = Modifier.graphicsLayer { alpha = 0.6f })
        }
    }
}

@Composable
private fun MatchRow(m: ChampionsMatch, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    val now = System.currentTimeMillis()
    val live = now in m.startUtc until m.endUtc
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp).navSound(),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.06f), contentColor = Aditv.Text, focusedContainerColor = Color.White, focusedContentColor = Aditv.OnLight),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(1.5.dp, Aditv.Accent), shape = shape, inset = (-2).dp)),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(62.dp)) {
                if (live) LiveTag() else Text(timeFmt.format(Date(m.startUtc)), style = MaterialTheme.typography.titleMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold))
                Text(if (live) "en cours" else dayFmt.format(Date(m.startUtc)), style = MaterialTheme.typography.bodySmall.copy(fontSize = 8.sp), modifier = Modifier.graphicsLayer { alpha = 0.7f })
            }
            Column(Modifier.weight(1f)) {
                Text(m.title, style = MaterialTheme.typography.titleMedium.copy(fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(com.aditv.data.live.ChannelFamilies.cleanName(m.channel.title) + if (live) " · OK pour regarder" else " · OK pour un rappel", style = MaterialTheme.typography.bodySmall.copy(fontSize = 8.5.sp), maxLines = 1, modifier = Modifier.graphicsLayer { alpha = 0.7f })
            }
        }
    }
}

/** Menu ≡ sur une chaîne : la déplacer dans une autre famille (correction du classement automatique). */
@Composable
private fun MoveDialog(ch: CatalogCard, onPick: (com.aditv.data.live.Family) -> Unit, onClose: () -> Unit) {
    val first = remember { FocusRequester() }
    com.aditv.ui.common.InitialFocus(first)
    androidx.activity.compose.BackHandler { onClose() }
    Box(Modifier.fillMaxSize().background(Color(0xB0030508)), contentAlignment = Alignment.Center) {
        Column(Modifier.width(380.dp).background(Color(0xF0141A22), RoundedCornerShape(14.dp)).padding(22.dp)) {
            Text("Déplacer « ${com.aditv.data.live.ChannelFamilies.cleanName(ch.title)} »", fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Aditv.Text, maxLines = 2)
            Spacer(Modifier.height(10.dp))
            com.aditv.data.live.Family.entries.sortedBy { it.order }.forEachIndexed { i, f ->
                com.aditv.ui.design.SheetOption(f.label, { onPick(f) }, if (i == 0) Modifier.focusRequester(first) else Modifier)
            }
        }
    }
}

/**
 * Fond du Direct. Cube : vraie vidéo de la chaîne, muette, qualité limitée à 720p, lancée 0,7 s après que la sélection
 * s'arrête (une connexion de l'abonnement est utilisée). Stick et mode démo : logo de la chaîne en grand.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun LiveBackground(channel: CatalogCard, session: Session.Active) {
    val c = LocalContainer.current
    var player by remember { mutableStateOf<androidx.media3.exoplayer.ExoPlayer?>(null) }
    var request by remember { mutableStateOf<com.aditv.playback.PlaybackRequest?>(null) }
    var showing by remember { mutableStateOf(false) }
    var starts by remember { mutableStateOf(0) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_START) { starts++ }
    if (DeviceProfile.livePreview) LaunchedEffect(channel.id, starts) {
        showing = false
        delay(700)
        val item = c.db.catalog().observeItem(channel.id).first() ?: return@LaunchedEffect
        val url = runCatching { session.adapter.streamUrl(com.aditv.data.provider.StreamTarget.Live(item.providerId)) }.getOrNull() ?: return@LaunchedEffect
        val req = com.aditv.playback.PlaybackRequest(url, item.title, "APERÇU", true, null, null, "LIVE", null)
        val p = c.playback.open(req, com.aditv.playback.PlaybackController.TrackPrefs(listOf("fr"), false, 2, false))
        p.volume = 0f
        p.addListener(object : androidx.media3.common.Player.Listener {
            override fun onRenderedFirstFrame() { showing = true }
        })
        request = req; player = p
        com.aditv.core.CrashReporter.event("Aperçu vidéo du direct")
    }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { request?.let { c.playback.releaseIf(it) } } }
    // ADI TV en arrière-plan : l'aperçu s'arrête (pas de connexion ni de réseau utilisés pour rien).
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP) {
        request?.let { c.playback.releaseIf(it) }; player = null; request = null; showing = false
    }

    Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF232C37), Aditv.Bg))), contentAlignment = Alignment.CenterEnd) {
        // Logo en grand à droite (visible tant que la vidéo n'a pas démarré).
        Box(Modifier.fillMaxHeight(0.42f).fillMaxWidth(0.34f).padding(end = 70.dp), contentAlignment = Alignment.Center) {
            if (channel.imageUrl != null) RemoteImage(channel.imageUrl, channel.title, Modifier.fillMaxSize(), ContentScale.Fit, fallbackTitle = false)
            else Text(com.aditv.data.live.ChannelFamilies.cleanName(channel.title), fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 34.sp, lineHeight = 40.sp, color = Aditv.Text.copy(alpha = 0.85f), maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        val p = player
        if (p != null) androidx.compose.ui.viewinterop.AndroidView(
            factory = { ctx -> androidx.media3.ui.PlayerView(ctx).apply { useController = false; resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM; setShutterBackgroundColor(android.graphics.Color.TRANSPARENT) } },
            update = { it.player = p },
            modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (showing) 1f else 0f },
        )
    }
}

@Composable
private fun ChannelRow(r: LiveRow, favorite: Boolean, onFocus: () -> Unit, onClick: () -> Unit, onMenu: () -> Unit, modifier: Modifier) {
    val shape = RoundedCornerShape(10.dp)
    val now = System.currentTimeMillis()
    Surface(
        onClick = onClick,
        modifier = (modifier.fillMaxWidth().height(56.dp).onFocusChanged { if (it.isFocused) onFocus() }
            .onPreviewKeyEvent { ev -> if (ev.type == androidx.compose.ui.input.key.KeyEventType.KeyDown && ev.key == androidx.compose.ui.input.key.Key.Menu) { onMenu(); true } else false }).navSound(),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.05f), contentColor = Aditv.Text, focusedContainerColor = Color.White, focusedContentColor = Aditv.OnLight),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(1.5.dp, Aditv.Accent), shape = shape, inset = (-2).dp)),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            // Logo en grand sur une plaque sobre.
            Box(Modifier.width(76.dp).height(42.dp).clip(RoundedCornerShape(7.dp)).background(Brush.verticalGradient(listOf(Color(0xFF2B323C), Color(0xFF191E25)))).padding(horizontal = 7.dp, vertical = 5.dp), contentAlignment = Alignment.Center) {
                if (r.channel.imageUrl != null) RemoteImage(r.channel.imageUrl, r.channel.title, Modifier.fillMaxSize(), ContentScale.Fit, fallbackTitle = false)
                else Text(com.aditv.data.live.ChannelFamilies.cleanName(r.channel.title), fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, lineHeight = 12.sp, color = Aditv.Text, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(com.aditv.data.live.ChannelFamilies.cleanName(r.channel.title), style = MaterialTheme.typography.titleMedium.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(r.now?.title ?: "Programme non communiqué", style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.graphicsLayer { alpha = 0.75f })
                progressOf(r.now, now)?.let { f ->
                    Box(Modifier.padding(top = 4.dp).fillMaxWidth(0.8f).height(2.dp).background(Color.Gray.copy(alpha = 0.35f), RoundedCornerShape(1.dp))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(Aditv.Accent, RoundedCornerShape(1.dp)))
                    }
                }
            }
            if (favorite) Icon(painterResource(R.drawable.ic_star), contentDescription = "Favori", tint = Aditv.Accent, modifier = Modifier.size(12.dp))
        }
    }
}

@Composable
private fun ProgrammeInfo(r: LiveRow, favorite: Boolean, onWatch: () -> Unit, onGuide: () -> Unit, onFavorite: () -> Unit) {
    val now = System.currentTimeMillis()
    val p = r.now
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (p != null) { LiveTag(); Spacer(Modifier.width(10.dp)) }
        Text(r.channel.title.uppercase(), style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp), color = Aditv.TextSecondary, maxLines = 1)
    }
    Spacer(Modifier.height(6.dp))
    Text(p?.title ?: r.channel.title, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 32.sp, color = Aditv.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
    if (p != null) {
        Spacer(Modifier.height(6.dp))
        Text(timeFmt.format(Date(p.startUtc)) + " – " + timeFmt.format(Date(p.endUtc)) + (p.category?.let { "  ·  $it" } ?: ""), style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary)
        progressOf(p, now)?.let { f ->
            Box(Modifier.padding(top = 8.dp).fillMaxWidth(0.7f).height(3.dp).background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(2.dp))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(f).background(Aditv.Accent, RoundedCornerShape(2.dp)))
            }
        }
        p.description?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = Aditv.Text.copy(alpha = 0.85f), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp)) }
        r.next?.let { n ->
            Text("À suivre · " + timeFmt.format(Date(n.startUtc)) + "  " + n.title, style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        }
    } else {
        Spacer(Modifier.height(4.dp))
        Text("Programme non communiqué par le guide.", style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary)
    }
    Spacer(Modifier.height(14.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        PillButton("Regarder", onWatch)
        PillButton("Guide TV", onGuide, icon = R.drawable.ic_guide, primary = false)
        com.aditv.ui.design.RoundButton(if (favorite) R.drawable.ic_star else R.drawable.ic_star_outline, if (favorite) "Favori" else "Ajouter aux favoris", onFavorite, active = favorite)
    }
}
