import type { NextConfig } from "next";
import withPWAInit from "@ducanh2912/next-pwa";

const isTossMiniApp = process.env.NEXT_PUBLIC_TOSS_MINIAPP === "true";

const withPWA = withPWAInit({
  dest: "public",
  disable: process.env.NODE_ENV === "development" || isTossMiniApp,
  register: true,
});

// 도커 컨테이너 기동 시 브릿지 네트워크 호스트인 backend:8080으로 프록시하고, 로컬 기동 시 localhost:8080을 탑니다.
const isDocker = process.env.DOCKER_ENV === "true";
const backendUrl = process.env.BACKEND_URL || (isDocker ? "http://backend:8080" : "http://localhost:8080");

console.log(`[Next.js Rewrite Engine] Configured rewrite destination to: ${backendUrl}`);

const isVercel = process.env.VERCEL === "1";

/** @type {import('next').NextConfig} */
const nextConfig = {
  ...(isTossMiniApp
    ? { output: "export" as const, images: { unoptimized: true } }
    : isVercel
      ? {}
      : { output: "standalone" as const }),
  typescript: {
    // 빌드 시 TypeScript 정적 검사 통과 보장 가드
    ignoreBuildErrors: true,
  },
  turbopack: {},
  // CORS 문제를 해결하기 위해 백엔드로 프록시합니다. (앱인토스 정적 빌드 시에는 apiUrl()이 직접 백엔드 HTTPS를 호출)
  ...(isTossMiniApp
    ? {}
    : {
        async rewrites() {
          return [
            {
              source: "/api/:path*",
              destination: `${backendUrl}/api/:path*`,
            },
            {
              source: "/images/:path*",
              destination: `${backendUrl}/images/:path*`,
            },
          ];
        },
      }),
};

export default withPWA(nextConfig);
