package com.ddaeany0919.insightdeal.presentation.auth

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import com.ddaeany0919.insightdeal.network.UserWithdrawRequest

enum class AuthTab {
    LOGIN, REGISTER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onBackClick: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var socialAgreed by remember { mutableStateOf(false) }

    val savedUsername by AuthManager.getUsername(ctx).collectAsState(initial = "guest")
    val savedNickname by AuthManager.getNickname(ctx).collectAsState(initial = "guest")
    val autoLoginEnabled by AuthManager.isAutoLoginEnabled(ctx).collectAsState(initial = true)
    val rememberIdEnabled by AuthManager.isRememberIdEnabled(ctx).collectAsState(initial = true)
    val savedId by AuthManager.getSavedId(ctx).collectAsState(initial = "admin")

    val brandAccent = MaterialTheme.colorScheme.primary
    val isUserGuest = savedUsername == "guest" || savedUsername.isNullOrEmpty()

    // 🔒 회원 탈퇴 제어 상태들
    var showWithdrawPasswordDialog by remember { mutableStateOf(false) }
    var showWithdrawConfirmDialog by remember { mutableStateOf(false) }
    var withdrawPasswordInput by remember { mutableStateOf("") }
    var isWithdrawPasswordVisible by remember { mutableStateOf(false) }

