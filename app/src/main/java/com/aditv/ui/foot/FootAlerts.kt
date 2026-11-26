package com.aditv.ui.foot

import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.aditv.data.football.Fixture
import com.aditv.data.football.FootballRepository
import com.aditv.di.AppContainer
import com.aditv.ui.design.PillButton
import com.aditv.ui.design.liquidGlass
import com.aditv.ui.theme.Aditv
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/** Alerte de but affichée par-dessus tout (même pendant une lecture). */
data class GoalAlert(val text: String, val detail: String, val at: Long = System.currentTimeMillis())

/**
 * Alertes FootAdi : buts de vos équipes suivies, détectés en comparant les scores. Pendant leurs matchs, relevé
 * « en direct » à un rythme calculé sur le quota restant (1 à 5 min, ~1 min 30 pour un match) ; sinon un coup d'œil
 * toutes les 30 min. Rien sans clé API-Football ni en mode économie.
 */
object FootAlerts {
    var current by mutableStateOf<GoalAlert?>(null); private set
    private val lastScore = HashMap<Long, Int>()

    suspend fun watch(c: AppContainer, isDemo: Boolean) {
        if (isDemo) return
        while (true) {
            val wait = runCatching { check(c) }.getOrDefault(30 * 60_000L)
            delay(wait)
        }
    }

    /** Renvoie le délai avant le prochain coup d'œil. */
    private suspend fun check(c: AppContainer): Long {
        if (c.footballKey.get() == null || c.football.saving) return 30 * 60_000L
        val now = System.currentTimeMillis()
        var mine = c.football.fixtures(0).filter(FootballRepository::isFollowed)
        val active = mine.any { it.live || (it.kickoff - now) in -5 * 60_000L..10 * 60_000L }
        if (active) mine = c.football.liveScores(maxAgeMs = 20_000).filter(FootballRepository::isFollowed)
        mine.filter { it.live || it.finished }.forEach { f ->
            val total = (f.homeGoals ?: 0) + (f.awayGoals ?: 0)
            val before = lastScore[f.id]
            if (before != null && total > before) {
                current = GoalAlert("⚽ BUT !  ${f.home.name} ${f.homeGoals ?: 0} – ${f.awayGoals ?: 0} ${f.away.name}",
                    (f.elapsed?.let { "$it' · " } ?: "") + (FootballRepository.COMPETITIONS[f.competition.id] ?: f.competition.name))
            }
            lastScore[f.id] = total
        }
        return if (active) c.football.liveInterval(mine) else 30 * 60_000L
    }

    fun dismiss() { current = null }
}

/** Bandeau de but (8 s). */
@Composable
fun GoalBanner(alert: GoalAlert, modifier: Modifier = Modifier) {
    LaunchedEffect(alert.at) { delay(8_000); FootAlerts.dismiss() }
    com.aditv.ui.design.AditvNotification(
        kicker = "But · " + alert.detail, title = alert.text.removePrefix("⚽ BUT !").trim(), subtitle = null,
        icon = com.aditv.R.drawable.ic_ball, accent = Aditv.Live, lifetimeMs = 8_000, actionKey = null, actionLabel = null, pulse = true,
        modifier = modifier, key = alert.at,
    )
}

private val hour = SimpleDateFormat("HH:mm", Locale.FRANCE)

/**
 * Accueil : vos équipes aujourd'hui. « Soir de match » : si l'une joue en ce moment ou dans les 3 h, grande carte en tête
 * (écussons, score ou heure, chaîne qui diffuse, Regarder / Détails) ; sinon une ligne de rappel discrète.
 * Lit le cache de FootAdi (scores tenus à jour par les alertes), relu chaque minute.
 */
