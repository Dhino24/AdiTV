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
interface LiveDao {
    // ---------- favoris ----------
    @Upsert suspend fun addFavorite(f: FavoriteChannelEntity)
    @Query("DELETE FROM favorite_channels WHERE accountId = :accountId AND itemId = :itemId") suspend fun removeFavorite(accountId: String, itemId: String)
    @Query("SELECT itemId FROM favorite_channels WHERE accountId = :accountId") fun favoriteIds(accountId: String): Flow<List<String>>

    @Query(
        """SELECT ci.id, ci.type, ci.title, ci.year, ci.imageUrl, ci.genre, NULL AS tmdbPoster, ci.variantKey, ci.lang, ci.quality, ci.epgChannelId, ci.categoryId
           FROM favorite_channels f JOIN catalog_items ci ON ci.id = f.itemId
           WHERE f.accountId = :accountId AND ci.available = 1 ORDER BY f.addedAt"""
    )
    fun favorites(accountId: String): Flow<List<CatalogCard>>

    @Query("DELETE FROM favorite_channels WHERE accountId = :accountId") suspend fun deleteFavorites(accountId: String)

    // ---------- sources ----------
    @Insert suspend fun insertSource(s: EpgSourceEntity): Long
    @Update suspend fun updateSource(s: EpgSourceEntity)
    @Query("DELETE FROM epg_sources WHERE id = :id") suspend fun deleteSource(id: Long)
    @Query("SELECT * FROM epg_sources WHERE accountId = :accountId ORDER BY priority, id") suspend fun sources(accountId: String): List<EpgSourceEntity>
    @Query("SELECT * FROM epg_sources WHERE accountId = :accountId ORDER BY priority, id") fun observeSources(accountId: String): Flow<List<EpgSourceEntity>>

    // ---------- chaînes de guide ----------
    @Upsert suspend fun upsertEpgChannels(c: List<EpgChannelEntity>)
    @Query("DELETE FROM epg_channels WHERE sourceId = :sourceId") suspend fun deleteEpgChannels(sourceId: Long)
    @Query("SELECT * FROM epg_channels WHERE accountId = :accountId AND (searchKey LIKE '%' || :key || '%' OR channelId LIKE '%' || :key || '%') ORDER BY displayName LIMIT 60")
    suspend fun searchEpgChannels(accountId: String, key: String): List<EpgChannelEntity>

    // ---------- association ----------
    @Upsert suspend fun upsertMapping(m: EpgMappingEntity)
    @Query("SELECT * FROM epg_mapping WHERE itemId = :itemId") suspend fun mapping(itemId: String): EpgMappingEntity?
    @Query("SELECT * FROM epg_mapping WHERE itemId = :itemId") fun observeMapping(itemId: String): Flow<EpgMappingEntity?>
    @Query("SELECT * FROM epg_mapping WHERE accountId = :accountId") suspend fun mappings(accountId: String): List<EpgMappingEntity>
    @Query("DELETE FROM epg_mapping WHERE itemId = :itemId") suspend fun deleteMapping(itemId: String)
    @Query("DELETE FROM epg_mapping WHERE accountId = :accountId") suspend fun deleteMappings(accountId: String)

    /** Identifiants de guide effectivement utilisés par le catalogue (fournisseur ou association manuelle). */
    @Query(
        """SELECT DISTINCT COALESCE(m.epgChannelId, ci.epgChannelId) FROM catalog_items ci
           LEFT JOIN epg_mapping m ON m.itemId = ci.id
           WHERE ci.accountId = :accountId AND ci.type = 'LIVE' AND ci.available = 1
             AND COALESCE(m.disabled, 0) = 0 AND COALESCE(m.epgChannelId, ci.epgChannelId) IS NOT NULL"""
    )
    suspend fun usedEpgIds(accountId: String): List<String>

    // ---------- programmes ----------
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertProgrammes(p: List<ProgrammeEntity>)

    @Query("DELETE FROM programmes WHERE sourceId = :sourceId AND importStamp <> :stamp")
    suspend fun deleteStaleProgrammes(sourceId: Long, stamp: Long): Int

    @Query("DELETE FROM programmes WHERE endUtc < :before") suspend fun deleteOldProgrammes(before: Long): Int
    @Query("DELETE FROM programmes WHERE sourceId = :sourceId") suspend fun deleteSourceProgrammes(sourceId: Long)
    @Query("DELETE FROM programmes WHERE accountId = :accountId") suspend fun deleteProgrammes(accountId: String)
    @Query("SELECT COUNT(*) FROM programmes WHERE sourceId = :sourceId") suspend fun countProgrammes(sourceId: Long): Int

    @Query(
        """SELECT * FROM programmes WHERE accountId = :accountId AND channelId IN (:channelIds)
           AND endUtc > :from AND startUtc < :to ORDER BY channelId, startUtc"""
    )
    suspend fun programmesIn(accountId: String, channelIds: List<String>, from: Long, to: Long): List<ProgrammeEntity>

    /** IDs de guide ayant au moins un programme à venir ou en cours (couverture). */
    @Query("SELECT DISTINCT channelId FROM programmes WHERE accountId = :accountId AND endUtc > :now")
    suspend fun coveredChannelIds(accountId: String, now: Long): List<String>

    @Transaction
    suspend fun clearAccount(accountId: String) {
        deleteFavorites(accountId); deleteProgrammes(accountId); deleteMappings(accountId)
        sources(accountId).forEach { deleteEpgChannels(it.id); deleteSource(it.id) }
    }
}
