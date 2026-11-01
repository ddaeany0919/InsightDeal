"use client";

import { useEffect, useState, useRef } from "react";
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
  ChevronRight
} from "lucide-react";
import Link from "next/link";

// 분리된 모듈 import
import type { Deal, PopularKeywordRanking } from "./lib/types";
import { formatPrice, categorizeDeal, getProxyImageUrl } from "./lib/utils";

// 리팩토링된 컴포넌트 import
import PrivacyModal from "./components/PrivacyModal";
import EulaModal from "./components/EulaModal";
import DealDetailModal from "./components/DealDetailModal";
import LoginPromptModal from "./components/LoginPromptModal";
import WishlistDrawer from "./components/WishlistDrawer";
import NotificationDrawer from "./components/NotificationDrawer";
import TrendingKeywordsTicker from "./components/TrendingKeywordsTicker";

export default function Home() {
  const [deals, setDeals] = useState<Deal[]>([]);
  const [loading, setLoading] = useState(true);
  const [isMounted, setIsMounted] = useState(false);
  const [activeTab, setActiveTab] = useState("전체");
  const [activePlatform, setActivePlatform] = useState("전체");
  const [deferredPrompt, setDeferredPrompt] = useState<any>(null);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const [currentTime, setCurrentTime] = useState(Date.now());
  const [wishlist, setWishlist] = useState<number[]>([]);
  const [wishlistItemsMap, setWishlistItemsMap] = useState<Record<number, Deal>>({});
  const [alerts, setAlerts] = useState<number[]>([]);
  const [serverAlertCount, setServerAlertCount] = useState<number>(0);
  const [selectedDeal, setSelectedDeal] = useState<Deal | null>(null);
  const [isNotificationOpen, setIsNotificationOpen] = useState(false);
  const [isWishlistOpen, setIsWishlistOpen] = useState(false);
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const [popularKeywords, setPopularKeywords] = useState<string[]>([
    "제로콜라", "로봇청소기", "아이폰", "모니터", "닭가슴살", "물티슈", "에어팟", "갤럭시"
  ]);
  const [popularRankings, setPopularRankings] = useState<PopularKeywordRanking[]>([]);
  const [popularUpdatedAt, setPopularUpdatedAt] = useState<string>("");
  const [onlyHotDeals, setOnlyHotDeals] = useState(false);
  const [hideClosedDeals, setHideClosedDeals] = useState(false);
  const [sortBy, setSortBy] = useState<"latest" | "popular" | "price_asc">("latest");
  const [hideKeywordBanner, setHideKeywordBanner] = useState(false);

  // 무한 스크롤 상태
  const [hasMore, setHasMore] = useState(true);
  const [isFetchingMore, setIsFetchingMore] = useState(false);
  const observerTarget = useRef<HTMLDivElement>(null);

  // 약관 및 안내 모달 상태
  const [showPrivacyModal, setShowPrivacyModal] = useState(false);
  const [showEulaModal, setShowEulaModal] = useState(false);
  const [showLoginModal, setShowLoginModal] = useState(false);

  const TABS = ["전체", "PC/하드웨어", "가전/TV", "식품/건강", "의류/뷰티", "육아/유아용품", "모바일/상품권", "해외직구", "기타"];
  const PLATFORMS = ["전체", "펨코", "뽐뿌", "루리웹", "퀘이사존", "클리앙", "알리뽐뿌", "빠삭국내", "빠삭해외", "빠삭육아"];
  const toastTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const showToast = (message: string) => {
    if (toastTimerRef.current) {
      clearTimeout(toastTimerRef.current);
    }
    setToastMessage(message);
    toastTimerRef.current = setTimeout(() => {
      setToastMessage(null);
      toastTimerRef.current = null;
    }, 2500);
  };

  const handleInstallApp = async () => {
    if (deferredPrompt) {
      deferredPrompt.prompt();
      const { outcome } = await deferredPrompt.userChoice;
      if (outcome === "accepted") {
        setDeferredPrompt(null);
        showToast("앱 설치가 시작되었습니다.");
      }
      return;
    }
    showToast("브라우저 메뉴에서 '홈 화면에 추가'를 누르면 앱처럼 사용할 수 있습니다.");
  };

  // 웹 푸시/키워드 알림과 동일한 기기 식별자 공유
  const getOrCreateBrowserUserId = () => {
    if (typeof window === "undefined") return "web_guest";
    let uuid = localStorage.getItem("insightdeal_web_device_uuid") || localStorage.getItem("insightdeal_token");
    if (!uuid) {
      const randomPart =
        typeof crypto !== "undefined" && crypto.randomUUID
          ? crypto.randomUUID()
          : `${Date.now()}_${Math.random().toString(36).substring(2, 10)}`;
      uuid = `web_${randomPart}`;
    }
    localStorage.setItem("insightdeal_web_device_uuid", uuid);
    localStorage.setItem("insightdeal_token", uuid);
    setIsLoggedIn(true);
    return uuid;
  };

  // 검색어 원클릭 즉시 알림 등록
  const handleQuickSubscribeKeyword = async (rawKw: string) => {
    const cleanKw = rawKw.trim();
    if (!cleanKw) return;
    try {
      const deviceUuid = getOrCreateBrowserUserId();
      await fetch("/api/push/keywords", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ device_uuid: deviceUuid, keyword: cleanKw }),
      }).catch(() => null);

      if (typeof window !== "undefined") {
        const saved = localStorage.getItem("insightdeal_web_keywords");
        const parsed: { id: string; keyword: string }[] = saved ? JSON.parse(saved) : [];
        if (!parsed.some((k) => k.keyword === cleanKw)) {
          const updated = [...parsed, { id: Date.now().toString(), keyword: cleanKw }];
          localStorage.setItem("insightdeal_web_keywords", JSON.stringify(updated));
        }
      }
      showToast(`'${cleanKw}' 키워드 알림을 켰어요`);
    } catch (e) {
      showToast("키워드 알림 등록 중 오류가 발생했습니다.");
    }
  };

  const handleShareDeal = async (e: React.MouseEvent, deal: Deal) => {
    e.preventDefault();
    e.stopPropagation();
    const shareUrl =
      typeof window !== "undefined"
        ? `${window.location.origin}/?keyword=${encodeURIComponent(deal.title.slice(0, 20))}`
        : deal.post_link;
    const shareText = `${deal.title} (${formatPrice(deal.price, deal.currency)})`;

    if (typeof navigator !== "undefined" && navigator.share) {
      try {
        await navigator.share({ title: deal.title, text: shareText, url: shareUrl });
        return;
      } catch (err) {}
    }
    if (typeof navigator !== "undefined" && navigator.clipboard) {
      await navigator.clipboard.writeText(`${shareText}\n${shareUrl}`);
      showToast("상품 링크가 복사되었습니다.");
    }
  };

  const toggleWishlist = (e: React.MouseEvent, id: number) => {
    e.preventDefault();
    e.stopPropagation();

    getOrCreateBrowserUserId();
    const targetDeal = deals.find((d) => d.id === id) || wishlistItemsMap[id];

    setWishlist((prev) => {
      const isAdded = prev.includes(id);
      const updated = isAdded ? prev.filter((item) => item !== id) : [...prev, id];
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
          localStorage.setItem("insightdeal_wishlist_items", JSON.stringify(nextMap));
        }
        return nextMap;
      });
      if (isAdded) showToast("찜 목록에서 삭제했습니다.");
      else showToast("찜 목록에 추가했습니다.");
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
      const updated = isAlertOn ? prev.filter((item) => item !== id) : [...prev, id];
      if (typeof window !== "undefined") {
        localStorage.setItem("insightdeal_alerts", JSON.stringify(updated));
      }
      if (isAlertOn) {
        showToast("가격 변동 알림을 해제했습니다.");
      } else {
        const rawPrice = targetDeal ? parseInt(String(targetDeal.price || "").replace(/[^0-9]/g, ""), 10) || 0 : 0;
        const autoTarget = rawPrice >= 1000 ? Math.max(100, Math.round((rawPrice * 0.95) / 100) * 100) : rawPrice;
        if (targetDeal && autoTarget > 0) {
          const cleanKw = (targetDeal.title || "")
            .replace(/\[[^\]]*\]/g, "")
            .replace(/\([^)]*\)/g, "")
            .trim()
            .slice(0, 50);
          fetch("/api/wishlist/item-alert", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              user_id: uid,
              keyword: cleanKw || targetDeal.title.slice(0, 40),
              target_price: autoTarget,
            }),
          }).catch(() => null);
          try {
            const mapStr = localStorage.getItem("insightdeal_target_prices");
            const targetMap = mapStr ? JSON.parse(mapStr) : {};
            targetMap[id] = autoTarget;
            localStorage.setItem("insightdeal_target_prices", JSON.stringify(targetMap));
          } catch {}
          showToast(`목표가(${autoTarget.toLocaleString()}원 이하) 알림을 등록했어요.`);
        } else {
          showToast("가격 변동 알림을 등록했습니다.");
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
      showToast("기기 연동이 완료되었습니다.");
    }
  };

  // 핫딜 데이터 로딩
  const fetchLiveDeals = async (
    showLoadingState = true,
    offset = 0,
    targetTab = activeTab,
    targetPlatform = activePlatform,
    overrideKeyword?: string
  ) => {
    try {
      if (showLoadingState) {
        setLoading(true);
      }

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

      const apiCategory = CATEGORY_MAP[targetTab] || "";
      const platformParam =
        targetPlatform && targetPlatform !== "전체"
          ? `&platform=${encodeURIComponent(targetPlatform)}`
          : "";
      const limit = 20;

      const activeKw = overrideKeyword !== undefined ? overrideKeyword : searchQuery;
      const queryParam = activeKw.trim() ? `&keyword=${encodeURIComponent(activeKw.trim())}` : "";
      const url = `/api/community/hot-deals?limit=${limit}&offset=${offset}${
        apiCategory ? `&category=${encodeURIComponent(apiCategory)}` : ""
      }${platformParam}${queryParam}`;
      const res = await fetch(url);

      if (!res.ok) throw new Error("API Offline");
      const data = await res.json();
      const dealsArray = Array.isArray(data) ? data : data.deals || data.items || [];

      const formatted: Deal[] = dealsArray.map((d: any) => {
        const titleStr = d.title || "";
        const cleanEcommerce =
          d.ecommerce_url && d.ecommerce_url.trim() !== "" ? d.ecommerce_url.trim() : null;
        const cleanPost = d.post_url && d.post_url.trim() !== "" ? d.post_url.trim() : null;
        const cleanPostLink = d.post_link && d.post_link.trim() !== "" ? d.post_link.trim() : null;
        const cleanLink = d.link && d.link.trim() !== "" ? d.link.trim() : null;

        const finalEcommerce = cleanEcommerce || cleanPostLink || cleanLink || "#";
        const finalPost = cleanPost || cleanPostLink || cleanLink || "#";

        return {
          id: d.id,
          community_name:
            d.site_name ||
            (d.site_names && d.site_names[0]) ||
            d.community_name ||
            d.communityName ||
            getCommunityName(d.source_community_id),
          title: titleStr,
          price: d.price || "가격 문의",
          image_url: d.image_url || d.imageUrl || null,
          scraped_at: d.scraped_at || d.timeAgo || "방금 전",
          post_link: finalPost,
          ecommerce_url: finalEcommerce,
          shipping_fee: d.shipping_fee || d.shippingFee || null,
          category: d.category || categorizeDeal(titleStr),
          isClosed: Boolean(d.is_closed ?? d.isClosed),
          originalDate: d.created_at || d.originalDate,
          currency: d.currency || "KRW",
          honeyScore: d.honey_score || d.honeyScore || 0,
          aiSummary: d.ai_summary || d.aiSummary || "",
          contentHtml: d.content_html || d.contentHtml || "",
          sources: d.sources || [],
          tags: Array.isArray(d.tags) ? d.tags : [],
        };
      });

      if (formatted.length > 0) {
        if (offset === 0) {
          setDeals([...formatted]);
          if (typeof window !== "undefined" && targetTab === "전체" && targetPlatform === "전체" && !activeKw.trim()) {
            localStorage.setItem("insightdeal_cached_deals", JSON.stringify(formatted));
          }
        } else {
          setDeals((prev) => {
            const existingIds = new Set(prev.map((item) => item.id));
            const newDeals = formatted.filter((item: Deal) => !existingIds.has(item.id));
            return [...prev, ...newDeals];
          });
        }

        setHasMore(formatted.length >= limit);
      } else {
        if (offset === 0) {
          setDeals(activeKw.trim() ? [] : getFallbackDeals());
        }
        setHasMore(false);
      }
    } catch (e) {
      if (offset === 0) {
        const fallback = getFallbackDeals();
        setDeals(fallback);
      }
      setHasMore(false);
    } finally {
      if (showLoadingState) {
        setLoading(false);
      }
    }
  };

  const getFallbackDeals = (): Deal[] => [
    {
      id: 901,
      community_name: "뽐뿌",
      title: "국내산 파로 영양 찰가래떡 특가 (26,900원/무료)",
      price: "26900",
      image_url: "https://images.unsplash.com/photo-1534422298391-e4f8c172dddb?w=500&auto=format&fit=crop&q=60",
      scraped_at: "방금 전",
      post_link: "#",
      ecommerce_url: "#",
      shipping_fee: "무료배송",
      category: "식품/건강",
      isClosed: false,
      honeyScore: 120,
      aiSummary: "커뮤니티 추천 핫딜",
    },
    {
      id: 902,
      community_name: "알리뽐뿌",
      title: "디럭스 M800 무선 게이밍 마우스 ($13.93/무료)",
      price: "1393",
      image_url: "https://images.unsplash.com/photo-1615663245857-ac93bb7c39e7?w=500&auto=format&fit=crop&q=60",
      scraped_at: "방금 전",
      post_link: "#",
      ecommerce_url: "#",
      shipping_fee: "무료배송",
      category: "PC/하드웨어",
      isClosed: false,
      honeyScore: 110,
      aiSummary: "해외직구 인기 상품",
    },
  ];

  const getCommunityName = (id: number) => {
    const map: Record<number, string> = {
      1: "뽐뿌", 2: "루리웹", 3: "클리앙", 4: "알리뽐뿌", 5: "퀘이사존",
      6: "펨코", 7: "빠삭국내", 8: "빠삭해외", 9: "빠삭육아"
    };
    return map[id] || "핫딜";
  };

  const handleRefresh = async () => {
    if (isRefreshing) return;
    setIsRefreshing(true);
    try {
      await fetch("/api/community/force-scrape", { method: "POST" });
    } catch (e) {}
    await fetchLiveDeals(false, 0, activeTab, activePlatform);
    setIsRefreshing(false);
    showToast("최신 핫딜 목록을 불러왔습니다.");
  };

  const handleTabChange = (tab: string) => {
    setActiveTab(tab);
    setDeals([]);
    setHasMore(true);
  };

  const handlePlatformChange = (platform: string) => {
    setActivePlatform(platform);
    setDeals([]);
    setHasMore(true);
  };

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setDeals([]);
    setHasMore(true);
    fetchLiveDeals(true, 0, activeTab, activePlatform, searchQuery);
  };

  const handleSelectPopularKeyword = (kw: string) => {
    setSearchQuery(kw);
    setDeals([]);
    setHasMore(true);
    fetchLiveDeals(true, 0, activeTab, activePlatform, kw);
  };

  useEffect(() => {
    setIsMounted(true);

    // PWA 설치 이벤트 리스너 등록
    const handleBeforeInstallPrompt = (e: Event) => {
      e.preventDefault();
      setDeferredPrompt(e);
    };
    if (typeof window !== "undefined") {
      window.addEventListener("beforeinstallprompt", handleBeforeInstallPrompt);

      // URL ?keyword= 공유 파라미터가 있으면 자동 검색 연동
      const urlParams = new URLSearchParams(window.location.search);
      const sharedKw = urlParams.get("keyword");
      if (sharedKw) {
        setSearchQuery(sharedKw);
      }

      if (localStorage.getItem("insightdeal_hide_kw_banner") === "1") {
        setHideKeywordBanner(true);
      }
    }

    // 실시간 인기 검색어 로드
    fetch("/api/community/popular-keywords")
      .then((r) => (r.ok ? r.json() : null))
      .then((data) => {
        if (data && Array.isArray(data.keywords) && data.keywords.length > 0) {
          setPopularKeywords(data.keywords.slice(0, 10));
        }
        if (data && Array.isArray(data.rankings)) {
          setPopularRankings(data.rankings.slice(0, 10));
        }
        if (data && data.updated_at) {
          setPopularUpdatedAt(String(data.updated_at));
        }
      })
      .catch(() => {});

    // 로컬 캐시 복원
    let cachedDeals: Deal[] = [];
    if (typeof window !== "undefined") {
      const saved = localStorage.getItem("insightdeal_cached_deals");
      if (saved) {
        try {
          cachedDeals = JSON.parse(saved);
          if (cachedDeals && cachedDeals.length > 0) {
            setDeals(cachedDeals);
            setLoading(false);
          }
        } catch (e) {}
      }

      const token = getOrCreateBrowserUserId();
      if (token) {
        setIsLoggedIn(true);
        fetch(`/api/users/notifications?user_id=${encodeURIComponent(token)}`)
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
        } catch (e) {}
      }
      const savedWishlistItems = localStorage.getItem("insightdeal_wishlist_items");
      if (savedWishlistItems) {
        try {
          setWishlistItemsMap(JSON.parse(savedWishlistItems));
        } catch (e) {}
      }
      const savedAlerts = localStorage.getItem("insightdeal_alerts");
      if (savedAlerts) {
        try {
          setAlerts(JSON.parse(savedAlerts));
        } catch (e) {}
      }
    }

    const initFetch = async () => {
      const sharedKw =
        typeof window !== "undefined"
          ? new URLSearchParams(window.location.search).get("keyword") || ""
          : "";
      const hasCache = cachedDeals.length > 0 && !sharedKw;
      setIsRefreshing(true);
      await fetchLiveDeals(!hasCache, 0, "전체", "전체", sharedKw);
      setIsRefreshing(false);
    };

    initFetch();

    const timer = setInterval(() => setCurrentTime(Date.now()), 60000);

    return () => {
      clearInterval(timer);
      if (typeof window !== "undefined") {
        window.removeEventListener("beforeinstallprompt", handleBeforeInstallPrompt);
      }
    };
  }, []);

  // 카테고리 또는 커뮤니티 변경 시 새로 로드
  useEffect(() => {
    if (isMounted) {
      setDeals([]);
      setHasMore(true);
      fetchLiveDeals(true, 0, activeTab, activePlatform);
    }
  }, [activeTab, activePlatform]);

  // 무한 스크롤 감지
  useEffect(() => {
    if (!isMounted || !hasMore || loading || isRefreshing) return;

    const observer = new IntersectionObserver(
      async (entries) => {
        if (entries[0].isIntersecting && !isFetchingMore) {
          setIsFetchingMore(true);
          const currentOffset = deals.length;
          await fetchLiveDeals(false, currentOffset, activeTab, activePlatform);
          setIsFetchingMore(false);
        }
      },
      { threshold: 0.8 }
    );

    if (observerTarget.current) {
      observer.observe(observerTarget.current);
    }

    return () => observer.disconnect();
  }, [isMounted, hasMore, deals.length, activeTab, activePlatform, loading, isRefreshing, isFetchingMore]);

  // 실시간 시간 포맷팅
  const getLiveTimeAgo = (dateStr?: string, fallback?: string) => {
    if (!isMounted) return fallback || "최근";
    if (!dateStr) return fallback || "최근";

    try {
      let isoStr = dateStr.trim();
      if (isoStr.includes(" ")) {
        isoStr = isoStr.replace(" ", "T");
      }

      let dealTime: number;
      if (isoStr.includes("+") || isoStr.endsWith("Z") || isoStr.includes("GMT")) {
        dealTime = new Date(isoStr).getTime();
      } else {
        isoStr += "Z";
        dealTime = new Date(isoStr).getTime();
      }

      if (isNaN(dealTime)) return fallback || "최근";

      let diffMs = currentTime - dealTime;
      const nineHoursMs = 9 * 60 * 60 * 1000;
      if (diffMs < 0 && Math.abs(diffMs) >= 8 * 60 * 60 * 1000 && Math.abs(diffMs) <= 10 * 60 * 60 * 1000) {
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
    } catch (e) {
      return fallback || "최근";
    }
  };

  // 클라이언트 빠른 필터링 및 정렬 (최신순 / 인기순 / 낮은 가격순)
  const filteredDeals = deals
    .filter((d) => {
      if (hideClosedDeals && d.isClosed) return false;
      if (onlyHotDeals && (d.honeyScore || 0) < 100) return false;
      return true;
    })
    .slice()
    .sort((a, b) => {
      if (sortBy === "popular") {
        return (b.honeyScore || 0) - (a.honeyScore || 0);
      }
      if (sortBy === "price_asc") {
        const pa = parseInt(String(a.price || "").replace(/[^0-9]/g, ""), 10) || 0;
        const pb = parseInt(String(b.price || "").replace(/[^0-9]/g, ""), 10) || 0;
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
      <div className="bg-ornament bg-ornament-1"></div>
      <div className="bg-ornament bg-ornament-2"></div>

      {/* 상단 네비게이션 헤더 */}
      <header className="navbar glass-panel">
        <div
          className="nav-logo"
          style={{ cursor: "pointer", display: "inline-flex", alignItems: "center", gap: "8px" }}
          onClick={() => {
            setSearchQuery("");
            setActiveTab("전체");
            setActivePlatform("전체");
            setOnlyHotDeals(false);
            setHideClosedDeals(false);
            fetchLiveDeals(true, 0, "전체", "전체", "");
          }}
        >
          <svg width="30" height="30" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <rect x="2" y="2" width="20" height="20" rx="6" fill="url(#insightdeal_logo_grad)" />
            <path
              d="M17.65 12.82L12.18 7.35C11.96 7.13 11.66 7 11.33 7H7.2C6.54 7 6 7.54 6 8.2V12.33C6 12.66 6.13 12.96 6.35 13.18L11.82 18.65C12.29 19.12 13.05 19.12 13.52 18.65L17.65 14.52C18.12 14.05 18.12 13.29 17.65 12.82ZM8.7 10.6C8.04 10.6 7.5 10.06 7.5 9.4C7.5 8.74 8.04 8.2 8.7 8.2C9.36 8.2 9.9 8.74 9.9 9.4C9.9 10.06 9.36 10.6 8.7 10.6Z"
              fill="#FFFFFF"
            />
            <defs>
              <linearGradient id="insightdeal_logo_grad" x1="2" y1="2" x2="22" y2="22" gradientUnits="userSpaceOnUse">
                <stop stopColor="#FF7E26" />
                <stop offset="1" stopColor="#FF5804" />
              </linearGradient>
            </defs>
          </svg>
          <span>InsightDeal</span>
          <span
            style={{
              background: "var(--deal-orange)",
              color: "#FFFFFF",
              WebkitTextFillColor: "#FFFFFF",
              fontSize: "0.68rem",
              fontWeight: 800,
              padding: "2px 6px",
              borderRadius: "5px",
              letterSpacing: "0.03em",
              lineHeight: 1.2,
            }}
          >
            LIVE
          </span>
        </div>
        <div className="nav-actions">
          <div className="global-counters">
            <div
              className="counter-item bounce-click"
              onClick={() => setIsWishlistOpen(true)}
              style={{ cursor: "pointer" }}
            >
              <Heart size={16} className="counter-icon icon-heart" color={wishlist.length > 0 ? "#EF4444" : "var(--text-sub)"} fill={wishlist.length > 0 ? "#EF4444" : "none"} />
              <span className="counter-label">찜</span>
              <span className={`counter-badge ${wishlist.length > 0 ? "badge-active animate-pulse-badge" : ""}`}>
                {wishlist.length}
              </span>
            </div>
            <div
              className="counter-item bounce-click"
              onClick={() => setIsNotificationOpen(true)}
              style={{ cursor: "pointer" }}
            >
              <Bell size={16} className="counter-icon icon-bell" color={alerts.length + serverAlertCount > 0 ? "#F59E0B" : "var(--text-sub)"} fill={alerts.length + serverAlertCount > 0 ? "#F59E0B" : "none"} />
              <span className="counter-label">알림 내역</span>
              <span className={`counter-badge ${alerts.length + serverAlertCount > 0 ? "badge-active animate-pulse-badge" : ""}`}>
                {alerts.length + serverAlertCount}
              </span>
            </div>
            <Link href="/settings/notifications" className="counter-item bounce-click" style={{ textDecoration: "none" }}>
              <Settings size={16} className="counter-icon" color="var(--text-sub)" />
              <span className="counter-label">
                키워드 알림
              </span>
            </Link>
          </div>
          <button className="btn-primary" onClick={handleInstallApp}>
            <Download size={18} />
            <span>앱 설치</span>
          </button>
        </div>
      </header>

      <div className="web-container">
        {/* 접속 즉시 한눈에 들어오는 메인 핫딜 섹션 */}
        <section style={{ padding: "24px 0 80px 0" }}>
          {/* 키워드 알림 유도 배너 (컴팩트 1줄 슬림 바 + 닫기 버튼) */}
          {!hideKeywordBanner && (
            <div
              style={{
                display: "flex",
                alignItems: "center",
                justifyContent: "space-between",
                padding: "9px 14px",
                marginBottom: "16px",
                borderRadius: "12px",
                background: "var(--card-bg)",
                border: "1px solid var(--card-border)",
                boxShadow: "var(--glass-shadow)",
                gap: "12px",
              }}
            >
              <Link
                href="/settings/notifications"
                style={{
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "space-between",
                  flex: 1,
                  textDecoration: "none",
                  gap: "10px",
                }}
              >
                <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
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
                    <Bell size={14} strokeWidth={2.4} color="var(--deal-orange)" />
                  </div>
                  <div style={{ display: "flex", alignItems: "center", gap: "8px", flexWrap: "wrap" }}>
                    <span style={{ fontSize: "0.88rem", color: "var(--text-main)", fontWeight: 700 }}>
                      놓치기 아까운 특가, 키워드 알림 받기
                    </span>
                    <span style={{ fontSize: "0.8rem", color: "var(--text-sub)", fontWeight: 500 }}>
                      찾는 상품이 올라오면 바로 알려드려요
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
                title="배너 닫기"
                onClick={() => {
                  setHideKeywordBanner(true);
                  if (typeof window !== "undefined") {
                    localStorage.setItem("insightdeal_hide_kw_banner", "1");
                  }
                }}
                style={{
                  width: "24px",
                  height: "24px",
                  borderRadius: "50%",
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  color: "var(--text-muted)",
                  cursor: "pointer",
                  flexShrink: 0,
                }}
              >
                <X size={14} />
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
              marginBottom: "20px",
            }}
          >
            <div>
              <h1 className="section-title" style={{ textAlign: "left", fontSize: "1.65rem", marginBottom: "4px" }}>
                실시간 핫딜 모아보기
              </h1>
              <p style={{ color: "var(--text-sub)", fontSize: "0.92rem" }}>
                뽐뿌 · 펨코 · 루리웹 · 퀘이사존 · 클리앙 · 빠삭의 특가 정보를 한곳에서 비교하세요.
              </p>
            </div>
            <div style={{ display: "flex", gap: "8px", alignItems: "center", flexWrap: "wrap" }}>
              <button
                type="button"
                onClick={() => setOnlyHotDeals((prev) => !prev)}
                className="bounce-click"
                style={{
                  padding: "8px 13px",
                  borderRadius: "10px",
                  fontSize: "0.82rem",
                  fontWeight: 700,
                  cursor: "pointer",
                  display: "inline-flex",
                  alignItems: "center",
                  gap: "5px",
                  background: onlyHotDeals ? "var(--deal-orange-bg)" : "var(--card-bg)",
                  color: onlyHotDeals ? "var(--deal-orange)" : "var(--text-sub)",
                  border: onlyHotDeals ? "1px solid var(--deal-orange)" : "1px solid var(--card-border)",
                }}
              >
                <Flame size={14} />
                <span>인기 핫딜만</span>
              </button>
              <button
                type="button"
                onClick={() => setHideClosedDeals((prev) => !prev)}
                className="bounce-click"
                style={{
                  padding: "8px 13px",
                  borderRadius: "10px",
                  fontSize: "0.82rem",
                  fontWeight: 700,
                  cursor: "pointer",
                  background: hideClosedDeals ? "rgba(var(--accent-rgb), 0.12)" : "var(--card-bg)",
                  color: hideClosedDeals ? "var(--accent)" : "var(--text-sub)",
                  border: hideClosedDeals ? "1px solid var(--accent)" : "1px solid var(--card-border)",
                }}
              >
                품절 숨기기
              </button>
              <div
                style={{
                  display: "inline-flex",
                  background: "var(--card-bg)",
                  border: "1px solid var(--card-border)",
                  borderRadius: "10px",
                  padding: "3px",
                }}
              >
                {[
                  { key: "latest", label: "최신순" },
                  { key: "popular", label: "인기순" },
                  { key: "price_asc", label: "낮은가격순" },
                ].map((opt) => {
                  const active = sortBy === opt.key;
                  return (
                    <button
                      key={opt.key}
                      type="button"
                      onClick={() => setSortBy(opt.key as "latest" | "popular" | "price_asc")}
                      style={{
                        border: "none",
                        background: active ? "var(--accent)" : "transparent",
                        color: active ? "#FFFFFF" : "var(--text-sub)",
                        fontWeight: active ? 800 : 600,
                        fontSize: "0.76rem",
                        padding: "5px 10px",
                        borderRadius: "7px",
                        cursor: "pointer",
                        transition: "all 0.15s ease",
                      }}
                    >
                      {opt.label}
                    </button>
                  );
                })}
              </div>
              <button
                type="button"
                className="bounce-click"
                onClick={handleRefresh}
                disabled={isRefreshing}
                style={{
                  padding: "8px 14px",
                  borderRadius: "10px",
                  background: "var(--card-bg)",
                  color: "var(--text-main)",
                  border: "1px solid var(--card-border)",
                  display: "inline-flex",
                  alignItems: "center",
                  gap: "6px",
                  fontWeight: 700,
                  cursor: isRefreshing ? "not-allowed" : "pointer",
                  opacity: isRefreshing ? 0.7 : 1,
                }}
              >
                <RefreshCw size={14} color="var(--accent)" className={isRefreshing ? "spin-animation" : ""} />
                <span style={{ fontSize: "0.82rem", color: "var(--text-main)" }}>새로고침</span>
                <style>{`
                  @keyframes spin { 100% { transform: rotate(360deg); } }
                  .spin-animation { animation: spin 1s linear infinite; }
                `}</style>
              </button>
            </div>
          </div>

          {/* 통합 검색창 */}
          <form onSubmit={handleSearchSubmit} className="search-container" style={{ marginBottom: "12px" }}>
            <div className="search-bar-wrapper">
              <Search size={18} className="search-icon" />
              <input
                type="text"
                placeholder="상품명·조건 검색 (예: 맥북 에어 -파우치 / 제로콜라 :15000)"
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="search-input"
              />
              {searchQuery && (
                <button
                  type="button"
                  onClick={() => {
                    setSearchQuery("");
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

          {/* 검색어 입력 시 원클릭 키워드 알림 등록 배너 */}
          {searchQuery.trim() && (
            <div
              style={{
                display: "flex",
                justifyContent: "space-between",
                alignItems: "center",
                flexWrap: "wrap",
                gap: "10px",
                padding: "10px 16px",
                marginBottom: "14px",
                borderRadius: "12px",
                background: "rgba(var(--accent-rgb), 0.08)",
                border: "1px solid rgba(var(--accent-rgb), 0.25)",
              }}
            >
              <span style={{ fontSize: "0.86rem", color: "var(--text-main)", fontWeight: 600, display: "inline-flex", alignItems: "center", gap: "6px" }}>
                <Bell size={15} color="var(--accent)" />
                <span><strong>'{searchQuery.trim()}'</strong> 새 핫딜이 올라오면 바로 알려드릴까요?</span>
              </span>
              <button
                type="button"
                onClick={() => handleQuickSubscribeKeyword(searchQuery)}
                className="bounce-click"
                style={{
                  padding: "6px 14px",
                  borderRadius: "8px",
                  background: "var(--accent)",
                  color: "#FFFFFF",
                  fontWeight: 700,
                  fontSize: "0.8rem",
                  border: "none",
                  cursor: "pointer",
                }}
              >
                이 키워드 즉시 알림 받기
              </button>
            </div>
          )}

          {/* 실시간 급상승 검색어 티커 & 랭킹 보드 */}
          <TrendingKeywordsTicker
            keywords={popularKeywords}
            rankings={popularRankings}
            updatedAt={popularUpdatedAt}
            selectedKeyword={searchQuery}
            onSelectKeyword={handleSelectPopularKeyword}
          />

          {/* 실시간 인기 검색어 칩 */}
          <div
            style={{
              display: "flex",
              alignItems: "center",
              gap: "6px",
              flexWrap: "wrap",
              marginBottom: "16px",
            }}
          >
            <span style={{ fontSize: "0.78rem", fontWeight: 800, color: "var(--accent)", marginRight: "4px" }}>
              인기 검색어
            </span>
            {popularKeywords.map((kw, idx) => {
              const isSelected = searchQuery === kw;
              return (
                <button
                  key={kw}
                  type="button"
                  onClick={() => handleSelectPopularKeyword(kw)}
                  className="bounce-click"
                  style={{
                    padding: "5px 12px",
                    borderRadius: "999px",
                    fontSize: "0.78rem",
                    fontWeight: isSelected ? 800 : 600,
                    cursor: "pointer",
                    background: isSelected ? "rgba(var(--accent-rgb), 0.12)" : "var(--card-bg)",
                    color: isSelected ? "var(--accent)" : "var(--text-main)",
                    border: isSelected ? "1px solid var(--accent)" : "1px solid var(--card-border)",
                    display: "inline-flex",
                    alignItems: "center",
                    gap: "5px",
                  }}
                >
                  <span style={{ fontSize: "0.72rem", fontWeight: 800, color: idx < 3 ? "var(--deal-orange)" : "var(--text-muted)" }}>
                    {idx + 1}
                  </span>
                  <span>{kw}</span>
                </button>
              );
            })}
          </div>

          {/* 출처 커뮤니티 필터 칩 */}
          <div style={{ display: "flex", gap: "8px", flexWrap: "wrap", marginBottom: "12px" }}>
            {PLATFORMS.map((platform) => {
              const isSelected = activePlatform === platform;
              return (
                <button
                  key={platform}
                  type="button"
                  onClick={() => handlePlatformChange(platform)}
                  className="bounce-click"
                  style={{
                    padding: "6px 14px",
                    borderRadius: "999px",
                    fontSize: "0.82rem",
                    fontWeight: isSelected ? 800 : 600,
                    cursor: "pointer",
                    background: isSelected ? "var(--accent)" : "var(--card-bg)",
                    color: isSelected ? "#FFFFFF" : "var(--text-sub)",
                    border: isSelected ? "1px solid var(--accent)" : "1px solid var(--card-border)",
                    transition: "all 0.2s ease",
                  }}
                >
                  {platform === "전체" ? "전체 커뮤니티" : platform}
                </button>
              );
            })}
          </div>

          {/* 카테고리 탭 목록 */}
          <div className="tabs-container">
            <div className="tabs">
              {TABS.map((tab) => (
                <button
                  key={tab}
                  className={`tab bounce-click ${activeTab === tab ? "active" : ""}`}
                  onClick={() => handleTabChange(tab)}
                >
                  {tab}
                </button>
              ))}
            </div>
          </div>

          {/* 핫딜 목록 카드 그리드 */}
          <div className="deal-list">
            {filteredDeals.length === 0 ? (
              <div
                className="glass-panel"
                style={{ padding: "40px", borderRadius: "24px", textAlign: "center", color: "var(--text-sub)" }}
              >
                <p>
                  {loading && deals.length === 0
                    ? "핫딜 목록을 불러오고 있습니다..."
                    : "조건에 맞는 핫딜이 없습니다."}
                </p>
              </div>
            ) : (
              filteredDeals.map((deal) => {
                const isWishlisted = wishlist.includes(deal.id);
                const isAlertSet = alerts.includes(deal.id);

                const hasValidEcommerceLink =
                  deal.ecommerce_url &&
                  deal.ecommerce_url !== "#" &&
                  String(deal.ecommerce_url).trim() !== "";

                return (
                  <a
                    key={deal.id}
                    href={hasValidEcommerceLink ? deal.ecommerce_url : "#"}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="glass-panel deal-card bounce-click"
                    style={deal.isClosed ? { opacity: 0.6, filter: "grayscale(0.5)" } : {}}
                    onClick={(e) => {
                      if (!hasValidEcommerceLink) {
                        e.preventDefault();
                        if (deal.post_link && deal.post_link !== "#") {
                          showToast("쇼핑몰 직접 링크가 없어 원본 게시글로 이동합니다.");
                          setTimeout(() => {
                            window.open(deal.post_link, "_blank", "noopener,noreferrer");
                          }, 400);
                        } else {
                          showToast("이동할 수 있는 링크가 없습니다.");
                        }
                      }
                    }}
                  >
                    <div className="deal-image-wrapper">
                      {deal.image_url ? (
                        <img
                          src={getProxyImageUrl(deal.image_url)}
                          alt={deal.title}
                          className="deal-image"
                          loading="lazy"
                          referrerPolicy="no-referrer"
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
                    </div>
                    <div className="deal-info">
                      <div>
                        <div className="deal-meta">
                          {(() => {
                            const badgeSources =
                              deal.sources && deal.sources.length > 0
                                ? deal.sources
                                : deal.community_name
                                    .split(", ")
                                    .map((cName) => ({ site_name: cName, post_url: deal.post_link }));

                            const uniqueBadgeSources: any[] = [];
                            badgeSources.forEach((src: any) => {
                              const nameClean = src.site_name ? src.site_name.replace(/ - .*$/, "").trim() : "";
                              if (
                                !uniqueBadgeSources.some(
                                  (x: any) => (x.site_name ? x.site_name.replace(/ - .*$/, "").trim() : "") === nameClean
                                )
                              ) {
                                uniqueBadgeSources.push(src);
                              }
                            });

                            return uniqueBadgeSources.map((source: any, idx: number) => {
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
                                    const targetUrl = source.post_url || deal.post_link;
                                    if (targetUrl && targetUrl !== "#") {
                                      window.open(targetUrl, "_blank", "noopener,noreferrer");
                                    } else {
                                      showToast("원본 게시글 링크를 찾을 수 없습니다.");
                                    }
                                  }}
                                  title={`${displaySiteName} 원본 글 보기`}
                                  style={{
                                    cursor: "pointer",
                                    transition: "all 0.2s ease",
                                    background: "rgba(var(--accent-rgb), 0.1)",
                                    color: "var(--accent)",
                                    border: "1px solid rgba(var(--accent-rgb), 0.22)",
                                    padding: "2px 8px",
                                    borderRadius: "6px",
                                    fontWeight: 800,
                                    display: "inline-flex",
                                    alignItems: "center",
                                    gap: "3px",
                                  }}
                                >
                                  {displaySiteName}
                                </span>
                              );
                            });
                          })()}
                          <span>•</span>
                          <span>{getLiveTimeAgo(deal.originalDate, deal.scraped_at)}</span>
                        </div>
                        <h3 className="deal-title">{deal.title}</h3>
                      </div>
                      <div
                        className="price-section"
                        style={{
                          marginTop: "auto",
                          display: "flex",
                          justifyContent: "space-between",
                          alignItems: "baseline",
                        }}
                      >
                        <div style={{ display: "flex", alignItems: "baseline", gap: "6px", flexWrap: "wrap" }}>
                          <span className="deal-price">{formatPrice(deal.price, deal.currency)}</span>
                          {deal.isClosed ? (
                            <span className="deal-tag" style={{ background: "var(--badge-bg)", color: "var(--text-muted)" }}>
                              종료
                            </span>
                          ) : (deal.honeyScore || 0) >= 100 ? (
                            <span
                              className="deal-tag"
                              style={{ background: "var(--deal-orange-bg)", color: "var(--deal-orange)", fontWeight: 800 }}
                            >
                              인기
                            </span>
                          ) : null}
                          {deal.shipping_fee && !deal.isClosed && (
                            <span
                              className="deal-tag"
                              style={{ background: "rgba(var(--accent-rgb), 0.1)", color: "var(--accent)" }}
                            >
                              {deal.shipping_fee}
                            </span>
                          )}
                          {!deal.isClosed &&
                            deal.tags &&
                            deal.tags.map((tag, tIdx) => {
                              const cleanTag = String(tag).replace(/^[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}\uFE0F]+\s*/gu, "").trim();
                              if (!cleanTag) return null;
                              return (
                                <span
                                  key={tIdx}
                                  className="deal-tag"
                                  style={{
                                    background: "var(--badge-bg)",
                                    color: "var(--badge-text)",
                                    border: "1px solid var(--card-border)",
                                    fontWeight: 700,
                                  }}
                                >
                                  {cleanTag}
                                </span>
                              );
                            })}
                        </div>
                        {/* 액션 버튼 (공유, 알림, 찜) */}
                        <div style={{ display: "flex", gap: "12px", alignItems: "center" }}>
                          <button
                            onClick={(e) => handleShareDeal(e, deal)}
                            className="bounce-click"
                            title="공유하기"
                            style={{
                              background: "none",
                              border: "none",
                              padding: 4,
                              cursor: "pointer",
                              display: "inline-flex",
                              alignItems: "center",
                              justifyContent: "center",
                            }}
                          >
                            <Share2 size={18} color="var(--text-sub)" />
                          </button>
                          <button
                            onClick={(e) => toggleAlert(e, deal.id)}
                            className="bounce-click"
                            title="가격 알림"
                            style={{
                              background: "none",
                              border: "none",
                              padding: 4,
                              cursor: "pointer",
                              display: "inline-flex",
                              alignItems: "center",
                              justifyContent: "center",
                            }}
                          >
                            <Bell
                              size={19}
                              color={isAlertSet ? "#F59E0B" : "var(--text-sub)"}
                              fill={isAlertSet ? "#F59E0B" : "none"}
                            />
                          </button>
                          <button
                            onClick={(e) => toggleWishlist(e, deal.id)}
                            className="bounce-click"
                            title="찜하기"
                            style={{
                              background: "none",
                              border: "none",
                              padding: 4,
                              cursor: "pointer",
                              display: "inline-flex",
                              alignItems: "center",
                              justifyContent: "center",
                            }}
                          >
                            <Heart
                              size={19}
                              color={isWishlisted ? "#EF4444" : "var(--text-sub)"}
                              fill={isWishlisted ? "#EF4444" : "none"}
                            />
                          </button>
                        </div>
                      </div>

                      {/* 상세보기 & 가격추이 버튼 */}
                      <button
                        onClick={(e) => {
                          e.preventDefault();
                          e.stopPropagation();
                          setSelectedDeal(deal);
                        }}
                        className="bounce-click"
                        style={{
                          width: "100%",
                          justifyContent: "center",
                          padding: "10px",
                          borderRadius: "12px",
                          fontSize: "0.84rem",
                          fontWeight: 700,
                          marginTop: "14px",
                          background: "rgba(var(--accent-rgb), 0.08)",
                          border: "1px solid rgba(var(--accent-rgb), 0.2)",
                          color: "var(--accent)",
                          boxShadow: "none",
                          display: "flex",
                          alignItems: "center",
                          gap: "6px",
                          cursor: "pointer",
                        }}
                      >
                        <span>상세 조건 · 가격 추이 · 의견 보기</span>
                      </button>
                    </div>
                  </a>
                );
              })
            )}
          </div>

          {/* 무한 스크롤 로더 */}
          {hasMore && (
            <div
              ref={observerTarget}
              style={{ display: "flex", justifyContent: "center", alignItems: "center", padding: "40px 0", width: "100%" }}
            >
              {isFetchingMore ? (
                <div className="ai-loader" style={{ width: "36px", height: "36px" }}></div>
              ) : (
                <div
                  style={{
                    color: "var(--text-sub)",
                    fontSize: "0.86rem",
                    fontWeight: 600,
                    background: "var(--card-bg)",
                    padding: "8px 20px",
                    borderRadius: "99px",
                    border: "1px solid var(--card-border)",
                  }}
                >
                  스크롤하면 더 많은 핫딜을 불러옵니다
                </div>
              )}
            </div>
          )}
        </section>

        {/* 토스트 메시지 */}
        {toastMessage && (
          <div
            className="toast fade-in"
            style={{
              position: "fixed",
              bottom: "24px",
              left: "50%",
              transform: "translateX(-50%)",
              backgroundColor: "rgba(25, 31, 40, 0.94)",
              color: "white",
              padding: "12px 24px",
              borderRadius: "9999px",
              boxShadow: "0 10px 25px rgba(0, 0, 0, 0.25)",
              backdropFilter: "blur(8px)",
              fontWeight: "600",
              fontSize: "0.92rem",
              zIndex: 1000,
              border: "1px solid rgba(255, 255, 255, 0.1)",
            }}
          >
            {toastMessage}
          </div>
        )}

        {/* 푸터 */}
        <footer className="footer">
          <p style={{ display: "flex", flexDirection: "column", gap: "8px", alignItems: "center" }}>
            <span>© 2026 InsightDeal. All rights reserved.</span>
            <span style={{ fontSize: "0.8rem", color: "var(--text-sub)" }}>
              실시간 커뮤니티 특가 및 키워드 알림 서비스, 인사이트딜
            </span>
            <span style={{ fontSize: "0.75rem", color: "var(--text-muted)", textAlign: "center", maxWidth: "640px", lineHeight: 1.5 }}>
              본 서비스에서 제공하는 일부 쇼핑몰 링크는 쿠팡 파트너스 및 제휴 마케팅 활동의 일환으로 이에 따른 일정액의 수수료를 제공받을 수 있으나, 구매 가격에는 영향을 미치지 않습니다.
            </span>
            <span style={{ display: "flex", gap: "16px", fontSize: "0.82rem", marginTop: "4px" }}>
              <button
                onClick={() => setIsWishlistOpen(true)}
                style={{ color: "#EF4444", fontWeight: 700, display: "flex", alignItems: "center", gap: "4px" }}
              >
                <Heart size={14} fill="#EF4444" />
                <span>내 찜 목록</span>
              </button>
              <span style={{ color: "var(--card-border)" }}>|</span>
              <button
                onClick={() => setShowPrivacyModal(true)}
                style={{ color: "var(--accent)", fontWeight: 700, textDecoration: "underline" }}
              >
                개인정보 처리방침
              </button>
              <span style={{ color: "var(--card-border)" }}>|</span>
              <button onClick={() => setShowEulaModal(true)} style={{ color: "var(--text-sub)", fontWeight: 600 }}>
                서비스 이용약관 (EULA)
              </button>
            </span>
          </p>
        </footer>
      </div>

      {/* 상세 모달 */}
      <DealDetailModal deal={selectedDeal} onClose={() => setSelectedDeal(null)} showToast={showToast} />

      {/* 알림 내역 서랍 */}
      <NotificationDrawer
        isOpen={isNotificationOpen}
        onClose={() => setIsNotificationOpen(false)}
        deals={deals}
        onSelectDeal={(deal) => setSelectedDeal(deal)}
        onServerAlertCountChange={(cnt) => setServerAlertCount(cnt)}
      />

      {/* 내 찜 목록 서랍 */}
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

      {/* 로그인 안내 모달 */}
      <LoginPromptModal isOpen={showLoginModal} onClose={() => setShowLoginModal(false)} />

      {/* 개인정보 처리방침 모달 */}
      <PrivacyModal isOpen={showPrivacyModal} onClose={() => setShowPrivacyModal(false)} />

      {/* 서비스 이용약관 모달 */}
      <EulaModal isOpen={showEulaModal} onClose={() => setShowEulaModal(false)} />
    </>
  );
}
