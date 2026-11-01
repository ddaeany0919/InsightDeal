package com.ddaeany0919.insightdeal.presentation.mypage

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.outlined.ExitToApp
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ddaeany0919.insightdeal.data.network.NetworkConfig
import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.network.ApiService
import com.ddaeany0919.insightdeal.network.NetworkModule
import com.ddaeany0919.insightdeal.presentation.KeywordManagerViewModel
import com.ddaeany0919.insightdeal.presentation.auth.AuthManager
import com.ddaeany0919.insightdeal.presentation.community.CommunityBoardUiState
import com.ddaeany0919.insightdeal.presentation.community.CommunityBoardViewModel
import com.ddaeany0919.insightdeal.presentation.formatPrice
import com.ddaeany0919.insightdeal.presentation.home.BrandPlaceholder
import com.ddaeany0919.insightdeal.presentation.home.KeywordSettingsBottomSheet
import com.ddaeany0919.insightdeal.presentation.home.NotificationHistoryBottomSheet
import com.ddaeany0919.insightdeal.presentation.mypage.history.MyCommentsUiState
import com.ddaeany0919.insightdeal.presentation.mypage.history.MyCommentsViewModel
import com.ddaeany0919.insightdeal.presentation.mypage.history.NotificationHistoryManager
import com.ddaeany0919.insightdeal.presentation.mypage.history.RecentDealManager
import kotlinx.coroutines.launch
import java.net.URLEncoder

