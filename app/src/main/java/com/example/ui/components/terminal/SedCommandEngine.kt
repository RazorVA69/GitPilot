package com.example.ui.components.terminal

import java.util.regex.Pattern

/**
 * Built-in GNU/POSIX-compatible `sed` stream editor for GitHub Terminal.
 *
 * Supports:
 * - Flags: -i / -i'' / -i.bak / --in-place, -n / --quiet / --silent, -E / -r / --regexp-extended, -e <expr>
 * - Addresses: line numbers, $, /regex/, \<d>regex<d>, ranges (addr1,addr2), negation (!)
 * - Commands: s<d>pat<d>repl<d>[g,i,I,p,1-9], d, D, N, n, p, P, q, a, i, c, y, { ... } blocks
 * - BRE (default) and ERE (-E / -r) regex semantics, &, \1-\9, \n, \t, and literal $ in replacements
 */
object SedCommandEngine {

    data class ParsedArgs(
        val inPlace: Boolean,
        val quiet: Boolean,
        val extendedRegex: Boolean,
        val expressions: List<String>,
        val files: List<String>,
        val error: String? = null
    )

    sealed interface Address {
        data class Line(val number: Int) : Address
        data object LastLine : Address
        data class RegexAddr(val pattern: Regex) : Address
    }

    sealed interface SedCmd {
        val addr1: Address?
        val addr2: Address?
        val negated: Boolean

        data class Substitute(
            override val addr1: Address? = null,
            override val addr2: Address? = null,
            override val negated: Boolean = false,
            val pattern: Regex,
            val replacement: String,
            val global: Boolean,
            val printOnMatch: Boolean,
            val occurrence: Int = 0
        ) : SedCmd

        data class Delete(
            override val addr1: Address? = null,
            override val addr2: Address? = null,
            override val negated: Boolean = false
        ) : SedCmd

        data class DeleteFirstLine(
            override val addr1: Address? = null,
            override val addr2: Address? = null,
            override val negated: Boolean = false
        ) : SedCmd

        data class NextAppend(
            override val addr1: Address? = null,
            override val addr2: Address? = null,
            override val negated: Boolean = false
        ) : SedCmd

        data class NextLine(
            override val addr1: Address? = null,
            override val addr2: Address? = null,
            override val negated: Boolean = false
        ) : SedCmd

        data class Print(
            override val addr1: Address? = null,
            override val addr2: Address? = null,
            override val negated: Boolean = false
        ) : SedCmd

        data class PrintFirstLine(
            override val addr1: Address? = null,
            override val addr2: Address? = null,
            override val negated: Boolean = false
        ) : SedCmd

        data class Quit(
            override val addr1: Address? = null,
            override val addr2: Address? = null,
            override val negated: Boolean = false
        ) : SedCmd

        data class Transliterate(
            override val addr1: Address? = null,
            override val addr2: Address? = null,
            override val negated: Boolean = false,
            val fromChars: String,
            val toChars: String
        ) : SedCmd

        data class TextInsert(
            override val addr1: Address? = null,
            override val addr2: Address? = null,
            override val negated: Boolean = false,
            val mode: Char, // 'a', 'i', 'c'
            val text: String
        ) : SedCmd

        data class Block(
            override val addr1: Address? = null,
            override val addr2: Address? = null,
            override val negated: Boolean = false,
            val commands: List<SedCmd>
        ) : SedCmd
    }

