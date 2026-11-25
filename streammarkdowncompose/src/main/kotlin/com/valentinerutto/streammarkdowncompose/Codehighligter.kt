package com.valentinerutto.streammarkdowncompose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle

/**
 * The first four colors are required (same positional order as before). The rest default to
 * [Color.Unspecified] = "don't color this token kind", so existing `SyntaxColors(a, b, c, d)` calls still compile.
 */
data class SyntaxColors(
    val keyword: Color,
    val string: Color,
    val comment: Color,
    val number: Color,
    val type: Color = Color.Unspecified,       // ClassNames, XML attribute names
    val function: Color = Color.Unspecified,   // name( ... )
    val annotation: Color = Color.Unspecified, // @Composable, @dataclass
)

/** Plug in your own (e.g. a Prism4j / highlight.js bridge) via [StreamingMarkdown]'s `highlighter` param. */
fun interface CodeHighlighter {
    fun highlight(code: String, language: String?, colors: SyntaxColors): AnnotatedString
}

/**
 * Lightweight, language-aware regex tokenizer. Each language gets its own comment syntax and keyword set;
 * unknown languages only get strings, numbers and comments (no guessed keywords); plain-text style blocks
 * (text, console, diff, markdown...) are left uncolored. Tolerates unterminated input, so it is safe on
 * a code block that is still streaming.
 */
object SimpleCodeHighlighter : CodeHighlighter {

    /** Beyond this the block is shown uncolored (the regex runs on every chunk while streaming). */
    private const val MAX_CHARS = 30_000

    private const val NUMBER =
        "\\b(?:0[xX][0-9A-Fa-f_]+|0[bB][01_]+|\\d[\\d_]*(?:\\.\\d+)?(?:[eE][+-]?\\d+)?[fFLlUuDd]*)\\b"
    private const val FUNCTION = "[A-Za-z_]\\w*(?=\\s*\\()"
    private const val WORD = "[A-Za-z_]\\w*"

    // ----------------------------------------------------------------------------------- language model

    private class Lang(
        val lineComments: List<String> = emptyList(),
        val blockComment: Pair<String, String>? = null,
        keywordList: List<String> = emptyList(),
        val ignoreCase: Boolean = false,
        val tripleDouble: Boolean = false,
        val tripleSingle: Boolean = false,
        val backtickStrings: Boolean = false,
        val annotations: Boolean = false,
        val types: Boolean = false, // Capitalized words get the type color
    ) {
        private val keywords = keywordList.map { if (ignoreCase) it.lowercase() else it }.toSet()
        fun isKeyword(word: String) = (if (ignoreCase) word.lowercase() else word) in keywords

        /** Groups: 1 comment, 2 string, 3 number, 4 annotation, 5 function call, 6 any other word. */
        val regex: Regex by lazy {
            val comment = buildList {
                lineComments.forEach { add(lineComment(it)) }
                blockComment?.let { (open, close) ->
                    add(Regex.escape(open) + "[\\s\\S]*?(?:" + Regex.escape(close) + "|\\z)")
                }
            }.joinToString("|").ifEmpty { "(?!)" } // (?!) never matches
            val string = buildList {
                if (tripleDouble) add("\"{3}[\\s\\S]*?(?:\"{3}|\\z)")
                if (tripleSingle) add("'{3}[\\s\\S]*?(?:'{3}|\\z)")
                // double quotes may be unterminated at end of line: colors the string WHILE it streams in
                add("\"(?:\\\\.|[^\"\\\\\\n])*(?:\"|(?=\\n)|\\z)")
                add("'(?:\\\\.|[^'\\\\\\n])*'")
                if (backtickStrings) add("`(?:\\\\.|[^`\\\\])*(?:`|\\z)")
            }.joinToString("|")
            val annotation = if (annotations) "@[A-Za-z_][\\w.]*" else "(?!)"
            Regex("($comment)|($string)|($NUMBER)|($annotation)|($FUNCTION)|($WORD)")
        }

        private fun lineComment(marker: String): String = when (marker) {
            "#" -> "(?<![^\\s])#[^\\n]*"  // only after whitespace/line start: not $#, ${#x}, a#b
            "//" -> "(?<!:)//[^\\n]*"     // not the :// in a URL
            else -> Regex.escape(marker) + "[^\\n]*"
        }
    }

    private fun words(s: String) = s.split(' ')

