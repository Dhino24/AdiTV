package com.aditv.ui.design

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.aditv.ui.common.RemoteImage
import com.aditv.ui.theme.Aditv

/**
 * « Vedette » de la direction cinéma : ce que l'écran met en avant (élément sélectionné à la télécommande).
 * Le fond plein écran et le grand titre en haut suivent la sélection, à la manière d'une salle de cinéma.
 */
data class Spot(
    val key: String,
    val title: String,
    val kicker: String? = null,
    val meta: List<String> = emptyList(),
    val overview: String? = null,
    val backdrop: String? = null,
    val poster: String? = null,
    val logo: String? = null,
    val lang: String? = null,
    val quality: Int? = null,
    val live: Boolean = false,
    /** Clé YouTube de la bande-annonce TMDB (fond vidéo muet sur le Cube). */
    val trailer: String? = null,
)

/** Fond plein écran de la vedette, en fondu enchaîné ; voiles à gauche et en bas pour la lisibilité. */
@Composable
fun CinemaBackdrop(spot: Spot?, reduced: Boolean, dim: Float = 0f) {
    Box(Modifier.fillMaxSize()) {
        // Fondu seulement quand l'image change (pas quand le résumé arrive pour la même vedette).
        val key = spot?.let { it.backdrop ?: it.poster ?: it.key }
        val byKey = androidx.compose.runtime.remember { LinkedHashMap<String?, Spot?>() }
        byKey[key] = spot
        while (byKey.size > 12) byKey.remove(byKey.keys.first())
        // Bande-annonce de fond en lecture : le visuel n'est plus dessiné (il masquerait la vidéo sur Fire TV).
        if (!com.aditv.ui.common.VideoHole.backgroundPlaying) Crossfade(targetState = key, animationSpec = tween(if (reduced) 0 else 450), label = "fond") { k ->
            val s = byKey[k]
            Box(Modifier.fillMaxSize()) {
                when {
                    s?.backdrop != null -> RemoteImage(s.backdrop, s.title, Modifier.fillMaxSize(), fallbackTitle = false)
                    s?.poster != null -> SideBackdrop(0.55f) { RemoteImage(s.poster, s.title, Modifier.fillMaxSize(), ContentScale.Crop, fallbackTitle = false) }
                    s != null -> SideBackdrop(0.6f) { ComposedArt(s.title, null, Modifier.fillMaxSize(), showTitle = false) }
                }
            }
        }
        TrailerBackground(spot?.trailer, spot?.key)
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Aditv.Bg, 0.28f to Aditv.Bg.copy(alpha = 0.82f), 0.55f to Aditv.Bg.copy(alpha = 0.25f), 0.8f to Color.Transparent)))
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Transparent, 0.62f to Aditv.Bg.copy(alpha = 0.7f), 0.85f to Aditv.Bg)))
        if (dim > 0f) Box(Modifier.fillMaxSize().background(Aditv.Bg.copy(alpha = dim)))
    }
}

/** Grand titre de la vedette (logo-titre TMDB s'il existe), infos, badges et résumé. */
@Composable
fun SpotInfo(spot: Spot?, modifier: Modifier = Modifier) {
    if (spot == null) return
    Column(modifier.width(560.dp)) {
        spot.kicker?.let { Kicker(it, color = if (spot.live) Aditv.Live else Aditv.TextSecondary) }
        Spacer(Modifier.height(8.dp))
        // Hauteur bornée (les rangées commencent juste en dessous) : titre sur 2 lignes → résumé sur 1 ligne.
        var titleLines by androidx.compose.runtime.remember(spot.title) { androidx.compose.runtime.mutableIntStateOf(1) }
        if (spot.logo != null) DisplayTitle(spot.title, spot.logo, size = 34.sp, maxWidth = 540.dp)
        else Text(
            spot.title.uppercase(), fontFamily = com.aditv.ui.theme.Outfit, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
            fontSize = 34.sp, lineHeight = 33.sp, letterSpacing = 0.5.sp, color = Aditv.Text, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 540.dp), onTextLayout = { titleLines = it.lineCount },
        )
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (spot.meta.isNotEmpty()) MetaLine(spot.meta)
            if (spot.lang != null || spot.quality != null) { Spacer(Modifier.width(10.dp)); VariantBadges(spot.lang, spot.quality) }
        }
        spot.overview?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 12.sp, lineHeight = 17.sp), color = Aditv.Text.copy(alpha = 0.9f), maxLines = if (spot.logo != null) 2 else if (titleLines > 1) 1 else 3, overflow = TextOverflow.Ellipsis)
        }
    }
}


/**
 * Bande-annonce muette en boucle derrière la vedette (Cube) : démarre après 3 s sur le même titre, apparaît en fondu
 * quand la lecture a réellement commencé ; rien si la vidéo refuse l'intégration.
 */
@Composable
fun TrailerBackground(trailer: String?, spotKey: String?) {
    if (!com.aditv.core.DeviceProfile.trailerBackground || trailer == null) return
    var ready by androidx.compose.runtime.remember(spotKey) { androidx.compose.runtime.mutableStateOf(false) }
    var visible by androidx.compose.runtime.remember(spotKey) { androidx.compose.runtime.mutableStateOf(false) }
    var failed by androidx.compose.runtime.remember(spotKey) { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(spotKey, trailer) { ready = false; kotlinx.coroutines.delay(3_000); ready = true }
    androidx.compose.runtime.DisposableEffect(spotKey) { onDispose { com.aditv.ui.common.VideoHole.backgroundPlaying = false } }
    // Pas de transparence sur le WebView lui-même (la vidéo ne la suivrait pas) : un voile au-dessus s'efface en fondu.
    val veil by androidx.compose.animation.core.animateFloatAsState(if (visible) 0f else 1f, tween(700), label = "bande-annonce")
    if (ready && !failed) {
        com.aditv.ui.common.TrailerEmbed(
            trailer, muted = true, loop = true, modifier = Modifier.fillMaxSize(),
            onPlaying = { visible = true; com.aditv.ui.common.VideoHole.backgroundPlaying = true },
            onError = { failed = true; com.aditv.ui.common.VideoHole.backgroundPlaying = false },
        )
        if (veil > 0f) Box(Modifier.fillMaxSize().background(Aditv.Bg.copy(alpha = veil)))
    }
}


