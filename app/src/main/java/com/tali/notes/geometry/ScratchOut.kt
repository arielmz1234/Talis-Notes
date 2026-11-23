package com.tali.notes.geometry

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Scribble to delete: a pen stroke scrubbed back and forth over writing takes what it covers off
 * the page. Off unless she turns it on in the eraser's settings (`AppSettings.scribbleDelete`).
 * The same rules as the iPad app's (TaliCore's ScratchOut), so both tell a scribble alike.
 *
 * A scribble is told from writing by its legs: it turns back on itself at least [MIN_TURNS] times,
 * each leg runs across most of the scribble, and the legs lie close together (a zigzag line drawn
 * across the page moves on too far between legs, and handwriting's legs are short and uneven).
 */
object ScratchOut {
  /** Five legs or more: over, back, over, back, over. */
  const val MIN_TURNS = 4

  /** Smallest size along its legs, on screen (dp). */
  const val MIN_LEG_SCREEN = 16f

  /** A turn counts once the pen has come back this much of the scribble's length along its legs. */
  private const val TURN_BACK = 0.3f

  /** Each leg on average runs across at least this much of the scribble. */
  private const val LEG_REACH = 0.6f

  /** How far it may move on between legs, against a leg's length. */
  private const val MAX_DRIFT = 0.25f

  /** How much of a thing has to lie under the scribble for it to go. */
  const val COVER_NEEDED = 0.6f

  /** The area [pts] scrubbed over (a convex outline, in the same units), or null when it isn't a scribble. */
  fun area(pts: List<Pt>, zoom: Float): List<Pt>? {
    if (pts.size < 12) return null
    var mx = 0f
    var my = 0f
    for (p in pts) {
      mx += p.x
      my += p.y
    }
    mx /= pts.size
    my /= pts.size
    var sxx = 0f
    var syy = 0f
    var sxy = 0f
    for (p in pts) {
      val dx = p.x - mx
      val dy = p.y - my
      sxx += dx * dx
      syy += dy * dy
      sxy += dx * dy
    }
    // Its main direction and the one across it: the legs run along one of them. Both are tried, as
    // the hand's wobble across a scribble scrubbed in place can look like many small legs.
    val angle = 0.5f * atan2(2 * sxy, sxx - syy)
    val u = Pt(cos(angle), sin(angle))
    val v = Pt(-u.y, u.x)
    val scrubbed = listOf(legs(pts, u, v), legs(pts, v, u)).any { l ->
      if (l == null || l.spans.size - 1 < MIN_TURNS || l.length * zoom < MIN_LEG_SCREEN) return@any false
      val meanSpan = l.spans.sum() / l.spans.size
      meanSpan >= LEG_REACH * l.length && l.width / l.spans.size <= MAX_DRIFT * meanSpan
    }
    if (!scrubbed) return null
    val hull = convexHull(pts)
    return if (hull.size >= 3) hull else null
  }

  /** Its legs' lengths, and its size along the legs and across them. */
  internal class Legs(val spans: List<Float>, val length: Float, val width: Float)

  /** The legs of [pts] going back and forth along [u]. */
  internal fun legs(pts: List<Pt>, u: Pt, v: Pt): Legs? {
    val proj = pts.map { it.dot(u) }
    val side = pts.map { it.dot(v) }
    val lo = proj.min()
    val hi = proj.max()
    if (hi <= lo) return null
    val length = hi - lo
    val back = TURN_BACK * length
    val spans = ArrayList<Float>()
    var dir = 0f
    var start = proj[0]
    var peak = proj[0]
    for (i in 1 until proj.size) {
      val x = proj[i]
      if (dir == 0f) {
        // Not yet under way: it sets off whichever way it first goes far enough.
        if (x - start >= back) {
          dir = 1f
          peak = x
        } else if (start - x >= back) {
          dir = -1f
          peak = x
        }
        continue
      }
      if ((x - peak) * dir > 0) {
        peak = x
      } else if ((peak - x) * dir >= back) {
        spans += abs(peak - start)
        start = peak
        peak = x
        dir = -dir
      }
    }
    if (dir != 0f) spans += abs(peak - start)
    return if (spans.isEmpty()) null else Legs(spans, length, side.max() - side.min())
  }

  /** Whether the scribble's [area] covers enough of a thing drawn as [lines] for it to go. */
  fun covers(lines: List<List<Pt>>, area: List<Pt>): Boolean {
    val box = Rect.around(area) ?: return false
    val step = max(max(box.width, box.height) / 40f, 0.5f)
    var inside = 0
    var all = 0
    fun count(p: Pt) {
      all++
      if (box.contains(p) && Geo.pointInPolygon(p, area)) inside++
    }
    for (line in lines) {
      val first = line.firstOrNull() ?: continue
      count(first)
      for (i in 1 until line.size) {
        val a = line[i - 1]
        val b = line[i]
        val parts = max(ceil(a.dist(b) / step).toInt(), 1)
        for (k in 1..parts) count(a + (b - a) * (k / parts.toFloat()))
      }
    }
    return all > 0 && inside >= COVER_NEEDED * all
  }

  /** The convex hull of [pts] (Andrew's monotone chain), counter-clockwise. */
  internal fun convexHull(pts: List<Pt>): List<Pt> {
    val sorted = pts.distinct().sortedWith(compareBy<Pt> { it.x }.thenBy { it.y })
    if (sorted.size < 3) return sorted
    fun cross(o: Pt, a: Pt, b: Pt) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
    val lower = ArrayList<Pt>()
    for (p in sorted) {
      while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], p) <= 0) lower.removeAt(lower.size - 1)
      lower += p
    }
    val upper = ArrayList<Pt>()
    for (p in sorted.asReversed()) {
      while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], p) <= 0) upper.removeAt(upper.size - 1)
      upper += p
    }
    return lower.dropLast(1) + upper.dropLast(1)
  }
}