    private val KOTLIN = Lang(
        lineComments = listOf("//"), blockComment = "/*" to "*/", tripleDouble = true, annotations = true, types = true,
        keywordList = words(
            "fun val var class object interface sealed data enum override private public protected internal suspend " +
                    "inline when if else for while do return break continue in is as try catch finally throw import package " +
                    "null true false this super typealias companion constructor init by where lateinit open abstract final " +
                    "const operator infix reified vararg",
        ),
    )
    private val JAVA = Lang(
        lineComments = listOf("//"), blockComment = "/*" to "*/", tripleDouble = true, annotations = true, types = true,
        keywordList = words(
            "public private protected class interface enum extends implements new static final void int long double float " +
                    "boolean byte short char if else for while do switch case default break continue return try catch finally " +
                    "throw throws import package null true false this super instanceof abstract synchronized volatile transient " +
                    "var record sealed permits def",
        ),
    )
    private val JS = Lang(
        lineComments = listOf("//"), blockComment = "/*" to "*/", backtickStrings = true, annotations = true, types = true,
        keywordList = words(
            "const let var function return if else for while do switch case default break continue new class extends super " +
                    "import export from as async await try catch finally throw typeof instanceof in of null undefined true false " +
                    "this void delete yield interface type enum implements public private protected readonly static abstract " +
                    "namespace declare",
        ),
    )
    private val PYTHON = Lang(
        lineComments = listOf("#"), tripleDouble = true, tripleSingle = true, annotations = true, types = true,
        keywordList = words(
            "def class return if elif else for while in not and or is import from as with try except finally raise pass " +
                    "break continue lambda yield async await global nonlocal None True False del assert match case self",
        ),
    )
    private val CFAMILY = Lang(
        lineComments = listOf("//"), blockComment = "/*" to "*/", types = true,
        keywordList = words(
            "if else for foreach while do switch case default break continue return struct class enum union typedef static " +
                    "const volatile extern inline void int long short char float double unsigned signed bool new delete this " +
                    "nullptr null true false sizeof namespace using template typename public private protected virtual override " +
                    "final try catch throw goto auto var string async await readonly abstract sealed interface ref out is as lock yield",
        ),
    )
    private val GO = Lang(
        lineComments = listOf("//"), blockComment = "/*" to "*/", backtickStrings = true, types = true,
        keywordList = words(
            "package import func var const type struct interface map chan go defer return if else for range switch case " +
                    "default break continue select fallthrough goto nil true false",
        ),
    )
    private val RUST = Lang(
        lineComments = listOf("//"), blockComment = "/*" to "*/", types = true,
        keywordList = words(
            "fn let mut const static struct enum impl trait pub use mod crate self Self super match if else for while loop " +
                    "in return break continue where as ref move async await dyn unsafe extern type true false",
        ),
    )
    private val SWIFT = Lang(
        lineComments = listOf("//"), blockComment = "/*" to "*/", tripleDouble = true, annotations = true, types = true,
        keywordList = words(
            "func let var class struct enum protocol extension import if else guard switch case default for while repeat in " +
                    "return break continue try catch throw throws defer init deinit self super nil true false static public " +
                    "private internal fileprivate open as is async await where inout some any",
        ),
    )
    private val DART = Lang(
        lineComments = listOf("//"), blockComment = "/*" to "*/", tripleDouble = true, tripleSingle = true,
        annotations = true, types = true,
        keywordList = words(
            "class void var final const late if else for while do switch case default break continue return try catch " +
                    "finally throw import export library part extends implements with mixin abstract static async await yield " +
                    "null true false this super new is as in required enum typedef factory get set on",
        ),
    )
    private val SHELL = Lang(
        lineComments = listOf("#"),
        keywordList = words(
            "if then else elif fi for while until do done case esac in function select return exit export local readonly " +
                    "declare unset source alias",
        ),
    )
    private val SQL = Lang(
        lineComments = listOf("--"), blockComment = "/*" to "*/", ignoreCase = true,
        keywordList = words(
            "select from where and or not in is null as join left right inner outer full cross on group by order having " +
                    "limit offset insert into values update set delete create table alter drop index view primary key foreign " +
                    "references unique default check constraint distinct union all case when then else end exists between like " +
                    "ilike with returning begin commit rollback true false asc desc",
        ),
    )
    private val YAML = Lang(lineComments = listOf("#"), keywordList = words("true false null yes no"))
    private val RUBY = Lang(
        lineComments = listOf("#"), types = true,
        keywordList = words(
            "def class module end if elsif else unless while until for in do begin rescue ensure return yield self nil " +
                    "true false and or not then case when require include",
        ),
    )
    private val DOCKER = Lang(
        lineComments = listOf("#"), ignoreCase = true,
        keywordList = words("from run cmd copy add env expose workdir entrypoint arg user volume label as"),
    )
    private val JSON = Lang(keywordList = words("true false null"))

    /** Unlabeled / unknown language: strings, numbers and comments only. No guessed keywords. */
    private val GENERIC = Lang(
        lineComments = listOf("//", "#"), blockComment = "/*" to "*/", keywordList = words("true false null"),
    )

