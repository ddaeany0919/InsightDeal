import type { DealSource, DealOptionItem } from "./types";
import { hasTossBridge, logTossClick } from "./tossBridge";

const NAMED_HTML_ENTITIES: Record<string, string> = {
  "&amp;": "&",
  "&lt;": "<",
  "&gt;": ">",
  "&quot;": '"',
  "&apos;": "'",
  "&#39;": "'",
  "&#039;": "'",
  "&nbsp;": " ",
  "&middot;": "·",
  "&ndash;": "–",
  "&mdash;": "—",
  "&hellip;": "…",
  "&times;": "×",
  "&won;": "₩",
};

function decodeHtmlPass(input: string): string {
  return input
    .replace(
      /&(?:amp|lt|gt|quot|apos|nbsp|middot|ndash|mdash|hellip|times|won|#0?39);/gi,
      (match) => NAMED_HTML_ENTITIES[match.toLowerCase()] ?? match
    )
    .replace(/&#(\d{1,7});/g, (full, dec) => {
      const code = parseInt(dec, 10);
      if (code > 0 && code <= 0x10ffff) {
        try {
          return String.fromCodePoint(code);
        } catch {
          return full;
        }
      }
      return full;
    })
    .replace(/&#x([0-9a-fA-F]{1,6});/g, (full, hex) => {
      const code = parseInt(hex, 16);
      if (code > 0 && code <= 0x10ffff) {
        try {
          return String.fromCodePoint(code);
        } catch {
          return full;
        }
      }
      return full;
    });
}

// 제목/배송비/옵션명 HTML 엔티티 디코딩 헬퍼 (이중 인코딩 &amp;#039; 등까지 복원)
export const decodeHtmlEntities = (raw?: string | null): string => {
  if (!raw) return "";
  let decoded = String(raw).replace(/\u00A0/g, " ").replace(/\uFFFD/g, "");
  if (decoded.includes("&")) {
    decoded = decodeHtmlPass(decoded);
    if (decoded.includes("&")) {
      decoded = decodeHtmlPass(decoded);
    }
  }
  return decoded.replace(/\s+/g, " ").trim();
};

const NON_MALL_WORDS = new Set([
  "특가",
  "재입고",
  "기타",
  "역대가",
  "브랜드데이",
  "브랜드 데이",
  "핫딜",
  "공구",
  "광군제",
  "광군제 공구",
  "광군제공구",
  "잔여공구코드",
  "잔여 공구 코드",
  "할인",
  "할인코드",
  "이벤트",
  "품절",
  "종료",
  "끌올",
  "인기",
  "추천",
  "무료",
  "무료배포",
  "일시무료",
  "무배",
  "무료배송",
  "직구",
  "해외직구",
  "국내",
  "해외",
  "쇼핑몰",
  "기타몰",
  "정보",
  "공지",
  // 커뮤니티/카테고리 말머리 오탐 차단
  "뽐뿌",
  "펨코",
  "에펨코리아",
  "루리웹",
  "퀘이사존",
  "클리앙",
  "알리뽐뿌",
  "빠삭",
  "빠삭국내",
  "빠삭해외",
  "빠삭육아",
  "먹거리",
  "식품",
  "음식",
  "가전",
  "디지털",
  "컴퓨터",
  "pc",
  "pc/하드웨어",
  "하드웨어",
  "생활",
  "패션",
  "의류",
  "뷰티",
  "육아",
  "상품권",
  "모바일",
  "게임",
  "sw",
  "소프트웨어",
]);

