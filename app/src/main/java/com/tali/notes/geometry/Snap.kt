package com.tali.notes.geometry

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * What something offers to snap to, in world units: its key [points] (corners, ends, middles,
 * centres), its [segments] (straight edges, and short pieces of curves) and its [box] (the drawn
 * outline's extent, without the pen's thickness).
 */
class SnapFeatures(val points: List<Pt>, val segments: List<Pair<Pt, Pt>>, val box: Rect?) {
  fun moved(dx: Float, dy: Float): SnapFeatures {
    if (dx == 0f && dy == 0f) return this
    val d = Pt(dx, dy)
    return SnapFeatures(points.map { it + d }, segments.map { (a, b) -> (a + d) to (b + d) }, box?.let { Rect(it.left + dx, it.top + dy, it.right + dx, it.bottom + dy) })
  }

  companion object {
    fun of(parts: List<SnapFeatures>): SnapFeatures =
      SnapFeatures(parts.flatMap { it.points }, parts.flatMap { it.segments }, parts.mapNotNull { it.box }.reduceOrNull(Rect::union))
  }
}

/** A guide shown while something is held snapped. */
sealed interface SnapGuide {
  /** Two things lined up: a line along x = [at] ([vertical]) or y = [at], reaching [from]..[to] the other way. */
  data class Align(val vertical: Boolean, val at: Float, val from: Float, val to: Float) : SnapGuide

  /** Touching: a point resting on another shape's point or edge. */
  data class Touch(val at: Pt) : SnapGuide
}

/** How far to nudge what's being moved, and the guides that show why. */
class SnapResult(val offset: Pt, val guides: List<SnapGuide>) {
  companion object {
    val NONE = SnapResult(Pt.ZERO, emptyList())
  }
}

/**
 * Magnetic snapping for shapes, like a drawing app's smart guides. Moving something, it locks on
 * when it comes within reach of another shape: an end or corner onto another's corner or edge (a
 * line meeting a line, a support sitting on a beam), and its edges or middle lining up with
 * another's top, bottom, sides or middle. Each direction snaps on its own, so a box can sit on a
 * line and line up with a neighbour at the same time.
 */
object Snap {
  /** Edges this close to level or upright (as a share of their length) count as straight across or up. */
  private const val AXIS = 0.02f

  /** A touch beats a lining-up at the same distance; an end on an end beats an end on an edge. */
  private const val TOUCH_BIAS = 0.75f
  private const val POINT_BIAS = 0.5f

  private class Best(var score: Float = Float.MAX_VALUE, var delta: Float = 0f, var guide: SnapGuide? = null) {
    fun offer(score: Float, delta: Float, guide: SnapGuide) {
      if (score < this.score) {
        this.score = score
        this.delta = delta
        this.guide = guide
      }
    }
  }

  private class Best2(var score: Float = Float.MAX_VALUE, var offset: Pt = Pt.ZERO, var guide: SnapGuide? = null)

  private fun vertical(a: Pt, b: Pt) = abs(a.x - b.x) <= abs(a.y - b.y) * AXIS
  private fun horizontal(a: Pt, b: Pt) = abs(a.y - b.y) <= abs(a.x - b.x) * AXIS

  /** The nearest point to [p] on the segment [a]–[b]. */
  fun nearestOn(p: Pt, a: Pt, b: Pt): Pt {
    val ab = b - a
    val len = ab.dot(ab)
    if (len < 1e-6f) return a
    val t = ((p - a).dot(ab) / len).coerceIn(0f, 1f)
    return a + ab * t
  }

  /** How far to move [moving] (already at its unsnapped place) to lock onto [targets], within [tol]. */
  fun move(moving: SnapFeatures, targets: List<SnapFeatures>, tol: Float): SnapResult {
    if (targets.isEmpty()) return SnapResult.NONE
    val bx = Best()
    val by = Best()
    val two = Best2()
    val mb = moving.box
    val reach = mb?.outset(tol)
    for (t in targets) {
      val tb = t.box
      // Lining up: sides and middles, each way.
      if (mb != null && tb != null) {
        val mx = floatArrayOf(mb.left, mb.center.x, mb.right)
        val tx = floatArrayOf(tb.left, tb.center.x, tb.right)
        for (m in mx) for (v in tx) {
          val d = v - m
          if (abs(d) < tol) bx.offer(abs(d), d, SnapGuide.Align(true, v, min(mb.top, tb.top), max(mb.bottom, tb.bottom)))
        }
        val my = floatArrayOf(mb.top, mb.center.y, mb.bottom)
        val ty = floatArrayOf(tb.top, tb.center.y, tb.bottom)
        for (m in my) for (v in ty) {
          val d = v - m
          if (abs(d) < tol) by.offer(abs(d), d, SnapGuide.Align(false, v, min(mb.left, tb.left), max(mb.right, tb.right)))
        }
      }
      // Touching only matters for things within reach.
      if (reach != null && tb != null && !reach.intersects(tb.outset(tol))) continue
      // An end or corner onto an end or corner.
      for (p in moving.points) for (q in t.points) {
        val d = p.dist(q)
        if (d < tol && d * POINT_BIAS < two.score) {
          two.score = d * POINT_BIAS
          two.offset = q - p
          two.guide = SnapGuide.Touch(q)
        }
      }
      // Moving points onto the target's edges, and the target's points onto moving edges.
      for ((a, b) in t.segments) for (p in moving.points) touch(p, a, b, tol, bx, by, two, sign = 1f)
      for ((a, b) in moving.segments) for (q in t.points) touch(q, a, b, tol, bx, by, two, sign = -1f)
    }
    val gx = bx.guide
    val gy = by.guide
    // One snap that fixes both ways (an end on an end, or on a slanted edge) wins if it's the nearest.
    val axisBest = min(bx.score, by.score)
    if (two.guide != null && two.score <= axisBest) return SnapResult(two.offset, listOf(two.guide!!))
    if (gx == null && gy == null) return SnapResult.NONE
    return SnapResult(Pt(if (gx != null) bx.delta else 0f, if (gy != null) by.delta else 0f), listOfNotNull(gx, gy))
  }

