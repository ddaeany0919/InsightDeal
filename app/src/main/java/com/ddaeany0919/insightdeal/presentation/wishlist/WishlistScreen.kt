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
        // TODO: 기존 목록/카드 UI를 이곳으로 이관
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
                    Log.d(TAG_UI, "링크 추가 요청: url=$url target=$target")
                    // TODO: ViewModel.addFromLink 구현 후 연결
                    showAddDialog = false
                }
            }
        )
    }
}