const MALL_ALIAS_MAP: Record<string, string> = {
  g마켓: "지마켓",
  gmarket: "지마켓",
  지마켓: "지마켓",
  지마켓글로벌: "지마켓",
  알리: "알리익스프레스",
  알리익스프레스: "알리익스프레스",
  aliexpress: "알리익스프레스",
  ali: "알리익스프레스",
  쿠팡: "쿠팡",
  coupang: "쿠팡",
  "11번가": "11번가",
  "11st": "11번가",
  십일번가: "11번가",
  "11번가아마존": "11번가",
  아마존11번가: "11번가",
  옥션: "옥션",
  auction: "옥션",
  네이버: "네이버",
  네이버쇼핑: "네이버",
  네이버스토어: "네이버",
  스마트스토어: "네이버",
  브랜드스토어: "네이버",
  네이버페이: "네이버",
  naver: "네이버",
  롯데온: "롯데온",
  롯데on: "롯데온",
  lotteon: "롯데온",
  ssg: "SSG닷컴",
  ssg닷컴: "SSG닷컴",
  쓱: "SSG닷컴",
  쓱닷컴: "SSG닷컴",
  이마트몰: "이마트몰",
  신세계몰: "신세계몰",
  컴퓨존: "컴퓨존",
  compuzone: "컴퓨존",
  탁탁몰: "탁탁몰",
  tagtag: "탁탁몰",
  타오바오: "타오바오",
  taobao: "타오바오",
  티몰: "타오바오",
  tmall: "타오바오",
  스팀: "스팀",
  steam: "스팀",
  에픽: "에픽게임즈",
  에픽게임즈: "에픽게임즈",
  epic: "에픽게임즈",
  epicgames: "에픽게임즈",
  아마존: "아마존",
  amazon: "아마존",
  무신사: "무신사",
  musinsa: "무신사",
  오늘의집: "오늘의집",
  컬리: "컬리",
  마켓컬리: "컬리",
  하이마트: "하이마트",
  롯데하이마트: "하이마트",
  올리브영: "올리브영",
  카카오: "카카오쇼핑",
  카카오쇼핑: "카카오쇼핑",
  카카오톡쇼핑하기: "카카오쇼핑",
  톡딜: "카카오쇼핑",
  카카오선물하기: "카카오쇼핑",
  토스: "토스쇼핑",
  토스쇼핑: "토스쇼핑",
  아이허브: "아이허브",
  iherb: "아이허브",
  큐텐: "큐텐",
  qoo10: "큐텐",
  테무: "테무",
  temu: "테무",
  다이소: "다이소몰",
  다이소몰: "다이소몰",
  배민: "배달의민족",
  배달의민족: "배달의민족",
  요기요: "요기요",
  티몬: "티몬",
  인터파크: "인터파크",
  지그재그: "지그재그",
  에이블리: "에이블리",
  gog: "GOG",
};

export const normalizeMallAlias = (raw?: string | null): string => {
  if (!raw) return "";
  const decoded = decodeHtmlEntities(raw)
    .replace(/^[\[\(\{]+|[\]\)\}]+$/g, "")
    .trim();
  if (!decoded) return "";
  const compactKey = decoded.toLowerCase().replace(/\s+/g, "");
  return MALL_ALIAS_MAP[compactKey] || MALL_ALIAS_MAP[decoded.toLowerCase()] || decoded;
};

export const isNonMallLabel = (raw?: string | null): boolean => {
  if (!raw) return true;
  const cleaned = decodeHtmlEntities(raw)
    .replace(/^[\[\(\{]+|[\]\)\}]+$/g, "")
    .trim();
  if (!cleaned || cleaned.length > 20) return true;
  const lower = cleaned.toLowerCase();
  const compact = lower.replace(/\s+/g, "");
  if (NON_MALL_WORDS.has(lower) || NON_MALL_WORDS.has(compact)) return true;
  if (
    /(?:공구|특가|할인\s*코드|잔여\s*공구|잔여\s*코드|쿠폰\s*코드|역대가|브랜드\s*데이|광군제|재입고|품절|종료|끌올)/i.test(
      cleaned
    )
  ) {
    return true;
  }
  // 가격이나 할인율 형태([15,000원], [30%]) 차단
  if (/^[0-9,.%\s원달러불$€¥~+-]+$/.test(cleaned)) return true;
  return false;
};

