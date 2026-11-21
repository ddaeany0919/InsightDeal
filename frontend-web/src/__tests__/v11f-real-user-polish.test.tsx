import React from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import ThemeToggle from "@/app/components/ThemeToggle";
import DealCard from "@/app/components/DealCard";
import DealDetailModal from "@/app/components/DealDetailModal";
import NotificationDrawer from "@/app/components/NotificationDrawer";
import PriceTrendChart from "@/app/components/PriceTrendChart";
import {
  categorizeDeal,
  computeShippingTotal,
  estimateKrwFromForeign,
  extractPaymentConditionBadge,
  getCachedDealDetail,
  getDealAgeDays,
  inferShopFromUrl,
  isCustomsDutyRisk,
  setCachedDealDetail,
  stripPromoPrefixForDisplay,
} from "@/app/lib/utils";
import type { Deal, RawDealApiResponse } from "@/app/lib/types";

function makeDeal(id: number, overrides: Partial<Deal> = {}): Deal {
  return {
    id,
    community_name: "뽐뿌",
    shop_name: "쿠팡",
    title: `[쿠팡] 테스트 핫딜 ${id}`,
    price: "15900",
    image_url: `https://cdn.example.com/item-${id}.jpg`,
    scraped_at: "방금 전",
    post_link: `https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=${id}`,
    ecommerce_url: `https://www.coupang.com/vp/products/${id}`,
    currency: "KRW",
    category: "생활용품",
    shipping_fee: "무료",
    isClosed: false,
    isSearchFallback: false,
    ...overrides,
  };
}

