package com.ddaeany0919.insightdeal.presentation.wishlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AlarmSettings() {
    var alarmEnabled by remember { mutableStateOf(true) }
    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "알람 설정")
        Spacer(modifier = Modifier.height(8.dp))
        Switch(
            checked = alarmEnabled,
            onCheckedChange = { alarmEnabled = it }
        )
    }
}
