package com.aditv.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.aditv.data.db.AditvDatabase
import com.aditv.data.db.CatalogItemEntity
import com.aditv.data.db.EpgSourceEntity
import com.aditv.data.db.ProgrammeEntity
import com.aditv.data.db.TrendingEntity
import com.aditv.data.prefs.AppPrefs
import com.aditv.data.sport.SportMatcher
import com.aditv.data.sport.SportRepository
import com.aditv.data.tmdb.TmdbClient
import com.aditv.data.tmdb.TrendingRepository
import com.aditv.data.variants.VariantParser
import com.aditv.data.variants.VariantPrefs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [25])
class SportTest {
    private lateinit var db: AditvDatabase
    private val now = 1_795_000_000_000L
    private val h = 3_600_000L

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AditvDatabase::class.java).allowMainThreadQueries().build()
    }
    @After fun tearDown() = db.close()

    private fun item(id: String, type: String, raw: String, epg: String? = null, year: Int? = null): CatalogItemEntity {
        val v = VariantParser.parse(raw, type, null, year)
        val title = com.aditv.core.TitleNormalizer.clean(raw).displayTitle
        return CatalogItemEntity(
            "a|$type|$id", "a", type, id, raw, title, com.aditv.core.TitleNormalizer.searchKey(title), year, null, null, null, null, null, null, epg, null, null, null, null,
            true, 1, 1, v.key, v.lang, v.quality,
        )
    }

    // ---------- reconnaissance des équipes ----------
    private fun preset(k: String) = SportMatcher.PRESETS.first { it.key == k }
    private fun mentions(title: String, k: String) = preset(k).let { SportMatcher.mentions(title, it.aliases, it.excludes) }

    @Test fun teamsAreRecognisedWithoutFalsePositives() {
        assertTrue(mentions("Liga : FC Barcelone - Real Madrid", "barca"))
        assertTrue(mentions("Liga : FC Barcelone - Real Madrid", "real"))
        assertTrue("Barça avec cédille", mentions("Barça - Séville", "barca"))
        assertFalse("Espanyol exclu", mentions("Espanyol Barcelona - Getafe", "barca"))
        assertFalse("pas l'Atlético", mentions("Atlético Madrid - Séville", "real"))
        assertTrue(mentions("Paris Saint-Germain - OM", "psg"))
        assertTrue(mentions("PSG / Lens", "psg"))
        assertTrue(mentions("Man City v Aston Villa", "mancity"))
        assertTrue(mentions("Man City v Aston Villa", "villa"))
        assertTrue("accents ignorés", mentions("Sénégal - Côte d'Ivoire", "senegal"))
        // Un documentaire sur le pays n'est pas un match.
        assertFalse(SportMatcher.isSportContext("Fleuves d'Afrique : le Sénégal", "Documentaire", onSportChannel = false))
        assertTrue(SportMatcher.isSportContext("Sénégal - Côte d'Ivoire", null, onSportChannel = false))
        assertTrue(SportMatcher.isLikelyNotLive("PSG - OM (rediffusion)", null))
        assertTrue(SportMatcher.isSportChannel("FR| beIN SPORTS 1 HD", null))
        assertTrue(SportMatcher.isSportChannel("FR| RMC Sport 2", null))
        assertFalse(SportMatcher.isSportChannel("FR| TF1", "FR | Généralistes"))
    }

    // ---------- matchs, meilleure chaîne, rappels automatiques ----------
    @Test fun matchesPickBestChannelAndCreateAutoReminders() = runBlocking {
        val prefs = AppPrefs(ApplicationProvider.getApplicationContext())
        db.catalog().insertItemsIgnore(listOf(
            item("1", "LIVE", "FR| beIN SPORTS 1 HD", "bein1.fr"),
            item("2", "LIVE", "FR| beIN SPORTS 1 FHD", "bein1.fr"),
            item("3", "LIVE", "UK| Sky Sports Main Event", "sky.uk"),
            item("4", "LIVE", "FR| Savane", "savane.fr"),
        ))
        val src = db.live().insertSource(EpgSourceEntity(accountId = "a", kind = "url", url = null, label = "t", priority = 0))
        fun p(ch: String, title: String, startH: Double, cat: String? = null) =
            ProgrammeEntity(accountId = "a", sourceId = src, channelId = ch, startUtc = now + (startH * h).toLong(), endUtc = now + ((startH + 2) * h).toLong(),
                title = title, description = null, category = cat, episode = null, importStamp = 1)
        db.live().insertProgrammes(listOf(
            p("bein1.fr", "Liga : FC Barcelone - Real Madrid", 2.0),
            p("bein1.fr", "PSG - OM (rediffusion)", 5.0),
            p("savane.fr", "Fleuves d'Afrique : le Sénégal", 3.0, "Documentaire"),
            p("sky.uk", "Sénégal - Côte d'Ivoire", 20.0),
        ))
        val repo = SportRepository(db, prefs, scheduler = null) { now }
        SportMatcher.PRESETS.filter { it.key in SportMatcher.DEFAULT_FOLLOWS }.forEach { repo.follow("a", it) }

        val found = repo.refresh("a")
        val clasico = found.single { it.title.contains("Barcelone") }
        assertEquals("une seule entrée, deux équipes", listOf("FC Barcelone", "Real Madrid").sorted(), clasico.follows.sorted())
        assertEquals("version FHD retenue", "a|LIVE|2", clasico.channelItemId)
        assertTrue("documentaire écarté", found.none { it.title.startsWith("Fleuves") })
        assertTrue("Sénégal trouvé sur une chaîne étrangère", found.any { it.title.startsWith("Sénégal") })
        assertTrue(found.single { it.title.startsWith("PSG") }.notLive)

        val reminders = db.sport().observeUpcoming("a", now).first()
        assertEquals("rappels auto : clasico + Sénégal, pas la rediffusion", 2, reminders.size)
        val r = reminders.first { it.title.contains("Barcelone") }
        assertEquals(clasico.startUtc - 5 * 60_000, r.notifyAt)

        // Refusé une fois : jamais recréé.
        repo.deleteReminder(r.id)
        repo.refresh("a")
        assertEquals(1, db.sport().observeUpcoming("a", now).first().size)

        // Rappel manuel depuis le guide, puis suppression.
        assertTrue(repo.toggleReminder("a", "PSG - OM (rediffusion)", now + 5 * h, now + 7 * h, "a|LIVE|2", "beIN SPORTS 1"))
        assertFalse(repo.toggleReminder("a", "PSG - OM (rediffusion)", now + 5 * h, now + 7 * h, "a|LIVE|2", "beIN SPORTS 1"))

        val sportFr = repo.frenchSportChannels("a")
        assertEquals("beIN regroupé, pas Sky ni Savane", listOf("a|LIVE|2"), sportFr.map { it.id })
    }

    // ---------- Top 10 : tendances TMDB limitées à l'abonnement ----------
    @Test fun topTenKeepsTmdbOrderAndOnlyAvailableTitles() = runBlocking {
        db.catalog().insertItemsIgnore(listOf(
            item("10", "MOVIE", "FR| Dune : Deuxième partie (2024) VF 4K", year = 2024),
            item("11", "MOVIE", "FR| Dune : Deuxième partie (2024) VOSTFR", year = 2024),
            item("12", "MOVIE", "FR| Wicked (2024)", year = 2024),
        ))
        val trending = listOf(
            TrendingEntity("movie", 1, 101, "Absent du catalogue", null, 2025, null, null, now),
            TrendingEntity("movie", 2, 102, "Wicked", "Wicked", 2024, "/w.jpg", null, now),
            TrendingEntity("movie", 3, 103, "Dune : Deuxième partie", "Dune: Part Two", 2024, "/d.jpg", null, now),
        )
        val repo = TrendingRepository(db, TmdbClient(OkHttpClient(), { null })) { now }
        val top = repo.top("a", "movie", trending, VariantPrefs())
        assertEquals(listOf("Wicked", "Dune : Deuxième partie"), top.map { it.card.title })
        assertEquals(listOf(1, 2), top.map { it.rank })
        assertEquals("rang TMDB conservé", listOf(2, 3), top.map { it.tmdbRank })
        assertEquals("VF 4K préférée", "a|MOVIE|10", top[1].card.id)
    }
}
