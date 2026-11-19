// Web Push 서비스 워커 (Background Web Push Receiver)

self.addEventListener('push', function (event) {
  let rawText = '';
  if (event.data) {
    try {
      rawText = event.data.text();
    } catch {
      rawText = '';
    }
  }

  let data = {};
  if (rawText) {
    try {
      data = JSON.parse(rawText);
    } catch {
      data = { title: '얼만데 핫딜 알림', body: rawText };
    }
  }

  const origin = self.location ? self.location.origin : '';
  const dealId = data.deal_id !== undefined && data.deal_id !== null ? data.deal_id : data.dealId;
  const fallbackPath = data.url && String(data.url).startsWith('/') ? String(data.url) : '/';
  const detailUrl = dealId
    ? `${origin}/?dealId=${encodeURIComponent(String(dealId))}`
    : `${origin}${fallbackPath}`;

  const title = data.title || '얼만데 핫딜 알림';
  const options = {
    body: data.body || '새로운 특가 정보가 도착했습니다.',
    icon: data.icon || '/icon-192x192.png',
    badge: data.badge || '/favicon-96x96.png',
    image: data.image_url || data.thumbnail_url || data.image || null,
    data: {
      url: detailUrl,
      deal_id: dealId ?? null,
    },
    actions: [
      { action: 'open_detail', title: '자세히 보기' },
    ],
  };

  event.waitUntil(self.registration.showNotification(title, options));
});

self.addEventListener('notificationclick', function (event) {
  event.notification.close();

  const origin = self.location ? self.location.origin : '';
  const notifData = event.notification.data || {};
  const targetUrl =
    notifData.url ||
    (notifData.deal_id ? `${origin}/?dealId=${encodeURIComponent(String(notifData.deal_id))}` : `${origin}/`);

  event.waitUntil(
    clients.matchAll({ type: 'window', includeUncontrolled: true }).then(function (clientList) {
      for (let i = 0; i < clientList.length; i++) {
        const client = clientList[i];
        try {
          if (origin && new URL(client.url).origin === origin) {
            if (typeof client.navigate === 'function') {
              return client.navigate(targetUrl).then(function (navigatedClient) {
                return navigatedClient && 'focus' in navigatedClient
                  ? navigatedClient.focus()
                  : client.focus();
              });
            }
            if (client.url === targetUrl && 'focus' in client) {
              return client.focus();
            }
          }
        } catch {
          // URL 파싱 실패 시 다음 클라이언트 검사
        }
      }
      if (clients.openWindow) {
        return clients.openWindow(targetUrl);
      }
    })
  );
});
