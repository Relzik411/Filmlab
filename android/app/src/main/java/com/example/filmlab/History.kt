package com.example.filmlab

/**
 * Undo and redo for edits. Changes become steps when [record] is called (after a pause in editing),
 * so dragging a slider makes one step rather than hundreds. Mirrors History.swift.
 */
class History<T>(initial: T) {
    private val undoStack = ArrayDeque<T>()
    private val redoStack = ArrayDeque<T>()
    /** The last recorded state. */
    var committed: T = initial
        private set

    fun canUndo(current: T) = undoStack.isNotEmpty() || current != committed
    fun canRedo(current: T) = redoStack.isNotEmpty() && current == committed

    /** Makes [current] a step, if it differs from the last one. Returns true if it did. */
    fun record(current: T): Boolean {
        if (current == committed) return false
        undoStack.addLast(committed)
        if (undoStack.size > LIMIT) undoStack.removeFirst()
        redoStack.clear()
        committed = current
        return true
    }

    fun undo(current: T): T? {
        record(current)
        val previous = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(committed)
        committed = previous
        return previous
    }

    fun redo(current: T): T? {
        if (current != committed) return null
        val next = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(committed)
        committed = next
        return next
    }

    private companion object {
        const val LIMIT = 100
    }
}
