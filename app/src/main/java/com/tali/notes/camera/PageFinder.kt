package com.tali.notes.camera

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** A point in a picture, in parts of its width and height (0..1). */
data class P(val x: Float, val y: Float)

/** A page's four corners, in parts of the picture: top-left, top-right, bottom-right, bottom-left. */
data class Quad(val tl: P, val tr: P, val br: P, val bl: P) {
  val points: List<P>
    get() = listOf(tl, tr, br, bl)

  fun with(i: Int, p: P): Quad =
    when (i) {
      0 -> copy(tl = p)
      1 -> copy(tr = p)
      2 -> copy(br = p)
      else -> copy(bl = p)
    }

  /** Moved part of the way toward [o] (for a live outline that glides instead of jumping). */
  fun toward(o: Quad, t: Float): Quad {
    fun m(a: P, b: P) = P(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
    return Quad(m(tl, o.tl), m(tr, o.tr), m(br, o.br), m(bl, o.bl))
  }

  /** The same corners in a picture turned a quarter clockwise [quarters] times. */
  fun turned(quarters: Int): Quad {
    var q = this
    repeat(((quarters % 4) + 4) % 4) {
      fun r(p: P) = P(1f - p.y, p.x)
      // Turned clockwise, the old bottom-left becomes the top-left.
      q = Quad(r(q.bl), r(q.tl), r(q.tr), r(q.br))
    }
    return q
  }

  /** No corner crossing over: the four make a shape that bulges outward everywhere. */
  val convex: Boolean
    get() {
      val ps = points
      var sign = 0
      for (i in 0 until 4) {
        val a = ps[i]
        val b = ps[(i + 1) % 4]
        val c = ps[(i + 2) % 4]
        val z = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
        val s = if (z > 0) 1 else if (z < 0) -1 else 0
        if (s == 0) return false
        if (sign == 0) sign = s else if (s != sign) return false
      }
      return true
    }

  companion object {
    /** The whole picture, a little in from its edges. */
    fun whole(inset: Float = 0f) = Quad(P(inset, inset), P(1 - inset, inset), P(1 - inset, 1 - inset), P(inset, 1 - inset))

    /** Four corners in any order, put round as top-left, top-right, bottom-right, bottom-left. */
    fun ordered(ps: List<P>): Quad {
      val cx = ps.sumOf { it.x.toDouble() } / ps.size
      val cy = ps.sumOf { it.y.toDouble() } / ps.size
      val round = ps.sortedBy { atan2(it.y - cy, it.x - cx) } // clockwise on screen (y down), from the left
      val start = round.indices.minBy { round[it].x + round[it].y }
      val r = List(4) { round[(start + it) % 4] }
      return Quad(r[0], r[1], r[2], r[3])
    }
  }
}

/**
 * Finds a sheet of paper in a small greyscale picture, without any library: the paper is a large
 * area clearly lighter (or, on a white desk, darker) than what's round it. The picture is blurred,
 * split light from dark at the level that best separates them (Otsu), and the biggest solid patch
 * that isn't just the background is taken; the four corners that cover most of its outline are the
 * page. Returns null when nothing looks like a page, and the person sets the corners by hand.
 */
object PageFinder {
  /** [lum] is [w]×[h] brightness 0..255, row by row. Small pictures (about 320 across) are plenty. */
  fun find(lum: IntArray, w: Int, h: Int): Quad? {
    if (w < 16 || h < 16) return null
    val blurred = blur(blur(lum, w, h), w, h)
    val t = otsu(blurred)
    // Light and dark must really differ: on a plain desk the split is only noise.
    var lo = 0L
    var nLo = 0
    var hi = 0L
    for (v in blurred) if (v > t) hi += v else { lo += v; nLo++ }
    val nHi = blurred.size - nLo
    if (nLo == 0 || nHi == 0 || hi / nHi - lo / nLo < MIN_CONTRAST) return null
    var best: Candidate? = null
    for (light in listOf(true, false)) {
      val mask = BooleanArray(w * h) { if (light) blurred[it] > t else blurred[it] <= t }
      val c = candidate(open(mask, w, h), w, h) ?: continue
      if (best == null || c.score > best.score) best = c
    }
    return best?.quad
  }

