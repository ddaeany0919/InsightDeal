package com.ddaeany0919.insightdeal.presentation.wishlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ddaeany0919.insightdeal.presentation.wishlist.model.Period
import com.ddaeany0919.insightdeal.presentation.wishlist.PriceHistoryItem
import java.time.format.DateTimeFormatter

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
                            Text("최저가: ${it.currentLowestPrice.toString().reversed().chunked(3).joinToString(",").reversed()}원 (${it.currentLowestPlatform})")
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
                Spacer(Modifier.height(16.dp))
            } ?: Box(
                modifier = Modifier.fillMaxWidth().height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("상품 데이터를 불러올 수 없습니다.", color = MaterialTheme.colorScheme.error)
                }
            }

            PeriodToggleRow(selectedPeriod, onPeriodSelected = { viewModel.setPeriod(it) })
            Spacer(Modifier.height(12.dp))
            PriceChart(priceHistory)
            Spacer(Modifier.height(16.dp))
            AlarmToggle(isAlarmOn, onToggle = { viewModel.toggleAlarm(it) })
        }
    }
}

@Composable
fun PeriodToggleRow(selectedPeriod: Period, onPeriodSelected: (Period) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        Period.values().forEach { period ->
            FilterChip(
                selected = period == selectedPeriod,
                onClick = { onPeriodSelected(period) },
                label = { Text(period.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}

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

@Composable
fun PriceChart(priceHistory: List<PriceHistoryItem>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
            Text("가격 변동 추이", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            
            if(priceHistory.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("해당 기간 가격 이력이 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                priceHistory.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(item.recordedAt.format(DateTimeFormatter.ofPattern("MM/dd HH:mm")), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${item.lowestPrice.toString().reversed().chunked(3).joinToString(",").reversed()}원",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}