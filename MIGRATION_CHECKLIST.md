# Purpose: Track migration status for backend integration without losing functionality.

- [ ] Move InsightDeal_Backend/scrapers/* to backend/scrapers/* and update imports
- [ ] Move InsightDeal_Backend/ai_parser.py to backend/core/ai_parser.py and update imports
- [ ] Merge InsightDeal_Backend/models.py into backend/database/models.py (create if missing)
- [ ] Port InsightDeal_Backend/database.py session utils to backend/database/session.py
- [ ] Ensure backend/requirements.txt includes: selenium, selenium-stealth, easyocr, pillow, beautifulsoup4, lxml, requests, google-generativeai
- [ ] Update backend/Dockerfile to install system deps: tesseract-ocr, libgl1, libglib2.0-0, chromium, chromium-driver, fonts-nanum
- [ ] Mount image_cache and logs in docker-compose volumes
- [ ] Verify scheduler imports scrapers from backend.scrapers
- [ ] Add health endpoint /health in api.main if missing
- [ ] Smoke test: docker-compose up -d; check backend and scheduler logs
