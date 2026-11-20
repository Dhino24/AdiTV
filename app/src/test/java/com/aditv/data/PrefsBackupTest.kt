package com.aditv.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aditv.data.db.AditvDatabase
import com.aditv.data.db.CatalogItemEntity
import com.aditv.data.db.FavoriteChannelEntity
import com.aditv.data.prefs.AppPrefs
import com.aditv.data.prefs.PrefsBackup
import com.aditv.data.sport.SportMatcher
import com.aditv.data.sport.SportRepository
import com.aditv.playback.engine.EngineChoice
import com.aditv.playback.engine.EngineStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Sauvegarde puis restauration sur une base vide (réinstallation) : préférences, suivis, favoris ; aucun secret. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [25])
class PrefsBackupTest {
    private fun ch(db: AditvDatabase, id: String, raw: String) = runBlocking {
        db.catalog().insertItemsIgnore(listOf(CatalogItemEntity("a|LIVE|$id", "a", "LIVE", id, raw, raw, raw.lowercase(), null, null, null, null, null, null, null, null, null, null, null, null, true, 1, 1)))
    }

    @Test fun exportThenImportOnFreshDatabase() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val prefs = AppPrefs(ctx)
        val engines = EngineStore(ctx)
        val db1 = Room.inMemoryDatabaseBuilder(ctx, AditvDatabase::class.java).allowMainThreadQueries().build()
        val sport1 = SportRepository(db1, prefs, null)
        ch(db1, "1", "FR| beIN SPORTS 1 HD")
        db1.live().addFavorite(FavoriteChannelEntity("a", "a|LIVE|1", 1))
        sport1.follow("a", SportMatcher.PRESETS.first { it.key == "psg" })
        prefs.setTextScale(115); prefs.setReminderLead(15)
        engines.defaultChoice = EngineChoice.VLC
        val file = PrefsBackup(ctx, db1, prefs, engines, sport1).export("a")
        val text = file.readText()
        assertFalse("aucun secret exporté", text.contains("password", ignoreCase = true) || text.contains("tmdb_key"))
        db1.close()

        // Nouvelle installation : base vide, préférences remises à zéro.
        prefs.setTextScale(100); prefs.setReminderLead(5); engines.defaultChoice = EngineChoice.AUTO
        val db2 = Room.inMemoryDatabaseBuilder(ctx, AditvDatabase::class.java).allowMainThreadQueries().build()
        ch(db2, "77", "FR| beIN SPORTS 1 HD") // même chaîne, autre identifiant fournisseur
        val sport2 = SportRepository(db2, prefs, null)
        val summary = PrefsBackup(ctx, db2, prefs, engines, sport2).import("a")
        assertTrue(summary, summary.contains("1 équipe"))
        assertEquals(115, prefs.textScalePercent.first())
        assertEquals(15, prefs.reminderLeadMinutes.first())
        assertEquals(EngineChoice.VLC, engines.defaultChoice)
        assertEquals(listOf("a|LIVE|77"), db2.live().favoriteIds("a").first())
        assertEquals(listOf("psg"), db2.sport().follows("a").map { it.key })
        db2.close()
        // Remise à zéro : les préférences sont partagées entre les tests du même processus.
        engines.defaultChoice = EngineChoice.AUTO; prefs.setTextScale(100); prefs.setReminderLead(5)
        Unit
    }
}
