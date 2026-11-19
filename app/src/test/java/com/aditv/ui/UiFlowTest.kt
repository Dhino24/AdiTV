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
import org.junit.Assert.assertEquals
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

    /** Touche Retour de la télécommande : passe par le gestionnaire Retour de l'activité, comme sur l'appareil. */
    private fun back() {
        rule.activityRule.scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300))
        rule.waitForIdle()
    }

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

    /** Au moins un nœud portant ce texte a le focus (un même libellé peut apparaître dans le menu et l'écran). */
    private fun assertFocused(text: String): Unit = try {
        val focused = rule.onAllNodes(hasText(text) and androidx.compose.ui.test.isFocused()).fetchSemanticsNodes()
        if (focused.isEmpty()) throw AssertionError("« $text » n'a pas le focus") else Unit
    } catch (e: AssertionError) { shot("echec_focus_" + text.take(12)); throw AssertionError("Focus actuel : " + focusedDump(), e) }

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
        waitText("fournisseur : «")
        waitMenuClosed()
        // Démo : la lecture est annoncée indisponible, sans faux lecteur.
        assertFocused("Lecture indisponible (démo)")
        waitText("Ajoutez votre clé TMDB")
        shot("05_fiche")

        // → puis OK : ajout à Ma liste.
        key(Key.DirectionRight)
        assertFocused("+ Ma liste")
        key(Key.DirectionCenter)
        waitText("✓ Dans ma liste")

        // Retour système : la fiche se ferme, retour à la grille Films.
        back()
        rule.waitUntil(5_000) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            rule.onAllNodesWithText("fournisseur : «", substring = true).fetchSemanticsNodes().isEmpty()
        }
        waitText("Comédies")

        // ← depuis la grille : catégories, puis menu (sur Films, rubrique active).
        key(Key.DirectionLeft, 2)
        assertFocused("Films")
        // Ma liste (2 crans plus bas que Films) contient le film ajouté.
        key(Key.DirectionDown, 2)
        assertFocused("Ma liste")
        key(Key.DirectionCenter)
        waitText("Les Ombres de Dakar")
        shot("06_ma_liste")
        key(Key.DirectionLeft)
        assertFocused("Ma liste")

        // Direct : une seule carte par chaîne, la meilleure version (« Sport Plus » SD / FHD / 4K → 4K).
        key(Key.DirectionUp, 4)
        assertFocused("Direct")
        key(Key.DirectionCenter)
        waitText("Information")
        waitText("Info 24 (démo)")
        assertEquals("variantes regroupées", 1, rule.onAllNodesWithText("Info 24 (démo)", substring = true).fetchSemanticsNodes().size)
        waitText("2 versions")
        shot("07_direct")

        // Guide TV : programmes fictifs de la démo, panneau de détail, Retour.
        key(Key.DirectionLeft, 2)
        assertFocused("Direct")
        key(Key.DirectionDown)
        assertFocused("Guide TV")
        key(Key.DirectionCenter)
        waitText("fictif n°")
        waitText("Couverture")
        waitMenuClosed()
        shot("08_guide")
        key(Key.DirectionCenter)
        waitText("Fiche de la chaîne")
        shot("09_programme")
        back()
        rule.waitUntil(5_000) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            rule.onAllNodesWithText("Fiche de la chaîne").fetchSemanticsNodes().isEmpty()
        }

        // Vers les paramètres : ← (chaîne) ← (menu sur Guide TV) puis ↓ ×4.
        key(Key.DirectionLeft, 2)
        assertFocused("Guide TV")
        key(Key.DirectionDown, 4)
        assertFocused("Paramètres")
        key(Key.DirectionCenter)
        waitText("Compte fournisseur")
        // Le focus est entré dans le contenu : le menu est replié.
        waitMenuClosed()
        waitText("éléments")
        shot("10_parametres")
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
