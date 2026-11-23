package com.aditv.ui.common

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.SubcomposeAsyncImage
import com.aditv.ui.theme.Aditv
import kotlin.math.abs

val FocusBorder = Border(BorderStroke(3.dp, Aditv.Text), shape = RoundedCornerShape(12.dp))

/** Bouton principal ADITV : blanc au focus, halo turquoise, mise à l'échelle 1,04. */
@Composable
fun AditvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    secondaryText: String? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = (modifier.height(30.dp)).navSound(),
        shape = ButtonDefaults.shape(RoundedCornerShape(50)),
        scale = ButtonDefaults.scale(focusedScale = 1.06f),
        glow = ButtonDefaults.glow(focusedGlow = androidx.tv.material3.Glow(Aditv.AccentSoft.copy(alpha = 0.16f), 6.dp)),
        colors = ButtonDefaults.colors(
            containerColor = Aditv.Text.copy(alpha = 0.13f),
            contentColor = Aditv.Text,
            focusedContainerColor = Aditv.Text,
            focusedContentColor = Aditv.OnLight,
            disabledContainerColor = Aditv.Text.copy(alpha = 0.05f),
            disabledContentColor = Aditv.TextSecondary.copy(alpha = 0.6f),
        ),
        border = ButtonDefaults.border(
            border = Border(BorderStroke(0.5.dp, Aditv.Text.copy(alpha = 0.14f)), shape = RoundedCornerShape(50)),
            focusedBorder = Border(BorderStroke(2.dp, Aditv.Accent.copy(alpha = 0.6f)), shape = RoundedCornerShape(50), inset = (-2).dp),
        ),
        contentPadding = PaddingValues(horizontal = 15.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
        if (secondaryText != null) {
            Spacer(Modifier.width(6.dp))
            Text(secondaryText, style = MaterialTheme.typography.bodySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium))
        }
    }
}

/** Visuel de repli quand aucune image fiable n'existe : dégradé stable dérivé du titre, jamais une fausse affiche. */
@Composable
fun GeneratedArt(title: String, modifier: Modifier = Modifier, showTitle: Boolean = true) {
    val palettes = listOf(
        listOf(Color(0xFFF2875A), Color(0xFF7C2B4A), Color(0xFF1B1030)),
        listOf(Color(0xFF0B1B33), Color(0xFF06223A), Color(0xFF02070E)),
        listOf(Color(0xFF8FB3A5), Color(0xFF2D4A44), Color(0xFF0E1A18)),
        listOf(Color(0xFF3A1F5C), Color(0xFF140B24), Color(0xFF090D12)),
        listOf(Color(0xFF1F5D33), Color(0xFF14361F), Color(0xFF0B1D10)),
        listOf(Color(0xFFEE964B), Color(0xFF5C2A0F), Color(0xFF1A0E06)),
        listOf(Color(0xFF4D6A85), Color(0xFF16202C), Color(0xFF090D12)),
    )
    val p = palettes[abs(title.hashCode()) % palettes.size]
    Box(modifier.background(Brush.verticalGradient(p)), contentAlignment = Alignment.BottomStart) {
        if (showTitle) {
            Text(
                title.uppercase(), maxLines = 4, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold, lineHeight = 19.sp),
                color = Aditv.Text, modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Composable
fun RemoteImage(url: String?, title: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop, fallbackTitle: Boolean = true) {
    if (url == null) { GeneratedArt(title, modifier, fallbackTitle); return }
    SubcomposeAsyncImage(
        model = url, contentDescription = null, contentScale = contentScale, modifier = modifier,
        loading = { GeneratedArt(title, Modifier.fillMaxSize(), fallbackTitle) },
        error = { GeneratedArt(title, Modifier.fillMaxSize(), fallbackTitle) },
    )
}

/** Carte affiche 2:3 (films, séries). */
@Composable
fun PosterCard(title: String, imageUrl: String?, subtitle: String?, onClick: () -> Unit, cardModifier: Modifier = Modifier, width: Dp = 128.dp) {
    Column(Modifier.width(width)) {
        Card(
            onClick = onClick,
            modifier = (cardModifier.width(width).height(width * 1.5f)).navSound(),
            shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
            scale = CardDefaults.scale(focusedScale = 1.05f),
            border = CardDefaults.border(focusedBorder = FocusBorder),
        ) { RemoteImage(imageUrl, title, Modifier.fillMaxSize()) }
        Spacer(Modifier.height(8.dp))
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = Aditv.Text)
        if (subtitle != null) Text(subtitle, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary)
    }
}

/** Carte chaîne : logo fournisseur (contenu, sans recadrage) sur surface sombre. */
@Composable
fun ChannelCard(name: String, logoUrl: String?, detail: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = (modifier.width(180.dp).height(104.dp)).navSound(),
        shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
        scale = CardDefaults.scale(focusedScale = 1.05f),
        border = CardDefaults.border(border = Border(BorderStroke(1.dp, Aditv.Line), shape = RoundedCornerShape(12.dp)), focusedBorder = FocusBorder),
        colors = CardDefaults.colors(containerColor = Aditv.Surface1, focusedContainerColor = Aditv.Surface2),
    ) {
        Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp)) {
                    if (logoUrl != null) RemoteImage(logoUrl, name, Modifier.fillMaxSize(), ContentScale.Fit, fallbackTitle = false)
                    else Monogram(name, Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(10.dp))
                Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, color = Aditv.Text)
            }
            if (detail != null) Text(detail, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary)
        }
    }
}

