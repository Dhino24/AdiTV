package com.aditv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.graphics.Color
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
    val running by c.sync.running.collectAsStateWithLifecycle()
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var confirmSignOut by remember { mutableStateOf(false) }
    LaunchedEffect(confirmSignOut) { if (confirmSignOut) { delay(5_000); confirmSignOut = false } }

    val scroll = rememberScrollState()
    val chrome = com.aditv.ui.shell.LocalNavChrome.current
    val scrolled by remember { androidx.compose.runtime.derivedStateOf { scroll.value > 40 } }
    LaunchedEffect(scrolled) { chrome.scrolled = scrolled }
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 48.dp, top = 74.dp, end = 48.dp, bottom = 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Réglages", style = MaterialTheme.typography.displaySmall, color = Aditv.Text)

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
            // Deux synchronisations indépendantes : chaînes d'un côté, films et séries de l'autre.
            listOf("LIVE", "MOVIE", "SERIES").forEach { t ->
                val s = states.firstOrNull { it.type == t }
                val run = running[com.aditv.data.provider.MediaType.valueOf(t)]
                Fact(typeLabel(t), when {
                    run != null -> "en cours · ${run.stage} · ${run.count} reçus"
                    s == null -> "jamais synchronisé"
                    s.lastError != null -> "${s.itemCount} en cache · échec le ${s.lastAttemptAt?.let(::formatMillisDateTime)} : ${s.lastError}"
                    s.lastSuccessAt == null -> "aucun import terminé"
                    else -> "${s.itemCount} éléments · ${s.categoryCount} catégories · ${formatMillisDateTime(s.lastSuccessAt)}"
                }, when {
                    run != null -> Aditv.Accent
                    s?.lastError != null || s?.itemCount == 0 || s?.lastSuccessAt == null -> Aditv.Warn
                    else -> Aditv.Text
                })
                if (run == null) s?.detail?.let { d -> Text("   import : $d", style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary) }
            }
            Text("Chaînes : actualisation automatique toutes les 12 h environ (réseau requis). Films et séries : importés automatiquement une seule fois. Les titres retirés par le fournisseur sont masqués, pas supprimés. Gardez ADITV ouvert pendant un premier import volumineux.",
                style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary)
            val liveRunning = com.aditv.data.provider.MediaType.LIVE in running
            val vodRunning = com.aditv.data.provider.MediaType.MOVIE in running || com.aditv.data.provider.MediaType.SERIES in running
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AditvButton(if (liveRunning) "Chaînes : en cours…" else "Synchroniser les chaînes", { c.syncLiveNow() }, enabled = !liveRunning)
                AditvButton(if (vodRunning) "Films et séries : en cours…" else "Synchroniser films et séries", { c.syncVodNow() }, enabled = !vodRunning)
            }
            Text("Films et séries : jamais rechargés à chaque ouverture. Une mise à jour est proposée sur l'accueil toutes les 3 semaines (« Plus tard » la repousse de 3 jours). Les chaînes, plus légères, restent mises à jour automatiquement.",
                style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary)
        }

        Section("Lecture : langue et qualité") {
            PlaybackPrefsSection()
        }

        Section("Moteur de lecture, Turbo et diagnostic") {
            EngineSection()
        }

        Section("Sport : équipes suivies et rappels") {
            SportSection(session)
        }

        Section("Guide TV") {
            GuideSection(session)
        }

        Section("Métadonnées TMDB") {
            TmdbSection()
        }

        Section("Accessibilité et sauvegarde") {
            AccessSection(session)
        }

        Section("Ouverture") {
            val introImage by c.prefs.introImageEnabled.collectAsStateWithLifecycle(true)
            val introSound by c.prefs.introSoundEnabled.collectAsStateWithLifecycle(true)
            Text("Logo animé de 2,4 s au démarrage à froid ; n'attend jamais le réseau, toute touche le passe. Animations réduites du système : version courte.",
                style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                AditvButton("Ouverture animée", { scope.launch { c.prefs.setIntroImage(!introImage) } }, secondaryText = if (introImage) "Activée" else "Désactivée")
                AditvButton("Son de l'ouverture", { scope.launch { c.prefs.setIntroSound(!introSound) } }, secondaryText = if (introSound) "Activé" else "Désactivé")
            }
        }

        Section("Plantages") { CrashSection() }
        Section("Application") {
            Fact("Version", BuildConfig.VERSION_NAME + if (BuildConfig.DEBUG) " (build de test)" else "")
            Fact("Appareil", com.aditv.core.DeviceProfile.label + " · Android " + android.os.Build.VERSION.RELEASE)
            Fact("Confidentialité", "Aucune télémétrie. Accès chiffrés (Android Keystore), exclus des sauvegardes et des journaux.")
            Fact("Lecture", "Moteurs Media3 et VLC (LibVLC 3), choix Auto par défaut")
            Fact("Licences", "Polices Outfit et Inter (OFL), LibVLC (LGPL 2.1), Media3 et ZXing (Apache 2.0) : détail dans docs/VERSIONS_ET_LICENCES.md")
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.035f), RoundedCornerShape(9.dp)).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        com.aditv.ui.design.Kicker(title, color = Aditv.Text)
        Spacer(Modifier.height(3.dp))
        content()
    }
}

