import asyncio
import httpx
import logging
import os
import random
from abc import ABC, abstractmethod
from typing import List, Optional

logger = logging.getLogger(__name__)

from curl_cffi.requests import AsyncSession

# 클라우드 실행 환경 감지 (GitHub Actions, Cloud Run, Docker 서버 등)
IS_CLOUD_ENV = bool(
    os.environ.get("GITHUB_ACTIONS") == "true"
    or os.environ.get("IS_CLOUD") == "true"
    or os.environ.get("RUN_IN_CLOUD") == "true"
)

# 에펨코리아 WebAssembly(fm5/fm6/lite_year) 보안 챌린지 통과 쿠키 전역 캐시
_FMKOREA_WASM_COOKIES: dict[str, str] = {}

class AsyncBaseScraper(ABC):
    """
    🏗️ [비동기 v2.0] 통합 스크래퍼 기본 클래스
    - curl_cffi 기반 완벽한 브라우저(Chrome) 지문 위장 (TLS Fingerprint)
    - Semaphore 기반 IP 차단 방지 (동시성 제한)
    """

    def __init__(self, platform_name: str, max_concurrent_requests: int = 5):
        self.platform_name = platform_name
        self.semaphore = asyncio.Semaphore(max_concurrent_requests)
        self.client: Optional[AsyncSession] = None
        self.max_retries = 3

    async def __aenter__(self):
        # chrome124 위장을 통해 Cloudflare 430/403 우회 (펨코 Wasm 쿠키 캐시 자동 주입)
        init_cookies = _FMKOREA_WASM_COOKIES.copy() if _FMKOREA_WASM_COOKIES else None
        self.client = AsyncSession(impersonate='chrome124', timeout=20.0, cookies=init_cookies)
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb):
        if self.client:
            await self.client.close()

    def _get_headers(self) -> dict:
        """User-Agent는 curl_cffi가 자동으로 처리하므로 기본 헤더만 추가"""
        return {
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
            "Accept-Language": "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7",
        }

    async def fetch_html(self, url: str, headers: Optional[dict] = None) -> Optional[str]:
        """비동기 네트워크 페칭 (동적 브라우저 지문 로테이션, 서울 릴레이 우회 및 재시도 적용)"""
        if not self.client:
            raise RuntimeError("Scraper must be used within 'async with' context")

        # 1. 클라우드 환경에서 빠삭(bbasak.com), 뽐뿌(ppomppu.co.kr), 클리앙(clien.net) 등 해외 IP 차단 사이트는 지문 로테이션을 건너뛰고 0초 만에 Vercel 서울 릴레이 직행
        if IS_CLOUD_ENV and ("bbasak.com" in url or "ppomppu.co.kr" in url or "clien.net" in url):
            relay_html = await self._fetch_via_seoul_relay(url)
            if relay_html:
                return relay_html

        # 2. 클라우드 환경에서는 불필요한 4회 지문 로테이션을 1회('chrome124')로 축소하고 타임아웃을 5.0초로 타이트하게 설정
        impersonates = ['chrome124'] if IS_CLOUD_ENV else ['chrome124', 'safari17_0', 'edge101', 'chrome120']
        req_headers = headers if headers is not None else self._get_headers()
        
        async with self.semaphore:
            for attempt, imp in enumerate(impersonates):
                try:
                    if not IS_CLOUD_ENV:
                        if "fmkorea" in url:
                            await asyncio.sleep(random.uniform(2.0, 3.2))
                        elif "quasarzone" in url:
                            await asyncio.sleep(random.uniform(0.35, 0.7))
                        else:
                            await asyncio.sleep(random.uniform(0.12, 0.3))
                    else:
                        if "fmkorea" in url:
                            await asyncio.sleep(random.uniform(0.5, 1.0))
                        else:
                            await asyncio.sleep(0.05)
                    
                    if attempt > 0:
                        logger.info(f"🔄 [{self.platform_name}] 지문 로테이션 재시도 ({imp}) - {url}")
                        await self.client.close()
                        self.client = AsyncSession(impersonate=imp, timeout=15.0)
                        
                        if 'chrome' not in imp:
                            req_headers = req_headers.copy()
                            req_headers.pop("Sec-Ch-Ua", None)
                            req_headers.pop("Sec-Ch-Ua-Mobile", None)
                            req_headers.pop("Sec-Ch-Ua-Platform", None)
                            
                    req_timeout = 5.0 if IS_CLOUD_ENV else (20.0 if attempt == 0 else 15.0)
                    response = await self.client.get(url, headers=req_headers, timeout=req_timeout)
                    
                    if response.status_code == 404:
                        logger.warning(f"[{self.platform_name}] 삭제된 게시글 감지 (HTTP 404) - 재시도 중단: {url}")
                        return None

                    if response.status_code == 430:
                        if "fmkorea" in url or "quasarzone" in url:
                            pw_html = await self._solve_playwright_stealth(url)
                            if pw_html:
                                return pw_html
                        logger.warning(f"[{self.platform_name}] 일시적 IP 쿨다운 감지 (HTTP 430) - 서울 릴레이 우회 시도: {url}")
                        relay_html = await self._fetch_via_seoul_relay(url)
                        if relay_html:
                            return relay_html
                        logger.info(f"⏳ [{self.platform_name}] 현재 IP 쿨다운(HTTP 430) 상태입니다. 기존 수집 데이터는 유지되며 다음 주기에 자동 재시도합니다.")
                        return None

                    if response.status_code == 403:
                        logger.warning(f"[{self.platform_name}] 안티봇 차단 감지 (HTTP 403) [시도 {attempt+1}/{len(impersonates)}] - {url}")
                        if "quasarzone" in url or "fmkorea" in url:
                            pw_html = await self._solve_playwright_stealth(url)
                            if pw_html:
                                return pw_html
                        if IS_CLOUD_ENV or attempt == len(impersonates) - 1:
                            relay_html = await self._fetch_via_seoul_relay(url)
                            if relay_html:
                                return relay_html
                            if IS_CLOUD_ENV:
                                return None
                            raise Exception(f"Anti-bot block detected after all retries: {response.status_code}")
                        await asyncio.sleep(1.5 * (attempt + 1))
                        continue
                        
                    response.raise_for_status()
                    return response.text
                except Exception as e:
                    if "quasarzone" in url or "fmkorea" in url:
                        logger.info(f"🛡️ [{self.platform_name}] 직접 요청 오류({e}) -> Playwright Stealth 우회 시도: {url}")
                        pw_html = await self._solve_playwright_stealth(url)
                        if pw_html:
                            return pw_html

                    if IS_CLOUD_ENV:
                        logger.warning(f"⚠️ [{self.platform_name}] 클라우드 직접 요청 실패 ({e}) -> 즉시 서울 릴레이 폴백: {url}")
                        relay_html = await self._fetch_via_seoul_relay(url)
                        if relay_html:
                            return relay_html
                        return None

                    if attempt == len(impersonates) - 1:
                        relay_html = await self._fetch_via_seoul_relay(url)
                        if relay_html:
                            return relay_html
                        raise e
                    logger.warning(f"⚠️ [{self.platform_name}] 요청 오류 (시도 {attempt+1}): {e}. 다음 지문으로 재시도합니다.")
                    await asyncio.sleep(1.5 * (attempt + 1))
            return None

    async def _solve_fmkorea_430_via_playwright(self, url: str) -> Optional[str]:
        """하위 호환용 래퍼: _solve_playwright_stealth로 위임"""
        return await self._solve_playwright_stealth(url)

    async def _solve_playwright_stealth(self, url: str) -> Optional[str]:
        """
        🛡️ Playwright Headless Chromium에 Stealth 플래그 및 init_script를 주입하여
        Cloudflare 403(퀘이사존) 및 Wasm 430(에펨코리아)을 우회하고 쿠키/HTML을 획득합니다.
        """
        if os.environ.get("SKIP_PLAYWRIGHT") == "true":
            logger.info(f"⚡ [{self.platform_name}] SKIP_PLAYWRIGHT 활성화 -> 무거운 브라우저 구동 생략 후 경량 브릿지/릴레이 폴백")
            return None
        try:
            logger.info(f"🛡️ [{self.platform_name}] Playwright Chromium Stealth 우회 가동: {url}")

            def _sync_stealth_fetch(target_url: str):
                import sys
                if sys.platform == "win32":
                    asyncio.set_event_loop_policy(asyncio.WindowsProactorEventLoopPolicy())
                from playwright.sync_api import sync_playwright
                with sync_playwright() as p:
                    # X11 가상 디스플레이(xvfb) 또는 헤드리스 환경 최적화
                    is_linux_display = bool(os.environ.get("DISPLAY"))
                    use_headless = not is_linux_display

                    browser = p.chromium.launch(
                        headless=use_headless,
                        args=[
                            '--no-sandbox',
                            '--disable-setuid-sandbox',
                            '--disable-dev-shm-usage',
                            '--disable-blink-features=AutomationControlled',
                            '--disable-infobars',
                            '--window-size=1920,1080',
                        ]
                    )
                    context = browser.new_context(
                        user_agent="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                        locale="ko-KR",
                        viewport={"width": 1920, "height": 1080},
                    )
                    page = context.new_page()
                    page.add_init_script("""
                        delete Object.getPrototypeOf(navigator).webdriver;
                        window.chrome = { runtime: {} };
                        Object.defineProperty(navigator, 'languages', { get: () => ['ko-KR', 'ko', 'en-US', 'en'] });
                        Object.defineProperty(navigator, 'plugins', { get: () => [1, 2, 3, 4, 5] });
                    """)

                    response = page.goto(target_url, wait_until="domcontentloaded", timeout=20000)
                    status_code = response.status if response else 0

                    if "quasarzone.com" in target_url:
                        try:
                            page.wait_for_selector(".v2-list-row, .market-info-type-list, .market-info-view-table, .view-content", timeout=4000)
                        except Exception:
                            try:
                                cf_el = page.query_selector(".cf-turnstile, iframe[src*='challenges.cloudflare.com']")
                                if cf_el:
                                    box = cf_el.bounding_box()
                                    if box:
                                        page.mouse.click(box["x"] + 28, box["y"] + 32)
                                page.wait_for_selector(".v2-list-row, .market-info-type-list, .market-info-view-table, .view-content", timeout=4000)
                            except Exception:
                                pass
                    elif "fmkorea.com" in target_url:
                        # 430 후 브라우저 내 Wasm/Turnstile 자동 실행 및 체크박스 클릭 시도
                        try:
                            page.wait_for_selector("li.li, table.bd_lst, .rd_body, article, .hotdeal_info", timeout=3500)
                        except Exception:
                            try:
                                cf_el = page.query_selector(".cf-turnstile, iframe[src*='challenges.cloudflare.com']")
                                if cf_el:
                                    box = cf_el.bounding_box()
                                    if box:
                                        page.mouse.click(box["x"] + 28, box["y"] + 32)
                                page.wait_for_timeout(2500)
                                page.evaluate("if (window.doPost) window.doPost()")
                                page.wait_for_selector("li.li, table.bd_lst, .rd_body, article, .hotdeal_info", timeout=3500)
                            except Exception:
                                pass
                    else:
                        page.wait_for_timeout(2000)

                    content_str = page.content()
                    cookie_list = context.cookies()
                    browser.close()
                    return content_str, cookie_list, status_code

            html, cookies, status = await asyncio.to_thread(_sync_stealth_fetch, url)

            if cookies:
                for c in cookies:
                    if c.get("name") and c.get("value"):
                        _FMKOREA_WASM_COOKIES[c["name"]] = c["value"]
                if self.client:
                    try:
                        await self.client.close()
                        self.client = AsyncSession(
                            impersonate="chrome124",
                            timeout=20.0,
                            cookies=_FMKOREA_WASM_COOKIES.copy(),
                        )
                        logger.info(f"✅ [{self.platform_name}] Stealth 보안 쿠키 {len(_FMKOREA_WASM_COOKIES)}종 획득 및 세션 동기화 완료!")
                    except Exception:
                        pass

            # Cloudflare 차단 안내문 시그니처 검사 (403/Turnstile 방어 페이지 오인 원천 차단)
            cf_blocked_signals = ["Attention Required! | Cloudflare", "Just a moment...", "cf-browser-verification", "Ray ID:", "Cloudflare Ray ID"]
            is_cf_blocked = html and any(sig in html for sig in cf_blocked_signals)

            if "quasarzone.com" in url:
                has_qz_items = html and any(sel in html for sel in [".v2-list-row", "v2-list-row", "market-info-type-list", "tr.default_list", "market-info-view-table", "view-content"])
                if not is_cf_blocked and has_qz_items:
                    logger.info(f"✅ [{self.platform_name}] 퀘이사존 Stealth 우회 성공! (HTML: {len(html):,} bytes, initial_status: {status})")
                    return html
                else:
                    logger.warning(f"⚠️ [{self.platform_name}] 퀘이사존 응답이 차단 페이지이거나 유효하지 않음 (status={status}, cf_blocked={is_cf_blocked}, has_items={has_qz_items})")
                    return None
            elif "fmkorea.com" in url:
                is_fm_430 = html and ("에펨코리아 보안 시스템" in html or "adcForm" in html)
                has_fm_items = html and any(sel in html for sel in ["hotdeal_info", "bd_lst", "rd_body", "li.li"])
                if not is_cf_blocked and not is_fm_430 and (has_fm_items or len(html) > 50000):
                    logger.info(f"✅ [{self.platform_name}] 펨코 Stealth 우회 성공! (HTML: {len(html):,} bytes, initial_status: {status})")
                    return html
                else:
                    logger.warning(f"⚠️ [{self.platform_name}] 펨코 응답이 430/차단 페이지임 (status={status}, cf_blocked={is_cf_blocked}, has_items={has_fm_items})")
                    return None
            elif html and len(html) > 1000 and status == 200 and not is_cf_blocked:
                return html
        except Exception as pw_err:
            logger.warning(f"⚠️ [{self.platform_name}] Playwright Stealth 우회 실패 ({url}): {pw_err}")
        return None

    async def _fetch_via_seoul_relay(self, url: str) -> Optional[str]:
        """미국(GCP us-central1) 서버 IP 차단 시 Vercel 서울 리전(icn1) 릴레이를 통해 한국 커뮤니티 HTML 페칭"""
        try:
            import re
            from urllib.parse import quote
            relay_url = f"https://insightdeal.vercel.app/api/scraper-relay?url={quote(url, safe='')}"
            relay_timeout = 10.0 if IS_CLOUD_ENV else 15.0
            async with httpx.AsyncClient(timeout=relay_timeout, follow_redirects=True) as client:
                res = await client.get(relay_url)
                if res.status_code == 200 and len(res.text) > 500:
                    cf_blocked_signals = ["Attention Required! | Cloudflare", "Just a moment...", "cf-browser-verification", "Ray ID:", "Cloudflare Ray ID", "cf-turnstile"]
                    is_cf_blocked = any(sig in res.text for sig in cf_blocked_signals)
                    if not is_cf_blocked:
                        logger.info(f"🌐 [{self.platform_name}] 서울 리전 릴레이 우회 페칭 성공: {url}")
                        return res.text
                    else:
                        logger.warning(f"⚠️ [{self.platform_name}] 서울 리전 릴레이 응답에 Cloudflare 차단 시그니처 감지: {url}")
                if "ppomppu.co.kr/zboard/zboard.php" in url and "page=" not in url:
                    m_id = re.search(r"id=([a-zA-Z0-9_]+)", url)
                    board_id = m_id.group(1) if m_id else "ppomppu"
                    rss_target = f"https://www.ppomppu.co.kr/rss.php?id={board_id}"
                    rss_relay = f"https://insightdeal.vercel.app/api/scraper-relay?url={quote(rss_target, safe='')}"
                    rss_res = await client.get(rss_relay)
                    if rss_res.status_code == 200 and "<item>" in rss_res.text:
                        logger.info(f"🌐 [{self.platform_name}] 서울 리전 RSS 릴레이 우회 페칭 성공: {rss_target}")
                        return rss_res.text
        except Exception as re_err:
            logger.warning(f"⚠️ [{self.platform_name}] 서울 리전 릴레이 페칭 실패 ({url}): {re_err}")
        return None

    async def fetch_og_image(self, url: str = "", fallback_title: str = "") -> Optional[str]:
        """외부 쇼핑몰 URL의 og:image 메타 태그를 파싱하여 고화질 대표 이미지 획득 (차단/무링크 시 상품명 기반 검색 폴백 완비)"""
        import re
        from urllib.parse import urljoin, quote

        def _is_valid_product_img(candidate_url: str) -> bool:
            if not candidate_url or not candidate_url.startswith('http'):
                return False
            low = candidate_url.lower().split('?')[0]
            if low.endswith('.svg') or low.endswith('.ico'):
                return False
            bad_tokens = [
                'logo', 'icon', 'noimage', 'no_image', 'blank', 'transparent',
                'common/', 'btn_', 'button', 'share_', 'footer', 'header', 'avatar', 'lazyload',
                '11st_', 'gmarket_logo', 'auction_logo', 'naver_logo',
                'lookaside.fbsbx.com', 'facebook.com', 'instagram.com',
                'close.jpg', 'close.png', 'close.gif', 'tvcast', 'sstatic/search'
            ]
            return not any(tok in low for tok in bad_tokens)

        # 1. 지마켓(Gmarket) 안티봇 차단 원천 우회를 위한 초고속 CDN 이미지 조립 룰 적용
        if url and 'gmarket.co.kr' in url:
            match = re.search(r'goodscode=([0-9]+)', url)
            if match:
                g_id = match.group(1)
                return f"https://gdimg.gmarket.co.kr/{g_id}/still/600"

        # 2. 외부 쇼핑몰 직접 파싱 시도 (타임아웃 8.0초 확장 + 11번가 모바일 폴백)
        is_community_internal = any(
            x in (url or '') for x in ['ppomppu.co.kr', 'fmkorea.com', 'fmkorea.org', 'quasarzone.com', 'clien.net', 'ruliweb.com', 'bbasak.com']
        )
        if url and url.startswith('http') and not is_community_internal and self.client:
            candidate_urls = [url]
            st11_m = re.search(r'11st\.co\.kr/products/([0-9]+)', url)
            if st11_m:
                candidate_urls.append(f"https://m.11st.co.kr/products/m/{st11_m.group(1)}")

            for target_url in candidate_urls:
                try:
                    logger.info(f"🔍 [og:image] 외부 쇼핑몰 og:image 탐색 시도: {target_url}")
                    response = await self.client.get(target_url, timeout=8.0, allow_redirects=True, headers={
                        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
                        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                    })
                    if response.status_code == 200 and response.text:
                        from bs4 import BeautifulSoup
                        final_base_url = str(getattr(response, 'url', target_url) or target_url)
                        soup = BeautifulSoup(response.text, 'html.parser')
                        meta_img = (
                            soup.find('meta', property='og:image')
                            or soup.find('meta', attrs={'name': 'twitter:image'})
                            or soup.find('meta', itemprop='image')
                        )
                        if meta_img and meta_img.get('content'):
                            img_url = meta_img['content'].strip()
                            if img_url.startswith('//'):
                                img_url = 'https:' + img_url
                            elif not img_url.startswith('http'):
                                img_url = urljoin(final_base_url, img_url)
                            if _is_valid_product_img(img_url):
                                return img_url

                        for img in soup.select('img'):
                            raw_src = (img.get('data-original') or img.get('data-src') or img.get('src') or '').strip()
                            if not raw_src or raw_src.startswith('data:'):
                                continue
                            if raw_src.startswith('//'):
                                full_src = 'https:' + raw_src
                            elif not raw_src.startswith('http'):
                                full_src = urljoin(final_base_url, raw_src)
                            else:
                                full_src = raw_src
                            if _is_valid_product_img(full_src) and any(
                                k in full_src.lower() for k in ['product', 'goods', 'item', 'prd', 'upload', 'shop', 'image', 'img']
                            ):
                                return full_src
                except Exception as e:
                    logger.warning(f"⚠️ [og:image] 외부 쇼핑몰 이미지 파싱 실패 ({target_url}): {e}")

        # 3. 쇼핑몰 403/타임아웃(옥션 등) 또는 본문 내 사진/쇼핑몰 링크가 없는 텍스트 게시글인 경우:
        #    스크래핑 시점에 상품 제목(fallback_title)으로 실시간 상품 이미지를 즉시 확보하여 누락 제로화!
        #    (클라우드 GCP IP에서도 100% 동작하는 네이버 썸네일 CDN -> 다음 카카오 CDN -> 빙 이미지 3중 폴백)
        if fallback_title and fallback_title.strip():
            try:
                from urllib.parse import unquote
                clean_q = re.sub(r'^\[[^\]]+\]\s*', '', fallback_title.strip())
                clean_q = re.sub(r'\([^)]*(?:원|무료|무배|배송|쿠폰|할인|적립|최대|\$|달러|불|유로|€)[^)]*\)', ' ', clean_q)
                clean_q = re.sub(r'(?:[0-9,]+\s*원|\$\s*[0-9,.]+|[0-9,.]+\s*(?:만\s*원?|달러|불|유로|€))', ' ', clean_q)
                clean_q = re.sub(r'(?:무료배송|무배|배송비|특가|할인|핫딜|역대가|초특가)', ' ', clean_q)
                clean_q = re.sub(r'\s+', ' ', clean_q).strip()
                first_part = re.split(r'[,/]', clean_q)[0].strip()
                short_q = " ".join(first_part.split()[:3]).strip()
                query_candidates = [q for q in dict.fromkeys([clean_q, first_part, short_q]) if len(q) >= 2]
                if query_candidates:
                    req_hdrs = {
                        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                        "Accept-Language": "ko-KR,ko;q=0.9"
                    }
                    async with httpx.AsyncClient(timeout=6.0, follow_redirects=True) as hclient:
                        for q_cand in query_candidates:
                            # 3-A. 네이버 통합검색 썸네일 (search.pstatic.net 프록시 CDN, GCP 서울/미국 모두 0.2s 내 응답)
                            naver_url = f"https://search.naver.com/search.naver?where=nexearch&query={quote(q_cand)}"
                            rn = await hclient.get(naver_url, headers=req_hdrs)
                            if rn.status_code == 200:
                                n_imgs = re.findall(r'(https://search\.pstatic\.net/common/\?src=[^\s"\'&<>]+)', rn.text)
                                naver_bad = (
                                    'blogpfthumb', 'kinimage', 'musicmeta', 'profile', 'icon', 'logo',
                                    'blank', 'avatar', 'favicon', '파비콘', '.ico', 'influencer-phinf',
                                    'clip-service-phinf', 'pup-post-phinf', '96x96', '32x32', '64x64',
                                    '192x192', 'cropped-', 'Press', 'news',
                                    'blogfiles.naver', 'postfiles.pstatic', 'mblogthumb-phinf', 'cafeptthumb',
                                    'tvcast', 'sstatic/search', 'sstatic.net/sstatic', 'kin-phinf', 'dbscthumb-phinf', 'navertv', 'veta-phinf'
                                )
                                preferred_naver = []
                                fallback_naver = []
                                for cand in n_imgs[:25]:
                                    low_dec = unquote(unquote(cand)).lower()
                                    if any(b.lower() in low_dec for b in naver_bad):
                                        continue
                                    if any(p in low_dec for p in ('shopping-phinf', 'shop-phinf', 'dthumb-phinf')):
                                        preferred_naver.append(cand)
                                    else:
                                        fallback_naver.append(cand)
                                chosen_naver = (preferred_naver or fallback_naver)
                                if chosen_naver:
                                    logger.info(f"🖼️ [Scraper Image Fallback - Naver] 상품명('{q_cand}') 대표 이미지 확보 성공: {chosen_naver[0]}")
                                    return chosen_naver[0]

                            # 3-B. 다음 통합검색 썸네일 (kakaocdn.net argon 320x320 CDN, 희귀 직구/캔디류 폴백)
                            daum_url = f"https://search.daum.net/search?w=tot&q={quote(q_cand)}"
                            rd = await hclient.get(daum_url, headers=req_hdrs)
                            if rd.status_code == 200:
                                d_imgs = re.findall(r'(https://search\d+\.kakaocdn\.net/argon/[^\s"\'<>]+)', rd.text)
                                for cand in d_imgs[:6]:
                                    if '320x320' in cand or '656x0' in cand or d_imgs:
                                        chosen_d = cand if ('320x320' in cand or '656x0' in cand) else d_imgs[0]
                                        logger.info(f"🖼️ [Scraper Image Fallback - Daum] 상품명('{q_cand}') 대표 이미지 확보 성공: {chosen_d}")
                                        return chosen_d

                        # 3-C. 빙 이미지 검색 폴백
                        search_url = f"https://www.bing.com/images/search?q={quote(query_candidates[-1] + ' 상품')}&FORM=HDRSC2"
                        res = await hclient.get(search_url, headers=req_hdrs)
                        if res.status_code == 200:
                            murls = re.findall(r'murl&quot;:&quot;(https?://[^&]+?)&quot;', res.text)
                            for candidate in murls[:6]:
                                if _is_valid_product_img(candidate):
                                    logger.info(f"🖼️ [Scraper Image Fallback - Bing] 상품명('{clean_q}') 기반 대표 이미지 확보 성공: {candidate}")
                                    return candidate
            except Exception as fb_err:
                logger.warning(f"⚠️ [Scraper Image Fallback] 상품명 이미지 검색 실패 ({fallback_title}): {fb_err}")

        return None

    @abstractmethod
    async def parse_list(self, html: str) -> List[dict]:
        """리스트 페이지 파싱 (자식 구현)"""
        pass

    @abstractmethod
    async def get_detail(self, url: str) -> Optional[dict]:
        """상세 페이지 파싱 (자식 구현)"""
        pass

    async def run(self, url: str) -> List[dict]:
        """메인 실행 파이프라인"""
        logger.info(f"▶️ [{self.platform_name}] 핫딜 수집 시작: {url}")
        try:
            html = await self.fetch_html(url)
            if not html:
                return []
        except Exception as e:
            logger.error(f"❌ [{self.platform_name}] fetch_html 실패: {e}")
            return []
        
        try:
            deals = await self.parse_list(html)
            # Validate with Pydantic schema
            from backend.scrapers.schemas import ScrapedDeal
            validated_deals = []
            for d in deals:
                try:
                    # Remove empty strings to let Pydantic handle default values or None
                    cleaned = {k: v for k, v in d.items() if v != ""}
                    ScrapedDeal(**cleaned)
                    validated_deals.append(d)
                except Exception as e:
                    logger.warning(f"[{self.platform_name}] 스키마 검증 실패: {e} | 데이터: {d.get('title')}")
            
            logger.info(f"✅ [{self.platform_name}] 수집 완료: 총 {len(validated_deals)}건 (유효)")
            return validated_deals
        except Exception as e:
            logger.error(f"❌ [{self.platform_name}] 파싱 실패: {e}")
            return []

    @staticmethod
    def parse_time_str(time_str: str) -> Optional[str]:
        """다양한 형식의 시간 문자열을 UTC ISO 8601 형식(시간대 오프셋 제거)으로 변환"""
        import re
        from datetime import datetime, timedelta, timezone
        
        if not time_str: return None
        time_str = time_str.strip()
        kst = timezone(timedelta(hours=9))
        now_kst = datetime.now(kst)
        posted_dt_kst = None
        
        try:
            # 분 전, 시간 전, 방금 전 등의 상대 시간 처리
            if '분 전' in time_str or '분전' in time_str:
                match = re.search(r'(\d+)\s*분', time_str)
                if match:
                    posted_dt_kst = now_kst - timedelta(minutes=int(match.group(1)))
            elif '시간 전' in time_str or '시간전' in time_str:
                match = re.search(r'(\d+)\s*시간', time_str)
                if match:
                    posted_dt_kst = now_kst - timedelta(hours=int(match.group(1)))
            elif '방금' in time_str:
                posted_dt_kst = now_kst
            elif ':' in time_str and not ('/' in time_str or '-' in time_str or '.' in time_str):
                # 14:20 or 14:20:10
                parts = time_str.split(':')
                if len(parts) >= 2:
                    h, m = int(parts[0]), int(parts[1])
                    s = int(parts[2]) if len(parts) > 2 else 0
                    posted_dt_kst = now_kst.replace(hour=h, minute=m, second=s, microsecond=0)
                    # 만약 미래 시간이면 작년/어제로 간주
                    if posted_dt_kst > now_kst + timedelta(hours=1):
                        posted_dt_kst -= timedelta(days=1)
            else:
                # 2024.04.28 or 24/04/28 12:34:56 (세 개의 날짜 세그먼트)
                date_match_3 = re.search(r'(\d{2,4})[/.-](\d{1,2})[/.-](\d{1,2})(?:\s+(\d{1,2}):(\d{1,2})(?::(\d{1,2}))?)?', time_str)
                # 06.02 or 06/02 12:34:56 (두 개의 날짜 세그먼트, 연도 생략)
                date_match_2 = re.search(r'(?<!\d)(\d{1,2})[/.-](\d{1,2})(?:\s+(\d{1,2}):(\d{1,2})(?::(\d{1,2}))?)?', time_str)
                
                if date_match_3:
                    y_str = date_match_3.group(1)
                    y = int(y_str)
                    if y < 100: y += 2000
                    # 혹시 파싱 오류로 2012년 등이 되면 2024년으로 자동 교정 (안전망)
                    if y < 2020: y = now_kst.year
                    
                    month, d = int(date_match_3.group(2)), int(date_match_3.group(3))
                    h = int(date_match_3.group(4)) if date_match_3.group(4) else 0
                    m_val = int(date_match_3.group(5)) if date_match_3.group(5) else 0
                    s_val = int(date_match_3.group(6)) if date_match_3.group(6) else 0
                    
                    if 1 <= month <= 12 and 1 <= d <= 31:
                        try:
                            posted_dt_kst = datetime(y, month, d, h, m_val, s_val, tzinfo=kst)
                            if posted_dt_kst and posted_dt_kst > now_kst + timedelta(hours=1):
                                posted_dt_kst = posted_dt_kst.replace(year=posted_dt_kst.year - 1)
                        except ValueError:
                            pass
                elif date_match_2:
                    y = now_kst.year
                    month, d = int(date_match_2.group(1)), int(date_match_2.group(2))
                    h = int(date_match_2.group(3)) if date_match_2.group(3) else 0
                    m_val = int(date_match_2.group(4)) if date_match_2.group(4) else 0
                    s_val = int(date_match_2.group(5)) if date_match_2.group(5) else 0
                    
                    if 1 <= month <= 12 and 1 <= d <= 31:
                        try:
                            posted_dt_kst = datetime(y, month, d, h, m_val, s_val, tzinfo=kst)
                            if posted_dt_kst and posted_dt_kst > now_kst + timedelta(hours=1):
                                posted_dt_kst = posted_dt_kst.replace(year=posted_dt_kst.year - 1)
                        except ValueError:
                            pass
                else:
                    # 한국어 날짜 형식 지원: "6월 1일" 또는 "2024년 6월 1일 15:30"
                    kor_date_match = re.search(r'(?:(\d{2,4})년\s*)?(\d{1,2})월\s*(\d{1,2})일(?:\s+(\d{1,2})[:시]\s*(\d{1,2})(?:분)?)?', time_str)
                    if kor_date_match:
                        y_str = kor_date_match.group(1)
                        if y_str:
                            y = int(y_str)
                            if y < 100: y += 2000
                        else:
                            y = now_kst.year
                        
                        month = int(kor_date_match.group(2))
                        d = int(kor_date_match.group(3))
                        h = int(kor_date_match.group(4)) if kor_date_match.group(4) else 0
                        m_val = int(kor_date_match.group(5)) if kor_date_match.group(5) else 0
                        s_val = 0
                        
                        if 1 <= month <= 12 and 1 <= d <= 31:
                            try:
                                posted_dt_kst = datetime(y, month, d, h, m_val, s_val, tzinfo=kst)
                                if posted_dt_kst and posted_dt_kst > now_kst + timedelta(hours=1):
                                    posted_dt_kst = posted_dt_kst.replace(year=posted_dt_kst.year - 1)
                            except ValueError:
                                pass
            
            if posted_dt_kst:
                utc_dt = posted_dt_kst.astimezone(timezone.utc)
                # Android가 완벽히 파싱할 수 있도록 Z 부착
                return utc_dt.replace(tzinfo=None).isoformat(timespec='seconds') + "Z"
        except Exception as e:
            logger.warning(f"시간 파싱 실패 '{time_str}': {e}")
        
        return None
