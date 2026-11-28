package com.example.ui.components.terminal

/**
 * Quote-aware POSIX/Bash-compatible shell parser & tokenizer for GitHub Terminal.
 */
object ShellScriptParser {

    /**
     * Tokenizes a single shell command honoring POSIX quoting rules:
     * - Inside single quotes ('...'): all characters (including \, $, ;, |, &) are literal.
     * - Inside double quotes ("..."): \ escapes ", \, $, and `; other backslashes are preserved.
     * - Outside quotes: \ escapes the next character; whitespace splits tokens.
     * - Empty quoted strings ("" or '') produce an empty string token.
     */
    fun tokenize(command: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var inSingleQuote = false
        var inDoubleQuote = false
        var escapeNext = false
        var hasTokenStarted = false

        var i = 0
        while (i < command.length) {
            val c = command[i]

            if (escapeNext) {
                current.append(c)
                escapeNext = false
                hasTokenStarted = true
                i++
                continue
            }

            if (inSingleQuote) {
                if (c == '\'') {
                    inSingleQuote = false
                } else {
                    current.append(c)
                }
                hasTokenStarted = true
                i++
                continue
            }

            if (inDoubleQuote) {
                if (c == '\\' && i + 1 < command.length) {
                    val next = command[i + 1]
                    if (next == '"' || next == '\\' || next == '$' || next == '`' || next == '\n') {
                        if (next != '\n') {
                            current.append(next)
                        }
                        hasTokenStarted = true
                        i += 2
                        continue
                    }
                }
                if (c == '"') {
                    inDoubleQuote = false
                } else {
                    current.append(c)
                }
                hasTokenStarted = true
                i++
                continue
            }

            // Outside quotes
            when {
                c == '\\' -> {
                    escapeNext = true
                    hasTokenStarted = true
                }
                c == '\'' -> {
                    inSingleQuote = true
                    hasTokenStarted = true
                }
                c == '"' -> {
                    inDoubleQuote = true
                    hasTokenStarted = true
                }
                c.isWhitespace() -> {
                    if (hasTokenStarted) {
                        tokens.add(current.toString())
                        current.clear()
                        hasTokenStarted = false
                    }
                }
                else -> {
                    current.append(c)
                    hasTokenStarted = true
                }
            }
            i++
        }

        if (hasTokenStarted) {
            tokens.add(current.toString())
        }
        return tokens
    }

    /**
     * Splits text on a delimiter (such as ";" or "&&") ONLY when outside single/double quotes
     * and not escaped with a backslash.
     */
    fun splitTopLevel(input: String, delimiter: String): List<String> {
        if (input.isEmpty() || delimiter.isEmpty()) return listOf(input)
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inSingle = false
        var inDouble = false
        var i = 0

        while (i < input.length) {
            val c = input[i]

            if (inSingle) {
                current.append(c)
                if (c == '\'') inSingle = false
                i++
                continue
            }

            if (inDouble) {
                if (c == '\\' && i + 1 < input.length) {
                    current.append(c).append(input[i + 1])
                    i += 2
                    continue
                }
                current.append(c)
                if (c == '"') inDouble = false
                i++
                continue
            }

            // Outside quotes
            if (c == '\\' && i + 1 < input.length) {
                current.append(c).append(input[i + 1])
                i += 2
                continue
            }
            if (c == '\'') {
                inSingle = true
                current.append(c)
                i++
                continue
            }
            if (c == '"') {
                inDouble = true
                current.append(c)
                i++
                continue
            }

            if (input.startsWith(delimiter, i)) {
                result.add(current.toString())
                current.clear()
                i += delimiter.length
                continue
            }

            current.append(c)
            i++
        }

        result.add(current.toString())
        return result
    }

    /**
     * Splits a command on unquoted single pipe '|' characters (ignoring '||' and pipes inside quotes).
     */
    fun splitPipeline(input: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inSingle = false
        var inDouble = false
        var i = 0

        while (i < input.length) {
            val c = input[i]

            if (inSingle) {
                current.append(c)
                if (c == '\'') inSingle = false
                i++
                continue
            }

            if (inDouble) {
                if (c == '\\' && i + 1 < input.length) {
                    current.append(c).append(input[i + 1])
                    i += 2
                    continue
                }
                current.append(c)
                if (c == '"') inDouble = false
                i++
                continue
            }

            if (c == '\\' && i + 1 < input.length) {
                current.append(c).append(input[i + 1])
                i += 2
                continue
            }
            if (c == '\'') {
                inSingle = true
                current.append(c)
                i++
                continue
            }
            if (c == '"') {
                inDouble = true
                current.append(c)
                i++
                continue
            }

            if (c == '|') {
                if (i + 1 < input.length && input[i + 1] == '|') {
                    // Logical OR '||', not a pipe
                    current.append("||")
                    i += 2
                    continue
                }
                result.add(current.toString().trim())
                current.clear()
                i++
                continue
            }

            current.append(c)
            i++
        }

        val tail = current.toString().trim()
        if (tail.isNotEmpty() || result.isNotEmpty()) {
            result.add(tail)
        }
        return result.filter { it.isNotEmpty() }
    }

