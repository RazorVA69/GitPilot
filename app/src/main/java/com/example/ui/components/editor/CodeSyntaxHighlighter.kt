package com.example.ui.components.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GitAccent
import com.example.ui.theme.GitText1
import com.example.ui.theme.GitText2
import com.example.ui.theme.GitText3

enum class SupportedLanguage {
    KOTLIN, JAVA, JAVASCRIPT, TYPESCRIPT, PYTHON, JSON, XML_HTML, CSS,
    RUST, GO, C_CPP, SHELL, YAML, SQL, MARKDOWN, PLAIN_TEXT;

    companion object {
        fun fromFileName(fileName: String): SupportedLanguage {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "kt", "kts" -> KOTLIN
                "java" -> JAVA
                "js", "mjs", "cjs", "jsx" -> JAVASCRIPT
                "ts", "tsx" -> TYPESCRIPT
                "py", "pyw" -> PYTHON
                "json", "json5", "geojson", "rc" -> JSON
                "xml", "html", "htm", "xhtml", "svg" -> XML_HTML
                "css", "scss", "sass", "less" -> CSS
                "rs" -> RUST
                "go" -> GO
                "c", "cpp", "cc", "cxx", "h", "hpp", "hh" -> C_CPP
                "sh", "bash", "zsh", "fish" -> SHELL
                "yml", "yaml" -> YAML
                "sql" -> SQL
                "md", "markdown", "mdown" -> MARKDOWN
                else -> {
                    if (fileName.startsWith(".env") || fileName == "Makefile" || fileName == "Dockerfile") SHELL
                    else PLAIN_TEXT
                }
            }
        }
    }
}

// Light Modern Syntax Color Palette
object SyntaxColors {
    val Keyword = Color(0xFF7C3AED)        // Deep Purple / Violet
    val TypeName = Color(0xFF0284C7)       // Deep Cyan / Azure
    val FunctionName = Color(0xFF2563EB)   // Royal Blue
    val StringLiteral = Color(0xFF0F9D74)  // Emerald Green
    val NumberLiteral = Color(0xFFD97706)  // Amber / Warm Orange
    val Comment = Color(0xFF94A3B8)        // Slate Muted Gray
    val Annotation = Color(0xFFE11D48)     // Rose / Ruby
    val Property = Color(0xFF0D9488)       // Teal
    val Punctuation = Color(0xFF64748B)    // Cool Gray
    val BracketHighlight = Color(0xFF0F9D74) // Emerald glow for matching bracket
    val BracketMatchBg = Color(0x330F9D74)  // Soft Emerald highlight box
}

class SyntaxHighlighter(private val language: SupportedLanguage) {

