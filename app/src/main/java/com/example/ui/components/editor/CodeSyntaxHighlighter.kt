package com.example.ui.components.editor

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

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

// Pre-allocated static SpanStyles to avoid high-volume allocations during scrolling/tab switching
private object SyntaxStyles {
    val Keyword = SpanStyle(color = SyntaxColors.Keyword, fontWeight = FontWeight.Bold)
    val TypeName = SpanStyle(color = SyntaxColors.TypeName, fontWeight = FontWeight.SemiBold)
    val FunctionName = SpanStyle(color = SyntaxColors.FunctionName)
    val StringLiteral = SpanStyle(color = SyntaxColors.StringLiteral)
    val NumberLiteral = SpanStyle(color = SyntaxColors.NumberLiteral, fontWeight = FontWeight.Medium)
    val Comment = SpanStyle(color = SyntaxColors.Comment, fontStyle = FontStyle.Italic)
    val Annotation = SpanStyle(color = SyntaxColors.Annotation, fontWeight = FontWeight.SemiBold)
    val Property = SpanStyle(color = SyntaxColors.Property, fontWeight = FontWeight.Medium)
    val Punctuation = SpanStyle(color = SyntaxColors.Punctuation)
    val BracketHighlight = SpanStyle(
        color = SyntaxColors.BracketHighlight,
        background = SyntaxColors.BracketMatchBg,
        fontWeight = FontWeight.ExtraBold
    )
}

// High-speed Global Base Syntax Cache across all files and editor tabs
object BaseSyntaxCache {
    private const val MAX_ENTRIES = 32

    private data class CacheKey(
        val language: SupportedLanguage,
        val textLength: Int,
        val sampleHash: Int
    )

    private val cache = object : LinkedHashMap<CacheKey, AnnotatedString>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, AnnotatedString>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    private fun computeSampleHash(text: String): Int {
        val len = text.length
        if (len == 0) return 0
        var h = len
        h = 31 * h + text[0].code
        h = 31 * h + text[len / 2].code
        h = 31 * h + text[len - 1].code
        if (len > 80) {
            h = 31 * h + text[40].code
            h = 31 * h + text[len - 40].code
        }
        return h
    }

    @Synchronized
    fun getOrCreate(
        language: SupportedLanguage,
        text: String,
        highlighter: SyntaxHighlighter
    ): AnnotatedString {
        if (language == SupportedLanguage.PLAIN_TEXT || language == SupportedLanguage.MARKDOWN || text.isEmpty()) {
            return AnnotatedString(text)
        }
        val key = CacheKey(language, text.length, computeSampleHash(text))
        cache[key]?.let { return it }

        val highlighted = highlighter.highlightBase(text)
        cache[key] = highlighted
        return highlighted
    }
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

    fun highlight(text: String): AnnotatedString = highlightBase(text)

