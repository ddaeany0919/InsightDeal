package com.example.insightdeal.ui.search

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import com.example.insightdeal.utils.TrendingKeywordManager

@Composable
fun TrendingKeywordSuggestions(
    onKeywordClick: (String) -> Unit
) {
    // 동적 키워드 가져오기
    val trendingKeywords by remember {
        mutableStateOf(TrendingKeywordManager.getTrendingKeywords())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.TrendingUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "지금 인기 검색어",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFFE91E63).copy(alpha = 0.1f)
            ) {
                Text(
                    text = "실시간",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFE91E63),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.heightIn(max = 300.dp)
        ) {
            itemsIndexed(trendingKeywords) { index, keyword ->
                TrendingKeywordItem(
                    rank = index + 1,
                    keyword = keyword,
                    onClick = { onKeywordClick(keyword) }
                )
            }
        }
    }
}

@Composable
fun TrendingKeywordItem(
    rank: Int,
    keyword: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 랭킹 표시
            Surface(
                modifier = Modifier.size(24.dp),
                shape = CircleShape,
                color = when (rank) {
                    1 -> Color(0xFFFFD700) // 금색
                    2 -> Color(0xFFC0C0C0) // 은색
                    3 -> Color(0xFFCD7F32) // 동색
                    else -> MaterialTheme.colorScheme.primaryContainer
                }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = rank.toString(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (rank <= 3) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Text(
                text = keyword,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Icon(
                imageVector = Icons.Default.TrendingUp,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
