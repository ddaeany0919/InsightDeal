# 🔥 InsightDeal

**커뮤니티 핫딜 + 쿠팡 가격 추적 통합 앱**

*타이밍 + 폴센트를 하나로 합친 올인원 핫딜 솔루션*

---

## 📱 **주요 기능**

### 🎯 **커뮤니티 핫딜 모니터링**
- **뽐뿌, 루리웹, 클리앙, 퀘이사존** 등 6개 커뮤니티 동시 모니터링
- AI 파싱으로 **자동 상품 정보 추출**
- 실시간 **FCM 푸시 알림**
- 중복 제거 및 **스마트 필터링**

### 💰 **쿠팡 가격 추적** (폴센트 스타일)
- 개인 **워치리스트** 방식 (URL 입력)
- **7일/30일/전체** 가격 그래프
- **목표가격 도달 시 즉시 알림**
- 역대 최저가 **하이라이트 표시**
- **구매 타이밍 AI 조언**

### 🎨 **세련된 UI/UX**
- **폴센트 영감** + **InsightDeal 브랜드**
- Material 3 디자인 시스템
- **가격 변동 직관적 표시** (🔺🔻)
- **4개 탭** 네비게이션 (핫딜/가격추적/검색/설정)

---

## 🚀 **기술 스택**

### **Frontend (Android)**
- **Kotlin + Jetpack Compose**
- Material 3 Design System
- **MPAndroidChart** (가격 그래프)
- **Firebase FCM** (푸시 알림)
- Navigation Compose
- Retrofit + OkHttp

### **Backend (Python)**
- **FastAPI** (REST API 서버)
- **PostgreSQL** (메인 데이터베이스)
- **Docker + Docker Compose**
- **Selenium** (웹 스크래핑)
- **APScheduler** (자동 스케줄링)
- **Firebase Admin SDK** (FCM)

---

## 🏗️ **프로젝트 구조**

```
InsightDeal/
├── 📱 app/                           # Android 앱
│   ├── src/main/java/.../
│   │   ├── MainActivity.kt           # 탭 네비게이션 메인
│   │   ├── CoupangTrackingScreen.kt  # 쿠팡 상품 관리
│   │   ├── PriceChartScreen.kt       # 가격 그래프 (MPAndroidChart)
│   │   ├── *ViewModel.kt             # MVVM 아키텍처
│   │   ├── ApiService.kt             # REST API 통신
│   │   ├── FCMService.kt             # 푸시 알림 처리
│   │   └── ui/theme/                 # 브랜드 테마 시스템
│   └── build.gradle                  # Android 의존성
│
└── 🐍 InsightDeal_Backend/           # Python 백엔드
    ├── start_server_enhanced.py      # FastAPI 메인 서버
    ├── scrapers/                     # 커뮤니티별 스크래퍼
    ├── coupang_tracker.py            # 쿠팡 가격 추적기
    ├── firebase_config.py            # FCM 설정
    ├── models.py                     # DB 모델
    └── docker-compose.yml            # 컨테이너 설정
```

---

## ⚡ **빠른 시작**

### **1️⃣ Backend 서버 실행**
```bash
cd InsightDeal_Backend
docker-compose up --build
```
**→ http://localhost:8000 에서 API 서버 동작**

