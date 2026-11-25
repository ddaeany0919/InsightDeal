"use client";

type TossHostWindow = Window & {
  __appsInTossConstants?: Record<string, unknown>;
  __appsInToss?: {
    ads?: {
      initialize?: { isSupported?: () => boolean };
      attachBanner?: { isSupported?: () => boolean };
    };
  };
};

export type TossEventParams = Record<
  string,
  string | number | boolean | null | undefined
>;

export type TossSdkModule = typeof import("@apps-in-toss/web-framework");

let sdkPromise: Promise<TossSdkModule | null> | null = null;
let tossAdsInitPromise: Promise<TossSdkModule | null> | null = null;

const REVIEW_SESSION_KEY = "insightdeal_toss_review_prompted";
const REVIEW_COOLDOWN_KEY = "insightdeal_toss_review_prompted_at";
const REVIEW_COOLDOWN_MS = 7 * 24 * 60 * 60 * 1000;
const TOSS_ADS_INIT_TIMEOUT_MS = 8000;

export const TOSS_HOME_BANNER_AD_GROUP_ID = (
  process.env.NEXT_PUBLIC_TOSS_BANNER_AD_GROUP_ID ||
  "ait.v2.live.b3a879ee2cbd4df8"
).trim();

/**
 * 실제 토스 앱(Apps in Toss 호스트 WebView) 브릿지가 주입된 런타임인지 판별합니다.
 * 일반 모바일/데스크톱 웹(insightdeal.vercel.app)에서는 항상 false를 반환하여
 * 불필요한 번들 로드나 브릿지 예외를 원천 차단합니다.
 */
export function hasTossBridge(): boolean {
  if (typeof window === "undefined") return false;
  return Boolean((window as TossHostWindow).__appsInTossConstants);
}

/**
 * 토스 인앱 광고(TossAds) 호스트 객체가 주입된 런타임인지 판별합니다.
 */
export function hasTossAdsHost(): boolean {
  if (typeof window === "undefined") return false;
  return Boolean((window as TossHostWindow).__appsInToss?.ads);
}

/**
 * 토스 미니앱 빌드 또는 토스 앱 내 실행 환경인지 판별합니다.
 * (타 앱스토어 유도 버튼 숨김 등 미니앱 검수 정책 준수용)
 */
export function isTossMiniAppEnv(): boolean {
  if (process.env.NEXT_PUBLIC_TOSS_MINIAPP === "true") return true;
  if (typeof window === "undefined") return false;
  if (hasTossBridge()) return true;
  const ua = typeof navigator !== "undefined" ? navigator.userAgent || "" : "";
  return (
    ua.includes("TossApp") ||
    window.location.hostname.includes("tossmini") ||
    window.location.hostname.includes("apps-in-toss")
  );
}

function loadTossSdk(): Promise<TossSdkModule | null> {
  if (!hasTossBridge() && !hasTossAdsHost()) return Promise.resolve(null);
  if (!sdkPromise) {
    sdkPromise = import("@apps-in-toss/web-framework").catch(() => {
      sdkPromise = null;
      return null;
    });
  }
  return sdkPromise;
}

/**
 * 토스 인앱 광고 SDK(TossAds)를 세션당 1회만 싱글톤으로 초기화합니다.
 * 미지원·실패·타임아웃 시 null을 반환하고 캐시를 비워 다음 시도에서 복구할 수 있게 합니다.
 */
export function ensureTossAdsInitialized(): Promise<TossSdkModule | null> {
  if (!hasTossAdsHost()) return Promise.resolve(null);
  if (tossAdsInitPromise) return tossAdsInitPromise;

  const pending = (async (): Promise<TossSdkModule | null> => {
    const sdk = await loadTossSdk();
    const ads = sdk?.TossAds;
    if (
      !ads ||
      !ads.initialize?.isSupported?.() ||
      !ads.attachBanner?.isSupported?.()
    ) {
      tossAdsInitPromise = null;
      return null;
    }

    return await new Promise<TossSdkModule | null>((resolve) => {
      let settled = false;
      const timerId = window.setTimeout(() => {
        if (settled) return;
        settled = true;
        tossAdsInitPromise = null;
        resolve(null);
      }, TOSS_ADS_INIT_TIMEOUT_MS);

      const finish = (result: TossSdkModule | null) => {
        if (settled) return;
        settled = true;
        window.clearTimeout(timerId);
        if (!result) {
          tossAdsInitPromise = null;
        }
        resolve(result);
      };

      try {
        ads.initialize({
          callbacks: {
            onInitialized: () => finish(sdk),
            onInitializationFailed: () => finish(null),
          },
        });
      } catch {
        finish(null);
      }
    });
  })();

  tossAdsInitPromise = pending;
  return pending;
}

