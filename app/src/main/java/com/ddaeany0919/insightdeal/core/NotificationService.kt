package com.ddaeany0919.insightdeal.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ddaeany0919.insightdeal.network.RegisterDeviceReq
import com.ddaeany0919.insightdeal.network.NetworkModule
import com.ddaeany0919.insightdeal.network.ApiService
import com.ddaeany0919.insightdeal.MainActivity
import com.ddaeany0919.insightdeal.R
import com.ddaeany0919.insightdeal.core.security.EncryptedPrefsManager
import com.ddaeany0919.insightdeal.presentation.mypage.history.NotificationHistoryManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.text.NumberFormat
import java.util.Calendar

class InsightDealFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "InsightDealFCM"
        private const val CHANNEL_ID = "hotdeal_notifications"
        private const val CHANNEL_NAME = "핫딜 알림"
        private const val CHANNEL_DESCRIPTION = "새로운 핫딜과 가격 변동을 실시간으로 알려드립니다"

        private const val SILENT_CHANNEL_ID = "silent_hotdeal_notifications"
        private const val SILENT_CHANNEL_NAME = "야간 무음 알림"
        private const val SILENT_CHANNEL_DESCRIPTION = "방해금지 시간대(21:00~08:00)에 소리와 진동 없이 수신되는 알림입니다"

        // 알림 카테고리
        private const val TYPE_NEW_HOTDEAL = "new_hotdeal"
        private const val TYPE_PRICE_DROP = "price_drop"
        private const val TYPE_TARGET_ACHIEVED = "target_achieved"
        private const val TYPE_LOWEST_PRICE = "lowest_price"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "🚀 InsightDeal FCM Service 시작")
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "🔄 FCM Token 갱신: ${token.take(20)}...")

        // 서버에 새 토큰 등록
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Log.d(TAG, "📡 NetworkModule을 사용하여 API Service 생성 중...")
                val apiService = NetworkModule.createService<ApiService>()

                val deviceId = getCustomDeviceId()
                Log.d(TAG, "📱 Device ID: $deviceId")
                
                // 🔒 보안 스토리지 전환 적용
                val prefs = EncryptedPrefsManager.getEncryptedPrefs(applicationContext)
                val nightPushConsent = prefs.getBoolean("night_push_consent", false)
                
                val request = RegisterDeviceReq(
                    fcm_token = token,
                    device_uuid = deviceId,
                    night_push_consent = nightPushConsent
                )
                
                Log.d(TAG, "📤 FCM Token 서버 전송 중...")
                val response = apiService.registerFCMToken(request)
                if (response.isSuccessful) {
                    Log.d(TAG, "✅ FCM Token 서버 등록 성공")
                    saveTokenToPrefs(token)
                } else {
                    Log.e(TAG, "❌ FCM Token 서버 등록 실패: ${response.code()}")
                }
            } catch (e: HttpException) {
                Log.e(TAG, "❌ FCM Token 등록 중 네트워크 오류: ${e.message}", e)
            } catch (e: Exception) {
                Log.e(TAG, "❌ FCM Token 등록 중 알 수 없는 오류: ${e.message}", e)
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "📨 FCM 메시지 수신: ${remoteMessage.from}")

        // 📥 데이터 수신 즉시 로컬 보관함에 적재 연동
        val title = remoteMessage.notification?.title 
            ?: remoteMessage.data["title"] 
            ?: "InsightDeal 알림"
        val keyword = remoteMessage.data["keyword"] 
            ?: remoteMessage.data["category"] 
            ?: "핫딜"
        val dealUrl = remoteMessage.data["ecommerce_url"] 
            ?: remoteMessage.data["post_url"] 
            ?: "https://insightdeal.com"

        try {
            NotificationHistoryManager.init(applicationContext)
            NotificationHistoryManager.addAlert(applicationContext, title, keyword, dealUrl)
            Log.d(TAG, "✅ FCM 수신 알림 로컬 보관함 적재 성공")
        } catch (e: Exception) {
            Log.e(TAG, "❌ FCM 수신 알림 로컬 보관함 적재 중 에러", e)
        }

        // 데이터 페이로드 처리
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "📊 데이터 페이로드: ${remoteMessage.data}")
            handleDataMessage(remoteMessage.data)
        }

        // 알림 페이로드 처리 (앱이 포그라운드일 때도 표시)
        remoteMessage.notification?.let { notification ->
            Log.d(TAG, "🔔 알림 페이로드: ${notification.title}")
            showSmartNotification(
                title = notification.title ?: "InsightDeal",
                body = notification.body ?: "새로운 알림이 있습니다",
                data = remoteMessage.data
            )
        }
    }

    private fun handleDataMessage(data: Map<String, String>) {
        val notificationType = data["type"] ?: return

        when (notificationType) {
            TYPE_NEW_HOTDEAL -> handleNewHotdealNotification(data)
            TYPE_PRICE_DROP -> handlePriceDropNotification(data)
            TYPE_TARGET_ACHIEVED -> handleTargetAchievedNotification(data)
            TYPE_LOWEST_PRICE -> handleLowestPriceNotification(data)
            else -> Log.w(TAG, "⚠️ 알 수 없는 알림 타입: $notificationType")
        }
    }

    private fun handleNewHotdealNotification(data: Map<String, String>) {
        val title = data["title"] ?: "새로운 핫딜"
        val siteName = data["site_name"] ?: "뽐뿌"
        val price = data["price"]?.let { formatPrice(it.toIntOrNull() ?: 0) } ?: ""
        val discountRate = data["discount_rate"] ?: ""

        val body = buildString {
            append("[$siteName] ")
            if (price.isNotEmpty()) append("$price ")
            if (discountRate.isNotEmpty()) append("($discountRate% 할인)")
            append(" 새로운 핫딜이 등록되었습니다!")
        }

        showSmartNotification(
            title = title,
            body = body,
            data = data,
            icon = R.drawable.ic_notification,
            priority = NotificationCompat.PRIORITY_HIGH
        )
    }

    private fun handlePriceDropNotification(data: Map<String, String>) {
        val productName = data["product_name"] ?: "관심 상품"
        val oldPrice = data["old_price"]?.toIntOrNull() ?: 0
        val newPrice = data["new_price"]?.toIntOrNull() ?: 0
        val dropPercent = if (oldPrice > 0) {
            ((oldPrice - newPrice) * 100 / oldPrice)
        } else 0

        val title = "💰 가격 하락 알림"
        val body = "$productName\n" +
                "${formatPrice(oldPrice)} → ${formatPrice(newPrice)}\n" +
                "${dropPercent}% 할인! 지금이 구매 기회입니다!"

        showSmartNotification(
            title = title,
            body = body,
            data = data,
            icon = R.drawable.ic_notification,
            priority = NotificationCompat.PRIORITY_MAX
        )
    }

    private fun handleTargetAchievedNotification(data: Map<String, String>) {
        val productName = data["product_name"] ?: "관심 상품"
        val targetPrice = data["target_price"]?.toIntOrNull() ?: 0
        val currentPrice = data["current_price"]?.toIntOrNull() ?: 0

        val title = "🎯 목표가격 달성!"
        val body = "$productName\n" +
                "목표: ${formatPrice(targetPrice)}\n" +
                "현재: ${formatPrice(currentPrice)}\n" +
                "지금 바로 구매하세요!"

        showSmartNotification(
            title = title,
            body = body,
            data = data,
            icon = R.drawable.ic_notification,
            priority = NotificationCompat.PRIORITY_MAX,
            autoCancel = false
        )
    }

    private fun handleLowestPriceNotification(data: Map<String, String>) {
        val productName = data["product_name"] ?: "관심 상품"
        val currentPrice = data["current_price"]?.toIntOrNull() ?: 0
        val period = data["period"] ?: "30일"

        val title = "⭐ 역대 최저가 달성!"
        val body = "$productName\n" +
                "현재가격: ${formatPrice(currentPrice)}\n" +
                "최근 $period 중 최저가입니다!\n" +
                "놓치면 후회할 기회입니다!"

        showSmartNotification(
            title = title,
            body = body,
            data = data,
            icon = R.drawable.ic_notification,
            priority = NotificationCompat.PRIORITY_MAX
        )
    }

    private fun downloadBitmap(context: Context, imageUrl: String): android.graphics.Bitmap? {
        return try {
            val loader = coil.ImageLoader(context)
            val request = coil.request.ImageRequest.Builder(context)
                .data(imageUrl)
                .allowHardware(false) // Notification에 사용하기 위해 소프트웨어 비트맵으로 로딩 필수
                .build()
            
            // FCM 백그라운드 스레드에서 동기적으로 이미지 로딩
            val result = kotlinx.coroutines.runBlocking {
                loader.execute(request)
            }
            
            if (result is coil.request.SuccessResult) {
                (result.drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ FCM Rich 알림 이미지 다운로드 중 에러: ${e.message}", e)
            null
        }
    }

    private fun showSmartNotification(
        title: String,
        body: String,
        data: Map<String, String>,
        icon: Int = R.drawable.ic_notification,
        priority: Int = NotificationCompat.PRIORITY_DEFAULT,
        autoCancel: Boolean = true
    ) {
        // ⏰ 야간 시간대 체크 (21:00 ~ 08:00)
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val isNightTime = hour >= 21 || hour < 8

        // 🔒 보안 스토리지를 통해 야간 수신 동의 여부 로드 (백그라운드 스레드 Keystore 예외 방지 Fallback 적용)
        val prefs = EncryptedPrefsManager.getEncryptedPrefs(applicationContext)
        val nightPushConsent = prefs.getBoolean("night_push_consent", false)

        if (isNightTime) {
            if (!nightPushConsent) {
                // 야간 수신 동의가 비활성화 상태이면 시스템 알림은 노출하지 않고 취소 (로컬 보관함에만 적재)
                Log.d(TAG, "🚫 야간 방해금지(21:00~08:00) 및 수신 비동의 상태로 시스템 알림 노출 스킵: $title")
                return
            }
        }

        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val intent = createDeepLinkIntent(data)
        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 야간 시간대 수신 동의 상태라면, 알림을 소리/진동 없이 "무음 채널"로 우회 생성하여 사용자 숙면 방해 최소화
        val activeChannelId = if (isNightTime) SILENT_CHANNEL_ID else CHANNEL_ID
        val activePriority = if (isNightTime) NotificationCompat.PRIORITY_LOW else priority

        val notificationBuilder = NotificationCompat.Builder(this, activeChannelId)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(icon)
            .setContentIntent(pendingIntent)
            .setAutoCancel(autoCancel)
            .setPriority(activePriority)

        // 🖼️ Rich Notification (BigPictureStyle) 이식
        // 데이터 페이로드에서 이미지 URL 추출 (다양한 키Fallback 적용)
        val imageUrl = data["image_url"] 
            ?: data["thumbnail_url"] 
            ?: data["imageUrl"] 
            ?: data["image"]
            ?: data["thumbnail"]

        if (!imageUrl.isNullOrBlank()) {
            val bitmap = downloadBitmap(applicationContext, imageUrl)
            if (bitmap != null) {
                notificationBuilder.setLargeIcon(bitmap)
                notificationBuilder.setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(bitmap)
                        .bigLargeIcon(null as android.graphics.Bitmap?) // 알림 펼쳤을 때 우측 작은 썸네일 중복 방지 제거
                        .setSummaryText(body)
                )
                Log.d(TAG, "🖼️ FCM Rich 알림 이미지 연동 성공: $imageUrl")
            } else {
                notificationBuilder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
            }
        } else {
            notificationBuilder.setStyle(NotificationCompat.BigTextStyle().bigText(body))
        }

        if (isNightTime) {
            notificationBuilder.setSound(null)
            notificationBuilder.setVibrate(null)
            notificationBuilder.setDefaults(0)
        } else {
            notificationBuilder.setDefaults(NotificationCompat.DEFAULT_ALL)
        }

        val notification = notificationBuilder.build()
        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notification)

        Log.d(TAG, "🔔 스마트 알림 표시 (채널: $activeChannelId): $title")
    }

    private fun createDeepLinkIntent(data: Map<String, String>): Intent {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        // 모든 페이로드 데이터를 Intent extras에 추가
        for ((key, value) in data) {
            intent.putExtra(key, value)
        }

        // 호환성을 위해 기존 type 로직 유지
        when (data["type"]) {
            TYPE_NEW_HOTDEAL -> {
                if (!data.containsKey("navigate_to")) intent.putExtra("navigate_to", "hotdeal")
                if (!data.containsKey("deal_id")) intent.putExtra("deal_id", data["deal_id"])
            }
            TYPE_PRICE_DROP, TYPE_TARGET_ACHIEVED, TYPE_LOWEST_PRICE -> {
                if (!data.containsKey("navigate_to")) intent.putExtra("navigate_to", "price_tracking")
                if (!data.containsKey("product_id")) intent.putExtra("product_id", data["product_id"])
            }
        }

        return intent
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 1. 기본 고음량 알림 채널
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                setShowBadge(true)
            }

            // 2. 야간 무음 알림 채널
            val silentImportance = NotificationManager.IMPORTANCE_LOW
            val silentChannel = NotificationChannel(SILENT_CHANNEL_ID, SILENT_CHANNEL_NAME, silentImportance).apply {
                description = SILENT_CHANNEL_DESCRIPTION
                enableLights(true)
                enableVibration(false)
                vibrationPattern = null
                setSound(null, null)
                setShowBadge(true)
            }

            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            notificationManager.createNotificationChannel(silentChannel)

            Log.d(TAG, "📱 알림 채널 생성 완료 (기본 및 무음 채널 등록 완료)")
        }
    }

    private fun formatPrice(price: Int): String {
        return "${NumberFormat.getInstance().format(price)}원"
    }

    private fun getCustomDeviceId(): String {
        return android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown_device"
    }

    private fun saveTokenToPrefs(token: String) {
        val prefs = EncryptedPrefsManager.getEncryptedPrefs(applicationContext)
        prefs.edit()
            .putString("fcm_token", token)
            .putLong("token_saved_at", System.currentTimeMillis())
            .apply()
        Log.d(TAG, "💾 FCM Token 로컬 보안 저장 완료")
    }
}