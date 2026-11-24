package com.tali.notes.canvas

import com.tali.notes.geometry.Rect
import com.tali.notes.model.AudioItem
import com.tali.notes.model.CanvasItem
import java.util.UUID

/**
 * One undoable edit: items removed and items added (a move or edit is both). A [tidy] change only
 * corrects how items are stored (the same items, nothing she did): it isn't undone, and the
 * notebook doesn't count as edited.
 */
class Change(val removed: List<CanvasItem>, val added: List<CanvasItem>, val tidy: Boolean = false) {
  fun inverse() = Change(added, removed)

  val isEmpty: Boolean
    get() = removed.isEmpty() && added.isEmpty()
}

/** The items of one notebook plus its undo history. Lives in the ViewModel so it survives rotation. */
class Document(initial: List<CanvasItem>) {
  private val items = LinkedHashMap<String, CanvasItem>()
  private val undoStack = ArrayDeque<Change>()
  private val redoStack = ArrayDeque<Change>()
  private var nextZ: Long

  /** Called for every applied change, including undo and redo, so storage and rendering follow along. */
  val listeners = mutableListOf<(Change) -> Unit>()

  /** Called when undo or redo availability changes. */
  var onHistoryChanged: ((canUndo: Boolean, canRedo: Boolean) -> Unit)? = null

  private val memoItems = LinkedHashMap<String, AudioItem>()

  /** The notebook's voice memos (not among [all]). */
  val memos: Collection<AudioItem>
    get() = memoItems.values

  init {
    initial.filterIsInstance<AudioItem>().forEach { memoItems[it.id] = it }
    initial.filterNot { it is AudioItem }.sortedBy { it.z }.forEach { items[it.id] = it }
    nextZ = (initial.maxOfOrNull { it.z } ?: 0L) + 1
  }

  val all: Collection<CanvasItem>
    get() = items.values

  val size: Int
    get() = items.size

  operator fun get(id: String): CanvasItem? = items[id] ?: memoItems[id]

  fun newId(): String = UUID.randomUUID().toString()

  fun nextZ(): Long = nextZ++

  fun intersecting(r: Rect): List<CanvasItem> = items.values.filter { it.bounds.intersects(r) }

  fun contentBounds(): Rect? = items.values.map { it.bounds }.reduceOrNull(Rect::union)

  /** Applies and records a change for undo. */
  fun commit(change: Change) {
    if (change.isEmpty) return
    applyRaw(change)
    undoStack.addLast(change)
    if (undoStack.size > 200) undoStack.removeFirst()
    redoStack.clear()
    notifyHistory()
  }

  /** Puts [items] in place of the items with their ids, as a [Change.tidy] change (not undoable). */
  fun tidy(items: List<CanvasItem>) {
    val old = items.mapNotNull { this.items[it.id] }
    if (items.isEmpty() || old.size != items.size) return
    applyRaw(Change(old, items, tidy = true))
  }

  fun undo() {
    val c = undoStack.removeLastOrNull() ?: return
    applyRaw(c.inverse())
    redoStack.addLast(c)
    notifyHistory()
  }

  fun redo() {
    val c = redoStack.removeLastOrNull() ?: return
    applyRaw(c)
    undoStack.addLast(c)
    notifyHistory()
  }

  val canUndo: Boolean
    get() = undoStack.isNotEmpty()

  val canRedo: Boolean
    get() = redoStack.isNotEmpty()

  private fun applyRaw(change: Change) {
    // Voice memos are kept apart: the canvas's tools (lasso, eraser, fill, drawing) never meet them.
    change.removed.forEach { if (it is AudioItem) memoItems.remove(it.id) }
    change.added.forEach { if (it is AudioItem) memoItems[it.id] = it }
    val c = Change(change.removed.filterNot { it is AudioItem }, change.added.filterNot { it is AudioItem })
    c.removed.forEach { items.remove(it.id) }
    // Re-insert in z order so iteration stays back-to-front.
    if (c.added.isNotEmpty()) {
      val topZ = items.values.lastOrNull()?.z ?: Long.MIN_VALUE
      if (c.added.all { it.z > topZ }) {
        // Common case (new ink on top): append without re-sorting everything.
        c.added.sortedBy { it.z }.forEach { items[it.id] = it }
      } else {
        val merged = (items.values + c.added).sortedBy { it.z }
        items.clear()
        merged.forEach { items[it.id] = it }
      }
      nextZ = maxOf(nextZ, c.added.maxOf { it.z } + 1)
    }
    // Listeners hear the whole change (storage keeps the memos too).
    listeners.toList().forEach { it(change) }
  }

  private fun notifyHistory() = onHistoryChanged?.invoke(canUndo, canRedo)
}
