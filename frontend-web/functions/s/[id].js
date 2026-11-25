const DEFAULT_BACKEND_ORIGIN = "https://34.59.208.14.sslip.io";

const BOT_UA_PATTERN =
  /kakaotalk-scrap|kakaostory|facebookexternalhit|facebot|twitterbot|slackbot|discordbot|telegrambot|naver|yeti|googlebot|bingbot|line|daum|applebot|whatsapp|linkedinbot|pinterest|meta-externalagent|developers\.kakao/i;

function escapeHtml(value) {
  return String(value ?? "")
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function formatDealPriceText(price, currency, isFree) {
  const num = Number(price);
  const normCurr = String(currency || "KRW").trim().toUpperCase();
  if (!Number.isFinite(num) || num <= 0) {
    return isFree ? "0원 (무료)" : "가격 확인";
  }
  if (normCurr === "USD") {
    const rawStr = String(price).trim();
    const dollars = rawStr.includes(".") ? num : num / 100;
    return `$${dollars.toFixed(2).replace(/\.00$/, "")}`;
  }
  if (normCurr === "EUR") {
    const rawStr = String(price).trim();
    const euros = rawStr.includes(".") ? num : num / 100;
    return `€${euros.toFixed(2).replace(/\.00$/, "")}`;
  }
  if (normCurr === "CNY") {
    const rawStr = String(price).trim();
    const yuan = rawStr.includes(".") ? num : num / 100;
    return `¥${yuan.toFixed(2).replace(/\.00$/, "")}`;
  }
  if (normCurr === "JPY") {
    return `¥${Math.round(num).toLocaleString("en-US")}`;
  }
  return `${Math.round(num).toLocaleString("en-US")}원`;
}

function resolveProxyOgImage(imageUrl, backendOrigin, siteOrigin) {
  const raw = String(imageUrl || "").trim();
  if (!/^https?:\/\//i.test(raw)) {
    return `${siteOrigin}/brand-logo.png`;
  }
  return `${backendOrigin}/api/proxy-image?url=${encodeURIComponent(raw)}`;
}

export async function onRequestGet(context) {
  const { request, params, env } = context;
  const reqUrl = new URL(request.url);
  const siteOrigin = reqUrl.origin;
  const rawId = Array.isArray(params?.id) ? params.id[0] : params?.id;
  const dealId = String(rawId || "").trim();

  if (!/^\d+$/.test(dealId)) {
    return Response.redirect(`${siteOrigin}/`, 302);
  }

  const userAgent = request.headers.get("user-agent") || "";
  const destinationUrl = `${siteOrigin}/?dealId=${dealId}`;

  if (!BOT_UA_PATTERN.test(userAgent)) {
    return new Response(null, {
      status: 302,
      headers: {
        Location: destinationUrl,
        "Cache-Control": "no-store, max-age=0",
      },
    });
  }

  const backendOrigin = String(
    env?.BACKEND_URL || env?.BACKEND_API_URL || DEFAULT_BACKEND_ORIGIN
  )
    .trim()
    .replace(/\/+$/, "");

  let ogTitle = "얼만데 - 실시간 커뮤니티 핫딜 모음";
  let ogDescription =
    "뽐뿌, 에펨코리아, 루리웹, 퀘이사존, 클리앙 등 실시간 최저가 핫딜과 가격 추이를 확인하세요.";
  let ogImage = `${siteOrigin}/brand-logo.png`;

  try {
    const apiRes = await fetch(
      `${backendOrigin}/api/community/deals/${encodeURIComponent(dealId)}`,
      {
        headers: {
          Accept: "application/json",
          "User-Agent": "InsightDeal-OG-Scrap/1.0",
        },
        signal: AbortSignal.timeout(3500),
      }
    );

    if (apiRes.ok) {
      const deal = await apiRes.json();
      if (deal && typeof deal === "object" && deal.title) {
        const cleanTitle = String(deal.title).replace(/\s+/g, " ").trim();
        const priceText = formatDealPriceText(
          deal.price,
          deal.currency,
          Boolean(deal.is_free)
        );
        const closedPrefix = deal.is_closed ? "[종료] " : "";
        ogTitle = `${closedPrefix}${cleanTitle} — ${priceText}`;

        const metaParts = [
          deal.shop_name,
          deal.shipping_fee,
          deal.site_name,
        ]
          .map((v) => String(v || "").trim())
          .filter(Boolean);
        ogDescription = metaParts.length
          ? `${metaParts.join(" · ")} | 얼만데에서 가격 추이·품절 여부 바로 보기`
          : "얼만데에서 가격 추이·품절 여부 바로 보기";

        ogImage = resolveProxyOgImage(deal.image_url, backendOrigin, siteOrigin);
      }
    }
  } catch {
    // 백엔드 조회 실패 시 사이트 기본 OG 메타 태그로 안전하게 폴백
  }

  const safeTitle = escapeHtml(ogTitle);
  const safeDesc = escapeHtml(ogDescription);
  const safeImg = escapeHtml(ogImage);
  const safeShareUrl = escapeHtml(`${siteOrigin}/s/${dealId}`);
  const safeDestUrl = escapeHtml(destinationUrl);

  const html = `<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>${safeTitle}</title>
  <meta name="description" content="${safeDesc}" />
  <meta property="og:type" content="website" />
  <meta property="og:site_name" content="얼만데 (Eolmande)" />
  <meta property="og:title" content="${safeTitle}" />
  <meta property="og:description" content="${safeDesc}" />
  <meta property="og:image" content="${safeImg}" />
  <meta property="og:url" content="${safeShareUrl}" />
  <meta name="twitter:card" content="summary_large_image" />
  <meta name="twitter:title" content="${safeTitle}" />
  <meta name="twitter:description" content="${safeDesc}" />
  <meta name="twitter:image" content="${safeImg}" />
  <meta http-equiv="refresh" content="0;url=${safeDestUrl}" />
</head>
<body>
  <p><a href="${safeDestUrl}">${safeTitle}</a></p>
</body>
</html>`;

  return new Response(html, {
    status: 200,
    headers: {
      "Content-Type": "text/html; charset=utf-8",
      "Cache-Control": "public, max-age=120, s-maxage=300",
    },
  });
}

export const onRequestHead = onRequestGet;
