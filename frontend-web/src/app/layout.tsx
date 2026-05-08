import type { Metadata, Viewport } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "InsightDeal - 핫딜 모아보기",
  description: "국내 최강 핫딜 어그리게이터 앱",
  keywords: ["핫딜", "스마트쇼핑", "할인", "이벤트", "뽐뿌", "펨코", "루리웹", "퀘이사존", "클리앙", "빠삭"],
  openGraph: {
    title: "InsightDeal - 핫딜 모아보기",
    description: "국내 최강 핫딜 어그리게이터 앱",
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
    { media: "(prefers-color-scheme: light)", color: "#f8fafc" },
    { media: "(prefers-color-scheme: dark)", color: "#020617" },
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