@Composable
private fun Fact(k: String, v: String, color: androidx.compose.ui.graphics.Color = Aditv.Text) = Row {
    Text(k, style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary, modifier = Modifier.width(150.dp))
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
    var phoneEntry by remember { mutableStateOf(false) }
    if (phoneEntry) androidx.compose.ui.window.Dialog(
        onDismissRequest = { phoneEntry = false },
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
    ) {
        com.aditv.ui.common.RemoteEntryPanel(
            com.aditv.net.RemoteEntryServer.Form.TMDB,
            onReceived = { v -> v["value"]?.trim()?.takeIf { it.isNotEmpty() }?.let { input = it; message = "Clé reçue du téléphone : choisissez « Enregistrer et tester »." } },
            onClose = { phoneEntry = false },
        )
    }
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
        AditvButton("Depuis mon téléphone", { phoneEntry = true }, enabled = !busy)
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

/** Version française et qualité : pour regrouper les variantes et choisir les pistes du lecteur. */
@Composable
private fun PlaybackPrefsSection() {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val preset by c.prefs.languagePresetKey.collectAsStateWithLifecycle("vf")
    val prefs by c.prefs.variantPrefs.collectAsStateWithLifecycle(com.aditv.data.variants.VariantPrefs())
    val force by c.prefs.forceHighestBitrate.collectAsStateWithLifecycle(true)
    Text("Quand un film, une série ou une chaîne existe en plusieurs versions chez le fournisseur, ADITV affiche et lit d'abord :",
        style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("vf" to "Français (VF)", "vostfr" to "VOSTFR", "vo" to "Version originale").forEach { (k, l) ->
            AditvButton(l, { scope.launch { c.prefs.setLanguagePreset(k) } }, secondaryText = if (preset == k) "●" else null)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(4 to "Qualité max (4K)", 3 to "1080p max", 2 to "720p max").forEach { (q, l) ->
            AditvButton(l, { scope.launch { c.prefs.setMaxQuality(q) } }, secondaryText = if (prefs.maxQuality == q) "●" else null)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AditvButton("Flux adaptatifs : débit max", { scope.launch { c.prefs.setForceHighestBitrate(true) } }, secondaryText = if (force) "●" else null)
        AditvButton("Adapté au réseau", { scope.launch { c.prefs.setForceHighestBitrate(false) } }, secondaryText = if (!force) "●" else null)
    }
    val matchRate by c.prefs.matchFrameRate.collectAsStateWithLifecycle(true)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AditvButton("Fréquence de l'écran : adaptée au flux", { scope.launch { c.prefs.setMatchFrameRate(true) } }, secondaryText = if (matchRate) "●" else null)
        AditvButton("Fréquence fixe", { scope.launch { c.prefs.setMatchFrameRate(false) } }, secondaryText = if (!matchRate) "●" else null)
    }
    Text("Fréquence adaptée : l'écran passe en 50 Hz pour un match européen et en 24 Hz pour un film (mouvements plus fluides) ; un écran noir d'1 à 2 s est normal au changement. Sur Samsung, activez aussi « Signal d'entrée plus » pour la prise HDMI de la Fire TV.",
        style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary)
    Text("La langue et la qualité sont celles annoncées dans les titres du fournisseur ; le flux réel peut différer. En cas de coupures en qualité max, essayez « Adapté au réseau » ou une limite plus basse. Le lecteur sélectionne aussi l'audio français quand le flux en contient.",
        style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary)
}

/** Sources du guide, actualisation, couverture et décalages. */
@Composable
private fun GuideSection(session: Session.Active) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val sources by remember(session.account.id) { c.db.live().observeSources(session.account.id) }.collectAsStateWithLifecycle(emptyList())
    val status by c.epg.status.collectAsStateWithLifecycle()
    var coverage by remember { mutableStateOf<com.aditv.data.epg.Coverage?>(null) }
    var newUrl by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(sources) { coverage = c.epg.coverage(session.account.id) }
    coverage?.let { Fact("Couverture", "${it.covered} / ${it.channels} chaînes avec programmes à venir (${it.withGuideId} avec un identifiant de guide)") }
    Fact("Fuseau d'affichage", java.util.TimeZone.getDefault().id + " (appareil) · horaires stockés en UTC, changements d'heure gérés")
    if (sources.isEmpty()) Text("Aucune source : elle sera créée à la première actualisation.", style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary)
    sources.forEach { src ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Fact(if (src.kind == "provider") "Fournisseur" else "Source XMLTV", buildString {
                append(src.label)
                append(if (src.enabled) "" else " (désactivée)")
                src.lastFetchAt?.let { append(" · ${src.programmeCount} programmes, ${src.channelCount} chaînes · ${formatMillisDateTime(it)}") }
                if (src.ambiguousTimes > 0) append(" · ${src.ambiguousTimes} horaires sans fuseau (fuseau du serveur appliqué)")
            }, if (src.lastError != null) Aditv.Warn else Aditv.Text)
            src.lastError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Aditv.Warn) }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                AditvButton("− 30 min", { scope.launch { c.epg.setSourceOffset(src, src.offsetMinutes - 30) } })
                AditvButton("+ 30 min", { scope.launch { c.epg.setSourceOffset(src, src.offsetMinutes + 30) } }, secondaryText = if (src.offsetMinutes != 0) "décalage ${src.offsetMinutes} min" else null)
                AditvButton(if (src.enabled) "Désactiver" else "Activer", { scope.launch { c.epg.setSourceEnabled(src, !src.enabled) } })
                if (src.kind == "url") AditvButton("Retirer", { scope.launch { c.epg.removeSource(src.id) } })
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        AditvButton(status ?: "Actualiser le guide", {
            scope.launch { c.refreshEpg(force = true); coverage = c.epg.coverage(session.account.id); message = "Guide actualisé." }
        }, enabled = status == null)
    }
    com.aditv.ui.common.TvTextField("Ajouter une source XMLTV (https://…)", newUrl, { newUrl = it; message = null }, Modifier.widthIn(max = 640.dp),
        keyboardType = androidx.compose.ui.text.input.KeyboardType.Uri, placeholder = "OK pour saisir")
    AditvButton("Ajouter la source", {
        val u = newUrl.trim()
        message = when {
            !u.startsWith("https://", true) -> "Seules les adresses https:// sont acceptées pour une source ajoutée."
            else -> { scope.launch { c.epg.addUrlSource(session.account.id, u) }; newUrl = ""; "Source ajoutée : lancez « Actualiser le guide »." }
        }
    })
    Text("Le guide du fournisseur est prioritaire ; une chaîne n'utilise qu'une seule source (pas de grilles mélangées). Les chaînes sans données restent « sans guide » : rien n'est inventé. Association manuelle : fiche de la chaîne → Guide.",
        style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary)
    message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary) }
}

