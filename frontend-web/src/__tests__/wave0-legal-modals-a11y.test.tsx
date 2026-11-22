import React, { useState } from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import EulaModal from "@/app/components/EulaModal";
import PrivacyModal from "@/app/components/PrivacyModal";

const FOCUSABLE_SELECTOR = [
  "a[href]",
  "button:not([disabled])",
  "input:not([disabled]):not([type='hidden'])",
  "select:not([disabled])",
  "textarea:not([disabled])",
  "[tabindex]:not([tabindex='-1'])",
].join(", ");

interface ModalHarnessProps {
  title: string;
  triggerLabel: string;
  ModalComponent: React.ComponentType<{ isOpen: boolean; onClose: () => void }>;
}

/**
 * 모달 열림/닫힘과 이전 포커스 복귀를 검증하기 위한 제어 하네스 컴포넌트
 */
function ModalHarness({
  title,
  triggerLabel,
  ModalComponent,
}: ModalHarnessProps) {
  const [isOpen, setIsOpen] = useState(false);

  return (
    <div>
      <button type="button" onClick={() => setIsOpen(true)}>
        {triggerLabel}
      </button>
      <ModalComponent isOpen={isOpen} onClose={() => setIsOpen(false)} />
      <span className="sr-only">{title}</span>
    </div>
  );
}

