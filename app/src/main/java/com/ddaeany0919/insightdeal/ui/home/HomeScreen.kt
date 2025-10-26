package com.ddaeany0919.insightdeal

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.*
import androidx.compose.ui.text.style.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ddaeany0919.insightdeal.models.DealItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    val viewModel: HomeViewModel = viewModel()
    val deals by viewModel.deals.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // ✅ 상단바 (타이밍 앱 스타일)
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "🔥 InsightDeal",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        modifier = Modifier.size(32.dp, 18.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFFF3B30)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "LIVE",
                                fontSize = 10.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
            actions = {
                IconButton(onClick = { navController.navigate("advanced_search") }) {
                    Icon(Icons.Default.Search, "검색")
                }
                IconButton(onClick = { navController.navigate("bookmarks") }) {
                    Icon(Icons.Default.Bookmark, "북마크")
                }
                IconButton(onClick = { navController.navigate("theme_settings") }) {
                    Icon(Icons.Default.Settings, "설정")
                }
            }
        )

        // ✅ 카테고리 탭
        LazyRow(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val categories = listOf(
                "전체" to "🔥", "전자기기" to "📱", "패션뷰티" to "💄",
                "생활용품" to "🏠", "식품" to "🍎", "게임" to "🎮"
            )

            items(categories.size) { index ->
                val (category, emoji) = categories[index]
                val isSelected = selectedCategory == category

                FilterChip(
                    onClick = { viewModel.selectCategory(category) },
                    label = { Text("$emoji $category") },
                    selected = isSelected
                )
            }
        }

        // ✅ 딜 피드
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(deals) { deal ->
                DealCardComposable(
                    deal = deal,
                    onClick = { navController.navigate("price_graph/${deal.id}") }
                )
            }
        }
    }
}

@Composable
fun DealCardComposable(
    deal: DealItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column {
            // 이미지
            Box {
                AsyncImage(
                    model = deal.imageUrl ?: "https://via.placeholder.com/400x225/E0E0E0/808080?text=No+Image",
                    contentDescription = deal.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                    contentScale = ContentScale.Crop
                )

                // 할인율 배지
                deal.discountRate?.let { rate ->
                    if (rate > 0) {
                        Surface(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp),
                            shape = CircleShape,
                            color = Color(0xFFFF3B30)
                        ) {
                            Text(
                                text = "-${rate}%",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // HOT 배지
                if ((deal.discountRate ?: 0) >= 30) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFFF6B35)
                    ) {
                        Text(
                            text = "🔥 HOT",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 내용
            Column(modifier = Modifier.padding(16.dp)) {
                // 제목
                Text(
                    text = deal.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                // 가격
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${String.format("%,d", deal.price)}원",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    deal.originalPrice?.let { original ->
                        if (original > deal.price) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${String.format("%,d", original)}원",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.outline,
                                textDecoration = TextDecoration.LineThrough
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 사이트 정보
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = deal.siteName,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }

                    Text(
                        text = "👀 ${deal.viewCount} 👍 ${deal.likeCount}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 액션 버튼
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { /* TODO: 북마크 */ },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.FavoriteBorder, null, Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("북마크", fontSize = 12.sp)
                    }

                    Button(
                        onClick = { /* TODO: deal.url 연결 */ },
                        modifier = Modifier.weight(1.5f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF6B35)
                        )
                    ) {
                        Icon(Icons.Default.ShoppingCart, null, Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("구매하기", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}
