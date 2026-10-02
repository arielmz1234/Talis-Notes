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

  /** How many pages it takes to hold everything down to [content]'s bottom edge. */
  fun needed(content: Rect?): Int = if (content == null) 1 else (indexAt(content.bottom) + 1).coerceAtLeast(1)

  companion object {
    const val GAP = 40f
    val PORTRAIT = Pages(false)
    val LANDSCAPE = Pages(true)

    fun of(landscape: Boolean) = if (landscape) LANDSCAPE else PORTRAIT
  }
}
