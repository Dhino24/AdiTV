package com.aditv.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.aditv.ui.LocalContainer
import com.aditv.ui.LocalScreenEntry
import com.aditv.ui.common.InitialFocus
import com.aditv.ui.common.PosterCard
import com.aditv.ui.common.displayImage
import com.aditv.ui.theme.Aditv

/** Films et séries suivis. Distinct des chaînes favorites (phase 3) et de l'historique. */
@Composable
fun MyListScreen(accountId: String, onOpen: (String) -> Unit) {
    val c = LocalContainer.current
    val list by remember(accountId) { c.db.library().watchlist(accountId) }.collectAsStateWithLifecycle(null)
    val first = remember { FocusRequester() }
    val entry = LocalScreenEntry.current
    InitialFocus(first, key = list?.isNotEmpty() == true, enabled = list?.isNotEmpty() == true)
    Column(Modifier.fillMaxSize().padding(start = 40.dp, top = 32.dp, end = 32.dp)) {
        Text("Ma liste", style = MaterialTheme.typography.headlineMedium, color = Aditv.Text)
        Spacer(Modifier.height(16.dp))
        if (list?.isEmpty() == true) Text("Votre liste est vide. Ajoutez des films et séries depuis leur fiche avec « + Ma liste ».", style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(146.dp), modifier = Modifier.fillMaxSize().focusRestorer(),
            contentPadding = PaddingValues(bottom = 48.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            items(list.orEmpty(), key = { it.id }) { card ->
                val m = if (card.id == list?.firstOrNull()?.id) Modifier.focusRequester(first).focusRequester(entry) else Modifier
                PosterCard(card.title, card.displayImage(), if (card.type == "SERIES") "Série" else card.year?.toString(), { onOpen(card.id) }, m)
            }
        }
    }
}
