package com.ddaeany0919.insightdeal.data

sealed class Resource<out T>( // ✅ 'out' 키워드 추가 (covariant)
    val data: T? = null,
    val message: String? = null,
    val throwable: Throwable? = null
) {
    class Success<T>(data: T) : Resource<T>(
        data = data,
        message = null,
        throwable = null
    )

    class Error<T>(
        message: String,
        data: T? = null,
        throwable: Throwable? = null
    ) : Resource<T>(
        data = data,
        message = message,
        throwable = throwable
    )

    class Loading<T>(data: T? = null) : Resource<T>(
        data = data,
        message = "Loading...",
        throwable = null
    )

    fun isLoading(): Boolean = this is Loading<*>
    fun isSuccess(): Boolean = this is Success<*>
    fun isError(): Boolean = this is Error<*>
    fun getDataOrNull(): T? = data
    fun getErrorMessageOrNull(): String? = if (this is Error) message else null
}
