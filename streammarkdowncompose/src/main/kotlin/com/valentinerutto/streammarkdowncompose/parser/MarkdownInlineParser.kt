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

/** Streaming-safe inline parser for code, emphasis, links, escapes, and safe HTML. */
internal fun buildInline(src: String, style: MarkdownStyle): AnnotatedString =
    buildAnnotatedString { appendInline(sanitizeInlineHtml(src), style) }

private fun AnnotatedString.Builder.appendInline(src: String, s: MarkdownStyle) {
    var i = 0
    while (i < src.length) {
        val c = src[i]

        if (c == '\\' && i + 1 < src.length) {
            if (src[i + 1] in "\\`*_[{}]<>#+-.!|()~") {
                append(src[i + 1])
                i += 2
                continue
            }
        }

        when {
            c == '`' -> {
                val end = src.indexOf('`', i + 1)
                if (end > i + 1) {
                    withStyle(s.inlineCode) { append(src.substring(i + 1, end)) }
                    i = end + 1
                } else {
                    append(c)
                    i++
                }
            }

            src.startsWith("**", i) || src.startsWith("__", i) -> {
                val delimiter = src.substring(i, i + 2)
                val end = src.indexOf(delimiter, i + 2)
                if (end > i + 2) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        appendInline(src.substring(i + 2, end), s)
                    }
                    i = end + 2
                } else {
                    append(delimiter)
                    i += 2
                }
            }

            src.startsWith("~~", i) -> {
                val end = src.indexOf("~~", i + 2)
                if (end > i + 2) {
                    withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                        appendInline(src.substring(i + 2, end), s)
                    }
                    i = end + 2
                } else {
                    append("~~")
                    i += 2
                }
            }

            (c == '*' || c == '_') &&
                (i == 0 || !src[i - 1].isLetterOrDigit()) -> {
                val end = src.indexOf(c, i + 1)
                if (end > i + 1 && i + 1 < src.length && !src[i + 1].isWhitespace()) {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        appendInline(src.substring(i + 1, end), s)
                    }
                    i = end + 1
                } else {
                    append(c)
                    i++
                }
            }

            c == '[' -> {
                val mid = src.indexOf("](", i + 1)
                val close = if (mid > i) src.indexOf(')', mid + 2) else -1
                if (mid > i && close > mid) {
                    val url = src.substring(mid + 2, close).trim()
                    val label = src.substring(i + 1, mid)
                    if (isSafeLink(url)) {
                        withLink(LinkAnnotation.Url(url, TextLinkStyles(style = s.link))) {
                            appendInline(label, s)
                        }
                    } else {
                        append(label)
                    }
                    i = close + 1
                } else {
                    append(c)
                    i++
                }
            }

            else -> {
                append(c)
                i++
            }
        }
    }
}

private fun sanitizeInlineHtml(source: String): String {
    val dangerousElements = Regex("(?is)<\\/?(script|style|iframe|object|embed|svg|form|button|video|audio)\\b[^>]*>")
    val allowedInline = setOf("b", "strong", "i", "em", "code", "s", "del", "br")
    var result = dangerousElements.replace(source, "")
    result = Regex("(?is)<\\/?([a-z][a-z0-9]*)\\b[^>]*>").replace(result) { match ->
        val tag = match.groupValues[1].lowercase()
        if (tag !in allowedInline) ""
        else if (tag == "br") "\n"
        else ""
    }
    return decodeEntities(result)
}

private fun decodeEntities(value: String): String = value
    .replace("&amp;", "&")
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&#39;", "'")

private fun isSafeLink(url: String): Boolean {
    val normalized = url.lowercase().trim()
    if (normalized.isEmpty()) return false
    if (normalized.startsWith("javascript:") || normalized.startsWith("vbscript:")) return false
    if (normalized.startsWith("file:") || normalized.startsWith("content:") || normalized.startsWith("intent:")) return false
    if (normalized.startsWith("//")) return false
    return normalized.startsWith("http://") ||
        normalized.startsWith("https://") ||
        normalized.startsWith("mailto:") ||
        normalized.startsWith("tel:") ||
        normalized.startsWith("/") ||
        !normalized.contains(":")
}