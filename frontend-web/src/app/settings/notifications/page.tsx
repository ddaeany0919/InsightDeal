'use client';

import React, { useState, useEffect } from 'react';
import Head from 'next/head';
import Link from 'next/link';
import { apiUrl, API_BASE_URL, getOrCreateWebDeviceUuid } from '../../lib/utils';

interface Keyword {
  id: string;
  keyword: string;
  is_active?: boolean;
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

  const getBrowserDeviceUuid = (): string => getOrCreateWebDeviceUuid();

  const fetchSettings = async () => {
    setIsLoading(true);
    try {
      const deviceUuid = getBrowserDeviceUuid();
      const response = await fetch(apiUrl(`/api/push/keywords?device_uuid=${encodeURIComponent(deviceUuid)}`)).catch(() => null);
      if (response && response.ok) {
        const data = await response.json();
        const detailed = Array.isArray(data.detailed_keywords)
          ? data.detailed_keywords.map((k: any) => ({ id: String(k.id), keyword: k.keyword, is_active: k.is_active !== false }))
          : (data.keywords || []).map((kw: string, idx: number) => ({ id: String(idx + 1), keyword: kw, is_active: true }));
        setKeywords(detailed);
        setIsDndEnabled(Boolean(data.dnd_enabled));
        setDndStart(data.dnd_start_time || '21:00');
        setDndEnd(data.dnd_end_time || '08:00');
      } else {
        const localSaved = typeof window !== 'undefined' ? localStorage.getItem('insightdeal_web_keywords') : null;
        if (localSaved) {
          setKeywords(JSON.parse(localSaved));
        } else {
          setKeywords([]);
        }
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
      const vapidResponse = await fetch(apiUrl('/api/push-public-key')).catch(() => null);
      const vapidKey = vapidResponse && vapidResponse.ok 
        ? await vapidResponse.text() 
        : 'BEl62i53FC45g9eZTIzMzYyR3N...'; // Fallback VAPID Key

      const subscription = await registration.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(vapidKey)
      });

      // 토큰 동기화 API Routes 호출 (브라우저 고유 device_uuid와 함께 전달하여 관심 키워드와 1:1 매핑)
      const subJson = subscription.toJSON();
      const deviceUuid = getBrowserDeviceUuid();
      const pushRegisterEndpoint = API_BASE_URL ? apiUrl('/api/push/register-web') : '/api/push-register';
      await fetch(pushRegisterEndpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          endpoint: subJson.endpoint || subscription.endpoint,
          keys: subJson.keys || {},
          device_uuid: deviceUuid
        })
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
        const deviceUuid = getBrowserDeviceUuid();
        await subscription.unsubscribe();
        // 백엔드 등록 토큰 해제 요청
        const pushDeleteEndpoint = API_BASE_URL
          ? apiUrl(`/api/push/register-web?endpoint=${encodeURIComponent(subscription.endpoint)}&device_uuid=${encodeURIComponent(deviceUuid)}`)
          : '/api/push-register';
        await fetch(pushDeleteEndpoint, {
          method: 'DELETE',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ endpoint: subscription.endpoint, device_uuid: deviceUuid })
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
    if (keywords.some(k => k.keyword === newKeywordStr)) {
      setKeywordInput('');
      return;
    }