  private class Candidate(val quad: Quad, val score: Float)

  private fun candidate(mask: BooleanArray, w: Int, h: Int): Candidate? {
    val comp = largest(mask, w, h) ?: return null
    val (pixels, border) = comp
    val area = pixels.size.toFloat() / (w * h)
    if (area < MIN_AREA) return null
    // Mostly round the picture's edge: that's the table or the room, not a page in it. A page that
    // fills the picture touches every edge too, but then it's nearly all of it.
    val edge = 2f * (w + h)
    if (border / edge > 0.55f && area < 0.8f) return null
    // The outline: each row's leftmost and rightmost pixel, then the hull round them.
    val left = IntArray(h) { Int.MAX_VALUE }
    val right = IntArray(h) { -1 }
    for (i in pixels) {
      val x = i % w
      val y = i / w
      if (x < left[y]) left[y] = x
      if (x > right[y]) right[y] = x
    }
    val pts = ArrayList<P>()
    for (y in 0 until h) if (right[y] >= 0) {
      pts += P(left[y].toFloat(), y.toFloat())
      pts += P(right[y] + 1f, y.toFloat())
      pts += P(left[y].toFloat(), y + 1f)
      pts += P(right[y] + 1f, y + 1f)
    }
    val hull = hull(pts)
    if (hull.size < 4) return null
    val hullArea = polyArea(hull)
    val corners = biggestQuad(simplify(hull, 48))
    val quadArea = polyArea(corners)
    // A page is solid (its writing aside) and four-sided: the hull is close to the four corners, and
    // the patch fills most of its hull.
    val fit = quadArea / hullArea
    val solid = pixels.size / hullArea
    if (fit < 0.88f || solid < 0.7f) return null
    val q = Quad.ordered(corners.map { P(it.x / w, it.y / h) })
    if (!q.convex) return null
    return Candidate(q, area * fit * solid)
  }

  /** The biggest 4-connected patch: its pixel indices and how many lie on the picture's edge. */
  private fun largest(mask: BooleanArray, w: Int, h: Int): Pair<IntArray, Int>? {
    val seen = BooleanArray(w * h)
    val stack = IntArray(w * h)
    var best: IntArray? = null
    var bestBorder = 0
    val buf = IntArray(w * h)
    for (start in mask.indices) {
      if (!mask[start] || seen[start]) continue
      var n = 0
      var top = 0
      var border = 0
      stack[top++] = start
      seen[start] = true
      while (top > 0) {
        val i = stack[--top]
        buf[n++] = i
        val x = i % w
        val y = i / w
        if (x == 0 || y == 0 || x == w - 1 || y == h - 1) border++
        if (x > 0 && mask[i - 1] && !seen[i - 1]) { seen[i - 1] = true; stack[top++] = i - 1 }
        if (x < w - 1 && mask[i + 1] && !seen[i + 1]) { seen[i + 1] = true; stack[top++] = i + 1 }
        if (y > 0 && mask[i - w] && !seen[i - w]) { seen[i - w] = true; stack[top++] = i - w }
        if (y < h - 1 && mask[i + w] && !seen[i + w]) { seen[i + w] = true; stack[top++] = i + w }
      }
      if (best == null || n > best.size) {
        best = buf.copyOf(n)
        bestBorder = border
      }
    }
    return best?.let { it to bestBorder }
  }

  /** Thin bridges (a cable, a shadow's edge) cut: shrunk by a pixel, then grown back. */
  private fun open(m: BooleanArray, w: Int, h: Int): BooleanArray {
    fun step(src: BooleanArray, keep: Boolean): BooleanArray =
      BooleanArray(w * h) { i ->
        val x = i % w
        val y = i / w
        var v = src[i]
        for (dy in -1..1) for (dx in -1..1) {
          val xx = (x + dx).coerceIn(0, w - 1)
          val yy = (y + dy).coerceIn(0, h - 1)
          v = if (keep) v && src[yy * w + xx] else v || src[yy * w + xx]
        }
        v
      }
    return step(step(m, true), false)
  }