    fun parseArgs(args: List<String>): ParsedArgs {
        var inPlace = false
        var quiet = false
        var extended = false
        val expressions = mutableListOf<String>()
        val positional = mutableListOf<String>()
        var i = 0
        var endOfOptions = false

        while (i < args.size) {
            val arg = args[i]
            if (!endOfOptions && arg == "--") {
                endOfOptions = true
                i++
                continue
            }
            if (!endOfOptions && arg.startsWith("--")) {
                when {
                    arg == "--in-place" || arg.startsWith("--in-place=") -> inPlace = true
                    arg == "--quiet" || arg == "--silent" -> quiet = true
                    arg == "--regexp-extended" -> extended = true
                    arg.startsWith("--expression=") -> expressions.add(arg.removePrefix("--expression="))
                    arg == "--expression" && i + 1 < args.size -> {
                        expressions.add(args[++i])
                    }
                    else -> return ParsedArgs(inPlace, quiet, extended, expressions, emptyList(), "sed: unrecognized option '$arg'")
                }
                i++
                continue
            }
            if (!endOfOptions && arg.startsWith("-") && arg.length > 1) {
                var j = 1
                while (j < arg.length) {
                    when (val ch = arg[j]) {
                        'i' -> {
                            inPlace = true
                            // Support macOS-style `sed -i '' 's/a/b/' file` where an empty backup suffix is passed as the next arg
                            if (j == arg.length - 1 && i + 1 < args.size && args[i + 1].isEmpty() && i + 2 < args.size) {
                                i++
                            }
                            // Everything remaining in -iSUFFIX is the backup suffix (e.g. -i.bak)
                            j = arg.length
                        }
                        'n' -> quiet = true
                        'E', 'r' -> extended = true
                        'e' -> {
                            val rest = arg.substring(j + 1)
                            if (rest.isNotEmpty()) {
                                expressions.add(rest)
                                j = arg.length
                            } else if (i + 1 < args.size) {
                                expressions.add(args[++i])
                                j = arg.length
                            } else {
                                return ParsedArgs(inPlace, quiet, extended, expressions, emptyList(), "sed: option requires an argument -- 'e'")
                            }
                        }
                        else -> return ParsedArgs(inPlace, quiet, extended, expressions, emptyList(), "sed: invalid option -- '$ch'")
                    }
                    j++
                }
                i++
                continue
            }
            positional.add(arg)
            i++
        }

        val finalExpressions = if (expressions.isEmpty()) {
            if (positional.isEmpty()) {
                return ParsedArgs(inPlace, quiet, extended, emptyList(), emptyList(), "sed: no script specified")
            }
            listOf(positional.first())
        } else {
            expressions
        }

        val files = if (expressions.isEmpty()) positional.drop(1) else positional
        return ParsedArgs(inPlace, quiet, extended, finalExpressions, files)
    }

    /**
     * Converts a POSIX/GNU BRE (or ERE when [extended] is true) pattern into a Java Regex string.
     */
    fun toJavaRegex(pattern: String, extended: Boolean, ignoreCase: Boolean = false): Regex {
        val javaPat = buildString {
            var i = 0
            var inBracket = false
            while (i < pattern.length) {
                val c = pattern[i]
                if (inBracket) {
                    if (c == '\\' && i + 1 < pattern.length) {
                        val next = pattern[i + 1]
                        when (next) {
                            'n' -> append('\n')
                            't' -> append('\t')
                            'r' -> append('\r')
                            else -> append('\\').append(next)
                        }
                        i += 2
                        continue
                    }
                    append(c)
                    if (c == ']') {
                        inBracket = false
                    }
                    i++
                    continue
                }

                if (c == '[') {
                    inBracket = true
                    append(c)
                    // Handle []...] or [^]...]
                    if (i + 1 < pattern.length && pattern[i + 1] == '^') {
                        append('^')
                        i++
                    }
                    if (i + 1 < pattern.length && pattern[i + 1] == ']') {
                        append("\\]")
                        i += 2
                        continue
                    }
                    i++
                    continue
                }

                if (c == '\\' && i + 1 < pattern.length) {
                    val next = pattern[i + 1]
                    when (next) {
                        'n' -> append('\n')
                        't' -> append('\t')
                        'r' -> append('\r')
                        '(', ')', '{', '}', '+', '?', '|' -> {
                            if (extended) {
                                // In ERE, escaped operator is literal
                                append('\\').append(next)
                            } else {
                                // In BRE, \( \) \{ \} \+ \? \| are regex operators
                                append(next)
                            }
                        }
                        '$' -> append("\\$")
                        '^' -> append("\\^")
                        '.', '*', '[', ']', '\\' -> append('\\').append(next)
                        '1', '2', '3', '4', '5', '6', '7', '8', '9',
                        's', 'S', 'd', 'D', 'w', 'W', 'b', 'B' -> append('\\').append(next)
                        else -> append(next)
                    }
                    i += 2
                    continue
                }

                if (!extended && (c == '(' || c == ')' || c == '{' || c == '}' || c == '+' || c == '?' || c == '|')) {
                    // Unescaped in BRE -> literal
                    append('\\').append(c)
                } else if (c == '^') {
                    append("\\A")
                } else if (c == '$') {
                    append("\\z")
                } else {
                    append(c)
                }
                i++
            }
        }
        val options = buildSet {
            if (ignoreCase) add(RegexOption.IGNORE_CASE)
        }
        return Regex(javaPat, options)
    }

