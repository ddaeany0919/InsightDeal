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
} from "lucide-react";
import Link from "next/link";

import type {
  Deal,
  DealSource,
  PopularKeywordRanking,
  RawDealApiResponse,
} from "./lib/types";
import {
  formatPrice,
  extractPriceFromTitle,
  categorizeDeal,
  getProxyImageUrl,
  getFallbackProxyImageUrl,
  apiUrl,
  resolveOutboundUrl,
  getOrCreateWebDeviceUuid,
  ensureWebPushSubscribed,
  getOptionSources,
  decodeHtmlEntities,
  resolveDealShopName,
  stripLeadingShopBracket,
  normalizeMallAlias,
  isNonMallLabel,
} from "./lib/utils";

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

const TABS = [
  "전체",
  "PC/하드웨어",
  "가전/TV",
  "식품/건강",
  "의류/뷰티",
  "육아/유아용품",
  "모바일/상품권",
  "해외직구",
  "기타",
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

const CATEGORY_MAP: Record<string, string> = {
  "PC/하드웨어": "PC용품",
  "가전/TV": "가전제품",
  "식품/건강": "음식",
  "의류/뷰티": "패션",
  "육아/유아용품": "육아/유아용품",
  "모바일/상품권": "모바일/기프티콘",
  "해외직구": "여행.해외핫딜",
  "기타": "기타",
};

function getCommunityName(id?: number): string {
  if (!id) return "핫딜";
  const map: Record<number, string> = {
    1: "뽐뿌",
    2: "루리웹",
    3: "클리앙",
    4: "알리뽐뿌",
    5: "퀘이사존",
    6: "펨코",
    7: "빠삭국내",
    8: "빠삭해외",
    9: "빠삭육아",
  };
  return map[id] || "핫딜";
}

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
      const { deviceUuid, pushGranted } = await ensureWebPushSubscribed();
      await fetch(apiUrl("/api/push/keywords"), {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ device_uuid: deviceUuid, keyword: cleanKw }),
      }).catch(() => null);

      if (typeof window !== "undefined") {
        const saved = localStorage.getItem("insightdeal_web_keywords");
        const parsed: { id: string; keyword: string }[] = saved
          ? JSON.parse(saved)
          : [];
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
      showToast(
        pushGranted
          ? `'${cleanKw}' 새 딜이 올라오면 바로 알려드릴게요`
          : `'${cleanKw}' 알림을 켰어요. 브라우저 알림을 허용하면 푸시로 알려드려요`
      );
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
        ? `${window.location.origin}/?keyword=${encodeURIComponent(
            cleanShareTitle.slice(0, 20)
          )}`
        : deal.post_link;
    const shareText = `${cleanShareTitle} (${formatPrice(
      deal.price,
      deal.currency,
      cleanShareTitle
    )})`;

    if (typeof navigator !== "undefined" && navigator.share) {
      try {
        await navigator.share({
          title: cleanShareTitle,
          text: shareText,
          url: shareUrl,
        });
        return;
      } catch {
        // 사용자 공유 취소 시 클립보드 복사로 넘어가지 않고 종료하거나 폴백
      }
    }
    if (typeof navigator !== "undefined" && navigator.clipboard) {
      await navigator.clipboard.writeText(`${shareText}\n${shareUrl}`);
      showToast("상품 링크를 복사했어요");
    }
  };

  const toggleWishlist = (e: React.MouseEvent, id: number) => {
    e.preventDefault();
    e.stopPropagation();

    getOrCreateBrowserUserId();
    const targetDeal = deals.find((d) => d.id === id) || wishlistItemsMap[id];

    setWishlist((prev) => {
      const isAdded = prev.includes(id);
      const updated = isAdded
        ? prev.filter((item) => item !== id)
        : [...prev, id];
      if (typeof window !== "undefined") {
        localStorage.setItem("insightdeal_wishlist", JSON.stringify(updated));
      }
      setWishlistItemsMap((prevMap) => {
        const nextMap = { ...prevMap };
        if (isAdded) {
          delete nextMap[id];
        } else if (targetDeal) {
          nextMap[id] = targetDeal;
        }
        if (typeof window !== "undefined") {
          localStorage.setItem(
            "insightdeal_wishlist_items",
            JSON.stringify(nextMap)
          );
        }
        return nextMap;
      });
      if (isAdded) showToast("찜 목록에서 뺐어요");
      else showToast("찜 목록에 담았어요");
      return updated;
    });
  };

  const toggleAlert = (e: React.MouseEvent, id: number) => {
    e.preventDefault();
    e.stopPropagation();

    const uid = getOrCreateBrowserUserId();
    const targetDeal = deals.find((d) => d.id === id) || wishlistItemsMap[id];

    setAlerts((prev) => {
      const isAlertOn = prev.includes(id);
      const updated = isAlertOn
        ? prev.filter((item) => item !== id)
        : [...prev, id];
      if (typeof window !== "undefined") {
        localStorage.setItem("insightdeal_alerts", JSON.stringify(updated));
      }
      if (isAlertOn) {
        showToast("가격 알림을 껐어요");
      } else {
        ensureWebPushSubscribed().catch(() => null);
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
        if (targetDeal && autoTarget > 0) {
          const cleanKw = decodeHtmlEntities(targetDeal.title || "")
            .replace(/\[[^\]]*\]/g, "")
            .replace(/\([^)]*\)/g, "")
            .trim()
            .slice(0, 50);
          fetch(apiUrl("/api/wishlist/item-alert"), {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              user_id: uid,
              keyword: cleanKw || decodeHtmlEntities(targetDeal.title).slice(0, 40),
              target_price: autoTarget,
            }),
          }).catch(() => null);
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
          showToast(
            `${formatPrice(autoTarget, targetDeal.currency, targetDeal.title)} 이하로 내려가면 알려드릴게요`
          );
        } else {
          showToast("가격 변동 알림을 켰어요");
        }
      }
      return updated;
    });
  };

  const handleLoginSimulate = () => {
    if (typeof window !== "undefined") {
      getOrCreateBrowserUserId();
      setIsLoggedIn(true);
      setShowLoginModal(false);
      showToast("기기 연동을 완료했어요");
    }
  };

  const fetchLiveDeals = useCallback(
    async (
      showLoadingState = true,
      offset = 0,
      targetTab = "전체",
      targetPlatform = "전체",
      overrideKeyword?: string
    ) => {
      if (liveDealsAbortRef.current) {
        liveDealsAbortRef.current.abort();
      }
      const controller = new AbortController();
      liveDealsAbortRef.current = controller;
      const { signal } = controller;

      try {
        if (showLoadingState) {
          setLoading(true);
        }

        const apiCategory = CATEGORY_MAP[targetTab] || "";
        const platformParam =
          targetPlatform && targetPlatform !== "전체"
            ? `&platform=${encodeURIComponent(targetPlatform)}`
            : "";
        const limit = targetPlatform === "뽐뿌" ? 50 : 20;

        const activeKw =
          overrideKeyword !== undefined
            ? overrideKeyword
            : appliedSearchRef.current;
        const queryParam = activeKw.trim()
          ? `&keyword=${encodeURIComponent(activeKw.trim())}`
          : "";
        const url = apiUrl(
          `/api/community/hot-deals?limit=${limit}&offset=${offset}${
            apiCategory ? `&category=${encodeURIComponent(apiCategory)}` : ""
          }${platformParam}${queryParam}`
        );

        let res: Response | null = null;
        for (let attempt = 0; attempt < 3; attempt++) {
          if (signal.aborted) return;
          try {
            const r = await fetch(url, { signal });
            if (r.ok) {
              res = r;
              break;
            }
          } catch (fetchErr) {
            if (signal.aborted || isAbortError(fetchErr)) {
              return;
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
            if (signal.aborted) return;
          }
        }

        if (signal.aborted || liveDealsAbortRef.current !== controller) return;
        if (!res || !res.ok) throw new Error("API Offline");

        const data = await res.json();
        if (signal.aborted || liveDealsAbortRef.current !== controller) return;

        const dealsArray: RawDealApiResponse[] = Array.isArray(data)
          ? data
          : data.deals || data.items || [];

        const formatted: Deal[] = dealsArray.map((d: RawDealApiResponse) => {
          const titleStr = decodeHtmlEntities(d.title || "");
          const cleanEcommerce =
            d.ecommerce_url && d.ecommerce_url.trim() !== ""
              ? d.ecommerce_url.trim()
              : null;
          const cleanPost =
            d.post_url && d.post_url.trim() !== "" ? d.post_url.trim() : null;
          const cleanPostLink =
            d.post_link && d.post_link.trim() !== ""
              ? d.post_link.trim()
              : null;
          const cleanLink =
            d.link && d.link.trim() !== "" ? d.link.trim() : null;

          const matchedSource =
            targetPlatform &&
            targetPlatform !== "전체" &&
            Array.isArray(d.sources)
              ? d.sources.find(
                  (s: DealSource) =>
                    String(s.site_name || "").includes(targetPlatform) &&
                    s.post_url
                )
              : null;
          const finalEcommerce =
            cleanEcommerce || cleanPostLink || cleanLink || "#";
          const finalPost =
            (matchedSource && matchedSource.post_url) ||
            cleanPost ||
            cleanPostLink ||
            cleanLink ||
            "#";
          const rawPriceNum = Number(
            String(d.price ?? "").replace(/[^0-9.-]/g, "")
          );
          const titleFallback =
            d.price === null ||
            d.price === undefined ||
            String(d.price).trim() === "" ||
            !Number.isFinite(rawPriceNum) ||
            rawPriceNum <= 0
              ? extractPriceFromTitle(titleStr)
              : null;
          const rawShipping = d.shipping_fee || d.shippingFee || null;
          const shippingStr = rawShipping
            ? decodeHtmlEntities(rawShipping)
            : null;
          const optionsDataArr = Array.isArray(d.options_data)
            ? d.options_data
            : Array.isArray(d.optionsData)
              ? d.optionsData
              : undefined;
          const resolvedShop = resolveDealShopName({
            shop_name: d.shop_name ?? d.shopName,
            title: titleStr,
            ecommerce_url: finalEcommerce,
          });

          return {
            id: d.id,
            community_name:
              d.site_name ||
              (d.site_names && d.site_names[0]) ||
              d.community_name ||
              d.communityName ||
              getCommunityName(d.source_community_id),
            shop_name: resolvedShop || undefined,
            title: titleStr,
            price: titleFallback
              ? titleFallback.price
              : d.price !== null &&
                  d.price !== undefined &&
                  String(d.price).trim() !== ""
                ? String(d.price)
                : "0",
            image_url: d.image_url || d.imageUrl || null,
            scraped_at: d.scraped_at || d.timeAgo || "방금 전",
            post_link: finalPost,
            ecommerce_url: finalEcommerce,
            shipping_fee: shippingStr,
            category: d.category || categorizeDeal(titleStr),
            isClosed: Boolean(d.is_closed ?? d.isClosed),
            originalDate: d.created_at || d.originalDate,
            currency: titleFallback
              ? titleFallback.currency
              : d.currency || "KRW",
            honeyScore: d.honey_score || d.honeyScore || 0,
            aiSummary: decodeHtmlEntities(d.ai_summary || d.aiSummary || ""),
            contentHtml: d.content_html || d.contentHtml || "",
            sources: d.sources || [],
            tags: Array.isArray(d.tags)
              ? d.tags.map((t) => decodeHtmlEntities(String(t))).filter(Boolean)
              : [],
            has_options: Boolean(d.has_options ?? d.hasOptions),
            options_count:
              d.options_count ??
              (optionsDataArr ? optionsDataArr.length : undefined),
            options_data: optionsDataArr,
          };
        });

        if (formatted.length > 0) {
          if (offset === 0) {
            setDeals([...formatted]);
            if (
              typeof window !== "undefined" &&
              targetTab === "전체" &&
              targetPlatform === "전체" &&
              !activeKw.trim()
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

          setHasMore(formatted.length >= limit);
        } else {
          if (offset === 0) {
            setDeals([]);
          }
          setHasMore(false);
        }
      } catch (err) {
        if (
          signal.aborted ||
          isAbortError(err) ||
          liveDealsAbortRef.current !== controller
        ) {
          return;
        }
        if (offset === 0 && typeof window !== "undefined") {
          try {
            const cached = localStorage.getItem("insightdeal_cached_deals");
            if (cached) {
              const parsed = JSON.parse(cached);
              if (Array.isArray(parsed) && parsed.length > 0) {
                setDeals(parsed);
              }
            }
          } catch {}
        }
        setHasMore(false);
      } finally {
        if (liveDealsAbortRef.current === controller) {
          liveDealsAbortRef.current = null;
          if (showLoadingState) {
            setLoading(false);
          }
        }
      }
    },
    []
  );

  const handleRefresh = async () => {
    if (isRefreshing) return;
    setIsRefreshing(true);
    setNewDealsCount(0);
    try {
      await fetch(apiUrl("/api/community/force-scrape"), { method: "POST" });
    } catch {}
    await fetchLiveDeals(false, 0, activeTab, activePlatform);
    setIsRefreshing(false);
    showToast("최신 딜을 불러왔어요");
  };

  const handleScrollToTopAndRefresh = async () => {
    if (typeof window !== "undefined") {
      window.scrollTo({ top: 0, behavior: "smooth" });
    }
    setNewDealsCount(0);
    setIsRefreshing(true);
    await fetchLiveDeals(false, 0, activeTab, activePlatform);
    setIsRefreshing(false);
    showToast("최신 딜을 반영했어요");
  };

  const handleTabChange = (tab: string) => {
    if (tab === activeTab) return;
    setActiveTab(tab);
    setNewDealsCount(0);
    setIsFetchingMore(false);
    setDeals([]);
    setHasMore(true);
    fetchLiveDeals(true, 0, tab, activePlatform);
  };

  const handlePlatformChange = (platform: string) => {
    if (platform === activePlatform) return;
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

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
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
    setSearchQuery("");
    updateAppliedSearch("");
    setActiveTab("전체");
    setActivePlatform("전체");
    setOnlyHotDeals(false);
    setHideClosedDeals(false);
    setSortBy("latest");
    setIsFetchingMore(false);
    setDeals([]);
    setHasMore(true);
    fetchLiveDeals(true, 0, "전체", "전체", "");
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
    if (typeof window !== "undefined") {
      window.addEventListener("beforeinstallprompt", handleBeforeInstallPrompt);

      const urlParams = new URLSearchParams(window.location.search);
      const sharedKw = urlParams.get("keyword");
      if (sharedKw && sharedKw.trim()) {
        initialSharedKw = sharedKw.trim();
        setSearchQuery(initialSharedKw);
        updateAppliedSearch(initialSharedKw);
      }

      if (localStorage.getItem("insightdeal_hide_kw_banner") === "1") {
        setHideKeywordBanner(true);
      }

      const ua = navigator.userAgent || "";
      if (
        ua.includes("TossApp") ||
        window.location.hostname.includes("toss") ||
        urlParams.get("toss") === "1"
      ) {
        setIsTossMiniApp(true);
      }
    }

    fetch(apiUrl("/api/community/popular-keywords"))
      .then((r) => (r.ok ? r.json() : null))
      .then(async (data) => {
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
        if (rawKeywords.length > 0) {
          setPopularKeywords(rawKeywords.slice(0, 10));
        }
        if (rawRankings.length > 0) {
          setPopularRankings(rawRankings.slice(0, 10));
        }

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
        const verified = await Promise.all(
          candidateList.map(async (item) => {
            const kw = (item.keyword || "").trim();
            if (!kw || kw === "일시") return null;
            try {
              const r = await fetch(
                apiUrl(
                  `/api/community/hot-deals?limit=1&keyword=${encodeURIComponent(kw)}`
                )
              );
              if (!r.ok) return item;
              const j = await r.json();
              const arr = Array.isArray(j) ? j : j.deals || [];
              return arr.length > 0 ? item : null;
            } catch {
              return item;
            }
          })
        );
        const validRankings = verified
          .filter((x): x is PopularKeywordRanking => x !== null)
          .slice(0, 10)
          .map((item, idx) => ({
            ...item,
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
        fetch(
          apiUrl(`/api/users/notifications?user_id=${encodeURIComponent(token)}`)
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
          setWishlist(JSON.parse(savedWishlist));
        } catch {}
      }
      const savedWishlistItems = localStorage.getItem(
        "insightdeal_wishlist_items"
      );
      if (savedWishlistItems) {
        try {
          setWishlistItemsMap(JSON.parse(savedWishlistItems));
        } catch {}
      }
      const savedAlerts = localStorage.getItem("insightdeal_alerts");
      if (savedAlerts) {
        try {
          setAlerts(JSON.parse(savedAlerts));
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
  }, [fetchLiveDeals, getOrCreateBrowserUserId, updateAppliedSearch]);

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
          await fetch(apiUrl("/api/community/force-scrape"), {
            method: "POST",
          }).catch(() => {});
          await fetchLiveDeals(false, 0, activeTab, activePlatform);
          showToast("최신 딜을 불러왔어요");
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
        const apiCategory = CATEGORY_MAP[activeTab] || "";
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
    if (!dateStr) return fallback || "최근";

    try {
      let isoStr = dateStr.trim();
      if (isoStr.includes(" ")) {
        isoStr = isoStr.replace(" ", "T");
      }

      let dealTime: number;
      if (
        isoStr.includes("+") ||
        isoStr.endsWith("Z") ||
        isoStr.includes("GMT")
      ) {
        dealTime = new Date(isoStr).getTime();
      } else {
        isoStr += "Z";
        dealTime = new Date(isoStr).getTime();
      }

      if (isNaN(dealTime)) return fallback || "최근";

      let diffMs = currentTime - dealTime;
      const nineHoursMs = 9 * 60 * 60 * 1000;
      if (
        diffMs < 0 &&
        Math.abs(diffMs) >= 8 * 60 * 60 * 1000 &&
        Math.abs(diffMs) <= 10 * 60 * 60 * 1000
      ) {
        dealTime += nineHoursMs;
        diffMs = currentTime - dealTime;
      }

      if (diffMs < 0) diffMs = 0;

      const diffMins = Math.floor(diffMs / 60000);
      if (diffMins < 1) return "방금 전";
      if (diffMins < 60) return `${diffMins}분 전`;

      const diffHours = Math.floor(diffMins / 60);
      if (diffHours < 24) return `${diffHours}시간 전`;

      const diffDays = Math.floor(diffHours / 24);
      if (diffDays < 7) return `${diffDays}일 전`;

      const d = new Date(dealTime);
      const month = String(d.getMonth() + 1).padStart(2, "0");
      const day = String(d.getDate()).padStart(2, "0");
      return `${month}.${day}`;
    } catch {
      return fallback || "최근";
    }
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
          const raw =
            parseFloat(String(item.price || "").replace(/[^0-9.]/g, "")) || 0;
          if (raw <= 0) return 0;
          const curr = (item.currency || "KRW").toUpperCase();
          const hasDec = String(item.price || "").includes(".");
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
            gap: "8px",
          }}
          onClick={() => {
            setSearchQuery("");
            updateAppliedSearch("");
            setActiveTab("전체");
            setActivePlatform("전체");
            setOnlyHotDeals(false);
            setHideClosedDeals(false);
            setIsFetchingMore(false);
            fetchLiveDeals(true, 0, "전체", "전체", "");
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
                      onClick={() => setSortBy(opt.key)}
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
                <style>{`
                  @keyframes spin { 100% { transform: rotate(360deg); } }
                  .spin-animation { animation: spin 0.9s linear infinite; }
                `}</style>
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
                onChange={(e) => setSearchQuery(e.target.value)}
                className="search-input"
              />
              {(searchQuery || appliedSearch) && (
                <button
                  type="button"
                  aria-label="검색어 지우기"
                  onClick={() => {
                    setSearchQuery("");
                    updateAppliedSearch("");
                    setIsFetchingMore(false);
                    setDeals([]);
                    setHasMore(true);
                    fetchLiveDeals(true, 0, activeTab, activePlatform, "");
                  }}
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
                        minHeight: "36px",
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
                          minHeight: "36px",
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
                          width: "28px",
                          height: "28px",
                          borderRadius: "50%",
                          border: "none",
                          background: "transparent",
                          color: "var(--text-muted)",
                          cursor: "pointer",
                        }}
                      >
                        <X size={12} />
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
                  minHeight: "36px",
                  padding: "0 6px",
                }}
              >
                전체 삭제
              </button>
            </div>
          )}

          {searchQuery.trim() && (
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
              {TABS.map((tab) => (
                <button
                  key={tab}
                  type="button"
                  className={`tab bounce-click ${
                    activeTab === tab ? "active" : ""
                  }`}
                  onClick={() => handleTabChange(tab)}
                >
                  {tab}
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
                    onClick={() => {
                      setSearchQuery("");
                      updateAppliedSearch("");
                      setIsFetchingMore(false);
                      setDeals([]);
                      setHasMore(true);
                      fetchLiveDeals(true, 0, activeTab, activePlatform, "");
                    }}
                    style={{
                      display: "inline-flex",
                      alignItems: "center",
                      minHeight: "36px",
                      gap: "4px",
                      padding: "4px 10px",
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
                      minHeight: "36px",
                      gap: "4px",
                      padding: "4px 10px",
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
                      minHeight: "36px",
                      gap: "4px",
                      padding: "4px 10px",
                      borderRadius: "999px",
                      background: "var(--surface-subtle)",
                      color: "var(--text-main)",
                      border: "1px solid var(--card-border)",
                      fontSize: "0.76rem",
                      fontWeight: 700,
                      cursor: "pointer",
                    }}
                  >
                    <span>카테고리: {activeTab}</span>
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
                  minHeight: "38px",
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

          <div className="deal-list">
            {filteredDeals.length === 0 ? (
              <div
                className="glass-panel"
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
                    : "찾으시는 조건의 딜이 없어요"}
                </p>
                {!loading && hasActiveFilters && (
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

                return (
                  <a
                    key={deal.id}
                    href={
                      hasValidEcommerceLink
                        ? resolveOutboundUrl(deal.ecommerce_url)
                        : deal.post_link && deal.post_link !== "#"
                          ? resolveOutboundUrl(deal.post_link)
                          : "#"
                    }
                    target="_blank"
                    rel="noopener"
                    className="glass-panel deal-card bounce-click"
                    style={
                      deal.isClosed
                        ? { opacity: 0.6, filter: "grayscale(0.5)" }
                        : undefined
                    }
                    onClick={(e) => {
                      if (!hasValidEcommerceLink) {
                        if (!deal.post_link || deal.post_link === "#") {
                          e.preventDefault();
                          showToast("이동할 수 있는 상품 주소를 찾지 못했어요");
                        } else {
                          showToast(
                            "구매처 바로가기가 없어 원본 게시글로 이동해요"
                          );
                        }
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

                    <div className="deal-info">
                      <div>
                        <div className="deal-meta">
                          {(() => {
                            const badgeSources: DealSource[] =
                              deal.sources && deal.sources.length > 0
                                ? deal.sources
                                : deal.community_name
                                    .split(", ")
                                    .map((cName) => ({
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

                            return uniqueBadgeSources.map(
                              (source: DealSource, idx: number) => {
                                const displaySiteName = source.site_name
                                  ? source.site_name.replace(/ - .*$/, "").trim()
                                  : "";
                                return (
                                  <span
                                    key={idx}
                                    className="deal-source"
                                    onClick={(e) => {
                                      e.preventDefault();
                                      e.stopPropagation();
                                      const targetUrl =
                                        source.post_url || deal.post_link;
                                      if (targetUrl && targetUrl !== "#") {
                                        window.open(
                                          resolveOutboundUrl(targetUrl),
                                          "_blank",
                                          "noopener"
                                        );
                                      } else {
                                        showToast(
                                          "원본 게시글 주소를 찾지 못했어요"
                                        );
                                      }
                                    }}
                                    title={`${displaySiteName} 원본 글 보기`}
                                    style={{
                                      cursor: "pointer",
                                      border:
                                        "1px solid rgba(var(--accent-rgb), 0.18)",
                                      display: "inline-flex",
                                      alignItems: "center",
                                    }}
                                  >
                                    {displaySiteName}
                                  </span>
                                );
                              }
                            );
                          })()}
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
                        <h3 className="deal-title">
                          <HighlightedTitle
                            title={displayTitle}
                            query={appliedSearch}
                          />
                        </h3>
                      </div>

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
                            {formatPrice(deal.price, deal.currency, deal.title)}
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
                            <>
                              {isMultiDeal && (
                                <span
                                  className="deal-tag"
                                  style={{
                                    background: "var(--badge-bg)",
                                    color: "var(--text-sub)",
                                    border: "1px solid var(--card-border)",
                                  }}
                                >
                                  {displayCount >= 2
                                    ? `${displayCount}종 옵션`
                                    : "옵션 상품"}
                                </span>
                              )}
                              {(deal.honeyScore || 0) >= 100 && (
                                <span
                                  className="deal-tag"
                                  style={{
                                    background: "var(--deal-orange-bg)",
                                    color: "var(--deal-orange)",
                                  }}
                                >
                                  인기
                                </span>
                              )}
                            </>
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

                      <button
                        type="button"
                        onClick={(e) => {
                          e.preventDefault();
                          e.stopPropagation();
                          setSelectedDeal(deal);
                        }}
                        className="deal-card-detail-btn bounce-click"
                      >
                        <span>상세 조건 · 가격 추이 · 의견 보기</span>
                      </button>
                    </div>
                  </a>
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
        onNotify={showToast}
      />

      <LoginPromptModal
        isOpen={showLoginModal}
        onClose={() => setShowLoginModal(false)}
        onNotify={showToast}
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
