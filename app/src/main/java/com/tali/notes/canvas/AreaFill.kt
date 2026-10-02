package com.tali.notes.canvas

/**
 * Finds the area around a tapped point that her writing encloses, on a grid of "wall" pixels (the
 * ink drawn into a mask). Small gaps in hand-drawn outlines are bridged by thickening the walls a
 * little first; if the area still runs out to the edge of the search window, it isn't closed and
 * nothing is filled (rather than flooding the page). The result is grown back by a few pixels so
 * the fill tucks under the lines instead of leaving a thin gap beside them.
 */
object AreaFill {
  /**
   * [walls] is a [w] by [h] grid (row by row). Returns the filled grid, or null when the tap is on a
   * line or the area isn't closed. [bridge] is how many pixels of gap to close.
   */
  fun fill(walls: BooleanArray, w: Int, h: Int, x: Int, y: Int, bridge: Int): BooleanArray? {
    if (x !in 0 until w || y !in 0 until h) return null
    val thick = dilate(walls, w, h, bridge)
    if (thick[y * w + x]) return null
    val filled = BooleanArray(w * h)
    // Scanline flood fill: each stack entry seeds one run of open cells in a row.
    var stack = IntArray(256)
    var top = 0
    stack[top++] = y * w + x
    var leaked = false
    while (top > 0) {
      val i = stack[--top]
      if (filled[i] || thick[i]) continue
      val row = i / w
      var l = i % w
      while (l > 0 && !thick[row * w + l - 1] && !filled[row * w + l - 1]) l--
      var r = i % w
      while (r < w - 1 && !thick[row * w + r + 1] && !filled[row * w + r + 1]) r++
      if (l == 0 || r == w - 1 || row == 0 || row == h - 1) {
        leaked = true
        break
      }
      for (cx in l..r) filled[row * w + cx] = true
      for (ny in intArrayOf(row - 1, row + 1)) {
        var open = false
        for (cx in l..r) {
          val j = ny * w + cx
          val free = !filled[j] && !thick[j]
          if (free && !open) {
            if (top == stack.size) stack = stack.copyOf(stack.size * 2)
            stack[top++] = j
          }
          open = free
        }
      }
    }
    if (leaked) return null
    return dilate(filled, w, h, bridge + 2)
  }

  /** Grows the true cells of [grid] by [r] cells in every direction (a square brush, two passes). */
  fun dilate(grid: BooleanArray, w: Int, h: Int, r: Int): BooleanArray {
    if (r <= 0) return grid.copyOf()
    val horizontal = BooleanArray(w * h)
    for (y in 0 until h) {
      var last = -r - 1 // index of the most recent true cell in this row
      for (x in 0 until w) {
        if (grid[y * w + x]) last = x
        if (x - last <= r) horizontal[y * w + x] = true
      }
      last = w + r + 1
      for (x in w - 1 downTo 0) {
        if (grid[y * w + x]) last = x
        if (last - x <= r) horizontal[y * w + x] = true
      }
    }
    val out = BooleanArray(w * h)
    for (x in 0 until w) {
      var last = -r - 1
      for (y in 0 until h) {
        if (horizontal[y * w + x]) last = y
        if (y - last <= r) out[y * w + x] = true
      }
      last = h + r + 1
      for (y in h - 1 downTo 0) {
        if (horizontal[y * w + x]) last = y
        if (last - y <= r) out[y * w + x] = true
      }
    }
    return out
  }
}
