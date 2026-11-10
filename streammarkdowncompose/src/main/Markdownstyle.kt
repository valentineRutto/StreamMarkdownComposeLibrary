package com.valentinerutto.streammarkdowncompose

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Immutable
data class MarkdownStyle(
    val body: TextStyle,
    val headings: List<TextStyle>, // h1..h6
    val inlineCode: SpanStyle,
    val link: SpanStyle,
    val code: TextStyle,
    val codeLabel: TextStyle,
    val codeBackground: Color,
    val codeHeaderBackground: Color,
    val codeShape: Shape,
    val syntax: SyntaxColors,
    val quoteBar: Color,
    val blockSpacing: Dp,
)

@Composable
fun markdownStyle(): MarkdownStyle {
    val cs = MaterialTheme.colorScheme
    val ty = MaterialTheme.typography
    val dark = cs.background.luminance() < 0.5f
    return remember(cs, ty) {
        val bold = FontWeight.Bold
        MarkdownStyle(
            body = ty.bodyLarge.copy(color = cs.onSurface),
            headings = listOf(
                ty.headlineSmall.copy(fontWeight = bold, color = cs.onSurface),
                ty.titleLarge.copy(fontWeight = bold, color = cs.onSurface),
                ty.titleMedium.copy(fontWeight = bold, color = cs.onSurface),
                ty.titleSmall.copy(fontWeight = bold, color = cs.onSurface),
                ty.titleSmall.copy(fontWeight = bold, color = cs.onSurface),
                ty.titleSmall.copy(fontWeight = bold, color = cs.onSurface),
            ),
            inlineCode = SpanStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                background = cs.onSurface.copy(alpha = 0.10f),
            ),
            link = SpanStyle(color = cs.primary, textDecoration = TextDecoration.Underline),
            code = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp, color = cs.onSurface),
            codeLabel = ty.labelSmall.copy(color = cs.onSurfaceVariant),
            codeBackground = cs.surfaceVariant.copy(alpha = 0.55f),
            codeHeaderBackground = cs.surfaceVariant,
            codeShape = RoundedCornerShape(10.dp),
            syntax = if (dark) SyntaxColors(Color(0xFFCC7832), Color(0xFF6A8759), Color(0xFF808080), Color(0xFF6897BB))
            else SyntaxColors(Color(0xFF0033B3), Color(0xFF067D17), Color(0xFF8C8C8C), Color(0xFF1750EB)),
            quoteBar = cs.outline,
            blockSpacing = 10.dp,
        )
    }
}