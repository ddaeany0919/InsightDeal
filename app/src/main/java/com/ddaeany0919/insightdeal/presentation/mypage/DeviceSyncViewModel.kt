package com.ddaeany0919.insightdeal.presentation.mypage

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ddaeany0919.insightdeal.data.network.ClaimPairingCodeRequest
import com.ddaeany0919.insightdeal.data.network.ClaimPairingCodeResponse
import com.ddaeany0919.insightdeal.network.ApiService
import com.ddaeany0919.insightdeal.network.NetworkModule
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class DeviceSyncUiState(
    val isIssuing: Boolean = false,
    val issuedCode: String? = null,
    val expiresAt: String? = null,
    val remainingSec: Int = 0,
    val issueError: String? = null,
    val isClaiming: Boolean = false,
    val claimSuccessMessage: String? = null,
    val claimError: String? = null,
    val lastClaimResponse: ClaimPairingCodeResponse? = null,
    val syncCompletedSeq: Int = 0
)

/**
 * 다기기 6자리 일회성 동기화 코드 발급/병합 ViewModel (P1-01)
 * - UI 뷰/컨텍스트 참조 없는 순수 MVVM ViewModel입니다.
 */
class DeviceSyncViewModel(
    private val apiService: ApiService = NetworkModule.createService<ApiService>(),
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val coroutineScopeOverride: CoroutineScope? = null
) : ViewModel() {

    private val vmScope: CoroutineScope
        get() = coroutineScopeOverride ?: viewModelScope

    private val _uiState = MutableStateFlow(DeviceSyncUiState())
    val uiState: StateFlow<DeviceSyncUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null

    fun issuePairingCode() {
        if (_uiState.value.isIssuing) return
        countdownJob?.cancel()
        vmScope.launch {
            _uiState.value = _uiState.value.copy(
                isIssuing = true,
                issueError = null
            )
            try {
                val response = withContext(ioDispatcher) {
                    apiService.issuePairingCode()
                }
                val body = if (response.isSuccessful) response.body() else null
                if (body != null && body.code.isNotBlank()) {
                    val totalSec = body.expiresInSec.coerceAtLeast(1)
                    _uiState.value = _uiState.value.copy(
                        isIssuing = false,
                        issuedCode = body.code,
                        expiresAt = body.expiresAt,
                        remainingSec = totalSec,
                        issueError = null
                    )
                    startCountdown(totalSec)
                } else {
                    val errorMsg = when (response.code()) {
                        429 -> "요청이 너무 많아요. 잠시 후 다시 시도해 주세요."
                        else -> "동기화 코드를 발급하지 못했어요. 잠시 후 다시 시도해 주세요."
                    }
                    _uiState.value = _uiState.value.copy(
                        isIssuing = false,
                        issueError = errorMsg
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    isIssuing = false,
                    issueError = "네트워크 오류가 발생했어요. 잠시 후 다시 시도해 주세요."
                )
            }
        }
    }

    private fun startCountdown(initialSec: Int) {
        countdownJob?.cancel()
        countdownJob = vmScope.launch {
            var left = initialSec
            while (left > 0) {
                delay(1000L)
                left -= 1
                _uiState.value = _uiState.value.copy(remainingSec = left.coerceAtLeast(0))
            }
        }
    }

    fun claimPairingCode(rawCode: String) {
        val cleanCode = rawCode.filter { it.isDigit() }
        if (cleanCode.length != 6) {
            _uiState.value = _uiState.value.copy(
                claimError = "6자리 숫자 동기화 코드를 정확히 입력해 주세요.",
                claimSuccessMessage = null
            )
            return
        }
        if (_uiState.value.isClaiming) return

        vmScope.launch {
            _uiState.value = _uiState.value.copy(
                isClaiming = true,
                claimError = null,
                claimSuccessMessage = null
            )
            try {
                val response = withContext(ioDispatcher) {
                    apiService.claimPairingCode(ClaimPairingCodeRequest(code = cleanCode))
                }
                val body = if (response.isSuccessful) response.body() else null
                if (body != null && (body.success || body.status.equals("ok", ignoreCase = true))) {
                    val currentSeq = _uiState.value.syncCompletedSeq
                    val summary = "동기화 완료 (키워드 ${body.mergedKeywords}건 · 찜 ${body.mergedWishlists}건 · 알림 ${body.mergedAlerts}건)"
                    _uiState.value = _uiState.value.copy(
                        isClaiming = false,
                        claimSuccessMessage = summary,
                        claimError = null,
                        lastClaimResponse = body,
                        syncCompletedSeq = currentSeq + 1
                    )
                } else {
                    val errorMsg = when (response.code()) {
                        400 -> "올바르지 않은 동기화 코드 형식이에요."
                        404 -> "만료되었거나 존재하지 않는 동기화 코드예요."
                        409 -> "이미 사용된 코드이거나 같은 기기의 코드예요."
                        429 -> "시도 횟수가 너무 많아요. 잠시 후 다시 시도해 주세요."
                        else -> "기기 동기화에 실패했어요. 잠시 후 다시 시도해 주세요."
                    }
                    _uiState.value = _uiState.value.copy(
                        isClaiming = false,
                        claimError = errorMsg,
                        claimSuccessMessage = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(
                    isClaiming = false,
                    claimError = "네트워크 오류로 동기화하지 못했어요. 다시 시도해 주세요.",
                    claimSuccessMessage = null
                )
            }
        }
    }

    fun clearFeedback() {
        _uiState.value = _uiState.value.copy(
            issueError = null,
            claimError = null,
            claimSuccessMessage = null
        )
    }

    override fun onCleared() {
        super.onCleared()
        countdownJob?.cancel()
    }
}
