package com.ddaeany0919.insightdeal

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.util.DebugLogger
import com.ddaeany0919.insightdeal.network.NetworkModule

class InsightDealApplication : Application(), ImageLoaderFactory {
    companion object {
        lateinit var instance: InsightDealApplication
            private set
        
        val database: com.ddaeany0919.insightdeal.local.db.AppDatabase by lazy {
            com.ddaeany0919.insightdeal.local.db.AppDatabase.getDatabase(instance)
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .crossfade(true)
            .logger(DebugLogger())
            .okHttpClient { NetworkModule.okHttpClient }
            .build()
    }
}
