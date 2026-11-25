package com.aditv.ui.update

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.aditv.BuildConfig
import com.aditv.R
import com.aditv.data.update.UpdateInfo
import com.aditv.ui.LocalContainer
import com.aditv.ui.common.InitialFocus
import com.aditv.ui.design.PillButton
import com.aditv.ui.design.liquidGlass
import com.aditv.ui.theme.Aditv
import kotlinx.coroutines.launch

/** Une seule proposition par lancement (« Plus tard » = au prochain lancement). */
object UpdateGate { var asked = false }

/** « Mise à jour disponible » : téléchargement vérifié puis installeur Android (Retour ou « Plus tard » ferme). */
@Composable
fun UpdateDialog(info: UpdateInfo, onClose: () -> Unit) {
    val c = LocalContainer.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<Float?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var needsPermission by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    InitialFocus(first, key = needsPermission)
    BackHandler { onClose() }
    fun start() {
        if (!c.updater.canInstall()) { needsPermission = true; return }
        needsPermission = false; message = null; progress = 0f
        scope.launch {
            try {
                val apk = c.updater.download(info) { progress = it }
                message = "Téléchargée et vérifiée. Confirmez « Installer » à l'écran suivant."
                c.updater.install(apk)
            } catch (e: Exception) { message = e.message ?: "Échec du téléchargement."; progress = null }
        }
    }
    Box(Modifier.fillMaxSize().background(Color(0xB0030508)), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 520.dp).liquidGlass(RoundedCornerShape(18.dp), strength = 1.8f).padding(horizontal = 30.dp, vertical = 26.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text("Mise à jour disponible", style = MaterialTheme.typography.headlineMedium, color = Aditv.Text)
            Text("ADI TV ${info.versionName} (vous avez ${BuildConfig.VERSION_NAME})", style = MaterialTheme.typography.bodyLarge, color = Aditv.TextSecondary)
            if (info.notes.isNotBlank()) Text(info.notes, style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary, maxLines = 6)
            progress?.let { p ->
                Box(Modifier.fillMaxWidth().height(5.dp).background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(3.dp))) {
                    Box(Modifier.fillMaxWidth(p).fillMaxHeight().background(Aditv.Accent, RoundedCornerShape(3.dp)))
                }
                Text(if (p < 1f) "Téléchargement… ${(p * 100).toInt()} %" else "Vérification…", style = MaterialTheme.typography.bodySmall, color = Aditv.TextTertiary)
            }
            if (needsPermission) Text(
                "Une seule fois : autorisez ADI TV à installer des applis. Sur la Fire TV : Paramètres → Ma Fire TV → Options pour les développeurs → Installer des applis inconnues → ADI TV → Activé.",
                style = MaterialTheme.typography.bodyMedium, color = Aditv.Warn,
            )
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = Aditv.Text) }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (needsPermission) {
                    PillButton("Ouvrir l'autorisation", { if (!c.updater.openInstallPermission()) message = "Ouvrez ce réglage à la main (chemin ci-dessus)." }, Modifier.focusRequester(first), icon = null)
                    PillButton("C'est fait, mettre à jour", { start() }, icon = R.drawable.ic_check, primary = false)
                } else PillButton(if (progress == null) "Mettre à jour" else "Réessayer", { start() }, Modifier.focusRequester(first), icon = R.drawable.ic_down)
                PillButton("Plus tard", onClose, icon = null, primary = false)
            }
        }
    }
}
