package com.aditv.ui.design

import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.aditv.R
import com.aditv.data.variants.VariantParser
import com.aditv.ui.common.RemoteImage
import com.aditv.ui.theme.Aditv
import com.aditv.ui.theme.Outfit
import kotlin.math.abs

/*
 * Système de design ADITV, reporté du prototype validé (design/prototype) :
 * - focus évident : l'élément devient blanc (boutons) ou s'entoure d'un liseré blanc et d'un halo turquoise (cartes),
 *   légèrement agrandi ; transitions courtes ;
 * - boutons au repos en verre fumé ; cartes au format cinémascope, alignées ;
 * - turquoise réservé aux états (focus, progression, sélection, VF).
 */

object Glass {
    val Idle = Color.White.copy(alpha = 0.13f)
    val Border = Color.White.copy(alpha = 0.14f)
    val Panel = Color(0xEB0E131A)
}

/** Animations réduites (réglage système « échelle d'animation » à 0) : pas de défilement automatique ni d'animation décorative. */
val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
fun systemReducedMotion(): Boolean {
    val ctx = LocalContext.current
    return remember { runCatching { Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false) }
}

private val Halo = Glow(elevationColor = Aditv.Accent.copy(alpha = 0.32f), elevation = 10.dp)
private val Lift = Glow(elevationColor = Color.Black.copy(alpha = 0.6f), elevation = 14.dp)

/** Bouton en pilule : principal (blanc) ou secondaire (verre fumé). Au focus : blanc plein, halo turquoise. */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes icon: Int? = R.drawable.ic_play,
    secondary: String? = null,
    primary: Boolean = true,
    small: Boolean = false,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(if (small) 27.dp else 33.dp),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Halo),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (primary) Color.White.copy(alpha = 0.92f) else Glass.Idle,
            contentColor = if (primary) Aditv.OnLight else Aditv.Text,
            focusedContainerColor = Color.White, focusedContentColor = Aditv.OnLight,
            disabledContainerColor = Glass.Idle.copy(alpha = 0.06f), disabledContentColor = Aditv.TextTertiary,
        ),
        border = ClickableSurfaceDefaults.border(
            border = if (primary) Border.None else Border(BorderStroke(0.5.dp, Glass.Border), shape = shape),
            focusedBorder = Border(BorderStroke(2.dp, Aditv.Accent.copy(alpha = 0.6f)), shape = shape, inset = (-2).dp),
        ),
    ) {
        Row(Modifier.fillMaxHeight().padding(horizontal = if (small) 12.dp else 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(if (small) 11.dp else 13.dp)); Spacer(Modifier.width(7.dp)) }
            Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = if (small) 10.sp else 11.sp), maxLines = 1)
            if (secondary != null) { Spacer(Modifier.width(6.dp)); Text(secondary, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Medium, fontSize = 10.sp), modifier = Modifier.padding(start = 0.dp)) }
        }
    }
}

/** Bouton rond à icône ; son libellé apparaît dessous au focus. */
@Composable
fun RoundButton(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    size: Dp = 33.dp,
    overlay: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Box(modifier.size(size), contentAlignment = Alignment.TopCenter) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(size).onFocusChanged { focused = it.isFocused },
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            glow = ClickableSurfaceDefaults.glow(focusedGlow = Halo),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Glass.Idle, contentColor = if (active) Aditv.Accent else Aditv.Text,
                focusedContainerColor = Color.White, focusedContentColor = if (active) Color(0xFF007D7D) else Aditv.OnLight,
            ),
            border = ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(0.5.dp, Glass.Border), shape = CircleShape),
                focusedBorder = Border(BorderStroke(2.dp, Aditv.Accent.copy(alpha = 0.6f)), shape = CircleShape, inset = (-2).dp),
            ),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(painterResource(icon), contentDescription = label, modifier = Modifier.size(size * 0.42f))
                if (overlay != null) Text(overlay, fontSize = (size.value * 0.19f).sp, fontWeight = FontWeight.Bold, modifier = Modifier.offset(y = 1.dp))
            }
        }
        if (focused) Text(
            label, style = MaterialTheme.typography.labelMedium, color = Aditv.Text, maxLines = 1, softWrap = false, textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = size + 7.dp).width(140.dp),
        )
    }
}

