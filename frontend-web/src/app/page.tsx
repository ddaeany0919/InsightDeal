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
  Share2,
  Flame,
  ChevronRight,
  RotateCcw,
  ExternalLink,
  AlertCircle,
} from "lucide-react";
import Link from "next/link";

import type {
  Deal,
  DealSource,
  PopularKeywordRanking,
  RawDealApiResponse,
  HotDealsApiResponse,
  CategoryMetaItem,
  WishlistApiItem,
} from "./lib/types";
import {
  formatPrice,
  getProxyImageUrl,
  getFallbackProxyImageUrl,
  apiUrl,
  resolveOutboundUrl,
  trackOutboundClick,
  getOrCreateWebDeviceUuid,
  ensureWebDeviceAuth,
  authFetch,
  ensureWebPushSubscribed,
  getOptionSources,
  decodeHtmlEntities,
  resolveDealShopName,
  stripLeadingShopBracket,
  normalizeMallAlias,
  isNonMallLabel,
  formatRelativeTimeKo,
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
  openExternalUrlInToss,
  copyTextSafe,
} from "./lib/tossBridge";

import PrivacyModal from "./components/PrivacyModal";
import EulaModal from "./components/EulaModal";
import DealDetailModal from "./components/DealDetailModal";
import LoginPromptModal from "./components/LoginPromptModal";
import WishlistDrawer from "./components/WishlistDrawer";
import NotificationDrawer from "./components/NotificationDrawer";
import TrendingKeywordsTicker from "./components/TrendingKeywordsTicker";
import HighlightedTitle from "./components/HighlightedTitle";

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

