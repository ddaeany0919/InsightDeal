"use client";

import React from "react";

interface PrivacyModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export default function PrivacyModal({ isOpen, onClose }: PrivacyModalProps) {
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
          "calc(20px + env(safe-area-inset-top, 0px)) 20px calc(20px + env(safe-area-inset-bottom, 0px)) 20px",
        animation: "slideUp 0.2s ease",
      }}
      onClick={onClose}
    >
      <div
        className="glass-panel"
        style={{
          maxWidth: "520px",
          width: "100%",
          borderRadius: "20px",
          padding: "24px",
          maxHeight: "85dvh",
          overflowY: "auto",
          display: "flex",
          flexDirection: "column",
          gap: "16px",
          background: "var(--card-bg)",
          border: "1px solid var(--card-border)",
        }}
        onClick={(e) => e.stopPropagation()}
      >
        <div>
          <h3
            style={{
              fontSize: "1.25rem",
              fontWeight: 800,
              marginBottom: "4px",
              color: "var(--text-main)",
            }}
          >
            개인정보 처리방침
          </h3>
          <span
            style={{
              fontSize: "0.82rem",
              color: "var(--text-sub)",
              fontWeight: 600,
            }}
          >
            기기 내 보안 영역 기반 처리 안내
          </span>
        </div>

        <div
          style={{
            fontSize: "0.9rem",
            lineHeight: "1.6",
            color: "var(--text-sub)",
            display: "flex",
            flexDirection: "column",
            gap: "12px",
          }}
        >
          <p>
            <strong style={{ color: "var(--text-main)" }}>
              1. 생체 인식 정보 처리
            </strong>
            <br />
            인사이트딜은 사용자의 지문 등 생체 정보를 별도로 수집하거나 서버로
            전송하지 않습니다. 인증은 기기 내 보안 영역(Android KeyStore / iOS
            Secure Enclave)에서만 처리됩니다.
          </p>
          <p>
            <strong style={{ color: "var(--text-main)" }}>
              2. 수집 항목 및 이용 목적
            </strong>
            <br />
            키워드 알림과 방해금지 시간대 설정을 위해 기기 식별자 및 알림 설정값을
            저장하며, 연동 해제 또는 탈퇴 요청 시 즉시 파기합니다.
          </p>
        </div>

        <button
          type="button"
          className="btn-primary"
          onClick={onClose}
          style={{
            width: "100%",
            minHeight: "48px",
            justifyContent: "center",
            padding: "14px",
            borderRadius: "12px",
            marginTop: "4px",
          }}
        >
          닫기
        </button>
      </div>
    </div>
  );
}
