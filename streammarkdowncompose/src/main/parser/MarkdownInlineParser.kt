package com.valentinerutto.streammarkdowncompose

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle

/**
 * Inline spans: `code`, **bold**, *italic*, ~~strike~~, [label](url).
 * A marker without its closing partner is emitted literally, so half-streamed
 * text like "**Hel" just shows as-is until the closer arrives.
 */
internal fun buildInline(src: String, style: MarkdownStyle): AnnotatedString =
    buildAnnotatedString { appendInline(src, style) }

private fun AnnotatedString.Builder.appendInline(src: String, s: MarkdownStyle) {
    var i = 0
    while (i < src.length) {
        val c = src[i]
        when {
            c == '`' -> {
                val end = src.indexOf('`', i + 1)
                if (end > i + 1) {
                    withStyle(s.inlineCode) { append(src.substring(i + 1, end)) }
                    i = end + 1
                } else { append(c); i++ }
            }

            src.startsWith("**", i) || src.startsWith("__", i) -> {
                val d = src.substring(i, i + 2)
                val end = src.indexOf(d, i + 2)
                if (end > i + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { appendInline(src.substring(i + 2, end), s) }
                    i = end + 2
                } else { append(d); i += 2 }
            }

            src.startsWith("~~", i) -> {
                val end = src.indexOf("~~", i + 2)
                if (end > i + 2) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) { appendInline(src.substring(i + 2, end), s) }
                    i = end + 2
                } else { append("~~"); i += 2 }
            }

            (c == '*' || c == '_') && (c == '*' || i == 0 || !src[i - 1].isLetterOrDigit()) -> {
                val end = src.indexOf(c, i + 1)
                if (end > i + 1 && !src[i + 1].isWhitespace()) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendInline(src.substring(i + 1, end), s) }
                    i = end + 1
                } else { append(c); i++ }
            }

            c == '[' -> {
                val mid = src.indexOf("](", i + 1)
                val close = if (mid > i) src.indexOf(')', mid + 2) else -1
                if (mid > i && close > mid) {
                    val url = src.substring(mid + 2, close)
                    withLink(LinkAnnotation.Url(url, TextLinkStyles(style = s.link))) {
                        appendInline(src.substring(i + 1, mid), s)
                    }
                    i = close + 1
                } else { append(c); i++ }
            }

            else -> { append(c); i++ }
        }
    }
}