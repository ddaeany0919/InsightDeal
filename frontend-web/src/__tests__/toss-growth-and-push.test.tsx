import React from "react";
import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import NotificationDrawer from "@/app/components/NotificationDrawer";
import NotificationSettingsPage from "@/app/settings/notifications/page";
import DealCard from "@/app/components/DealCard";
import DealDetailModal from "@/app/components/DealDetailModal";
import type { Deal } from "@/app/lib/types";
import {
  TOSS_DETAIL_BANNER_AD_GROUP_ID,
  TOSS_INTERSTITIAL_AD_GROUP_ID,
  TOSS_PUSH_TEMPLATE_CODE,
  getTossAnonymousKey,
  maybeShowTossInterstitialOnDetailClose,
  recordDealDetailViewAndPreload,
  requestTossPushAgreement,
  resetTossAdsInitState,
  resetTossInterstitialState,
  resetTossPushHelpersForTest,
  syncTossAnonymousKeyToBackend,
} from "@/app/lib/tossBridge";

const sdkMocks = vi.hoisted(() => ({
  loadSupported: true,
  showSupported: true,
  anonSupported: true,
  agreementSupported: true,
  loadFullScreenAd: vi.fn(),
  showFullScreenAd: vi.fn(),
  getAnonymousKey: vi.fn(),
  requestAgreement: vi.fn(),
  analyticsClick: vi.fn(() => Promise.resolve()),
  analyticsScreen: vi.fn(() => Promise.resolve()),
  ensureWebDeviceAuth: vi.fn(
    (async () => ({
      deviceUuid: "test-device-uuid-123",
      deviceToken: "v1.test-token",
    })) as (options?: { forceRefresh?: boolean }) => Promise<{
      deviceUuid: string;
      deviceToken: string;
    }>
  ),
  authFetch: vi.fn(
    (async () => ({
      ok: true,
      json: async () => ({ keywords: [], detailed_keywords: [] }),
    })) as (
      input?: unknown,
      init?: RequestInit
    ) => Promise<{ ok: boolean; json: () => Promise<Record<string, unknown>> }>
  ),
}));

vi.mock("@apps-in-toss/web-framework", () => ({
  loadFullScreenAd: Object.assign(sdkMocks.loadFullScreenAd, {
    isSupported: () => sdkMocks.loadSupported,
  }),
  showFullScreenAd: Object.assign(sdkMocks.showFullScreenAd, {
    isSupported: () => sdkMocks.showSupported,
  }),
  User: {
    getAnonymousKey: Object.assign(sdkMocks.getAnonymousKey, {
      isSupported: () => sdkMocks.anonSupported,
    }),
  },
  Notification: {
    requestAgreement: Object.assign(sdkMocks.requestAgreement, {
      isSupported: () => sdkMocks.agreementSupported,
    }),
  },
  Analytics: {
    click: sdkMocks.analyticsClick,
    screen: sdkMocks.analyticsScreen,
  },
  TossAds: {
    initialize: Object.assign(vi.fn(), { isSupported: () => true }),
    attachBanner: Object.assign(vi.fn(() => ({ destroy: vi.fn() })), {
      isSupported: () => true,
    }),
  },
}));

vi.mock("next/link", () => ({
  default: ({
    href,
    children,
    ...rest
  }: {
    href: string;
    children: React.ReactNode;
  }) => (
    <a href={href} {...rest}>
      {children}
    </a>
  ),
}));

vi.mock("@/app/lib/utils", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/app/lib/utils")>();
  return {
    ...actual,
    ensureWebDeviceAuth: ((...args: Parameters<typeof actual.ensureWebDeviceAuth>) =>
      sdkMocks.ensureWebDeviceAuth(...args)) as typeof actual.ensureWebDeviceAuth,
    authFetch: ((...args: Parameters<typeof actual.authFetch>) =>
      sdkMocks.authFetch(...args)) as typeof actual.authFetch,
  };
});

function installTossBridgeAndAdsHost(): void {
  Object.defineProperty(window, "__appsInTossConstants", {
    configurable: true,
    writable: true,
    value: { appName: "insightdeal" },
  });
  Object.defineProperty(window, "__appsInToss", {
    configurable: true,
    writable: true,
    value: {
      ads: {
        initialize: { isSupported: () => true },
        attachBanner: { isSupported: () => true },
      },
    },
  });
}

