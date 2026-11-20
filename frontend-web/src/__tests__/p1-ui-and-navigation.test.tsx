import React from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import ThemeToggle from "@/app/components/ThemeToggle";
import DealCard from "@/app/components/DealCard";
import WishlistDrawer from "@/app/components/WishlistDrawer";
import InAppBrowserBanner from "@/app/components/InAppBrowserBanner";
import TrendingKeywordsTicker from "@/app/components/TrendingKeywordsTicker";
import DealDetailModal from "@/app/components/DealDetailModal";
import PriceTrendChart from "@/app/components/PriceTrendChart";
import ImageLightboxModal from "@/app/components/ImageLightboxModal";
import Home from "@/app/page";
import {
  PLATFORMS,
  computeUnitPriceBadge,
  extractPaymentConditionBadge,
  upgradeLowresImageUrl,
} from "@/app/lib/utils";
import type { Deal } from "@/app/lib/types";

vi.mock("next/link", () => ({
  default: ({
    href,
    children,
    ...rest
  }: React.AnchorHTMLAttributes<HTMLAnchorElement> & { href: string }) => (
    <a href={href} {...rest}>
      {children}
    </a>
  ),
}));

function buildDeal(id: number, overrides: Partial<Deal> = {}): Deal {
  return {
    id,
    community_name: "뽐뿌",
    shop_name: "쿠팡",
    title: `[쿠팡] 테스트 상품 ${id}`,
    price: "19900",
    image_url: `https://cdn.example.com/p1-${id}.jpg`,
    scraped_at: "방금 전",
    post_link: `https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=${id}`,
    ecommerce_url: `https://www.coupang.com/vp/products/${id}`,
    currency: "KRW",
    category: "생활용품",
    isClosed: false,
    isSearchFallback: false,
    ...overrides,
  };
}

