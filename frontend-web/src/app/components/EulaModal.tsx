"use client";

import React from "react";

interface EulaModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export default function EulaModal({ isOpen, onClose }: EulaModalProps) {
  if (!isOpen) return null;

  return (
    <div style={{
      position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
      background: 'rgba(15, 23, 42, 0.55)', backdropFilter: 'blur(8px)',
      display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 2000,
      padding: '20px', animation: 'slideUp 0.25s ease'
    }} onClick={onClose}>
      <div className="glass-panel" style={{
        maxWidth: '520px', width: '100%', borderRadius: '20px', padding: '28px',
        maxHeight: '85vh', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '18px',
        background: 'var(--card-bg)', border: '1px solid var(--card-border)'
      }} onClick={(e) => e.stopPropagation()}>
        <div>
          <h3 style={{ fontSize: '1.35rem', fontWeight: 900, marginBottom: '6px', color: 'var(--text-main)' }}>서비스 이용약관 (EULA)</h3>
          <span style={{ fontSize: '0.82rem', color: 'var(--accent)', fontWeight: 800 }}>✓ 최종 사용자 라이선스 계약</span>
        </div>
        
        <div style={{ fontSize: '0.9rem', lineHeight: '1.6', color: 'var(--text-sub)', display: 'flex', flexDirection: 'column', gap: '12px' }}>
          <p>
            <strong style={{ color: 'var(--text-main)' }}>1. 서비스 제공 및 신뢰성 고지</strong><br />
            인사이트딜은 수집 엔진을 기반으로 실시간 최저가 정보를 제공합니다. 다만, 원본 사이트의 실시간 품절 상황이나 쿠폰 조기 종료 등으로 인해 실제 구매처 가격과 차이가 발생할 수 있으며, 이로 인한 직접적/간접적 손실에 대해 법적인 손해배상 책임을 지지 않습니다.
          </p>
          <p>
            <strong style={{ color: 'var(--text-main)' }}>2. 사용 금지 행위 및 계정 조치</strong><br />
            비정상적인 크롤러 기동, 시스템 오버로드 시도, 악성 코드 배포, 및 타인의 저작권을 훼손하는 우회 행위 시, 예고 없이 IP 차단 및 서비스 이용이 즉각 영구 중단될 수 있습니다.
          </p>
        </div>

        <button className="btn-primary" onClick={onClose} style={{ width: '100%', justifyContent: 'center', padding: '14px', borderRadius: '14px', marginTop: '8px' }}>
          확인하고 닫기
        </button>
      </div>
    </div>
  );
}
