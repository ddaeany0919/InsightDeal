"use client";

import React, { useEffect, useRef, useState } from "react";
import { Bell, ExternalLink, Heart, Share2 } from "lucide-react";
import type { Deal, DealSource } from "../lib/types";
import {
  computeUnitPriceBadge,
  decodeHtmlEntities,
  formatPrice,
  formatRelativeTimeKo,
  getOptionSources,
  getProxyImageUrl,
  isNonMallLabel,
  normalizeMallAlias,
  resolveDealShopName,
  resolveOutboundUrl,
  stripLeadingShopBracket,
  trackOutboundClick,
  tryApplyFallbackProxyImage,
} from "../lib/utils";
import { openExternalUrlInToss } from "../lib/tossBridge";
import BrandImagePlaceholder from "./BrandImagePlaceholder";
import HighlightedTitle from "./HighlightedTitle";

interface DealCardProps {
  deal: Deal;
  appliedSearch: string;
  isMounted: boolean;
  currentTime: number;
  isWishlisted: boolean;
  isAlertSet: boolean;
  priority?: boolean;
  onSelectDeal: (deal: Deal) => void;
  onShareDeal: (e: React.MouseEvent, deal: Deal) => void;
  onToggleAlert: (e: React.MouseEvent, dealId: number) => void;
  onToggleWishlist: (e: React.MouseEvent, dealId: number) => void;
  onShowToast: (message: string) => void;
}

