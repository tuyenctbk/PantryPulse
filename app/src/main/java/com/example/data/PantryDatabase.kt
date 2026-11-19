package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Ingredient::class,
        PantryItemEntity::class,
        SavedRecipeEntity::class,
        RestockItemEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class PantryDatabase : RoomDatabase() {
    abstract fun pantryDao(): PantryDao
    abstract fun ingredientDao(): IngredientDao

    companion object {
        @Volatile
        private var INSTANCE: PantryDatabase? = null

        /**
         * Non-destructive migration from v3 to v4:
         * Adds explicit calendar expiryDateEpochMillis and updatedTimestamp columns,
         * backfills existing rows from addedTimestamp + daysUntilExpiry, and creates
         * SQLite indexes for fast offline sorting, low-stock queries, and search.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE pantry_items ADD COLUMN expiryDateEpochMillis INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE pantry_items ADD COLUMN updatedTimestamp INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "UPDATE pantry_items SET expiryDateEpochMillis = addedTimestamp + (daysUntilExpiry * 86400000), updatedTimestamp = addedTimestamp WHERE expiryDateEpochMillis = 0"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_pantry_items_isConsumed_expiryDateEpochMillis ON pantry_items (isConsumed, expiryDateEpochMillis)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_pantry_items_name ON pantry_items (name)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_pantry_items_stockPercent ON pantry_items (stockPercent)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_restock_items_isChecked_addedTimestamp ON restock_items (isChecked, addedTimestamp)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_restock_items_sourcePantryItemId ON restock_items (sourcePantryItemId)"
                )
            }
        }

        /**
         * Non-destructive migration from v4 to v5:
         * Creates the canonical `ingredients` table for the [Ingredient] Room entity
         * with id, name, quantity, unit, and expiryDate columns plus SQLite indexes.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `ingredients` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`quantity` REAL NOT NULL, " +
                        "`unit` TEXT NOT NULL, " +
                        "`expiryDate` INTEGER NOT NULL" +
                        ")"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_ingredients_expiryDate` ON `ingredients` (`expiryDate`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_ingredients_name` ON `ingredients` (`name`)"
                )
            }
        }

        fun getInstance(context: Context): PantryDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PantryDatabase::class.java,
                    "pantry_pulse_offline.db"
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
