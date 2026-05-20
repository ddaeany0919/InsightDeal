# 🎨 [구현 계획서] InsightDeal 3단계 최종 통합 고도화 계획 (수집처 카테고리 우선 매핑 아키텍처 추가)

본 구현 계획서는 **InsightDeal** 앱의 상용화 수준 완성도(토스, 29CM 급)를 달성하기 위해, 사용자가 지정하신 **4대 핵심 프리미엄 피처(수수료 고지 UI, 실시간 FCM 푸시 딥링크, UI/UX 터치 및 네트워크 감쇠 최적화, Paging 3 위시리스트)**와 더불어, **"수집처 자체 카테고리 최우선 매핑 및 Fallback RegexNormalizer 아키텍처"** 고도화 과제를 유기적으로 백엔드와 안드로이드 클라이언트에 완벽 이식하는 설계 및 구현 계획을 명세합니다.

---

## 📌 5대 핵심 고도화 과제 및 아키텍처 개요

### 1. [법적 필수] 제휴 마케팅 수수료 우아한 고지 UI 도입 및 파트너스 도메인 필터링
*   **해결 방안**:
    *   유저가 핫딜 상세 화면 또는 카드를 클릭해 외부 브라우저로 아웃바운드될 때, Toss 감성의 프리미엄 **수수료 안내 BottomSheet**를 띄웁니다.
    *   **도메인 필터링**: 이탈하려는 외부 링크가 쿠팡(`coupang.com`)이나 알리익스프레스(`aliexpress.com`) 등 제휴 마케팅 대상 도메인일 경우에만 작동합니다.
    *   **"오늘 하루 동안 보지 않기"** 체크박스를 탑재하고 `EncryptedSharedPreferences`로 쿠키 수명을 관리하여 유저 중복 피로도를 최소화합니다.

### 2. [서비스 락인] 키워드 실시간 Rich FCM 푸시 알림 및 딥링크(Deep Link)
*   **해결 방안**:
    *   **딥링크(Deep Link) 탑재**: 알림창을 탭하면 앱의 메인 화면이 아닌, 해당 **핫딜 상품의 인앱 상세 분석 페이지**(`/deals/{deal_id}`)로 곧바로 직행(Redirect)하도록 클라이언트에 이식합니다.
    *   **Rich Notification**: 이미지 썸네일 URL을 FCM 페이로드에 포함하여 알림창 우측에 핫딜 이미지가 큼직하게 노출되도록 구현합니다.
    *   **백엔드 푸시 전송 고도화**: 사용자가 등록한 관심 키워드와 핫딜 제목을 대조하여 매칭 즉시 FCM 발송 요청을 처리합니다.

### 3. [UI/UX 디테일] Scroll-Aware 터치 모션 조율 및 네트워크 Flapping 디바운스 이식
*   **해결 방안**:
    *   **Scroll-Aware bounceClick Modifier**: 카드를 터치한 후 이동(Drag) 거리가 스크롤 임계점을 넘는 즉시 바운스 스케일을 즉각 원래대로(`1.0f`) 복구시키고 클릭 동작을 부드럽게 Cancel 처리합니다.
    *   **Network Debounce 스트림**: `NetworkMonitor`의 StateFlow 변화 주기에 `debounce(1500)` 필터를 걸어 단기적인 네트워크 껌뻑임 현상을 무시함으로써 레이아웃의 무한 리포지셔닝 떨림 현상을 원천 방어합니다.

### 4. [위시리스트 고도화] Room DB와 Paging 3를 결합한 초고성능 오프라인 우선 위시리스트
*   **해결 방안**:
    *   위시리스트 로딩 데이터 파이프라인을 `PagingSource`로 추상화하여, Room DB로부터 무한 스크롤 환경에서 20개 단위로 데이터를 안전하고 매끄럽게 페이징 로딩(Paging 3)함으로써 60fps/120fps Jank-Free 오프라인 UX를 이식합니다.

