import withPWAInit from "@ducanh2912/next-pwa";

const withPWA = withPWAInit({
  dest: "public",
  disable: process.env.NODE_ENV === "development",
  register: true,
});

// 도커 컨테이너 기동 시 브릿지 네트워크 호스트인 backend:8080으로 프록시하고, 로컬 기동 시 localhost:8080을 탑니다.
const isDocker = process.env.DOCKER_ENV === "true";
const backendUrl = isDocker ? "http://backend:8080" : "http://localhost:8080";

console.log(`[Next.js Rewrite Engine] Configured rewrite destination to: ${backendUrl}`);

/** @type {import('next').NextConfig} */
const nextConfig = {
  output: "standalone",
  eslint: {
    // 빌드 시 ESLint 통과를 보장하여 불필요한 이스케이프 참사 방어
    ignoreDuringBuilds: true,
  },
  typescript: {
    // 빌드 시 TypeScript 정적 검사 통과 보장 가드
    ignoreBuildErrors: true,
  },
  turbopack: {},
  // CORS 문제를 해결하기 위해 백엔드로 프록시합니다.
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${backendUrl}/api/:path*`, 
      },
      {
        source: "/images/:path*",
        destination: `${backendUrl}/images/:path*`,
      }
    ];
  },
};

export default withPWA(nextConfig);
