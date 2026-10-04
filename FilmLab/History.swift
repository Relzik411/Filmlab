/// Undo and redo for edits. Changes are recorded as steps when `record` is called (after a pause
/// in editing), so dragging a slider makes one step rather than hundreds.
struct History<State: Equatable> {
    private var undoStack: [State] = []
    private var redoStack: [State] = []
    /// The last recorded state.
    private(set) var committed: State
    private let limit = 100

    init(_ initial: State) {
        committed = initial
    }

    func canUndo(from current: State) -> Bool { !undoStack.isEmpty || current != committed }
    func canRedo(from current: State) -> Bool { !redoStack.isEmpty && current == committed }

    /// Makes `current` a step, if it differs from the last one. Returns true if it did.
    @discardableResult
    mutating func record(_ current: State) -> Bool {
        guard current != committed else { return false }
        undoStack.append(committed)
        if undoStack.count > limit { undoStack.removeFirst() }
        redoStack.removeAll()
        committed = current
        return true
    }

    mutating func undo(from current: State) -> State? {
        record(current)
        guard let previous = undoStack.popLast() else { return nil }
        redoStack.append(committed)
        committed = previous
        return previous
    }

    mutating func redo(from current: State) -> State? {
        guard current == committed, let next = redoStack.popLast() else { return nil }
        undoStack.append(committed)
        committed = next
        return next
    }
}