    fun compileScript(script: String, extended: Boolean): List<SedCmd> {
        val parser = ScriptParser(script, extended)
        return parser.parseCommands(untilClosingBrace = false)
    }

    private class ScriptParser(private val src: String, private val extended: Boolean) {
        private var pos = 0

        private fun skipSeparators() {
            while (pos < src.length && (src[pos] == ' ' || src[pos] == '\t' || src[pos] == ';' || src[pos] == '\n' || src[pos] == '\r')) {
                pos++
            }
        }

        private fun skipHorizontalSpace() {
            while (pos < src.length && (src[pos] == ' ' || src[pos] == '\t')) {
                pos++
            }
        }

        fun parseCommands(untilClosingBrace: Boolean): List<SedCmd> {
            val cmds = mutableListOf<SedCmd>()
            while (pos < src.length) {
                skipSeparators()
                if (pos >= src.length) break
                if (untilClosingBrace && src[pos] == '}') {
                    pos++
                    break
                }
                if (src[pos] == '#') {
                    while (pos < src.length && src[pos] != '\n') pos++
                    continue
                }
                val cmd = parseSingleCommand()
                if (cmd != null) cmds.add(cmd)
            }
            return cmds
        }

        private fun parseAddress(): Address? {
            skipHorizontalSpace()
            if (pos >= src.length) return null
            val c = src[pos]
            return when {
                c.isDigit() -> {
                    val start = pos
                    while (pos < src.length && src[pos].isDigit()) pos++
                    Address.Line(src.substring(start, pos).toInt())
                }
                c == '$' -> {
                    pos++
                    Address.LastLine
                }
                c == '/' -> {
                    pos++
                    val pat = readDelimited('/')
                    Address.RegexAddr(toJavaRegex(pat, extended))
                }
                c == '\\' && pos + 1 < src.length -> {
                    val delim = src[pos + 1]
                    pos += 2
                    val pat = readDelimited(delim)
                    Address.RegexAddr(toJavaRegex(pat, extended))
                }
                else -> null
            }
        }

        private fun readDelimited(delim: Char): String {
            val sb = StringBuilder()
            var inBracket = false
            while (pos < src.length) {
                val c = src[pos]
                if (c == '\\' && pos + 1 < src.length) {
                    val next = src[pos + 1]
                    if (next == delim) {
                        sb.append(delim)
                    } else {
                        sb.append('\\').append(next)
                    }
                    pos += 2
                    continue
                }
                if (!inBracket && c == delim) {
                    pos++
                    return sb.toString()
                }
                if (c == '[') inBracket = true
                else if (c == ']' && inBracket) inBracket = false
                sb.append(c)
                pos++
            }
            return sb.toString()
        }

        private fun readReplacementDelimited(delim: Char): String {
            val sb = StringBuilder()
            while (pos < src.length) {
                val c = src[pos]
                if (c == '\\' && pos + 1 < src.length) {
                    val next = src[pos + 1]
                    if (next == delim) {
                        sb.append(delim)
                    } else {
                        sb.append('\\').append(next)
                    }
                    pos += 2
                    continue
                }
                if (c == delim) {
                    pos++
                    return sb.toString()
                }
                sb.append(c)
                pos++
            }
            return sb.toString()
        }

