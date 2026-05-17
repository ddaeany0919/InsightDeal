package com.ddaeany0919.insightdeal.presentation.community

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Link
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CommunityPostDetailViewModel : ViewModel() {
    private val api = NetworkModule.createService<com.ddaeany0919.insightdeal.network.ApiService>()
    
    private val _post = MutableStateFlow<CommunityPostDto?>(null)
    val post: StateFlow<CommunityPostDto?> = _post.asStateFlow()
    
    private val _linkedDeal = MutableStateFlow<DealItem?>(null)
    val linkedDeal: StateFlow<DealItem?> = _linkedDeal.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _isCommentSubmitting = MutableStateFlow(false)
    val isCommentSubmitting: StateFlow<Boolean> = _isCommentSubmitting.asStateFlow()
    
    fun loadPostDetail(postId: Int, showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading) {
                _isLoading.value = true
            }
            try {
                val response = api.getCommunityPost(postId)
                if (response.isSuccessful && response.body() != null) {
                    val p = response.body()!!
                    _post.value = p
                    p.location?.let { loc ->
                        val dealId = if (loc.startsWith("deal_id:")) {
                            loc.removePrefix("deal_id:").toIntOrNull()
                        } else {
                            loc.toIntOrNull()
                        }
                        if (dealId != null) {
                            loadLinkedDeal(dealId)
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore
            } finally {
                if (showLoading) {
                    _isLoading.value = false
                }
            }
        }
    }
    
    private fun loadLinkedDeal(dealId: Int) {
        viewModelScope.launch {
            try {
                val response = api.getEnhancedDealInfo(dealId)
                if (response.isSuccessful && response.body() != null) {
                    _linkedDeal.value = response.body()
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
    
    fun submitComment(postId: Int, userId: String, nickname: String, content: String, dealUrl: String? = null, parentId: Int? = null, onComplete: () -> Unit) {
        viewModelScope.launch {
            _isCommentSubmitting.value = true
            try {
                val req = com.ddaeany0919.insightdeal.data.network.CommunityCommentCreateReq(
                    userId = userId,
                    nickname = nickname,
                    content = content,
                    dealUrl = dealUrl,
                    parentId = parentId
                )
                val response = api.createCommunityComment(postId, req)
                if (response.isSuccessful) {
                    loadPostDetail(postId, showLoading = false)
                    onComplete()
                }
            } catch (e: Exception) {
                // Ignore
            } finally {
                _isCommentSubmitting.value = false
            }
        }
    }
    
    fun acceptComment(postId: Int, commentId: Int, userId: String) {
        viewModelScope.launch {
            try {
                val response = api.acceptCommunityComment(postId, commentId, userId)
                if (response.isSuccessful) {
                    loadPostDetail(postId, showLoading = false)
                }
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommunityPostDetailScreen(
    postId: Int,
    navController: NavController? = null,
    viewModel: CommunityPostDetailViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    val post by viewModel.post.collectAsState()
    val linkedDeal by viewModel.linkedDeal.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isCommentSubmitting by viewModel.isCommentSubmitting.collectAsState()
    
    val currentUserId by AuthManager.getUsername(context).collectAsState(initial = "admin")
    val currentUserNickname by AuthManager.getNickname(context).collectAsState(initial = "익명")
    
    var commentText by remember { mutableStateOf("") }
    var referenceUrl by remember { mutableStateOf("") }
    var showUrlInput by remember { mutableStateOf(false) }
    var replyingToCommentId by remember { mutableStateOf<Int?>(null) }
    var replyingToNickname by remember { mutableStateOf<String?>(null) }
    
    LaunchedEffect(postId) {
        viewModel.loadPostDetail(postId)
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("살까말까 고민 상세", fontWeight = FontWeight.Bold, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = { 
                        navController?.navigate("community") {
                            popUpTo("community") { inclusive = true }
                        }
                    }) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                color = Color.White,
                modifier = Modifier.imePadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    if (replyingToNickname != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "↳ $replyingToNickname 님에게 답글 작성 중...",
                                fontSize = 12.sp,
                                color = AccentOrange,
                                fontWeight = FontWeight.SemiBold
                            )
                            IconButton(
                                onClick = { 
                                    replyingToCommentId = null
                                    replyingToNickname = null
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Text("✕", fontSize = 14.sp, color = NeutralGray500)
                            }
                        }
                    }
                    if (showUrlInput) {
                        OutlinedTextField(
                            value = referenceUrl,
                            onValueChange = { referenceUrl = it },
                            placeholder = { Text("참고할 핫딜/상품 링크 (선택)", fontSize = 13.sp, color = NeutralGray500) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .padding(bottom = 6.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentOrange,
                                focusedLabelColor = AccentOrange
                            )
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = { showUrlInput = !showUrlInput },
                            modifier = Modifier.size(36.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = if (showUrlInput) AccentOrangeSoft else NeutralGray50
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Link,
                                contentDescription = "링크 첨부",
                                tint = if (showUrlInput) AccentOrange else NeutralGray500,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        
                        OutlinedTextField(
                            value = commentText,
                            onValueChange = { commentText = it },
                            placeholder = { Text("조언이나 피드백을 남겨주세요...", fontSize = 13.sp) },
                            modifier = Modifier.weight(1f).heightIn(min = 40.dp, max = 80.dp),
                            maxLines = 3,
                            shape = RoundedCornerShape(20.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = AccentOrange,
                                focusedLabelColor = AccentOrange
                            )
                        )
                        
                        IconButton(
                            onClick = {
                                if (commentText.isNotBlank()) {
                                    viewModel.submitComment(
                                        postId = postId,
                                        userId = currentUserId ?: "admin",
                                        nickname = currentUserNickname ?: "익명 고수",
                                        content = commentText,
                                        dealUrl = referenceUrl.takeIf { it.isNotBlank() },
                                        parentId = replyingToCommentId
                                    ) {
                                        commentText = ""
                                        referenceUrl = ""
                                        showUrlInput = false
                                        replyingToCommentId = null
                                        replyingToNickname = null
                                    }
                                }
                            },
                            enabled = commentText.isNotBlank() && !isCommentSubmitting,
                            modifier = Modifier.size(36.dp),
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = AccentOrange,
                                contentColor = Color.White,
                                disabledContainerColor = NeutralGray200,
                                disabledContentColor = NeutralGray500
                            )
                        ) {
                            Icon(imageVector = Icons.Default.Send, contentDescription = "전송", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        },
        containerColor = NeutralGray50
    ) { paddingValues ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentOrange)
            }
            return@Scaffold
        }
        
        post?.let { currentPost ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. 메인 고민글 본문 카드
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // 헤더 (조회수 및 작성시간 포함 초컴팩트 통합 헤더 - 작성자 제거)
                            Row(
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        color = AccentOrangeSoft,
                                        shape = RoundedCornerShape(20.dp)
                                    ) {
                                        Text(
                                            text = "⚖️ 살까말까 고민",
                                            color = AccentOrange,
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                        )
                                    }
                                    if (currentPost.isResolved) {
                                        Surface(
                                            color = Color(0xFFFFF9DB),
                                            shape = RoundedCornerShape(20.dp)
                                        ) {
                                            Text(
                                                text = "🏆 답변 완료",
                                                color = Color(0xFFF59F00),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                            )
                                        }
                                    }
                                }
                                
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = formatRelativeTime(currentPost.createdAt),
                                        fontSize = 11.sp,
                                        color = NeutralGray500
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Outlined.Visibility, 
                                            contentDescription = "조회수", 
                                            modifier = Modifier.size(13.dp),
                                            tint = NeutralGray500
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = "${currentPost.viewCount}", 
                                            fontSize = 11.sp, 
                                            color = NeutralGray500
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(14.dp))
                            
                            Text(
                                text = currentPost.title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = NeutralGray900
                            )
                            
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            Text(
                                text = currentPost.content,
                                style = MaterialTheme.typography.bodyLarge,
                                color = NeutralGray700,
                                lineHeight = 24.sp
                            )
                            
                            if (currentPost.targetPrice != null && currentPost.targetPrice > 0) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                    color = NeutralGray50,
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "💰 고민중인 체감가: ${"%,d".format(currentPost.targetPrice)}원",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = NeutralGray900,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                // 2. 연동된 핫딜 상품 카드
                linkedDeal?.let { deal ->
                    item {
                        Card(
                            onClick = {
                                navController?.navigate("deal_detail/${deal.id}")
                            },
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = BorderStroke(1.5.dp, AccentOrange.copy(alpha = 0.2f)),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ShoppingCart,
                                        contentDescription = null,
                                        tint = AccentOrange,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "🛍️ 연동된 핫딜 상품 정보",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AccentOrange
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    if (!deal.imageUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = deal.imageUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .size(72.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(NeutralGray50)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Surface(
                                                color = AccentOrangeSoft,
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = deal.siteName,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = AccentOrange,
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                )
                                            }
                                            if (deal.discountRate != null && deal.discountRate > 0) {
                                                Text(
                                                    text = "${deal.discountRate}% 할인",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Black,
                                                    color = AccentOrange
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = deal.title,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NeutralGray900,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${"%,d".format(deal.price)}원",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = NeutralGray900
                                        )
                                    }
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "이동",
                                        tint = NeutralGray400
                                    )
                                }
                            }
                        }
                    }
                }
                
                // 3. 살까말까 투표 패널 (중복 제거되고 오직 투표 상태/현황/버튼만 렌더링)
                item {
                    ShouldIBuyDetailVotePanel(
                        post = currentPost,
                        onVoteSubmit = { viewModel.loadPostDetail(postId, showLoading = false) }
                    )
                }
                
                // 4. 댓글 리더보드 헤더
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ChatBubbleOutline,
                            contentDescription = null,
                            tint = AccentOrange,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "댓글 (${currentPost.comments?.size ?: 0})",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeutralGray900
                        )
                    }
                }
                
                // 5. 댓글 목록
                val commentList = currentPost.comments ?: emptyList()
                if (commentList.isEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 36.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("💬", fontSize = 28.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "첫 번째 댓글을 남겨 고민 해결을 도와주세요!",
                                        fontSize = 13.sp,
                                        color = NeutralGray500,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                } else {
                    items(commentList, key = { it.id }) { comment ->
                        val isReply = comment.parentId != null
                        Row(modifier = Modifier.fillMaxWidth()) {
                            if (isReply) {
                                Spacer(modifier = Modifier.width(32.dp))
                            }
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (comment.isAccepted) Color(0xFFFFFDF5) else if (isReply) NeutralGray50 else Color.White
                                ),
                                border = if (comment.isAccepted) BorderStroke(1.5.dp, Color(0xFFF59F00)) else null,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(26.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    if (comment.isAccepted) {
                                                        Brush.linearGradient(listOf(Color(0xFFFFE066), Color(0xFFF59F00)))
                                                    } else {
                                                        Brush.linearGradient(listOf(Color(0xFFE9ECEF), Color(0xFFCED4DA)))
                                                    }
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = comment.nickname.take(1).uppercase(),
                                                color = if (comment.isAccepted) Color.White else NeutralGray700,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = comment.nickname,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = NeutralGray900
                                        )
                                        
                                        if (comment.isAccepted) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                color = Color(0xFFFFF9DB),
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = "🏆 채택됨",
                                                    color = Color(0xFFF59F00),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Text(
                                            text = formatRelativeTime(comment.createdAt),
                                            fontSize = 10.sp,
                                            color = NeutralGray500
                                        )
                                        Text(
                                            text = "답글달기",
                                            fontSize = 10.sp,
                                            color = AccentOrange,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clickable {
                                                replyingToCommentId = comment.parentId ?: comment.id
                                                replyingToNickname = comment.nickname
                                            }
                                        )
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Text(
                                    text = comment.content,
                                    fontSize = 13.sp,
                                    color = NeutralGray800,
                                    lineHeight = 20.sp
                                )
                                
                                if (!comment.dealUrl.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Surface(
                                        color = NeutralGray50,
                                        shape = RoundedCornerShape(6.dp),
                                        border = BorderStroke(0.5.dp, NeutralGray200),
                                        modifier = Modifier.clickable {
                                            try {
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(comment.dealUrl))
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                // Ignore
                                            }
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Link,
                                                contentDescription = null,
                                                tint = AccentOrange,
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Text(
                                                text = "추천 핫딜 링크 바로가기 🔗",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = AccentOrange
                                            )
                                        }
                                    }
                                }
                                
                                // 채택 버튼 노출 조건: 글쓴이가 본인이고, 아직 고민 해결 전 상태이고, 자신이 쓴 댓글이 아닌 경우
                                if (currentPost.userId == currentUserId && !currentPost.isResolved && comment.userId != currentUserId) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(
                                            onClick = {
                                                viewModel.acceptComment(
                                                    postId = currentPost.id,
                                                    commentId = comment.id,
                                                    userId = currentUserId ?: "admin"
                                                )
                                            },
                                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFF59F00))
                                        ) {
                                            Text("이 답변 채택하기 👍", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

@Composable
fun ShouldIBuyDetailVotePanel(
    post: CommunityPostDto,
    onVoteSubmit: () -> Unit
) {
    val context = LocalContext.current
    val api = remember { NetworkModule.createService<com.ddaeany0919.insightdeal.network.ApiService>() }
    val scope = rememberCoroutineScope()
    
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 타이틀
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ThumbsUpDown,
                        contentDescription = null,
                        tint = AccentOrange,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "실시간 살까말까 투표",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeutralGray900
                    )
                }
                if (totalVotes > 0) {
                    Surface(
                        color = AccentOrangeSoft,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = "🔥 ${totalVotes}명 투표 완료",
                            color = AccentOrange,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                } else {
                    Surface(
                        color = NeutralGray50,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = "첫 투표의 주인공이 되어보세요!",
                            color = NeutralGray500,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            
            Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clip(RoundedCornerShape(14.dp))
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
                            .padding(horizontal = 14.dp),
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
            Spacer(modifier = Modifier.height(10.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                if (userVote == "BUY") {
                                    val newBuy = (post.bountyPoints - 1).coerceAtLeast(0)
                                    val req = CommunityPostCreateReq(
                                        postType = post.postType, userId = post.userId, nickname = post.nickname, title = post.title, content = post.content, targetPrice = post.targetPrice, bountyPoints = newBuy, likeCount = post.likeCount, location = post.location
                                    )
                                    val response = api.updateCommunityPost(post.id, req)
                                    if (response.isSuccessful) {
                                        val prefs = context.getSharedPreferences("should_i_buy_votes", Context.MODE_PRIVATE)
                                        prefs.edit().remove("vote_${post.id}").apply()
                                        userVote = null
                                        onVoteSubmit()
                                    }
                                } else {
                                    var newBuy = post.bountyPoints
                                    var newSkip = post.likeCount
                                    
                                    if (userVote == "SKIP") newSkip = (newSkip - 1).coerceAtLeast(0)
                                    newBuy += 1
                                    
                                    val req = CommunityPostCreateReq(
                                        postType = post.postType, userId = post.userId, nickname = post.nickname, title = post.title, content = post.content, targetPrice = post.targetPrice, bountyPoints = newBuy, likeCount = newSkip, location = post.location
                                    )
                                    val response = api.updateCommunityPost(post.id, req)
                                    if (response.isSuccessful) {
                                        saveUserVote(context, post.id, "BUY")
                                        userVote = "BUY"
                                        onVoteSubmit()
                                    }
                                }
                            } catch (e: Exception) {}
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (userVote == "BUY") AccentOrange else if (userVote == "SKIP") NeutralGray50 else AccentOrangeSoft),
                    border = BorderStroke(1.dp, if (userVote == "BUY") AccentOrange else if (userVote == "SKIP") NeutralGray200 else AccentOrange),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.ThumbUp, contentDescription = null, tint = if (userVote == "BUY") Color.White else if (userVote == "SKIP") NeutralGray500 else AccentOrange, modifier = Modifier.size(16.dp))
                        Text("살까 👍", color = if (userVote == "BUY") Color.White else if (userVote == "SKIP") NeutralGray500 else AccentOrange, fontWeight = FontWeight.Bold)
                    }
                }
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                if (userVote == "SKIP") {
                                    val newSkip = (post.likeCount - 1).coerceAtLeast(0)
                                    val req = CommunityPostCreateReq(
                                        postType = post.postType, userId = post.userId, nickname = post.nickname, title = post.title, content = post.content, targetPrice = post.targetPrice, bountyPoints = post.bountyPoints, likeCount = newSkip, location = post.location
                                    )
                                    val response = api.updateCommunityPost(post.id, req)
                                    if (response.isSuccessful) {
                                        val prefs = context.getSharedPreferences("should_i_buy_votes", Context.MODE_PRIVATE)
                                        prefs.edit().remove("vote_${post.id}").apply()
                                        userVote = null
                                        onVoteSubmit()
                                    }
                                } else {
                                    var newBuy = post.bountyPoints
                                    var newSkip = post.likeCount
                                    
                                    if (userVote == "BUY") newBuy = (newBuy - 1).coerceAtLeast(0)
                                    newSkip += 1
                                    
                                    val req = CommunityPostCreateReq(
                                        postType = post.postType, userId = post.userId, nickname = post.nickname, title = post.title, content = post.content, targetPrice = post.targetPrice, bountyPoints = newBuy, likeCount = newSkip, location = post.location
                                    )
                                    val response = api.updateCommunityPost(post.id, req)
                                    if (response.isSuccessful) {
                                        saveUserVote(context, post.id, "SKIP")
                                        userVote = "SKIP"
                                        onVoteSubmit()
                                    }
                                }
                            } catch (e: Exception) {}
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (userVote == "SKIP") NeutralGray700 else NeutralGray50),
                    border = BorderStroke(1.dp, if (userVote == "SKIP") NeutralGray700 else NeutralGray200),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.ThumbDown, contentDescription = null, tint = if (userVote == "SKIP") Color.White else NeutralGray500, modifier = Modifier.size(16.dp))
                        Text("말까 👎", color = if (userVote == "SKIP") Color.White else NeutralGray700, fontWeight = FontWeight.Bold)
                    }
                }
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

private val AccentOrange = Color(0xFFFF6B35)
private val AccentOrangeSoft = Color(0xFFFFF0EB)
private val NeutralGray50 = Color(0xFFF8F9FA)
private val NeutralGray200 = Color(0xFFE9ECEF)
private val NeutralGray400 = Color(0xFFCED4DA)
private val NeutralGray500 = Color(0xFF868E96)
private val NeutralGray700 = Color(0xFF495057)
private val NeutralGray800 = Color(0xFF343A40)
private val NeutralGray900 = Color(0xFF212529)
