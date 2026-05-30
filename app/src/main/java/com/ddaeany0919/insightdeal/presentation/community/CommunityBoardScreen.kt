package com.ddaeany0919.insightdeal.presentation.community

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.ThumbsUpDown
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.ddaeany0919.insightdeal.data.network.CommunityPostCreateReq
import com.ddaeany0919.insightdeal.data.network.CommunityPostDto
import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.network.NetworkModule
import com.ddaeany0919.insightdeal.presentation.auth.AuthManager
import com.ddaeany0919.insightdeal.presentation.bounceClick
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class CommunityBoardUiState {
    object Loading : CommunityBoardUiState()
    data class Success(val posts: List<CommunityPostDto>) : CommunityBoardUiState()
    data class Error(val message: String) : CommunityBoardUiState()
}

class CommunityBoardViewModel : ViewModel() {
    private val api = NetworkModule.createService<com.ddaeany0919.insightdeal.network.ApiService>()
    
    private val _uiState = MutableStateFlow<CommunityBoardUiState>(CommunityBoardUiState.Loading)
    val uiState: StateFlow<CommunityBoardUiState> = _uiState.asStateFlow()

    fun loadPosts(showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) {
                _uiState.value = CommunityBoardUiState.Loading
            }
            try {
                val response = api.getCommunityPosts(postType = "REQUEST")
                if (response.isSuccessful && response.body() != null) {
                    _uiState.value = CommunityBoardUiState.Success(response.body()!!)
                } else {
                    _uiState.value = CommunityBoardUiState.Error("데이터를 불러오지 못했습니다.")
                }
            } catch (e: Exception) {
                _uiState.value = CommunityBoardUiState.Error(e.message ?: "알 수 없는 오류")
            }
        }
    }

    fun votePost(context: Context, post: CommunityPostDto, isBuy: Boolean, oldVote: String?, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                var newBuy = post.bountyPoints
                var newSkip = post.likeCount
                
                if (oldVote == "BUY") newBuy = (newBuy - 1).coerceAtLeast(0)
                if (oldVote == "SKIP") newSkip = (newSkip - 1).coerceAtLeast(0)
                
                if (isBuy) newBuy += 1 else newSkip += 1

                val req = CommunityPostCreateReq(
                    postType = post.postType,
                    userId = post.userId,
                    nickname = post.nickname,
                    title = post.title,
                    content = post.content,
                    targetPrice = post.targetPrice,
                    bountyPoints = newBuy,
                    likeCount = newSkip,
                    location = post.location
                )
                val response = api.updateCommunityPost(post.id, req)
                if (response.isSuccessful) {
                    saveUserVote(context, post.id, if (isBuy) "BUY" else "SKIP")
                    onComplete()
                }
            } catch (e: Exception) {
            }
        }
    }

    fun cancelVotePost(context: Context, post: CommunityPostDto, currentVote: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                val newBuy = if (currentVote == "BUY") (post.bountyPoints - 1).coerceAtLeast(0) else post.bountyPoints
                val newSkip = if (currentVote == "SKIP") (post.likeCount - 1).coerceAtLeast(0) else post.likeCount

                val req = CommunityPostCreateReq(
                    postType = post.postType,
                    userId = post.userId,
                    nickname = post.nickname,
                    title = post.title,
                    content = post.content,
                    targetPrice = post.targetPrice,
                    bountyPoints = newBuy,
                    likeCount = newSkip,
                    location = post.location
                )
                val response = api.updateCommunityPost(post.id, req)
                if (response.isSuccessful) {
                    val prefs = context.getSharedPreferences("should_i_buy_votes", Context.MODE_PRIVATE)
                    prefs.edit().remove("vote_${post.id}").apply()
                    onComplete()
                }
            } catch (e: Exception) {
            }
        }
    }
}

private fun getUserVote(context: Context, postId: Int): String? {
    val prefs = context.getSharedPreferences("should_i_buy_votes", Context.MODE_PRIVATE)
    return prefs.getString("vote_$postId", null)
}

private fun saveUserVote(context: Context, postId: Int, vote: String) {
    val prefs = context.getSharedPreferences("should_i_buy_votes", Context.MODE_PRIVATE)
    prefs.edit().putString("vote_$postId", vote).apply()
}

