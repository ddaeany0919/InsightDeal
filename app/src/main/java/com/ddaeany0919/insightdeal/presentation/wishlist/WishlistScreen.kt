package com.ddaeany0919.insightdeal.presentation.wishlist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 💎 관심상품 화면
 * 키워드 기반 관심상품 등록, 조회, 수정, 삭제 및 가격 추적 기능
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistScreen(
    viewModel: WishlistViewModel = viewModel()
) {
    var showAddDialog by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsState()
    
    // 초기 데이터 로드
    LaunchedEffect(Unit) {
        viewModel.loadWishlist()
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // 헤더
            WishlistHeader(
                totalItems = uiState.wishlists.size,
                onAddClick = { showAddDialog = true }
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 콘텐츠
            when {
                uiState.isLoading -> {
                    LoadingState()
                }
                uiState.wishlists.isEmpty() -> {
                    EmptyWishlistState { showAddDialog = true }
                }
                else -> {
                    WishlistContent(
                        wishlists = uiState.wishlists,
                        onItemClick = { /* TODO: 상세 화면 */ },
                        onDeleteClick = { viewModel.deleteWishlist(it.id) },
                        onPriceCheckClick = { viewModel.checkPrice(it.id) }
                    )
                }
            }
        }
        
        // 관심상품 추가 다이얼로그
        if (showAddDialog) {
            AddWishlistDialog(
                onDismiss = { showAddDialog = false },
                onAdd = { keyword, targetPrice ->
                    viewModel.addWishlist(keyword, targetPrice)
                    showAddDialog = false
                }
            )
        }
        
        // 에러 스낵바
        uiState.errorMessage?.let { error ->
            LaunchedEffect(error) {
                // TODO: 스낵바 표시
                viewModel.clearError()
            }
        }
    }
}

@Composable
private fun WishlistHeader(
    totalItems: Int,
    onAddClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = "관심상품",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${totalItems}개 상품을 추적 중",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        
        FloatingActionButton(
            onClick = onAddClick,
            modifier = Modifier.size(56.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "관심상품 추가",
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun WishlistContent(
    wishlists: List<WishlistItem>,
    onItemClick: (WishlistItem) -> Unit,
    onDeleteClick: (WishlistItem) -> Unit,
    onPriceCheckClick: (WishlistItem) -> Unit
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = wishlists,
            key = { it.id }
        ) { wishlist ->
            WishlistCard(
                wishlist = wishlist,
                onClick = { onItemClick(wishlist) },
                onDeleteClick = { onDeleteClick(wishlist) },
                onPriceCheckClick = { onPriceCheckClick(wishlist) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WishlistCard(
    wishlist: WishlistItem,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onPriceCheckClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)),
        onClick = onClick,
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (wishlist.isTargetReached) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // 키워드와 상태 배지
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = wishlist.keyword,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                
                Row {
                    // 가격 하락 뱃지
                    if (wishlist.priceDropPercentage > 0) {
                        Surface(
                            color = Color(0xFFFF5722),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = "🔥 ${wishlist.priceDropPercentage}% 할인!",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    
                    // 목표 달성 뱃지
                    if (wishlist.isTargetReached) {
                        Surface(
                            color = Color(0xFF4CAF50),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "🎯 목표 달성!",
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 현재 가격 vs 목표 가격
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "목표 가격",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${String.format("%,d", wishlist.targetPrice)}원",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "현재 최저가",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    wishlist.currentLowestPrice?.let { price ->
                        Text(
                            text = "${String.format("%,d", price)}원",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = if (price <= wishlist.targetPrice) {
                                Color(0xFF4CAF50)
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                        
                        wishlist.currentLowestPlatform?.let { platform ->
                            Text(
                                text = platform,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } ?: run {
                        Text(
                            text = "검색 중...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // 진행바
            Spacer(modifier = Modifier.height(12.dp))
            
            val progress = wishlist.currentLowestPrice?.let { current ->
                (current.toFloat() / wishlist.targetPrice).coerceAtMost(1f)
            } ?: 1f
            
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth(),
                color = if (progress <= 1f) {
                    Color(0xFF4CAF50)
                } else {
                    Color(0xFFFF5722)
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            
            // 액션 버튼들
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 가격 체크 버튼
                OutlinedButton(
                    onClick = onPriceCheckClick,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("가격 체크")
                }
                
                // 삭제 버튼
                OutlinedButton(
                    onClick = onDeleteClick,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFFF5722)
                    )
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "삭제",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            // 마지막 체크 시간
            wishlist.lastChecked?.let { lastChecked ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "마지막 체크: ${lastChecked.format(DateTimeFormatter.ofPattern("MM/dd HH:mm", Locale.KOREAN))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun AddWishlistDialog(
    onDismiss: () -> Unit,
    onAdd: (String, Int) -> Unit
) {
    var keyword by remember { mutableStateOf("") }
    var targetPrice by remember { mutableStateOf("") }
    var keywordError by remember { mutableStateOf<String?>(null) }
    var priceError by remember { mutableStateOf<String?>(null) }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    text = "관심상품 추가",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 키워드 입력
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { 
                        keyword = it
                        keywordError = null
                    },
                    label = { Text("검색 키워드") },
                    placeholder = { Text("예: 아이폰 15, 갤럭시 S24") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = keywordError != null,
                    supportingText = keywordError?.let { { Text(it) } }
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // 목표 가격 입력
                OutlinedTextField(
                    value = targetPrice,
                    onValueChange = { 
                        targetPrice = it.filter { char -> char.isDigit() }
                        priceError = null
                    },
                    label = { Text("목표 가격") },
                    placeholder = { Text("1200000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    isError = priceError != null,
                    supportingText = priceError?.let { { Text(it) } },
                    trailingIcon = {
                        Text(
                            text = "원",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // 버튼들
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("취소")
                    }
                    
                    Button(
                        onClick = {
                            // 유효성 검사
                            var hasError = false
                            
                            if (keyword.trim().length < 2) {
                                keywordError = "키워드는 2글자 이상 입력해주세요"
                                hasError = true
                            }
                            
                            val price = targetPrice.toIntOrNull()
                            if (price == null || price <= 0) {
                                priceError = "올바른 가격을 입력해주세요"
                                hasError = true
                            }
                            
                            if (!hasError) {
                                onAdd(keyword.trim(), price!!)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("추가")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyWishlistState(
    onAdd: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.FavoriteBorder,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "관심상품이 없습니다",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "키워드를 등록하고\n가격 변화를 추적해보세요",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(
            onClick = onAdd,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("첫 관심상품 추가")
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "관심상품을 불러오는 중...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}