/** Équipes et compétitions suivies, délai des rappels, rappels programmés (depuis le guide uniquement). */
@Composable
private fun SportSection(session: Session.Active) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val accountId = session.account.id
    val follows by remember(accountId) { c.sport.follows(accountId) }.collectAsStateWithLifecycle(emptyList())
    val reminders by remember(accountId) { c.sport.upcomingReminders(accountId) }.collectAsStateWithLifecycle(emptyList())
    val matches by c.sport.matches.collectAsStateWithLifecycle()
    val lead by c.prefs.reminderLeadMinutes.collectAsStateWithLifecycle(5)
    val followed = follows.map { it.key }.toSet()
    Text("Les matchs sont trouvés dans le guide TV (titres, catégories, chaînes de sport) sur 7 jours ; un rappel automatique est créé pour chacun. " +
        "Horaires du guide : le coup d'envoi réel peut différer. ${matches.size} programme(s) trouvé(s).",
        style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary)
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(5.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp, horizontal = 2.dp)) {
        items(com.aditv.data.sport.SportMatcher.PRESETS.size) { i ->
            val p = com.aditv.data.sport.SportMatcher.PRESETS[i]
            val on = p.key in followed
            com.aditv.ui.design.FilterChip((if (on) "✓ " else "") + p.name, on, {
                c.appScope.launch { if (on) c.sport.unfollow(accountId, p.key) else c.sport.follow(accountId, p); c.refreshSport() }
            })
        }
    }
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("Rappel", style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary, modifier = Modifier.width(80.dp))
        listOf(5, 10, 15, 30).forEach { m ->
            com.aditv.ui.design.FilterChip("$m min avant", lead == m, { scope.launch { c.prefs.setReminderLead(m) } })
        }
    }
    if (reminders.isEmpty()) Text("Aucun rappel programmé pour l'instant (le guide doit contenir les matchs à venir).", style = MaterialTheme.typography.bodyMedium, color = Aditv.TextTertiary)
    reminders.take(12).forEach { r ->
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(com.aditv.ui.sport.matchWhen(r.startUtc), style = MaterialTheme.typography.bodyMedium, color = Aditv.Accent, modifier = Modifier.width(150.dp))
            Text(r.title + " · " + r.channelName + if (r.auto) " (auto)" else "", style = MaterialTheme.typography.bodyMedium, color = Aditv.Text, maxLines = 1, modifier = Modifier.weight(1f))
            AditvButton("Supprimer", { scope.launch { c.sport.deleteReminder(r.id) } })
        }
    }
}