/** Puce de filtre (genres, saisons, catégories) : sélection soulignée de turquoise, focus blanc. */
@Composable
fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Surface(
        onClick = onClick,
        modifier = modifier.height(25.dp),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.07f),
        glow = ClickableSurfaceDefaults.glow(focusedGlow = Halo),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.06f),
            contentColor = if (selected) Aditv.Text else Aditv.TextSecondary,
            focusedContainerColor = Color.White, focusedContentColor = Aditv.OnLight,
        ),
    ) {
        Box(
            Modifier.fillMaxHeight().then(if (selected) Modifier.drawBehind {
                drawLine(Aditv.Accent, Offset(size.height / 2, size.height - 1.dp.toPx()), Offset(size.width - size.height / 2, size.height - 1.dp.toPx()), 1.5.dp.toPx())
            } else Modifier).padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) { Text(text, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, fontSize = 10.sp), maxLines = 1) }
    }
}

/** Surtitre : trait turquoise + capitales espacées. */
@Composable
fun Kicker(text: String, modifier: Modifier = Modifier, color: Color = Aditv.TextSecondary) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp, 1.5.dp).background(Aditv.Accent, RoundedCornerShape(1.dp)))
        Spacer(Modifier.width(7.dp))
        Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
    }
}

/** Titre d'affiche : logo-titre TMDB s'il existe, sinon le titre en capitales Outfit. */
@Composable
fun DisplayTitle(title: String, logoUrl: String?, modifier: Modifier = Modifier, size: TextUnit = 48.sp, maxWidth: Dp = 460.dp) {
    if (logoUrl != null) AsyncImage(
        model = logoUrl, contentDescription = title, contentScale = ContentScale.Fit, alignment = Alignment.CenterStart,
        modifier = modifier.widthIn(max = maxWidth).width(maxWidth * 0.8f).height(size.value.dp * 2f),
    ) else Text(
        title.uppercase(), fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = size, lineHeight = size * 0.98f,
        letterSpacing = 0.5.sp, color = Aditv.Text, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = modifier.widthIn(max = maxWidth),
    )
}

/** Badge de qualité ou de langue ; « VF » en turquoise plein. */
@Composable
fun Badge(text: String, filled: Boolean = false) {
    Text(
        text, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 8.sp, letterSpacing = 0.3.sp),
        color = if (filled) Color(0xFF071214) else Aditv.Text, maxLines = 1,
        modifier = (if (filled) Modifier.background(Aditv.Accent, RoundedCornerShape(3.dp))
        else Modifier.border(0.75.dp, Color.White.copy(alpha = 0.45f), RoundedCornerShape(3.dp))).padding(horizontal = 5.dp, vertical = 1.5.dp),
    )
}

/** Badges dérivés du titre fournisseur (langue et qualité « annoncées »). */
@Composable
fun VariantBadges(lang: String?, quality: Int?) {
    VariantParser.qualityLabel(quality)?.let { Badge(it) }
    VariantParser.languageLabel(lang)?.let { Badge(it, filled = lang == "fr") }
}

/** Ligne d'informations séparées par des points, suivie de badges éventuels. */
@Composable
fun MetaLine(parts: List<String>, modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyLarge, badges: @Composable RowScope.() -> Unit = {}) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        parts.forEachIndexed { i, p ->
            if (i > 0) Box(Modifier.size(2.5.dp).background(Aditv.TextTertiary, CircleShape))
            Text(p, style = style, color = Aditv.TextSecondary, maxLines = 1)
        }
        badges()
    }
}

