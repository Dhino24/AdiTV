package com.aditv.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Compte fournisseur. Aucun identifiant ni mot de passe ici : seulement une référence vers le SecretStore. */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val kind: String,
    /** Adresse canonique, sans identifiants. */
    val endpoint: String,
    val secretRef: String?,
    /** L'utilisateur a accepté explicitement HTTP en clair pour cet hôte. */
    val cleartextConsent: Boolean,
    val createdAt: Long,
    val status: String?,
    val expiresAtEpochSec: Long?,
    val maxConnections: Int?,
    val activeConnections: Int?,
    val isTrial: Boolean?,
    val serverTimezone: String?,
    val statusCheckedAt: Long?,
    /** Formats de flux direct déclarés (ex. « ts,m3u8 »), null si non communiqués. */
    val liveFormats: String? = null,
)

/** Identifiant local = accountId|type|providerId : les IDs fournisseur ne sont jamais réutilisés entre comptes. */
@Entity(
    tableName = "categories",
    indices = [Index("accountId", "type", "present", "position")],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val type: String,
    val providerId: String,
    val name: String,
    val position: Int,
    val present: Boolean,
    val lastSeenSync: Long,
    /** Préférence locale, préservée par les synchronisations. */
    @ColumnInfo(defaultValue = "0") val hidden: Boolean = false,
)

@Entity(
    tableName = "catalog_items",
    indices = [
        Index("accountId", "type", "categoryId"),
        Index("accountId", "type", "addedAt"),
        Index("accountId", "searchKey"),
    ],
)
data class CatalogItemEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    val type: String,
    val providerId: String,
    val rawTitle: String,
    val title: String,
    val searchKey: String,
    val year: Int?,
    val categoryId: String?,
    val imageUrl: String?,
    val providerTmdbId: String?,
    val providerRating: String?,
    /** Date d'ajout déclarée par le fournisseur (secondes), si plausible. */
    val addedAt: Long?,
    val containerExt: String?,
    val epgChannelId: String?,
    val catchupDays: Int?,
    val plot: String?,
    val genre: String?,
    val position: Int?,
    /** Faux si absent de la dernière synchronisation réussie : jamais supprimé, pour garder progression et favoris. */
    val available: Boolean,
    val lastSeenSync: Long,
    val firstSeenAt: Long,
    /** Regroupement des variantes (langue/qualité annoncées par le titre fournisseur). */
    val variantKey: String? = null,
    val lang: String? = null,
    val quality: Int? = null,
)

/** Champs mis à jour par une synchronisation (firstSeenAt et préférences locales préservés). */
data class CatalogItemSyncUpdate(
    val id: String,
    val rawTitle: String,
    val title: String,
    val searchKey: String,
    val year: Int?,
    val categoryId: String?,
    val imageUrl: String?,
    val providerTmdbId: String?,
    val providerRating: String?,
    val addedAt: Long?,
    val containerExt: String?,
    val epgChannelId: String?,
    val catchupDays: Int?,
    val plot: String?,
    val genre: String?,
    val position: Int?,
    val available: Boolean,
    val lastSeenSync: Long,
    val variantKey: String?,
    val lang: String?,
    val quality: Int?,
)

data class CategorySyncUpdate(
    val id: String,
    val name: String,
    val position: Int,
    val present: Boolean,
    val lastSeenSync: Long,
)

@Entity(tableName = "sync_state", primaryKeys = ["accountId", "type"])
data class SyncStateEntity(
    val accountId: String,
    val type: String,
    val lastSuccessAt: Long?,
    val lastAttemptAt: Long?,
    val itemCount: Int,
    val lastError: String?,
)

/** Projection légère pour les grilles et rangées. */
data class CatalogCard(
    val id: String,
    val type: String,
    val title: String,
    val year: Int?,
    val imageUrl: String?,
    val genre: String?,
    /** Affiche TMDB si la correspondance est confirmée. */
    val tmdbPoster: String? = null,
    val variantKey: String? = null,
    val lang: String? = null,
    val quality: Int? = null,
    val epgChannelId: String? = null,
    val categoryId: String? = null,
)
