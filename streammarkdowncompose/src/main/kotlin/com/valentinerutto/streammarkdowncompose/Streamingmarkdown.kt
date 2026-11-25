package com.valentinerutto.streammarkdowncompose

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valentinerutto.streammarkdowncompose.parser.ListItem
import com.valentinerutto.streammarkdowncompose.parser.MarkdownBlock
import com.valentinerutto.streammarkdowncompose.parser.MarkdownBlock.*
import com.valentinerutto.streammarkdowncompose.parser.MarkdownParser
import com.valentinerutto.streammarkdowncompose.parser.TableAlignment
import com.valentinerutto.streammarkdowncompose.parser.TaskState
import com.valentinerutto.streammarkdowncompose.parser.buildInline
import com.valentinerutto.streammarkdowncompose.parser.htmlToPlainText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** Cap parser work during fast streams while keeping updates visually responsive. */
private const val STREAM_PARSE_INTERVAL_MS = 50L

/**
 * Renders markdown from a stream of text deltas.
 *
 * Pass a *stable* flow (`remember(messageId) { vm.replyFlow(messageId) }`), otherwise collection
 * restarts on every recomposition.
 *
 * [onComplete]: called once, on the main thread, after the flow finishes normally and the final text
 * has been handed to the UI. NOT called on error ([onError]) or when the composable leaves composition
 * mid-stream. A flow that is already finished (chat history) completes immediately, so it fires for
 * those too: guard live-only effects (haptics, analytics) yourself.
 */
@Composable
fun StreamingMarkdown(
    chunks: Flow<String>,
    modifier: Modifier = Modifier,
    style: MarkdownStyle = markdownStyle(),
    highlighter: CodeHighlighter = SimpleCodeHighlighter,
    onLinkClick: ((String) -> Unit)? = null,
    onError: (Throwable) -> Unit = {},
    onComplete: () -> Unit = {},
) {
    var blocks by remember(chunks) { mutableStateOf<List<MarkdownBlock>>(emptyList()) }
    var isStreaming by remember(chunks) { mutableStateOf(true) }
    // LaunchedEffect only restarts when `chunks` changes, so read the callbacks through updated state
    // (otherwise it would keep calling the lambdas from the first composition).
    val latestOnComplete by rememberUpdatedState(onComplete)
    val latestOnError by rememberUpdatedState(onError)

    LaunchedEffect(chunks) {
        isStreaming = true
        try {
            chunks
                .accumulatedSnapshots(STREAM_PARSE_INTERVAL_MS)
                .conflate()                             // snapshots are cumulative; parsing an older one is unnecessary
                .map { MarkdownParser.parse(it) }
                .flowOn(Dispatchers.Default)            // parse off the main thread
                .collect { blocks = it }
            latestOnComplete()
        } catch (e: CancellationException) {
            throw e // never swallow cancellation
        } catch (e: Throwable) {
            latestOnError(e) // partial text stays on screen
        } finally {
            isStreaming = false
        }
    }

    MarkdownBlocks(blocks, modifier, style, highlighter, streaming = isStreaming, onLinkClick = onLinkClick)
}

/** For finished messages (chat history): same renderer, no flow. */
@Composable
fun StreamingMarkdown(
    text: String,
    modifier: Modifier = Modifier,
    style: MarkdownStyle = markdownStyle(),
    highlighter: CodeHighlighter = SimpleCodeHighlighter,
    onLinkClick: ((String) -> Unit)? = null,
) {
    val blocks = remember(text) { MarkdownParser.parse(text) }
    MarkdownBlocks(blocks, modifier, style, highlighter, streaming = false, onLinkClick = onLinkClick)
}

/** [streaming] = the stream is still active; only the LAST block is treated as "still growing". */
@Composable
fun MarkdownBlocks(
    blocks: List<MarkdownBlock>,
    modifier: Modifier = Modifier,
    style: MarkdownStyle = markdownStyle(),
    highlighter: CodeHighlighter = SimpleCodeHighlighter,
    streaming: Boolean = false,
    onLinkClick: ((String) -> Unit)? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(style.blockSpacing)) {
        blocks.forEachIndexed { index, block ->
            key(index) {
                MarkdownBlockView(
                    block = block,
                    style = style,
                    highlighter = highlighter,
                    streaming = streaming && index == blocks.lastIndex,
                    onLinkClick = onLinkClick,
                )
            }
        }
    }
}

