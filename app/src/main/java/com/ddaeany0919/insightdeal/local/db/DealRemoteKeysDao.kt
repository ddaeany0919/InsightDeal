package com.ddaeany0919.insightdeal.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * 🔑 RemoteMediator 오프셋 키 쿼리를 처리하는 DAO
 */
@Dao
interface DealRemoteKeysDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(remoteKey: List<DealRemoteKeysEntity>)

    @Query("SELECT * FROM deal_remote_keys WHERE dealId = :dealId")
    suspend fun getRemoteKeysForDealId(dealId: Int): DealRemoteKeysEntity?

    @Query("DELETE FROM deal_remote_keys")
    suspend fun clearRemoteKeys()
}
