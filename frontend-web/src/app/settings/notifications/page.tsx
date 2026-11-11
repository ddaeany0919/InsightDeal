'use client';

import React, { useState, useEffect, useCallback } from 'react';
import Link from 'next/link';
import { apiUrl, API_BASE_URL, getOrCreateWebDeviceUuid } from '../../lib/utils';

interface Keyword {
  id: string;
  keyword: string;
  is_active?: boolean;
}

interface RawKeywordDetail {
  id?: string | number;
  keyword: string;
  is_active?: boolean;
}

const DEFAULT_VAPID_PUBLIC_KEY =
  process.env.NEXT_PUBLIC_VAPID_PUBLIC_KEY ||
  'BEl62iUYgUivxIkv69yViEuiBIa-Ib9-SkvMeAtA3LFgDzkrxZJjSgSnfckjBJuBkr3qBUYIHBQFLXYp5Nksh8U';

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

  const getBrowserDeviceUuid = useCallback((): string => getOrCreateWebDeviceUuid(), []);

  const checkSubscription = useCallback(async () => {
    try {
      const registration = await navigator.serviceWorker.ready;
      const subscription = await registration.pushManager.getSubscription();
      setIsPushEnabled(!!subscription);
    } catch (e) {
      console.error('Failed to check push subscription', e);
    }
  }, []);

  const fetchSettings = useCallback(async () => {
    setIsLoading(true);
    try {
      const deviceUuid = getBrowserDeviceUuid();
      const response = await fetch(
        apiUrl(`/api/push/keywords?device_uuid=${encodeURIComponent(deviceUuid)}`)
      ).catch(() => null);
      if (response && response.ok) {
        const data = await response.json();
        const detailed = Array.isArray(data.detailed_keywords)
          ? data.detailed_keywords.map((k: RawKeywordDetail) => ({
              id: String(k.id ?? k.keyword),
              keyword: k.keyword,
              is_active: k.is_active !== false,
            }))
          : (data.keywords || []).map((kw: string, idx: number) => ({
              id: String(idx + 1),
              keyword: kw,
              is_active: true,
            }));
        setKeywords(detailed);
        setIsDndEnabled(Boolean(data.dnd_enabled));
        setDndStart(data.dnd_start_time || '21:00');
        setDndEnd(data.dnd_end_time || '08:00');
      } else {
        const localSaved =
          typeof window !== 'undefined'
            ? localStorage.getItem('insightdeal_web_keywords')
            : null;
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
  }, [getBrowserDeviceUuid]);

  useEffect(() => {
    if (
      typeof window !== 'undefined' &&
      'serviceWorker' in navigator &&
      'PushManager' in window
    ) {
      setIsSupported(true);
      setPermission(Notification.permission);
      checkSubscription();
    }
    fetchSettings();
  }, [checkSubscription, fetchSettings]);

  const urlBase64ToUint8Array = (
    base64String: string
  ): Uint8Array<ArrayBuffer> | null => {
    try {
      if (!base64String || typeof base64String !== 'string') return null;
      const cleanBase64 = base64String.trim().replace(/\.+$/, '');
      const padding = '='.repeat((4 - (cleanBase64.length % 4)) % 4);
      const base64 = (cleanBase64 + padding)
        .replace(/-/g, '+')
        .replace(/_/g, '/');
      const rawData = window.atob(base64);
      const buffer = new ArrayBuffer(rawData.length);
      const outputArray = new Uint8Array(buffer);
      for (let i = 0; i < rawData.length; ++i) {
        outputArray[i] = rawData.charCodeAt(i);
      }
      return outputArray;
    } catch (e) {
      console.error('Failed to parse VAPID key to Uint8Array', e);
      return null;
    }
  };

  const registerServiceWorkerAndSubscribe = async () => {
    try {
      const registration = await navigator.serviceWorker.register('/sw.js');

      let vapidKey = process.env.NEXT_PUBLIC_VAPID_PUBLIC_KEY;
      if (!vapidKey) {
        try {
          const vapidResponse = await fetch(
            apiUrl('/api/push-public-key')
          ).catch(() => null);
          if (vapidResponse && vapidResponse.ok) {
            const fetched = (await vapidResponse.text()).trim();
            if (fetched && !fetched.includes('...')) {
              vapidKey = fetched;
            }
          }
        } catch {
          // 기본 공개키 사용
        }
      }
      if (!vapidKey || vapidKey.includes('...')) {
        vapidKey = DEFAULT_VAPID_PUBLIC_KEY;
      }

      let appServerKey = urlBase64ToUint8Array(vapidKey);
      if (!appServerKey) {
        appServerKey = urlBase64ToUint8Array(DEFAULT_VAPID_PUBLIC_KEY);
      }

      if (!appServerKey) {
        throw new Error('알림용 공개키를 불러오지 못했어요.');
      }

      const subscription = await registration.pushManager.subscribe({
        userVisibleOnly: true,
        applicationServerKey: appServerKey,
      });

      const subJson = subscription.toJSON();
      const deviceUuid = getBrowserDeviceUuid();
      const pushRegisterEndpoint = API_BASE_URL
        ? apiUrl('/api/push/register-web')
        : '/api/push-register';
      await fetch(pushRegisterEndpoint, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          endpoint: subJson.endpoint || subscription.endpoint,
          keys: subJson.keys || {},
          device_uuid: deviceUuid,
        }),
      });

      setIsPushEnabled(true);
    } catch (e: unknown) {
      console.error('Failed to subscribe to push notification', e);
      setErrorMsg('푸시 알림을 연결하지 못했어요. 잠시 후 다시 시도해 주세요.');
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
    } catch {
      setErrorMsg('알림 권한을 확인하는 중 문제가 생겼어요.');
    } finally {
      setIsLoading(false);
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
        const pushDeleteEndpoint = API_BASE_URL
          ? apiUrl(
              `/api/push/register-web?endpoint=${encodeURIComponent(
                subscription.endpoint
              )}&device_uuid=${encodeURIComponent(deviceUuid)}`
            )
          : '/api/push-register';
        await fetch(pushDeleteEndpoint, {
          method: 'DELETE',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            endpoint: subscription.endpoint,
            device_uuid: deviceUuid,
          }),
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
    if (keywords.some((k) => k.keyword === newKeywordStr)) {
      setKeywordInput('');
      return;
    }

    setIsLoading(true);
    try {
      const deviceUuid = getBrowserDeviceUuid();
      await fetch(apiUrl('/api/push/keywords'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ device_uuid: deviceUuid, keyword: newKeywordStr }),
      }).catch(() => null);

      const newId = Date.now().toString();
      const updated = [
        ...keywords,
        { id: newId, keyword: newKeywordStr, is_active: true },
      ];
      setKeywords(updated);
      if (typeof window !== 'undefined') {
        localStorage.setItem(
          'insightdeal_web_keywords',
          JSON.stringify(updated)
        );
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
        body: JSON.stringify({ device_uuid: deviceUuid, keyword: keywordStr }),
      }).catch(() => null);
      let nextActive: boolean | undefined;
      if (res && res.ok) {
        const data = await res.json();
        nextActive = Boolean(data.is_active);
      }
      const updated = keywords.map((k) =>
        k.keyword === keywordStr
          ? {
              ...k,
              is_active:
                nextActive !== undefined ? nextActive : k.is_active === false,
            }
          : k
      );
      setKeywords(updated);
      if (typeof window !== 'undefined') {
        localStorage.setItem(
          'insightdeal_web_keywords',
          JSON.stringify(updated)
        );
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
        body: JSON.stringify({ device_uuid: deviceUuid, keyword: keywordStr }),
      }).catch(() => null);

      const updated = keywords.filter((k) => k.keyword !== keywordStr);
      setKeywords(updated);
      if (typeof window !== 'undefined') {
        localStorage.setItem(
          'insightdeal_web_keywords',
          JSON.stringify(updated)
        );
      }
    } catch (e) {
      console.error(e);
    } finally {
      setIsLoading(false);
    }
  };

  const syncDndToBackend = async (
    nextEnabled: boolean,
    startStr: string,
    endStr: string
  ) => {
    try {
      const deviceUuid = getBrowserDeviceUuid();
      await fetch(apiUrl('/api/push/device'), {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          device_uuid: deviceUuid,
          dnd_enabled: nextEnabled,
          dnd_start_time: startStr,
          dnd_end_time: endStr,
        }),
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

  return (
    <div
      className="web-container"
      style={{
        paddingTop: 'calc(32px + env(safe-area-inset-top, 0px))',
        paddingBottom: 'calc(80px + env(safe-area-inset-bottom, 0px))',
      }}
    >
      <div style={{ maxWidth: '760px', margin: '0 auto' }}>
        <header style={{ marginBottom: '28px', textAlign: 'left' }}>
          <div style={{ display: 'flex', justifyContent: 'flex-start', marginBottom: '16px' }}>
            <Link
              href="/"
              className="bounce-click"
              style={{
                display: 'inline-flex',
                alignItems: 'center',
                minHeight: '44px',
                gap: '6px',
                padding: '8px 14px',
                borderRadius: '10px',
                background: 'var(--card-bg)',
                border: '1px solid var(--card-border)',
                color: 'var(--text-main)',
                fontSize: '0.88rem',
                fontWeight: 700,
                textDecoration: 'none',
              }}
            >
              ← 홈으로 돌아가기
            </Link>
          </div>
          <h1
            style={{
              fontSize: '1.85rem',
              fontWeight: 800,
              letterSpacing: '-0.8px',
              marginBottom: '6px',
              color: 'var(--text-main)',
            }}
          >
            알림 및 키워드 설정
          </h1>
          <p style={{ color: 'var(--text-sub)', fontSize: '0.94rem' }}>
            찾는 상품 키워드를 등록하고 방해금지 시간을 설정할 수 있어요
          </p>
        </header>

        {errorMsg && (
          <div
            style={{
              marginBottom: '20px',
              background: 'rgba(239, 68, 68, 0.08)',
              border: '1px solid rgba(239, 68, 68, 0.25)',
              padding: '10px 12px 10px 16px',
              borderRadius: '14px',
              color: '#ef4444',
              fontSize: '0.88rem',
              fontWeight: 700,
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center',
              gap: '8px',
            }}
          >
            <span>{errorMsg}</span>
            <button
              type="button"
              aria-label="오류 안내 닫기"
              onClick={() => setErrorMsg(null)}
              style={{
                width: '44px',
                height: '44px',
                minWidth: '44px',
                minHeight: '44px',
                borderRadius: '50%',
                background: 'transparent',
                border: 'none',
                color: '#ef4444',
                fontWeight: 800,
                cursor: 'pointer',
                display: 'inline-flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              ✕
            </button>
          </div>
        )}

        <main style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          <section
            className="glass-panel"
            style={{
              borderRadius: '18px',
              padding: '24px',
              display: 'flex',
              flexDirection: 'column',
              gap: '16px',
            }}
          >
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                flexWrap: 'wrap',
                gap: '16px',
              }}
            >
              <div>
                <h2
                  style={{
                    fontSize: '1.15rem',
                    fontWeight: 800,
                    marginBottom: '4px',
                    color: 'var(--text-main)',
                  }}
                >
                  브라우저 푸시 알림
                </h2>
                <p style={{ color: 'var(--text-sub)', fontSize: '0.88rem' }}>
                  알림을 켜두면 화면을 닫아도 새 특가가 올라올 때 바로 알려드려요
                </p>
              </div>
              {isPushEnabled ? (
                <button
                  type="button"
                  onClick={unsubscribePush}
                  disabled={isLoading}
                  className="bounce-click"
                  style={{
                    minHeight: '44px',
                    background: 'rgba(239, 68, 68, 0.1)',
                    color: '#ef4444',
                    padding: '10px 20px',
                    borderRadius: '12px',
                    fontWeight: 700,
                    border: '1px solid rgba(239, 68, 68, 0.25)',
                    cursor: 'pointer',
                  }}
                >
                  알림 끄기
                </button>
              ) : (
                <button
                  type="button"
                  onClick={requestPermission}
                  disabled={isLoading}
                  className="btn-primary"
                >
                  알림 켜기
                </button>
              )}
            </div>
            {permission === 'denied' && (
              <div
                style={{
                  background: 'rgba(239, 68, 68, 0.06)',
                  border: '1px solid rgba(239, 68, 68, 0.2)',
                  padding: '12px 14px',
                  borderRadius: '12px',
                  fontSize: '0.86rem',
                  color: '#dc2626',
                  fontWeight: 600,
                }}
              >
                브라우저 알림 권한이 꺼져 있어요. 주소창 왼쪽 아이콘에서 알림을 허용해 주세요.
              </div>
            )}
          </section>

          <section
            className="glass-panel"
            style={{
              borderRadius: '18px',
              padding: '24px',
              display: 'flex',
              flexDirection: 'column',
              gap: '18px',
            }}
          >
            <div>
              <h2
                style={{
                  fontSize: '1.15rem',
                  fontWeight: 800,
                  marginBottom: '4px',
                  color: 'var(--text-main)',
                }}
              >
                알림 키워드
              </h2>
              <p style={{ color: 'var(--text-sub)', fontSize: '0.88rem' }}>
                등록한 단어가 포함된 새 딜이 올라오면 바로 알려드려요
              </p>
            </div>

            <form onSubmit={handleAddKeyword} style={{ display: 'flex', gap: '10px' }}>
              <input
                type="text"
                value={keywordInput}
                onChange={(e) => setKeywordInput(e.target.value)}
                placeholder="예: 제로콜라 :15000 / 아이폰 -케이스 -필름"
                aria-label="알림 키워드 입력"
                style={{
                  flex: 1,
                  minWidth: 0,
                  minHeight: '46px',
                  background: 'var(--surface-subtle)',
                  border: '1px solid var(--card-border)',
                  borderRadius: '12px',
                  padding: '12px 16px',
                  color: 'var(--text-main)',
                  fontSize: '0.95rem',
                  fontWeight: 600,
                  outline: 'none',
                }}
              />
              <button
                type="submit"
                disabled={isLoading}
                className="btn-primary"
                style={{ minHeight: '46px', padding: '0 22px', flexShrink: 0 }}
              >
                추가
              </button>
            </form>

            <div
              style={{
                fontSize: '0.83rem',
                color: 'var(--text-sub)',
                background: 'var(--surface-subtle)',
                border: '1px solid var(--card-border)',
                borderRadius: '12px',
                padding: '10px 14px',
                lineHeight: 1.5,
              }}
            >
              <strong style={{ color: 'var(--text-main)' }}>조건 검색 팁:</strong> 제외할 단어
              앞에는 <code>-</code>를, 상한 금액 앞에는 <code>:</code>를 붙여 보세요 (예:{' '}
              <code>아이폰 -케이스</code>, <code>삼다수 :12000</code>).
            </div>

            <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', alignItems: 'center' }}>
              <span
                style={{
                  fontSize: '0.82rem',
                  color: 'var(--text-sub)',
                  fontWeight: 700,
                  marginRight: '2px',
                }}
              >
                예시 키워드
              </span>
              {['아이폰 -케이스', '제로콜라 :15000', '그래픽카드', '닭가슴살', 'PS5 -커버'].map(
                (tag) => (
                  <button
                    key={tag}
                    type="button"
                    onClick={() => setKeywordInput(tag)}
                    className="bounce-click"
                    style={{
                      minHeight: '40px',
                      background: 'var(--surface-subtle)',
                      border: '1px solid var(--card-border)',
                      borderRadius: '99px',
                      padding: '6px 14px',
                      fontSize: '0.8rem',
                      fontWeight: 600,
                      color: 'var(--text-sub)',
                      cursor: 'pointer',
                    }}
                  >
                    {tag}
                  </button>
                )
              )}
            </div>

            <div style={{ borderTop: '1px solid var(--card-border)', paddingTop: '18px' }}>
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  marginBottom: '12px',
                  flexWrap: 'wrap',
                  gap: '6px',
                }}
              >
                <h3
                  style={{
                    fontSize: '1rem',
                    fontWeight: 800,
                    color: 'var(--text-main)',
                  }}
                >
                  등록한 키워드 ({keywords.length})
                </h3>
                <span style={{ fontSize: '0.78rem', color: 'var(--text-sub)', fontWeight: 500 }}>
                  키워드를 누르면 알림을 잠시 멈출 수 있어요
                </span>
              </div>

              {keywords.length === 0 ? (
                <div
                  style={{
                    textAlign: 'center',
                    padding: '28px 0',
                    color: 'var(--text-sub)',
                    fontSize: '0.9rem',
                  }}
                >
                  아직 등록한 키워드가 없어요
                </div>
              ) : (
                <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                  {keywords.map((kw) => {
                    const isActive = kw.is_active !== false;
                    return (
                      <div
                        key={kw.id}
                        className="glass-panel"
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          minHeight: '44px',
                          padding: '0 4px 0 14px',
                          borderRadius: '99px',
                          fontSize: '0.86rem',
                          fontWeight: 700,
                          border: isActive
                            ? '1px solid rgba(var(--accent-rgb), 0.3)'
                            : '1px solid var(--card-border)',
                          background: isActive
                            ? 'rgba(var(--accent-rgb), 0.08)'
                            : 'var(--surface-subtle)',
                          color: isActive ? 'var(--accent)' : 'var(--text-sub)',
                          opacity: isActive ? 1 : 0.72,
                        }}
                      >
                        <button
                          type="button"
                          onClick={() => handleToggleKeyword(kw.keyword)}
                          title={isActive ? '알림 일시정지' : '알림 다시 켜기'}
                          style={{
                            minHeight: '44px',
                            display: 'inline-flex',
                            alignItems: 'center',
                            paddingRight: '6px',
                            fontWeight: 700,
                            fontSize: 'inherit',
                            color: 'inherit',
                            cursor: 'pointer',
                          }}
                        >
                          {kw.keyword}
                          {!isActive ? ' (일시정지)' : ''}
                        </button>
                        <button
                          type="button"
                          aria-label={`${kw.keyword} 키워드 삭제`}
                          onClick={() => handleDeleteKeyword(kw.keyword)}
                          style={{
                            display: 'inline-flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            width: '36px',
                            height: '36px',
                            borderRadius: '50%',
                            background: 'transparent',
                            border: 'none',
                            color: 'var(--text-muted)',
                            fontSize: '12px',
                            fontWeight: 800,
                            cursor: 'pointer',
                          }}
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

          <section
            className="glass-panel"
            style={{
              borderRadius: '18px',
              padding: '24px',
              display: 'flex',
              flexDirection: 'column',
              gap: '16px',
            }}
          >
            <div
              style={{
                display: 'flex',
                justifyContent: 'space-between',
                alignItems: 'center',
                flexWrap: 'wrap',
                gap: '16px',
              }}
            >
              <div>
                <h2
                  style={{
                    fontSize: '1.15rem',
                    fontWeight: 800,
                    marginBottom: '4px',
                    color: 'var(--text-main)',
                  }}
                >
                  방해금지 시간대
                </h2>
                <p style={{ color: 'var(--text-sub)', fontSize: '0.88rem' }}>
                  설정한 시간 동안에는 알림을 보내지 않고 끝난 뒤 모아 알려드려요
                </p>
              </div>

              <button
                type="button"
                role="switch"
                aria-checked={isDndEnabled}
                aria-label="방해금지 시간대 설정"
                onClick={handleToggleDnd}
                className="bounce-click"
                style={{
                  width: '56px',
                  height: '32px',
                  minHeight: '44px',
                  background: 'transparent',
                  display: 'inline-flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  cursor: 'pointer',
                  padding: 0,
                }}
              >
                <span
                  style={{
                    width: '52px',
                    height: '30px',
                    borderRadius: '99px',
                    background: isDndEnabled
                      ? 'var(--accent)'
                      : 'rgba(148, 163, 184, 0.35)',
                    position: 'relative',
                    transition: 'background-color 0.2s ease',
                    display: 'inline-block',
                  }}
                >
                  <span
                    style={{
                      width: '22px',
                      height: '22px',
                      borderRadius: '50%',
                      background: '#ffffff',
                      boxShadow: '0 1px 4px rgba(15, 23, 42, 0.2)',
                      position: 'absolute',
                      top: '4px',
                      left: isDndEnabled ? '26px' : '4px',
                      transition: 'left 0.2s ease',
                    }}
                  />
                </span>
              </button>
            </div>

            {isDndEnabled && (
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  flexWrap: 'wrap',
                  gap: '10px',
                  padding: '16px',
                  borderRadius: '12px',
                  background: 'var(--surface-subtle)',
                  border: '1px solid var(--card-border)',
                }}
              >
                <span style={{ fontSize: '0.88rem', fontWeight: 700, color: 'var(--text-sub)' }}>
                  매일
                </span>
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
                    minHeight: '44px',
                    background: 'var(--card-bg)',
                    border: '1px solid var(--card-border)',
                    borderRadius: '10px',
                    padding: '8px 12px',
                    color: 'var(--text-main)',
                    fontWeight: 700,
                    outline: 'none',
                  }}
                />
                <span style={{ fontSize: '0.88rem', color: 'var(--text-sub)', fontWeight: 700 }}>
                  부터
                </span>
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
                    minHeight: '44px',
                    background: 'var(--card-bg)',
                    border: '1px solid var(--card-border)',
                    borderRadius: '10px',
                    padding: '8px 12px',
                    color: 'var(--text-main)',
                    fontWeight: 700,
                    outline: 'none',
                  }}
                />
                <span style={{ fontSize: '0.88rem', color: 'var(--text-sub)', fontWeight: 700 }}>
                  까지
                </span>
              </div>
            )}
          </section>
        </main>
      </div>
    </div>
  );
}
