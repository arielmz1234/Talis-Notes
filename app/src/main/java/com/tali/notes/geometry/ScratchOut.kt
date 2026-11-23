package com.tali.notes.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

/**
 * Scribble to delete: a pen stroke scrubbed back and forth over writing takes what it covers off
 * the page. Off unless she turns it on in the eraser's settings (`AppSettings.scribbleDelete`).
 * The same rules as the iPad app's (TaliCore's ScratchOut), so both tell a scribble alike.
 *
 * A scribble is told from writing by its legs: along some direction it turns back on itself at
 * least [MIN_TURNS] times, its legs run across at least half of it on average, and they lie close
 * together (a zigzag line drawn across the page moves on too far between legs). Every direction is
 * tried, every 15 degrees, since a quick scribble is often slanted loops rather than a level scrub.
 *
 * What it takes is what its pen actually ran over: a thing must lie mostly under it, or be crossed
 * by it and not much bigger than it, and the scribble's path must pass over most of what lies under
 * it (so circling a word, however many times, leaves the word alone).
 */
object ScratchOut {
  /** Five legs or more: over, back, over, back, over. */
  const val MIN_TURNS = 4

  /** Smallest size along its legs, on screen (dp). */
  const val MIN_LEG_SCREEN = 16f

  /** A turn counts once the pen has come back this much of the scribble's length along its legs. */
  private const val TURN_BACK = 0.3f

  /** Each leg on average runs across at least this much of the scribble. */
  private const val LEG_REACH = 0.5f

  /** How far it may move on between legs, against a leg's length. */
  private const val MAX_DRIFT = 0.25f

  /** Directions tried for the legs, spread over half a turn (every 15 degrees). */
  private const val DIRECTIONS = 12

  /** How much of a thing has to lie under the scribble for it to go. */
  const val COVER_NEEDED = 0.6f

  /** How much of a thing the scribble has to cross for it to go, when it isn't mostly under it. */
  private const val CROSS_NEEDED = 0.15f

  /** How far past the scribble (against its size) a thing it crosses may reach and still go. */
  private const val REACH_PAST = 1f

  /** How much of what lies under the scribble its path must have passed over. */
  private const val PASSED_NEEDED = 0.6f

  /** "Passed over" is within this share of the gap between its legs of its path... */
  private const val PASS_GAP = 0.75f

  /** ...and never less than this, on screen (dp). */
  private const val PASS_MIN_SCREEN = 6f

  /** A scribble: the area it scrubbed over (a convex outline), its path, and how near its path counts as passed over. */
  class Scribble(val hull: List<Pt>, val path: List<Pt>, val reach: Float)

  /** [pts] as a scribble, or null when it isn't one. [zoom] is screen dp per unit. */
  fun scribble(pts: List<Pt>, zoom: Float): Scribble? {
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
    // Starting from its main direction, the legs' way is the one where they lie closest together.
    val angle = 0.5f * atan2(2 * sxy, sxx - syy)
    var gap = Float.MAX_VALUE
    for (k in 0 until DIRECTIONS) {
      val a = angle + k * PI.toFloat() / DIRECTIONS
      val u = Pt(cos(a), sin(a))
      val l = legs(pts, u, Pt(-u.y, u.x)) ?: continue
      if (l.spans.size - 1 < MIN_TURNS || l.length * zoom < MIN_LEG_SCREEN) continue
      val meanSpan = l.spans.sum() / l.spans.size
      val drift = l.width / l.spans.size
      if (meanSpan >= LEG_REACH * l.length && drift <= MAX_DRIFT * meanSpan) gap = minOf(gap, drift)
    }
    if (gap == Float.MAX_VALUE) return null
    val hull = convexHull(pts)
    return if (hull.size >= 3) Scribble(hull, pts, max(PASS_GAP * gap, PASS_MIN_SCREEN / zoom)) else null
  }

