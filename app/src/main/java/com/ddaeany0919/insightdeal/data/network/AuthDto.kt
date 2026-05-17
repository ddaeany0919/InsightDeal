package com.ddaeany0919.insightdeal.data.network

data class UserCreateReq(
    val username: String,
    val password: String,
    val nickname: String
)

data class UserLoginReq(
    val username: String,
    val password: String
)

data class UserResponseDto(
    val username: String,
    val nickname: String,
    val honey_points: Int
)