    private val languages: Map<String, Lang> = buildMap {
        fun reg(lang: Lang, vararg names: String) = names.forEach { put(it, lang) }
        reg(KOTLIN, "kotlin", "kt", "kts", "kotlin-script")
        reg(JAVA, "java", "groovy", "gradle", "scala")
        reg(JS, "javascript", "js", "jsx", "mjs", "typescript", "ts", "tsx", "node")
        reg(PYTHON, "python", "py", "py3", "python3")
        reg(CFAMILY, "c", "h", "cpp", "c++", "cc", "hpp", "cs", "csharp", "c#", "objc", "objective-c")
        reg(GO, "go", "golang")
        reg(RUST, "rust", "rs")
        reg(SWIFT, "swift")
        reg(DART, "dart")
        reg(SHELL, "bash", "sh", "shell", "zsh", "fish", "powershell", "ps1", "bat")
        reg(SQL, "sql", "mysql", "postgresql", "postgres", "sqlite", "plsql")
        reg(YAML, "yaml", "yml", "toml", "ini", "properties", "makefile", "make")
        reg(RUBY, "ruby", "rb", "perl", "r")
        reg(DOCKER, "dockerfile", "docker")
        reg(JSON, "json", "jsonc", "json5")
    }

    private val xmlLangs = setOf("xml", "html", "xhtml", "svg", "xaml", "plist")

    /** Output / prose style blocks: coloring words in them is noise. */
    private val plainLangs = setOf("text", "txt", "plaintext", "plain", "output", "console", "log", "logs", "markdown", "md", "diff", "patch", "csv", "tsv")

    // ----------------------------------------------------------------------------------------- highlight

    override fun highlight(code: String, language: String?, colors: SyntaxColors): AnnotatedString {
        if (code.isEmpty() || code.length > MAX_CHARS) return AnnotatedString(code)
        val key = normalize(language)
        return when {
            key in plainLangs -> AnnotatedString(code)
            key in xmlLangs -> highlightXml(code, colors)
            else -> highlightWith(languages[key] ?: GENERIC, code, colors)
        }
    }

    /** "```kotlin:Main.kt", "language-kotlin", "kotlin {1,3}" all become "kotlin". */
    private fun normalize(language: String?): String = language.orEmpty().trim().lowercase()
        .removePrefix("language-")
        .substringBefore(' ').substringBefore('{').substringBefore(':').substringBefore(',')

    private fun highlightWith(lang: Lang, code: String, c: SyntaxColors): AnnotatedString = buildAnnotatedString {
        var last = 0
        for (m in lang.regex.findAll(code)) {
            if (m.range.first > last) append(code.substring(last, m.range.first))
            val g = m.groups
            val style = when {
                g[1] != null -> c.comment.span(italic = true)
                g[2] != null -> c.string.span()
                g[3] != null -> c.number.span()
                g[4] != null -> c.annotation.span()
                lang.isKeyword(m.value) -> c.keyword.span() // before "function", so `if (` stays a keyword
                g[5] != null -> c.function.span()
                lang.types && m.value[0].isUpperCase() -> c.type.span()
                else -> null
            }
            if (style == null) append(m.value) else withStyle(style) { append(m.value) }
            last = m.range.last + 1
        }
        if (last < code.length) append(code.substring(last))
    }

    // XML / HTML: 1 comment or <?...?>, 2 attribute value (only right after "="), 3 tag name, 4 attribute name, 5 entity
    private val xmlRegex = Regex(
        "(<!--[\\s\\S]*?(?:-->|\\z)|<\\?[\\s\\S]*?(?:\\?>|\\z))" +
                "|((?<==)(?:\"[^\"]*(?:\"|\\z)|'[^']*(?:'|\\z)))" +
                "|(</?[A-Za-z_][\\w:.-]*)" +
                "|([A-Za-z_:][\\w:.-]*(?=\\s*=))" +
                "|(&#?\\w+;)",
    )

    private fun highlightXml(code: String, c: SyntaxColors): AnnotatedString = buildAnnotatedString {
        var last = 0
        for (m in xmlRegex.findAll(code)) {
            if (m.range.first > last) append(code.substring(last, m.range.first))
            val g = m.groups
            val style = when {
                g[1] != null -> c.comment.span(italic = true)
                g[2] != null -> c.string.span()
                g[3] != null -> c.keyword.span()
                g[4] != null -> c.type.orElse(c.function).orElse(c.number).span()
                else -> c.number.span()
            }
            if (style == null) append(m.value) else withStyle(style) { append(m.value) }
            last = m.range.last + 1
        }
        if (last < code.length) append(code.substring(last))
    }

    private fun Color.orElse(other: Color) = if (this == Color.Unspecified) other else this

    private fun Color.span(italic: Boolean = false): SpanStyle? =
        if (this == Color.Unspecified) null
        else SpanStyle(color = this, fontStyle = if (italic) FontStyle.Italic else null)
}