export const inferShopFromUrl = (rawUrl?: string | null): string | null => {
  if (!rawUrl || rawUrl === "#") return null;
  const lower = rawUrl.trim().toLowerCase();
  if (!lower.startsWith("http://") && !lower.startsWith("https://")) return null;

  // 커뮤니티 도메인은 쇼핑몰 추론에서 제외
  if (
    lower.includes("ppomppu.co.kr") ||
    lower.includes("fmkorea.") ||
    lower.includes("quasarzone.com") ||
    lower.includes("ruliweb.com") ||
    lower.includes("clien.net") ||
    lower.includes("bbasak.com") ||
    lower.includes("coolenjoy.net") ||
    lower.includes("damoang.net")
  ) {
    return null;
  }

  const domainRules: Array<[RegExp, string]> = [
    [/(?:^|\/\/|\.)gmarket\.co\.kr/, "지마켓"],
    [/(?:^|\/\/|\.)(?:coupang\.com|coupa\.ng)/, "쿠팡"],
    [/(?:^|\/\/|\.)11st\.co\.kr/, "11번가"],
    [/(?:^|\/\/|\.)auction\.co\.kr/, "옥션"],
    [/(?:^|\/\/|\.)aliexpress\.com/, "알리익스프레스"],
    [/(?:smartstore|brand|shopping)\.naver\.com|naver\.me\//, "네이버"],
    [/(?:^|\/\/|\.)lotteon\.com/, "롯데온"],
    [/(?:^|\/\/|\.)compuzone\.co\.kr/, "컴퓨존"],
    [/(?:^|\/\/|\.)tagtag\.co\.kr/, "탁탁몰"],
    [/(?:^|\/\/|\.)(?:taobao|tmall)\.com/, "타오바오"],
    [/(?:store\.steampowered|steamcommunity)\.com/, "스팀"],
    [/(?:^|\/\/|\.)epicgames\.com/, "에픽게임즈"],
    [/(?:^|\/\/|\.)ssg\.com/, "SSG닷컴"],
    [/(?:^|\/\/|\.)(?:amazon\.com|amazon\.co\.jp|amzn\.to)/, "아마존"],
    [/(?:^|\/\/|\.)musinsa\.com/, "무신사"],
    [/(?:^|\/\/|\.)(?:ohou\.se|bucketplace)/, "오늘의집"],
    [/(?:^|\/\/|\.)kurly\.com/, "컬리"],
    [/(?:^|\/\/|\.)e-himart\.co\.kr/, "하이마트"],
    [/(?:^|\/\/|\.)oliveyoung\.co\.kr/, "올리브영"],
    [/(?:store|gift|shopping)\.kakao\.com/, "카카오쇼핑"],
    [/(?:^|\/\/|\.)iherb\.com/, "아이허브"],
    [/(?:^|\/\/|\.)qoo10\.com/, "큐텐"],
    [/(?:^|\/\/|\.)temu\.com/, "테무"],
    [/(?:^|\/\/|\.)daisomall\.co\.kr/, "다이소몰"],
    [/(?:^|\/\/|\.)gog\.com/, "GOG"],
    [/(?:store\.)?playstation\.com/, "PS스토어"],
    [/store\.nintendo\.co\.kr/, "닌텐도"],
  ];

  for (const [regex, shop] of domainRules) {
    if (regex.test(lower)) return shop;
  }
  return null;
};

/**
 * 쇼핑몰 배지 정규화:
 * 1) deal.shop_name이 유효하고 비쇼핑몰 단어가 아니면 정규화하여 1순위로 반환
 * 2) 제목 앞 [말머리]가 유효한 쇼핑몰명이면 정규화하여 반환 (비쇼핑몰 말머리면 스킵)
 * 3) ecommerce_url 도메인 역추론 결과 반환
 */
export const resolveDealShopName = (params: {
  shop_name?: string | null;
  title?: string | null;
  ecommerce_url?: string | null;
}): string | null => {
  if (params.shop_name && !isNonMallLabel(params.shop_name)) {
    const normalized = normalizeMallAlias(params.shop_name);
    if (normalized && !isNonMallLabel(normalized)) {
      return normalized;
    }
  }

  const cleanTitle = decodeHtmlEntities(params.title);
  if (cleanTitle) {
    // 선두 최대 2개의 대괄호 말머리 검사 (예: "[특가] [G마켓] ..." -> "지마켓")
    let rest = cleanTitle;
    for (let i = 0; i < 2; i++) {
      const m = rest.match(/^\s*\[([^\[\]]{1,20})\]\s*/);
      if (!m) break;
      const bracketLabel = m[1].trim();
      rest = rest.slice(m[0].length);
      if (!isNonMallLabel(bracketLabel)) {
        const normalized = normalizeMallAlias(bracketLabel);
        if (normalized && !isNonMallLabel(normalized)) {
          return normalized;
        }
      }
    }
  }

  return inferShopFromUrl(params.ecommerce_url);
};

/**
 * 쇼핑몰 배지가 이미 표시될 때 제목 앞의 동일한 [쇼핑몰명] 중복 노출 제거 + HTML 엔티티 디코딩
 */
export const stripLeadingShopBracket = (
  rawTitle?: string | null,
  shopBadge?: string | null
): string => {
  const decoded = decodeHtmlEntities(rawTitle);
  if (!decoded || !shopBadge) return decoded;

  const targetNorm = normalizeMallAlias(shopBadge).toLowerCase().replace(/\s+/g, "");
  if (!targetNorm) return decoded;

  let current = decoded;
  // 1) 맨 앞 연속된 [쇼핑몰명] 또는 (쇼핑몰명) 제거 (예: "[G마켓] [지마켓] 상품명")
  while (true) {
    const m = current.match(/^\s*[\[\(]([^\]\)]{1,20})[\]\)]\s*/);
    if (!m) break;
    const candidateNorm = normalizeMallAlias(m[1]).toLowerCase().replace(/\s+/g, "");
    if (candidateNorm === targetNorm) {
      current = current.slice(m[0].length).trim();
    } else {
      break;
    }
  }

  // 2) "[특가] [G마켓] 상품명"처럼 비쇼핑몰 말머리 바로 뒤에 동일 쇼핑몰명이 오는 경우도 중복 제거
  const secondBracketMatch = current.match(
    /^(\s*\[[^\[\]]{1,20}\]\s*)[\[\(]([^\]\)]{1,20})[\]\)]\s*/
  );
  if (secondBracketMatch) {
    const secondNorm = normalizeMallAlias(secondBracketMatch[2])
      .toLowerCase()
      .replace(/\s+/g, "");
    if (secondNorm === targetNorm) {
      current = (
        secondBracketMatch[1] + current.slice(secondBracketMatch[0].length)
      ).trim();
    }
  }

  return current || decoded;
};

