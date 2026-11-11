package com.ddaeany0919.insightdeal.local.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * 핫딜 로컬 캐싱 쿼리를 처리하는 DAO
 */
@Dao
interface DealDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(deals: List<DealEntity>)

    /**
     * 카테고리, 검색 키워드, 커뮤니티 플랫폼 필터링 및 3종 정렬(latest / popular / price_asc) 지원 PagingSource 쿼리
     */
    @Query("""
        SELECT * FROM deals 
        WHERE (:category IS NULL OR :category = '전체' OR :category = '핫딜모음' OR category = :category)
          AND (:keyword IS NULL OR :keyword = '' OR title LIKE '%' || :keyword || '%')
          AND (:platform IS NULL OR :platform = '전체' OR :platform = '' OR (
              (INSTR(',' || :platform || ',', ',뽐뿌,') > 0 AND INSTR(REPLACE(REPLACE(siteName, '알리뽐뿌', ''), '뽐뿌해외', ''), '뽐뿌') > 0) OR
              (INSTR(',' || :platform || ',', ',퀘이사존,') > 0 AND INSTR(siteName, '퀘이사존') > 0) OR
              (INSTR(',' || :platform || ',', ',펨코,') > 0 AND INSTR(siteName, '펨코') > 0) OR
              (INSTR(',' || :platform || ',', ',루리웹,') > 0 AND INSTR(siteName, '루리웹') > 0) OR
              (INSTR(',' || :platform || ',', ',클리앙,') > 0 AND INSTR(siteName, '클리앙') > 0) OR
              (INSTR(',' || :platform || ',', ',알리뽐뿌,') > 0 AND INSTR(siteName, '알리뽐뿌') > 0) OR
              (INSTR(',' || :platform || ',', ',뽐뿌해외,') > 0 AND INSTR(siteName, '뽐뿌해외') > 0) OR
              (INSTR(',' || :platform || ',', ',빠삭,') > 0 AND INSTR(siteName, '빠삭') > 0) OR
              (INSTR(',' || :platform || ',', ',빠삭국내,') > 0 AND INSTR(siteName, '빠삭국내') > 0) OR
              (INSTR(',' || :platform || ',', ',빠삭해외,') > 0 AND INSTR(siteName, '빠삭해외') > 0) OR
              (INSTR(',' || :platform || ',', ',빠삭육아,') > 0 AND INSTR(siteName, '빠삭육아') > 0)
          ))
        ORDER BY
          CASE WHEN :sort = 'popular' THEN honeyScore END DESC,
          CASE WHEN :sort = 'popular' THEN likeCount END DESC,
          CASE WHEN :sort = 'popular' THEN commentCount END DESC,
          CASE WHEN :sort = 'price_asc' THEN (CASE WHEN price > 0 THEN 0 ELSE 1 END) END ASC,
          CASE WHEN :sort = 'price_asc' THEN price END ASC,
          createdAt DESC,
          id DESC
    """)
    fun getPagedDeals(
        category: String?,
        keyword: String?,
        platform: String?,
        sort: String = "latest"
    ): PagingSource<Int, DealEntity>

    @Query("DELETE FROM deals")
    suspend fun clearAllDeals()

    @Query("SELECT COUNT(*) FROM deals")
    suspend fun getDealCount(): Int
}
