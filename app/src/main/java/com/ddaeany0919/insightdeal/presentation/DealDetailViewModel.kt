package com.ddaeany0919.insightdeal.presentation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddaeany0919.insightdeal.data.LocalDataProvider
import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.models.DealSource
import com.ddaeany0919.insightdeal.models.PriceHistoryPoint
import com.ddaeany0919.insightdeal.network.ApiCommentCreate
import com.ddaeany0919.insightdeal.network.ApiDealComment
import com.ddaeany0919.insightdeal.network.ApiDealVotes
import com.ddaeany0919.insightdeal.network.ApiService
import com.ddaeany0919.insightdeal.network.ApiVoteCreate
import com.ddaeany0919.insightdeal.network.NetworkModule
import com.ddaeany0919.insightdeal.network.TrackClickRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

@Immutable
data class DealDetailUiState(
    val isLoading: Boolean = true,
    val deal: DealItem? = null,
    val currentDeviceUuid: String = "",
    val selectedOptionIndex: Int = 0,
    val priceHistory: List<PriceHistoryPoint> = emptyList(),
    val isHistoryLoading: Boolean = false,
    val error: String? = null,
    val isRecordLow: Boolean = false,
    // 1-RTT 요약 필드 (상세 진입 즉시 반영)
    val buyCount: Int = 0,
    val passCount: Int = 0,
    val userVote: String? = null,
    val commentCount: Int = 0,
    val historyCount: Int = 0,
    val isSearchFallback: Boolean = false,
    val isFree: Boolean = false,
    // P1-06 품절/종료 배지 및 원클릭 품절 제보 상태
    val isClosed: Boolean = false,
    val reportCount: Int = 0,
    val isReportingClosed: Boolean = false,
    val hasReportedClosed: Boolean = false,
    val reportClosedFeedback: String? = null,
    val reportClosedFeedbackSeq: Int = 0,
    // 2급 [안 B] 아코디언 Lazy-fetch & CRUD 상태
    val comments: List<ApiDealComment> = emptyList(),
    val commentsLoaded: Boolean = false,
    val isCommentsLoading: Boolean = false,
    val commentsError: String? = null,
    val votes: ApiDealVotes? = null,
    val isVoting: Boolean = false,
    val voteError: String? = null,
    val isSubmittingComment: Boolean = false,
    val deletingCommentId: Int? = null,
    val commentActionError: String? = null,
    val commentSubmitSuccessSeq: Int = 0
)

