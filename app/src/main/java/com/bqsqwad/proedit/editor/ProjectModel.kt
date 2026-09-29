package com.bqsqwad.proedit.editor

/** Bounded undo/redo history for immutable project states. */
class BqProjectHistory(initial: BqProject = BqProject(), private val maxEntries: Int = 150) {
    private val undoStack = ArrayDeque<BqProject>().apply { addLast(initial) }
    private val redoStack = ArrayDeque<BqProject>()

    val current: BqProject get() = undoStack.last()
    val canUndo: Boolean get() = undoStack.size > 1
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun commit(next: BqProject) {
        if (next == current) return
        undoStack.addLast(next)
        while (undoStack.size > maxEntries.coerceAtLeast(2)) undoStack.removeFirst()
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

    fun clearRedo() = redoStack.clear()
}
