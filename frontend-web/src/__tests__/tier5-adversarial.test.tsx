import React from "react";
import {
  act,
  fireEvent,
  render,
  screen,
  waitFor,
  within,
} from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import Home from "@/app/page";
import HeaderMoreMenu from "@/app/components/HeaderMoreMenu";
import NotificationDrawer from "@/app/components/NotificationDrawer";

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

function buildRawDeal(id: number, overrides: Record<string, unknown> = {}) {
  return {
    id,
    title: `[쿠팡] 테스트 핫딜 ${id}`,
    price: 15000 + id,
    currency: "KRW",
    site_name: "뽐뿌",
    shop_name: "쿠팡",
    post_url: `https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=${id}`,
    ecommerce_url: `https://www.coupang.com/vp/products/${id}`,
    image_url: `https://cdn.example.com/deal-${id}.jpg`,
    category: "생활용품",
    honey_score: 120,
    is_closed: false,
    ...overrides,
  };
}

function createCommonFetchMock(
  hotDealsHandler: (url: string) => Response | Promise<Response>,
  extraHandler?: (url: string) => Response | Promise<Response> | null
) {
  return vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
    const url =
      typeof input === "string"
        ? input
        : input instanceof URL
          ? input.toString()
          : input.url;

    if (extraHandler) {
      const custom = await extraHandler(url);
      if (custom) return custom;
    }
    if (url.includes("/api/meta/categories")) {
      return new Response(JSON.stringify({ categories: [] }), { status: 200 });
    }
    if (url.includes("/api/community/popular-keywords")) {
      return new Response(
        JSON.stringify({ keywords: [], rankings: [], updated_at: "" }),
        { status: 200 }
      );
    }
    if (url.includes("/api/push/device")) {
      return new Response(
        JSON.stringify({
          status: "ok",
          device_uuid: "web_tier5_uuid",
          device_token: "tok_tier5_token",
        }),
        { status: 200 }
      );
    }
    if (url.includes("/api/users/notifications") || url.includes("/api/wishlist/")) {
      return new Response(JSON.stringify([]), { status: 200 });
    }
    if (url.includes("/api/community/hot-deals")) {
      return hotDealsHandler(url);
    }
    return new Response(JSON.stringify({ ok: true }), { status: 200 });
  });
}

