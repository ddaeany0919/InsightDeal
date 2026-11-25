import React from "react";
import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import BrandImagePlaceholder, {
  resolveBrandVisual,
} from "../app/components/BrandImagePlaceholder";

describe("BrandImagePlaceholder 브랜드 컬러·모노그램 플레이스홀더", () => {
  it("주요 쇼핑몰명에서 브랜드 비주얼(이름/모노그램/컬러)을 정확히 해석한다", () => {
    expect(resolveBrandVisual("네이버")?.mark).toBe("N");
    expect(resolveBrandVisual("스마트스토어")?.name).toBe("네이버");
    expect(resolveBrandVisual("쿠팡")?.mark).toBe("C");
    expect(resolveBrandVisual("11번가")?.mark).toBe("11");
    expect(resolveBrandVisual("무신사")?.mark).toBe("M");
    expect(resolveBrandVisual("29CM")?.name).toBe("무신사");
    expect(resolveBrandVisual("알수없는몰")).toBeNull();
  });

  it("일반 모드에서 쇼핑몰별 브랜드명·모노그램과 '이미지 없음' 라벨을 함께 렌더링한다", () => {
    const { container, rerender } = render(
      <BrandImagePlaceholder shopName="네이버" category="식품" />
    );
    const rootNaver = container.firstElementChild as HTMLElement;
    expect(rootNaver.dataset.brand).toBe("네이버");
    expect(screen.getByText("N")).toBeInTheDocument();
    expect(screen.getByText("네이버")).toBeInTheDocument();
    expect(screen.getByText("이미지 없음")).toBeInTheDocument();

    rerender(<BrandImagePlaceholder shopName="쿠팡" />);
    expect((container.firstElementChild as HTMLElement).dataset.brand).toBe("쿠팡");
    expect(screen.getByText("C")).toBeInTheDocument();
    expect(screen.getByText("쿠팡")).toBeInTheDocument();

    rerender(<BrandImagePlaceholder shopName="11번가" />);
    expect((container.firstElementChild as HTMLElement).dataset.brand).toBe("11번가");
    expect(screen.getByText("11")).toBeInTheDocument();

    rerender(<BrandImagePlaceholder shopName="무신사" />);
    expect((container.firstElementChild as HTMLElement).dataset.brand).toBe("무신사");
    expect(screen.getByText("M")).toBeInTheDocument();
  });

  it("compact 모드와 미매칭 쇼핑몰 폴백에서도 '이미지 없음' 접근성/테스트 계약을 유지한다", () => {
    const { container, rerender } = render(
      <BrandImagePlaceholder compact shopName="쿠팡" />
    );
    expect((container.firstElementChild as HTMLElement).dataset.brand).toBe("쿠팡");
    expect(screen.getByText("C")).toBeInTheDocument();
    expect(screen.getByText("이미지 없음")).toHaveClass("sr-only");

    rerender(<BrandImagePlaceholder shopName="미등록쇼핑몰" />);
    expect((container.firstElementChild as HTMLElement).dataset.brand).toBeUndefined();
    expect(screen.getByText("이미지 없음")).toBeInTheDocument();
  });
});
