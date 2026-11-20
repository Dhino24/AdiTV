package com.aditv.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Upsert suspend fun upsert(a: AccountEntity)
    @Query("SELECT * FROM accounts WHERE id = :id") suspend fun get(id: String): AccountEntity?
    @Query("SELECT * FROM accounts WHERE id = :id") fun observe(id: String): Flow<AccountEntity?>
    @Query("DELETE FROM accounts WHERE id = :id") suspend fun delete(id: String)
}

@Dao
interface CatalogDao {
    // ---------- écriture (synchronisation) ----------
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertItemsIgnore(items: List<CatalogItemEntity>)
    @Update(entity = CatalogItemEntity::class) suspend fun updateItems(items: List<CatalogItemSyncUpdate>)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertCategoriesIgnore(c: List<CategoryEntity>)
    @Update(entity = CategoryEntity::class) suspend fun updateCategories(c: List<CategorySyncUpdate>)

    @Transaction
    suspend fun upsertItems(full: List<CatalogItemEntity>, updates: List<CatalogItemSyncUpdate>) {
        insertItemsIgnore(full)
        updateItems(updates)
    }

    @Transaction
    suspend fun upsertCategories(full: List<CategoryEntity>, updates: List<CategorySyncUpdate>) {
        insertCategoriesIgnore(full)
        updateCategories(updates)
    }

    @Query("UPDATE catalog_items SET available = 0 WHERE accountId = :accountId AND type = :type AND lastSeenSync <> :syncId AND available = 1")
    suspend fun markMissingItems(accountId: String, type: String, syncId: Long): Int

    @Query("UPDATE categories SET present = 0 WHERE accountId = :accountId AND type = :type AND lastSeenSync <> :syncId")
    suspend fun markMissingCategories(accountId: String, type: String, syncId: Long): Int

    @Upsert suspend fun upsertSyncState(s: SyncStateEntity)
    @Query("SELECT * FROM sync_state WHERE accountId = :accountId") fun observeSyncStates(accountId: String): Flow<List<SyncStateEntity>>
    @Query("SELECT * FROM sync_state WHERE accountId = :accountId") suspend fun syncStates(accountId: String): List<SyncStateEntity>

    @Query("DELETE FROM catalog_items WHERE accountId = :accountId") suspend fun deleteItems(accountId: String)
    @Query("DELETE FROM categories WHERE accountId = :accountId") suspend fun deleteCategories(accountId: String)
    @Query("DELETE FROM sync_state WHERE accountId = :accountId") suspend fun deleteSyncStates(accountId: String)

    // ---------- lecture (UI) ----------
    @Query("SELECT * FROM categories WHERE accountId = :accountId AND type = :type AND present = 1 AND hidden = 0 ORDER BY position")
    fun categories(accountId: String, type: String): Flow<List<CategoryEntity>>

    @Query(
        """SELECT ci.id, ci.type, COALESCE(tm.title, ci.title) AS title, ci.year, ci.imageUrl, ci.genre, tm.posterPath AS tmdbPoster, tm.backdropPath AS tmdbBackdrop, tm.logoPath AS tmdbLogo, ci.variantKey, ci.lang, ci.quality, ci.epgChannelId, ci.categoryId
           FROM catalog_items ci
           LEFT JOIN metadata_match mm ON mm.itemId = ci.id AND mm.status = 'confirmed'
           LEFT JOIN tmdb_metadata tm ON tm.mediaType = mm.mediaType AND tm.tmdbId = mm.tmdbId
           WHERE ci.accountId = :accountId AND ci.type = :type AND ci.categoryId = :categoryId AND ci.available = 1
           ORDER BY ci.position IS NULL, ci.position, ci.title"""
    )
    fun cardsInCategory(accountId: String, type: String, categoryId: String): Flow<List<CatalogCard>>

