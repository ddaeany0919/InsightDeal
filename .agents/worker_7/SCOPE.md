# Scope: Scraper Integration Test Fix

## Architecture
- backend/tests/run_scraper_test.py가 주 진입점.
- db에서 Community 엔티티를 조회하여 community.id를 획득.
- 각 커뮤니티 스크래퍼(ClienScraper, QuasarzoneScraper, RuliwebScraper) 클래스를 생성할 때 community_id=community.id 형태로 정수 ID를 전달.
- scraper.run(url)를 비동기로 호출하고 수집 완료.

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | 탐색 및 문제 확인 | backend/tests/run_scraper_test.py 및 스크래퍼 코드 분석 | none | DONE |
| 2 | run_scraper_test.py 수정 | run_test() 비동기화 및 생성자/메서드 호출 시그니처 수정 | M1 | DONE |
| 3 | 개별 테스트 수정 | test_clien.py, test_quasarzone.py, test_ruliweb.py 수정 | M2 | DONE |
| 4 | 통합 테스트 실행 | run_scraper_test.py 실행 및 출력 검증 | M3 | DONE |
