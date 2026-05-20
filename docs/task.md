# 📝 InsightDeal 3단계 최종 통합 고도화 할 일 목록 (Task)

- `[x]` **1. 제휴 마케팅 수수료 우아한 고지 UI 도입**
  - `[x]` 1-1. `HomeScreen.kt`에 Toss/29CM 감성의 프리미엄 **수수료 안내 BottomSheet** 설계 및 배치
  - `[x]` 1-2. "오늘 하루 동안 보지 않기" 체크박스 상태 제어를 위한 `EncryptedSharedPreferences` 보안 로컬 연동
  - `[x]` 1-3. 핫딜 클릭 및 아웃바운드 시 쿠키 검증 필터를 통해 BottomSheet 노출/스킵 제어 적용

- `[x]` **2. 키워드 실시간 Rich FCM 푸시 알림 및 상세화면 딥링크 연동**
  - `[x]` 2-1. FCM 알림창 탭 시 메인이 아닌 **핫딜 상세 분석 화면(`/deals/{deal_id}`)**으로 다이렉트 랜딩하는 딥링크 인텐트 필터 및 라우팅 구현 (`NotificationService.kt`, `AndroidManifest.xml` 등)
  - `[x]` 2-2. 썸네일 이미지 URL을 FCM 페이로드에서 파싱하여 알림창 우측에 핫딜 이미지를 렌더링하는 Rich Notification(BigPictureStyle) 이식
  - `[x]` 2-3. 백엔드 `push_worker.py` 및 `aggregator_service.py`에서 키워드 정밀 매핑 및 이미지 주입 지원

- `[x]` **3. Scroll-Aware bounceClick 제스처 튜닝 및 네트워크 Flapping 디바운스 이식**
  - `[x]` 3-1. `bounceClick` Modifier 내에 드래그 제스처 인지 루틴을 통합하여, 터치 변위가 스크롤 임계점을 넘는 즉각 바운스 스케일을 `1.0f`로 강제 환원하고 클릭 감지 캔슬 처리
  - `[x]` 3-2. `NetworkMonitor.kt`의 `callbackFlow` 스트림에 `debounce(1500)` 필터를 적용하여 음영 네트워크 플래핑 시 레이아웃 깜빡임 및 떨림 현상 원천 차단

- `[x]` **4. Room DB와 Paging 3를 결합한 초고성능 오프라인 우선 위시리스트 구축**
  - `[x]` 4-1. 로컬 찜 데이터를 영구 보관할 `WishlistEntity` [NEW]와 `WishlistDao` [NEW] 설계
  - `[x]` 4-2. 위시리스트 데이터를 20개 단위로 안전하고 매끄럽게 가져오는 `PagingSource` 로직 구현 및 Paging 3 파이프라인 결합
  - `[x]` 4-3. 위시리스트 화면에 Room 캐시 페이징 스크롤 연동하여 60fps Jank-Free 오프라인 UX 완성

- `[x]` **5. [기획 연동] 제휴 마케팅 수수료 파트너스 ID 및 도메인 필터링 연동**
  - `[x]` 5-1. 백엔드 `.env` 파일에 쿠팡 파트너스 ID(`COUPANG_PARTNERS_ID`) 및 알리익스프레스 어소시에이트 ID(`ALI_ASSOCIATES_ID`) 연동 환경변수 기획 수립 및 docs 등재
  - `[x]` 5-2. 핫딜 아웃링크 생성 시, 백엔드에서 파트너스 파라미터를 동적으로 조립하는 수익화 파이프라인 설계
  - `[x]` 5-3. 안드로이드 `HomeScreen.kt` 내 외부 이탈 링크가 쿠팡/알리익스프레스 등 제휴 도메인인 경우에만 BottomSheet가 선택적으로 노출되는 도메인 화이트리스트 필터링 구현
  - `[x]` 5-4. 제휴 수수료 고지 BottomSheet UI 내에 각 도메인별(쿠팡 파트너스 / 알리 어소시에이트) 맞춤형 제휴 안내 문구를 동적으로 매핑하여 출력하도록 개선

- `[x]` **6. [카테고리 연동] 수집처 자체 카테고리 최우선 매핑 및 Fallback 아키텍처**
  - `[x]` 6-1. `ppomppu_scraper.py` 대괄호 접두사 카테고리 파싱 및 `quasarzone_scraper.py` `span.category` 파싱 고도화
  - `[x]` 6-2. `RegexNormalizer.py`에 수집처 고유 카테고리를 당사 10대 정형 카테고리로 변환하는 화이트리스트 `SCRAPER_CATEGORY_MAP` 구축 및 `normalize`에 1순위 적용/2순위 Fallback 구조 이식
  - `[x]` 6-3. `LlmNormalizer.py` 내에 이모지/특수문자 제거 및 429 API 장애극복 로직 보완 및 `scraped_category` 파라미터 프롬프트 힌트 컨텍스트 주입 지원
  - `[x]` 6-4. `aggregator_service.py`에서 수집한 `scraped_category` 값을 `normalize` 인자로 완벽 연계 이식
  - `[x]` 6-5. `test_scraped_category_prioritization.py` 유닛 테스트 작성을 통한 1순위 수집 분류 우선 고정 기능 정상 작동 및 무결성 검증

- `[x]` **7. [전수 고도화] 타 사이트 스크래퍼 카테고리 수집 보강**
  - `[x]` 7-1. 클리앙(`clien_scraper.py`): 제목 대괄호 정규식 추출 및 `"category"` 이식 완료
  - `[x]` 7-2. 루리웹(`ruliweb_scraper.py`): 대괄호 정규식 추출 및 `"category"` 이식 완료
  - `[x]` 7-3. 빠삭(`bbasak_base_scraper.py`): 제목 대괄호 정규식 + 각 서브게시판별 `default_category` 주입 하이브리드 로직 완공
  - `[x]` 7-4. 네이버 쇼핑(`naver_shopping_scraper.py`): `category1`을 단일 `"category"` 대표 필드로 매핑 및 딕셔너리 호환 인터페이스(`__getitem__`, `get`) 완비로 기존 API 버그 원천 차단
