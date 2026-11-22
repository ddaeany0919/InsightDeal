"use client";

import { useEffect, useState, useRef, useCallback } from "react";
import {
  Download,
  Heart,
  Bell,
  RefreshCw,
  Settings,
  Search,
  X,
  Flame,
  RotateCcw,
  AlertCircle,
  Smartphone,
  Check,
  ChevronDown,
  ChevronUp,
  ArrowUp,
  Clock,
  EyeOff,
  LayoutGrid,
  List,
  SlidersHorizontal,
} from "lucide-react";
import Link from "next/link";

import type {
  Deal,
  PopularKeywordRanking,
  RawDealApiResponse,
  HotDealsApiResponse,
  CategoryMetaItem,
  WishlistApiItem,
  NextCursor,
} from "./lib/types";
import {
  PLATFORMS,
  formatPrice,
  apiUrl,
  getOrCreateWebDeviceUuid,
  ensureWebDeviceAuth,
  authFetch,
  ensureWebPushSubscribed,
  decodeHtmlEntities,
  mapRawDealToDeal,
  buildDealWishlistKeyword,
  loadWishlistIdMap,
  saveWishlistIdEntry,
  removeWishlistIdEntry,
  resolveServerWishlistId,
} from "./lib/utils";
import {
  hasTossBridge,
  logTossScreen,
  logTossClick,
  maybeRequestTossReview,
  copyTextSafe,
} from "./lib/tossBridge";

import PrivacyModal from "./components/PrivacyModal";
import EulaModal from "./components/EulaModal";
import DealDetailModal from "./components/DealDetailModal";
import DeviceSyncModal from "./components/DeviceSyncModal";
import WishlistDrawer from "./components/WishlistDrawer";
import NotificationDrawer from "./components/NotificationDrawer";
import TrendingKeywordsTicker from "./components/TrendingKeywordsTicker";
import ThemeToggle from "./components/ThemeToggle";
import InAppBrowserBanner from "./components/InAppBrowserBanner";
import DealCard from "./components/DealCard";

interface BeforeInstallPromptEvent extends Event {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: "accepted" | "dismissed"; platform: string }>;
}

const DEFAULT_CATEGORIES: CategoryMetaItem[] = [
  { key: "전체", label: "전체" },
  { key: "음식", label: "식품/건강" },
  { key: "SW/게임", label: "SW/게임" },
  { key: "PC용품", label: "PC/하드웨어" },
  { key: "가전제품", label: "가전/TV" },
  { key: "생활용품", label: "생활용품" },
  { key: "패션", label: "의류/패션" },
  { key: "뷰티", label: "뷰티" },
  { key: "모바일/기프티콘", label: "모바일/기프티콘" },
  { key: "상품권", label: "상품권" },
  { key: "적립", label: "적립/포인트" },
  { key: "이벤트", label: "이벤트" },
  { key: "육아/유아용품", label: "육아/유아용품" },
  { key: "해외직구", label: "해외직구" },
  { key: "핫딜모음", label: "핫딜모음" },
  { key: "기타", label: "기타" },
];

const HOT_DEALS_ERROR_COPY =
  "핫딜 목록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.";

function isAbortError(err: unknown): boolean {
  return (
    (typeof DOMException !== "undefined" &&
      err instanceof DOMException &&
      err.name === "AbortError") ||
    (err instanceof Error && err.name === "AbortError")
  );
}

type FeedSortKey =
  | "latest"
  | "popular"
  | "price_asc"
  | "price_desc"
  | "comments"
  | "recommendations";

interface FeedCacheEntry {
  deals: Deal[];
  hasMore: boolean;
  nextCursor: NextCursor | null;
  cachedAt: number;
}

const FEED_SWR_FRESH_MS = 15_000;
const FEED_SWR_STALE_MS = 45_000;
const RECENT_DEALS_STORAGE_KEY = "insightdeal_recent_deals";
const HIDDEN_DEALS_STORAGE_KEY = "insightdeal_hidden_deal_ids";
const MUTED_KEYWORDS_STORAGE_KEY = "insightdeal_muted_keywords";

function formatCompactPriceLabel(rawNum: string): string {
  const num = parseInt(rawNum.replace(/[^0-9]/g, ""), 10);
  if (!Number.isFinite(num) || num <= 0) return "";
  return `${num.toLocaleString("ko-KR")}원`;
}