  /**
   * Point [p] against segment [a]–[b] (sign 1: [p] moves; -1: the segment moves). Level and
   * upright edges snap one way only (so the other way can still line up); slanted ones both ways.
   */
  private fun touch(p: Pt, a: Pt, b: Pt, tol: Float, bx: Best, by: Best, two: Best2, sign: Float) {
    if (vertical(a, b)) {
      if (p.y < min(a.y, b.y) - tol || p.y > max(a.y, b.y) + tol) return
      val x = (a.x + b.x) / 2f
      val d = x - p.x
      if (abs(d) < tol) bx.offer(abs(d) * TOUCH_BIAS, d * sign, SnapGuide.Touch(Pt(if (sign > 0) x else p.x, p.y.coerceIn(min(a.y, b.y), max(a.y, b.y)))))
    } else if (horizontal(a, b)) {
      if (p.x < min(a.x, b.x) - tol || p.x > max(a.x, b.x) + tol) return
      val y = (a.y + b.y) / 2f
      val d = y - p.y
      if (abs(d) < tol) by.offer(abs(d) * TOUCH_BIAS, d * sign, SnapGuide.Touch(Pt(p.x.coerceIn(min(a.x, b.x), max(a.x, b.x)), if (sign > 0) y else p.y)))
    } else {
      val q = nearestOn(p, a, b)
      val d = p.dist(q)
      if (d < tol && d * TOUCH_BIAS < two.score) {
        two.score = d * TOUCH_BIAS
        two.offset = (q - p) * sign
        two.guide = SnapGuide.Touch(if (sign > 0) q else p)
      }
    }
  }

  /** The nearest end or corner of [targets] within [tol] of [p]. */
  fun corner(p: Pt, targets: List<SnapFeatures>, tol: Float): Pt? {
    var best: Pt? = null
    var dist = tol
    for (t in targets) for (q in t.points) {
      val d = p.dist(q)
      if (d < dist) {
        dist = d
        best = q
      }
    }
    return best
  }

  /**
   * Where the line from [from] through [to] meets an edge of [targets] within [tol] of [to]: a line
   * being drawn keeps its direction (its tidy angle) and just stops on the edge it reaches.
   */
  fun alongLine(from: Pt, to: Pt, targets: List<SnapFeatures>, tol: Float): Pt? {
    val dir = to - from
    if (dir.length() < 1e-3f) return null
    var best: Pt? = null
    var dist = tol
    for (t in targets) {
      val tb = t.box
      if (tb != null && !tb.outset(tol).contains(to)) continue
      for ((a, b) in t.segments) {
        val e = b - a
        val den = dir.cross(e)
        if (abs(den) < 1e-6f) continue
        // from + dir·s = a + e·u, with the meeting point on the edge (0..1 along it).
        val u = (a - from).cross(dir) / den
        if (u < -1e-4f || u > 1f + 1e-4f) continue
        val q = a + e * u.coerceIn(0f, 1f)
        val d = q.dist(to)
        if (d < dist) {
          dist = d
          best = q
        }
      }
    }
    return best
  }

  /**
   * Where a single point being dragged (a line's end, a shape's dot, where a new shape starts)
   * locks on: another shape's end or corner first, else the nearest place on an edge, within [tol].
   */
  fun point(p: Pt, targets: List<SnapFeatures>, tol: Float): Pt? {
    var best: Pt? = null
    var score = Float.MAX_VALUE
    for (t in targets) {
      val tb = t.box
      if (tb != null && !tb.outset(tol).contains(p)) continue
      for (q in t.points) {
        val d = p.dist(q)
        if (d < tol && d * POINT_BIAS < score) {
          score = d * POINT_BIAS
          best = q
        }
      }
      for ((a, b) in t.segments) {
        val q = nearestOn(p, a, b)
        val d = p.dist(q)
        if (d < tol && d < score) {
          score = d
          best = q
        }
      }
    }
    return best
  }
}
