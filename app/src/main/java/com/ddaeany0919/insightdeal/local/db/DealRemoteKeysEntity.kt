package com.ddaeany0919.insightdeal.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 🔑 RemoteMediator에서 다음 페이지 및 이전 페이지 호출 오프셋을 관리하기 위한 Key Entity
 */
@Entity(tableName = "deal_remote_keys")
data class DealRemoteKeysEntity(
    @PrimaryKey val dealId: Int,
    val prevKey: Int?,
    val nextKey: Int?
)
