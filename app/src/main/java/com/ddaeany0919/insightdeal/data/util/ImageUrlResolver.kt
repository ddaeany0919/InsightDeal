package com.ddaeany0919.insightdeal.data.util

import com.ddaeany0919.insightdeal.BuildConfig
import com.ddaeany0919.insightdeal.data.network.NetworkConfig
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * 2중 하이브리드 이미지 CDN/프록시 URL 리졸버
 * 1. 로컬 디버그 호스트(10.0.2.2/localhost/127.0.0.1)는 디버그에서 직접 로드(SSRF 404 루프 방지), 릴리스에서는 운영 BASE_URL 치환 또는 차단
 * 2. 상대 경로(/api/proxy-image, /images/..., //...)를 활성 BASE_URL과 결합
 * 3. 외부 http:// URL은 https://로 강제 업그레이드 (릴리스 cleartextTrafficPermitted="false" 정합)
 * 4. 핫링크 차단 커뮤니티는 자체 백엔드 프록시(/api/proxy-image), 일반 허용 도메인은 wsrv.nl CDN으로 라우팅
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

    private val LOCAL_HOSTS = setOf("10.0.2.2", "localhost", "127.0.0.1")

    private val KAKAOCDN_LOWRES_REGEX = Regex(
        "^(https?://search\\d*\\.kakaocdn\\.net/argon/)(?:130x130(?:_\\d+_[a-zA-Z0-9]+)?|200x200(?:_\\d+_[a-zA-Z0-9]+)?|320x320(?:_\\d+_[a-zA-Z0-9]+)?|0x\\d+(?:_\\d+_[a-zA-Z0-9]+)?|656x0(?:_\\d+_[a-zA-Z0-9]+)?)(/[^?\\s]+)",
        RegexOption.IGNORE_CASE
    )

    fun upgradeLowresImageUrl(url: String): String {
        return if (KAKAOCDN_LOWRES_REGEX.containsMatchIn(url)) {
            KAKAOCDN_LOWRES_REGEX.replace(url) { m ->
                "${m.groupValues[1]}600x0_65_wr${m.groupValues[2]}"
            }
        } else {
            url
        }
    }

    fun isBlockedCommunity(url: String): Boolean {
        val lower = url.lowercase()
        return BLOCKED_DOMAINS.any { lower.contains(it) }
    }

    /**
     * 기본 1차 이미지 URL 해석기
     */
    fun resolve(rawUrl: String?, targetWidth: Int? = null): String? {
        return resolve(
            rawUrl = rawUrl,
            targetWidth = targetWidth,
            activeBaseUrl = resolveRuntimeBaseUrl(),
            isDebug = BuildConfig.DEBUG
        )
    }

    internal fun resolve(
        rawUrl: String?,
        targetWidth: Int? = null,
        activeBaseUrl: String,
        isDebug: Boolean
    ): String? {
        val trimmed = rawUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val cleanBase = normalizeBaseUrl(activeBaseUrl, isDebug)

        // data: URI 등 특수 스킴은 그대로 반환
        if (trimmed.startsWith("data:", ignoreCase = true)) {
            return trimmed
        }

        // 1. 프로토콜 생략 URL (//cdn.example.com/...) -> https:// 결합 + 저해상도 kakaocdn 업그레이드
        val normalizedInput = upgradeLowresImageUrl(
            when {
                trimmed.startsWith("//") -> "https:$trimmed"
                trimmed.startsWith("/") -> "$cleanBase$trimmed"
                trimmed.startsWith("http://", ignoreCase = true) -> "http://${trimmed.substring(7)}"
                trimmed.startsWith("https://", ignoreCase = true) -> "https://${trimmed.substring(8)}"
                else -> return trimmed
            }
        )

        val parsedUri = parseUriSafe(normalizedInput)
        val host = extractHost(normalizedInput, parsedUri)
        val pathAndQuery = extractPathAndQuery(normalizedInput, parsedUri)
        val cleanBaseHost = extractHost(cleanBase, parseUriSafe(cleanBase))

        // 2. 로컬 디버그 호스트(10.0.2.2, localhost, 127.0.0.1) 처리
        if (host in LOCAL_HOSTS) {
            return if (isDebug) {
                // 디버그: localhost/127.0.0.1 등을 활성 디버그 베이스(예: 10.0.2.2:8080)로 정규화해 직접 반환 (프록시 재래핑 금지)
                if (pathAndQuery.isNotBlank()) "$cleanBase$pathAndQuery" else cleanBase
            } else {
                // 릴리스: 10.0.2.2 평문 절대 미노출! /images/ 또는 /api/proxy-image 경로만 운영 BASE_URL로 치환
                if (isKnownBackendAssetOrProxyPath(pathAndQuery)) {
                    "$cleanBase$pathAndQuery"
                } else {
                    null
                }
            }
        }

        // 3. 이미 백엔드 프록시·자체 이미지 캐시(/images/...)이거나 wsrv.nl인 경우 중복/외부 CDN 래핑 방지 + https 보정
        if (normalizedInput.contains("/api/proxy-image", ignoreCase = true) ||
            normalizedInput.contains("/api/image-proxy", ignoreCase = true) ||
            host.contains("wsrv.nl") ||
            (host.isNotEmpty() && host == cleanBaseHost && isKnownBackendAssetOrProxyPath(pathAndQuery))
        ) {
            return ensureHttpsForExternal(normalizedInput)
        }

        // 4. 차단 커뮤니티는 자체 백엔드 프록시 경유 (앱 -> 백엔드 프록시 통신 URL은 릴리스에서 https 보장)
        if (isBlockedCommunity(normalizedInput)) {
            return try {
                val encoded = URLEncoder.encode(normalizedInput, "UTF-8")
                val widthParam = if (targetWidth != null && targetWidth > 0) "&w=$targetWidth" else ""
                "$cleanBase/api/proxy-image?url=$encoded$widthParam&v=5"
            } catch (_: Exception) {
                ensureHttpsForExternal(normalizedInput)
            }
        }

        // 5. 비차단 외부 도메인: http:// -> https:// 강제 업그레이드 후 wsrv.nl CDN 최적화
        val httpsUrl = ensureHttpsForExternal(normalizedInput)
        return try {
            val encoded = URLEncoder.encode(httpsUrl, "UTF-8")
            val widthParam = if (targetWidth != null && targetWidth > 0) "&w=$targetWidth" else ""
            "https://wsrv.nl/?url=$encoded&output=webp$widthParam&we=1"
        } catch (_: Exception) {
            httpsUrl
        }
    }

    /**
     * wsrv.nl 로딩 실패 시 백엔드 프록시로 폴백하기 위한 대체 URL 생성
     */
    fun getFallbackBackendProxyUrl(rawUrl: String?): String? {
        return getFallbackBackendProxyUrl(
            rawUrl = rawUrl,
            activeBaseUrl = resolveRuntimeBaseUrl(),
            isDebug = BuildConfig.DEBUG
        )
    }

    internal fun getFallbackBackendProxyUrl(
        rawUrl: String?,
        activeBaseUrl: String,
        isDebug: Boolean
    ): String? {
        val trimmed = rawUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
        val cleanBase = normalizeBaseUrl(activeBaseUrl, isDebug)

        if (trimmed.startsWith("data:", ignoreCase = true)) {
            return trimmed
        }

        val normalizedInput = upgradeLowresImageUrl(
            when {
                trimmed.startsWith("//") -> "https:$trimmed"
                trimmed.startsWith("/") -> "$cleanBase$trimmed"
                trimmed.startsWith("http://", ignoreCase = true) -> "http://${trimmed.substring(7)}"
                trimmed.startsWith("https://", ignoreCase = true) -> "https://${trimmed.substring(8)}"
                else -> return trimmed
            }
        )

        val parsedUri = parseUriSafe(normalizedInput)
        val host = extractHost(normalizedInput, parsedUri)
        val pathAndQuery = extractPathAndQuery(normalizedInput, parsedUri)
        val cleanBaseHost = extractHost(cleanBase, parseUriSafe(cleanBase))

        // 로컬 디버그 호스트는 백엔드 프록시 루프(SSRF 차단)로 감싸지 않고 직접 정규화 반환
        if (host in LOCAL_HOSTS) {
            return if (isDebug) {
                if (pathAndQuery.isNotBlank()) "$cleanBase$pathAndQuery" else cleanBase
            } else {
                if (isKnownBackendAssetOrProxyPath(pathAndQuery)) {
                    "$cleanBase$pathAndQuery"
                } else {
                    null
                }
            }
        }

        // 이미 백엔드 프록시 또는 자체 이미지 캐시(/images/...)인 경우 https 보정 후 반환
        if (normalizedInput.contains("/api/proxy-image", ignoreCase = true) ||
            normalizedInput.contains("/api/image-proxy", ignoreCase = true) ||
            (host.isNotEmpty() && host == cleanBaseHost && isKnownBackendAssetOrProxyPath(pathAndQuery))
        ) {
            return ensureHttpsForExternal(normalizedInput)
        }

        // wsrv.nl URL이 들어오면 내부 원본 url 파라미터 언래핑
        val targetUrl = if (host.contains("wsrv.nl")) {
            extractWsrvTargetUrl(parsedUri) ?: ensureHttpsForExternal(normalizedInput)
        } else {
            if (isBlockedCommunity(normalizedInput)) normalizedInput else ensureHttpsForExternal(normalizedInput)
        }

        return try {
            val encoded = URLEncoder.encode(targetUrl, "UTF-8")
            "$cleanBase/api/proxy-image?url=$encoded&v=5"
        } catch (_: Exception) {
            ensureHttpsForExternal(targetUrl)
        }
    }

    private fun resolveRuntimeBaseUrl(): String {
        val rawBase = runCatching { NetworkConfig.getActiveServerUrl() }
            .getOrDefault(BuildConfig.BASE_URL)
        return if (!BuildConfig.DEBUG && containsLocalHost(rawBase)) {
            BuildConfig.BASE_URL
        } else {
            rawBase
        }
    }

    private fun normalizeBaseUrl(activeBaseUrl: String, isDebug: Boolean): String {
        val cleaned = activeBaseUrl.trim().removeSuffix("/")
        return if (!isDebug && cleaned.startsWith("http://", ignoreCase = true)) {
            "https://${cleaned.substring(7)}"
        } else {
            cleaned
        }
    }

    private fun containsLocalHost(url: String): Boolean {
        val host = extractHost(url, parseUriSafe(url))
        return host in LOCAL_HOSTS
    }

    private fun ensureHttpsForExternal(url: String): String {
        val host = extractHost(url, parseUriSafe(url))
        if (host in LOCAL_HOSTS) return url
        return if (url.startsWith("http://", ignoreCase = true)) {
            "https://${url.substring(7)}"
        } else {
            url
        }
    }

    private fun isKnownBackendAssetOrProxyPath(pathAndQuery: String): Boolean {
        return pathAndQuery.startsWith("/images/", ignoreCase = true) ||
            pathAndQuery.startsWith("/api/proxy-image", ignoreCase = true) ||
            pathAndQuery.startsWith("/api/image-proxy", ignoreCase = true)
    }

    private fun parseUriSafe(url: String): URI? {
        return try {
            URI(url)
        } catch (_: Exception) {
            try {
                URI(sanitizeUrlForUriParsing(url))
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun sanitizeUrlForUriParsing(url: String): String {
        return buildString(url.length) {
            for (ch in url) {
                when (ch) {
                    ' ' -> append("%20")
                    '[' -> append("%5B")
                    ']' -> append("%5D")
                    '{' -> append("%7B")
                    '}' -> append("%7D")
                    '|' -> append("%7C")
                    '^' -> append("%5E")
                    '\\' -> append("%5C")
                    '"' -> append("%22")
                    '<' -> append("%3C")
                    '>' -> append("%3E")
                    else -> append(ch)
                }
            }
        }
    }

    private val HOST_FALLBACK_REGEX = Regex("^https?://([^/:?#]+)", RegexOption.IGNORE_CASE)
    private val PATH_AND_QUERY_FALLBACK_REGEX = Regex("^https?://[^/?#]+([/?].*)$", RegexOption.IGNORE_CASE)

    private fun extractHost(url: String, parsedUri: URI? = null): String {
        val uriHost = parsedUri?.host?.lowercase()?.takeIf { it.isNotBlank() }
        if (uriHost != null) return uriHost
        val matched = HOST_FALLBACK_REGEX.find(url)?.groupValues?.getOrNull(1).orEmpty()
        return matched.substringAfterLast('@').lowercase()
    }

    private fun extractPathAndQuery(url: String, parsedUri: URI?): String {
        if (parsedUri != null) {
            return buildPathAndQuery(parsedUri)
        }
        val rawTail = PATH_AND_QUERY_FALLBACK_REGEX.find(url)?.groupValues?.getOrNull(1).orEmpty()
        if (rawTail.isBlank()) return ""
        val withoutFragment = rawTail.substringBefore('#')
        return sanitizeUrlForUriParsing(withoutFragment)
    }

    private fun buildPathAndQuery(uri: URI?): String {
        if (uri == null) return ""
        val path = uri.rawPath.orEmpty()
        val query = uri.rawQuery?.let { "?$it" }.orEmpty()
        return "$path$query"
    }

    private fun extractWsrvTargetUrl(uri: URI?): String? {
        val query = uri?.rawQuery ?: return null
        val pairs = query.split("&")
        for (pair in pairs) {
            val idx = pair.indexOf('=')
            if (idx > 0 && pair.substring(0, idx) == "url") {
                val decoded = runCatching { URLDecoder.decode(pair.substring(idx + 1), "UTF-8") }.getOrNull()
                if (!decoded.isNullOrBlank()) {
                    return if (isBlockedCommunity(decoded)) decoded else ensureHttpsForExternal(decoded)
                }
            }
        }
        return null
    }
}
