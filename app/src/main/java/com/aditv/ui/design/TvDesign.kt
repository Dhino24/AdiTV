package com.aditv.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.aditv.ui.common.GeneratedArt
import com.aditv.ui.common.RemoteImage
import com.aditv.ui.theme.Aditv

/**
 * Langage visuel inspiré des interfaces TV haut de gamme (grands visuels, verre fumé, élévation au focus),
 * avec l'identité ADITV : anthracite, blanc, accent turquoise réservé aux états actifs.
 */
object Glass {
    val Panel = Brush.verticalGradient(listOf(Color(0xE6343B44), Color(0xE61C2229)))
    val Border = Color.White.copy(alpha = 0.12f)
    val Idle = Color.White.copy(alpha = 0.16f)
}

private val Shadow = Glow(elevationColor = Color.Black.copy(alpha = 0.55f), elevation = 18.dp)

/** Bouton principal en pilule (« Lire », « Reprendre ») : blanc, texte sombre. */
@Composable
fun PillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: String? = "▶", secondary: String? = null, primary: Boolean = true) {
    val shape = RoundedCornerShape(26.dp)
    Surface(
        onClick = onClick,
        modifier = modifier.height(52.dp),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Shadow),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) Color.White.copy(alpha = 0.92f) else Glass.Idle,
            contentColor = if (primary) Aditv.OnLight else Aditv.Text,
            focusedContainerColor = Color.White, focusedContentColor = Aditv.OnLight,
        ),
        border = ClickableSurfaceDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Aditv.Accent), shape = shape, inset = (-3).dp)),
    ) {
        Row(Modifier.fillMaxHeight().padding(horizontal = 28.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Text(icon, fontSize = 16.sp); Spacer(Modifier.width(10.dp)) }
            Text(text, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold))
            if (secondary != null) { Spacer(Modifier.width(8.dp)); Text(secondary, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

/** Bouton rond à icône ; son libellé apparaît dessous au focus (accessible sans deviner l'icône). */
@Composable
fun RoundIconButton(icon: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, active: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    Box(modifier.width(52.dp).height(52.dp), contentAlignment = Alignment.TopCenter) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(52.dp).onFocusChanged { focused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            glow = ClickableSurfaceDefaults.glow(focusedGlow = Shadow),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (active) Aditv.Accent.copy(alpha = 0.35f) else Glass.Idle, contentColor = Aditv.Text,
                focusedContainerColor = Color.White, focusedContentColor = Aditv.OnLight,
            ),
        ) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(icon, fontSize = 20.sp, fontWeight = FontWeight.Bold) } }
        if (focused) Text(
            label, style = MaterialTheme.typography.labelMedium, color = Aditv.Text, maxLines = 1, softWrap = false,
            modifier = Modifier.padding(top = 60.dp).width(160.dp), textAlign = TextAlign.Center,
        )
    }
}

/** Logo-titre TMDB (image) si disponible, sinon le titre en grand. */
@Composable
fun TitleArt(title: String, logoUrl: String?, modifier: Modifier = Modifier, maxWidth: Dp = 420.dp, maxHeight: Dp = 120.dp, textSizeSp: Int = 44) {
    if (logoUrl != null) {
        AsyncImage(model = logoUrl, contentDescription = title, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
            modifier = modifier.width(maxWidth).height(maxHeight))
    } else {
        Text(title, fontSize = textSizeSp.sp, fontWeight = FontWeight.ExtraBold, lineHeight = (textSizeSp + 4).sp, color = Aditv.Text,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = modifier.width(maxWidth + 120.dp))
    }
}

/**
 * Vignette paysage 16:9 : fond TMDB si disponible ; sinon l'affiche du fournisseur centrée sur un fond
 * coloré (jamais étirée). Élévation au focus, liseré blanc fin pour rester visible sans couleur.
 */
