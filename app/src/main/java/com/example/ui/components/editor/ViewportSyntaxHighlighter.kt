package com.example.ui.components.editor

import android.graphics.Color as AndroidColor
import androidx.collection.LruCache

/**
 * Compact per-line token run used for direct Android Canvas syntax rendering with zero Compose overhead.
 */
data class LineTokenRun(
    val startCol: Int,
    val endCol: Int,
    val colorArgb: Int,
    val isBold: Boolean = false,
    val isItalic: Boolean = false
)

/**
 * Unlimited-file-size stateful line-by-line syntax highlighter.
 *
 * Unlike whole-string regex/VisualTransformation highlighters that stall or cut off after 50,000 chars,
 * [ViewportSyntaxHighlighter] tracks multiline comment/heredoc/triple-quote states in a 1-byte-per-line
 * array and tokenizes only the ~40 visible lines currently inside the viewport.
 */
class ViewportSyntaxHighlighter {

    companion object {
        const val STATE_NORMAL: Byte = 0
        const val STATE_BLOCK_COMMENT: Byte = 1
        const val STATE_TRIPLE_DOUBLE: Byte = 2
        const val STATE_TRIPLE_SINGLE: Byte = 3
        const val STATE_XML_COMMENT: Byte = 4

        val COLOR_DEFAULT: Int = AndroidColor.parseColor("#18181B")
        val COLOR_KEYWORD: Int = AndroidColor.parseColor("#7C3AED")
        val COLOR_TYPE: Int = AndroidColor.parseColor("#0284C7")
        val COLOR_FUNCTION: Int = AndroidColor.parseColor("#2563EB")
        val COLOR_STRING: Int = AndroidColor.parseColor("#0F9D74")
        val COLOR_NUMBER: Int = AndroidColor.parseColor("#D97706")
        val COLOR_COMMENT: Int = AndroidColor.parseColor("#94A3B8")
        val COLOR_ANNOTATION: Int = AndroidColor.parseColor("#E11D48")
        val COLOR_PROPERTY: Int = AndroidColor.parseColor("#0D9488")
        val COLOR_XML_TAG: Int = AndroidColor.parseColor("#1D4ED8")
        val COLOR_XML_ATTR: Int = AndroidColor.parseColor("#9333EA")

        private val KEYWORDS_MAP: Map<SupportedLanguage, Set<String>> = mapOf(
            SupportedLanguage.KOTLIN to setOf(
                "package", "import", "class", "interface", "object", "enum", "sealed", "data",
                "val", "var", "fun", "constructor", "init", "this", "super", "if", "else", "when",
                "for", "while", "do", "return", "break", "continue", "throw", "try", "catch",
                "finally", "override", "open", "abstract", "final", "private", "protected", "public",
                "internal", "lateinit", "lazy", "companion", "infix", "inline", "tailrec", "operator",
                "suspend", "typealias", "is", "as", "in", "out", "by", "get", "set", "true", "false", "null"
            ),
            SupportedLanguage.JAVA to setOf(
                "package", "import", "class", "interface", "enum", "extends", "implements",
                "public", "private", "protected", "static", "final", "abstract", "synchronized",
                "volatile", "transient", "native", "strictfp", "void", "boolean", "byte", "char",
                "short", "int", "long", "float", "double", "if", "else", "switch", "case", "default",
                "while", "do", "for", "break", "continue", "return", "throw", "throws", "try", "catch",
                "finally", "new", "this", "super", "instanceof", "assert", "true", "false", "null"
            ),
            SupportedLanguage.JAVASCRIPT to setOf(
                "const", "let", "var", "function", "return", "if", "else", "for", "while", "do",
                "switch", "case", "default", "break", "continue", "try", "catch", "finally", "throw",
                "class", "extends", "super", "this", "new", "import", "export", "from", "as",
                "async", "await", "yield", "typeof", "instanceof", "in", "of", "delete", "void",
                "true", "false", "null", "undefined", "NaN"
            ),
            SupportedLanguage.TYPESCRIPT to setOf(
                "const", "let", "var", "function", "return", "if", "else", "for", "while", "do",
                "switch", "case", "default", "break", "continue", "try", "catch", "finally", "throw",
                "class", "extends", "super", "this", "new", "import", "export", "from", "as",
                "async", "await", "yield", "typeof", "instanceof", "in", "of", "delete", "void",
                "true", "false", "null", "undefined", "NaN", "interface", "type", "enum", "implements",
                "declare", "abstract", "private", "protected", "public", "readonly", "override"
            ),
            SupportedLanguage.PYTHON to setOf(
                "def", "class", "return", "if", "elif", "else", "for", "while", "break", "continue",
                "try", "except", "finally", "raise", "import", "from", "as", "global", "nonlocal",
                "lambda", "yield", "with", "pass", "assert", "del", "async", "await", "and", "or",
                "not", "is", "in", "True", "False", "None"
            ),
            SupportedLanguage.RUST to setOf(
                "fn", "let", "mut", "const", "static", "struct", "enum", "trait", "impl", "type",
                "mod", "use", "pub", "crate", "self", "Self", "super", "if", "else", "match", "while",
                "loop", "for", "in", "return", "break", "continue", "move", "ref", "where", "async",
                "await", "unsafe", "dyn", "true", "false", "Some", "None", "Ok", "Err"
            ),
            SupportedLanguage.GO to setOf(
                "package", "import", "func", "return", "var", "const", "type", "struct", "interface",
                "map", "chan", "go", "select", "defer", "if", "else", "switch", "case", "default",
                "for", "range", "break", "continue", "fallthrough", "true", "false", "nil", "iota"
            ),
            SupportedLanguage.C_CPP to setOf(
                "auto", "break", "case", "char", "const", "continue", "default", "do", "double",
                "else", "enum", "extern", "float", "for", "goto", "if", "int", "long", "register",
                "return", "short", "signed", "sizeof", "static", "struct", "switch", "typedef",
                "union", "unsigned", "void", "volatile", "while", "class", "namespace", "template",
                "typename", "public", "protected", "private", "virtual", "override", "true", "false",
                "nullptr", "using", "inline", "constexpr", "include", "define", "ifdef", "ifndef", "endif"
            ),
            SupportedLanguage.SHELL to setOf(
                "if", "then", "else", "elif", "fi", "case", "esac", "for", "while", "until", "do",
                "done", "in", "function", "select", "time", "return", "exit", "export", "local",
                "readonly", "unset", "shift", "source", "echo", "cd", "pwd", "true", "false"
            ),
            SupportedLanguage.SQL to setOf(
                "select", "from", "where", "insert", "into", "update", "delete", "create", "table",
                "drop", "alter", "add", "column", "primary", "key", "foreign", "references", "join",
                "inner", "left", "right", "full", "outer", "on", "group", "by", "order", "having",
                "limit", "offset", "union", "all", "distinct", "as", "and", "or", "not", "in",
                "between", "like", "is", "null", "case", "when", "then", "else", "end", "count",
                "sum", "avg", "min", "max", "asc", "desc", "true", "false"
            ),
            SupportedLanguage.CSS to setOf(
                "import", "media", "charset", "keyframes", "supports", "font-face", "root",
                "inherit", "initial", "unset", "none", "auto", "important"
            )
        )
    }

