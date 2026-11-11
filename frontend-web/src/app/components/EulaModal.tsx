"use client";

import React from "react";

interface EulaModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export default function EulaModal({ isOpen, onClose }: EulaModalProps) {
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
            서비스 이용약관
          </h3>
          <span
            style={{
              fontSize: "0.82rem",
              color: "var(--text-sub)",
              fontWeight: 600,
            }}
          >
            최종 사용자 이용 안내
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
              1. 가격 및 재고 안내
            </strong>
            <br />
            인사이트딜은 주요 커뮤니티에 등록된 특가 정보를 모아 제공합니다. 판매처
            사정에 따라 품절이나 쿠폰 종료로 실제 결제 금액이 달라질 수 있으니 구매
            전 최종 가격을 확인해 주세요.
          </p>
          <p>
            <strong style={{ color: "var(--text-main)" }}>
              2. 비정상 이용 제한
            </strong>
            <br />
            비정상적인 자동화 요청, 과도한 트래픽 유발, 타인의 권리를 침해하는 행위가
            확인될 경우 사전 안내 없이 이용이 제한될 수 있습니다.
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