const PLATFORMS = [
  "전체",
  "펨코",
  "뽐뿌",
  "루리웹",
  "퀘이사존",
  "클리앙",
  "알리뽐뿌",
  "빠삭국내",
  "빠삭해외",
  "빠삭육아",
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

export default function Home() {
  const [deals, setDeals] = useState<Deal[]>([]);
  const [loading, setLoading] = useState(true);
  const [fetchError, setFetchError] = useState<string | null>(null);
  const [categories, setCategories] =
    useState<CategoryMetaItem[]>(DEFAULT_CATEGORIES);
  const [isMounted, setIsMounted] = useState(false);
  const [activeTab, setActiveTab] = useState("전체");
  const [activePlatform, setActivePlatform] = useState("전체");
  const [deferredPrompt, setDeferredPrompt] =
    useState<BeforeInstallPromptEvent | null>(null);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [pullOffset, setPullOffset] = useState<number>(0);
  const [newDealsCount, setNewDealsCount] = useState<number>(0);
  const touchStartYRef = useRef<number>(0);
  const isPullingRef = useRef<boolean>(false);
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const [currentTime, setCurrentTime] = useState<number>(0);
  const [wishlist, setWishlist] = useState<number[]>([]);
  const [wishlistItemsMap, setWishlistItemsMap] = useState<Record<number, Deal>>(
    {}
  );
  const [alerts, setAlerts] = useState<number[]>([]);
  const wishlistRef = useRef<number[]>([]);
  const wishlistItemsMapRef = useRef<Record<number, Deal>>({});
  const alertsRef = useRef<number[]>([]);
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
  const [sortBy, setSortBy] = useState<"latest" | "popular" | "price_asc">(
    "latest"
  );
  const sortByRef = useRef<"latest" | "popular" | "price_asc">("latest");
  const [hideKeywordBanner, setHideKeywordBanner] = useState(false);
  const [isTossMiniApp, setIsTossMiniApp] = useState(
    process.env.NEXT_PUBLIC_TOSS_MINIAPP === "true"
  );

  const [hasMore, setHasMore] = useState(true);
  const [isFetchingMore, setIsFetchingMore] = useState(false);
  const observerTarget = useRef<HTMLDivElement>(null);

  const [showPrivacyModal, setShowPrivacyModal] = useState(false);
  const [showEulaModal, setShowEulaModal] = useState(false);
  const [showLoginModal, setShowLoginModal] = useState(false);

  const toastTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const liveDealsAbortRef = useRef<AbortController | null>(null);
  const isComposingRef = useRef(false);
  const searchDebounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pendingWishlistRef = useRef<Set<number>>(new Set());
  const pendingAlertRef = useRef<Set<number>>(new Set());

  const clearSearchDebounce = useCallback(() => {
    if (searchDebounceRef.current) {
      clearTimeout(searchDebounceRef.current);
      searchDebounceRef.current = null;
    }
  }, []);

  useEffect(() => {
    wishlistRef.current = wishlist;
  }, [wishlist]);

  useEffect(() => {
    wishlistItemsMapRef.current = wishlistItemsMap;
  }, [wishlistItemsMap]);

  useEffect(() => {
    alertsRef.current = alerts;
  }, [alerts]);

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

    await enqueueDealMutation(id, async () => {
      try {
        const auth = await ensureWebDeviceAuth();
        setIsLoggedIn(true);

        const targetDeal =
          deals.find((d) => d.id === id) || wishlistItemsMapRef.current[id];
        const keyword = buildDealWishlistKeyword(targetDeal?.title || `deal_${id}`);
        const isAdded = wishlistRef.current.includes(id);

        if (isAdded) {
          const prevSavedDeal = wishlistItemsMapRef.current[id];
          applyWishlistRemove(id);

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
                    }
                  }
                } else {
                  serverOk = false;
                }
              } else {
                removeWishlistIdEntry(id);
              }
            }
          }

          if (!serverOk) {
            applyWishlistAdd(id, prevSavedDeal);
            showToast("찜 해제에 실패했어요. 잠시 후 다시 시도해 주세요");
            return;
          }

          showToast("찜 목록에서 뺐어요");
        } else {
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

    await enqueueDealMutation(id, async () => {
      try {
        const auth = await ensureWebDeviceAuth();
        setIsLoggedIn(true);

        const targetDeal =
          deals.find((d) => d.id === id) || wishlistItemsMapRef.current[id];
        const keyword = buildDealWishlistKeyword(targetDeal?.title || `deal_${id}`);
        const isAlertOn = alertsRef.current.includes(id);

        if (isAlertOn) {
          let prevTargetPriceVal: number | undefined;
          try {
            const mapStr = localStorage.getItem("insightdeal_target_prices");
            const targetMap = mapStr ? JSON.parse(mapStr) : {};
            prevTargetPriceVal = targetMap[id];
          } catch {}

          applyAlertRemove(id);

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
                    }
                  }
                } else {
                  serverOk = false;
                }
              } else {
                removeWishlistIdEntry(id);
              }
            }
          }

          if (!serverOk) {
            applyAlertAdd(id);
            showToast("가격 알림 해제에 실패했어요. 잠시 후 다시 시도해 주세요");
            return;
          }

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
        showToast("가격 알림 설정에 실패했어요. 잠시 후 다시 시도해 주세요");
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
      setShowLoginModal(false);
      showToast("기기 연동을 완료했어요");
    } catch {
      showToast("기기 연동에 실패했어요. 잠시 후 다시 시도해 주세요");
    }
  };

  const fetchLiveDeals = useCallback(
    async (
      showLoadingState = true,
      offset = 0,
      targetTab = "전체",
      targetPlatform = "전체",
      overrideKeyword?: string,
      overrideSort?: "latest" | "popular" | "price_asc"
    ): Promise<boolean> => {
      if (liveDealsAbortRef.current) {
        liveDealsAbortRef.current.abort();
      }
      const controller = new AbortController();
      liveDealsAbortRef.current = controller;
      const { signal } = controller;

      try {
        if (offset === 0) {
          setFetchError(null);
        }
        if (showLoadingState) {
          setLoading(true);
        }

        const apiCategory =
          targetTab && targetTab !== "전체" ? targetTab : "";
        const platformParam =
          targetPlatform && targetPlatform !== "전체"
            ? `&platform=${encodeURIComponent(targetPlatform)}`
            : "";
        const limit = targetPlatform === "뽐뿌" ? 50 : 20;

        const activeKw =
          overrideKeyword !== undefined
            ? overrideKeyword
            : appliedSearchRef.current;
        const activeSort = overrideSort ?? sortByRef.current;
        const queryParam = activeKw.trim()
          ? `&keyword=${encodeURIComponent(activeKw.trim())}`
          : "";
        const sortParam = `&sort=${encodeURIComponent(activeSort)}`;
        const url = apiUrl(
          `/api/community/hot-deals?limit=${limit}&offset=${offset}${
            apiCategory ? `&category=${encodeURIComponent(apiCategory)}` : ""
          }${platformParam}${queryParam}${sortParam}`
        );

        let res: Response | null = null;
        let lastStatus = 0;
        for (let attempt = 0; attempt < 3; attempt++) {
          if (signal.aborted) return false;
          try {
            const r = await fetch(url, { signal });
            lastStatus = r.status;
            if (r.ok) {
              res = r;
              break;
            }
            if (r.status >= 400 && r.status < 500) {
              break;
            }
          } catch (fetchErr) {
            if (signal.aborted || isAbortError(fetchErr)) {
              return false;
            }
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
          mapRawDealToDeal(d, targetPlatform)
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

        if (formatted.length > 0) {
          if (offset === 0) {
            setDeals([...formatted]);
            if (
              typeof window !== "undefined" &&
              targetTab === "전체" &&
              targetPlatform === "전체" &&
              !activeKw.trim() &&
              activeSort === "latest"
            ) {
              localStorage.setItem(
                "insightdeal_cached_deals",
                JSON.stringify(formatted)
              );
            }
          } else {
            setDeals((prev) => {
              const existingIds = new Set(prev.map((item) => item.id));
              const newDeals = formatted.filter(
                (item: Deal) => !existingIds.has(item.id)
              );
              return [...prev, ...newDeals];
            });
          }

          setHasMore(
            explicitHasMore !== undefined
              ? explicitHasMore
              : formatted.length >= limit
          );
        } else {
          if (offset === 0) {
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
            targetTab === "전체" &&
            targetPlatform === "전체" &&
            activeSortOnErr === "latest"
          ) {
            try {
              const cached = localStorage.getItem("insightdeal_cached_deals");
              if (cached) {
                const parsed = JSON.parse(cached);
                if (Array.isArray(parsed) && parsed.length > 0) {
                  setDeals(parsed);
                }
              }
            } catch {}
          } else {
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
          if (showLoadingState) {
            setLoading(false);
          }
        }
      }
    },
    [showToast]
  );

  const handleRefresh = async () => {
    if (isRefreshing) return;
    setIsRefreshing(true);
    setNewDealsCount(0);
    const ok = await fetchLiveDeals(false, 0, activeTab, activePlatform);
    setIsRefreshing(false);
    showToast(ok ? "최신 딜을 불러왔어요" : HOT_DEALS_ERROR_COPY);
  };

  const handleScrollToTopAndRefresh = async () => {
    if (typeof window !== "undefined") {
      window.scrollTo({ top: 0, behavior: "smooth" });
    }
    setNewDealsCount(0);
    setIsRefreshing(true);
    const ok = await fetchLiveDeals(false, 0, activeTab, activePlatform);
    setIsRefreshing(false);
    showToast(ok ? "최신 딜을 반영했어요" : HOT_DEALS_ERROR_COPY);
  };

  const handleTabChange = (tab: string) => {
    if (tab === activeTab) return;
    clearSearchDebounce();
    setActiveTab(tab);
    setNewDealsCount(0);
    setIsFetchingMore(false);
    setDeals([]);
    setHasMore(true);
    fetchLiveDeals(true, 0, tab, activePlatform);
  };

  const handlePlatformChange = (platform: string) => {
    if (platform === activePlatform) return;
    clearSearchDebounce();
    setActivePlatform(platform);
    setNewDealsCount(0);
    setIsFetchingMore(false);
    setDeals([]);
    setHasMore(true);
    fetchLiveDeals(true, 0, activeTab, platform);
  };

  const saveRecentSearch = (rawKw: string) => {
    const trimmed = rawKw.trim();
    if (!trimmed) return;
    setRecentSearches((prev) => {
      const next = [trimmed, ...prev.filter((item) => item !== trimmed)].slice(
        0,
        8
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

  const scheduleDebouncedSearch = (
    nextValue: string,
    targetTab: string,
    targetPlatform: string
  ) => {
    clearSearchDebounce();
    if (isComposingRef.current) return;
    if (!isAutoSearchableQuery(nextValue)) return;

    searchDebounceRef.current = setTimeout(() => {
      searchDebounceRef.current = null;
      const trimmed = nextValue.trim();
      if (appliedSearchRef.current === trimmed) return;
      updateAppliedSearch(trimmed);
      setIsFetchingMore(false);
      setDeals([]);
      setHasMore(true);
      fetchLiveDeals(true, 0, targetTab, targetPlatform, trimmed);
    }, 300);
  };

  const handleClearSearch = () => {
    clearSearchDebounce();
    isComposingRef.current = false;
    setSearchQuery("");
    updateAppliedSearch("");
    setIsFetchingMore(false);
    setDeals([]);
    setHasMore(true);
    fetchLiveDeals(true, 0, activeTab, activePlatform, "");
  };

  const handleSortChange = (nextSort: "latest" | "popular" | "price_asc") => {
    if (sortByRef.current === nextSort) return;
    sortByRef.current = nextSort;
    setSortBy(nextSort);
    setIsFetchingMore(false);
    setDeals([]);
    setHasMore(true);
    fetchLiveDeals(true, 0, activeTab, activePlatform, undefined, nextSort);
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
    setDeals([]);
    setHasMore(true);
    fetchLiveDeals(true, 0, activeTab, activePlatform, trimmed);
  };

  const handleSelectPopularKeyword = (kw: string) => {
    clearSearchDebounce();
    isComposingRef.current = false;
    const trimmed = kw.trim();
    if (appliedSearchRef.current === trimmed) {
      setSearchQuery("");
      updateAppliedSearch("");
      setIsFetchingMore(false);
      setDeals([]);
      setHasMore(true);
      fetchLiveDeals(true, 0, activeTab, activePlatform, "");
      return;
    }
    setSearchQuery(trimmed);
    updateAppliedSearch(trimmed);
    saveRecentSearch(trimmed);
    setActivePlatform("전체");
    setActiveTab("전체");
    setIsFetchingMore(false);
    setDeals([]);
    setHasMore(true);
    fetchLiveDeals(true, 0, "전체", "전체", trimmed);
  };

  const handleResetAllFilters = () => {
    clearSearchDebounce();
    isComposingRef.current = false;
    setSearchQuery("");
    updateAppliedSearch("");
    setActiveTab("전체");
    setActivePlatform("전체");
    setOnlyHotDeals(false);
    setHideClosedDeals(false);
    sortByRef.current = "latest";
    setSortBy("latest");
    setIsFetchingMore(false);
    setDeals([]);
    setHasMore(true);
    fetchLiveDeals(true, 0, "전체", "전체", "", "latest");
    showToast("필터를 초기화했어요");
  };

  const hasActiveFilters =
    appliedSearch !== "" ||
    activePlatform !== "전체" ||
    activeTab !== "전체" ||
    onlyHotDeals ||
    hideClosedDeals ||
    sortBy !== "latest";

  useEffect(() => {
    setIsMounted(true);
    setCurrentTime(Date.now());

    const handleBeforeInstallPrompt = (e: Event) => {
      e.preventDefault();
      setDeferredPrompt(e as BeforeInstallPromptEvent);
    };

    let initialSharedKw = "";
    let initialDealId: number | null = null;
    if (typeof window !== "undefined") {
      window.addEventListener("beforeinstallprompt", handleBeforeInstallPrompt);

      const urlParams = new URLSearchParams(window.location.search);
      const sharedKw = urlParams.get("keyword");
      if (sharedKw && sharedKw.trim()) {
        initialSharedKw = sharedKw.trim();
        setSearchQuery(initialSharedKw);
        updateAppliedSearch(initialSharedKw);
      }

      const dealIdRaw = urlParams.get("dealId");
      if (dealIdRaw && /^\d+$/.test(dealIdRaw.trim())) {
        const parsedId = Number(dealIdRaw.trim());
        if (Number.isSafeInteger(parsedId) && parsedId > 0) {
          initialDealId = parsedId;
        }
      }

      if (localStorage.getItem("insightdeal_hide_kw_banner") === "1") {
        setHideKeywordBanner(true);
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

    let cachedDeals: Deal[] = [];
    if (typeof window !== "undefined") {
      const saved = localStorage.getItem("insightdeal_cached_deals");
      if (saved && !initialSharedKw) {
        try {
          cachedDeals = JSON.parse(saved);
          if (cachedDeals && cachedDeals.length > 0) {
            setDeals(cachedDeals);
            setLoading(false);
          }
        } catch {}
      }

      const token = getOrCreateBrowserUserId();
      if (token) {
        setIsLoggedIn(true);
        ensureWebDeviceAuth()
          .then(() =>
            authFetch(
              (latestAuth) =>
                `/api/users/notifications?user_id=${encodeURIComponent(latestAuth.deviceUuid)}`
            )
          )
          .then((r) => (r.ok ? r.json() : []))
          .then((list) => {
            if (Array.isArray(list)) setServerAlertCount(list.length);
          })
          .catch(() => {});
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
          if (Array.isArray(parsed)) setRecentSearches(parsed.slice(0, 8));
        } catch {}
      }
    }

    const initFetch = async () => {
      const hasCache = cachedDeals.length > 0 && !initialSharedKw;
      setIsRefreshing(true);
      await fetchLiveDeals(!hasCache, 0, "전체", "전체", initialSharedKw);
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
      }
    };
  }, [clearSearchDebounce, fetchLiveDeals, getOrCreateBrowserUserId, updateAppliedSearch]);

  useEffect(() => {
    if (!isMounted || !hasMore || loading || isRefreshing) return;

    const observer = new IntersectionObserver(
      async (entries) => {
        if (entries[0].isIntersecting && !isFetchingMore) {
          setIsFetchingMore(true);
          try {
            const currentOffset = deals.length;
            await fetchLiveDeals(false, currentOffset, activeTab, activePlatform);
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
    activeTab,
    activePlatform,
    loading,
    isRefreshing,
    isFetchingMore,
    fetchLiveDeals,
  ]);

  useEffect(() => {
    if (typeof window === "undefined") return;

    const handleTouchStart = (e: TouchEvent) => {
      if (
        selectedDeal ||
        isWishlistOpen ||
        isNotificationOpen ||
        showPrivacyModal ||
        showEulaModal ||
        showLoginModal
      ) {
        isPullingRef.current = false;
        return;
      }
      if (window.scrollY <= 0 && e.touches.length === 1) {
        touchStartYRef.current = e.touches[0].clientY;
        isPullingRef.current = true;
      } else {
        isPullingRef.current = false;
      }
    };

    const handleTouchMove = (e: TouchEvent) => {
      if (!isPullingRef.current || isRefreshing) return;
      if (window.scrollY > 0) {
        isPullingRef.current = false;
        setPullOffset(0);
        return;
      }
      const currentY = e.touches[0].clientY;
      const diff = currentY - touchStartYRef.current;
      if (diff > 0) {
        const damped = Math.min(75, Math.pow(diff, 0.82) * 1.2);
        setPullOffset(damped);
      } else {
        setPullOffset(0);
      }
    };

    const handleTouchEnd = async () => {
      if (!isPullingRef.current) return;
      isPullingRef.current = false;
      if (pullOffset >= 50 && !isRefreshing) {
        setPullOffset(46);
        setIsRefreshing(true);
        setNewDealsCount(0);
        try {
          const ok = await fetchLiveDeals(false, 0, activeTab, activePlatform);
          showToast(ok ? "최신 딜을 불러왔어요" : HOT_DEALS_ERROR_COPY);
        } finally {
          setIsRefreshing(false);
          setPullOffset(0);
        }
      } else {
        setPullOffset(0);
      }
    };

    window.addEventListener("touchstart", handleTouchStart, { passive: true });
    window.addEventListener("touchmove", handleTouchMove, { passive: true });
    window.addEventListener("touchend", handleTouchEnd);

    return () => {
      window.removeEventListener("touchstart", handleTouchStart);
      window.removeEventListener("touchmove", handleTouchMove);
      window.removeEventListener("touchend", handleTouchEnd);
    };
  }, [
    isRefreshing,
    activeTab,
    activePlatform,
    pullOffset,
    selectedDeal,
    isWishlistOpen,
    isNotificationOpen,
    showPrivacyModal,
    showEulaModal,
    showLoginModal,
    fetchLiveDeals,
    showToast,
  ]);

  useEffect(() => {
    if (!isMounted || deals.length === 0) return;

    const checkInterval = setInterval(async () => {
      if (document.hidden) return;

      try {
        const apiCategory =
          activeTab && activeTab !== "전체" ? activeTab : "";
        const platformParam =
          activePlatform && activePlatform !== "전체"
            ? `&platform=${encodeURIComponent(activePlatform)}`
            : "";
        const queryParam = appliedSearch
          ? `&keyword=${encodeURIComponent(appliedSearch)}`
          : "";
        const checkUrl = apiUrl(
          `/api/community/hot-deals?limit=8&offset=0${
            apiCategory ? `&category=${encodeURIComponent(apiCategory)}` : ""
          }${platformParam}${queryParam}`
        );

        const res = await fetch(checkUrl);
        if (!res.ok) return;
        const data = await res.json();
        const incomingDeals: RawDealApiResponse[] = Array.isArray(data)
          ? data
          : data.deals || data.items || [];
        if (incomingDeals.length > 0 && deals.length > 0) {
          const latestKnownId = deals[0]?.id || 0;
          const freshCount = incomingDeals.filter(
            (d: RawDealApiResponse) => d.id > latestKnownId
          ).length;
          if (freshCount > 0) {
            setNewDealsCount(freshCount);
          }
        }
      } catch {}
    }, 50000);

    return () => clearInterval(checkInterval);
  }, [isMounted, deals, activeTab, activePlatform, appliedSearch]);

  const getLiveTimeAgo = (dateStr?: string, fallback?: string) => {
    if (!isMounted || currentTime <= 0) return fallback || "최근";
    return formatRelativeTimeKo(dateStr, currentTime, fallback || "최근");
  };

  const filteredDeals = deals
    .filter((d) => {
      if (hideClosedDeals && d.isClosed) return false;
      if (onlyHotDeals && (d.honeyScore || 0) < 100) return false;
      if (activePlatform && activePlatform !== "전체") {
        const commNames = (d.community_name || "")
          .split(",")
          .map((s) => s.trim());
        const srcNames = (d.sources || []).map((s: DealSource) =>
          String(s.site_name || "")
            .replace(/ - .*$/, "")
            .trim()
        );
        if (
          !commNames.includes(activePlatform) &&
          !srcNames.includes(activePlatform)
        ) {
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
      if (sortBy === "price_asc") {
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
        const pa = toApproxKrw(a);
        const pb = toApproxKrw(b);
        if (pa <= 0 && pb <= 0) return 0;
        if (pa <= 0) return 1;
        if (pb <= 0) return -1;
        return pa - pb;
      }
      return 0;
    });

  const wishlistDeals = wishlist
    .map((id) => deals.find((d) => d.id === id) || wishlistItemsMap[id])
    .filter((d): d is Deal => Boolean(d));

  return (
    <>
      {(pullOffset > 0 || isRefreshing) && (
        <div
          className="ptr-spinner"
          style={{
            top: `calc(${Math.max(12, Math.min(84, pullOffset + 12))}px + env(safe-area-inset-top, 0px))`,
            transition: isPullingRef.current
              ? "none"
              : "top 0.25s ease, opacity 0.25s ease",
            opacity: isRefreshing ? 1 : Math.min(1, pullOffset / 40),
          }}
        >
          <RefreshCw
            size={16}
            color="var(--accent)"
            className={isRefreshing ? "spin-animation" : ""}
            style={{
              transform: isRefreshing
                ? undefined
                : `rotate(${pullOffset * 6}deg)`,
              transition: isRefreshing ? undefined : "transform 0.05s linear",
            }}
          />
        </div>
      )}

      {newDealsCount > 0 && (
        <button
          type="button"
          onClick={handleScrollToTopAndRefresh}
          className="floating-new-deals-pill bounce-click"
        >
          <RefreshCw size={14} />
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
          onClick={() => {
            clearSearchDebounce();
            isComposingRef.current = false;
            setSearchQuery("");
            updateAppliedSearch("");
            setActiveTab("전체");
            setActivePlatform("전체");
            setOnlyHotDeals(false);
            setHideClosedDeals(false);
            sortByRef.current = "latest";
            setSortBy("latest");
            setIsFetchingMore(false);
            setDeals([]);
            setHasMore(true);
            fetchLiveDeals(true, 0, "전체", "전체", "", "latest");
          }}
        >
          <svg
            width="28"
            height="28"
            viewBox="0 0 24 24"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
          >
            <rect width="24" height="24" rx="7" fill="var(--deal-orange)" />
            <path
              d="M17.65 12.82L12.18 7.35C11.96 7.13 11.66 7 11.33 7H7.2C6.54 7 6 7.54 6 8.2V12.33C6 12.66 6.13 12.96 6.35 13.18L11.82 18.65C12.29 19.12 13.05 19.12 13.52 18.65L17.65 14.52C18.12 14.05 18.12 13.29 17.65 12.82ZM8.7 10.6C8.04 10.6 7.5 10.06 7.5 9.4C7.5 8.74 8.04 8.2 8.7 8.2C9.36 8.2 9.9 8.74 9.9 9.4C9.9 10.06 9.36 10.6 8.7 10.6Z"
              fill="#FFFFFF"
            />
          </svg>
          <span>InsightDeal</span>
          <span
            style={{
              background: "var(--deal-orange-bg)",
              color: "var(--deal-orange)",
              fontSize: "0.68rem",
              fontWeight: 800,
              padding: "2px 6px",
              borderRadius: "5px",
              letterSpacing: "0.02em",
              lineHeight: 1.2,
            }}
          >
            LIVE
          </span>
        </button>

        <div className="nav-actions">
          <div className="global-counters">
            <button
              type="button"
              className="counter-item bounce-click"
              aria-label="내 찜 목록 열기"
              onClick={() => setIsWishlistOpen(true)}
            >
              <Heart
                size={16}
                color={wishlist.length > 0 ? "#EF4444" : "var(--text-sub)"}
                fill={wishlist.length > 0 ? "#EF4444" : "none"}
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
              <Settings size={16} color="var(--text-sub)" />
              <span className="counter-label">키워드 알림</span>
            </Link>
          </div>
          {!isTossMiniApp && (
            <button
              type="button"
              className="btn-primary"
              aria-label="앱 설치"
              onClick={handleInstallApp}
            >
              <Download size={17} />
              <span>앱 설치</span>
            </button>
          )}
        </div>
      </header>

      <div className="web-container">
        <section style={{ padding: "16px 0 80px 0" }}>
          {!hideKeywordBanner && (
            <div
              style={{
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
                padding: "4px 6px 4px 14px",
                marginBottom: "16px",
                borderRadius: "12px",
                background: "var(--card-bg)",
                border: "1px solid var(--card-border)",
                boxShadow: "var(--glass-shadow)",
                gap: "8px",
              }}
            >
              <Link
                href="/settings/notifications"
                style={{
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "space-between",
                  flex: 1,
                  minHeight: "44px",
                  textDecoration: "none",
                  gap: "10px",
                }}
              >
                <div
                  style={{
                    display: "flex",
                    alignItems: "center",
                    gap: "10px",
                  }}
                >
                  <div
                    style={{
                      width: "26px",
                      height: "26px",
                      borderRadius: "8px",
                      background: "var(--deal-orange-bg)",
                      display: "flex",
                      alignItems: "center",
                      justifyContent: "center",
                      flexShrink: 0,
                    }}
                  >
                    <Bell
                      size={14}
                      strokeWidth={2.3}
                      color="var(--deal-orange)"
                    />
                  </div>
                  <div
                    style={{
                      display: "flex",
                      alignItems: "center",
                      gap: "8px",
                      flexWrap: "wrap",
                    }}
                  >
                    <span
                      style={{
                        fontSize: "0.88rem",
                        color: "var(--text-main)",
                        fontWeight: 700,
                      }}
                    >
                      찾는 상품 키워드 알림 받기
                    </span>
                    <span
                      style={{
                        fontSize: "0.8rem",
                        color: "var(--text-sub)",
                        fontWeight: 500,
                      }}
                    >
                      새 특가가 올라오면 바로 알려드려요
                    </span>
                  </div>
                </div>
                <div
                  style={{
                    display: "flex",
                    alignItems: "center",
                    gap: "2px",
                    fontWeight: 700,
                    fontSize: "0.82rem",
                    color: "var(--deal-orange)",
                    flexShrink: 0,
                  }}
                >
                  설정 <ChevronRight size={15} />
                </div>
              </Link>
              <button
                type="button"
                aria-label="배너 닫기"
                title="배너 닫기"
                className="banner-close-btn"
                onClick={() => {
                  setHideKeywordBanner(true);
                  if (typeof window !== "undefined") {
                    localStorage.setItem("insightdeal_hide_kw_banner", "1");
                  }
                }}
              >
                <X size={16} />
              </button>
            </div>
          )}

          <div
            style={{
              display: "flex",
              justifyContent: "space-between",
              alignItems: "center",
              flexWrap: "wrap",
              gap: "12px",
              marginBottom: "18px",
            }}
          >
            <div>
              <h1
                className="section-title"
                style={{
                  textAlign: "left",
                  fontSize: "1.55rem",
                  marginBottom: "4px",
                }}
              >
                실시간 핫딜 모아보기
              </h1>
              <p style={{ color: "var(--text-sub)", fontSize: "0.9rem" }}>
                뽐뿌 · 펨코 · 루리웹 · 퀘이사존 · 클리앙 · 빠삭의 특가를 한곳에서
                비교하세요
              </p>
            </div>

            <div
              style={{
                display: "flex",
                gap: "8px",
                alignItems: "center",
                flexWrap: "wrap",
              }}
            >
              <button
                type="button"
                onClick={() => setOnlyHotDeals((prev) => !prev)}
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
                <Flame size={14} />
                <span>인기 딜만</span>
              </button>

              <button
                type="button"
                onClick={() => setHideClosedDeals((prev) => !prev)}
                className="filter-chip-btn bounce-click"
                style={{
                  background: hideClosedDeals
                    ? "rgba(var(--accent-rgb), 0.1)"
                    : "var(--card-bg)",
                  color: hideClosedDeals ? "var(--accent)" : "var(--text-sub)",
                  border: hideClosedDeals
                    ? "1px solid var(--accent)"
                    : "1px solid var(--card-border)",
                }}
              >
                품절 숨기기
              </button>

              <div className="sort-segment-group" role="group" aria-label="정렬 기준">
                {(
                  [
                    { key: "latest", label: "최신순" },
                    { key: "popular", label: "인기순" },
                    { key: "price_asc", label: "낮은가격순" },
                  ] as const
                ).map((opt) => {
                  const active = sortBy === opt.key;
                  return (
                    <button
                      key={opt.key}
                      type="button"
                      className="sort-segment-btn"
                      onClick={() => handleSortChange(opt.key)}
                      style={{
                        background: active ? "var(--accent)" : "transparent",
                        color: active ? "#FFFFFF" : "var(--text-sub)",
                        fontWeight: active ? 700 : 600,
                      }}
                    >
                      {opt.label}
                    </button>
                  );
                })}
              </div>

              {hasActiveFilters && (
                <button
                  type="button"
                  className="filter-chip-btn bounce-click"
                  onClick={handleResetAllFilters}
                  style={{
                    background: "rgba(var(--accent-rgb), 0.1)",
                    color: "var(--accent)",
                    border: "1px solid rgba(var(--accent-rgb), 0.3)",
                  }}
                >
                  <RotateCcw size={13} />
                  <span>초기화</span>
                </button>
              )}

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
                  className={isRefreshing ? "spin-animation" : ""}
                />
                <span>새로고침</span>
              </button>
            </div>
          </div>

          <form
            onSubmit={handleSearchSubmit}
            className="search-container"
            style={{ marginBottom: "12px" }}
          >
            <div className="search-bar-wrapper">
              <Search size={18} className="search-icon" />
              <input
                type="text"
                placeholder="상품명·조건 검색 (예: 맥북 에어 -파우치 / 제로콜라 :15000)"
                value={searchQuery}
                onCompositionStart={() => {
                  isComposingRef.current = true;
                  clearSearchDebounce();
                }}
                onCompositionEnd={(e) => {
                  isComposingRef.current = false;
                  const composedVal = e.currentTarget.value;
                  setSearchQuery(composedVal);
                  scheduleDebouncedSearch(composedVal, activeTab, activePlatform);
                }}
                onChange={(e) => {
                  const nextVal = e.target.value;
                  setSearchQuery(nextVal);
                  const nativeEvt = e.nativeEvent as InputEvent;
                  if (nativeEvt?.isComposing || isComposingRef.current) {
                    return;
                  }
                  scheduleDebouncedSearch(nextVal, activeTab, activePlatform);
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

          {recentSearches.length > 0 && (
            <div
              style={{
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
                gap: "8px",
                flexWrap: "wrap",
                marginBottom: "12px",
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
                        onClick={() => handleSelectPopularKeyword(kw)}
                        style={{
                          cursor: "pointer",
                          minHeight: "44px",
                          display: "inline-flex",
                          alignItems: "center",
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
                marginBottom: "14px",
                borderRadius: "12px",
                background: "rgba(var(--accent-rgb), 0.06)",
                border: "1px solid rgba(var(--accent-rgb), 0.2)",
              }}
            >
              <span
                style={{
                  fontSize: "0.86rem",
                  color: "var(--text-main)",
                  fontWeight: 600,
                  display: "inline-flex",
                  alignItems: "center",
                  gap: "6px",
                }}
              >
                <Bell size={15} color="var(--accent)" />
                <span>
                  <strong>&lsquo;{searchQuery.trim()}&rsquo;</strong> 새 딜이
                  올라오면 바로 알려드릴까요?
                </span>
              </span>
              <button
                type="button"
                onClick={() => handleQuickSubscribeKeyword(searchQuery)}
                className="bounce-click"
                style={{
                  minHeight: "40px",
                  padding: "8px 14px",
                  borderRadius: "10px",
                  background: "var(--accent)",
                  color: "#FFFFFF",
                  fontWeight: 700,
                  fontSize: "0.8rem",
                  border: "none",
                  cursor: "pointer",
                }}
              >
                알림 받기
              </button>
            </div>
          )}

          <TrendingKeywordsTicker
            keywords={popularKeywords}
            rankings={popularRankings}
            updatedAt={popularUpdatedAt}
            selectedKeyword={appliedSearch}
            onSelectKeyword={handleSelectPopularKeyword}
          />

          <div className="platform-chips-row">
            {PLATFORMS.map((platform) => {
              const isSelected = activePlatform === platform;
              return (
                <button
                  key={platform}
                  type="button"
                  onClick={() => handlePlatformChange(platform)}
                  className="platform-chip bounce-click"
                  style={{
                    fontWeight: isSelected ? 700 : 600,
                    background: isSelected ? "var(--accent)" : "var(--card-bg)",
                    color: isSelected ? "#FFFFFF" : "var(--text-sub)",
                    border: isSelected
                      ? "1px solid var(--accent)"
                      : "1px solid var(--card-border)",
                  }}
                >
                  {platform === "전체" ? "전체 커뮤니티" : platform}
                </button>
              );
            })}
          </div>

          <div className="tabs-container">
            <div className="tabs">
              {categories.map((cat) => (
                <button
                  key={cat.key}
                  type="button"
                  className={`tab bounce-click ${
                    activeTab === cat.key ? "active" : ""
                  }`}
                  onClick={() => handleTabChange(cat.key)}
                >
                  {cat.label}
                </button>
              ))}
            </div>
          </div>

          {hasActiveFilters && (
            <div
              style={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
                flexWrap: "wrap",
                gap: "8px",
                padding: "8px 14px",
                marginBottom: "16px",
                borderRadius: "12px",
                background: "var(--card-bg)",
                border: "1px solid var(--card-border)",
              }}
            >
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: "6px",
                  flexWrap: "wrap",
                  fontSize: "0.78rem",
                }}
              >
                <span
                  style={{
                    fontWeight: 700,
                    color: "var(--text-sub)",
                    marginRight: "2px",
                  }}
                >
                  적용 중인 필터
                </span>
                {appliedSearch && (
                  <button
                    type="button"
                    onClick={handleClearSearch}
                    style={{
                      display: "inline-flex",
                      alignItems: "center",
                      minHeight: "44px",
                      gap: "4px",
                      padding: "6px 12px",
                      borderRadius: "999px",
                      background: "rgba(var(--accent-rgb), 0.1)",
                      color: "var(--accent)",
                      border: "1px solid rgba(var(--accent-rgb), 0.28)",
                      fontSize: "0.76rem",
                      fontWeight: 700,
                      cursor: "pointer",
                    }}
                  >
                    <span>검색: {appliedSearch}</span>
                    <X size={12} />
                  </button>
                )}
                {activePlatform !== "전체" && (
                  <button
                    type="button"
                    onClick={() => handlePlatformChange("전체")}
                    style={{
                      display: "inline-flex",
                      alignItems: "center",
                      minHeight: "44px",
                      gap: "4px",
                      padding: "6px 12px",
                      borderRadius: "999px",
                      background: "var(--surface-subtle)",
                      color: "var(--text-main)",
                      border: "1px solid var(--card-border)",
                      fontSize: "0.76rem",
                      fontWeight: 700,
                      cursor: "pointer",
                    }}
                  >
                    <span>커뮤니티: {activePlatform}</span>
                    <X size={12} />
                  </button>
                )}
                {activeTab !== "전체" && (
                  <button
                    type="button"
                    onClick={() => handleTabChange("전체")}
                    style={{
                      display: "inline-flex",
                      alignItems: "center",
                      minHeight: "44px",
                      gap: "4px",
                      padding: "6px 12px",
                      borderRadius: "999px",
                      background: "var(--surface-subtle)",
                      color: "var(--text-main)",
                      border: "1px solid var(--card-border)",
                      fontSize: "0.76rem",
                      fontWeight: 700,
                      cursor: "pointer",
                    }}
                  >
                    <span>
                      카테고리:{" "}
                      {categories.find((c) => c.key === activeTab)?.label ||
                        activeTab}
                    </span>
                    <X size={12} />
                  </button>
                )}
                {onlyHotDeals && (
                  <span
                    style={{
                      fontSize: "0.76rem",
                      fontWeight: 700,
                      color: "var(--deal-orange)",
                    }}
                  >
                    · 인기 딜만
                  </span>
                )}
                {hideClosedDeals && (
                  <span
                    style={{
                      fontSize: "0.76rem",
                      fontWeight: 700,
                      color: "var(--accent)",
                    }}
                  >
                    · 품절 숨김
                  </span>
                )}
              </div>
              <button
                type="button"
                onClick={handleResetAllFilters}
                className="bounce-click"
                style={{
                  display: "inline-flex",
                  alignItems: "center",
                  minHeight: "44px",
                  gap: "5px",
                  padding: "6px 12px",
                  borderRadius: "8px",
                  background: "var(--surface-subtle)",
                  color: "var(--text-main)",
                  border: "1px solid var(--card-border)",
                  fontSize: "0.76rem",
                  fontWeight: 700,
                  cursor: "pointer",
                }}
              >
                <RotateCcw size={12} />
                <span>전체 초기화</span>
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
                borderRadius: "12px",
                background: "rgba(239, 68, 68, 0.08)",
                border: "1px solid rgba(239, 68, 68, 0.28)",
                color: "#EF4444",
                fontSize: "0.84rem",
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
                  void fetchLiveDeals(true, 0, activeTab, activePlatform)
                }
                className="btn-primary bounce-click"
                style={{
                  minHeight: "38px",
                  padding: "6px 14px",
                  borderRadius: "10px",
                  fontSize: "0.8rem",
                }}
              >
                다시 시도
              </button>
            </div>
          )}

          <div className="deal-list">
            {filteredDeals.length === 0 ? (
              <div
                className="glass-panel"
                role={fetchError ? "alert" : undefined}
                style={{
                  gridColumn: "1 / -1",
                  padding: "48px 24px",
                  borderRadius: "20px",
                  textAlign: "center",
                  color: "var(--text-sub)",
                  display: "flex",
                  flexDirection: "column",
                  alignItems: "center",
                  gap: "12px",
                }}
              >
                {fetchError && !loading && (
                  <AlertCircle size={28} color="#EF4444" aria-hidden="true" />
                )}
                <p
                  style={{
                    fontSize: "0.98rem",
                    fontWeight: 700,
                    color: "var(--text-main)",
                    margin: 0,
                  }}
                >
                  {loading && deals.length === 0
                    ? "핫딜을 불러오고 있어요"
                    : fetchError
                      ? fetchError
                      : "찾으시는 조건의 딜이 없어요"}
                </p>
                {!loading && fetchError ? (
                  <button
                    type="button"
                    onClick={() =>
                      void fetchLiveDeals(true, 0, activeTab, activePlatform)
                    }
                    className="btn-primary bounce-click"
                    style={{
                      marginTop: "4px",
                      padding: "10px 18px",
                      borderRadius: "12px",
                      fontSize: "0.85rem",
                    }}
                  >
                    <RefreshCw size={15} aria-hidden="true" />
                    <span>다시 시도</span>
                  </button>
                ) : (
                  !loading &&
                  hasActiveFilters && (
                    <>
                      <p
                        style={{
                          fontSize: "0.84rem",
                          color: "var(--text-sub)",
                          margin: 0,
                        }}
                      >
                        검색어나 필터를 초기화하면 전체 딜을 볼 수 있어요
                      </p>
                      <button
                        type="button"
                        onClick={handleResetAllFilters}
                        className="btn-primary bounce-click"
                        style={{
                          marginTop: "4px",
                          padding: "10px 18px",
                          borderRadius: "12px",
                          fontSize: "0.85rem",
                        }}
                      >
                        <RotateCcw size={15} />
                        <span>필터 초기화</span>
                      </button>
                    </>
                  )
                )}
              </div>
            ) : (
              filteredDeals.map((deal) => {
                const isWishlisted = wishlist.includes(deal.id);
                const isAlertSet = alerts.includes(deal.id);

                const hasValidEcommerceLink =
                  deal.ecommerce_url &&
                  deal.ecommerce_url !== "#" &&
                  String(deal.ecommerce_url).trim() !== "";
                const outboundTarget = hasValidEcommerceLink
                  ? resolveOutboundUrl(deal.ecommerce_url)
                  : deal.post_link && deal.post_link !== "#"
                    ? resolveOutboundUrl(deal.post_link)
                    : "#";

                const optionSources = getOptionSources(
                  deal.sources,
                  deal.options_data
                );
                const optionCount = deal.options_count || optionSources.length;
                const isMultiDeal = Boolean(
                  deal.has_options || optionCount >= 2
                );
                const displayCount =
                  optionCount >= 2 ? optionCount : deal.has_options ? 2 : 0;

                const shopBadge = resolveDealShopName({
                  shop_name: deal.shop_name,
                  title: deal.title,
                  ecommerce_url: deal.ecommerce_url,
                });
                const displayTitle = stripLeadingShopBracket(
                  deal.title,
                  shopBadge
                );
                const displayShipping = deal.shipping_fee
                  ? decodeHtmlEntities(deal.shipping_fee)
                  : null;

                const badgeSources: DealSource[] =
                  deal.sources && deal.sources.length > 0
                    ? deal.sources
                    : deal.community_name.split(", ").map((cName) => ({
                        site_name: cName,
                        post_url: deal.post_link,
                      }));

                const uniqueBadgeSources: DealSource[] = [];
                badgeSources.forEach((src: DealSource) => {
                  const nameClean = src.site_name
                    ? src.site_name.replace(/ - .*$/, "").trim()
                    : "";
                  if (
                    !uniqueBadgeSources.some(
                      (x: DealSource) =>
                        (x.site_name
                          ? x.site_name.replace(/ - .*$/, "").trim()
                          : "") === nameClean
                    )
                  ) {
                    uniqueBadgeSources.push(src);
                  }
                });

                return (
                  <article
                    key={deal.id}
                    className="glass-panel deal-card"
                    style={
                      deal.isClosed
                        ? { opacity: 0.6, filter: "grayscale(0.5)" }
                        : undefined
                    }
                  >
                    {/* 1. 본문 클릭 영역 (비인터랙티브 자식만 포함 -> 상세 모달 오픈) */}
                    <div
                      role="button"
                      tabIndex={0}
                      className="deal-card-body-trigger"
                      aria-label={`${displayTitle} 상세 정보 보기`}
                      onClick={() => setSelectedDeal(deal)}
                      onKeyDown={(e) => {
                        if (e.key === "Enter" || e.key === " ") {
                          e.preventDefault();
                          setSelectedDeal(deal);
                        }
                      }}
                    >
                      <div className="deal-image-wrapper">
                        {deal.image_url ? (
                          <img
                            src={getProxyImageUrl(deal.image_url, { width: 400 })}
                            alt={displayTitle}
                            className="deal-image"
                            loading="lazy"
                            referrerPolicy="no-referrer"
                            onError={(e) => {
                              const fallback = getFallbackProxyImageUrl(
                                deal.image_url
                              );
                              if (fallback && e.currentTarget.src !== fallback) {
                                e.currentTarget.src = fallback;
                              }
                            }}
                          />
                        ) : (
                          <div
                            style={{
                              width: "100%",
                              height: "100%",
                              display: "flex",
                              alignItems: "center",
                              justifyContent: "center",
                              color: "var(--text-sub)",
                              background: "var(--badge-bg)",
                              fontSize: "0.8rem",
                              fontWeight: 600,
                            }}
                          >
                            이미지 없음
                          </div>
                        )}

                        {isMultiDeal && (
                          <div
                            style={{
                              position: "absolute",
                              bottom: "8px",
                              left: "8px",
                              background: "rgba(25, 31, 40, 0.78)",
                              backdropFilter: "blur(6px)",
                              color: "#FFFFFF",
                              border: "1px solid rgba(255, 255, 255, 0.14)",
                              padding: "3px 8px",
                              borderRadius: "6px",
                              fontSize: "0.72rem",
                              fontWeight: 700,
                              display: "inline-flex",
                              alignItems: "center",
                              zIndex: 2,
                            }}
                          >
                            <span>
                              {displayCount >= 2
                                ? `${displayCount}종 옵션`
                                : "옵션 상품"}
                            </span>
                          </div>
                        )}
                      </div>

                      <h3 className="deal-title">
                        <HighlightedTitle
                          title={displayTitle}
                          query={appliedSearch}
                        />
                      </h3>
                    </div>

                    {/* 2. 출처 링크 + 쇼핑몰 + 시간 메타 바 (본문 트리거의 형제 요소) */}
                    <div className="deal-info">
                      <div className="deal-meta">
                        {uniqueBadgeSources.map(
                          (source: DealSource, idx: number) => {
                            const displaySiteName = source.site_name
                              ? source.site_name.replace(/ - .*$/, "").trim()
                              : "";
                            const targetPostUrl = source.post_url || deal.post_link;
                            const hasPostUrl = Boolean(
                              targetPostUrl && targetPostUrl !== "#"
                            );
                            const resolvedPostUrl = hasPostUrl
                              ? resolveOutboundUrl(targetPostUrl)
                              : "#";
                            return hasPostUrl ? (
                              <a
                                key={idx}
                                href={resolvedPostUrl}
                                target="_blank"
                                rel="noopener noreferrer"
                                className="deal-source deal-source-link"
                                title={`${displaySiteName} 원본 글 보기`}
                                onClick={(e) => {
                                  openExternalUrlInToss(resolvedPostUrl, e);
                                }}
                                style={{
                                  border:
                                    "1px solid rgba(var(--accent-rgb), 0.18)",
                                }}
                              >
                                {displaySiteName}
                              </a>
                            ) : (
                              <span key={idx} className="deal-source">
                                {displaySiteName}
                              </span>
                            );
                          }
                        )}
                        {shopBadge && (
                          <span
                            style={{
                              fontSize: "0.72rem",
                              fontWeight: 700,
                              color: "var(--text-main)",
                              background: "var(--badge-bg)",
                              border: "1px solid var(--card-border)",
                              padding: "2px 7px",
                              borderRadius: "5px",
                              display: "inline-flex",
                              alignItems: "center",
                              lineHeight: 1.3,
                            }}
                          >
                            {shopBadge}
                          </span>
                        )}
                        <span>·</span>
                        <span>
                          {getLiveTimeAgo(deal.originalDate, deal.scraped_at)}
                        </span>
                      </div>

                      {/* 3. 가격 + 배지(중복 N종 옵션 배지 제거) + 찜/알림/공유 액션 */}
                      <div className="price-section">
                        <div
                          style={{
                            display: "flex",
                            alignItems: "baseline",
                            gap: "6px",
                            flexWrap: "wrap",
                            minWidth: 0,
                          }}
                        >
                          <span className="deal-price">
                            {deal.isFree
                              ? "0원 (무료)"
                              : formatPrice(deal.price, deal.currency, deal.title)}
                          </span>
                          {deal.isClosed ? (
                            <span
                              className="deal-tag"
                              style={{
                                background: "var(--badge-bg)",
                                color: "var(--text-muted)",
                              }}
                            >
                              종료
                            </span>
                          ) : (
                            (deal.honeyScore || 0) >= 100 && (
                              <span
                                className="deal-tag"
                                style={{
                                  background: "var(--deal-orange-bg)",
                                  color: "var(--deal-orange)",
                                }}
                              >
                                인기
                              </span>
                            )
                          )}
                          {displayShipping && !deal.isClosed && (
                            <span
                              className="deal-tag"
                              style={{
                                background: "rgba(var(--accent-rgb), 0.1)",
                                color: "var(--accent)",
                              }}
                            >
                              {displayShipping}
                            </span>
                          )}
                          {!deal.isClosed &&
                            deal.tags &&
                            deal.tags.map((tag, tIdx) => {
                              const cleanTag = decodeHtmlEntities(String(tag))
                                .replace(
                                  /^[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}\uFE0F]+\s*/gu,
                                  ""
                                )
                                .trim();
                              if (!cleanTag) return null;
                              if (
                                shopBadge &&
                                normalizeMallAlias(cleanTag) === shopBadge
                              ) {
                                return null;
                              }
                              if (
                                isNonMallLabel(cleanTag) &&
                                cleanTag !== "역대가" &&
                                cleanTag !== "할인코드"
                              ) {
                                return null;
                              }
                              return (
                                <span
                                  key={tIdx}
                                  className="deal-tag"
                                  style={{
                                    background: "var(--badge-bg)",
                                    color: "var(--badge-text)",
                                    border: "1px solid var(--card-border)",
                                  }}
                                >
                                  {cleanTag}
                                </span>
                              );
                            })}
                        </div>

                        <div className="deal-card-actions">
                          <button
                            type="button"
                            onClick={(e) => handleShareDeal(e, deal)}
                            className="deal-card-icon-btn bounce-click"
                            aria-label="공유하기"
                            title="공유하기"
                          >
                            <Share2 size={18} color="var(--text-sub)" />
                          </button>
                          <button
                            type="button"
                            onClick={(e) => toggleAlert(e, deal.id)}
                            className="deal-card-icon-btn bounce-click"
                            aria-label="가격 알림 설정"
                            title="가격 알림"
                          >
                            <Bell
                              size={18}
                              color={
                                isAlertSet ? "var(--accent)" : "var(--text-sub)"
                              }
                              fill={isAlertSet ? "var(--accent)" : "none"}
                            />
                          </button>
                          <button
                            type="button"
                            onClick={(e) => toggleWishlist(e, deal.id)}
                            className="deal-card-icon-btn bounce-click"
                            aria-label="찜하기"
                            title="찜하기"
                          >
                            <Heart
                              size={18}
                              color={isWishlisted ? "#EF4444" : "var(--text-sub)"}
                              fill={isWishlisted ? "#EF4444" : "none"}
                            />
                          </button>
                        </div>
                      </div>

                      {/* 4. 하단 독립 2버튼: 상세 보기 <button> + 독립 형제 <a> 바로구매 (44px+ 터치 타깃) */}
                      <div className="deal-card-footer-actions">
                        <button
                          type="button"
                          onClick={() => setSelectedDeal(deal)}
                          className="deal-card-detail-btn bounce-click"
                        >
                          <span>상세 · 의견 보기</span>
                        </button>
                        <a
                          href={outboundTarget}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="btn-primary bounce-click deal-card-buy-link"
                          aria-label={`${displayTitle} ${
                            deal.isSearchFallback
                              ? "쇼핑몰 검색 결과 보기"
                              : "바로구매"
                          } (새 창)`}
                          onClick={(e) => {
                            if (!outboundTarget || outboundTarget === "#") {
                              e.preventDefault();
                              showToast("이동할 수 있는 상품 주소를 찾지 못했어요");
                              return;
                            }
                            if (!hasValidEcommerceLink) {
                              showToast(
                                "구매처 바로가기가 없어 원본 게시글로 이동해요"
                              );
                            }
                            trackOutboundClick(
                              outboundTarget,
                              deal.id,
                              deal.price,
                              deal.currency
                            );
                            openExternalUrlInToss(outboundTarget, e);
                          }}
                        >
                          <span>
                            {deal.isSearchFallback
                              ? "검색 결과 보기"
                              : "바로구매"}
                          </span>
                          <ExternalLink size={14} aria-hidden="true" />
                        </a>
                      </div>
                    </div>
                  </article>
                );
              })
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
                    fontSize: "0.84rem",
                    fontWeight: 600,
                    background: "var(--card-bg)",
                    padding: "8px 18px",
                    borderRadius: "99px",
                    border: "1px solid var(--card-border)",
                  }}
                >
                  스크롤하면 다음 딜을 불러와요
                </div>
              )}
            </div>
          )}
        </section>

        {toastMessage && <div className="toast">{toastMessage}</div>}

        <footer className="footer">
          <div
            style={{
              display: "flex",
              flexDirection: "column",
              gap: "8px",
              alignItems: "center",
            }}
          >
            <span>© 2026 InsightDeal. All rights reserved.</span>
            <span style={{ fontSize: "0.8rem", color: "var(--text-sub)" }}>
              실시간 커뮤니티 특가 및 키워드 알림 서비스, 인사이트딜
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
                style={{ color: "#EF4444", fontWeight: 700 }}
              >
                <Heart size={14} fill="#EF4444" />
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
            </div>
          </div>
        </footer>
      </div>

      <DealDetailModal
        deal={selectedDeal}
        onClose={() => setSelectedDeal(null)}
        showToast={showToast}
        onTargetAlertSaved={(dealId) => {
          applyAlertAdd(dealId);
        }}
      />

      <NotificationDrawer
        isOpen={isNotificationOpen}
        onClose={() => setIsNotificationOpen(false)}
        deals={deals}
        onSelectDeal={(deal) => setSelectedDeal(deal)}
        onServerAlertCountChange={(cnt) => setServerAlertCount(cnt)}
      />

      <WishlistDrawer
        isOpen={isWishlistOpen}
        onClose={() => setIsWishlistOpen(false)}
        wishlistDeals={wishlistDeals}
        onRemoveDeal={toggleWishlist}
        onSelectDeal={(deal) => {
          setIsWishlistOpen(false);
          setSelectedDeal(deal);
        }}
        isLoggedIn={isLoggedIn}
        onLoginSimulate={handleLoginSimulate}
      />

      <LoginPromptModal
        isOpen={showLoginModal}
        onClose={() => setShowLoginModal(false)}
        onActivateGuest={handleLoginSimulate}
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