    @Query(
        """SELECT ci.id, ci.type, COALESCE(tm.title, ci.title) AS title, ci.year, ci.imageUrl, ci.genre, tm.posterPath AS tmdbPoster, tm.backdropPath AS tmdbBackdrop, tm.logoPath AS tmdbLogo, ci.variantKey, ci.lang, ci.quality, ci.epgChannelId, ci.categoryId
           FROM catalog_items ci
           LEFT JOIN metadata_match mm ON mm.itemId = ci.id AND mm.status = 'confirmed'
           LEFT JOIN tmdb_metadata tm ON tm.mediaType = mm.mediaType AND tm.tmdbId = mm.tmdbId
           WHERE ci.accountId = :accountId AND ci.type = :type AND ci.available = 1 AND ci.addedAt IS NOT NULL
           ORDER BY ci.addedAt DESC LIMIT :limit"""
    )
    fun recentlyAdded(accountId: String, type: String, limit: Int): Flow<List<CatalogCard>>

    @Query(
        """SELECT ci.id, ci.type, COALESCE(tm.title, ci.title) AS title, ci.year, ci.imageUrl, ci.genre, tm.posterPath AS tmdbPoster, tm.backdropPath AS tmdbBackdrop, tm.logoPath AS tmdbLogo, ci.variantKey, ci.lang, ci.quality, ci.epgChannelId, ci.categoryId
           FROM catalog_items ci
           LEFT JOIN metadata_match mm ON mm.itemId = ci.id AND mm.status = 'confirmed'
           LEFT JOIN tmdb_metadata tm ON tm.mediaType = mm.mediaType AND tm.tmdbId = mm.tmdbId
           WHERE ci.accountId = :accountId AND ci.type = :type AND ci.available = 1
           ORDER BY ci.position IS NULL, ci.position, ci.title LIMIT :limit"""
    )
    fun firstItems(accountId: String, type: String, limit: Int): Flow<List<CatalogCard>>

    @Query(
        """SELECT ci.id, ci.type, COALESCE(tm.title, ci.title) AS title, ci.year, ci.imageUrl, ci.genre, tm.posterPath AS tmdbPoster, tm.backdropPath AS tmdbBackdrop, tm.logoPath AS tmdbLogo, ci.variantKey, ci.lang, ci.quality, ci.epgChannelId, ci.categoryId
           FROM catalog_items ci
           LEFT JOIN metadata_match mm ON mm.itemId = ci.id AND mm.status = 'confirmed'
           LEFT JOIN tmdb_metadata tm ON tm.mediaType = mm.mediaType AND tm.tmdbId = mm.tmdbId
           WHERE ci.accountId = :accountId AND ci.available = 1 AND ci.searchKey LIKE '%' || :key || '%'
           ORDER BY (ci.searchKey LIKE :key || '%') DESC, ci.type, ci.title LIMIT :limit"""
    )
    suspend fun search(accountId: String, key: String, limit: Int): List<CatalogCard>

    @Query(
        """SELECT ci.id, ci.type, COALESCE(tm.title, ci.title) AS title, ci.year, ci.imageUrl, ci.genre, tm.posterPath AS tmdbPoster, tm.backdropPath AS tmdbBackdrop, tm.logoPath AS tmdbLogo, ci.variantKey, ci.lang, ci.quality, ci.epgChannelId, ci.categoryId
           FROM catalog_items ci
           LEFT JOIN metadata_match mm ON mm.itemId = ci.id AND mm.status = 'confirmed'
           LEFT JOIN tmdb_metadata tm ON tm.mediaType = mm.mediaType AND tm.tmdbId = mm.tmdbId
           WHERE ci.accountId = :accountId AND ci.type = :type AND ci.available = 1
           ORDER BY ci.addedAt IS NULL, ci.addedAt DESC, ci.title"""
    )
    fun allCards(accountId: String, type: String): Flow<List<CatalogCard>>

