import { useEffect, useRef, type RefObject } from "react";

const FOCUSABLE_SELECTOR = [
  "a[href]",
  "button:not([disabled])",
  "input:not([disabled]):not([type='hidden'])",
  "select:not([disabled])",
  "textarea:not([disabled])",
  "[tabindex]:not([tabindex='-1'])",
].join(", ");

function getFocusableElements(container: HTMLElement): HTMLElement[] {
  return Array.from(container.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR)).filter(
    (el) => {
      if (el.getAttribute("aria-hidden") === "true") return false;
      const style = typeof window !== "undefined" ? window.getComputedStyle(el) : null;
      if (style && (style.display === "none" || style.visibility === "hidden")) {
        return false;
      }
      return true;
    }
  );
}

interface UseDialogA11yOptions {
  isOpen: boolean;
  onClose: () => void;
  containerRef: RefObject<HTMLElement | null>;
  initialFocusRef?: RefObject<HTMLElement | null>;
}

/**
 * 모달/드로어 공통 WCAG 2.2 다이얼로그 접근성 훅:
 * - 열림 시 이전 포커스 저장 및 컨테이너 내부 첫 포커스 가능 요소로 포커스 이동
 * - Escape 키 닫기 및 Tab/Shift+Tab 포커스 트랩
 * - 닫힘/언마운트 시 트리거 요소로 포커스 복귀
 */
export function useDialogA11y({
  isOpen,
  onClose,
  containerRef,
  initialFocusRef,
}: UseDialogA11yOptions) {
  const onCloseRef = useRef(onClose);
  const previousActiveRef = useRef<HTMLElement | null>(null);

  useEffect(() => {
    onCloseRef.current = onClose;
  }, [onClose]);

  useEffect(() => {
    if (!isOpen || typeof document === "undefined") return;

    previousActiveRef.current =
      document.activeElement instanceof HTMLElement ? document.activeElement : null;

    const rafId = window.requestAnimationFrame(() => {
      const container = containerRef.current;
      if (!container) return;

      const preferred = initialFocusRef?.current;
      if (preferred && container.contains(preferred)) {
        preferred.focus({ preventScroll: true });
        return;
      }

      const focusables = getFocusableElements(container);
      if (focusables.length > 0) {
        focusables[0].focus({ preventScroll: true });
      } else {
        if (!container.hasAttribute("tabindex")) {
          container.setAttribute("tabindex", "-1");
        }
        container.focus({ preventScroll: true });
      }
    });

    const handleKeyDown = (e: KeyboardEvent) => {
      const container = containerRef.current;
      if (!container) return;

      // 중첩 다이얼로그(예: DealDetailModal 위의 ImageLightboxModal)에서 최상위 다이얼로그만 키 이벤트 처리
      const openDialogs = document.querySelectorAll('[role="dialog"]');
      if (
        openDialogs.length > 1 &&
        openDialogs[openDialogs.length - 1] !== container
      ) {
        return;
      }

      if (e.key === "Escape") {
        e.preventDefault();
        e.stopPropagation();
        onCloseRef.current();
        return;
      }

      if (e.key !== "Tab") return;

      const focusables = getFocusableElements(container);
      if (focusables.length === 0) {
        e.preventDefault();
        container.focus({ preventScroll: true });
        return;
      }

      const first = focusables[0];
      const last = focusables[focusables.length - 1];
      const active = document.activeElement;

      if (e.shiftKey) {
        if (!active || !container.contains(active) || active === first) {
          e.preventDefault();
          last.focus({ preventScroll: true });
        }
      } else {
        if (!active || !container.contains(active) || active === last) {
          e.preventDefault();
          first.focus({ preventScroll: true });
        }
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => {
      window.cancelAnimationFrame(rafId);
      window.removeEventListener("keydown", handleKeyDown);
      const prev = previousActiveRef.current;
      if (prev && typeof prev.focus === "function" && document.contains(prev)) {
        prev.focus({ preventScroll: true });
      }
      previousActiveRef.current = null;
    };
  }, [isOpen, containerRef, initialFocusRef]);
}