    private val keywords: Set<String> = when (language) {
        SupportedLanguage.KOTLIN -> setOf(
            "package", "import", "class", "interface", "object", "enum", "sealed", "data",
            "val", "var", "fun", "constructor", "init", "this", "super", "if", "else", "when",
            "for", "while", "do", "return", "break", "continue", "throw", "try", "catch",
            "finally", "override", "open", "abstract", "final", "private", "protected", "public",
            "internal", "lateinit", "lazy", "companion", "infix", "inline", "tailrec", "operator",
            "suspend", "typealias", "is", "as", "in", "out", "by", "get", "set", "true", "false", "null"
        )
        SupportedLanguage.JAVA -> setOf(
            "package", "import", "class", "interface", "enum", "extends", "implements",
            "public", "private", "protected", "static", "final", "abstract", "synchronized",
            "volatile", "transient", "native", "strictfp", "void", "boolean", "byte", "char",
            "short", "int", "long", "float", "double", "if", "else", "switch", "case", "default",
            "while", "do", "for", "break", "continue", "return", "throw", "throws", "try", "catch",
            "finally", "new", "this", "super", "instanceof", "assert", "true", "false", "null"
        )
        SupportedLanguage.JAVASCRIPT, SupportedLanguage.TYPESCRIPT -> setOf(
            "const", "let", "var", "function", "return", "if", "else", "for", "while", "do",
            "switch", "case", "default", "break", "continue", "try", "catch", "finally", "throw",
            "class", "extends", "super", "this", "new", "import", "export", "from", "as", "default",
            "async", "await", "yield", "typeof", "instanceof", "in", "of", "delete", "void",
            "true", "false", "null", "undefined", "NaN", "interface", "type", "enum", "implements",
            "declare", "abstract", "private", "protected", "public", "readonly", "override"
        )
        SupportedLanguage.PYTHON -> setOf(
            "def", "class", "return", "if", "elif", "else", "for", "while", "break", "continue",
            "try", "except", "finally", "raise", "import", "from", "as", "global", "nonlocal",
            "lambda", "yield", "with", "pass", "assert", "del", "async", "await", "and", "or",
            "not", "is", "in", "True", "False", "None"
        )
        SupportedLanguage.RUST -> setOf(
            "fn", "let", "mut", "const", "static", "struct", "enum", "trait", "impl", "type",
            "mod", "use", "pub", "crate", "self", "Self", "super", "if", "else", "match", "while",
            "loop", "for", "in", "return", "break", "continue", "move", "ref", "where", "async",
            "await", "unsafe", "dyn", "true", "false", "Some", "None", "Ok", "Err"
        )
        SupportedLanguage.GO -> setOf(
            "package", "import", "func", "return", "var", "const", "type", "struct", "interface",
            "map", "chan", "go", "select", "defer", "if", "else", "switch", "case", "default",
            "for", "range", "break", "continue", "fallthrough", "true", "false", "nil", "iota"
        )
        SupportedLanguage.C_CPP -> setOf(
            "auto", "break", "case", "char", "const", "continue", "default", "do", "double",
            "else", "enum", "extern", "float", "for", "goto", "if", "int", "long", "register",
            "return", "short", "signed", "sizeof", "static", "struct", "switch", "typedef",
            "union", "unsigned", "void", "volatile", "while", "class", "namespace", "template",
            "typename", "public", "protected", "private", "virtual", "override", "true", "false",
            "nullptr", "using", "inline", "constexpr", "include", "define", "ifdef", "ifndef", "endif"
        )
        SupportedLanguage.SHELL -> setOf(
            "if", "then", "else", "elif", "fi", "case", "esac", "for", "while", "until", "do",
            "done", "in", "function", "select", "time", "return", "exit", "export", "local",
            "readonly", "unset", "shift", "source", "echo", "cd", "pwd", "true", "false"
        )
        SupportedLanguage.SQL -> setOf(
            "select", "from", "where", "insert", "into", "update", "delete", "create", "table",
            "drop", "alter", "add", "column", "primary", "key", "foreign", "references", "join",
            "inner", "left", "right", "full", "outer", "on", "group", "by", "order", "having",
            "limit", "offset", "union", "all", "distinct", "as", "and", "or", "not", "in",
            "between", "like", "is", "null", "case", "when", "then", "else", "end", "count",
            "sum", "avg", "min", "max", "asc", "desc", "true", "false"
        )
        SupportedLanguage.CSS -> setOf(
            "import", "media", "charset", "keyframes", "supports", "font-face", "root",
            "inherit", "initial", "unset", "none", "auto", "important"
        )
        SupportedLanguage.JSON, SupportedLanguage.XML_HTML, SupportedLanguage.YAML,
        SupportedLanguage.MARKDOWN, SupportedLanguage.PLAIN_TEXT -> emptySet()
    }

