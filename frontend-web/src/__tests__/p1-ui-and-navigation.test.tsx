import React from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import ThemeToggle from "@/app/components/ThemeToggle";
import DealCard from "@/app/components/DealCard";
import WishlistDrawer from "@/app/components/WishlistDrawer";
import InAppBrowserBanner from "@/app/components/InAppBrowserBanner";
import TrendingKeywordsTicker from "@/app/components/TrendingKeywordsTicker";
import Home from "@/app/page";
import { PLATFORMS } from "@/app/lib/utils";
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
});
