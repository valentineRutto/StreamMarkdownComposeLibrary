# StreamMarkdownCompose

A lightweight, streaming Markdown renderer for Jetpack Compose and native Android applications.

StreamMarkdownCompose turns incremental text—such as AI-generated responses—into structured, styled Compose UI. It supports Markdown blocks, inline formatting, tables, task lists, fenced code blocks, syntax highlighting, and a configurable theme.

## Overview

StreamMarkdownCompose is built for text that arrives a chunk at a time. It maintains the complete streamed response, parses it into immutable Markdown blocks, and updates the Compose UI as new chunks arrive. The same parser and renderer can also be used for complete, stored Markdown messages.

It provides:

- Separate APIs for Kotlin `Flow` streams and complete strings
- Streaming-safe parsing for unfinished Markdown fences and formatting
- Native Jetpack Compose rendering without a WebView
- Material 3-aware default styling
- Customizable text and syntax colors
- Safe handling of links and limited HTML
- A replaceable code highlighter

## Purpose

- Render streaming AI or chat responses naturally in Android apps
- Display Markdown without manually tracking or splitting streamed chunks
- Preserve a valid UI snapshot when a stream is cancelled or parsing an intermediate state
- Integrate into Compose layouts with configurable modifiers and styles
- Support both live streaming messages and complete chat-history messages

## Getting started

This project is currently in its early library/app setup phase. Add it to your Android project and build your UI around the stream-rendering primitives as the library evolves.

## Example

Render a complete Markdown message:

```kotlin
StreamingMarkdown(
    text = """
        # StreamMarkdownCompose

        - Parse incremental AI responses
        - Render **bold**, *italic*, and `inline code`

        ```kotlin
        val greeting = "Hello"
        ```
    """.trimIndent()
)
```

Render text arriving through a Kotlin `Flow`:

```kotlin
val responseFlow: Flow<String> = viewModel.responseStream

StreamingMarkdown(
    chunks = responseFlow,
    modifier = Modifier.fillMaxWidth(),
    onComplete = {
        // Called after the stream finishes successfully
    }
)
```

Keep the `Flow` stable across recompositions. When creating it in Compose, remember it by the message or conversation identifier:

```kotlin
val responseFlow = remember(messageId) {
    viewModel.streamResponse(messageId)
}

StreamingMarkdown(chunks = responseFlow)
```

Customize the renderer theme:

```kotlin
StreamingMarkdown(
    text = markdown,
    style = markdownStyle().copy(
        blockSpacing = 14.dp
    )
)
```

## Supported Markdown

StreamMarkdownCompose is designed for streaming chat and AI responses. It currently supports the following elements.

### Block elements

- Paragraphs
- ATX headings from `#` through `######`
- Fenced code blocks using backticks or tildes
- Unclosed code fences while content is still streaming
- Ordered lists using `1.` or `1)`
- Unordered lists using `-`, `*`, or `+`
- Nested lists
- Task lists using `[ ]` and `[x]` or `[X]`
- Blockquotes using `>`
- Horizontal rules using `---`, `***`, or `___`
- Pipe tables with left, right, and center column alignment
- Limited HTML block tags, stripped and sanitized before rendering

### Inline elements

- Bold: `**text**` and `__text__`
- Italic: `*text*` and `_text_`
- Strikethrough: `~~text~~`
- Inline code using backticks
- Markdown links: `[label](https://example.com)`
- Safe links using `http`, `https`, `mailto`, and `tel`
- Backslash escaping for Markdown punctuation
- Basic HTML entities
- Limited inline HTML:
  - `<b>`, `<strong>`
  - `<i>`, `<em>`
  - `<code>`
  - `<s>`, `<del>`
  - `<br>`

Dangerous HTML such as scripts, styles, iframes, forms, and media elements is removed rather than rendered.

### Code highlighting

`SimpleCodeHighlighter` provides lightweight syntax highlighting for streamed code blocks. It recognizes comments, strings, numbers, and keywords across common language families, including Kotlin, Java, JavaScript, Python, Bash, YAML, Ruby, SQL, XML, HTML, Rust, and Go.

Code highlighting is regex-based and is not a full language parser. A custom highlighter can be supplied through the `CodeHighlighter` interface.

### Currently not supported

- Images
- Setext headings
- Indented code blocks
- Footnotes
- Definition lists
- Reference-style links
- Bare URL/autolink detection
- Arbitrary/raw HTML rendering
- Mermaid diagrams
- LaTeX or math expressions

## License
Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
