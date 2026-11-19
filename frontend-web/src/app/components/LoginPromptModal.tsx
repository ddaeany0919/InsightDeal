"use client";

import React, { useId, useRef } from "react";
import { useDialogA11y } from "../lib/useDialogA11y";

interface LoginPromptModalProps {
  isOpen: boolean;
  onClose: () => void;
  onActivateGuest?: () => void;
}

export default function LoginPromptModal({
  isOpen,
  onClose,
  onActivateGuest,
}: LoginPromptModalProps) {
  const panelRef = useRef<HTMLDivElement>(null);
  const titleId = useId();

  useDialogA11y({ isOpen, onClose, containerRef: panelRef });

  if (!isOpen) return null;

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
          "max(20px, env(safe-area-inset-top, 0px)) 20px max(20px, env(safe-area-inset-bottom, 0px)) 20px",
        animation: "slideUp 0.25s ease",
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
          maxWidth: "460px",
          width: "100%",
          borderRadius: "20px",
          padding: "28px",
          maxHeight: "85vh",
          overflowY: "auto",
          display: "flex",
          flexDirection: "column",
          gap: "20px",
          background: "var(--card-bg)",
          border: "1px solid var(--card-border)",
        }}
        onClick={(e) => e.stopPropagation()}
      >
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
          <div>
            <h3
              id={titleId}
              style={{
                fontSize: "1.3rem",
                fontWeight: 900,
                marginBottom: "6px",
                color: "var(--text-main)",
              }}
            >
              기기 연동이 필요해요
            </h3>
            <span style={{ fontSize: "0.85rem", color: "var(--accent)", fontWeight: 800 }}>
              InsightDeal 맞춤 기능 안내
            </span>
          </div>
          <button
            type="button"
            onClick={onClose}
            aria-label="모달 닫기"
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

        <p style={{ fontSize: "0.95rem", lineHeight: "1.6", color: "var(--text-sub)" }}>
          이 브라우저에서 기기 연동을 켜면 찜 목록과 키워드·목표가 알림 기능을 별도
          회원가입 없이 바로 이용할 수 있어요.
        </p>

        {onActivateGuest && (
          <button
            type="button"
            className="btn-primary"
            onClick={() => {
              onActivateGuest();
              onClose();
            }}
            style={{
              width: "100%",
              minHeight: "46px",
              justifyContent: "center",
              borderRadius: "14px",
            }}
          >
            이 기기에서 바로 사용하기
          </button>
        )}

        <button
          type="button"
          className="btn-primary"
          onClick={onClose}
          style={{
            width: "100%",
            minHeight: "44px",
            justifyContent: "center",
            padding: "12px",
            borderRadius: "14px",
            background: "var(--surface-subtle)",
            border: "1px solid var(--card-border)",
            boxShadow: "none",
            color: "var(--text-main)",
            fontWeight: 700,
          }}
        >
          닫기
        </button>
      </div>
    </div>
  );
}
