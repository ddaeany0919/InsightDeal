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

    // 종료딜 포함 토글 -> include_closed=true 파라미터 전송 검증
    const includeClosedBtn = screen.getByRole("button", { name: "종료딜 포함" });
    fireEvent.click(includeClosedBtn);
    await waitFor(() => {
      expect(includeClosedBtn).toHaveAttribute("aria-pressed", "true");
      expect(hotDealCalls.some((u) => u.includes("include_closed=true"))).toBe(true);
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

  it("InAppBrowserBanner는 Android 카톡 UA에서 intent://를 제공하고 토스 미니앱 UA에서는 숨긴다", () => {
    const originalUa = navigator.userAgent;

    Object.defineProperty(navigator, "userAgent", {
      configurable: true,
      value: "Mozilla/5.0 (Linux; Android 14) KAKAOTALK 10.5.0",
    });
    const { rerender, unmount } = render(<InAppBrowserBanner />);
    const openLink = screen.getByRole("link", { name: /외부 브라우저로 열기/ });
    expect(openLink.getAttribute("href")).toMatch(/^intent:\/\//);
    expect(openLink.getAttribute("href")).toContain("package=com.android.chrome");
    unmount();

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
    void rerender;
  });

  it("TrendingKeywordsTicker 펼침 버튼에 aria-expanded가 정상 토글된다", () => {
    render(
      <TrendingKeywordsTicker
        keywords={["제로콜라", "아이폰"]}
        rankings={[]}
        selectedKeyword=""
        onSelectKeyword={vi.fn()}
      />
    );

    const expandBtn = screen.getByRole("button", { name: /전체 순위/ });
    expect(expandBtn).toHaveAttribute("aria-expanded", "false");
    fireEvent.click(expandBtn);
    expect(screen.getByRole("button", { name: /순위 접기/ })).toHaveAttribute(
      "aria-expanded",
      "true"
    );
  });
});