        private fun parseSingleCommand(): SedCmd? {
            val addr1 = parseAddress()
            var addr2: Address? = null
            skipHorizontalSpace()
            if (pos < src.length && src[pos] == ',') {
                pos++
                addr2 = parseAddress()
            }
            skipHorizontalSpace()
            var negated = false
            if (pos < src.length && src[pos] == '!') {
                negated = true
                pos++
                skipHorizontalSpace()
            }
            if (pos >= src.length) return null

            val op = src[pos++]
            return when (op) {
                '{' -> {
                    val inner = parseCommands(untilClosingBrace = true)
                    SedCmd.Block(addr1, addr2, negated, inner)
                }
                'd' -> SedCmd.Delete(addr1, addr2, negated)
                'D' -> SedCmd.DeleteFirstLine(addr1, addr2, negated)
                'N' -> SedCmd.NextAppend(addr1, addr2, negated)
                'n' -> SedCmd.NextLine(addr1, addr2, negated)
                'p' -> SedCmd.Print(addr1, addr2, negated)
                'P' -> SedCmd.PrintFirstLine(addr1, addr2, negated)
                'q' -> SedCmd.Quit(addr1, addr2, negated)
                's' -> {
                    if (pos >= src.length) throw IllegalArgumentException("sed: unterminated 's' command")
                    val delim = src[pos++]
                    val patStr = readDelimited(delim)
                    val replStr = readReplacementDelimited(delim)
                    var global = false
                    var ignoreCase = false
                    var printOnMatch = false
                    var occurrence = 0
                    while (pos < src.length && src[pos] != ';' && src[pos] != '\n' && src[pos] != '\r' && src[pos] != '}') {
                        when (val f = src[pos]) {
                            'g' -> global = true
                            'i', 'I' -> ignoreCase = true
                            'p' -> printOnMatch = true
                            in '1'..'9' -> occurrence = f - '0'
                            ' ', '\t' -> {}
                            else -> throw IllegalArgumentException("sed: unknown option to 's': '$f'")
                        }
                        pos++
                    }
                    val compiled = toJavaRegex(patStr, extended, ignoreCase)
                    SedCmd.Substitute(addr1, addr2, negated, compiled, replStr, global, printOnMatch, occurrence)
                }
                'y' -> {
                    if (pos >= src.length) throw IllegalArgumentException("sed: unterminated 'y' command")
                    val delim = src[pos++]
                    val fromStr = readReplacementDelimited(delim)
                    val toStr = readReplacementDelimited(delim)
                    SedCmd.Transliterate(addr1, addr2, negated, fromStr, toStr)
                }
                'a', 'i', 'c' -> {
                    if (pos < src.length && src[pos] == '\\') pos++
                    if (pos < src.length && src[pos] == '\n') pos++
                    val sb = StringBuilder()
                    while (pos < src.length) {
                        val ch = src[pos]
                        if (ch == '\\' && pos + 1 < src.length && src[pos + 1] == '\n') {
                            sb.append('\n')
                            pos += 2
                            continue
                        }
                        if (ch == '\n') {
                            pos++
                            break
                        }
                        sb.append(ch)
                        pos++
                    }
                    SedCmd.TextInsert(addr1, addr2, negated, op, unescapeSedText(sb.toString()))
                }
                else -> throw IllegalArgumentException("sed: unknown command: '$op'")
            }
        }
    }

