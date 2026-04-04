# 📱 InsightDeal Architecture Rule

1. **기술 스택 강제**
   - UI는 오직 **Jetpack Compose(Material 3)**만 사용한다. 기존 XML 방식(Layout 파일)은 폐기 및 사용 금지.
   - 비동기 처리는 `Coroutines`와 `Flow(StateFlow)`를 사용한다. 절대 Callback 패턴을 사용하지 말 것.

2. **아키텍처 (MVVM)**
   - 철저한 안드로이드 MVVM 패턴을 준수한다. 
   - `ViewModel` 안에는 절대 안드로이드 Context나 UI 로직(ex: View, Color) 인스턴스가 들어가선 안 된다.

3. **성능 이슈 최적화**
   - 위시리스트 관리 특성상 100개 이상의 아이템이 리스트에 뜰 수 있다. 리스트 구현 시 무조건 `LazyColumn`과 `Paging3`를 사용해 OOM(메모리 부족)을 방어한다.
   - 다중 선택 / 대량 편집 시 병목이 발생하지 않도록 Dispatchers.IO에서 병렬 처리한다.

4. **결과물 출력 시 요원(Agent) 주의사항**
   - 코드를 제안할 때는 파일을 토막 내지 말고, 가급적 **전체 코드 컨텍스트를 하나로 묶어서** (Imports 포함) 완성된 형태로 제안하여 사용자가 복사&붙여넣기 하기 편하게 제공한다.
   - 불필요한 서론은 줄이고, 핵심 로직에 집중한다.