describe("Tier 5 적대적 회귀 검증 (W-GAP-01 ~ W-GAP-04, W-NOTE-01 ~ W-NOTE-03)", () => {
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
    localStorage.clear();
    sessionStorage.clear();
    try {
      window.history.replaceState(null, "", "/");
    } catch {}
  });

  it("T5-W-01: DealDetailModal에서 딜 101→102 내부 전환 후 닫기/popstate 1회 시 ?dealId=가 완전히 제거되고 부활하지 않는다", async () => {
    window.history.replaceState(null, "", "/");
    const pushSpy = vi.spyOn(window.history, "pushState");
    const replaceSpy = vi.spyOn(window.history, "replaceState");

    createCommonFetchMock(
      () =>
        new Response(
          JSON.stringify({
            deals: [buildRawDeal(101), buildRawDeal(102)],
            has_more: false,
            next_cursor: null,
          }),
          { status: 200 }
        ),
      (url) => {
        if (url.includes("/api/community/deals/101/comments")) {
          return new Response(JSON.stringify([]), { status: 200 });
        }
        if (url.includes("/api/community/deals/102/comments")) {
          return new Response(JSON.stringify([]), { status: 200 });
        }
        if (url.includes("/api/community/deals/101")) {
          return new Response(
            JSON.stringify({
              ...buildRawDeal(101),
              similar_deals: [buildRawDeal(102)],
            }),
            { status: 200 }
          );
        }
        if (url.includes("/api/community/deals/102")) {
          return new Response(JSON.stringify(buildRawDeal(102)), {
            status: 200,
          });
        }
        return null;
      }
    );

    render(<Home />);

    const card101 = await screen.findByRole("button", {
      name: /테스트 핫딜 101 상세 정보 보기/,
    });
    fireEvent.click(card101);

    await waitFor(() => {
      expect(screen.getByRole("dialog")).toBeInTheDocument();
      expect(window.location.search).toContain("dealId=101");
    });

    const pushCallsAfterOpen = pushSpy.mock.calls.filter(
      (args) =>
        (args[0] as { insightDealModal?: number } | null)?.insightDealModal !==
        undefined
    ).length;
    expect(pushCallsAfterOpen).toBe(1);

    // ArrowRight 키로 101 -> 102 내부 전환: pushState 추가 적재 금지, replaceState로 교체
    fireEvent.keyDown(window, { key: "ArrowRight" });

    await waitFor(() => {
      expect(window.location.search).toContain("dealId=102");
    });

    const pushCallsAfterSwitch = pushSpy.mock.calls.filter(
      (args) =>
        (args[0] as { insightDealModal?: number } | null)?.insightDealModal !==
        undefined
    ).length;
    expect(pushCallsAfterSwitch).toBe(1);
    expect(
      replaceSpy.mock.calls.some(
        (args) =>
          (args[0] as { insightDealModal?: number } | null)?.insightDealModal ===
            102 && String(args[2] || "").includes("dealId=102")
      )
    ).toBe(true);

    // 닫기 버튼 클릭 1회: history.back() 1회만으로 ?dealId= 완전 제거
    fireEvent.click(screen.getByRole("button", { name: "상세 모달 닫기" }));

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
      expect(window.location.search).not.toContain("dealId=");
    });

    // 이후 필터 변경(replaceState) 시에도 닫힌 딜 101/102가 부활하지 않아야 함
    fireEvent.click(screen.getByRole("button", { name: "인기 딜만" }));

    await waitFor(() => {
      expect(window.location.search).toContain("hot=1");
    });
    expect(window.location.search).not.toContain("dealId=");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();

    // similar_deals 전환 후 popstate 1회 닫기 검증
    const card101Again = await screen.findByRole("button", {
      name: /테스트 핫딜 101 상세 정보 보기/,
    });
    fireEvent.click(card101Again);

    const dialogEl = await screen.findByRole("dialog");
    const relatedBtn = await within(dialogEl).findByText(/테스트 핫딜 102/);
    fireEvent.click(relatedBtn);

    await waitFor(() => {
      expect(window.location.search).toContain("dealId=102");
    });

    window.history.replaceState(null, "", "/?hot=1");
    fireEvent(window, new PopStateEvent("popstate", { state: null }));

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
      expect(window.location.search).not.toContain("dealId=");
    });
  });

  it("T5-W-01b: ?dealId=101 딥링크 진입 후 모달 내 딜 102 전환·닫기 시 history.back() 오발동 없이 ?dealId=가 제거된다", async () => {
    window.history.replaceState(null, "", "/?dealId=101");
    const backSpy = vi.spyOn(window.history, "back").mockImplementation(() => {});

    createCommonFetchMock(
      () =>
        new Response(
          JSON.stringify({
            deals: [buildRawDeal(101), buildRawDeal(102)],
            has_more: false,
            next_cursor: null,
          }),
          { status: 200 }
        ),
      (url) => {
        if (url.includes("/comments")) {
          return new Response(JSON.stringify([]), { status: 200 });
        }
        if (url.includes("/api/community/deals/101")) {
          return new Response(JSON.stringify(buildRawDeal(101)), { status: 200 });
        }
        if (url.includes("/api/community/deals/102")) {
          return new Response(JSON.stringify(buildRawDeal(102)), { status: 200 });
        }
        return null;
      }
    );

    render(<Home />);

    await waitFor(() => {
      expect(screen.getByRole("dialog")).toBeInTheDocument();
    });

    fireEvent.keyDown(window, { key: "ArrowRight" });
    await waitFor(() => {
      expect(window.location.search).toContain("dealId=102");
    });

    fireEvent.click(screen.getByRole("button", { name: "상세 모달 닫기" }));

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
      expect(window.location.search).not.toContain("dealId=");
    });
    expect(backSpy).not.toHaveBeenCalled();
  });

  it("T5-W-02: 50초 실시간 폴링 URL에 shop, free_shipping, min_price, max_price 필터가 포함되고 미매칭 시 허위 캡슐을 띄우지 않는다", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const pollUrls: string[] = [];

    createCommonFetchMock((url) => {
      if (url.includes("limit=8")) {
        pollUrls.push(url);
        // 필터 조건(무신사/무료배송/10000원 이하) 적용 후 폴링 시 매칭 신규 딜 0건 응답
        return new Response(JSON.stringify({ deals: [], has_more: false }), {
          status: 200,
        });
      }
      return new Response(
        JSON.stringify({
          deals: [
            buildRawDeal(500, {
              title: "[무신사] 테스트 핫딜 500",
              shop_name: "무신사",
              price: 9000,
            }),
          ],
          has_more: false,
          next_cursor: null,
        }),
        { status: 200 }
      );
    });

    render(<Home />);

    expect(await screen.findByText("테스트 핫딜 500")).toBeInTheDocument();

    // 상세 필터 드로어에서 shop="무신사", freeShipping=true, maxPrice="10000" 적용
    fireEvent.click(screen.getByRole("button", { name: /상세 필터/ }));
    fireEvent.change(screen.getByLabelText("쇼핑몰 필터"), {
      target: { value: "무신사" },
    });
    fireEvent.change(screen.getByLabelText("최대 가격"), {
      target: { value: "10000" },
    });
    fireEvent.click(screen.getByRole("button", { name: "무료배송만" }));
    fireEvent.click(screen.getByRole("button", { name: "적용하기" }));

    await waitFor(() => {
      expect(
        screen.getByRole("button", { name: /쇼핑몰: 무신사/ })
      ).toBeInTheDocument();
    });

    // 50,000ms 경과 후 폴링 실행
    await act(async () => {
      await vi.advanceTimersByTimeAsync(50_200);
    });

    expect(pollUrls.length).toBeGreaterThanOrEqual(1);
    const lastPollUrl = pollUrls[pollUrls.length - 1];
    expect(lastPollUrl).toContain("shop=%EB%AC%B4%EC%8B%A0%EC%82%AC");
    expect(lastPollUrl).toContain("free_shipping=true");
    expect(lastPollUrl).toContain("max_price=10000");
    expect(
      screen.queryByRole("button", { name: /방금 새 핫딜/ })
    ).not.toBeInTheDocument();
  });

  it("T5-W-03: 무한스크롤 offset > 0 실패 시 IntersectionObserver 무한 재호출 루프에 빠지지 않고 수동 '다시 시도' 버튼을 제공한다", async () => {
    vi.useFakeTimers();

    const observerCallbacks: IntersectionObserverCallback[] = [];
    const OriginalIO = window.IntersectionObserver;
    let observeCallCount = 0;

    class ControllableIO implements IntersectionObserver {
      readonly root: Element | Document | null = null;
      readonly rootMargin: string = "";
      readonly thresholds: ReadonlyArray<number> = [];
      constructor(cb: IntersectionObserverCallback) {
        observerCallbacks.push(cb);
      }
      disconnect(): void {}
      observe(): void {
        observeCallCount += 1;
      }
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

    let page2AttemptCount = 0;
    let shouldFailPage2 = true;
    const initialDeals = Array.from({ length: 20 }, (_, i) =>
      buildRawDeal(1000 - i)
    );

    try {
      createCommonFetchMock((url) => {
        if (url.includes("offset=20")) {
          page2AttemptCount += 1;
          if (shouldFailPage2) {
            return new Response(
              JSON.stringify({ detail: "Service Unavailable" }),
              { status: 503 }
            );
          }
          return new Response(
            JSON.stringify({
              deals: [buildRawDeal(950)],
              has_more: false,
              next_cursor: null,
            }),
            { status: 200 }
          );
        }
        return new Response(
          JSON.stringify({
            deals: initialDeals,
            has_more: true,
            next_cursor: { indexed_at: "2026-11-24T10:00:00Z", id: 981 },
          }),
          { status: 200 }
        );
      });

      render(<Home />);

      await act(async () => {
        await vi.advanceTimersByTimeAsync(150);
      });
      expect(screen.getByText("테스트 핫딜 1000")).toBeInTheDocument();

      const latestCb = observerCallbacks[observerCallbacks.length - 1];
      expect(latestCb).toBeDefined();

      // 하단 센티널 교차 발화 (offset=20 요청 시작)
      act(() => {
        latestCb(
          [{ isIntersecting: true } as IntersectionObserverEntry],
          {} as IntersectionObserver
        );
      });

      // fetchLiveDeals 내부 503 재시도 백오프(600ms + 1200ms) 포함 완료 대기
      await act(async () => {
        await vi.advanceTimersByTimeAsync(2500);
      });

      // 1라운드(내부 fetch 최대 3회) 완료 후 추가 라운드 재호출이 없어야 함
      const attemptsRightAfterFailure = page2AttemptCount;
      const observeCountAfterError = observeCallCount;
      expect(attemptsRightAfterFailure).toBe(3);

      // 수동 "다시 시도" 버튼이 표시되고 토스트는 1개만 유지됨
      const retryBtn = screen.getByRole("button", { name: "다시 시도" });
      expect(retryBtn).toBeInTheDocument();
      const alerts = screen.getAllByText(
        "추가 딜을 불러오지 못했어요. 잠시 후 다시 시도해 주세요."
      );
      // 하단 배너 1 + 토스트 1 이하 (무한 재호출 없음)
      expect(alerts.length).toBeLessThanOrEqual(2);

      // 충분히 시간이 더 지나도 IntersectionObserver가 재관찰/무한루프를 유발하지 않음
      await act(async () => {
        await vi.advanceTimersByTimeAsync(5000);
      });
      expect(page2AttemptCount).toBe(attemptsRightAfterFailure);
      expect(observeCallCount).toBe(observeCountAfterError);

      // 수동 "다시 시도" 클릭 시 2페이지 정상 로드
      shouldFailPage2 = false;
      fireEvent.click(retryBtn);
      await act(async () => {
        await vi.advanceTimersByTimeAsync(200);
      });

      expect(screen.getByText("테스트 핫딜 950")).toBeInTheDocument();
      expect(
        screen.queryByRole("button", { name: "다시 시도" })
      ).not.toBeInTheDocument();

      // 재실패 후 필터 변경(인기 딜만 토글) 시에도 loadMoreError가 즉시 해제되어야 함
      shouldFailPage2 = true;
      // 다시 has_more: true인 필터 결과에서 offset=20 실패를 시뮬레이션하기 위해 새 필터 적용
      fireEvent.click(screen.getByRole("button", { name: "인기 딜만" }));
      await act(async () => {
        await vi.advanceTimersByTimeAsync(150);
      });
      const cbAfterFilter = observerCallbacks[observerCallbacks.length - 1];
      act(() => {
        cbAfterFilter(
          [{ isIntersecting: true } as IntersectionObserverEntry],
          {} as IntersectionObserver
        );
      });
      await act(async () => {
        await vi.advanceTimersByTimeAsync(2500);
      });
      expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();

      // 새로고침 클릭 시 loadMoreError 해제 확인
      fireEvent.click(screen.getByRole("button", { name: "새로고침" }));
      await act(async () => {
        await vi.advanceTimersByTimeAsync(200);
      });
      expect(
        screen.queryByRole("button", { name: "다시 시도" })
      ).not.toBeInTheDocument();
    } finally {
      Object.defineProperty(window, "IntersectionObserver", {
        writable: true,
        configurable: true,
        value: OriginalIO,
      });
    }
  });

  it("T5-W-04: insightdeal_recent_searches에 비문자열 오염 데이터가 섞여도 크래시 없이 정상 문자열만 렌더링하고 검색한다", async () => {
    localStorage.setItem(
      "insightdeal_recent_searches",
      JSON.stringify([123, null, { bad: 1 }, "   ", "정상키워드"])
    );

    const requestedUrls: string[] = [];
    createCommonFetchMock((url) => {
      requestedUrls.push(url);
      return new Response(
        JSON.stringify({
          deals: [buildRawDeal(301)],
          has_more: false,
          next_cursor: null,
        }),
        { status: 200 }
      );
    });

    render(<Home />);

    const searchInput = await screen.findByLabelText("핫딜 검색어 입력");
    fireEvent.focus(searchInput);

    const recentChip = await screen.findByRole("button", {
      name: "정상키워드",
    });
    expect(recentChip).toBeInTheDocument();
    expect(screen.queryByText("123")).not.toBeInTheDocument();

    fireEvent.click(recentChip);

    await waitFor(() => {
      expect(
        requestedUrls.some((u) =>
          u.includes("keyword=%EC%A0%95%EC%83%81%ED%82%A4%EC%9B%8C%EB%93%9C")
        )
      ).toBe(true);
    });
  });

  it("T5-W-05: FilterDrawerModal에서 minPrice > maxPrice 역전 입력 시 10000~50000으로 자동 교정하여 요청·표시·복원한다", async () => {
    const requestedUrls: string[] = [];
    createCommonFetchMock((url) => {
      requestedUrls.push(url);
      return new Response(
        JSON.stringify({
          deals: [buildRawDeal(401, { price: 25000 })],
          has_more: false,
          next_cursor: null,
        }),
        { status: 200 }
      );
    });

    render(<Home />);

    await waitFor(() => {
      expect(screen.getByText("테스트 핫딜 401")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: /상세 필터/ }));
    fireEvent.change(screen.getByLabelText("최소 가격"), {
      target: { value: "50000" },
    });
    fireEvent.change(screen.getByLabelText("최대 가격"), {
      target: { value: "10000" },
    });
    fireEvent.click(screen.getByRole("button", { name: "적용하기" }));

    await waitFor(() => {
      expect(
        requestedUrls.some(
          (u) => u.includes("min_price=10000") && u.includes("max_price=50000")
        )
      ).toBe(true);
    });

    expect(
      screen.getByRole("button", { name: /가격: 10,000원 ~ 50,000원/ })
    ).toBeInTheDocument();

    // 드로어 재오픈 시 교정된 10000 / 50000 유지 확인
    fireEvent.click(screen.getByRole("button", { name: /상세 필터/ }));
    expect(screen.getByLabelText("최소 가격")).toHaveValue(10000);
    expect(screen.getByLabelText("최대 가격")).toHaveValue(50000);
  });

  it("W-NOTE-03: ?toss=1 디버그 쿼리 모드에서 설정 이동 링크가 ?toss=1과 prefetch={false}를 모두 유지한다", async () => {
    window.history.replaceState(null, "", "/?toss=1");
    createCommonFetchMock(() =>
      new Response(JSON.stringify({ deals: [], has_more: false }), {
        status: 200,
      })
    );

    const { unmount } = render(
      <HeaderMoreMenu
        isTossMiniApp={true}
        onOpenSyncModal={vi.fn()}
        onInstallApp={vi.fn()}
      />
    );
    fireEvent.click(screen.getByRole("button", { name: "더보기 메뉴" }));
    const menuSettingsLink = screen.getByRole("link", {
      name: "키워드 알림 설정",
    });
    expect(menuSettingsLink).toHaveAttribute(
      "href",
      "/settings/notifications?toss=1"
    );
    expect(menuSettingsLink).toHaveAttribute("data-prefetch", "false");
    unmount();

    render(
      <NotificationDrawer
        isOpen={true}
        onClose={vi.fn()}
        deals={[]}
        onSelectDeal={vi.fn()}
      />
    );
    const drawerSettingsLink = await screen.findByRole("link", {
      name: /키워드 알림 설정하기/,
    });
    expect(drawerSettingsLink).toHaveAttribute(
      "href",
      "/settings/notifications?toss=1"
    );
    expect(drawerSettingsLink).toHaveAttribute("data-prefetch", "false");
  });
});