    /**
     * Grille Films / Séries / Chaînes chargée par morceaux : seulement les [limit] premiers titres dans l'ordre demandé
     * (0 = fournisseur, 1 = A → Z, 2 = plus récents). [categoryId] null = tout le type (ordre : ajouts récents).
     */
    @Query(
        """SELECT ci.id, ci.type, COALESCE(tm.title, ci.title) AS title, ci.year, ci.imageUrl, ci.genre, tm.posterPath AS tmdbPoster, tm.backdropPath AS tmdbBackdrop, tm.logoPath AS tmdbLogo, ci.variantKey, ci.lang, ci.quality, ci.epgChannelId, ci.categoryId
           FROM catalog_items ci
           LEFT JOIN metadata_match mm ON mm.itemId = ci.id AND mm.status = 'confirmed'
           LEFT JOIN tmdb_metadata tm ON tm.mediaType = mm.mediaType AND tm.tmdbId = mm.tmdbId
           WHERE ci.accountId = :accountId AND ci.type = :type AND ci.available = 1 AND (:categoryId IS NULL OR ci.categoryId = :categoryId)
           ORDER BY
             CASE WHEN :sort = 1 THEN COALESCE(tm.title, ci.title) END COLLATE NOCASE,
             CASE WHEN :sort = 2 THEN ci.year END DESC,
             CASE WHEN :categoryId IS NULL THEN ci.addedAt IS NULL END,
             CASE WHEN :categoryId IS NULL THEN ci.addedAt END DESC,
             ci.position IS NULL, ci.position, ci.title
           LIMIT :limit"""
    )
    fun cardsWindow(accountId: String, type: String, categoryId: String?, sort: Int, limit: Int): Flow<List<CatalogCard>>

    /** Nombre de contenus (versions regroupées) pour l'en-tête de la grille. */
    @Query(
        """SELECT COUNT(DISTINCT COALESCE(ci.variantKey, ci.id)) FROM catalog_items ci
           WHERE ci.accountId = :accountId AND ci.type = :type AND ci.available = 1 AND (:categoryId IS NULL OR ci.categoryId = :categoryId)"""
    )
    fun countCards(accountId: String, type: String, categoryId: String?): Flow<Int>

    @Query("SELECT * FROM catalog_items WHERE id = :id") fun observeItem(id: String): Flow<CatalogItemEntity?>

    // ---------- variantes ----------
    @Query("SELECT * FROM catalog_items WHERE accountId = :accountId AND type = :type AND variantKey = :variantKey AND available = 1")
    suspend fun variantSiblings(accountId: String, type: String, variantKey: String): List<CatalogItemEntity>

    @Query(
        """SELECT ci.id, ci.type, ci.rawTitle, ci.year, c.name AS categoryName FROM catalog_items ci
           LEFT JOIN categories c ON c.id = ci.categoryId WHERE ci.variantKey IS NULL LIMIT :limit"""
    )
    suspend fun itemsWithoutVariant(limit: Int): List<VariantBackfillRow>

    @Query("UPDATE catalog_items SET variantKey = :key, lang = :lang, quality = :quality WHERE id = :id")
    suspend fun setVariant(id: String, key: String, lang: String?, quality: Int)

    @Transaction
    suspend fun setVariants(rows: List<Pair<String, com.aditv.data.variants.VariantInfo>>) { rows.forEach { (id, v) -> setVariant(id, v.key, v.lang, v.quality) } }

    @Query(
        """SELECT ci.id, ci.type, COALESCE(tm.title, ci.title) AS title, ci.year, ci.imageUrl, ci.genre, tm.posterPath AS tmdbPoster, tm.backdropPath AS tmdbBackdrop, tm.logoPath AS tmdbLogo, ci.variantKey, ci.lang, ci.quality, ci.epgChannelId, ci.categoryId
           FROM catalog_items ci
           LEFT JOIN metadata_match mm ON mm.itemId = ci.id AND mm.status = 'confirmed'
           LEFT JOIN tmdb_metadata tm ON tm.mediaType = mm.mediaType AND tm.tmdbId = mm.tmdbId
           WHERE ci.accountId = :accountId AND ci.type = 'LIVE' AND ci.available = 1
           ORDER BY ci.position IS NULL, ci.position, ci.title"""
    )
    suspend fun allChannels(accountId: String): List<CatalogCard>

    @Query("SELECT name FROM categories WHERE id = :id") suspend fun categoryName(id: String): String?

    @Query("SELECT COUNT(*) FROM catalog_items WHERE accountId = :accountId AND type = :type AND available = 1")
    fun count(accountId: String, type: String): Flow<Int>
}

data class VariantBackfillRow(val id: String, val type: String, val rawTitle: String, val year: Int?, val categoryName: String?)
