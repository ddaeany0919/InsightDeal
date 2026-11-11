"use client";

import React, { useRef, useEffect, useState } from "react";
import { Smartphone, Download, Flame } from "lucide-react";

interface HeroProps {
  onNotify?: (message: string) => void;
}

export default function Hero({ onNotify }: HeroProps) {
  const phoneMockupRef = useRef<HTMLDivElement>(null);
  const [inlineToast, setInlineToast] = useState<string | null>(null);
  const toastTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const notify = (message: string) => {
    if (onNotify) {
      onNotify(message);
      return;
    }
    if (toastTimerRef.current) {
      clearTimeout(toastTimerRef.current);
    }
    setInlineToast(message);
    toastTimerRef.current = setTimeout(() => {
      setInlineToast(null);
      toastTimerRef.current = null;
    }, 2500);
  };

  useEffect(() => {
    const mockup = phoneMockupRef.current;
    if (!mockup) return;

    const handleMouseMove = (e: MouseEvent) => {
      const rect = mockup.getBoundingClientRect();
      const x = e.clientX - rect.left - rect.width / 2;
      const y = e.clientY - rect.top - rect.height / 2;

      const rotateY = (x / rect.width) * 20;
      const rotateX = -(y / rect.height) * 20;

      mockup.style.transform = `rotateY(${rotateY}deg) rotateX(${rotateX}deg)`;
    };

    const handleMouseLeave = () => {
      mockup.style.transform = `rotateY(-12deg) rotateX(8deg)`;
    };

    const wrapper = mockup.closest<HTMLElement>(".phone-mockup-wrapper");
    if (wrapper) {
      wrapper.addEventListener("mousemove", handleMouseMove);
      wrapper.addEventListener("mouseleave", handleMouseLeave);
    }

    return () => {
      if (wrapper) {
        wrapper.removeEventListener("mousemove", handleMouseMove);
        wrapper.removeEventListener("mouseleave", handleMouseLeave);
      }
      if (toastTimerRef.current) {
        clearTimeout(toastTimerRef.current);
      }
    };
  }, []);

  return (
    <section className="hero-section">
      <div className="hero-text-area">
        <div className="hero-badge">실시간 커뮤니티 특가 모아보기</div>
        <h1 className="hero-title">
          흩어진 핫딜을 <span>한눈에 비교하세요</span>
        </h1>
        <p className="hero-subtitle">
          뽐뿌, 펨코, 퀘이사존, 클리앙 등 주요 커뮤니티에 올라오는 특가를 모아
          이전 가격 추이와 구매 조건을 한곳에서 보여드립니다.
        </p>
        <div className="hero-download-buttons">
          <button
            type="button"
            className="btn-primary"
            style={{ minHeight: "44px" }}
            onClick={() => notify("Play Store 출시를 준비하고 있어요")}
          >
            <Smartphone size={18} />
            <span>Google Play</span>
          </button>
          <button
            type="button"
            className="btn-primary"
            style={{
              minHeight: "44px",
              background: "var(--surface-subtle)",
              color: "var(--text-main)",
              border: "1px solid var(--card-border)",
              boxShadow: "none",
            }}
            onClick={() => notify("App Store 출시를 준비하고 있어요")}
          >
            <Download size={18} color="var(--text-main)" />
            <span style={{ color: "var(--text-main)" }}>App Store</span>
          </button>
        </div>
        {inlineToast && (
          <div
            role="status"
            aria-live="polite"
            style={{
              fontSize: "0.85rem",
              fontWeight: 600,
              color: "var(--text-main)",
              background: "var(--surface-subtle)",
              border: "1px solid var(--card-border)",
              borderRadius: "10px",
              padding: "10px 14px",
              alignSelf: "flex-start",
            }}
          >
            {inlineToast}
          </div>
        )}
      </div>

      <div className="phone-mockup-wrapper">
        <div className="phone-mockup" ref={phoneMockupRef}>
          <div className="phone-screen">
            <div className="glass-panel" style={{ padding: "12px", borderRadius: "14px", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <span style={{ fontWeight: 800, color: "var(--accent)", fontSize: "0.88rem" }}>InsightDeal</span>
              <span style={{ fontSize: "0.72rem", color: "var(--text-sub)", fontWeight: 600 }}>실시간</span>
            </div>

            <div className="glass-panel" style={{ padding: "14px", borderRadius: "14px", display: "flex", flexDirection: "column", gap: "6px" }}>
              <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
                <div style={{ width: "7px", height: "7px", borderRadius: "50%", background: "#10b981" }}></div>
                <span style={{ fontSize: "0.74rem", fontWeight: 700, color: "#10b981" }}>키워드 알림</span>
              </div>
              <span style={{ fontSize: "0.84rem", fontWeight: 700 }}>&ldquo;맥북 프로&rdquo; 새 특가 등록</span>
              <span style={{ fontSize: "0.74rem", color: "var(--text-sub)" }}>방금 전</span>
            </div>

            <div style={{ fontSize: "0.74rem", fontWeight: 700, color: "var(--text-sub)", marginTop: "4px" }}>최근 인기 특가</div>

            <div className="glass-panel" style={{ borderRadius: "14px", padding: "10px", display: "flex", gap: "10px" }}>
              <div style={{ width: "48px", height: "48px", borderRadius: "8px", background: "#1f2937", display: "flex", alignItems: "center", justifyContent: "center" }}>
                <Flame size={18} color="var(--deal-orange)" />
              </div>
              <div style={{ display: "flex", flexDirection: "column", justifyContent: "center", flex: 1, minWidth: 0 }}>
                <span style={{ fontSize: "0.75rem", fontWeight: 700, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>에어팟 프로 2세대 USB-C</span>
                <span style={{ fontSize: "0.84rem", fontWeight: 800, color: "var(--deal-orange)" }}>239,000원</span>
              </div>
            </div>

            <div className="glass-panel" style={{ borderRadius: "14px", padding: "10px", display: "flex", gap: "10px" }}>
              <div style={{ width: "48px", height: "48px", borderRadius: "8px", background: "#1f2937", display: "flex", alignItems: "center", justifyContent: "center" }}>
                <Flame size={18} color="var(--deal-orange)" />
              </div>
              <div style={{ display: "flex", flexDirection: "column", justifyContent: "center", flex: 1, minWidth: 0 }}>
                <span style={{ fontSize: "0.75rem", fontWeight: 700, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>맥북 에어 M3 13인치 기본형</span>
                <span style={{ fontSize: "0.84rem", fontWeight: 800, color: "var(--deal-orange)" }}>1,180,000원</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
