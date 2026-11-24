"use client";

import React, { useId, useRef, useState } from "react";
import { Check, EyeOff, LayoutGrid, List, RotateCcw, SlidersHorizontal, X } from "lucide-react";
import { PLATFORMS, normalizePriceRange } from "../lib/utils";
import { useDialogA11y } from "../lib/useDialogA11y";

export const SHOP_FILTER_OPTIONS = [
  "쿠팡",
  "네이버",
  "지마켓",
  "옥션",
  "11번가",
  "SSG",
  "롯데온",
  "카카오쇼핑",
  "토스쇼핑",
  "오늘의집",
  "올리브영",
  "컬리",
  "알리익스프레스",
  "아마존",
  "CJ온스타일",
  "무신사",
  "스팀",
  "에픽게임즈",
] as const;

export interface AppliedFilterDraft {
  platforms: string[];
  shop: string;
  minPrice: string;
  maxPrice: string;
  freeShipping: boolean;
  hideClosed: boolean;
  includeClosed: boolean;
  viewMode: "card" | "compact";
}

export interface FilterDrawerModalProps {
  isOpen: boolean;
  drawerId: string;
  onClose: () => void;
  selectedPlatforms: string[];
  shopFilter: string;
  minPriceInput: string;
  maxPriceInput: string;
  freeShippingOnly: boolean;
  hideClosedDeals: boolean;
  includeClosedDeals: boolean;
  viewMode: "card" | "compact";
  hiddenDealIds: number[];
  mutedKeywords: string[];
  onApplyFilters: (next: AppliedFilterDraft) => void;
  onResetAll: () => void;
  onAddMutedKeyword: (kw: string) => void;
  onRemoveMutedKeyword: (kw: string) => void;
  onUnhideDeal: (dealId: number) => void;
  onRestoreAllHiddenDeals: () => void;
}

interface FilterDrawerContentProps {
  titleId: string;
  onClose: () => void;
  initialPlatforms: string[];
  initialShop: string;
  initialMinPrice: string;
  initialMaxPrice: string;
  initialFreeShipping: boolean;
  initialHideClosed: boolean;
  initialIncludeClosed: boolean;
  initialViewMode: "card" | "compact";
  hiddenDealIds: number[];
  mutedKeywords: string[];
  onApplyFilters: (next: AppliedFilterDraft) => void;
  onResetAll: () => void;
  onAddMutedKeyword: (kw: string) => void;
  onRemoveMutedKeyword: (kw: string) => void;
  onUnhideDeal: (dealId: number) => void;
  onRestoreAllHiddenDeals: () => void;
}

