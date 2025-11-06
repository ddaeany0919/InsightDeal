package com.ddaeany0919.insightdeal.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ddaeany0919.insightdeal.data.theme.ThemeManager
import com.ddaeany0919.insightdeal.data.theme.ThemePreferences

@Composable
fun ThemeSettingsScreenCollapsible(tm: ThemeManager = ThemeManager.getInstance(LocalContext.current)) {
    var expanded by remember { mutableStateOf(true) }
    val mode by tm.modeFlow.collectAsState(initial = ThemePreferences.Mode.SYSTEM)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text(text = "테마 설정", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { expanded = !expanded }) {
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = null)
            }
        }

        AnimatedVisibility(visible = expanded, enter = expandVertically(), exit = shrinkVertically()) {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                AssistChip(
                    onClick = { tm.setMode(ThemePreferences.Mode.LIGHT) },
                    label = { Text("라이트") },
                    leadingIcon = { if (mode == ThemePreferences.Mode.LIGHT) Icon(Icons.Default.Palette, null) }
                )
                AssistChip(
                    onClick = { tm.setMode(ThemePreferences.Mode.DARK) },
                    label = { Text("다크") },
                    leadingIcon = { if (mode == ThemePreferences.Mode.DARK) Icon(Icons.Default.Palette, null) }
                )
                AssistChip(
                    onClick = { tm.setMode(ThemePreferences.Mode.AMOLED) },
                    label = { Text("AMOLED") },
                    leadingIcon = { if (mode == ThemePreferences.Mode.AMOLED) Icon(Icons.Default.Palette, null) }
                )
                AssistChip(
                    onClick = { tm.setMode(ThemePreferences.Mode.SYSTEM) },
                    label = { Text("시스템") },
                    leadingIcon = { if (mode == ThemePreferences.Mode.SYSTEM) Icon(Icons.Default.Palette, null) }
                )
            }
        }
    }
}
