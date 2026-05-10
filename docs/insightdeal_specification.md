# 📱 InsightDeal Product Specification (초정밀 기능 사양서)

본 문서는 InsightDeal 안드로이드 애플리케이션의 탭별 상세 UI 구성, 데이터 흐름, 컴포넌트 아키텍처, 백엔드 API 명세 및 세부 비즈니스 로직을 코드 레벨 수준으로 초정밀하게 정의한 PRD(Product Requirements Document)입니다.

---

## 1. 🏠 홈 (Home) - `HomeScreen.kt` / `HomeViewModel.kt`
실시간으로 업데이트되는 전체 핫딜 피드를 무한 스크롤(Paging)로 제공하는 메인 화면.

### 1.1 주요 UI 및 상태 관리
*   **상태 관리 (StateFlow)**: `HomeViewModel` 내 `dealsPagingData` (`Flow<PagingData<HotDealDto>>`)를 통해 데이터 스트림을 관리. 화면 회전(Configuration Change) 시에도 `cachedIn(viewModelScope)`를 통해 스크롤 위치 보존.
*   **시간 포매팅 초정밀 최적화 (`StringUtils.kt`)**: 
    *   `GlobalTimeTicker`: 아이템마다 타이머를 생성하여 메모리가 누수되는 것을 방지하기 위해, 싱글톤 코루틴에서 현재 시간 기준 다음 정각(0초)까지의 지연 시간(`millisUntilNextMinute`)을 계산하여 정확하게 1분 단위로 `tick`(`System.currentTimeMillis()`)을 발생시킴.
    *   `rememberRelativeTime`: 이 전역 틱(상태)을 구독하여 "방금 전", "X분 전", "X시간 전" 등의 텍스트를 실제 시간 흐름에 맞춰 오차 없이 리컴포지션함.
*   **플랫폼 명칭 정규화**: `getPlatformDisplayName()`을 통해 `gmarket` -> `G마켓`, `ppomppu` -> `뽐뿌` 등 8가지 이상의 영문 플랫폼 ID를 한글명으로 맵핑하여 UI에 표시.
*   **감성적 핫딜 추천 뱃지**: `honeyScore` 기반의 모호한 통계 라벨링(상위 N%)을 대신해, "🔥 포텐/인기 초특가(100점)", "🍯 커뮤니티 강력 추천 핫딜" 등 직관적이고 감성적인 문구와 동적 컬러링을 결합하여 클릭 유도.

### 1.2 딜 상세 화면 (`DealDetailScreen.kt`)
홈이나 다른 탭에서 핫딜 카드를 클릭했을 때 진입하는 상세 뷰.
*   **프리미엄 AI 구매 타점 분석 (`AIBuyerGuide`)**: 가격 히스토리 데이터(`PriceHistoryPoint`)를 기반으로 리포트 형태 UI 렌더링. 역대 최저가 갱신 시 녹색/붉은색 강조, `Insights` 아이콘 적용, 고점 대비 할인 금액 표기 등 시각적 효과를 극대화해 강력한 매수 타이밍 안내.
*   **가격 변동 차트 (`PriceHistoryInteractiveCard`)**: 히스토리 데이터가 3개 이상일 때 활성화. `PriceChart` 커스텀 컴포넌트를 호출하여 200dp 높이의 라인 차트를 그림. 터치 시 상세 금액 노출.
*   **최저가 비교 테이블 (`MallPriceTable`)**: 여러 웹 판매처의 최저가를 KRW, USD, EUR 등의 통화(`formatPrice`)에 맞춰 변환하여 리스트업하고 '바로가기' 버튼 제공.
*   **목표가 알림 (`PriceAlertRegistrationButton`)**: 현재 가격 기준 -5%(`currentPrice * 0.95`)를 기본값으로, 사용자가 원하는 목표 가격 도달 시 푸시를 받도록 다이얼로그 제공.
*   **UI 컬러링**: 출처별 테마 컬러 강제 적용 (예: 뽐뿌=파란색 `#1565C0`, 퀘이사존=주황색 `#E65100`, 루리웹=남색 `#0D47A1`).

---

## 2. 🔍 검색 (Search) - `AdvancedSearchScreen.kt`
*   **한글 IME 최적화**: 텍스트필드 상태를 `String`이 아닌 `TextFieldValue`로 유지하여 자음/모음 조합 시(`우` -> `우유`) 깜빡임이나 백스페이스 씹힘 현상 방지.
*   **Debounce 로직**: `snapshotFlow`를 생성 후 `debounce(300)` 적용. 사용자가 입력을 멈추고 0.3초가 지나면 검색 쿼리 실행.
*   **검색 API 연동**: 
    *   Endpoint: `GET /deals/search`
    *   Params: `q`(검색어), `page`, `limit`, `min_price`, `max_price`, `category`, `site`, `sort`(기본값: relevance).

---

## 3. 🤖 AI 개인화 추천 탭 (Recommendation) - `RecommendationViewModel.kt`
InsightDeal의 핵심 지능형 추천 엔진.

