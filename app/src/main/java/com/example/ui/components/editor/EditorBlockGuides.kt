package com.example.ui.components.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextLayoutResult

/**
 * Represents a code block hierarchy range for drawing VSCode-style block indent guides.
 */
data class CodeBlockGuide(
    val indentSpaces: Int,
    val startCharOffset: Int,
    val endCharOffset: Int,
    val hasClosingBrace: Boolean
)

object EditorBlockGuideHelper {

    /**
     * Computes all code block ranges (e.g. curly brace pairs, indentation scopes) from source text.
     * Cached with remember(text) so it never runs during scroll.
     */
    fun computeBlockGuides(text: String): List<CodeBlockGuide> {
        if (text.isEmpty()) return emptyList()

        val lineStarts = ArrayList<Int>(1024)
        lineStarts.add(0)
        for (i in text.indices) {
            if (text[i] == '\n' && i + 1 < text.length) {
                lineStarts.add(i + 1)
            }
        }

        fun getLineIndex(offset: Int): Int {
            var low = 0
            var high = lineStarts.size - 1
            var best = 0
            while (low <= high) {
                val mid = (low + high) ushr 1
                if (lineStarts[mid] <= offset) {
                    best = mid
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }
            return best
        }

        fun getLeadingSpaces(lineStartOffset: Int): Int {
            var spaces = 0
            var idx = lineStartOffset
            while (idx < text.length) {
                val c = text[idx]
                if (c == ' ') spaces++
                else if (c == '\t') spaces += 4
                else if (c == '\r') { /* ignore */ }
                else break
                idx++
            }
            return spaces
        }

        data class OpenBrace(
            val charOffset: Int,
            val lineIndex: Int,
            val indentSpaces: Int,
            val brace: Char
        )

        val braceStack = mutableListOf<OpenBrace>()
        val result = mutableListOf<CodeBlockGuide>()

        var inLineComment = false
        var inBlockComment = false
        var inString = false
        var stringChar = ' '
        var isEscape = false

        var i = 0
        val len = text.length

        while (i < len) {
            val c = text[i]
            val nextC = if (i + 1 < len) text[i + 1] else ' '

            if (inLineComment) {
                if (c == '\n') inLineComment = false
                i++
                continue
            }

            if (inBlockComment) {
                if (c == '*' && nextC == '/') {
                    inBlockComment = false
                    i += 2
                    continue
                }
                i++
                continue
            }

            if (inString) {
                if (isEscape) {
                    isEscape = false
                } else if (c == '\\') {
                    isEscape = true
                } else if (c == stringChar) {
                    inString = false
                } else if (c == '\n' && stringChar == '\'') {
                    inString = false
                }
                i++
                continue
            }

            // Check comments and strings
            if (c == '/' && nextC == '/') {
                inLineComment = true
                i += 2
                continue
            }
            if (c == '/' && nextC == '*') {
                inBlockComment = true
                i += 2
                continue
            }
            if (c == '"' || c == '\'') {
                inString = true
                stringChar = c
                isEscape = false
                i++
                continue
            }

            // Process Braces
            if (c == '{' || c == '(' || c == '[') {
                val lineIdx = getLineIndex(i)
                val lineStart = lineStarts[lineIdx]
                val indent = getLeadingSpaces(lineStart)
                braceStack.add(OpenBrace(i, lineIdx, indent, c))
            } else if (c == '}' || c == ')' || c == ']') {
                val expectedOpen = when (c) {
                    '}' -> '{'
                    ')' -> '('
                    ']' -> '['
                    else -> ' '
                }

                // Find matching open brace from top of stack
                var foundIdx = -1
                for (sIdx in braceStack.indices.reversed()) {
                    if (braceStack[sIdx].brace == expectedOpen) {
                        foundIdx = sIdx
                        break
                    }
                }

                if (foundIdx >= 0) {
                    val open = braceStack.removeAt(foundIdx)
                    val endLine = getLineIndex(i)
                    if (endLine > open.lineIndex && open.indentSpaces > 0) {
                        result.add(
                            CodeBlockGuide(
                                indentSpaces = open.indentSpaces,
                                startCharOffset = lineStarts[open.lineIndex],
                                endCharOffset = lineStarts[endLine],
                                hasClosingBrace = true
                            )
                        )
                    }
                }
            }

            i++
        }

        return result
    }

    /**
     * Renders VSCode-style block indent lines with start/end indicators.
     */
    fun renderBlockGuides(
        drawScope: DrawScope,
        layout: TextLayoutResult,
        guides: List<CodeBlockGuide>,
        charWidth: Float,
        cursorOffset: Int,
        accentColor: Color
    ) {
        if (guides.isEmpty() || layout.lineCount <= 0) return

        val defaultGuideColor = Color(0x2864748B) // Crisp subtle slate guide line
        val activeGuideColor = accentColor.copy(alpha = 0.85f)
        val tickLength = (charWidth * 0.75f).coerceIn(4f, 12f)

        // Find the most specific active block enclosing cursor
        val activeGuide = guides
            .filter { cursorOffset in it.startCharOffset..it.endCharOffset }
            .maxByOrNull { it.indentSpaces }

        for (guide in guides) {
            if (guide.indentSpaces <= 0) continue

            val startVisualLine = layout.getLineForOffset(guide.startCharOffset.coerceIn(0, layout.layoutInput.text.length))
            val endVisualLine = layout.getLineForOffset(guide.endCharOffset.coerceIn(0, layout.layoutInput.text.length))

            if (startVisualLine >= endVisualLine) continue

            val x = guide.indentSpaces * charWidth
            val yStart = layout.getLineBottom(startVisualLine)
            val yEnd = if (guide.hasClosingBrace) layout.getLineBaseline(endVisualLine) else layout.getLineBottom(endVisualLine)

            val isActive = (guide == activeGuide)
            val color = if (isActive) activeGuideColor else defaultGuideColor
            val strokeWidth = if (isActive) 1.6f else 1.1f

            // 1. Top start tick (┌) - clearly indicates block origin
            drawScope.drawLine(
                color = color,
                start = Offset(x, yStart),
                end = Offset(x + tickLength, yStart),
                strokeWidth = strokeWidth
            )

            // 2. Continuous vertical block guide line (│) - connects start to end
            drawScope.drawLine(
                color = color,
                start = Offset(x, yStart),
                end = Offset(x, yEnd),
                strokeWidth = strokeWidth
            )

            // 3. Bottom end tick (└) - clearly connects into closing brace
            if (guide.hasClosingBrace) {
                drawScope.drawLine(
                    color = color,
                    start = Offset(x, yEnd),
                    end = Offset(x + tickLength, yEnd),
                    strokeWidth = strokeWidth
                )
            }
        }
    }
}
