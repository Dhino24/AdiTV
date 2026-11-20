package com.aditv.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.aditv.net.RemoteEntryServer
import com.aditv.ui.design.PillButton
import com.aditv.ui.theme.Aditv
import com.aditv.ui.theme.Outfit
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Matrice du QR code (marge d'un module), ou null si le texte ne peut pas être encodé. */
fun qrMatrix(text: String): BitMatrix? = runCatching {
    QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 1, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M))
}.getOrNull()

@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val m = remember(text) { qrMatrix(text) } ?: return
    Canvas(modifier.background(Color.White, RoundedCornerShape(10.dp)).padding(10.dp)) {
        val cell = size.minDimension / m.width
        for (y in 0 until m.height) for (x in 0 until m.width) {
            // Léger recouvrement : pas de lignes blanches entre modules.
            if (m.get(x, y)) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.6f, cell + 0.6f))
        }
    }
}

/**
 * Panneau « Saisir depuis mon téléphone » : QR code + adresse courte + code à 4 chiffres.
 * Les valeurs reçues sont transmises une seule fois à [onReceived], puis oubliées par le serveur.
 */
@Composable
fun RemoteEntryPanel(form: RemoteEntryServer.Form, onReceived: (Map<String, String>) -> Unit, onClose: () -> Unit) {
    val received by rememberUpdatedState(onReceived)
    val close by rememberUpdatedState(onClose)
    // (ouverture terminée, serveur ou null si la TV n'est sur aucun réseau local)
    val opened by produceState<Pair<Boolean, RemoteEntryServer?>>(false to null) {
        value = true to withContext(Dispatchers.IO) { RemoteEntryServer.addressProvider()?.let { RemoteEntryServer(form, it).apply { start() } } }
    }
    val (resolved, server) = opened
    DisposableEffect(server) { onDispose { server?.close() } }
    BackHandler { close() }
    val closeRequester = remember { FocusRequester() }
    InitialFocus(closeRequester)

    val state = server?.state?.collectAsStateWithLifecycle()?.value
    LaunchedEffect(state) {
        if (state is RemoteEntryServer.State.Received) {
            received(state.values)
            server?.consume()
            close()
        }
    }

    Box(Modifier.fillMaxSize().background(Color(0xE6030508)), contentAlignment = Alignment.Center) {
        Row(
            Modifier.widthIn(max = 820.dp).background(Color(0xFF10151C), RoundedCornerShape(16.dp)).padding(32.dp),
            horizontalArrangement = Arrangement.spacedBy(32.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            when (state) {
                is RemoteEntryServer.State.Ready -> QrCode(state.url, Modifier.size(240.dp))
                else -> Box(Modifier.size(240.dp).background(Aditv.Surface2, RoundedCornerShape(10.dp)))
            }
            Column(Modifier.widthIn(max = 480.dp)) {
                Text("Saisir depuis mon téléphone", fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, color = Aditv.Text)
                Spacer(Modifier.height(4.dp))
                Text(form.title, style = MaterialTheme.typography.bodyMedium, color = Aditv.Accent)
                Spacer(Modifier.height(14.dp))
                when {
                    state is RemoteEntryServer.State.Ready -> {
                        Step("1", "Scannez le QR code avec l'appareil photo du téléphone (même Wi-Fi que la TV).")
                        Step("2", "Tapez ou collez vos informations, puis « Envoyer à la TV ».")
                        Spacer(Modifier.height(12.dp))
                        Text("Sans QR code : ouvrez", style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary)
                        Text(state.shortUrl, style = MaterialTheme.typography.titleMedium, color = Aditv.Text)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("puis tapez le code ", style = MaterialTheme.typography.bodySmall, color = Aditv.TextSecondary)
                            Text(state.pin, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, letterSpacing = 6.sp, color = Aditv.Accent)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "La page est servie par la TV elle-même, sur votre réseau local, sans passer par Internet. " +
                                "Elle se ferme après l'envoi, au bout de 10 minutes ou après 5 essais faux.",
                            style = MaterialTheme.typography.bodySmall, color = Aditv.TextTertiary,
                        )
                    }
                    state is RemoteEntryServer.State.Closed -> Text(state.reason, style = MaterialTheme.typography.bodyLarge, color = Aditv.Warn)
                    server == null && resolved -> Text(
                        "La TV n'est pas connectée à un réseau local (Wi-Fi ou Ethernet) : la saisie depuis le téléphone est impossible.",
                        style = MaterialTheme.typography.bodyLarge, color = Aditv.Warn,
                    )
                    else -> Text("Ouverture…", style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary)
                }
                Spacer(Modifier.height(18.dp))
                PillButton("Fermer", { close() }, Modifier.focusRequester(closeRequester), icon = null, primary = false)
            }
        }
    }
}

@Composable
private fun Step(n: String, text: String) = Row(Modifier.padding(vertical = 3.dp)) {
    Text(n, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = Aditv.Accent, modifier = Modifier.width(22.dp))
    Text(text, style = MaterialTheme.typography.bodyMedium, color = Aditv.Text)
}
