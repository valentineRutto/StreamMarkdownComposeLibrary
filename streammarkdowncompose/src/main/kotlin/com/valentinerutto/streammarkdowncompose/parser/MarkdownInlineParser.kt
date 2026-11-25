package com.valentinerutto.streammarkdowncompose.parser

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import com.valentinerutto.streammarkdowncompose.MarkdownStyle

/**
 * Streaming-safe inline parser: `code`, ***emphasis***, ~~strike~~, [links](url), <autolinks>,
 * bare URLs, escapes, <br> and common entities.
 *
 * @param streaming true ONLY for the block that is still receiving text. Unfinished syntax is then
 *   styled optimistically (e.g. "**Hel" is already bold) instead of flashing raw markers. Re-render with
 *   false once the stream ends and anything that never closed falls back to literal text.
 * @param onLinkClick lets the app intercept taps (confirm dialog, in-app browser, analytics).
 *   When null, Compose's default UriHandler is used.
 */
internal fun buildInline(
    src: String,
    style: MarkdownStyle,
    streaming: Boolean = false,
    onLinkClick: ((String) -> Unit)? = null,
): AnnotatedString = buildAnnotatedString {
    InlineWriter(this, style, streaming, onLinkClick).write(src)
}

private val brTag = Regex("(?i)br\\s*/?")