    private data class CacheKey(
        val language: SupportedLanguage,
        val startState: Byte,
        val lineText: String
    )

    private val tokenCache = LruCache<CacheKey, List<LineTokenRun>>(600)

    private var currentLanguage: SupportedLanguage = SupportedLanguage.PLAIN_TEXT
    private var lineStartStates = ByteArray(256)
    private var validUpToLine: Int = 0
    private var trackedLineCount: Int = 0

    fun setLanguage(language: SupportedLanguage) {
        if (currentLanguage != language) {
            currentLanguage = language
            validUpToLine = 0
        }
    }

    fun invalidateAll() {
        validUpToLine = 0
    }

    fun invalidateFromLine(lineIndex: Int) {
        if (lineIndex < validUpToLine) {
            validUpToLine = lineIndex.coerceAtLeast(0)
        }
    }

    /**
     * Tokenizes [lineIndex] of [buffer] with accurate multiline comment / triple-quote state.
     */
    fun getLineTokens(
        buffer: CodeDocumentBuffer,
        lineIndex: Int,
        defaultTextColor: Int = COLOR_DEFAULT
    ): List<LineTokenRun> {
        val lineText = buffer.getLine(lineIndex)
        if (lineText.isEmpty()) return emptyList()

        // Guard excessively long single minified lines (> 4000 chars on one line) so rendering stays 120 FPS
        if (
            currentLanguage == SupportedLanguage.PLAIN_TEXT ||
            currentLanguage == SupportedLanguage.MARKDOWN ||
            lineText.length > 4_000
        ) {
            return listOf(LineTokenRun(0, lineText.length, defaultTextColor))
        }

        ensureStatesUpTo(buffer, lineIndex)
        val startState = if (lineIndex in lineStartStates.indices) lineStartStates[lineIndex] else STATE_NORMAL

        val key = CacheKey(currentLanguage, startState, lineText)
        tokenCache.get(key)?.let { return it }

        val computed = if (currentLanguage == SupportedLanguage.XML_HTML) {
            tokenizeXmlLine(lineText, startState, defaultTextColor)
        } else {
            tokenizeGeneralLine(lineText, startState, currentLanguage, defaultTextColor)
        }
        tokenCache.put(key, computed)
        return computed
    }