describe("P1 UI 리뉴얼 · 테마 · 11종 플랫폼 · 이미지 폴백 · 인앱 배너 검증", () => {
  afterEach(() => {
    try {
      window.history.replaceState(null, "", "/");
    } catch {}
  });

  it("ThemeToggle 3단 전환 시 dataset.theme, localStorage, aria-pressed가 동기화된다", () => {
    render(<ThemeToggle />);

    const sysBtn = screen.getByRole("button", { name: /시스템 테마/ });
    const lightBtn = screen.getByRole("button", { name: /라이트 모드/ });
    const darkBtn = screen.getByRole("button", { name: /다크 모드/ });

    expect(sysBtn).toHaveAttribute("aria-pressed", "true");

    fireEvent.click(darkBtn);
    expect(document.documentElement.dataset.theme).toBe("dark");
    expect(localStorage.getItem("insightdeal_theme")).toBe("dark");
    expect(darkBtn).toHaveAttribute("aria-pressed", "true");
    expect(sysBtn).toHaveAttribute("aria-pressed", "false");

    fireEvent.click(lightBtn);
    expect(document.documentElement.dataset.theme).toBe("light");
    expect(localStorage.getItem("insightdeal_theme")).toBe("light");
    expect(lightBtn).toHaveAttribute("aria-pressed", "true");

    fireEvent.click(sysBtn);
    expect(document.documentElement.dataset.theme).toBeUndefined();
    expect(localStorage.getItem("insightdeal_theme")).toBe("system");
    expect(sysBtn).toHaveAttribute("aria-pressed", "true");
  });

  it("PLATFORMS 11종 순서(뽐뿌해외 포함)가 정확히 일치하고 Home에서 서버 필터·하이드레이션을 수행한다", async () => {
    expect(Array.from(PLATFORMS)).toEqual([
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
    ]);

    const hotDealCalls: string[] = [];
    vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
      const url = typeof input === "string" ? input : input instanceof URL ? input.toString() : input.url;
      if (url.includes("/api/meta/categories")) {
        return new Response(JSON.stringify({ categories: [] }), { status: 200 });
      }
      if (url.includes("/api/community/popular-keywords")) {
        return new Response(JSON.stringify({ keywords: ["제로콜라"], rankings: [] }), { status: 200 });
      }
      if (url.includes("/api/push/device")) {
        return new Response(
          JSON.stringify({ status: "ok", device_uuid: "web_u1", device_token: "tok_u1" }),
          { status: 200 }
        );
      }
      if (url.includes("/api/users/notifications")) {
        return new Response(JSON.stringify([]), { status: 200 });
      }
      if (url.includes("/api/wishlist/")) {
        return new Response(
          JSON.stringify([
            {
              id: 88,
              keyword: "해외 직구 테스트",
              target_price: 15000,
              user_id: "web_u1",
              alert_enabled: true,
            },
          ]),
          { status: 200 }
        );
      }
      if (url.includes("/api/community/hot-deals")) {
        hotDealCalls.push(url);
        return new Response(
          JSON.stringify({
            deals: [
              {
                id: 301,
                title: "[알리] 해외 직구 테스트",
                price: 19900,
                currency: "KRW",
                site_name: "뽐뿌해외",
                is_search_fallback: true,
                is_closed: false,
              },
            ],
            has_more: false,
            next_cursor: null,
          }),
          { status: 200 }
        );
      }
      return new Response(JSON.stringify({ ok: true }), { status: 200 });
    });

    render(<Home />);

    await waitFor(() => {
      expect(screen.getByText("해외 직구 테스트")).toBeInTheDocument();
    });

    // 검색이동 소형 배지 노출 확인
    expect(screen.getByText("검색이동")).toBeInTheDocument();

    // 뽐뿌해외 칩 클릭 -> platform=뽐뿌해외 서버 필터 및 aria-pressed 검증
    const ppomOverseasBtn = screen.getByRole("button", { name: "뽐뿌해외" });
    fireEvent.click(ppomOverseasBtn);
    await waitFor(() => {
      expect(ppomOverseasBtn).toHaveAttribute("aria-pressed", "true");
      expect(
        hotDealCalls.some((u) => u.includes(encodeURIComponent("뽐뿌해외")))
      ).toBe(true);
    });

    // 종료 포함 토글 -> include_closed=true 파라미터 전송 검증
    const includeClosedBtn = screen.getByRole("button", { name: "종료 포함" });
    fireEvent.click(includeClosedBtn);
    await waitFor(() => {
      expect(includeClosedBtn).toHaveAttribute("aria-pressed", "true");
      expect(hotDealCalls.some((u) => u.includes("include_closed=true"))).toBe(true);
    });
  });

  it("로딩 중 스켈레톤 카드 6개(.deal-skeleton-card)를 렌더링하고, 커뮤니티·카테고리 다중 선택·재클릭 해제·전체 펼치기·필터 보존을 수행한다", async () => {
    let resolveInitialFetch!: (res: Response) => void;
    let isFirstHotDealsCall = true;
    const hotDealCalls: string[] = [];

    vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
      const url =
        typeof input === "string"
          ? input
          : input instanceof URL
            ? input.toString()
            : input.url;

      if (url.includes("/api/meta/categories")) {
        return new Response(JSON.stringify({ categories: [] }), { status: 200 });
      }
      if (url.includes("/api/community/popular-keywords")) {
        return new Response(
          JSON.stringify({
            keywords: ["제로콜라", "로봇청소기"],
            rankings: [
              { rank: 1, keyword: "제로콜라", deal_count: 12, recent_count: 4, status: "HOT" },
              { rank: 2, keyword: "로봇청소기", deal_count: 7, recent_count: 3, status: "NEW" },
            ],
          }),
          { status: 200 }
        );
      }
      if (url.includes("/api/push/device")) {
        return new Response(
          JSON.stringify({ status: "ok", device_uuid: "web_multi", device_token: "tok_multi" }),
          { status: 200 }
        );
      }
      if (url.includes("/api/users/notifications")) {
        return new Response(JSON.stringify([]), { status: 200 });
      }
      if (url.includes("/api/wishlist/")) {
        return new Response(JSON.stringify([]), { status: 200 });
      }
      if (url.includes("/api/community/hot-deals")) {
        hotDealCalls.push(url);
        if (isFirstHotDealsCall) {
          isFirstHotDealsCall = false;
          return new Promise<Response>((resolve) => {
            resolveInitialFetch = resolve;
          });
        }
        if (url.includes("keyword=%EC%A0%9C%EB%A1%9C%EC%BD%9C%EB%9D%BC") && !url.includes("empty")) {
          return new Response(
            JSON.stringify({
              deals: [],
              has_more: false,
              next_cursor: null,
            }),
            { status: 200 }
          );
        }
        return new Response(
          JSON.stringify({
            deals: [
              {
                id: 711,
                title: "[쿠팡] 뽐뿌 다중 필터 딜",
                price: 19900,
                currency: "KRW",
                site_name: "뽐뿌",
                is_closed: false,
              },
              {
                id: 712,
                title: "[네이버] 펨코 다중 필터 딜",
                price: 25900,
                currency: "KRW",
                site_name: "펨코",
                is_closed: false,
              },
            ],
            has_more: false,
            next_cursor: null,
          }),
          { status: 200 }
        );
      }
      return new Response(JSON.stringify({ ok: true }), { status: 200 });
    });

    const { container } = render(<Home />);

    // 1. 초기 로딩 중 스켈레톤 카드 6개 및 스크린리더 상태 메시지 확인
    await waitFor(() => {
      expect(container.querySelectorAll(".deal-skeleton-card")).toHaveLength(6);
      expect(screen.getByText("핫딜을 불러오고 있어요")).toBeInTheDocument();
    });

    // 초기 응답 도착 -> 스켈레톤 제거 및 실데이터 표시
    resolveInitialFetch(
      new Response(
        JSON.stringify({
          deals: [
            {
              id: 710,
              title: "[지마켓] 초기 로드 딜",
              price: 12900,
              currency: "KRW",
              site_name: "뽐뿌",
              is_closed: false,
            },
          ],
          has_more: false,
          next_cursor: null,
        }),
        { status: 200 }
      )
    );

    await waitFor(() => {
      expect(screen.getByText("초기 로드 딜")).toBeInTheDocument();
    });
    expect(container.querySelectorAll(".deal-skeleton-card")).toHaveLength(0);

    // 2. 커뮤니티 다중 선택 (뽐뿌 + 펨코) 및 클라이언트 결과 양립 통과 검증
    const ppomBtn = screen.getByRole("button", { name: "뽐뿌" });
    const fmkoreaBtn = screen.getByRole("button", { name: "펨코" });
    const allCommunityBtn = screen.getByRole("button", { name: "전체 커뮤니티" });

    fireEvent.click(ppomBtn);
    expect(ppomBtn).toHaveAttribute("aria-pressed", "true");
    expect(allCommunityBtn).toHaveAttribute("aria-pressed", "false");

    fireEvent.click(fmkoreaBtn);
    expect(ppomBtn).toHaveAttribute("aria-pressed", "true");
    expect(fmkoreaBtn).toHaveAttribute("aria-pressed", "true");

    await waitFor(() => {
      expect(
        hotDealCalls.some((u) =>
          u.includes(`platform=${encodeURIComponent("뽐뿌,펨코")}`)
        )
      ).toBe(true);
      expect(screen.getByText("뽐뿌 다중 필터 딜")).toBeInTheDocument();
      expect(screen.getByText("펨코 다중 필터 딜")).toBeInTheDocument();
    });

    // 적용 필터 바 개별 해제 버튼 확인 -> [커뮤니티: 뽐뿌 ×] 클릭 시 뽐뿌만 제거되고 펨코는 유지
    const removePpomFilterBtn = screen.getByRole("button", { name: /커뮤니티: 뽐뿌/ });
    expect(removePpomFilterBtn).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /커뮤니티: 펨코/ })).toBeInTheDocument();
    fireEvent.click(removePpomFilterBtn);
    expect(ppomBtn).toHaveAttribute("aria-pressed", "false");
    expect(fmkoreaBtn).toHaveAttribute("aria-pressed", "true");
    await waitFor(() => {
      expect(
        hotDealCalls.some((u) => u.includes(`platform=${encodeURIComponent("펨코")}`))
      ).toBe(true);
    });

    // 3. 이미 선택된 칩 재클릭 해제 (펨코 해제 -> 전체 커뮤니티 자동 복귀)
    fireEvent.click(fmkoreaBtn);
    expect(fmkoreaBtn).toHaveAttribute("aria-pressed", "false");
    expect(allCommunityBtn).toHaveAttribute("aria-pressed", "true");

    // 3-1. 개별 커뮤니티 10개 모두 선택 시 "전체 커뮤니티"([])로 정규화
    const individualPlatforms = PLATFORMS.filter((p) => p !== "전체");
    for (const p of individualPlatforms) {
      fireEvent.click(screen.getByRole("button", { name: p }));
    }
    expect(allCommunityBtn).toHaveAttribute("aria-pressed", "true");
    for (const p of individualPlatforms) {
      expect(screen.getByRole("button", { name: p })).toHaveAttribute(
        "aria-pressed",
        "false"
      );
    }

    // 4. 카테고리 다중 선택 (가전/TV + PC/하드웨어) 및 재클릭 해제 + "전체 펼치기" 검증
    const tvCatBtn = screen.getByRole("button", { name: "가전/TV" });
    const pcCatBtn = screen.getByRole("button", { name: "PC/하드웨어" });
    const allCatBtn = screen.getByRole("button", { name: "전체" });

    fireEvent.click(tvCatBtn);
    fireEvent.click(pcCatBtn);
    expect(tvCatBtn).toHaveAttribute("aria-pressed", "true");
    expect(pcCatBtn).toHaveAttribute("aria-pressed", "true");
    expect(allCatBtn).toHaveAttribute("aria-pressed", "false");
    await waitFor(() => {
      expect(
        hotDealCalls.some((u) =>
          u.includes(`category=${encodeURIComponent("가전제품,PC용품")}`)
        )
      ).toBe(true);
    });

    fireEvent.click(tvCatBtn);
    expect(tvCatBtn).toHaveAttribute("aria-pressed", "false");
    expect(pcCatBtn).toHaveAttribute("aria-pressed", "true");

    fireEvent.click(pcCatBtn);
    expect(pcCatBtn).toHaveAttribute("aria-pressed", "false");
    expect(allCatBtn).toHaveAttribute("aria-pressed", "true");

    const catExpandBtn = screen.getByRole("button", { name: /전체 펼치기/ });
    expect(catExpandBtn).toHaveAttribute("aria-expanded", "false");
    fireEvent.click(catExpandBtn);
    const catCollapseBtn = screen.getByRole("button", { name: /접기/ });
    expect(catCollapseBtn).toHaveAttribute("aria-expanded", "true");
    expect(container.querySelector(".tabs-container.expanded")).not.toBeNull();
    expect(container.querySelector(".tabs.expanded")).not.toBeNull();

    // 5. 인기 검색어 클릭 시 기존 커뮤니티 필터 보존 + 결과 0건 조건 완화 버튼 검증
    fireEvent.click(ppomBtn);
    const tickerBtn = container.querySelector(".trending-ticker-left") as HTMLButtonElement;
    expect(tickerBtn).not.toBeNull();
    fireEvent.click(tickerBtn);

    expect(ppomBtn).toHaveAttribute("aria-pressed", "true");
    await waitFor(() => {
      expect(
        hotDealCalls.some(
          (u) =>
            u.includes(`platform=${encodeURIComponent("뽐뿌")}`) &&
            u.includes(`keyword=${encodeURIComponent("제로콜라")}`)
        )
      ).toBe(true);
      expect(screen.getByText("조건에 맞는 핫딜이 없어요")).toBeInTheDocument();
      expect(screen.getByRole("button", { name: "종료 포함해서 보기" })).toBeInTheDocument();
    });
  });

  it("DealCard와 WishlistDrawer는 2차 이미지 실패 시 무한 루프를 차단하고 'No Img' 없이 BrandImagePlaceholder를 렌더링한다", () => {
    const deal = buildDeal(401, { isSearchFallback: true });
    render(
      <DealCard
        deal={deal}
        appliedSearch=""
        isMounted={true}
        currentTime={Date.now()}
        isWishlisted={false}
        isAlertSet={true}
        onSelectDeal={vi.fn()}
        onShareDeal={vi.fn()}
        onToggleAlert={vi.fn()}
        onToggleWishlist={vi.fn()}
        onShowToast={vi.fn()}
      />
    );

    const cardImg = screen.getByAltText("테스트 상품 401") as HTMLImageElement;
    // 1차 onError: /api/proxy-image 폴백 및 dataset.fallbackTried = "1"
    fireEvent.error(cardImg);
    expect(cardImg.dataset.fallbackTried).toBe("1");
    expect(cardImg.getAttribute("src")).toBe(
      `/api/proxy-image?url=${encodeURIComponent("https://cdn.example.com/p1-401.jpg")}`
    );

    // 2차 onError: <img> 제거되고 BrandImagePlaceholder("이미지 없음") 전환
    fireEvent.error(cardImg);
    expect(screen.queryByAltText("테스트 상품 401")).toBeNull();
    expect(screen.getByText("이미지 없음")).toBeInTheDocument();

    // WishlistDrawer에서 이미지 없을 때 "No Img" 영문 미노출 검증
    render(
      <WishlistDrawer
        isOpen={true}
        onClose={vi.fn()}
        wishlistDeals={[buildDeal(402, { image_url: null })]}
        onRemoveDeal={vi.fn()}
        isLoggedIn={true}
        onLoginSimulate={vi.fn()}
      />
    );
    expect(screen.queryByText("No Img")).toBeNull();
  });

  it("InAppBrowserBanner는 Android 카톡 UA에서 Chrome intent://를, iOS에서 주소 복사하기를 제공하고 토스 미니앱 UA에서는 숨긴다", async () => {
    const originalUa = navigator.userAgent;
    const originalClipboard = navigator.clipboard;

    Object.defineProperty(navigator, "userAgent", {
      configurable: true,
      value: "Mozilla/5.0 (Linux; Android 14) KAKAOTALK 10.5.0",
    });
    const { rerender, unmount } = render(<InAppBrowserBanner />);
    const openLink = screen.getByRole("link", { name: /Chrome으로 열기/ });
    expect(openLink.getAttribute("href")).toMatch(/^intent:\/\//);
    expect(openLink.getAttribute("href")).toContain("package=com.android.chrome");
    unmount();

    // iOS 카톡 UA -> "주소 복사하기" 버튼 및 토스트 안내 확인
    Object.defineProperty(navigator, "userAgent", {
      configurable: true,
      value: "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) KAKAOTALK 10.5.0",
    });
    Object.defineProperty(navigator, "clipboard", {
      configurable: true,
      value: { writeText: vi.fn().mockResolvedValue(undefined) },
    });
    const onShowToast = vi.fn();
    const { unmount: unmountIos } = render(
      <InAppBrowserBanner onShowToast={onShowToast} />
    );
    expect(
      screen.getByText("외부 브라우저로 열면 알림과 결제 이동이 더 원활해요")
    ).toBeInTheDocument();
    const copyBtn = screen.getByRole("button", { name: /주소 복사하기/ });
    fireEvent.click(copyBtn);
    await waitFor(() => {
      expect(onShowToast).toHaveBeenCalledWith(
        "주소를 복사했어요. Safari 주소창에 붙여넣어 열어주세요"
      );
    });
    unmountIos();

    // 토스 앱 내에서는 절대 미노출
    Object.defineProperty(navigator, "userAgent", {
      configurable: true,
      value: "Mozilla/5.0 (Linux; Android 14) TossApp/5.180.0",
    });
    const { container } = render(<InAppBrowserBanner />);
    expect(container.firstChild).toBeNull();

    Object.defineProperty(navigator, "userAgent", {
      configurable: true,
      value: originalUa,
    });
    Object.defineProperty(navigator, "clipboard", {
      configurable: true,
      value: originalClipboard,
    });
    void rerender;
  });

  it("TrendingKeywordsTicker는 한국어 배지(인기/신규)·건수 병기 및 aria-expanded 토글이 동작한다", () => {
    render(
      <TrendingKeywordsTicker
        keywords={["제로콜라", "아이폰"]}
        rankings={[
          { rank: 1, keyword: "제로콜라", deal_count: 12, recent_count: 4, status: "HOT" },
          { rank: 2, keyword: "아이폰", deal_count: 5, recent_count: 2, status: "NEW" },
        ]}
        selectedKeyword=""
        onSelectKeyword={vi.fn()}
      />
    );

    expect(screen.getByText("인기")).toBeInTheDocument();
    expect(screen.getByText("12건")).toBeInTheDocument();

    const expandBtn = screen.getByRole("button", { name: /전체 순위/ });
    expect(expandBtn).toHaveAttribute("aria-expanded", "false");
    fireEvent.click(expandBtn);
    expect(screen.getByRole("button", { name: /순위 접기/ })).toHaveAttribute(
      "aria-expanded",
      "true"
    );
    expect(screen.getByText("신규")).toBeInTheDocument();
    expect(screen.getByText("5건")).toBeInTheDocument();
  });

  it("홈 3단 공간 위계(.discovery-bar-grid·.filter-scope-dock·.feed-control-toolbar)와 상위 6개 카드 priority eager 로딩을 검증한다", async () => {
    const eightDeals = Array.from({ length: 8 }, (_, idx) => ({
      id: 1100 + idx,
      title: `[쿠팡] 우선로딩 테스트 상품 ${idx + 1}`,
      price: 19900 + idx * 1000,
      currency: "KRW",
      site_name: "뽐뿌",
      image_url: `https://cdn.example.com/prio-${idx + 1}.jpg`,
      is_closed: false,
    }));

    vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
      const url =
        typeof input === "string"
          ? input
          : input instanceof URL
            ? input.toString()
            : input.url;

      if (url.includes("/api/meta/categories")) {
        return new Response(JSON.stringify({ categories: [] }), { status: 200 });
      }
      if (url.includes("/api/community/popular-keywords")) {
        return new Response(
          JSON.stringify({
            keywords: ["제로콜라"],
            rankings: [
              { rank: 1, keyword: "제로콜라", deal_count: 9, recent_count: 3, status: "HOT" },
            ],
          }),
          { status: 200 }
        );
      }
      if (url.includes("/api/push/device")) {
        return new Response(
          JSON.stringify({ status: "ok", device_uuid: "web_ui3", device_token: "tok_ui3" }),
          { status: 200 }
        );
      }
      if (url.includes("/api/users/notifications") || url.includes("/api/wishlist/")) {
        return new Response(JSON.stringify([]), { status: 200 });
      }
      if (url.includes("/api/community/hot-deals")) {
        return new Response(
          JSON.stringify({ deals: eightDeals, has_more: false, next_cursor: null }),
          { status: 200 }
        );
      }
      return new Response(JSON.stringify({ ok: true }), { status: 200 });
    });

    const { container } = render(<Home />);

    await waitFor(() => {
      expect(screen.getByText("우선로딩 테스트 상품 1")).toBeInTheDocument();
      expect(screen.getByText("우선로딩 테스트 상품 8")).toBeInTheDocument();
    });

    // Tier 1: 헤더 구분선 및 접근성 버튼 마운트 확인
    expect(container.querySelector(".nav-divider")).not.toBeNull();
    expect(screen.getByRole("button", { name: "내 찜 목록 열기" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "알림 내역 열기" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "키워드 알림 설정" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "기기 동기화 및 계정 연결" })).toBeInTheDocument();
    expect(screen.getByRole("group", { name: "화면 테마 전환" })).toBeInTheDocument();

    // Tier 2-A: sr-only h1 + 통합 검색·급상승 그리드
    const heading = screen.getByRole("heading", { level: 1, name: "실시간 핫딜 모아보기" });
    expect(heading).toHaveClass("sr-only");
    const discoveryGrid = container.querySelector(".discovery-bar-grid");
    expect(discoveryGrid).not.toBeNull();
    expect(discoveryGrid?.querySelector(".search-container")).not.toBeNull();
    expect(discoveryGrid?.querySelector(".trending-ticker-wrapper")).not.toBeNull();
    expect(
      screen.getByPlaceholderText(
        "주요 커뮤니티 10곳 핫딜 검색 (예: 맥북 -파우치, 콜라 :15000)"
      )
    ).toBeInTheDocument();

    // Tier 2-B: 출처/카테고리 라벨 도크 및 .platform-chip / .tab 분리 렌더
    const dock = container.querySelector(".filter-scope-dock");
    expect(dock).not.toBeNull();
    expect(screen.getByText("출처")).toBeInTheDocument();
    expect(screen.getByText("카테고리")).toBeInTheDocument();
    expect(dock?.querySelectorAll(".platform-chip").length).toBe(11);
    expect(dock?.querySelectorAll(".tab").length).toBe(16);

    // Tier 3: .feed-control-toolbar가 .filter-scope-dock 뒤, .deal-list 직상단에 위치
    const toolbar = container.querySelector(".feed-control-toolbar");
    const dealList = container.querySelector(".deal-list");
    expect(toolbar).not.toBeNull();
    expect(dealList).not.toBeNull();
    expect(
      toolbar!.compareDocumentPosition(dealList!) & Node.DOCUMENT_POSITION_FOLLOWING
    ).toBeTruthy();
    expect(toolbar?.querySelector('[aria-label="정렬 기준"]')).not.toBeNull();

    // 상위 6개 카드 eager + fetchpriority="high", 7번째 이후 lazy
    for (let i = 1; i <= 8; i++) {
      const img = screen.getByAltText(`우선로딩 테스트 상품 ${i}`) as HTMLImageElement;
      expect(img.getAttribute("decoding")).toBe("async");
      if (i <= 6) {
        expect(img.getAttribute("loading")).toBe("eager");
        expect(img.getAttribute("fetchpriority")).toBe("high");
      } else {
        expect(img.getAttribute("loading")).toBe("lazy");
        expect(img.getAttribute("fetchpriority")).toBeNull();
      }
    }
  });

  it("PriceTrendChart는 initialHistory 제공 시 fetch 없이 0ms 동기 렌더링하고 기간 탭 클릭 시에만 /history를 호출한다", async () => {
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify([
          { price: 32000, recordedAt: "2026-10-20T00:00:00Z", originalPrice: null, discountRate: null, source: "뽐뿌" },
          { price: 27000, recordedAt: "2026-11-18T00:00:00Z", originalPrice: null, discountRate: null, source: "뽐뿌" },
        ]),
        { status: 200, headers: { "Content-Type": "application/json" } }
      )
    );
    const onHistoryStatus = vi.fn();

    const { container, rerender } = render(
      <PriceTrendChart
        dealId={9201}
        currency="KRW"
        initialHistory={[
          { price: 30000, recordedAt: "2026-11-10T00:00:00Z", originalPrice: null, discountRate: null, source: "뽐뿌" },
          { price: 25000, recordedAt: "2026-11-15T00:00:00Z", originalPrice: null, discountRate: null, source: "뽐뿌" },
          { price: 20000, recordedAt: "2026-11-19T00:00:00Z", originalPrice: null, discountRate: null, source: "뽐뿌" },
        ]}
        initialHistoryPeriod="7d_auto"
        onHistoryStatus={onHistoryStatus}
      />
    );

    // 0ms 동기 렌더링: 로딩 스피너 및 추가 fetch 없이 즉시 SVG 차트 표시
    expect(screen.queryByText(/불러오고 있어요/)).toBeNull();
    expect(screen.getByText("실거래 3건")).toBeInTheDocument();
    expect(container.querySelector("polyline")).not.toBeNull();
    expect(fetchSpy).not.toHaveBeenCalled();
    expect(onHistoryStatus).toHaveBeenCalledWith(true, {
      minPrice: 20000,
      avgPrice: 25000,
      maxPrice: 30000,
      latestPrice: 20000,
    });

    // SVG 내부 라벨 가독성 크기(>=11) 검증
    const svgTexts = Array.from(container.querySelectorAll("svg text"));
    expect(svgTexts.length).toBeGreaterThan(0);
    svgTexts.forEach((t) => {
      expect(Number(t.getAttribute("font-size"))).toBeGreaterThanOrEqual(11);
    });

    // 기간 탭('1개월') 클릭 시에만 백엔드 /history 호출
    fireEvent.click(screen.getByRole("button", { name: "1개월" }));
    await waitFor(() => {
      expect(fetchSpy).toHaveBeenCalledTimes(1);
      expect(String(fetchSpy.mock.calls[0][0])).toContain(
        "/api/community/deals/9201/history?period=1m"
      );
    });

    // <2건 initialHistory인 경우에도 fetch 없이 즉시 부족 안내 문구 표시
    fetchSpy.mockClear();
    rerender(
      <PriceTrendChart
        dealId={9202}
        currency="KRW"
        initialHistory={[
          { price: 15000, recordedAt: "2026-11-19T00:00:00Z", originalPrice: null, discountRate: null, source: "뽐뿌" },
          { price: 0, recordedAt: "2026-11-18T00:00:00Z", originalPrice: null, discountRate: null, source: "뽐뿌" },
        ]}
      />
    );
    expect(screen.queryByText(/불러오고 있어요/)).toBeNull();
    expect(screen.getByText("아직 비교할 가격 이력이 충분하지 않아요")).toBeInTheDocument();
    expect(fetchSpy).not.toHaveBeenCalled();
  });

  it("DealDetailModal은 initial_history 수신 시 0클릭 시세 요약 바를 표시하고 차트 아코디언을 본문 위로 전진 배치해 0ms 렌더링한다", async () => {
    localStorage.setItem("insightdeal_web_device_uuid", "web_0click");
    localStorage.setItem("insightdeal_web_device_token", "tok_0click");

    const fetchCalls: string[] = [];
    vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
      const url =
        typeof input === "string"
          ? input
          : input instanceof URL
            ? input.toString()
            : input.url;
      fetchCalls.push(url);
      return new Response(
        JSON.stringify({
          id: 802,
          title: "[쿠팡] 0클릭 시세 요약 테스트 상품",
          price: 20000,
          currency: "KRW",
          site_name: "뽐뿌",
          content_html: "<p>상세 본문 내용입니다</p>",
          history_count: 3,
          initial_history_period: "7d_auto",
          initial_history: [
            { price: 30000, recordedAt: "2026-11-10T00:00:00Z", originalPrice: null, discountRate: null, source: "뽐뿌" },
            { price: 25000, recordedAt: "2026-11-15T00:00:00Z", originalPrice: null, discountRate: null, source: "뽐뿌" },
            { price: 20000, recordedAt: "2026-11-19T00:00:00Z", originalPrice: null, discountRate: null, source: "뽐뿌" },
          ],
        }),
        { status: 200 }
      );
    });

    const deal = buildDeal(802, { title: "[쿠팡] 0클릭 시세 요약 테스트 상품", price: "20000" });
    render(<DealDetailModal deal={deal} onClose={vi.fn()} showToast={vi.fn()} />);

    // 0클릭 시세 요약 바 즉시 노출 (Summary Pill은 여전히 aria-expanded="false")
    await waitFor(() => {
      expect(screen.getByText("역대 최저가 구간 · 평균 대비 20% 저렴")).toBeInTheDocument();
      expect(screen.getByText("최저 20,000원 · 평균 25,000원 · 최고 30,000원")).toBeInTheDocument();
    });
    const chartPill = screen.getByRole("button", { name: /가격 추이 \(3건\)/ });
    expect(chartPill).toHaveAttribute("aria-expanded", "false");
    expect(fetchCalls).toHaveLength(1);

    // 가격 추이 아코디언이 상세 본문보다 DOM 상단에 전진 배치되었는지 확인
    const chartAccordion = document.getElementById("deal-accordion-chart-802");
    const bodyHeading = screen.getByText("상세 내용 및 구매 조건");
    expect(chartAccordion).not.toBeNull();
    expect(
      chartAccordion!.compareDocumentPosition(bodyHeading) &
        Node.DOCUMENT_POSITION_FOLLOWING
    ).toBeTruthy();

    // 가격 추이 클릭 시 추가 /history fetch 없이 즉시 0ms 차트 렌더링
    fireEvent.click(chartPill);
    expect(chartPill).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByText("실거래 3건")).toBeInTheDocument();
    expect(fetchCalls).toHaveLength(1);
  });

  it("ImageLightboxModal은 차단 도메인에 &w=1200을 보조 부착하고 인접 이미지 프리로드와 로딩 스피너를 제공한다", () => {
    const preloadedSrcs: string[] = [];
    const OrigImage = window.Image;
    class TrackingImage {
      referrerPolicy = "";
      private _src = "";
      set src(val: string) {
        this._src = val;
        preloadedSrcs.push(val);
      }
      get src() {
        return this._src;
      }
    }
    Object.defineProperty(window, "Image", {
      configurable: true,
      writable: true,
      value: TrackingImage,
    });

    try {
      render(
        <ImageLightboxModal
          isOpen={true}
          images={[
            "https://cdn.ppomppu.co.kr/img/main.jpg",
            "https://cdn.example.com/next-1.jpg",
            "https://cdn.ppomppu.co.kr/img/prev-1.jpg",
          ]}
          initialIndex={0}
          title="라이트박스 테스트"
          onClose={vi.fn()}
        />
      );

      // 로딩 스피너 초기 노출 및 고해상도 &w=1200 힌트 확인
      expect(screen.getByText("이미지 불러오는 중...")).toBeInTheDocument();
      const mainImg = screen.getByAltText("라이트박스 테스트 상세 이미지 1") as HTMLImageElement;
      expect(mainImg.getAttribute("src")).toBe(
        `/api/proxy-image?url=${encodeURIComponent("https://cdn.ppomppu.co.kr/img/main.jpg")}&w=1200`
      );

      // 인접 이미지(index 1, index 2) 백그라운드 프리로드 확인
      expect(preloadedSrcs).toContain(
        `https://wsrv.nl/?url=${encodeURIComponent("https://cdn.example.com/next-1.jpg")}&output=webp&w=1200&we=1`
      );
      expect(preloadedSrcs).toContain(
        `/api/proxy-image?url=${encodeURIComponent("https://cdn.ppomppu.co.kr/img/prev-1.jpg")}&w=1200`
      );

      // 이미지 로드 완료 시 스피너 제거
      fireEvent.load(mainImg);
      expect(screen.queryByText("이미지 불러오는 중...")).toBeNull();
    } finally {
      Object.defineProperty(window, "Image", {
        configurable: true,
        writable: true,
        value: OrigImage,
      });
    }
  });

  it("computeUnitPriceBadge는 식품·생필품 체감 단가를 계산하고 IT·가전·USD 오탐을 차단한다", () => {
    expect(computeUnitPriceBadge("[쿠팡] 비비고 육개장 500g x 6개", 17900, "KRW", "음식")).toEqual({
      label: "100g당 597원",
      detail: "총 3,000g · 개당 2,983원",
    });
    expect(computeUnitPriceBadge("[지마켓] 코카콜라 제로 당류 0g 355ml x 24캔", 16900, "KRW", "음식")).toEqual({
      label: "100ml당 198원",
      detail: "총 8,520ml · 개당 704원",
    });
    expect(computeUnitPriceBadge("[티몬] 베베숲 물티슈 70매 x 10팩", 15900, "KRW", "육아/유아용품")).toEqual({
      label: "1팩당 1,590원",
      detail: "70매 × 10팩 기준",
    });
    expect(computeUnitPriceBadge("[쿠팡] 두루마리 휴지 30롤", 18000, "KRW", "생활용품")).toEqual({
      label: "1롤당 600원",
      detail: "총 30롤 기준",
    });
    expect(computeUnitPriceBadge("농심 신라면 120g 5봉 x 4팩", 13800, "KRW", "음식")).toEqual({
      label: "100g당 575원",
      detail: "총 2,400g · 개당 690원",
    });
    expect(computeUnitPriceBadge("[알리] Netac NVMe SSD 1TB", 69000, "KRW", "PC용품")).toBeNull();
    expect(computeUnitPriceBadge("[쿠팡] 27인치 게이밍 모니터 165Hz", 199000, "KRW", "생활용품")).toBeNull();
    expect(computeUnitPriceBadge("[아마존] 비타민 500g", 2000, "USD", "음식")).toBeNull();
  });

  it("DealCard는 단가 배지와 1-Step /api/proxy-image 즉시 플레이스홀더를 동작시키고 Home은 h2.sr-only·모바일 단축 플레이스홀더·맨 위로 FAB를 제공한다", async () => {
    const dealUnit = buildDeal(901, {
      title: "[쿠팡] 코카콜라 제로 355ml x 24캔",
      price: "16900",
      category: "음식",
      image_url: "https://cdn.ppomppu.co.kr/zboard/expired_901.jpg",
    });
    render(
      <DealCard
        deal={dealUnit}
        appliedSearch=""
        isMounted={true}
        currentTime={Date.now()}
        isWishlisted={false}
        isAlertSet={false}
        onSelectDeal={vi.fn()}
        onShareDeal={vi.fn()}
        onToggleAlert={vi.fn()}
        onToggleWishlist={vi.fn()}
        onShowToast={vi.fn()}
      />
    );
    expect(screen.getByText("100ml당 198원")).toBeInTheDocument();

    const img = screen.getByAltText("코카콜라 제로 355ml x 24캔") as HTMLImageElement;
    expect(img.getAttribute("src")).toContain("/api/proxy-image");
    fireEvent.error(img);
    expect(screen.queryByAltText("코카콜라 제로 355ml x 24캔")).toBeNull();

    const origWidth = window.innerWidth;
    const origScrollTo = window.scrollTo;
    const scrollToSpy = vi.fn();
    Object.defineProperty(window, "innerWidth", { configurable: true, writable: true, value: 390 });
    Object.defineProperty(window, "scrollTo", { configurable: true, writable: true, value: scrollToSpy });

    vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
      const url = typeof input === "string" ? input : input instanceof URL ? input.toString() : input.url;
      if (url.includes("/api/meta/categories")) {
        return new Response(JSON.stringify({ categories: [], sources: [] }), { status: 200 });
      }
      if (url.includes("/api/community/popular-keywords")) {
        return new Response(JSON.stringify({ keywords: ["콜라"], rankings: [] }), { status: 200 });
      }
      if (url.includes("/api/push/device")) {
        return new Response(JSON.stringify({ status: "ok", device_uuid: "web_fab", device_token: "tok_fab" }), { status: 200 });
      }
      if (url.includes("/api/users/notifications") || url.includes("/api/wishlist/")) {
        return new Response(JSON.stringify([]), { status: 200 });
      }
      if (url.includes("/api/community/hot-deals")) {
        return new Response(
          JSON.stringify({
            deals: [
              {
                id: 901,
                title: dealUnit.title,
                price: 16900,
                currency: "KRW",
                site_name: "뽐뿌",
                shop_name: "쿠팡",
                post_url: dealUnit.post_link,
                ecommerce_url: dealUnit.ecommerce_url,
                image_url: dealUnit.image_url,
                category: "음식",
                honey_score: 120,
                indexed_at: "2026-11-20T04:00:00Z",
                is_closed: false,
              },
            ],
            has_more: false,
            next_cursor: null,
          }),
          { status: 200 }
        );
      }
      return new Response(JSON.stringify({ ok: true }), { status: 200 });
    });

    try {
      render(<Home />);
      await waitFor(() => {
        expect(screen.getByRole("heading", { level: 2, name: "실시간 핫딜 목록" })).toHaveClass("sr-only");
      });
      expect(
        screen.getByPlaceholderText("핫딜 검색 (맥북 -파우치, :15000)")
      ).toBeInTheDocument();

      Object.defineProperty(window, "scrollY", { configurable: true, writable: true, value: 720 });
      fireEvent.scroll(window);
      const scrollTopBtn = await screen.findByRole("button", { name: "맨 위로 이동" });
      fireEvent.click(scrollTopBtn);
      expect(scrollToSpy).toHaveBeenCalledWith({ top: 0, behavior: "smooth" });
    } finally {
      Object.defineProperty(window, "innerWidth", { configurable: true, writable: true, value: origWidth });
      Object.defineProperty(window, "scrollY", { configurable: true, writable: true, value: 0 });
      Object.defineProperty(window, "scrollTo", { configurable: true, writable: true, value: origScrollTo });
    }
  });

  it("Wave 1: 단위가격 확장 파서(1:1 패리티), kakaocdn 고해상도 변환, URL 쿼리 복원 및 출처 더보기·딥링크 뒤로가기를 검증한다", async () => {
    // 1. 단위가격 확장 파서 및 오탐 차단 검증
    expect(computeUnitPriceBadge("삼다수 무라벨 500mlx2개+2개", 4000, "KRW", "음식")).toEqual({
      label: "100ml당 200원",
      detail: "총 2,000ml · 개당 1,000원",
    });
    expect(computeUnitPriceBadge("종근당 락토핏 (2g x 30포) 2박스", 12900, "KRW", "음식")).toEqual({
      label: "1포당 215원",
      detail: "총 60포 · 120g 기준",
    });
    expect(computeUnitPriceBadge("피죤 리치퍼퓸 4.2L 본품+2L 리필 2개", 25200, "KRW", "생활용품")).toEqual({
      label: "100ml당 307원",
      detail: "총 8,200ml 기준",
    });
    expect(computeUnitPriceBadge("김치 900g 3종 세트", 18900, "KRW", "음식")).toEqual({
      label: "100g당 700원",
      detail: "총 2,700g · 개당 6,300원",
    });
    expect(computeUnitPriceBadge("동물복지 계란 특란 30구 x 2판", 15800, "KRW", "음식")).toEqual({
      label: "1구당 263원",
      detail: "총 60구 기준",
    });
    expect(computeUnitPriceBadge("[알리] 8구 개별스위치 멀티탭 2M", 12900, "KRW", "생활용품")).toBeNull();
    expect(computeUnitPriceBadge("애슐리 딥디쉬 피자 시카고2+고구마2", 19900, "KRW", "음식")).toEqual({
      label: "개당 4,975원",
      detail: "총 4개 기준",
    });
    expect(computeUnitPriceBadge("[네이버] 레스노베 워터 앰플 미스트60ml", 4900, "KRW", "뷰티")).toEqual({
      label: "10ml당 817원",
      detail: "총 60ml 기준",
    });
    expect(computeUnitPriceBadge("와인25플러스 모바일상품권 5만원권", 46560, "KRW", "모바일/기프티콘")).toEqual({
      label: "약 6.9% 할인",
      detail: "액면가 50,000원 기준",
    });
    expect(extractPaymentConditionBadge("[던킨도너츠] 네이버페이 결제시 50% 할인", "")).toBe("네이버페이 조건");
    expect(extractPaymentConditionBadge("[컴퓨존] 스위치2 포켓몬 레전드 ZA (우리카드)", "")).toBe("우리카드 조건");
    expect(extractPaymentConditionBadge("퍼실 세탁세제", "네멤무료")).toBe("네멤 조건");

    // 2. kakaocdn 저해상도·656x0 썸네일 고해상도(600x0_65_wr) 업그레이드·치유
    expect(
      upgradeLowresImageUrl("https://search1.kakaocdn.net/argon/130x130_85_c/6W6ac7UeBi1")
    ).toBe("https://search1.kakaocdn.net/argon/600x0_65_wr/6W6ac7UeBi1");
    expect(
      upgradeLowresImageUrl("https://search2.kakaocdn.net/argon/656x0_80_wr/Ij9QZhtv6N0")
    ).toBe("https://search2.kakaocdn.net/argon/600x0_65_wr/Ij9QZhtv6N0");

    // 3. Home URL 쿼리 복원(?platform=뽐뿌&sort=price_asc&hot=1) & 출처 더보기 토글
    window.history.replaceState(null, "", "/?platform=%EB%BD%90%EB%BF%8C&sort=price_asc&hot=1");
    const fetchCalls: string[] = [];
    vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
      const url = typeof input === "string" ? input : input instanceof URL ? input.toString() : input.url;
      fetchCalls.push(url);
      if (url.includes("/api/meta/categories")) {
        return new Response(JSON.stringify({ categories: [] }), { status: 200 });
      }
      if (url.includes("/api/community/popular-keywords")) {
        return new Response(JSON.stringify({ keywords: [], rankings: [] }), { status: 200 });
      }
      if (url.includes("/api/push/device")) {
        return new Response(JSON.stringify({ status: "ok", device_uuid: "web_w1", device_token: "tok_w1" }), { status: 200 });
      }
      if (url.includes("/api/users/notifications") || url.includes("/api/wishlist/")) {
        return new Response(JSON.stringify([]), { status: 200 });
      }
      if (url.includes("/api/community/hot-deals")) {
        return new Response(JSON.stringify({ deals: [], has_more: false, next_cursor: null }), { status: 200 });
      }
      return new Response(JSON.stringify({ ok: true }), { status: 200 });
    });

    const { container } = render(<Home />);
    await waitFor(() => {
      expect(
        fetchCalls.some(
          (u) =>
            u.includes("platform=%EB%BD%90%EB%BF%8C") &&
            u.includes("sort=price_asc") &&
            u.includes("only_hot=true")
        )
      ).toBe(true);
    });
    const platformExpandBtn = screen.getByRole("button", { name: /출처 더보기/ });
    fireEvent.click(platformExpandBtn);
    expect(container.querySelector(".platform-chips-row.expanded")).not.toBeNull();
    expect(screen.getByRole("button", { name: /출처 접기/ })).toHaveAttribute("aria-expanded", "true");

    // 4. DealDetailModal 딥링크 진입 시 history.back() 오발동 차단 & 닫기 시 URL dealId 제거
    const backSpy = vi.spyOn(window.history, "back").mockImplementation(() => {});
    window.history.replaceState(null, "", "/?dealId=902");
    const onCloseDeep = vi.fn();
    const { unmount: unmountDeep } = render(
      <DealDetailModal
        deal={buildDeal(902)}
        onClose={onCloseDeep}
        showToast={vi.fn()}
      />
    );
    const closeBtn = screen.getByRole("button", { name: "상세 모달 닫기" });
    fireEvent.click(closeBtn);
    expect(backSpy).not.toHaveBeenCalled();
    expect(onCloseDeep).toHaveBeenCalledTimes(1);
    expect(window.location.search).not.toContain("dealId=902");
    unmountDeep();
    backSpy.mockRestore();

    // 5. 라이트박스 오픈 후 브라우저 뒤로가기(popstate) 1회는 모달을 닫지 않고 라이트박스만 닫는다
    window.history.replaceState(null, "", "/");
    const onCloseModal = vi.fn();
    render(
      <DealDetailModal
        deal={buildDeal(903, { image_url: "https://cdn.example.com/p-903.jpg" })}
        onClose={onCloseModal}
        showToast={vi.fn()}
      />
    );
    const zoomBtn = screen.getByRole("button", { name: /상품 이미지 확대 보기/ });
    fireEvent.click(zoomBtn);
    await waitFor(() => {
      expect(window.history.state?.insightDealLightbox).toBe(true);
    });
    fireEvent(window, new PopStateEvent("popstate", { state: { insightDealModal: 903 } }));
    expect(onCloseModal).not.toHaveBeenCalled();
    fireEvent(window, new PopStateEvent("popstate", { state: null }));
    expect(onCloseModal).toHaveBeenCalledTimes(1);
  });
});
