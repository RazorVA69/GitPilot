package com.example.ui.components.terminal

import java.util.regex.Pattern

/**
 * Built-in Python 3 Script Engine for GitHub Terminal.
 *
 * Supports common file manipulation, regex substitutions, batch patch modifications,
 * glob file iteration, and text processing workflows used by developers in GitHub terminals.
 */
object PythonScriptEngine {

    data class Context(
        val workingDir: String,
        val getFile: suspend (String) -> String?,
        val writeFile: suspend (String, String) -> Unit,
        val listMatchingFiles: (String) -> List<String>,
        val printOutput: (String) -> Unit,
        val printError: (String) -> Unit,
        val tempFiles: Map<String, String>
    )

    private enum class ControlFlow {
        NONE,
        CONTINUE,
        BREAK,
        RETURN
    }

    suspend fun execute(script: String, context: Context): Boolean {
        val lines = script.lines()
        val env = mutableMapOf<String, Any?>()
        // Standard constants
        env["True"] = true
        env["False"] = false
        env["None"] = null
        env["re.S"] = Pattern.DOTALL
        env["re.DOTALL"] = Pattern.DOTALL
        env["re.I"] = Pattern.CASE_INSENSITIVE
        env["re.IGNORECASE"] = Pattern.CASE_INSENSITIVE
        env["re.M"] = Pattern.MULTILINE
        env["re.MULTILINE"] = Pattern.MULTILINE

        try {
            executeBlock(lines, 0, lines.size, 0, env, context)
            return true
        } catch (e: Exception) {
            context.printError("Traceback (most recent call last):\n  ${e.message ?: e.toString()}")
            return false
        }
    }

    private suspend fun executeBlock(
        lines: List<String>,
        startLine: Int,
        endLine: Int,
        baseIndent: Int,
        env: MutableMap<String, Any?>,
        context: Context
    ): ControlFlow {
        var idx = startLine
        while (idx < endLine) {
            val rawLine = lines[idx]
            val trimmed = rawLine.trim()

            // Skip empty lines or pure comments
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                idx++
                continue
            }

            val indent = getLineIndent(rawLine)
            if (indent < baseIndent) {
                // Outer block boundary reached
                break
            }

            // 1. FOR loop
            val forMatch = Regex("""^for\s+([a-zA-Z_][a-zA-Z0-9_]*)\s+in\s+(.+):$""").find(trimmed)
            if (forMatch != null) {
                val varName = forMatch.groupValues[1]
                val iterExpr = forMatch.groupValues[2]
                val iterable = evalExpression(iterExpr, env, context)

                // Find block bounds
                val blockStart = idx + 1
                var blockEnd = blockStart
                while (blockEnd < endLine) {
                    val nextRaw = lines[blockEnd]
                    val nextTrim = nextRaw.trim()
                    if (nextTrim.isNotEmpty() && !nextTrim.startsWith("#")) {
                        if (getLineIndent(nextRaw) <= indent) break
                    }
                    blockEnd++
                }

                val itemsList: List<Any?> = when (iterable) {
                    is List<*> -> iterable
                    is String -> iterable.lines()
                    is Set<*> -> iterable.toList()
                    is Array<*> -> iterable.toList()
                    else -> emptyList()
                }

                for (item in itemsList) {
                    env[varName] = item
                    val flow = executeBlock(lines, blockStart, blockEnd, indent + 1, env, context)
                    if (flow == ControlFlow.BREAK) break
                    if (flow == ControlFlow.RETURN) return ControlFlow.RETURN
                }

                idx = blockEnd
                continue
            }

            // 2. IF statement
            val ifMatch = Regex("""^if\s+(.+):$""").find(trimmed)
            if (ifMatch != null) {
                val condExpr = ifMatch.groupValues[1]
                val condVal = isTruthy(evalExpression(condExpr, env, context))

                // Find block bounds
                val blockStart = idx + 1
                var blockEnd = blockStart
                while (blockEnd < endLine) {
                    val nextRaw = lines[blockEnd]
                    val nextTrim = nextRaw.trim()
                    if (nextTrim.isNotEmpty() && !nextTrim.startsWith("#")) {
                        if (getLineIndent(nextRaw) <= indent) break
                    }
                    blockEnd++
                }

                if (condVal) {
                    val flow = executeBlock(lines, blockStart, blockEnd, indent + 1, env, context)
                    if (flow != ControlFlow.NONE) return flow
                }

                idx = blockEnd
                continue
            }

            // 3. Control flow statements
            if (trimmed == "continue") {
                return ControlFlow.CONTINUE
            }
            if (trimmed == "break") {
                return ControlFlow.BREAK
            }
            if (trimmed == "pass") {
                idx++
                continue
            }

            // 4. Import statement
            if (trimmed.startsWith("import ") || trimmed.startsWith("from ")) {
                idx++
                continue
            }

            // 5. Assignment: var = expr
            val assignMatch = Regex("""^([a-zA-Z_][a-zA-Z0-9_]*)\s*=\s*(.+)$""").find(trimmed)
            if (assignMatch != null && !trimmed.startsWith("if ") && !trimmed.contains("==")) {
                val varName = assignMatch.groupValues[1]
                val rhs = assignMatch.groupValues[2]
                val value = evalExpression(rhs, env, context)
                env[varName] = value
                idx++
                continue
            }

            // 6. Expression statement (e.g. open(...).write(...) or print(...))
            evalExpression(trimmed, env, context)
            idx++
        }