@Composable
private fun MarkdownBlockView(
    block: MarkdownBlock,
    style: MarkdownStyle,
    highlighter: CodeHighlighter,
    streaming: Boolean,
    onLinkClick: ((String) -> Unit)?,
) {
    when (block) {
        is Paragraph -> InlineText(block.text, style.body, style, streaming, onLinkClick)

        is Heading -> InlineText(
            text = block.text,
            textStyle = style.headings[(block.level - 1).coerceIn(0, 5)],
            style = style,
            streaming = streaming,
            onLinkClick = onLinkClick,
            modifier = Modifier.padding(top = 6.dp).semantics { heading() }, // TalkBack heading navigation
        )

        is CodeBlock -> CodeBlockView(block, style, highlighter, streaming)

        is ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            block.items.forEachIndexed { idx, item ->
                ListItemView(
                    item = item,
                    style = style,
                    streaming = streaming && idx == block.items.lastIndex,
                    onLinkClick = onLinkClick,
                    modifier = Modifier.padding(start = (item.depth * 16).dp), // top level only; children are indented by nesting
                )
            }
        }

        is TableBlock -> TableView(block, style, onLinkClick)

        is HtmlBlock -> {
            val text = remember(block.html) { htmlToPlainText(block.html) }
            InlineText(text, style.body, style, streaming, onLinkClick)
        }

        // Quotes can hold lists, code and more, so parse their content as blocks too.
        is Quote -> {
            val inner = remember(block.text) { MarkdownParser.parse(block.text) }
            val bar = style.quoteBar
            Column(
                Modifier
                    .drawBehind { drawRect(bar, size = Size(3.dp.toPx(), size.height)) }
                    .padding(start = 13.dp),
            ) {
                MarkdownBlocks(inner, Modifier, style, highlighter, streaming, onLinkClick)
            }
        }

        Divider -> Box(
            Modifier.fillMaxWidth().padding(vertical = 4.dp).height(1.dp).background(style.quoteBar),
        )
    }
}

