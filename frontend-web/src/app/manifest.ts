import { MetadataRoute } from 'next'

export const dynamic = 'force-static'

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: '얼만데 - 실시간 핫딜 모아보기',
    short_name: '얼만데',
    description: '10개 커뮤니티 실시간 핫딜 & 다중 옵션 최저가 비교',
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
