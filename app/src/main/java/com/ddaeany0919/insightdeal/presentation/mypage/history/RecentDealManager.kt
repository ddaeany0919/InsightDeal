package com.ddaeany0919.insightdeal.presentation.mypage.history

import android.content.Context
import com.ddaeany0919.insightdeal.models.DealItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object RecentDealManager {
    private const val PREF_NAME = "recent_deals_prefs"
    private const val KEY_RECENT_DEALS = "recent_deals"
    private val gson = Gson()

    private val _recentDeals = MutableStateFlow<List<DealItem>>(emptyList())
    val recentDeals: StateFlow<List<DealItem>> = _recentDeals.asStateFlow()

    fun init(context: Context) {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_RECENT_DEALS, null)
        if (json != null) {
            val type = object : TypeToken<List<DealItem>>() {}.type
            try {
                val list: List<DealItem> = gson.fromJson(json, type)
                _recentDeals.value = list
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addRecentDeal(context: Context, deal: DealItem) {
        val currentList = _recentDeals.value.toMutableList()
        // Remove if already exists to move it to top
        currentList.removeAll { it.id == deal.id }
        // Add to top
        currentList.add(0, deal)
        // Keep only top 50
        val newList = currentList.take(50)
        _recentDeals.value = newList

        // Save to SharedPreferences
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_RECENT_DEALS, gson.toJson(newList)).apply()
    }

    fun removeRecentDeal(context: Context, dealId: Int) {
        val newList = _recentDeals.value.filter { it.id != dealId }
        _recentDeals.value = newList
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_RECENT_DEALS, gson.toJson(newList)).apply()
    }

    fun clearRecentDeals(context: Context) {
        _recentDeals.value = emptyList()
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_RECENT_DEALS).apply()
    }
}
