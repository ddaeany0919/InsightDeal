package com.ddaeany0919.insightdeal.network

import android.util.Log
import com.ddaeany0919.insightdeal.BuildConfig
import com.ddaeany0919.insightdeal.core.security.DeviceIdentityStore
import com.ddaeany0919.insightdeal.data.LocalDataProvider
import com.google.gson.GsonBuilder
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 통합 네트워크 모듈
 *
 * 모든 Repository가 공유하는 단일 OkHttp/Retrofit 인스턴스이며,
 * 동일 백엔드 호스트 요청에 한해 X-Device-Token 헤더 주입 및 최초 자동 등록을 수행합니다.
 */
object NetworkModule {

    internal const val TAG = "NetworkModule"
    internal const val HEADER_DEVICE_TOKEN = "X-Device-Token"

    // BuildConfig.BASE_URL 연동 (Debug: http://10.0.2.2:8080/, Release: 운영 서버 URL)
    val BASE_URL: String = BuildConfig.BASE_URL

    private const val CONNECT_TIMEOUT = 8L
    private const val READ_TIMEOUT = 15L
    private const val WRITE_TIMEOUT = 15L

    private val tokenLock = Any()

    private inline fun logDebug(message: () -> String) {
        if (BuildConfig.DEBUG_MODE) {
            runCatching { Log.d(TAG, message()) }
        }
    }

    private inline fun logWarn(message: () -> String) {
        if (BuildConfig.DEBUG_MODE) {
            runCatching { Log.w(TAG, message()) }
        }
    }

    private inline fun logError(message: () -> String, error: Throwable? = null) {
        if (BuildConfig.DEBUG_MODE) {
            runCatching {
                if (error != null) Log.e(TAG, message(), error) else Log.e(TAG, message())
            }
        }
    }

