# 🔐 InsightDeal 로그인/인증 시스템 구현 계획

> **구현 예정 시기**: Phase 3-4 (Phase 1-2 핵심 기능 완성 후)  
> **현재 상태**: 로그인 없이 기기별 익명 사용자로 운영  
> **작성일**: 2025.11.07

## 📅 언제 구현할까?

### 구현 시점 판단 기준
- [ ] 핵심 기능(위시리스트 + 커뮤니티 핫딜) 완전 완성
- [ ] 실제 사용자 100명 이상 확보
- [ ] "다른 폰에서도 위시리스트 보고싶다" 요청 증가
- [ ] 푸시 알림 시스템 고도화 필요 시점
- [ ] 개인화 추천 정확도 향상 필요 시점

### 예상 구현 시기
- **Phase 3** (3-4주차): 기본 OAuth 로그인
- **Phase 4** (4-5주차): 고급 개인화 기능

## 🎯 로그인 구현 목표

### 주요 목적
1. **멀티 디바이스 동기화**: 핸드폰 ↔ 태블릿 ↔ 웹
2. **개인화 강화**: 취향 학습, 맞춤 핫딜 추천
3. **푸시 알림 정교화**: 개인 스케줄/관심사 기반 알림
4. **프리미엄 기능**: 무제한 위시리스트, 고급 분석

### 비즈니스 가치
- **사용자 정착률** 향상 (기기 변경해도 데이터 유지)
- **개인화 추천** 정확도 증대
- **프리미엄 모델** 기반 마련

## 🔧 기술 구현 계획

### 1. OAuth/SNS 로그인 지원
```kotlin
// domain/auth/AuthRepository.kt
interface AuthRepository {
    suspend fun loginWithNaver(): AuthResult
    suspend fun loginWithKakao(): AuthResult  
    suspend fun loginWithGoogle(): AuthResult
    suspend fun logout()
    suspend fun getCurrentUser(): AuthState
}

sealed class AuthState {
    object Guest : AuthState()
    data class LoggedIn(
        val userId: String,
        val name: String,
        val email: String?,
        val profileImageUrl: String?
    ) : AuthState()
}

sealed class AuthResult {
    data class Success(val authState: AuthState.LoggedIn) : AuthResult()
    data class Error(val message: String) : AuthResult()
    object Cancelled : AuthResult()
}
```

### 2. 사용자 데이터 마이그레이션
```kotlin
// data/migration/UserDataMigration.kt
class UserDataMigration {
    suspend fun migrateGuestToLoggedIn(
        guestId: String, 
        newUserId: String
    ) {
        // 기존 위시리스트를 새 사용자 ID로 이전
        repository.migrateUserData(guestId, newUserId)
    }
    
    suspend fun offerDataImport(authState: AuthState.LoggedIn) {
        // "기존 데이터를 가져올까요?" UI 제공
    }
}
```

### 3. 토큰 관리 시스템
```kotlin
// data/auth/TokenManager.kt  
class TokenManager {
    private val tokenStore: DataStore<Preferences>
    
    suspend fun saveTokens(accessToken: String, refreshToken: String)
    suspend fun getAccessToken(): String?
    suspend fun refreshAccessToken(): String?
    suspend fun clearTokens()
}

// network/AuthInterceptor.kt
class AuthInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenManager.getAccessToken()
        val request = if (token != null) {
            chain.request().newBuilder()
                .addHeader(\"Authorization\", \"Bearer $token\")
                .build()
        } else {
            chain.request()
        }
        
        val response = chain.proceed(request)
        
        // 401 시 토큰 갱신 후 재시도
        if (response.code == 401 && token != null) {
            return refreshAndRetry(chain, request)
        }
        
        return response
    }
}
```

### 4. 점진적 기능 확장
```kotlin
// presentation/profile/ProfileScreen.kt
@Composable
fun ProfileScreen() {
    val authState by authViewModel.authState.collectAsState()
    
    when (authState) {
        is AuthState.Guest -> {
            GuestProfileCard(
                onLoginClick = { authViewModel.showLoginOptions() }
            )
        }
        is AuthState.LoggedIn -> {
            LoggedInProfile(
                user = authState,
                onLogoutClick = { authViewModel.logout() }
            )
        }
    }
}
```

