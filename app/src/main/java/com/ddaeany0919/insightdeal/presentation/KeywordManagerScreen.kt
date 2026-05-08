package com.ddaeany0919.insightdeal.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeywordManagerScreen(
    onNavigateBack: () -> Unit,
    viewModel: KeywordManagerViewModel = viewModel()
) {
    val context = LocalContext.current
    
    // ViewModel StateFlow 구독
    val keywords by viewModel.keywords.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    var keywordInput by remember { mutableStateOf("") }
    var isNightModeEnabled by remember { mutableStateOf(true) }

    // 에러 발생 시 Snackbar나 Toast 처리 (여기서는 간이 AlertDialog)
    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("오류") },
            text = { Text(errorMessage!!) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) { Text("확인") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("알림 키워드 관리", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // 야간 방해금지 설정 영역 (법적 필수)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "야간 푸시 알림 차단", 
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            text = "정보통신망법에 따라 야간(21:00~08:00)에는 알림을 받지 않습니다.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isNightModeEnabled,
                        onCheckedChange = { isNightModeEnabled = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 키워드 입력 영역
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = keywordInput,
                    onValueChange = { keywordInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("예: 아이폰 15") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    enabled = !isLoading
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (keywordInput.isNotBlank()) {
                            viewModel.addKeyword(keywordInput)
                            keywordInput = ""
                        }
                    },
                    modifier = Modifier.height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White)
                    } else {
                        Icon(Icons.Default.Add, contentDescription = "추가")
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "등록된 키워드 (${keywords.size}개)",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // 키워드 리스트
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(keywords, key = { it }) { keyword ->
                    AnimatedVisibility(
                        visible = true,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        KeywordItem(
                            keyword = keyword,
                            onDelete = { viewModel.deleteKeyword(keyword) },
                            isEnabled = !isLoading
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun KeywordItem(keyword: String, onDelete: () -> Unit, isEnabled: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = keyword,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        Icon(
            imageVector = Icons.Default.Delete,
            contentDescription = "삭제",
            tint = if (isEnabled) MaterialTheme.colorScheme.error else Color.Gray,
            modifier = Modifier
                .clickable(enabled = isEnabled) { onDelete() }
                .padding(4.dp)
        )
    }
}