// 무료 배포 딜 여부 판별 (배송비 무료만 있는 경우 제외)
export const isFreeGiveawayTitle = (titleHint?: string | null): boolean => {
  if (!titleHint) return false;
  const stripped = decodeHtmlEntities(titleHint).replace(
    /(?:무료\s*배송|배송비?\s*무료|택배비?\s*무료|무배|무료\s*반품|무료\s*교환|무료\s*설치|무료\s*체험)/gi,
    " "
  );
  if (/무료\s*배포|일시\s*무료|한시적?\s*무료|전액\s*무료/i.test(stripped)) {
    return true;
  }
  if (/[\(\[\{]\s*(?:무료|0\s*원|FREE)\s*[\)\]\}]/i.test(stripped)) {
    return true;
  }
  if (/[\(\[\{][^)\]}]*(?:\/\s*)(?:무료|0\s*원)\s*[\)\]\}]/i.test(stripped)) {
    return true;
  }
  if (/\$\s*0(?:\.0+)?(?![0-9])/.test(stripped)) {
    return true;
  }
  if (
    /(?:^|[\s\(\[\{\/·|,:-])(?:무료|0\s*원)(?=$|[\s\)\]\}\/·|,!?.~])/i.test(
      stripped
    )
  ) {
    return true;
  }
  return false;
};

