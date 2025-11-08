package com.ddaeany0919.insightdeal.presentation.wishlist

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.*

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.NotificationsOff
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ddaeany0919.insightdeal.presentation.wishlist.model.Period
import com.ddaeany0919.insightdeal.presentation.wishlist.model.PriceHistoryItem

@Composable
fun WishlistDetailScreen(
    viewModel: WishlistViewModel = viewModel()
) {
    val selectedPeriod by viewModel.selectedPeriod.collectAsState()
    val priceHistory by viewModel.filteredPriceHistory.collectAsState()
    val isAlarmOn by viewModel.isAlarmOn.collectAsState()

    Column(modifier = Modifier.padding(16.dp)) {
        PeriodToggleRow(selectedPeriod, onPeriodSelected = { viewModel.setPeriod(it) })
        PriceChart(priceHistory)
        Spacer(modifier = Modifier.height(16.dp))
        AlarmToggle(isAlarmOn, onToggle = { viewModel.toggleAlarm(it) })
    }
}

@Composable
fun PeriodToggleRow(selectedPeriod: Period, onPeriodSelected: (Period) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
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

@Composable
fun AlarmToggle(isOn: Boolean, onToggle: (Boolean) -> Unit) {
    IconToggleButton(checked = isOn, onCheckedChange = onToggle) {
        if (isOn) {
            Icon(Icons.Filled.Notifications, contentDescription = "알림 켜짐")
        } else {
            Icon(Icons.Outlined.NotificationsOff, contentDescription = "알림 꺼짐")
        }
    }
}

@Composable
fun PriceChart(priceHistory: List<PriceHistoryItem>) {
    Column(modifier = Modifier.fillMaxWidth()) {
        priceHistory.forEach { item ->
            Text(text = "${item.date} - ${item.lowestPrice}원 (${item.lowestPriceShopName})")
        }
    }
}
