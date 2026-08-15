package com.example.lifeos.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Adaptive Glassmorphism modifier that adjusts contrast based on Light/Dark Mode.
 */
fun Modifier.glassCard(
    cornerRadius: Dp = 20.dp
): Modifier = this.composed {
    // Detect light vs dark by observing background color
    val isDark = MaterialTheme.colorScheme.background != Color(0xFFF5F7FA)
    
    val glassColor = if (isDark) {
        Color.White.copy(alpha = 0.12f)
    } else {
        Color.White.copy(alpha = 0.55f)
    }
    
    val borderColor = if (isDark) {
        Color.White.copy(alpha = 0.25f)
    } else {
        Color.Black.copy(alpha = 0.08f)
    }

    this
        .clip(RoundedCornerShape(cornerRadius))
        .background(glassColor)
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    borderColor,
                    borderColor.copy(alpha = 0.3f)
                )
            ),
            shape = RoundedCornerShape(cornerRadius)
        )
}

fun Modifier.glassSurface(
    cornerRadius: Dp = 24.dp
): Modifier = this.composed {
    val isDark = MaterialTheme.colorScheme.background != Color(0xFFF5F7FA)
    
    val glassColor = if (isDark) {
        Color.White.copy(alpha = 0.08f)
    } else {
        Color.White.copy(alpha = 0.65f)
    }
    
    val borderColor = if (isDark) {
        Color.White.copy(alpha = 0.2f)
    } else {
        Color.Black.copy(alpha = 0.06f)
    }

    this
        .clip(RoundedCornerShape(cornerRadius))
        .background(glassColor)
        .border(
            width = 0.5.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    borderColor,
                    borderColor.copy(alpha = 0.3f)
                )
            ),
            shape = RoundedCornerShape(cornerRadius)
        )
}
