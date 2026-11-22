package com.aditv.ui.shell

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.aditv.R
import com.aditv.data.Session
import com.aditv.data.provider.MediaType
import com.aditv.ui.LocalContainer
import com.aditv.ui.browse.BrowseScreen
import com.aditv.ui.design.PillButton
import com.aditv.ui.home.HomeScreen
import com.aditv.ui.search.SearchScreen
import com.aditv.ui.settings.SettingsScreen
import com.aditv.ui.theme.Aditv
import com.aditv.ui.theme.Outfit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class Tab(val route: String, val label: String)

private val tabs = listOf(
    Tab("home", "Accueil"),
    Tab("live", "Direct"),
    Tab("movies", "Films"),
    Tab("series", "Séries"),
    Tab("mylist", "Ma liste"),
)
private val topRoutes = tabs.map { it.route } + listOf("search", "settings")

/** Barre de navigation : les écrans qui défilent la masquent ; elle réapparaît dès qu'elle reçoit le focus. */
@Stable
class NavChrome { var scrolled by mutableStateOf(false) }
val LocalNavChrome = staticCompositionLocalOf { NavChrome() }

/**
 * Coque de navigation (design validé du prototype) : barre haute compacte — logo, rubriques en pilules,
 * heure, recherche, réglages. ↑ depuis le haut d'un écran y monte sur la rubrique active ; ↓ redescend sur
 * l'élément quitté. Retour : contenu → barre → Accueil → « Quitter ADITV ? ».
 */
