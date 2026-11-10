import type { DealSource } from "./types";

// 제목에서 가격/통화 보조 추출 (예: "11.6불", "$13.93", "8500원", "9천원")
export const extractPriceFromTitle = (title?: string | null): { price: string; currency: string } | null => {
  if (!title) return null;
  const usdMatch =
    title.match(/\$\s*([0-9]+(?:\.[0-9]+)?)/i) ||
    title.match(/([0-9]+(?:\.[0-9]+)?)\s*(?:달러|불)(?![가-힣])/i);
  if (usdMatch && usdMatch[1]) {
    const val = parseFloat(usdMatch[1]);
    if (val > 0 && val < 10000) {
      return { price: String(Math.round(val * 100)), currency: "USD" };
    }
  }
  const wonMatches = Array.from(
    title.matchAll(/([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{3,7})\s*원(?!\s*(?:권|이상|할인|적립|페이백|캐시백|청구|한도|증정))/g)
  );
  if (wonMatches.length > 0) {
    const lastWon = wonMatches[wonMatches.length - 1][1].replace(/,/g, "");
    const val = parseInt(lastWon, 10);
    if (val >= 100 && val <= 15000000) {
      return { price: String(val), currency: "KRW" };
    }
  }
  const cheonMatch = title.match(
    /([0-9]+(?:\.[0-9]+)?)\s*천\s*원(?!\s*(?:권|이상|할인|적립|페이백|캐시백|청구|한도|증정))/
  );
  if (cheonMatch && cheonMatch[1]) {
    const val = Math.round(parseFloat(cheonMatch[1]) * 1000);
    if (val >= 500) {
      return { price: String(val), currency: "KRW" };
    }
  }
  return null;
};

// 가격 표시 포맷팅 헬퍼
export const formatPrice = (priceVal: any, currency?: string, title?: string): string => {
    let targetPrice = priceVal;
    let targetCurrency = currency;
    const rawStr = targetPrice === null || targetPrice === undefined ? "" : String(targetPrice).trim();
    if (!rawStr || rawStr === "0" || rawStr === "정보 없음" || rawStr === "가격 문의" || rawStr === "null" || rawStr === "undefined") {
      const fromTitle = extractPriceFromTitle(title);
      if (fromTitle) {
        targetPrice = fromTitle.price;
        targetCurrency = fromTitle.currency;
      } else {
        return "가격 문의";
      }
    }
    const price = String(targetPrice).trim();
    if (price.includes("원") || price.includes("$") || price.includes("€")) return price;
    const numOnly = price.replace(/[^0-9.]/g, "");
    if (!numOnly) return price;
    const parsedNum = parseFloat(numOnly);
    if (parsedNum <= 0) return "가격 문의";

    if (targetCurrency === "USD" || (price.includes(".") && parsedNum < 150)) {
      if (!price.includes(".") && parsedNum >= 100) {
        const dollars = parsedNum / 100;
        return dollars % 1 === 0 ? `$${dollars}` : `$${dollars.toFixed(2)}`;
      } else {
        return parsedNum % 1 === 0 ? `$${parsedNum}` : `$${parsedNum.toFixed(2)}`;
      }
    }
    if (targetCurrency === "EUR") {
      if (!price.includes(".") && parsedNum >= 100) {
        const euros = parsedNum / 100;
        return euros % 1 === 0 ? `€${euros}` : `€${euros.toFixed(2)}`;
      } else {
        return parsedNum % 1 === 0 ? `€${parsedNum}` : `€${parsedNum.toFixed(2)}`;
      }
    }
    const formatted = Math.round(parsedNum).toLocaleString();
    return `${formatted}원`;
};

// 제목 기반 카테고리 분류
export const categorizeDeal = (title: string): string => {
    const t = title.toLowerCase();
    if (/pc|컴퓨터|그래픽|모니터|ssd|hdd|ram|램|메모리|키보드|마우스|노트북|글카|rtx|보드|cpu|인텔|amd|라이젠|지포스|로지텍|공유기|nas|웹캠|헤드셋/i.test(t)) return "PC/하드웨어";
    if (/스마트폰|아이폰|갤럭시|충전|보조배터리|이어폰|버즈|에어팟|헤드폰|애플워치|워치|태블릿|패드|아이패드|케이스|케이블/i.test(t)) return "모바일/상품권";
    if (/tv|티비|냉장고|청소기|세탁기|건조기|선풍기|에어컨|가습기|블렌더|모터|스타일러|공기청정기|안마|로봇|다이슨|lg|삼성/i.test(t)) return "가전/TV";
    if (/나이키|아디다스|티셔츠|바지|신발|운동화|자켓|의류|패딩|모자|양말|구두|슬리퍼|가방|팬츠|아식스|뉴발/i.test(t)) return "의류/뷰티";
    if (/물|생수|라면|커피|치킨|피자|콜라|제로|비타민|유산균|햇반|고기|돼지|소고기|과자|두유|우유|닭|스팸|참치|만두|볶음밥|삼다수|사이다|펩시|오레오/i.test(t)) return "식품/건강";
    if (/상품권|컬쳐|해피머니|도서문화|구글|게임|기프트|쿠폰|편의점|cu|gs|포인트|페이|네이버페이|스팀/i.test(t)) return "모바일/상품권";
    return "기타";
};

// 외부 백엔드 API 기본 경로 (앱인토스 정적 빌드 시 NEXT_PUBLIC_API_BASE_URL 환경변수로 주입)
export const API_BASE_URL = (process.env.NEXT_PUBLIC_API_BASE_URL || "").replace(/\/+$/, "");

export const apiUrl = (path: string): string => {
  if (!path) return API_BASE_URL;
  if (path.startsWith("http://") || path.startsWith("https://")) return path;
  const normalized = path.startsWith("/") ? path : `/${path}`;
  return API_BASE_URL ? `${API_BASE_URL}${normalized}` : normalized;
};

// 차단 커뮤니티 도메인 목록 (외부 핫링크 차단으로 인해 wsrv.nl CDN 직접 조회가 불가능하고 자체 백엔드 프록시가 필수인 도메인)
export const BLOCKED_COMMUNITY_DOMAINS = [
  "ppomppu.co.kr",
  "fmkorea.com",
  "fmkorea.org",
  "quasarzone.com",
  "ruliweb.com",
  "bbasak.com",
  "clien.net",
  "coolenjoy.net",
  "damoang.net",
];

export const isBlockedCommunityUrl = (url: string): boolean => {
  const lower = url.toLowerCase();
  return BLOCKED_COMMUNITY_DOMAINS.some((domain) => lower.includes(domain));
};

/**
 * 2중 하이브리드 이미지 프록시/CDN URL 변환기
 * 1. 뽐뿌 등 핫링크 차단 커뮤니티 및 http:// -> 자체 백엔드 프록시 (GCP)
 * 2. 일반 쇼핑몰/포털(네이버, 쿠팡, 지마켓 등) -> 무료 글로벌 CDN (wsrv.nl WebP 변환)
 */
export const getProxyImageUrl = (
  url: string | null | undefined,
  options?: { width?: number; quality?: number }
): string => {
  if (!url) return "";
  const trimmed = url.trim();
  if (!trimmed) return "";

  // 이미 백엔드 프록시 경로이거나 로컬 이미지인 경우
  if (
    trimmed.startsWith("/api/proxy-image") ||
    trimmed.startsWith("/api/image-proxy") ||
    trimmed.startsWith("/images/")
  ) {
    return apiUrl(trimmed);
  }

  // data URI이거나 이미 wsrv.nl인 경우
  if (trimmed.startsWith("data:") || trimmed.includes("wsrv.nl")) {
    return trimmed;
  }

  // 1. 외부 핫링크 차단 커뮤니티 또는 비보안 http:// -> 자체 백엔드 프록시
  if (trimmed.startsWith("http://") || isBlockedCommunityUrl(trimmed)) {
    return apiUrl(`/api/proxy-image?url=${encodeURIComponent(trimmed)}`);
  }

  // 2. 외부 접근이 허용되는 일반 쇼핑몰/포털 https:// 이미지 -> wsrv.nl 무료 글로벌 CDN (WebP 최적화)
  if (trimmed.startsWith("https://")) {
    const widthParam = options?.width ? `&w=${options.width}` : "";
    const qualityParam = options?.quality ? `&q=${options.quality}` : "";
    return `https://wsrv.nl/?url=${encodeURIComponent(trimmed)}&output=webp${widthParam}${qualityParam}&we=1`;
  }

  return trimmed;
};

/**
 * CDN 실패 시 100% 안전하게 자체 백엔드 프록시로 폴백하기 위한 헬퍼
 */
export const getFallbackProxyImageUrl = (rawUrl: string | null | undefined): string => {
  if (!rawUrl) return "";
  const trimmed = rawUrl.trim();
  if (!trimmed) return "";
  if (
    trimmed.startsWith("/api/proxy-image") ||
    trimmed.startsWith("/api/image-proxy") ||
    trimmed.startsWith("/images/")
  ) {
    return apiUrl(trimmed);
  }
  return apiUrl(`/api/proxy-image?url=${encodeURIComponent(trimmed)}`);
};

// 기기 환경(모바일 vs PC)에 맞춰 쇼핑몰 봇 차단 방지 및 커뮤니티 본문 직행 URL로 변환
export const resolveOutboundUrl = (rawUrl: string | undefined | null): string => {
  if (!rawUrl || rawUrl === "#") return "#";
  let url = String(rawUrl).trim();
  if (!url) return "#";

  const isMobile =
    typeof navigator !== "undefined" &&
    /Android|iPhone|iPad|iPod|Mobile|TossApp|KAKAOTALK/i.test(navigator.userAgent || "");

  try {
    // 1. 빠삭(bbasak.com): 상단 900px 거대 배너/헤더를 건너뛰고 실제 게시글 본문(#view_content)으로 즉시 스크롤 이동
    if (url.includes("bbasak.com/bbs/board.php") && url.includes("wr_id=")) {
      if (!url.includes("#")) {
        return `${url}#view_content`;
      }
      return url;
    }

    if (isMobile) {
      // 2. 모바일에서 데스크톱 전용 쇼핑몰 URL 접속 시 Akamai/Cloudflare 봇 인증(Captcha)이 뜨는 현상 원천 차단
      // (1) 옥션: itempage3.auction.co.kr/DetailView.aspx?itemno=XXXX -> mitem.auction.co.kr/vip?itemNo=XXXX
      const auctionMatch = url.match(/itempage3\.auction\.co\.kr\/DetailView\.aspx\?.*?(?:itemno|itemNo)=([A-Za-z0-9]+)/i);
      if (auctionMatch && auctionMatch[1]) {
        return `http://mitem.auction.co.kr/vip?itemNo=${auctionMatch[1]}`;
      }

      // (2) G마켓: item.gmarket.co.kr/Item?goodscode=XXXX -> mitem.gmarket.co.kr/vi/product/XXXX
      const gmarketMatch = url.match(/item\.gmarket\.co\.kr\/Item\?.*?goodscode=([0-9]+)/i);
      if (gmarketMatch && gmarketMatch[1]) {
        return `http://mitem.gmarket.co.kr/vi/product/${gmarketMatch[1]}`;
      }

      // (3) 11번가: www.11st.co.kr/products/XXXX -> m.11st.co.kr/products/m/XXXX
      const st11Match = url.match(/www\.11st\.co\.kr\/products\/([0-9]+)/i);
      if (st11Match && st11Match[1]) {
        return `https://m.11st.co.kr/products/m/${st11Match[1]}`;
      }

      // (4) 쿠팡: www.coupang.com/vp/products/XXXX -> m.coupang.com/vm/products/XXXX
      if (url.includes("www.coupang.com/vp/products/")) {
        return url.replace("www.coupang.com/vp/products/", "m.coupang.com/vm/products/");
      }

      // (5) 뽐뿌 PC 게시글 -> 모바일 뷰어 직행
      const ppomppuMatch = url.match(/www\.ppomppu\.co\.kr\/zboard\/(?:view|zboard)\.php\?.*?id=([^&#]+).*?no=([0-9]+)/i);
      if (ppomppuMatch && ppomppuMatch[1] && ppomppuMatch[2]) {
        return `https://m.ppomppu.co.kr/new/bbs_view.php?id=${ppomppuMatch[1]}&no=${ppomppuMatch[2]}`;
      }

      // (6) 클리앙 PC 게시글 -> 모바일 뷰어 직행
      if (url.includes("www.clien.net/service/board/")) {
        return url.replace("www.clien.net/service/board/", "m.clien.net/service/board/");
      }
    } else {
      // 3. PC(데스크톱)에서 모바일 전용 쇼핑몰 링크 클릭 시 PC 상세 페이지로 정규화
      const mAuctionMatch = url.match(/mitem\.auction\.co\.kr\/vip\?.*?itemNo=([A-Za-z0-9]+)/i);
      if (mAuctionMatch && mAuctionMatch[1]) {
        return `http://itempage3.auction.co.kr/DetailView.aspx?itemno=${mAuctionMatch[1]}`;
      }
      const mGmarketMatch = url.match(/m(?:item)?\.gmarket\.co\.kr\/vi\/product\/([0-9]+)/i);
      if (mGmarketMatch && mGmarketMatch[1]) {
        return `http://item.gmarket.co.kr/Item?goodscode=${mGmarketMatch[1]}`;
      }
    }
  } catch {
    return url;
  }

  return url;
};

/**
 * 옵션명 다중 중첩 접두사 정제 헬퍼 ("옵션 - 알리검색 - 네이버검색 - 상품명" -> "상품명")
 * '알리검색 - ', '네이버검색 - ', '다나와검색 - ', '쿠팡검색 - ', '옵션 - ' 등 반복되는 접두어를 완전히 제거합니다.
 */
export const cleanOptionTitle = (rawName: string): string => {
  if (!rawName) return "";
  let name = rawName.replace(/\u00A0/g, " ").trim();
  // 1단계: "사이트 - ", "옵션 - " 등 최초 1회 분리
  if (name.includes(" - ")) {
    name = name.split(" - ").slice(1).join(" - ").trim();
  }
  // 2단계: '알리검색 - ', '네이버검색 - ', '옵션 - ', '알리 - ', '다나와검색 - ' 등 불필요한 접두어 반복 제거
  const prefixRegex = /^(?:옵션|알리검색|네이버검색|다나와검색|쿠팡검색|알리뽐뿌|뽐뿌|클리앙|루리웹|퀘이사존|에펨코리아|빠삭|알리|네이버)\s*[-:·]\s*/i;
  while (prefixRegex.test(name)) {
    name = name.replace(prefixRegex, "").trim();
  }
  // 3단계: 앞머리 번호('1. '), 여는 괄호 없는 닫는 괄호 머리말('광군제특가] ', '잔여공구코드] ') 및 끝단 미완성 열린 괄호('(옵션') 제거
  name = name.replace(/^(?:\d{1,2}\s*[.)](?!\d)\s+|[\u2460-\u2473]\s*)/, "").trim();
  name = name.replace(/^\d{1,2}(?:\.\d+)?%\s*[/\u00B7\-|]\s*/, "").trim();
  name = name.replace(/^[^\[\]\(\)]{1,14}[\]\)]\s*/, "").trim();
  name = name.replace(/\s*[\(\[][^\)\]]*$/, "").trim();
  return name || rawName.trim();
};

/**
 * 옵션 타이틀 추출 헬퍼 ("알리뽐뿌 - UWANT V600" -> "UWANT V600")
 * cleanOptionTitle을 호출하여 다중 중첩 접두사를 완전히 정제합니다.
 */
export const extractOptionTitle = (siteName: string): string => {
  return cleanOptionTitle(siteName);
};

/**
 * 딜의 sources 배열 또는 fallback 옵션에서 다중 상품(공구/모음전) 옵션 목록만 추출하고 정규화합니다.
 */
export const getOptionSources = (
  sources?: DealSource[] | null,
  fallbackOptions?: any[] | null
): DealSource[] => {
  const seen = new Set<string>();
  const result: DealSource[] = [];

  if (Array.isArray(sources) && sources.length > 0) {
    for (const src of sources) {
      if (!src.site_name || !src.site_name.includes(" - ")) continue;
      const optTitle = cleanOptionTitle(src.site_name);
      const normKey = optTitle.toLowerCase().replace(/\s+/g, "");
      if (!normKey || seen.has(normKey)) continue;
      seen.add(normKey);
      result.push(src);
    }
  }

  if (result.length === 0 && Array.isArray(fallbackOptions) && fallbackOptions.length > 0) {
    for (const opt of fallbackOptions) {
      const optName = cleanOptionTitle(opt.name || opt.site_name || "");
      const normKey = optName.toLowerCase().replace(/\s+/g, "");
      if (!normKey || seen.has(normKey)) continue;
      seen.add(normKey);
      result.push({
        site_name: `옵션 - ${optName}`,
        post_url: opt.url || opt.post_url || "",
        ecommerce_url: opt.url || opt.ecommerce_url || "",
        price: opt.price,
        currency: opt.currency,
        code: opt.code,
        image_url: opt.image_url,
        sub_urls: opt.sub_urls,
      });
    }
  }

  return result;
};

// 본문 HTML/텍스트 자동 정돈 (공구 다중 상품 구분선, 깨진 할인코드 줄바꿈 병합, 불필요한 화살표 링크 노이즈 제거)
export const polishDealBodyText = (rawHtml: string | undefined | null): string => {
  if (!rawHtml) return "";
  let text = rawHtml
    .replace(/<br\s*\/?>/gi, "\n")
    .replace(/<\/(p|div|li|tr|h[1-6])>/gi, "\n")
    .replace(/<[^>]+>/g, "")
    .replace(/&nbsp;/g, " ")
    .replace(/&amp;/g, "&")
    .replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">")
    .replace(/&quot;/g, '"')
    .replace(/&#39;/g, "'")
    .replace(/\uFFFD/g, "");

  // 스크래퍼 파싱용 상품 이미지 마커 및 첨부 이미지 안내문 완전 제거
  text = text
    .replace(/\[상품이미지\s*:\s*https?:\/\/[^\]]+\]/gi, "")
    .replace(/\[첨부된\s*이미지[^\]]*\]/gi, "");

  // 한 줄로 뭉쳐진 본문(퀘이사존/클리앙 등) 자동 줄바꿈 복원
  text = text
    .replace(/\s+(?=[📱📶💰📦🎁⚠️✅🎟️🔥👉ⒶⒷ①②③④⑤])/g, "\n")
    .replace(/\s*🔗\s*/g, "\n")
    .replace(/\s+(?=(?:[1-9]\.\s+[가-힣A-Za-z]))/g, "\n\n")
    .replace(/([^\n])\s*(최종가\s*:\s*[0-9,]+원)/g, "$1\n$2\n");

  const rawLines = text
    .split(/\r?\n/)
    .map((l) => l.replace(/\s+/g, " ").trim())
    .map((l) => l.replace(/^(?:보기|바로가기|제품\s*바로가기|상품\s*바로가기)\s*>{1,}\s*/i, "▪ "))
    .map((l) => l.replace(/\s+최근\s*본\s*상품\s*>\s*$/i, ""))
    .filter((l) => {
      if (!l || l === "▪") return false;
      if (/^\[상품이미지\s*:\s*https?:\/\/[^\]]+\]$/i.test(l)) return false;
      if (/^\[첨부된\s*이미지/i.test(l)) return false;
      if (/^(?:링크|바로가기|보기)\s*>{0,}$/i.test(l)) return false;
      if (/^(?:링크|구매\s*링크)\s*>{2,}/i.test(l)) return false;
      if (/^>{2,}/.test(l)) return false;
      if (/^링크오류시$/i.test(l)) return false;
      if (/^(?:갤럭시|아이폰)\s*:\s*.*(?:링크\s*꾹|알리익스프레스\s*앱)/i.test(l)) return false;
      if (/^2\)\s*알리앱\s*꾹\s*눌러서/i.test(l)) return false;
      if (/^-\s*가격은\s*쿠폰\s*적용\s*전\s*가격을\s*적어주세요/i.test(l)) return false;
      return true;
    });

  // 깨진 프로모션/공구코드 줄바꿈 병합 (숫자 천단위 콤마 96,030은 절대 건드리지 않음)
  const mergedLines: string[] = [];
  for (let i = 0; i < rawLines.length; i++) {
    let curr = rawLines[i]
      .replace(/,\s*,+/g, ", ")
      .replace(/([A-Za-z0-9]{4,})\s*,\s*(?=[A-Za-z0-9]{4,})/g, "$1, ")
      .replace(/,\s*$/, "")
      .trim();

    if (!curr) continue;

    const isCodeOnlyLine =
      /^[A-Z0-9]{4,14}(?:\s*,\s*[A-Z0-9]{4,14})*$/i.test(curr) &&
      /[A-Z]/i.test(curr) &&
      /[0-9]/.test(curr);
    if (isCodeOnlyLine && mergedLines.length > 0) {
      const prev = mergedLines[mergedLines.length - 1];
      if (/(?:코드|쿠폰|할인)/i.test(prev) || /[A-Z0-9]{4,14}\)?$/i.test(prev)) {
        if (prev.endsWith(")")) {
          mergedLines[mergedLines.length - 1] = `${prev.slice(0, -1)}, ${curr})`;
        } else {
          mergedLines[mergedLines.length - 1] =
            prev.endsWith(":") || prev.endsWith(",") ? `${prev} ${curr}` : `${prev} (${curr})`;
        }
        continue;
      }
    }
    mergedLines.push(curr);
  }

  // 다중 상품 공구 게시글에서 '최종가 ...', '최대 할인가 ...', '합계 ...' 뒤에 다음 상품이 이어질 때 구분선 삽입
  const finalLines: string[] = [];
  for (let i = 0; i < mergedLines.length; i++) {
    const line = mergedLines[i];
    finalLines.push(line);
    const isFinalPriceLine =
      /^(?:최종가|최종\s*체감가|최대\s*혜택가|최대\s*할인가|합계)(?=\s|:|$)/i.test(line) ||
      /최저가\s*:\s*\$?[0-9,.]+/i.test(line);
    if (isFinalPriceLine && i < mergedLines.length - 1) {
      const next1 = mergedLines[i + 1] || "";
      const next2 = mergedLines[i + 2] || "";
      const isNext1PaymentFollowUp = /^(?:우리카드|국민카드|결제\s*할인|카드\s*할인|※\s*관세|\*[0-9]+인치)/i.test(next1);
      const hasFollowUpFinal =
        /^(?:최종가|최대\s*할인가)(?=\s|:|$)/i.test(next1) ||
        (isNext1PaymentFollowUp && /^(?:최종가|최대\s*할인가)(?=\s|:|$)/i.test(next2));
      if (!hasFollowUpFinal) {
        finalLines.push("────────────────────────");
      }
    }
  }

  return finalLines.join("\n").replace(/\n{3,}/g, "\n\n").trim();
};

