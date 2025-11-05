package com.ddaeany0919.insightdeal.settings

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ddaeany0919.insightdeal.data.theme.ThemeManager
import com.ddaeany0919.insightdeal.data.theme.ThemePreferences
import kotlinx.coroutines.launch

private const val TAG = "MainActivity"

@Composable
fun ThemeSettingsScreen() {
    val ctx = LocalContext.current
    val tm = remember { ThemeManager.getInstance(ctx) }
    val mode by tm.modeFlow.collectAsState(initial = ThemePreferences.Mode.SYSTEM)
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text(text = "설정", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))

        // User ID 설정 섹션
        Text("사용자 설정", style = MaterialTheme.typography.titleMedium)
        var currentUserId by remember {
            val prefs = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)
            mutableStateOf(prefs.getString("user_id", "guest") ?: "guest")
        }
        OutlinedTextField(
            value = currentUserId,
            onValueChange = { currentUserId = it },
            label = { Text("User ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                ctx.getSharedPreferences("app", Context.MODE_PRIVATE)
                    .edit().putString("user_id", currentUserId).apply()
                Log.d(TAG, "Saved user_id -> $currentUserId")
            }) { Text("저장") }
            TextButton(onClick = {
                val prefs = ctx.getSharedPreferences("app", Context.MODE_PRIVATE)
                currentUserId = prefs.getString("user_id", "guest") ?: "guest"
            }) { Text("불러오기") }
        }
        Text(
            text = "서버에 등록된 user_id를 입력하고 저장하세요.",
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Start
        )

        Divider(Modifier.padding(vertical = 16.dp))

        Text(text = "테마 설정", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(12 dp))
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
