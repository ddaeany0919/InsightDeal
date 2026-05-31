package com.ddaeany0919.insightdeal.presentation.mypage


import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Fingerprint
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

    // 🔒 [Wow-Factor] 꿀 포인트 룸 꾸미기 & 아바타 샵 포인트 정보 연동
    val appPrefs = remember { context.getSharedPreferences("app", android.content.Context.MODE_PRIVATE) }
    var decorPoints by remember { mutableStateOf(appPrefs.getInt("decor_points", 0).coerceAtLeast(0)) }
    var equippedItem by remember { mutableStateOf(appPrefs.getString("equipped_item", "") ?: "") }
    var unlockedItems by remember { 
        val savedSet = appPrefs.getStringSet("unlocked_items", setOf("default")) ?: setOf("default")
        mutableStateOf(savedSet)
    }
    
    // 둥실 아바타 및 룸 확장 장식 착용 상태
    var equippedCharacter by remember { mutableStateOf(appPrefs.getString("equipped_character", "bee") ?: "bee") }
    var unlockedCharacters by remember {
        val savedSet = appPrefs.getStringSet("unlocked_characters", setOf("bee")) ?: setOf("bee")
        mutableStateOf(savedSet)
    }
    
    var equippedRoom by remember { mutableStateOf(appPrefs.getString("equipped_room", "hive") ?: "hive") }
    var unlockedRooms by remember {
        val savedSet = appPrefs.getStringSet("unlocked_rooms", setOf("hive")) ?: setOf("hive")
        mutableStateOf(savedSet)
    }
    
    var equippedFurniture by remember { mutableStateOf(appPrefs.getString("equipped_furniture", "") ?: "") }
    var unlockedFurnitures by remember {
        val savedSet = appPrefs.getStringSet("unlocked_furnitures", setOf("default")) ?: setOf("default")
        mutableStateOf(savedSet)
    }
    
    var showDecorDialog by remember { mutableStateOf(false) }
    
    // 출석 체크 자동 보너스 (하루 접속시 10포인트 적립)
    LaunchedEffect(username) {
        if (isLoggedIn) {
            val sdf = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
            val todayStr = sdf.format(java.util.Date())
            val lastAttendance = appPrefs.getString("last_attendance_date", "") ?: ""
            
            if (lastAttendance != todayStr) {
                val bonus = 10
                val newPoints = decorPoints + bonus
                decorPoints = newPoints
                appPrefs.edit()
                    .putInt("decor_points", newPoints)
                    .putString("last_attendance_date", todayStr)
                    .apply()
                android.widget.Toast.makeText(context, "🎉 내 정보 방문 보너스! 꾸미기 꿀 포인트 +${bonus}P 적립 완료! 🍯", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }


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
                            if (isLoggedIn) {
                                val isWallFurniture = equippedFurniture in setOf("chandelier", "clock", "frame", "neonsign", "antenna", "heart_lamp")
                                val isRideFurniture = equippedFurniture in setOf("bed", "sofa", "carpet", "cart")
                                
                                Box(
                                    modifier = Modifier.size(width = 96.dp, height = 92.dp),
                                    contentAlignment = Alignment.TopStart
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(84.dp)
                                            .clip(CircleShape)
                                            .background(Color.Transparent)
                                            .border(2.dp, MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f), CircleShape)
                                            .clickable { showDecorDialog = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        // 3D Isometric 룸 박스 Canvas 드로잉
                                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                            val w = size.width
                                            val h = size.height
                                            val wallH = h * 0.85f
                                            val floorY = wallH
                                            
                                            // 1. 방 바닥 배경색 (8대 테마 파스텔 스킨)
                                            val floorColor = when (equippedRoom) {
                                                "hive" -> Color(0xFFFFF9C4) // Lemon 벌집
                                                "forest" -> Color(0xFFE8F5E9) // 숲속 포근 연두
                                                "desert" -> Color(0xFFFFF3E0) // 사막 베이지
                                                "snow" -> Color(0xFFECEFF1) // 겨울 눈밭 화이트
                                                "cherry" -> Color(0xFFFFF0F5) // 파스텔 벚꽃
                                                "aquarium" -> Color(0xFFE0F7FA) // 수족관 아쿠아
                                                "cozy" -> Color(0xFFF5F5DC) // 베이지 코지
                                                "classic" -> Color(0xFFEFEBE9) // 브라운 클래식
                                                else -> Color(0xFFF5F5F5)
                                            }
                                            
                                            // 3D Isometric 바닥 패스 계산
                                            val floorPath = androidx.compose.ui.graphics.Path().apply {
                                                moveTo(w * 0.45f, h * 0.85f)
                                                lineTo(0f, floorY)
                                                lineTo(w * 0.45f, h)
                                                lineTo(w, floorY)
                                                close()
                                            }
                                            drawPath(path = floorPath, color = floorColor)
                                            
                                            // 3D 바닥 격자 타일선 드로잉
                                            val tileColor = Color.White.copy(alpha = 0.35f)
                                            val dxLeft = w * 0.45f
                                            val dyLeft = h * 0.15f
                                            val dxRight = w * 0.55f
                                            val dyRight = h * 0.15f
                                            
                                            // 왼쪽 격자 평행선들
                                            drawLine(
                                                color = tileColor,
                                                start = androidx.compose.ui.geometry.Offset(w * 0.25f, floorY),
                                                end = androidx.compose.ui.geometry.Offset(w * 0.25f + dxLeft, floorY + dyLeft),
                                                strokeWidth = 1f
                                            )
                                            drawLine(
                                                color = tileColor,
                                                start = androidx.compose.ui.geometry.Offset(w * 0.5f, floorY),
                                                end = androidx.compose.ui.geometry.Offset(w * 0.5f + dxLeft, floorY + dyLeft),
                                                strokeWidth = 1f
                                            )
                                            drawLine(
                                                color = tileColor,
                                                start = androidx.compose.ui.geometry.Offset(-w * 0.25f, floorY),
                                                end = androidx.compose.ui.geometry.Offset(-w * 0.25f + dxLeft, floorY + dyLeft),
                                                strokeWidth = 1f
                                            )

                                            // 오른쪽 격자 평행선들
                                            drawLine(
                                                color = tileColor,
                                                start = androidx.compose.ui.geometry.Offset(w * 0.75f, floorY),
                                                end = androidx.compose.ui.geometry.Offset(w * 0.75f - dxRight, floorY - dyRight),
                                                strokeWidth = 1f
                                            )
                                            drawLine(
                                                color = tileColor,
                                                start = androidx.compose.ui.geometry.Offset(w * 0.5f, floorY),
                                                end = androidx.compose.ui.geometry.Offset(w * 0.5f - dxRight, floorY - dyRight),
                                                strokeWidth = 1f
                                            )
                                            drawLine(
                                                color = tileColor,
                                                start = androidx.compose.ui.geometry.Offset(w * 1.25f, floorY),
                                                end = androidx.compose.ui.geometry.Offset(w * 1.25f - dxRight, floorY - dyRight),
                                                strokeWidth = 1f
                                            )
                                            
                                            // 3D 입체 걸레받이 몰딩 (Baseboard/Molding) 패스
                                            val moldingH = 6f
                                            val moldingLeft = androidx.compose.ui.graphics.Path().apply {
                                                moveTo(0f, floorY - moldingH)
                                                lineTo(w * 0.45f, h * 0.85f - moldingH)
                                                lineTo(w * 0.45f, h * 0.85f)
                                                lineTo(0f, floorY)
                                                close()
                                            }
                                            val moldingRight = androidx.compose.ui.graphics.Path().apply {
                                                moveTo(w * 0.45f, h * 0.85f - moldingH)
                                                lineTo(w, floorY - moldingH)
                                                lineTo(w, floorY)
                                                lineTo(w * 0.45f, h * 0.85f)
                                                close()
                                            }
                                            // 명암차가 적용된 몰딩 페인팅
                                            drawPath(path = moldingLeft, color = Color.White.copy(alpha = 0.38f))
                                            drawPath(path = moldingRight, color = Color.White.copy(alpha = 0.28f))

                                            // 벽면 정중앙 세로 모서리 3D 음영선 배선
                                            drawLine(
                                                color = Color.Black.copy(alpha = 0.04f),
                                                start = androidx.compose.ui.geometry.Offset(w * 0.45f, 0f),
                                                end = androidx.compose.ui.geometry.Offset(w * 0.45f, h * 0.85f),
                                                strokeWidth = 1.2f
                                            )
                                            
                                            // 벽 모퉁이 테두리 윤곽선 보정
                                            drawLine(
                                                color = Color.Black.copy(alpha = 0.05f),
                                                start = androidx.compose.ui.geometry.Offset(0f, floorY),
                                                end = androidx.compose.ui.geometry.Offset(w * 0.45f, h * 0.85f),
                                                strokeWidth = 1.5f
                                            )
                                            drawLine(
                                                color = Color.Black.copy(alpha = 0.05f),
                                                start = androidx.compose.ui.geometry.Offset(w * 0.45f, h * 0.85f),
                                                end = androidx.compose.ui.geometry.Offset(w, floorY),
                                                strokeWidth = 1.5f
                                            )
                                            drawLine(
                                                color = Color.Black.copy(alpha = 0.04f),
                                                start = androidx.compose.ui.geometry.Offset(w * 0.45f, h * 0.85f),
                                                end = androidx.compose.ui.geometry.Offset(w * 0.45f, h),
                                                strokeWidth = 2.0f
                                            )
                                        }

                                        // 2. 룸 테마별 파스텔 백드롭 실감 드로잉 매칭
                                        RoomInteriorBackdrop(
                                            roomId = equippedRoom,
                                            modifier = Modifier.matchParentSize()
                                        )

                                        // 🌟 Room theme signature landmark large icon projection (Centering centering!)
                                        val roomEmoji = remember(equippedRoom) {
                                            when (equippedRoom) {
                                                "hive" -> "🍯"
                                                "forest" -> "🌳"
                                                "desert" -> "🌵"
                                                "snow" -> "❄️"
                                                "cherry" -> "🌸"
                                                "aquarium" -> "🐠"
                                                "camp" -> "⛺"
                                                "hanok" -> "🏠"
                                                "space" -> "🌌"
                                                "aurora" -> "🌌"
                                                "gallery" -> "🖼️"
                                                "bamboo" -> "🎍"
                                                "xmas" -> "🎄"
                                                "beach" -> "🏖️"
                                                "castle" -> "🏰"
                                                else -> getRoomBackdropEmoji(equippedRoom)
                                            }
                                        }
                                        
                                        if (roomEmoji.isNotEmpty()) {
                                            Text(
                                                text = roomEmoji,
                                                fontSize = 44.sp,
                                                modifier = Modifier
                                                    .align(Alignment.Center)
                                                    .offset(y = (-8).dp)
                                                    .graphicsLayer { alpha = 0.35f }
                                            )
                                        }

                                        // 3. 해금 및 착용된 룸 가구 배치
                                        if (equippedFurniture.isNotEmpty()) {
                                            val furnitureEmoji = getFurnitureEmoji(equippedFurniture)
                                            
                                            when {
                                                isWallFurniture -> {
                                                    // 벽에 걸리는 가구 (예: 조명/시계/액자 등)
                                                    Text(
                                                        text = furnitureEmoji,
                                                        fontSize = 15.sp,
                                                        modifier = Modifier
                                                            .align(Alignment.TopCenter)
                                                            .offset(y = 8.dp)
                                                    )
                                                }
                                                isRideFurniture -> {
                                                    // 앉거나 탑승하는 가구 (예: 침대/소파/카트 등)
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.BottomCenter)
                                                            .offset(x = 0.dp, y = (-11).dp)
                                                            .size(width = 24.dp, height = 5.dp)
                                                            .background(Color.Black.copy(alpha = 0.12f), CircleShape)
                                                    )
                                                    Text(
                                                        text = furnitureEmoji,
                                                        fontSize = 18.sp,
                                                        modifier = Modifier
                                                            .align(Alignment.BottomCenter)
                                                            .offset(x = 0.dp, y = (-12).dp)
                                                    )
                                                }
                                                else -> {
                                                    // 일반 바닥 배치 가구
                                                    Box(
                                                        modifier = Modifier
                                                            .align(Alignment.BottomStart)
                                                            .offset(x = 10.dp, y = (-11).dp)
                                                            .size(width = 18.dp, height = 4.dp)
                                                            .background(Color.Black.copy(alpha = 0.12f), CircleShape)
                                                    )
                                                    Text(
                                                        text = furnitureEmoji,
                                                        fontSize = 16.sp,
                                                        modifier = Modifier
                                                            .align(Alignment.BottomStart)
                                                            .offset(x = 10.dp, y = (-12).dp)
                                                    )
                                                }
                                            }
                                        }

                                        // 4. [Wow-Factor] 캐릭터 둥실 움직임 맞춤 3D 소프트 그림자 크기 및 알파 동기화 (바닥 밀착 핏으로 하강)
                                        val currentOffsetFactor = (beeScale - 0.92f) / (1.08f - 0.92f)
                                        val shadowWidth = if (isRideFurniture) 16.dp else (18f + (currentOffsetFactor * 4f)).dp
                                        val shadowAlpha = (if (isRideFurniture) 0.18f else 0.16f) - (currentOffsetFactor * 0.05f)
                                        val shadowOffsetY = if (isRideFurniture) (-6).dp else (-1).dp
                                        
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .offset(x = 0.dp, y = shadowOffsetY)
                                                .size(width = shadowWidth, height = if (isRideFurniture) 4.dp else 5.dp)
                                                .background(Color.Black.copy(alpha = shadowAlpha), CircleShape)
                                        )

                                        // 5. 캐릭터 둥실 오프셋 계산 (가구 탑승 시 조금 높임)
                                        val charOffsetY = when {
                                            isRideFurniture -> (-8).dp + (beeScale * 2f - 1f).dp
                                            else -> (-2).dp + (beeScale * 2f - 1f).dp
                                        }

                                        EquippedAvatarView(
                                            charId = equippedCharacter,
                                            decorId = equippedItem,
                                            fontSize = 20.sp,
                                            modifier = Modifier
                                                .align(Alignment.BottomCenter)
                                                .offset(x = 0.dp, y = charOffsetY)
                                                .graphicsLayer {
                                                    scaleX = beeScale
                                                    scaleY = beeScale
                                                    rotationZ = beeRotation
                                                }
                                        )
                                    }

                                    // 🏠 룸 꾸미기 플로팅 미니 원형 뱃지 (프로필을 전혀 가리지 않도록 아바타 우측 하단에 세련되게 걸침!)
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .offset(x = (-4).dp, y = (-2).dp)
                                            .size(22.dp)
                                            .shadow(elevation = 2.dp, shape = CircleShape)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                                            .border(1.5.dp, Color.White, CircleShape)
                                            .clickable { showDecorDialog = true },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "🏠",
                                            fontSize = 11.sp,
                                            modifier = Modifier.offset(y = (-0.5).dp)
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(84.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f))
                                        .clickable { onNavigateToAuth() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        modifier = Modifier.size(36.dp),
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .align(Alignment.CenterVertically)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Start
                                    ) {
                                        Text(
                                            text = if (isLoggedIn) "$username 님" else "익명 임시 계정 님", 
                                            fontSize = 19.sp,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.weight(1f, fill = false)
                                        )
                                        if (!isLoggedIn) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                                contentDescription = "로그인",
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clickable { onNavigateToAuth() },
                                                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                                            )
                                        }
                                    }
                                    
                                    if (isLoggedIn) {
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Surface(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(20.dp))
                                                .clickable { showDecorDialog = true }
                                                .shadow(elevation = 1.dp, shape = RoundedCornerShape(20.dp)),
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "🍯 ${decorPoints} P",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = if (isLoggedIn) 0.12f else 0.05f)
                                ) {
                                    Text(
                                        text = if (isLoggedIn) honeyLevelName else "🔒 꿀 내공 혜택 비활성", 
                                        fontSize = 10.5.sp, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, letterSpacing = (-0.3).sp, 
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
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
                            text = "AI 추천 꿀딜",
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
                                        Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .align(Alignment.CenterVertically)
                            ) {
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

                    // 🔒 프리미엄 계정 안전 로그아웃 버튼 (나의 활동 리스트 최하단에 정갈한 빨간 글씨 메뉴로 통일성 있게 매핑)
                    if (isLoggedIn) {
                        val scope = rememberCoroutineScope()
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f),
                            thickness = 1.dp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        MyPageMenuItem(
                            icon = Icons.Default.ExitToApp,
                            title = "로그아웃",
                            textColor = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            iconColor = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                            onClick = {
                                scope.launch {
                                    AuthManager.logout(context)
                                    android.widget.Toast.makeText(context, "로그아웃 되어 guest 상태로 변경되었습니다. 🔒", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(32.dp)) // 최하단 안전 패딩
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



    
    if (showDecorDialog) {
        DecorShopDialog(
            currentPoints = decorPoints,
            equippedChar = equippedCharacter,
            equippedItem = equippedItem,
            equippedRoom = equippedRoom,
            equippedFurniture = equippedFurniture,
            unlockedItems = unlockedItems,
            unlockedCharacters = unlockedCharacters,
            unlockedRooms = unlockedRooms,
            unlockedFurnitures = unlockedFurnitures,
            isTestingAdmin = isTestingAdmin,
            onDismiss = { showDecorDialog = false },
            onEquipCharacter = { charId ->
                equippedCharacter = charId
                appPrefs.edit().putString("equipped_character", charId).apply()
            },
            onEquipItem = { itemId ->
                equippedItem = itemId
                appPrefs.edit().putString("equipped_item", itemId).apply()
            },
            onEquipRoom = { roomId ->
                equippedRoom = roomId
                appPrefs.edit().putString("equipped_room", roomId).apply()
            },
            onEquipFurniture = { furnitureId ->
                equippedFurniture = furnitureId
                appPrefs.edit().putString("equipped_furniture", furnitureId).apply()
            },
            onUnlockItem = { itemId, cost ->
                val newUnlocked = unlockedItems + itemId
                unlockedItems = newUnlocked
                val newPoints = if (isTestingAdmin) decorPoints else (decorPoints - cost).coerceAtLeast(0)
                decorPoints = newPoints
                appPrefs.edit()
                    .putStringSet("unlocked_items", newUnlocked)
                    .putInt("decor_points", newPoints)
                    .apply()
            },
            onUnlockCharacter = { charId, cost ->
                val newUnlocked = unlockedCharacters + charId
                unlockedCharacters = newUnlocked
                val newPoints = if (isTestingAdmin) decorPoints else (decorPoints - cost).coerceAtLeast(0)
                decorPoints = newPoints
                appPrefs.edit()
                    .putStringSet("unlocked_characters", newUnlocked)
                    .putInt("decor_points", newPoints)
                    .apply()
            },
            onUnlockRoom = { roomId, cost ->
                val newUnlocked = unlockedRooms + roomId
                unlockedRooms = newUnlocked
                val newPoints = if (isTestingAdmin) decorPoints else (decorPoints - cost).coerceAtLeast(0)
                decorPoints = newPoints
                appPrefs.edit()
                    .putStringSet("unlocked_rooms", newUnlocked)
                    .putInt("decor_points", newPoints)
                    .apply()
            },
            onUnlockFurniture = { furnitureId, cost ->
                val newUnlocked = unlockedFurnitures + furnitureId
                unlockedFurnitures = newUnlocked
                val newPoints = if (isTestingAdmin) decorPoints else (decorPoints - cost).coerceAtLeast(0)
                decorPoints = newPoints
                appPrefs.edit()
                    .putStringSet("unlocked_furnitures", newUnlocked)
                    .putInt("decor_points", newPoints)
                    .apply()
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
fun MyPageMenuItem(
    icon: ImageVector,
    title: String,
    value: String? = null,
    iconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    textColor: Color = MaterialTheme.colorScheme.onBackground,
    onClick: () -> Unit
) {
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
            tint = iconColor
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = title, 
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
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

// ==========================================
// 🏠 룸 꾸미기 & 아바타 샵 정밀 합성 렌더링 엔진 컴포저블 및 헬퍼 함수군
// ==========================================

data class DecorShopItem(
    val id: String,
    val name: String,
    val emoji: String,
    val price: Int,
    val category: String, // "char", "decor", "room", "furniture"
    val subCategory: String = "" // "hat", "glasses", "tool", "outfit"
)

object DecorRegistry {
    val items = listOf(
        // === 펫 동물 캐릭터 분양 30종 ===
        DecorShopItem("bee", "귀요미 아기꿀벌", "🐝", 0, "char"),
        DecorShopItem("chick", "삐약이 아기병아리", "🐥", 15, "char"),
        DecorShopItem("cat", "도도한 아기고양이", "🐱", 25, "char"),
        DecorShopItem("dog", "충직한 아기강아지", "🐶", 25, "char"),
        DecorShopItem("bear", "푸근한 꿀곰돌이", "🐻", 35, "char"),
        DecorShopItem("panda", "하루종일 판다", "🐼", 45, "char"),
        DecorShopItem("koala", "쿨쿨 잠만보 코알라", "🐨", 40, "char"),
        DecorShopItem("pig", "풍요로운 아기돼지", "🐷", 15, "char"),
        DecorShopItem("monkey", "지혜로운 아기원숭이", "🐵", 20, "char"),
        DecorShopItem("tiger", "용맹한 아기호랑이", "🐯", 50, "char"),
        DecorShopItem("lion", "초원의 왕 아기사자", "🦁", 55, "char"),
        DecorShopItem("fox", "영리한 꼬마여우", "🦊", 30, "char"),
        DecorShopItem("rabbit", "러블리 꼬마토끼", "🐰", 30, "char"),
        DecorShopItem("frog", "노래하는 개구리", "🐸", 20, "char"),
        DecorShopItem("butterfly", "봄날의 나비", "🦋", 25, "char"),
        DecorShopItem("penguin", "뒤뚱뒤뚱 아기펭귄", "🐧", 40, "char"),
        DecorShopItem("duck", "둥둥 아기오리", "🐤", 15, "char"),
        DecorShopItem("swan", "우아한 백조", "🦢", 45, "char"),
        DecorShopItem("owl", "밤의 수호자 부엉이", "🦉", 35, "char"),
        DecorShopItem("eagle", "하늘의 제왕 독수리", "🦅", 60, "char"),
        DecorShopItem("mouse", "영리한 쥐", "🐭", 10, "char"),
        DecorShopItem("hamster", "볼빵빵 햄스터", "🐹", 15, "char"),
        DecorShopItem("wolf", "고독한 늑대", "🐺", 50, "char"),
        DecorShopItem("cow", "단순한 얼룩소", "🐮", 30, "char"),
        DecorShopItem("sheep", "몽실몽실 면양", "🐑", 25, "char"),
        DecorShopItem("horse", "질주하는 야생마", "🐴", 40, "char"),
        DecorShopItem("deer", "꽃사슴", "🦌", 35, "char"),
        DecorShopItem("squirrel", "도토리 다람쥐", "🐿️", 20, "char"),
        DecorShopItem("chicken", "새벽을 깨우는 닭", "🐔", 15, "char"),
        DecorShopItem("sloth", "느긋한 나무늘보", "🦥", 50, "char"),

        // === 펫 아바타 장식 상점 110종 ===
        // 1. 모자 (hat)
        DecorShopItem("hat_fedora", "클래식 페도라 모자", "🎩", 15, "decor", "hat"),
        DecorShopItem("hat_straw", "시골 감성 밀짚모자", "👒", 12, "decor", "hat"),
        DecorShopItem("hat_crown", "황금빛 영광의 왕관", "👑", 50, "decor", "hat"),
        DecorShopItem("hat_tiara", "실버 티아라 왕관", "👑", 45, "decor", "hat"),
        DecorShopItem("hat_party", "알록달록 파티 고깔모자", "🎉", 10, "decor", "hat"),
        DecorShopItem("hat_santa", "크리스마스 루돌프 모자", "🎅", 18, "decor", "hat"),
        DecorShopItem("hat_chef", "프라이팬 모자", "🍳", 15, "decor", "hat"),
        DecorShopItem("hat_police", "경찰 사이렌 모자", "👮", 25, "decor", "hat"),
        DecorShopItem("hat_wizard", "신비로운 마법 고깔모자", "🧙", 35, "decor", "hat"),
        DecorShopItem("hat_detective", "돋보기 탐정 모자", "🕵️", 20, "decor", "hat"),
        DecorShopItem("hat_cowboy", "황금 별 보안관 모자", "🤠", 22, "decor", "hat"),
        DecorShopItem("hat_pirate", "카리브해 해적 선장모자", "🏴‍☠️", 30, "decor", "hat"),
        DecorShopItem("hat_grad", "학사모", "🎓", 25, "decor", "hat"),
        DecorShopItem("hat_beret", "프랑스 화가 베레모", "🎨", 15, "decor", "hat"),
        DecorShopItem("hat_helmet", "우주 비행사 헬멧", "🧑‍🚀", 40, "decor", "hat"),
        DecorShopItem("hat_flower", "봄날의 생화 머리띠", "🌸", 10, "decor", "hat"),
        DecorShopItem("hat_ribbon", "헤어 밴드 리본", "🎀", 8, "decor", "hat"),
        DecorShopItem("hat_sprout", "파릇파릇 새싹 머리핀", "🌱", 5, "decor", "hat"),
        DecorShopItem("hat_cherry", "상큼발랄 더블 체리핀", "🍒", 8, "decor", "hat"),
        DecorShopItem("hat_sunflower", "해바라기 꽃 머리띠", "🌻", 12, "decor", "hat"),
        DecorShopItem("hat_halo", "반짝 천사 천사링", "😇", 35, "decor", "hat"),
        DecorShopItem("hat_devil_horn", "러블리 레드 악마 뿔", "😈", 30, "decor", "hat"),
        DecorShopItem("hat_pumpkin", "할로윈 잭오랜턴 모자", "🎃", 20, "decor", "hat"),
        DecorShopItem("hat_witch", "신비로운 꼬마 마녀 모자", "🧙‍♀️", 32, "decor", "hat"),
        DecorShopItem("hat_earmuff", "겨울철 뽀송 귀도리", "🎧", 15, "decor", "hat"),
        DecorShopItem("hat_knit", "따스한 털실 비니", "🧶", 12, "decor", "hat"),
        DecorShopItem("hat_bunny", "말랑말랑 토끼 귀 머리띠", "🐰", 18, "decor", "hat"),
        DecorShopItem("hat_bear_ear", "귀여운 곰돌이 귀 머리띠", "🐻", 18, "decor", "hat"),
        DecorShopItem("hat_frog_cap", "초록 개구리 모자", "🐸", 15, "decor", "hat"),
        DecorShopItem("hat_cat_ear", "고양이 야옹이 머리띠", "🐱", 18, "decor", "hat"),
        DecorShopItem("hat_crying", "슬픈 눈물 구름 핀", "☁️", 8, "decor", "hat"),
        DecorShopItem("hat_sleeping", "잠꾸러기 졸음 핀", "💤", 6, "decor", "hat"),
        DecorShopItem("hat_sparkle", "반짝반짝 감성 데코핀", "✨", 10, "decor", "hat"),
        DecorShopItem("hat_bulb", "아이디어 번뜩 전구핀", "💡", 15, "decor", "hat"),
        DecorShopItem("hat_heart", "사랑이 가득 러브핀", "💖", 12, "decor", "hat"),
        DecorShopItem("hat_rainbow", "오색 무지개 머리띠", "🌈", 20, "decor", "hat"),
        DecorShopItem("hat_cloud", "포근포근 하얀 구름핀", "☁️", 10, "decor", "hat"),
        DecorShopItem("hat_balloon", "둥실 바람 풍선", "🎈", 12, "decor", "hat"),
        DecorShopItem("hat_clover", "네잎클로버 행운핀", "🍀", 8, "decor", "hat"),
        DecorShopItem("hat_music", "신나는 음표 음악핀", "🎵", 10, "decor", "hat"),

        // 2. 안경 (glasses)
        DecorShopItem("glasses_normal", "스마트 뿔테안경", "👓", 10, "decor", "glasses"),
        DecorShopItem("sunglasses", "레트로 선글라스", "🕶️", 18, "decor", "glasses"),
        DecorShopItem("glasses_heart", "러브 하트 안경", "😍", 20, "decor", "glasses"),
        DecorShopItem("glasses_gold", "골드 라운드 안경", "👓", 35, "decor", "glasses"),
        DecorShopItem("glasses_monocle", "영국 신사 모노클", "🧐", 25, "decor", "glasses"),
        DecorShopItem("glasses_star", "스타 선글라스", "😎", 22, "decor", "glasses"),
        DecorShopItem("glasses_cyber", "미래 고글 안경", "🥽", 30, "decor", "glasses"),
        DecorShopItem("glasses_swirl", "뱅글뱅글 안경", "🌀", 12, "decor", "glasses"),
        DecorShopItem("glasses_swim", "수영장 물안경", "🥽", 15, "decor", "glasses"),
        DecorShopItem("glasses_mask", "무도회 가면", "🎭", 28, "decor", "glasses"),
        DecorShopItem("glasses_reading", "독서 돋보기", "📖", 12, "decor", "glasses"),
        DecorShopItem("glasses_cool", "바다 스포츠 선글라스", "🕶️", 18, "decor", "glasses"),
        DecorShopItem("glasses_cat_eye", "캣츠아이 패션안경", "👓", 15, "decor", "glasses"),
        DecorShopItem("glasses_aviator", "보잉 선글라스", "🕶️", 22, "decor", "glasses"),
        DecorShopItem("glasses_rimless", "라운드 무테안경", "👓", 14, "decor", "glasses"),
        DecorShopItem("glasses_3d", "영화관 3D 입체안경", "🕶️", 10, "decor", "glasses"),
        DecorShopItem("glasses_sleep", "꿀잠 수면 안대", "💤", 8, "decor", "glasses"),
        DecorShopItem("glasses_nerd", "너드 공부 안경", "🤓", 12, "decor", "glasses"),
        DecorShopItem("glasses_spy", "블랙 보안 요원 선글라스", "🕶️", 24, "decor", "glasses"),
        DecorShopItem("glasses_cute", "동글동글 안경", "👓", 10, "decor", "glasses"),

        // 3. 도구 (tool)
        DecorShopItem("wand", "마법 요술봉", "🪄", 30, "decor", "tool"),
        DecorShopItem("rose", "붉은 장미 한 송이", "🌹", 15, "decor", "tool"),
        DecorShopItem("magnifier", "진실의 돋보기", "🔍", 12, "decor", "tool"),
        DecorShopItem("guitar", "감성 통기타", "🎸", 40, "decor", "tool"),
        DecorShopItem("sword", "용사의 전설적인 검", "🗡️", 45, "decor", "tool"),
        DecorShopItem("shield", "골든 이지스 방패", "🛡️", 40, "decor", "tool"),
        DecorShopItem("camera", "빈티지 필름 카메라", "📷", 25, "decor", "tool"),
        DecorShopItem("umbrella", "파스텔 노란 우산", "🌂", 12, "decor", "tool"),
        DecorShopItem("balloon_hand", "오색 바람 풍선 다발", "🎈", 8, "decor", "tool"),
        DecorShopItem("coffeecup", "따스한 테이크아웃 커피", "☕", 10, "decor", "tool"),
        DecorShopItem("fan", "시원한 전통 부채", "🪭", 15, "decor", "tool"),
        DecorShopItem("icecream", "콘 아이스크림", "🍦", 8, "decor", "tool"),
        DecorShopItem("donut", "인기 초코 도넛", "🍩", 7, "decor", "tool"),
        DecorShopItem("clover_hand", "행운의 네잎클로버", "🍀", 8, "decor", "tool"),
        DecorShopItem("brush", "마법의 청소 빗자루", "🧹", 18, "decor", "tool"),
        DecorShopItem("lantern", "야간 감성 랜턴", "🏮", 22, "decor", "tool"),
        DecorShopItem("candy", "롤리팝 막대사탕", "🍭", 6, "decor", "tool"),
        DecorShopItem("hammer", "천둥 신의 망치", "🔨", 30, "decor", "tool"),
        DecorShopItem("potion", "신비로운 보라 마법물약", "🧪", 25, "decor", "tool"),
        DecorShopItem("micro", "골든 노래방 마이크", "🎤", 35, "decor", "tool"),
        DecorShopItem("book", "마법 마도서 책", "📖", 15, "decor", "tool"),
        DecorShopItem("pizza", "마르게리타 피자 조각", "🍕", 10, "decor", "tool"),
        DecorShopItem("beer", "축배 시원한 생맥주", "🍺", 15, "decor", "tool"),
        DecorShopItem("surf", "파도 서핑 보드", "🏄", 25, "decor", "tool"),
        DecorShopItem("clock_hand", "골든 회중시계", "🪙", 20, "decor", "tool"),

        // 4. 의상/기타 (outfit)
        DecorShopItem("wings", "깃털 날개", "🪶", 40, "decor", "outfit"),
        DecorShopItem("scarf", "크리스마스 머플러 목도리", "🧣", 18, "decor", "outfit"),
        DecorShopItem("gold_chain", "힙합 골드 목걸이", "🪙", 30, "decor", "outfit"),
        DecorShopItem("bowtie_red", "붉은 나비넥타이 보타이", "🎀", 10, "decor", "outfit"),
        DecorShopItem("backpack", "아웃도어 백팩 배낭", "🎒", 20, "decor", "outfit"),
        DecorShopItem("ribbon_hair", "뒷머리 빅 리본끈", "🎀", 12, "decor", "outfit"),
        DecorShopItem("necklace_pearl", "진주 진주목걸이", "📿", 35, "decor", "outfit"),
        DecorShopItem("suspenders", "코지 멜빵 코디세트", "👖", 15, "decor", "outfit"),
        DecorShopItem("cape", "히어로 RED 망토", "🦸", 30, "decor", "outfit"),
        DecorShopItem("badge", "보안관 뱃지", "🛡️", 25, "decor", "outfit"),
        DecorShopItem("crown_jewel", "황실 보석 루비 반지", "💍", 50, "decor", "outfit"),
        DecorShopItem("earring_star", "별빛 귀걸이", "⭐", 15, "decor", "outfit"),
        DecorShopItem("headphones_neon", "네온 게이밍 헤드셋", "🎧", 28, "decor", "outfit"),
        DecorShopItem("sunglasses_aviator", "빈티지 보잉 선글라스", "🕶️", 22, "decor", "outfit"),
        DecorShopItem("mask_party", "가면무도회 고양이 가면", "🎭", 25, "decor", "outfit"),
        DecorShopItem("boots", "방한 털 어그부츠", "👢", 18, "decor", "outfit"),
        DecorShopItem("glasses_retro", "라운드 골드 안경", "👓", 12, "decor", "outfit"),
        DecorShopItem("tie_stripe", "회사원 신사 스트라이프 넥타이", "👔", 15, "decor", "outfit"),
        DecorShopItem("scarf_winter", "눈사람 털 목도리", "🧣", 16, "decor", "outfit"),
        DecorShopItem("ufo_mini", "미니 UFO 우주선 탑승", "🛸", 45, "decor", "outfit"),
        DecorShopItem("surfboard", "바다 서핑보드", "🏄", 28, "decor", "outfit"),
        DecorShopItem("tube", "바다 튜브", "🛟", 15, "decor", "outfit"),
        DecorShopItem("tie", "슬림 블랙 넥타이", "👔", 15, "decor", "outfit"),
        DecorShopItem("necklace", "블링 체인 목걸이", "📿", 30, "decor", "outfit"),
        DecorShopItem("earring", "크리스탈 샹들리에 귀걸이", "💎", 45, "decor", "outfit"),

        // === 룸 및 배경 테마 30종 ===
        DecorShopItem("hive", "노란 벌집 방", "🍯", 0, "room"),
        DecorShopItem("forest", "초록 숲속 방", "🌳", 40, "room"),
        DecorShopItem("cloud", "뭉게구름 방", "☁️", 60, "room"),
        DecorShopItem("space", "은하 우주 방", "🌌", 80, "room"),
        DecorShopItem("desert", "황금 사막 방", "🌵", 50, "room"),
        DecorShopItem("snow", "민트 겨울 방", "❄️", 70, "room"),
        DecorShopItem("cherry", "핑크 벚꽃 방", "🌸", 65, "room"),
        DecorShopItem("aquarium", "민트 수족관 방", "🐠", 75, "room"),
        DecorShopItem("beach", "해변 파도 방", "🏖️", 60, "room"),
        DecorShopItem("aurora", "오로라 밤하늘 방", "🌌", 90, "room"),
        DecorShopItem("castle", "동화 속 성 방", "🏰", 80, "room"),
        DecorShopItem("hanok", "한옥 격자 방", "🏠", 75, "room"),
        DecorShopItem("camp", "인디언 텐트 캠핑 방", "⛺", 65, "room"),
        DecorShopItem("gallery", "미니멀 액자 미술관 방", "🖼️", 70, "room"),
        DecorShopItem("bamboo", "싱그러운 대나무숲 방", "🎋", 55, "room"),
        DecorShopItem("xmas", "따스한 크리스마스 전구 방", "🎄", 85, "room"),

        // === 가구 가구 데코 ===
        DecorShopItem("bed", "아담한 1인용 침대", "🛏️", 30, "furniture"),
        DecorShopItem("sofa", "푹신한 코지 소파", "🛋️", 25, "furniture"),
        DecorShopItem("carpet", "화이트 원형 카펫", "🧹", 15, "furniture"),
        DecorShopItem("cart", "피크닉 미니 카트", "🛒", 20, "furniture"),
        DecorShopItem("chandelier", "황실 크리스탈 샹들리에", "👑", 50, "furniture"),
        DecorShopItem("clock", "레트로 뻐꾸기 벽시계", "🕰️", 18, "furniture"),
        DecorShopItem("frame", "파스텔 명화 액자", "🖼️", 15, "furniture"),
        DecorShopItem("neonsign", "수정구 네온사인", "🔮", 30, "furniture"),
        DecorShopItem("antenna", "우주 신호 안테나", "📡", 35, "furniture"),
        DecorShopItem("heart_lamp", "러브 하트 무드등", "💝", 22, "furniture"),
        DecorShopItem("plant", "테이블 몬스테라 화분", "🪴", 12, "furniture"),
        DecorShopItem("tv", "미니 브라운관 아날로그 TV", "📺", 28, "furniture"),
        DecorShopItem("piano", "미니어처 그랜드 피아노", "🎹", 45, "furniture"),
        DecorShopItem("guitar", "아쿠스틱 미니 우쿠렐레 기타", "🎸", 18, "furniture"),
        DecorShopItem("bookshelf", "우드 미니 책꽂이 서가", "📚", 25, "furniture"),
        DecorShopItem("fishbowl", "동글동글 금붕어 어항", "🐳", 20, "furniture"),
        DecorShopItem("window", "화이트 우드 창문 프레임", "🪟", 22, "furniture"),
        DecorShopItem("desk", "빈티지 우드 1인용 책상", "✍️", 30, "furniture"),
        DecorShopItem("mirror", "앤틱 원형 전신 거울", "🪞", 16, "furniture"),
        DecorShopItem("hanger", "코트와 목도리 스탠드 행거", "🧥", 18, "furniture")
    )
}

fun getRoomGradient(roomId: String): List<Color> {
    return when (roomId) {
        "hive" -> listOf(Color(0xFFFFF9C4), Color(0xFFFFF59D)) // 레몬 벌집
        "forest" -> listOf(Color(0xFFE8F5E9), Color(0xFFC8E6C9)) // 포근 연두
        "desert" -> listOf(Color(0xFFFFF3E0), Color(0xFFFFE0B2)) // 사막 베이지
        "snow" -> listOf(Color(0xFFECEFF1), Color(0xFFCFD8DC)) // 겨울 눈밭
        "cherry" -> listOf(Color(0xFFFFF0F5), Color(0xFFFFD1DC)) // 파스텔 벚꽃
        "aquarium" -> listOf(Color(0xFFE0F7FA), Color(0xFFB2EBF2)) // 아쿠아 민트
        "cozy" -> listOf(Color(0xFFF5F5DC), Color(0xFFEFEBE9)) // 베이지 코지
        "classic" -> listOf(Color(0xFFEFEBE9), Color(0xFFD7CCC8)) // 브라운 클래식
        else -> listOf(Color(0xFFF5F5F5), Color(0xFFE0E0E0))
    }
}

fun getRoomBackdropEmoji(roomId: String): String {
    return when (roomId) {
        "hive" -> "🐝"
        "forest" -> "🌳"
        "desert" -> "🌵"
        "snow" -> "⛄"
        "cherry" -> "🌸"
        "aquarium" -> "🐠"
        "cozy" -> "🛋️"
        "classic" -> "📜"
        else -> "🏠"
    }
}

fun getFurnitureEmoji(furnitureId: String): String {
    return when (furnitureId) {
        "bed" -> "🛏️"
        "sofa" -> "🛋️"
        "carpet" -> "🧹"
        "cart" -> "🛒"
        "chandelier" -> "👑"
        "clock" -> "🕰️"
        "frame" -> "🖼️"
        "neonsign" -> "🔮"
        "antenna" -> "📡"
        "heart_lamp" -> "💝"
        "plant" -> "🪴"
        "tv" -> "📺"
        "piano" -> "🎹"
        "guitar" -> "🎸"
        "bookshelf" -> "📚"
        "fishbowl" -> "🐳"
        "window" -> "🪟"
        "desk" -> "✍️"
        "mirror" -> "🪞"
        "hanger" -> "🧥"
        else -> ""
    }
}

fun getCharacterEmoji(charId: String): String {
    return DecorRegistry.items.firstOrNull { it.id == charId }?.emoji ?: "🐴"
}

fun getDecorEmoji(decorId: String): String {
    return DecorRegistry.items.firstOrNull { it.id == decorId }?.emoji ?: ""
}

@Composable
fun RoomInteriorBackdrop(roomId: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(
        modifier = modifier.fillMaxSize()
    ) {
        val w = size.width
        val h = size.height
        val wallH = h * 0.85f // 바닥과의 경계선 위쪽 3D 입체벽 높이
        val floorY = wallH

        // 1. 방 테마별 3D 입체 그라데이션 벽지 브러시 구성
        val colors = getRoomGradient(roomId)
        val leftBrush = androidx.compose.ui.graphics.Brush.linearGradient(
            colors = colors,
            start = androidx.compose.ui.geometry.Offset(0f, 0f),
            end = androidx.compose.ui.geometry.Offset(w * 0.45f, wallH)
        )
        val rightBrush = androidx.compose.ui.graphics.Brush.linearGradient(
            colors = colors.map { it.copy(alpha = 0.85f) }, // 명암 음영차가 나도록 우측 벽은 살짝 어둡게
            start = androidx.compose.ui.geometry.Offset(w * 0.45f, 0f),
            end = androidx.compose.ui.geometry.Offset(w, wallH)
        )

        // 3D Isometric 입체 벽 패스 계산
        val leftWallPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, 0f)
            lineTo(w * 0.45f, 0f)
            lineTo(w * 0.45f, wallH)
            lineTo(0f, floorY)
            close()
        }
        val rightWallPath = androidx.compose.ui.graphics.Path().apply {
            moveTo(w * 0.45f, 0f)
            lineTo(w, 0f)
            lineTo(w, floorY)
            lineTo(w * 0.45f, wallH)
            close()
        }

        // 명암차가 조화롭게 가미된 3D 입체 벽지 그라디언트 페인팅!
        drawPath(path = leftWallPath, brush = leftBrush)
        drawPath(path = rightWallPath, brush = rightBrush)

        when (roomId) {
            "hive", "yellow" -> {
                // 노란 벌집 방
                val honeycombColor = Color.White.copy(alpha = 0.08f)
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.2f, wallH * 0.2f)
                    lineTo(w * 0.3f, wallH * 0.15f)
                    lineTo(w * 0.4f, wallH * 0.2f)
                    lineTo(w * 0.4f, wallH * 0.35f)
                    lineTo(w * 0.3f, wallH * 0.4f)
                    lineTo(w * 0.2f, wallH * 0.35f)
                    close()
                    
                    moveTo(w * 0.6f, wallH * 0.3f)
                    lineTo(w * 0.7f, wallH * 0.25f)
                    lineTo(w * 0.8f, wallH * 0.3f)
                    lineTo(w * 0.8f, wallH * 0.45f)
                    lineTo(w * 0.7f, wallH * 0.5f)
                    lineTo(w * 0.6f, wallH * 0.45f)
                    close()
                }
                drawPath(path = path, color = honeycombColor, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f))
                
                val honeyColor = Color(0xFFFFD54F).copy(alpha = 0.22f)
                val dripPath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.75f, 0f)
                    lineTo(w * 0.75f, wallH * 0.15f)
                    cubicTo(w * 0.75f, wallH * 0.22f, w * 0.79f, wallH * 0.22f, w * 0.79f, wallH * 0.15f)
                    lineTo(w * 0.79f, 0f)
                    close()
                }
                drawPath(path = dripPath, color = honeyColor)
                drawCircle(color = honeyColor, radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(w * 0.77f, wallH * 0.21f))
            }
            "forest", "green" -> {
                // 포근 초록 숲속 방
                val treeColor = Color.White.copy(alpha = 0.12f)
                val path1 = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.1f, wallH * 0.75f)
                    lineTo(w * 0.22f, wallH * 0.25f)
                    lineTo(w * 0.34f, wallH * 0.75f)
                    close()
                }
                val path2 = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.62f, wallH * 0.8f)
                    lineTo(w * 0.74f, wallH * 0.3f)
                    lineTo(w * 0.86f, wallH * 0.8f)
                    close()
                }
                drawPath(path = path1, color = treeColor)
                drawPath(path = path2, color = treeColor)
                
                val mushroomColor = Color.White.copy(alpha = 0.25f)
                drawCircle(color = mushroomColor, radius = 5.dp.toPx(), center = androidx.compose.ui.geometry.Offset(w * 0.8f, wallH * 0.85f))
                drawRoundRect(color = mushroomColor, topLeft = androidx.compose.ui.geometry.Offset(w * 0.79f, wallH * 0.85f), size = androidx.compose.ui.geometry.Size(2.dp.toPx(), 6.dp.toPx()))
            }
            "cloud", "blue" -> {
                // 뭉게구름 방
                val cloudColor = Color.White.copy(alpha = 0.28f)
                drawCircle(color = cloudColor, radius = w * 0.1f, center = androidx.compose.ui.geometry.Offset(w * 0.2f, wallH * 0.35f))
                drawCircle(color = cloudColor, radius = w * 0.14f, center = androidx.compose.ui.geometry.Offset(w * 0.3f, wallH * 0.4f))
                drawCircle(color = cloudColor, radius = w * 0.1f, center = androidx.compose.ui.geometry.Offset(w * 0.4f, wallH * 0.42f))
                
                drawCircle(color = cloudColor, radius = w * 0.07f, center = androidx.compose.ui.geometry.Offset(w * 0.75f, wallH * 0.25f))
                drawCircle(color = cloudColor, radius = w * 0.09f, center = androidx.compose.ui.geometry.Offset(w * 0.82f, wallH * 0.28f))
                
                val rainbowColor = Color.White.copy(alpha = 0.12f)
                drawCircle(
                    color = rainbowColor,
                    radius = w * 0.22f,
                    center = androidx.compose.ui.geometry.Offset(w * 0.5f, wallH * 0.15f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx())
                )
            }
            "space", "purple" -> {
                // 은하 우주 방
                val starColor = Color.White.copy(alpha = 0.25f)
                drawCircle(color = starColor, radius = 2f, center = androidx.compose.ui.geometry.Offset(w * 0.15f, wallH * 0.2f))
                drawCircle(color = starColor, radius = 3f, center = androidx.compose.ui.geometry.Offset(w * 0.5f, wallH * 0.15f))
                drawCircle(color = starColor, radius = 2f, center = androidx.compose.ui.geometry.Offset(w * 0.85f, wallH * 0.3f))
                
                val planetColor = Color.White.copy(alpha = 0.12f)
                drawCircle(color = planetColor, radius = w * 0.09f, center = androidx.compose.ui.geometry.Offset(w * 0.72f, wallH * 0.32f))
                drawLine(
                    color = planetColor,
                    start = androidx.compose.ui.geometry.Offset(w * 0.6f, wallH * 0.36f),
                    end = androidx.compose.ui.geometry.Offset(w * 0.84f, wallH * 0.28f),
                    strokeWidth = 2f
                )
            }
            "desert", "orange" -> {
                // 황금 사막 방
                val duneColor = Color.White.copy(alpha = 0.12f)
                val path = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, wallH * 0.8f)
                    quadraticBezierTo(w * 0.3f, wallH * 0.5f, w * 0.6f, wallH * 0.75f)
                    quadraticBezierTo(w * 0.8f, wallH * 0.85f, w, wallH * 0.65f)
                    lineTo(w, wallH)
                    lineTo(0f, wallH)
                    close()
                }
                drawPath(path = path, color = duneColor)
                
                drawCircle(
                    color = Color.White.copy(alpha = 0.15f),
                    radius = w * 0.07f,
                    center = androidx.compose.ui.geometry.Offset(w * 0.8f, wallH * 0.25f)
                )
                
                val cactusColor = Color.White.copy(alpha = 0.25f)
                drawRoundRect(
                    color = cactusColor,
                    topLeft = androidx.compose.ui.geometry.Offset(w * 0.75f, wallH * 0.66f),
                    size = androidx.compose.ui.geometry.Size(w * 0.04f, wallH * 0.22f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )
                drawRoundRect(
                    color = cactusColor,
                    topLeft = androidx.compose.ui.geometry.Offset(w * 0.16f, wallH * 0.76f),
                    size = androidx.compose.ui.geometry.Size(w * 0.03f, wallH * 0.12f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }
            "snow", "mint" -> {
                // 민트 겨울 방
                val flakeColor = Color.White.copy(alpha = 0.18f)
                val drawSnowflake = { cx: Float, cy: Float, size: Float ->
                    drawLine(color = flakeColor, start = androidx.compose.ui.geometry.Offset(cx - size, cy), end = androidx.compose.ui.geometry.Offset(cx + size, cy), strokeWidth = 1f)
                    drawLine(color = flakeColor, start = androidx.compose.ui.geometry.Offset(cx, cy - size), end = androidx.compose.ui.geometry.Offset(cx, cy + size), strokeWidth = 1f)
                }
                drawSnowflake(w * 0.25f, wallH * 0.3f, 8f)
                drawSnowflake(w * 0.75f, wallH * 0.45f, 10f)
                
                val snowmanColor = Color.White.copy(alpha = 0.25f)
                drawCircle(color = snowmanColor, radius = 6.dp.toPx(), center = androidx.compose.ui.geometry.Offset(w * 0.18f, wallH * 0.84f))
                drawCircle(color = snowmanColor, radius = 4.dp.toPx(), center = androidx.compose.ui.geometry.Offset(w * 0.18f, wallH * 0.78f))
            }
            "cherry", "sweet", "pink" -> {
                // 핑크 벚꽃 방
                val branchColor = Color.White.copy(alpha = 0.15f)
                val branchPath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, 0f)
                    quadraticBezierTo(w * 0.2f, wallH * 0.1f, w * 0.38f, wallH * 0.18f)
                    quadraticBezierTo(w * 0.44f, wallH * 0.22f, w * 0.52f, wallH * 0.2f)
                }
                drawPath(
                    path = branchPath,
                    color = branchColor,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
                
                val flowerColor = Color.White.copy(alpha = 0.28f)
                val drawBlossom = { cx: Float, cy: Float, r: Float ->
                    drawCircle(color = flowerColor, radius = r, center = androidx.compose.ui.geometry.Offset(cx, cy))
                    drawCircle(color = flowerColor, radius = r * 0.8f, center = androidx.compose.ui.geometry.Offset(cx - r * 0.8f, cy - r * 0.3f))
                    drawCircle(color = flowerColor, radius = r * 0.8f, center = androidx.compose.ui.geometry.Offset(cx + r * 0.8f, cy + r * 0.3f))
                    drawCircle(color = flowerColor, radius = r * 0.8f, center = androidx.compose.ui.geometry.Offset(cx - r * 0.3f, cy + r * 0.8f))
                    drawCircle(color = flowerColor, radius = r * 0.8f, center = androidx.compose.ui.geometry.Offset(cx + r * 0.3f, cy - r * 0.8f))
                }
                drawBlossom(w * 0.22f, wallH * 0.11f, 5.dp.toPx())
                drawBlossom(w * 0.38f, wallH * 0.18f, 6.dp.toPx())
                
                val petalColor = Color.White.copy(alpha = 0.32f)
                val drawPetal = { cx: Float, cy: Float ->
                    val petalPath = androidx.compose.ui.graphics.Path().apply {
                        moveTo(cx, cy)
                        cubicTo(cx - 3f, cy - 3f, cx - 6f, cy, cx, cy + 5f)
                        cubicTo(cx + 6f, cy, cx + 3f, cy - 3f, cx, cy)
                        close()
                    }
                    drawPath(path = petalPath, color = petalColor)
                }
                drawPetal(w * 0.22f, wallH * 0.42f)
                drawPetal(w * 0.64f, wallH * 0.3f)
                drawPetal(w * 0.78f, wallH * 0.55f)
            }
            "aquarium", "cyan" -> {
                // 민트 수족관 방
                val bubbleColor = Color.White.copy(alpha = 0.18f)
                drawCircle(color = bubbleColor, radius = 3f, center = androidx.compose.ui.geometry.Offset(w * 0.25f, wallH * 0.5f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f))
                drawCircle(color = bubbleColor, radius = 5f, center = androidx.compose.ui.geometry.Offset(w * 0.28f, wallH * 0.4f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f))
                drawCircle(color = bubbleColor, radius = 4f, center = androidx.compose.ui.geometry.Offset(w * 0.74f, wallH * 0.35f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1f))
                
                val fishColor = Color.White.copy(alpha = 0.16f)
                val fishPath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.6f, wallH * 0.45f)
                    quadraticBezierTo(w * 0.65f, wallH * 0.42f, w * 0.7f, wallH * 0.45f)
                    lineTo(w * 0.72f, wallH * 0.43f)
                    lineTo(w * 0.72f, wallH * 0.47f)
                    lineTo(w * 0.7f, wallH * 0.45f)
                    quadraticBezierTo(w * 0.65f, wallH * 0.48f, w * 0.6f, wallH * 0.45f)
                    close()
                }
                drawPath(path = fishPath, color = fishColor)
            }
            "beach", "ocean", "sea", "blue2" -> {
                // 해변 방
                val waveColor = Color.White.copy(alpha = 0.18f)
                val wavePath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, wallH * 0.8f)
                    quadraticBezierTo(w * 0.25f, wallH * 0.72f, w * 0.5f, wallH * 0.8f)
                    quadraticBezierTo(w * 0.75f, wallH * 0.88f, w, wallH * 0.8f)
                    lineTo(w, wallH)
                    lineTo(0f, wallH)
                    close()
                }
                drawPath(path = wavePath, color = waveColor)
                
                val treeColor = Color.White.copy(alpha = 0.15f)
                val trunkPath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.15f, wallH * 0.82f)
                    quadraticBezierTo(w * 0.18f, wallH * 0.6f, w * 0.12f, wallH * 0.45f)
                    lineTo(w * 0.14f, wallH * 0.45f)
                    quadraticBezierTo(w * 0.2f, wallH * 0.6f, w * 0.17f, wallH * 0.82f)
                    close()
                }
                drawPath(path = trunkPath, color = treeColor)
                drawCircle(color = treeColor, radius = w * 0.05f, center = androidx.compose.ui.geometry.Offset(w * 0.08f, wallH * 0.42f))
                drawCircle(color = treeColor, radius = w * 0.06f, center = androidx.compose.ui.geometry.Offset(w * 0.16f, wallH * 0.40f))
            }
            "aurora" -> {
                // 오로라 방
                val auroraColor = Color.White.copy(alpha = 0.12f)
                val auroraPath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, wallH * 0.3f)
                    quadraticBezierTo(w * 0.3f, wallH * 0.1f, w * 0.6f, wallH * 0.35f)
                    quadraticBezierTo(w * 0.8f, wallH * 0.45f, w, wallH * 0.25f)
                    lineTo(w, wallH * 0.4f)
                    quadraticBezierTo(w * 0.8f, wallH * 0.55f, w * 0.6f, wallH * 0.48f)
                    quadraticBezierTo(w * 0.3f, wallH * 0.25f, 0f, wallH * 0.45f)
                    close()
                }
                drawPath(path = auroraPath, color = auroraColor)
                drawCircle(color = Color.White.copy(alpha = 0.22f), radius = w * 0.06f, center = androidx.compose.ui.geometry.Offset(w * 0.85f, wallH * 0.2f))
            }
            "castle" -> {
                // 성 방
                val castleColor = Color.White.copy(alpha = 0.16f)
                drawRoundRect(color = castleColor, topLeft = androidx.compose.ui.geometry.Offset(w * 0.2f, wallH * 0.4f), size = androidx.compose.ui.geometry.Size(w * 0.2f, wallH * 0.45f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx(), 4.dp.toPx()))
                val roofPath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.2f, wallH * 0.4f)
                    lineTo(w * 0.3f, wallH * 0.2f)
                    lineTo(w * 0.4f, wallH * 0.4f)
                    close()
                }
                drawPath(path = roofPath, color = castleColor)
            }
            "hanok" -> {
                // 한옥 방
                val linePaint = Color.White.copy(alpha = 0.2f)
                drawRoundRect(color = linePaint, topLeft = androidx.compose.ui.geometry.Offset(w * 0.25f, wallH * 0.25f), size = androidx.compose.ui.geometry.Size(w * 0.5f, wallH * 0.5f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()))
                drawLine(color = linePaint, start = androidx.compose.ui.geometry.Offset(w * 0.5f, wallH * 0.25f), end = androidx.compose.ui.geometry.Offset(w * 0.5f, wallH * 0.75f), strokeWidth = 1.2f.dp.toPx())
                drawLine(color = linePaint, start = androidx.compose.ui.geometry.Offset(w * 0.25f, wallH * 0.5f), end = androidx.compose.ui.geometry.Offset(w * 0.75f, wallH * 0.5f), strokeWidth = 1.2f.dp.toPx())
            }
            "camp" -> {
                // 캠핑 방
                val campColor = Color.White.copy(alpha = 0.15f)
                val tentPath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(w * 0.5f, wallH * 0.35f)
                    lineTo(w * 0.3f, wallH * 0.8f)
                    lineTo(w * 0.7f, wallH * 0.8f)
                    close()
                }
                drawPath(path = tentPath, color = campColor)
                drawLine(color = Color.White.copy(alpha = 0.25f), start = androidx.compose.ui.geometry.Offset(w * 0.5f, wallH * 0.32f), end = androidx.compose.ui.geometry.Offset(w * 0.5f, wallH * 0.8f), strokeWidth = 1.dp.toPx())
            }
            "gallery" -> {
                // 갤러리 방
                val frameColor = Color.White.copy(alpha = 0.18f)
                drawRoundRect(color = frameColor, topLeft = androidx.compose.ui.geometry.Offset(w * 0.2f, wallH * 0.3f), size = androidx.compose.ui.geometry.Size(w * 0.22f, wallH * 0.3f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()))
                drawCircle(color = frameColor, radius = w * 0.08f, center = androidx.compose.ui.geometry.Offset(w * 0.7f, wallH * 0.42f), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx()))
            }
            "bamboo" -> {
                // 대나무 방
                val bambooColor = Color.White.copy(alpha = 0.15f)
                drawRoundRect(color = bambooColor, topLeft = androidx.compose.ui.geometry.Offset(w * 0.78f, wallH * 0.2f), size = androidx.compose.ui.geometry.Size(w * 0.06f, wallH * 0.65f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx(), 1.dp.toPx()))
                drawLine(color = Color.White.copy(alpha = 0.3f), start = androidx.compose.ui.geometry.Offset(w * 0.78f, wallH * 0.45f), end = androidx.compose.ui.geometry.Offset(w * 0.84f, wallH * 0.45f), strokeWidth = 1.dp.toPx())
                drawLine(color = Color.White.copy(alpha = 0.3f), start = androidx.compose.ui.geometry.Offset(w * 0.78f, wallH * 0.65f), end = androidx.compose.ui.geometry.Offset(w * 0.84f, wallH * 0.65f), strokeWidth = 1.dp.toPx())
            }
            "xmas" -> {
                // 크리스마스 방
                val stringColor = Color.White.copy(alpha = 0.18f)
                val bulbColor = Color.White.copy(alpha = 0.35f)
                drawLine(color = stringColor, start = androidx.compose.ui.geometry.Offset(0f, wallH * 0.15f), end = androidx.compose.ui.geometry.Offset(w, wallH * 0.25f), strokeWidth = 1.dp.toPx())
                drawCircle(color = bulbColor, radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(w * 0.2f, wallH * 0.17f))
                drawCircle(color = bulbColor, radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(w * 0.5f, wallH * 0.20f))
                drawCircle(color = bulbColor, radius = 3.dp.toPx(), center = androidx.compose.ui.geometry.Offset(w * 0.8f, wallH * 0.23f))
            }
            else -> {
                val gridColor = Color.White.copy(alpha = 0.1f)
                drawLine(color = gridColor, start = androidx.compose.ui.geometry.Offset(w * 0.25f, 0f), end = androidx.compose.ui.geometry.Offset(w * 0.25f, wallH), strokeWidth = 1f)
                drawLine(color = gridColor, start = androidx.compose.ui.geometry.Offset(w * 0.7f, 0f), end = androidx.compose.ui.geometry.Offset(w * 0.7f, wallH), strokeWidth = 1f)
                drawLine(color = gridColor, start = androidx.compose.ui.geometry.Offset(0f, wallH * 0.4f), end = androidx.compose.ui.geometry.Offset(w, wallH * 0.4f), strokeWidth = 1f)
            }
        }
    }
}