// 본문 내 프로모션/할인 코드 자동 추출 (원클릭 복사용 — 상품 모델번호 오탐 원천 차단)
export const extractPromoCodes = (rawText: string | undefined | null, dealTitle?: string | null): string[] => {
  if (!rawText) return [];
  const codes = new Set<string>();
  const blacklist = new Set([
    "HTTP", "HTTPS", "HTML", "UTF8", "ALLDOCUBE", "BIGME", "VIOFO", "VIOFEO",
    "JONR", "UWANT", "SHOKZ", "IPLAY", "MINI", "PRO", "MAX", "PLUS", "ULTRA",
    "WIFI", "LTE", "NVME", "DDR4", "DDR5", "HDMI", "USBC", "TYPEC", "PD100W",
    "PAW3395", "5000IU", "10000MAH", "20000MAH", "PD20W", "IN1200T", "CB3000",
    "11STREET", "27U411B", "27G411B", "LS32FG810", "S32FG500", "S32FG50", "32U701SB"
  ]);

  const titleUpper = (dealTitle || "").toUpperCase().replace(/[^A-Z0-9]/g, " ");
  const titleTokens = new Set(titleUpper.split(/\s+/).filter((t) => t.length >= 4));

  const lines = rawText.split(/\r?\n/);
  for (const line of lines) {
    const hasExplicitCodeLabel = /(?:공구\s*코드|할인\s*코드|프로모션\s*코드|쿠폰\s*코드|소진시\s*쿠폰|코드\s*할인|코드\s*:|코드\s*\$|🎟|Ⓐ|Ⓑ)/i.test(line);
    const isCodeListLine = /^[A-Z0-9,\s()/$-]{5,80}$/i.test(line.trim()) && line.includes(",");
    const matches = line.match(/\b[A-Z0-9]{5,14}\b/g);
    if (matches) {
      for (const m of matches) {
        const up = m.toUpperCase();
        if (blacklist.has(up)) continue;
        if (titleTokens.has(up) || Array.from(titleTokens).some((tt) => tt.includes(up) || up.includes(tt))) continue;
        if (!/[A-Z]/.test(up) || !/[0-9]/.test(up)) continue;
        if (/^[0-9]+(?:GB|TB|MB|MAH|W|HZ|INCH|PACK|P|G|ML|KG|IU)$/i.test(up)) continue;
        // 모니터/가전 모델명 패턴(예: 27U411B, LS32FG810, RQ33DB74C1AP) 배제
        if (/^(?:[0-9]{2}[A-Z]{1,3}[0-9]{3,4}[A-Z]{0,2}|LS[0-9]{2}[A-Z]{2}[0-9]{3}|RQ[0-9]{2}[A-Z0-9]{5,})$/i.test(up)) continue;
        if ((hasExplicitCodeLabel || isCodeListLine) && up.length >= 5 && /^[A-Z0-9]+$/.test(up)) {
          codes.add(up);
        } else if (up.length >= 6 && /^[A-Z]{3,8}[0-9]{2,6}$/.test(up)) {
          codes.add(up);
        }
      }
    }
  }
  return Array.from(codes).slice(0, 10);
};

