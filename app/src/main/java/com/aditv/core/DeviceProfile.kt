package com.aditv.core

import com.aditv.BuildConfig

/**
 * Limites selon l'appareil. La Stick 4K 1re génération (1,5 Go, 4 cœurs 1,7 GHz, 32 bits) garde des réglages prudents ;
 * le Cube 3e génération (2 Go, 8 cœurs, 64 bits) en a de plus généreux. Les garde-fous (calculs hors interface,
 * chargement par morceaux) restent actifs partout.
 */
object DeviceProfile {
    val highEnd: Boolean = BuildConfig.HIGH_END

    val label: String get() = if (highEnd) "Fire TV Cube (réglages étendus)" else "Fire TV Stick (réglages prudents)"
    /** Turbo Media3 : réserve maximale (ms) et plafond mémoire (octets). */
    val turboMaxBufferMs: Int get() = if (highEnd) 180_000 else 120_000
    val turboTargetBytes: Int get() = if (highEnd) 200 * 1024 * 1024 else 96 * 1024 * 1024
    /** VLC en Turbo : mise en cache réseau (ms). */
    val vlcTurboCachingMs: Int get() = if (highEnd) 12_000 else 8_000
    /** Part de la mémoire de l'appli pour le cache d'images. */
    val imageMemoryPercent: Double get() = if (highEnd) 0.25 else 0.15
    /** Direct, filtre « Toutes » : nombre de chaînes affichées. */
    val guideAllMax: Int get() = if (highEnd) 600 else 200
    /** Grilles : titres chargés par morceau. */
    val gridPage: Int get() = if (highEnd) 480 else 240
    /** Grandes images de fond TMDB : pleine qualité sur le Cube (55 pouces), 1280 px sur la Stick. */
    val backdropSize: String get() = if (highEnd) "original" else "w1280"
    /** Guide TV : attendre que l'on ne touche plus la télécommande (Stick) ou mettre à jour tout de suite (Cube). */
    val epgIdleMs: Long get() = if (highEnd) 0L else 90_000L
    /** Mises à jour de fond (catalogue, guide, TMDB) pendant une lecture : en pause sur la Stick, permises sur le Cube. */
    val backgroundDuringPlayback: Boolean get() = highEnd
    /** Délai avant les tâches de fond au démarrage. */
    val startupGraceMs: Long get() = if (highEnd) 4_000 else 12_000
}