/** Moteur par défaut, Turbo par défaut et journal des dernières sessions (comparaison des moteurs sur la Stick). */
@Composable
private fun EngineSection() {
    val c = LocalContainer.current
    var engineDefault by remember { mutableStateOf(c.engineStore.defaultChoice) }
    var turbo by remember { mutableStateOf(c.engineStore.turboDefault) }
    var log by remember { mutableStateOf(c.engineStore.sessions()) }
    Text("Auto : Media3 d'abord, puis VLC une seule fois si Media3 refuse le format (jamais pour une panne réseau). Le choix fait dans le lecteur est mémorisé par contenu.",
        style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary)
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        com.aditv.playback.engine.EngineChoice.entries.forEach { e ->
            com.aditv.ui.design.FilterChip(e.label, engineDefault == e, { c.engineStore.defaultChoice = e; engineDefault = e })
        }
    }
    Text("Turbo : réserve de lecture étendue (60 à ${com.aditv.core.DeviceProfile.turboMaxBufferMs / 1000} s, ${com.aditv.core.DeviceProfile.turboTargetBytes / 1048576} Mo au plus), synchronisation et enrichissement en pause pendant la lecture. Aucun gain de débit n'est promis : comparez les interruptions ci-dessous.",
        style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary)
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        com.aditv.ui.design.FilterChip("Turbo désactivé", !turbo, { c.engineStore.turboDefault = false; turbo = false })
        com.aditv.ui.design.FilterChip("Turbo activé par défaut", turbo, { c.engineStore.turboDefault = true; turbo = true })
    }
    Text("Dernières sessions (mesures du moteur utilisé)", style = MaterialTheme.typography.labelLarge, color = Aditv.Text, modifier = Modifier.padding(top = 6.dp))
    if (log.isEmpty()) Text("Aucune session enregistrée : lisez un même contenu avec Media3 puis VLC pour comparer.", style = MaterialTheme.typography.bodyMedium, color = Aditv.TextTertiary)
    val fmt = remember { java.text.SimpleDateFormat("d MMM HH:mm", java.util.Locale.FRANCE) }
    log.take(10).forEach { l ->
        Row {
            Text(fmt.format(java.util.Date(l.at)), style = MaterialTheme.typography.bodyMedium, color = Aditv.TextTertiary, modifier = Modifier.width(110.dp))
            Text(l.engine + if (l.turbo) " + Turbo" else "", style = MaterialTheme.typography.bodyMedium, color = Aditv.Accent, modifier = Modifier.width(110.dp))
            Text(
                listOfNotNull(
                    l.title,
                    l.startupMs?.let { "démarrage $it ms" },
                    "${l.stalls} interruption${if (l.stalls > 1) "s" else ""}",
                    l.dropped?.let { "$it images perdues" },
                    "${l.watchedSec / 60} min",
                    if (l.fallback) "bascule Auto" else null,
                    l.error?.let { "erreur : $it" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium, color = Aditv.Text, maxLines = 1,
            )
        }
    }
    if (log.isNotEmpty()) AditvButton("Effacer le journal", { c.engineStore.clearLog(); log = emptyList() })
}

/** Taille du texte, animations réduites, export / import des préférences (sans aucun secret). */
@Composable
private fun AccessSection(session: Session.Active) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    val scale by c.prefs.textScalePercent.collectAsStateWithLifecycle(100)
    val reduce by c.prefs.reduceMotionPref.collectAsStateWithLifecycle(false)
    var message by remember { mutableStateOf<String?>(null) }
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("Taille du texte", style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary, modifier = Modifier.width(120.dp))
        listOf(100 to "Normale", 115 to "Grande", 130 to "Très grande").forEach { (v, l) ->
            com.aditv.ui.design.FilterChip(l, scale == v, { scope.launch { c.prefs.setTextScale(v) } })
        }
    }
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text("Animations", style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary, modifier = Modifier.width(120.dp))
        com.aditv.ui.design.FilterChip("Normales", !reduce, { scope.launch { c.prefs.setReduceMotion(false) } })
        com.aditv.ui.design.FilterChip("Réduites", reduce, { scope.launch { c.prefs.setReduceMotion(true) } })
    }
    Text("Sauvegarde : préférences, équipes suivies, chaînes favorites et Ma liste (jamais vos accès ni la clé TMDB), dans ${c.backup.file.path}. Récupérable avec « adb pull ».",
        style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary)
    Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        AditvButton("Exporter", { scope.launch { message = runCatching { "Exporté : " + c.backup.export(session.account.id).name }.getOrElse { "Export impossible : ${it.message}" } } })
        AditvButton("Importer", { scope.launch { message = runCatching { c.backup.import(session.account.id) }.getOrElse { "Import impossible : ${it.message}" }; c.refreshSport() } })
    }
    message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Aditv.Accent) }
}

