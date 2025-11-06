package com.ddaeany0919.insightdeal.presentation.wishlist

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private const val TAG_UI = "WishlistUI"

@Composable
fun AddWishlistDialogDetailed(
    onDismiss: () -> Unit,
    onAdd: (String, Int) -> Unit,
    onAddFromLink: ((String, Int) -> Unit)? = null
) {
    var tabIndex by remember { mutableStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("관심상품 추가") },
        text = {
            Column {
                if (onAddFromLink != null) {
                    TabRow(selectedTabIndex = tabIndex) {
                        Tab(selected = tabIndex == 0, onClick = { tabIndex = 0 }, text = { Text("키워드") }, icon = { Icon(Icons.Filled.Search, null) })
                        Tab(selected = tabIndex == 1, onClick = { tabIndex = 1 }, text = { Text("링크") }, icon = { Icon(Icons.Filled.Link, null) })
                    }
                    Spacer(Modifier.height(16.dp))
                }
                when (tabIndex) {
                    0 -> KeywordInputTab(onDismiss, onAdd)
                    1 -> if (onAddFromLink != null) LinkInputTab(onDismiss, onAddFromLink)
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@Composable
private fun KeywordInputTab(onDismiss: () -> Unit, onAdd: (String, Int) -> Unit) {
    var keyword by remember { mutableStateOf("") }
    var targetText by remember { mutableStateOf("") }
    var keywordError by remember { mutableStateOf<String?>(null) }
    var targetError by remember { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = keyword,
            onValueChange = { keyword = it; if (keywordError != null) keywordError = null },
            label = { Text("키워드 (예: 갤럭시 S24 128GB)") },
            isError = keywordError != null,
            supportingText = { keywordError?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next)
        )
        OutlinedTextField(
            value = targetText,
            onValueChange = { val f = it.filter(Char::isDigit); targetText = f; if (targetError != null) targetError = null },
            label = { Text("목표가 (원 단위)") },
            isError = targetError != null,
            supportingText = { targetError?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focus.clearFocus() })
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("취소") }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = {
                val k = keyword.trim(); val t = targetText.toIntOrNull(); var ok = true
                if (k.length < 2) { keywordError = "키워드는 2자 이상"; ok = false }
                if (t == null || t <= 0) { targetError = "목표가는 0보다 커야 합니다"; ok = false }
                if (!ok) { Log.d(TAG_UI, "KeywordTab 검증 실패: $k / $targetText"); return@TextButton }
                Log.d(TAG_UI, "KeywordTab 확인: $k / $t"); onAdd(k, t!!)
            }) { Text("추가하기") }
        }
    }
}

@Composable
private fun LinkInputTab(onDismiss: () -> Unit, onAddFromLink: (String, Int) -> Unit) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var targetText by remember { mutableStateOf("") }
    var urlError by remember { mutableStateOf<String?>(null) }
    var targetError by remember { mutableStateOf<String?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it; urlError = null; preview = null },
            label = { Text("상품 링크 (예: https://www.coupang.com/…)") },
            isError = urlError != null,
            supportingText = { urlError?.let { Text(it, color = MaterialTheme.colorScheme.error) } ?: preview?.let { Text(it) } },
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
            leadingIcon = { Icon(Icons.Filled.Link, null) }
        )

        if (preview != null) {
            OutlinedTextField(
                value = targetText,
                onValueChange = { val f = it.filter(Char::isDigit); targetText = f; targetError = null },
                label = { Text("목표가 (원 단위)") },
                isError = targetError != null,
                supportingText = { targetError?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() })
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text("취소") }
            Spacer(Modifier.width(8.dp))
            if (preview == null) {
                Button(onClick = {
                    if (url.isBlank() || !url.startsWith("http")) { urlError = "올바른 URL을 입력해 주세요"; return@Button }
                    isAnalyzing = true
                    scope.launch {
                        try {
                            // 실제 백엔드 연동
                            // val res = api.analyzeLink(url)
                            // preview = "${res.extracted_info.product_name} | 최저가 ${res.lowest_total_price?.let { "%,d원".format(it) } ?: "-"}"
                            preview = "분석을 완료했습니다"
                        } finally { isAnalyzing = false }
                    }
                }, enabled = !isAnalyzing) {
                    if (isAnalyzing) { CircularProgressIndicator(Modifier.size(16.dp)) ; Spacer(Modifier.width(8.dp)); Text("분석중…") } else { Text("분석하기") }
                }
            } else {
                Button(onClick = {
                    val t = targetText.toIntOrNull()
                    if (t == null || t <= 0) { targetError = "목표가를 입력해 주세요"; return@Button }
                    onAddFromLink(url, t)
                }) { Text("위시리스트 추가") }
            }
        }
    }
}
