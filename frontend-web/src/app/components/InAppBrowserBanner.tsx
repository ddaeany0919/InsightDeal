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
        ? "주소를 복사했어요. Safari 주소창에 붙여넣어 열어주세요"
        : "오른쪽 위 ··· 메뉴에서 'Safari로 열기'를 눌러주세요"
    );
  };

  return (
    <aside
      className="inapp-banner"
      role="region"
      aria-label="외부 브라우저 열기 안내"
    >
      <div className="inapp-banner-main">
        <Compass size={16} aria-hidden="true" style={{ flexShrink: 0 }} />
        <span className="inapp-banner-text">
          외부 브라우저로 열면 알림과 결제 이동이 더 원활해요
        </span>
      </div>

      <div className="inapp-banner-actions">
        {intentUrl ? (
          <a
            href={intentUrl}
            className="btn-primary bounce-click inapp-banner-cta"
          >
            <span>Chrome으로 열기</span>
            <ExternalLink size={13} aria-hidden="true" />
          </a>
        ) : (
          <button
            type="button"
            onClick={() => void handleCopyAndGuideIos()}
            className="btn-primary bounce-click inapp-banner-cta"
          >
            <span>{isIos ? "주소 복사하기" : "Chrome으로 열기"}</span>
            <ExternalLink size={13} aria-hidden="true" />
          </button>
        )}

        <button
          type="button"
          onClick={handleDismiss}
          aria-label="외부 브라우저 안내 닫기"
          className="banner-close-btn inapp-banner-close"
          style={{ color: "var(--warning-text)" }}
        >
          <X size={15} aria-hidden="true" />
        </button>
      </div>
    </aside>
  );
}
