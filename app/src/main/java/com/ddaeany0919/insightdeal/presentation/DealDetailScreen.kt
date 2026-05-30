package com.ddaeany0919.insightdeal.presentation

import com.ddaeany0919.insightdeal.presentation.formatPrice
import androidx.compose.animation.core.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.composed
import coil.compose.AsyncImage
import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.models.PriceHistoryPoint
import com.ddaeany0919.insightdeal.models.MallPrice
import java.util.Locale

import com.ddaeany0919.insightdeal.presentation.wishlist.WishlistViewModel
import com.ddaeany0919.insightdeal.presentation.wishlist.WishlistUiState
import com.ddaeany0919.insightdeal.network.ApiDealVotes

/**
 * 사이트 칩의 메타 정보를 담는 고성능 정적 데이터 클래스
 */
private data class SiteChipInfo(
    val name: String,
    val bgColor: Color,
    val textColor: Color
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DealDetailRoute(
    dealId: Int,
    viewModel: DealDetailViewModel,
    wishlistViewModel: WishlistViewModel,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onNavigateToCommunity: () -> Unit,
    onNavigateToWatchlist: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val wishlistState by wishlistViewModel.uiState.collectAsState()
    
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(dealId) {
        viewModel.loadDealData(dealId)
    }

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    if (uiState.error != null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = uiState.error ?: "알 수 없는 에러가 발생했습니다.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.titleMedium
            )
        }
        return
    }

    val deal = uiState.deal
    if (deal == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("데이터를 찾을 수 없습니다.", style = MaterialTheme.typography.titleMedium)
        }
        return
    }

    val localContext = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(deal.id) {
        com.ddaeany0919.insightdeal.presentation.mypage.history.RecentDealManager.addRecentDeal(localContext, deal)
    }

    val currentPriceInt = deal.price.toInt()
    val targetUrl = deal.ecommerceUrl?.takeIf { it.isNotBlank() } ?: deal.postUrl ?: ""
    val isBookmarked = (wishlistState as? WishlistUiState.Success)?.items?.any { it.productUrl == targetUrl } ?: false

    val pricesList = remember(deal.sources, deal.siteName, deal.price, deal.currency, targetUrl) {
        deal.sources?.map { source ->
            MallPrice(
                platform = source.siteName,
                price = currentPriceInt,
                url = source.postUrl, // Kotlin compiler warning: postUrl is non-nullable String, no elvis needed
                currency = deal.currency
            )
        }?.takeIf { it.isNotEmpty() } ?: listOf(
            MallPrice(platform = deal.siteName, price = currentPriceInt, url = targetUrl, currency = deal.currency)
        )
    }

    val selectedPeriod by viewModel.selectedPeriod.collectAsState()

    DealDetailScreen(
        deal = deal,
        priceHistory = uiState.priceHistory,
        mallPrices = pricesList,
        isBookmarked = isBookmarked,
        votes = uiState.votes,
        selectedPeriod = selectedPeriod,
        onPeriodSelected = { viewModel.setPeriod(it) },
        onBack = onBack,
        onBookmarkToggle = {
            if (isBookmarked) {
                val itemToDelete = (wishlistState as? WishlistUiState.Success)?.items?.find { it.productUrl == targetUrl }
                if (itemToDelete != null) {
                    wishlistViewModel.deleteItem(itemToDelete)
                }
            } else {
                wishlistViewModel.addItem(
                    keyword = deal.title,
                    productUrl = targetUrl,
                    targetPrice = deal.price.toInt(),
                    currentLowestPrice = deal.price.toInt(),
                    currentLowestPlatform = deal.siteName
                )
                scope.launch {
                    val result = snackbarHostState.showSnackbar(
                        message = "관심 상품에 등록되었습니다 💖",
                        actionLabel = "찜 목록 보기 🚀",
                        duration = SnackbarDuration.Short
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        onNavigateToWatchlist()
                    }
                }
            }
        },
        onShare = { /* Todo */ },
        onOpenOrigin = onOpenUrl,
        onNavigateToCommunity = onNavigateToCommunity,
        snackbarHostState = snackbarHostState
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DealDetailScreen(
    deal: DealItem,
    priceHistory: List<PriceHistoryPoint>,
    mallPrices: List<MallPrice>,
    isBookmarked: Boolean,
    votes: ApiDealVotes?,
    selectedPeriod: String,
    onPeriodSelected: (String) -> Unit,
    onBack: () -> Unit,
    onBookmarkToggle: () -> Unit,
    onShare: () -> Unit,
    onOpenOrigin: (String) -> Unit,
    onNavigateToCommunity: () -> Unit,
    snackbarHostState: SnackbarHostState
) {
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            val targetUrl = remember(deal, mallPrices) {
                deal.ecommerceUrl?.takeIf { it.isNotBlank() } 
                    ?: mallPrices.firstOrNull()?.url 
                    ?: deal.sources?.firstOrNull()?.postUrl
            }
            if (targetUrl != null) {
                androidx.compose.material3.Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 16.dp,
                    modifier = Modifier.navigationBarsPadding()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                            .height(56.dp)
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary
                                    )
                                ),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .bounceClick { onOpenOrigin(targetUrl) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "최저가 구매하러 가기 🚀",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                letterSpacing = (-0.3).sp
                            )
                        }
                    }
                }
            }
        },
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = deal.title, 
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack, 
                            contentDescription = "뒤로가기",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onShare) {
                        Icon(
                            imageVector = Icons.Default.Share, 
                            contentDescription = "공유",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = onBookmarkToggle) {
                        Icon(
                            imageVector = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "북마크",
                            tint = if (isBookmarked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 고유한 key 및 contentType을 선언하여 스크롤 시 Compose 아이템의 재활용 성능을 극대화
            item(key = "deal_header", contentType = "header") {
                DealHeader(deal)
            }
            item(key = "price_chart", contentType = "chart") { 
                PriceHistoryInteractiveCard(
                    history = priceHistory,
                    currency = deal.currency,
                    selectedPeriod = selectedPeriod,
                    onPeriodSelected = onPeriodSelected
                ) 
            }
            item(key = "buyer_guide", contentType = "guide") {
                if (priceHistory.size > 1 || deal.honeyScore > 0) {
                    AIBuyerGuide(deal = deal, priceHistory = priceHistory, currentPrice = deal.price.toInt())
                }
            }
            item(key = "alert_register", contentType = "alert_btn") {
                val minPrice = remember(mallPrices) { mallPrices.minOfOrNull { it.price } ?: 0 }
                PriceAlertRegistrationButton(
                    currentPrice = minPrice,
                    onAlertClick = { _ -> /* MVP: 백엔드 푸시 알람 등록 */ }
                )
            }
            item(key = "community_gomin", contentType = "gomin_banner") {
                CommunityGominBanner(
                    votes = votes,
                    onClick = onNavigateToCommunity
                )
            }
            item(key = "mall_prices", contentType = "price_table") { 
                MallPriceTable(mallPrices, onOpenOrigin) 
            }
        }
    }
}

@Composable
private fun CommunityGominBanner(
    votes: ApiDealVotes?,
    onClick: () -> Unit
) {
    val totalVotes = remember(votes) { (votes?.buy_count ?: 0) + (votes?.pass_count ?: 0) }
    val hasVotes = totalVotes > 0
    val buyRatio = remember(votes, totalVotes) {
        if (hasVotes) {
            ((votes!!.buy_count.toFloat() / totalVotes) * 100).toInt()
        } else {
            0
        }
    }

    val statusText = remember(votes, hasVotes, buyRatio, totalVotes) {
        if (hasVotes) {
            "지름 지수 $buyRatio% 🔥 (${totalVotes}명 투표 중! 살까 ${votes?.buy_count ?: 0} | 말까 ${votes?.pass_count ?: 0})"
        } else {
            "지름 고수들의 투표를 보고 실패 없는 쇼핑을 시작하세요!"
        }
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick { onClick() }
    ) {
        Box(
            modifier = Modifier
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color(0xFFFF8E53), Color(0xFFFE6B8B))
                    )
                )
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.25f),
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("⚖️", fontSize = 20.sp)
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "이 특가 핫딜, 살까 말까 고민되시나요?",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = (-0.3).sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f),
                        letterSpacing = (-0.2).sp
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PriceAlertRegistrationButton(currentPrice: Int, onAlertClick: (Int) -> Unit) {
    if (currentPrice == 0) return
    var showDialog by remember { mutableStateOf(false) }
    var inputPrice by remember { mutableStateOf((currentPrice * 0.90).toInt().toString()) }
    var isConfigured by remember { mutableStateOf(false) }

    val formattedCurrentPrice = remember(currentPrice) { String.format("%,d", currentPrice) }

    OutlinedButton(
        onClick = { showDialog = true },
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .bounceClick { showDialog = true },
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (isConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(
            width = 1.5.dp, 
            color = if (isConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Icon(
            imageVector = if (isConfigured) Icons.Default.CheckCircle else Icons.Default.Notifications, 
            contentDescription = null, 
            modifier = Modifier.size(20.dp),
            tint = if (isConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (isConfigured) "목표가 알림 설정 완료" else "목표가 도달 시 알림 설정",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.3).sp
        )
    }

    if (showDialog) {
        val calculatedPrice = inputPrice.toIntOrNull() ?: 0
        val savedMoney = remember(currentPrice, calculatedPrice) {
            if (currentPrice > calculatedPrice && calculatedPrice > 0) {
                currentPrice - calculatedPrice
            } else {
                0
            }
        }
        val discountPercent = remember(currentPrice, calculatedPrice) {
            if (currentPrice > calculatedPrice && calculatedPrice > 0) {
                ((currentPrice - calculatedPrice).toFloat() / currentPrice * 100).toInt()
            } else {
                0
            }
        }

        AlertDialog(
            onDismissRequest = { showDialog = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            title = {
                Text(
                    text = "알림 받을 목표가 설정 🔔", 
                    fontWeight = FontWeight.ExtraBold, 
                    style = MaterialTheme.typography.titleLarge,
                    letterSpacing = (-0.5).sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "원하시는 목표 가격을 설정하면 가격 도달 시 푸시 알림을 보내드려요.\n현재 최저가: ${formattedCurrentPrice}원", 
                        style = MaterialTheme.typography.bodyMedium, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp,
                        letterSpacing = (-0.2).sp
                    )
                    Spacer(Modifier.height(16.dp))

                    // ⚡ 퀵 제안 할인 프리셋 칩 추가
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf(5, 10, 15).forEach { percent ->
                            val presetPrice = (currentPrice * (100 - percent) / 100)
                            FilterChip(
                                selected = (calculatedPrice == presetPrice),
                                onClick = { inputPrice = presetPrice.toString() },
                                label = { Text("-$percent%") },
                                shape = RoundedCornerShape(8.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = inputPrice,
                        onValueChange = { newValue ->
                            if (newValue.all { it.isDigit() }) inputPrice = newValue
                        },
                        label = { Text("목표 가격 (원)") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                        )
                    )

                    if (savedMoney > 0) {
                        Spacer(Modifier.height(12.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text(
                                text = "💸 설정 완료 시 오늘보다 약 ${String.format("%,d", savedMoney)}원 ($discountPercent%) 더 저렴하게 지를 수 있어요!",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                letterSpacing = (-0.3).sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = inputPrice.toIntOrNull() ?: 0
                        if (target > 0) {
                            onAlertClick(target)
                            isConfigured = true
                        }
                        showDialog = false
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("설정 완료", fontWeight = FontWeight.Bold, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDialog = false }
                ) {
                    Text("취소", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
private fun PriceHistoryInteractiveCard(
    history: List<PriceHistoryPoint>,
    currency: String = "KRW",
    selectedPeriod: String = "7d",
    onPeriodSelected: (String) -> Unit = {}
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header Row (핫딜 가격 변동 + 기간 선택 칩들)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "핫딜 가격 변동",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    letterSpacing = (-0.3).sp
                )
                
                // 기간 선택 칩들 (7일, 30일, 90일)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        "7d" to "7일",
                        "1m" to "30일",
                        "3m" to "90일"
                    ).forEach { (periodKey, label) ->
                        val isSelected = periodKey == selectedPeriod
                        val containerColor = if (isSelected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent
                        val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        val borderStroke = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                        
                        androidx.compose.material3.Surface(
                            onClick = { onPeriodSelected(periodKey) },
                            shape = RoundedCornerShape(8.dp),
                            color = containerColor,
                            contentColor = contentColor,
                            border = borderStroke,
                            modifier = Modifier.height(28.dp)
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(Modifier.height(16.dp))
            
            if (history.size <= 1) {
                // ⚡ 가격 정보 부족 시 미려한 AI 펄스 웨이브 애니메이션 카드 제공
                val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 0.95f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 1500, easing = LinearOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseAlpha"
                )
                val scale by infiniteTransition.animateFloat(
                    initialValue = 0.97f,
                    targetValue = 1.01f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseScale"
                )
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    androidx.compose.material3.Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f * alpha),
                        modifier = Modifier.size(54.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "AI 가격 이력 차트 빌딩 중 📈",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = (-0.3).sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "AI가 스마트 수집 로봇을 통해 이 상품의 역대 가격 변동 데이터를 백그라운드에서 실시간 적재하고 있습니다. 차트가 완성되는 대로 곧 보여드릴게요!",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                        letterSpacing = (-0.2).sp
                    )
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                    PriceChart(
                        prices = history.map { it.price.toFloat() },
                        dates = history.map { it.date },
                        modifier = Modifier.fillMaxSize(),
                        currency = currency
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "☝️ 그래프 영역을 터치하여 실시간 금액을 비교해 보세요", 
                    style = MaterialTheme.typography.labelSmall, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    letterSpacing = (-0.1).sp
                )
            }
        }
    }
}

@Composable
private fun AIBuyerGuide(deal: DealItem, priceHistory: List<PriceHistoryPoint>, currentPrice: Int) {
    if (currentPrice == 0) return

    val currency = remember(deal.currency) { deal.currency }
    val minPrice = remember(priceHistory, currentPrice) { priceHistory.minOfOrNull { it.price } ?: currentPrice }
    val maxPrice = remember(priceHistory, currentPrice) { priceHistory.maxOfOrNull { it.price } ?: currentPrice }
    val isRecordLow = remember(currentPrice, minPrice, priceHistory) { currentPrice <= minPrice && priceHistory.size > 1 }
    
    val isGoodDeal = deal.honeyScore >= 80
    
    // 다크모드/라이트모드에 완벽 반응하는 프리미엄 테마 컬러 동적 맵핑
    val isDark = isSystemInDarkTheme()
    
    val titleColor = remember(isRecordLow, isGoodDeal, isDark) {
        if (isRecordLow || isGoodDeal) {
            if (isDark) Color(0xFF4ADE80) else Color(0xFF166534)
        } else {
            if (isDark) Color(0xFF94A3B8) else Color(0xFF334155)
        }
    }
    val containerColor = remember(isRecordLow, isGoodDeal, isDark) {
        if (isRecordLow || isGoodDeal) {
            if (isDark) Color(0xFF064E3B).copy(alpha = 0.3f) else Color(0xFFF0FDF4)
        } else {
            if (isDark) Color(0xFF1E293B).copy(alpha = 0.3f) else Color(0xFFF8FAFC)
        }
    }
    val borderColor = remember(isRecordLow, isGoodDeal, isDark) {
        if (isRecordLow || isGoodDeal) {
            if (isDark) Color(0xFF059669) else Color(0xFF4ADE80)
        } else {
            if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
        }
    }

    // 통화별 가격 차이 포매팅 헬퍼 함수
    val formatDiff = remember(currency) {
        { diffVal: Int ->
            val safeCurr = currency.uppercase(Locale.getDefault())
            when (safeCurr) {
                "USD" -> String.format(Locale.US, "$%.2f", diffVal / 100.0)
                "EUR" -> String.format(Locale.US, "€%.2f", diffVal / 100.0)
                else -> String.format(Locale.getDefault(), "%,d원", diffVal)
            }
        }
    }

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isRecordLow || isGoodDeal) {
                        if (isDark) Color(0xFF065F46) else Color(0xFFDCFCE7)
                    } else {
                        if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)
                    },
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome, 
                            contentDescription = "AI", 
                            tint = titleColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "AI 지름 타점 정밀 분석", 
                        style = MaterialTheme.typography.titleMedium, 
                        fontWeight = FontWeight.ExtraBold,
                        color = titleColor,
                        letterSpacing = (-0.3).sp
                    )
                    Text(
                        text = if (isRecordLow || isGoodDeal) "강력 지름 추천 (매수 권장)" else "안정적 관망 또는 목표가 대기",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isRecordLow || isGoodDeal) Color(0xFF22C55E) else Color(0xFF64748B),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.2).sp
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = borderColor.copy(alpha = 0.5f))
            Spacer(Modifier.height(16.dp))
            
            if (priceHistory.size <= 1) {
                Text(
                    text = "💡 AI 가치 분석 점수: ${deal.honeyScore}점",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isGoodDeal) {
                        if (isDark) Color(0xFF34D399) else Color(0xFF059669)
                    } else {
                        if (isDark) Color(0xFFE2E8F0) else Color(0xFF334155)
                    },
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.3).sp
                )
                Spacer(Modifier.height(8.dp))
                val message = remember(isGoodDeal) {
                    if (isGoodDeal) {
                        "과거 수집 정보가 초기 단계이지만, 현 디바이스 데이터베이스의 유사 품종 평균보다 압도적으로 저렴한 지표를 갖추고 있습니다. 품절 전에 바로 진입하는 것을 권장합니다."
                    } else {
                        "유사 품종 평균 가격 대비 대동소이하며 뚜렷한 가격 하락 시그널은 없습니다. 핫딜 가격 알림을 켜고 목표가 도달을 대기하세요."
                    }
                }
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF334155),
                    lineHeight = 22.sp,
                    letterSpacing = (-0.2).sp
                )
            } else if (isRecordLow) {
                Text(
                    text = "🔥 역대 최저가 돌파 갱신!",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isDark) Color(0xFFF87171) else Color(0xFFDC2626),
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.3).sp
                )
                Spacer(Modifier.height(8.dp))
                val diffFromMax = maxPrice - currentPrice
                val formattedDiff = remember(diffFromMax) { formatDiff(diffFromMax) }
                Text(
                    text = "현재 수집된 역대 가격 역사 중 최저점을 돌파했습니다. 이전 최고가 대비 약 ${formattedDiff} 다운된 상황으로, 수량이 소진되기 전에 강력 추천합니다.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF334155),
                    lineHeight = 22.sp,
                    letterSpacing = (-0.2).sp
                )
            } else {
                val prevPrice = remember(priceHistory) {
                    if (priceHistory.size >= 2) priceHistory[priceHistory.size - 2].price else currentPrice
                }
                val diffFromPrev = currentPrice - prevPrice
                val diffFromMin = currentPrice - minPrice
                
                val formattedDiffPrev = remember(diffFromPrev) { formatDiff(Math.abs(diffFromPrev)) }
                val formattedDiffMin = remember(diffFromMin) { formatDiff(diffFromMin) }

                val dynamicMessage = remember(diffFromPrev, diffFromMin, formattedDiffPrev, formattedDiffMin) {
                    when {
                        diffFromPrev > 0 -> {
                            "📈 가격 상승 흐름 포착! 현재 가격은 직전 등록가 대비 약 ${formattedDiffPrev} 상승했으며, 역대 최저가 대비해서는 약 ${formattedDiffMin} 높은 상태입니다. 추가 상승 우려가 있으니 필요시 빠르게 결정을 내리시는 것을 제안합니다."
                        }
                        diffFromPrev < 0 -> {
                            "📉 점진적 하락세 진입! 현재 가격은 직전 등록가 대비 약 ${formattedDiffPrev} 더 저렴해졌으며, 역대 최저가와는 약 ${formattedDiffMin} 차이로 좁혀졌습니다. 꽤 매력적인 진입 타이밍입니다."
                        }
                        else -> {
                            "현재 가격은 직전 가격과 변동 없이 유지되고 있으며, 역대 최저가 대비 약 ${formattedDiffMin} 높은 수준에서 방어되고 있습니다. 굳이 급하지 않다면 '목표가 알림'을 걸어놓고 지켜보시는 것을 제안합니다."
                        }
                    }
                }

                Text(
                    text = "📊 AI 가격 트렌드 실시간 브리핑",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB),
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.3).sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                     text = dynamicMessage,
                     style = MaterialTheme.typography.bodyMedium,
                     color = if (isDark) Color(0xFFCBD5E1) else Color(0xFF334155),
                     lineHeight = 22.sp,
                     letterSpacing = (-0.2).sp
                )
            }
        }
    }
}

