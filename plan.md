# 작업 계획

## 🎯 목표
멀티 위시리스트 다중 관리 및 실시간 최저가 추적을 지원하는 고도화된 트렌디한 안드로이드(Compose) UI/UX 구축 및 Firebase 동기화 연동 완료

## ✅ 완료 기준
- [ ] 100개 이상의 관심 상품이 버벅임 없이 LazyColumn과 Paging3로 렌더링될 것.
- [ ] 여러 아이템을 한 번에 선택하여 일괄 가격 체크 및 일괄 삭제가 가능할 것.
- [ ] 아이템이 추가/삭제/갱신될 때 쫀득한 Compose 기반 애니메이션이 재생될 것.
- [ ] WorkManager를 이용해 백그라운드 환경에서 매일 1회 가격 체크가 자동으로 수행될 것.
- [ ] Firebase와 연동되어 기기가 변경되어도 데이터가 소실되지 않고 실시간 동기화될 것.

## 🏃 단계
1. **[UI/UX 최적화]** Compose `AnimatedVisibility`를 이용한 카드 등장/삭제 애니메이션 적용 및 Lottie 기반 빈 화면 구현.
2. **[비즈니스 로직 작성]** 상단 TopBar [모두 체크] 버튼 구현 및 ViewModel 내 `checkAllPrices()` 병렬 처리 코루틴 로직 개발.
3. **[백그라운드 통신]** 안드로이드 `WorkManager` 스케줄 서비스 부착 및 병목 현상(Rate Limit) 방어 로직 설계.
4. **[서버 연동]** Firebase 백업/복구 시스템 및 실시간 데이터 Sync 적용.

## 📚 참고 자료
- `README.md` (InsightDeal 코어 아키텍처 및 상세 기획)
- `PROJECT_STATUS.md` (현재까지 구현된 앱 구조 상태 트래킹)
