import crypto from "crypto";
import { NextRequest, NextResponse } from "next/server";

export const runtime = "nodejs";
export const preferredRegion = "icn1";

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

let cachedFmkoreaCookies = "";
let cachedMcwBytes: ArrayBuffer | null = null;

interface McwWasmExports extends WebAssembly.Exports {
  memory: WebAssembly.Memory;
  __wbindgen_malloc: (size: number, align: number) => number;
  fm5: (p0: number, l0: number, p1: number, l1: number) => void;
}

interface FakeWindowShape {
  document: { cookie: string };
  window: FakeWindowShape;
  self: FakeWindowShape;
}

function getRelaySecret(): string {
  return (
    process.env.FEEDER_API_TOKEN ||
    process.env.SCRAPER_RELAY_SECRET ||
    ""
  ).trim();
}

function isAllowedRelayHost(hostname: string): boolean {
  const normalized = hostname.toLowerCase();
  return ALLOWED_HOSTS.some(
    (h) => normalized === h || normalized.endsWith(`.${h}`)
  );
}

/**
 * X-Feeder-Token 엄격 HMAC 검증:
 * - 포맷: v1.<unix_ts>.<hmac_sha256_hex> (평문 토큰 폴백 불가)
 * - 서명 원문: `${unix_ts}:${decoded_target_url}`
 * - 허용 오차: ±300초, 상수 시간 비교(crypto.timingSafeEqual)
 */
function verifyFeederHmacToken(
  providedToken: string,
  secret: string,
  targetUrl: string
): boolean {
  if (!providedToken || !secret) return false;

  const parts = providedToken.split(".");
  if (parts.length !== 3 || parts[0] !== "v1") return false;

  const tsStr = parts[1];
  const sigHex = parts[2];
  if (!/^(?:0|[1-9]\d*)$/.test(tsStr) || !/^[0-9a-f]{64}$/i.test(sigHex)) {
    return false;
  }

  const ts = Number(tsStr);
  if (!Number.isSafeInteger(ts)) return false;

  const nowSec = Math.floor(Date.now() / 1000);
  if (Math.abs(nowSec - ts) > HMAC_TOLERANCE_SECONDS) {
    return false;
  }

  const payload = `${ts}:${targetUrl}`;
  const expectedDigest = crypto
    .createHmac("sha256", secret)
    .update(payload)
    .digest();
  const providedDigest = Buffer.from(sigHex.toLowerCase(), "hex");

  if (providedDigest.length !== expectedDigest.length) {
    return false;
  }
  return crypto.timingSafeEqual(providedDigest, expectedDigest);
}

async function fetchWithAllowedRedirects(
  initialUrl: string,
  init: RequestInit
): Promise<{ response: Response; finalUrl: URL }> {
  let currentUrl = initialUrl;

  for (let hop = 0; hop <= MAX_REDIRECT_HOPS; hop++) {
    const parsed = new URL(currentUrl);
    if (!/^https?:$/i.test(parsed.protocol) || !isAllowedRelayHost(parsed.hostname)) {
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
    if (!/^https?:$/i.test(nextUrl.protocol) || !isAllowedRelayHost(nextUrl.hostname)) {
      throw new RelayForbiddenError("Redirect target host not allowed");
    }
    currentUrl = nextUrl.toString();
  }

  throw new Error("Too many redirects");
}

class RelayForbiddenError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "RelayForbiddenError";
  }
}

