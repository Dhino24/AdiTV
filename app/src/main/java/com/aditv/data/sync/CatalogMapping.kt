package com.aditv.data.sync

import com.aditv.core.TitleNormalizer
import com.aditv.data.db.CatalogItemEntity
import com.aditv.data.db.CatalogItemSyncUpdate
import com.aditv.data.db.CategoryEntity
import com.aditv.data.db.CategorySyncUpdate
import com.aditv.data.provider.MediaType
import com.aditv.data.provider.RawCategory
import com.aditv.data.provider.RawItem

object CatalogMapping {
    fun itemId(accountId: String, type: MediaType, providerId: String) = "$accountId|${type.name}|$providerId"
    fun categoryId(accountId: String, type: MediaType, providerId: String) = "$accountId|${type.name}|cat:$providerId"

    fun toEntity(accountId: String, raw: RawItem, syncId: Long, now: Long): CatalogItemEntity {
        val c = TitleNormalizer.clean(raw.rawTitle)
        return CatalogItemEntity(
            id = itemId(accountId, raw.type, raw.providerId), accountId = accountId, type = raw.type.name,
            providerId = raw.providerId, rawTitle = raw.rawTitle, title = c.displayTitle, searchKey = c.searchKey,
            year = raw.releaseYear ?: c.year,
            categoryId = raw.categoryProviderId?.let { categoryId(accountId, raw.type, it) },
            imageUrl = raw.imageUrl, providerTmdbId = raw.providerTmdbId, providerRating = raw.providerRating,
            addedAt = raw.addedAtEpochSec, containerExt = raw.containerExt, epgChannelId = raw.epgChannelId,
            catchupDays = raw.catchupDays, plot = raw.plot, genre = raw.genre, position = raw.position,
            available = true, lastSeenSync = syncId, firstSeenAt = now,
        )
    }

    fun toUpdate(e: CatalogItemEntity) = CatalogItemSyncUpdate(
        e.id, e.rawTitle, e.title, e.searchKey, e.year, e.categoryId, e.imageUrl, e.providerTmdbId, e.providerRating,
        e.addedAt, e.containerExt, e.epgChannelId, e.catchupDays, e.plot, e.genre, e.position, e.available, e.lastSeenSync,
    )

    fun toCategory(accountId: String, type: MediaType, raw: RawCategory, syncId: Long) = CategoryEntity(
        id = categoryId(accountId, type, raw.providerId), accountId = accountId, type = type.name,
        providerId = raw.providerId, name = TitleNormalizer.clean(raw.name).displayTitle.ifBlank { raw.name },
        position = raw.position, present = true, lastSeenSync = syncId,
    )

    fun toCategoryUpdate(c: CategoryEntity) = CategorySyncUpdate(c.id, c.name, c.position, c.present, c.lastSeenSync)
}
