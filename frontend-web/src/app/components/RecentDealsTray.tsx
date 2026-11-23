"use client";

import React, { useState } from "react";
import { X } from "lucide-react";
import type { Deal } from "../lib/types";
import {
  decodeHtmlEntities,
  formatPrice,
  getProxyImageUrl,
  resolveDealShopName,
  stripLeadingShopBracket,
  stripPromoPrefixForDisplay,
  tryApplyFallbackProxyImage,
} from "../lib/utils";
import BrandImagePlaceholder from "./BrandImagePlaceholder";

export interface RecentDealsTrayProps {
  deals: Deal[];
  onSelectDeal: (deal: Deal) => void;
  onRemoveDeal: (dealId: number) => void;
  onClearAll: () => void;
}

export default function RecentDealsTray({
  deals,
  onSelectDeal,
  onRemoveDeal,
  onClearAll,
}: RecentDealsTrayProps) {
  const [failedImages, setFailedImages] = useState<Record<string, boolean>>({});

  return (
    <section
      className="glass-panel recent-deals-panel"
      aria-label="최근 본 핫딜"
    >
      <div className="recent-deals-header">
        <strong className="recent-deals-heading">
          최근 본 핫딜 ({deals.length}건)
        </strong>
        {deals.length > 0 && (
          <button
            type="button"
            className="recent-deals-clear-btn"
            onClick={onClearAll}
          >
            전체 비우기
          </button>
        )}
      </div>

      {deals.length === 0 ? (
        <p className="recent-deals-empty">
          아직 열어본 핫딜이 없어요. 카드를 누르면 최근 기록이 여기에 쌓여요.
        </p>
      ) : (
        <ul className="recent-deals-track">
          {deals.map((rd) => {
            const shopBadge = resolveDealShopName({
              shop_name: rd.shop_name,
              title: rd.title,
              ecommerce_url: rd.ecommerce_url,
            });
            const titleWithoutShop = stripLeadingShopBracket(
              rd.title,
              shopBadge
            );
            const { cleanTitle } = stripPromoPrefixForDisplay(titleWithoutShop);
            const displayTitle =
              cleanTitle || decodeHtmlEntities(rd.title) || "핫딜 상품";
            const priceLabel = rd.isFree
              ? "무료"
              : formatPrice(rd.price ?? "", rd.currency, rd.title);
            const imgKey = `${rd.id}_${rd.image_url || ""}`;
            const primarySrc = rd.image_url
              ? getProxyImageUrl(rd.image_url, { width: 120 })
              : "";
            const isImgFailed =
              !rd.image_url || !primarySrc || Boolean(failedImages[imgKey]);

            return (
              <li
                key={rd.id}
                className={`recent-deals-card${rd.isClosed ? " is-closed" : ""}`}
              >
                <button
                  type="button"
                  className="recent-deals-card-trigger"
                  onClick={() => onSelectDeal(rd)}
                  aria-label={`${displayTitle}${shopBadge ? `, ${shopBadge}` : ""}${rd.isClosed ? ", 품절" : ""}, ${priceLabel} 상세 보기`}
                >
                  <div className="recent-deals-thumb">
                    {!isImgFailed && rd.image_url ? (
                      // eslint-disable-next-line @next/next/no-img-element
                      <img
                        key={imgKey}
                        src={primarySrc}
                        alt={displayTitle}
                        className={`recent-deals-thumb-img${rd.isClosed ? " is-closed" : ""}`}
                        loading="lazy"
                        decoding="async"
                        referrerPolicy="no-referrer"
                        onError={(e) => {
                          if (
                            tryApplyFallbackProxyImage(
                              e.currentTarget,
                              rd.image_url,
                              primarySrc
                            )
                          ) {
                            return;
                          }
                          setFailedImages((prev) => ({
                            ...prev,
                            [imgKey]: true,
                          }));
                        }}
                      />
                    ) : (
                      <BrandImagePlaceholder compact />
                    )}
                  </div>

                  <div className="recent-deals-info">
                    {(shopBadge || rd.isClosed) && (
                      <div className="recent-deals-badges">
                        {shopBadge && (
                          <span className="recent-deals-shop-badge">
                            {shopBadge}
                          </span>
                        )}
                        {rd.isClosed && (
                          <span className="badge badge-danger recent-deals-closed-badge">
                            품절
                          </span>
                        )}
                      </div>
                    )}
                    <span className="recent-deals-title">{displayTitle}</span>
                    <span
                      className={`recent-deals-price${rd.isClosed ? " is-closed" : ""}`}
                    >
                      {priceLabel}
                    </span>
                  </div>
                </button>

                <button
                  type="button"
                  className="recent-deals-remove-btn"
                  onClick={() => onRemoveDeal(rd.id)}
                  aria-label={`${displayTitle} 최근 본 목록에서 삭제`}
                  title="삭제"
                >
                  <X size={13} aria-hidden="true" />
                </button>
              </li>
            );
          })}
        </ul>
      )}
    </section>
  );
}
