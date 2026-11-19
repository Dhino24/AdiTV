package com.aditv.tmdb

import com.aditv.core.TitleNormalizer
import com.aditv.data.tmdb.TmdbCandidate
import com.aditv.data.tmdb.TmdbMatcher
import com.aditv.data.tmdb.TmdbMatcher.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TmdbMatcherTest {
    private fun m(id: Int, title: String, year: Int?, original: String? = null, pop: Double = 10.0) = TmdbCandidate(id, "movie", title, original, year, pop)

    private val duneRemakes = listOf(m(438631, "Dune", 2021, pop = 90.0), m(841, "Dune", 1984, pop = 30.0), m(1, "Dune : Deuxième partie", 2024, "Dune: Part Two"))

    @Test fun remakeIsResolvedByYear() {
        val d = TmdbMatcher.decide("Dune", 1984, duneRemakes)
        assertEquals(Status.CONFIRMED, d.status)
        assertEquals(841, d.best!!.id)
    }

    @Test fun remakeWithoutYearStaysAmbiguousEvenIfOneIsMorePopular() {
        val d = TmdbMatcher.decide("Dune", null, duneRemakes)
        assertEquals(Status.AMBIGUOUS, d.status)
    }

    @Test fun providerTitleIsCleanedBeforeMatching() {
        val raw = TitleNormalizer.clean("FR| Intouchables (2011) VOSTFR 1080p")
        val d = TmdbMatcher.decide(raw.displayTitle, raw.year, listOf(m(77338, "Intouchables", 2011), m(9, "Les Intouchables", 1987)))
        assertEquals(Status.CONFIRMED, d.status)
        assertEquals(77338, d.best!!.id)
    }

    @Test fun originalTitleCountsAndOneYearShiftIsTolerated() {
        val d = TmdbMatcher.decide("The Intouchables", 2012, listOf(m(77338, "Intouchables", 2011, "The Intouchables")))
        assertEquals(Status.CONFIRMED, d.status)
    }

    @Test fun sequelIsNotConfusedWithOriginal() {
        val d = TmdbMatcher.decide("Alien", 1979, listOf(m(348, "Alien, le huitième passager", 1979, "Alien"), m(945961, "Alien : Romulus", 2024, "Alien: Romulus")))
        assertEquals(348, d.best!!.id)
        assertNotEquals(Status.UNMATCHED, d.status)
    }

    @Test fun wrongYearPreventsAutomaticConfirmation() {
        val d = TmdbMatcher.decide("Le Dernier Ferry", 2023, listOf(m(5, "Le Dernier Ferry", 1998)))
        assertNotEquals(Status.CONFIRMED, d.status)
    }

    @Test fun unrelatedResultsAreUnmatched() {
        val d = TmdbMatcher.decide("Les Ombres de Dakar", 2026, listOf(m(7, "Dakar Express", 2009), m(8, "Ombres chinoises", 1990)))
        assertEquals(Status.UNMATCHED, d.status)
    }
}
