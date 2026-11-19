package com.aditv.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.aditv.core.TitleNormalizer
import com.aditv.data.db.CatalogCard
import com.aditv.ui.LocalContainer
import com.aditv.ui.common.ChannelCard
import com.aditv.ui.common.PosterCard
import com.aditv.ui.common.TvTextField
import com.aditv.ui.theme.Aditv
import kotlinx.coroutines.delay

/** Recherche locale dans le catalogue en cache : aucune requête serveur par frappe. */
@Composable
fun SearchScreen(accountId: String, onOpen: (String) -> Unit) {
    val c = LocalContainer.current
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<CatalogCard>>(emptyList()) }
    val field = remember { FocusRequester() }
    com.aditv.ui.common.InitialFocus(field)
    LaunchedEffect(query) {
        val key = TitleNormalizer.searchKey(query)
        if (key.length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(250)
        results = c.db.catalog().search(accountId, key, 120)
    }
    Column(Modifier.fillMaxSize().padding(start = 40.dp, top = 32.dp, end = 32.dp)) {
        Text("Recherche", style = MaterialTheme.typography.headlineMedium, color = Aditv.Text)
        Spacer(Modifier.height(16.dp))
        TvTextField("Titre de film, série ou chaîne", query, { query = it }, Modifier.width(560.dp), placeholder = "OK pour saisir", imeAction = ImeAction.Search, surfaceRequester = field, modifierEntry = com.aditv.ui.LocalScreenEntry.current)
        Spacer(Modifier.height(12.dp))
        Text(
            when {
                TitleNormalizer.searchKey(query).length < 2 -> "Saisissez au moins 2 caractères. Programmes TV : phase 3."
                results.isEmpty() -> "Aucun résultat dans le catalogue local."
                else -> "${results.size} résultat(s)" + if (results.size == 120) " — affinez la recherche" else ""
            },
            style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary,
        )
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(146.dp), modifier = Modifier.fillMaxSize().focusRestorer(),
            contentPadding = PaddingValues(bottom = 48.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(results, key = { it.id }) { card ->
                if (card.type == "LIVE") ChannelCard(card.title, card.imageUrl, "Chaîne", { onOpen(card.id) })
                else PosterCard(card.title, card.imageUrl, (if (card.type == "SERIES") "Série" else "Film") + (card.year?.let { " · $it" } ?: ""), { onOpen(card.id) })
            }
        }
    }
}
