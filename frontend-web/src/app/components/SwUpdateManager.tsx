"use client";

import React, { useCallback, useEffect, useRef, useState } from "react";
import { RefreshCw, X } from "lucide-react";
import { isTossMiniAppEnv } from "../lib/tossBridge";

const LEGACY_RUNTIME_CACHES = ["start-url", "pages"];
const SW_IDLE_SCROLL_MAX_Y = 120;

export function isSwReloadSafeIdle(): boolean {
  if (typeof window === "undefined" || typeof document === "undefined") {
    return false;
  }
  if (document.visibilityState !== "visible") {
    return false;
  }
  if ((window.scrollY || 0) > SW_IDLE_SCROLL_MAX_Y) {
    return false;
  }
  if (
    document.querySelector(
      '[role="dialog"][aria-modal="true"], .deal-modal-overlay, .header-search-popover'
    )
  ) {
    return false;
  }
  const activeEl = document.activeElement;
  if (activeEl) {
    const tag = activeEl.tagName?.toUpperCase() || "";
    if (
      tag === "INPUT" ||
      tag === "TEXTAREA" ||
      tag === "SELECT" ||
      activeEl.hasAttribute("contenteditable")
    ) {
      return false;
    }
  }
  return true;
}

export default function SwUpdateManager() {
  const [updateReady, setUpdateReady] = useState(false);
  const waitingWorkerRef = useRef<ServiceWorker | null>(null);
  const reloadingRef = useRef(false);

  const applyWaitingUpdate = useCallback(() => {
    const waiting = waitingWorkerRef.current;
    if (!waiting) {
      window.location.reload();
      return;
    }
    waiting.postMessage({ type: "SKIP_WAITING" });
  }, []);

  useEffect(() => {
    if (
      isTossMiniAppEnv() ||
      typeof window === "undefined" ||
      !("serviceWorker" in navigator)
    ) {
      return;
    }

    let isUnmounted = false;
    let registrationRef: ServiceWorkerRegistration | null = null;
    const hadControllerAtBoot = Boolean(navigator.serviceWorker.controller);

    const handleWaitingWorker = (waiting: ServiceWorker | null) => {
      if (!waiting || isUnmounted) return;
      waitingWorkerRef.current = waiting;
      if (hadControllerAtBoot && isSwReloadSafeIdle()) {
        waiting.postMessage({ type: "SKIP_WAITING" });
      } else {
        setUpdateReady(true);
      }
    };

    const handleControllerChange = () => {
      if (reloadingRef.current) return;
      reloadingRef.current = true;
      window.location.reload();
    };

    navigator.serviceWorker.addEventListener("controllerchange", handleControllerChange);

    if ("caches" in window) {
      void Promise.all(
        LEGACY_RUNTIME_CACHES.map((cacheName) =>
          window.caches.delete(cacheName).catch(() => false)
        )
      );
    }

    navigator.serviceWorker
      .register("/pwa-sw.js", { scope: "/", updateViaCache: "none" })
      .then((reg) => {
        if (isUnmounted) return;
        registrationRef = reg;

        if (reg.waiting) {
          handleWaitingWorker(reg.waiting);
        }

        reg.addEventListener("updatefound", () => {
          const installing = reg.installing;
          if (!installing) return;
          installing.addEventListener("statechange", () => {
            if (
              installing.state === "installed" &&
              navigator.serviceWorker.controller
            ) {
              handleWaitingWorker(reg.waiting || installing);
            }
          });
        });

        void reg.update().catch(() => {});
      })
      .catch(() => {});

    const handleVisibilityChange = () => {
      if (document.visibilityState === "visible" && registrationRef) {
        void registrationRef.update().catch(() => {});
      }
    };

    document.addEventListener("visibilitychange", handleVisibilityChange);

    return () => {
      isUnmounted = true;
      navigator.serviceWorker.removeEventListener(
        "controllerchange",
        handleControllerChange
      );
      document.removeEventListener("visibilitychange", handleVisibilityChange);
    };
  }, []);

  if (!updateReady) {
    return null;
  }

  return (
    <div
      role="status"
      aria-live="polite"
      className="sw-update-toast"
    >
      <span className="sw-update-toast__label">새 버전이 준비됐어요</span>
      <button
        type="button"
        onClick={applyWaitingUpdate}
        className="sw-update-toast__action bounce-click"
      >
        <RefreshCw size={13} aria-hidden="true" />
        <span>새로고침</span>
      </button>
      <button
        type="button"
        onClick={() => setUpdateReady(false)}
        aria-label="업데이트 안내 닫기"
        className="sw-update-toast__close"
      >
        <X size={14} aria-hidden="true" />
      </button>
    </div>
  );
}
