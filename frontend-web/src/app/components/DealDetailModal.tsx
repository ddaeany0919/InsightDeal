"use client";

import React, { useState, useEffect } from "react";
import { ThumbsUp, ThumbsDown, Share2, MessageSquare, Send, Bell } from "lucide-react";
import type { Deal } from "../lib/types";
import { formatPrice, getProxyImageUrl, apiUrl, resolveOutboundUrl } from "../lib/utils";
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
  const [hasChartData, setHasChartData] = useState(false);
  const [chartStats, setChartStats] = useState<PriceTrendStats | null>(null);
  const [targetPriceInput, setTargetPriceInput] = useState<string>("");
  const [savedTargetPrice, setSavedTargetPrice] = useState<number | null>(null);
  const [savingTargetAlert, setSavingTargetAlert] = useState<boolean>(false);
  const [detailHtml, setDetailHtml] = useState<string>("");
  const [buyCount, setBuyCount] = useState<number>(0);
  const [saveCount, setSaveCount] = useState<number>(0);
  const [userVote, setUserVote] = useState<string | null>(null);
  const [comments, setComments] = useState<DealCommentItem[]>([]);
  const [commentInput, setCommentInput] = useState<string>("");
  const [submittingComment, setSubmittingComment] = useState<boolean>(false);

  const getBrowserUserId = () => {
    if (typeof window === "undefined") return "web_guest";
    return (
      localStorage.getItem("insightdeal_web_device_uuid") ||
      localStorage.getItem("insightdeal_token") ||
      "web_guest"
    );
  };

  useEffect(() => {
    setHasChartData(false);
    setChartStats(null);
    setDetailHtml(deal?.contentHtml || "");
    setBuyCount(0);
    setSaveCount(0);
    setUserVote(null);
    setComments([]);
    setCommentInput("");

    if (!deal) return;

    const rawPriceNum = parseInt(String(deal.price || "").replace(/[^0-9]/g, ""), 10) || 0;
    try {
      const mapStr = localStorage.getItem("insightdeal_target_prices");
      const targetMap = mapStr ? JSON.parse(mapStr) : {};
      if (targetMap && targetMap[deal.id]) {
        setSavedTargetPrice(Number(targetMap[deal.id]));
        setTargetPriceInput(String(targetMap[deal.id]));
      } else {
        setSavedTargetPrice(null);
        setTargetPriceInput(rawPriceNum >= 1000 ? String(Math.round((rawPriceNum * 0.95) / 100) * 100) : "");
      }
    } catch {
      setSavedTargetPrice(null);
      setTargetPriceInput(rawPriceNum >= 1000 ? String(Math.round((rawPriceNum * 0.95) / 100) * 100) : "");
    }

    const uid = getBrowserUserId();

    // 1. 상세 본문(content_html)이 비어 있으면 단건 조회로 보충
    if (!deal.contentHtml) {
      fetch(apiUrl(`/api/community/deals/${deal.id}`))
        .then((r) => (r.ok ? r.json() : null))
        .then((d) => {
          if (d && d.content_html) {
            setDetailHtml(d.content_html);
          }
        })
        .catch(() => {});
    }

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

  if (!deal) return null;

  // HTML 태그를 제거하고 깔끔한 본문 텍스트만 추출
  const cleanBodyText = detailHtml
    ? detailHtml
        .replace(/<br\s*\/?>/gi, "\n")
        .replace(/<\/p>/gi, "\n")
        .replace(/<[^>]+>/g, "")
        .replace(/&nbsp;/g, " ")
        .replace(/&amp;/g, "&")
        .replace(/&lt;/g, "<")
        .replace(/&gt;/g, ">")
        .replace(/\n{3,}/g, "\n\n")
        .trim()
    : "";

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
      style={{
        position: "fixed",
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        background: "rgba(15, 23, 42, 0.55)",
        backdropFilter: "blur(10px)",
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        zIndex: 2000,
        padding: "20px",
        animation: "slideUp 0.25s ease",
      }}
      onClick={onClose}
    >
      <div
        className="glass-panel"
        style={{
          maxWidth: "620px",
          width: "100%",
          borderRadius: "24px",
          padding: "24px 24px 0 24px",
          maxHeight: "90vh",
          overflowY: "auto",
          display: "flex",
          flexDirection: "column",
          gap: "18px",
          border: "1px solid var(--card-border)",
        }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* 헤더 (상품 썸네일 + 메타 배지 + 상품명 + 액션 버튼) */}
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", gap: "14px" }}>
          <div style={{ display: "flex", gap: "14px", alignItems: "flex-start", flex: 1, minWidth: 0 }}>
            {deal.image_url && (
              <div
                style={{
                  width: "76px",
                  height: "76px",
                  borderRadius: "14px",
                  overflow: "hidden",
                  flexShrink: 0,
                  background: "var(--surface-subtle)",
                  border: "1px solid var(--card-border)",
                }}
              >
                <img
                  src={getProxyImageUrl(deal.image_url)}
                  alt={deal.title}
                  style={{ width: "100%", height: "100%", objectFit: "cover" }}
                  referrerPolicy="no-referrer"
                />
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
              <h3 style={{ fontSize: "1.15rem", fontWeight: 800, lineHeight: 1.4, color: "var(--text-main)" }}>
                {deal.title}
              </h3>
            </div>
          </div>
          <div style={{ display: "flex", gap: "8px", flexShrink: 0 }}>
            <button
              onClick={handleShare}
              title="공유하기"
              style={{
                background: "var(--surface-subtle)",
                border: "1px solid var(--card-border)",
                cursor: "pointer",
                width: 34,
                height: 34,
                borderRadius: "50%",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                color: "var(--text-main)",
              }}
            >
              <Share2 size={15} />
            </button>
            <button
              onClick={onClose}
              aria-label="상세 모달 닫기"
              title="닫기"
              style={{
                background: "var(--surface-subtle)",
                border: "1px solid var(--card-border)",
                cursor: "pointer",
                width: 34,
                height: 34,
                borderRadius: "50%",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                color: "var(--text-sub)",
              }}
            >
              ✕
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
            <div style={{ fontSize: "0.78rem", color: "var(--text-sub)", fontWeight: 600 }}>판매 가격</div>
            <div style={{ color: "var(--deal-orange)", fontSize: "1.45rem", fontWeight: 900, marginTop: "2px", fontVariantNumeric: "tabular-nums" }}>
              {formatPrice(deal.price, deal.currency)}
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
          }}
        >
          <div style={{ fontSize: "0.82rem", color: "var(--accent)", fontWeight: 800, marginBottom: "8px" }}>
            상세 내용 및 구매 조건
          </div>
          <div
            style={{
              fontSize: "0.9rem",
              lineHeight: 1.6,
              color: "var(--text-main)",
              whiteSpace: "pre-line",
              maxHeight: "180px",
              overflowY: "auto",
            }}
          >
            {cleanBodyText
              ? cleanBodyText.slice(0, 600)
              : deal.aiSummary ||
                `[${deal.community_name}]에서 등록된 핫딜입니다. 쿠폰 및 카드 할인 조건은 아래 쇼핑몰 또는 원본 게시글에서 확인해 주세요.`}
          </div>
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
            basePrice={deal.price}
            dealId={deal.id}
            onHistoryStatus={(hasData, stats) => {
              setHasChartData(hasData);
              if (stats) setChartStats(stats);
            }}
          />
        </div>

        {/* 목표가 도달 시 알림 설정 카드 */}
        {(() => {
          const cleanPriceNum = parseInt(String(deal.price || "").replace(/[^0-9]/g, ""), 10) || 0;
          if (cleanPriceNum < 1000) return null;

          const round100 = (v: number) => Math.max(100, Math.round(v / 100) * 100);
          const presets: { label: string; value: number }[] = [];
          if (chartStats && chartStats.minPrice >= 1000 && chartStats.minPrice < cleanPriceNum) {
            presets.push({ label: `역대 최저가 (${chartStats.minPrice.toLocaleString()}원)`, value: chartStats.minPrice });
          }
          if (chartStats && chartStats.avgPrice >= 1000 && round100(chartStats.avgPrice * 0.95) < cleanPriceNum) {
            const avgTarget = round100(chartStats.avgPrice * 0.95);
            if (!presets.some((p) => p.value === avgTarget)) {
              presets.push({ label: `평균 -5% (${avgTarget.toLocaleString()}원)`, value: avgTarget });
            }
          }
          [
            { label: "-5%", value: round100(cleanPriceNum * 0.95) },
            { label: "-10%", value: round100(cleanPriceNum * 0.9) },
            { label: "-15%", value: round100(cleanPriceNum * 0.85) },
          ].forEach((item) => {
            if (!presets.some((p) => p.value === item.value)) {
              presets.push(item);
            }
          });

          const parsedInput = parseInt(targetPriceInput.replace(/[^0-9]/g, ""), 10) || 0;
          const diffAmount = cleanPriceNum - parsedInput;

          const handleSaveTargetAlert = async (e: React.FormEvent) => {
            e.preventDefault();
            if (parsedInput < 100 || savingTargetAlert) {
              showToast("유효한 목표 금액을 입력해 주세요.");
              return;
            }
            setSavingTargetAlert(true);
            try {
              const uid = getBrowserUserId();
              const keywordToTrack = (deal.title || "")
                .replace(/\[[^\]]*\]/g, "")
                .replace(/\([^)]*\)/g, "")
                .trim()
                .slice(0, 50);

              const res = await fetch(apiUrl("/api/wishlist/item-alert"), {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                  user_id: uid,
                  keyword: keywordToTrack || deal.title.slice(0, 40),
                  target_price: parsedInput,
                }),
              });

              if (res.ok) {
                setSavedTargetPrice(parsedInput);
                try {
                  const mapStr = localStorage.getItem("insightdeal_target_prices");
                  const targetMap = mapStr ? JSON.parse(mapStr) : {};
                  targetMap[deal.id] = parsedInput;
                  localStorage.setItem("insightdeal_target_prices", JSON.stringify(targetMap));
                } catch {}
                showToast(`목표가(${parsedInput.toLocaleString()}원) 이하 도달 시 알림이 설정되었어요.`);
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
                    설정됨 · {savedTargetPrice.toLocaleString()}원 이하
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
                    원
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
                    ? `현재가보다 ${diffAmount.toLocaleString()}원 더 내려가면 알려드려요.`
                    : "현재 판매가 이하로 금액을 설정하면 가격 하락 시 알림을 받을 수 있어요."}
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
        <div
          style={{
            position: "sticky",
            bottom: 0,
            background: "var(--card-bg)",
            padding: "14px 0 20px 0",
            borderTop: "1px solid var(--card-border)",
            display: "flex",
            flexDirection: "column",
            gap: "10px",
            zIndex: 10,
          }}
        >
          <div style={{ display: "flex", gap: "10px", flexWrap: "wrap" }}>
            <a
              className="btn-primary bounce-click"
              href={resolveOutboundUrl(
                deal.ecommerce_url && deal.ecommerce_url !== "#" && String(deal.ecommerce_url).trim() !== ""
                  ? deal.ecommerce_url
                  : deal.post_link
              )}
              target="_blank"
              rel="noopener"
              onClick={(e) => {
                const hasValidEcommerceLink =
                  deal.ecommerce_url &&
                  deal.ecommerce_url !== "#" &&
                  String(deal.ecommerce_url).trim() !== "";
                const targetUrl = resolveOutboundUrl(hasValidEcommerceLink ? deal.ecommerce_url : deal.post_link);
                if (!targetUrl || targetUrl === "#") {
                  e.preventDefault();
                  showToast("이동할 수 있는 링크가 없습니다.");
                } else if (!hasValidEcommerceLink) {
                  showToast("쇼핑몰 직접 링크가 없어 원본 게시글로 이동합니다.");
                }
              }}
              style={{
                flex: 2,
                minWidth: "180px",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                padding: "14px",
                borderRadius: "14px",
                textDecoration: "none",
              }}
            >
              <span>쇼핑몰 바로가기</span>
            </a>
            {deal.post_link && deal.post_link !== "#" && (
              <a
                href={resolveOutboundUrl(deal.post_link)}
                target="_blank"
                rel="noopener"
                className="bounce-click"
                style={{
                  flex: 1.5,
                  minWidth: "150px",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  padding: "14px",
                  borderRadius: "14px",
                  background: "rgba(var(--accent-rgb), 0.1)",
                  border: "1px solid rgba(var(--accent-rgb), 0.3)",
                  color: "var(--accent)",
                  fontWeight: 800,
                  textDecoration: "none",
                }}
              >
                <span>원본 게시글 보기</span>
              </a>
            )}
            <button
              type="button"
              className="bounce-click"
              onClick={onClose}
              style={{
                flex: 0.7,
                minWidth: "70px",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                padding: "14px",
                borderRadius: "14px",
                background: "var(--surface-subtle)",
                border: "1px solid var(--card-border)",
                color: "var(--text-main)",
                fontWeight: 700,
              }}
            >
              <span>닫기</span>
            </button>
          </div>
          <p style={{ fontSize: "0.7rem", color: "var(--text-muted)", textAlign: "center", margin: "2px 0 0 0" }}>
            일부 쇼핑몰 이동 링크는 제휴 마케팅의 일환으로 일정액의 수수료를 제공받을 수 있습니다.
          </p>
        </div>
      </div>
    </div>
  );
}
