'use client';

import React, { useState, useEffect, useCallback, useSyncExternalStore } from 'react';
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
  appendTossDebugQuery,
  isTossMiniAppEnv,
  logTossScreen,
  logTossClick,
  maybeRequestTossReview,
  copyTextSafe,
} from '../../lib/tossBridge';

const subscribeNoop = () => () => {};

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

const WEEKDAY_KEYS = ['mon', 'tue', 'wed', 'thu', 'fri'] as const;
const WEEKEND_KEYS = ['sat', 'sun'] as const;

interface DayDndCfg {
  enabled: boolean;
  start: string;
  end: string;
}

function parseWeekdayWeekendDnd(
  rawJson: unknown,
  fallbackEnabled: boolean,
  fallbackStart: string,
  fallbackEnd: string
): {
  weekday: DayDndCfg;
  weekend: DayDndCfg;
} {
  const defaultCfg: DayDndCfg = {
    enabled: fallbackEnabled,
    start: fallbackStart,
    end: fallbackEnd,
  };
  if (!rawJson) {
    return { weekday: { ...defaultCfg }, weekend: { ...defaultCfg } };
  }
  try {
    const parsed =
      typeof rawJson === 'string'
        ? (JSON.parse(rawJson) as Record<string, Partial<DayDndCfg>>)
        : (rawJson as Record<string, Partial<DayDndCfg>>);
    const mon = parsed?.mon;
    const sat = parsed?.sat;
    return {
      weekday:
        mon && typeof mon === 'object'
          ? {
              enabled: mon.enabled !== undefined ? Boolean(mon.enabled) : fallbackEnabled,
              start: typeof mon.start === 'string' && mon.start ? mon.start : fallbackStart,
              end: typeof mon.end === 'string' && mon.end ? mon.end : fallbackEnd,
            }
          : { ...defaultCfg },
      weekend:
        sat && typeof sat === 'object'
          ? {
              enabled: sat.enabled !== undefined ? Boolean(sat.enabled) : fallbackEnabled,
              start: typeof sat.start === 'string' && sat.start ? sat.start : fallbackStart,
              end: typeof sat.end === 'string' && sat.end ? sat.end : fallbackEnd,
            }
          : { ...defaultCfg },
    };
  } catch {
    return { weekday: { ...defaultCfg }, weekend: { ...defaultCfg } };
  }
}

function serializeWeekdayWeekendDnd(
  weekday: DayDndCfg,
  weekend: DayDndCfg
): string {
  const map: Record<string, DayDndCfg> = {};
  for (const k of WEEKDAY_KEYS) {
    map[k] = { ...weekday };
  }
  for (const k of WEEKEND_KEYS) {
    map[k] = { ...weekend };
  }
  return JSON.stringify(map);
}

