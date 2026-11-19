package com.aditv.ui.detail

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.aditv.ui.LocalContainer
import com.aditv.ui.common.RemoteImage
import com.aditv.ui.common.Tag
import com.aditv.ui.formatEpochDate
import com.aditv.ui.home.typeLabel
import com.aditv.ui.theme.Aditv

/**
 * Fiche fournisseur (phase 1) : uniquement ce que déclare le catalogue. L'enrichissement TMDB
 * (synopsis FR, distribution, note contextualisée) et la lecture arrivent en phase 2.
 */
@Composable
fun ItemScreen(id: String) {
    val c = LocalContainer.current
    val item by remember(id) { c.db.catalog().observeItem(id) }.collectAsStateWithLifecycle(null)
    var categoryName by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(item?.categoryId) { categoryName = item?.categoryId?.let { c.db.catalog().categoryName(it) } }
    com.aditv.ui.common.InitialFocus(focus, key = item != null, enabled = item != null)

    val it = item ?: return
    Row(Modifier.fillMaxSize().padding(40.dp).focusRequester(focus).focusable()) {
        Box(Modifier.width(if (it.type == "LIVE") 220.dp else 240.dp).height(if (it.type == "LIVE") 220.dp else 360.dp).clip(RoundedCornerShape(16.dp)).background(Aditv.Surface1)) {
            RemoteImage(it.imageUrl, it.title, Modifier.fillMaxSize(), if (it.type == "LIVE") ContentScale.Fit else ContentScale.Crop)
        }
        Spacer(Modifier.width(36.dp))
        Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("${typeLabel(it.type).uppercase()} · CATALOGUE FOURNISSEUR", style = MaterialTheme.typography.labelSmall, color = Aditv.Accent)
            Text(it.title, style = MaterialTheme.typography.displaySmall, color = Aditv.Text)
            Text("Titre fournisseur : « ${it.rawTitle} »", style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                it.year?.let { y -> Tag(y.toString()) }
                it.genre?.let { g -> Tag(g) }
                it.containerExt?.let { e -> Tag(e.uppercase(), dashed = true) }
                if (!it.available) Tag("Retiré du catalogue", dashed = true)
            }
            it.plot?.let { p -> Text(p, style = MaterialTheme.typography.bodyLarge, color = Aditv.Text, modifier = Modifier.fillMaxWidth(0.8f)) }
            Spacer(Modifier.height(8.dp))
            Fact("Catégorie", categoryName ?: "—")
            it.addedAt?.let { a -> Fact("Ajouté (déclaré)", formatEpochDate(a)) }
            it.providerRating?.let { r -> Fact("Note fournie", "$r (source fournisseur, non vérifiée)") }
            it.providerTmdbId?.let { t -> Fact("ID TMDB fourni", "$t — vérification et enrichissement en phase 2") }
            if (it.type == "LIVE") {
                Fact("ID guide (EPG)", it.epgChannelId ?: "non fourni — association manuelle possible en phase 3")
                Fact("Replay déclaré", it.catchupDays?.let { d -> "$d jours (à vérifier en phase 3)" } ?: "non")
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Lecture disponible en phase 2 (Media3), après validation sur votre Fire TV.",
                style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary,
                modifier = Modifier.background(Aditv.Surface1, RoundedCornerShape(10.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun Fact(k: String, v: String) = Row {
    Text(k, style = MaterialTheme.typography.bodyMedium, color = Aditv.TextSecondary, modifier = Modifier.width(200.dp))
    Text(v, style = MaterialTheme.typography.bodyMedium, color = Aditv.Text)
}