    /**
     * Splits a command on unquoted '&&' and '||' left-to-right.
     * Returns list of (command, followingOperator) where followingOperator is "&&", "||", or null for the last item.
     */
    fun splitConditionalChain(input: String): List<Pair<String, String?>> {
        val result = mutableListOf<Pair<String, String?>>()
        val current = StringBuilder()
        var inSingle = false
        var inDouble = false
        var i = 0

        while (i < input.length) {
            val c = input[i]

            if (inSingle) {
                current.append(c)
                if (c == '\'') inSingle = false
                i++
                continue
            }

            if (inDouble) {
                if (c == '\\' && i + 1 < input.length) {
                    current.append(c).append(input[i + 1])
                    i += 2
                    continue
                }
                current.append(c)
                if (c == '"') inDouble = false
                i++
                continue
            }

            if (c == '\\' && i + 1 < input.length) {
                current.append(c).append(input[i + 1])
                i += 2
                continue
            }
            if (c == '\'') {
                inSingle = true
                current.append(c)
                i++
                continue
            }
            if (c == '"') {
                inDouble = true
                current.append(c)
                i++
                continue
            }

            if (input.startsWith("&&", i)) {
                val cmd = current.toString().trim()
                if (cmd.isNotEmpty()) result.add(cmd to "&&")
                current.clear()
                i += 2
                continue
            }
            if (input.startsWith("||", i)) {
                val cmd = current.toString().trim()
                if (cmd.isNotEmpty()) result.add(cmd to "||")
                current.clear()
                i += 2
                continue
            }

            current.append(c)
            i++
        }

        val tail = current.toString().trim()
        if (tail.isNotEmpty()) {
            result.add(tail to null)
        }
        return result
    }

    /**
     * Strips an unquoted inline comment starting with '#' (only when preceded by whitespace or at start).
     */
    fun stripInlineComment(line: String): String {
        var inSingle = false
        var inDouble = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (inSingle) {
                if (c == '\'') inSingle = false
                i++
                continue
            }
            if (inDouble) {
                if (c == '\\' && i + 1 < line.length) {
                    i += 2
                    continue
                }
                if (c == '"') inDouble = false
                i++
                continue
            }
            if (c == '\\' && i + 1 < line.length) {
                i += 2
                continue
            }
            if (c == '\'') {
                inSingle = true
            } else if (c == '"') {
                inDouble = true
            } else if (c == '#' && (i == 0 || line[i - 1].isWhitespace())) {
                return line.substring(0, i).trim()
            }
            i++
        }
        return line.trim()
    }

    /**
     * Joins lines that have unclosed quotes or trailing backslash continuations into logical command lines.
     */
    fun joinContinuedLines(lines: List<String>): List<String> {
        val logical = mutableListOf<String>()
        val buffer = StringBuilder()
        var inSingle = false
        var inDouble = false

        for (rawLine in lines) {
            if (buffer.isEmpty()) {
                val trimmed = rawLine.trim()
                if (trimmed.isEmpty()) continue
                if (trimmed.startsWith("#")) {
                    logical.add(trimmed)
                    continue
                }
            }

            var line = rawLine
            var endsWithBackslash = false

            // Scan quote & trailing backslash state for this line
            var i = 0
            while (i < line.length) {
                val c = line[i]
                if (inSingle) {
                    if (c == '\'') inSingle = false
                    i++
                    continue
                }
                if (inDouble) {
                    if (c == '\\' && i + 1 < line.length) {
                        i += 2
                        continue
                    }
                    if (c == '"') inDouble = false
                    i++
                    continue
                }
                if (c == '\\') {
                    if (i == line.length - 1) {
                        endsWithBackslash = true
                    }
                    i += 2
                    continue
                }
                if (c == '\'') inSingle = true
                else if (c == '"') inDouble = true
                i++
            }

            if (endsWithBackslash && !inSingle) {
                line = line.dropLast(1)
            }

            if (buffer.isNotEmpty()) {
                if (inSingle || inDouble) {
                    buffer.append('\n')
                } else if (!endsWithBackslash) {
                    buffer.append('\n')
                }
            }
            buffer.append(line)

            if (!inSingle && !inDouble && !endsWithBackslash) {
                logical.add(buffer.toString())
                buffer.clear()
            }
        }

        if (buffer.isNotEmpty()) {
            logical.add(buffer.toString())
        }
        return logical
    }

    /**
     * Substitutes $VAR and ${VAR} ONLY outside single-quoted ('...') regions and ONLY for keys present in [vars].
     */
    fun substituteVariables(cmd: String, vars: Map<String, String>): String {
        if (!cmd.contains('$') || vars.isEmpty()) return cmd
        val out = StringBuilder()
        var inSingle = false
        var inDouble = false
        var i = 0

        while (i < cmd.length) {
            val c = cmd[i]
            if (inSingle) {
                out.append(c)
                if (c == '\'') inSingle = false
                i++
                continue
            }
            if (c == '\\' && i + 1 < cmd.length) {
                out.append(c).append(cmd[i + 1])
                i += 2
                continue
            }
            if (c == '\'' && !inDouble) {
                inSingle = true
                out.append(c)
                i++
                continue
            }
            if (c == '"') {
                inDouble = !inDouble
                out.append(c)
                i++
                continue
            }

            if (c == '$' && i + 1 < cmd.length) {
                if (cmd[i + 1] == '{') {
                    val closeIdx = cmd.indexOf('}', i + 2)
                    if (closeIdx != -1) {
                        val varName = cmd.substring(i + 2, closeIdx)
                        val replacement = vars[varName]
                        if (replacement != null) {
                            out.append(replacement)
                            i = closeIdx + 1
                            continue
                        }
                    }
                } else if (cmd[i + 1].isLetter() || cmd[i + 1] == '_') {
                    var end = i + 2
                    while (end < cmd.length && (cmd[end].isLetterOrDigit() || cmd[end] == '_')) {
                        end++
                    }
                    val varName = cmd.substring(i + 1, end)
                    val replacement = vars[varName]
                    if (replacement != null) {
                        out.append(replacement)
                        i = end
                        continue
                    }
                }
            }

            out.append(c)
            i++
        }
        return out.toString()
    }
}
