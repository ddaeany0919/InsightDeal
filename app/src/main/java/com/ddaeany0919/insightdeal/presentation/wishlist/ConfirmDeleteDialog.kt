package com.ddaeany0919.insightdeal.presentation.wishlist

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier

@Composable
fun ConfirmDeleteDialog(
    keyword: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("삭제 확인") },
        text = { Text("'${'$'}keyword' 항목을 삭제하시겠습니까?") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("삭제") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("취소") }
        },
        modifier = Modifier.fillMaxWidth()
    )
}
