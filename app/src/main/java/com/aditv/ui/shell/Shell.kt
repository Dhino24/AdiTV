package com.aditv.ui.shell

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.aditv.R
import com.aditv.data.Session
import com.aditv.data.provider.MediaType
import com.aditv.ui.browse.BrowseScreen
import com.aditv.ui.common.NotYetAvailable
import com.aditv.ui.design.Glass
import com.aditv.ui.home.HomeScreen
import com.aditv.ui.search.SearchScreen
import com.aditv.ui.settings.SettingsScreen
import com.aditv.ui.theme.Aditv
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class Dest(val route: String, val label: String, val icon: Int)

private val destinations = listOf(
    Dest("search", "Rechercher", R.drawable.ic_search),
    Dest("home", "Accueil", R.drawable.ic_home),
    Dest("live", "Direct", R.drawable.ic_live),
    Dest("guide", "Guide TV", R.drawable.ic_guide),
    Dest("movies", "Films", R.drawable.ic_movies),
    Dest("series", "Séries", R.drawable.ic_series),
    Dest("mylist", "Ma liste", R.drawable.ic_list),
    Dest("settings", "Réglages", R.drawable.ic_settings),
)

/**
 * Coque de navigation. Le menu latéral est un panneau flottant en verre fumé, masqué par défaut :
 * le contenu occupe tout l'écran. Il s'ouvre avec ← au bord gauche du contenu, ou Retour sur un écran
 * principal ; il retient la rubrique active ; → ou Retour le referme et rend le focus là où il était.
 */
@Composable
fun Shell(session: Session.Active) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val currentRoute = entry?.destination?.route
    var selected by remember { mutableStateOf("home") }
    if (currentRoute != null && destinations.any { it.route == currentRoute }) selected = currentRoute
    val requesters = remember { destinations.associate { it.route to FocusRequester() } }
    val contentRequester = remember { FocusRequester() }
    val entries = remember { destinations.associate { it.route to FocusRequester() } }
    var menuOpen by remember { mutableStateOf(false) }
    var menuHasFocus by remember { mutableStateOf(false) }
    var contentFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val accountId = session.account.id
    val scope = rememberCoroutineScope()
    val topLevel = currentRoute != null && destinations.any { it.route == currentRoute }
    val fullScreen = currentRoute?.startsWith("play/") == true

    fun closeMenu() { menuOpen = false; runCatching { contentRequester.requestFocus() } }
    fun openMenu() { if (!fullScreen) menuOpen = true }

    LaunchedEffect(menuOpen) {
        if (menuOpen) repeat(30) { withFrameNanos { }; if (runCatching { requesters.getValue(selected).requestFocus() }.getOrDefault(false)) return@LaunchedEffect }
    }

    fun go(route: String) {
        menuOpen = false
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
                if (!menuHasFocus && stable > 0) { if (++stable >= 12) return@launch }
                else if (runCatching { entries.getValue(route).requestFocus() }.getOrDefault(false)) stable = 1
            }
        }
    }

    // Écran principal : Retour ouvre le menu (puis, dans le menu, double Retour pour quitter).
    BackHandler(enabled = topLevel && !menuOpen && !fullScreen) { openMenu() }
    ExitOnDoubleBack(enabled = menuOpen)
    BackHandler(enabled = false) {}

    val openItem: (String) -> Unit = { nav.navigate("item/" + Uri.encode(it)) }
    val play: (String, Boolean) -> Unit = { id, fromStart -> nav.navigate("play/" + Uri.encode(id) + "?start=" + fromStart) }
    // Direct : la liste d'origine (favoris, catégorie, toutes) sert au zapping ↑/↓ dans le lecteur.
    val playLive: (String, String?) -> Unit = { id, list -> nav.navigate("play/" + Uri.encode(id) + "?start=false&list=" + Uri.encode(list ?: "all")) }

    Box(Modifier.fillMaxSize().background(Aditv.Bg)) {
        Box(
            Modifier
                .fillMaxSize()
                .focusRequester(contentRequester)
                .focusRestorer()
                .onFocusChanged { contentFocused = it.hasFocus },
        ) {
            // Bord gauche invisible : ← depuis le premier élément d'une rangée y arrive et ouvre le menu.
            if (!fullScreen && !menuOpen) Box(
                Modifier.align(Alignment.CenterStart).fillMaxHeight().width(2.dp)
                    .onFocusChanged { if (it.isFocused) openMenu() }
                    // Jamais cible du focus initial : seulement atteinte par ← depuis le contenu.
                    .focusProperties { canFocus = contentFocused }
                    .focusable(),
            )
            // Transitions courtes (cible 120–300 ms) : opacité seulement, pas de recalcul de mise en page.
            NavHost(
                nav, startDestination = "home",
                enterTransition = { fadeIn(tween(180)) }, exitTransition = { fadeOut(tween(120)) },
                popEnterTransition = { fadeIn(tween(180)) }, popExitTransition = { fadeOut(tween(120)) },
            ) {
                composable("home") { Entry(entries, "home") { HomeScreen(accountId, session.isDemo, onOpen = openItem, onPlay = play, onPlayLive = playLive) } }
                composable("search") { Entry(entries, "search") { SearchScreen(accountId, onOpen = openItem, onPlayLive = { playLive(it, "all") }) } }
                composable("live") { Entry(entries, "live") { BrowseScreen(accountId, MediaType.LIVE, onOpen = openItem, onPlayLive = { id, cat -> playLive(id, when (cat) { com.aditv.ui.browse.BrowseViewModel.FAVORITES -> "fav"; com.aditv.ui.browse.BrowseViewModel.ALL, null -> "all"; else -> "cat:$cat" }) }) } }
                composable("movies") { Entry(entries, "movies") { BrowseScreen(accountId, MediaType.MOVIE, onOpen = openItem, onPlayLive = { id, cat -> playLive(id, when (cat) { com.aditv.ui.browse.BrowseViewModel.FAVORITES -> "fav"; com.aditv.ui.browse.BrowseViewModel.ALL, null -> "all"; else -> "cat:$cat" }) }) } }
                composable("series") { Entry(entries, "series") { BrowseScreen(accountId, MediaType.SERIES, onOpen = openItem, onPlayLive = { id, cat -> playLive(id, when (cat) { com.aditv.ui.browse.BrowseViewModel.FAVORITES -> "fav"; com.aditv.ui.browse.BrowseViewModel.ALL, null -> "all"; else -> "cat:$cat" }) }) } }
                composable("guide") { Entry(entries, "guide") { com.aditv.ui.guide.GuideScreen(session, onPlayLive = playLive, onPlay = play, onOpen = openItem) } }
                composable("epgmap/{id}") { e -> com.aditv.ui.guide.EpgMapScreen(Uri.decode(e.arguments?.getString("id").orEmpty()), onDone = { nav.popBackStack() }) }
                composable("mylist") { Entry(entries, "mylist") { com.aditv.ui.library.MyListScreen(accountId, onOpen = openItem) } }
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

        // Voile + panneau du menu
        AnimatedVisibility(menuOpen, enter = fadeIn(tween(160)), exit = fadeOut(tween(120))) {
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Black.copy(alpha = 0.15f)))))
        }
        AnimatedVisibility(
            menuOpen,
            enter = fadeIn(tween(160)) + slideInHorizontally(tween(180)) { -it / 4 },
            exit = fadeOut(tween(120)) + slideOutHorizontally(tween(120)) { -it / 4 },
        ) {
            SideMenu(
                isDemo = session.isDemo, selected = selected, requesters = requesters,
                onPick = { go(it) }, onClose = { closeMenu() },
                modifier = Modifier.onFocusChanged { menuHasFocus = it.hasFocus },
            )
        }
    }
}

