package com.ddaeany0919.insightdeal.presentation.wishlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.KeyboardType

@Composable
fun AddWishlistUI(
    onDismiss: () -> Unit,
    onAdd: (String, String, Int) -> Unit
) {
    var keyword by remember { mutableStateOf("") }
    var productUrl by remember { mutableStateOf("") }
    var targetPrice by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("관심상품 추가") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = keyword,
                    onValueChange = { keyword = it },
                    label = { Text("상품명") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = productUrl,
                    onValueChange = { productUrl = it },
                    label = { Text("상품 URL (선택)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = targetPrice,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.all { char -> char.isDigit() }) {
                            targetPrice = newValue
                        }
                    },
                    label = { Text("목표 가격 (원)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val price = targetPrice.toIntOrNull()
                    if (keyword.isNotBlank() && price != null && price > 0) {
                        onAdd(keyword, productUrl, price)
                    }
                },
                enabled = keyword.isNotBlank() && targetPrice.toIntOrNull()?.let { it > 0 } == true
            ) {
                Text("추가")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("취소")
            }
        }
    )
}
