import React from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import Home from "@/app/page";
import NotificationSettingsPage from "@/app/settings/notifications/page";
import NotificationsAliasPage from "@/app/notifications/page";

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

const ORIGINAL_USER_AGENT = navigator.userAgent;

function mockNotificationAndHomeApis() {
  return vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
    const url =
      typeof input === "string"
        ? input
        : input instanceof URL
          ? input.toString()
          : input.url;

    if (url.includes("/api/push/device")) {
      return new Response(
        JSON.stringify({
          status: "ok",
          device_uuid: "web_toss_nav_uuid",
          device_token: "tok_toss_nav_token",
        }),
        { status: 200 }
      );
    }
    if (url.includes("/api/push/keywords")) {
      return new Response(
        JSON.stringify({
          keywords: [],
          detailed_keywords: [],
          dnd_enabled: false,
          dnd_start_time: "21:00",
          dnd_end_time: "08:00",
        }),
        { status: 200 }
      );
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
    if (url.includes("/api/users/notifications") || url.includes("/api/wishlist/")) {
      return new Response(JSON.stringify([]), { status: 200 });
    }
    if (url.includes("/api/community/hot-deals")) {
      return new Response(
        JSON.stringify({
          deals: [
            {
              id: 901,
              title: "[쿠팡] 토스 내비게이션 검증 테스트 상품",
              price: 15900,
              currency: "KRW",
              site_name: "뽐뿌",
              shop_name: "쿠팡",
              post_url: "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=901",
              ecommerce_url: "https://www.coupang.com/vp/products/901",
              image_url: "https://cdn.example.com/toss-nav-901.jpg",
              category: "생활용품",
              honey_score: 100,
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

describe("앱인토스 20261124-3 내비게이션 중복 헤더·뒤로가기 제거 회귀 검증", () => {
  afterEach(() => {
    delete (window as unknown as { __appsInTossConstants?: unknown })
      .__appsInTossConstants;
    vi.unstubAllEnvs();
    Object.defineProperty(window.navigator, "userAgent", {
      configurable: true,
      value: ORIGINAL_USER_AGENT,
    });
    vi.restoreAllMocks();
    localStorage.clear();
    sessionStorage.clear();
    try {
      window.history.replaceState(null, "", "/");
    } catch {}
  });

  it("일반 웹 환경에서는 NotificationSettingsPage와 /notifications re-export 모두에 ← 홈으로 돌아가기 링크가 노출된다", async () => {
    mockNotificationAndHomeApis();

    const settingsView = render(<NotificationSettingsPage />);

    await waitFor(() => {
      const backLink = screen.getByRole("link", { name: /홈으로 돌아가기/ });
      expect(backLink).toBeInTheDocument();
      expect(backLink).toHaveAttribute("href", "/");
      expect(backLink).toHaveAttribute("data-prefetch", "false");
    });

    settingsView.unmount();

    render(<NotificationsAliasPage />);
    await waitFor(() => {
      const aliasBackLink = screen.getByRole("link", { name: /홈으로 돌아가기/ });
      expect(aliasBackLink).toBeInTheDocument();
      expect(aliasBackLink).toHaveAttribute("href", "/");
      expect(aliasBackLink).toHaveAttribute("data-prefetch", "false");
    });
  });

  it("토스 미니앱 환경(__appsInTossConstants 및 NEXT_PUBLIC_TOSS_MINIAPP=true)에서는 NotificationSettingsPage에 ← 홈으로 돌아가기 링크가 미노출된다", async () => {
    (window as unknown as { __appsInTossConstants?: Record<string, unknown> })
      .__appsInTossConstants = {};
    vi.stubEnv("NEXT_PUBLIC_TOSS_MINIAPP", "true");
    mockNotificationAndHomeApis();

    const settingsView = render(<NotificationSettingsPage />);

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: "알림 및 키워드 설정" })
      ).toBeInTheDocument();
    });
    expect(
      screen.queryByRole("link", { name: /홈으로 돌아가기/ })
    ).not.toBeInTheDocument();

    settingsView.unmount();

    // 런타임 브릿지만 주입되고 env는 일반 웹인 경우에도 useEffect 후 미노출 보장
    vi.unstubAllEnvs();
    render(<NotificationsAliasPage />);
    await waitFor(() => {
      expect(
        screen.queryByRole("link", { name: /홈으로 돌아가기/ })
      ).not.toBeInTheDocument();
    });
  });

  it("일반 웹 환경에서는 Home의 header.navbar에 .nav-logo가 노출되고 .navbar--toss가 미적용된다", async () => {
    mockNotificationAndHomeApis();

    const { container } = render(<Home />);

    await waitFor(() => {
      expect(
        screen.getByText("토스 내비게이션 검증 테스트 상품")
      ).toBeInTheDocument();
    });

    const navbar = container.querySelector("header.navbar");
    expect(navbar).not.toBeNull();
    expect(navbar).not.toHaveClass("navbar--toss");

    const navLogo = container.querySelector("header.navbar .nav-logo");
    expect(navLogo).not.toBeNull();
    expect(navLogo).toHaveTextContent("얼만데");
  });

  it("토스 미니앱 환경(__appsInTossConstants 주입)에서는 Home의 header.navbar에 .navbar--toss가 적용되고 .nav-logo는 미노출되며 검색·찜·알림·더보기(앱 설치 제외)가 유지된다", async () => {
    (window as unknown as { __appsInTossConstants?: Record<string, unknown> })
      .__appsInTossConstants = {};
    mockNotificationAndHomeApis();

    const { container } = render(<Home />);

    await waitFor(() => {
      const navbar = container.querySelector("header.navbar");
      expect(navbar).not.toBeNull();
      expect(navbar).toHaveClass("navbar--toss");
    });

    expect(container.querySelector("header.navbar .nav-logo")).toBeNull();

    // 핵심 상단 컨트롤 유지 확인
    expect(
      screen.getByRole("combobox", { name: "핫딜 검색어 입력" })
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "내 찜 목록 열기" })
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "알림 내역 열기" })
    ).toBeInTheDocument();

    const moreBtn = screen.getByRole("button", { name: "더보기 메뉴" });
    expect(moreBtn).toBeInTheDocument();
    fireEvent.click(moreBtn);

    const moreRegion = screen.getByRole("region", { name: "더보기 메뉴" });
    expect(moreRegion).toBeInTheDocument();
    const moreSettingsLink = screen.getByRole("link", { name: "키워드 알림 설정" });
    expect(moreSettingsLink).toBeInTheDocument();
    expect(moreSettingsLink).toHaveAttribute("href", "/settings/notifications");
    expect(moreSettingsLink).toHaveAttribute("data-prefetch", "false");
    expect(
      screen.getByRole("button", { name: "기기 동기화 및 계정 연결" })
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "앱 설치" })
    ).not.toBeInTheDocument();
  });
});
