package com.bqsqwad.proedit.editor

import android.net.Uri

/** Non-destructive project state. Media files remain untouched; edits are metadata. */
data class BqClip(
    val id: String,
    val uri: Uri,
    val name: String,
    val startMs: Long = 0L,
    val endMs: Long? = null,
    val speed: Float = 1f,
    val rotationDegrees: Float = 0f,
    val scale: Float = 1f,
    val x: Float = 0f,
    val y: Float = 0f,
    val volume: Float = 1f
)

data class BqProject(
    val name: String = "Untitled Project",
    val clips: List<BqClip> = emptyList(),
    val selectedClipId: String? = null,
    val playheadMs: Long = 0L
)

class BqProjectHistory(initial: BqProject = BqProject()) {
    private val undoStack = ArrayDeque<BqProject>().apply { addLast(initial) }
    private val redoStack = ArrayDeque<BqProject>()

    val current: BqProject get() = undoStack.last()
    val canUndo: Boolean get() = undoStack.size > 1
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun commit(next: BqProject) {
        if (next == current) return
        undoStack.addLast(next)
        redoStack.clear()
    }

    fun undo(): BqProject? {
        if (!canUndo) return null
        redoStack.addLast(undoStack.removeLast())
        return current
    }

    fun redo(): BqProject? {
        if (!canRedo) return null
        val next = redoStack.removeLast()
        undoStack.addLast(next)
        return next
    }
}
