package com.ddaeany0919.insightdeal.presentation.webview

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DealWebViewScreen(
    url: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var webView: WebView? by remember { mutableStateOf(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("상세보기") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (webView?.canGoBack() == true) {
                            webView?.goBack()
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(webView?.url ?: url))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            // Ignore
                        }
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
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val requestUrl = request?.url?.toString() ?: ""
                            
                            if (requestUrl.startsWith("intent://")) {
                                try {
                                    val intent = Intent.parseUri(requestUrl, Intent.URI_INTENT_SCHEME)
                                    // 1. 앱이 설치되어 있으면 앱 실행
                                    if (intent.resolveActivity(context.packageManager) != null) {
                                        context.startActivity(intent)
                                        return true
                                    }
                                    // 2. 앱이 없으면 fallback URL로 이동 (웹페이지)
                                    val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                                    if (fallbackUrl != null) {
                                        view?.loadUrl(fallbackUrl)
                                        return true
                                    }
                                    // 3. fallback URL도 없으면 플레이스토어로 이동
                                    val packageName = intent.`package`
                                    if (packageName != null) {
                                        val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
                                        if (playStoreIntent.resolveActivity(context.packageManager) != null) {
                                            context.startActivity(playStoreIntent)
                                            return true
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                // 앱 스키마 오류 시 웹뷰 기본 에러(ERR_UNKNOWN_URL_SCHEME) 방지
                                return true
                            } else if (!requestUrl.startsWith("http://") && !requestUrl.startsWith("https://")) {
                                // 일반적인 외부 앱 스키마 (market:// 등)
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(requestUrl))
                                    if (intent.resolveActivity(context.packageManager) != null) {
                                        context.startActivity(intent)
                                        return true
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
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
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }

                    // 봇 차단(네이버 스마트스토어, 클라우드플레어 등)을 완벽히 우회하기 위해 
                    // 일반 모바일 크롬 브라우저와 완전히 동일한 User-Agent 사용
                    val cleanMobileUA = "Mozilla/5.0 (Linux; Android 14; SM-S928N) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                    
                    if (url.contains("bbasak.com")) {
                        // 빠삭은 PC 버전 필수
                        settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                    } else {
                        // 스마트스토어 등 나머지 모든 사이트는 순수 모바일 크롬 UA 사용
                        settings.userAgentString = cleanMobileUA
                    }

                    webView = this
                    
                    // 네이버 스마트스토어 등 봇 차단 우회를 위한 헤더 조작
                    val extraHeaders = mutableMapOf<String, String>()
                    if (url.contains("naver.com") || url.contains("fmkorea.com") || url.contains("clien.net") || url.contains("quasarzone.com")) {
                        extraHeaders["Referer"] = "https://search.naver.com"
                    }

                    loadUrl(url, extraHeaders)
                }
            },
            update = {
                // Ignore
            }
        )
    }
}
