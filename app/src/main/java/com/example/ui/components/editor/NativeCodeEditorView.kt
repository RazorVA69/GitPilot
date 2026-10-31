package com.example.ui.components.editor

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.InputType
import android.view.ActionMode
import android.view.GestureDetector
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.CursorAnchorInfo
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.OverScroller
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.inputmethod.EditorInfoCompat
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Per-file tab state cached in memory so switching tabs is instantaneous (O(1))
 * while preserving full Undo/Redo history, cursor/selection, and scroll offsets.
 */
class EditorTabSession(
    val filePath: String,
    initialContent: String
) {
    val buffer = CodeDocumentBuffer(initialContent)
    val undoRedo = UndoRedoManager(maxHistorySize = 500)
    var selectionAnchor: Int = 0
    var selectionCaret: Int = 0
    var scrollX: Int = 0
    var scrollY: Int = 0
    var lastDispatchedContent: String = initialContent
    var hasPendingLocalSync: Boolean = false
    var pendingJumpLine: Pair<Int, Boolean>? = null
}

/**
 * Observable controller bridging Compose UI chrome (TopAppBar, Tabs, Find/Replace, Scrollbars,
 * Status Bar) with [NativeCodeEditorView] without causing per-frame Compose recompositions.
 */
class EditorController {
    private val sessions = HashMap<String, EditorTabSession>()

    internal var attachedView: NativeCodeEditorView? = null

    var activeFilePath by mutableStateOf("")
        private set

    var lineCount by mutableIntStateOf(1)
        internal set

    var charCount by mutableIntStateOf(0)
        internal set

    var cursorLine by mutableIntStateOf(1)
        internal set

    var cursorColumn by mutableIntStateOf(1)
        internal set

    var hasSelection by mutableStateOf(false)
        internal set

    var canUndo by mutableStateOf(false)
        internal set

    var canRedo by mutableStateOf(false)
        internal set

    var hasConflictMarkers by mutableStateOf(false)
        internal set

    var scrollY by mutableIntStateOf(0)
        internal set

    var maxScrollY by mutableIntStateOf(0)
        internal set

    var viewportHeight by mutableIntStateOf(1)
        internal set

    var scrollX by mutableIntStateOf(0)
        internal set

    var maxScrollX by mutableIntStateOf(0)
        internal set

    var viewportWidth by mutableIntStateOf(1)
        internal set

    var localEditCounter by mutableLongStateOf(0L)
        internal set

    var activeSession: EditorTabSession? = null
        private set

    fun getOrCreateSession(
        filePath: String,
        content: String,
        savedScrollY: Int = 0,
        savedScrollX: Int = 0,
        savedSelStart: Int = 0,
        savedSelEnd: Int = 0
    ): EditorTabSession {
        val existing = sessions[filePath]
        if (existing != null) {
            // Only overwrite buffer if external content genuinely changed and no local unsynced edits exist
            if (!existing.hasPendingLocalSync && content != existing.lastDispatchedContent && content != existing.buffer.getFullText()) {
                existing.buffer.setText(content, markClean = false)
                existing.lastDispatchedContent = content
                val len = existing.buffer.length
                existing.selectionAnchor = existing.selectionAnchor.coerceIn(0, len)
                existing.selectionCaret = existing.selectionCaret.coerceIn(0, len)
            }
            return existing
        }
        val created = EditorTabSession(filePath, content).apply {
            val len = buffer.length
            // Default to start of file (0, 0) instead of EOF so opening a file never jumps to bottom!
            selectionAnchor = savedSelStart.coerceIn(0, len)
            selectionCaret = savedSelEnd.coerceIn(0, len)
            scrollY = savedScrollY.coerceAtLeast(0)
            scrollX = savedScrollX.coerceAtLeast(0)
            lastDispatchedContent = content
        }
        sessions[filePath] = created
        return created
    }

    fun switchToFile(
        filePath: String,
        content: String,
        savedScrollY: Int = 0,
        savedScrollX: Int = 0,
        savedSelStart: Int = 0,
        savedSelEnd: Int = 0
    ) {
        attachedView?.saveStateToSession()
        val session = getOrCreateSession(
            filePath = filePath,
            content = content,
            savedScrollY = savedScrollY,
            savedScrollX = savedScrollX,
            savedSelStart = savedSelStart,
            savedSelEnd = savedSelEnd
        )
        activeFilePath = filePath
        activeSession = session
        attachedView?.bindSession(session)
        syncSnapshotFromSession(session)
    }

    /**
     * Called when [content] prop updates for the SAME [filePath].
     * Ignores echoes of edits that originated inside this editor, preventing cursor/scroll jumps!
     */
    fun onExternalContentPropChanged(filePath: String, incomingContent: String) {
        val session = sessions[filePath] ?: return
        if (session.hasPendingLocalSync) {
            if (incomingContent == session.lastDispatchedContent) {
                // ViewModel caught up to our latest dispatched edit
                return
            }
            // Local buffer is still ahead of ViewModel debounce — NEVER clobber user's live typing!
            return
        }
        if (incomingContent == session.lastDispatchedContent) {
            return
        }
        val currentText = session.buffer.getFullText()
        if (incomingContent == currentText) {
            session.lastDispatchedContent = incomingContent
            return
        }
        // Genuine external content update (e.g. Discard Changes, Git checkout, Conflict resolved externally)
        session.buffer.setText(incomingContent, markClean = true)
        session.lastDispatchedContent = incomingContent
        session.undoRedo.clear()
        val len = session.buffer.length
        session.selectionAnchor = session.selectionAnchor.coerceIn(0, len)
        session.selectionCaret = session.selectionCaret.coerceIn(0, len)
        if (activeSession === session) {
            attachedView?.onSessionBufferExternallyReplaced()
            syncSnapshotFromSession(session)
        }
    }

    /**
     * Replaces entire document content as an explicit user action (e.g. Discard Changes, Resolve Conflict, Replace All).
     */
    fun replaceAllUserAction(newContent: String, resetUndo: Boolean = false, moveCursorToStart: Boolean = false) {
        val view = attachedView
        if (view != null) {
            view.replaceFullDocumentText(newContent, resetUndo = resetUndo, moveCursorToStart = moveCursorToStart)
        } else {
            val session = activeSession ?: return
            session.buffer.setText(newContent, markClean = resetUndo)
            if (resetUndo) session.undoRedo.clear()
            if (moveCursorToStart) {
                session.selectionAnchor = 0
                session.selectionCaret = 0
            }
            session.hasPendingLocalSync = true
            localEditCounter++
            syncSnapshotFromSession(session)
        }
    }

    fun flushPendingSync(onContentChange: (String) -> Unit) {
        sessions.values.forEach { session ->
            if (session.hasPendingLocalSync) {
                val full = session.buffer.getFullText()
                session.hasPendingLocalSync = false
                if (full != session.lastDispatchedContent) {
                    session.lastDispatchedContent = full
                    if (session === activeSession) {
                        onContentChange(full)
                    }
                }
            }
        }
    }

    fun closeSession(filePath: String) {
        sessions.remove(filePath)
    }

    fun cut() { attachedView?.cutSelection() }
    fun copy() { attachedView?.copySelection(copyAllIfCollapsed = true) }
    fun paste() { attachedView?.pasteClipboard() }
    fun selectAll() { attachedView?.selectAll() }
    fun deselect() { attachedView?.collapseSelection() }
    fun undo() { attachedView?.undo() }
    fun redo() { attachedView?.redo() }
    fun insertSymbol(symbol: String, cursorOffsetInSymbol: Int) {
        attachedView?.insertSymbolAtSelection(symbol, cursorOffsetInSymbol)
    }
    fun jumpToLine(oneBasedLine: Int, selectEntireLine: Boolean = false) {
        val view = attachedView
        if (view != null) {
            view.jumpToLine(oneBasedLine, selectEntireLine = selectEntireLine)
        } else {
            activeSession?.pendingJumpLine = Pair(oneBasedLine, selectEntireLine)
        }
    }
    fun scrollToTop() {
        attachedView?.scrollToDocumentTop()
    }
    fun scrollToBottom() {
        attachedView?.scrollToDocumentBottom()
    }
    fun scrollToVerticalPx(targetY: Int, animate: Boolean = false) {
        attachedView?.scrollVerticalTo(targetY, animate = animate)
    }
    fun scrollToHorizontalPx(targetX: Int, animate: Boolean = false) {
        attachedView?.scrollHorizontalTo(targetX, animate = animate)
    }
    fun selectAndRevealRange(startOffset: Int, endOffset: Int) {
        attachedView?.selectRangeAndReveal(startOffset, endOffset)
    }
    fun replaceRangeUserAction(startOffset: Int, endOffset: Int, replacement: String) {
        attachedView?.replaceRangeAndSelectAfter(startOffset, endOffset, replacement)
    }
    fun getSelectedText(): String {
        return attachedView?.getSelectedTextString() ?: ""
    }

    internal fun syncSnapshotFromSession(session: EditorTabSession) {
        val buf = session.buffer
        lineCount = buf.lineCount
        charCount = buf.length
        val lc = buf.getLineAndColumn(session.selectionCaret)
        cursorLine = lc.line + 1
        cursorColumn = lc.column + 1
        hasSelection = session.selectionAnchor != session.selectionCaret
        canUndo = session.undoRedo.canUndo
        canRedo = session.undoRedo.canRedo
        hasConflictMarkers = buf.hasConflictMarkers()
    }
}

/**
 * Native Android Canvas + InputConnection code editor view.
 *
 * Key guarantees:
 * 1. 100% Gboard compatibility (including Gboard Text Editing panel: Select, Select all, Cut, Copy,
 *    Paste, directional arrows, Home/End, Backspace, composing text, glide typing, cursor anchor).
 * 2. Virtualized viewport rendering: renders ONLY the visible ~40 lines per frame, handling
 *    100,000+ line / multi-MB code & text files smoothly at 60/120 FPS with zero Binder overflow.
 * 3. Zero unexpected cursor/scroll jumps: strict batch-edit isolation, passive-only layout updates,
 *    and minimal-delta cursor visibility rules.
 */
class NativeCodeEditorView(context: Context) : View(context) {

    companion object {
        private const val MAX_IME_SURROUNDING_CHARS = 65_536
        private const val MAX_IME_EXTRACTED_WINDOW = 131_072
        private const val TAB_SPACES = 4

        private const val HANDLE_NONE = 0
        private const val HANDLE_START = 1
        private const val HANDLE_END = 2
        private const val HANDLE_CARET = 3

        private val NUMBER_STRINGS = Array(20_000) { it.toString() }
    }

    var controller: EditorController? = null
    var onImmediateContentSync: ((String) -> Unit)? = null
    var onFontSizePinchChanged: ((Float) -> Unit)? = null

    private var session: EditorTabSession = EditorTabSession("", "")
    val buffer: CodeDocumentBuffer
        get() = session.buffer

