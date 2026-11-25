package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Ingredient::class,
        Recipe::class,
        RecipeIngredientCrossRef::class,
        PantryItemEntity::class,
        SavedRecipeEntity::class,
        RestockItemEntity::class
    ],
    version = 6,
    exportSchema = false
)
@TypeConverters(PantryTypeConverters::class)
abstract class PantryDatabase : RoomDatabase() {
    abstract fun pantryDao(): PantryDao
    abstract fun ingredientDao(): IngredientDao
    abstract fun recipeDao(): RecipeDao

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

        /**
         * Non-destructive migration from v5 to v6:
         * Creates the canonical `recipes` table and `recipe_ingredient_cross_ref` junction
         * table with foreign keys and SQLite indexes for offline recipe & ingredient storage.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recipes` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`externalId` TEXT NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`description` TEXT NOT NULL, " +
                        "`category` TEXT NOT NULL, " +
                        "`prepTimeMinutes` INTEGER NOT NULL, " +
                        "`servings` INTEGER NOT NULL, " +
                        "`caloriesPerServing` INTEGER NOT NULL, " +
                        "`estimatedCostPerServing` REAL NOT NULL, " +
                        "`rescueScore` INTEGER NOT NULL, " +
                        "`applianceUsed` TEXT NOT NULL, " +
                        "`ingredientsList` TEXT NOT NULL, " +
                        "`spicesList` TEXT NOT NULL, " +
                        "`instructions` TEXT NOT NULL, " +
                        "`chefTip` TEXT NOT NULL, " +
                        "`isFavorite` INTEGER NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL" +
                        ")"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_recipes_title` ON `recipes` (`title`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_recipes_category` ON `recipes` (`category`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_recipes_isFavorite_createdAt` ON `recipes` (`isFavorite`, `createdAt`)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recipe_ingredient_cross_ref` (" +
                        "`recipeId` INTEGER NOT NULL, " +
                        "`ingredientId` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`recipeId`, `ingredientId`), " +
                        "FOREIGN KEY(`recipeId`) REFERENCES `recipes`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE, " +
                        "FOREIGN KEY(`ingredientId`) REFERENCES `ingredients`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE" +
                        ")"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_recipe_ingredient_cross_ref_recipeId` ON `recipe_ingredient_cross_ref` (`recipeId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_recipe_ingredient_cross_ref_ingredientId` ON `recipe_ingredient_cross_ref` (`ingredientId`)"
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
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
