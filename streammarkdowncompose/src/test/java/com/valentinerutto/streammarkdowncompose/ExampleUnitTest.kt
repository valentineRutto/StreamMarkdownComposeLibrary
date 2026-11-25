package com.valentinerutto.streammarkdowncompose

import com.valentinerutto.streammarkdowncompose.parser.MarkdownBlock
import com.valentinerutto.streammarkdowncompose.parser.MarkdownBlock.*
import com.valentinerutto.streammarkdowncompose.parser.MarkdownParser
import com.valentinerutto.streammarkdowncompose.parser.TableAlignment
import com.valentinerutto.streammarkdowncompose.parser.TaskState
import com.valentinerutto.streammarkdowncompose.parser.isSafeLink
import com.valentinerutto.streammarkdowncompose.parser.sanitizeInlineHtml
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {
    @Test
    fun headingsOneThroughSix() {
        val source = (1..6).joinToString("\n") { level -> "${"#".repeat(level)} Heading $level" }
        val blocks = MarkdownParser.parse(source)
        assertEquals(6, blocks.size)
        blocks.forEachIndexed { index, block ->
            block as Heading
            assertEquals(index + 1, block.level)
            assertEquals("Heading ${index + 1}", block.text)
        }
    }

    @Test
    fun paragraphsBoldItalicStrikeCodeAndLinks() {
        val paragraph = (MarkdownParser.parse("**Bold** *italic* ~~old~~ `code` [Android](https://developer.android.com)").single() as Paragraph)
        assertEquals("**Bold** *italic* ~~old~~ `code` [Android](https://developer.android.com)", paragraph.text)
    }

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
    fun gfmTableKeepsHeadersRowsAndAlignment() {
        val table = MarkdownParser.parse("""
            | Name | Status |
            |:-----|------:|
            | Kotlin | Ready |
            | Android | Streaming |
        """.trimIndent()).single() as TableBlock
        assertEquals(listOf("Name", "Status"), table.headers)
        assertEquals(TableAlignment.Left, table.alignments[0])
        assertEquals(TableAlignment.Right, table.alignments[1])
        assertEquals(2, table.rows.size)
    }

    @Test
    fun incompleteTableSeparatorIsParagraphText() {
        val text = "| Name | Status |\n|:-----|"
        val block = MarkdownParser.parse(text).single()
        assertTrue(block is Paragraph)
    }

    @Test
    fun escapedPipeDoesNotCreateColumns() {
        val line = "A \\| B"
        val block = MarkdownParser.parse(line).single() as Paragraph
        assertEquals(line, block.text)
    }

    @Test
    fun quotesRemainGrouped() {
        val block = MarkdownParser.parse("> First\n> Second").single() as Quote
        assertEquals("First\nSecond", block.text)
    }

    @Test
    fun dividerAndPartialFence() {
        val divider = MarkdownParser.parse("---").single()
        assertTrue(divider is Divider)
        val openFence = MarkdownParser.parse("```kotlin\nfun hello()").single() as CodeBlock
        assertEquals("kotlin", openFence.language)
        assertEquals("fun hello()", openFence.code)
        assertFalse(openFence.isClosed)
    }

    @Test
    fun closedFenceIsRecognized() {
        val block = MarkdownParser.parse("```kotlin\nfun hello() {}\n```").single() as CodeBlock
        assertTrue(block.isClosed)
        assertEquals("fun hello() {}", block.code)
    }

    @Test
    fun htmlBlockSanitizesDangerousTags() {
        val block = MarkdownParser.parse("<p>Hello <strong>world</strong><script>alert(1)</script></p>").single() as HtmlBlock
        assertEquals("Hello world", block.html)
        assertFalse(block.html.contains("script"))
        assertFalse(block.html.contains("alert"))
    }

    @Test
    fun unsafeAndSafeLinks() {
        assertTrue(isSafeLink("https://developer.android.com"))
        assertTrue(isSafeLink("mailto:test@example.com"))
        assertTrue(isSafeLink("/docs/page"))
        assertFalse(isSafeLink("javascript:alert(1)"))
        assertFalse(isSafeLink("java\nscript:alert(1)"))
        assertFalse(isSafeLink("file:///etc/passwd"))
        assertFalse(isSafeLink("intent://example"))
    }

    @Test
    fun inlineHtmlSanitizesDangerousAndUnknownTags() {
        assertEquals("Hello world",
            sanitizeInlineHtml("<p>Hello <strong>world</strong><script>bad()</script></p>")
        )
        assertFalse(sanitizeInlineHtml("<iframe src='bad'>bad</iframe>").contains("bad"))
    }

    @Test
    fun streamingAccumulationProducesFinalTableAndFence() = runTest {
        val chunks = flowOf("| Name |", " Status |\n", "|:-----|", "------:|\n", "| Kotlin |", " Ready |\n\n", "```kotlin", "\nfun hello()", "\n```")
        val snapshots = chunks.scan("") { accumulated, delta -> accumulated + delta }.toList()
        val finalBlocks = MarkdownParser.parse(snapshots.last())
        assertTrue(finalBlocks.any { it is TableBlock })
        val fence = finalBlocks.filterIsInstance<CodeBlock>().single()
        assertTrue(fence.isClosed)
        assertEquals("fun hello()", fence.code)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun streamingCancellationDoesNotPublishLateResult() = runTest {
        val chunks = flow {
            emit("# First")
            kotlinx.coroutines.awaitCancellation()
        }
        var published: List<MarkdownBlock>? = null
        val job = launch {
            chunks.scan("") { accumulated, delta -> accumulated + delta }.collect { text ->
                published = MarkdownParser.parse(text)
            }
        }
        // Schedule the first emission
        runCurrent()
        job.cancel()
        job.join()
        assertEquals("First", published?.let {
            (it.firstOrNull() as? Heading)?.text
        })
    }

    @Test
    fun escapingPreventsFormattingMarkers() {
        val source = "\\*literal\\* \\[literal\\] \\#heading \\|"
        val block = MarkdownParser.parse(source).single() as Paragraph
        assertEquals(source, block.text)
    }

    @Test
    fun horizontalRuleAndAdjacentFencesRemainSeparateBlocks() {
        val source = """
            ### Horizontal rule

            ---

            ### Fenced code

            ```kotlin
            StreamingMarkdown(
                chunks = stream,
                modifier = Modifier.padding(16.dp)
            )
            ```

            ```python
            def greet(name):
                print(f"Hello, {name}!")
            ```
        """.trimIndent()

        val blocks = MarkdownParser.parse(source)
        assertTrue(blocks.any { it is Heading && it.text == "Horizontal rule" })
        assertTrue(blocks.any { it is Divider })
        assertTrue(blocks.any { it is Heading && it.text == "Fenced code" })
        assertEquals(2, blocks.filterIsInstance<CodeBlock>().size)
        assertTrue(blocks.filterIsInstance<CodeBlock>().all { it.isClosed })
    }

    @Test
    fun mainActivitySampleProducesTwoExactClosedCodeBlocks() {
        val source = """
            ### Fenced code

            ```kotlin
            StreamingMarkdown(
                chunks = stream,
                modifier = Modifier.padding(16.dp)
            )
            ```

            ```python
            def greet(name):
                print(f"Hello, {name}!")
            ```
        """.trimIndent()

        val blocks = MarkdownParser.parse(source)
        val fences = blocks.filterIsInstance<CodeBlock>()
        assertEquals(2, fences.size)
        assertTrue(fences.all { it.isClosed })

        assertEquals("kotlin", fences[0].language)
        assertEquals("""
            StreamingMarkdown(
                chunks = stream,
                modifier = Modifier.padding(16.dp)
            )
        """.trimIndent(), fences[0].code)

        assertEquals("python", fences[1].language)
        assertEquals("""
            def greet(name):
                print(f"Hello, {name}!")
        """.trimIndent(), fences[1].code)
    }

    @Test
    fun recognizesHorizontalRuleVariants() {
        listOf("---", "***", "___", "- - -", "* * *", "_ _ _").forEach { rule ->
            assertTrue("Expected divider: $rule", MarkdownParser.parse(rule).single() is Divider)
        }
    }

    @Test
    fun tildeFencePreservesCodeAndLanguage() {
        val block = MarkdownParser.parse("~~~python\ndef greet():\n    print('hello')\n~~~").single() as CodeBlock
        assertEquals("python", block.language)
        assertTrue(block.isClosed)
        assertEquals("def greet():\n    print('hello')", block.code)
    }

    @Test
    fun emptyAndUnclosedCodeFences() {
        val empty = MarkdownParser.parse("```json\n``` ").single() as CodeBlock
        assertTrue(empty.isClosed)
        assertEquals("", empty.code)

        val unclosed = MarkdownParser.parse("```kotlin\nfun main() {}").single() as CodeBlock
        assertFalse(unclosed.isClosed)
        assertEquals("fun main() {}", unclosed.code)
    }

    @Test
    fun codeFenceSplitAcrossMultipleChunksClosesAtFinalFence() = runTest {
        val chunks = flowOf("### Fenced", " code\n\n", "```", "kotlin\n", "fun main()", " {\n}", "\n```")
        val finalText = chunks.scan("") { accumulated, delta -> accumulated + delta }.toList().last()
        val blocks = MarkdownParser.parse(finalText)
        val fences = blocks.filterIsInstance<CodeBlock>()
        assertEquals(1, fences.size)
        assertEquals("kotlin", fences[0].language)
        assertTrue(fences[0].isClosed)
        assertEquals("fun main() {\n}", fences[0].code)
    }
}