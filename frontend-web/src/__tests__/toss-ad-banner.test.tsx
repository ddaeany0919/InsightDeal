import React from "react";
import { act, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import TossAdBanner from "@/app/components/TossAdBanner";
import {
  TOSS_HOME_BANNER_AD_GROUP_ID,
  resetTossAdsInitState,
} from "@/app/lib/tossBridge";

const mocks = vi.hoisted(() => {
  return {
    initializeSupported: true,
    attachSupported: true,
    initialize: vi.fn(),
    attachBanner: vi.fn(),
    destroy: vi.fn(),
  };
});

vi.mock("@apps-in-toss/web-framework", () => ({
  TossAds: {
    initialize: Object.assign(mocks.initialize, {
      isSupported: () => mocks.initializeSupported,
    }),
    attachBanner: Object.assign(mocks.attachBanner, {
      isSupported: () => mocks.attachSupported,
    }),
    destroy: vi.fn(),
    destroyAll: vi.fn(),
  },
}));

function installTossHost(): void {
  Object.defineProperty(window, "__appsInToss", {
    configurable: true,
    writable: true,
    value: {
      ads: {
        initialize: { isSupported: () => mocks.initializeSupported },
        attachBanner: { isSupported: () => mocks.attachSupported },
      },
    },
  });
}

describe("토스 인앱 배너 광고(TossAdBanner)", () => {
  beforeEach(() => {
    resetTossAdsInitState();
    mocks.initializeSupported = true;
    mocks.attachSupported = true;
    mocks.initialize.mockReset();
    mocks.attachBanner.mockReset();
    mocks.destroy.mockReset();

    delete (window as unknown as { __appsInToss?: unknown }).__appsInToss;
    delete document.documentElement.dataset.theme;
  });

  afterEach(() => {
    resetTossAdsInitState();
    delete (window as unknown as { __appsInToss?: unknown }).__appsInToss;
    delete document.documentElement.dataset.theme;
  });

  it("일반 웹 환경(__appsInToss 미주입)에서는 DOM을 생성하지 않고 SDK를 호출하지 않는다", () => {
    const { container } = render(<TossAdBanner />);

    expect(container).toBeEmptyDOMElement();
    expect(screen.queryByTestId("toss-ad-banner")).not.toBeInTheDocument();
    expect(mocks.initialize).not.toHaveBeenCalled();
    expect(mocks.attachBanner).not.toHaveBeenCalled();
  });

  it("토스 호스트 환경에서 싱글톤 초기화 후 배너를 부착하고, 렌더링 완료·언마운트 정리까지 정상 동작한다", async () => {
    installTossHost();

    let latestCallbacks: {
      onAdRendered?: (payload: unknown) => void;
      onNoFill?: (payload: unknown) => void;
      onAdFailedToRender?: (payload: unknown) => void;
    } = {};

    mocks.initialize.mockImplementation(({ callbacks }) => {
      callbacks?.onInitialized?.();
    });
    mocks.attachBanner.mockImplementation((_adGroupId, _target, options) => {
      latestCallbacks = options?.callbacks ?? {};
      return { destroy: mocks.destroy };
    });

    const { unmount } = render(<TossAdBanner />);

    await waitFor(() => {
      expect(mocks.initialize).toHaveBeenCalledTimes(1);
      expect(mocks.attachBanner).toHaveBeenCalledTimes(1);
    });

    const [attachedGroupId, attachedEl, attachedOptions] =
      mocks.attachBanner.mock.calls[0];
    expect(attachedGroupId).toBe(TOSS_HOME_BANNER_AD_GROUP_ID);
    expect(attachedGroupId).toBe("ait.v2.live.b3a879ee2cbd4df8");
    expect(attachedEl).toBeInstanceOf(HTMLElement);
    expect(attachedOptions).toMatchObject({
      theme: "auto",
      tone: "blackAndWhite",
      variant: "card",
    });

    const slot = screen.getByTestId("toss-ad-banner");
    expect(slot).toHaveAttribute("data-slot-state", "loading");
    expect(slot).toHaveAttribute("aria-hidden", "true");

    act(() => {
      latestCallbacks.onAdRendered?.({
        slotId: "slot-1",
        adGroupId: attachedGroupId,
        adMetadata: { creativeId: "c-1", requestId: "r-1" },
      });
    });

    expect(slot).toHaveAttribute("data-slot-state", "rendered");
    expect(slot).toHaveAttribute("aria-hidden", "false");
    expect(mocks.attachBanner).toHaveBeenCalledTimes(1);
    expect(mocks.destroy).not.toHaveBeenCalled();

    unmount();
    expect(mocks.destroy).toHaveBeenCalledTimes(1);
  });

  it("No-Fill 또는 렌더 오류 시 배너 DOM을 제거하여 빈 여백을 남기지 않는다", async () => {
    installTossHost();

    let latestCallbacks: {
      onNoFill?: (payload: unknown) => void;
      onAdFailedToRender?: (payload: unknown) => void;
    } = {};

    mocks.initialize.mockImplementation(({ callbacks }) => {
      callbacks?.onInitialized?.();
    });
    mocks.attachBanner.mockImplementation((_adGroupId, _target, options) => {
      latestCallbacks = options?.callbacks ?? {};
      return { destroy: mocks.destroy };
    });

    const first = render(<TossAdBanner />);
    await waitFor(() => {
      expect(mocks.attachBanner).toHaveBeenCalledTimes(1);
    });

    act(() => {
      latestCallbacks.onNoFill?.({
        slotId: "slot-1",
        adGroupId: TOSS_HOME_BANNER_AD_GROUP_ID,
        adMetadata: {},
      });
    });

    await waitFor(() => {
      expect(screen.queryByTestId("toss-ad-banner")).not.toBeInTheDocument();
      expect(mocks.destroy).toHaveBeenCalledTimes(1);
    });
    first.unmount();

    const second = render(<TossAdBanner />);
    await waitFor(() => {
      expect(mocks.attachBanner).toHaveBeenCalledTimes(2);
    });
    // 이미 초기화된 싱글톤을 재사용하므로 initialize는 1회만 유지
    expect(mocks.initialize).toHaveBeenCalledTimes(1);

    act(() => {
      latestCallbacks.onAdFailedToRender?.({
        slotId: "slot-2",
        adGroupId: TOSS_HOME_BANNER_AD_GROUP_ID,
        adMetadata: {},
        error: { code: 500, message: "render failed" },
      });
    });

    await waitFor(() => {
      expect(screen.queryByTestId("toss-ad-banner")).not.toBeInTheDocument();
    });
    second.unmount();
  });

  it("초기화 실패 시 캐시를 비우고 다음 마운트에서 재시도하여 배너를 복구한다", async () => {
    installTossHost();

    mocks.initialize
      .mockImplementationOnce(({ callbacks }) => {
        callbacks?.onInitializationFailed?.(new Error("temporary failure"));
      })
      .mockImplementationOnce(({ callbacks }) => {
        callbacks?.onInitialized?.();
      });
    mocks.attachBanner.mockImplementation(() => ({ destroy: mocks.destroy }));

    const first = render(<TossAdBanner />);
    await waitFor(() => {
      expect(mocks.initialize).toHaveBeenCalledTimes(1);
      expect(screen.queryByTestId("toss-ad-banner")).not.toBeInTheDocument();
    });
    expect(mocks.attachBanner).not.toHaveBeenCalled();
    first.unmount();

    render(<TossAdBanner />);
    await waitFor(() => {
      expect(mocks.initialize).toHaveBeenCalledTimes(2);
      expect(mocks.attachBanner).toHaveBeenCalledTimes(1);
    });
  });

  it("앱 테마 변경 이벤트(amoled → dark, light → light)를 배너 옵션에 실시간 반영한다", async () => {
    installTossHost();
    document.documentElement.dataset.theme = "amoled";

    mocks.initialize.mockImplementation(({ callbacks }) => {
      callbacks?.onInitialized?.();
    });
    mocks.attachBanner.mockImplementation((_id, _el, options) => {
      options?.callbacks?.onAdRendered?.({
        slotId: "slot-theme",
        adGroupId: TOSS_HOME_BANNER_AD_GROUP_ID,
        adMetadata: { creativeId: "c", requestId: "r" },
      });
      return { destroy: mocks.destroy };
    });

    render(<TossAdBanner />);

    await waitFor(() => {
      expect(mocks.attachBanner).toHaveBeenCalledTimes(1);
    });
    expect(mocks.attachBanner.mock.calls[0][2]).toMatchObject({
      theme: "dark",
    });

    act(() => {
      document.documentElement.dataset.theme = "light";
      window.dispatchEvent(new Event("insightdeal-theme-change"));
    });

    await waitFor(() => {
      expect(mocks.destroy).toHaveBeenCalledTimes(1);
      expect(mocks.attachBanner).toHaveBeenCalledTimes(2);
    });
    expect(mocks.attachBanner.mock.calls[1][2]).toMatchObject({
      theme: "light",
    });
  });
});