### 5. [지능형 카테고리] 수집처 자체 카테고리 최우선 매핑 및 Fallback RegexNormalizer 아키텍처 (신규 추가)
*   **문제 상황**: 기존에는 수집처 분류와 무관하게 무조건 제목 텍스트 기반 정규식(`RegexNormalizer.py`)으로 카테고리를 추정하여 오분류 위험이 상존했습니다. (예: 화장품 핫딜 제목에 가전 명칭이 우연히 포함된 경우 가전제품으로 잘못 자동 매핑됨)
*   **해결 방안**:
    1.  **스크래퍼 자체 파싱 고도화**:
        *   **뽐뿌 (`ppomppu_scraper.py`)**: 뽐뿌 핫딜은 리스트와 제목 맨 앞의 대괄호 `[분류명]` 형태로 카테고리가 강제 제공되므로, 정규식을 통해 이를 `category` 필드로 안전하게 추출합니다. (예: `[식품/건강]` -> `category="식품/건강"`)
        *   **퀘이사존 (`quasarzone_scraper.py`)**: 퀘이사존 핫딜 목록의 `span.category` 태그를 직접 CSS Selector로 스나이핑하여 `category` 필드를 채워줍니다.
        *   **에펨코리아 (`fmkorea_scraper.py`)**: 기존 파싱 로직의 `span.category` 추출 로직을 그대로 활용하여 `category` 필드를 채워 보냅니다.
    2.  **1순위 매핑 및 2순위 Fallback 구조 정립 (`RegexNormalizer.py`)**:
        *   `RegexNormalizer`에 수집처 고유 카테고리를 당사 10대 정형 카테고리(`음식`, `의류`, `PC제품`, `가전제품`, `생활용품`, `화장품`, `SW/게임`, `모바일/기프티콘`, `패키지/이용권`, `해외핫딜`)로 변환시켜 주는 **화이트리스트 매핑 테이블(`SCRAPER_CATEGORY_MAP`)**을 구축합니다.
        *   스크래퍼가 카테고리를 성공적으로 넘겼다면 해당 변환 테이블을 거쳐 **최우선(1순위)으로 확정**합니다.
        *   만약 수집처 카테고리가 누락되었거나 변환 테이블 매핑에 실패한 경우(None/기타)에만 **2차(Fallback)로 기존 제목 기반 정규표현식 휴리스틱 파서**를 구동시킵니다.
    3.  **LlmNormalizer 하이브리드 파이프라인 연동**:
        *   `ProductNormalizer.normalize` 인터페이스 시그니처에 `scraped_category` 매개변수를 추가하여 스크래퍼 데이터를 안전하게 내장 파이프라인에 주입합니다.
        *   AI(Gemini) 분석이 불가피하게 구동될 때도, 수집처 카테고리를 프롬프트에 **컨텍스트 힌트**로 동시 제공하여 AI의 상품 카테고리 정확도를 200% 극대화합니다.

---

## 🛠️ Proposed Changes (제안된 변경 내역)

### 💻 백엔드 컴포넌트

#### [MODIFY] [base.py](file:///C:/Users/kth00/StudioProjects/InsightDeal/backend/services/normalizer/base.py)
*   `ProductNormalizer.normalize` 추상 메서드 시그니처에 `scraped_category: Optional[str] = None` 선택 인자 주입.

#### [MODIFY] [regex_normalizer.py](file:///C:/Users/kth00/StudioProjects/InsightDeal/backend/services/normalizer/regex_normalizer.py)
*   수집처 카테고리 매핑 딕셔너리 `SCRAPER_CATEGORY_MAP` 정의.
*   `map_scraper_category` 및 `normalize` 메서드를 고쳐 외부 수집 카테고리를 1순위로 채택하고, 누락 시에만 정규식 Fallback을 태우는 통합 분기 구조 장착.

#### [MODIFY] [llm_normalizer.py](file:///C:/Users/kth00/StudioProjects/InsightDeal/backend/services/normalizer/llm_normalizer.py)
*   `normalize` 시그니처에 `scraped_category` 파라미터 적용 및 `fallback_normalizer.normalize`로 흐름 연동.
*   Gemini 프롬프트에 수집처 자체 분류 힌트를 주입해 정확도 상승 제어.

#### [MODIFY] [aggregator_service.py](file:///C:/Users/kth00/StudioProjects/InsightDeal/backend/services/aggregator_service.py)
*   수집 데이터 저장 시 `scraped_data.get("category")`를 추출하여 `self.normalizer.normalize(raw_title, scraped_category)`로 직접 연계 주입.

#### [MODIFY] [ppomppu_scraper.py](file:///C:/Users/kth00/StudioProjects/InsightDeal/backend/scrapers/ppomppu_scraper.py)
*   뽐뿌 제목의 대괄호 `[...]` 접두사를 정규식으로 안전하게 파싱하여 `"category"` 데이터 필드로 이식.

#### [MODIFY] [quasarzone_scraper.py](file:///C:/Users/kth00/StudioProjects/InsightDeal/backend/scrapers/quasarzone_scraper.py)
*   리스트 내 `span.category` 태그의 텍스트를 CSS Selector로 추출하여 `"category"` 데이터 필드로 이식.

---

## 🧪 Verification Plan (검증 계획)

### 1. 백엔드 카테고리 파이프라인 무결성 검증
*   `backend/scrapers/test_ppomppu.py` 및 `test_normalizer.py` 등 테스트 배치를 실행하여, 제목에 엉뚱한 키워드가 섞여 있어도 수집처 카테고리가 있을 때 1순위로 완벽히 매핑되는지 검증합니다.
    *   **예시 검증 케이스**: `[화장품/미용] 로지텍 무선 마우스 세트` 라는 가상의 뽐뿌 핫딜이 수집될 때, 기존에는 '마우스' 때문에 `PC제품`으로 오분류되었으나, 개선 후에는 `[화장품/미용]` 대괄호를 근거로 `화장품` 카테고리로 1순위 강제 고정되는지 확인합니다.

### 2. 안드로이드 클라이언트 컴파일 빌드 검증
*   `.\gradlew compileDebugKotlin`를 실행하여 딥링크 인텐트, Room 페이징, 바운스 클릭 제스처가 경고나 컴파일 빌드 실패 없이 무결한지 검증합니다.
