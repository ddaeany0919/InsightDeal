"use client";

import React, { useSyncExternalStore } from "react";
import { ExternalLink, X, Compass } from "lucide-react";
import {
  buildExternalBrowserIntentUrl,
  isExternalInAppWebView,
  isIosUserAgent,
} from "../lib/utils";
import { copyTextSafe } from "../lib/tossBridge";

const DISMISS_STORAGE_KEY = "insightdeal_dismiss_inapp_banner";
const DISMISS_CHANGE_EVENT = "insightdeal-inapp-banner-change";

interface InAppBrowserBannerProps {
  onShowToast?: (message: string) => void;
}

function getBannerSnapshot(): boolean {
  if (typeof window === "undefined") return false;
  if (!isExternalInAppWebView()) return false;
  try {
    if (sessionStorage.getItem(DISMISS_STORAGE_KEY) === "1") {
      return false;
    }
  } catch {}
  return true;
}

function subscribeBanner(callback: () => void): () => void {
  if (typeof window === "undefined") return () => {};
  window.addEventListener(DISMISS_CHANGE_EVENT, callback);
  return () => window.removeEventListener(DISMISS_CHANGE_EVENT, callback);
}

export default function InAppBrowserBanner({
  onShowToast,
}: InAppBrowserBannerProps) {
  const visible = useSyncExternalStore(
    subscribeBanner,
    getBannerSnapshot,
    () => false
  );

  if (!visible) return null;

  const isIos = isIosUserAgent();
  const intentUrl = !isIos ? buildExternalBrowserIntentUrl() : null;

  const handleDismiss = () => {
    try {
      sessionStorage.setItem(DISMISS_STORAGE_KEY, "1");
    } catch {}
    if (typeof window !== "undefined") {
      window.dispatchEvent(new Event(DISMISS_CHANGE_EVENT));
    }
  };

  const handleCopyAndGuideIos = async () => {
    if (typeof window === "undefined") return;
    const copied = await copyTextSafe(window.location.href);
    onShowToast?.(
      copied
        ? "주소를 복사했어요. Safari에서 붙여넣어 열면 푸시 알림을 받을 수 있어요"
        : "우측 상단 ··· 메뉴에서 'Safari로 열기'를 선택해 주세요"
    );
  };

  return (
    <aside
      className="inapp-banner"
      role="region"
      aria-label="외부 브라우저 열기 안내"
    >
      <div
        style={{
          display: "flex",
          alignItems: "center",
          gap: "10px",
          flex: "1 1 220px",
          minWidth: 0,
        }}
      >
        <Compass size={18} aria-hidden="true" style={{ flexShrink: 0 }} />
        <div style={{ display: "flex", flexDirection: "column", gap: "2px" }}>
          <span style={{ fontSize: "0.84rem", fontWeight: 800 }}>
            인앱 브라우저에서는 알림·구매 이동이 제한될 수 있어요
          </span>
          <span style={{ fontSize: "0.76rem", fontWeight: 600 }}>
            {isIos
              ? "Safari로 열면 실시간 키워드 푸시 알림을 받을 수 있어요."
              : "Chrome 등 외부 브라우저로 열면 모든 기능을 안정적으로 쓸 수 있어요."}
          </span>
        </div>
      </div>

      <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
        {intentUrl ? (
          <a
            href={intentUrl}
            className="btn-primary bounce-click"
            style={{
              minHeight: "44px",
              padding: "8px 14px",
              borderRadius: "10px",
              fontSize: "0.8rem",
              whiteSpace: "nowrap",
            }}
          >
            <span>외부 브라우저로 열기</span>
            <ExternalLink size={14} aria-hidden="true" />
          </a>
        ) : (
          <button
            type="button"
            onClick={() => void handleCopyAndGuideIos()}
            className="btn-primary bounce-click"
            style={{
              minHeight: "44px",
              padding: "8px 14px",
              borderRadius: "10px",
              fontSize: "0.8rem",
              whiteSpace: "nowrap",
            }}
          >
            <span>{isIos ? "Safari용 주소 복사" : "외부 브라우저로 열기"}</span>
            <ExternalLink size={14} aria-hidden="true" />
          </button>
        )}

        <button
          type="button"
          onClick={handleDismiss}
          aria-label="외부 브라우저 안내 닫기"
          className="banner-close-btn"
          style={{ color: "var(--warning-text)" }}
        >
          <X size={16} aria-hidden="true" />
        </button>
      </div>
    </aside>
  );
}
