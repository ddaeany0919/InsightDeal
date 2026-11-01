import { MetadataRoute } from 'next'

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: 'InsightDeal - 실시간 핫딜 모아보기',
    short_name: 'InsightDeal',
    description: '9개 커뮤니티 실시간 핫딜 & 다중 옵션 최저가 비교',
    start_url: '/',
    display: 'standalone',
    background_color: '#F2F4F6',
    theme_color: '#FF5A26',
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
        purpose: 'maskable',
      },
    ],
  }
}