export default function NotificationSettingsPage() {
  const [isTossMiniApp, setIsTossMiniApp] = useState(
    () => process.env.NEXT_PUBLIC_TOSS_MINIAPP === 'true'
  );
  const homeHref = useSyncExternalStore(
    subscribeNoop,
    () => appendTossDebugQuery('/'),
    () => '/'
  );
  const [supportStatus, setSupportStatus] =
    useState<WebPushSupportStatus>('unsupported');
  const [permission, setPermission] =
    useState<NotificationPermission>('default');
  const [isPushEnabled, setIsPushEnabled] = useState(false);
  const [keywords, setKeywords] = useState<Keyword[]>([]);
  const [keywordInput, setKeywordInput] = useState('');
  const [excludeInput, setExcludeInput] = useState('');
  const [maxPriceInput, setMaxPriceInput] = useState('');
  const [shopFilterInput, setShopFilterInput] = useState('');
  const [editingKeywordOrig, setEditingKeywordOrig] = useState<string | null>(null);
  const [editingKeywordVal, setEditingKeywordVal] = useState('');
  const [isDndEnabled, setIsDndEnabled] = useState(false);
  const [dndStart, setDndStart] = useState('21:00');
  const [dndEnd, setDndEnd] = useState('08:00');
  const [useSeparateWeekendDnd, setUseSeparateWeekendDnd] = useState(false);
  const [weekendDndEnabled, setWeekendDndEnabled] = useState(true);
  const [weekendDndStart, setWeekendDndStart] = useState('22:00');
  const [weekendDndEnd, setWeekendDndEnd] = useState('09:00');
  const [testPushMsg, setTestPushMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const [isSendingTest, setIsSendingTest] = useState(false);
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
        if (data.dnd_settings_json) {
          const parsedWeek = parseWeekdayWeekendDnd(
            data.dnd_settings_json,
            Boolean(data.dnd_enabled),
            data.dnd_start_time || '21:00',
            data.dnd_end_time || '08:00'
          );
          const differs =
            parsedWeek.weekday.enabled !== parsedWeek.weekend.enabled ||
            parsedWeek.weekday.start !== parsedWeek.weekend.start ||
            parsedWeek.weekday.end !== parsedWeek.weekend.end;
          setUseSeparateWeekendDnd(differs);
          setWeekendDndEnabled(parsedWeek.weekend.enabled);
          setWeekendDndStart(parsedWeek.weekend.start);
          setWeekendDndEnd(parsedWeek.weekend.end);
          if (differs) {
            setDndStart(parsedWeek.weekday.start);
            setDndEnd(parsedWeek.weekday.end);
          }
        }
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
    // 토스 미니앱 호스트(런타임 브릿지·UA·도메인·디버그 쿼리) 여부를 클라이언트 마운트 후 안전하게 판별
    const urlParams =
      typeof window !== 'undefined'
        ? new URLSearchParams(window.location.search)
        : null;
    if (isTossMiniAppEnv() || urlParams?.get('toss') === '1') {
      setIsTossMiniApp(true);
    }
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

  const buildStructuredKeyword = (): string => {
    const base = keywordInput.trim();
    if (!base) return '';
    const tokens: string[] = [base];
    const ex = excludeInput.trim();
    if (ex) {
      ex.split(/[\s,]+/)
        .map((t) => t.trim().replace(/^-+/, ''))
        .filter(Boolean)
        .forEach((t) => tokens.push(`-${t}`));
    }
    const maxP = maxPriceInput.replace(/[^0-9]/g, '');
    if (maxP && Number(maxP) > 0 && !base.includes(':')) {
      tokens.push(`:${maxP}`);
    }
    const sh = shopFilterInput.trim().replace(/^@+/, '');
    if (sh && !base.includes('@')) {
      tokens.push(`@${sh}`);
    }
    return tokens.join(' ').trim();
  };

  const handleAddKeyword = async (e: React.FormEvent) => {
    e.preventDefault();
    const newKeywordStr = buildStructuredKeyword();
    if (!newKeywordStr) return;

    if (keywords.some((k) => k.keyword === newKeywordStr)) {
      setKeywordInput('');
      setExcludeInput('');
      setMaxPriceInput('');
      setShopFilterInput('');
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
      setExcludeInput('');
      setMaxPriceInput('');
      setShopFilterInput('');
    } catch {
      setErrorMsg('네트워크 오류로 키워드를 등록하지 못했어요.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleSaveEditKeyword = async (oldKw: string) => {
    const nextKw = editingKeywordVal.trim();
    if (!nextKw || nextKw === oldKw) {
      setEditingKeywordOrig(null);
      setEditingKeywordVal('');
      return;
    }
    setErrorMsg(null);
    setIsLoading(true);
    try {
      await ensureWebDeviceAuth();
      const addRes = await authFetch((latestAuth) => ({
        pathOrUrl: '/api/push/keywords',
        init: {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ device_uuid: latestAuth.deviceUuid, keyword: nextKw }),
        },
      }));
      if (!addRes.ok) {
        setErrorMsg('키워드를 수정하지 못했어요. 잠시 후 다시 시도해 주세요.');
        return;
      }
      const created = (await addRes.json().catch(() => null)) as { id?: string | number; keyword?: string } | null;
      await authFetch((latestAuth) => ({
        pathOrUrl: '/api/push/keywords',
        init: {
          method: 'DELETE',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ device_uuid: latestAuth.deviceUuid, keyword: oldKw }),
        },
      }));
      const updated = keywords
        .filter((k) => k.keyword !== oldKw)
        .concat({ id: String(created?.id ?? Date.now()), keyword: created?.keyword || nextKw, is_active: true });
      setKeywords(updated);
      if (typeof window !== 'undefined') {
        localStorage.setItem('insightdeal_web_keywords', JSON.stringify(updated));
      }
      setEditingKeywordOrig(null);
      setEditingKeywordVal('');
      setLiveAnnouncement(`'${oldKw}' 키워드를 '${nextKw}'로 수정했어요.`);
    } catch {
      setErrorMsg('네트워크 오류로 키워드를 수정하지 못했어요.');
    } finally {
      setIsLoading(false);
    }
  };

  const handleSendTestPush = async () => {
    if (isSendingTest) return;
    setIsSendingTest(true);
    setTestPushMsg(null);
    try {
      await ensureWebDeviceAuth();
      const res = await authFetch((latestAuth) => ({
        pathOrUrl: '/api/push/test',
        init: {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ device_uuid: latestAuth.deviceUuid }),
        },
      }));
      const data = (await res.json().catch(() => null)) as { message?: string; detail?: string; delivered?: boolean } | null;
      if (res.status === 429) {
        setTestPushMsg({ ok: false, text: '잠시 후 다시 테스트해 주세요. (요청 제한)' });
        return;
      }
      if (!res.ok) {
        setTestPushMsg({ ok: false, text: data?.detail || '테스트 알림 전송에 실패했어요.' });
        return;
      }
      setTestPushMsg({
        ok: true,
        text: data?.message || '테스트 알림을 보냈어요. 알림함과 푸시를 확인해 주세요.',
      });
    } catch {
      setTestPushMsg({ ok: false, text: '네트워크 오류로 테스트 알림을 보내지 못했어요.' });
    } finally {
      setIsSendingTest(false);
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
    prevSnapshot: { enabled: boolean; start: string; end: string },
    extra?: {
      separateWeekend?: boolean;
      weekendEnabled?: boolean;
      weekendStart?: string;
      weekendEnd?: string;
    }
  ) => {
    setErrorMsg(null);
    const sep = extra?.separateWeekend ?? useSeparateWeekendDnd;
    const wkEnabled = extra?.weekendEnabled ?? weekendDndEnabled;
    const wkStart = extra?.weekendStart ?? weekendDndStart;
    const wkEnd = extra?.weekendEnd ?? weekendDndEnd;
    const dndJsonPayload = sep
      ? serializeWeekdayWeekendDnd(
          { enabled: nextEnabled, start: startStr, end: endStr },
          { enabled: nextEnabled && wkEnabled, start: wkStart, end: wkEnd }
        )
      : serializeWeekdayWeekendDnd(
          { enabled: nextEnabled, start: startStr, end: endStr },
          { enabled: nextEnabled, start: startStr, end: endStr }
        );

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
            dnd_settings_json: dndJsonPayload,
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
          {!isTossMiniApp && (
            <div style={{ display: 'flex', justifyContent: 'flex-start', marginBottom: '16px' }}>
              {/* 정적 export 환경에서 RSC prefetch 404 방지 */}
              <Link
                href={homeHref}
                prefetch={false}
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
          )}
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
                <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', alignItems: 'center' }}>
                  <button
                    type="button"
                    onClick={handleSendTestPush}
                    disabled={isLoading || isSendingTest}
                    className="bounce-click"
                    style={{
                      minHeight: '44px',
                      background: 'var(--bg-subtle, rgba(16, 185, 129, 0.12))',
                      color: 'var(--accent-primary, #059669)',
                      padding: '10px 16px',
                      borderRadius: '12px',
                      fontWeight: 700,
                      border: '1px solid rgba(16, 185, 129, 0.35)',
                      cursor: isSendingTest ? 'not-allowed' : 'pointer',
                      opacity: isSendingTest ? 0.65 : 1,
                    }}
                  >
                    {isSendingTest ? '전송 중...' : '테스트 알림 보내기'}
                  </button>
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
                </div>
              ) : (
                <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', alignItems: 'center' }}>
                  <button
                    type="button"
                    onClick={handleSendTestPush}
                    disabled={isLoading || isSendingTest}
                    className="bounce-click"
                    style={{
                      minHeight: '44px',
                      background: 'var(--bg-subtle, rgba(16, 185, 129, 0.12))',
                      color: 'var(--accent-primary, #059669)',
                      padding: '10px 16px',
                      borderRadius: '12px',
                      fontWeight: 700,
                      border: '1px solid rgba(16, 185, 129, 0.35)',
                      cursor: isSendingTest ? 'not-allowed' : 'pointer',
                      opacity: isSendingTest ? 0.65 : 1,
                    }}
                  >
                    {isSendingTest ? '전송 중...' : '테스트 알림 보내기'}
                  </button>
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
                </div>
              )}
            </div>

            {testPushMsg && (
              <div
                role="status"
                style={{
                  background: testPushMsg.ok ? 'var(--accent-soft-bg)' : 'var(--danger-bg)',
                  border: testPushMsg.ok
                    ? '1px solid var(--accent-soft-border)'
                    : '1px solid var(--danger-border)',
                  color: testPushMsg.ok ? 'var(--accent)' : 'var(--danger-text)',
                  padding: '10px 14px',
                  borderRadius: '12px',
                  fontSize: '0.86rem',
                  fontWeight: 700,
                }}
              >
                {testPushMsg.text}
              </div>
            )}

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

            <form onSubmit={handleAddKeyword} style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              <div style={{ display: 'flex', gap: '10px' }}>
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
              </div>
              <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap' }}>
                <input
                  type="text"
                  value={excludeInput}
                  onChange={(e) => setExcludeInput(e.target.value)}
                  placeholder="제외어 (예: 케이스 필름)"
                  aria-label="제외 키워드 입력"
                  style={{
                    flex: '1 1 140px',
                    minWidth: 0,
                    minHeight: '40px',
                    background: 'var(--surface-subtle)',
                    border: '1px solid var(--card-border)',
                    borderRadius: '10px',
                    padding: '8px 12px',
                    color: 'var(--text-main)',
                    fontSize: '0.84rem',
                  }}
                />
                <input
                  type="number"
                  min={0}
                  step={1000}
                  value={maxPriceInput}
                  onChange={(e) => setMaxPriceInput(e.target.value)}
                  placeholder="상한 금액 (원)"
                  aria-label="상한 가격 입력"
                  style={{
                    flex: '1 1 120px',
                    minWidth: 0,
                    minHeight: '40px',
                    background: 'var(--surface-subtle)',
                    border: '1px solid var(--card-border)',
                    borderRadius: '10px',
                    padding: '8px 12px',
                    color: 'var(--text-main)',
                    fontSize: '0.84rem',
                  }}
                />
                <input
                  type="text"
                  value={shopFilterInput}
                  onChange={(e) => setShopFilterInput(e.target.value)}
                  placeholder="쇼핑몰 (예: 쿠팡)"
                  aria-label="쇼핑몰 필터 입력"
                  style={{
                    flex: '1 1 120px',
                    minWidth: 0,
                    minHeight: '40px',
                    background: 'var(--surface-subtle)',
                    border: '1px solid var(--card-border)',
                    borderRadius: '10px',
                    padding: '8px 12px',
                    color: 'var(--text-main)',
                    fontSize: '0.84rem',
                  }}
                />
              </div>
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
              앞에는 <code>-</code>를, 상한 금액 앞에는 <code>:</code>를, 쇼핑몰 지정은{' '}
              <code>@</code>를 붙여 보세요 (예: <code>아이폰 -케이스</code>,{' '}
              <code>삼다수 :12000 @쿠팡</code>).
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
                  키워드를 누르면 일시정지, 수정 버튼으로 조건을 바로 고칠 수 있어요
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
                    const isEditing = editingKeywordOrig === kw.keyword;
                    if (isEditing) {
                      return (
                        <div
                          key={kw.id}
                          className="glass-panel"
                          style={{
                            display: 'inline-flex',
                            alignItems: 'center',
                            gap: '6px',
                            minHeight: '44px',
                            padding: '4px 8px',
                            borderRadius: '14px',
                            border: '1px solid var(--accent-soft-border)',
                            background: 'var(--surface-subtle)',
                          }}
                        >
                          <input
                            type="text"
                            value={editingKeywordVal}
                            onChange={(e) => setEditingKeywordVal(e.target.value)}
                            onKeyDown={(e) => {
                              if (e.key === 'Enter') {
                                e.preventDefault();
                                void handleSaveEditKeyword(kw.keyword);
                              } else if (e.key === 'Escape') {
                                setEditingKeywordOrig(null);
                                setEditingKeywordVal('');
                              }
                            }}
                            aria-label={`${kw.keyword} 키워드 수정 입력`}
                            style={{
                              minHeight: '34px',
                              padding: '4px 8px',
                              borderRadius: '8px',
                              border: '1px solid var(--card-border)',
                              background: 'var(--card-bg)',
                              color: 'var(--text-main)',
                              fontSize: '0.84rem',
                              fontWeight: 600,
                            }}
                          />
                          <button
                            type="button"
                            onClick={() => void handleSaveEditKeyword(kw.keyword)}
                            disabled={isLoading}
                            style={{
                              minHeight: '36px',
                              padding: '4px 10px',
                              borderRadius: '8px',
                              background: 'var(--accent)',
                              color: '#ffffff',
                              fontSize: '0.78rem',
                              fontWeight: 700,
                              border: 'none',
                              cursor: 'pointer',
                            }}
                          >
                            저장
                          </button>
                          <button
                            type="button"
                            onClick={() => {
                              setEditingKeywordOrig(null);
                              setEditingKeywordVal('');
                            }}
                            style={{
                              minHeight: '36px',
                              padding: '4px 8px',
                              borderRadius: '8px',
                              background: 'transparent',
                              color: 'var(--text-sub)',
                              fontSize: '0.78rem',
                              fontWeight: 600,
                              border: '1px solid var(--card-border)',
                              cursor: 'pointer',
                            }}
                          >
                            취소
                          </button>
                        </div>
                      );
                    }
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
                          aria-label={`${kw.keyword} 키워드 수정`}
                          title="키워드 수정"
                          onClick={() => {
                            setEditingKeywordOrig(kw.keyword);
                            setEditingKeywordVal(kw.keyword);
                          }}
                          style={{
                            display: 'inline-flex',
                            alignItems: 'center',
                            justifyContent: 'center',
                            minWidth: '34px',
                            height: '38px',
                            borderRadius: '50%',
                            background: 'transparent',
                            border: 'none',
                            color: 'var(--text-sub)',
                            fontSize: '12px',
                            fontWeight: 700,
                            cursor: 'pointer',
                          }}
                        >
                          수정
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
                  flexDirection: 'column',
                  gap: '12px',
                  padding: '16px',
                  borderRadius: '12px',
                  background: 'var(--surface-subtle)',
                  border: '1px solid var(--card-border)',
                }}
              >
                <div
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    flexWrap: 'wrap',
                    gap: '10px',
                  }}
                >
                  <span style={{ fontSize: '0.88rem', fontWeight: 700, color: 'var(--text-sub)' }}>
                    {useSeparateWeekendDnd ? '평일 (월~금)' : '매일'}
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

                <div
                  style={{
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    gap: '10px',
                    borderTop: '1px solid var(--card-border)',
                    paddingTop: '10px',
                  }}
                >
                  <label
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '8px',
                      fontSize: '0.84rem',
                      fontWeight: 600,
                      color: 'var(--text-main)',
                      cursor: 'pointer',
                    }}
                  >
                    <input
                      type="checkbox"
                      checked={useSeparateWeekendDnd}
                      onChange={(e) => {
                        const nextSep = e.target.checked;
                        setUseSeparateWeekendDnd(nextSep);
                        void syncDndToBackend(
                          isDndEnabled,
                          dndStart,
                          dndEnd,
                          { enabled: isDndEnabled, start: dndStart, end: dndEnd },
                          { separateWeekend: nextSep }
                        );
                      }}
                    />
                    <span>평일·주말 다르게 설정</span>
                  </label>

                  {useSeparateWeekendDnd && (
                    <div
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        flexWrap: 'wrap',
                        gap: '10px',
                      }}
                    >
                      <label
                        style={{
                          display: 'inline-flex',
                          alignItems: 'center',
                          gap: '6px',
                          fontSize: '0.84rem',
                          fontWeight: 700,
                          color: 'var(--text-sub)',
                          cursor: 'pointer',
                        }}
                      >
                        <input
                          type="checkbox"
                          checked={weekendDndEnabled}
                          onChange={(e) => {
                            const nextWk = e.target.checked;
                            setWeekendDndEnabled(nextWk);
                            void syncDndToBackend(
                              isDndEnabled,
                              dndStart,
                              dndEnd,
                              { enabled: isDndEnabled, start: dndStart, end: dndEnd },
                              { separateWeekend: true, weekendEnabled: nextWk }
                            );
                          }}
                        />
                        <span>주말 (토·일)</span>
                      </label>
                      {weekendDndEnabled && (
                        <>
                          <input
                            type="time"
                            aria-label="주말 방해금지 시작 시간"
                            value={weekendDndStart}
                            onChange={(e) => {
                              const nextWkStart = e.target.value;
                              setWeekendDndStart(nextWkStart);
                              void syncDndToBackend(
                                isDndEnabled,
                                dndStart,
                                dndEnd,
                                { enabled: isDndEnabled, start: dndStart, end: dndEnd },
                                { separateWeekend: true, weekendStart: nextWkStart }
                              );
                            }}
                            style={{
                              minHeight: '42px',
                              background: 'var(--card-bg)',
                              border: '1px solid var(--card-border)',
                              borderRadius: '10px',
                              padding: '6px 10px',
                              color: 'var(--text-main)',
                              fontWeight: 700,
                            }}
                          />
                          <span style={{ fontSize: '0.84rem', color: 'var(--text-sub)', fontWeight: 700 }}>
                            부터
                          </span>
                          <input
                            type="time"
                            aria-label="주말 방해금지 종료 시간"
                            value={weekendDndEnd}
                            onChange={(e) => {
                              const nextWkEnd = e.target.value;
                              setWeekendDndEnd(nextWkEnd);
                              void syncDndToBackend(
                                isDndEnabled,
                                dndStart,
                                dndEnd,
                                { enabled: isDndEnabled, start: dndStart, end: dndEnd },
                                { separateWeekend: true, weekendEnd: nextWkEnd }
                              );
                            }}
                            style={{
                              minHeight: '42px',
                              background: 'var(--card-bg)',
                              border: '1px solid var(--card-border)',
                              borderRadius: '10px',
                              padding: '6px 10px',
                              color: 'var(--text-main)',
                              fontWeight: 700,
                            }}
                          />
                          <span style={{ fontSize: '0.84rem', color: 'var(--text-sub)', fontWeight: 700 }}>
                            까지
                          </span>
                        </>
                      )}
                    </div>
                  )}
                </div>
              </div>
            )}
          </section>
        </main>
      </div>
    </div>
  );
}
