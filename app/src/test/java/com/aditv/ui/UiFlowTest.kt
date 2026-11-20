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
        com.aditv.ui.intro.IntroGate.enabled = false
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

    private fun waitGone(text: String) = rule.waitUntil(5_000) {
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
        rule.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty()
    }

    @Test fun demoJourneyWithRemote() {
        waitText("Connectez votre abonnement")
        // Focus initial sur le premier champ, visible sans toucher l'écran.
        rule.waitUntil(5_000) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            rule.onAllNodes(androidx.compose.ui.test.isFocused()).fetchSemanticsNodes().any { n -> n.boundsInRoot.top in 150f..400f }
        }
        shot("01_connexion")

        // HTTP sans consentement : avertissement explicite avant toute requête.
        press("Tester la connexion")
        waitText("Renseignez l'identifiant")

        press("Essayer le mode démo")
        waitText("Votre soirée commence ici")
        // Focus initial sur l'action principale du carrousel.
        assertFocused("Lecture")
        shot("02_accueil")

        // ↑ : la barre de navigation, sur la rubrique active ; ↓ : retour exact sur l'élément quitté.
        key(Key.DirectionUp)
        assertFocused("Accueil")
        shot("03_menu")
        key(Key.DirectionDown)
        assertFocused("Lecture")
        // Rangées : ↓ descend dans les vignettes, Retour remonte sur la barre (rubrique active).
        key(Key.DirectionDown, 2)
        shot("03b_accueil_rangees")
        back()
        assertFocused("Accueil")

        // Films : puces de catégories, grille alignée.
        key(Key.DirectionRight, 2)
        assertFocused("Films")
        key(Key.DirectionCenter)
        waitText("Tous les films")
        assertFocused("Tous les films")
        waitText("Les Grands Fleuves") // le plus récent en premier
        key(Key.DirectionRight)
        assertFocused("Thrillers")
        key(Key.DirectionCenter)
        waitText("Fjord noir")
        shot("04_films")

        // Grille → fiche.
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        waitText("Lecture indisponible (démo)")
        assertFocused("Lecture indisponible (démo)")
        waitText("Ajoutez votre clé TMDB")
        shot("05_fiche")

        // → puis OK : ajout à Ma liste (bouton rond, libellé affiché au focus).
        key(Key.DirectionRight)
        waitText("Ajouter à Ma liste")
        key(Key.DirectionCenter)
        waitText("Dans Ma liste")

        // Retour système : la fiche se ferme, retour sur la grille Films.
        back()
        waitGone("Lecture indisponible (démo)")
        waitText("Fjord noir")

        // ↑ ↑ : puces puis barre (sur Films) ; → → Ma liste.
        key(Key.DirectionUp, 2)
        assertFocused("Films")
        key(Key.DirectionRight, 2)
        assertFocused("Ma liste")
        key(Key.DirectionCenter)
        waitText("Chaînes favorites")
        shot("06_ma_liste")

        // Direct : aperçu + guide (programmes fictifs de la démo), panneau de détail, Retour.
        key(Key.DirectionUp)
        key(Key.DirectionLeft, 3)
        assertFocused("Direct")
        key(Key.DirectionCenter)
        waitText("fictif n°")
        waitText("Couverture")
        shot("07_direct")
        key(Key.DirectionCenter)
        waitText("Fiche de la chaîne")
        shot("08_programme")
        back()
        waitGone("Fiche de la chaîne")

        // Recherche : clavier à l'écran ; une seule carte par chaîne (variantes regroupées).
        back()
        assertFocused("Direct")
        key(Key.DirectionRight, 4)
        key(Key.DirectionCenter)
        waitText("Saisissez au moins 2 caractères")
        listOf("i", "n", "f", "o").forEach { press(it) }
        waitText("Info 24 (démo)")
        assertEquals("variantes regroupées", 1, rule.onAllNodesWithText("Info 24 (démo)", substring = true).fetchSemanticsNodes().size)
        shot("09_recherche")

        // Réglages : ↑ vers la barre, → (icône réglages).
        key(Key.DirectionUp, 2)
        key(Key.DirectionRight)
        key(Key.DirectionCenter)
        waitText("COMPTE FOURNISSEUR")
        waitText("éléments")
        shot("10_reglages")

        // Retour : barre → Accueil → barre → « Quitter ADITV ? » ; Retour referme la question.
        back(); back()
        waitText("Votre soirée commence ici")
        back(); back()
        waitText("Quitter ADITV ?")
        shot("11_quitter")
        back()
        waitGone("Quitter ADITV ?")
    }

    /** Ouverture : logo animé au démarrage à froid, puis l'application (sans attendre le réseau). */
    @Test fun introThenApp() {
        com.aditv.ui.intro.IntroGate.played = false
        com.aditv.ui.intro.IntroGate.enabled = true
        rule.mainClock.autoAdvance = false
        rule.activityRule.scenario.recreate()
        // Horloge de composition pilotée : capture à mi-animation (nom en cours de dévoilement, reflet).
        repeat(26) { rule.mainClock.advanceTimeBy(50); shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50)) }
        if (screenshots) {
            val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
            File(outDir, "00_ouverture.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        rule.mainClock.autoAdvance = true
        waitText("Connectez votre abonnement")
        rule.waitUntil(5_000) { shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50)); com.aditv.ui.intro.IntroGate.played }
        com.aditv.ui.intro.IntroGate.enabled = false
    }


    /** Réglages → Sport : équipes demandées suivies d'office, délai, rappels. */
    @Test fun sportSettings() {
        waitText("Connectez votre abonnement")
        press("Essayer le mode démo")
        waitText("Votre soirée commence ici")
        key(Key.DirectionUp)
        key(Key.DirectionRight, 6)
        key(Key.DirectionCenter)
        waitText("COMPTE FOURNISSEUR")
        waitText("Sénégal")
        // ↓ jusqu'aux équipes suivies (celles demandées sont cochées d'office).
        var n = 0
        while (n++ < 25 && rule.onAllNodes(hasText("FC Barcelone", substring = true) and androidx.compose.ui.test.isFocused()).fetchSemanticsNodes().isEmpty()) key(Key.DirectionDown)
        assertFocused("✓ FC Barcelone")
        shot("14_reglages_sport")
    }

    /** Fiche série (saisons, épisodes) depuis l'onglet Séries. */
    @Test fun seriesDetailWithEpisodes() {
        waitText("Connectez votre abonnement")
        press("Essayer le mode démo")
        waitText("Votre soirée commence ici")
        key(Key.DirectionUp)
        key(Key.DirectionRight, 3)
        assertFocused("Séries")
        key(Key.DirectionCenter)
        waitText("Toutes les séries")
        key(Key.DirectionDown)
        key(Key.DirectionCenter)
        waitText("SÉRIE")
        waitText("Saison 1")
        shot("12_fiche_serie")
        key(Key.DirectionDown)
        shot("13_episodes")
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
