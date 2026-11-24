import React from "react";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import Home from "@/app/page";
import DealCard from "@/app/components/DealCard";
import DealDetailModal from "@/app/components/DealDetailModal";
import DeviceSyncModal from "@/app/components/DeviceSyncModal";
import ImageLightboxModal from "@/app/components/ImageLightboxModal";
import NotificationDrawer from "@/app/components/NotificationDrawer";
import NotificationSettingsPage from "@/app/settings/notifications/page";
import {
  extractBodyImagesFromHtml,
  getStoredAccountToken,
  getStoredAccountUser,
  isSafeHttpUrl,
} from "@/app/lib/utils";
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

describe("M2 Iteration 2 회귀 테스트 (Major 3 + Medium 2 + Minor)", () => {
  it("Home + DealDetailModal 통합 시 품절 제보 후에도 선택 옵션·열린 아코디언이 유지되고 상세 1-RTT 조회가 1회만 호출된다", async () => {
    localStorage.setItem("insightdeal_web_device_uuid", "web_iter2_1");
    localStorage.setItem("insightdeal_web_device_token", "tok_iter2_1");

    let detailFetchCount = 0;

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
            device_uuid: "web_iter2_1",
            device_token: "tok_iter2_1",
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
      if (url.includes("/api/community/deals/801/report-closed")) {
        return new Response(
          JSON.stringify({
            status: "ok",
            deal_id: 801,
            report_count: 3,
            is_closed: true,
            threshold: 3,
          }),
          { status: 200 }
        );
      }
      if (url.includes("/api/community/deals/801/comments")) {
        return new Response(
          JSON.stringify([
            {
              id: 1,
              user_id: "other_user",
              nickname: "댓글테스터",
              content: "옵션 2가 좋아요",
              created_at: "2026-11-19T12:00:00Z",
            },
          ]),
          { status: 200 }
        );
      }
      if (url.includes("/api/community/deals/801")) {
        detailFetchCount += 1;
        return new Response(
          JSON.stringify({
            id: 801,
            title: "[쿠팡] 옵션 아코디언 유지 테스트 딜",
            price: 19900,
            currency: "KRW",
            site_name: "뽐뿌",
            content_html: "<p>상세 본문</p>",
            is_closed: false,
            report_count: 2,
            comment_count: 1,
            options_data: [
              { name: "옵션 1번", price: 19900, currency: "KRW", url: "https://shop.example.com/1" },
              { name: "옵션 2번", price: 24900, currency: "KRW", url: "https://shop.example.com/2" },
            ],
          }),
          { status: 200 }
        );
      }
      if (url.includes("/api/community/hot-deals")) {
        return new Response(
          JSON.stringify({
            deals: [
              {
                id: 801,
                title: "[쿠팡] 옵션 아코디언 유지 테스트 딜",
                price: 19900,
                currency: "KRW",
                site_name: "뽐뿌",
                is_closed: false,
                report_count: 2,
                has_options: true,
                options_count: 2,
                options_data: [
                  { name: "옵션 1번", price: 19900, currency: "KRW", url: "https://shop.example.com/1" },
                  { name: "옵션 2번", price: 24900, currency: "KRW", url: "https://shop.example.com/2" },
                ],
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
    await waitFor(() => {
      expect(screen.getByText("옵션 아코디언 유지 테스트 딜")).toBeInTheDocument();
    });

    const bodyTrigger = container.querySelector(".deal-card-body-trigger");
    expect(bodyTrigger).not.toBeNull();
    fireEvent.click(bodyTrigger!);

    const reportBtn = await screen.findByRole("button", { name: /품절 제보 \(2\)/ });
    expect(detailFetchCount).toBe(1);

    // 옵션 2번 선택 및 aria-pressed 확인
    const optionButtons = screen
      .getAllByRole("button")
      .filter((el) => el.textContent?.includes("옵션 2번") && el.hasAttribute("aria-pressed"));
    expect(optionButtons.length).toBeGreaterThanOrEqual(1);
    const option2Btn = optionButtons[0];
    fireEvent.click(option2Btn);
    expect(option2Btn).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByText("옵션 #2 적용")).toBeInTheDocument();

    // 한줄 의견 아코디언 펼침
    const commentAccordion = screen.getByRole("button", { name: /한줄 의견/ });
    fireEvent.click(commentAccordion);
    expect(await screen.findByText("옵션 2가 좋아요")).toBeInTheDocument();
    expect(commentAccordion).toHaveAttribute("aria-expanded", "true");

    // 품절 제보 클릭 -> 부모 onDealClosed로 selectedDeal 갱신
    fireEvent.click(reportBtn);
    await waitFor(() => {
      expect(screen.getByText("품절/종료")).toBeInTheDocument();
      expect(screen.getByText("품절·종료")).toBeInTheDocument();
    });

    // 상태 유지 & 상세 1-RTT 재호출 없음 검증
    expect(option2Btn).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByText("옵션 #2 적용")).toBeInTheDocument();
    expect(commentAccordion).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByText("옵션 2가 좋아요")).toBeInTheDocument();
    expect(detailFetchCount).toBe(1);
  });

  it("Home 마운트 시 GET /api/wishlist/가 hot-deals보다 먼저 응답해도 카드의 찜/알림 aria-pressed='true'가 복원된다", async () => {
    localStorage.setItem("insightdeal_web_device_uuid", "web_race_1");
    localStorage.setItem("insightdeal_web_device_token", "tok_race_1");

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
            device_uuid: "web_race_1",
            device_token: "tok_race_1",
          }),
          { status: 200 }
        );
      }
      if (url.includes("/api/users/notifications")) {
        return new Response(JSON.stringify([]), { status: 200 });
      }
      if (url.includes("/api/wishlist/")) {
        // wishlist가 즉시 먼저 응답
        return new Response(
          JSON.stringify([
            {
              id: 303,
              keyword: "레이스 컨디션 테스트 상품",
              target_price: 15000,
              user_id: "web_race_1",
              alert_enabled: true,
            },
          ]),
          { status: 200 }
        );
      }
      if (url.includes("/api/community/hot-deals")) {
        // hot-deals가 나중에(50ms 지연) 응답
        await new Promise((r) => setTimeout(r, 50));
        return new Response(
          JSON.stringify({
            deals: [
              {
                id: 901,
                title: "[쿠팡] 레이스 컨디션 테스트 상품",
                price: 19900,
                currency: "KRW",
                site_name: "뽐뿌",
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
      expect(screen.getByText("레이스 컨디션 테스트 상품")).toBeInTheDocument();
    });

    await waitFor(() => {
      expect(screen.getByRole("button", { name: "찜하기" })).toHaveAttribute(
        "aria-pressed",
        "true"
      );
      expect(
        screen.getByRole("button", { name: "가격 알림 설정" })
      ).toHaveAttribute("aria-pressed", "true");
    });
  });

  it("DealCard는 isClosed=true일 때 article에 opacity/filter를 적용하지 않고 썸네일 img에만 적용한다", () => {
    const closedDeal: Deal = {
      id: 950,
      community_name: "뽐뿌",
      shop_name: "쿠팡",
      title: "[쿠팡] 종료된 상품 명암비 테스트",
      price: "12900",
      image_url: "https://cdn.example.com/closed-950.jpg",
      scraped_at: "1시간 전",
      post_link: "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=950",
      ecommerce_url: "https://www.coupang.com/vp/products/950",
      currency: "KRW",
      category: "생활용품",
      isClosed: true,
    };

    const { container } = render(
      <DealCard
        deal={closedDeal}
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

    const article = container.querySelector("article.deal-card") as HTMLElement;
    expect(article).not.toBeNull();
    expect(article.style.opacity).toBe("");
    expect(article.style.filter).toBe("");

    const thumbImg = screen.getByAltText(
      "종료된 상품 명암비 테스트"
    ) as HTMLImageElement;
    expect(thumbImg.style.filter).toBe("grayscale(0.85)");
    expect(thumbImg.style.opacity).toBe("0.72");
    expect(screen.getByText("품절·종료")).toBeInTheDocument();
  });

  it("isSafeHttpUrl 및 extractBodyImagesFromHtml이 SSRF/트래킹픽셀/제어문자/userinfo를 차단하고 대소문자 중복 제거 및 /api/proxy-image를 허용한다", () => {
    expect(isSafeHttpUrl("https://cdn.example.com/pixel.gif?uid=99")).toBe(false);
    expect(isSafeHttpUrl("https://cdn.example.com/1x1.png?foo=bar")).toBe(false);
    expect(isSafeHttpUrl("http://127.0.0.1:8080/admin.jpg")).toBe(false);
    expect(isSafeHttpUrl("http://169.254.169.254/latest/meta-data/")).toBe(false);
    expect(isSafeHttpUrl("http://localhost/test.jpg")).toBe(false);
    expect(isSafeHttpUrl("http://10.0.0.5/a.jpg")).toBe(false);
    expect(isSafeHttpUrl("http://172.16.1.2/a.jpg")).toBe(false);
    expect(isSafeHttpUrl("http://192.168.0.1/a.jpg")).toBe(false);
    expect(isSafeHttpUrl("http://[::1]/a.jpg")).toBe(false);
    expect(isSafeHttpUrl("https://user:pass@evil.com/a.jpg")).toBe(false);
    expect(isSafeHttpUrl("https://cdn.example.com/a.jpg\0")).toBe(false);

    expect(
      isSafeHttpUrl("/api/proxy-image?url=https%3A%2F%2Fcdn.example.com%2Fa.jpg")
    ).toBe(true);
    expect(isSafeHttpUrl("/api/proxy-image?url=//evil.com/a.jpg")).toBe(false);
    expect(
      isSafeHttpUrl("/api/proxy-image?url=http%3A%2F%2F127.0.0.1%2Fa.jpg")
    ).toBe(false);

    const extracted = extractBodyImagesFromHtml(`
      <img src="https://cdn.example.com/pixel.gif?uid=99" />
      <img src="https://cdn.example.com/real.jpg?a=1&b=2" />
      <img src="HTTPS://cdn.example.com/real.jpg?a=1&b=2" />
      <img src="http://127.0.0.1/secret.jpg" />
      <img src="/api/proxy-image?url=https%3A%2F%2Fcdn.example.com%2Fproxied.jpg" />
    `);
    expect(extracted).toEqual([
      "https://cdn.example.com/real.jpg?a=1&b=2",
      "/api/proxy-image?url=https%3A%2F%2Fcdn.example.com%2Fproxied.jpg",
    ]);
  });

  it("DeviceSyncModal은 7자리 코드를 절삭 전송하지 않고 차단하며 link-device 실패 시 토큰을 저장하지 않는다", async () => {
    localStorage.setItem("insightdeal_web_device_uuid", "web_dev_sync_err");
    localStorage.setItem("insightdeal_web_device_token", "tok_dev_sync_err");

    let claimCalls = 0;
    vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
      const url =
        typeof input === "string"
          ? input
          : input instanceof URL
            ? input.toString()
            : input.url;

      if (url.includes("/api/auth/claim-pairing-code")) {
        claimCalls += 1;
        return new Response(JSON.stringify({ status: "ok" }), { status: 200 });
      }
      if (url.includes("/api/auth/login")) {
        return new Response(
          JSON.stringify({
            username: "tester",
            nickname: "테스터",
            access_token: "acc_tok_should_not_save",
          }),
          { status: 200 }
        );
      }
      if (url.includes("/api/auth/link-device")) {
        return new Response(JSON.stringify({ detail: "Server Error" }), {
          status: 500,
        });
      }
      return new Response(JSON.stringify({ ok: true }), { status: 200 });
    });

    render(<DeviceSyncModal isOpen={true} onClose={vi.fn()} />);

    const codeInput = screen.getByLabelText("6자리 동기화 코드 입력");
    fireEvent.change(codeInput, { target: { value: "1234567" } });
    fireEvent.click(screen.getByRole("button", { name: "내 기기와 합치기" }));

    expect(
      await screen.findByText("6자리 숫자 코드를 정확히 입력해 주세요.")
    ).toBeInTheDocument();
    expect(claimCalls).toBe(0);

    // 선택형 계정 연결 탭 -> login 200, link-device 500
    fireEvent.click(screen.getByRole("button", { name: /선택형 계정 연결/ }));
    fireEvent.change(screen.getByLabelText("계정 아이디"), {
      target: { value: "tester" },
    });
    fireEvent.change(screen.getByLabelText("계정 비밀번호"), {
      target: { value: "pw1234" },
    });
    fireEvent.click(
      screen.getByRole("button", { name: "로그인 후 이 기기와 연결하기" })
    );

    expect(
      await screen.findByText("계정은 인증됐지만 현재 기기 연결에 실패했어요.")
    ).toBeInTheDocument();
    expect(getStoredAccountToken()).toBe("");
    expect(getStoredAccountUser()).toBeNull();
  });

  it("ImageLightboxModal 하단 썸네일 스트립 2차 실패 시 BrandImagePlaceholder를 렌더링하고 NotificationDrawer는 안전하지 않은 링크를 차단하고 전체 목록을 표시한다", async () => {
    const { unmount } = render(
      <ImageLightboxModal
        isOpen={true}
        images={[
          "https://cdn.example.com/a.jpg",
          "https://cdn.example.com/b.jpg",
        ]}
        initialIndex={0}
        title="테스트"
        onClose={vi.fn()}
      />
    );

    const thumbBtn = screen.getByRole("button", { name: "2번 이미지 보기" });
    const thumbImg = thumbBtn.querySelector("img") as HTMLImageElement;
    expect(thumbImg).not.toBeNull();
    fireEvent.error(thumbImg);
    expect(thumbImg.dataset.fallbackTried).toBe("1");
    fireEvent.error(thumbImg);
    expect(thumbBtn.querySelector("img")).toBeNull();
    expect(thumbBtn.textContent).toContain("이미지 없음");
    unmount();

    // NotificationDrawer 13건 전체 렌더링 및 javascript: 링크 차단 검증
    localStorage.setItem("insightdeal_web_device_uuid", "web_notif_13");
    localStorage.setItem("insightdeal_web_device_token", "tok_notif_13");

    const alerts13 = Array.from({ length: 13 }, (_, idx) => ({
      id: idx + 1,
      keyword: `키워드${idx + 1}`,
      title: `알림 제목 ${idx + 1}`,
      deal_url:
        idx === 0 ? "javascript:alert(1)" : `https://shop.example.com/d/${idx + 1}`,
      received_at: "2026-11-19T12:00:00Z",
    }));

    vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
      const url =
        typeof input === "string"
          ? input
          : input instanceof URL
            ? input.toString()
            : input.url;
      if (url.includes("/api/users/notifications")) {
        return new Response(JSON.stringify(alerts13), { status: 200 });
      }
      return new Response(JSON.stringify([]), { status: 200 });
    });

    render(
      <NotificationDrawer
        isOpen={true}
        onClose={vi.fn()}
        deals={[]}
        onSelectDeal={vi.fn()}
      />
    );

    expect(await screen.findByText("알림 제목 13")).toBeInTheDocument();
    const firstTitle = screen.getByText("알림 제목 1");
    expect(firstTitle.tagName).toBe("SPAN");
    const safeTitle = screen.getByText("알림 제목 2");
    expect(safeTitle.tagName).toBe("A");
    expect(safeTitle).toHaveAttribute("href", "https://shop.example.com/d/2");
  });
});

