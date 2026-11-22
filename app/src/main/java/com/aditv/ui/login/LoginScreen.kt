package com.aditv.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.aditv.R
import com.aditv.ui.common.AditvButton
import com.aditv.ui.common.Monogram
import com.aditv.ui.LocalContainer
import com.aditv.ui.formatEpochDate
import com.aditv.ui.theme.Aditv

@Composable
fun LoginScreen(notice: String?) {
    val container = LocalContainer.current
    val vm: LoginViewModel = viewModel { LoginViewModel(container) }
    val first = remember { FocusRequester() }
    val consentRequester = remember { FocusRequester() }
    val saveRequester = remember { FocusRequester() }
    val testRequester = remember { FocusRequester() }
    var phoneEntry by remember { mutableStateOf(false) }
    var gotFromPhone by remember { mutableStateOf(false) }
    var panelUsed by remember { mutableStateOf(false) }
    com.aditv.ui.common.InitialFocus(first)
    // Panneau fermé : focus sur « Tester la connexion » si des accès sont arrivés, sinon sur le premier champ.
    LaunchedEffect(phoneEntry) {
        if (!phoneEntry && panelUsed) runCatching { (if (gotFromPhone) testRequester else first).requestFocus() }
    }
    LaunchedEffect(vm.phase) {
        when (vm.phase) {
            is LoginPhase.NeedsCleartextConsent -> consentRequester.requestFocus()
            is LoginPhase.Tested -> saveRequester.requestFocus()
            else -> Unit
        }
    }

    Box(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxSize().background(Aditv.Bg)) {
        Column(Modifier.weight(1f).fillMaxHeight().padding(start = 64.dp, end = 32.dp), verticalArrangement = Arrangement.Center) {
            Image(painterResource(R.drawable.logo_full), contentDescription = "ADI TV", modifier = Modifier.width(300.dp).aspectRatio(com.aditv.ui.intro.LOGO_RATIO))
            Spacer(Modifier.height(28.dp))
            Text("Connectez votre abonnement", style = MaterialTheme.typography.headlineMedium, color = Aditv.Text)
            Spacer(Modifier.height(12.dp))
            Text(
                "ADI TV ne fournit aucun contenu. Saisissez l'adresse, l'identifiant et le mot de passe donnés par votre fournisseur. " +
                    "Le mot de passe est chiffré sur l'appareil et n'apparaît jamais dans les journaux.",
                style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary,
            )
            if (notice != null) {
                Spacer(Modifier.height(16.dp))
                Text(notice, style = MaterialTheme.typography.bodyMedium, color = Aditv.Warn)
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight().padding(end = 64.dp), verticalArrangement = Arrangement.Center) {
            TvTextFieldRow(vm, first)
            Spacer(Modifier.height(20.dp))
            when (val p = vm.phase) {
                is LoginPhase.NeedsCleartextConsent -> CleartextWarning(p.host, vm, consentRequester)
                is LoginPhase.Tested -> {
                    StatusSummary(p)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AditvButton("Enregistrer et importer", vm::save, Modifier.focusRequester(saveRequester))
                        AditvButton("Modifier", vm::onEdited)
                    }
                }
                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AditvButton(
                            if (p is LoginPhase.Testing) "Test en cours…" else if (p is LoginPhase.Saving) "Enregistrement…" else "Tester la connexion",
                            vm::test, Modifier.focusRequester(testRequester), enabled = p !is LoginPhase.Testing && p !is LoginPhase.Saving,
                        )
                        AditvButton("Essayer le mode démo", vm::demo, enabled = p !is LoginPhase.Testing && p !is LoginPhase.Saving)
                    }
                    Spacer(Modifier.height(12.dp))
                    AditvButton("Saisir depuis mon téléphone (QR code)", { phoneEntry = true; panelUsed = true }, enabled = p !is LoginPhase.Testing && p !is LoginPhase.Saving)
                    if (p is LoginPhase.Failed) {
                        Spacer(Modifier.height(14.dp))
                        Text(p.message, style = MaterialTheme.typography.bodyLarge, color = Aditv.Error)
                        if (p.hint != null) Text(p.hint, style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary)
                    }
                }
            }
        }
    }
    if (phoneEntry) com.aditv.ui.common.RemoteEntryPanel(
        com.aditv.net.RemoteEntryServer.Form.LOGIN,
        onReceived = { v ->
            v["server"]?.takeIf { it.isNotBlank() }?.let { vm.server = it }
            v["username"]?.takeIf { it.isNotBlank() }?.let { vm.username = it }
            v["password"]?.takeIf { it.isNotEmpty() }?.let { vm.password = it }
            vm.onEdited()
            gotFromPhone = true
        },
        onClose = { phoneEntry = false },
    )
    }
}