### **2️⃣ Firebase 설정**
1. [Firebase Console](https://console.firebase.google.com)에서 새 프로젝트 생성
2. Android 앱 추가: `com.yourpackage.insightdeal`
3. **google-services.json** 다운로드 → `app/` 폴더에 배치

### **3️⃣ Android 앱 빌드**
```bash
# Android Studio에서
1. Build > Rebuild Project
2. 실제 기기에서 실행
3. FCM 토큰 자동 등록 확인
```

---

## 🎯 **사용법**

### **커뮤니티 핫딜 모니터링**
1. **"핫딜" 탭** 클릭
2. 뽐뿌, 루리웹 등에서 **자동 수집된 핫딜** 확인
3. 관심 딜 클릭 → 상세 정보 및 **구매 링크**

### **쿠팡 가격 추적** 
1. **"가격추적" 탭** 클릭
2. **"+"** 버튼으로 쿠팡 URL 입력
3. **목표가격** 설정 (예: 50,000원)
4. **가격 그래프**로 추이 확인
5. 목표가격 도달 시 **자동 푸시 알림**

---

## 🔥 **차별화 포인트**

| 기능 | 타이밍 | 폴센트 | **InsightDeal** |
|------|--------|--------|----------------|
| 쿠팡 가격 추적 | ✅ | ✅ | ✅ |
| 가격 그래프 | ❌ | ✅ | ✅ |
| 커뮤니티 핫딜 | ❌ | ❌ | **✅** |
| AI 상품 파싱 | ❌ | ❌ | **✅** |
| 통합 푸시 알림 | ✅ | ❌ | **✅** |
| 구매 타이밍 조언 | ❌ | ✅ | **✅** |
| 무료 이용 | ❌ | ❌ | **✅** |

**→ InsightDeal = 타이밍 + 폴센트 + 커뮤니티 핫딜 + AI** 🚀

---

## 🎨 **스크린샷 미리보기**

### **메인 화면 (4개 탭)**
```
📱 InsightDeal
┌─────────────────────────────────┐
│ 🔥 최신 핫딜                    │
│ ┌─────────────────────────────┐ │
│ │ [뽐뿌] 갤럭시 버즈2 특가     │ │
│ │ [루리웹] PS5 재입고 소식     │ │
│ │ [클리앙] 맥북 에어 할인      │ │
│ └─────────────────────────────┘ │
│ [🔥핫딜] [💰추적] [🔍검색] [⚙설정] │
└─────────────────────────────────┘
```

### **가격 추적 화면 (폴센트 스타일)**
```
📱 InsightDeal - 가격추적
┌─────────────────────────────────┐
│ 💰 가격 추적    3개 상품 추적중  │
│ ┌─────────────────────────────┐ │
│ │ 📱 갤럭시 버즈2 - 89,000원   │ │
│ │ 🔻 -5% [최저] [목표달성]     │ │
│ │ 📊 가격 그래프 보기          │ │
│ └─────────────────────────────┘ │
│ + 새 상품 추가                  │
└─────────────────────────────────┘
```

---

## 📊 **개발 현황**

### **✅ Week 1 완료 (2025.10.24)**
- [x] Firebase FCM 설정 및 테스트
- [x] 쿠팡 크롤링 시스템 구현
- [x] 폴센트 스타일 UI 완성
- [x] 가격 그래프 (MPAndroidChart)
- [x] 통합 테스트 및 버그 수정

### **🔄 Week 2 계획**
- [ ] 사용자 계정 시스템
- [ ] 고급 알림 필터링
- [ ] 검색 기능 강화
- [ ] UI/UX 추가 개선

### **🤖 Week 3 계획**  
- [ ] AI 개인화 추천
- [ ] 꿀딜 지수 시스템
- [ ] 고급 분석 기능

---

## 🔧 **개발 환경**

### **필요 도구**
- Android Studio Hedgehog | 2023.1.1+
- Python 3.9+
- Docker & Docker Compose
- PostgreSQL 15+

### **주요 라이브러리**
- **MPAndroidChart**: v3.1.0 (그래프)
- **Firebase FCM**: v32.7.1 (푸시)
- **Retrofit**: v2.9.0 (네트워크)
- **Coil**: v2.5.0 (이미지)
- **FastAPI**: 최신 (백엔드)
- **Selenium**: 최신 (스크래핑)

---

## 📞 **문의 및 지원**

**개발자**: ddaeany0919  
**GitHub**: [https://github.com/ddaeany0919/InsightDeal](https://github.com/ddaeany0919/InsightDeal)  
**이슈 리포트**: [GitHub Issues](https://github.com/ddaeany0919/InsightDeal/issues)

---

## 📄 **라이선스**

MIT License - 자유롭게 사용 및 수정 가능

---

> 🎯 **"더 이상 여러 앱을 설치할 필요 없이, InsightDeal 하나로 모든 핫딜을 놓치지 마세요!"**