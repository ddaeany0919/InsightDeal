package com.ddaeany0919.insightdeal

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
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
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25) // 앱 가용 메모리의 25%를 메모리 캐시로 할당
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache_dnd_7d"))
                    .maxSizeBytes(100L * 1024L * 1024L) // 100MB 크기 제한으로 VRAM/디스크 가드
                    .build()
            }
            .logger(DebugLogger())
            .okHttpClient { NetworkModule.okHttpClient }
            .build()
    }
}
