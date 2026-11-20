package com.aditv.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** Équipe ou compétition suivie (v5). Les alias servent à reconnaître ses matchs dans le guide. */
@Entity(tableName = "follows", primaryKeys = ["accountId", "key"])
data class FollowEntity(
    val accountId: String,
    val key: String,
    val name: String,
    /** Alias normalisés, un par ligne. */
    val aliases: String,
    /** Alias à exclure (« espanyol » pour Barcelone…), un par ligne. */
    val excludes: String,
    val kind: String,
    val createdAt: Long,
)

/**
 * Rappel d'un programme du guide. Un même match diffusé sur plusieurs chaînes ne donne qu'un rappel
 * (clé titre + début), rattaché à la meilleure chaîne (favorite, française, meilleure version).
 */
@Entity(
    tableName = "reminders",
    indices = [Index(value = ["accountId", "matchKey"], unique = true), Index("notifyAt")],
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val accountId: String,
    val matchKey: String,
    val channelItemId: String,
    val channelName: String,
    val title: String,
    val startUtc: Long,
    val endUtc: Long,
    val notifyAt: Long,
    /** Créé automatiquement pour une équipe suivie (sinon : « Me rappeler » choisi dans le guide). */
    val auto: Boolean,
    val followKey: String?,
    /** Rappel automatique refusé par l'utilisateur : ne pas le recréer. */
    val dismissed: Boolean = false,
    val firedAt: Long? = null,
    val createdAt: Long,
)

/** Tendances TMDB de la semaine (films / séries), rafraîchies au plus toutes les 12 h. */
@Entity(tableName = "trending", primaryKeys = ["mediaType", "rank"])
data class TrendingEntity(
    val mediaType: String,
    val rank: Int,
    val tmdbId: Int,
    val title: String,
    val originalTitle: String?,
    val year: Int?,
    val posterPath: String?,
    val backdropPath: String?,
    val fetchedAt: Long,
)

@Dao
interface SportDao {
    @Upsert suspend fun upsertFollow(f: FollowEntity)
    @Query("DELETE FROM follows WHERE accountId = :accountId AND `key` = :key") suspend fun deleteFollow(accountId: String, key: String)
    @Query("SELECT * FROM follows WHERE accountId = :accountId ORDER BY createdAt") fun observeFollows(accountId: String): Flow<List<FollowEntity>>
    @Query("SELECT * FROM follows WHERE accountId = :accountId ORDER BY createdAt") suspend fun follows(accountId: String): List<FollowEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertReminder(r: ReminderEntity): Long
    @Upsert suspend fun upsertReminder(r: ReminderEntity)
    @Query("SELECT * FROM reminders WHERE accountId = :accountId AND matchKey = :key") suspend fun reminderByKey(accountId: String, key: String): ReminderEntity?
    @Query("SELECT * FROM reminders WHERE id = :id") suspend fun reminder(id: Long): ReminderEntity?
    @Query("SELECT * FROM reminders WHERE accountId = :accountId AND dismissed = 0 AND endUtc > :now ORDER BY startUtc")
    fun observeUpcoming(accountId: String, now: Long): Flow<List<ReminderEntity>>
    @Query("SELECT * FROM reminders WHERE dismissed = 0 AND firedAt IS NULL AND notifyAt > :now ORDER BY notifyAt")
    suspend fun pending(now: Long): List<ReminderEntity>
    @Query("SELECT * FROM reminders WHERE accountId = :accountId AND auto = 1") suspend fun autoReminders(accountId: String): List<ReminderEntity>
    @Query("UPDATE reminders SET firedAt = :at WHERE id = :id") suspend fun markFired(id: Long, at: Long)
    @Query("UPDATE reminders SET dismissed = 1 WHERE id = :id") suspend fun dismiss(id: Long)
    @Query("DELETE FROM reminders WHERE id = :id") suspend fun deleteReminder(id: Long)
    /** Nettoyage : rappels terminés depuis plus d'un jour. */
    @Query("DELETE FROM reminders WHERE endUtc < :before") suspend fun purge(before: Long): Int

    @Query("DELETE FROM trending WHERE mediaType = :mediaType") suspend fun clearTrending(mediaType: String)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertTrending(t: List<TrendingEntity>)
    @Query("SELECT * FROM trending WHERE mediaType = :mediaType ORDER BY rank") fun observeTrending(mediaType: String): Flow<List<TrendingEntity>>
    @Query("SELECT MIN(fetchedAt) FROM trending WHERE mediaType = :mediaType") suspend fun trendingFetchedAt(mediaType: String): Long?

    /** Programmes à venir dont le titre contient un des motifs (LIKE, insensible à la casse ASCII). */
    @Query(
        """SELECT * FROM programmes WHERE accountId = :accountId AND endUtc > :from AND startUtc < :to
           AND (title LIKE '%' || :p1 || '%' OR title LIKE '%' || :p2 || '%' OR title LIKE '%' || :p3 || '%' OR title LIKE '%' || :p4 || '%')
           ORDER BY startUtc LIMIT 2000"""
    )
    suspend fun programmesMatching(accountId: String, from: Long, to: Long, p1: String, p2: String, p3: String, p4: String): List<ProgrammeEntity>
}
