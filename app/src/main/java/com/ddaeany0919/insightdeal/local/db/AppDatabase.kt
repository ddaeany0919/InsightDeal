package com.ddaeany0919.insightdeal.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        WishlistEntity::class,
        DealEntity::class,
        DealRemoteKeysEntity::class,
        KeywordEntity::class
    ],
    version = 8,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun wishlistDao(): WishlistDao
    abstract fun dealDao(): DealDao
    abstract fun dealRemoteKeysDao(): DealRemoteKeysDao
    abstract fun keywordDao(): KeywordDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE wishlist ADD COLUMN serverWishlistId INTEGER DEFAULT NULL")
            }
        }

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE deals ADD COLUMN bodyImages TEXT NOT NULL DEFAULT '[]'")
                db.execSQL("ALTER TABLE deals ADD COLUMN isSearchFallback INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE deals ADD COLUMN isFree INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE deals ADD COLUMN reportCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE deal_remote_keys ADD COLUMN nextCursorIndexedAt TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE deal_remote_keys ADD COLUMN nextCursorId INTEGER DEFAULT NULL")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "insightdeal_db"
                )
                    .addMigrations(MIGRATION_6_7, MIGRATION_7_8)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