/**
 * 테스트 격리를 위해 TossAds 초기화 싱글톤 캐시를 비웁니다.
 */
export function resetTossAdsInitState(): void {
  tossAdsInitPromise = null;
  sdkPromise = null;
}

function sanitizeLogParams(
  logName: string,
  params?: TossEventParams
): Record<string, string | number | boolean> {
  const cleaned: Record<string, string | number | boolean> = {
    log_name: logName.slice(0, 64),
  };
  if (!params) return cleaned;

  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null) continue;
    if (typeof value === "string") {
      cleaned[key] = value.slice(0, 64);
    } else if (typeof value === "number" && Number.isFinite(value)) {
      cleaned[key] = value;
    } else if (typeof value === "boolean") {
      cleaned[key] = value;
    }
  }
  return cleaned;
}

/**
 * 토스 콘솔 [분석 > 이벤트] 및 활성 지표 집계용 화면 진입 로그를 전송합니다.
 */
export function logTossScreen(
  logName: string,
  params?: TossEventParams
): void {
  if (!hasTossBridge()) return;
  const payload = sanitizeLogParams(logName, params);
  void loadTossSdk().then((sdk) => {
    if (!sdk) return;
    try {
      sdk.Analytics.screen(payload)?.catch?.(() => {});
    } catch {}
  });
}

/**
 * 토스 콘솔 [분석 > 핵심 지표] 전환 지표 집계용 클릭/액션 로그를 전송합니다.
 */
export function logTossClick(
  logName: string,
  params?: TossEventParams
): void {
  if (!hasTossBridge()) return;
  const payload = sanitizeLogParams(logName, params);
  void loadTossSdk().then((sdk) => {
    if (!sdk) return;
    try {
      sdk.Analytics.click(payload)?.catch?.(() => {});
    } catch {}
  });
}

/**
 * 사용자가 알림 설정·찜하기 등 긍정적인 인앱 핵심 액션을 완료한 직후에만
 * 세션당 최대 1회(7일 쿨다운) 비차단 방식으로 토스 미니앱 리뷰 시트를 호출합니다.
 */
export function maybeRequestTossReview(): void {
  if (!hasTossBridge() || typeof window === "undefined") return;
  try {
    if (sessionStorage.getItem(REVIEW_SESSION_KEY) === "1") return;
    const lastPrompted = Number(
      localStorage.getItem(REVIEW_COOLDOWN_KEY) || "0"
    );
    const now = Date.now();
    if (lastPrompted > 0 && now - lastPrompted < REVIEW_COOLDOWN_MS) return;

    sessionStorage.setItem(REVIEW_SESSION_KEY, "1");
    localStorage.setItem(REVIEW_COOLDOWN_KEY, String(now));
  } catch {
    return;
  }

  window.setTimeout(() => {
    void loadTossSdk().then((sdk) => {
      if (!sdk) return;
      try {
        if (sdk.Review?.request?.isSupported?.()) {
          sdk.Review.request().catch(() => {});
        }
      } catch {}
    });
  }, 650);
}

/**
 * 토스 미니앱 내부에서 외부 쇼핑몰/커뮤니티 링크를 클릭할 때
 * Device.openURL을 통해 기기 기본 브라우저나 쇼핑몰 네이티브 앱으로 자연스럽게 연결합니다.
 * 일반 웹 브라우저에서는 false를 반환하여 기본 <a target="_blank"> 동작을 유지합니다.
 */
