package com.valentinerutto.streammarkdowncompose.parser

import com.valentinerutto.streammarkdowncompose.parser.MarkdownBlock.*

/** Tolerant block parser for streamed Markdown prefixes. */
object MarkdownParser {
    private val heading = Regex("^(#{1,6})\\s+(.*)$")
    private val bullet = Regex("^(\\s*)[-*+]\\s+(.*)$")
    private val ordered = Regex("^(\\s*)(\\d{1,9})[.)]\\s+(.*)$")
    private val divider = Regex("^\\s*(?:(?:-[\\t ]*){3,}|(?:\\*[\\t ]*){3,}|(?:_[\\t ]*){3,})\\s*$")
    private val task = Regex("^\\[([ xX])\\]\\s*(.*)$")
    private val fenceOpening = Regex("^ {0,3}(`{3,}|~{3,})([^\\r\\n]*)$")

    fun parse(source: CharSequence): List<MarkdownBlock> {
        val lines = source.toString().replace("\r\n", "\n").split('\n')
        val lastIdx = lines.lastIndex
        val out = ArrayList<MarkdownBlock>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trimStart()
            when {
                i == lastIdx && isPartialFence(line) -> i++
                line.isBlank() -> i++

                isOpeningFence(line) -> {
                    val opening = fenceOpening.find(line)!!
                    val marker = opening.groupValues[1]
                    val fenceChar = marker[0]
                    val openingFenceLength = marker.length
                    val language = opening.groupValues[2].trim().takeIf { it.isNotEmpty() }
                    val code = ArrayList<String>()
                    var closed = false
                    i++

                    while (i < lines.size) {
                        val closing = lines[i].trim()
                        val closingFenceLength = closing.takeWhile { it == fenceChar }.length
                        if (closing.length == closingFenceLength && closingFenceLength >= openingFenceLength) {
                            closed = true
                            i++
                            break
                        }
                        code += lines[i]
                        i++
                    }

                    if (!closed && code.isNotEmpty() && isPartialFence(code.last())) {
                        code.removeAt(code.lastIndex)
                    }
                    out += CodeBlock(language, code.joinToString("\n"), closed)
                }

                heading.matches(line) -> {
                    val m = heading.find(line)!!
                    out += Heading(m.groupValues[1].length, m.groupValues[2].trim())
                    i++
                }

                divider.matches(line) -> {
                    out += Divider
                    i++
                }

                isTable(lines, i) -> {
                    val table = parseTable(lines, i)
                    out += table.block
                    i = table.nextIndex
                }

                trimmed.startsWith(">") -> {
                    val quote = ArrayList<String>()
                    while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                        quote += lines[i].trimStart().removePrefix(">").removePrefix(" ")
                        i++
                    }
                    out += Quote(quote.joinToString("\n"))
                }

                bullet.matches(line) || ordered.matches(line) -> {
                    val parsed = parseList(lines, i)
                    out += parsed.block
                    i = parsed.nextIndex
                }

                containsHtmlBlock(trimmed) -> {
                    val html = collectHtml(lines, i)
                    out += HtmlBlock(html.text)
                    i = html.nextIndex
                }

                else -> {
                    val para = ArrayList<String>()
                    while (i < lines.size && lines[i].isNotBlank() &&
                        (para.isEmpty() || !startsBlock(lines[i]))) {
                        if (!(i == lastIdx && isPartialFence(lines[i]))) para += lines[i].trimEnd()
                        i++
                    }
                    if (para.isNotEmpty()) out += Paragraph(para.joinToString("\n"))
                }
            }
        }
        return out
    }

    private data class ParsedList(val block: ListBlock, val nextIndex: Int)

    private fun parseList(lines: List<String>, start: Int): ParsedList {
        val raw = ArrayList<RawListItem>()
        var i = start
        while (i < lines.size) {
            val line = lines[i]
            val b = bullet.find(line)
            val o = ordered.find(line)
            val match = b ?: o
            if (match == null) break

            val indentation = match.groupValues[1].length
            val content = if (b != null) b.groupValues[2] else o!!.groupValues[3]
            val marker = if (b != null) "•" else o!!.groupValues[2] + "."
            val taskMatch = task.find(content)
            val taskState = when (taskMatch?.groupValues?.get(1)?.lowercase()) {
                "x" -> TaskState.Checked
                " " -> TaskState.Unchecked
                else -> null
            }
            val text = taskMatch?.groupValues?.get(2) ?: content
            raw += RawListItem(indentation, marker, text, taskState)
            i++
        }

        val ordered = lines[start].let { ordered.find(it) != null }
        return ParsedList(ListBlock(buildNestedList(raw), ordered), i)
    }

    private data class RawListItem(
        val indent: Int,
        val marker: String,
        val text: String,
        val task: TaskState?,
    )

    private fun buildNestedList(raw: List<RawListItem>): List<ListItem> {
        if (raw.isEmpty()) return emptyList()
        val rootIndent = raw.minOf { it.indent }

        fun buildFrom(start: Int, indent: Int): Pair<List<ListItem>, Int> {
            val items = ArrayList<ListItem>()
            var i = start
            while (i < raw.size) {
                val current = raw[i]
                if (current.indent < indent) break
                if (current.indent > indent) {
                    i++
                    continue
                }

                var next = i + 1
                val children = if (next < raw.size && raw[next].indent > indent) {
                    val childIndent = raw[next].indent
                    val result = buildFrom(next, childIndent)
                    next = result.second
                    result.first
                } else emptyList()

                val depth = ((current.indent - rootIndent) / 2).coerceAtLeast(0)
                items += ListItem(depth, current.marker, current.text, current.task, children)
                i = next
            }
            return items to i
        }

        return buildFrom(0, rootIndent).first
    }

    private data class ParsedTable(val block: TableBlock, val nextIndex: Int)

    private fun isTable(lines: List<String>, i: Int): Boolean {
        if (i + 1 >= lines.size) return false
        val header = splitTableRow(lines[i]) ?: return false
        val separator = splitTableRow(lines[i + 1]) ?: return false
        if (header.isEmpty() || separator.size != header.size) return false
        return separator.all { cell ->
            val c = cell.trim()
            c.matches(Regex("^:?-{3,}:?$"))
        }
    }

    private fun parseTable(lines: List<String>, start: Int): ParsedTable {
        val headers = splitTableRow(lines[start])!!.map { it.trim() }
        val separators = splitTableRow(lines[start + 1])!!.map { it.trim() }
        val alignments = separators.map { separator ->
            val left = separator.startsWith(':')
            val right = separator.endsWith(':')
            when {
                left && right -> TableAlignment.Center
                left -> TableAlignment.Left
                right -> TableAlignment.Right
                else -> TableAlignment.None
            }
        }

        val rows = ArrayList<List<String>>()
        var i = start + 2
        while (i < lines.size) {
            val row = splitTableRow(lines[i]) ?: break
            if (row.all { it.isBlank() }) break
            rows += row.take(headers.size).map { it.trim() }.let {
                it + List((headers.size - it.size).coerceAtLeast(0)) { "" }
            }
            i++
        }
        return ParsedTable(TableBlock(headers, alignments, rows), i)
    }

    private fun splitTableRow(line: String): List<String>? {
        val trimmed = line.trim()
        if (!trimmed.contains('|')) return null
        val content = trimmed.removePrefix("|").removeSuffix("|")
        val cells = ArrayList<String>()
        var start = 0
        var escaped = false
        for (index in content.indices) {
            val c = content[index]
            if (c == '\\' && !escaped) escaped = true
            else if (c == '|' && !escaped) {
                cells += content.substring(start, index).replace("\\|", "|")
                start = index + 1
                escaped = false
            } else escaped = false
        }
        cells += content.substring(start).replace("\\|", "|")
        return cells
    }

    private data class ParsedHtml(val text: String, val nextIndex: Int)

    private fun containsHtmlBlock(line: String): Boolean =
        Regex("(?i)^<(p|div|section|table|ul|ol|blockquote|h[1-6])(?:\\s|>)").containsMatchIn(line)

    private fun collectHtml(lines: List<String>, start: Int): ParsedHtml {
        val tagRegex = Regex("(?is)<\\/?([a-z][a-z0-9]*)\\b[^>]*>")
        val dangerous = setOf("script", "style", "iframe", "object", "embed", "svg", "form", "button", "video", "audio")
        val text = StringBuilder()
        var i = start

        while (i < lines.size) {
            val line = lines[i]
            val lineWithoutDangerous = stripDangerousHtml(line, dangerous)
            val lineText = tagRegex.replace(lineWithoutDangerous) { match ->
                if (match.groupValues[1].equals("br", ignoreCase = true)) "\n" else ""
            }.trim()

            if (lineText.isNotEmpty()) {
                if (text.isNotEmpty()) text.append('\n')
                text.append(decodeBasicEntities(lineText))
            }

            i++
            val trimmed = line.trim()
            if (trimmed.matches(Regex("(?is)^<\\/(p|div|section|table|ul|ol|blockquote|h[1-6])\\s*>.*$"))) break
            if (i < lines.size && line.trim().startsWith("<") &&
                !line.trim().contains('>') && !lines[i].trim().contains('>')) break
        }

        return ParsedHtml(text.toString().trim(), i)
    }

    private fun stripDangerousHtml(value: String, dangerous: Set<String>): String {
        var result = value
        dangerous.forEach { tag ->
            result = Regex("(?is)<$tag\\b[^>]*>.*?</$tag\\s*>").replace(result, "")
            result = Regex("(?is)</?$tag\\b[^>]*>").replace(result, "")
        }
        return result
    }

    private fun decodeBasicEntities(value: String): String = value
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")

    private fun isPartialFence(line: String): Boolean {
        val t = line.trim()
        return t.isNotEmpty() && t.length < 3 && (t.all { it == '`' } || t.all { it == '~' })
    }

    private fun startsBlock(line: String): Boolean {
        val t = line.trimStart()
        return isOpeningFence(t) || t.startsWith(">") ||
            heading.matches(line) || bullet.matches(line) || ordered.matches(line) || divider.matches(line) ||
            isTableLinePair(line) || containsHtmlBlock(t)
    }

    private fun isTableLinePair(line: String): Boolean = splitTableRow(line)?.let { it.size > 1 } == true

    private fun isOpeningFence(line: String): Boolean = fenceOpening.matches(line)
}