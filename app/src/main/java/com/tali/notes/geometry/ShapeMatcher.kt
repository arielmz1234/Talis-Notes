package com.tali.notes.geometry

import kotlin.math.ln
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Compares drawings by their point clouds (the \$P recognizer): both are resampled to the same
 * number of points, centred and scaled to a unit size, then each point is greedily paired with the
 * nearest unpaired point of the other cloud. Stroke order, direction and how many strokes a
 * template was drawn with don't matter, so one continuous pen stroke can match a template that was
 * built from several parts. Orientation does matter: a shape drawn upside down is a different shape.
 */
object ShapeMatcher {
  private const val N = 48

  /** Distances below this (in units of the drawing's size) count as a match. */
  const val MATCH = 0.055f

  class Cloud internal constructor(internal val points: Array<Pt>, val aspect: Float)

  /** Builds a cloud from one or more polylines, spreading the points along them by length. */
  fun cloud(paths: List<List<Pt>>): Cloud? {
    val usable = paths.filter { it.size >= 2 }
    val total = usable.sumOf { Geo.pathLength(it).toDouble() }.toFloat()
    if (total < 1e-3f) return null
    val box = Rect.around(usable.flatten()) ?: return null
    val step = total / N
    val out = ArrayList<Pt>(N + usable.size)
    // Walk every path at the same spacing; each path contributes at least its start point.
    var carry = 0f
    for (path in usable) {
      out += path.first()
      carry = 0f
      for (i in 1 until path.size) {
        var a = path[i - 1]
        val b = path[i]
        var d = a.dist(b)
        while (carry + d >= step && d > 0f) {
          val t = (step - carry) / d
          val q = a + (b - a) * t
          out += q
          a = q
          d = a.dist(b)
          carry = 0f
        }
        carry += d
      }
    }
    val points = resize(out, N)
    val c = Geo.centroid(points)
    val size = max(box.width, box.height).coerceAtLeast(1e-3f)
    return Cloud(Array(points.size) { (points[it] - c) / size }, (box.width + 1e-3f) / (box.height + 1e-3f))
  }

  /** Evenly drops or repeats points so every cloud has exactly [n]. */
  private fun resize(points: List<Pt>, n: Int): List<Pt> = List(n) { i -> points[(i.toLong() * points.size / n).toInt()] }

  /**
   * How different two clouds are, as a weighted mean distance between paired points in units of the
   * drawing's size: about 0.015–0.045 for the same shape drawn by hand, 0.06 and up for different ones.
   */
  fun distance(a: Cloud, b: Cloud): Float = min(greedy(a.points, b.points), greedy(b.points, a.points))

  /** Whether two clouds have a similar width-to-height ratio (within a factor of [factor]). */
  fun similarAspect(a: Cloud, b: Cloud, factor: Float = 1.7f): Boolean {
    // Very thin drawings (lines, arrows) have unstable ratios; compare how thin they are instead.
    val ra = a.aspect.coerceIn(0.08f, 12f)
    val rb = b.aspect.coerceIn(0.08f, 12f)
    return abs(ln(ra / rb)) < ln(factor)
  }

  private fun greedy(a: Array<Pt>, b: Array<Pt>): Float {
    val n = a.size
    val step = max(1, sqrt(n.toFloat()).toInt())
    var best = Float.MAX_VALUE
    var start = 0
    while (start < n) {
      best = min(best, cloudDistance(a, b, start))
      start += step
    }
    return best
  }

  private fun cloudDistance(a: Array<Pt>, b: Array<Pt>, start: Int): Float {
    val n = a.size
    val matched = BooleanArray(n)
    var sum = 0f
    var weights = 0f
    var i = start
    do {
      var bestJ = -1
      var bestD = Float.MAX_VALUE
      for (j in 0 until n) {
        if (matched[j]) continue
        val d = a[i].dist(b[j])
        if (d < bestD) {
          bestD = d
          bestJ = j
        }
      }
      matched[bestJ] = true
      val w = 1f - ((i - start + n) % n).toFloat() / n
      sum += w * bestD
      weights += w
      i = (i + 1) % n
    } while (i != start)
    return sum / weights
  }
}
