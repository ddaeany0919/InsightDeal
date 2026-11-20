import { MetadataRoute } from 'next'

export const dynamic = 'force-static'

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: '얼만데 - 실시간 핫딜 모아보기',
    short_name: '얼만데',
    description: '주요 커뮤니티 10곳의 실시간 핫딜을 한눈에 모아보세요 — 얼만데',
    start_url: '/',
    display: 'standalone',
    background_color: '#F2F4F6',
    theme_color: '#00B87A',
    icons: [
      {
        src: '/icon-192x192.png',
        sizes: '192x192',
        type: 'image/png',
        purpose: 'any',
      },
      {
        src: '/icon-512x512.png',
        sizes: '512x512',
        type: 'image/png',
        purpose: 'any',
      },
      {
        src: '/icon-maskable-512x512.png',
        sizes: '512x512',
        type: 'image/png',
        purpose: 'maskable',
      },
    ],
  }
}
