const ALLOWED_HOSTS = [
  "ppomppu.co.kr",
  "quasarzone.com",
  "fmkorea.com",
  "ruliweb.com",
  "clien.net",
  "bbasak.com",
  "auction.co.kr",
  "gmarket.co.kr",
  "11st.co.kr",
  "bnkrmall.co.kr",
];

const MAX_REDIRECT_HOPS = 3;
const HMAC_TOLERANCE_SECONDS = 300;
const RELAY_PATHS = new Set(["/", "/api/scraper-relay"]);

class RelayForbiddenError extends Error {
  /** @param {string} message */
  constructor(message) {
    super(message);
    this.name = "RelayForbiddenError";
  }
}

/**
 * @param {unknown} data
 * @param {number} status
 */
function jsonResponse(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "Content-Type": "application/json; charset=utf-8",
      "Cache-Control": "no-store, max-age=0",
    },
  });
}

/** @param {Record<string, unknown> | undefined} env */
function getRelaySecret(env) {
  return String(
    env?.FEEDER_API_TOKEN || env?.SCRAPER_RELAY_SECRET || ""
  ).trim();
}

/** @param {Record<string, unknown> | undefined} env */
function getDelegateBackendBaseUrl(env) {
  const raw = String(
    env?.BACKEND_URL ||
      env?.BACKEND_API_URL ||
      "https://34.59.208.14.sslip.io"
  ).trim();
  return raw.replace(/\/+$/, "");
}

/** @param {string} hostname */
export function isAllowedRelayHost(hostname) {
  const normalized = String(hostname || "").toLowerCase();
  return ALLOWED_HOSTS.some(
    (h) => normalized === h || normalized.endsWith(`.${h}`)
  );
}

/** @param {string} providedToken */
function parseHmacParts(providedToken) {
  const parts = String(providedToken || "").split(".");
  if (parts.length !== 3 || parts[0] !== "v1") return null;
  const tsStr = parts[1];
  const sigHex = parts[2];
  if (!/^(?:0|[1-9]\d*)$/.test(tsStr) || !/^[0-9a-f]{64}$/i.test(sigHex)) {
    return null;
  }
  const ts = Number(tsStr);
  if (!Number.isSafeInteger(ts)) return null;
  const nowSec = Math.floor(Date.now() / 1000);
  if (Math.abs(nowSec - ts) > HMAC_TOLERANCE_SECONDS) {
    return null;
  }
  return { ts, sigHex: sigHex.toLowerCase() };
}

/** @param {string} hex */
function hexToBytes(hex) {
  const bytes = new Uint8Array(hex.length / 2);
  for (let i = 0; i < hex.length; i += 2) {
    bytes[i / 2] = parseInt(hex.slice(i, i + 2), 16);
  }
  return bytes;
}

/**
 * @param {Uint8Array} a
 * @param {Uint8Array} b
 */
function constantTimeEqual(a, b) {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) {
    diff |= a[i] ^ b[i];
  }
  return diff === 0;
}

/**
 * X-Feeder-Token 엄격 HMAC 검증 (`v1.<unix_ts>.<hmac_sha256_hex>`)
 * 서명 원문: `${unix_ts}:${decoded_target_url}`
 * @param {string} providedToken
 * @param {string} secret
 * @param {string} targetUrl
 * @returns {Promise<boolean>}
 */
export async function verifyFeederHmacToken(providedToken, secret, targetUrl) {
  if (!providedToken || !secret) return false;
  const parsed = parseHmacParts(providedToken);
  if (!parsed) return false;

  const subtle = globalThis.crypto?.subtle;
  if (!subtle) return false;

  const enc = new TextEncoder();
  const key = await subtle.importKey(
    "raw",
    enc.encode(secret),
    { name: "HMAC", hash: "SHA-256" },
    false,
    ["sign"]
  );
  const signature = await subtle.sign(
    "HMAC",
    key,
    enc.encode(`${parsed.ts}:${targetUrl}`)
  );
  const expectedBytes = new Uint8Array(signature);
  const providedBytes = hexToBytes(parsed.sigHex);
  return constantTimeEqual(providedBytes, expectedBytes);
}

/**
 * @param {string} initialUrl
 * @param {RequestInit} init
 */
async function fetchWithAllowedRedirects(initialUrl, init) {
  let currentUrl = initialUrl;

  for (let hop = 0; hop <= MAX_REDIRECT_HOPS; hop++) {
    const parsed = new URL(currentUrl);
    if (
      !/^https?:$/i.test(parsed.protocol) ||
      !isAllowedRelayHost(parsed.hostname)
    ) {
      throw new RelayForbiddenError("Redirect host not allowed");
    }

    const res = await fetch(currentUrl, {
      ...init,
      redirect: "manual",
    });

    const isRedirect = res.status >= 300 && res.status < 400;
    if (!isRedirect) {
      return { response: res, finalUrl: parsed };
    }

    const location = res.headers.get("location");
    if (!location) {
      return { response: res, finalUrl: parsed };
    }

    const nextUrl = new URL(location, parsed);
    if (
      !/^https?:$/i.test(nextUrl.protocol) ||
      !isAllowedRelayHost(nextUrl.hostname)
    ) {
      throw new RelayForbiddenError("Redirect target host not allowed");
    }
    currentUrl = nextUrl.toString();
  }

  throw new Error("Too many redirects");
}

/**
 * @param {ArrayBuffer} arrayBuffer
 * @param {string} contentType
 * @param {string} hostname
 */