@Composable
private fun SideMenu(
    isDemo: Boolean,
    selected: String,
    requesters: Map<String, FocusRequester>,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier,
) {
    val clock by produceState(SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date())) {
        while (true) { delay(15_000); value = SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date()) }
    }
    Column(
        modifier
            .padding(start = 20.dp, top = 20.dp)
            .width(250.dp)
            .background(Glass.Panel, RoundedCornerShape(28.dp))
            .border(BorderStroke(1.dp, Glass.Border), RoundedCornerShape(28.dp))
            .padding(horizontal = 12.dp, vertical = 16.dp)
            .onPreviewKeyEvent { ev ->
                if (ev.type == KeyEventType.KeyDown && ev.key == Key.DirectionRight) { onClose(); true } else false
            }
            // Le menu retient sa sélection ; le focus n'en sort que par → ou Retour.
            .focusProperties {
                onEnter = { requesters[selected]?.requestFocus() }
                onExit = { cancelFocusChange() }
            }
            .focusGroup(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(Modifier.padding(start = 8.dp, end = 8.dp, bottom = 10.dp).height(36.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.logo_a), contentDescription = "ADITV", modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(10.dp))
            Text(if (isDemo) "ADITV · Démo" else "ADITV", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = Aditv.Text)
            Spacer(Modifier.weight(1f))
            Text(clock, style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary)
        }
        destinations.forEach { d ->
            val isSel = d.route == selected
            Surface(
                onClick = { onPick(d.route) },
                modifier = Modifier.height(44.dp).focusRequester(requesters.getValue(d.route)),
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(22.dp)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = if (isSel) Color.White.copy(alpha = 0.14f) else Color.Transparent,
                    contentColor = Aditv.Text,
                    focusedContainerColor = Color.White, focusedContentColor = Aditv.OnLight,
                ),
            ) {
                Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(d.icon), contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(d.label, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun Entry(entries: Map<String, FocusRequester>, route: String, content: @Composable () -> Unit) =
    CompositionLocalProvider(com.aditv.ui.LocalScreenEntry provides entries.getValue(route), content = content)

/** À la racine, Retour ne ferme l'application qu'après un second appui (2 s). */
@Composable
private fun ExitOnDoubleBack(enabled: Boolean) {
    val activity = LocalActivity.current
    var armed by remember { mutableStateOf(false) }
    BackHandler(enabled) { if (armed) activity?.finish() else armed = true }
    LaunchedEffect(armed) { if (armed) { delay(2_000); armed = false } }
    if (armed && enabled) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Text(
                "Appuyez de nouveau sur Retour pour quitter ADITV",
                style = MaterialTheme.typography.bodyLarge, color = Aditv.Text,
                modifier = Modifier.padding(bottom = 32.dp).background(Aditv.Surface2, RoundedCornerShape(12.dp)).padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}
