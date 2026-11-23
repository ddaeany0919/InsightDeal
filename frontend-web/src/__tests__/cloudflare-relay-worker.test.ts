import crypto from "crypto";
import { afterEach, describe, expect, it, vi } from "vitest";
import worker from "../../../cloudflare-relay/src/index.js";

function signRelayToken(secret: string, ts: number, targetUrl: string): string {
  const hex = crypto
    .createHmac("sha256", secret)
    .update(`${ts}:${targetUrl}`)
    .digest("hex");
  return `v1.${ts}.${hex}`;
}

describe("Cloudflare Workers 무료 릴레이 (insightdeal-relay)", () => {
  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it("GET /health는 200 OK JSON을 반환한다", async () => {
    const req = new Request("https://insightdeal-relay.workers.dev/health");
    const res = await worker.fetch(req, {});
    expect(res.status).toBe(200);
    const body = await res.json();
    expect(body).toEqual({ ok: true, service: "insightdeal-relay" });
  });

  it("X-Feeder-Token 누락, 서명 불일치, 만료 시 401을 반환한다", async () => {
    const secret = "cf-worker-test-secret";
    const env = { FEEDER_API_TOKEN: secret, BACKEND_URL: "" };
    const target = "https://www.ppomppu.co.kr/rss.php?id=ppomppu";
    const nowSec = Math.floor(Date.now() / 1000);

    const noTok = await worker.fetch(
      new Request(
        `https://insightdeal-relay.workers.dev/?url=${encodeURIComponent(target)}`
      ),
      env
    );
    expect(noTok.status).toBe(401);

    const badSig = await worker.fetch(
      new Request(
        `https://insightdeal-relay.workers.dev/?url=${encodeURIComponent(target)}`,
        { headers: { "X-Feeder-Token": `v1.${nowSec}.${"b".repeat(64)}` } }
      ),
      env
    );
    expect(badSig.status).toBe(401);

    const expired = await worker.fetch(
      new Request(
        `https://insightdeal-relay.workers.dev/?url=${encodeURIComponent(target)}`,
        { headers: { "X-Feeder-Token": signRelayToken(secret, nowSec - 305, target) } }
      ),
      env
    );
    expect(expired.status).toBe(401);
  });

  it("비허용 도메인, 사설 IP, 리다이렉트 우회 시도를 403으로 차단한다", async () => {
    const secret = "cf-worker-test-secret";
    const env = { FEEDER_API_TOKEN: secret, BACKEND_URL: "" };
    const nowSec = Math.floor(Date.now() / 1000);

    for (const forbidden of [
      "https://evil.example.com/payload",
      "http://127.0.0.1:8080/admin",
    ]) {
      const res = await worker.fetch(
        new Request(
          `https://insightdeal-relay.workers.dev/?url=${encodeURIComponent(forbidden)}`,
          { headers: { "X-Feeder-Token": signRelayToken(secret, nowSec, forbidden) } }
        ),
        env
      );
      expect(res.status).toBe(403);
    }

    const allowedTarget = "https://www.ppomppu.co.kr/zboard/view.php?id=ppomppu&no=1";
    const mockFetch = vi.fn().mockResolvedValue(
      new Response(null, {
        status: 302,
        headers: { location: "http://127.0.0.1/internal" },
      })
    );
    vi.stubGlobal("fetch", mockFetch);

    const redirectRes = await worker.fetch(
      new Request(
        `https://insightdeal-relay.workers.dev/api/scraper-relay?url=${encodeURIComponent(
          allowedTarget
        )}`,
        {
          headers: {
            "X-Feeder-Token": signRelayToken(secret, nowSec, allowedTarget),
          },
        }
      ),
      env
    );
    expect(redirectRes.status).toBe(403);
    expect(mockFetch).toHaveBeenCalledTimes(1);
  });

  it("유효한 HMAC 토큰과 허용 도메인 요청 시 200 UTF-8 본문과 no-store 헤더를 반환한다", async () => {
    const secret = "cf-worker-test-secret";
    const env = { FEEDER_API_TOKEN: secret, BACKEND_URL: "" };
    const target = "https://www.ppomppu.co.kr/rss.php?id=ppomppu";
    const nowSec = Math.floor(Date.now() / 1000);

    const xmlPayload =
      '<?xml version="1.0" encoding="UTF-8"?><rss><channel><item><title>핫딜 테스트</title></item></channel></rss>';
    const mockFetch = vi.fn().mockResolvedValue(
      new Response(xmlPayload, {
        status: 200,
        headers: { "content-type": "application/xml; charset=utf-8" },
      })
    );
    vi.stubGlobal("fetch", mockFetch);

    const res = await worker.fetch(
      new Request(
        `https://insightdeal-relay.workers.dev/?url=${encodeURIComponent(target)}`,
        { headers: { "X-Feeder-Token": signRelayToken(secret, nowSec, target) } }
      ),
      env
    );
    expect(res.status).toBe(200);
    expect(res.headers.get("cache-control")).toBe("no-store, max-age=0");
    const text = await res.text();
    expect(text).toContain("핫딜 테스트");
  });
});
