package com.aditv.data.provider

/** Contrat commun des sources de catalogue. Toute implémentation de démonstration est identifiée comme telle. */
interface ProviderAdapter {
    val kind: ProviderKind

    /** Vérifie les accès. Lève [ProviderError]. */
    suspend fun authenticate(): AccountStatus

    suspend fun categories(type: MediaType): List<RawCategory>

    /** Diffuse les éléments par lots pour éviter de charger tout le catalogue en mémoire. */
    suspend fun streamItems(type: MediaType, batchSize: Int, onBatch: suspend (List<RawItem>) -> Unit)

    /** Saisons/épisodes d'une série, chargés à la demande. */
    suspend fun seriesEpisodes(seriesProviderId: String): List<RawEpisode>

    /** URL de lecture, ou null si la source ne permet pas la lecture (démo). Contient des secrets : ne jamais la stocker ni la journaliser. */
    fun streamUrl(target: StreamTarget): String?

    /** URL du guide XMLTV du fournisseur (secrète : contient les accès), ou null. */
    fun xmltvUrl(): String?

    /** Maintenant / ensuite pour une chaîne, via l'API courte du fournisseur (repli si pas de XMLTV). */
    suspend fun shortEpg(streamProviderId: String): List<RawShortProgramme>

    /** Guide XMLTV fourni en mémoire (démo uniquement), sinon null. */
    fun inlineXmltv(nowMs: Long): String? = null
}
