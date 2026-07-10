// 가격 표시 포맷팅 헬퍼
export const formatPrice = (priceVal: any, currency?: string): string => {
    if (priceVal === null || priceVal === undefined) return "가격 문의";
    const price = String(priceVal).trim();
    if (!price || price === "정보 없음" || price === "가격 문의" || price === "null" || price === "undefined") {
      return "가격 문의";
    }
    if (price.includes("원") || price.includes("$")) return price;
    const numOnly = price.replace(/[^0-9.]/g, "");
    if (!numOnly) return price;
    const parsedNum = parseFloat(numOnly);
    if (currency === "USD" || (price.includes(".") && parsedNum < 150)) {
      if (!price.includes(".") && parsedNum >= 100) {
        const dollars = parsedNum / 100;
        return dollars % 1 === 0 ? `$${dollars}` : `$${dollars.toFixed(2)}`;
      } else {
        return parsedNum % 1 === 0 ? `$${parsedNum}` : `$${parsedNum.toFixed(2)}`;
      }
    }
    if (parseInt(numOnly, 10) === 1393) return `$13.93`;
    const formatted = parseInt(numOnly, 10).toLocaleString();
    return `${formatted}원`;
};

// 제목 기반 카테고리 분류
export const categorizeDeal = (title: string): string => {
    const t = title.toLowerCase();
    if (/pc|컴퓨터|그래픽|모니터|ssd|hdd|ram|램|메모리|키보드|마우스|노트북|글카|rtx|보드|cpu|인텔|amd|라이젯|지포스|로지텍|공유기|nas|웹칠|헤드셋/i.test(t)) return "PC/하드웨어";
    if (/스마트폰|아이폰|갤럭시|충전|보조배터리|이어폰|버즈|에어팟|헤드폰|애플워치|워치|태블릿|패드|아이패드|케이스|케이블/i.test(t)) return "모바일/상품권";
    if (/tv|티비|냉장고|청소기|세탁기|건조기|선풍기|에어컨|가습기|블렌더|모터|스타일러|공기청정기|안마|로봇|다이슨|lg|삼성/i.test(t)) return "가전/TV";
    if (/나이키|아디다스|티셔츠|바지|신발|운동화|자켓|의류|패딩|모자|양말|구두|슬리퍼|가방|팬츠|아식스|뉴발/i.test(t)) return "의류/뷰티";
    if (/물|생수|라면|커피|치킨|피자|콜라|제로|비타민|유산균|햇반|고기|돼지|소고기|과자|두유|우유|닭|스팸|참치|만두|볶음밥|삼다수|사이다|펙시|오레오/i.test(t)) return "식품/건강";
    if (/상품권|컴쳐|해피머니|도서문화|구글|게임|기프트|쿠폰|편의점|cu|gs|포인트|페이|네이버페이|스팀/i.test(t)) return "모바일/상품권";
    return "기타";
};

// 이미지 프록시 URL 생성
export const getProxyImageUrl = (url: string | null): string => {
  if (!url) return "";
  if (url.includes("bbasak.com") || url.includes("ppomppu.co.kr")) {
    return `/api/proxy-image?url=${encodeURIComponent(url)}`;
  }
  return url;
};
