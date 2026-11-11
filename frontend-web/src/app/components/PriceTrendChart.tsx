"use client";

import { useState, useEffect, useRef, useId, useMemo } from "react";
import { RefreshCw, BarChart3 } from "lucide-react";
import type { PriceHistoryItem } from "../lib/types";
import { apiUrl, formatPrice } from "../lib/utils";

export interface PriceTrendStats {
  minPrice: number;
  avgPrice: number;
  maxPrice: number;
  latestPrice: number;
}

interface PriceTrendChartProps {
  basePrice: string | number | null | undefined;
  dealId?: number;
  currency?: string;
  optionName?: string;
  onHistoryStatus?: (hasData: boolean, stats?: PriceTrendStats) => void;
}

const PERIOD_OPTIONS = [
  { key: "7d", label: "7일" },
  { key: "1m", label: "1개월" },
  { key: "3m", label: "3개월" },
  { key: "6m", label: "6개월" },
] as const;

type PeriodKey = (typeof PERIOD_OPTIONS)[number]["key"];

interface ParsedHistoryDate {
  label: string;
  daySerial: number | null;
  isLongRangeFormat: boolean;
}

function pad2(n: number): string {
  return String(n).padStart(2, "0");
}

/**
 * iOS Safari / 토스 WebView(WebKit)에서 "YYYY-MM-DD HH:mm:ss" 공백 구분자 문자열에
 * new Date() 호출 시 Invalid Date(NaN)가 반환되는 이슈를 원천 차단하는 순수 정규식 파서
 */
function parseSafeHistoryDate(raw: string | null | undefined): ParsedHistoryDate {
  const s = String(raw ?? "").trim();
  if (!s) {
    return { label: "", daySerial: null, isLongRangeFormat: false };
  }

  // 1) 4자리 연도 타임스탬프 (예: "2026-11-11 12:30:00", "2026-11-11T12:30:00Z", "2026.11.11")
  const fullMatch = s.match(
    /^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})(?:[T\s]+(\d{1,2}):(\d{1,2})(?::(\d{1,2}))?)?/
  );
  if (fullMatch) {
    const yyyy = Number(fullMatch[1]);
    const mm = Number(fullMatch[2]);
    const dd = Number(fullMatch[3]);
    const hh = fullMatch[4] !== undefined ? Number(fullMatch[4]) : null;
    const mi = fullMatch[5] !== undefined ? Number(fullMatch[5]) : null;
    if (mm >= 1 && mm <= 12 && dd >= 1 && dd <= 31) {
      const daySerial = Math.floor(Date.UTC(yyyy, mm - 1, dd) / 86400000);
      const label =
        hh !== null && mi !== null && !(hh === 0 && mi === 0)
          ? `${pad2(mm)}.${pad2(dd)} ${pad2(hh)}:${pad2(mi)}`
          : `${String(yyyy).slice(-2)}.${pad2(mm)}.${pad2(dd)}`;
      return { label, daySerial, isLongRangeFormat: false };
    }
  }

  // 2) 백엔드 3m/6m 포맷 "YY.MM.DD" (예: "26.09.15")
  const shortYmdMatch = s.match(
    /^(\d{2})[-/.](\d{1,2})[-/.](\d{1,2})(?:\s+(\d{1,2}):(\d{1,2}))?$/
  );
  if (shortYmdMatch) {
    const yy = Number(shortYmdMatch[1]);
    const mm = Number(shortYmdMatch[2]);
    const dd = Number(shortYmdMatch[3]);
    if (mm >= 1 && mm <= 12 && dd >= 1 && dd <= 31) {
      const yyyy = 2000 + yy;
      const daySerial = Math.floor(Date.UTC(yyyy, mm - 1, dd) / 86400000);
      return {
        label: `${pad2(yy)}.${pad2(mm)}.${pad2(dd)}`,
        daySerial,
        isLongRangeFormat: true,
      };
    }
  }

  // 3) 백엔드 7d 포맷 "MM.DD HH:mm" / "MM.DD" / "MM/DD" (예: "11.11 12:30", "11.11")
  const mdMatch = s.match(
    /^(\d{1,2})[./-](\d{1,2})(?:\s+(\d{1,2}):(\d{1,2}))?$/
  );
  if (mdMatch) {
    const mm = Number(mdMatch[1]);
    const dd = Number(mdMatch[2]);
    const hh = mdMatch[3] !== undefined ? Number(mdMatch[3]) : null;
    const mi = mdMatch[4] !== undefined ? Number(mdMatch[4]) : null;
    if (mm >= 1 && mm <= 12 && dd >= 1 && dd <= 31) {
      const refYear = new Date().getUTCFullYear();
      const daySerial = Math.floor(Date.UTC(refYear, mm - 1, dd) / 86400000);
      const label =
        hh !== null && mi !== null && !(hh === 0 && mi === 0)
          ? `${pad2(mm)}.${pad2(dd)} ${pad2(hh)}:${pad2(mi)}`
          : `${pad2(mm)}.${pad2(dd)}`;
      return { label, daySerial, isLongRangeFormat: false };
    }
  }

  // 4) 상대 날짜 문자열 ("3일 전", "어제", "오늘" 등) 그대로 안전 반환
  return { label: s, daySerial: null, isLongRangeFormat: false };
}