describe("v1.1f 실사용자 100대 개선 웹 회귀 테스트", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
    delete document.documentElement.dataset.theme;
  });

  it("ThemeToggle AMOLED 순수 블랙 모드 전환 시 data-theme='amoled'과 localStorage가 동기화된다", () => {
    render(<ThemeToggle />);

    const amoledBtn = screen.getByRole("button", { name: /AMOLED 순수 블랙 모드 전환/ });
    fireEvent.click(amoledBtn);

    expect(document.documentElement.dataset.theme).toBe("amoled");
    expect(document.documentElement.style.colorScheme).toBe("dark");
    expect(localStorage.getItem("insightdeal_theme")).toBe("amoled");
    expect(amoledBtn).toHaveAttribute("aria-pressed", "true");
  });

  it("utils.ts: 쇼핑몰 추론, 카테고리 가드, 멤버십 배지, 환율/관세/배송 합산, 프로모션 접두사, 경과일수, 상세 SWR 캐시를 검증한다", () => {
    expect(inferShopFromUrl("https://www.xbox.com/ko-KR/games/store/game")).toBe("엑스박스");
    expect(inferShopFromUrl("https://store.kakao.com/brand/products/123")).toBe("카카오쇼핑");

    expect(categorizeDeal("애플TV 4K 128GB Wi-Fi")).not.toBe("육아/유아용품");
    expect(categorizeDeal("아가드 모서리 안전가드 4개입")).toBe("생활용품");
    expect(categorizeDeal("아기 젖병 소독기 유아용")).toBe("육아/유아용품");

    expect(extractPaymentConditionBadge("[지마켓] 생수 특가", "유클무료")).toBe("유니버스클럽 조건");
    expect(extractPaymentConditionBadge("[옥션] 휴지 특가", "스클무료")).toBe("스마일클럽 조건");

    const est = estimateKrwFromForeign("10000", "USD");
    expect(est).not.toBeNull();
    expect(est?.krw).toBeGreaterThan(100_000);

    const aliOver = isCustomsDutyRisk("18000", "USD", "알리익스프레스", "안드로이드 태블릿");
    expect(aliOver.thresholdUsd).toBe(150);
    expect(aliOver.atRisk).toBe(true);

    const usUnder = isCustomsDutyRisk("18000", "USD", "아마존", "미국 직구 헤드폰");
    expect(usUnder.thresholdUsd).toBe(200);
    expect(usUnder.atRisk).toBe(false);

    const shipTotal = computeShippingTotal("15000", "KRW", "3,000원");
    expect(shipTotal?.shippingFee).toBe(3000);
    expect(shipTotal?.totalKrw).toBe(18000);
    expect(shipTotal?.label).toBe("배송비 포함 총 18,000원");
    expect(computeShippingTotal("15000", "KRW", "평생무료적립")).toBeNull();

    const promo = stripPromoPrefixForDisplay("[타임딜] 오뚜기 진라면 20봉");
    expect(promo.urgentBadge).toBe("타임딜");
    expect(promo.cleanTitle).toBe("오뚜기 진라면 20봉");

    const nowMs = Date.UTC(2026, 10, 21, 12, 0, 0);
    expect(getDealAgeDays("2026-11-18T12:00:00Z", nowMs)).toBe(3);

    const sampleRaw = { id: 9101, title: "캐시 테스트", price: "9900" } as RawDealApiResponse;
    setCachedDealDetail(9101, sampleRaw);
    expect(getCachedDealDetail(9101)?.data.title).toBe("캐시 테스트");
  });

  it("DealCard: 카드 전체 클릭, 비무료 0원 '옵션·할인율 확인' 표기, 컴팩트 뷰 클래스를 검증한다", () => {
    const onSelectDeal = vi.fn();
    const zeroNonFreeDeal = makeDeal(501, {
      title: "[올리브영] 전품목 장바구니 할인 쿠폰 안내",
      price: "0",
      isFree: false,
      shipping_fee: "무료",
      commentCount: 5,
      recommendations: 12,
    });

    const { container } = render(
      <DealCard
        deal={zeroNonFreeDeal}
        appliedSearch=""
        isMounted={true}
        currentTime={Date.now()}
        isWishlisted={false}
        isAlertSet={false}
        viewMode="compact"
        showCategoryPill={true}
        onSelectDeal={onSelectDeal}
        onShareDeal={vi.fn()}
        onToggleAlert={vi.fn()}
        onToggleWishlist={vi.fn()}
        onShowToast={vi.fn()}
      />
    );

    expect(container.querySelector(".deal-card--compact")).toBeTruthy();
    expect(screen.getByText("옵션·할인율 확인")).toBeInTheDocument();

    const article = container.querySelector("article.deal-card") as HTMLElement;
    expect(article).toBeTruthy();
    fireEvent.click(article);
    expect(onSelectDeal).toHaveBeenCalledWith(zeroNonFreeDeal);
  });

  it("DealDetailModal: similar_deals 추천 스트립 클릭 시 onSelectRelatedDeal을 호출하고 화살표 키로 이전/다음 이동한다", async () => {
    const targetDeal = makeDeal(710, {
      title: "[쿠팡] 코카콜라 제로 355ml 24캔",
      price: "16900",
    });
    const onNavigateDeal = vi.fn();
    const onSelectRelatedDeal = vi.fn();

    vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
      const url = typeof input === "string" ? input : input instanceof URL ? input.toString() : input.url;
      if (url.includes("/api/community/deals/710/comments")) {
        return new Response(JSON.stringify([]), { status: 200 });
      }
      if (url.includes("/api/community/deals/710")) {
        return new Response(
          JSON.stringify({
            id: 710,
            title: "[쿠팡] 코카콜라 제로 355ml 24캔",
            price: 16900,
            currency: "KRW",
            site_name: "뽐뿌",
            shop_name: "쿠팡",
            post_url: targetDeal.post_link,
            ecommerce_url: targetDeal.ecommerce_url,
            similar_deals: [
              {
                id: 711,
                title: "[네이버] 펩시 제로 라임 355ml 24캔",
                price: 15500,
                currency: "KRW",
                site_name: "펨코",
                shop_name: "네이버",
              },
            ],
          }),
          { status: 200 }
        );
      }
      return new Response(JSON.stringify({}), { status: 200 });
    });

    render(
      <DealDetailModal
        deal={targetDeal}
        onClose={vi.fn()}
        showToast={vi.fn()}
        onNavigateDeal={onNavigateDeal}
        onSelectRelatedDeal={onSelectRelatedDeal}
      />
    );

    expect(await screen.findByText("비슷한 커뮤니티 핫딜")).toBeInTheDocument();
    const relatedBtn = await screen.findByText(/펩시 제로 라임/);
    fireEvent.click(relatedBtn);
    expect(onSelectRelatedDeal).toHaveBeenCalledTimes(1);
    expect(onSelectRelatedDeal.mock.calls[0][0].id).toBe(711);

    fireEvent.keyDown(window, { key: "ArrowRight" });
    expect(onNavigateDeal).toHaveBeenCalledWith(1);
  });

  it("NotificationDrawer: 읽지 않은 알림 점 표시 및 '모두 읽음' 클릭 시 읽음 상태를 저장한다", async () => {
    localStorage.setItem("insightdeal_web_device_uuid", "web_v11f_notif");
    localStorage.setItem("insightdeal_web_device_token", "tok_v11f_notif");

    vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
      const url = typeof input === "string" ? input : input instanceof URL ? input.toString() : input.url;
      if (url.includes("/api/users/notifications")) {
        return new Response(
          JSON.stringify([
            {
              id: 301,
              title: "새 로보락 핫딜 도착",
              keyword: "로보락",
              deal_url: "https://example.com/deal/301",
              received_at: "2026-11-21T09:00:00Z",
            },
          ]),
          { status: 200 }
        );
      }
      if (url.includes("/api/wishlist/")) {
        return new Response(JSON.stringify([]), { status: 200 });
      }
      return new Response(JSON.stringify({}), { status: 200 });
    });

    render(
      <NotificationDrawer
        isOpen={true}
        onClose={vi.fn()}
        deals={[]}
        onSelectDeal={vi.fn()}
      />
    );

    await waitFor(() => {
      expect(screen.getByLabelText("읽지 않은 알림")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByText("모두 읽음"));

    await waitFor(() => {
      expect(screen.queryByLabelText("읽지 않은 알림")).toBeNull();
    });
    const stored = JSON.parse(localStorage.getItem("insightdeal_read_notif_ids") || "[]");
    expect(stored).toContain("301");
  });

  it("PriceTrendChart: '1년'과 '전체' 기간 탭을 렌더링한다", () => {
    render(
      <PriceTrendChart
        basePrice={19900}
        dealId={880}
        currency="KRW"
        initialHistory={[
          { price: 22000, originalPrice: null, discountRate: null, recordedAt: "2026-01-01T00:00:00Z", source: "뽐뿌" },
          { price: 19900, originalPrice: null, discountRate: null, recordedAt: "2026-11-21T00:00:00Z", source: "뽐뿌" },
        ]}
        initialHistoryPeriod="1y"
      />
    );

    expect(screen.getByRole("button", { name: "1년" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "전체" })).toBeInTheDocument();
  });
});
