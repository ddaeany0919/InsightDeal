package com.ddaeany0919.insightdeal.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Entity(tableName = "keywords")
data class KeywordEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val keyword: String,
    val isActive: Boolean = true,
    val createdAt: LocalDateTime = LocalDateTime.now()
)
