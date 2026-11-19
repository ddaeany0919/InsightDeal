"use client";

import React, { useState, useEffect, useRef, useCallback, useId } from "react";
import {
  Share2,
  Copy,
  ExternalLink,
  Check,
  X,
  BarChart3,
  ThumbsUp,
  MessageSquare,
  Bell,
  Info,
} from "lucide-react";
import type {
  Deal,
  DealSource,
  DealOptionItem,
  DealCommentItem,
  DealVoteApiResponse,
  RawDealApiResponse,
  WishlistApiItem,
} from "../lib/types";
import {
  formatPrice,
  getProxyImageUrl,
  getFallbackProxyImageUrl,
  apiUrl,
  resolveOutboundUrl,
  trackOutboundClick,
  polishDealBodyText,
  extractPromoCodes,
  getOrCreateWebDeviceUuid,
  getStoredWebDeviceToken,
  ensureWebDeviceAuth,
  authFetch,
  ensureWebPushSubscribed,
  getOptionSources,
  cleanOptionTitle,
  decodeHtmlEntities,
  resolveDealShopName,
  stripLeadingShopBracket,
  normalizeMallAlias,
  isNonMallLabel,
  buildDealWishlistKeyword,
  saveWishlistIdEntry,
} from "../lib/utils";
import type { PriceTrendStats } from "./PriceTrendChart";
import DealDetailSecondarySections, {
  type AccordionSectionKey,
} from "./DealDetailSecondarySections";
import { useDialogA11y } from "../lib/useDialogA11y";
import {
  logTossScreen,
  logTossClick,
  maybeRequestTossReview,
  openExternalUrlInToss,
  copyTextSafe,
} from "../lib/tossBridge";

interface DealDetailModalProps {
  deal: Deal | null;
  onClose: () => void;
  showToast: (message: string) => void;
  onTargetAlertSaved?: (dealId: number) => void;
}

const INITIAL_ACCORDION_STATE: Record<AccordionSectionKey, boolean> = {
  chart: false,
  vote: false,
  comments: false,
  target: false,
};

