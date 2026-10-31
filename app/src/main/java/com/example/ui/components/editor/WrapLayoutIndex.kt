package com.example.ui.components.editor

import androidx.collection.LruCache
import java.util.Arrays

data class VisualRowInfo(
    val logicalLine: Int,
    val subRow: Int,
    val subRowCount: Int,
    val colStart: Int,
    val colEnd: Int
)

/**
 * High-speed visual row mapper supporting both unwrapped O(1) mode and soft word-wrap O(log N) mode
 * for documents with 100,000+ lines.
 */
class WrapLayoutIndex {

    var isWordWrapEnabled: Boolean = false
        private set

    private var wrapWidthPx: Float = 0f
    private var monoCharWidthPx: Float = 16f
    private var isMonospaceFont: Boolean = true

    private var rowCounts = IntArray(128)
    private var bit = IntArray(129)
    private var trackedLines = 0
    private var highestBit = 1

    private val breaksCache = LruCache<String, IntArray>(512)

    fun configure(
        wordWrap: Boolean,
        availableWidthPx: Float,
        charWidthPx: Float,
        isMonospace: Boolean,
        buffer: CodeDocumentBuffer,
        measureFn: (String, Int, Int) -> Float
    ): Boolean {
        val cleanWidth = availableWidthPx.coerceAtLeast(charWidthPx * 6f)
        val changed = (isWordWrapEnabled != wordWrap) ||
            (wordWrap && kotlin.math.abs(wrapWidthPx - cleanWidth) > 1.5f) ||
            (kotlin.math.abs(monoCharWidthPx - charWidthPx) > 0.05f) ||
            (isMonospaceFont != isMonospace) ||
            (trackedLines != buffer.lineCount)

        if (!changed) return false

        isWordWrapEnabled = wordWrap
        wrapWidthPx = cleanWidth
        monoCharWidthPx = charWidthPx.coerceAtLeast(1f)
        isMonospaceFont = isMonospace
        breaksCache.evictAll()
        rebuildAll(buffer, measureFn)
        return true
    }

    fun getTotalVisualRows(buffer: CodeDocumentBuffer): Int {
        if (!isWordWrapEnabled || wrapWidthPx <= 0f) return buffer.lineCount
        if (trackedLines != buffer.lineCount) return buffer.lineCount
        return bitPrefixSum(trackedLines).coerceAtLeast(1)
    }

    fun getFirstVisualRowForLine(lineIndex: Int, buffer: CodeDocumentBuffer): Int {
        val clamped = lineIndex.coerceIn(0, buffer.lineCount - 1)
        if (!isWordWrapEnabled || wrapWidthPx <= 0f || trackedLines != buffer.lineCount) {
            return clamped
        }
        return bitPrefixSum(clamped)
    }

    fun getLineRowBreaks(
        lineText: String,
        measureFn: (String, Int, Int) -> Float
    ): IntArray {
        if (!isWordWrapEnabled || wrapWidthPx <= 0f || lineText.isEmpty()) {
            return intArrayOf(0, lineText.length)
        }

        // Fast-path: short ASCII line that definitely fits on 1 row
        val maxPossibleWidth = if (isMonospaceFont && isSimpleAsciiNoTabs(lineText)) {
            lineText.length * monoCharWidthPx
        } else {
            lineText.length * monoCharWidthPx * 2.2f
        }
        if (maxPossibleWidth <= wrapWidthPx) {
            return intArrayOf(0, lineText.length)
        }

        breaksCache.get(lineText)?.let { return it }

        val computed = computeBreaksSlow(lineText, measureFn)
        if (lineText.length <= 4_000) {
            breaksCache.put(lineText, computed)
        }
        return computed
    }

    fun onSingleLineUpdated(
        lineIndex: Int,
        buffer: CodeDocumentBuffer,
        measureFn: (String, Int, Int) -> Float
    ) {
        if (!isWordWrapEnabled || wrapWidthPx <= 0f) {
            trackedLines = buffer.lineCount
            return
        }
        if (trackedLines != buffer.lineCount || lineIndex !in 0 until trackedLines) {
            rebuildAll(buffer, measureFn)
            return
        }
        val newRows = estimateOrCountRows(buffer.getLine(lineIndex), measureFn)
        val oldRows = rowCounts[lineIndex]
        if (newRows != oldRows) {
            rowCounts[lineIndex] = newRows
            addFenwick(lineIndex + 1, newRows - oldRows)
        }
    }

    fun rebuildAll(
        buffer: CodeDocumentBuffer,
        measureFn: (String, Int, Int) -> Float
    ) {
        val n = buffer.lineCount
        trackedLines = n
        if (!isWordWrapEnabled || wrapWidthPx <= 0f) return

        if (rowCounts.size < n) {
            rowCounts = IntArray(maxOf(n, rowCounts.size * 2, 128))
        }
        if (bit.size < n + 1) {
            bit = IntArray(maxOf(n + 1, bit.size * 2, 129))
        } else {
            Arrays.fill(bit, 0, n + 1, 0)
        }

        for (i in 0 until n) {
            val lineStr = buffer.getLine(i)
            val rows = estimateOrCountRows(lineStr, measureFn)
            rowCounts[i] = rows
            val idx1 = i + 1
            bit[idx1] += rows
            val parent = idx1 + (idx1 and -idx1)
            if (parent <= n) {
                bit[parent] += bit[idx1]
            }
        }
        highestBit = Integer.highestOneBit(n).coerceAtLeast(1)
    }

