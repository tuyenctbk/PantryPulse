package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PantryItemEntity::class,
        SavedRecipeEntity::class,
        RestockItemEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class PantryDatabase : RoomDatabase() {
    abstract fun pantryDao(): PantryDao

    companion object {
        @Volatile
        private var INSTANCE: PantryDatabase? = null

        fun getInstance(context: Context): PantryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PantryDatabase::class.java,
                    "pantry_pulse_offline.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
