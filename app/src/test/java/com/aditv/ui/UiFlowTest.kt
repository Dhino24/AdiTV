package com.aditv.ui

import android.graphics.Bitmap
import android.os.Looper
import java.time.Duration
import org.robolectric.Shadows.shadowOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Before
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aditv.MainActivity
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Parcours réel de l'interface (Compose exécuté par Robolectric) à 1920×1080 :
 * connexion → mode démo → accueil → menu (mémoire de sélection) → films → fiche → retour → paramètres.
 * Ne remplace pas la validation sur Fire TV (performances, télécommande physique, IME Amazon).
 */
@OptIn(ExperimentalTestApi::class)
abstract class UiFlowBase(private val screenshots: Boolean) {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val outDir = File(System.getProperty("aditv.screens", "build/screens")).apply { mkdirs() }

    private fun shot(name: String) {
        rule.waitForIdle()
        if (!screenshots) return
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        File(outDir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Le looper principal Robolectric est en pause : on avance son horloge pendant l'attente (delay, debounce). */
    private fun waitText(text: String, timeoutMs: Long = 20_000) = try {
        rule.waitUntil(timeoutMs) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            rule.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
    } catch (e: Throwable) { shot("echec_attente"); throw AssertionError("Texte attendu absent : « $text »", e) }

    private fun waitMenuClosed() = try {
        rule.waitUntil(5_000) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            rule.onAllNodesWithText("Ma liste").fetchSemanticsNodes().isEmpty()
        }
    } catch (e: Throwable) { shot("echec_menu_ouvert"); throw AssertionError("Menu resté ouvert. Focus : " + focusedDump(), e) }

    private fun key(k: Key, times: Int = 1) = repeat(times) {
        rule.onRoot().performKeyInput { pressKey(k) }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(800))
        rule.waitForIdle()
    }

    /** Une Fire TV démarre hors mode tactile : on reproduit cet état avant de relancer l'activité. */
    @Before fun remoteMode() {
        InstrumentationRegistry.getInstrumentation().setInTouchMode(false)
        rule.activityRule.scenario.recreate()
    }

    private fun press(text: String) { node(text).performSemanticsAction(SemanticsActions.OnClick); rule.waitForIdle() }

    private fun node(text: String): SemanticsNodeInteraction = rule.onNode(hasText(text), useUnmergedTree = false)

    private fun focusedDump(): String = rule.onAllNodes(androidx.compose.ui.test.isFocused(), useUnmergedTree = true)
        .fetchSemanticsNodes().joinToString { it.config.toString().take(160) + " @" + it.boundsInRoot }

    private fun assertFocused(text: String) = try { node(text).assertIsFocused() } catch (e: AssertionError) { shot("echec_focus_" + text.take(12)); throw AssertionError("Focus actuel : " + focusedDump(), e) }

    @Test fun demoJourneyWithRemote() {
        waitText("Connectez votre abonnement")
        shot("01_connexion")
        // Focus initial sur le premier champ, visible sans toucher l'écran.
        rule.waitUntil(5_000) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            rule.onAllNodes(androidx.compose.ui.test.isFocused()).fetchSemanticsNodes().any { n -> n.boundsInRoot.top in 250f..300f }
        }
        shot("01_connexion")

        // HTTP sans consentement : avertissement explicite avant toute requête.
        press("Tester la connexion")
        waitText("Renseignez l'identifiant")

        press("Essayer le mode démo")
        waitText("Ajouts récents · Films")
        waitText("Catalogue du")
        shot("02_accueil")

        // Focus initial sur l'action principale du hero.
        assertFocused("Voir la fiche")

        // ← ouvre le menu sur la rubrique active (Accueil), pas sur l'icône la plus proche.
        key(Key.DirectionLeft)
        waitText("Paramètres")
        assertFocused("Accueil")
        shot("03_menu_ouvert")

        // Descendre jusqu'à Films sans valider puis → : retour exact sur l'élément de contenu quitté.
        key(Key.DirectionDown, 4)
        assertFocused("Films")
        key(Key.DirectionRight)
        assertFocused("Voir la fiche")
        // Depuis une affiche, ← rouvre le menu sur la rubrique active (Accueil), pas sur Films.
        key(Key.DirectionDown)
        key(Key.DirectionLeft)
        assertFocused("Accueil")

        // Ouvrir Films.
        key(Key.DirectionDown, 4)
        key(Key.DirectionCenter)
        waitText("Thrillers")
        waitText("Les Ombres de Dakar")
        shot("04_films")

        // Catégorie → grille → fiche.
        key(Key.DirectionRight)
        key(Key.DirectionCenter)
        waitText("Titre fournisseur")
        waitMenuClosed()
        shot("05_fiche_fournisseur")

        // Retour : revient à la grille Films.
        key(Key.Back)
        waitText("Thrillers")

        // ← depuis la grille : catégories, puis menu (sur Films, rubrique active).
        key(Key.DirectionLeft, 2)
        assertFocused("Films")
        key(Key.DirectionDown, 3)
        assertFocused("Paramètres")
        key(Key.DirectionCenter)
        waitText("Compte fournisseur")
        // Le focus est entré dans le contenu : le menu est replié.
        waitMenuClosed()
        waitText("éléments")
        shot("06_parametres")
    }
}

/** Android 15 avec rendu natif : génère les captures de design/captures-app/. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp-land-xhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UiFlowTest : UiFlowBase(screenshots = true)

/** Android 7.1 (API 25) = Fire OS 6 de la Fire TV Stick 4K 1re génération. Même parcours, sans capture. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [25], qualifiers = "w960dp-h540dp-land-xhdpi")
class UiFlowFireOs6Test : UiFlowBase(screenshots = false)
