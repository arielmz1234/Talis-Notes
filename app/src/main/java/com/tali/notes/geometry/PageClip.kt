package com.tali.notes.geometry

import kotlin.math.max
import kotlin.math.min

/**
 * Cuts a pen path to the parts that lie inside some rectangles (the pages, or the page column),
 * so writing that runs off a page stops at its edge.
 *
 * Positions along the path are given as `s = i + t`: point `i`, then `t` of the way to point
 * `i + 1`. Each run is the stretch [start, end] of one unbroken part inside.
 */
object PageClip {
  data class Run(val start: Float, val end: Float)

  /** The inside stretches of [points] (in order); empty when it never touches [areas]. */
  fun runs(points: List<Pt>, areas: List<Rect>): List<Run> {
    if (points.isEmpty() || areas.isEmpty()) return emptyList()
    if (points.size == 1) return if (areas.any { it.contains(points[0]) }) listOf(Run(0f, 0f)) else emptyList()
    val parts = ArrayList<Run>()
    for (i in 0 until points.size - 1) {
      val a = points[i]
      val b = points[i + 1]
      for (r in areas) {
        val (t0, t1) = clip(a, b, r) ?: continue
        parts += Run(i + t0, i + t1)
      }
    }
    parts.sortBy { it.start }
    // Join stretches that meet (the end of one segment's part is the start of the next one's).
    val out = ArrayList<Run>()
    for (p in parts) {
      val last = out.lastOrNull()
      if (last != null && p.start <= last.end + JOIN) out[out.size - 1] = Run(last.start, max(last.end, p.end))
      else out += p
    }
    return out
  }

  /** The part of a→b inside [r], as (t0, t1) along it, or null (Liang–Barsky). */
  private fun clip(a: Pt, b: Pt, r: Rect): Pair<Float, Float>? {
    var t0 = 0f
    var t1 = 1f
    val dx = b.x - a.x
    val dy = b.y - a.y
    val p = floatArrayOf(-dx, dx, -dy, dy)
    val q = floatArrayOf(a.x - r.left, r.right - a.x, a.y - r.top, r.bottom - a.y)
    for (k in 0..3) {
      if (p[k] == 0f) {
        if (q[k] < 0f) return null
      } else {
        val t = q[k] / p[k]
        if (p[k] < 0f) t0 = max(t0, t) else t1 = min(t1, t)
      }
    }
    return if (t0 <= t1) t0 to t1 else null
  }

  private const val JOIN = 1e-4f
}
