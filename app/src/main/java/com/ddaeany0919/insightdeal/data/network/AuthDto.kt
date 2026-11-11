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
