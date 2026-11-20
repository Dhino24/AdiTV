package com.aditv.ui

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aditv.data.sport.UpcomingMatch
import com.aditv.ui.design.RowTitle
import com.aditv.ui.sport.MatchCard
import com.aditv.ui.sport.RankedTile
import com.aditv.ui.sport.ReminderBanner
import com.aditv.ui.theme.Aditv
import com.aditv.ui.theme.AditvTheme
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Rendu des composants sport et Top 10 (données d'exemple, titres fictifs). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SportComponentsTest {
    @get:Rule val rule = createComposeRule()

    @Test fun render() {
        val now = System.currentTimeMillis()
        val h = 3_600_000L
        val matches = listOf(
            UpcomingMatch("1", "Liga : FC Barcelone - Real Madrid", now + 3 * h, now + 5 * h, "c1", "beIN SPORTS 1", listOf("FC Barcelone", "Real Madrid"), false),
            UpcomingMatch("2", "Paris Saint-Germain - Aston Villa", now + 26 * h, now + 28 * h, "c2", "Canal+ Sport", listOf("Paris Saint-Germain", "Aston Villa"), false),
            UpcomingMatch("3", "Sénégal - Côte d'Ivoire", now - h / 2, now + h, "c3", "beIN SPORTS 2", listOf("Sénégal"), false),
        )
        rule.setContent {
            AditvTheme {
                Box(Modifier.fillMaxSize().background(Aditv.Bg)) {
                    Column(Modifier.padding(start = 48.dp, top = 30.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        RowTitle("Matchs à venir", "vos équipes · d'après le guide TV")
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { matches.forEachIndexed { i, m -> MatchCard(m, reminded = i == 0, onClick = {}) } }
                        RowTitle("Top 10 des films du moment", "tendances TMDB de la semaine, disponibles dans votre abonnement")
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            listOf("Exemple un", "Exemple deux", "Exemple trois", "Exemple quatre", "Exemple dix").forEachIndexed { i, t ->
                                RankedTile(if (i == 4) 10 else i + 1, t, "2026 · VF · 4K", null, {})
                            }
                        }
                    }
                    ReminderBanner("Liga : FC Barcelone - Real Madrid", "beIN SPORTS 1", now + 5 * 60_000 + 30_000, Modifier.align(Alignment.TopEnd).padding(top = 30.dp, end = 24.dp))
                }
            }
        }
        rule.waitForIdle()
        val out = File(System.getProperty("aditv.screens", "build/screens")).apply { mkdirs() }
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        File(out, "15_sport_top10.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
