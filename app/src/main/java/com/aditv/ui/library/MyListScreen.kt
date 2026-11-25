package com.aditv.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.aditv.R
import com.aditv.data.variants.collapsed
import com.aditv.ui.LocalContainer
import com.aditv.ui.LocalScreenEntry
import com.aditv.ui.common.InitialFocus
import com.aditv.ui.common.displayImage
import com.aditv.ui.common.rememberNowPlaying
import com.aditv.ui.common.tmdbImage
import com.aditv.ui.design.ChannelTile
import com.aditv.ui.design.FilterChip
import com.aditv.ui.design.PillButton
import com.aditv.ui.design.PosterTile
import com.aditv.ui.design.RowTitle
import com.aditv.ui.design.StateBox
import com.aditv.ui.design.WideCard
import com.aditv.ui.shell.LocalNavChrome
import com.aditv.ui.theme.Aditv

/** Films et séries suivis, et chaînes favorites. */
@Composable
fun MyListScreen(accountId: String, onOpen: (String) -> Unit, onPlayLive: (String, String?) -> Unit, onBrowse: (String) -> Unit) {
    val c = LocalContainer.current
    val list by remember(accountId) { c.db.library().watchlist(com.aditv.data.profile.Profiles.key(accountId)) }.collectAsStateWithLifecycle(null)
    val favRaw by remember(accountId) { c.db.live().favorites(com.aditv.data.profile.Profiles.key(accountId)) }.collectAsStateWithLifecycle(null)
    val prefs by c.prefs.variantPrefs.collectAsStateWithLifecycle(com.aditv.data.variants.VariantPrefs())
    val favorites = remember(favRaw, prefs) { favRaw?.collapsed(prefs)?.map { it.first } }
    var filter by rememberSaveable { mutableStateOf("all") }
    val firstChip = remember { FocusRequester() }
    val entry = LocalScreenEntry.current
    val grid = rememberLazyGridState()
    val chrome = LocalNavChrome.current
    val scrolled by remember { derivedStateOf { grid.firstVisibleItemIndex > 0 } }
    LaunchedEffect(scrolled) { chrome.scrolled = scrolled }
    InitialFocus(firstChip)

    val titles = list.orEmpty().filter { filter == "all" || it.type == filter }
    val channels = favorites.orEmpty()
    val nowPlaying = rememberNowPlaying(accountId, channels)
    val wide = titles.isNotEmpty() && titles.count { it.tmdbBackdrop != null } * 2 >= titles.size
    val showTitles = filter != "ch"
    val showChannels = (filter == "all" || filter == "ch") && channels.isNotEmpty()
    val loading = list == null || favorites == null

    LazyVerticalGrid(
        state = grid, columns = GridCells.Fixed(if (wide) 4 else 6), modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 84.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "head", span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text("Ma liste", style = MaterialTheme.typography.displaySmall, color = Aditv.Text)
                Spacer(Modifier.height(6.dp))
                Text("${list.orEmpty().size} titre${if (list.orEmpty().size > 1) "s" else ""} · ${channels.size} chaîne${if (channels.size > 1) "s" else ""} favorite${if (channels.size > 1) "s" else ""}",
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 10.sp), color = Aditv.TextTertiary)
                Spacer(Modifier.height(14.dp))
                LazyRow(Modifier.focusRestorer(firstChip), horizontalArrangement = Arrangement.spacedBy(5.dp), contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp)) {
                    items(listOf("all" to "Tout", "MOVIE" to "Films", "SERIES" to "Séries", "ch" to "Chaînes favorites"), key = { it.first }) { (k, l) ->
                        FilterChip(l, filter == k, { filter = k }, if (k == "all") Modifier.focusRequester(firstChip).focusRequester(entry) else Modifier)
                    }
                }
            }
        }
        if (!loading && (!showTitles || titles.isEmpty()) && !showChannels) item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
            Box(Modifier.fillMaxWidth().padding(top = 36.dp), contentAlignment = Alignment.TopCenter) {
                StateBox(
                    R.drawable.ic_bookmark,
                    if (filter == "ch") "Aucune chaîne favorite" else "Votre liste est vide",
                    if (filter == "ch") "Dans le lecteur ou sur la fiche d'une chaîne, choisissez ★ pour la retrouver ici et en tête du guide."
                    else "Ajoutez un film ou une série depuis sa fiche avec ＋. Vos chaînes favorites apparaîtront aussi ici.",
                ) {
                    PillButton("Explorer les films", { onBrowse("movies") }, icon = R.drawable.ic_film)
                    PillButton("Voir le direct", { onBrowse("live") }, icon = R.drawable.ic_tv, primary = false)
                }
            }
        }
        if (showTitles) items(titles.distinctBy { it.id }, key = { it.id }) { card ->
            val sub = (if (card.type == "SERIES") "Série" else "Film") + (card.year?.let { " · $it" } ?: "")
            if (wide) WideCard(card.title, sub, tmdbImage(card.tmdbBackdrop, "w780"), card.displayImage(), { onOpen(card.id) }, logoUrl = tmdbImage(card.tmdbLogo, "w300"))
            else PosterTile(card.title, sub, card.displayImage(), { onOpen(card.id) }, width = 134.dp)
        }
        if (showChannels) item(key = "channels", span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(top = if (titles.isEmpty() || !showTitles) 0.dp else 8.dp)) {
                RowTitle("Chaînes favorites", "↑/↓ dans le lecteur pour zapper entre elles", Modifier.padding(bottom = 3.dp))
                LazyRow(Modifier.focusRestorer(), contentPadding = PaddingValues(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(channels.distinctBy { it.id }, key = { it.id }) { ch -> ChannelTile(ch.title, ch.imageUrl, nowPlaying[ch.id], { onPlayLive(ch.id, "fav") }, favorite = true) }
                }
            }
        }
    }
}
