package com.ddaeany0919.insightdeal.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent

object CustomTabLauncher {

    private val KNOWN_SHOPPING_SCHEMES = mapOf(
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

    fun launchShoppingUrl(context: Context, rawUrl: String) {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return

        if (trimmed.startsWith("intent://", ignoreCase = true)) {
            handleIntentScheme(context, trimmed)
            return
        }

        val uri = try {
            Uri.parse(trimmed)
        } catch (_: Exception) {
            return
        }
        val scheme = uri.scheme?.lowercase().orEmpty()

        if (scheme.isNotEmpty() && scheme != "http" && scheme != "https") {
            launchExternalAppScheme(context, uri, KNOWN_SHOPPING_SCHEMES[scheme])
            return
        }

        val normalizedHttpUrl = if (scheme.isEmpty()) "https://$trimmed" else trimmed
        val httpUri = Uri.parse(normalizedHttpUrl)

        try {
            val customTabsIntent = CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setShareState(CustomTabsIntent.SHARE_STATE_ON)
                .build()
            if (context !is Activity) {
                customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            customTabsIntent.launchUrl(context, httpUri)
        } catch (_: Exception) {
            launchBrowserFallback(context, httpUri)
        }
    }

    private fun handleIntentScheme(context: Context, intentUrl: String) {
        try {
            val intent = Intent.parseUri(intentUrl, Intent.URI_INTENT_SCHEME).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                component = null
                selector = null
                if (context !is Activity) {
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
                    launchShoppingUrl(context, fallbackUrl)
                } else {
                    openPlayStore(context, intent.`package`)
                }
            }
        } catch (_: Exception) {
            // Ignore malformed intent URI
        }
    }

    private fun launchExternalAppScheme(context: Context, uri: Uri, fallbackPackage: String?) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                component = null
                selector = null
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            openPlayStore(context, fallbackPackage)
        } catch (_: Exception) {
            openPlayStore(context, fallbackPackage)
        }
    }

    private fun launchBrowserFallback(context: Context, uri: Uri) {
        try {
            val fallbackIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                component = null
                selector = null
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(fallbackIntent)
        } catch (_: Exception) {
            // Ignore if no browser available
        }
    }

    private fun openPlayStore(context: Context, packageName: String?) {
        if (packageName.isNullOrBlank()) return
        try {
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                component = null
                selector = null
                if (context !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(marketIntent)
        } catch (_: Exception) {
            launchBrowserFallback(context, Uri.parse("https://play.google.com/store/apps/details?id=$packageName"))
        }
    }
}