@Composable
private fun TvTextFieldRow(vm: LoginViewModel, first: FocusRequester) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        com.aditv.ui.common.TvTextField(
            "Adresse du serveur", vm.server, { vm.server = it; vm.onEdited() },
            placeholder = "https://exemple.fournisseur:8080", keyboardType = KeyboardType.Uri, surfaceRequester = first,
        )
        com.aditv.ui.common.TvTextField("Identifiant", vm.username, { vm.username = it; vm.onEdited() }, placeholder = "Identifiant fourni")
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            com.aditv.ui.common.TvTextField(
                "Mot de passe", vm.password, { vm.password = it; vm.onEdited() }, Modifier.weight(1f),
                password = true, revealed = vm.revealPassword, imeAction = ImeAction.Done,
            )
            AditvButton(if (vm.revealPassword) "Visible 5 s" else "Afficher", vm::revealMomentarily)
        }
    }
}

@Composable
private fun CleartextWarning(host: String, vm: LoginViewModel, requester: FocusRequester) {
    Column(
        Modifier.fillMaxWidth().background(Aditv.Surface1, RoundedCornerShape(14.dp))
            .border(1.dp, Aditv.Warn, RoundedCornerShape(14.dp)).padding(20.dp),
    ) {
        Text("Connexion non chiffrée (HTTP)", style = MaterialTheme.typography.titleLarge, color = Aditv.Warn)
        Spacer(Modifier.height(8.dp))
        Text(
            "Avec http://, votre identifiant, votre mot de passe et les flux transitent en clair sur le réseau. " +
                "ADI TV n'autorisera HTTP que vers $host. Préférez https:// si votre fournisseur le propose.",
            style = MaterialTheme.typography.bodyMedium, color = Aditv.Text,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AditvButton("Annuler", vm::declineCleartext, Modifier.focusRequester(requester))
            AditvButton("Continuer en HTTP", vm::acceptCleartext)
        }
    }
}

@Composable
private fun StatusSummary(p: LoginPhase.Tested) {
    val s = p.status
    Column(
        Modifier.widthIn(max = 560.dp).background(Aditv.Surface1, RoundedCornerShape(14.dp)).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            com.aditv.ui.common.StatusDot(Aditv.Ok); Spacer(Modifier.width(10.dp))
            Text("Connexion réussie", style = MaterialTheme.typography.titleLarge, color = Aditv.Text)
        }
        Line("Statut déclaré", s.status ?: "non communiqué")
        Line("Expiration", s.expiresAtEpochSec?.let { formatEpochDate(it) } ?: "non communiquée")
        Line("Connexions simultanées", s.maxConnections?.let { "$it max · ${s.activeConnections ?: "?"} en cours" } ?: "non communiqué")
        Line("Fuseau du serveur", s.serverTimezone ?: "non communiqué")
    }
}

@Composable
private fun Line(k: String, v: String) = Row {
    Text(k, style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary, modifier = Modifier.width(220.dp))
    Text(v, style = MaterialTheme.typography.bodyMedium, color = Aditv.Text)
}