@Composable
private fun MallPriceTable(mallPrices: List<MallPrice>, onOpenOrigin: (String) -> Unit) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "웹 판매처 최저가 비교", 
                style = MaterialTheme.typography.titleMedium, 
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = (-0.3).sp
            )
            Spacer(Modifier.height(14.dp))
            
            mallPrices.forEachIndexed { index, row ->
                val priceText = remember(row.price, row.currency) {
                    when (row.currency?.uppercase()) {
                        "USD" -> "$${String.format(Locale.US, "%.2f", row.price.toDouble() / 100)}"
                        "EUR" -> "€${String.format(Locale.US, "%.2f", row.price.toDouble() / 100)}"
                        else -> "${formatPrice(row.price.toLong(), row.currency ?: "KRW")}"
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                        .bounceClick { onOpenOrigin(row.url) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = row.platform, 
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "실시간 매칭가", 
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = priceText, 
                            fontWeight = FontWeight.ExtraBold, 
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = (-0.2).sp
                        )
                        
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                if (index < mallPrices.size - 1) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    }
}

@Composable
fun DealHeader(deal: DealItem) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (!deal.imageUrl.isNullOrEmpty()) {
            Card(
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) {
                AsyncImage(
                    model = deal.imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp)
                        .background(Color.White),
                    contentScale = ContentScale.Fit,
                    alpha = 1.0f
                )
            }
        }
        
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // 고성능 렌더링 최적화: 사이트 이름 매칭 칩에 필요한 색상 매핑 연산을 remember 블록 내부에서 1회만 계산하도록 최적화
                val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
                val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
                
                val siteChips = remember(deal.siteNames, deal.siteName, surfaceVariant, onSurfaceVariant) {
                    val rawList = if (deal.siteNames.isNotEmpty()) deal.siteNames else deal.siteName.split(",").map { it.trim() }
                    rawList.map { sName ->
                        val siteNameLower = sName.lowercase(Locale.getDefault())
                        val (bgColor, textColor) = when {
                            siteNameLower.contains("뽐뿌") -> Color(0xFF1565C0) to Color.White
                            siteNameLower.contains("퀘이사존") -> Color(0xFFE65100) to Color.White
                            siteNameLower.contains("루리웹") -> Color(0xFF0D47A1) to Color.White
                            siteNameLower.contains("펨코") || siteNameLower.contains("에펨코리아") -> Color(0xFF0288D1) to Color.White
                            siteNameLower.contains("빠삭") -> Color(0xFFC2185B) to Color.White
                            siteNameLower.contains("클리앙") -> Color(0xFF37474F) to Color.White
                            else -> surfaceVariant to onSurfaceVariant
                        }
                        SiteChipInfo(name = sName, bgColor = bgColor, textColor = textColor)
                    }
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    siteChips.forEach { chip ->
                        Text(
                            text = chip.name,
                            style = MaterialTheme.typography.labelMedium,
                            color = chip.textColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .background(chip.bgColor, RoundedCornerShape(6.dp))
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
                
                // 배송비 관련 로직도 remember {} 블록 내부로 전면 격리하여 1회만 실행하도록 최적화
                val displayShippingInfo = remember(deal.shippingFee, deal.category, deal.title) {
                    val digitalCategories = listOf("적립", "이벤트", "모바일/기프티콘", "상품권", "패키지/이용권")
                    val isDigitalTitle = deal.title.contains("요금제") || deal.title.contains("데이터")
                    val hideShipping = deal.category in digitalCategories || isDigitalTitle
                    
                    if (hideShipping) {
                        null
                    } else {
                        val trimmed = deal.shippingFee?.trim() ?: "정보 없음"
                        when {
                            trimmed == "정보 없음" || trimmed.isEmpty() -> "확인 필요"
                            trimmed == "0" || trimmed == "0원" || trimmed == "무배" || trimmed == "무료" || trimmed == "무료배송" -> "무료"
                            trimmed.matches(Regex("^0(원)?\\s*(/|\\+).*")) -> trimmed.replace(Regex("^0(원)?\\s*"), "무료 ")
                            trimmed == "유료" || trimmed == "유료배송" -> "유료"
                            trimmed.matches(Regex("^[0-9,]+$")) -> "${trimmed}원"
                            else -> trimmed.replace("무료배송", "무료").replace("유료배송", "유료")
                        }
                    }
                }
                
                if (displayShippingInfo != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text(
                            text = "배송비: $displayShippingInfo",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(14.dp))
            
            Text(
                text = deal.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 28.sp,
                letterSpacing = (-0.5).sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(verticalAlignment = Alignment.Bottom) {
                if (deal.discountRate != null && deal.discountRate > 0) {
                    Text(
                        text = "${deal.discountRate}%",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.5).sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                
                val priceText = remember(deal.price, deal.currency) { formatPrice(deal.price, deal.currency) }
                Text(
                    text = priceText,
                    style = if (priceText == "본문 참조") MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Black,
                    color = if (priceText == "본문 참조") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    letterSpacing = (-0.5).sp
                )
                
                if (priceText == "본문 참조") {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp).padding(bottom = 2.dp)
                    )
                }
            }

            // ⌚ 등록 일시 & 실시간 조회수 메타데이터 바 (Premium HSL Gray Typography & Dot Divider)
            Spacer(modifier = Modifier.height(10.dp))
            val absoluteTimeText = remember(deal.createdAt) {
                if (deal.createdAt.isNullOrEmpty()) return@remember ""
                try {
                    val parsedTime = try {
                        java.time.OffsetDateTime.parse(deal.createdAt)
                    } catch (e: Exception) {
                        val local = java.time.LocalDateTime.parse(deal.createdAt)
                        local.atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime()
                    }
                    val formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm", Locale.getDefault())
                    parsedTime.format(formatter)
                } catch (e: Exception) {
                    ""
                }
            }
            val relativeTime = rememberRelativeTime(deal.createdAt)
            val timeDisplayText = remember(relativeTime, absoluteTimeText) {
                when {
                    relativeTime.isNotEmpty() && absoluteTimeText.isNotEmpty() -> "$relativeTime ($absoluteTimeText)"
                    relativeTime.isNotEmpty() -> relativeTime
                    absoluteTimeText.isNotEmpty() -> absoluteTimeText
                    else -> "등록일 정보 없음"
                }
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "등록 $timeDisplayText",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                
                Text(
                    text = "조회 ${deal.viewCount}회",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Medium
                )
            }
            
            if (deal.aiSummary != null && deal.aiSummary.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
                    elevation = CardDefaults.cardElevation(0.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome, 
                            contentDescription = "AI 요약", 
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 2.dp).size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = deal.aiSummary, 
                            style = MaterialTheme.typography.bodyMedium,
                            lineHeight = 22.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            letterSpacing = (-0.2).sp
                        )
                    }
                }
            }

            if (deal.contentHtml != null && deal.contentHtml.isNotEmpty() && deal.contentHtml.length > 10) {
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "본문 내용",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(10.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    elevation = CardDefaults.cardElevation(0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = deal.contentHtml,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 22.sp,
                            letterSpacing = (-0.1).sp
                        ),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}
