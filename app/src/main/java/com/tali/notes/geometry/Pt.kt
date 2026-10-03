package com.tali.notes.geometry

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlinx.serialization.Serializable

/** A point (or vector) in world coordinates. World units are density-independent pixels at 100% zoom. */
@Serializable
data class Pt(val x: Float, val y: Float) {
  operator fun plus(o: Pt) = Pt(x + o.x, y + o.y)

  operator fun minus(o: Pt) = Pt(x - o.x, y - o.y)

  operator fun times(s: Float) = Pt(x * s, y * s)

  operator fun div(s: Float) = Pt(x / s, y / s)

  fun length(): Float = hypot(x, y)

  fun dist(o: Pt): Float = hypot(x - o.x, y - o.y)

  fun dot(o: Pt): Float = x * o.x + y * o.y

  fun cross(o: Pt): Float = x * o.y - y * o.x

  fun angle(): Float = atan2(y, x)

  fun normalized(): Pt {
    val l = length()
    return if (l < 1e-6f) Pt(0f, 0f) else Pt(x / l, y / l)
  }

  fun rotated(radians: Float): Pt {
    val c = cos(radians)
    val s = sin(radians)
    return Pt(x * c - y * s, x * s + y * c)
  }

  fun rotatedAround(center: Pt, radians: Float): Pt = (this - center).rotated(radians) + center

  companion object {
    val ZERO = Pt(0f, 0f)

    fun polar(radius: Float, radians: Float) = Pt(radius * cos(radians), radius * sin(radians))
  }
}

/** Axis-aligned rectangle in world coordinates. */
@Serializable
data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
  val width: Float
    get() = right - left

  val height: Float
    get() = bottom - top

  val center: Pt
    get() = Pt((left + right) / 2f, (top + bottom) / 2f)

  fun isEmpty() = width <= 0f || height <= 0f

  fun intersects(o: Rect) = left < o.right && o.left < right && top < o.bottom && o.top < bottom

  fun contains(p: Pt) = p.x >= left && p.x <= right && p.y >= top && p.y <= bottom

  fun union(o: Rect) = Rect(minOf(left, o.left), minOf(top, o.top), maxOf(right, o.right), maxOf(bottom, o.bottom))

  fun inset(d: Float) = Rect(left + d, top + d, right - d, bottom - d)

  fun outset(d: Float) = inset(-d)

  companion object {
    fun around(points: Iterable<Pt>): Rect? {
      var minX = Float.POSITIVE_INFINITY
      var minY = Float.POSITIVE_INFINITY
      var maxX = Float.NEGATIVE_INFINITY
      var maxY = Float.NEGATIVE_INFINITY
      var any = false
      for (p in points) {
        any = true
        if (p.x < minX) minX = p.x
        if (p.y < minY) minY = p.y
        if (p.x > maxX) maxX = p.x
        if (p.y > maxY) maxY = p.y
      }
      return if (any) Rect(minX, minY, maxX, maxY) else null
    }
  }
}

/** 2D affine transform `[a c tx; b d ty]`, used to move and scale selections without Android types. */
@Serializable
data class Affine(val a: Float = 1f, val b: Float = 0f, val c: Float = 0f, val d: Float = 1f, val tx: Float = 0f, val ty: Float = 0f) {
  fun apply(p: Pt) = Pt(a * p.x + c * p.y + tx, b * p.x + d * p.y + ty)

  /** Uniform scale factor, used to scale brush sizes along with geometry. */
  fun scaleFactor(): Float = kotlin.math.sqrt(kotlin.math.abs(a * d - b * c))

  /** Returns `other` applied first, then this. */
  fun then(other: Affine) =
    Affine(
      a = other.a * a + other.c * b,
      b = other.b * a + other.d * b,
      c = other.a * c + other.c * d,
      d = other.b * c + other.d * d,
      tx = other.a * tx + other.c * ty + other.tx,
      ty = other.b * tx + other.d * ty + other.ty,
    )

