# InsightDeal 🛍️

> **매일 쓰고 싶은** 한국 커뮤니티 핫딜 통합 앱  
> **폴센트를 뛰어넘는** 90일 가격 히스토리와 4개 쇼핑몰 실시간 비교

AI 분석 기반 한국 시장 대상 핫딜 통합 앱으로, Google Play 배포 및 수익화를 목표로 개발 중입니다.

---

## 🎯 **핵심 차별화 포인트**

### **vs 폴센트 비교**
| 기능 | 폴센트 | InsightDeal |
|------|--------|-------------|
| 가격 히스토리 | 30일 | **90일 (3배 더 길게!)** |
| 플랫폼 지원 | 쿠팡만 | **4개 몰 동시 (쿠팡/11번가/G마켓/옥션)** |
| 응답 속도 | 알 수 없음 | **1초 내 차트 로딩** |
| 사용자 경험 | - | **첫 설치부터 매일 쓰고 싶게** |

### **사용자 중심 설계**
- **첫 인상**: 빈 화면 없음, 샘플 딜로 즉시 가치 체감
- **명확한 이득**: "16,000원 절약!" + "3개월 중 최저가" 
- **신뢰 확보**: "2분 전 확인됨" + "배송비 포함 총액" 표시
- **즉시 피드백**: "✅ 추적 시작! 첫 확인 예정 13:45"

---

## 🏗️ **Project Status (Day 5 완료)**

### **✅ Backend (완료)**
- **PostgreSQL 스키마**: 90일 가격 히스토리, 트래킹, 알림, 스케줄러 로그
- **90일 가격 히스토리 API**: `GET /api/history?product={name}&period={7|30|90}`
- **가격 수집 스케줄러**: 4몰 활성 트랙 우선 수집, 15분/1시간 주기, 5% 변동 감지

### **✅ Android (완료)**  
- **PriceHistoryRepository**: 90일 지원, 1초 로드, 5분 캐시, 7/30/90 프리로드
- **온보딩**: 빈 피드 시 샘플 딜 섹션 + "첫 상품 추적 시작하기"
- **가격 카드**: 절약/3개월 최저/하락률 표시 + "지금 구매" vs "5% 하락 알림" CTA
- **추적 등록 피드백**: "✅ 추적 시작! 5분마다 확인… 첫 확인 예정 HH:mm"

### **📁 완료된 주요 파일들**
```
backend/
├── database/schema.sql          # 90일 히스토리 PostgreSQL 스키마
├── api/history.py              # 90일 가격 히스토리 API 엔드포인트  
└── scheduler/price_collector.py # 4몰 가격 수집 스케줄러

app/src/main/java/com/ddaeany0919/insightdeal/
├── data/PriceHistoryRepository.kt        # 90일 히스토리 Android Repository
├── SampleDealsOnboarding.kt              # 첫 사용자 온보딩 컴포넌트
├── ui/components/EnhancedPriceCard.kt    # 사용자 중심 가격 카드
└── ui/components/TrackingRegistrationFeedback.kt # 추적 등록 즉시 피드백
```

---

## 🗂️ **Repository Structure (통합 예정)**

### **현재 구조**
```
InsightDeal/
├── InsightDeal_Backend/    # 구 백엔드 (통합 예정)
├── backend/                # 신 백엔드 (활성)
│   ├── api/               # FastAPI 엔드포인트
│   ├── scheduler/         # APScheduler 가격 수집
│   ├── database/          # PostgreSQL 스키마
│   └── ...
├── app/                   # Android Jetpack Compose
└── README.md
```

### **예정 구조 (Day 6)**
```
InsightDeal/
├── backend/               # 통합된 백엔드 (진입점)
│   ├── api/              # FastAPI 라우터
│   ├── scheduler/        # APScheduler 잡
│   ├── scrapers/         # base + 각 플랫폼 스크래퍼
│   ├── database/         # schema.sql, migrations/
│   ├── core/             # 공통 모델/로깅/설정
│   └── settings/         # 환경별 설정
├── app/                  # Android
├── infra/                # docker-compose, Dockerfile들
└── README.md
```

---

## 🚀 **Next Steps (Day 6-7 로드맵)**

### **⏳ Day 6 (내일) - 핵심 연결**
1. **리포 구조 통합**
   - InsightDeal_Backend → backend로 병합
   - 최상위/하위 README 갱신, docker-compose 추가