function parseBasePriceToRawUnit(
  pVal: string | number | null | undefined,
  normCurrency: string
): number {
  if (pVal === null || pVal === undefined) return 0;
  if (typeof pVal === "number") {
    return Number.isFinite(pVal) && pVal > 0 ? Math.round(pVal) : 0;
  }
  const trimmed = String(pVal).trim();
  if (
    !trimmed ||
    trimmed === "0" ||
    trimmed === "0원" ||
    trimmed === "무료" ||
    trimmed === "가격 확인" ||
    trimmed === "가격 문의" ||
    trimmed === "정보 없음"
  ) {
    return 0;
  }
  const clean = trimmed.replace(/[^0-9.-]/g, "");
  if (!clean) return 0;
  const num = parseFloat(clean);
  if (!Number.isFinite(num) || num <= 0) return 0;

  const isSubunit =
    normCurrency === "USD" || normCurrency === "EUR" || normCurrency === "CNY";
  if (isSubunit && trimmed.includes(".")) {
    return Math.round(num * 100);
  }
  return Math.round(num);
}

// [react-patterns] React StrictMode 이중 마운트 및 탭 재전환 시 중복 네트워크 호출 방지용 인플라이트 캐시
const historyPromiseCache = new Map<string, { ts: number; promise: Promise<PriceHistoryItem[]> }>();