    fun highlight(
        text: String,
        matchingBracketIndices: Pair<Int, Int>? = null
    ): AnnotatedString {
        if (language == SupportedLanguage.PLAIN_TEXT || text.isEmpty()) {
            return buildAnnotatedString {
                append(text)
                applyBracketHighlight(this, text, matchingBracketIndices)
            }
        }

        return buildAnnotatedString {
            append(text)

            var i = 0
            val len = text.length

            while (i < len) {
                val c = text[i]

                // Line comment
                if (c == '/' && i + 1 < len && text[i + 1] == '/' && language != SupportedLanguage.CSS) {
                    val end = text.indexOf('\n', i).let { if (it == -1) len else it }
                    addStyle(SpanStyle(color = SyntaxColors.Comment, fontStyle = FontStyle.Italic), i, end)
                    i = end
                    continue
                }

                // Hash comment (#) for Python, Shell, YAML
                if (c == '#' && (language == SupportedLanguage.PYTHON || language == SupportedLanguage.SHELL || language == SupportedLanguage.YAML)) {
                    val end = text.indexOf('\n', i).let { if (it == -1) len else it }
                    addStyle(SpanStyle(color = SyntaxColors.Comment, fontStyle = FontStyle.Italic), i, end)
                    i = end
                    continue
                }

                // SQL comment (--)
                if (c == '-' && i + 1 < len && text[i + 1] == '-' && language == SupportedLanguage.SQL) {
                    val end = text.indexOf('\n', i).let { if (it == -1) len else it }
                    addStyle(SpanStyle(color = SyntaxColors.Comment, fontStyle = FontStyle.Italic), i, end)
                    i = end
                    continue
                }

                // Block comment (/* ... */)
                if (c == '/' && i + 1 < len && text[i + 1] == '*') {
                    val end = text.indexOf("*/", i + 2).let { if (it == -1) len else it + 2 }
                    addStyle(SpanStyle(color = SyntaxColors.Comment, fontStyle = FontStyle.Italic), i, end)
                    i = end
                    continue
                }

                // XML/HTML Comment (<!-- ... -->)
                if (c == '<' && i + 3 < len && text.substring(i, (i + 4).coerceAtMost(len)) == "<!--") {
                    val end = text.indexOf("-->", i + 4).let { if (it == -1) len else it + 3 }
                    addStyle(SpanStyle(color = SyntaxColors.Comment, fontStyle = FontStyle.Italic), i, end)
                    i = end
                    continue
                }

                // XML / HTML Tags (<tag ... > or </tag>)
                if ((language == SupportedLanguage.XML_HTML) && (c == '<')) {
                    val tagEnd = text.indexOf('>', i)
                    if (tagEnd != -1) {
                        addStyle(SpanStyle(color = SyntaxColors.TypeName, fontWeight = FontWeight.Bold), i, tagEnd + 1)
                        i = tagEnd + 1
                        continue
                    }
                }

                // Annotations (@Something)
                if (c == '@' && (language == SupportedLanguage.KOTLIN || language == SupportedLanguage.JAVA || language == SupportedLanguage.TYPESCRIPT || language == SupportedLanguage.PYTHON)) {
                    var end = i + 1
                    while (end < len && (text[end].isLetterOrDigit() || text[end] == '.' || text[end] == '_')) {
                        end++
                    }
                    if (end > i + 1) {
                        addStyle(SpanStyle(color = SyntaxColors.Annotation, fontWeight = FontWeight.SemiBold), i, end)
                        i = end
                        continue
                    }
                }

                // Strings ("..." or '...' or `...`)
                if (c == '"' || c == '\'' || c == '`') {
                    val quote = c
                    // Check for multi-line string in Kotlin / Python
                    val isTriple = i + 2 < len && text[i + 1] == quote && text[i + 2] == quote
                    if (isTriple) {
                        val triple = "$quote$quote$quote"
                        val end = text.indexOf(triple, i + 3).let { if (it == -1) len else it + 3 }
                        addStyle(SpanStyle(color = SyntaxColors.StringLiteral), i, end)
                        i = end
                        continue
                    } else {
                        var end = i + 1
                        while (end < len) {
                            if (text[end] == '\\') {
                                end += 2 // Skip escaped character
                                continue
                            }
                            if (text[end] == quote || text[end] == '\n') {
                                if (text[end] == quote) end++
                                break
                            }
                            end++
                        }
                        addStyle(SpanStyle(color = SyntaxColors.StringLiteral), i, end.coerceAtMost(len))
                        i = end
                        continue
                    }
                }

                // Numbers (0-9 or hex 0x)
                if (c.isDigit() && (i == 0 || !text[i - 1].isLetterOrDigit() && text[i - 1] != '_')) {
                    var end = i + 1
                    while (end < len && (text[end].isLetterOrDigit() || text[end] == '.' || text[end] == '_')) {
                        end++
                    }
                    addStyle(SpanStyle(color = SyntaxColors.NumberLiteral, fontWeight = FontWeight.Medium), i, end)
                    i = end
                    continue
                }

                // Words / Identifiers / Keywords
                if (c.isLetter() || c == '_') {
                    var end = i + 1
                    while (end < len && (text[end].isLetterOrDigit() || text[end] == '_')) {
                        end++
                    }
                    val word = text.substring(i, end)

                    if (keywords.contains(word) || (language == SupportedLanguage.SQL && keywords.contains(word.lowercase()))) {
                        addStyle(SpanStyle(color = SyntaxColors.Keyword, fontWeight = FontWeight.Bold), i, end)
                    } else if (word[0].isUpperCase() && (language == SupportedLanguage.KOTLIN || language == SupportedLanguage.JAVA || language == SupportedLanguage.TYPESCRIPT || language == SupportedLanguage.RUST)) {
                        // Class/Type Name convention (PascalCase)
                        addStyle(SpanStyle(color = SyntaxColors.TypeName, fontWeight = FontWeight.SemiBold), i, end)
                    } else if (end < len && text[end] == '(') {
                        // Function Call or definition
                        addStyle(SpanStyle(color = SyntaxColors.FunctionName), i, end)
                    } else if (language == SupportedLanguage.JSON && end < len && text.substring(end).trimStart().startsWith(":")) {
                        // JSON Key
                        addStyle(SpanStyle(color = SyntaxColors.Property, fontWeight = FontWeight.Medium), i, end)
                    } else if (language == SupportedLanguage.YAML && end < len && text.substring(end).trimStart().startsWith(":")) {
                        // YAML Key
                        addStyle(SpanStyle(color = SyntaxColors.Property, fontWeight = FontWeight.Bold), i, end)
                    }

                    i = end
                    continue
                }

                // Brackets & Punctuation
                if (c in "{}[],();:") {
                    addStyle(SpanStyle(color = SyntaxColors.Punctuation), i, i + 1)
                }

                i++
            }

            // Highlight bracket pair matching under cursor
            applyBracketHighlight(this, text, matchingBracketIndices)
        }
    }

