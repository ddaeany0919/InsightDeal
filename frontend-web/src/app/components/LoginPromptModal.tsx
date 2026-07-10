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
      background: "rgba(2, 6, 23, 0.85)", backdropFilter: "blur(12px)",
      display: "flex", alignItems: "center", justifyContent: "center", zIndex: 2000,
      padding: "20px", animation: "slideUp 0.3s ease"
    }} onClick={onClose}>
      <div className="glass-panel" style={{
        maxWidth: "500px", width: "100%", borderRadius: "28px", padding: "32px",
        maxHeight: "85vh", overflowY: "auto", display: "flex", flexDirection: "column", gap: "24px",
        border: "1px solid rgba(255,255,255,0.08)"
      }} onClick={(e) => e.stopPropagation()}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start" }}>
          <div>
            <h3 style={{ fontSize: "1.4rem", fontWeight: 900, marginBottom: "6px" }}>로그인이 필요합니다 🔐</h3>
            <span style={{ fontSize: "0.85rem", color: "var(--accent)", fontWeight: 800 }}>
              ✓ InsightDeal 회원 전용 기능
            </span>
          </div>
          <button onClick={onClose} style={{ background: "rgba(255,255,255,0.05)", border: "none", cursor: "pointer", width: 32, height: 32, borderRadius: "50%", display: "flex", alignItems: "center", justifyContent: "center", color: "var(--text-sub)" }}>
            ✕
          </button>
        </div>

        <p style={{ fontSize: "0.95rem", lineHeight: "1.6", color: "var(--text-sub)" }}>
          즐겨찾기(찜) 및 가격 변동 실시간 알림 기능은 모바일 앱 또는 로그인 상태에서만 완벽히 지원됩니다.
          지금 즉시 공식 모바일 앱을 설치하시고 0.1초 만에 최저가 핫딜 키워드 푸시 알림을 무료로 받아보세요!
        </p>

        <div style={{ display: "flex", flexDirection: "column", gap: "12px", marginTop: "8px" }}>
          <button className="btn-primary" style={{ width: "100%", justifyContent: "center", padding: "14px", borderRadius: "16px" }} onClick={() => alert("Google Play 출시 준비 중입니다! 🤖")}>
            <Smartphone size={20} />
            <span>Play Store에서 다운로드 (Android)</span>
          </button>
          <button className="btn-primary" style={{ width: "100%", justifyContent: "center", padding: "14px", borderRadius: "16px", background: "linear-gradient(135deg, #10b981, #059669)", boxShadow: "0 4px 20px rgba(16, 185, 129, 0.25)" }} onClick={() => alert("App Store 출시 준비 중입니다! 🍎")}>
            <Download size={20} />
            <span>App Store에서 다운로드 (iOS)</span>
          </button>
        </div>

        <button className="btn-primary" onClick={onClose} style={{ width: "100%", justifyContent: "center", padding: "12px", borderRadius: "16px", background: "rgba(255,255,255,0.05)", border: "1px solid var(--card-border)", boxShadow: "none" }}>
          <span style={{ color: "var(--text-main)" }}>닫기</span>
        </button>
      </div>
    </div>
  );
}
