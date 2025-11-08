package com.ddaeany0919.insightdeal.presentation.wishlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AnimatedWishlistCard(
    visible: Boolean,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(visible = visible) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize()
                .padding(vertical = 6.dp, horizontal = 4.dp)
        ) {
            content()
        }
    }
}