  val isIdentity: Boolean
    get() = this == IDENTITY

  /** Where a direction at angle [t] points after this transform (right for rotations and mirroring alike). */
  fun mapAngle(t: Float): Float = kotlin.math.atan2(b * kotlin.math.cos(t) + d * kotlin.math.sin(t), a * kotlin.math.cos(t) + c * kotlin.math.sin(t))

  /** Whether this transform mirrors (turns clockwise into anticlockwise). */
  val mirrors: Boolean
    get() = a * d - b * c < 0f

  companion object {
    val IDENTITY = Affine()

    fun translate(dx: Float, dy: Float) = Affine(tx = dx, ty = dy)

    fun scaleAround(pivot: Pt, sx: Float, sy: Float = sx) = Affine(a = sx, d = sy, tx = pivot.x - sx * pivot.x, ty = pivot.y - sy * pivot.y)

    fun rotateAround(pivot: Pt, radians: Float): Affine {
      val cos = kotlin.math.cos(radians)
      val sin = kotlin.math.sin(radians)
      return Affine(a = cos, b = sin, c = -sin, d = cos, tx = pivot.x - cos * pivot.x + sin * pivot.y, ty = pivot.y - sin * pivot.x - cos * pivot.y)
    }
  }
}

object Geo {
  fun pathLength(points: List<Pt>): Float {
    var len = 0f
    for (i in 1 until points.size) len += points[i].dist(points[i - 1])
    return len
  }

  /** Distance from [p] to the segment [a]-[b]. */
  fun distToSegment(p: Pt, a: Pt, b: Pt): Float {
    val ab = b - a
    val lenSq = ab.dot(ab)
    if (lenSq < 1e-9f) return p.dist(a)
    val t = ((p - a).dot(ab) / lenSq).coerceIn(0f, 1f)
    return p.dist(a + ab * t)
  }

  /** Perpendicular distance from [p] to the infinite line through [a] and [b]. */
  fun distToLine(p: Pt, a: Pt, b: Pt): Float {
    val ab = b - a
    val len = ab.length()
    if (len < 1e-6f) return p.dist(a)
    return kotlin.math.abs(ab.cross(p - a)) / len
  }

  /** Resamples a polyline to [n] points evenly spaced by arc length. */
  fun resample(points: List<Pt>, n: Int): List<Pt> {
    if (points.size < 2 || n < 2) return points
    val total = pathLength(points)
    if (total < 1e-6f) return List(n) { points.first() }
    val step = total / (n - 1)
    val out = ArrayList<Pt>(n)
    out.add(points.first())
    var acc = 0f
    var prev = points.first()
    var i = 1
    while (i < points.size && out.size < n - 1) {
      val cur = points[i]
      val d = prev.dist(cur)
      if (acc + d >= step && d > 0f) {
        val t = (step - acc) / d
        val q = prev + (cur - prev) * t
        out.add(q)
        prev = q
        acc = 0f
      } else {
        acc += d
        prev = cur
        i++
      }
    }
    while (out.size < n) out.add(points.last())
    return out
  }

