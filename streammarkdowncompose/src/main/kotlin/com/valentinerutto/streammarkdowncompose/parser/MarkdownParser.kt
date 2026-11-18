package com.valentinerutto.streammarkdowncompose

import com.valentinerutto.streammarkdowncompose.MarkdownBlock.*

/**
 * Tolerant block parser. Safe to call on any prefix of a document, so it can be run on the
 * accumulated text after every streamed chunk. An unterminated ``` fence becomes an open [CodeBlock].
 */
object MarkdownParser {
    private val heading = Regex("^(#{1,6})\\s+(.*)$")
    private val bullet = Regex("^(\\s*)[-*+]\\s+(.*)$")
    private val ordered = Regex("^(\\s*)(\\d{1,9})[.)]\\s+(.*)$")
    private val divider = Regex("^\\s*([-*_])(\\s*\\1){2,}\\s*$")

    fun parse(source: CharSequence): List<MarkdownBlock> {
        val lines = source.toString().replace("\r\n", "\n").split('\n')
        val lastIdx = lines.lastIndex
        val out = ArrayList<MarkdownBlock>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trimStart()
            when {
                // "`" or "``" dangling at the very end of the stream: hide until it becomes a real fence
                i == lastIdx && isPartialFence(line) -> i++

                line.isBlank() -> i++

                trimmed.startsWith("```") || trimmed.startsWith("~~~") -> {
                    val fenceChar = trimmed[0]
                    val lang = trimmed.dropWhile { it == fenceChar }.trim().takeIf { it.isNotEmpty() }
                    val code = ArrayList<String>()
                    var closed = false
                    i++
                    while (i < lines.size) {
                        val t = lines[i].trim()
                        if (t.length >= 3 && t.all { it == fenceChar }) {
                            closed = true; i++; break
                        }
                        code += lines[i]; i++
                    }
                    if (!closed && code.isNotEmpty() && isPartialFence(code.last())) code.removeAt(code.lastIndex)
                    out += CodeBlock(lang, code.joinToString("\n"), closed)
                }

                heading.matches(line) -> {
                    val m = heading.find(line)!!
                    out += Heading(m.groupValues[1].length, m.groupValues[2].trim()); i++
                }

                divider.matches(line) -> { out += Divider; i++ }

                trimmed.startsWith(">") -> {
                    val quote = ArrayList<String>()
                    while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                        quote += lines[i].trimStart().removePrefix(">").removePrefix(" "); i++
                    }
                    out += Quote(quote.joinToString("\n"))
                }

                bullet.matches(line) || ordered.matches(line) -> {
                    val items = ArrayList<ListItem>()
                    while (i < lines.size) {
                        val b = bullet.find(lines[i])
                        val o = ordered.find(lines[i])
                        when {
                            b != null -> items += ListItem(b.groupValues[1].length / 2, "•", b.groupValues[2])
                            o != null -> items += ListItem(o.groupValues[1].length / 2, o.groupValues[2] + ".", o.groupValues[3])
                            else -> break
                        }
                        i++
                    }
                    out += ListBlock(items)
                }

                else -> {
                    val para = ArrayList<String>()
                    while (i < lines.size && lines[i].isNotBlank() && (para.isEmpty() || !startsBlock(lines[i]))) {
                        if (!(i == lastIdx && isPartialFence(lines[i]))) para += lines[i].trimEnd()
                        i++
                    }
                    if (para.isNotEmpty()) out += Paragraph(para.joinToString("\n"))
                }
            }
        }
        return out
    }

    private fun isPartialFence(line: String): Boolean {
        val t = line.trim()
        return t.isNotEmpty() && t.length < 3 && t.all { it == '`' }
    }

    private fun startsBlock(line: String): Boolean {
        val t = line.trimStart()
        return t.startsWith("```") || t.startsWith("~~~") || t.startsWith(">") ||
                heading.matches(line) || bullet.matches(line) || ordered.matches(line) || divider.matches(line)
    }
}