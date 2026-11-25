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

## Use cases and examples

### Render saved Markdown with the defaults

Use the `text` overload for completed responses, such as chat history. If you omit `style` and `highlighter`, the library uses `markdownStyle()` and `SimpleCodeHighlighter`.

```kotlin
@Composable
fun SavedAssistantMessage(markdown: String) {
    StreamingMarkdown(text = markdown)
}
```

The defaults can also be passed explicitly:

```kotlin
StreamingMarkdown(
    text = markdown,
    modifier = Modifier.fillMaxWidth(),
    style = markdownStyle(),
    highlighter = SimpleCodeHighlighter,
)
```

### Render a live response with the defaults

Pass a `Flow<String>` to the `chunks` overload. The renderer collects the flow and updates the Markdown as text arrives.

```kotlin
@Composable
fun LiveAssistantMessage(response: Flow<String>) {
    StreamingMarkdown(
        chunks = response,
        modifier = Modifier.fillMaxWidth(),
    )
}
```

Keep the flow stable across recompositions. When creating it in Compose, remember it by the message or conversation identifier:

```kotlin
val responseFlow = remember(messageId) {
    viewModel.streamResponse(messageId)
}

StreamingMarkdown(chunks = responseFlow)
```

### Customize the style

Start with `markdownStyle()` to inherit the current Material 3 typography and colors, then copy only the values you want to change:

```kotlin
@Composable
fun StyledAssistantMessage(markdown: String) {
    val defaults = markdownStyle()
    val customStyle = defaults.copy(
        body = defaults.body.copy(fontSize = 16.sp, lineHeight = 24.sp),
        link = defaults.link.copy(color = MaterialTheme.colorScheme.tertiary),
        codeBackground = MaterialTheme.colorScheme.surfaceVariant,
        codeHeaderBackground = MaterialTheme.colorScheme.secondaryContainer,
        blockSpacing = 14.dp,
    )

    StreamingMarkdown(text = markdown, style = customStyle)
}
```

### Customize code highlighting

Implement `CodeHighlighter`, or wrap `SimpleCodeHighlighter` and adjust its syntax colors. This example keeps the built-in language support and changes the function color:

```kotlin
private val appCodeHighlighter = CodeHighlighter { code, language, colors ->
    SimpleCodeHighlighter.highlight(
        code = code,
        language = language,
        colors = colors.copy(function = Color(0xFF7C4DFF)),
    )
}

@Composable
fun HighlightedAssistantMessage(markdown: String) {
    StreamingMarkdown(
        text = markdown,
        highlighter = appCodeHighlighter,
    )
}
```

### Handle link taps

Use `onLinkClick` to route tapped Markdown links through app navigation, open an in-app browser, or ask the user before leaving the screen:

```kotlin
@Composable
fun AssistantMessage(
    response: Flow<String>,
    navigateToLink: (String) -> Unit,
) {
    StreamingMarkdown(
        chunks = response,
        modifier = Modifier.fillMaxWidth(),
        onLinkClick = { url -> navigateToLink(url) },
    )
}
```

When `onLinkClick` is provided, the app handles link taps. Without it, the renderer uses Compose's default URL handling for supported absolute links.

### Handle stream errors

Use `onError` to report a failed response, show an error message, or trigger retry UI. Cancellation does not call this callback:

```kotlin
@Composable
fun AssistantMessageWithErrors(
    response: Flow<String>,
    reportError: (Throwable) -> Unit,
) {
    StreamingMarkdown(
        chunks = response,
        modifier = Modifier.fillMaxWidth(),
        onError = { error -> reportError(error) },
    )
}
```

### Handle successful completion

Use `onComplete` for work that should happen after the flow finishes normally, such as marking a response complete or enabling follow-up actions:

```kotlin
@Composable
fun AssistantMessageOnComplete(
    response: Flow<String>,
    onResponseComplete: () -> Unit,
) {
    StreamingMarkdown(
        chunks = response,
        modifier = Modifier.fillMaxWidth(),
        onComplete = { onResponseComplete() },
    )
}
```

`onComplete` runs after the final text has been handed to the UI. It is not called when the flow fails or is cancelled.

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
- `<br>` line breaks

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
- Arbitrary/raw HTML rendering
- Mermaid diagrams
- LaTeX or math expressions

## Requirements

- Android `minSdk 24` or higher
- Kotlin `2.2.10` or newer
- Jetpack Compose
- Material 3
- Kotlin Coroutines `1.11.0` or newer
- Java 11 source compatibility

The library uses Jetpack Compose UI, Foundation, and Material 3. The Compose implementation is built with the Compose BOM `2024.09.00`. Applications can manage their Compose dependencies through their own BOM, but should keep Compose UI and Material 3 versions compatible with each other.

For streamed Markdown, the consuming application should include Coroutines and expose the response as `kotlinx.coroutines.flow.Flow<String>`.

## Installation

Until the Maven Central release is available, use the library as a local Gradle module:

```kotlin
implementation(project(":streammarkdowncompose"))
```

After publication, add the Maven Central artifact:

```kotlin
implementation("io.github.valentinerutto:streammarkdowncompose:1.0.0")
```

## License
Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.
