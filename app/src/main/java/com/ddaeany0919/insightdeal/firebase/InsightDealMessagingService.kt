package com.ddaeany0919.insightdeal.firebase

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ddaeany0919.insightdeal.MainActivity
import com.ddaeany0919.insightdeal.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * 🔔 InsightDeal Firebase Cloud Messaging 서비스
 * 
 * 핫딜 알림, 가격 하락 알림 등을 처리합니다.
 */
class InsightDealMessagingService : FirebaseMessagingService() {
    
    companion object {
        private const val TAG = "InsightDealFCM"
        private const val CHANNEL_ID = "insightdeal_notifications"
        private const val CHANNEL_NAME = "InsightDeal 알림"
    }
    
    /**
     * 🆔 FCM 토큰이 새로 발급되거나 갱신될 때 호출
     */
    override fun onNewToken(token: String) {
        Log.d(TAG, "새로운 FCM 토큰: $token")
        
        // 서버에 토큰 전송
        sendTokenToServer(token)
    }
    
    /**
     * 📨 FCM 메시지 수신 처리
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "메시지 수신: ${remoteMessage.from}")
        
        // 데이터 메시지 처리
        remoteMessage.data.isNotEmpty().let {
            Log.d(TAG, "메시지 데이터: ${remoteMessage.data}")
            
            val notificationType = remoteMessage.data["type"]
            when (notificationType) {
                "price_drop" -> handlePriceDropNotification(remoteMessage.data)
                "hot_deal" -> handleHotDealNotification(remoteMessage.data)
                "keyword_alert" -> handleKeywordAlertNotification(remoteMessage.data)
                else -> handleDefaultNotification(remoteMessage)
            }
        }
        
        // 일반 알림 메시지 처리
        remoteMessage.notification?.let {
            Log.d(TAG, "알림 제목: ${it.title}")
            Log.d(TAG, "알림 내용: ${it.body}")
            
            sendNotification(
                title = it.title ?: "InsightDeal",
                body = it.body ?: "새로운 알림이 도착했습니다.",
                data = remoteMessage.data
            )
        }
    }
    
    /**
     * 💰 가격 하락 알림 처리
     */
    private fun handlePriceDropNotification(data: Map<String, String>) {
        val productName = data["product_name"] ?: "상품"
        val originalPrice = data["original_price"] ?: "0"
        val currentPrice = data["current_price"] ?: "0"
        val discountRate = data["discount_rate"] ?: "0"
        
        sendNotification(
            title = "🔥 가격 하락 알림!",
            body = "$productName 가격이 ${discountRate}% 할인되었습니다! $originalPrice → $currentPrice",
            data = data
        )
    }
    
    /**
     * 🔥 핫딜 알림 처리
     */
    private fun handleHotDealNotification(data: Map<String, String>) {
        val dealTitle = data["deal_title"] ?: "새로운 핫딜"
        val site = data["site"] ?: "커뮤니티"
        val discountRate = data["discount_rate"]
        
        val body = if (discountRate != null) {
            "$site에서 $discountRate% 할인 핫딜을 발견했습니다!"
        } else {
            "$site에서 새로운 핫딜을 발견했습니다!"
        }
        
        sendNotification(
            title = "🔥 $dealTitle",
            body = body,
            data = data
        )
    }
    
    /**
     * 🔔 키워드 알림 처리
     */
    private fun handleKeywordAlertNotification(data: Map<String, String>) {
        val keyword = data["keyword"] ?: "관심상품"
        val dealCount = data["deal_count"] ?: "1"
        
        sendNotification(
            title = "🔍 키워드 알림: $keyword",
            body = "'$keyword' 관련 새로운 딜 $dealCount 개가 등록되었습니다!",
            data = data
        )
    }
    
    /**
     * 📱 기본 알림 처리
     */
    private fun handleDefaultNotification(remoteMessage: RemoteMessage) {
        sendNotification(
            title = remoteMessage.notification?.title ?: "InsightDeal",
            body = remoteMessage.notification?.body ?: "새로운 알림이 도착했습니다.",
            data = remoteMessage.data
        )
    }
    
    /**
     * 📨 알림 발송
     */
    private fun sendNotification(
        title: String,
        body: String,
        data: Map<String, String> = emptyMap()
    ) {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            // 알림 데이터를 Intent에 추가
            data.forEach { (key, value) ->
                putExtra(key, value)
            }
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Android 8.0 이상에서 알림 채널 생성
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "InsightDeal 핫딜 및 가격 알림"
                enableLights(true)
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
    
    /**
     * 📤 서버에 FCM 토큰 전송
     */
    private fun sendTokenToServer(token: String) {
        // TODO: 실제 서버 API 호출하여 토큰 등록
        Log.d(TAG, "서버에 FCM 토큰 전송: $token")
        
        // SharedPreferences에 토큰 저장 (임시)
        val prefs = getSharedPreferences("insightdeal_fcm", Context.MODE_PRIVATE)
        prefs.edit().putString("fcm_token", token).apply()
        
        // 실제 구현에서는 retrofit 등을 사용하여 서버에 전송
        // ApiService.registerFCMToken(token)
    }
}