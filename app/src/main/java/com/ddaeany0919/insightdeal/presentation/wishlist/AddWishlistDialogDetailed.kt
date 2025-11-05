package com.ddaeany0919.insightdeal.presentation.wishlist

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private const val TAG_UI = "WishlistUI"

@Composable
fun AddWishlistDialogDetailed(
    onDismiss: () -> Unit,
    onAdd: (String, Int) -> Unit
) {
    var keyword by remember { mutableStateOf("") }
    var targetText by remember { mutableStateOf("") }
    var keywordError by remember { mutableStateOf<String?>(null) }
    var targetError by remember { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("관심상품 추가") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = {
                        keyword = it
                        if (keywordError != null) keywordError = null
                    },
                    label = { Text("키워드 (2자 이상)") },
                    isError = keywordError != null,
                    supportingText = { keywordError?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                    singleLine = true,
                    keyboardOptions = androidx.compose.ui.text.input.KeyboardOptions.Default.copy(imeAction = ImeAction.Next)
                )
                OutlinedTextField(
                    value = targetText,
                    onValueChange = {
                        // 숫자만 허용
                        val filtered = it.filter { ch -> ch.isDigit() }
                        targetText = filtered
                        if (targetError != null) targetError = null
                    },
                    label = { Text("목표가 (원)") },
                    isError = targetError != null,
                    supportingText = { targetError?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                    singleLine = true,
                    keyboardOptions = androidx.compose.ui.text.input.KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = androidx.compose.ui.text.input.KeyboardActions(onDone = { focus.clearFocus() })
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // 검증
                val k = keyword.trim()
                val t = targetText.toIntOrNull()
                var ok = true
                if (k.length < 2) { keywordError = "키워드는 2자 이상"; ok = false }
                if (t == null || t <= 0) { targetError = "목표가는 0보다 커야 합니다"; ok = false }
                if (!ok) {
                    Log.d(TAG_UI, "AddDialog 검증 실패: keyword='$k' target='$targetText'")
                    return@TextButton
                }
                Log.d(TAG_UI, "AddDialog 확인: keyword='$k' target=$t")
                onAdd(k, t)
            }) { Text("추가") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } }
    )
}
