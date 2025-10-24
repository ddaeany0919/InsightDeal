package com.yourpackage.insightdeal

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

// 폴센트 스타일 브랜드 컬러
val PrimaryOrange = Color(0xFFFF6B35)
val LightGray = Color(0xFFF5F5F5)
val DarkGray = Color(0xFF333333)
val Green = Color(0xFF4CAF50)
val Red = Color(0xFFF44336)
val Blue = Color(0xFF2196F3)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoupangTrackingScreen(
    viewModel: CoupangTrackingViewModel = viewModel(),
    onNavigateToChart: (Int) -> Unit = {}
) {
    val products by viewModel.products.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    
    var showAddDialog by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // 🎨 폴센트 스타일 헤더
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = PrimaryOrange,
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "가격 추적",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "${products.size}개 상품 추적 중",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                }
                
                FloatingActionButton(
                    onClick = { showAddDialog = true },
                    containerColor = Color.White,
                    contentColor = PrimaryOrange,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "상품 추가")
                }
            }
        }
        
        // 🔄 로딩 상태
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = PrimaryOrange)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("상품 정보 가져오는 중...")
                }
            }
        } else if (products.isEmpty()) {
            // 🏷️ 빈 상태 화면 (폴센트 스타일)
            EmptyStateScreen(onAddProduct = { showAddDialog = true })
        } else {
            // 📦 상품 목록
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(products) { product ->
                    ProductTrackingCard(
                        product = product,
                        onCardClick = { onNavigateToChart(product.id) },
                        onTargetPriceChange = { newPrice ->
                            viewModel.updateTargetPrice(product.id, newPrice)
                        }
                    )
                }
            }
        }
    }
    
    // 📲 상품 추가 다이얼로그
    if (showAddDialog) {
        AddProductDialog(
            onDismiss = { showAddDialog = false },
            onAddProduct = { url, targetPrice ->
                viewModel.addProduct(url, targetPrice)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun ProductTrackingCard(
    product: ProductData,
    onCardClick: () -> Unit,
    onTargetPriceChange: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCardClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 📸 상품 이미지 + 기본 정보
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 상품 이미지
                AsyncImage(
                    model = product.imageUrl,
                    contentDescription = "상품 이미지",
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(LightGray),
                    contentScale = ContentScale.Crop
                )
                
                // 상품 정보
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    Text(
                        text = product.title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = DarkGray,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    if (product.brand.isNotEmpty()) {
                        Text(
                            text = product.brand,
                            fontSize = 13.sp,
                            color = Color.Gray
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 💰 현재 가격 (폴센트 스타일)
                    Row(
                        verticalAlignment = Alignment.Baseline,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${product.currentPrice:,}원",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = DarkGray
                        )
                        
                        // 🔺🔻 가격 변동 표시
                        if (product.priceChangePercent != 0f) {
                            val isIncrease = product.priceChangePercent > 0
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Icon(
                                    imageVector = if (isIncrease) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                    contentDescription = if (isIncrease) "가격 상승" else "가격 하락",
                                    tint = if (isIncrease) Red else Blue,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "${kotlin.math.abs(product.priceChangePercent)}%",
                                    fontSize = 13.sp,
                                    color = if (isIncrease) Red else Blue,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // 📊 가격 정보 요약 (폴센트 스타일)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                PriceInfoChip(
                    label = "역대 최저",
                    value = "${product.lowestPrice:,}원",
                    color = Blue,
                    isHighlight = product.currentPrice == product.lowestPrice
                )
                
                PriceInfoChip(
                    label = "목표 가격",
                    value = "${product.targetPrice:,}원",
                    color = Green,
                    isHighlight = product.currentPrice <= product.targetPrice
                )
                
                PriceInfoChip(
                    label = "할인률",
                    value = "${product.discountRate}%",
                    color = PrimaryOrange
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // 🎯 목표가 달성 알림
            if (product.currentPrice <= product.targetPrice) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    color = Green.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "목표 달성",
                            tint = Green,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "🎉 목표 가격 달성! 지금이 구매 타이밍입니다.",
                            fontSize = 14.sp,
                            color = Green,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PriceInfoChip(
    label: String,
    value: String,
    color: Color,
    isHighlight: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isHighlight) color.copy(alpha = 0.15f) else LightGray
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                color = Color.Gray
            )
            Text(
                text = value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = if (isHighlight) color else DarkGray
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddProductDialog(
    onDismiss: () -> Unit,
    onAddProduct: (String, Int) -> Unit
) {
    var url by remember { mutableStateOf("") }
    var targetPriceText by remember { mutableStateOf("") }
    var isUrlValid by remember { mutableStateOf(true) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val targetPrice = targetPriceText.toIntOrNull() ?: 0
                    if (url.contains("coupang.com") && targetPrice > 0) {
                        onAddProduct(url, targetPrice)
                    }
                },
                enabled = url.contains("coupang.com") && targetPriceText.toIntOrNull() != null
            ) {
                Text("추가", color = PrimaryOrange)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        },
        title = {
            Text(
                text = "🛒 쿠팡 상품 추가",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { 
                        url = it
                        isUrlValid = it.contains("coupang.com")
                    },
                    label = { Text("쿠팡 상품 URL") },
                    placeholder = { Text("https://www.coupang.com/vp/products/...") },
                    isError = !isUrlValid && url.isNotEmpty(),
                    supportingText = {
                        if (!isUrlValid && url.isNotEmpty()) {
                            Text("올바른 쿠팡 URL을 입력해주세요", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = targetPriceText,
                    onValueChange = { targetPriceText = it.filter { char -> char.isDigit() } },
                    label = { Text("목표 가격") },
                    placeholder = { Text("50000") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    trailingIcon = { Text("원", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    )
}

@Composable
fun EmptyStateScreen(onAddProduct: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.ShoppingCart,
            contentDescription = "빈 장바구니",
            modifier = Modifier.size(80.dp),
            tint = Color.Gray.copy(alpha = 0.5f)
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "추적 중인 상품이 없습니다",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = DarkGray
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "쿠팡 상품을 추가하고\\n가격 변동을 추적해보세요!",
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onAddProduct,
            colors = ButtonDefaults.buttonColors(containerColor = PrimaryOrange),
            shape = RoundedCornerShape(24.dp)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "추가",
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("첫 상품 추가하기")
        }
    }
}

// 📊 데이터 모델
data class ProductData(
    val id: Int,
    val title: String,
    val brand: String,
    val imageUrl: String,
    val currentPrice: Int,
    val lowestPrice: Int,
    val highestPrice: Int,
    val targetPrice: Int,
    val priceChangePercent: Float,
    val discountRate: Int
)