package com.aditv.ui.match

import com.aditv.ui.common.navSound
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.aditv.data.tmdb.TmdbCandidate
import com.aditv.data.tmdb.TmdbError
import com.aditv.ui.LocalContainer
import com.aditv.ui.common.AditvButton
import com.aditv.ui.common.InitialFocus
import com.aditv.ui.common.RemoteImage
import com.aditv.ui.common.TvTextField
import com.aditv.ui.common.tmdbImage
import com.aditv.ui.theme.Aditv
import kotlinx.coroutines.launch

/** Correction manuelle d'une correspondance TMDB. Le choix est verrouillé et survit aux synchronisations. */
@Composable
fun MatchScreen(itemId: String, onDone: () -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val item by remember(itemId) { c.db.catalog().observeItem(itemId) }.collectAsStateWithLifecycle(null)
    val match by remember(itemId) { c.db.metadata().observeMatch(itemId) }.collectAsStateWithLifecycle(null)
    var query by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<TmdbCandidate>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }

    LaunchedEffect(item?.id) {
        val it = item ?: return@LaunchedEffect
        if (query.isEmpty()) { query = it.title; year = it.year?.toString().orEmpty() }
    }
    LaunchedEffect(match?.candidatesJson) { if (results == null) c.metadata.candidatesOf(match).takeIf { it.isNotEmpty() }?.let { results = it } }
    InitialFocus(first, key = item != null, enabled = item != null)

    val it = item ?: return
    val type = if (it.type == "SERIES") "tv" else "movie"
    fun search() {
        busy = true; message = null
        scope.launch {
            try {
                results = c.metadata.searchCandidates(type, query.trim(), year.trim().toIntOrNull())
                if (results!!.isEmpty()) message = "Aucun résultat TMDB pour cette recherche."
            } catch (e: TmdbError) { message = e.message }
            busy = false
        }
    }

    Row(Modifier.fillMaxSize().padding(start = 40.dp, top = 32.dp, end = 32.dp)) {
        Column(Modifier.width(520.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Correspondance TMDB", style = MaterialTheme.typography.headlineMedium, color = Aditv.Text)
            Text("Titre fournisseur : « ${it.rawTitle} »", style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary)
            Text(
                when {
                    match == null -> "Aucune correspondance enregistrée."
                    match!!.origin == "manual" -> "Choix manuel actuel (verrouillé)."
                    else -> "État actuel : ${match!!.status} (${match!!.origin})."
                },
                style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary,
            )
            TvTextField("Titre à chercher", query, { q -> query = q }, surfaceRequester = first)
            TvTextField("Année (facultatif)", year, { y -> year = y.filter(Char::isDigit).take(4) }, keyboardType = KeyboardType.Number, imeAction = ImeAction.Search, onImeAction = { search() })
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AditvButton(if (busy) "Recherche…" else "Rechercher", { search() }, enabled = !busy)
                AditvButton("Aucune correspondance", { scope.launch { c.metadata.rejectManually(itemId); onDone() } })
            }
            if (match?.locked == true) AditvButton("Revenir au choix automatique", { scope.launch { message = c.metadata.resetToAutomatic(itemId)?.message; if (message == null) onDone() } })
            message?.let { m -> Text(m, style = MaterialTheme.typography.bodyMedium, color = Aditv.Warn) }
            Text("Le type (${if (type == "tv") "série" else "film"}) est fixé par le catalogue du fournisseur.", style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary)
        }
        Spacer(Modifier.width(32.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(results.orEmpty().distinctBy { it.id }, key = { r -> r.id }) { r ->
                ListItem(
                    selected = match?.tmdbId == r.id,
                    onClick = { scope.launch { busy = true; runCatching { c.metadata.chooseManually(itemId, r.mediaType, r.id) }.onFailure { e -> message = e.message }; busy = false; if (message == null) onDone() } },
                    leadingContent = { RemoteImage(tmdbImage(r.posterPath, "w92"), r.title, Modifier.size(width = 46.dp, height = 69.dp)) },
                    headlineContent = { Text(r.title + (r.year?.let { y -> " ($y)" } ?: "")) },
                    supportingContent = { r.originalTitle?.takeIf { o -> o != r.title }?.let { o -> Text(o) } },
                    shape = ListItemDefaults.shape(RoundedCornerShape(10.dp)),
                    colors = ListItemDefaults.colors(focusedContainerColor = Aditv.Text, focusedContentColor = Aditv.OnLight, selectedContainerColor = Aditv.Surface2),
                    modifier = (Modifier.height(88.dp)).navSound(),
                )
            }
        }
    }
}
