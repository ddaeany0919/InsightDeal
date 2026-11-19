import crypto from "crypto";
import { NextRequest } from "next/server";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { GET } from "@/app/api/scraper-relay/route";

function signRelayToken(secret: string, ts: number, targetUrl: string): string {
  const hex = crypto
    .createHmac("sha256", secret)
    .update(`${ts}:${targetUrl}`)
    .digest("hex");
  return `v1.${ts}.${hex}`;
}

describe("GET /api/scraper-relay HMAC 인증 및 호스트 가드", () => {
  const originalFeederToken = process.env.FEEDER_API_TOKEN;
  const originalRelaySecret = process.env.SCRAPER_RELAY_SECRET;

  beforeEach(() => {
    delete process.env.FEEDER_API_TOKEN;
    delete process.env.SCRAPER_RELAY_SECRET;
  });

  afterEach(() => {
    if (originalFeederToken === undefined) {
      delete process.env.FEEDER_API_TOKEN;
    } else {
      process.env.FEEDER_API_TOKEN = originalFeederToken;
    }
    if (originalRelaySecret === undefined) {
      delete process.env.SCRAPER_RELAY_SECRET;
    } else {
      process.env.SCRAPER_RELAY_SECRET = originalRelaySecret;
    }
  });

  it("시크릿 미설정 시 503 Fail-Closed를 반환한다", async () => {
    const target = "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=1";
    const req = new NextRequest(
      `http://localhost:3000/api/scraper-relay?url=${encodeURIComponent(target)}`
    );
    const res = await GET(req);
    expect(res.status).toBe(503);
  });

  it("X-Feeder-Token 누락, 서명 불일치, 만료(>300초), URL 변조 시 401을 반환한다", async () => {
    const secret = "m4-test-relay-secret-key";
    process.env.FEEDER_API_TOKEN = secret;
    const target = "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=10";
    const nowSec = Math.floor(Date.now() / 1000);

    // 1. 헤더 누락
    const noHeaderReq = new NextRequest(
      `http://localhost:3000/api/scraper-relay?url=${encodeURIComponent(target)}`
    );
    expect((await GET(noHeaderReq)).status).toBe(401);

    // 2. 잘못된 서명
    const badSigReq = new NextRequest(
      `http://localhost:3000/api/scraper-relay?url=${encodeURIComponent(target)}`,
      { headers: { "X-Feeder-Token": `v1.${nowSec}.${"a".repeat(64)}` } }
    );
    expect((await GET(badSigReq)).status).toBe(401);

    // 3. 타임스탬프 만료 (305초 전)
    const expiredTs = nowSec - 305;
    const expiredToken = signRelayToken(secret, expiredTs, target);
    const expiredReq = new NextRequest(
      `http://localhost:3000/api/scraper-relay?url=${encodeURIComponent(target)}`,
      { headers: { "X-Feeder-Token": expiredToken } }
    );
    expect((await GET(expiredReq)).status).toBe(401);

    // 4. 서명 대상 URL 변조
    const tokenForOtherUrl = signRelayToken(
      secret,
      nowSec,
      "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=999"
    );
    const tamperedReq = new NextRequest(
      `http://localhost:3000/api/scraper-relay?url=${encodeURIComponent(target)}`,
      { headers: { "X-Feeder-Token": tokenForOtherUrl } }
    );
    expect((await GET(tamperedReq)).status).toBe(401);
  });

  it("유효한 HMAC이라도 비허용 호스트는 403으로 차단한다", async () => {
    const secret = "m4-test-relay-secret-key";
    process.env.FEEDER_API_TOKEN = secret;
    const forbiddenUrl = "https://evil.example.com/secret";
    const ts = Math.floor(Date.now() / 1000);
    const token = signRelayToken(secret, ts, forbiddenUrl);

    const req = new NextRequest(
      `http://localhost:3000/api/scraper-relay?url=${encodeURIComponent(forbiddenUrl)}`,
      { headers: { "X-Feeder-Token": token } }
    );
    const res = await GET(req);
    expect(res.status).toBe(403);
  });

  it("유효한 HMAC + 허용 도메인은 200 OK와 no-store 캐시 헤더를 반환한다", async () => {
    const secret = "m4-test-relay-secret-key";
    process.env.FEEDER_API_TOKEN = secret;
    const target = "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=12345";
    const ts = Math.floor(Date.now() / 1000);
    const token = signRelayToken(secret, ts, target);

    const sampleBytes = new TextEncoder().encode("<html><body>ppomppu deal</body></html>");
    const fetchSpy = vi.spyOn(globalThis, "fetch").mockResolvedValue(
      new Response(sampleBytes, {
        status: 200,
        headers: { "Content-Type": "text/html; charset=utf-8" },
      })
    );

    const req = new NextRequest(
      `http://localhost:3000/api/scraper-relay?url=${encodeURIComponent(target)}`,
      { headers: { "X-Feeder-Token": token } }
    );
    const res = await GET(req);
    expect(res.status).toBe(200);
    expect(res.headers.get("cache-control")).toBe("no-store, max-age=0");
    expect(await res.text()).toContain("ppomppu deal");
    expect(fetchSpy).toHaveBeenCalledTimes(1);
  });
});
