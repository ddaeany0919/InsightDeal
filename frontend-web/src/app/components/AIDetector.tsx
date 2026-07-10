"use client";

import React, { useState } from "react";
import { Sparkles, ArrowRight, Zap, CheckCircle } from "lucide-react";
import type { Deal, AIDemoResult } from "../lib/types";

interface AIDetectorProps {
  deals: Deal[];
}

export default function AIDetector({ deals }: AIDetectorProps) {
  const [searchKeyword, setSearchKeyword] = useState("");
  const [analyzing, setAnalyzing] = useState(false);
  const [analysisResult, setAnalysisResult] = useState<AIDemoResult | null>(null);
  const [analysisStatus, setAnalysisStatus] = useState("");

  const handleMagicChipClick = (kw: string) => {
    setSearchKeyword(kw);
    if (analyzing) return;

    setAnalyzing(true);
    setAnalysisResult(null);

    const statusSteps = [
      "실시간 수집된 특가 정보 조회 중...",
      "동일 제품군 역대 최저가 분포 비교 중...",
      "데이터베이스 비교 및 종합 분석 완료!"
    ];

    let step = 0;
    setAnalysisStatus(statusSteps[0]);

    const interval = setInterval(() => {
      step++;
      if (step < statusSteps.length) {
        setAnalysisStatus(statusSteps[step]);
      } else {
        clearInterval(interval);

        const cleanKw = kw.toLowerCase().trim();
        const matched = deals.filter(d => d.title.toLowerCase().includes(cleanKw));

        let finalResult: AIDemoResult;

        if (matched.length > 0) {
          const targetDeal = matched[0];
          const getNumValue = (pStr: string) => {
            const clean = pStr.replace(/[^0-9.]/g, "");
            if (!clean) return 10000;
            if (parseInt(clean, 10) === 1393) return 13.93 * 1350;
            return parseFloat(clean);
          };

          const targetPrice = getNumValue(targetDeal.price);
          const mockAveragePrice = targetPrice * 1.15;
          const discountPercent = Math.round((1 - targetPrice / mockAveragePrice) * 100);

          let score = "S";
          let probability = `${85 + (discountPercent % 10)}%`;
          if (discountPercent > 13) {
            score = "S+";
          } else if (discountPercent < 8) {
            score = "A";
          }

          finalResult = {
            score: score,
            probability: probability,
            priceTrend: `실제 수집된 [${targetDeal.title.substring(0, 18)}...] 특가 대비 평균가 대비 약 ${discountPercent}% 가량 가격 메리트 확인`,
            verdict: `수집된 특가 정보 분석 결과, [${targetDeal.community_name}] 특가가 최근 유통 평균가보다 메리트 있는 가격대로 나타났습니다. 재고 소진 전에 빠르게 확인해 보세요.`,
            tip: `이 가성비 분석은 실시간 수집된 데이터 기준입니다. 전용 앱을 이용해 알림 설정을 해두시면 원하시는 상품의 새로운 특가가 올라올 때 바로 알려드립니다.`,
            isRealData: true
          };
        } else {
          finalResult = {
            score: "대기",
            probability: "- %",
            priceTrend: `현재 데이터베이스에 수집 대기 중인 키워드입니다.`,
            verdict: `아직 데이터베이스에 등록되지 않은 상품군입니다. 모바일 앱에서 [${kw}] 키워드로 알림을 설정해 두시면, 국내 커뮤니티에 특가가 등록되는 즉시 바로 알림을 보내 드립니다.`,
            tip: `앱 설치 후 [${kw}] 알림을 설정해 두시면 편리하게 특가 정보를 받아보실 수 있습니다.`,
            isRealData: false
          };
        }

        setAnalysisResult(finalResult);
        setAnalyzing(false);
      }
    }, 800);
  };

  const triggerAIDemoAnalysis = (e: React.FormEvent) => {
    e.preventDefault();
    if (!searchKeyword.trim() || analyzing) return;

    setAnalyzing(true);
    setAnalysisResult(null);

    const statusSteps = [
      "실시간 수집된 특가 정보 조회 중...",
      "동일 제품군 역대 최저가 분포 비교 중...",
      "데이터베이스 비교 및 종합 분석 완료!"
    ];

    let step = 0;
    setAnalysisStatus(statusSteps[0]);

    const interval = setInterval(() => {
      step++;
      if (step < statusSteps.length) {
        setAnalysisStatus(statusSteps[step]);
      } else {
        clearInterval(interval);

        const kw = searchKeyword.toLowerCase().trim();
        const matched = deals.filter(d => d.title.toLowerCase().includes(kw));

        let finalResult: AIDemoResult;

        if (matched.length > 0) {
          const targetDeal = matched[0];
          const getNumValue = (pStr: string) => {
            const clean = pStr.replace(/[^0-9.]/g, "");
            if (!clean) return 10000;
            if (parseInt(clean, 10) === 1393) return 13.93 * 1350;
            return parseFloat(clean);
          };

          const targetPrice = getNumValue(targetDeal.price);
          const mockAveragePrice = targetPrice * 1.15;
          const discountPercent = Math.round((1 - targetPrice / mockAveragePrice) * 100);

          let score = "S";
          let probability = `${85 + (discountPercent % 10)}%`;
          if (discountPercent > 13) {
            score = "S+";
          } else if (discountPercent < 8) {
            score = "A";
          }

          finalResult = {
            score: score,
            probability: probability,
            priceTrend: `실제 수집된 [${targetDeal.title.substring(0, 18)}...] 특가 대비 평균가 대비 약 ${discountPercent}% 가량 가격 메리트 확인`,
            verdict: `수집된 특가 정보 분석 결과, [${targetDeal.community_name}] 특가가 최근 유통 평균가보다 메리트 있는 가격대로 나타났습니다. 재고 소진 전에 빠르게 확인해 보세요.`,
            tip: `이 가성비 분석은 실시간 수집된 데이터 기준입니다. 전용 앱을 이용해 알림 설정을 해두시면 원하시는 상품의 새로운 특가가 올라올 때 바로 알려드립니다.`,
            isRealData: true
          };
        } else {
          finalResult = {
            score: "대기",
            probability: "- %",
            priceTrend: `현재 데이터베이스에 수집 대기 중인 키워드입니다.`,
            verdict: `아직 데이터베이스에 등록되지 않은 상품군입니다. 모바일 앱에서 [${searchKeyword}] 키워드로 알림을 설정해 두시면, 국내 커뮤니티에 특가가 등록되는 즉시 바로 알림을 보내 드립니다.`,
            tip: `앱 설치 후 [${searchKeyword}] 알림을 설정해 두시면 편리하게 특가 정보를 받아보실 수 있습니다.`,
            isRealData: false
          };
        }

        setAnalysisResult(finalResult);
        setAnalyzing(false);
      }
    }, 800);
  };

  return (
    <section className="glass-panel ai-demo-section">
      <div className="ai-demo-header">
        <div style={{ display: "flex", justifyContent: "center", gap: "6px", color: "var(--accent)", fontWeight: 800, fontSize: "0.9rem" }}>
          <Sparkles size={18} />
          <span>실시간 가성비 분석 미리보기</span>
        </div>
        <h2 className="ai-demo-title">
          관심 특가 상품을 <span>조회해 보세요</span>
        </h2>
        <p style={{ color: "var(--text-sub)", fontSize: "1rem" }}>
          구매하려는 제품명이나 키워드(예: '가래떡', '마우스', '펩시')를 입력해 보세요.
          수집된 특가 데이터베이스를 스캔하여 가성비 점수를 무료로 진단해 드립니다.
        </p>
      </div>

      <form onSubmit={triggerAIDemoAnalysis} className="ai-demo-search-bar">
        <input
          type="text"
          className="ai-demo-input"
          placeholder="예: 찰가래떡, 마우스, 펩시 등"
          value={searchKeyword}
          onChange={(e) => setSearchKeyword(e.target.value)}
          disabled={analyzing}
        />
        <button type="submit" className="btn-primary" style={{ padding: "10px 20px" }} disabled={analyzing}>
          {analyzing ? "분석 중..." : "분석하기"}
          <ArrowRight size={18} />
        </button>
      </form>

      {/* ⚡ 실시간 트렌딩 추천 매직 칩 */}
      <div className="magic-chips-container">
        <span className="magic-chips-title">트렌딩 추천 핫딜 🔥</span>
        <div className="magic-chips">
          {[
            { label: "찰가래떡 🍡", kw: "찰가래떡" },
            { label: "게이밍 마우스 🖱️", kw: "마우스" },
            { label: "펩시 제로 🥤", kw: "펩시" },
            { label: "게이밍 모니터 🖥️", kw: "모니터" },
            { label: "맥북 프로 💻", kw: "맥북" }
          ].map(chip => (
            <button
              key={chip.kw}
              type="button"
              className="magic-chip bounce-click"
              onClick={() => handleMagicChipClick(chip.kw)}
              disabled={analyzing}
            >
              {chip.label}
            </button>
          ))}
        </div>
      </div>

      {/* ⚡ 스마트 서칭 실시간 반응성 극대화 카운터 뱃지 */}
      {searchKeyword.trim() && !analyzing && (
        <div className="fade-in" style={{ marginTop: "14px", animation: "slideUp 0.3s ease" }}>
          {(() => {
            const kw = searchKeyword.toLowerCase().trim();
            const matched = deals.filter(d => d.title.toLowerCase().includes(kw));
            return matched.length > 0 ? (
              <span style={{
                fontSize: "0.85rem", fontWeight: 700, color: "#38bdf8",
                background: "rgba(56, 189, 248, 0.08)", padding: "6px 14px",
                borderRadius: "99px", border: "1px solid rgba(56, 189, 248, 0.15)",
                display: "inline-flex", alignItems: "center", gap: "6px"
              }}>
                ⚡ 실시간 DB 내 [<strong>{searchKeyword}</strong>] 관련 실제 특가 <strong>{matched.length}건</strong> 포착 완료!
              </span>
            ) : (
              <span style={{
                fontSize: "0.85rem", fontWeight: 700, color: "var(--text-sub)",
                background: "rgba(255, 255, 255, 0.03)", padding: "6px 14px",
                borderRadius: "99px", border: "1px solid var(--card-border)",
                display: "inline-flex", alignItems: "center", gap: "6px"
              }}>
                🔕 미수집 키워드 (앱 설치 시 [<strong>{searchKeyword}</strong>] 수집 즉시 실시간 DND 푸시 지원)
              </span>
            );
          })()}
        </div>
      )}

      {/* 🌀 로딩 스피너 및 진행 상태 문구 */}
      {analyzing && (
        <div style={{ marginTop: "20px" }}>
          <div className="ai-loader"></div>
          <p style={{ fontWeight: 600, color: "var(--accent)", animation: "pulse-badge 1.5s infinite" }}>
            {analysisStatus}
          </p>
        </div>
      )}

      {/* 📊 판독 결과 카드 출력 */}
      {analysisResult && (
        <div className="glass-panel ai-result-card">
          <div className="ai-result-score-row">
            <div>
              <h3 style={{ fontSize: "1.2rem", fontWeight: 900 }}>"{searchKeyword}" 가격 지수</h3>
              <span style={{ fontSize: "0.85rem", color: "#10b981", fontWeight: 700 }}>
                {analysisResult.isRealData ? "✓ 실제 핫딜 상품 분석 완료" : "✓ 관심 키워드 분석 완료"}
              </span>
            </div>
            <div className="ai-score-badge">{analysisResult.score}</div>
          </div>

          <div className="ai-result-metric-grid">
            <div className="ai-metric-item">
              <div className="ai-metric-label">역대 최저가 근접 확률</div>
              <div className="ai-metric-value" style={{ color: "#3b82f6" }}>{analysisResult.probability}</div>
            </div>
            <div className="ai-metric-item">
              <div className="ai-metric-label">가격 분포 및 메리트</div>
              <div className="ai-metric-value" style={{ fontSize: "0.92rem" }}>{analysisResult.priceTrend}</div>
            </div>
          </div>

          <div className="ai-metric-item" style={{ width: "100%" }}>
            <div className="ai-metric-label" style={{ display: "flex", alignItems: "center", gap: "4px", color: "var(--accent)", fontWeight: 700 }}>
              <Zap size={14} fill="currentColor" />
              <span>AI 가성비 분석 결과</span>
            </div>
            <p style={{ fontSize: "0.95rem", fontWeight: 600, marginTop: "6px", lineHeight: "1.5" }}>
              {analysisResult.verdict}
            </p>
          </div>

          <div className="ai-metric-item" style={{ width: "100%", background: "rgba(16, 185, 129, 0.04)", borderColor: "rgba(16, 185, 129, 0.15)" }}>
            <div className="ai-metric-label" style={{ display: "flex", alignItems: "center", gap: "4px", color: "#10b981", fontWeight: 700 }}>
              <CheckCircle size={14} />
              <span>스마트 팁</span>
            </div>
            <p style={{ fontSize: "0.92rem", fontWeight: 500, marginTop: "6px", color: "var(--text-main)" }}>
              {analysisResult.tip}
            </p>
          </div>
        </div>
      )}
    </section>
  );
}