    private fun ensureStatesUpTo(buffer: CodeDocumentBuffer, targetLine: Int) {
        val count = buffer.lineCount
        if (count + 1 > lineStartStates.size) {
            lineStartStates = lineStartStates.copyOf(maxOf(count + 64, lineStartStates.size * 2))
        }
        if (trackedLineCount != count) {
            trackedLineCount = count
        }
        if (validUpToLine == 0) {
            lineStartStates[0] = STATE_NORMAL
        }
        val limit = targetLine.coerceIn(0, count - 1)
        var line = validUpToLine
        while (line < limit) {
            val inState = lineStartStates[line]
            val lineStr = buffer.getLine(line)
            val outState = if (lineStr.length > 8_000) {
                inState
            } else if (currentLanguage == SupportedLanguage.XML_HTML) {
                scanXmlExitState(lineStr, inState)
            } else {
                scanGeneralExitState(lineStr, inState, currentLanguage)
            }
            lineStartStates[line + 1] = outState
            line++
        }
        if (limit > validUpToLine) {
            validUpToLine = limit
        }
    }

    private fun scanXmlExitState(line: String, startState: Byte): Byte {
        var state = startState
        var i = 0
        val len = line.length
        while (i < len) {
            if (state == STATE_XML_COMMENT) {
                val close = line.indexOf("-->", i)
                if (close == -1) return STATE_XML_COMMENT
                state = STATE_NORMAL
                i = close + 3
            } else {
                val open = line.indexOf("<!--", i)
                if (open == -1) return STATE_NORMAL
                state = STATE_XML_COMMENT
                i = open + 4
            }
        }
        return state
    }

