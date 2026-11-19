package com.aditv.ui.shell

import androidx.activity.compose.LocalActivity
import android.net.Uri
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.ModalNavigationDrawer
import androidx.tv.material3.NavigationDrawerItem
import androidx.tv.material3.NavigationDrawerItemDefaults
import androidx.tv.material3.Text
import com.aditv.R
import com.aditv.data.Session
import com.aditv.data.provider.MediaType
import com.aditv.ui.browse.BrowseScreen
import com.aditv.ui.common.NotYetAvailable
import com.aditv.ui.detail.ItemScreen
import com.aditv.ui.home.HomeScreen
import com.aditv.ui.search.SearchScreen
import com.aditv.ui.settings.SettingsScreen
import com.aditv.ui.theme.Aditv
import kotlinx.coroutines.delay

private data class Dest(val route: String, val label: String, val icon: Int)

private val destinations = listOf(
    Dest("home", "Accueil", R.drawable.ic_home),
    Dest("search", "Recherche", R.drawable.ic_search),
    Dest("live", "Direct", R.drawable.ic_live),
    Dest("guide", "Guide TV", R.drawable.ic_guide),
    Dest("movies", "Films", R.drawable.ic_movies),
    Dest("series", "Séries", R.drawable.ic_series),
    Dest("mylist", "Ma liste", R.drawable.ic_list),
    Dest("settings", "Paramètres", R.drawable.ic_settings),
)

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
    val accountId = session.account.id

    val scope = rememberCoroutineScope()
    fun go(route: String) {
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
        // OK sur une rubrique : le focus entre dans le contenu une fois l'écran composé.
        scope.launch {
            repeat(30) {
                withFrameNanos { }
                if (runCatching { entries.getValue(route).requestFocus() }.getOrDefault(false)) return@launch
            }
        }
    }

    ExitOnDoubleBack(enabled = currentRoute == "home")

    ModalNavigationDrawer(
        scrimBrush = Brush.horizontalGradient(listOf(Aditv.Bg.copy(alpha = 0.92f), Color.Transparent)),
        drawerContent = { value ->
            Column(
                Modifier
                    .fillMaxHeight()
                    .background(Brush.horizontalGradient(listOf(Aditv.Bg, Aditv.Bg.copy(alpha = 0.6f))))
                    .padding(horizontal = 12.dp, vertical = 20.dp)
                    // Le menu retient sa sélection : y entrer ramène sur la rubrique active.
                    .focusProperties { onEnter = { requesters[selected]?.requestFocus() } }
                    .focusGroup(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(Modifier.height(48.dp).padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.logo_a), contentDescription = "ADITV", modifier = Modifier.size(32.dp))
                    if (value == DrawerValue.Open) {
                        Spacer(Modifier.width(12.dp))
                        Text(if (session.isDemo) "ADITV · Démo" else "ADITV", style = MaterialTheme.typography.titleLarge, color = Aditv.Text)
                    }
                }
                Spacer(Modifier.height(16.dp))
                destinations.forEachIndexed { i, d ->
                    if (d.route == "settings") Spacer(Modifier.weight(1f))
                    NavigationDrawerItem(
                        selected = selected == d.route,
                        onClick = { go(d.route) },
                        leadingContent = { Icon(painterResource(d.icon), contentDescription = null) },
                        // → quitte le menu vers l'élément de contenu précédemment actif (le menu ouvert masque la 1re colonne).
                        modifier = Modifier
                            .focusRequester(requesters.getValue(d.route))
                            .onPreviewKeyEvent {
                                if (it.type == KeyEventType.KeyDown && it.key == Key.DirectionRight) contentRequester.requestFocus() else false
                            },
                        shape = NavigationDrawerItemDefaults.shape(RoundedCornerShape(12.dp)),
                        colors = NavigationDrawerItemDefaults.colors(
                            focusedContainerColor = Aditv.Text, focusedContentColor = Aditv.OnLight,
                            selectedContainerColor = Aditv.Surface2, selectedContentColor = Aditv.Text,
                            contentColor = Aditv.TextSecondary, inactiveContentColor = Aditv.TextSecondary,
                            focusedSelectedContainerColor = Aditv.Text, focusedSelectedContentColor = Aditv.OnLight,
                        ),
                    ) { Text(d.label) }
                }
            }
        },
    ) {
        Box(Modifier.fillMaxSize().background(Aditv.Bg).padding(start = 72.dp).focusRequester(contentRequester).focusRestorer()) {
            // Transitions courtes (cible 120–300 ms) : opacité seulement, pas de recalcul de mise en page.
            NavHost(
                nav, startDestination = "home",
                enterTransition = { fadeIn(tween(180)) }, exitTransition = { fadeOut(tween(120)) },
                popEnterTransition = { fadeIn(tween(180)) }, popExitTransition = { fadeOut(tween(120)) },
            ) {
                composable("home") { Entry(entries, "home") { HomeScreen(accountId, session.isDemo, onOpen = { nav.navigate("item/" + Uri.encode(it)) }) } }
                composable("search") { Entry(entries, "search") { SearchScreen(accountId, onOpen = { nav.navigate("item/" + Uri.encode(it)) }) } }
                composable("live") { Entry(entries, "live") { BrowseScreen(accountId, MediaType.LIVE, onOpen = { nav.navigate("item/" + Uri.encode(it)) }) } }
                composable("movies") { Entry(entries, "movies") { BrowseScreen(accountId, MediaType.MOVIE, onOpen = { nav.navigate("item/" + Uri.encode(it)) }) } }
                composable("series") { Entry(entries, "series") { BrowseScreen(accountId, MediaType.SERIES, onOpen = { nav.navigate("item/" + Uri.encode(it)) }) } }
                composable("guide") {
                    NotYetAvailable("Guide TV", "phase 3", "Grille EPG alimentée par l'API du fournisseur ou vos sources XMLTV, couverture affichée chaîne par chaîne. Aucun programme ne sera inventé.")
                }
                composable("mylist") {
                    NotYetAvailable("Ma liste", "phase 2", "Films et séries suivis, séparés des chaînes favorites et de l'historique de lecture.")
                }
                composable("settings") { Entry(entries, "settings") { SettingsScreen(session) } }
                composable("item/{id}") { e -> ItemScreen(Uri.decode(e.arguments?.getString("id").orEmpty())) }
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
