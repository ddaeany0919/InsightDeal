package com.ddaeany0919.insightdeal.presentation.wishlist

import android.util.Log
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch

private const val TAG_UI = "WishlistUI"

@Composable
fun WishlistScreenDetailed(
    viewModel: WishlistViewModel = viewModel()
) {
    var showAddDialog by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.loadWishlist() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                Log.d(TAG_UI, "+ 버튼 클릭됨: showAddDialog=true")
                showAddDialog = true
            }) { Icon(Icons.Filled.Add, contentDescription = "관심상품 추가") }
        }
    ) { inner ->
        // 기존 본문을 내부로 인라인하여 Unresolved reference: WishlistBody 제거
        WishlistScreenBody(uiState = uiState, modifier = Modifier.padding(inner), viewModel = viewModel, snackbarHostState = snackbarHostState)
    }

    if (showAddDialog) {
        AddWishlistDialogDetailed(
            onDismiss = { showAddDialog = false },
            onAdd = { keyword, target ->
                scope.launch {
                    viewModel.addItem(keyword, target)
                    showAddDialog = false
                }
            },
            onAddFromLink = { url, target ->
                scope.launch {
                    try {
                        // ViewModel에 링크 추가 메서드가 없다면 우선 키워드 기반으로 대체하거나 TODO 남김
                        // TODO: viewModel.addFromLink(url, target)
                        Log.d(TAG_UI, "링크 추가 요청: url=$url target=$target")
                        showAddDialog = false
                    } catch (e: Exception) {
                        Log.d(TAG_UI, "링크 추가 실패: ${e.message}")
                    }
                }
            }
        )
    }
}

@Composable
private fun WishlistScreenBody(
    uiState: WishlistState,
    modifier: Modifier,
    viewModel: WishlistViewModel,
    snackbarHostState: SnackbarHostState
) {
    // 기존 WishlistScreen 콘텐츠를 여기로 이동해 충돌 방지
    // TODO: 기존 LazyColumn/카드 렌더링 로직 붙여넣기
}
