import React from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import DealCard from "@/app/components/DealCard";
import DealDetailModal from "@/app/components/DealDetailModal";
import Home from "@/app/page";
import type { Deal } from "@/app/lib/types";

vi.mock("next/link", () => ({
  default: ({
    href,
    children,
    prefetch,
    ...rest
  }: React.AnchorHTMLAttributes<HTMLAnchorElement> & {
    href: string;
    prefetch?: boolean;
  }) => (
    <a
      href={href}
      data-prefetch={prefetch !== undefined ? String(prefetch) : undefined}
      {...rest}
    >
      {children}
    </a>
  ),
}));

function buildDeal(id: number, overrides: Partial<Deal> = {}): Deal {
  return {
    id,
    community_name: "뽐뿌",
    shop_name: "쿠팡",
    title: `[쿠팡] v2.0 테스트 상품 ${id}`,
    price: "19900",
    image_url: `https://cdn.example.com/v2-${id}.jpg`,
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

function mockHomeApis(dealsPayload: Record<string, unknown>[] = []) {
  return vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
    const url =
      typeof input === "string"
        ? input
        : input instanceof URL
          ? input.toString()
          : input.url;

    if (url.includes("/api/meta/categories")) {
      return new Response(JSON.stringify({ categories: [], sources: [] }), {
        status: 200,
      });
    }
    if (url.includes("/api/community/popular-keywords")) {
      return new Response(JSON.stringify({ keywords: [], rankings: [] }), {
        status: 200,
      });
    }
    if (url.includes("/api/push/device")) {
      return new Response(
        JSON.stringify({
          status: "ok",
          device_uuid: "web_v2_uuid",
          device_token: "tok_v2_token",
        }),
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
      return new Response(
        JSON.stringify({
          deals: dealsPayload,
          has_more: false,
          next_cursor: null,
        }),
        { status: 200 }
      );
    }
    return new Response(JSON.stringify({ ok: true }), { status: 200 });
  });
}

describe("v2.0 Dynamic Commerce UI 모션·스크롤 헤더·모바일 엄지 도크·회귀 검증", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
    localStorage.clear();
    Object.defineProperty(window, "scrollY", {
      configurable: true,
      writable: true,
      value: 0,
    });
  });

  it("찜하기 클릭 시 aria-pressed가 전환되고 deal-action-btn--heart-pop 클래스가 적용된다", async () => {
    const deal = buildDeal(2001);
    const fixedNow = 1795510000000;
    function WishlistHarness() {
      const [wishlisted, setWishlisted] = React.useState(false);
      return (
        <DealCard
          deal={deal}
          appliedSearch=""
          isMounted={true}
          currentTime={fixedNow}
          isWishlisted={wishlisted}
          isAlertSet={false}
          onSelectDeal={vi.fn()}
          onShareDeal={vi.fn()}
          onToggleAlert={vi.fn()}
          onToggleWishlist={() => setWishlisted((prev) => !prev)}
          onShowToast={vi.fn()}
        />
      );
    }

    render(<WishlistHarness />);
    const wishBtn = screen.getByRole("button", { name: "찜하기" });
    expect(wishBtn).toHaveAttribute("aria-pressed", "false");
    expect(wishBtn).not.toHaveClass("deal-action-btn--heart-pop");

    fireEvent.click(wishBtn);

    expect(wishBtn).toHaveAttribute("aria-pressed", "true");
    expect(wishBtn).toHaveClass("deal-action-btn--heart-pop");

    await waitFor(
      () => {
        expect(wishBtn).not.toHaveClass("deal-action-btn--heart-pop");
      },
      { timeout: 1000 }
    );
  });

  it("스크롤 히스테리시스에 따라 window.scrollY=200에서 navbar--scrolled가 적용되고 window.scrollY=10에서 해제된다", async () => {
    mockHomeApis([
      {
        id: 2002,
        title: "[쿠팡] 스크롤 헤더 테스트 상품",
        price: 15900,
        currency: "KRW",
        site_name: "뽐뿌",
        is_closed: false,
      },
    ]);

    const { container } = render(<Home />);
    await waitFor(() => {
      expect(screen.getByText("스크롤 헤더 테스트 상품")).toBeInTheDocument();
    });

    const header = container.querySelector("header.navbar");
    expect(header).not.toBeNull();
    expect(header).not.toHaveClass("navbar--scrolled");

    Object.defineProperty(window, "scrollY", {
      configurable: true,
      writable: true,
      value: 200,
    });
    fireEvent.scroll(window);
    expect(header).toHaveClass("navbar--scrolled");

    // 히스테리시스 구간(45~110px)에서는 축소 상태 유지
    Object.defineProperty(window, "scrollY", {
      configurable: true,
      writable: true,
      value: 70,
    });
    fireEvent.scroll(window);
    expect(header).toHaveClass("navbar--scrolled");

    // 45px 미만으로 내려오면 해제
    Object.defineProperty(window, "scrollY", {
      configurable: true,
      writable: true,
      value: 10,
    });
    fireEvent.scroll(window);
    expect(header).not.toHaveClass("navbar--scrolled");
  });

  it("로딩 중 .deal-skeleton-card 6개를 유지하고 모바일 뷰포트에서 .mobile-thumb-dock 빠른 필터 토글이 동작한다", async () => {
    const origWidth = window.innerWidth;
    Object.defineProperty(window, "innerWidth", {
      configurable: true,
      writable: true,
      value: 390,
    });

    let resolveDeals!: (res: Response) => void;
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
        return new Response(JSON.stringify({ keywords: [], rankings: [] }), {
          status: 200,
        });
      }
      if (url.includes("/api/push/device")) {
        return new Response(
          JSON.stringify({
            status: "ok",
            device_uuid: "web_v2_mob",
            device_token: "tok_v2_mob",
          }),
          { status: 200 }
        );
      }
      if (url.includes("/api/users/notifications") || url.includes("/api/wishlist/")) {
        return new Response(JSON.stringify([]), { status: 200 });
      }
      if (url.includes("/api/community/hot-deals")) {
        return new Promise<Response>((resolve) => {
          resolveDeals = resolve;
        });
      }
      return new Response(JSON.stringify({ ok: true }), { status: 200 });
    });

    try {
      const { container } = render(<Home />);
      await waitFor(() => {
        expect(container.querySelectorAll(".deal-skeleton-card")).toHaveLength(6);
      });

      resolveDeals(
        new Response(
          JSON.stringify({
            deals: [
              {
                id: 2003,
                title: "[쿠팡] 모바일 엄지 도크 테스트",
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
        expect(screen.getByText("모바일 엄지 도크 테스트")).toBeInTheDocument();
      });

      const dock = container.querySelector(".mobile-thumb-dock");
      expect(dock).not.toBeNull();

      const dockFilterBtn = screen.getByRole("button", {
        name: "빠른 상세 필터 열기",
      });
      expect(screen.queryByRole("dialog", { name: "상세 필터" })).toBeNull();

      fireEvent.click(dockFilterBtn);
      expect(
        screen.getByRole("dialog", { name: "상세 필터" })
      ).toBeInTheDocument();

      fireEvent.click(screen.getByRole("button", { name: "상세 필터 닫기" }));
      expect(screen.queryByRole("dialog", { name: "상세 필터" })).toBeNull();
    } finally {
      Object.defineProperty(window, "innerWidth", {
        configurable: true,
        writable: true,
        value: origWidth,
      });
    }
  });

  it("DealDetailModal은 커머스 톤 핵심 체크와 기존 접근성 계약(핫딜 핵심 요약 카드, 가격 추이, 상세 모달 닫기)을 유지한다", async () => {
    localStorage.setItem("insightdeal_web_device_uuid", "web_v2_modal");
    localStorage.setItem("insightdeal_web_device_token", "tok_v2_modal");

    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify({
          id: 2004,
          title: "[네이버] 모달 계약 검증 상품",
          price: 29000,
          currency: "KRW",
          site_name: "뽐뿌",
          content_html: "<p>짧은 본문</p>",
          history_count: 4,
          is_closed: false,
        }),
        { status: 200 }
      )
    );

    render(
      <DealDetailModal
        deal={buildDeal(2004, { title: "[네이버] 모달 계약 검증 상품", price: "29000" })}
        onClose={vi.fn()}
        showToast={vi.fn()}
      />
    );

    expect(await screen.findByLabelText("핫딜 핵심 요약 카드")).toBeInTheDocument();
    expect(screen.getByText("핵심 3줄 체크")).toBeInTheDocument();
    expect(screen.getByText("가격 추이 (4건)")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "상세 모달 닫기" })).toBeInTheDocument();
  });
});
