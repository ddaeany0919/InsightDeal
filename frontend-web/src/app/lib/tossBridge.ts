"use client";

type TossHostWindow = Window & {
  __appsInTossConstants?: Record<string, unknown>;
};

export type TossEventParams = Record<
  string,
  string | number | boolean | null | undefined
>;

type TossSdkModule = typeof import("@apps-in-toss/web-framework");

let sdkPromise: Promise<TossSdkModule | null> | null = null;

const REVIEW_SESSION_KEY = "insightdeal_toss_review_prompted";
const REVIEW_COOLDOWN_KEY = "insightdeal_toss_review_prompted_at";
const REVIEW_COOLDOWN_MS = 7 * 24 * 60 * 60 * 1000;

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
  if (!hasTossBridge()) return Promise.resolve(null);
  if (!sdkPromise) {
    sdkPromise = import("@apps-in-toss/web-framework").catch(() => null);
  }
  return sdkPromise;
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