  /**
   * A smooth curve through an open polyline's points (Catmull-Rom), sampled about every [step]
   * units, so a simplified hand-drawn line draws as the curve it was rather than as a chain of
   * straight bits. Real corners (a turn sharper than [cornerDeg]) stay sharp.
   */
  fun smooth(points: List<Pt>, step: Float = 1.5f, cornerDeg: Float = 55f): List<Pt> {
    if (points.size < 3) return points
    val corner = Math.toRadians(cornerDeg.toDouble()).toFloat()
    val out = ArrayList<Pt>(points.size * 4)
    out.add(points[0])
    // Runs between corners are smoothed on their own, so a curve never overshoots a corner.
    var start = 0
    for (i in 1 until points.size) {
      val isCorner = i < points.size - 1 && kotlin.math.PI.toFloat() - interiorAngle(points[i - 1], points[i], points[i + 1]) > corner
      if (isCorner || i == points.size - 1) {
        val run = points.subList(start, i + 1)
        for (j in 0 until run.size - 1) {
          val p0 = run[maxOf(j - 1, 0)]
          val p1 = run[j]
          val p2 = run[j + 1]
          val p3 = run[minOf(j + 2, run.size - 1)]
          val n = kotlin.math.ceil(p1.dist(p2) / step).toInt().coerceIn(1, 32)
          for (k in 1..n) {
            val t = k.toFloat() / n
            val t2 = t * t
            val t3 = t2 * t
            out.add(
              Pt(
                0.5f * (2 * p1.x + (-p0.x + p2.x) * t + (2 * p0.x - 5 * p1.x + 4 * p2.x - p3.x) * t2 + (-p0.x + 3 * p1.x - 3 * p2.x + p3.x) * t3),
                0.5f * (2 * p1.y + (-p0.y + p2.y) * t + (2 * p0.y - 5 * p1.y + 4 * p2.y - p3.y) * t2 + (-p0.y + 3 * p1.y - 3 * p2.y + p3.y) * t3),
              )
            )
          }
        }
        start = i
      }
    }
    return out
  }

  /** Ramer-Douglas-Peucker simplification of an open polyline. */
  fun simplify(points: List<Pt>, epsilon: Float): List<Pt> {
    if (points.size < 3) return points
    val keep = BooleanArray(points.size)
    keep[0] = true
    keep[points.size - 1] = true
    val stack = ArrayDeque<Pair<Int, Int>>()
    stack.addLast(0 to points.size - 1)
    while (stack.isNotEmpty()) {
      val (s, e) = stack.removeLast()
      var maxD = 0f
      var idx = -1
      for (i in s + 1 until e) {
        val d = distToSegment(points[i], points[s], points[e])
        if (d > maxD) {
          maxD = d
          idx = i
        }
      }
      if (idx != -1 && maxD > epsilon) {
        keep[idx] = true
        stack.addLast(s to idx)
        stack.addLast(idx to e)
      }
    }
    return points.filterIndexed { i, _ -> keep[i] }
  }

  /** Mean distance from each point to the nearest edge of a polyline (closed when [closed]). */
  fun meanDistToPolyline(points: List<Pt>, poly: List<Pt>, closed: Boolean): Float {
    if (poly.size < 2 || points.isEmpty()) return Float.MAX_VALUE
    val edges = if (closed) poly.size else poly.size - 1
    var sum = 0f
    for (p in points) {
      var best = Float.MAX_VALUE
      for (i in 0 until edges) {
        val d = distToSegment(p, poly[i], poly[(i + 1) % poly.size])
        if (d < best) best = d
      }
      sum += best
    }
    return sum / points.size
  }

  fun centroid(points: List<Pt>): Pt {
    var sx = 0f
    var sy = 0f
    for (p in points) {
      sx += p.x
      sy += p.y
    }
    return Pt(sx / points.size, sy / points.size)
  }

  /** Interior angle at [b] formed by [a]-[b]-[c], in radians (π for a straight line). */
  fun interiorAngle(a: Pt, b: Pt, c: Pt): Float {
    val u = (a - b).normalized()
    val v = (c - b).normalized()
    return kotlin.math.acos(u.dot(v).coerceIn(-1f, 1f))
  }

  fun pointInPolygon(p: Pt, poly: List<Pt>): Boolean {
    var inside = false
    var j = poly.size - 1
    for (i in poly.indices) {
      val pi = poly[i]
      val pj = poly[j]
      if ((pi.y > p.y) != (pj.y > p.y) && p.x < (pj.x - pi.x) * (p.y - pi.y) / (pj.y - pi.y) + pi.x) inside = !inside
      j = i
    }
    return inside
  }
}
