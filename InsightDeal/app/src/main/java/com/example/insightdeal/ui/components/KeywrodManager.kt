package com.example.insightdeal.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeywordManagementDialog(
    onDismiss: () -> Unit
) {
    var excludeKeywords by remember { mutableStateOf(listOf("광고", "스팸", "종료")) }
    var alertKeywords by remember { mutableStateOf(listOf("갤럭시", "아이폰", "무료")) }
    var newKeyword by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column {
                // 헤더
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "키워드 관리",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "닫기")
                    }
                }

                // 탭
                TabRow(
                    selectedTabIndex = selectedTab,
                    modifier = Modifier.padding(horizontal = 20.dp)
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("제외 키워드") },
                        icon = { Icon(Icons.Default.Block, contentDescription = null) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("알림 키워드") },
                        icon = { Icon(Icons.Default.Notifications, contentDescription = null) }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 키워드 입력
                OutlinedTextField(
                    value = newKeyword,
                    onValueChange = { newKeyword = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    placeholder = { Text("새 키워드 입력") },
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                if (newKeyword.isNotBlank()) {
                                    if (selectedTab == 0) {
                                        excludeKeywords = excludeKeywords + newKeyword
                                    } else {
                                        alertKeywords = alertKeywords + newKeyword
                                    }
                                    newKeyword = ""
                                }
                            },
                            enabled = newKeyword.isNotBlank()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "추가")
                        }
                    },
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 키워드 리스트
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val currentKeywords = if (selectedTab == 0) excludeKeywords else alertKeywords
                    val keywordColor = if (selectedTab == 0) Color(0xFFE57373) else Color(0xFF81C784)

                    items(currentKeywords) { keyword ->
                        KeywordChip(
                            keyword = keyword,
                            color = keywordColor,
                            onDelete = {
                                if (selectedTab == 0) {
                                    excludeKeywords = excludeKeywords - keyword
                                } else {
                                    alertKeywords = alertKeywords - keyword
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun KeywordChip(
    keyword: String,
    color: Color,
    onDelete: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = color.copy(alpha = 0.15f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = keyword,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = color
            )

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "삭제",
                    tint = color,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
