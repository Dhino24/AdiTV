package com.aditv.ui.foot

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
 * Alertes FootAdi : buts de vos équipes suivies, détectés en comparant les scores. Interrogation seulement quand une
 * équipe suivie joue (en direct ou coup d'envoi dans 10 min), via le cache de FootAdi (1 requête / 5 min au plus pendant
 * le match) ; sinon un coup d'œil toutes les 30 min. Rien sans clé API-Football ni en mode économie.
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
        val mine = c.football.fixtures(0).filter(FootballRepository::isFollowed)
        mine.filter { it.live || it.finished }.forEach { f ->
            val total = (f.homeGoals ?: 0) + (f.awayGoals ?: 0)
            val before = lastScore[f.id]
            if (before != null && total > before) {
                current = GoalAlert("⚽ BUT !  ${f.home.name} ${f.homeGoals ?: 0} – ${f.awayGoals ?: 0} ${f.away.name}",
                    (f.elapsed?.let { "$it' · " } ?: "") + (FootballRepository.COMPETITIONS[f.competition.id] ?: f.competition.name))
            }
            lastScore[f.id] = total
        }
        val active = mine.any { it.live || (it.kickoff - now) in 0..10 * 60_000L }
        return if (active) 5 * 60_000L else 30 * 60_000L
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

/** Accueil : « Vos équipes aujourd'hui » (matchs du jour des équipes suivies), OK → FootAdi. Lit le cache de FootAdi. */
@Composable
fun MyTeamsToday(c: AppContainer, isDemo: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val matches by produceState<List<Fixture>>(emptyList(), isDemo) {
        value = if (isDemo || c.footballKey.get() == null) emptyList()
            else runCatching { c.football.fixtures(0).filter { FootballRepository.isFollowed(it) && !it.finished } }.getOrDefault(emptyList())
    }
    if (matches.isEmpty()) return
    Row(modifier.padding(start = 48.dp, top = 6.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("VOS ÉQUIPES AUJOURD'HUI", style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.5.sp, fontSize = 9.sp, fontWeight = FontWeight.SemiBold), color = Aditv.Accent)
        matches.take(3).forEach { f ->
            val whenTxt = if (f.live) "● ${f.elapsed ?: ""}' ${f.homeGoals ?: 0}–${f.awayGoals ?: 0}" else hour.format(Date(f.kickoff))
            PillButton("${f.home.name} – ${f.away.name} · $whenTxt", onOpen, icon = null, primary = false, small = true)
        }
    }
}
