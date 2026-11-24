import type {
  Deal,
  DealSource,
  DealOptionItem,
  RawDealApiResponse,
  WebDeviceAuth,
  WishlistApiItem,
} from "./types";
import { hasTossBridge, isTossMiniAppEnv, logTossClick } from "./tossBridge";

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
  카카오톡딜: "카카오쇼핑",
  카카오선물하기: "카카오쇼핑",
  cj온: "CJ온스타일",
  cjon: "CJ온스타일",
  cj온스타일: "CJ온스타일",
  엑박스토어: "엑스박스",
  xbox스토어: "엑스박스",
  엑박: "엑스박스",
  xbox: "엑스박스",
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
    [/(?:store|gift|talkdeal|shopping)\.kakao\.com/, "카카오쇼핑"],
    [/(?:^|\/\/|\.)cjonstyle\.com/, "CJ온스타일"],
    [/(?:^|\/\/|\.)xbox\.com/, "엑스박스"],
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
    cleanTitle.match(/([0-9]+(?:\.[0-9]+)?)(?:\s*(?:달러|USD|불)(?![가-힣])|\$(?!\d))/i) ||
    (/(?:직배|직구|해외직구|아마존|알리|뉴에그|베스트바이)/i.test(cleanTitle)
      ? cleanTitle.match(/[\(\[]\s*([0-9]{1,4}\.[0-9]{1,2})\s*(?:\/|직배)/i)
      : null);
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

  const priceCleanTitle = cleanTitle.replace(
    /(?:배송\s*비|택배\s*비|우편\s*비|유료\s*배송|유배)\s*[:：=]?\s*[0-9,]+\s*원?/gi,
    " "
  );
  const slashShipMatch = priceCleanTitle.match(
    /(?:^|[\s/])([1-9][0-9]{0,2}(?:,[0-9]{3})+|[1-9][0-9]{3,6})\s*원?\s*\/\s*(?:무배|무료배송|무료|[1-9][0-9]{0,1},?[0-9]{3}\s*원?)\s*$/i
  );
  if (slashShipMatch && slashShipMatch[1]) {
    const val = parseInt(slashShipMatch[1].replace(/,/g, ""), 10);
    if (val >= 1000 && val <= 30000000) {
      return { price: String(val), currency: "KRW" };
    }
  }

  const wonMatches = Array.from(
    priceCleanTitle.matchAll(
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

// 제목 기반 15종 표준 카테고리 분류
export const categorizeDeal = (title: string): string => {
  const t = decodeHtmlEntities(title).toLowerCase();
  // 애플TV+/디즈니+/넷플릭스/티빙/웨이브 VOD 이용권 가드 (육아/가전 오탐 선제 차단)
  if (/(?:애플\s*티비|애플\s*tv|apple\s*tv)/i.test(t)) {
    if (/(?:4k|셋톱|스트리밍\s*박스|a15|64gb|128gb|리모컨|이더넷)/i.test(t)) {
      return "PC용품";
    }
    return "상품권";
  }
  if (
    /(?:디즈니\+|넷플릭스|티빙|웨이브|vod|영화)/i.test(t) &&
    !/(?:레고|장난감|피규어|유모차|카시트|기저귀)/i.test(t)
  ) {
    return "상품권";
  }
  // MVNO 알뜰폰 요금제 가드
  if (
    /(?:알뜰폰|알뜰요금제|이야기모바일|프리티|유모바일|스노우맨|kg모바일|kt\s*m모바일|kt엠모바일|세븐모바일|리브엠|이지모바일|에이모바일|아이즈모바일|티플러스|헬로모바일|모나\s*5g|모나\s*lte|(?:lg|kt|skt|u\+|유플러스)망)/i.test(
      t
    )
  ) {
    return "상품권";
  }
  if (
    /pc|컴퓨터|그래픽|모니터|ssd|hdd|ram|램|메모리|키보드|마우스|노트북|글카|rtx|보드|cpu|인텔|amd|라이젠|지포스|로지텍|공유기|nas|웹캠|헤드셋/i.test(
      t
    )
  ) {
    return "PC용품";
  }
  if (
    /스팀|steam|에픽|게임|닌텐도|스위치|ps5|플레이스테이션|xbox|엑스박스|엑박|소프트웨어|윈도우|오피스|디지털코드|gog/i.test(
      t
    )
  ) {
    return "SW/게임";
  }
  if (/(?:아가드|구름목욕시간|색깔목욕시간|목욕시간|목욕놀이|유아입욕제)/i.test(t)) {
    return "생활용품";
  }
  if (
    /기저귀|분유|물티슈|아기|유아|육아|유모차|카시트|젖병|장난감|레고/i.test(
      t
    )
  ) {
    return "육아/유아용품";
  }
  if (
    /tv|티비|냉장고|청소기|세탁기|건조기|선풍기|에어컨|가습기|블렌더|스타일러|공기청정기|안마|로봇청소기|다이슨|lg|삼성|전자레인지|에어프라이어|커피머신/i.test(
      t
    )
  ) {
    return "가전제품";
  }
  if (
    /상품권|컬쳐|해피머니|도서문화|북앤라이프|문화상품권|온누리/i.test(
      t
    )
  ) {
    return "상품권";
  }
  const hasApparelOrFoodHint =
    /나이키|아디다스|티셔츠|바지|신발|운동화|자켓|의류|패딩|모자|양말|구두|슬리퍼|가방|팬츠|아식스|뉴발|무신사|후디|후드티|스웻셔츠|스웨트셔츠|타미진스|h패션|방한화|털슬리퍼|패딩부츠|밸롭|물|생수|라면|커피|치킨|피자|콜라|제로|비타민|유산균|햇반|고기|돼지|소고기|과자|두유|우유|닭|스팸|참치|만두|볶음밥|삼다수|사이다|펩시|오레오|프로틴/i.test(
      t
    );
  if (
    !hasApparelOrFoodHint &&
    /적립|포인트|페이백|캐시백|출석체크|리워드|네이버페이/i.test(t)
  ) {
    return "적립";
  }
  if (/이벤트|응모|추첨|무료나눔|무료배포|체험단/i.test(t)) {
    return "이벤트";
  }
  if (
    /스마트폰|아이폰|갤럭시|충전|보조배터리|이어폰|버즈|에어팟|헤드폰|애플워치|워치|태블릿|패드|아이패드|케이스|케이블|기프티콘|기프트카드|편의점|cu|gs25/i.test(
      t
    )
  ) {
    return "모바일/기프티콘";
  }
  if (
    /스킨|로션|선크림|샴푸|린스|바디워시|클렌징|화장품|향수|마스크팩|올리브영|립/i.test(
      t
    )
  ) {
    return "뷰티";
  }
  if (
    /나이키|아디다스|티셔츠|바지|신발|운동화|자켓|의류|패딩|모자|양말|구두|슬리퍼|가방|팬츠|아식스|뉴발|무신사|후디|후드티|스웻셔츠|스웨트셔츠|타미진스|h패션|방한화|털슬리퍼|패딩부츠|밸롭/i.test(
      t
    )
  ) {
    return "패션";
  }
  if (
    /물|생수|라면|커피|치킨|피자|콜라|제로|비타민|유산균|햇반|고기|돼지|소고기|과자|두유|우유|닭|스팸|참치|만두|볶음밥|삼다수|사이다|펩시|오레오|프로틴/i.test(
      t
    )
  ) {
    return "음식";
  }
  if (
    /휴지|세제|섬유유연제|칫솔|치약|건전지|수건|쓰레기봉투|매트|이불|베개/i.test(
      t
    )
  ) {
    return "생활용품";
  }
  if (
    /해외직구|직구|아마존|amazon|알리익스프레스|aliexpress|타오바오|아이허브|iherb|큐텐|테무|temu|뉴에그/i.test(
      t
    )
  ) {
    return "해외직구";
  }
  if (/모음전|총정리|핫딜모음|브랜드데이/i.test(t)) {
    return "핫딜모음";
  }
  return "기타";
};

export const PLATFORMS = [
  "전체",
  "펨코",
  "뽐뿌",
  "뽐뿌해외",
  "루리웹",
  "퀘이사존",
  "클리앙",
  "알리뽐뿌",
  "빠삭국내",
  "빠삭해외",
  "빠삭육아",
] as const;

function isPrivateOrLoopbackHost(hostname: string): boolean {
  const host = hostname.toLowerCase().replace(/\.$/, "");
  if (!host) return true;

  if (
    host === "localhost" ||
    host.endsWith(".localhost") ||
    host === "0.0.0.0" ||
    host === "::1" ||
    host === "[::1]" ||
    host.endsWith(".local") ||
    host.endsWith(".internal")
  ) {
    return true;
  }

  if (
    /^0\./.test(host) ||
    /^127\./.test(host) ||
    /^10\./.test(host) ||
    /^169\.254\./.test(host) ||
    /^192\.168\./.test(host) ||
    /^172\.(?:1[6-9]|2\d|3[01])\./.test(host)
  ) {
    return true;
  }

  const bareHost = host.replace(/^\[|\]$/g, "");
  if (
    bareHost === "::1" ||
    bareHost === "::" ||
    /^fe[89ab][0-9a-f]:/i.test(bareHost) ||
    /^f[cd][0-9a-f]{2}:/i.test(bareHost) ||
    /^::ffff:/i.test(bareHost)
  ) {
    return true;
  }

  return false;
}

export const isSafeHttpUrl = (rawUrl?: string | null): boolean => {
  if (!rawUrl || typeof rawUrl !== "string") return false;
  const trimmed = rawUrl.trim();
  if (!trimmed || trimmed.length > 2048) return false;
  if (/[\x00-\x1f\x7f\s<>"'`\\]/.test(trimmed)) return false;

  // M1 백엔드 프록시 정규화 상대경로 허용 (/api/proxy-image?...)
  if (trimmed.startsWith("/api/proxy-image?")) {
    const qIdx = trimmed.indexOf("?");
    const pathPart = trimmed.slice(0, qIdx);
    const queryPart = trimmed.slice(qIdx + 1);
    if (pathPart !== "/api/proxy-image" || !queryPart) {
      return false;
    }
    try {
      const params = new URLSearchParams(queryPart);
      for (const key of params.keys()) {
        if (key.toLowerCase() === "url" && key !== "url") {
          return false;
        }
      }
      const urls = params.getAll("url");
      if (urls.length !== 1) {
        return false;
      }
      const rawProxiedUrl = urls[0];
      const proxiedUrl = rawProxiedUrl.trim();
      if (
        !proxiedUrl ||
        proxiedUrl !== rawProxiedUrl ||
        !/^https?:\/\//i.test(proxiedUrl) ||
        !isSafeHttpUrl(proxiedUrl)
      ) {
        return false;
      }
    } catch {
      return false;
    }
    return true;
  }

  let parsed: URL;
  try {
    parsed = new URL(trimmed);
  } catch {
    return false;
  }

  if (parsed.protocol !== "http:" && parsed.protocol !== "https:") {
    return false;
  }
  if (parsed.username || parsed.password) {
    return false;
  }
  if (isPrivateOrLoopbackHost(parsed.hostname)) {
    return false;
  }
  if (/(?:^|\/)(?:spacer|pixel|blank|1x1)\.(?:gif|png|jpe?g)$/i.test(parsed.pathname)) {
    return false;
  }
  return true;
};

export const extractBodyImagesFromHtml = (
  rawHtml?: string | null
): string[] => {
  if (!rawHtml || typeof rawHtml !== "string") return [];
  const seen = new Set<string>();
  const results: string[] = [];

  const normalizeDedupKey = (urlStr: string): string => {
    if (urlStr.startsWith("/")) return urlStr;
    try {
      return new URL(urlStr).toString();
    } catch {
      return urlStr;
    }
  };

  const pushIfValid = (candidate: string) => {
    const decoded = decodeHtmlEntities(candidate).trim();
    if (!isSafeHttpUrl(decoded)) return;
    const dedupKey = normalizeDedupKey(decoded);
    if (seen.has(dedupKey)) return;
    seen.add(dedupKey);
    results.push(decoded);
  };

  const imgRegex = /<img[^>]+src=["']([^"']+)["']/gi;
  let match: RegExpExecArray | null = imgRegex.exec(rawHtml);
  while (match !== null) {
    pushIfValid(match[1]);
    match = imgRegex.exec(rawHtml);
  }

  const markerRegex = /\[상품이미지\s*:\s*((?:https?:\/\/|\/api\/proxy-image\?)[^\]\s]+)\]/gi;
  let markerMatch: RegExpExecArray | null = markerRegex.exec(rawHtml);
  while (markerMatch !== null) {
    pushIfValid(markerMatch[1]);
    markerMatch = markerRegex.exec(rawHtml);
  }

  return results.slice(0, 20);
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
  "seorincomputer.co.kr",
];

export const isBlockedCommunityUrl = (url: string): boolean => {
  const lower = url.toLowerCase();
  return BLOCKED_COMMUNITY_DOMAINS.some((domain) => lower.includes(domain));
};

const _KAKAOCDN_LOWRES_RE =
  /^(https?:\/\/search\d*\.kakaocdn\.net\/argon\/)(?:130x130(?:_\d+_[a-zA-Z0-9]+)?|200x200(?:_\d+_[a-zA-Z0-9]+)?|320x320(?:_\d+_[a-zA-Z0-9]+)?|0x\d+(?:_\d+_[a-zA-Z0-9]+)?|656x0(?:_\d+_[a-zA-Z0-9]+)?)(\/[^?\s]+)/i;

export const upgradeLowresImageUrl = (url: string): string => {
  if (!url) return url;
  return url.replace(
    _KAKAOCDN_LOWRES_RE,
    (_m, prefix: string, suffix: string) => `${prefix}600x0_65_wr${suffix}`
  );
};

export const getProxyImageUrl = (
  url: string | null | undefined,
  options?: { width?: number; quality?: number }
): string => {
  if (!url) return "";
  const trimmed = upgradeLowresImageUrl(url.trim());
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
  const trimmed = upgradeLowresImageUrl(rawUrl.trim());
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

/**
 * 1차 이미지(wsrv.nl 등) 로드 실패 시 /api/proxy-image 로 단 1회만 폴백을 시도한다.
 * 이미 /api/proxy-image 경로에서 실패한 경우 중복 404 왕복 없이 즉시 false를 반환한다.
 */
export const tryApplyFallbackProxyImage = (
  img: HTMLImageElement,
  rawUrl: string | null | undefined,
  primarySrc: string
): boolean => {
  const fallback = getFallbackProxyImageUrl(rawUrl);
  if (
    fallback &&
    fallback !== primarySrc &&
    !img.dataset.fallbackTried &&
    !primarySrc.includes("/api/proxy-image")
  ) {
    img.dataset.fallbackTried = "1";
    img.src = fallback;
    return true;
  }
  img.dataset.fallbackTried = "1";
  return false;
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
        return `https://mitem.auction.co.kr/vip?itemNo=${auctionMatch[1]}`;
      }

      const gmarketMatch = url.match(
        /item\.gmarket\.co\.kr\/Item\?.*?goodscode=([0-9]+)/i
      );
      if (gmarketMatch && gmarketMatch[1]) {
        return `https://mitem.gmarket.co.kr/vi/product/${gmarketMatch[1]}`;
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
        return `https://itempage3.auction.co.kr/DetailView.aspx?itemno=${mAuctionMatch[1]}`;
      }
      const mGmarketMatch = url.match(
        /m(?:item)?\.gmarket\.co\.kr\/vi\/product\/([0-9]+)/i
      );
      if (mGmarketMatch && mGmarketMatch[1]) {
        return `https://item.gmarket.co.kr/Item?goodscode=${mGmarketMatch[1]}`;
      }
    }
  } catch {
    return url;
  }

  // 이미 http:// 로 들어온 옥션/지마켓 URL도 HTTPS로 강제 업그레이드 (Mixed Content 방어)
  if (/^http:\/\/(?:mitem|itempage3)\.auction\.co\.kr/i.test(url) ||
      /^http:\/\/(?:mitem|m|item)\.gmarket\.co\.kr/i.test(url)) {
    return url.replace(/^http:\/\//i, "https://");
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

export const polishDealBodyText = (
  rawHtml: string | undefined | null,
  titleHint?: string | null
): string => {
  if (!rawHtml) return "";
  const normalizedTitle = titleHint
    ? decodeHtmlEntities(titleHint)
        .toLowerCase()
        .replace(/\[[^\]]*\]/g, "")
        .replace(/[^0-9a-z가-힣]/gi, "")
    : "";
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
    .filter((l, idx) => {
      if (!l || l === "▪") return false;
      if (/^\[상품이미지\s*:\s*https?:\/\/[^\]]+\]$/i.test(l)) return false;
      if (/^\[첨부된\s*이미지/i.test(l)) return false;
      if (/^(?:링크|바로가기|보기)\s*>{0,}$/i.test(l)) return false;
      if (/^(?:링크|구매\s*링크)\s*>{2,}/i.test(l)) return false;
      if (/^>{2,}/.test(l)) return false;
      if (/^링크오류시$/i.test(l)) return false;
      if (/^(?:게시글\s*본문은\s*원본\s*링크에서\s*확인|원본\s*게시글에서\s*확인|출처\s*:\s*https?:\/\/)/i.test(l)) {
        return false;
      }
      if (/^(?:갤럭시|아이폰)\s*:\s*.*(?:링크\s*꾹|알리익스프레스\s*앱)/i.test(l))
        return false;
      if (/^2\)\s*알리앱\s*꾹\s*눌러서/i.test(l)) return false;
      if (/^-\s*가격은\s*쿠폰\s*적용\s*전\s*가격을\s*적어주세요/i.test(l))
        return false;
      if (idx === 0 && normalizedTitle && normalizedTitle.length >= 6) {
        const compactLine = l
          .toLowerCase()
          .replace(/\[[^\]]*\]/g, "")
          .replace(/[^0-9a-z가-힣]/gi, "");
        if (
          compactLine &&
          (compactLine === normalizedTitle ||
            (compactLine.includes(normalizedTitle) && compactLine.length <= normalizedTitle.length + 12))
        ) {
          return false;
        }
      }
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

const DEVICE_UUID_KEY = "insightdeal_web_device_uuid";
const LEGACY_DEVICE_UUID_KEY = "insightdeal_token";
const DEVICE_TOKEN_KEY = "insightdeal_web_device_token";
const ACCOUNT_TOKEN_KEY = "insightdeal_web_account_token";
const ACCOUNT_USER_KEY = "insightdeal_web_account_user";
export const WISHLIST_ID_MAP_KEY = "insightdeal_wishlist_id_map";

export interface WishlistIdEntry {
  wishlistId: number;
  keyword: string;
}

export type WebPushSupportStatus =
  | "supported"
  | "in_app_webview"
  | "toss_miniapp"
  | "unsupported";

export interface EnsureWebPushResult {
  deviceUuid: string;
  deviceToken?: string;
  pushGranted: boolean;
  reason?:
    | "unsupported"
    | "in_app_webview"
    | "toss_miniapp"
    | "denied"
    | "vapid_unavailable"
    | "register_failed"
    | "auth_failed"
    | "subscribe_failed";
}

let deviceAuthInFlight: Promise<WebDeviceAuth> | null = null;
let forceRefreshInFlight: Promise<WebDeviceAuth> | null = null;

export function urlBase64ToUint8Array(
  base64String: string
): Uint8Array<ArrayBuffer> {
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

export const isInAppWebView = (): boolean => {
  if (typeof navigator === "undefined") return false;
  if (isTossMiniAppEnv()) return true;
  const ua = navigator.userAgent || "";
  return /KAKAOTALK|Instagram|NAVER|FBAN|FBAV|Line\/|DaumApps|Whale\/.*Mobile/i.test(
    ua
  );
};

export const isExternalInAppWebView = (): boolean => {
  if (typeof navigator === "undefined") return false;
  if (isTossMiniAppEnv()) return false;
  return isInAppWebView();
};

export const isIosUserAgent = (): boolean => {
  if (typeof navigator === "undefined") return false;
  return /iPhone|iPad|iPod/i.test(navigator.userAgent || "");
};

export const buildExternalBrowserIntentUrl = (
  targetUrl?: string
): string | null => {
  if (typeof window === "undefined") return null;
  if (isIosUserAgent()) return null;
  const raw = (targetUrl || window.location.href || "").trim();
  if (!/^https?:\/\//i.test(raw)) return null;
  try {
    const parsed = new URL(raw);
    const scheme = parsed.protocol.replace(":", "");
    const pathAndQuery = `${parsed.host}${parsed.pathname}${parsed.search}${parsed.hash}`;
    return `intent://${pathAndQuery}#Intent;scheme=${scheme};package=com.android.chrome;S.browser_fallback_url=${encodeURIComponent(
      parsed.toString()
    )};end`;
  } catch {
    return null;
  }
};

export const getWebPushSupportStatus = (): WebPushSupportStatus => {
  if (typeof window === "undefined") return "unsupported";
  if (isTossMiniAppEnv()) return "toss_miniapp";
  if (isInAppWebView()) return "in_app_webview";
  if (
    !("serviceWorker" in navigator) ||
    !("PushManager" in window) ||
    !("Notification" in window)
  ) {
    return "unsupported";
  }
  return "supported";
};

function generateRandomDeviceUuid(): string {
  const randomPart =
    typeof crypto !== "undefined" && typeof crypto.randomUUID === "function"
      ? crypto.randomUUID()
      : `${Date.now()}_${Math.random().toString(36).substring(2, 10)}`;
  return `web_${randomPart}`;
}

export const getOrCreateWebDeviceUuid = (): string => {
  if (typeof window === "undefined") return "web_guest";
  let uuid =
    localStorage.getItem(DEVICE_UUID_KEY) ||
    localStorage.getItem(LEGACY_DEVICE_UUID_KEY);
  if (!uuid || uuid === "web_guest") {
    uuid = generateRandomDeviceUuid();
  }
  localStorage.setItem(DEVICE_UUID_KEY, uuid);
  localStorage.setItem(LEGACY_DEVICE_UUID_KEY, uuid);
  return uuid;
};

export const getStoredWebDeviceToken = (): string => {
  if (typeof window === "undefined") return "";
  return (localStorage.getItem(DEVICE_TOKEN_KEY) || "").trim();
};

export const getDeviceToken = getStoredWebDeviceToken;

export interface StoredAccountUser {
  username: string;
  nickname: string;
  honey_points?: number;
}

export const getStoredAccountToken = (): string => {
  if (typeof window === "undefined") return "";
  return (localStorage.getItem(ACCOUNT_TOKEN_KEY) || "").trim();
};

export const getStoredAccountUser = (): StoredAccountUser | null => {
  if (typeof window === "undefined") return null;
  try {
    const raw = localStorage.getItem(ACCOUNT_USER_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as StoredAccountUser;
    return parsed && parsed.username ? parsed : null;
  } catch {
    return null;
  }
};

export const saveStoredAccountAuth = (
  accessToken: string,
  user: StoredAccountUser
): void => {
  if (typeof window === "undefined") return;
  try {
    localStorage.setItem(ACCOUNT_TOKEN_KEY, accessToken);
    localStorage.setItem(ACCOUNT_USER_KEY, JSON.stringify(user));
  } catch {}
};

export const clearStoredAccountAuth = (): void => {
  if (typeof window === "undefined") return;
  try {
    localStorage.removeItem(ACCOUNT_TOKEN_KEY);
    localStorage.removeItem(ACCOUNT_USER_KEY);
  } catch {}
};

function rotateNewWebDeviceUuid(): string {
  const freshUuid = generateRandomDeviceUuid();
  if (typeof window !== "undefined") {
    localStorage.setItem(DEVICE_UUID_KEY, freshUuid);
    localStorage.setItem(LEGACY_DEVICE_UUID_KEY, freshUuid);
    localStorage.removeItem(DEVICE_TOKEN_KEY);
    // 구 게정의 wishlistId 캐시가 새 세션에 남지 않도록 즉시 초기화
    localStorage.removeItem(WISHLIST_ID_MAP_KEY);
  }
  return freshUuid;
}

async function performDeviceAuth(forceFreshSession = false): Promise<WebDeviceAuth> {
  if (typeof window === "undefined") {
    throw new Error("브라우저 환경에서만 기기 인증을 수행할 수 있어요.");
  }

  let deviceUuid = forceFreshSession
    ? rotateNewWebDeviceUuid()
    : getOrCreateWebDeviceUuid();
  const existingToken = forceFreshSession ? "" : getStoredWebDeviceToken();

  if (!forceFreshSession && deviceUuid && existingToken) {
    return { deviceUuid, deviceToken: existingToken };
  }

  const registerOnce = async (
    targetUuid: string,
    tokenHeader?: string
  ): Promise<Response> => {
    const headers: Record<string, string> = {
      "Content-Type": "application/json",
    };
    if (tokenHeader) {
      headers["X-Device-Token"] = tokenHeader;
    }
    return fetch(apiUrl("/api/push/device"), {
      method: "POST",
      headers,
      // fcm_token은 미전달(또는 미설정)로 유지해 기존 토큰을 공백으로 덮어쓰지 않도록 보호
      body: JSON.stringify({
        device_uuid: targetUuid,
        platform: "web",
      }),
    });
  };

  let res = await registerOnce(deviceUuid);

  if (res.status === 503) {
    throw new Error(
      "서버 기기 인증 설정이 준비되지 않았어요. 잠시 후 다시 시도해 주세요."
    );
  }

  // 기존 UUID가 서버에 등록되어 있으나 로컬 토큰이 없거나 만료된 경우(401/403):
  // 동일 UUID 무한 재시도 대신 1회만 새 게스트 세션 UUID로 회전 등록
  if (res.status === 401 || res.status === 403) {
    deviceUuid = rotateNewWebDeviceUuid();
    res = await registerOnce(deviceUuid);
    if (res.status === 503) {
      throw new Error(
        "서버 기기 인증 설정이 준비되지 않았어요. 잠시 후 다시 시도해 주세요."
      );
    }
  }

  if (!res.ok) {
    throw new Error(`기기 인증에 실패했어요 (${res.status})`);
  }

  const data = (await res.json().catch(() => ({}))) as Record<string, unknown>;
  const resolvedUuid = String(
    data.device_uuid ?? data.deviceUuid ?? deviceUuid
  ).trim();
  const issuedToken = String(
    data.device_token ?? data.deviceToken ?? ""
  ).trim();

  if (!resolvedUuid || !issuedToken) {
    throw new Error("서버로부터 유효한 기기 인증 토큰을 받지 못했어요.");
  }

  localStorage.setItem(DEVICE_UUID_KEY, resolvedUuid);
  localStorage.setItem(LEGACY_DEVICE_UUID_KEY, resolvedUuid);
  localStorage.setItem(DEVICE_TOKEN_KEY, issuedToken);

  return { deviceUuid: resolvedUuid, deviceToken: issuedToken };
}

/**
 * 게스트 기기 식별자 및 HMAC X-Device-Token 확보 (비강제/강제 회전 In-Flight 분리 보장)
 */
export const ensureWebDeviceAuth = async (options?: {
  forceRefresh?: boolean;
}): Promise<WebDeviceAuth> => {
  if (typeof window === "undefined") {
    throw new Error("브라우저 환경에서만 기기 인증을 수행할 수 있어요.");
  }

  const forceRefresh = Boolean(options?.forceRefresh);
  if (!forceRefresh) {
    if (forceRefreshInFlight) {
      return forceRefreshInFlight;
    }
    const existingUuid =
      localStorage.getItem(DEVICE_UUID_KEY) ||
      localStorage.getItem(LEGACY_DEVICE_UUID_KEY);
    const existingToken = getStoredWebDeviceToken();
    if (existingUuid && existingUuid !== "web_guest" && existingToken) {
      return { deviceUuid: existingUuid, deviceToken: existingToken };
    }
    if (!deviceAuthInFlight) {
      deviceAuthInFlight = performDeviceAuth(false).finally(() => {
        deviceAuthInFlight = null;
      });
    }
    return deviceAuthInFlight;
  }

  // 강제 회전 요청은 진행 중인 비강제 deviceAuthInFlight 프라미스를 재사용하지 않고
  // 선행 요청 완료를 기다린 뒤 별도 forceRefreshInFlight로 새 세션을 발급한다.
  if (!forceRefreshInFlight) {
    localStorage.removeItem(DEVICE_TOKEN_KEY);
    const pendingNormal = deviceAuthInFlight;
    forceRefreshInFlight = (async () => {
      if (pendingNormal) {
        await pendingNormal.catch(() => null);
      }
      localStorage.removeItem(DEVICE_TOKEN_KEY);
      return performDeviceAuth(true);
    })().finally(() => {
      forceRefreshInFlight = null;
    });
  }
  return forceRefreshInFlight;
};

export type AuthFetchRequestBuilder = (
  auth: WebDeviceAuth
) => string | { pathOrUrl: string; init?: RequestInit };

function rewriteAuthRequestUuid(
  pathOrUrl: string,
  init: RequestInit,
  oldUuid: string,
  newUuid: string
): { url: string; requestInit: RequestInit } {
  let rewrittenUrl = pathOrUrl;
  if (oldUuid && newUuid && oldUuid !== newUuid) {
    try {
      const isAbsolute = /^https?:\/\//i.test(pathOrUrl);
      const parsed = new URL(pathOrUrl, "http://placeholder.local");
      let urlChanged = false;
      for (const paramKey of ["user_id", "device_uuid"]) {
        if (!parsed.searchParams.has(paramKey)) continue;
        const values = parsed.searchParams.getAll(paramKey);
        if (values.some((v) => v === oldUuid)) {
          parsed.searchParams.delete(paramKey);
          values.forEach((v) =>
            parsed.searchParams.append(paramKey, v === oldUuid ? newUuid : v)
          );
          urlChanged = true;
        }
      }
      if (urlChanged) {
        rewrittenUrl = isAbsolute
          ? parsed.toString()
          : `${parsed.pathname}${parsed.search}${parsed.hash}`;
      }
    } catch {
      // URL 파싱 실패 시 원본 유지
    }
  }

  let nextInit: RequestInit = init;
  if (
    oldUuid &&
    newUuid &&
    oldUuid !== newUuid &&
    typeof init.body === "string" &&
    init.body.trim().startsWith("{")
  ) {
    try {
      const parsedBody = JSON.parse(init.body) as Record<string, unknown>;
      if (
        parsedBody &&
        typeof parsedBody === "object" &&
        !Array.isArray(parsedBody)
      ) {
        let bodyChanged = false;
        if (parsedBody.user_id === oldUuid) {
          parsedBody.user_id = newUuid;
          bodyChanged = true;
        }
        if (parsedBody.device_uuid === oldUuid) {
          parsedBody.device_uuid = newUuid;
          bodyChanged = true;
        }
        if (bodyChanged) {
          nextInit = { ...init, body: JSON.stringify(parsedBody) };
        }
      }
    } catch {
      // JSON 파싱 실패 시 원본 바디 유지
    }
  }

  return { url: rewrittenUrl, requestInit: nextInit };
}

/**
 * X-Device-Token 자동 첨부 인증 fetch 래퍼 (401 수신 시 1회 세션 재인증 + UUID 정합화 후 재시도)
 */
export const authFetch = async (
  pathOrUrlOrBuilder: string | AuthFetchRequestBuilder,
  init: RequestInit = {}
): Promise<Response> => {
  const auth = await ensureWebDeviceAuth();

  const resolveRequest = (
    currentAuth: WebDeviceAuth
  ): { url: string; requestInit: RequestInit } => {
    if (typeof pathOrUrlOrBuilder === "function") {
      const built = pathOrUrlOrBuilder(currentAuth);
      if (typeof built === "string") {
        return { url: built, requestInit: init };
      }
      return { url: built.pathOrUrl, requestInit: built.init ?? {} };
    }
    return { url: pathOrUrlOrBuilder, requestInit: init };
  };

  const firstReq = resolveRequest(auth);
  const headers = new Headers(firstReq.requestInit.headers);
  headers.set("X-Device-Token", auth.deviceToken);

  let res = await fetch(apiUrl(firstReq.url), {
    ...firstReq.requestInit,
    headers,
  });

  if (res.status === 401) {
    const currentToken = getStoredWebDeviceToken();
    const currentUuid =
      typeof window !== "undefined"
        ? localStorage.getItem(DEVICE_UUID_KEY) ||
          localStorage.getItem(LEGACY_DEVICE_UUID_KEY) ||
          ""
        : "";

    const refreshed =
      currentToken &&
      currentUuid &&
      currentUuid !== "web_guest" &&
      currentToken !== auth.deviceToken
        ? { deviceUuid: currentUuid, deviceToken: currentToken }
        : await ensureWebDeviceAuth({ forceRefresh: true });

    let retryReq = resolveRequest(refreshed);
    if (
      typeof pathOrUrlOrBuilder !== "function" &&
      refreshed.deviceUuid !== auth.deviceUuid
    ) {
      retryReq = rewriteAuthRequestUuid(
        retryReq.url,
        retryReq.requestInit,
        auth.deviceUuid,
        refreshed.deviceUuid
      );
    }

    const retryHeaders = new Headers(retryReq.requestInit.headers);
    retryHeaders.set("X-Device-Token", refreshed.deviceToken);
    res = await fetch(apiUrl(retryReq.url), {
      ...retryReq.requestInit,
      headers: retryHeaders,
    });
  }

  return res;
};

/**
 * 서버 VAPID 공개키 조회 (하드코딩 샘플 키 폴백 완전 제거, JSON/텍스트 양립 파싱)
 */
export const fetchVapidPublicKey = async (): Promise<string> => {
  const envKey = (process.env.NEXT_PUBLIC_VAPID_PUBLIC_KEY || "").trim();
  if (envKey && envKey.length >= 64) {
    return envKey;
  }

  const res = await fetch(apiUrl("/api/push-public-key"), {
    cache: "no-store",
  });
  if (!res.ok) {
    throw new Error(`VAPID 공개키를 불러오지 못했어요 (${res.status})`);
  }

  const rawText = (await res.text()).trim();
  if (!rawText) {
    throw new Error("VAPID 공개키 응답이 비어 있어요.");
  }

  let extracted = rawText;
  if (rawText.startsWith("{")) {
    try {
      const parsed = JSON.parse(rawText) as {
        public_key?: string;
        publicKey?: string;
      };
      extracted = String(parsed.public_key || parsed.publicKey || "").trim();
    } catch {
      extracted = "";
    }
  } else {
    extracted = rawText.replace(/^"+|"+$/g, "").trim();
  }

  if (!extracted || extracted.length < 32 || extracted.includes("...")) {
    throw new Error("유효한 VAPID 공개키가 설정되지 않았어요.");
  }
  return extracted;
};

/**
 * Workbox /pwa-sw.js가 이미 등록된 프로덕션 환경에서는 기존 등록을 재사용하고,
 * 미등록(개발 환경 등)일 때만 /sw.js를 등록해 캐싱 SW 덮어쓰기를 방지합니다.
 */
export const registerOrReusePushServiceWorker =
  async (): Promise<ServiceWorkerRegistration> => {
    const existing = await navigator.serviceWorker.getRegistration("/");
    if (existing) {
      const scriptUrl =
        existing.active?.scriptURL ||
        existing.waiting?.scriptURL ||
        existing.installing?.scriptURL ||
        "";
      if (scriptUrl.includes("pwa-sw.js") || scriptUrl.includes("/sw.js")) {
        return existing;
      }
    }
    return navigator.serviceWorker.register("/sw.js");
  };

export const ensureWebPushSubscribed =
  async (): Promise<EnsureWebPushResult> => {
    let auth: WebDeviceAuth;
    try {
      auth = await ensureWebDeviceAuth();
    } catch {
      return {
        deviceUuid: getOrCreateWebDeviceUuid(),
        pushGranted: false,
        reason: "auth_failed",
      };
    }

    const support = getWebPushSupportStatus();
    if (support !== "supported") {
      return {
        deviceUuid: auth.deviceUuid,
        deviceToken: auth.deviceToken,
        pushGranted: false,
        reason: support,
      };
    }

    try {
      let permission = Notification.permission;
      if (permission === "default") {
        permission = await Notification.requestPermission();
      }
      if (permission !== "granted") {
        return {
          deviceUuid: auth.deviceUuid,
          deviceToken: auth.deviceToken,
          pushGranted: false,
          reason: "denied",
        };
      }

      const registration = await registerOrReusePushServiceWorker();
      await navigator.serviceWorker.ready;

      let subscription = await registration.pushManager.getSubscription();
      if (!subscription) {
        let vapidKey = "";
        try {
          vapidKey = await fetchVapidPublicKey();
        } catch {
          return {
            deviceUuid: auth.deviceUuid,
            deviceToken: auth.deviceToken,
            pushGranted: false,
            reason: "vapid_unavailable",
          };
        }

        subscription = await registration.pushManager.subscribe({
          userVisibleOnly: true,
          applicationServerKey: urlBase64ToUint8Array(vapidKey),
        });
      }

      const subJson = subscription.toJSON();
      const registerPath = API_BASE_URL
        ? "/api/push/register-web"
        : "/api/push-register";
      const regRes = await authFetch((latestAuth) => ({
        pathOrUrl: registerPath,
        init: {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            device_uuid: latestAuth.deviceUuid,
            endpoint: subJson.endpoint,
            keys: subJson.keys,
          }),
        },
      }));

      const latestAuth = await ensureWebDeviceAuth().catch(() => auth);

      if (!regRes.ok) {
        return {
          deviceUuid: latestAuth.deviceUuid,
          deviceToken: latestAuth.deviceToken,
          pushGranted: false,
          reason: "register_failed",
        };
      }

      return {
        deviceUuid: latestAuth.deviceUuid,
        deviceToken: latestAuth.deviceToken,
        pushGranted: true,
      };
    } catch {
      return {
        deviceUuid: auth.deviceUuid,
        deviceToken: auth.deviceToken,
        pushGranted: false,
        reason: "subscribe_failed",
      };
    }
  };

/**
 * 명시적 타임존 ISO-8601 타임스탬프 파싱 (+9h 인위 보정 제거)
 */
export function parseIsoTimestampMs(
  dateInput?: string | number | null
): number | null {
  if (dateInput === null || dateInput === undefined || dateInput === "") {
    return null;
  }
  if (typeof dateInput === "number") {
    return Number.isFinite(dateInput) ? dateInput : null;
  }

  const trimmed = String(dateInput).trim();
  if (!trimmed) return null;

  if (/^\d{10,13}$/.test(trimmed)) {
    const numeric = Number(trimmed);
    return trimmed.length === 10 ? numeric * 1000 : numeric;
  }

  let isoStr = trimmed.replace(" ", "T");
  if (!/(?:[zZ]|[+-]\d{2}:?\d{2})$/.test(isoStr)) {
    isoStr += "Z";
  }

  const ms = Date.parse(isoStr);
  return Number.isNaN(ms) ? null : ms;
}

export function formatRelativeTimeKo(
  dateInput?: string | number | null,
  nowMs: number = Date.now(),
  fallbackLabel = "방금 전"
): string {
  const targetMs = parseIsoTimestampMs(dateInput);
  if (targetMs === null) {
    return fallbackLabel;
  }

  const diffMs = Math.max(0, nowMs - targetMs);
  const diffMin = Math.floor(diffMs / 60000);
  if (diffMin < 1) return "방금 전";
  if (diffMin < 60) return `${diffMin}분 전`;

  const diffHour = Math.floor(diffMin / 60);
  if (diffHour < 24) return `${diffHour}시간 전`;

  const diffDay = Math.floor(diffHour / 24);
  if (diffDay < 7) return `${diffDay}일 전`;

  const d = new Date(targetMs);
  return `${d.getMonth() + 1}.${d.getDate()}`;
}

export function getCommunityName(id?: number): string {
  if (!id) return "핫딜";
  const map: Record<number, string> = {
    1: "뽐뿌",
    2: "루리웹",
    3: "클리앙",
    4: "알리뽐뿌",
    5: "퀘이사존",
    6: "펨코",
    7: "빠삭국내",
    8: "빠삭해외",
    9: "빠삭육아",
    10: "뽐뿌해외",
  };
  return map[id] || "핫딜";
}

/**
 * 백엔드 RawDealApiResponse(목록/상세 1-RTT/이중 키) -> 프론트 Deal 정규화 매핑
 */
export function mapRawDealToDeal(
  d: RawDealApiResponse,
  targetPlatform?: string | string[]
): Deal {
  const titleStr = decodeHtmlEntities(d.title || "");
  const cleanEcommerce =
    d.ecommerce_url && d.ecommerce_url.trim() !== ""
      ? d.ecommerce_url.trim()
      : null;
  const cleanPost =
    d.post_url && d.post_url.trim() !== "" ? d.post_url.trim() : null;
  const cleanPostLink =
    d.post_link && d.post_link.trim() !== "" ? d.post_link.trim() : null;
  const cleanLink = d.link && d.link.trim() !== "" ? d.link.trim() : null;

  const activePlatforms = Array.isArray(targetPlatform)
    ? targetPlatform.map((p) => p.trim()).filter((p) => p && p !== "전체")
    : typeof targetPlatform === "string"
      ? targetPlatform
          .split(",")
          .map((p) => p.trim())
          .filter((p) => p && p !== "전체")
      : [];

  const matchedSource =
    activePlatforms.length > 0 && Array.isArray(d.sources)
      ? d.sources.find(
          (s: DealSource) =>
            activePlatforms.some((plat) =>
              String(s.site_name || "").includes(plat)
            ) && Boolean(s.post_url)
        )
      : null;

  const finalEcommerce = cleanEcommerce || cleanPostLink || cleanLink || "#";
  const finalPost =
    (matchedSource && matchedSource.post_url) ||
    cleanPost ||
    cleanPostLink ||
    cleanLink ||
    "#";

  const rawPriceNum = Number(String(d.price ?? "").replace(/[^0-9.-]/g, ""));
  const isExplicitFree = Boolean(d.is_free ?? d.isFree);
  const titleFallback =
    !isExplicitFree &&
    (d.price === null ||
      d.price === undefined ||
      String(d.price).trim() === "" ||
      !Number.isFinite(rawPriceNum) ||
      rawPriceNum <= 0)
      ? extractPriceFromTitle(titleStr)
      : null;

  const rawShipping = d.shipping_fee || d.shippingFee || null;
  const shippingStr = rawShipping ? decodeHtmlEntities(rawShipping) : null;
  const optionsDataArr = Array.isArray(d.options_data)
    ? d.options_data
    : Array.isArray(d.optionsData)
      ? d.optionsData
      : undefined;

  const resolvedShop = resolveDealShopName({
    shop_name: d.shop_name ?? d.shopName,
    title: titleStr,
    ecommerce_url: finalEcommerce,
  });

  const rawCategory =
    d.category && d.category.includes("해외핫딜") ? "해외직구" : d.category || "";

  return {
    id: d.id,
    community_name:
      d.site_name ||
      (d.site_names && d.site_names[0]) ||
      d.community_name ||
      d.communityName ||
      getCommunityName(d.source_community_id),
    shop_name: resolvedShop || undefined,
    title: titleStr,
    price: isExplicitFree
      ? "0"
      : titleFallback
        ? titleFallback.price
        : d.price !== null &&
            d.price !== undefined &&
            String(d.price).trim() !== ""
          ? String(d.price)
          : "0",
    image_url: d.image_url || d.imageUrl || null,
    scraped_at: d.scraped_at || d.indexed_at || d.timeAgo || "방금 전",
    post_link: finalPost,
    ecommerce_url: finalEcommerce,
    shipping_fee: shippingStr,
    category: rawCategory || categorizeDeal(titleStr),
    isClosed: Boolean(d.is_closed ?? d.isClosed),
    originalDate: d.created_at || d.originalDate || d.indexed_at || undefined,
    currency: titleFallback
      ? titleFallback.currency
      : d.currency || "KRW",
    honeyScore: Number(d.honey_score ?? d.honeyScore ?? d.score ?? 0),
    isSuperHotdeal: Boolean(d.is_super_hotdeal),
    likeCount: d.like_count ?? d.buy_count ?? d.buyCount ?? undefined,
    aiSummary: decodeHtmlEntities(d.ai_summary || d.aiSummary || ""),
    contentHtml: d.content_html || d.contentHtml || "",
    sources: d.sources || [],
    tags: Array.isArray(d.tags)
      ? d.tags.map((t) => decodeHtmlEntities(String(t))).filter(Boolean)
      : [],
    has_options: Boolean(d.has_options ?? d.hasOptions),
    options_count:
      d.options_count ?? (optionsDataArr ? optionsDataArr.length : undefined),
    options_data: optionsDataArr,
    buyCount:
      d.buy_count ?? d.buyCount ?? undefined,
    passCount:
      d.pass_count ??
      d.passCount ??
      d.save_count ??
      d.saveCount ??
      undefined,
    userVote: d.user_vote ?? d.userVote ?? null,
    commentCount:
      d.comment_count ?? d.commentCount ?? d.comments ?? undefined,
    appCommentCount:
      d.app_comment_count ?? d.appCommentCount ?? undefined,
    comments:
      d.comments ?? d.comment_count ?? d.commentCount ?? undefined,
    recommendations:
      d.recommendations ?? d.buy_count ?? d.buyCount ?? d.like_count ?? undefined,
    historyCount: d.history_count ?? d.historyCount ?? undefined,
    isSearchFallback: Boolean(d.is_search_fallback ?? d.isSearchFallback),
    is_search_fallback: Boolean(d.is_search_fallback ?? d.isSearchFallback),
    isFree: isExplicitFree,
    bodyImages: (() => {
      const rawImgs = Array.isArray(d.body_images)
        ? d.body_images
        : Array.isArray(d.bodyImages)
          ? d.bodyImages
          : [];
      return rawImgs.filter((img) => isSafeHttpUrl(img));
    })(),
    reportCount: Number(d.report_count ?? d.reportCount ?? 0),
  };
}

/**
 * 찜 / 목표가 알림 공통 키워드 정규화
 */
export function buildDealWishlistKeyword(
  dealTitle: string,
  optionTitle?: string
): string {
  const source = optionTitle || dealTitle || "";
  const cleaned = decodeHtmlEntities(source)
    .replace(/\[[^\]]*\]/g, "")
    .replace(/\([^)]*\)/g, "")
    .replace(/\s+/g, " ")
    .trim();
  const fallback = decodeHtmlEntities(source).trim();
  return (cleaned || fallback).slice(0, 50);
}

export function loadWishlistIdMap(): Record<string, WishlistIdEntry> {
  if (typeof window === "undefined") return {};
  try {
    const raw = localStorage.getItem(WISHLIST_ID_MAP_KEY);
    if (!raw) return {};
    const parsed = JSON.parse(raw) as unknown;
    if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
      return {};
    }
    return parsed as Record<string, WishlistIdEntry>;
  } catch {
    return {};
  }
}

export function saveWishlistIdEntry(
  key: string | number,
  wishlistId: number,
  keyword: string
): void {
  if (typeof window === "undefined" || !Number.isFinite(wishlistId)) return;
  try {
    const map = loadWishlistIdMap();
    map[String(key)] = { wishlistId, keyword };
    localStorage.setItem(WISHLIST_ID_MAP_KEY, JSON.stringify(map));
  } catch {}
}

export function removeWishlistIdEntry(key: string | number): void {
  if (typeof window === "undefined") return;
  try {
    const map = loadWishlistIdMap();
    delete map[String(key)];
    localStorage.setItem(WISHLIST_ID_MAP_KEY, JSON.stringify(map));
  } catch {}
}

/**
 * 로컬 ID 맵에 없을 때 서버 GET /api/wishlist/에서 keyword 일치 항목 ID를 조회
 */
export async function resolveServerWishlistId(
  localKey: string | number,
  keyword: string,
  deviceUuid: string,
  altKeywords: string[] = []
): Promise<number | null> {
  const map = loadWishlistIdMap();
  const cached = map[String(localKey)];
  if (cached && Number.isFinite(cached.wishlistId) && cached.wishlistId > 0) {
    return cached.wishlistId;
  }

  try {
    const res = await authFetch(
      (auth) => `/api/wishlist/?user_id=${encodeURIComponent(auth.deviceUuid)}`
    );
    if (!res.ok) return null;
    const list = (await res.json()) as WishlistApiItem[];
    if (!Array.isArray(list)) return null;

    const candidates = new Set(
      [keyword, ...altKeywords]
        .map((k) => decodeHtmlEntities(k).trim().toLowerCase())
        .filter(Boolean)
    );

    const matched = list.find((item) => {
      const k = decodeHtmlEntities(item.keyword || "").trim().toLowerCase();
      return candidates.has(k);
    });
    if (matched && Number.isFinite(matched.id)) {
      saveWishlistIdEntry(localKey, matched.id, matched.keyword);
      return matched.id;
    }
  } catch {}

  return null;
}

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

const _UNIT_PRICE_EXCLUDED_CATEGORIES = new Set([
  "PC/하드웨어",
  "PC용품",
  "SW/게임",
  "가전/TV",
  "가전제품",
  "상품권",
  "적립/포인트",
  "적립",
  "이벤트",
  "모바일/기프티콘",
]);

const _UNIT_PRICE_TECH_SPEC_RE =
  /(?:^|[\s(/\[,])(?:\d+\s*(?:gb|tb|mb|ghz|mhz|hz|mah|wh|w|인치|inch)|rtx\s*\d+|gtx\s*\d+|rx\s*\d+|ddr[45]|nvme|ssd|hdd|모니터|노트북|그래픽카드|메인보드|키보드|마우스|이어폰|헤드셋|충전기|케이블|보조배터리|스마트워치|스마트글래스|스마트\s*글래스|오디오\s*안경|블루투스|스피커|태블릿|아이패드|갤럭시탭|맥북|멀티탭|콘센트|완본체)(?=$|[\s)/\],])/i;

const _EGG_ITEM_HINT_RE =
  /(?:계란|달걀|구운란|반숙|훈제란|메추리|초란|유정란|특란|대란|왕란|중란|동물복지란)/i;
const _APPLIANCE_OUTLET_RE =
  /(?:멀티탭|콘센트|어댑터|충전기|인덕션|하이라이트|가스레인지|포트|허브|스위치|전등|조명|소켓|usb|hdmi)/i;
const _NUTRITION_WORD_RE =
  /(?:당류|당|단백질|프로틴|단백|지방|포화지방|트랜스지방|탄수화물|나트륨|식이섬유|콜라겐|카페인|타우린|아르기닌|유산균|비타민|칼슘|마그네슘|오메가|함량|함유|저당|무당|고단백)\s*[:：]?\s*$/i;
const _PROTEIN_HINT_RE =
  /(?:프로틴|단백질|하이뮨|셀렉스|더단백|테이크핏|마이프로틴|닥터유\s*프로)/i;
const _FASHION_CATEGORY_RE = /^(?:패션|패션\/의류|의류|패션\/잡화)$/;
const _NON_LIQUID_VOLUME_RE =
  /(?:쓰레기봉투|종량제|리빙박스|수납함|정리함|백팩|배낭|캐리어|밀폐용기|냉동용기|보관용기|반찬통|텀블러|보온병|에어프라이어|전자레인지|오븐|냉장고|김치냉장고|세탁기|건조기)/i;
const _BEAUTY_HINT_RE =
  /(?:세럼|에센스|앰플|크림|선크림|립밤|아이크림|쿠션|파운데이션|틴트|미스트|토너|로션|향수|오일|마스크팩)/i;

export interface UnitPriceBadgeInfo {
  label: string;
  detail: string;
}

const _VOUCHER_TITLE_RE =
  /(?:상품권|컬쳐랜드|컬처랜드|해피머니|북앤라이프|기프트카드|금액권)/;
const _VOUCHER_GIFT_ATTACHED_RE =
  /\+\s*[^+]{0,16}(?:상품권|컬쳐|컬처|해피머니|기프트|금액권)/;
const _VOUCHER_MAN_FACE_RE =
  /(?:^|[\s(\[,·])(\d{1,2})\s*만\s*(?:원\s*권|원권|권|원)?(?=$|[\s)\],·]|(?![가-힣0-9]))(?!\s*(?:이상|이하|할인|적립|페이백|증정|답례품))/;
const _VOUCHER_THOUSAND_FACE_RE = /([1-9]\d?,000)\s*원?\s*권/;

/**
 * 제목/배송비에서 특정 카드사·간편결제·멤버십 전용 조건 배지를 추출한다.
 * 단순 쇼핑몰 말머리([네이버], [토스])나 일반 적립글 오탐을 방지한다.
 */
export const extractPaymentConditionBadge = (
  title?: string | null,
  shippingFee?: string | null
): string | null => {
  const cleanTitle = decodeHtmlEntities(title || "").trim();
  const cleanShip = decodeHtmlEntities(shippingFee || "").trim();
  const combined = `${cleanTitle} ${cleanShip}`.trim();
  if (!combined) return null;

  const cardMatch =
    /(우리카드|삼성카드|신한카드|국민카드|KB\s*국민카드|KB\s*카드|현대카드|롯데카드|하나카드|농협카드|NH\s*농협카드|NH\s*카드|비씨카드|BC\s*카드)/i.exec(
      cleanTitle
    );
  if (cardMatch) {
    const rawCard = cardMatch[1].replace(/\s+/g, "").toUpperCase();
    if (rawCard.startsWith("KB")) return "국민카드 조건";
    if (rawCard.startsWith("NH")) return "농협카드 조건";
    if (rawCard.startsWith("BC")) return "비씨카드 조건";
    return `${cardMatch[1].replace(/\s+/g, "")} 조건`;
  }

  const payMatch =
    /(네이버페이|카카오페이|토스페이|페이코)\s*(?:결제|할인|시\s*\d|적립\s*할인|체감|최대|\d+%)/i.exec(
      cleanTitle
    );
  if (payMatch) {
    return `${payMatch[1]} 조건`;
  }

  if (/(?:와우\s*(?:할인|회원|특가|전용|\d)|\[\s*와우\s+\d)/i.test(cleanTitle) || /와우/i.test(cleanShip)) {
    return "와우회원 조건";
  }
  if (/(?:네멤\s*(?:할인|특가|전용)|네이버\s*멤버십)/i.test(cleanTitle) || /네멤/i.test(cleanShip)) {
    return "네멤 조건";
  }
  if (/유니버스\s*클럽|유클/i.test(cleanTitle) || /유클/i.test(cleanShip)) {
    return "유니버스클럽 조건";
  }
  if (/스마일\s*클럽|스클/i.test(cleanTitle) || /스클/i.test(cleanShip)) {
    return "스마일클럽 조건";
  }
  if (/슈퍼\s*적립/i.test(cleanTitle)) {
    return "슈퍼적립 조건";
  }
  return null;
};

/**
 * 식품·생활용품·소모품 제목에서 중량/용량/팩/수량을 추출해 100g당·100ml당·1팩당·개당·상품권 할인율을 계산한다.
 * IT/가전 스펙이나 비원화 딜은 오탐 방지를 위해 null을 반환한다.
 */
export const computeUnitPriceBadge = (
  title?: string | null,
  price?: number | string | null,
  currency?: string | null,
  category?: string | null
): UnitPriceBadgeInfo | null => {
  const numericPrice =
    typeof price === "number"
      ? price
      : typeof price === "string"
        ? Number(price.replace(/[^0-9.]/g, ""))
        : NaN;
  if (!title || !Number.isFinite(numericPrice) || numericPrice < 1000) return null;
  const normalizedPrice = Math.round(numericPrice);
  if (currency && currency.trim().toUpperCase() !== "KRW") return null;

  // 0. 상품권·금액권 액면가 대비 할인율 배지 (모바일/기프티콘·상품권 카테고리 제외 전 선판정)
  const voucherTitle = decodeHtmlEntities(title)
    .replace(/\s*\/\s*(?:[0-9,]+(?:\s*원)?\s*\/\s*)?(?:무료배송|무배|무료|유배|유료배송|[0-9,]+\s*원)\s*$/g, "")
    .replace(/^\[[^\]]+\]\s*/g, "")
    .replace(/\s+/g, " ")
    .trim();
  if (
    voucherTitle &&
    !voucherTitle.includes("/") &&
    _VOUCHER_TITLE_RE.test(voucherTitle) &&
    !_VOUCHER_GIFT_ATTACHED_RE.test(voucherTitle)
  ) {
    let faceValue = 0;
    const manMatch = _VOUCHER_MAN_FACE_RE.exec(voucherTitle);
    if (manMatch) {
      faceValue = Number(manMatch[1]) * 10000;
    } else {
      const thouMatch = _VOUCHER_THOUSAND_FACE_RE.exec(voucherTitle);
      if (thouMatch) {
        faceValue = Number(thouMatch[1].replace(/,/g, ""));
      }
    }
    if (faceValue > normalizedPrice) {
      const discountPct = ((faceValue - normalizedPrice) / faceValue) * 100;
      if (discountPct >= 1.0 && discountPct <= 35.0) {
        const rounded10 = Math.round(discountPct * 10);
        const pctLabel =
          rounded10 % 10 === 0
            ? `${Math.round(rounded10 / 10)}% 할인`
            : `약 ${(rounded10 / 10).toFixed(1)}% 할인`;
        return {
          label: pctLabel,
          detail: `액면가 ${faceValue.toLocaleString("ko-KR")}원 기준`,
        };
      }
    }
  }

  if (category && _UNIT_PRICE_EXCLUDED_CATEGORIES.has(category.trim())) {
    return null;
  }

  const trimmedCategory = (category || "").trim();
  const isFashionCategory = _FASHION_CATEGORY_RE.test(trimmedCategory);

  const cleanedTitle = decodeHtmlEntities(title)
    .replace(/\([^)]*원[^)]*\)/g, " ")
    .replace(/\s*\/\s*(?:[0-9,]+(?:\s*원)?\s*\/\s*)?(?:무료배송|무배|무료|유배|유료배송|[0-9,]+\s*원)\s*$/g, "")
    .replace(/^\[[^\]]+\]\s*/g, "")
    .replace(/\s+/g, " ")
    .trim();
  if (!cleanedTitle || _UNIT_PRICE_TECH_SPEC_RE.test(cleanedTitle)) {
    return null;
  }

  // 1. 물티슈/시트류 (N매 x M팩)
  const sheetPackRe =
    /(\d{2,4})\s*매(?:입)?\s*(?:[xX×*]|,|\s)\s*(\d{1,3})\s*(팩|봉|개|통|박스)(?![가-힣])/;
  const sheetMatch = sheetPackRe.exec(cleanedTitle);
  if (sheetMatch) {
    const sheets = Number(sheetMatch[1]);
    const packCount = Number(sheetMatch[2]);
    if (packCount >= 2 && packCount <= 200) {
      const perPack = Math.round(normalizedPrice / packCount);
      if (perPack >= 10 && perPack < normalizedPrice) {
        return {
          label: `1팩당 ${perPack.toLocaleString("ko-KR")}원`,
          detail: `${sheets.toLocaleString("ko-KR")}매 × ${packCount}팩 기준`,
        };
      }
    }
  }

  // Helper: 기준 단위 직후 36자 내 2단 번들((2g x 30포) 2박스, 5봉 x 4팩), 단위 없는 x24, 1+1/2+1 추출
  const extractTrailingMeta = (
    afterStr: string,
    pattern: RegExp
  ): { count: number; matched: boolean; unitLabel: string } => {
    const windowStr = afterStr.slice(0, 36);
    const m = pattern.exec(windowStr);
    if (!m) {
      const unitlessM = /^\s*[xX×*]\s*(\d{1,3})(?=\s|$|\))/.exec(windowStr);
      if (unitlessM) {
        const cnt = Number(unitlessM[1]);
        if (cnt >= 1 && cnt <= 500) {
          return { count: cnt, matched: true, unitLabel: "개" };
        }
      }
      const plusRatioM = /^\s*(\d)\s*\+\s*(\d)(?![\d.]|[a-zA-Z])/.exec(windowStr);
      if (plusRatioM) {
        const sumCnt = Number(plusRatioM[1]) + Number(plusRatioM[2]);
        if (sumCnt >= 2 && sumCnt <= 20) {
          return { count: sumCnt, matched: true, unitLabel: "개" };
        }
      }
      return { count: 1, matched: false, unitLabel: "" };
    }
    const baseCnt = Number(m[1]);
    if (!Number.isFinite(baseCnt) || baseCnt < 1 || baseCnt > 500) {
      return { count: 1, matched: false, unitLabel: "" };
    }
    const unitLabel = m[2] || "";
    const tailWindow = windowStr.slice(m.index + m[0].length);
    const secondM =
      /^\s*\)?\s*(?:[xX×*]|\s)*(\d{1,2})\s*(?:개입|개|팩|봉지|봉|박스|세트|묶음|번들|통|곽|입|판|종(?:\s*세트)?)(?![가-힣])/.exec(
        tailWindow
      );
    if (secondM) {
      const secondCnt = Number(secondM[1]);
      const tiered = baseCnt * secondCnt;
      if (Number.isFinite(tiered) && tiered >= 1 && tiered <= 500) {
        return { count: tiered, matched: true, unitLabel };
      }
    }
    const bonusM =
      /^\s*\)?\s*\+\s*(\d{1,2})\s*(?:개입|개|팩|봉|캔|병|포|통)(?!\s*(?:g|kg|ml|l|리터))/.exec(
        tailWindow
      );
    const bonusCnt = bonusM ? Number(bonusM[1]) : 0;
    return {
      count: baseCnt + (Number.isFinite(bonusCnt) ? bonusCnt : 0),
      matched: true,
      unitLabel,
    };
  };

  const trailingWeightRe =
    /^(?:\s*(?:본품|리필|용기|증정)\s*(?:[xX×*,]|\s)+|(?:[xX×*,]|\s)+)(\d{1,3})\s*(개입|개|팩|봉지|봉|캔|병|통|포|스틱|캡슐|정|환|곽|입|박스|세트|종(?:\s*세트)?)(?![가-힣])/;
  const trailingVolumeRe =
    /^(?:\s*(?:본품|리필|용기|증정)\s*(?:[xX×*,]|\s)+|(?:[xX×*,]|\s)+)(\d{1,3})\s*(개입|개|캔|병|팩|페트|pet|포|스틱|통|입|박스|세트|종(?:\s*세트)?)(?![a-wyzA-WYZ가-힣])/i;

  const volumeTokenPresent = /(?<![a-zA-Z0-9.])\d+(?:\.\d+)?\s*(?:ml|l|리터)(?![a-wyz]|x(?!\d)|당)/i.test(
    cleanedTitle
  );
  const standaloneBundleHintRe =
    /(?:총\s*|[\s,xX×*+(/\[])(\d{1,3})\s*(개입|개(?![월가-힣])|팩|봉지|봉|캔|병|포|스틱|롤|캡슐|정|켤레|박스|인분|환|(?<!매)입)(?![가-힣])/;
  const standaloneM = standaloneBundleHintRe.exec(cleanedTitle);
  const hasStandaloneBundleCount = Boolean(
    standaloneM && Number(standaloneM[1]) >= 2 && Number(standaloneM[1]) <= 500
  );

  // 2. 중량 (kg / g) — 100g당 및 소용량 포(2g x 30포 2박스 -> 1포당), 영양성분(당/단백질 22g 등) 정밀 차단
  const weightRe = /(?<![a-zA-Z0-9.])(\d+(?:\.\d+)?)\s*(kg|g)(?![a-wyz]|x(?!\d)|당)/gi;
  const weightEntries: Array<{
    totalGrams: number;
    count: number;
    unitGrams: number;
    unitLabel: string;
    matchedCount: boolean;
    startIdx: number;
    endIdx: number;
  }> = [];
  if (!isFashionCategory) {
    for (const wm of cleanedTitle.matchAll(weightRe)) {
      const rawVal = Number(wm[1]);
      const unit = wm[2].toLowerCase();
      if (!Number.isFinite(rawVal) || rawVal <= 0) continue;
      const unitGrams = unit === "kg" ? rawVal * 1000 : rawVal;
      const startIdx = wm.index ?? 0;
      const endIdx = startIdx + wm[0].length;
      const beforeSlice = cleanedTitle.slice(Math.max(0, startIdx - 20), startIdx);
      const afterStr = cleanedTitle.slice(endIdx);
      const trailing = extractTrailingMeta(afterStr, trailingWeightRe);

      // 영양성분 함량(프로틴 22g / 당류 0g 등) 차단
      const isNutritionToken =
        unitGrams <= 60 &&
        (_NUTRITION_WORD_RE.test(beforeSlice) ||
          (unitGrams >= 10 &&
            unitGrams <= 50 &&
            !trailing.matched &&
            _PROTEIN_HINT_RE.test(cleanedTitle) &&
            (volumeTokenPresent || hasStandaloneBundleCount)));
      if (isNutritionToken) {
        continue;
      }

      if (unitGrams < 10) {
        if (
          unitGrams >= 1 &&
          trailing.matched &&
          trailing.count >= 5 &&
          /^(?:포|스틱|봉|캡슐|정|개입|입)$/.test(trailing.unitLabel)
        ) {
          const sachetTotal = unitGrams * trailing.count;
          if (sachetTotal >= 20 && sachetTotal <= 100000) {
            const perSachet = Math.round(normalizedPrice / trailing.count);
            if (perSachet >= 10 && perSachet < normalizedPrice) {
              const uName = /^(?:포|스틱|봉|캡슐|정)$/.test(trailing.unitLabel)
                ? trailing.unitLabel
                : "개";
              return {
                label: `1${uName}당 ${perSachet.toLocaleString("ko-KR")}원`,
                detail: `총 ${trailing.count}${trailing.unitLabel} · ${Math.round(
                  sachetTotal
                ).toLocaleString("ko-KR")}g 기준`,
              };
            }
          }
        }
        continue;
      }

      const totalGrams = unitGrams * trailing.count;
      if (totalGrams >= 10 && totalGrams <= 100000) {
        weightEntries.push({
          totalGrams,
          count: trailing.count,
          unitGrams,
          unitLabel: trailing.unitLabel,
          matchedCount: trailing.matched,
          startIdx,
          endIdx,
        });
      }
    }
  }

  // + 로 결합된 복수 중량 합산 (예: 900g 본품 + 300g 증정; / 선택옵션은 합산 제외)
  if (weightEntries.length >= 2 && cleanedTitle.includes("+") && !cleanedTitle.includes("/")) {
    let chained = false;
    let sumGrams = weightEntries[0].totalGrams;
    for (let i = 1; i < weightEntries.length; i++) {
      const gap = cleanedTitle.slice(weightEntries[i - 1].endIdx, weightEntries[i].startIdx);
      if (gap.includes("+") && !gap.includes("/")) {
        sumGrams += weightEntries[i].totalGrams;
        chained = true;
      }
    }
    if (chained && sumGrams >= 50 && sumGrams <= 100000) {
      const per100g = Math.round((normalizedPrice / sumGrams) * 100);
      if (per100g >= 10 && per100g < normalizedPrice) {
        return {
          label: `100g당 ${per100g.toLocaleString("ko-KR")}원`,
          detail: `총 ${Math.round(sumGrams).toLocaleString("ko-KR")}g 기준`,
        };
      }
    }
  }

  for (const we of weightEntries) {
    if (we.totalGrams >= 50 && we.totalGrams <= 100000) {
      const per100g = Math.round((normalizedPrice / we.totalGrams) * 100);
      if (per100g >= 10 && per100g < normalizedPrice) {
        return {
          label: `100g당 ${per100g.toLocaleString("ko-KR")}원`,
          detail:
            we.count >= 2
              ? `총 ${Math.round(we.totalGrams).toLocaleString("ko-KR")}g · 개당 ${Math.round(
                  normalizedPrice / we.count
                ).toLocaleString("ko-KR")}원`
              : `총 ${Math.round(we.totalGrams).toLocaleString("ko-KR")}g 기준`,
        };
      }
    }
  }

  // 3. 용량 (ml / L / 리터) — 비액체 용기·쓰레기봉투·가전 및 패션 제외
  const volumeRe = /(?<![a-zA-Z0-9.])(\d+(?:\.\d+)?)\s*(ml|l|리터)(?![a-wyz]|x(?!\d)|당)/gi;
  const volumeEntries: Array<{
    totalMl: number;
    count: number;
    matchedCount: boolean;
    startIdx: number;
    endIdx: number;
  }> = [];
  if (!isFashionCategory && !_NON_LIQUID_VOLUME_RE.test(cleanedTitle)) {
    for (const vm of cleanedTitle.matchAll(volumeRe)) {
      const rawVal = Number(vm[1]);
      const unit = vm[2].toLowerCase();
      if (!Number.isFinite(rawVal) || rawVal <= 0) continue;
      const unitMl = unit === "ml" ? rawVal : rawVal * 1000;
      if (unitMl < 10) continue;
      const startIdx = vm.index ?? 0;
      const endIdx = startIdx + vm[0].length;
      const afterStr = cleanedTitle.slice(endIdx);
      const trailing = extractTrailingMeta(afterStr, trailingVolumeRe);
      const totalMl = unitMl * trailing.count;
      if (totalMl >= 10 && totalMl <= 100000) {
        volumeEntries.push({
          totalMl,
          count: trailing.count,
          matchedCount: trailing.matched,
          startIdx,
          endIdx,
        });
      }
    }
  }

  // + 로 결합된 복수 용량 합산 (예: 4.2L 본품+2L 리필 2개 -> 8,200ml)
  if (volumeEntries.length >= 2 && cleanedTitle.includes("+") && !cleanedTitle.includes("/")) {
    let chained = false;
    let sumMl = volumeEntries[0].totalMl;
    for (let i = 1; i < volumeEntries.length; i++) {
      const gap = cleanedTitle.slice(volumeEntries[i - 1].endIdx, volumeEntries[i].startIdx);
      if (gap.includes("+") && !gap.includes("/")) {
        sumMl += volumeEntries[i].totalMl;
        chained = true;
      }
    }
    if (chained && sumMl >= 50 && sumMl <= 100000) {
      const per100ml = Math.round((normalizedPrice / sumMl) * 100);
      if (per100ml >= 10 && per100ml < normalizedPrice) {
        return {
          label: `100ml당 ${per100ml.toLocaleString("ko-KR")}원`,
          detail: `총 ${Math.round(sumMl).toLocaleString("ko-KR")}ml 기준`,
        };
      }
    }
  }

  for (const ve of volumeEntries) {
    if (ve.totalMl >= 50 && ve.totalMl <= 100000) {
      const per100ml = Math.round((normalizedPrice / ve.totalMl) * 100);
      if (per100ml >= 10 && per100ml < normalizedPrice) {
        return {
          label: `100ml당 ${per100ml.toLocaleString("ko-KR")}원`,
          detail:
            ve.count >= 2
              ? `총 ${Math.round(ve.totalMl).toLocaleString("ko-KR")}ml · 개당 ${Math.round(
                  normalizedPrice / ve.count
                ).toLocaleString("ko-KR")}원`
              : `총 ${Math.round(ve.totalMl).toLocaleString("ko-KR")}ml 기준`,
        };
      }
    }
  }

  // 4. 순수 묶음 수량 (총N개/개입/스틱/인분/환/롤/캡슐/정/켤레/팩/봉 + 계란 한정 N구)
  const formatBundleBadge = (count: number, unitLabel: string): UnitPriceBadgeInfo | null => {
    if (count < 2 || count > 500) return null;
    const perUnit = Math.round(normalizedPrice / count);
    if (perUnit < 10 || perUnit >= normalizedPrice) return null;
    let prefix = "개당";
    if (
      unitLabel === "롤" ||
      unitLabel === "캡슐" ||
      unitLabel === "정" ||
      unitLabel === "켤레" ||
      unitLabel === "인분" ||
      unitLabel === "환" ||
      unitLabel === "구" ||
      unitLabel === "포" ||
      unitLabel === "스틱"
    ) {
      prefix = `1${unitLabel}당`;
    }
    return {
      label: `${prefix} ${perUnit.toLocaleString("ko-KR")}원`,
      detail: `총 ${count}${unitLabel} 기준`,
    };
  };

  const totalBundleRe =
    /총\s*(\d{1,3})\s*(개입|개(?![월가-힣])|팩|봉지|봉|캔|병|포|스틱|롤|캡슐|정|켤레|박스|인분|환|(?<!매)입)(?![가-힣])/;
  const tbm = totalBundleRe.exec(cleanedTitle);
  if (tbm) {
    const res = formatBundleBadge(Number(tbm[1]), tbm[2]);
    if (res) return res;
  }

  const bundleRe =
    /(?:^|[\s,xX×*+(/\[])(\d{2,3}|[2-9])\s*(개입|개(?![월가-힣])|팩|봉지|봉|캔|병|포|스틱|롤|캡슐|정|켤레|박스|인분|환|(?<!매)입)(?![가-힣])/g;
  for (const bm of cleanedTitle.matchAll(bundleRe)) {
    const bmStart = bm.index ?? 0;
    const dosePrefix = cleanedTitle.slice(Math.max(0, bmStart - 8), bmStart);
    if (/(?:1일|하루|일일|매일)\s*$/.test(dosePrefix)) continue;
    const baseCnt = Number(bm[1]);
    const unitLabel = bm[2];
    const endIdx = bmStart + bm[0].length;
    const afterStr = cleanedTitle.slice(endIdx);
    const tierM = /^\s*(?:[xX×*]|\s)\s*(\d{1,2})\s*(?:박스|세트|묶음|번들|팩|통)(?![가-힣])/.exec(
      afterStr.slice(0, 20)
    );
    const totalCnt =
      tierM && baseCnt * Number(tierM[1]) <= 500 ? baseCnt * Number(tierM[1]) : baseCnt;
    const res = formatBundleBadge(totalCnt, unitLabel);
    if (res) return res;
  }

  // 계란/달걀 '구' (멀티탭 8구 등 전기·가전 오탐 엄격 배제)
  if (
    !_APPLIANCE_OUTLET_RE.test(cleanedTitle) &&
    (_EGG_ITEM_HINT_RE.test(cleanedTitle) || trimmedCategory === "음식")
  ) {
    const eggMatch = /(?:^|[\s,xX×*+(/\[])(\d{2,3}|[2-9])\s*구(?![가-힣])/.exec(cleanedTitle);
    if (eggMatch) {
      const baseEgg = Number(eggMatch[1]);
      if (_EGG_ITEM_HINT_RE.test(cleanedTitle) || baseEgg >= 10) {
        const afterEgg = cleanedTitle.slice((eggMatch.index ?? 0) + eggMatch[0].length);
        const eggTier = /^\s*(?:[xX×*]|\s)\s*(\d{1,2})\s*(?:판|박스|세트|팩)(?![가-힣])/.exec(
          afterEgg.slice(0, 18)
        );
        const totalEggs =
          eggTier && baseEgg * Number(eggTier[1]) <= 500 ? baseEgg * Number(eggTier[1]) : baseEgg;
        const res = formatBundleBadge(totalEggs, "구");
        if (res) return res;
      }
    }
  }

  // 식품 맛 조합 수량 (예: '애슐리 딥디쉬 피자 시카고2+고구마2')
  if (trimmedCategory === "음식" && !cleanedTitle.includes("/")) {
    const comboMatch =
      /([가-힣]{2,10})\s*(\d{1,2})\s*\+\s*([가-힣]{2,10})\s*(\d{1,2})(?![가-힣0-9]|\s*(?:만|천|원|%|개월|년|일|시|분))/.exec(
        cleanedTitle
      );
    if (comboMatch) {
      const c1 = Number(comboMatch[2]);
      const c2 = Number(comboMatch[4]);
      const totalCombo = c1 + c2;
      if (c1 >= 1 && c1 <= 20 && c2 >= 1 && c2 <= 20 && totalCombo >= 2 && totalCombo <= 40) {
        const res = formatBundleBadge(totalCombo, "개");
        if (res) return res;
      }
    }
  }

  // 7. 소용량 10g당 / 10ml당 최후 폴백 (묶음 수량·영양성분 차단 이후, 뷰티·소용량 화장품 전용 허용)
  const isBeautyContext =
    trimmedCategory === "뷰티" ||
    trimmedCategory === "화장품" ||
    _BEAUTY_HINT_RE.test(cleanedTitle);
  const per10gCap = isBeautyContext ? 20000 : 5000;
  const per10mlCap = isBeautyContext ? 25000 : 3000;

  for (const we of weightEntries) {
    if (!we.matchedCount && hasStandaloneBundleCount) continue;
    if (we.totalGrams >= 10 && we.totalGrams <= 100) {
      const per10g = Math.round((normalizedPrice / we.totalGrams) * 10);
      if (per10g >= 10 && per10g < normalizedPrice && per10g <= per10gCap) {
        return {
          label: `10g당 ${per10g.toLocaleString("ko-KR")}원`,
          detail:
            we.count >= 2
              ? `총 ${Math.round(we.totalGrams).toLocaleString("ko-KR")}g · 개당 ${Math.round(
                  normalizedPrice / we.count
                ).toLocaleString("ko-KR")}원`
              : `총 ${Math.round(we.totalGrams).toLocaleString("ko-KR")}g 기준`,
        };
      }
    }
  }

  for (const ve of volumeEntries) {
    if (!ve.matchedCount && hasStandaloneBundleCount) continue;
    if (ve.totalMl >= 10 && ve.totalMl <= 100) {
      const per10ml = Math.round((normalizedPrice / ve.totalMl) * 10);
      if (per10ml >= 10 && per10ml < normalizedPrice && per10ml <= per10mlCap) {
        return {
          label: `10ml당 ${per10ml.toLocaleString("ko-KR")}원`,
          detail:
            ve.count >= 2
              ? `총 ${Math.round(ve.totalMl).toLocaleString("ko-KR")}ml · 개당 ${Math.round(
                  normalizedPrice / ve.count
                ).toLocaleString("ko-KR")}원`
              : `총 ${Math.round(ve.totalMl).toLocaleString("ko-KR")}ml 기준`,
        };
      }
    }
  }

  return null;
};

// 상세 정보 60초 SWR 프리페치 메모리 캐시 (최대 30건 LRU + In-Flight 중복 차단)
const _DEAL_DETAIL_CACHE_TTL_MS = 60_000;
const _DEAL_DETAIL_CACHE_MAX = 30;
const _dealDetailCache = new Map<
  number,
  { data: RawDealApiResponse; cachedAt: number }
>();
const _dealDetailInflight = new Set<number>();

export function getCachedDealDetail(
  dealId: number
): { data: RawDealApiResponse; cachedAt: number } | null {
  if (!Number.isFinite(dealId) || dealId <= 0) return null;
  const entry = _dealDetailCache.get(dealId);
  if (!entry) return null;
  if (Date.now() - entry.cachedAt > _DEAL_DETAIL_CACHE_TTL_MS) {
    _dealDetailCache.delete(dealId);
    return null;
  }
  return entry;
}

export function setCachedDealDetail(
  dealId: number,
  data: RawDealApiResponse
): void {
  if (!Number.isFinite(dealId) || dealId <= 0 || !data) return;
  if (_dealDetailCache.has(dealId)) {
    _dealDetailCache.delete(dealId);
  } else if (_dealDetailCache.size >= _DEAL_DETAIL_CACHE_MAX) {
    const oldestKey = _dealDetailCache.keys().next().value;
    if (oldestKey !== undefined) {
      _dealDetailCache.delete(oldestKey);
    }
  }
  _dealDetailCache.set(dealId, { data, cachedAt: Date.now() });
}

export function invalidateDealDetailCache(dealId: number): void {
  if (!Number.isFinite(dealId) || dealId <= 0) return;
  _dealDetailCache.delete(dealId);
}

export function clearDealDetailCache(): void {
  _dealDetailCache.clear();
  _dealDetailInflight.clear();
}

export function prefetchDealDetail(dealId: number): void {
  if (typeof window === "undefined") return;
  if (!Number.isFinite(dealId) || dealId <= 0) return;
  if (getCachedDealDetail(dealId) || _dealDetailInflight.has(dealId)) return;

  _dealDetailInflight.add(dealId);
  const headers: Record<string, string> = {};
  const token = getStoredWebDeviceToken();
  if (token) {
    headers["X-Device-Token"] = token;
  }

  fetch(apiUrl(`/api/community/deals/${dealId}`), { headers })
    .then(async (res) => {
      if (!res.ok) return;
      const json = (await res.json()) as RawDealApiResponse;
      if (json && Number.isFinite(json.id)) {
        setCachedDealDetail(dealId, json);
      }
    })
    .catch(() => {})
    .finally(() => {
      _dealDetailInflight.delete(dealId);
    });
}

const _FOREIGN_TO_KRW_RATES = {
  USD: 1450,
  EUR: 1550,
  CNY: 200,
  JPY: 9.5,
} as const;

type SupportedForeignCurrency = keyof typeof _FOREIGN_TO_KRW_RATES;

function resolveForeignMajorAmount(
  priceVal: string | number | null | undefined,
  currency?: string | null
): { currency: SupportedForeignCurrency; amount: number } | null {
  const normCurr = (currency || "KRW").trim().toUpperCase();
  if (
    normCurr !== "USD" &&
    normCurr !== "EUR" &&
    normCurr !== "CNY" &&
    normCurr !== "JPY"
  ) {
    return null;
  }
  const rawStr =
    priceVal === null || priceVal === undefined ? "" : String(priceVal).trim();
  if (!rawStr) return null;
  const numOnly = rawStr.replace(/[^0-9.]/g, "");
  if (!numOnly) return null;
  const parsed = parseFloat(numOnly);
  if (!Number.isFinite(parsed) || parsed <= 0) return null;
  if (normCurr === "JPY") {
    return { currency: "JPY", amount: parsed };
  }
  const hasDecimal = rawStr.includes(".");
  const hasExplicitSymbol = /[$€¥]/.test(rawStr);
  const major = hasDecimal || hasExplicitSymbol ? parsed : parsed / 100;
  return { currency: normCurr, amount: major };
}

export function estimateKrwFromForeign(
  price: string | number | null | undefined,
  currency?: string | null
): { krw: number; formatted: string } | null {
  const resolved = resolveForeignMajorAmount(price, currency);
  if (!resolved) return null;
  const krw = Math.round(
    resolved.amount * _FOREIGN_TO_KRW_RATES[resolved.currency]
  );
  if (!Number.isFinite(krw) || krw <= 0) return null;
  return {
    krw,
    formatted: `약 ${krw.toLocaleString("ko-KR")}원`,
  };
}

export function isCustomsDutyRisk(
  price: string | number | null | undefined,
  currency?: string | null,
  shopName?: string | null,
  title?: string | null
): { atRisk: boolean; thresholdUsd: number; usdApprox: number } {
  const resolved = resolveForeignMajorAmount(price, currency);
  if (!resolved) {
    return { atRisk: false, thresholdUsd: 150, usdApprox: 0 };
  }
  const krwRate = _FOREIGN_TO_KRW_RATES[resolved.currency];
  const usdApprox =
    resolved.currency === "USD"
      ? Math.round(resolved.amount * 100) / 100
      : Math.round(((resolved.amount * krwRate) / _FOREIGN_TO_KRW_RATES.USD) * 100) /
        100;

  const contextText = `${shopName || ""} ${title || ""}`.toLowerCase();
  const isNonUsOrigin =
    /알리|테무|타오바오|큐텐|일본|아마존\s*재팬|amazon\.co\.jp|일마존|독마존|유럽|중국/.test(
      contextText
    );
  const isUsOrigin =
    !isNonUsOrigin &&
    (/미국|아마존|amazon\.com|뉴에그|newegg|베스트바이|bestbuy|오레곤|델라웨어/.test(
      contextText
    ) ||
      (resolved.currency === "USD" && /11번가\s*아마존|우주패스/.test(contextText)));
  const thresholdUsd = isUsOrigin ? 200 : 150;
  return {
    atRisk: usdApprox > thresholdUsd,
    thresholdUsd,
    usdApprox,
  };
}

export function computeShippingTotal(
  price: string | number | null | undefined,
  currency: string | null | undefined,
  shippingText: string | null | undefined
): { shippingFee: number; totalKrw: number; label: string } | null {
  const normCurr = (currency || "KRW").trim().toUpperCase();
  if (normCurr !== "KRW") return null;
  const priceNum =
    typeof price === "number"
      ? Math.round(price)
      : parseInt(String(price ?? "").replace(/[^0-9]/g, ""), 10);
  if (!Number.isFinite(priceNum) || priceNum <= 0) return null;

  const cleanShip = decodeHtmlEntities(shippingText || "").trim();
  if (!cleanShip) return null;
  if (
    /무료|무배|유클|스클|평생|선착순|적립|증정|무제한|약정|할부|착불|별도/i.test(
      cleanShip
    )
  ) {
    return null;
  }
  if (/^\d+\s*(?:개월|년|일)/.test(cleanShip)) {
    return null;
  }

  const m = cleanShip.match(/([0-9]{1,2}(?:,[0-9]{3})+|[0-9]{3,5})\s*원?/);
  if (!m) return null;
  const shippingFee = parseInt(m[1].replace(/,/g, ""), 10);
  if (!Number.isFinite(shippingFee) || shippingFee < 500 || shippingFee > 50000) {
    return null;
  }
  const totalKrw = priceNum + shippingFee;
  return {
    shippingFee,
    totalKrw,
    label: `배송비 포함 총 ${totalKrw.toLocaleString("ko-KR")}원`,
  };
}

const _URGENT_PROMO_LABELS = [
  "선착순특가",
  "오늘끝딜",
  "타임딜",
  "긴급특가",
  "게릴라특가",
  "단독특가",
  "번개딜",
];

export function stripPromoPrefixForDisplay(title: string): {
  cleanTitle: string;
  urgentBadge: string | null;
} {
  const decoded = decodeHtmlEntities(title || "").trim();
  if (!decoded) return { cleanTitle: "", urgentBadge: null };

  for (const promo of _URGENT_PROMO_LABELS) {
    const bracketRe = new RegExp(
      `^\\s*\\[\\s*${promo}(?:\\s*(?:[:·\\-]\\s*)?[0-9,.$₩원만%~\\-+]+){0,2}\\s*\\]\\s*`,
      "i"
    );
    if (bracketRe.test(decoded)) {
      return {
        cleanTitle: decoded.replace(bracketRe, "").trim() || decoded,
        urgentBadge: promo,
      };
    }
    const bareRe = new RegExp(`^\\s*${promo}\\s*[-:·]?\\s+`, "i");
    if (bareRe.test(decoded)) {
      return {
        cleanTitle: decoded.replace(bareRe, "").trim() || decoded,
        urgentBadge: promo,
      };
    }
  }

  const inlinePromo = _URGENT_PROMO_LABELS.find((p) => decoded.includes(p)) || null;
  return { cleanTitle: decoded, urgentBadge: inlinePromo };
}

export function getDealAgeDays(
  createdAt?: string | null,
  nowMs: number = Date.now()
): number {
  const ts = parseIsoTimestampMs(createdAt);
  if (ts === null) return 0;
  const diffMs = Math.max(0, nowMs - ts);
  return Math.floor(diffMs / 86_400_000);
}

/**
 * 최소·최대 가격 입력값을 숫자만으로 정제하고, min > max 역전 시 자동으로 교정(swap)합니다.
 */
export function normalizePriceRange(
  rawMin: string,
  rawMax: string
): { minPrice: string; maxPrice: string; swapped: boolean } {
  let cleanMin = rawMin.replace(/[^0-9]/g, "");
  let cleanMax = rawMax.replace(/[^0-9]/g, "");
  let swapped = false;

  if (cleanMin && cleanMax) {
    const minVal = Number(cleanMin);
    const maxVal = Number(cleanMax);
    if (Number.isFinite(minVal) && Number.isFinite(maxVal) && minVal > maxVal) {
      [cleanMin, cleanMax] = [cleanMax, cleanMin];
      swapped = true;
    }
  }

  return { minPrice: cleanMin, maxPrice: cleanMax, swapped };
}