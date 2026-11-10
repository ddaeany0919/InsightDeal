"use client";

import React, { useState, useEffect } from "react";
import { ThumbsUp, ThumbsDown, Share2, MessageSquare, Send, Bell, Copy, ExternalLink, Check, X } from "lucide-react";
import type { Deal, DealSource } from "../lib/types";
import {
  formatPrice,
  getProxyImageUrl,
  getFallbackProxyImageUrl,
  apiUrl,
  resolveOutboundUrl,
  polishDealBodyText,
  extractPromoCodes,
  getOrCreateWebDeviceUuid,
  ensureWebPushSubscribed,
  getOptionSources,
  extractOptionTitle,
  cleanOptionTitle,
} from "../lib/utils";
import PriceTrendChart, { type PriceTrendStats } from "./PriceTrendChart";

interface DealDetailModalProps {
  deal: Deal | null;
  onClose: () => void;
  showToast: (message: string) => void;
}

interface DealCommentItem {
  id: number;
  userId: string;
  nickname: string;
  content: string;
  createdAt: string;
}

export default function DealDetailModal({ deal, onClose, showToast }: DealDetailModalProps) {
  const [selectedOptionIndex, setSelectedOptionIndex] = useState<number>(0);
  const [hasChartData, setHasChartData] = useState(false);
  const [chartStats, setChartStats] = useState<PriceTrendStats | null>(null);
  const [targetPriceInput, setTargetPriceInput] = useState<string>("");
  const [savedTargetPrice, setSavedTargetPrice] = useState<number | null>(null);
  const [savingTargetAlert, setSavingTargetAlert] = useState<boolean>(false);
  const [detailHtml, setDetailHtml] = useState<string>("");
  const [detailSources, setDetailSources] = useState<DealSource[]>([]);
  const [buyCount, setBuyCount] = useState<number>(0);
  const [saveCount, setSaveCount] = useState<number>(0);
  const [userVote, setUserVote] = useState<string | null>(null);
  const [comments, setComments] = useState<DealCommentItem[]>([]);
  const [commentInput, setCommentInput] = useState<string>("");
  const [submittingComment, setSubmittingComment] = useState<boolean>(false);
  const [copiedCode, setCopiedCode] = useState<string | null>(null);

  const getBrowserUserId = () => getOrCreateWebDeviceUuid();

  useEffect(() => {
    setSelectedOptionIndex(0);
    setHasChartData(false);
    setChartStats(null);
    setDetailHtml(deal?.contentHtml || "");
    setDetailSources(deal?.sources || []);
    setBuyCount(0);
    setSaveCount(0);
    setUserVote(null);
    setComments([]);
    setCommentInput("");
    setCopiedCode(null);

    if (!deal) return;

    const isUsd = deal.currency === "USD";
    const rawPriceNum = parseInt(String(deal.price || "").replace(/[^0-9]/g, ""), 10) || 0;
    const defaultTarget = isUsd
      ? rawPriceNum >= 500
        ? Math.max(1, Math.floor((rawPriceNum / 100) * 0.95))
        : 0
      : rawPriceNum >= 1000
        ? Math.round((rawPriceNum * 0.95) / 100) * 100
        : 0;
    try {
      const mapStr = localStorage.getItem("insightdeal_target_prices");
      const targetMap = mapStr ? JSON.parse(mapStr) : {};
      if (targetMap && targetMap[deal.id]) {
        setSavedTargetPrice(Number(targetMap[deal.id]));
        setTargetPriceInput(String(targetMap[deal.id]));
      } else {
        setSavedTargetPrice(null);
        setTargetPriceInput(defaultTarget > 0 ? String(defaultTarget) : "");
      }
    } catch {
      setSavedTargetPrice(null);
      setTargetPriceInput(defaultTarget > 0 ? String(defaultTarget) : "");
    }

    const uid = getBrowserUserId();

    // 1. 단건 조회로 상세 본문(content_html) 및 공구/다중 옵션(sources) 전체 로드
    fetch(apiUrl(`/api/community/deals/${deal.id}`))
      .then((r) => (r.ok ? r.json() : null))
      .then((d) => {
        if (!d) return;
        if (d.content_html) {
          setDetailHtml(d.content_html);
        }
        if (Array.isArray(d.sources) && d.sources.length > 0) {
          setDetailSources(d.sources);
        }
      })
      .catch(() => {});

    // 2. 살까/말까 투표 현황 로드
    fetch(apiUrl(`/api/community/deals/${deal.id}/votes?user_id=${encodeURIComponent(uid)}`))
      .then((r) => (r.ok ? r.json() : null))
      .then((v) => {
        if (v) {
          setBuyCount(v.buyCount || 0);
          setSaveCount(v.saveCount || 0);
          setUserVote(v.userVote || null);
        }
      })
      .catch(() => {});

    // 3. 한줄평 댓글 로드
    fetch(apiUrl(`/api/community/deals/${deal.id}/comments`))
      .then((r) => (r.ok ? r.json() : []))
      .then((list) => {
        if (Array.isArray(list)) {
          setComments(list);
        }
      })
      .catch(() => {});
  }, [deal]);

  useEffect(() => {
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") onClose();
    };
    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [onClose]);

  const preOptionSources = deal ? getOptionSources(detailSources, deal.options_data) : [];
  const preSelectedOption =
    preOptionSources.length > 0 && selectedOptionIndex < preOptionSources.length
      ? preOptionSources[selectedOptionIndex]
      : null;
  const preActivePrice =
    preSelectedOption && preSelectedOption.price && preSelectedOption.price > 0
      ? preSelectedOption.price
      : deal?.price;
  const preActiveCurrency = preSelectedOption?.currency || deal?.currency || "KRW";

  useEffect(() => {
    if (!deal) return;
    const isUsd = preActiveCurrency === "USD";
    const rawPriceNum =
      typeof preActivePrice === "number"
        ? preActivePrice
        : parseInt(String(preActivePrice || "").replace(/[^0-9]/g, ""), 10) || 0;
    const defaultTarget = isUsd
      ? rawPriceNum >= 500
        ? Math.max(1, Math.floor((rawPriceNum / 100) * 0.95))
        : 0
      : rawPriceNum >= 1000
        ? Math.round((rawPriceNum * 0.95) / 100) * 100
        : 0;
    try {
      const mapStr = localStorage.getItem("insightdeal_target_prices");
      const targetMap = mapStr ? JSON.parse(mapStr) : {};
      const key = preOptionSources.length > 0 ? `${deal.id}_${selectedOptionIndex}` : String(deal.id);
      const savedVal = targetMap?.[key] ?? (selectedOptionIndex === 0 ? targetMap?.[deal.id] : undefined);
      if (savedVal) {
        setSavedTargetPrice(Number(savedVal));
        setTargetPriceInput(String(savedVal));
      } else {
        setSavedTargetPrice(null);
        setTargetPriceInput(defaultTarget > 0 ? String(defaultTarget) : "");
      }
    } catch {
      setSavedTargetPrice(null);
      setTargetPriceInput(defaultTarget > 0 ? String(defaultTarget) : "");
    }
  }, [deal?.id, selectedOptionIndex, preActivePrice, preActiveCurrency, preOptionSources.length]);

  if (!deal) return null;

  // HTML 태그 제거 + 공구 다중 상품 구분선 및 깨진 쿠폰 코드 줄바꿈 자동 다듬기
  const cleanBodyText = polishDealBodyText(detailHtml);
  const promoCodes = extractPromoCodes(cleanBodyText, deal.title);
  const optionSources = getOptionSources(detailSources, deal.options_data);

  const selectedOption =
    optionSources.length > 0 && selectedOptionIndex < optionSources.length
      ? optionSources[selectedOptionIndex]
      : null;

  const activeOptionTitle = selectedOption ? cleanOptionTitle(selectedOption.site_name) : "";
  const activePrice =
    selectedOption && selectedOption.price && selectedOption.price > 0
      ? selectedOption.price
      : deal.price;
  const activeCurrency = selectedOption?.currency || deal.currency || "KRW";
  const activeImageUrl =
    selectedOption?.image_url && selectedOption.image_url.trim() !== ""
      ? selectedOption.image_url
      : deal.image_url;

  const activeEcommerceUrl =
    selectedOption?.ecommerce_url &&
    selectedOption.ecommerce_url !== "#" &&
    selectedOption.ecommerce_url.trim() !== ""
      ? selectedOption.ecommerce_url
      : deal.ecommerce_url &&
        deal.ecommerce_url !== "#" &&
        String(deal.ecommerce_url).trim() !== ""
      ? deal.ecommerce_url
      : deal.post_link;

  const handleVote = async (voteType: "buy" | "save") => {
    try {
      const uid = getBrowserUserId();
      const res = await fetch(apiUrl(`/api/community/deals/${deal.id}/votes`), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ user_id: uid, vote_type: voteType }),
      });
      if (res.ok) {
        const data = await res.json();
        setBuyCount(data.buyCount || 0);
        setSaveCount(data.saveCount || 0);
        setUserVote(data.userVote || voteType);
        showToast(voteType === "buy" ? "'추천해요'에 투표했습니다." : "'아쉬워요'에 투표했습니다.");
      }
    } catch (e) {
      showToast("투표 처리 중 오류가 발생했습니다.");
    }
  };

  const handleAddComment = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!commentInput.trim() || submittingComment) return;
    setSubmittingComment(true);
    try {
      const uid = getBrowserUserId();
      const res = await fetch(apiUrl(`/api/community/deals/${deal.id}/comments`), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          user_id: uid,
          nickname: `회원_${uid.slice(-4)}`,
          content: commentInput.trim(),
        }),
      });
      if (res.ok) {
        const created = await res.json();
        setComments((prev) => [created, ...prev]);
        setCommentInput("");
        showToast("댓글이 등록되었습니다.");
      }
    } catch (e) {
      showToast("댓글 등록에 실패했습니다.");
    } finally {
      setSubmittingComment(false);
    }
  };

  const handleShare = async () => {
    const shareUrl =
      typeof window !== "undefined"
        ? `${window.location.origin}/?keyword=${encodeURIComponent(deal.title.slice(0, 20))}`
        : deal.post_link;
    const shareText = `${deal.title} (${formatPrice(deal.price, deal.currency)})`;

    if (typeof navigator !== "undefined" && navigator.share) {
      try {
        await navigator.share({ title: deal.title, text: shareText, url: shareUrl });
        return;
      } catch (e) {}
    }
    if (typeof navigator !== "undefined" && navigator.clipboard) {
      await navigator.clipboard.writeText(`${shareText}\n${shareUrl}`);
      showToast("상품 링크가 클립보드에 복사되었습니다.");
    }
  };

  return (
    <div
      className="deal-modal-overlay"
      onClick={onClose}
    >
      <div
        className="glass-panel deal-modal-panel"
        onClick={(e) => e.stopPropagation()}
      >
        {/* 헤더 (상품 썸네일 + 메타 배지 + 상품명 + 액션 버튼) */}
        <div className="deal-modal-header">
          <div className="deal-modal-header-main">
            {activeImageUrl && (
              <div
                key={activeImageUrl}
                className="deal-modal-thumb"
                style={{
                  border: selectedOption ? "1.5px solid var(--accent)" : "1px solid var(--card-border)",
                }}
              >
                <img
                  src={getProxyImageUrl(activeImageUrl, { width: 400 })}
                  alt={activeOptionTitle || deal.title}
                  style={{ width: "100%", height: "100%", objectFit: "cover" }}
                  referrerPolicy="no-referrer"
                  onError={(e) => {
                    const fallback = getFallbackProxyImageUrl(activeImageUrl);
                    if (fallback && e.currentTarget.src !== fallback) {
                      e.currentTarget.src = fallback;
                    }
                  }}
                />
                {selectedOption && (selectedOptionIndex === 0 || Boolean(selectedOption.image_url && selectedOption.image_url.trim() !== "")) && (
                  <span
                    style={{
                      position: "absolute",
                      bottom: "3px",
                      right: "3px",
                      background: "rgba(15, 23, 42, 0.75)",
                      color: "#fff",
                      fontSize: "0.62rem",
                      fontWeight: 800,
                      padding: "1px 5px",
                      borderRadius: "4px",
                      backdropFilter: "blur(4px)",
                    }}
                  >
                    #{selectedOptionIndex + 1}
                  </span>
                )}
              </div>
            )}
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ display: "flex", alignItems: "center", gap: "6px", marginBottom: "6px", flexWrap: "wrap" }}>
                <span className="deal-source">{deal.community_name}</span>
                {deal.tags &&
                  deal.tags.map((t, idx) => {
                    const cleanTag = String(t).replace(/^[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}\uFE0F]+\s*/gu, "").trim();
                    if (!cleanTag) return null;
                    return (
                      <span
                        key={idx}
                        style={{
                          fontSize: "0.73rem",
                          padding: "3px 9px",
                          borderRadius: "6px",
                          background: "var(--badge-bg)",
                          border: "1px solid var(--card-border)",
                          color: "var(--badge-text)",
                          fontWeight: 700,
                          letterSpacing: "-0.01em",
                        }}
                      >
                        {cleanTag}
                      </span>
                    );
                  })}
              </div>
              <h3 className="deal-modal-title">
                {deal.title}
              </h3>
            </div>
          </div>
          <div style={{ display: "flex", gap: "8px", flexShrink: 0 }}>
            <button
              type="button"
              onClick={handleShare}
              title="공유하기"
              aria-label="상품 공유하기"
              className="deal-modal-icon-btn bounce-click"
            >
              <Share2 size={15} />
            </button>
            <button
              type="button"
              onClick={onClose}
              aria-label="상세 모달 닫기"
              title="닫기"
              className="deal-modal-icon-btn bounce-click"
            >
              <X size={17} strokeWidth={2.4} />
            </button>
          </div>
        </div>

        {/* 가격 및 살까/말까 투표 바 */}
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            flexWrap: "wrap",
            gap: "12px",
            background: "var(--surface-subtle)",
            border: "1px solid var(--card-border)",
            borderRadius: "16px",
            padding: "16px",
          }}
        >
          <div>
            <div style={{ fontSize: "0.78rem", color: "var(--text-sub)", fontWeight: 600, display: "flex", alignItems: "center", gap: "6px" }}>
              <span>판매 가격</span>
              {selectedOption && (
                <span
                  style={{
                    fontSize: "0.72rem",
                    color: "var(--accent)",
                    fontWeight: 700,
                    background: "rgba(var(--accent-rgb), 0.1)",
                    padding: "1px 6px",
                    borderRadius: "4px",
                  }}
                >
                  옵션 #{selectedOptionIndex + 1} 적용
                </span>
              )}
            </div>
            <div style={{ color: "var(--deal-orange)", fontSize: "1.45rem", fontWeight: 900, marginTop: "2px", fontVariantNumeric: "tabular-nums" }}>
              {formatPrice(activePrice, activeCurrency, activeOptionTitle || deal.title)}
            </div>
            {deal.shipping_fee && (
              <div style={{ fontSize: "0.8rem", color: "var(--accent)", marginTop: "2px", fontWeight: 700 }}>
                배송비: {deal.shipping_fee}
              </div>
            )}
          </div>

          <div style={{ display: "flex", gap: "8px" }}>
            <button
              type="button"
              onClick={() => handleVote("buy")}
              className="bounce-click"
              style={{
                display: "flex",
                alignItems: "center",
                gap: "6px",
                padding: "8px 14px",
                borderRadius: "12px",
                cursor: "pointer",
                fontWeight: 700,
                fontSize: "0.84rem",
                background: userVote === "buy" ? "rgba(16, 185, 129, 0.14)" : "var(--card-bg)",
                color: userVote === "buy" ? "#10B981" : "var(--text-main)",
                border: userVote === "buy" ? "1px solid rgba(16, 185, 129, 0.45)" : "1px solid var(--card-border)",
              }}
            >
              <ThumbsUp size={15} />
              <span>살까 ({buyCount})</span>
            </button>
            <button
              type="button"
              onClick={() => handleVote("save")}
              className="bounce-click"
              style={{
                display: "flex",
                alignItems: "center",
                gap: "6px",
                padding: "8px 14px",
                borderRadius: "12px",
                cursor: "pointer",
                fontWeight: 700,
                fontSize: "0.84rem",
                background: userVote === "save" ? "rgba(239, 68, 68, 0.14)" : "var(--card-bg)",
                color: userVote === "save" ? "#EF4444" : "var(--text-sub)",
                border: userVote === "save" ? "1px solid rgba(239, 68, 68, 0.45)" : "1px solid var(--card-border)",
              }}
            >
              <ThumbsDown size={15} />
              <span>말까 ({saveCount})</span>
            </button>
          </div>
        </div>

        {/* 1. 본문 상세 내용 (3대 핵심 뼈대 #1) */}
        <div
          style={{
            width: "100%",
            background: "var(--surface-subtle)",
            border: "1px solid var(--card-border)",
            borderRadius: "16px",
            padding: "16px",
            display: "flex",
            flexDirection: "column",
            gap: "12px",
          }}
        >
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: "8px" }}>
            <div style={{ fontSize: "0.82rem", color: "var(--accent)", fontWeight: 800 }}>
              상세 내용 및 구매 조건
            </div>
            {promoCodes.length > 0 && (
              <span style={{ fontSize: "0.72rem", color: "var(--text-sub)", fontWeight: 600 }}>
                코드를 누르면 바로 복사돼요
              </span>
            )}
          </div>

          {/* 원클릭 프로모션/공구 할인코드 복사 칩 */}
          {promoCodes.length > 0 && (
            <div style={{ display: "flex", gap: "6px", flexWrap: "wrap" }}>
              {promoCodes.map((code) => (
                <button
                  key={code}
                  type="button"
                  onClick={async () => {
                    if (typeof navigator !== "undefined" && navigator.clipboard) {
                      await navigator.clipboard.writeText(code);
                      showToast(`할인코드 '${code}'가 복사되었습니다.`);
                    }
                  }}
                  className="bounce-click"
                  style={{
                    display: "inline-flex",
                    alignItems: "center",
                    gap: "5px",
                    padding: "5px 10px",
                    borderRadius: "8px",
                    background: "rgba(var(--accent-rgb), 0.1)",
                    border: "1px dashed rgba(var(--accent-rgb), 0.45)",
                    color: "var(--accent)",
                    fontSize: "0.76rem",
                    fontWeight: 800,
                    cursor: "pointer",
                  }}
                  title="클릭하여 할인코드 복사"
                >
                  <Copy size={12} />
                  <span>{code}</span>
                </button>
              ))}
            </div>
          )}

          <div
            style={{
              fontSize: "0.9rem",
              lineHeight: 1.65,
              color: "var(--text-main)",
              whiteSpace: "pre-line",
              maxHeight: "230px",
              overflowY: "auto",
              paddingRight: "4px",
            }}
          >
            {cleanBodyText
              ? cleanBodyText.slice(0, 3500)
              : deal.aiSummary ||
                `[${deal.community_name}]에서 등록된 핫딜입니다. 쿠폰 및 카드 할인 조건은 아래 쇼핑몰 또는 원본 게시글에서 확인해 주세요.`}
          </div>

          {/* 다중 상품(공구/모음전) 개별 상품 선택 및 바로가기 */}
          {optionSources.length > 0 && (
            <div
              style={{
                borderTop: "1px solid var(--card-border)",
                paddingTop: "14px",
                display: "flex",
                flexDirection: "column",
                gap: "10px",
              }}
            >
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <div style={{ fontSize: "0.84rem", color: "var(--accent)", fontWeight: 800 }}>
                  구성 상품 선택 ({optionSources.length}종)
                </div>
                <span style={{ fontSize: "0.72rem", color: "var(--text-sub)", fontWeight: 600 }}>
                  카드를 누르면 상단 가격과 시세가 바뀌어요
                </span>
              </div>

              <div
                style={{
                  display: "flex",
                  flexDirection: "column",
                  gap: "8px",
                  maxHeight: "320px",
                  overflowY: "auto",
                  paddingRight: "4px",
                }}
              >
                {optionSources.map((src, idx) => {
                  const isSelected = selectedOptionIndex === idx;
                  const optionTitle = cleanOptionTitle(src.site_name);
                  const optionCurrency = src.currency || deal.currency || "KRW";
                  const itemUrl = resolveOutboundUrl(src.ecommerce_url || deal.ecommerce_url || deal.post_link);
                  const isCopied = copiedCode === src.code;

                  return (
                    <div
                      key={`${src.site_name}-${idx}`}
                      role="button"
                      tabIndex={0}
                      onClick={() => setSelectedOptionIndex(idx)}
                      className="bounce-click"
                      style={{
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "space-between",
                        gap: "12px",
                        padding: "10px 12px",
                        borderRadius: "12px",
                        background: isSelected ? "rgba(var(--accent-rgb), 0.08)" : "var(--card-bg)",
                        border: isSelected ? "2px solid var(--accent)" : "1px solid var(--card-border)",
                        boxShadow: isSelected ? "0 4px 14px rgba(var(--accent-rgb), 0.16)" : "0 2px 8px rgba(0,0,0,0.04)",
                        cursor: "pointer",
                        transition: "all 0.2s ease",
                        position: "relative",
                      }}
                    >
                      {/* 1:1 개별 상품 썸네일 (50x50) */}
                      <div
                        style={{
                          width: "50px",
                          height: "50px",
                          borderRadius: "10px",
                          overflow: "hidden",
                          flexShrink: 0,
                          background: "var(--surface-subtle)",
                          border: isSelected ? "1.5px solid var(--accent)" : "1px solid var(--card-border)",
                          display: "flex",
                          alignItems: "center",
                          justifyContent: "center",
                          position: "relative",
                        }}
                      >
                        {src.image_url ? (
                          <img
                            src={getProxyImageUrl(src.image_url, { width: 120 })}
                            alt={optionTitle}
                            style={{ width: "100%", height: "100%", objectFit: "cover" }}
                            referrerPolicy="no-referrer"
                            loading="lazy"
                            onError={(e) => {
                              const fallback = getFallbackProxyImageUrl(src.image_url);
                              if (fallback && e.currentTarget.src !== fallback) {
                                e.currentTarget.src = fallback;
                              } else {
                                e.currentTarget.style.display = "none";
                              }
                            }}
                          />
                        ) : (
                          <span
                            style={{
                              fontSize: "0.75rem",
                              color: isSelected ? "var(--accent)" : "var(--text-sub)",
                              fontWeight: 800,
                            }}
                          >
                            #{idx + 1}
                          </span>
                        )}
                      </div>

                      {/* 상품 정보 (명칭, 가격, 할인코드 복사 칩, 보조링크) */}
                      <div style={{ flex: 1, minWidth: 0, display: "flex", flexDirection: "column", gap: "4px" }}>
                        <div style={{ display: "flex", alignItems: "flex-start", gap: "6px" }}>
                          <span
                            title={optionTitle}
                            style={{
                              fontSize: "0.85rem",
                              fontWeight: isSelected ? 800 : 700,
                              color: isSelected ? "var(--accent)" : "var(--text-main)",
                              display: "-webkit-box",
                              WebkitLineClamp: 2,
                              WebkitBoxOrient: "vertical",
                              overflow: "hidden",
                              lineHeight: 1.35,
                              wordBreak: "keep-all",
                              flex: 1,
                            }}
                          >
                            {optionTitle}
                          </span>
                          {isSelected && (
                            <span
                              style={{
                                fontSize: "0.68rem",
                                fontWeight: 800,
                                color: "var(--accent)",
                                background: "rgba(var(--accent-rgb), 0.12)",
                                padding: "2px 6px",
                                borderRadius: "4px",
                                display: "inline-flex",
                                alignItems: "center",
                                gap: "2px",
                                flexShrink: 0,
                              }}
                            >
                              <Check size={11} strokeWidth={3} />
                              <span>선택</span>
                            </span>
                          )}
                        </div>

                        <div style={{ display: "flex", alignItems: "center", gap: "8px", flexWrap: "wrap" }}>
                          <span
                            style={{
                              fontSize: "0.88rem",
                              fontWeight: 800,
                              color: "var(--deal-orange)",
                              fontVariantNumeric: "tabular-nums",
                            }}
                          >
                            {src.price && src.price > 0 ? formatPrice(src.price, optionCurrency) : "가격 확인"}
                          </span>

                          {/* 원클릭 할인코드 복사 칩 */}
                          {src.code && (
                            <button
                              type="button"
                              onClick={async (e) => {
                                e.preventDefault();
                                e.stopPropagation();
                                try {
                                  if (typeof navigator !== "undefined" && navigator.clipboard) {
                                    await navigator.clipboard.writeText(src.code!);
                                  } else {
                                    const textarea = document.createElement("textarea");
                                    textarea.value = src.code!;
                                    document.body.appendChild(textarea);
                                    textarea.select();
                                    document.execCommand("copy");
                                    document.body.removeChild(textarea);
                                  }
                                  setCopiedCode(src.code!);
                                  setTimeout(() => {
                                    setCopiedCode((prev) => (prev === src.code ? null : prev));
                                  }, 2000);
                                  showToast(`할인코드 '${src.code}'가 복사되었습니다.`);
                                } catch {
                                  showToast("할인코드 복사에 실패했습니다.");
                                }
                              }}
                              className="bounce-click"
                              style={{
                                display: "inline-flex",
                                alignItems: "center",
                                gap: "4px",
                                padding: "2px 8px",
                                borderRadius: "6px",
                                background: isCopied ? "rgba(16, 185, 129, 0.15)" : "rgba(var(--accent-rgb), 0.1)",
                                border: isCopied ? "1px solid #10B981" : "1px dashed rgba(var(--accent-rgb), 0.4)",
                                color: isCopied ? "#10B981" : "var(--accent)",
                                fontSize: "0.72rem",
                                fontWeight: 800,
                                cursor: "pointer",
                                transition: "all 0.2s ease",
                              }}
                              title="클릭하여 할인코드 복사"
                            >
                              {isCopied ? <Check size={11} /> : <Copy size={11} />}
                              <span>{isCopied ? "✓ 복사됨" : src.code}</span>
                            </button>
                          )}

                          {/* 보조 링크들 (sub_urls) */}
                          {Array.isArray(src.sub_urls) && src.sub_urls.length > 0 && (
                            <div
                              style={{ display: "inline-flex", alignItems: "center", gap: "4px" }}
                              onClick={(e) => e.stopPropagation()}
                            >
                              {src.sub_urls.map((sUrl, sIdx) => (
                                <a
                                  key={sIdx}
                                  href={resolveOutboundUrl(sUrl)}
                                  target="_blank"
                                  rel="noopener"
                                  onClick={(e) => e.stopPropagation()}
                                  style={{
                                    fontSize: "0.7rem",
                                    color: "var(--text-sub)",
                                    textDecoration: "underline",
                                    fontWeight: 600,
                                  }}
                                  title={`보조 링크 ${sIdx + 1}`}
                                >
                                  [링크{sIdx + 2}]
                                </a>
                              ))}
                            </div>
                          )}
                        </div>
                      </div>

                      {/* 개별 쇼핑몰 직접 구매 버튼 (클릭 시에만 외부 이동) */}
                      <a
                        href={itemUrl}
                        target="_blank"
                        rel="noopener"
                        onClick={(e) => {
                          e.stopPropagation();
                          if (src.code && typeof navigator !== "undefined" && navigator.clipboard) {
                            navigator.clipboard.writeText(src.code).catch(() => {});
                            showToast(`할인코드 '${src.code}' 복사 완료! 쇼핑몰로 이동합니다.`);
                          }
                        }}
                        className="bounce-click"
                        style={{
                          display: "inline-flex",
                          alignItems: "center",
                          gap: "4px",
                          padding: "7px 12px",
                          borderRadius: "8px",
                          background: isSelected ? "var(--accent)" : "var(--primary-gradient)",
                          color: "#fff",
                          fontSize: "0.78rem",
                          fontWeight: 700,
                          textDecoration: "none",
                          flexShrink: 0,
                          boxShadow: "0 2px 8px rgba(var(--accent-rgb), 0.22)",
                        }}
                        title="쇼핑몰 바로가기"
                      >
                        <span>구매</span>
                        <ExternalLink size={12} color="#fff" />
                      </a>
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </div>

        {/* 가격 추이 차트 */}
        <div
          style={{
            background: "var(--surface-subtle)",
            border: "1px solid var(--card-border)",
            borderRadius: "18px",
            padding: "16px",
          }}
        >
          <PriceTrendChart
            basePrice={activePrice}
            dealId={deal.id}
            currency={activeCurrency}
            optionName={activeOptionTitle}
            onHistoryStatus={(hasData, stats) => {
              setHasChartData(hasData);
              if (stats) setChartStats(stats);
            }}
          />
        </div>

        {/* 목표가 도달 시 알림 설정 카드 */}
        {(() => {
          const isUsd = activeCurrency === "USD";
          const rawPriceNum = typeof activePrice === "number" ? activePrice : parseInt(String(activePrice || "").replace(/[^0-9]/g, ""), 10) || 0;
          const cleanPriceNum = isUsd ? Math.round(rawPriceNum / 100) : rawPriceNum;
          if ((isUsd && cleanPriceNum < 5) || (!isUsd && cleanPriceNum < 1000)) return null;

          const roundUnit = (v: number) => (isUsd ? Math.max(1, Math.floor(v)) : Math.max(100, Math.round(v / 100) * 100));
          const formatUnit = (v: number) => (isUsd ? `$${v.toLocaleString()}` : `${v.toLocaleString()}원`);
          const presets: { label: string; value: number }[] = [];
          const statMin = chartStats ? (isUsd ? Math.round(chartStats.minPrice / 100) : chartStats.minPrice) : 0;
          const statAvg = chartStats ? (isUsd ? Math.round(chartStats.avgPrice / 100) : chartStats.avgPrice) : 0;

          if (statMin > 0 && statMin < cleanPriceNum) {
            presets.push({ label: `역대 최저가 (${formatUnit(statMin)})`, value: statMin });
          }
          if (statAvg > 0 && roundUnit(statAvg * 0.95) < cleanPriceNum) {
            const avgTarget = roundUnit(statAvg * 0.95);
            if (!presets.some((p) => p.value === avgTarget)) {
              presets.push({ label: `평균 -5% (${formatUnit(avgTarget)})`, value: avgTarget });
            }
          }
          [
            { label: "-5%", value: roundUnit(cleanPriceNum * 0.95) },
            { label: "-10%", value: roundUnit(cleanPriceNum * 0.9) },
            { label: "-15%", value: roundUnit(cleanPriceNum * 0.85) },
          ].forEach((item) => {
            if (item.value > 0 && !presets.some((p) => p.value === item.value)) {
              presets.push(item);
            }
          });

          const parsedInput = parseInt(targetPriceInput.replace(/[^0-9]/g, ""), 10) || 0;
          const diffAmount = cleanPriceNum - parsedInput;

          const handleSaveTargetAlert = async (e: React.FormEvent) => {
            e.preventDefault();
            const minValid = isUsd ? 1 : 100;
            if (parsedInput < minValid || savingTargetAlert) {
              showToast("유효한 목표 금액을 입력해 주세요.");
              return;
            }
            setSavingTargetAlert(true);
            try {
              const { deviceUuid, pushGranted } = await ensureWebPushSubscribed();
              const keywordToTrack = (activeOptionTitle || deal.title || "")
                .replace(/\[[^\]]*\]/g, "")
                .replace(/\([^)]*\)/g, "")
                .trim()
                .slice(0, 50);

              const apiTargetPrice = isUsd ? parsedInput * 100 : parsedInput;
              const res = await fetch(apiUrl("/api/wishlist/item-alert"), {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                  user_id: deviceUuid,
                  keyword: keywordToTrack || deal.title.slice(0, 40),
                  target_price: apiTargetPrice,
                }),
              });

              if (res.ok) {
                setSavedTargetPrice(parsedInput);
                try {
                  const mapStr = localStorage.getItem("insightdeal_target_prices");
                  const targetMap = mapStr ? JSON.parse(mapStr) : {};
                  const storageKey = optionSources.length > 0 ? `${deal.id}_${selectedOptionIndex}` : String(deal.id);
                  targetMap[storageKey] = parsedInput;
                  localStorage.setItem("insightdeal_target_prices", JSON.stringify(targetMap));
                } catch {}
                showToast(
                  pushGranted
                    ? `목표가(${formatUnit(parsedInput)}) 이하 도달 시 브라우저 푸시 알림이 발송돼요.`
                    : `목표가(${formatUnit(parsedInput)}) 알림이 저장되었어요. 브라우저 알림 권한을 허용하면 실시간 푸시를 받을 수 있어요.`
                );
              } else {
                showToast("목표가 알림 저장 중 오류가 발생했어요.");
              }
            } catch {
              showToast("네트워크 오류로 목표가 알림 저장에 실패했어요.");
            } finally {
              setSavingTargetAlert(false);
            }
          };

          return (
            <div
              style={{
                background: "rgba(var(--accent-rgb), 0.05)",
                border: "1px solid rgba(var(--accent-rgb), 0.2)",
                borderRadius: "16px",
                padding: "16px",
                display: "flex",
                flexDirection: "column",
                gap: "12px",
              }}
            >
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: "8px" }}>
                <div style={{ display: "flex", alignItems: "center", gap: "6px", fontSize: "0.86rem", fontWeight: 800, color: "var(--text-main)" }}>
                  <Bell size={15} color="var(--accent)" />
                  <span>목표가 도달 시 알림 설정</span>
                </div>
                {savedTargetPrice && (
                  <span
                    style={{
                      fontSize: "0.72rem",
                      fontWeight: 800,
                      color: "#10b981",
                      background: "rgba(16, 185, 129, 0.12)",
                      border: "1px solid rgba(16, 185, 129, 0.25)",
                      padding: "2px 8px",
                      borderRadius: "999px",
                    }}
                  >
                    설정됨 · {formatUnit(savedTargetPrice)} 이하
                  </span>
                )}
              </div>

              <div style={{ display: "flex", gap: "6px", flexWrap: "wrap" }}>
                {presets.map((preset) => {
                  const isSelected = parsedInput === preset.value;
                  return (
                    <button
                      key={preset.label}
                      type="button"
                      onClick={() => setTargetPriceInput(String(preset.value))}
                      style={{
                        fontSize: "0.74rem",
                        fontWeight: 700,
                        padding: "5px 10px",
                        borderRadius: "999px",
                        cursor: "pointer",
                        background: isSelected ? "var(--accent)" : "var(--card-bg)",
                        color: isSelected ? "#fff" : "var(--text-sub)",
                        border: isSelected ? "1px solid var(--accent)" : "1px solid var(--card-border)",
                        transition: "all 0.15s ease",
                      }}
                    >
                      {preset.label}
                    </button>
                  );
                })}
              </div>

              <form onSubmit={handleSaveTargetAlert} style={{ display: "flex", gap: "8px", alignItems: "center" }}>
                <div style={{ position: "relative", flex: 1 }}>
                  <input
                    type="text"
                    inputMode="numeric"
                    value={parsedInput > 0 ? parsedInput.toLocaleString() : ""}
                    onChange={(e) => setTargetPriceInput(e.target.value.replace(/[^0-9]/g, ""))}
                    placeholder="희망 목표 금액 입력"
                    style={{
                      width: "100%",
                      background: "var(--card-bg)",
                      border: "1px solid var(--card-border)",
                      borderRadius: "10px",
                      padding: "9px 32px 9px 12px",
                      color: "var(--text-main)",
                      fontSize: "0.88rem",
                      fontWeight: 800,
                      outline: "none",
                    }}
                  />
                  <span style={{ position: "absolute", right: "12px", top: "50%", transform: "translateY(-50%)", fontSize: "0.8rem", color: "var(--text-sub)", fontWeight: 700 }}>
                    {isUsd ? "$" : "원"}
                  </span>
                </div>
                <button
                  type="submit"
                  disabled={savingTargetAlert}
                  className="btn-primary"
                  style={{ padding: "9px 14px", borderRadius: "10px", fontSize: "0.82rem", whiteSpace: "nowrap" }}
                >
                  <span>{savedTargetPrice ? "목표가 변경" : "알림 받기"}</span>
                </button>
              </form>

              {parsedInput > 0 && (
                <div style={{ fontSize: "0.75rem", color: diffAmount > 0 ? "#10b981" : "var(--text-sub)", fontWeight: 700 }}>
                  {diffAmount > 0
                    ? `현재가보다 ${formatUnit(diffAmount)} 더 내려가면 로그인 없이 이 기기(브라우저)로 즉시 푸시 알림을 보내드려요.`
                    : "현재 판매가 이하로 금액을 설정하면 가격 하락 시 이 기기로 바로 알림을 받을 수 있어요."}
                </div>
              )}
            </div>
          );
        })()}

        {/* 한줄평 댓글 영역 */}
        <div
          style={{
            background: "var(--surface-subtle)",
            border: "1px solid var(--card-border)",
            borderRadius: "16px",
            padding: "16px",
            display: "flex",
            flexDirection: "column",
            gap: "12px",
          }}
        >
          <div style={{ display: "flex", alignItems: "center", gap: "6px", fontSize: "0.85rem", fontWeight: 700 }}>
            <MessageSquare size={15} color="var(--accent)" />
            <span>한줄 의견 ({comments.length})</span>
          </div>

          <form onSubmit={handleAddComment} style={{ display: "flex", gap: "8px" }}>
            <input
              type="text"
              value={commentInput}
              onChange={(e) => setCommentInput(e.target.value)}
              placeholder="이 상품에 대한 의견을 남겨보세요"
              style={{
                flex: 1,
                background: "var(--card-bg)",
                border: "1px solid var(--card-border)",
                borderRadius: "10px",
                padding: "9px 12px",
                color: "var(--text-main)",
                fontSize: "0.85rem",
                outline: "none",
              }}
            />
            <button
              type="submit"
              disabled={submittingComment}
              className="btn-primary"
              style={{ padding: "8px 14px", borderRadius: "10px", fontSize: "0.82rem" }}
            >
              <Send size={14} />
              <span>등록</span>
            </button>
          </form>

          {comments.length > 0 && (
            <div style={{ display: "flex", flexDirection: "column", gap: "8px", maxHeight: "140px", overflowY: "auto" }}>
              {comments.map((c) => (
                <div
                  key={c.id}
                  style={{
                    fontSize: "0.83rem",
                    padding: "8px 10px",
                    borderRadius: "10px",
                    background: "var(--card-bg)",
                    border: "1px solid var(--card-border)",
                  }}
                >
                  <span style={{ fontWeight: 700, color: "var(--accent)", marginRight: "8px" }}>{c.nickname}</span>
                  <span style={{ color: "var(--text-main)" }}>{c.content}</span>
                </div>
              ))}
            </div>
          )}
        </div>

        {/* 2 & 3. 구매처 다이렉트 링크 & 원본 커뮤니티 게시글 바로가기 (하단 Sticky 액션 바) */}
        <div className="deal-modal-footer">
          {/* 선택된 옵션 전용 프로모션 코드 안내 바 */}
          {selectedOption?.code && (
            <div
              style={{
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
                background: "rgba(var(--accent-rgb), 0.08)",
                border: "1px solid rgba(var(--accent-rgb), 0.25)",
                borderRadius: "10px",
                padding: "7px 12px",
                fontSize: "0.78rem",
              }}
            >
              <span style={{ color: "var(--text-sub)", fontWeight: 600 }}>
                선택 옵션 할인코드: <strong style={{ color: "var(--accent)", letterSpacing: "0.02em" }}>{selectedOption.code}</strong>
              </span>
              <button
                type="button"
                onClick={async () => {
                  if (typeof navigator !== "undefined" && navigator.clipboard) {
                    await navigator.clipboard.writeText(selectedOption.code!);
                    showToast(`할인코드 '${selectedOption.code}'가 복사되었습니다.`);
                  }
                }}
                className="bounce-click"
                style={{
                  background: "none",
                  border: "none",
                  color: "var(--accent)",
                  fontWeight: 800,
                  cursor: "pointer",
                  fontSize: "0.76rem",
                  display: "inline-flex",
                  alignItems: "center",
                  gap: "3px",
                  padding: "2px 6px",
                  borderRadius: "4px",
                }}
              >
                <Copy size={12} />
                <span>코드 복사</span>
              </button>
            </div>
          )}

          <div className="deal-modal-actions">
            <a
              className="btn-primary bounce-click deal-modal-btn-primary"
              href={resolveOutboundUrl(activeEcommerceUrl)}
              target="_blank"
              rel="noopener"
              onClick={(e) => {
                const hasValidEcommerceLink =
                  activeEcommerceUrl &&
                  activeEcommerceUrl !== "#" &&
                  String(activeEcommerceUrl).trim() !== "";
                const targetUrl = resolveOutboundUrl(hasValidEcommerceLink ? activeEcommerceUrl : deal.post_link);
                if (!targetUrl || targetUrl === "#") {
                  e.preventDefault();
                  showToast("이동할 수 있는 링크가 없습니다.");
                  return;
                }
                const codeToCopy = selectedOption?.code || (promoCodes.length > 0 ? promoCodes[0] : null);
                if (codeToCopy && typeof navigator !== "undefined" && navigator.clipboard) {
                  navigator.clipboard.writeText(codeToCopy).catch(() => {});
                  showToast(`할인코드 '${codeToCopy}' 복사 완료! 쇼핑몰로 이동합니다.`);
                } else if (!hasValidEcommerceLink) {
                  showToast("쇼핑몰 직접 링크가 없어 원본 게시글로 이동합니다.");
                }
              }}
            >
              <span style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                {selectedOption
                  ? `선택 상품 구매 (${formatPrice(activePrice, activeCurrency)})`
                  : "쇼핑몰 바로가기"}
              </span>
            </a>
            {deal.post_link && deal.post_link !== "#" && (
              <a
                href={resolveOutboundUrl(deal.post_link)}
                target="_blank"
                rel="noopener"
                className="bounce-click deal-modal-btn-secondary"
              >
                <span>원본 게시글 보기</span>
              </a>
            )}
            <button
              type="button"
              className="bounce-click deal-modal-btn-close"
              onClick={onClose}
            >
              <span>닫기</span>
            </button>
          </div>
          <p style={{ fontSize: "0.68rem", color: "var(--text-muted)", textAlign: "center", margin: "1px 0 0 0", lineHeight: 1.3 }}>
            일부 쇼핑몰 이동 링크는 제휴 마케팅의 일환으로 일정액의 수수료를 제공받을 수 있습니다.
          </p>
        </div>
      </div>
    </div>
  );
}
