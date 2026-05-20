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
import androidx.compose.ui.graphics.Brush
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

    val dndEnabled by viewModel.dndEnabled.collectAsState()
    val dndStartTime by viewModel.dndStartTime.collectAsState()
    val dndEndTime by viewModel.dndEndTime.collectAsState()

    var keywordInput by remember { mutableStateOf("") }
    
    // 시간 피커 다이얼로그용 상태
    var showTimePickerDialog by remember { mutableStateOf(false) }
    var isSettingStartTime by remember { mutableStateOf(true) } // true: 시작, false: 종료

    // 에러 발생 시 알림 다이얼로그
    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("알림", fontWeight = FontWeight.Bold) },
            text = { Text(errorMessage!!) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) { Text("확인") }
            }
        )
    }

    // Material 3 TimePickerDialog 구현
    if (showTimePickerDialog) {
        val initialTimeStr = if (isSettingStartTime) dndStartTime else dndEndTime
        val parts = initialTimeStr.split(":")
        val initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 22
        val initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        val timePickerState = rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = false
        )

        AlertDialog(
            onDismissRequest = { showTimePickerDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        val formattedTime = String.format("%02d:%02d", timePickerState.hour, timePickerState.minute)
                        if (isSettingStartTime) {
                            viewModel.updateDndSettings(enabled = dndEnabled, startTime = formattedTime, endTime = dndEndTime)
                        } else {
                            viewModel.updateDndSettings(enabled = dndEnabled, startTime = dndStartTime, endTime = formattedTime)
                        }
                        showTimePickerDialog = false
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("설정 완료")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePickerDialog = false }) {
                    Text("취소")
                }
            },
            title = {
                Text(
                    text = if (isSettingStartTime) "방해금지 시작 시간" else "방해금지 종료 시간",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(state = timePickerState)
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("키워드 및 DND 관리", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
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
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // 💎 Toss 스타일 프리미엄 방해금지(DND) 카드 레이아웃
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ),
                border = CardDefaults.outlinedCardBorder(enabled = true)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "방해금지 시간대 설정 (DND)", 
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "설정된 시간 동안에는 스마트 푸시 알림 발송을 서버에서 차단합니다.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = dndEnabled,
                            onCheckedChange = {
                                viewModel.updateDndSettings(enabled = it, startTime = dndStartTime, endTime = dndEndTime)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            )
                        )
                    }

                    // DND 활성화 시 부드러운 시작/종료 시간 조절 칩 노출
                    AnimatedVisibility(
                        visible = dndEnabled,
                        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
                    ) {
                        Column {
                            Divider(
                                modifier = Modifier.padding(vertical = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                // 시작 시간 조절 캡슐 버튼
                                TimeCapsule(
                                    label = "시작 시간",
                                    time = dndStartTime,
                                    onClick = {
                                        isSettingStartTime = true
                                        showTimePickerDialog = true
                                    }
                                )

                                Text(
                                    text = "부터",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                // 종료 시간 조절 캡슐 버튼
                                TimeCapsule(
                                    label = "종료 시간",
                                    time = dndEndTime,
                                    onClick = {
                                        isSettingStartTime = false
                                        showTimePickerDialog = true
                                    }
                                )

                                Text(
                                    text = "까지",
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 키워드 입력 영역
            Text(
                text = "알림 키워드 등록",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = keywordInput,
                    onValueChange = { keywordInput = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("키워드를 입력해주세요 (예: 에어팟)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                    ),
                    enabled = !isLoading
                )
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                    onClick = {
                        if (keywordInput.isNotBlank()) {
                            viewModel.addKeyword(keywordInput.trim())
                            keywordInput = ""
                        }
                    },
                    modifier = Modifier.height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Add, contentDescription = "추가")
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "등록된 알림 키워드 (${keywords.size}개)",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // 키워드 리스트 (LazyColumn 적용 및 부드러운 전환 효과)
            if (keywords.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "등록된 키워드가 없습니다.\n관심 있는 핫딜 키워드를 추가해보세요!",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Normal,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    items(keywords, key = { it }) { keyword ->
                        AnimatedVisibility(
                            visible = true,
                            enter = fadeIn(animationSpec = tween(300)) + expandVertically(),
                            exit = fadeOut(animationSpec = tween(300)) + shrinkVertically()
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
}

@Composable
fun TimeCapsule(
    label: String,
    time: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = time,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun KeywordItem(keyword: String, onDelete: () -> Unit, isEnabled: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
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
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        IconButton(
            onClick = onDelete,
            enabled = isEnabled,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "삭제",
                tint = if (isEnabled) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}


