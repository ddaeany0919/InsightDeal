package com.ddaeany0919.insightdeal.data.network

import com.google.gson.annotations.SerializedName

data class UserCreateReq(
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String,
    @SerializedName("nickname") val nickname: String
)

data class UserLoginReq(
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String
)

data class UserResponseDto(
    @SerializedName("username") val username: String,
    @SerializedName("nickname") val nickname: String,
    @SerializedName("honey_points") val honey_points: Int = 0
)

/**
 * 다기기 6자리 일회성 동기화 코드 발급 응답 (POST /api/auth/pairing-code)
 */
data class PairingCodeResponse(
    @SerializedName("code") val code: String = "",
    @SerializedName(value = "expires_at", alternate = ["expiresAt"]) val expiresAt: String = "",
    @SerializedName(value = "expires_in_sec", alternate = ["expiresInSec"]) val expiresInSec: Int = 600
)

/**
 * 타 기기 동기화 코드 입력 병합 요청 (POST /api/auth/claim-pairing-code)
 */
data class ClaimPairingCodeRequest(
    @SerializedName("code") val code: String
)

/**
 * 타 기기 동기화 코드 병합 결과 응답 (POST /api/auth/claim-pairing-code)
 */
data class ClaimPairingCodeResponse(
    @SerializedName("status") val status: String = "",
    @SerializedName("success") val success: Boolean = false,
    @SerializedName(value = "merged_keywords", alternate = ["mergedKeywords"]) val mergedKeywords: Int = 0,
    @SerializedName(value = "merged_wishlists", alternate = ["mergedWishlists"]) val mergedWishlists: Int = 0,
    @SerializedName(value = "merged_alerts", alternate = ["mergedAlerts"]) val mergedAlerts: Int = 0,
    @SerializedName(value = "dnd_synced", alternate = ["dndSynced"]) val dndSynced: Boolean = false
)
