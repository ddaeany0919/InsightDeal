"use client";

import React, { useEffect, useState, useRef } from "react";
import { Bell, Trash2 } from "lucide-react";
import Link from "next/link";
import type { Deal } from "../lib/types";
import { formatPrice, decodeHtmlEntities, apiUrl } from "../lib/utils";

interface NotificationDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  deals: Deal[];
  onSelectDeal: (deal: Deal) => void;
  onServerAlertCountChange?: (count: number) => void;
}

interface NotificationLogItem {
  id: string | number;
  title: string;
  keyword: string;
  receivedAt?: number;
  received_at?: string;
  dealUrl?: string;
  deal_url?: string;
}

function formatAlertTime(item: NotificationLogItem, referenceNow: number): string {
  if (item.receivedAt && typeof item.receivedAt === "number" && referenceNow > 0) {
    const diffMins = Math.max(0, Math.floor((referenceNow - item.receivedAt) / 60000));
    if (diffMins < 1) return "방금 전";
    if (diffMins < 60) return `${diffMins}분 전`;
    const diffHours = Math.floor(diffMins / 60);
    if (diffHours < 24) return `${diffHours}시간 전`;
    const d = new Date(item.receivedAt);
    return `${String(d.getMonth() + 1).padStart(2, "0")}.${String(d.getDate()).padStart(2, "0")}`;
  }
  return item.received_at || "최근";
}

export default function NotificationDrawer({
  isOpen,
  onClose,
  deals,
  onSelectDeal,
  onServerAlertCountChange,
}: NotificationDrawerProps) {
  const [serverAlerts, setServerAlerts] = useState<NotificationLogItem[]>([]);
  const [alertIds, setAlertIds] = useState<number[]>([]);
  const [nowTs, setNowTs] = useState<number>(0);
  const onCountChangeRef = useRef(onServerAlertCountChange);

  useEffect(() => {
    onCountChangeRef.current = onServerAlertCountChange;
  }, [onServerAlertCountChange]);

  useEffect(() => {
    if (!isOpen) return;
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [isOpen, onClose]);

  const getDeviceUuid = () => {
    if (typeof window === "undefined") return "";
    return (
      localStorage.getItem("insightdeal_web_device_uuid") ||
      localStorage.getItem("insightdeal_token") ||
      ""
    );
  };

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

    // 백엔드 알림 수신 내역 조회
    const controller = new AbortController();
    const deviceUuid = getDeviceUuid();
    if (deviceUuid) {
      fetch(apiUrl(`/api/users/notifications?user_id=${encodeURIComponent(deviceUuid)}`), {
        signal: controller.signal,
      })
        .then((res) => (res.ok ? res.json() : []))
        .then((data) => {
          if (!controller.signal.aborted && Array.isArray(data)) {
            setServerAlerts(data);
            onCountChangeRef.current?.(data.length);
          }
        })
        .catch(() => {});
    }

    return () => {
      window.clearTimeout(initTimer);
      window.clearInterval(tick);
      controller.abort();
    };
  }, [isOpen]);

  const handleDeleteServerAlert = async (e: React.MouseEvent, alertId: string | number) => {
    e.stopPropagation();
    const deviceUuid = getDeviceUuid();
    if (!deviceUuid) return;
    await fetch(apiUrl(`/api/users/notifications/${alertId}?user_id=${encodeURIComponent(deviceUuid)}`), {
      method: "DELETE",
    }).catch(() => null);
    setServerAlerts((prev) => {
      const next = prev.filter((a) => String(a.id) !== String(alertId));
      onCountChangeRef.current?.(next.length);
      return next;
    });
  };

  const handleClearAllServerAlerts = async () => {
    const deviceUuid = getDeviceUuid();
    if (!deviceUuid) return;
    await fetch(apiUrl(`/api/users/notifications/clear?user_id=${encodeURIComponent(deviceUuid)}`), {
      method: "POST",
    }).catch(() => null);
    setServerAlerts([]);
    onCountChangeRef.current?.(0);
  };

  if (!isOpen) return null;

  const watchedDeals = deals.filter((d) => alertIds.includes(d.id));

  return (
    <div
      style={{
        position: "fixed",
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
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
        className="glass-panel"
        style={{
          width: "100%",
          maxWidth: "420px",
          height: "100%",
          padding: "calc(24px + env(safe-area-inset-top, 0px)) 24px calc(28px + env(safe-area-inset-bottom, 0px)) 24px",
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
            <Bell size={20} color="var(--accent)" />
            <h3 style={{ fontSize: "1.2rem", fontWeight: 800 }}>내 알림 내역</h3>
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

        {/* 1. 키워드 매칭 알림 수신 내역 */}
        {serverAlerts.length > 0 && (
          <div style={{ display: "flex", flexDirection: "column", gap: "10px" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <span style={{ fontSize: "0.8rem", fontWeight: 700, color: "var(--accent)" }}>
                최근 도착한 키워드·목표가 알림 ({serverAlerts.length})
              </span>
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
                <Trash2 size={13} />
                <span>전체 비우기</span>
              </button>
            </div>
            {serverAlerts.slice(0, 12).map((item) => {
              const targetLink = item.dealUrl || item.deal_url || "";
              return (
                <div
                  key={item.id}
                  className="bounce-click"
                  onClick={() => {
                    if (targetLink) {
                      window.open(targetLink, "_blank", "noopener,noreferrer");
                    }
                  }}
                  style={{
                    background: "var(--surface-subtle)",
                    border: "1px solid var(--card-border)",
                    borderRadius: "14px",
                    padding: "14px",
                    display: "flex",
                    flexDirection: "column",
                    gap: "6px",
                    cursor: targetLink ? "pointer" : "default",
                  }}
                >
                  <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: "8px" }}>
                    <span className="deal-source" style={{ fontSize: "0.7rem", padding: "2px 8px" }}>
                      #{item.keyword}
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
                  <span style={{ fontSize: "0.85rem", fontWeight: 700, lineHeight: 1.4, color: "var(--text-main)" }}>
                    {decodeHtmlEntities(item.title)}
                  </span>
                </div>
              );
            })}
          </div>
        )}

        {/* 2. 카드에서 종(🔔) 아이콘으로 켜둔 상품 목록 */}
        <div style={{ display: "flex", flexDirection: "column", gap: "10px", flex: 1 }}>
          <span style={{ fontSize: "0.8rem", fontWeight: 700, color: "var(--text-sub)" }}>
            가격 변동 알림 켜둔 상품 ({watchedDeals.length})
          </span>
          {watchedDeals.length === 0 && serverAlerts.length === 0 ? (
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
            watchedDeals.map((deal) => (
              <div
                key={deal.id}
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
                  display: "flex",
                  flexDirection: "column",
                  gap: "6px",
                  cursor: "pointer",
                }}
              >
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                  <span className="deal-source" style={{ fontSize: "0.7rem", padding: "2px 6px" }}>
                    {deal.community_name}
                  </span>
                  <span style={{ fontSize: "0.85rem", color: "var(--deal-orange)", fontWeight: 800 }}>
                    {formatPrice(deal.price, deal.currency, deal.title)}
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
              </div>
            ))
          )}
        </div>

        <Link href="/settings/notifications" onClick={onClose} style={{ textDecoration: "none" }}>
          <div
            style={{
              marginTop: "12px",
              minHeight: "48px",
              background: "rgba(var(--accent-rgb), 0.1)",
              border: "1px solid rgba(var(--accent-rgb), 0.3)",
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
