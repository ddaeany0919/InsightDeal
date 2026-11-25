"use client";

import React, { useId, useRef, useState } from "react";
import { Download, Edit3, Heart, Link2, Trash2 } from "lucide-react";
import type { Deal, WishlistApiItem } from "../lib/types";
import {
  formatPrice,
  decodeHtmlEntities,
  getProxyImageUrl,
  tryApplyFallbackProxyImage,
  authFetch,
  ensureWebDeviceAuth,
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
  onSyncComplete?: () => Promise<void> | void;
  onSelectDeal?: (deal: Deal) => void;
  onSearchKeyword?: (keyword: string) => void;
  onShowToast?: (message: string) => void;
  isLoggedIn: boolean;
  onLoginSimulate: () => void;
}

type WishlistSortMode = "latest" | "price_asc" | "target_near";

export default function WishlistDrawer({
  isOpen,
  onClose,
  wishlistDeals,
  serverWishlists = [],
  onRemoveDeal,
  onRemoveServerWishlist,
  onSyncComplete,
  onSelectDeal,
  onSearchKeyword,
  onShowToast,
  isLoggedIn,
  onLoginSimulate,
}: WishlistDrawerProps) {
  const panelRef = useRef<HTMLDivElement>(null);
  const titleId = useId();
  const [failedImages, setFailedImages] = useState<Record<string, boolean>>({});
  const [sortMode, setSortMode] = useState<WishlistSortMode>("latest");
  const [editingWishlistId, setEditingWishlistId] = useState<number | null>(null);
  const [editingPriceInput, setEditingPriceInput] = useState<string>("");
  const [showUrlAddForm, setShowUrlAddForm] = useState<boolean>(false);
  const [urlInput, setUrlInput] = useState<string>("");
  const [urlTargetPrice, setUrlTargetPrice] = useState<string>("");
  const [submittingUrl, setSubmittingUrl] = useState<boolean>(false);
  const [actionNotice, setActionNotice] = useState<string | null>(null);

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

  const sortedWishlistDeals = [...wishlistDeals].sort((a, b) => {
    if (sortMode === "price_asc") {
      const pa = parseInt(String(a.price || "").replace(/[^0-9]/g, ""), 10) || Number.MAX_SAFE_INTEGER;
      const pb = parseInt(String(b.price || "").replace(/[^0-9]/g, ""), 10) || Number.MAX_SAFE_INTEGER;
      return pa - pb;
    }
    if (sortMode === "target_near") {
      let targetMap: Record<string, number> = {};
      if (typeof window !== "undefined") {
        try {
          targetMap = JSON.parse(localStorage.getItem("insightdeal_target_prices") || "{}") || {};
        } catch {}
      }
      const gapRatio = (d: Deal) => {
        const t = Number(targetMap?.[d.id] ?? targetMap?.[`${d.id}_0`]);
        const p = parseInt(String(d.price || "").replace(/[^0-9]/g, ""), 10);
        if (!Number.isFinite(t) || t <= 0 || !Number.isFinite(p) || p <= 0) return Number.MAX_SAFE_INTEGER;
        return Math.abs(p - t) / t;
      };
      return gapRatio(a) - gapRatio(b);
    }
    return b.id - a.id;
  });

  const closedWishlistDeals = wishlistDeals.filter((d) => d.isClosed);
  const totalCount = wishlistDeals.length + syncedOnlyWishlists.length;

  const handleCleanupClosed = (e: React.MouseEvent) => {
    closedWishlistDeals.forEach((d) => onRemoveDeal(e, d.id));
    setActionNotice(`품절·종료된 찜 ${closedWishlistDeals.length}건을 정리했어요.`);
    onShowToast?.(`종료된 찜 ${closedWishlistDeals.length}건을 정리했어요`);
  };

  const handleSaveInlineTargetPrice = async (wishlistId: number) => {
    const num = parseInt(editingPriceInput.replace(/[^0-9]/g, ""), 10);
    if (!Number.isFinite(num) || num < 100) {
      setActionNotice("목표가는 100원 이상 숫자로 입력해 주세요.");
      return;
    }
    try {
      await ensureWebDeviceAuth();
      const res = await authFetch(`/api/wishlist/${wishlistId}`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ target_price: num }),
      });
      if (!res.ok) {
        setActionNotice("목표가 수정에 실패했어요. 잠시 후 다시 시도해 주세요.");
        return;
      }
      setEditingWishlistId(null);
      setEditingPriceInput("");
      setActionNotice(`목표가를 ${num.toLocaleString()}원으로 수정했어요.`);
      onShowToast?.("목표가를 수정했어요");
      await onSyncComplete?.();
    } catch {
      setActionNotice("네트워크 오류로 목표가를 수정하지 못했어요.");
    }
  };

  const handleAddFromUrl = async (e: React.FormEvent) => {
    e.preventDefault();
    const cleanUrl = urlInput.trim();
    const numPrice = parseInt(urlTargetPrice.replace(/[^0-9]/g, ""), 10);
    if (!/^https?:\/\//i.test(cleanUrl)) {
      setActionNotice("http:// 또는 https:// 로 시작하는 상품 URL을 입력해 주세요.");
      return;
    }
    if (!Number.isFinite(numPrice) || numPrice < 100) {
      setActionNotice("목표가는 100원 이상으로 입력해 주세요.");
      return;
    }
    if (submittingUrl) return;
    setSubmittingUrl(true);
    setActionNotice(null);
    try {
      await ensureWebDeviceAuth();
      const res = await authFetch("/api/wishlist/from-url", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ product_url: cleanUrl, target_price: numPrice }),
      });
      if (!res.ok) {
        setActionNotice("URL 찜 추가에 실패했어요. 잠시 후 다시 시도해 주세요.");
        return;
      }
      setUrlInput("");
      setUrlTargetPrice("");
      setShowUrlAddForm(false);
      setActionNotice("URL 기반 찜 알림을 추가했어요.");
      onShowToast?.("링크로 찜 항목을 추가했어요");
      await onSyncComplete?.();
    } catch {
      setActionNotice("네트워크 오류로 URL 찜 추가에 실패했어요.");
    } finally {
      setSubmittingUrl(false);
    }
  };

  const handleExportJsonBackup = () => {
    if (typeof window === "undefined") return;
    const payload = {
      exported_at: new Date().toISOString(),
      wishlist_deals: wishlistDeals,
      server_wishlists: serverWishlists,
    };
    const blob = new Blob([JSON.stringify(payload, null, 2)], {
      type: "application/json;charset=utf-8",
    });
    const blobUrl = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = blobUrl;
    a.download = `insightdeal-wishlist-${new Date().toISOString().slice(0, 10)}.json`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    window.setTimeout(() => URL.revokeObjectURL(blobUrl), 1500);
    onShowToast?.("찜 목록 JSON 백업을 다운로드했어요");
  };

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
        ) : (
          <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
            <div
              style={{
                display: "flex",
                flexDirection: "column",
                gap: "8px",
                padding: "10px 12px",
                borderRadius: "14px",
                background: "var(--surface-subtle)",
                border: "1px solid var(--card-border)",
              }}
            >
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "space-between",
                  gap: "6px",
                  flexWrap: "wrap",
                }}
              >
                <select
                  aria-label="찜 목록 정렬"
                  value={sortMode}
                  onChange={(e) => setSortMode(e.target.value as WishlistSortMode)}
                  style={{
                    minHeight: "36px",
                    padding: "4px 8px",
                    borderRadius: "8px",
                    border: "1px solid var(--card-border)",
                    background: "var(--card-bg)",
                    color: "var(--text-main)",
                    fontSize: "0.78rem",
                    fontWeight: 700,
                  }}
                >
                  <option value="latest">최신 찜 순</option>
                  <option value="price_asc">낮은 가격순</option>
                  <option value="target_near">목표가 근접순</option>
                </select>
                <div style={{ display: "flex", gap: "6px", flexWrap: "wrap" }}>
                  <button
                    type="button"
                    onClick={() => setShowUrlAddForm((v) => !v)}
                    style={{
                      minHeight: "36px",
                      padding: "4px 10px",
                      borderRadius: "8px",
                      border: "1px solid var(--card-border)",
                      background: "var(--card-bg)",
                      color: "var(--accent)",
                      fontSize: "0.76rem",
                      fontWeight: 700,
                      display: "inline-flex",
                      alignItems: "center",
                      gap: "4px",
                    }}
                  >
                    <Link2 size={13} aria-hidden="true" />
                    <span>링크로 찜 추가</span>
                  </button>
                  <button
                    type="button"
                    onClick={handleExportJsonBackup}
                    aria-label="찜 목록 JSON 백업"
                    style={{
                      minHeight: "36px",
                      padding: "4px 10px",
                      borderRadius: "8px",
                      border: "1px solid var(--card-border)",
                      background: "var(--card-bg)",
                      color: "var(--text-sub)",
                      fontSize: "0.76rem",
                      fontWeight: 700,
                      display: "inline-flex",
                      alignItems: "center",
                      gap: "4px",
                    }}
                  >
                    <Download size={13} aria-hidden="true" />
                    <span>JSON 백업</span>
                  </button>
                  {closedWishlistDeals.length > 0 && (
                    <button
                      type="button"
                      onClick={handleCleanupClosed}
                      style={{
                        minHeight: "36px",
                        padding: "4px 10px",
                        borderRadius: "8px",
                        border: "1px solid var(--danger-border)",
                        background: "var(--danger-bg)",
                        color: "var(--danger-text)",
                        fontSize: "0.76rem",
                        fontWeight: 700,
                      }}
                    >
                      종료 항목 정리 ({closedWishlistDeals.length})
                    </button>
                  )}
                </div>
              </div>

              {showUrlAddForm && (
                <form
                  onSubmit={handleAddFromUrl}
                  style={{ display: "flex", flexDirection: "column", gap: "6px", marginTop: "4px" }}
                >
                  <input
                    type="url"
                    value={urlInput}
                    onChange={(e) => setUrlInput(e.target.value)}
                    placeholder="https://... 상품 URL 입력"
                    aria-label="상품 URL 입력"
                    style={{
                      minHeight: "40px",
                      padding: "6px 10px",
                      borderRadius: "8px",
                      border: "1px solid var(--card-border)",
                      background: "var(--card-bg)",
                      color: "var(--text-main)",
                      fontSize: "0.82rem",
                    }}
                  />
                  <div style={{ display: "flex", gap: "6px" }}>
                    <input
                      type="number"
                      min={100}
                      value={urlTargetPrice}
                      onChange={(e) => setUrlTargetPrice(e.target.value)}
                      placeholder="목표가(원)"
                      aria-label="URL 찜 목표 가격"
                      style={{
                        flex: 1,
                        minHeight: "40px",
                        padding: "6px 10px",
                        borderRadius: "8px",
                        border: "1px solid var(--card-border)",
                        background: "var(--card-bg)",
                        color: "var(--text-main)",
                        fontSize: "0.82rem",
                      }}
                    />
                    <button
                      type="submit"
                      disabled={submittingUrl}
                      className="btn-primary"
                      style={{ minHeight: "40px", padding: "0 14px", fontSize: "0.8rem" }}
                    >
                      {submittingUrl ? "추가 중" : "저장"}
                    </button>
                  </div>
                </form>
              )}

              {actionNotice && (
                <div role="status" style={{ fontSize: "0.76rem", fontWeight: 700, color: "var(--accent)" }}>
                  {actionNotice}
                </div>
              )}
            </div>

            {totalCount === 0 ? (
              <div
                style={{
                  display: "flex",
                  flexDirection: "column",
                  alignItems: "center",
                  justifyContent: "center",
                  padding: "36px 16px",
                  gap: "12px",
                  textAlign: "center",
                  color: "var(--text-sub)",
                }}
              >
                <Heart size={44} color="var(--text-sub)" style={{ opacity: 0.3 }} aria-hidden="true" />
                <p style={{ fontWeight: 700 }}>찜한 상품이 없어요</p>
                <p style={{ fontSize: "0.82rem", lineHeight: 1.4 }}>
                  하트 아이콘을 누르거나 위 &lsquo;링크로 찜 추가&rsquo;로 URL을 저장해 보세요.
                </p>
              </div>
            ) : (
            <>
            {sortedWishlistDeals.map((deal) => {
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
                          style={{
                            width: "100%",
                            height: "100%",
                            objectFit: "cover",
                            filter: deal.isClosed ? "grayscale(0.85)" : undefined,
                          }}
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
                        <BrandImagePlaceholder compact shopName={deal.shop_name} />
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
                        {deal.isClosed && (
                          <span className="badge badge-danger" style={{ fontSize: "0.66rem", padding: "1px 6px" }}>
                            품절·종료
                          </span>
                        )}
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

            {syncedOnlyWishlists.map((item) => {
              const isEditingThis = editingWishlistId === item.id;
              return (
              <div
                key={`srv-${item.id}`}
                style={{
                  background: "var(--surface-subtle)",
                  border: "1px solid var(--card-border)",
                  borderRadius: "16px",
                  padding: "10px 12px",
                  display: "flex",
                  flexDirection: "column",
                  gap: "8px",
                }}
              >
              <div style={{ display: "flex", gap: "8px", alignItems: "center" }}>
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
                <button
                  type="button"
                  aria-label={`${item.keyword} 목표가 수정`}
                  title="목표가 수정"
                  onClick={() => {
                    if (isEditingThis) {
                      setEditingWishlistId(null);
                      setEditingPriceInput("");
                    } else {
                      setEditingWishlistId(item.id);
                      setEditingPriceInput(String(Number(item.target_price) || ""));
                    }
                  }}
                  style={{
                    width: 40,
                    height: 44,
                    minWidth: 40,
                    display: "inline-flex",
                    alignItems: "center",
                    justifyContent: "center",
                    color: "var(--accent)",
                    flexShrink: 0,
                  }}
                >
                  <Edit3 size={15} aria-hidden="true" />
                </button>
                {onRemoveServerWishlist && (
                  <button
                    type="button"
                    aria-label={`${item.keyword} 서버 찜 해제`}
                    onClick={() => onRemoveServerWishlist(item.id)}
                    style={{
                      width: 40,
                      height: 44,
                      minWidth: 40,
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
              {isEditingThis && (
                <div style={{ display: "flex", gap: "6px", alignItems: "center" }}>
                  <input
                    type="number"
                    min={100}
                    aria-label={`${item.keyword} 새 목표가`}
                    value={editingPriceInput}
                    onChange={(e) => setEditingPriceInput(e.target.value)}
                    style={{
                      flex: 1,
                      minHeight: "38px",
                      padding: "4px 10px",
                      borderRadius: "8px",
                      border: "1px solid var(--card-border)",
                      background: "var(--card-bg)",
                      color: "var(--text-main)",
                      fontSize: "0.82rem",
                    }}
                  />
                  <button
                    type="button"
                    className="btn-primary"
                    onClick={() => void handleSaveInlineTargetPrice(item.id)}
                    style={{ minHeight: "38px", padding: "0 12px", fontSize: "0.78rem" }}
                  >
                    저장
                  </button>
                </div>
              )}
              </div>
              );
            })}
            </>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