export default function DealDetailModal({
  deal,
  onClose,
  showToast,
  onTargetAlertSaved,
}: DealDetailModalProps) {
  const [selectedOptionIndex, setSelectedOptionIndex] = useState<number>(0);
  const [chartStats, setChartStats] = useState<PriceTrendStats | null>(null);
  const [targetPriceInput, setTargetPriceInput] = useState<string>("");
  const [savedTargetPrice, setSavedTargetPrice] = useState<number | null>(null);
  const [savingTargetAlert, setSavingTargetAlert] = useState<boolean>(false);

  const [detailHtml, setDetailHtml] = useState<string>("");
  const [detailSources, setDetailSources] = useState<DealSource[]>([]);
  const [detailOptionsData, setDetailOptionsData] = useState<DealOptionItem[]>([]);
  const [detailShopName, setDetailShopName] = useState<string>("");

  // 1-RTT 요약 상태
  const [buyCount, setBuyCount] = useState<number>(0);
  const [saveCount, setSaveCount] = useState<number>(0);
  const [userVote, setUserVote] = useState<string | null>(null);
  const [commentCount, setCommentCount] = useState<number>(0);
  const [historyCount, setHistoryCount] = useState<number>(0);
  const [isSearchFallback, setIsSearchFallback] = useState<boolean>(false);
  const [isFreeDeal, setIsFreeDeal] = useState<boolean>(false);

  // 2급 아코디언 상태 및 Lazy-fetch 상태
  const [expandedSections, setExpandedSections] = useState<
    Record<AccordionSectionKey, boolean>
  >(INITIAL_ACCORDION_STATE);
  const [votesLoaded, setVotesLoaded] = useState<boolean>(false);
  const [votesLoading, setVotesLoading] = useState<boolean>(false);
  const [votesError, setVotesError] = useState<string | null>(null);
  const [comments, setComments] = useState<DealCommentItem[]>([]);
  const [commentsLoaded, setCommentsLoaded] = useState<boolean>(false);
  const [commentsLoading, setCommentsLoading] = useState<boolean>(false);
  const [commentsError, setCommentsError] = useState<string | null>(null);
  const [commentInput, setCommentInput] = useState<string>("");
  const [submittingComment, setSubmittingComment] = useState<boolean>(false);
  const [deletingCommentId, setDeletingCommentId] = useState<number | null>(null);
  const [currentDeviceUuid, setCurrentDeviceUuid] = useState<string>("");

  const [copiedCode, setCopiedCode] = useState<string | null>(null);
  const [sheetOffsetY, setSheetOffsetY] = useState<number>(0);
  const [isDraggingSheet, setIsDraggingSheet] = useState<boolean>(false);

  const panelRef = useRef<HTMLDivElement>(null);
  const titleId = useId();
  const sheetOffsetYRef = useRef<number>(0);
  const dragStartYRef = useRef<number | null>(null);
  const closedByPopRef = useRef<boolean>(false);

  const handleDismiss = useCallback(() => {
    sheetOffsetYRef.current = 0;
    dragStartYRef.current = null;
    setSheetOffsetY(0);
    setIsDraggingSheet(false);
    if (
      typeof window !== "undefined" &&
      !closedByPopRef.current &&
      window.history.state?.insightDealModal
    ) {
      closedByPopRef.current = true;
      window.history.back();
      onClose();
      return;
    }
    onClose();
  }, [onClose]);

  // WCAG 2.2 다이얼로그 포커스 이동 / 트랩 / Escape / 트리거 포커스 복귀
  useDialogA11y({
    isOpen: Boolean(deal),
    onClose: handleDismiss,
    containerRef: panelRef,
  });

  // 모달 오픈 시 단건 상세 1-RTT 조회만 실행 (2급 영역은 펼침 시 Lazy-fetch)
  useEffect(() => {
    setSelectedOptionIndex(0);
    setChartStats(null);
    setDetailHtml(deal?.contentHtml || "");
    setDetailSources(deal?.sources || []);
    setDetailOptionsData(deal?.options_data || []);
    setDetailShopName(deal?.shop_name || "");
    setBuyCount(deal?.buyCount ?? 0);
    setSaveCount(deal?.passCount ?? 0);
    setUserVote(deal?.userVote ?? null);
    setCommentCount(deal?.commentCount ?? 0);
    setHistoryCount(deal?.historyCount ?? 0);
    setIsSearchFallback(Boolean(deal?.isSearchFallback));
    setIsFreeDeal(Boolean(deal?.isFree));
    setExpandedSections({ ...INITIAL_ACCORDION_STATE });
    setVotesLoaded(false);
    setVotesLoading(false);
    setVotesError(null);
    setComments([]);
    setCommentsLoaded(false);
    setCommentsLoading(false);
    setCommentsError(null);
    setCommentInput("");
    setCopiedCode(null);
    sheetOffsetYRef.current = 0;
    dragStartYRef.current = null;
    setSheetOffsetY(0);
    setIsDraggingSheet(false);

    if (!deal) return;

    const uid = getOrCreateWebDeviceUuid();
    setCurrentDeviceUuid(uid);

    logTossScreen("deal_detail_view", {
      deal_id: deal.id,
      category: deal.category || "기타",
    });

    const controller = new AbortController();
    const { signal } = controller;

    const initCurr = (deal.currency || "KRW").toUpperCase();
    const isSubunitInit = initCurr === "USD" || initCurr === "EUR" || initCurr === "CNY";
    const rawPriceNum = parseInt(String(deal.price || "").replace(/[^0-9]/g, ""), 10) || 0;
    const defaultTarget = isSubunitInit
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

    const storedToken = getStoredWebDeviceToken();
    const detailHeaders: Record<string, string> = {};
    if (storedToken) {
      detailHeaders["X-Device-Token"] = storedToken;
    }

    // 1-RTT 상세 조회 (본문 + 옵션 + 1-RTT 요약 필 통합 수신)
    fetch(
      apiUrl(`/api/community/deals/${deal.id}?user_id=${encodeURIComponent(uid)}`),
      { signal, headers: detailHeaders }
    )
      .then((r) => (r.ok ? (r.json() as Promise<RawDealApiResponse>) : null))
      .then((d) => {
        if (signal.aborted || !d) return;
        const htmlBody = d.content_html ?? d.contentHtml;
        if (htmlBody) setDetailHtml(htmlBody);
        if (Array.isArray(d.sources) && d.sources.length > 0) {
          setDetailSources(d.sources);
        }
        if (Array.isArray(d.options_data) && d.options_data.length > 0) {
          setDetailOptionsData(d.options_data);
        } else if (Array.isArray(d.optionsData) && d.optionsData.length > 0) {
          setDetailOptionsData(d.optionsData);
        }
        if (d.shop_name || d.shopName) {
          setDetailShopName(String(d.shop_name || d.shopName));
        }

        const nextBuy = d.buy_count ?? d.buyCount ?? d.like_count;
        if (nextBuy !== undefined) setBuyCount(Number(nextBuy) || 0);

        const nextPass =
          d.pass_count ?? d.passCount ?? d.save_count ?? d.saveCount ?? d.dislike_count;
        if (nextPass !== undefined) setSaveCount(Number(nextPass) || 0);

        const nextVote = d.user_vote ?? d.userVote;
        if (nextVote !== undefined) setUserVote(nextVote ?? null);

        const nextCommentCnt = d.comment_count ?? d.commentCount ?? d.comments;
        if (nextCommentCnt !== undefined) setCommentCount(Number(nextCommentCnt) || 0);

        const nextHistoryCnt = d.history_count ?? d.historyCount;
        if (nextHistoryCnt !== undefined) setHistoryCount(Number(nextHistoryCnt) || 0);

        const nextFallback = d.is_search_fallback ?? d.isSearchFallback;
        if (nextFallback !== undefined) setIsSearchFallback(Boolean(nextFallback));

        const nextFree = d.is_free ?? d.isFree;
        if (nextFree !== undefined) setIsFreeDeal(Boolean(nextFree));
      })
      .catch(() => {});

    return () => {
      controller.abort();
    };
  }, [deal]);

  const dealId = deal?.id ?? null;

  useEffect(() => {
    if (dealId === null || typeof window === "undefined") return;

    closedByPopRef.current = false;
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";

    if (!window.history.state?.insightDealModal) {
      try {
        window.history.pushState({ insightDealModal: dealId }, "");
      } catch {}
    }

    const handlePopState = () => {
      closedByPopRef.current = true;
      sheetOffsetYRef.current = 0;
      onClose();
    };

    window.addEventListener("popstate", handlePopState);
    return () => {
      document.body.style.overflow = prevOverflow;
      window.removeEventListener("popstate", handlePopState);
    };
  }, [dealId, onClose]);

  const effectiveOptionsData =
    detailOptionsData.length > 0 ? detailOptionsData : deal?.options_data;
  const preOptionSources = deal
    ? getOptionSources(detailSources, effectiveOptionsData)
    : [];
  const preSelectedOption =
    preOptionSources.length > 0 && selectedOptionIndex < preOptionSources.length
      ? preOptionSources[selectedOptionIndex]
      : null;
  const preActivePrice =
    preSelectedOption && preSelectedOption.price !== undefined && preSelectedOption.price >= 0
      ? preSelectedOption.price
      : deal?.price;
  const preActiveCurrency = preSelectedOption?.currency || deal?.currency || "KRW";

  useEffect(() => {
    if (dealId === null) return;
    const preCurr = (preActiveCurrency || "KRW").toUpperCase();
    const isSubunitPre = preCurr === "USD" || preCurr === "EUR" || preCurr === "CNY";
    const rawPriceNum =
      typeof preActivePrice === "number"
        ? preActivePrice
        : parseInt(String(preActivePrice || "").replace(/[^0-9]/g, ""), 10) || 0;
    const defaultTarget = isSubunitPre
      ? rawPriceNum >= 500
        ? Math.max(1, Math.floor((rawPriceNum / 100) * 0.95))
        : 0
      : rawPriceNum >= 1000
        ? Math.round((rawPriceNum * 0.95) / 100) * 100
        : 0;
    try {
      const mapStr = localStorage.getItem("insightdeal_target_prices");
      const targetMap = mapStr ? JSON.parse(mapStr) : {};
      const key =
        preOptionSources.length > 0 ? `${dealId}_${selectedOptionIndex}` : String(dealId);
      const savedVal =
        targetMap?.[key] ?? (selectedOptionIndex === 0 ? targetMap?.[dealId] : undefined);
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
  }, [dealId, selectedOptionIndex, preActivePrice, preActiveCurrency, preOptionSources.length]);

  const loadVotesLazy = useCallback(async (targetDealId: number) => {
    setVotesLoading(true);
    setVotesError(null);
    try {
      await ensureWebDeviceAuth();
      const res = await authFetch(
        (latestAuth) =>
          `/api/community/deals/${targetDealId}/votes?user_id=${encodeURIComponent(latestAuth.deviceUuid)}`
      );
      setCurrentDeviceUuid(getOrCreateWebDeviceUuid());
      if (!res.ok) {
        setVotesError("투표 현황을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.");
        return;
      }
      const v = (await res.json()) as DealVoteApiResponse;
      setBuyCount(Number(v.buy_count ?? v.buyCount ?? 0));
      setSaveCount(Number(v.pass_count ?? v.passCount ?? v.save_count ?? v.saveCount ?? 0));
      setUserVote((v.user_vote ?? v.userVote ?? null) as string | null);
      setVotesLoaded(true);
    } catch {
      setVotesError("투표 현황을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setVotesLoading(false);
    }
  }, []);

  const loadCommentsLazy = useCallback(async (targetDealId: number) => {
    setCommentsLoading(true);
    setCommentsError(null);
    try {
      const storedToken = getStoredWebDeviceToken();
      const headers: Record<string, string> = {};
      if (storedToken) headers["X-Device-Token"] = storedToken;
      const res = await fetch(apiUrl(`/api/community/deals/${targetDealId}/comments`), {
        headers,
      });
      if (!res.ok) {
        setCommentsError("의견을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.");
        return;
      }
      const list = (await res.json()) as DealCommentItem[];
      if (Array.isArray(list)) {
        const normalized = list.map((c) => ({
          ...c,
          userId: c.userId ?? c.user_id ?? "",
          createdAt: c.createdAt ?? c.created_at ?? "",
        }));
        setComments(normalized);
        setCommentCount(normalized.length);
        setCommentsLoaded(true);
      } else {
        setCommentsError("의견 형식이 올바르지 않아요. 잠시 후 다시 시도해 주세요.");
      }
    } catch {
      setCommentsError("의견을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.");
    } finally {
      setCommentsLoading(false);
    }
  }, []);

  const handleToggleSection = useCallback(
    (key: AccordionSectionKey) => {
      if (!deal) return;
      const nextOpen = !expandedSections[key];
      setExpandedSections((prev) => ({ ...prev, [key]: !prev[key] }));
      if (nextOpen) {
        if (key === "vote" && !votesLoaded && !votesLoading) {
          void loadVotesLazy(deal.id);
        } else if (key === "comments" && !commentsLoaded && !commentsLoading) {
          void loadCommentsLazy(deal.id);
        }
      }
    },
    [
      deal,
      expandedSections,
      votesLoaded,
      votesLoading,
      commentsLoaded,
      commentsLoading,
      loadVotesLazy,
      loadCommentsLazy,
    ]
  );

  const handleJumpToSection = useCallback(
    (key: AccordionSectionKey) => {
      if (!deal) return;
      const wasClosed = !expandedSections[key];
      setExpandedSections((prev) => ({ ...prev, [key]: true }));
      if (wasClosed) {
        if (key === "vote" && !votesLoaded && !votesLoading) {
          void loadVotesLazy(deal.id);
        } else if (key === "comments" && !commentsLoaded && !commentsLoading) {
          void loadCommentsLazy(deal.id);
        }
      }
      if (typeof window !== "undefined") {
        window.requestAnimationFrame(() => {
          document.getElementById(`deal-accordion-${key}-${deal.id}`)?.scrollIntoView({
            behavior: "smooth",
            block: "nearest",
          });
        });
      }
    },
    [
      deal,
      expandedSections,
      votesLoaded,
      votesLoading,
      commentsLoaded,
      commentsLoading,
      loadVotesLazy,
      loadCommentsLazy,
    ]
  );

  if (!deal) return null;

  const cleanBodyText = polishDealBodyText(detailHtml);
  const promoCodes = extractPromoCodes(cleanBodyText, deal.title);
  const optionSources = getOptionSources(detailSources, effectiveOptionsData);

  const selectedOption =
    optionSources.length > 0 && selectedOptionIndex < optionSources.length
      ? optionSources[selectedOptionIndex]
      : null;

  const activeOptionTitle = selectedOption ? cleanOptionTitle(selectedOption.site_name) : "";
  const activePrice =
    selectedOption && selectedOption.price !== undefined && selectedOption.price >= 0
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

  const shopBadge = resolveDealShopName({
    shop_name: detailShopName || deal.shop_name,
    title: deal.title,
    ecommerce_url: activeEcommerceUrl,
  });
  const displayTitle = stripLeadingShopBracket(deal.title, shopBadge);
  const displayShipping = deal.shipping_fee ? decodeHtmlEntities(deal.shipping_fee) : null;

  const isSelectedOptionZeroFree =
    selectedOption !== null &&
    (selectedOption.price === 0 || (isFreeDeal && !selectedOption.price));
  const showZeroWonPrice =
    isSelectedOptionZeroFree ||
    (isFreeDeal && !selectedOption) ||
    String(activePrice ?? "").trim() === "0원";

  const normActiveCurr = (activeCurrency || "KRW").toUpperCase();
  const isSubunitCurr =
    normActiveCurr === "USD" || normActiveCurr === "EUR" || normActiveCurr === "CNY";
  const isForeignCurr = isSubunitCurr || normActiveCurr === "JPY";
  const currSymbol =
    normActiveCurr === "USD"
      ? "$"
      : normActiveCurr === "EUR"
        ? "€"
        : normActiveCurr === "CNY" || normActiveCurr === "JPY"
          ? "¥"
          : "원";
  const rawPriceNum =
    typeof activePrice === "number"
      ? activePrice
      : parseInt(String(activePrice || "").replace(/[^0-9]/g, ""), 10) || 0;
  const cleanPriceNum = isSubunitCurr ? Math.round(rawPriceNum / 100) : rawPriceNum;
  const canShowTargetAlert =
    !isFreeDeal &&
    !(
      (isSubunitCurr && cleanPriceNum < 5) ||
      (normActiveCurr === "JPY" && cleanPriceNum < 500) ||
      (!isForeignCurr && cleanPriceNum < 1000)
    );

  const roundUnit = (v: number) =>
    isSubunitCurr
      ? Math.max(1, Math.floor(v))
      : normActiveCurr === "JPY"
        ? Math.max(50, Math.round(v / 10) * 10)
        : Math.max(100, Math.round(v / 100) * 100);
  const formatUnit = (v: number) =>
    isForeignCurr ? `${currSymbol}${v.toLocaleString()}` : `${v.toLocaleString()}원`;

  const presets: { label: string; value: number }[] = [];
  if (canShowTargetAlert) {
    const statMin = chartStats
      ? isSubunitCurr
        ? Math.round(chartStats.minPrice / 100)
        : chartStats.minPrice
      : 0;
    const statAvg = chartStats
      ? isSubunitCurr
        ? Math.round(chartStats.avgPrice / 100)
        : chartStats.avgPrice
      : 0;

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
  }

  const parsedTargetInput = parseInt(targetPriceInput.replace(/[^0-9]/g, ""), 10) || 0;
  const diffAmount = cleanPriceNum - parsedTargetInput;

  const handleVote = async (voteType: "buy" | "save") => {
    try {
      await ensureWebDeviceAuth();
      const res = await authFetch((latestAuth) => ({
        pathOrUrl: `/api/community/deals/${deal.id}/votes`,
        init: {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ user_id: latestAuth.deviceUuid, vote_type: voteType }),
        },
      }));
      setCurrentDeviceUuid(getOrCreateWebDeviceUuid());
      if (!res.ok) {
        showToast("투표를 반영하지 못했어요. 잠시 후 다시 시도해 주세요.");
        return;
      }
      const data = (await res.json()) as DealVoteApiResponse;
      setBuyCount(Number(data.buy_count ?? data.buyCount ?? 0));
      setSaveCount(
        Number(data.pass_count ?? data.passCount ?? data.save_count ?? data.saveCount ?? 0)
      );
      setUserVote((data.user_vote ?? data.userVote ?? voteType) as string | null);
      setVotesError(null);
      setVotesLoaded(true);
      showToast(voteType === "buy" ? "'살까'에 투표했어요" : "'말까'에 투표했어요");
    } catch {
      showToast("잠시 후 다시 시도해 주세요");
    }
  };

  const handleAddComment = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = commentInput.trim();
    if (!trimmed || submittingComment) return;
    setSubmittingComment(true);
    try {
      await ensureWebDeviceAuth();
      const res = await authFetch((latestAuth) => ({
        pathOrUrl: `/api/community/deals/${deal.id}/comments`,
        init: {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            user_id: latestAuth.deviceUuid,
            nickname: `회원_${latestAuth.deviceUuid.slice(-4)}`,
            content: trimmed,
          }),
        },
      }));
      const latestUuid = getOrCreateWebDeviceUuid();
      setCurrentDeviceUuid(latestUuid);
      if (!res.ok) {
        showToast("의견을 등록하지 못했어요. 잠시 후 다시 시도해 주세요.");
        return;
      }
      const created = (await res.json()) as DealCommentItem;
      const normalized: DealCommentItem = {
        ...created,
        userId: created.userId ?? created.user_id ?? latestUuid,
        createdAt: created.createdAt ?? created.created_at ?? new Date().toISOString(),
      };
      setComments((prev) => [normalized, ...prev]);
      setCommentCount((c) => c + 1);
      setCommentsError(null);
      setCommentsLoaded(true);
      setCommentInput("");
      showToast("의견을 남겼어요");
    } catch {
      showToast("의견을 등록하지 못했어요");
    } finally {
      setSubmittingComment(false);
    }
  };

  const handleDeleteComment = async (commentId: number) => {
    if (deletingCommentId !== null) return;
    setDeletingCommentId(commentId);
    try {
      await ensureWebDeviceAuth();
      const res = await authFetch(
        (latestAuth) =>
          `/api/community/deals/${deal.id}/comments/${commentId}?user_id=${encodeURIComponent(latestAuth.deviceUuid)}`,
        { method: "DELETE" }
      );
      setCurrentDeviceUuid(getOrCreateWebDeviceUuid());
      if (!res.ok) {
        showToast("의견을 삭제하지 못했어요. 잠시 후 다시 시도해 주세요.");
        return;
      }
      setComments((prev) => prev.filter((c) => c.id !== commentId));
      setCommentCount((c) => Math.max(0, c - 1));
      showToast("내 의견을 삭제했어요");
    } catch {
      showToast("네트워크 오류로 의견을 삭제하지 못했어요");
    } finally {
      setDeletingCommentId(null);
    }
  };

  const handleSaveTargetAlert = async (e: React.FormEvent) => {
    e.preventDefault();
    const minValid = isSubunitCurr ? 1 : normActiveCurr === "JPY" ? 50 : 100;
    if (parsedTargetInput < minValid || savingTargetAlert) {
      showToast("목표 금액을 확인해 주세요");
      return;
    }
    setSavingTargetAlert(true);
    try {
      const { pushGranted } = await ensureWebPushSubscribed();
      const keywordToTrack = buildDealWishlistKeyword(deal.title, activeOptionTitle);
      const apiTargetPrice = isSubunitCurr ? parsedTargetInput * 100 : parsedTargetInput;

      const res = await authFetch((latestAuth) => ({
        pathOrUrl: "/api/wishlist/item-alert",
        init: {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            user_id: latestAuth.deviceUuid,
            keyword: keywordToTrack || deal.title.slice(0, 40),
            title: decodeHtmlEntities(deal.title),
            product_url: activeEcommerceUrl || deal.post_link,
            target_price: apiTargetPrice,
          }),
        },
      }));
      setCurrentDeviceUuid(getOrCreateWebDeviceUuid());

      if (!res.ok) {
        showToast("목표가 알림 저장 중 오류가 발생했어요.");
        return;
      }

      const saved = (await res.json().catch(() => null)) as WishlistApiItem | null;
      const storageKey =
        optionSources.length > 0 ? `${deal.id}_${selectedOptionIndex}` : String(deal.id);
      if (saved && typeof saved.id === "number") {
        saveWishlistIdEntry(storageKey, saved.id, saved.keyword || keywordToTrack);
        if (selectedOptionIndex === 0) {
          saveWishlistIdEntry(deal.id, saved.id, saved.keyword || keywordToTrack);
        }
      }

      setSavedTargetPrice(parsedTargetInput);
      try {
        const mapStr = localStorage.getItem("insightdeal_target_prices");
        const targetMap = mapStr ? JSON.parse(mapStr) : {};
        targetMap[storageKey] = parsedTargetInput;
        localStorage.setItem("insightdeal_target_prices", JSON.stringify(targetMap));
      } catch {}

      onTargetAlertSaved?.(deal.id);

      showToast(
        pushGranted
          ? `목표가(${formatUnit(parsedTargetInput)}) 이하 도달 시 브라우저 푸시 알림이 발송돼요.`
          : `목표가(${formatUnit(parsedTargetInput)}) 알림이 저장되었어요. 브라우저 알림 권한을 허용하면 실시간 푸시를 받을 수 있어요.`
      );
      logTossClick("save_target_price_alert", {
        button_name: "target_price_alert_button",
        deal_id: deal.id,
      });
      maybeRequestTossReview();
    } catch {
      showToast("네트워크 오류로 목표가 알림 저장에 실패했어요.");
    } finally {
      setSavingTargetAlert(false);
    }
  };

  const handleShare = async () => {
    const cleanShareTitle = decodeHtmlEntities(deal.title);
    const shareUrl =
      typeof window !== "undefined"
        ? `${window.location.origin}/?dealId=${deal.id}`
        : deal.post_link;
    const sharePrice = showZeroWonPrice
      ? "0원 (무료)"
      : formatPrice(deal.price, deal.currency, cleanShareTitle);
    const shareText = `${cleanShareTitle} (${sharePrice})`;

    if (typeof navigator !== "undefined" && navigator.share) {
      try {
        await navigator.share({ title: cleanShareTitle, text: shareText, url: shareUrl });
        return;
      } catch {}
    }
    const copied = await copyTextSafe(`${shareText}\n${shareUrl}`);
    if (copied) {
      showToast("상품 주소를 복사했어요");
    } else {
      showToast("상품 주소를 복사하지 못했어요");
    }
  };

  const handleHeaderTouchStart = (e: React.TouchEvent) => {
    if (e.touches.length === 1) {
      dragStartYRef.current = e.touches[0].clientY;
      sheetOffsetYRef.current = 0;
      setSheetOffsetY(0);
      setIsDraggingSheet(true);
    }
  };

  const handleHeaderTouchMove = (e: React.TouchEvent) => {
    if (dragStartYRef.current === null) return;
    const deltaY = e.touches[0].clientY - dragStartYRef.current;
    const nextOffset = deltaY > 0 ? Math.min(260, deltaY) : 0;
    sheetOffsetYRef.current = nextOffset;
    setSheetOffsetY(nextOffset);
  };

  const handleHeaderTouchEnd = () => {
    if (dragStartYRef.current === null) return;
    const currentOffset = sheetOffsetYRef.current;
    dragStartYRef.current = null;
    setIsDraggingSheet(false);
    if (currentOffset > 75) {
      sheetOffsetYRef.current = 0;
      handleDismiss();
    } else {
      sheetOffsetYRef.current = 0;
      setSheetOffsetY(0);
    }
  };

  const primaryCtaLabel = isSearchFallback
    ? "쇼핑몰 검색 결과 보기"
    : selectedOption
      ? `선택 상품 구매 (${showZeroWonPrice ? "0원 (무료)" : formatPrice(activePrice, activeCurrency, activeOptionTitle || deal.title)})`
      : "쇼핑몰 바로가기";

  return (
    <div className="deal-modal-overlay" onClick={handleDismiss}>
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        className="glass-panel deal-modal-panel"
        onClick={(e) => e.stopPropagation()}
        style={{
          transform: sheetOffsetY > 0 ? `translateY(${sheetOffsetY}px)` : undefined,
          transition: isDraggingSheet
            ? "none"
            : "transform 0.22s cubic-bezier(0.22, 1, 0.36, 1)",
        }}
      >
        {/* 상단 Sticky 헤더 */}
        <div
          className="deal-modal-header-sticky"
          onTouchStart={handleHeaderTouchStart}
          onTouchMove={handleHeaderTouchMove}
          onTouchEnd={handleHeaderTouchEnd}
          onTouchCancel={handleHeaderTouchEnd}
        >
          <div className="deal-modal-grabber" aria-hidden="true" />
          <div className="deal-modal-header">
            <div className="deal-modal-header-main">
              {activeImageUrl && (
                <div
                  key={activeImageUrl}
                  className="deal-modal-thumb"
                  style={{
                    border: selectedOption
                      ? "1.5px solid var(--accent)"
                      : "1px solid var(--card-border)",
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
                  {selectedOption &&
                    (selectedOptionIndex === 0 ||
                      Boolean(selectedOption.image_url && selectedOption.image_url.trim() !== "")) && (
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
                <div
                  style={{
                    display: "flex",
                    alignItems: "center",
                    gap: "6px",
                    marginBottom: "6px",
                    flexWrap: "wrap",
                  }}
                >
                  <span className="deal-source">{deal.community_name}</span>
                  {shopBadge && (
                    <span
                      style={{
                        fontSize: "0.73rem",
                        padding: "3px 9px",
                        borderRadius: "6px",
                        background: "var(--badge-bg)",
                        border: "1px solid var(--card-border)",
                        color: "var(--text-main)",
                        fontWeight: 700,
                        letterSpacing: "-0.01em",
                      }}
                    >
                      {shopBadge}
                    </span>
                  )}
                  {deal.tags &&
                    deal.tags.map((t, idx) => {
                      const cleanTag = decodeHtmlEntities(String(t))
                        .replace(/^[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}\uFE0F]+\s*/gu, "")
                        .trim();
                      if (!cleanTag) return null;
                      if (shopBadge && normalizeMallAlias(cleanTag) === shopBadge) return null;
                      if (
                        isNonMallLabel(cleanTag) &&
                        cleanTag !== "역대가" &&
                        cleanTag !== "할인코드"
                      ) {
                        return null;
                      }
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
                <h3 id={titleId} className="deal-modal-title">
                  {displayTitle}
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
                <Share2 size={15} aria-hidden="true" />
              </button>
              <button
                type="button"
                onClick={handleDismiss}
                aria-label="상세 모달 닫기"
                title="닫기"
                className="deal-modal-icon-btn deal-modal-close-icon-btn bounce-click"
              >
                <X size={18} strokeWidth={2.5} aria-hidden="true" />
              </button>
            </div>
          </div>
        </div>

        {/* 1급 영역 ①: 가격 요약 & 검색 폴백 배지 */}
        <div
          style={{
            display: "flex",
            flexDirection: "column",
            gap: "10px",
            background: "var(--surface-subtle)",
            border: "1px solid var(--card-border)",
            borderRadius: "16px",
            padding: "16px",
          }}
        >
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-end", flexWrap: "wrap", gap: "10px" }}>
            <div>
              <div
                style={{
                  fontSize: "0.78rem",
                  color: "var(--text-sub)",
                  fontWeight: 600,
                  display: "flex",
                  alignItems: "center",
                  gap: "6px",
                }}
              >
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
              <div
                style={{
                  color: "var(--deal-orange)",
                  fontSize: "1.45rem",
                  fontWeight: 900,
                  marginTop: "2px",
                  fontVariantNumeric: "tabular-nums",
                }}
              >
                {showZeroWonPrice
                  ? "0원 (무료)"
                  : formatPrice(activePrice, activeCurrency, activeOptionTitle || deal.title)}
              </div>
              {displayShipping && (
                <div style={{ fontSize: "0.8rem", color: "var(--accent)", marginTop: "2px", fontWeight: 700 }}>
                  배송비: {displayShipping}
                </div>
              )}
            </div>
          </div>

          {isSearchFallback && (
            <div
              role="note"
              style={{
                display: "flex",
                alignItems: "center",
                gap: "8px",
                padding: "9px 12px",
                borderRadius: "10px",
                background: "rgba(245, 158, 11, 0.12)",
                border: "1px solid rgba(245, 158, 11, 0.3)",
                color: "#b45309",
                fontSize: "0.78rem",
                fontWeight: 700,
              }}
            >
              <Info size={14} aria-hidden="true" style={{ flexShrink: 0 }} />
              <span>직접 링크 미제공 · 쇼핑몰 검색 결과로 이동해요</span>
            </div>
          )}
        </div>

        {/* 1급 영역 ②: 1-RTT Summary Pill 스트립 (클릭 시 2급 아코디언 바로 열기) */}
        <div className="deal-summary-strip" aria-label="상세 요약 바로가기">
          <button
            type="button"
            className={`deal-summary-pill ${expandedSections.chart ? "active" : ""}`}
            onClick={() => handleJumpToSection("chart")}
          >
            <BarChart3 size={14} aria-hidden="true" />
            <span>가격 추이 ({historyCount}건)</span>
          </button>
          <button
            type="button"
            className={`deal-summary-pill ${expandedSections.vote ? "active" : ""}`}
            onClick={() => handleJumpToSection("vote")}
          >
            <ThumbsUp size={14} aria-hidden="true" />
            <span>
              살까 {buyCount} / 말까 {saveCount}
            </span>
          </button>
          <button
            type="button"
            className={`deal-summary-pill ${expandedSections.comments ? "active" : ""}`}
            onClick={() => handleJumpToSection("comments")}
          >
            <MessageSquare size={14} aria-hidden="true" />
            <span>의견 {Math.max(commentCount, comments.length)}개</span>
          </button>
          {canShowTargetAlert && (
            <button
              type="button"
              className={`deal-summary-pill ${expandedSections.target ? "active" : ""}`}
              onClick={() => handleJumpToSection("target")}
            >
              <Bell size={14} aria-hidden="true" />
              <span>목표가 알림</span>
            </button>
          )}
        </div>

        {/* 1급 영역 ③: 본문 상세 내용 (이중 스크롤 제거) + 할인코드 copyTextSafe + 다중 옵션 */}
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
          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
              flexWrap: "wrap",
              gap: "8px",
            }}
          >
            <div style={{ fontSize: "0.82rem", color: "var(--accent)", fontWeight: 800 }}>
              상세 내용 및 구매 조건
            </div>
            {promoCodes.length > 0 && (
              <span style={{ fontSize: "0.72rem", color: "var(--text-sub)", fontWeight: 600 }}>
                코드를 누르면 바로 복사돼요
              </span>
            )}
          </div>

          {promoCodes.length > 0 && (
            <div style={{ display: "flex", gap: "6px", flexWrap: "wrap" }}>
              {promoCodes.map((code) => (
                <button
                  key={code}
                  type="button"
                  onClick={async () => {
                    const copied = await copyTextSafe(code);
                    if (copied) {
                      showToast(`할인코드 '${code}'를 복사했어요`);
                    } else {
                      showToast("할인코드를 복사하지 못했어요");
                    }
                  }}
                  className="bounce-click"
                  style={{
                    display: "inline-flex",
                    alignItems: "center",
                    gap: "5px",
                    minHeight: "36px",
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
                  <Copy size={12} aria-hidden="true" />
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
              wordBreak: "break-word",
            }}
          >
            {cleanBodyText
              ? cleanBodyText.slice(0, 3500)
              : deal.aiSummary ||
                `[${deal.community_name}]에서 등록된 핫딜입니다. 쿠폰 및 카드 할인 조건은 아래 쇼핑몰 또는 원본 게시글에서 확인해 주세요.`}
          </div>

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
                }}
              >
                {optionSources.map((src, idx) => {
                  const isSelected = selectedOptionIndex === idx;
                  const optionTitle = cleanOptionTitle(src.site_name);
                  const optionCurrency = src.currency || deal.currency || "KRW";
                  const itemUrl = resolveOutboundUrl(
                    src.ecommerce_url || deal.ecommerce_url || deal.post_link
                  );
                  const isCopied = copiedCode === src.code;

                  return (
                    <div
                      key={`${src.site_name}-${idx}`}
                      style={{
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "space-between",
                        gap: "10px",
                        padding: "10px 12px",
                        borderRadius: "12px",
                        background: isSelected
                          ? "rgba(var(--accent-rgb), 0.08)"
                          : "var(--card-bg)",
                        border: isSelected
                          ? "2px solid var(--accent)"
                          : "1px solid var(--card-border)",
                        boxShadow: isSelected
                          ? "0 4px 14px rgba(var(--accent-rgb), 0.16)"
                          : "0 2px 8px rgba(0,0,0,0.04)",
                        position: "relative",
                      }}
                    >
                      {/* 옵션 선택 본문 영역 (형제 버튼/링크와 중첩 분리) */}
                      <div
                        role="button"
                        tabIndex={0}
                        onClick={() => {
                          if (selectedOptionIndex !== idx) {
                            setChartStats(null);
                            setSelectedOptionIndex(idx);
                          }
                        }}
                        onKeyDown={(e) => {
                          if (e.key === "Enter" || e.key === " ") {
                            e.preventDefault();
                            if (selectedOptionIndex !== idx) {
                              setChartStats(null);
                              setSelectedOptionIndex(idx);
                            }
                          }
                        }}
                        style={{
                          flex: 1,
                          minWidth: 0,
                          display: "flex",
                          alignItems: "center",
                          gap: "12px",
                          cursor: "pointer",
                          borderRadius: "8px",
                        }}
                      >
                        <div
                          style={{
                            width: "50px",
                            height: "50px",
                            borderRadius: "10px",
                            overflow: "hidden",
                            flexShrink: 0,
                            background: "var(--surface-subtle)",
                            border: isSelected
                              ? "1.5px solid var(--accent)"
                              : "1px solid var(--card-border)",
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
                                <Check size={11} strokeWidth={3} aria-hidden="true" />
                                <span>선택</span>
                              </span>
                            )}
                          </div>

                          <span
                            style={{
                              fontSize: "0.88rem",
                              fontWeight: 800,
                              color: "var(--deal-orange)",
                              fontVariantNumeric: "tabular-nums",
                            }}
                          >
                            {src.price === 0
                              ? "0원 (무료)"
                              : src.price && src.price > 0
                                ? formatPrice(src.price, optionCurrency, optionTitle)
                                : formatPrice(0, optionCurrency, optionTitle)}
                          </span>
                        </div>
                      </div>

                      {/* 형제 액션 영역 (코드 복사 / 보조 링크 / 구매 버튼) */}
                      <div style={{ display: "flex", alignItems: "center", gap: "6px", flexShrink: 0 }}>
                        {src.code && (
                          <button
                            type="button"
                            onClick={async () => {
                              const copied = await copyTextSafe(src.code!);
                              if (copied) {
                                setCopiedCode(src.code!);
                                setTimeout(() => {
                                  setCopiedCode((prev) => (prev === src.code ? null : prev));
                                }, 2000);
                                showToast(`할인코드 '${src.code}'를 복사했어요`);
                              } else {
                                showToast("할인코드를 복사하지 못했어요");
                              }
                            }}
                            className="bounce-click"
                            style={{
                              display: "inline-flex",
                              alignItems: "center",
                              gap: "4px",
                              minHeight: "36px",
                              padding: "4px 8px",
                              borderRadius: "6px",
                              background: isCopied
                                ? "rgba(16, 185, 129, 0.15)"
                                : "rgba(var(--accent-rgb), 0.1)",
                              border: isCopied
                                ? "1px solid #10B981"
                                : "1px dashed rgba(var(--accent-rgb), 0.4)",
                              color: isCopied ? "#10B981" : "var(--accent)",
                              fontSize: "0.72rem",
                              fontWeight: 800,
                              cursor: "pointer",
                            }}
                            title="클릭하여 할인코드 복사"
                          >
                            {isCopied ? <Check size={11} aria-hidden="true" /> : <Copy size={11} aria-hidden="true" />}
                            <span>{isCopied ? "복사됨" : src.code}</span>
                          </button>
                        )}
                        {Array.isArray(src.sub_urls) &&
                          src.sub_urls.slice(0, 2).map((sUrl, sIdx) => (
                            <a
                              key={sIdx}
                              href={resolveOutboundUrl(sUrl)}
                              target="_blank"
                              rel="noopener noreferrer"
                              onClick={(e) => {
                                const resolvedSubUrl = resolveOutboundUrl(sUrl);
                                trackOutboundClick(
                                  resolvedSubUrl,
                                  deal.id,
                                  src.price || activePrice,
                                  optionCurrency
                                );
                                openExternalUrlInToss(resolvedSubUrl, e);
                              }}
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
                        <a
                          href={itemUrl}
                          target="_blank"
                          rel="noopener noreferrer"
                          onClick={(e) => {
                            trackOutboundClick(
                              itemUrl,
                              deal.id,
                              src.price || activePrice,
                              optionCurrency
                            );
                            if (src.code) {
                              void copyTextSafe(src.code);
                              showToast(`할인코드 '${src.code}'를 복사했어요`);
                            }
                            openExternalUrlInToss(itemUrl, e);
                          }}
                          className="bounce-click"
                          style={{
                            display: "inline-flex",
                            alignItems: "center",
                            gap: "4px",
                            minHeight: "38px",
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
                          <ExternalLink size={12} color="#fff" aria-hidden="true" />
                        </a>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </div>

        {/* 2급 영역 (초기 접힘 4개 아코디언 + 펼침 시 Lazy-fetch) */}
        <DealDetailSecondarySections
          deal={deal}
          activePrice={activePrice}
          activeCurrency={activeCurrency}
          activeOptionTitle={activeOptionTitle}
          expandedSections={expandedSections}
          onToggleSection={handleToggleSection}
          historyCount={historyCount}
          onHistoryStatus={(hasData, stats) => {
            setChartStats(hasData && stats ? stats : null);
          }}
          buyCount={buyCount}
          saveCount={saveCount}
          userVote={userVote}
          votesLoading={votesLoading}
          votesError={votesError}
          onRetryVotes={() => void loadVotesLazy(deal.id)}
          onVote={handleVote}
          comments={comments}
          commentCount={commentCount}
          commentsLoading={commentsLoading}
          commentsError={commentsError}
          onRetryComments={() => void loadCommentsLazy(deal.id)}
          commentInput={commentInput}
          onCommentInputChange={setCommentInput}
          submittingComment={submittingComment}
          onAddComment={handleAddComment}
          currentDeviceUuid={currentDeviceUuid}
          deletingCommentId={deletingCommentId}
          onDeleteComment={handleDeleteComment}
          canShowTargetAlert={canShowTargetAlert}
          currSymbol={currSymbol}
          parsedTargetInput={parsedTargetInput}
          diffAmount={diffAmount}
          savedTargetPrice={savedTargetPrice}
          savingTargetAlert={savingTargetAlert}
          presets={presets}
          formatUnit={formatUnit}
          onSelectPreset={(val) => setTargetPriceInput(String(val))}
          onTargetInputChange={(val) => setTargetPriceInput(val)}
          onSaveTargetAlert={handleSaveTargetAlert}
        />

        {/* 하단 Sticky 2버튼 액션 바 (닫기 버튼은 상단 X로 일원화하여 360px 압착 해소) */}
        <div className="deal-modal-footer">
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
                선택 옵션 할인코드:{" "}
                <strong style={{ color: "var(--accent)", letterSpacing: "0.02em" }}>
                  {selectedOption.code}
                </strong>
              </span>
              <button
                type="button"
                onClick={async () => {
                  const copied = await copyTextSafe(selectedOption.code!);
                  if (copied) {
                    showToast(`할인코드 '${selectedOption.code}'를 복사했어요`);
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
                <Copy size={12} aria-hidden="true" />
                <span>코드 복사</span>
              </button>
            </div>
          )}

          <div className="deal-modal-actions">
            <a
              className="btn-primary bounce-click deal-modal-btn-primary"
              href={resolveOutboundUrl(activeEcommerceUrl)}
              target="_blank"
              rel="noopener noreferrer"
              onClick={(e) => {
                const hasValidEcommerceLink =
                  activeEcommerceUrl &&
                  activeEcommerceUrl !== "#" &&
                  String(activeEcommerceUrl).trim() !== "";
                const targetUrl = resolveOutboundUrl(
                  hasValidEcommerceLink ? activeEcommerceUrl : deal.post_link
                );
                if (!targetUrl || targetUrl === "#") {
                  e.preventDefault();
                  showToast("열 수 있는 상품 주소가 없어요");
                  return;
                }
                trackOutboundClick(targetUrl, deal.id, activePrice, activeCurrency);
                const codeToCopy =
                  selectedOption?.code || (promoCodes.length > 0 ? promoCodes[0] : null);
                if (codeToCopy) {
                  void copyTextSafe(codeToCopy);
                  showToast(`할인코드 '${codeToCopy}'를 복사했어요`);
                } else if (!hasValidEcommerceLink) {
                  showToast("구매 링크가 없어 원본 글로 이동해요");
                }
                openExternalUrlInToss(targetUrl, e);
              }}
            >
              <span style={{ overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                {primaryCtaLabel}
              </span>
            </a>
            {deal.post_link && deal.post_link !== "#" && (
              <a
                href={resolveOutboundUrl(deal.post_link)}
                target="_blank"
                rel="noopener noreferrer"
                onClick={(e) => {
                  const resolvedPostUrl = resolveOutboundUrl(deal.post_link);
                  openExternalUrlInToss(resolvedPostUrl, e);
                }}
                className="bounce-click deal-modal-btn-secondary"
              >
                <span>원본 게시글 보기</span>
              </a>
            )}
          </div>
          <p
            style={{
              fontSize: "0.68rem",
              color: "var(--text-muted)",
              textAlign: "center",
              margin: "1px 0 0 0",
              lineHeight: 1.3,
            }}
          >
            일부 쇼핑몰 이동 링크는 제휴 마케팅의 일환으로 일정액의 수수료를 제공받을 수 있습니다.
          </p>
        </div>
      </div>
    </div>
  );
}