export default function DealCard({
  deal,
  appliedSearch,
  isMounted,
  currentTime,
  isWishlisted,
  isAlertSet,
  priority = false,
  onSelectDeal,
  onShareDeal,
  onToggleAlert,
  onToggleWishlist,
  onShowToast,
}: DealCardProps) {
  const [failedImageUrl, setFailedImageUrl] = useState<string | null>(null);
  const [loadedImageUrl, setLoadedImageUrl] = useState<string | null>(null);
  const imgRef = useRef<HTMLImageElement | null>(null);

  useEffect(() => {
    const img = imgRef.current;
    if (!deal.image_url || !img || !img.complete || img.naturalWidth <= 0) {
      return;
    }
    const targetUrl = deal.image_url;
    const rafId = window.requestAnimationFrame(() => {
      setLoadedImageUrl(targetUrl);
    });
    return () => window.cancelAnimationFrame(rafId);
  }, [deal.image_url]);

  const imgFailed = !deal.image_url || failedImageUrl === deal.image_url;
  const imgLoaded = Boolean(deal.image_url && loadedImageUrl === deal.image_url);

  const hasValidEcommerceLink =
    deal.ecommerce_url &&
    deal.ecommerce_url !== "#" &&
    String(deal.ecommerce_url).trim() !== "";
  const outboundTarget = hasValidEcommerceLink
    ? resolveOutboundUrl(deal.ecommerce_url)
    : deal.post_link && deal.post_link !== "#"
      ? resolveOutboundUrl(deal.post_link)
      : "#";

  const optionSources = getOptionSources(deal.sources, deal.options_data);
  const optionCount = deal.options_count || optionSources.length;
  const isMultiDeal = Boolean(deal.has_options || optionCount >= 2);
  const displayCount = optionCount >= 2 ? optionCount : deal.has_options ? 2 : 0;

  const shopBadge = resolveDealShopName({
    shop_name: deal.shop_name,
    title: deal.title,
    ecommerce_url: deal.ecommerce_url,
  });
  const displayTitle = stripLeadingShopBracket(deal.title, shopBadge);
  const displayShipping = deal.shipping_fee
    ? decodeHtmlEntities(deal.shipping_fee)
    : null;
  const isSearchFallback = Boolean(
    deal.isSearchFallback || deal.is_search_fallback
  );
  const unitPriceBadge =
    !deal.isClosed && !deal.isFree && !isMultiDeal
      ? computeUnitPriceBadge(
          displayTitle,
          deal.price,
          deal.currency,
          deal.category
        )
      : null;

  const badgeSources: DealSource[] =
    deal.sources && deal.sources.length > 0
      ? deal.sources
      : deal.community_name.split(", ").map((cName) => ({
          site_name: cName,
          post_url: deal.post_link,
        }));

  const uniqueBadgeSources: DealSource[] = [];
  badgeSources.forEach((src: DealSource) => {
    const nameClean = src.site_name
      ? src.site_name.replace(/ - .*$/, "").trim()
      : "";
    if (
      !uniqueBadgeSources.some(
        (x: DealSource) =>
          (x.site_name ? x.site_name.replace(/ - .*$/, "").trim() : "") ===
          nameClean
      )
    ) {
      uniqueBadgeSources.push(src);
    }
  });

  const liveTimeAgo =
    !isMounted || currentTime <= 0
      ? deal.scraped_at || "최근"
      : formatRelativeTimeKo(
          deal.originalDate,
          currentTime,
          deal.scraped_at || "최근"
        );

  return (
    <article className="glass-panel deal-card">
      {/* 1. 본문 클릭 영역 (비인터랙티브 자식만 포함 -> 상세 모달 오픈) */}
      <div
        role="button"
        tabIndex={0}
        className="deal-card-body-trigger"
        aria-label={`${displayTitle} 상세 정보 보기`}
        onClick={() => onSelectDeal(deal)}
        onKeyDown={(e) => {
          if (e.key === "Enter" || e.key === " ") {
            e.preventDefault();
            onSelectDeal(deal);
          }
        }}
      >
        <div className="deal-image-wrapper">
          {!imgFailed && deal.image_url ? (
            <>
              {!imgLoaded && (
                <div
                  className="skeleton-shimmer"
                  aria-hidden="true"
                  style={{ position: "absolute", inset: 0 }}
                />
              )}
              {/* eslint-disable-next-line @next/next/no-img-element */}
              <img
                ref={imgRef}
                src={getProxyImageUrl(deal.image_url, { width: 400 })}
                alt={displayTitle}
                className={`deal-image ${imgLoaded ? "is-loaded" : ""}`}
                loading={priority ? "eager" : "lazy"}
                fetchPriority={priority ? "high" : undefined}
                decoding="async"
                referrerPolicy="no-referrer"
                style={
                  deal.isClosed
                    ? { filter: "grayscale(0.85)", opacity: 0.72 }
                    : undefined
                }
                onLoad={() => setLoadedImageUrl(deal.image_url)}
                onError={(e) => {
                  const primarySrc = getProxyImageUrl(deal.image_url, { width: 400 });
                  if (tryApplyFallbackProxyImage(e.currentTarget, deal.image_url, primarySrc)) {
                    return;
                  }
                  setFailedImageUrl(deal.image_url);
                }}
              />
            </>
          ) : (
            <BrandImagePlaceholder />
          )}

          {isMultiDeal && (
            <div
              style={{
                position: "absolute",
                bottom: "8px",
                left: "8px",
                background: "rgba(25, 31, 40, 0.82)",
                backdropFilter: "blur(6px)",
                color: "#FFFFFF",
                border: "1px solid rgba(255, 255, 255, 0.14)",
                padding: "3px 8px",
                borderRadius: "var(--radius-xs)",
                fontSize: "var(--fs-caption)",
                fontWeight: 700,
                display: "inline-flex",
                alignItems: "center",
                zIndex: 2,
              }}
            >
              <span>
                {displayCount >= 2 ? `${displayCount}종 옵션` : "옵션 상품"}
              </span>
            </div>
          )}
        </div>

        <h3 className="deal-title">
          <HighlightedTitle title={displayTitle} query={appliedSearch} />
        </h3>
      </div>

      {/* 2. 출처 링크 + 쇼핑몰 + 시간 메타 바 (본문 트리거의 형제 요소) */}
      <div className="deal-info">
        <div className="deal-meta">
          {uniqueBadgeSources.map((source: DealSource, idx: number) => {
            const displaySiteName = source.site_name
              ? source.site_name.replace(/ - .*$/, "").trim()
              : "";
            const targetPostUrl = source.post_url || deal.post_link;
            const hasPostUrl = Boolean(targetPostUrl && targetPostUrl !== "#");
            const resolvedPostUrl = hasPostUrl
              ? resolveOutboundUrl(targetPostUrl)
              : "#";
            return hasPostUrl ? (
              <a
                key={idx}
                href={resolvedPostUrl}
                target="_blank"
                rel="noopener noreferrer"
                className="deal-source deal-source-link"
                title={`${displaySiteName} 원본 글 보기`}
                onClick={(e) => {
                  openExternalUrlInToss(resolvedPostUrl, e);
                }}
                style={{
                  border: "1px solid var(--accent-soft-border)",
                }}
              >
                {displaySiteName}
              </a>
            ) : (
              <span key={idx} className="deal-source">
                {displaySiteName}
              </span>
            );
          })}
          {shopBadge && (
            <span
              style={{
                fontSize: "var(--fs-caption)",
                fontWeight: 700,
                color: "var(--text-main)",
                background: "var(--badge-bg)",
                border: "1px solid var(--card-border)",
                padding: "3px 8px",
                borderRadius: "var(--radius-xs)",
                display: "inline-flex",
                alignItems: "center",
                lineHeight: 1.3,
              }}
            >
              {shopBadge}
            </span>
          )}
          <span aria-hidden="true">·</span>
          <span>{liveTimeAgo}</span>
        </div>

        {/* 3. 가격 + 배지 + 찜/알림/공유 액션 */}
        <div className="price-section">
          <div
            style={{
              display: "flex",
              alignItems: "baseline",
              gap: "6px",
              flexWrap: "wrap",
              minWidth: 0,
            }}
          >
            <span className="deal-price">
              {deal.isFree
                ? "0원 (무료)"
                : formatPrice(deal.price, deal.currency, deal.title)}
            </span>
            {deal.isClosed ? (
              <span className="badge badge-danger">품절·종료</span>
            ) : (
              (deal.honeyScore || 0) >= 100 && (
                <span className="deal-tag">인기</span>
              )
            )}
            {isSearchFallback && (
              <span
                className="deal-tag search-fallback-badge"
                title="직접 상품 링크 대신 쇼핑몰 검색 결과로 이동해요"
              >
                검색이동
              </span>
            )}
            {displayShipping && !deal.isClosed && (
              <span className="badge badge-mint">{displayShipping}</span>
            )}
            {unitPriceBadge && (
              <span
                className="deal-tag deal-unit-price-badge"
                title={unitPriceBadge.detail}
              >
                {unitPriceBadge.label}
              </span>
            )}
            {!deal.isClosed &&
              deal.tags &&
              deal.tags.map((tag, tIdx) => {
                const cleanTag = decodeHtmlEntities(String(tag))
                  .replace(
                    /^[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}\uFE0F]+\s*/gu,
                    ""
                  )
                  .trim();
                if (!cleanTag) return null;
                if (shopBadge && normalizeMallAlias(cleanTag) === shopBadge) {
                  return null;
                }
                if (
                  isNonMallLabel(cleanTag) &&
                  cleanTag !== "역대가" &&
                  cleanTag !== "할인코드"
                ) {
                  return null;
                }
                return (
                  <span
                    key={tIdx}
                    className="deal-tag"
                    style={{
                      background: "var(--badge-bg)",
                      color: "var(--badge-text)",
                      border: "1px solid var(--card-border)",
                    }}
                  >
                    {cleanTag}
                  </span>
                );
              })}
          </div>

          <div className="deal-card-actions">
            <button
              type="button"
              onClick={(e) => onShareDeal(e, deal)}
              className="deal-card-icon-btn bounce-click"
              aria-label="공유하기"
              title="공유하기"
            >
              <Share2 size={18} color="var(--text-sub)" aria-hidden="true" />
            </button>
            <button
              type="button"
              onClick={(e) => onToggleAlert(e, deal.id)}
              className="deal-card-icon-btn bounce-click"
              aria-label="가격 알림 설정"
              aria-pressed={isAlertSet}
              title="가격 알림"
            >
              <Bell
                size={18}
                color={isAlertSet ? "var(--accent)" : "var(--text-sub)"}
                fill={isAlertSet ? "var(--accent)" : "none"}
                aria-hidden="true"
              />
            </button>
            <button
              type="button"
              onClick={(e) => onToggleWishlist(e, deal.id)}
              className="deal-card-icon-btn bounce-click"
              aria-label="찜하기"
              aria-pressed={isWishlisted}
              title="찜하기"
            >
              <Heart
                size={18}
                color={isWishlisted ? "var(--danger-text)" : "var(--text-sub)"}
                fill={isWishlisted ? "var(--danger-text)" : "none"}
                aria-hidden="true"
              />
            </button>
          </div>
        </div>

        {/* 4. 하단 독립 2버튼 */}
        <div className="deal-card-footer-actions">
          <button
            type="button"
            onClick={() => onSelectDeal(deal)}
            className="deal-card-detail-btn bounce-click"
          >
            <span>상세 · 의견 보기</span>
          </button>
          <a
            href={outboundTarget}
            target="_blank"
            rel="noopener noreferrer"
            className="btn-primary bounce-click deal-card-buy-link"
            aria-label={`${displayTitle} ${
              isSearchFallback ? "쇼핑몰 검색 결과 보기" : "바로구매"
            } (새 창)`}
            onClick={(e) => {
              if (!outboundTarget || outboundTarget === "#") {
                e.preventDefault();
                onShowToast("이동할 수 있는 상품 주소를 찾지 못했어요");
                return;
              }
              if (!hasValidEcommerceLink) {
                onShowToast("구매처 바로가기가 없어 원본 게시글로 이동해요");
              }
              trackOutboundClick(
                outboundTarget,
                deal.id,
                deal.price,
                deal.currency
              );
              openExternalUrlInToss(outboundTarget, e);
            }}
          >
            <span>{isSearchFallback ? "검색 결과 보기" : "바로구매"}</span>
            <ExternalLink size={14} aria-hidden="true" />
          </a>
        </div>
      </div>
    </article>
  );
}
