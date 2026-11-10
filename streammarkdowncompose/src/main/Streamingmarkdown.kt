package com.valentinerutto.streammarkdowncompose

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.scan
import com.valentinerutto.streammarkdowncompose.MarkdownBlock.*

/**
 * Renders markdown from a stream of text deltas.
 *
 * IMPORTANT: pass a *stable* flow (`remember(messageId) { vm.stream(...) }`), otherwise the
 * collection restarts on every recomposition.
 */
@Composable
fun StreamingMarkdown(
    chunks: Flow<String>,
    modifier: Modifier = Modifier,
    style: MarkdownStyle = markdownStyle(),
    highlighter: CodeHighlighter = SimpleCodeHighlighter,
    onComplete: () -> Unit = {},
) {
    var blocks by remember(chunks) { mutableStateOf<List<MarkdownBlock>>(emptyList()) }

    LaunchedEffect(chunks) {
        chunks
            .scan("") { acc, delta -> acc + delta }   // accumulate
            .conflate()                               // if parsing is slower than the stream, skip to the latest text
            .map { MarkdownParser.parse(it) }         // parse off the main thread
            .flowOn(Dispatchers.Default)
            .collect { blocks = it }
        onComplete()
    }

    MarkdownBlocks(blocks, modifier, style, highlighter)
}

/** For finished messages (chat history) — same renderer, no flow. */
@Composable
fun StreamingMarkdown(
    text: String,
    modifier: Modifier = Modifier,
    style: MarkdownStyle = markdownStyle(),
    highlighter: CodeHighlighter = SimpleCodeHighlighter,
) {
    val blocks = remember(text) { MarkdownParser.parse(text) }
    MarkdownBlocks(blocks, modifier, style, highlighter)
}

@Composable
fun MarkdownBlocks(
    blocks: List<MarkdownBlock>,
    modifier: Modifier = Modifier,
    style: MarkdownStyle = markdownStyle(),
    highlighter: CodeHighlighter = SimpleCodeHighlighter,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(style.blockSpacing)) {
        blocks.forEachIndexed { index, block ->
            // Positional key + immutable blocks: only the block that is still growing recomposes.
            key(index) { MarkdownBlockView(block, style, highlighter) }
        }
    }
}

@Composable
private fun MarkdownBlockView(block: MarkdownBlock, style: MarkdownStyle, highlighter: CodeHighlighter) {
    when (block) {
        is Paragraph -> InlineText(block.text, style.body, style)
        is Heading -> InlineText(block.text, style.headings[(block.level - 1).coerceIn(0, 5)], style)
        is CodeBlock -> CodeBlockView(block, style, highlighter)
        is ListBlock -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            block.items.forEach { item ->
                Row(Modifier.padding(start = (item.depth * 16).dp)) {
                    Text(item.marker, style = style.body, modifier = Modifier.widthIn(min = 22.dp))
                    InlineText(item.text, style.body, style, Modifier.weight(1f))
                }
            }
        }
        is Quote -> Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(3.dp).fillMaxHeight().background(style.quoteBar))
            Spacer(Modifier.width(10.dp))
            InlineText(block.text, style.body, style)
        }
        Divider -> Box(Modifier.fillMaxWidth().height(1.dp).background(style.quoteBar))
    }
}

@Composable
private fun InlineText(text: String, textStyle: TextStyle, style: MarkdownStyle, modifier: Modifier = Modifier) {
    val annotated = remember(text, style) { buildInline(text, style) }
    Text(annotated, style = textStyle, modifier = modifier)
}

@Composable
private fun CodeBlockView(block: CodeBlock, style: MarkdownStyle, highlighter: CodeHighlighter) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    LaunchedEffect(copied) { if (copied) { delay(1_500); copied = false } }

    val highlighted = remember(block.code, block.language, style.syntax) {
        highlighter.highlight(block.code, block.language, style.syntax)
    }

    Column(Modifier.fillMaxWidth().clip(style.codeShape).background(style.codeBackground)) {
        Row(
            Modifier.fillMaxWidth().background(style.codeHeaderBackground).padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(block.language ?: "code", style = style.codeLabel)
            TextButton(onClick = { clipboard.setText(AnnotatedString(block.code)); copied = true }) {
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