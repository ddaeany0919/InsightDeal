import React from "react";
import { describe, expect, it, vi, beforeEach, afterEach } from "vitest";
import { render, screen, act, cleanup, fireEvent } from "@testing-library/react";
import SwUpdateManager, { isSwReloadSafeIdle } from "../app/components/SwUpdateManager";
import DealCard from "../app/components/DealCard";
import type { Deal } from "../app/lib/types";

describe("SwUpdateManager & Multi-Source DealCard", () => {
  const originalServiceWorker = navigator.serviceWorker;
  const originalScrollY = window.scrollY;

  beforeEach(() => {
    vi.restoreAllMocks();
    Object.defineProperty(window, "scrollY", { value: 0, configurable: true, writable: true });
    Object.defineProperty(document, "visibilityState", {
      value: "visible",
      configurable: true,
    });
    delete (window as unknown as Record<string, unknown>).__appsInTossConstants;
    delete (window as unknown as Record<string, unknown>).__appsInToss;
  });

  afterEach(() => {
    cleanup();
    document.body.innerHTML = "";
    Object.defineProperty(navigator, "serviceWorker", {
      value: originalServiceWorker,
      configurable: true,
    });
    Object.defineProperty(window, "scrollY", {
      value: originalScrollY,
      configurable: true,
      writable: true,
    });
  });

  it("앱인토스 환경에서는 서비스 워커를 등록하지 않는다", () => {
    (window as unknown as Record<string, unknown>).__appsInTossConstants = {
      appName: "insightdeal",
    };
    const registerMock = vi.fn();
    Object.defineProperty(navigator, "serviceWorker", {
      value: { register: registerMock, addEventListener: vi.fn(), removeEventListener: vi.fn() },
      configurable: true,
    });

    const { container } = render(<SwUpdateManager />);
    expect(container.firstChild).toBeNull();
    expect(registerMock).not.toHaveBeenCalled();
  });

  it("유휴 상태(상단·모달/입력 비활성)에서 waiting SW 발견 시 SKIP_WAITING 자동 적용한다", async () => {
    const postMessageMock = vi.fn();
    const updateMock = vi.fn().mockResolvedValue(undefined);
    const waitingWorker = { postMessage: postMessageMock } as unknown as ServiceWorker;
    const registerMock = vi.fn().mockResolvedValue({
      waiting: waitingWorker,
      installing: null,
      update: updateMock,
      addEventListener: vi.fn(),
    });

    Object.defineProperty(navigator, "serviceWorker", {
      value: {
        controller: {} as ServiceWorker,
        register: registerMock,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      },
      configurable: true,
    });

    await act(async () => {
      render(<SwUpdateManager />);
    });

    expect(registerMock).toHaveBeenCalledWith("/pwa-sw.js", {
      scope: "/",
      updateViaCache: "none",
    });
    expect(postMessageMock).toHaveBeenCalledWith({ type: "SKIP_WAITING" });
  });

  it("깊은 스크롤(scrollY > 120) 또는 모달 열람 중에는 자동 리로드 대신 비파괴 업데이트 토스트 배너를 띄운다", async () => {
    Object.defineProperty(window, "scrollY", { value: 450, configurable: true, writable: true });
    expect(isSwReloadSafeIdle()).toBe(false);

    const postMessageMock = vi.fn();
    const waitingWorker = { postMessage: postMessageMock } as unknown as ServiceWorker;
    const registerMock = vi.fn().mockResolvedValue({
      waiting: waitingWorker,
      installing: null,
      update: vi.fn().mockResolvedValue(undefined),
      addEventListener: vi.fn(),
    });

    Object.defineProperty(navigator, "serviceWorker", {
      value: {
        controller: {} as ServiceWorker,
        register: registerMock,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      },
      configurable: true,
    });

    await act(async () => {
      render(<SwUpdateManager />);
    });

    expect(postMessageMock).not.toHaveBeenCalled();
    const banner = await screen.findByRole("status");
    expect(banner).toHaveTextContent("새 버전이 준비됐어요");
    const refreshBtn = screen.getByRole("button", { name: /새로고침/ });
    fireEvent.click(refreshBtn);
    expect(postMessageMock).toHaveBeenCalledWith({ type: "SKIP_WAITING" });
  });

  it("DealCard는 교차 병합 출처가 2곳 이상일 때 대표 1개와 +N 배지를 함께 렌더링한다", () => {
    const deal: Deal = {
      id: 26130,
      title: "[네이버] 아트뮤 Qi2 25w 맥세이프 보조배터리",
      price: "41700",
      currency: "KRW",
      shipping_fee: "무료배송",
      shop_name: "네이버",
      community_name: "퀘이사존, 펨코, 루리웹",
      scraped_at: "2026-11-24T13:03:15Z",
      post_link: "https://quasarzone.com/bbs/qb_saleinfo/views/2004021",
      ecommerce_url: "https://brand.naver.com/artmu/products/13891267753",
      image_url: "https://d2w999htn2iva3.cloudfront.net/thumbs/3/2004021.jpg",
      category: "PC용품",
      isClosed: false,
      sources: [
        {
          site_name: "퀘이사존",
          post_url: "https://quasarzone.com/bbs/qb_saleinfo/views/2004021",
          ecommerce_url: "https://brand.naver.com/artmu/products/13891267753",
          price: 41700,
        },
        {
          site_name: "펨코",
          post_url: "https://www.fmkorea.com/10588889091",
          ecommerce_url: "https://brand.naver.com/artmu/products/13891267753",
          price: 41700,
        },
        {
          site_name: "루리웹",
          post_url: "https://bbs.ruliweb.com/market/board/1020/read/109341",
          ecommerce_url: "https://m.brand.naver.com/artmu/store-content/detail/10000011184",
          price: 41700,
        },
      ],
    };

    render(
      <DealCard
        deal={deal}
        appliedSearch=""
        isMounted={true}
        currentTime={Date.now()}
        isWishlisted={false}
        isAlertSet={false}
        viewMode="card"
        onSelectDeal={vi.fn()}
        onToggleAlert={vi.fn()}
        onToggleWishlist={vi.fn()}
        onShowToast={vi.fn()}
      />
    );

    expect(screen.getByText("+2")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "퀘이사존" })).toBeInTheDocument();
  });
});