2. **Android UX 완성**
   - 홈 빈 피드 → 온보딩 연결
   - Watchlist 상세 7/30/90 토글 + 프리로드

3. **FCM 알림 시스템**
   - 목표가/하락 5% 알림
   - 채널/권한/딥링크 처리

### **🎯 Day 7 (모레) - 배포 준비**
4. **실데이터 연결**
   - scrapers → 홈 피드 API 연결
   - 오류/지연 대비 스켈레톤/리트라이

5. **배포 준비**
   - APK 릴리스, 프로가드
   - Google Play 스토어 자료

---

## 🛠️ **How to Run (요약)**

### **Backend**
```bash
# 현재 백엔드 진입점: backend/
cd backend
poetry install  # 또는 pip install -r requirements.txt
uvicorn api.main:app --reload
# 스케줄러는 별도 프로세스로 실행
```

### **Android App**
```bash
# Android Studio로 빌드
# BASE_URL을 로컬/서버에 맞게 설정
```

### **환경 변수**
```bash
DATABASE_URL=postgresql://localhost/insightdeal
FCM_SERVER_KEY=your_fcm_key
```

---

## 📋 **AI 인수인계 스크립트**

다음 AI에게 전달할 때 이렇게 말하세요:

> "이 프로젝트는 한국 커뮤니티 핫딜 통합 앱 'InsightDeal'입니다. 목표는 **매일 쓰는 앱**. 현재 **Day 5까지 완료** 상태로 90일 가격 히스토리(폴센트 30일 대비 3배), 4몰 가격 수집, 사용자 중심 UX(온보딩/절약/명확한 CTA/추적 피드백)가 반영되어 있습니다.
>
> **이어서 다음을 진행해주세요:**
> 1) InsightDeal_Backend 디렉터리를 backend로 통합하고, backend 하위에 api/scheduler/scrapers/database/core/settings 구조 정리. 최상위 README와 backend/README를 실행/배포 가이드 포함해 업데이트. docker-compose.yml과 Dockerfile.api/Dockerfile.scheduler를 추가.
> 2) Android에서 홈 빈 피드 시 SampleDealsOnboarding을 연결하고, Watchlist 상세 화면에 7/30/90 토글을 추가, PriceHistoryRepository.preloadHistoryPeriods와 연결해 200ms 내 전환되도록 확인.
> 3) 백엔드에 FCM 연동 모듈을 추가하고, 목표가 또는 5% 이상 하락 시 푸시를 발송. Android 쪽에 알림 채널/권한/딥링크 처리.
> 4) 커뮤니티 스크래퍼로 홈 피드 실데이터 표시. 실패/지연 시 스켈레톤 UI와 리트라이 UX로 사용자 불편 최소화.
> 5) 배포 준비(프로가드, 릴리스 키, 스토어 자료)까지 정리.
>
> **주의할 점:**
> - **사용자 경험 최우선**. 1초 내 차트, 200ms 토글 전환, 실패 시 조용한 폴백 유지.
> - 모든 변경은 traceId 기반 로그를 남기고 커밋 메시지에 작업 범위를 명확히 기록.
> - **README 최상단에 진입점과 실행법을 명확히 제시**할 것."

---

## 🏆 **완성 목표**

**이번 주 금요일까지**: Google Play에 배포 가능한 완성된 앱

- **기술적 우수성**: 90일 히스토리, 4몰 동시 수집, 1초 로딩
- **사용자 경험**: 첫 설치부터 매일 쓰고 싶은 앱
- **수익화 준비**: 광고/프리미엄 모델 기반 구조

**핵심 슬로간**: "폴센트보다 3배 더 오래, 4배 더 많이, 10배 더 빠르게" 🚀

---

## 🛍️ **기존 README 내용 (참고용)**

<details>
<summary>기존 상세 기능 설명 펼치기</summary>

### 🎆 기능 개요

**"๋ฐ๊ฒฌ โ ์ถ์  โ ๋น๊ต โ ๊ตฌ๋งค"** ๋ฅผ ํ ์ฑ์์!

