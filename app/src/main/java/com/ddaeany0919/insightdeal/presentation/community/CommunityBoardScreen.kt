package com.ddaeany0919.insightdeal.presentation.community

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.ThumbsUpDown
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.ddaeany0919.insightdeal.data.network.CommunityPostDto
import com.ddaeany0919.insightdeal.presentation.auth.AuthManager
import com.ddaeany0919.insightdeal.presentation.bounceClick
import com.ddaeany0919.insightdeal.presentation.decodeHtmlEntities
import com.ddaeany0919.insightdeal.presentation.formatPrice
import com.ddaeany0919.insightdeal.presentation.formatRelativeTime

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
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val currentUserId by AuthManager.getUsername(context).collectAsStateWithLifecycle(initialValue = "guest")
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
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
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
                            "게시글을 불러오는 중이에요",
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
                                "아직 등록된 게시글이 없어요",
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
                                onClick = {
                                    if (isLoggedIn) {
                                        navController?.navigate("community_write")
                                    } else {
                                        showLoginIncentiveDialog = true
                                    }
                                },
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
                                    currentUserId = currentUserId ?: "guest",
                                    onEditClick = { navController?.navigate("community_edit/${post.id}") },
                                    onVoteSubmit = { viewModel.loadPosts(showLoading = false) },
                                    navController = navController,
                                    onClick = { navController?.navigate("community_detail/${post.id}") },
                                    viewModel = viewModel,
                                    onLoginRequired = { showLoginIncentiveDialog = true }
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
                Text("로그인이 필요해요", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface)
            },
            text = {
                Text("커뮤니티 게시글 작성 및 투표 참여는 로그인 후 이용할 수 있어요.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLoginIncentiveDialog = false
                        navController?.navigate("auth")
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
    viewModel: CommunityBoardViewModel,
    onLoginRequired: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val linkedDeals by viewModel.linkedDeals.collectAsStateWithLifecycle()

    var userVote by remember(post.id) { mutableStateOf(viewModel.getUserVote(post.id)) }

    LaunchedEffect(post.id) {
        userVote = viewModel.getUserVote(post.id)
    }

    val dealId = remember(post.location) { viewModel.parseLinkedDealId(post.location) }
    val linkedDeal = dealId?.let { linkedDeals[it] }

    LaunchedEffect(post.location) {
        viewModel.ensureLinkedDealLoaded(post.location)
    }

    val totalVotes = post.bountyPoints + post.likeCount
    val buyRatio = if (totalVotes > 0) post.bountyPoints.toFloat() / totalVotes else 0.5f
    val skipRatio = if (totalVotes > 0) post.likeCount.toFloat() / totalVotes else 0.5f

    val animatedProgress by animateFloatAsState(
        targetValue = buyRatio,
        animationSpec = spring(
            dampingRatio = 0.55f,
            stiffness = 120f
        ),
        label = "progressAnimation"
    )

    val buyIconScale by animateFloatAsState(
        targetValue = if (userVote == "BUY") 1.2f else 1.0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 250f),
        label = "buyIconScale"
    )
    val buyIconRotation by animateFloatAsState(
        targetValue = if (userVote == "BUY") -12f else 0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 200f),
        label = "buyIconRotation"
    )
    val skipIconScale by animateFloatAsState(
        targetValue = if (userVote == "SKIP") 1.2f else 1.0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 250f),
        label = "skipIconScale"
    )
    val skipIconRotation by animateFloatAsState(
        targetValue = if (userVote == "SKIP") 12f else 0f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = 200f),
        label = "skipIconRotation"
    )

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

                if (currentUserId != "guest" && post.userId == currentUserId) {
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

            linkedDeal?.let { deal ->
                val cleanDealTitle = remember(deal.id, deal.title) { decodeHtmlEntities(deal.title) }
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
                                com.ddaeany0919.insightdeal.data.util.ImageUrlResolver.resolve(deal.imageUrl, targetWidth = 100)
                            }
                            val fallbackThumbUrl = remember(deal.imageUrl) {
                                com.ddaeany0919.insightdeal.data.util.ImageUrlResolver.getFallbackBackendProxyUrl(deal.imageUrl)
                            }
                            var activeThumbUrl by remember(proxyThumbUrl) { mutableStateOf(proxyThumbUrl) }
                            val imageRequest = remember(activeThumbUrl) {
                                coil.request.ImageRequest.Builder(context)
                                    .data(activeThumbUrl)
                                    .crossfade(true)
                                    .build()
                            }
                            AsyncImage(
                                model = imageRequest,
                                onError = {
                                    if (fallbackThumbUrl != null && activeThumbUrl != fallbackThumbUrl) {
                                        activeThumbUrl = fallbackThumbUrl
                                    }
                                },
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = cleanDealTitle,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = formatPrice(deal.price, deal.currency, cleanDealTitle),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFF5A26)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

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
                            color = if (buyRatio >= skipRatio) AccentOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                        Text(
                            text = "말까 ${"%.0f".format(skipRatio * 100)}% (${post.likeCount})",
                            fontWeight = FontWeight.SemiBold,
                            color = if (skipRatio > buyRatio) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
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
                        if (currentUserId == "guest" || currentUserId.isBlank()) {
                            if (onLoginRequired != null) onLoginRequired() else navController?.navigate("auth")
                            return@OutlinedButton
                        }
                        if (isBuySelected) {
                            viewModel.cancelVotePost(post, "BUY") {
                                userVote = null
                                onVoteSubmit()
                            }
                        } else {
                            viewModel.votePost(post, isBuy = true, oldVote = userVote) {
                                userVote = "BUY"
                                onVoteSubmit()
                            }
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isBuySelected) AccentOrange.copy(alpha = 0.14f) else Color.Transparent,
                        contentColor = if (isBuySelected) AccentOrange else MaterialTheme.colorScheme.onSurfaceVariant
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
                            tint = if (isBuySelected) AccentOrange else MaterialTheme.colorScheme.onSurfaceVariant,
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
                            color = if (isBuySelected) AccentOrange else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isBuySelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                }

                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (currentUserId == "guest" || currentUserId.isBlank()) {
                            if (onLoginRequired != null) onLoginRequired() else navController?.navigate("auth")
                            return@OutlinedButton
                        }
                        if (isSkipSelected) {
                            viewModel.cancelVotePost(post, "SKIP") {
                                userVote = null
                                onVoteSubmit()
                            }
                        } else {
                            viewModel.votePost(post, isBuy = false, oldVote = userVote) {
                                userVote = "SKIP"
                                onVoteSubmit()
                            }
                        }
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isSkipSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
                        contentColor = if (isSkipSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (isSkipSelected) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outlineVariant
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(vertical = 8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(
                            imageVector = Icons.Default.ThumbDown,
                            contentDescription = null,
                            tint = if (isSkipSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
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
                            color = if (isSkipSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isSkipSelected) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "${post.nickname ?: "익명"} · ${formatRelativeTime(post.createdAt.orEmpty())}",
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
