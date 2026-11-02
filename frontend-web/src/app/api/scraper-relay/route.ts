import { NextRequest, NextResponse } from "next/server";

export const runtime = "nodejs";
export const preferredRegion = ["icn1", "hnd1"];

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

    const res = await fetch(targetUrl, {
      headers: {
        "User-Agent":
          "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
        Accept:
          "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language": "ko-KR,ko;q=0.9,en-US;q=0.8,en;q=0.7",
        Referer: `${parsed.protocol}//${parsed.host}/`,
      },
      redirect: "follow",
      signal: controller.signal,
      cache: "no-store",
    });
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
        if (temp.includes("charset=utf-8") || temp.includes('charset="utf-8"')) {
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
