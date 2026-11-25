"use client";

import React, { useState } from "react";
import { Bell, Check, Plus } from "lucide-react";

export interface KeywordPresetItem {
  keyword: string;
  label: string;
}

export const POPULAR_KEYWORD_PRESETS: readonly KeywordPresetItem[] = [
  { keyword: "생수", label: "생수·음료" },
  { keyword: "에어팟", label: "에어팟·애플" },
  { keyword: "치킨", label: "치킨·기프티콘" },
  { keyword: "SSD", label: "SSD·저장장치" },
  { keyword: "물티슈", label: "물티슈·생활" },
  { keyword: "커피", label: "커피·캡슐" },
];

export interface PopularKeywordPresetsProps {
  activeKeywords: string[];
  onToggleKeyword: (keyword: string, nextEnabled: boolean) => Promise<boolean> | void;
  compact?: boolean;
  disabled?: boolean;
}

function hasKeywordMatch(activeKeywords: string[], target: string): boolean {
  const norm = target.trim().toLowerCase();
  return activeKeywords.some((k) => k.trim().toLowerCase() === norm);
}

/**
 * 첫 방문 사용자가 타자 입력 없이 터치 한 번으로 인기 핫딜 키워드 알림을
 * 즉시 구독·해제할 수 있는 원탭 프리셋 칩 묶음입니다.
 */
export default function PopularKeywordPresets({
  activeKeywords,
  onToggleKeyword,
  compact = false,
  disabled = false,
}: PopularKeywordPresetsProps) {
  const [pendingKeyword, setPendingKeyword] = useState<string | null>(null);

  const handleChipClick = async (keyword: string, currentlyActive: boolean) => {
    if (disabled || pendingKeyword !== null) return;
    setPendingKeyword(keyword);
    try {
      await onToggleKeyword(keyword, !currentlyActive);
    } finally {
      setPendingKeyword(null);
    }
  };

  return (
    <div
      className={`keyword-preset-wrap${compact ? " is-compact" : ""}`}
      data-testid="popular-keyword-presets"
    >
      <div className="keyword-preset-header">
        <span className="keyword-preset-title">
          <Bell size={13} aria-hidden="true" />
          <span>{compact ? "터치 1번 인기 알림" : "지금 많이 받는 원탭 알림 TOP 6"}</span>
        </span>
        <span className="keyword-preset-hint">터치 1번 구독</span>
      </div>

      <div className="keyword-preset-chips">
        {POPULAR_KEYWORD_PRESETS.map((item) => {
          const isActive = hasKeywordMatch(activeKeywords, item.keyword);
          const isBusy = pendingKeyword === item.keyword;
          return (
            <button
              key={item.keyword}
              type="button"
              aria-pressed={isActive}
              disabled={disabled || isBusy}
              onClick={() => void handleChipClick(item.keyword, isActive)}
              className={`keyword-preset-chip bounce-click${isActive ? " is-active" : ""}${compact ? " is-compact" : ""}`}
              data-keyword={item.keyword}
            >
              {isActive ? (
                <Check size={13} aria-hidden="true" />
              ) : (
                <Plus size={13} aria-hidden="true" />
              )}
              <span>{isActive ? `${item.label} 알림ON` : item.label}</span>
            </button>
          );
        })}
      </div>
    </div>
  );
}
