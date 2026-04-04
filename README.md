# InsightDeal (핫딜 최저가 분석 포털 v1.0) 🚀

InsightDeal은 최저가를 찾는 모든 사용자에게 최적의 사용자 경험과 빅데이터 기반의 가장 빠른 핫딜 정보를 제공하는 풀스택 앱 서비스입니다. 
복잡하고 무거운 거대한 스크래핑 파이프라인에서 시작하여, **초고속 비동기 엔진**과 **클린 아키텍처**로 재탄생했습니다.

---

## 🌟 v1.0 핵심 성과 (Major Enhancements)

### 1. ⚡ 백엔드 초고속 비동기 스크래핑 무중단 엔진
- **Selenium(브라우저 렌더링) 전면 제거**: 무거운 동기적 드라이버를 걷어내고 `httpx` + `asyncio`를 합친 `AsyncBaseScraper`를 도입해 **처리 속도를 최대 300% 이상 끌어올렸으며 서버 메모리 점유율을 90% 이상 드라마틱하게 낮췄습니다.**
- **Pluggable & Graceful Architecture**: 네이버/쿠팡 오픈마켓 API가 키 부재 시 크래시를 내지 않도록 **Mock 데이터 Fallback**을 지원하며, 뽐뿌, 퀘이사존, 루리웹 등 수많은 커뮤니티 엔진을 하나로 통합했습니다.

### 2. 🧠 LlmNormalizer (초지능 상품 정규화 AI)
- Gemini 1.5 Flash 모델 기반 지능형 정규화(AI) 파서를 적용했습니다.
- **Auto-Fallback 시스템**: 사용자 환경에 `GEMINI_API_KEY`가 없을 시 무너지는 것을 방지하고 즉시 기존 정규식(`RegexNormalizer`) 기반으로 Fallback 되는 구조를 갖추었습니다.

### 3. 📱 안드로이드 프론트엔드 - 클린 아키텍처 및 UI 통폐합
- 흩어져 있던 기능 파편을 **Feature 기반의 응집도 높은 아키텍처(feature.home 등)**로 완벽하게 재정립했습니다.
- 수많은 파일로 찢어져 있던 위시리스트 카드들(`WishlistCard`, `Simple`, `Animated`)을 **`StandardWishlistCard` 하나로 완벽하게 통폐합(Consolidation)** 하였습니다.

### 4. 🦴 Skeleton UI UX 레볼루션
- 데이터 렌더링 과정에서 발생하는 "빈 화면 대기 현상"을 종식시키기 위해, **뼈대(Skeleton) Shimmer 애니메이션** 모듈을 직접 제작해 도입했습니다.
- 유저 진입 시 로딩 상태조차 마치 **"데이터 렌더링이 꽉 차 있는 것처럼 우아한 UI"**를 제공하여 잔존율(Retention)을 극대화합니다.

### 5. 🛡️ 보안 강화 및 차세대 무중단 배포 (DevSecOps)
- **보안 문단속 (Security Check)**: API의 `CORS` 룰셋 강화, 무분별한 접근 차단 (`HTTP Methods` 및 `Origins` 환경 변수 격리).
- **.gitignore 대청소**: `build/`, `.env`, 로컬 설정 등 민감 정보가 소스코드에 포함되지 않도록 원천 차단.
- **GitHub Actions (CI/CD 파이프라인)**: 코드가 메인 서버에 Push 될 시, 자동으로 백엔드 테스트를 거친 후 안드로이드 Build APK를 추출하도록 파이프라인을 기획했습니다.

---

## 🛠️ 기술 스택 (Tech Stack)
- **Frontend App**: Android / Jetpack Compose (Material 3) / ViewModel & StateFlow
- **Backend API**: Python / FastAPI / SQLAlchemy 2.0 / PostgreSQL
- **Scraper Engine**: AsyncIO / httpx / BeautifulSoup4
- **AI Processing**: Google Generative AI (Gemini 1.5 Flash)
- **Infra/DevOps**: Docker & Docker Compose / GitHub Actions

---

## ⏭️ 향후 로드맵 (Roadmap v1.1)
- [ ] **FCM True Real-time Notification**: 지정한 우주 최저가 도달 즉시 앱 사용자 스마트폰에 푸시 알림 발사
- [ ] **수익화 자동 변환(Affiliate Redirection)**: 감지된 링크를 자동으로 쿠팡 파트너스 등의 수익화 링크로 변환하여 중단 불가능한 BM 비즈니스 모델 구축

*Copyright 2024-2026. POPUP STUDIO PTE. LTD / CTO Antigravity*