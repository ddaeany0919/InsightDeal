import React from "react";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import PopularKeywordPresets, {
  POPULAR_KEYWORD_PRESETS,
} from "@/app/components/PopularKeywordPresets";
import TossAdBanner from "@/app/components/TossAdBanner";
import {
  resetTossAdsInitState,
  shareDealInToss,
} from "@/app/lib/tossBridge";
import { onRequestGet as handleShareOgRequest } from "../../functions/s/[id].js";

const sdkMocks = vi.hoisted(() => ({
  initialize: vi.fn(),
  attachBanner: vi.fn(),
  destroy: vi.fn(),
  createLink: vi.fn(),
  sendMessage: vi.fn(),
  analyticsClick: vi.fn(),
}));

vi.mock("@apps-in-toss/web-framework", () => {
  const initializeFn = Object.assign(
    (...args: unknown[]) => sdkMocks.initialize(...args),
    { isSupported: () => true }
  );
  const attachBannerFn = Object.assign(
    (...args: unknown[]) => sdkMocks.attachBanner(...args),
    { isSupported: () => true }
  );
  return {
    TossAds: {
      initialize: initializeFn,
      attachBanner: attachBannerFn,
    },
    Share: {
      createLink: (...args: unknown[]) => sdkMocks.createLink(...args),
      sendMessage: (...args: unknown[]) => sdkMocks.sendMessage(...args),
    },
    Analytics: {
      screen: vi.fn().mockResolvedValue(undefined),
      click: (...args: unknown[]) => sdkMocks.analyticsClick(...args),
    },
    Review: {
      request: Object.assign(vi.fn(), { isSupported: () => false }),
    },
    Device: {
      openURL: vi.fn(),
    },
    Clipboard: {
      setText: vi.fn(),
    },
  };
});

