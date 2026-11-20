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
import com.aditv.ui.common.displayImage
import com.aditv.ui.common.tmdbImage
import com.aditv.ui.design.ChannelTile
import com.aditv.ui.design.FilterChip
import com.aditv.ui.design.PillButton
import com.aditv.ui.design.PosterTile
import com.aditv.ui.design.StateBox
import com.aditv.ui.design.WideCard
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.sp
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
import kotlinx.coroutines.flow.onStart

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
    /** null pendant le chargement d'une catégorie (squelettes à l'écran). */
    val cards: StateFlow<List<Pair<CatalogCard, Int>>?> = selected
        .flatMapLatest { id ->
            val source = if (id == FAVORITES) c.db.live().favorites(accountId)
            else if (id == ALL) dao.allCards(accountId, type.name)
            else if (id == null) flowOf(emptyList()) else dao.cardsInCategory(accountId, type.name, id)
            source.combine(c.prefs.variantPrefs) { list, prefs -> list.collapsed(prefs) as List<Pair<CatalogCard, Int>>? }.onStart { emit(null) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val sort = MutableStateFlow(Sort.PROVIDER)
    enum class Sort(val label: String) { PROVIDER("Ordre du fournisseur"), AZ("A → Z"), YEAR("Plus récents") }

    companion object { const val FAVORITES = "★favoris"; const val ALL = "★tout" }
}

@Composable
fun BrowseScreen(accountId: String, type: MediaType, onOpen: (String) -> Unit, onPlayLive: (id: String, list: String?) -> Unit) {
    val c = LocalContainer.current
    val vm: BrowseViewModel = viewModel(key = "browse-$accountId-$type") { BrowseViewModel(c, accountId, type) }
    val categories by vm.categories.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val rawCards by vm.cards.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val running by c.sync.running.collectAsStateWithLifecycle()
    val importing = running[type]
    val firstChip = remember { FocusRequester() }
    val entry = com.aditv.ui.LocalScreenEntry.current
    val gridState = rememberLazyGridState()
    val chrome = com.aditv.ui.shell.LocalNavChrome.current
    val scrolled by remember { derivedStateOf { gridState.firstVisibleItemIndex > 0 } }
    LaunchedEffect(scrolled) { chrome.scrolled = scrolled }

    LaunchedEffect(categories) {
        val list = categories ?: return@LaunchedEffect
        if ((vm.selected.value == null || list.none { it.id == vm.selected.value }) && list.isNotEmpty()) vm.selected.value = list.first().id
    }
    com.aditv.ui.common.InitialFocus(firstChip, key = categories?.isNotEmpty() == true, enabled = categories?.isNotEmpty() == true)

    val cards = remember(rawCards, sort) {
        rawCards?.let { l ->
            when (sort) {
                BrowseViewModel.Sort.PROVIDER -> l
                BrowseViewModel.Sort.AZ -> l.sortedBy { it.first.title.lowercase() }
                BrowseViewModel.Sort.YEAR -> l.sortedByDescending { it.first.year ?: 0 }
            }
        }
    }
    val wide = remember(cards) { type != MediaType.LIVE && cards != null && cards.count { it.first.tmdbBackdrop != null } * 2 >= cards.size && cards.isNotEmpty() }
    val columns = when { type == MediaType.LIVE -> 6; wide -> 4; else -> 6 }
    val title = when (type) { MediaType.LIVE -> "Chaînes"; MediaType.MOVIE -> "Films"; MediaType.SERIES -> "Séries" }
    val word = when (type) { MediaType.LIVE -> "chaîne"; MediaType.MOVIE -> "film"; MediaType.SERIES -> "série" }
    val listKey: (String?) -> String = { cat -> when (cat) { BrowseViewModel.FAVORITES -> "fav"; BrowseViewModel.ALL, null -> "all"; else -> "cat:$cat" } }
    val selectedName = categories?.firstOrNull { it.id == selected }?.name
    val nowPlaying = if (type == MediaType.LIVE) com.aditv.ui.common.rememberNowPlaying(accountId, cards.orEmpty().map { it.first }) else emptyMap()

    LazyVerticalGrid(
        state = gridState,
        columns = GridCells.Fixed(columns),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 74.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "head", span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text(title, style = MaterialTheme.typography.displaySmall, color = Aditv.Text)
                Spacer(Modifier.height(6.dp))
                Text(
                    when {
                        importing != null -> "Import en cours : ${importing.stage} · ${importing.count} reçus"
                        cards == null -> "Chargement…"
                        else -> "${cards.size} ${word}${if (cards.size > 1) "s" else ""}" + (selectedName?.let { " · $it" } ?: "") + " · meilleure version selon vos préférences"
                    },
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 10.sp), color = Aditv.TextTertiary,
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LazyRow(Modifier.weight(1f).focusRestorer(firstChip), horizontalArrangement = Arrangement.spacedBy(5.dp), contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp)) {
                        items(categories.orEmpty(), key = { it.id }) { cat ->
                            val isFirst = cat.id == categories!!.first().id
                            FilterChip(cat.name, cat.id == selected, { vm.selected.value = cat.id },
                                if (isFirst) Modifier.focusRequester(firstChip).focusRequester(entry) else Modifier)
                        }
                    }
                    if (type != MediaType.LIVE) {
                        Spacer(Modifier.width(12.dp))
                        PillButton("Trier : " + sort.label, { vm.sort.value = BrowseViewModel.Sort.entries[(sort.ordinal + 1) % BrowseViewModel.Sort.entries.size] }, icon = com.aditv.R.drawable.ic_sort, primary = false, small = true)
                    }
                }
            }
        }
        when {
            categories == null || cards == null -> items(8, key = { "skel$it" }) { com.aditv.ui.design.SkeletonCard(if (wide) 207.dp else 134.dp, if (type == MediaType.LIVE) 16f / 9f else if (wide) com.aditv.ui.design.CINEMASCOPE else 2f / 3f) }
            categories!!.isEmpty() || cards.isEmpty() -> item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                Box(Modifier.fillMaxWidth().padding(top = 36.dp), contentAlignment = Alignment.TopCenter) {
                    when {
                        importing != null -> StateBox(com.aditv.R.drawable.ic_film, "Import en cours", "${importing.stage} · ${importing.count} reçus. Les titres apparaissent au fur et à mesure ; vous pouvez déjà naviguer.")
                        categories!!.isEmpty() -> StateBox(com.aditv.R.drawable.ic_cloud_off, "Aucun ${word} reçu", "Aucune catégorie n'a été reçue pour ce type. Vérifiez Réglages → Catalogue local (nombre d'éléments et éventuelle erreur).", error = true)
                        selected == BrowseViewModel.ALL -> StateBox(com.aditv.R.drawable.ic_film, "Aucun ${word} importé", "Voir Réglages → Catalogue local : nombre d'éléments reçus et éventuelle erreur de synchronisation.")
                        else -> StateBox(com.aditv.R.drawable.ic_film, "Catégorie vide", "Le fournisseur ne propose rien dans « ${selectedName ?: ""} » pour l'instant. Essayez une autre catégorie.") {
                            PillButton(if (type == MediaType.SERIES) "Voir toutes les séries" else if (type == MediaType.MOVIE) "Voir tous les films" else "Toutes les chaînes", { vm.selected.value = BrowseViewModel.ALL }, icon = null)
                        }
                    }
                }
            }
            else -> {
                items(cards, key = { it.first.id }) { (card, versions) ->
                    val badge = variantBadge(card.lang, card.quality)
                    val extra = if (versions > 1) "$versions versions" else null
                    when {
                        type == MediaType.LIVE -> ChannelTile(card.title, card.imageUrl, listOfNotNull(nowPlaying[card.id], badge, extra).joinToString(" · ").ifEmpty { null }, { onPlayLive(card.id, listKey(selected)) }, width = 134.dp)
                        wide -> WideCard(card.title, listOfNotNull(card.genre?.substringBefore(',')?.trim(), card.year?.toString(), badge, extra).joinToString(" · ").ifEmpty { null },
                            tmdbImage(card.tmdbBackdrop, "w780"), tmdbImage(card.tmdbPoster, "w342") ?: card.imageUrl, { onOpen(card.id) }, logoUrl = tmdbImage(card.tmdbLogo, "w300"))
                        else -> PosterTile(card.title, listOfNotNull(card.year?.toString(), badge, extra).joinToString(" · ").ifEmpty { null }, card.displayImage(), { onOpen(card.id) }, width = 134.dp)
                    }
                }
            }
        }
    }
}
