"use client";

import React from "react";
import { Bell, Clock, Search, TrendingUp, X } from "lucide-react";
import type { PopularKeywordRanking } from "../lib/types";

export interface HeaderSearchPopoverProps {
  isOpen: boolean;
  popoverId: string;
  searchQuery: string;
  appliedSearch: string;
  isAutoSearchable: boolean;
  recentSearches: string[];
  popularKeywords: string[];
  popularRankings: PopularKeywordRanking[];
  popularUpdatedAt?: string;
  onSelectRecentKeyword: (kw: string) => void;
  onRemoveRecentSearch: (kw: string) => void;
  onClearRecentSearches: () => void;
  onSelectPopularKeyword: (kw: string) => void;
  onQuickSubscribeKeyword: (kw: string) => void;
  onClose: () => void;
}

export default function HeaderSearchPopover({
  isOpen,
  popoverId,
  searchQuery,
  appliedSearch,
  isAutoSearchable,
  recentSearches,
  popularKeywords,
  popularRankings,
  popularUpdatedAt,
  onSelectRecentKeyword,
  onRemoveRecentSearch,
  onClearRecentSearches,
  onSelectPopularKeyword,
  onQuickSubscribeKeyword,
  onClose,
}: HeaderSearchPopoverProps) {
  if (!isOpen) return null;

  const trimmedQuery = searchQuery.trim();
  const showQuickSubscribe = Boolean(trimmedQuery && isAutoSearchable);

  const items: PopularKeywordRanking[] =
    popularRankings && popularRankings.length > 0
      ? popularRankings.slice(0, 10)
      : popularKeywords.slice(0, 10).map((kw, idx) => ({
          rank: idx + 1,
          keyword: kw,
          deal_count: 0,
          recent_count: 0,
          status: idx === 0 ? "HOT" : idx < 3 ? "UP" : "SAME",
        }));

  if (!showQuickSubscribe && recentSearches.length === 0 && items.length === 0) {
    return null;
  }

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
      id={popoverId}
      role="region"
      aria-label="검색 추천 및 인기 검색어"
      className="header-search-popover glass-panel"
      onMouseDown={(e) => e.preventDefault()}
    >
      {showQuickSubscribe && (
        <div className="header-search-subscribe-banner">
          <span className="header-search-subscribe-text">
            <Bell size={14} color="var(--accent)" aria-hidden="true" />
            <span>
              <strong>&lsquo;{trimmedQuery}&rsquo;</strong> 새 핫딜 알림 받기
            </span>
          </span>
          <button
            type="button"
            className="header-search-subscribe-btn bounce-click"
            onClick={() => {
              onQuickSubscribeKeyword(searchQuery);
              onClose();
            }}
          >
            키워드 알림 받기
          </button>
        </div>
      )}

      {recentSearches.length > 0 && (
        <div className="header-search-section">
          <div className="header-search-section-header">
            <span className="header-search-section-title">
              <Clock size={13} color="var(--text-sub)" aria-hidden="true" />
              <span>최근 검색어</span>
            </span>
            <button
              type="button"
              onClick={onClearRecentSearches}
              className="header-search-clear-all-btn"
            >
              전체 삭제
            </button>
          </div>
          <div className="header-search-recent-list">
            {recentSearches.map((kw) => {
              const isSelected = appliedSearch === kw;
              return (
                <div
                  key={kw}
                  className={`header-search-recent-chip ${isSelected ? "is-selected" : ""}`}
                >
                  <button
                    type="button"
                    className="header-search-recent-trigger"
                    onClick={() => {
                      onSelectRecentKeyword(kw);
                      onClose();
                    }}
                  >
                    <Search size={12} aria-hidden="true" />
                    <span>{kw}</span>
                  </button>
                  <button
                    type="button"
                    className="header-search-recent-remove"
                    aria-label={`${kw} 최근 검색어 삭제`}
                    title={`${kw} 삭제`}
                    onClick={() => onRemoveRecentSearch(kw)}
                  >
                    <X size={12} aria-hidden="true" />
                  </button>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {items.length > 0 && (
        <div className="header-search-section">
          <div className="header-search-section-header">
            <span className="header-search-section-title">
              <TrendingUp size={14} color="var(--accent)" aria-hidden="true" />
              <span>실시간 인기 검색어 TOP 10</span>
            </span>
            {popularUpdatedAt && (
              <span className="trending-board-time">{popularUpdatedAt}</span>
            )}
          </div>
          <div className="trending-board-grid">
            {items.map((item) => {
              const isSelected = appliedSearch === item.keyword;
              return (
                <button
                  key={`${item.rank}-${item.keyword}`}
                  type="button"
                  className="trending-board-row"
                  onClick={() => {
                    onSelectPopularKeyword(item.keyword);
                    onClose();
                  }}
                >
                  <span className="trending-board-row-left">
                    <span
                      className={`trending-rank-num ${item.rank <= 3 ? "top-rank" : ""}`}
                    >
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
