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
        FavoriteChannelEntity::class, EpgSourceEntity::class, EpgChannelEntity::class, EpgMappingEntity::class, ProgrammeEntity::class,
    ],
    version = 4,
    exportSchema = true,
    // v2 : nouvelles tables (épisodes, progression, ma liste, TMDB) + colonne accounts.liveFormats. Aucune donnée perdue.
    // v3 : variantes (colonnes nullable, recalculées au démarrage), favoris et guide TV.
    // v4 : diagnostic d'import (catégories, mode) dans sync_state.
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4)],
)
abstract class AditvDatabase : RoomDatabase() {
    abstract fun accounts(): AccountDao
    abstract fun catalog(): CatalogDao
    abstract fun library(): LibraryDao
    abstract fun metadata(): MetadataDao
    abstract fun live(): LiveDao

    companion object {
        fun build(context: Context): AditvDatabase =
            Room.databaseBuilder(context, AditvDatabase::class.java, "aditv.db")
                // Aucune migration destructive : chaque changement de schéma aura sa migration testée.
                .build()
    }
}
