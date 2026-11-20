package com.aditv.ui.sport

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.aditv.R
import com.aditv.data.sport.UpcomingMatch
import com.aditv.ui.common.InitialFocus
import com.aditv.ui.design.CardFrame
import com.aditv.ui.design.Kicker
import com.aditv.ui.design.LiveTag
import com.aditv.ui.design.PillButton
import com.aditv.ui.design.PosterTile
import com.aditv.ui.theme.Aditv
import com.aditv.ui.theme.Outfit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dayFmt = SimpleDateFormat("EEE d MMM", Locale.FRANCE)
private val timeFmt = SimpleDateFormat("HH:mm", Locale.FRANCE)

/** « Aujourd'hui 21:00 », « Demain 18:30 », « sam. 23 nov. 20:45 ». */
fun matchWhen(start: Long, now: Long = System.currentTimeMillis()): String {
    val cal = java.util.Calendar.getInstance()
    fun day(t: Long): Int { cal.timeInMillis = t; return cal.get(java.util.Calendar.YEAR) * 400 + cal.get(java.util.Calendar.DAY_OF_YEAR) }
    val d = day(start) - day(now)
    val label = when (d) { 0 -> "Aujourd'hui"; 1 -> "Demain"; else -> dayFmt.format(Date(start)).replaceFirstChar { it.uppercase() } }
    return "$label · ${timeFmt.format(Date(start))}"
}

/** Carte de match (titre du guide, chaîne retenue, équipes suivies) ; cloche si un rappel est programmé. */
@Composable
fun MatchCard(m: UpcomingMatch, reminded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, width: Dp = 207.dp) {
    var focused by remember { mutableStateOf(false) }
    val now = System.currentTimeMillis()
    val live = m.isLive(now)
    Column(Modifier.width(width)) {
        CardFrame(onClick, modifier.onFocusChanged { focused = it.isFocused }, width = width) {
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF123238), Color(0xFF0E131A), Color(0xFF151C25)))))
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(if (live) "EN DIRECT" else matchWhen(m.startUtc, now).uppercase(), fontSize = 7.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                    color = if (live) Aditv.Live else Aditv.Accent)
                Spacer(Modifier.height(4.dp))
                Text(m.title, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 14.sp, color = Aditv.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (live) LiveTag(Modifier.align(Alignment.BottomStart).padding(8.dp))
            if (reminded) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(17.dp).background(Color(0xB80A0C10), CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_bell_on), contentDescription = "Rappel programmé", tint = Aditv.Accent, modifier = Modifier.size(10.dp))
            }
        }
        Column(Modifier.padding(start = 2.dp, top = if (focused) 11.dp else 7.dp)) {
            Text(m.channelName, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium.copy(fontSize = 10.sp), color = Aditv.Text)
            Text(m.follows.joinToString(" · ") + if (m.notLive) " · rediffusion ou magazine" else "", maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 8.5.sp), color = if (focused) Aditv.TextSecondary else Aditv.TextTertiary, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

/** Affiche du Top 10 précédée de son rang en grand chiffre détouré. */
@Composable
fun RankedTile(rank: Int, title: String, subtitle: String?, posterUrl: String?, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.Top) {
        // Le chiffre déborde légèrement sous l'affiche, sans jamais la masquer.
        Box(Modifier.width(if (rank >= 10) 96.dp else 52.dp).height(156.dp), contentAlignment = Alignment.BottomEnd) {
            Text(
                rank.toString(), fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 96.sp, lineHeight = 96.sp,
                style = TextStyle(drawStyle = Stroke(width = 4f)), color = Color.White.copy(alpha = 0.78f),
                maxLines = 1, softWrap = false, letterSpacing = (-4).sp,
                modifier = Modifier.wrapContentWidth(Alignment.End, unbounded = true).offset(x = 3.dp, y = 16.dp),
            )
        }
        PosterTile(title, subtitle, posterUrl, onClick, width = 104.dp)
    }
}

/** Choix sur un match à venir : rappel (créer / supprimer), regarder la chaîne, fermer. */
@Composable
fun MatchDialog(m: UpcomingMatch, reminded: Boolean, leadMinutes: Int, onToggleReminder: () -> Unit, onWatch: () -> Unit, onClose: () -> Unit) {
    val first = remember { FocusRequester() }
    InitialFocus(first, key = m.key)
    BackHandler { onClose() }
    Box(Modifier.fillMaxSize().background(Color(0x9E030508)), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 470.dp).background(Color(0xEB10151C), RoundedCornerShape(14.dp)).padding(horizontal = 28.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Kicker(m.follows.joinToString(" · "))
            Text(m.title, style = MaterialTheme.typography.headlineMedium, color = Aditv.Text)
            Text(matchWhen(m.startUtc) + " – " + timeFmt.format(Date(m.endUtc)) + " · " + m.channelName, style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary)
            Text(
                if (reminded) "Rappel programmé $leadMinutes min avant le début : bandeau dans ADITV (même pendant une lecture), notification sinon."
                else "Horaires du guide : le coup d'envoi réel peut différer de quelques minutes.",
                style = MaterialTheme.typography.bodyMedium, color = Aditv.TextTertiary,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                PillButton(if (reminded) "Supprimer le rappel" else "Me rappeler", onToggleReminder, Modifier.focusRequester(first), icon = if (reminded) R.drawable.ic_bell else R.drawable.ic_bell_on)
                PillButton("Regarder la chaîne", onWatch, icon = R.drawable.ic_play, primary = false)
                PillButton("Fermer", onClose, icon = null, primary = false)
            }
        }
    }
}

/** Bandeau de rappel en haut à droite, sans prendre le focus : la touche Menu ouvre la chaîne. */
@Composable
fun ReminderBanner(title: String, channel: String, startUtc: Long, modifier: Modifier = Modifier) {
    val minutes = ((startUtc - System.currentTimeMillis()) / 60_000).coerceAtLeast(0)
    Row(
        modifier.widthIn(max = 380.dp).background(Color(0xEB10151C), RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(30.dp).background(Aditv.Accent.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_bell_on), contentDescription = null, tint = Aditv.Accent, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(11.dp))
        Column {
            Text((if (minutes > 0) "DANS $minutes MIN" else "COMMENCE MAINTENANT") + " · " + channel.uppercase(), fontSize = 7.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, color = Aditv.Accent, maxLines = 1)
            Text(title, style = MaterialTheme.typography.headlineSmall.copy(fontSize = 13.sp), color = Aditv.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Menu ≡ : regarder · le bandeau se ferme seul", style = MaterialTheme.typography.bodySmall, color = Aditv.TextTertiary)
        }
    }
}