describe("Wave 0 법정 고지 모달(PrivacyModal / EulaModal) 콘텐츠 및 접근성 검증", () => {
  it("isOpen={false}일 때는 두 모달 모두 다이얼로그를 렌더링하지 않는다", () => {
    const onClose = vi.fn();
    render(
      <>
        <PrivacyModal isOpen={false} onClose={onClose} />
        <EulaModal isOpen={false} onClose={onClose} />
      </>
    );

    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("PrivacyModal은 5대 법정 고지 영역과 mailto 링크 및 다이얼로그 속성을 모두 렌더링한다", () => {
    render(<PrivacyModal isOpen={true} onClose={vi.fn()} />);

    const dialog = screen.getByRole("dialog", { name: "개인정보 처리방침" });
    expect(dialog).toBeInTheDocument();
    expect(dialog).toHaveAttribute("aria-modal", "true");

    const labelledBy = dialog.getAttribute("aria-labelledby");
    expect(labelledBy).toBeTruthy();
    const heading = document.getElementById(labelledBy as string);
    expect(heading).toHaveTextContent("개인정보 처리방침");

    expect(dialog).toHaveStyle({
      maxHeight: "85dvh",
      overflowY: "auto",
    });

    // ① 수집 항목 및 이용 목적 (게스트/계정/로그/로컬 저장 vs 모바일 생체인증 구분)
    expect(
      screen.getByRole("heading", { name: /1\. 수집하는 항목 및 이용 목적/ })
    ).toBeInTheDocument();
    expect(dialog).toHaveTextContent(/device_uuid/);
    expect(dialog).toHaveTextContent(/Web Push/);
    expect(dialog).toHaveTextContent(/FCM 토큰/);
    expect(dialog).toHaveTextContent(/PBKDF2-HMAC-SHA256/);
    expect(dialog).toHaveTextContent(/localStorage/);
    expect(dialog).toHaveTextContent(/Android KeyStore/);
    expect(dialog).toHaveTextContent(/iOS Secure Enclave/);

    // ② 보유 및 이용기간 (탈퇴·초기화·법령상 예외)
    expect(
      screen.getByRole("heading", { name: /2\. 보유 및 이용기간과 파기 방법/ })
    ).toBeInTheDocument();
    expect(dialog).toHaveTextContent(/계정 탈퇴/);
    expect(dialog).toHaveTextContent(/통신비밀보호법/);
    expect(dialog).toHaveTextContent(/전자상거래 등에서의 소비자보호에 관한 법률/);

    // ③ 처리위탁 및 국외 이전
    expect(
      screen.getByRole("heading", { name: /3\. 개인정보 처리위탁 및 국외 이전/ })
    ).toBeInTheDocument();
    expect(dialog).toHaveTextContent(/Google Cloud Platform/);
    expect(dialog).toHaveTextContent(/Vercel Inc\./);
    expect(dialog).toHaveTextContent(/Firebase Cloud Messaging/);
    expect(dialog).toHaveTextContent(/비바리퍼블리카\(앱인토스\)/);

    // ④ 제휴 마케팅 및 외부 사이트 이동 안내
    expect(
      screen.getByRole("heading", {
        name: /4\. 제휴 마케팅\(쿠팡 파트너스·링크프라이스\) 및 외부 사이트 이동 안내/,
      })
    ).toBeInTheDocument();
    expect(dialog).toHaveTextContent(/쿠팡 파트너스/);
    expect(dialog).toHaveTextContent(/링크프라이스/);
    expect(dialog).toHaveTextContent(/구매 가격에도 영향을 미치지 않습니다/);

    // ⑤ 권리행사 방법, 보호책임자, 문의처 mailto 링크, 구제기관
    expect(
      screen.getByRole("heading", {
        name: /5\. 정보주체의 권리·행사 방법, 개인정보 보호책임자 및 구제기관/,
      })
    ).toBeInTheDocument();
    expect(dialog).toHaveTextContent(/얼만데 서비스 운영팀/);
    expect(dialog).toHaveTextContent(/개인정보분쟁조정위원회/);
    expect(dialog).toHaveTextContent(/개인정보침해신고센터/);

    const mailLink = screen.getByRole("link", { name: "support@insightdeal.com" });
    expect(mailLink).toHaveAttribute("href", "mailto:support@insightdeal.com");

    const topCloseBtn = screen.getByRole("button", {
      name: "개인정보 처리방침 닫기",
    });
    expect(topCloseBtn).toHaveStyle({ minWidth: "44px", minHeight: "44px" });
  });

  it("EulaModal은 6개 약관 조항과 제휴·면책·문의처 및 다이얼로그 속성을 모두 렌더링한다", () => {
    render(<EulaModal isOpen={true} onClose={vi.fn()} />);

    const dialog = screen.getByRole("dialog", { name: "서비스 이용약관" });
    expect(dialog).toBeInTheDocument();
    expect(dialog).toHaveAttribute("aria-modal", "true");

    const labelledBy = dialog.getAttribute("aria-labelledby");
    expect(labelledBy).toBeTruthy();
    const heading = document.getElementById(labelledBy as string);
    expect(heading).toHaveTextContent("서비스 이용약관");

    expect(dialog).toHaveStyle({
      maxHeight: "85dvh",
      overflowY: "auto",
    });

    // 6개 조항 제목 및 핵심 고지 문구 검증
    expect(
      screen.getByRole("heading", { name: /1\. 서비스 성격 및 당사자 지위/ })
    ).toBeInTheDocument();
    expect(dialog).toHaveTextContent(/통신판매중개자나 상품의 판매 당사자가 아니며/);

    expect(
      screen.getByRole("heading", { name: /2\. 가격·재고·할인 조건 변동 안내/ })
    ).toBeInTheDocument();
    expect(dialog).toHaveTextContent(/카드사·멤버십·쿠폰 조건 변경/);

    expect(
      screen.getByRole("heading", { name: /3\. 제휴 마케팅 대가성 고지/ })
    ).toBeInTheDocument();
    expect(dialog).toHaveTextContent(/쿠팡 파트너스 및 링크프라이스/);
    expect(dialog).toHaveTextContent(/추가 부담도 주지 않습니다/);

    expect(
      screen.getByRole("heading", {
        name: /4\. 외부 링크·커뮤니티 게시물 면책 및 권리 보호/,
      })
    ).toBeInTheDocument();
    expect(dialog).toHaveTextContent(/품절 제보/);

    expect(
      screen.getByRole("heading", { name: /5\. 비정상 이용 제한/ })
    ).toBeInTheDocument();
    expect(dialog).toHaveTextContent(/자동화 수집/);

    expect(
      screen.getByRole("heading", {
        name: /6\. 서비스 변경·중단, 약관 개정 및 문의처/,
      })
    ).toBeInTheDocument();

    const mailLink = screen.getByRole("link", { name: "support@insightdeal.com" });
    expect(mailLink).toHaveAttribute("href", "mailto:support@insightdeal.com");

    const topCloseBtn = screen.getByRole("button", {
      name: "서비스 이용약관 닫기",
    });
    expect(topCloseBtn).toHaveStyle({ minWidth: "44px", minHeight: "44px" });
  });

  it("패널 내부 클릭은 전파 차단되고 백드롭·상단 X·하단 닫기 버튼만 onClose를 호출한다", () => {
    const onClosePrivacy = vi.fn();
    const { unmount } = render(
      <PrivacyModal isOpen={true} onClose={onClosePrivacy} />
    );

    const privacyDialog = screen.getByRole("dialog", { name: "개인정보 처리방침" });
    fireEvent.click(privacyDialog);
    fireEvent.click(screen.getByRole("heading", { name: "개인정보 처리방침" }));
    expect(onClosePrivacy).not.toHaveBeenCalled();

    const privacyBackdrop = privacyDialog.parentElement as HTMLElement;
    fireEvent.click(privacyBackdrop);
    expect(onClosePrivacy).toHaveBeenCalledTimes(1);

    fireEvent.click(
      screen.getByRole("button", { name: "개인정보 처리방침 닫기" })
    );
    expect(onClosePrivacy).toHaveBeenCalledTimes(2);

    fireEvent.click(screen.getByRole("button", { name: "닫기" }));
    expect(onClosePrivacy).toHaveBeenCalledTimes(3);

    unmount();

    const onCloseEula = vi.fn();
    render(<EulaModal isOpen={true} onClose={onCloseEula} />);

    const eulaDialog = screen.getByRole("dialog", { name: "서비스 이용약관" });
    fireEvent.click(eulaDialog);
    fireEvent.click(screen.getByRole("heading", { name: "서비스 이용약관" }));
    expect(onCloseEula).not.toHaveBeenCalled();

    const eulaBackdrop = eulaDialog.parentElement as HTMLElement;
    fireEvent.click(eulaBackdrop);
    expect(onCloseEula).toHaveBeenCalledTimes(1);

    fireEvent.click(
      screen.getByRole("button", { name: "서비스 이용약관 닫기" })
    );
    expect(onCloseEula).toHaveBeenCalledTimes(2);

    fireEvent.click(screen.getByRole("button", { name: "닫기" }));
    expect(onCloseEula).toHaveBeenCalledTimes(3);
  });

  it.each([
    {
      name: "PrivacyModal",
      triggerLabel: "개인정보 처리방침 열기",
      topCloseLabel: "개인정보 처리방침 닫기",
      ModalComponent: PrivacyModal,
    },
    {
      name: "EulaModal",
      triggerLabel: "서비스 이용약관 열기",
      topCloseLabel: "서비스 이용약관 닫기",
      ModalComponent: EulaModal,
    },
  ])(
    "$name: 열림 시 상단 X 버튼으로 rAF 포커스 진입, Escape 닫기 시 트리거 포커스 복귀를 보장한다",
    async ({ triggerLabel, topCloseLabel, ModalComponent }) => {
      render(
        <ModalHarness
          title={triggerLabel}
          triggerLabel={triggerLabel}
          ModalComponent={ModalComponent}
        />
      );

      const trigger = screen.getByRole("button", { name: triggerLabel });
      trigger.focus();
      expect(trigger).toHaveFocus();

      fireEvent.click(trigger);

      const dialog = screen.getByRole("dialog");
      const topCloseBtn = screen.getByRole("button", { name: topCloseLabel });

      // useDialogA11y의 requestAnimationFrame 초기 포커스가 본문 하단 링크 대신 상단 X 버튼에 진입하는지 확인
      await waitFor(() => {
        expect(dialog).toContainElement(document.activeElement as HTMLElement | null);
        expect(topCloseBtn).toHaveFocus();
      });

      fireEvent.keyDown(window, { key: "Escape" });

      expect(screen.queryByRole("dialog")).toBeNull();
      expect(trigger).toHaveFocus();
    }
  );

  it.each([
    {
      name: "PrivacyModal",
      ModalComponent: PrivacyModal,
    },
    {
      name: "EulaModal",
      ModalComponent: EulaModal,
    },
  ])(
    "$name: Tab / Shift+Tab 경계 랩어라운드 포커스 트랩이 정상 동작한다",
    ({ ModalComponent }) => {
      render(<ModalComponent isOpen={true} onClose={vi.fn()} />);

      const dialog = screen.getByRole("dialog");
      const focusables = Array.from(
        dialog.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR)
      );

      // 상단 X 버튼 + mailto 링크 + 하단 닫기 버튼 = 3개 포커스 가능 요소
      expect(focusables.length).toBe(3);

      const first = focusables[0];
      const last = focusables[focusables.length - 1];

      first.focus();
      expect(first).toHaveFocus();
      fireEvent.keyDown(window, { key: "Tab", shiftKey: true });
      expect(last).toHaveFocus();

      last.focus();
      expect(last).toHaveFocus();
      fireEvent.keyDown(window, { key: "Tab" });
      expect(first).toHaveFocus();
    }
  );
});
