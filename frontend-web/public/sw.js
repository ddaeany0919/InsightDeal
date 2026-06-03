// Web Push 서비스 워커 (FCM Background Web Push Receiver)

self.addEventListener('push', function(event) {
  console.log('[Service Worker] Push Received.');
  console.log(`[Service Worker] Push had this data: "${event.data.text()}"`);

  let data = {};
  if (event.data) {
    try {
      data = event.data.json();
    } catch (e) {
      data = { title: 'InsightDeal 핫딜 알림', body: event.data.text() };
    }
  }

  const title = data.title || 'InsightDeal 핫딜 알림';
  const options = {
    body: data.body || '새로운 특가 정보가 도착했습니다.',
    icon: data.icon || '/icon-192x192.png',
    badge: data.badge || '/badge-72x72.png',
    image: data.image_url || data.thumbnail_url || data.image || null,
    data: {
      url: data.ecommerce_url || data.post_url || 'https://insightdeal.com'
    },
    actions: [
      { action: 'open_url', title: '쇼핑몰 바로가기' }
    ]
  };

  event.waitUntil(
    self.registration.showNotification(title, options)
  );
});

self.addEventListener('notificationclick', function(event) {
  console.log('[Service Worker] Notification click Received.');
  event.notification.close();

  let targetUrl = 'https://insightdeal.com';
  if (event.notification.data && event.notification.data.url) {
    targetUrl = event.notification.data.url;
  }

  event.waitUntil(
    clients.matchAll({ type: 'window' }).then(function(clientList) {
      for (let i = 0; i < clientList.length; i++) {
        const client = clientList[i];
        if (client.url === targetUrl && 'focus' in client) {
          return client.focus();
        }
      }
      if (clients.openWindow) {
        return clients.openWindow(targetUrl);
      }
    })
  );
});
