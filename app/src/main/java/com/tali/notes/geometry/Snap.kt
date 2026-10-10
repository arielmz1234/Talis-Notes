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

  /**
   * The ways one direction could lock on. The nearest wins; every other one that lands on the same
   * place shows too (a box beside one the same size lines up at the top, middle and bottom at once).
   */
  private class Best {
    private class Option(val score: Float, val delta: Float, val guide: SnapGuide)

    private val options = ArrayList<Option>()
    private var best: Option? = null

    fun offer(score: Float, delta: Float, guide: SnapGuide) {
      val o = Option(score, delta, guide)
      options += o
      if (score < (best?.score ?: Float.MAX_VALUE)) best = o
    }

    val score: Float
      get() = best?.score ?: Float.MAX_VALUE
    val delta: Float
      get() = best?.delta ?: 0f

    /** The winner's guides: touches when it's a touch (shown as a cross where they meet), else every lining-up at the same place. */
    fun guides(): List<SnapGuide> {
      val win = best ?: return emptyList()
      val touch = win.guide is SnapGuide.Touch
      val same = options.filter { abs(it.delta - win.delta) < SAME && (it.guide is SnapGuide.Touch) == touch }.map { it.guide }
      return if (touch) same.distinct() else mergeAligns(same.filterIsInstance<SnapGuide.Align>())
    }
  }

  /** Two snaps this close (world units) land on the same place. */
  private const val SAME = 0.05f

  /** Lining-up guides along the same line become one, reaching across everything lined up on it. */
  private fun mergeAligns(all: List<SnapGuide.Align>): List<SnapGuide> {
    val out = ArrayList<SnapGuide.Align>()
    for (g in all.sortedBy { it.at }) {
      val last = out.lastOrNull()
      if (last != null && last.vertical == g.vertical && abs(last.at - g.at) < SAME) out[out.size - 1] = last.copy(from = min(last.from, g.from), to = max(last.to, g.to))
      else out += g
    }
    return out
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
    val gx = bx.guides()
    val gy = by.guides()
    // One snap that fixes both ways (an end on an end, or on a slanted edge) wins if it's the nearest.
    val axisBest = min(bx.score, by.score)
    if (two.guide != null && two.score <= axisBest) return SnapResult(two.offset, listOf(two.guide!!))
    if (gx.isEmpty() && gy.isEmpty()) return SnapResult.NONE
    return SnapResult(Pt(if (gx.isNotEmpty()) bx.delta else 0f, if (gy.isNotEmpty()) by.delta else 0f), gx + gy)
  }

  /**
   * Resizing from the corner opposite [anchor]: the scale [s] of [box] (as it was) nudged so its far
   * side or middle lines up with something in [targets], within [tol] of where it is now. Returns
   * the scale to use and the guides of everything lined up.
   */
  fun scale(box: Rect, anchor: Pt, s: Float, targets: List<SnapFeatures>, tol: Float): Pair<Float, List<SnapGuide>> {
    fun at(v: Float, a: Float, k: Float) = a + (v - a) * k
    val now = Rect(at(box.left, anchor.x, s), at(box.top, anchor.y, s), at(box.right, anchor.x, s), at(box.bottom, anchor.y, s))
    var bestErr = tol
    var bestS = s
    for (t in targets) {
      val tb = t.box ?: continue
      // The far side and the middle move with the size; the near side stays at the anchor.
      for (v in floatArrayOf(tb.left, tb.center.x, tb.right)) {
        for ((edge, base) in listOf(now.right to box.right, now.center.x to box.center.x)) {
          val err = abs(v - edge)
          if (err < bestErr && abs(base - anchor.x) > 1e-3f) { bestErr = err; bestS = (v - anchor.x) / (base - anchor.x) }
        }
      }
      for (v in floatArrayOf(tb.top, tb.center.y, tb.bottom)) {
        for ((edge, base) in listOf(now.bottom to box.bottom, now.center.y to box.center.y)) {
          val err = abs(v - edge)
          if (err < bestErr && abs(base - anchor.y) > 1e-3f) { bestErr = err; bestS = (v - anchor.y) / (base - anchor.y) }
        }
      }
    }
    if (bestS == s || bestS <= 0f) return s to emptyList()
    // Everything that lines up at that size.
    val moved = SnapFeatures(emptyList(), emptyList(), Rect(at(box.left, anchor.x, bestS), at(box.top, anchor.y, bestS), at(box.right, anchor.x, bestS), at(box.bottom, anchor.y, bestS)))
    val lined = move(moved, targets.map { SnapFeatures(emptyList(), emptyList(), it.box) }, SAME * 4)
    return bestS to lined.guides
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

  /**
   * A dot being dragged to [p] (a shape's corner or side, a new shape's corner) lines up with the
   * sides, middles and corners of [targets], each way on its own, within [tol]. Returns where it
   * goes and the guides of everything it lines up with (none when it doesn't).
   */
  fun alignPoint(p: Pt, targets: List<SnapFeatures>, tol: Float): Pair<Pt, List<SnapGuide>> {
    val bx = Best()
    val by = Best()
    for (t in targets) {
      val tb = t.box
      if (tb != null) {
        for (v in floatArrayOf(tb.left, tb.center.x, tb.right)) {
          val d = v - p.x
          if (abs(d) < tol) bx.offer(abs(d), d, SnapGuide.Align(true, v, min(p.y, tb.top), max(p.y, tb.bottom)))
        }
        for (v in floatArrayOf(tb.top, tb.center.y, tb.bottom)) {
          val d = v - p.y
          if (abs(d) < tol) by.offer(abs(d), d, SnapGuide.Align(false, v, min(p.x, tb.left), max(p.x, tb.right)))
        }
      }
      for (q in t.points) {
        val dx = q.x - p.x
        if (abs(dx) < tol) bx.offer(abs(dx), dx, SnapGuide.Align(true, q.x, min(p.y, q.y), max(p.y, q.y)))
        val dy = q.y - p.y
        if (abs(dy) < tol) by.offer(abs(dy), dy, SnapGuide.Align(false, q.y, min(p.x, q.x), max(p.x, q.x)))
      }
    }
    val gx = bx.guides()
    val gy = by.guides()
    return Pt(p.x + if (gx.isNotEmpty()) bx.delta else 0f, p.y + if (gy.isNotEmpty()) by.delta else 0f) to (gx + gy)
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