describe("M2 Iteration 3 회귀 테스트 (Challenger 1·2 Iter2 지적 전량)", () => {
  it("알림 동반(alert_enabled: true, target_price: 12000) 찜 해제 후 무한스크롤 2페이지가 로드되어도 aria-pressed='false'를 유지한다", async () => {
    localStorage.setItem("insightdeal_web_device_uuid", "web_iter3_scroll");
    localStorage.setItem("insightdeal_web_device_token", "tok_iter3_scroll");

    const observerCallbacks: IntersectionObserverCallback[] = [];
    const OriginalIO = window.IntersectionObserver;
    class ControllableIO implements IntersectionObserver {
      readonly root: Element | Document | null = null;
      readonly rootMargin: string = "";
      readonly thresholds: ReadonlyArray<number> = [];
      private cb: IntersectionObserverCallback;
      constructor(cb: IntersectionObserverCallback) {
        this.cb = cb;
        observerCallbacks.push(cb);
      }
      disconnect(): void {}
      observe(): void {}
      takeRecords(): IntersectionObserverEntry[] {
        return [];
      }
      unobserve(): void {}
    }
    Object.defineProperty(window, "IntersectionObserver", {
      writable: true,
      configurable: true,
      value: ControllableIO,
    });

    try {
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
              device_uuid: "web_iter3_scroll",
              device_token: "tok_iter3_scroll",
            }),
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
                id: 401,
                keyword: "알림 동반 찜 해제 테스트",
                target_price: 12000,
                user_id: "web_iter3_scroll",
                alert_enabled: true,
              },
            ]),
            { status: 200 }
          );
        }
        if (url.includes("/api/community/hot-deals")) {
          if (url.includes("cursor_id=501") || url.includes("offset=1")) {
            return new Response(
              JSON.stringify({
                deals: [
                  {
                    id: 502,
                    title: "[옥션] 2페이지 신규 핫딜",
                    price: 9900,
                    currency: "KRW",
                    site_name: "뽐뿌",
                    is_closed: false,
                  },
                ],
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
                  id: 501,
                  title: "[쿠팡] 알림 동반 찜 해제 테스트",
                  price: 15000,
                  currency: "KRW",
                  site_name: "뽐뿌",
                  is_closed: false,
                },
              ],
              has_more: true,
              next_cursor: { indexed_at: "2026-11-19T10:00:00Z", id: 501 },
            }),
            { status: 200 }
          );
        }
        return new Response(JSON.stringify({ ok: true }), { status: 200 });
      });

      render(<Home />);

      await waitFor(() => {
        expect(screen.getByText("알림 동반 찜 해제 테스트")).toBeInTheDocument();
      });

      await waitFor(() => {
        expect(screen.getByRole("button", { name: "찜하기" })).toHaveAttribute(
          "aria-pressed",
          "true"
        );
        expect(
          screen.getByRole("button", { name: "가격 알림 설정" })
        ).toHaveAttribute("aria-pressed", "true");
      });

      fireEvent.click(screen.getByRole("button", { name: "찜하기" }));

      await waitFor(() => {
        expect(screen.getByRole("button", { name: "찜하기" })).toHaveAttribute(
          "aria-pressed",
          "false"
        );
      });
      expect(
        screen.getByRole("button", { name: "가격 알림 설정" })
      ).toHaveAttribute("aria-pressed", "true");

      // 무한스크롤 2페이지 로드 트리거
      const latestCb = observerCallbacks[observerCallbacks.length - 1];
      expect(latestCb).toBeDefined();
      latestCb(
        [{ isIntersecting: true } as IntersectionObserverEntry],
        {} as IntersectionObserver
      );

      await waitFor(() => {
        expect(screen.getByText("2페이지 신규 핫딜")).toBeInTheDocument();
      });

      // 1페이지 카드 찜 해제 상태 유지 검증
      const wishButtons = screen.getAllByRole("button", { name: "찜하기" });
      expect(wishButtons[0]).toHaveAttribute("aria-pressed", "false");
    } finally {
      Object.defineProperty(window, "IntersectionObserver", {
        writable: true,
        configurable: true,
        value: OriginalIO,
      });
    }
  });

  it("캐시된 피드에서 지연된 GET /api/wishlist/ 응답이 DELETE 이후 도착해도 찜 해제(aria-pressed='false')를 유지한다", async () => {
    localStorage.setItem("insightdeal_web_device_uuid", "web_iter3_delay");
    localStorage.setItem("insightdeal_web_device_token", "tok_iter3_delay");
    localStorage.setItem(
      "insightdeal_cached_deals",
      JSON.stringify([
        {
          id: 511,
          community_name: "뽐뿌",
          shop_name: "쿠팡",
          title: "[쿠팡] 지연 수화 찜 해제 테스트",
          price: "19900",
          image_url: "https://cdn.example.com/511.jpg",
          scraped_at: "방금 전",
          post_link: "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=511",
          ecommerce_url: "https://www.coupang.com/vp/products/511",
          currency: "KRW",
          category: "디지털",
        },
      ])
    );
    localStorage.setItem("insightdeal_wishlist", JSON.stringify([511]));
    localStorage.setItem(
      "insightdeal_wishlist_id_map",
      JSON.stringify({
        "511": { wishlistId: 777, keyword: "지연 수화 찜 해제 테스트" },
      })
    );

    let resolveDelayedWishlist!: (res: Response) => void;
    const delayedWishlistPromise = new Promise<Response>((resolve) => {
      resolveDelayedWishlist = resolve;
    });
    let deleteCount = 0;

    vi.spyOn(globalThis, "fetch").mockImplementation(async (input, init) => {
      const url =
        typeof input === "string"
          ? input
          : input instanceof URL
            ? input.toString()
            : input.url;
      const method = (init?.method || "GET").toUpperCase();

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
            device_uuid: "web_iter3_delay",
            device_token: "tok_iter3_delay",
          }),
          { status: 200 }
        );
      }
      if (url.includes("/api/users/notifications")) {
        return new Response(JSON.stringify([]), { status: 200 });
      }
      if (url.includes("/api/wishlist/777") && method === "DELETE") {
        deleteCount += 1;
        return new Response(JSON.stringify({ status: "ok" }), { status: 200 });
      }
      if (url.includes("/api/wishlist/")) {
        return delayedWishlistPromise;
      }
      if (url.includes("/api/community/hot-deals")) {
        return new Response(
          JSON.stringify({
            deals: [
              {
                id: 511,
                title: "[쿠팡] 지연 수화 찜 해제 테스트",
                price: 19900,
                currency: "KRW",
                site_name: "뽐뿌",
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
      expect(screen.getByText("지연 수화 찜 해제 테스트")).toBeInTheDocument();
    });

    const wishBtn = screen.getByRole("button", { name: "찜하기" });
    expect(wishBtn).toHaveAttribute("aria-pressed", "true");

    // 지연 수화 응답 도착 전에 사용자가 찜 해제 클릭
    fireEvent.click(wishBtn);

    await waitFor(() => {
      expect(deleteCount).toBe(1);
      expect(screen.getByRole("button", { name: "찜하기" })).toHaveAttribute(
        "aria-pressed",
        "false"
      );
    });

    // 이제 지연된 초기 GET /api/wishlist/ 구 스냅샷 도착
    resolveDelayedWishlist(
      new Response(
        JSON.stringify([
          {
            id: 777,
            keyword: "지연 수화 찜 해제 테스트",
            target_price: 0,
            user_id: "web_iter3_delay",
            alert_enabled: false,
          },
        ]),
        { status: 200 }
      )
    );

    await new Promise((r) => setTimeout(r, 30));

    expect(screen.getByRole("button", { name: "찜하기" })).toHaveAttribute(
      "aria-pressed",
      "false"
    );
  });

  it("isSafeHttpUrl이 /api/proxy-image? 대소문자 우회·파라미터 누락·HPP 중복을 차단하고 미인코딩 https:// 프록시는 허용한다", () => {
    expect(isSafeHttpUrl("/api/proxy-image?URL=javascript:alert(1)")).toBe(false);
    expect(isSafeHttpUrl("/api/proxy-image?foo=bar")).toBe(false);
    expect(
      isSafeHttpUrl(
        "/api/proxy-image?url=https%3A%2F%2Fcdn.example.com%2Fa.jpg&url=http%3A%2F%2F127.0.0.1%2Fb.jpg"
      )
    ).toBe(false);
    expect(isSafeHttpUrl("/api/proxy-image?url=https://cdn.example.com/a.jpg")).toBe(
      true
    );
  });

  it("NotificationDrawer는 /api/proxy-image? 상대경로를 외부 <a href> 링크로 렌더링하지 않는다", async () => {
    localStorage.setItem("insightdeal_web_device_uuid", "web_notif_proxy");
    localStorage.setItem("insightdeal_web_device_token", "tok_notif_proxy");

    vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
      const url =
        typeof input === "string"
          ? input
          : input instanceof URL
            ? input.toString()
            : input.url;
      if (url.includes("/api/users/notifications")) {
        return new Response(
          JSON.stringify([
            {
              id: 99,
              keyword: "프록시테스트",
              title: "프록시 상대경로 차단 알림",
              deal_url: "/api/proxy-image?url=https://cdn.example.com/a.jpg",
              received_at: "2026-11-19T12:00:00Z",
            },
          ]),
          { status: 200 }
        );
      }
      return new Response(JSON.stringify([]), { status: 200 });
    });

    render(
      <NotificationDrawer
        isOpen={true}
        onClose={vi.fn()}
        deals={[]}
        onSelectDeal={vi.fn()}
      />
    );

    const titleEl = await screen.findByText("프록시 상대경로 차단 알림");
    expect(titleEl.tagName).toBe("SPAN");
    expect(titleEl.closest("a")).toBeNull();
  });
});

