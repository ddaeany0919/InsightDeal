import withPWAInit from "@ducanh2912/next-pwa";

const withPWA = withPWAInit({
  dest: "public",
  disable: process.env.NODE_ENV === "development",
  register: true,
});

/** @type {import('next').NextConfig} */
const nextConfig = {
  turbopack: {},
  // CORS 문제를 해결하기 위해 백엔드로 프록시합니다.
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: "http://localhost:8000/api/:path*", 
      },
      {
        source: "/images/:path*",
        destination: "http://localhost:8000/images/:path*",
      }
    ];
  },
};

export default withPWA(nextConfig);
