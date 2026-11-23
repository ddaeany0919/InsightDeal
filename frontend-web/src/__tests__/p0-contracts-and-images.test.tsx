import React from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import DealDetailModal from "@/app/components/DealDetailModal";
import NotificationSettingsPage from "@/app/settings/notifications/page";
import type { Deal, RawDealApiResponse } from "@/app/lib/types";
import {
  extractPriceFromTitle,
  getFallbackProxyImageUrl,
  getProxyImageUrl,
  mapRawDealToDeal,
  polishDealBodyText,
  resolveOutboundUrl,
} from "@/app/lib/utils";

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

describe("DTO 1-RTT/이중 키/이미지 전 구간/DND 표준 카피 계약 검증", () => {
  it("mapRawDealToDeal은 1-RTT 요약 필, 이중 키 및 레거시 '여행.해외핫딜'->'해외직구' 매핑을 지원한다", () => {
    const raw: RawDealApiResponse = {
      id: 5001,
      title: "[알리] 미니 PC 특가",
      price: 0,
      is_free: true,
      category: "여행.해외핫딜",
      site_name: "알리뽐뿌",
      buyCount: 7,
      saveCount: 3,
      userVote: "buy",
      commentCount: 4,
      historyCount: 2,
      is_search_fallback: true,
      indexed_at: "2026-11-19T12:00:00Z",
    };

    const mapped = mapRawDealToDeal(raw);
    expect(mapped.category).toBe("해외직구");
    expect(mapped.isFree).toBe(true);
    expect(mapped.price).toBe("0");
    expect(mapped.buyCount).toBe(7);
    expect(mapped.passCount).toBe(3);
    expect(mapped.userVote).toBe("buy");
    expect(mapped.commentCount).toBe(4);
    expect(mapped.historyCount).toBe(2);
    expect(mapped.isSearchFallback).toBe(true);
  });

  it("getProxyImageUrl, polishDealBodyText, resolveOutboundUrl이 이미지 및 옥션/지마켓 HTTPS 보정을 정확히 수행한다", () => {
    expect(extractPriceFromTitle("[네이버] PS5 콜오브듀티 모던 워페어4 /88,900원/2500원")).toEqual({
      price: "88900",
      currency: "KRW",
    });
    expect(extractPriceFromTitle("[루리웹] PS5 콜오브듀티 모던 워페어4 85,900원 택배비 2,500원")).toEqual({
      price: "85900",
      currency: "KRW",
    });
    // 차단 커뮤니티 -> 백엔드 프록시 직행
    expect(getProxyImageUrl("https://cdn.ppomppu.co.kr/img/a.jpg")).toBe(
      `/api/proxy-image?url=${encodeURIComponent("https://cdn.ppomppu.co.kr/img/a.jpg")}`
    );
    expect(getProxyImageUrl("https://img.fmkorea.com/b.png")).toBe(
      `/api/proxy-image?url=${encodeURIComponent("https://img.fmkorea.com/b.png")}`
    );
    expect(
      getProxyImageUrl("https://seorincomputer.co.kr/data/pimg/163/163082_600.jpg", {
        width: 400,
      })
    ).toBe(
      `/api/proxy-image?url=${encodeURIComponent("https://seorincomputer.co.kr/data/pimg/163/163082_600.jpg")}`
    );
    expect(
      getProxyImageUrl("https://www.seorincomputer.co.kr/data/pimg/163/163082_600.jpg")
    ).toBe(
      `/api/proxy-image?url=${encodeURIComponent("https://www.seorincomputer.co.kr/data/pimg/163/163082_600.jpg")}`
    );
    // 평문 http:// -> 백엔드 프록시 직행
    expect(getProxyImageUrl("http://shop.example.com/item.jpg")).toBe(
      `/api/proxy-image?url=${encodeURIComponent("http://shop.example.com/item.jpg")}`
    );
    // 일반 https:// -> wsrv.nl
    expect(getProxyImageUrl("https://cdn.example.com/ok.png", { width: 400 })).toBe(
      `https://wsrv.nl/?url=${encodeURIComponent("https://cdn.example.com/ok.png")}&output=webp&w=400&we=1`
    );
    expect(getFallbackProxyImageUrl("https://cdn.example.com/ok.png")).toBe(
      `/api/proxy-image?url=${encodeURIComponent("https://cdn.example.com/ok.png")}`
    );

    // 옥션/지마켓 http:// -> https:// 업그레이드
    expect(
      resolveOutboundUrl("http://itempage3.auction.co.kr/DetailView.aspx?itemno=A123")
    ).toMatch(/^https:\/\//);
    expect(
      resolveOutboundUrl("http://mitem.auction.co.kr/vip?itemNo=A123")
    ).toMatch(/^https:\/\//);
    expect(
      resolveOutboundUrl("http://item.gmarket.co.kr/Item?goodscode=123456")
    ).toMatch(/^https:\/\//);
    expect(
      resolveOutboundUrl("http://mitem.gmarket.co.kr/vi/product/123456")
    ).toMatch(/^https:\/\//);

    // 본문 HTML 내 깨진 이미지 태그 및 아티팩트 정제
    const polished = polishDealBodyText(
      '<p>알찬 특가 본문입니다.</p><img src="https://bad.img/1.jpg" /><p>[상품이미지 : https://bad.img/2.jpg]</p><p>[첨부된 이미지 링크들]</p>'
    );
    expect(polished).toContain("알찬 특가 본문입니다.");
    expect(polished).not.toContain("<img");
    expect(polished).not.toContain("[상품이미지");
    expect(polished).not.toContain("[첨부된 이미지");
  });

  it("DealDetailModal은 1-RTT 단건만 호출하고 Summary Pill, 검색폴백 카피, 이미지 폴백, 아코디언 Lazy-fetch를 수행한다", async () => {
    localStorage.setItem("insightdeal_web_device_uuid", "web_dev_501");
    localStorage.setItem("insightdeal_web_device_token", "tok_dev_501");

    const fetchCalls: string[] = [];
    vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
      const url = typeof input === "string" ? input : input instanceof URL ? input.toString() : input.url;
      fetchCalls.push(url);

      if (url.includes("/api/community/deals/501/comments")) {
        return new Response(
          JSON.stringify([
            {
              id: 11,
              userId: "web_dev_501",
              nickname: "찰떡테스터",
              content: "이중 키 댓글입니다",
              createdAt: "2026-11-19T12:30:00Z",
            },
          ]),
          { status: 200 }
        );
      }
      if (url.includes("/api/community/deals/501/votes")) {
        return new Response(
          JSON.stringify({
            buy_count: 5,
            pass_count: 2,
            user_vote: null,
          }),
          { status: 200 }
        );
      }
      return new Response(
        JSON.stringify({
          id: 501,
          title: "[쿠팡] 모달 테스트 상품",
          price: 29900,
          currency: "KRW",
          site_name: "뽐뿌",
          post_url: "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=501",
          ecommerce_url: "https://www.coupang.com/np/search?q=테스트",
          image_url: "https://cdn.example.com/modal-main.jpg",
          content_html: '<p>깨끗한 본문</p><img src="https://x.com/a.jpg" /><p>[상품이미지 : https://x.com/b.jpg]</p>',
          buy_count: 5,
          pass_count: 2,
          user_vote: null,
          comment_count: 4,
          history_count: 3,
          is_search_fallback: true,
          is_free: false,
          options_data: [
            {
              name: "옵션1",
              price: 29900,
              currency: "KRW",
              url: "https://shop.example.com/opt1",
              image_url: "https://cdn.example.com/opt-1.jpg",
            },
            {
              name: "옵션2",
              price: 39900,
              currency: "KRW",
              url: "https://shop.example.com/opt2",
              image_url: "https://cdn.ppomppu.co.kr/opt-2.jpg",
            },
          ],
        }),
        { status: 200 }
      );
    });

    const initialDeal: Deal = {
      id: 501,
      community_name: "뽐뿌",
      title: "[쿠팡] 모달 테스트 상품",
      price: "29900",
      image_url: "https://cdn.example.com/modal-main.jpg",
      scraped_at: "2026-11-19T12:00:00Z",
      post_link: "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=501",
      ecommerce_url: "https://www.coupang.com/np/search?q=테스트",
      currency: "KRW",
      category: "생활용품",
      isClosed: false,
    };

    render(
      <DealDetailModal deal={initialDeal} onClose={vi.fn()} showToast={vi.fn()} />
    );

    await waitFor(() => {
      expect(screen.getByText("가격 추이 (3건)")).toBeInTheDocument();
      expect(screen.getByText("살까 5 / 말까 2")).toBeInTheDocument();
      expect(screen.getAllByText("의견 4개").length).toBeGreaterThanOrEqual(1);
    });

    // 1-RTT 단건만 호출 (아코디언 펼침 전에는 /votes, /comments 미호출)
    expect(fetchCalls).toHaveLength(1);
    expect(fetchCalls[0]).toContain("/api/community/deals/501");

    // 검색 폴백 표준 카피 및 CTA
    expect(
      screen.getByText("직접 링크 미제공 · 쇼핑몰 검색 결과로 이동해요")
    ).toBeInTheDocument();
    expect(screen.getByText("쇼핑몰 검색 결과 보기")).toBeInTheDocument();

    // 본문 정제 확인
    expect(screen.getByText("깨끗한 본문")).toBeInTheDocument();
    expect(screen.queryByText(/\[상품이미지/)).not.toBeInTheDocument();

    // 메인 썸네일 & 옵션 썸네일 프록시 + onError 폴백 확인
    const optionImgs = screen.getAllByAltText("옵션1") as HTMLImageElement[];
    expect(optionImgs.length).toBeGreaterThanOrEqual(2);
    const mainImg = optionImgs[0];
    expect(mainImg.getAttribute("src")).toContain("https://wsrv.nl/?url=");
    expect(mainImg.getAttribute("src")).toContain("w=400");
    fireEvent.error(mainImg);
    expect(mainImg.getAttribute("src")).toBe(
      `/api/proxy-image?url=${encodeURIComponent("https://cdn.example.com/opt-1.jpg")}`
    );

    const opt2Img = screen.getByAltText("옵션2") as HTMLImageElement;
    expect(opt2Img.getAttribute("src")).toBe(
      `/api/proxy-image?url=${encodeURIComponent("https://cdn.ppomppu.co.kr/opt-2.jpg")}`
    );

    // 의견 아코디언 펼침 -> Lazy-fetch로 /comments 호출
    const commentToggleBtn = screen.getByRole("button", { name: /한줄 의견/ });
    expect(commentToggleBtn).toHaveAttribute("aria-expanded", "false");
    fireEvent.click(commentToggleBtn);

    await waitFor(() => {
      expect(screen.getByText("이중 키 댓글입니다")).toBeInTheDocument();
    });
    expect(fetchCalls.some((u) => u.includes("/api/community/deals/501/comments"))).toBe(
      true
    );
  });

  it("알림 설정 페이지는 표준 DND 카피를 표시하고 스위치 클릭 시 PATCH /api/push/dnd + X-Device-Token을 전송한다", async () => {
    localStorage.setItem("insightdeal_web_device_uuid", "web_dnd_uuid");
    localStorage.setItem("insightdeal_web_device_token", "web_dnd_token");

    const patchCalls: Array<{ url: string; headers: HeadersInit | undefined; body: string | undefined }> = [];
    vi.spyOn(globalThis, "fetch").mockImplementation(async (input, init) => {
      const url = typeof input === "string" ? input : input instanceof URL ? input.toString() : input.url;
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
      if (url.includes("/api/push/dnd")) {
        patchCalls.push({
          url,
          headers: init?.headers,
          body: typeof init?.body === "string" ? init.body : undefined,
        });
        return new Response(JSON.stringify({ status: "ok" }), { status: 200 });
      }
      if (url.includes("/api/push-public-key")) {
        return new Response(JSON.stringify({ public_key: "BRealKeyForTestOnly" }), {
          status: 200,
        });
      }
      return new Response(JSON.stringify({ status: "ok" }), { status: 200 });
    });

    render(<NotificationSettingsPage />);

    expect(
      await screen.findByText(
        "설정한 시간에는 푸시 알림을 보내지 않아요 (놓친 알림은 알림함에서 확인 가능)"
      )
    ).toBeInTheDocument();

    const dndSwitch = screen.getByRole("switch", { name: "방해금지 시간대 설정" });
    fireEvent.click(dndSwitch);

    await waitFor(() => {
      expect(patchCalls).toHaveLength(1);
    });
    expect(patchCalls[0].url).toContain("/api/push/dnd");
    const hdrs = new Headers(patchCalls[0].headers);
    expect(hdrs.get("X-Device-Token")).toBe("web_dnd_token");
  });
});
