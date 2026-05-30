package com.ddaeany0919.insightdeal.presentation.mypage

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.ExitToApp
import com.ddaeany0919.insightdeal.presentation.auth.AuthManager
import com.ddaeany0919.insightdeal.presentation.mypage.history.NotificationHistoryManager
import com.ddaeany0919.insightdeal.presentation.mypage.history.NotificationAlert
import androidx.compose.ui.window.Dialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.ddaeany0919.insightdeal.presentation.formatPrice
import com.ddaeany0919.insightdeal.presentation.components.PinLockDialog
import com.ddaeany0919.insightdeal.core.security.EncryptedPrefsManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPageScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToAuth: () -> Unit,
    onNavigateToWatchlist: () -> Unit,
    onNavigateToMyPosts: () -> Unit,
    onNavigateToMyComments: () -> Unit,
    onNavigateToRecentDeals: () -> Unit,
    navController: androidx.navigation.NavController? = null
) {
    val context = LocalContext.current
    val username by AuthManager.getUsername(context).collectAsState(initial = "guest")
    val isLoggedIn = username != "guest" && !username.isNullOrEmpty()
    
    // 알림 히스토리 초기화 및 구독
    LaunchedEffect(Unit) {
        NotificationHistoryManager.init(context)
    }
    val notificationAlerts by NotificationHistoryManager.alerts.collectAsState()
    
    val recentDeals by com.ddaeany0919.insightdeal.presentation.mypage.history.RecentDealManager.recentDeals.collectAsState()
    
    val viewModel: com.ddaeany0919.insightdeal.presentation.community.CommunityBoardViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val uiState by viewModel.uiState.collectAsState()
    
    LaunchedEffect(Unit) {
        viewModel.loadPosts()
    }

    val myPostsCount = if (uiState is com.ddaeany0919.insightdeal.presentation.community.CommunityBoardUiState.Success) {
        (uiState as com.ddaeany0919.insightdeal.presentation.community.CommunityBoardUiState.Success).posts.count { it.userId == username }
    } else 0

    val myCommentsViewModel: com.ddaeany0919.insightdeal.presentation.mypage.history.MyCommentsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val myCommentsUiState by myCommentsViewModel.uiState.collectAsState()
    
    LaunchedEffect(username) {
        myCommentsViewModel.loadComments(username ?: "admin")
    }
    
    val myCommentsCount = if (myCommentsUiState is com.ddaeany0919.insightdeal.presentation.mypage.history.MyCommentsUiState.Success) {
        (myCommentsUiState as com.ddaeany0919.insightdeal.presentation.mypage.history.MyCommentsUiState.Success).comments.size
    } else 0

    var showNotificationHistory by remember { mutableStateOf(false) }
    val keywordViewModel: com.ddaeany0919.insightdeal.presentation.KeywordManagerViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    var showKeywordDialog by remember { mutableStateOf(false) }
    var showAuthRequiredDialog by remember { mutableStateOf(false) }

    // AI 1:1 개인화 추천 꿀딜 API 연동 및 모형 딜 상태 정의
    val api = remember { com.ddaeany0919.insightdeal.network.NetworkModule.createService<com.ddaeany0919.insightdeal.network.ApiService>() }
    var aiRecommendedDeals by remember { mutableStateOf<List<com.ddaeany0919.insightdeal.models.DealItem>>(emptyList()) }
    val mockDeals = remember {
        listOf(
            com.ddaeany0919.insightdeal.models.DealItem(id = 1, title = "아이패드 프로 11인치 M4 역대급 특가 딜", price = 1180000, siteName = "쿠팡", imageUrl = "", discountRate = 12),
            com.ddaeany0919.insightdeal.models.DealItem(id = 2, title = "소니 WH-1000XM5 노이즈캔슬링 헤드폰 핫딜", price = 312000, siteName = "11번가", imageUrl = "", discountRate = 15),
            com.ddaeany0919.insightdeal.models.DealItem(id = 3, title = "LG 올레드 evo 55인치 스탠드형 초특가", price = 1450000, siteName = "G마켓", imageUrl = "", discountRate = 20)
        )
    }
    
    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn) {
            try {
                val response = api.getCommunityHotDeals(limit = 50)
                if (response.isSuccessful && response.body() != null) {
                    val allDeals = response.body()?.deals ?: emptyList()
                    val now = java.time.OffsetDateTime.now()
                    val freshDeals = allDeals.filter { deal ->
                        deal.createdAt?.let { dateStr ->
                            try {
                                val parsedDate = if (dateStr.contains("Z") || dateStr.contains("+") || (dateStr.length > 19 && (dateStr[19] == '+' || dateStr[19] == '-'))) {
                                    java.time.OffsetDateTime.parse(dateStr)
                                } else {
                                    java.time.LocalDateTime.parse(dateStr).atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime()
                                }
                                val hoursBetween = java.time.Duration.between(parsedDate, now).toHours()
                                hoursBetween in 0..48
                            } catch (e: Exception) {
                                true
                            }
                        } ?: true
                    }
                    aiRecommendedDeals = freshDeals.sortedByDescending { it.discountRate ?: 0 }.take(3)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }

    // 🔒 보안 잠금 설정 정보 바인딩
    val prefs = remember { EncryptedPrefsManager.getEncryptedPrefs(context) }
    var appLockEnabled by remember { mutableStateOf(prefs.getBoolean("app_lock_enabled", false)) }
    var appLockPin by remember { mutableStateOf(prefs.getString("app_lock_pin", "") ?: "") }
    
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var showPinVerifyDialogToDisable by remember { mutableStateOf(false) }

    // 🍯 꿀 레벨 게이지 포인트 계산 (일일 최대 50포인트 제한 적용, 최대 999레벨)
    val isTestingAdmin = username == "admin"
    val basePoints = if (isTestingAdmin) 9990 else ((myPostsCount * 10) + (myCommentsCount * 3))
    val honeyPoints = if (isTestingAdmin) 9990 else basePoints.coerceAtMost(50) // 일일 최대 50 P 획득 제약
    
    // 10포인트당 1레벨 상승, 최대 999레벨
    val honeyLevel = if (isTestingAdmin) 999 else ((honeyPoints / 10) + 1).coerceIn(1, 999)
    
    val honeyLevelName = when {
        honeyLevel >= 999 -> "합리적 쇼핑의 신 (최종 만랩) 👑"
        honeyLevel >= 500 -> "전설의 꿀딜 마스터 ⚡"
        honeyLevel >= 100 -> "베테랑 꿀딜 사냥꾼 🎯"
        honeyLevel >= 10 -> "숙련된 지름꾼 🔥"
        honeyLevel >= 2 -> "지름 지망생 🌱"
        else -> "초보 지름꾼 🍯"
    }
    val nextLevelPoints = if (honeyLevel == 999) 9990 else honeyLevel * 10
    val prevLevelPoints = (honeyLevel - 1) * 10
    val progress = if (honeyLevel == 999) 1f else {
        ((honeyPoints - prevLevelPoints).toFloat() / (nextLevelPoints - prevLevelPoints).toFloat()).coerceIn(0f, 1f)
    }

    // 🐝 꿀벌 펄싱 및 기어가기 햅틱 애니메이션 설정
    val infiniteTransition = rememberInfiniteTransition(label = "BeeMotion")
    val beeScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BeeScale"
    )
    val beeRotation by infiniteTransition.animateFloat(
        initialValue = -5f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BeeRotation"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("내 정보", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp) },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "설정", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ✨ 사용자 프로필 요약 카드 - 홈 화면 수준의 프리미엄 그라디언트 적용
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .shadow(elevation = 3.dp, shape = RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isLoggedIn) "$username 님" else "익명 임시 계정 님", 
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = if (isLoggedIn) 0.12f else 0.05f)
                                ) {
                                    Text(
                                        text = if (isLoggedIn) honeyLevelName else "🔒 꿀 내공 혜택 비활성", 
                                        style = MaterialTheme.typography.bodySmall, 
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                            if (isLoggedIn) {
                                val scope = rememberCoroutineScope()
                                Surface(
                                    modifier = Modifier
                                        .padding(start = 8.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            scope.launch {
                                                AuthManager.logout(context)
                                                android.widget.Toast.makeText(context, "로그아웃 되어 guest 상태로 변경되었습니다. 🔒", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f)
                                ) {
                                    Text(
                                        text = "로그아웃",
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                    contentDescription = "프로필 수정",
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable { onNavigateToAuth() },
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                                )
                            }
                        }
                        
                        // 비로그인 시 소셜 간편 로그인 유도 배너 격발
                        if (!isLoggedIn) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onNavigateToAuth,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(44.dp)
                                    .shadow(elevation = 2.dp, shape = RoundedCornerShape(12.dp)),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFF9800), // 네온 오렌지
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp)
                            ) {
                                Text("로그인하고 꿀 혜택 받기", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f),
                            thickness = 1.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // 🍯 꿀 레벨 게이지 렌더링 영역 (비로그인 시 잠금 마스크 적용)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        alpha = if (isLoggedIn) 1f else 0.35f
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "🍯 꿀 내공 Lv.$honeyLevel",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = if (honeyLevel == 999) "MAX 9990 P" else "$honeyPoints / $nextLevelPoints P (일일 최대 50 P 제한)",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                
                                // 꿀벌이 progress 끝단을 기어가는 프리미엄 게이지 바 구현
                                BoxWithConstraints(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(28.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    val barWidth = maxWidth
                                    val beeOffset = (progress * (barWidth.value - 24)).dp
                                    
                                    // 아크릴 그라디언트 배경 Progress Bar
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(8.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(progress)
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(
                                                    Brush.horizontalGradient(
                                                        colors = listOf(
                                                            Color(0xFFFFA726),
                                                            Color(0xFFFF7043)
                                                        )
                                                    )
                                                )
                                        )
                                    }
                                    
                                    // 기어가는 꿀벌 펄싱 아이콘
                                    Box(
                                        modifier = Modifier
                                            .offset(x = beeOffset)
                                            .graphicsLayer {
                                                scaleX = beeScale
                                                scaleY = beeScale
                                                rotationZ = beeRotation
                                            }
                                            .size(24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(if (isLoggedIn) "🐝" else "💤🐝", fontSize = 18.sp)
                                    }
                                }
                            }
                            
                            // 비로그인 시 은은한 아크릴 잠금 마스크 레이어
                            if (!isLoggedIn) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(Color.Transparent)
                                        .clickable { onNavigateToAuth() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color.Black.copy(alpha = 0.65f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = null,
                                                tint = Color(0xFFFFB300),
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = "로그인 후 꿀 내공 혜택 잠금 해제",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 🍯 등급별 회원 독점 특전 비주얼 카드 (추후 제공 예정 주석 처리)
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .shadow(elevation = 2.dp, shape = RoundedCornerShape(24.dp)),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("🔒", fontSize = 16.sp)
                            Text(
                                text = "등급별 회원 독점 특전 (추후 제공 예정)",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                        /* 추후 서비스 고도화 및 잘 풀린 시점에 아래 혜택 로우 봉인 해제 예정!
                        Spacer(modifier = Modifier.height(14.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        alpha = if (isLoggedIn) 1f else 0.35f
                                    },
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                BenefitRow(
                                    level = "Lv.1",
                                    title = "가입 즉시 1:1 지능형 AI 추천 활성화",
                                    status = if (isLoggedIn) "달성 완료 🌱" else "대기 중",
                                    isAchieved = isLoggedIn
                                )
                                BenefitRow(
                                    level = "Lv.2",
                                    title = "파트너 쇼핑몰 제휴 10% 꿀쿠폰 & 비밀 특가 딜 해제",
                                    status = if (isLoggedIn && honeyLevel >= 2) "달성 완료 ⚡" else "Lv.2 도달 시 자동 오픈",
                                    isAchieved = isLoggedIn && honeyLevel >= 2
                                )
                                BenefitRow(
                                    level = "Lv.5",
                                    title = "최저가 꿀딜 알림 최우선 푸시 + 특별 기프트 제공",
                                    status = if (isLoggedIn && honeyLevel >= 5) "달성 완료 👑" else "Lv.5 도달 시 자동 오픈",
                                    isAchieved = isLoggedIn && honeyLevel >= 5
                                )
                            }
                            
                            if (!isLoggedIn) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .background(Color.Transparent)
                                        .clickable { onNavigateToSettings() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color.Black.copy(alpha = 0.7f),
                                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                                    ) {
                                        Text(
                                            text = "로그인하고 등급별 비밀 혜택 잠금 해제 ⚡",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                        */
                    }
                }
            }

            // ✨ Quick Stats
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    QuickStatCard(
                        modifier = Modifier.weight(1f), 
                        title = "포인트", 
                        value = "0 P",
                        onClick = {
                            android.widget.Toast.makeText(context, "포인트는 향후 커뮤니티 리워드 및 상품 교환에 활용될 예정입니다.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
                    QuickStatCard(
                        modifier = Modifier.weight(1f), 
                        title = "쿠폰", 
                        value = "0 장",
                        onClick = {
                            android.widget.Toast.makeText(context, "쿠폰은 파트너사 제휴 할인 및 특별 이벤트에 사용됩니다.", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
                    QuickStatCard(
                        modifier = Modifier.weight(1f), 
                        title = "작성글", 
                        value = "$myPostsCount 건",
                        onClick = onNavigateToMyPosts
                    )
                }
            }
            
            // 구분선 1
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .padding(vertical = 4.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                )
            }

            // ✨ C. 최근 본 핫딜 슬라이더 (가로 캐러셀) 이식
            item {
                if (recentDeals.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⏱️ 최근 본 핫딜", 
                                fontSize = 17.sp, 
                                fontWeight = FontWeight.Black, 
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "전체보기",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.clickable { onNavigateToRecentDeals() }
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(recentDeals.take(10)) { deal ->
                                Card(
                                    modifier = Modifier
                                        .width(132.dp)
                                        .clickable { navController?.navigate("deal_detail/${deal.id}") }
                                        .shadow(elevation = 2.dp, shape = RoundedCornerShape(14.dp)),
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
                                        horizontalAlignment = Alignment.Start
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(84.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        ) {
                                            if (!deal.imageUrl.isNullOrEmpty()) {
                                                coil.compose.AsyncImage(
                                                    model = deal.imageUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                )
                                            } else {
                                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                    Text("Insight", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                            
                                            // 사이트 정보 뱃지
                                            Surface(
                                                modifier = Modifier
                                                    .padding(4.dp)
                                                    .align(Alignment.TopStart),
                                                shape = RoundedCornerShape(4.dp),
                                                color = Color.Black.copy(alpha = 0.6f)
                                            ) {
                                                Text(
                                                    text = deal.siteName,
                                                    color = Color.White,
                                                    fontSize = 8.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            if (deal.discountRate != null && deal.discountRate > 0) {
                                                Text(
                                                    text = "${deal.discountRate}%",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFFF3B30)
                                                )
                                            }
                                            Text(
                                                text = formatPrice(deal.price),
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = deal.title,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        }
                        
                        // 비로그인 시 "기기 임시 저장 모드" 경고 배너 이식 (이질감 전면 척결 & 토스 스타일 정화)
                        if (!isLoggedIn) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFFFF9800).copy(alpha = 0.07f))
                                    .border(
                                        width = 1.dp,
                                        color = Color(0xFFFF9800).copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(14.dp)
                                    )
                                    .clickable { onNavigateToAuth() }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "경고",
                                    tint = Color(0xFFFFA000),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "기기 임시 저장 모드 이용 중",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFE65100)
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "앱 삭제나 기기 변경 시 찜 목록 및 히스토리가 즉시 소멸됩니다. 안전한 클라우드 동기화를 위해 로그인을 진행해 주세요.",
                                        fontSize = 11.sp,
                                        color = Color(0xFFE65100).copy(alpha = 0.8f),
                                        lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Fallback: 최근 본 상품이 없을 때의 예외 가드 렌더링
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                    ) {
                        Text(
                            text = "⏱️ 최근 본 핫딜", 
                            fontSize = 17.sp, 
                            fontWeight = FontWeight.Black, 
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "최근에 본 핫딜이 없습니다. 핫딜을 구경해보세요!",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 구분선 2
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                )
            }

            // 🤖 1:1 지능형 AI 맞춤 추천 픽 (AI Pick)
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("🤖", fontSize = 18.sp)
                        Text(
                            text = "1:1 지능형 AI 추천 꿀딜 (AI Pick)",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        if (isLoggedIn) {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = Color(0xFFFFECE6)
                            ) {
                                Text(
                                    text = "실시간 추천 중",
                                    color = Color(0xFFFF6B35),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        val dealsToRender = if (isLoggedIn) aiRecommendedDeals else mockDeals
                        
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    // 비로그인 상태일 때는 흐릿하고 어둡게 처리
                                    alpha = if (isLoggedIn) 1f else 0.25f
                                },
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            dealsToRender.forEach { deal ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (isLoggedIn) {
                                                navController?.navigate("deal_detail/${deal.id}")
                                            }
                                        },
                                    shape = RoundedCornerShape(16.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, if (isLoggedIn) Color(0xFFFF6B35).copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(60.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        ) {
                                            if (!deal.imageUrl.isNullOrEmpty()) {
                                                coil.compose.AsyncImage(
                                                    model = deal.imageUrl,
                                                    contentDescription = null,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                                )
                                            } else {
                                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                                    Text("AI Pick", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF6B35))
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = Color(0xFFFFECE6)
                                                ) {
                                                    Text(
                                                        text = deal.siteName,
                                                        color = Color(0xFFFF6B35),
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                    )
                                                }
                                                if (deal.discountRate != null && deal.discountRate > 0) {
                                                    Text(
                                                        text = "${deal.discountRate}%",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.ExtraBold,
                                                        color = Color(0xFFFF3B30)
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = deal.title,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "${formatPrice(deal.price)}원",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        if (isLoggedIn) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                                contentDescription = null,
                                                modifier = Modifier.size(12.dp),
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        
                        if (!isLoggedIn) {
                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .background(Color.Transparent)
                                    .clickable { onNavigateToAuth() },
                                contentAlignment = Alignment.Center
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.Black.copy(alpha = 0.7f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "🤖 AI 맞춤 꿀딜 분석엔진 비활성화",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "1초 소셜 로그인 시 최적의 개인화 핫딜을 즉시 추천합니다.",
                                            color = Color.White.copy(alpha = 0.8f),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color(0xFFFF9800)
                                        ) {
                                            Text(
                                                text = "AI 추천 픽 해제하기 ⚡",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 구분선 3
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                )
            }

            // 메뉴 목록 - 나의 활동
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = "나의 활동", 
                        fontSize = 17.sp, 
                        fontWeight = FontWeight.Black, 
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                    )
                    
                    MyPageMenuItem(
                        icon = Icons.Outlined.FavoriteBorder, 
                        title = "내 관심목록 (찜)", 
                        onClick = {
                            if (isLoggedIn) {
                                onNavigateToWatchlist()
                            } else {
                                showAuthRequiredDialog = true
                            }
                        }
                    )
                    MyPageMenuItem(icon = Icons.Outlined.History, title = "최근 본 핫딜", value = "${recentDeals.size} 건", onClick = onNavigateToRecentDeals)
                    MyPageMenuItem(icon = Icons.Outlined.ChatBubbleOutline, title = "내가 쓴 댓글", value = "$myCommentsCount 건", onClick = onNavigateToMyComments)
                    
                    // ✨ 신규 피처: 알림 항목 추가
                    MyPageMenuItem(
                        icon = Icons.Default.Notifications, 
                        title = "알림 항목 추가",
                        onClick = {
                            if (isLoggedIn) {
                                showKeywordDialog = true
                            } else {
                                showAuthRequiredDialog = true
                            }
                        }
                    )
                }
            }

            // 구분선 3
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                )
            }

            // ✨ B. 보안 PIN 앱 잠금 퀵 토글 섹션 추가
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    Text(
                        text = "보안 및 설정", 
                        fontSize = 17.sp, 
                        fontWeight = FontWeight.Black, 
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
                    )
                    
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "화면 잠금 보호 (PIN)", 
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = if (appLockEnabled) "앱 구동 시 4자리 PIN 보안 암호가 작동 중입니다." else "비활성화됨 (원터치로 간편하게 보안 강화)",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                        Switch(
                            checked = appLockEnabled,
                            onCheckedChange = { checked ->
                                if (checked) {
                                    if (appLockPin.isEmpty()) {
                                        showPinSetupDialog = true
                                    } else {
                                        prefs.edit().putBoolean("app_lock_enabled", true).apply()
                                        appLockEnabled = true
                                        android.widget.Toast.makeText(context, "보안 잠금이 활성화되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    showPinVerifyDialogToDisable = true
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }
    }

    // ✨ 신규 피처: 알림 내역 바텀시트 및 키워드 알림 바텀시트 체인 연동

    if (showNotificationHistory) {
        com.ddaeany0919.insightdeal.presentation.home.NotificationHistoryBottomSheet(
            onDismiss = { showNotificationHistory = false },
            onNavigateToKeywordSettings = {
                showNotificationHistory = false
                showKeywordDialog = true
            },
            onOpenUrlInWebView = { url ->
                if (navController != null) {
                    val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
                    navController.navigate("webview/$encodedUrl")
                } else {
                    try {
                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "URL을 열 수 없습니다.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    if (showKeywordDialog) {
        com.ddaeany0919.insightdeal.presentation.home.KeywordSettingsBottomSheet(
            onDismiss = { showKeywordDialog = false },
            keywordViewModel = keywordViewModel
        )
    }

    // ✨ PIN 번호 설정 및 검증 다이얼로그 호출 바인딩
    if (showPinSetupDialog) {
        PinLockDialog(
            title = "신규 PIN 번호 설정",
            subtitle = "보안 잠금을 위한 4자리 PIN을 입력하세요.",
            correctPin = "",
            isSetupMode = true,
            onDismiss = { 
                showPinSetupDialog = false
                appLockEnabled = false
            },
            onSuccess = { newPin ->
                prefs.edit()
                    .putBoolean("app_lock_enabled", true)
                    .putString("app_lock_pin", newPin)
                    .apply()
                appLockEnabled = true
                appLockPin = newPin
                showPinSetupDialog = false
                android.widget.Toast.makeText(context, "보안 PIN 설정 완료 및 잠금이 활성화되었습니다. 🔒", android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showPinVerifyDialogToDisable) {
        PinLockDialog(
            title = "보안 PIN 번호 확인",
            subtitle = "잠금을 해제하기 위해 기존 4자리 PIN을 입력하세요.",
            correctPin = appLockPin,
            isSetupMode = false,
            onDismiss = { 
                showPinVerifyDialogToDisable = false
                appLockEnabled = true
            },
            onSuccess = {
                prefs.edit()
                    .putBoolean("app_lock_enabled", false)
                    .apply()
                appLockEnabled = false
                showPinVerifyDialogToDisable = false
                android.widget.Toast.makeText(context, "보안 잠금이 완전히 해제되었습니다.", android.widget.Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showAuthRequiredDialog) {
        AlertDialog(
            onDismissRequest = { showAuthRequiredDialog = false },
            title = {
                Text(
                    text = "로그인이 필요합니다 🔐",
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "해당 기능(관심목록 찜, 키워드 알림 내역 등)은 고관여 멤버십 혜택으로, 안전한 클라우드 동기화 및 1:1 맞춤 피드백을 위해 로그인이 필수적입니다.\n\n지금 1초 만에 소셜 로그인을 진행하시겠습니까? ⚡",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showAuthRequiredDialog = false
                        onNavigateToAuth()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF9800),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("로그인하러 가기 🚀", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAuthRequiredDialog = false }) {
                    Text("나중에 할게요", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface
        )
    }
}

@Composable
fun QuickStatCard(modifier: Modifier = Modifier, title: String, value: String, onClick: () -> Unit = {}) {
    Card(
        modifier = modifier
            .clickable(onClick = onClick)
            .shadow(elevation = 2.dp, shape = RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.height(6.dp))
            Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun MyPageMenuItem(icon: ImageVector, title: String, value: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(22.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title, 
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
    }
}

@Composable
private fun BenefitRow(level: String, title: String, status: String, isAchieved: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (isAchieved) Color(0xFFFF9800).copy(alpha = 0.05f) else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = if (isAchieved) Color(0xFFFF9800) else Color(0xFFE9ECEF),
            modifier = Modifier.size(width = 38.dp, height = 20.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = level,
                    color = if (isAchieved) Color.White else Color(0xFF868E96),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isAchieved) Color(0xFF212529) else Color(0xFF868E96)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = status,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isAchieved) Color(0xFFFF9800) else Color(0xFFADB5BD)
            )
        }
    }
}

