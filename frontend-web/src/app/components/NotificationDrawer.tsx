"use client";

import React, { useEffect, useState, useRef, useId, useCallback } from "react";
import { Bell, CheckCheck, Trash2, RefreshCw } from "lucide-react";
import Link from "next/link";
import type { Deal, NotificationLogItem, WishlistApiItem } from "../lib/types";
import {
  formatPrice,
  decodeHtmlEntities,
  ensureWebDeviceAuth,
  authFetch,
  formatRelativeTimeKo,
  isSafeHttpUrl,
  loadWishlistIdMap,
} from "../lib/utils";
import { openExternalUrlInToss } from "../lib/tossBridge";
import { useDialogA11y } from "../lib/useDialogA11y";

interface NotificationDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  deals: Deal[];
  onSelectDeal: (deal: Deal) => void;
  onSearchKeyword?: (keyword: string) => void;
  onServerAlertCountChange?: (count: number) => void;
}

function formatAlertTime(item: NotificationLogItem, referenceNow: number): string {
  const rawTime =
    typeof item.receivedAt === "number" ? item.receivedAt : item.received_at;
  return formatRelativeTimeKo(rawTime, referenceNow || Date.now(), "최근");
}

const READ_NOTIF_STORAGE_KEY = "insightdeal_read_notif_ids";

function loadReadNotifIds(): Set<string> {
  if (typeof window === "undefined") return new Set();
  try {
    const raw = localStorage.getItem(READ_NOTIF_STORAGE_KEY);
    const parsed = raw ? JSON.parse(raw) : [];
    return Array.isArray(parsed) ? new Set(parsed.map(String)) : new Set();
  } catch {
    return new Set();
  }
}