function FilterDrawerContent({
  titleId,
  onClose,
  initialPlatforms,
  initialShop,
  initialMinPrice,
  initialMaxPrice,
  initialFreeShipping,
  initialHideClosed,
  initialIncludeClosed,
  initialViewMode,
  hiddenDealIds,
  mutedKeywords,
  onApplyFilters,
  onResetAll,
  onAddMutedKeyword,
  onRemoveMutedKeyword,
  onUnhideDeal,
  onRestoreAllHiddenDeals,
}: FilterDrawerContentProps) {
  const [draftPlatforms, setDraftPlatforms] = useState<string[]>(initialPlatforms);
  const [draftShop, setDraftShop] = useState<string>(initialShop);
  const [draftMinPrice, setDraftMinPrice] = useState<string>(initialMinPrice);
  const [draftMaxPrice, setDraftMaxPrice] = useState<string>(initialMaxPrice);
  const [draftFreeShipping, setDraftFreeShipping] = useState<boolean>(initialFreeShipping);
  const [draftHideClosed, setDraftHideClosed] = useState<boolean>(initialHideClosed);
  const [draftIncludeClosed, setDraftIncludeClosed] = useState<boolean>(initialIncludeClosed);
  const [draftViewMode, setDraftViewMode] = useState<"card" | "compact">(initialViewMode);
  const [muteInput, setMuteInput] = useState<string>("");

  const handleToggleDraftPlatform = (platform: string) => {
    if (platform === "전체") {
      setDraftPlatforms([]);
      return;
    }
    setDraftPlatforms((prev) => {
      if (prev.includes(platform)) {
        return prev.filter((p) => p !== platform);
      }
      const added = [...prev, platform];
      const individualPlatforms = PLATFORMS.filter((p) => p !== "전체");
      return individualPlatforms.length > 0 &&
        individualPlatforms.every((p) => added.includes(p))
        ? []
        : added;
    });
  };

  const handleToggleHideClosed = () => {
    setDraftHideClosed((prev) => {
      const next = !prev;
      if (next) setDraftIncludeClosed(false);
      return next;
    });
  };

  const handleToggleIncludeClosed = () => {
    setDraftIncludeClosed((prev) => {
      const next = !prev;
      if (next) setDraftHideClosed(false);
      return next;
    });
  };

  const handleApply = () => {
    const { minPrice, maxPrice } = normalizePriceRange(
      draftMinPrice,
      draftMaxPrice
    );
    setDraftMinPrice(minPrice);
    setDraftMaxPrice(maxPrice);
    onApplyFilters({
      platforms: draftPlatforms,
      shop: draftShop.trim(),
      minPrice,
      maxPrice,
      freeShipping: draftFreeShipping,
      hideClosed: draftHideClosed,
      includeClosed: draftIncludeClosed,
      viewMode: draftViewMode,
    });
    onClose();
  };

  const handleReset = () => {
    onResetAll();
    onClose();
  };

  return (
    <>
      <div className="filter-drawer-header">
        <div className="filter-drawer-grabber" aria-hidden="true" />
        <div className="filter-drawer-title-row">
          <h2 id={titleId} className="filter-drawer-title">
            <SlidersHorizontal size={17} color="var(--accent)" aria-hidden="true" />
            <span>상세 필터</span>
          </h2>
          <button
            type="button"
            className="deal-modal-icon-btn deal-modal-close-icon-btn bounce-click"
            aria-label="상세 필터 닫기"
            onClick={onClose}
          >
            <X size={18} strokeWidth={2.4} aria-hidden="true" />
          </button>
        </div>
      </div>

      <div className="filter-drawer-body">
        {/* 1. 출처 (커뮤니티 11종) */}
        <section className="filter-drawer-section" aria-label="출처 커뮤니티 선택">
          <h3 className="filter-drawer-section-title">출처 (커뮤니티)</h3>
          <div className="filter-drawer-chips" role="group" aria-label="커뮤니티 필터">
            {PLATFORMS.map((platform) => {
              const isSelected =
                platform === "전체"
                  ? draftPlatforms.length === 0
                  : draftPlatforms.includes(platform);
              return (
                <button
                  key={platform}
                  type="button"
                  aria-pressed={isSelected}
                  onClick={() => handleToggleDraftPlatform(platform)}
                  className={`platform-chip bounce-click ${isSelected ? "active" : ""}`}
                >
                  {isSelected && <Check size={13} strokeWidth={2.5} aria-hidden="true" />}
                  <span>{platform === "전체" ? "전체 커뮤니티" : platform}</span>
                </button>
              );
            })}
          </div>
        </section>

        {/* 2. 쇼핑몰 18종 */}
        <section className="filter-drawer-section">
          <label htmlFor="drawer-shop-select" className="filter-drawer-section-title">
            쇼핑몰
          </label>
          <select
            id="drawer-shop-select"
            aria-label="쇼핑몰 필터"
            value={draftShop}
            onChange={(e) => setDraftShop(e.target.value)}
            className="filter-drawer-select"
          >
            <option value="">전체 쇼핑몰</option>
            {SHOP_FILTER_OPTIONS.map((m) => (
              <option key={m} value={m}>
                {m}
              </option>
            ))}
          </select>
        </section>

        {/* 3. 가격대 & 빠른 프리셋 */}
        <section className="filter-drawer-section" aria-label="가격대 설정">
          <h3 className="filter-drawer-section-title">가격대</h3>
          <div className="filter-drawer-price-inputs">
            <input
              type="number"
              min={0}
              step={1000}
              aria-label="최소 가격"
              placeholder="최소 원"
              value={draftMinPrice}
              onChange={(e) => setDraftMinPrice(e.target.value)}
              className="filter-drawer-price-input"
            />
            <span className="filter-drawer-price-tilde" aria-hidden="true">
              ~
            </span>
            <input
              type="number"
              min={0}
              step={1000}
              aria-label="최대 가격"
              placeholder="최대 원"
              value={draftMaxPrice}
              onChange={(e) => setDraftMaxPrice(e.target.value)}
              className="filter-drawer-price-input"
            />
          </div>
          <div className="filter-drawer-chips" style={{ marginTop: "8px" }}>
            <button
              type="button"
              className={`filter-chip-btn bounce-click filter-drawer-toggle-btn ${
                !draftMinPrice && draftMaxPrice === "10000" ? "is-active" : ""
              }`}
              aria-pressed={!draftMinPrice && draftMaxPrice === "10000"}
              onClick={() => {
                if (!draftMinPrice && draftMaxPrice === "10000") {
                  setDraftMaxPrice("");
                } else {
                  setDraftMinPrice("");
                  setDraftMaxPrice("10000");
                }
              }}
            >
              1만원 이하
            </button>
            <button
              type="button"
              className={`filter-chip-btn bounce-click filter-drawer-toggle-btn ${
                !draftMinPrice && draftMaxPrice === "30000" ? "is-active" : ""
              }`}
              aria-pressed={!draftMinPrice && draftMaxPrice === "30000"}
              onClick={() => {
                if (!draftMinPrice && draftMaxPrice === "30000") {
                  setDraftMaxPrice("");
                } else {
                  setDraftMinPrice("");
                  setDraftMaxPrice("30000");
                }
              }}
            >
              3만원 이하
            </button>
          </div>
        </section>

        {/* 4. 배송 · 마감 조건 */}
        <section className="filter-drawer-section" aria-label="배송 및 마감 조건">
          <h3 className="filter-drawer-section-title">배송 · 마감 조건</h3>
          <div className="filter-drawer-chips">
            <button
              type="button"
              aria-pressed={draftFreeShipping}
              onClick={() => setDraftFreeShipping((v) => !v)}
              className={`filter-chip-btn bounce-click filter-drawer-toggle-btn ${
                draftFreeShipping ? "is-active" : ""
              }`}
            >
              무료배송만
            </button>
            <button
              type="button"
              aria-pressed={draftHideClosed}
              onClick={handleToggleHideClosed}
              className={`filter-chip-btn bounce-click filter-drawer-toggle-btn ${
                draftHideClosed ? "is-active" : ""
              }`}
            >
              품절 제외
            </button>
            <button
              type="button"
              aria-pressed={draftIncludeClosed}
              onClick={handleToggleIncludeClosed}
              className={`filter-chip-btn bounce-click filter-drawer-toggle-btn ${
                draftIncludeClosed ? "is-active-secondary" : ""
              }`}
            >
              종료 포함
            </button>
          </div>
        </section>

        {/* 5. 보기 방식 */}
        <section className="filter-drawer-section" aria-label="보기 방식 설정">
          <h3 className="filter-drawer-section-title">보기 방식</h3>
          <div className="filter-drawer-view-group" role="group" aria-label="보기 방식">
            <button
              type="button"
              aria-pressed={draftViewMode === "card"}
              aria-label="카드 보기"
              onClick={() => setDraftViewMode("card")}
              className={`filter-drawer-view-btn ${draftViewMode === "card" ? "is-active" : ""}`}
            >
              <LayoutGrid size={14} aria-hidden="true" />
              <span>카드 보기</span>
            </button>
            <button
              type="button"
              aria-pressed={draftViewMode === "compact"}
              aria-label="한줄 컴팩트 보기"
              onClick={() => setDraftViewMode("compact")}
              className={`filter-drawer-view-btn ${draftViewMode === "compact" ? "is-active" : ""}`}
            >
              <List size={14} aria-hidden="true" />
              <span>한줄 컴팩트 보기</span>
            </button>
          </div>
        </section>

        {/* 6. 숨김 · 뮤트 관리 */}
        <section className="filter-drawer-section" aria-label="숨김 및 뮤트 관리">
          <h3 className="filter-drawer-section-title">
            <EyeOff size={14} aria-hidden="true" />
            <span>숨김·뮤트 ({hiddenDealIds.length + mutedKeywords.length})</span>
          </h3>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              if (!muteInput.trim()) return;
              onAddMutedKeyword(muteInput);
              setMuteInput("");
            }}
            className="filter-drawer-mute-form"
          >
            <input
              type="text"
              value={muteInput}
              onChange={(e) => setMuteInput(e.target.value)}
              placeholder="피드에서 가릴 키워드 추가 (예: 휴대폰 케이스)"
              aria-label="뮤트 키워드 입력"
              className="filter-drawer-mute-input"
            />
            <button type="submit" className="btn-primary filter-drawer-mute-submit">
              뮤트 추가
            </button>
          </form>

          {mutedKeywords.length > 0 && (
            <div className="filter-drawer-manage-list">
              <span className="filter-drawer-manage-label">뮤트 키워드:</span>
              {mutedKeywords.map((kw) => (
                <button
                  key={kw}
                  type="button"
                  onClick={() => onRemoveMutedKeyword(kw)}
                  className="applied-filter-chip"
                >
                  <span>{kw}</span>
                  <X size={12} aria-hidden="true" />
                </button>
              ))}
            </div>
          )}

          {hiddenDealIds.length > 0 && (
            <div className="filter-drawer-manage-list">
              <span className="filter-drawer-manage-label">
                숨긴 핫딜 {hiddenDealIds.length}건
              </span>
              {hiddenDealIds.slice(0, 12).map((id) => (
                <button
                  key={id}
                  type="button"
                  onClick={() => onUnhideDeal(id)}
                  className="applied-filter-chip"
                >
                  <span>#{id} 복구</span>
                  <X size={12} aria-hidden="true" />
                </button>
              ))}
              <button
                type="button"
                onClick={onRestoreAllHiddenDeals}
                className="applied-filter-chip"
              >
                전체 복구
              </button>
            </div>
          )}
        </section>
      </div>

      <div className="filter-drawer-footer">
        <button
          type="button"
          onClick={handleReset}
          className="filter-drawer-reset-btn bounce-click"
        >
          <RotateCcw size={14} aria-hidden="true" />
          <span>초기화</span>
        </button>
        <button
          type="button"
          onClick={handleApply}
          className="btn-primary filter-drawer-apply-btn bounce-click"
        >
          <span>적용하기</span>
        </button>
      </div>
    </>
  );
}