// 제목에서 가격/통화 보조 추출 (예: "11.6불", "$0.99", "8500원", "9천원")
export const extractPriceFromTitle = (
  title?: string | null
): { price: string; currency: string } | null => {
  if (!title) return null;
  const cleanTitle = decodeHtmlEntities(title);

  const usdMatch =
    cleanTitle.match(/\$\s*([0-9]+(?:\.[0-9]+)?)/i) ||
    cleanTitle.match(/([0-9]+(?:\.[0-9]+)?)\s*(?:달러|불)(?![가-힣])/i);
  if (usdMatch && usdMatch[1]) {
    const val = parseFloat(usdMatch[1]);
    if (val > 0 && val < 100000) {
      return { price: String(Math.round(val * 100)), currency: "USD" };
    }
  }

  const eurMatch =
    cleanTitle.match(/€\s*([0-9]+(?:\.[0-9]+)?)/i) ||
    cleanTitle.match(/([0-9]+(?:\.[0-9]+)?)\s*유로(?![가-힣])/i);
  if (eurMatch && eurMatch[1]) {
    const val = parseFloat(eurMatch[1]);
    if (val > 0 && val < 100000) {
      return { price: String(Math.round(val * 100)), currency: "EUR" };
    }
  }

  const cnyMatch = cleanTitle.match(/([0-9]+(?:\.[0-9]+)?)\s*위안(?![가-힣])/i);
  if (cnyMatch && cnyMatch[1]) {
    const val = parseFloat(cnyMatch[1]);
    if (val > 0 && val < 1000000) {
      return { price: String(Math.round(val * 100)), currency: "CNY" };
    }
  }

  const jpyMatch = cleanTitle.match(/([0-9]{2,7})\s*엔(?![가-힣])/i);
  if (jpyMatch && jpyMatch[1]) {
    const val = parseInt(jpyMatch[1].replace(/,/g, ""), 10);
    if (val > 0 && val < 10000000) {
      return { price: String(val), currency: "JPY" };
    }
  }

  const wonMatches = Array.from(
    cleanTitle.matchAll(
      /([0-9]{1,3}(?:,[0-9]{3})+|[0-9]{3,8})\s*원(?!\s*(?:권|이상|할인|적립|페이백|캐시백|청구|한도|증정))/g
    )
  );
  if (wonMatches.length > 0) {
    const lastWon = wonMatches[wonMatches.length - 1][1].replace(/,/g, "");
    const val = parseInt(lastWon, 10);
    if (val >= 100 && val <= 30000000) {
      return { price: String(val), currency: "KRW" };
    }
  }

  const cheonMatch = cleanTitle.match(
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

function formatSubunitCurrency(amount: number, symbol: string): string {
  const rounded = Math.round(amount * 100) / 100;
  if (Math.abs(rounded - Math.round(rounded)) < 1e-9) {
    return `${symbol}${Math.round(rounded).toLocaleString("en-US")}`;
  }
  return `${symbol}${rounded.toLocaleString("en-US", {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  })}`;
}

// 가격 표시 포맷팅 헬퍼 (외화 센트/전 정규화 + 무료/가격 확인 통일)
export const formatPrice = (
  priceVal: string | number | null | undefined,
  currency?: string,
  titleHint?: string
): string => {
  let targetPrice = priceVal;
  let targetCurrency = currency;
  const rawStr =
    targetPrice === null || targetPrice === undefined
      ? ""
      : String(targetPrice).trim();

  if (rawStr === "무료") return "무료";

  if (
    !rawStr ||
    rawStr === "0" ||
    rawStr === "0원" ||
    rawStr === "정보 없음" ||
    rawStr === "가격 문의" ||
    rawStr === "가격 확인" ||
    rawStr === "null" ||
    rawStr === "undefined"
  ) {
    const fromTitle = extractPriceFromTitle(titleHint);
    if (fromTitle) {
      targetPrice = fromTitle.price;
      targetCurrency = fromTitle.currency;
    } else {
      return isFreeGiveawayTitle(titleHint) ? "무료" : "가격 확인";
    }
  }

  const price = String(targetPrice).trim();
  if (
    (price.includes("원") ||
      price.includes("$") ||
      price.includes("€") ||
      price.includes("¥")) &&
    /[1-9]/.test(price)
  ) {
    return price;
  }

  const numOnly = price.replace(/[^0-9.]/g, "");
  if (!numOnly) {
    return isFreeGiveawayTitle(titleHint) ? "무료" : "가격 확인";
  }

  const parsedNum = parseFloat(numOnly);
  if (!Number.isFinite(parsedNum) || parsedNum <= 0) {
    const fromTitle = extractPriceFromTitle(titleHint);
    if (fromTitle) {
      return formatPrice(fromTitle.price, fromTitle.currency);
    }
    return isFreeGiveawayTitle(titleHint) ? "무료" : "가격 확인";
  }

  const normCurrency = (targetCurrency || "KRW").trim().toUpperCase();
  const hasDecimalPoint = price.includes(".");

  if (normCurrency === "USD") {
    const dollars = hasDecimalPoint ? parsedNum : parsedNum / 100;
    return formatSubunitCurrency(dollars, "$");
  }
  if (normCurrency === "EUR") {
    const euros = hasDecimalPoint ? parsedNum : parsedNum / 100;
    return formatSubunitCurrency(euros, "€");
  }
  if (normCurrency === "CNY") {
    const yuan = hasDecimalPoint ? parsedNum : parsedNum / 100;
    return formatSubunitCurrency(yuan, "¥");
  }
  if (normCurrency === "JPY") {
    return `¥${Math.round(parsedNum).toLocaleString()}`;
  }

  return `${Math.round(parsedNum).toLocaleString()}원`;
};

// 제목 기반 카테고리 분류
export const categorizeDeal = (title: string): string => {
  const t = decodeHtmlEntities(title).toLowerCase();
  if (
    /pc|컴퓨터|그래픽|모니터|ssd|hdd|ram|램|메모리|키보드|마우스|노트북|글카|rtx|보드|cpu|인텔|amd|라이젠|지포스|로지텍|공유기|nas|웹캠|헤드셋/i.test(
      t
    )
  )
    return "PC/하드웨어";
  if (
    /스마트폰|아이폰|갤럭시|충전|보조배터리|이어폰|버즈|에어팟|헤드폰|애플워치|워치|태블릿|패드|아이패드|케이스|케이블/i.test(
      t
    )
  )
    return "모바일/상품권";
  if (
    /tv|티비|냉장고|청소기|세탁기|건조기|선풍기|에어컨|가습기|블렌더|모터|스타일러|공기청정기|안마|로봇|다이슨|lg|삼성/i.test(
      t
    )
  )
    return "가전/TV";
  if (
    /나이키|아디다스|티셔츠|바지|신발|운동화|자켓|의류|패딩|모자|양말|구두|슬리퍼|가방|팬츠|아식스|뉴발/i.test(
      t
    )
  )
    return "의류/뷰티";
  if (
    /물|생수|라면|커피|치킨|피자|콜라|제로|비타민|유산균|햇반|고기|돼지|소고기|과자|두유|우유|닭|스팸|참치|만두|볶음밥|삼다수|사이다|펩시|오레오/i.test(
      t
    )
  )
    return "식품/건강";
  if (
    /상품권|컬쳐|해피머니|도서문화|구글|게임|기프트|쿠폰|편의점|cu|gs|포인트|페이|네이버페이|스팀/i.test(
      t
    )
  )
    return "모바일/상품권";
  return "기타";
};

// 외부 백엔드 API 기본 경로 (앱인토스 정적 빌드 시 NEXT_PUBLIC_API_BASE_URL 환경변수로 주입)
export const API_BASE_URL = (
  process.env.NEXT_PUBLIC_API_BASE_URL || ""
).replace(/\/+$/, "");

export const apiUrl = (path: string): string => {
  if (!path) return API_BASE_URL;
  if (path.startsWith("http://") || path.startsWith("https://")) return path;
  const normalized = path.startsWith("/") ? path : `/${path}`;
  return API_BASE_URL ? `${API_BASE_URL}${normalized}` : normalized;
};

// 차단 커뮤니티 도메인 목록
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

export const getProxyImageUrl = (
  url: string | null | undefined,
  options?: { width?: number; quality?: number }
): string => {
  if (!url) return "";
  const trimmed = url.trim();
  if (!trimmed) return "";

  if (
    trimmed.startsWith("/api/proxy-image") ||
    trimmed.startsWith("/api/image-proxy") ||
    trimmed.startsWith("/images/")
  ) {
    return apiUrl(trimmed);
  }

  if (trimmed.startsWith("data:") || trimmed.includes("wsrv.nl")) {
    return trimmed;
  }

  if (trimmed.startsWith("http://") || isBlockedCommunityUrl(trimmed)) {
    return apiUrl(`/api/proxy-image?url=${encodeURIComponent(trimmed)}`);
  }

  if (trimmed.startsWith("https://")) {
    const widthParam = options?.width ? `&w=${options.width}` : "";
    const qualityParam = options?.quality ? `&q=${options.quality}` : "";
    return `https://wsrv.nl/?url=${encodeURIComponent(
      trimmed
    )}&output=webp${widthParam}${qualityParam}&we=1`;
  }

  return trimmed;
};

export const getFallbackProxyImageUrl = (
  rawUrl: string | null | undefined
): string => {
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

export const resolveOutboundUrl = (
  rawUrl: string | undefined | null
): string => {
  if (!rawUrl || rawUrl === "#") return "#";
  const url = String(rawUrl).trim();
  if (!url) return "#";

  const isMobile =
    typeof navigator !== "undefined" &&
    /Android|iPhone|iPad|iPod|Mobile|TossApp|KAKAOTALK/i.test(
      navigator.userAgent || ""
    );

  try {
    if (url.includes("bbasak.com/bbs/board.php") && url.includes("wr_id=")) {
      if (!url.includes("#")) {
        return `${url}#view_content`;
      }
      return url;
    }

    if (isMobile) {
      const auctionMatch = url.match(
        /itempage3\.auction\.co\.kr\/DetailView\.aspx\?.*?(?:itemno|itemNo)=([A-Za-z0-9]+)/i
      );
      if (auctionMatch && auctionMatch[1]) {
        return `http://mitem.auction.co.kr/vip?itemNo=${auctionMatch[1]}`;
      }

      const gmarketMatch = url.match(
        /item\.gmarket\.co\.kr\/Item\?.*?goodscode=([0-9]+)/i
      );
      if (gmarketMatch && gmarketMatch[1]) {
        return `http://mitem.gmarket.co.kr/vi/product/${gmarketMatch[1]}`;
      }

      const st11Match = url.match(/www\.11st\.co\.kr\/products\/([0-9]+)/i);
      if (st11Match && st11Match[1]) {
        return `https://m.11st.co.kr/products/m/${st11Match[1]}`;
      }

      if (url.includes("www.coupang.com/vp/products/")) {
        return url.replace(
          "www.coupang.com/vp/products/",
          "m.coupang.com/vm/products/"
        );
      }

      const ppomppuMatch = url.match(
        /www\.ppomppu\.co\.kr\/zboard\/(?:view|zboard)\.php\?.*?id=([^&#]+).*?no=([0-9]+)/i
      );
      if (ppomppuMatch && ppomppuMatch[1] && ppomppuMatch[2]) {
        return `https://m.ppomppu.co.kr/new/bbs_view.php?id=${ppomppuMatch[1]}&no=${ppomppuMatch[2]}`;
      }

      if (url.includes("www.clien.net/service/board/")) {
        return url.replace(
          "www.clien.net/service/board/",
          "m.clien.net/service/board/"
        );
      }
    } else {
      const mAuctionMatch = url.match(
        /mitem\.auction\.co\.kr\/vip\?.*?itemNo=([A-Za-z0-9]+)/i
      );
      if (mAuctionMatch && mAuctionMatch[1]) {
        return `http://itempage3.auction.co.kr/DetailView.aspx?itemno=${mAuctionMatch[1]}`;
      }
      const mGmarketMatch = url.match(
        /m(?:item)?\.gmarket\.co\.kr\/vi\/product\/([0-9]+)/i
      );
      if (mGmarketMatch && mGmarketMatch[1]) {
        return `http://item.gmarket.co.kr/Item?goodscode=${mGmarketMatch[1]}`;
      }
    }
  } catch {
    return url;
  }

  return url;
};

export const cleanOptionTitle = (rawName: string): string => {
  if (!rawName) return "";
  const decodedRaw = decodeHtmlEntities(rawName);
  let name = decodedRaw;
  if (name.includes(" - ")) {
    name = name.split(" - ").slice(1).join(" - ").trim();
  }
  const prefixRegex =
    /^(?:옵션|알리검색|네이버검색|다나와검색|쿠팡검색|알리뽐뿌|뽐뿌|클리앙|루리웹|퀘이사존|에펨코리아|빠삭|알리|네이버)\s*[-:·]\s*/i;
  while (prefixRegex.test(name)) {
    name = name.replace(prefixRegex, "").trim();
  }
  name = name
    .replace(/^(?:\d{1,2}\s*[.)](?!\d)\s+|[\u2460-\u2473]\s*)/, "")
    .trim();
  name = name.replace(/^\d{1,2}(?:\.\d+)?%\s*[/\u00B7\-|]\s*/, "").trim();
  name = name.replace(/^[^\[\]\(\)]{1,14}[\]\)]\s*/, "").trim();
  name = name.replace(/\s*[\(\[][^\)\]]*$/, "").trim();
  return name || decodedRaw;
};

export const extractOptionTitle = (siteName: string): string => {
  return cleanOptionTitle(siteName);
};

export const getOptionSources = (
  sources?: DealSource[] | null,
  fallbackOptions?: DealOptionItem[] | null
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
      result.push({
        ...src,
        site_name: decodeHtmlEntities(src.site_name),
      });
    }
  }

  if (
    result.length === 0 &&
    Array.isArray(fallbackOptions) &&
    fallbackOptions.length > 0
  ) {
    for (const opt of fallbackOptions) {
      const optName = cleanOptionTitle(
        opt.name || opt.title || opt.option_name || opt.site_name || ""
      );
      const normKey = optName.toLowerCase().replace(/\s+/g, "");
      if (!normKey || seen.has(normKey)) continue;
      seen.add(normKey);
      result.push({
        site_name: `옵션 - ${optName}`,
        post_url: opt.url || opt.post_url || "",
        ecommerce_url: opt.ecommerce_url || opt.url || "",
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

export const polishDealBodyText = (rawHtml: string | undefined | null): string => {
  if (!rawHtml) return "";
  let text = rawHtml
    .replace(/<br\s*\/?>/gi, "\n")
    .replace(/<\/(p|div|li|tr|h[1-6])>/gi, "\n")
    .replace(/<[^>]+>/g, "");

  text = text
    .split(/\r?\n/)
    .map((line) => decodeHtmlEntities(line))
    .join("\n");

  text = text
    .replace(/\[상품이미지\s*:\s*https?:\/\/[^\]]+\]/gi, "")
    .replace(/\[첨부된\s*이미지[^\]]*\]/gi, "");

  text = text
    .replace(/\s+(?=[📱📶💰📦🎁⚠️✅🎟️🔥👉ⒶⒷ①②③④⑤])/g, "\n")
    .replace(/\s*🔗\s*/g, "\n")
    .replace(/\s+(?=(?:[1-9]\.\s+[가-힣A-Za-z]))/g, "\n\n")
    .replace(/([^\n])\s*(최종가\s*:\s*[0-9,]+원)/g, "$1\n$2\n");

  const rawLines = text
    .split(/\r?\n/)
    .map((l) => l.replace(/\s+/g, " ").trim())
    .map((l) =>
      l.replace(/^(?:보기|바로가기|제품\s*바로가기|상품\s*바로가기)\s*>{1,}\s*/i, "▪ ")
    )
    .map((l) => l.replace(/\s+최근\s*본\s*상품\s*>\s*$/i, ""))
    .filter((l) => {
      if (!l || l === "▪") return false;
      if (/^\[상품이미지\s*:\s*https?:\/\/[^\]]+\]$/i.test(l)) return false;
      if (/^\[첨부된\s*이미지/i.test(l)) return false;
      if (/^(?:링크|바로가기|보기)\s*>{0,}$/i.test(l)) return false;
      if (/^(?:링크|구매\s*링크)\s*>{2,}/i.test(l)) return false;
      if (/^>{2,}/.test(l)) return false;
      if (/^링크오류시$/i.test(l)) return false;
      if (/^(?:갤럭시|아이폰)\s*:\s*.*(?:링크\s*꾹|알리익스프레스\s*앱)/i.test(l))
        return false;
      if (/^2\)\s*알리앱\s*꾹\s*눌러서/i.test(l)) return false;
      if (/^-\s*가격은\s*쿠폰\s*적용\s*전\s*가격을\s*적어주세요/i.test(l))
        return false;
      return true;
    });

  const mergedLines: string[] = [];
  for (let i = 0; i < rawLines.length; i++) {
    const curr = rawLines[i]
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
            prev.endsWith(":") || prev.endsWith(",")
              ? `${prev} ${curr}`
              : `${prev} (${curr})`;
        }
        continue;
      }
    }
    mergedLines.push(curr);
  }

  const finalLines: string[] = [];
  for (let i = 0; i < mergedLines.length; i++) {
    const line = mergedLines[i];
    finalLines.push(line);
    const isFinalPriceLine =
      /^(?:최종가|최종\s*체감가|최대\s*혜택가|최대\s*할인가|합계)(?=\s|:|$)/i.test(
        line
      ) || /최저가\s*:\s*\$?[0-9,.]+/i.test(line);
    if (isFinalPriceLine && i < mergedLines.length - 1) {
      const next1 = mergedLines[i + 1] || "";
      const next2 = mergedLines[i + 2] || "";
      const isNext1PaymentFollowUp =
        /^(?:우리카드|국민카드|결제\s*할인|카드\s*할인|※\s*관세|\*[0-9]+인치)/i.test(
          next1
        );
      const hasFollowUpFinal =
        /^(?:최종가|최대\s*할인가)(?=\s|:|$)/i.test(next1) ||
        (isNext1PaymentFollowUp &&
          /^(?:최종가|최대\s*할인가)(?=\s|:|$)/i.test(next2));
      if (!hasFollowUpFinal) {
        finalLines.push("────────────────────────");
      }
    }
  }

  return finalLines.join("\n").replace(/\n{3,}/g, "\n\n").trim();
};

