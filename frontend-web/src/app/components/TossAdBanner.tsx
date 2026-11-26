"use client";

import React, { useEffect, useRef, useState, useSyncExternalStore } from "react";
import {
  TOSS_HOME_BANNER_AD_GROUP_ID,
  ensureTossAdsInitialized,
  hasTossAdsHost,
} from "../lib/tossBridge";

const THEME_CHANGE_EVENT = "insightdeal-theme-change";

type BannerTheme = "auto" | "light" | "dark";
type SlotState = "loading" | "rendered" | "empty";

export interface TossAdBannerProps {
  adGroupId?: string;
  variant?: "card" | "expanded";
  tone?: "blackAndWhite" | "grey";
  className?: string;
}

function subscribeNoop(): () => void {
  return () => {};
}

function getTossAdsHostSnapshot(): boolean {
  return hasTossAdsHost();
}

function getServerFalseSnapshot(): boolean {
  return false;
}

function getBannerThemeSnapshot(): BannerTheme {
  if (typeof document === "undefined") return "auto";
  const theme = document.documentElement.dataset.theme;
  if (theme === "light") return "light";
  if (theme === "dark" || theme === "amoled") return "dark";
  return "auto";
}

function getServerBannerThemeSnapshot(): BannerTheme {
  return "auto";
}

function subscribeBannerTheme(callback: () => void): () => void {
  if (typeof window === "undefined") return () => {};
  const onThemeChange = () => callback();
  window.addEventListener(THEME_CHANGE_EVENT, onThemeChange);
  return () => {
    window.removeEventListener(THEME_CHANGE_EVENT, onThemeChange);
  };
}

/**
 * 토스 앱 인앱 브라우저(TossAds 호스트 주입 환경)에서만 렌더링되는 배너 광고 슬롯입니다.
 * 일반 웹 브라우저, SSR, 또는 No-Fill / 로드 실패 시 DOM을 비워 레이아웃 여백을 남기지 않습니다.
 */
function TossAdBannerBase({
  adGroupId = TOSS_HOME_BANNER_AD_GROUP_ID,
  variant = "card",
  tone = "blackAndWhite",
  className = "",
}: TossAdBannerProps) {
  const adHostAvailable = useSyncExternalStore(
    subscribeNoop,
    getTossAdsHostSnapshot,
    getServerFalseSnapshot
  );
  const bannerTheme = useSyncExternalStore(
    subscribeBannerTheme,
    getBannerThemeSnapshot,
    getServerBannerThemeSnapshot
  );

  const resolvedAdGroupId = adGroupId.trim();
  const [slotState, setSlotState] = useState<SlotState>("loading");
  const [isInViewport, setIsInViewport] = useState<boolean>(
    () => typeof IntersectionObserver !== "function"
  );
  const slotEmptyRef = useRef(false);
  const containerRef = useRef<HTMLDivElement | null>(null);
  const targetRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!adHostAvailable || !resolvedAdGroupId || isInViewport) {
      return;
    }
    if (typeof IntersectionObserver !== "function") {
      const id = window.setTimeout(() => setIsInViewport(true), 0);
      return () => window.clearTimeout(id);
    }
    const el = containerRef.current;
    if (!el) return;

    const observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting || entry.intersectionRatio > 0)) {
          setIsInViewport(true);
          observer.disconnect();
        }
      },
      { rootMargin: "120px 0px", threshold: 0 }
    );
    observer.observe(el);
    return () => observer.disconnect();
  }, [adHostAvailable, resolvedAdGroupId, isInViewport]);

  useEffect(() => {
    if (!adHostAvailable || !resolvedAdGroupId || !isInViewport || slotEmptyRef.current) {
      return;
    }

    let cancelled = false;
    let bannerHandle: { destroy: () => void } | null = null;

    const cleanupHandle = () => {
      bannerHandle?.destroy?.();
      bannerHandle = null;
    };

    void ensureTossAdsInitialized().then((sdk) => {
      if (cancelled || slotEmptyRef.current) return;
      const targetEl = targetRef.current;
      if (!sdk?.TossAds?.attachBanner || !targetEl) {
        slotEmptyRef.current = true;
        setSlotState("empty");
        return;
      }

      try {
        const handle = sdk.TossAds.attachBanner(resolvedAdGroupId, targetEl, {
          theme: bannerTheme,
          tone,
          variant,
          callbacks: {
            onAdRendered: () => {
              if (!cancelled && !slotEmptyRef.current) {
                setSlotState("rendered");
              }
            },
            onNoFill: () => {
              if (!cancelled) {
                slotEmptyRef.current = true;
                cleanupHandle();
                setSlotState("empty");
              }
            },
            onAdFailedToRender: () => {
              if (!cancelled) {
                slotEmptyRef.current = true;
                cleanupHandle();
                setSlotState("empty");
              }
            },
          },
        });
        if (cancelled || slotEmptyRef.current) {
          handle?.destroy?.();
          return;
        }
        bannerHandle = handle;
      } catch {
        if (!cancelled) {
          slotEmptyRef.current = true;
          cleanupHandle();
          setSlotState("empty");
        }
      }
    });

    return () => {
      cancelled = true;
      cleanupHandle();
    };
  }, [adHostAvailable, resolvedAdGroupId, isInViewport, variant, tone, bannerTheme]);

  if (!adHostAvailable || !resolvedAdGroupId || slotState === "empty") {
    return null;
  }

  const stateClass =
    slotState === "rendered"
      ? "toss-ad-banner-slot--rendered"
      : "toss-ad-banner-slot--loading";

  return (
    <div
      ref={containerRef}
      className={`toss-ad-banner-slot ${stateClass} ${className}`.trim()}
      role="region"
      aria-label="스폰서 광고"
      aria-hidden={slotState !== "rendered"}
      data-testid="toss-ad-banner"
      data-slot-state={slotState}
    >
      <div ref={targetRef} className="toss-ad-banner-target" />
    </div>
  );
}

const TossAdBanner = React.memo(TossAdBannerBase);
export default TossAdBanner;
