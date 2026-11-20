import type { NextConfig } from "next";
import withPWAInit from "@ducanh2912/next-pwa";

const isTossMiniApp = process.env.NEXT_PUBLIC_TOSS_MINIAPP === "true";

const withPWA = withPWAInit({
  dest: "public",
  sw: "pwa-sw.js",
  disable: process.env.NODE_ENV === "development" || isTossMiniApp,
  register: true,
  publicExcludes: ["!sw.js"],
  workboxOptions: {
    importScripts: ["/sw.js"],
  },
});

// Vercel 배포 시 클라우드 실서버(GCP systemd + Caddy)로, 로컬 기동 시 localhost:8080으로 프록시합니다.
const isVercel = process.env.VERCEL === "1";
const backendUrl = (
  process.env.BACKEND_URL ||
  process.env.BACKEND_API_URL ||
  (isVercel
    ? "https://34.59.208.14.sslip.io"
    : "http://localhost:8080")
).replace(/\/+$/, "");

console.log(`[Next.js Rewrite Engine] Configured rewrite destination to: ${backendUrl}`);

const nextConfig: NextConfig = {
  ...(isTossMiniApp
    ? { output: "export" as const, images: { unoptimized: true } }
    : {}),
  turbopack: {},
  // CORS 문제를 해결하기 위해 백엔드로 프록시합니다. (앱인토스 정적 빌드 시에는 apiUrl()이 직접 백엔드 HTTPS를 호출)
  ...(isTossMiniApp
    ? {}
    : {
        async rewrites() {
          return {
            beforeFiles: [
              {
                source: "/",
                has: [
                  {
                    type: "query",
                    key: "dealId",
                    value: "(?<dealId>\\d+)",
                  },
                ],
                missing: [
                  {
                    type: "header",
                    key: "x-og-bypass",
                    value: "1",
                  },
                  {
                    type: "query",
                    key: "_og",
                    value: "1",
                  },
                ],
                destination: "/api/og-deal?dealId=:dealId",
              },
            ],
            afterFiles: [
              {
                source: "/api/:path*",
                destination: `${backendUrl}/api/:path*`,
              },
              {
                source: "/images/:path*",
                destination: `${backendUrl}/images/:path*`,
              },
            ],
          };
        },
      }),
};

export default withPWA(nextConfig);
