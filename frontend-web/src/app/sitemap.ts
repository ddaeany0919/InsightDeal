import type { MetadataRoute } from "next";

export const dynamic = "force-static";

export default function sitemap(): MetadataRoute.Sitemap {
  const now = new Date();
  return [
    {
      url: "https://eolmande.pages.dev/",
      lastModified: now,
      changeFrequency: "always",
      priority: 1.0,
    },
    {
      url: "https://eolmande.pages.dev/notifications",
      lastModified: now,
      changeFrequency: "daily",
      priority: 0.7,
    },
    {
      url: "https://eolmande.pages.dev/settings/notifications",
      lastModified: now,
      changeFrequency: "weekly",
      priority: 0.6,
    },
  ];
}