- **๋ฐ๊ฒฌ**: ๋ฝ๋ฝ/๋ฃจ๋ฆฌ์น/ํด๋ฆฌ์ ์ปค๋ฎค๋ํฐ ๋ ์ ์ค์๊ฐ ์์ง
- **์ถ์ **: ํด์ผํธ ์คํ์ผ๋ก ๊ด์ฌ ์ํ ๊ฐ๊ฒฉ ์ถ์ 
- **๋น๊ต**: ์ฟ ํ/11๋ฒ๊ฐ/G๋งํท/์ฅ์ 4๊ฐ ์ผํ๋ชฐ ๊ฐ๊ฒฉ ํ๋์ ๋น๊ต
- **๋งค์นญ**: AI๊ฐ ์ถ์  ์ํ๊ณผ ์ปค๋ฎค๋ํฐ ๋์ ์๋์ผ๋ก ์ฐ๊ฒฐ

### ๐  ์ฃผ์ ๊ธฐ๋ฅ

#### ๐  **ํ - ๋ ๋ฐ๊ฒฌ ํผ๋**
- **์ ๋ณด ์ฐ์  ๋ฆฌ์คํธ ๋ทฐ**: ํซ๋์ ์ต์ ํ๋ UI/UX
- **์ ํ์  ๊ทธ๋ฆฌ๋ ๋ทฐ**: ์๊ฐ์  ์ผํ ๊ฒฝํ
- **์ค์๊ฐ 4๋ชฐ ๊ฐ๊ฒฉ ๋น๊ต**: ์ฟ ํ/11๋ฒ๊ฐ/G๋งํท/์ฅ์
- **์ค๋งํธ ํํฐ**: ๋ฌด๋ฃ๋ฐฐ์ก/ํด์ธ์ ์ธ/์ฟ ํ๋ณด๋ค์ธ๋ ๋ฑ
- **์์ํด๋ฆญ ์ถ์ **: "์ถ์  ์ถ๊ฐ" ๋ฒํผ์ผ๋ก ์ฆ์ ์์๋ฆฌ์คํธ ๋ฑ๋ก

#### ๐ **์ถ์  - ๊ฐ์ธ ์์๋ฆฌ์คํธ**
- **URL ๋ถ์ฌ๋ฃ๊ธฐ**: ํด์ผํธ ๋ฐฉ์ ์ํ ์ถ๊ฐ
- **๋ชฉํ๊ฐ ์ค์ **: ์ํ๋ ๊ฐ๊ฒฉ ๋๋ฌ ์ ํธ์ ์๋ฆผ
- **๊ฐ๊ฒฉ ํ์คํ ๋ฆฌ**: 7์ผ/30์ผ/90์ผ ์ ํ ๊ฐ๋ฅ
- **๋ฏธ๋ ๊ทธ๋ํ**: ์นด๋์์ 30์ผ ๊ฐ๊ฒฉ ๋ณ๋ ์ฆ์ ํ์ธ
- **4๋ชฐ ๋์ ์ถ์ **: ์ด๋ ์ผํ๋ชฐ์ด ๊ฐ์ฅ ์ธ์ง ์ค์๊ฐ ๋น๊ต

#### ๐ฏ **๋งค์นญ - AI ๋ ๋ฐ๊ฒฌ**
- **์ค๋งํธ ๋งค์นญ**: ์ถ์  ์ค์ธ ์ํ์ด ์ปค๋ฎค๋ํฐ์ ํน๊ฐ๋ก ๋ฑ์ฅ ์ ์๋ฆผ
- **์ ์ฝ ๊ณ์ฐ**: "๋ชฉํ๊ฐ๋ณด๋ค 2๋ง์ ๋ ์ธ๊ฒ ๋์์ด์!"
- **์๋ ๊ฒ์ฆ**: ์ง์ง ํน๊ฐ vs ๊ฐ์ง ํ ์ธ ๊ตฌ๋ถ

#### โ๏ธ **์ค์  - ๊ฐ์ธํ**
- **4๊ฐ์ง ์ปฌ๋ฌ ํ๋ง**: Orange/Blue/Green/Purple
- **AMOLED ์ต์ ํ**: ์์ ํ ๋ธ๋ ํ๋ง ์ง์
- **์๋ฆผ ์ค์ **: ๋ชฉํ๊ฐ ๋ฌ์ฑ/๋ ๋ฐ๊ฒฌ/๊ฐ๊ฒฉ ๋ณ๋๋ณ ์ธ๋ฐ ์กฐ์ 

</details>

---

> ๐ **InsightDeal**: ๋ฐ๊ฒฌ์ ์ฆ๊ฑฐ์ + ์ถ์ ์ ํ์ค + ๋น๊ต์ ๋๋ํจ = ์๋ฒฝํ ์ผํ ๊ฒฝํ