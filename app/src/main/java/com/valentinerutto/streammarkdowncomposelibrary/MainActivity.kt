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

                                This content is rendered as it streams.

                                You can render **bold text**, `inline code`, lists, quotes, and fenced code:

                                - Native Jetpack Compose
                                - Incremental streaming
                                - Copyable code blocks

                                > Built for AI-generated chat responses.

                                ```Kotlin
                                StreamingMarkdown(
                                    chunks = stream,
                                    modifier = Modifier.padding(16.dp)
                                )
                                ```
                            """.trimIndent()

                            content.split(Regex("(?<=\\n)|(?<= )")).forEach { chunk ->
                                emit(chunk)
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