class DealDetailViewModel(
    private val apiService: ApiService = NetworkModule.createService<ApiService>(),
    private val deviceUuidProvider: () -> String = {
        runCatching { LocalDataProvider.getDeviceUuid() }.getOrDefault("")
    },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val coroutineScopeOverride: CoroutineScope? = null
) : ViewModel() {

    private val vmScope: CoroutineScope
        get() = coroutineScopeOverride ?: viewModelScope

    private val _uiState = MutableStateFlow(DealDetailUiState())
    val uiState: StateFlow<DealDetailUiState> = _uiState.asStateFlow()

    private val _selectedPeriod = MutableStateFlow("7d")
    val selectedPeriod: StateFlow<String> = _selectedPeriod.asStateFlow()

    private var currentDealId: Int? = null
    private var detailJob: Job? = null
    private var historyJob: Job? = null
    private var reportJob: Job? = null
    private val historyCache = ConcurrentHashMap<String, List<PriceHistoryPoint>>()

    private fun resolveDeviceUuid(): String {
        return runCatching { deviceUuidProvider().trim() }.getOrDefault("")
    }

    fun setPeriod(period: String) {
        if (_selectedPeriod.value != period) {
            _selectedPeriod.value = period
            currentDealId?.let { loadHistoryData(it, period) }
        }
    }

    fun selectOption(index: Int) {
        if (_uiState.value.deal == null) return
        if (_uiState.value.selectedOptionIndex == index) return
        _uiState.value = _uiState.value.copy(selectedOptionIndex = index)
        currentDealId?.let { dealId ->
            loadHistoryData(dealId, _selectedPeriod.value)
        }
    }

    /**
     * 쇼핑몰 구매 링크 클릭 시 비동기(Fire-and-forget)로 /api/coupang/track-click 수익화 집계를 전송합니다.
     */
    fun trackOutboundClick(
        url: String,
        price: Long? = null,
        currency: String? = null
    ) {
        val trimmedUrl = url.trim()
        if (trimmedUrl.isEmpty()) return
        val deal = _uiState.value.deal
        val resolvedDealId = currentDealId ?: deal?.id
        val resolvedPrice = (price ?: deal?.price ?: 0L).coerceAtLeast(0L).toInt()
        val resolvedCurrency = (currency ?: deal?.currency ?: "KRW").ifBlank { "KRW" }

        vmScope.launch(ioDispatcher) {
            try {
                apiService.trackOutboundClick(
                    TrackClickRequest(
                        url = trimmedUrl,
                        dealId = resolvedDealId,
                        price = resolvedPrice,
                        currency = resolvedCurrency,
                        platform = "android"
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // 트래킹 실패가 구매 전환을 방해하지 않도록 무시
            }
        }
    }

    fun resolveMultiOptions(deal: DealItem?): List<DealSource> = extractValidMultiOptions(deal)

    private fun loadHistoryData(dealId: Int, period: String) {
        historyJob?.cancel()
        historyJob = vmScope.launch {
            _uiState.value = _uiState.value.copy(isHistoryLoading = true)
            try {
                val deal = _uiState.value.deal
                val multiOptions = resolveMultiOptions(deal)
                val selectedOpt = if (multiOptions.size > 1) multiOptions.getOrNull(_uiState.value.selectedOptionIndex) else null
                val optQuery = selectedOpt?.let { cleanOptionTitle(it.siteName) }?.takeIf { it.isNotBlank() }
                val optCurrency = selectedOpt?.currency ?: deal?.currency

                val cacheKey = "${dealId}_${optQuery ?: "default"}_${optCurrency}_$period"
                val cached = historyCache[cacheKey]
                if (cached != null) {
                    val validCached = cached.filter { it.price > 0 }
                    val currentPrice = selectedOpt?.price ?: deal?.price ?: 0L
                    val minPrice = validCached.minOfOrNull { it.price } ?: Int.MAX_VALUE
                    val isLow = currentPrice > 0 && validCached.isNotEmpty() && currentPrice <= minPrice
                    _uiState.value = _uiState.value.copy(
                        priceHistory = validCached,
                        isRecordLow = isLow,
                        isHistoryLoading = false
                    )
                    return@launch
                }

                val historyResponse = withContext(ioDispatcher) {
                    apiService.getDealPriceHistory(
                        dealId = dealId,
                        period = period,
                        query = optQuery,
                        currency = optCurrency
                    )
                }
                if (historyResponse.isSuccessful) {
                    val history = (historyResponse.body() ?: emptyList()).filter { it.price > 0 }
                    historyCache[cacheKey] = history
                    val currentPrice = selectedOpt?.price ?: deal?.price ?: 0L
                    val minPrice = history.minOfOrNull { it.price } ?: Int.MAX_VALUE
                    val isLow = currentPrice > 0 && history.isNotEmpty() && currentPrice <= minPrice
                    _uiState.value = _uiState.value.copy(
                        priceHistory = history,
                        isRecordLow = isLow,
                        isHistoryLoading = false
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isHistoryLoading = false)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(isHistoryLoading = false)
            }
        }
    }

    /**
     * 상세 진입 시 1-RTT 요약 필 기반으로 상세 정보를 로드합니다.
     * 초기 진입 시 불필요한 댓글(/comments)·투표(/votes) 선제 왕복을 제거했습니다.
     */
    fun loadDealData(dealId: Int) {
        currentDealId = dealId
        detailJob?.cancel()
        historyJob?.cancel()
        reportJob?.cancel()
        detailJob = vmScope.launch {
            val myUuid = resolveDeviceUuid()
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                error = null,
                currentDeviceUuid = myUuid
            )
            try {
                val requestedPeriod = if (_selectedPeriod.value == "7d") "7d_auto" else _selectedPeriod.value
                val dealResponse = withContext(ioDispatcher) {
                    apiService.getEnhancedDealInfo(
                        dealId = dealId,
                        userId = myUuid.ifBlank { null }
                    )
                }

                val deal = if (dealResponse.isSuccessful) dealResponse.body() else null
                if (deal != null) {
                    val multiOptions = resolveMultiOptions(deal)
                    val firstOpt = if (multiOptions.size > 1) multiOptions.firstOrNull() else null
                    val firstOptQuery = firstOpt?.let { cleanOptionTitle(it.siteName) }?.takeIf { it.isNotBlank() }
                    val firstOptCurrency = firstOpt?.currency ?: deal.currency

                    // 1-RTT 요약의 initialHistory가 존재하고 기본 옵션·기본 기간("7d")인 경우 추가 네트워크 왕복 생략
                    val initialHistoryPoints = deal.initialHistory
                    val history = if (
                        firstOptQuery == null &&
                        _selectedPeriod.value == "7d" &&
                        !initialHistoryPoints.isNullOrEmpty()
                    ) {
                        initialHistoryPoints.filter { it.price > 0 }
                    } else if (deal.historyCount > 0) {
                        // 1-RTT 요약의 historyCount가 0건인 경우 불필요한 가격 추이 API 호출 생략
                        val historyResponse = try {
                            withContext(ioDispatcher) {
                                apiService.getDealPriceHistory(
                                    dealId = dealId,
                                    period = requestedPeriod,
                                    query = firstOptQuery,
                                    currency = firstOptCurrency
                                )
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            null
                        }
                        if (historyResponse?.isSuccessful == true) {
                            (historyResponse.body() ?: emptyList()).filter { it.price > 0 }
                        } else {
                            emptyList()
                        }
                    } else {
                        emptyList()
                    }

                    if (_selectedPeriod.value == "7d" && history.size >= 2 && history.any { it.date.matches(Regex("^\\d{2}\\.\\d{2}\\.\\d{2}$")) }) {
                        _selectedPeriod.value = "3m"
                    }

                    val cacheKey = "${dealId}_${firstOptQuery ?: "default"}_${firstOptCurrency}_${_selectedPeriod.value}"
                    historyCache[cacheKey] = history

                    var isLow = false
                    if (history.isNotEmpty()) {
                        val currentPrice = firstOpt?.price ?: deal.price
                        val minPrice = history.minOfOrNull { it.price } ?: Int.MAX_VALUE
                        if (currentPrice > 0 && currentPrice <= minPrice) {
                            isLow = true
                        }
                    }

                    val summaryVotes = ApiDealVotes(
                        buy_count = deal.buyCount,
                        pass_count = deal.passCount,
                        user_vote = deal.userVote
                    )

                    _uiState.value = DealDetailUiState(
                        isLoading = false,
                        deal = deal,
                        currentDeviceUuid = myUuid,
                        selectedOptionIndex = 0,
                        priceHistory = history,
                        isHistoryLoading = false,
                        error = null,
                        isRecordLow = isLow,
                        buyCount = deal.buyCount,
                        passCount = deal.passCount,
                        userVote = deal.userVote,
                        commentCount = deal.commentCount,
                        historyCount = maxOf(deal.historyCount, history.size),
                        isSearchFallback = deal.isSearchFallback,
                        isFree = deal.isFree,
                        isClosed = deal.isClosed,
                        reportCount = deal.reportCount,
                        comments = emptyList(),
                        commentsLoaded = false,
                        isCommentsLoading = false,
                        commentsError = null,
                        votes = summaryVotes
                    )
                } else {
                    _uiState.value = DealDetailUiState(
                        isLoading = false,
                        currentDeviceUuid = myUuid,
                        error = STANDARD_DETAIL_ERROR_MESSAGE
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.value = DealDetailUiState(
                    isLoading = false,
                    currentDeviceUuid = myUuid,
                    error = STANDARD_DETAIL_ERROR_MESSAGE
                )
            }
        }
    }

    /**
     * [안 B] 한줄평 아코디언 펼침 시 Lazy-fetch로 1회 조회합니다.
     */
    fun loadCommentsIfNeeded(forceRefresh: Boolean = false) {
        val dealId = currentDealId ?: return
        val current = _uiState.value
        if (current.isCommentsLoading) return
        if (!forceRefresh && current.commentsLoaded) return

        vmScope.launch {
            _uiState.value = _uiState.value.copy(
                isCommentsLoading = true,
                commentsError = null,
                currentDeviceUuid = resolveDeviceUuid()
            )
            try {
                val response = withContext(ioDispatcher) {
                    apiService.getDealComments(dealId)
                }
                if (response.isSuccessful) {
                    val body = response.body() ?: emptyList()
                    _uiState.value = _uiState.value.copy(
                        comments = body,
                        commentsLoaded = true,
                        commentCount = body.size,
                        isCommentsLoading = false,
                        commentsError = null
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isCommentsLoading = false,
                        commentsError = "의견을 불러오지 못했어요. 잠시 후 다시 시도해 주세요."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCommentsLoading = false,
                    commentsError = "의견을 불러오지 못했어요. 잠시 후 다시 시도해 주세요."
                )
            }
        }
    }

    /**
     * 투표 상태 수동 재동기화 (오류 복구용)
     */
    fun refreshVotes() {
        val dealId = currentDealId ?: return
        if (_uiState.value.isVoting) return
        val myUuid = resolveDeviceUuid()

        vmScope.launch {
            _uiState.value = _uiState.value.copy(
                isVoting = true,
                voteError = null,
                currentDeviceUuid = myUuid
            )
            try {
                val response = withContext(ioDispatcher) {
                    apiService.getDealVotes(dealId, myUuid.ifBlank { null })
                }
                val body = if (response.isSuccessful) response.body() else null
                if (body != null) {
                    _uiState.value = _uiState.value.copy(
                        buyCount = body.buy_count,
                        passCount = body.pass_count,
                        userVote = body.user_vote,
                        votes = body,
                        isVoting = false,
                        voteError = null
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isVoting = false,
                        voteError = "투표 현황을 불러오지 못했어요. 잠시 후 다시 시도해 주세요."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    isVoting = false,
                    voteError = "투표 현황을 불러오지 못했어요. 잠시 후 다시 시도해 주세요."
                )
            }
        }
    }

    fun addComment(content: String) {
        val dealId = currentDealId ?: return
        val trimmed = content.trim()
        if (trimmed.isEmpty() || _uiState.value.isSubmittingComment) return

        val myUuid = resolveDeviceUuid()
        val nickname = if (myUuid.length >= 4) "회원_${myUuid.takeLast(4)}" else "익명의찰떡이"

        vmScope.launch {
            _uiState.value = _uiState.value.copy(
                isSubmittingComment = true,
                commentActionError = null,
                currentDeviceUuid = myUuid
            )
            try {
                val req = ApiCommentCreate(
                    user_id = myUuid,
                    content = trimmed,
                    nickname = nickname
                )
                val response = withContext(ioDispatcher) {
                    apiService.addDealComment(dealId, req)
                }
                val newComment = if (response.isSuccessful) response.body() else null
                if (newComment != null) {
                    val currentState = _uiState.value
                    val wasLoaded = currentState.commentsLoaded
                    val shouldRecoverComments = !wasLoaded && (currentState.commentCount > 0 || currentState.commentsError != null)
                    val updatedComments = listOf(newComment) + currentState.comments.filterNot { it.id == newComment.id }
                    val nextCommentCount = maxOf(currentState.commentCount + 1, updatedComments.size)
                    _uiState.value = currentState.copy(
                        comments = updatedComments,
                        commentCount = nextCommentCount,
                        commentsLoaded = wasLoaded,
                        isSubmittingComment = false,
                        commentActionError = null,
                        commentSubmitSuccessSeq = currentState.commentSubmitSuccessSeq + 1
                    )
                    if (shouldRecoverComments) {
                        loadCommentsIfNeeded(forceRefresh = true)
                    }
                } else {
                    _uiState.value = _uiState.value.copy(
                        isSubmittingComment = false,
                        commentActionError = "의견을 등록하지 못했어요. 잠시 후 다시 시도해 주세요."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSubmittingComment = false,
                    commentActionError = "의견을 등록하지 못했어요. 잠시 후 다시 시도해 주세요."
                )
            }
        }
    }

    fun deleteComment(commentId: Int) {
        val dealId = currentDealId ?: return
        if (_uiState.value.deletingCommentId != null) return
        val myUuid = resolveDeviceUuid()

        vmScope.launch {
            _uiState.value = _uiState.value.copy(
                deletingCommentId = commentId,
                commentActionError = null,
                currentDeviceUuid = myUuid
            )
            try {
                val response = withContext(ioDispatcher) {
                    apiService.deleteDealComment(dealId, commentId)
                }
                if (response.isSuccessful) {
                    val updatedComments = _uiState.value.comments.filterNot { it.id == commentId }
                    _uiState.value = _uiState.value.copy(
                        comments = updatedComments,
                        commentCount = (_uiState.value.commentCount - 1).coerceAtLeast(0),
                        deletingCommentId = null,
                        commentActionError = null
                    )
                } else {
                    val errorMsg = if (response.code() == 403) {
                        "본인이 작성한 의견만 삭제할 수 있어요."
                    } else {
                        "의견을 삭제하지 못했어요. 잠시 후 다시 시도해 주세요."
                    }
                    _uiState.value = _uiState.value.copy(
                        deletingCommentId = null,
                        commentActionError = errorMsg
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    deletingCommentId = null,
                    commentActionError = "의견을 삭제하지 못했어요. 잠시 후 다시 시도해 주세요."
                )
            }
        }
    }

    fun vote(voteType: String) {
        val dealId = currentDealId ?: return
        val normalizedTarget = if (voteType == "pass") "save" else voteType
        if (normalizedTarget != "buy" && normalizedTarget != "save") return

        val current = _uiState.value
        if (current.isVoting) return

        val myUuid = resolveDeviceUuid()
        val prevBuy = current.buyCount
        val prevPass = current.passCount
        val prevVote = current.userVote
        val prevVotesObj = current.votes

        val currentNormalized = if (prevVote == "pass") "save" else prevVote
        var nextBuy = prevBuy
        var nextPass = prevPass
        val nextVote: String? = if (currentNormalized == normalizedTarget) {
            if (normalizedTarget == "buy") {
                nextBuy = (nextBuy - 1).coerceAtLeast(0)
            } else {
                nextPass = (nextPass - 1).coerceAtLeast(0)
            }
            null
        } else {
            if (currentNormalized == "buy") {
                nextBuy = (nextBuy - 1).coerceAtLeast(0)
            } else if (currentNormalized == "save") {
                nextPass = (nextPass - 1).coerceAtLeast(0)
            }
            if (normalizedTarget == "buy") {
                nextBuy += 1
            } else {
                nextPass += 1
            }
            normalizedTarget
        }

        vmScope.launch {
            // 낙관적 업데이트 반영
            _uiState.value = _uiState.value.copy(
                buyCount = nextBuy,
                passCount = nextPass,
                userVote = nextVote,
                votes = ApiDealVotes(
                    buy_count = nextBuy,
                    pass_count = nextPass,
                    user_vote = nextVote
                ),
                isVoting = true,
                voteError = null,
                currentDeviceUuid = myUuid
            )
            try {
                val req = ApiVoteCreate(
                    user_id = myUuid,
                    vote_type = normalizedTarget
                )
                val response = withContext(ioDispatcher) {
                    apiService.addDealVote(dealId, req)
                }
                val serverVotes = if (response.isSuccessful) response.body() else null
                if (serverVotes != null) {
                    _uiState.value = _uiState.value.copy(
                        buyCount = serverVotes.buy_count,
                        passCount = serverVotes.pass_count,
                        userVote = serverVotes.user_vote,
                        votes = serverVotes,
                        isVoting = false,
                        voteError = null
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        buyCount = prevBuy,
                        passCount = prevPass,
                        userVote = prevVote,
                        votes = prevVotesObj,
                        isVoting = false,
                        voteError = "투표를 반영하지 못했어요. 잠시 후 다시 시도해 주세요."
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    buyCount = prevBuy,
                    passCount = prevPass,
                    userVote = prevVote,
                    votes = prevVotesObj,
                    isVoting = false,
                    voteError = "투표를 반영하지 못했어요. 잠시 후 다시 시도해 주세요."
                )
            }
        }
    }

    fun clearActionError() {
        _uiState.value = _uiState.value.copy(
            voteError = null,
            commentActionError = null,
            commentsError = null,
            reportClosedFeedback = null
        )
    }

    /**
     * 원클릭 품절/종료 제보 (POST /api/community/deals/{deal_id}/report-closed)
     */
    fun reportClosed() {
        val dealId = currentDealId ?: return
        val current = _uiState.value
        if (current.isReportingClosed || current.hasReportedClosed || current.isClosed) return

        reportJob?.cancel()
        reportJob = vmScope.launch {
            _uiState.value = _uiState.value.copy(isReportingClosed = true)
            try {
                val response = withContext(ioDispatcher) {
                    apiService.reportDealClosed(dealId)
                }
                if (currentDealId != dealId) return@launch
                val body = if (response.isSuccessful) response.body() else null
                if (body != null) {
                    val stateNow = _uiState.value
                    val updatedClosed = body.isClosed || stateNow.isClosed
                    val updatedCount = maxOf(body.reportCount, stateNow.reportCount)
                    val threshold = body.threshold.coerceAtLeast(1)
                    val msg = if (updatedClosed) {
                        "품절/종료 처리되었어요"
                    } else {
                        "품절 제보가 접수되었어요 (${updatedCount}/${threshold})"
                    }
                    _uiState.value = stateNow.copy(
                        isClosed = updatedClosed,
                        reportCount = updatedCount,
                        deal = stateNow.deal?.withClosedReportState(updatedClosed, updatedCount),
                        isReportingClosed = false,
                        hasReportedClosed = true,
                        reportClosedFeedback = msg,
                        reportClosedFeedbackSeq = stateNow.reportClosedFeedbackSeq + 1
                    )
                } else {
                    val stateNow = _uiState.value
                    when (response.code()) {
                        409 -> {
                            _uiState.value = stateNow.copy(
                                isReportingClosed = false,
                                hasReportedClosed = true,
                                reportClosedFeedback = "이미 품절 제보를 완료했어요",
                                reportClosedFeedbackSeq = stateNow.reportClosedFeedbackSeq + 1
                            )
                        }
                        429 -> {
                            _uiState.value = stateNow.copy(
                                isReportingClosed = false,
                                reportClosedFeedback = "요청이 너무 많아요. 잠시 후 다시 시도해 주세요",
                                reportClosedFeedbackSeq = stateNow.reportClosedFeedbackSeq + 1
                            )
                        }
                        else -> {
                            _uiState.value = stateNow.copy(
                                isReportingClosed = false,
                                reportClosedFeedback = "품절 제보 처리에 실패했어요. 잠시 후 다시 시도해 주세요",
                                reportClosedFeedbackSeq = stateNow.reportClosedFeedbackSeq + 1
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                if (currentDealId != dealId) return@launch
                val stateNow = _uiState.value
                _uiState.value = stateNow.copy(
                    isReportingClosed = false,
                    reportClosedFeedback = "품절 제보 처리에 실패했어요. 잠시 후 다시 시도해 주세요",
                    reportClosedFeedbackSeq = stateNow.reportClosedFeedbackSeq + 1
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        detailJob?.cancel()
        historyJob?.cancel()
        reportJob?.cancel()
    }

    companion object {
        const val STANDARD_DETAIL_ERROR_MESSAGE =
            "상세 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요."

        /**
         * 유효한 다중 옵션을 추출합니다.
         * 0원(무료/사은품/가격확인) 옵션은 보존하고, 공백 옵션명과 음수(-1원 미만) 또는 null 가격만 제외합니다.
         */
        fun extractValidMultiOptions(deal: DealItem?): List<DealSource> {
            if (deal == null) return emptyList()
            val sourceOptions = deal.sources
                ?.filter {
                    it.siteName.contains(" - ") &&
                        it.siteName.substringAfter(" - ").isNotBlank() &&
                        cleanOptionTitle(it.siteName).isNotBlank() &&
                        (it.price ?: -1L) >= 0L
                }
                .orEmpty()

            val safeOptionsData = (deal.optionsData as List<com.ddaeany0919.insightdeal.models.DealOptionItem>?)
                ?: emptyList()
            val combined = if (sourceOptions.isNotEmpty()) {
                sourceOptions
            } else {
                safeOptionsData
                    .filter {
                        it.name.isNotBlank() &&
                            cleanOptionTitle(it.name).isNotBlank() &&
                            it.price >= 0L
                    }
                    .map { opt ->
                        DealSource(
                            siteName = "${deal.siteName} - ${opt.name}",
                            postUrl = deal.postUrl.orEmpty(),
                            ecommerceUrl = opt.url ?: deal.ecommerceUrl,
                            price = opt.price,
                            currency = opt.currency ?: deal.currency,
                            code = opt.code,
                            imageUrl = opt.imageUrl ?: deal.imageUrl,
                            subUrls = opt.subUrls ?: emptyList()
                        )
                    }
            }

            val seen = mutableSetOf<String>()
            return combined.filter { src ->
                val optTitle = cleanOptionTitle(src.siteName).lowercase().replace(" ", "")
                optTitle.isNotEmpty() && seen.add(optTitle)
            }
        }
    }
}
