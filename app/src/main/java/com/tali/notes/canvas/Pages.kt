package com.tali.notes.canvas

import com.tali.notes.export.Exporter
import com.tali.notes.geometry.Rect
import kotlin.math.floor

/**
 * Where the A4 pages of a paged notebook sit on the canvas: one column of pages starting at the
 * origin, with a small gap between them, upright ([PORTRAIT]) or on their side ([LANDSCAPE]) as
 * chosen when the notebook was made. Everything is in world units (dp).
 */
class Pages private constructor(val landscape: Boolean) {
  val width = if (landscape) Exporter.A4_HEIGHT else Exporter.A4_WIDTH
  val height = if (landscape) Exporter.A4_WIDTH else Exporter.A4_HEIGHT
  val pitch = height + GAP

  /** Width over height. */
  val aspect: Float
    get() = width / height

  fun rect(index: Int) = Rect(0f, index * pitch, width, index * pitch + height)

  fun all(count: Int): List<Rect> = List(count) { rect(it) }

  /** The page a height falls on (the gap below a page counts as that page). */
  fun indexAt(y: Float): Int = floor(y / pitch).toInt()

  /** Whether [x], [y] lies on one of the first [count] pages. */
  fun contains(count: Int, x: Float, y: Float): Boolean {
    if (x < 0f || x > width || y < 0f) return false
    val i = indexAt(y)
    return i < count && y - i * pitch <= height
  }

  /**
   * Where [item] is shown on the first [count] pages: writing and shapes are never drawn past a
   * page's sides; with [between] (Write between pages) they may cross the gaps, otherwise each stays
   * on its own page (where a stroke began, or where most of a shape is). The rest is only hidden:
   * moved, it shows again. Null for pictures and text, which aren't clipped.
   */
  fun clipFor(item: com.tali.notes.model.CanvasItem, count: Int, between: Boolean): Rect? {
    if (item !is com.tali.notes.model.InkItem && item !is com.tali.notes.model.ShapeItem) return null
    if (count <= 0) return null
    if (between) return Rect(0f, 0f, width, rect(count - 1).bottom)
    val y = if (item is com.tali.notes.model.InkItem) item.start.y else item.bounds.center.y
    return rect(indexAt(y).coerceIn(0, count - 1))
  }

  /** How many pages it takes to hold everything down to [content]'s bottom edge. */
  fun needed(content: Rect?): Int = if (content == null) 1 else (indexAt(content.bottom) + 1).coerceAtLeast(1)

  companion object {
    /** The pages left after deleting [gone] (by index) from [count]: the order to lay them out in. */
    fun withoutPages(count: Int, gone: Set<Int>): List<Int> = (0 until count).filterNot { it in gone }

    /** Every page, each one in [copied] followed straight after by its copy. */
    fun withCopies(count: Int, copied: Set<Int>): List<Int> = (0 until count).flatMap { if (it in copied) listOf(it, it) else listOf(it) }

    /** Where page [p] ends up when page [from] is moved to position [to] (the ones between shift one place). */
    fun movedIndex(p: Int, from: Int, to: Int): Int =
      when {
        p == from -> to
        from < to && p in from + 1..to -> p - 1
        from > to && p in to until from -> p + 1
        else -> p
      }

    const val GAP = 40f
    val PORTRAIT = Pages(false)
    val LANDSCAPE = Pages(true)

    fun of(landscape: Boolean) = if (landscape) LANDSCAPE else PORTRAIT
  }
}
