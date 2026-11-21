import type { MetadataRoute } from "next";

export const dynamic = "force-static";

export default function sitemap(): MetadataRoute.Sitemap {
  const now = new Date();
  return [
    {
      url: "https://insightdeal.vercel.app/",
      lastModified: now,
      changeFrequency: "always",
      priority: 1.0,
    },
    {
      url: "https://insightdeal.vercel.app/notifications",
      lastModified: now,
      changeFrequency: "daily",
      priority: 0.7,
    },
    {
      url: "https://insightdeal.vercel.app/settings/notifications",
      lastModified: now,
      changeFrequency: "weekly",
      priority: 0.6,
    },
  ];
}