    private fun scanGeneralExitState(line: String, startState: Byte, lang: SupportedLanguage): Byte {
        var state = startState
        var i = 0
        val len = line.length
        while (i < len) {
            when (state) {
                STATE_BLOCK_COMMENT -> {
                    val close = line.indexOf("*/", i)
                    if (close == -1) return STATE_BLOCK_COMMENT
                    state = STATE_NORMAL
                    i = close + 2
                }
                STATE_TRIPLE_DOUBLE -> {
                    val close = line.indexOf("\"\"\"", i)
                    if (close == -1) return STATE_TRIPLE_DOUBLE
                    state = STATE_NORMAL
                    i = close + 3
                }
                STATE_TRIPLE_SINGLE -> {
                    val close = line.indexOf("'''", i)
                    if (close == -1) return STATE_TRIPLE_SINGLE
                    state = STATE_NORMAL
                    i = close + 3
                }
                else -> {
                    val c = line[i]
                    if (c == '/' && i + 1 < len && line[i + 1] == '/' && lang != SupportedLanguage.CSS) {
                        return STATE_NORMAL
                    }
                    if (c == '#' && (lang == SupportedLanguage.PYTHON || lang == SupportedLanguage.SHELL || lang == SupportedLanguage.YAML)) {
                        return STATE_NORMAL
                    }
                    if (c == '-' && i + 1 < len && line[i + 1] == '-' && lang == SupportedLanguage.SQL) {
                        return STATE_NORMAL
                    }
                    if (c == '/' && i + 1 < len && line[i + 1] == '*') {
                        state = STATE_BLOCK_COMMENT
                        i += 2
                        continue
                    }
                    if (c == '"' && i + 2 < len && line[i + 1] == '"' && line[i + 2] == '"') {
                        state = STATE_TRIPLE_DOUBLE
                        i += 3
                        continue
                    }
                    if (c == '\'' && i + 2 < len && line[i + 1] == '\'' && line[i + 2] == '\'' && lang == SupportedLanguage.PYTHON) {
                        state = STATE_TRIPLE_SINGLE
                        i += 3
                        continue
                    }
                    if (c == '"' || c == '\'' || c == '`') {
                        val q = c
                        i++
                        while (i < len) {
                            if (line[i] == '\\') {
                                i += 2
                                continue
                            }
                            if (line[i] == q) {
                                i++
                                break
                            }
                            i++
                        }
                        continue
                    }
                    i++
                }
            }
        }
        return state
    }

    private fun tokenizeXmlLine(
        line: String,
        startState: Byte,
        defaultColor: Int
    ): List<LineTokenRun> {
        val runs = ArrayList<LineTokenRun>(8)
        val len = line.length
        var i = 0
        var plainStart = 0
        var inComment = (startState == STATE_XML_COMMENT)

        fun flushPlain(upTo: Int) {
            if (upTo > plainStart) {
                runs.add(LineTokenRun(plainStart, upTo, defaultColor))
            }
            plainStart = upTo
        }

        while (i < len) {
            if (inComment) {
                val close = line.indexOf("-->", i)
                if (close == -1) {
                    flushPlain(i)
                    runs.add(LineTokenRun(i, len, COLOR_COMMENT, isItalic = true))
                    plainStart = len
                    break
                } else {
                    flushPlain(i)
                    val end = close + 3
                    runs.add(LineTokenRun(i, end, COLOR_COMMENT, isItalic = true))
                    plainStart = end
                    i = end
                    inComment = false
                    continue
                }
            }

            if (line[i] == '<') {
                if (i + 3 < len && line.startsWith("<!--", i)) {
                    flushPlain(i)
                    inComment = true
                    continue
                }
                flushPlain(i)
                val isClose = (i + 1 < len && line[i + 1] == '/')
                val tagPrefixEnd = if (isClose) (i + 2).coerceAtMost(len) else (i + 1).coerceAtMost(len)
                runs.add(LineTokenRun(i, tagPrefixEnd, defaultColor))
                var p = tagPrefixEnd
                while (p < len && (line[p].isLetterOrDigit() || line[p] == ':' || line[p] == '-' || line[p] == '_')) {
                    p++
                }
                if (p > tagPrefixEnd) {
                    runs.add(LineTokenRun(tagPrefixEnd, p, COLOR_XML_TAG, isBold = true))
                }
                var attrScan = p
                while (attrScan < len && line[attrScan] != '>') {
                    val ch = line[attrScan]
                    if (ch == '"' || ch == '\'') {
                        if (attrScan > p) runs.add(LineTokenRun(p, attrScan, defaultColor))
                        val q = ch
                        val vStart = attrScan
                        attrScan++
                        while (attrScan < len && line[attrScan] != q) {
                            if (line[attrScan] == '\\' && attrScan + 1 < len) attrScan += 2
                            else attrScan++
                        }
                        if (attrScan < len && line[attrScan] == q) attrScan++
                        runs.add(LineTokenRun(vStart, attrScan, COLOR_STRING))
                        p = attrScan
                        continue
                    }
                    if (ch.isLetter() || ch == '_' || ch == ':') {
                        if (attrScan > p) runs.add(LineTokenRun(p, attrScan, defaultColor))
                        val aStart = attrScan
                        while (attrScan < len && (line[attrScan].isLetterOrDigit() || line[attrScan] == ':' || line[attrScan] == '-' || line[attrScan] == '_')) {
                            attrScan++
                        }
                        runs.add(LineTokenRun(aStart, attrScan, COLOR_XML_ATTR))
                        p = attrScan
                        continue
                    }
                    attrScan++
                }
                if (attrScan < len && line[attrScan] == '>') attrScan++
                if (attrScan > p) {
                    runs.add(LineTokenRun(p, attrScan, defaultColor))
                }
                plainStart = attrScan
                i = attrScan
                continue
            }
            i++
        }
        flushPlain(len)
        return mergeAdjacentRuns(runs)
    }

