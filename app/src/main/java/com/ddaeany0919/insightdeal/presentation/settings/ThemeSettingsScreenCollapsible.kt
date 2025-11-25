package com.ddaeany0919.insightdeal.presentation.settings

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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ddaeany0919.insightdeal.presentation.theme.ThemeManager
import com.ddaeany0919.insightdeal.presentation.theme.ThemeMode
import kotlinx.coroutines.launch

@Composable
fun ThemeSettingsScreenCollapsible(tm: ThemeManager = ThemeManager.getInstance(LocalContext.current)) {
    rememberCoroutineScope()
    var expanded by remember { mutableStateOf(true) }
    val mode by tm.themeMode.collectAsState()

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
                    onClick = { tm.setThemeMode(ThemeMode.LIGHT) },
                    label = { Text("라이트") },
                    leadingIcon = { if (mode == ThemeMode.LIGHT) Icon(Icons.Default.Palette, null) }
                )
                AssistChip(
                    onClick = { tm.setThemeMode(ThemeMode.DARK) },
                    label = { Text("다크") },
                    leadingIcon = { if (mode == ThemeMode.DARK) Icon(Icons.Default.Palette, null) }
                )
                AssistChip(
                    onClick = { tm.setThemeMode(ThemeMode.AMOLED) },
                    label = { Text("AMOLED") },
                    leadingIcon = { if (mode == ThemeMode.AMOLED) Icon(Icons.Default.Palette, null) }
                )
                AssistChip(
                    onClick = { tm.setThemeMode(ThemeMode.SYSTEM) },
                    label = { Text("시스템") },
                    leadingIcon = { if (mode == ThemeMode.SYSTEM) Icon(Icons.Default.Palette, null) }
                )
            }
        }
    }
}