@Composable
fun Shell(session: Session.Active) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route
    androidx.compose.runtime.LaunchedEffect(currentRoute) { currentRoute?.let { com.aditv.core.CrashReporter.event("Écran : " + it.substringBefore('/')) } }
    var selected by remember { mutableStateOf("home") }
    if (currentRoute != null && currentRoute in topRoutes) selected = currentRoute
    val requesters = remember { topRoutes.associateWith { FocusRequester() } }
    val entries = remember { topRoutes.associateWith { FocusRequester() } }
    var navFocused by remember { mutableStateOf(false) }
    var exitAsked by remember { mutableStateOf(false) }
    val chrome = remember { NavChrome() }
    val contentRequester = remember { FocusRequester() }
    val accountId = session.account.id
    val scope = rememberCoroutineScope()
    val topLevel = currentRoute != null && currentRoute in topRoutes
    val activity = LocalActivity.current

    fun go(route: String) {
        chrome.scrolled = false
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        // Le focus entre dans l'élément principal du nouvel écran, une fois composé.
        scope.launch {
            var stable = 0
            repeat(120) {
                withFrameNanos { }
                if (!navFocused && stable > 0) { if (++stable >= 12) return@launch }
                else if (runCatching { entries.getValue(route).requestFocus() }.getOrDefault(false)) stable = 1
            }
        }
    }


    val openItem: (String) -> Unit = { nav.navigate("item/" + Uri.encode(it)) }
    val play: (String, Boolean) -> Unit = { id, fromStart -> nav.navigate("play/" + Uri.encode(id) + "?start=" + fromStart) }
    // Direct : la liste d'origine (favoris, catégorie, toutes) sert au zapping ↑/↓ dans le lecteur.
    val playLive: (String, String?) -> Unit = { id, list -> nav.navigate("play/" + Uri.encode(id) + "?start=false&list=" + Uri.encode(list ?: "all")) }

    // Rappel déclenché pendant l'utilisation : bandeau 60 s ; Menu ≡ ouvre la chaîne (même depuis le lecteur).
    val reminder by com.aditv.data.sport.ReminderBus.current.collectAsStateWithLifecycle()
    LaunchedEffect(reminder) { if (reminder != null) { delay(60_000); com.aditv.data.sport.ReminderBus.clear() } }
    // Notification de rappel choisie alors qu'ADITV était fermée : lecture de la chaîne.
    val pendingChannel by com.aditv.data.sport.PendingLaunch.channel.collectAsStateWithLifecycle()
    LaunchedEffect(pendingChannel) { pendingChannel?.let { com.aditv.data.sport.PendingLaunch.channel.value = null; playLive(it, "sport") } }

    CompositionLocalProvider(LocalNavChrome provides chrome) {
        Box(Modifier.fillMaxSize().background(Aditv.Bg).onPreviewKeyEvent { ev ->
            val r = reminder
            if (r != null && ev.type == KeyEventType.KeyDown && ev.key == Key.Menu) {
                com.aditv.data.sport.ReminderBus.clear(); playLive(r.channelItemId, "sport"); true
            } else false
        }) {
            Box(Modifier.fillMaxSize().focusRequester(contentRequester).focusRestorer()) {
                // Transitions courtes : opacité seulement, pas de recalcul de mise en page.
                NavHost(
                    nav, startDestination = "home",
                    enterTransition = { fadeIn(tween(180)) }, exitTransition = { fadeOut(tween(120)) },
                    popEnterTransition = { fadeIn(tween(180)) }, popExitTransition = { fadeOut(tween(120)) },
                ) {
                    composable("home") { Entry(entries, "home") { HomeScreen(accountId, session.isDemo, onOpen = openItem, onPlay = play, onPlayLive = playLive) } }
                    composable("search") { Entry(entries, "search") { SearchScreen(accountId, onOpen = openItem, onPlayLive = { playLive(it, "all") }) } }
                    composable("live") { Entry(entries, "live") { com.aditv.ui.guide.GuideScreen(session, onPlayLive = playLive, onPlay = play, onOpen = openItem) } }
                    composable("movies") { Entry(entries, "movies") { BrowseScreen(accountId, MediaType.MOVIE, onOpen = openItem, onPlayLive = playLive) } }
                    composable("series") { Entry(entries, "series") { BrowseScreen(accountId, MediaType.SERIES, onOpen = openItem, onPlayLive = playLive) } }
                    composable("channels") { BrowseScreen(accountId, MediaType.LIVE, onOpen = openItem, onPlayLive = playLive) }
                    composable("epgmap/{id}") { e -> com.aditv.ui.guide.EpgMapScreen(Uri.decode(e.arguments?.getString("id").orEmpty()), onDone = { nav.popBackStack() }) }
                    composable("mylist") { Entry(entries, "mylist") { com.aditv.ui.library.MyListScreen(accountId, onOpen = openItem, onPlayLive = playLive, onBrowse = { go(it) }) } }
                    composable("settings") { Entry(entries, "settings") { SettingsScreen(session) } }
                    composable("item/{id}") { e ->
                        com.aditv.ui.detail.DetailScreen(
                            session, Uri.decode(e.arguments?.getString("id").orEmpty()),
                            onPlay = { pid, fs -> if (pid.contains("|LIVE|")) playLive(pid, "all") else play(pid, fs) },
                            onOpen = openItem, onCorrect = { nav.navigate("match/" + Uri.encode(it)) },
                            onMapGuide = { nav.navigate("epgmap/" + Uri.encode(it)) },
                        )
                    }
                    composable("match/{id}") { e -> com.aditv.ui.match.MatchScreen(Uri.decode(e.arguments?.getString("id").orEmpty()), onDone = { nav.popBackStack() }) }
                    composable(
                        "play/{id}?start={start}&list={list}",
                        arguments = listOf(
                            androidx.navigation.navArgument("start") { type = androidx.navigation.NavType.BoolType; defaultValue = false },
                            androidx.navigation.navArgument("list") { type = androidx.navigation.NavType.StringType; nullable = true; defaultValue = null },
                        ),
                    ) { e ->
                        com.aditv.ui.player.PlayerScreen(
                            session, Uri.decode(e.arguments?.getString("id").orEmpty()), e.arguments?.getBoolean("start") ?: false,
                            liveList = e.arguments?.getString("list")?.let(Uri::decode),
                            onExit = { nav.popBackStack() },
                        )
                    }
                }
            }

            if (topLevel) TopNav(
                isDemo = session.isDemo, selected = selected, requesters = requesters,
                hidden = chrome.scrolled && !navFocused,
                onPick = { go(it) },
                // ↓ depuis la barre : retour exact sur l'élément du contenu quitté.
                onDown = { runCatching { contentRequester.requestFocus() }.getOrDefault(false) },
                modifier = Modifier.onFocusChanged { navFocused = it.hasFocus },
            )

            // Retour sur un écran principal : contenu → barre (rubrique active) → Accueil → confirmation de sortie.
            // Déclaré après le NavHost pour passer avant son propre retour arrière.
            BackHandler(enabled = topLevel && !exitAsked) {
                when {
                    !navFocused -> runCatching { requesters.getValue(selected).requestFocus() }
                    selected != "home" -> go("home")
                    else -> exitAsked = true
                }
            }

            reminder?.let { r ->
                com.aditv.ui.sport.ReminderBanner(r.title, r.channelName, r.startUtc, Modifier.align(Alignment.TopEnd).padding(top = 62.dp, end = 24.dp))
            }

            if (exitAsked) ExitDialog(onStay = { exitAsked = false; runCatching { requesters.getValue(selected).requestFocus() } }, onQuit = { activity?.finish() })
        }
    }
}