/** Rapports de plantage enregistrés sur la Stick, lisibles sur le téléphone (QR code) pour les transmettre. */
@Composable
private fun CrashSection() {
    var reports by remember { mutableStateOf(com.aditv.core.CrashReporter.reports()) }
    var shown by remember { mutableStateOf<String?>(null) }
    shown?.let { text ->
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { shown = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            com.aditv.ui.common.RemoteEntryPanel(
                com.aditv.net.RemoteEntryServer.Form.report(text), onReceived = {}, onClose = { shown = null },
                heading = "Lire le rapport sur mon téléphone",
            )
        }
    }
    if (reports.isEmpty()) {
        Fact("État", "Aucun plantage enregistré.")
        return
    }
    Fact("Enregistrés", "${reports.size} (les 5 derniers sont conservés)", Aditv.Warn)
    reports.take(3).forEach { r -> Text(r.title, style = MaterialTheme.typography.bodyMedium, color = Aditv.Text) }
    Text(
        "Ouvrez le rapport sur votre téléphone, copiez-le et envoyez-le pour diagnostic. Aucun accès ni adresse de flux n'y figure.",
        style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AditvButton("Voir sur mon téléphone (QR code)", { shown = reports.joinToString("\n\n==========\n\n") { it.text } })
        AditvButton("Effacer les rapports", { com.aditv.core.CrashReporter.clear(); reports = emptyList() })
    }
}
