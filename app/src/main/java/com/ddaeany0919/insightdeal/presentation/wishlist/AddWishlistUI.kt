package com.ddaeany0919.insightdeal.presentation.wishlist

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun AddWishlistFab(onAdd: (String, Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    FloatingActionButton(onClick = { open = true }) { Icon(Icons.Default.Add, contentDescription = "추가") }
    if (open) AddWishlistDialog(onDismiss = { open = false }) { k, p -> open = false; onAdd(k, p) }
}

@Composable
fun AddWishlistDialog(onDismiss: () -> Unit, onSubmit: (String, Int) -> Unit) {
    var keyword by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = keyword.isNotBlank() && target.toIntOrNull() != null,
                onClick = { onSubmit(keyword.trim(), target.toInt()) }
            ) { Text("추가") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
        title = { Text("관심상품 추가") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = keyword, onValueChange = { keyword = it }, label = { Text("키워드") })
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it.filter { ch -> ch.isDigit() } },
                    label = { Text("목표가") },
                    keyboardOptions = androidx.compose.ui.text.input.KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        }
    )
}
