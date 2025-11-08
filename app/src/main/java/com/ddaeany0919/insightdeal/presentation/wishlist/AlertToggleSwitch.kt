package com.ddaeany0919.insightdeal.presentation.wishlist

import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

@Composable
fun AlertToggleSwitch(isChecked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Switch(
        checked = isChecked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
    )
}
