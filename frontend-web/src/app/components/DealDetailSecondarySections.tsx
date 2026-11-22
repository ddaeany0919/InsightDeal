"use client";

import React, { useEffect, useState } from "react";
import {
  ThumbsUp,
  ThumbsDown,
  MessageSquare,
  Send,
  Bell,
  ChevronDown,
  ChevronUp,
  BarChart3,
  Trash2,
  RefreshCw,
} from "lucide-react";
import type { Deal, DealCommentItem, PriceHistoryItem } from "../lib/types";
import PriceTrendChart, { type PriceTrendStats } from "./PriceTrendChart";
import { formatRelativeTimeKo } from "../lib/utils";

export type AccordionSectionKey = "chart" | "vote" | "comments" | "target";

interface DealDetailSecondarySectionsProps {
  deal: Deal;
  activePrice: string | number | undefined;
  activeCurrency: string;
  activeOptionTitle: string;
  initialHistory?: PriceHistoryItem[];
  initialHistoryPeriod?: string;
  bodySlot?: React.ReactNode;
  expandedSections: Record<AccordionSectionKey, boolean>;
  onToggleSection: (key: AccordionSectionKey) => void;
  // 가격 추이
  historyCount: number;
  onHistoryStatus: (hasData: boolean, stats?: PriceTrendStats) => void;
  // 살까/말까 투표
  buyCount: number;
  saveCount: number;
  userVote: string | null;
  votesLoading: boolean;
  votesError: string | null;
  onRetryVotes: () => void;
  onVote: (voteType: "buy" | "save") => void;
  // 한줄 의견
  comments: DealCommentItem[];
  commentCount: number;
  commentsLoading: boolean;
  commentsError: string | null;
  onRetryComments: () => void;
  commentInput: string;
  onCommentInputChange: (val: string) => void;
  submittingComment: boolean;
  onAddComment: (e: React.FormEvent) => void;
  currentDeviceUuid: string;
  deletingCommentId: number | null;
  onDeleteComment: (commentId: number) => void;
  // 목표가 알림
  canShowTargetAlert: boolean;
  currSymbol: string;
  parsedTargetInput: number;
  diffAmount: number;
  savedTargetPrice: number | null;
  savingTargetAlert: boolean;
  presets: { label: string; value: number }[];
  formatUnit: (v: number) => string;
  onSelectPreset: (val: number) => void;
  onTargetInputChange: (val: string) => void;
  onSaveTargetAlert: (e: React.FormEvent) => void;
}