@Composable
fun Monogram(name: String, modifier: Modifier = Modifier) {
    val letters = name.split(' ', '-', '|').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }
    Box(modifier.background(Aditv.Surface2, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
        Text(letters, style = MaterialTheme.typography.labelMedium, color = Aditv.Text)
    }
}

/** Fonction pas encore livrée : affichée honnêtement, sans faux bouton. */
@Composable
fun NotYetAvailable(title: String, phase: String, detail: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(48.dp), verticalArrangement = Arrangement.Center) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = Aditv.Text)
        Spacer(Modifier.height(12.dp))
        Text("Prévu en $phase", style = MaterialTheme.typography.labelLarge, color = Aditv.Accent)
        Spacer(Modifier.height(8.dp))
        Text(detail, style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary, modifier = Modifier.fillMaxWidth(0.6f))
    }
}

@Composable
fun StatusDot(color: Color) = Box(Modifier.size(8.dp).background(color, CircleShape))

@Composable
fun Tag(text: String, dashed: Boolean = false) {
    Text(
        text, style = MaterialTheme.typography.labelMedium, color = if (dashed) Aditv.TextSecondary else Aditv.Text,
        modifier = Modifier.border(1.dp, Aditv.TextSecondary.copy(alpha = 0.5f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * Donne le focus initial dès que l'élément est réellement attaché (quelques images après la composition),
 * sans boucle infinie : la télécommande doit toujours avoir un point de départ visible.
 */
@Composable
fun InitialFocus(
    requester: androidx.compose.ui.focus.FocusRequester,
    key: Any? = Unit,
    enabled: Boolean = true,
    /** Si fourni : on insiste tant que la cible n'a pas réellement le focus (un autre composant peut le reprendre). */
    isFocused: (() -> Boolean)? = null,
) {
    androidx.compose.runtime.LaunchedEffect(key, enabled) {
        if (!enabled) return@LaunchedEffect
        var stableFrames = 0
        repeat(60) {
            androidx.compose.runtime.withFrameNanos { }
            if (isFocused == null) {
                if (runCatching { requester.requestFocus() }.getOrDefault(false)) return@LaunchedEffect
            } else if (isFocused()) {
                if (++stableFrames >= 3) return@LaunchedEffect
            } else {
                stableFrames = 0
                runCatching { requester.requestFocus() }
            }
        }
    }
}

/** URL d'image TMDB à la taille d'affichage (jamais l'original). */
fun tmdbImage(path: String?, size: String): String? = path?.let { com.aditv.data.tmdb.TmdbClient.DEFAULT_IMAGE_BASE + size + it }

/** Affiche TMDB si correspondance confirmée, sinon visuel du fournisseur. */
fun com.aditv.data.db.CatalogCard.displayImage(): String? = tmdbImage(tmdbPoster, "w342") ?: imageUrl

/** Carte paysage avec barre de progression (Reprendre, épisodes). */
@Composable
fun ProgressCard(title: String, subtitle: String?, imageUrl: String?, progress: Float?, onClick: () -> Unit, cardModifier: Modifier = Modifier, width: Dp = 240.dp) {
    Column(Modifier.width(width)) {
        Card(
            onClick = onClick,
            modifier = (cardModifier.width(width).height(width * 9f / 16f)).navSound(),
            shape = CardDefaults.shape(RoundedCornerShape(12.dp)),
            scale = CardDefaults.scale(focusedScale = 1.05f),
            border = CardDefaults.border(focusedBorder = FocusBorder),
        ) {
            Box(Modifier.fillMaxSize()) {
                RemoteImage(imageUrl, title, Modifier.fillMaxSize(), fallbackTitle = false)
                if (progress != null) {
                    Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(10.dp).height(4.dp).background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp))) {
                        Box(Modifier.fillMaxSize(progress.coerceIn(0f, 1f)).background(Aditv.Accent, RoundedCornerShape(2.dp)))
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = Aditv.Text)
        if (subtitle != null) Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary)
    }
}

/** Programme en cours par chaîne (clé = id de carte), rafraîchi chaque minute ; vide si pas de guide. */
@Composable
fun rememberNowPlaying(accountId: String, cards: List<com.aditv.data.db.CatalogCard>): Map<String, String> {
    val c = com.aditv.ui.LocalContainer.current
    val live = cards.filter { it.type == "LIVE" }
    val guideVersion by c.epg.version.collectAsStateWithLifecycle()
    val state = androidx.compose.runtime.produceState(emptyMap<String, String>(), live.map { it.id }, guideVersion) {
        val ids = live.map { it.id to it.epgChannelId }
        while (true) {
            val now = System.currentTimeMillis()
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                c.epg.programmesFor(accountId, ids, now, now + 60_000)
                    .mapNotNull { (id, list) -> list.firstOrNull { p -> p.isLive(now) }?.let { id to it.title } }.toMap()
            }
            // Recalé sur la minute suivante (les rangées ne recalculent pas toutes en même temps qu'un import).
            kotlinx.coroutines.delay(60_000 - System.currentTimeMillis() % 60_000 + 500)
        }
    }
    return state.value
}
