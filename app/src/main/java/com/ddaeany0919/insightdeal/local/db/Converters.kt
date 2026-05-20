package com.ddaeany0919.insightdeal.local.db

import androidx.room.TypeConverter
import com.ddaeany0919.insightdeal.models.DealSource
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * 📊 Room DB TypeConverters
 * - JSON 직렬화/역직렬화 시 가비지 컬렉션 부하를 막기 위해 Gson 싱글톤을 재사용합니다.
 */
class Converters {
    companion object {
        private val gson = Gson()
    }

    @TypeConverter
    fun fromTimestamp(value: String?): LocalDateTime? {
        return value?.let { LocalDateTime.parse(it, DateTimeFormatter.ISO_LOCAL_DATE_TIME) }
    }

    @TypeConverter
    fun dateToTimestamp(date: LocalDateTime?): String? {
        return date?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }

    // 🏷️ List<String> JSON 변환기
    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return value?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        if (value.isNullOrEmpty()) return emptyList()
        val listType = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, listType)
    }

    // 🛍️ List<DealSource> JSON 변환기
    @TypeConverter
    fun fromSourceList(value: List<DealSource>?): String? {
        return value?.let { gson.toJson(it) }
    }

    @TypeConverter
    fun toSourceList(value: String?): List<DealSource>? {
        if (value.isNullOrEmpty()) return emptyList()
        val listType = object : TypeToken<List<DealSource>>() {}.type
        return gson.fromJson(value, listType)
    }
}
