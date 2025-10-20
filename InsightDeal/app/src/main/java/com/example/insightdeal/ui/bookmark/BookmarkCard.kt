package com.example.insightdeal.ui.bookmark

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import coil.compose.*
import coil.request.*
import com.example.insightdeal.model.BookmarkItem
import com.example.insightdeal.network.ApiClient
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun BookmarkCard(
    bookmark: BookmarkItem,
    onDealClick: () -> Unit,
    onRemoveBookmark: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onDealClick)
            .animateContentSize(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 이미지 섹션
            Card(
                modifier = Modifier.size(80.dp),
                shape = RoundedCornerShape(12.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                if (bookmark.dealType == "이벤트") {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFFE8F5E9),
                                        Color(0xFFDCEDC8)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stars,
                            contentDescription = "이벤트",
                            modifier = Modifier.size(32.dp),
                            tint = Color(0xFF4CAF50)
                        )
                    }
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(ApiClient.BASE_URL.removeSuffix("/") + bookmark.imageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = bookmark.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // 콘텐츠 섹션
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // 상단: 커뮤니티 + 북마크 삭제 버튼
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = getCommunityColor(bookmark.community).copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = bookmark.community,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = getCommunityColor(bookmark.community),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    IconButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.BookmarkRemove,
                            contentDescription = "북마크 삭제",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 제목
                Text(
                    text = bookmark.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    lineHeight = 22.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 쇼핑몰 및 배송비
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = bookmark.shopName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when {
                            bookmark.shippingFee.contains("무료") -> Color(0xFF4CAF50).copy(alpha = 0.15f)
                            bookmark.shippingFee == "정보 없음" -> MaterialTheme.colorScheme.surfaceVariant
                            else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        }
                    ) {
                        Text(
                            text = when {
                                bookmark.shippingFee.contains("무료") -> "무료배송"
                                bookmark.shippingFee == "정보 없음" -> "배송비 미표시"
                                else -> bookmark.shippingFee
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = when {
                                bookmark.shippingFee.contains("무료") -> Color(0xFF4CAF50)
                                bookmark.shippingFee == "정보 없음" -> MaterialTheme.colorScheme.onSurfaceVariant
                                else -> MaterialTheme.colorScheme.error
                            },
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 가격 및 북마크 시간
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text(
                        text = if (bookmark.price == "정보 없음") "가격 미표시" else bookmark.price,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = if (bookmark.price == "정보 없음")
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        else
                            MaterialTheme.colorScheme.primary
                    )

                    Text(
                        text = formatBookmarkTime(bookmark.bookmarkedAt),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 종료된 딜 오버레이
        if (bookmark.isClosed) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.White.copy(alpha = 0.9f)
                ) {
                    Text(
                        text = "종료된 딜",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }

    // 삭제 확인 다이얼로그
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("북마크 삭제") },
            text = { Text("이 딜을 북마크에서 제거하시겠습니까?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveBookmark()
                        showDeleteConfirm = false
                    }
                ) {
                    Text("삭제", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("취소")
                }
            }
        )
    }
}

@Composable
private fun getCommunityColor(community: String): Color {
    return when (community) {
        "뽐뿌", "뽐뿌해외", "알리뽐뿌" -> Color(0xFFE53935)
        "펨코" -> Color(0xFF43A047)
        "클리앙" -> Color(0xFF1976D2)
        "퀘이사존" -> Color(0xFFFF6F00)
        "빠삭", "빠삭해외" -> Color(0xFF7B1FA2)
        "루리웹" -> Color(0xFF00ACC1)
        else -> Color(0xFF757575)
    }
}

private fun formatBookmarkTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "방금 전"
        diff < 3600_000 -> "${diff / 60_000}분 전"
        diff < 86400_000 -> "${diff / 3600_000}시간 전"
        diff < 604800_000 -> "${diff / 86400_000}일 전"
        else -> {
            val sdf = SimpleDateFormat("MM/dd", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}
