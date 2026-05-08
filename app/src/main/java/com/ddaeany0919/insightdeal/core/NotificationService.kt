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
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.text.NumberFormat

class InsightDealFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "InsightDealFCM"
        private const val CHANNEL_ID = "hotdeal_notifications"
        private const val CHANNEL_NAME = "핫딜 알림"
        private const val CHANNEL_DESCRIPTION = "새로운 핫딜과 가격 변동을 실시간으로 알려드립니다"

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
                
                val prefs = getSharedPreferences("insight_deal_prefs", Context.MODE_PRIVATE)
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

    private fun showSmartNotification(
        title: String,
        body: String,
        data: Map<String, String>,
        icon: Int = R.drawable.ic_notification,
        priority: Int = NotificationCompat.PRIORITY_DEFAULT,
        autoCancel: Boolean = true
    ) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val intent = createDeepLinkIntent(data)
        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(icon)
            .setContentIntent(pendingIntent)
            .setAutoCancel(autoCancel)
            .setPriority(priority)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .build()

        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notification)

        Log.d(TAG, "🔔 스마트 알림 표시: $title")
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
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500)
                setShowBadge(true)
            }

            val notificationManager: NotificationManager =
                getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)

            Log.d(TAG, "📱 알림 채널 생성 완료")
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
        val prefs = getSharedPreferences("insightdeal_prefs", MODE_PRIVATE)
        prefs.edit()
            .putString("fcm_token", token)
            .putLong("token_saved_at", System.currentTimeMillis())
            .apply()
        Log.d(TAG, "💾 FCM Token 로컬 저장 완료")
    }
}