  /** The area [pts] scrubbed over (a convex outline, in the same units), or null when it isn't a scribble. */
  fun area(pts: List<Pt>, zoom: Float): List<Pt>? = scribble(pts, zoom)?.hull

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

  /**
   * Whether the scribble [s] takes a thing drawn as [lines]: mostly under it ([COVER_NEEDED]), or
   * crossed by it ([CROSS_NEEDED]) and no bigger than the scribble plus its own size ([REACH_PAST])
   * all round; and its path passed over most of the part under it ([PASSED_NEEDED]). So a word's
   * tall letters go when the scribble runs across their middles, while a long line, a box it only
   * crosses one side of, or a word it only circled stays.
   */
  fun takes(lines: List<List<Pt>>, s: Scribble): Boolean {
    val area = s.hull
    val box = Rect.around(area) ?: return false
    val near = PathIndex(s.path, s.reach)
    var inside = 0
    var passed = 0
    var all = 0
    var l = Float.MAX_VALUE
    var t = Float.MAX_VALUE
    var r = -Float.MAX_VALUE
    var b = -Float.MAX_VALUE
    eachSample(lines, max(max(box.width, box.height) / 40f, 0.5f)) { p ->
      all++
      val ranOver = near.has(p)
      // Under it: inside its outline, or right by its path (a scrub in place leaves a thin outline).
      if (ranOver || box.contains(p) && Geo.pointInPolygon(p, area)) {
        inside++
        if (ranOver) passed++
      }
      l = minOf(l, p.x)
      t = minOf(t, p.y)
      r = maxOf(r, p.x)
      b = maxOf(b, p.y)
    }
    if (all == 0 || inside == 0 || passed < PASSED_NEEDED * inside) return false
    if (inside >= COVER_NEEDED * all) return true
    val reach = box.outset(REACH_PAST * max(box.width, box.height))
    return inside >= CROSS_NEEDED * all && l >= reach.left && t >= reach.top && r <= reach.right && b <= reach.bottom
  }

  /** Whether the scribble's [area] covers enough of a thing drawn as [lines] for it to go (the iPad app's first rule). */
  fun covers(lines: List<List<Pt>>, area: List<Pt>): Boolean {
    val box = Rect.around(area) ?: return false
    var inside = 0
    var all = 0
    eachSample(lines, max(max(box.width, box.height) / 40f, 0.5f)) { p ->
      all++
      if (box.contains(p) && Geo.pointInPolygon(p, area)) inside++
    }
    return all > 0 && inside >= COVER_NEEDED * all
  }

  /** Finds whether a point lies within [reach] of [path] (filled in so no gap is wider than half of it). */
  private class PathIndex(path: List<Pt>, private val reach: Float) {
    private val cells = HashMap<Long, MutableList<Pt>>()

    init {
      eachSample(listOf(path), reach / 2) { p -> cells.getOrPut(key(cell(p.x), cell(p.y))) { ArrayList() } += p }
    }

    private fun cell(v: Float) = floor(v / reach).toInt()

    private fun key(x: Int, y: Int) = (x.toLong() shl 32) or (y.toLong() and 0xffffffffL)

    fun has(p: Pt): Boolean {
      val cx = cell(p.x)
      val cy = cell(p.y)
      for (dx in -1..1) for (dy in -1..1) cells[key(cx + dx, cy + dy)]?.forEach { q -> if (q.dist(p) <= reach) return true }
      return false
    }
  }

  /** Points along [lines] at most [step] apart. */
  private inline fun eachSample(lines: List<List<Pt>>, step: Float, f: (Pt) -> Unit) {
    for (line in lines) {
      val first = line.firstOrNull() ?: continue
      f(first)
      for (i in 1 until line.size) {
        val a = line[i - 1]
        val b = line[i]
        val parts = max(ceil(a.dist(b) / step).toInt(), 1)
        for (k in 1..parts) f(a + (b - a) * (k / parts.toFloat()))
      }
    }
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
