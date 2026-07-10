"use client";

import { useEffect, useState, useRef } from "react";
import { 
  Download, 
  Heart,
  Bell,
  RefreshCw,
  Settings,
  Search,
  X
} from "lucide-react";
import Link from "next/link";

// 분리된 모듈 import
import type { Deal } from "./lib/types";
import { formatPrice, categorizeDeal, getProxyImageUrl } from "./lib/utils";

// 리팩토링된 컴포넌트 import
import Hero from "./components/Hero";
import AIDetector from "./components/AIDetector";
import PrivacyModal from "./components/PrivacyModal";
import EulaModal from "./components/EulaModal";
import DealDetailModal from "./components/DealDetailModal";
import LoginPromptModal from "./components/LoginPromptModal";
import WishlistDrawer from "./components/WishlistDrawer";
import NotificationDrawer from "./components/NotificationDrawer";

export default function Home() {
  const [deals, setDeals] = useState<Deal[]>([]);
  const [loading, setLoading] = useState(true);
  const [isMounted, setIsMounted] = useState(false);
  const [activeTab, setActiveTab] = useState("전체");
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const [currentTime, setCurrentTime] = useState(Date.now());
  const [wishlist, setWishlist] = useState<number[]>([]);
  const [alerts, setAlerts] = useState<number[]>([]);
  const [selectedDeal, setSelectedDeal] = useState<Deal | null>(null);
  const [isNotificationOpen, setIsNotificationOpen] = useState(false);
  const [isWishlistOpen, setIsWishlistOpen] = useState(false);
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  
  // 🔄 무한 스크롤(Infinite Scroll) 지능형 연쇄 스펙 탑재
  const [hasMore, setHasMore] = useState(true);
  const [isFetchingMore, setIsFetchingMore] = useState(false);
  const observerTarget = useRef<HTMLDivElement>(null);
  
  // 📜 법적 안전망 규격 모달 상태
  const [showPrivacyModal, setShowPrivacyModal] = useState(false);
  const [showEulaModal, setShowEulaModal] = useState(false);
  const [showLoginModal, setShowLoginModal] = useState(false);

  const TABS = ["전체", "PC/하드웨어", "가전/TV", "식품/건강", "의류/뷰티", "모바일/상품권", "해외직구", "기타"];

  const showToast = (message: string) => {
    setToastMessage(message);
    setTimeout(() => setToastMessage(null), 2500);
  };

  const toggleWishlist = (e: React.MouseEvent, id: number) => {
    e.preventDefault(); 
    e.stopPropagation(); // 이벤트 버블링 차단 (부모 anchor 이동 방지)
    
    // 🔒 로그인 여부 검증 가드 작동
    const token = typeof window !== "undefined" ? localStorage.getItem("insightdeal_token") : null;
    if (!token) {
      setShowLoginModal(true);
      return;
    }

    setWishlist(prev => {
      const isAdded = prev.includes(id);
      const updated = isAdded ? prev.filter(item => item !== id) : [...prev, id];
      if (typeof window !== "undefined") {
        localStorage.setItem("insightdeal_wishlist", JSON.stringify(updated));
      }
      if (isAdded) showToast("즐겨찾기에서 제거되었습니다. 🤍");
      else showToast("즐겨찾기에 추가되었습니다. ❤️");
      return updated;
    });
  };

  const toggleAlert = (e: React.MouseEvent, id: number) => {
    e.preventDefault(); 
    e.stopPropagation(); // 이벤트 버블링 차단 (부모 anchor 이동 방지)
    
    // 🔒 로그인 여부 검증 가드 작동
    const token = typeof window !== "undefined" ? localStorage.getItem("insightdeal_token") : null;
    if (!token) {
      setShowLoginModal(true);
      return;
    }

    setAlerts(prev => {
      const isAlertOn = prev.includes(id);
      const updated = isAlertOn ? prev.filter(item => item !== id) : [...prev, id];
      if (typeof window !== "undefined") {
        localStorage.setItem("insightdeal_alerts", JSON.stringify(updated));
      }
      if (isAlertOn) showToast("가격 변동 알림이 해제되었습니다. 🔕");
      else showToast("가격 변동 알림이 등록되었습니다. 🔔");
      return updated;
    });
  };

  const handleLoginSimulate = () => {
    if (typeof window !== "undefined") {
      localStorage.setItem("insightdeal_token", "dummy_token_12345");
      setIsLoggedIn(true);
      setShowLoginModal(false);
      showToast("임시 로그인되었습니다! 🔓");
    }
  };

  // 📝 핫딜 데이터 로딩 (백엔드 API 폴백 및 오프셋 페이지네이션 탑재)
  const fetchLiveDeals = async (showLoadingState = true, offset = 0, targetTab = activeTab) => {
    try {
      if (showLoadingState) {
        setLoading(true);
      }
      
      // 🏷️ 프론트엔드 탭명 -> 백엔드 카테고리 매핑 규격화
      const CATEGORY_MAP: Record<string, string> = {
        "PC/하드웨어": "PC용품",
        "가전/TV": "가전제품",
        "식품/건강": "음식",
        "의류/뷰티": "패션",
        "모바일/상품권": "모바일/기프티콘",
        "해외직구": "여행.해외핫딜",
        "기타": "기타"
      };
      
      const apiCategory = CATEGORY_MAP[targetTab] || "";
      const limit = 20;
      
      // 🚀 백엔드 API 페이지네이션 쿼리 격발
      const queryParam = searchQuery.trim() ? `&keyword=${encodeURIComponent(searchQuery.trim())}` : "";
      const url = `/api/community/hot-deals?limit=${limit}&offset=${offset}${apiCategory ? `&category=${encodeURIComponent(apiCategory)}` : ""}${queryParam}`;
      const res = await fetch(url);
      
      if (!res.ok) throw new Error("API Offline");
      const data = await res.json();
      const dealsArray = Array.isArray(data) ? data : (data.deals || data.items || []);
      
      const formatted = dealsArray.map((d: any) => {
        const titleStr = d.title || "";
        // 🛡️ 빈 문자열 ("") 및 공백 링크 원천 방어 가드
        const cleanEcommerce = d.ecommerce_url && d.ecommerce_url.trim() !== "" ? d.ecommerce_url.trim() : null;
        const cleanPost = d.post_url && d.post_url.trim() !== "" ? d.post_url.trim() : null;
        const cleanPostLink = d.post_link && d.post_link.trim() !== "" ? d.post_link.trim() : null;
        const cleanLink = d.link && d.link.trim() !== "" ? d.link.trim() : null;
        
        const finalEcommerce = cleanEcommerce || cleanPostLink || cleanLink || "#";
        const finalPost = cleanPost || cleanPostLink || cleanLink || "#";

        return {
          id: d.id,
          community_name: d.site_name || (d.site_names && d.site_names[0]) || d.community_name || d.communityName || getCommunityName(d.source_community_id),
          title: titleStr,
          price: d.price || "가격 문의",
          image_url: d.image_url || d.imageUrl || null,
          scraped_at: d.scraped_at || d.timeAgo || "방금 전",
          post_link: finalPost,
          ecommerce_url: finalEcommerce,
          real_ecommerce_link: finalEcommerce,
          real_post_link: finalPost,
          shipping_fee: d.shipping_fee || d.shippingFee || null,
          category: d.category || categorizeDeal(titleStr),
          isClosed: d.isClosed || false,
          originalDate: d.created_at || d.originalDate,
          currency: d.currency || "KRW",
          honeyScore: d.honey_score || d.honeyScore || 0,
          aiSummary: d.ai_summary || d.aiSummary || "",
          sources: d.sources || []
        };
      });
      
      if (formatted.length > 0) {
        if (offset === 0) {
          setDeals([...formatted]);
          if (typeof window !== "undefined" && targetTab === "전체") {
            localStorage.setItem("insightdeal_cached_deals", JSON.stringify(formatted));
          }
        } else {
          setDeals(prev => {
            // 중복 상품 중복 방어 디듀플리케이션 가드 작동
            const existingIds = new Set(prev.map(item => item.id));
            const newDeals = formatted.filter((item: Deal) => !existingIds.has(item.id));
            return [...prev, ...newDeals];
          });
        }
        
        // 20개 미만이면 더 가져올 수 없으므로 hasMore를 false로 잠금
        if (formatted.length < limit) {
          setHasMore(false);
        } else {
          setHasMore(true);
        }
      } else {
        if (offset === 0) {
          const fallback = getFallbackDeals();
          setDeals(fallback);
          setHasMore(false);
        } else {
          setHasMore(false);
        }
      }
    } catch (e) {
      console.log("Using Fallback premium deals for high visual dashboard");
      if (offset === 0) {
        const fallback = getFallbackDeals();
        setDeals(fallback);
        if (typeof window !== "undefined") {
          localStorage.setItem("insightdeal_cached_deals", JSON.stringify(fallback));
        }
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
      title: "국내생산 파로 영양 찰가래떡 야간 런칭 특가 국내최저가(26900원/무료)",
      price: "26900",
      image_url: "https://images.unsplash.com/photo-1534422298391-e4f8c172dddb?w=500&auto=format&fit=crop&q=60",
      scraped_at: "방금 전",
      post_link: "#",
      ecommerce_url: "#",
      shipping_fee: "무료배송",
      category: "식품/건강",
      isClosed: false,
      honeyScore: 120,
      aiSummary: "🔥 [커뮤니티 인기] 국내 최저가 달성!"
    },
    {
      id: 902,
      community_name: "알리뽐뿌",
      title: "디럭스 M800 무선 게이밍 마우스($13.93/무료)",
      price: "1393",
      image_url: "https://images.unsplash.com/photo-1615663245857-ac93bb7c39e7?w=500&auto=format&fit=crop&q=60",
      scraped_at: "방금 전",
      post_link: "#",
      ecommerce_url: "#",
      shipping_fee: "무료배송",
      category: "PC/하드웨어",
      isClosed: false,
      honeyScore: 110,
      aiSummary: "🔥 [커뮤니티 인증 핫딜] 해외 최저가 가성비!"
    },
    {
      id: 903,
      community_name: "네이버",
      title: "[네이버] 펩시에크스트라피즈 355ml 24캔+모자or변색컵 (14,550원/무료)",
      price: "14550",
      image_url: "https://images.unsplash.com/photo-1622483767028-3f66f32aef97?w=500&auto=format&fit=crop&q=60",
      scraped_at: "방금 전",
      post_link: "#",
      ecommerce_url: "#",
      shipping_fee: "무료배송",
      category: "식품/건강",
      isClosed: false,
      honeyScore: 60,
      aiSummary: ""
    },
    {
      id: 904,
      community_name: "쿠팡",
      title: "[쿠팡] LG 울트라기어 32인치 4K UHD 게이밍 모니터 역대급 특가!",
      price: "649000",
      image_url: "https://images.unsplash.com/photo-1527443224154-c4a3942d3acf?w=500&auto=format&fit=crop&q=60",
      scraped_at: "5분 전",
      post_link: "#",
      ecommerce_url: "#",
      shipping_fee: "무료배송",
      category: "PC/하드웨어",
      isClosed: false,
      honeyScore: 130,
      aiSummary: "🔥 [커뮤니티 인기] 쿠팡 역대 최저가 달성!"
    },
    {
      id: 905,
      community_name: "클리앙",
      title: "아디다스 삼바 비건 클래식 스니커즈 역대급 관세내 할인!",
      price: "89000",
      image_url: "https://images.unsplash.com/photo-1595950653106-6c9ebd614d3a?w=500&auto=format&fit=crop&q=60",
      scraped_at: "12분 전",
      post_link: "#",
      ecommerce_url: "#",
      shipping_fee: "3,000원",
      category: "의류/뷰티",
      isClosed: false,
      honeyScore: 55,
      aiSummary: ""
    }
  ];

  const getCommunityName = (id: number) => {
    const map: Record<number, string> = {
      1: "뽐뿌", 2: "루리웹", 3: "클리앙", 4: "펨코", 5: "퀘이사존",
      6: "알리뽐뿌", 7: "빠삭국내", 8: "빠삭해외"
    };
    return map[id] || "핫딜";
  };

  const handleRefresh = async () => {
    if (isRefreshing) return;
    setIsRefreshing(true);
    try {
      await fetch('/api/community/force-scrape', { method: 'POST' });
    } catch (e) {
      console.log('Force scrape offline fallback');
    }
    await fetchLiveDeals(false);
    setIsRefreshing(false);
    showToast("최신 핫딜이 실시간 동기화되었습니다! 🔄");
  };

  const handleTabChange = (tab: string) => {
    setSearchQuery(""); // 카테고리 탭 이동 시 기존 검색어 리셋
    setActiveTab(tab);
    setDeals([]);
    setHasMore(true);
  };

  const handleSearchSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setDeals([]);
    setHasMore(true);
    // 상태 비동기성 예외 방어를 위해 약간의 딜레이 후 실시간 핫딜 Fetch 호출
    setTimeout(() => {
      fetchLiveDeals(true, 0, activeTab);
    }, 20);
  };

  useEffect(() => {
    setIsMounted(true);

    // 💾 마운트 즉시 로컬 캐시에서 즉각적인 수혈 시도
    let cachedDeals: Deal[] = [];
    if (typeof window !== "undefined") {
      const saved = localStorage.getItem("insightdeal_cached_deals");
      if (saved) {
        try {
          cachedDeals = JSON.parse(saved);
          
          // 🛡️ 극강의 안전 대책
          const hasCorruptedCache = cachedDeals.some(d => 
            d.title && (d.title.includes("$") || d.title.includes("달러")) && 
            d.price && !String(d.price).includes("$") && parseInt(String(d.price).replace(/[^0-9]/g, ""), 10) < 150000
          );
          if (hasCorruptedCache) {
            cachedDeals = [];
            localStorage.removeItem("insightdeal_cached_deals");
          }

          if (cachedDeals && cachedDeals.length > 0) {
            setDeals(cachedDeals);
            setLoading(false);
          }
        } catch (e) {}
      }

      // 로컬 스토리지 기반 개인화 정보 복원
      const token = localStorage.getItem("insightdeal_token");
      if (token) {
        setIsLoggedIn(true);
      }
      const savedWishlist = localStorage.getItem("insightdeal_wishlist");
      if (savedWishlist) {
        try {
          setWishlist(JSON.parse(savedWishlist));
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
      const hasCache = cachedDeals.length > 0;
      if (hasCache) {
        setIsRefreshing(true);
        await fetchLiveDeals(false, 0, "전체");
        setIsRefreshing(false);
      } else {
        setIsRefreshing(true);
        await fetchLiveDeals(true, 0, "전체");
        setIsRefreshing(false);
      }
    };

    initFetch();
    
    // 실시간 1분 타이머 구독
    const timer = setInterval(() => setCurrentTime(Date.now()), 60000);
    
    return () => {
      clearInterval(timer);
    };
  }, []);

  // 🔄 activeTab 변경 감지 즉시 첫 페이지 로드 연쇄 격발
  useEffect(() => {
    if (isMounted) {
      setDeals([]);
      setHasMore(true);
      fetchLiveDeals(true, 0, activeTab);
    }
  }, [activeTab]);

  // 🔗 [Intersection Observer 무한 스크롤 트리거 가드]
  useEffect(() => {
    if (!isMounted || !hasMore || loading || isRefreshing) return;

    const observer = new IntersectionObserver(
      async (entries) => {
        if (entries[0].isIntersecting && !isFetchingMore) {
          setIsFetchingMore(true);
          const currentOffset = deals.length;
          await fetchLiveDeals(false, currentOffset, activeTab);
          setIsFetchingMore(false);
        }
      },
      { threshold: 0.8 }
    );

    if (observerTarget.current) {
      observer.observe(observerTarget.current);
    }

    return () => observer.disconnect();
  }, [isMounted, hasMore, deals.length, activeTab, loading, isRefreshing, isFetchingMore]);

  // 실시간 시간 포맷팅 헬퍼
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
        if (!isoStr.endsWith("Z") && !isoStr.includes("+")) {
          isoStr += "Z";
        }
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
      const month = String(d.getMonth() + 1).padStart(2, '0');
      const day = String(d.getDate()).padStart(2, '0');
      return `${month}.${day}`;
    } catch (e) {
      return fallback || "최근";
    }
  };

  const filteredDeals = deals;
  const wishlistDeals = deals.filter(deal => wishlist.includes(deal.id));

  return (
    <>
      {/* 🌟 백그라운드 디자인 오너먼트 */}
      <div className="bg-ornament bg-ornament-1"></div>
      <div className="bg-ornament bg-ornament-2"></div>

      {/* 🌟 웅장한 네비게이션 헤더 */}
      <header className="navbar glass-panel">
        <div className="nav-logo">InsightDeal</div>
        <div className="nav-actions">
          {/* ⚡ 실시간 위시리스트 & 가격 알림 전역 카운터 뱃지 */}
          <div className="global-counters">
            <div className="counter-item bounce-click" onClick={() => setIsWishlistOpen(true)} style={{ cursor: 'pointer' }}>
              <Heart size={16} className="counter-icon icon-heart" fill={wishlist.length > 0 ? "#EF4444" : "none"} />
              <span className="counter-label">찜</span>
              <span className={`counter-badge ${wishlist.length > 0 ? "badge-active animate-pulse-badge" : ""}`}>
                {wishlist.length}
              </span>
            </div>
            <div className="counter-item bounce-click" onClick={() => setIsNotificationOpen(true)} style={{ cursor: 'pointer' }}>
              <Bell size={16} className="counter-icon icon-bell" fill={alerts.length > 0 ? "#F59E0B" : "none"} />
              <span className="counter-label">실시간 알림</span>
              <span className={`counter-badge ${alerts.length > 0 ? "badge-active animate-pulse-badge" : ""}`}>
                {alerts.length}
              </span>
            </div>
            <Link href="/settings/notifications" className="counter-item bounce-click" style={{ textDecoration: 'none' }}>
              <Settings size={16} className="counter-icon" color="var(--text-sub)" />
              <span className="counter-label" style={{ color: 'var(--text-sub)' }}>알림 설정</span>
            </Link>
          </div>
          <button className="btn-primary" onClick={() => window.open("#", "_blank")}>
            <Download size={18} />
            <span>앱 설치하기</span>
          </button>
        </div>
      </header>

      <div className="web-container">

        {/* 🌟 웅장한 HERO 섹션 */}
        <Hero />

        {/* 🌟 AI 핫딜 판독기 데모 체험존 */}
        <AIDetector deals={deals} />

        {/* 🌟 실제 핫딜 포털 대시보드 융합 피더 */}
        <section style={{ padding: "20px 0 80px 0" }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', marginBottom: '32px' }}>
            <div>
              <h2 className="section-title" style={{ textAlign: 'left' }}>실시간 핫딜 아카이브</h2>
              <p style={{ color: 'var(--text-sub)', fontSize: '1rem', marginTop: '4px' }}>
                전체 커뮤니티에서 실시간 수집된 실제 핫딜 아카이브 목록입니다.
              </p>
            </div>
            <button 
              className="btn-primary" 
              onClick={handleRefresh} 
              disabled={isRefreshing}
              style={{ 
                padding: '8px 16px', 
                borderRadius: '12px', 
                background: 'rgba(255,255,255,0.05)', 
                border: '1px solid var(--card-border)', 
                boxShadow: 'none',
                cursor: isRefreshing ? 'not-allowed' : 'pointer',
                opacity: isRefreshing ? 0.7 : 1,
                transition: 'all 0.25s cubic-bezier(0.4, 0, 0.2, 1)'
              }}
            >
              <RefreshCw size={16} className={isRefreshing ? "spin-animation" : ""} />
              <span style={{ fontSize: '0.85rem' }}>동기화</span>
              <style>{`
                @keyframes spin { 100% { transform: rotate(360deg); } }
                .spin-animation { animation: spin 1s linear infinite; }
              `}</style>
            </button>
          </div>

          {/* 🔍 실시간 특가 검색창 (Toss Style Glassmorphism) */}
          <form onSubmit={handleSearchSubmit} className="search-container">
            <div className="search-bar-wrapper">
              <Search size={18} className="search-icon" />
              <input
                type="text"
                placeholder="찾으시는 특가 상품을 입력하세요 (예: 참치, 닭가슴살, 게이밍 모니터...)"
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
                    setTimeout(() => {
                      fetchLiveDeals(true, 0, activeTab);
                    }, 50);
                  }}
                  className="search-clear-btn"
                >
                  <X size={16} />
                </button>
              )}
              <button type="submit" className="search-submit-btn">검색</button>
            </div>
          </form>

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
              <div className="glass-panel" style={{ padding: '40px', borderRadius: '24px', textAlign: 'center', color: 'var(--text-sub)' }}>
                <p>
                  {loading && deals.length === 0
                    ? "실시간 핫딜을 로드하고 있습니다... 잠시만 기다려 주세요. ⚡"
                    : "해당 카테고리에 등록된 실시간 핫딜이 존재하지 않습니다."}
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
                    style={deal.isClosed ? { opacity: 0.6, filter: 'grayscale(0.5)' } : {}}
                    onClick={(e) => {
                      if (hasValidEcommerceLink) {
                        // 🟢 진짜 쇼핑몰 링크가 존재하면 쇼핑몰로 다이렉트 이동
                      } else {
                        // 🟡 쇼핑몰 링크가 유실되었거나 커뮤니티 주소로 오염된 경우 -> 커뮤니티 원글로 fallback 연쇄 복원
                        e.preventDefault();
                        if (deal.post_link && deal.post_link !== "#") {
                          showToast("이 상품은 쇼핑몰 직접 링크를 복원 중입니다. 출처 커뮤니티 원문으로 안내해 드립니다! 🔗");
                          setTimeout(() => {
                            window.open(deal.post_link, "_blank", "noopener,noreferrer");
                          }, 800);
                        } else {
                          showToast("실시간 쇼핑몰 구매 링크가 준비되지 않았습니다. ⚠️");
                        }
                      }
                    }}
                  >
                    <div className="deal-image-wrapper">
                      {deal.image_url ? (
                        <img src={getProxyImageUrl(deal.image_url)} alt={deal.title} className="deal-image" loading="lazy" referrerPolicy="no-referrer" />
                      ) : (
                        <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--text-sub)', background: 'var(--badge-bg)', fontSize: '0.8rem', fontWeight: 600 }}>
                          이미지 없음
                        </div>
                      )}
                    </div>
                    <div className="deal-info">
                      <div>
                        <div className="deal-meta">
                          {(() => {
                            const badgeSources = (deal.sources && deal.sources.length > 0)
                              ? deal.sources
                              : deal.community_name.split(', ').map(cName => ({ site_name: cName, post_url: deal.post_link }));

                            const uniqueBadgeSources: any[] = [];
                            badgeSources.forEach((src: any) => {
                              const nameClean = src.site_name ? src.site_name.replace(/ - .*$/, '').trim() : '';
                              if (!uniqueBadgeSources.some((x: any) => (x.site_name ? x.site_name.replace(/ - .*$/, '').trim() : '') === nameClean)) {
                                uniqueBadgeSources.push(src);
                              }
                            });

                            return uniqueBadgeSources.map((source: any, idx: number) => {
                              const displaySiteName = source.site_name ? source.site_name.replace(/ - .*$/, '').trim() : '';
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
                                      showToast("출처 커뮤니티 원글 링크를 찾을 수 없습니다. ⚠️");
                                    }
                                  }}
                                  title={`${displaySiteName} 원문 글 보러가기 🔗`}
                                  style={{ 
                                    cursor: 'pointer', 
                                    transition: 'all 0.2s ease',
                                    background: 'rgba(59, 130, 246, 0.15)',
                                    color: '#60a5fa',
                                    border: '1px solid rgba(59, 130, 246, 0.25)',
                                    padding: '2px 8px',
                                    borderRadius: '6px',
                                    fontWeight: 800,
                                    display: 'inline-flex',
                                    alignItems: 'center',
                                    gap: '3px'
                                  }}
                                  onMouseEnter={(e) => {
                                    e.currentTarget.style.background = 'rgba(59, 130, 246, 0.28)';
                                    e.currentTarget.style.borderColor = 'rgba(59, 130, 246, 0.4)';
                                  }}
                                  onMouseLeave={(e) => {
                                    e.currentTarget.style.background = 'rgba(59, 130, 246, 0.15)';
                                    e.currentTarget.style.borderColor = 'rgba(59, 130, 246, 0.25)';
                                  }}
                                >
                                  {displaySiteName} 🔗
                                </span>
                              );
                            });
                          })()}
                          <span>•</span>
                          <span>{getLiveTimeAgo(deal.originalDate, deal.scraped_at)}</span>
                        </div>
                        <h3 className="deal-title">{deal.title}</h3>
                      </div>
                      <div className="price-section" style={{ marginTop: 'auto', display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
                        <div style={{ display: 'flex', alignItems: 'baseline', gap: '8px', flexWrap: 'wrap' }}>
                          <span className="deal-price">{formatPrice(deal.price, deal.currency)}</span>
                          {deal.isClosed ? (
                            <span className="deal-tag" style={{ background: '#334155', color: '#CBD5E1' }}>종료</span>
                          ) : (
                            !deal.isClosed && (
                              (deal.aiSummary?.includes("🔥 [커뮤니티 인기]") || deal.aiSummary?.includes("🔥 [커뮤니티 인증 핫딜]")) &&
                              (deal.honeyScore || 0) >= 100
                            ) ? (
                              <span className="deal-tag" style={{ background: '#FFEBEB', color: '#D32F2F', fontWeight: 800 }}>🔥 핫딜</span>
                            ) : null
                          )}
                          {deal.shipping_fee && !deal.isClosed && (
                            <span className="deal-tag" style={{ background: 'rgba(56, 189, 248, 0.1)', color: '#38BDF8' }}>{deal.shipping_fee}</span>
                          )}
                        </div>
                        {/* 액션 위젯 버튼 (즐겨찾기, 알림) */}
                        <div style={{ display: 'flex', gap: '16px', alignItems: 'center' }}>
                          <button 
                            onClick={(e) => toggleAlert(e, deal.id)}
                            className="bounce-click"
                            style={{ background: 'none', border: 'none', padding: 4, cursor: 'pointer', display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}
                          >
                            <Bell size={20} color={isAlertSet ? "#F59E0B" : "var(--text-sub)"} fill={isAlertSet ? "#F59E0B" : "none"} />
                          </button>
                          <button 
                            onClick={(e) => toggleWishlist(e, deal.id)}
                            className="bounce-click"
                            style={{ background: 'none', border: 'none', padding: 4, cursor: 'pointer', display: 'inline-flex', alignItems: 'center', justifyContent: 'center' }}
                          >
                            <Heart size={20} color={isWishlisted ? "#EF4444" : "var(--text-sub)"} fill={isWishlisted ? "#EF4444" : "none"} />
                          </button>
                        </div>
                      </div>
                      
                      {/* 🔮 AI 판독 리포트 바로가기 버튼 */}
                      <button
                        onClick={(e) => {
                          e.preventDefault();
                          e.stopPropagation();
                          setSelectedDeal(deal);
                        }}
                        className="btn-primary bounce-click"
                        style={{
                          width: '100%',
                          justifyContent: 'center',
                          padding: '10px',
                          borderRadius: '12px',
                          fontSize: '0.85rem',
                          marginTop: '14px',
                          background: 'rgba(99, 102, 241, 0.08)',
                          border: '1px solid rgba(99, 102, 241, 0.15)',
                          color: 'var(--accent)',
                          boxShadow: 'none',
                          display: 'flex',
                          alignItems: 'center',
                          gap: '6px'
                        }}
                      >
                        <RefreshCw size={14} />
                        <span>상세보기 & AI 판독 🔮</span>
                      </button>
                    </div>
                  </a>
                );
              })
            )}
          </div>
          
          {/* 🔄 무한 스크롤(Infinite Scroll) 지능형 트리거 감지 앵커 및 스피너 */}
          {hasMore && (
            <div ref={observerTarget} style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', padding: '40px 0', width: '100%' }}>
              {isFetchingMore ? (
                <div className="ai-loader" style={{ width: '36px', height: '36px' }}></div>
              ) : (
                <div style={{ 
                  color: 'var(--text-sub)', 
                  fontSize: '0.88rem', 
                  fontWeight: 600, 
                  background: 'rgba(255,255,255,0.03)', 
                  padding: '8px 20px', 
                  borderRadius: '99px',
                  border: '1px solid var(--card-border)',
                  animation: 'pulse-badge 2s infinite'
                }}>
                  ⚡ 스크롤하여 더 많은 실시간 핫딜 불러오기...
                </div>
              )}
            </div>
          )}
        </section>

        {/* Toast Notification */}
        {toastMessage && (
          <div className="toast fade-in" style={{
            position: 'fixed',
            bottom: '24px',
            left: '50%',
            transform: 'translateX(-50%)',
            backgroundColor: 'rgba(15, 23, 42, 0.95)',
            color: 'white',
            padding: '12px 24px',
            borderRadius: '9999px',
            boxShadow: '0 10px 25px rgba(0, 0, 0, 0.3)',
            backdropFilter: 'blur(8px)',
            fontWeight: '600',
            fontSize: '0.92rem',
            zIndex: 1000,
            border: '1px solid rgba(255, 255, 255, 0.1)'
          }}>
            {toastMessage}
          </div>
        )}

        {/* <footer> */}
        <footer className="footer">
          <p style={{ display: 'flex', flexDirection: 'column', gap: '8px', alignItems: 'center' }}>
            <span>© 2026 InsightDeal. All rights reserved.</span>
            <span style={{ fontSize: '0.8rem', color: 'var(--text-sub)' }}>
              ⚡ AI 실시간 핫딜 판독 & 스마트 키워드 알림 플랫폼, 인사이트딜
            </span>
            <span style={{ display: 'flex', gap: '16px', fontSize: '0.82rem', marginTop: '4px' }}>
              <button onClick={() => setIsWishlistOpen(true)} style={{ color: '#EF4444', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '4px' }}>
                <Heart size={14} fill="#EF4444" />
                <span>내 찜 목록</span>
              </button>
              <span style={{ color: 'var(--card-border)' }}>|</span>
              <button onClick={() => setShowPrivacyModal(true)} style={{ color: 'var(--accent)', fontWeight: 700, textDecoration: 'underline' }}>
                개인정보 처리방침
              </button>
              <span style={{ color: 'var(--card-border)' }}>|</span>
              <button onClick={() => setShowEulaModal(true)} style={{ color: 'var(--text-sub)', fontWeight: 600 }}>
                서비스 이용약관 (EULA)
              </button>
            </span>
          </p>
        </footer>
      </div>

      {/* 🔮 AI 상세 판독 리포트 모달 */}
      <DealDetailModal 
        deal={selectedDeal} 
        onClose={() => setSelectedDeal(null)} 
        showToast={showToast} 
      />

      {/* 🔔 실시간 핫딜 알림 히스토리 슬라이드 오버 패널 */}
      <NotificationDrawer 
        isOpen={isNotificationOpen} 
        onClose={() => setIsNotificationOpen(false)} 
        deals={deals} 
        onSelectDeal={(deal) => setSelectedDeal(deal)} 
      />

      {/* ❤️ 내 찜 목록 슬라이드 오버 패널 */}
      <WishlistDrawer 
        isOpen={isWishlistOpen} 
        onClose={() => setIsWishlistOpen(false)} 
        wishlistDeals={wishlistDeals} 
        onRemoveDeal={toggleWishlist} 
        isLoggedIn={isLoggedIn} 
        onLoginSimulate={handleLoginSimulate} 
      />

      {/* 🔒 로그인 유도 모달 */}
      <LoginPromptModal 
        isOpen={showLoginModal} 
        onClose={() => setShowLoginModal(false)} 
      />

      {/* 📜 개인정보 처리방침 모달 */}
      <PrivacyModal 
        isOpen={showPrivacyModal} 
        onClose={() => setShowPrivacyModal(false)} 
      />

      {/* 📜 EULA 서비스 이용약관 모달 */}
      <EulaModal 
        isOpen={showEulaModal} 
        onClose={() => setShowEulaModal(false)} 
      />
    </>
  );
}
