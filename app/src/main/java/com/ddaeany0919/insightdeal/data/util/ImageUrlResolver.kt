package com.ddaeany0919.insightdeal.data.util

import com.ddaeany0919.insightdeal.data.network.NetworkConfig
import java.net.URLEncoder

/**
 * 2중 하이브리드 이미지 CDN/프록시 URL 리졸버
 * 1. 뽐뿌/에펨/루리웹 등 외부 핫링크 차단 커뮤니티 및 http:// -> 자체 GCP 백엔드 프록시 (/api/proxy-image)
 * 2. 네이버/지마켓/쿠팡/11번가/알리 등 일반 허용 쇼핑몰 https:// -> wsrv.nl 무료 글로벌 CDN (WebP 최적화)
 * 3. CDN 장애 또는 로드 실패 시 백엔드 프록시로 안전 전환하기 위한 Fallback URL 생성기 제공
 */
object ImageUrlResolver {
    val BLOCKED_DOMAINS = listOf(
        "ppomppu.co.kr",
        "fmkorea.com",
        "fmkorea.org",
        "quasarzone.com",
        "ruliweb.com",
        "bbasak.com",
        "clien.net",
        "coolenjoy.net",
        "damoang.net"
    )

    fun isBlockedCommunity(url: String): Boolean {
        val lower = url.lowercase()
        return BLOCKED_DOMAINS.any { lower.contains(it) }
    }

    /**
     * 기본 1차 이미지 URL 해석기
     */
    fun resolve(rawUrl: String?, targetWidth: Int? = null): String? {
        if (rawUrl.isNullOrBlank()) return null
        val trimmed = rawUrl.trim()
        if (!trimmed.startsWith("http")) return trimmed

        // 이미 백엔드 프록시이거나 이미 wsrv.nl인 경우 중복 래핑 방지
        if (trimmed.contains("/api/proxy-image") || trimmed.contains("wsrv.nl")) {
            return trimmed
        }

        // 1. 외부 차단 커뮤니티 또는 비보안 http:// -> 자체 백엔드 프록시
        if (trimmed.startsWith("http://") || isBlockedCommunity(trimmed)) {
            return try {
                val baseUrl = NetworkConfig.getActiveServerUrl().removeSuffix("/")
                val encoded = URLEncoder.encode(trimmed, "UTF-8")
                "$baseUrl/api/proxy-image?url=$encoded&v=5"
            } catch (e: Exception) {
                trimmed
            }
        }

        // 2. 일반 쇼핑몰 및 외부 접근 허용 https:// 도메인 -> wsrv.nl 무료 글로벌 CDN (WebP 압축)
        if (trimmed.startsWith("https://")) {
            return try {
                val encoded = URLEncoder.encode(trimmed, "UTF-8")
                val widthParam = if (targetWidth != null && targetWidth > 0) "&w=$targetWidth" else ""
                "https://wsrv.nl/?url=$encoded&output=webp$widthParam&we=1"
            } catch (e: Exception) {
                trimmed
            }
        }

        return trimmed
    }

    /**
     * wsrv.nl 로딩 실패 시 백엔드 프록시로 폴백하기 위한 대체 URL 생성
     */
    fun getFallbackBackendProxyUrl(rawUrl: String?): String? {
        if (rawUrl.isNullOrBlank() || !rawUrl.startsWith("http")) return rawUrl
        val trimmed = rawUrl.trim()
        if (trimmed.contains("/api/proxy-image")) return trimmed
        return try {
            val baseUrl = NetworkConfig.getActiveServerUrl().removeSuffix("/")
            val encoded = URLEncoder.encode(trimmed, "UTF-8")
            "$baseUrl/api/proxy-image?url=$encoded&v=5"
        } catch (_: Exception) {
            rawUrl
        }
    }
}