export default function DealDetailSecondarySections({
  deal,
  activePrice,
  activeCurrency,
  activeOptionTitle,
  initialHistory,
  initialHistoryPeriod,
  bodySlot,
  expandedSections,
  onToggleSection,
  historyCount,
  onHistoryStatus,
  buyCount,
  saveCount,
  userVote,
  votesLoading,
  votesError,
  onRetryVotes,
  onVote,
  comments,
  commentCount,
  commentsLoading,
  commentsError,
  onRetryComments,
  commentInput,
  onCommentInputChange,
  submittingComment,
  onAddComment,
  currentDeviceUuid,
  deletingCommentId,
  onDeleteComment,
  canShowTargetAlert,
  currSymbol,
  parsedTargetInput,
  diffAmount,
  savedTargetPrice,
  savingTargetAlert,
  presets,
  formatUnit,
  onSelectPreset,
  onTargetInputChange,
  onSaveTargetAlert,
}: DealDetailSecondarySectionsProps) {
  const [nowTs, setNowTs] = useState<number>(0);

  useEffect(() => {
    const initTimer = window.setTimeout(() => setNowTs(Date.now()), 0);
    const interval = window.setInterval(() => setNowTs(Date.now()), 60000);
    return () => {
      window.clearTimeout(initTimer);
      window.clearInterval(interval);
    };
  }, []);

  const displayCommentTotal = Math.max(commentCount, comments.length);

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "12px" }}>
      {/* 1. 가격 추이 아코디언 (펼침 시에만 PriceTrendChart 마운트 -> 1-RTT 보장) */}
      <div id={`deal-accordion-chart-${deal.id}`} className="deal-accordion">
        <button
          type="button"
          className="deal-accordion-trigger"
          aria-expanded={expandedSections.chart}
          aria-controls={`deal-sec-chart-${deal.id}`}
          onClick={() => onToggleSection("chart")}
        >
          <span style={{ display: "inline-flex", alignItems: "center", gap: "8px" }}>
            <BarChart3 size={16} color="var(--accent)" aria-hidden="true" />
            <span>가격 변동 추이</span>
          </span>
          <span style={{ display: "inline-flex", alignItems: "center", gap: "8px" }}>
            <span className="deal-accordion-pill">
              {historyCount >= 2
                ? `기록 ${historyCount}건`
                : historyCount === 1
                  ? "초기 1건 (추이 대기)"
                  : "첫 핫딜 수집"}
            </span>
            {expandedSections.chart ? (
              <ChevronUp size={16} aria-hidden="true" />
            ) : (
              <ChevronDown size={16} aria-hidden="true" />
            )}
          </span>
        </button>
        {expandedSections.chart && (
          <div id={`deal-sec-chart-${deal.id}`} className="deal-accordion-body" style={{ paddingTop: "14px" }}>
            <PriceTrendChart
              basePrice={activePrice}
              dealId={deal.id}
              currency={activeCurrency}
              optionName={activeOptionTitle}
              initialHistory={initialHistory}
              initialHistoryPeriod={initialHistoryPeriod}
              onHistoryStatus={onHistoryStatus}
            />
          </div>
        )}
      </div>

      {bodySlot}

      {/* 2. 살까 / 말까 투표 아코디언 */}
      <div id={`deal-accordion-vote-${deal.id}`} className="deal-accordion">
        <button
          type="button"
          className="deal-accordion-trigger"
          aria-expanded={expandedSections.vote}
          aria-controls={`deal-sec-vote-${deal.id}`}
          onClick={() => onToggleSection("vote")}
        >
          <span style={{ display: "inline-flex", alignItems: "center", gap: "8px" }}>
            <ThumbsUp size={16} color="var(--accent)" aria-hidden="true" />
            <span>얼만데 살까 · 말까 투표</span>
          </span>
          <span style={{ display: "inline-flex", alignItems: "center", gap: "8px" }}>
            <span className="deal-accordion-pill">
              살까 {buyCount} · 말까 {saveCount}
            </span>
            {expandedSections.vote ? (
              <ChevronUp size={16} aria-hidden="true" />
            ) : (
              <ChevronDown size={16} aria-hidden="true" />
            )}
          </span>
        </button>
        {expandedSections.vote && (
          <div
            id={`deal-sec-vote-${deal.id}`}
            className="deal-accordion-body"
            style={{ paddingTop: "14px", display: "flex", flexDirection: "column", gap: "10px" }}
          >
            {votesLoading && (
              <span style={{ fontSize: "0.78rem", color: "var(--text-sub)" }}>
                투표 현황을 불러오는 중...
              </span>
            )}
            {votesError && !votesLoading && (
              <div
                role="alert"
                style={{
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "space-between",
                  gap: "8px",
                  flexWrap: "wrap",
                  padding: "8px 12px",
                  borderRadius: "10px",
                  background: "var(--error-bg)",
                  border: "1px solid var(--error-border)",
                  fontSize: "0.78rem",
                  color: "var(--error-text)",
                  fontWeight: 700,
                }}
              >
                <span>{votesError}</span>
                <button
                  type="button"
                  onClick={onRetryVotes}
                  className="bounce-click"
                  style={{
                    minHeight: "44px",
                    padding: "8px 12px",
                    borderRadius: "8px",
                    border: "1px solid var(--error-border)",
                    background: "var(--card-bg)",
                    color: "var(--error-text)",
                    fontWeight: 700,
                    fontSize: "0.78rem",
                    display: "inline-flex",
                    alignItems: "center",
                    gap: "4px",
                    cursor: "pointer",
                  }}
                >
                  <RefreshCw size={13} aria-hidden="true" />
                  <span>다시 시도</span>
                </button>
              </div>
            )}
            <div style={{ display: "flex", gap: "10px", flexWrap: "wrap" }}>
              <button
                type="button"
                aria-pressed={userVote === "buy"}
                onClick={() => onVote("buy")}
                className="bounce-click"
                style={{
                  flex: 1,
                  minWidth: "120px",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  gap: "6px",
                  minHeight: "46px",
                  padding: "10px 14px",
                  borderRadius: "12px",
                  cursor: "pointer",
                  fontWeight: 800,
                  fontSize: "0.86rem",
                  background:
                    userVote === "buy" ? "var(--success-bg)" : "var(--card-bg)",
                  color: userVote === "buy" ? "var(--success-text)" : "var(--text-main)",
                  border:
                    userVote === "buy"
                      ? "1px solid var(--success-border)"
                      : "1px solid var(--card-border)",
                }}
              >
                <ThumbsUp size={15} aria-hidden="true" />
                <span>살까 ({buyCount})</span>
              </button>
              <button
                type="button"
                aria-pressed={userVote === "save"}
                onClick={() => onVote("save")}
                className="bounce-click"
                style={{
                  flex: 1,
                  minWidth: "120px",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  gap: "6px",
                  minHeight: "46px",
                  padding: "10px 14px",
                  borderRadius: "12px",
                  cursor: "pointer",
                  fontWeight: 800,
                  fontSize: "0.86rem",
                  background:
                    userVote === "save" ? "var(--error-bg)" : "var(--card-bg)",
                  color: userVote === "save" ? "var(--error-text)" : "var(--text-sub)",
                  border:
                    userVote === "save"
                      ? "1px solid var(--error-border)"
                      : "1px solid var(--card-border)",
                }}
              >
                <ThumbsDown size={15} aria-hidden="true" />
                <span>말까 ({saveCount})</span>
              </button>
            </div>
          </div>
        )}
      </div>

      {/* 3. 한줄 의견 아코디언 (조회/작성/본인 댓글 삭제) */}
      <div id={`deal-accordion-comments-${deal.id}`} className="deal-accordion">
        <button
          type="button"
          className="deal-accordion-trigger"
          aria-expanded={expandedSections.comments}
          aria-controls={`deal-sec-comments-${deal.id}`}
          onClick={() => onToggleSection("comments")}
        >
          <span style={{ display: "inline-flex", alignItems: "center", gap: "8px" }}>
            <MessageSquare size={16} color="var(--accent)" aria-hidden="true" />
            <span>얼만데 한줄 의견</span>
          </span>
          <span style={{ display: "inline-flex", alignItems: "center", gap: "8px" }}>
            <span className="deal-accordion-pill">의견 {displayCommentTotal}개</span>
            {expandedSections.comments ? (
              <ChevronUp size={16} aria-hidden="true" />
            ) : (
              <ChevronDown size={16} aria-hidden="true" />
            )}
          </span>
        </button>
        {expandedSections.comments && (
          <div
            id={`deal-sec-comments-${deal.id}`}
            className="deal-accordion-body"
            style={{ paddingTop: "14px", display: "flex", flexDirection: "column", gap: "12px" }}
          >
            <form onSubmit={onAddComment} style={{ display: "flex", gap: "8px" }}>
              <input
                type="text"
                value={commentInput}
                onChange={(e) => onCommentInputChange(e.target.value)}
                placeholder="이 상품에 대한 의견을 남겨보세요"
                aria-label="한줄 의견 입력"
                style={{
                  flex: 1,
                  minWidth: 0,
                  minHeight: "44px",
                  background: "var(--card-bg)",
                  border: "1px solid var(--card-border)",
                  borderRadius: "10px",
                  padding: "9px 12px",
                  color: "var(--text-main)",
                  fontSize: "0.85rem",
                }}
              />
              <button
                type="submit"
                disabled={submittingComment}
                className="btn-primary"
                style={{
                  minHeight: "44px",
                  padding: "8px 14px",
                  borderRadius: "10px",
                  fontSize: "0.82rem",
                  whiteSpace: "nowrap",
                }}
              >
                <Send size={14} aria-hidden="true" />
                <span>등록</span>
              </button>
            </form>

            {commentsLoading ? (
              <span style={{ fontSize: "0.8rem", color: "var(--text-sub)" }}>
                의견을 불러오는 중...
              </span>
            ) : commentsError && comments.length === 0 ? (
              <div
                role="alert"
                style={{
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "space-between",
                  gap: "8px",
                  flexWrap: "wrap",
                  padding: "10px 12px",
                  borderRadius: "10px",
                  background: "var(--error-bg)",
                  border: "1px solid var(--error-border)",
                  fontSize: "0.8rem",
                  color: "var(--error-text)",
                  fontWeight: 700,
                }}
              >
                <span>{commentsError}</span>
                <button
                  type="button"
                  onClick={onRetryComments}
                  className="bounce-click"
                  style={{
                    minHeight: "44px",
                    padding: "8px 12px",
                    borderRadius: "8px",
                    border: "1px solid var(--error-border)",
                    background: "var(--card-bg)",
                    color: "var(--error-text)",
                    fontWeight: 700,
                    fontSize: "0.78rem",
                    display: "inline-flex",
                    alignItems: "center",
                    gap: "4px",
                    cursor: "pointer",
                  }}
                >
                  <RefreshCw size={13} aria-hidden="true" />
                  <span>다시 시도</span>
                </button>
              </div>
            ) : comments.length === 0 ? (
              <span style={{ fontSize: "0.8rem", color: "var(--text-sub)" }}>
                아직 등록된 의견이 없어요. 첫 의견을 남겨보세요.
              </span>
            ) : (
              <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
                {commentsError && (
                  <div
                    role="alert"
                    style={{
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "space-between",
                      gap: "8px",
                      flexWrap: "wrap",
                      padding: "8px 12px",
                      borderRadius: "10px",
                      background: "var(--error-bg)",
                      border: "1px solid var(--error-border)",
                      fontSize: "0.78rem",
                      color: "var(--error-text)",
                      fontWeight: 700,
                    }}
                  >
                    <span>{commentsError}</span>
                    <button
                      type="button"
                      onClick={onRetryComments}
                      className="bounce-click"
                      style={{
                        minHeight: "44px",
                        padding: "8px 12px",
                        borderRadius: "8px",
                        border: "1px solid var(--error-border)",
                        background: "var(--card-bg)",
                        color: "var(--error-text)",
                        fontWeight: 700,
                        fontSize: "0.78rem",
                        display: "inline-flex",
                        alignItems: "center",
                        gap: "4px",
                        cursor: "pointer",
                      }}
                    >
                      <RefreshCw size={13} aria-hidden="true" />
                      <span>다시 시도</span>
                    </button>
                  </div>
                )}
                {comments.map((c) => {
                  const authorId = c.userId ?? c.user_id ?? "";
                  const isMine = Boolean(currentDeviceUuid && authorId === currentDeviceUuid);
                  const createdRaw = c.createdAt ?? c.created_at ?? null;
                  return (
                    <div
                      key={c.id}
                      style={{
                        fontSize: "0.83rem",
                        padding: "10px 12px",
                        borderRadius: "10px",
                        background: "var(--card-bg)",
                        border: "1px solid var(--card-border)",
                        display: "flex",
                        alignItems: "flex-start",
                        justifyContent: "space-between",
                        gap: "8px",
                      }}
                    >
                      <div style={{ flex: 1, minWidth: 0 }}>
                        <div style={{ display: "flex", alignItems: "center", gap: "6px", marginBottom: "2px" }}>
                          <span style={{ fontWeight: 700, color: "var(--accent)" }}>
                            {c.nickname}
                          </span>
                          {createdRaw && nowTs > 0 && (
                            <span style={{ fontSize: "0.72rem", color: "var(--text-muted)" }}>
                              {formatRelativeTimeKo(createdRaw, nowTs, "")}
                            </span>
                          )}
                        </div>
                        <span style={{ color: "var(--text-main)", wordBreak: "break-word" }}>
                          {c.content}
                        </span>
                      </div>
                      {isMine && (
                        <button
                          type="button"
                          aria-label="내 의견 삭제"
                          title="내 의견 삭제"
                          disabled={deletingCommentId === c.id}
                          onClick={() => onDeleteComment(c.id)}
                          style={{
                            minWidth: "44px",
                            minHeight: "44px",
                            borderRadius: "10px",
                            display: "inline-flex",
                            alignItems: "center",
                            justifyContent: "center",
                            color: "var(--text-sub)",
                            flexShrink: 0,
                          }}
                        >
                          <Trash2 size={15} aria-hidden="true" />
                        </button>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}
      </div>

      {/* 4. 목표가 도달 시 알림 설정 아코디언 (무료/소액 딜 제외) */}
      {canShowTargetAlert && (
        <div id={`deal-accordion-target-${deal.id}`} className="deal-accordion">
          <button
            type="button"
            className="deal-accordion-trigger"
            aria-expanded={expandedSections.target}
            aria-controls={`deal-sec-target-${deal.id}`}
            onClick={() => onToggleSection("target")}
          >
            <span style={{ display: "inline-flex", alignItems: "center", gap: "8px" }}>
              <Bell size={16} color="var(--accent)" aria-hidden="true" />
              <span>목표가 도달 시 알림</span>
            </span>
            <span style={{ display: "inline-flex", alignItems: "center", gap: "8px" }}>
              <span
                className="deal-accordion-pill"
                style={
                  savedTargetPrice
                    ? {
                        color: "var(--success-text)",
                        borderColor: "var(--success-border)",
                        background: "var(--success-bg)",
                      }
                    : undefined
                }
              >
                {savedTargetPrice ? `${formatUnit(savedTargetPrice)} 이하` : "미설정"}
              </span>
              {expandedSections.target ? (
                <ChevronUp size={16} aria-hidden="true" />
              ) : (
                <ChevronDown size={16} aria-hidden="true" />
              )}
            </span>
          </button>
          {expandedSections.target && (
            <div
              id={`deal-sec-target-${deal.id}`}
              className="deal-accordion-body"
              style={{ paddingTop: "14px", display: "flex", flexDirection: "column", gap: "12px" }}
            >
              <div style={{ display: "flex", gap: "6px", flexWrap: "wrap" }}>
                {presets.map((preset) => {
                  const isSelected = parsedTargetInput === preset.value;
                  return (
                    <button
                      key={preset.label}
                      type="button"
                      aria-pressed={isSelected}
                      onClick={() => onSelectPreset(preset.value)}
                      style={{
                        fontSize: "0.76rem",
                        fontWeight: 700,
                        minHeight: "44px",
                        padding: "8px 14px",
                        borderRadius: "999px",
                        cursor: "pointer",
                        background: isSelected ? "var(--accent)" : "var(--card-bg)",
                        color: isSelected ? "var(--on-accent)" : "var(--text-sub)",
                        border: isSelected
                          ? "1px solid var(--accent)"
                          : "1px solid var(--card-border)",
                      }}
                    >
                      {preset.label}
                    </button>
                  );
                })}
              </div>

              <form onSubmit={onSaveTargetAlert} style={{ display: "flex", gap: "8px", alignItems: "center" }}>
                <div style={{ position: "relative", flex: 1, minWidth: 0 }}>
                  <input
                    type="text"
                    inputMode="numeric"
                    value={parsedTargetInput > 0 ? parsedTargetInput.toLocaleString() : ""}
                    onChange={(e) => onTargetInputChange(e.target.value.replace(/[^0-9]/g, ""))}
                    placeholder="희망 목표 금액 입력"
                    aria-label="희망 목표 금액 입력"
                    style={{
                      width: "100%",
                      minHeight: "44px",
                      background: "var(--card-bg)",
                      border: "1px solid var(--card-border)",
                      borderRadius: "10px",
                      padding: "9px 32px 9px 12px",
                      color: "var(--text-main)",
                      fontSize: "0.88rem",
                      fontWeight: 800,
                    }}
                  />
                  <span
                    style={{
                      position: "absolute",
                      right: "12px",
                      top: "50%",
                      transform: "translateY(-50%)",
                      fontSize: "0.8rem",
                      color: "var(--text-sub)",
                      fontWeight: 700,
                    }}
                  >
                    {currSymbol}
                  </span>
                </div>
                <button
                  type="submit"
                  disabled={savingTargetAlert}
                  className="btn-primary"
                  style={{
                    minHeight: "44px",
                    padding: "9px 14px",
                    borderRadius: "10px",
                    fontSize: "0.82rem",
                    whiteSpace: "nowrap",
                  }}
                >
                  <span>{savedTargetPrice ? "목표가 변경" : "알림 받기"}</span>
                </button>
              </form>

              {parsedTargetInput > 0 && (
                <div
                  style={{
                    fontSize: "0.75rem",
                    color: diffAmount > 0 ? "var(--success-text)" : "var(--text-sub)",
                    fontWeight: 700,
                  }}
                >
                  {diffAmount > 0
                    ? `현재가보다 ${formatUnit(diffAmount)} 더 내려가면 로그인 없이 이 기기(브라우저)로 즉시 푸시 알림을 보내드려요.`
                    : "현재 판매가 이하로 금액을 설정하면 가격 하락 시 이 기기로 바로 알림을 받을 수 있어요."}
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
}
