package com.ddaeany0919.insightdeal.presentation.category

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

@Composable
fun getCategoryGradient(categoryName: String): Brush {
    val startColor = when (categoryName) {
        "핫딜모음" -> Color(0xFFFFEAEA)
        "음식" -> Color(0xFFFFF3E0)
        "SW/게임" -> Color(0xFFEDE7F6)
        "PC제품" -> Color(0xFFE3F2FD)
        "가전제품" -> Color(0xFFE0F7FA)
        "생활용품" -> Color(0xFFF5F5F5)
        "의류" -> Color(0xFFFCE4EC)
        "화장품" -> Color(0xFFFCE4EC)
        "모바일/기프티콘" -> Color(0xFFE8F5E9)
        "상품권" -> Color(0xFFFFFDE7)
        "패키지/이용권" -> Color(0xFFF3E5F5)
        "여행.해외핫딜" -> Color(0xFFE1F5FE)
        "적립" -> Color(0xFFFFFDE7)
        "이벤트" -> Color(0xFFFDF2E9)
        else -> Color(0xFFEFEBE9)
    }
    
    val endColor = when (categoryName) {
        "핫딜모음" -> Color(0xFFFFC5C5)
        "음식" -> Color(0xFFFFCC80)
        "SW/게임" -> Color(0xFFD1C4E9)
        "PC제품" -> Color(0xFF90CAF9)
        "가전제품" -> Color(0xFF80DEEA)
        "생활용품" -> Color(0xFFD6D6D6)
        "의류" -> Color(0xFFF48FB1)
        "화장품" -> Color(0xFFEF9A9A)
        "모바일/기프티콘" -> Color(0xFFA5D6A7)
        "상품권" -> Color(0xFFFFF59D)
        "패키지/이용권" -> Color(0xFFCE93D8)
        "여행.해외핫딜" -> Color(0xFF81D4FA)
        "적립" -> Color(0xFFFFD54F)
        "이벤트" -> Color(0xFFF5B041)
        else -> Color(0xFFD7CCC8)
    }
    
    return Brush.linearGradient(listOf(startColor, endColor))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(navController: NavController? = null) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("카테고리", fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        val categories = listOf(
            "핫딜모음" to "🔥",
            "음식" to "🍔",
            "SW/게임" to "🎮",
            "PC제품" to "💻",
            "가전제품" to "📺",
            "생활용품" to "🧻",
            "의류" to "👕",
            "화장품" to "💄",
            "모바일/기프티콘" to "📱",
            "상품권" to "💳",
            "패키지/이용권" to "🎟",
            "여행.해외핫딜" to "✈️",
            "적립" to "💰",
            "이벤트" to "🎉",
            "기타" to "📦"
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            items(categories) { (name, emoji) ->
                CategoryCard(
                    name = name,
                    emoji = emoji,
                    onClick = {
                        val encodedName = java.net.URLEncoder.encode(name, "UTF-8")
                        navController?.navigate("category_detail/$encodedName")
                    }
                )
            }
        }
    }
}

@Composable
fun CategoryCard(
    name: String,
    emoji: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    // ✨ 프리미엄 마이크로 스케일 인터랙션 효과 (Spring Animation)
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "scale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.02f)
            .scale(scale)
            .shadow(
                elevation = if (isPressed) 1.dp else 4.dp,
                shape = RoundedCornerShape(20.dp),
                clip = false
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(20.dp),
        onClick = onClick,
        interactionSource = interactionSource
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // ✨ 입체적인 네오모피즘 스타일 그라데이션 아이콘 링
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .shadow(
                        elevation = 4.dp,
                        shape = CircleShape,
                        ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                    .clip(CircleShape)
                    .background(getCategoryGradient(name)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = emoji,
                    fontSize = 26.sp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = name,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.3).sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
