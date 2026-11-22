package com.aditv.ui.search

import com.aditv.ui.common.navSound
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.aditv.R
import com.aditv.core.TitleNormalizer
import com.aditv.data.db.CatalogCard
import com.aditv.data.variants.collapsed
import com.aditv.ui.LocalContainer
import com.aditv.ui.LocalScreenEntry
import com.aditv.ui.common.InitialFocus
import com.aditv.ui.common.displayImage
import com.aditv.ui.common.tmdbImage
import com.aditv.ui.design.ChannelTile
import com.aditv.ui.design.PosterTile
import com.aditv.ui.design.RowTitle
import com.aditv.ui.design.StateBox
import com.aditv.ui.design.WideCard
import com.aditv.ui.theme.Aditv
import kotlinx.coroutines.delay

private val LETTERS = "abcdefghijklmnopqrstuvwxyz'-".map { it.toString() }
private val DIGITS = "1234567890&:!?.,()+#@/_".map { it.toString() }.let { it + List(28 - it.size) { "" } }

/** Recherche locale dans le catalogue en cache (aucune requête serveur par frappe), clavier à l'écran. */
@Composable
fun SearchScreen(accountId: String, onOpen: (String) -> Unit, onPlayLive: (String) -> Unit) {
    val c = LocalContainer.current
    val prefs by c.prefs.variantPrefs.collectAsStateWithLifecycle(com.aditv.data.variants.VariantPrefs())
    var query by rememberSaveable { mutableStateOf("") }
    var digits by rememberSaveable { mutableStateOf(false) }
    var raw by remember { mutableStateOf<List<CatalogCard>?>(emptyList()) }
    val results = remember(raw, prefs) { raw?.collapsed(prefs)?.map { it.first } }
    val myList by remember(accountId) { c.db.library().watchlist(accountId) }.collectAsStateWithLifecycle(emptyList())
    val firstKey = remember { FocusRequester() }
    InitialFocus(firstKey)
    LaunchedEffect(query) {
        val key = TitleNormalizer.searchKey(query)
        if (key.length < 2) { raw = emptyList(); return@LaunchedEffect }
        raw = null
        delay(250)
        raw = c.db.catalog().search(accountId, key, 200)
    }
    val enoughChars = TitleNormalizer.searchKey(query).length >= 2

    Row(Modifier.fillMaxSize().padding(start = 48.dp, top = 75.dp)) {
        Column(Modifier.width(280.dp)) {
            Row(
                Modifier.fillMaxWidth().height(42.dp).background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(8.dp)).border(0.5.dp, Aditv.Line, RoundedCornerShape(8.dp)).padding(horizontal = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = Aditv.TextTertiary, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(8.dp))
                if (query.isEmpty()) Text("Titre, chaîne…", style = MaterialTheme.typography.bodyLarge.copy(fontSize = 14.sp), color = Aditv.TextTertiary)
                else Text(query, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.Medium), color = Aditv.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Box(Modifier.padding(start = 2.dp).width(1.5.dp).height(20.dp).background(Aditv.Accent))
            }
            Spacer(Modifier.height(13.dp))
            val keys = if (digits) DIGITS else LETTERS
            Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.focusRestorer(firstKey)) {
                keys.chunked(7).forEachIndexed { r, rowKeys ->
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        rowKeys.forEachIndexed { i, k ->
                            Key(k.ifEmpty { " " }, Modifier.weight(1f).then(if (r == 0 && i == 0) Modifier.focusRequester(firstKey).focusRequester(LocalScreenEntry.current) else Modifier), enabled = k.isNotEmpty()) { query += k }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Key(if (digits) "abc" else "123", Modifier.weight(1f), small = true) { digits = !digits }
                    Key("Espace", Modifier.weight(3f), small = true) { if (query.isNotEmpty() && !query.endsWith(" ")) query += " " }
                    Key("⌫", Modifier.weight(1f), icon = R.drawable.ic_del) { query = query.dropLast(1) }
                    Key("Effacer", Modifier.weight(2f), small = true) { query = "" }
                }
            }
            Spacer(Modifier.height(11.dp))
            Text("Recherche dans le catalogue déjà synchronisé : films, séries et chaînes.", style = MaterialTheme.typography.bodySmall, color = Aditv.TextTertiary)
        }
        Spacer(Modifier.width(42.dp))
        Box(Modifier.fillMaxSize()) {
            val films = results.orEmpty().filter { it.type == "MOVIE" }
            val series = results.orEmpty().filter { it.type == "SERIES" }
            val chans = results.orEmpty().filter { it.type == "LIVE" }
            when {
                !enoughChars -> LazyColumn(contentPadding = PaddingValues(bottom = 40.dp)) {
                    item {
                        Text(if (query.isEmpty()) "Saisissez au moins 2 caractères." else "Encore un caractère…", style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary, modifier = Modifier.padding(bottom = 18.dp))
                    }
                    if (myList.isNotEmpty()) item { ResultRow("Dans Ma liste", myList, onOpen, onPlayLive) }
                }
                results == null -> Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { repeat(3) { com.aditv.ui.design.SkeletonCard(170.dp) } }
                results.isEmpty() -> Box(Modifier.padding(top = 50.dp)) {
                    StateBox(R.drawable.ic_search, "Aucun résultat pour « ${query.trim()} »", "Vérifiez l'orthographe, ou essayez un autre mot du titre ou le nom de la chaîne.", centered = false) {
                        com.aditv.ui.design.PillButton("Effacer la recherche", { query = "" }, icon = null, primary = false)
                    }
                }
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    if (films.isNotEmpty()) item { ResultRow("Films", films, onOpen, onPlayLive, "${films.size}") }
                    if (series.isNotEmpty()) item { ResultRow("Séries", series, onOpen, onPlayLive, "${series.size}") }
                    if (chans.isNotEmpty()) item { ResultRow("Chaînes", chans, onOpen, onPlayLive, "${chans.size}") }
                    if (raw?.size == 200) item { Text("Plus de 200 résultats : affinez la recherche.", style = MaterialTheme.typography.bodySmall, color = Aditv.TextTertiary) }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(title: String, cards: List<CatalogCard>, onOpen: (String) -> Unit, onPlayLive: (String) -> Unit, hint: String? = null) {
    val wide = cards.count { it.tmdbBackdrop != null } * 2 >= cards.size
    Column {
        RowTitle(title, hint, Modifier.padding(bottom = 3.dp))
        LazyRow(Modifier.focusRestorer(), contentPadding = PaddingValues(top = 8.dp, bottom = 8.dp, end = 48.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(cards.distinctBy { it.id }, key = { it.id }) { card ->
                val sub = (if (card.type == "SERIES") "Série" else "Film") + (card.year?.let { " · $it" } ?: "")
                when {
                    card.type == "LIVE" -> ChannelTile(card.title, card.imageUrl, "OK pour regarder", { onPlayLive(card.id) }, width = 110.dp)
                    wide -> WideCard(card.title, sub, tmdbImage(card.tmdbBackdrop, "w780"), card.displayImage(), { onOpen(card.id) }, width = 170.dp, logoUrl = tmdbImage(card.tmdbLogo, "w300"))
                    else -> PosterTile(card.title, sub, card.displayImage(), { onOpen(card.id) }, width = 100.dp)
                }
            }
        }
    }
}

private val KeyHalo = Glow(elevationColor = Aditv.Accent.copy(alpha = 0.3f), elevation = 8.dp)

@Composable
private fun Key(label: String, modifier: Modifier, small: Boolean = false, icon: Int? = null, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        onClick = onClick, enabled = enabled,
        modifier = (modifier.height(31.dp)).navSound(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = KeyHalo),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.05f), contentColor = Aditv.Text,
            focusedContainerColor = Color.White, focusedContentColor = Aditv.OnLight,
            disabledContainerColor = Color.Transparent,
        ),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (icon != null) Icon(painterResource(icon), contentDescription = "Effacer un caractère", modifier = Modifier.size(14.dp))
            else Text(label, style = MaterialTheme.typography.bodyLarge.copy(fontSize = if (small) 10.sp else 13.sp, fontWeight = FontWeight.Medium))
        }
    }
}