## 📱 UI/UX 설계

### 로그인 유도 전략 (부드럽게)
1. **처음에는 강제하지 않음**
   - "게스트로 계속" 버튼 제공
   - 위시리스트 5개 이상 추가 시 "로그인하면 데이터 보호됩니다" 안내

2. **가치 기반 유도**
   - "다른 폰에서도 보려면 로그인"
   - "맞춤 핫딜 추천받으려면 로그인"
   - "위시리스트 무제한 사용하려면 로그인"

### 로그인 UI 디자인
```kotlin
// 간단하고 신뢰도 높은 디자인
@Composable
fun LoginBottomSheet() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("더 편리하게 사용하기", style = MaterialTheme.typography.headlineSmall)
        Text("로그인하면 어느 기기에서든 위시리스트를 확인할 수 있어요")
        
        LoginButton(LoginType.NAVER, onLogin)
        LoginButton(LoginType.KAKAO, onLogin)  
        LoginButton(LoginType.GOOGLE, onLogin)
        
        TextButton(onClick = onDismiss) {
            Text("나중에 하기")
        }
    }
}
```

## 🔄 데이터 마이그레이션 시나리오

### 게스트 → 로그인 사용자
1. **현재 위시리스트 보존**
   ```kotlin
   suspend fun linkGuestToAccount(guestId: String, loggedInUserId: String) {
       // 서버에서 게스트 데이터를 로그인 계정으로 이전
       migrationService.linkAccounts(guestId, loggedInUserId)
   }
   ```

2. **중복 데이터 처리**
   - 이미 로그인 계정에 위시리스트가 있으면 "합치기/교체/보관" 옵션 제공

3. **부드러운 전환**
   - 로그인 후에도 기존 UI/기능은 동일하게 유지
   - 추가 기능만 점진적으로 해제

## 📊 인증 관련 KPI (미래)

### Phase 3-4에서 측정할 지표
- **로그인 전환율**: 게스트 → 로그인 사용자 %
- **데이터 보존률**: 로그인 후 기존 위시리스트 유지 %  
- **재방문률**: 로그인 사용자 vs 게스트 재방문 차이
- **기능 사용률**: 로그인 후 추가 기능 사용률

## ⚠️ 현재 집중할 것

### 로그인 전에 반드시 완성해야 할 기능들
- [ ] **네트워크 연결 안정화** (현재 진행 중)
- [ ] **링크 기반 상품 추가** 완전 동작
- [ ] **커뮤니티 핫딜 스크래퍼** 구현
- [ ] **위시리스트 ↔ 핫딜 매칭** 알고리즘
- [ ] **실시간 가격 추적** 시스템
- [ ] **푸시 알림** 기본 기능

### 로그인 없어도 충분한 기능들
- 개인 위시리스트 관리
- 가격 추적 및 알림
- 커뮤니티 핫딜 탐색
- AI 상품 분석
- 다중 쇼핑몰 가격 비교

---

## 📝 Action Items (미래 구현 시)

### Phase 3: 기본 인증 (3-4주차)
- [ ] 네이버/카카오/구글 OAuth SDK 연동
- [ ] JWT 토큰 관리 시스템
- [ ] 게스트 데이터 마이그레이션 로직
- [ ] 로그인 UI/UX 구현

### Phase 4: 고급 인증 (4-5주차)  
- [ ] 소셜 프로필 정보 활용
- [ ] 개인화 추천 알고리즘 강화
- [ ] 멀티 디바이스 동기화
- [ ] 프리미엄 기능 권한 관리

### 보안 고려사항
- [ ] 토큰 저장 보안 (Keystore/EncryptedSharedPreferences)
- [ ] API 요청 서명/검증
- [ ] 개인정보 최소 수집 원칙
- [ ] GDPR/개인정보보호법 준수

---

*현재는 로그인 구현을 연기하고, Phase 1-2 핵심 기능 완성에 집중합니다.  
사용자 피드백과 사용량 증가에 따라 Phase 3에서 점진적으로 도입 예정입니다.*