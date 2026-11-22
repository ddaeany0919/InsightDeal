"use client";

import React, { useEffect, useId, useRef, useState } from "react";
import { MessageSquarePlus, Send, X } from "lucide-react";
import { useDialogA11y } from "../lib/useDialogA11y";
import { authFetch, ensureWebDeviceAuth } from "../lib/utils";
import { hasTossBridge } from "../lib/tossBridge";

interface FeedbackModalProps {
  isOpen: boolean;
  onClose: () => void;
  onShowToast?: (message: string) => void;
}

type FeedbackCategory = "bug" | "feature" | "deal_error" | "general";

const FEEDBACK_CATEGORIES: { key: FeedbackCategory; label: string }[] = [
  { key: "bug", label: "오류 제보" },
  { key: "feature", label: "기능 제안" },
  { key: "deal_error", label: "핫딜 정보 오류" },
  { key: "general", label: "기타 문의" },
];

export default function FeedbackModal({
  isOpen,
  onClose,
  onShowToast,
}: FeedbackModalProps) {
  const panelRef = useRef<HTMLDivElement>(null);
  const titleId = useId();
  const [category, setCategory] = useState<FeedbackCategory>("bug");
  const [message, setMessage] = useState("");
  const [contact, setContact] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  useDialogA11y({ isOpen, onClose, containerRef: panelRef });

  useEffect(() => {
    if (isOpen) {
      setErrorMsg(null);
    }
  }, [isOpen]);

  if (!isOpen) return null;

  const trimmedLen = message.trim().length;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (submitting) return;
    const trimmedMsg = message.trim();
    if (trimmedMsg.length < 5) {
      setErrorMsg("내용을 5자 이상 자세히 적어주세요.");
      return;
    }

    setSubmitting(true);
    setErrorMsg(null);
    try {
      await ensureWebDeviceAuth();
      const platform = hasTossBridge() ? "toss" : "web";
      const res = await authFetch("/api/community/feedback", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          category,
          message: trimmedMsg,
          contact: contact.trim() || null,
          platform,
          app_version: "1.1g",
        }),
      });
      const data = (await res.json().catch(() => null)) as {
        message?: string;
        detail?: string;
      } | null;

      if (res.status === 429) {
        setErrorMsg("잠시 후 다시 시도해 주세요. (요청 횟수 제한)");
        return;
      }
      if (!res.ok) {
        setErrorMsg(
          data?.detail || "의견을 전송하지 못했어요. 잠시 후 다시 시도해 주세요."
        );
        return;
      }

      setMessage("");
      setContact("");
      onShowToast?.(data?.message || "소중한 의견을 접수했어요. 빠르게 검토할게요!");
      onClose();
    } catch {
      setErrorMsg("네트워크 오류로 의견을 보내지 못했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div
      style={{
        position: "fixed",
        inset: 0,
        background: "rgba(15, 23, 42, 0.55)",
        backdropFilter: "blur(8px)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        zIndex: 2000,
        padding:
          "calc(20px + env(safe-area-inset-top, 0px)) 20px calc(20px + env(safe-area-inset-bottom, 0px)) 20px",
        animation: "slideUp 0.2s ease",
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
          maxWidth: "520px",
          width: "100%",
          borderRadius: "20px",
          padding: "24px",
          maxHeight: "88dvh",
          overflowY: "auto",
          overscrollBehavior: "contain",
          display: "flex",
          flexDirection: "column",
          gap: "16px",
          background: "var(--card-bg)",
          border: "1px solid var(--card-border)",
        }}
        onClick={(e) => e.stopPropagation()}
      >
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "flex-start",
            gap: "12px",
          }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
            <div
              style={{
                width: "36px",
                height: "36px",
                borderRadius: "10px",
                background: "var(--accent-soft-bg)",
                color: "var(--accent)",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                flexShrink: 0,
              }}
            >
              <MessageSquarePlus size={18} aria-hidden="true" />
            </div>
            <div>
              <h3
                id={titleId}
                style={{
                  fontSize: "1.18rem",
                  fontWeight: 800,
                  margin: "0 0 2px 0",
                  color: "var(--text-main)",
                }}
              >
                문의 · 오류 제보
              </h3>
              <p style={{ margin: 0, fontSize: "0.82rem", color: "var(--text-sub)" }}>
                불편한 점이나 찾지 못한 상품·오류를 알려주시면 바로 반영할게요
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="문의 및 오류 제보 닫기"
            className="bounce-click"
            style={{
              width: "40px",
              height: "40px",
              minWidth: "40px",
              minHeight: "40px",
              borderRadius: "999px",
              border: "1px solid var(--card-border)",
              background: "var(--surface-subtle)",
              color: "var(--text-main)",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
              cursor: "pointer",
              flexShrink: 0,
            }}
          >
            <X size={18} aria-hidden="true" />
          </button>
        </div>

        <form onSubmit={handleSubmit} style={{ display: "flex", flexDirection: "column", gap: "14px" }}>
          <div style={{ display: "flex", flexWrap: "wrap", gap: "6px" }} role="group" aria-label="제보 유형">
            {FEEDBACK_CATEGORIES.map((item) => {
              const active = category === item.key;
              return (
                <button
                  key={item.key}
                  type="button"
                  aria-pressed={active}
                  onClick={() => setCategory(item.key)}
                  style={{
                    minHeight: "36px",
                    padding: "6px 12px",
                    borderRadius: "10px",
                    border: active ? "1px solid var(--accent)" : "1px solid var(--card-border)",
                    background: active ? "var(--accent-soft-bg)" : "var(--surface-subtle)",
                    color: active ? "var(--accent)" : "var(--text-sub)",
                    fontSize: "0.84rem",
                    fontWeight: 700,
                    cursor: "pointer",
                  }}
                >
                  {item.label}
                </button>
              );
            })}
          </div>

          <div style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <label
                htmlFor="feedback-message-input"
                style={{ fontSize: "0.85rem", fontWeight: 700, color: "var(--text-main)" }}
              >
                상세 내용
              </label>
              <span style={{ fontSize: "0.75rem", color: "var(--text-muted)" }}>
                {trimmedLen} / 1000자
              </span>
            </div>
            <textarea
              id="feedback-message-input"
              rows={4}
              maxLength={1000}
              value={message}
              onChange={(e) => {
                setMessage(e.target.value);
                if (errorMsg) setErrorMsg(null);
              }}
              placeholder="예: '로보락 S9' 검색 시 결과가 안 나와요 / 특정 카드 가격이 달라요"
              style={{
                width: "100%",
                padding: "12px",
                borderRadius: "12px",
                border: "1px solid var(--card-border)",
                background: "var(--surface-subtle)",
                color: "var(--text-main)",
                fontSize: "0.9rem",
                lineHeight: 1.5,
                resize: "vertical",
                fontFamily: "inherit",
              }}
            />
          </div>

          <div style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
            <label
              htmlFor="feedback-contact-input"
              style={{ fontSize: "0.85rem", fontWeight: 700, color: "var(--text-main)" }}
            >
              답변 받을 이메일 (선택)
            </label>
            <input
              id="feedback-contact-input"
              type="text"
              maxLength={120}
              value={contact}
              onChange={(e) => setContact(e.target.value)}
              placeholder="name@example.com"
              style={{
                width: "100%",
                minHeight: "42px",
                padding: "8px 12px",
                borderRadius: "12px",
                border: "1px solid var(--card-border)",
                background: "var(--surface-subtle)",
                color: "var(--text-main)",
                fontSize: "0.88rem",
              }}
            />
          </div>

          {errorMsg && (
            <div
              role="alert"
              style={{
                padding: "10px 12px",
                borderRadius: "10px",
                background: "var(--danger-bg)",
                border: "1px solid var(--danger-border)",
                color: "var(--danger-text)",
                fontSize: "0.84rem",
                fontWeight: 700,
              }}
            >
              {errorMsg}
            </div>
          )}

          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
              gap: "10px",
              flexWrap: "wrap",
            }}
          >
            <a
              href="mailto:support@insightdeal.com?subject=%5B%EC%96%BC%EB%A7%8C%EB%8D%B0%5D%20%EB%AC%B8%EC%9D%98%20%EB%B0%8F%20%EC%98%A4%EB%A5%98%20%EC%A0%9C%EB%B3%B4"
              style={{ fontSize: "0.78rem", color: "var(--text-sub)", textDecoration: "underline" }}
            >
              이메일로 직접 보내기
            </a>
            <button
              type="submit"
              disabled={submitting}
              className="btn-primary bounce-click"
              style={{
                minHeight: "44px",
                padding: "10px 18px",
                borderRadius: "12px",
                display: "inline-flex",
                alignItems: "center",
                gap: "6px",
                cursor: submitting ? "not-allowed" : "pointer",
                opacity: submitting ? 0.65 : 1,
              }}
            >
              <Send size={15} aria-hidden="true" />
              <span>{submitting ? "전송 중..." : "의견 보내기"}</span>
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
