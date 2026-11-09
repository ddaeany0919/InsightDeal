import { NextRequest, NextResponse } from "next/server";

export const runtime = "nodejs";
export const preferredRegion = "icn1";

let cachedFmkoreaCookies = "";
let cachedMcwBytes: ArrayBuffer | null = null;

async function solveFmkoreaWasmInNode(
  arg0: string,
  arg1: string,
  baseHeaders: Record<string, string>
): Promise<string[]> {
  if (!cachedMcwBytes) {
    const wasmRes = await fetch("https://www.fmkorea.com/mc/mcw.php", {
      headers: baseHeaders,
      cache: "no-store",
    });
    if (!wasmRes.ok) return [];
    cachedMcwBytes = await wasmRes.arrayBuffer();
  }

  class FakeWindow {}
  const outCookies: string[] = [];
  const doc = {
    set cookie(v: string) {
      outCookies.push(v);
    },
  };
  const win: any = Object.create(FakeWindow.prototype);
  win.document = doc;
  win.window = win;
  win.self = win;

  let wasm: any;
  const heap: any[] = new Array(128).fill(undefined);
  heap.push(undefined, null, true, false);
  let heap_next = heap.length;
  const getObject = (idx: number) => heap[idx];
  const dropObject = (idx: number) => {
    if (idx < 132) return;
    heap[idx] = heap_next;
    heap_next = idx;
  };
  const takeObject = (idx: number) => {
    const ret = getObject(idx);
    dropObject(idx);
    return ret;
  };
  const addHeapObject = (obj: any) => {
    if (heap_next === heap.length) heap.push(heap.length + 1);
    const idx = heap_next;
    heap_next = heap[idx];
    heap[idx] = obj;
    return idx;
  };
  let WASM_VECTOR_LEN = 0;
  const getUint8Memory0 = () => new Uint8Array(wasm.memory.buffer);
  const getInt32Memory0 = () => new Int32Array(wasm.memory.buffer);
  const enc = new TextEncoder();
  const dec = new TextDecoder("utf-8", { ignoreBOM: true, fatal: true });
  const passStringToWasm0 = (arg: string, malloc: Function) => {
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
      __wbindgen_object_drop_ref: (a: number) => takeObject(a),
      __wbg_instanceof_Window_f401953a2cf86220: (a: number) =>
        getObject(a) instanceof FakeWindow,
      __wbg_document_5100775d18896c16: (a: number) =>
        addHeapObject(getObject(a).document),
      __wbg_setcookie_21ee60540965860c: (a: number, b: number, c: number) => {
        getObject(a).cookie = getStringFromWasm0(b, c);
      },
      __wbg_newnoargs_e258087cd0daa0ea: () => addHeapObject(() => win),
      __wbg_call_27c0f87801dedf93: (a: number, b: number) =>
        addHeapObject(getObject(a).call(getObject(b))),
      __wbg_self_ce0dbfc45cf2f5be: () => addHeapObject(win),
      __wbg_window_c6fb939a7f436783: () => addHeapObject(win),
      __wbg_globalThis_d1e6af4856ba331b: () => addHeapObject(win),
      __wbg_global_207b558942527489: () => addHeapObject(win),
      __wbindgen_is_undefined: (a: number) => getObject(a) === undefined,
      __wbg_getDate_27ca688e44a3c7be: (a: number) => getObject(a).getDate(),
      __wbg_new0_7d84e5b2cd9fdc73: () => addHeapObject(new Date()),
      __wbg_setDate_80dcac1241d0bc50: (a: number, b: number) =>
        getObject(a).setDate(b >>> 0),
      __wbg_toUTCString_aef4e727dedbd221: (a: number) =>
        addHeapObject(getObject(a).toUTCString()),
      __wbindgen_object_clone_ref: (a: number) => addHeapObject(getObject(a)),
      __wbindgen_string_get: (a: number, b: number) => {
        const obj = getObject(b);
        const ret = typeof obj === "string" ? obj : undefined;
        const ptr1 = !ret ? 0 : passStringToWasm0(ret, wasm.__wbindgen_malloc);
        getInt32Memory0()[a / 4 + 1] = WASM_VECTOR_LEN;
        getInt32Memory0()[a / 4 + 0] = ptr1;
      },
      __wbindgen_debug_string: (a: number, b: number) => {
        const ptr1 = passStringToWasm0(
          String(getObject(b)),
          wasm.__wbindgen_malloc
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
  wasm = inst.exports;
  const p0 = passStringToWasm0(arg0, wasm.__wbindgen_malloc);
  const l0 = WASM_VECTOR_LEN;
  const p1 = passStringToWasm0(arg1, wasm.__wbindgen_malloc);
  const l1 = WASM_VECTOR_LEN;
  wasm.fm5(p0, l0, p1, l1);
  return outCookies.map((c) => c.split(";")[0].trim());
}

export async function GET(req: NextRequest) {
  const targetUrl = req.nextUrl.searchParams.get("url");
  if (!targetUrl || !/^https?:\/\//i.test(targetUrl)) {
    return NextResponse.json({ error: "Invalid url" }, { status: 400 });
  }

  const allowedHosts = [
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
  try {
    const parsed = new URL(targetUrl);
    const isAllowed = allowedHosts.some(
      (h) => parsed.hostname === h || parsed.hostname.endsWith(`.${h}`)
    );
    if (!isAllowed) {
      return NextResponse.json({ error: "Host not allowed" }, { status: 403 });
    }

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 9000);

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

    let res = await fetch(targetUrl, {
      headers: baseHeaders,
      redirect: "follow",
      signal: controller.signal,
      cache: "no-store",
    });

    if (res.status === 430 && parsed.hostname.includes("fmkorea.com")) {
      const challengeHtml = await res.text();
      const m = challengeHtml.match(/fm5\('([^']+)',\s*'([^']+)'\)/);
      if (m) {
        const arg0 = m[1];
        const arg1 = m[2];
        const setCookieHeaders =
          typeof (res.headers as any).getSetCookie === "function"
            ? (res.headers as any).getSetCookie()
            : [res.headers.get("set-cookie") || ""];
        const respCookies = setCookieHeaders
          .filter(Boolean)
          .map((c: string) => c.split(";")[0].trim());
        const wasmCookies = await solveFmkoreaWasmInNode(
          arg0,
          arg1,
          baseHeaders
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
          cache: "no-store",
        }).catch(() => null);

        res = await fetch(targetUrl, {
          headers: {
            ...baseHeaders,
            Cookie: cachedFmkoreaCookies,
          },
          redirect: "follow",
          cache: "no-store",
        });
      }
    }
    clearTimeout(timeout);

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
  } catch (err: any) {
    return NextResponse.json(
      { error: err?.message || "Relay fetch failed" },
      { status: 502 }
    );
  }
}
