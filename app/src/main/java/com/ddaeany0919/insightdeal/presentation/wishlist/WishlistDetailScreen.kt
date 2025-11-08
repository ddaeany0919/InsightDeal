package com.ddaeany0919.insightdeal.presentation.wishlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ddaeany0919.insightdeal.presentation.wishlist.model.Period
import com.ddaeany0919.insightdeal.presentation.wishlist.PriceHistoryItem
import java.time.format.DateTimeFormatter
import androidx.compose.material.icons.automirrored.filled.ArrowBack

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistDetailScreen(
    itemId: Int,
    onBack: () -> Unit,
    viewModel: WishlistViewModel = viewModel()
) {
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    val priceHistory by viewModel.filteredPriceHistory.collectAsState()
    val isAlarmOn by viewModel.isAlarmOn.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    // 실제 아이템 불러오기
    val item = (uiState as? WishlistUiState.Success)?.items?.find { it.id == itemId }
    val timeFormatter = DateTimeFormatter.ofPattern("MM/dd HH:mm")

    BackHandler { onBack() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("상세 정보") },
                navigationIcon = {
                    IconButton(onClick = { onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(Modifier.padding(paddingValues).padding(16.dp)) {
            item?.let {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(it.keyword, style = MaterialTheme.typography.headlineSmall)
                        Text("목표가: ${it.targetPrice.toString().reversed().chunked(3).joinToString(",").reversed()}원")
                        if (it.currentLowestPrice != null) {
                            Text("최저가: ${it.currentLowestPrice.toString().reversed().chunked(3).joinToString(",").reversed()}원 (${it.currentLowestPlatform ?: "-"})")
                        }
                        it.lastChecked?.let { lastChecked ->
                            Text("마지막 체크: ${lastChecked.format(timeFormatter)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            PeriodToggleRow(selectedPeriod, onPeriodSelected = { viewModel.setPeriod(it) })
            Spacer(Modifier.height(12.dp))
            PriceChart(priceHistory)
            Spacer(Modifier.height(16.dp))
            AlarmToggle(isAlarmOn, onToggle = { viewModel.toggleAlarm(it) })
        }
    }
}

// 기간 버튼 그룹
@Composable
fun PeriodToggleRow(selectedPeriod: Period, onPeriodSelected: (Period) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Period.values().forEach { period ->
            Button(
                onClick = { onPeriodSelected(period) },
                colors = if (period == selectedPeriod)
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                else
                    ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Text(text = period.label)
            }
        }
    }
}

// 알림 토글 스위치
@Composable
fun AlarmToggle(isOn: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("가격 알림", style = MaterialTheme.typography.titleMedium)
        IconToggleButton(checked = isOn, onCheckedChange = onToggle) {
            if (isOn) {
                Icon(Icons.Filled.Notifications, contentDescription = "알림 켜짐")
            } else {
                Icon(Icons.Outlined.NotificationsOff, contentDescription = "알림 꺼짐")
            }
        }
    }
}

// 가격 히스토리 차트 (예시: 리스트 출력)
@Composable
fun PriceChart(priceHistory: List<PriceHistoryItem>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        priceHistory.forEach { item ->
            Text(text = "${item.recordedAt} - ${item.lowestPrice.toString().reversed().chunked(3).joinToString(",").reversed()}원 (${item.platform})")
        }
    }
}