/** Barre de reprise + libellé (« 42 min restantes »). */
@Composable
fun ResumeBar(fraction: Float, label: String?, modifier: Modifier = Modifier, width: Dp = 140.dp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(width).height(2.5.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.22f))) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(Aditv.Accent))
        }
        if (label != null) { Spacer(Modifier.width(9.dp)); Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium, fontSize = 9.sp), color = Aditv.TextSecondary) }
    }
}

/** Petite barre de progression à l'intérieur d'une carte. */
@Composable
fun BoxScope.CardProgress(fraction: Float) {
    Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 8.dp, vertical = 7.dp).height(2.5.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.28f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(Aditv.Accent))
    }
}

/** Pastille « EN DIRECT ». */
@Composable
fun LiveTag(modifier: Modifier = Modifier) {
    Row(modifier.background(Color(0xB80A0C10), RoundedCornerShape(50)).padding(horizontal = 6.dp, vertical = 2.5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(4.dp).background(Aditv.Live, CircleShape))
        Spacer(Modifier.width(4.dp))
        Text("EN DIRECT", fontSize = 6.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp, color = Color.White)
    }
}

/** Format des cartes : cinémascope (2,39:1) comme le prototype. */
const val CINEMASCOPE = 2.39f

/**
 * Carte cinémascope : fond TMDB recadré ; sans fond, l'affiche du fournisseur à droite sur un dégradé sombre
 * et le titre composé en Outfit à gauche (jamais une image étirée ni une fausse affiche).
 */
@Composable
fun WideCard(
    title: String,
    subtitle: String?,
    backdropUrl: String?,
    posterUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = 207.dp,
    aspect: Float = CINEMASCOPE,
    progress: Float? = null,
    logoUrl: String? = null,
    live: Boolean = false,
    channelMark: String? = null,
    topRight: (@Composable BoxScope.() -> Unit)? = null,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(7.dp)
    Column(Modifier.width(width)) {
        Card(
            onClick = onClick,
            modifier = modifier.width(width).height(width / aspect).onFocusChanged { focused = it.isFocused },
            shape = CardDefaults.shape(shape),
            scale = CardDefaults.scale(focusedScale = 1.06f),
            glow = CardDefaults.glow(glow = Glow.None, focusedGlow = Halo),
            colors = CardDefaults.colors(containerColor = Aditv.Surface2),
            border = CardDefaults.border(
                border = Border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.06f)), shape = shape),
                focusedBorder = Border(BorderStroke(1.5.dp, Aditv.Text), shape = shape),
            ),
        ) {
            Box(Modifier.fillMaxSize()) {
                if (backdropUrl != null) {
                    RemoteImage(backdropUrl, title, Modifier.fillMaxSize(), fallbackTitle = false)
                    if (logoUrl != null || live) Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.6f))))
                    if (logoUrl != null) AsyncImage(
                        model = logoUrl, contentDescription = null, contentScale = ContentScale.Fit, alignment = Alignment.BottomStart,
                        modifier = Modifier.align(Alignment.BottomStart).padding(start = 9.dp, bottom = if (progress != null) 14.dp else 9.dp).width(width * 0.45f).height(width / aspect * 0.32f),
                    )
                } else ComposedArt(title, posterUrl, Modifier.fillMaxSize(), titleSize = (width.value / 12.5f).sp)
                if (live) LiveTag(Modifier.align(Alignment.TopStart).padding(7.dp))
                if (channelMark != null) Text(
                    channelMark.uppercase(), fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 7.5.sp, letterSpacing = 0.8.sp, color = Color.White,
                    maxLines = 1, modifier = Modifier.align(Alignment.TopEnd).padding(7.dp),
                )
                topRight?.invoke(this)
                if (progress != null) CardProgress(progress)
            }
        }
        Column(Modifier.padding(start = 2.dp, top = if (focused) 11.dp else 7.dp)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium.copy(fontSize = 10.sp), color = Aditv.Text)
            if (subtitle != null) Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall.copy(fontSize = 8.5.sp), color = if (focused) Aditv.TextSecondary else Aditv.TextTertiary, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** Composition de repli : dégradé sombre stable, affiche du fournisseur à droite, titre en Outfit à gauche. */
