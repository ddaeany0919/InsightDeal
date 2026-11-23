import React from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import RecentDealsTray from "@/app/components/RecentDealsTray";
import Home from "@/app/page";
import type { Deal } from "@/app/lib/types";
import { getProxyImageUrl, isBlockedCommunityUrl } from "@/app/lib/utils";

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

function makeRecentDeal(id: number, overrides: Partial<Deal> = {}): Deal {
  return {
    id,
    community_name: "뽐뿌",
    shop_name: "쿠팡",
    title: `[쿠팡] 테스트 최근 핫딜 ${id}`,
    price: String(10000 + id * 100),
    image_url: `https://cdn.example.com/recent-${id}.jpg`,
    scraped_at: "2026-11-23T10:00:00Z",
    post_link: `https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=${id}`,
    ecommerce_url: `https://www.coupang.com/vp/products/${id}`,
    currency: "KRW",
    category: "생활용품",
    isClosed: false,
    isFree: false,
    ...overrides,
  };
}

function mockHomeApis() {
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
          device_uuid: "web_recent_uuid",
          device_token: "web_recent_token",
        }),
        { status: 200 }
      );
    }
    if (url.includes("/api/users/notifications")) {
      return new Response(JSON.stringify({ notifications: [] }), {
        status: 200,
      });
    }
    if (url.includes("/api/wishlist")) {
      return new Response(JSON.stringify([]), { status: 200 });
    }
    if (url.includes("/api/community/hot-deals")) {
      return new Response(JSON.stringify({ deals: [], has_more: false }), {
        status: 200,
      });
    }
    return new Response(JSON.stringify({ ok: true }), { status: 200 });
  });
}

