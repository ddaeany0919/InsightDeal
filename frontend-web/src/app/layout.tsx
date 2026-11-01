import type { Metadata, Viewport } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "InsightDeal - 실시간 핫딜 모아보기",
  description: "9개 주요 커뮤니티 실시간 핫딜 및 다중 옵션 최저가 인텔리전스",
  keywords: ["핫딜", "스마트쇼핑", "할인", "이벤트", "뽐뿌", "펨코", "루리웹", "퀘이사존", "클리앙", "빠삭"],
  icons: {
    icon: [
      { url: "/favicon.ico" },
      { url: "/favicon-96x96.png", sizes: "96x96", type: "image/png" },
    ],
    shortcut: "/favicon.ico",
    apple: "/apple-touch-icon.png",
  },
  appleWebApp: {
    title: "InsightDeal",
    statusBarStyle: "default",
  },
  openGraph: {
    title: "InsightDeal - 실시간 핫딜 모아보기",
    description: "9개 주요 커뮤니티 실시간 핫딜 및 다중 옵션 최저가 인텔리전스",
    type: "website",
    locale: "ko_KR",
  },
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  maximumScale: 1,
  userScalable: false,
  themeColor: [
    { media: "(prefers-color-scheme: light)", color: "#F2F4F6" },
    { media: "(prefers-color-scheme: dark)", color: "#101318" },
  ],
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="ko">
      <body>{children}</body>
    </html>
  );
}
