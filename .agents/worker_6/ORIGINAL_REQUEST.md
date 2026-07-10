# Original User Request

## Initial Request — 2026-07-10T19:46:09+09:00

당신은 InsightDeal 프로젝트의 개발자(worker_6)입니다.
작업 디렉토리: C:\Users\kth00\StudioProjects\InsightDeal\.agents\worker_6
귀하의 임무는 활성 스크래퍼들의 통합 테스트를 수행하고, SQLite DB(`insight_deal.db`) 데이터 적재 무결성을 확인하며, 백엔드 API 서버를 8080 포트로 구동하여 검증하는 것입니다.

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A Forensic Auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

작업 지침:
1. `backend/scrapers` 및 `backend/main.py` 크롤러 파이프라인 상의 모든 스크래핑 스크립트(뽐뿌, 펨코, 루리웹, 퀘이사존, 클리앙 등)를 순차적으로 구동하여 최신 핫딜 페이지를 수집하는지 검증하십시오.
2. 수집된 데이터가 로컬 SQLite 데이터베이스(`insight_deal.db`)에 누락 없이 무결하게 적재되는지 확인하십시오. 중복 적재 방지 제약이 데이터베이스 수준에서 정상적으로 동작하는지 교차 검토하고 에러 발생 시 디버깅을 수행하십시오.
3. 백엔드 FastAPI 서버를 `8080` 포트로 기동하여, `GET /api/community` API 호출 시 HTTP 200 응답과 함께 유효한 핫딜 데이터 JSON 배열이 정상 수신되는지 검증하십시오.
4. 각 스크래퍼 구동 로그, 데이터 적재 개수, API 응답 결과 등 검증된 사실들을 C:\Users\kth00\StudioProjects\InsightDeal\.agents\worker_6\handoff.md 에 상세히 작성하고 parent에게 완료 메시지를 보내십시오.
