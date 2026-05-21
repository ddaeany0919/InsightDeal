package com.ddaeany0919.insightdeal.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface KeywordDao {
    @Query("SELECT * FROM keywords ORDER BY createdAt DESC")
    fun getAllKeywordsFlow(): Flow<List<KeywordEntity>>

    @Query("SELECT * FROM keywords ORDER BY createdAt DESC")
    suspend fun getAllKeywords(): List<KeywordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKeyword(keyword: KeywordEntity)

    @Delete
    suspend fun deleteKeyword(keyword: KeywordEntity)

    @Query("DELETE FROM keywords WHERE id = :id")
    suspend fun deleteKeywordById(id: Int)

    @Query("SELECT EXISTS(SELECT 1 FROM keywords WHERE keyword = :keyword LIMIT 1)")
    suspend fun isKeywordExists(keyword: String): Boolean

    @Query("UPDATE keywords SET isActive = :isActive WHERE id = :id")
    suspend fun updateKeywordActive(id: Int, isActive: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertKeywords(keywords: List<KeywordEntity>)
}