    // ⚡ 로그인 / 회원가입 탭 상태
    var selectedTab by remember { mutableStateOf(AuthTab.LOGIN) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        text = if (isUserGuest) "회원가입 / 로그인" else "회원 정보 수정", 
                        fontWeight = FontWeight.ExtraBold, 
                        fontSize = 20.sp
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로가기",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(paddingValues)
                .verticalScroll(scrollState)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 👤 1. 가입 / 로그인 입력 폼 통합 프리미엄 카드
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    var currentUserId by remember(savedUsername, savedId) { 
                        mutableStateOf(if (savedUsername == "guest" || savedUsername.isNullOrEmpty()) (savedId ?: "admin") else savedUsername!!) 
                    }
                    var currentNickname by remember(savedNickname) {
                        mutableStateOf(if (savedNickname == "guest" || savedNickname.isNullOrEmpty()) "합리적쇼핑꾼" else savedNickname!!)
                    }
                    var currentPassword by remember { mutableStateOf("") }
                    var confirmPassword by remember { mutableStateOf("") }
                    var oldPassword by remember { mutableStateOf("") }
                    var showPasswordFields by remember { mutableStateOf(false) }
                    var isCurrentPasswordVerified by remember { mutableStateOf(false) }
                    var currentPasswordErrorText by remember { mutableStateOf("") }

                    var isPasswordVisible by remember { mutableStateOf(false) }
                    var isConfirmPasswordVisible by remember { mutableStateOf(false) }
                    var isOldPasswordVisible by remember { mutableStateOf(false) }

                    // 상단 타이틀 및 탭 전환 (게스트인 경우에만 탭 셀렉터 노출)
                    if (isUserGuest) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (selectedTab == AuthTab.LOGIN) brandAccent else Color.Transparent,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { selectedTab = AuthTab.LOGIN }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "로그인",
                                    color = if (selectedTab == AuthTab.LOGIN) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (selectedTab == AuthTab.REGISTER) brandAccent else Color.Transparent,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { selectedTab = AuthTab.REGISTER }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "회원가입",
                                    color = if (selectedTab == AuthTab.REGISTER) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    Text(
                        text = if (!isUserGuest) "👤 회원 정보 수정" else if (selectedTab == AuthTab.LOGIN) "🔑 계정 로그인" else "✨ 신규 회원가입",
                        style = MaterialTheme.typography.titleMedium,
                        color = brandAccent,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(modifier = Modifier.height(18.dp))

                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        // 1) User ID 필드 (로그인, 회원가입, 정보수정 공통)
                        Column {
                            Text("고유 User ID", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = currentUserId,
                                onValueChange = { currentUserId = it },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                enabled = isUserGuest, // 이미 로그인된 회원은 ID 수정 불가
                                placeholder = { Text("아이디를 입력하세요 (예: admin)") },
                                leadingIcon = { Icon(Icons.Default.Person, contentDescription = "ID") },
                                textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = brandAccent,
                                    focusedLabelColor = brandAccent,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                    focusedContainerColor = MaterialTheme.colorScheme.background
                                )
                            )
                        }

                        // 2) 비밀번호 필드 (로그인, 회원가입 시 필수 노출, 정보수정 시에는 생략 혹은 간소화)
                        if (isUserGuest) {
                            Column {
                                Text("로그인 비밀번호", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = currentPassword,
                                    onValueChange = { currentPassword = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    placeholder = { Text("비밀번호를 입력하세요") },
                                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Password") },
                                    trailingIcon = {
                                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                            Icon(
                                                imageVector = if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                                contentDescription = "비밀번호 표시 토글"
                                            )
                                        }
                                    },
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = brandAccent,
                                        focusedLabelColor = brandAccent,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                        unfocusedContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                        focusedContainerColor = MaterialTheme.colorScheme.background
                                    )
                                )
                            }

                            // ⚡ [로그인 탭인 경우] 자동 로그인 & 아이디 저장 소형 체크박스 노출
                            if (selectedTab == AuthTab.LOGIN) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Start
                                ) {
                                    // 자동 로그인 체크박스
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clickable {
                                                scope.launch {
                                                    AuthManager.setAutoLoginEnabled(ctx, !autoLoginEnabled)
                                                }
                                            }
                                    ) {
                                        Checkbox(
                                            checked = autoLoginEnabled,
                                            onCheckedChange = { isChecked ->
                                                scope.launch {
                                                    AuthManager.setAutoLoginEnabled(ctx, isChecked)
                                                }
                                            },
                                            colors = CheckboxDefaults.colors(checkedColor = brandAccent)
                                        )
                                        Text("자동 로그인", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }

                                    Spacer(modifier = Modifier.width(16.dp))

                                    // 아이디 저장 체크박스
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clickable {
                                                scope.launch {
                                                    AuthManager.setRememberIdEnabled(ctx, !rememberIdEnabled)
                                                }
                                            }
                                    ) {
                                        Checkbox(
                                            checked = rememberIdEnabled,
                                            onCheckedChange = { isChecked ->
                                                scope.launch {
                                                    AuthManager.setRememberIdEnabled(ctx, isChecked)
                                                }
                                            },
                                            colors = CheckboxDefaults.colors(checkedColor = brandAccent)
                                        )
                                        Text("아이디 저장", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }

                            // 3) 비밀번호 확인 필드 (회원가입 모드일 때만 노출)
                            if (selectedTab == AuthTab.REGISTER) {
                                Column {
                                    Text("비밀번호 확인", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = confirmPassword,
                                        onValueChange = { confirmPassword = it },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        visualTransformation = if (isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        placeholder = { Text("비밀번호를 한 번 더 입력하세요") },
                                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Confirm Password") },
                                        trailingIcon = {
                                            IconButton(onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible }) {
                                                Icon(
                                                    imageVector = if (isConfirmPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                                    contentDescription = "비밀번호 표시 토글"
                                                )
                                            }
                                        },
                                        textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = brandAccent,
                                            focusedLabelColor = brandAccent,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                            unfocusedContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                            focusedContainerColor = MaterialTheme.colorScheme.background
                                        )
                                    )
                                    if (currentPassword.isNotEmpty() && confirmPassword.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        if (currentPassword == confirmPassword) {
                                            Text("비밀번호가 일치합니다. ✓", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                        } else {
                                            Text("비밀번호가 일치하지 않습니다. ⚠️", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }

                        // 4) 사용자 닉네임 필드 (회원가입 모드 또는 정보수정 모드일 때 노출)
                        if (!isUserGuest || selectedTab == AuthTab.REGISTER) {
                            Column {
                                Text("사용자 닉네임", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = currentNickname,
                                    onValueChange = { currentNickname = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    placeholder = { Text("닉네임을 입력하세요 (예: 꿀딜탐색기)") },
                                    leadingIcon = { Icon(Icons.Default.Face, contentDescription = "Nickname") },
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = brandAccent,
                                        focusedLabelColor = brandAccent,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                        unfocusedContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                        focusedContainerColor = MaterialTheme.colorScheme.background
                                    )
                                )
                            }
                        }
                    }

                    // 🔒 [정보 수정 모드일 때] 비밀번호 변경용 접이식 서브 폼 제공
                    if (!isUserGuest) {
                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                        Spacer(modifier = Modifier.height(14.dp))
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { 
                                    showPasswordFields = !showPasswordFields 
                                    if (!showPasswordFields) {
                                        // 접힐 때 모든 입력 필드 및 검증 상태 안전하게 초기화
                                        oldPassword = ""
                                        currentPassword = ""
                                        confirmPassword = ""
                                        isCurrentPasswordVerified = false
                                        currentPasswordErrorText = ""
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("🔒", fontSize = 14.sp)
                                Text(
                                    text = "비밀번호 변경",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = brandAccent
                                )
                            }
                            Text(
                                text = if (showPasswordFields) "접기 ▲" else "펼치기 ▼",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        if (showPasswordFields) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                // 1) 현재 비밀번호 입력 및 확인 버튼
                                Column {
                                    Text("현재 비밀번호", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = oldPassword,
                                            onValueChange = { 
                                                oldPassword = it 
                                                currentPasswordErrorText = ""
                                            },
                                            modifier = Modifier.weight(1f),
                                            singleLine = true,
                                            enabled = !isCurrentPasswordVerified,
                                            visualTransformation = if (isOldPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                            placeholder = { Text("현재 비밀번호를 입력하세요") },
                                            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Current Password") },
                                            trailingIcon = {
                                                if (!isCurrentPasswordVerified) {
                                                    IconButton(onClick = { isOldPasswordVisible = !isOldPasswordVisible }) {
                                                        Icon(
                                                            imageVector = if (isOldPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                                            contentDescription = "비밀번호 표시 토글"
                                                        )
                                                    }
                                                }
                                            },
                                            textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = brandAccent,
                                                focusedLabelColor = brandAccent,
                                                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                                unfocusedContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                                focusedContainerColor = MaterialTheme.colorScheme.background
                                            )
                                        )
                                        
                                        Button(
                                            onClick = {
                                                if (oldPassword.isBlank()) {
                                                    currentPasswordErrorText = "비밀번호를 입력해 주세요. ⚠️"
                                                    return@Button
                                                }
                                                scope.launch {
                                                    val isMatch = AuthManager.checkUserCredentials(ctx, currentUserId, oldPassword)
                                                    if (isMatch) {
                                                        isCurrentPasswordVerified = true
                                                        currentPasswordErrorText = ""
                                                    } else {
                                                        isCurrentPasswordVerified = false
                                                        currentPasswordErrorText = "비밀번호가 일치하지 않습니다. 다시 입력해 주세요. ⚠️"
                                                    }
                                                }
                                            },
                                            enabled = !isCurrentPasswordVerified && oldPassword.isNotEmpty(),
                                            shape = RoundedCornerShape(12.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = brandAccent,
                                                contentColor = MaterialTheme.colorScheme.onPrimary
                                            ),
                                            modifier = Modifier.height(56.dp)
                                        ) {
                                            Text(
                                                text = if (isCurrentPasswordVerified) "확인 완료 ✓" else "확인",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp
                                            )
                                        }
                                    }
                                    
                                    if (currentPasswordErrorText.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(currentPasswordErrorText, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                    } else if (isCurrentPasswordVerified) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text("현재 비밀번호가 성공적으로 확인되었습니다. ✓", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                    }
                                }

                                // ⚡ [Wow-Factor] 현재 비밀번호가 성공적으로 검증되었을 때만 새 비밀번호 필드를 스무스하게 슬라이드 오픈
                                AnimatedVisibility(
                                    visible = isCurrentPasswordVerified,
                                    enter = expandVertically() + fadeIn(),
                                    exit = shrinkVertically() + fadeOut()
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                        // 2) 새 비밀번호
                                        Column {
                                            Text("새 비밀번호", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(modifier = Modifier.height(6.dp))
                                            OutlinedTextField(
                                                value = currentPassword,
                                                onValueChange = { currentPassword = it },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true,
                                                visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                                placeholder = { Text("새로운 비밀번호를 입력하세요") },
                                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "New Password") },
                                                trailingIcon = {
                                                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                                                        Icon(
                                                            imageVector = if (isPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                                            contentDescription = "비밀번호 표시 토글"
                                                        )
                                                    }
                                                },
                                                textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = brandAccent,
                                                    focusedLabelColor = brandAccent,
                                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                                    unfocusedContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                                    focusedContainerColor = MaterialTheme.colorScheme.background
                                                )
                                            )
                                        }

                                        // 3) 새 비밀번호 확인
                                        Column {
                                            Text("새 비밀번호 확인", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(modifier = Modifier.height(6.dp))
                                            OutlinedTextField(
                                                value = confirmPassword,
                                                onValueChange = { confirmPassword = it },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true,
                                                visualTransformation = if (isConfirmPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                                placeholder = { Text("새 비밀번호를 한 번 더 입력하세요") },
                                                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = "Confirm Password") },
                                                trailingIcon = {
                                                    IconButton(onClick = { isConfirmPasswordVisible = !isConfirmPasswordVisible }) {
                                                        Icon(
                                                            imageVector = if (isConfirmPasswordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                                            contentDescription = "비밀번호 표시 토글"
                                                        )
                                                    }
                                                },
                                                textStyle = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                                shape = RoundedCornerShape(12.dp),
                                                colors = OutlinedTextFieldDefaults.colors(
                                                    focusedBorderColor = brandAccent,
                                                    focusedLabelColor = brandAccent,
                                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                                    unfocusedContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                                    focusedContainerColor = MaterialTheme.colorScheme.background
                                                )
                                            )
                                            if (currentPassword.isNotEmpty() && confirmPassword.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(4.dp))
                                                if (currentPassword == confirmPassword) {
                                                    Text("비밀번호가 일치합니다. ✓", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                                } else {
                                                    Text("비밀번호가 일치하지 않습니다. ⚠️", fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (currentUserId.isBlank()) {
                                android.widget.Toast.makeText(ctx, "아이디를 입력해 주세요. ⚠️", android.widget.Toast.LENGTH_SHORT).show()
                                return@Button
                            }

                            scope.launch {
                                if (!isUserGuest) {
                                    // ⚡ 정보 수정 시나리오
                                    if (currentNickname.isBlank()) {
                                        android.widget.Toast.makeText(ctx, "닉네임을 입력해 주세요. ⚠️", android.widget.Toast.LENGTH_SHORT).show()
                                        return@launch
                                    }
                                    
                                    val isModifyingPassword = oldPassword.isNotEmpty() || currentPassword.isNotEmpty() || confirmPassword.isNotEmpty()
                                    
                                    if (isModifyingPassword) {
                                        if (!isCurrentPasswordVerified) {
                                            android.widget.Toast.makeText(ctx, "현재 비밀번호 확인을 완료해 주세요. ⚠️", android.widget.Toast.LENGTH_SHORT).show()
                                            return@launch
                                        }
                                        
                                        if (currentPassword.isBlank()) {
                                            android.widget.Toast.makeText(ctx, "새 비밀번호를 입력해 주세요. ⚠️", android.widget.Toast.LENGTH_SHORT).show()
                                            return@launch
                                        }
                                        
                                        if (currentPassword != confirmPassword) {
                                            android.widget.Toast.makeText(ctx, "새 비밀번호가 서로 일치하지 않습니다. ⚠️", android.widget.Toast.LENGTH_SHORT).show()
                                            return@launch
                                        }
                                        AuthManager.saveUserWithPassword(ctx, currentUserId, currentNickname, currentPassword)
                                        android.widget.Toast.makeText(ctx, "회원 정보 및 비밀번호가 안전하게 변경되었습니다. 🔐", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        AuthManager.saveUser(ctx, currentUserId, currentNickname)
                                        android.widget.Toast.makeText(ctx, "회원 정보가 성공적으로 저장되었습니다. 💾", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                    onBackClick()
                                } else if (selectedTab == AuthTab.LOGIN) {
                                    // ⚡ 로그인 시나리오
                                    if (currentPassword.isBlank()) {
                                        android.widget.Toast.makeText(ctx, "비밀번호를 입력해 주세요. ⚠️", android.widget.Toast.LENGTH_SHORT).show()
                                        return@launch
                                    }
                                    val isMatch = AuthManager.checkUserCredentials(ctx, currentUserId, currentPassword)
                                    if (isMatch) {
                                        // 로그인 성공 시 세션 복구 및 마이페이지 워프
                                        val nicknameKey = if (currentUserId == "admin") "최고운영자" else "합리적쇼핑꾼"
                                        AuthManager.saveUser(ctx, currentUserId, nicknameKey)
                                        android.widget.Toast.makeText(ctx, "로그인에 성공했습니다! 🎉", android.widget.Toast.LENGTH_SHORT).show()
                                        onBackClick()
                                    } else {
                                        android.widget.Toast.makeText(ctx, "아이디 또는 비밀번호가 일치하지 않습니다. ⚠️", android.widget.Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    // ⚡ 회원가입 시나리오
                                    if (currentPassword.isBlank() || currentNickname.isBlank()) {
                                        android.widget.Toast.makeText(ctx, "비밀번호와 닉네임을 모두 입력해 주세요. ⚠️", android.widget.Toast.LENGTH_SHORT).show()
                                        return@launch
                                    }
                                    if (currentPassword != confirmPassword) {
                                        android.widget.Toast.makeText(ctx, "비밀번호가 서로 일치하지 않습니다. 확인해 주세요. ⚠️", android.widget.Toast.LENGTH_SHORT).show()
                                        return@launch
                                    }
                                    
                                    // ID 중복 가입 체크
                                    val isRegistered = AuthManager.isUserRegistered(ctx, currentUserId)
                                    if (isRegistered) {
                                        android.widget.Toast.makeText(ctx, "이미 가입된 사용자 ID입니다. 다른 ID를 사용해 주세요. ⚠️", android.widget.Toast.LENGTH_LONG).show()
                                        return@launch
                                    }

                                    // 신규 가입 처리 및 자동 로그인 세션 연동
                                    AuthManager.saveUserWithPassword(ctx, currentUserId, currentNickname, currentPassword)
                                    android.widget.Toast.makeText(ctx, "회원가입 및 로그인이 완료되었습니다! 🎉", android.widget.Toast.LENGTH_SHORT).show()
                                    onBackClick()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(52.dp)
                    ) {
                        Text(
                            text = if (!isUserGuest) "변경 사항 저장" else if (selectedTab == AuthTab.LOGIN) "로그인" else "회원가입 완료",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    if (isUserGuest && selectedTab == AuthTab.LOGIN) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { socialAgreed = !socialAgreed }
                        ) {
                            Checkbox(
                                checked = socialAgreed,
                                onCheckedChange = { socialAgreed = it },
                                colors = CheckboxDefaults.colors(checkedColor = brandAccent)
                            )
                            Text(
                                text = "서비스 이용약관 및 개인정보 제3자 제공 동의 (필수)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // 카카오 로그인
                            Button(
                                onClick = {
                                    if (!socialAgreed) {
                                        android.widget.Toast.makeText(ctx, "필수 약관에 동의하셔야 소셜 로그인이 가능합니다. ⚠️", android.widget.Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    scope.launch {
                                        AuthManager.saveUser(ctx, "kakao_94821", "카카오쇼핑꾼")
                                        android.widget.Toast.makeText(ctx, "카카오 간편 로그인 성공! 🎉", android.widget.Toast.LENGTH_SHORT).show()
                                        onBackClick()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFFEE500),
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(50.dp)
                            ) {
                                Text("💬 카카오 로그인", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }

                            // 네이버 로그인
                            Button(
                                onClick = {
                                    if (!socialAgreed) {
                                        android.widget.Toast.makeText(ctx, "필수 약관에 동의하셔야 소셜 로그인이 가능합니다. ⚠️", android.widget.Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    scope.launch {
                                        AuthManager.saveUser(ctx, "naver_38291", "네이버쇼핑꾼")
                                        android.widget.Toast.makeText(ctx, "네이버 간편 로그인 성공! 🎉", android.widget.Toast.LENGTH_SHORT).show()
                                        onBackClick()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF03C75A),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(50.dp)
                            ) {
                                Text("💚 네이버 로그인", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                        }
                    }

                    // ⚡ [정보 수정 상태인 경우] 카드 내부에 자동 로그인 & 아이디 저장 소형 체크박스 통합 제공
                    if (!isUserGuest) {
                        Spacer(modifier = Modifier.height(20.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Text(
                            text = "로그인 정책 설정",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = brandAccent
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            // 자동 로그인 체크박스
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable {
                                        scope.launch {
                                            AuthManager.setAutoLoginEnabled(ctx, !autoLoginEnabled)
                                        }
                                    }
                            ) {
                                Checkbox(
                                    checked = autoLoginEnabled,
                                    onCheckedChange = { isChecked ->
                                        scope.launch {
                                            AuthManager.setAutoLoginEnabled(ctx, isChecked)
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = brandAccent)
                                )
                                Text("자동 로그인", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }

                            Spacer(modifier = Modifier.width(20.dp))

                            // 아이디 저장 체크박스
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clickable {
                                        scope.launch {
                                            AuthManager.setRememberIdEnabled(ctx, !rememberIdEnabled)
                                        }
                                    }
                            ) {
                                Checkbox(
                                    checked = rememberIdEnabled,
                                    onCheckedChange = { isChecked ->
                                        scope.launch {
                                            AuthManager.setRememberIdEnabled(ctx, isChecked)
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = brandAccent)
                                )
                                Text("아이디 저장", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    
                    // 🔒 [Wow-Factor] 회원 정보 수정 모드일 때 하단 은밀한 회원탈퇴 트리거 배치
                    if (!isUserGuest) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "회원 탈퇴",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                style = androidx.compose.ui.text.TextStyle(
                                    color = Color(0xFFFF9800), // 프리미엄 주황색(오렌지) 톤 수혈
                                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline // 명확한 액션 단추 느낌 부여
                                ),
                                modifier = Modifier
                                    .clickable {
                                        withdrawPasswordInput = ""
                                        isWithdrawPasswordVisible = false
                                        showWithdrawPasswordDialog = true
                                    }
                                    .padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // 1단계: 본인 확인용 비밀번호 입력 다이얼로그
    if (showWithdrawPasswordDialog) {
        AlertDialog(
            onDismissRequest = { showWithdrawPasswordDialog = false },
            title = { Text("회원 탈퇴 - 비밀번호 확인", fontWeight = FontWeight.Bold, color = Color(0xFFFF9800)) }, // 타이틀 주황색
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "본인 확인 및 안전한 탈퇴를 위해 현재 계정의 비밀번호를 입력해 주세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = withdrawPasswordInput,
                        onValueChange = { withdrawPasswordInput = it },
                        label = { Text("비밀번호", color = Color(0xFFFF9800)) }, // 라벨 주황색
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (isWithdrawPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { isWithdrawPasswordVisible = !isWithdrawPasswordVisible }) {
                                Icon(
                                    imageVector = if (isWithdrawPasswordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (isWithdrawPasswordVisible) "비밀번호 숨기기" else "비밀번호 표시",
                                    tint = Color(0xFFFF9800) // 아이콘 주황색
                                )
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFFF9800), // 포커스 보더 주황색
                            focusedLabelColor = Color(0xFFFF9800),
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                            focusedContainerColor = MaterialTheme.colorScheme.background
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (withdrawPasswordInput.isBlank()) {
                            android.widget.Toast.makeText(ctx, "비밀번호를 입력해 주세요.", android.widget.Toast.LENGTH_SHORT).show()
                        } else {
                            showWithdrawPasswordDialog = false
                            showWithdrawConfirmDialog = true
                        }
                    }
                ) {
                    Text("다음", fontWeight = FontWeight.Bold, color = Color(0xFFFF9800)) // 버튼 텍스트 주황색
                }
            },
            dismissButton = {
                TextButton(onClick = { showWithdrawPasswordDialog = false }) {
                    Text("취소", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                }
            }
        )
    }

    // 2단계: 최종 오동작 방지 탈퇴 컨펌 다이얼로그
    if (showWithdrawConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showWithdrawConfirmDialog = false },
            title = { Text("회원 탈퇴 최종 확인", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error) },
            text = { 
                Text(
                    "정말 탈퇴하시겠습니까?\n\n" +
                    "탈퇴 즉시 백엔드 데이터베이스의 회원 정보가 완전히 삭제(DB 반영)되며, " +
                    "기기에 저장된 위시리스트, 최근 본 내역 및 모든 설정 데이터가 영구적으로 파기되어 다시는 복구할 수 없습니다.",
                    style = MaterialTheme.typography.bodyMedium
                ) 
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showWithdrawConfirmDialog = false
                        scope.launch {
                            try {
                                val apiService = com.ddaeany0919.insightdeal.network.NetworkModule.createService<com.ddaeany0919.insightdeal.network.ApiService>()
                                val response = apiService.withdrawUser(
                                    UserWithdrawRequest(
                                        username = savedUsername ?: "guest",
                                        password = withdrawPasswordInput
                                    )
                                )
                                
                                if (response.isSuccessful && response.body()?.success == true) {
                                    // 백엔드 영구 DB 삭제 대성공 시 로컬 자격증명 및 데이터 클리어
                                    AuthManager.logout(ctx)
                                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                        com.ddaeany0919.insightdeal.local.db.AppDatabase.getDatabase(ctx).clearAllTables()
                                    }
                                    com.ddaeany0919.insightdeal.presentation.mypage.history.RecentDealManager.clearRecentDeals(ctx)
                                    
                                    val appPrefs = ctx.getSharedPreferences("app", android.content.Context.MODE_PRIVATE)
                                    val dealPrefs = ctx.getSharedPreferences("deals", android.content.Context.MODE_PRIVATE)
                                    appPrefs.edit().clear().apply()
                                    dealPrefs.edit().clear().apply()
                                    
                                    android.widget.Toast.makeText(ctx, "회원 탈퇴가 완료되어 DB에 실시간 반영되었습니다. 앱을 안전하게 종료합니다.", android.widget.Toast.LENGTH_LONG).show()
                                    
                                    (ctx as? android.app.Activity)?.finishAffinity()
                                    android.os.Process.killProcess(android.os.Process.myPid())
                                } else {
                                    val errorMsg = response.body()?.message ?: "비밀번호가 올바르지 않거나 탈퇴 요청 처리에 실패했습니다."
                                    android.widget.Toast.makeText(ctx, errorMsg, android.widget.Toast.LENGTH_LONG).show()
                                }
                            } catch (e: Exception) {
                                android.util.Log.e("AuthScreen", "회원 탈퇴 네트워크 통신 실패", e)
                                android.widget.Toast.makeText(ctx, "네트워크 통신 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("탈퇴", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWithdrawConfirmDialog = false }) {
                    Text("취소")
                }
            }
        )
    }
}