    private fun tokenizeGeneralLine(
        line: String,
        startState: Byte,
        lang: SupportedLanguage,
        defaultColor: Int
    ): List<LineTokenRun> {
        val runs = ArrayList<LineTokenRun>(10)
        val keywords = KEYWORDS_MAP[lang] ?: emptySet()
        val len = line.length
        var i = 0
        var plainStart = 0
        var state = startState

        fun flushPlain(upTo: Int) {
            if (upTo > plainStart) {
                runs.add(LineTokenRun(plainStart, upTo, defaultColor))
            }
            plainStart = upTo
        }

        while (i < len) {
            if (state == STATE_BLOCK_COMMENT) {
                flushPlain(i)
                val close = line.indexOf("*/", i)
                if (close == -1) {
                    runs.add(LineTokenRun(i, len, COLOR_COMMENT, isItalic = true))
                    plainStart = len
                    break
                } else {
                    val end = close + 2
                    runs.add(LineTokenRun(i, end, COLOR_COMMENT, isItalic = true))
                    plainStart = end
                    i = end
                    state = STATE_NORMAL
                    continue
                }
            }
            if (state == STATE_TRIPLE_DOUBLE || state == STATE_TRIPLE_SINGLE) {
                flushPlain(i)
                val delim = if (state == STATE_TRIPLE_DOUBLE) "\"\"\"" else "'''"
                val close = line.indexOf(delim, i)
                if (close == -1) {
                    runs.add(LineTokenRun(i, len, COLOR_STRING))
                    plainStart = len
                    break
                } else {
                    val end = close + 3
                    runs.add(LineTokenRun(i, end, COLOR_STRING))
                    plainStart = end
                    i = end
                    state = STATE_NORMAL
                    continue
                }
            }

            val c = line[i]

            // Line comments
            if (
                (c == '/' && i + 1 < len && line[i + 1] == '/' && lang != SupportedLanguage.CSS) ||
                (c == '#' && (lang == SupportedLanguage.PYTHON || lang == SupportedLanguage.SHELL || lang == SupportedLanguage.YAML)) ||
                (c == '-' && i + 1 < len && line[i + 1] == '-' && lang == SupportedLanguage.SQL)
            ) {
                flushPlain(i)
                runs.add(LineTokenRun(i, len, COLOR_COMMENT, isItalic = true))
                plainStart = len
                break
            }

            // Block comment start
            if (c == '/' && i + 1 < len && line[i + 1] == '*') {
                flushPlain(i)
                val close = line.indexOf("*/", i + 2)
                if (close == -1) {
                    runs.add(LineTokenRun(i, len, COLOR_COMMENT, isItalic = true))
                    plainStart = len
                    break
                } else {
                    val end = close + 2
                    runs.add(LineTokenRun(i, end, COLOR_COMMENT, isItalic = true))
                    plainStart = end
                    i = end
                    continue
                }
            }

            // Annotations
            if (c == '@' && (lang == SupportedLanguage.KOTLIN || lang == SupportedLanguage.JAVA || lang == SupportedLanguage.TYPESCRIPT || lang == SupportedLanguage.PYTHON)) {
                var end = i + 1
                while (end < len && (line[end].isLetterOrDigit() || line[end] == '.' || line[end] == '_')) {
                    end++
                }
                if (end > i + 1) {
                    flushPlain(i)
                    runs.add(LineTokenRun(i, end, COLOR_ANNOTATION, isBold = true))
                    plainStart = end
                    i = end
                    continue
                }
            }

            // String literals
            if (c == '"' || c == '\'' || c == '`') {
                flushPlain(i)
                if (i + 2 < len && line[i + 1] == c && line[i + 2] == c && (c == '"' || (c == '\'' && lang == SupportedLanguage.PYTHON))) {
                    val delim = "$c$c$c"
                    val close = line.indexOf(delim, i + 3)
                    if (close == -1) {
                        runs.add(LineTokenRun(i, len, COLOR_STRING))
                        plainStart = len
                        break
                    } else {
                        val end = close + 3
                        runs.add(LineTokenRun(i, end, COLOR_STRING))
                        plainStart = end
                        i = end
                        continue
                    }
                } else {
                    var end = i + 1
                    while (end < len) {
                        if (line[end] == '\\') {
                            end = (end + 2).coerceAtMost(len)
                            continue
                        }
                        if (line[end] == c) {
                            end++
                            break
                        }
                        end++
                    }
                    // JSON / YAML string key highlight check
                    var color = COLOR_STRING
                    if (lang == SupportedLanguage.JSON) {
                        var k = end
                        while (k < len && (line[k] == ' ' || line[k] == '\t')) k++
                        if (k < len && line[k] == ':') color = COLOR_PROPERTY
                    }
                    runs.add(LineTokenRun(i, end, color))
                    plainStart = end
                    i = end
                    continue
                }
            }

            // Number literals
            if (c.isDigit() && (i == 0 || (!line[i - 1].isLetterOrDigit() && line[i - 1] != '_'))) {
                flushPlain(i)
                var end = i + 1
                while (end < len && (line[end].isLetterOrDigit() || line[end] == '.' || line[end] == '_')) {
                    end++
                }
                runs.add(LineTokenRun(i, end, COLOR_NUMBER))
                plainStart = end
                i = end
                continue
            }

            // Identifiers / Keywords / Types / Functions
            if (c.isLetter() || c == '_') {
                var end = i + 1
                while (end < len && (line[end].isLetterOrDigit() || line[end] == '_')) {
                    end++
                }
                val wordLen = end - i
                var tokenColor = 0
                var bold = false
                if (wordLen in 2..20) {
                    val word = line.substring(i, end)
                    if (keywords.contains(word) || (lang == SupportedLanguage.SQL && keywords.contains(word.lowercase()))) {
                        tokenColor = COLOR_KEYWORD
                        bold = true
                    } else if (word[0].isUpperCase() && (lang == SupportedLanguage.KOTLIN || lang == SupportedLanguage.JAVA || lang == SupportedLanguage.TYPESCRIPT || lang == SupportedLanguage.RUST || lang == SupportedLanguage.C_CPP)) {
                        tokenColor = COLOR_TYPE
                        bold = true
                    } else if (end < len && line[end] == '(') {
                        tokenColor = COLOR_FUNCTION
                    } else if (lang == SupportedLanguage.YAML) {
                        var k = end
                        while (k < len && (line[k] == ' ' || line[k] == '\t')) k++
                        if (k < len && line[k] == ':') tokenColor = COLOR_PROPERTY
                    }
                } else if (end < len && line[end] == '(') {
                    tokenColor = COLOR_FUNCTION
                }

                if (tokenColor != 0) {
                    flushPlain(i)
                    runs.add(LineTokenRun(i, end, tokenColor, isBold = bold))
                    plainStart = end
                }
                i = end
                continue
            }

            i++
        }
        flushPlain(len)
        return mergeAdjacentRuns(runs)
    }

    private fun mergeAdjacentRuns(runs: List<LineTokenRun>): List<LineTokenRun> {
        if (runs.size <= 1) return runs
        val merged = ArrayList<LineTokenRun>(runs.size)
        var current = runs[0]
        for (idx in 1 until runs.size) {
            val next = runs[idx]
            if (
                current.endCol == next.startCol &&
                current.colorArgb == next.colorArgb &&
                current.isBold == next.isBold &&
                current.isItalic == next.isItalic
            ) {
                current = current.copy(endCol = next.endCol)
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)
        return merged
    }
}
