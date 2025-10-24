package com.yourpackage.insightdeal

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FCMService : FirebaseMessagingService() {
    
    companion object {
        private const val TAG = "FCMService"
        private const val CHANNEL_ID = "hotdeal_channel"
        private const val CHANNEL_NAME = "InsightDeal 핫딜 알림"
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }
    
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        
        Log.d(TAG, "📨 FCM Message received: ${remoteMessage.notification?.title}")
        
        // 포그라운드에서도 알림 표시
        showNotification(
            title = remoteMessage.notification?.title ?: "🔥 새 핫딜!",
            body = remoteMessage.notification?.body ?: "새로운 핫딜이 발견되었습니다!",
            data = remoteMessage.data
        )
    }
    
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "🔑 New FCM Token: ${token.take(20)}...")
        
        // 새 토큰을 서버에 전송
        sendTokenToServer(token)
    }
    
    private fun createNotificationChannel() {
        """안드로이드 O 이상에서 필요한 알림 채널 생성"""
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "InsightDeal 핫딜 및 가격 알림"
                enableLights(true)
                lightColor = android.graphics.Color.parseColor("#FF6B35")
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 200, 300)
                setShowBadge(true)
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            
            Log.d(TAG, "✅ Notification channel created: $CHANNEL_NAME")
        }
    }
    
    private fun showNotification(title: String, body: String, data: Map<String, String>) {
        """알림 표시 (폴센트 스타일 UI)"""
        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // 알림 타입에 따른 아이콘 및 액션 결정
            val (iconRes, intentAction) = when (data["type"]) {
                "new_deal" -> Pair(R.drawable.ic_deal_notification, "DEAL_DETAIL")
                "price_alert" -> Pair(R.drawable.ic_price_notification, "PRODUCT_DETAIL")
                "lowest_price" -> Pair(R.drawable.ic_chart_notification, "PRODUCT_CHART")
                else -> Pair(R.drawable.ic_notification, "MAIN")
            }
            
            // 인텐트 생성
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("notification_action", intentAction)
                
                // 데이터에 따른 추가 정보
                data["deal_id"]?.let { putExtra("deal_id", it.toInt()) }
                data["product_id"]?.let { putExtra("product_id", it.toInt()) }
            }
            
            val pendingIntent = PendingIntent.getActivity(
                this, 
                System.currentTimeMillis().toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            // 폴센트 스타일 알림 디자인
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(body)
                .setSmallIcon(iconRes)
                .setColor(android.graphics.Color.parseColor("#FF6B35")) // InsightDeal 브랜드 컬러
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setStyle(
                    NotificationCompat.BigTextStyle()
                        .bigText(body)
                        .setBigContentTitle(title)
                )
                .addAction(
                    R.drawable.ic_open,
                    "열어보기",
                    pendingIntent
                )
                .build()
            
            notificationManager.notify(System.currentTimeMillis().toInt(), notification)
            Log.d(TAG, "✅ Notification shown: $title")
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to show notification: ${e.message}")
        }
    }
    
    private fun sendTokenToServer(token: String) {
        """서버에 FCM 토큰 전송"""
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val apiService = ApiService.create()
                val response = apiService.registerFCMToken(
                    mapOf(
                        "token" to token,
                        "user_id" to "anonymous", // 추후 사용자 시스템에서 가져오기
                        "device_info" to "${Build.MANUFACTURER} ${Build.MODEL}",
                        "app_version" to BuildConfig.VERSION_NAME
                    )
                )
                
                Log.d(TAG, "✅ FCM Token sent to server successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to send FCM token to server: ${e.message}")
            }
        }
    }
}