    setIsLoading(true);
    try {
      const deviceUuid = getBrowserDeviceUuid();
      await fetch(apiUrl('/api/push/keywords'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ device_uuid: deviceUuid, keyword: newKeywordStr })
      }).catch(() => null);

      const newId = Date.now().toString();
      const updated = [...keywords, { id: newId, keyword: newKeywordStr, is_active: true }];
      setKeywords(updated);
      if (typeof window !== 'undefined') {
        localStorage.setItem('insightdeal_web_keywords', JSON.stringify(updated));
      }
      setKeywordInput('');
    } catch (e) {
      console.error(e);
    } finally {
      setIsLoading(false);
    }
  };

  const handleToggleKeyword = async (keywordStr: string) => {
    try {
      const deviceUuid = getBrowserDeviceUuid();
      const res = await fetch(apiUrl('/api/push/keywords/toggle'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ device_uuid: deviceUuid, keyword: keywordStr })
      }).catch(() => null);
      let nextActive: boolean | undefined;
      if (res && res.ok) {
        const data = await res.json();
        nextActive = Boolean(data.is_active);
      }
      const updated = keywords.map(k =>
        k.keyword === keywordStr
          ? { ...k, is_active: nextActive !== undefined ? nextActive : k.is_active === false }
          : k
      );
      setKeywords(updated);
      if (typeof window !== 'undefined') {
        localStorage.setItem('insightdeal_web_keywords', JSON.stringify(updated));
      }
    } catch (e) {
      console.error(e);
    }
  };

  const handleDeleteKeyword = async (keywordStr: string) => {
    setIsLoading(true);
    try {
      const deviceUuid = getBrowserDeviceUuid();
      await fetch(apiUrl('/api/push/keywords'), {
        method: 'DELETE',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ device_uuid: deviceUuid, keyword: keywordStr })
      }).catch(() => null);

      const updated = keywords.filter(k => k.keyword !== keywordStr);
      setKeywords(updated);
      if (typeof window !== 'undefined') {
        localStorage.setItem('insightdeal_web_keywords', JSON.stringify(updated));
      }
    } catch (e) {
      console.error(e);
    } finally {
      setIsLoading(false);
    }
  };

  const syncDndToBackend = async (nextEnabled: boolean, startStr: string, endStr: string) => {
    try {
      const deviceUuid = getBrowserDeviceUuid();
      await fetch(apiUrl('/api/push/device'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          device_uuid: deviceUuid,
          dnd_enabled: nextEnabled,
          dnd_start_time: startStr,
          dnd_end_time: endStr
        })
      }).catch(() => null);
    } catch (e) {
      console.error(e);
    }
  };

  const handleToggleDnd = async () => {
    const nextVal = !isDndEnabled;
    setIsDndEnabled(nextVal);
    await syncDndToBackend(nextVal, dndStart, dndEnd);
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
          <header style={{ marginBottom: '36px', textAlign: 'center' }}>
            <div style={{ display: 'flex', justifyContent: 'flex-start', marginBottom: '16px' }}>
              <Link
                href="/"
                className="bounce-click"
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  gap: '6px',
                  padding: '8px 14px',
                  borderRadius: '10px',
                  background: 'var(--card-bg)',
                  border: '1px solid var(--card-border)',
                  color: 'var(--text-main)',
                  fontSize: '0.88rem',
                  fontWeight: 700,
                  textDecoration: 'none'
                }}
              >
                ← 홈으로 돌아가기
              </Link>
            </div>
            <h1 style={{ fontSize: '2.2rem', fontWeight: 900, letterSpacing: '-1.2px', marginBottom: '10px', color: 'var(--text-main)' }}>
              알림 및 키워드 설정
            </h1>
            <p style={{ color: 'var(--text-sub)', fontSize: '1rem' }}>
              관심 있는 상품 키워드를 등록하고 방해금지 시간대를 설정할 수 있습니다.
            </p>
          </header>

          {errorMsg && (
            <div style={{ marginBottom: '20px', background: 'rgba(239, 68, 68, 0.08)', border: '1px solid rgba(239, 68, 68, 0.25)', padding: '14px 18px', borderRadius: '14px', color: '#ef4444', fontSize: '0.9rem', fontWeight: 700, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span>{errorMsg}</span>
              <button onClick={() => setErrorMsg(null)} style={{ background: 'transparent', border: 'none', color: '#ef4444', fontWeight: 800, cursor: 'pointer' }}>✕</button>
            </div>
          )}

          <main style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
            {/* 1. 웹 푸시 알림 허용 여부 카드 */}
            <section className="glass-panel" style={{ borderRadius: '20px', padding: '28px', display: 'flex', flexDirection: 'column', gap: '18px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px' }}>
                <div>
                  <h2 style={{ fontSize: '1.3rem', fontWeight: 800, marginBottom: '6px', color: 'var(--text-main)' }}>웹 푸시 알림</h2>
                  <p style={{ color: 'var(--text-sub)', fontSize: '0.92rem' }}>브라우저 알림을 켜두면 화면을 닫아도 새 핫딜 등록 시 바로 알림을 받을 수 있습니다.</p>
                </div>
                {isPushEnabled ? (
                  <button 
                    onClick={unsubscribePush}
                    disabled={isLoading}
                    className="bounce-click" 
                    style={{ background: 'rgba(239, 68, 68, 0.1)', color: '#ef4444', padding: '11px 22px', borderRadius: '12px', fontWeight: 700, border: '1px solid rgba(239, 68, 68, 0.25)', cursor: 'pointer' }}
                  >
                    알림 끄기
                  </button>
                ) : (
                  <button 
                    onClick={requestPermission}
                    disabled={isLoading}
                    className="btn-primary"
                  >
                    알림 켜기
                  </button>
                )}
              </div>
              {permission === 'denied' && (
                <div style={{ background: 'rgba(239, 68, 68, 0.06)', border: '1px dashed rgba(239, 68, 68, 0.25)', padding: '14px 16px', borderRadius: '14px', fontSize: '0.88rem', color: '#dc2626', fontWeight: 600 }}>
                  현재 브라우저 알림 권한이 차단되어 있습니다. 주소창 왼쪽의 설정 아이콘을 눌러 알림 권한을 허용해 주세요.
                </div>
              )}
            </section>

            {/* 2. 관심 키워드 알림 관리 카드 */}
            <section className="glass-panel" style={{ borderRadius: '20px', padding: '28px', display: 'flex', flexDirection: 'column', gap: '20px' }}>
              <div>
                <h2 style={{ fontSize: '1.3rem', fontWeight: 800, marginBottom: '6px', color: 'var(--text-main)' }}>알림 키워드 등록</h2>
                <p style={{ color: 'var(--text-sub)', fontSize: '0.92rem' }}>등록한 키워드가 포함된 핫딜이 올라오면 즉시 알려드립니다.</p>
              </div>

              <form onSubmit={handleAddKeyword} style={{ display: 'flex', gap: '12px' }}>
                <input 
                  type="text" 
                  value={keywordInput}
                  onChange={(e) => setKeywordInput(e.target.value)}
                  placeholder="예: 제로콜라 :15000 / 아이폰 -케이스 -필름"
                  aria-label="알림 키워드 입력"
                  style={{
                    flex: 1,
                    background: 'var(--surface-subtle)',
                    border: '1px solid var(--card-border)',
                    borderRadius: '12px',
                    padding: '13px 18px',
                    color: 'var(--text-main)',
                    fontSize: '0.96rem',
                    fontWeight: 600,
                    outline: 'none',
                    transition: 'border-color 0.2s ease'
                  }}
                />
                <button type="submit" disabled={isLoading} className="btn-primary" style={{ padding: '0 26px' }}>추가</button>
              </form>

              <div style={{ fontSize: '0.84rem', color: 'var(--text-sub)', background: 'rgba(59, 130, 246, 0.08)', border: '1px solid rgba(59, 130, 246, 0.2)', borderRadius: '12px', padding: '10px 14px', lineHeight: 1.5 }}>
                <strong style={{ color: 'var(--text-main)' }}>조건 필터 팁:</strong> 제외할 단어 앞에 <code>-</code>를 붙이거나(예: <code>아이폰 -케이스 -필름</code>), 상한 금액 앞에 <code>:</code>를 붙이면(예: <code>삼다수 :12000</code>) 원하는 조건의 핫딜만 골라서 알림을 받을 수 있어요.
              </div>

              {/* 추천 태그 리스트 */}
              <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', alignItems: 'center' }}>
                <span style={{ fontSize: '0.85rem', color: 'var(--text-sub)', fontWeight: 700, marginRight: '4px' }}>추천 예시:</span>
                {['아이폰 -케이스', '제로콜라 :15000', '그래픽카드', '닭가슴살', 'PS5 -커버'].map(tag => (
                  <button 
                    key={tag}
                    type="button"
                    onClick={() => setKeywordInput(tag)}
                    className="bounce-click"
                    style={{
                      background: 'var(--surface-subtle)',
                      border: '1px solid var(--card-border)',
                      borderRadius: '99px',
                      padding: '6px 14px',
                      fontSize: '0.8rem',
                      fontWeight: 700,
                      color: 'var(--text-sub)',
                      cursor: 'pointer'
                    }}
                  >
                    #{tag}
                  </button>
                ))}
              </div>

              <div style={{ borderTop: '1px solid var(--card-border)', paddingTop: '20px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '14px', flexWrap: 'wrap', gap: '8px' }}>
                  <h3 style={{ fontSize: '1.05rem', fontWeight: 800, color: 'var(--text-main)' }}>등록된 알림 키워드 ({keywords.length}개)</h3>
                  <span style={{ fontSize: '0.78rem', color: 'var(--text-sub)', fontWeight: 600 }}>키워드를 클릭하면 삭제하지 않고 알림을 일시정지할 수 있어요</span>
                </div>
                
                {keywords.length === 0 ? (
                  <div style={{ textAlign: 'center', padding: '32px 0', color: 'var(--text-sub)', fontSize: '0.92rem' }}>
                    등록된 키워드가 없습니다. 위 입력창에서 자주 찾는 상품을 등록해 보세요.
                  </div>
                ) : (
                  <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap' }}>
                    {keywords.map(kw => {
                      const isActive = kw.is_active !== false;
                      return (
                      <div 
                        key={kw.id} 
                        className="glass-panel"
                        onClick={() => handleToggleKeyword(kw.keyword)}
                        title={isActive ? '클릭하여 알림 일시정지' : '클릭하여 알림 다시 켜기'}
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: '10px',
                          padding: '8px 16px',
                          borderRadius: '99px',
                          fontSize: '0.88rem',
                          fontWeight: 700,
                          cursor: 'pointer',
                          border: isActive ? '1px solid rgba(var(--accent-rgb), 0.3)' : '1px solid var(--card-border)',
                          background: isActive ? 'rgba(var(--accent-rgb), 0.08)' : 'var(--surface-subtle)',
                          color: isActive ? 'var(--accent)' : 'var(--text-sub)',
                          opacity: isActive ? 1 : 0.7,
                          animation: 'popIn 0.25s cubic-bezier(0.34, 1.56, 0.64, 1) forwards'
                        }}
                      >
                        <span>{kw.keyword}{!isActive ? ' (일시정지)' : ''}</span>
                        <button 
                          type="button"
                          aria-label={`${kw.keyword} 키워드 삭제`}
                          onClick={(e) => { e.stopPropagation(); handleDeleteKeyword(kw.keyword); }}
                          style={{
                            display: 'inline-flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            width: '18px',
                            height: '18px',
                            borderRadius: '50%',
                            background: 'rgba(239, 68, 68, 0.12)',
                            border: 'none',
                            color: '#ef4444',
                            fontSize: '11px',
                            fontWeight: 900,
                            lineHeight: 1,
                            cursor: 'pointer',
                            transition: 'background-color 0.2s'
                          }}
                          onMouseEnter={(e) => e.currentTarget.style.backgroundColor = 'rgba(239, 68, 68, 0.22)'}
                          onMouseLeave={(e) => e.currentTarget.style.backgroundColor = 'rgba(239, 68, 68, 0.12)'}
                        >
                          ✕
                        </button>
                      </div>
                      );
                    })}
                  </div>
                )}
              </div>
            </section>

            {/* 3. 야간 알림 차단 DND 스케줄러 카드 */}
            <section className="glass-panel" style={{ borderRadius: '20px', padding: '28px', display: 'flex', flexDirection: 'column', gap: '18px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '16px' }}>
                <div>
                  <h2 style={{ fontSize: '1.3rem', fontWeight: 800, marginBottom: '6px', color: 'var(--text-main)' }}>야간 알림 차단 (방해금지 설정)</h2>
                  <p style={{ color: 'var(--text-sub)', fontSize: '0.92rem' }}>설정한 시간대 동안에는 수신되는 알림이 일시적으로 큐에 보관된 뒤 아침에 전송됩니다.</p>
                </div>
                
                {/* 프리미엄 그라데이션 토글 스위치 */}
                <button 
                  type="button"
                  role="switch"
                  aria-checked={isDndEnabled}
                  aria-label="야간 알림 차단 방해금지 설정"
                  onClick={handleToggleDnd}
                  className="bounce-click"
                  style={{
                    width: '60px',
                    height: '32px',
                    borderRadius: '99px',
                    background: isDndEnabled ? 'var(--primary-gradient)' : 'rgba(148, 163, 184, 0.35)',
                    border: '1px solid var(--card-border)',
                    position: 'relative',
                    cursor: 'pointer',
                    transition: 'background 0.3s ease',
                    padding: 0
                  }}
                >
                  <div 
                    style={{
                      width: '24px',
                      height: '24px',
                      borderRadius: '50%',
                      background: 'white',
                      boxShadow: '0 2px 6px rgba(15, 23, 42, 0.2)',
                      position: 'absolute',
                      top: '3px',
                      left: isDndEnabled ? '31px' : '4px',
                      transition: 'left 0.25s cubic-bezier(0.34, 1.56, 0.64, 1)'
                    }}
                  />
                </button>
              </div>

              {isDndEnabled && (
                <div 
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    gap: '12px',
                    padding: '18px',
                    borderRadius: '14px',
                    background: 'var(--surface-subtle)',
                    border: '1px solid var(--card-border)',
                    animation: 'slideDown 0.3s cubic-bezier(0.16, 1, 0.3, 1) forwards'
                  }}
                >
                  <span style={{ fontSize: '0.9rem', fontWeight: 700, color: 'var(--text-sub)' }}>매일</span>
                  <input 
                    type="time" 
                    aria-label="방해금지 시작 시간"
                    value={dndStart}
                    onChange={(e) => {
                      const nextStart = e.target.value;
                      setDndStart(nextStart);
                      syncDndToBackend(isDndEnabled, nextStart, dndEnd);
                    }}
                    style={{
                      background: 'var(--card-bg)',
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
                    aria-label="방해금지 종료 시간"
                    value={dndEnd}
                    onChange={(e) => {
                      const nextEnd = e.target.value;
                      setDndEnd(nextEnd);
                      syncDndToBackend(isDndEnabled, dndStart, nextEnd);
                    }}
                    style={{
                      background: 'var(--card-bg)',
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
