import { NextRequest, NextResponse } from "next/server";

const BACKEND_URL = (
  process.env.BACKEND_URL ||
  process.env.BACKEND_API_URL ||
  (process.env.VERCEL === "1"
    ? "https://34.59.208.14.sslip.io"
    : "http://localhost:8080")
).replace(/\/+$/, "");

const KAKAOCDN_LOWRES_RE =
  /^(https?:\/\/search\d*\.kakaocdn\.net\/argon\/)(?:130x130(?:_\d+_[a-zA-Z0-9]+)?|200x200(?:_\d+_[a-zA-Z0-9]+)?|320x320(?:_\d+_[a-zA-Z0-9]+)?|0x\d+(?:_\d+_[a-zA-Z0-9]+)?|656x0(?:_\d+_[a-zA-Z0-9]+)?)(\/[^?\s]+)/i;

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function upgradeOgImage(rawUrl: string | null | undefined, origin: string): string {
  const trimmed = String(rawUrl || "").trim();
  if (!trimmed) return `${origin}/brand-logo.png`;
  let normalized = trimmed.startsWith("//")
    ? `https:${trimmed}`
    : trimmed.startsWith("/")
      ? `${BACKEND_URL}${trimmed}`
      : trimmed;
  if (KAKAOCDN_LOWRES_RE.test(normalized)) {
    normalized = normalized.replace(
      KAKAOCDN_LOWRES_RE,
      (_m, prefix: string, suffix: string) => `${prefix}600x0_65_wr${suffix}`
    );
  }
  if (!/^https?:\/\//i.test(normalized)) {
    return `${origin}/brand-logo.png`;
  }
  return normalized;
}

function formatOgPrice(price: unknown, currency: unknown, isFree?: unknown): string {
  if (isFree === true) return "무료";
  const num = Number(price || 0);
  if (!Number.isFinite(num) || num <= 0) return "가격 확인";
  const curr = String(currency || "KRW").trim().toUpperCase();
  if (curr === "USD") return `$${(num / 100).toFixed(num % 100 === 0 ? 0 : 2)}`;
  if (curr === "EUR") return `€${(num / 100).toFixed(num % 100 === 0 ? 0 : 2)}`;
  if (curr === "CNY") return `¥${(num / 100).toFixed(num % 100 === 0 ? 0 : 2)}`;
  if (curr === "JPY") return `¥${Math.round(num).toLocaleString("ko-KR")}`;
  return `${Math.round(num).toLocaleString("ko-KR")}원`;
}

function upsertMetaTag(
  html: string,
  attrKey: "property" | "name",
  attrVal: string,
  content: string
): string {
  const escapedAttr = attrVal.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const escapedContent = escapeHtml(content);
  const tagRegex = new RegExp(
    `<meta[^>]*\\b${attrKey}=["']${escapedAttr}["'][^>]*>`,
    "i"
  );
  const replacement = `<meta ${attrKey}="${attrVal}" content="${escapedContent}" />`;
  if (tagRegex.test(html)) {
    return html.replace(tagRegex, replacement);
  }
  return html.replace(/<\/head>/i, `  ${replacement}\n</head>`);
}

export async function GET(req: NextRequest) {
  const dealIdRaw = req.nextUrl.searchParams.get("dealId") || "";
  const origin = req.nextUrl.origin;

  if (!/^\d+$/.test(dealIdRaw)) {
    return NextResponse.redirect(new URL("/", origin), 307);
  }

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 3200);

  try {
    const [shellRes, dealRes] = await Promise.allSettled([
      fetch(`${origin}/`, {
        headers: {
          "x-og-bypass": "1",
          accept: "text/html,application/xhtml+xml",
        },
        signal: controller.signal,
        cache: "no-store",
      }),
      fetch(`${BACKEND_URL}/api/community/deals/${dealIdRaw}`, {
        headers: { accept: "application/json" },
        signal: controller.signal,
        next: { revalidate: 60 },
      }),
    ]);

    if (shellRes.status !== "fulfilled" || !shellRes.value.ok) {
      return NextResponse.redirect(new URL(`/?dealId=${dealIdRaw}&_og=1`, origin), 307);
    }

    let html = await shellRes.value.text();
    let metaTitle = "얼만데 - 실시간 최저가 핫딜 & 역대 가격 추이";
    let metaDesc = "국내 커뮤니티 10곳의 검증된 실시간 핫딜 최저가와 가격 추이를 한곳에서 확인하세요.";
    let metaImage = `${origin}/brand-logo.png`;
    const canonicalUrl = `${origin}/?dealId=${dealIdRaw}`;

    if (dealRes.status === "fulfilled" && dealRes.value.ok) {
      const detail = (await dealRes.value.json()) as Record<string, unknown>;
      const rawTitle = String(detail.title || "").trim();
      const priceLabel = formatOgPrice(detail.price, detail.currency, detail.is_free);
      const shopLabel = String(detail.shop_name || detail.shopName || "").trim();
      const commLabel = String(detail.site_name || detail.community_name || "").trim();
      const shipLabel = String(detail.shipping_fee || detail.shippingFee || "").trim();
      if (rawTitle) {
        metaTitle = `[${priceLabel}] ${rawTitle} | 얼만데`;
        const descParts = [
          priceLabel !== "가격 확인" ? `현재가 ${priceLabel}` : "",
          shipLabel,
          shopLabel,
          commLabel,
        ].filter(Boolean);
        metaDesc = `${descParts.join(" · ")} — 얼만데 실시간 핫딜 비교 & 역대 시세`;
        metaImage = upgradeOgImage(
          (detail.image_url ?? detail.imageUrl) as string | undefined,
          origin
        );
      }
    }

    const escapedTitle = escapeHtml(metaTitle);
    if (/<title[^>]*>[\s\S]*?<\/title>/i.test(html)) {
      html = html.replace(/<title[^>]*>[\s\S]*?<\/title>/i, `<title>${escapedTitle}</title>`);
    } else {
      html = html.replace(/<\/head>/i, `  <title>${escapedTitle}</title>\n</head>`);
    }

    html = upsertMetaTag(html, "name", "description", metaDesc);
    html = upsertMetaTag(html, "property", "og:title", metaTitle);
    html = upsertMetaTag(html, "property", "og:description", metaDesc);
    html = upsertMetaTag(html, "property", "og:image", metaImage);
    html = upsertMetaTag(html, "property", "og:url", canonicalUrl);
    html = upsertMetaTag(html, "name", "twitter:card", "summary_large_image");
    html = upsertMetaTag(html, "name", "twitter:title", metaTitle);
    html = upsertMetaTag(html, "name", "twitter:description", metaDesc);
    html = upsertMetaTag(html, "name", "twitter:image", metaImage);

    return new NextResponse(html, {
      status: 200,
      headers: {
        "Content-Type": "text/html; charset=utf-8",
        "Cache-Control": "public, s-maxage=60, stale-while-revalidate=300",
      },
    });
  } catch {
    return NextResponse.redirect(
      new URL(`/?dealId=${encodeURIComponent(dealIdRaw)}&_og=1`, origin),
      307
    );
  } finally {
    clearTimeout(timeout);
  }
}