    // Configuration properties
    var isReadOnlyMode: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                if (value) stopSelectionActionMode()
                invalidate()
            }
        }

    var showLineNumbers: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                recalculateLayoutMetrics(rebuildWrap = isWordWrapEnabled)
                invalidate()
            }
        }

    var isWordWrapEnabled: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                if (value) virtualScrollX = 0
                recalculateLayoutMetrics(rebuildWrap = true)
                invalidate()
            }
        }

    var isSyntaxHighlightingEnabled: Boolean = true
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private var language: SupportedLanguage = SupportedLanguage.PLAIN_TEXT
    private var fontFamilyOption: EditorFontFamily = EditorFontFamily.JETBRAINS_MONO
    private var fontSizeSp: Float = 13.5f

    // Search match highlight state
    private var searchMatchOffsets: IntArray = IntArray(0)
    private var searchMatchLength: Int = 0
    private var activeSearchMatchIndex: Int = -1

    // Selection & IME Composing state
    var selectionAnchor: Int = 0
        private set
    var selectionCaret: Int = 0
        private set
    val selMin: Int
        get() = min(selectionAnchor, selectionCaret)
    val selMax: Int
        get() = max(selectionAnchor, selectionCaret)

    var composingStart: Int = -1
        private set
    var composingEnd: Int = -1
        private set

    private var preferredCursorX: Float = -1f
    private var batchEditDepth: Int = 0
    private var pendingImeNotify: Boolean = false
    private var pendingDocNotify: Boolean = false
    private var pendingEnsureVisible: Boolean = false
    private var imeShiftHeld: Boolean = false
    private var pendingJumpTargetLine: Int = -1

    private var extractedTextRequest: ExtractedTextRequest? = null
    private var monitorCursorAnchor: Boolean = false

    // Virtualized scrolling & physics
    private val scroller = OverScroller(context)
    private var velocityTracker: VelocityTracker? = null
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val minFlingVelocity = ViewConfiguration.get(context).scaledMinimumFlingVelocity
    private val maxFlingVelocity = ViewConfiguration.get(context).scaledMaximumFlingVelocity

    var virtualScrollX: Int = 0
        private set
    var virtualScrollY: Int = 0
        private set

    // Syntax & Wrap engines
    private val syntaxHighlighter = ViewportSyntaxHighlighter()
    private val wrapIndex = WrapLayoutIndex()
    private var cachedBracketMatch: Pair<Int, Int>? = null
    private var cachedBracketVersion: Long = -1L
    private var cachedBracketCursor: Int = -1

    // Typography & Drawing Paints
    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity

    private var baseTypeface: Typeface = Typeface.MONOSPACE
    private var boldTypeface: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    private var italicTypeface: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.ITALIC)

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isSubpixelText = true
    }
    private val gutterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        color = AndroidColor.parseColor("#98979F")
    }
    private val gutterActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.RIGHT
        color = AndroidColor.parseColor("#2563EB")
        isFakeBoldText = true
    }
    private val wrapSymbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.parseColor("#98979F")
        typeface = Typeface.MONOSPACE
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    // Theme colors
    private var bgColorArgb: Int = AndroidColor.parseColor("#FFFFFF")
    private var gutterBgColorArgb: Int = AndroidColor.parseColor("#FFFFFF")
    private var gutterDividerColorArgb: Int = AndroidColor.parseColor("#E4E4E7")
    private var textColorArgb: Int = AndroidColor.parseColor("#18181B")
    private var accentColorArgb: Int = AndroidColor.parseColor("#2563EB")
    private var selectionBgColorArgb: Int = AndroidColor.parseColor("#383B82F6")
    private var activeLineBgColorArgb: Int = AndroidColor.parseColor("#0D2563EB")
    private var indentGuideColorArgb: Int = AndroidColor.parseColor("#2464748B")
    private var bracketBgColorArgb: Int = AndroidColor.parseColor("#330F9D74")
    private var bracketBorderColorArgb: Int = AndroidColor.parseColor("#0F9D74")

    // Metrics
    private var fontSizePx: Float = 13.5f * scaledDensity
    private var lineHeightPx: Float = 20f * scaledDensity
    private var baselineOffsetPx: Float = 16f * scaledDensity
    private var spaceWidthPx: Float = 8f * scaledDensity
    private var gutterWidthPx: Float = 44f * density
    private val topPaddingPx: Float = 12f * density
    private val bottomExtraSpacePx: Float = 160f * density
    private val codeStartPaddingPx: Float = 12f * density
    private val codeEndPaddingPx: Float = 24f * density

    // Cursor blink & selection handles
    private var cursorBlinkVisible: Boolean = true
    private var showCaretHandleUntilMs: Long = 0L
    private var activeDragHandle: Int = HANDLE_NONE
    private var isLongPressDragSelecting: Boolean = false
    private var longPressAnchorOffset: Int = 0
    private var isTouchPanning: Boolean = false
    private var lastTouchX: Float = 0f
    private var lastTouchY: Float = 0f
    private var downTouchX: Float = 0f
    private var downTouchY: Float = 0f

    private val leftHandleCenter = RectF()
    private val rightHandleCenter = RectF()
    private val caretHandleCenter = RectF()
    private val handlePath = Path()
    private var selectionActionMode: ActionMode? = null

    private val blinkRunnable = object : Runnable {
        override fun run() {
            if (isAttachedToWindow && hasFocus() && !isReadOnlyMode) {
                cursorBlinkVisible = !cursorBlinkVisible
                invalidate()
                postDelayed(this, 530L)
            }
        }
    }

    private val measureSegmentFn: (String, Int, Int) -> Float = { line, start, end ->
        measureTextWithTabs(line, start, end)
    }

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            private var startFontSp = 13.5f
            override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
                startFontSp = fontSizeSp
                return true
            }

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val target = (startFontSp * detector.scaleFactor).coerceIn(9f, 26f)
                if (abs(target - fontSizeSp) >= 0.25f) {
                    startFontSp = target
                    setTypography(target, fontFamilyOption)
                    onFontSizePinchChanged?.invoke(target)
                }
                return true
            }
        }
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean {
                if (!scroller.isFinished) {
                    scroller.abortAnimation()
                }
                return true
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                val hitOffset = hitTestCharacterOffset(e.x, e.y)
                imeShiftHeld = false
                imeFinishComposingText()
                setSelectionInternal(hitOffset, hitOffset, ensureVisible = false, notifyIme = true)
                preferredCursorX = -1f
                showCaretHandleUntilMs = System.currentTimeMillis() + 3500L
                stopSelectionActionMode()
                requestEditorFocusAndShowKeyboard()
                invalidate()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                val hitOffset = hitTestCharacterOffset(e.x, e.y)
                val wordRange = findWordBoundsAt(hitOffset)
                imeFinishComposingText()
                setSelectionInternal(wordRange.first, wordRange.second, ensureVisible = false, notifyIme = true)
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                requestEditorFocusAndShowKeyboard()
                if (wordRange.first != wordRange.second) {
                    showSelectionActionMode()
                }
                invalidate()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (scaleDetector.isInProgress || isTouchPanning) return
                val hitOffset = hitTestCharacterOffset(e.x, e.y)
                requestEditorFocusAndShowKeyboard()
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

                // If user long-presses on an ALREADY selected region, preserve their selection and show Cut/Copy/Paste!
                if (selectionAnchor != selectionCaret && hitOffset in selMin..selMax) {
                    showSelectionActionMode()
                    invalidate()
                    return
                }

                val wordRange = findWordBoundsAt(hitOffset)
                imeFinishComposingText()
                if (wordRange.first < wordRange.second) {
                    longPressAnchorOffset = wordRange.first
                    isLongPressDragSelecting = true
                    setSelectionInternal(wordRange.first, wordRange.second, ensureVisible = false, notifyIme = true)
                } else {
                    longPressAnchorOffset = hitOffset
                    isLongPressDragSelecting = true
                    setSelectionInternal(hitOffset, hitOffset, ensureVisible = false, notifyIme = true)
                }
                showSelectionActionMode()
                invalidate()
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (activeDragHandle != HANDLE_NONE || isLongPressDragSelecting || scaleDetector.isInProgress) {
                    return false
                }
                val vx = if (isWordWrapEnabled) 0 else (-velocityX).roundToInt().coerceIn(-maxFlingVelocity, maxFlingVelocity)
                val vy = (-velocityY).roundToInt().coerceIn(-maxFlingVelocity, maxFlingVelocity)
                if (abs(vx) > minFlingVelocity || abs(vy) > minFlingVelocity) {
                    stopSelectionActionMode()
                    scroller.fling(
                        virtualScrollX,
                        virtualScrollY,
                        vx,
                        vy,
                        0,
                        computeMaxScrollX(),
                        0,
                        computeMaxScrollY()
                    )
                    postInvalidateOnAnimation()
                    return true
                }
                return false
            }
        }
    )

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        isClickable = true
        isLongClickable = true
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            defaultFocusHighlightEnabled = false
        }
        applyTypographyMetrics()
    }

    // =========================================================================
    // SESSION & CONFIGURATION API
    // =========================================================================

    fun bindSession(target: EditorTabSession) {
        if (!scroller.isFinished) scroller.abortAnimation()
        stopSelectionActionMode()
        composingStart = -1
        composingEnd = -1
        batchEditDepth = 0
        pendingImeNotify = false
        pendingDocNotify = false
        pendingEnsureVisible = false
        imeShiftHeld = false
        pendingJumpTargetLine = -1
        preferredCursorX = -1f

        session = target
        val len = buffer.length
        selectionAnchor = target.selectionAnchor.coerceIn(0, len)
        selectionCaret = target.selectionCaret.coerceIn(0, len)

        syntaxHighlighter.setLanguage(language)
        syntaxHighlighter.invalidateAll()
        recalculateLayoutMetrics(rebuildWrap = true)

        virtualScrollY = target.scrollY.coerceIn(0, computeMaxScrollY())
        virtualScrollX = if (isWordWrapEnabled) 0 else target.scrollX.coerceIn(0, computeMaxScrollX())

        val deferredJump = target.pendingJumpLine
        if (deferredJump != null) {
            target.pendingJumpLine = null
            jumpToLine(deferredJump.first, selectEntireLine = deferredJump.second)
            return
        }

        restartImeForNewDocument()
        syncControllerScrollAndDocState()
        invalidate()
    }

    fun saveStateToSession() {
        session.selectionAnchor = selectionAnchor
        session.selectionCaret = selectionCaret
        session.scrollX = virtualScrollX
        session.scrollY = virtualScrollY
    }

    fun onSessionBufferExternallyReplaced() {
        composingStart = -1
        composingEnd = -1
        val len = buffer.length
        selectionAnchor = session.selectionAnchor.coerceIn(0, len)
        selectionCaret = session.selectionCaret.coerceIn(0, len)
        syntaxHighlighter.invalidateAll()
        recalculateLayoutMetrics(rebuildWrap = true)
        virtualScrollY = virtualScrollY.coerceIn(0, computeMaxScrollY())
        virtualScrollX = virtualScrollX.coerceIn(0, computeMaxScrollX())
        restartImeForNewDocument()
        syncControllerScrollAndDocState()
        invalidate()
    }

    fun setLanguage(newLanguage: SupportedLanguage) {
        if (language != newLanguage) {
            language = newLanguage
            syntaxHighlighter.setLanguage(newLanguage)
            syntaxHighlighter.invalidateAll()
            invalidate()
        }
    }

    fun setTypography(newFontSizeSp: Float, newFontFamily: EditorFontFamily) {
        val clampedSp = newFontSizeSp.coerceIn(8f, 28f)
        if (abs(fontSizeSp - clampedSp) < 0.01f && fontFamilyOption == newFontFamily) return
        fontSizeSp = clampedSp
        fontFamilyOption = newFontFamily
        applyTypographyMetrics()
        recalculateLayoutMetrics(rebuildWrap = true)
        virtualScrollY = virtualScrollY.coerceIn(0, computeMaxScrollY())
        virtualScrollX = virtualScrollX.coerceIn(0, computeMaxScrollX())
        syncControllerScrollAndDocState()
        invalidate()
    }

    fun setThemeColors(
        bgColor: Int,
        gutterDividerColor: Int,
        textColor: Int,
        accentColor: Int
    ) {
        bgColorArgb = bgColor
        gutterBgColorArgb = bgColor
        gutterDividerColorArgb = gutterDividerColor
        textColorArgb = textColor
        accentColorArgb = accentColor
        gutterActivePaint.color = accentColor
        invalidate()
    }

    fun setSearchHighlights(matches: IntArray, queryLength: Int, activeIndex: Int) {
        searchMatchOffsets = matches
        searchMatchLength = queryLength
        activeSearchMatchIndex = activeIndex
        invalidate()
    }

    private fun applyTypographyMetrics() {
        val dm = resources.displayMetrics
        fontSizePx = fontSizeSp * dm.scaledDensity
        baseTypeface = when (fontFamilyOption) {
            EditorFontFamily.JETBRAINS_MONO, EditorFontFamily.SYSTEM_MONO -> Typeface.MONOSPACE
            EditorFontFamily.SANS_SERIF -> Typeface.SANS_SERIF
            EditorFontFamily.SERIF -> Typeface.SERIF
            EditorFontFamily.CURSIVE -> Typeface.create("cursive", Typeface.NORMAL)
        }
        boldTypeface = Typeface.create(baseTypeface, Typeface.BOLD)
        italicTypeface = Typeface.create(baseTypeface, Typeface.ITALIC)

        textPaint.typeface = baseTypeface
        textPaint.textSize = fontSizePx
        gutterPaint.typeface = Typeface.MONOSPACE
        gutterPaint.textSize = fontSizePx * 0.90f
        gutterActivePaint.typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        gutterActivePaint.textSize = fontSizePx * 0.90f
        wrapSymbolPaint.textSize = fontSizePx * 0.78f

        val fm = textPaint.fontMetrics
        val naturalHeight = fm.descent - fm.ascent
        lineHeightPx = max(fontSizePx * 1.50f, naturalHeight * 1.18f)
        val extraLeading = (lineHeightPx - naturalHeight) * 0.5f
        baselineOffsetPx = extraLeading - fm.ascent
        spaceWidthPx = textPaint.measureText(" ").coerceAtLeast(4f)
    }

    private fun isMonospaceSelected(): Boolean {
        return fontFamilyOption == EditorFontFamily.JETBRAINS_MONO ||
            fontFamilyOption == EditorFontFamily.SYSTEM_MONO
    }

    private fun recalculateLayoutMetrics(rebuildWrap: Boolean) {
        val digits = max(2, buffer.lineCount.toString().length)
        gutterWidthPx = if (showLineNumbers) {
            digits * (fontSizePx * 0.62f) + 18f * density
        } else {
            0f
        }
        val codeAvailWidth = (width - gutterWidthPx - codeStartPaddingPx - codeEndPaddingPx)
            .coerceAtLeast(spaceWidthPx * 8f)
        if (width > 0) {
            val reconfigured = wrapIndex.configure(
                wordWrap = isWordWrapEnabled,
                availableWidthPx = codeAvailWidth,
                charWidthPx = spaceWidthPx,
                isMonospace = isMonospaceSelected(),
                buffer = buffer,
                measureFn = measureSegmentFn
            )
            if (!reconfigured && rebuildWrap && isWordWrapEnabled) {
                wrapIndex.rebuildAll(buffer, measureSegmentFn)
            }
        }
    }

    // =========================================================================
    // ANDROID IME / GBOARD INPUTCONNECTION IMPLEMENTATION
    // =========================================================================

    override fun onCheckIsTextEditor(): Boolean = !isReadOnlyMode

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        if (isReadOnlyMode) return null
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or
            InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_ACTION_NONE
        outAttrs.initialSelStart = selMin
        outAttrs.initialSelEnd = selMax
        outAttrs.initialCapsMode = 0

        // Provide safe bounded initial surrounding text for Android 11+ / Gboard fast startup
        try {
            if (selMax - selMin <= 4096) {
                val winStart = (selMin - 2048).coerceAtLeast(0)
                val winEnd = (selMax + 2048).coerceAtMost(buffer.length)
                if (winStart == 0 && winEnd == buffer.length && buffer.length <= 8192) {
                    EditorInfoCompat.setInitialSurroundingText(outAttrs, buffer.getFullText())
                } else {
                    val sub = buffer.subSequence(winStart, winEnd)
                    EditorInfoCompat.setInitialSurroundingSubText(outAttrs, sub, winStart)
                }
            }
        } catch (_: Exception) {
            // Safe fallback if platform helper rejects extreme indices
        }

        return EditorInputConnection(this)
    }

    internal fun beginImeBatchEdit(): Boolean {
        batchEditDepth++
        return true
    }

    internal fun endImeBatchEdit(): Boolean {
        if (batchEditDepth > 0) {
            batchEditDepth--
        }
        if (batchEditDepth == 0) {
            if (pendingEnsureVisible) {
                pendingEnsureVisible = false
                ensureCaretVisible(animate = false)
            }
            if (pendingImeNotify) {
                pendingImeNotify = false
                dispatchSelectionToIme()
            }
            if (pendingDocNotify) {
                pendingDocNotify = false
                notifyLocalEditCommitted(immediateSync = false)
            }
            resetCursorBlink()
            invalidate()
        }
        return batchEditDepth > 0
    }

    internal fun onInputConnectionClosed() {
        batchEditDepth = 0
        imeShiftHeld = false
        composingStart = -1
        composingEnd = -1
        extractedTextRequest = null
        monitorCursorAnchor = false
        invalidate()
    }

    internal fun imeClearMetaKeyStates(states: Int): Boolean {
        if ((states and KeyEvent.META_SHIFT_MASK) != 0 || (states and KeyEvent.META_SHIFT_ON) != 0) {
            imeShiftHeld = false
        }
        return true
    }

    internal fun imeCommitText(text: CharSequence, newCursorPosition: Int): Boolean {
        if (isReadOnlyMode) return false
        val str = CodeDocumentBuffer.normalizeLineEndings(text.toString())
        val hasRangeSel = selectionAnchor != selectionCaret
        val wasComposing = !hasRangeSel && hasActiveComposing()
        val replaceStart = if (wasComposing) composingStart.coerceIn(0, buffer.length) else selMin
        val replaceEnd = if (wasComposing) composingEnd.coerceIn(replaceStart, buffer.length) else selMax
        composingStart = -1
        composingEnd = -1
        if (hasRangeSel) imeShiftHeld = false

        // 1. Auto-indent on raw Enter key / newline commit
        if (str == "\n" && !wasComposing) {
            return insertNewlineWithAutoIndentAt(replaceStart, replaceEnd)
        }

        // 2. Smart bracket skip & auto-pair when typing a single non-composing delimiter
        if (str.length == 1 && !wasComposing && replaceStart == replaceEnd) {
            val ch = str[0]
            val nextCh = buffer.charAt(replaceStart)
            if ((ch == ')' || ch == ']' || ch == '}') && nextCh == ch) {
                setSelectionInternal(replaceStart + 1, replaceStart + 1, ensureVisible = true, notifyIme = true)
                return true
            }
            val closer = when (ch) {
                '(' -> ')'
                '[' -> ']'
                '{' -> '}'
                else -> '\u0000'
            }
            if (closer != '\u0000' && isAutoPairAllowedBefore(nextCh)) {
                applyBufferEdit(
                    start = replaceStart,
                    end = replaceEnd,
                    replacement = "$ch$closer",
                    newAnchor = replaceStart + 1,
                    newCaret = replaceStart + 1,
                    recordUndo = true,
                    allowUndoMerge = false,
                    ensureVisible = true,
                    notifyIme = true
                )
                return true
            }
        }

        val allowMerge = str.length == 1 && replaceStart == replaceEnd && str[0] != '\n'
        val targetCursorAfter = if (newCursorPosition > 0) {
            replaceStart + str.length + newCursorPosition - 1
        } else {
            replaceStart + newCursorPosition
        }

        applyBufferEdit(
            start = replaceStart,
            end = replaceEnd,
            replacement = str,
            newAnchor = targetCursorAfter,
            newCaret = targetCursorAfter,
            recordUndo = true,
            allowUndoMerge = allowMerge,
            ensureVisible = true,
            notifyIme = true
        )
        return true
    }

    internal fun imeSetComposingText(text: CharSequence, newCursorPosition: Int): Boolean {
        if (isReadOnlyMode) return false
        val str = CodeDocumentBuffer.normalizeLineEndings(text.toString())
        val wasComposing = hasActiveComposing()
        val replaceStart = if (wasComposing) composingStart.coerceIn(0, buffer.length) else selMin
        val replaceEnd = if (wasComposing) composingEnd.coerceIn(replaceStart, buffer.length) else selMax

        val targetCursor = if (newCursorPosition > 0) {
            replaceStart + str.length + newCursorPosition - 1
        } else {
            replaceStart + newCursorPosition
        }

        // Apply replacement in buffer and track composing span [replaceStart, replaceStart + str.length)
        applyBufferEdit(
            start = replaceStart,
            end = replaceEnd,
            replacement = str,
            newAnchor = targetCursor,
            newCaret = targetCursor,
            recordUndo = true,
            allowUndoMerge = true,
            ensureVisible = true,
            notifyIme = false
        )

        if (str.isNotEmpty()) {
            composingStart = replaceStart
            composingEnd = (replaceStart + str.length).coerceAtMost(buffer.length)
        } else {
            composingStart = -1
            composingEnd = -1
        }
        requestImeNotification()
        return true
    }

    internal fun imeSetComposingRegion(start: Int, end: Int): Boolean {
        val len = buffer.length
        val s = min(start, end).coerceIn(0, len)
        val e = max(start, end).coerceIn(0, len)
        if (s == e) {
            composingStart = -1
            composingEnd = -1
        } else {
            composingStart = s
            composingEnd = e
        }
        requestImeNotification()
        invalidate()
        return true
    }

    internal fun imeFinishComposingText(): Boolean {
        if (composingStart != -1 || composingEnd != -1) {
            composingStart = -1
            composingEnd = -1
            requestImeNotification()
            invalidate()
        }
        return true
    }

    internal fun imeDeleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        if (isReadOnlyMode) return false
        if (beforeLength <= 0 && afterLength <= 0) return true

        beginImeBatchEdit()
        try {
            val a = selMin
            val b = selMax

            // Delete after selection first so offsets before `a` remain unchanged
            if (afterLength > 0 && b < buffer.length) {
                val delAfterEnd = (b + afterLength).coerceAtMost(buffer.length)
                if (delAfterEnd > b) {
                    applyBufferEdit(
                        start = b,
                        end = delAfterEnd,
                        replacement = "",
                        newAnchor = selectionAnchor,
                        newCaret = selectionCaret,
                        recordUndo = true,
                        allowUndoMerge = afterLength == 1 && beforeLength == 0,
                        ensureVisible = true,
                        notifyIme = true
                    )
                }
            }

            if (beforeLength > 0 && a > 0) {
                val curMin = selMin
                val delBeforeStart = (curMin - beforeLength).coerceAtLeast(0)
                val removedCount = curMin - delBeforeStart
                if (removedCount > 0) {
                    val adjAnchor = (selectionAnchor - removedCount).coerceAtLeast(0)
                    val adjCaret = (selectionCaret - removedCount).coerceAtLeast(0)
                    applyBufferEdit(
                        start = delBeforeStart,
                        end = curMin,
                        replacement = "",
                        newAnchor = adjAnchor,
                        newCaret = adjCaret,
                        recordUndo = true,
                        allowUndoMerge = beforeLength == 1 && afterLength == 0,
                        ensureVisible = true,
                        notifyIme = true
                    )
                }
            }
            composingStart = -1
            composingEnd = -1
        } finally {
            endImeBatchEdit()
        }
        return true
    }

    internal fun imeDeleteSurroundingTextInCodePoints(beforeCodePoints: Int, afterCodePoints: Int): Boolean {
        if (isReadOnlyMode) return false
        val len = buffer.length
        val a = selMin
        val b = selMax

        var beforeChars = 0
        if (beforeCodePoints > 0 && a > 0) {
            val lookbackStart = (a - beforeCodePoints * 2 - 4).coerceAtLeast(0)
            val slice = buffer.subSequence(lookbackStart, a)
            var idx = slice.length
            var cpRemaining = beforeCodePoints
            while (idx > 0 && cpRemaining > 0) {
                idx = Character.offsetByCodePoints(slice, idx, -1)
                cpRemaining--
            }
            beforeChars = slice.length - idx
        }

        var afterChars = 0
        if (afterCodePoints > 0 && b < len) {
            val lookaheadEnd = (b + afterCodePoints * 2 + 4).coerceAtMost(len)
            val slice = buffer.subSequence(b, lookaheadEnd)
            var idx = 0
            var cpRemaining = afterCodePoints
            while (idx < slice.length && cpRemaining > 0) {
                idx = Character.offsetByCodePoints(slice, idx, 1)
                cpRemaining--
            }
            afterChars = idx
        }

        return imeDeleteSurroundingText(beforeChars, afterChars)
    }

    internal fun imeSetSelection(start: Int, end: Int): Boolean {
        val len = buffer.length
        val clampedAnchor = start.coerceIn(0, len)
        val clampedCaret = end.coerceIn(0, len)
        if (clampedAnchor != clampedCaret ||
            (hasActiveComposing() && clampedCaret !in composingStart..composingEnd)
        ) {
            composingStart = -1
            composingEnd = -1
        }
        setSelectionInternal(
            newAnchor = clampedAnchor,
            newCaret = clampedCaret,
            // Do not yank viewport on Select All from IME!
            ensureVisible = !(min(clampedAnchor, clampedCaret) == 0 && max(clampedAnchor, clampedCaret) == len && len > 0),
            notifyIme = true
        )
        return true
    }

    internal fun imeGetTextBeforeCursor(requestedLength: Int): CharSequence {
        val safeLen = requestedLength.coerceIn(0, MAX_IME_SURROUNDING_CHARS)
        val end = selMin
        val start = (end - safeLen).coerceAtLeast(0)
        return buffer.subSequence(start, end)
    }

    internal fun imeGetTextAfterCursor(requestedLength: Int): CharSequence {
        val safeLen = requestedLength.coerceIn(0, MAX_IME_SURROUNDING_CHARS)
        val start = selMax
        val end = (start + safeLen).coerceAtMost(buffer.length)
        return buffer.subSequence(start, end)
    }

    internal fun imeGetSelectedText(): CharSequence? {
        if (selectionAnchor == selectionCaret) return null
        val start = selMin
        // Cap IPC Binder payload to 128 KB while full in-process Cut/Copy handles multi-MB selections
        val end = min(selMax, start + MAX_IME_EXTRACTED_WINDOW)
        return buffer.subSequence(start, end)
    }

    internal fun imeGetExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText {
        if ((flags and InputConnection.GET_EXTRACTED_TEXT_MONITOR) != 0 && request != null) {
            extractedTextRequest = request
        }
        return buildExtractedTextSnapshot()
    }

    private fun buildExtractedTextSnapshot(): ExtractedText {
        val out = ExtractedText()
        val total = buffer.length
        out.partialStartOffset = -1
        out.partialEndOffset = -1
        if (total <= MAX_IME_EXTRACTED_WINDOW) {
            out.startOffset = 0
            out.text = buffer.getFullText()
            out.selectionStart = selMin
            out.selectionEnd = selMax
        } else {
            // Sliding 128 KB window around caret prevents Binder TransactionTooLargeException on huge files!
            val half = MAX_IME_EXTRACTED_WINDOW / 2
            val winStart = (selectionCaret - half).coerceIn(0, (total - MAX_IME_EXTRACTED_WINDOW).coerceAtLeast(0))
            val winEnd = (winStart + MAX_IME_EXTRACTED_WINDOW).coerceAtMost(total)
            out.startOffset = winStart
            out.text = buffer.subSequence(winStart, winEnd)
            val winLen = winEnd - winStart
            out.selectionStart = (selMin - winStart).coerceIn(0, winLen)
            out.selectionEnd = (selMax - winStart).coerceIn(0, winLen)
        }
        return out
    }

    internal fun imePerformEditorAction(actionCode: Int): Boolean {
        if (isReadOnlyMode) return false
        return when (actionCode) {
            EditorInfo.IME_ACTION_NONE,
            EditorInfo.IME_ACTION_UNSPECIFIED,
            EditorInfo.IME_ACTION_DONE,
            EditorInfo.IME_ACTION_NEXT -> {
                imeFinishComposingText()
                insertNewlineWithAutoIndentAt(selMin, selMax)
            }
            else -> false
        }
    }

    internal fun imeRequestCursorUpdates(cursorUpdateMode: Int): Boolean {
        val immediate = (cursorUpdateMode and InputConnection.CURSOR_UPDATE_IMMEDIATE) != 0
        monitorCursorAnchor = (cursorUpdateMode and InputConnection.CURSOR_UPDATE_MONITOR) != 0
        if (immediate) {
            sendCursorAnchorInfoToIme()
        }
        return true
    }

    /**
     * Handles Gboard & Android Context Menu Actions:
     * - android.R.id.cut (0x01020020)
     * - android.R.id.copy (0x01020021)
     * - android.R.id.paste (0x01020022)
     * - android.R.id.pasteAsPlainText (0x01020031)
     * - android.R.id.selectAll (0x0102001f)
     * - android.R.id.undo / redo
     */
    fun performEditorContextMenuAction(id: Int): Boolean {
        return when (id) {
            android.R.id.selectAll -> {
                selectAll()
                true
            }
            android.R.id.cut -> {
                if (isReadOnlyMode) return false
                cutSelection()
                true
            }
            android.R.id.copy -> {
                copySelection(copyAllIfCollapsed = false)
                true
            }
            android.R.id.paste, android.R.id.pasteAsPlainText -> {
                if (isReadOnlyMode) return false
                pasteClipboard()
                true
            }
            android.R.id.undo -> {
                if (isReadOnlyMode) return false
                undo()
                true
            }
            android.R.id.redo -> {
                if (isReadOnlyMode) return false
                redo()
                true
            }
            else -> false
        }
    }

    // =========================================================================
    // CLIPBOARD, EDITING, UNDO/REDO, & KEYBOARD NAVIGATION
    // =========================================================================

    fun getSelectedTextString(): String {
        if (selectionAnchor == selectionCaret) return ""
        return buffer.subSequence(selMin, selMax)
    }

    fun copySelection(copyAllIfCollapsed: Boolean = false): Boolean {
        val textToCopy = if (selectionAnchor != selectionCaret) {
            buffer.subSequence(selMin, selMax)
        } else if (copyAllIfCollapsed) {
            buffer.getFullText()
        } else {
            ""
        }
        if (textToCopy.isEmpty()) return false
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("code", textToCopy))
        stopSelectionActionMode()
        return true
    }

    fun cutSelection(): Boolean {
        if (isReadOnlyMode || selectionAnchor == selectionCaret) return false
        val start = selMin
        val end = selMax
        val selectedText = buffer.subSequence(start, end)
        if (selectedText.isEmpty()) return false

        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        cm?.setPrimaryClip(ClipData.newPlainText("code", selectedText))

        composingStart = -1
        composingEnd = -1
        imeShiftHeld = false
        stopSelectionActionMode()

        applyBufferEdit(
            start = start,
            end = end,
            replacement = "",
            newAnchor = start,
            newCaret = start,
            recordUndo = true,
            allowUndoMerge = false,
            ensureVisible = true,
            notifyIme = true,
            immediateViewModelFlush = true
        )
        return true
    }

    fun pasteClipboard(): Boolean {
        if (isReadOnlyMode) return false
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        val clip = cm.primaryClip ?: return false
        if (clip.itemCount <= 0) return false
        val rawClip = clip.getItemAt(0).coerceToText(context)?.toString() ?: return false
        if (rawClip.isEmpty()) return false

        val normClip = CodeDocumentBuffer.normalizeLineEndings(rawClip)
        val hasRangeSel = selectionAnchor != selectionCaret
        val start = if (!hasRangeSel && hasActiveComposing()) composingStart.coerceIn(0, buffer.length) else selMin
        val end = if (!hasRangeSel && hasActiveComposing()) composingEnd.coerceIn(start, buffer.length) else selMax
        composingStart = -1
        composingEnd = -1
        imeShiftHeld = false
        stopSelectionActionMode()

        val afterCursor = start + normClip.length
        applyBufferEdit(
            start = start,
            end = end,
            replacement = normClip,
            newAnchor = afterCursor,
            newCaret = afterCursor,
            recordUndo = true,
            allowUndoMerge = false,
            ensureVisible = true,
            notifyIme = true,
            immediateViewModelFlush = true
        )
        return true
    }

    fun selectAll() {
        composingStart = -1
        composingEnd = -1
        val len = buffer.length
        // Select entire document WITHOUT scrolling away from the user's current line!
        setSelectionInternal(0, len, ensureVisible = false, notifyIme = true)
    }

    fun collapseSelection() {
        stopSelectionActionMode()
        setSelectionInternal(selectionCaret, selectionCaret, ensureVisible = false, notifyIme = true)
    }

    fun undo(): Boolean {
        if (isReadOnlyMode) return false
        imeFinishComposingText()
        stopSelectionActionMode()
        val restored = session.undoRedo.undo(buffer) ?: return false
        syntaxHighlighter.invalidateAll()
        recalculateLayoutMetrics(rebuildWrap = isWordWrapEnabled)
        setSelectionInternal(restored.first, restored.second, ensureVisible = true, notifyIme = true)
        notifyLocalEditCommitted(immediateSync = true)
        invalidate()
        return true
    }

    fun redo(): Boolean {
        if (isReadOnlyMode) return false
        imeFinishComposingText()
        stopSelectionActionMode()
        val restored = session.undoRedo.redo(buffer) ?: return false
        syntaxHighlighter.invalidateAll()
        recalculateLayoutMetrics(rebuildWrap = isWordWrapEnabled)
        setSelectionInternal(restored.first, restored.second, ensureVisible = true, notifyIme = true)
        notifyLocalEditCommitted(immediateSync = true)
        invalidate()
        return true
    }

    fun insertSymbolAtSelection(symbol: String, cursorOffsetInSymbol: Int) {
        if (isReadOnlyMode) return
        imeFinishComposingText()
        val start = selMin
        val end = selMax
        val targetCursor = (start + cursorOffsetInSymbol).coerceAtLeast(0)
        applyBufferEdit(
            start = start,
            end = end,
            replacement = symbol,
            newAnchor = targetCursor,
            newCaret = targetCursor,
            recordUndo = true,
            allowUndoMerge = false,
            ensureVisible = true,
            notifyIme = true
        )
    }

    fun replaceFullDocumentText(newContent: String, resetUndo: Boolean, moveCursorToStart: Boolean) {
        imeFinishComposingText()
        stopSelectionActionMode()
        val oldText = buffer.getFullText()
        if (newContent == oldText && !resetUndo) return

        val oldAnchor = selectionAnchor
        val oldCaret = selectionCaret
        if (resetUndo) {
            buffer.setText(newContent, markClean = true)
            session.undoRedo.clear()
        } else {
            val res = buffer.replace(0, buffer.length, newContent)
            val targetPos = if (moveCursorToStart) 0 else oldCaret.coerceIn(0, buffer.length)
            session.undoRedo.recordEdit(
                startOffset = 0,
                deletedText = res.deletedText,
                insertedText = res.insertedText,
                anchorBefore = oldAnchor,
                caretBefore = oldCaret,
                anchorAfter = targetPos,
                caretAfter = targetPos,
                allowMerge = false
            )
        }
        syntaxHighlighter.invalidateAll()
        recalculateLayoutMetrics(rebuildWrap = true)
        val newCursor = if (moveCursorToStart) 0 else selectionCaret.coerceIn(0, buffer.length)
        selectionAnchor = newCursor
        selectionCaret = newCursor
        if (moveCursorToStart) {
            virtualScrollY = 0
            virtualScrollX = 0
        } else {
            virtualScrollY = virtualScrollY.coerceIn(0, computeMaxScrollY())
            virtualScrollX = virtualScrollX.coerceIn(0, computeMaxScrollX())
        }
        restartImeForNewDocument()
        notifyLocalEditCommitted(immediateSync = true)
        invalidate()
    }

    fun replaceRangeAndSelectAfter(startOffset: Int, endOffset: Int, replacement: String) {
        if (isReadOnlyMode) return
        imeFinishComposingText()
        val s = min(startOffset, endOffset).coerceIn(0, buffer.length)
        val e = max(startOffset, endOffset).coerceIn(s, buffer.length)
        val norm = CodeDocumentBuffer.normalizeLineEndings(replacement)
        val after = s + norm.length
        applyBufferEdit(
            start = s,
            end = e,
            replacement = norm,
            newAnchor = after,
            newCaret = after,
            recordUndo = true,
            allowUndoMerge = false,
            ensureVisible = true,
            notifyIme = true,
            immediateViewModelFlush = true
        )
    }

    fun selectRangeAndReveal(startOffset: Int, endOffset: Int) {
        val len = buffer.length
        val s = startOffset.coerceIn(0, len)
        val e = endOffset.coerceIn(0, len)
        imeFinishComposingText()
        setSelectionInternal(s, e, ensureVisible = true, notifyIme = true)
    }

    fun jumpToLine(oneBasedLine: Int, selectEntireLine: Boolean = false) {
        val targetLineIdx = (oneBasedLine - 1).coerceIn(0, buffer.lineCount - 1)
        val lineStart = buffer.getLineStart(targetLineIdx)
        val lineEnd = buffer.getLineEnd(targetLineIdx)
        imeFinishComposingText()
        if (selectEntireLine) {
            selectionAnchor = lineStart
            selectionCaret = lineEnd
        } else {
            selectionAnchor = lineStart
            selectionCaret = lineStart
        }
        if (height <= 0) {
            pendingJumpTargetLine = targetLineIdx
            saveStateToSession()
            dispatchSelectionToIme()
            syncControllerScrollAndDocState()
            invalidate()
            return
        }
        pendingJumpTargetLine = -1
        val vRow = wrapIndex.getFirstVisualRowForLine(targetLineIdx, buffer)
        val targetY = (topPaddingPx + vRow * lineHeightPx - height * 0.25f)
            .roundToInt()
            .coerceIn(0, computeMaxScrollY())
        virtualScrollY = targetY
        if (!isWordWrapEnabled) virtualScrollX = 0
        saveStateToSession()
        dispatchSelectionToIme()
        syncControllerScrollAndDocState()
        invalidate()
    }

    fun scrollToDocumentTop() {
        if (!scroller.isFinished) scroller.abortAnimation()
        imeFinishComposingText()
        virtualScrollY = 0
        virtualScrollX = 0
        setSelectionInternal(0, 0, ensureVisible = false, notifyIme = true)
        syncControllerScrollAndDocState()
        invalidate()
    }

    fun scrollToDocumentBottom() {
        if (!scroller.isFinished) scroller.abortAnimation()
        imeFinishComposingText()
        val end = buffer.length
        virtualScrollY = computeMaxScrollY()
        setSelectionInternal(end, end, ensureVisible = false, notifyIme = true)
        syncControllerScrollAndDocState()
        invalidate()
    }

    fun scrollVerticalTo(targetY: Int, animate: Boolean = false) {
        val clamped = targetY.coerceIn(0, computeMaxScrollY())
        if (!scroller.isFinished) scroller.abortAnimation()
        if (animate && abs(clamped - virtualScrollY) > 4) {
            scroller.startScroll(virtualScrollX, virtualScrollY, 0, clamped - virtualScrollY, 220)
            postInvalidateOnAnimation()
        } else {
            virtualScrollY = clamped
            saveStateToSession()
            syncControllerScrollAndDocState()
            invalidate()
        }
    }

    fun scrollHorizontalTo(targetX: Int, animate: Boolean = false) {
        if (isWordWrapEnabled) return
        val clamped = targetX.coerceIn(0, computeMaxScrollX())
        if (!scroller.isFinished) scroller.abortAnimation()
        if (animate && abs(clamped - virtualScrollX) > 4) {
            scroller.startScroll(virtualScrollX, virtualScrollY, clamped - virtualScrollX, 0, 220)
            postInvalidateOnAnimation()
        } else {
            virtualScrollX = clamped
            saveStateToSession()
            syncControllerScrollAndDocState()
            invalidate()
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (handleKeyDownInternal(keyCode, event)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            imeShiftHeld = false
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    internal fun handleEditorKeyEvent(event: KeyEvent): Boolean {
        return when (event.action) {
            KeyEvent.ACTION_DOWN -> handleKeyDownInternal(event.keyCode, event)
            KeyEvent.ACTION_UP -> {
                if (event.keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || event.keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
                    imeShiftHeld = false
                    true
                } else {
                    isHandledEditorKeyCode(event.keyCode, event)
                }
            }
            KeyEvent.ACTION_MULTIPLE -> {
                val chars = event.characters
                if (!chars.isNullOrEmpty() && !isReadOnlyMode) {
                    imeCommitText(chars, 1)
                } else {
                    false
                }
            }
            else -> false
        }
    }

    private fun isHandledEditorKeyCode(keyCode: Int, event: KeyEvent): Boolean {
        val isCtrl = event.isCtrlPressed || (event.metaState and KeyEvent.META_CTRL_MASK) != 0
        return when (keyCode) {
            KeyEvent.KEYCODE_SHIFT_LEFT,
            KeyEvent.KEYCODE_SHIFT_RIGHT,
            KeyEvent.KEYCODE_DPAD_LEFT,
            KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_DPAD_UP,
            KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_MOVE_HOME,
            KeyEvent.KEYCODE_MOVE_END,
            KeyEvent.KEYCODE_PAGE_UP,
            KeyEvent.KEYCODE_PAGE_DOWN,
            KeyEvent.KEYCODE_CUT,
            KeyEvent.KEYCODE_COPY,
            KeyEvent.KEYCODE_PASTE,
            KeyEvent.KEYCODE_DEL,
            KeyEvent.KEYCODE_FORWARD_DEL,
            KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_NUMPAD_ENTER,
            KeyEvent.KEYCODE_TAB -> true
            KeyEvent.KEYCODE_A,
            KeyEvent.KEYCODE_C,
            KeyEvent.KEYCODE_X,
            KeyEvent.KEYCODE_V,
            KeyEvent.KEYCODE_Z,
            KeyEvent.KEYCODE_Y -> isCtrl
            else -> false
        }
    }

    private fun handleKeyDownInternal(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SHIFT_LEFT || keyCode == KeyEvent.KEYCODE_SHIFT_RIGHT) {
            imeShiftHeld = true
            return true
        }
        val meta = event.metaState
        val isShift = imeShiftHeld || event.isShiftPressed || (meta and KeyEvent.META_SHIFT_MASK) != 0 || (meta and KeyEvent.META_SHIFT_ON) != 0
        val isCtrl = event.isCtrlPressed || (meta and KeyEvent.META_CTRL_MASK) != 0 || (meta and KeyEvent.META_CTRL_ON) != 0
        val isAlt = event.isAltPressed || (meta and KeyEvent.META_ALT_MASK) != 0

        // IMPORTANT: Android KeyEvent constants:
        // KEYCODE_CUT  = 277 (0x115)
        // KEYCODE_COPY = 278 (0x116)
        // KEYCODE_PASTE = 279 (0x117)
        when (keyCode) {
            KeyEvent.KEYCODE_CUT -> {
                cutSelection()
                return true
            }
            KeyEvent.KEYCODE_COPY -> {
                copySelection(copyAllIfCollapsed = false)
                return true
            }
            KeyEvent.KEYCODE_PASTE -> {
                pasteClipboard()
                return true
            }
        }

        if (isCtrl) {
            when (keyCode) {
                KeyEvent.KEYCODE_A -> {
                    selectAll()
                    return true
                }
                KeyEvent.KEYCODE_C, KeyEvent.KEYCODE_INSERT -> {
                    copySelection(copyAllIfCollapsed = false)
                    return true
                }
                KeyEvent.KEYCODE_X -> {
                    cutSelection()
                    return true
                }
                KeyEvent.KEYCODE_V -> {
                    pasteClipboard()
                    return true
                }
                KeyEvent.KEYCODE_Z -> {
                    if (isShift) redo() else undo()
                    return true
                }
                KeyEvent.KEYCODE_Y -> {
                    redo()
                    return true
                }
            }
        }

        when (keyCode) {
            // 1. Gboard '<' / Left Arrow
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                imeFinishComposingText()
                preferredCursorX = -1f
                if (isShift) {
                    val nextCaret = if (isCtrl) stepWordLeft(selectionCaret) else stepCharLeft(selectionCaret)
                    setSelectionInternal(selectionAnchor, nextCaret, ensureVisible = true, notifyIme = true)
                } else {
                    val nextPos = if (selectionAnchor != selectionCaret) {
                        selMin
                    } else if (isCtrl) {
                        stepWordLeft(selectionCaret)
                    } else {
                        stepCharLeft(selectionCaret)
                    }
                    setSelectionInternal(nextPos, nextPos, ensureVisible = true, notifyIme = true)
                }
                return true
            }

            // 2. Gboard '>' / Right Arrow
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                imeFinishComposingText()
                preferredCursorX = -1f
                if (isShift) {
                    val nextCaret = if (isCtrl) stepWordRight(selectionCaret) else stepCharRight(selectionCaret)
                    setSelectionInternal(selectionAnchor, nextCaret, ensureVisible = true, notifyIme = true)
                } else {
                    val nextPos = if (selectionAnchor != selectionCaret) {
                        selMax
                    } else if (isCtrl) {
                        stepWordRight(selectionCaret)
                    } else {
                        stepCharRight(selectionCaret)
                    }
                    setSelectionInternal(nextPos, nextPos, ensureVisible = true, notifyIme = true)
                }
                return true
            }

            // 3. Gboard '^' / Up Arrow
            KeyEvent.KEYCODE_DPAD_UP -> {
                imeFinishComposingText()
                val target = computeVerticalMoveOffset(selectionCaret, deltaVisualRows = -1)
                if (isShift) {
                    setSelectionInternal(selectionAnchor, target, ensureVisible = true, notifyIme = true, keepPreferredX = true)
                } else {
                    setSelectionInternal(target, target, ensureVisible = true, notifyIme = true, keepPreferredX = true)
                }
                return true
            }

            // 4. Gboard 'v' / Down Arrow
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                imeFinishComposingText()
                val target = computeVerticalMoveOffset(selectionCaret, deltaVisualRows = 1)
                if (isShift) {
                    setSelectionInternal(selectionAnchor, target, ensureVisible = true, notifyIme = true, keepPreferredX = true)
                } else {
                    setSelectionInternal(target, target, ensureVisible = true, notifyIme = true, keepPreferredX = true)
                }
                return true
            }

            // 5. Gboard '|<' / Move Home
            KeyEvent.KEYCODE_MOVE_HOME -> {
                imeFinishComposingText()
                preferredCursorX = -1f
                val target = if (isCtrl) {
                    0
                } else {
                    val lc = buffer.getLineAndColumn(selectionCaret)
                    val lineStr = buffer.getLine(lc.line)
                    val lineStart = buffer.getLineStart(lc.line)
                    var firstNonWs = 0
                    while (firstNonWs < lineStr.length && (lineStr[firstNonWs] == ' ' || lineStr[firstNonWs] == '\t')) {
                        firstNonWs++
                    }
                    val codeStart = lineStart + firstNonWs
                    if (selectionCaret > codeStart || selectionCaret == lineStart) codeStart else lineStart
                }
                if (isShift) {
                    setSelectionInternal(selectionAnchor, target, ensureVisible = true, notifyIme = true)
                } else {
                    setSelectionInternal(target, target, ensureVisible = true, notifyIme = true)
                }
                return true
            }

            // 6. Gboard '>|' / Move End
            KeyEvent.KEYCODE_MOVE_END -> {
                imeFinishComposingText()
                preferredCursorX = -1f
                val target = if (isCtrl) {
                    buffer.length
                } else {
                    val lc = buffer.getLineAndColumn(selectionCaret)
                    buffer.getLineEnd(lc.line)
                }
                if (isShift) {
                    setSelectionInternal(selectionAnchor, target, ensureVisible = true, notifyIme = true)
                } else {
                    setSelectionInternal(target, target, ensureVisible = true, notifyIme = true)
                }
                return true
            }

            // 7. Page Up / Page Down
            KeyEvent.KEYCODE_PAGE_UP -> {
                imeFinishComposingText()
                val pageRows = max(1, (height / lineHeightPx).toInt() - 2)
                val target = computeVerticalMoveOffset(selectionCaret, deltaVisualRows = -pageRows)
                if (isShift) {
                    setSelectionInternal(selectionAnchor, target, ensureVisible = true, notifyIme = true, keepPreferredX = true)
                } else {
                    setSelectionInternal(target, target, ensureVisible = true, notifyIme = true, keepPreferredX = true)
                }
                return true
            }

            KeyEvent.KEYCODE_PAGE_DOWN -> {
                imeFinishComposingText()
                val pageRows = max(1, (height / lineHeightPx).toInt() - 2)
                val target = computeVerticalMoveOffset(selectionCaret, deltaVisualRows = pageRows)
                if (isShift) {
                    setSelectionInternal(selectionAnchor, target, ensureVisible = true, notifyIme = true, keepPreferredX = true)
                } else {
                    setSelectionInternal(target, target, ensureVisible = true, notifyIme = true, keepPreferredX = true)
                }
                return true
            }

            // 8. Backspace ([x])
            KeyEvent.KEYCODE_DEL -> {
                if (isReadOnlyMode) return false
                composingStart = -1
                composingEnd = -1
                if (selectionAnchor != selectionCaret) {
                    imeShiftHeld = false
                    val start = selMin
                    val end = selMax
                    applyBufferEdit(
                        start = start,
                        end = end,
                        replacement = "",
                        newAnchor = start,
                        newCaret = start,
                        recordUndo = true,
                        allowUndoMerge = false,
                        ensureVisible = true,
                        notifyIme = true
                    )
                } else if (selectionCaret > 0) {
                    val delStart = if (isCtrl) stepWordLeft(selectionCaret) else stepCharLeft(selectionCaret)
                    applyBufferEdit(
                        start = delStart,
                        end = selectionCaret,
                        replacement = "",
                        newAnchor = delStart,
                        newCaret = delStart,
                        recordUndo = true,
                        allowUndoMerge = !isCtrl,
                        ensureVisible = true,
                        notifyIme = true
                    )
                }
                return true
            }

            // 9. Forward Delete
            KeyEvent.KEYCODE_FORWARD_DEL -> {
                if (isReadOnlyMode) return false
                composingStart = -1
                composingEnd = -1
                if (selectionAnchor != selectionCaret) {
                    val start = selMin
                    val end = selMax
                    applyBufferEdit(
                        start = start,
                        end = end,
                        replacement = "",
                        newAnchor = start,
                        newCaret = start,
                        recordUndo = true,
                        allowUndoMerge = false,
                        ensureVisible = true,
                        notifyIme = true
                    )
                } else if (selectionCaret < buffer.length) {
                    val delEnd = if (isCtrl) stepWordRight(selectionCaret) else stepCharRight(selectionCaret)
                    applyBufferEdit(
                        start = selectionCaret,
                        end = delEnd,
                        replacement = "",
                        newAnchor = selectionCaret,
                        newCaret = selectionCaret,
                        recordUndo = true,
                        allowUndoMerge = !isCtrl,
                        ensureVisible = true,
                        notifyIme = true
                    )
                }
                return true
            }

            // 10. Enter
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (isReadOnlyMode) return false
                imeFinishComposingText()
                insertNewlineWithAutoIndentAt(selMin, selMax)
                return true
            }

            // 11. Tab
            KeyEvent.KEYCODE_TAB -> {
                if (isReadOnlyMode) return false
                imeFinishComposingText()
                insertSymbolAtSelection("    ", 4)
                return true
            }
        }

        // Printable Unicode character from physical/virtual KeyEvent
        val unicode = event.getUnicodeChar(meta)
        if (!isReadOnlyMode && !isCtrl && !isAlt && unicode != 0 && (unicode and android.view.KeyCharacterMap.COMBINING_ACCENT) == 0) {
            val str = String(Character.toChars(unicode))
            imeCommitText(str, 1)
            return true
        }

        return false
    }

    // =========================================================================
    // INTERNAL EDIT, CURSOR, & IME SYNC HELPERS
    // =========================================================================

    private fun insertNewlineWithAutoIndentAt(replaceStart: Int, replaceEnd: Int): Boolean {
        val indent = buffer.computeAutoIndent(replaceStart)
        val prevCh = buffer.charAt(replaceStart - 1)
        val nextCh = buffer.charAt(replaceEnd)
        if (replaceStart == replaceEnd &&
            ((prevCh == '{' && nextCh == '}') || (prevCh == '(' && nextCh == ')') || (prevCh == '[' && nextCh == ']'))
        ) {
            val baseIndent = if (indent.endsWith("    ")) indent.dropLast(4) else indent
            val insertion = "\n$indent\n$baseIndent"
            val targetCaret = replaceStart + 1 + indent.length
            applyBufferEdit(
                start = replaceStart,
                end = replaceEnd,
                replacement = insertion,
                newAnchor = targetCaret,
                newCaret = targetCaret,
                recordUndo = true,
                allowUndoMerge = false,
                ensureVisible = true,
                notifyIme = true
            )
            return true
        }

        val insertion = "\n$indent"
        val targetCaret = replaceStart + insertion.length
        applyBufferEdit(
            start = replaceStart,
            end = replaceEnd,
            replacement = insertion,
            newAnchor = targetCaret,
            newCaret = targetCaret,
            recordUndo = true,
            allowUndoMerge = false,
            ensureVisible = true,
            notifyIme = true
        )
        return true
    }

    private fun applyBufferEdit(
        start: Int,
        end: Int,
        replacement: String,
        newAnchor: Int,
        newCaret: Int,
        recordUndo: Boolean,
        allowUndoMerge: Boolean,
        ensureVisible: Boolean,
        notifyIme: Boolean,
        immediateViewModelFlush: Boolean = false
    ): DocumentEditResult {
        val anchorBefore = selectionAnchor
        val caretBefore = selectionCaret
        val editRes = buffer.replace(start, end, replacement)
        val clampedAnchor = newAnchor.coerceIn(0, buffer.length)
        val clampedCaret = newCaret.coerceIn(0, buffer.length)

        if (recordUndo && (editRes.deletedText.isNotEmpty() || editRes.insertedText.isNotEmpty())) {
            session.undoRedo.recordEdit(
                startOffset = editRes.startOffset,
                deletedText = editRes.deletedText,
                insertedText = editRes.insertedText,
                anchorBefore = anchorBefore,
                caretBefore = caretBefore,
                anchorAfter = clampedAnchor,
                caretAfter = clampedCaret,
                allowMerge = allowUndoMerge
            )
        }

        selectionAnchor = clampedAnchor
        selectionCaret = clampedCaret
        preferredCursorX = -1f

        syntaxHighlighter.invalidateFromLine(editRes.startLineChanged)
        if (editRes.linesDelta == 0) {
            wrapIndex.onSingleLineUpdated(editRes.startLineChanged, buffer, measureSegmentFn)
        } else {
            recalculateLayoutMetrics(rebuildWrap = true)
        }

        saveStateToSession()

        if (batchEditDepth > 0) {
            if (ensureVisible) pendingEnsureVisible = true
            if (notifyIme) pendingImeNotify = true
            pendingDocNotify = true
        } else {
            if (ensureVisible) ensureCaretVisible(animate = false)
            if (notifyIme) dispatchSelectionToIme()
            notifyLocalEditCommitted(immediateSync = immediateViewModelFlush)
            resetCursorBlink()
            invalidate()
        }

        return editRes
    }

    private fun setSelectionInternal(
        newAnchor: Int,
        newCaret: Int,
        ensureVisible: Boolean,
        notifyIme: Boolean,
        keepPreferredX: Boolean = false
    ) {
        val len = buffer.length
        selectionAnchor = newAnchor.coerceIn(0, len)
        selectionCaret = newCaret.coerceIn(0, len)
        if (!keepPreferredX) {
            preferredCursorX = -1f
        }
        saveStateToSession()

        if (batchEditDepth > 0) {
            if (ensureVisible) pendingEnsureVisible = true
            if (notifyIme) pendingImeNotify = true
        } else {
            if (ensureVisible) ensureCaretVisible(animate = false)
            if (notifyIme) dispatchSelectionToIme()
            syncControllerScrollAndDocState()
            resetCursorBlink()
            invalidate()
        }
    }

    private fun requestImeNotification() {
        if (batchEditDepth > 0) {
            pendingImeNotify = true
        } else {
            dispatchSelectionToIme()
        }
    }

    private fun dispatchSelectionToIme() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        val cStart = if (hasActiveComposing()) composingStart else -1
        val cEnd = if (hasActiveComposing()) composingEnd else -1
        // Always report normalized [selMin, selMax] so Gboard recognizes forward AND reverse selections!
        imm.updateSelection(this, selMin, selMax, cStart, cEnd)

        val req = extractedTextRequest
        if (req != null && imm.isActive(this)) {
            imm.updateExtractedText(this, req.token, buildExtractedTextSnapshot())
        }
        if (monitorCursorAnchor && imm.isActive(this)) {
            sendCursorAnchorInfoToIme()
        }
    }

    private fun restartImeForNewDocument() {
        extractedTextRequest = null
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        if (imm.isActive(this)) {
            imm.restartInput(this)
        }
    }

    private fun sendCursorAnchorInfoToIme() {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
        if (!imm.isActive(this)) return
        try {
            val builder = CursorAnchorInfo.Builder()
            builder.setSelectionRange(selectionAnchor, selectionCaret)
            if (hasActiveComposing()) {
                val cText = buffer.subSequence(composingStart, composingEnd)
                builder.setComposingText(composingStart, cText)
            }
            val caretCoords = computeOffsetCanvasCoords(selectionCaret)
            val matrix = Matrix()
            val loc = IntArray(2)
            getLocationOnScreen(loc)
            matrix.setTranslate(loc[0].toFloat(), loc[1].toFloat())
            builder.setMatrix(matrix)
            builder.setInsertionMarkerLocation(
                caretCoords.first,
                caretCoords.second,
                caretCoords.second + baselineOffsetPx,
                caretCoords.second + lineHeightPx,
                CursorAnchorInfo.FLAG_HAS_VISIBLE_REGION
            )
            imm.updateCursorAnchorInfo(this, builder.build())
        } catch (_: Exception) {
            // Ignore in headless unit tests if screen matrix unavailable
        }
    }

    private fun notifyLocalEditCommitted(immediateSync: Boolean) {
        session.hasPendingLocalSync = true
        val ctrl = controller
        if (ctrl != null) {
            ctrl.localEditCounter++
            ctrl.syncSnapshotFromSession(session)
        }
        syncScrollMetricsToController()
        if (immediateSync) {
            val fullText = buffer.getFullText()
            session.hasPendingLocalSync = false
            session.lastDispatchedContent = fullText
            onImmediateContentSync?.invoke(fullText)
        }
    }

    private fun syncControllerScrollAndDocState() {
        val ctrl = controller ?: return
        ctrl.syncSnapshotFromSession(session)
        syncScrollMetricsToController()
    }

    private fun syncScrollMetricsToController() {
        val ctrl = controller ?: return
        ctrl.scrollY = virtualScrollY
        ctrl.maxScrollY = computeMaxScrollY()
        ctrl.viewportHeight = height.coerceAtLeast(1)
        ctrl.scrollX = virtualScrollX
        ctrl.maxScrollX = computeMaxScrollX()
        ctrl.viewportWidth = (width - gutterWidthPx).roundToInt().coerceAtLeast(1)
    }

    private fun hasActiveComposing(): Boolean {
        return composingStart >= 0 && composingEnd > composingStart && composingEnd <= buffer.length
    }

    private fun isAutoPairAllowedBefore(nextCh: Char): Boolean {
        return nextCh == '\u0000' ||
            nextCh == '\n' ||
            nextCh == ' ' ||
            nextCh == '\t' ||
            nextCh == ')' ||
            nextCh == ']' ||
            nextCh == '}' ||
            nextCh == ',' ||
            nextCh == ';'
    }

    private fun stepCharLeft(offset: Int): Int {
        if (offset <= 0) return 0
        if (offset >= 2) {
            val low = buffer.charAt(offset - 1)
            val high = buffer.charAt(offset - 2)
            if (Character.isLowSurrogate(low) && Character.isHighSurrogate(high)) {
                return offset - 2
            }
        }
        return offset - 1
    }

    private fun stepCharRight(offset: Int): Int {
        val total = buffer.length
        if (offset >= total) return total
        if (offset + 1 < total) {
            val high = buffer.charAt(offset)
            val low = buffer.charAt(offset + 1)
            if (Character.isHighSurrogate(high) && Character.isLowSurrogate(low)) {
                return offset + 2
            }
        }
        return offset + 1
    }

    private fun stepWordLeft(offset: Int): Int {
        if (offset <= 0) return 0
        var p = offset - 1
        while (p > 0 && buffer.charAt(p).isWhitespace() && buffer.charAt(p) != '\n') p--
        if (buffer.charAt(p) == '\n') return p
        val isIdent = isWordChar(buffer.charAt(p))
        while (p > 0) {
            val prev = buffer.charAt(p - 1)
            if (prev == '\n') break
            if (isIdent && !isWordChar(prev)) break
            if (!isIdent && (isWordChar(prev) || prev.isWhitespace())) break
            p--
        }
        return p
    }

    private fun stepWordRight(offset: Int): Int {
        val total = buffer.length
        if (offset >= total) return total
        var p = offset
        if (buffer.charAt(p) == '\n') return p + 1
        val isIdent = isWordChar(buffer.charAt(p))
        while (p < total) {
            val c = buffer.charAt(p)
            if (c == '\n') break
            if (isIdent && !isWordChar(c)) break
            if (!isIdent && (isWordChar(c) || c.isWhitespace())) break
            p++
        }
        while (p < total && buffer.charAt(p).isWhitespace() && buffer.charAt(p) != '\n') p++
        return p
    }

    private fun findWordBoundsAt(offset: Int): Pair<Int, Int> {
        val total = buffer.length
        if (total == 0) return Pair(0, 0)
        val lc = buffer.getLineAndColumn(offset)
        val lineStr = buffer.getLine(lc.line)
        if (lineStr.isEmpty()) {
            val s = buffer.getLineStart(lc.line)
            return Pair(s, s)
        }
        val probeCol = when {
            lc.column < lineStr.length && isWordChar(lineStr[lc.column]) -> lc.column
            lc.column > 0 && isWordChar(lineStr[lc.column - 1]) -> lc.column - 1
            else -> lc.column.coerceIn(0, lineStr.length - 1)
        }
        val targetIsWord = isWordChar(lineStr[probeCol])
        var sCol = probeCol
        var eCol = probeCol + 1
        if (targetIsWord) {
            while (sCol > 0 && isWordChar(lineStr[sCol - 1])) sCol--
            while (eCol < lineStr.length && isWordChar(lineStr[eCol])) eCol++
        }
        val lineStart = buffer.getLineStart(lc.line)
        return Pair(lineStart + sCol, lineStart + eCol)
    }

    private fun isWordChar(c: Char): Boolean {
        return c.isLetterOrDigit() || c == '_' || c == '$'
    }

    private fun computeVerticalMoveOffset(currentOffset: Int, deltaVisualRows: Int): Int {
        val lc = buffer.getLineAndColumn(currentOffset)
        val lineStr = buffer.getLine(lc.line)
        val breaks = wrapIndex.getLineRowBreaks(lineStr, measureSegmentFn)
        var curSubRow = 0
        for (r in 0 until breaks.size - 1) {
            if (lc.column >= breaks[r] && lc.column <= breaks[r + 1]) {
                curSubRow = r
                if (lc.column < breaks[r + 1]) break
            }
        }
        val curVisualRow = wrapIndex.getFirstVisualRowForLine(lc.line, buffer) + curSubRow
        val curLocalX = measureTextWithTabs(lineStr, breaks[curSubRow], lc.column)
        if (preferredCursorX < 0f) {
            preferredCursorX = curLocalX
        }

        val totalVisualRows = wrapIndex.getTotalVisualRows(buffer)
        val nextVisualRow = curVisualRow + deltaVisualRows
        if (nextVisualRow < 0) return 0
        if (nextVisualRow >= totalVisualRows) return buffer.length

        val targetInfo = wrapIndex.getVisualRowInfo(nextVisualRow, buffer, measureSegmentFn)
        val targetLineStr = buffer.getLine(targetInfo.logicalLine)
        val targetCol = findColumnForAdvance(
            line = targetLineStr,
            startCol = targetInfo.colStart,
            endCol = targetInfo.colEnd,
            targetX = preferredCursorX
        )
        return buffer.getLineStart(targetInfo.logicalLine) + targetCol
    }

    // =========================================================================
    // VIEWPORT SCROLLING & ROCK-STEADY CARET VISIBILITY
    // =========================================================================

    private fun computeMaxScrollY(): Int {
        if (height <= 0) return 0
        val totalRows = wrapIndex.getTotalVisualRows(buffer)
        val contentHeight = topPaddingPx + totalRows * lineHeightPx + bottomExtraSpacePx
        return max(0, (contentHeight - height).roundToInt())
    }

    private fun computeMaxScrollX(): Int {
        if (isWordWrapEnabled || width <= 0) return 0
        val viewportCodeW = (width - gutterWidthPx).coerceAtLeast(1f)
        val maxContentW = codeStartPaddingPx + (buffer.maxLineCharLength + 8) * spaceWidthPx + codeEndPaddingPx
        return max(0, (maxContentW - viewportCodeW).roundToInt())
    }

    /**
     * Scrolls ONLY by the minimum delta needed to bring [selectionCaret] into view.
     * If the caret is already visible inside the safe margins, virtualScrollY changes by 0px!
     */
    fun ensureCaretVisible(animate: Boolean = false) {
        if (height <= 0 || width <= 0) return
        val (caretRow, caretLocalX) = computeCaretVisualRowAndLocalX(selectionCaret)
        val rowTopDocY = topPaddingPx + caretRow * lineHeightPx
        val rowBottomDocY = rowTopDocY + lineHeightPx

        val topSafeMargin = min(lineHeightPx * 0.8f, height * 0.2f)
        val bottomSafeMargin = min(lineHeightPx * 1.4f, height * 0.28f)

        var newScrollY = virtualScrollY
        val visibleTop = virtualScrollY + topSafeMargin
        val visibleBottom = virtualScrollY + height - bottomSafeMargin

        if (rowBottomDocY > visibleBottom) {
            newScrollY = (rowBottomDocY - height + bottomSafeMargin).roundToInt()
        } else if (rowTopDocY < visibleTop) {
            newScrollY = (rowTopDocY - topSafeMargin).roundToInt()
        }
        newScrollY = newScrollY.coerceIn(0, computeMaxScrollY())

        var newScrollX = virtualScrollX
        if (!isWordWrapEnabled) {
            val codeViewportW = (width - gutterWidthPx).coerceAtLeast(1f)
            val caretDocX = codeStartPaddingPx + caretLocalX
            val leftMarginX = min(spaceWidthPx * 3f, codeViewportW * 0.2f)
            val rightMarginX = min(spaceWidthPx * 6f, codeViewportW * 0.25f)

            if (caretDocX > virtualScrollX + codeViewportW - rightMarginX) {
                newScrollX = (caretDocX - codeViewportW + rightMarginX).roundToInt()
            } else if (caretDocX < virtualScrollX + leftMarginX) {
                newScrollX = (caretDocX - leftMarginX).roundToInt()
            }
            newScrollX = newScrollX.coerceIn(0, computeMaxScrollX())
        } else {
            newScrollX = 0
        }

        if (newScrollY != virtualScrollY || newScrollX != virtualScrollX) {
            if (animate) {
                scroller.startScroll(
                    virtualScrollX,
                    virtualScrollY,
                    newScrollX - virtualScrollX,
                    newScrollY - virtualScrollY,
                    180
                )
                postInvalidateOnAnimation()
            } else {
                virtualScrollX = newScrollX
                virtualScrollY = newScrollY
                saveStateToSession()
                syncScrollMetricsToController()
                invalidate()
            }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val widthChanged = (w != oldw)
        recalculateLayoutMetrics(rebuildWrap = widthChanged && isWordWrapEnabled)

        if (h > 0 && pendingJumpTargetLine >= 0) {
            val targetIdx = pendingJumpTargetLine.coerceIn(0, buffer.lineCount - 1)
            pendingJumpTargetLine = -1
            val vRow = wrapIndex.getFirstVisualRowForLine(targetIdx, buffer)
            virtualScrollY = (topPaddingPx + vRow * lineHeightPx - h * 0.25f)
                .roundToInt()
                .coerceIn(0, computeMaxScrollY())
            if (!isWordWrapEnabled) virtualScrollX = 0
            saveStateToSession()
            syncScrollMetricsToController()
            return
        }

        // If soft keyboard (Gboard) just opened and shrank the viewport height while focused,
        // keep the active working line visible right above Gboard without jumping anywhere else!
        if (hasFocus() && oldh > 0 && h > 0 && h < oldh) {
            val (caretRow, _) = computeCaretVisualRowAndLocalX(selectionCaret)
            val rowTopDocY = topPaddingPx + caretRow * lineHeightPx
            val rowBottomDocY = rowTopDocY + lineHeightPx
            val wasVisibleBeforeIme = rowTopDocY >= virtualScrollY - lineHeightPx &&
                rowTopDocY <= virtualScrollY + oldh
            val bottomMargin = min(lineHeightPx * 1.2f, h * 0.25f)
            if (wasVisibleBeforeIme && rowBottomDocY > virtualScrollY + h - bottomMargin) {
                virtualScrollY = (rowBottomDocY - h + bottomMargin)
                    .roundToInt()
                    .coerceIn(0, computeMaxScrollY())
            } else {
                virtualScrollY = virtualScrollY.coerceIn(0, computeMaxScrollY())
            }
        } else {
            virtualScrollY = virtualScrollY.coerceIn(0, computeMaxScrollY())
        }
        virtualScrollX = virtualScrollX.coerceIn(0, computeMaxScrollX())
        saveStateToSession()
        syncScrollMetricsToController()
    }

    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            val nextX = if (isWordWrapEnabled) 0 else scroller.currX.coerceIn(0, computeMaxScrollX())
            val nextY = scroller.currY.coerceIn(0, computeMaxScrollY())
            if (nextX != virtualScrollX || nextY != virtualScrollY) {
                virtualScrollX = nextX
                virtualScrollY = nextY
                saveStateToSession()
                syncScrollMetricsToController()
            }
            postInvalidateOnAnimation()
        }
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        removeCallbacks(blinkRunnable)
        if (gainFocus && !isReadOnlyMode) {
            cursorBlinkVisible = true
            postDelayed(blinkRunnable, 530L)
        } else {
            stopSelectionActionMode()
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(blinkRunnable)
        stopSelectionActionMode()
        velocityTracker?.recycle()
        velocityTracker = null
        super.onDetachedFromWindow()
    }

    private fun resetCursorBlink() {
        cursorBlinkVisible = true
        removeCallbacks(blinkRunnable)
        if (isAttachedToWindow && hasFocus() && !isReadOnlyMode) {
            postDelayed(blinkRunnable, 530L)
        }
    }

    fun requestEditorFocusAndShowKeyboard() {
        if (isReadOnlyMode) return
        if (!hasFocus()) {
            requestFocus()
        }
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(this, 0)
        resetCursorBlink()
    }

    // =========================================================================
    // TOUCH GESTURES, SELECTION HANDLES, & FLOATING ACTION MODE
    // =========================================================================

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (velocityTracker == null) {
            velocityTracker = VelocityTracker.obtain()
        }
        velocityTracker?.addMovement(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downTouchX = event.x
                downTouchY = event.y
                lastTouchX = event.x
                lastTouchY = event.y
                isTouchPanning = false
                isLongPressDragSelecting = false
                activeDragHandle = hitTestSelectionHandle(event.x, event.y)
                if (activeDragHandle != HANDLE_NONE) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    stopSelectionActionMode()
                    return true
                }
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                activeDragHandle = HANDLE_NONE
                isLongPressDragSelecting = false
                stopSelectionActionMode()
            }

            MotionEvent.ACTION_MOVE -> {
                if (activeDragHandle != HANDLE_NONE) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    val targetY = (event.y - lineHeightPx * 0.75f)
                    val hitOffset = hitTestCharacterOffset(event.x, targetY)
                    when (activeDragHandle) {
                        HANDLE_START -> {
                            val currentMax = selMax
                            val currentMin = selMin
                            if (selectionAnchor <= selectionCaret) {
                                setSelectionInternal(min(hitOffset, currentMax), currentMax, ensureVisible = true, notifyIme = true)
                            } else {
                                setSelectionInternal(currentMax, min(hitOffset, currentMax), ensureVisible = true, notifyIme = true)
                            }
                            if (currentMin != selMin) performEdgeAutoScroll(event.x, event.y)
                        }
                        HANDLE_END -> {
                            val currentMin = selMin
                            val currentMax = selMax
                            if (selectionAnchor <= selectionCaret) {
                                setSelectionInternal(currentMin, max(hitOffset, currentMin), ensureVisible = true, notifyIme = true)
                            } else {
                                setSelectionInternal(max(hitOffset, currentMin), currentMin, ensureVisible = true, notifyIme = true)
                            }
                            if (currentMax != selMax) performEdgeAutoScroll(event.x, event.y)
                        }
                        HANDLE_CARET -> {
                            showCaretHandleUntilMs = System.currentTimeMillis() + 3500L
                            setSelectionInternal(hitOffset, hitOffset, ensureVisible = true, notifyIme = true)
                            performEdgeAutoScroll(event.x, event.y)
                        }
                    }
                    return true
                }

                if (isLongPressDragSelecting) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    val hitOffset = hitTestCharacterOffset(event.x, event.y - lineHeightPx * 0.35f)
                    setSelectionInternal(longPressAnchorOffset, hitOffset, ensureVisible = true, notifyIme = true)
                    return true
                }

                scaleDetector.onTouchEvent(event)
                if (scaleDetector.isInProgress || event.pointerCount > 1) {
                    isTouchPanning = false
                    return true
                }

                val dxTotal = event.x - downTouchX
                val dyTotal = event.y - downTouchY
                if (!isTouchPanning && hypot(dxTotal, dyTotal) > touchSlop) {
                    isTouchPanning = true
                    stopSelectionActionMode()
                    parent?.requestDisallowInterceptTouchEvent(true)
                }

                if (isTouchPanning) {
                    val dx = (lastTouchX - event.x).roundToInt()
                    val dy = (lastTouchY - event.y).roundToInt()
                    lastTouchX = event.x
                    lastTouchY = event.y

                    val nextY = (virtualScrollY + dy).coerceIn(0, computeMaxScrollY())
                    val nextX = if (isWordWrapEnabled) 0 else (virtualScrollX + dx).coerceIn(0, computeMaxScrollX())
                    if (nextY != virtualScrollY || nextX != virtualScrollX) {
                        virtualScrollX = nextX
                        virtualScrollY = nextY
                        saveStateToSession()
                        syncScrollMetricsToController()
                        invalidate()
                    }
                    gestureDetector.onTouchEvent(event)
                    return true
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (activeDragHandle != HANDLE_NONE) {
                    val wasSel = selectionAnchor != selectionCaret
                    activeDragHandle = HANDLE_NONE
                    if (wasSel && event.actionMasked == MotionEvent.ACTION_UP) {
                        showSelectionActionMode()
                    }
                    invalidate()
                    return true
                }
                if (isLongPressDragSelecting) {
                    isLongPressDragSelecting = false
                    if (selectionAnchor != selectionCaret) {
                        showSelectionActionMode()
                    }
                    invalidate()
                    return true
                }
                isTouchPanning = false
            }
        }

        if (event.pointerCount > 1) {
            scaleDetector.onTouchEvent(event)
        }
        gestureDetector.onTouchEvent(event)
        return true
    }

    private fun performEdgeAutoScroll(touchX: Float, touchY: Float) {
        val edgeZone = 36f * density
        val step = (lineHeightPx * 0.45f).roundToInt().coerceAtLeast(4)
        var dy = 0
        if (touchY < edgeZone) dy = -step
        else if (touchY > height - edgeZone) dy = step

        var dx = 0
        if (!isWordWrapEnabled) {
            if (touchX < gutterWidthPx + edgeZone) dx = -step
            else if (touchX > width - edgeZone) dx = step
        }
        if (dy != 0 || dx != 0) {
            virtualScrollY = (virtualScrollY + dy).coerceIn(0, computeMaxScrollY())
            virtualScrollX = (virtualScrollX + dx).coerceIn(0, computeMaxScrollX())
            syncScrollMetricsToController()
            invalidate()
        }
    }

    private fun hitTestSelectionHandle(x: Float, y: Float): Int {
        val hitRadius = 26f * density
        if (selectionAnchor != selectionCaret) {
            val dLeft = hypot(x - leftHandleCenter.centerX(), y - leftHandleCenter.centerY())
            val dRight = hypot(x - rightHandleCenter.centerX(), y - rightHandleCenter.centerY())
            if (dLeft <= hitRadius && dLeft <= dRight) return HANDLE_START
            if (dRight <= hitRadius) return HANDLE_END
        } else if (System.currentTimeMillis() < showCaretHandleUntilMs) {
            val dCaret = hypot(x - caretHandleCenter.centerX(), y - caretHandleCenter.centerY())
            if (dCaret <= hitRadius) return HANDLE_CARET
        }
        return HANDLE_NONE
    }

    private fun showSelectionActionMode() {
        if (isReadOnlyMode || !isAttachedToWindow) return
        stopSelectionActionMode()
        val callback = object : ActionMode.Callback2() {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                if (selectionAnchor != selectionCaret) {
                    menu.add(0, android.R.id.cut, 0, "Cut")
                        .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
                    menu.add(0, android.R.id.copy, 1, "Copy")
                        .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
                }
                menu.add(0, android.R.id.paste, 2, "Paste")
                    .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS)
                menu.add(0, android.R.id.selectAll, 3, "Select all")
                    .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                val handled = performEditorContextMenuAction(item.itemId)
                if (item.itemId != android.R.id.selectAll) {
                    mode.finish()
                }
                return handled
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                if (selectionActionMode === mode) {
                    selectionActionMode = null
                }
            }

            override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
                val minCoords = computeOffsetCanvasCoords(selMin)
                val maxCoords = computeOffsetCanvasCoords(selMax)
                val top = min(minCoords.second, maxCoords.second).roundToInt().coerceIn(0, height)
                val bottom = (max(minCoords.second, maxCoords.second) + lineHeightPx).roundToInt().coerceIn(top + 1, max(1, height))
                val left = min(minCoords.first, maxCoords.first).roundToInt().coerceIn(0, width)
                val right = max(minCoords.first + spaceWidthPx, maxCoords.first + spaceWidthPx).roundToInt().coerceIn(left + 1, max(1, width))
                outRect.set(left, top, right, bottom)
            }
        }
        selectionActionMode = startActionMode(callback, ActionMode.TYPE_FLOATING)
    }

    private fun stopSelectionActionMode() {
        selectionActionMode?.finish()
        selectionActionMode = null
    }

    // =========================================================================
    // COORDINATE MAPPING & TEXT MEASUREMENT WITH EXACT TAB SUPPORT
    // =========================================================================

    private fun measureTextWithTabs(line: String, startCol: Int, endCol: Int): Float {
        val s = startCol.coerceIn(0, line.length)
        val e = endCol.coerceIn(s, line.length)
        if (s == e) return 0f

        val tabIdx = line.indexOf('\t', s)
        if (tabIdx == -1 || tabIdx >= e) {
            return textPaint.measureText(line, s, e)
        }

        val tabWidth = spaceWidthPx * TAB_SPACES
        var widthAcc = 0f
        var segStart = s
        var i = s
        while (i < e) {
            if (line[i] == '\t') {
                if (i > segStart) {
                    widthAcc += textPaint.measureText(line, segStart, i)
                }
                widthAcc += tabWidth
                segStart = i + 1
            }
            i++
        }
        if (e > segStart) {
            widthAcc += textPaint.measureText(line, segStart, e)
        }
        return widthAcc
    }

    private fun findColumnForAdvance(line: String, startCol: Int, endCol: Int, targetX: Float): Int {
        val s = startCol.coerceIn(0, line.length)
        val e = endCol.coerceIn(s, line.length)
        if (targetX <= 0f || s == e) return s

        val fullW = measureTextWithTabs(line, s, e)
        if (targetX >= fullW) return e

        // Binary search character column closest to targetX
        var low = s
        var high = e
        var best = s
        while (low <= high) {
            val mid = (low + high) ushr 1
            val w = measureTextWithTabs(line, s, mid)
            if (w <= targetX) {
                best = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        // Snap to next character if past half its glyph width
        if (best < e) {
            var nextCharEnd = best + 1
            if (nextCharEnd < e && Character.isHighSurrogate(line[best]) && Character.isLowSurrogate(line[nextCharEnd])) {
                nextCharEnd++
            }
            val wBest = measureTextWithTabs(line, s, best)
            val wNext = measureTextWithTabs(line, s, nextCharEnd)
            if (targetX - wBest > (wNext - wBest) * 0.5f) {
                best = nextCharEnd
            }
        }
        if (best in (s + 1) until e && Character.isLowSurrogate(line[best]) && Character.isHighSurrogate(line[best - 1])) {
            best--
        }
        return best
    }

    private fun hitTestCharacterOffset(canvasX: Float, canvasY: Float): Int {
        val docY = (virtualScrollY + canvasY - topPaddingPx).coerceAtLeast(0f)
        val totalRows = wrapIndex.getTotalVisualRows(buffer)
        val visualRow = (docY / lineHeightPx).toInt().coerceIn(0, (totalRows - 1).coerceAtLeast(0))
        val rowInfo = wrapIndex.getVisualRowInfo(visualRow, buffer, measureSegmentFn)
        val lineStr = buffer.getLine(rowInfo.logicalLine)

        val localCodeX = (canvasX - gutterWidthPx - codeStartPaddingPx + (if (isWordWrapEnabled) 0 else virtualScrollX))
            .coerceAtLeast(0f)
        val col = findColumnForAdvance(lineStr, rowInfo.colStart, rowInfo.colEnd, localCodeX)
        return buffer.getLineStart(rowInfo.logicalLine) + col
    }

    private fun computeCaretVisualRowAndLocalX(offset: Int): Pair<Int, Float> {
        val lc = buffer.getLineAndColumn(offset)
        val lineStr = buffer.getLine(lc.line)
        val firstRow = wrapIndex.getFirstVisualRowForLine(lc.line, buffer)
        if (!isWordWrapEnabled) {
            return Pair(firstRow, measureTextWithTabs(lineStr, 0, lc.column))
        }
        val breaks = wrapIndex.getLineRowBreaks(lineStr, measureSegmentFn)
        var subRow = 0
        for (r in 0 until breaks.size - 1) {
            if (lc.column >= breaks[r] && lc.column <= breaks[r + 1]) {
                subRow = r
                if (lc.column < breaks[r + 1]) break
            }
        }
        val localX = measureTextWithTabs(lineStr, breaks[subRow], lc.column)
        return Pair(firstRow + subRow, localX)
    }

    private fun computeOffsetCanvasCoords(offset: Int): Pair<Float, Float> {
        val (vRow, localX) = computeCaretVisualRowAndLocalX(offset)
        val x = gutterWidthPx + codeStartPaddingPx + localX - (if (isWordWrapEnabled) 0 else virtualScrollX)
        val y = topPaddingPx + vRow * lineHeightPx - virtualScrollY
        return Pair(x, y)
    }

    // =========================================================================
    // VIEWPORT-ONLY GPU CANVAS RENDERING (60/120 FPS ON 100,000+ LINES)
    // =========================================================================

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. Background
        canvas.drawColor(bgColorArgb)
        if (width <= 0 || height <= 0) return

        val totalVisualRows = wrapIndex.getTotalVisualRows(buffer)
        val firstVisibleRow = ((virtualScrollY - topPaddingPx) / lineHeightPx).toInt()
            .minus(1)
            .coerceIn(0, (totalVisualRows - 1).coerceAtLeast(0))
        val lastVisibleRow = ((virtualScrollY + height - topPaddingPx) / lineHeightPx).toInt()
            .plus(2)
            .coerceIn(firstVisibleRow, (totalVisualRows - 1).coerceAtLeast(0))

        val activeCursorLC = buffer.getLineAndColumn(selectionCaret)
        val codeOriginX = gutterWidthPx + codeStartPaddingPx - (if (isWordWrapEnabled) 0 else virtualScrollX)

        // Compute bracket pair near cursor (bounded O(1) scan)
        val bracketPair = if (isSyntaxHighlightingEnabled &&
            language != SupportedLanguage.PLAIN_TEXT &&
            language != SupportedLanguage.MARKDOWN
        ) {
            if (cachedBracketVersion != buffer.version || cachedBracketCursor != selectionCaret) {
                cachedBracketVersion = buffer.version
                cachedBracketCursor = selectionCaret
                cachedBracketMatch = buffer.findMatchingBracket(selectionCaret)
            }
            cachedBracketMatch
        } else {
            null
        }

        leftHandleCenter.setEmpty()
        rightHandleCenter.setEmpty()
        caretHandleCenter.setEmpty()

        // 2. Clip & render code area [gutterWidthPx .. width]
        canvas.save()
        canvas.clipRect(gutterWidthPx, 0f, width.toFloat(), height.toFloat())

        val minSel = selMin
        val maxSel = selMax
        val isSelected = selectionAnchor != selectionCaret

        for (vRow in firstVisibleRow..lastVisibleRow) {
            val rowTopY = topPaddingPx + vRow * lineHeightPx - virtualScrollY
            val rowBottomY = rowTopY + lineHeightPx
            val baselineY = rowTopY + baselineOffsetPx

            val rowInfo = wrapIndex.getVisualRowInfo(vRow, buffer, measureSegmentFn)
            val logicalLine = rowInfo.logicalLine
            val lineStr = buffer.getLine(logicalLine)
            val lineDocStart = buffer.getLineStart(logicalLine)
            val segStartOffset = lineDocStart + rowInfo.colStart
            val segEndOffset = lineDocStart + rowInfo.colEnd

            // 2a. Active cursor line subtle highlight (when collapsed)
            if (!isSelected && hasFocus() && logicalLine == activeCursorLC.line) {
                fillPaint.color = activeLineBgColorArgb
                canvas.drawRect(gutterWidthPx, rowTopY, width.toFloat(), rowBottomY, fillPaint)
            }

            // 2b. VSCode-style indentation guides on leading spaces (works even on 100,000+ line files!)
            if (rowInfo.subRow == 0 && language != SupportedLanguage.PLAIN_TEXT && language != SupportedLanguage.MARKDOWN) {
                drawLineIndentGuides(canvas, lineStr, codeOriginX, rowTopY, rowBottomY, logicalLine == activeCursorLC.line)
            }

            // 2c. Selection highlight on this visual row
            if (isSelected) {
                val lineDocEndWithNewline = lineDocStart + lineStr.length + (if (logicalLine < buffer.lineCount - 1) 1 else 0)
                val rowMaxOffset = if (rowInfo.subRow == rowInfo.subRowCount - 1) lineDocEndWithNewline else segEndOffset
                val overlapStart = max(minSel, segStartOffset)
                val overlapEnd = min(maxSel, rowMaxOffset)
                if (overlapStart < overlapEnd) {
                    val hlStartCol = (overlapStart - lineDocStart).coerceIn(rowInfo.colStart, rowInfo.colEnd)
                    val hlEndCol = (overlapEnd - lineDocStart).coerceIn(rowInfo.colStart, rowInfo.colEnd)
                    val x1 = codeOriginX + measureTextWithTabs(lineStr, rowInfo.colStart, hlStartCol)
                    var x2 = codeOriginX + measureTextWithTabs(lineStr, rowInfo.colStart, hlEndCol)
                    if (overlapEnd > lineDocStart + lineStr.length) {
                        x2 += spaceWidthPx * 0.85f // Highlight trailing newline
                    }
                    fillPaint.color = selectionBgColorArgb
                    canvas.drawRect(x1, rowTopY, max(x1 + 3f, x2), rowBottomY, fillPaint)
                }
            }

            // 2d. Search Match Highlights on this visual row (binary search into searchMatchOffsets!)
            if (searchMatchLength > 0 && searchMatchOffsets.isNotEmpty()) {
                drawRowSearchHighlights(
                    canvas = canvas,
                    lineStr = lineStr,
                    lineDocStart = lineDocStart,
                    colStart = rowInfo.colStart,
                    colEnd = rowInfo.colEnd,
                    codeOriginX = codeOriginX,
                    rowTopY = rowTopY,
                    rowBottomY = rowBottomY
                )
            }

            // 2e. Matching Bracket Highlight
            if (bracketPair != null) {
                drawBracketBoxIfOnRow(canvas, bracketPair.first, lineStr, lineDocStart, rowInfo.colStart, rowInfo.colEnd, codeOriginX, rowTopY, rowBottomY)
                drawBracketBoxIfOnRow(canvas, bracketPair.second, lineStr, lineDocStart, rowInfo.colStart, rowInfo.colEnd, codeOriginX, rowTopY, rowBottomY)
            }

            // 2f. Syntax-Highlighted Text Glyphs
            if (lineStr.isNotEmpty() && rowInfo.colStart < rowInfo.colEnd) {
                if (isSyntaxHighlightingEnabled &&
                    language != SupportedLanguage.PLAIN_TEXT &&
                    language != SupportedLanguage.MARKDOWN
                ) {
                    val runs = syntaxHighlighter.getLineTokens(buffer, logicalLine, textColorArgb)
                    drawTokenizedSubRow(
                        canvas = canvas,
                        lineStr = lineStr,
                        runs = runs,
                        subColStart = rowInfo.colStart,
                        subColEnd = rowInfo.colEnd,
                        startX = codeOriginX,
                        baselineY = baselineY
                    )
                } else {
                    textPaint.color = textColorArgb
                    textPaint.typeface = baseTypeface
                    drawPlainSubRowWithTabs(
                        canvas = canvas,
                        lineStr = lineStr,
                        subColStart = rowInfo.colStart,
                        subColEnd = rowInfo.colEnd,
                        startX = codeOriginX,
                        baselineY = baselineY,
                        paint = textPaint
                    )
                }
            }

            // 2g. Gboard Composing Text Underline
            if (hasActiveComposing()) {
                val cOverlapStart = max(composingStart, segStartOffset)
                val cOverlapEnd = min(composingEnd, segEndOffset)
                if (cOverlapStart < cOverlapEnd) {
                    val csCol = (cOverlapStart - lineDocStart).coerceIn(rowInfo.colStart, rowInfo.colEnd)
                    val ceCol = (cOverlapEnd - lineDocStart).coerceIn(rowInfo.colStart, rowInfo.colEnd)
                    val ux1 = codeOriginX + measureTextWithTabs(lineStr, rowInfo.colStart, csCol)
                    val ux2 = codeOriginX + measureTextWithTabs(lineStr, rowInfo.colStart, ceCol)
                    strokePaint.color = accentColorArgb
                    strokePaint.strokeWidth = 1.4f * density
                    val uy = baselineY + 2.5f * density
                    canvas.drawLine(ux1, uy, ux2, uy, strokePaint)
                }
            }

            // 2h. Soft-Wrap Indicators ("⤡")
            if (isWordWrapEnabled && rowInfo.subRowCount > 1) {
                if (rowInfo.subRow < rowInfo.subRowCount - 1) {
                    val endX = codeOriginX + measureTextWithTabs(lineStr, rowInfo.colStart, rowInfo.colEnd) + 3f * density
                    canvas.drawText("⤡", endX, baselineY, wrapSymbolPaint)
                }
                if (rowInfo.subRow > 0) {
                    val startIndicatorX = (gutterWidthPx + 2f * density)
                    canvas.drawText("⤡", startIndicatorX, baselineY, wrapSymbolPaint)
                }
            }
        }

        // 2i. Active Blinking Cursor Caret & Draggable Selection Handles
        if (!isReadOnlyMode) {
            if (!isSelected && hasFocus() && cursorBlinkVisible) {
                val (caretX, caretTopY) = computeOffsetCanvasCoords(selectionCaret)
                fillPaint.color = accentColorArgb
                val cw = max(2f * density, 2f)
                canvas.drawRoundRect(
                    caretX - cw * 0.5f,
                    caretTopY + 1.5f * density,
                    caretX + cw * 0.5f,
                    caretTopY + lineHeightPx - 1.5f * density,
                    cw,
                    cw,
                    fillPaint
                )
            }
        }
        canvas.restore()

        // 3. Draw Pinned Line Numbers Gutter [0 .. gutterWidthPx]
        if (showLineNumbers && gutterWidthPx > 0f) {
            fillPaint.color = gutterBgColorArgb
            canvas.drawRect(0f, 0f, gutterWidthPx, height.toFloat(), fillPaint)

            strokePaint.color = gutterDividerColorArgb
            strokePaint.strokeWidth = 1f * density
            canvas.drawLine(gutterWidthPx, 0f, gutterWidthPx, height.toFloat(), strokePaint)

            val numRightX = gutterWidthPx - 7f * density
            for (vRow in firstVisibleRow..lastVisibleRow) {
                val rowInfo = wrapIndex.getVisualRowInfo(vRow, buffer, measureSegmentFn)
                if (rowInfo.subRow == 0) {
                    val oneBased = rowInfo.logicalLine + 1
                    val numStr = if (oneBased in NUMBER_STRINGS.indices) NUMBER_STRINGS[oneBased] else oneBased.toString()
                    val rowTopY = topPaddingPx + vRow * lineHeightPx - virtualScrollY
                    val baselineY = rowTopY + baselineOffsetPx
                    val paintToUse = if (rowInfo.logicalLine == activeCursorLC.line) gutterActivePaint else gutterPaint
                    canvas.drawText(numStr, numRightX, baselineY, paintToUse)
                }
            }
        }

        // 4. Draw Selection Teardrop Handles (above gutter/text clip so handles never clip awkwardly)
        if (!isReadOnlyMode && hasFocus()) {
            if (isSelected) {
                val (lx, ly) = computeOffsetCanvasCoords(minSel)
                val (rx, ry) = computeOffsetCanvasCoords(maxSel)
                if (lx >= gutterWidthPx - 8f * density && ly in -lineHeightPx..height.toFloat()) {
                    drawSelectionHandle(canvas, lx, ly + lineHeightPx, isLeftHandle = true, outBounds = leftHandleCenter)
                }
                if (rx >= gutterWidthPx - 8f * density && ry in -lineHeightPx..height.toFloat()) {
                    drawSelectionHandle(canvas, rx, ry + lineHeightPx, isLeftHandle = false, outBounds = rightHandleCenter)
                }
            } else if (System.currentTimeMillis() < showCaretHandleUntilMs) {
                val (cx, cy) = computeOffsetCanvasCoords(selectionCaret)
                if (cx >= gutterWidthPx && cy in -lineHeightPx..height.toFloat()) {
                    drawCaretTeardropHandle(canvas, cx, cy + lineHeightPx, caretHandleCenter)
                }
            }
        }
    }

    private fun drawLineIndentGuides(
        canvas: Canvas,
        lineStr: String,
        codeOriginX: Float,
        rowTopY: Float,
        rowBottomY: Float,
        isActiveLine: Boolean
    ) {
        if (lineStr.isEmpty()) return
        var spaces = 0
        var i = 0
        while (i < lineStr.length) {
            val c = lineStr[i]
            if (c == ' ') spaces++
            else if (c == '\t') spaces += TAB_SPACES
            else break
            i++
        }
        val indentStep = 4
        val guideCount = (spaces - 1) / indentStep
        if (guideCount <= 0) return

        strokePaint.color = if (isActiveLine) {
            (accentColorArgb and 0x00FFFFFF) or 0x45000000
        } else {
            indentGuideColorArgb
        }
        strokePaint.strokeWidth = 1f * density
        for (g in 1..min(guideCount, 16)) {
            val gx = codeOriginX + g * indentStep * spaceWidthPx
            if (gx >= gutterWidthPx) {
                canvas.drawLine(gx, rowTopY, gx, rowBottomY, strokePaint)
            }
        }
    }

    private fun drawRowSearchHighlights(
        canvas: Canvas,
        lineStr: String,
        lineDocStart: Int,
        colStart: Int,
        colEnd: Int,
        codeOriginX: Float,
        rowTopY: Float,
        rowBottomY: Float
    ) {
        val segDocStart = lineDocStart + colStart
        val segDocEnd = lineDocStart + colEnd
        val matches = searchMatchOffsets
        val qLen = searchMatchLength

        // Binary search first match whose end > segDocStart
        var low = 0
        var high = matches.size - 1
        var firstIdx = matches.size
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (matches[mid] + qLen > segDocStart) {
                firstIdx = mid
                high = mid - 1
            } else {
                low = mid + 1
            }
        }

        var idx = firstIdx
        while (idx < matches.size) {
            val mStart = matches[idx]
            if (mStart >= segDocEnd) break
            val mEnd = mStart + qLen
            val ovStart = max(mStart, segDocStart)
            val ovEnd = min(mEnd, segDocEnd)
            if (ovStart < ovEnd) {
                val c1 = (ovStart - lineDocStart).coerceIn(colStart, colEnd)
                val c2 = (ovEnd - lineDocStart).coerceIn(colStart, colEnd)
                val x1 = codeOriginX + measureTextWithTabs(lineStr, colStart, c1)
                val x2 = codeOriginX + measureTextWithTabs(lineStr, colStart, c2)
                val isCurrent = (idx == activeSearchMatchIndex)
                fillPaint.color = if (isCurrent) AndroidColor.parseColor("#FFFFD54F") else AndroidColor.parseColor("#66FFD54F")
                canvas.drawRect(x1, rowTopY, max(x1 + 4f, x2), rowBottomY, fillPaint)
                if (isCurrent) {
                    strokePaint.color = accentColorArgb
                    strokePaint.strokeWidth = 1.6f * density
                    canvas.drawRect(x1, rowTopY, max(x1 + 4f, x2), rowBottomY, strokePaint)
                }
            }
            idx++
        }
    }

    private fun drawBracketBoxIfOnRow(
        canvas: Canvas,
        bracketOffset: Int,
        lineStr: String,
        lineDocStart: Int,
        colStart: Int,
        colEnd: Int,
        codeOriginX: Float,
        rowTopY: Float,
        rowBottomY: Float
    ) {
        val segStart = lineDocStart + colStart
        val segEnd = lineDocStart + colEnd
        if (bracketOffset !in segStart until segEnd) return
        val col = bracketOffset - lineDocStart
        if (col !in colStart until colEnd) return

        val x1 = codeOriginX + measureTextWithTabs(lineStr, colStart, col)
        val x2 = codeOriginX + measureTextWithTabs(lineStr, colStart, col + 1)
        fillPaint.color = bracketBgColorArgb
        canvas.drawRect(x1, rowTopY + 1f, x2, rowBottomY - 1f, fillPaint)
        strokePaint.color = bracketBorderColorArgb
        strokePaint.strokeWidth = 1.3f * density
        canvas.drawRect(x1, rowTopY + 1f, x2, rowBottomY - 1f, strokePaint)
    }

    private fun drawTokenizedSubRow(
        canvas: Canvas,
        lineStr: String,
        runs: List<LineTokenRun>,
        subColStart: Int,
        subColEnd: Int,
        startX: Float,
        baselineY: Float
    ) {
        var curX = startX
        for (i in runs.indices) {
            val run = runs[i]
            if (run.endCol <= subColStart) continue
            if (run.startCol >= subColEnd) break
            val drawStart = max(run.startCol, subColStart)
            val drawEnd = min(run.endCol, subColEnd)
            if (drawStart >= drawEnd) continue

            textPaint.color = run.colorArgb
            textPaint.typeface = when {
                run.isBold -> boldTypeface
                run.isItalic -> italicTypeface
                else -> baseTypeface
            }
            curX = drawPlainSubRowWithTabs(canvas, lineStr, drawStart, drawEnd, curX, baselineY, textPaint)
        }
    }

    private fun drawPlainSubRowWithTabs(
        canvas: Canvas,
        lineStr: String,
        subColStart: Int,
        subColEnd: Int,
        startX: Float,
        baselineY: Float,
        paint: Paint
    ): Float {
        var curX = startX
        val tabIdx = lineStr.indexOf('\t', subColStart)
        if (tabIdx == -1 || tabIdx >= subColEnd) {
            if (curX < width && curX + (subColEnd - subColStart) * spaceWidthPx * 2.5f > gutterWidthPx - 50f) {
                canvas.drawText(lineStr, subColStart, subColEnd, curX, baselineY, paint)
            }
            return curX + paint.measureText(lineStr, subColStart, subColEnd)
        }

        val tabWidth = spaceWidthPx * TAB_SPACES
        var segStart = subColStart
        var i = subColStart
        while (i < subColEnd) {
            if (lineStr[i] == '\t') {
                if (i > segStart) {
                    canvas.drawText(lineStr, segStart, i, curX, baselineY, paint)
                    curX += paint.measureText(lineStr, segStart, i)
                }
                curX += tabWidth
                segStart = i + 1
            }
            i++
        }
        if (subColEnd > segStart) {
            canvas.drawText(lineStr, segStart, subColEnd, curX, baselineY, paint)
            curX += paint.measureText(lineStr, segStart, subColEnd)
        }
        return curX
    }

    private fun drawSelectionHandle(
        canvas: Canvas,
        tipX: Float,
        tipY: Float,
        isLeftHandle: Boolean,
        outBounds: RectF
    ) {
        val r = 8.5f * density
        fillPaint.color = accentColorArgb
        val cx = if (isLeftHandle) tipX - r else tipX + r
        val cy = tipY + r
        outBounds.set(cx - r * 1.5f, tipY, cx + r * 1.5f, cy + r * 1.5f)

        canvas.drawCircle(cx, cy, r, fillPaint)
        if (isLeftHandle) {
            canvas.drawRect(tipX - r, tipY, tipX, tipY + r, fillPaint)
        } else {
            canvas.drawRect(tipX, tipY, tipX + r, tipY + r, fillPaint)
        }
    }

    private fun drawCaretTeardropHandle(
        canvas: Canvas,
        tipX: Float,
        tipY: Float,
        outBounds: RectF
    ) {
        val r = 7.5f * density
        val cy = tipY + r * 1.35f
        fillPaint.color = accentColorArgb
        handlePath.reset()
        handlePath.moveTo(tipX, tipY)
        handlePath.lineTo(tipX - r * 0.72f, cy - r * 0.45f)
        handlePath.lineTo(tipX + r * 0.72f, cy - r * 0.45f)
        handlePath.close()
        canvas.drawPath(handlePath, fillPaint)
        canvas.drawCircle(tipX, cy, r, fillPaint)
        outBounds.set(tipX - r * 1.8f, tipY, tipX + r * 1.8f, cy + r * 1.8f)
    }
}