export default function Home() {
  const [deals, setDeals] = useState<Deal[]>([]);
  const [loading, setLoading] = useState(true);
  const [fetchError, setFetchError] = useState<string | null>(null);
  const [categories, setCategories] =
    useState<CategoryMetaItem[]>(DEFAULT_CATEGORIES);
  const [isMounted, setIsMounted] = useState(false);
  const [selectedCategories, setSelectedCategories] = useState<string[]>([]);
  const selectedCategoriesRef = useRef<string[]>([]);
  const [selectedPlatforms, setSelectedPlatforms] = useState<string[]>([]);
  const selectedPlatformsRef = useRef<string[]>([]);
  const [isPlatformsExpanded, setIsPlatformsExpanded] = useState(false);
  const [isCategoriesExpanded, setIsCategoriesExpanded] = useState(false);
  const [deferredPrompt, setDeferredPrompt] =
    useState<BeforeInstallPromptEvent | null>(null);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [newDealsCount, setNewDealsCount] = useState<number>(0);
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const [currentTime, setCurrentTime] = useState<number>(0);
  const [wishlist, setWishlist] = useState<number[]>([]);
  const [wishlistItemsMap, setWishlistItemsMap] = useState<Record<number, Deal>>(
    {}
  );
  const [alerts, setAlerts] = useState<number[]>([]);
  const [serverWishlists, setServerWishlists] = useState<WishlistApiItem[]>([]);
  const serverWishlistsRef = useRef<WishlistApiItem[]>([]);
  const dealsRef = useRef<Deal[]>([]);
  const feedCacheRef = useRef<Map<string, FeedCacheEntry>>(new Map());
  const searchInputRef = useRef<HTMLInputElement | null>(null);
  const wishlistRef = useRef<number[]>([]);
  const wishlistItemsMapRef = useRef<Record<number, Deal>>({});
  const alertsRef = useRef<number[]>([]);
  const unwishlistedDealIdsRef = useRef<Set<number>>(new Set());
  const hydrateEpochRef = useRef<number>(0);
  const idMutationQueueRef = useRef<Map<number, Promise<void>>>(new Map());
  const [serverAlertCount, setServerAlertCount] = useState<number>(0);
  const [selectedDeal, setSelectedDeal] = useState<Deal | null>(null);
  const [isNotificationOpen, setIsNotificationOpen] = useState(false);
  const [isWishlistOpen, setIsWishlistOpen] = useState(false);
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [appliedSearch, setAppliedSearch] = useState("");
  const appliedSearchRef = useRef("");
  const [recentSearches, setRecentSearches] = useState<string[]>([]);
  const [recentDeals, setRecentDeals] = useState<Deal[]>([]);
  const [showRecentDealsPanel, setShowRecentDealsPanel] = useState(false);
  const [hiddenDealIds, setHiddenDealIds] = useState<number[]>([]);
  const [mutedKeywords, setMutedKeywords] = useState<string[]>([]);
  const [muteKeywordInput, setMuteKeywordInput] = useState("");
  const [showHiddenManagePanel, setShowHiddenManagePanel] = useState(false);
  const [freeShippingOnly, setFreeShippingOnly] = useState(false);
  const freeShippingOnlyRef = useRef(false);
  const [shopFilter, setShopFilter] = useState("");
  const shopFilterRef = useRef("");
  const [minPriceInput, setMinPriceInput] = useState("");
  const [maxPriceInput, setMaxPriceInput] = useState("");
  const minPriceRef = useRef("");
  const maxPriceRef = useRef("");
  const [viewMode, setViewMode] = useState<"card" | "compact">("card");
  const [showMobileQuickFilters, setShowMobileQuickFilters] = useState(false);
  const [popularKeywords, setPopularKeywords] = useState<string[]>([
    "제로콜라",
    "로봇청소기",
    "아이폰",
    "모니터",
    "닭가슴살",
    "물티슈",
    "에어팟",
    "갤럭시",
  ]);
  const [popularRankings, setPopularRankings] = useState<
    PopularKeywordRanking[]
  >([]);
  const [popularUpdatedAt, setPopularUpdatedAt] = useState<string>("");
  const [onlyHotDeals, setOnlyHotDeals] = useState(false);
  const [hideClosedDeals, setHideClosedDeals] = useState(false);
  const [includeClosedDeals, setIncludeClosedDeals] = useState(false);
  const onlyHotDealsRef = useRef(false);
  const hideClosedDealsRef = useRef(false);
  const includeClosedDealsRef = useRef(false);
  const nextCursorRef = useRef<NextCursor | null>(null);
  const [sortBy, setSortBy] = useState<FeedSortKey>("latest");
  const sortByRef = useRef<FeedSortKey>("latest");
  const [isTossMiniApp, setIsTossMiniApp] = useState(
    process.env.NEXT_PUBLIC_TOSS_MINIAPP === "true"
  );

  const [hasMore, setHasMore] = useState(true);
  const [isFetchingMore, setIsFetchingMore] = useState(false);
  const observerTarget = useRef<HTMLDivElement>(null);

  const [showPrivacyModal, setShowPrivacyModal] = useState(false);
  const [showEulaModal, setShowEulaModal] = useState(false);
  const [showSyncModal, setShowSyncModal] = useState(false);
  const [isCompactViewport, setIsCompactViewport] = useState(false);
  const [isUltraCompactViewport, setIsUltraCompactViewport] = useState(false);
  const [showScrollTop, setShowScrollTop] = useState(false);
  const urlSyncReadyRef = useRef(false);

  useEffect(() => {
    const syncViewport = () => {
      setIsCompactViewport(window.innerWidth <= 480);
      const rootFontPx =
        parseFloat(getComputedStyle(document.documentElement).fontSize || "16") ||
        16;
      setIsUltraCompactViewport(
        window.innerWidth <= 368 || rootFontPx >= 18
      );
    };
    syncViewport();
    window.addEventListener("resize", syncViewport);
    return () => window.removeEventListener("resize", syncViewport);
  }, []);

  useEffect(() => {
    const onScroll = () => setShowScrollTop(window.scrollY > 640);
    onScroll();
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  const toastTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const liveDealsAbortRef = useRef<AbortController | null>(null);
  const isComposingRef = useRef(false);
  const searchDebounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pendingWishlistRef = useRef<Set<number>>(new Set());
  const pendingAlertRef = useRef<Set<number>>(new Set());
  const maxKnownDealIdRef = useRef<number>(0);

  const clearSearchDebounce = useCallback(() => {
    if (searchDebounceRef.current) {
      clearTimeout(searchDebounceRef.current);
      searchDebounceRef.current = null;
    }
  }, []);

  useEffect(() => {
    dealsRef.current = deals;
    maxKnownDealIdRef.current = deals.reduce((acc, item) => (item.id > acc ? item.id : acc), 0);
  }, [deals]);

  useEffect(() => {
    wishlistRef.current = wishlist;
  }, [wishlist]);

  useEffect(() => {
    wishlistItemsMapRef.current = wishlistItemsMap;
  }, [wishlistItemsMap]);

  useEffect(() => {
    alertsRef.current = alerts;
  }, [alerts]);

  const reconcileWishlistsWithDeals = useCallback(
    (targetDeals: Deal[], serverList: WishlistApiItem[]) => {
      if (typeof window === "undefined" || serverList.length === 0) return;

      const idMap = loadWishlistIdMap();
      const allKnownDeals = [
        ...targetDeals,
        ...Object.values(wishlistItemsMapRef.current),
      ];
      if (allKnownDeals.length === 0) return;

      const nextWishlist = [...wishlistRef.current];
      const nextAlerts = [...alertsRef.current];
      const nextItemsMap = { ...wishlistItemsMapRef.current };
      let targetMap: Record<string, number> = {};
      try {
        const rawTarget = localStorage.getItem("insightdeal_target_prices");
        if (rawTarget) {
          const parsed = JSON.parse(rawTarget);
          if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
            targetMap = parsed as Record<string, number>;
          }
        }
      } catch {}

      let matchedAny = false;
      for (const wItem of serverList) {
        const normKw = decodeHtmlEntities(wItem.keyword || "")
          .trim()
          .toLowerCase();
        if (!normKw) continue;

        let matchedDeal: Deal | undefined;
        for (const [keyStr, entry] of Object.entries(idMap)) {
          if (entry.wishlistId === wItem.id) {
            const parsedId = Number(keyStr.split("_")[0]);
            matchedDeal = allKnownDeals.find((d) => d.id === parsedId);
            if (matchedDeal) break;
          }
        }
        if (!matchedDeal) {
          matchedDeal = allKnownDeals.find((d) => {
            const kw = buildDealWishlistKeyword(d.title).toLowerCase();
            const titleLower = decodeHtmlEntities(d.title).toLowerCase();
            return kw === normKw || titleLower.includes(normKw);
          });
        }
        if (matchedDeal) {
          if (unwishlistedDealIdsRef.current.has(matchedDeal.id)) {
            // 사용자가 찜을 해제한 딜은 알림용 항목(alert_enabled=true)으로 서버에 남아있더라도 재편입 금지
            if (wItem.alert_enabled !== false && Number(wItem.target_price) > 0) {
              saveWishlistIdEntry(matchedDeal.id, wItem.id, wItem.keyword);
            }
            continue;
          }
          matchedAny = true;
          saveWishlistIdEntry(matchedDeal.id, wItem.id, wItem.keyword);
          if (!nextWishlist.includes(matchedDeal.id)) {
            nextWishlist.push(matchedDeal.id);
          }
          nextItemsMap[matchedDeal.id] = matchedDeal;
          if (wItem.alert_enabled !== false && Number(wItem.target_price) > 0) {
            if (!nextAlerts.includes(matchedDeal.id)) {
              nextAlerts.push(matchedDeal.id);
            }
            const dealCurr = String(matchedDeal.currency || "KRW").trim().toUpperCase();
            const isSubunit = dealCurr === "USD" || dealCurr === "EUR" || dealCurr === "CNY";
            const rawTarget = Number(wItem.target_price);
            targetMap[String(matchedDeal.id)] = isSubunit
              ? Math.round(rawTarget) / 100
              : rawTarget;
          }
        }
      }

      if (!matchedAny) return;

      wishlistRef.current = nextWishlist;
      setWishlist(nextWishlist);
      wishlistItemsMapRef.current = nextItemsMap;
      setWishlistItemsMap(nextItemsMap);
      alertsRef.current = nextAlerts;
      setAlerts(nextAlerts);
      try {
        localStorage.setItem("insightdeal_wishlist", JSON.stringify(nextWishlist));
        localStorage.setItem("insightdeal_wishlist_items", JSON.stringify(nextItemsMap));
        localStorage.setItem("insightdeal_alerts", JSON.stringify(nextAlerts));
        localStorage.setItem("insightdeal_target_prices", JSON.stringify(targetMap));
      } catch {}
    },
    []
  );

  const hydrateFromServer = useCallback(async () => {
    if (typeof window === "undefined") return;
    const epoch = hydrateEpochRef.current;
    try {
      await ensureWebDeviceAuth();
      if (epoch !== hydrateEpochRef.current) return;
      const [notifRes, wishlistRes] = await Promise.allSettled([
        authFetch(
          (latestAuth) =>
            `/api/users/notifications?user_id=${encodeURIComponent(latestAuth.deviceUuid)}`
        ),
        authFetch(
          (latestAuth) =>
            `/api/wishlist/?user_id=${encodeURIComponent(latestAuth.deviceUuid)}`
        ),
      ]);

      if (notifRes.status === "fulfilled" && notifRes.value.ok) {
        const notifData = await notifRes.value.json().catch(() => null);
        if (Array.isArray(notifData)) {
          setServerAlertCount(notifData.length);
        }
      }

      if (wishlistRes.status === "fulfilled" && wishlistRes.value.ok) {
        const wData = await wishlistRes.value.json().catch(() => null);
        if (Array.isArray(wData)) {
          if (epoch !== hydrateEpochRef.current) {
            return;
          }
          const list = wData as WishlistApiItem[];
          serverWishlistsRef.current = list;
          setServerWishlists(list);
          reconcileWishlistsWithDeals(dealsRef.current, list);
        }
      }
    } catch {}
  }, [reconcileWishlistsWithDeals]);

  const enqueueDealMutation = useCallback(
    (dealId: number, task: () => Promise<void>): Promise<void> => {
      const prev = idMutationQueueRef.current.get(dealId) ?? Promise.resolve();
      const next = prev
        .catch(() => undefined)
        .then(task)
        .finally(() => {
          if (idMutationQueueRef.current.get(dealId) === next) {
            idMutationQueueRef.current.delete(dealId);
          }
        });
      idMutationQueueRef.current.set(dealId, next);
      return next;
    },
    []
  );

  const applyWishlistAdd = useCallback((id: number, targetDeal?: Deal) => {
    const nextWishlist = wishlistRef.current.includes(id)
      ? wishlistRef.current
      : [...wishlistRef.current, id];
    wishlistRef.current = nextWishlist;
    setWishlist((prev) => (prev.includes(id) ? prev : [...prev, id]));

    if (targetDeal) {
      const nextMap = { ...wishlistItemsMapRef.current, [id]: targetDeal };
      wishlistItemsMapRef.current = nextMap;
      setWishlistItemsMap((prev) => ({ ...prev, [id]: targetDeal }));
    }

    if (typeof window !== "undefined") {
      localStorage.setItem("insightdeal_wishlist", JSON.stringify(nextWishlist));
      localStorage.setItem(
        "insightdeal_wishlist_items",
        JSON.stringify(wishlistItemsMapRef.current)
      );
    }
  }, []);

  const applyWishlistRemove = useCallback((id: number) => {
    const nextWishlist = wishlistRef.current.filter((item) => item !== id);
    wishlistRef.current = nextWishlist;
    setWishlist((prev) => prev.filter((item) => item !== id));

    const nextMap = { ...wishlistItemsMapRef.current };
    delete nextMap[id];
    wishlistItemsMapRef.current = nextMap;
    setWishlistItemsMap((prev) => {
      const copy = { ...prev };
      delete copy[id];
      return copy;
    });

    if (typeof window !== "undefined") {
      localStorage.setItem("insightdeal_wishlist", JSON.stringify(nextWishlist));
      localStorage.setItem("insightdeal_wishlist_items", JSON.stringify(nextMap));
    }
  }, []);

  const applyAlertAdd = useCallback((id: number) => {
    const nextAlerts = alertsRef.current.includes(id)
      ? alertsRef.current
      : [...alertsRef.current, id];
    alertsRef.current = nextAlerts;
    setAlerts((prev) => (prev.includes(id) ? prev : [...prev, id]));
    if (typeof window !== "undefined") {
      localStorage.setItem("insightdeal_alerts", JSON.stringify(nextAlerts));
    }
  }, []);

  const applyAlertRemove = useCallback((id: number) => {
    const nextAlerts = alertsRef.current.filter((item) => item !== id);
    alertsRef.current = nextAlerts;
    setAlerts((prev) => prev.filter((item) => item !== id));
    if (typeof window !== "undefined") {
      localStorage.setItem("insightdeal_alerts", JSON.stringify(nextAlerts));
    }
  }, []);

  const updateAppliedSearch = useCallback((nextQuery: string) => {
    const trimmed = nextQuery.trim();
    appliedSearchRef.current = trimmed;
    setAppliedSearch(trimmed);
  }, []);

  const showToast = useCallback((message: string) => {
    if (toastTimerRef.current) {
      clearTimeout(toastTimerRef.current);
    }
    setToastMessage(message);
    toastTimerRef.current = setTimeout(() => {
      setToastMessage(null);
      toastTimerRef.current = null;
    }, 2500);
  }, []);

  const handleInstallApp = async () => {
    if (deferredPrompt) {
      await deferredPrompt.prompt();
      const { outcome } = await deferredPrompt.userChoice;
      if (outcome === "accepted") {
        setDeferredPrompt(null);
        showToast("앱 설치를 시작했어요");
      }
      return;
    }
    showToast("브라우저 메뉴에서 '홈 화면에 추가'를 눌러 설치할 수 있어요");
  };

  const getOrCreateBrowserUserId = useCallback(() => {
    const uuid = getOrCreateWebDeviceUuid();
    setIsLoggedIn(true);
    return uuid;
  }, []);

  const handleQuickSubscribeKeyword = async (rawKw: string) => {
    const cleanKw = rawKw.trim();
    if (!cleanKw) return;
    try {
      const { pushGranted, reason } = await ensureWebPushSubscribed();
      const res = await authFetch((latestAuth) => ({
        pathOrUrl: "/api/push/keywords",
        init: {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            device_uuid: latestAuth.deviceUuid,
            keyword: cleanKw,
          }),
        },
      }));

      if (!res.ok) {
        showToast("알림을 등록하지 못했어요. 잠시 후 다시 시도해 주세요");
        return;
      }

      if (typeof window !== "undefined") {
        let parsed: { id: string; keyword: string }[] = [];
        try {
          const saved = localStorage.getItem("insightdeal_web_keywords");
          const rawParsed = saved ? JSON.parse(saved) : [];
          if (Array.isArray(rawParsed)) {
            parsed = rawParsed;
          }
        } catch {
          parsed = [];
        }
        if (!parsed.some((k) => k.keyword === cleanKw)) {
          const updated = [
            ...parsed,
            { id: Date.now().toString(), keyword: cleanKw },
          ];
          localStorage.setItem(
            "insightdeal_web_keywords",
            JSON.stringify(updated)
          );
        }
      }
      if (pushGranted) {
        showToast(`'${cleanKw}' 새 딜이 올라오면 바로 알려드릴게요`);
      } else if (reason === "in_app_webview" || reason === "toss_miniapp") {
        showToast(
          `'${cleanKw}' 키워드를 등록했어요. 인앱 브라우저에서는 푸시 대신 알림함에서 확인할 수 있어요`
        );
      } else {
        showToast(
          `'${cleanKw}' 키워드를 등록했어요. 브라우저 알림을 허용하면 푸시로 알려드려요`
        );
      }
      logTossClick("register_keyword_alert", {
        button_name: "keyword_alert_submit",
      });
      maybeRequestTossReview();
    } catch {
      showToast("알림을 등록하지 못했어요. 잠시 후 다시 시도해 주세요");
    }
  };

  const handleShareDeal = async (e: React.MouseEvent, deal: Deal) => {
    e.preventDefault();
    e.stopPropagation();
    const cleanShareTitle = decodeHtmlEntities(deal.title);
    const shareUrl =
      typeof window !== "undefined"
        ? `${window.location.origin}/?dealId=${deal.id}`
        : deal.post_link;
    const sharePrice = deal.isFree
      ? "0원 (무료)"
      : formatPrice(deal.price, deal.currency, cleanShareTitle);
    const shareText = `${cleanShareTitle} (${sharePrice})`;

    if (typeof navigator !== "undefined" && navigator.share) {
      try {
        await navigator.share({
          title: cleanShareTitle,
          text: shareText,
          url: shareUrl,
        });
        return;
      } catch {
        // 사용자 공유 취소 시 클립보드 복사로 폴백
      }
    }
    const copied = await copyTextSafe(`${shareText}\n${shareUrl}`);
    if (copied) {
      showToast("상품 링크를 복사했어요");
    }
  };

  const toggleWishlist = async (e: React.MouseEvent, id: number) => {
    e.preventDefault();
    e.stopPropagation();
    if (pendingWishlistRef.current.has(id)) return;
    pendingWishlistRef.current.add(id);
    hydrateEpochRef.current += 1;

    await enqueueDealMutation(id, async () => {
      let rollbackAction: (() => void) | null = null;
      try {
        const auth = await ensureWebDeviceAuth();
        setIsLoggedIn(true);

        const targetDeal =
          dealsRef.current.find((d) => d.id === id) ||
          wishlistItemsMapRef.current[id];
        const keyword = buildDealWishlistKeyword(targetDeal?.title || `deal_${id}`);
        const isAdded = wishlistRef.current.includes(id);

        if (isAdded) {
          const prevSavedDeal = wishlistItemsMapRef.current[id];
          const wasUnwishlistedBefore = unwishlistedDealIdsRef.current.has(id);
          unwishlistedDealIdsRef.current.add(id);
          applyWishlistRemove(id);
          rollbackAction = () => {
            if (!wasUnwishlistedBefore) {
              unwishlistedDealIdsRef.current.delete(id);
            }
            applyWishlistAdd(id, prevSavedDeal);
          };

          const isAlertStillOn = alertsRef.current.includes(id);
          let serverOk = true;
          if (!isAlertStillOn) {
            const beforeUuid = auth.deviceUuid;
            const wishlistId = await resolveServerWishlistId(
              id,
              keyword,
              beforeUuid,
              targetDeal?.title ? [decodeHtmlEntities(targetDeal.title)] : []
            );
            if (wishlistId !== null) {
              const delRes = await authFetch(
                (latestAuth) =>
                  `/api/wishlist/${wishlistId}?user_id=${encodeURIComponent(latestAuth.deviceUuid)}`,
                { method: "DELETE" }
              );
              const afterUuid = getOrCreateWebDeviceUuid();
              if (!delRes.ok && delRes.status !== 404) {
                if (delRes.status === 403 && afterUuid !== beforeUuid) {
                  // 401 세션 회전으로 구 계정의 wishlistId를 호출한 경우 새 계정 기준으로 재확인
                  removeWishlistIdEntry(id);
                  const rotatedWishlistId = await resolveServerWishlistId(
                    id,
                    keyword,
                    afterUuid,
                    targetDeal?.title ? [decodeHtmlEntities(targetDeal.title)] : []
                  );
                  if (rotatedWishlistId !== null) {
                    const retryDel = await authFetch(
                      (latestAuth) =>
                        `/api/wishlist/${rotatedWishlistId}?user_id=${encodeURIComponent(latestAuth.deviceUuid)}`,
                      { method: "DELETE" }
                    );
                    if (!retryDel.ok && retryDel.status !== 404) {
                      serverOk = false;
                    } else {
                      removeWishlistIdEntry(id);
                      serverWishlistsRef.current = serverWishlistsRef.current.filter(
                        (item) => item.id !== rotatedWishlistId
                      );
                      setServerWishlists(serverWishlistsRef.current);
                    }
                  }
                } else {
                  serverOk = false;
                }
              } else {
                removeWishlistIdEntry(id);
                serverWishlistsRef.current = serverWishlistsRef.current.filter(
                  (item) => item.id !== wishlistId
                );
                setServerWishlists(serverWishlistsRef.current);
              }
            }
          }

          if (!serverOk) {
            rollbackAction();
            rollbackAction = null;
            showToast("찜 해제에 실패했어요. 잠시 후 다시 시도해 주세요");
            return;
          }

          rollbackAction = null;
          showToast("찜 목록에서 뺐어요");
        } else {
          const wasUnwishlisted = unwishlistedDealIdsRef.current.has(id);
          unwishlistedDealIdsRef.current.delete(id);
          rollbackAction = () => {
            if (wasUnwishlisted) {
              unwishlistedDealIdsRef.current.add(id);
            } else {
              unwishlistedDealIdsRef.current.delete(id);
            }
          };
          const existingMap = loadWishlistIdMap();
          if (!existingMap[String(id)]) {
            const postRes = await authFetch((latestAuth) => ({
              pathOrUrl: "/api/wishlist/from-keyword",
              init: {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                  keyword,
                  target_price: 0,
                  user_id: latestAuth.deviceUuid,
                }),
              },
            }));
            if (!postRes.ok) {
              rollbackAction();
              rollbackAction = null;
              showToast("찜 등록에 실패했어요. 잠시 후 다시 시도해 주세요");
              return;
            }
            const saved = (await postRes.json().catch(() => null)) as
              | WishlistApiItem
              | null;
            if (saved && typeof saved.id === "number") {
              saveWishlistIdEntry(id, saved.id, saved.keyword || keyword);
            }
          }

          rollbackAction = null;
          applyWishlistAdd(id, targetDeal);
          showToast("찜 목록에 담았어요");
          logTossClick("save_wishlist_deal", {
            button_name: "wishlist_button",
            deal_id: id,
          });
          if (wishlistRef.current.length >= 2) {
            maybeRequestTossReview();
          }
        }
      } catch {
        rollbackAction?.();
        showToast("찜 상태를 변경하지 못했어요. 잠시 후 다시 시도해 주세요");
      } finally {
        pendingWishlistRef.current.delete(id);
      }
    });
  };

  const toggleAlert = async (e: React.MouseEvent, id: number) => {
    e.preventDefault();
    e.stopPropagation();
    if (pendingAlertRef.current.has(id)) return;
    pendingAlertRef.current.add(id);
    hydrateEpochRef.current += 1;

    await enqueueDealMutation(id, async () => {
      let rollbackAction: (() => void) | null = null;
      let isTurningOff = false;
      try {
        const auth = await ensureWebDeviceAuth();
        setIsLoggedIn(true);

        const targetDeal =
          dealsRef.current.find((d) => d.id === id) ||
          wishlistItemsMapRef.current[id];
        const keyword = buildDealWishlistKeyword(targetDeal?.title || `deal_${id}`);
        const isAlertOn = alertsRef.current.includes(id);
        isTurningOff = isAlertOn;

        if (isAlertOn) {
          let prevTargetPriceVal: number | undefined;
          try {
            const mapStr = localStorage.getItem("insightdeal_target_prices");
            const targetMap = mapStr ? JSON.parse(mapStr) : {};
            prevTargetPriceVal = targetMap[id];
          } catch {}

          applyAlertRemove(id);
          rollbackAction = () => {
            applyAlertAdd(id);
          };

          const beforeUuid = auth.deviceUuid;
          const wishlistId = await resolveServerWishlistId(
            id,
            keyword,
            beforeUuid,
            targetDeal?.title ? [decodeHtmlEntities(targetDeal.title)] : []
          );

          let serverOk = true;
          if (wishlistId !== null) {
            const isStillWishlisted = wishlistRef.current.includes(id);
            if (isStillWishlisted) {
              const patchRes = await authFetch(
                (latestAuth) => ({
                  pathOrUrl: `/api/wishlist/${wishlistId}?user_id=${encodeURIComponent(latestAuth.deviceUuid)}`,
                  init: {
                    method: "PATCH",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({
                      alert_enabled: false,
                      target_price: 0,
                    }),
                  },
                })
              );
              const afterUuid = getOrCreateWebDeviceUuid();
              if (!patchRes.ok && patchRes.status !== 404) {
                if (patchRes.status === 403 && afterUuid !== beforeUuid) {
                  removeWishlistIdEntry(id);
                } else {
                  serverOk = false;
                }
              } else {
                serverWishlistsRef.current = serverWishlistsRef.current.map((item) =>
                  item.id === wishlistId
                    ? { ...item, alert_enabled: false, target_price: 0 }
                    : item
                );
                setServerWishlists(serverWishlistsRef.current);
              }
            } else {
              const delRes = await authFetch(
                (latestAuth) =>
                  `/api/wishlist/${wishlistId}?user_id=${encodeURIComponent(latestAuth.deviceUuid)}`,
                { method: "DELETE" }
              );
              const afterUuid = getOrCreateWebDeviceUuid();
              if (!delRes.ok && delRes.status !== 404) {
                if (delRes.status === 403 && afterUuid !== beforeUuid) {
                  removeWishlistIdEntry(id);
                  const rotatedWishlistId = await resolveServerWishlistId(
                    id,
                    keyword,
                    afterUuid,
                    targetDeal?.title ? [decodeHtmlEntities(targetDeal.title)] : []
                  );
                  if (rotatedWishlistId !== null) {
                    const retryDel = await authFetch(
                      (latestAuth) =>
                        `/api/wishlist/${rotatedWishlistId}?user_id=${encodeURIComponent(latestAuth.deviceUuid)}`,
                      { method: "DELETE" }
                    );
                    if (!retryDel.ok && retryDel.status !== 404) {
                      serverOk = false;
                    } else {
                      removeWishlistIdEntry(id);
                      serverWishlistsRef.current = serverWishlistsRef.current.filter(
                        (item) => item.id !== rotatedWishlistId
                      );
                      setServerWishlists(serverWishlistsRef.current);
                    }
                  }
                } else {
                  serverOk = false;
                }
              } else {
                removeWishlistIdEntry(id);
                serverWishlistsRef.current = serverWishlistsRef.current.filter(
                  (item) => item.id !== wishlistId
                );
                setServerWishlists(serverWishlistsRef.current);
              }
            }
          }

          if (!serverOk) {
            rollbackAction();
            rollbackAction = null;
            showToast("가격 알림 해제에 실패했어요. 잠시 후 다시 시도해 주세요");
            return;
          }

          rollbackAction = null;
          if (typeof window !== "undefined" && prevTargetPriceVal !== undefined) {
            try {
              const mapStr = localStorage.getItem("insightdeal_target_prices");
              const targetMap = mapStr ? JSON.parse(mapStr) : {};
              delete targetMap[id];
              localStorage.setItem(
                "insightdeal_target_prices",
                JSON.stringify(targetMap)
              );
            } catch {}
          }
          showToast("가격 알림을 껐어요");
        } else {
          const pushRes = await ensureWebPushSubscribed();
          const dealCurr = (targetDeal?.currency || "KRW").toUpperCase();
          const isSubunitCurrency =
            dealCurr === "USD" || dealCurr === "EUR" || dealCurr === "CNY";
          const rawPrice = targetDeal
            ? parseInt(String(targetDeal.price || "").replace(/[^0-9]/g, ""), 10) ||
              0
            : 0;
          const autoTarget = isSubunitCurrency
            ? rawPrice >= 500
              ? Math.max(100, Math.floor((rawPrice / 100) * 0.95) * 100)
              : Math.round(rawPrice * 0.95)
            : rawPrice >= 1000
              ? Math.max(100, Math.round((rawPrice * 0.95) / 100) * 100)
              : rawPrice;

          if (!targetDeal || targetDeal.isFree || autoTarget <= 0) {
            showToast("가격 정보가 확인된 상품만 목표가 알림을 켤 수 있어요");
            return;
          }

          const postRes = await authFetch((latestAuth) => ({
            pathOrUrl: "/api/wishlist/item-alert",
            init: {
              method: "POST",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify({
                user_id: latestAuth.deviceUuid,
                keyword: keyword || decodeHtmlEntities(targetDeal.title).slice(0, 40),
                title: decodeHtmlEntities(targetDeal.title),
                product_url: targetDeal.ecommerce_url || targetDeal.post_link,
                target_price: autoTarget,
              }),
            },
          }));

          if (!postRes.ok) {
            showToast("가격 알림 설정에 실패했어요. 잠시 후 다시 시도해 주세요");
            return;
          }

          const saved = (await postRes.json().catch(() => null)) as
            | WishlistApiItem
            | null;
          if (saved && typeof saved.id === "number") {
            saveWishlistIdEntry(id, saved.id, saved.keyword || keyword);
          }

          const wasUnwishlistedBefore = unwishlistedDealIdsRef.current.has(id);
          const wasAlertedBefore = alertsRef.current.includes(id);
          rollbackAction = () => {
            if (!wasUnwishlistedBefore) {
              unwishlistedDealIdsRef.current.delete(id);
            }
            if (!wasAlertedBefore) {
              applyAlertRemove(id);
            }
          };

          if (!wishlistRef.current.includes(id)) {
            unwishlistedDealIdsRef.current.add(id);
          }
          applyAlertAdd(id);
          if (typeof window !== "undefined") {
            try {
              const mapStr = localStorage.getItem("insightdeal_target_prices");
              const targetMap = mapStr ? JSON.parse(mapStr) : {};
              targetMap[id] = isSubunitCurrency
                ? Math.max(1, Math.round(autoTarget / 100))
                : autoTarget;
              localStorage.setItem(
                "insightdeal_target_prices",
                JSON.stringify(targetMap)
              );
            } catch {}
          }

          rollbackAction = null;
          const priceFormatted = formatPrice(
            autoTarget,
            targetDeal.currency,
            targetDeal.title
          );
          showToast(
            pushRes.pushGranted
              ? `${priceFormatted} 이하로 내려가면 알려드릴게요`
              : `${priceFormatted} 이하 목표가 알림을 켰어요. 알림함에서 확인할 수 있어요`
          );
          logTossClick("save_target_price_alert", {
            button_name: "card_price_alert_button",
            deal_id: id,
          });
          maybeRequestTossReview();
        }
      } catch {
        rollbackAction?.();
        showToast(
          isTurningOff
            ? "가격 알림 해제에 실패했어요. 잠시 후 다시 시도해 주세요"
            : "가격 알림 설정에 실패했어요. 잠시 후 다시 시도해 주세요"
        );
      } finally {
        pendingAlertRef.current.delete(id);
      }
    });
  };

  const handleLoginSimulate = async () => {
    if (typeof window === "undefined") return;
    try {
      await ensureWebDeviceAuth();
      setIsLoggedIn(true);
      await hydrateFromServer();
      showToast("기기 연동을 완료했어요");
    } catch {
      showToast("기기 연동에 실패했어요. 잠시 후 다시 시도해 주세요");
    }
  };

  const handleRemoveServerWishlist = async (wishlistId: number) => {
    hydrateEpochRef.current += 1;
    try {
      await ensureWebDeviceAuth();
      const res = await authFetch(
        (latestAuth) =>
          `/api/wishlist/${wishlistId}?user_id=${encodeURIComponent(latestAuth.deviceUuid)}`,
        { method: "DELETE" }
      );
      if (!res.ok && res.status !== 404) {
        showToast("찜 해제에 실패했어요. 잠시 후 다시 시도해 주세요");
        return;
      }
      const idMap = loadWishlistIdMap();
      for (const [keyStr, entry] of Object.entries(idMap)) {
        if (entry.wishlistId === wishlistId) {
          const parsedId = Number(keyStr.split("_")[0]);
          if (Number.isFinite(parsedId)) {
            unwishlistedDealIdsRef.current.add(parsedId);
            removeWishlistIdEntry(parsedId);
            if (wishlistRef.current.includes(parsedId)) {
              applyWishlistRemove(parsedId);
            }
          }
        }
      }
      serverWishlistsRef.current = serverWishlistsRef.current.filter(
        (item) => item.id !== wishlistId
      );
      setServerWishlists((prev) => prev.filter((item) => item.id !== wishlistId));
      showToast("찜 목록에서 뺐어요");
    } catch {
      showToast("찜 해제에 실패했어요. 잠시 후 다시 시도해 주세요");
    }
  };

  const handleDealClosed = useCallback(
    (dealId: number, isClosed: boolean, reportCount: number) => {
      feedCacheRef.current.clear();
      setDeals((prev) =>
        prev.map((d) =>
          d.id === dealId ? { ...d, isClosed, reportCount } : d
        )
      );
      setSelectedDeal((prev) =>
        prev && prev.id === dealId ? { ...prev, isClosed, reportCount } : prev
      );
    },
    []
  );

  const openDealWithHistory = useCallback((deal: Deal) => {
    setSelectedDeal(deal);
    setRecentDeals((prev) => {
      const next = [deal, ...prev.filter((item) => item.id !== deal.id)].slice(0, 30);
      if (typeof window !== "undefined") {
        try {
          localStorage.setItem(RECENT_DEALS_STORAGE_KEY, JSON.stringify(next));
        } catch {}
      }
      return next;
    });
  }, []);

  const handleHideDeal = useCallback(
    (dealId: number) => {
      setHiddenDealIds((prev) => {
        if (prev.includes(dealId)) return prev;
        const next = [...prev, dealId].slice(-200);
        if (typeof window !== "undefined") {
          try {
            localStorage.setItem(HIDDEN_DEALS_STORAGE_KEY, JSON.stringify(next));
          } catch {}
        }
        return next;
      });
      showToast("해당 핫딜을 피드에서 숨겼어요");
    },
    [showToast]
  );

  const handleUnhideDeal = useCallback((dealId: number) => {
    setHiddenDealIds((prev) => {
      const next = prev.filter((id) => id !== dealId);
      if (typeof window !== "undefined") {
        try {
          localStorage.setItem(HIDDEN_DEALS_STORAGE_KEY, JSON.stringify(next));
        } catch {}
      }
      return next;
    });
  }, []);

  const handleAddMutedKeyword = useCallback(
    (kw: string) => {
      const trimmed = kw.trim();
      if (!trimmed) return;
      setMutedKeywords((prev) => {
        if (prev.some((item) => item.toLowerCase() === trimmed.toLowerCase())) {
          return prev;
        }
        const next = [trimmed, ...prev].slice(0, 50);
        if (typeof window !== "undefined") {
          try {
            localStorage.setItem(MUTED_KEYWORDS_STORAGE_KEY, JSON.stringify(next));
          } catch {}
        }
        return next;
      });
      setMuteKeywordInput("");
      showToast(`'${trimmed}' 포함 핫딜을 숨겼어요`);
    },
    [showToast]
  );

  const handleRemoveMutedKeyword = useCallback((kw: string) => {
    setMutedKeywords((prev) => {
      const next = prev.filter((item) => item !== kw);
      if (typeof window !== "undefined") {
        try {
          localStorage.setItem(MUTED_KEYWORDS_STORAGE_KEY, JSON.stringify(next));
        } catch {}
      }
      return next;
    });
  }, []);

  const fetchLiveDeals = useCallback(
    async (
      showLoadingState = true,
      offset = 0,
      targetCategories: string[] = selectedCategoriesRef.current,
      targetPlatforms: string[] = selectedPlatformsRef.current,
      overrideKeyword?: string,
      overrideSort?: FeedSortKey,
      overrides?: {
        includeClosed?: boolean;
        onlyHot?: boolean;
        hideClosed?: boolean;
        freeShipping?: boolean;
        shop?: string;
        minPrice?: string;
        maxPrice?: string;
        bypassCache?: boolean;
        cursor?: NextCursor | null;
      }
    ): Promise<boolean> => {
      if (liveDealsAbortRef.current) {
        liveDealsAbortRef.current.abort();
      }
      const controller = new AbortController();
      liveDealsAbortRef.current = controller;
      const { signal } = controller;

      const normalizedCategories = targetCategories
        .map((c) => c.trim())
        .filter((c) => c && c !== "전체");
      const normalizedPlatforms = targetPlatforms
        .map((p) => p.trim())
        .filter((p) => p && p !== "전체");

      try {
        if (offset === 0) {
          setFetchError(null);
          nextCursorRef.current = null;
        }

        const apiCategory =
          normalizedCategories.length > 0 ? normalizedCategories.join(",") : "";
        const platformParam =
          normalizedPlatforms.length > 0
            ? `&platform=${encodeURIComponent(normalizedPlatforms.join(","))}`
            : "";
        const limit = normalizedPlatforms.includes("뽐뿌") ? 50 : 20;

        const activeKw =
          overrideKeyword !== undefined
            ? overrideKeyword
            : appliedSearchRef.current;
        const activeSort = overrideSort ?? sortByRef.current;
        const activeOnlyHot = overrides?.onlyHot ?? onlyHotDealsRef.current;
        const activeHideClosed =
          overrides?.hideClosed ?? hideClosedDealsRef.current;
        const activeIncludeClosed =
          overrides?.includeClosed ?? includeClosedDealsRef.current;
        const activeFreeShip =
          overrides?.freeShipping ?? freeShippingOnlyRef.current;
        const activeShop = (overrides?.shop ?? shopFilterRef.current).trim();
        const activeMinPrice = (overrides?.minPrice ?? minPriceRef.current).replace(/[^0-9]/g, "");
        const activeMaxPrice = (overrides?.maxPrice ?? maxPriceRef.current).replace(/[^0-9]/g, "");
        const activeCursor =
          offset > 0 ? (overrides?.cursor ?? nextCursorRef.current) : null;

        const cacheKey = `${apiCategory}|${normalizedPlatforms.join(",")}|${activeKw.trim()}|${activeSort}|${activeOnlyHot ? 1 : 0}|${activeHideClosed ? 1 : 0}|${activeIncludeClosed ? 1 : 0}|${activeFreeShip ? 1 : 0}|${activeShop}|${activeMinPrice}|${activeMaxPrice}`;

        let servedStaleCache = false;
        if (offset === 0 && !overrides?.bypassCache) {
          const cached = feedCacheRef.current.get(cacheKey);
          if (cached) {
            const ageMs = Date.now() - cached.cachedAt;
            if (ageMs <= FEED_SWR_FRESH_MS) {
              dealsRef.current = cached.deals;
              setDeals(cached.deals);
              setHasMore(cached.hasMore);
              nextCursorRef.current = cached.nextCursor;
              setLoading(false);
              setFetchError(null);
              return true;
            }
            if (ageMs <= FEED_SWR_STALE_MS && cached.deals.length > 0) {
              dealsRef.current = cached.deals;
              setDeals(cached.deals);
              setHasMore(cached.hasMore);
              nextCursorRef.current = cached.nextCursor;
              setLoading(false);
              servedStaleCache = true;
            }
          }
        }

        if (showLoadingState && !servedStaleCache) {
          setLoading(true);
        }

        const queryParam = activeKw.trim()
          ? `&keyword=${encodeURIComponent(activeKw.trim())}`
          : "";
        const sortParam = `&sort=${encodeURIComponent(activeSort)}`;
        const filterParams = `${activeOnlyHot ? "&only_hot=true" : ""}${
          activeHideClosed ? "&hide_closed=true" : ""
        }${activeIncludeClosed ? "&include_closed=true" : ""}${
          activeFreeShip ? "&free_shipping=true" : ""
        }${activeShop ? `&shop=${encodeURIComponent(activeShop)}` : ""}${
          activeMinPrice ? `&min_price=${encodeURIComponent(activeMinPrice)}` : ""
        }${activeMaxPrice ? `&max_price=${encodeURIComponent(activeMaxPrice)}` : ""}`;
        const cursorParams =
          activeCursor && activeCursor.indexed_at && Number.isFinite(activeCursor.id)
            ? `&cursor_indexed_at=${encodeURIComponent(
                activeCursor.indexed_at
              )}&cursor_id=${activeCursor.id}`
            : "";

        const url = apiUrl(
          `/api/community/hot-deals?limit=${limit}&offset=${offset}${
            apiCategory ? `&category=${encodeURIComponent(apiCategory)}` : ""
          }${platformParam}${queryParam}${sortParam}${filterParams}${cursorParams}`
        );

        let res: Response | null = null;
        let lastStatus = 0;
        for (let attempt = 0; attempt < 3; attempt++) {
          if (signal.aborted) return false;
          const attemptController = new AbortController();
          let timedOut = false;
          const onOuterAbort = () => attemptController.abort();
          signal.addEventListener("abort", onOuterAbort, { once: true });
          const timeoutId = setTimeout(() => {
            timedOut = true;
            attemptController.abort();
          }, 8000);
          try {
            const r = await fetch(url, { signal: attemptController.signal });
            lastStatus = r.status;
            if (r.ok) {
              res = r;
              break;
            }
            if (r.status >= 400 && r.status < 500) {
              break;
            }
          } catch (fetchErr) {
            if (signal.aborted) {
              return false;
            }
            if (!timedOut && isAbortError(fetchErr)) {
              return false;
            }
          } finally {
            clearTimeout(timeoutId);
            signal.removeEventListener("abort", onOuterAbort);
          }
          if (attempt < 2) {
            await new Promise<void>((resolve) => {
              const retryTimer = setTimeout(resolve, 600 * (attempt + 1));
              signal.addEventListener(
                "abort",
                () => {
                  clearTimeout(retryTimer);
                  resolve();
                },
                { once: true }
              );
            });
            if (signal.aborted) return false;
          }
        }

        if (signal.aborted || liveDealsAbortRef.current !== controller) {
          return false;
        }
        if (!res || !res.ok) {
          throw new Error(`API Offline (${lastStatus || "network"})`);
        }

        const data = (await res.json()) as
          | RawDealApiResponse[]
          | HotDealsApiResponse;
        if (signal.aborted || liveDealsAbortRef.current !== controller) {
          return false;
        }

        const dealsArray: RawDealApiResponse[] = Array.isArray(data)
          ? data
          : data.deals || data.items || [];

        const formatted: Deal[] = dealsArray.map((d: RawDealApiResponse) =>
          mapRawDealToDeal(d, normalizedPlatforms)
        );

        if (offset === 0 && activeKw.trim().length >= 2) {
          logTossClick("search_deals", {
            button_name: "search_input",
            result_count: formatted.length,
          });
        }

        const explicitHasMore =
          !Array.isArray(data) && typeof data?.has_more === "boolean"
            ? data.has_more
            : !Array.isArray(data) && typeof data?.hasMore === "boolean"
              ? data.hasMore
              : undefined;

        const parsedCursor = !Array.isArray(data)
          ? data?.next_cursor ?? data?.nextCursor ?? null
          : null;
        nextCursorRef.current =
          parsedCursor &&
          typeof parsedCursor.indexed_at === "string" &&
          Number.isFinite(parsedCursor.id)
            ? parsedCursor
            : null;

        if (formatted.length > 0) {
          let mergedDealsForRef: Deal[];
          if (offset === 0) {
            mergedDealsForRef = [...formatted];
            dealsRef.current = mergedDealsForRef;
            setDeals(mergedDealsForRef);
            const resolvedHasMore =
              explicitHasMore !== undefined
                ? explicitHasMore
                : formatted.length >= limit;
            if (feedCacheRef.current.size >= 24) {
              const oldest = feedCacheRef.current.keys().next().value;
              if (oldest !== undefined) feedCacheRef.current.delete(oldest);
            }
            feedCacheRef.current.set(cacheKey, {
              deals: mergedDealsForRef,
              hasMore: resolvedHasMore,
              nextCursor: nextCursorRef.current,
              cachedAt: Date.now(),
            });
            if (
              typeof window !== "undefined" &&
              normalizedCategories.length === 0 &&
              normalizedPlatforms.length === 0 &&
              !activeKw.trim() &&
              activeSort === "latest" &&
              !activeOnlyHot &&
              !activeHideClosed &&
              !activeIncludeClosed &&
              !activeFreeShip &&
              !activeShop &&
              !activeMinPrice &&
              !activeMaxPrice
            ) {
              localStorage.setItem(
                "insightdeal_cached_deals",
                JSON.stringify(formatted)
              );
            }
          } else {
            const existingIds = new Set(dealsRef.current.map((item) => item.id));
            const newDeals = formatted.filter(
              (item: Deal) => !existingIds.has(item.id)
            );
            mergedDealsForRef = [...dealsRef.current, ...newDeals];
            dealsRef.current = mergedDealsForRef;
            setDeals((prev) => {
              const prevIds = new Set(prev.map((item) => item.id));
              const appended = formatted.filter(
                (item: Deal) => !prevIds.has(item.id)
              );
              return [...prev, ...appended];
            });
          }

          if (serverWishlistsRef.current.length > 0) {
            reconcileWishlistsWithDeals(
              mergedDealsForRef,
              serverWishlistsRef.current
            );
          }

          setHasMore(
            explicitHasMore !== undefined
              ? explicitHasMore
              : formatted.length >= limit
          );
        } else {
          if (offset === 0) {
            dealsRef.current = [];
            setDeals([]);
          }
          setHasMore(explicitHasMore !== undefined ? explicitHasMore : false);
        }

        setFetchError(null);
        return true;
      } catch (err) {
        if (
          signal.aborted ||
          isAbortError(err) ||
          liveDealsAbortRef.current !== controller
        ) {
          return false;
        }
        const activeKwOnErr =
          overrideKeyword !== undefined
            ? overrideKeyword
            : appliedSearchRef.current;
        const activeSortOnErr = overrideSort ?? sortByRef.current;

        if (offset === 0) {
          setFetchError(HOT_DEALS_ERROR_COPY);
          if (
            typeof window !== "undefined" &&
            !activeKwOnErr.trim() &&
            normalizedCategories.length === 0 &&
            normalizedPlatforms.length === 0 &&
            activeSortOnErr === "latest" &&
            !onlyHotDealsRef.current &&
            !hideClosedDealsRef.current &&
            !includeClosedDealsRef.current &&
            !freeShippingOnlyRef.current &&
            !shopFilterRef.current &&
            !minPriceRef.current &&
            !maxPriceRef.current
          ) {
            try {
              const cached = localStorage.getItem("insightdeal_cached_deals");
              if (cached) {
                const parsed = JSON.parse(cached);
                if (Array.isArray(parsed) && parsed.length > 0) {
                  dealsRef.current = parsed;
                  setDeals(parsed);
                }
              }
            } catch {}
          } else {
            dealsRef.current = [];
            setDeals([]);
          }
          setHasMore(false);
        } else {
          showToast("추가 딜을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.");
        }
        return false;
      } finally {
        if (liveDealsAbortRef.current === controller) {
          liveDealsAbortRef.current = null;
          if (showLoadingState || offset === 0) {
            setLoading(false);
          }
        }
      }
    },
    [reconcileWishlistsWithDeals, showToast]
  );

  const handleRefresh = async () => {
    if (isRefreshing) return;
    setIsRefreshing(true);
    setNewDealsCount(0);
    feedCacheRef.current.clear();
    const ok = await fetchLiveDeals(
      false,
      0,
      selectedCategoriesRef.current,
      selectedPlatformsRef.current,
      undefined,
      undefined,
      { bypassCache: true }
    );
    setIsRefreshing(false);
    showToast(ok ? "최신 딜을 불러왔어요" : HOT_DEALS_ERROR_COPY);
  };

  const handleScrollToTopAndRefresh = async () => {
    if (typeof window !== "undefined") {
      window.scrollTo({ top: 0, behavior: "smooth" });
    }
    setNewDealsCount(0);
    setIsRefreshing(true);
    feedCacheRef.current.clear();
    const ok = await fetchLiveDeals(
      false,
      0,
      selectedCategoriesRef.current,
      selectedPlatformsRef.current,
      undefined,
      undefined,
      { bypassCache: true }
    );
    setIsRefreshing(false);
    showToast(ok ? "최신 딜을 반영했어요" : HOT_DEALS_ERROR_COPY);
  };

  const handleCategoryToggle = (catKey: string) => {
    clearSearchDebounce();
    let next: string[];
    if (catKey === "전체") {
      if (selectedCategoriesRef.current.length === 0) return;
      next = [];
    } else {
      const current = selectedCategoriesRef.current;
      if (current.includes(catKey)) {
        next = current.filter((k) => k !== catKey);
      } else {
        const added = [...current, catKey];
        const individualCatKeys = categories
          .filter((c) => c.key !== "전체")
          .map((c) => c.key);
        next =
          individualCatKeys.length > 0 &&
          individualCatKeys.every((k) => added.includes(k))
            ? []
            : added;
      }
    }
    selectedCategoriesRef.current = next;
    setSelectedCategories(next);
    setNewDealsCount(0);
    setIsFetchingMore(false);
    dealsRef.current = [];
    setDeals([]);
    setHasMore(true);
    void fetchLiveDeals(true, 0, next, selectedPlatformsRef.current);
  };

  const handlePlatformToggle = (platform: string) => {
    clearSearchDebounce();
    let next: string[];
    if (platform === "전체") {
      if (selectedPlatformsRef.current.length === 0) return;
      next = [];
    } else {
      const current = selectedPlatformsRef.current;
      if (current.includes(platform)) {
        next = current.filter((p) => p !== platform);
      } else {
        const added = [...current, platform];
        const individualPlatforms = PLATFORMS.filter((p) => p !== "전체");
        next =
          individualPlatforms.length > 0 &&
          individualPlatforms.every((p) => added.includes(p))
            ? []
            : added;
      }
    }
    selectedPlatformsRef.current = next;
    setSelectedPlatforms(next);
    setNewDealsCount(0);
    setIsFetchingMore(false);
    dealsRef.current = [];
    setDeals([]);
    setHasMore(true);
    void fetchLiveDeals(true, 0, selectedCategoriesRef.current, next);
  };

  const saveRecentSearch = (rawKw: string) => {
    const trimmed = rawKw.trim();
    if (!trimmed) return;
    setRecentSearches((prev) => {
      const next = [trimmed, ...prev.filter((item) => item !== trimmed)].slice(
        0,
        5
      );
      if (typeof window !== "undefined") {
        localStorage.setItem(
          "insightdeal_recent_searches",
          JSON.stringify(next)
        );
      }
      return next;
    });
  };

  const removeRecentSearch = (kw: string) => {
    setRecentSearches((prev) => {
      const next = prev.filter((item) => item !== kw);
      if (typeof window !== "undefined") {
        localStorage.setItem(
          "insightdeal_recent_searches",
          JSON.stringify(next)
        );
      }
      return next;
    });
  };

  const clearRecentSearches = () => {
    setRecentSearches([]);
    if (typeof window !== "undefined") {
      localStorage.removeItem("insightdeal_recent_searches");
    }
  };

  const isAutoSearchableQuery = (raw: string): boolean => {
    const trimmed = raw.trim();
    if (!trimmed) return true;
    // 미완성 한글 자모만 있거나 1글자 영문, 연산자 기호만 입력 중일 때는 자동 검색을 보류하여 빈 화면 깜빡임 방지
    if (/^[ㄱ-ㅎㅏ-ㅣ]+$/.test(trimmed)) return false;
    if (/^[a-zA-Z]$/.test(trimmed)) return false;
    if (/^[-:]+$/.test(trimmed)) return false;
    return true;
  };

  const scheduleDebouncedSearch = (nextValue: string) => {
    clearSearchDebounce();
    if (isComposingRef.current) return;
    if (!isAutoSearchableQuery(nextValue)) return;

    searchDebounceRef.current = setTimeout(() => {
      searchDebounceRef.current = null;
      const trimmed = nextValue.trim();
      if (appliedSearchRef.current === trimmed) return;
      updateAppliedSearch(trimmed);
      setIsFetchingMore(false);
      dealsRef.current = [];
      setDeals([]);
      setHasMore(true);
      void fetchLiveDeals(
        true,
        0,
        selectedCategoriesRef.current,
        selectedPlatformsRef.current,
        trimmed
      );
    }, 300);
  };

  const handleClearSearch = () => {
    clearSearchDebounce();
    isComposingRef.current = false;
    setSearchQuery("");
    updateAppliedSearch("");
    setIsFetchingMore(false);
    dealsRef.current = [];
    setDeals([]);
    setHasMore(true);
    void fetchLiveDeals(
      true,
      0,
      selectedCategoriesRef.current,
      selectedPlatformsRef.current,
      ""
    );
  };

  const handleSortChange = (nextSort: FeedSortKey) => {
    if (sortByRef.current === nextSort) return;
    sortByRef.current = nextSort;
    setSortBy(nextSort);
    setIsFetchingMore(false);
    dealsRef.current = [];
    setDeals([]);
    setHasMore(true);
    void fetchLiveDeals(
      true,
      0,
      selectedCategoriesRef.current,
      selectedPlatformsRef.current,
      undefined,
      nextSort
    );
  };

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    clearSearchDebounce();
    isComposingRef.current = false;
    const trimmed = searchQuery.trim();
    if (trimmed) {
      saveRecentSearch(trimmed);
    }
    updateAppliedSearch(trimmed);
    setIsFetchingMore(false);
    dealsRef.current = [];
    setDeals([]);
    setHasMore(true);
    void fetchLiveDeals(
      true,
      0,
      selectedCategoriesRef.current,
      selectedPlatformsRef.current,
      trimmed
    );
  };

  const handleSelectPopularKeyword = (kw: string) => {
    clearSearchDebounce();
    isComposingRef.current = false;
    const trimmed = kw.trim();
    if (appliedSearchRef.current === trimmed) {
      setSearchQuery("");
      updateAppliedSearch("");
      setIsFetchingMore(false);
      dealsRef.current = [];
      setDeals([]);
      setHasMore(true);
      void fetchLiveDeals(
        true,
        0,
        selectedCategoriesRef.current,
        selectedPlatformsRef.current,
        ""
      );
      return;
    }
    setSearchQuery(trimmed);
    updateAppliedSearch(trimmed);
    saveRecentSearch(trimmed);
    setIsFetchingMore(false);
    dealsRef.current = [];
    setDeals([]);
    setHasMore(true);
    void fetchLiveDeals(
      true,
      0,
      selectedCategoriesRef.current,
      selectedPlatformsRef.current,
      trimmed
    );
  };

  const handleSelectRecentKeyword = (kw: string) => {
    clearSearchDebounce();
    isComposingRef.current = false;
    const trimmed = kw.trim();
    if (!trimmed) return;
    setSearchQuery(trimmed);
    updateAppliedSearch(trimmed);
    saveRecentSearch(trimmed);
    setIsFetchingMore(false);
    dealsRef.current = [];
    setDeals([]);
    setHasMore(true);
    void fetchLiveDeals(
      true,
      0,
      selectedCategoriesRef.current,
      selectedPlatformsRef.current,
      trimmed
    );
  };

  const handleToggleOnlyHot = () => {
    const next = !onlyHotDealsRef.current;
    onlyHotDealsRef.current = next;
    setOnlyHotDeals(next);
    setIsFetchingMore(false);
    setHasMore(true);
    void fetchLiveDeals(
      true,
      0,
      selectedCategoriesRef.current,
      selectedPlatformsRef.current,
      undefined,
      undefined,
      {
        onlyHot: next,
      }
    );
  };

  const handleToggleHideClosed = () => {
    const next = !hideClosedDealsRef.current;
    hideClosedDealsRef.current = next;
    setHideClosedDeals(next);
    let nextInclude = includeClosedDealsRef.current;
    if (next && nextInclude) {
      nextInclude = false;
      includeClosedDealsRef.current = false;
      setIncludeClosedDeals(false);
    }
    setIsFetchingMore(false);
    setHasMore(true);
    void fetchLiveDeals(
      true,
      0,
      selectedCategoriesRef.current,
      selectedPlatformsRef.current,
      undefined,
      undefined,
      {
        hideClosed: next,
        includeClosed: nextInclude,
      }
    );
  };

  const handleToggleIncludeClosed = () => {
    const next = !includeClosedDealsRef.current;
    includeClosedDealsRef.current = next;
    setIncludeClosedDeals(next);
    let nextHide = hideClosedDealsRef.current;
    if (next && nextHide) {
      nextHide = false;
      hideClosedDealsRef.current = false;
      setHideClosedDeals(false);
    }
    setIsFetchingMore(false);
    setHasMore(true);
    void fetchLiveDeals(
      true,
      0,
      selectedCategoriesRef.current,
      selectedPlatformsRef.current,
      undefined,
      undefined,
      {
        includeClosed: next,
        hideClosed: nextHide,
      }
    );
  };

  const handleToggleFreeShipping = () => {
    const next = !freeShippingOnlyRef.current;
    freeShippingOnlyRef.current = next;
    setFreeShippingOnly(next);
    setIsFetchingMore(false);
    dealsRef.current = [];
    setDeals([]);
    setHasMore(true);
    void fetchLiveDeals(
      true,
      0,
      selectedCategoriesRef.current,
      selectedPlatformsRef.current,
      undefined,
      undefined,
      { freeShipping: next }
    );
  };

  const handleShopFilterChange = (nextShop: string) => {
    const clean = nextShop.trim();
    shopFilterRef.current = clean;
    setShopFilter(clean);
    setIsFetchingMore(false);
    dealsRef.current = [];
    setDeals([]);
    setHasMore(true);
    void fetchLiveDeals(
      true,
      0,
      selectedCategoriesRef.current,
      selectedPlatformsRef.current,
      undefined,
      undefined,
      { shop: clean }
    );
  };

  const handleApplyPriceRange = (rawMin: string, rawMax: string) => {
    const cleanMin = rawMin.replace(/[^0-9]/g, "");
    const cleanMax = rawMax.replace(/[^0-9]/g, "");
    minPriceRef.current = cleanMin;
    maxPriceRef.current = cleanMax;
    setMinPriceInput(cleanMin);
    setMaxPriceInput(cleanMax);
    setIsFetchingMore(false);
    dealsRef.current = [];
    setDeals([]);
    setHasMore(true);
    void fetchLiveDeals(
      true,
      0,
      selectedCategoriesRef.current,
      selectedPlatformsRef.current,
      undefined,
      undefined,
      { minPrice: cleanMin, maxPrice: cleanMax }
    );
  };

  const handleViewModeChange = (nextMode: "card" | "compact") => {
    setViewMode(nextMode);
    if (typeof window !== "undefined") {
      try {
        localStorage.setItem("insightdeal_view_mode", nextMode);
      } catch {}
    }
  };

  const handleResetAllFilters = () => {
    clearSearchDebounce();
    isComposingRef.current = false;
    setSearchQuery("");
    updateAppliedSearch("");
    selectedCategoriesRef.current = [];
    setSelectedCategories([]);
    selectedPlatformsRef.current = [];
    setSelectedPlatforms([]);
    onlyHotDealsRef.current = false;
    setOnlyHotDeals(false);
    hideClosedDealsRef.current = false;
    setHideClosedDeals(false);
    includeClosedDealsRef.current = false;
    setIncludeClosedDeals(false);
    freeShippingOnlyRef.current = false;
    setFreeShippingOnly(false);
    shopFilterRef.current = "";
    setShopFilter("");
    minPriceRef.current = "";
    maxPriceRef.current = "";
    setMinPriceInput("");
    setMaxPriceInput("");
    sortByRef.current = "latest";
    setSortBy("latest");
    feedCacheRef.current.clear();
    setIsFetchingMore(false);
    dealsRef.current = [];
    setDeals([]);
    setHasMore(true);
    void fetchLiveDeals(true, 0, [], [], "", "latest", {
      onlyHot: false,
      hideClosed: false,
      includeClosed: false,
      freeShipping: false,
      shop: "",
      minPrice: "",
      maxPrice: "",
      bypassCache: true,
    });
    showToast("필터를 초기화했어요");
  };

  const hasActiveFilters =
    appliedSearch !== "" ||
    selectedPlatforms.length > 0 ||
    selectedCategories.length > 0 ||
    onlyHotDeals ||
    hideClosedDeals ||
    includeClosedDeals ||
    freeShippingOnly ||
    shopFilter !== "" ||
    minPriceInput !== "" ||
    maxPriceInput !== "" ||
    sortBy !== "latest";

  const handleCloseDealModal = useCallback(() => {
    setSelectedDeal(null);
  }, []);

  const handleTargetAlertSaved = useCallback(
    (dealId: number) => {
      hydrateEpochRef.current += 1;
      if (!wishlistRef.current.includes(dealId)) {
        unwishlistedDealIdsRef.current.add(dealId);
      }
      applyAlertAdd(dealId);
    },
    [applyAlertAdd]
  );

  useEffect(() => {
    setIsMounted(true);
    setCurrentTime(Date.now());

    const handleBeforeInstallPrompt = (e: Event) => {
      e.preventDefault();
      setDeferredPrompt(e as BeforeInstallPromptEvent);
    };

    const handleGlobalKeyDown = (e: KeyboardEvent) => {
      if (e.key !== "/" || e.metaKey || e.ctrlKey || e.altKey) return;
      const tgt = e.target as HTMLElement | null;
      const tag = tgt?.tagName?.toLowerCase();
      if (
        tag === "input" ||
        tag === "textarea" ||
        tag === "select" ||
        tgt?.isContentEditable
      ) {
        return;
      }
      if (searchInputRef.current) {
        e.preventDefault();
        searchInputRef.current.focus();
      }
    };

    let initialSharedKw = "";
    let initialPlatforms: string[] = [];
    let initialCategories: string[] = [];
    let initialSort: FeedSortKey = "latest";
    let initialOnlyHot = false;
    let initialHideClosed = false;
    let initialIncludeClosed = false;
    let initialFreeShipping = false;
    let initialShop = "";
    let initialMinPrice = "";
    let initialMaxPrice = "";
    let initialDealId: number | null = null;
    if (typeof window !== "undefined") {
      window.addEventListener("beforeinstallprompt", handleBeforeInstallPrompt);
      window.addEventListener("keydown", handleGlobalKeyDown);

      const urlParams = new URLSearchParams(window.location.search);
      const sharedKw = urlParams.get("keyword");
      if (sharedKw && sharedKw.trim()) {
        initialSharedKw = sharedKw.trim();
        setSearchQuery(initialSharedKw);
        updateAppliedSearch(initialSharedKw);
      }

      const platformRaw = urlParams.get("platform");
      if (platformRaw && platformRaw.trim()) {
        const allowedPlatforms = new Set<string>(
          PLATFORMS.filter((p) => p !== "전체")
        );
        initialPlatforms = Array.from(
          new Set(
            platformRaw
              .split(",")
              .map((p) => p.trim())
              .filter((p) => allowedPlatforms.has(p))
          )
        );
        if (initialPlatforms.length > 0) {
          selectedPlatformsRef.current = initialPlatforms;
          setSelectedPlatforms(initialPlatforms);
        }
      }

      const categoryRaw = urlParams.get("category");
      if (categoryRaw && categoryRaw.trim()) {
        initialCategories = Array.from(
          new Set(
            categoryRaw
              .split(",")
              .map((c) => c.trim())
              .map((c) => (c.includes("해외핫딜") ? "해외직구" : c))
              .filter((c) => c && c !== "전체")
          )
        );
        if (initialCategories.length > 0) {
          selectedCategoriesRef.current = initialCategories;
          setSelectedCategories(initialCategories);
        }
      }

      const sortRaw = urlParams.get("sort");
      if (
        sortRaw === "popular" ||
        sortRaw === "price_asc" ||
        sortRaw === "price_desc" ||
        sortRaw === "comments" ||
        sortRaw === "recommendations" ||
        sortRaw === "latest"
      ) {
        initialSort = sortRaw;
        sortByRef.current = sortRaw;
        setSortBy(sortRaw);
      }

      const freeShipRaw = urlParams.get("freeShipping");
      if (freeShipRaw === "1" || freeShipRaw === "true") {
        initialFreeShipping = true;
        freeShippingOnlyRef.current = true;
        setFreeShippingOnly(true);
      }

      const shopRaw = urlParams.get("shop");
      if (shopRaw && shopRaw.trim()) {
        initialShop = shopRaw.trim();
        shopFilterRef.current = initialShop;
        setShopFilter(initialShop);
      }

      const minPRaw = (urlParams.get("minPrice") || "").replace(/[^0-9]/g, "");
      if (minPRaw) {
        initialMinPrice = minPRaw;
        minPriceRef.current = minPRaw;
        setMinPriceInput(minPRaw);
      }

      const maxPRaw = (urlParams.get("maxPrice") || "").replace(/[^0-9]/g, "");
      if (maxPRaw) {
        initialMaxPrice = maxPRaw;
        maxPriceRef.current = maxPRaw;
        setMaxPriceInput(maxPRaw);
      }

      const hotRaw = urlParams.get("hot") ?? urlParams.get("onlyHot");
      if (hotRaw === "1" || hotRaw === "true") {
        initialOnlyHot = true;
        onlyHotDealsRef.current = true;
        setOnlyHotDeals(true);
      }

      const hideRaw = urlParams.get("hideClosed");
      if (hideRaw === "1" || hideRaw === "true") {
        initialHideClosed = true;
        hideClosedDealsRef.current = true;
        setHideClosedDeals(true);
      } else {
        const incRaw = urlParams.get("includeClosed");
        if (incRaw === "1" || incRaw === "true") {
          initialIncludeClosed = true;
          includeClosedDealsRef.current = true;
          setIncludeClosedDeals(true);
        }
      }

      const dealIdRaw = urlParams.get("dealId") ?? urlParams.get("deal");
      if (dealIdRaw && /^\d+$/.test(dealIdRaw.trim())) {
        const parsedId = Number(dealIdRaw.trim());
        if (Number.isSafeInteger(parsedId) && parsedId > 0) {
          initialDealId = parsedId;
        }
      }

      const ua = navigator.userAgent || "";
      if (
        hasTossBridge() ||
        ua.includes("TossApp") ||
        window.location.hostname.includes("toss") ||
        urlParams.get("toss") === "1"
      ) {
        setIsTossMiniApp(true);
      }
      logTossScreen("home_feed_view", { tab: "전체" });
      urlSyncReadyRef.current = true;
    }

    // 15종 표준 카테고리 메타데이터 동기화 (구형 원시 키 자동 배제)
    fetch(apiUrl("/api/meta/categories"))
      .then((r) => (r.ok ? r.json() : null))
      .then((data) => {
        if (!data || !Array.isArray(data.categories)) return;
        const normalized: CategoryMetaItem[] = (data.categories as CategoryMetaItem[])
          .filter((c) => c && typeof c.key === "string" && typeof c.label === "string")
          .map((c) => ({
            key: c.key.includes("해외핫딜") ? "해외직구" : c.key,
            label: c.label,
          }))
          .filter((c) => c.key !== "전체");
        if (normalized.length > 0) {
          setCategories([{ key: "전체", label: "전체" }, ...normalized]);
        }
      })
      .catch(() => {});

    // /?dealId= 딥링크 진입 시 상세 모달 자동 오픈
    if (initialDealId !== null) {
      fetch(apiUrl(`/api/community/deals/${initialDealId}`))
        .then((r) => (r.ok ? (r.json() as Promise<RawDealApiResponse>) : null))
        .then((detail) => {
          if (detail && detail.id) {
            setSelectedDeal(mapRawDealToDeal(detail));
          }
        })
        .catch(() => {});
    }

    fetch(apiUrl("/api/community/popular-keywords"))
      .then((r) => (r.ok ? r.json() : null))
      .then((data) => {
        if (!data) return;
        if (data.updated_at) {
          setPopularUpdatedAt(String(data.updated_at));
        }
        const rawKeywords: string[] = Array.isArray(data.keywords)
          ? data.keywords.slice(0, 12)
          : [];
        const rawRankings: PopularKeywordRanking[] = Array.isArray(
          data.rankings
        )
          ? data.rankings.slice(0, 12)
          : [];

        // 백엔드 /popular-keywords가 이미 매칭 딜 건수를 검증해 내려주므로 N+1 중복 조회 없이 즉시 반영
        const candidateList =
          rawRankings.length > 0
            ? rawRankings
            : rawKeywords.map((kw, i) => ({
                rank: i + 1,
                keyword: kw,
                deal_count: 1,
                recent_count: 1,
                status: (i === 0 ? "HOT" : i < 3 ? "UP" : "SAME") as
                  | "HOT"
                  | "UP"
                  | "NEW"
                  | "SAME",
              }));

        const validRankings = candidateList
          .filter((item) => {
            const kw = (item.keyword || "").trim();
            return Boolean(kw && kw !== "일시");
          })
          .slice(0, 10)
          .map((item, idx) => ({
            ...item,
            keyword: item.keyword.trim(),
            rank: idx + 1,
            status: (idx === 0 ? "HOT" : item.status) as
              | "HOT"
              | "UP"
              | "NEW"
              | "SAME",
          }));

        if (validRankings.length > 0) {
          setPopularRankings(validRankings);
          setPopularKeywords(validRankings.map((v) => v.keyword));
        }
      })
      .catch(() => {});

    const hasInitialFilter = Boolean(
      initialSharedKw ||
        initialCategories.length > 0 ||
        initialPlatforms.length > 0 ||
        initialSort !== "latest" ||
        initialOnlyHot ||
        initialHideClosed ||
        initialIncludeClosed ||
        initialFreeShipping ||
        initialShop ||
        initialMinPrice ||
        initialMaxPrice
    );

    let cachedDeals: Deal[] = [];
    if (typeof window !== "undefined") {
      const saved = localStorage.getItem("insightdeal_cached_deals");
      if (saved && !hasInitialFilter) {
        try {
          cachedDeals = JSON.parse(saved);
          if (Array.isArray(cachedDeals) && cachedDeals.length > 0) {
            dealsRef.current = cachedDeals;
            setDeals(cachedDeals);
            setLoading(false);
          }
        } catch {}
      }

      const token = getOrCreateBrowserUserId();
      if (token) {
        setIsLoggedIn(true);
      }
      const savedWishlist = localStorage.getItem("insightdeal_wishlist");
      if (savedWishlist) {
        try {
          const parsed = JSON.parse(savedWishlist);
          if (Array.isArray(parsed)) {
            wishlistRef.current = parsed;
            setWishlist(parsed);
          }
        } catch {}
      }
      const savedWishlistItems = localStorage.getItem(
        "insightdeal_wishlist_items"
      );
      if (savedWishlistItems) {
        try {
          const parsed = JSON.parse(savedWishlistItems);
          if (parsed && typeof parsed === "object" && !Array.isArray(parsed)) {
            wishlistItemsMapRef.current = parsed;
            setWishlistItemsMap(parsed);
          }
        } catch {}
      }
      const savedAlerts = localStorage.getItem("insightdeal_alerts");
      if (savedAlerts) {
        try {
          const parsed = JSON.parse(savedAlerts);
          if (Array.isArray(parsed)) {
            alertsRef.current = parsed;
            setAlerts(parsed);
          }
        } catch {}
      }
      const savedRecent = localStorage.getItem("insightdeal_recent_searches");
      if (savedRecent) {
        try {
          const parsed = JSON.parse(savedRecent);
          if (Array.isArray(parsed)) setRecentSearches(parsed.slice(0, 5));
        } catch {}
      }
      try {
        const savedRecentDeals = localStorage.getItem(RECENT_DEALS_STORAGE_KEY);
        if (savedRecentDeals) {
          const parsed = JSON.parse(savedRecentDeals);
          if (Array.isArray(parsed)) setRecentDeals(parsed.slice(0, 30));
        }
      } catch {}
      try {
        const savedHidden = localStorage.getItem(HIDDEN_DEALS_STORAGE_KEY);
        if (savedHidden) {
          const parsed = JSON.parse(savedHidden);
          if (Array.isArray(parsed)) {
            setHiddenDealIds(parsed.filter((v): v is number => typeof v === "number"));
          }
        }
      } catch {}
      try {
        const savedMuted = localStorage.getItem(MUTED_KEYWORDS_STORAGE_KEY);
        if (savedMuted) {
          const parsed = JSON.parse(savedMuted);
          if (Array.isArray(parsed)) {
            setMutedKeywords(parsed.filter((v): v is string => typeof v === "string"));
          }
        }
      } catch {}
      const savedViewMode = localStorage.getItem("insightdeal_view_mode");
      if (savedViewMode === "compact" || savedViewMode === "card") {
        setViewMode(savedViewMode);
      }
      if (token) {
        void hydrateFromServer();
      }
    }

    const initFetch = async () => {
      const hasCache = cachedDeals.length > 0 && !hasInitialFilter;
      setIsRefreshing(true);
      await fetchLiveDeals(
        !hasCache,
        0,
        initialCategories,
        initialPlatforms,
        initialSharedKw,
        initialSort,
        {
          onlyHot: initialOnlyHot,
          hideClosed: initialHideClosed,
          includeClosed: initialIncludeClosed,
          freeShipping: initialFreeShipping,
          shop: initialShop,
          minPrice: initialMinPrice,
          maxPrice: initialMaxPrice,
        }
      );
      setIsRefreshing(false);
    };

    initFetch();

    const timer = setInterval(() => setCurrentTime(Date.now()), 60000);

    return () => {
      clearInterval(timer);
      clearSearchDebounce();
      if (liveDealsAbortRef.current) {
        liveDealsAbortRef.current.abort();
      }
      if (toastTimerRef.current) {
        clearTimeout(toastTimerRef.current);
      }
      if (typeof window !== "undefined") {
        window.removeEventListener(
          "beforeinstallprompt",
          handleBeforeInstallPrompt
        );
        window.removeEventListener("keydown", handleGlobalKeyDown);
      }
    };
  }, [clearSearchDebounce, fetchLiveDeals, getOrCreateBrowserUserId, hydrateFromServer, updateAppliedSearch]);

  useEffect(() => {
    if (!isMounted || !urlSyncReadyRef.current || typeof window === "undefined") {
      return;
    }
    try {
      const nextParams = new URLSearchParams(window.location.search);
      nextParams.delete("_og");
      if (appliedSearch.trim()) {
        nextParams.set("keyword", appliedSearch.trim());
      } else {
        nextParams.delete("keyword");
      }
      if (selectedPlatforms.length > 0) {
        nextParams.set("platform", selectedPlatforms.join(","));
      } else {
        nextParams.delete("platform");
      }
      if (selectedCategories.length > 0) {
        nextParams.set("category", selectedCategories.join(","));
      } else {
        nextParams.delete("category");
      }
      if (sortBy !== "latest") {
        nextParams.set("sort", sortBy);
      } else {
        nextParams.delete("sort");
      }
      if (onlyHotDeals) {
        nextParams.set("hot", "1");
      } else {
        nextParams.delete("hot");
        nextParams.delete("onlyHot");
      }
      if (hideClosedDeals) {
        nextParams.set("hideClosed", "1");
      } else {
        nextParams.delete("hideClosed");
      }
      if (includeClosedDeals && !hideClosedDeals) {
        nextParams.set("includeClosed", "1");
      } else {
        nextParams.delete("includeClosed");
      }
      if (freeShippingOnly) {
        nextParams.set("freeShipping", "1");
      } else {
        nextParams.delete("freeShipping");
      }
      if (shopFilter.trim()) {
        nextParams.set("shop", shopFilter.trim());
      } else {
        nextParams.delete("shop");
      }
      if (minPriceInput.trim()) {
        nextParams.set("minPrice", minPriceInput.trim());
      } else {
        nextParams.delete("minPrice");
      }
      if (maxPriceInput.trim()) {
        nextParams.set("maxPrice", maxPriceInput.trim());
      } else {
        nextParams.delete("maxPrice");
      }
      const nextQs = nextParams.toString();
      const currQs = window.location.search.replace(/^\?/, "");
      if (nextQs !== currQs) {
        const nextUrl = `${window.location.pathname}${nextQs ? `?${nextQs}` : ""}${window.location.hash}`;
        window.history.replaceState(window.history.state, "", nextUrl);
      }
    } catch {}
  }, [
    isMounted,
    appliedSearch,
    selectedPlatforms,
    selectedCategories,
    sortBy,
    onlyHotDeals,
    hideClosedDeals,
    includeClosedDeals,
    freeShippingOnly,
    shopFilter,
    minPriceInput,
    maxPriceInput,
  ]);

  useEffect(() => {
    if (!isMounted || !hasMore || loading || isRefreshing) return;

    const observer = new IntersectionObserver(
      async (entries) => {
        if (entries[0].isIntersecting && !isFetchingMore) {
          setIsFetchingMore(true);
          try {
            const currentOffset = deals.length;
            await fetchLiveDeals(
              false,
              currentOffset,
              selectedCategoriesRef.current,
              selectedPlatformsRef.current,
              undefined,
              undefined,
              { cursor: nextCursorRef.current }
            );
          } finally {
            setIsFetchingMore(false);
          }
        }
      },
      { threshold: 0.8 }
    );

    if (observerTarget.current) {
      observer.observe(observerTarget.current);
    }

    return () => observer.disconnect();
  }, [
    isMounted,
    hasMore,
    deals.length,
    selectedCategories,
    selectedPlatforms,
    loading,
    isRefreshing,
    isFetchingMore,
    fetchLiveDeals,
  ]);

  const hasLoadedDeals = deals.length > 0;

  useEffect(() => {
    if (!isMounted || !hasLoadedDeals || sortBy !== "latest") return;

    let activePollController: AbortController | null = null;
    const checkInterval = setInterval(async () => {
      if (document.hidden) return;
      activePollController?.abort();
      const pollController = new AbortController();
      activePollController = pollController;
      const pollTimeout = setTimeout(() => pollController.abort(), 8000);

      try {
        const activeCategories = selectedCategories.filter(
          (c) => c && c !== "전체"
        );
        const activePlatforms = selectedPlatforms.filter(
          (p) => p && p !== "전체"
        );
        const apiCategory =
          activeCategories.length > 0 ? activeCategories.join(",") : "";
        const platformParam =
          activePlatforms.length > 0
            ? `&platform=${encodeURIComponent(activePlatforms.join(","))}`
            : "";
        const queryParam = appliedSearch
          ? `&keyword=${encodeURIComponent(appliedSearch)}`
          : "";
        const filterParams = `${onlyHotDeals ? "&only_hot=true" : ""}${
          hideClosedDeals ? "&hide_closed=true" : ""
        }${includeClosedDeals ? "&include_closed=true" : ""}`;
        const checkUrl = apiUrl(
          `/api/community/hot-deals?limit=8&offset=0${
            apiCategory ? `&category=${encodeURIComponent(apiCategory)}` : ""
          }${platformParam}${queryParam}${filterParams}`
        );

        const res = await fetch(checkUrl, { signal: pollController.signal });
        if (!res.ok) return;
        const data = await res.json();
        const incomingDeals: RawDealApiResponse[] = Array.isArray(data)
          ? data
          : data.deals || data.items || [];
        const latestKnownId = maxKnownDealIdRef.current;
        if (incomingDeals.length > 0 && latestKnownId > 0) {
          const freshCount = incomingDeals.filter(
            (d: RawDealApiResponse) => d.id > latestKnownId
          ).length;
          if (freshCount > 0) {
            setNewDealsCount(freshCount);
          }
        }
      } catch {
      } finally {
        clearTimeout(pollTimeout);
        if (activePollController === pollController) {
          activePollController = null;
        }
      }
    }, 50000);

    return () => {
      clearInterval(checkInterval);
      activePollController?.abort();
    };
  }, [
    isMounted,
    hasLoadedDeals,
    sortBy,
    selectedCategories,
    selectedPlatforms,
    appliedSearch,
    onlyHotDeals,
    hideClosedDeals,
    includeClosedDeals,
  ]);

  const toApproxKrw = (item: Deal) => {
    const priceStr = String(item.price ?? "").trim();
    if (
      !priceStr ||
      priceStr === "0" ||
      priceStr === "0원" ||
      priceStr === "가격 확인" ||
      priceStr === "가격 문의" ||
      priceStr === "정보 없음"
    ) {
      return 0;
    }
    const raw = parseFloat(priceStr.replace(/[^0-9.-]/g, "")) || 0;
    if (!Number.isFinite(raw) || raw <= 0) return 0;
    const curr = (item.currency || "KRW").trim().toUpperCase();
    const hasDec = priceStr.includes(".");
    if (curr === "USD") return (hasDec ? raw : raw / 100) * 1400;
    if (curr === "EUR") return (hasDec ? raw : raw / 100) * 1500;
    if (curr === "CNY") return (hasDec ? raw : raw / 100) * 195;
    if (curr === "JPY") return raw * 9.5;
    return raw;
  };
  const parseShippingKrw = (sf?: string | null): number => {
    const clean = (sf || "").trim();
    if (!clean || /무료|무배|직배무료|직구무배|공짜/.test(clean)) return 0;
    const digits = clean.replace(/[^0-9]/g, "");
    const val = digits ? parseInt(digits, 10) : 0;
    return Number.isFinite(val) && val > 0 && val <= 200000 ? val : 0;
  };
  const toTotalApproxKrw = (item: Deal) => {
    const base = toApproxKrw(item);
    return base > 0 ? base + parseShippingKrw(item.shipping_fee) : 0;
  };

  const filteredDeals = deals
    .filter((d) => {
      if (hiddenDealIds.length > 0 && hiddenDealIds.includes(d.id)) return false;
      if (mutedKeywords.length > 0) {
        const lowerTitle = decodeHtmlEntities(d.title || "").toLowerCase();
        if (mutedKeywords.some((kw) => kw && lowerTitle.includes(kw.toLowerCase()))) {
          return false;
        }
      }
      if (hideClosedDeals && d.isClosed) return false;
      if (freeShippingOnly) {
        const sf = (d.shipping_fee || "").trim();
        if (sf && /\d/.test(sf) && !/무료|무배|직배무료|직구무배|공짜|^0원?$/.test(sf)) {
          return false;
        }
      }
      if (
        onlyHotDeals &&
        (d.honeyScore || 0) < 100 &&
        !d.isSuperHotdeal &&
        (d.likeCount || 0) < 8
      ) {
        return false;
      }
      if (selectedPlatforms.length > 0) {
        const commNames = (d.community_name || "")
          .split(",")
          .map((s) => s.trim())
          .filter(Boolean);
        const srcNames = (d.sources || [])
          .map((s) =>
            String(s.site_name || "")
              .replace(/ - .*$/, "")
              .trim()
          )
          .filter(Boolean);
        const matched =
          commNames.some((name) => selectedPlatforms.includes(name)) ||
          srcNames.some((name) => selectedPlatforms.includes(name));
        if (!matched) {
          return false;
        }
      }
      return true;
    })
    .slice()
    .sort((a, b) => {
      if (sortBy === "popular") {
        return (b.honeyScore || 0) - (a.honeyScore || 0);
      }
      if (sortBy === "comments") {
        return (b.comments ?? b.commentCount ?? 0) - (a.comments ?? a.commentCount ?? 0);
      }
      if (sortBy === "recommendations") {
        return (b.recommendations ?? b.likeCount ?? 0) - (a.recommendations ?? a.likeCount ?? 0);
      }
      if (sortBy === "price_asc" || sortBy === "price_desc") {
        const pa = toTotalApproxKrw(a);
        const pb = toTotalApproxKrw(b);
        if (pa <= 0 && pb <= 0) return 0;
        if (pa <= 0) return 1;
        if (pb <= 0) return -1;
        if (pa !== pb) return sortBy === "price_asc" ? pa - pb : pb - pa;
        return sortBy === "price_asc"
          ? toApproxKrw(a) - toApproxKrw(b)
          : toApproxKrw(b) - toApproxKrw(a);
      }
      return 0;
    });

  const selectedDealIndex = selectedDeal
    ? filteredDeals.findIndex((d) => d.id === selectedDeal.id)
    : -1;
  const handleNavigateSelectedDeal = (delta: -1 | 1) => {
    if (filteredDeals.length <= 1 || selectedDealIndex < 0) return;
    const nextIdx =
      (selectedDealIndex + delta + filteredDeals.length) % filteredDeals.length;
    const nextDeal = filteredDeals[nextIdx];
    if (nextDeal) openDealWithHistory(nextDeal);
  };

  const wishlistDeals = wishlist
    .map((id) => deals.find((d) => d.id === id) || wishlistItemsMap[id])
    .filter((d): d is Deal => Boolean(d));

  return (
    <>
      {newDealsCount > 0 && (
        <button
          type="button"
          onClick={handleScrollToTopAndRefresh}
          className="floating-new-deals-pill bounce-click"
        >
          <RefreshCw size={14} aria-hidden="true" />
          <span>새 핫딜 {newDealsCount}건 보기</span>
        </button>
      )}

      <header className="navbar glass-panel">
        <button
          type="button"
          className="nav-logo"
          style={{
            cursor: "pointer",
            display: "inline-flex",
            alignItems: "center",
          }}
          onClick={handleResetAllFilters}
        >
          {/* eslint-disable-next-line @next/next/no-img-element */}
          <img
            src="/brand-logo.png"
            alt=""
            aria-hidden="true"
            width={30}
            height={30}
            style={{ width: 30, height: 30, objectFit: "contain", display: "block" }}
          />
          <span>얼만데</span>
          <span className="live-badge">LIVE</span>
        </button>

        <div className="nav-actions">
          <div className="nav-group nav-group-feed global-counters">
            <button
              type="button"
              className="counter-item bounce-click"
              aria-label="내 찜 목록 열기"
              onClick={() => setIsWishlistOpen(true)}
            >
              <Heart
                size={16}
                color={wishlist.length > 0 ? "var(--danger-text)" : "var(--text-sub)"}
                fill={wishlist.length > 0 ? "var(--danger-text)" : "none"}
                aria-hidden="true"
              />
              <span className="counter-label">찜</span>
              {wishlist.length > 0 && (
                <span className="counter-badge badge-active">
                  {wishlist.length}
                </span>
              )}
            </button>
            <button
              type="button"
              className="counter-item bounce-click"
              aria-label="알림 내역 열기"
              onClick={() => setIsNotificationOpen(true)}
            >
              <Bell
                size={16}
                color={
                  alerts.length + serverAlertCount > 0
                    ? "var(--accent)"
                    : "var(--text-sub)"
                }
                fill={
                  alerts.length + serverAlertCount > 0
                    ? "var(--accent)"
                    : "none"
                }
                aria-hidden="true"
              />
              <span className="counter-label">알림 내역</span>
              {alerts.length + serverAlertCount > 0 && (
                <span className="counter-badge badge-active">
                  {alerts.length + serverAlertCount}
                </span>
              )}
            </button>
            <Link
              href="/settings/notifications"
              className="counter-item bounce-click"
              aria-label="키워드 알림 설정"
              style={{ textDecoration: "none" }}
            >
              <Settings size={16} color="var(--text-sub)" aria-hidden="true" />
              <span className="counter-label">키워드 알림</span>
            </Link>
          </div>

          <span className="nav-divider" aria-hidden="true" />

          <div className="nav-group nav-group-env">
            <button
              type="button"
              className="counter-item counter-item-env bounce-click"
              aria-label="기기 동기화 및 계정 연결"
              onClick={() => setShowSyncModal(true)}
            >
              <Smartphone size={16} color="var(--text-sub)" aria-hidden="true" />
              <span className="counter-label">기기 동기화</span>
            </button>
            <ThemeToggle />
            {!isTossMiniApp && (
              <button
                type="button"
                className="btn-primary nav-install-btn"
                aria-label="앱 설치"
                onClick={handleInstallApp}
              >
                <Download size={16} aria-hidden="true" />
                <span>앱 설치</span>
              </button>
            )}
          </div>
        </div>
      </header>

      <div className="web-container">
        <InAppBrowserBanner onShowToast={showToast} />
        <section style={{ padding: "4px 0 80px 0" }}>
          <h1 className="sr-only">실시간 핫딜 모아보기</h1>

          <div className="discovery-bar-grid">
            <form onSubmit={handleSearchSubmit} className="search-container">
              <div className="search-bar-wrapper">
                <Search size={18} className="search-icon" aria-hidden="true" />
                <input
                  ref={searchInputRef}
                  type="text"
                  aria-label="핫딜 검색어 입력"
                  placeholder={
                    isUltraCompactViewport
                      ? "핫딜 검색 (-제외, :가격)"
                      : isCompactViewport
                        ? "핫딜 검색 (맥북 -파우치, :15000)"
                        : "주요 커뮤니티 10곳 핫딜 검색 (예: 맥북 -파우치, 콜라 :15000)"
                  }
                  value={searchQuery}
                  onCompositionStart={() => {
                    isComposingRef.current = true;
                    clearSearchDebounce();
                  }}
                  onCompositionEnd={(e) => {
                    isComposingRef.current = false;
                    const composedVal = e.currentTarget.value;
                    setSearchQuery(composedVal);
                    scheduleDebouncedSearch(composedVal);
                  }}
                  onChange={(e) => {
                    const nextVal = e.target.value;
                    setSearchQuery(nextVal);
                    const nativeEvt = e.nativeEvent as InputEvent;
                    if (nativeEvt?.isComposing || isComposingRef.current) {
                      return;
                    }
                    scheduleDebouncedSearch(nextVal);
                  }}
                  className="search-input"
                />
                {(searchQuery || appliedSearch) && (
                  <button
                    type="button"
                    aria-label="검색어 지우기"
                    onClick={handleClearSearch}
                    className="search-clear-btn"
                  >
                    <X size={16} />
                  </button>
                )}
                <button type="submit" className="search-submit-btn">
                  검색
                </button>
              </div>
            </form>

            <TrendingKeywordsTicker
              keywords={popularKeywords}
              rankings={popularRankings}
              updatedAt={popularUpdatedAt}
              selectedKeyword={appliedSearch}
              onSelectKeyword={handleSelectPopularKeyword}
            />
          </div>

          {recentSearches.length > 0 && (
            <div
              style={{
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
                gap: "8px",
                flexWrap: "wrap",
                marginBottom: "10px",
              }}
            >
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: "6px",
                  flexWrap: "wrap",
                }}
              >
                <span
                  style={{
                    fontSize: "0.76rem",
                    fontWeight: 700,
                    color: "var(--text-sub)",
                    marginRight: "2px",
                  }}
                >
                  최근 검색어
                </span>
                {recentSearches.map((kw) => {
                  const isSelected = appliedSearch === kw;
                  return (
                    <div
                      key={kw}
                      style={{
                        display: "inline-flex",
                        alignItems: "center",
                        minHeight: "44px",
                        padding: "0 4px 0 12px",
                        borderRadius: "999px",
                        fontSize: "0.78rem",
                        fontWeight: isSelected ? 700 : 600,
                        background: isSelected
                          ? "rgba(var(--accent-rgb), 0.1)"
                          : "var(--card-bg)",
                        color: isSelected ? "var(--accent)" : "var(--text-main)",
                        border: isSelected
                          ? "1px solid var(--accent)"
                          : "1px solid var(--card-border)",
                      }}
                    >
                      <button
                        type="button"
                        onClick={() => handleSelectRecentKeyword(kw)}
                        style={{
                          cursor: "pointer",
                          minWidth: "44px",
                          minHeight: "44px",
                          display: "inline-flex",
                          alignItems: "center",
                          justifyContent: "center",
                          paddingRight: "4px",
                          fontWeight: "inherit",
                          fontSize: "inherit",
                        }}
                      >
                        {kw}
                      </button>
                      <button
                        type="button"
                        aria-label={`${kw} 최근 검색어 삭제`}
                        title={`${kw} 삭제`}
                        onClick={() => removeRecentSearch(kw)}
                        style={{
                          display: "inline-flex",
                          alignItems: "center",
                          justifyContent: "center",
                          width: "44px",
                          height: "44px",
                          minWidth: "44px",
                          minHeight: "44px",
                          borderRadius: "50%",
                          border: "none",
                          background: "transparent",
                          color: "var(--text-muted)",
                          cursor: "pointer",
                        }}
                      >
                        <X size={13} />
                      </button>
                    </div>
                  );
                })}
              </div>
              <button
                type="button"
                onClick={clearRecentSearches}
                style={{
                  border: "none",
                  background: "transparent",
                  color: "var(--text-muted)",
                  fontSize: "0.76rem",
                  fontWeight: 600,
                  cursor: "pointer",
                  minHeight: "44px",
                  padding: "0 8px",
                }}
              >
                전체 삭제
              </button>
            </div>
          )}

          {searchQuery.trim() && isAutoSearchableQuery(searchQuery) && (
            <div
              style={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
                flexWrap: "wrap",
                gap: "10px",
                padding: "10px 14px",
                marginBottom: "10px",
                borderRadius: "12px",
                background: "rgba(var(--accent-rgb), 0.06)",
                border: "1px solid rgba(var(--accent-rgb), 0.2)",
              }}
            >
              <span
                style={{
                  fontSize: "var(--fs-control)",
                  color: "var(--text-main)",
                  fontWeight: 600,
                  display: "inline-flex",
                  alignItems: "center",
                  gap: "6px",
                }}
              >
                <Bell size={15} color="var(--accent)" aria-hidden="true" />
                <span>
                  <strong>&lsquo;{searchQuery.trim()}&rsquo;</strong> 새 핫딜이
                  뜨면 바로 알려드릴까요?
                </span>
              </span>
              <button
                type="button"
                onClick={() => handleQuickSubscribeKeyword(searchQuery)}
                className="bounce-click"
                style={{
                  minHeight: "var(--control-h-lg)",
                  minWidth: "var(--touch-target-min)",
                  padding: "8px 14px",
                  borderRadius: "var(--radius-sm)",
                  background: "var(--accent)",
                  color: "var(--on-accent)",
                  fontWeight: 700,
                  fontSize: "var(--fs-control)",
                  border: "none",
                  cursor: "pointer",
                }}
              >
                키워드 알림 받기
              </button>
            </div>
          )}

          <div className="filter-scope-dock">
            <div className="filter-scope-row">
              <span className="filter-scope-label">출처</span>
              <div className="platform-rail-wrapper">
                <div
                  id="platform-filter-rail"
                  className={`platform-chips-row ${isPlatformsExpanded ? "expanded" : ""}`}
                  role="group"
                  aria-label="커뮤니티 필터"
                >
                  {PLATFORMS.map((platform) => {
                    const isSelected =
                      platform === "전체"
                        ? selectedPlatforms.length === 0
                        : selectedPlatforms.includes(platform);
                    return (
                      <button
                        key={platform}
                        type="button"
                        aria-pressed={isSelected}
                        onClick={() => handlePlatformToggle(platform)}
                        className={`platform-chip bounce-click ${isSelected ? "active" : ""}`}
                      >
                        {isSelected && (
                          <Check size={13} strokeWidth={2.5} aria-hidden="true" />
                        )}
                        <span>{platform === "전체" ? "전체 커뮤니티" : platform}</span>
                      </button>
                    );
                  })}
                </div>
                <button
                  type="button"
                  className="platform-expand-btn bounce-click"
                  onClick={() => setIsPlatformsExpanded((prev) => !prev)}
                  aria-expanded={isPlatformsExpanded}
                  aria-controls="platform-filter-rail"
                >
                  {isPlatformsExpanded ? (
                    <>
                      <span>출처 접기</span>
                      <ChevronUp size={13} aria-hidden="true" />
                    </>
                  ) : (
                    <>
                      <span>출처 더보기</span>
                      <ChevronDown size={13} aria-hidden="true" />
                    </>
                  )}
                </button>
              </div>
            </div>

            <div className="filter-scope-row filter-scope-row--category">
              <span className="filter-scope-label">카테고리</span>
              <div className="category-rail-wrapper">
                <div
                  id="category-filter-rail"
                  className={`tabs-container ${isCategoriesExpanded ? "expanded" : ""}`}
                >
                  <div
                    className={`tabs ${isCategoriesExpanded ? "expanded" : ""}`}
                    role="group"
                    aria-label="카테고리 필터"
                  >
                    {categories.map((cat) => {
                      const isCatActive =
                        cat.key === "전체"
                          ? selectedCategories.length === 0
                          : selectedCategories.includes(cat.key);
                      return (
                        <button
                          key={cat.key}
                          type="button"
                          aria-pressed={isCatActive}
                          className={`tab bounce-click ${isCatActive ? "active" : ""}`}
                          onClick={() => handleCategoryToggle(cat.key)}
                        >
                          {isCatActive && (
                            <Check size={13} strokeWidth={2.5} aria-hidden="true" />
                          )}
                          <span>{cat.label}</span>
                        </button>
                      );
                    })}
                  </div>
                </div>
                <button
                  type="button"
                  className="category-expand-btn bounce-click"
                  aria-expanded={isCategoriesExpanded}
                  aria-controls="category-filter-rail"
                  onClick={() => setIsCategoriesExpanded((prev) => !prev)}
                >
                  <span>{isCategoriesExpanded ? "접기" : "전체 펼치기"}</span>
                  {isCategoriesExpanded ? (
                    <ChevronUp size={13} aria-hidden="true" />
                  ) : (
                    <ChevronDown size={13} aria-hidden="true" />
                  )}
                </button>
              </div>
            </div>
          </div>

          <div className="feed-control-toolbar">
            <div className="sort-segment-group" role="group" aria-label="정렬 기준">
              {(
                [
                  { key: "latest", label: "최신순" },
                  { key: "popular", label: "인기순" },
                  { key: "price_asc", label: "낮은가격순" },
                  { key: "price_desc", label: "높은가격순" },
                  { key: "comments", label: "댓글순" },
                  { key: "recommendations", label: "추천순" },
                ] as const
              ).map((opt) => {
                const active = sortBy === opt.key;
                return (
                  <button
                    key={opt.key}
                    type="button"
                    aria-pressed={active}
                    className="sort-segment-btn"
                    onClick={() => handleSortChange(opt.key)}
                    style={{
                      background: active ? "var(--accent)" : "transparent",
                      color: active ? "var(--on-accent)" : "var(--text-sub)",
                      fontWeight: active ? 700 : 600,
                    }}
                  >
                    {opt.label}
                  </button>
                );
              })}
            </div>

            <div className="feed-control-toggles">
              <button
                type="button"
                aria-pressed={onlyHotDeals}
                onClick={handleToggleOnlyHot}
                className="filter-chip-btn bounce-click"
                style={{
                  background: onlyHotDeals
                    ? "var(--deal-orange-bg)"
                    : "var(--card-bg)",
                  color: onlyHotDeals ? "var(--deal-orange)" : "var(--text-sub)",
                  border: onlyHotDeals
                    ? "1px solid var(--deal-orange)"
                    : "1px solid var(--card-border)",
                }}
              >
                <Flame size={14} aria-hidden="true" />
                <span>인기 딜만</span>
              </button>

              <button
                type="button"
                aria-pressed={hideClosedDeals}
                onClick={handleToggleHideClosed}
                className="filter-chip-btn bounce-click"
                style={{
                  background: hideClosedDeals
                    ? "var(--accent-soft-bg)"
                    : "var(--card-bg)",
                  color: hideClosedDeals ? "var(--accent)" : "var(--text-sub)",
                  border: hideClosedDeals
                    ? "1px solid var(--accent)"
                    : "1px solid var(--card-border)",
                }}
              >
                품절 제외
              </button>

              <button
                type="button"
                aria-pressed={includeClosedDeals}
                onClick={handleToggleIncludeClosed}
                className="filter-chip-btn bounce-click"
                style={{
                  background: includeClosedDeals
                    ? "var(--secondary-soft-bg)"
                    : "var(--card-bg)",
                  color: includeClosedDeals ? "var(--secondary)" : "var(--text-sub)",
                  border: includeClosedDeals
                    ? "1px solid var(--secondary)"
                    : "1px solid var(--card-border)",
                }}
              >
                종료 포함
              </button>

              <button
                type="button"
                className="filter-chip-btn bounce-click"
                onClick={handleRefresh}
                disabled={isRefreshing}
                style={{
                  background: "var(--card-bg)",
                  color: "var(--text-main)",
                  border: "1px solid var(--card-border)",
                  cursor: isRefreshing ? "not-allowed" : "pointer",
                  opacity: isRefreshing ? 0.7 : 1,
                }}
              >
                <RefreshCw
                  size={14}
                  color="var(--accent)"
                  aria-hidden="true"
                  className={isRefreshing ? "spin-animation" : ""}
                />
                <span>새로고침</span>
              </button>
            </div>
          </div>

          <div className="feed-quick-filter-mobile-bar">
            <button
              type="button"
              aria-expanded={showMobileQuickFilters}
              aria-controls="feed-quick-filter-panel"
              onClick={() => setShowMobileQuickFilters((v) => !v)}
              className="filter-chip-btn bounce-click"
              style={{
                minHeight: "34px",
                padding: "0 9px",
                fontSize: "0.74rem",
                gap: "4px",
                whiteSpace: "nowrap",
                flexShrink: 0,
                background:
                  showMobileQuickFilters ||
                  freeShippingOnly ||
                  shopFilter ||
                  minPriceInput.trim() ||
                  maxPriceInput.trim()
                    ? "var(--accent-soft-bg)"
                    : "var(--card-bg)",
                color:
                  showMobileQuickFilters ||
                  freeShippingOnly ||
                  shopFilter ||
                  minPriceInput.trim() ||
                  maxPriceInput.trim()
                    ? "var(--accent)"
                    : "var(--text-main)",
                border:
                  showMobileQuickFilters ||
                  freeShippingOnly ||
                  shopFilter ||
                  minPriceInput.trim() ||
                  maxPriceInput.trim()
                    ? "1px solid var(--accent)"
                    : "1px solid var(--card-border)",
              }}
            >
              <SlidersHorizontal size={12} aria-hidden="true" />
              <span>
                상세 필터
                {(() => {
                  const cnt =
                    (freeShippingOnly ? 1 : 0) +
                    (shopFilter ? 1 : 0) +
                    (minPriceInput.trim() || maxPriceInput.trim() ? 1 : 0);
                  return cnt > 0 ? ` (${cnt})` : "";
                })()}
              </span>
              {showMobileQuickFilters ? (
                <ChevronUp size={12} aria-hidden="true" />
              ) : (
                <ChevronDown size={12} aria-hidden="true" />
              )}
            </button>

            <div style={{ display: "inline-flex", alignItems: "center", gap: "6px", flexShrink: 0 }}>
              <div
                role="group"
                aria-label="모바일 보기 방식"
                style={{
                  display: "inline-flex",
                  flexShrink: 0,
                  borderRadius: "10px",
                  border: "1px solid var(--card-border)",
                  overflow: "hidden",
                }}
              >
                <button
                  type="button"
                  aria-pressed={viewMode === "card"}
                  onClick={() => handleViewModeChange("card")}
                  style={{
                    minHeight: "34px",
                    padding: "4px 7px",
                    display: "inline-flex",
                    alignItems: "center",
                    gap: "3px",
                    fontSize: "0.73rem",
                    fontWeight: 600,
                    whiteSpace: "nowrap",
                    border: "none",
                    background: viewMode === "card" ? "var(--accent)" : "var(--card-bg)",
                    color: viewMode === "card" ? "var(--on-accent)" : "var(--text-sub)",
                    cursor: "pointer",
                  }}
                >
                  <LayoutGrid size={12} aria-hidden="true" />
                  <span>카드</span>
                </button>
                <button
                  type="button"
                  aria-pressed={viewMode === "compact"}
                  onClick={() => handleViewModeChange("compact")}
                  style={{
                    minHeight: "34px",
                    padding: "4px 7px",
                    display: "inline-flex",
                    alignItems: "center",
                    gap: "3px",
                    fontSize: "0.73rem",
                    fontWeight: 600,
                    whiteSpace: "nowrap",
                    border: "none",
                    background: viewMode === "compact" ? "var(--accent)" : "var(--card-bg)",
                    color: viewMode === "compact" ? "var(--on-accent)" : "var(--text-sub)",
                    cursor: "pointer",
                  }}
                >
                  <List size={12} aria-hidden="true" />
                  <span>컴팩트</span>
                </button>
              </div>

              <button
                type="button"
                aria-expanded={showRecentDealsPanel}
                aria-label={`최근 본 핫딜 (${recentDeals.length})`}
                onClick={() => setShowRecentDealsPanel((v) => !v)}
                className="filter-chip-btn bounce-click"
                style={{
                  minHeight: "34px",
                  padding: "0 8px",
                  fontSize: "0.73rem",
                  gap: "4px",
                  whiteSpace: "nowrap",
                  flexShrink: 0,
                  background: showRecentDealsPanel ? "var(--accent-soft-bg)" : "var(--card-bg)",
                  color: showRecentDealsPanel ? "var(--accent)" : "var(--text-sub)",
                  border: "1px solid var(--card-border)",
                }}
              >
                <Clock size={12} aria-hidden="true" />
                <span>최근 ({recentDeals.length})</span>
              </button>
            </div>
          </div>

          <div
            id="feed-quick-filter-panel"
            className={`feed-quick-filter-row ${
              showMobileQuickFilters ? "" : "feed-quick-filter-row--collapsed"
            }`}
          >
            <button
              type="button"
              aria-pressed={freeShippingOnly}
              onClick={handleToggleFreeShipping}
              className="filter-chip-btn bounce-click"
              style={{
                background: freeShippingOnly ? "var(--accent-soft-bg)" : "var(--card-bg)",
                color: freeShippingOnly ? "var(--accent)" : "var(--text-sub)",
                border: freeShippingOnly
                  ? "1px solid var(--accent)"
                  : "1px solid var(--card-border)",
              }}
            >
              무료배송만
            </button>

            <select
              aria-label="쇼핑몰 필터"
              value={shopFilter}
              onChange={(e) => handleShopFilterChange(e.target.value)}
              style={{
                minHeight: "36px",
                padding: "4px 10px",
                borderRadius: "10px",
                background: "var(--card-bg)",
                color: "var(--text-main)",
                border: shopFilter
                  ? "1px solid var(--accent)"
                  : "1px solid var(--card-border)",
                fontSize: "0.82rem",
                fontWeight: 600,
              }}
            >
              <option value="">전체 쇼핑몰</option>
              {[
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
              ].map((m) => (
                <option key={m} value={m}>
                  {m}
                </option>
              ))}
            </select>

            <form
              onSubmit={(e) => {
                e.preventDefault();
                handleApplyPriceRange(minPriceInput, maxPriceInput);
              }}
              style={{ display: "inline-flex", alignItems: "center", gap: "4px", flexWrap: "wrap" }}
            >
              <input
                type="number"
                min={0}
                step={1000}
                aria-label="최소 가격"
                placeholder="최소 원"
                value={minPriceInput}
                onChange={(e) => setMinPriceInput(e.target.value)}
                style={{
                  width: "86px",
                  minHeight: "36px",
                  padding: "4px 8px",
                  borderRadius: "10px",
                  background: "var(--card-bg)",
                  color: "var(--text-main)",
                  border: "1px solid var(--card-border)",
                  fontSize: "0.8rem",
                }}
              />
              <span style={{ color: "var(--text-muted)", fontSize: "0.8rem" }}>~</span>
              <input
                type="number"
                min={0}
                step={1000}
                aria-label="최대 가격"
                placeholder="최대 원"
                value={maxPriceInput}
                onChange={(e) => setMaxPriceInput(e.target.value)}
                style={{
                  width: "86px",
                  minHeight: "36px",
                  padding: "4px 8px",
                  borderRadius: "10px",
                  background: "var(--card-bg)",
                  color: "var(--text-main)",
                  border: "1px solid var(--card-border)",
                  fontSize: "0.8rem",
                }}
              />
              <button
                type="submit"
                className="filter-chip-btn bounce-click"
                style={{
                  background: "var(--card-bg)",
                  color: "var(--text-main)",
                  border: "1px solid var(--card-border)",
                }}
              >
                가격 적용
              </button>
              <button
                type="button"
                className="filter-chip-btn bounce-click"
                onClick={() => {
                  setMinPriceInput("");
                  setMaxPriceInput("10000");
                  handleApplyPriceRange("", "10000");
                }}
                style={{
                  background:
                    !minPriceInput && maxPriceInput === "10000"
                      ? "var(--accent-soft-bg)"
                      : "var(--card-bg)",
                  color:
                    !minPriceInput && maxPriceInput === "10000"
                      ? "var(--accent)"
                      : "var(--text-sub)",
                  border:
                    !minPriceInput && maxPriceInput === "10000"
                      ? "1px solid var(--accent)"
                      : "1px solid var(--card-border)",
                }}
              >
                1만원 이하
              </button>
              <button
                type="button"
                className="filter-chip-btn bounce-click"
                onClick={() => {
                  setMinPriceInput("");
                  setMaxPriceInput("30000");
                  handleApplyPriceRange("", "30000");
                }}
                style={{
                  background:
                    !minPriceInput && maxPriceInput === "30000"
                      ? "var(--accent-soft-bg)"
                      : "var(--card-bg)",
                  color:
                    !minPriceInput && maxPriceInput === "30000"
                      ? "var(--accent)"
                      : "var(--text-sub)",
                  border:
                    !minPriceInput && maxPriceInput === "30000"
                      ? "1px solid var(--accent)"
                      : "1px solid var(--card-border)",
                }}
              >
                3만원 이하
              </button>
            </form>

            <div
              role="group"
              aria-label="보기 방식"
              className="feed-quick-filter-desktop-only"
              style={{
                display: "inline-flex",
                borderRadius: "10px",
                border: "1px solid var(--card-border)",
                overflow: "hidden",
              }}
            >
              <button
                type="button"
                aria-pressed={viewMode === "card"}
                aria-label="카드 보기"
                onClick={() => handleViewModeChange("card")}
                style={{
                  minHeight: "36px",
                  padding: "4px 10px",
                  display: "inline-flex",
                  alignItems: "center",
                  gap: "4px",
                  fontSize: "0.8rem",
                  fontWeight: 600,
                  border: "none",
                  background: viewMode === "card" ? "var(--accent)" : "var(--card-bg)",
                  color: viewMode === "card" ? "var(--on-accent)" : "var(--text-sub)",
                  cursor: "pointer",
                }}
              >
                <LayoutGrid size={13} aria-hidden="true" />
                <span>카드</span>
              </button>
              <button
                type="button"
                aria-pressed={viewMode === "compact"}
                aria-label="한줄 컴팩트 보기"
                onClick={() => handleViewModeChange("compact")}
                style={{
                  minHeight: "36px",
                  padding: "4px 10px",
                  display: "inline-flex",
                  alignItems: "center",
                  gap: "4px",
                  fontSize: "0.8rem",
                  fontWeight: 600,
                  border: "none",
                  background: viewMode === "compact" ? "var(--accent)" : "var(--card-bg)",
                  color: viewMode === "compact" ? "var(--on-accent)" : "var(--text-sub)",
                  cursor: "pointer",
                }}
              >
                <List size={13} aria-hidden="true" />
                <span>컴팩트</span>
              </button>
            </div>

            <button
              type="button"
              aria-expanded={showRecentDealsPanel}
              onClick={() => setShowRecentDealsPanel((v) => !v)}
              className="filter-chip-btn bounce-click feed-quick-filter-desktop-only"
              style={{
                background: showRecentDealsPanel ? "var(--accent-soft-bg)" : "var(--card-bg)",
                color: showRecentDealsPanel ? "var(--accent)" : "var(--text-sub)",
                border: "1px solid var(--card-border)",
              }}
            >
              <Clock size={13} aria-hidden="true" />
              <span>최근 본 ({recentDeals.length})</span>
            </button>

            <button
              type="button"
              aria-expanded={showHiddenManagePanel}
              onClick={() => setShowHiddenManagePanel((v) => !v)}
              className="filter-chip-btn bounce-click"
              style={{
                background: showHiddenManagePanel ? "var(--accent-soft-bg)" : "var(--card-bg)",
                color: showHiddenManagePanel ? "var(--accent)" : "var(--text-sub)",
                border: "1px solid var(--card-border)",
              }}
            >
              <EyeOff size={13} aria-hidden="true" />
              <span>숨김·뮤트 ({hiddenDealIds.length + mutedKeywords.length})</span>
            </button>
          </div>

          {showRecentDealsPanel && (
            <div
              className="glass-panel"
              style={{
                padding: "12px 14px",
                borderRadius: "14px",
                marginBottom: "12px",
                display: "flex",
                flexDirection: "column",
                gap: "8px",
              }}
            >
              <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <strong style={{ fontSize: "0.85rem", color: "var(--text-main)" }}>
                  최근 본 핫딜 ({recentDeals.length}건)
                </strong>
                {recentDeals.length > 0 && (
                  <button
                    type="button"
                    onClick={() => {
                      setRecentDeals([]);
                      if (typeof window !== "undefined") {
                        try {
                          localStorage.removeItem(RECENT_DEALS_STORAGE_KEY);
                        } catch {}
                      }
                    }}
                    style={{
                      background: "transparent",
                      border: "none",
                      color: "var(--text-muted)",
                      fontSize: "0.78rem",
                      cursor: "pointer",
                    }}
                  >
                    전체 비우기
                  </button>
                )}
              </div>
              {recentDeals.length === 0 ? (
                <span style={{ fontSize: "0.82rem", color: "var(--text-sub)" }}>
                  아직 열어본 핫딜이 없어요. 카드를 누르면 최근 기록이 여기에 쌓여요.
                </span>
              ) : (
                <div style={{ display: "flex", gap: "8px", flexWrap: "wrap" }}>
                  {recentDeals.slice(0, 10).map((rd) => {
                    const rdPriceLabel = rd.isFree
                      ? "무료"
                      : formatPrice(rd.price, rd.currency, rd.title);
                    return (
                      <button
                        key={rd.id}
                        type="button"
                        onClick={() => openDealWithHistory(rd)}
                        style={{
                          padding: "6px 12px",
                          borderRadius: "999px",
                          border: "1px solid var(--card-border)",
                          background: "var(--card-bg)",
                          color: "var(--text-main)",
                          fontSize: "0.8rem",
                          fontWeight: 600,
                          maxWidth: "280px",
                          display: "inline-flex",
                          alignItems: "center",
                          gap: "6px",
                          cursor: "pointer",
                        }}
                      >
                        <span
                          style={{
                            overflow: "hidden",
                            textOverflow: "ellipsis",
                            whiteSpace: "nowrap",
                            minWidth: 0,
                          }}
                        >
                          {decodeHtmlEntities(rd.title)}
                        </span>
                        <span
                          style={{
                            flexShrink: 0,
                            color: "var(--accent)",
                            fontWeight: 800,
                            fontSize: "0.75rem",
                            fontVariantNumeric: "tabular-nums",
                          }}
                        >
                          {rdPriceLabel}
                        </span>
                      </button>
                    );
                  })}
                </div>
              )}
            </div>
          )}

          {showHiddenManagePanel && (
            <div
              className="glass-panel"
              style={{
                padding: "12px 14px",
                borderRadius: "14px",
                marginBottom: "12px",
                display: "flex",
                flexDirection: "column",
                gap: "10px",
              }}
            >
              <form
                onSubmit={(e) => {
                  e.preventDefault();
                  handleAddMutedKeyword(muteKeywordInput);
                }}
                style={{ display: "flex", gap: "8px", flexWrap: "wrap", alignItems: "center" }}
              >
                <input
                  type="text"
                  value={muteKeywordInput}
                  onChange={(e) => setMuteKeywordInput(e.target.value)}
                  placeholder="피드에서 가릴 키워드 추가 (예: 휴대폰 케이스)"
                  aria-label="뮤트 키워드 입력"
                  style={{
                    flex: 1,
                    minWidth: "200px",
                    minHeight: "38px",
                    padding: "6px 12px",
                    borderRadius: "10px",
                    border: "1px solid var(--card-border)",
                    background: "var(--card-bg)",
                    color: "var(--text-main)",
                    fontSize: "0.84rem",
                  }}
                />
                <button type="submit" className="btn-primary" style={{ minHeight: "38px", padding: "0 14px" }}>
                  뮤트 추가
                </button>
              </form>
              {mutedKeywords.length > 0 && (
                <div style={{ display: "flex", gap: "6px", flexWrap: "wrap", alignItems: "center" }}>
                  <span style={{ fontSize: "0.78rem", color: "var(--text-sub)", fontWeight: 700 }}>
                    뮤트 키워드:
                  </span>
                  {mutedKeywords.map((kw) => (
                    <button
                      key={kw}
                      type="button"
                      onClick={() => handleRemoveMutedKeyword(kw)}
                      className="applied-filter-chip"
                    >
                      <span>{kw}</span>
                      <X size={12} aria-hidden="true" />
                    </button>
                  ))}
                </div>
              )}
              {hiddenDealIds.length > 0 && (
                <div style={{ display: "flex", gap: "8px", flexWrap: "wrap", alignItems: "center" }}>
                  <span style={{ fontSize: "0.78rem", color: "var(--text-sub)", fontWeight: 700 }}>
                    숨긴 핫딜 {hiddenDealIds.length}건
                  </span>
                  {hiddenDealIds.slice(0, 12).map((id) => (
                    <button
                      key={id}
                      type="button"
                      onClick={() => handleUnhideDeal(id)}
                      className="applied-filter-chip"
                    >
                      <span>#{id} 복구</span>
                      <X size={12} aria-hidden="true" />
                    </button>
                  ))}
                  <button
                    type="button"
                    onClick={() => {
                      setHiddenDealIds([]);
                      if (typeof window !== "undefined") {
                        try {
                          localStorage.removeItem(HIDDEN_DEALS_STORAGE_KEY);
                        } catch {}
                      }
                      showToast("숨긴 핫딜을 모두 복구했어요");
                    }}
                    className="applied-filter-chip"
                  >
                    전체 복구
                  </button>
                </div>
              )}
            </div>
          )}

          {hasActiveFilters && (
            <div className="applied-filters-bar">
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: "8px",
                  flexWrap: "wrap",
                  fontSize: "var(--fs-caption)",
                }}
              >
                <span
                  style={{
                    fontWeight: 700,
                    color: "var(--text-sub)",
                    marginRight: "2px",
                  }}
                >
                  적용 필터
                </span>
                {appliedSearch && (
                  <button
                    type="button"
                    onClick={handleClearSearch}
                    className="applied-filter-chip applied-filter-chip--accent"
                  >
                    <span>검색: {appliedSearch}</span>
                    <X size={12} aria-hidden="true" />
                  </button>
                )}
                {selectedPlatforms.map((platform) => (
                  <button
                    key={platform}
                    type="button"
                    onClick={() => handlePlatformToggle(platform)}
                    className="applied-filter-chip"
                  >
                    <span>커뮤니티: {platform}</span>
                    <X size={12} aria-hidden="true" />
                  </button>
                ))}
                {selectedCategories.map((catKey) => {
                  const catLabel =
                    categories.find((c) => c.key === catKey)?.label || catKey;
                  return (
                    <button
                      key={catKey}
                      type="button"
                      onClick={() => handleCategoryToggle(catKey)}
                      className="applied-filter-chip"
                    >
                      <span>카테고리: {catLabel}</span>
                      <X size={12} aria-hidden="true" />
                    </button>
                  );
                })}
                {onlyHotDeals && (
                  <button
                    type="button"
                    onClick={handleToggleOnlyHot}
                    className="applied-filter-chip applied-filter-chip--orange"
                  >
                    <span>인기 딜만</span>
                    <X size={12} aria-hidden="true" />
                  </button>
                )}
                {hideClosedDeals && (
                  <button
                    type="button"
                    onClick={handleToggleHideClosed}
                    className="applied-filter-chip applied-filter-chip--accent"
                  >
                    <span>품절 제외</span>
                    <X size={12} aria-hidden="true" />
                  </button>
                )}
                {includeClosedDeals && (
                  <button
                    type="button"
                    onClick={handleToggleIncludeClosed}
                    className="applied-filter-chip applied-filter-chip--secondary"
                  >
                    <span>종료 포함</span>
                    <X size={12} aria-hidden="true" />
                  </button>
                )}
                {freeShippingOnly && (
                  <button
                    type="button"
                    onClick={handleToggleFreeShipping}
                    className="applied-filter-chip applied-filter-chip--accent"
                  >
                    <span>무료배송만</span>
                    <X size={12} aria-hidden="true" />
                  </button>
                )}
                {shopFilter && (
                  <button
                    type="button"
                    onClick={() => handleShopFilterChange("")}
                    className="applied-filter-chip"
                  >
                    <span>쇼핑몰: {shopFilter}</span>
                    <X size={12} aria-hidden="true" />
                  </button>
                )}
                {(minPriceInput || maxPriceInput) && (
                  <button
                    type="button"
                    onClick={() => handleApplyPriceRange("", "")}
                    className="applied-filter-chip"
                  >
                    <span>
                      가격:{" "}
                      {minPriceInput ? formatCompactPriceLabel(minPriceInput) : "0원"}
                      {" ~ "}
                      {maxPriceInput ? formatCompactPriceLabel(maxPriceInput) : "제한없음"}
                    </span>
                    <X size={12} aria-hidden="true" />
                  </button>
                )}
                {sortBy !== "latest" && (
                  <button
                    type="button"
                    onClick={() => handleSortChange("latest")}
                    className="applied-filter-chip"
                  >
                    <span>
                      정렬:{" "}
                      {{
                        latest: "최신순",
                        popular: "인기순",
                        price_asc: "낮은가격순",
                        price_desc: "높은가격순",
                        comments: "댓글순",
                        recommendations: "추천순",
                      }[sortBy]}
                    </span>
                    <X size={12} aria-hidden="true" />
                  </button>
                )}
              </div>
              <button
                type="button"
                onClick={handleResetAllFilters}
                className="applied-filter-reset-btn bounce-click"
              >
                <RotateCcw size={12} aria-hidden="true" />
                <span>필터 초기화</span>
              </button>
            </div>
          )}

          {fetchError && filteredDeals.length > 0 && (
            <div
              role="alert"
              style={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
                flexWrap: "wrap",
                gap: "10px",
                padding: "10px 14px",
                marginBottom: "16px",
                borderRadius: "var(--radius-md)",
                background: "var(--danger-bg)",
                border: "1px solid var(--danger-border)",
                color: "var(--danger-text)",
                fontSize: "var(--fs-control)",
                fontWeight: 700,
              }}
            >
              <span style={{ display: "inline-flex", alignItems: "center", gap: "6px" }}>
                <AlertCircle size={15} aria-hidden="true" />
                <span>{fetchError}</span>
              </span>
              <button
                type="button"
                onClick={() =>
                  void fetchLiveDeals(
                    true,
                    0,
                    selectedCategoriesRef.current,
                    selectedPlatformsRef.current
                  )
                }
                className="btn-primary bounce-click"
                style={{
                  minHeight: "var(--control-h-lg)",
                  padding: "8px 14px",
                  borderRadius: "var(--radius-sm)",
                  fontSize: "var(--fs-control)",
                }}
              >
                다시 시도
              </button>
            </div>
          )}

          <h2 className="sr-only">실시간 핫딜 목록</h2>
          <div className={viewMode === "compact" ? "deal-list deal-list--compact" : "deal-list"}>
            {loading && filteredDeals.length === 0 && !fetchError ? (
              <>
                <span className="sr-only" role="status">
                  핫딜을 불러오고 있어요
                </span>
                {Array.from({ length: 6 }).map((_, idx) => (
                  <div
                    key={idx}
                    className="glass-panel deal-card deal-skeleton-card"
                    aria-hidden="true"
                  >
                    <div className="deal-image-wrapper skeleton-shimmer" />
                    <div className="skeleton-shimmer skeleton-line skeleton-line--title" />
                    <div className="skeleton-shimmer skeleton-line skeleton-line--title-short" />
                    <div className="skeleton-row">
                      <span className="skeleton-shimmer skeleton-pill" />
                      <span className="skeleton-shimmer skeleton-pill skeleton-pill--sm" />
                    </div>
                    <div className="skeleton-price-row">
                      <div className="skeleton-shimmer skeleton-line skeleton-line--price" />
                      <div className="skeleton-shimmer skeleton-pill skeleton-pill--sm" />
                    </div>
                    <div className="deal-card-footer-actions">
                      <div className="skeleton-shimmer skeleton-btn" />
                      <div className="skeleton-shimmer skeleton-btn" />
                    </div>
                  </div>
                ))}
              </>
            ) : filteredDeals.length === 0 ? (
              <div
                className="glass-panel"
                role={fetchError ? "alert" : undefined}
                style={{
                  gridColumn: "1 / -1",
                  padding: "48px 24px",
                  borderRadius: "var(--radius-lg)",
                  textAlign: "center",
                  color: "var(--text-sub)",
                  display: "flex",
                  flexDirection: "column",
                  alignItems: "center",
                  gap: "12px",
                }}
              >
                {fetchError && (
                  <AlertCircle size={28} color="var(--danger-text)" aria-hidden="true" />
                )}
                <p
                  style={{
                    fontSize: "var(--fs-body)",
                    fontWeight: 700,
                    color: "var(--text-main)",
                    margin: 0,
                  }}
                >
                  {fetchError ? fetchError : "조건에 맞는 핫딜이 없어요"}
                </p>
                {fetchError ? (
                  <button
                    type="button"
                    onClick={() =>
                      void fetchLiveDeals(
                        true,
                        0,
                        selectedCategoriesRef.current,
                        selectedPlatformsRef.current
                      )
                    }
                    className="btn-primary bounce-click"
                    style={{
                      marginTop: "4px",
                      minHeight: "var(--control-h-lg)",
                      padding: "10px 18px",
                      borderRadius: "var(--radius-sm)",
                      fontSize: "var(--fs-control)",
                    }}
                  >
                    <RefreshCw size={15} aria-hidden="true" />
                    <span>다시 시도</span>
                  </button>
                ) : (
                  <>
                    {hasActiveFilters && (
                      <p
                        style={{
                          fontSize: "var(--fs-control)",
                          color: "var(--text-sub)",
                          margin: 0,
                        }}
                      >
                        조건을 조금 완화하면 더 많은 핫딜을 찾을 수 있어요
                      </p>
                    )}
                    {(!includeClosedDeals || onlyHotDeals || hasActiveFilters) && (
                      <div className="empty-quick-actions">
                        {!includeClosedDeals && (
                          <button
                            type="button"
                            onClick={handleToggleIncludeClosed}
                            className="empty-quick-btn bounce-click"
                          >
                            종료 포함해서 보기
                          </button>
                        )}
                        {onlyHotDeals && (
                          <button
                            type="button"
                            onClick={handleToggleOnlyHot}
                            className="empty-quick-btn bounce-click"
                          >
                            인기 딜만 끄기
                          </button>
                        )}
                        {hasActiveFilters && (
                          <button
                            type="button"
                            onClick={handleResetAllFilters}
                            className="btn-primary bounce-click"
                            style={{
                              minHeight: "var(--control-h)",
                              height: "var(--control-h)",
                              padding: "0 16px",
                              borderRadius: "var(--radius-sm)",
                              fontSize: "var(--fs-control)",
                            }}
                          >
                            <RotateCcw size={14} aria-hidden="true" />
                            <span>필터 초기화</span>
                          </button>
                        )}
                      </div>
                    )}
                  </>
                )}
              </div>
            ) : (
              filteredDeals.map((deal, idx) => (
                <DealCard
                  key={deal.id}
                  deal={deal}
                  appliedSearch={appliedSearch}
                  isMounted={isMounted}
                  currentTime={currentTime}
                  isWishlisted={wishlist.includes(deal.id)}
                  isAlertSet={alerts.includes(deal.id)}
                  priority={idx < 6}
                  viewMode={viewMode}
                  showCategoryPill={selectedCategories.length === 0}
                  onHideDeal={handleHideDeal}
                  onSelectDeal={(selected) => openDealWithHistory(selected)}
                  onShareDeal={handleShareDeal}
                  onToggleAlert={toggleAlert}
                  onToggleWishlist={toggleWishlist}
                  onShowToast={showToast}
                />
              ))
            )}
          </div>

          {hasMore && (
            <div
              ref={observerTarget}
              style={{
                display: "flex",
                justifyContent: "center",
                alignItems: "center",
                padding: "36px 0",
                width: "100%",
              }}
            >
              {isFetchingMore ? (
                <div
                  className="ai-loader"
                  style={{ width: "32px", height: "32px" }}
                />
              ) : (
                <div
                  style={{
                    color: "var(--text-sub)",
                    fontSize: "var(--fs-control)",
                    fontWeight: 600,
                    background: "var(--card-bg)",
                    padding: "8px 18px",
                    borderRadius: "var(--radius-pill)",
                    border: "1px solid var(--card-border)",
                  }}
                >
                  아래로 내리면 핫딜을 더 불러와요
                </div>
              )}
            </div>
          )}
        </section>

        <div role="status" aria-live="polite" aria-atomic="true">
          {toastMessage && <div className="toast">{toastMessage}</div>}
        </div>

        {showScrollTop && !selectedDeal && (
          <button
            type="button"
            className="scroll-top-fab bounce-click"
            aria-label="맨 위로 이동"
            title="맨 위로 이동"
            onClick={() => window.scrollTo({ top: 0, behavior: "smooth" })}
          >
            <ArrowUp size={18} aria-hidden="true" />
          </button>
        )}

        <footer className="footer">
          <div
            style={{
              display: "flex",
              flexDirection: "column",
              gap: "8px",
              alignItems: "center",
            }}
          >
            <span>© 2026 얼만데. All rights reserved.</span>
            <span style={{ fontSize: "0.8rem", color: "var(--text-sub)" }}>
              실시간 커뮤니티 특가 및 키워드 알림 서비스, 얼만데
            </span>
            <span
              style={{
                fontSize: "0.75rem",
                color: "var(--text-muted)",
                textAlign: "center",
                maxWidth: "640px",
                lineHeight: 1.5,
              }}
            >
              본 서비스에서 제공하는 일부 쇼핑몰 링크는 쿠팡 파트너스 및 제휴
              마케팅 활동의 일환으로 이에 따른 일정액의 수수료를 제공받을 수
              있으나, 구매 가격에는 영향을 미치지 않습니다.
            </span>
            <div
              style={{
                display: "flex",
                alignItems: "center",
                flexWrap: "wrap",
                justifyContent: "center",
                gap: "8px",
                fontSize: "0.82rem",
                marginTop: "4px",
              }}
            >
              <button
                type="button"
                className="footer-link-btn"
                onClick={() => setIsWishlistOpen(true)}
                style={{ color: "var(--danger-text)", fontWeight: 700 }}
              >
                <Heart size={14} fill="var(--danger-text)" aria-hidden="true" />
                <span>내 찜 목록</span>
              </button>
              <span style={{ color: "var(--card-border)" }}>|</span>
              <button
                type="button"
                className="footer-link-btn"
                onClick={() => setShowPrivacyModal(true)}
                style={{
                  color: "var(--accent)",
                  fontWeight: 700,
                  textDecoration: "underline",
                }}
              >
                개인정보 처리방침
              </button>
              <span style={{ color: "var(--card-border)" }}>|</span>
              <button
                type="button"
                className="footer-link-btn"
                onClick={() => setShowEulaModal(true)}
                style={{ color: "var(--text-sub)", fontWeight: 600 }}
              >
                서비스 이용약관
              </button>
              <span style={{ color: "var(--card-border)" }}>|</span>
              <a
                href="mailto:support@insightdeal.com?subject=%5B%EC%96%BC%EB%A7%8C%EB%8D%B0%5D%20%EB%AC%B8%EC%9D%98%20%EB%B0%8F%20%EC%98%A4%EB%A5%98%20%EC%A0%9C%EB%B3%B4"
                className="footer-link-btn"
                style={{ color: "var(--text-sub)", fontWeight: 600 }}
              >
                문의 · 오류 제보
              </a>
            </div>
          </div>
        </footer>
      </div>

      <DealDetailModal
        deal={selectedDeal}
        onClose={handleCloseDealModal}
        showToast={showToast}
        onTargetAlertSaved={handleTargetAlertSaved}
        onDealClosed={handleDealClosed}
        onNavigateDeal={
          filteredDeals.length > 1 && selectedDealIndex >= 0
            ? handleNavigateSelectedDeal
            : undefined
        }
        onSelectRelatedDeal={(rel) => openDealWithHistory(rel)}
      />

      <NotificationDrawer
        isOpen={isNotificationOpen}
        onClose={() => setIsNotificationOpen(false)}
        deals={deals}
        onSelectDeal={(deal) => openDealWithHistory(deal)}
        onSearchKeyword={(kw) => handleSelectPopularKeyword(kw)}
        onServerAlertCountChange={(cnt) => setServerAlertCount(cnt)}
      />

      <WishlistDrawer
        isOpen={isWishlistOpen}
        onClose={() => setIsWishlistOpen(false)}
        wishlistDeals={wishlistDeals}
        serverWishlists={serverWishlists}
        onRemoveDeal={toggleWishlist}
        onRemoveServerWishlist={handleRemoveServerWishlist}
        onSelectDeal={(deal) => {
          setIsWishlistOpen(false);
          openDealWithHistory(deal);
        }}
        onSearchKeyword={(kw) => handleSelectPopularKeyword(kw)}
        isLoggedIn={isLoggedIn}
        onLoginSimulate={handleLoginSimulate}
        onSyncComplete={hydrateFromServer}
        onShowToast={showToast}
      />

      <DeviceSyncModal
        isOpen={showSyncModal}
        onClose={() => setShowSyncModal(false)}
        onSyncComplete={hydrateFromServer}
        onShowToast={showToast}
      />

      <PrivacyModal
        isOpen={showPrivacyModal}
        onClose={() => setShowPrivacyModal(false)}
      />

      <EulaModal
        isOpen={showEulaModal}
        onClose={() => setShowEulaModal(false)}
      />
    </>
  );
}
