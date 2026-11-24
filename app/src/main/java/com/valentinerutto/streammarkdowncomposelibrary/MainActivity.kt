package com.valentinerutto.streammarkdowncomposelibrary

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.valentinerutto.streammarkdowncompose.StreamingMarkdown
import com.valentinerutto.streammarkdowncomposelibrary.ui.theme.StreamMarkdownComposeLibraryTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StreamMarkdownComposeLibraryTheme {
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

                                Safe inline HTML: <strong>bold HTML</strong>, <em>italic HTML</em>, and <code>inline HTML code</code>.

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
                                | HTML | Limited | Safe inline/block tags |

                                ### Quote

                                > Built for AI-generated chat responses.
                                > Multiple quoted lines stay together.

                                ### Block HTML

                                <p>This is <strong>bold HTML text</strong> inside a block.</p>

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

                    StreamingMarkdown(
                        chunks = markdownFlow,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp)
                    )
                }
            }
        }
    }
}