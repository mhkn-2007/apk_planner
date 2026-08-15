package com.example.lifeos.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
// import androidx.compose.ui.text.font.Font
// import com.example.lifeos.R

// TODO: Add Vazirmatn font family once font files are added to res/font
// val Vazirmatn = FontFamily(
//     Font(R.font.vazirmatn_regular, FontWeight.Normal),
//     Font(R.font.vazirmatn_medium, FontWeight.Medium),
//     Font(R.font.vazirmatn_bold, FontWeight.Bold)
// )

val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default, // Replace with Vazirmatn
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default, // Replace with Vazirmatn
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default, // Replace with Vazirmatn
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
    )
)
