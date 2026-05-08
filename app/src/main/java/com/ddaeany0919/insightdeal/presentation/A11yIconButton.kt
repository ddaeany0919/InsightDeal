package com.ddaeany0919.insightdeal.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * 🔎 접근성 보장 아이콘 버튼 (48dp 최소 터치 영역)
 */
@Composable
fun A11yIconButton(
    onClick: () -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(48.dp)
            .semantics {
                this.contentDescription = contentDescription
            },
        enabled = enabled,
        content = content
    )
}
