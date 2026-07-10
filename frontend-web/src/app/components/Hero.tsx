"use client";

import React, { useRef, useEffect } from "react";
import { Smartphone, Download, Flame } from "lucide-react";

export default function Hero() {
  const phoneMockupRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const mockup = phoneMockupRef.current;
    if (!mockup) return;

    const handleMouseMove = (e: MouseEvent) => {
      const rect = mockup.getBoundingClientRect();
      const x = e.clientX - rect.left - rect.width / 2;
      const y = e.clientY - rect.top - rect.height / 2;

      const rotateY = (x / rect.width) * 30;
      const rotateX = -(y / rect.height) * 30;

      mockup.style.transform = `rotateY(${rotateY}deg) rotateX(${rotateX}deg) scale(1.02)`;
    };

    const handleMouseLeave = () => {
      mockup.style.transform = `rotateY(-15deg) rotateX(10deg) scale(1)`;
    };

    const wrapper = mockup.closest(".phone-mockup-wrapper");
    if (wrapper) {
      wrapper.addEventListener("mousemove", handleMouseMove as any);
      wrapper.addEventListener("mouseleave", handleMouseLeave);
    }

    return () => {
      if (wrapper) {
        wrapper.removeEventListener("mousemove", handleMouseMove as any);
        wrapper.removeEventListener("mouseleave", handleMouseLeave);
      }
    };
  }, []);

  return (
    <section className="hero-section">
      <div className="hero-text-area">
        <div className="hero-badge">⚡ AI 실시간 핫딜 무결점 가이드</div>
        <h1 className="hero-title">
          핫딜을 찾는 <span>가장 세련된 방법</span>
        </h1>
        <p className="hero-subtitle">
          뽐뿌, 퀘이사존, 펨코, 클리앙 등 커뮤니티의 핫딜을 AI가 실시간으로 수집하고,
          역대 최저가 달성 확률과 가성비 점수를 판독해 알려드립니다.
          이제 가격 짤림과 뒷북 구매 없이 단번에 스마트 컨슈머로 도약하세요!
        </p>
        <div className="hero-download-buttons">
          <button className="btn-primary" onClick={() => alert("Google Play 출시 준비 중입니다! 🤖")}>
            <Smartphone size={20} />
            <span>Play Store</span>
          </button>
          <button className="btn-primary" style={{ background: "linear-gradient(135deg, #10b981, #059669)", boxShadow: "0 4px 20px rgba(16, 185, 129, 0.25)" }} onClick={() => alert("App Store 출시 준비 중입니다! 🍎")}>
            <Download size={20} />
            <span>App Store</span>
          </button>
        </div>
      </div>

      <div className="phone-mockup-wrapper">
        <div className="phone-mockup" ref={phoneMockupRef}>
          <div className="phone-screen">
            {/* 인앱 프리미엄 UI 섀도잉 */}
            <div className="glass-panel" style={{ padding: "12px", borderRadius: "16px", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <span style={{ fontWeight: 800, color: "var(--accent)", fontSize: "0.9rem" }}>InsightDeal Live</span>
              <span style={{ fontSize: "0.75rem", background: "rgba(255,255,255,0.08)", padding: "2px 6px", borderRadius: "4px" }}>Beta 1.0</span>
            </div>

            <div className="glass-panel" style={{ padding: "14px", borderRadius: "18px", display: "flex", flexDirection: "column", gap: "8px" }}>
              <div style={{ display: "flex", alignItems: "center", gap: "6px" }}>
                <div style={{ width: "8px", height: "8px", borderRadius: "50%", background: "#10b981" }}></div>
                <span style={{ fontSize: "0.75rem", fontWeight: 700, color: "#10b981" }}>실시간 스마트 키워드</span>
              </div>
              <span style={{ fontSize: "0.85rem", fontWeight: 800 }}>"맥북 프로" 최저가 포착 알림</span>
              <span style={{ fontSize: "0.75rem", color: "var(--text-sub)" }}>5초 전 알림 완료</span>
            </div>

            <div style={{ fontSize: "0.75rem", fontWeight: 700, color: "var(--text-sub)", marginTop: "4px" }}>오늘의 강력 추천 딜</div>

            <div className="glass-panel" style={{ borderRadius: "18px", padding: "10px", display: "flex", gap: "10px" }}>
              <div style={{ width: "50px", height: "50px", borderRadius: "8px", background: "#1f2937", display: "flex", alignItems: "center", justifyContent: "center" }}>
                <Flame size={20} color="#f87171" />
              </div>
              <div style={{ display: "flex", flexDirection: "column", justifyContent: "center", flex: 1 }}>
                <span style={{ fontSize: "0.75rem", fontWeight: 700, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>에어팟 프로 2세대 역대급 특가</span>
                <span style={{ fontSize: "0.85rem", fontWeight: 900, color: "#f87171" }}>239,000원</span>
              </div>
            </div>

            <div className="glass-panel" style={{ borderRadius: "18px", padding: "10px", display: "flex", gap: "10px" }}>
              <div style={{ width: "50px", height: "50px", borderRadius: "8px", background: "#1f2937", display: "flex", alignItems: "center", justifyContent: "center" }}>
                <Flame size={20} color="#f87171" />
              </div>
              <div style={{ display: "flex", flexDirection: "column", justifyContent: "center", flex: 1 }}>
                <span style={{ fontSize: "0.75rem", fontWeight: 700, whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>맥북에어 M3 신품 초특가 세일</span>
                <span style={{ fontSize: "0.85rem", fontWeight: 900, color: "#f87171" }}>1,180,000원</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </section>
  );
}
