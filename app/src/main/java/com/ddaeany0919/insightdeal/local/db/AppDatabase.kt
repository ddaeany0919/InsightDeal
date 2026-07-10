package com.ddaeany0919.insightdeal.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        WishlistEntity::class, 
        DealEntity::class, 
        DealRemoteKeysEntity::class,
        KeywordEntity::class
    ], 
    version = 4, 
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

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "insightdeal_db"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
