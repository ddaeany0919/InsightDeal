---
name: "android-e2e-sequence-audit"
description: "End-to-end functional sequence, callback wiring, network waterfall elimination, and Read/Write pipeline symmetry verification skill that complements Google's 33 official Android SDK skills."
---

# Android End-to-End Sequence & Functional Wiring Audit Skill

Google's 33 official Android skills (`jetpack-compose-performance`, `anr-audit-and-fix`, `r8-analyzer`, `edge-to-edge`, etc.) enforce Android OS/SDK engineering standards. This skill enforces **application-level end-to-end execution sequences** so that UI actions, ViewModels, APIs, Database queries, and Background Push Workers operate as a single unbroken chain.

## Mandatory Audit Gates

### Gate 1: Zero-Stub & Callback Wiring Verification (UI → ViewModel → Repository → API → DB)
- Never leave placeholder lambdas (`{ _ -> /* MVP */ }`, `{ /* Todo */ }`, `{}`) on interactive UI components (`Button`, `IconButton`, `FilterChip`, `AlertDialog`).
- For every user-facing action on a screen (Bookmark, Target Price Alert, Share, Vote, Comment, External Link, Period Chip, Search Filter):
  1. Trace the callback from the `@Composable` parameter up to the `Route` / `Screen` entry point.
  2. Verify the `ViewModel` function is invoked and updates `StateFlow` / Room DB / Retrofit API.
  3. Verify persisted state is reflected back into the UI upon re-entering the screen (e.g., previously configured target price or bookmark state).

### Gate 2: Single-RTT Network Sequence (No Sequential Waterfall Calls)
- When a screen loads multiple resources (`deal`, `history`, `comments`, `votes`), launch independent requests in parallel using `coroutineScope { async { ... } }`.
- Eliminate client-side sequential fallback waterfalls (e.g., calling `period=7d`, waiting for `<= 1` result, and making a second HTTP call for `period=3m`). Handle sparse-window expansion inside the backend endpoint in a single HTTP round-trip or pass the effective period directly.

### Gate 3: Read / Write Pipeline Symmetry (Scraper Upsert ↔ API Query ↔ Push Notification)
- Whenever matching, clustering (`build_dsu`), or price history (`get_deal_history`) rules are modified on the Read path (`community.py`), audit the Write path (`aggregator_service.py` and `push_worker.py`) for symmetry:
  1. **Price History Recording on Upsert**: When a rolling-window duplicate/cross-community deal is merged into an existing deal and its price differs (`price != old_price`), record the price point in `PriceHistory` so intra-day/multi-community price movements are preserved.
  2. **Target Price Notification Trigger on Price Drop**: When an existing deal's price drops (`price < old_price`) during Upsert, ensure target-price / wishlist push notification checks are not skipped.

### Gate 4: Real-Database Edge-Case Verification
- Before concluding any matching, search, or history task, execute a verification pass against actual database records to confirm:
  1. Zero false merges across products that only share unit/quantity/generic tokens (`1kg`, `120g`, `5개`, `1.5L`, `국내산`).
  2. True manufacturer model code matches (`WIZ040`, `LS32FM701`, etc.) succeed across communities even when marketing modifiers differ.
  3. Timezone normalization (UTC `15:00:00` vs KST) never creates duplicate flat-line points on single-post deals.
