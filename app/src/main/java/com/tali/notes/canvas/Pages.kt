package com.tali.notes.canvas

import com.tali.notes.export.Exporter
import com.tali.notes.geometry.Rect
import kotlin.math.floor

/**
 * Where the A4 pages of a paged notebook sit on the canvas: one column of portrait pages starting
 * at the origin, with a small gap between them. Everything is in world units (dp).
 */
object Pages {
  val WIDTH = Exporter.A4_WIDTH
  val HEIGHT = Exporter.A4_HEIGHT
  const val GAP = 40f
  val PITCH = HEIGHT + GAP

  fun rect(index: Int) = Rect(0f, index * PITCH, WIDTH, index * PITCH + HEIGHT)

  fun all(count: Int): List<Rect> = List(count) { rect(it) }

  /** The page a height falls on (the gap below a page counts as that page). */
  fun indexAt(y: Float): Int = floor(y / PITCH).toInt()

  /** Whether [x], [y] lies on one of the first [count] pages. */
  fun contains(count: Int, x: Float, y: Float): Boolean {
    if (x < 0f || x > WIDTH || y < 0f) return false
    val i = indexAt(y)
    return i < count && y - i * PITCH <= HEIGHT
  }

  /** How many pages it takes to hold everything down to [content]'s bottom edge. */
  fun needed(content: Rect?): Int = if (content == null) 1 else (indexAt(content.bottom) + 1).coerceAtLeast(1)
}