@Composable
fun ComposedArt(title: String, posterUrl: String?, modifier: Modifier, titleSize: TextUnit = 16.sp, showTitle: Boolean = true) {
    val tints = listOf(Color(0xFF16323A), Color(0xFF2A1E36), Color(0xFF33261A), Color(0xFF1A2A3D), Color(0xFF1E3326), Color(0xFF3A1E24))
    val tint = tints[abs(title.hashCode()) % tints.size]
    Box(modifier.background(Brush.linearGradient(listOf(tint, Aditv.Surface1), start = Offset(0f, 0f), end = Offset(600f, 300f)))) {
        if (posterUrl != null) {
            RemoteImage(posterUrl, title, Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.42f), ContentScale.Crop, fallbackTitle = false)
            Box(Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.42f).background(Brush.horizontalGradient(listOf(Color(0xFF0E131A), Color.Transparent), endX = 120f)))
        }
        if (showTitle) Text(
            title.uppercase(), fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = titleSize, lineHeight = titleSize * 1.02f,
            color = Aditv.Text, maxLines = 3, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 10.dp, end = 4.dp).fillMaxWidth(if (posterUrl != null) 0.6f else 0.9f),
        )
    }
}

/** Carte affiche 2:3 (affiches du fournisseur ou TMDB). */
@Composable
fun PosterTile(title: String, subtitle: String?, posterUrl: String?, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp = 118.dp, progress: Float? = null) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(7.dp)
    Column(Modifier.width(width)) {
        Card(
            onClick = onClick,
            modifier = modifier.width(width).height(width * 1.5f).onFocusChanged { focused = it.isFocused },
            shape = CardDefaults.shape(shape),
            scale = CardDefaults.scale(focusedScale = 1.06f),
            glow = CardDefaults.glow(glow = Glow.None, focusedGlow = Halo),
            colors = CardDefaults.colors(containerColor = Aditv.Surface2),
            border = CardDefaults.border(focusedBorder = Border(BorderStroke(1.5.dp, Aditv.Text), shape = shape)),
        ) {
            Box(Modifier.fillMaxSize()) {
                if (posterUrl != null) RemoteImage(posterUrl, title, Modifier.fillMaxSize(), fallbackTitle = false)
                else ComposedArt(title, null, Modifier.fillMaxSize(), titleSize = 13.sp)
                if (progress != null) CardProgress(progress)
            }
        }
        Column(Modifier.padding(start = 2.dp, top = if (focused) 11.dp else 7.dp)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium.copy(fontSize = 10.sp), color = Aditv.Text)
            if (subtitle != null) Text(subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall.copy(fontSize = 8.5.sp), color = if (focused) Aditv.TextSecondary else Aditv.TextTertiary, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** Tuile chaîne : logo du fournisseur sur fond sombre (ou nom en capitales), numéro et programme en cours dessous. */
@Composable
fun ChannelTile(name: String, logoUrl: String?, detail: String?, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp = 120.dp, number: Int? = null, favorite: Boolean = false) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(7.dp)
    Column(Modifier.width(width)) {
        Card(
            onClick = onClick,
            modifier = modifier.width(width).height(width * 9f / 16f).onFocusChanged { focused = it.isFocused },
            shape = CardDefaults.shape(shape),
            scale = CardDefaults.scale(focusedScale = 1.06f),
            glow = CardDefaults.glow(glow = Glow.None, focusedGlow = Halo),
            colors = CardDefaults.colors(containerColor = Aditv.Surface2, focusedContainerColor = Aditv.Surface3),
            border = CardDefaults.border(
                border = Border(BorderStroke(0.5.dp, Aditv.Line), shape = shape),
                focusedBorder = Border(BorderStroke(1.5.dp, Aditv.Text), shape = shape),
            ),
        ) {
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Color(0xFF22303E), Color(0xFF10161D)))).padding(12.dp), contentAlignment = Alignment.Center) {
                if (logoUrl != null) RemoteImage(logoUrl, name, Modifier.fillMaxSize(), ContentScale.Fit, fallbackTitle = false)
                else Text(name.uppercase(), fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, letterSpacing = 0.8.sp, color = Aditv.Text, textAlign = TextAlign.Center, maxLines = 2)
                if (favorite) Icon(painterResource(R.drawable.ic_star), contentDescription = "Favori", tint = Aditv.Accent, modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp).size(9.dp))
            }
        }
        Column(Modifier.padding(start = 2.dp, top = if (focused) 10.dp else 6.dp)) {
            Text(listOfNotNull(number?.toString(), name).joinToString(" · "), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium.copy(fontSize = 9.5.sp), color = Aditv.Text)
            if (detail != null) Text(detail, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall.copy(fontSize = 8.5.sp), color = Aditv.TextTertiary)
        }
    }
}

