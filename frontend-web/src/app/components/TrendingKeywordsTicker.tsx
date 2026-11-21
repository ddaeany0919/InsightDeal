"use client";

import { useEffect, useId, useRef, useState } from "react";
import { TrendingUp, ChevronDown, ChevronUp } from "lucide-react";
import type { PopularKeywordRanking } from "../lib/types";

interface TrendingKeywordsTickerProps {
  keywords: string[];
  rankings: PopularKeywordRanking[];
  updatedAt?: string;
  selectedKeyword: string;
  onSelectKeyword: (keyword: string) => void;
}

export default function TrendingKeywordsTicker({
  keywords,
  rankings,
  updatedAt,
  selectedKeyword,
  onSelectKeyword,
}: TrendingKeywordsTickerProps) {
  const [tickerIndex, setTickerIndex] = useState(0);
  const [isExpanded, setIsExpanded] = useState(false);
  const [isPaused, setIsPaused] = useState(false);
  const wrapperRef = useRef<HTMLDivElement>(null);
  const boardId = useId();

  const items: PopularKeywordRanking[] =
    rankings && rankings.length > 0
      ? rankings.slice(0, 10)
      : keywords.slice(0, 10).map((kw, idx) => ({
          rank: idx + 1,
          keyword: kw,
          deal_count: 0,
          recent_count: 0,
          status: idx === 0 ? "HOT" : idx < 3 ? "UP" : "SAME",
        }));

  useEffect(() => {
    if (items.length === 0 || isExpanded || isPaused) return;
    const timer = setInterval(() => {
      setTickerIndex((prev) => (prev + 1) % items.length);
    }, 3200);
    return () => clearInterval(timer);
  }, [items.length, isExpanded, isPaused]);

  // 팝오버 형태로 펼쳐졌을 때 외부 클릭 또는 Escape 키로 자연스럽게 닫기
  useEffect(() => {
    if (!isExpanded) return;
    const handlePointerDown = (e: MouseEvent) => {
      if (wrapperRef.current && !wrapperRef.current.contains(e.target as Node)) {
        setIsExpanded(false);
      }
    };
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        setIsExpanded(false);
      }
    };
    document.addEventListener("mousedown", handlePointerDown);
    window.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("mousedown", handlePointerDown);
      window.removeEventListener("keydown", handleKeyDown);
    };
  }, [isExpanded]);

  if (items.length === 0) return null;

  const currentItem = items[tickerIndex % items.length];

  const renderStatusBadge = (status: string) => {
    if (status === "HOT") {
      return (
        <span className="trending-status-tag hot" aria-label="인기 급상승">
          인기
        </span>
      );
    }
    if (status === "UP") {
      return (
        <span className="trending-status-tag up" aria-label="순위 상승">
          ▲
        </span>
      );
    }
    if (status === "NEW") {
      return (
        <span className="trending-status-tag new" aria-label="신규 진입">
          신규
        </span>
      );
    }
    return (
      <span className="trending-status-tag same" aria-label="순위 유지">
        -
      </span>
    );
  };

  return (
    <div
      ref={wrapperRef}
      className="trending-ticker-wrapper"
      onMouseEnter={() => setIsPaused(true)}
      onMouseLeave={() => setIsPaused(false)}
      onFocus={() => setIsPaused(true)}
      onBlur={(e) => {
        if (!wrapperRef.current?.contains(e.relatedTarget as Node | null)) {
          setIsPaused(false);
        }
      }}
    >
      <div className="trending-ticker-bar">
        <button
          type="button"
          className="trending-ticker-left"
          onClick={() => onSelectKeyword(currentItem.keyword)}
          title={`'${currentItem.keyword}' 검색하기`}
        >
          <span className="trending-badge-pill">
            <TrendingUp size={13} strokeWidth={2.5} aria-hidden="true" />
            <span>급상승</span>
          </span>
          <span className={`trending-rank-num ${currentItem.rank <= 3 ? "top-rank" : ""}`}>
            {currentItem.rank}
          </span>
          <span className="trending-keyword-text">{currentItem.keyword}</span>
          {currentItem.deal_count > 0 && (
            <span className="trending-deal-count">{currentItem.deal_count}건</span>
          )}
          {renderStatusBadge(currentItem.status)}
        </button>

        <button
          type="button"
          className="trending-expand-btn"
          aria-expanded={isExpanded}
          aria-controls={boardId}
          onClick={() => setIsExpanded((prev) => !prev)}
        >
          <span>{isExpanded ? "순위 접기" : "전체 순위"}</span>
          {isExpanded ? <ChevronUp size={15} aria-hidden="true" /> : <ChevronDown size={15} aria-hidden="true" />}
        </button>
      </div>

      {isExpanded && (
        <div id={boardId} className="trending-board-card">
          <div className="trending-board-header">
            <span className="trending-board-title">
              <TrendingUp size={15} color="var(--accent)" aria-hidden="true" />
              <span>실시간 인기 검색어 TOP 10</span>
            </span>
            {updatedAt && <span className="trending-board-time">{updatedAt}</span>}
          </div>
          <div className="trending-board-grid">
            {items.map((item) => {
              const isSelected = selectedKeyword === item.keyword;
              return (
                <button
                  key={item.rank}
                  type="button"
                  className="trending-board-row"
                  onClick={() => {
                    onSelectKeyword(item.keyword);
                    setIsExpanded(false);
                  }}
                >
                  <span className="trending-board-row-left">
                    <span className={`trending-rank-num ${item.rank <= 3 ? "top-rank" : ""}`}>
                      {item.rank}
                    </span>
                    <span
                      className={`trending-keyword-text ${isSelected ? "is-selected" : ""}`}
                    >
                      {item.keyword}
                    </span>
                  </span>
                  <span className="trending-board-row-right">
                    {item.deal_count > 0 && (
                      <span className="trending-deal-count">{item.deal_count}건</span>
                    )}
                    {renderStatusBadge(item.status)}
                  </span>
                </button>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
