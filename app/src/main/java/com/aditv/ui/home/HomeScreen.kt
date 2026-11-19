package com.aditv.ui.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.aditv.data.db.CatalogCard
import com.aditv.data.provider.MediaType
import com.aditv.data.sync.SyncProgress
import com.aditv.di.AppContainer
import com.aditv.ui.LocalContainer
import com.aditv.ui.common.AditvButton
import com.aditv.ui.common.ChannelCard
import com.aditv.ui.common.PosterCard
import com.aditv.ui.common.RemoteImage
import com.aditv.ui.common.StatusDot
import com.aditv.ui.common.Tag
import com.aditv.ui.formatMillisDateTime
import com.aditv.ui.theme.Aditv
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeRow(val title: String, val subtitle: String?, val type: MediaType, val cards: List<CatalogCard>)

data class HomeState(
    val loaded: Boolean = false,
    val hero: CatalogCard? = null,
    val rows: List<HomeRow> = emptyList(),
    val lastSyncAt: Long? = null,
    val syncErrors: List<String> = emptyList(),
)

class HomeViewModel(c: AppContainer, accountId: String) : ViewModel() {
    private val dao = c.db.catalog()
    val progress: StateFlow<SyncProgress> = c.sync.progress

    val state: StateFlow<HomeState> = combine(
        dao.recentlyAdded(accountId, MediaType.MOVIE.name, 24),
        dao.recentlyAdded(accountId, MediaType.SERIES.name, 24),
        dao.firstItems(accountId, MediaType.LIVE.name, 24),
        dao.firstItems(accountId, MediaType.MOVIE.name, 24),
        dao.observeSyncStates(accountId),
    ) { recentMovies, recentSeries, channels, anyMovies, syncStates ->
        val rows = buildList {
            // « Ajouts récents » = date d'ajout déclarée par le fournisseur, pas des sorties récentes.
            if (recentMovies.isNotEmpty()) add(HomeRow("Ajouts récents · Films", "selon la date d'ajout du fournisseur", MediaType.MOVIE, recentMovies))
            else if (anyMovies.isNotEmpty()) add(HomeRow("Films", null, MediaType.MOVIE, anyMovies))
            if (recentSeries.isNotEmpty()) add(HomeRow("Ajouts récents · Séries", "selon la date d'ajout du fournisseur", MediaType.SERIES, recentSeries))
            if (channels.isNotEmpty()) add(HomeRow("Chaînes", "ordre du fournisseur", MediaType.LIVE, channels))
        }
        HomeState(
            loaded = true,
            hero = recentMovies.firstOrNull() ?: recentSeries.firstOrNull() ?: anyMovies.firstOrNull(),
            rows = rows,
            lastSyncAt = syncStates.mapNotNull { it.lastSuccessAt }.maxOrNull(),
            syncErrors = syncStates.mapNotNull { s -> s.lastError?.let { "${typeLabel(s.type)} : $it" } },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())
}

fun typeLabel(type: String) = when (type) { "LIVE" -> "Direct"; "MOVIE" -> "Films"; else -> "Séries" }

@Composable
fun HomeScreen(accountId: String, isDemo: Boolean, onOpen: (String) -> Unit) {
    val c = LocalContainer.current
    val vm: HomeViewModel = viewModel(key = "home-$accountId") { HomeViewModel(c, accountId) }
    val state by vm.state.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val heroRequester = remember { FocusRequester() }

    com.aditv.ui.common.InitialFocus(heroRequester, key = state.hero != null, enabled = state.hero != null)

    Box(Modifier.fillMaxSize()) {
        if (state.loaded && state.rows.isEmpty()) {
            EmptyCatalog(progress, state.syncErrors)
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(28.dp)) {
                item { Hero(state.hero, isDemo, heroRequester, onOpen) }
                items(state.rows, key = { it.title }) { row -> ContentRow(row, onOpen) }
            }
        }
        SyncBadge(progress, state.lastSyncAt, state.syncErrors.isNotEmpty(), Modifier.align(Alignment.TopEnd).padding(24.dp))
    }
}

