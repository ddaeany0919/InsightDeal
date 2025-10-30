package com.ddaeany0919.insightdeal.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ddaeany0919.insightdeal.data.theme.ThemeManager
import com.ddaeany0919.insightdeal.data.theme.ThemePreferences
import kotlinx.coroutines.launch

@Composable
fun ThemeSettingsScreen()
{
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val tm = remember { ThemeManager.getInstance(ctx) }
    val mode by tm.modeFlow.collectAsState(initial = ThemePreferences.Mode.SYSTEM)
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text(text = "테마 설정", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        ThemeOptionRow("시스템 기본", ThemePreferences.Mode.SYSTEM, mode) { scope.launch { tm.updateMode(it) } }
        ThemeOptionRow("라이트 모드", ThemePreferences.Mode.LIGHT, mode) { scope.launch { tm.updateMode(it) } }
        ThemeOptionRow("다크 모드", ThemePreferences.Mode.DARK, mode) { scope.launch { tm.updateMode(it) } }
        ThemeOptionRow("블랙(AMOLED)", ThemePreferences.Mode.AMOLED, mode) { scope.launch { tm.updateMode(it) } }
    }
}

@Composable
private fun ThemeOptionRow(
    label: String,
    value: ThemePreferences.Mode,
    current: ThemePreferences.Mode,
    onSelect: (ThemePreferences.Mode) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = current == value, onClick = { onSelect(value) })
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
