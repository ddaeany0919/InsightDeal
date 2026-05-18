package com.ddaeany0919.insightdeal.presentation.mypage.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.ddaeany0919.insightdeal.network.NetworkModule
import com.ddaeany0919.insightdeal.network.UserCommentDto
import com.ddaeany0919.insightdeal.presentation.auth.AuthManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class MyCommentsUiState {
    object Loading : MyCommentsUiState()
    data class Success(val comments: List<UserCommentDto>) : MyCommentsUiState()
    data class Error(val message: String) : MyCommentsUiState()
}

class MyCommentsViewModel : ViewModel() {
    private val api = NetworkModule.createService<com.ddaeany0919.insightdeal.network.ApiService>()
    
    private val _uiState = MutableStateFlow<MyCommentsUiState>(MyCommentsUiState.Loading)
    val uiState: StateFlow<MyCommentsUiState> = _uiState.asStateFlow()

    fun loadComments(userId: String) {
        viewModelScope.launch {
            _uiState.value = MyCommentsUiState.Loading
            try {
                val response = api.getUserComments(userId)
                if (response.isSuccessful && response.body() != null) {
                    _uiState.value = MyCommentsUiState.Success(response.body()!!)
                } else {
                    _uiState.value = MyCommentsUiState.Error("데이터를 불러오지 못했습니다.")
                }
            } catch (e: Exception) {
                _uiState.value = MyCommentsUiState.Error(e.message ?: "알 수 없는 오류")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyCommentsScreen(
    navController: NavController? = null,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val currentUserId by AuthManager.getUsername(context).collectAsState(initial = "admin")
    val viewModel: MyCommentsViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(currentUserId) {
        viewModel.loadComments(currentUserId ?: "admin")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("작성한 댓글", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                }
            )
        }
    ) { paddingValues ->
        when (val state = uiState) {
            is MyCommentsUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            is MyCommentsUiState.Error -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            is MyCommentsUiState.Success -> {
                val myComments = state.comments
                if (myComments.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "아직 작성하신 댓글이 없습니다.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(myComments, key = { it.id }) { comment ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { 
                                        navController?.navigate("community_detail/${comment.post_id}")
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = comment.content,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(12.dp))
                                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "원문: ${comment.post_title}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                                        )
                                        Text(
                                            text = formatRelativeTime(comment.created_at),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
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
