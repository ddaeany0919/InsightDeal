package com.ddaeany0919.insightdeal

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ddaeany0919.insightdeal.network.ApiClient  // ✅ ApiClient import 추가
import com.ddaeany0919.insightdeal.network.FCMTokenRequest  // ✅ FCMTokenRequest import 추가
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
                val apiService = ApiClient.create()  // ✅ ApiClient 사용
                
                // ✅ FCMTokenRequest DTO 사용 (Map 대신)
                val request = FCMTokenRequest(
                    token = token,
                    deviceId = getDeviceId(),
                    platform = "android"
                )
                
                val response = apiService.registerFCMToken(request)  // ✅ 타입 일치
                if (response.isSuccessful) {
                    Log.d(TAG, "✅ FCM Token 서버 등록 성공")
                    saveTokenToPrefs(token)
                } else {
                    Log.e(TAG, "❌ FCM Token 서버 등록 실패: ${response.code()}")
                }
            } catch (e: HttpException) {
                Log.e(TAG, "❌ FCM Token 등록 중 네트워크 오류: ${e.message}")
            } catch (e: Exception) {
                Log.e(TAG, "❌ FCM Token 등록 중 알 수 없는 오류: ${e.message}")
            }
        }
    }
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "📨 FCM 메시지 수신: ${remoteMessage.from}")
        
        // 데이터 페이로드 처리
        remoteMessage.data.isNotEmpty().let {
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
        val siteName = data["site_name"] ?: "뽐뽐"
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
            autoCancel = false // 중요한 알림이므로 자동 삭제 안 함
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
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // 딥링크 인텐트 생성
        val intent = createDeepLinkIntent(data)
        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        // 액션 버튼들
        val actionButtons = createActionButtons(data)
        
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setSmallIcon(icon)
            .setContentIntent(pendingIntent)
            .setAutoCancel(autoCancel)
            .setPriority(priority)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .apply {
                // 액션 버튼 추가
                actionButtons.forEach { addAction(it) }
                
                // 중요한 알림의 경우 LED, 진동 설정
                if (priority == NotificationCompat.PRIORITY_MAX) {
                    setVibrate(longArrayOf(0, 500, 200, 500))
                }
            }
            .build()
        
        // 알림 표시
        val notificationId = System.currentTimeMillis().toInt()
        notificationManager.notify(notificationId, notification)
        
        Log.d(TAG, "🔔 스마트 알림 표시: $title")
    }
    
    private fun createDeepLinkIntent(data: Map<String, String>): Intent {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        
        // 알림 타입에 따른 딥링크 설정
        when (data["type"]) {
            TYPE_NEW_HOTDEAL -> {
                intent.putExtra("navigate_to", "hotdeal")
                intent.putExtra("deal_id", data["deal_id"])
            }
            TYPE_PRICE_DROP, TYPE_TARGET_ACHIEVED, TYPE_LOWEST_PRICE -> {
                intent.putExtra("navigate_to", "price_tracking")
                intent.putExtra("product_id", data["product_id"])
            }
        }
        
        return intent
    }
    
    private fun createActionButtons(data: Map<String, String>): List<NotificationCompat.Action> {
        val actions = mutableListOf<NotificationCompat.Action>()
        
        when (data["type"]) {
            TYPE_NEW_HOTDEAL -> {
                // "바로가기" 액션
                val viewIntent = createDeepLinkIntent(data)
                val viewPendingIntent = PendingIntent.getActivity(
                    this, 1, viewIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                actions.add(
                    NotificationCompat.Action.Builder(
                        R.drawable.ic_notification,
                        "바로가기",
                        viewPendingIntent
                    ).build()
                )
            }
            
            TYPE_PRICE_DROP, TYPE_TARGET_ACHIEVED, TYPE_LOWEST_PRICE -> {
                // "구매하기" 액션
                val buyIntent = Intent(Intent.ACTION_VIEW)
                buyIntent.data = android.net.Uri.parse(data["product_url"])
                val buyPendingIntent = PendingIntent.getActivity(
                    this, 3, buyIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                actions.add(
                    NotificationCompat.Action.Builder(
                        R.drawable.ic_notification,
                        "구매하기",
                        buyPendingIntent
                    ).build()
                )
                
                // "그래프 보기" 액션
                val graphIntent = createDeepLinkIntent(data)
                val graphPendingIntent = PendingIntent.getActivity(
                    this, 4, graphIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                actions.add(
                    NotificationCompat.Action.Builder(
                        R.drawable.ic_notification,
                        "그래프",
                        graphPendingIntent
                    ).build()
                )
            }
        }
        
        return actions
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
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            
            Log.d(TAG, "📱 알림 채널 생성 완료")
        }
    }
    
    private fun formatPrice(price: Int): String {
        return "${NumberFormat.getInstance().format(price)}원"
    }
    
    private fun getDeviceId(): String {
        // 간단한 디바이스 ID 생성 (실제로는 더 복잡한 로직 필요)
        return android.provider.Settings.Secure.getString(
            contentResolver,
            android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown_device"
    }
    
    private fun saveTokenToPrefs(token: String) {
        val prefs = getSharedPreferences("insightdeal_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putString("fcm_token", token)
            .putLong("token_saved_at", System.currentTimeMillis())
            .apply()
        Log.d(TAG, "💾 FCM Token 로컬 저장 완료")
    }
}

// 🎯 스마트 알림 관리자 - 중복 알림 방지 및 우선순위 관리
class SmartNotificationManager {
    
    companion object {
        private val recentNotifications = mutableMapOf<String, Long>()
        private const val DUPLICATE_THRESHOLD = 60_000L // 1분 내 중복 방지
        
        fun shouldShowNotification(type: String, productId: String? = null): Boolean {
            val key = "${type}_${productId ?: "general"}"
            val now = System.currentTimeMillis()
            
            return recentNotifications[key]?.let { lastTime ->
                (now - lastTime) > DUPLICATE_THRESHOLD
            } ?: true.also {
                recentNotifications[key] = now
            }
        }
        
        fun clearOldNotifications() {
            val now = System.currentTimeMillis()
            recentNotifications.entries.removeAll { (_, time) ->
                (now - time) > DUPLICATE_THRESHOLD * 10 // 10분 후 정리
            }
        }
    }
}