const VAPID_PUBLIC_KEY =
  process.env.NEXT_PUBLIC_VAPID_PUBLIC_KEY ||
  "BEl62iUYgUivxIkv69yViEuiBIa-Ib9-SkvMeAtA3LFgDzkrxZJjSgSnfckjBJuBkr3qBUYIHBQFLXYp5Nksh8U";

function urlBase64ToUint8Array(base64String: string) {
  const padding = "=".repeat((4 - (base64String.length % 4)) % 4);
  const base64 = (base64String + padding).replace(/-/g, "+").replace(/_/g, "/");
  const rawData = window.atob(base64);
  const outputArray = new Uint8Array(rawData.length);
  for (let i = 0; i < rawData.length; ++i) {
    outputArray[i] = rawData.charCodeAt(i);
  }
  return outputArray;
}

// 비로그인 기기 고유 UUID 발급 및 로컬스토리지 키 통일
export const getOrCreateWebDeviceUuid = (): string => {
  if (typeof window === "undefined") return "web_guest";
  let uuid =
    localStorage.getItem("insightdeal_web_device_uuid") ||
    localStorage.getItem("insightdeal_token");
  if (!uuid || uuid === "web_guest") {
    const randomPart =
      typeof crypto !== "undefined" && crypto.randomUUID
        ? crypto.randomUUID()
        : `${Date.now()}_${Math.random().toString(36).substring(2, 10)}`;
    uuid = `web_${randomPart}`;
  }
  localStorage.setItem("insightdeal_web_device_uuid", uuid);
  localStorage.setItem("insightdeal_token", uuid);
  return uuid;
};