private class InlineWriter(
    private val out: AnnotatedString.Builder,
    private val s: MarkdownStyle,
    private val streaming: Boolean,
    private val onLinkClick: ((String) -> Unit)?,
) {
    private var inLink = false

    fun write(src: String) {
        var i = 0
        while (i < src.length) {
            val c = src[i]
            i = when {
                c == '\\' -> escape(src, i)
                c == '`' -> codeSpan(src, i)
                c == '*' || c == '_' || c == '~' -> emphasis(src, i)
                c == '!' && src.startsWith("![", i) -> link(src, i, image = true)
                c == '[' -> link(src, i, image = false)
                c == '<' -> angle(src, i)
                c == '&' -> entity(src, i)
                c == 'h' && !inLink && isUrlStart(src, i) -> bareUrl(src, i)
                else -> { out.append(c); i + 1 }
            }
        }
    }

    // ---------------------------------------------------------------- escapes

    private fun escape(src: String, i: Int): Int {
        if (i + 1 >= src.length) {
            if (!streaming) out.append('\\') // lone trailing backslash: hold back while streaming
            return i + 1
        }
        val next = src[i + 1]
        return if (next.isAsciiPunctuation()) { out.append(next); i + 2 } else { out.append('\\'); i + 1 }
    }

    private fun Char.isAsciiPunctuation() = this in '!'..'/' || this in ':'..'@' || this in '['..'`' || this in '{'..'~'

    // ------------------------------------------------------------- code spans

    private fun backtickRun(src: String, i: Int): Int {
        var n = 0
        while (i + n < src.length && src[i + n] == '`') n++
        return n
    }

    /** Start of the closing backtick run of exactly [n] backticks at/after [from], or -1. */
    private fun findCodeClose(src: String, from: Int, n: Int): Int {
        var j = from
        while (j < src.length) {
            j = src.indexOf('`', j)
            if (j < 0) return -1
            val run = backtickRun(src, j)
            if (run == n) return j
            j += run
        }
        return -1
    }

    private fun codeSpan(src: String, i: Int): Int {
        val n = backtickRun(src, i)
        val start = i + n
        val close = findCodeClose(src, start, n)
        return when {
            close >= 0 -> { emitCode(src.substring(start, close)); close + n }
            streaming && start < src.length -> { emitCode(src.substring(start)); src.length }
            else -> { out.append(src.substring(i, start)); start }
        }
    }

    private fun emitCode(raw: String) {
        // CommonMark: strip ONE leading+trailing space so `` `code` `` can hold backticks
        val text = if (raw.length >= 2 && raw.first() == ' ' && raw.last() == ' ' && raw.isNotBlank()) {
            raw.substring(1, raw.length - 1)
        } else raw
        out.withStyle(s.inlineCode) { append(text) }
    }

    // --------------------------------------------------------------- emphasis

    private fun emphasis(src: String, i: Int): Int {
        val c = src[i]
        var run = 0
        while (i + run < src.length && src[i + run] == c) run++
        val after = i + run

        // Marker run at the very end of the stream: hold it back instead of flashing raw "**"
        if (after >= src.length && streaming) return src.length

        val d = if (c == '~') (if (run == 2) 2 else 0) else if (run <= 3) run else 0
        val canOpen = d > 0 && after < src.length && !src[after].isWhitespace() &&
                (c == '~' || (c == '*' && d >= 2) || i == 0 || !src[i - 1].isLetterOrDigit())
        if (!canOpen) { out.append(src.substring(i, after)); return after }

        val end = findCloser(src, c, d, after)
        return when {
            end >= 0 -> { styled(c, d) { write(src.substring(after, end)) }; end + d }
            streaming -> { styled(c, d) { write(src.substring(after)) }; src.length }
            else -> { out.append(src.substring(i, after)); after }
        }
    }

    /**
     * Finds the closing delimiter. Runs of a different length are treated as nested markers and skipped,
     * except a run of 3 which can close a 1- or 2-char opener ("**bold *italic***"). Code spans and
     * escaped characters are skipped.
     */
    private fun findCloser(src: String, c: Char, d: Int, contentStart: Int): Int {
        var j = contentStart
        while (j < src.length) {
            val ch = src[j]
            if (ch == '\\') { j += 2; continue }
            if (ch == '`') {
                val n = backtickRun(src, j)
                val close = findCodeClose(src, j + n, n)
                j = if (close >= 0) close + n else j + n
                continue
            }
            if (ch != c) { j++; continue }

            var r = 0
            while (j + r < src.length && src[j + r] == c) r++
            if (r == d || (r == 3 && d < 3 && c != '~')) {
                val end = j + r - d
                val okBefore = end > contentStart && !src[end - 1].isWhitespace()
                val okAfter = c != '_' || end + d >= src.length || !src[end + d].isLetterOrDigit()
                if (okBefore && okAfter) return end
            }
            j += r
        }
        return -1
    }

    private fun styled(c: Char, d: Int, body: () -> Unit) {
        val span = when {
            c == '~' -> SpanStyle(textDecoration = TextDecoration.LineThrough)
            d == 1 -> SpanStyle(fontStyle = FontStyle.Italic)
            d == 2 -> SpanStyle(fontWeight = FontWeight.Bold)
            else -> SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
        }
        out.withStyle(span) { body() }
    }

    // ------------------------------------------------------------------ links

    private fun link(src: String, start: Int, image: Boolean): Int {
        val open = if (image) start + 1 else start
        val literal = { out.append(src.substring(start, open + 1)); open + 1 }

        val close = matchingBracket(src, open)
        if (close < 0 || close + 1 >= src.length || src[close + 1] != '(') return literal()
        val label = src.substring(open + 1, close)

        // destination: balanced parentheses allowed, stops at whitespace (an optional "title" may follow)
        val urlStart = close + 2
        var p = urlStart
        var depth = 0
        while (p < src.length) {
            val ch = src[p]
            if (ch == '(') {
                depth++
            } else if (ch == ')') {
                if (depth == 0) break
                depth--
            } else if (ch.isWhitespace()) {
                break
            }
            p++
        }
        val url = src.substring(urlStart, p)
        val end = when {
            p < src.length && src[p] == ')' -> p
            p < src.length -> src.indexOf(')', p) // skip over the optional title
            else -> -1
        }

        if (end < 0) {
            // Destination still streaming in: show the label as a link, hide the half-typed URL
            if (streaming) { out.withStyle(s.link) { write(label) }; return src.length }
            return literal()
        }

        when {
            image -> write(label) // no image support: fall back to the alt text
            isClickable(url) -> linked(url) { write(label) }
            else -> write(label) // unsafe scheme: keep the text, drop the link
        }
        return end + 1
    }

    /** Index of the ']' that matches the '[' at [open] (nesting + escapes aware), or -1. */
    private fun matchingBracket(src: String, open: Int): Int {
        var depth = 0
        var i = open
        while (i < src.length) {
            when (src[i]) {
                '\\' -> i++
                '[' -> depth++
                ']' -> { depth--; if (depth == 0) return i }
            }
            i++
        }
        return -1
    }

    /**
     * Absolute http/https/mailto/tel links are always clickable. Relative links (no scheme) only are when the
     * app supplies [onLinkClick], because Compose's default UriHandler throws on a URL it cannot open.
     */
    private fun isClickable(url: String): Boolean = isSafeLink(url) && (onLinkClick != null || ':' in url)

    private fun linked(url: String, body: () -> Unit) {
        val listener = onLinkClick?.let { cb -> LinkInteractionListener { cb(url) } }
        out.withLink(LinkAnnotation.Url(url, TextLinkStyles(style = s.link), listener)) {
            val was = inLink
            inLink = true
            body()
            inLink = was
        }
    }

    // ------------------------------------------------- autolinks, <br>, entities

    private fun angle(src: String, i: Int): Int {
        val end = src.indexOf('>', i + 1)
        if (end > i && end - i <= 2048) {
            val inner = src.substring(i + 1, end)
            if (brTag.matches(inner)) { out.append('\n'); return end + 1 }
            if (isUrlStart(inner, 0) && inner.none { it.isWhitespace() } && isSafeLink(inner)) {
                linked(inner) { out.append(inner) }
                return end + 1
            }
        }
        // Everything else (List<String>, <your-api-key>, a < b) stays literal: Compose Text never interprets HTML.
        out.append('<')
        return i + 1
    }

    private fun isUrlStart(src: String, i: Int): Boolean =
        (src.startsWith("http://", i) || src.startsWith("https://", i)) &&
                (i == 0 || !src[i - 1].isLetterOrDigit())

    private fun bareUrl(src: String, i: Int): Int {
        var end = i
        while (end < src.length && !src[end].isWhitespace() && src[end] != '<') end++
        // drop trailing punctuation that is almost never part of the URL; keep balanced ")"
        while (end > i) {
            val ch = src[end - 1]
            val candidate = src.substring(i, end)
            val trim = ch in ".,;:!?'\"*_~]}" ||
                    (ch == ')' && candidate.count { it == '(' } < candidate.count { it == ')' })
            if (trim) end-- else break
        }
        val url = src.substring(i, end)
        if (url.substringAfter("://").isEmpty() || !isSafeLink(url)) { out.append(src[i]); return i + 1 }
        linked(url) { out.append(url) }
        return end
    }

    private fun entity(src: String, i: Int): Int {
        val semi = src.indexOf(';', i + 1)
        if (semi in (i + 3)..(i + 9)) {
            val decoded = decodeEntity(src.substring(i + 1, semi))
            if (decoded != null) { out.append(decoded); return semi + 1 }
        }
        out.append('&')
        return i + 1
    }

    private fun decodeEntity(name: String): String? = when {
        name == "amp" -> "&"
        name == "lt" -> "<"
        name == "gt" -> ">"
        name == "quot" -> "\""
        name == "apos" -> "'"
        name == "nbsp" -> "\u00A0"
        name.startsWith("#") -> {
            val cp = if (name.length > 2 && (name[1] == 'x' || name[1] == 'X')) name.substring(2).toIntOrNull(16)
            else name.substring(1).toIntOrNull()
            if (cp != null && cp > 0 && Character.isValidCodePoint(cp)) String(Character.toChars(cp)) else null
        }
        else -> null
    }
}

