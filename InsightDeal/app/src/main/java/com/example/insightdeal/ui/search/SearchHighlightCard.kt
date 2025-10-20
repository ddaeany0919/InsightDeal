package com.example.insightdeal.ui.search

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import coil.compose.*
import coil.request.*
import com.example.insightdeal.model.*
import com.example.insightdeal.network.*

@Composable
fun SearchHighlightedDealCard(
    deal: DealItem,
    searchQuery: String,
    onClick: () -> Unit
) {
    var isBookmarked by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
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
                shape = RoundedCornerShape(8.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(ApiClient.BASE_URL.removeSuffix("/") + deal.imageUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = deal.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // 콘텐츠 섹션
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                // 쇼핑몰 태그
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ) {
                    Text(
                        text = deal.shopName,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // 검색어 하이라이트된 제목
                HighlightedText(
                    text = deal.title,
                    searchQuery = searchQuery,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 가격 정보
                Text(
                    text = if (deal.price == "정보 없음") "가격 문의" else deal.price,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.error
                )

                Spacer(modifier = Modifier.height(4.dp))

                // 배송비 및 북마크
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = when {
                            deal.shippingFee.contains("무료") -> "무료배송"
                            deal.shippingFee == "정보 없음" -> "배송비 별도"
                            else -> deal.shippingFee
                        },
                        fontSize = 12.sp,
                        color = if (deal.shippingFee.contains("무료"))
                            Color(0xFF4CAF50)
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    IconButton(
                        onClick = { isBookmarked = !isBookmarked },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "북마크",
                            tint = if (isBookmarked) Color(0xFFFF9800) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HighlightedText(
    text: String,
    searchQuery: String,
    modifier: Modifier = Modifier
) {
    val annotatedString = buildAnnotatedString {
        if (searchQuery.isNotBlank() && text.contains(searchQuery, ignoreCase = true)) {
            val startIndex = text.indexOf(searchQuery, ignoreCase = true)
            val endIndex = startIndex + searchQuery.length

            // 검색어 이전 텍스트
            append(text.substring(0, startIndex))

            // 검색어 하이라이트
            withStyle(
                style = SpanStyle(
                    background = Color(0xFFFFEB3B).copy(alpha = 0.4f), // 노란색 하이라이트
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            ) {
                append(text.substring(startIndex, endIndex))
            }

            // 검색어 이후 텍스트
            append(text.substring(endIndex))
        } else {
            append(text)
        }
    }


    Text(
        text = annotatedString,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}
