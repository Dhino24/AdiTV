package com.aditv.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.ListItem
import androidx.tv.material3.ListItemDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.aditv.data.db.CatalogCard
import com.aditv.data.db.CategoryEntity
import com.aditv.data.provider.MediaType
import com.aditv.di.AppContainer
import com.aditv.ui.LocalContainer
import com.aditv.ui.common.ChannelCard
import com.aditv.ui.common.PosterCard
import com.aditv.ui.common.displayImage
import com.aditv.ui.theme.Aditv
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import com.aditv.data.variants.collapsed
import com.aditv.data.variants.variantBadge
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class BrowseViewModel(c: AppContainer, private val accountId: String, private val type: MediaType) : ViewModel() {
    private val dao = c.db.catalog()
    val categories: StateFlow<List<CategoryEntity>?> =
        dao.categories(accountId, type.name)
            .combine(c.db.live().favoriteIds(accountId)) { cats, favs ->
                buildList {
                    // « ★ Favoris » en tête du Direct dès qu'une chaîne est favorite.
                    if (type == MediaType.LIVE && favs.isNotEmpty()) add(CategoryEntity(FAVORITES, accountId, type.name, FAVORITES, "★ Favoris", -2, true, 0))
                    // « Tout » : visible même si le fournisseur ne rattache pas ses titres à des catégories.
                    add(CategoryEntity(ALL, accountId, type.name, ALL, when (type) { MediaType.LIVE -> "Toutes les chaînes"; MediaType.MOVIE -> "Tous les films"; MediaType.SERIES -> "Toutes les séries" }, -1, true, 0))
                    addAll(cats)
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val selected = MutableStateFlow<String?>(null)
    /** Une carte par contenu : la meilleure version selon la langue et la qualité préférées. */
    val cards: StateFlow<List<Pair<CatalogCard, Int>>> = selected
        .flatMapLatest { id ->
            if (id == FAVORITES) c.db.live().favorites(accountId)
            else if (id == ALL) dao.allCards(accountId, type.name)
            else if (id == null) flowOf(emptyList()) else dao.cardsInCategory(accountId, type.name, id)
        }
        .combine(c.prefs.variantPrefs) { list, prefs -> list.collapsed(prefs) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object { const val FAVORITES = "★favoris"; const val ALL = "★tout" }
}

@Composable
fun BrowseScreen(accountId: String, type: MediaType, onOpen: (String) -> Unit, onPlayLive: (id: String, list: String?) -> Unit) {
    val c = LocalContainer.current
    val vm: BrowseViewModel = viewModel(key = "browse-$accountId-$type") { BrowseViewModel(c, accountId, type) }
    val categories by vm.categories.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val cards by vm.cards.collectAsStateWithLifecycle()
    val running by c.sync.running.collectAsStateWithLifecycle()
    val importing = running[type]
    var focusedCategory by remember { mutableStateOf<String?>(null) }
    val firstCategory = remember { FocusRequester() }
    val entry = com.aditv.ui.LocalScreenEntry.current

    // Sélection au focus, avec un court délai pour ne pas recharger à chaque appui maintenu.
    LaunchedEffect(focusedCategory) {
        val id = focusedCategory ?: return@LaunchedEffect
        delay(220); vm.selected.value = id
    }
    LaunchedEffect(categories) {
        val list = categories ?: return@LaunchedEffect
        if (vm.selected.value == null && list.isNotEmpty()) vm.selected.value = list.first().id
    }

    com.aditv.ui.common.InitialFocus(firstCategory, key = categories?.isNotEmpty() == true, enabled = categories?.isNotEmpty() == true)

    val title = when (type) { MediaType.LIVE -> "Direct"; MediaType.MOVIE -> "Films"; MediaType.SERIES -> "Séries" }
    Column(Modifier.fillMaxSize().padding(top = 32.dp)) {
        Row(Modifier.padding(start = 40.dp, bottom = 16.dp)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, color = Aditv.Text)
            if (type == MediaType.LIVE) {
                Spacer(Modifier.width(16.dp))
                Text("OK : regarder · ★ dans le lecteur pour ajouter aux favoris · meilleure version selon vos préférences", style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary, modifier = Modifier.padding(top = 12.dp))
            }
        }
        when {
            categories == null -> Unit
            categories!!.isEmpty() && importing != null -> Text(
                "Import en cours : ${importing.stage} · ${importing.count} reçus.",
                style = MaterialTheme.typography.bodyLarge, color = Aditv.Accent, modifier = Modifier.padding(start = 40.dp),
            )
            categories!!.isEmpty() -> Text(
                "Aucune catégorie reçue pour ce type. Vérifiez la synchronisation dans Réglages.",
                style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary, modifier = Modifier.padding(start = 40.dp),
            )
            else -> Row(Modifier.fillMaxSize()) {
                LazyColumn(
                    Modifier.width(260.dp).fillMaxHeight().focusRestorer(firstCategory),
                    contentPadding = PaddingValues(start = 28.dp, end = 8.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(categories!!, key = { it.id }) { cat ->
                        val isFirst = cat.id == categories!!.first().id
                        ListItem(
                            selected = cat.id == selected,
                            onClick = { vm.selected.value = cat.id },
                            headlineContent = { Text(cat.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            shape = ListItemDefaults.shape(RoundedCornerShape(10.dp)),
                            colors = ListItemDefaults.colors(
                                focusedContainerColor = Aditv.Text, focusedContentColor = Aditv.OnLight,
                                selectedContainerColor = Aditv.Surface2, selectedContentColor = Aditv.Text,
                                contentColor = Aditv.TextSecondary,
                            ),
                            scale = ListItemDefaults.scale(focusedScale = 1.02f),
                            modifier = Modifier
                                .then(if (isFirst) Modifier.focusRequester(firstCategory).focusRequester(entry) else Modifier)
                                .onFocusChanged { if (it.isFocused) focusedCategory = cat.id },
                        )
                    }
                }
                Box(Modifier.fillMaxSize()) {
                    if (cards.isEmpty() && selected != null) {
                        if (importing != null) Text(
                            "Import en cours : ${importing.stage} · ${importing.count} reçus. Les titres apparaissent au fur et à mesure.",
                            style = MaterialTheme.typography.bodyLarge, color = Aditv.Accent, modifier = Modifier.padding(24.dp),
                        ) else Text(if (selected == BrowseViewModel.ALL) "Aucun élément importé pour ce type : voir Réglages → Catalogue local (nombre d'éléments et éventuelle erreur)." else "Catégorie vide chez le fournisseur. Essayez « ${categories?.getOrNull(if (type == MediaType.LIVE && categories!!.firstOrNull()?.id == BrowseViewModel.FAVORITES) 1 else 0)?.name ?: "Tout"} ».", style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary, modifier = Modifier.padding(24.dp))
                    }
                    val nowPlaying = if (type == MediaType.LIVE) com.aditv.ui.common.rememberNowPlaying(accountId, cards.map { it.first }) else emptyMap()
                    LazyVerticalGrid(
                        columns = if (type == MediaType.LIVE) GridCells.Adaptive(190.dp) else GridCells.Adaptive(146.dp),
                        modifier = Modifier.fillMaxSize().focusRestorer(),
                        contentPadding = PaddingValues(start = 16.dp, end = 32.dp, top = 8.dp, bottom = 48.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        items(cards, key = { it.first.id }) { (card, versions) ->
                            val badge = variantBadge(card.lang, card.quality)
                            val extra = if (versions > 1) "$versions versions" else null
                            if (type == MediaType.LIVE) ChannelCard(card.title, card.imageUrl, listOfNotNull(nowPlaying[card.id], badge, extra).joinToString(" · ").ifEmpty { null }, { onPlayLive(card.id, selected) })
                            else PosterCard(card.title, card.displayImage(), listOfNotNull(card.year?.toString(), badge, extra).joinToString(" · ").ifEmpty { null }, { onOpen(card.id) })
                        }
                    }
                }
            }
        }
    }
}