### 3.1 추천 알고리즘 종류 (`PersonalizationEngine`)
*   **협업 필터링 (Collaborative Filtering)**: `findSimilarUserRecommendations()`를 통해 유사한 패턴을 가진 다른 사용자가 클릭한 딜 추천.
*   **콘텐츠 기반 (Content-Based)**: `getContentBasedRecommendations()`를 통해 내가 클릭했던 카테고리(IT, 패션 등)나 브랜드(삼성, 애플 등)와 텍스트 유사도가 높은 항목 추천.
*   **하이브리드 추천 (`getHybridRecommendations`)**: 위 두 가지 추천 방식과 유저 피드백 데이터를 합쳐 `distinctBy`로 중복을 제거하고 `shuffled().take(30)`로 최종 30개의 개인화 딜 서빙.

### 3.2 카테고리 & 브랜드 파서 (자연어 추출)
*   `extractCategoryFromTitle()`: 제목 내 "갤럭시, 그래픽카드" -> `IT`, "향수, 의류" -> `해외` 등으로 자체 분류.
*   `extractBrandFromTitle()`: "samsung", "애플", "다이슨" 등의 정규화 키워드로 브랜드 매핑.

### 3.3 사용자 상호작용 추적 (Tracking)
*   `trackInteraction()`: 사용자가 카드를 클릭하거나 체류한 시간(`InteractionType`)을 기록. 누적 횟수 및 프로필 완성도를 `PersonalizationInsights` 모델로 관리.

---

## 4. 🌐 커뮤니티 (Community) - `CommunityScreen.kt`
다양한 커뮤니티 성향 필터 및 전용 UI 적용.

### 4.1 필터링 전략
*   **"🔥 핫딜만"**: `category != "적립" && category != "이벤트"` (물리적 상품만).
*   **"💰 앱테크만"**: `category == "적립" || category == "이벤트"` (무료 혜택, 포인트 적립 등).
*   **UI/UX**: 전체 핫딜을 로드한 뒤 탭 인덱스(`selectedTabIndex`)에 따라 `state.deals.filter`를 수행하여, 네트워크 재요청 없이 메모리(클라이언트) 단에서 즉각적인 탭 전환 제공.

### 4.2 배송비 및 0원 처리 로직
*   상품 가격이 `0`인 경우: 카테고리가 "이벤트"이거나 제목에 "무료/쿠폰"이 있으면 "무료 (쿠폰/이벤트)" 표출. 그 외는 "정보 확인필요".
*   배송비 파싱: `shippingFee.trim()`이 "0" 이거나 정규표현식 `^0(원)?\s*(/|\+).*` 에 일치하면 "무료배송" 텍스트 덧붙임 처리.

---

## 5. 👤 내정보 (My Info) - `MyPageScreen.kt`
개인화된 활동 내역과 푸시 알림 인프라 제어.

### 5.1 FCM 푸시 & 알림 인프라 (`ApiService` 연동)
*   **기기 등록 (`POST /api/push/device`)**: 익명 가입 시 `device_uuid`와 `fcm_token`을 백엔드로 전송하여 알림 구독 테이블 생성.
*   **키워드 알림 (`POST /api/push/keywords`)**: "아이패드" 등을 등록 시 DB에 매핑되어 크롤러가 해당 텍스트 감지 시 백그라운드 푸시 전송.
*   **위시리스트 (찜목록)**: `POST /api/wishlist/from-keyword`, `GET /api/wishlist/{wishlist_id}/history` 등 전용 Wishlist API를 통해 관심 상품의 지속적인 가격 추적 및 변동 알림(Alarm State) 토글 기능 제공.

---

## 🛠️ 6. 프론트엔드 네트워크 통신 아키텍처 - `NetworkModule.kt`

### 6.1 타임아웃 및 인터셉터 설정
*   **타임아웃 분배**: 커넥션, 리드, 라이트 타임아웃을 각각 **120초**로 길게 설정. 이는 백엔드(Python)에서 AI 파싱 로직(`Gemini`)이 가동될 때 응답 시간이 길어지는 상황을 고려한 예외 처리.
*   **로깅 & 헤더**: `HttpLoggingInterceptor` 적용 및 모든 요청에 `User-Agent: InsightDeal-Android/1.0` 주입.

---

## 🕸️ 7. 인앱 브라우저 (WebView) 심화 로직 - `DealWebViewScreen.kt`

### 7.1 봇 차단 방어막(WAF) 완벽 우회 (User-Agent 변조)
*   네이버 스마트스토어, 클라우드플레어 등 봇/스크래퍼를 튕겨내는 쇼핑몰을 무사히 열기 위해 **정상 모바일 크롬 브라우저 UA**(`Mozilla/5.0 (Linux; Android 14; SM-S928N)...`)를 강제로 주입.
*   `bbasak.com` 같은 특정 폐쇄몰의 경우 **PC 버전 브라우저 UA**(`Windows NT 10.0; Win64; x64...`)로 런타임에 분기 처리.
*   **Referer 변조**: 네이버 쇼핑몰 접근 시 `Referer` 헤더를 `https://search.naver.com`으로 조작하여 자연스러운 검색 유입으로 위장.