async function solveFmkoreaWasmInNode(
  arg0: string,
  arg1: string,
  baseHeaders: Record<string, string>,
  signal: AbortSignal
): Promise<string[]> {
  if (!cachedMcwBytes) {
    const wasmRes = await fetch("https://www.fmkorea.com/mc/mcw.php", {
      headers: baseHeaders,
      cache: "no-store",
      signal,
    });
    if (!wasmRes.ok) return [];
    cachedMcwBytes = await wasmRes.arrayBuffer();
  }

  class FakeWindow {}
  const outCookies: string[] = [];
  const doc = {
    get cookie(): string {
      return outCookies.join("; ");
    },
    set cookie(v: string) {
      outCookies.push(v);
    },
  };
  const win = Object.create(FakeWindow.prototype) as FakeWindowShape;
  win.document = doc;
  win.window = win;
  win.self = win;

  const wasmRef: { current: McwWasmExports | null } = { current: null };
  const getWasm = (): McwWasmExports => {
    if (!wasmRef.current) {
      throw new Error("Wasm exports not initialized");
    }
    return wasmRef.current;
  };

  const heap: unknown[] = new Array(128).fill(undefined);
  heap.push(undefined, null, true, false);
  let heap_next = heap.length;
  const getObject = (idx: number): unknown => heap[idx];
  const dropObject = (idx: number) => {
    if (idx < 132) return;
    heap[idx] = heap_next;
    heap_next = idx;
  };
  const takeObject = (idx: number): unknown => {
    const ret = getObject(idx);
    dropObject(idx);
    return ret;
  };
  const addHeapObject = (obj: unknown): number => {
    if (heap_next === heap.length) heap.push(heap.length + 1);
    const idx = heap_next;
    heap_next = heap[idx] as number;
    heap[idx] = obj;
    return idx;
  };
  let WASM_VECTOR_LEN = 0;
  const getUint8Memory0 = () => new Uint8Array(getWasm().memory.buffer);
  const getInt32Memory0 = () => new Int32Array(getWasm().memory.buffer);
  const enc = new TextEncoder();
  const dec = new TextDecoder("utf-8", { ignoreBOM: true, fatal: true });
  const passStringToWasm0 = (
    arg: string,
    malloc: (size: number, align: number) => number
  ) => {
    const buf = enc.encode(arg);
    const ptr = malloc(buf.length, 1) >>> 0;
    getUint8Memory0()
      .subarray(ptr, ptr + buf.length)
      .set(buf);
    WASM_VECTOR_LEN = buf.length;
    return ptr;
  };
  const getStringFromWasm0 = (ptr: number, len: number) =>
    dec.decode(getUint8Memory0().subarray(ptr >>> 0, (ptr >>> 0) + len));

  const imports = {
    wbg: {
      __wbindgen_object_drop_ref: (a: number) => {
        takeObject(a);
      },
      __wbg_instanceof_Window_f401953a2cf86220: (a: number) =>
        getObject(a) instanceof FakeWindow,
      __wbg_document_5100775d18896c16: (a: number) =>
        addHeapObject((getObject(a) as FakeWindowShape).document),
      __wbg_setcookie_21ee60540965860c: (a: number, b: number, c: number) => {
        (getObject(a) as { cookie: string }).cookie = getStringFromWasm0(b, c);
      },
      __wbg_newnoargs_e258087cd0daa0ea: () => addHeapObject(() => win),
      __wbg_call_27c0f87801dedf93: (a: number, b: number) =>
        addHeapObject(
          (getObject(a) as (this: unknown) => unknown).call(getObject(b))
        ),
      __wbg_self_ce0dbfc45cf2f5be: () => addHeapObject(win),
      __wbg_window_c6fb939a7f436783: () => addHeapObject(win),
      __wbg_globalThis_d1e6af4856ba331b: () => addHeapObject(win),
      __wbg_global_207b558942527489: () => addHeapObject(win),
      __wbindgen_is_undefined: (a: number) => getObject(a) === undefined,
      __wbg_getDate_27ca688e44a3c7be: (a: number) =>
        (getObject(a) as Date).getDate(),
      __wbg_new0_7d84e5b2cd9fdc73: () => addHeapObject(new Date()),
      __wbg_setDate_80dcac1241d0bc50: (a: number, b: number) =>
        (getObject(a) as Date).setDate(b >>> 0),
      __wbg_toUTCString_aef4e727dedbd221: (a: number) =>
        addHeapObject((getObject(a) as Date).toUTCString()),
      __wbindgen_object_clone_ref: (a: number) => addHeapObject(getObject(a)),
      __wbindgen_string_get: (a: number, b: number) => {
        const obj = getObject(b);
        const ret = typeof obj === "string" ? obj : undefined;
        const ptr1 = !ret
          ? 0
          : passStringToWasm0(ret, getWasm().__wbindgen_malloc);
        getInt32Memory0()[a / 4 + 1] = WASM_VECTOR_LEN;
        getInt32Memory0()[a / 4 + 0] = ptr1;
      },
      __wbindgen_debug_string: (a: number, b: number) => {
        const ptr1 = passStringToWasm0(
          String(getObject(b)),
          getWasm().__wbindgen_malloc
        );
        getInt32Memory0()[a / 4 + 1] = WASM_VECTOR_LEN;
        getInt32Memory0()[a / 4 + 0] = ptr1;
      },
      __wbindgen_throw: (a: number, b: number) => {
        throw new Error(getStringFromWasm0(a, b));
      },
    },
  };

  const mod = new WebAssembly.Module(cachedMcwBytes);
  const inst = new WebAssembly.Instance(mod, imports);
  const wasmExports = inst.exports as unknown as McwWasmExports;
  wasmRef.current = wasmExports;
  const p0 = passStringToWasm0(arg0, wasmExports.__wbindgen_malloc);
  const l0 = WASM_VECTOR_LEN;
  const p1 = passStringToWasm0(arg1, wasmExports.__wbindgen_malloc);
  const l1 = WASM_VECTOR_LEN;
  wasmExports.fm5(p0, l0, p1, l1);
  return outCookies.map((c) => c.split(";")[0].trim());
}

