import React from "react";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import DealCard from "@/app/components/DealCard";
import DealDetailModal from "@/app/components/DealDetailModal";
import HeaderMoreMenu from "@/app/components/HeaderMoreMenu";
import Home from "@/app/page";
import { SHOP_FILTER_OPTIONS } from "@/app/components/FilterDrawerModal";
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
    title: `[쿠팡] M1 개편 테스트 상품 ${id}`,
    price: "19900",
    image_url: `https://cdn.example.com/m1-${id}.jpg`,
    scraped_at: "방금 전",
    post_link: `https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=${id}`,
    ecommerce_url: `https://www.coupang.com/vp/products/${id}`,
    currency: "KRW",
    category: "생활용품",
    shipping_fee: "무료배송",
    isClosed: false,
    honeyScore: 120,
    ...overrides,
  };
}

function mockHomeApis(
  hotDealsHandler?: (url: string) => Response | Promise<Response>
) {
  return vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
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
            { rank: 1, keyword: "제로콜라", deal_count: 15, recent_count: 5, status: "HOT" },
            { rank: 2, keyword: "로봇청소기", deal_count: 8, recent_count: 3, status: "NEW" },
          ],
          updated_at: "10분 전 기준",
        }),
        { status: 200 }
      );
    }
    if (url.includes("/api/push/device")) {
      return new Response(
        JSON.stringify({
          status: "ok",
          device_uuid: "web_m1_uuid",
          device_token: "tok_m1_token",
        }),
        { status: 200 }
      );
    }
    if (url.includes("/api/users/notifications") || url.includes("/api/wishlist/")) {
      return new Response(JSON.stringify([]), { status: 200 });
    }
    if (url.includes("/api/community/deals/")) {
      return new Response(
        JSON.stringify({
          id: 501,
          title: "[쿠팡] M1 상세 숨기기 테스트 상품",
          price: 19900,
          currency: "KRW",
          site_name: "뽐뿌",
          shop_name: "쿠팡",
          content_html: "<p>상세 본문</p>",
          is_closed: false,
        }),
        { status: 200 }
      );
    }
    if (url.includes("/api/community/hot-deals")) {
      if (hotDealsHandler) {
        return hotDealsHandler(url);
      }
      return new Response(
        JSON.stringify({
          deals: [
            {
              id: 501,
              title: "[쿠팡] M1 상세 숨기기 테스트 상품",
              price: 19900,
              currency: "KRW",
              site_name: "뽐뿌",
              shop_name: "쿠팡",
              post_url: "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=501",
              ecommerce_url: "https://www.coupang.com/vp/products/501",
              image_url: "https://cdn.example.com/m1-501.jpg",
              category: "생활용품",
              honey_score: 120,
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
}

describe("M1 UI/UX 전면 개편 회귀 검증 (F1~F6)", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.useRealTimers();
    localStorage.clear();
    try {
      window.history.replaceState(null, "", "/");
    } catch {}
  });

  it("F1: 헤더 검색창 포커스 시 팝오버(최근검색어 개별/전체 삭제·인기검색어 TOP10·알림 배너)가 열리고 relatedTarget/Escape/외부클릭으로 닫힌다", async () => {
    localStorage.setItem(
      "insightdeal_recent_searches",
      JSON.stringify(["맥북", "에어팟"])
    );
    mockHomeApis();

    const { container } = render(<Home />);

    await waitFor(() => {
      expect(screen.getByText("M1 상세 숨기기 테스트 상품")).toBeInTheDocument();
    });

    const searchInput = screen.getByLabelText("핫딜 검색어 입력");
    expect(searchInput).toHaveAttribute("aria-expanded", "false");

    fireEvent.focus(searchInput);
    expect(searchInput).toHaveAttribute("aria-expanded", "true");
    expect(screen.getByText("최근 검색어")).toBeInTheDocument();
    expect(screen.getByText("실시간 인기 검색어 TOP 10")).toBeInTheDocument();

    // 팝오버 내부에 중첩 버튼이 없음을 확인
    const popover = container.querySelector(".header-search-popover");
    expect(popover).not.toBeNull();
    expect(popover?.querySelectorAll("button button")).toHaveLength(0);

    // 입력 중 키워드 알림 받기 배너 노출
    fireEvent.change(searchInput, { target: { value: "로보락" } });
    expect(screen.getByRole("button", { name: "키워드 알림 받기" })).toBeInTheDocument();

    // relatedTarget이 팝오버 내부 버튼이면 onBlur가 팝오버를 닫지 않음
    const clearAllBtn = screen.getByRole("button", { name: "전체 삭제" });
    fireEvent.blur(searchInput, { relatedTarget: clearAllBtn });
    expect(searchInput).toHaveAttribute("aria-expanded", "true");

    // iOS Safari/WKWebView 시뮬레이션: mouseDown(button) -> blur(relatedTarget: null) 후에도 팝오버가 열려 있고 클릭이 정상 동작함
    const removeMacbookBtn = screen.getByRole("button", { name: "맥북 최근 검색어 삭제" });
    fireEvent.mouseDown(removeMacbookBtn);
    fireEvent.blur(searchInput, { relatedTarget: null });
    expect(searchInput).toHaveAttribute("aria-expanded", "true");
    expect(removeMacbookBtn).toBeInTheDocument();

    // 최근 검색어 개별 삭제 -> 맥북만 삭제되고 에어팟은 유지
    fireEvent.click(removeMacbookBtn);
    expect(screen.queryByRole("button", { name: "맥북" })).toBeNull();
    expect(screen.getByRole("button", { name: "에어팟" })).toBeInTheDocument();

    // 전체 삭제
    fireEvent.click(clearAllBtn);
    expect(screen.queryByText("최근 검색어")).toBeNull();

    // Escape 키로 닫힘
    fireEvent.keyDown(window, { key: "Escape" });
    expect(searchInput).toHaveAttribute("aria-expanded", "false");

    // 재오픈 후 외부 클릭으로 닫힘
    fireEvent.click(searchInput);
    expect(searchInput).toHaveAttribute("aria-expanded", "true");
    fireEvent.mouseDown(document.body);
    expect(searchInput).toHaveAttribute("aria-expanded", "false");
  });

  it("F1: HeaderMoreMenu는 4개 항목(키워드 알림·기기 동기화·ThemeToggle·앱 설치)을 수납하고 Escape 시 포커스를 복귀하며 토스 환경에서 앱 설치를 숨긴다", () => {
    const onOpenSyncModal = vi.fn();
    const onInstallApp = vi.fn();

    const { rerender } = render(
      <HeaderMoreMenu
        isTossMiniApp={false}
        onOpenSyncModal={onOpenSyncModal}
        onInstallApp={onInstallApp}
      />
    );

    const trigger = screen.getByRole("button", { name: "더보기 메뉴" });
    expect(trigger).toHaveAttribute("aria-haspopup", "true");
    expect(trigger).toHaveAttribute("aria-expanded", "false");

    fireEvent.click(trigger);
    expect(trigger).toHaveAttribute("aria-expanded", "true");
    const notifSettingsLink = screen.getByRole("link", { name: "키워드 알림 설정" });
    expect(notifSettingsLink).toBeInTheDocument();
    expect(notifSettingsLink).toHaveAttribute("href", "/settings/notifications");
    expect(notifSettingsLink).toHaveAttribute("data-prefetch", "false");
    expect(screen.getByRole("button", { name: "기기 동기화 및 계정 연결" })).toBeInTheDocument();
    expect(screen.getByRole("group", { name: "화면 테마 전환" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "앱 설치" })).toBeInTheDocument();

    // Escape 시 닫히고 트리거에 포커스 복귀
    fireEvent.keyDown(window, { key: "Escape" });
    expect(trigger).toHaveAttribute("aria-expanded", "false");
    expect(document.activeElement).toBe(trigger);

    // 토스 미니앱 환경에서는 앱 설치 미노출
    rerender(
      <HeaderMoreMenu
        isTossMiniApp={true}
        onOpenSyncModal={onOpenSyncModal}
        onInstallApp={onInstallApp}
      />
    );
    fireEvent.click(trigger);
    expect(screen.queryByRole("button", { name: "앱 설치" })).toBeNull();
  });

  it("F2: FilterDrawerModal은 18종 쇼핑몰·상호배타 마감토글·보기모드·뮤트관리를 수납하고 일괄 적용(1-RTT) 및 보조 필터 1줄 칩을 제어한다", async () => {
    const hotDealUrls: string[] = [];
    mockHomeApis((url) => {
      hotDealUrls.push(url);
      return new Response(
        JSON.stringify({
          deals: [
            {
              id: 501,
              title: "[쿠팡] M1 필터 드로어 테스트 상품",
              price: 19900,
              currency: "KRW",
              site_name: "뽐뿌",
              shop_name: "쿠팡",
              is_closed: false,
            },
          ],
          has_more: false,
          next_cursor: null,
        }),
        { status: 200 }
      );
    });

    const { container } = render(<Home />);
    await waitFor(() => {
      expect(screen.getByText("M1 필터 드로어 테스트 상품")).toBeInTheDocument();
    });

    // 1차 필터(인기 딜만)만 켰을 때는 보조 필터 적용 칩 바가 노출되지 않는다
    fireEvent.click(screen.getByRole("button", { name: "인기 딜만" }));
    expect(container.querySelector(".applied-filters-bar")).toBeNull();

    // 상세 필터 드로어 열기
    const callCountBeforeOpen = hotDealUrls.length;
    fireEvent.click(screen.getByRole("button", { name: /상세 필터/ }));
    expect(screen.getByRole("dialog", { name: "상세 필터" })).toBeInTheDocument();

    // 18종 쇼핑몰 옵션 검증
    const shopSelect = screen.getByLabelText("쇼핑몰 필터") as HTMLSelectElement;
    expect(SHOP_FILTER_OPTIONS).toHaveLength(18);
    expect(shopSelect.querySelectorAll("option").length).toBe(19);

    // 품절 제외 <-> 종료 포함 상호 배타 검증
    const hideClosedBtn = screen.getByRole("button", { name: "품절 제외" });
    const includeClosedBtn = screen.getByRole("button", { name: "종료 포함" });
    fireEvent.click(hideClosedBtn);
    expect(hideClosedBtn).toHaveAttribute("aria-pressed", "true");
    fireEvent.click(includeClosedBtn);
    expect(includeClosedBtn).toHaveAttribute("aria-pressed", "true");
    expect(hideClosedBtn).toHaveAttribute("aria-pressed", "false");

    // 무료배송만 + 1만원 이하 + 쇼핑몰 쿠팡 + 컴팩트 보기 선택 (적용 전에는 서버 재요청 없음)
    fireEvent.click(screen.getByRole("button", { name: "무료배송만" }));
    fireEvent.click(screen.getByRole("button", { name: "1만원 이하" }));
    fireEvent.change(shopSelect, { target: { value: "쿠팡" } });
    fireEvent.click(screen.getByRole("button", { name: "한줄 컴팩트 보기" }));
    expect(hotDealUrls.length).toBe(callCountBeforeOpen);

    // 적용하기 클릭 -> 1-RTT 호출 & 컴팩트 클래스 전환 & 보조 필터 배지(4)/칩 1줄 노출
    fireEvent.click(screen.getByRole("button", { name: "적용하기" }));
    await waitFor(() => {
      expect(hotDealUrls.length).toBe(callCountBeforeOpen + 1);
      const lastUrl = hotDealUrls[hotDealUrls.length - 1];
      expect(lastUrl).toContain("free_shipping=true");
      expect(lastUrl).toContain("include_closed=true");
      expect(lastUrl).toContain(`shop=${encodeURIComponent("쿠팡")}`);
      expect(lastUrl).toContain("max_price=10000");
    });

    expect(container.querySelector(".deal-list--compact")).not.toBeNull();
    expect(container.querySelector(".applied-filters-bar")).not.toBeNull();
    expect(container.querySelector(".filter-count-badge")).toHaveTextContent("4");
    expect(screen.getByRole("button", { name: /무료배송만/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /쇼핑몰: 쿠팡/ })).toBeInTheDocument();

    // 초기화 클릭 시 보조 필터 칩 제거 및 viewMode 카드 복귀
    fireEvent.click(screen.getByRole("button", { name: "적용 필터 초기화" }));
    expect(container.querySelector(".applied-filters-bar")).toBeNull();
    expect(container.querySelector(".deal-list--compact")).toBeNull();
    expect(localStorage.getItem("insightdeal_view_mode")).toBe("card");
  });

  it("F2(회귀): minPriceInput='0' 칩 공백 방지 및 FilterDrawerModal 내부 '초기화' 시 viewMode가 'card'로 복원된다", async () => {
    mockHomeApis();
    const { container } = render(<Home />);

    await waitFor(() => {
      expect(screen.getByText("M1 상세 숨기기 테스트 상품")).toBeInTheDocument();
    });

    // 상세 필터에서 최소 가격 '0', 최대 가격 '10000', '한줄 컴팩트 보기' 적용
    fireEvent.click(screen.getByRole("button", { name: /상세 필터/ }));
    fireEvent.change(screen.getByLabelText("최소 가격"), { target: { value: "0" } });
    fireEvent.change(screen.getByLabelText("최대 가격"), { target: { value: "10000" } });
    fireEvent.click(screen.getByRole("button", { name: "한줄 컴팩트 보기" }));
    fireEvent.click(screen.getByRole("button", { name: "적용하기" }));

    await waitFor(() => {
      expect(container.querySelector(".deal-list--compact")).not.toBeNull();
    });
    expect(localStorage.getItem("insightdeal_view_mode")).toBe("compact");
    expect(screen.getByText("가격: 0원 ~ 10,000원")).toBeInTheDocument();

    // 드로어 재오픈 후 드로어 내부 '초기화' 버튼 클릭 -> viewMode가 'card'로 복원됨
    fireEvent.click(screen.getByRole("button", { name: /상세 필터/ }));
    expect(screen.getByRole("dialog", { name: "상세 필터" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "초기화" }));

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).toBeNull();
    });
    expect(container.querySelector(".deal-list--compact")).toBeNull();
    expect(localStorage.getItem("insightdeal_view_mode")).toBe("card");
    expect(container.querySelector(".applied-filters-bar")).toBeNull();
  });

  it("F3: DealDetailModal의 '이 핫딜 숨기기' 클릭 시 모달이 닫히고 피드에서 즉시 제거되며 localStorage에 저장된다", async () => {
    mockHomeApis();

    const { container } = render(<Home />);
    await waitFor(() => {
      expect(screen.getByText("M1 상세 숨기기 테스트 상품")).toBeInTheDocument();
    });

    fireEvent.click(
      screen.getByRole("button", { name: /M1 상세 숨기기 테스트 상품 상세 정보 보기/ })
    );

    const hideBtn = await screen.findByRole("button", { name: "이 핫딜 숨기기" });
    fireEvent.click(hideBtn);

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).toBeNull();
      expect(container.querySelectorAll(".deal-list article.deal-card")).toHaveLength(0);
    });

    const hiddenStored = JSON.parse(
      localStorage.getItem("insightdeal_hidden_deal_ids") || "[]"
    );
    expect(hiddenStored).toContain(501);

    // 단독 컴포넌트 단위 검증: onHideDeal + onClose 호출 확인
    const onHideDeal = vi.fn();
    const onClose = vi.fn();
    render(
      <DealDetailModal
        deal={buildDeal(777, { isClosed: true })}
        onClose={onClose}
        showToast={vi.fn()}
        onHideDeal={onHideDeal}
      />
    );
    fireEvent.click(await screen.findByRole("button", { name: "이 핫딜 숨기기" }));
    expect(onHideDeal).toHaveBeenCalledWith(777);
    expect(onClose).toHaveBeenCalledTimes(1);
  });

  it("F3/F6: DealCard는 비-중첩 트리거, display !== 'none' 형제 액션, 좌상단 배지 최대 1개(품절 우선)를 보장한다", () => {
    const deal = buildDeal(888, { isClosed: true, honeyScore: 250 });
    const { container } = render(
      <DealCard
        deal={deal}
        appliedSearch=""
        isMounted={true}
        currentTime={Date.now()}
        isWishlisted={false}
        isAlertSet={false}
        onSelectDeal={vi.fn()}
        onToggleAlert={vi.fn()}
        onToggleWishlist={vi.fn()}
        onShowToast={vi.fn()}
      />
    );

    const article = container.querySelector("article.glass-panel.deal-card");
    expect(article).not.toBeNull();

    const bodyTrigger = article?.querySelector(".deal-card-body-trigger");
    expect(bodyTrigger).not.toBeNull();
    expect(bodyTrigger?.querySelectorAll("a, button")).toHaveLength(0);

    // 좌상단 핵심 배지 최대 1개 ('품절·종료' 우선 -> '인기' 미렌더)
    expect(article?.querySelectorAll(".deal-thumb-badge")).toHaveLength(1);
    expect(screen.getByText("품절·종료")).toBeInTheDocument();
    expect(screen.queryByText("인기")).toBeNull();

    // 브랜드 톤 텍스트 컬러 클래스(코랄레드) 적용 확인
    const coupangShopEl = article?.querySelector(".deal-meta-shop");
    expect(coupangShopEl).toHaveClass("deal-meta-shop--coral");

    // 형제 요소 display !== "none" 검증
    const wishBtn = screen.getByRole("button", { name: "찜하기" });
    const bellBtn = screen.getByRole("button", { name: "가격 알림 설정" });
    const sourceLink = article?.querySelector("a.deal-source-link") as HTMLElement;
    const buyLink = article?.querySelector("a.deal-card-buy-link") as HTMLElement;
    expect(sourceLink).not.toBeNull();
    expect(buyLink).not.toBeNull();

    [wishBtn, bellBtn, sourceLink, buyLink].forEach((el) => {
      expect(window.getComputedStyle(el).display).not.toBe("none");
    });

    // 네이버/올리브영/지마켓 등은 에메랄드 브랜드 톤 클래스 적용
    const naverDeal = buildDeal(889, {
      shop_name: "네이버",
      title: "[네이버] 에메랄드 톤 닭가슴살 100g 10개",
      price: "19900",
    });
    const { container: naverContainer } = render(
      <DealCard
        deal={naverDeal}
        appliedSearch=""
        isMounted={true}
        currentTime={Date.now()}
        isWishlisted={false}
        isAlertSet={false}
        onSelectDeal={vi.fn()}
        onToggleAlert={vi.fn()}
        onToggleWishlist={vi.fn()}
        onShowToast={vi.fn()}
      />
    );
    expect(naverContainer.querySelector(".deal-meta-shop")).toHaveClass(
      "deal-meta-shop--emerald"
    );
  });

  it("F5: 새 핫딜 폴링 감지 시 다크 캡슐('방금 새 핫딜 N건 도착 ↑')을 렌더링하고 클릭 시 상단 리로드한다", async () => {
    vi.useFakeTimers();
    let pollTriggered = false;
    const scrollToSpy = vi.fn();
    const origScrollTo = window.scrollTo;
    Object.defineProperty(window, "scrollTo", {
      configurable: true,
      writable: true,
      value: scrollToSpy,
    });

    mockHomeApis((url) => {
      if (url.includes("limit=8")) {
        pollTriggered = true;
        return new Response(
          JSON.stringify({
            deals: [
              { id: 901, title: "[쿠팡] 방금 올라온 새 딜 1", price: 10000, site_name: "뽐뿌" },
              { id: 902, title: "[쿠팡] 방금 올라온 새 딜 2", price: 12000, site_name: "뽐뿌" },
            ],
            has_more: false,
          }),
          { status: 200 }
        );
      }
      return new Response(
        JSON.stringify({
          deals: [
            {
              id: 500,
              title: "[쿠팡] 기존 최신 딜",
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
      );
    });

    try {
      const { container } = render(<Home />);

      await act(async () => {
        await vi.advanceTimersByTimeAsync(100);
      });
      expect(screen.getByText("기존 최신 딜")).toBeInTheDocument();

      await act(async () => {
        await vi.advanceTimersByTimeAsync(50_500);
      });
      expect(pollTriggered).toBe(true);

      const pill = screen.getByRole("button", { name: /방금 새 핫딜 2건 도착 ↑/ });
      expect(pill).toBeInTheDocument();
      expect(container.querySelector(".floating-new-deals-dot")).not.toBeNull();

      await act(async () => {
        fireEvent.click(pill);
      });
      expect(scrollToSpy).toHaveBeenCalledWith({ top: 0, behavior: "smooth" });
      expect(screen.queryByRole("button", { name: /방금 새 핫딜/ })).toBeNull();
    } finally {
      Object.defineProperty(window, "scrollTo", {
        configurable: true,
        writable: true,
        value: origScrollTo,
      });
      vi.useRealTimers();
    }
  });
});
