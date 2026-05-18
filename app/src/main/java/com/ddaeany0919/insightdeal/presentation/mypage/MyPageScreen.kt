package com.ddaeany0919.insightdeal.presentation.mypage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.HeadsetMic
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ddaeany0919.insightdeal.presentation.auth.AuthManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPageScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToWatchlist: () -> Unit,
    onNavigateToMyPosts: () -> Unit,
    onNavigateToMyComments: () -> Unit,
    onNavigateToRecentDeals: () -> Unit
) {
    val context = LocalContext.current
    val username by AuthManager.getUsername(context).collectAsState(initial = "admin")
    
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("내 정보", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
        ) {
            // 사용자 프로필 요약 카드
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNavigateToSettings() }
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(modifier = Modifier.width(20.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "$username 님", 
                            style = MaterialTheme.typography.titleLarge, 
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "일반 회원", 
                            style = MaterialTheme.typography.bodyMedium, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = "프로필 수정",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Quick Stats (쿠폰, 포인트, 리뷰)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
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
            
            // 구분선
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            )

            // 메뉴 목록
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Text(
                    text = "나의 활동", 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Bold, 
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                MyPageMenuItem(icon = Icons.Outlined.FavoriteBorder, title = "내 관심목록 (찜)", onClick = onNavigateToWatchlist)
                MyPageMenuItem(icon = Icons.Outlined.History, title = "최근 본 핫딜", value = "${recentDeals.size} 건", onClick = onNavigateToRecentDeals)
                MyPageMenuItem(icon = Icons.Outlined.ChatBubbleOutline, title = "내가 쓴 댓글", value = "$myCommentsCount 건", onClick = onNavigateToMyComments)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp)
            ) {
                Text(
                    text = "고객 지원", 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Bold, 
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                MyPageMenuItem(icon = Icons.Outlined.HeadsetMic, title = "고객센터", onClick = { 
                    val intent = android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:kth0000919@gmail.com"))
                    intent.putExtra(android.content.Intent.EXTRA_SUBJECT, "[InsightDeal] 앱 문의사항")
                    try {
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        android.widget.Toast.makeText(context, "메일 앱을 찾을 수 없습니다.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                })
                MyPageMenuItem(icon = Icons.Outlined.Info, title = "버전 정보", value = "v1.0.0", onClick = { /* TODO */ })
            }
        }
    }
}

@Composable
fun QuickStatCard(modifier: Modifier = Modifier, title: String, value: String, onClick: () -> Unit = {}) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
fun MyPageMenuItem(icon: ImageVector, title: String, value: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title, 
            style = MaterialTheme.typography.bodyLarge, 
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        if (value != null) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
    }
}

