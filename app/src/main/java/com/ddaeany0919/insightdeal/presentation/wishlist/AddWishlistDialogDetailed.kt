package com.ddaeany0919.insightdeal.presentation.wishlist

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ddaeany0919.insightdeal.data.network.AnalyzeLinkRequest
import com.ddaeany0919.insightdeal.network.ApiService
import com.ddaeany0919.insightdeal.network.NetworkModule
import com.ddaeany0919.insightdeal.presentation.auth.AuthManager
import com.ddaeany0919.insightdeal.presentation.decodeHtmlEntities
import com.ddaeany0919.insightdeal.presentation.formatPrice
import com.ddaeany0919.insightdeal.presentation.inferMallFromUrl
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val TAG_UI = "WishlistUI"

@Composable
fun AddWishlistDialogDetailed(
    onDismiss: () -> Unit,
    onAdd: (String, Int) -> Unit,
    onAddFromLink: ((String, Int) -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "상품 링크로 추가하기",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LinkInputTab(
                onDismiss = onDismiss,
                onAddFromLink = onAddFromLink ?: { url, target ->
                    onAdd(url, target)
                }
            )
        },
        confirmButton = {},
        dismissButton = {}
    )
}

@Composable
private fun LinkInputTab(
    onDismiss: () -> Unit,
    onAddFromLink: (String, Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apiService = remember { NetworkModule.createService<ApiService>() }
    val currentUsername by AuthManager.getUsername(context).collectAsStateWithLifecycle(initialValue = "guest")

    var url by remember { mutableStateOf("") }
    var targetText by remember { mutableStateOf("") }
    var urlError by remember { mutableStateOf<String?>(null) }
    var targetError by remember { mutableStateOf<String?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var previewTitle by remember { mutableStateOf<String?>(null) }
    var previewSubtitle by remember { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = url,
            onValueChange = {
                url = it
                urlError = null
                previewTitle = null
                previewSubtitle = null
            },
            label = { Text("상품 링크") },
            placeholder = { Text("https://www.coupang.com/vp/products/...") },
            isError = urlError != null,
            supportingText = {
                urlError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error)
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
            leadingIcon = { Icon(Icons.Filled.Link, contentDescription = "링크") },
            modifier = Modifier.fillMaxWidth()
        )

        if (previewTitle != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = previewTitle.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    if (!previewSubtitle.isNullOrBlank()) {
                        Text(
                            text = previewSubtitle.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }

            OutlinedTextField(
                value = targetText,
                onValueChange = {
                    val filtered = it.filter(Char::isDigit)
                    targetText = filtered
                    targetError = null
                },
                label = { Text("목표가") },
                placeholder = { Text("원하는 가격을 입력하세요") },
                isError = targetError != null,
                supportingText = {
                    targetError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    } ?: Text("목표가 이하로 내려가면 알림을 보내드려요")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
                suffix = { Text("원") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }

            if (previewTitle == null) {
                Button(
                    onClick = {
                        val trimmedUrl = url.trim()
                        if (trimmedUrl.isBlank()) {
                            urlError = "상품 링크를 입력해 주세요"
                            return@Button
                        }
                        if (!trimmedUrl.startsWith("http://", ignoreCase = true) &&
                            !trimmedUrl.startsWith("https://", ignoreCase = true)
                        ) {
                            urlError = "http:// 또는 https://로 시작하는 주소를 입력해 주세요"
                            return@Button
                        }

                        isAnalyzing = true
                        urlError = null
                        scope.launch {
                            try {
                                val resolvedUser = currentUsername?.takeIf { it.isNotBlank() && it != "guest" } ?: "default"
                                val response = apiService.analyzeLink(
                                    AnalyzeLinkRequest(
                                        url = trimmedUrl,
                                        userId = resolvedUser
                                    )
                                )
                                val cleanName = decodeHtmlEntities(response.productName).trim()
                                previewTitle = cleanName.ifEmpty {
                                    inferMallFromUrl(trimmedUrl)?.let { "$it 상품" } ?: "링크 확인 완료"
                                }
                                val lowestPrice = response.estimatedLowestPrice
                                if (lowestPrice != null && lowestPrice > 0) {
                                    previewSubtitle = "현재 확인 가격: ${formatPrice(lowestPrice.toLong(), "KRW", cleanName)}"
                                    if (targetText.isBlank()) {
                                        targetText = lowestPrice.toString()
                                    }
                                } else {
                                    previewSubtitle = "원하는 목표가를 입력해 주세요."
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.w(TAG_UI, "Link analysis fallback triggered: ${e.message}")
                                val mallName = inferMallFromUrl(trimmedUrl)
                                previewTitle = mallName?.let { "$it 상품 링크" } ?: "상품 링크 확인 완료"
                                previewSubtitle = "자동 가격 조회가 어려운 링크예요. 목표가를 직접 입력해 주세요."
                            } finally {
                                isAnalyzing = false
                            }
                        }
                    },
                    enabled = !isAnalyzing
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("확인 중...")
                    } else {
                        Text("링크 확인")
                    }
                }
            } else {
                Button(
                    onClick = {
                        val targetPrice = targetText.toIntOrNull()
                        if (targetPrice == null || targetPrice <= 0) {
                            targetError = "올바른 목표가를 입력해 주세요"
                            return@Button
                        }
                        onAddFromLink(url.trim(), targetPrice)
                    }
                ) {
                    Text("관심 상품 등록")
                }
            }
        }
    }
}