/** Titre de rangée. */
@Composable
fun RowTitle(text: String, hint: String? = null, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.Bottom) {
        Text(text, style = MaterialTheme.typography.titleLarge, color = Aditv.Text)
        if (hint != null) { Spacer(Modifier.width(8.dp)); Text(hint, style = MaterialTheme.typography.bodySmall, color = Aditv.TextTertiary, modifier = Modifier.padding(bottom = 1.dp)) }
    }
}

/** Indicateur de pages du carrousel. */
@Composable
fun PageDots(count: Int, index: Int, modifier: Modifier = Modifier) {
    if (count <= 1) return
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        repeat(count) { i ->
            Box(Modifier.size(width = if (i == index) 15.dp else 4.5.dp, height = 4.5.dp).background(if (i == index) Aditv.Text else Color.White.copy(alpha = 0.32f), RoundedCornerShape(3.dp)))
        }
    }
}

/** État vide ou erreur : pictogramme, titre, explication, actions. */
@Composable
fun StateBox(
    @DrawableRes icon: Int,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    error: Boolean = false,
    code: String? = null,
    centered: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier.widthIn(max = 420.dp), horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
        Box(Modifier.size(52.dp).background(Color.White.copy(alpha = 0.05f), CircleShape).border(0.5.dp, Aditv.Line, CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = if (error) Aditv.Error else Aditv.TextSecondary, modifier = Modifier.size(23.dp))
        }
        Spacer(Modifier.height(15.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, color = Aditv.Text, textAlign = if (centered) TextAlign.Center else TextAlign.Start)
        Spacer(Modifier.height(7.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 10.5.sp, lineHeight = 15.sp), color = Aditv.TextSecondary, textAlign = if (centered) TextAlign.Center else TextAlign.Start)
        if (code != null) { Spacer(Modifier.height(5.dp)); Text(code, style = MaterialTheme.typography.bodySmall, color = Aditv.TextTertiary) }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), content = actions)
    }
}

/** Carte fantôme pendant un chargement (scintillement désactivé si les animations sont réduites). */
@Composable
fun SkeletonCard(width: Dp, aspect: Float = CINEMASCOPE) {
    val reduced = LocalReducedMotion.current
    val shift = if (reduced) 0.5f else rememberInfiniteTransition(label = "skel").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1300, easing = LinearEasing), RepeatMode.Restart), label = "skel-x",
    ).value
    val brush = Brush.linearGradient(
        listOf(Color(0xFF121820), Color(0xFF1B232E), Color(0xFF121820)),
        start = Offset(-400f + 1200f * shift, 0f), end = Offset(-100f + 1200f * shift, 0f),
    )
    Column(Modifier.width(width)) {
        Box(Modifier.width(width).height(width / aspect).clip(RoundedCornerShape(7.dp)).background(brush))
        Box(Modifier.padding(top = 9.dp).width(width * 0.64f).height(9.dp).clip(RoundedCornerShape(3.dp)).background(brush))
        Box(Modifier.padding(top = 5.dp).width(width * 0.4f).height(7.dp).clip(RoundedCornerShape(3.dp)).background(brush))
    }
}

