package com.ddaeany0919.insightdeal.presentation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeywordManagerScreen(
    onNavigateBack: () -> Unit,
    viewModel: KeywordManagerViewModel = viewModel()
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    
    // ViewModel StateFlow 구독
    val keywords by viewModel.keywords.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    val dndEnabled by viewModel.dndEnabled.collectAsState()
    val dndStartTime by viewModel.dndStartTime.collectAsState()
    val dndEndTime by viewModel.dndEndTime.collectAsState()

    // 🗓️ 요일별 DND 설정을 위한 StateFlow 구독
    val weekDndSettings by viewModel.weekDndSettings.collectAsState()
    var selectedDayKey by remember { mutableStateOf("mon") }

    var keywordInput by remember { mutableStateOf("") }
    
    // 시간 피커 다이얼로그용 상태
    var showTimePickerDialog by remember { mutableStateOf(false) }
    // timePickerTarget: "global_start", "global_end", "day_start_[dayKey]", "day_end_[dayKey]"
    var timePickerTarget by remember { mutableStateOf("global_start") }

    // 권한 관련 상태 및 다이얼로그 제어
    var showPermissionGuideDialog by remember { mutableStateOf(false) }

    // Android 13+ 알림 런타임 권한 요청 런처
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Toast.makeText(context, "🔔 알림 수신 권한이 허용되었습니다.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "⚠️ 알림 권한이 거부되었습니다. 설정에서 직접 허용할 수 있습니다.", Toast.LENGTH_LONG).show()
        }
    }

    // 권한 여부 체크 함수
    fun hasNotificationPermission(ctx: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                ctx,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    // 최초 진입 시 알림 권한 체크 후 없으면 Toss 스타일 설득 다이얼로그 팝업
    LaunchedEffect(Unit) {
        if (!hasNotificationPermission(context)) {
            showPermissionGuideDialog = true
        }
    }

    // 에러 발생 시 알림 다이얼로그
    if (errorMessage != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("알림", fontWeight = FontWeight.Bold) },
            text = { Text(errorMessage!!) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) { Text("확인") }
            }
        )
    }

    // Toss 스타일 프리미엄 알림 권한 가이드 다이얼로그 (유저 설득형)
    if (showPermissionGuideDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionGuideDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        showPermissionGuideDialog = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("알림 켜기", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPermissionGuideDialog = false },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "나중에 설정할게요",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        fontSize = 14.sp
                    )
                }
            },
            title = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF3A7BD5),
                                        Color(0xFF3A6073)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "특가 타이밍을 가장 먼저 알려드릴게요",
                        fontWeight = FontWeight.Black,
                        fontSize = 18.sp,
                        textAlign = TextAlign.Center,
                        lineHeight = 24.sp
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(
                        text = "알림을 허용하시면 이런 혜택을 드려요.",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    // 혜택 리스트
                    val benefits = listOf(
                        "📌 등록한 키워드의 초특가 딜 실시간 매칭 알림",
                        "🌙 방해금지(DND) 설정으로 수면 시간 수신 완벽 차단",
                        "🔥 품절 대란 핫딜 정보 선착순 1초 컷 진입 보장"
                    )
                    benefits.forEach { benefit ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = benefit,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // 법적 고지 문구
                    Text(
                        text = "본 알림 서비스는 정보통신망법(제50조)을 준수하며, 야간 시간대(21:00 ~ 익일 08:00)의 광고성 푸시는 수신 동의 여부와 관계없이 설정된 DND 규칙에 따라 완전히 필터링됩니다.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        lineHeight = 16.sp,
                        textAlign = TextAlign.Center
                    )
                }
            },
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }

    // Material 3 TimePickerDialog 구현
    if (showTimePickerDialog) {
        val initialTimeStr = when {
            timePickerTarget == "global_start" -> dndStartTime
            timePickerTarget == "global_end" -> dndEndTime
            timePickerTarget.startsWith("day_start_") -> {
                val dayKey = timePickerTarget.removePrefix("day_start_")
                getDaySetting(weekDndSettings, dayKey).start
            }
            timePickerTarget.startsWith("day_end_") -> {
                val dayKey = timePickerTarget.removePrefix("day_end_")
                getDaySetting(weekDndSettings, dayKey).end
            }
            else -> "22:00"
        }
        val parts = initialTimeStr.split(":")
        val initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 22
        val initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0

        val timePickerState = rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = false
        )

        AlertDialog(
            onDismissRequest = { showTimePickerDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        val formattedTime = String.format("%02d:%02d", timePickerState.hour, timePickerState.minute)
                        when {
                            timePickerTarget == "global_start" -> {
                                viewModel.updateDndSettings(enabled = dndEnabled, startTime = formattedTime, endTime = dndEndTime)
                            }
                            timePickerTarget == "global_end" -> {
                                viewModel.updateDndSettings(enabled = dndEnabled, startTime = dndStartTime, endTime = formattedTime)
                            }
                            timePickerTarget.startsWith("day_start_") -> {
                                val dayKey = timePickerTarget.removePrefix("day_start_")
                                val currentSetting = getDaySetting(weekDndSettings, dayKey)
                                val updatedSetting = currentSetting.copy(start = formattedTime)
                                val newWeekSettings = updateWeekDndSetting(weekDndSettings, dayKey, updatedSetting)
                                viewModel.updateWeekDndSettings(newWeekSettings)
                            }
                            timePickerTarget.startsWith("day_end_") -> {
                                val dayKey = timePickerTarget.removePrefix("day_end_")
                                val currentSetting = getDaySetting(weekDndSettings, dayKey)
                                val updatedSetting = currentSetting.copy(end = formattedTime)
                                val newWeekSettings = updateWeekDndSetting(weekDndSettings, dayKey, updatedSetting)
                                viewModel.updateWeekDndSettings(newWeekSettings)
                            }
                        }
                        showTimePickerDialog = false
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("설정 완료")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePickerDialog = false }) {
                    Text("취소")
                }
            },
            title = {
                Text(
                    text = when {
                        timePickerTarget == "global_start" -> "방해금지 시작 시간"
                        timePickerTarget == "global_end" -> "방해금지 종료 시간"
                        timePickerTarget.startsWith("day_start_") -> {
                            val dayKey = timePickerTarget.removePrefix("day_start_")
                            val dayName = getDayNameKor(dayKey)
                            ("${dayName}요일 방해금지 시작 시간")
                        }
                        timePickerTarget.startsWith("day_end_") -> {
                            val dayKey = timePickerTarget.removePrefix("day_end_")
                            val dayName = getDayNameKor(dayKey)
                            ("${dayName}요일 방해금지 종료 시간")
                        }
                        else -> "시간 설정"
                    },
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    TimePicker(state = timePickerState)
                }
            },
            shape = RoundedCornerShape(24.dp)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("키워드 및 DND 관리", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로가기")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // LazyColumn으로 감싸 스크롤이 가능하게 구조적 확장 (키보드 팝업이나 요일별 상세 추가로 인한 오버플로우 방지)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                // 💎 Toss 스타일 프리미엄 방해금지(DND) 카드 레이아웃
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp)),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        border = CardDefaults.outlinedCardBorder(enabled = true)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "방해금지 시간대 설정 (DND)", 
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (dndEnabled) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(30.dp),
                                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(6.dp)
                                                            .clip(CircleShape)
                                                            .background(Color(0xFF34C759))
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "실시간 서버 동기화 완료",
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "설정된 시간 동안에는 스마트 푸시 알림 발송을 서버에서 차단합니다.",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = dndEnabled,
                                    onCheckedChange = {
                                        viewModel.updateDndSettings(enabled = it, startTime = dndStartTime, endTime = dndEndTime)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                    )
                                )
                            }

                            // DND 활성화 시 부드러운 시작/종료 시간 조절 칩 노출
                            AnimatedVisibility(
                                visible = dndEnabled,
                                enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                                exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
                            ) {
                                Column {
                                    HorizontalDivider(
                                        modifier = Modifier.padding(vertical = 16.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                    )
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        // 시작 시간 조절 캡슐 버튼
                                        TimeCapsule(
                                            label = "시작 시간",
                                            time = dndStartTime,
                                            onClick = {
                                                timePickerTarget = "global_start"
                                                showTimePickerDialog = true
                                            }
                                        )

                                        Text(
                                            text = "부터",
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )

                                        // 종료 시간 조절 캡슐 버튼
                                        TimeCapsule(
                                            label = "종료 시간",
                                            time = dndEndTime,
                                            onClick = {
                                                timePickerTarget = "global_end"
                                                showTimePickerDialog = true
                                            }
                                        )

                                        Text(
                                            text = "까지",
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 🗓️ 요일별 세부 DND 설정 카드 (전체 DND가 활성화되었을 때만 우아하게 슬라이드 노출)
                item {
                    AnimatedVisibility(
                        visible = dndEnabled,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp)),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            border = CardDefaults.outlinedCardBorder(enabled = true)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp)
                            ) {
                                Text(
                                    text = "요일별 개별 시간 설정",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "특정 요일만 알림을 차단하거나, 요일마다 다르게 시간을 지정하세요.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(12.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    val templates = listOf(
                                        "weekdays" to "평일만 💤",
                                        "weekends" to "주말만 ☕",
                                        "everyday" to "매일 ⏰",
                                        "clear" to "해제 🔓"
                                    )
                                    templates.forEach { (type, label) ->
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
                                                .border(
                                                    width = 1.dp,
                                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .bounceClick {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    viewModel.applyDndTemplate(type)
                                                }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                val days = listOf(
                                    "mon" to "월",
                                    "tue" to "화",
                                    "wed" to "수",
                                    "thu" to "목",
                                    "fri" to "금",
                                    "sat" to "토",
                                    "sun" to "일"
                                )
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    days.forEach { (dayKey, dayName) ->
                                        val isSelected = selectedDayKey == dayKey
                                        val daySetting = getDaySetting(weekDndSettings, dayKey)
                                        val isDayEnabled = daySetting.enabled
                                        
                                        // 프리미엄 룩앤필의 둥근 모서리 요일 칩셋
                                        Box(
                                            modifier = Modifier
                                                .size(38.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(
                                                    when {
                                                        isSelected -> MaterialTheme.colorScheme.primary
                                                        isDayEnabled -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
                                                    }
                                                )
                                                .border(
                                                    width = 1.dp,
                                                    color = when {
                                                        isSelected -> MaterialTheme.colorScheme.primary
                                                        isDayEnabled -> MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                                                        else -> Color.Transparent
                                                    },
                                                    shape = RoundedCornerShape(12.dp)
                                                )
                                                .bounceClick {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    selectedDayKey = dayKey
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.Center
                                            ) {
                                                Text(
                                                    text = dayName,
                                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = when {
                                                        isSelected -> MaterialTheme.colorScheme.onPrimary
                                                        isDayEnabled -> MaterialTheme.colorScheme.primary
                                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                    }
                                                )
                                                if (isDayEnabled && !isSelected) {
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .size(4.dp)
                                                            .clip(RoundedCornerShape(2.dp))
                                                            .background(MaterialTheme.colorScheme.primary)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
                                )
                                
                                val selectedDaySetting = getDaySetting(weekDndSettings, selectedDayKey)
                                val selectedDayName = getDayNameKor(selectedDayKey)
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${selectedDayName}요일 알림 차단 예약",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    
                                    Switch(
                                        checked = selectedDaySetting.enabled,
                                        onCheckedChange = { isChecked ->
                                            val updated = selectedDaySetting.copy(enabled = isChecked)
                                            val newSettings = updateWeekDndSetting(weekDndSettings, selectedDayKey, updated)
                                            viewModel.updateWeekDndSettings(newSettings)
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                        )
                                    )
                                }
                                
                                AnimatedVisibility(
                                    visible = selectedDaySetting.enabled,
                                    enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
                                    exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
                                ) {
                                    Column {
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceEvenly
                                        ) {
                                            TimeCapsule(
                                                label = "시작 시간",
                                                time = selectedDaySetting.start,
                                                onClick = {
                                                    timePickerTarget = "day_start_$selectedDayKey"
                                                    showTimePickerDialog = true
                                                }
                                            )
                                            
                                            Text(
                                                text = "부터",
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            
                                            TimeCapsule(
                                                label = "종료 시간",
                                                time = selectedDaySetting.end,
                                                onClick = {
                                                    timePickerTarget = "day_end_$selectedDayKey"
                                                    showTimePickerDialog = true
                                                }
                                            )
                                            
                                            Text(
                                                text = "까지",
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 14.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 키워드 입력 및 추가 영역
                item {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "알림 키워드 등록",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = keywordInput,
                                onValueChange = { keywordInput = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("키워드를 입력해주세요 (예: 에어팟)") },
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                ),
                                enabled = !isLoading
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Button(
                                onClick = {
                                    if (keywordInput.isNotBlank()) {
                                        viewModel.addKeyword(keywordInput.trim())
                                        keywordInput = ""
                                    }
                                },
                                modifier = Modifier.height(56.dp),
                                shape = RoundedCornerShape(14.dp),
                                enabled = !isLoading
                            ) {
                                if (isLoading) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Add, contentDescription = "추가")
                                }
                            }
                        }
                    }
                }

                // 🌟 추천 키워드 가로 스크롤 칩 (LazyRow) 배치 및 마이크로 인터랙션
                item {
                    val recommendedKeywords = listOf("아이폰", "갤럭시", "PS5", "닌텐도", "그래픽카드", "모니터", "닭가슴살", "캠핑")
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(recommendedKeywords) { keyword ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(30.dp))
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f))
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(30.dp)
                                    )
                                    .bounceClick {
                                        keywordInput = keyword
                                    }
                                    .padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "#$keyword",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                // 등록된 키워드 제목
                item {
                    Text(
                        text = "등록된 알림 키워드 (${keywords.size}개)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                }

                // 키워드 리스트 (LazyColumn의 중첩 스크롤 방지를 위해, LazyColumn의 하위 item으로 KeywordItem을 Loop 렌더링)
                if (keywords.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "등록된 키워드가 없습니다.\n관심 있는 핫딜 키워드를 추가해보세요!",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    items(keywords, key = { it.id }) { keyword ->
                        KeywordItem(
                            keyword = keyword.keyword,
                            onDelete = { viewModel.deleteKeyword(keyword.keyword) },
                            isEnabled = !isLoading
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TimeCapsule(
    label: String,
    time: String,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
            .bounceClick { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = time,
            fontSize = 16.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun KeywordItem(keyword: String, onDelete: () -> Unit, isEnabled: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = keyword,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        
        IconButton(
            onClick = onDelete,
            enabled = isEnabled,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "삭제",
                tint = if (isEnabled) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// 헬퍼 유틸리티 함수들 정의
private fun getDaySetting(
    settings: KeywordManagerViewModel.WeekDndSettings,
    dayKey: String
): KeywordManagerViewModel.DayDndSetting {
    return when (dayKey) {
        "mon" -> settings.mon
        "tue" -> settings.tue
        "wed" -> settings.wed
        "thu" -> settings.thu
        "fri" -> settings.fri
        "sat" -> settings.sat
        "sun" -> settings.sun
        else -> KeywordManagerViewModel.DayDndSetting()
    }
}

private fun updateWeekDndSetting(
    settings: KeywordManagerViewModel.WeekDndSettings,
    dayKey: String,
    newSetting: KeywordManagerViewModel.DayDndSetting
): KeywordManagerViewModel.WeekDndSettings {
    return when (dayKey) {
        "mon" -> settings.copy(mon = newSetting)
        "tue" -> settings.copy(tue = newSetting)
        "wed" -> settings.copy(wed = newSetting)
        "thu" -> settings.copy(thu = newSetting)
        "fri" -> settings.copy(fri = newSetting)
        "sat" -> settings.copy(sat = newSetting)
        "sun" -> settings.copy(sun = newSetting)
        else -> settings
    }
}

private fun getDayNameKor(dayKey: String): String {
    return when (dayKey) {
        "mon" -> "월"
        "tue" -> "화"
        "wed" -> "수"
        "thu" -> "목"
        "fri" -> "금"
        "sat" -> "토"
        "sun" -> "일"
        else -> ""
    }
}
