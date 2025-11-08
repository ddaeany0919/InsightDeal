// 알람 고급 설정 UI 마크업 준비
// 토글 버튼 포함 기본 알람 동작 유지, 상태 관리 확장 예정

import androidx.compose.foundation.layout.Column
import androidx.compose.material.Switch
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@Composable
fun AlarmSettings() {
    var alarmEnabled by remember { mutableStateOf(true) }

    Column {
        Text(text = "알람 설정")
        Switch(
            checked = alarmEnabled,
            onCheckedChange = { alarmEnabled = it }
        )
    }
}