    fun getVisualRowInfo(
        visualRow: Int,
        buffer: CodeDocumentBuffer,
        measureFn: (String, Int, Int) -> Float
    ): VisualRowInfo {
        val lineCount = buffer.lineCount
        if (!isWordWrapEnabled || wrapWidthPx <= 0f || trackedLines != lineCount) {
            val safeLine = visualRow.coerceIn(0, lineCount - 1)
            val len = buffer.getLineLength(safeLine)
            return VisualRowInfo(safeLine, 0, 1, 0, len)
        }

        val totalRows = getTotalVisualRows(buffer)
        val clampedRow = visualRow.coerceIn(0, (totalRows - 1).coerceAtLeast(0))

        // Binary lifting on Fenwick tree to find logicalLine where bitPrefixSum(logicalLine) <= clampedRow
        var idx = 0
        var sum = 0
        var step = highestBit
        while (step > 0) {
            val next = idx + step
            if (next <= trackedLines && sum + bit[next] <= clampedRow) {
                idx = next
                sum += bit[next]
            }
            step = step ushr 1
        }
        val logicalLine = idx.coerceIn(0, lineCount - 1)
        val lineStr = buffer.getLine(logicalLine)
        val breaks = getLineRowBreaks(lineStr, measureFn)
        val subCount = (breaks.size - 1).coerceAtLeast(1)
        val subRow = (clampedRow - sum).coerceIn(0, subCount - 1)
        return VisualRowInfo(
            logicalLine = logicalLine,
            subRow = subRow,
            subRowCount = subCount,
            colStart = breaks[subRow],
            colEnd = breaks[subRow + 1]
        )
    }

    private fun estimateOrCountRows(
        lineText: String,
        measureFn: (String, Int, Int) -> Float
    ): Int {
        if (lineText.isEmpty()) return 1
        if (isMonospaceFont && isSimpleAsciiNoTabs(lineText)) {
            if (lineText.length * monoCharWidthPx <= wrapWidthPx) return 1
            if (lineText.length > 4_000) {
                val colsPerRow = (wrapWidthPx / monoCharWidthPx).toInt().coerceAtLeast(4)
                return (lineText.length + colsPerRow - 1) / colsPerRow
            }
        }
        val breaks = getLineRowBreaks(lineText, measureFn)
        return (breaks.size - 1).coerceAtLeast(1)
    }

    private fun computeBreaksSlow(
        lineText: String,
        measureFn: (String, Int, Int) -> Float
    ): IntArray {
        val len = lineText.length
        if (len == 0) return intArrayOf(0, 0)

        // Ultra-long single-line guard (e.g. minified JS bundle on 1 line)
        val approxCols = (wrapWidthPx / monoCharWidthPx).toInt().coerceAtLeast(6)
        if (len > 4_000) {
            val count = (len + approxCols - 1) / approxCols
            val out = IntArray(count + 1)
            var pos = 0
            var k = 0
            out[k++] = 0
            while (pos < len && k < out.size) {
                var next = (pos + approxCols).coerceAtMost(len)
                if (next < len && Character.isHighSurrogate(lineText[next - 1]) && Character.isLowSurrogate(lineText[next])) {
                    next--
                }
                if (next <= pos) next = (pos + 1).coerceAtMost(len)
                out[k++] = next
                pos = next
            }
            return if (k == out.size) out else out.copyOf(k)
        }

        val breaks = ArrayList<Int>(8)
        breaks.add(0)
        var start = 0
        while (start < len) {
            if (measureFn(lineText, start, len) <= wrapWidthPx) {
                breaks.add(len)
                break
            }
            // Binary search largest end in (start+1 .. len) where measureFn(start, end) <= wrapWidthPx
            var low = start + 1
            var high = (start + approxCols * 2 + 16).coerceAtMost(len)
            while (high < len && measureFn(lineText, start, high) <= wrapWidthPx) {
                low = high
                high = (high * 2 - start).coerceAtMost(len)
            }
            var best = start + 1
            while (low <= high) {
                val mid = (low + high) ushr 1
                if (measureFn(lineText, start, mid) <= wrapWidthPx) {
                    best = mid
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }
            // Avoid splitting UTF-16 surrogate pair
            if (best < len && best > start + 1 &&
                Character.isHighSurrogate(lineText[best - 1]) &&
                Character.isLowSurrogate(lineText[best])
            ) {
                best--
            }
            // Prefer clean word/punctuation break within last 20 chars if available
            if (best < len && best - start > 12) {
                val minWordBack = maxOf(start + 4, best - 20)
                var wordBreak = -1
                for (scan in best - 1 downTo minWordBack) {
                    val ch = lineText[scan]
                    if (ch == ' ' || ch == '\t' || ch == ',' || ch == ';' || ch == '/' || ch == '(' || ch == '{') {
                        wordBreak = scan + 1
                        break
                    }
                }
                if (wordBreak > start) {
                    best = wordBreak
                }
            }
            breaks.add(best)
            start = best
        }
        val out = IntArray(breaks.size)
        for (i in breaks.indices) out[i] = breaks[i]
        return out
    }

    private fun isSimpleAsciiNoTabs(s: String): Boolean {
        for (i in 0 until s.length) {
            val c = s[i].code
            if (c < 0x20 || c > 0x7E) return false
        }
        return true
    }

    private fun addFenwick(oneBasedIdx: Int, delta: Int) {
        var i = oneBasedIdx
        while (i <= trackedLines) {
            bit[i] += delta
            i += i and -i
        }
    }

    private fun bitPrefixSum(count: Int): Int {
        var sum = 0
        var i = count.coerceIn(0, trackedLines)
        while (i > 0) {
            sum += bit[i]
            i -= i and -i
        }
        return sum
    }
}
