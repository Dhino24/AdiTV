package com.aditv.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aditv.ui.design.Spot
import com.aditv.ui.design.SpotInfo
import com.aditv.ui.theme.AditvTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** La vedette (titre + infos + résumé) ne doit jamais descendre sur « Tout parcourir » / les rangées (place : 158 dp). */
@RunWith(AndroidJUnit4::class)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
class SpotInfoLayoutTest {
    @get:Rule val rule = createComposeRule()

    private fun height(spot: Spot): Float {
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, d.fontScale * 1.3f)) {
                AditvTheme { Box(Modifier.testTag("spot")) { SpotInfo(spot) } }
            }
        }
        val b = rule.onNodeWithTag("spot").getBoundsInRoot()
        return (b.bottom - b.top).value
    }

    @Test fun longTitleAndOverviewFit() {
        val h = height(Spot("k", "Children of the Corn : The Gathering of the Damned", kicker = "Ajouts récents", meta = listOf("1998", "Horreur", "1 h 23"),
            overview = "Six étudiants se trompent de route et arrivent dans une ville déserte. Mais ils découvrent bientôt qu'au cœur des champs se cache un culte meurtrier, dont les enfants sont les gardiens impitoyables et sans pitié.", lang = "fr"))
        println("SPOT h=$h"); assertTrue("vedette trop haute : $h dp", h <= 158f)
    }
}
