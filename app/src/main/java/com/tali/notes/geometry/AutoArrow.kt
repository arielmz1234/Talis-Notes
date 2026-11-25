package com.tali.notes.geometry

import com.tali.notes.model.LineShape
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max

/**
 * *Auto arrow* (the Shapes panel's Style tab, `AppSettings.autoArrow`): an arrow written by hand
 * becomes a clean one as soon as the pen lifts, with no hold. The arrow is a straight shaft and a
 * head at one end of it, the head drawn as a V or a triangle. Either one stroke (the shaft, then
 * the head without lifting) or two (a straight line, then the head as the very next stroke).
 *
 * Everything is in world units. Shafts are at least [MIN_SHAFT] long, so a dash and a ">" in a
 * line of writing stay as written.
 */
object AutoArrow {
  /** About 6 mm on an A4 page: shorter is writing (a minus sign, a dash), not an arrow. */
  const val MIN_SHAFT = 40f
  /** The head's barbs leave the tip between these angles from the shaft. */
  private val BARB_MIN = cos(Math.toRadians(10.0)).toFloat()
  private val BARB_MAX = cos(Math.toRadians(80.0)).toFloat()
  /** Head lengths go in steps of this (world units: about 1.6 mm on an A4 page), rounded down. */
  const val HEAD_STEP = 10f
  /** A head drawn within this share of the last arrow's head is given exactly that head. */
  const val HEAD_LOCK = 0.2f

  /** A straight stroke, which a head drawn next can make into an arrow: its two ends, or null. */
  fun straight(raw: List<Pt>): Pair<Pt, Pt>? {
    val points = dedupe(raw)
    if (points.size < 2) return null
    val a = points.first()
    val b = points.last()
    val len = a.dist(b)
    if (len < MIN_SHAFT) return null
    val dev = points.maxOf { Geo.distToLine(it, a, b) }
    return if (dev < max(0.06f * len, 3f) && Geo.pathLength(points) < 1.25f * len) a to b else null
  }

  /** One stroke: a straight shaft, then without lifting a head (a V or a triangle) at its end. */
  fun oneStroke(raw: List<Pt>, lastHead: Float = 0f): LineShape? {
    val points = dedupe(raw)
    if (points.size < 4) return null
    val box = Rect.around(points) ?: return null
    val v = Geo.simplify(points, max(0.04f * hypot(box.width, box.height), 3f))
    if (v.size < 4) return null
    val start = v[0]
    val end = v[1]
    val shaft = start.dist(end)
    if (shaft < MIN_SHAFT) return null
    val at = points.indexOf(end)
    if (at < 1) return null
    if (points.subList(0, at + 1).any { Geo.distToSegment(it, start, end) > max(0.06f * shaft, 3f) }) return null
    return headAt(start, end, points.subList(at, points.size), lastHead)
  }

  /**
   * Two strokes: [head] drawn just after the straight line [a]–[b], at either of its ends. The
   * arrow points the way the head does (a head at [a] makes it point back towards [a]).
   */
  fun twoStrokes(a: Pt, b: Pt, head: List<Pt>, lastHead: Float = 0f): LineShape? {
    val h = dedupe(head)
    return headAt(a, b, h, lastHead) ?: headAt(b, a, h, lastHead)
  }

  /**
   * The arrow from [start] through [end] when [head] is an arrow's head there: a V or a triangle
   * pointing on along the shaft, one barb either side of it, small beside the shaft, with the
   * shaft's end on its axis (at the tip, the base or between). Its tip lands on the shaft's line.
   */
  fun headAt(start: Pt, end: Pt, head: List<Pt>, lastHead: Float = 0f): LineShape? {
    val shaft = start.dist(end)
    if (shaft < MIN_SHAFT || head.size < 3) return null
    val u = (end - start) / shaft
    val n = Pt(-u.y, u.x)
    fun along(p: Pt) = (p - end).dot(u)
    fun across(p: Pt) = (p - end).dot(n)
    val tip = head.maxBy { along(it) }
    val left = head.maxBy { across(it) }
    val right = head.minBy { across(it) }
    val width = across(left) - across(right)
    val box = Rect.around(head) ?: return null
    // A head's size: not a speck, and not a second shape as big as the shaft.
    if (width < max(10f, 0.05f * shaft) || max(box.width, box.height) > 0.75f * shaft) return null
    // One barb on either side of the shaft, the tip on its line.
    if (across(left) < 0.2f * width || across(right) > -0.2f * width) return null
    if (kotlin.math.abs(across(tip)) > 0.3f * width) return null
    // Both barbs run back from the tip, neither straight back nor square to the shaft.
    val back = Pt(-u.x, -u.y)
    for (side in listOf(left, right)) {
      val c = back.dot((side - tip).normalized())
      if (c > BARB_MIN || c < BARB_MAX) return null
    }
    // The shaft ends on the head's axis: at its tip, its base, or anywhere between.
    val base = (left + right) / 2f
    if (Geo.distToSegment(end, base, tip) > max(0.3f * width, 6f)) return null
    // Drawn as the head's sides (a V, or a triangle with its base), nothing else.
    val tol = max(0.15f * width, 3f)
    val edges = listOf(tip to left, tip to right, left to right, end to tip)
    if (head.any { p -> edges.none { (x, y) -> Geo.distToSegment(p, x, y) < tol } }) return null
    val point = end + u * along(tip)
    // The clean head is a standard arrowhead (equal barbs, 30° each side) as long as hers.
    val barbs = (left.dist(tip) + right.dist(tip)) / 2f
    return ShapeRecognizer.snappedLine(start, point, arrowEnd = true).copy(head = headSize(barbs, lastHead))
  }

  /**
   * The head length for barbs [drawn] long: the last arrow's head ([last], 0 = none yet) when it's
   * within [HEAD_LOCK] of it, so arrows drawn alike come out exactly alike; otherwise rounded down to
   * a step of [HEAD_STEP].
   */
  fun headSize(drawn: Float, last: Float = 0f): Float {
    if (last > 0f && kotlin.math.abs(drawn - last) <= last * HEAD_LOCK) return last
    return max(HEAD_STEP, kotlin.math.floor(drawn / HEAD_STEP) * HEAD_STEP)
  }

  private fun dedupe(points: List<Pt>): List<Pt> {
    val out = ArrayList<Pt>(points.size)
    for (p in points) if (out.isEmpty() || out.last().dist(p) > 0.5f) out.add(p)
    return out
  }
}
