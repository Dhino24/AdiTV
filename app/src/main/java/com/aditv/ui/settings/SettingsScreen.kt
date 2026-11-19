package com.aditv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.aditv.BuildConfig
import com.aditv.data.Session
import com.aditv.data.provider.ProviderError
import com.aditv.data.sync.SyncProgress
import com.aditv.ui.LocalContainer
import com.aditv.ui.common.AditvButton
import com.aditv.ui.formatEpochDate
import com.aditv.ui.formatMillisDateTime
import com.aditv.ui.home.typeLabel
import com.aditv.ui.theme.Aditv
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

@Composable
fun SettingsScreen(session: Session.Active) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val a = session.account
    val states by remember(a.id) { c.db.catalog().observeSyncStates(a.id) }.collectAsStateWithLifecycle(emptyList())
    val progress by c.sync.progress.collectAsStateWithLifecycle()
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var confirmSignOut by remember { mutableStateOf(false) }
    LaunchedEffect(confirmSignOut) { if (confirmSignOut) { delay(5_000); confirmSignOut = false } }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 40.dp, top = 32.dp, end = 40.dp, bottom = 48.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Text("Paramètres", style = MaterialTheme.typography.headlineMedium, color = Aditv.Text)

        Section("Compte fournisseur") {
            Fact("Type", if (session.isDemo) "Démo (contenu fictif, sans lecture)" else "API compatible Xtream (vérifiée à la connexion)")
            if (!session.isDemo) {
                val url = a.endpoint.toHttpUrlOrNull()
                Fact("Serveur", url?.let { "${it.scheme}://${it.host}:${it.port}" } ?: a.endpoint)
                if (url?.isHttps == false) Fact("Sécurité", "HTTP en clair accepté pour cet hôte uniquement", Aditv.Warn)
            }
            Fact("Statut déclaré", a.status ?: "non communiqué")
            Fact("Expiration", a.expiresAtEpochSec?.let { formatEpochDate(it) } ?: "non communiquée")
            Fact("Connexions", a.maxConnections?.let { "$it max · ${a.activeConnections ?: "?"} en cours (au dernier contrôle)" } ?: "non communiqué")
            a.statusCheckedAt?.let { Fact("Dernier contrôle", formatMillisDateTime(it)) }
            statusMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AditvButton("Vérifier le statut", modifier = Modifier.focusRequester(com.aditv.ui.LocalScreenEntry.current), onClick = {
                    scope.launch {
                        statusMessage = try { c.accounts.refreshStatus(); "Statut mis à jour." } catch (e: ProviderError) { e.message } catch (e: Exception) { "Erreur : ${e.javaClass.simpleName}" }
                    }
                })
                AditvButton(
                    if (confirmSignOut) "Confirmer : effacer compte et catalogue local" else "Se déconnecter",
                    { if (confirmSignOut) scope.launch { c.playback.release(); c.accounts.signOut() } else confirmSignOut = true },
                )
            }
        }

        Section("Catalogue local") {
            when (val p = progress) {
                is SyncProgress.Running -> Fact("En cours", "${typeLabel(p.type.name)} · ${p.stage} · ${p.count}", Aditv.Accent)
                else -> Unit
            }
            listOf("LIVE", "MOVIE", "SERIES").forEach { t ->
                val s = states.firstOrNull { it.type == t }
                Fact(typeLabel(t), when {
                    s == null -> "jamais synchronisé"
                    s.lastError != null -> "${s.itemCount} en cache · échec le ${s.lastAttemptAt?.let(::formatMillisDateTime)} : ${s.lastError}"
                    else -> "${s.itemCount} éléments · ${s.lastSuccessAt?.let(::formatMillisDateTime)}"
                }, if (s?.lastError != null) Aditv.Warn else Aditv.Text)
            }
            Text("Actualisation automatique toutes les 12 h environ (réseau requis). Les titres retirés par le fournisseur sont masqués, pas supprimés.", style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary)
            AditvButton(if (progress is SyncProgress.Running) "Synchronisation en cours…" else "Synchroniser maintenant", { c.syncNow() }, enabled = progress !is SyncProgress.Running)
        }

        Section("Métadonnées TMDB") {
            TmdbSection()
        }

        Section("Application") {
            Fact("Version", BuildConfig.VERSION_NAME + if (BuildConfig.DEBUG) " (build de test)" else "")
            Fact("Confidentialité", "Aucune télémétrie. Accès chiffrés (Android Keystore), exclus des sauvegardes et des journaux.")
            Fact("Lecture", "Moteur Media3 (LibVLC et choix Auto : phase 4)")
            Fact("À venir", "Guide TV et favoris (phase 3) · diagnostic (phase 4) · Turbo, intro (phase 5)")
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.widthIn(max = 900.dp).background(Aditv.Surface1, RoundedCornerShape(16.dp)).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Aditv.Text)
        Spacer(Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun Fact(k: String, v: String, color: androidx.compose.ui.graphics.Color = Aditv.Text) = Row {
    Text(k, style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary, modifier = Modifier.width(180.dp))
    Text(v, style = MaterialTheme.typography.bodyMedium, color = color)
}

/** Clé TMDB personnelle : chiffrée sur l'appareil, testée avant enregistrement, jamais affichée en clair. */
@Composable
private fun TmdbSection() {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    var configured by remember { mutableStateOf(c.tmdbKey.get() != null) }
    var input by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Fact("État", if (configured) "Clé configurée (chiffrée sur l'appareil)" else "Aucune clé : fiches limitées aux informations du fournisseur", if (configured) Aditv.Text else Aditv.Warn)
    Text(
        "Créez gratuitement une clé sur themoviedb.org (Paramètres du compte → API), puis saisissez la clé API (v3) ou le jeton d'accès en lecture (v4).",
        style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary,
    )
    com.aditv.ui.common.TvTextField("Clé ou jeton TMDB", input, { input = it; message = null }, Modifier.widthIn(max = 640.dp), password = true, placeholder = if (configured) "•••••• (laisser vide pour conserver)" else "OK pour saisir")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AditvButton(if (busy) "Vérification…" else "Enregistrer et tester", {
            val key = input.trim()
            if (key.isEmpty()) { message = "Saisissez une clé."; return@AditvButton }
            busy = true
            scope.launch {
                val test = com.aditv.data.tmdb.TmdbClient(c.http.tmdb, { key })
                message = try {
                    test.imageBaseUrl() // appel /configuration : vérifie la clé
                    c.tmdbKey.set(key); configured = true; input = ""
                    c.enrichInBackground()
                    "Clé valide et enregistrée. Enrichissement des ajouts récents en arrière-plan."
                } catch (e: com.aditv.data.tmdb.TmdbError) { e.message } catch (e: Exception) { "Erreur : ${e.javaClass.simpleName}" }
                busy = false
            }
        }, enabled = !busy)
        if (configured) AditvButton("Supprimer la clé", { c.tmdbKey.clear(); configured = false; message = "Clé supprimée." })
        AditvButton("Vider le cache TMDB", {
            scope.launch {
                (c.accounts.session.value as? Session.Active)?.let { c.db.metadata().deleteAutomaticMatches(it.account.id) }
                c.db.metadata().clearMetadataCache()
                message = "Cache vidé. Les choix manuels sont conservés ; leurs fiches seront rechargées à l'ouverture."
            }
        })
    }
    message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary) }
    Text(com.aditv.data.tmdb.TmdbClient.ATTRIBUTION, style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary)
}