/**
 * Link allow-list. Relative URLs (no scheme) pass; anything with a scheme must be http, https, mailto or tel.
 * Whitespace is stripped first so "java script:..." style tricks cannot slip through.
 */
internal fun isSafeLink(url: String): Boolean {
    val normalized = url.trim().replace(Regex("\\s+"), "")
    if (normalized.isEmpty() || normalized.startsWith("//")) return false

    val schemeEnd = normalized.indexOf(':')
    if (schemeEnd < 0) return true

    val scheme = normalized.substring(0, schemeEnd).lowercase()
    return scheme in setOf("http", "https", "mailto", "tel")
}

// ---------------------------------------------------------------------------------- HTML blocks

private val htmlComment = Regex("<!--[\\s\\S]*?(?:-->|\\z)")
private val htmlHidden = Regex("(?is)<(script|style)\\b.*?(?:</\\1>|\\z)")
private val htmlBreak = Regex("(?i)<br\\s*/?>|</(?:p|div|li|tr|h[1-6])>")
private val htmlTag = Regex("<[^>]+>")

/**
 * For HtmlBlock ONLY: drops tags (keeps their text), comments, and script/style content.
 * Entities are NOT decoded here, buildInline decodes them once. Never run this on normal inline text,
 * it would delete things like List<String> and <your-api-key>.
 */
internal fun htmlToPlainText(html: String): String = html
    .replace(htmlComment, "")
    .replace(htmlHidden, "")
    .replace(htmlBreak, "\n")
    .replace(htmlTag, "")
    .trim()

@Deprecated("No longer part of buildInline. Use htmlToPlainText for HtmlBlock content.", ReplaceWith("htmlToPlainText(source)"))
internal fun sanitizeInlineHtml(source: String): String = htmlToPlainText(source)