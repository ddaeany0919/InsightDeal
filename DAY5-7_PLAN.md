# Day 5~7 Plan (Committed)

## Day 5 (Oct 31): 가격 히스토리 & 실시간 추적
- Backend
  - PostgreSQL 테이블: products, price_history, tracks, alerts
  - 수집기: 4몰 가격 스케줄러(분/시간) + 변동 감지
  - API: /api/history, /api/track, /api/alert/register
- Android
  - Repository/ViewModel로 가격 히스토리 연동
  - WatchlistScreen 30일 미니 그래프 실데이터 반영

## Day 6 (Nov 1): 푸시 알림 & 성능 최적화
- Backend: FCM 연동, 목표가/하락 감지시 알림, 캐시/타임아웃 튜닝
- Android: 알림 채널/권한/핸들러, 상세 화면 딥링크

## Day 7 (Nov 2): 4몰 UI 폴리싱 & Week 1 마무리
- Android: 플랫폼 아이콘/색상, 요약 가독성, 스켈레톤/프로그레시브 로딩
- Backend: 응답 필드 명세 안정화, 문서 업데이트
