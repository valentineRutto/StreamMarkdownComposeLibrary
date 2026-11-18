package com.valentinerutto.streammarkdowncompose

import com.valentinerutto.streammarkdowncompose.MarkdownBlock.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun nestedListBuildsChildrenAndTaskStates() {
        val blocks = MarkdownParser.parse("""
            - [ ] Parent
              - [x] Child
              - Child two
        """.trimIndent())

        val list = blocks.single() as ListBlock
        assertEquals(1, list.items.size)
        assertEquals(TaskState.Unchecked, list.items[0].task)
        assertEquals(2, list.items[0].children.size)
        assertEquals(TaskState.Checked, list.items[0].children[0].task)
        assertFalse(list.ordered)
    }

    @Test
    fun orderedAndUnorderedListsParseAtNestedDepth() {
        val blocks = MarkdownParser.parse("""
            1. First
               - Nested
            2. Second
        """.trimIndent())

        val list = blocks.single() as ListBlock
        assertTrue(list.ordered)
        assertEquals(2, list.items.size)
        assertEquals(1, list.items[0].children.size)
    }

    @Test
    fun gfmTableRequiresHeaderSeparatorAndKeepsAlignment() {
        val blocks = MarkdownParser.parse("""
            | Name | Status |
            |:-----|------:|
            | Kotlin | Ready |
            | Android | Streaming |
        """.trimIndent())

        val table = blocks.single() as TableBlock
        assertEquals(listOf("Name", "Status"), table.headers)
        assertEquals(TableAlignment.Left, table.alignments[0])
        assertEquals(TableAlignment.Right, table.alignments[1])
        assertEquals(2, table.rows.size)
    }

    @Test
    fun escapedPipeDoesNotCreateTableColumn() {
        val line = "A \\| B"
        val blocks = MarkdownParser.parse(line)
        assertEquals(line, (blocks.single() as Paragraph).text)
    }

    @Test
    fun unterminatedFenceRemainsOpenDuringStreaming() {
        val block = (MarkdownParser.parse("```kotlin\nfun hello()") .single() as CodeBlock)
        assertEquals("kotlin", block.language)
        assertFalse(block.isClosed)
    }

    @Test
    fun htmlBlockIsRetainedAsSanitizedText() {
        val block = MarkdownParser.parse("<p>Hello <strong>world</strong></p>").single() as HtmlBlock
        assertEquals("Hello world", block.html)
    }
}