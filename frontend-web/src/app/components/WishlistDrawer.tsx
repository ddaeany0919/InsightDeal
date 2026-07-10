"use client";

import React from "react";
import { Heart, Smartphone, Download, Trash2 } from "lucide-react";
import type { Deal } from "../lib/types";
import { formatPrice, getProxyImageUrl } from "../lib/utils";

interface WishlistDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  wishlistDeals: Deal[];
  onRemoveDeal: (e: React.MouseEvent, id: number) => void;
  isLoggedIn: boolean;
  onLoginSimulate: () => void;
}

export default function WishlistDrawer({
  isOpen,
  onClose,
  wishlistDeals,
  onRemoveDeal,
  isLoggedIn,
  onLoginSimulate,
}: WishlistDrawerProps) {
  if (!isOpen) return null;

  return (
    <div style={{
      position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
      background: 'rgba(2, 6, 23, 0.4)', backdropFilter: 'blur(8px)',
      zIndex: 1900, display: 'flex', justifyContent: 'flex-end',
      animation: 'fadeIn 0.3s ease'
    }} onClick={onClose}>
      <div className="glass-panel" style={{
        width: '100%', maxWidth: '420px', height: '100%',
        padding: '32px 24px', display: 'flex', flexDirection: 'column', gap: '24px',
        borderLeft: '1px solid rgba(255,255,255,0.08)', borderRadius: '28px 0 0 28px',
        boxShadow: '-10px 0 40px rgba(0,0,0,0.5)', animation: 'slideLeft 0.3s cubic-bezier(0.16, 1, 0.3, 1)',
        overflowY: 'auto'
      }} onClick={(e) => e.stopPropagation()}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Heart size={20} color="#EF4444" fill="#EF4444" />
            <h3 style={{ fontSize: '1.25rem', fontWeight: 900 }}>내 찜 목록</h3>
          </div>
          <button onClick={onClose} style={{ background: 'rgba(255,255,255,0.05)', border: 'none', cursor: 'pointer', width: 32, height: 32, borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--text-sub)' }}>
            ✕
          </button>
        </div>

        {!isLoggedIn ? (
          /* Empty State 1: 비로그인 */
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', flex: 1, gap: '20px', textAlign: 'center', padding: '20px' }}>
            <Heart size={48} color="var(--text-sub)" style={{ opacity: 0.5 }} />
            <div>
              <p style={{ fontWeight: 700, fontSize: '1rem', marginBottom: '6px' }}>로그인이 필요합니다</p>
              <p style={{ fontSize: '0.85rem', color: 'var(--text-sub)', lineHeight: 1.5 }}>
                찜 목록을 관리하려면 로그인이 필요합니다. 아래 버튼을 눌러 테스트용 계정으로 임시 로그인하거나 앱을 다운로드해 보세요.
              </p>
            </div>
            <button className="btn-primary" onClick={onLoginSimulate} style={{ width: '100%', justifyContent: 'center' }}>
              임시 로그인하기 🔑
            </button>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', width: '100%' }}>
              <button className="btn-primary" style={{ width: "100%", justifyContent: "center", padding: "10px", borderRadius: "12px", fontSize: '0.8rem' }} onClick={() => alert("Google Play 출시 준비 중입니다! 🤖")}>
                <Smartphone size={16} />
                <span>Play Store</span>
              </button>
              <button className="btn-primary" style={{ width: "100%", justifyContent: "center", padding: "10px", borderRadius: "12px", background: "linear-gradient(135deg, #10b981, #059669)", boxShadow: "none", fontSize: '0.8rem' }} onClick={() => alert("App Store 출시 준비 중입니다! 🍎")}>
                <Download size={16} />
                <span>App Store</span>
              </button>
            </div>
          </div>
        ) : wishlistDeals.length === 0 ? (
          /* Empty State 2: 빈 목록 */
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', flex: 1, gap: '16px', textAlign: 'center', color: 'var(--text-sub)' }}>
            <Heart size={48} color="var(--text-sub)" style={{ opacity: 0.3 }} />
            <p style={{ fontWeight: 700 }}>찜한 상품이 없습니다.</p>
            <p style={{ fontSize: '0.82rem', lineHeight: 1.4 }}>
              관심 있는 핫딜 카드의 하트(❤️) 아이콘을 눌러 즐겨찾기에 추가해 보세요.
            </p>
          </div>
        ) : (
          /* 정상 목록 상태 */
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            {wishlistDeals.map((deal) => (
              <div 
                key={deal.id} 
                style={{
                  background: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.04)',
                  borderRadius: '16px', padding: '12px', display: 'flex', gap: '12px',
                  alignItems: 'center', transition: 'all 0.25s ease'
                }}
              >
                <div style={{ width: '50px', height: '50px', borderRadius: '8px', overflow: 'hidden', flexShrink: 0, background: 'var(--badge-bg)' }}>
                  {deal.image_url ? (
                    <img src={getProxyImageUrl(deal.image_url)} alt={deal.title} style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
                  ) : (
                    <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '0.65rem' }}>No Img</div>
                  )}
                </div>
                <div style={{ flex: 1, display: 'flex', flexDirection: 'column', minWidth: 0 }}>
                  <span style={{ fontSize: '0.8rem', fontWeight: 700, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {deal.title}
                  </span>
                  <span style={{ fontSize: '0.75rem', color: '#f87171', fontWeight: 800, marginTop: '2px' }}>
                    {formatPrice(deal.price, deal.currency)}
                  </span>
                </div>
                <button 
                  onClick={(e) => onRemoveDeal(e, deal.id)} 
                  style={{ background: 'none', border: 'none', cursor: 'pointer', padding: '6px', color: 'var(--text-sub)' }}
                  title="찜 해제"
                >
                  <Trash2 size={16} />
                </button>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
