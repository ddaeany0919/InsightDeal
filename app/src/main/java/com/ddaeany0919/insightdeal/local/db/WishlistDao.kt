package com.ddaeany0919.insightdeal.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Delete
import kotlinx.coroutines.flow.Flow

@Dao
interface WishlistDao {
    @Query("SELECT * FROM wishlist ORDER BY createdAt DESC")
    fun getAllWishlistsFlow(): Flow<List<WishlistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWishlist(wishlist: WishlistEntity): Long

    @Update
    suspend fun updateWishlist(wishlist: WishlistEntity)

    @Delete
    suspend fun deleteWishlist(wishlist: WishlistEntity)
    
    @Query("DELETE FROM wishlist WHERE id = :id")
    suspend fun deleteWishlistById(id: Int)
}