    private val gson = GsonBuilder()
        .setLenient()
        .setDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'")
        .create()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * 자동 등록 전용 경량 클라이언트 (헤더/자동등록 인터셉터 미부착 → 무한 재귀 원천 차단)
     */
    private val defaultRegistrationClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .addInterceptor(createLoggingInterceptor())
            .build()
    }

    /**
     * OkHttp 클라이언트 (싱글톤)
     */
    val okHttpClient: OkHttpClient by lazy {
        logDebug { "OkHttpClient 초기화 중 (BASE_URL=$BASE_URL)" }
        createOkHttpClient(
            baseUrl = BASE_URL,
            identityStoreProvider = { LocalDataProvider.identityStore },
            registrationClient = defaultRegistrationClient
        )
    }

    /**
     * 테스트 및 프로덕션 공용 OkHttpClient 팩토리
     */
    internal fun createOkHttpClient(
        baseUrl: String,
        identityStoreProvider: () -> DeviceIdentityStore,
        registrationClient: OkHttpClient = defaultRegistrationClient
    ): OkHttpClient {
        val parsedBaseUrl = baseUrl.toHttpUrlOrNull()
        return OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT, TimeUnit.SECONDS)
            .addInterceptor(
                createHeaderInterceptor(
                    baseUrl = baseUrl,
                    parsedBaseUrl = parsedBaseUrl,
                    identityStoreProvider = identityStoreProvider,
                    registrationClient = registrationClient
                )
            )
            .addInterceptor(createLoggingInterceptor())
            .build()
    }

    /**
     * 로깅 인터셉터 (X-Device-Token 마스킹 적용, 출시 빌드에서는 비활성화)
     */
    private fun createLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor { message ->
            logDebug { "HTTP: $message" }
        }.apply {
            redactHeader(HEADER_DEVICE_TOKEN)
            level = if (BuildConfig.DEBUG_MODE) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    internal fun isSameBackendHost(requestUrl: HttpUrl, baseHttpUrl: HttpUrl?): Boolean {
        if (baseHttpUrl == null) return false
        return requestUrl.host.equals(baseHttpUrl.host, ignoreCase = true) &&
            requestUrl.port == baseHttpUrl.port
    }

    internal fun isDeviceRegistrationPath(requestUrl: HttpUrl): Boolean {
        val path = requestUrl.encodedPath.trimEnd('/')
        return path.endsWith("/api/push/device")
    }

    internal fun requiresDeviceTokenRegistration(request: Request): Boolean {
        if (isDeviceRegistrationPath(request.url)) return false
        val method = request.method.uppercase()
        if (method in setOf("POST", "PUT", "PATCH", "DELETE")) {
            return true
        }
        val path = request.url.encodedPath
        return path.startsWith("/api/push/") ||
            path.startsWith("/api/wishlist") ||
            path.startsWith("/api/users") ||
            path.startsWith("/api/auth") ||
            path.startsWith("/products") ||
            path.startsWith("/api/product/track") ||
            path.contains("/api/community/deals/")
    }

    private const val MAX_REGISTRATION_PEEK_BYTES = 64 * 1024L

    /**
     * 공통 헤더 및 동일 호스트 X-Device-Token 자동 주입 인터셉터
     */
    private fun createHeaderInterceptor(
        baseUrl: String,
        parsedBaseUrl: HttpUrl?,
        identityStoreProvider: () -> DeviceIdentityStore,
        registrationClient: OkHttpClient
    ) = Interceptor { chain ->
        val original = chain.request()
        val requestBuilder = original.newBuilder()
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .header("User-Agent", "InsightDeal-Android/${BuildConfig.VERSION_NAME}")

        if (isSameBackendHost(original.url, parsedBaseUrl)) {
            val store = runCatching { identityStoreProvider() }.getOrNull()
            if (store != null) {
                if (isDeviceRegistrationPath(original.url)) {
                    // 재귀 방지: 저장된 토큰이 있으면 즉시 헤더에 첨부하고, 미발급(Cold Start) 시에는 tokenLock 하에 직렬화합니다.
                    val existingToken = runCatching { store.getDeviceToken() }.getOrNull()
                    if (!existingToken.isNullOrBlank()) {
                        requestBuilder.header(HEADER_DEVICE_TOKEN, existingToken)
                        return@Interceptor proceedWithLogging(chain, requestBuilder.build())
                    }

                    return@Interceptor synchronized(tokenLock) {
                        val recheckedToken = runCatching { store.getDeviceToken() }.getOrNull()
                        if (!recheckedToken.isNullOrBlank()) {
                            requestBuilder.header(HEADER_DEVICE_TOKEN, recheckedToken)
                            proceedWithLogging(chain, requestBuilder.build())
                        } else {
                            val response = proceedWithLogging(chain, requestBuilder.build())
                            if (original.method.equals("POST", ignoreCase = true) && response.isSuccessful) {
                                persistTokenFromRegistrationResponse(response, store)
                            }
                            response
                        }
                    }
                } else {
                    var token = runCatching { store.getDeviceToken() }.getOrNull()
                    if (token.isNullOrBlank() && requiresDeviceTokenRegistration(original)) {
                        token = ensureDeviceRegisteredSync(
                            baseUrl = baseUrl,
                            store = store,
                            registrationClient = registrationClient
                        )
                    }
                    if (!token.isNullOrBlank()) {
                        requestBuilder.header(HEADER_DEVICE_TOKEN, token)
                    }
                }
            }
        }

        proceedWithLogging(chain, requestBuilder.build())
    }

    private fun proceedWithLogging(
        chain: Interceptor.Chain,
        request: Request
    ): okhttp3.Response {
        val startTime = System.currentTimeMillis()
        return try {
            val response = chain.proceed(request)
            logDebug {
                val duration = System.currentTimeMillis() - startTime
                "Response: ${response.code} in ${duration}ms"
            }
            response
        } catch (e: Exception) {
            logError({
                val duration = System.currentTimeMillis() - startTime
                "Request failed after ${duration}ms: ${e.message}"
            }, e)
            throw e
        }
    }

    private fun persistTokenFromRegistrationResponse(
        response: okhttp3.Response,
        store: DeviceIdentityStore
    ) {
        runCatching {
            val bodyString = response.peekBody(MAX_REGISTRATION_PEEK_BYTES).string()
            if (bodyString.isBlank()) return
            val parsed = gson.fromJson(bodyString, DeviceRegistrationResponse::class.java)
            val issuedToken = parsed?.deviceToken?.trim()?.takeIf { it.isNotBlank() } ?: return
            val resolvedUuid = parsed.deviceUuid?.trim()?.takeIf { it.isNotBlank() }
                ?: runCatching { store.getOrCreateDeviceUuid() }.getOrNull().orEmpty()
            if (resolvedUuid.isNotBlank()) {
                store.saveDeviceToken(issuedToken, resolvedUuid)
            }
        }.onFailure { e ->
            logWarn { "직접 기기 등록 응답 토큰 추출 실패: ${e.message}" }
        }
    }

    /**
     * device_token 미발급 시 synchronized(tokenLock) 하에 POST {BASE_URL}api/push/device를 1회 동기 실행합니다.
     * - fcm_token은 캐시값 또는 null만 전송하며 절대 빈 문자열("")을 송신하지 않습니다.
     * - 401/403 응답 시 재시도 루프를 돌지 않습니다.
     */
    private fun ensureDeviceRegisteredSync(
        baseUrl: String,
        store: DeviceIdentityStore,
        registrationClient: OkHttpClient
    ): String? {
        return synchronized(tokenLock) {
            val existing = runCatching { store.getDeviceToken() }.getOrNull()
            if (!existing.isNullOrBlank()) {
                return@synchronized existing
            }

            val deviceUuid = runCatching { store.getOrCreateDeviceUuid() }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: return@synchronized null
            val cachedFcmToken = runCatching { store.getCachedFcmToken() }.getOrNull()
                ?.takeIf { it.isNotBlank() }

            val normalizedBaseUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
            val registerUrl = "${normalizedBaseUrl}api/push/device"

            val payload = RegisterDeviceReq(
                device_uuid = deviceUuid,
                fcm_token = cachedFcmToken
            )
            val request = Request.Builder()
                .url(registerUrl)
                .post(gson.toJson(payload).toRequestBody(jsonMediaType))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "InsightDeal-Android/${BuildConfig.VERSION_NAME}")
                .build()

            runCatching {
                registrationClient.newCall(request).execute().use { response ->
                    if (response.code == 401 || response.code == 403 || !response.isSuccessful) {
                        logWarn { "기기 자동 등록 실패/차단(재시도 안 함): code=${response.code}" }
                        return@synchronized null
                    }
                    val bodyString = response.body?.string().orEmpty()
                    if (bodyString.isBlank()) return@synchronized null

                    val parsed = gson.fromJson(bodyString, DeviceRegistrationResponse::class.java)
                    val issuedToken = parsed?.deviceToken?.trim()?.takeIf { it.isNotBlank() }
                    val resolvedUuid = parsed?.deviceUuid?.trim()?.takeIf { it.isNotBlank() } ?: deviceUuid
                    if (issuedToken != null) {
                        store.saveDeviceToken(issuedToken, resolvedUuid)
                    }
                    issuedToken
                }
            }.onFailure { e ->
                logWarn { "기기 자동 등록 요청 예외: ${e.message}" }
            }.getOrNull()
        }
    }

    /**
     * Retrofit 인스턴스 (싱글톤)
     */
    val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    /**
     * API 서비스 생성 헬퍼
     */
    inline fun <reified T> createService(): T {
        return retrofit.create(T::class.java)
    }
}