    fun highlightBase(text: String): AnnotatedString {
        if (language == SupportedLanguage.PLAIN_TEXT || language == SupportedLanguage.MARKDOWN || text.isEmpty()) {
            return AnnotatedString(text)
        }

        // Bound scanning to 120,000 characters for instant sub-millisecond execution even on huge files
        val scanLimit = text.length.coerceAtMost(120_000)

        return buildAnnotatedString {
            append(text)

            var i = 0

            while (i < scanLimit) {
                val c = text[i]

                // Line comment (//)
                if (c == '/' && i + 1 < scanLimit && text[i + 1] == '/' && language != SupportedLanguage.CSS) {
                    val end = text.indexOf('\n', i).let { if (it == -1 || it > scanLimit) scanLimit else it }
                    addStyle(SyntaxStyles.Comment, i, end)
                    i = end
                    continue
                }

                // Hash comment (#) for Python, Shell, YAML
                if (c == '#' && (language == SupportedLanguage.PYTHON || language == SupportedLanguage.SHELL || language == SupportedLanguage.YAML)) {
                    val end = text.indexOf('\n', i).let { if (it == -1 || it > scanLimit) scanLimit else it }
                    addStyle(SyntaxStyles.Comment, i, end)
                    i = end
                    continue
                }

                // SQL comment (--)
                if (c == '-' && i + 1 < scanLimit && text[i + 1] == '-' && language == SupportedLanguage.SQL) {
                    val end = text.indexOf('\n', i).let { if (it == -1 || it > scanLimit) scanLimit else it }
                    addStyle(SyntaxStyles.Comment, i, end)
                    i = end
                    continue
                }

                // Block comment (/* ... */)
                if (c == '/' && i + 1 < scanLimit && text[i + 1] == '*') {
                    val end = text.indexOf("*/", i + 2).let { if (it == -1 || it + 2 > scanLimit) scanLimit else it + 2 }
                    addStyle(SyntaxStyles.Comment, i, end)
                    i = end
                    continue
                }

                // XML/HTML Comment (<!-- ... -->)
                if (c == '<' && i + 3 < scanLimit && text.startsWith("<!--", i)) {
                    val end = text.indexOf("-->", i + 4).let { if (it == -1 || it + 3 > scanLimit) scanLimit else it + 3 }
                    addStyle(SyntaxStyles.Comment, i, end)
                    i = end
                    continue
                }

                // XML / HTML Tags (<tag ... > or </tag>)
                if ((language == SupportedLanguage.XML_HTML) && (c == '<')) {
                    val tagEnd = text.indexOf('>', i)
                    if (tagEnd != -1 && tagEnd < scanLimit) {
                        addStyle(SyntaxStyles.TypeName, i, tagEnd + 1)
                        i = tagEnd + 1
                        continue
                    }
                }

                // Annotations (@Something)
                if (c == '@' && (language == SupportedLanguage.KOTLIN || language == SupportedLanguage.JAVA || language == SupportedLanguage.TYPESCRIPT || language == SupportedLanguage.PYTHON)) {
                    var end = i + 1
                    while (end < scanLimit && (text[end].isLetterOrDigit() || text[end] == '.' || text[end] == '_')) {
                        end++
                    }
                    if (end > i + 1) {
                        addStyle(SyntaxStyles.Annotation, i, end)
                        i = end
                        continue
                    }
                }

                // Strings ("..." or '...' or `...`)
                if (c == '"' || c == '\'' || c == '`') {
                    val quote = c
                    val isTriple = i + 2 < scanLimit && text[i + 1] == quote && text[i + 2] == quote
                    if (isTriple) {
                        val triple = "$quote$quote$quote"
                        val end = text.indexOf(triple, i + 3).let { if (it == -1 || it + 3 > scanLimit) scanLimit else it + 3 }
                        addStyle(SyntaxStyles.StringLiteral, i, end)
                        i = end
                        continue
                    } else {
                        var end = i + 1
                        while (end < scanLimit) {
                            if (text[end] == '\\') {
                                end += 2
                                continue
                            }
                            if (text[end] == quote || text[end] == '\n') {
                                if (text[end] == quote) end++
                                break
                            }
                            end++
                        }
                        addStyle(SyntaxStyles.StringLiteral, i, end.coerceAtMost(scanLimit))
                        i = end
                        continue
                    }
                }

                // Numbers (0-9)
                if (c.isDigit() && (i == 0 || !text[i - 1].isLetterOrDigit() && text[i - 1] != '_')) {
                    var end = i + 1
                    while (end < scanLimit && (text[end].isLetterOrDigit() || text[end] == '.' || text[end] == '_')) {
                        end++
                    }
                    addStyle(SyntaxStyles.NumberLiteral, i, end)
                    i = end
                    continue
                }

                // Words / Identifiers / Keywords
                if (c.isLetter() || c == '_') {
                    var end = i + 1
                    while (end < scanLimit && (text[end].isLetterOrDigit() || text[end] == '_')) {
                        end++
                    }
                    val wordLength = end - i

                    // Quick length check to prevent useless string allocations
                    if (wordLength in 2..18) {
                        val word = text.substring(i, end)
                        if (keywords.contains(word) || (language == SupportedLanguage.SQL && keywords.contains(word.lowercase()))) {
                            addStyle(SyntaxStyles.Keyword, i, end)
                        } else if (word[0].isUpperCase() && (language == SupportedLanguage.KOTLIN || language == SupportedLanguage.JAVA || language == SupportedLanguage.TYPESCRIPT || language == SupportedLanguage.RUST)) {
                            addStyle(SyntaxStyles.TypeName, i, end)
                        } else if (end < scanLimit && text[end] == '(') {
                            addStyle(SyntaxStyles.FunctionName, i, end)
                        } else if ((language == SupportedLanguage.JSON || language == SupportedLanguage.YAML) && end < scanLimit) {
                            var checkPos = end
                            while (checkPos < scanLimit && (text[checkPos] == ' ' || text[checkPos] == '\t')) {
                                checkPos++
                            }
                            if (checkPos < scanLimit && text[checkPos] == ':') {
                                addStyle(SyntaxStyles.Property, i, end)
                            }
                        }
                    } else if (end < scanLimit && text[end] == '(') {
                        addStyle(SyntaxStyles.FunctionName, i, end)
                    }

                    i = end
                    continue
                }

                // Brackets & Punctuation
                if (c in "{}[],();:") {
                    addStyle(SyntaxStyles.Punctuation, i, i + 1)
                }

                i++
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
        val raw = text.text
        val base = BaseSyntaxCache.getOrCreate(language, raw, highlighter)

        if (matchingBracketIndices == null) {
            return TransformedText(base, OffsetMapping.Identity)
        }

        val (first, second) = matchingBracketIndices
        val hasFirst = first in raw.indices
        val hasSecond = second in raw.indices
        if (!hasFirst && !hasSecond) {
            return TransformedText(base, OffsetMapping.Identity)
        }

        val builder = AnnotatedString.Builder(base)
        if (hasFirst) {
            builder.addStyle(SyntaxStyles.BracketHighlight, first, first + 1)
        }
        if (hasSecond) {
            builder.addStyle(SyntaxStyles.BracketHighlight, second, second + 1)
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

// Bounded Bracket Matching Utilities for High Performance
object BracketMatcher {
    private val OPEN_TO_CLOSE = mapOf('(' to ')', '{' to '}', '[' to ']', '<' to '>')
    private val CLOSE_TO_OPEN = mapOf(')' to '(', '}' to '{', ']' to '[', '>' to '<')
    private const val MAX_SCAN_DISTANCE = 2500

    fun findMatchingBracket(text: String, cursorPosition: Int): Pair<Int, Int>? {
        if (text.isEmpty()) return null

        val candidates = listOf(cursorPosition, cursorPosition - 1).filter { it in text.indices }

        for (pos in candidates) {
            val char = text[pos]

            if (OPEN_TO_CLOSE.containsKey(char)) {
                val target = OPEN_TO_CLOSE[char]!!
                var depth = 1
                val maxLimit = (pos + MAX_SCAN_DISTANCE).coerceAtMost(text.length)
                for (i in (pos + 1) until maxLimit) {
                    if (text[i] == char) depth++
                    else if (text[i] == target) {
                        depth--
                        if (depth == 0) return Pair(pos, i)
                    }
                }
            } else if (CLOSE_TO_OPEN.containsKey(char)) {
                val target = CLOSE_TO_OPEN[char]!!
                var depth = 1
                val minLimit = (pos - MAX_SCAN_DISTANCE).coerceAtLeast(0)
                for (i in (pos - 1) downTo minLimit) {
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

        val indentPrefix = lastLine.takeWhile { it == ' ' || it == '\t' }
        val trimmed = lastLine.trimEnd()

        val extraIndent = if (trimmed.endsWith("{") || trimmed.endsWith("(") || trimmed.endsWith("[") || trimmed.endsWith(":")) {
            "  "
        } else {
            ""
        }

        return indentPrefix + extraIndent
    }
}