export function openExternalUrlInToss(
  url: string | undefined | null,
  event?: { preventDefault: () => void }
): boolean {
  if (!url || url === "#" || !/^https?:\/\//i.test(url)) return false;
  if (!hasTossBridge()) return false;

  event?.preventDefault();
  void loadTossSdk().then((sdk) => {
    if (!sdk) {
      window.open(url, "_blank", "noopener");
      return;
    }
    try {
      sdk.Device.openURL(url).catch(() => {
        window.open(url, "_blank", "noopener");
      });
    } catch {
      window.open(url, "_blank", "noopener");
    }
  });
  return true;
}

/**
 * 토스 미니앱 클립보드 권한 브릿지(Clipboard.setText)와 표준 웹 Clipboard API를
 * 순차 폴백하여 프로모션 할인코드를 안전하게 복사합니다.
 */
export async function copyTextSafe(text: string): Promise<boolean> {
  if (!text || typeof window === "undefined") return false;
  if (hasTossBridge()) {
    const sdk = await loadTossSdk();
    if (sdk?.Clipboard?.setText) {
      try {
        await sdk.Clipboard.setText(text);
        return true;
      } catch {}
    }
  }
  if (typeof navigator !== "undefined" && navigator.clipboard?.writeText) {
    try {
      await navigator.clipboard.writeText(text);
      return true;
    } catch {}
  }
  return false;
}

export interface ShareDealInTossParams {
  dealId: number | string;
  title: string;
  priceText: string;
  shippingFee?: string | null;
  shopName?: string | null;
  ogImageUrl?: string | null;
}

export type TossShareOutcome = "shared" | "cancelled" | "unsupported" | "fallback";

type LegacyTossShareModule = TossSdkModule & {
  getTossShareLink?: (path: string, ogImageUrl?: string) => Promise<string>;
  share?: (params: { message: string }) => Promise<void>;
};

/**
 * 토스 미니앱 내부에서 Share.createLink(`intoss://insightdeal?dealId=...`)와
 * Share.sendMessage를 호출해 수신자가 토스 앱으로 즉시 진입할 수 있는 공유 시트를 엽니다.
 */
export async function shareDealInToss({
  dealId,
  title,
  priceText,
  shippingFee,
  shopName,
  ogImageUrl,
}: ShareDealInTossParams): Promise<TossShareOutcome> {
  if (!hasTossBridge()) return "unsupported";
  const sdk = (await loadTossSdk()) as LegacyTossShareModule | null;
  if (!sdk) return "fallback";

  const createLinkFn = sdk.Share?.createLink
    ? (path: string, ogImg?: string) => sdk.Share.createLink({ path, ogImageUrl: ogImg })
    : sdk.getTossShareLink;
  const sendMessageFn = sdk.Share?.sendMessage
    ? (message: string) => sdk.Share.sendMessage({ message })
    : sdk.share
      ? (message: string) => sdk.share!({ message })
      : null;

  if (!createLinkFn || !sendMessageFn) return "fallback";

  try {
    const deepLink = `intoss://insightdeal?dealId=${encodeURIComponent(String(dealId))}`;
    const validOgImg =
      ogImageUrl && /^https?:\/\//i.test(ogImageUrl) ? ogImageUrl : undefined;
    const tossShareUrl = await createLinkFn(deepLink, validOgImg);
    if (!tossShareUrl) return "fallback";

    const metaParts = [priceText, shippingFee?.trim(), shopName?.trim()].filter(Boolean);
    const metaLine = metaParts.join(" · ");
    const message = [
      `🔥 ${title}`,
      metaLine ? `${metaLine}` : null,
      "토스 '얼만데'에서 가격 추이·품절 여부 바로 보기 👇",
      tossShareUrl,
    ]
      .filter(Boolean)
      .join("\n");

    await sendMessageFn(message);
    logTossClick("share_deal", {
      deal_id: Number(dealId) || 0,
      channel: "toss_native",
    });
    return "shared";
  } catch (err) {
    if (err instanceof Error && /abort|cancel/i.test(err.name + " " + err.message)) {
      return "cancelled";
    }
    return "fallback";
  }
}

/**
 * URL 파라미터 디버그 모드(`?toss=1`)로 접근한 경우 내부 라우트 이동 시에도 `?toss=1`을 유지합니다.
 */
export function appendTossDebugQuery(path: string): string {
  if (typeof window === "undefined") return path;
  try {
    const currentParams = new URLSearchParams(window.location.search);
    if (currentParams.get("toss") !== "1") return path;
    const [baseAndQuery, hash = ""] = path.split("#");
    const [pathname, search = ""] = baseAndQuery.split("?");
    const nextParams = new URLSearchParams(search);
    nextParams.set("toss", "1");
    const qs = nextParams.toString();
    return `${pathname}${qs ? `?${qs}` : ""}${hash ? `#${hash}` : ""}`;
  } catch {
    return path;
  }
}
