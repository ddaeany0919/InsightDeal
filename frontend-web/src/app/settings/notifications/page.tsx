'use client';

import React, { useState, useEffect, useCallback } from 'react';
import Link from 'next/link';
import {
  API_BASE_URL,
  ensureWebDeviceAuth,
  authFetch,
  fetchVapidPublicKey,
  urlBase64ToUint8Array,
  getWebPushSupportStatus,
  registerOrReusePushServiceWorker,
  buildExternalBrowserIntentUrl,
  isIosUserAgent,
  type WebPushSupportStatus,
} from '../../lib/utils';
import {
  logTossScreen,
  logTossClick,
  maybeRequestTossReview,
  copyTextSafe,
} from '../../lib/tossBridge';

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

const STANDARD_DND_COPY =
  '설정한 시간에는 푸시 알림을 보내지 않아요 (놓친 알림은 알림함에서 확인 가능)';

const STANDARD_DND_DIGEST_COPY =
  '방해금지 해제 시각(기본 아침 08:00)에 "밤사이 놓친 핫딜 N건" 요약 푸시를 1회 보내드려요.';

export default function NotificationSettingsPage() {
  const [supportStatus, setSupportStatus] =
    useState<WebPushSupportStatus>('unsupported');
  const [permission, setPermission] =
    useState<NotificationPermission>('default');
  const [isPushEnabled, setIsPushEnabled] = useState(false);
  const [keywords, setKeywords] = useState<Keyword[]>([]);
  const [keywordInput, setKeywordInput] = useState('');
  const [isDndEnabled, setIsDndEnabled] = useState(false);
  const [dndStart, setDndStart] = useState('21:00');
  const [dndEnd, setDndEnd] = useState('08:00');
  const [isLoading, setIsLoading] = useState(false);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const [liveAnnouncement, setLiveAnnouncement] = useState('');
  const [safariCopyNotice, setSafariCopyNotice] = useState<{
    ok: boolean;
    text: string;
  } | null>(null);

  const isSupported = supportStatus === 'supported';

  const checkSubscription = useCallback(async () => {
    try {
      const registration = await navigator.serviceWorker.ready;
      const subscription = await registration.pushManager.getSubscription();
      setIsPushEnabled(!!subscription);
    } catch {
      setIsPushEnabled(false);
    }
  }, []);

  const fetchSettings = useCallback(async () => {
    setIsLoading(true);
    try {
      await ensureWebDeviceAuth();
      const response = await authFetch(
        (latestAuth) =>
          `/api/push/keywords?device_uuid=${encodeURIComponent(latestAuth.deviceUuid)}`
      );
      if (response.ok) {
        const data = await response.json();
        const detailed: Keyword[] = Array.isArray(data.detailed_keywords)
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
        if (typeof window !== 'undefined') {
          localStorage.setItem(
            'insightdeal_web_keywords',
            JSON.stringify(detailed)
          );
        }
      } else {
        setErrorMsg(
          '알림 설정을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.'
        );
        const localSaved =
          typeof window !== 'undefined'
            ? localStorage.getItem('insightdeal_web_keywords')
            : null;
        if (localSaved) {
          try {
            const parsed = JSON.parse(localSaved);
            setKeywords(Array.isArray(parsed) ? parsed : []);
          } catch {
            setKeywords([]);
          }
        }
      }
    } catch {
      setErrorMsg('알림 설정을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.');
      const localSaved =
        typeof window !== 'undefined'
          ? localStorage.getItem('insightdeal_web_keywords')
          : null;
      if (localSaved) {
        try {
          const parsed = JSON.parse(localSaved);
          setKeywords(Array.isArray(parsed) ? parsed : []);
        } catch {
          setKeywords([]);
        }
      }
    } finally {
      setIsLoading(false);
    }
  }, []);

  useEffect(() => {
    logTossScreen('keyword_settings_view');
    const status = getWebPushSupportStatus();
    setSupportStatus(status);
    if (status === 'supported' && typeof Notification !== 'undefined') {
      setPermission(Notification.permission);
      void checkSubscription();
    }
    void fetchSettings();
  }, [checkSubscription, fetchSettings]);

  const registerServiceWorkerAndSubscribe = async () => {
    setErrorMsg(null);
    let newlyCreatedSub: PushSubscription | null = null;
    try {
      await ensureWebDeviceAuth();
      const registration = await registerOrReusePushServiceWorker();
      await navigator.serviceWorker.ready;

      let subscription = await registration.pushManager.getSubscription();
      if (!subscription) {
        let vapidKey = '';
        try {
          vapidKey = await fetchVapidPublicKey();
        } catch {
          setErrorMsg(
            '푸시 알림 서버 설정이 아직 준비되지 않았어요. 잠시 후 다시 시도해 주세요.'
          );
          return;
        }

        const appServerKey = urlBase64ToUint8Array(vapidKey);
        subscription = await registration.pushManager.subscribe({
          userVisibleOnly: true,
          applicationServerKey: appServerKey,
        });
        newlyCreatedSub = subscription;
      }

      const subJson = subscription.toJSON();
      const pushRegisterPath = API_BASE_URL
        ? '/api/push/register-web'
        : '/api/push-register';
      const regRes = await authFetch((latestAuth) => ({
        pathOrUrl: pushRegisterPath,
        init: {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            endpoint: subJson.endpoint || subscription.endpoint,
            keys: subJson.keys || {},
            device_uuid: latestAuth.deviceUuid,
          }),
        },
      }));

      if (!regRes.ok) {
        if (newlyCreatedSub) {
          await newlyCreatedSub.unsubscribe().catch(() => null);
        }
        setErrorMsg(
          '푸시 알림 등록에 실패했어요. 잠시 후 다시 시도해 주세요.'
        );
        return;
      }

      setIsPushEnabled(true);
    } catch {
      if (newlyCreatedSub) {
        await newlyCreatedSub.unsubscribe().catch(() => null);
      }
      setErrorMsg('푸시 알림을 연결하지 못했어요. 잠시 후 다시 시도해 주세요.');
    }
  };

  const requestPermission = async () => {
    if (!isSupported) return;
    setErrorMsg(null);
    setIsLoading(true);
    try {
      const result = await Notification.requestPermission();
      setPermission(result);
      if (result === 'granted') {
        await registerServiceWorkerAndSubscribe();
      } else if (result === 'denied') {
        setErrorMsg(
          '브라우저 알림 권한이 거부되어 있어요. 주소창 왼쪽 자물쇠/설정 아이콘에서 허용해 주세요.'
        );
      }
    } catch {
      setErrorMsg('알림 권한을 확인하는 중 문제가 생겼어요.');
    } finally {
      setIsLoading(false);
    }
  };

  const unsubscribePush = async () => {
    setErrorMsg(null);
    setIsLoading(true);
    try {
      await ensureWebDeviceAuth();
      const registration = await navigator.serviceWorker.ready;
      const subscription = await registration.pushManager.getSubscription();
      if (subscription) {
        const res = await authFetch((latestAuth) => ({
          pathOrUrl: API_BASE_URL
            ? `/api/push/register-web?endpoint=${encodeURIComponent(
                subscription.endpoint
              )}&device_uuid=${encodeURIComponent(latestAuth.deviceUuid)}`
            : `/api/push-register?endpoint=${encodeURIComponent(
                subscription.endpoint
              )}&device_uuid=${encodeURIComponent(latestAuth.deviceUuid)}`,
          init: {
            method: 'DELETE',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
              endpoint: subscription.endpoint,
              device_uuid: latestAuth.deviceUuid,
            }),
          },
        }));
        if (!res.ok) {
          setErrorMsg(
            '푸시 알림 해제 요청에 실패했어요. 잠시 후 다시 시도해 주세요.'
          );
          return;
        }
        await subscription.unsubscribe();
        setIsPushEnabled(false);
      }
    } catch {
      setErrorMsg(
        '푸시 알림을 끄는 중 오류가 발생했어요. 잠시 후 다시 시도해 주세요.'
      );
    } finally {
      setIsLoading(false);
    }
  };

  const handleAddKeyword = async (e: React.FormEvent) => {
    e.preventDefault();
    const newKeywordStr = keywordInput.trim();
    if (!newKeywordStr) return;

    if (keywords.some((k) => k.keyword === newKeywordStr)) {
      setKeywordInput('');
      return;
    }

    setErrorMsg(null);
    setIsLoading(true);
    try {
      await ensureWebDeviceAuth();
      const res = await authFetch((latestAuth) => ({
        pathOrUrl: '/api/push/keywords',
        init: {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            device_uuid: latestAuth.deviceUuid,
            keyword: newKeywordStr,
          }),
        },
      }));

      if (!res.ok) {
        setErrorMsg(
          '키워드를 등록하지 못했어요. 잠시 후 다시 시도해 주세요.'
        );
        return;
      }

      const created = (await res.json().catch(() => null)) as {
        id?: string | number;
        keyword?: string;
        is_active?: boolean;
      } | null;

      const newId = String(created?.id ?? Date.now());
      const updated = [
        ...keywords,
        {
          id: newId,
          keyword: created?.keyword || newKeywordStr,
          is_active: created?.is_active !== false,
        },
      ];
      setKeywords(updated);
      if (typeof window !== 'undefined') {
        localStorage.setItem(
          'insightdeal_web_keywords',
          JSON.stringify(updated)
        );
      }
      logTossClick('register_keyword_alert', {
        button_name: 'settings_keyword_submit',
      });
      maybeRequestTossReview();
      setLiveAnnouncement(`'${newKeywordStr}' 알림 키워드를 등록했어요.`);
      setKeywordInput('');
    } catch {
      setErrorMsg('네트워크 오류로 키워드를 등록하지 못했어요.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleToggleKeyword = async (keywordStr: string) => {
    setErrorMsg(null);
    const prevSnapshot = keywords;
    try {
      await ensureWebDeviceAuth();
      const res = await authFetch((latestAuth) => ({
        pathOrUrl: '/api/push/keywords/toggle',
        init: {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            device_uuid: latestAuth.deviceUuid,
            keyword: keywordStr,
          }),
        },
      }));
      if (!res.ok) {
        setKeywords(prevSnapshot);
        setErrorMsg(
          '키워드 상태를 변경하지 못했어요. 잠시 후 다시 시도해 주세요.'
        );
        return;
      }
      const data = await res.json();
      const nextActive = Boolean(data.is_active);
      const updated = keywords.map((k) =>
        k.keyword === keywordStr ? { ...k, is_active: nextActive } : k
      );
      setKeywords(updated);
      if (typeof window !== 'undefined') {
        localStorage.setItem(
          'insightdeal_web_keywords',
          JSON.stringify(updated)
        );
      }
      setLiveAnnouncement(
        nextActive
          ? `'${keywordStr}' 키워드 알림을 다시 켰어요.`
          : `'${keywordStr}' 키워드 알림을 일시정지했어요.`
      );
    } catch {
      setKeywords(prevSnapshot);
      setErrorMsg('네트워크 오류로 키워드 상태를 변경하지 못했어요.');
    }
  };

  const handleDeleteKeyword = async (keywordStr: string) => {
    setErrorMsg(null);
    setIsLoading(true);
    try {
      await ensureWebDeviceAuth();
      const res = await authFetch((latestAuth) => ({
        pathOrUrl: '/api/push/keywords',
        init: {
          method: 'DELETE',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            device_uuid: latestAuth.deviceUuid,
            keyword: keywordStr,
          }),
        },
      }));

      if (!res.ok) {
        setErrorMsg(
          '키워드를 삭제하지 못했어요. 잠시 후 다시 시도해 주세요.'
        );
        return;
      }

      const updated = keywords.filter((k) => k.keyword !== keywordStr);
      setKeywords(updated);
      if (typeof window !== 'undefined') {
        localStorage.setItem(
          'insightdeal_web_keywords',
          JSON.stringify(updated)
        );
      }
      setLiveAnnouncement(`'${keywordStr}' 키워드를 삭제했어요.`);
    } catch {
      setErrorMsg('네트워크 오류로 키워드를 삭제하지 못했어요.');
    } finally {
      setIsLoading(false);
    }
  };

  const syncDndToBackend = async (
    nextEnabled: boolean,
    startStr: string,
    endStr: string,
    prevSnapshot: { enabled: boolean; start: string; end: string }
  ) => {
    setErrorMsg(null);
    try {
      await ensureWebDeviceAuth();
      const res = await authFetch((latestAuth) => ({
        pathOrUrl: '/api/push/dnd',
        init: {
          method: 'PATCH',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            device_uuid: latestAuth.deviceUuid,
            dnd_enabled: nextEnabled,
            dnd_start_time: startStr,
            dnd_end_time: endStr,
          }),
        },
      }));
      if (!res.ok) {
        setIsDndEnabled(prevSnapshot.enabled);
        setDndStart(prevSnapshot.start);
        setDndEnd(prevSnapshot.end);
        setErrorMsg(
          '방해금지 시간 설정을 저장하지 못했어요. 잠시 후 다시 시도해 주세요.'
        );
      } else {
        setLiveAnnouncement(
          nextEnabled
            ? `방해금지 시간대(${startStr}~${endStr})를 켰어요.`
            : '방해금지 시간대를 껐어요.'
        );
      }
    } catch {
      setIsDndEnabled(prevSnapshot.enabled);
      setDndStart(prevSnapshot.start);
      setDndEnd(prevSnapshot.end);
      setErrorMsg(
        '방해금지 시간 설정을 저장하지 못했어요. 잠시 후 다시 시도해 주세요.'
      );
    }
  };

  const handleToggleDnd = async () => {
    const prevSnapshot = {
      enabled: isDndEnabled,
      start: dndStart,
      end: dndEnd,
    };
    const nextVal = !isDndEnabled;
    setIsDndEnabled(nextVal);
    await syncDndToBackend(nextVal, dndStart, dndEnd, prevSnapshot);
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
            role="alert"
            style={{
              marginBottom: '20px',
              background: 'var(--danger-bg)',
              border: '1px solid var(--danger-border)',
              padding: '10px 12px 10px 16px',
              borderRadius: '14px',
              color: 'var(--danger-text)',
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
                color: 'var(--danger-text)',
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

        <div role="status" aria-live="polite" className="sr-only">
          {liveAnnouncement}
        </div>

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
                    background: 'var(--danger-bg)',
                    color: 'var(--danger-text)',
                    padding: '10px 20px',
                    borderRadius: '12px',
                    fontWeight: 700,
                    border: '1px solid var(--danger-border)',
                    cursor: 'pointer',
                  }}
                >
                  알림 끄기
                </button>
              ) : (
                <button
                  type="button"
                  onClick={requestPermission}
                  disabled={isLoading || !isSupported || permission === 'denied'}
                  className="btn-primary"
                  style={
                    !isSupported || permission === 'denied'
                      ? { opacity: 0.55, cursor: 'not-allowed' }
                      : undefined
                  }
                >
                  {!isSupported
                    ? '현재 브라우저 미지원'
                    : permission === 'denied'
                      ? '브라우저에서 차단됨'
                      : '알림 켜기'}
                </button>
              )}
            </div>

            {supportStatus === 'in_app_webview' && (
              <div
                role="note"
                style={{
                  background: 'var(--warning-bg)',
                  border: '1px solid var(--warning-border)',
                  padding: '12px 14px',
                  borderRadius: '12px',
                  fontSize: '0.86rem',
                  color: 'var(--warning-text)',
                  fontWeight: 600,
                  lineHeight: 1.5,
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '10px',
                  alignItems: 'flex-start',
                }}
              >
                <span>
                  카카오톡·인스타그램·네이버 등의 인앱 브라우저에서는 웹 푸시 알림이 지원되지 않아요.
                  Chrome 또는 Safari(홈 화면에 추가)로 열면 실시간 푸시를 받을 수 있으며, 등록한 키워드 알림 내역은 인앱 브라우저에서도 알림함에서 확인할 수 있어요.
                </span>
                {(() => {
                  const intentUrl = !isIosUserAgent()
                    ? buildExternalBrowserIntentUrl()
                    : null;
                  if (intentUrl) {
                    return (
                      <a
                        href={intentUrl}
                        className="btn-primary"
                        style={{ minHeight: '44px', borderRadius: '10px', fontSize: '0.82rem' }}
                      >
                        외부 브라우저(Chrome)로 열기
                      </a>
                    );
                  }
                  return (
                    <>
                      <button
                        type="button"
                        className="btn-primary"
                        style={{ minHeight: '44px', borderRadius: '10px', fontSize: '0.82rem' }}
                        onClick={async () => {
                          if (typeof window === 'undefined') return;
                          const ok = await copyTextSafe(window.location.href);
                          const message = ok
                            ? '현재 페이지 주소를 복사했어요. Safari 주소창에 붙여넣어 열어주세요.'
                            : '주소를 복사하지 못했어요. 브라우저 메뉴에서 Safari로 열기를 선택해 주세요.';
                          setLiveAnnouncement(message);
                          setSafariCopyNotice({ ok, text: message });
                        }}
                      >
                        Safari로 열기용 주소 복사
                      </button>
                      {safariCopyNotice && (
                        <div
                          role="status"
                          style={{
                            width: '100%',
                            padding: '10px 12px',
                            borderRadius: '10px',
                            fontSize: '0.82rem',
                            fontWeight: 700,
                            background: safariCopyNotice.ok
                              ? 'var(--accent-soft-bg)'
                              : 'var(--danger-bg)',
                            border: safariCopyNotice.ok
                              ? '1px solid var(--accent-soft-border)'
                              : '1px solid var(--danger-border)',
                            color: safariCopyNotice.ok
                              ? 'var(--accent)'
                              : 'var(--danger-text)',
                          }}
                        >
                          {safariCopyNotice.text}
                        </div>
                      )}
                    </>
                  );
                })()}
              </div>
            )}

            {supportStatus === 'toss_miniapp' && (
              <div
                role="note"
                style={{
                  background: 'var(--accent-soft-bg)',
                  border: '1px solid var(--accent-soft-border)',
                  padding: '12px 14px',
                  borderRadius: '12px',
                  fontSize: '0.86rem',
                  color: 'var(--accent)',
                  fontWeight: 600,
                  lineHeight: 1.5,
                }}
              >
                토스 미니앱 환경에서는 브라우저 웹 푸시 대신 아래 키워드 등록 및 상단 알림함 기능을 이용해 주세요.
              </div>
            )}

            {supportStatus === 'unsupported' && (
              <div
                role="note"
                style={{
                  background: 'var(--warning-bg)',
                  border: '1px solid var(--warning-border)',
                  padding: '12px 14px',
                  borderRadius: '12px',
                  fontSize: '0.86rem',
                  color: 'var(--warning-text)',
                  fontWeight: 600,
                  lineHeight: 1.5,
                }}
              >
                현재 브라우저에서는 웹 푸시 알림을 지원하지 않아요.
                iOS Safari 사용 시 하단 공유 버튼 → &ldquo;홈 화면에 추가&rdquo; 후 실행하면 푸시 알림을 받을 수 있어요.
              </div>
            )}

            {isSupported && permission === 'denied' && (
              <div
                role="note"
                style={{
                  background: 'var(--danger-bg)',
                  border: '1px solid var(--danger-border)',
                  padding: '12px 14px',
                  borderRadius: '12px',
                  fontSize: '0.86rem',
                  color: 'var(--danger-text)',
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
                      minHeight: '44px',
                      background: 'var(--surface-subtle)',
                      border: '1px solid var(--card-border)',
                      borderRadius: '99px',
                      padding: '8px 14px',
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
                            ? '1px solid var(--accent-soft-border)'
                            : '1px solid var(--card-border)',
                          background: isActive
                            ? 'var(--accent-soft-bg)'
                            : 'var(--surface-subtle)',
                          color: isActive ? 'var(--accent)' : 'var(--text-muted)',
                        }}
                      >
                        <button
                          type="button"
                          aria-pressed={isActive}
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
                            width: '44px',
                            height: '44px',
                            minWidth: '44px',
                            minHeight: '44px',
                            borderRadius: '50%',
                            background: 'transparent',
                            border: 'none',
                            color: 'var(--text-muted)',
                            fontSize: '13px',
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
                  {STANDARD_DND_COPY}
                </p>
                <p style={{ color: 'var(--text-muted)', fontSize: '0.82rem', marginTop: '4px' }}>
                  {STANDARD_DND_DIGEST_COPY}
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
                  height: '44px',
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
                      : 'var(--switch-off-bg)',
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
                    const prevSnapshot = {
                      enabled: isDndEnabled,
                      start: dndStart,
                      end: dndEnd,
                    };
                    const nextStart = e.target.value;
                    setDndStart(nextStart);
                    void syncDndToBackend(
                      isDndEnabled,
                      nextStart,
                      dndEnd,
                      prevSnapshot
                    );
                  }}
                  style={{
                    minHeight: '44px',
                    background: 'var(--card-bg)',
                    border: '1px solid var(--card-border)',
                    borderRadius: '10px',
                    padding: '8px 12px',
                    color: 'var(--text-main)',
                    fontWeight: 700,
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
                    const prevSnapshot = {
                      enabled: isDndEnabled,
                      start: dndStart,
                      end: dndEnd,
                    };
                    const nextEnd = e.target.value;
                    setDndEnd(nextEnd);
                    void syncDndToBackend(
                      isDndEnabled,
                      dndStart,
                      nextEnd,
                      prevSnapshot
                    );
                  }}
                  style={{
                    minHeight: '44px',
                    background: 'var(--card-bg)',
                    border: '1px solid var(--card-border)',
                    borderRadius: '10px',
                    padding: '8px 12px',
                    color: 'var(--text-main)',
                    fontWeight: 700,
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