  internal fun blur(src: IntArray, w: Int, h: Int): IntArray {
    val tmp = IntArray(w * h)
    for (y in 0 until h) for (x in 0 until w) {
      val r = y * w
      tmp[r + x] = (src[r + maxOf(x - 1, 0)] + src[r + x] + src[r + minOf(x + 1, w - 1)]) / 3
    }
    val out = IntArray(w * h)
    for (y in 0 until h) for (x in 0 until w) {
      out[y * w + x] = (tmp[maxOf(y - 1, 0) * w + x] + tmp[y * w + x] + tmp[minOf(y + 1, h - 1) * w + x]) / 3
    }
    return out
  }

  internal fun otsu(lum: IntArray): Int {
    val hist = IntArray(256)
    for (v in lum) hist[v.coerceIn(0, 255)]++
    val total = lum.size.toDouble()
    var sum = 0.0
    for (i in 0..255) sum += i * hist[i].toDouble()
    var sumB = 0.0
    var wB = 0.0
    var best = 0.0
    var t = 127
    for (i in 0..255) {
      wB += hist[i]
      if (wB == 0.0) continue
      val wF = total - wB
      if (wF == 0.0) break
      sumB += i * hist[i].toDouble()
      val mB = sumB / wB
      val mF = (sum - sumB) / wF
      val between = wB * wF * (mB - mF) * (mB - mF)
      if (between > best) {
        best = between
        t = i
      }
    }
    return t
  }

  /** Andrew's monotone chain; clockwise on screen. */
  internal fun hull(points: List<P>): List<P> {
    val ps = points.distinct().sortedWith(compareBy<P> { it.x }.thenBy { it.y })
    if (ps.size < 3) return ps
    fun cross(o: P, a: P, b: P) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
    val lower = ArrayList<P>()
    for (p in ps) {
      while (lower.size >= 2 && cross(lower[lower.size - 2], lower.last(), p) <= 0) lower.removeAt(lower.size - 1)
      lower += p
    }
    val upper = ArrayList<P>()
    for (p in ps.asReversed()) {
      while (upper.size >= 2 && cross(upper[upper.size - 2], upper.last(), p) <= 0) upper.removeAt(upper.size - 1)
      upper += p
    }
    return lower.dropLast(1) + upper.dropLast(1)
  }

  /** At most [max] hull points, dropping the ones that matter least (smallest triangle with their neighbours). */
  private fun simplify(hull: List<P>, max: Int): List<P> {
    val ps = hull.toMutableList()
    while (ps.size > max) {
      var worst = 0
      var least = Float.MAX_VALUE
      for (i in ps.indices) {
        val a = ps[(i - 1 + ps.size) % ps.size]
        val b = ps[i]
        val c = ps[(i + 1) % ps.size]
        val t = abs((b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x))
        if (t < least) {
          least = t
          worst = i
        }
      }
      ps.removeAt(worst)
    }
    return ps
  }

  /** The four hull points enclosing the most area (every pair of opposite corners tried). */
  private fun biggestQuad(ps: List<P>): List<P> {
    val n = ps.size
    if (n <= 4) return ps
    fun tri(a: P, b: P, c: P) = abs((b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)) / 2f
    var best = 0f
    var pick = listOf(ps[0], ps[1], ps[2], ps[3])
    for (i in 0 until n) for (k in i + 2 until n) {
      var left = 0f
      var lj = -1
      for (j in i + 1 until k) {
        val a = tri(ps[i], ps[j], ps[k])
        if (a > left) { left = a; lj = j }
      }
      var right = 0f
      var rl = -1
      for (l in k + 1 until n + i) {
        val a = tri(ps[i], ps[k], ps[l % n])
        if (a > right) { right = a; rl = l % n }
      }
      if (lj >= 0 && rl >= 0 && left + right > best) {
        best = left + right
        pick = listOf(ps[i], ps[lj], ps[k], ps[rl])
      }
    }
    return pick
  }

  internal fun polyArea(ps: List<P>): Float {
    var a = 0f
    for (i in ps.indices) {
      val p = ps[i]
      val q = ps[(i + 1) % ps.size]
      a += p.x * q.y - q.x * p.y
    }
    return abs(a) / 2f
  }

  fun distance(a: P, b: P) = hypot(a.x - b.x, a.y - b.y)

  private const val MIN_AREA = 0.12f
  private const val MIN_CONTRAST = 28
}
