import React from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import NotificationSettingsPage from "@/app/settings/notifications/page";

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

describe("3순위 키워드 알림 고도화 (제외어 · 최소/최대 가격 · 인기 딜만 받기 · 조건 칩)", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it("제외어·최소금액·상한금액·인기만 토글 입력 시 구조화 키워드 문자열을 저장하고 조건 칩으로 표시한다", async () => {
    localStorage.setItem("insightdeal_web_device_uuid", "dev_kw_filter_1");
    localStorage.setItem("insightdeal_web_device_token", "tok_kw_filter_1");

    const postedKeywords: string[] = [];

    vi.spyOn(globalThis, "fetch").mockImplementation(async (input, init) => {
      const url = typeof input === "string" ? input : input.toString();
      if (url.includes("/api/push/keywords") && (!init?.method || init.method === "GET")) {
        return new Response(
          JSON.stringify({
            keywords: ["아이폰 -케이스 >=700000"],
            detailed_keywords: [
              { id: 1, keyword: "아이폰 -케이스 >=700000", is_active: true },
            ],
            dnd_enabled: false,
            dnd_start_time: "21:00",
            dnd_end_time: "08:00",
          }),
          { status: 200 }
        );
      }
      if (url.includes("/api/push/keywords") && init?.method === "POST") {
        const body = JSON.parse(String(init.body || "{}"));
        postedKeywords.push(body.keyword);
        return new Response(
          JSON.stringify({ id: 2, keyword: body.keyword, is_active: true }),
          { status: 200 }
        );
      }
      return new Response(JSON.stringify({ ok: true }), { status: 200 });
    });

    render(<NotificationSettingsPage />);

    // 기존 등록 키워드 조건 칩 파싱 확인
    expect(await screen.findByText("아이폰")).toBeInTheDocument();
    expect(screen.getByText("제외: 케이스")).toBeInTheDocument();
    expect(screen.getByText("700,000원 이상")).toBeInTheDocument();

    // 새 조건 입력: 콜라 + 제외어 콜라겐 + 최소 10000 + 상한 20000 + 인기 딜만 받기 ON
    fireEvent.change(screen.getByLabelText("알림 키워드 입력"), {
      target: { value: "콜라" },
    });
    fireEvent.change(screen.getByLabelText("제외 키워드 입력"), {
      target: { value: "콜라겐" },
    });
    fireEvent.change(screen.getByLabelText("최소 가격 입력"), {
      target: { value: "10000" },
    });
    fireEvent.change(screen.getByLabelText("상한 가격 입력"), {
      target: { value: "20000" },
    });
    const hotToggle = screen.getByRole("button", { name: /인기 딜만 받기/ });
    fireEvent.click(hotToggle);
    expect(hotToggle).toHaveAttribute("aria-pressed", "true");

    fireEvent.click(screen.getByRole("button", { name: "추가" }));

    await waitFor(() => {
      expect(postedKeywords).toEqual(["콜라 -콜라겐 >=10000 :20000 +인기"]);
    });

    expect(await screen.findByText("제외: 콜라겐")).toBeInTheDocument();
    expect(screen.getByText("10,000원 이상")).toBeInTheDocument();
    expect(screen.getByText("20,000원 이하")).toBeInTheDocument();
    expect(screen.getByText("인기만")).toBeInTheDocument();
  });
});
