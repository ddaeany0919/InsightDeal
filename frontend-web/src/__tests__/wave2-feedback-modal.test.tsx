import React from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import FeedbackModal from "@/app/components/FeedbackModal";

describe("Wave 2 경량 VOC 피드백 모달(FeedbackModal)", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it("5자 미만 제출 시 검증 오류를 표시하고, 정상 입력 시 /api/community/feedback로 제출 후 닫힌다", async () => {
    const onClose = vi.fn();
    const onShowToast = vi.fn();

    localStorage.setItem("insightdeal_device_uuid", "test-device-voc-1");
    localStorage.setItem("insightdeal_device_token", "v1.tok.voc");

    const fetchMock = vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
      const url = typeof input === "string" ? input : input instanceof URL ? input.toString() : input.url;
      if (url.includes("/api/push/device")) {
        return new Response(
          JSON.stringify({
            device_uuid: "test-device-voc-1",
            device_token: "v1.tok.voc",
          }),
          { status: 200 }
        );
      }
      return new Response(
        JSON.stringify({
          success: true,
          id: 42,
          message: "소중한 의견을 접수했어요. 빠르게 검토할게요!",
        }),
        { status: 200 }
      );
    });

    render(<FeedbackModal isOpen={true} onClose={onClose} onShowToast={onShowToast} />);

    expect(screen.getByRole("dialog", { name: "문의 · 오류 제보" })).toBeInTheDocument();

    const submitBtn = screen.getByRole("button", { name: "의견 보내기" });
    fireEvent.click(submitBtn);
    expect(screen.getByRole("alert")).toHaveTextContent("5자 이상");
    expect(fetchMock).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole("button", { name: "기능 제안" }));
    fireEvent.change(screen.getByLabelText("상세 내용"), {
      target: { value: "로보락 S9 동의어도 검색되면 좋겠어요" },
    });
    fireEvent.change(screen.getByLabelText("답변 받을 이메일 (선택)"), {
      target: { value: "user@example.com" },
    });
    fireEvent.click(submitBtn);

    await waitFor(() => {
      const feedbackCall = fetchMock.mock.calls.find(([u]) => String(u).includes("/api/community/feedback"));
      expect(feedbackCall).toBeTruthy();
      const parsedBody = JSON.parse(String(feedbackCall?.[1]?.body || "{}"));
      expect(parsedBody.category).toBe("feature");
      expect(parsedBody.message).toBe("로보락 S9 동의어도 검색되면 좋겠어요");
      expect(parsedBody.contact).toBe("user@example.com");
      expect(onShowToast).toHaveBeenCalledWith("소중한 의견을 접수했어요. 빠르게 검토할게요!");
      expect(onClose).toHaveBeenCalled();
    });
  });

  it("Escape 키 입력 시 onClose를 호출한다", () => {
    const onClose = vi.fn();
    render(<FeedbackModal isOpen={true} onClose={onClose} />);
    fireEvent.keyDown(window, { key: "Escape" });
    expect(onClose).toHaveBeenCalledTimes(1);
  });
});
