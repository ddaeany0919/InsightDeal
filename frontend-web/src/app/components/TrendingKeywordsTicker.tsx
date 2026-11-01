"use client";

import { useEffect, useState } from "react";
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
    if (items.length === 0 || isExpanded) return;
    const timer = setInterval(() => {
      setTickerIndex((prev) => (prev + 1) % items.length);
    }, 3200);
    return () => clearInterval(timer);
  }, [items.length, isExpanded]);

  if (items.length === 0) return null;

  const currentItem = items[tickerIndex % items.length];

  const renderStatusBadge = (status: string) => {
    if (status === "HOT") {
      return <span className="trending-status-tag hot">HOT</span>;
    }
    if (status === "UP") {
      return <span className="trending-status-tag up">▲</span>;
    }
    if (status === "NEW") {
      return <span className="trending-status-tag new">NEW</span>;
    }
    return <span className="trending-status-tag same">-</span>;
  };

  return (
    <div className="trending-ticker-wrapper">
      <div className="trending-ticker-bar">
        <div
          className="trending-ticker-left"
          onClick={() => onSelectKeyword(currentItem.keyword)}
          title={`'${currentItem.keyword}' 검색하기`}
        >
          <span className="trending-badge-pill">
            <TrendingUp size={13} strokeWidth={2.5} />
            <span>급상승</span>
          </span>
          <span className={`trending-rank-num ${currentItem.rank <= 3 ? "top-rank" : ""}`}>
            {currentItem.rank}
          </span>
          <span className="trending-keyword-text">{currentItem.keyword}</span>
          {renderStatusBadge(currentItem.status)}
        </div>

        <button
          type="button"
          className="trending-expand-btn"
          onClick={() => setIsExpanded((prev) => !prev)}
        >
          <span>{isExpanded ? "순위 접기" : "전체 순위"}</span>
          {isExpanded ? <ChevronUp size={15} /> : <ChevronDown size={15} />}
        </button>
      </div>

      {isExpanded && (
        <div className="trending-board-card">
          <div className="trending-board-header">
            <span className="trending-board-title">
              <TrendingUp size={15} color="var(--accent)" />
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
                      className="trending-keyword-text"
                      style={isSelected ? { color: "var(--accent)", fontWeight: 800 } : undefined}
                    >
                      {item.keyword}
                    </span>
                  </span>
                  {renderStatusBadge(item.status)}
                </button>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
