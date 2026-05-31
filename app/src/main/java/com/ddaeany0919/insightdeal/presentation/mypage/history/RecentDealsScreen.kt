package com.ddaeany0919.insightdeal.presentation.mypage.history

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ddaeany0919.insightdeal.presentation.home.DealCardComposable
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentDealsScreen(
    onBack: () -> Unit,
    onDealClick: (Int) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(Unit) {
        RecentDealManager.init(context)
    }
    
    val recentDeals by RecentDealManager.recentDeals.collectAsState()

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("최근 본 핫딜", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                actions = {
                    if (recentDeals.isNotEmpty()) {
                        IconButton(onClick = { RecentDealManager.clearRecentDeals(context) }) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = "전체 삭제")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (recentDeals.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "최근 본 핫딜이 없습니다.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(recentDeals, key = { it.id }) { deal ->
                    val currentDeal = deal
                    
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { dismissValue ->
                            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                // 1. 햅틱 피드백 수행 (손맛이 살아있는 다이내믹 피드백)
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                
                                // 2. 삭제 취소를 대비한 현재 리스트 백업
                                val backupList = recentDeals.toList()
                                
                                // 3. 리스트에서 아이템 제거
                                RecentDealManager.removeRecentDeal(context, currentDeal.id)
                                
                                // 4. Undo 스낵바 제공
                                coroutineScope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    val result = snackbarHostState.showSnackbar(
                                        message = "최근 본 핫딜을 삭제했습니다.",
                                        actionLabel = "실행 취소",
                                        duration = SnackbarDuration.Short
                                    )
                                    if (result == SnackbarResult.ActionPerformed) {
                                        // 실행 취소 시 원래 리스트 및 정확한 순서 복구
                                        RecentDealManager.updateRecentDeals(context, backupList)
                                    }
                                }
                                true
                            } else {
                                false
                            }
                        }
                    )

                    // SwipeToDismissBox를 활용한 카드 스와이프 감싸기 (홈 화면과 완전한 격리 달성)
                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false, // 오른쪽에서 왼쪽으로의 스와이프만 허용
                        backgroundContent = {
                            val color by animateColorAsState(
                                targetValue = when (dismissState.targetValue) {
                                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.errorContainer
                                    else -> Color.Transparent
                                },
                                label = "dismissBackgroundColor"
                            )
                            
                            val iconColor = MaterialTheme.colorScheme.onErrorContainer
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(MaterialTheme.shapes.medium)
                                    .background(color)
                                    .padding(horizontal = 24.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "삭제",
                                    tint = iconColor,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        },
                        content = {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                DealCardComposable(
                                    deal = currentDeal,
                                    onDetailClick = { onDealClick(currentDeal.id) }
                                )
                                
                                // 사용자가 요청한 우측 하단 휴지통 단추 추가 (터치 오작동이 없는 하단 영역에 배치)
                                IconButton(
                                    onClick = {
                                        // 1. 햅틱 피드백 수행 (진동)
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        
                                        // 2. 삭제 취소를 대비한 현재 리스트 백업
                                        val backupList = recentDeals.toList()
                                        
                                        // 3. 리스트에서 아이템 제거
                                        RecentDealManager.removeRecentDeal(context, currentDeal.id)
                                        
                                        // 4. Undo 스낵바 제공
                                        coroutineScope.launch {
                                            snackbarHostState.currentSnackbarData?.dismiss()
                                            val result = snackbarHostState.showSnackbar(
                                                message = "최근 본 핫딜을 삭제했습니다.",
                                                actionLabel = "실행 취소",
                                                duration = SnackbarDuration.Short
                                            )
                                            if (result == SnackbarResult.ActionPerformed) {
                                                // 실행 취소 시 원래 리스트 및 정확한 순서 복구
                                                RecentDealManager.updateRecentDeals(context, backupList)
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(bottom = 12.dp, end = 12.dp)
                                        .size(36.dp)
                                        .background(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f),
                                            shape = MaterialTheme.shapes.small
                                        )
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete, // 빨간색으로 채워진 삭제 휴지통
                                        contentDescription = "삭제",
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