describe("RecentDealsTray 컴포넌트 및 최근 본 핫딜 E2E 상태 동기화", () => {
  it("11건 이상(30건 포함)의 딜을 slice(0, 10) 자르기 없이 1줄 가로 스크롤 트랙에 모두 렌더링한다", () => {
    const deals = Array.from({ length: 30 }, (_, idx) =>
      makeRecentDeal(idx + 1)
    );

    const { container } = render(
      <RecentDealsTray
        deals={deals}
        onSelectDeal={vi.fn()}
        onRemoveDeal={vi.fn()}
        onClearAll={vi.fn()}
      />
    );

    expect(screen.getByText("최근 본 핫딜 (30건)")).toBeInTheDocument();
    const track = container.querySelector("ul.recent-deals-track");
    expect(track).not.toBeNull();

    const cards = container.querySelectorAll("li.recent-deals-card");
    expect(cards).toHaveLength(30);
    expect(screen.getByText("테스트 최근 핫딜 1")).toBeInTheDocument();
    expect(screen.getByText("테스트 최근 핫딜 15")).toBeInTheDocument();
    expect(screen.getByText("테스트 최근 핫딜 30")).toBeInTheDocument();
  });

  it("썸네일 2단 폴백(wsrv.nl -> /api/proxy-image -> BrandImagePlaceholder) 및 품절/쇼핑몰/무료 배지를 렌더링한다", () => {
    const deals: Deal[] = [
      makeRecentDeal(101, {
        title: "[쿠팡] 폴백 테스트 상품",
        price: "29900",
        image_url: "https://cdn.example.com/item-101.jpg",
      }),
      makeRecentDeal(102, {
        shop_name: "서린컴퓨터",
        title: "서린컴퓨터 품절 특가",
        price: "0",
        isClosed: true,
        isFree: true,
        image_url: "https://seorincomputer.co.kr/data/pimg/163/163082_600.jpg",
        ecommerce_url: "https://seorincomputer.co.kr/shop/item.php?it_id=163",
      }),
      makeRecentDeal(103, {
        title: "이미지 없는 상품",
        image_url: null,
      }),
    ];

    render(
      <RecentDealsTray
        deals={deals}
        onSelectDeal={vi.fn()}
        onRemoveDeal={vi.fn()}
        onClearAll={vi.fn()}
      />
    );

    // 101: wsrv.nl -> /api/proxy-image -> BrandImagePlaceholder
    const img101 = screen.getByAltText("폴백 테스트 상품") as HTMLImageElement;
    expect(img101.getAttribute("src")).toBe(
      `https://wsrv.nl/?url=${encodeURIComponent("https://cdn.example.com/item-101.jpg")}&output=webp&w=120&we=1`
    );
    fireEvent.error(img101);
    expect(img101.dataset.fallbackTried).toBe("1");
    expect(img101.getAttribute("src")).toBe(
      `/api/proxy-image?url=${encodeURIComponent("https://cdn.example.com/item-101.jpg")}`
    );
    fireEvent.error(img101);
    expect(screen.queryByAltText("폴백 테스트 상품")).toBeNull();

    // 102: seorincomputer.co.kr은 wsrv.nl을 우회해 /api/proxy-image 직행
    const img102 = screen.getByAltText("서린컴퓨터 품절 특가") as HTMLImageElement;
    expect(img102.getAttribute("src")).toBe(
      `/api/proxy-image?url=${encodeURIComponent("https://seorincomputer.co.kr/data/pimg/163/163082_600.jpg")}`
    );
    expect(img102).toHaveClass("is-closed");

    // 쇼핑몰 배지, 품절 배지, 무료 표기 확인
    expect(screen.getAllByText("쿠팡").length).toBeGreaterThanOrEqual(1);
    expect(screen.getByText("서린컴퓨터")).toBeInTheDocument();
    expect(screen.getByText("품절")).toBeInTheDocument();
    expect(screen.getByText("무료")).toBeInTheDocument();

    // 103(초기 null) + 101(2차 실패) = BrandImagePlaceholder("이미지 없음") 2개
    expect(screen.getAllByText("이미지 없음")).toHaveLength(2);
  });

  it("카드 본문 클릭과 개별 삭제(×) 버튼은 중첩 없이 분리되어 버블링되지 않으며 전체 비우기/빈 상태를 지원한다", () => {
    const onSelectDeal = vi.fn();
    const onRemoveDeal = vi.fn();
    const onClearAll = vi.fn();
    const deals = [makeRecentDeal(11), makeRecentDeal(12)];

    const { container, rerender } = render(
      <RecentDealsTray
        deals={deals}
        onSelectDeal={onSelectDeal}
        onRemoveDeal={onRemoveDeal}
        onClearAll={onClearAll}
      />
    );

    // 중첩 버튼 금지 확인
    expect(container.querySelectorAll("button button")).toHaveLength(0);

    // 개별 삭제(×) 클릭 시 onRemoveDeal만 호출, onSelectDeal 미호출
    const removeBtn = screen.getByRole("button", {
      name: "테스트 최근 핫딜 11 최근 본 목록에서 삭제",
    });
    fireEvent.click(removeBtn);
    expect(onRemoveDeal).toHaveBeenCalledTimes(1);
    expect(onRemoveDeal).toHaveBeenCalledWith(11);
    expect(onSelectDeal).not.toHaveBeenCalled();

    // 카드 본문 클릭 시 onSelectDeal 호출
    const cardBtn = screen.getByRole("button", {
      name: /테스트 최근 핫딜 12.*상세 보기/,
    });
    fireEvent.click(cardBtn);
    expect(onSelectDeal).toHaveBeenCalledTimes(1);
    expect(onSelectDeal).toHaveBeenCalledWith(deals[1]);

    // 전체 비우기 클릭
    fireEvent.click(screen.getByRole("button", { name: "전체 비우기" }));
    expect(onClearAll).toHaveBeenCalledTimes(1);

    // 0건 빈 상태
    rerender(
      <RecentDealsTray
        deals={[]}
        onSelectDeal={onSelectDeal}
        onRemoveDeal={onRemoveDeal}
        onClearAll={onClearAll}
      />
    );
    expect(screen.getByText("최근 본 핫딜 (0건)")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "전체 비우기" })).toBeNull();
    expect(
      screen.getByText(
        "아직 열어본 핫딜이 없어요. 카드를 누르면 최근 기록이 여기에 쌓여요."
      )
    ).toBeInTheDocument();
  });

  it("Home에서 localStorage 12건 복원(손상 항목 필터링) -> 개별 × 삭제 시 localStorage 즉시 동기화 -> 전체 비우기 동작을 검증한다", async () => {
    mockHomeApis();
    const storedDeals = Array.from({ length: 12 }, (_, idx) =>
      makeRecentDeal(idx + 1)
    );
    localStorage.setItem(
      "insightdeal_recent_deals",
      JSON.stringify([
        ...storedDeals,
        { id: "broken", title: 123 },
        null,
      ])
    );

    render(<Home />);

    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: "최근 본 핫딜 (12)" })
      ).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: "최근 본 핫딜 (12)" }));

    expect(screen.getByText("최근 본 핫딜 (12건)")).toBeInTheDocument();
    expect(screen.getByText("테스트 최근 핫딜 12")).toBeInTheDocument();

    // 12번 딜 개별 삭제 -> 11건으로 감소 및 localStorage 즉시 동기화
    fireEvent.click(
      screen.getByRole("button", {
        name: "테스트 최근 핫딜 12 최근 본 목록에서 삭제",
      })
    );

    expect(screen.getByText("최근 본 핫딜 (11건)")).toBeInTheDocument();
    expect(screen.queryByText("테스트 최근 핫딜 12")).toBeNull();
    const afterRemove = JSON.parse(
      localStorage.getItem("insightdeal_recent_deals") || "[]"
    ) as Deal[];
    expect(afterRemove).toHaveLength(11);
    expect(afterRemove.some((item) => item.id === 12)).toBe(false);

    // 전체 비우기 -> 0건 및 localStorage 제거
    fireEvent.click(screen.getByRole("button", { name: "전체 비우기" }));
    expect(screen.getByText("최근 본 핫딜 (0건)")).toBeInTheDocument();
    expect(localStorage.getItem("insightdeal_recent_deals")).toBeNull();
  });

  it("Home에서 마지막 1건 개별 삭제 시 localStorage 키를 완전히 제거한다", async () => {
    mockHomeApis();
    localStorage.setItem(
      "insightdeal_recent_deals",
      JSON.stringify([makeRecentDeal(99)])
    );

    render(<Home />);

    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: "최근 본 핫딜 (1)" })
      ).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: "최근 본 핫딜 (1)" }));
    fireEvent.click(
      screen.getByRole("button", {
        name: "테스트 최근 핫딜 99 최근 본 목록에서 삭제",
      })
    );

    expect(screen.getByText("최근 본 핫딜 (0건)")).toBeInTheDocument();
    expect(localStorage.getItem("insightdeal_recent_deals")).toBeNull();
  });

  it("seorincomputer.co.kr 도메인은 차단 도메인으로 인식되어 wsrv.nl을 우회하고 /api/proxy-image로 직행한다", () => {
    const url = "https://seorincomputer.co.kr/data/pimg/163/163082_600.jpg";
    expect(isBlockedCommunityUrl(url)).toBe(true);
    expect(getProxyImageUrl(url, { width: 400 })).toBe(
      `/api/proxy-image?url=${encodeURIComponent(url)}`
    );
  });
});
