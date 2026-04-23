package com.ddaeany0919.insightdeal.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "wishlist")
data class WishlistEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val keyword: String,
    val productUrl: String,
    val targetPrice: Int,
    val currentLowestPrice: Int? = null,
    val currentLowestPlatform: String? = null,
    val currentLowestProductTitle: String? = null,
    val priceDropPercentage: Double = 0.0,
    val isTargetReached: Boolean = false,
    val isActive: Boolean = true,
    val alertEnabled: Boolean = true,
    val createdAt: LocalDateTime = LocalDateTime.now(),
    val updatedAt: LocalDateTime = LocalDateTime.now(),
    val lastChecked: LocalDateTime? = null
)
