package com.valentinerutto.streammarkdowncompose

import androidx.compose.runtime.Immutable

/** Block-level model produced by [MarkdownParser]. Immutable + data classes => finished blocks are skipped on recomposition. */
@Immutable
sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock
    data class Heading(val level: Int, val text: String) : MarkdownBlock

    /** [isClosed] is false while the closing ``` has not streamed in yet. */
    data class CodeBlock(val language: String?, val code: String, val isClosed: Boolean) : MarkdownBlock
    data class ListBlock(val items: List<ListItem>, val ordered: Boolean = false) : MarkdownBlock
    data class TableBlock(
        val headers: List<String>,
        val alignments: List<TableAlignment>,
        val rows: List<List<String>>,
    ) : MarkdownBlock
    data class HtmlBlock(val html: String) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data object Divider : MarkdownBlock
}

@Immutable
data class ListItem(
    val depth: Int,
    val marker: String,
    val text: String,
    val task: TaskState? = null,
    val children: List<ListItem> = emptyList(),
)

@Immutable
enum class TaskState {
    Unchecked,
    Checked,
}

@Immutable
enum class TableAlignment {
    None,
    Left,
    Center,
    Right,
}