import React from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import DealDetailModal from "@/app/components/DealDetailModal";
import DeviceSyncModal from "@/app/components/DeviceSyncModal";
import NotificationDrawer from "@/app/components/NotificationDrawer";
import type { Deal } from "@/app/lib/types";

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

const BASE_DEAL: Deal = {
  id: 701,
  community_name: "뽐뿌",
  title: "[쿠팡] P1 모달 테스트 상품",
  price: "29900",
  image_url: "https://cdn.example.com/main-701.jpg",
  scraped_at: "방금 전",
  post_link: "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=701",
  ecommerce_url: "https://www.coupang.com/vp/products/701",
  currency: "KRW",
  category: "생활용품",
  isClosed: false,
};

describe("P1 상세모달·라이트박스·품절제보·다기기동기화·알림함 검증", () => {
  it("body_images 갤러리 클릭 시 ImageLightboxModal이 열리고 줌/화살표 전환 및 Escape 중첩 격리가 동작한다", async () => {
    localStorage.setItem("insightdeal_web_device_uuid", "web_lb_1");
    localStorage.setItem("insightdeal_web_device_token", "tok_lb_1");

    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify({
          id: 701,
          title: "[쿠팡] P1 모달 테스트 상품",
          price: 29900,
          currency: "KRW",
          site_name: "뽐뿌",
          image_url: "https://cdn.example.com/main-701.jpg",
          body_images: [
            "https://cdn.example.com/body-1.jpg",
            "https://cdn.example.com/body-2.jpg",
          ],
          content_html: "<p>본문입니다</p>",
          is_closed: false,
          report_count: 1,
        }),
        { status: 200 }
      )
    );

    const onCloseDetail = vi.fn();
    render(
      <DealDetailModal deal={BASE_DEAL} onClose={onCloseDetail} showToast={vi.fn()} />
    );

    await waitFor(() => {
      expect(screen.getByText("상품 이미지 (3장)")).toBeInTheDocument();
    });

    // 대표 이미지 확대 버튼 클릭 -> 라이트박스 오픈 (총 3장: 대표 + 본문 2장)
    const zoomTrigger = screen.getByRole("button", { name: "상품 이미지 확대 보기" });
    fireEvent.click(zoomTrigger);

    await waitFor(() => {
      expect(screen.getByText("1 / 3")).toBeInTheDocument();
    });

    const zoomInBtn = screen.getByRole("button", { name: "이미지 확대" });
    fireEvent.click(zoomInBtn);
    expect(screen.getByText("150%")).toBeInTheDocument();

    // 다음 이미지 화살표 이동 시 2 / 3 및 배율 100% 리셋
    fireEvent.click(screen.getByRole("button", { name: "다음 이미지" }));
    expect(screen.getByText("2 / 3")).toBeInTheDocument();
    expect(screen.getByText("100%")).toBeInTheDocument();

    // Escape 키 입력 시 라이트박스만 닫히고 DealDetailModal onClose는 호출되지 않아야 함
    fireEvent.keyDown(window, { key: "Escape" });
    await waitFor(() => {
      expect(screen.queryByText("2 / 3")).toBeNull();
    });
    expect(onCloseDetail).not.toHaveBeenCalled();
    expect(screen.getByText("P1 모달 테스트 상품")).toBeInTheDocument();
  });

  it("원클릭 품절 제보 API 호출(200 임계치 도달 / 409 중복) 및 부모 상태 동기화를 검증한다", async () => {
    localStorage.setItem("insightdeal_web_device_uuid", "web_rep_1");
    localStorage.setItem("insightdeal_web_device_token", "tok_rep_1");

    const reportHeaders: Headers[] = [];
    let reportAttempt = 0;

    vi.spyOn(globalThis, "fetch").mockImplementation(async (input, init) => {
      const url = typeof input === "string" ? input : input instanceof URL ? input.toString() : input.url;
      if (url.includes("/report-closed")) {
        reportAttempt += 1;
        reportHeaders.push(new Headers(init?.headers));
        if (reportAttempt === 1) {
          return new Response(
            JSON.stringify({
              status: "ok",
              deal_id: 701,
              report_count: 3,
              is_closed: true,
              threshold: 3,
            }),
            { status: 200 }
          );
        }
        return new Response(JSON.stringify({ detail: "Already reported" }), { status: 409 });
      }
      return new Response(
        JSON.stringify({
          id: 701,
          title: "[쿠팡] P1 모달 테스트 상품",
          price: 29900,
          currency: "KRW",
          site_name: "뽐뿌",
          content_html: "<p>본문</p>",
          is_closed: false,
          report_count: 2,
        }),
        { status: 200 }
      );
    });

    const onDealClosed = vi.fn();
    const showToast = vi.fn();
    render(
      <DealDetailModal
        deal={BASE_DEAL}
        onClose={vi.fn()}
        showToast={showToast}
        onDealClosed={onDealClosed}
      />
    );

    const reportBtn = await screen.findByRole("button", { name: /품절 제보 \(2\)/ });
    fireEvent.click(reportBtn);

    await waitFor(() => {
      expect(onDealClosed).toHaveBeenCalledWith(701, true, 3);
    });
    expect(reportHeaders[0].get("X-Device-Token")).toBe("tok_rep_1");
    expect(screen.getByText("품절/종료")).toBeInTheDocument();
    expect(showToast).toHaveBeenCalledWith(
      expect.stringContaining("종료 처리되었어요")
    );
  });

  it("DeviceSyncModal에서 6자리 동기화 코드 발급/입력 병합 및 오류 상태를 처리한다", async () => {
    localStorage.setItem("insightdeal_web_device_uuid", "web_sync_1");
    localStorage.setItem("insightdeal_web_device_token", "tok_sync_1");

    let claimMode: "ok" | "404" | "409" = "ok";
    vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
      const url = typeof input === "string" ? input : input instanceof URL ? input.toString() : input.url;
      if (url.includes("/api/auth/pairing-code")) {
        return new Response(
          JSON.stringify({ code: "482910", expires_in_sec: 600 }),
          { status: 200 }
        );
      }
      if (url.includes("/api/auth/claim-pairing-code")) {
        if (claimMode === "404") {
          return new Response(JSON.stringify({ detail: "Not found" }), { status: 404 });
        }
        if (claimMode === "409") {
          return new Response(JSON.stringify({ detail: "Already used" }), { status: 409 });
        }
        return new Response(
          JSON.stringify({
            status: "ok",
            merged_wishlists: 2,
            merged_keywords: 3,
            merged_alerts: 1,
          }),
          { status: 200 }
        );
      }
      return new Response(JSON.stringify({ ok: true }), { status: 200 });
    });

    const onSyncComplete = vi.fn();
    render(
      <DeviceSyncModal
        isOpen={true}
        onClose={vi.fn()}
        onSyncComplete={onSyncComplete}
        onShowToast={vi.fn()}
      />
    );

    fireEvent.click(screen.getByRole("button", { name: "6자리 동기화 코드 발급하기" }));
    expect(await screen.findByText("482910")).toBeInTheDocument();

    const codeInput = screen.getByLabelText("6자리 동기화 코드 입력");
    const submitBtn = screen.getByRole("button", { name: "내 기기와 합치기" });

    claimMode = "404";
    fireEvent.change(codeInput, { target: { value: "999999" } });
    fireEvent.click(submitBtn);
    expect(
      await screen.findByText("존재하지 않거나 이미 만료된 6자리 코드예요.")
    ).toBeInTheDocument();

    claimMode = "ok";
    fireEvent.change(codeInput, { target: { value: "123456" } });
    fireEvent.click(submitBtn);
    await waitFor(() => {
      expect(onSyncComplete).toHaveBeenCalledTimes(1);
    });
    expect(
      screen.getByText(/찜 2건 · 키워드 3건 · 알림 1건/)
    ).toBeInTheDocument();
  });

  it("NotificationDrawer는 피드에 없는 타 기기 동기화 목표가 알림도 서버에서 조회해 표시한다", async () => {
    localStorage.setItem("insightdeal_web_device_uuid", "web_notif_1");
    localStorage.setItem("insightdeal_web_device_token", "tok_notif_1");

    vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
      const url = typeof input === "string" ? input : input instanceof URL ? input.toString() : input.url;
      if (url.includes("/api/users/notifications")) {
        return new Response(JSON.stringify([]), { status: 200 });
      }
      if (url.includes("/api/wishlist/")) {
        return new Response(
          JSON.stringify([
            {
              id: 55,
              keyword: "타기기 목표가 상품",
              target_price: 25000,
              user_id: "web_notif_1",
              alert_enabled: true,
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

    expect(await screen.findByText("타기기 목표가 상품")).toBeInTheDocument();
    expect(screen.getByText("25,000원 이하")).toBeInTheDocument();
  });
});
