package com.ddaeany0919.insightdeal.local.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * 💾 핫딜 로컬 캐싱 쿼리를 처리하는 DAO
 */
@Dao
interface DealDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(deals: List<DealEntity>)

    /**
     * 카테고리, 검색 키워드, 제휴 플랫폼 필터링을 로컬 수준에서 유연하게 지원하는 PagingSource 반환 쿼리
     * - category가 "전체"이거나 null인 경우 무시 처리
     * - platform이 "전체"이거나 null인 경우 무시 처리
     */
    @Query("""
        SELECT * FROM deals 
        WHERE (:category IS NULL OR :category = '전체' OR :category = '핫딜모음' OR category = :category)
          AND (:keyword IS NULL OR :keyword = '' OR title LIKE '%' || :keyword || '%')
          AND (:platform IS NULL OR :platform = '전체' OR :platform = '' OR INSTR(',' || :platform || ',', ',' || siteName || ',') > 0)
        ORDER BY createdAt DESC, id DESC
    """)
    fun getPagedDeals(
        category: String?,
        keyword: String?,
        platform: String?
    ): PagingSource<Int, DealEntity>

    @Query("DELETE FROM deals")
    suspend fun clearAllDeals()

    @Query("SELECT COUNT(*) FROM deals")
    suspend fun getDealCount(): Int
}