@Composable
fun LandscapeCard(
    title: String,
    backdropUrl: String?,
    logoUrl: String?,
    posterUrl: String?,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    width: Dp = 236.dp,
) {
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.width(width)) {
        Card(
            onClick = onClick,
            modifier = modifier.width(width).height(width * 9f / 16f),
            shape = CardDefaults.shape(shape),
            scale = CardDefaults.scale(focusedScale = 1.08f),
            glow = CardDefaults.glow(focusedGlow = Shadow),
            border = CardDefaults.border(focusedBorder = Border(BorderStroke(2.dp, Color.White.copy(alpha = 0.9f)), shape = shape)),
        ) {
            Box(Modifier.fillMaxSize()) {
                when {
                    backdropUrl != null -> RemoteImage(backdropUrl, title, Modifier.fillMaxSize(), fallbackTitle = false)
                    posterUrl != null -> {
                        GeneratedArt(title, Modifier.fillMaxSize(), showTitle = false)
                        RemoteImage(posterUrl, title, Modifier.fillMaxSize().padding(vertical = 8.dp), ContentScale.Fit, fallbackTitle = false)
                    }
                    else -> GeneratedArt(title, Modifier.fillMaxSize(), showTitle = false)
                }
                if (backdropUrl != null || posterUrl == null) {
                    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))))
                    Box(Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                        if (logoUrl != null) AsyncImage(model = logoUrl, contentDescription = null, contentScale = ContentScale.Fit, alignment = Alignment.BottomStart,
                            modifier = Modifier.width(width * 0.55f).height(width * 0.16f))
                        else Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (progress != null) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp).height(4.dp).background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(2.dp))) {
                    Box(Modifier.fillMaxSize(progress.coerceIn(0f, 1f)).background(Aditv.Accent, RoundedCornerShape(2.dp)))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = Aditv.Text)
        if (subtitle != null) Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary)
    }
}

/** Tuile chaîne : logo sur verre fumé, nom et programme en cours dessous. */
@Composable
fun ChannelTile(name: String, logoUrl: String?, detail: String?, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp = 196.dp) {
    val shape = RoundedCornerShape(14.dp)
    Column(Modifier.width(width)) {
        Card(
            onClick = onClick,
            modifier = modifier.width(width).height(width * 9f / 16f),
            shape = CardDefaults.shape(shape),
            scale = CardDefaults.scale(focusedScale = 1.08f),
            glow = CardDefaults.glow(focusedGlow = Shadow),
            colors = CardDefaults.colors(containerColor = Aditv.Surface2, focusedContainerColor = Color(0xFF26313C)),
            border = CardDefaults.border(
                border = Border(BorderStroke(1.dp, Glass.Border), shape = shape),
                focusedBorder = Border(BorderStroke(2.dp, Color.White.copy(alpha = 0.9f)), shape = shape),
            ),
        ) {
            Box(Modifier.fillMaxSize().padding(18.dp), contentAlignment = Alignment.Center) {
                if (logoUrl != null) RemoteImage(logoUrl, name, Modifier.fillMaxSize(), ContentScale.Fit, fallbackTitle = false)
                else Text(name, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold), color = Aditv.Text, textAlign = TextAlign.Center, maxLines = 2)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, color = Aditv.Text)
        if (detail != null) Text(detail, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary)
    }
}

/** Titre de rangée façon TV : sobre, gras, aligné sur la marge. */
@Composable
fun RowTitle(text: String, hint: String? = null, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(text, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = Aditv.Text)
        if (hint != null) { Spacer(Modifier.width(12.dp)); Text(hint, style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary) }
    }
}

/** Indicateur de pages du carrousel. */
@Composable
fun PageDots(count: Int, index: Int, modifier: Modifier = Modifier) {
    if (count <= 1) return
    Row(modifier.background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { i ->
            Box(Modifier.size(width = if (i == index) 18.dp else 6.dp, height = 6.dp).background(if (i == index) Color.White else Color.White.copy(alpha = 0.45f), RoundedCornerShape(3.dp)))
        }
    }
}