function clearTossHost(): void {
  delete (window as unknown as { __appsInTossConstants?: unknown }).__appsInTossConstants;
  delete (window as unknown as { __appsInToss?: unknown }).__appsInToss;
}

describe("토스 수익화 확장 · 소프트 전면형 · 무료 푸시(anonKey) · 웹 텔레그램 CTA", () => {
  beforeEach(() => {
    clearTossHost();
    localStorage.clear();
    sessionStorage.clear();
    resetTossAdsInitState();
    resetTossInterstitialState();
    resetTossPushHelpersForTest();

    sdkMocks.loadSupported = true;
    sdkMocks.showSupported = true;
    sdkMocks.anonSupported = true;
    sdkMocks.agreementSupported = true;
    sdkMocks.loadFullScreenAd.mockReset();
    sdkMocks.showFullScreenAd.mockReset();
    sdkMocks.getAnonymousKey.mockReset();
    sdkMocks.requestAgreement.mockReset();
    sdkMocks.analyticsClick.mockClear();
    sdkMocks.analyticsScreen.mockClear();
    sdkMocks.ensureWebDeviceAuth.mockClear();
    sdkMocks.authFetch.mockReset();
    sdkMocks.authFetch.mockResolvedValue({
      ok: true,
      json: async () => ({ keywords: [], detailed_keywords: [] }),
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    clearTossHost();
    resetTossAdsInitState();
    resetTossInterstitialState();
    resetTossPushHelpersForTest();
  });

  it("콘솔 신규 광고 그룹 ID 및 푸시 템플릿 코드 상수가 정확히 매핑된다", () => {
    expect(TOSS_DETAIL_BANNER_AD_GROUP_ID).toBe("ait.v2.live.3599842d107c48bd");
    expect(TOSS_INTERSTITIAL_AD_GROUP_ID).toBe("ait.v2.live.ac45d5b23fb94fc7");
    expect(TOSS_PUSH_TEMPLATE_CODE).toBe("insightdeal-keyword-hotdeal");
  });

  it("소프트 전면형 광고는 일반 웹에서 동작하지 않으며, 토스에서는 3회 탐색 후·10분 쿨다운·일 2회 상한을 지킨다", async () => {
    expect(maybeShowTossInterstitialOnDetailClose()).toBe(false);
    expect(sdkMocks.loadFullScreenAd).not.toHaveBeenCalled();

    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-11-26T03:00:00Z")); // 12:00 KST
    installTossBridgeAndAdsHost();

    const loadCleanup = vi.fn();
    const showCleanup = vi.fn();
    sdkMocks.loadFullScreenAd.mockImplementation(({ onEvent }) => {
      Promise.resolve().then(() => onEvent({ type: "loaded" }));
      return loadCleanup;
    });
    sdkMocks.showFullScreenAd.mockImplementation(({ onEvent }) => {
      Promise.resolve().then(() => {
        onEvent({ type: "show" });
        onEvent({ type: "dismissed" });
      });
      return showCleanup;
    });

    // 1번째 진입: 아직 프리로드도 안 함
    recordDealDetailViewAndPreload();
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });
    expect(sdkMocks.loadFullScreenAd).not.toHaveBeenCalled();
    expect(maybeShowTossInterstitialOnDetailClose()).toBe(false);

    // 2번째 진입: 프리로드만 수행 (노출 0회)
    recordDealDetailViewAndPreload();
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
      await Promise.resolve();
    });
    expect(sdkMocks.loadFullScreenAd).toHaveBeenCalledTimes(1);
    expect(sdkMocks.loadFullScreenAd.mock.calls[0][0].options.adGroupId).toBe(
      TOSS_INTERSTITIAL_AD_GROUP_ID
    );
    expect(maybeShowTossInterstitialOnDetailClose()).toBe(false);

    // 3번째 진입 후 닫기: 1차 노출 성공
    recordDealDetailViewAndPreload();
    expect(maybeShowTossInterstitialOnDetailClose()).toBe(true);
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });
    expect(sdkMocks.showFullScreenAd).toHaveBeenCalledTimes(1);
    expect(showCleanup).toHaveBeenCalledTimes(1);

    // 10분 쿨다운 이내 재시도 차단
    vi.setSystemTime(new Date("2026-11-26T03:05:00Z"));
    recordDealDetailViewAndPreload();
    expect(maybeShowTossInterstitialOnDetailClose()).toBe(false);
    expect(sdkMocks.loadFullScreenAd).toHaveBeenCalledTimes(1);

    // 11분 경과 후 4번째(프리로드) -> 5번째 닫기: 2차 노출 성공
    vi.setSystemTime(new Date("2026-11-26T03:11:00Z"));
    recordDealDetailViewAndPreload();
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
      await Promise.resolve();
    });
    expect(sdkMocks.loadFullScreenAd).toHaveBeenCalledTimes(2);
    expect(maybeShowTossInterstitialOnDetailClose()).toBe(true);
    await act(async () => {
      await Promise.resolve();
      await Promise.resolve();
    });
    expect(sdkMocks.showFullScreenAd).toHaveBeenCalledTimes(2);

    // 1시간 후 일일 상한(2회) 도달로 3차 노출 차단
    vi.setSystemTime(new Date("2026-11-26T04:30:00Z"));
    recordDealDetailViewAndPreload();
    expect(maybeShowTossInterstitialOnDetailClose()).toBe(false);
    expect(sdkMocks.loadFullScreenAd).toHaveBeenCalledTimes(2);
    expect(sdkMocks.showFullScreenAd).toHaveBeenCalledTimes(2);
  });

  it("User.getAnonymousKey · Notification.requestAgreement · 백엔드 동기화가 정상 동작한다", async () => {
    installTossBridgeAndAdsHost();
    sdkMocks.getAnonymousKey.mockResolvedValue({
      type: "HASH",
      hash: "toss-anon-hash-999",
    });
    sdkMocks.requestAgreement.mockImplementation(({ onEvent }) => {
      onEvent({ type: "newAgreement" });
      return vi.fn();
    });
    sdkMocks.authFetch.mockResolvedValue({
      ok: true,
      json: async () => ({ status: "ok" }),
    });

    const agreementOutcome = await requestTossPushAgreement();
    expect(agreementOutcome).toBe("agreed");
    expect(sdkMocks.requestAgreement).toHaveBeenCalledTimes(1);
    expect(sdkMocks.requestAgreement.mock.calls[0][0].options.templateCode).toBe(
      "insightdeal-keyword-hotdeal"
    );

    // 캐시된 동의 결과는 재호출 시 시트를 다시 띄우지 않음
    expect(await requestTossPushAgreement()).toBe("agreed");
    expect(sdkMocks.requestAgreement).toHaveBeenCalledTimes(1);

    expect(await getTossAnonymousKey()).toBe("toss-anon-hash-999");
    expect(await syncTossAnonymousKeyToBackend()).toBe(true);
    expect(sdkMocks.authFetch).toHaveBeenCalledTimes(1);

    const builder = sdkMocks.authFetch.mock.calls[0][0] as (auth: {
      deviceUuid: string;
      deviceToken: string;
    }) => { pathOrUrl: string; init?: { body?: string } };
    const built = builder({ deviceUuid: "dev-uuid-1", deviceToken: "tok" });
    expect(built.pathOrUrl).toBe("/api/push/device");
    expect(JSON.parse(built.init?.body || "{}")).toEqual({
      device_uuid: "dev-uuid-1",
      toss_anon_key: "toss-anon-hash-999",
    });

    // 동일 세션 내 중복 동기화 방지
    expect(await syncTossAnonymousKeyToBackend()).toBe(true);
    expect(sdkMocks.authFetch).toHaveBeenCalledTimes(1);
  });

  it("텔레그램 채널 CTA는 일반 웹에서만 노출되고 토스 미니앱에서는 완전히 숨겨진다", async () => {
    clearTossHost();
    const drawerWeb = render(
      <NotificationDrawer
        isOpen={true}
        onClose={vi.fn()}
        deals={[]}
        onSelectDeal={vi.fn()}
      />
    );
    const webCta = await screen.findByTestId("telegram-channel-cta");
    expect(webCta).toHaveAttribute("href", "https://t.me/eolmande_hotdeal");
    drawerWeb.unmount();

    const settingsWeb = render(<NotificationSettingsPage />);
    const settingsCta = await screen.findByTestId("telegram-channel-cta");
    expect(settingsCta.querySelector("a")).toHaveAttribute(
      "href",
      "https://t.me/eolmande_hotdeal"
    );
    settingsWeb.unmount();

    // 토스 미니앱 호스트 환경에서는 외부 메신저(텔레그램) CTA를 철저히 숨겨 검수 정책 준수
    installTossBridgeAndAdsHost();
    const drawerToss = render(
      <NotificationDrawer
        isOpen={true}
        onClose={vi.fn()}
        deals={[]}
        onSelectDeal={vi.fn()}
      />
    );
    await waitFor(() => {
      expect(screen.queryByTestId("telegram-channel-cta")).toBeNull();
    });
    drawerToss.unmount();

    const settingsToss = render(<NotificationSettingsPage />);
    await waitFor(() => {
      expect(screen.queryByTestId("telegram-channel-cta")).toBeNull();
      expect(screen.getByText("토스 푸시 알림 동의하기")).toBeInTheDocument();
    });
    settingsToss.unmount();
  });

  it("동의 거부(rejected) 시 백엔드 토스 키 동기화 및 연동 완료 오표시를 하지 않는다", async () => {
    installTossBridgeAndAdsHost();
    sdkMocks.getAnonymousKey
      .mockResolvedValueOnce(null)
      .mockResolvedValue({
        type: "HASH",
        hash: "toss-anon-hash-reject",
      });
    sdkMocks.requestAgreement.mockImplementation(({ onEvent }) => {
      onEvent({ type: "agreementRejected" });
      return vi.fn();
    });

    render(<NotificationSettingsPage />);
    const btn = await screen.findByText("토스 푸시 알림 동의하기");
    await waitFor(() => {
      expect(sdkMocks.getAnonymousKey).toHaveBeenCalledTimes(1);
    });
    sdkMocks.authFetch.mockClear();
    fireEvent.click(btn);

    await waitFor(() => {
      expect(screen.getByText(/동의가 거부되었어요/)).toBeInTheDocument();
    });
    expect(sdkMocks.authFetch).not.toHaveBeenCalled();
    expect(screen.queryByText("토스 푸시 연동됨")).toBeNull();
  });

  it("유료 딜의 0원 옵션은 무료로 오표시되지 않으며 빈 옵션 응답은 허위 배지를 노출하지 않는다", async () => {
    clearTossHost();
    const baseDeal: Deal = {
      id: 77701,
      title: "[지마켓] 9800X3D RX9070XT 완본체",
      price: "3349900",
      shipping_fee: "무료배송",
      currency: "KRW",
      shop_name: "지마켓",
      community_name: "퀘이사존",
      scraped_at: new Date().toISOString(),
      post_link: "https://quasarzone.com/bbs/qb_saleinfo/views/77701",
      ecommerce_url: "https://item.gmarket.co.kr/Item?goodscode=77701",
      image_url: null,
      category: "PC/하드웨어",
      isClosed: false,
      has_options: true,
      options_data: [],
      sources: [
        {
          site_name: "옵션 A 가격미상",
          price: 0,
          currency: "KRW",
          post_url: "https://quasarzone.com/bbs/qb_saleinfo/views/77701",
          ecommerce_url: "https://item.gmarket.co.kr/Item?goodscode=77701",
        },
        {
          site_name: "옵션 B 본품",
          price: 3349900,
          currency: "KRW",
          post_url: "https://quasarzone.com/bbs/qb_saleinfo/views/77701",
          ecommerce_url: "https://item.gmarket.co.kr/Item?goodscode=77701",
        },
      ],
      isFree: false,
    };

    const cardView = render(
      <DealCard
        deal={baseDeal}
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
    expect(screen.queryByText("2종 옵션")).toBeNull();
    cardView.unmount();

    const fetchMock = vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        ...baseDeal,
        options_data: [],
        sources: baseDeal.sources,
      }),
    });
    vi.stubGlobal("fetch", fetchMock);

    const modalView = render(
      <DealDetailModal
        deal={{
          ...baseDeal,
          options_data: [
            {
              name: "오염 옵션",
              price: 100000,
              currency: "KRW",
              url: "https://item.gmarket.co.kr/Item?goodscode=77701",
            },
          ],
        }}
        onClose={vi.fn()}
        showToast={vi.fn()}
      />
    );

    await waitFor(() => {
      expect(fetchMock).toHaveBeenCalled();
    });
    expect(screen.queryByText(/0원 \(무료\)/)).toBeNull();
    expect(screen.queryByText(/오염 옵션/)).toBeNull();
    modalView.unmount();
    vi.unstubAllGlobals();
  });
});