@Composable
private fun TopNav(
    isDemo: Boolean,
    selected: String,
    requesters: Map<String, FocusRequester>,
    hidden: Boolean,
    onPick: (String) -> Unit,
    onDown: () -> Boolean,
    modifier: Modifier,
) {
    val c = LocalContainer.current
    val alpha by animateFloatAsState(if (hidden) 0f else 1f, tween(220), label = "nav")
    val clock by produceState(SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date())) {
        while (true) { delay(15_000); value = SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date()) }
    }
    val running by c.sync.running.collectAsStateWithLifecycle()
    Box(
        modifier.fillMaxWidth().height(60.dp)
            .graphicsLayer { this.alpha = alpha; translationY = -12.dp.toPx() * (1f - alpha) }
            .background(Brush.verticalGradient(listOf(Aditv.Bg.copy(alpha = 0.78f), Color.Transparent)))
            // La barre retient sa rubrique : y entrer ramène sur la rubrique active.
            .focusProperties { onEnter = { requesters[selected]?.requestFocus() } }
            .onPreviewKeyEvent { ev -> ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionDown && onDown() }
            .focusGroup()
            .padding(horizontal = 48.dp),
    ) {
        Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.logo_full), contentDescription = "ADI TV", modifier = Modifier.height(20.dp).aspectRatio(com.aditv.ui.intro.LOGO_RATIO))
            if (isDemo) { Spacer(Modifier.width(8.dp)); Text("DÉMO", fontSize = 7.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Aditv.OnLight, modifier = Modifier.background(Aditv.Accent, RoundedCornerShape(50)).padding(horizontal = 5.dp, vertical = 1.5.dp)) }
        }
        Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEach { t -> NavPill(t.label, t.route == selected, { onPick(t.route) }, Modifier.focusRequester(requesters.getValue(t.route))) }
        }
        Row(Modifier.align(Alignment.CenterEnd), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            running.values.firstOrNull()?.let { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(5.dp).background(Aditv.Accent, CircleShape))
                    Spacer(Modifier.width(5.dp))
                    Text("Mise à jour · ${com.aditv.ui.home.typeLabel(r.type.name)} ${r.count}", style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary, maxLines = 1)
                }
            }
            Text(clock, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium), color = Aditv.TextSecondary)
            NavIcon(R.drawable.ic_search, "Rechercher", selected == "search", { onPick("search") }, Modifier.focusRequester(requesters.getValue("search")))
            NavIcon(R.drawable.ic_settings, "Réglages", selected == "settings", { onPick("settings") }, Modifier.focusRequester(requesters.getValue("settings")))
        }
    }
}

private val NavHalo = Glow(elevationColor = Aditv.Accent.copy(alpha = 0.25f), elevation = 8.dp)

@Composable
private fun NavPill(label: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(26.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.08f),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = NavHalo),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) Color.White.copy(alpha = 0.14f) else Color.Transparent,
            contentColor = if (isSelected) Aditv.Text else Aditv.TextSecondary,
            focusedContainerColor = Color.White, focusedContentColor = Aditv.OnLight,
        ),
    ) {
        Box(Modifier.fillMaxHeight().padding(horizontal = 13.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium, fontSize = 11.sp), maxLines = 1)
        }
    }
}

@Composable
private fun NavIcon(icon: Int, label: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier.size(27.dp),
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = NavHalo),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) Color.White.copy(alpha = 0.14f) else Color.Transparent,
            contentColor = Aditv.Text, focusedContainerColor = Color.White, focusedContentColor = Aditv.OnLight,
        ),
    ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(painterResource(icon), contentDescription = label, modifier = Modifier.size(15.dp)) } }
}

/** « Quitter ADITV ? » : Rester (par défaut) ou Quitter. Retour ferme la question. */
@Composable
private fun ExitDialog(onStay: () -> Unit, onQuit: () -> Unit) {
    val stay = remember { FocusRequester() }
    com.aditv.ui.common.InitialFocus(stay)
    BackHandler { onStay() }
    Box(Modifier.fillMaxSize().background(Color(0x9E030508)), contentAlignment = Alignment.Center) {
        Column(
            Modifier.widthIn(max = 410.dp).background(Color(0xEB10151C), RoundedCornerShape(14.dp)).padding(horizontal = 30.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(42.dp).background(Aditv.Accent.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_power), contentDescription = null, tint = Aditv.Accent, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("Quitter ADI TV ?", fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, color = Aditv.Text)
            Spacer(Modifier.height(7.dp))
            Text("Vous retrouverez votre position dans chaque rubrique au prochain lancement.", style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary)
            Spacer(Modifier.height(19.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                PillButton("Rester", onStay, Modifier.focusRequester(stay), icon = null)
                PillButton("Quitter", onQuit, icon = R.drawable.ic_power, primary = false)
            }
        }
    }
}

@Composable
private fun Entry(entries: Map<String, FocusRequester>, route: String, content: @Composable () -> Unit) =
    CompositionLocalProvider(com.aditv.ui.LocalScreenEntry provides entries.getValue(route), content = content)

