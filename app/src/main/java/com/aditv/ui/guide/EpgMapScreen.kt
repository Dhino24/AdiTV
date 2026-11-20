package com.aditv.ui.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.aditv.core.TitleNormalizer
import com.aditv.data.db.EpgChannelEntity
import com.aditv.ui.LocalContainer
import com.aditv.ui.common.AditvButton
import com.aditv.ui.common.InitialFocus
import com.aditv.ui.common.TvTextField
import com.aditv.ui.theme.Aditv
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Association manuelle chaîne → guide. Les noms proches sont des propositions, jamais une preuve :
 * l'utilisateur choisit. Le choix et le décalage sont conservés aux synchronisations.
 */
@Composable
fun EpgMapScreen(itemId: String, onDone: () -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val item by remember(itemId) { c.db.catalog().observeItem(itemId) }.collectAsStateWithLifecycle(null)
    val mapping by remember(itemId) { c.db.live().observeMapping(itemId) }.collectAsStateWithLifecycle(null)
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<EpgChannelEntity>>(emptyList()) }
    val first = remember { FocusRequester() }
    LaunchedEffect(item?.id) { item?.let { if (query.isEmpty()) query = it.title } }
    LaunchedEffect(query, item?.accountId) {
        val acc = item?.accountId ?: return@LaunchedEffect
        delay(250)
        results = c.db.live().searchEpgChannels(acc, TitleNormalizer.searchKey(query))
    }
    InitialFocus(first, key = item != null, enabled = item != null)
    val it = item ?: return
    val offset = mapping?.offsetMinutes ?: 0
    Row(Modifier.fillMaxSize().padding(start = 40.dp, top = 32.dp, end = 32.dp)) {
        Column(Modifier.width(560.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Guide de la chaîne", style = MaterialTheme.typography.headlineMedium, color = Aditv.Text)
            Text(it.title, style = MaterialTheme.typography.titleLarge, color = Aditv.Text)
            Text(
                "Actuel : " + when {
                    mapping?.disabled == true -> "pas de guide (choix manuel)"
                    mapping?.epgChannelId != null -> "${mapping!!.epgChannelId} (manuel)"
                    it.epgChannelId != null -> "${it.epgChannelId} (fourni par le fournisseur)"
                    else -> "aucun identifiant"
                },
                style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary,
            )
            TvTextField("Rechercher une chaîne dans les guides", query, { q -> query = q }, imeAction = ImeAction.Search, surfaceRequester = first)
            Text("Décalage horaire de cette chaîne : ${if (offset > 0) "+" else ""}$offset min", style = MaterialTheme.typography.bodyMedium, color = Aditv.Text)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AditvButton("− 30 min", { scope.launch { c.epg.setChannelOffset(it, offset - 30) } })
                AditvButton("+ 30 min", { scope.launch { c.epg.setChannelOffset(it, offset + 30) } })
                if (offset != 0) AditvButton("Aucun décalage", { scope.launch { c.epg.setChannelOffset(it, 0) } })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AditvButton("Pas de guide", { scope.launch { c.epg.setManualMapping(it, null, disabled = true); onDone() } })
                if (mapping != null) AditvButton("Association du fournisseur", { scope.launch { c.epg.resetMapping(it.id); onDone() } })
            }
            Text("Les noms proches ne prouvent pas qu'il s'agit de la même chaîne (versions régionales, pays). Vérifiez avant de choisir.",
                style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary)
        }
        Spacer(Modifier.width(32.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (results.isEmpty()) item { Text("Aucune chaîne de guide trouvée. Actualisez le guide dans Réglages si besoin.", style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary) }
            items(results, key = { r -> "${r.sourceId}|${r.channelId}" }) { r ->
                ListItem(
                    selected = (mapping?.epgChannelId ?: it.epgChannelId) == r.channelId,
                    onClick = { scope.launch { c.epg.setManualMapping(it, r.channelId); onDone() } },
                    headlineContent = { Text(r.displayName) },
                    supportingContent = { Text(r.channelId) },
                    shape = ListItemDefaults.shape(RoundedCornerShape(10.dp)),
                    colors = ListItemDefaults.colors(focusedContainerColor = Aditv.Text, focusedContentColor = Aditv.OnLight, selectedContainerColor = Aditv.Surface2),
                )
            }
        }
    }
}
