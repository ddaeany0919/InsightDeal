"use client";

import { useState, useEffect } from "react";
import { RefreshCw, Sparkles } from "lucide-react";
import type { PriceHistoryItem } from "../lib/types";
import { apiUrl } from "../lib/utils";

export interface PriceTrendStats {
  minPrice: number;
  avgPrice: number;
  maxPrice: number;
  latestPrice: number;
}

interface PriceTrendChartProps {
  basePrice: any;
  dealId?: number;
  onHistoryStatus?: (hasData: boolean, stats?: PriceTrendStats) => void;
}

const PERIOD_OPTIONS = [
  { key: "7d", label: "7일" },
  { key: "1m", label: "1개월" },
  { key: "3m", label: "3개월" },
  { key: "6m", label: "6개월" },
] as const;

type PeriodKey = (typeof PERIOD_OPTIONS)[number]["key"];

// [react-patterns] React StrictMode 이중 마운트 및 탭 재전환 시 중복 네트워크 호출 방지용 인플라이트 캐시
const historyPromiseCache = new Map<string, { ts: number; promise: Promise<PriceHistoryItem[]> }>();

export default function PriceTrendChart({ basePrice, dealId, onHistoryStatus }: PriceTrendChartProps) {
  const [userPeriod, setUserPeriod] = useState<PeriodKey | null>(null);
  const [autoFellBackTo3m, setAutoFellBackTo3m] = useState(false);
  const [chartData, setChartData] = useState<{ price: number; date: string }[]>([]);
  const [isRealData, setIsRealData] = useState(false);
  const [loading, setLoading] = useState(false);

  const period: PeriodKey = userPeriod ?? (autoFellBackTo3m ? "3m" : "7d");
  const setPeriod = (nextPeriod: PeriodKey) => {
    setUserPeriod(nextPeriod);
  };

  const getCleanPrice = (pVal: any) => {
    if (pVal === null || pVal === undefined) return 10000;
    const clean = String(pVal).replace(/[^0-9.]/g, "");
    if (!clean) return 10000;
    const num = parseFloat(clean);
    if (num <= 0) return 10000;
    if (parseInt(clean, 10) === 1393) return 13.93 * 1350;
    return num;
  };

  const base = getCleanPrice(basePrice);

  useEffect(() => {
    if (!dealId) {
      const dummyPrices = [Math.round(base * 1.15), Math.round(base * 1.08), Math.round(base * 1.0)];
      const dummyDates = ["3일 전", "어제", "오늘"];
      setChartData(dummyPrices.map((p, idx) => ({ price: p, date: dummyDates[idx] })));
      setIsRealData(false);
      onHistoryStatus?.(true, {
        minPrice: Math.min(...dummyPrices),
        avgPrice: Math.round(dummyPrices.reduce((a, b) => a + b, 0) / dummyPrices.length),
        maxPrice: Math.max(...dummyPrices),
        latestPrice: dummyPrices[dummyPrices.length - 1],
      });
      return;
    }

    let isMounted = true;
    setLoading(true);

    // [android-e2e-sequence-audit Gate 2] 최초 진입 시 7d_auto 단일 왕복(1-RTT) API를 호출하여 7d -> 3m 순차 Waterfall 차단
    const queryPeriod = userPeriod ?? "7d_auto";
    const cacheKey = `${dealId}:${queryPeriod}`;

    (async () => {
      try {
        const now = Date.now();
        let cached = historyPromiseCache.get(cacheKey);
        if (!cached || now - cached.ts > 15000) {
          const promise = fetch(apiUrl(`/api/community/deals/${dealId}/history?period=${queryPeriod}`)).then((res) => {
            if (!res.ok) throw new Error("Failed to fetch");
            return res.json() as Promise<PriceHistoryItem[]>;
          });
          cached = { ts: now, promise };
          historyPromiseCache.set(cacheKey, cached);
        }
        const data: PriceHistoryItem[] = await cached.promise;
        if (!isMounted) return;
        if (data && data.length >= 2) {
          if (userPeriod === null) {
            // 7d_auto가 3m 데이터로 자동 확장되었는지 판별 (첫 기록과 마지막 기록의 날짜 차이가 7일 초과인지 확인)
            const parseMmDd = (s: string) => {
              const parts = s.split("/").map(Number);
              if (parts.length === 2 && !isNaN(parts[0]) && !isNaN(parts[1])) {
                return parts[0] * 31 + parts[1];
              }
              return null;
            };
            const firstDay = parseMmDd(data[0].recordedAt);
            const lastDay = parseMmDd(data[data.length - 1].recordedAt);
            if (firstDay !== null && lastDay !== null && Math.abs(lastDay - firstDay) > 8) {
              setAutoFellBackTo3m(true);
            } else {
              setAutoFellBackTo3m(false);
            }
          }
          const formatted = data.map(item => ({
            price: item.price,
            date: item.recordedAt
          }));
          setChartData(formatted);
          setIsRealData(true);
          const pVals = formatted.map(d => d.price);
          onHistoryStatus?.(true, {
            minPrice: Math.min(...pVals),
            avgPrice: Math.round(pVals.reduce((a, b) => a + b, 0) / pVals.length),
            maxPrice: Math.max(...pVals),
            latestPrice: pVals[pVals.length - 1],
          });
        } else {
          setChartData([]);
          setIsRealData(false);
          onHistoryStatus?.(false);
        }
      } catch {
        if (!isMounted) return;
        setChartData([]);
        setIsRealData(false);
        onHistoryStatus?.(false);
      } finally {
        if (isMounted) setLoading(false);
      }
    })();

    return () => {
      isMounted = false;
    };
  }, [dealId, userPeriod]);

  const formatPriceNum = (num: number) => {
    if (num < 150) return `$${num.toFixed(2)}`;
    return `${Math.round(num).toLocaleString()}원`;
  };

  // 1. 로딩 상태
  if (loading) {
    return (
      <div style={{ width: "100%", display: "flex", flexDirection: "column", gap: "16px" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "8px" }}>
          <span style={{ fontSize: "0.9rem", fontWeight: 800, color: "var(--accent)" }}>핫딜 가격 변동</span>
          <div style={{ display: "flex", background: "var(--card-bg)", borderRadius: "10px", padding: "3px", border: "1px solid var(--card-border)", pointerEvents: "none", opacity: 0.5 }}>
            {PERIOD_OPTIONS.map((opt) => (
              <span key={opt.key} style={{ color: "var(--text-sub)", padding: "4px 10px", fontSize: "0.75rem", fontWeight: 800 }}>{opt.label}</span>
            ))}
          </div>
        </div>
        <div style={{ height: 160, display: "flex", alignItems: "center", justifyContent: "center", color: "var(--text-sub)", fontSize: "0.85rem", fontWeight: 700, background: "var(--card-bg)", border: "1px solid var(--card-border)", borderRadius: "16px" }}>
          <RefreshCw className="animate-spin" size={16} style={{ marginRight: "8px" }} />
          가격 추이를 불러오고 있어요...
        </div>
      </div>
    );
  }

  // 2. 데이터가 아예 없거나 부족할 때
  if (chartData.length < 2) {
    return (
      <div style={{ width: "100%", display: "flex", flexDirection: "column", gap: "16px" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: "8px" }}>
          <span style={{ fontSize: "0.9rem", fontWeight: 800, color: "var(--accent)" }}>핫딜 가격 변동</span>
          <div style={{ display: "flex", background: "var(--card-bg)", borderRadius: "10px", padding: "3px", border: "1px solid var(--card-border)" }}>
            {PERIOD_OPTIONS.map((opt) => {
              const isActive = period === opt.key;
              return (
                <button
                  key={opt.key}
                  onClick={() => setPeriod(opt.key)}
                  style={{
                    background: isActive ? "var(--accent)" : "transparent",
                    color: isActive ? "#fff" : "var(--text-sub)",
                    border: "none",
                    borderRadius: "8px",
                    padding: "4px 10px",
                    fontSize: "0.75rem",
                    fontWeight: 800,
                    cursor: "pointer",
                    transition: "all 0.2s"
                  }}
                >
                  {opt.label}
                </button>
              );
            })}
          </div>
        </div>

        <div style={{
          display: "flex",
          flexDirection: "column",
          alignItems: "center",
          justifyContent: "center",
          padding: "24px 16px",
          background: "var(--card-bg)",
          border: "1px dashed var(--card-border)",
          borderRadius: "16px",
          textAlign: "center",
          minHeight: "160px",
          gap: "12px"
        }}>
          <div style={{
            width: "36px",
            height: "36px",
            borderRadius: "50%",
            background: "rgba(var(--accent-rgb), 0.1)",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            color: "var(--accent)"
          }}>
            <Sparkles size={18} fill="currentColor" />
          </div>
          <div style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
            <span style={{ fontSize: "0.92rem", fontWeight: 900, color: "var(--text-main)" }}>
              가격 기록을 모으고 있어요
            </span>
            <p style={{ fontSize: "0.8rem", color: "var(--text-sub)", lineHeight: "1.5", maxWidth: "340px", margin: "0 auto", fontWeight: 600 }}>
              동일 상품의 핫딜 기록이 2건 이상 쌓이면 가격 변동 추이를 한눈에 볼 수 있어요.
            </p>
          </div>
        </div>
      </div>
    );
  }

  // 3. 데이터가 정상 존재할 때 (핫구사 스타일 명확한 KPI + 평균 기준선 + 최고/최저 마커)
  const prices = chartData.map(d => d.price);
  const dates = chartData.map(d => d.date);

  const width = 500;
  const height = 210;
  const paddingX = 56;
  const paddingY = 32;

  const maxPrice = Math.max(...prices);
  const minPrice = Math.min(...prices);
  const avgPrice = Math.round(prices.reduce((acc, v) => acc + v, 0) / prices.length);
  const latestPrice = prices[prices.length - 1];
  const vsAvgPercent = avgPrice > 0 ? Math.round(((latestPrice - avgPrice) / avgPrice) * 100) : 0;

  const priceRange = maxPrice - minPrice || Math.max(1000, maxPrice * 0.1);
  const yMax = maxPrice + priceRange * 0.22;
  const yMin = Math.max(0, minPrice - priceRange * 0.22);
  const yRange = yMax - yMin || 1000;

  const points = prices.map((price, i) => {
    const x = paddingX + (i * (width - paddingX * 2)) / Math.max(1, prices.length - 1);
    const y = height - paddingY - ((price - yMin) * (height - paddingY * 2)) / yRange;
    return { x, y, price, date: dates[i] };
  });

  const avgY = height - paddingY - ((avgPrice - yMin) * (height - paddingY * 2)) / yRange;
  const minIdx = prices.lastIndexOf(minPrice);
  const maxIdx = prices.indexOf(maxPrice);

  const polylinePoints = points.map(p => `${p.x},${p.y}`).join(" ");

  const areaPath = `
    M ${points[0].x} ${height - paddingY} 
    L ${points[0].x} ${points[0].y}
    ${points.slice(1).map(p => `L ${p.x} ${p.y}`).join(" ")}
    L ${points[points.length - 1].x} ${height - paddingY}
    Z
  `;

  return (
    <div style={{ width: "100%", display: "flex", flexDirection: "column", gap: "14px" }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: "10px" }}>
        <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
          <span style={{ fontSize: "0.9rem", fontWeight: 800, color: "var(--accent)" }}>가격 변동 내역</span>
          {isRealData ? (
            <span style={{ fontSize: "0.72rem", color: "#10b981", background: "rgba(16, 185, 129, 0.1)", padding: "2px 8px", borderRadius: "10px", fontWeight: 800, border: "1px solid rgba(16, 185, 129, 0.2)", display: "flex", alignItems: "center", gap: "4px" }}>
              <span style={{ width: "5px", height: "5px", borderRadius: "50%", backgroundColor: "#10b981", display: "inline-block" }}></span>
              실거래 {chartData.length}건
            </span>
          ) : (
            <span style={{ fontSize: "0.72rem", color: "var(--accent)", background: "rgba(var(--accent-rgb), 0.1)", padding: "2px 8px", borderRadius: "10px", fontWeight: 800, border: "1px solid rgba(var(--accent-rgb), 0.2)", display: "flex", alignItems: "center", gap: "4px" }}>
              <span style={{ width: "5px", height: "5px", borderRadius: "50%", backgroundColor: "var(--accent)", display: "inline-block" }}></span>
              예상 추이
            </span>
          )}
        </div>
        <div style={{ display: "flex", background: "var(--card-bg)", borderRadius: "10px", padding: "3px", border: "1px solid var(--card-border)" }}>
          {PERIOD_OPTIONS.map((opt) => {
            const isActive = period === opt.key;
            return (
              <button
                key={opt.key}
                onClick={() => setPeriod(opt.key)}
                style={{
                  background: isActive ? "var(--accent)" : "transparent",
                  color: isActive ? "#fff" : "var(--text-sub)",
                  border: "none",
                  borderRadius: "8px",
                  padding: "4px 10px",
                  fontSize: "0.75rem",
                  fontWeight: 800,
                  cursor: "pointer",
                  transition: "all 0.2s"
                }}
              >
                {opt.label}
              </button>
            );
          })}
        </div>
      </div>

      {/* 핫구사(Hotgusa) 스타일 3대 요약 지표 바 (역대 최저가 / 평균 핫딜가 / 기간 최고가) */}
      <div style={{
        display: "grid",
        gridTemplateColumns: "repeat(3, 1fr)",
        gap: "8px",
        padding: "10px 12px",
        background: "var(--card-bg)",
        border: "1px solid var(--card-border)",
        borderRadius: "12px",
        textAlign: "center"
      }}>
        <div>
          <div style={{ fontSize: "0.72rem", color: "var(--text-sub)", fontWeight: 700 }}>역대 최저가</div>
          <div style={{ fontSize: "0.9rem", color: "#10b981", fontWeight: 900, marginTop: "2px" }}>{formatPriceNum(minPrice)}</div>
        </div>
        <div style={{ borderLeft: "1px solid var(--card-border)", borderRight: "1px solid var(--card-border)" }}>
          <div style={{ fontSize: "0.72rem", color: "var(--text-sub)", fontWeight: 700 }}>평균 핫딜가</div>
          <div style={{ fontSize: "0.9rem", color: "var(--text-main)", fontWeight: 800, marginTop: "2px" }}>{formatPriceNum(avgPrice)}</div>
        </div>
        <div>
          <div style={{ fontSize: "0.72rem", color: "var(--text-sub)", fontWeight: 700 }}>기간 최고가</div>
          <div style={{ fontSize: "0.9rem", color: "#f59e0b", fontWeight: 800, marginTop: "2px" }}>{formatPriceNum(maxPrice)}</div>
        </div>
      </div>

      {/* 가격 포지션 게이지 바 (최저가 ↔ 현재가 ↔ 최고가 한눈에 보기) */}
      {maxPrice > minPrice && (() => {
        const posPct = Math.min(100, Math.max(0, Math.round(((latestPrice - minPrice) / (maxPrice - minPrice)) * 100)));
        const avgPct = Math.min(100, Math.max(0, Math.round(((avgPrice - minPrice) / (maxPrice - minPrice)) * 100)));
        const pinColor = posPct <= 25 ? "#10b981" : posPct <= 60 ? "#3b82f6" : "#f59e0b";
        return (
          <div style={{
            display: "flex",
            flexDirection: "column",
            gap: "6px",
            padding: "10px 12px",
            background: "var(--card-bg)",
            border: "1px solid var(--card-border)",
            borderRadius: "12px"
          }}>
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", fontSize: "0.72rem", fontWeight: 700 }}>
              <span style={{ color: "#10b981" }}>최저가 구간</span>
              <span style={{ color: pinColor, fontWeight: 800 }}>
                {posPct === 0 ? "현재 역대 최저가 (0%)" : `현재 가격 위치 상위 ${100 - posPct}% (하위 ${posPct}%)`}
              </span>
              <span style={{ color: "#f59e0b" }}>최고가 구간</span>
            </div>
            <div style={{ position: "relative", height: "8px", borderRadius: "999px", background: "linear-gradient(90deg, rgba(16,185,129,0.85) 0%, rgba(59,130,246,0.85) 50%, rgba(245,158,11,0.85) 100%)", margin: "4px 0" }}>
              {/* 평균 위치 눈금 */}
              <div
                title={`평균가 ${formatPriceNum(avgPrice)}`}
                style={{
                  position: "absolute",
                  left: `${avgPct}%`,
                  top: "-2px",
                  bottom: "-2px",
                  width: "2px",
                  background: "var(--card-bg)",
                  transform: "translateX(-50%)"
                }}
              />
              {/* 현재가 마커 핀 */}
              <div
                style={{
                  position: "absolute",
                  left: `${posPct}%`,
                  top: "50%",
                  width: "14px",
                  height: "14px",
                  borderRadius: "50%",
                  background: pinColor,
                  border: "2.5px solid var(--card-bg)",
                  boxShadow: `0 0 0 2px ${pinColor}`,
                  transform: "translate(-50%, -50%)",
                  transition: "left 0.3s ease"
                }}
              />
            </div>
          </div>
        );
      })()}

      {/* 현재가 포지션 한줄 요약 (이모지 대신 시맨틱 도트 + 텍스트) */}
      <div style={{ display: "flex", alignItems: "center", gap: "6px", fontSize: "0.78rem", fontWeight: 800, color: latestPrice <= minPrice || vsAvgPercent < 0 ? "#10b981" : "var(--accent)" }}>
        <span style={{
          width: "6px",
          height: "6px",
          borderRadius: "50%",
          backgroundColor: latestPrice <= minPrice || vsAvgPercent < 0 ? "#10b981" : "var(--accent)",
          display: "inline-block",
          flexShrink: 0
        }} />
        <span>
          {latestPrice <= minPrice && maxPrice > minPrice
            ? `최근 ${chartData.length}건 중 가장 낮은 가격이에요${vsAvgPercent < 0 ? ` · 평균보다 ${-vsAvgPercent}% 저렴` : ""}`
            : vsAvgPercent < 0
            ? `평균가보다 ${-vsAvgPercent}% 저렴해요`
            : vsAvgPercent > 0
            ? `평균가보다 ${vsAvgPercent}% 높아요`
            : `최근 ${chartData.length}건 가격이 일정하게 유지되고 있어요`}
        </span>
      </div>

      <div style={{ position: "relative", width: "100%", overflow: "visible" }}>
        <svg viewBox={`0 0 ${width} ${height}`} style={{ width: "100%", height: "auto", overflow: "visible" }}>
          <defs>
            <linearGradient id="chartGrad" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#3b82f6" stopOpacity="0.28" />
              <stop offset="100%" stopColor="#3b82f6" stopOpacity="0.0" />
            </linearGradient>
          </defs>

          {/* 평균가 수평 점선 기준선 */}
          <line x1={paddingX} y1={avgY} x2={width - paddingX} y2={avgY} stroke="rgba(148,163,184,0.45)" strokeWidth="1.2" strokeDasharray="4,4" />
          <text x={width - paddingX} y={Math.max(14, avgY - 5)} fill="var(--text-sub)" fontSize="8.5" fontWeight="700" textAnchor="end">
            평균 {formatPriceNum(avgPrice)}
          </text>

          <line x1={paddingX} y1={height - paddingY} x2={width - paddingX} y2={height - paddingY} stroke="var(--card-border)" />

          <path d={areaPath} fill="url(#chartGrad)" />

          <polyline fill="none" stroke="#3b82f6" strokeWidth="2.5" points={polylinePoints} strokeLinecap="round" strokeLinejoin="round" />

          {points.map((p, i) => {
            const isMin = i === minIdx;
            const isMax = i === maxIdx && maxPrice > minPrice;
            const dotColor = isMin ? "#10b981" : isMax ? "#f59e0b" : "#3b82f6";
            return (
              <g key={i}>
                <circle cx={p.x} cy={p.y} r={isMin || isMax ? "5" : "3.8"} fill={dotColor} stroke="var(--card-bg)" strokeWidth="1.5" />
              </g>
            );
          })}

          {/* 최저가 / 최고가 콜아웃 라벨 */}
          {minIdx >= 0 && (
            <text x={points[minIdx].x} y={Math.min(height - paddingY - 4, points[minIdx].y + 15)} fill="#10b981" fontSize="9.5" fontWeight="800" textAnchor="middle">
              최저 {formatPriceNum(minPrice)}
            </text>
          )}
          {maxIdx >= 0 && maxPrice > minPrice && maxIdx !== minIdx && (
            <text x={points[maxIdx].x} y={Math.max(14, points[maxIdx].y - 9)} fill="#f59e0b" fontSize="9.5" fontWeight="800" textAnchor="middle">
              최고 {formatPriceNum(maxPrice)}
            </text>
          )}

          <text x={paddingX - 8} y={paddingY + 4} fill="var(--text-sub)" fontSize="9" fontWeight="600" textAnchor="end">
            {formatPriceNum(maxPrice)}
          </text>
          <text x={paddingX - 8} y={height - paddingY + 4} fill="var(--text-sub)" fontSize="9" fontWeight="600" textAnchor="end">
            {formatPriceNum(minPrice)}
          </text>

          <text x={points[0].x} y={height - paddingY + 18} fill="var(--text-sub)" fontSize="9" fontWeight="600" textAnchor="middle">
            {points[0].date}
          </text>
          <text x={points[points.length - 1].x} y={height - paddingY + 18} fill="var(--text-sub)" fontSize="9" fontWeight="600" textAnchor="middle">
            {points[points.length - 1].date}
          </text>
        </svg>
      </div>
    </div>
  );
}
