"use client";

import React from "react";
import { Bell } from "lucide-react";
import Link from "next/link";
import type { Deal } from "../lib/types";

interface NotificationDrawerProps {
  isOpen: boolean;
  onClose: () => void;
  deals: Deal[];
  onSelectDeal: (deal: Deal) => void;
}

export default function NotificationDrawer({
  isOpen,
  onClose,
  deals,
  onSelectDeal,
}: NotificationDrawerProps) {
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
            <Bell size={20} color="var(--accent)" />
            <h3 style={{ fontSize: '1.25rem', fontWeight: 900 }}>실시간 알림 허브</h3>
          </div>
          <button onClick={onClose} style={{ background: 'rgba(255,255,255,0.05)', border: 'none', cursor: 'pointer', width: 32, height: 32, borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'var(--text-sub)' }}>
            ✕
          </button>
        </div>

        <p style={{ fontSize: '0.88rem', color: 'var(--text-sub)', lineHeight: 1.5 }}>
          인사이트딜 AI 수집 엔진이 국내 커뮤니티에서 초단위로 포착해 알림을 격발한 최근 푸시 히스토리입니다. 알림 클릭 시 AI 판독 리포트로 즉시 랜딩됩니다.
        </p>

        <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
          {deals.slice(0, 5).map((deal, idx) => {
            const timeAgoLabels = ["3초 전", "45초 전", "2분 전", "5분 전", "12분 전"];
            return (
              <div 
                key={deal.id} 
                className="bounce-click" 
                onClick={() => {
                  onClose();
                  onSelectDeal(deal);
                }}
                style={{
                  background: 'rgba(255,255,255,0.02)', border: '1px solid rgba(255,255,255,0.04)',
                  borderRadius: '16px', padding: '16px', display: 'flex', flexDirection: 'column', gap: '8px',
                  cursor: 'pointer', transition: 'all 0.25s ease'
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span className="deal-source" style={{ fontSize: '0.68rem', padding: '2px 6px' }}>{deal.community_name}</span>
                  <span style={{ fontSize: '0.72rem', color: 'var(--accent)', fontWeight: 800 }}>{timeAgoLabels[idx] || "최근"}</span>
                </div>
                <span style={{ fontSize: '0.85rem', fontWeight: 700, lineHeight: 1.4, display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical', overflow: 'hidden' }}>
                  {deal.title}
                </span>
                <span style={{ fontSize: '0.8rem', color: 'var(--text-sub)', display: 'flex', alignItems: 'center', gap: '4px', fontWeight: 600 }}>
                  ⚡ AI 초정밀 알림 완료
                </span>
              </div>
            );
          })}
        </div>

        <div style={{ marginTop: 'auto', background: 'rgba(59, 130, 246, 0.05)', border: '1px solid rgba(59, 130, 246, 0.12)', borderRadius: '18px', padding: '16px', textAlign: 'center' }}>
          <span style={{ fontSize: '0.82rem', fontWeight: 800, color: 'var(--accent)', display: 'block', marginBottom: '4px' }}>
            실시간 100% 무중단 감시 중 🔍
          </span>
          <span style={{ fontSize: '0.78rem', color: 'var(--text-sub)' }}>
            관심 키워드를 등록하시면 0.1초 만에 최저가 타점을 수집해 푸시해 드립니다.
          </span>
        </div>

        <Link href="/settings/notifications" onClick={onClose} style={{ textDecoration: 'none' }}>
          <div style={{
            marginTop: '12px',
            background: 'rgba(255, 255, 255, 0.05)',
            backdropFilter: 'blur(10px)',
            border: '1px solid rgba(255, 255, 255, 0.1)',
            borderRadius: '16px',
            padding: '12px 16px',
            textAlign: 'center',
            fontSize: '0.85rem',
            fontWeight: 700,
            color: 'var(--text)',
            cursor: 'pointer',
            transition: 'all 0.25s ease',
            boxShadow: '0 8px 32px 0 rgba(0, 0, 0, 0.2)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            gap: '6px'
          }}
          className="bounce-click"
          onMouseEnter={(e) => {
            e.currentTarget.style.background = 'rgba(255, 255, 255, 0.1)';
            e.currentTarget.style.borderColor = 'rgba(255, 255, 255, 0.2)';
          }}
          onMouseLeave={(e) => {
            e.currentTarget.style.background = 'rgba(255, 255, 255, 0.05)';
            e.currentTarget.style.borderColor = 'rgba(255, 255, 255, 0.1)';
          }}
          >
            키워드 알림 설정하러 가기 ⚙️
          </div>
        </Link>
      </div>
    </div>
  );
}
