package com.aditv.ui.cinema

import com.aditv.core.DeviceProfile
import com.aditv.data.db.CatalogCard
import com.aditv.di.AppContainer
import com.aditv.ui.common.tmdbImage
import com.aditv.ui.design.Spot

/** Vedette immédiate (sans accès disque) : affichée dès que la carte reçoit le focus. */
fun quickSpot(card: CatalogCard, kicker: String?) = Spot(
    card.id, card.title, kicker, listOfNotNull(card.year?.toString()),
    backdrop = tmdbImage(card.tmdbBackdrop, "w1280"), poster = tmdbImage(card.tmdbPoster, "w500") ?: card.imageUrl,
    logo = tmdbImage(card.tmdbLogo, "w500"), lang = card.lang, quality = card.quality,
)

/** Vedette complète : résumé, genre, durée et grand fond TMDB quand la correspondance est confirmée. */
suspend fun AppContainer.spotFor(card: CatalogCard, kicker: String?): Spot {
    val m = db.metadata().match(card.id)?.takeIf { it.status == "confirmed" }?.tmdbId
        ?.let { db.metadata().metadata(if (card.type == "SERIES") "tv" else "movie", it) }
    return Spot(
        key = card.id, title = m?.title ?: card.title,
        kicker = kicker ?: if (card.type == "SERIES") "Série" else "Film",
        meta = listOfNotNull(
            m?.releaseDate?.take(4) ?: card.year?.toString(),
            (m?.genres?.split(", ")?.firstOrNull() ?: card.genre)?.substringBefore(',')?.trim(),
            m?.runtimeMin?.takeIf { it > 0 }?.let { if (it >= 60) "${it / 60} h ${"%02d".format(it % 60)}" else "$it min" },
        ),
        overview = m?.overview,
        backdrop = tmdbImage(m?.backdropPath ?: card.tmdbBackdrop, DeviceProfile.backdropSize),
        poster = tmdbImage(card.tmdbPoster, "w500") ?: card.imageUrl,
        logo = tmdbImage(m?.logoPath ?: card.tmdbLogo, "w500"),
        lang = card.lang, quality = card.quality, trailer = m?.trailerKey,
    )
}