export const extractPromoCodes = (
  rawText: string | undefined | null,
  dealTitle?: string | null
): string[] => {
  if (!rawText) return [];
  const codes = new Set<string>();
  const blacklist = new Set([
    "HTTP",
    "HTTPS",
    "HTML",
    "UTF8",
    "ALLDOCUBE",
    "BIGME",
    "VIOFO",
    "VIOFEO",
    "JONR",
    "UWANT",
    "SHOKZ",
    "IPLAY",
    "MINI",
    "PRO",
    "MAX",
    "PLUS",
    "ULTRA",
    "WIFI",
    "LTE",
    "NVME",
    "DDR4",
    "DDR5",
    "HDMI",
    "USBC",
    "TYPEC",
    "PD100W",
    "PAW3395",
    "5000IU",
    "10000MAH",
    "20000MAH",
    "PD20W",
    "IN1200T",
    "CB3000",
    "11STREET",
    "27U411B",
    "27G411B",
    "LS32FG810",
    "S32FG500",
    "S32FG50",
    "32U701SB",
  ]);

  const titleUpper = decodeHtmlEntities(dealTitle)
    .toUpperCase()
    .replace(/[^A-Z0-9]/g, " ");
  const titleTokens = new Set(
    titleUpper.split(/\s+/).filter((t) => t.length >= 4)
  );

  const lines = rawText.split(/\r?\n/);
  for (const line of lines) {
    const hasExplicitCodeLabel =
      /(?:공구\s*코드|할인\s*코드|프로모션\s*코드|쿠폰\s*코드|소진시\s*쿠폰|코드\s*할인|코드\s*:|코드\s*\$|🎟|Ⓐ|Ⓑ)/i.test(
        line
      );
    const isCodeListLine =
      /^[A-Z0-9,\s()/$-]{5,80}$/i.test(line.trim()) && line.includes(",");
    const matches = line.match(/\b[A-Z0-9]{5,14}\b/g);
    if (matches) {
      for (const m of matches) {
        const up = m.toUpperCase();
        if (blacklist.has(up)) continue;
        if (
          titleTokens.has(up) ||
          Array.from(titleTokens).some(
            (tt) => tt.includes(up) || up.includes(tt)
          )
        )
          continue;
        if (!/[A-Z]/.test(up) || !/[0-9]/.test(up)) continue;
        if (/^[0-9]+(?:GB|TB|MB|MAH|W|HZ|INCH|PACK|P|G|ML|KG|IU)$/i.test(up))
          continue;
        if (
          /^(?:[0-9]{2}[A-Z]{1,3}[0-9]{3,4}[A-Z]{0,2}|LS[0-9]{2}[A-Z]{2}[0-9]{3}|RQ[0-9]{2}[A-Z0-9]{5,})$/i.test(
            up
          )
        )
          continue;
        if (
          (hasExplicitCodeLabel || isCodeListLine) &&
          up.length >= 5 &&
          /^[A-Z0-9]+$/.test(up)
        ) {
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

function urlBase64ToUint8Array(base64String: string): Uint8Array<ArrayBuffer> {
  const padding = "=".repeat((4 - (base64String.length % 4)) % 4);
  const base64 = (base64String + padding).replace(/-/g, "+").replace(/_/g, "/");
  const rawData = window.atob(base64);
  const buffer = new ArrayBuffer(rawData.length);
  const outputArray = new Uint8Array(buffer);
  for (let i = 0; i < rawData.length; ++i) {
    outputArray[i] = rawData.charCodeAt(i);
  }
  return outputArray;
}

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
      hasTossBridge() ||
      ua.includes("TossApp") ||
      window.location.hostname.includes("toss") ||
      process.env.NEXT_PUBLIC_TOSS_MINIAPP === "true";
    const platform = isToss
      ? "toss"
      : /Android|iPhone|iPad|iPod|Mobile/i.test(ua)
        ? "mobile_web"
        : "pc_web";

    logTossClick("click_outbound_deal", {
      button_name: "buy_now_button",
      deal_id: dealId ?? 0,
      currency: (currency || "KRW").toUpperCase(),
    });

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