    private fun unescapeSedText(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            if (s[i] == '\\' && i + 1 < s.length) {
                when (val next = s[i + 1]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    else -> sb.append(next)
                }
                i += 2
            } else {
                sb.append(s[i])
                i++
            }
        }
        return sb.toString()
    }

    private fun expandReplacement(replTemplate: String, match: MatchResult): String {
        val out = StringBuilder()
        var i = 0
        while (i < replTemplate.length) {
            val c = replTemplate[i]
            if (c == '&') {
                out.append(match.value)
                i++
                continue
            }
            if (c == '\\' && i + 1 < replTemplate.length) {
                val next = replTemplate[i + 1]
                when (next) {
                    'n' -> out.append('\n')
                    't' -> out.append('\t')
                    'r' -> out.append('\r')
                    '&' -> out.append('&')
                    '\\' -> out.append('\\')
                    in '0'..'9' -> {
                        val grp = next - '0'
                        out.append(match.groups[grp]?.value ?: "")
                    }
                    else -> out.append(next)
                }
                i += 2
                continue
            }
            out.append(c)
            i++
        }
        return out.toString()
    }

    private fun applySubstitution(input: String, cmd: SedCmd.Substitute): Pair<String, Boolean> {
        val matches = cmd.pattern.findAll(input).toList()
        if (matches.isEmpty()) return input to false

        if (cmd.global) {
            val sb = StringBuilder()
            var lastEnd = 0
            for (m in matches) {
                sb.append(input, lastEnd, m.range.first)
                sb.append(expandReplacement(cmd.replacement, m))
                lastEnd = m.range.last + 1
            }
            sb.append(input, lastEnd, input.length)
            return sb.toString() to true
        } else {
            val targetIdx = if (cmd.occurrence > 0) cmd.occurrence - 1 else 0
            val m = matches.getOrNull(targetIdx) ?: return input to false
            val sb = StringBuilder()
            sb.append(input, 0, m.range.first)
            sb.append(expandReplacement(cmd.replacement, m))
            sb.append(input, m.range.last + 1, input.length)
            return sb.toString() to true
        }
    }

    private class AddressMatcher(private val totalLines: Int) {
        private val activeRanges = mutableMapOf<SedCmd, Boolean>()

        private fun matchesSingle(addr: Address, lineNumber: Int, patternSpace: String): Boolean {
            return when (addr) {
                is Address.Line -> lineNumber == addr.number
                is Address.LastLine -> lineNumber == totalLines
                is Address.RegexAddr -> addr.pattern.containsMatchIn(patternSpace)
            }
        }

        fun matches(cmd: SedCmd, lineNumber: Int, patternSpace: String): Boolean {
            val rawMatch = when {
                cmd.addr1 == null -> true
                cmd.addr2 == null -> matchesSingle(cmd.addr1!!, lineNumber, patternSpace)
                else -> {
                    val active = activeRanges[cmd] ?: false
                    if (!active) {
                        if (matchesSingle(cmd.addr1!!, lineNumber, patternSpace)) {
                            // If addr2 is a line number already <= current line, range closes immediately
                            val a2 = cmd.addr2!!
                            val closesImmediately = a2 is Address.Line && a2.number <= lineNumber
                            if (!closesImmediately) {
                                activeRanges[cmd] = true
                            }
                            true
                        } else {
                            false
                        }
                    } else {
                        if (matchesSingle(cmd.addr2!!, lineNumber, patternSpace)) {
                            activeRanges[cmd] = false
                        }
                        true
                    }
                }
            }
            return if (cmd.negated) !rawMatch else rawMatch
        }
    }

    /**
     * Executes compiled sed commands over [content] and returns the transformed text.
     */
    fun transform(content: String, script: String, quiet: Boolean = false, extended: Boolean = false): String {
        val hadTrailingNewline = content.endsWith("\n")
        val rawLines = if (content.isEmpty()) {
            emptyList()
        } else if (hadTrailingNewline) {
            content.dropLast(1).split("\n")
        } else {
            content.split("\n")
        }

        if (rawLines.isEmpty()) return ""

        val commands = compileScript(script, extended)
        val matcher = AddressMatcher(rawLines.size)
        val outputLines = mutableListOf<String>()
        val pendingAppends = mutableListOf<String>()

        var lineIdx = 0
        var quitRequested = false

        while (lineIdx < rawLines.size && !quitRequested) {
            var patternSpace = rawLines[lineIdx]
            var currentLineNo = lineIdx + 1
            var restartCycle = true

            while (restartCycle) {
                restartCycle = false
                var deleted = false
                var changedToText = false

                suspendOrRunCommands@ for (cmd in commands) {
                    if (!matcher.matches(cmd, currentLineNo, patternSpace)) continue

                    when (cmd) {
                        is SedCmd.Substitute -> {
                            val (replaced, didMatch) = applySubstitution(patternSpace, cmd)
                            patternSpace = replaced
                            if (didMatch && cmd.printOnMatch) {
                                outputLines.add(patternSpace)
                            }
                        }
                        is SedCmd.Delete -> {
                            deleted = true
                            break@suspendOrRunCommands
                        }
                        is SedCmd.DeleteFirstLine -> {
                            val nlIdx = patternSpace.indexOf('\n')
                            if (nlIdx != -1) {
                                patternSpace = patternSpace.substring(nlIdx + 1)
                                restartCycle = true
                            } else {
                                deleted = true
                            }
                            break@suspendOrRunCommands
                        }
                        is SedCmd.NextAppend -> {
                            if (lineIdx + 1 < rawLines.size) {
                                lineIdx++
                                currentLineNo = lineIdx + 1
                                patternSpace = "$patternSpace\n${rawLines[lineIdx]}"
                            } else {
                                quitRequested = true
                                break@suspendOrRunCommands
                            }
                        }
                        is SedCmd.NextLine -> {
                            if (!quiet) outputLines.add(patternSpace)
                            for (app in pendingAppends) outputLines.add(app)
                            pendingAppends.clear()
                            if (lineIdx + 1 < rawLines.size) {
                                lineIdx++
                                currentLineNo = lineIdx + 1
                                patternSpace = rawLines[lineIdx]
                            } else {
                                deleted = true
                                quitRequested = true
                                break@suspendOrRunCommands
                            }
                        }
                        is SedCmd.Print -> {
                            outputLines.add(patternSpace)
                        }
                        is SedCmd.PrintFirstLine -> {
                            val nlIdx = patternSpace.indexOf('\n')
                            outputLines.add(if (nlIdx != -1) patternSpace.substring(0, nlIdx) else patternSpace)
                        }
                        is SedCmd.Quit -> {
                            quitRequested = true
                            break@suspendOrRunCommands
                        }
                        is SedCmd.Transliterate -> {
                            val map = mutableMapOf<Char, Char>()
                            val len = minOf(cmd.fromChars.length, cmd.toChars.length)
                            for (k in 0 until len) map[cmd.fromChars[k]] = cmd.toChars[k]
                            patternSpace = buildString {
                                for (ch in patternSpace) append(map[ch] ?: ch)
                            }
                        }
                        is SedCmd.TextInsert -> {
                            when (cmd.mode) {
                                'i' -> outputLines.add(cmd.text)
                                'a' -> pendingAppends.add(cmd.text)
                                'c' -> {
                                    outputLines.add(cmd.text)
                                    changedToText = true
                                    deleted = true
                                    break@suspendOrRunCommands
                                }
                            }
                        }
                        is SedCmd.Block -> {
                            for (sub in cmd.commands) {
                                if (!matcher.matches(sub, currentLineNo, patternSpace)) continue
                                when (sub) {
                                    is SedCmd.Substitute -> {
                                        val (replaced, didMatch) = applySubstitution(patternSpace, sub)
                                        patternSpace = replaced
                                        if (didMatch && sub.printOnMatch) outputLines.add(patternSpace)
                                    }
                                    is SedCmd.Delete -> {
                                        deleted = true
                                        break@suspendOrRunCommands
                                    }
                                    is SedCmd.Print -> outputLines.add(patternSpace)
                                    is SedCmd.Quit -> {
                                        quitRequested = true
                                        break@suspendOrRunCommands
                                    }
                                    else -> {}
                                }
                            }
                        }
                    }
                }

                if (!restartCycle) {
                    if (!deleted && !quiet && !changedToText) {
                        outputLines.add(patternSpace)
                    }
                    if (pendingAppends.isNotEmpty()) {
                        outputLines.addAll(pendingAppends)
                        pendingAppends.clear()
                    }
                }
            }

            lineIdx++
        }

        if (outputLines.isEmpty()) return ""
        val joined = outputLines.joinToString("\n")
        return if (hadTrailingNewline) "$joined\n" else joined
    }
}
