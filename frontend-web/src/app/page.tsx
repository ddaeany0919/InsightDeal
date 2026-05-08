"use client";

import { useEffect, useState } from "react";
import { Link, Search, TrendingUp, Flame, RefreshCw, Heart, Bell } from "lucide-react";

interface Deal {
  id: number;
  source_community_id: number;
  community_name: string;
  title: string;
  post_link: string;
  price: string;
  image_url: string | null;
  scraped_at: string;
  category: string;
  isClosed: boolean;
  ecommerceLink: string | null;
  shipping_fee: string;
  originalDate?: string;
}

export default function Home() {
  const [deals, setDeals] = useState<Deal[]>([]);
  const [loading, setLoading] = useState(true);
  const [activeTab, setActiveTab] = useState("전체");
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [toastMessage, setToastMessage] = useState<string | null>(null);
  const [currentTime, setCurrentTime] = useState(Date.now());
  const [wishlist, setWishlist] = useState<number[]>([]);
  const [alerts, setAlerts] = useState<number[]>([]);

  const TABS = ["전체", "PC/하드웨어", "가전/TV", "식품/건강", "의류/뷰티", "모바일/상품권", "해외직구", "기타"];

  // 제목 기반 카테고리 분류 함수
  const categorizeDeal = (title: string): string => {
    const t = title.toLowerCase();
    if (/pc|컴퓨터|그래픽|모니터|ssd|hdd|ram|램|메모리|키보드|마우스|노트북|글카|rtx|보드|cpu|인텔|amd|라이젠|지포스|로지텍|공유기|nas|웹캠|헤드셋/i.test(t)) return "PC/하드웨어";
    if (/스마트폰|아이폰|갤럭시|충전|보조배터리|이어폰|버즈|에어팟|헤드폰|애플워치|워치|태블릿|패드|아이패드|케이스|케이블/i.test(t)) return "모바일/상품권";
    if (/tv|티비|냉장고|청소기|세탁기|건조기|선풍기|에어컨|가습기|블렌더|모터|스타일러|공기청정기|안마|로봇|다이슨|lg|삼성/i.test(t)) return "가전/TV";
    if (/나이키|아디다스|티셔츠|바지|신발|운동화|자켓|의류|패딩|모자|양말|구두|슬리퍼|가방|팬츠|아식스|뉴발/i.test(t)) return "의류/뷰티";
    if (/물|생수|라면|커피|치킨|피자|콜라|제로|비타민|유산균|햇반|고기|돼지|소고기|과자|두유|우유|닭|스팸|참치|만두|볶음밥|삼다수|사이다|펩시|오레오/i.test(t)) return "식품/건강";
    if (/상품권|컬쳐|해피머니|도서문화|구글|게임|기프트|쿠폰|편의점|cu|gs|포인트|페이|네이버페이|스팀/i.test(t)) return "모바일/상품권";
    return "기타";
  };

  // 토스트 메시지 띄우기 로직
  const showToast = (message: string) => {
    setToastMessage(message);
    setTimeout(() => setToastMessage(null), 2500);
  };

  const toggleWishlist = (e: React.MouseEvent, id: number) => {
    e.preventDefault(); // a 태그 이동 방지
    setWishlist(prev => {
      const isAdded = prev.includes(id);
      if (isAdded) showToast("즐겨찾기에서 제거되었습니다.");
      else showToast("즐겨찾기에 추가되었습니다.");
      return isAdded ? prev.filter(item => item !== id) : [...prev, id];
    });
  };

  const toggleAlert = (e: React.MouseEvent, id: number) => {
    e.preventDefault(); // a 태그 이동 방지
    setAlerts(prev => {
      const isAlertOn = prev.includes(id);
      if (isAlertOn) showToast("해당 상품의 가격 알림이 해제되었습니다.");
      else showToast("해당 상품의 가격 알림이 설정되었습니다.");
      return isAlertOn ? prev.filter(item => item !== id) : [...prev, id];
    });
  };

  const fetchDeals = async () => {
    try {
      setIsRefreshing(true);
      const res = await fetch("/api/community/hot-deals");
      if (!res.ok) throw new Error("Failed to fetch deals");
      
      const data = await res.json();
      const dealsArray = Array.isArray(data) ? data : (data.deals || data.items || []);
      
      const formattedDeals = dealsArray.map((d: any) => {
        const titleStr = d.title || "";
        return {
          id: d.id,
          source_community_id: d.source_community_id,
          community_name: d.community_name || d.communityName || getCommunityName(d.source_community_id),
          title: titleStr,
          post_link: d.post_link || d.link,
          ecommerceLink: d.ecommerceLink || null,
          isClosed: d.isClosed || false,
          price: d.price || "정보 없음",
          shipping_fee: d.shippingFee || "유료/조건부",
          image_url: d.image_url || d.imageUrl,
          scraped_at: d.scraped_at || d.timeAgo || "방금 전",
          originalDate: d.originalDate,
          category: categorizeDeal(titleStr)
        };
      });

      setDeals(formattedDeals);
    } catch (error) {
      console.error("Error fetching deals:", error);
    } finally {
      setLoading(false);
      setIsRefreshing(false);
    }
  };

  useEffect(() => {
    fetchDeals();
    
    // 실시간 1분 타이머 구독
    const timer = setInterval(() => setCurrentTime(Date.now()), 60000);
    return () => clearInterval(timer);
  }, []);

  const handleRefresh = async () => {
    if (isRefreshing) return;
    setIsRefreshing(true);
    // 1. 강제 스크래핑 지시 (백엔드의 force-scrape 엔드포인트)
    try {
      await fetch('http://localhost:8000/api/community/force-scrape', { method: 'POST' });
    } catch (e) {
      console.log('Force scrape error or not available', e);
    }
    // 2. 데이터 다시 불러오기
    await fetchDeals();
    setIsRefreshing(false);
  };

  const getCommunityName = (id: number) => {
    const map: Record<number, string> = {
      1: "뽐뿌", 2: "루리웹", 3: "클리앙", 4: "펨코", 5: "퀘이사존",
      6: "알리뽐뿌", 7: "빠삭국내", 8: "빠삭해외"
    };
    return map[id] || "알 수 없음";
  };

  const filteredDeals = (deal: Deal) => {
    if (activeTab === "전체") return true;
    return deal.category === activeTab;
  };

  const displayDeals = deals.filter(filteredDeals);

  // 실시간 시간 포맷팅 헬퍼
  const getLiveTimeAgo = (dateStr?: string, fallback?: string) => {
    if (!dateStr) return fallback || "최근";
    const dealTime = new Date(dateStr).getTime();
    if (isNaN(dealTime)) return fallback || "최근";
    
    const diffMs = currentTime - dealTime;
    const diffMins = Math.floor(diffMs / 60000);
    if (diffMins <= 0) return "방금 전";
    if (diffMins < 60) return `${diffMins}분 전`;
    
    const diffHours = Math.floor(diffMins / 60);
    if (diffHours < 24) return `${diffHours}시간 전`;
    
    return `${Math.floor(diffHours / 24)}일 전`;
  };

  return (
    <div className="container" style={{ position: "relative" }}>
      <header className="header glass-panel">
        <h1>InsightDeal</h1>
        <div style={{ display: 'flex', gap: '12px' }}>
          <button className="icon-btn" onClick={handleRefresh} style={{ opacity: isRefreshing ? 0.5 : 1, background: 'none', border: 'none', cursor: 'pointer', color: 'var(--text-sub)' }}>
            <RefreshCw size={22} className={isRefreshing ? "spin-animation" : ""} />
            <style>{`.spin-animation { animation: spin 1s linear infinite; }`}</style>
          </button>
          <button className="icon-btn" style={{ background: 'none', border: 'none', cursor: 'pointer', color: 'var(--text-sub)' }}><Search size={22} /></button>
        </div>
      </header>

      {/* Categories */}
      <div className="tabs-container">
        <div className="tabs">
          {TABS.map((tab) => (
            <button
              key={tab}
              className={`tab ${activeTab === tab ? "active" : ""}`}
              onClick={() => setActiveTab(tab)}
            >
              {tab}
            </button>
          ))}
        </div>
      </div>

      {/* Deal List */}
      <div className="deal-list">
        {loading ? (
          <div className="loading" style={{ marginTop: '40px', textAlign: 'center' }}>
            <TrendingUp size={48} color="#60A5FA" style={{ margin: '0 auto', opacity: 0.5, animation: 'pulse 2s infinite' }} />
            <p style={{ marginTop: '16px', fontWeight: 600, color: 'var(--text-sub)' }}>최신 핫딜을 불러오는 중...</p>
          </div>
        ) : displayDeals.length === 0 ? (
          <div className="empty-state">
            <p>해당 카테고리에 등록된 핫딜이 없습니다.</p>
          </div>
        ) : (
          displayDeals.map((deal: Deal) => {
            const isWishlisted = wishlist.includes(deal.id);
            const isAlertSet = alerts.includes(deal.id);

            return (
              <a key={deal.id} href={deal.ecommerceLink || deal.post_link} target="_blank" rel="noopener noreferrer" className="glass-panel deal-card" style={deal.isClosed ? { opacity: 0.6, filter: 'grayscale(0.5)' } : {}}>
                <div className="deal-image-wrapper">
                  {deal.image_url ? (
                    <img src={deal.image_url} alt={deal.title} className="deal-image" loading="lazy" referrerPolicy="no-referrer" />
                  ) : (
                    <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#64748B', background: '#1E293B', fontSize: '0.8rem', fontWeight: 600 }}>
                      No Img
                    </div>
                  )}
                </div>
                <div className="deal-info">
                  <div>
                    <div className="deal-meta">
                      <span className="deal-source">{deal.community_name}</span>
                      <span>•</span>
                      <span>{getLiveTimeAgo(deal.originalDate, deal.scraped_at)}</span>
                    </div>
                    <h3 className="deal-title">{deal.title}</h3>
                  </div>
                  <div className="price-section" style={{ marginTop: 'auto', display: 'flex', justifyContent: 'space-between', alignItems: 'baseline' }}>
                    <div style={{ display: 'flex', alignItems: 'baseline', gap: '8px', flexWrap: 'wrap' }}>
                       <span className="deal-price">{deal.price}</span>
                       {deal.isClosed ? <span className="deal-tag" style={{ background: '#334155', color: '#CBD5E1' }}>종료</span> : (deal.price !== "정보 없음" && <span className="deal-tag">일반</span>)}
                       {deal.shipping_fee && !deal.isClosed && <span className="deal-tag" style={{ background: 'rgba(56, 189, 248, 0.1)', color: '#38BDF8' }}>{deal.shipping_fee}</span>}
                    </div>
                    {/* 위젯 버튼 (즐겨찾기, 알림) */}
                    <div style={{ display: 'flex', gap: '16px', alignItems: 'center' }}>
                      <button 
                         onClick={(e) => toggleAlert(e, deal.id)}
                         style={{ background: 'none', border: 'none', padding: 0, cursor: 'pointer' }}
                      >
                         <Bell size={22} color={isAlertSet ? "#F59E0B" : "var(--text-sub)"} fill={isAlertSet ? "#F59E0B" : "none"} />
                      </button>
                      <button 
                         onClick={(e) => toggleWishlist(e, deal.id)}
                         style={{ background: 'none', border: 'none', padding: 0, cursor: 'pointer' }}
                      >
                         <Heart size={22} color={isWishlisted ? "#EF4444" : "var(--text-sub)"} fill={isWishlisted ? "#EF4444" : "none"} />
                      </button>
                    </div>
                  </div>
                </div>
              </a>
            );
          })
        )}
      </div>

      {/* Toast Notification */}
      {toastMessage && (
        <div className="toast fade-in" style={{
          position: 'fixed',
          bottom: '24px',
          left: '50%',
          transform: 'translateX(-50%)',
          backgroundColor: 'rgba(30, 41, 59, 0.95)',
          color: 'white',
          padding: '12px 24px',
          borderRadius: '9999px',
          boxShadow: '0 10px 15px -3px rgba(0, 0, 0, 0.1)',
          backdropFilter: 'blur(8px)',
          fontWeight: '500',
          zIndex: 1000,
          border: '1px solid rgba(255, 255, 255, 0.1)'
        }}>
          {toastMessage}
        </div>
      )}

      <style jsx>{`
        @keyframes spin { 100% { transform: rotate(360deg); } }
        @keyframes fadeIn { from { opacity: 0; transform: translate(-50%, 10px); } to { opacity: 1; transform: translate(-50%, 0); } }
        .fade-in { animation: fadeIn 0.3s ease-out forwards; }
      `}</style>
    </div>
  );
}