@Composable
private fun ListItemView(
    item: ListItem,
    style: MarkdownStyle,
    streaming: Boolean,
    onLinkClick: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            if (item.task != null) {
                // Read-only. Default checkbox is ~24dp, close to one body line, so it lines up with the first line of text.
                Checkbox(
                    checked = item.task == TaskState.Checked,
                    onCheckedChange = null,
                    modifier = Modifier.padding(end = 4.dp),
                )
            } else {
                Text(item.marker, style = style.body, modifier = Modifier.widthIn(min = 22.dp))
            }
            InlineText(
                text = item.text,
                textStyle = style.body,
                style = style,
                streaming = streaming && item.children.isEmpty(),
                onLinkClick = onLinkClick,
                modifier = Modifier.weight(1f),
            )
        }
        if (item.children.isNotEmpty()) {
            Column(Modifier.padding(start = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                item.children.forEachIndexed { idx, child ->
                    ListItemView(child, style, streaming && idx == item.children.lastIndex, onLinkClick)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------- tables

@Composable
private fun TableView(block: TableBlock, style: MarkdownStyle, onLinkClick: ((String) -> Unit)?) {
    val columns = block.headers.size
    if (columns == 0) return
    val rows = remember(block) { block.rows.map { it.padTo(columns) } }
    fun alignment(c: Int) = block.alignments.getOrElse(c) { TableAlignment.None }

    SelectionContainer {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(style.codeShape)
                .horizontalScroll(rememberScrollState())
                .background(style.codeBackground),
        ) {
            TableLayout(columns) {
                block.headers.forEachIndexed { c, cell -> TableCell(cell, alignment(c), true, style, onLinkClick) }
                rows.forEach { row ->
                    row.forEachIndexed { c, cell -> TableCell(cell, alignment(c), false, style, onLinkClick) }
                }
            }
        }
    }
}

/**
 * Grid layout: every column is as wide as its widest cell (64dp..280dp) and every row as tall as its
 * tallest cell, so columns line up across rows. Cells are passed row-major, [columns] per row.
 */
@Composable
private fun TableLayout(columns: Int, content: @Composable () -> Unit) {
    Layout(content = content) { measurables, _ ->
        val minCol = 64.dp.roundToPx()
        val maxCol = 280.dp.roundToPx()
        val rowCount = (measurables.size + columns - 1) / columns

        val colW = IntArray(columns)
        measurables.forEachIndexed { i, m ->
            val c = i % columns
            colW[c] = maxOf(colW[c], m.maxIntrinsicWidth(Constraints.Infinity).coerceIn(minCol, maxCol))
        }
        val rowH = IntArray(rowCount)
        measurables.forEachIndexed { i, m ->
            val r = i / columns
            rowH[r] = maxOf(rowH[r], m.minIntrinsicHeight(colW[i % columns]))
        }
        val placeables = measurables.mapIndexed { i, m ->
            m.measure(Constraints.fixed(colW[i % columns], rowH[i / columns]))
        }

        val xs = IntArray(columns).also { for (c in 1 until columns) it[c] = it[c - 1] + colW[c - 1] }
        val ys = IntArray(rowCount).also { for (r in 1 until rowCount) it[r] = it[r - 1] + rowH[r - 1] }
        val width = xs[columns - 1] + colW[columns - 1]
        val height = if (rowCount == 0) 0 else ys[rowCount - 1] + rowH[rowCount - 1]

        layout(width, height) {
            placeables.forEachIndexed { i, p -> p.placeRelative(xs[i % columns], ys[i / columns]) }
        }
    }
}

@Composable
private fun TableCell(
    text: String,
    alignment: TableAlignment,
    isHeader: Boolean,
    style: MarkdownStyle,
    onLinkClick: ((String) -> Unit)?,
) {
    val annotated = remember(text, style) { buildInline(text, style, onLinkClick = onLinkClick) }
    val line = style.quoteBar.copy(alpha = 0.4f)
    val textAlign = when (alignment) {
        TableAlignment.Center -> TextAlign.Center
        TableAlignment.Right -> TextAlign.End
        TableAlignment.Left, TableAlignment.None -> TextAlign.Start
    }
    val boxAlignment = when (alignment) {
        TableAlignment.Center -> Alignment.Center
        TableAlignment.Right -> Alignment.CenterEnd
        TableAlignment.Left, TableAlignment.None -> Alignment.CenterStart
    }
    Box(
        modifier = Modifier
            .background(if (isHeader) style.codeHeaderBackground else Color.Transparent)
            .drawBehind {
                drawLine(line, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = boxAlignment,
    ) {
        Text(
            text = annotated,
            style = if (isHeader) style.body.copy(fontWeight = FontWeight.Bold) else style.body,
            textAlign = textAlign,
        )
    }
}

// ------------------------------------------------------------------------- text + code

@Composable
private fun InlineText(
    text: String,
    textStyle: TextStyle,
    style: MarkdownStyle,
    streaming: Boolean,
    onLinkClick: ((String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val annotated = remember(text, style, streaming) { buildInline(text, style, streaming, onLinkClick) }
    Text(annotated, style = textStyle, modifier = modifier)
}

@Composable
private fun CodeBlockView(
    block: CodeBlock,
    style: MarkdownStyle,
    highlighter: CodeHighlighter,
    streaming: Boolean, // true only while THIS block is the one still receiving text
) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1_500); copied = false } }

    val highlighted = remember(block.code, block.language, style.syntax, streaming, block.isClosed) {
        if (streaming && !block.isClosed) AnnotatedString(block.code)
        else highlighter.highlight(block.code, block.language, style.syntax)
    }

    Column(Modifier.fillMaxWidth().clip(style.codeShape).background(style.codeBackground)) {
        Row(
            Modifier.fillMaxWidth().background(style.codeHeaderBackground).padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(block.language ?: "code", style = style.codeLabel)
            TextButton(
                onClick = { clipboard.setText(AnnotatedString(block.code)); copied = true },
                enabled = !streaming, // don't copy a half-written snippet
            ) {
                Text(if (copied) "Copied" else "Copy", fontSize = 12.sp)
            }
        }
        SelectionContainer {
            Text(
                text = highlighted,
                style = style.code,
                softWrap = false, // long lines scroll horizontally instead of wrapping
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp),
            )
        }
    }
}
