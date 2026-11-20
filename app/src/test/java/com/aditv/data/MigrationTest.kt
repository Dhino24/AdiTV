package com.aditv.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.aditv.data.db.AditvDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** La mise à jour 0.1.x → 0.2 conserve compte et catalogue (aucune migration destructive). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [25])
class MigrationTest {
    @get:Rule val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AditvDatabase::class.java)

    @Test fun v1ToV3KeepsData() {
        helper.createDatabase("m.db", 1).apply {
            execSQL("INSERT INTO accounts (id, kind, endpoint, secretRef, cleartextConsent, createdAt) VALUES ('a', 'XTREAM', 'https://p', 'r', 0, 1)")
            execSQL("""INSERT INTO catalog_items (id, accountId, type, providerId, rawTitle, title, searchKey, available, lastSeenSync, firstSeenAt)
                       VALUES ('a|MOVIE|1', 'a', 'MOVIE', '1', 'FR| X', 'X', 'x', 1, 1, 1)""")
            close()
        }
        val db = helper.runMigrationsAndValidate("m.db", 2, true)
        db.query("SELECT COUNT(*) FROM catalog_items").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        db.query("SELECT liveFormats FROM accounts WHERE id = 'a'").use { it.moveToFirst(); assertEquals(true, it.isNull(0)) }
        db.close()
        val v3 = helper.runMigrationsAndValidate("m.db", 3, true)
        v3.query("SELECT variantKey FROM catalog_items").use { it.moveToFirst(); assertEquals("recalculé au démarrage", true, it.isNull(0)) }
        v3.close()
        val v4 = helper.runMigrationsAndValidate("m.db", 4, true)
        v4.query("SELECT categoryCount FROM sync_state").use { }
        val v3x = v4
        v3x.execSQL("INSERT INTO favorite_channels (accountId, itemId, addedAt) VALUES ('a','a|LIVE|1',1)")
        v3x.execSQL("INSERT INTO watch_progress (accountId, contentId, kind, seriesItemId, positionMs, durationMs, updatedAt, completed) VALUES ('a','a|MOVIE|1','MOVIE',NULL,1,2,3,0)")
        v3x.close()
        // v5 : tables sport et tendances ajoutées, données existantes intactes.
        val v5 = helper.runMigrationsAndValidate("m.db", 5, true)
        v5.query("SELECT COUNT(*) FROM favorite_channels").use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
        v5.execSQL("INSERT INTO follows (accountId, `key`, name, aliases, excludes, kind, createdAt) VALUES ('a','psg','PSG','psg','','team',1)")
        v5.query("SELECT COUNT(*) FROM reminders").use { it.moveToFirst(); assertEquals(0, it.getInt(0)) }
        v5.close()
    }
}
