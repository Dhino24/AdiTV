package com.aditv.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Chaînes favorites : distinctes de « Ma liste » (films/séries) et de l'historique. */
@Entity(tableName = "favorite_channels", primaryKeys = ["accountId", "itemId"])
data class FavoriteChannelEntity(val accountId: String, val itemId: String, val addedAt: Long)

/**
 * Source de guide. `provider` = XMLTV du fournisseur (URL reconstruite avec les accès chiffrés, jamais stockée) ;
 * `url` = source XMLTV ajoutée par l'utilisateur. Priorité croissante = plus prioritaire en premier.
 */
@Entity(tableName = "epg_sources", indices = [Index("accountId")])
data class EpgSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: String,
    val kind: String,
    val url: String?,
    val label: String,
    val priority: Int,
    val enabled: Boolean = true,
    /** Décalage manuel appliqué à toute la source (minutes), affiché clairement. */
    val offsetMinutes: Int = 0,
    val lastFetchAt: Long? = null,
    val lastError: String? = null,
    val programmeCount: Int = 0,
    val channelCount: Int = 0,
    /** Horaires sans fuseau rencontrés (interprétés avec le fuseau du serveur). */
    val ambiguousTimes: Int = 0,
)

/** Chaînes déclarées par une source XMLTV (pour l'association manuelle). */
@Entity(tableName = "epg_channels", primaryKeys = ["sourceId", "channelId"], indices = [Index("accountId", "searchKey")])
data class EpgChannelEntity(
    val sourceId: Long,
    val accountId: String,
    val channelId: String,
    val displayName: String,
    val searchKey: String,
    val iconUrl: String?,
)

/** Association chaîne du catalogue → identifiant de guide, si différente de celle du fournisseur, et décalage par chaîne. */
@Entity(tableName = "epg_mapping", indices = [Index("accountId")])
data class EpgMappingEntity(
    @PrimaryKey val itemId: String,
    val accountId: String,
    val epgChannelId: String?,
    val manual: Boolean,
    val offsetMinutes: Int = 0,
    /** « Pas de guide » choisi par l'utilisateur. */
    val disabled: Boolean = false,
)

/** Programme : horaires en UTC (ms). Clé de déduplication : source + chaîne + début. */
@Entity(
    tableName = "programmes",
    indices = [
        Index("accountId", "channelId", "startUtc"),
        Index(value = ["sourceId", "channelId", "startUtc"], unique = true),
        Index("endUtc"),
    ],
)
data class ProgrammeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: String,
    val sourceId: Long,
    val channelId: String,
    val startUtc: Long,
    val endUtc: Long,
    val title: String,
    val description: String?,
    val category: String?,
    val episode: String?,
    val importStamp: Long,
)
