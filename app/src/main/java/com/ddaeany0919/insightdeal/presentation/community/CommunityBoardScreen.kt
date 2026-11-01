package com.ddaeany0919.insightdeal.presentation.community

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.graphics.graphicsLayer
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
                    Text(
                        text = "커뮤니티",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.4).sp,
                        color = MaterialTheme.colorScheme.onBackground
                    )
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
                containerColor = NeutralGray900,
                contentColor = Color.White,
                shape = RoundedCornerShape(14.dp),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "글쓰기", modifier = Modifier.size(18.dp))
                    Text("글쓰기", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
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
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.5.dp,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            "게시글을 불러오는 중...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NeutralGray500
                        )
                    }
                }
                is CommunityBoardUiState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = state.message,
                            color = NeutralGray500,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        OutlinedButton(
                            onClick = { viewModel.loadPosts() },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("다시 시도", fontWeight = FontWeight.SemiBold)
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
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(64.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Outlined.ThumbsUpDown,
                                        contentDescription = null,
                                        tint = NeutralGray700,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "아직 등록된 게시글이 없습니다",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "구매가 망설여지는 핫딜이 있다면 의견을 물어보세요.",
                                style = MaterialTheme.typography.bodySmall,
                                color = NeutralGray500
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = { navController?.navigate("community_write") },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Text("첫 게시글 작성하기", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(items = state.posts, key = { it.id }, contentType = { "community_post_item" }) { post ->
                                ShouldIBuyVoteCard(
                                    post = post,
                                    currentUserId = currentUserId ?: "admin",
                                    onEditClick = { navController?.navigate("community_edit/${post.id}") },
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

    if (showLoginIncentiveDialog) {
        AlertDialog(
            onDismissRequest = { showLoginIncentiveDialog = false },
            title = {
                Text("로그인이 필요합니다", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
            },
            text = {
                Text("커뮤니티 게시글 작성 및 투표 참여는 로그인 후 이용할 수 있습니다.", fontSize = 14.sp, color = NeutralGray700)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLoginIncentiveDialog = false
                        navController?.navigate("settings")
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("로그인하기", fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLoginIncentiveDialog = false }
                ) {
                    Text("닫기", color = NeutralGray500)
                }
            },
            shape = RoundedCornerShape(18.dp)
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
        animationSpec = spring(
            dampingRatio = 0.55f, // 찰랑거리는 바운스 강도 극대화
            stiffness = 120f      // 쫀득한 속도감
        ),
        label = "progressAnimation"
    )

    // 엄지척 이모지 쫀득한 spring 튕김 애니메이션 상태 추가
    val buyIconScale by animateFloatAsState(
        targetValue = if (userVote == "BUY") 1.25f else 1.0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 250f),
        label = "buyIconScale"
    )
    val buyIconRotation by animateFloatAsState(
        targetValue = if (userVote == "BUY") -15f else 0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 200f),
        label = "buyIconRotation"
    )
    val skipIconScale by animateFloatAsState(
        targetValue = if (userVote == "SKIP") 1.25f else 1.0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 250f),
        label = "skipIconScale"
    )
    val skipIconRotation by animateFloatAsState(
        targetValue = if (userVote == "SKIP") 15f else 0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 200f),
        label = "skipIconRotation"
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
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = post.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                )

                if (post.userId == currentUserId) {
                    Surface(
                        onClick = onEditClick,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = CircleShape,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "수정",
                                tint = NeutralGray500,
                                modifier = Modifier.size(13.dp)
                            )
                        }
                    }
                }
            }

            if (post.content.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = post.content,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 연동 상품
            linkedDeal?.let { deal ->
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            navController?.navigate("deal_detail/${deal.id}")
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!deal.imageUrl.isNullOrBlank()) {
                            val proxyThumbUrl = remember(deal.imageUrl) {
                                val raw = deal.imageUrl ?: ""
                                val baseUrl = com.ddaeany0919.insightdeal.data.network.NetworkConfig.getActiveServerUrl().trimEnd('/')
                                if (raw.startsWith("http")) "$baseUrl/api/proxy-image?url=${java.net.URLEncoder.encode(raw, "UTF-8")}&v=5" else raw
                            }
                            AsyncImage(
                                model = proxyThumbUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(NeutralGray50)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.ShoppingCart,
                                contentDescription = null,
                                tint = NeutralGray500,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Surface(
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = deal.siteName,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = NeutralGray700,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = deal.title,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = com.ddaeany0919.insightdeal.presentation.formatPrice(deal.price.toLong(), null, deal.title),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFF5A26)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // 투표 현황 바 (참여자가 있을 때만 비율 표시, 0명일 때는 중립 트랙)
            if (totalVotes > 0) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "살까 ${"%.0f".format(buyRatio * 100)}% (${post.bountyPoints})",
                            fontWeight = FontWeight.SemiBold,
                            color = if (buyRatio >= skipRatio) AccentOrange else NeutralGray500,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "말까 ${"%.0f".format(skipRatio * 100)}% (${post.likeCount})",
                            fontWeight = FontWeight.SemiBold,
                            color = if (skipRatio > buyRatio) NeutralGray900 else NeutralGray500,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(NeutralGray200)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animatedProgress)
                                .background(AccentOrange)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val isBuySelected = userVote == "BUY"
                val isSkipSelected = userVote == "SKIP"

                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch {
                            if (isBuySelected) {
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
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isBuySelected) AccentOrangeSoft else Color.Transparent,
                        contentColor = if (isBuySelected) AccentOrange else NeutralGray700
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isBuySelected) AccentOrange else MaterialTheme.colorScheme.outlineVariant
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(
                            imageVector = Icons.Default.ThumbUp,
                            contentDescription = null,
                            tint = if (isBuySelected) AccentOrange else NeutralGray500,
                            modifier = Modifier
                                .size(14.dp)
                                .graphicsLayer {
                                    scaleX = buyIconScale
                                    scaleY = buyIconScale
                                    rotationZ = buyIconRotation
                                }
                        )
                        Text(
                            text = if (post.bountyPoints > 0) "살까 ${post.bountyPoints}" else "살까",
                            color = if (isBuySelected) AccentOrange else NeutralGray700,
                            fontWeight = if (isBuySelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        scope.launch {
                            if (isSkipSelected) {
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
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isSkipSelected) NeutralGray200.copy(alpha = 0.6f) else Color.Transparent,
                        contentColor = if (isSkipSelected) NeutralGray900 else NeutralGray700
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isSkipSelected) NeutralGray700 else MaterialTheme.colorScheme.outlineVariant
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(
                            imageVector = Icons.Default.ThumbDown,
                            contentDescription = null,
                            tint = if (isSkipSelected) NeutralGray900 else NeutralGray500,
                            modifier = Modifier
                                .size(14.dp)
                                .graphicsLayer {
                                    scaleX = skipIconScale
                                    scaleY = skipIconScale
                                    rotationZ = skipIconRotation
                                }
                        )
                        Text(
                            text = if (post.likeCount > 0) "말까 ${post.likeCount}" else "말까",
                            color = if (isSkipSelected) NeutralGray900 else NeutralGray700,
                            fontWeight = if (isSkipSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 작성자 / 시간 및 조회수/댓글수
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${post.nickname ?: "익명"} · ${formatRelativeTime(post.createdAt)}",
                    fontSize = 11.sp,
                    color = NeutralGray500
                )

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Visibility,
                            contentDescription = "조회수",
                            modifier = Modifier.size(13.dp),
                            tint = NeutralGray500
                        )
                        Spacer(modifier = Modifier.width(3.dp))
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
                            tint = NeutralGray500
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "${post.commentCount}",
                            fontSize = 11.sp,
                            color = NeutralGray500,
                            fontWeight = FontWeight.Medium
                        )
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
