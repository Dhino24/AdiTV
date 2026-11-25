package com.aditv.ui.design

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.aditv.ui.theme.Aditv
import com.aditv.ui.theme.Outfit

/**
 * Notification ADI TV (rappel, but…) : carte de verre qui glisse depuis la droite, liseré et pastille d'icône
 * colorés (turquoise = rappel, corail = direct / but, avec pulsation), surtitre en petites capitales, titre en grand,
 * touche de télécommande dessinée (« ≡ Regarder »), barre fine qui se vide jusqu'à la fermeture automatique.
 */
@Composable
fun AditvNotification(
    kicker: String, title: String, subtitle: String?, @DrawableRes icon: Int, accent: Color, lifetimeMs: Long,
    actionKey: String? = "≡", actionLabel: String? = "Regarder", pulse: Boolean = false, modifier: Modifier = Modifier, key: Any = title,
) {
    var shown by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) { shown = true }
    val remaining = remember(key) { Animatable(1f) }
    LaunchedEffect(key) { remaining.animateTo(0f, tween(lifetimeMs.toInt(), easing = LinearEasing)) }
    val reduced = LocalReducedMotion.current
    AnimatedVisibility(
        shown, modifier,
        enter = if (reduced) fadeIn() else slideInHorizontally(spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessMediumLow)) { it / 2 } + fadeIn(tween(250)),
    ) {
        Box(Modifier.width(430.dp).clip(RoundedCornerShape(16.dp)).liquidGlass(RoundedCornerShape(16.dp), strength = 2.3f)) {
            // Liseré coloré à gauche.
            Box(Modifier.width(4.dp).height(96.dp).background(Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.35f)))))
            Row(Modifier.padding(start = 18.dp, end = 16.dp, top = 14.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                    if (pulse && !reduced) {
                        val t = rememberInfiniteTransition(label = "pulse")
                        val s by t.animateFloat(1f, 1.45f, infiniteRepeatable(tween(1_200), RepeatMode.Restart), label = "s")
                        val a by t.animateFloat(0.55f, 0f, infiniteRepeatable(tween(1_200), RepeatMode.Restart), label = "a")
                        Box(Modifier.size(40.dp).graphicsLayer { scaleX = s; scaleY = s; alpha = a }.background(accent, CircleShape))
                    }
                    Box(Modifier.size(40.dp).background(Brush.linearGradient(listOf(accent, accent.copy(alpha = 0.7f))), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(painterResource(icon), contentDescription = null, tint = Aditv.OnLight, modifier = Modifier.size(19.dp))
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(kicker.uppercase(), fontSize = 7.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp, color = accent, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(3.dp))
                    Text(title, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Aditv.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (subtitle != null || actionKey != null) {
                        Spacer(Modifier.height(7.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (actionKey != null) {
                                // Touche de télécommande dessinée.
                                Box(Modifier.border(1.dp, Aditv.Text.copy(alpha = 0.55f), RoundedCornerShape(5.dp)).padding(horizontal = 6.dp, vertical = 1.dp)) {
                                    Text(actionKey, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Aditv.Text)
                                }
                                actionLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = Aditv.Text) }
                            }
                            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Aditv.TextTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        }
                    }
                }
            }
            // Temps restant avant fermeture automatique.
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(2.dp).background(Color.White.copy(alpha = 0.06f))) {
                Box(Modifier.fillMaxWidth(remaining.value).fillMaxHeight().background(accent.copy(alpha = 0.8f)))
            }
        }
    }
}
