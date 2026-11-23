package com.aditv.ui.cinema

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aditv.R
import com.aditv.data.db.CatalogCard
import com.aditv.data.provider.MediaType
import com.aditv.data.variants.collapsed
import com.aditv.data.variants.variantBadge
import com.aditv.ui.LocalContainer
import com.aditv.ui.common.tmdbImage
import com.aditv.ui.design.CinemaBackdrop
import com.aditv.ui.design.LocalReducedMotion
import com.aditv.ui.design.PillButton
import com.aditv.ui.design.PosterTile
import com.aditv.ui.design.RowTitle
import com.aditv.ui.design.Spot
import com.aditv.ui.design.SpotInfo
import com.aditv.ui.design.StateBox
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Films / Séries, direction cinéma : vedette plein écran du titre sélectionné en haut, une rangée par catégorie
 * en bas (grandes affiches, une rangée à la fois). « Tout parcourir » ouvre la grille complète (filtres, tri).
 */
@Composable
fun CinemaBrowseScreen(accountId: String, type: MediaType, onOpen: (String) -> Unit, onBrowseAll: () -> Unit) {
    val c = LocalContainer.current
    val reduced = LocalReducedMotion.current
    val word = if (type == MediaType.SERIES) "Séries" else "Films"
    val categories by remember(accountId, type) { c.db.catalog().categories(accountId, type.name) }.collectAsStateWithLifecycle(null)
    val recent by produceState<List<CatalogCard>?>(null, accountId, type) {
        val prefs = c.prefs.variantPrefs.first()
        value = withContext(Dispatchers.Default) {
            c.db.catalog().recentlyAdded(accountId, type.name, 60).first().ifEmpty { c.db.catalog().cardsWindow(accountId, type.name, null, 0, 60).first() }
                .collapsed(prefs).map { it.first }.distinctBy { it.id }.take(30)
        }
    }
    var spot by remember { mutableStateOf<Spot?>(null) }
    var focused by remember { mutableStateOf<Pair<CatalogCard, String>?>(null) }
    LaunchedEffect(focused) { val (card, k) = focused ?: return@LaunchedEffect; delay(140); spot = c.spotFor(card, k) }
    val onCard: (CatalogCard, String) -> Unit = { card, k -> spot = quickSpot(card, k); focused = card to k }
    val first = remember { FocusRequester() }
    val entry = com.aditv.ui.LocalScreenEntry.current
    com.aditv.ui.common.InitialFocus(first, key = recent?.isNotEmpty() == true, enabled = recent?.isNotEmpty() == true)
    LaunchedEffect(recent) { if (spot == null) recent?.firstOrNull()?.let { spot = c.spotFor(it, "Ajouts récents") } }

    Box(Modifier.fillMaxSize()) {
        CinemaBackdrop(spot, reduced)
        SpotInfo(spot, Modifier.padding(start = 48.dp, top = 84.dp))
        when {
            recent == null -> Unit
            recent!!.isEmpty() && categories.isNullOrEmpty() -> Box(Modifier.fillMaxSize().padding(top = 120.dp), contentAlignment = Alignment.TopCenter) {
                StateBox(R.drawable.ic_film, "Aucun titre pour l'instant", "Le catalogue se met à jour : Réglages → Catalogue local pour suivre l'import.")
            }
            else -> LazyColumn(Modifier.fillMaxSize().padding(top = 250.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
                item(key = "actions") {
                    Row(Modifier.padding(start = 48.dp, bottom = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton("Tout parcourir", onBrowseAll, icon = R.drawable.ic_layers, primary = false, small = true)
                    }
                }
                recent?.takeIf { it.isNotEmpty() }?.let { list ->
                    item(key = "recent") { PosterRow("Ajouts récents", list, onOpen, onCard, Modifier.focusRequester(first).focusRequester(entry)) }
                }
                items(categories.orEmpty().distinctBy { it.id }, key = { it.id }) { cat -> CategoryRow(accountId, type, cat.id, cat.name, onOpen, onCard) }
            }
        }
    }
}

/** Rangée d'une catégorie : ses 24 premiers titres, chargés seulement quand la rangée apparaît. */
@Composable
private fun CategoryRow(accountId: String, type: MediaType, categoryId: String, name: String, onOpen: (String) -> Unit, onCard: (CatalogCard, String) -> Unit) {
    val c = LocalContainer.current
    val cards by produceState<List<CatalogCard>?>(null, categoryId) {
        val prefs = c.prefs.variantPrefs.first()
        value = withContext(Dispatchers.Default) {
            c.db.catalog().cardsWindow(accountId, type.name, categoryId, 0, 48).first().collapsed(prefs).map { it.first }.distinctBy { it.id }.take(24)
        }
    }
    val list = cards ?: return Box(Modifier.height(220.dp))
    if (list.isEmpty()) return
    PosterRow(name, list, onOpen, onCard)
}

@Composable
private fun PosterRow(title: String, cards: List<CatalogCard>, onOpen: (String) -> Unit, onCard: (CatalogCard, String) -> Unit, firstModifier: Modifier = Modifier) {
    Column(Modifier.padding(top = 8.dp)) {
        RowTitle(title, modifier = Modifier.padding(start = 48.dp, bottom = 4.dp))
        LazyRow(Modifier.focusRestorer(), contentPadding = PaddingValues(horizontal = 48.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(cards, key = { it.id }) { card ->
                PosterTile(
                    card.title, listOfNotNull(card.year?.toString(), variantBadge(card.lang, card.quality)).joinToString(" · ").ifEmpty { null },
                    tmdbImage(card.tmdbPoster, "w342") ?: card.imageUrl, { onOpen(card.id) },
                    (if (card === cards.first()) firstModifier else Modifier).onFocusChanged { if (it.isFocused) onCard(card, title) },
                    width = 112.dp,
                )
            }
        }
    }
}
