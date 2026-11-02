// 가격 표시 포맷팅 헬퍼
export const formatPrice = (priceVal: any, currency?: string): string => {
    if (priceVal === null || priceVal === undefined) return "가격 문의";
    const price = String(priceVal).trim();
    if (!price || price === "0" || price === "정보 없음" || price === "가격 문의" || price === "null" || price === "undefined") {
      return "가격 문의";
    }
    if (price.includes("원") || price.includes("$") || price.includes("€")) return price;
    const numOnly = price.replace(/[^0-9.]/g, "");
    if (!numOnly) return price;
    const parsedNum = parseFloat(numOnly);
    if (parsedNum <= 0) return "가격 문의";

    if (currency === "USD" || (price.includes(".") && parsedNum < 150)) {
      if (!price.includes(".") && parsedNum >= 100) {
        const dollars = parsedNum / 100;
        return dollars % 1 === 0 ? `$${dollars}` : `$${dollars.toFixed(2)}`;
      } else {
        return parsedNum % 1 === 0 ? `$${parsedNum}` : `$${parsedNum.toFixed(2)}`;
      }
    }
    if (currency === "EUR") {
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

// 이미지 프록시 URL 생성 (핫링크 차단 커뮤니티 이미지 및 http 혼합 콘텐츠 전체 방어)
export const getProxyImageUrl = (url: string | null): string => {
  if (!url) return "";
  if (url.startsWith("/api/proxy-image") || url.startsWith("/images/")) {
    return apiUrl(url);
  }
  if (
    url.startsWith("http://") ||
    url.includes("bbasak.com") ||
    url.includes("ppomppu.co.kr") ||
    url.includes("fmkorea.com") ||
    url.includes("fmkorea.org") ||
    url.includes("quasarzone.com") ||
    url.includes("ruliweb.com") ||
    url.includes("clien.net")
  ) {
    return apiUrl(`/api/proxy-image?url=${encodeURIComponent(url)}`);
  }
  return url;
};
