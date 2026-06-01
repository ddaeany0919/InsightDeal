package com.ddaeany0919.insightdeal.data.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.TimeUnit

/**
 * Network configuration utility for Phase 1 connectivity improvements
 * Handles real device → PC server connection issues
 */
object NetworkConfig {
    private const val TAG = "NetworkConfig"
    private const val SERVER_PORT = 8000
    
    // 활성 서버 URL 메모리 캐시 가드
    @Volatile
    private var cachedServerUrl: String? = null

    // 잠재적인 서버 IP 리스트
    private fun getPotentialIps(): List<String> {
        val list = mutableListOf<String>()
        try {
            // 0순위: BuildConfig.BASE_URL에서 실시간 추출한 IP
            val buildUrl = com.ddaeany0919.insightdeal.BuildConfig.BASE_URL
            val cleanIp = buildUrl.replace("http://", "").replace("https://", "").substringBefore(":").substringBefore("/")
            if (cleanIp.isNotBlank()) {
                list.add(cleanIp)
                Log.d(TAG, "0순위 실시간 BuildConfig IP 탑재 성공: $cleanIp")
            }
        } catch (e: Exception) {
            Log.e(TAG, "BuildConfig IP 추출 실패", e)
        }
        
        // 1순위 이하 잠재 IP 리스트
        val potentials = listOf(
            "192.168.0.4",  // Old configured IP
            "192.168.0.36", // Current PC IP
            "192.168.0.1",  // Router/gateway
            "192.168.1.1",  // Alternative router
            "192.168.1.100", // Common PC IP
            "192.168.0.100", // Alternative PC IP
            "10.0.2.2"      // Emulator fallback
        )
        
        potentials.forEach { ip ->
            if (!list.contains(ip)) {
                list.add(ip)
            }
        }
        return list
    }
    
    /**
     * Find the best available server URL
     * Tests connectivity to multiple IPs and returns the first working one
     */
    suspend fun findBestServerUrl(forceScan: Boolean = false): String = withContext(Dispatchers.IO) {
        // 캐시된 URL이 존재하고 연결이 유효하면 즉각 반환 (0ms 가속)
        val cached = cachedServerUrl
        if (!forceScan && cached != null) {
            val cachedHost = cached.replace("http://", "").replace("https://", "").substringBefore(":").substringBefore("/")
            if (testConnectivity(cachedHost, SERVER_PORT)) {
                Log.d(TAG, "캐시된 활성 서버 사용 성공 (0ms): $cached")
                return@withContext cached
            } else {
                Log.w(TAG, "캐시된 서버 연결 끊김 감지, 재스캔 격발!")
                cachedServerUrl = null
            }
        }

        Log.d(TAG, "findBestServerUrl: 서버 검색 시작")
        
        // First, try the device's own network to find PC
        val deviceIP = getDeviceIP()
        Log.d(TAG, "Device IP: $deviceIP")
        
        val ips = getPotentialIps()
        // Test each potential server IP
        for (ip in ips) {
            val url = "http://$ip:$SERVER_PORT/"
            Log.d(TAG, "Testing connectivity to: $url")
            
            if (testConnectivity(ip, SERVER_PORT)) {
                Log.d(TAG, "findBestServerUrl: 성공! 사용할 URL = $url")
                cachedServerUrl = url // 캐시 기록
                return@withContext url
            }
        }
        
        // If nothing works, return default
        val fallbackUrl = com.ddaeany0919.insightdeal.BuildConfig.BASE_URL
        Log.w(TAG, "findBestServerUrl: 모든 IP 테스트 실패, 기본값 사용: $fallbackUrl")
        fallbackUrl
    }
    
    /**
     * Test if server is reachable at given IP and port
     */
    private suspend fun testConnectivity(ip: String, port: Int): Boolean = withContext(Dispatchers.IO) {
        return@withContext try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), 2000) // 2 second timeout
                Log.d(TAG, "Connectivity test SUCCESS: $ip:$port")
                true
            }
        } catch (e: Exception) {
            Log.d(TAG, "Connectivity test FAILED: $ip:$port - ${e.message}")
            false
        }
    }
    
    /**
     * Get current device's IP address
     */
    private fun getDeviceIP(): String? {
        return try {
            val enumeration = java.net.NetworkInterface.getNetworkInterfaces()
            for (networkInterface in enumeration) {
                for (inetAddress in networkInterface.inetAddresses) {
                    if (!inetAddress.isLoopbackAddress && !inetAddress.isLinkLocalAddress && inetAddress.hostAddress?.contains(":") == false) {
                        return inetAddress.hostAddress
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.w(TAG, "Failed to get device IP", e)
            null
        }
    }
    
    /**
     * Get server URL with automatic network detection
     * Call this instead of using BuildConfig.BASE_URL directly
     */
    suspend fun getServerUrl(): String {
        return try {
            findBestServerUrl()
        } catch (e: Exception) {
            Log.e(TAG, "getServerUrl failed, using fallback", e)
            com.ddaeany0919.insightdeal.BuildConfig.BASE_URL // Safe fallback
        }
    }

    /**
     * 현재 캐시되어 작동이 증명된 최적 백엔드 서버 주소를 반환합니다.
     * 만약 세팅되지 않았다면 BuildConfig.BASE_URL을 폴백으로 리턴합니다.
     */
    fun getActiveServerUrl(): String {
        return cachedServerUrl ?: "http://192.168.0.36:8000/"
    }
}