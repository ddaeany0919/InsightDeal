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
    
    // Potential server IP addresses to try
    private val POTENTIAL_SERVER_IPS = listOf(
        "192.168.0.4",  // Current configured IP
        "192.168.0.1",  // Router/gateway
        "192.168.1.1",  // Alternative router
        "192.168.1.100", // Common PC IP
        "192.168.0.100", // Alternative PC IP
        "10.0.2.2"      // Emulator fallback
    )
    
    /**
     * Find the best available server URL
     * Tests connectivity to multiple IPs and returns the first working one
     */
    suspend fun findBestServerUrl(): String = withContext(Dispatchers.IO) {
        Log.d(TAG, "findBestServerUrl: 서버 검색 시작")
        
        // First, try the device's own network to find PC
        val deviceIP = getDeviceIP()
        Log.d(TAG, "Device IP: $deviceIP")
        
        // Test each potential server IP
        for (ip in POTENTIAL_SERVER_IPS) {
            val url = "http://$ip:$SERVER_PORT/"
            Log.d(TAG, "Testing connectivity to: $url")
            
            if (testConnectivity(ip, SERVER_PORT)) {
                Log.d(TAG, "findBestServerUrl: 성공! 사용할 URL = $url")
                return@withContext url
            }
        }
        
        // If nothing works, return default
        val fallbackUrl = "http://192.168.0.4:8000/"
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
                    if (!inetAddress.isLoopbackAddress && !inetAddress.isLinkLocalAddress && inetAddress.hostAddress.indexOf(':') == -1) {
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
            "http://192.168.0.4:8000/" // Safe fallback
        }
    }
}