describe("M2 Iteration 4 회귀 테스트 (Challenger 1·2 Iter3 지적 전량)", () => {
  it("toggleWishlist 찜 해제 중 DELETE 네트워크 예외 발생 시 aria-pressed='true'로 롤백되고 localStorage가 복원된다", async () => {
    localStorage.setItem("insightdeal_web_device_uuid", "web_iter4_neterr_wish");
    localStorage.setItem("insightdeal_web_device_token", "tok_iter4_neterr_wish");
    localStorage.setItem(
      "insightdeal_cached_deals",
      JSON.stringify([
        {
          id: 521,
          community_name: "뽐뿌",
          shop_name: "쿠팡",
          title: "[쿠팡] 네트워크 예외 찜 롤백 테스트",
          price: "21900",
          image_url: "https://cdn.example.com/521.jpg",
          scraped_at: "방금 전",
          post_link: "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=521",
          ecommerce_url: "https://www.coupang.com/vp/products/521",
          currency: "KRW",
          category: "디지털",
        },
      ])
    );
    localStorage.setItem("insightdeal_wishlist", JSON.stringify([521]));
    localStorage.setItem(
      "insightdeal_wishlist_items",
      JSON.stringify({
        "521": {
          id: 521,
          community_name: "뽐뿌",
          shop_name: "쿠팡",
          title: "[쿠팡] 네트워크 예외 찜 롤백 테스트",
          price: "21900",
          image_url: "https://cdn.example.com/521.jpg",
          scraped_at: "방금 전",
          post_link: "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=521",
          ecommerce_url: "https://www.coupang.com/vp/products/521",
          currency: "KRW",
          category: "디지털",
        },
      })
    );
    localStorage.setItem(
      "insightdeal_wishlist_id_map",
      JSON.stringify({
        "521": { wishlistId: 409, keyword: "네트워크 예외 찜 롤백 테스트" },
      })
    );

    vi.spyOn(globalThis, "fetch").mockImplementation(async (input, init) => {
      const url =
        typeof input === "string"
          ? input
          : input instanceof URL
            ? input.toString()
            : input.url;
      const method = (init?.method || "GET").toUpperCase();

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
            device_uuid: "web_iter4_neterr_wish",
            device_token: "tok_iter4_neterr_wish",
          }),
          { status: 200 }
        );
      }
      if (url.includes("/api/users/notifications")) {
        return new Response(JSON.stringify([]), { status: 200 });
      }
      if (url.includes("/api/wishlist/409") && method === "DELETE") {
        return Promise.reject(new TypeError("Failed to fetch"));
      }
      if (url.includes("/api/wishlist/")) {
        return new Response(
          JSON.stringify([
            {
              id: 409,
              keyword: "네트워크 예외 찜 롤백 테스트",
              target_price: 0,
              user_id: "web_iter4_neterr_wish",
              alert_enabled: false,
            },
          ]),
          { status: 200 }
        );
      }
      if (url.includes("/api/community/hot-deals")) {
        return new Response(
          JSON.stringify({
            deals: [
              {
                id: 521,
                title: "[쿠팡] 네트워크 예외 찜 롤백 테스트",
                price: 21900,
                currency: "KRW",
                site_name: "뽐뿌",
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
      expect(screen.getByText("네트워크 예외 찜 롤백 테스트")).toBeInTheDocument();
    });

    const wishBtn = screen.getByRole("button", { name: "찜하기" });
    expect(wishBtn).toHaveAttribute("aria-pressed", "true");

    fireEvent.click(wishBtn);

    await waitFor(() => {
      expect(
        screen.getByText("찜 상태를 변경하지 못했어요. 잠시 후 다시 시도해 주세요")
      ).toBeInTheDocument();
    });

    expect(screen.getByRole("button", { name: "찜하기" })).toHaveAttribute(
      "aria-pressed",
      "true"
    );
    expect(JSON.parse(localStorage.getItem("insightdeal_wishlist") || "[]")).toContain(
      521
    );
    const savedMap = JSON.parse(
      localStorage.getItem("insightdeal_wishlist_items") || "{}"
    ) as Record<string, { id?: number }>;
    expect(savedMap["521"]?.id).toBe(521);
  });

  it("toggleAlert 가격 알림 해제 중 네트워크 예외 발생 시 aria-pressed='true'로 롤백되고 localStorage가 복원된다", async () => {
    localStorage.setItem("insightdeal_web_device_uuid", "web_iter4_neterr_alert");
    localStorage.setItem("insightdeal_web_device_token", "tok_iter4_neterr_alert");
    localStorage.setItem("insightdeal_alerts", JSON.stringify([531]));
    localStorage.setItem("insightdeal_target_prices", JSON.stringify({ "531": 18000 }));
    localStorage.setItem(
      "insightdeal_wishlist_id_map",
      JSON.stringify({
        "531": { wishlistId: 419, keyword: "네트워크 예외 알림 롤백 테스트" },
      })
    );

    vi.spyOn(globalThis, "fetch").mockImplementation(async (input, init) => {
      const url =
        typeof input === "string"
          ? input
          : input instanceof URL
            ? input.toString()
            : input.url;
      const method = (init?.method || "GET").toUpperCase();

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
            device_uuid: "web_iter4_neterr_alert",
            device_token: "tok_iter4_neterr_alert",
          }),
          { status: 200 }
        );
      }
      if (url.includes("/api/users/notifications")) {
        return new Response(JSON.stringify([]), { status: 200 });
      }
      if (url.includes("/api/wishlist/419") && (method === "DELETE" || method === "PATCH")) {
        return Promise.reject(new TypeError("Failed to fetch"));
      }
      if (url.includes("/api/wishlist/")) {
        return new Response(JSON.stringify([]), { status: 200 });
      }
      if (url.includes("/api/community/hot-deals")) {
        return new Response(
          JSON.stringify({
            deals: [
              {
                id: 531,
                title: "[쿠팡] 네트워크 예외 알림 롤백 테스트",
                price: 20000,
                currency: "KRW",
                site_name: "뽐뿌",
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
      expect(screen.getByText("네트워크 예외 알림 롤백 테스트")).toBeInTheDocument();
    });

    const alertBtn = screen.getByRole("button", { name: "가격 알림 설정" });
    expect(alertBtn).toHaveAttribute("aria-pressed", "true");

    fireEvent.click(alertBtn);

    await waitFor(() => {
      expect(
        screen.getByText("가격 알림 해제에 실패했어요. 잠시 후 다시 시도해 주세요")
      ).toBeInTheDocument();
    });

    expect(screen.getByRole("button", { name: "가격 알림 설정" })).toHaveAttribute(
      "aria-pressed",
      "true"
    );
    expect(JSON.parse(localStorage.getItem("insightdeal_alerts") || "[]")).toContain(
      531
    );
    expect(
      JSON.parse(localStorage.getItem("insightdeal_target_prices") || "{}")["531"]
    ).toBe(18000);
  });

  it("찜하지 않은 딜에서 DealDetailModal 목표가 알림만 저장 후 DeviceSyncModal 동기화(hydrateFromServer)를 실행해도 카드 찜하기는 aria-pressed='false'를 유지한다", async () => {
    localStorage.setItem("insightdeal_web_device_uuid", "web_iter4_modal_sync");
    localStorage.setItem("insightdeal_web_device_token", "tok_iter4_modal_sync");

    let itemAlertSaved = false;
    let claimedPairing = false;
    let wishlistFetchesAfterClaim = 0;

    vi.spyOn(globalThis, "fetch").mockImplementation(async (input, init) => {
      const url =
        typeof input === "string"
          ? input
          : input instanceof URL
            ? input.toString()
            : input.url;
      const method = (init?.method || "GET").toUpperCase();

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
            device_uuid: "web_iter4_modal_sync",
            device_token: "tok_iter4_modal_sync",
          }),
          { status: 200 }
        );
      }
      if (url.includes("/api/users/notifications")) {
        return new Response(JSON.stringify([]), { status: 200 });
      }
      if (url.includes("/api/wishlist/item-alert") && method === "POST") {
        itemAlertSaved = true;
        return new Response(
          JSON.stringify({
            id: 901,
            keyword: "모달 알림 단독 저장 동기화 테스트",
            target_price: 25000,
            user_id: "web_iter4_modal_sync",
            alert_enabled: true,
          }),
          { status: 200 }
        );
      }
      if (url.includes("/api/wishlist/")) {
        if (claimedPairing) {
          wishlistFetchesAfterClaim += 1;
        }
        return new Response(
          JSON.stringify(
            itemAlertSaved
              ? [
                  {
                    id: 901,
                    keyword: "모달 알림 단독 저장 동기화 테스트",
                    target_price: 25000,
                    user_id: "web_iter4_modal_sync",
                    alert_enabled: true,
                  },
                ]
              : []
          ),
          { status: 200 }
        );
      }
      if (url.includes("/api/auth/claim-pairing-code") && method === "POST") {
        claimedPairing = true;
        return new Response(
          JSON.stringify({
            status: "ok",
            success: true,
            merged_keywords: 0,
            merged_wishlists: 1,
            merged_alerts: 0,
            dnd_synced: true,
          }),
          { status: 200 }
        );
      }
      if (url.includes("/api/community/deals/601/comments")) {
        return new Response(JSON.stringify([]), { status: 200 });
      }
      if (url.includes("/api/community/deals/601")) {
        return new Response(
          JSON.stringify({
            id: 601,
            title: "[쿠팡] 모달 알림 단독 저장 동기화 테스트",
            price: 29900,
            currency: "KRW",
            site_name: "뽐뿌",
            content_html: "<p>모달 테스트 본문</p>",
            is_closed: false,
            report_count: 0,
          }),
          { status: 200 }
        );
      }
      if (url.includes("/api/community/hot-deals")) {
        return new Response(
          JSON.stringify({
            deals: [
              {
                id: 601,
                title: "[쿠팡] 모달 알림 단독 저장 동기화 테스트",
                price: 29900,
                currency: "KRW",
                site_name: "뽐뿌",
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
      expect(screen.getByText("모달 알림 단독 저장 동기화 테스트")).toBeInTheDocument();
    });

    expect(screen.getByRole("button", { name: "찜하기" })).toHaveAttribute(
      "aria-pressed",
      "false"
    );

    // 상세 모달 열기 -> 목표가 알림 단독 저장 -> 닫기
    fireEvent.click(
      screen.getByRole("button", { name: /모달 알림 단독 저장 동기화 테스트 상세 정보 보기/ })
    );

    const targetAccordionBtn = await screen.findByRole("button", {
      name: /목표가 도달 시 알림/,
    });
    fireEvent.click(targetAccordionBtn);

    const targetInput = await screen.findByLabelText("희망 목표 금액 입력");
    fireEvent.change(targetInput, { target: { value: "25000" } });
    fireEvent.click(screen.getByRole("button", { name: "알림 받기" }));

    await waitFor(() => {
      expect(itemAlertSaved).toBe(true);
    });

    fireEvent.click(screen.getByRole("button", { name: "상세 모달 닫기" }));

    // HeaderMoreMenu -> DeviceSyncModal 열기 -> 6자리 동기화 실행(hydrateFromServer 트리거)
    fireEvent.click(screen.getByRole("button", { name: "더보기 메뉴" }));
    fireEvent.click(
      screen.getByRole("button", { name: "기기 동기화 및 계정 연결" })
    );
    const codeInput = await screen.findByLabelText("6자리 동기화 코드 입력");
    fireEvent.change(codeInput, { target: { value: "123456" } });
    fireEvent.click(screen.getByRole("button", { name: "내 기기와 합치기" }));

    await waitFor(() => {
      expect(screen.getAllByText(/동기화 완료!/).length).toBeGreaterThan(0);
      expect(wishlistFetchesAfterClaim).toBeGreaterThanOrEqual(1);
    });

    fireEvent.click(screen.getByRole("button", { name: "모달 닫기" }));

    expect(screen.getByRole("button", { name: "찜하기" })).toHaveAttribute(
      "aria-pressed",
      "false"
    );
    expect(screen.getByRole("button", { name: "가격 알림 설정" })).toHaveAttribute(
      "aria-pressed",
      "true"
    );
  });

  it("isSafeHttpUrl이 공백 인코딩(%20, +) 기반 중첩 프록시 우회 URL을 차단(false)한다", () => {
    expect(
      isSafeHttpUrl("/api/proxy-image?url=%20/api/proxy-image?url=https://a.com/a.jpg")
    ).toBe(false);
    expect(
      isSafeHttpUrl("/api/proxy-image?url=+/api/proxy-image?url=https://a.com/a.jpg")
    ).toBe(false);
    expect(
      isSafeHttpUrl("/api/proxy-image?url=%20https://cdn.example.com/a.jpg")
    ).toBe(false);
    expect(
      isSafeHttpUrl("/api/proxy-image?url=https://cdn.example.com/a.jpg")
    ).toBe(true);
  });

  it("M4 회귀: DealCard는 getProxyImageUrl과 getFallbackProxyImageUrl이 같으면 1회 404 후 동일 URL 재요청 없이 즉시 BrandImagePlaceholder로 전환한다", () => {
    const deal: Deal = {
      id: 9901,
      community_name: "뽐뿌",
      title: "[테스트] 동일 프록시 URL 404 단발 폴백 상품",
      price: "19900",
      image_url: "http://127.0.0.1/broken-404.png",
      scraped_at: "방금 전",
      post_link: "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=9901",
      ecommerce_url: "https://shop.example.com/9901",
      currency: "KRW",
      category: "생활용품",
      isClosed: false,
    };

    render(
      <DealCard
        deal={deal}
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

    const img = screen.getByAltText("동일 프록시 URL 404 단발 폴백 상품") as HTMLImageElement;
    expect(img.getAttribute("src")).toBe(
      `/api/proxy-image?url=${encodeURIComponent("http://127.0.0.1/broken-404.png")}`
    );

    // 1회 error 발생 시 동일 URL 재시도 없이 즉시 <img> 제거 및 플레이스홀더 렌더링
    fireEvent.error(img);
    expect(screen.queryByAltText("동일 프록시 URL 404 단발 폴백 상품")).toBeNull();
    expect(screen.getByText("이미지 없음")).toBeInTheDocument();
  });

  it("M4 회귀: DealDetailModal의 요약 필(.deal-summary-pill)은 aria-expanded 상태를 정확히 반영한다", async () => {
    localStorage.setItem("insightdeal_web_device_uuid", "web_m4_pill");
    localStorage.setItem("insightdeal_web_device_token", "tok_m4_pill");

    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify({
          id: 9902,
          title: "[쿠팡] 요약 필 접근성 테스트",
          price: 15900,
          currency: "KRW",
          site_name: "뽐뿌",
          content_html: "<p>본문</p>",
          history_count: 2,
          buy_count: 3,
          pass_count: 1,
          comment_count: 2,
        }),
        { status: 200 }
      )
    );

    const deal: Deal = {
      id: 9902,
      community_name: "뽐뿌",
      title: "[쿠팡] 요약 필 접근성 테스트",
      price: "15900",
      image_url: null,
      scraped_at: "방금 전",
      post_link: "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=9902",
      ecommerce_url: "https://www.coupang.com/vp/products/9902",
      currency: "KRW",
      category: "생활용품",
      isClosed: false,
    };

    render(<DealDetailModal deal={deal} onClose={vi.fn()} showToast={vi.fn()} />);

    const chartPill = await screen.findByRole("button", { name: /가격 추이 \(2건\)/ });
    expect(chartPill).toHaveAttribute("aria-expanded", "false");
    fireEvent.click(chartPill);
    expect(chartPill).toHaveAttribute("aria-expanded", "true");
  });

  it("M4 회귀: 알림 설정 페이지는 iOS 인앱 브라우저에서 Safari 복사 버튼 클릭 시 화면상 시각적 안내 배너를 표시한다", async () => {
    const originalUa = navigator.userAgent;
    const originalClipboard = navigator.clipboard;

    Object.defineProperty(navigator, "userAgent", {
      configurable: true,
      value: "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) KAKAOTALK 10.5.0",
    });
    Object.defineProperty(navigator, "clipboard", {
      configurable: true,
      value: { writeText: vi.fn().mockResolvedValue(undefined) },
    });

    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify({
          keywords: [],
          detailed_keywords: [],
          dnd_enabled: false,
          dnd_start_time: "21:00",
          dnd_end_time: "08:00",
        }),
        { status: 200 }
      )
    );

    try {
      render(<NotificationSettingsPage />);
      const copyBtn = await screen.findByRole("button", {
        name: "Safari로 열기용 주소 복사",
      });
      fireEvent.click(copyBtn);

      await waitFor(() => {
        const notices = screen.getAllByText(
          "현재 페이지 주소를 복사했어요. Safari 주소창에 붙여넣어 열어주세요."
        );
        // .sr-only 영역 외에 시각적 배너(status)가 함께 렌더링됨
        expect(notices.length).toBeGreaterThanOrEqual(2);
        expect(notices.some((el) => !el.classList.contains("sr-only"))).toBe(true);
      });
    } finally {
      Object.defineProperty(navigator, "userAgent", {
        configurable: true,
        value: originalUa,
      });
      Object.defineProperty(navigator, "clipboard", {
        configurable: true,
        value: originalClipboard,
      });
    }
  });

  it("다중 칩 연속 클릭 시 선행 요청 AbortSignal을 취소하고 에러 문구를 노출하지 않으며 8초 타임아웃 시 무한 로딩을 차단한다", async () => {
    const signals: Array<{ url: string; signal?: AbortSignal | null }> = [];
    let hangMode = false;

    vi.spyOn(globalThis, "fetch").mockImplementation((input, init) => {
      const url =
        typeof input === "string"
          ? input
          : input instanceof URL
            ? input.toString()
            : input.url;

      if (url.includes("/api/meta/categories")) {
        return Promise.resolve(
          new Response(JSON.stringify({ categories: [] }), { status: 200 })
        );
      }
      if (url.includes("/api/community/popular-keywords")) {
        return Promise.resolve(
          new Response(JSON.stringify({ keywords: [], rankings: [] }), {
            status: 200,
          })
        );
      }
      if (url.includes("/api/push/device")) {
        return Promise.resolve(
          new Response(
            JSON.stringify({
              status: "ok",
              device_uuid: "web_abort_test",
              device_token: "tok_abort_test",
            }),
            { status: 200 }
          )
        );
      }
      if (url.includes("/api/users/notifications") || url.includes("/api/wishlist/")) {
        return Promise.resolve(new Response(JSON.stringify([]), { status: 200 }));
      }
      if (url.includes("/api/community/hot-deals")) {
        signals.push({ url, signal: init?.signal });
        if (hangMode) {
          return new Promise<Response>((_, reject) => {
            init?.signal?.addEventListener(
              "abort",
              () => reject(new DOMException("Aborted", "AbortError")),
              { once: true }
            );
          });
        }
        return Promise.resolve(
          new Response(
            JSON.stringify({
              deals: [
                {
                  id: 9991,
                  title: "[쿠팡] 정상 응답 딜",
                  price: 15000,
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
      }
      return Promise.resolve(new Response(JSON.stringify({ ok: true }), { status: 200 }));
    });

    render(<Home />);
    await waitFor(() => {
      expect(screen.getByText("정상 응답 딜")).toBeInTheDocument();
    });

    // 연속 클릭 시 첫 번째(hang) 요청은 AbortController로 즉시 취소되고 에러 문구가 뜨지 않음
    hangMode = true;
    const tvCatBtn = screen.getByRole("button", { name: "가전/TV" });
    const livingCatBtn = screen.getByRole("button", { name: "생활용품" });

    fireEvent.click(tvCatBtn);
    const firstFilterReq = signals[signals.length - 1];
    expect(firstFilterReq.signal?.aborted).toBe(false);

    hangMode = false;
    fireEvent.click(livingCatBtn);
    expect(firstFilterReq.signal?.aborted).toBe(true);
    expect(
      screen.queryByText("핫딜 목록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.")
    ).toBeNull();

    await waitFor(() => {
      expect(
        signals.some((s) =>
          s.url.includes(`category=${encodeURIComponent("가전제품,생활용품")}`)
        )
      ).toBe(true);
    });

    // 8초 타임아웃 검증 (가상 타이머로 3회 시도 x 8000ms + 백오프 진행 -> 무한 로딩 대신 표준 에러 노출)
    vi.useFakeTimers();
    try {
      hangMode = true;
      await act(async () => {
        fireEvent.click(screen.getByRole("button", { name: "식품/건강" }));
      });
      await act(async () => {
        await vi.advanceTimersByTimeAsync(8500);
      });
      await act(async () => {
        await vi.advanceTimersByTimeAsync(9000);
      });
      await act(async () => {
        await vi.advanceTimersByTimeAsync(10000);
      });
      expect(
        screen.getByText("핫딜 목록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.")
      ).toBeInTheDocument();
    } finally {
      vi.useRealTimers();
    }
  });
});
