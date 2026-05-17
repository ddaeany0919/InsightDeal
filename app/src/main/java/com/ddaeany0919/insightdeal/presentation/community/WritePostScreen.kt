package com.ddaeany0919.insightdeal.presentation.community

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.ShoppingCart
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.ddaeany0919.insightdeal.data.network.CommunityPostCreateReq
import com.ddaeany0919.insightdeal.models.DealItem
import com.ddaeany0919.insightdeal.network.NetworkModule
import com.ddaeany0919.insightdeal.presentation.auth.AuthManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.FlowPreview::class)
class WritePostViewModel : ViewModel() {
    private val api = NetworkModule.createService<com.ddaeany0919.insightdeal.network.ApiService>()

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _submitSuccess = MutableStateFlow(false)
    val submitSuccess: StateFlow<Boolean> = _submitSuccess.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    var postType = MutableStateFlow("REQUEST")
    var title = MutableStateFlow("")
    var content = MutableStateFlow("")
    var targetPrice = MutableStateFlow("")
    var location = MutableStateFlow("")
    var bountyPoints = MutableStateFlow(0)
    var likeCount = MutableStateFlow(0)

    // 🛍️ 핫딜 연동 상태
    private val _selectedDeal = MutableStateFlow<DealItem?>(null)
    val selectedDeal: StateFlow<DealItem?> = _selectedDeal.asStateFlow()

    private val _selectedDealLoading = MutableStateFlow(false)
    val selectedDealLoading: StateFlow<Boolean> = _selectedDealLoading.asStateFlow()

    private val _deals = MutableStateFlow<List<DealItem>>(emptyList())
    val deals: StateFlow<List<DealItem>> = _deals.asStateFlow()

    private val _dealsLoading = MutableStateFlow(false)
    val dealsLoading: StateFlow<Boolean> = _dealsLoading.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    init {
        // 검색 쿼리에 따른 핫딜 자동 페칭
        viewModelScope.launch {
            _searchQuery
                .debounce(300)
                .distinctUntilChanged()
                .collect { query ->
                    _dealsLoading.value = true
                    try {
                        val response = api.getCommunityHotDeals(
                            limit = 20,
                            offset = 0,
                            category = null,
                            keyword = if (query.isBlank()) null else query,
                            platform = null
                        )
                        if (response.isSuccessful && response.body() != null) {
                            _deals.value = response.body()!!.deals
                        }
                    } catch (e: Exception) {
                        // 에러 로그 무시
                    } finally {
                        _dealsLoading.value = false
                    }
                }
        }
    }

    fun setSelectedDeal(deal: DealItem?) {
        _selectedDeal.value = deal
        if (deal != null) {
            location.value = "deal_id:${deal.id}"
            if (targetPrice.value.isBlank()) {
                targetPrice.value = deal.price.toString()
            }
        } else {
            location.value = ""
        }
    }

    fun loadSelectedDeal(dealId: Int) {
        viewModelScope.launch {
            _selectedDealLoading.value = true
            try {
                val response = api.getEnhancedDealInfo(dealId)
                if (response.isSuccessful && response.body() != null) {
                    _selectedDeal.value = response.body()
                }
            } catch (e: Exception) {
                // 에러 무시
            } finally {
                _selectedDealLoading.value = false
            }
        }
    }

