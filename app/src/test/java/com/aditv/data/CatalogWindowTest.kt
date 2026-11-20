package com.aditv.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aditv.data.db.AditvDatabase
import com.aditv.data.db.CatalogItemEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Grilles chargées par morceaux : tri fait par la base, limite respectée, total sans tout charger. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [25])
class CatalogWindowTest {
    private lateinit var db: AditvDatabase
    @Before fun setUp() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AditvDatabase::class.java).allowMainThreadQueries().build() }
    @After fun tearDown() = db.close()

    private fun item(id: Int, title: String, year: Int?, cat: String, added: Long?, variant: String? = null) = CatalogItemEntity(
        "a|MOVIE|$id", "a", "MOVIE", "$id", title, title, title.lowercase(), year, cat, null, null, null, added, null, null, null, null, null, id,
        true, 1, 1, variant ?: "k$id",
    )

    @Test fun windowSortsLimitsAndCounts() = runBlocking {
        db.catalog().insertItemsIgnore(listOf(
            item(1, "zorro", 1998, "c1", 100), item(2, "Avatar", 2009, "c1", 300), item(3, "Matrix", 1999, "c2", 200),
            item(4, "Avatar 4K", 2009, "c1", 50, variant = "k2"), item(5, "Inconnu", null, "c2", null),
        ))
        val dao = db.catalog()
        fun ids(cat: String?, sort: Int, limit: Int) = runBlocking { dao.cardsWindow("a", "MOVIE", cat, sort, limit).first().map { it.id.substringAfterLast('|').toInt() } }
        assertEquals("tout : ajouts récents d'abord, sans date en dernier", listOf(2, 3, 1, 4, 5), ids(null, 0, 10))
        assertEquals("limite", listOf(2, 3), ids(null, 0, 2))
        assertEquals("A → Z sans tenir compte de la casse", listOf(2, 4, 5, 3, 1), ids(null, 1, 10))
        assertEquals("plus récents, année inconnue en dernier", 5, ids(null, 2, 10).last())
        assertEquals("catégorie : ordre du fournisseur", listOf(1, 2, 4), ids("c1", 0, 10))
        assertEquals("versions regroupées dans le total", 4, dao.countCards("a", "MOVIE", null).first())
        assertEquals(2, dao.countCards("a", "MOVIE", "c1").first())
    }
}
