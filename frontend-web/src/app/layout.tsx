import type { Metadata, Viewport } from "next";
import "./globals.css";

const isTossMiniApp = process.env.NEXT_PUBLIC_TOSS_MINIAPP === "true";

const THEME_INIT_SCRIPT = `(function(){try{var t=localStorage.getItem('insightdeal_theme');var r=document.documentElement;if(t==='light'||t==='dark'||t==='amoled'){r.dataset.theme=t;r.style.colorScheme=(t==='light'?'light':'dark');}else{delete r.dataset.theme;r.style.colorScheme=window.matchMedia('(prefers-color-scheme: dark)').matches?'dark':'light';}}catch(e){}})();`;

export const metadata: Metadata = {
  metadataBase: new URL("https://eolmande.pages.dev"),
  title: "얼만데 - 실시간 핫딜 모아보기",
  description: "주요 커뮤니티 10곳의 실시간 핫딜을 한눈에 모아보세요 — 얼만데",
  keywords: [
    "얼만데",
    "핫딜",
    "스마트쇼핑",
    "할인",
    "이벤트",
    "뽐뿌",
    "뽐뿌해외",
    "알리뽐뿌",
    "펨코",
    "루리웹",
    "퀘이사존",
    "클리앙",
    "빠삭",
  ],
  icons: {
    icon: [
      { url: "/favicon.ico" },
      { url: "/favicon-96x96.png", sizes: "96x96", type: "image/png" },
    ],
    shortcut: "/favicon.ico",
    apple: "/apple-touch-icon.png",
  },
  appleWebApp: {
    title: "얼만데",
    statusBarStyle: "default",
  },
  openGraph: {
    title: "얼만데 - 실시간 핫딜 모아보기",
    description: "주요 커뮤니티 10곳의 실시간 핫딜을 한눈에 모아보세요 — 얼만데",
    type: "website",
    locale: "ko_KR",
    images: [
      {
        url: "/icon-512x512.png",
        width: 512,
        height: 512,
        alt: "얼만데 - 실시간 핫딜 모아보기",
      },
    ],
  },
};

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  ...(isTossMiniApp ? { maximumScale: 2 } : {}),
  viewportFit: "cover",
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
    <html lang="ko" suppressHydrationWarning>
      <head>
        <script dangerouslySetInnerHTML={{ __html: THEME_INIT_SCRIPT }} />
      </head>
      <body>{children}</body>
    </html>
  );
}
