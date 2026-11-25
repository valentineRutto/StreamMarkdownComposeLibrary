package com.valentinerutto.streammarkdowncomposelibrary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.valentinerutto.streammarkdowncompose.CodeHighlighter
import com.valentinerutto.streammarkdowncompose.SimpleCodeHighlighter
import com.valentinerutto.streammarkdowncompose.StreamingMarkdown
import com.valentinerutto.streammarkdowncompose.markdownStyle
import com.valentinerutto.streammarkdowncomposelibrary.ui.theme.StreamMarkdownComposeLibraryTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StreamMarkdownComposeLibraryTheme {
                var streamStatus by remember { mutableStateOf("Streaming…") }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val markdownFlow = remember {
                        flow {
                            val content = """
                                # StreamMarkdownCompose

                                ## Streaming Markdown Test
                                ### H3 Heading
                                #### H4 Heading
                                ##### H5 Heading
                                ###### H6 Heading

                                This stream supports **bold**, __bold too__, *italic*, _italic too_, ~~strikethrough~~, and `inline code`.

                                You can also use [safe links](https://developer.android.com) and escaped characters: \*not italic\*, \[not a link\], \# not a heading, and \| not a table separator.

                                HTML note: <br> creates a line break; block HTML tags are stripped to text.

                                ### Unordered and nested lists

                                - [ ] Unchecked task
                                - [x] Checked task
                                - Parent item
                                  - Nested child
                                    - Deeply nested child
                                  - Another child
                                - Final item

                                ### Ordered and nested lists

                                1. First item
                                   - Nested unordered item
                                   - Another nested item
                                2. Second item
                                   1. Nested ordered item
                                   2. Another nested item

                                ### Table

                                | Feature | Status | Notes |
                                |:--------|:------:|------:|
                                | Streaming | Ready | Parses deltas |
                                | Nested lists | Ready | Indentation-aware |
                                | Task lists | Ready | Read-only checkboxes |
                                | Tables | Ready | Horizontally scrollable |
                                | Escapes | Ready | Backslash-aware |
                                | HTML | Limited | Block tags stripped; `<br>` supported |

                                ### Quote

                                > Built for AI-generated chat responses.
                                > Multiple quoted lines stay together.

                                ### Block HTML

                                <p>Block HTML tags are stripped, leaving this text.</p>

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

                            content.lines().forEach { line ->
                                emit("$line\n")
                                delay(18)
                            }
                        }
                    }

                    val defaultStyle = markdownStyle()
                    val customStyle = defaultStyle.copy(
                        body = defaultStyle.body.copy(fontSize = 16.sp, lineHeight = 24.sp),
                        link = defaultStyle.link.copy(
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline,
                        ),
                        codeBackground = MaterialTheme.colorScheme.surfaceVariant,
                        codeHeaderBackground = MaterialTheme.colorScheme.secondaryContainer,
                        blockSpacing = 14.dp,
                    )
                    val customHighlighter = remember {
                        CodeHighlighter { code, language, colors ->
                            SimpleCodeHighlighter.highlight(
                                code = code,
                                language = language,
                                colors = colors.copy(function = Color(0xFF7C4DFF)),
                            )
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                        Text(
                            text = "Stream status: $streamStatus",
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                        )

                        StreamingMarkdown(
                            chunks = markdownFlow,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(16.dp),
                            style = customStyle,
                            highlighter = customHighlighter,
                            onLinkClick = { url -> streamStatus = "Link selected: $url" },
                            onError = { error ->
                                streamStatus = "Stream failed: ${error.message ?: "unknown error"}"
                            },
                            onComplete = { streamStatus = "Completed" },
                        )
                    }
                }
            }
        }
    }
}
