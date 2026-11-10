package com.valentinerutto.streammarkdowncompose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle

data class SyntaxColors(
    val keyword: Color,
    val string: Color,
    val comment: Color,
    val number: Color,
)

/** Plug in your own (e.g. a Prism4j / highlight.js bridge) via [StreamingMarkdown]'s `highlighter` param. */
fun interface CodeHighlighter {
    fun highlight(code: String, language: String?, colors: SyntaxColors): AnnotatedString
}

/** Lightweight regex tokenizer: comments, strings, numbers, common keywords. Tolerates unterminated input. */
object SimpleCodeHighlighter : CodeHighlighter {

    private val hashLangs = setOf("python", "py", "bash", "sh", "shell", "zsh", "yaml", "yml", "ruby", "rb", "toml", "dockerfile")

    private val keywords = setOf(
        "fun", "val", "var", "class", "object", "interface", "sealed", "data", "enum", "override", "private", "public",
        "protected", "internal", "suspend", "inline", "when", "if", "else", "for", "while", "do", "return", "break",
        "continue", "in", "is", "as", "try", "catch", "finally", "throw", "import", "package", "null", "true", "false",
        "this", "super", "new", "static", "final", "void", "int", "long", "double", "float", "boolean", "string",
        "def", "lambda", "from", "with", "pass", "None", "True", "False", "elif", "except", "async", "await",
        "function", "const", "let", "export", "default", "extends", "implements", "typeof", "struct", "impl", "fn",
        "pub", "mut", "use", "match", "self", "go", "func", "type", "switch", "case",
    )

    private const val STRING = "(\"{3}[\\s\\S]*?(?:\"{3}|\\z)|\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])*')"
    private const val NUMBER = "(\\b\\d[\\d_]*(?:\\.\\d+)?[fFLuU]*\\b)"
    private const val WORD = "([A-Za-z_][A-Za-z0-9_]*)"

    private val slashRegex = Regex(listOf("(//[^\\n]*|/\\*[\\s\\S]*?(?:\\*/|\\z))", STRING, NUMBER, WORD).joinToString("|"))
    private val hashRegex = Regex(listOf("(#[^\\n]*|//[^\\n]*)", STRING, NUMBER, WORD).joinToString("|"))

    override fun highlight(code: String, language: String?, colors: SyntaxColors): AnnotatedString {
        val re = if (language.orEmpty().lowercase() in hashLangs) hashRegex else slashRegex
        return buildAnnotatedString {
            var last = 0
            for (m in re.findAll(code)) {
                if (m.range.first > last) append(code.substring(last, m.range.first))
                val color = when {
                    m.groups[1] != null -> colors.comment
                    m.groups[2] != null -> colors.string
                    m.groups[3] != null -> colors.number
                    m.value in keywords -> colors.keyword
                    else -> null
                }
                if (color == null) append(m.value) else withStyle(SpanStyle(color = color)) { append(m.value) }
                last = m.range.last + 1
            }
            if (last < code.length) append(code.substring(last))
        }
    }
}