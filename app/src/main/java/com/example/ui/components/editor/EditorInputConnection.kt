package com.example.ui.components.editor

import android.os.Bundle
import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.CompletionInfo
import android.view.inputmethod.CorrectionInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest

/**
 * Full-fidelity Android [android.view.inputmethod.InputConnection] tailored for Gboard
 * (including Gboard's dedicated "Text editing" D-pad panel: Select, Select all, Cut, Copy, Paste,
 * directional arrows, Home/End, Backspace, glide typing, composing spans, and spacebar trackpad)
 * as well as hardware keyboards and huge multi-megabyte documents.
 *
 * All text and selection operations delegate directly to [NativeCodeEditorView] and its
 * [CodeDocumentBuffer] — never falling back to a detached dummy Editable.
 */
class EditorInputConnection(
    private val editorView: NativeCodeEditorView
) : BaseInputConnection(editorView, false) {

    override fun beginBatchEdit(): Boolean {
        return editorView.beginImeBatchEdit()
    }

    override fun endBatchEdit(): Boolean {
        return editorView.endImeBatchEdit()
    }

    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
        return editorView.imeCommitText(text ?: "", newCursorPosition)
    }

    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
        return editorView.imeSetComposingText(text ?: "", newCursorPosition)
    }

    override fun setComposingRegion(start: Int, end: Int): Boolean {
        return editorView.imeSetComposingRegion(start, end)
    }

    override fun finishComposingText(): Boolean {
        return editorView.imeFinishComposingText()
    }

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        return editorView.imeDeleteSurroundingText(beforeLength, afterLength)
    }

    override fun deleteSurroundingTextInCodePoints(beforeLength: Int, afterLength: Int): Boolean {
        return editorView.imeDeleteSurroundingTextInCodePoints(beforeLength, afterLength)
    }

    override fun setSelection(start: Int, end: Int): Boolean {
        return editorView.imeSetSelection(start, end)
    }

    override fun getTextBeforeCursor(length: Int, flags: Int): CharSequence {
        return editorView.imeGetTextBeforeCursor(length)
    }

    override fun getTextAfterCursor(length: Int, flags: Int): CharSequence {
        return editorView.imeGetTextAfterCursor(length)
    }

    override fun getSelectedText(flags: Int): CharSequence? {
        return editorView.imeGetSelectedText()
    }

    override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText {
        return editorView.imeGetExtractedText(request, flags)
    }

    override fun getCursorCapsMode(reqModes: Int): Int {
        // Code editors must not auto-capitalize variable/function identifiers mid-line
        return 0
    }

    override fun performContextMenuAction(id: Int): Boolean {
        return editorView.performEditorContextMenuAction(id)
    }

    override fun performEditorAction(actionCode: Int): Boolean {
        return editorView.imePerformEditorAction(actionCode)
    }

    override fun sendKeyEvent(event: KeyEvent): Boolean {
        return editorView.handleEditorKeyEvent(event)
    }

    override fun requestCursorUpdates(cursorUpdateMode: Int): Boolean {
        return editorView.imeRequestCursorUpdates(cursorUpdateMode)
    }

    override fun commitCorrection(correctionInfo: CorrectionInfo?): Boolean {
        return true
    }

    override fun commitCompletion(text: CompletionInfo?): Boolean {
        val committed = text?.text ?: return false
        return editorView.imeCommitText(committed, 1)
    }

    override fun clearMetaKeyStates(states: Int): Boolean {
        return editorView.imeClearMetaKeyStates(states)
    }

    override fun performPrivateCommand(action: String?, data: Bundle?): Boolean {
        return true
    }

    override fun closeConnection() {
        super.closeConnection()
        editorView.onInputConnectionClosed()
    }
}