@Composable
private fun Hero(card: CatalogCard?, isDemo: Boolean, requester: FocusRequester, onOpen: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().height(290.dp)) {
        if (card?.imageUrl != null) {
            RemoteImage(card.imageUrl, card.title, Modifier.fillMaxHeight().fillMaxWidth(0.62f).align(Alignment.TopEnd), fallbackTitle = false)
            Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0.38f to Aditv.Bg, 0.6f to Aditv.Bg.copy(alpha = 0.6f), 0.9f to Aditv.Bg.copy(alpha = 0f))))
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.55f to Aditv.Bg.copy(alpha = 0f), 1f to Aditv.Bg)))
        } else {
            // Pas d'image fiable : simple halo d'ambiance, aucune fausse affiche.
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Aditv.Accent.copy(alpha = 0.16f), Aditv.Bg.copy(alpha = 0f)), center = androidx.compose.ui.geometry.Offset(1500f, 120f), radius = 900f)))
        }
        Column(Modifier.padding(start = 40.dp, top = 44.dp).width(520.dp)) {
            Text(
                if (isDemo) "MODE DÉMO · CONTENU FICTIF" else "AJOUT RÉCENT DU FOURNISSEUR",
                style = MaterialTheme.typography.labelSmall, color = Aditv.Accent,
            )
            Spacer(Modifier.height(10.dp))
            Text(card?.title ?: "Bienvenue sur ADITV", style = MaterialTheme.typography.displaySmall, color = Aditv.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                card?.year?.let { Text(it.toString(), style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary) }
                card?.genre?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary) }
                if (card != null) Tag(if (card.type == "SERIES") "Série" else "Film")
            }
            Spacer(Modifier.height(20.dp))
            if (card != null) AditvButton("Voir la fiche", { onOpen(card.id) }, Modifier.focusRequester(requester).focusRequester(com.aditv.ui.LocalScreenEntry.current))
        }
    }
}

@Composable
private fun ContentRow(row: HomeRow, onOpen: (String) -> Unit) {
    Column {
        Row(Modifier.padding(start = 40.dp, bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
            Text(row.title, style = MaterialTheme.typography.headlineSmall, color = Aditv.Text)
            if (row.subtitle != null) {
                Spacer(Modifier.width(12.dp))
                Text(row.subtitle, style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary)
            }
        }
        LazyRow(
            Modifier.focusRestorer(),
            contentPadding = PaddingValues(horizontal = 40.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(row.cards, key = { it.id }) { card ->
                if (row.type == MediaType.LIVE) ChannelCard(card.title, card.imageUrl, null, { onOpen(card.id) })
                else PosterCard(card.title, card.imageUrl, card.year?.toString(), { onOpen(card.id) })
            }
        }
    }
}

@Composable
private fun EmptyCatalog(progress: SyncProgress, errors: List<String>) {
    Column(Modifier.fillMaxSize().padding(48.dp), verticalArrangement = Arrangement.Center) {
        when (progress) {
            is SyncProgress.Running -> {
                Text("Import du catalogue en cours", style = MaterialTheme.typography.headlineMedium, color = Aditv.Text)
                Spacer(Modifier.height(10.dp))
                Text("${typeLabel(progress.type.name)} · ${progress.stage} · ${progress.count} reçus", style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary)
                Text("Le catalogue s'affiche au fur et à mesure ; vous pouvez déjà naviguer.", style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary)
            }
            else -> {
                Text("Catalogue vide", style = MaterialTheme.typography.headlineMedium, color = Aditv.Text)
                Spacer(Modifier.height(10.dp))
                Text(
                    if (errors.isEmpty()) "Aucun élément reçu pour le moment. Lancez une synchronisation depuis Paramètres."
                    else "La dernière synchronisation a échoué :", style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary,
                )
                errors.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = Aditv.Error) }
            }
        }
    }
}

@Composable
private fun SyncBadge(progress: SyncProgress, lastSyncAt: Long?, hasError: Boolean, modifier: Modifier) {
    Row(modifier.background(Aditv.Bg.copy(alpha = 0.7f), androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        when {
            progress is SyncProgress.Running -> { StatusDot(Aditv.Accent); Spacer(Modifier.width(8.dp)); Text("Mise à jour · ${typeLabel(progress.type.name)} ${progress.count}", style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary) }
            hasError -> { StatusDot(Aditv.Warn); Spacer(Modifier.width(8.dp)); Text("Synchronisation incomplète · voir Paramètres", style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary) }
            lastSyncAt != null -> { StatusDot(Aditv.Ok); Spacer(Modifier.width(8.dp)); Text("Catalogue du ${formatMillisDateTime(lastSyncAt)}", style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary) }
        }
    }
}
