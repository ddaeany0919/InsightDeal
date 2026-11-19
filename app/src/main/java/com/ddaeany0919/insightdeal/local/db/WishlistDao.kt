package com.ddaeany0919.insightdeal.local.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface WishlistDao {
    @Query("SELECT * FROM wishlist ORDER BY createdAt DESC")
    fun getAllWishlistsFlow(): Flow<List<WishlistEntity>>

    @Query("SELECT * FROM wishlist ORDER BY createdAt DESC")
    fun getAllWishlistsPaged(): PagingSource<Int, WishlistEntity>

    @Query("SELECT * FROM wishlist WHERE id = :id LIMIT 1")
    suspend fun getWishlistById(id: Int): WishlistEntity?

    @Query("SELECT * FROM wishlist")
    suspend fun getAllWishlistsOnce(): List<WishlistEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWishlist(wishlist: WishlistEntity): Long

    @Update
    suspend fun updateWishlist(wishlist: WishlistEntity)

    @Query("UPDATE wishlist SET serverWishlistId = :serverId WHERE id = :localId")
    suspend fun updateServerWishlistId(localId: Int, serverId: Int)

    @Delete
    suspend fun deleteWishlist(wishlist: WishlistEntity)

    @Query("DELETE FROM wishlist WHERE id = :id")
    suspend fun deleteWishlistById(id: Int)
}
