package com.aditv.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.aditv.R
import com.aditv.data.sport.SportMatcher
import com.aditv.net.RemoteEntryServer
import com.aditv.ui.LocalContainer
import com.aditv.ui.common.InitialFocus
import com.aditv.ui.common.RemoteEntryPanel
import com.aditv.ui.design.FilterChip
import com.aditv.ui.design.PillButton
import com.aditv.ui.design.liquidGlass
import com.aditv.ui.design.premiumBackground
import com.aditv.ui.theme.Aditv
import com.aditv.ui.theme.Outfit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Premier démarrage d'un nouvel utilisateur (après la connexion à l'abonnement) : 1) ses équipes, 2) les options
 * gratuites (TMDB, API-Football, football-data.org) saisies par QR code depuis le téléphone. Tout est facultatif
 * (« Passer ») et modifiable ensuite dans les Réglages. Montré une seule fois.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnboardingWizard(accountId: String, onDone: () -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    var step by remember { mutableStateOf(0) }
    val follows by remember(accountId) { c.sport.follows(accountId) }.collectAsState(emptyList())
    val followed = follows.map { it.key }.toSet()
    // Nouvel utilisateur : on part d'une sélection vide (les équipes par défaut sont celles d'Adama).
    LaunchedEffect(accountId) {
        c.sport.seedDefaults(accountId)
        c.sport.follows(accountId).first().forEach { c.sport.unfollow(accountId, it.key) }
    }
    fun finish() { scope.launch { c.prefs.setOnboardingDone(); onDone() } }
    BackHandler { if (step > 0) step-- else finish() }
    val first = remember { FocusRequester() }
    InitialFocus(first, key = step)

    Box(Modifier.fillMaxSize().premiumBackground(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 760.dp).liquidGlass(RoundedCornerShape(22.dp), strength = 1.6f).padding(horizontal = 34.dp, vertical = 28.dp)) {
            Text("Bienvenue sur ADI TV · étape ${step + 1} sur 2", style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 1.5.sp), color = Aditv.Accent)
            Spacer(Modifier.height(8.dp))
            when (step) {
                0 -> {
                    Text("Vos équipes", fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = Aditv.Text)
                    Text("Leurs matchs apparaissent sur l'accueil, avec rappels et alertes de but. OK pour cocher / décocher.", style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary)
                    Spacer(Modifier.height(14.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SportMatcher.PRESETS.forEachIndexed { i, p ->
                            val on = p.key in followed
                            FilterChip((if (on) "✓ " else "") + p.name, on, {
                                scope.launch { if (on) c.sport.unfollow(accountId, p.key) else c.sport.follow(accountId, p) }
                            }, if (i == 0) Modifier.focusRequester(first) else Modifier)
                        }
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        PillButton("Continuer", { step = 1 }, icon = null)
                        PillButton("Passer", { step = 1 }, icon = null, primary = false)
                    }
                }
                else -> OptionsStep(first, onFinish = { finish() })
            }
        }
    }
}

/** Options gratuites : chaque clé se saisit depuis le téléphone (QR code), avec ce qu'elle apporte. */
@Composable
private fun OptionsStep(first: FocusRequester, onFinish: () -> Unit) {
    val c = LocalContainer.current
    var entry by remember { mutableStateOf<RemoteEntryServer.Form?>(null) }
    var tick by remember { mutableStateOf(0) }
    Text("Options gratuites (facultatives)", fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = Aditv.Text)
    Text("Chaque clé se crée gratuitement en 2 minutes sur le site indiqué, puis se colle depuis votre téléphone (QR code). Vous pourrez aussi le faire plus tard dans Réglages.",
        style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary)
    Spacer(Modifier.height(14.dp))
    @Suppress("UNUSED_EXPRESSION") tick
    Option("Affiches, résumés, bandes-annonces, Top 10", "themoviedb.org → Paramètres → API", c.tmdbKey.get() != null, Modifier.focusRequester(first)) { entry = RemoteEntryServer.Form.TMDB }
    Option("FootAdi : matchs, scores, chaînes, compositions", "api-football.com → Dashboard → API Key", c.footballKey.get() != null) { entry = RemoteEntryServer.Form.FOOTBALL }
    Option("Classements des championnats", "football-data.org → inscription (clé par e-mail)", c.footballDataKey.get() != null) { entry = RemoteEntryServer.Form.FOOTBALL_DATA }
    Spacer(Modifier.height(18.dp))
    PillButton("Terminer", onFinish, icon = R.drawable.ic_check)
    entry?.let { form ->
        RemoteEntryPanel(form, onReceived = { v ->
            v["value"]?.trim()?.takeIf { it.isNotEmpty() }?.let { k ->
                when (form) {
                    RemoteEntryServer.Form.TMDB -> c.tmdbKey.set(k)
                    RemoteEntryServer.Form.FOOTBALL -> c.footballKey.set(k)
                    else -> c.footballDataKey.set(k)
                }
                tick++
            }
        }, onClose = { entry = null })
    }
}

@Composable
private fun Option(what: String, where: String, configured: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text((if (configured) "✓ " else "") + what, style = MaterialTheme.typography.bodyLarge, color = if (configured) Aditv.Ok else Aditv.Text)
            Text("Clé : $where", style = MaterialTheme.typography.bodySmall, color = Aditv.TextTertiary)
        }
        Spacer(Modifier.width(12.dp))
        PillButton(if (configured) "Changer" else "Saisir (QR code)", onClick, modifier, icon = null, primary = false, small = true)
    }
}
