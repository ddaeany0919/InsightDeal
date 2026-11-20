"use client";

import React, { useId, useRef, useState } from "react";
import { Heart, Trash2 } from "lucide-react";
import type { Deal, WishlistApiItem } from "../lib/types";
import {
  formatPrice,
  decodeHtmlEntities,
  getProxyImageUrl,
  tryApplyFallbackProxyImage,
} from "../lib/utils";
import { useDialogA11y } from "../lib/useDialogA11y";
import BrandImagePlaceholder from "./BrandImagePlaceholder";

interface WishlistDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  wishlistDeals: Deal[];
  serverWishlists?: WishlistApiItem[];
  onRemoveDeal: (e: React.MouseEvent, id: number) => void;
  onRemoveServerWishlist?: (wishlistId: number) => void;
  onSelectDeal?: (deal: Deal) => void;
  onSearchKeyword?: (keyword: string) => void;
  isLoggedIn: boolean;
  onLoginSimulate: () => void;
}

export default function WishlistDrawer({
  isOpen,
  onClose,
  wishlistDeals,
  serverWishlists = [],
  onRemoveDeal,
  onRemoveServerWishlist,
  onSelectDeal,
  onSearchKeyword,
  isLoggedIn,
  onLoginSimulate,
}: WishlistDrawerProps) {
  const panelRef = useRef<HTMLDivElement>(null);
  const titleId = useId();
  const [failedImages, setFailedImages] = useState<Record<string, boolean>>({});

  useDialogA11y({ isOpen, onClose, containerRef: panelRef });

  if (!isOpen) return null;

  const localTitlesLower = new Set(
    wishlistDeals.map((d) => decodeHtmlEntities(d.title).toLowerCase())
  );
  const syncedOnlyWishlists = serverWishlists.filter((item) => {
    const kw = decodeHtmlEntities(item.keyword || "").trim().toLowerCase();
    if (!kw) return false;
    for (const titleLower of localTitlesLower) {
      if (titleLower.includes(kw) || kw.includes(titleLower)) {
        return false;
      }
    }
    return true;
  });

  const totalCount = wishlistDeals.length + syncedOnlyWishlists.length;

  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        background: "rgba(15, 23, 42, 0.45)",
        backdropFilter: "blur(8px)",
        zIndex: 1900,
        display: "flex",
        justifyContent: "flex-end",
        animation: "fadeIn 0.3s ease",
      }}
      onClick={onClose}
    >
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        className="glass-panel"
        style={{
          width: "100%",
          maxWidth: "420px",
          height: "100%",
          padding:
            "calc(24px + env(safe-area-inset-top, 0px)) 24px calc(28px + env(safe-area-inset-bottom, 0px)) 24px",
          display: "flex",
          flexDirection: "column",
          gap: "24px",
          borderLeft: "1px solid var(--card-border)",
          borderRadius: "28px 0 0 28px",
          boxShadow: "-10px 0 40px rgba(0,0,0,0.2)",
          animation: "slideLeft 0.3s cubic-bezier(0.16, 1, 0.3, 1)",
          overflowY: "auto",
        }}
        onClick={(e) => e.stopPropagation()}
      >
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
          <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
            <Heart
              size={20}
              color="var(--danger-text)"
              fill="var(--danger-text)"
              aria-hidden="true"
            />
            <h3 id={titleId} style={{ fontSize: "1.25rem", fontWeight: 900 }}>
              내 찜 목록
            </h3>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="보관함 닫기"
            style={{
              background: "var(--surface-subtle)",
              border: "1px solid var(--card-border)",
              cursor: "pointer",
              width: 44,
              height: 44,
              minWidth: 44,
              minHeight: 44,
              borderRadius: "50%",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              color: "var(--text-sub)",
            }}
          >
            ✕
          </button>
        </div>

        {!isLoggedIn ? (
          <div
            style={{
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              justifyContent: "center",
              flex: 1,
              gap: "20px",
              textAlign: "center",
              padding: "20px",
            }}
          >
            <Heart size={44} color="var(--text-sub)" style={{ opacity: 0.45 }} aria-hidden="true" />
            <div>
              <p style={{ fontWeight: 700, fontSize: "1rem", marginBottom: "6px" }}>
                기기 연동이 필요해요
              </p>
              <p style={{ fontSize: "0.85rem", color: "var(--text-sub)", lineHeight: 1.5 }}>
                이 브라우저에서 기기 연동을 켜두면 찜한 상품을 한곳에 모아볼 수 있어요.
              </p>
            </div>
            <button
              type="button"
              className="btn-primary"
              onClick={onLoginSimulate}
              style={{ width: "100%", minHeight: "44px", justifyContent: "center" }}
            >
              이 기기에서 바로 사용하기
            </button>
          </div>
        ) : totalCount === 0 ? (
          <div
            style={{
              display: "flex",
              flexDirection: "column",
              alignItems: "center",
              justifyContent: "center",
              flex: 1,
              gap: "16px",
              textAlign: "center",
              color: "var(--text-sub)",
            }}
          >
            <Heart size={48} color="var(--text-sub)" style={{ opacity: 0.3 }} aria-hidden="true" />
            <p style={{ fontWeight: 700 }}>찜한 상품이 없어요</p>
            <p style={{ fontSize: "0.82rem", lineHeight: 1.4 }}>
              관심 있는 핫딜 카드의 하트 아이콘을 눌러 저장해 보세요.
            </p>
          </div>
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
            {wishlistDeals.map((deal) => {
              const imgKey = `${deal.id}_${deal.image_url || ""}`;
              const isImgFailed = !deal.image_url || Boolean(failedImages[imgKey]);
              return (
                <div
                  key={deal.id}
                  style={{
                    background: "var(--surface-subtle)",
                    border: "1px solid var(--card-border)",
                    borderRadius: "16px",
                    padding: "10px 12px",
                    display: "flex",
                    gap: "8px",
                    alignItems: "center",
                  }}
                >
                  <div
                    role="button"
                    tabIndex={0}
                    onClick={() => onSelectDeal?.(deal)}
                    onKeyDown={(e) => {
                      if (e.key === "Enter" || e.key === " ") {
                        e.preventDefault();
                        onSelectDeal?.(deal);
                      }
                    }}
                    title="클릭하여 상세 정보 및 가격 추이 보기"
                    style={{
                      flex: 1,
                      minWidth: 0,
                      display: "flex",
                      gap: "12px",
                      alignItems: "center",
                      cursor: "pointer",
                      borderRadius: "10px",
                      minHeight: "44px",
                    }}
                  >
                    <div
                      style={{
                        width: "50px",
                        height: "50px",
                        borderRadius: "8px",
                        overflow: "hidden",
                        flexShrink: 0,
                        background: "var(--badge-bg)",
                        border: "1px solid var(--card-border)",
                      }}
                    >
                      {!isImgFailed && deal.image_url ? (
                        // eslint-disable-next-line @next/next/no-img-element
                        <img
                          src={getProxyImageUrl(deal.image_url, { width: 120 })}
                          alt={deal.title}
                          style={{ width: "100%", height: "100%", objectFit: "cover" }}
                          referrerPolicy="no-referrer"
                          onError={(e) => {
                            const primarySrc = getProxyImageUrl(deal.image_url, { width: 120 });
                            if (tryApplyFallbackProxyImage(e.currentTarget, deal.image_url, primarySrc)) {
                              return;
                            }
                            setFailedImages((prev) => ({ ...prev, [imgKey]: true }));
                          }}
                        />
                      ) : (
                        <BrandImagePlaceholder compact />
                      )}
                    </div>
                    <div style={{ flex: 1, display: "flex", flexDirection: "column", minWidth: 0 }}>
                      <span
                        style={{
                          fontSize: "0.82rem",
                          fontWeight: 700,
                          color: "var(--text-main)",
                          overflow: "hidden",
                          textOverflow: "ellipsis",
                          whiteSpace: "nowrap",
                        }}
                      >
                        {decodeHtmlEntities(deal.title)}
                      </span>
                      <div
                        style={{
                          display: "flex",
                          alignItems: "center",
                          gap: "6px",
                          marginTop: "2px",
                          flexWrap: "wrap",
                        }}
                      >
                        <span
                          style={{
                            fontSize: "0.8rem",
                            color: "var(--deal-orange)",
                            fontWeight: 800,
                          }}
                        >
                          {deal.isFree ? "0원 (무료)" : formatPrice(deal.price, deal.currency, deal.title)}
                        </span>
                        {(() => {
                          try {
                            const mapStr =
                              typeof window !== "undefined"
                                ? localStorage.getItem("insightdeal_target_prices")
                                : null;
                            const targetMap = mapStr ? JSON.parse(mapStr) : {};
                            const targetVal = targetMap?.[deal.id] ?? targetMap?.[`${deal.id}_0`];
                            const numericTarget = Number(targetVal);
                            if (Number.isFinite(numericTarget) && numericTarget > 0) {
                              const normCurr = (deal.currency || "KRW").trim().toUpperCase();
                              const formattedTarget =
                                normCurr === "USD"
                                  ? `$${numericTarget.toLocaleString("en-US")}`
                                  : normCurr === "EUR"
                                    ? `€${numericTarget.toLocaleString("en-US")}`
                                    : normCurr === "CNY" || normCurr === "JPY"
                                      ? `¥${numericTarget.toLocaleString("en-US")}`
                                      : `${Math.round(numericTarget).toLocaleString()}원`;
                              return (
                                <span
                                  style={{
                                    fontSize: "0.68rem",
                                    fontWeight: 800,
                                    color: "var(--success-text)",
                                    background: "var(--success-bg)",
                                    border: "1px solid var(--success-border)",
                                    padding: "1px 6px",
                                    borderRadius: "999px",
                                  }}
                                >
                                  목표가 {formattedTarget}
                                </span>
                              );
                            }
                          } catch {}
                          return null;
                        })()}
                      </div>
                    </div>
                  </div>
                  <button
                    type="button"
                    aria-label={`${deal.title} 찜 해제`}
                    onClick={(e) => onRemoveDeal(e, deal.id)}
                    style={{
                      background: "none",
                      border: "none",
                      cursor: "pointer",
                      width: 44,
                      height: 44,
                      minWidth: 44,
                      minHeight: 44,
                      display: "inline-flex",
                      alignItems: "center",
                      justifyContent: "center",
                      color: "var(--text-sub)",
                      flexShrink: 0,
                    }}
                    title="찜 해제"
                  >
                    <Trash2 size={16} aria-hidden="true" />
                  </button>
                </div>
              );
            })}

            {syncedOnlyWishlists.map((item) => (
              <div
                key={`srv-${item.id}`}
                style={{
                  background: "var(--surface-subtle)",
                  border: "1px solid var(--card-border)",
                  borderRadius: "16px",
                  padding: "10px 12px",
                  display: "flex",
                  gap: "8px",
                  alignItems: "center",
                }}
              >
                <button
                  type="button"
                  onClick={() => {
                    onClose();
                    onSearchKeyword?.(item.keyword);
                  }}
                  style={{
                    flex: 1,
                    minWidth: 0,
                    minHeight: "44px",
                    display: "flex",
                    alignItems: "center",
                    gap: "12px",
                    textAlign: "left",
                  }}
                >
                  <div
                    style={{
                      width: "50px",
                      height: "50px",
                      borderRadius: "8px",
                      overflow: "hidden",
                      flexShrink: 0,
                      border: "1px solid var(--card-border)",
                    }}
                  >
                    <BrandImagePlaceholder compact />
                  </div>
                  <div style={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column", gap: "3px" }}>
                    <span
                      style={{
                        fontSize: "0.84rem",
                        fontWeight: 700,
                        color: "var(--text-main)",
                        overflow: "hidden",
                        textOverflow: "ellipsis",
                        whiteSpace: "nowrap",
                      }}
                    >
                      {decodeHtmlEntities(item.keyword)}
                    </span>
                    <div style={{ display: "flex", alignItems: "center", gap: "6px", flexWrap: "wrap" }}>
                      <span className="badge badge-mint">동기화된 찜</span>
                      {Number(item.target_price) > 0 && (
                        <span
                          style={{
                            fontSize: "0.68rem",
                            fontWeight: 800,
                            color: "var(--success-text)",
                            background: "var(--success-bg)",
                            border: "1px solid var(--success-border)",
                            padding: "1px 6px",
                            borderRadius: "999px",
                          }}
                        >
                          목표가 {Number(item.target_price).toLocaleString()}원
                        </span>
                      )}
                    </div>
                  </div>
                </button>
                {onRemoveServerWishlist && (
                  <button
                    type="button"
                    aria-label={`${item.keyword} 서버 찜 해제`}
                    onClick={() => onRemoveServerWishlist(item.id)}
                    style={{
                      width: 44,
                      height: 44,
                      minWidth: 44,
                      minHeight: 44,
                      display: "inline-flex",
                      alignItems: "center",
                      justifyContent: "center",
                      color: "var(--text-sub)",
                      flexShrink: 0,
                    }}
                  >
                    <Trash2 size={16} aria-hidden="true" />
                  </button>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
