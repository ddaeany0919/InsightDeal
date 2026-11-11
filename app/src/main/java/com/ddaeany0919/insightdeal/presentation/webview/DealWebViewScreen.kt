package com.ddaeany0919.insightdeal.presentation.webview

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.ddaeany0919.insightdeal.utils.CustomTabLauncher

private const val CLEAN_MOBILE_UA = "Mozilla/5.0 (Linux; Android 14; SM-S928N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
private const val DESKTOP_CHROME_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

private val SHOPPING_SCHEME_PACKAGES = mapOf(
    "coupang" to "com.coupang.mobile",
    "coupangapp" to "com.coupang.mobile",
    "elevenstreet" to "com.elevenst",
    "elevenst" to "com.elevenst",
    "st11" to "com.elevenst",
    "gmarket" to "com.ebay.kr.gmarket",
    "auction" to "com.ebay.kr.auction",
    "lotteon" to "com.lotte.lotteon",
    "ssg" to "kr.co.ssg",
    "aliexpress" to "com.alibaba.aliexpresshd",
    "naversearchapp" to "com.nhn.android.search"
)

fun resolveMobileWebViewUrl(rawUrl: String): String {
    var trimmed = rawUrl.trim().replace(":443", "")
    if (trimmed.isBlank()) return ""
    if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
        trimmed = "https://$trimmed"
    }

    if (trimmed.contains("bbasak.com/bbs/board.php", ignoreCase = true) &&
        trimmed.contains("wr_id=", ignoreCase = true) &&
        !trimmed.contains("#")
    ) {
        trimmed = "$trimmed#view_content"
    }

    val auctionMatch = Regex("itempage3?\\.auction\\.co\\.kr/DetailView\\.aspx\\?.*itemno=([A-Za-z0-9]+)", RegexOption.IGNORE_CASE).find(trimmed)
    if (auctionMatch != null) {
        return "http://mitem.auction.co.kr/vip?itemNo=${auctionMatch.groupValues[1]}"
    }

    val gmarketMatch = Regex("item\\.gmarket\\.co\\.kr/Item\\?.*goodscode=([0-9]+)", RegexOption.IGNORE_CASE).find(trimmed)
    if (gmarketMatch != null) {
        return "http://mitem.gmarket.co.kr/vi/product/${gmarketMatch.groupValues[1]}"
    }

    val st11Match = Regex("www\\.11st\\.co\\.kr/products/([0-9]+)", RegexOption.IGNORE_CASE).find(trimmed)
    if (st11Match != null) {
        return "https://m.11st.co.kr/products/m/${st11Match.groupValues[1]}"
    }

    if (trimmed.contains("://www.coupang.com/vp/products/", ignoreCase = true)) {
        return trimmed.replace(Regex("://www\\.coupang\\.com/vp/products/", RegexOption.IGNORE_CASE), "://m.coupang.com/vm/products/")
    }

    val ppomppuMatch = Regex("www\\.ppomppu\\.co\\.kr/zboard/(?:view|zboard)\\.php\\?.*id=([^&]+).*no=([0-9]+)", RegexOption.IGNORE_CASE).find(trimmed)
    if (ppomppuMatch != null) {
        return "https://m.ppomppu.co.kr/new/bbs_view.php?id=${ppomppuMatch.groupValues[1]}&no=${ppomppuMatch.groupValues[2]}"
    }

    if (trimmed.contains("://www.clien.net/service/board/", ignoreCase = true)) {
        return trimmed.replace("://www.clien.net/", "://m.clien.net/")
    }

    return trimmed
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DealWebViewScreen(
    url: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var webView: WebView? by remember { mutableStateOf(null) }
    var canGoBack by remember { mutableStateOf(false) }

    BackHandler(enabled = canGoBack) {
        if (webView?.canGoBack() == true) {
            webView?.goBack()
        } else {
            canGoBack = false
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("원본 게시글") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (webView?.canGoBack() == true) {
                            webView?.goBack()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val targetUrl = webView?.url ?: url
                        CustomTabLauncher.launchShoppingUrl(context, targetUrl)
                    }) {
                        Icon(Icons.Default.OpenInBrowser, contentDescription = "브라우저로 열기")
                    }
                }
            )
        }
    ) { padding ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewClient = object : WebViewClient() {
                        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                            super.doUpdateVisitedHistory(view, url, isReload)
                            canGoBack = view?.canGoBack() == true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            canGoBack = view?.canGoBack() == true
                            if (url?.contains("bbasak.com/bbs/board.php") == true && url.contains("wr_id=")) {
                                view?.evaluateJavascript(
                                    "(function(){var el=document.getElementById('view_content')||document.querySelector('.view_content,.board_view');if(el){el.scrollIntoView({behavior:'instant',block:'start'});}})();",
                                    null
                                )
                            }
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val requestUrl = request?.url?.toString().orEmpty()
                            if (requestUrl.isBlank()) return false

                            if (requestUrl.startsWith("intent://", ignoreCase = true)) {
                                try {
                                    val intent = Intent.parseUri(requestUrl, Intent.URI_INTENT_SCHEME).apply {
                                        addCategory(Intent.CATEGORY_BROWSABLE)
                                        component = null
                                        selector = null
                                        if (context !is android.app.Activity) {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    }
                                    val fallbackUrl = intent.getStringExtra("browser_fallback_url")?.takeIf {
                                        it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
                                    }
                                    try {
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        if (fallbackUrl != null) {
                                            view?.loadUrl(fallbackUrl)
                                        } else {
                                            val packageName = intent.`package`
                                            if (!packageName.isNullOrBlank()) {
                                                try {
                                                    val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                                                        addCategory(Intent.CATEGORY_BROWSABLE)
                                                        component = null
                                                        selector = null
                                                        if (context !is android.app.Activity) {
                                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                        }
                                                    }
                                                    context.startActivity(playStoreIntent)
                                                } catch (_: Exception) {
                                                    // Ignore
                                                }
                                            }
                                        }
                                    }
                                } catch (_: Exception) {
                                    // Ignore malformed intent scheme
                                }
                                return true
                            }

                            if (!requestUrl.startsWith("http://", ignoreCase = true) &&
                                !requestUrl.startsWith("https://", ignoreCase = true)
                            ) {
                                val parsedUri = runCatching { Uri.parse(requestUrl) }.getOrNull()
                                val scheme = parsedUri?.scheme?.lowercase().orEmpty()
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, parsedUri ?: Uri.parse(requestUrl)).apply {
                                        addCategory(Intent.CATEGORY_BROWSABLE)
                                        component = null
                                        selector = null
                                        if (context !is android.app.Activity) {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    }
                                    context.startActivity(intent)
                                } catch (_: ActivityNotFoundException) {
                                    val fallbackPkg = SHOPPING_SCHEME_PACKAGES[scheme]
                                    if (fallbackPkg != null) {
                                        runCatching {
                                            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$fallbackPkg")).apply {
                                                addCategory(Intent.CATEGORY_BROWSABLE)
                                                component = null
                                                selector = null
                                                if (context !is android.app.Activity) {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                }
                                            }
                                            context.startActivity(marketIntent)
                                        }
                                    }
                                } catch (_: Exception) {
                                    // Ignore
                                }
                                return true
                            }

                            return super.shouldOverrideUrlLoading(view, request)
                        }
                    }

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        setSupportZoom(true)
                        builtInZoomControls = true
                        displayZoomControls = false
                        allowFileAccess = false
                        allowContentAccess = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    }

                    val resolvedUrl = resolveMobileWebViewUrl(url)
                    settings.userAgentString = if (resolvedUrl.contains("bbasak.com")) {
                        DESKTOP_CHROME_UA
                    } else {
                        CLEAN_MOBILE_UA
                    }

                    webView = this

                    val extraHeaders = mutableMapOf<String, String>()
                    if (resolvedUrl.contains("naver.com") || resolvedUrl.contains("fmkorea.com") || resolvedUrl.contains("clien.net") || resolvedUrl.contains("quasarzone.com")) {
                        extraHeaders["Referer"] = "https://search.naver.com"
                    } else if (resolvedUrl.contains("auction.co.kr")) {
                        extraHeaders["Referer"] = "https://m.auction.co.kr"
                    } else if (resolvedUrl.contains("gmarket.co.kr")) {
                        extraHeaders["Referer"] = "https://m.gmarket.co.kr"
                    }

                    loadUrl(resolvedUrl, extraHeaders)
                }
            },
            update = {}
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            webView?.apply {
                stopLoading()
                loadUrl("about:blank")
                clearHistory()
                removeAllViews()
                destroy()
            }
            webView = null
        }
    }
}
