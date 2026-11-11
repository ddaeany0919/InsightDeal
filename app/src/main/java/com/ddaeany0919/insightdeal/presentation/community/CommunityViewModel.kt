package com.ddaeany0919.insightdeal.presentation.community

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddaeany0919.insightdeal.data.CommunityRepository
import com.ddaeany0919.insightdeal.data.CommunityVoteStore
import com.ddaeany0919.insightdeal.data.HotDealDto
import com.ddaeany0919.insightdeal.data.network.CommunityPostCreateReq
import com.ddaeany0919.insightdeal.data.network.CommunityPostDto
import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.network.ApiService
import com.ddaeany0919.insightdeal.network.NetworkModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

sealed class CommunityUiState {
    object Loading : CommunityUiState()
    data class Success(val deals: List<HotDealDto>) : CommunityUiState()
    data class Error(val message: String) : CommunityUiState()
}

class CommunityViewModel : ViewModel() {
    private val repository = CommunityRepository()

    private val _uiState = MutableStateFlow<CommunityUiState>(CommunityUiState.Loading)
    val uiState: StateFlow<CommunityUiState> = _uiState.asStateFlow()

    init {
        loadHotDeals()
    }

    fun loadHotDeals(category: String? = null) {
        viewModelScope.launch {
            _uiState.value = CommunityUiState.Loading
            try {
                val deals = repository.getHotDeals(category)
                _uiState.value = CommunityUiState.Success(deals)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("CommunityViewModel", "Error fetching deals", e)
                _uiState.value = CommunityUiState.Error("핫딜 정보를 불러오지 못했어요.")
            }
        }
    }
}

sealed class CommunityBoardUiState {
    object Loading : CommunityBoardUiState()
    data class Success(val posts: List<CommunityPostDto>) : CommunityBoardUiState()
    data class Error(val message: String) : CommunityBoardUiState()
}

class CommunityBoardViewModel : ViewModel() {
    private val api = NetworkModule.createService<ApiService>()

    private val _uiState = MutableStateFlow<CommunityBoardUiState>(CommunityBoardUiState.Loading)
    val uiState: StateFlow<CommunityBoardUiState> = _uiState.asStateFlow()

    private val _linkedDeals = MutableStateFlow<Map<Int, DealItem>>(emptyMap())
    val linkedDeals: StateFlow<Map<Int, DealItem>> = _linkedDeals.asStateFlow()

    private val inFlightDealIds = ConcurrentHashMap.newKeySet<Int>()

    fun parseLinkedDealId(location: String?): Int? {
        val trimmed = location?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return if (trimmed.startsWith("deal_id:")) {
            trimmed.removePrefix("deal_id:").trim().toIntOrNull()
        } else {
            trimmed.toIntOrNull()
        }
    }

    fun getUserVote(postId: Int): String? = CommunityVoteStore.getVote(postId)

    fun loadPosts(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) {
                _uiState.value = CommunityBoardUiState.Loading
            }
            try {
                val response = api.getCommunityPosts(postType = "REQUEST")
                val posts = response.body()
                if (response.isSuccessful && posts != null) {
                    _uiState.value = CommunityBoardUiState.Success(posts)
                    posts.mapNotNull { parseLinkedDealId(it.location) }
                        .distinct()
                        .forEach { dealId -> ensureLinkedDealLoaded(dealId) }
                } else {
                    _uiState.value = CommunityBoardUiState.Error("게시글을 불러오지 못했어요.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = CommunityBoardUiState.Error("네트워크 연결을 확인해 주세요.")
            }
        }
    }

    fun ensureLinkedDealLoaded(location: String?) {
        val dealId = parseLinkedDealId(location) ?: return
        ensureLinkedDealLoaded(dealId)
    }

    private fun ensureLinkedDealLoaded(dealId: Int) {
        if (dealId <= 0) return
        if (_linkedDeals.value.containsKey(dealId)) return
        if (!inFlightDealIds.add(dealId)) return

        viewModelScope.launch {
            try {
                val response = api.getEnhancedDealInfo(dealId)
                val body = response.body()
                if (response.isSuccessful && body != null) {
                    _linkedDeals.update { current -> current + (dealId to body) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            } finally {
                inFlightDealIds.remove(dealId)
            }
        }
    }

    fun votePost(
        post: CommunityPostDto,
        isBuy: Boolean,
        oldVote: String?,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                var newBuy = post.bountyPoints
                var newSkip = post.likeCount

                if (oldVote == "BUY") newBuy = (newBuy - 1).coerceAtLeast(0)
                if (oldVote == "SKIP") newSkip = (newSkip - 1).coerceAtLeast(0)

                if (isBuy) newBuy += 1 else newSkip += 1

                val req = CommunityPostCreateReq(
                    postType = post.postType,
                    userId = post.userId,
                    nickname = post.nickname,
                    title = post.title,
                    content = post.content,
                    targetPrice = post.targetPrice,
                    bountyPoints = newBuy,
                    likeCount = newSkip,
                    location = post.location
                )
                val response = api.updateCommunityPost(post.id, req)
                if (response.isSuccessful) {
                    CommunityVoteStore.saveVote(post.id, if (isBuy) "BUY" else "SKIP")
                    onComplete()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    fun cancelVotePost(
        post: CommunityPostDto,
        currentVote: String,
        onComplete: () -> Unit
    ) {
        viewModelScope.launch {
            try {
                val newBuy = if (currentVote == "BUY") (post.bountyPoints - 1).coerceAtLeast(0) else post.bountyPoints
                val newSkip = if (currentVote == "SKIP") (post.likeCount - 1).coerceAtLeast(0) else post.likeCount

                val req = CommunityPostCreateReq(
                    postType = post.postType,
                    userId = post.userId,
                    nickname = post.nickname,
                    title = post.title,
                    content = post.content,
                    targetPrice = post.targetPrice,
                    bountyPoints = newBuy,
                    likeCount = newSkip,
                    location = post.location
                )
                val response = api.updateCommunityPost(post.id, req)
                if (response.isSuccessful) {
                    CommunityVoteStore.clearVote(post.id)
                    onComplete()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }
}
