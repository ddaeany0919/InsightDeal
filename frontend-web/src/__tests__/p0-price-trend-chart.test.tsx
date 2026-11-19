import React from "react";
import { render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import PriceTrendChart from "@/app/components/PriceTrendChart";

describe("PriceTrendChart P0 검증 (dummyPrices 제거 & <2건 표준 안내)", () => {
  it("가격 이력이 0건이면 가짜 차트 없이 표준 안내 문구를 렌더링한다", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify([]), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      })
    );

    const { container } = render(<PriceTrendChart dealId={9101} currency="KRW" />);

    await waitFor(() => {
      expect(
        screen.getByText("아직 비교할 가격 이력이 충분하지 않아요")
      ).toBeInTheDocument();
    });
    expect(container.querySelector("polyline")).toBeNull();
  });

  it("가격 이력이 1건뿐이면 차트를 그리지 않고 표준 안내 문구를 렌더링한다", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify([
          { price: 15000, recordedAt: "2026-11-19T00:00:00Z" },
        ]),
        {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }
      )
    );

    const { container } = render(<PriceTrendChart dealId={9102} currency="KRW" />);

    await waitFor(() => {
      expect(
        screen.getByText("아직 비교할 가격 이력이 충분하지 않아요")
      ).toBeInTheDocument();
    });
    expect(container.querySelector("polyline")).toBeNull();
  });

  it("가격 이력이 2건 이상이면 실거래 SVG 차트와 요약 건수를 렌더링한다", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify([
          { price: 29000, recordedAt: "2026-11-10T01:00:00Z" },
          { price: 24000, recordedAt: "2026-11-19T01:00:00Z" },
        ]),
        {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }
      )
    );

    const { container } = render(<PriceTrendChart dealId={9103} currency="KRW" />);

    await waitFor(() => {
      expect(screen.getByText("실거래 2건")).toBeInTheDocument();
    });
    expect(container.querySelector("svg")).not.toBeNull();
    expect(container.querySelector("polyline")).not.toBeNull();
  });

  it("API 503 장애 시 에러 문구와 다시 시도 버튼을 렌더링한다", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(JSON.stringify({ detail: "Service Unavailable" }), {
        status: 503,
        headers: { "Content-Type": "application/json" },
      })
    );

    render(<PriceTrendChart dealId={9104} currency="KRW" />);

    await waitFor(() => {
      expect(
        screen.getByText("가격 추이를 불러오지 못했어요")
      ).toBeInTheDocument();
    });
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
    expect(
      screen.queryByText("아직 비교할 가격 이력이 충분하지 않아요")
    ).not.toBeInTheDocument();
  });
});
