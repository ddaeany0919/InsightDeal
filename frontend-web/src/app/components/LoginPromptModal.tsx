"use client";

import React from "react";
import { Smartphone, Download } from "lucide-react";

interface LoginPromptModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export default function LoginPromptModal({ isOpen, onClose }: LoginPromptModalProps) {
  if (!isOpen) return null;

  return (
    <div style={{
      position: "fixed", top: 0, left: 0, right: 0, bottom: 0,
      background: "rgba(15, 23, 42, 0.55)", backdropFilter: "blur(8px)",
      display: "flex", alignItems: "center", justifyContent: "center", zIndex: 2000,
      padding: "20px", animation: "slideUp 0.25s ease"
    }} onClick={onClose}>
      <div className="glass-panel" style={{
        maxWidth: "480px", width: "100%", borderRadius: "20px", padding: "28px",
        maxHeight: "85vh", overflowY: "auto", display: "flex", flexDirection: "column", gap: "20px",
        background: "var(--card-bg)", border: "1px solid var(--card-border)"
      }} onClick={(e) => e.stopPropagation()}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
          <div>
            <h3 style={{ fontSize: "1.35rem", fontWeight: 900, marginBottom: "6px", color: "var(--text-main)" }}>로그인이 필요해요</h3>
            <span style={{ fontSize: "0.85rem", color: "var(--accent)", fontWeight: 800 }}>
              InsightDeal 회원 전용 기능
            </span>
          </div>
          <button onClick={onClose} aria-label="모달 닫기" style={{ background: "var(--surface-subtle)", border: "1px solid var(--card-border)", cursor: "pointer", width: 32, height: 32, borderRadius: "50%", display: "flex", alignItems: "center", justifyContent: "center", color: "var(--text-sub)" }}>
            ✕
          </button>
        </div>

        <p style={{ fontSize: "0.95rem", lineHeight: "1.6", color: "var(--text-sub)" }}>
          찜 목록 동기화와 가격 변동 실시간 알림은 모바일 앱 또는 로그인 상태에서 이용할 수 있어요.
          공식 모바일 앱에서 키워드와 목표가 푸시 알림을 받아보세요.
        </p>

        <div style={{ display: "flex", flexDirection: "column", gap: "12px", marginTop: "4px" }}>
          <button className="btn-primary" style={{ width: "100%", justifyContent: "center", padding: "14px", borderRadius: "14px" }} onClick={() => alert("Google Play 출시 준비 중입니다.")}>
            <Smartphone size={20} />
            <span>Play Store에서 다운로드 (Android)</span>
          </button>
          <button className="btn-primary" style={{ width: "100%", justifyContent: "center", padding: "14px", borderRadius: "14px", background: "linear-gradient(135deg, #10b981, #059669)", boxShadow: "0 4px 14px rgba(16, 185, 129, 0.2)" }} onClick={() => alert("App Store 출시 준비 중입니다.")}>
            <Download size={20} />
            <span>App Store에서 다운로드 (iOS)</span>
          </button>
        </div>

        <button className="btn-primary" onClick={onClose} style={{ width: "100%", justifyContent: "center", padding: "12px", borderRadius: "14px", background: "var(--surface-subtle)", border: "1px solid var(--card-border)", boxShadow: "none" }}>
          <span style={{ color: "var(--text-main)", fontWeight: 700 }}>닫기</span>
        </button>
      </div>
    </div>
  );
}
