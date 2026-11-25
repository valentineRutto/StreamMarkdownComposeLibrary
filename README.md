# StreamMarkdownComposeLibrary

StreamMarkdownCompose — A native Android streaming content renderer Library for AI applications.

Send a stream. Let the library figure out how to render it.

## Overview

StreamMarkdownCompose is designed for apps that need to render streaming AI-generated content in a native Android UI. Instead of forcing each response format to be manually handled, the library interprets the incoming stream and decides how to present it in Compose.

## Purpose

- Render streamed text and structured content in Android apps
- Fit naturally into Jetpack Compose UIs
- Help AI-driven experiences show content without custom parsing logic for every response type

## Getting started

This project is currently in its early library/app setup phase. Add it to your Android project and build your UI around the stream-rendering primitives as the library evolves.

## Example

```kotlin
// Example intent:
// Send a stream of AI output into StreamMarkdownCompose
// and let the library render the content appropriately.
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