private val AccentOrange = Color(0xFFFF6B35)
private val AccentOrangeSoft = Color(0xFFFFF0EB)
private val NeutralGray50 = Color(0xFFF8F9FA)
private val NeutralGray200 = Color(0xFFE9ECEF)
private val NeutralGray500 = Color(0xFF868E96)
private val NeutralGray700 = Color(0xFF495057)
private val NeutralGray900 = Color(0xFF212529)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityBoardScreen(
    navController: NavController? = null,
    viewModel: CommunityBoardViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val currentUserId by AuthManager.getUsername(context).collectAsState(initial = "admin")
    val isLoggedIn = currentUserId != "guest" && !currentUserId.isNullOrEmpty()
    var showLoginIncentiveDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.loadPosts()
    }
    
    LaunchedEffect(navController?.currentBackStackEntry) {
        viewModel.loadPosts(showLoading = false)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.Forum,
                            contentDescription = null,
                            tint = AccentOrange,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "살까말까 고민방",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeutralGray900
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadPosts() }) {
                        Icon(
                            Icons.Default.Refresh, 
                            contentDescription = "새로고침",
                            tint = NeutralGray500
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (isLoggedIn) {
                        navController?.navigate("community_write")
                    } else {
                        showLoginIncentiveDialog = true
                    }
                },
                containerColor = AccentOrange,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "투표 올리기", modifier = Modifier.size(20.dp))
                    Text("고민 등록", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        },
        containerColor = NeutralGray50
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when (val state = uiState) {
                is CommunityBoardUiState.Loading -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = AccentOrange,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(40.dp)
                        )
                        Text(
                            "고민글을 불러오는 중...", 
                            style = MaterialTheme.typography.bodyMedium,
                            color = NeutralGray500
                        )
                    }
                }
                is CommunityBoardUiState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("😅", fontSize = 48.sp)
                        Text(
                            text = state.message, 
                            color = NeutralGray500,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Button(
                            onClick = { viewModel.loadPosts() },
                            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("다시 시도", fontWeight = FontWeight.Bold)
                        }
                    }
                }
                is CommunityBoardUiState.Success -> {
                    if (state.posts.isEmpty()) {
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = AccentOrangeSoft,
                                modifier = Modifier.size(80.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.ThumbsUpDown,
                                        contentDescription = null,
                                        tint = AccentOrange,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "등록된 고민글이 없어요",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = NeutralGray900
                            )
                            Text(
                                "살지 말지 망설여지는 특가 정보를 가장 먼저 물어보세요!",
                                style = MaterialTheme.typography.bodyMedium,
                                color = NeutralGray500
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedButton(
                                onClick = { navController?.navigate("community_write") },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentOrange),
                                border = BorderStroke(1.5.dp, AccentOrange)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = AccentOrange
                                    )
                                    Text("첫 고민 등록하기", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.posts, key = { it.id }) { post ->
                                ShouldIBuyVoteCard(
                                    post = post, 
                                    currentUserId = currentUserId ?: "admin",
                                    onEditClick = { navController?.navigate("community_write?postId=${post.id}") },
                                    onVoteSubmit = { viewModel.loadPosts(showLoading = false) },
                                    navController = navController,
                                    onClick = { navController?.navigate("community_detail/${post.id}") },
                                    viewModel = viewModel
                                )
                            }
                            item { Spacer(modifier = Modifier.height(80.dp)) }
                        }
                    }
                }
            }
        }
    }

    // 소셜 로그인 강력 권장 및 락인 설득 다이얼로그
    if (showLoginIncentiveDialog) {
        AlertDialog(
            onDismissRequest = { showLoginIncentiveDialog = false },
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text("💡 회원 전용 혜택 안내", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AccentOrange)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("커뮤니티 글쓰기는 로그인 후 이용 가능합니다.", fontSize = 13.sp, color = NeutralGray700)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Text("⚡ 소셜 로그인 시 이런 꿀 혜택을 드려요:", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = NeutralGray900)
                    Text("• 🍯 꿀딜 고민 등록 시 즉시 +10 내공 포인트 획득", fontSize = 13.sp, color = NeutralGray700)
                    Text("• 🔒 Lv.2 이상 도달 시 시크릿 특가 딜 & 할인 쿠폰 해제", fontSize = 13.sp, color = NeutralGray700)
                    Text("• 🤖 나만을 위한 1:1 지능형 AI 추천 핫딜 피드 활성화", fontSize = 13.sp, color = NeutralGray700)
                    Text("• ⏱️ 기기 변경 시에도 안전한 클라우드 100% 자동 백업", fontSize = 13.sp, color = NeutralGray700)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLoginIncentiveDialog = false
                        navController?.navigate("settings")
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
                ) {
                    Text("1초 간편 로그인하기 ⚡", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLoginIncentiveDialog = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("다음에 할게요", color = NeutralGray500)
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
fun ShouldIBuyVoteCard(
    post: CommunityPostDto, 
    currentUserId: String,
    onEditClick: () -> Unit,
    onVoteSubmit: () -> Unit,
    navController: NavController? = null,
    onClick: (() -> Unit)? = null,
    viewModel: CommunityBoardViewModel
) {
    val context = LocalContext.current
    val api = remember { NetworkModule.createService<com.ddaeany0919.insightdeal.network.ApiService>() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    
    var userVote by remember { mutableStateOf(getUserVote(context, post.id)) }
    
    LaunchedEffect(post) {
        userVote = getUserVote(context, post.id)
    }
    
    val totalVotes = post.bountyPoints + post.likeCount
    val buyRatio = if (totalVotes > 0) post.bountyPoints.toFloat() / totalVotes else 0.5f
    val skipRatio = if (totalVotes > 0) post.likeCount.toFloat() / totalVotes else 0.5f
    
    val animatedProgress by animateFloatAsState(
        targetValue = buyRatio,
        animationSpec = tween(600),
        label = "progressAnimation"
    )

    var linkedDeal by remember { mutableStateOf<DealItem?>(null) }

    LaunchedEffect(post.location) {
        post.location?.let { loc ->
            val dealId = if (loc.startsWith("deal_id:")) {
                loc.removePrefix("deal_id:").toIntOrNull()
            } else {
                loc.toIntOrNull()
            }
            if (dealId != null) {
                try {
                    val response = api.getEnhancedDealInfo(dealId)
                    if (response.isSuccessful && response.body() != null) {
                        linkedDeal = response.body()
                    }
                } catch (e: Exception) {
                }
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick {
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick?.invoke()
            }
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        AccentOrange.copy(alpha = 0.35f),
                        Color(0xFF3A7BD5).copy(alpha = 0.15f)
                    )
                ),
                shape = RoundedCornerShape(20.dp)
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 상단: 제목(좌측) & 라벨/수정버튼(우측)
            Row(
                horizontalArrangement = Arrangement.SpaceBetween, 
                verticalAlignment = Alignment.CenterVertically, 
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = post.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = NeutralGray900,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )
                
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = AccentOrangeSoft,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = "⚖️ 살까말까 투표",
                            color = AccentOrange,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    if (post.userId == currentUserId) {
                        Surface(
                            onClick = onEditClick,
                            color = NeutralGray50,
                            shape = CircleShape,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Edit, 
                                    contentDescription = "수정", 
                                    tint = NeutralGray500,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 컴팩트 연동 상품
            linkedDeal?.let { deal ->
                Spacer(modifier = Modifier.height(12.dp))
                Surface(
                    color = NeutralGray50,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(0.5.dp, NeutralGray200),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            navController?.navigate("deal_detail/${deal.id}")
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!deal.imageUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = deal.imageUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(NeutralGray50)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.ShoppingCart,
                                contentDescription = null,
                                tint = AccentOrange,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Surface(
                            color = AccentOrangeSoft,
                            shape = RoundedCornerShape(3.dp)
                        ) {
                            Text(
                                text = deal.siteName,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentOrange,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        Text(
                            text = deal.title,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeutralGray700,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${"%,d".format(deal.price)}원",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = NeutralGray900
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))

            // 작성자 / 시간 및 조회수/댓글수 (본문 제거 후 컴팩트 하단 정보)
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${post.nickname ?: "admin"} • ${formatRelativeTime(post.createdAt)}",
                    fontSize = 11.sp,
                    color = NeutralGray500
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Visibility, 
                            contentDescription = "조회수", 
                            modifier = Modifier.size(13.dp),
                            tint = NeutralGray500
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "${post.viewCount}", 
                            fontSize = 11.sp, 
                            color = NeutralGray500
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.ChatBubbleOutline, 
                            contentDescription = "댓글", 
                            modifier = Modifier.size(13.dp),
                            tint = if (post.commentCount > 0) AccentOrange else NeutralGray500
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Text(
                            text = "${post.commentCount}", 
                            fontSize = 11.sp, 
                            color = if (post.commentCount > 0) AccentOrange else NeutralGray500,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(10.dp))
            
            
            Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFE3F2FD)) // 연한 파란색으로 '말까' 게이지 표시
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(animatedProgress)
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFFFF9E80), AccentOrange)
                                )
                            )
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "살까 👍 ${if (totalVotes == 0) "0" else "%.0f".format(buyRatio * 100)}%",
                            fontWeight = FontWeight.ExtraBold,
                            color = if (buyRatio > 0.3f) Color.White else AccentOrange,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "말까 👎 ${if (totalVotes == 0) "0" else "%.0f".format(skipRatio * 100)}%",
                            fontWeight = FontWeight.ExtraBold,
                            color = if (skipRatio > 0.3f) NeutralGray700 else Color.White,
                            fontSize = 11.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch {
                            if (userVote == "BUY") {
                                viewModel.cancelVotePost(context, post, "BUY") {
                                    userVote = null
                                    onVoteSubmit()
                                }
                            } else {
                                viewModel.votePost(context, post, isBuy = true, oldVote = userVote) {
                                    userVote = "BUY"
                                    onVoteSubmit()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (userVote == "BUY") AccentOrange else if (userVote == "SKIP") NeutralGray50 else AccentOrangeSoft),
                    border = BorderStroke(1.dp, if (userVote == "BUY") AccentOrange else if (userVote == "SKIP") NeutralGray200 else AccentOrange),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .bounceClick { }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.ThumbUp, contentDescription = null, tint = if (userVote == "BUY") Color.White else if (userVote == "SKIP") NeutralGray500 else AccentOrange, modifier = Modifier.size(16.dp))
                        Text("살까 👍", color = if (userVote == "BUY") Color.White else if (userVote == "SKIP") NeutralGray500 else AccentOrange, fontWeight = FontWeight.Bold)
                    }
                }
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch {
                            if (userVote == "SKIP") {
                                viewModel.cancelVotePost(context, post, "SKIP") {
                                    userVote = null
                                    onVoteSubmit()
                                }
                            } else {
                                viewModel.votePost(context, post, isBuy = false, oldVote = userVote) {
                                    userVote = "SKIP"
                                    onVoteSubmit()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (userVote == "SKIP") NeutralGray700 else NeutralGray50),
                    border = BorderStroke(1.dp, if (userVote == "SKIP") NeutralGray700 else NeutralGray200),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .bounceClick { }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Default.ThumbDown, contentDescription = null, tint = if (userVote == "SKIP") Color.White else NeutralGray500, modifier = Modifier.size(16.dp))
                        Text("말까 👎", color = if (userVote == "SKIP") Color.White else NeutralGray700, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

private fun formatRelativeTime(dateTimeStr: String?): String {
    if (dateTimeStr.isNullOrBlank()) return ""
    return try {
        val cleanStr = dateTimeStr.substringBefore(".").substringBefore("+")
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
        sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
        val date = sdf.parse(cleanStr) ?: return dateTimeStr
        
        val diff = System.currentTimeMillis() - date.time
        val seconds = diff / 1000
        val minutes = seconds / 60
        val hours = minutes / 60
        val days = hours / 24
        
        when {
            seconds < 60 -> "방금 전"
            minutes < 60 -> "${minutes}분 전"
            hours < 24 -> "${hours}시간 전"
            days < 7 -> "${days}일 전"
            else -> dateTimeStr.substringBefore("T")
        }
    } catch (e: Exception) {
        dateTimeStr.substringBefore("T")
    }
}
