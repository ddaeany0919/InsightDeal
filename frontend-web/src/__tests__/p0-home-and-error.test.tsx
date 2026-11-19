import React from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import Home from "@/app/page";

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

function buildMockDealPayload(id: number, overrides: Record<string, unknown> = {}) {
  return {
    id,
    title: `[쿠팡] 테스트 핫딜 상품 ${id}`,
    price: 19900,
    currency: "KRW",
    site_name: "뽐뿌",
    shop_name: "쿠팡",
    post_url: `https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=${id}`,
    ecommerce_url: `https://www.coupang.com/vp/products/${id}`,
    image_url: `https://cdn.example.com/item-${id}.jpg`,
    category: "생활용품",
    honey_score: 120,
    indexed_at: "2026-11-19T10:00:00Z",
    is_closed: false,
    buy_count: 3,
    pass_count: 1,
    comment_count: 2,
    history_count: 2,
    is_search_fallback: false,
    is_free: false,
    ...overrides,
  };
}

function createHomeFetchMock(hotDealsHandler: () => Response) {
  return vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
    const url = typeof input === "string" ? input : input instanceof URL ? input.toString() : input.url;

    if (url.includes("/api/meta/categories")) {
      return new Response(JSON.stringify({ categories: [], sources: [] }), { status: 200 });
    }
    if (url.includes("/api/community/popular-keywords")) {
      return new Response(JSON.stringify({ keywords: [], rankings: [] }), { status: 200 });
    }
    if (url.includes("/api/push/device")) {
      return new Response(
        JSON.stringify({ status: "ok", device_uuid: "web_test_uuid", device_token: "web_test_token" }),
        { status: 200 }
      );
    }
    if (url.includes("/api/users/notifications")) {
      return new Response(JSON.stringify({ notifications: [] }), { status: 200 });
    }
    if (url.includes("/api/wishlist")) {
      return new Response(JSON.stringify([]), { status: 200 });
    }
    if (url.includes("/api/community/deals/")) {
      return new Response(
        JSON.stringify({
          ...buildMockDealPayload(101),
          content_html: "<p>모달 상세 본문입니다.</p>",
        }),
        { status: 200 }
      );
    }
    if (url.includes("/api/community/hot-deals")) {
      return hotDealsHandler();
    }
    return new Response(JSON.stringify({ ok: true }), { status: 200 });
  });
}

describe("Home 페이지 P0 카드 구조, 503 장애 UI 및 이미지 전 구간 검증", () => {
  it("카드를 시맨틱 <article>로 분리하고 중첩 <a> 없이 본문 클릭 시 상세 모달을 연다 + 이미지 onError 폴백", async () => {
    createHomeFetchMock(
      () =>
        new Response(
          JSON.stringify({
            deals: [
              buildMockDealPayload(101),
              buildMockDealPayload(102, { image_url: null, title: "[네이버] 이미지 없는 딜 102" }),
            ],
            has_more: false,
          }),
          { status: 200 }
        )
    );

    const { container } = render(<Home />);

    await waitFor(() => {
      expect(screen.getByText("테스트 핫딜 상품 101")).toBeInTheDocument();
    });

    const articles = container.querySelectorAll("article.deal-card");
    expect(articles).toHaveLength(2);
    expect(screen.getByText("이미지 없음")).toBeInTheDocument();

    // 중첩 <a> 금지 및 본문 트리거 내부 인터랙티브 자식 부재 확인
    articles.forEach((article) => {
      expect(article.querySelectorAll("a a")).toHaveLength(0);
      const bodyTrigger = article.querySelector(".deal-card-body-trigger");
      expect(bodyTrigger).not.toBeNull();
      expect(bodyTrigger?.querySelectorAll("a, button")).toHaveLength(0);
      expect(article.querySelector("a.deal-source-link")).not.toBeNull();
      expect(article.querySelector("a.deal-card-buy-link")).not.toBeNull();
    });

    // 이미지 URL이 wsrv.nl 프록시로 변환되고 <a> 안에 중첩되지 않으며 onError 시 /api/proxy-image로 폴백
    const cardImg = screen.getByAltText("테스트 핫딜 상품 101") as HTMLImageElement;
    expect(cardImg.getAttribute("src")).toContain("https://wsrv.nl/?url=");
    expect(cardImg.closest("a")).toBeNull();

    fireEvent.error(cardImg);
    expect(cardImg.getAttribute("src")).toBe(
      `/api/proxy-image?url=${encodeURIComponent("https://cdn.example.com/item-101.jpg")}`
    );

    // 본문 클릭 시 DealDetailModal 오픈 확인
    const trigger = articles[0].querySelector(".deal-card-body-trigger");
    expect(trigger).not.toBeNull();
    if (trigger) {
      fireEvent.click(trigger);
    }
    await waitFor(() => {
      expect(screen.getByRole("dialog")).toBeInTheDocument();
    });
  });

  it(
    "503 연속 장애 시 표준 에러 문구와 다시 시도 버튼을 표시하고, 재시도 성공 시 복구한다",
    async () => {
      let shouldFail = true;
      let hotDealsCalls = 0;
      createHomeFetchMock(() => {
        hotDealsCalls += 1;
        if (shouldFail) {
          return new Response(JSON.stringify({ detail: "Service Unavailable" }), {
            status: 503,
          });
        }
        return new Response(
          JSON.stringify({
            deals: [buildMockDealPayload(201, { title: "[지마켓] 복구된 핫딜 201" })],
            has_more: false,
          }),
          { status: 200 }
        );
      });

      render(<Home />);

      await waitFor(
        () => {
          const alertEl = screen.getByRole("alert");
          expect(alertEl).toHaveTextContent(
            "핫딜 목록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요."
          );
        },
        { timeout: 6000 }
      );

      const callsBeforeRetry = hotDealsCalls;
      const retryBtn = screen.getByRole("button", { name: /다시 시도/ });
      shouldFail = false;
      fireEvent.click(retryBtn);

      await waitFor(
        () => {
          expect(hotDealsCalls).toBeGreaterThan(callsBeforeRetry);
          expect(screen.getByText(/복구된 핫딜 201/)).toBeInTheDocument();
        },
        { timeout: 5000 }
      );
    },
    15000
  );
});
