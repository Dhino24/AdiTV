package com.aditv.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [AccountEntity::class, CategoryEntity::class, CatalogItemEntity::class, SyncStateEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class AditvDatabase : RoomDatabase() {
    abstract fun accounts(): AccountDao
    abstract fun catalog(): CatalogDao

    companion object {
        fun build(context: Context): AditvDatabase =
            Room.databaseBuilder(context, AditvDatabase::class.java, "aditv.db")
                // Aucune migration destructive : chaque changement de schéma aura sa migration testée.
                .build()
    }
}
