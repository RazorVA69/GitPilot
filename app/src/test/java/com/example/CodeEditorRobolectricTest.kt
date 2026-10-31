package com.example

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import androidx.test.core.app.ApplicationProvider
import com.example.ui.components.editor.CodeDocumentBuffer
import com.example.ui.components.editor.EditorController
import com.example.ui.components.editor.NativeCodeEditorView
import com.example.ui.components.editor.SupportedLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CodeEditorRobolectricTest {

    private lateinit var context: Context
    private lateinit var clipboardManager: ClipboardManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clipboardManager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboardManager.setPrimaryClip(ClipData.newPlainText("init", ""))
    }

    private fun createEditorWithText(
        text: String,
        filePath: String = "src/Main.kt",
        width: Int = 1080,
        height: Int = 1920
    ): Triple<EditorController, NativeCodeEditorView, InputConnection> {
        val controller = EditorController()
        val view = NativeCodeEditorView(context)
        controller.attachedView = view
        view.controller = controller
        view.setLanguage(SupportedLanguage.KOTLIN)

        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, height)

        controller.switchToFile(filePath, text)
        val editorInfo = EditorInfo()
        val ic = view.onCreateInputConnection(editorInfo)
        assertNotNull("InputConnection must be created for editable view", ic)
        return Triple(controller, view, ic!!)
    }

    @Test
    fun `gboard context menu cut removes selected text and copies to clipboard`() {
        val initial = "fun greet() {\n    val message = \"Hello World\"\n    println(message)\n}"
        var syncedText: String? = null
        val (_, view, ic) = createEditorWithText(initial)
        view.onImmediateContentSync = { syncedText = it }

        val target = "\"Hello World\""
        val selStart = initial.indexOf(target)
        val selEnd = selStart + target.length
        assertTrue(ic.setSelection(selStart, selEnd))
        assertEquals(target, ic.getSelectedText(0)?.toString())

        // Simulate Gboard Text Editing "Cut" action via performContextMenuAction(android.R.id.cut)
        val cutResult = ic.performContextMenuAction(android.R.id.cut)
        assertTrue(cutResult)

        // Verify text was actually cut from buffer
        val expectedAfterCut = initial.removeRange(selStart, selEnd)
        assertEquals(expectedAfterCut, view.buffer.getFullText())
        assertEquals(expectedAfterCut, syncedText)
        assertEquals(selStart, view.selectionAnchor)
        assertEquals(selStart, view.selectionCaret)

        // Verify clipboard received exact cut text
        val clipText = clipboardManager.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()
        assertEquals(target, clipText)

        // Verify Paste restores it via Gboard context menu paste
        assertTrue(ic.performContextMenuAction(android.R.id.paste))
        assertEquals(initial, view.buffer.getFullText())
    }

    @Test
    fun `keycode cut 277 cuts text while keycode copy 278 only copies`() {
        // Explicit regression test for KEYCODE_CUT (277) vs KEYCODE_COPY (278)
        assertEquals(277, KeyEvent.KEYCODE_CUT)
        assertEquals(278, KeyEvent.KEYCODE_COPY)

        val initial = "val alpha = 100\nval beta = 200\n"
        val (_, view, ic) = createEditorWithText(initial)

        // 1. Test KEYCODE_COPY (278): copies "100" WITHOUT deleting it
        val s1 = initial.indexOf("100")
        ic.setSelection(s1, s1 + 3)
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_COPY))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_COPY))
        assertEquals(initial, view.buffer.getFullText())
        assertEquals("100", clipboardManager.primaryClip?.getItemAt(0)?.text?.toString())

        // 2. Test KEYCODE_CUT (277): cuts "200" AND deletes it from buffer
        val s2 = initial.indexOf("200")
        ic.setSelection(s2, s2 + 3)
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CUT))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CUT))
        assertEquals("val alpha = 100\nval beta = \n", view.buffer.getFullText())
        assertEquals("200", clipboardManager.primaryClip?.getItemAt(0)?.text?.toString())

        // 3. Test KEYCODE_PASTE (279): pastes "200" back
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_PASTE))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_PASTE))
        assertEquals(initial, view.buffer.getFullText())
    }

    @Test
    fun `gboard text editing select mode supports expanding shrinking reverse selection and cut`() {
        val initial = "abcdef0123456789"
        val (_, view, ic) = createEditorWithText(initial)

        // Place cursor at index 6 ('0')
        ic.setSelection(6, 6)

        // Part A: Gboard Select mode via META_SHIFT_ON — expand right 4 chars ("0123"), then shrink left 2 chars ("01")
        repeat(4) {
            ic.sendKeyEvent(KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT, 0, KeyEvent.META_SHIFT_ON))
        }
        assertEquals("0123", view.getSelectedTextString())

        // Shrink selection back by 2 chars (regression test: old editor blocked shrinking selections!)
        repeat(2) {
            ic.sendKeyEvent(KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT, 0, KeyEvent.META_SHIFT_ON))
        }
        assertEquals("01", view.getSelectedTextString())

        // Part B: Cross anchor to select backwards ("ef" before index 6) via KEYCODE_SHIFT_LEFT latch (no metaState on DPAD)
        ic.setSelection(6, 6)
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SHIFT_LEFT))
        repeat(2) {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_LEFT))
        }
        assertEquals(6, view.selectionAnchor)
        assertEquals(4, view.selectionCaret)
        assertEquals("ef", view.getSelectedTextString())

        // ExtractedText must always report normalized [selectionStart <= selectionEnd] so Gboard enables Cut/Copy
        val extracted = ic.getExtractedText(ExtractedTextRequest(), 0)
        assertEquals(4, extracted.selectionStart)
        assertEquals(6, extracted.selectionEnd)

        // Cut backward selection via Gboard Cut
        assertTrue(ic.performContextMenuAction(android.R.id.cut))
        assertEquals("abcd0123456789", view.buffer.getFullText())
        assertEquals("ef", clipboardManager.primaryClip?.getItemAt(0)?.text?.toString())
        assertEquals(4, view.selectionCaret)
    }

    @Test
    fun `composing text batch edits and stale composing selection replacement work cleanly`() {
        val initial = "val name = \nval other = 42"
        val (_, view, ic) = createEditorWithText(initial)

        val insertPos = "val name = ".length
        ic.setSelection(insertPos, insertPos)

        // Simulate Gboard glide/composing input
        ic.beginBatchEdit()
        ic.setComposingText("Git", 1)
        ic.endBatchEdit()
        assertEquals("val name = Git\nval other = 42", view.buffer.getFullText())

        ic.beginBatchEdit()
        ic.setComposingText("GitPilot", 1)
        ic.endBatchEdit()
        assertEquals("val name = GitPilot\nval other = 42", view.buffer.getFullText())

        // While "GitPilot" was composing, user selects "42" at the end and types/cuts over it
        val fortyTwoStart = view.buffer.getFullText().indexOf("42")
        ic.setSelection(fortyTwoStart, fortyTwoStart + 2)
        ic.commitText("99", 1)

        // Must replace "42" -> "99", NOT clobber "GitPilot"!
        assertEquals("val name = GitPilot\nval other = 99", view.buffer.getFullText())
    }

    @Test
    fun `editing on visible line and selectAll never jump scroll position`() {
        val sb = StringBuilder()
        for (i in 1..1000) {
            sb.append("    val lineItemNumber").append(i).append(" = ").append(i).append('\n')
        }
        val docText = sb.toString()
        val (controller, view, ic) = createEditorWithText(docText)

        // Jump once to line 500
        controller.jumpToLine(500, selectEntireLine = false)
        val steadyScrollY = view.virtualScrollY
        assertTrue("Expected non-zero scrollY at line 500, got $steadyScrollY", steadyScrollY > 2000)

        // Collapse at end of line 500 and type characters via Gboard InputConnection
        val line500End = view.buffer.getLineEnd(499)
        ic.setSelection(line500End, line500End)
        assertEquals(steadyScrollY, view.virtualScrollY)

        ic.commitText(" + 123", 1)
        assertEquals("Typing on visible line 500 must change scrollY by 0px", steadyScrollY, view.virtualScrollY)
        assertEquals(500, controller.cursorLine)

        // Simulate ViewModel 220ms debounce echoing back PREVIOUS or CURRENT content while user continues typing
        controller.onExternalContentPropChanged("src/Main.kt", docText)
        assertEquals("Stale ViewModel debounce echo must NOT jump scrollY", steadyScrollY, view.virtualScrollY)
        assertTrue(view.buffer.getLine(499).endsWith(" + 123"))

        controller.flushPendingSync { echoed ->
            controller.onExternalContentPropChanged("src/Main.kt", echoed)
        }
        assertEquals("Synced ViewModel content echo must NOT jump scrollY", steadyScrollY, view.virtualScrollY)

        // Gboard Select All must select 0..length WITHOUT jumping viewport away from line 500!
        ic.performContextMenuAction(android.R.id.selectAll)
        assertEquals(0, view.selMin)
        assertEquals(view.buffer.length, view.selMax)
        assertEquals("Select All must NOT yank scrollY to top or bottom", steadyScrollY, view.virtualScrollY)
    }

    @Test
    fun `cold open jumpToLine before layout lands accurately on first measure and never re-triggers`() {
        val sb = StringBuilder()
        for (i in 1..600) {
            sb.append("fun function").append(i).append("() = ").append(i).append('\n')
        }
        val controller = EditorController()
        val unmeasuredView = NativeCodeEditorView(context)
        controller.attachedView = unmeasuredView
        unmeasuredView.controller = controller

        // Bind session and request jumpToLine(350) BEFORE view has been measured/laid out (height == 0)
        controller.switchToFile("src/ColdOpen.kt", sb.toString())
        controller.jumpToLine(350, selectEntireLine = true)
        assertEquals(350, controller.cursorLine)

        // Now perform initial layout pass (0 -> 1920px height)
        unmeasuredView.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
        )
        unmeasuredView.layout(0, 0, 1080, 1920)
        val jumpedScrollY = unmeasuredView.virtualScrollY
        assertTrue("Cold jumpToLine should land near line 350 after layout, got $jumpedScrollY", jumpedScrollY > 1500)

        // User scrolls manually down to another region and keyboard opens (shrinking height 1920 -> 1100)
        val userScrollY = jumpedScrollY + 900
        controller.scrollToVerticalPx(userScrollY, animate = false)
        assertEquals(userScrollY, unmeasuredView.virtualScrollY)

        unmeasuredView.measure(
            View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(1100, View.MeasureSpec.EXACTLY)
        )
        unmeasuredView.layout(0, 0, 1080, 1100)
        // Must NOT jump back to jumpedScrollY!
        assertEquals(userScrollY, unmeasuredView.virtualScrollY)
    }

    @Test
    fun `huge 55000 line 2_6MB file edits renders and extracts binder safe window smoothly`() {
        val lineTemplate = "    val computedValueForLargeFileRow = calculateSomethingImportant(index = "
        val sb = StringBuilder(55_000 * 86)
        sb.append("package com.example.huge\n\nclass GiantGeneratedSource {\n")
        for (i in 1..55_000) {
            sb.append(lineTemplate).append(i).append(")\n")
        }
        sb.append("}\n")
        val hugeContent = sb.toString()
        assertTrue("Expected > 2.5 MB file, got ${hugeContent.length}", hugeContent.length > 2_500_000)

        val (controller, view, ic) = createEditorWithText(hugeContent, filePath = "src/GiantGeneratedSource.kt")
        assertEquals(55_005, view.buffer.lineCount)

        // Jump to line 48,000 near bottom of 2.6 MB file
        controller.jumpToLine(48_000, selectEntireLine = false)
        val caretAt48k = view.selectionCaret
        assertTrue(caretAt48k > 2_000_000)

        // Verify ExtractedText is strictly capped to <= 131,072 chars (Binder safe!) with exact offset math
        val extracted = ic.getExtractedText(ExtractedTextRequest(), InputConnection.GET_EXTRACTED_TEXT_MONITOR)
        assertTrue("ExtractedText must be <= 131072 chars to prevent TransactionTooLargeException", extracted.text.length <= 131_072)
        assertEquals(caretAt48k, extracted.startOffset + extracted.selectionStart)
        assertEquals(caretAt48k, extracted.startOffset + extracted.selectionEnd)

        // Verify 50 single-line keystrokes at line 48,000 execute in milliseconds via Fenwick tree O(log N)
        val startNs = System.nanoTime()
        repeat(50) {
            ic.commitText("x", 1)
        }
        val elapsedMs = (System.nanoTime() - startNs) / 1_000_000
        assertTrue("50 keystrokes on 55,000-line file took ${elapsedMs}ms (expected < 500ms)", elapsedMs < 500)

        // Verify viewport Canvas rendering on 55,000-line file renders only visible rows cleanly
        val bmp = Bitmap.createBitmap(540, 960, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        view.draw(canvas)

        // Verify Undo & Redo on 2.6 MB file
        assertTrue(controller.canUndo)
        assertTrue(view.undo())
        assertTrue(controller.canRedo)
        assertTrue(view.redo())

        // Verify CodeDocumentBuffer streaming search across 55,000 lines
        val matches = CodeDocumentBuffer(hugeContent).findMatches("index = 48000)", ignoreCase = false)
        assertEquals(1, matches.size)
    }
}
