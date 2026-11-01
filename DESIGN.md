# 🎨 InsightDeal Design System (`DESIGN.md`)
> **Hybrid Reference Architecture**: **Toss (`getdesign.kr/toss`)** + **Karrot SEED Design (`getdesign.kr/seed-design`)**
> **Version**: 2.0 (Toss Clarity & Hierarchy × Karrot Warm Commerce Signals)

---

## 1. Visual Theme & Brand Philosophy

InsightDeal은 9개 커뮤니티(뽐뿌, 에펨코리아, 퀘이사존, 루리웹, 클리앙, 빠삭 등)의 실시간 핫딜과 다중 옵션 최저가를 1초 만에 비교·판단하는 **초고밀도 쇼핑 인텔리전스 플랫폼**입니다.

1. **Toss의 숫자·위계 명료성 (Financial Clarity)**
   - 배경 캔버스는 시원한 쿨그레이(`#F2F4F6`)로 깔고, 개별 딜 카드는 순백색(`#FFFFFF`)의 둥근 보더리스 카드(`16px~20px`)로 띄워 무거운 그림자 없이도 완벽한 정보 분리감을 제공합니다.
   - 가격은 `tabular-nums`(고정폭 숫자)와 강한 볼드 웨이트(`800~900`), 타이트한 자간(`-0.02em`)을 적용해 다중 옵션 가격표를 한눈에 스캔할 수 있게 합니다.
2. **Karrot (SEED Design)의 따뜻한 커머스 시그널 (Warm Local Commerce)**
   - 공격적인 원색 빨강 대신 당근마켓 SEED Design의 시그니처 웜 오렌지(`#FF6F0F`)를 **초특가/역대최저가/인기 딜 하이라이트 포인트**로 사용하여 구매 전환율과 시각적 친밀도를 동시에 높입니다.
   - 커뮤니티 출처 뱃지와 카테고리 칩은 부드러운 파스텔 서피스(`#F3F4F6` / `#EFF6FF` / `#FFF3E8`)와 필(Pill, `9999px`) 또는 소프트 라운드(`8px`) 형태로 정돈합니다.

---

## 2. Color Palette & Semantic Tokens

### Light Mode (Default Consumer Commerce Canvas)
| Role | Token Name | Hex / Value | Usage |
| :--- | :--- | :--- | :--- |
| **Background Canvas** | `--bg-color` / `BackgroundLight` | `#F2F4F6` | 전체 페이지 배경 (Toss Cool Gray Canvas) |
| **Elevated Surface** | `--card-bg` / `SurfaceLight` | `#FFFFFF` | 핫딜 카드, 모달, 드로어, 검색바 표면 |
| **Subtle Surface** | `--surface-subtle` | `#F9FAFB` | 다중 옵션 리스트 박스, 본문 요약 컨테이너 |
| **Primary Brand** | `--accent` / `TossBlue` | `#3182F6` | 주요 CTA 버튼, 활성 탭, 링크, 목표가 알림 포인트 |
| **Primary Hover** | `--accent-hover` | `#1B64DA` | Primary 버튼 호버/프레스 상태 |
| **Hot Deal Accent** | `--deal-orange` / `KarrotOrange` | `#FF6F0F` | 핫딜 가격 강조, 초특가 뱃지, 인기 불꽃 아이콘 |
| **Hot Deal Tint** | `--deal-orange-bg` | `#FFF3E8` | 초특가/다중옵션 뱃지 배경 |
| **Primary Ink** | `--text-main` / `TextPrimaryLight` | `#191F28` | 상품 제목, 가격, 핵심 본문 타이틀 (Toss Charcoal) |
| **Secondary Ink** | `--text-sub` / `TextSecondaryLight` | `#4E5968` | 옵션명, 쇼핑몰명, 본문 요약 텍스트 |
| **Muted Ink** | `--text-muted` | `#8B95A1` | 등록 시간, 조회수, 배송비 부가 정보 |
| **Hairline Border** | `--card-border` / `OutlineLight` | `#E5E8EB` | 카드 외곽선, 구분선 (`1px solid`) |
| **Positive / Lowest** | `--status-success` | `#00C471` | 역대 최저가 달성, 무료배송 강조 |
| **Danger / Urgent** | `--status-danger` | `#F04452` | 품절/종료 임박 경고 |