@Composable
fun EquippedAvatarView(
    charId: String,
    decorId: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier
) {
    // 11종 동물의 우향 정밀 대칭반사graphicsLayer 매핑
    val isFlippedChar = charId in setOf(
        "horse", "chick", "bee", "chicken", "deer", "duck", 
        "swan", "eagle", "squirrel", "sloth", "butterfly"
    )

    // 양(sheep)과 사슴(deer)을 제외하여 이중 다리 버그 박멸 및 멜빵바지 필요 순수 머리형 동물 18종 선별
    val isHeadOnlyChar = charId in setOf(
        "bear", "panda", "koala", "rabbit", "cat", "dog", "pig", 
        "frog", "monkey", "tiger", "lion", "fox", "horse", 
        "mouse", "hamster", "wolf", "cow", "chicken"
    )

    val decorItem = DecorRegistry.items.find { it.id == decorId && it.category == "decor" }
    val decorEmoji = decorItem?.emoji ?: ""
    val decorCat = decorItem?.subCategory ?: ""
    val baseCharEmoji = getCharacterEmoji(charId)

    Box(
        modifier = modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        // [Z-INDEX 1순위] 멜빵바지 바디 Box (머리보다 '먼저' 그려서 Z-Index상 뒤에 자연스럽게 깔리게 함!)
        if (isHeadOnlyChar) {
            val skinColor = when (charId) {
                "bear" -> Color(0xFF8D6E63)
                "panda" -> Color(0xFFE0E0E0)
                "koala" -> Color(0xFFB0BEC5)
                "rabbit" -> Color(0xFFFFF1F1)
                "cat" -> Color(0xFFFFCC80)
                "dog" -> Color(0xFFFFE082)
                "pig" -> Color(0xFFF8BBD0)
                "frog" -> Color(0xFF81C784)
                "monkey" -> Color(0xFFCAA472)
                "tiger" -> Color(0xFFFFB74D)
                "lion" -> Color(0xFFFFB300)
                "fox" -> Color(0xFFFF7043)
                "horse" -> Color(0xFFCAA472)
                "mouse" -> Color(0xFFCFD8DC)
                "hamster" -> Color(0xFFFFCC80)
                "wolf" -> Color(0xFF78909C)
                "cow" -> Color(0xFFFFF9C4)
                "sheep" -> Color(0xFFFAFAFA)
                "deer" -> Color(0xFFD7CCC8)
                "chicken" -> Color(0xFFFFFFFF)
                else -> Color(0xFFFFD54F)
            }

            val clothesColor = when (charId) {
                "bear" -> Color(0xFFFFD54F)
                "panda" -> Color(0xFF37474F)
                "koala" -> Color(0xFFEF9A9A)
                "rabbit" -> Color(0xFF80CBC4)
                "cat" -> Color(0xFFB39DDB)
                "dog" -> Color(0xFF81D4FA)
                "pig" -> Color(0xFFFFF59D)
                "frog" -> Color(0xFFFFB74D)
                "monkey" -> Color(0xFF80DEEA)
                "tiger" -> Color(0xFF4DB6AC)
                "lion" -> Color(0xFF90A4AE)
                "fox" -> Color(0xFFA5D6A7)
                "horse" -> Color(0xFFA5D6A7)
                "mouse" -> Color(0xFFF8BBD0)
                "hamster" -> Color(0xFFB39DDB)
                "wolf" -> Color(0xFFFFB74D)
                "cow" -> Color(0xFF37474F)
                "sheep" -> Color(0xFFF8BBD0)
                "deer" -> Color(0xFFFFB74D)
                "chicken" -> Color(0xFFFFD54F)
                else -> Color(0xFF81C784)
            }

            Box(
                modifier = Modifier
                    .offset(y = 11.dp)
                    .size(width = 18.dp, height = 14.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp, topStart = 4.dp, topEnd = 4.dp))
                        .background(skinColor)
                        .border(0.8.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp, topStart = 4.dp, topEnd = 4.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.62f)
                            .align(Alignment.BottomCenter)
                            .background(clothesColor)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Box(modifier = Modifier.size(width = 2.dp, height = 7.dp).background(clothesColor))
                        Box(modifier = Modifier.size(width = 2.dp, height = 7.dp).background(clothesColor))
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 0.5.dp)
                        .offset(y = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(modifier = Modifier.size(4.dp).background(skinColor, CircleShape).border(0.6.dp, Color.White.copy(alpha = 0.3f), CircleShape))
                    Box(modifier = Modifier.size(4.dp).background(skinColor, CircleShape).border(0.6.dp, Color.White.copy(alpha = 0.3f), CircleShape))
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 2.dp)
                        .width(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(modifier = Modifier.size(4.dp).background(skinColor, CircleShape).border(0.6.dp, Color.White.copy(alpha = 0.3f), CircleShape))
                    Box(modifier = Modifier.size(4.dp).background(skinColor, CircleShape).border(0.6.dp, Color.White.copy(alpha = 0.3f), CircleShape))
                }

                Text(
                    text = "🎀",
                    fontSize = 6.sp,
                    modifier = Modifier.offset(y = (-1.5).dp)
                )
            }
        }

        // [Z-INDEX 2순위] 동물 머리 + 왕관/장식 Box (멜빵바지보다 '나중에' 그려서 Z-Index상 위에 자연스럽게 얹히게 함!)
        // 유저 요청 피드백 반영: 말의 긴 목덜미와 목선 아랫부분이 멜빵바지 앞면에 얹혀 자연스럽고 위풍당당한 2등신 피규어 실물 핏 복원!
        val headOffsetX = when (charId) {
            "horse" -> 3.dp
            "deer" -> 2.5.dp
            "chick", "chicken" -> 1.5.dp
            "duck", "swan" -> 1.5.dp
            else -> 0.dp
        }
        val headOffsetY = when (charId) {
            "horse" -> 1.5.dp
            "deer" -> 1.dp
            else -> if (isHeadOnlyChar) (-3).dp else 0.dp
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset(x = headOffsetX, y = headOffsetY)
                .graphicsLayer {
                    if (isFlippedChar) {
                        scaleX = -1f
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = baseCharEmoji,
                fontSize = fontSize
            )

            if (decorEmoji.isNotEmpty()) {
                val (offsets, rotationZ) = when (decorCat) {
                    "hat" -> {
                        when (charId) {
                            "bee" -> Triple((-9).dp, (-18).dp, 0.42f) to -18f
                            "butterfly" -> Triple((-8).dp, (-18).dp, 0.42f) to -15f
                            "fly" -> Triple((-8).dp, (-17).dp, 0.42f) to -15f
                            "mosquito" -> Triple((-7).dp, (-18).dp, 0.42f) to -15f
                            "ant" -> Triple((-7).dp, (-19).dp, 0.42f) to -15f
                            "cricket" -> Triple((-6).dp, (-19).dp, 0.42f) to -10f
                            
                            "bear", "panda", "koala", "dog", "horse", "wolf", "deer" -> Triple(0.dp, (-28).dp, 0.44f) to 0f
                            "pig", "monkey", "mouse", "hamster", "cow", "sheep" -> Triple(0.dp, (-13).dp, 0.44f) to 0f
                            "cat" -> Triple((-3).dp, (-27).dp, 0.43f) to -8f
                            "tiger", "lion" -> Triple(0.dp, (-29).dp, 0.44f) to 0f
                            "fox" -> Triple((-2).dp, (-28).dp, 0.43f) to -4f
                            "rabbit" -> Triple(0.dp, (-34).dp, 0.42f) to 0f
                            
                            "chick" -> Triple((-4).dp, (-19).dp, 0.42f) to -8f
                            "chicken", "penguin", "duck" -> Triple(0.dp, (-21).dp, 0.42f) to 0f
                            "swan", "owl", "eagle" -> Triple(0.dp, (-22).dp, 0.43f) to 0f
                            
                            "frog" -> Triple(0.dp, (-22).dp, 0.42f) to 5f
                            else -> Triple(0.dp, (-24).dp, 0.44f) to 0f
                        }
                    }
                    "glasses" -> {
                        when (charId) {
                            "bee" -> Triple((-4).dp, (-10).dp, 0.42f) to -12f
                            "butterfly" -> Triple((-3).dp, (-9).dp, 0.42f) to -10f
                            "fly" -> Triple((-3).dp, (-8).dp, 0.42f) to -10f
                            "mosquito" -> Triple((-2).dp, (-9).dp, 0.42f) to -10f
                            "ant" -> Triple((-2).dp, (-10).dp, 0.42f) to -10f
                            
                            "bear", "panda", "koala", "dog", "horse", "wolf", "deer" -> Triple(0.dp, (-11).dp, 0.45f) to 0f
                            "cat" -> Triple(0.dp, (-10).dp, 0.43f) to 0f
                            "pig", "monkey", "mouse", "hamster", "cow", "sheep" -> Triple(0.dp, (-10).dp, 0.43f) to 0f
                            
                            "chick" -> Triple(1.dp, (-8).dp, 0.42f) to 0f
                            "chicken", "penguin", "duck" -> Triple(1.dp, (-7).dp, 0.42f) to 0f
                            "frog" -> Triple(0.dp, (-12).dp, 0.43f) to 0f
                            else -> Triple(0.dp, (-8).dp, 0.45f) to 0f
                        }
                    }
                    "tool" -> {
                        when (charId) {
                            "bee", "butterfly", "fly", "mosquito" -> Triple(12.dp, 8.dp, 0.42f) to 15f
                            "bear", "panda", "koala", "cat", "dog", "pig" -> Triple((-14).dp, 12.dp, 0.45f) to -15f
                            "chick", "chicken", "penguin" -> Triple((-11).dp, 8.dp, 0.42f) to -10f
                            else -> Triple((-12).dp, 9.dp, 0.45f) to -10f
                        }
                    }
                    "outfit" -> {
                        when (charId) {
                            "bee" -> Triple(6.dp, 8.dp, 0.48f) to 10f
                            "butterfly" -> Triple(6.dp, 6.dp, 0.48f) to 10f
                            "bear", "panda", "koala", "cat", "dog", "pig" -> Triple(0.dp, 14.dp, 0.52f) to 0f
                            "chick", "chicken", "penguin" -> Triple(0.dp, 6.dp, 0.48f) to 0f
                            else -> Triple(0.dp, 8.dp, 0.5f) to 0f
                        }
                    }
                    else -> Triple(0.dp, 0.dp, 1f) to 0f
                }

                val (offsetX, offsetY, decorScale) = offsets
                val scaledFontSize = (fontSize.value * decorScale).sp

                Text(
                    text = decorEmoji,
                    fontSize = scaledFontSize,
                    modifier = Modifier
                        .offset(x = offsetX, y = offsetY)
                        .graphicsLayer {
                            this.rotationZ = rotationZ
                        }
                )
            }
        }
    }
}