describe("v6 유입·리텐션·광고 노출 개선 기능", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    resetTossAdsInitState();
    delete (window as unknown as { __appsInTossConstants?: unknown }).__appsInTossConstants;
    delete (window as unknown as { __appsInToss?: unknown }).__appsInToss;
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("Pages Function /s/[id]는 크롤러에게 상품 OG HTML을, 일반 브라우저에는 ?dealId= 리다이렉트를 반환한다", async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(
        JSON.stringify({
          id: 26505,
          title: '삼성 <b>SSD</b> "2TB"',
          price: 159000,
          currency: "KRW",
          shipping_fee: "무료배송",
          shop_name: "쿠팡",
          site_name: "퀘이사존",
          image_url: "https://cdn.example.com/ssd.jpg",
          is_closed: false,
          is_free: false,
        }),
        { status: 200, headers: { "Content-Type": "application/json" } }
      )
    );
    vi.stubGlobal("fetch", fetchMock);

    // 1. 크롤러(카카오톡 스크랩 봇) -> 200 동적 OG + XSS 이스케이프 + 프록시 이미지
    const botRes = await handleShareOgRequest({
      request: new Request("https://eolmande.pages.dev/s/26505", {
        headers: { "user-agent": "facebookexternalhit/1.1;kakaotalk-scrap/1.0" },
      }),
      params: { id: "26505" },
      env: {},
    });
    expect(botRes.status).toBe(200);
    const botHtml = await botRes.text();
    expect(botHtml).toContain("159,000원");
    expect(botHtml).toContain("&lt;b&gt;SSD&lt;/b&gt; &quot;2TB&quot;");
    expect(botHtml).not.toContain("<b>SSD</b>");
    expect(botHtml).toContain(
      "https://34.59.208.14.sslip.io/api/proxy-image?url=https%3A%2F%2Fcdn.example.com%2Fssd.jpg"
    );

    // 2. 일반 브라우저 -> 즉시 /?dealId=26505 302 리다이렉트
    const userRes = await handleShareOgRequest({
      request: new Request("https://eolmande.pages.dev/s/26505", {
        headers: { "user-agent": "Mozilla/5.0 (Linux; Android 14) KAKAOTALK 10.0" },
      }),
      params: { id: "26505" },
      env: {},
    });
    expect(userRes.status).toBe(302);
    expect(userRes.headers.get("Location")).toBe(
      "https://eolmande.pages.dev/?dealId=26505"
    );

    // 3. 비숫자 경로 -> 루트로 안전 리다이렉트
    const badRes = await handleShareOgRequest({
      request: new Request("https://eolmande.pages.dev/s/not-a-number", {
        headers: { "user-agent": "kakaotalk-scrap/1.0" },
      }),
      params: { id: "not-a-number" },
      env: {},
    });
    expect(badRes.status).toBe(302);
    expect(badRes.headers.get("Location")).toBe("https://eolmande.pages.dev/");
  });

  it("shareDealInToss는 토스 브릿지 환경에서 intoss:// 딥링크를 생성하고 Share.sendMessage를 호출한다", async () => {
    // 일반 웹에서는 unsupported 반환
    expect(
      await shareDealInToss({
        dealId: 26505,
        title: "테스트 상품",
        priceText: "14,100원",
      })
    ).toBe("unsupported");

    (window as unknown as { __appsInTossConstants?: Record<string, unknown> }).__appsInTossConstants = {
      appName: "insightdeal",
    };
    sdkMocks.createLink.mockResolvedValue("https://toss.im/_m/share123");
    sdkMocks.sendMessage.mockResolvedValue(undefined);
    sdkMocks.analyticsClick.mockResolvedValue(undefined);

    const outcome = await shareDealInToss({
      dealId: 26505,
      title: "푸마 런 벨로시티 쇼츠",
      priceText: "14,100원",
      shippingFee: "무료배송",
      shopName: "무신사",
      ogImageUrl: "https://34.59.208.14.sslip.io/api/proxy-image?url=https%3A%2F%2Fcdn.example.com%2Fa.jpg",
    });

    expect(outcome).toBe("shared");
    expect(sdkMocks.createLink).toHaveBeenCalledWith({
      path: "intoss://insightdeal?dealId=26505",
      ogImageUrl: "https://34.59.208.14.sslip.io/api/proxy-image?url=https%3A%2F%2Fcdn.example.com%2Fa.jpg",
    });
    expect(sdkMocks.sendMessage).toHaveBeenCalledWith({
      message: expect.stringContaining("https://toss.im/_m/share123"),
    });
  });

  it("TossAdBanner는 IntersectionObserver 환경에서 뷰포트 진입 전까지 광고를 요청하지 않고 진입 시 1회만 호출한다", async () => {
    (window as unknown as { __appsInToss?: unknown }).__appsInToss = {
      ads: {
        initialize: { isSupported: () => true },
        attachBanner: { isSupported: () => true },
      },
    };

    let ioCallback: IntersectionObserverCallback | null = null;
    const disconnectSpy = vi.fn();
    const observeSpy = vi.fn();

    class MockIntersectionObserver {
      constructor(cb: IntersectionObserverCallback) {
        ioCallback = cb;
      }
      observe = observeSpy;
      disconnect = disconnectSpy;
      unobserve = vi.fn();
      takeRecords = () => [];
      root = null;
      rootMargin = "";
      thresholds = [];
    }
    vi.stubGlobal("IntersectionObserver", MockIntersectionObserver);

    sdkMocks.initialize.mockImplementation(({ callbacks }) => {
      callbacks?.onInitialized?.();
    });
    sdkMocks.attachBanner.mockImplementation((_id, _el, opts) => {
      opts?.callbacks?.onAdRendered?.();
      return { destroy: sdkMocks.destroy };
    });

    render(<TossAdBanner />);
    expect(observeSpy).toHaveBeenCalledTimes(1);
    expect(sdkMocks.initialize).not.toHaveBeenCalled();
    expect(sdkMocks.attachBanner).not.toHaveBeenCalled();

    act(() => {
      ioCallback?.(
        [{ isIntersecting: true, intersectionRatio: 0.5 } as IntersectionObserverEntry],
        {} as IntersectionObserver
      );
    });

    await waitFor(() => {
      expect(sdkMocks.initialize).toHaveBeenCalledTimes(1);
      expect(sdkMocks.attachBanner).toHaveBeenCalledTimes(1);
    });
    expect(disconnectSpy).toHaveBeenCalled();
    expect(screen.getByTestId("toss-ad-banner")).toHaveAttribute(
      "data-slot-state",
      "rendered"
    );
  });

  it("PopularKeywordPresets는 원탭으로 인기 키워드를 구독·해제 토글한다", async () => {
    const onToggle = vi.fn().mockResolvedValue(true);
    render(
      <PopularKeywordPresets
        activeKeywords={["생수"]}
        onToggleKeyword={onToggle}
      />
    );

    expect(screen.getAllByRole("button")).toHaveLength(
      POPULAR_KEYWORD_PRESETS.length
    );

    const activeBtn = screen.getByRole("button", { name: /생수·음료 알림ON/ });
    const inactiveBtn = screen.getByRole("button", { name: "에어팟·애플" });
    expect(activeBtn).toHaveAttribute("aria-pressed", "true");
    expect(inactiveBtn).toHaveAttribute("aria-pressed", "false");

    await act(async () => {
      fireEvent.click(inactiveBtn);
    });
    expect(onToggle).toHaveBeenCalledWith("에어팟", true);

    await act(async () => {
      fireEvent.click(activeBtn);
    });
    expect(onToggle).toHaveBeenCalledWith("생수", false);
  });
});