function decodeResponseBody(arrayBuffer, contentType, hostname) {
  const lowerCt = String(contentType || "").toLowerCase();
  const lowerHost = String(hostname || "").toLowerCase();
  if (
    lowerCt.includes("euc-kr") ||
    lowerCt.includes("cp949") ||
    lowerHost.includes("ppomppu.co.kr")
  ) {
    try {
      const temp = new TextDecoder("euc-kr").decode(arrayBuffer);
      if (
        temp.includes("charset=utf-8") ||
        temp.includes('charset="utf-8"') ||
        temp.includes('encoding="UTF-8"') ||
        temp.includes("encoding='UTF-8'")
      ) {
        return new TextDecoder("utf-8").decode(arrayBuffer);
      }
      return temp;
    } catch {
      return new TextDecoder("utf-8").decode(arrayBuffer);
    }
  }
  return new TextDecoder("utf-8").decode(arrayBuffer);
}

const worker = {
  /**
   * @param {Request} request
   * @param {Record<string, unknown>} [env]
   */
  async fetch(request, env = {}) {
    const reqUrl = new URL(request.url);
    if (request.method === "GET" && reqUrl.pathname === "/health") {
      return jsonResponse({ ok: true, service: "insightdeal-relay" }, 200);
    }

    if (request.method !== "GET" || !RELAY_PATHS.has(reqUrl.pathname)) {
      return jsonResponse({ error: "Not found" }, 404);
    }

    // 1. 시크릿 미설정 & 백엔드 위임 불가 시 즉시 503 Fail-Closed
    const secret = getRelaySecret(env);
    const backendUrl = getDelegateBackendBaseUrl(env);
    const canDelegateToBackend = !secret && Boolean(backendUrl);

    if (!secret && !canDelegateToBackend) {
      return jsonResponse(
        { error: "Relay authentication secret is not configured" },
        503
      );
    }

    // 2. X-Feeder-Token 헤더 확인
    const providedToken = (request.headers.get("x-feeder-token") || "").trim();
    if (!providedToken) {
      return jsonResponse({ error: "Missing feeder authentication token" }, 401);
    }

    // 3. targetUrl 및 허용 도메인 검증
    const targetUrl = reqUrl.searchParams.get("url");
    if (!targetUrl || !/^https?:\/\//i.test(targetUrl)) {
      return jsonResponse({ error: "Invalid url" }, 400);
    }

    /** @type {URL} */
    let parsedTarget;
    try {
      parsedTarget = new URL(targetUrl);
    } catch {
      return jsonResponse({ error: "Invalid url" }, 400);
    }

    if (!isAllowedRelayHost(parsedTarget.hostname)) {
      return jsonResponse({ error: "Host not allowed" }, 403);
    }

    // 4. 엄격 HMAC 검증 (로컬 시크릿 우선, 미설정 시 백엔드 위임)
    if (secret) {
      const valid = await verifyFeederHmacToken(providedToken, secret, targetUrl);
      if (!valid) {
        return jsonResponse({ error: "Invalid or expired feeder token" }, 401);
      }
    } else {
      if (!parseHmacParts(providedToken)) {
        return jsonResponse({ error: "Invalid or expired feeder token" }, 401);
      }
      try {
        const verifyRes = await fetch(
          `${backendUrl}/api/community/verify-relay-token`,
          {
            method: "POST",
            headers: {
              "Content-Type": "application/json",
              "X-Feeder-Token": providedToken,
            },
            body: JSON.stringify({ target_url: targetUrl }),
            signal: AbortSignal.timeout(4000),
          }
        );
        if (verifyRes.status === 401 || verifyRes.status === 403) {
          return jsonResponse(
            { error: "Invalid or expired feeder token" },
            verifyRes.status
          );
        }
        if (!verifyRes.ok) {
          return jsonResponse(
            { error: "Relay authentication secret is not configured" },
            503
          );
        }
      } catch {
        return jsonResponse(
          { error: "Relay authentication backend unavailable" },
          503
        );
      }
    }

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 9000);

    try {
      const baseHeaders = {
        "User-Agent":
          "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        Accept:
          "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language": "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7",
        Referer: parsedTarget.hostname.includes("ppomppu.co.kr")
          ? "https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu"
          : `${parsedTarget.protocol}//${parsedTarget.host}/`,
        "Sec-Ch-Ua":
          '"Chromium";v="124", "Google Chrome";v="124", "Not-A.Brand";v="99"',
        "Sec-Ch-Ua-Mobile": "?0",
        "Sec-Ch-Ua-Platform": '"Windows"',
        "Upgrade-Insecure-Requests": "1",
      };

      const { response: res, finalUrl } = await fetchWithAllowedRedirects(
        targetUrl,
        {
          headers: baseHeaders,
          signal: controller.signal,
        }
      );

      const arrayBuffer = await res.arrayBuffer();
      const contentType = res.headers.get("content-type") || "text/html";
      const decoded = decodeResponseBody(
        arrayBuffer,
        contentType,
        finalUrl.hostname
      );

      return new Response(decoded, {
        status: res.status,
        headers: {
          "Content-Type": "text/html; charset=utf-8",
          "Cache-Control": "no-store, max-age=0",
        },
      });
    } catch (err) {
      if (err instanceof RelayForbiddenError) {
        return jsonResponse({ error: err.message }, 403);
      }
      const message = err instanceof Error ? err.message : "Relay fetch failed";
      return jsonResponse({ error: message }, 502);
    } finally {
      clearTimeout(timeout);
    }
  },
};

export default worker;
