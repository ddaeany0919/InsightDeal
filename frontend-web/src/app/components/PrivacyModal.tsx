"use client";

import React from "react";

interface PrivacyModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export default function PrivacyModal({ isOpen, onClose }: PrivacyModalProps) {
  if (!isOpen) return null;

  return (
    <div style={{
      position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
      background: 'rgba(2, 6, 23, 0.85)', backdropFilter: 'blur(12px)',
      display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 2000,
      padding: '20px', animation: 'slideUp 0.3s ease'
    }} onClick={onClose}>
      <div className="glass-panel" style={{
        maxWidth: '550px', width: '100%', borderRadius: '28px', padding: '32px',
        maxHeight: '85vh', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '20px',
        border: '1px solid rgba(255,255,255,0.08)'
      }} onClick={(e) => e.stopPropagation()}>
        <div>
          <h3 style={{ fontSize: '1.4rem', fontWeight: 900, marginBottom: '6px' }}>개인정보 처리방침</h3>
          <span style={{ fontSize: '0.8rem', color: 'var(--accent)', fontWeight: 800 }}>✓ 생체 인증 로컬 보호</span>
        </div>
        
        <div style={{ fontSize: '0.9rem', lineHeight: '1.6', color: 'var(--text-sub)', display: 'flex', flexDirection: 'column', gap: '12px' }}>
          <p>
            <strong>1. 생체 인식 데이터의 안전한 처리 (핵심)</strong><br />
            인사이트딜(InsightDeal)은 사용자의 생체 정보(지문 등)를 별도로 수집, 저장, 또는 서버로 전송하지 않습니다. 모든 생체 인증은 사용자의 기기 내부 보안 영역(Android KeyStore / iOS Secure Enclave) 내에서만 안전하게 일회성으로 처리 및 검증되며 외부 서버로 유출되지 않음을 보증합니다.
          </p>
          <p>
            <strong>2. 개인정보 수집 및 이용 목적</strong><br />
            키워드 알림 및 기기별 방해금지(DND) 설정을 처리하기 위한 목적으로 기기 식별 값(FCM Token) 및 찜/알림 설정 값을 안전하게 데이터베이스에 전송하며, 회원 탈퇴 시 즉시 영구적으로 파기됩니다.
          </p>
        </div>

        <button className="btn-primary" onClick={onClose} style={{ width: '100%', justifyContent: 'center', padding: '14px', borderRadius: '16px', marginTop: '12px' }}>
          동의 및 닫기
        </button>
      </div>
    </div>
  );
}