        return ControlFlow.NONE
    }

    private fun getLineIndent(line: String): Int {
        var count = 0
        for (c in line) {
            if (c == ' ') count++
            else if (c == '\t') count += 4
            else break
        }
        return count
    }

    private fun isTruthy(value: Any?): Boolean {
        return when (value) {
            null -> false
            is Boolean -> value
            is Number -> value.toDouble() != 0.0
            is String -> value.isNotEmpty()
            is Collection<*> -> value.isNotEmpty()
            is Map<*, *> -> value.isNotEmpty()
            else -> true
        }
    }

    private suspend fun evalExpression(rawExpr: String, env: MutableMap<String, Any?>, context: Context): Any? {
        val expr = rawExpr.trim()
        if (expr.isEmpty()) return null

        // Logical 'or'
        if (expr.contains(" or ")) {
            val parts = splitByTopLevel(expr, " or ")
            if (parts.size > 1) {
                for (p in parts) {
                    val v = evalExpression(p, env, context)
                    if (isTruthy(v)) return v
                }
                return false
            }
        }

        // Logical 'and'
        if (expr.contains(" and ")) {
            val parts = splitByTopLevel(expr, " and ")
            if (parts.size > 1) {
                var last: Any? = true
                for (p in parts) {
                    val v = evalExpression(p, env, context)
                    if (!isTruthy(v)) return false
                    last = v
                }
                return last
            }
        }

        // Logical 'not in'
        if (expr.contains(" not in ")) {
            val parts = splitByTopLevel(expr, " not in ")
            if (parts.size == 2) {
                val left = evalExpression(parts[0], env, context)
                val right = evalExpression(parts[1], env, context)
                return !containsCheck(right, left)
            }
        }

        // Logical 'in'
        if (expr.contains(" in ") && !expr.contains(" not in ")) {
            val parts = splitByTopLevel(expr, " in ")
            if (parts.size == 2) {
                val left = evalExpression(parts[0], env, context)
                val right = evalExpression(parts[1], env, context)
                return containsCheck(right, left)
            }
        }

        // Comparison '=='
        if (expr.contains(" == ")) {
            val parts = splitByTopLevel(expr, " == ")
            if (parts.size == 2) {
                val l = evalExpression(parts[0], env, context)
                val r = evalExpression(parts[1], env, context)
                return l == r
            }
        }

        // Comparison '!='
        if (expr.contains(" != ")) {
            val parts = splitByTopLevel(expr, " != ")
            if (parts.size == 2) {
                val l = evalExpression(parts[0], env, context)
                val r = evalExpression(parts[1], env, context)
                return l != r
            }
        }

        // Print statement/function: print(...)
        if (expr.startsWith("print(") && expr.endsWith(")")) {
            val inside = expr.removePrefix("print(").removeSuffix(")")
            val argExprs = parseArguments(inside)
            val outputs = argExprs.map { evalExpression(it, env, context)?.toString() ?: "None" }
            val joined = outputs.joinToString(" ")
            context.printOutput(joined)
            return null
        }

        // open(...).write(...)
        val writeMatch = Regex("""^open\((.+?)\)\.write\((.+)\)$""", RegexOption.DOT_MATCHES_ALL).find(expr)
        if (writeMatch != null) {
            val openArgs = parseArguments(writeMatch.groupValues[1])
            val filePathExpr = openArgs.firstOrNull() ?: ""
            val filePath = evalExpression(filePathExpr, env, context)?.toString() ?: ""
            val writeVal = evalExpression(writeMatch.groupValues[2], env, context)?.toString() ?: ""

            context.writeFile(filePath, writeVal)
            return writeVal.length
        }

        // open(...).read()
        val readMatch = Regex("""^open\((.+?)\)\.read\(\)$""").find(expr)
        if (readMatch != null) {
            val openArgs = parseArguments(readMatch.groupValues[1])
            val filePathExpr = openArgs.firstOrNull() ?: ""
            val filePath = evalExpression(filePathExpr, env, context)?.toString() ?: ""
            return context.getFile(filePath) ?: ""
        }

        // glob.glob(...)
        if (expr.startsWith("glob.glob(") && expr.endsWith(")")) {
            val inside = expr.removePrefix("glob.glob(").removeSuffix(")")
            val pat = evalExpression(inside, env, context)?.toString() ?: "*"
            return context.listMatchingFiles(pat)
        }

        // re.sub(pattern, replacement, string, count=0, flags=0)
        if (expr.startsWith("re.sub(") && expr.endsWith(")")) {
            val inside = expr.removePrefix("re.sub(").removeSuffix(")")
            val args = parseArguments(inside)
            if (args.size >= 3) {
                val patStr = evalStringLiteral(args[0], env)
                val replStr = evalStringLiteral(args[1], env)
                val targetStr = evalExpression(args[2], env, context)?.toString() ?: ""

                var count = 0
                var flagMask = 0
                for (a in args.drop(3)) {
                    val trimmedArg = a.trim()
                    if (trimmedArg.startsWith("count=")) {
                        count = trimmedArg.removePrefix("count=").trim().toIntOrNull() ?: 0
                    } else if (trimmedArg.startsWith("flags=")) {
                        val flagExpr = trimmedArg.removePrefix("flags=").trim()
                        if (flagExpr == "re.S" || flagExpr == "re.DOTALL") flagMask = flagMask or Pattern.DOTALL
                        if (flagExpr == "re.I" || flagExpr == "re.IGNORECASE") flagMask = flagMask or Pattern.CASE_INSENSITIVE
                        if (flagExpr == "re.M" || flagExpr == "re.MULTILINE") flagMask = flagMask or Pattern.MULTILINE
                    }
                }

                return try {
                    val p = Pattern.compile(patStr, flagMask)
                    val m = p.matcher(targetStr)
                    if (count == 1) {
                        m.replaceFirst(replStr)
                    } else {
                        m.replaceAll(replStr)
                    }
                } catch (e: Exception) {
                    targetStr
                }
            }
        }

        // Method chain: s.replace(...).replace(...)
        if (expr.contains(".replace(") && expr.endsWith(")")) {
            return evalStringMethodChains(expr, env, context)
        }

        // String literals
        if ((expr.startsWith("\"") && expr.endsWith("\"")) || (expr.startsWith("'") && expr.endsWith("'"))) {
            return evalStringLiteral(expr, env)
        }
        if (expr.startsWith("r\"") || expr.startsWith("r'")) {
            return evalStringLiteral(expr, env)
        }

        // Numbers
        val intVal = expr.toIntOrNull()
        if (intVal != null) return intVal
        val doubleVal = expr.toDoubleOrNull()
        if (doubleVal != null) return doubleVal

        // Boolean & None
        if (expr == "True") return true
        if (expr == "False") return false
        if (expr == "None") return null

        // Variable lookup
        if (env.containsKey(expr)) {
            return env[expr]
        }

        return expr
    }

    private suspend fun evalStringMethodChains(expr: String, env: MutableMap<String, Any?>, context: Context): Any? {
        val firstReplace = expr.indexOf(".replace(")
        if (firstReplace == -1) return null

        val targetPart = expr.substring(0, firstReplace).trim()
        var currentVal = evalExpression(targetPart, env, context)?.toString() ?: ""

        var idx = firstReplace
        while (idx < expr.length && expr.startsWith(".replace(", idx)) {
            val startArgs = idx + ".replace(".length
            var depth = 1
            var i = startArgs
            var inS = false
            var inD = false
            while (i < expr.length && depth > 0) {
                val c = expr[i]
                if (c == '\'' && !inD) inS = !inS
                else if (c == '"' && !inS) inD = !inD
                else if (!inS && !inD) {
                    if (c == '(') depth++
                    else if (c == ')') depth--
                }
                if (depth > 0) i++
            }
            val argContent = expr.substring(startArgs, i)
            val args = parseArguments(argContent)
            if (args.size >= 2) {
                val oldStr = evalExpression(args[0], env, context)?.toString() ?: ""
                val newStr = evalExpression(args[1], env, context)?.toString() ?: ""
                currentVal = currentVal.replace(oldStr, newStr)
            }
            idx = i + 1
        }
        return currentVal
    }

    private fun evalStringLiteral(literal: String, env: Map<String, Any?>): String {
        val trimmed = literal.trim()
        if (trimmed.startsWith("r\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(2, trimmed.length - 1)
        }
        if (trimmed.startsWith("r'") && trimmed.endsWith("'")) {
            return trimmed.substring(2, trimmed.length - 1)
        }
        if (trimmed.startsWith("\"\"\"") && trimmed.endsWith("\"\"\"")) {
            return trimmed.substring(3, trimmed.length - 3)
        }
        if (trimmed.startsWith("'''") && trimmed.endsWith("'''")) {
            return trimmed.substring(3, trimmed.length - 3)
        }
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            val inside = trimmed.substring(1, trimmed.length - 1)
            return unescapeString(inside)
        }
        if (trimmed.startsWith("'") && trimmed.endsWith("'")) {
            val inside = trimmed.substring(1, trimmed.length - 1)
            return unescapeString(inside)
        }
        if (env.containsKey(trimmed)) {
            return env[trimmed]?.toString() ?: ""
        }
        return trimmed
    }

    private fun unescapeString(s: String): String {
        return s.replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\'", "'")
            .replace("\\\\", "\\")
    }

    private fun containsCheck(container: Any?, item: Any?): Boolean {
        if (container == null || item == null) return false
        val itemStr = item.toString()
        return when (container) {
            is String -> container.contains(itemStr)
            is Collection<*> -> container.contains(item) || container.contains(itemStr)
            is Map<*, *> -> container.containsKey(item) || container.containsKey(itemStr)
            else -> false
        }
    }

    private fun splitByTopLevel(text: String, delimiter: String): List<String> {
        val result = mutableListOf<String>()
        var depthParen = 0
        var inSingle = false
        var inDouble = false
        var lastIdx = 0

        var i = 0
        while (i <= text.length - delimiter.length) {
            val c = text[i]
            if (c == '\'' && !inDouble) inSingle = !inSingle
            else if (c == '"' && !inSingle) inDouble = !inDouble
            else if (!inSingle && !inDouble) {
                if (c == '(') depthParen++
                else if (c == ')') depthParen--
                else if (depthParen == 0 && text.startsWith(delimiter, i)) {
                    result.add(text.substring(lastIdx, i).trim())
                    i += delimiter.length
                    lastIdx = i
                    continue
                }
            }
            i++
        }
        if (lastIdx < text.length) {
            result.add(text.substring(lastIdx).trim())
        }
        return if (result.isEmpty()) listOf(text) else result
    }

    private fun parseArguments(argString: String): List<String> {
        val args = mutableListOf<String>()
        var depthParen = 0
        var inSingle = false
        var inDouble = false
        var lastIdx = 0

        for (i in argString.indices) {
            val c = argString[i]
            if (c == '\'' && !inDouble) inSingle = !inSingle
            else if (c == '"' && !inSingle) inDouble = !inDouble
            else if (!inSingle && !inDouble) {
                if (c == '(' || c == '[' || c == '{') depthParen++
                else if (c == ')' || c == ']' || c == '}') depthParen--
                else if (c == ',' && depthParen == 0) {
                    args.add(argString.substring(lastIdx, i).trim())
                    lastIdx = i + 1
                }
            }
        }
        if (lastIdx < argString.length) {
            val remaining = argString.substring(lastIdx).trim()
            if (remaining.isNotEmpty()) args.add(remaining)
        }
        return args
    }
}
