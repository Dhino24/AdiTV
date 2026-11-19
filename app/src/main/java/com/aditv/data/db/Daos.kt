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
        """SELECT id, type, title, year, imageUrl, genre FROM catalog_items
           WHERE accountId = :accountId AND type = :type AND categoryId = :categoryId AND available = 1
           ORDER BY position IS NULL, position, title"""
    )
    fun cardsInCategory(accountId: String, type: String, categoryId: String): Flow<List<CatalogCard>>

    @Query(
        """SELECT id, type, title, year, imageUrl, genre FROM catalog_items
           WHERE accountId = :accountId AND type = :type AND available = 1 AND addedAt IS NOT NULL
           ORDER BY addedAt DESC LIMIT :limit"""
    )
    fun recentlyAdded(accountId: String, type: String, limit: Int): Flow<List<CatalogCard>>

    @Query(
        """SELECT id, type, title, year, imageUrl, genre FROM catalog_items
           WHERE accountId = :accountId AND type = :type AND available = 1
           ORDER BY position IS NULL, position, title LIMIT :limit"""
    )
    fun firstItems(accountId: String, type: String, limit: Int): Flow<List<CatalogCard>>

    @Query(
        """SELECT id, type, title, year, imageUrl, genre FROM catalog_items
           WHERE accountId = :accountId AND available = 1 AND searchKey LIKE '%' || :key || '%'
           ORDER BY (searchKey LIKE :key || '%') DESC, type, title LIMIT :limit"""
    )
    suspend fun search(accountId: String, key: String, limit: Int): List<CatalogCard>

    @Query("SELECT * FROM catalog_items WHERE id = :id") fun observeItem(id: String): Flow<CatalogItemEntity?>

    @Query("SELECT name FROM categories WHERE id = :id") suspend fun categoryName(id: String): String?

    @Query("SELECT COUNT(*) FROM catalog_items WHERE accountId = :accountId AND type = :type AND available = 1")
    fun count(accountId: String, type: String): Flow<Int>
}