function parseHmacShapeAndTimestamp(providedToken: string): boolean {
  const parts = providedToken.split(".");
  if (parts.length !== 3 || parts[0] !== "v1") return false;
  const tsStr = parts[1];
  const sigHex = parts[2];
  if (!/^(?:0|[1-9]\d*)$/.test(tsStr) || !/^[0-9a-f]{64}$/i.test(sigHex)) {
    return false;
  }
  const ts = Number(tsStr);
  if (!Number.isSafeInteger(ts)) return false;
  const nowSec = Math.floor(Date.now() / 1000);
  return Math.abs(nowSec - ts) <= HMAC_TOLERANCE_SECONDS;
}

function getDelegateBackendBaseUrl(): string {
  const raw =
    process.env.BACKEND_URL ||
    process.env.BACKEND_API_URL ||
    "https://34.59.208.14.sslip.io";
  return raw.replace(/\/+$/, "");
}

export async function GET(req: NextRequest) {
  // 1. 시크릿 미설정 & 백엔드 위임 불가 시 즉시 503 Fail-Closed
  const secret = getRelaySecret();
  const canDelegateToBackend =
    !secret &&
    (process.env.VERCEL === "1" ||
      Boolean(process.env.BACKEND_URL || process.env.BACKEND_API_URL));

  if (!secret && !canDelegateToBackend) {
    return NextResponse.json(
      { error: "Relay authentication secret is not configured" },
      { status: 503 }
    );
  }

  // 2. X-Feeder-Token 헤더 기본 형식 확인
  const providedToken = (req.headers.get("x-feeder-token") || "").trim();
  if (!providedToken) {
    return NextResponse.json(
      { error: "Missing feeder authentication token" },
      { status: 401 }
    );
  }

  // 3. targetUrl 검증 (searchParams.get은 URL-decoded 값을 반환)
  const targetUrl = req.nextUrl.searchParams.get("url");
  if (!targetUrl || !/^https?:\/\//i.test(targetUrl)) {
    return NextResponse.json({ error: "Invalid url" }, { status: 400 });
  }

  let parsed: URL;
  try {
    parsed = new URL(targetUrl);
  } catch {
    return NextResponse.json({ error: "Invalid url" }, { status: 400 });
  }

  if (!isAllowedRelayHost(parsed.hostname)) {
    return NextResponse.json({ error: "Host not allowed" }, { status: 403 });
  }

  // 4. 엄격 v1.<unix_ts>.<hmac_sha256_hex> 검증 (로컬 시크릿 우선, 미설정 시 백엔드 위임)
  if (secret) {
    if (!verifyFeederHmacToken(providedToken, secret, targetUrl)) {
      return NextResponse.json(
        { error: "Invalid or expired feeder token" },
        { status: 401 }
      );
    }
  } else {
    if (!parseHmacShapeAndTimestamp(providedToken)) {
      return NextResponse.json(
        { error: "Invalid or expired feeder token" },
        { status: 401 }
      );
    }
    try {
      const verifyRes = await fetch(
        `${getDelegateBackendBaseUrl()}/api/community/verify-relay-token`,
        {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            "X-Feeder-Token": providedToken,
          },
          body: JSON.stringify({ target_url: targetUrl }),
          signal: AbortSignal.timeout(4000),
          cache: "no-store",
        }
      );
      if (verifyRes.status === 401 || verifyRes.status === 403) {
        return NextResponse.json(
          { error: "Invalid or expired feeder token" },
          { status: verifyRes.status }
        );
      }
      if (!verifyRes.ok) {
        return NextResponse.json(
          { error: "Relay authentication secret is not configured" },
          { status: 503 }
        );
      }
    } catch {
      return NextResponse.json(
        { error: "Relay authentication backend unavailable" },
        { status: 503 }
      );
    }
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 9000);

  try {
    const baseHeaders: Record<string, string> = {
      "User-Agent":
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
      Accept:
        "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
      "Accept-Language": "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7",
      Referer: parsed.hostname.includes("ppomppu.co.kr")
        ? "https://www.ppomppu.co.kr/zboard/zboard.php?id=ppomppu"
        : `${parsed.protocol}//${parsed.host}/`,
      "Sec-Ch-Ua": '"Chromium";v="124", "Google Chrome";v="124", "Not-A.Brand";v="99"',
      "Sec-Ch-Ua-Mobile": "?0",
      "Sec-Ch-Ua-Platform": '"Windows"',
      "Sec-Fetch-Dest": "document",
      "Sec-Fetch-Mode": "navigate",
      "Sec-Fetch-Site": "same-origin",
      "Sec-Fetch-User": "?1",
      "Upgrade-Insecure-Requests": "1",
    };
    if (parsed.hostname.includes("fmkorea.com") && cachedFmkoreaCookies) {
      baseHeaders["Cookie"] = cachedFmkoreaCookies;
    }

    let { response: res } = await fetchWithAllowedRedirects(targetUrl, {
      headers: baseHeaders,
      signal: controller.signal,
      cache: "no-store",
    });

    if (res.status === 430 && parsed.hostname.includes("fmkorea.com")) {
      const challengeHtml = await res.text();
      const m = challengeHtml.match(/fm5\('([^']+)',\s*'([^']+)'\)/);
      if (m) {
        const arg0 = m[1];
        const arg1 = m[2];
        const headersWithSetCookie = res.headers as Headers & {
          getSetCookie?: () => string[];
        };
        const setCookieHeaders =
          typeof headersWithSetCookie.getSetCookie === "function"
            ? headersWithSetCookie.getSetCookie()
            : [res.headers.get("set-cookie") || ""];
        const respCookies = setCookieHeaders
          .filter(Boolean)
          .map((c: string) => c.split(";")[0].trim());
        const wasmCookies = await solveFmkoreaWasmInNode(
          arg0,
          arg1,
          baseHeaders,
          controller.signal
        );
        const allCookieParts = [
          ...respCookies,
          `lite_year=${arg0}`,
          `g_lite_year=${arg0}`,
          ...wasmCookies,
        ];
        cachedFmkoreaCookies = Array.from(new Set(allCookieParts)).join("; ");

        // Submit POST adcForm (is_dlc=1) + retry GET with Wasm cookies
        await fetch(targetUrl, {
          method: "POST",
          headers: {
            ...baseHeaders,
            "Content-Type": "application/x-www-form-urlencoded",
            Cookie: cachedFmkoreaCookies,
          },
          body: "is_dlc=1",
          redirect: "manual",
          signal: controller.signal,
          cache: "no-store",
        }).catch(() => null);

        const retryResult = await fetchWithAllowedRedirects(targetUrl, {
          headers: {
            ...baseHeaders,
            Cookie: cachedFmkoreaCookies,
          },
          signal: controller.signal,
          cache: "no-store",
        });
        res = retryResult.response;
      }
    }

    const arrayBuffer = await res.arrayBuffer();
    const contentType = res.headers.get("content-type") || "text/html";
    let decoded = "";
    if (
      contentType.toLowerCase().includes("euc-kr") ||
      contentType.toLowerCase().includes("cp949") ||
      parsed.hostname.includes("ppomppu.co.kr")
    ) {
      try {
        const eucDecoder = new TextDecoder("euc-kr");
        const temp = eucDecoder.decode(arrayBuffer);
        if (
          temp.includes("charset=utf-8") ||
          temp.includes('charset="utf-8"') ||
          temp.includes('encoding="UTF-8"') ||
          temp.includes("encoding='UTF-8'")
        ) {
          decoded = new TextDecoder("utf-8").decode(arrayBuffer);
        } else {
          decoded = temp;
        }
      } catch {
        decoded = new TextDecoder("utf-8").decode(arrayBuffer);
      }
    } else {
      decoded = new TextDecoder("utf-8").decode(arrayBuffer);
    }

    return new NextResponse(decoded, {
      status: res.status,
      headers: {
        "Content-Type": "text/html; charset=utf-8",
        "Cache-Control": "no-store, max-age=0",
      },
    });
  } catch (err: unknown) {
    if (err instanceof RelayForbiddenError) {
      return NextResponse.json({ error: err.message }, { status: 403 });
    }
    const message = err instanceof Error ? err.message : "Relay fetch failed";
    return NextResponse.json({ error: message }, { status: 502 });
  } finally {
    clearTimeout(timeout);
  }
}