### 7.2 앱 스키마 (intent://) 핸들링
*   `shouldOverrideUrlLoading`을 오버라이딩하여, 쇼핑몰 결제창이나 앱카드 구동 시 발생하는 `intent://` 프로토콜을 `Intent.parseUri`로 변환하여 로컬 기기에 설치된 앱(예: 카드사 앱, 네이버페이 앱)이 정상적으로 뜨도록 분기.

---

## 🧠 8. 하이브리드 어그리게이터 (Backend Aggregation) - `aggregator_service.py`
InsightDeal의 심장부로, 크롤링된 가공되지 않은 데이터를 완벽한 핫딜 정보로 탈바꿈시키는 계층.

### 8.1 스팸/어뷰징 게시판 뻘글 필터링
*   `spam_keywords` 리스트("공지", "질문", "몰테일", "바이럴", "활동내역" 등)를 사용하여 핫딜 게시판에 올라오는 질문 글이나 운영자 공지를 원천 차단하여 DB 오염을 막음.

### 8.2 AI 기반 다중 핫딜 분할 엔진 (Gemini 2.5 Flash)
*   **분할 트리거 (Token Saving)**: 제목의 온점/쉼표 갯수(`count(".")`)와 본문의 금액 정규식 패턴(`[0-9]+원`) 매칭 수를 비교. 1개의 글에 여러 상품이 올려진 "모음전" 형태이거나 가격 추출에 완전히 실패한 경우에만 AI 작동.
*   **외화 자동 변환**: 프롬프트 규칙을 통해 `$` 또는 `€` 표시의 해외 직구 가격을 원화(KRW)로 자동 환산하여 파싱.
*   **할인가 계산기**: "40,000원 정가 75% 할인"과 같은 자연어에서 LLM이 직접 수식을 계산하여 최종 가격 `10,000`을 추출하도록 강제.

### 8.3 메타태그 스나이핑 로직
*   본문 정규식이나 AI로도 가격 파싱에 실패했을 때 (`final_price == 0` 이고 `ecommerce_link`가 존재하는 경우), 
    해당 쇼핑몰 링크로 직접 침투(`urllib.request`)하여 `og:price:amount` 또는 `product:price:amount` 메타 태그를 읽어와 숨겨진 최종 결제 금액을 탈취함 (단, 쿠팡 등 방어가 강한 몰은 제외).

### 8.4 꿀딜(Honey Score) 동적 점수 알고리즘
*   `조회수 / 100 + (좋아요 * 10) + (댓글수 * 5)` 공식을 사용하여 `honey_score`를 계산.
*   특정 커뮤니티의 기준을 넘은 글(`is_super_hotdeal == True`)은 알고리즘 점수와 무관하게 꿀딜 점수를 100점으로 강제 세팅.
*   가격이 0원 초과인데 점수가 너무 낮게 측정된 경우 난수(`random.randint(50, 70)`)를 부여하여 최소한의 노출도를 보장함.

---

## 9. 데이터 파이프라인 및 개발 환경 관리 (Operations & Scraper)

### 9.1 스크래퍼 정교화 및 고품질 데이터 확보
*   **FmkoreaScraper (펨코)**: 일반 게시판 스크래핑 방식에서 벗어나, `&sort_index=pop&order_type=desc` 파라미터를 활용한 '인기/포텐' 필터링 URL을 직접 타겟팅.
*   **PpomppuScraper (뽐뿌)**: 일반 게시판 스크래핑 방식에서 벗어나, `&hotlist_flag=999` 파라미터를 활용한 핫게시글을 직접 타겟팅하며, 제목 앞의 `[인기]`, `[HOT]` 아이콘 여부를 추가 확인하여 비인기 딜을 엄격하게 필터링.
*   **엄격한 필터링**: 메타데이터 상 `is_super_hotdeal` 정보가 없거나 점수가 낮은 무의미한 게시물은 파이프라인 단에서 즉각 `None` 처리하여 DB 적재를 원천 차단.

### 9.2 프로젝트 폴더 아키텍처 및 IDE 환경 세팅
*   **루트 디렉토리 청결화**: 크롤링 과정에서 발생한 HTML 덤프나 테스트 임시 파일(`*.html`, `*.txt` 등)은 `agent_workspace/` 하위로 격리 보관.
*   **IDE 안전 숨김 처리**: 안드로이드 스튜디오 환경을 고려하여, `.gemini`, `.bkit`, `.idea` 등 시스템 및 에이전트 구동에 필수적인 설정 폴더들은 물리적 이동을 금지하고, IDE 내부의 `Ignored Files and Folders` 세팅으로 시각적으로만 가려 빌드 및 에이전트 안정성을 보장.