### Dark Mode (Nighttime Deal Hunting Mode)
| Role | Token Name | Hex / Value | Usage |
| :--- | :--- | :--- | :--- |
| **Background Canvas** | `--bg-color` / `BackgroundDark` | `#101318` | 다크 모드 메인 배경 (Toss Dark Slate) |
| **Elevated Surface** | `--card-bg` / `SurfaceDark` | `#191F28` | 다크 모드 딜 카드 및 모달 표면 |
| **Subtle Surface** | `--surface-subtle` | `#222934` | 다중 옵션 리스트 박스, 태그 배경 |
| **Primary Brand** | `--accent` | `#4593FC` | 다크 모드 가독성 보정 토스 블루 |
| **Hot Deal Accent** | `--deal-orange` | `#FF8433` | 다크 모드 당근 웜 오렌지 가격/뱃지 |
| **Primary Ink** | `--text-main` | `#F9FAFB` | 다크 모드 상품 제목 |
| **Secondary Ink** | `--text-sub` | `#B0B8C1` | 다크 모드 보조 텍스트 |
| **Muted Ink** | `--text-muted` | `#6B7684` | 다크 모드 시간/메타데이터 |
| **Hairline Border** | `--card-border` | `#2B333E` | 다크 모드 카드 외곽선 |

---

## 3. Typography & Scannable Hierarchy

- **Font Stack**: `'Pretendard Variable', 'Pretendard', -apple-system, BlinkMacSystemFont, system-ui, Roboto, sans-serif`
- **Numeric Discipline**: 모든 가격(`deal-price`), 할인율, 옵션 가격표, 시세 차트 축에는 반드시 `font-variant-numeric: tabular-nums`를 적용합니다.

| Element | Size (Web / Android) | Weight | Line Height | Letter Spacing |
| :--- | :--- | :--- | :--- | :--- |
| **Hero / Section Title** | `28px~36px` / `22sp` | `800 (ExtraBold)` | `1.25` | `-0.03em` |
| **Deal Card Title** | `16px` / `16sp` | `700 (Bold)` | `1.42` | `-0.02em` |
| **Deal Price (Primary)** | `20px` / `19sp` | `800 (ExtraBold)` | `1.20` | `-0.02em` |
| **Multi-Option Row** | `13.5px` / `13sp` | `600 (SemiBold)` | `1.40` | `-0.01em` |
| **Meta / Community Chip** | `12px` / `11.5sp` | `700 (Bold)` | `1.30` | `-0.01em` |

---

## 4. Component Specifications & 3-Core Detail Rule

### A. Hot Deal Feed Card (Web & Android)
- **Card Container**: `border-radius: 20px`, `background: var(--card-bg)`, `border: 1px solid var(--card-border)`, 소프트 엘리베이션(`0 4px 20px rgba(25, 31, 40, 0.04)`).
- **Thumbnail Framing**: `1:1` 또는 `16:10` 라운드 프레임(`14px`), 배경은 `#F9FAFB`로 처리해 투명/흰색 배경 상품 이미지가 깔끔하게 돋보이도록 구성.
- **Metadata Row**: `[커뮤니티 뱃지]` + `[쇼핑몰 뱃지]` + `[상대 시간]`을 한 줄에 정돈.
- **Price & Option Summary**:
  - 단일 상품: 웜 오렌지(`#FF6F0F`) 또는 토스 차콜(`#191F28`)로 가격과 배송비를 명확히 분리.
  - 다중 옵션 상품(`options_data` 2개 이상): `"총 N개 옵션 최저가"` 뱃지와 상위 옵션 요약 리스트를 깔끔한 서브 서피스(`#F9FAFB`) 박스로 노출.

### B. Deal Detail View (3-Core Skeleton Rule 엄수)
상세 보기 화면(웹 모달 & 안드로이드 상세 화면)은 불필요한 외부 쇼핑몰 실시간 가격비교 위젯을 배제하고 오직 **3대 핵심 뼈대**에 집중합니다:
1. **핫딜 상세 본문 내용 (`content_html` / `contentHtml`)** + 다중 옵션 개별 선택 리스트
2. **원본 커뮤니티 게시글 바로가기 링크 버튼** (서브 아웃라인/소프트 버튼)
3. **구매처 다이렉트 링크 아웃 버튼 (`ecommerce_link` / `ecommerceLink`)** (토스 블루 `#3182F6` 풀폭 프라이머리 CTA 버튼)