export default function PriceTrendChart({
  basePrice,
  dealId,
  currency = "KRW",
  optionName,
  onHistoryStatus,
}: PriceTrendChartProps) {
  const gradientId = useId().replace(/:/g, "");
  const svgRef = useRef<SVGSVGElement | null>(null);
  const normCurrency = (currency || "KRW").trim().toUpperCase();
  const cleanOpt = optionName ? optionName.trim() : "";

  // 딜 ID / 옵션 / 통화 변경 시 이전 옵션 상태가 남지 않도록 스코프 키 기반 상태 격리
  const scopeKey = `${dealId ?? "preview"}:${cleanOpt}:${normCurrency}`;

  const [periodByScope, setPeriodByScope] = useState<{
    scope: string;
    value: PeriodKey | null;
  }>({ scope: scopeKey, value: null });
  const [fallbackByScope, setFallbackByScope] = useState<{
    scope: string;
    fellBack: boolean;
  }>({ scope: scopeKey, fellBack: false });
  const [activePointByScope, setActivePointByScope] = useState<{
    scope: string;
    index: number | null;
  }>({ scope: scopeKey, index: null });
  const [fetchedState, setFetchedState] = useState<{
    requestKey: string;
    points: { price: number; date: string }[];
  } | null>(null);

  const userPeriod =
    periodByScope.scope === scopeKey ? periodByScope.value : null;
  const autoFellBackTo3m =
    fallbackByScope.scope === scopeKey ? fallbackByScope.fellBack : false;
  const activePointIdx =
    activePointByScope.scope === scopeKey ? activePointByScope.index : null;

  const period: PeriodKey = userPeriod ?? (autoFellBackTo3m ? "3m" : "7d");
  const setPeriod = (nextPeriod: PeriodKey) => {
    setPeriodByScope({ scope: scopeKey, value: nextPeriod });
    setActivePointByScope({ scope: scopeKey, index: null });
  };

  // [android-e2e-sequence-audit Gate 2] 최초 진입 시 7d_auto 단일 왕복(1-RTT) API를 호출하여 7d -> 3m 순차 Waterfall 차단
  const queryPeriod = userPeriod ?? "7d_auto";
  const requestKey = dealId ? `${scopeKey}:${queryPeriod}` : null;

  const previewBase = useMemo(
    () => (dealId ? 0 : parseBasePriceToRawUnit(basePrice, normCurrency)),
    [dealId, basePrice, normCurrency]
  );

  const previewPoints = useMemo(() => {
    if (dealId || previewBase <= 0) return [];
    const dummyPrices = [
      Math.max(1, Math.round(previewBase * 1.15)),
      Math.max(1, Math.round(previewBase * 1.08)),
      previewBase,
    ];
    const dummyDates = ["3일 전", "어제", "오늘"];
    return dummyPrices.map((p, idx) => ({ price: p, date: dummyDates[idx] }));
  }, [dealId, previewBase]);

  const onHistoryStatusRef = useRef(onHistoryStatus);
  useEffect(() => {
    onHistoryStatusRef.current = onHistoryStatus;
  }, [onHistoryStatus]);

  useEffect(() => {
    if (!dealId || !requestKey) {
      if (previewPoints.length >= 2) {
        const pVals = previewPoints.map((d) => d.price);
        onHistoryStatusRef.current?.(true, {
          minPrice: Math.min(...pVals),
          avgPrice: Math.round(pVals.reduce((a, b) => a + b, 0) / pVals.length),
          maxPrice: Math.max(...pVals),
          latestPrice: pVals[pVals.length - 1],
        });
      } else {
        onHistoryStatusRef.current?.(false);
      }
      return;
    }

    let isMounted = true;

    (async () => {
      try {
        const now = Date.now();
        let cached = historyPromiseCache.get(requestKey);
        if (!cached || now - cached.ts > 15000) {
          const optParam = cleanOpt
            ? `&option_name=${encodeURIComponent(cleanOpt)}`
            : "";
          const currParam = normCurrency
            ? `&currency=${encodeURIComponent(normCurrency)}`
            : "";
          const targetUrl = apiUrl(
            `/api/community/deals/${dealId}/history?period=${queryPeriod}${optParam}${currParam}`
          );
          const promise = fetch(targetUrl).then((res) => {
            if (!res.ok) throw new Error("Failed to fetch");
            return res.json() as Promise<PriceHistoryItem[]>;
          });
          cached = { ts: now, promise };
          historyPromiseCache.set(requestKey, cached);
        }

        const rawData: PriceHistoryItem[] = await cached.promise;
        if (!isMounted) return;

        // 0원 이하 또는 비정상(NaN/Infinity) 데이터 포인트 원천 필터링
        const validItems = Array.isArray(rawData)
          ? rawData.filter(
              (item) =>
                item &&
                typeof item.price === "number" &&
                Number.isFinite(item.price) &&
                item.price > 0
            )
          : [];

        if (validItems.length >= 2) {
          const parsedDates = validItems.map((item) =>
            parseSafeHistoryDate(item.recordedAt)
          );

          if (userPeriod === null) {
            const firstParsed = parsedDates[0];
            const lastParsed = parsedDates[parsedDates.length - 1];
            let fellBack = parsedDates.some((d) => d.isLongRangeFormat);
            if (
              !fellBack &&
              firstParsed.daySerial !== null &&
              lastParsed.daySerial !== null
            ) {
              let daySpan = lastParsed.daySerial - firstParsed.daySerial;
              if (daySpan < -180) {
                daySpan += 365;
              }
              if (Math.abs(daySpan) > 7) {
                fellBack = true;
              }
            }
            setFallbackByScope({ scope: scopeKey, fellBack });
          }

          const formatted = validItems.map((item, idx) => ({
            price: Math.round(item.price),
            date: parsedDates[idx].label || String(item.recordedAt || "").trim(),
          }));

          setFetchedState({ requestKey, points: formatted });
          const pVals = formatted.map((d) => d.price);
          onHistoryStatusRef.current?.(true, {
            minPrice: Math.min(...pVals),
            avgPrice: Math.round(
              pVals.reduce((a, b) => a + b, 0) / pVals.length
            ),
            maxPrice: Math.max(...pVals),
            latestPrice: pVals[pVals.length - 1],
          });
        } else {
          setFetchedState({ requestKey, points: [] });
          onHistoryStatusRef.current?.(false);
        }
      } catch {
        if (!isMounted) return;
        setFetchedState({ requestKey, points: [] });
        onHistoryStatusRef.current?.(false);
      }
    })();

    return () => {
      isMounted = false;
    };
  }, [dealId, requestKey, queryPeriod, cleanOpt, normCurrency, userPeriod, scopeKey, previewPoints]);

  const loading =
    requestKey !== null && fetchedState?.requestKey !== requestKey;
  const chartData = dealId
    ? fetchedState?.requestKey === requestKey
      ? fetchedState.points
      : []
    : previewPoints;
  const isRealData = Boolean(dealId && chartData.length >= 2);

  const formatPriceNum = (num: number) => {
    if (!Number.isFinite(num) || num <= 0) return "가격 확인";
    const rounded = Math.round(num);
    if (
      normCurrency === "USD" ||
      normCurrency === "EUR" ||
      normCurrency === "CNY" ||
      normCurrency === "JPY"
    ) {
      return formatPrice(rounded, normCurrency);
    }
    return `${rounded.toLocaleString()}원`;
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
                  type="button"
                  onClick={() => setPeriod(opt.key)}
                  style={{
                    background: isActive ? "var(--accent)" : "transparent",
                    color: isActive ? "#fff" : "var(--text-sub)",
                    border: "none",
                    borderRadius: "8px",
                    minHeight: "38px",
                    padding: "6px 11px",
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
            <BarChart3 size={18} />
          </div>
          <div style={{ display: "flex", flexDirection: "column", gap: "6px" }}>
            <span style={{ fontSize: "0.92rem", fontWeight: 900, color: "var(--text-main)" }}>
              {cleanOpt ? `"${cleanOpt}" 핫딜 기록을 모으고 있어요` : "가격 기록을 모으고 있어요"}
            </span>
            <p style={{ fontSize: "0.8rem", color: "var(--text-sub)", lineHeight: "1.5", maxWidth: "340px", margin: "0 auto", fontWeight: 600 }}>
              동일 상품의 핫딜 기록이 2건 이상 쌓이면 가격 변동 추이를 한눈에 볼 수 있어요.
            </p>
          </div>
        </div>
      </div>
    );
  }

  // 3. 데이터가 정상 존재할 때
  const prices = chartData.map((d) => d.price);
  const dates = chartData.map((d) => d.date);

  const width = 500;
  const height = 214;
  const paddingLeft = 68;
  const paddingRight = 22;
  const paddingY = 32;
  const plotWidth = width - paddingLeft - paddingRight;
  const plotHeight = height - paddingY * 2;

  const maxPrice = Math.max(...prices);
  const minPrice = Math.min(...prices);
  const avgPrice = Math.round(prices.reduce((acc, v) => acc + v, 0) / prices.length);
  const latestPrice = prices[prices.length - 1];
  const vsAvgPercent = avgPrice > 0 ? Math.round(((latestPrice - avgPrice) / avgPrice) * 100) : 0;

  // 소액 외화 센트 단위($0.99 = 99) 평행선에서도 바닥으로 찌그러지지 않고 NaN이 발생하지 않도록 비례 스케일 적용
  const priceRange =
    maxPrice > minPrice ? maxPrice - minPrice : Math.max(1, maxPrice * 0.12);
  const yMax = maxPrice + priceRange * 0.24;
  const yMin = Math.max(0, minPrice - priceRange * 0.24);
  const yRange = Math.max(1e-6, yMax - yMin);
  const xStepDenom = Math.max(1, prices.length - 1);

  const points = prices.map((price, i) => {
    const x =
      prices.length === 1
        ? paddingLeft + plotWidth / 2
        : paddingLeft + (i * plotWidth) / xStepDenom;
    const y = height - paddingY - ((price - yMin) * plotHeight) / yRange;
    return { x, y, price, date: dates[i] };
  });

  const avgY = height - paddingY - ((avgPrice - yMin) * plotHeight) / yRange;
  const minIdx = prices.lastIndexOf(minPrice);
  const maxIdx = prices.indexOf(maxPrice);

  const polylinePoints = points.map((p) => `${p.x},${p.y}`).join(" ");

  const areaPath = `
    M ${points[0].x} ${height - paddingY}
    L ${points[0].x} ${points[0].y}
    ${points.slice(1).map((p) => `L ${p.x} ${p.y}`).join(" ")}
    L ${points[points.length - 1].x} ${height - paddingY}
    Z
  `;

  const getCalloutAnchor = (x: number): "start" | "middle" | "end" => {
    if (x <= paddingLeft + 52) return "start";
    if (x >= width - paddingRight - 52) return "end";
    return "middle";
  };

  const selectNearestPoint = (clientX: number) => {
    const svgEl = svgRef.current;
    if (!svgEl || points.length === 0) return;
    const rect = svgEl.getBoundingClientRect();
    if (rect.width <= 0) return;
    const svgX = ((clientX - rect.left) / rect.width) * width;
    let nearestIdx = 0;
    let minDist = Infinity;
    for (let i = 0; i < points.length; i++) {
      const dist = Math.abs(points[i].x - svgX);
      if (dist < minDist) {
        minDist = dist;
        nearestIdx = i;
      }
    }
    setActivePointByScope({ scope: scopeKey, index: nearestIdx });
  };

  const activePoint =
    activePointIdx !== null &&
    activePointIdx >= 0 &&
    activePointIdx < points.length
      ? points[activePointIdx]
      : null;
  const activeDiffPct =
    activePoint && avgPrice > 0
      ? Math.round(((activePoint.price - avgPrice) / avgPrice) * 100)
      : 0;

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
                type="button"
                onClick={() => setPeriod(opt.key)}
                style={{
                  background: isActive ? "var(--accent)" : "transparent",
                  color: isActive ? "#fff" : "var(--text-sub)",
                  border: "none",
                  borderRadius: "8px",
                  minHeight: "38px",
                  padding: "6px 11px",
                  fontSize: "0.75rem",
                  fontWeight: 700,
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

      {/* 3대 요약 지표 바 (역대 최저가 / 평균 핫딜가 / 기간 최고가) */}
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
        <div style={{ minWidth: 0 }}>
          <div style={{ fontSize: "0.72rem", color: "var(--text-sub)", fontWeight: 700 }}>역대 최저가</div>
          <div style={{ fontSize: "0.88rem", color: "#10b981", fontWeight: 900, marginTop: "2px", fontVariantNumeric: "tabular-nums", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{formatPriceNum(minPrice)}</div>
        </div>
        <div style={{ minWidth: 0, borderLeft: "1px solid var(--card-border)", borderRight: "1px solid var(--card-border)", padding: "0 4px" }}>
          <div style={{ fontSize: "0.72rem", color: "var(--text-sub)", fontWeight: 700 }}>평균 핫딜가</div>
          <div style={{ fontSize: "0.88rem", color: "var(--text-main)", fontWeight: 800, marginTop: "2px", fontVariantNumeric: "tabular-nums", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{formatPriceNum(avgPrice)}</div>
        </div>
        <div style={{ minWidth: 0 }}>
          <div style={{ fontSize: "0.72rem", color: "var(--text-sub)", fontWeight: 700 }}>기간 최고가</div>
          <div style={{ fontSize: "0.88rem", color: "#f59e0b", fontWeight: 800, marginTop: "2px", fontVariantNumeric: "tabular-nums", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{formatPriceNum(maxPrice)}</div>
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
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", gap: "6px", fontSize: "0.72rem", fontWeight: 700 }}>
              <span style={{ color: "#10b981", flexShrink: 0 }}>최저가 구간</span>
              <span style={{ color: pinColor, fontWeight: 800, textAlign: "center" }}>
                {posPct === 0 ? "현재 역대 최저가 (0%)" : `하위 ${posPct}% 구간`}
              </span>
              <span style={{ color: "#f59e0b", flexShrink: 0 }}>최고가 구간</span>
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

      {/* 선택 포인트 인라인 툴팁 바 또는 현재가 포지션 요약 (360px 소형 화면 잘림 제로) */}
      {activePoint ? (
        <div
          style={{
            display: "flex",
            alignItems: "center",
            justifyContent: "space-between",
            gap: "8px",
            flexWrap: "wrap",
            padding: "8px 12px",
            borderRadius: "10px",
            background: "var(--card-bg)",
            border: "1px solid rgba(var(--accent-rgb), 0.35)",
            fontSize: "0.78rem",
            fontWeight: 700,
          }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: "8px", flexWrap: "wrap" }}>
            <span style={{ color: "var(--text-sub)", fontVariantNumeric: "tabular-nums" }}>
              {activePoint.date}
            </span>
            <span style={{ color: "var(--text-main)", fontWeight: 900, fontVariantNumeric: "tabular-nums" }}>
              {formatPriceNum(activePoint.price)}
            </span>
            {activeDiffPct !== 0 && (
              <span
                style={{
                  color: activeDiffPct < 0 ? "#10b981" : "#f59e0b",
                  fontWeight: 800,
                  fontSize: "0.74rem",
                }}
              >
                {activeDiffPct < 0
                  ? `평균보다 ${-activeDiffPct}% 저렴`
                  : `평균보다 +${activeDiffPct}%`}
              </span>
            )}
          </div>
          <button
            type="button"
            onClick={() => setActivePointByScope({ scope: scopeKey, index: null })}
            style={{
              background: "transparent",
              border: "none",
              color: "var(--text-sub)",
              fontSize: "0.72rem",
              fontWeight: 700,
              cursor: "pointer",
              padding: "2px 4px",
            }}
          >
            닫기
          </button>
        </div>
      ) : (
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
      )}

      <div style={{ position: "relative", width: "100%", overflow: "hidden" }}>
        <svg
          ref={svgRef}
          viewBox={`0 0 ${width} ${height}`}
          style={{ width: "100%", height: "auto", display: "block", touchAction: "pan-y", userSelect: "none" }}
          onMouseMove={(e) => selectNearestPoint(e.clientX)}
          onMouseLeave={() => setActivePointByScope({ scope: scopeKey, index: null })}
          onTouchStart={(e) => {
            if (e.touches.length > 0) {
              selectNearestPoint(e.touches[0].clientX);
            }
          }}
          onTouchMove={(e) => {
            if (e.touches.length > 0) {
              selectNearestPoint(e.touches[0].clientX);
            }
          }}
        >
          <defs>
            <linearGradient id={gradientId} x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#3b82f6" stopOpacity="0.28" />
              <stop offset="100%" stopColor="#3b82f6" stopOpacity="0.0" />
            </linearGradient>
          </defs>

          {/* 평균가 수평 점선 기준선 */}
          <line x1={paddingLeft} y1={avgY} x2={width - paddingRight} y2={avgY} stroke="rgba(148,163,184,0.45)" strokeWidth="1.2" strokeDasharray="4,4" />
          {maxPrice > minPrice && (
            <text x={width - paddingRight} y={Math.max(14, avgY - 5)} fill="var(--text-sub)" fontSize="8.5" fontWeight="700" textAnchor="end">
              평균 {formatPriceNum(avgPrice)}
            </text>
          )}

          <line x1={paddingLeft} y1={height - paddingY} x2={width - paddingRight} y2={height - paddingY} stroke="var(--card-border)" />

          <path d={areaPath} fill={`url(#${gradientId})`} />

          <polyline fill="none" stroke="#3b82f6" strokeWidth="2.5" points={polylinePoints} strokeLinecap="round" strokeLinejoin="round" />

          {/* 터치/호버 활성 수직 가이드선 */}
          {activePoint && (
            <line
              x1={activePoint.x}
              y1={paddingY - 6}
              x2={activePoint.x}
              y2={height - paddingY}
              stroke="var(--accent)"
              strokeWidth="1.2"
              strokeDasharray="3,3"
              opacity="0.7"
            />
          )}

          {points.map((p, i) => {
            const isMin = i === minIdx;
            const isMax = i === maxIdx && maxPrice > minPrice;
            const isActive = i === activePointIdx;
            const dotColor = isMin ? "#10b981" : isMax ? "#f59e0b" : "#3b82f6";
            return (
              <g key={i}>
                {isActive && (
                  <circle
                    cx={p.x}
                    cy={p.y}
                    r="8"
                    fill={dotColor}
                    opacity="0.2"
                  />
                )}
                <circle
                  cx={p.x}
                  cy={p.y}
                  r={isActive ? "5.8" : isMin || isMax ? "5" : "3.8"}
                  fill={dotColor}
                  stroke="var(--card-bg)"
                  strokeWidth="1.5"
                />
              </g>
            );
          })}

          {/* 최저가 / 최고가 콜아웃 라벨 (좌우 경계 클리핑 방지 동적 textAnchor) */}
          {minIdx >= 0 && maxPrice > minPrice && (
            <text
              x={points[minIdx].x}
              y={Math.min(height - paddingY - 4, points[minIdx].y + 15)}
              fill="#10b981"
              fontSize="9.5"
              fontWeight="800"
              textAnchor={getCalloutAnchor(points[minIdx].x)}
            >
              최저 {formatPriceNum(minPrice)}
            </text>
          )}
          {maxIdx >= 0 && maxPrice > minPrice && maxIdx !== minIdx && (
            <text
              x={points[maxIdx].x}
              y={Math.max(14, points[maxIdx].y - 9)}
              fill="#f59e0b"
              fontSize="9.5"
              fontWeight="800"
              textAnchor={getCalloutAnchor(points[maxIdx].x)}
            >
              최고 {formatPriceNum(maxPrice)}
            </text>
          )}

          {/* 좌측 Y축 눈금 (평행선일 때는 중앙 기준값 1개만 표시) */}
          {maxPrice > minPrice ? (
            <>
              <text x={paddingLeft - 8} y={paddingY + 4} fill="var(--text-sub)" fontSize="9" fontWeight="600" textAnchor="end">
                {formatPriceNum(maxPrice)}
              </text>
              <text x={paddingLeft - 8} y={height - paddingY + 4} fill="var(--text-sub)" fontSize="9" fontWeight="600" textAnchor="end">
                {formatPriceNum(minPrice)}
              </text>
            </>
          ) : (
            <text x={paddingLeft - 8} y={avgY + 3} fill="var(--text-sub)" fontSize="9" fontWeight="700" textAnchor="end">
              {formatPriceNum(avgPrice)}
            </text>
          )}

          <text x={points[0].x} y={height - paddingY + 18} fill="var(--text-sub)" fontSize="9" fontWeight="600" textAnchor="start">
            {points[0].date}
          </text>
          {points.length > 1 && (
            <text x={points[points.length - 1].x} y={height - paddingY + 18} fill="var(--text-sub)" fontSize="9" fontWeight="600" textAnchor="end">
              {points[points.length - 1].date}
            </text>
          )}
        </svg>
      </div>
    </div>
  );
}