// 웹에서 알림 설정 클릭 시 별도 로그인 없이 브라우저 Web Push 권한 요청 및 서비스워커 자동 구독
export const ensureWebPushSubscribed = async (): Promise<{
  deviceUuid: string;
  pushGranted: boolean;
}> => {
  const deviceUuid = getOrCreateWebDeviceUuid();
  if (
    typeof window === "undefined" ||
    !("serviceWorker" in navigator) ||
    !("PushManager" in window) ||
    !("Notification" in window)
  ) {
    return { deviceUuid, pushGranted: false };
  }

  try {
    let permission = Notification.permission;
    if (permission === "default") {
      permission = await Notification.requestPermission();
    }
    if (permission !== "granted") {
      return { deviceUuid, pushGranted: false };
    }

    const registration = await navigator.serviceWorker.register("/sw.js");
    await navigator.serviceWorker.ready;

    let subscription = await registration.pushManager.getSubscription();
    if (!subscription) {
      subscription = await registration.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(VAPID_PUBLIC_KEY),
      });
    }

    const subJson = subscription.toJSON();
    await fetch(apiUrl("/api/push-register"), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        device_uuid: deviceUuid,
        endpoint: subJson.endpoint,
        keys: subJson.keys,
      }),
    }).catch(() => null);

    return { deviceUuid, pushGranted: true };
  } catch {
    return { deviceUuid, pushGranted: Notification.permission === "granted" };
  }
};


/**
 * 쇼핑몰 구매 링크 클릭 시 페이지 이동 지연(0ms) 없이 백엔드 수익화 분석기로 클릭 이벤트를 비동기 전송합니다.
 */
export const trackOutboundClick = (
  url: string | undefined | null,
  dealId?: number,
  price?: number | string,
  currency: string = "KRW"
): void => {
  if (!url || url === "#" || typeof window === "undefined") return;
  try {
    const rawPriceNum =
      typeof price === "number"
        ? price
        : parseInt(String(price || "").replace(/[^0-9]/g, ""), 10) || 0;
    const ua = navigator.userAgent || "";
    const isToss =
      ua.includes("TossApp") ||
      window.location.hostname.includes("toss") ||
      process.env.NEXT_PUBLIC_TOSS_MINIAPP === "true";
    const platform = isToss ? "toss" : /Android|iPhone|iPad|iPod|Mobile/i.test(ua) ? "mobile_web" : "pc_web";

    fetch(apiUrl("/api/coupang/track-click"), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        url,
        deal_id: dealId,
        price: rawPriceNum,
        currency: currency || "KRW",
        platform,
      }),
      keepalive: true,
    }).catch(() => {});
  } catch {}
};
