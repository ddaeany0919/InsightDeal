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
  ZoomIn,
  Flag,
  Images,
  AlertTriangle,
  ChevronLeft,
  ChevronRight,
  EyeOff,
} from "lucide-react";
import type {
  Deal,
  DealSource,
  DealOptionItem,
  DealCommentItem,
  DealVoteApiResponse,
  PriceHistoryItem,
  RawDealApiResponse,
  ReportClosedResponse,
  WishlistApiItem,
} from "../lib/types";
import {
  formatPrice,
  getProxyImageUrl,
  tryApplyFallbackProxyImage,
  apiUrl,
  resolveOutboundUrl,
  trackOutboundClick,
  polishDealBodyText,
  extractPromoCodes,
  extractBodyImagesFromHtml,
  isSafeHttpUrl,
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
  computeUnitPriceBadge,
  extractPaymentConditionBadge,
  getCachedDealDetail,
  setCachedDealDetail,
  invalidateDealDetailCache,
  estimateKrwFromForeign,
  isCustomsDutyRisk,
  computeShippingTotal,
  getDealAgeDays,
  mapRawDealToDeal,
  formatRelativeTimeKo,
} from "../lib/utils";
import type { PriceTrendStats } from "./PriceTrendChart";
import DealDetailSecondarySections, {
  type AccordionSectionKey,
} from "./DealDetailSecondarySections";
import BrandImagePlaceholder from "./BrandImagePlaceholder";
import ImageLightboxModal from "./ImageLightboxModal";
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
  onDealClosed?: (dealId: number, isClosed: boolean, reportCount: number) => void;
  onDealInteractionChange?: (
    dealId: number,
    patch: { buyCount?: number; passCount?: number; appCommentCount?: number }
  ) => void;
  onDealSynced?: (dealId: number, patch: Partial<Deal>) => void;
  onNavigateDeal?: (delta: -1 | 1) => void;
  onSelectRelatedDeal?: (deal: Deal) => void;
  onHideDeal?: (dealId: number) => void;
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
  onDealClosed,
  onDealInteractionChange,
  onDealSynced,
  onNavigateDeal,
  onSelectRelatedDeal,
  onHideDeal,
}: DealDetailModalProps) {
  const [selectedOptionIndex, setSelectedOptionIndex] = useState<number>(0);
  const [chartStats, setChartStats] = useState<PriceTrendStats | null>(null);
  const [detailInitialHistory, setDetailInitialHistory] = useState<
    PriceHistoryItem[] | undefined
  >(undefined);
  const [detailInitialHistoryPeriod, setDetailInitialHistoryPeriod] = useState<
    string | undefined
  >(undefined);
  const [targetPriceInput, setTargetPriceInput] = useState<string>("");
  const [savedTargetPrice, setSavedTargetPrice] = useState<number | null>(null);
  const [savingTargetAlert, setSavingTargetAlert] = useState<boolean>(false);

  const [detailHtml, setDetailHtml] = useState<string>("");
  const [detailSources, setDetailSources] = useState<DealSource[]>([]);
  const [detailOptionsData, setDetailOptionsData] = useState<DealOptionItem[]>([]);
  const [detailShopName, setDetailShopName] = useState<string>("");
  const [detailImageUrl, setDetailImageUrl] = useState<string | null | undefined>(undefined);
  const [detailBodyImages, setDetailBodyImages] = useState<string[]>([]);
  const [similarDeals, setSimilarDeals] = useState<Deal[]>([]);
  const [isClosedState, setIsClosedState] = useState<boolean>(false);
  const [reportCount, setReportCount] = useState<number>(0);
  const [hasReportedClosed, setHasReportedClosed] = useState<boolean>(false);
  const [reportingClosed, setReportingClosed] = useState<boolean>(false);
  const [failedImageMap, setFailedImageMap] = useState<Record<string, boolean>>({});
  const [loadedImageMap, setLoadedImageMap] = useState<Record<string, boolean>>({});
  const [lightboxIndex, setLightboxIndex] = useState<number | null>(null);

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
  const openedWithPushRef = useRef<boolean>(false);
  const activeModalSessionRef = useRef<boolean>(false);
  const lightboxIndexRef = useRef<number | null>(lightboxIndex);
  const lightboxPushedRef = useRef<boolean>(false);
  const suppressNextPopRef = useRef<boolean>(false);
  const reportingClosedRef = useRef<boolean>(false);
  const dealId = deal?.id ?? null;
  const latestDealRef = useRef<Deal | null>(deal);
  const onCloseRef = useRef(onClose);
  const onDealInteractionChangeRef = useRef(onDealInteractionChange);
  const onDealSyncedRef = useRef(onDealSynced);

  useEffect(() => {
    latestDealRef.current = deal;
    onCloseRef.current = onClose;
    onDealInteractionChangeRef.current = onDealInteractionChange;
    onDealSyncedRef.current = onDealSynced;
    lightboxIndexRef.current = lightboxIndex;
  });

  const closeLightbox = useCallback(() => {
    setLightboxIndex(null);
    if (
      typeof window !== "undefined" &&
      lightboxPushedRef.current &&
      window.history.state?.insightDealLightbox
    ) {
      lightboxPushedRef.current = false;
      suppressNextPopRef.current = true;
      window.setTimeout(() => {
        suppressNextPopRef.current = false;
      }, 150);
      try {
        window.history.back();
      } catch {}
    } else {
      lightboxPushedRef.current = false;
    }
  }, []);

  const stripDealIdFromUrl = () => {
    if (typeof window === "undefined") return;
    try {
      const nextUrl = new URL(window.location.href);
      if (nextUrl.searchParams.has("dealId") || nextUrl.searchParams.has("_og")) {
        nextUrl.searchParams.delete("dealId");
        nextUrl.searchParams.delete("_og");
        const cleanHref = `${nextUrl.pathname}${nextUrl.search}${nextUrl.hash}`;
        window.history.replaceState(null, "", cleanHref || "/");
      }
    } catch {}
  };

  const handleDismiss = useCallback(() => {
    if (lightboxIndex !== null) {
      closeLightbox();
      return;
    }
    sheetOffsetYRef.current = 0;
    dragStartYRef.current = null;
    setSheetOffsetY(0);
    setIsDraggingSheet(false);
    if (
      typeof window !== "undefined" &&
      openedWithPushRef.current &&
      !closedByPopRef.current &&
      window.history.state?.insightDealModal === dealId
    ) {
      closedByPopRef.current = true;
      window.history.back();
      onCloseRef.current();
      return;
    }
    stripDealIdFromUrl();
    onCloseRef.current();
  }, [lightboxIndex, dealId, closeLightbox]);

  // WCAG 2.2 다이얼로그 포커스 이동 / 트랩 / Escape / 트리거 포커스 복귀
  useDialogA11y({
    isOpen: Boolean(deal),
    onClose: handleDismiss,
    containerRef: panelRef,
  });

  // 모달 오픈 시 단건 상세 1-RTT 조회만 실행 (deal.id 전환 시에만 초기화)
  useEffect(() => {
    const currentDeal = latestDealRef.current;
    setSelectedOptionIndex(0);
    setChartStats(null);
    setDetailInitialHistory(undefined);
    setDetailInitialHistoryPeriod(undefined);
    setDetailHtml(currentDeal?.contentHtml || "");
    setDetailSources(currentDeal?.sources || []);
    setDetailOptionsData(currentDeal?.options_data || []);
    setDetailShopName(currentDeal?.shop_name || "");
    setDetailImageUrl(undefined);
    setDetailBodyImages(currentDeal?.bodyImages || []);
    setSimilarDeals([]);
    setIsClosedState(Boolean(currentDeal?.isClosed));
    setReportCount(currentDeal?.reportCount ?? 0);
    reportingClosedRef.current = false;
    setReportingClosed(false);
    setFailedImageMap({});
    setLoadedImageMap({});
    setLightboxIndex(null);
    setBuyCount(currentDeal?.buyCount ?? 0);
    setSaveCount(currentDeal?.passCount ?? 0);
    setUserVote(currentDeal?.userVote ?? null);
    setCommentCount(currentDeal?.appCommentCount ?? 0);
    setHistoryCount(currentDeal?.historyCount ?? 0);
    setIsSearchFallback(Boolean(currentDeal?.isSearchFallback));
    setIsFreeDeal(Boolean(currentDeal?.isFree));
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

    if (dealId === null || !currentDeal) {
      setHasReportedClosed(false);
      return;
    }

    try {
      const reportedRaw = localStorage.getItem("insightdeal_reported_closed_ids");
      const reportedIds = reportedRaw ? JSON.parse(reportedRaw) : [];
      setHasReportedClosed(
        Array.isArray(reportedIds) && reportedIds.includes(dealId)
      );
    } catch {
      setHasReportedClosed(false);
    }

    const uid = getOrCreateWebDeviceUuid();
    setCurrentDeviceUuid(uid);

    logTossScreen("deal_detail_view", {
      deal_id: dealId,
      category: currentDeal.category || "기타",
    });

    const controller = new AbortController();
    const { signal } = controller;

    const initCurr = (currentDeal.currency || "KRW").toUpperCase();
    const isSubunitInit = initCurr === "USD" || initCurr === "EUR" || initCurr === "CNY";
    const rawPriceNum = parseInt(String(currentDeal.price || "").replace(/[^0-9]/g, ""), 10) || 0;
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
      if (targetMap && targetMap[dealId]) {
        setSavedTargetPrice(Number(targetMap[dealId]));
        setTargetPriceInput(String(targetMap[dealId]));
      } else {
        setSavedTargetPrice(null);
        setTargetPriceInput(defaultTarget > 0 ? String(defaultTarget) : "");
      }
    } catch {
      setSavedTargetPrice(null);
      setTargetPriceInput(defaultTarget > 0 ? String(defaultTarget) : "");
    }

    const applyDetailPayload = (d: RawDealApiResponse) => {
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

      const nextBuy = d.buy_count ?? d.buyCount;
      if (nextBuy !== undefined) setBuyCount(Number(nextBuy) || 0);

      const nextPass =
        d.pass_count ?? d.passCount ?? d.save_count ?? d.saveCount;
      if (nextPass !== undefined) setSaveCount(Number(nextPass) || 0);

      const nextVote = d.user_vote ?? d.userVote;
      if (nextVote !== undefined) setUserVote(nextVote ?? null);

      const nextCommentCnt =
        d.app_comment_count ?? d.appCommentCount ?? d.comment_count ?? d.commentCount;
      if (nextCommentCnt !== undefined) setCommentCount(Number(nextCommentCnt) || 0);

      if (
        nextBuy !== undefined ||
        nextPass !== undefined ||
        nextCommentCnt !== undefined
      ) {
        onDealInteractionChangeRef.current?.(dealId, {
          ...(nextBuy !== undefined ? { buyCount: Number(nextBuy) || 0 } : {}),
          ...(nextPass !== undefined ? { passCount: Number(nextPass) || 0 } : {}),
          ...(nextCommentCnt !== undefined
            ? { appCommentCount: Number(nextCommentCnt) || 0 }
            : {}),
        });
      }

      const nextHistoryCnt = d.history_count ?? d.historyCount;
      if (nextHistoryCnt !== undefined) setHistoryCount(Number(nextHistoryCnt) || 0);

      const nextInitialHistory = d.initial_history ?? d.initialHistory;
      if (Array.isArray(nextInitialHistory)) {
        setDetailInitialHistory(nextInitialHistory);
        if (nextHistoryCnt === undefined) {
          setHistoryCount(nextInitialHistory.length);
        }
      }
      const nextInitialHistoryPeriod =
        d.initial_history_period ?? d.initialHistoryPeriod;
      if (
        typeof nextInitialHistoryPeriod === "string" &&
        nextInitialHistoryPeriod.trim()
      ) {
        setDetailInitialHistoryPeriod(nextInitialHistoryPeriod.trim());
      }

      const nextFallback = d.is_search_fallback ?? d.isSearchFallback;
      if (nextFallback !== undefined) setIsSearchFallback(Boolean(nextFallback));

      const nextFree = d.is_free ?? d.isFree;
      if (nextFree !== undefined) setIsFreeDeal(Boolean(nextFree));

      const nextBodyImgs = d.body_images ?? d.bodyImages;
      if (Array.isArray(nextBodyImgs)) {
        setDetailBodyImages(nextBodyImgs.filter((img) => isSafeHttpUrl(img)));
      }

      const nextClosed = d.is_closed ?? d.isClosed;
      if (nextClosed !== undefined) setIsClosedState(Boolean(nextClosed));

      const nextReportCnt = d.report_count ?? d.reportCount;
      if (nextReportCnt !== undefined) setReportCount(Number(nextReportCnt) || 0);

      const rawSimilar = d.similar_deals ?? d.similarDeals;
      if (Array.isArray(rawSimilar)) {
        setSimilarDeals(rawSimilar.slice(0, 4).map((item) => mapRawDealToDeal(item)));
      }

      const rawDetailImg = d.image_url !== undefined ? d.image_url : d.imageUrl;
      const syncedPatch: Partial<Deal> = {};
      if (rawDetailImg !== undefined) {
        const normalizedImg =
          typeof rawDetailImg === "string" && rawDetailImg.trim() !== ""
            ? rawDetailImg.trim()
            : null;
        setDetailImageUrl(normalizedImg);
        syncedPatch.image_url = normalizedImg;
      }
      if (typeof d.title === "string" && d.title.trim() !== "") {
        syncedPatch.title = decodeHtmlEntities(d.title);
      }
      if (d.price !== undefined && d.price !== null && String(d.price).trim() !== "") {
        syncedPatch.price = String(d.price);
      }
      if (typeof d.currency === "string" && d.currency.trim() !== "") {
        syncedPatch.currency = d.currency.trim();
      }
      if (nextFree !== undefined) {
        const freeFlag = Boolean(nextFree);
        syncedPatch.isFree = freeFlag;
        if (freeFlag) {
          syncedPatch.price = "0";
        }
      }
      if (nextClosed !== undefined) {
        syncedPatch.isClosed = Boolean(nextClosed);
      }
      if (nextReportCnt !== undefined) {
        syncedPatch.reportCount = Number(nextReportCnt) || 0;
      }
      if (d.shop_name !== undefined || d.shopName !== undefined) {
        const resolvedShop = resolveDealShopName({
          shop_name: d.shop_name ?? d.shopName,
          title: syncedPatch.title ?? currentDeal.title,
          ecommerce_url: d.ecommerce_url ?? currentDeal.ecommerce_url,
        });
        if (resolvedShop) {
          syncedPatch.shop_name = resolvedShop;
        }
      }
      if (typeof d.ecommerce_url === "string" && d.ecommerce_url.trim() !== "") {
        syncedPatch.ecommerce_url = d.ecommerce_url.trim();
      }
      if (typeof d.category === "string" && d.category.trim() !== "") {
        syncedPatch.category = d.category.includes("해외핫딜")
          ? "해외직구"
          : d.category.trim();
      }
      if (Object.keys(syncedPatch).length > 0) {
        onDealSyncedRef.current?.(dealId, syncedPatch);
      }
    };

    const cachedDetail = getCachedDealDetail(dealId);
    if (cachedDetail) {
      applyDetailPayload(cachedDetail.data);
      if (Date.now() - cachedDetail.cachedAt < 15_000) {
        return () => {
          controller.abort();
        };
      }
    }

    const storedToken = getStoredWebDeviceToken();
    const detailHeaders: Record<string, string> = {};
    if (storedToken) {
      detailHeaders["X-Device-Token"] = storedToken;
    }

    // 1-RTT 상세 조회 (본문 + 옵션 + 1-RTT 요약 필 통합 수신)
    fetch(
      apiUrl(`/api/community/deals/${dealId}?user_id=${encodeURIComponent(uid)}`),
      { signal, headers: detailHeaders }
    )
      .then((r) => (r.ok ? (r.json() as Promise<RawDealApiResponse>) : null))
      .then((d) => {
        if (signal.aborted || !d) return;
        setCachedDealDetail(dealId, d);
        applyDetailPayload(d);
      })
      .catch(() => {});

    return () => {
      controller.abort();
    };
  }, [dealId]);

  const propIsClosed = Boolean(deal?.isClosed);
  const propReportCount = deal?.reportCount;

  // 부모에서 { ...prev, isClosed, reportCount } 갱신 시 모달 컨텍스트를 보존하고 종료/신고 수만 경량 동기화
  useEffect(() => {
    if (dealId === null) return;
    if (propIsClosed) {
      setIsClosedState(true);
    }
    if (typeof propReportCount === "number" && propReportCount > 0) {
      setReportCount((prev) => Math.max(prev, propReportCount));
    }
  }, [dealId, propIsClosed, propReportCount]);

  useEffect(() => {
    if (dealId === null) {
      openedWithPushRef.current = false;
      closedByPopRef.current = false;
      activeModalSessionRef.current = false;
      return;
    }
    if (typeof window === "undefined") return;

    closedByPopRef.current = false;
    const isSwitchingWithinModal = activeModalSessionRef.current;
    activeModalSessionRef.current = true;
    const prevOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";

    try {
      const currUrl = new URL(window.location.href);
      const urlHasSameDeal = currUrl.searchParams.get("dealId") === String(dealId);
      if (isSwitchingWithinModal) {
        // 모달이 이미 열린 상태에서 내부 탐색으로 dealId가 전환될 때는 pushState 중복 적재 대신 replaceState로 교체
        currUrl.searchParams.set("dealId", String(dealId));
        currUrl.searchParams.delete("_og");
        const nextHref = `${currUrl.pathname}${currUrl.search}${currUrl.hash}`;
        window.history.replaceState(
          openedWithPushRef.current
            ? { insightDealModal: dealId }
            : window.history.state,
          "",
          nextHref
        );
      } else {
        openedWithPushRef.current = false;
        if (!urlHasSameDeal && window.history.state?.insightDealModal !== dealId) {
          currUrl.searchParams.set("dealId", String(dealId));
          currUrl.searchParams.delete("_og");
          window.history.pushState(
            { insightDealModal: dealId },
            "",
            `${currUrl.pathname}${currUrl.search}${currUrl.hash}`
          );
          openedWithPushRef.current = true;
        }
      }
    } catch {
      if (isSwitchingWithinModal) {
        if (openedWithPushRef.current) {
          try {
            window.history.replaceState({ insightDealModal: dealId }, "");
          } catch {}
        }
      } else {
        openedWithPushRef.current = false;
        if (!window.history.state?.insightDealModal) {
          try {
            window.history.pushState({ insightDealModal: dealId }, "");
            openedWithPushRef.current = true;
          } catch {}
        }
      }
    }

    const handlePopState = () => {
      if (suppressNextPopRef.current) {
        suppressNextPopRef.current = false;
        return;
      }
      if (lightboxIndexRef.current !== null) {
        lightboxPushedRef.current = false;
        setLightboxIndex(null);
        return;
      }
      if (closedByPopRef.current) return;
      closedByPopRef.current = true;
      sheetOffsetYRef.current = 0;
      onCloseRef.current();
    };

    window.addEventListener("popstate", handlePopState);
    return () => {
      document.body.style.overflow = prevOverflow;
      window.removeEventListener("popstate", handlePopState);
    };
  }, [dealId]);

  useEffect(() => {
    if (dealId === null || lightboxIndex !== null || !onNavigateDeal || typeof window === "undefined") {
      return;
    }
    const handleArrowNav = (e: KeyboardEvent) => {
      if (e.key !== "ArrowLeft" && e.key !== "ArrowRight") return;
      const target = e.target as HTMLElement | null;
      if (
        target &&
        (target.tagName === "INPUT" ||
          target.tagName === "TEXTAREA" ||
          target.tagName === "SELECT" ||
          target.isContentEditable)
      ) {
        return;
      }
      e.preventDefault();
      onNavigateDeal(e.key === "ArrowLeft" ? -1 : 1);
    };
    window.addEventListener("keydown", handleArrowNav);
    return () => window.removeEventListener("keydown", handleArrowNav);
  }, [dealId, lightboxIndex, onNavigateDeal]);

  useEffect(() => {
    if (typeof window === "undefined") return;
    if (dealId !== null && lightboxIndex !== null) {
      if (!lightboxPushedRef.current && !window.history.state?.insightDealLightbox) {
        try {
          window.history.pushState(
            { insightDealModal: dealId, insightDealLightbox: true },
            "",
            window.location.href
          );
          lightboxPushedRef.current = true;
        } catch {}
      }
    } else if (lightboxIndex === null || dealId === null) {
      lightboxPushedRef.current = false;
    }
  }, [dealId, lightboxIndex]);

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
      const nextBuy = Number(v.buy_count ?? v.buyCount ?? 0);
      const nextPass = Number(
        v.pass_count ?? v.passCount ?? v.save_count ?? v.saveCount ?? 0
      );
      setBuyCount(nextBuy);
      setSaveCount(nextPass);
      setUserVote((v.user_vote ?? v.userVote ?? null) as string | null);
      setVotesLoaded(true);
      onDealInteractionChangeRef.current?.(targetDealId, {
        buyCount: nextBuy,
        passCount: nextPass,
      });
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
        onDealInteractionChangeRef.current?.(targetDealId, {
          appCommentCount: normalized.length,
        });
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

  const cleanBodyText = polishDealBodyText(detailHtml, deal.title);
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
  const fallbackDealImageUrl =
    detailImageUrl !== undefined ? detailImageUrl : deal.image_url;
  const activeImageUrl =
    selectedOption?.image_url && selectedOption.image_url.trim() !== ""
      ? selectedOption.image_url
      : fallbackDealImageUrl;

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

  const fallbackBodyImages =
    detailBodyImages.length > 0
      ? detailBodyImages
      : extractBodyImagesFromHtml(detailHtml);
  const galleryImages = Array.from(
    new Set(
      [activeImageUrl, ...fallbackBodyImages].filter(
        (img): img is string => Boolean(img && isSafeHttpUrl(img))
      )
    )
  );

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

  const validInitialPrices =
    selectedOptionIndex === 0 && Array.isArray(detailInitialHistory)
      ? detailInitialHistory
          .map((item) => Number(item?.price))
          .filter((p) => Number.isFinite(p) && p > 0)
          .map((p) => Math.round(p))
      : [];
  const initialHistoryStats: PriceTrendStats | null =
    validInitialPrices.length >= 2
      ? {
          minPrice: Math.min(...validInitialPrices),
          avgPrice: Math.round(
            validInitialPrices.reduce((a, b) => a + b, 0) / validInitialPrices.length
          ),
          maxPrice: Math.max(...validInitialPrices),
          latestPrice: validInitialPrices[validInitialPrices.length - 1],
        }
      : null;
  const activeSummaryStats: PriceTrendStats | null =
    chartStats ?? (selectedOptionIndex === 0 ? initialHistoryStats : null);

  const presets: { label: string; value: number }[] = [];
  if (canShowTargetAlert) {
    const statMin = activeSummaryStats
      ? isSubunitCurr
        ? Math.round(activeSummaryStats.minPrice / 100)
        : activeSummaryStats.minPrice
      : 0;
    const statAvg = activeSummaryStats
      ? isSubunitCurr
        ? Math.round(activeSummaryStats.avgPrice / 100)
        : activeSummaryStats.avgPrice
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
      const nextBuy = Number(data.buy_count ?? data.buyCount ?? 0);
      const nextPass = Number(
        data.pass_count ?? data.passCount ?? data.save_count ?? data.saveCount ?? 0
      );
      setBuyCount(nextBuy);
      setSaveCount(nextPass);
      setUserVote((data.user_vote ?? data.userVote ?? voteType) as string | null);
      setVotesError(null);
      setVotesLoaded(true);
      invalidateDealDetailCache(deal.id);
      onDealInteractionChangeRef.current?.(deal.id, {
        buyCount: nextBuy,
        passCount: nextPass,
      });
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
      const nextComments = [normalized, ...comments];
      const nextCommentCount = commentsLoaded
        ? nextComments.length
        : Math.max(commentCount + 1, nextComments.length);
      setComments(nextComments);
      setCommentCount(nextCommentCount);
      setCommentsError(null);
      setCommentsLoaded(true);
      setCommentInput("");
      invalidateDealDetailCache(deal.id);
      onDealInteractionChangeRef.current?.(deal.id, {
        appCommentCount: nextCommentCount,
      });
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
      const nextComments = comments.filter((c) => c.id !== commentId);
      const nextCommentCount = commentsLoaded
        ? nextComments.length
        : Math.max(0, commentCount - 1);
      setComments(nextComments);
      setCommentCount(nextCommentCount);
      invalidateDealDetailCache(deal.id);
      onDealInteractionChangeRef.current?.(deal.id, {
        appCommentCount: nextCommentCount,
      });
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
      } catch (err) {
        if (err instanceof Error && err.name === "AbortError") {
          return;
        }
      }
    }
    const copied = await copyTextSafe(`${shareText}\n${shareUrl}`);
    if (copied) {
      showToast("상품 링크를 복사했어요");
    } else {
      showToast("상품 링크를 복사하지 못했어요");
    }
  };

  const handleReportClosed = async () => {
    if (reportingClosedRef.current || reportingClosed) return;
    if (hasReportedClosed) {
      showToast("이미 품절 제보를 완료했어요");
      return;
    }
    reportingClosedRef.current = true;
    setReportingClosed(true);
    try {
      await ensureWebDeviceAuth();
      const res = await authFetch(`/api/community/deals/${deal.id}/report-closed`, {
        method: "POST",
      });
      const data = (await res.json().catch(() => null)) as ReportClosedResponse | null;

      if (res.status === 409) {
        setHasReportedClosed(true);
        try {
          const raw = localStorage.getItem("insightdeal_reported_closed_ids");
          const prev = raw ? JSON.parse(raw) : [];
          const next = Array.isArray(prev)
            ? Array.from(new Set([...prev, deal.id]))
            : [deal.id];
          localStorage.setItem("insightdeal_reported_closed_ids", JSON.stringify(next));
        } catch {}
        showToast("이미 이 기기에서 품절 제보를 접수했어요");
        return;
      }

      if (res.status === 429) {
        showToast("제보 요청이 너무 많아요. 잠시 후 다시 시도해 주세요");
        return;
      }

      if (!res.ok) {
        showToast(data?.detail || "품절 제보를 접수하지 못했어요. 잠시 후 다시 시도해 주세요");
        return;
      }

      const nextCount = Number(data?.report_count ?? data?.reportCount ?? reportCount + 1);
      const nextClosed = Boolean(data?.is_closed ?? data?.isClosed ?? isClosedState);
      const threshold = Number(data?.threshold ?? 3);

      setReportCount(nextCount);
      setHasReportedClosed(true);
      if (nextClosed) {
        setIsClosedState(true);
      }

      try {
        const raw = localStorage.getItem("insightdeal_reported_closed_ids");
        const prev = raw ? JSON.parse(raw) : [];
        const next = Array.isArray(prev)
          ? Array.from(new Set([...prev, deal.id]))
          : [deal.id];
        localStorage.setItem("insightdeal_reported_closed_ids", JSON.stringify(next));
      } catch {}

      onDealClosed?.(deal.id, nextClosed, nextCount);
      showToast(
        nextClosed
          ? `품절 제보 ${nextCount}건이 누적되어 종료 처리되었어요`
          : `품절 제보를 접수했어요 (${nextCount}/${threshold}명 누적 시 자동 종료)`
      );
    } catch {
      showToast("네트워크 오류로 품절 제보에 실패했어요");
    } finally {
      reportingClosedRef.current = false;
      setReportingClosed(false);
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

  const formatSummaryPrice = (v: number) =>
    isForeignCurr
      ? formatPrice(Math.round(v), activeCurrency)
      : `${Math.round(v).toLocaleString()}원`;

  const comparePriceForSummary = (() => {
    if (isSubunitCurr && typeof activePrice === "string" && activePrice.includes(".")) {
      const parsedFloat = parseFloat(activePrice.replace(/[^0-9.]/g, ""));
      if (Number.isFinite(parsedFloat) && parsedFloat > 0) {
        return Math.round(parsedFloat * 100);
      }
    }
    if (rawPriceNum > 0) return rawPriceNum;
    return activeSummaryStats?.latestPrice ?? 0;
  })();

  const unitFromOption = activeOptionTitle
    ? computeUnitPriceBadge(activeOptionTitle, activePrice, activeCurrency, deal.category)
    : null;
  const unitFromDealTitle =
    !unitFromOption &&
    (!selectedOption ||
      Math.round(Number(activePrice || 0)) === Math.round(Number(deal.price || 0)) ||
      /^(?:로켓배송|무료배송|기본|선택\s*\d+|옵션\s*\d+)$/i.test(activeOptionTitle.trim()))
      ? computeUnitPriceBadge(deal.title, activePrice, activeCurrency, deal.category)
      : null;

  const detailUnitPriceBadge =
    !isClosedState &&
    !showZeroWonPrice &&
    !(deal.has_options && !selectedOption)
      ? (unitFromOption ?? unitFromDealTitle)
      : null;
  const rawDetailPaymentCondBadge =
    !isClosedState && !showZeroWonPrice
      ? extractPaymentConditionBadge(
          activeOptionTitle ? `${deal.title} ${activeOptionTitle}` : deal.title,
          deal.shipping_fee
        )
      : null;
  const detailPaymentCondBadge =
    rawDetailPaymentCondBadge &&
    !((rawDetailPaymentCondBadge === "와우회원 조건" && (displayShipping || "").includes("와우")) ||
      (rawDetailPaymentCondBadge === "네멤 조건" && (displayShipping || "").includes("네멤")))
      ? rawDetailPaymentCondBadge
      : null;

  const summaryInsight = (() => {
    if (!activeSummaryStats || showZeroWonPrice || comparePriceForSummary <= 0) {
      return null;
    }
    const diffPct =
      activeSummaryStats.avgPrice > 0
        ? Math.round(
            ((comparePriceForSummary - activeSummaryStats.avgPrice) /
              activeSummaryStats.avgPrice) *
              100
          )
        : 0;
    const isHistoricLow =
      comparePriceForSummary <= activeSummaryStats.minPrice &&
      activeSummaryStats.maxPrice > activeSummaryStats.minPrice;
    const headline = isHistoricLow
      ? diffPct < 0
        ? `역대 최저가 구간 · 평균 대비 ${-diffPct}% 저렴`
        : "역대 최저가 구간"
      : diffPct < 0
        ? `평균 대비 ${-diffPct}% 저렴`
        : diffPct > 0
          ? `평균 대비 +${diffPct}%`
          : "평균가 수준";

    return {
      headline,
      isPositive: isHistoricLow || diffPct < 0,
      isHigh: !isHistoricLow && diffPct > 0,
      minText: formatSummaryPrice(activeSummaryStats.minPrice),
      avgText: formatSummaryPrice(activeSummaryStats.avgPrice),
      maxText: formatSummaryPrice(activeSummaryStats.maxPrice),
    };
  })();

  const krwEstimate = !showZeroWonPrice
    ? estimateKrwFromForeign(activePrice, activeCurrency)
    : null;
  const dutyRisk = !showZeroWonPrice
    ? isCustomsDutyRisk(activePrice, activeCurrency, shopBadge, displayTitle)
    : { atRisk: false, thresholdUsd: 150, usdApprox: 0 };
  const shippingTotalInfo =
    !showZeroWonPrice && !isClosedState
      ? computeShippingTotal(activePrice, activeCurrency, displayShipping)
      : null;
  const dealAgeDays = getDealAgeDays(deal.originalDate);

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
              <div
                key={activeImageUrl || "placeholder"}
                className="deal-modal-thumb"
                style={{
                  border: selectedOption
                    ? "1.5px solid var(--accent)"
                    : "1px solid var(--card-border)",
                }}
              >
                {activeImageUrl && !failedImageMap[activeImageUrl] ? (
                  <button
                    type="button"
                    aria-label="상품 이미지 확대 보기"
                    onClick={() => {
                      const idx = galleryImages.indexOf(activeImageUrl);
                      setLightboxIndex(idx >= 0 ? idx : 0);
                    }}
                    style={{
                      width: "100%",
                      height: "100%",
                      minWidth: "44px",
                      minHeight: "44px",
                      padding: 0,
                      position: "relative",
                      display: "block",
                      overflow: "hidden",
                      cursor: "zoom-in",
                    }}
                  >
                    {!loadedImageMap[activeImageUrl] && (
                      <div
                        className="skeleton-shimmer"
                        aria-hidden="true"
                        style={{ position: "absolute", inset: 0, zIndex: 0 }}
                      />
                    )}
                    {/* eslint-disable-next-line @next/next/no-img-element */}
                    <img
                      ref={(imgEl) => {
                        if (imgEl && imgEl.complete && imgEl.naturalWidth > 0) {
                          setLoadedImageMap((prev) =>
                            prev[activeImageUrl]
                              ? prev
                              : { ...prev, [activeImageUrl]: true }
                          );
                        }
                      }}
                      src={getProxyImageUrl(activeImageUrl, { width: 400 })}
                      alt={activeOptionTitle || deal.title}
                      style={{
                        position: "relative",
                        zIndex: 0,
                        width: "100%",
                        height: "100%",
                        objectFit: "cover",
                        filter: isClosedState ? "grayscale(0.85)" : undefined,
                        opacity: isClosedState ? 0.72 : 1,
                      }}
                      referrerPolicy="no-referrer"
                      onLoad={() =>
                        setLoadedImageMap((prev) => ({ ...prev, [activeImageUrl]: true }))
                      }
                      onError={(e) => {
                        const primarySrc = getProxyImageUrl(activeImageUrl, { width: 400 });
                        if (tryApplyFallbackProxyImage(e.currentTarget, activeImageUrl, primarySrc)) {
                          return;
                        }
                        setFailedImageMap((prev) => ({ ...prev, [activeImageUrl]: true }));
                      }}
                    />
                    <span
                      aria-hidden="true"
                      style={{
                        position: "absolute",
                        top: "4px",
                        left: "4px",
                        width: "20px",
                        height: "20px",
                        borderRadius: "6px",
                        background: "rgba(15, 23, 42, 0.72)",
                        color: "#fff",
                        display: "inline-flex",
                        alignItems: "center",
                        justifyContent: "center",
                      }}
                    >
                      <ZoomIn size={11} />
                    </span>
                  </button>
                ) : (
                  <BrandImagePlaceholder compact />
                )}
                {isClosedState && (
                  <span
                    style={{
                      position: "absolute",
                      top: "4px",
                      right: "4px",
                      background: "var(--danger-bg)",
                      color: "var(--danger-text)",
                      border: "1px solid var(--danger-border)",
                      fontSize: "0.62rem",
                      fontWeight: 800,
                      padding: "1px 5px",
                      borderRadius: "4px",
                      pointerEvents: "none",
                    }}
                  >
                    종료
                  </span>
                )}
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
                        pointerEvents: "none",
                      }}
                    >
                      #{selectedOptionIndex + 1}
                    </span>
                  )}
              </div>
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
                  {isClosedState && <span className="badge badge-danger">품절/종료</span>}
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
            <div className="deal-modal-header-actions">
              {onNavigateDeal && (
                <>
                  <button
                    type="button"
                    onClick={() => onNavigateDeal(-1)}
                    title="이전 핫딜 (←)"
                    aria-label="이전 핫딜"
                    className="deal-modal-icon-btn bounce-click"
                  >
                    <ChevronLeft size={16} aria-hidden="true" />
                  </button>
                  <button
                    type="button"
                    onClick={() => onNavigateDeal(1)}
                    title="다음 핫딜 (→)"
                    aria-label="다음 핫딜"
                    className="deal-modal-icon-btn bounce-click"
                  >
                    <ChevronRight size={16} aria-hidden="true" />
                  </button>
                </>
              )}
              <button
                type="button"
                onClick={async () => {
                  const ok = await copyTextSafe(displayTitle);
                  showToast(ok ? "상품명을 복사했어요" : "상품명을 복사하지 못했어요");
                }}
                title="상품명 복사"
                aria-label="상품명 복사"
                className="deal-modal-icon-btn bounce-click"
              >
                <Copy size={14} aria-hidden="true" />
              </button>
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

        {dealAgeDays >= 3 && (
          <div
            role="note"
            style={{
              display: "flex",
              alignItems: "center",
              gap: "8px",
              padding: "9px 12px",
              borderRadius: "10px",
              background: "var(--warning-bg)",
              border: "1px solid var(--warning-border)",
              color: "var(--warning-text)",
              fontSize: "0.78rem",
              fontWeight: 700,
            }}
          >
            <AlertTriangle size={14} aria-hidden="true" style={{ flexShrink: 0 }} />
            <span>
              등록된 지 {dealAgeDays}일 지난 핫딜입니다. 품절·가격 변동 여부를 확인하세요.
            </span>
          </div>
        )}

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
                      background: "var(--accent-soft-bg)",
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
                  display: "flex",
                  alignItems: "baseline",
                  gap: "8px",
                  flexWrap: "wrap",
                }}
              >
                <span>
                  {showZeroWonPrice
                    ? "0원 (무료)"
                    : formatPrice(activePrice, activeCurrency, activeOptionTitle || deal.title)}
                </span>
                {krwEstimate && (
                  <span style={{ fontSize: "0.86rem", fontWeight: 700, color: "var(--text-sub)" }}>
                    ({krwEstimate.formatted})
                  </span>
                )}
              </div>
              {displayShipping && (
                <div style={{ fontSize: "0.8rem", color: "var(--accent)", marginTop: "2px", fontWeight: 700 }}>
                  배송비: {displayShipping}
                  {shippingTotalInfo && (
                    <span style={{ color: "var(--text-sub)", marginLeft: "6px" }}>
                      ({shippingTotalInfo.label})
                    </span>
                  )}
                </div>
              )}
              {dutyRisk.atRisk && (
                <div
                  role="note"
                  style={{
                    marginTop: "6px",
                    display: "inline-flex",
                    alignItems: "center",
                    gap: "5px",
                    padding: "4px 8px",
                    borderRadius: "6px",
                    background: "var(--warning-bg)",
                    border: "1px solid var(--warning-border)",
                    color: "var(--warning-text)",
                    fontSize: "0.74rem",
                    fontWeight: 700,
                  }}
                >
                  <AlertTriangle size={12} aria-hidden="true" />
                  <span>
                    미화 약 ${dutyRisk.usdApprox} · 면세 한도(${dutyRisk.thresholdUsd}) 초과 관부가세 주의
                  </span>
                </div>
              )}
            </div>

            <div
              style={{
                display: "flex",
                alignItems: "center",
                gap: "6px",
                flexWrap: "wrap",
                justifyContent: "flex-end",
              }}
            >
              {!isClosedState && (
                <button
                  type="button"
                  onClick={() => void handleReportClosed()}
                  disabled={reportingClosed}
                  aria-pressed={hasReportedClosed}
                  className="bounce-click"
                  style={{
                    minHeight: "44px",
                    minWidth: "44px",
                    padding: "8px 12px",
                    borderRadius: "10px",
                    display: "inline-flex",
                    alignItems: "center",
                    gap: "5px",
                    fontSize: "0.78rem",
                    fontWeight: 700,
                    cursor: reportingClosed ? "not-allowed" : "pointer",
                    background: hasReportedClosed ? "var(--danger-bg)" : "var(--card-bg)",
                    color: hasReportedClosed ? "var(--danger-text)" : "var(--text-sub)",
                    border: hasReportedClosed
                      ? "1px solid var(--danger-border)"
                      : "1px solid var(--card-border)",
                  }}
                >
                  <Flag size={13} aria-hidden="true" />
                  <span>
                    {hasReportedClosed
                      ? `품절 제보 완료${reportCount > 0 ? ` (${reportCount})` : ""}`
                      : `품절 제보${reportCount > 0 ? ` (${reportCount})` : ""}`}
                  </span>
                </button>
              )}
              {onHideDeal && (
                <button
                  type="button"
                  onClick={() => {
                    onHideDeal(deal.id);
                    handleDismiss();
                  }}
                  aria-label="이 핫딜 숨기기"
                  className="bounce-click"
                  style={{
                    minHeight: "44px",
                    minWidth: "44px",
                    padding: "8px 12px",
                    borderRadius: "10px",
                    display: "inline-flex",
                    alignItems: "center",
                    gap: "5px",
                    fontSize: "0.78rem",
                    fontWeight: 700,
                    cursor: "pointer",
                    background: "var(--card-bg)",
                    color: "var(--text-sub)",
                    border: "1px solid var(--card-border)",
                  }}
                >
                  <EyeOff size={13} aria-hidden="true" />
                  <span>이 핫딜 숨기기</span>
                </button>
              )}
            </div>
          </div>

          {(detailUnitPriceBadge || detailPaymentCondBadge) && (
            <div
              className="deal-detail-unit-price-row"
              style={{
                display: "flex",
                flexWrap: "wrap",
                alignItems: "center",
                gap: "6px 8px",
                minWidth: 0,
              }}
            >
              {detailUnitPriceBadge && (
                <>
                  <span className="badge deal-unit-price-badge">
                    {detailUnitPriceBadge.label}
                  </span>
                  {detailUnitPriceBadge.detail && (
                    <span
                      style={{
                        fontSize: "0.78rem",
                        color: "var(--text-muted)",
                        fontWeight: 600,
                        fontVariantNumeric: "tabular-nums",
                      }}
                    >
                      {detailUnitPriceBadge.detail}
                    </span>
                  )}
                </>
              )}
              {detailPaymentCondBadge && (
                <span className="badge deal-payment-cond-badge">
                  {detailPaymentCondBadge}
                </span>
              )}
            </div>
          )}

          {summaryInsight && (
            <div
              className="deal-price-insight-bar"
              aria-label="0클릭 시세 요약"
              style={{
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
                flexWrap: "wrap",
                gap: "6px 10px",
                padding: "8px 11px",
                borderRadius: "10px",
                background: summaryInsight.isPositive
                  ? "var(--success-bg)"
                  : summaryInsight.isHigh
                    ? "var(--warning-bg)"
                    : "var(--card-bg)",
                border: summaryInsight.isPositive
                  ? "1px solid var(--success-border)"
                  : summaryInsight.isHigh
                    ? "1px solid var(--warning-border)"
                    : "1px solid var(--card-border)",
                fontSize: "0.78rem",
              }}
            >
              <span
                style={{
                  fontWeight: 800,
                  color: summaryInsight.isPositive
                    ? "var(--success-text)"
                    : summaryInsight.isHigh
                      ? "var(--warning-text)"
                      : "var(--accent)",
                }}
              >
                {summaryInsight.headline}
              </span>
              <span
                style={{
                  color: "var(--text-sub)",
                  fontWeight: 600,
                  fontVariantNumeric: "tabular-nums",
                }}
              >
                최저 {summaryInsight.minText} · 평균 {summaryInsight.avgText} · 최고{" "}
                {summaryInsight.maxText}
              </span>
            </div>
          )}

          {isSearchFallback && (
            <div
              role="note"
              style={{
                display: "flex",
                alignItems: "center",
                gap: "8px",
                padding: "9px 12px",
                borderRadius: "10px",
                background: "var(--warning-bg)",
                border: "1px solid var(--warning-border)",
                color: "var(--warning-text)",
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
            aria-expanded={expandedSections.chart}
            className={`deal-summary-pill ${expandedSections.chart ? "active" : ""}`}
            onClick={() => handleJumpToSection("chart")}
          >
            <BarChart3 size={14} aria-hidden="true" />
            <span>
              {historyCount >= 2
                ? `가격 추이 (${historyCount}건)`
                : historyCount === 1
                  ? "가격 추이 (1건·수집중)"
                  : "가격 추이 (신규)"}
            </span>
          </button>
          <button
            type="button"
            aria-expanded={expandedSections.vote}
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
            aria-expanded={expandedSections.comments}
            className={`deal-summary-pill ${expandedSections.comments ? "active" : ""}`}
            onClick={() => handleJumpToSection("comments")}
          >
            <MessageSquare size={14} aria-hidden="true" />
            <span>의견 {Math.max(commentCount, comments.length)}개</span>
          </button>
          {canShowTargetAlert && (
            <button
              type="button"
              aria-expanded={expandedSections.target}
              className={`deal-summary-pill ${expandedSections.target ? "active" : ""}`}
              onClick={() => handleJumpToSection("target")}
            >
              <Bell size={14} aria-hidden="true" />
              <span>목표가 알림</span>
            </button>
          )}
        </div>

        {/* 2급 영역 (Summary Pill 직후 가격 추이 아코디언 전진 배치 -> bodySlot 본문 -> 투표/의견/알림 아코디언) */}
        <DealDetailSecondarySections
          deal={deal}
          activePrice={activePrice}
          activeCurrency={activeCurrency}
          activeOptionTitle={activeOptionTitle}
          initialHistory={
            selectedOptionIndex === 0 ? detailInitialHistory : undefined
          }
          initialHistoryPeriod={
            selectedOptionIndex === 0 ? detailInitialHistoryPeriod : undefined
          }
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
          bodySlot={
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
                    minHeight: "44px",
                    minWidth: "44px",
                    padding: "8px 12px",
                    borderRadius: "8px",
                    background: "var(--accent-soft-bg)",
                    border: "1px dashed var(--accent-soft-border)",
                    color: "var(--accent)",
                    fontSize: "0.78rem",
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

          {cleanBodyText.length < 40 && (
            <div
              aria-label="핫딜 핵심 요약 카드"
              style={{
                background: "var(--card-bg)",
                border: "1px solid var(--card-border)",
                borderRadius: "12px",
                padding: "12px 14px",
                display: "grid",
                gridTemplateColumns: "repeat(auto-fit, minmax(120px, 1fr))",
                gap: "8px 12px",
                fontSize: "0.8rem",
              }}
            >
              <div
                style={{
                  gridColumn: "1 / -1",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "space-between",
                  paddingBottom: "6px",
                  borderBottom: "1px solid var(--card-border)",
                  fontSize: "0.75rem",
                  fontWeight: 800,
                  color: "var(--accent)",
                  letterSpacing: "-0.01em",
                }}
              >
                <span>핵심 3줄 체크</span>
                <span style={{ color: "var(--text-muted)", fontWeight: 600 }}>
                  구매 전 주요 조건
                </span>
              </div>
              <div>
                <div style={{ color: "var(--text-muted)", fontSize: "0.72rem", fontWeight: 700 }}>판매처 · 출처</div>
                <div style={{ color: "var(--text-main)", fontWeight: 800, marginTop: "2px" }}>
                  {shopBadge || deal.community_name} · {deal.community_name}
                </div>
              </div>
              <div>
                <div style={{ color: "var(--text-muted)", fontSize: "0.72rem", fontWeight: 700 }}>가격</div>
                <div style={{ color: "var(--deal-orange)", fontWeight: 800, marginTop: "2px", fontVariantNumeric: "tabular-nums" }}>
                  {showZeroWonPrice
                    ? "0원 (무료)"
                    : formatPrice(activePrice, activeCurrency, activeOptionTitle || deal.title)}
                  {krwEstimate ? ` (${krwEstimate.formatted})` : ""}
                </div>
              </div>
              <div>
                <div style={{ color: "var(--text-muted)", fontSize: "0.72rem", fontWeight: 700 }}>배송 · 조건</div>
                <div style={{ color: "var(--text-main)", fontWeight: 700, marginTop: "2px" }}>
                  {displayShipping || "판매처 확인"}
                  {detailPaymentCondBadge ? ` · ${detailPaymentCondBadge}` : ""}
                </div>
              </div>
              <div>
                <div style={{ color: "var(--text-muted)", fontSize: "0.72rem", fontWeight: 700 }}>등록 시점</div>
                <div style={{ color: "var(--text-main)", fontWeight: 700, marginTop: "2px" }}>
                  {formatRelativeTimeKo(deal.originalDate, undefined, deal.scraped_at || "최근")}
                </div>
              </div>
            </div>
          )}

          {cleanBodyText ? (
            <div
              style={{
                fontSize: "0.9rem",
                lineHeight: 1.65,
                color: "var(--text-main)",
                whiteSpace: "pre-line",
                wordBreak: "break-word",
              }}
            >
              {cleanBodyText.slice(0, 3500)}
            </div>
          ) : (
            <div
              style={{
                fontSize: "0.86rem",
                lineHeight: 1.6,
                color: "var(--text-sub)",
                whiteSpace: "pre-line",
                wordBreak: "break-word",
              }}
            >
              {deal.aiSummary ||
                `[${deal.community_name}]에 등록된 특가 정보입니다. 쿠폰·카드 할인 및 재고는 아래 구매처 또는 원본 글에서 확인해 주세요.`}
            </div>
          )}

          {galleryImages.length > 0 && (
            <div
              style={{
                borderTop: "1px solid var(--card-border)",
                paddingTop: "12px",
                display: "flex",
                flexDirection: "column",
                gap: "8px",
              }}
            >
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "space-between",
                  gap: "8px",
                }}
              >
                <span
                  style={{
                    fontSize: "0.8rem",
                    fontWeight: 800,
                    color: "var(--accent)",
                    display: "inline-flex",
                    alignItems: "center",
                    gap: "5px",
                  }}
                >
                  <Images size={14} aria-hidden="true" />
                  <span>상품 이미지 ({galleryImages.length}장)</span>
                </span>
                <span style={{ fontSize: "0.74rem", color: "var(--text-muted)", fontWeight: 600 }}>
                  누르면 크게 볼 수 있어요
                </span>
              </div>
              <div
                style={{
                  display: "flex",
                  gap: "10px",
                  overflowX: "auto",
                  paddingBottom: "4px",
                }}
              >
                {galleryImages.map((imgUrl, idx) => {
                  const isFailed = Boolean(failedImageMap[imgUrl]);
                  const isLoaded = Boolean(loadedImageMap[imgUrl]);
                  return (
                    <button
                      key={`${imgUrl}-${idx}`}
                      type="button"
                      aria-label={`본문 이미지 ${idx + 1} 크게 보기`}
                      onClick={() => setLightboxIndex(idx)}
                      className="bounce-click"
                      style={{
                        width: "96px",
                        height: "96px",
                        minWidth: "96px",
                        minHeight: "96px",
                        borderRadius: "12px",
                        overflow: "hidden",
                        border: "1px solid var(--card-border)",
                        background: "var(--card-bg)",
                        position: "relative",
                        padding: 0,
                        flexShrink: 0,
                      }}
                    >
                      {!isFailed ? (
                        <>
                          {!isLoaded && (
                            <div
                              className="skeleton-shimmer"
                              aria-hidden="true"
                              style={{ position: "absolute", inset: 0 }}
                            />
                          )}
                          {/* eslint-disable-next-line @next/next/no-img-element */}
                          <img
                            src={getProxyImageUrl(imgUrl, { width: 240 })}
                            alt={`${displayTitle} 첨부 이미지 ${idx + 1}`}
                            loading="lazy"
                            referrerPolicy="no-referrer"
                            style={{
                              width: "100%",
                              height: "100%",
                              objectFit: "cover",
                              filter: isClosedState ? "grayscale(0.8)" : undefined,
                            }}
                            onLoad={() =>
                              setLoadedImageMap((prev) => ({ ...prev, [imgUrl]: true }))
                            }
                            onError={(e) => {
                              const primarySrc = getProxyImageUrl(imgUrl, { width: 240 });
                              if (tryApplyFallbackProxyImage(e.currentTarget, imgUrl, primarySrc)) {
                                return;
                              }
                              setFailedImageMap((prev) => ({ ...prev, [imgUrl]: true }));
                            }}
                          />
                        </>
                      ) : (
                        <BrandImagePlaceholder compact />
                      )}
                    </button>
                  );
                })}
              </div>
            </div>
          )}

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
                        aria-pressed={isSelected}
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
                          {src.image_url && !failedImageMap[src.image_url] ? (
                            // eslint-disable-next-line @next/next/no-img-element
                            <img
                              src={getProxyImageUrl(src.image_url, { width: 120 })}
                              alt={optionTitle}
                              style={{ width: "100%", height: "100%", objectFit: "cover" }}
                              referrerPolicy="no-referrer"
                              loading="lazy"
                              onError={(e) => {
                                const primarySrc = getProxyImageUrl(src.image_url, { width: 120 });
                                if (tryApplyFallbackProxyImage(e.currentTarget, src.image_url, primarySrc)) {
                                  return;
                                }
                                if (src.image_url) {
                                  setFailedImageMap((prev) => ({
                                    ...prev,
                                    [src.image_url!]: true,
                                  }));
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
                      <div
                        style={{
                          display: "flex",
                          alignItems: "center",
                          gap: "6px",
                          flexShrink: 0,
                          flexWrap: "wrap",
                          justifyContent: "flex-end",
                        }}
                      >
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
                              minHeight: "44px",
                              minWidth: "44px",
                              padding: "6px 10px",
                              borderRadius: "8px",
                              background: isCopied
                                ? "var(--success-bg)"
                                : "var(--accent-soft-bg)",
                              border: isCopied
                                ? "1px solid var(--success-text)"
                                : "1px dashed var(--accent-soft-border)",
                              color: isCopied ? "var(--success-text)" : "var(--accent)",
                              fontSize: "0.74rem",
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
                                minHeight: "44px",
                                minWidth: "44px",
                                padding: "0 8px",
                                display: "inline-flex",
                                alignItems: "center",
                                justifyContent: "center",
                                fontSize: "0.76rem",
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
                            minHeight: "44px",
                            minWidth: "44px",
                            padding: "8px 14px",
                            borderRadius: "8px",
                            background: isSelected ? "var(--accent)" : "var(--primary-gradient)",
                            color: "var(--on-accent)",
                            fontSize: "0.78rem",
                            fontWeight: 700,
                            textDecoration: "none",
                            flexShrink: 0,
                            boxShadow: "0 2px 8px rgba(var(--accent-rgb), 0.22)",
                          }}
                          title="쇼핑몰 바로가기"
                        >
                          <span>구매</span>
                          <ExternalLink size={12} color="var(--on-accent)" aria-hidden="true" />
                        </a>
                      </div>
                    </div>
                  );
                })}
              </div>
            </div>
          )}
        </div>
          }
        />

        {similarDeals.length > 0 && (
          <section
            className="deal-similar-section"
            aria-label="유사한 핫딜 추천"
            style={{
              padding: '12px 20px 14px',
              borderTop: '1px solid var(--card-border)',
              background: 'var(--surface-subtle)',
            }}
          >
            <div
              style={{
                fontSize: '13px',
                fontWeight: 700,
                marginBottom: '8px',
                color: 'var(--text-main)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
              }}
            >
              <span>비슷한 커뮤니티 핫딜</span>
              <span style={{ fontSize: '11px', fontWeight: 500, color: 'var(--text-sub)' }}>
                {similarDeals.length}건
              </span>
            </div>
            <div
              style={{
                display: 'grid',
                gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
                gap: '8px',
              }}
            >
              {similarDeals.slice(0, 4).map((sd) => {
                const priceLabel = sd.isFree
                  ? '무료'
                  : formatPrice(sd.price, sd.currency, sd.title) || '옵션 확인';
                return (
                  <button
                    key={sd.id}
                    type="button"
                    onClick={() => onSelectRelatedDeal?.(sd)}
                    style={{
                      display: 'flex',
                      flexDirection: 'column',
                      alignItems: 'flex-start',
                      gap: '4px',
                      padding: '10px 12px',
                      borderRadius: '12px',
                      border: '1px solid var(--card-border)',
                      background: 'var(--card-bg)',
                      color: 'var(--text-main)',
                      textAlign: 'left',
                      cursor: onSelectRelatedDeal ? 'pointer' : 'default',
                      opacity: sd.isClosed ? 0.65 : 1,
                    }}
                  >
                    <span
                      style={{
                        fontSize: '12px',
                        fontWeight: 600,
                        color: 'var(--text-main)',
                        lineHeight: 1.35,
                        display: '-webkit-box',
                        WebkitLineClamp: 2,
                        WebkitBoxOrient: 'vertical',
                        overflow: 'hidden',
                        width: '100%',
                      }}
                    >
                      {sd.title}
                    </span>
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: '6px',
                        fontSize: '11px',
                        color: 'var(--text-sub)',
                        flexWrap: 'wrap',
                      }}
                    >
                      <strong style={{ color: 'var(--accent)', fontSize: '12px' }}>
                        {priceLabel}
                      </strong>
                      <span>· {sd.shop_name || sd.community_name}</span>
                      {sd.isClosed && <span>(종료)</span>}
                    </div>
                  </button>
                );
              })}
            </div>
          </section>
        )}

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
                  fontSize: "0.78rem",
                  display: "inline-flex",
                  alignItems: "center",
                  gap: "4px",
                  minHeight: "44px",
                  minWidth: "44px",
                  padding: "6px 10px",
                  borderRadius: "8px",
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
              fontSize: "0.75rem",
              color: "var(--text-muted)",
              textAlign: "center",
              margin: "1px 0 0 0",
              lineHeight: 1.35,
            }}
          >
            일부 쇼핑몰 이동 링크는 제휴 마케팅의 일환으로 일정액의 수수료를 제공받을 수 있습니다.
          </p>
        </div>
      </div>

      <ImageLightboxModal
        isOpen={lightboxIndex !== null && galleryImages.length > 0}
        images={galleryImages}
        initialIndex={lightboxIndex ?? 0}
        title={displayTitle}
        onClose={closeLightbox}
      />
    </div>
  );
}
