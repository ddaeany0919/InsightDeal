package com.ddaeany0919.insightdeal.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * 🌐 NetworkMonitor
 * - ConnectivityManager의 NetworkCallback을 이용해 실시간 네트워크 상태를 감지합니다.
 * - 코루틴 callbackFlow를 사용하여 Flow<Boolean> 형태로 인터넷 연결 유무(isOnline)를 스트리밍합니다.
 * - debounce(1500)를 이식하여 1.5초 이내의 짧은 네트워크 흔들림(Flapping) 현상을 방지합니다.
 */
class NetworkMonitor(private val context: Context) {

    @OptIn(FlowPreview::class)
    val isOnline: Flow<Boolean> = callbackFlow {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        
        if (connectivityManager == null) {
            trySend(false)
            close()
            return@callbackFlow
        }

        // 초기 네트워크 상태 발송
        val initialCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        val isInitialOnline = initialCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        trySend(isInitialOnline)

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onLost(network: Network) {
                trySend(false)
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                trySend(hasInternet)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, callback)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(callback)
        }
    }
    .debounce(1500)
    .distinctUntilChanged()
}