@Composable
fun MyTeamsToday(c: AppContainer, isDemo: Boolean, accountId: String, onOpen: () -> Unit, onPlayLive: (String, String?) -> Unit, modifier: Modifier = Modifier) {
    val matches by produceState<List<Fixture>>(emptyList(), isDemo) {
        while (true) {
            value = if (isDemo || c.footballKey.get() == null) emptyList()
                else runCatching { c.football.fixtures(0).filter { FootballRepository.isFollowed(it) && !it.finished } }.getOrDefault(emptyList())
            delay(60_000)
        }
    }
    if (matches.isEmpty()) return
    val now = System.currentTimeMillis()
    val hot = matches.firstOrNull { it.live } ?: matches.firstOrNull { it.kickoff - now in 0..3 * 3_600_000L }
    if (hot != null) { MatchNightHero(c, accountId, hot, onOpen, onPlayLive, modifier); return }
    Row(modifier.padding(start = 48.dp, top = 6.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("VOS ÉQUIPES AUJOURD'HUI", style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.5.sp, fontSize = 9.sp, fontWeight = FontWeight.SemiBold), color = Aditv.Accent)
        matches.take(3).forEach { f ->
            PillButton("${f.home.name} – ${f.away.name} · ${hour.format(Date(f.kickoff))}", onOpen, icon = null, primary = false, small = true)
        }
    }
}

@Composable
private fun MatchNightHero(c: AppContainer, accountId: String, f: Fixture, onOpen: () -> Unit, onPlayLive: (String, String?) -> Unit, modifier: Modifier) {
    val broadcast by produceState<Broadcast?>(null, f.id) { value = runCatching { Broadcasts.find(c, accountId, f).firstOrNull() }.getOrNull() }
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier.padding(horizontal = 48.dp, vertical = 6.dp).fillMaxWidth().liquidGlass(shape, strength = 1.5f)
            .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf((if (f.live) Aditv.Live else Aditv.Accent).copy(alpha = 0.22f), androidx.compose.ui.graphics.Color.Transparent)), shape)
            .padding(horizontal = 26.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(170.dp)) {
            Text(if (f.live) "● EN DIRECT" else "SOIR DE MATCH", style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.8.sp, fontSize = 10.sp, fontWeight = FontWeight.Bold),
                color = if (f.live) Aditv.Live else Aditv.Accent)
            Spacer(Modifier.height(4.dp))
            Text(FootballRepository.COMPETITIONS[f.competition.id] ?: f.competition.name, style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        HeroTeam(f.home.logo, f.home.name, Modifier.weight(1f))
        Column(Modifier.width(150.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (f.live) {
                Text("${f.homeGoals ?: 0} – ${f.awayGoals ?: 0}", fontFamily = com.aditv.ui.theme.Outfit, fontWeight = FontWeight.Bold, fontSize = 34.sp, color = Aditv.Text)
                f.elapsed?.let { Text("$it'", style = MaterialTheme.typography.bodyMedium, color = Aditv.Live) }
            } else {
                Text(hour.format(Date(f.kickoff)), fontFamily = com.aditv.ui.theme.Outfit, fontWeight = FontWeight.Bold, fontSize = 30.sp, color = Aditv.Text)
                val left = (f.kickoff - System.currentTimeMillis()) / 60_000
                Text(if (left >= 60) "dans ${left / 60} h ${"%02d".format(left % 60)}" else "dans ${left.coerceAtLeast(0)} min", style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary)
            }
        }
        HeroTeam(f.away.logo, f.away.name, Modifier.weight(1f))
        Column(Modifier.width(250.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val b = broadcast
            if (b != null) PillButton("Regarder sur ${b.name}", { onPlayLive(b.channel.id, "fam:SPORT_FR") }, Modifier.fillMaxWidth(), icon = com.aditv.R.drawable.ic_play, small = true)
            else Text("Chaîne pas encore trouvée dans votre guide.", style = MaterialTheme.typography.bodySmall, color = Aditv.TextTertiary)
            PillButton("Détails et compositions", onOpen, Modifier.fillMaxWidth(), icon = com.aditv.R.drawable.ic_list, primary = false, small = true)
        }
    }
}

@Composable
private fun HeroTeam(logo: String?, name: String, modifier: Modifier) = Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    Box(Modifier.size(62.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
        com.aditv.ui.common.RemoteImage(logo, name, Modifier.size(46.dp), androidx.compose.ui.layout.ContentScale.Fit, fallbackTitle = false)
    }
    Spacer(Modifier.height(6.dp))
    Text(name, style = MaterialTheme.typography.titleMedium.copy(fontSize = 13.sp), color = Aditv.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
}