export default function NotificationDrawer({
  isOpen,
  onClose,
  deals,
  onSelectDeal,
  onSearchKeyword,
  onServerAlertCountChange,
}: NotificationDrawerProps) {
  const [serverAlerts, setServerAlerts] = useState<NotificationLogItem[]>([]);
  const [serverTargetWishlists, setServerTargetWishlists] = useState<WishlistApiItem[]>([]);
  const [alertIds, setAlertIds] = useState<number[]>([]);
  const [readIds, setReadIds] = useState<Set<string>>(() => loadReadNotifIds());
  const [nowTs, setNowTs] = useState<number>(0);
  const [fetchError, setFetchError] = useState<string | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const persistReadIds = (nextSet: Set<string>) => {
    setReadIds(nextSet);
    if (typeof window !== "undefined") {
      try {
        localStorage.setItem(
          READ_NOTIF_STORAGE_KEY,
          JSON.stringify(Array.from(nextSet).slice(-300))
        );
      } catch {}
    }
  };

  const markAlertRead = (alertId: string | number) => {
    const key = String(alertId);
    if (readIds.has(key)) return;
    const next = new Set(readIds);
    next.add(key);
    persistReadIds(next);
  };

  const handleMarkAllRead = () => {
    const next = new Set(readIds);
    serverAlerts.forEach((a) => next.add(String(a.id)));
    persistReadIds(next);
  };

  const panelRef = useRef<HTMLDivElement>(null);
  const titleId = useId();
  const onCountChangeRef = useRef(onServerAlertCountChange);

  useEffect(() => {
    onCountChangeRef.current = onServerAlertCountChange;
  }, [onServerAlertCountChange]);

  useDialogA11y({ isOpen, onClose, containerRef: panelRef });

  const loadNotifications = useCallback(async (signal?: AbortSignal) => {
    setIsLoading(true);
    setFetchError(null);
    setActionError(null);
    try {
      await ensureWebDeviceAuth();
      if (signal?.aborted) return;

      const [notifRes, wishlistRes] = await Promise.allSettled([
        authFetch(
          (latestAuth) =>
            `/api/users/notifications?user_id=${encodeURIComponent(latestAuth.deviceUuid)}`,
          { signal }
        ),
        authFetch(
          (latestAuth) =>
            `/api/wishlist/?user_id=${encodeURIComponent(latestAuth.deviceUuid)}`,
          { signal }
        ),
      ]);
      if (signal?.aborted) return;

      let notifFailed = true;
      if (notifRes.status === "fulfilled" && notifRes.value.ok) {
        const data = await notifRes.value.json().catch(() => null);
        if (!signal?.aborted && Array.isArray(data)) {
          notifFailed = false;
          setServerAlerts(data as NotificationLogItem[]);
          onCountChangeRef.current?.(data.length);
        }
      }

      if (wishlistRes.status === "fulfilled" && wishlistRes.value.ok) {
        const wData = await wishlistRes.value.json().catch(() => null);
        if (!signal?.aborted && Array.isArray(wData)) {
          const activeTargets = (wData as WishlistApiItem[]).filter(
            (w) => w.alert_enabled !== false && Number(w.target_price) > 0
          );
          setServerTargetWishlists(activeTargets);
        }
      }

      if (notifFailed) {
        setFetchError("알림 내역을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.");
      }
    } catch (err) {
      if (signal?.aborted) return;
      if (err instanceof DOMException && err.name === "AbortError") return;
      setFetchError("알림 내역을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      if (!signal?.aborted) {
        setIsLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    if (!isOpen || typeof window === "undefined") return;

    const syncLocalState = () => {
      setNowTs(Date.now());
      try {
        const savedAlerts = localStorage.getItem("insightdeal_alerts");
        setAlertIds(savedAlerts ? JSON.parse(savedAlerts) : []);
      } catch {
        setAlertIds([]);
      }
    };

    const initTimer = window.setTimeout(syncLocalState, 0);
    const tick = window.setInterval(() => setNowTs(Date.now()), 60000);

    const controller = new AbortController();
    void loadNotifications(controller.signal);

    return () => {
      window.clearTimeout(initTimer);
      window.clearInterval(tick);
      controller.abort();
    };
  }, [isOpen, loadNotifications]);

  const handleDeleteServerAlert = async (
    e: React.MouseEvent,
    alertId: string | number
  ) => {
    e.stopPropagation();
    setActionError(null);
    try {
      await ensureWebDeviceAuth();
      const res = await authFetch(
        (latestAuth) =>
          `/api/users/notifications/${encodeURIComponent(String(alertId))}?user_id=${encodeURIComponent(latestAuth.deviceUuid)}`,
        { method: "DELETE" }
      );
      if (!res.ok) {
        setActionError("알림을 삭제하지 못했어요. 잠시 후 다시 시도해 주세요.");
        return;
      }
      setServerAlerts((prev) => {
        const next = prev.filter((a) => String(a.id) !== String(alertId));
        onCountChangeRef.current?.(next.length);
        return next;
      });
    } catch {
      setActionError("알림을 삭제하지 못했어요. 잠시 후 다시 시도해 주세요.");
    }
  };

  const handleClearAllServerAlerts = async () => {
    setActionError(null);
    try {
      await ensureWebDeviceAuth();
      const res = await authFetch(
        (latestAuth) =>
          `/api/users/notifications/clear?user_id=${encodeURIComponent(latestAuth.deviceUuid)}`,
        { method: "POST" }
      );
      if (!res.ok) {
        setActionError("알림 전체 비우기에 실패했어요. 잠시 후 다시 시도해 주세요.");
        return;
      }
      setServerAlerts([]);
      onCountChangeRef.current?.(0);
    } catch {
      setActionError("알림 전체 비우기에 실패했어요. 잠시 후 다시 시도해 주세요.");
    }
  };

  if (!isOpen) return null;

  const idMap = loadWishlistIdMap();
  const matchedDealIdsFromServer = new Set<number>();
  for (const [dealKey, entry] of Object.entries(idMap)) {
    if (serverTargetWishlists.some((w) => w.id === entry.wishlistId)) {
      const parsedId = Number(dealKey.split("_")[0]);
      if (Number.isFinite(parsedId)) matchedDealIdsFromServer.add(parsedId);
    }
  }

  const watchedDeals = deals.filter(
    (d) => alertIds.includes(d.id) || matchedDealIdsFromServer.has(d.id)
  );
  const watchedIds = new Set(watchedDeals.map((d) => d.id));
  const serverOnlyTargets = serverTargetWishlists.filter((w) => {
    const matchedDeal = deals.find((d) => {
      if (!watchedIds.has(d.id)) return false;
      const entry = idMap[String(d.id)];
      if (entry && entry.wishlistId === w.id) return true;
      return decodeHtmlEntities(d.title)
        .toLowerCase()
        .includes(decodeHtmlEntities(w.keyword).toLowerCase());
    });
    return !matchedDeal;
  });

  const watchedTotalCount = watchedDeals.length + serverOnlyTargets.length;

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
          gap: "20px",
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
            <Bell size={20} color="var(--accent)" aria-hidden="true" />
            <h3 id={titleId} style={{ fontSize: "1.2rem", fontWeight: 800 }}>
              내 알림 내역
            </h3>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="알림 내역 닫기"
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

        <p style={{ fontSize: "0.86rem", color: "var(--text-sub)", lineHeight: 1.5 }}>
          키워드 알림으로 도착한 내역과 알림을 켜둔 관심 핫딜 목록입니다.
        </p>

        {actionError && (
          <div
            role="alert"
            style={{
              fontSize: "0.82rem",
              fontWeight: 700,
              color: "var(--error-text)",
              background: "var(--error-bg)",
              border: "1px solid var(--error-border)",
              borderRadius: "12px",
              padding: "10px 12px",
            }}
          >
            {actionError}
          </div>
        )}

        {fetchError && (
          <div
            role="alert"
            style={{
              padding: "16px",
              borderRadius: "14px",
              background: "var(--error-bg)",
              border: "1px solid var(--error-border)",
              display: "flex",
              flexDirection: "column",
              gap: "10px",
              alignItems: "flex-start",
            }}
          >
            <span style={{ fontSize: "0.84rem", fontWeight: 700, color: "var(--error-text)" }}>
              {fetchError}
            </span>
            <button
              type="button"
              onClick={() => void loadNotifications()}
              className="btn-primary"
              style={{
                minHeight: "44px",
                padding: "8px 14px",
                fontSize: "0.8rem",
                borderRadius: "10px",
              }}
            >
              <RefreshCw size={14} aria-hidden="true" />
              <span>다시 시도</span>
            </button>
          </div>
        )}

        {isLoading && serverAlerts.length === 0 && !fetchError && (
          <div style={{ fontSize: "0.84rem", color: "var(--text-sub)", padding: "8px 0" }}>
            알림 내역을 불러오는 중...
          </div>
        )}

        {/* 1. 키워드 매칭 알림 수신 내역 */}
        {serverAlerts.length > 0 && (() => {
          const unreadCount = serverAlerts.filter(
            (a) => !readIds.has(String(a.id))
          ).length;
          return (
          <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: "8px", flexWrap: "wrap" }}>
              <span style={{ fontSize: "0.8rem", fontWeight: 700, color: "var(--accent)" }}>
                최근 도착한 키워드·목표가 알림 ({serverAlerts.length}
                {unreadCount > 0 ? ` · 미읽음 ${unreadCount}` : ""})
              </span>
              <div style={{ display: "flex", alignItems: "center", gap: "4px" }}>
                {unreadCount > 0 && (
                  <button
                    type="button"
                    onClick={handleMarkAllRead}
                    style={{
                      background: "none",
                      border: "none",
                      color: "var(--accent)",
                      fontSize: "0.74rem",
                      fontWeight: 700,
                      cursor: "pointer",
                      display: "inline-flex",
                      alignItems: "center",
                      gap: "4px",
                      minHeight: "44px",
                      padding: "0 6px",
                    }}
                  >
                    <CheckCheck size={13} aria-hidden="true" />
                    <span>모두 읽음</span>
                  </button>
                )}
                <button
                  type="button"
                  onClick={handleClearAllServerAlerts}
                  style={{
                    background: "none",
                    border: "none",
                    color: "var(--text-sub)",
                    fontSize: "0.74rem",
                    fontWeight: 700,
                    cursor: "pointer",
                    display: "inline-flex",
                    alignItems: "center",
                    gap: "4px",
                    minHeight: "44px",
                    padding: "0 6px",
                  }}
                >
                  <Trash2 size={13} aria-hidden="true" />
                  <span>전체 비우기</span>
                </button>
              </div>
            </div>
            {serverAlerts.map((item) => {
              const rawLink = (item.dealUrl || item.deal_url || "").trim();
              const hasSafeLink = !rawLink.startsWith("/") && isSafeHttpUrl(rawLink);
              const isRead = readIds.has(String(item.id));
              return (
                <div
                  key={item.id}
                  onClick={() => markAlertRead(item.id)}
                  style={{
                    background: "var(--surface-subtle)",
                    border: isRead
                      ? "1px solid var(--card-border)"
                      : "1px solid var(--accent-soft-border)",
                    borderRadius: "14px",
                    padding: "12px 14px",
                    display: "flex",
                    flexDirection: "column",
                    gap: "6px",
                    opacity: isRead ? 0.8 : 1,
                  }}
                >
                  <div
                    style={{
                      display: "flex",
                      justifyContent: "space-between",
                      alignItems: "center",
                      gap: "8px",
                    }}
                  >
                    <span
                      className="deal-source"
                      style={{
                        fontSize: "0.7rem",
                        padding: "2px 8px",
                        display: "inline-flex",
                        alignItems: "center",
                        gap: "5px",
                      }}
                    >
                      {!isRead && (
                        <span
                          aria-label="읽지 않은 알림"
                          style={{
                            width: "6px",
                            height: "6px",
                            borderRadius: "50%",
                            background: "var(--accent)",
                            display: "inline-block",
                          }}
                        />
                      )}
                      <span>#{item.keyword}</span>
                    </span>
                    <div style={{ display: "flex", alignItems: "center", gap: "4px" }}>
                      <span style={{ fontSize: "0.72rem", color: "var(--text-sub)" }}>
                        {formatAlertTime(item, nowTs)}
                      </span>
                      <button
                        type="button"
                        aria-label="알림 삭제"
                        onClick={(e) => handleDeleteServerAlert(e, item.id)}
                        title="알림 삭제"
                        style={{
                          background: "none",
                          border: "none",
                          color: "var(--text-sub)",
                          cursor: "pointer",
                          fontSize: "0.75rem",
                          minWidth: "44px",
                          minHeight: "44px",
                          margin: "-10px -8px -10px 0",
                          display: "inline-flex",
                          alignItems: "center",
                          justifyContent: "center",
                        }}
                      >
                        ✕
                      </button>
                    </div>
                  </div>
                  {hasSafeLink ? (
                    <a
                      href={rawLink}
                      target="_blank"
                      rel="noopener noreferrer"
                      onClick={(e) => {
                        markAlertRead(item.id);
                        openExternalUrlInToss(rawLink, e);
                      }}
                      style={{
                        fontSize: "0.85rem",
                        fontWeight: isRead ? 600 : 700,
                        lineHeight: 1.4,
                        color: "var(--text-main)",
                        minHeight: "44px",
                        display: "inline-flex",
                        alignItems: "center",
                      }}
                    >
                      {decodeHtmlEntities(item.title)}
                    </a>
                  ) : (
                    <span
                      style={{
                        fontSize: "0.85rem",
                        fontWeight: isRead ? 600 : 700,
                        lineHeight: 1.4,
                        color: "var(--text-main)",
                      }}
                    >
                      {decodeHtmlEntities(item.title)}
                    </span>
                  )}
                </div>
              );
            })}
          </div>
          );
        })()}

        {/* 2. 가격 변동 알림 켜둔 상품 (현재 피드 + 서버 동기화 목표가 항목) */}
        <div style={{ display: "flex", flexDirection: "column", gap: "10px", flex: 1 }}>
          <span style={{ fontSize: "0.8rem", fontWeight: 700, color: "var(--text-sub)" }}>
            가격 변동 알림 켜둔 상품 ({watchedTotalCount})
          </span>
          {watchedTotalCount === 0 && serverAlerts.length === 0 && !fetchError && !isLoading ? (
            <div
              style={{
                padding: "36px 16px",
                textAlign: "center",
                color: "var(--text-sub)",
                fontSize: "0.85rem",
                lineHeight: 1.6,
                background: "var(--surface-subtle)",
                borderRadius: "16px",
                border: "1px solid var(--card-border)",
              }}
            >
              아직 도착한 알림이 없어요.
              <br />
              원하는 키워드를 등록하거나 상품 카드의 알림 아이콘을 눌러보세요.
            </div>
          ) : (
            <>
              {watchedDeals.map((deal) => (
                <button
                  key={deal.id}
                  type="button"
                  className="bounce-click"
                  onClick={() => {
                    onClose();
                    onSelectDeal(deal);
                  }}
                  style={{
                    background: "var(--surface-subtle)",
                    border: "1px solid var(--card-border)",
                    borderRadius: "16px",
                    padding: "14px",
                    minHeight: "44px",
                    display: "flex",
                    flexDirection: "column",
                    gap: "6px",
                    cursor: "pointer",
                    textAlign: "left",
                    width: "100%",
                  }}
                >
                  <div
                    style={{
                      display: "flex",
                      justifyContent: "space-between",
                      alignItems: "center",
                      width: "100%",
                    }}
                  >
                    <span className="deal-source" style={{ fontSize: "0.7rem", padding: "2px 6px" }}>
                      {deal.community_name}
                    </span>
                    <span style={{ fontSize: "0.85rem", color: "var(--deal-orange)", fontWeight: 800 }}>
                      {deal.isFree ? "0원 (무료)" : formatPrice(deal.price, deal.currency, deal.title)}
                    </span>
                  </div>
                  <span
                    style={{
                      fontSize: "0.85rem",
                      fontWeight: 700,
                      lineHeight: 1.4,
                      color: "var(--text-main)",
                      display: "-webkit-box",
                      WebkitLineClamp: 2,
                      WebkitBoxOrient: "vertical",
                      overflow: "hidden",
                    }}
                  >
                    {decodeHtmlEntities(deal.title)}
                  </span>
                </button>
              ))}

              {serverOnlyTargets.map((item) => (
                <button
                  key={`srv-alert-${item.id}`}
                  type="button"
                  className="bounce-click"
                  onClick={() => {
                    onClose();
                    onSearchKeyword?.(item.keyword);
                  }}
                  style={{
                    background: "var(--surface-subtle)",
                    border: "1px solid var(--card-border)",
                    borderRadius: "16px",
                    padding: "14px",
                    minHeight: "44px",
                    display: "flex",
                    flexDirection: "column",
                    gap: "6px",
                    cursor: "pointer",
                    textAlign: "left",
                    width: "100%",
                  }}
                >
                  <div
                    style={{
                      display: "flex",
                      justifyContent: "space-between",
                      alignItems: "center",
                      width: "100%",
                    }}
                  >
                    <span className="badge badge-mint">동기화된 목표가 알림</span>
                    <span style={{ fontSize: "0.82rem", color: "var(--success-text)", fontWeight: 800 }}>
                      {Number(item.target_price).toLocaleString()}원 이하
                    </span>
                  </div>
                  <span
                    style={{
                      fontSize: "0.85rem",
                      fontWeight: 700,
                      lineHeight: 1.4,
                      color: "var(--text-main)",
                    }}
                  >
                    {decodeHtmlEntities(item.keyword)}
                  </span>
                </button>
              ))}
            </>
          )}
        </div>

        <Link href="/settings/notifications" onClick={onClose} style={{ textDecoration: "none" }}>
          <div
            style={{
              marginTop: "12px",
              minHeight: "48px",
              background: "var(--accent-soft-bg)",
              border: "1px solid var(--accent-soft-border)",
              borderRadius: "16px",
              padding: "13px 16px",
              textAlign: "center",
              fontSize: "0.88rem",
              fontWeight: 800,
              color: "var(--accent)",
              cursor: "pointer",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              gap: "6px",
            }}
            className="bounce-click"
          >
            키워드 알림 설정하기
          </div>
        </Link>
      </div>
    </div>
  );
}
