package com.aditv.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AccountEntity::class, CategoryEntity::class, CatalogItemEntity::class, SyncStateEntity::class,
        EpisodeEntity::class, WatchProgressEntity::class, WatchlistEntity::class, MetadataMatchEntity::class, TmdbMetadataEntity::class,
    ],
    version = 2,
    exportSchema = true,
    // v2 : nouvelles tables (épisodes, progression, ma liste, TMDB) + colonne accounts.liveFormats. Aucune donnée perdue.
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class AditvDatabase : RoomDatabase() {
    abstract fun accounts(): AccountDao
    abstract fun catalog(): CatalogDao
    abstract fun library(): LibraryDao
    abstract fun metadata(): MetadataDao

    companion object {
        fun build(context: Context): AditvDatabase =
            Room.databaseBuilder(context, AditvDatabase::class.java, "aditv.db")
                // Aucune migration destructive : chaque changement de schéma aura sa migration testée.
                .build()
    }
}
