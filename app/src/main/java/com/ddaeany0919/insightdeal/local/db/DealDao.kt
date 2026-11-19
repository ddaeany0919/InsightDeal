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
     * 카테고리(15종 표준 키 + 레거시 별칭 + 서버 제목/커뮤니티 폴백 동치 매칭), 검색 키워드, 커뮤니티 플랫폼 필터링 및 3종 정렬 지원 PagingSource 쿼리
     */
    @Query("""
        SELECT * FROM deals 
        WHERE (
            :category IS NULL
            OR :category = '전체'
            OR (:category = '핫딜모음' AND honeyScore >= 100)
            OR category = :category
            OR (:category = '음식' AND (
                category IN ('음식', '식품/건강', '식품', '먹거리', '간식', '음료', '음료수', '건강')
                OR COALESCE(category, '') LIKE '%음식%'
                OR COALESCE(category, '') LIKE '%식품%'
                OR COALESCE(category, '') LIKE '%먹거리%'
                OR COALESCE(category, '') LIKE '%간식%'
                OR COALESCE(category, '') LIKE '%음료%'
                OR COALESCE(category, '') LIKE '%건강%'
            ))
            OR (:category = 'SW/게임' AND (
                category IN ('SW/게임', 'SW', '게임', '소프트웨어', '콘솔')
                OR LOWER(COALESCE(category, '')) LIKE '%sw%'
                OR COALESCE(category, '') LIKE '%게임%'
                OR COALESCE(category, '') LIKE '%소프트웨어%'
                OR COALESCE(category, '') LIKE '%콘솔%'
            ))
            OR (:category = 'PC용품' AND (
                category IN ('PC용품', 'PC/하드웨어', 'PC', '하드웨어', '컴퓨터', '노트북', '데스크탑', '모니터')
                OR LOWER(COALESCE(category, '')) LIKE '%pc%'
                OR COALESCE(category, '') LIKE '%하드웨어%'
                OR COALESCE(category, '') LIKE '%노트북%'
                OR COALESCE(category, '') LIKE '%컴퓨터%'
                OR COALESCE(category, '') LIKE '%데스크탑%'
                OR COALESCE(category, '') LIKE '%모니터%'
            ))
            OR (:category = '가전제품' AND (
                category IN ('가전제품', '가전/TV', '가전', '가전/디지털', '디지털', 'TV')
                OR COALESCE(category, '') LIKE '%가전%'
                OR COALESCE(category, '') LIKE '%냉장고%'
                OR LOWER(COALESCE(category, '')) LIKE '%tv%'
                OR COALESCE(category, '') LIKE '%음향%'
                OR COALESCE(category, '') LIKE '%디지털%'
                OR title LIKE '%선풍기%'
                OR title LIKE '%에어컨%'
                OR title LIKE '%청소기%'
                OR title LIKE '%로봇청소기%'
                OR title LIKE '%냉장고%'
                OR title LIKE '%세탁기%'
                OR title LIKE '%건조기%'
                OR title LIKE '%공기청정기%'
                OR title LIKE '%제습기%'
                OR title LIKE '%가습기%'
                OR title LIKE '%전자레인지%'
                OR title LIKE '%오븐%'
                OR title LIKE '%에어프라이어%'
                OR title LIKE '%밥솥%'
                OR title LIKE '%식기세척기%'
                OR title LIKE '%블렌더%'
                OR title LIKE '%믹서기%'
                OR title LIKE '%다리미%'
                OR title LIKE '%안마%'
                OR title LIKE '%드라이기%'
                OR title LIKE '%면도기%'
                OR title LIKE '%전동칫솔%'
                OR title LIKE '%이어폰%'
                OR title LIKE '%헤드폰%'
                OR title LIKE '%스피커%'
                OR title LIKE '%사운드바%'
                OR title LIKE '%버즈%'
                OR title LIKE '%에어팟%'
                OR title LIKE '%빔프로젝터%'
                OR LOWER(title) LIKE '%tv%'
                OR title LIKE '%티비%'
            ))
            OR (:category = '생활용품' AND (
                category IN ('생활용품', '생활/식품', '생활', '생필품', '주방', '가구', '인테리어')
                OR COALESCE(category, '') LIKE '%생활%'
                OR COALESCE(category, '') LIKE '%생필품%'
                OR COALESCE(category, '') LIKE '%주방%'
                OR COALESCE(category, '') LIKE '%가구%'
                OR COALESCE(category, '') LIKE '%인테리어%'
            ))
            OR (:category = '패션' AND (
                category IN ('패션', '의류/패션', '의류', '신발', '잡화', '가방', '화장품')
                OR COALESCE(category, '') LIKE '%패션%'
                OR COALESCE(category, '') LIKE '%의류%'
                OR COALESCE(category, '') LIKE '%신발%'
                OR COALESCE(category, '') LIKE '%잡화%'
                OR COALESCE(category, '') LIKE '%가방%'
                OR COALESCE(category, '') LIKE '%화장품%'
            ))
            OR (:category = '뷰티' AND (
                category IN ('뷰티', '화장품', '미용')
                OR COALESCE(category, '') LIKE '%뷰티%'
                OR COALESCE(category, '') LIKE '%화장품%'
                OR COALESCE(category, '') LIKE '%미용%'
                OR title LIKE '%선크림%'
                OR title LIKE '%바디워시%'
                OR title LIKE '%샴푸%'
                OR title LIKE '%트리트먼트%'
                OR title LIKE '%클렌징%'
                OR title LIKE '%토너%'
                OR title LIKE '%세럼%'
                OR title LIKE '%로션%'
                OR title LIKE '%올리브영%'
                OR title LIKE '%이니스프리%'
                OR title LIKE '%닥터지%'
                OR title LIKE '%달바%'
            ))
            OR (:category = '모바일/기프티콘' AND (
                category IN ('모바일/기프티콘', '모바일', '스마트폰', '태블릿', '기프티콘', '쿠폰', 'e쿠폰', 'e-쿠폰')
                OR COALESCE(category, '') LIKE '%모바일%'
                OR COALESCE(category, '') LIKE '%스마트폰%'
                OR COALESCE(category, '') LIKE '%태블릿%'
                OR COALESCE(category, '') LIKE '%기프티콘%'
                OR COALESCE(category, '') LIKE '%쿠폰%'
            ))
            OR (:category = '상품권' AND (
                category IN ('상품권', '이용권')
                OR COALESCE(category, '') LIKE '%상품권%'
                OR COALESCE(category, '') LIKE '%이용권%'
                OR COALESCE(category, '') LIKE '%패키지%'
                OR COALESCE(category, '') LIKE '%기프티콘%'
                OR COALESCE(category, '') LIKE '%커피%'
                OR COALESCE(category, '') LIKE '%이커머스%'
            ))
            OR (:category = '적립' AND (
                category IN ('적립', '적립/포인트', '포인트')
                OR COALESCE(category, '') LIKE '%적립%'
                OR COALESCE(category, '') LIKE '%포인트%'
                OR COALESCE(category, '') LIKE '%사인업%'
                OR COALESCE(category, '') LIKE '%하이패스%'
                OR COALESCE(category, '') LIKE '%리퀘스트%'
                OR title LIKE '%네이버페이%'
                OR title LIKE '%적립%'
                OR title LIKE '%네이버포인트%'
                OR title LIKE '%페이포인트%'
                OR title LIKE '%라방%'
                OR title LIKE '%클릭%'
                OR title LIKE '%출석%'
            ))
            OR (:category = '이벤트' AND (
                category IN ('이벤트', '체험단')
                OR COALESCE(category, '') LIKE '%이벤트%'
                OR COALESCE(category, '') LIKE '%추석%'
                OR COALESCE(category, '') LIKE '%명절%'
                OR COALESCE(category, '') LIKE '%무료증정%'
                OR COALESCE(category, '') LIKE '%리뷰%'
                OR COALESCE(category, '') LIKE '%체험단%'
                OR COALESCE(category, '') LIKE '%선착순%'
                OR COALESCE(category, '') LIKE '%무료배포%'
                OR title LIKE '%이벤트%'
                OR title LIKE '%선착순%'
                OR title LIKE '%무료배포%'
                OR title LIKE '%일시무료%'
                OR title LIKE '%프로모션%'
                OR title LIKE '%체험단%'
                OR title LIKE '%응모%'
            ))
            OR (:category = '육아/유아용품' AND (
                category IN ('육아/유아용품', '육아', '유아용품', '유아')
                OR COALESCE(category, '') LIKE '%육아%'
                OR COALESCE(category, '') LIKE '%유아%'
                OR siteName LIKE '%빠삭육아%'
                OR LOWER(siteName) LIKE '%bbasak_parenting%'
                OR COALESCE(siteNames, '') LIKE '%빠삭육아%'
                OR title LIKE '%기저귀%'
                OR title LIKE '%분유%'
                OR title LIKE '%아기%'
                OR title LIKE '%유아%'
                OR title LIKE '%키즈%'
                OR title LIKE '%하기스%'
                OR title LIKE '%팸퍼스%'
                OR title LIKE '%젖병%'
                OR title LIKE '%카시트%'
                OR title LIKE '%유모차%'
                OR title LIKE '%베베%'
                OR title LIKE '%아동%'
            ))
            OR (:category = '해외직구' AND (
                category IN ('해외직구', '여행.해외핫딜', '해외핫딜', '여행/해외핫딜', '여행/해외', '직구', '해외')
                OR COALESCE(category, '') LIKE '%해외직구%'
                OR COALESCE(category, '') LIKE '%해외핫딜%'
                OR COALESCE(category, '') LIKE '%여행%'
                OR COALESCE(category, '') LIKE '%해외%'
                OR COALESCE(category, '') LIKE '%호텔%'
                OR COALESCE(category, '') LIKE '%프리미엄%'
                OR COALESCE(category, '') LIKE '%항공%'
                OR COALESCE(category, '') LIKE '%직구%'
            ))
        )
          AND (
              :keyword IS NULL
              OR TRIM(:keyword) = ''
              OR INSTR(TRIM(:keyword), ' ') > 0
              OR INSTR(:keyword, '-') > 0
              OR INSTR(:keyword, ':') > 0
              OR title LIKE '%' || TRIM(:keyword) || '%'
              OR COALESCE(shopName, '') LIKE '%' || TRIM(:keyword) || '%'
              OR COALESCE(category, '') LIKE '%' || TRIM(:keyword) || '%'
              OR COALESCE(siteName, '') LIKE '%' || TRIM(:keyword) || '%'
              OR COALESCE(ecommerceUrl, '') LIKE '%' || TRIM(:keyword) || '%'
              OR COALESCE(contentHtml, '') LIKE '%' || TRIM(:keyword) || '%'
              OR COALESCE(aiSummary, '') LIKE '%' || TRIM(:keyword) || '%'
          )
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
          CASE WHEN :sort = 'price_asc' THEN (
              CASE UPPER(TRIM(COALESCE(currency, 'KRW')))
                  WHEN 'USD' THEN price * 1400.0 / 100.0
                  WHEN 'EUR' THEN price * 1500.0 / 100.0
                  WHEN 'CNY' THEN price * 195.0 / 100.0
                  WHEN 'JPY' THEN price * 9.5
                  ELSE price * 1.0
              END
          ) END ASC,
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
