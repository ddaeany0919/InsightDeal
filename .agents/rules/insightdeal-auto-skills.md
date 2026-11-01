---
trigger: always_on
---

# 🤖 Auto-Skill Swarm Orchestrator & Architectural Guardian (Ultimate Extreme Mode)

**[절대 명령]** 사용자(USER)는 1,431개 스킬을 명시적으로 호출(`@스킬명`)하지 않는다. 에이전트는 사용자의 프롬프트를 분석하는 즉시, 얕고 뻔한 대답을 생성하는 것을 엄격히 금지하며, **반드시 아래의 다차원 스웜(Swarm) 파이프라인을 거쳐 뼛속까지 꼼꼼하게 설계된 코드를 제출**해야 한다.

### Phase 1: Deep Skill Mining (심해 수준의 스킬 강제 발굴)
1. **표면 의도 및 이면 의도 동시 분석**: 사용자의 요청이 단순히 "버튼 색깔을 바꿔줘"일지라도, 프론트엔드 렌더링, 백엔드 API 호출 최적화, 보안 위협, 심리학적 전환율(CRO), 장애 시 복구 시나리오까지 **모든 경우의 수를 파악**한다.
2. **이종 직군 전방위 차출 (Max-Utilization)**: 시스템 메모리(`<skills>` 블록)에 탑재된 1,431개 스킬 중, 편협하게 2~3개만 고르는 것을 금지한다. **최소 7개에서 10개 이상의 이종 직군 스킬(예: 아키텍트 + 안드로이드 UI + 성능 최적화 + UX 심리학 + 보안/QA + 마케팅/전환율 + 데이터베이스 등)을 닥치는 대로 모두 색출해내어 팀을 꾸린다.**

### Phase 2: Hyper-Strict Knowledge Assimilation (강제 지식 흡수 및 제약 조건 캐싱)
1. **`view_file` 강제 정독**: 코드를 단 한 줄이라도 짜기 전에, 선택된 10여 개 스킬들의 `SKILL.md`를 **전부 샅샅이 읽고** 각 스킬이 요구하는 제약 조건(Constraints)과 금지 사항을 머릿속에 완벽히 캐싱(Caching)한다.
2. **절대 위반 불가 체크리스트 생성**: 스킬들을 읽고 얻어낸 아키텍처 규칙들(예: XML 사용 금지, ViewModel 내 Context 참조 금지, Coroutine Exception 방어 등)을 모아 내부적으로 '사전 검열 체크리스트'를 만든다.

### Phase 3: Multi-Dimensional Cross-Validation (다차원 교차 검증 및 코드 생성)
1. **스킬 간 치열한 핑퐁 (Debate & Tuning)**: 발동된 스킬들 간에 상충하는 요소가 없는지 교차 검증한다. (예: `@frontend-design`이 화려한 애니메이션을 요구할 때, `@performance-engineer`가 메모리 릭을 경고하면, `@android-jetpack-compose-expert`가 최적의 Recomposition 방어 코드를 짜내는 식의 융합)
2. **Top-Tier 프로덕션 퀄리티 강제**: 예외 처리(Try-Catch), 엣지 케이스 방어, Null 안정성, 비동기(Flow/Coroutine) 생명주기 관리, 화려하지만 버벅임 없는 UI 트랜지션까지 **모든 스킬의 요구사항이 코드의 각 줄에 스며들도록** 극도로 꼼꼼하게 작성한다. 교과서적인 MVP 코드는 제출을 거부당할 것이다.

### Phase 4: End-to-End Sequence & Functional Wiring Gate (`android-e2e-sequence-audit` 필수 연동)
1. **구글 공식 Android Skills 33종 + `android-e2e-sequence-audit` 동시 가동**: Google 공식 스킬이 프레임워크·성능·보안을 검증한다면, `.agents/skills/android-e2e-sequence-audit/SKILL.md`를 통해 **비즈니스 시퀀스와 데이터 흐름의 끝단 연결**을 반드시 함께 검증한다.
2. **껍데기 콜백(Stub/TODO) 제로화**: UI 버튼(`onClick`, `onAlertClick`, `onShare` 등)에 `/* Todo */`, `/* MVP */`, 빈 람다(`{ _ -> }`)가 남아있는지 전수 확인하고, `UI → ViewModel → Room/Retrofit → FastAPI → DB/Push`까지 실제 데이터가 흐르도록 완결한다.
3. **네트워크 Waterfall 차단 및 Read/Write 파이프라인 대칭 검증**: 클라이언트가 불필요하게 2회 연속 API를 호출하는 Waterfall 시퀀스를 단일 왕복(1-RTT)으로 압축하고, 조회(`community.py`)와 수집/알림(`aggregator_service.py` / `push_worker.py`) 양쪽 시퀀스의 정합성을 실제 DB 데이터로 검증한다.
