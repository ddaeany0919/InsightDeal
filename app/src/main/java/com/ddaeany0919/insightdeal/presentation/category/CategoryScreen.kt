package com.ddaeany0919.insightdeal.presentation.category

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController

@Immutable
private data class CategorySpec(
    val routeName: String,
    val displayTitle: String,
    val icon: ImageVector,
    val tintColor: Color
)

// [ui-tokens] 15개 알록달록 무지개 색상 대신 5대 시맨틱 톤(핫딜 오렌지 / 브랜드 블루 / 에메랄드 그린 / 웜 앰버 / 슬레이트)으로 정제하여 시각적 통일성 강화
private val ToneHotOrange = Color(0xFFFF5A26)
private val ToneBrandBlue = Color(0xFF3182F6)
private val ToneEmerald = Color(0xFF0D9488)
private val ToneWarmAmber = Color(0xFFD97706)
private val ToneIndigo = Color(0xFF4F46E5)
private val ToneRose = Color(0xFFE11D48)
private val ToneSlate = Color(0xFF4E5968)

private val CATEGORIES_LIST = listOf(
    CategorySpec("핫딜모음", "핫딜모음", Icons.Outlined.LocalFireDepartment, ToneHotOrange),
    CategorySpec("음식", "식품", Icons.Outlined.Restaurant, ToneWarmAmber),
    CategorySpec("생활용품", "생활용품", Icons.Outlined.Inventory2, ToneEmerald),
    CategorySpec("PC용품", "PC·하드웨어", Icons.Outlined.Computer, ToneBrandBlue),
    CategorySpec("가전제품", "가전·디지털", Icons.Outlined.Tv, ToneIndigo),
    CategorySpec("SW/게임", "게임·SW", Icons.Outlined.SportsEsports, ToneIndigo),
    CategorySpec("패션", "패션·의류", Icons.Outlined.Checkroom, ToneRose),
    CategorySpec("뷰티", "뷰티", Icons.Outlined.AutoAwesome, ToneRose),
    CategorySpec("모바일/기프티콘", "모바일·쿠폰", Icons.Outlined.Smartphone, ToneBrandBlue),
    CategorySpec("상품권", "상품권", Icons.Outlined.ConfirmationNumber, ToneWarmAmber),
    CategorySpec("육아/유아용품", "출산·육아", Icons.Outlined.ChildCare, ToneEmerald),
    CategorySpec("여행.해외핫딜", "해외직구·여행", Icons.Outlined.FlightTakeoff, ToneBrandBlue),
    CategorySpec("적립", "포인트·적립", Icons.Outlined.Savings, ToneEmerald),
    CategorySpec("이벤트", "이벤트", Icons.Outlined.Redeem, ToneHotOrange),
    CategorySpec("기타", "기타", Icons.Outlined.MoreHoriz, ToneSlate)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryScreen(navController: NavController? = null) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "카테고리",
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                        letterSpacing = (-0.4).sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            items(
                items = CATEGORIES_LIST,
                key = { it.routeName },
                contentType = { "category_grid_item" }
            ) { spec ->
                CategoryCard(
                    spec = spec,
                    onClick = {
                        val encodedName = java.net.URLEncoder.encode(spec.routeName, "UTF-8")
                        navController?.navigate("category_detail/$encodedName")
                    }
                )
            }
        }
    }
}

@Composable
private fun CategoryCard(
    spec: CategorySpec,
    onClick: () -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "categoryCardScale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(92.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(14.dp),
        onClick = onClick,
        interactionSource = interactionSource
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(spec.tintColor.copy(alpha = if (isDark) 0.16f else 0.09f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = spec.icon,
                    contentDescription = spec.displayTitle,
                    tint = spec.tintColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = spec.displayTitle,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = (-0.3).sp,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