@Composable
fun FilterChipItem(
    label: String,
    filterId: String,
    currentFilter: String,
    onClick: (String) -> Unit
) {
    val isSelected = currentFilter == filterId
    Surface(
        modifier = Modifier
            .padding(end = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick(filterId) },
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
        border = BorderStroke(1.dp, if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun DecorShopDialog(
    currentPoints: Int,
    equippedChar: String,
    equippedItem: String,
    equippedRoom: String,
    equippedFurniture: String,
    unlockedItems: Set<String>,
    unlockedCharacters: Set<String>,
    unlockedRooms: Set<String>,
    unlockedFurnitures: Set<String>,
    isTestingAdmin: Boolean,
    onDismiss: () -> Unit,
    onEquipCharacter: (String) -> Unit,
    onEquipItem: (String) -> Unit,
    onEquipRoom: (String) -> Unit,
    onEquipFurniture: (String) -> Unit,
    onUnlockItem: (String, Int) -> Unit,
    onUnlockCharacter: (String, Int) -> Unit,
    onUnlockRoom: (String, Int) -> Unit,
    onUnlockFurniture: (String, Int) -> Unit
) {
    var activeTab by remember { mutableStateOf(0) } // 0: 펫 동물, 1: 펫 장식, 2: 룸 꾸미기
    var subFilter by remember { mutableStateOf("all") }

    val filteredItems by remember(activeTab, subFilter, unlockedItems, unlockedCharacters, unlockedRooms, unlockedFurnitures) {
        derivedStateOf {
            when (activeTab) {
                0 -> {
                    DecorRegistry.items.filter { it.category == "char" }
                }
                1 -> {
                    val base = DecorRegistry.items.filter { it.category == "decor" }
                    if (subFilter == "all") base else base.filter { it.subCategory == subFilter }
                }
                2 -> {
                    val base = DecorRegistry.items.filter { it.category in setOf("room", "furniture") }
                    if (subFilter == "all") {
                        base
                    } else if (subFilter == "room") {
                        base.filter { it.category == "room" }
                    } else {
                        base.filter { it.category == "furniture" }
                    }
                }
                else -> emptyList()
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .shadow(12.dp, RoundedCornerShape(28.dp)),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 16.dp, horizontal = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "펫 & 룸 꾸미기 상점 🍯",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "닫기")
                    }
                }

                Surface(
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .align(Alignment.End),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isTestingAdmin) "무제한 (Admin)" else "보유 꿀: ${currentPoints}P 🍯",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TabRow(
                    selectedTabIndex = activeTab,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                ) {
                    Tab(
                        selected = activeTab == 0,
                        onClick = {
                            activeTab = 0
                            subFilter = "all"
                        },
                        text = { Text("펫 동물 분양", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, maxLines = 1, letterSpacing = (-0.4).sp, softWrap = false) }
                    )
                    Tab(
                        selected = activeTab == 1,
                        onClick = {
                            activeTab = 1
                            subFilter = "all"
                        },
                        text = { Text("펫 아바타 장식", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, maxLines = 1, letterSpacing = (-0.4).sp, softWrap = false) }
                    )
                    Tab(
                        selected = activeTab == 2,
                        onClick = {
                            activeTab = 2
                            subFilter = "all"
                        },
                        text = { Text("룸 꾸미기", fontWeight = FontWeight.Bold, fontSize = 11.5.sp, maxLines = 1, letterSpacing = (-0.4).sp, softWrap = false) }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (activeTab == 1) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item { FilterChipItem("전체", "all", subFilter) { subFilter = it } }
                        item { FilterChipItem("모자", "hat", subFilter) { subFilter = it } }
                        item { FilterChipItem("안경", "glasses", subFilter) { subFilter = it } }
                        item { FilterChipItem("도구", "tool", subFilter) { subFilter = it } }
                        item { FilterChipItem("의상", "outfit", subFilter) { subFilter = it } }
                    }
                } else if (activeTab == 2) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        item { FilterChipItem("전체", "all", subFilter) { subFilter = it } }
                        item { FilterChipItem("방 배경", "room", subFilter) { subFilter = it } }
                        item { FilterChipItem("가구 데코", "furniture", subFilter) { subFilter = it } }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = filteredItems,
                        key = { it.id }
                    ) { item ->
                        val isUnlocked = when (item.category) {
                            "char" -> unlockedCharacters.contains(item.id)
                            "room" -> unlockedRooms.contains(item.id)
                            "furniture" -> unlockedFurnitures.contains(item.id)
                            else -> unlockedItems.contains(item.id) || item.id == "default"
                        }
                        
                        val isEquipped = when (item.category) {
                            "char" -> equippedChar == item.id
                            "room" -> equippedRoom == item.id
                            "furniture" -> equippedFurniture == item.id
                            else -> equippedItem == item.id
                        }

                        val canAfford = isTestingAdmin || currentPoints >= item.price

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.85f)
                                .clickable {
                                    if (isUnlocked) {
                                        when (item.category) {
                                            "char" -> onEquipCharacter(item.id)
                                            "room" -> onEquipRoom(item.id)
                                            "furniture" -> onEquipFurniture(item.id)
                                            else -> {
                                                if (equippedItem == item.id) {
                                                    onEquipItem("")
                                                } else {
                                                    onEquipItem(item.id)
                                                }
                                            }
                                        }
                                    } else {
                                        if (canAfford) {
                                            when (item.category) {
                                                "char" -> onUnlockCharacter(item.id, item.price)
                                                "room" -> onUnlockRoom(item.id, item.price)
                                                "furniture" -> onUnlockFurniture(item.id, item.price)
                                                else -> onUnlockItem(item.id, item.price)
                                            }
                                        } else {
                                            // 꿀 포인트 부족 피드백
                                        }
                                    }
                                }
                                .border(
                                    width = 1.5.dp,
                                    color = if (isEquipped) MaterialTheme.colorScheme.primary else Color.Transparent,
                                    shape = RoundedCornerShape(16.dp)
                                ),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isEquipped) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                }
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isEquipped) {
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                                } else {
                                                    Color.Black.copy(alpha = 0.02f)
                                                }
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = item.emoji, fontSize = 28.sp)
                                    }

                                    Text(
                                        text = item.name,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )

                                    if (isUnlocked) {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isEquipped) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.05f)
                                        ) {
                                            Text(
                                                text = if (isEquipped) "착용중" else "해금됨",
                                                color = if (isEquipped) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    } else {
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (canAfford) Color(0xFFFFECE6) else Color.Black.copy(alpha = 0.05f)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                                            ) {
                                                Text(
                                                    text = "${item.price}P",
                                                    color = if (canAfford) Color(0xFFFF6B35) else Color.Gray,
                                                    fontSize = 8.sp,
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
            }
        }
    }
}