    fun loadPost(postId: Int) {
        viewModelScope.launch {
            _isLoading.value = true
            _errorMessage.value = null
            try {
                val response = api.getCommunityPost(postId)
                if (response.isSuccessful) {
                    val p = response.body()
                    if (p != null) {
                        postType.value = p.postType
                        title.value = p.title
                        content.value = p.content
                        targetPrice.value = p.targetPrice?.toString() ?: ""
                        location.value = p.location ?: ""
                        bountyPoints.value = p.bountyPoints
                        likeCount.value = p.likeCount
                        
                        p.location?.let { loc ->
                            if (loc.startsWith("deal_id:")) {
                                val idStr = loc.removePrefix("deal_id:")
                                idStr.toIntOrNull()?.let { dealId ->
                                    loadSelectedDeal(dealId)
                                }
                            } else if (loc.toIntOrNull() != null) {
                                loc.toIntOrNull()?.let { dealId ->
                                    loadSelectedDeal(dealId)
                                }
                            }
                            Unit
                        }
                    }
                } else {
                    _errorMessage.value = "게시글 정보를 불러오지 못했습니다."
                }
            } catch (e: Exception) {
                _errorMessage.value = "네트워크 오류: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun submitPost(
        postId: Int?,
        userId: String,
        nickname: String
    ) {
        viewModelScope.launch {
            _isSubmitting.value = true
            _errorMessage.value = null
            try {
                val req = CommunityPostCreateReq(
                    postType = "REQUEST", // unified 고민방
                    userId = userId,
                    nickname = nickname,
                    title = title.value,
                    content = content.value,
                    targetPrice = targetPrice.value.toIntOrNull(),
                    bountyPoints = if (postId != null) bountyPoints.value else 0,
                    likeCount = if (postId != null) likeCount.value else 0,
                    location = location.value.takeIf { it.isNotBlank() }
                )
                val response = if (postId != null) {
                    api.updateCommunityPost(postId, req)
                } else {
                    api.createCommunityPost(req)
                }
                
                if (response.isSuccessful) {
                    _submitSuccess.value = true
                } else {
                    _errorMessage.value = "요청에 실패했습니다. (에러: ${response.code()})"
                }
            } catch (e: Exception) {
                _errorMessage.value = "네트워크 오류: ${e.message}"
            } finally {
                _isSubmitting.value = false
            }
        }
    }
}

private val AccentOrange = Color(0xFFFF6B35)
private val AccentOrangeSoft = Color(0xFFFFF0EB)
private val NeutralGray50 = Color(0xFFF8F9FA)
private val NeutralGray200 = Color(0xFFE9ECEF)
private val NeutralGray500 = Color(0xFF868E96)
private val NeutralGray700 = Color(0xFF495057)
private val NeutralGray900 = Color(0xFF212529)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WritePostScreen(
    navController: NavController? = null,
    postId: Int? = null,
    viewModel: WritePostViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    val userId by AuthManager.getUsername(context).collectAsState(initial = "admin")
    val nickname by AuthManager.getNickname(context).collectAsState(initial = "admin")

    val isSubmitting by viewModel.isSubmitting.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val submitSuccess by viewModel.submitSuccess.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val title by viewModel.title.collectAsState()
    val content by viewModel.content.collectAsState()
    val targetPrice by viewModel.targetPrice.collectAsState()

    val selectedDeal by viewModel.selectedDeal.collectAsState()
    val deals by viewModel.deals.collectAsState()
    val dealsLoading by viewModel.dealsLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(postId) {
        if (postId != null) {
            viewModel.loadPost(postId)
        }
    }

    LaunchedEffect(submitSuccess) {
        if (submitSuccess) {
            navController?.popBackStack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (postId == null) "⚖️ 살까말까 고민 올리기" else "게시글 수정", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController?.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로 가기")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.submitPost(
                                postId = postId,
                                userId = userId ?: "admin",
                                nickname = nickname ?: "admin"
                            )
                        },
                        enabled = title.isNotBlank() && content.isNotBlank() && !isSubmitting && !isLoading,
                        colors = ButtonDefaults.textButtonColors(contentColor = AccentOrange)
                    ) {
                        Text(if (postId == null) "등록" else "수정", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    }
                }
            )
        },
        containerColor = NeutralGray50
    ) { paddingValues ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentOrange)
            }
            return@Scaffold
        }
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            errorMessage?.let {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = it, 
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // 가이드 배너
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = AccentOrangeSoft.copy(alpha = 0.6f)
                ),
                border = BorderStroke(
                    1.dp,
                    AccentOrange.copy(alpha = 0.2f)
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = AccentOrange.copy(alpha = 0.15f),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("⚖️", fontSize = 18.sp)
                        }
                    }
                    Column {
                        Text(
                            text = "⚖️ 살까말까 고민방 가이드",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = AccentOrange
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "살까 말까 망설여지는 특가 핫딜을 공유하고 글을 남겨주세요. 지름 고수들의 투표와 날카로운 피드백으로 고민을 해결해 드립니다!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = NeutralGray700,
                            lineHeight = 20.sp
                        )
                    }
                }
            }

            // 🛍️ 핫딜 상품 연동 영역
            if (selectedDeal == null) {
                Card(
                    onClick = { showBottomSheet = true },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.5.dp, NeutralGray200),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = AccentOrangeSoft,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Outlined.ShoppingCart,
                                    contentDescription = null,
                                    tint = AccentOrange,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "🛍️ 투표에 연동할 핫딜 상품 선택 (선택)",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeutralGray900
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "망설여지는 핫딜을 연동해 고수들의 투표율을 높여보세요.",
                                fontSize = 12.sp,
                                color = NeutralGray500
                            )
                        }
                    }
                }
            } else {
                val deal = selectedDeal!!
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.5.dp, AccentOrange.copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(
                                    imageVector = Icons.Outlined.ShoppingCart,
                                    contentDescription = null,
                                    tint = AccentOrange,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "🛍️ 연동된 핫딜 상품",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentOrange
                                )
                            }
                            
                            IconButton(
                                onClick = { viewModel.setSelectedDeal(null) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "연동 해제",
                                    tint = NeutralGray500,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (!deal.imageUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = deal.imageUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(NeutralGray50)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(NeutralGray50),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ShoppingCart,
                                        contentDescription = null,
                                        tint = NeutralGray500,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        color = AccentOrangeSoft,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = deal.siteName,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AccentOrange,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    if (deal.discountRate != null && deal.discountRate > 0) {
                                        Text(
                                            text = "${deal.discountRate}% 할인",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Black,
                                            color = AccentOrange
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = deal.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeutralGray900,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${"%,d".format(deal.price)}원",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = NeutralGray900
                                )
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = title,
                onValueChange = { viewModel.title.value = it },
                label = { Text("제목을 입력하세요") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentOrange,
                    focusedLabelColor = AccentOrange
                )
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = targetPrice,
                    onValueChange = { viewModel.targetPrice.value = it },
                    label = { Text("고민 중인 구매 가격 (선택)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    prefix = { Text("₩ ") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentOrange,
                        focusedLabelColor = AccentOrange
                    )
                )
            }

            OutlinedTextField(
                value = content,
                onValueChange = { viewModel.content.value = it },
                label = { Text("고민되는 이유를 자유롭게 적어주세요\n(예: 지금 타도 괜찮을까요? 디자인이 마음에 드는데...)") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 240.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentOrange,
                    focusedLabelColor = AccentOrange
                )
            )
        }
    }

    // 🛍️ 핫딜 상품 선택 바텀 시트
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            containerColor = Color.White
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
            ) {
                // 바텀 시트 헤더
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🛍️ 연동할 핫딜 상품 선택",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = NeutralGray900
                    )
                    IconButton(onClick = { showBottomSheet = false }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "닫기", tint = NeutralGray500)
                    }
                }
                
                // 검색 바
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    placeholder = { Text("핫딜 상품명 검색...", color = NeutralGray500) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "검색", tint = NeutralGray500)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(imageVector = Icons.Default.Clear, contentDescription = "지우기", tint = NeutralGray500)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AccentOrange,
                        focusedLabelColor = AccentOrange
                    )
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 최근 핫딜 리스트
                if (dealsLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AccentOrange)
                    }
                } else if (deals.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🔍", fontSize = 40.sp)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "검색 결과가 없습니다",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = NeutralGray700
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "다른 단어로 검색해 보세요",
                                fontSize = 13.sp,
                                color = NeutralGray500
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(deals, key = { it.id }) { deal ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        viewModel.setSelectedDeal(deal)
                                        showBottomSheet = false
                                    }
                                    .padding(vertical = 12.dp, horizontal = 20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                if (!deal.imageUrl.isNullOrBlank()) {
                                    AsyncImage(
                                        model = deal.imageUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(NeutralGray50)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(NeutralGray50),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ShoppingCart,
                                            contentDescription = null,
                                            tint = NeutralGray500,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Surface(
                                            color = NeutralGray50,
                                            border = BorderStroke(1.dp, NeutralGray200),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = deal.siteName,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = NeutralGray700,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        if (deal.discountRate != null && deal.discountRate > 0) {
                                            Text(
                                                text = "${deal.discountRate}%",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Black,
                                                color = AccentOrange
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = deal.title,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = NeutralGray900,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "${"%,d".format(deal.price)}원",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = NeutralGray700
                                    )
                                }
                            }
                            HorizontalDivider(color = NeutralGray200, thickness = 0.5.dp)
                        }
                    }
                }
            }
        }
    }
}