    private fun applyBracketHighlight(
        builder: AnnotatedString.Builder,
        text: String,
        matchingBracketIndices: Pair<Int, Int>?
    ) {
        matchingBracketIndices?.let { (first, second) ->
            if (first in text.indices) {
                builder.addStyle(
                    SpanStyle(
                        color = SyntaxColors.BracketHighlight,
                        background = SyntaxColors.BracketMatchBg,
                        fontWeight = FontWeight.ExtraBold
                    ),
                    first, first + 1
                )
            }
            if (second in text.indices) {
                builder.addStyle(
                    SpanStyle(
                        color = SyntaxColors.BracketHighlight,
                        background = SyntaxColors.BracketMatchBg,
                        fontWeight = FontWeight.ExtraBold
                    ),
                    second, second + 1
                )
            }
        }
    }
}

class CodeSyntaxVisualTransformation(
    private val language: SupportedLanguage,
    private val matchingBracketIndices: Pair<Int, Int>? = null
) : VisualTransformation {
    private val highlighter = SyntaxHighlighter(language)

    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = highlighter.highlight(text.text, matchingBracketIndices)
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}

// Bracket Matching Utilities
object BracketMatcher {
    private val OPEN_TO_CLOSE = mapOf('(' to ')', '{' to '}', '[' to ']', '<' to '>')
    private val CLOSE_TO_OPEN = mapOf(')' to '(', '}' to '{', ']' to '[', '>' to '<')

    fun findMatchingBracket(text: String, cursorPosition: Int): Pair<Int, Int>? {
        if (text.isEmpty()) return null

        // Check if cursor is on or adjacent to a bracket
        val candidates = listOf(cursorPosition, cursorPosition - 1).filter { it in text.indices }

        for (pos in candidates) {
            val char = text[pos]

            if (OPEN_TO_CLOSE.containsKey(char)) {
                val target = OPEN_TO_CLOSE[char]!!
                var depth = 1
                for (i in (pos + 1) until text.length) {
                    if (text[i] == char) depth++
                    else if (text[i] == target) {
                        depth--
                        if (depth == 0) return Pair(pos, i)
                    }
                }
            } else if (CLOSE_TO_OPEN.containsKey(char)) {
                val target = CLOSE_TO_OPEN[char]!!
                var depth = 1
                for (i in (pos - 1) downTo 0) {
                    if (text[i] == char) depth++
                    else if (text[i] == target) {
                        depth--
                        if (depth == 0) return Pair(i, pos)
                    }
                }
            }
        }
        return null
    }

    fun computeAutoIndent(currentText: String, cursorPosition: Int): String {
        if (cursorPosition <= 0 || cursorPosition > currentText.length) return ""

        val textBeforeCursor = currentText.substring(0, cursorPosition)
        val lastLine = textBeforeCursor.substringAfterLast('\n', "")

        // Extract whitespace prefix of previous line
        val indentPrefix = lastLine.takeWhile { it == ' ' || it == '\t' }
        val trimmed = lastLine.trimEnd()

        // If line ends with block openers, increase indent by 2 spaces
        val extraIndent = if (trimmed.endsWith("{") || trimmed.endsWith("(") || trimmed.endsWith("[") || trimmed.endsWith(":")) {
            "  "
        } else {
            ""
        }

        return indentPrefix + extraIndent
    }
}
