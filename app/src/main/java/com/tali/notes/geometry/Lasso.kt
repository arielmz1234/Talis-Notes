package com.tali.notes.geometry

/**
 * What a lasso loop picks up. It takes whatever it touches: a stroke counts if any of it is inside
 * the loop or it crosses the loop's edge, so circling just the tip of a long line takes the whole
 * line. Pictures count when the loop crosses or encloses them, but not when the loop is drawn wholly
 * inside a picture: then she's picking up the writing on top of it, not the picture.
 */
object Lasso {
  /** Whether the path [path] (points in drawing order) touches the closed loop [poly]. */
  fun touchesPath(path: List<Pt>, poly: List<Pt>): Boolean {
    if (path.isEmpty() || poly.size < 3) return false
    if (path.any { Geo.pointInPolygon(it, poly) }) return true
    // Nothing inside, but a segment may still pass through a thin part of the loop.
    for (i in 1 until path.size) if (crossesLoop(path[i - 1], path[i], poly)) return true
    return false
  }

  /** Whether [r] is touched: part of it is inside the loop or the loop crosses its edge. */
  fun touchesRect(r: Rect, poly: List<Pt>): Boolean {
    if (poly.size < 3) return false
    val corners = listOf(Pt(r.left, r.top), Pt(r.right, r.top), Pt(r.right, r.bottom), Pt(r.left, r.bottom))
    if (corners.any { Geo.pointInPolygon(it, poly) } || Geo.pointInPolygon(r.center, poly)) return true
    for (i in corners.indices) if (crossesLoop(corners[i], corners[(i + 1) % 4], poly)) return true
    // The loop drawn wholly inside the rectangle doesn't count (see above).
    return false
  }

  private fun crossesLoop(a: Pt, b: Pt, poly: List<Pt>): Boolean {
    var j = poly.size - 1
    for (i in poly.indices) {
      if (segmentsCross(a, b, poly[j], poly[i])) return true
      j = i
    }
    return false
  }

  /** Whether segments [a]-[b] and [c]-[d] cross (touching counts). */
  fun segmentsCross(a: Pt, b: Pt, c: Pt, d: Pt): Boolean {
    fun side(p: Pt, q: Pt, r: Pt) = (q.x - p.x) * (r.y - p.y) - (q.y - p.y) * (r.x - p.x)
    val d1 = side(c, d, a)
    val d2 = side(c, d, b)
    val d3 = side(a, b, c)
    val d4 = side(a, b, d)
    if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) return true
    fun on(p: Pt, q: Pt, r: Pt) = minOf(p.x, q.x) <= r.x && r.x <= maxOf(p.x, q.x) && minOf(p.y, q.y) <= r.y && r.y <= maxOf(p.y, q.y)
    return (d1 == 0f && on(c, d, a)) || (d2 == 0f && on(c, d, b)) || (d3 == 0f && on(a, b, c)) || (d4 == 0f && on(a, b, d))
  }

  /**
   * Free-form selecting: whether a pen [path] passes within [r] of the drawing [points] (a stroke
   * or a shape's outline), so tracing over or touching something picks it up.
   */
  fun nearPath(points: List<Pt>, path: List<Pt>, r: Float): Boolean {
    if (points.isEmpty() || path.isEmpty()) return false
    val a = if (points.size == 1) listOf(points[0], points[0]) else points
    val b = if (path.size == 1) listOf(path[0], path[0]) else path
    for (i in 0 until a.size - 1) for (j in 0 until b.size - 1) {
      if (segmentDistance(a[i], a[i + 1], b[j], b[j + 1]) <= r) return true
    }
    return false
  }

  /** Free-form selecting: whether a pen [path] passes within [r] of the rectangle [rect] (a picture or text). */
  fun nearRect(rect: Rect, path: List<Pt>, r: Float): Boolean {
    val grown = Rect(rect.left - r, rect.top - r, rect.right + r, rect.bottom + r)
    if (path.any { grown.contains(it) }) return true
    val corners = listOf(Pt(grown.left, grown.top), Pt(grown.right, grown.top), Pt(grown.right, grown.bottom), Pt(grown.left, grown.bottom))
    for (j in 0 until path.size - 1) for (k in 0..3) if (segmentsCross(path[j], path[j + 1], corners[k], corners[(k + 1) % 4])) return true
    return false
  }

  private fun pointSegment(p: Pt, a: Pt, b: Pt): Float {
    val ab = b - a
    val len = ab.dot(ab)
    if (len < 1e-9f) return p.dist(a)
    val t = ((p - a).dot(ab) / len).coerceIn(0f, 1f)
    return p.dist(a + ab * t)
  }

  private fun segmentDistance(a: Pt, b: Pt, c: Pt, d: Pt): Float =
    if (segmentsCross(a, b, c, d)) 0f else minOf(minOf(pointSegment(a, c, d), pointSegment(b, c, d)), minOf(pointSegment(c, a, b), pointSegment(d, a, b)))
}
