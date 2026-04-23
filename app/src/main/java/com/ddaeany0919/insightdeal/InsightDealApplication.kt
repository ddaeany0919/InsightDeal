package com.ddaeany0919.insightdeal

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.util.DebugLogger
import com.ddaeany0919.insightdeal.network.NetworkModule

class InsightDealApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .crossfade(true)
            .logger(DebugLogger())
            .okHttpClient { NetworkModule.okHttpClient }
            .build()
    }
}
