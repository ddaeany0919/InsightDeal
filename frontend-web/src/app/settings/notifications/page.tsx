'use client';

import React, { useState, useEffect } from 'react';
import Head from 'next/head';

interface Keyword {
  id: string;
  keyword: string;
}

export default function NotificationSettingsPage() {
  const [isSupported, setIsSupported] = useState(false);
  const [permission, setPermission] = useState<NotificationPermission>('default');
  const [isPushEnabled, setIsPushEnabled] = useState(false);
  const [keywords, setKeywords] = useState<Keyword[]>([]);
  const [keywordInput, setKeywordInput] = useState('');
  const [isDndEnabled, setIsDndEnabled] = useState(false);
  const [dndStart, setDndStart] = useState('21:00');
  const [dndEnd, setDndEnd] = useState('08:00');
  const [isLoading, setIsLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  useEffect(() => {
    // Web Push 지원 여부 확인
    if (typeof window !== 'undefined' && 'serviceWorker' in navigator && 'PushManager' in window) {
      setIsSupported(true);
      setPermission(Notification.permission);
      checkSubscription();
    }
    fetchSettings();
  }, []);

  const checkSubscription = async () => {
    try {
      const registration = await navigator.serviceWorker.ready;
      const subscription = await registration.pushManager.getSubscription();
      setIsPushEnabled(!!subscription);
    } catch (e) {
      console.error('Failed to check push subscription', e);
    }
  };

  const fetchSettings = async () => {
    setIsLoading(true);
    try {
      // 로컬 mock 또는 백엔드 API 연동
      // 실제 API 엔드포인트 `/api/notifications/settings` 호출
      const response = await fetch('/api/notifications/settings').catch(() => null);
      if (response && response.ok) {
        const data = await response.json();
        setKeywords(data.keywords || []);
        setIsDndEnabled(data.dnd_enabled || false);
        setDndStart(data.dnd_start || '21:00');
        setDndEnd(data.dnd_end || '08:00');
      } else {
        // Fallback Mock data
        setKeywords([
          { id: '1', keyword: '아이폰' },
          { id: '2', keyword: '갤럭시' },
          { id: '3', keyword: '모니터' }
        ]);
      }
    } catch (e) {
      console.error(e);
    } finally {
      setIsLoading(false);
    }
  };

  const requestPermission = async () => {
    if (!isSupported) return;
    setIsLoading(true);
    try {
      const result = await Notification.requestPermission();
      setPermission(result);
      if (result === 'granted') {
        await registerServiceWorkerAndSubscribe();
      }
    } catch (e) {
      setErrorMsg('알림 권한 획득 중 에러가 발생했습니다.');
    } finally {
      setIsLoading(false);
    }
  };

  const registerServiceWorkerAndSubscribe = async () => {
    try {
      const registration = await navigator.serviceWorker.register('/sw.js');
      console.log('Service Worker registered with scope:', registration.scope);

      // 백엔드 VAPID Public Key 가져오기
      const vapidResponse = await fetch('/api/push-public-key').catch(() => null);
      const vapidKey = vapidResponse && vapidResponse.ok 
        ? await vapidResponse.text() 
        : 'BEl62i53FC45g9eZTIzMzYyR3N...'; // Fallback VAPID Key

      const subscription = await registration.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(vapidKey)
      });

      // 토큰 동기화 API Routes 호출
      await fetch('/api/push-register', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(subscription)
      });

      setIsPushEnabled(true);
    } catch (e) {
      console.error('Failed to subscribe to push notification', e);
      setErrorMsg('푸시 알림 구독 중 오류가 발생했습니다.');
    }
  };

  const unsubscribePush = async () => {
    setIsLoading(true);
    try {
      const registration = await navigator.serviceWorker.ready;
      const subscription = await registration.pushManager.getSubscription();
      if (subscription) {
        await subscription.unsubscribe();
        // 백엔드 등록 토큰 해제 요청
        await fetch('/api/push-register', {
          method: 'DELETE',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ endpoint: subscription.endpoint })
        }).catch(() => null);
        setIsPushEnabled(false);
      }
    } catch (e) {
      console.error(e);
    } finally {
      setIsLoading(false);
    }
  };

  const handleAddKeyword = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!keywordInput.trim()) return;

    const newKeywordStr = keywordInput.trim();
    setIsLoading(true);
    try {
      const response = await fetch('/api/notifications/keywords', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ keyword: newKeywordStr })
      }).catch(() => null);

      const newId = Date.now().toString();
      setKeywords(prev => [...prev, { id: newId, keyword: newKeywordStr }]);
      setKeywordInput('');
    } catch (e) {
      console.error(e);
    } finally {
      setIsLoading(false);
    }
  };

  const handleDeleteKeyword = async (keywordStr: string) => {
    setIsLoading(true);
    try {
      await fetch(`/api/notifications/keywords?keyword=${encodeURIComponent(keywordStr)}`, {
        method: 'DELETE'
      }).catch(() => null);

      setKeywords(prev => prev.filter(k => k.keyword !== keywordStr));
    } catch (e) {
      console.error(e);
    } finally {
      setIsLoading(false);
    }
  };

  const handleToggleDnd = async () => {
    const nextVal = !isDndEnabled;
    setIsDndEnabled(nextVal);
    try {
      await fetch('/api/notifications/settings', {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          dnd_enabled: nextVal,
          dnd_start: dndStart,
          dnd_end: dndEnd
        })
      }).catch(() => null);
    } catch (e) {
      console.error(e);
    }
  };

  const urlBase64ToUint8Array = (base64String: string) => {
    const padding = '='.repeat((4 - base64String.length % 4) % 4);
    const base64 = (base64String + padding)
      .replace(/\-/g, '+')
      .replace(/_/g, '/');
    const rawData = window.atob(base64);
    const outputArray = new Uint8Array(rawData.length);
    for (let i = 0; i < rawData.length; ++i) {
      outputArray[i] = rawData.charCodeAt(i);
    }
    return outputArray;
  };

  return (
    <>
      <Head>
        <title>알림 및 키워드 설정 - InsightDeal</title>
        <meta name="description" content="실시간 관심 키워드 특가 및 야간 방해금지 DND 설정을 관리해보세요." />
      </Head>

      <div className="bg-ornament bg-ornament-1"></div>
      <div className="bg-ornament bg-ornament-2"></div>

      <div className="web-container" style={{ paddingBottom: '100px' }}>
        <div style={{ maxWidth: '800px', margin: '0 auto' }}>
          <header style={{ marginBottom: '40px', textAlign: 'center' }}>
            <h1 style={{ fontSize: '2.5rem', fontWeight: 900, letterSpacing: '-1.5px', marginBottom: '12px' }}>
              알림 및 키워드 설정
            </h1>
            <p style={{ color: 'var(--text-sub)', fontSize: '1.1rem' }}>
              관심 핫딜의 타이밍을 실시간으로 캐치하고, 야간 방해금지 시간대를 조율해보세요.
            </p>
          </header>

          <main style={{ display: 'flex', flexDirection: 'column', gap: '30px' }}>
            {/* 1. 웹 푸시 알림 허용 여부 카드 */}
            <section className="glass-panel" style={{ borderRadius: '24px', padding: '32px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px' }}>
                <div>
                  <h2 style={{ fontSize: '1.4rem', fontWeight: 800, marginBottom: '6px' }}>실시간 웹 푸시 알림</h2>
                  <p style={{ color: 'var(--text-sub)', fontSize: '0.95rem' }}>브라우저 알림을 켜두시면 탭을 닫아도 실시간 핫딜이 떴을 때 즉각 안내해 드립니다.</p>
                </div>
                {isPushEnabled ? (
                  <button 
                    onClick={unsubscribePush}
                    className="bounce-click" 
                    style={{ background: 'rgba(239, 68, 68, 0.1)', color: '#ef4444', padding: '12px 24px', borderRadius: '16px', fontWeight: 700 }}
                  >
                    알림 끄기
                  </button>
                ) : (
                  <button 
                    onClick={requestPermission}
                    className="btn-primary"
                  >
                    알림 활성화
                  </button>
                )}
              </div>
              {permission === 'denied' && (
                <div style={{ background: 'rgba(239, 68, 68, 0.05)', border: '1px dashed rgba(239, 68, 68, 0.2)', padding: '16px', borderRadius: '16px', fontSize: '0.88rem', color: '#f87171' }}>
                  현재 브라우저 알림 권한이 차단되어 있습니다. 브라우저 주소창 왼쪽의 설정 아이콘을 눌러 알림 권한을 직접 허용해 주세요.
                </div>
              )}
            </section>

            {/* 2. 관심 키워드 알림 관리 카드 */}
            <section className="glass-panel" style={{ borderRadius: '24px', padding: '32px', display: 'flex', flexDirection: 'column', gap: '24px' }}>
              <div>
                <h2 style={{ fontSize: '1.4rem', fontWeight: 800, marginBottom: '6px' }}>알림 키워드 등록</h2>
                <p style={{ color: 'var(--text-sub)', fontSize: '0.95rem' }}>설정한 단어가 포함된 특가가 수집되면 가장 빠르고 영롱하게 알림을 쏩니다.</p>
              </div>

              <form onSubmit={handleAddKeyword} style={{ display: 'flex', gap: '12px' }}>
                <input 
                  type="text" 
                  value={keywordInput}
                  onChange={(e) => setKeywordInput(e.target.value)}
                  placeholder="예: 아이패드, 에어팟, 피존"
                  style={{
                    flex: 1,
                    background: 'rgba(255, 255, 255, 0.04)',
                    border: '1px solid var(--card-border)',
                    borderRadius: '16px',
                    padding: '14px 20px',
                    color: 'var(--text-main)',
                    fontSize: '1rem',
                    fontWeight: 600,
                    outline: 'none',
                    transition: 'border-color 0.3s ease'
                  }}
                />
                <button type="submit" className="btn-primary" style={{ padding: '0 28px' }}>추가</button>
              </form>

              {/* 추천 태그 리스트 */}
              <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', alignItems: 'center' }}>
                <span style={{ fontSize: '0.85rem', color: 'var(--text-sub)', fontWeight: 700, marginRight: '4px' }}>추천 태그:</span>
                {['아이폰', '그래픽카드', '닭가슴살', '캠핑', 'PS5'].map(tag => (
                  <button 
                    key={tag}
                    onClick={() => setKeywordInput(tag)}
                    className="bounce-click"
                    style={{
                      background: 'rgba(255,255,255,0.03)',
                      border: '1px solid var(--card-border)',
                      borderRadius: '99px',
                      padding: '6px 14px',
                      fontSize: '0.8rem',
                      fontWeight: 700,
                      color: 'var(--text-sub)'
                    }}
                  >
                    #{tag}
                  </button>
                ))}
              </div>

              <div style={{ borderTop: '1px solid var(--card-border)', paddingTop: '24px' }}>
                <h3 style={{ fontSize: '1.1rem', fontWeight: 800, marginBottom: '16px' }}>등록된 알림 키워드 ({keywords.length}개)</h3>
                
                {keywords.length === 0 ? (
                  <div style={{ textAlign: 'center', padding: '40px 0', color: 'var(--text-sub)', fontSize: '0.95rem' }}>
                    등록된 키워드가 없습니다. 관심 있는 키워드를 위에 등록해 특가 기회를 사수해 보세요!
                  </div>
                ) : (
                  <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
                    {keywords.map(kw => (
                      <div 
                        key={kw.id} 
                        className="glass-panel"
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: '10px',
                          padding: '8px 18px',
                          borderRadius: '99px',
                          fontSize: '0.9rem',
                          fontWeight: 700,
                          border: '1px solid rgba(var(--accent-rgb), 0.25)',
                          background: 'rgba(var(--accent-rgb), 0.05)',
                          color: 'var(--accent)',
                          animation: 'popIn 0.3s cubic-bezier(0.34, 1.56, 0.64, 1) forwards'
                        }}
                      >
                        <span>{kw.keyword}</span>
                        <button 
                          onClick={() => handleDeleteKeyword(kw.keyword)}
                          style={{
                            display: 'inline-flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            width: '18px',
                            height: '18px',
                            borderRadius: '50%',
                            background: 'rgba(239, 68, 68, 0.1)',
                            color: '#ef4444',
                            fontSize: '11px',
                            fontWeight: 900,
                            lineHeight: 1,
                            transition: 'background-color 0.2s'
                          }}
                          onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'rgba(239, 68, 68, 0.2)'}
                          onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'rgba(239, 68, 68, 0.1)'}
                        >
                          ✕
                        </button>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </section>

            {/* 3. 야간 알림 차단 DND 스케줄러 카드 */}
            <section className="glass-panel" style={{ borderRadius: '24px', padding: '32px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px' }}>
                <div>
                  <h2 style={{ fontSize: '1.4rem', fontWeight: 800, marginBottom: '6px' }}>야간 알림 차단 (방해금지 설정)</h2>
                  <p style={{ color: 'var(--text-sub)', fontSize: '0.95rem' }}>설정한 시간대 동안에는 수신되는 알림이 일시적으로 큐에 보관된 뒤 아침에 전송됩니다.</p>
                </div>
                
                {/* 프리미엄 그라데이션 토글 스위치 */}
                <div 
                  onClick={handleToggleDnd}
                  className="bounce-click"
                  style={{
                    width: '60px',
                    height: '32px',
                    borderRadius: '99px',
                    background: isDndEnabled ? 'var(--primary-gradient)' : 'rgba(255, 255, 255, 0.1)',
                    border: '1px solid var(--card-border)',
                    position: 'relative',
                    cursor: 'pointer',
                    transition: 'background 0.3s ease'
                  }}
                >
                  <div 
                    style={{
                      width: '24px',
                      height: '24px',
                      borderRadius: '50%',
                      background: 'white',
                      boxShadow: '0 2px 8px rgba(0,0,0,0.3)',
                      position: 'absolute',
                      top: '3px',
                      left: isDndEnabled ? '31px' : '4px',
                      transition: 'left 0.25s cubic-bezier(0.34, 1.56, 0.64, 1)'
                    }}
                  />
                </div>
              </div>

              {isDndEnabled && (
                <div 
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: '12px',
                    padding: '20px',
                    borderRadius: '16px',
                    background: 'rgba(255,255,255,0.02)',
                    border: '1px solid var(--card-border)',
                    animation: 'slideDown 0.3s cubic-bezier(0.16, 1, 0.3, 1) forwards'
                  }}
                >
                  <span style={{ fontSize: '0.9rem', fontWeight: 700, color: 'var(--text-sub)' }}>매일</span>
                  <input 
                    type="time" 
                    value={dndStart}
                    onChange={(e) => setDndStart(e.target.value)}
                    style={{
                      background: 'rgba(255,255,255,0.04)',
                      border: '1px solid var(--card-border)',
                      borderRadius: '10px',
                      padding: '8px 12px',
                      color: 'var(--text-main)',
                      fontWeight: 700,
                      outline: 'none'
                    }}
                  />
                  <span style={{ fontSize: '0.9rem', color: 'var(--text-sub)', fontWeight: 700 }}>부터</span>
                  <input 
                    type="time" 
                    value={dndEnd}
                    onChange={(e) => setDndEnd(e.target.value)}
                    style={{
                      background: 'rgba(255,255,255,0.04)',
                      border: '1px solid var(--card-border)',
                      borderRadius: '10px',
                      padding: '8px 12px',
                      color: 'var(--text-main)',
                      fontWeight: 700,
                      outline: 'none'
                    }}
                  />
                  <span style={{ fontSize: '0.9rem', color: 'var(--text-sub)', fontWeight: 700 }}>까지</span>
                </div>
              )}
            </section>
          </main>
        </div>
      </div>

      <style jsx global>{`
        @keyframes popIn {
          from { opacity: 0; transform: scale(0.85); }
          to { opacity: 1; transform: scale(1); }
        }
        @keyframes slideDown {
          from { opacity: 0; transform: translateY(-10px); }
          to { opacity: 1; transform: translateY(0); }
        }
      `}</style>
    </>
  );
}