private fun buildProxiedThumbUrl(rawUrl: String?): String? {
    if (rawUrl.isNullOrBlank()) return null
    if (!rawUrl.startsWith("http")) return rawUrl
    return try {
        val base = NetworkConfig.getActiveServerUrl().removeSuffix("/")
        "$base/api/proxy-image?url=${URLEncoder.encode(rawUrl, "UTF-8")}&v=5"
    } catch (_: Exception) {
        rawUrl
    }
}

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
    val scope = rememberCoroutineScope()
    val username by AuthManager.getUsername(context).collectAsState(initial = "guest")
    val isLoggedIn = username != "guest" && !username.isNullOrEmpty()

    LaunchedEffect(Unit) {
        NotificationHistoryManager.init(context)
    }

    val recentDeals by RecentDealManager.recentDeals.collectAsState()

    val boardViewModel: CommunityBoardViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val boardUiState by boardViewModel.uiState.collectAsState()
    LaunchedEffect(Unit) {
        boardViewModel.loadPosts()
    }
    val myPostsCount = if (boardUiState is CommunityBoardUiState.Success) {
        (boardUiState as CommunityBoardUiState.Success).posts.count { it.userId == username }
    } else 0

    val myCommentsViewModel: MyCommentsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val myCommentsUiState by myCommentsViewModel.uiState.collectAsState()
    LaunchedEffect(username) {
        myCommentsViewModel.loadComments(username ?: "guest")
    }
    val myCommentsCount = if (myCommentsUiState is MyCommentsUiState.Success) {
        (myCommentsUiState as MyCommentsUiState.Success).comments.size
    } else 0

    val keywordViewModel: KeywordManagerViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val keywords by keywordViewModel.keywords.collectAsState()
    val activeKeywordCount = remember(keywords) { keywords.count { it.isActive } }

    var showNotificationHistory by remember { mutableStateOf(false) }
    var showKeywordDialog by remember { mutableStateOf(false) }

    val api = remember { NetworkModule.createService<ApiService>() }
    var recommendedDeals by remember { mutableStateOf<List<DealItem>>(emptyList()) }

    LaunchedEffect(recentDeals) {
        try {
            val response = api.getCommunityHotDeals(limit = 40)
            if (response.isSuccessful) {
                val allDeals = response.body()?.deals.orEmpty().filter { !it.isClosed && it.price > 0 }
                val preferredCategories = recentDeals.mapNotNull { it.category }.distinct().take(3)
                val recentIds = recentDeals.map { it.id }.toSet()

                val matched = if (preferredCategories.isNotEmpty()) {
                    allDeals.filter { it.id !in recentIds && it.category in preferredCategories }
                } else {
                    emptyList()
                }
                recommendedDeals = (matched + allDeals.filter { it.id !in recentIds })
                    .distinctBy { it.id }
                    .take(3)
            }
        } catch (_: Exception) {
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "내 정보",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 20.sp,
                        letterSpacing = (-0.5).sp
                    )
                },
                actions = {
                    IconButton(onClick = { showNotificationHistory = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = "알림 내역",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Outlined.Settings,
                            contentDescription = "설정",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
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
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 36.dp)
        ) {
            // 1. 프로필 & 핵심 요약 카드
            item(key = "profile_summary_card") {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = if (isLoggedIn) "$username 님" else "비회원 이용 중",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        letterSpacing = (-0.3).sp
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = if (isLoggedIn) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    ) {
                                        Text(
                                            text = if (isLoggedIn) "계정 연동됨" else "기기 저장",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isLoggedIn) {
                                                MaterialTheme.colorScheme.primary
                                            } else {
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            },
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = if (isLoggedIn) {
                                        "관심 상품과 키워드 알림이 계정에 동기화됩니다."
                                    } else {
                                        "로그인하면 기기 변경 시에도 알림과 관심 목록이 유지됩니다."
                                    },
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 16.sp,
                                    letterSpacing = (-0.1).sp
                                )
                            }
                            if (!isLoggedIn) {
                                Spacer(modifier = Modifier.width(8.dp))
                                FilledTonalButton(
                                    onClick = onNavigateToAuth,
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    colors = ButtonDefaults.filledTonalButtonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                ) {
                                    Text("로그인", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SummaryStatItem(
                                modifier = Modifier.weight(1f),
                                label = "알림 키워드",
                                value = "${activeKeywordCount}개",
                                onClick = { navController?.navigate("keyword_alarm") }
                            )
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(28.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                            )
                            SummaryStatItem(
                                modifier = Modifier.weight(1f),
                                label = "최근 본 딜",
                                value = "${recentDeals.size}건",
                                onClick = onNavigateToRecentDeals
                            )
                            Box(
                                modifier = Modifier
                                    .width(1.dp)
                                    .height(28.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
                            )
                            SummaryStatItem(
                                modifier = Modifier.weight(1f),
                                label = "작성 글·댓글",
                                value = "${myPostsCount + myCommentsCount}건",
                                onClick = onNavigateToMyPosts
                            )
                        }
                    }
                }
            }

            // 2. 최근 본 핫딜 섹션
            item(key = "recent_deals_section") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "최근 본 핫딜",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            letterSpacing = (-0.3).sp
                        )
                        if (recentDeals.isNotEmpty()) {
                            Text(
                                text = "전체보기",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.clickable { onNavigateToRecentDeals() }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))

                    if (recentDeals.isEmpty()) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 22.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "최근 확인한 핫딜이 없습니다.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(
                                items = recentDeals.take(10),
                                key = { it.id },
                                contentType = { "recent_deal_card" }
                            ) { deal ->
                                val proxiedUrl = remember(deal.id, deal.imageUrl) {
                                    buildProxiedThumbUrl(deal.imageUrl)
                                }
                                Card(
                                    modifier = Modifier
                                        .width(136.dp)
                                        .clickable { navController?.navigate("deal_detail/${deal.id}") },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(86.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color.White)
                                        ) {
                                            if (!proxiedUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(context)
                                                        .data(proxiedUrl)
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = deal.title,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                BrandPlaceholder(
                                                    siteName = deal.siteName,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = deal.title.replace(Regex("^[\\uD83C-\\uDBFF\\uDC00-\\uDFFF\\u2600-\\u27BF\\uFE0F]+\\s*"), ""),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            letterSpacing = (-0.2).sp
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = formatPrice(deal.price, deal.currency, deal.title),
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFFFF5A26)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 3. 관심 카테고리 추천 딜 섹션 (실제 데이터 기반)
            if (recommendedDeals.isNotEmpty()) {
                item(key = "recommended_deals_section") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "주목할 만한 인기 핫딜",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            letterSpacing = (-0.3).sp,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            recommendedDeals.forEach { deal ->
                                val proxiedUrl = remember(deal.id, deal.imageUrl) {
                                    buildProxiedThumbUrl(deal.imageUrl)
                                }
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { navController?.navigate("deal_detail/${deal.id}") },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(56.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(Color.White)
                                        ) {
                                            if (!proxiedUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = ImageRequest.Builder(context)
                                                        .data(proxiedUrl)
                                                        .crossfade(true)
                                                        .build(),
                                                    contentDescription = deal.title,
                                                    modifier = Modifier.fillMaxSize(),
                                                    contentScale = ContentScale.Crop
                                                )
                                            } else {
                                                BrandPlaceholder(
                                                    siteName = deal.siteName,
                                                    modifier = Modifier.fillMaxSize()
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.surfaceVariant
                                                ) {
                                                    Text(
                                                        text = deal.siteName.substringBefore(",").trim(),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                                    )
                                                }
                                                val rawShip = deal.shippingFee?.trim().orEmpty()
                                                val shipLabel = when {
                                                    rawShip.isEmpty() || rawShip == "정보 없음" || rawShip == "배송비 확인 필요" -> "배송비: 확인 필요"
                                                    rawShip in listOf("0", "0원", "무배", "무료", "무료배송") -> "배송비: 무료"
                                                    rawShip.startsWith("배송비") -> rawShip
                                                    else -> "배송비: ${rawShip.replace("무료배송", "무료")}"
                                                }
                                                Text(
                                                    text = shipLabel,
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = deal.title.replace(Regex("^[\\uD83C-\\uDBFF\\uDC00-\\uDFFF\\u2600-\\u27BF\\uFE0F]+\\s*"), ""),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                letterSpacing = (-0.2).sp
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = formatPrice(deal.price, deal.currency, deal.title),
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = Color(0xFFFF5A26)
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                            contentDescription = null,
                                            modifier = Modifier.size(13.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. 나의 활동 및 관리 메뉴
            item(key = "my_activity_menu") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "활동 및 관리",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = (-0.3).sp,
                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 6.dp)
                    )

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                            MyPageMenuItem(
                                icon = Icons.Outlined.FavoriteBorder,
                                title = "관심 상품 목록 (찜)",
                                onClick = onNavigateToWatchlist
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                            MyPageMenuItem(
                                icon = Icons.Outlined.NotificationsActive,
                                title = "키워드 알림 관리",
                                value = if (activeKeywordCount > 0) "${activeKeywordCount}개 활성" else "설정",
                                onClick = { navController?.navigate("keyword_alarm") }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                            MyPageMenuItem(
                                icon = Icons.Outlined.History,
                                title = "최근 본 핫딜 내역",
                                value = "${recentDeals.size}건",
                                onClick = onNavigateToRecentDeals
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                            MyPageMenuItem(
                                icon = Icons.Outlined.Article,
                                title = "내가 작성한 게시글",
                                value = "${myPostsCount}건",
                                onClick = onNavigateToMyPosts
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                            MyPageMenuItem(
                                icon = Icons.Outlined.ChatBubbleOutline,
                                title = "내가 작성한 댓글",
                                value = "${myCommentsCount}건",
                                onClick = onNavigateToMyComments
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                            MyPageMenuItem(
                                icon = Icons.Outlined.Settings,
                                title = "앱 환경설정",
                                onClick = onNavigateToSettings
                            )

                            if (isLoggedIn) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                MyPageMenuItem(
                                    icon = Icons.AutoMirrored.Outlined.ExitToApp,
                                    title = "로그아웃",
                                    textColor = MaterialTheme.colorScheme.error,
                                    iconColor = MaterialTheme.colorScheme.error,
                                    onClick = {
                                        scope.launch {
                                            AuthManager.logout(context)
                                            android.widget.Toast.makeText(
                                                context,
                                                "로그아웃되었습니다.",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNotificationHistory) {
        NotificationHistoryBottomSheet(
            onDismiss = { showNotificationHistory = false },
            onNavigateToKeywordSettings = {
                showNotificationHistory = false
                showKeywordDialog = true
            },
            onOpenUrlInWebView = { url ->
                if (navController != null) {
                    val encodedUrl = URLEncoder.encode(url, "UTF-8")
                    navController.navigate("webview/$encodedUrl")
                }
            }
        )
    }

    if (showKeywordDialog) {
        KeywordSettingsBottomSheet(
            onDismiss = { showKeywordDialog = false },
            keywordViewModel = keywordViewModel
        )
    }
}

@Composable
private fun SummaryStatItem(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun MyPageMenuItem(
    icon: ImageVector,
    title: String,
    value: String? = null,
    iconColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
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
            modifier = Modifier.size(20.dp),
            tint = iconColor
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            modifier = Modifier.weight(1f),
            letterSpacing = (-0.2).sp
        )
        if (value != null) {
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        )
    }
}
