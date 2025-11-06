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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private const val TAG_UI = "WishlistUI"

/**
 * Link-only product addition dialog (Phase 1 simplification)
 * Removed keyword tab for focused user experience
 */
@Composable
fun AddWishlistDialogDetailed(
    onDismiss: () -> Unit,
    onAdd: (String, Int) -> Unit,
    onAddFromLink: ((String, Int) -> Unit)? = null
) {
    Log.d(TAG_UI, "AddWishlistDialogDetailed: link-only mode, onAddFromLink=${onAddFromLink != null}")
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("상품 링크로 추가하기") },
        text = {
            // Direct link input without tabs for simplified UX
            LinkInputTab(
                onDismiss = onDismiss,
                onAddFromLink = onAddFromLink ?: { url, target -> 
                    Log.d(TAG_UI, "Fallback to keyword-style add: $url, $target")
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
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var targetText by remember { mutableStateOf("") }
    var urlError by remember { mutableStateOf<String?>(null) }
    var targetError by remember { mutableStateOf<String?>(null) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<String?>(null) }
    val focus = LocalFocusManager.current

    Log.d(TAG_UI, "LinkInputTab: rendered, url='$url', preview=$preview")

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Product link input
        OutlinedTextField(
            value = url,
            onValueChange = { 
                url = it
                urlError = null
                preview = null
                Log.d(TAG_UI, "URL changed: '$it'")
            },
            label = { Text("상품 링크") },
            placeholder = { Text("https://www.coupang.com/vp/products/...") },
            isError = urlError != null,
            supportingText = { 
                urlError?.let { 
                    Text(it, color = MaterialTheme.colorScheme.error) 
                } ?: preview?.let { 
                    Text(it, color = MaterialTheme.colorScheme.primary) 
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next),
            leadingIcon = { Icon(Icons.Filled.Link, contentDescription = "링크") },
            modifier = Modifier.fillMaxWidth()
        )

        // Analysis result preview card
        if (preview != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "상품 분석 완료",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = preview!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            
            // Target price input (only show after analysis)
            OutlinedTextField(
                value = targetText,
                onValueChange = { 
                    val filtered = it.filter(Char::isDigit)
                    targetText = filtered
                    targetError = null
                    Log.d(TAG_UI, "Target price changed: '$filtered'")
                },
                label = { Text("목표가") },
                placeholder = { Text("원하는 가격을 입력하세요") },
                isError = targetError != null,
                supportingText = { 
                    targetError?.let { 
                        Text(it, color = MaterialTheme.colorScheme.error) 
                    } ?: Text("목표가 이하로 떨어지면 알림드립니다") 
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

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            TextButton(onClick = {
                Log.d(TAG_UI, "LinkInputTab: Cancel clicked")
                onDismiss()
            }) {
                Text("취소")
            }
            
            if (preview == null) {
                // Analysis button
                Button(
                    onClick = {
                        Log.d(TAG_UI, "LinkInputTab: Analyze clicked for URL='$url'")
                        
                        if (url.isBlank()) {
                            urlError = "링크를 입력해 주세요"
                            return@Button
                        }
                        
                        if (!url.startsWith("http")) {
                            urlError = "올바른 URL을 입력해 주세요 (http:// 또는 https://)"
                            return@Button
                        }
                        
                        // Validate supported shopping malls
                        val supportedSites = listOf("coupang.com", "11st.co.kr", "shopping.naver.com")
                        if (!supportedSites.any { url.contains(it, ignoreCase = true) }) {
                            urlError = "현재 쿠팡, 11번가, 네이버쇼핑만 지원됩니다"
                            return@Button
                        }
                        
                        isAnalyzing = true
                        scope.launch {
                            try {
                                Log.d(TAG_UI, "Starting analysis for: $url")
                                // TODO: Replace with actual backend API call
                                // val response = repository.analyzeLink(url)
                                // preview = "${response.productName} | 예상 최저가: ${response.lowestPrice}원"
                                
                                // Temporary mock response
                                kotlinx.coroutines.delay(2000) // Simulate API delay
                                preview = "상품 분석이 완료되었습니다. 목표가를 설정해 주세요."
                                Log.d(TAG_UI, "Analysis completed, preview set")
                            } catch (e: Exception) {
                                Log.e(TAG_UI, "Analysis failed", e)
                                urlError = "분석에 실패했습니다. 다시 시도해 주세요."
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
                        Text("분석 중...")
                    } else {
                        Text("분석하기")
                    }
                }
            } else {
                // Add to wishlist button
                Button(
                    onClick = {
                        Log.d(TAG_UI, "LinkInputTab: Add to wishlist clicked, target='$targetText'")
                        
                        val targetPrice = targetText.toIntOrNull()
                        if (targetPrice == null || targetPrice <= 0) {
                            targetError = "올바른 목표가를 입력해 주세요"
                            return@Button
                        }
                        
                        Log.d(TAG_UI, "Adding to wishlist: url='$url', target=$targetPrice")
                        onAddFromLink(url, targetPrice)
                    }
                ) {
                    Text("위시리스트에 추가")
                }
            }
        }
    }
}