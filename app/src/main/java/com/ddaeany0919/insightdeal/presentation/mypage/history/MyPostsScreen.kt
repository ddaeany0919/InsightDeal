package com.ddaeany0919.insightdeal.presentation.mypage.history

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.ddaeany0919.insightdeal.presentation.auth.AuthManager
import com.ddaeany0919.insightdeal.presentation.community.CommunityBoardUiState
import com.ddaeany0919.insightdeal.presentation.community.CommunityBoardViewModel
import com.ddaeany0919.insightdeal.presentation.community.ShouldIBuyVoteCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyPostsScreen(
    navController: NavController? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: CommunityBoardViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val uiState by viewModel.uiState.collectAsState()
    val currentUserId by AuthManager.getUsername(context).collectAsState(initial = "guest")

    LaunchedEffect(Unit) {
        viewModel.loadPosts()
    }

    val myPosts = remember(uiState, currentUserId) {
        if (uiState is CommunityBoardUiState.Success) {
            (uiState as CommunityBoardUiState.Success).posts.filter { it.userId == currentUserId }
        } else emptyList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("작성글", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                }
            )
        },
        containerColor = Color(0xFFF8F9FA)
    ) { paddingValues ->
        if (uiState is CommunityBoardUiState.Loading) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFFFF6B35))
            }
        } else if (myPosts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "아직 작성하신 게시글이 없습니다.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(
                    items = myPosts,
                    key = { it.id },
                    contentType = { "my_post_item" }
                ) { post ->
                    ShouldIBuyVoteCard(
                        post = post,
                        currentUserId = currentUserId ?: "guest",
                        // [Bug Fix] MainActivity의 정의된 라우트("community_edit/{postId}")와 일치하도록 수정
                        onEditClick = { navController?.navigate("community_edit/${post.id}") },
                        onVoteSubmit = { viewModel.loadPosts(showLoading = false) },
                        navController = navController,
                        onClick = { navController?.navigate("community_detail/${post.id}") },
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}
