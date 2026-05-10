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
    var pendingIntent: Intent? by remember { mutableStateOf(null) }
    var pendingFallbackUrl: String? by remember { mutableStateOf(null) }

    if (pendingIntent != null) {
        AlertDialog(
            onDismissRequest = { 
                pendingIntent = null
                pendingFallbackUrl = null
            },
            title = { Text("쇼핑몰 연결", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
            text = { Text("해당 쇼핑몰 앱으로 열면 더 편리하게 구매하실 수 있습니다.\n어떻게 이동하시겠습니까?") },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            context.startActivity(pendingIntent!!)
                        } catch (e: Exception) {
                            val packageName = pendingIntent!!.`package`
                            if (packageName != null) {
                                try {
                                    val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
                                    context.startActivity(playStoreIntent)
                                } catch (e2: Exception) {
                                    e2.printStackTrace()
                                }
                            }
                        }
                        pendingIntent = null
                        pendingFallbackUrl = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.ui.graphics.Color(0xFFFF6D00))
                ) {
                    Text("App에서 보기")
                }
            },
            dismissButton = {
                if (pendingFallbackUrl != null) {
                    OutlinedButton(onClick = {
                        webView?.loadUrl(pendingFallbackUrl!!)
                        pendingIntent = null
                        pendingFallbackUrl = null
                    }) {
                        Text("웹에서 보기")
                    }
                } else {
                    OutlinedButton(onClick = {
                        pendingIntent = null
                        pendingFallbackUrl = null
                    }) {
                        Text("취소")
                    }
                }
            }
        )
    }

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
                                    val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                                    
                                    if (fallbackUrl != null) {
                                        // 1. fallback URL(웹)이 있다면 다이얼로그를 띄워 물어본다
                                        pendingIntent = intent
                                        pendingFallbackUrl = fallbackUrl
                                        return true
                                    } else {
                                        // 2. fallback URL이 없다면 선택의 여지가 없으므로 바로 앱 실행
                                        try {
                                            context.startActivity(intent)
                                            return true
                                        } catch (e: Exception) {
                                            val packageName = intent.`package`
                                            if (packageName != null) {
                                                try {
                                                    val playStoreIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
                                                    context.startActivity(playStoreIntent)
                                                } catch (e2: Exception) {
                                                    e2.printStackTrace()
                                                }
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                // 앱 스키마 오류 시 웹뷰 기본 에러(ERR_UNKNOWN_URL_SCHEME) 방지
                                return true
                            } else if (!requestUrl.startsWith("http://") && !requestUrl.startsWith("https://")) {
                                // 일반적인 외부 앱 스키마 (market://, kakaotalk://, toss:// 등)
                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(requestUrl))
                                    context.startActivity(intent)
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