export default function FilterDrawerModal({
  isOpen,
  drawerId,
  onClose,
  selectedPlatforms,
  shopFilter,
  minPriceInput,
  maxPriceInput,
  freeShippingOnly,
  hideClosedDeals,
  includeClosedDeals,
  viewMode,
  hiddenDealIds,
  mutedKeywords,
  onApplyFilters,
  onResetAll,
  onAddMutedKeyword,
  onRemoveMutedKeyword,
  onUnhideDeal,
  onRestoreAllHiddenDeals,
}: FilterDrawerModalProps) {
  const panelRef = useRef<HTMLDivElement>(null);
  const titleId = useId();

  useDialogA11y({ isOpen, onClose, containerRef: panelRef });

  if (!isOpen) return null;

  return (
    <div
      className="filter-drawer-overlay"
      onClick={(e) => {
        if (e.target === e.currentTarget) {
          onClose();
        }
      }}
    >
      <div
        id={drawerId}
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className="filter-drawer-panel glass-panel"
      >
        <FilterDrawerContent
          titleId={titleId}
          onClose={onClose}
          initialPlatforms={selectedPlatforms}
          initialShop={shopFilter}
          initialMinPrice={minPriceInput}
          initialMaxPrice={maxPriceInput}
          initialFreeShipping={freeShippingOnly}
          initialHideClosed={hideClosedDeals}
          initialIncludeClosed={includeClosedDeals}
          initialViewMode={viewMode}
          hiddenDealIds={hiddenDealIds}
          mutedKeywords={mutedKeywords}
          onApplyFilters={onApplyFilters}
          onResetAll={onResetAll}
          onAddMutedKeyword={onAddMutedKeyword}
          onRemoveMutedKeyword={onRemoveMutedKeyword}
          onUnhideDeal={onUnhideDeal}
          onRestoreAllHiddenDeals={onRestoreAllHiddenDeals}
        />
      </div>
    </div>
  );
}
