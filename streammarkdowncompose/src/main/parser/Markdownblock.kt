package streamingmd

import androidx.compose.runtime.Immutable

/** Block-level model produced by [MarkdownParser]. Immutable + data classes => finished blocks are skipped on recomposition. */
@Immutable
sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock
    data class Heading(val level: Int, val text: String) : MarkdownBlock

    /** [isClosed] is false while the closing ``` has not streamed in yet. */
    data class CodeBlock(val language: String?, val code: String, val isClosed: Boolean) : MarkdownBlock
    data class ListBlock(val items: List<ListItem>) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data object Divider : MarkdownBlock
}

@Immutable
data class ListItem(val depth: Int, val marker: String, val text: String)