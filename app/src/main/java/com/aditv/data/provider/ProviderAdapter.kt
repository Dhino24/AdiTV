package com.aditv.data.provider

/** Contrat commun des sources de catalogue. Toute implémentation de démonstration est identifiée comme telle. */
interface ProviderAdapter {
    val kind: ProviderKind

    /** Vérifie les accès. Lève [ProviderError]. */
    suspend fun authenticate(): AccountStatus

    suspend fun categories(type: MediaType): List<RawCategory>

    /** Diffuse les éléments par lots pour éviter de charger tout le catalogue en mémoire. */
    suspend fun streamItems(type: MediaType, batchSize: Int, onBatch: suspend (List<RawItem>) -> Unit)
}
