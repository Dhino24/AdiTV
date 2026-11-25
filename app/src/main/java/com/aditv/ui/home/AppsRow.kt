package com.aditv.ui.home

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.aditv.ui.common.navSound
import com.aditv.ui.design.RowTitle
import com.aditv.ui.theme.Aditv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Appli TV installée : nom, image (bannière TV de l'appli, sinon icône), intention d'ouverture. */
class TvApp(val pkg: String, val label: String, val image: ImageBitmap?, val isBanner: Boolean, val launch: Intent)

object InstalledApps {
    /** Plateformes connues, affichées en premier dans cet ordre (les autres applis TV suivent). */
    private val preferred = listOf(
        "com.netflix.ninja", "com.amazon.avod", "com.amazon.avod.thirdpartyclient", "com.amazon.firetv.youtube", "com.google.android.youtube.tv",
        "com.disney.disneyplus", "com.canal.android.canal", "tv.molotov.app", "fr.francetv.apps.pluzz", "fr.m6.m6replay", "fr.tf1.mytf1",
        "com.apple.atve.amazon.appletv", "com.spotify.tv.android", "com.dazn",
    )
    /** Écrans système et utilitaires : pas dans « Mes applis ». */
    private val hidden = listOf("com.amazon.tv.settings", "com.amazon.tv.launcher", "com.amazon.venezia", "com.android.", "com.amazon.device.", "com.amazon.ftv.")

    fun list(context: Context): List<TvApp> {
        val pm = context.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
        val found = runCatching { pm.queryIntentActivities(main, 0) }.getOrDefault(emptyList())
            .map { it.activityInfo.packageName }.distinct()
            .filter { p -> p != context.packageName && hidden.none { p.startsWith(it) } }
        val ordered = (preferred.filter { it in found } + found.filterNot { it in preferred }.sortedBy { label(pm, it).lowercase() }).take(14)
        return ordered.mapNotNull { pkg ->
            val launch = (pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg))?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return@mapNotNull null
            val banner = runCatching { pm.getApplicationBanner(pkg) }.getOrNull()
            val drawable = banner ?: runCatching { pm.getApplicationIcon(pkg) }.getOrNull()
            val bmp = drawable?.let { d -> runCatching { (if (banner != null) d.toBitmap(320, 180) else d.toBitmap(128, 128)).asImageBitmap() }.getOrNull() }
            TvApp(pkg, label(pm, pkg), bmp, banner != null, launch)
        }
    }

    private fun label(pm: PackageManager, pkg: String) = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() }.getOrDefault(pkg)
}

/** Rangée « Mes applis » : Netflix, Prime Video, YouTube… installées sur la Fire TV, ouvertes d'un clic. */
@Composable
fun AppsRow() {
    val context = LocalContext.current
    val apps by produceState<List<TvApp>>(emptyList()) { value = withContext(Dispatchers.Default) { InstalledApps.list(context) } }
    if (apps.isEmpty()) return
    Column(Modifier.padding(top = 10.dp)) {
        RowTitle("Mes applis", "installées sur cette Fire TV", Modifier.padding(start = 48.dp, bottom = 3.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 48.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(apps, key = { it.pkg }) { app ->
                Column(Modifier.width(160.dp)) {
                    Card(
                        onClick = { runCatching { context.startActivity(app.launch) } },
                        modifier = Modifier.width(160.dp).height(90.dp).navSound(),
                        shape = CardDefaults.shape(RoundedCornerShape(10.dp)),
                        scale = CardDefaults.scale(focusedScale = 1.08f),
                        colors = CardDefaults.colors(containerColor = Aditv.Surface2),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            when {
                                app.image == null -> Text(app.label, style = MaterialTheme.typography.titleMedium, color = Aditv.Text, maxLines = 2)
                                app.isBanner -> Image(app.image, app.label, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                else -> Image(app.image, app.label, Modifier.size(54.dp))
                            }
                        }
                    }
                    Text(app.label, style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.sp), color = Aditv.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 2.dp, top = 6.dp))
                }
            }
        }
    }
}