/** Option de panneau latéral (pistes, versions, réglages) : coche, libellé, précision. */
@Composable
fun SheetOption(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, checked: Boolean? = null, detail: String? = null, switch: Boolean? = null) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(35.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(7.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, contentColor = Aditv.Text, focusedContainerColor = Color.White, focusedContentColor = Aditv.OnLight),
    ) {
        Row(Modifier.fillMaxSize().padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            if (checked != null) {
                Box(Modifier.width(18.dp)) { if (checked) Icon(painterResource(R.drawable.ic_check), contentDescription = "Choisi", tint = Aditv.Accent, modifier = Modifier.size(13.dp)) }
            }
            Text(text, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium, fontSize = 10.5.sp), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = Aditv.TextTertiary, maxLines = 1)
            if (switch != null) Box(Modifier.padding(start = 8.dp).size(27.dp, 15.dp).background(if (switch) Aditv.Accent else Color.White.copy(alpha = 0.2f), RoundedCornerShape(50))) {
                Box(Modifier.align(if (switch) Alignment.CenterEnd else Alignment.CenterStart).padding(2.dp).size(11.dp).background(Color.White, CircleShape))
            }
        }
    }
}

/** Panneau latéral en verre fumé (droite de l'écran). */
@Composable
fun SideSheet(title: String, lead: String?, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier.fillMaxHeight().width(320.dp).background(Glass.Panel).padding(horizontal = 30.dp, vertical = 40.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium.copy(fontSize = 20.sp), color = Aditv.Text)
        if (lead != null) { Spacer(Modifier.height(6.dp)); Text(lead, style = MaterialTheme.typography.bodyMedium.copy(fontSize = 9.5.sp, lineHeight = 14.sp), color = Aditv.TextSecondary) }
        Spacer(Modifier.height(12.dp))
        content()
    }
}

/** Intitulé de section dans un panneau (« AUDIO », « SOUS-TITRES »). */
@Composable
fun SheetHeader(text: String) = Text(
    text.uppercase(), style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.6.sp), color = Aditv.TextTertiary,
    modifier = Modifier.padding(top = 14.dp, bottom = 5.dp, start = 2.dp),
)

/** Cadre de carte focalisable (liseré blanc + halo turquoise au focus) pour un contenu libre. */
@Composable
fun CardFrame(onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp = 207.dp, aspect: Float = CINEMASCOPE, content: @Composable BoxScope.() -> Unit) {
    val shape = RoundedCornerShape(7.dp)
    Card(
        onClick = onClick,
        modifier = modifier.width(width).height(width / aspect),
        shape = CardDefaults.shape(shape),
        scale = CardDefaults.scale(focusedScale = 1.06f),
        glow = CardDefaults.glow(glow = Glow.None, focusedGlow = Halo),
        colors = CardDefaults.colors(containerColor = Aditv.Surface2),
        border = CardDefaults.border(
            border = Border(BorderStroke(0.5.dp, Color.White.copy(alpha = 0.06f)), shape = shape),
            focusedBorder = Border(BorderStroke(1.5.dp, Aditv.Text), shape = shape),
        ),
    ) { Box(Modifier.fillMaxSize(), content = content) }
}


/** Barre de progression fine, pleine largeur (aperçus, bandeaux). */
@Composable
fun CardProgressBox(fraction: Float) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 9.dp, vertical = 8.dp).height(2.5.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.28f))) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(fraction.coerceIn(0f, 1f)).background(Aditv.Accent))
    }
}

/** Visuel de fond partiel (affiche, logo) à droite de l'écran, fondu vers l'anthracite sur son bord gauche. */
@Composable
fun BoxScope.SideBackdrop(widthFraction: Float, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.align(Alignment.TopEnd).fillMaxHeight().fillMaxWidth(widthFraction)) {
        content()
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(0f to Aditv.Bg, 0.45f to Aditv.Bg.copy(alpha = 0.55f), 0.8f to Color.Transparent)))
    }
}
