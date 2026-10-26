package com.tali.notes.geometry

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * What something offers to snap to, in world units: its key [points] (corners, ends, middles,
 * centres), its [segments] (straight edges, and short pieces of curves) and its [box] (the drawn
 * outline's extent, without the pen's thickness). [middleOnly]: only the middle of its box lines
 * up (handwriting picked up with the lasso), and nothing of it touches.
 */
class SnapFeatures(val points: List<Pt>, val segments: List<Pair<Pt, Pt>>, val box: Rect?, val middleOnly: Boolean = false) {
  fun moved(dx: Float, dy: Float): SnapFeatures {
    if (dx == 0f && dy == 0f) return this
    val d = Pt(dx, dy)
    return SnapFeatures(points.map { it + d }, segments.map { (a, b) -> (a + d) to (b + d) }, box?.let { Rect(it.left + dx, it.top + dy, it.right + dx, it.bottom + dy) }, middleOnly)
  }

  companion object {
    fun of(parts: List<SnapFeatures>): SnapFeatures =
      SnapFeatures(parts.flatMap { it.points }, parts.flatMap { it.segments }, parts.mapNotNull { it.box }.reduceOrNull(Rect::union))

    /** Just the middle of [box], for handwriting: it lines up its middle with shapes, and nothing lines up with it. */
    fun middleOf(box: Rect) = SnapFeatures(emptyList(), emptyList(), box, middleOnly = true)
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
 * One drag's snapping: how far things catch, and what she has pulled free of.
 *
 * Touching (an end or corner onto another shape's end, corner or edge) and middles lining up (a
 * shape centred on another, or inside it) catch from [contact] away and hold firmly; sides lining
 * up with something at a distance are softer, catching only from [align]. When she pulls something out of a lock, that lock lets go for the rest of the drag
 * (she wanted it near, not on), until she moves well away from it ([REARM] times [contact]); after
 * that it catches again, so coming back to it on purpose still works.
 *
 * Each frame: [begin], then any of [Snap]'s functions, then [end]. Each lock is known by a key
 * naming the two things that meet (which corner of what's moving, which edge of which shape), so
 * pulling free of one doesn't stop anything else catching.
 */
class SnapSession(contact: Float, align: Float = contact) {
  var contact = contact
    private set
  var align = align
    private set

  /** Locks shown last frame. */
  private var engaged: Set<Long> = emptySet()

  /** Locks she pulled out of: they don't catch until she moves well away from them. */
  private val escaped = HashSet<Long>()

  /** This frame: how near each lock within reach came, and how far it catches from. */
  private val seen = HashMap<Long, Float>()
  private val catchOf = HashMap<Long, Float>()
  private val used = HashSet<Long>()

  /** How far away locks are still watched (to notice pulling out of them, and moving away). */
  val reach: Float
    get() = contact * REARM

  /** Starts a frame; [contact] and [align] follow the zoom. */
  fun begin(contact: Float = this.contact, align: Float = this.align) {
    this.contact = contact
    this.align = align
    seen.clear()
    catchOf.clear()
    used.clear()
  }

  /** Whether lock [key], [dist] away, catches: from [contact] when [firm] (a touch, or middles lining up), else from [align]. */
  fun catches(key: Long, dist: Float, firm: Boolean): Boolean {
    val tol = if (firm) contact else align
    val before = seen[key]
    if (before == null || dist < before) {
      seen[key] = dist
      catchOf[key] = tol
    }
    return dist < tol && key !in escaped
  }

  /** Lock [key] is what's holding (it's shown). */
  fun use(key: Long) {
    used += key
  }

  /**
   * Ends a frame: a lock held last frame that's now further than it catches from was pulled free
   * of, and lets go; a lock let go of is back once she's moved away from it.
   */
  fun end() {
    for (k in engaged) if (k !in used) {
      val d = seen[k]
      if (d == null || d >= (catchOf[k] ?: 0f)) escaped += k
    }
    escaped.removeAll { k -> (seen[k] ?: Float.MAX_VALUE) > reach }
    engaged = HashSet(used)
  }

  /** Whether anything is holding this frame. */
  val holding: Boolean
    get() = used.isNotEmpty()

  companion object {
    /** A lock she pulled free of catches again once she's this many [contact]s from it. */
    const val REARM = 3f
  }
}

/**
 * Magnetic snapping for shapes, like a drawing app's smart guides. Moving something, it locks on
 * when it comes within reach of another shape: an end or corner onto another's corner or edge (a
 * line meeting a line, a support sitting on a beam), and its edges or middle lining up with
 * another's top, bottom, sides or middle. Each direction snaps on its own, so a box can sit on a
 * line and line up with a neighbour at the same time. See [SnapSession] for how firmly each kind
 * holds and how she pulls free.
 */
object Snap {
  /** Edges this close to level or upright (as a share of their length) count as straight across or up. */
  private const val AXIS = 0.02f

  /** A touch beats a lining-up at the same distance; an end on an end beats an end on an edge. */
  private const val TOUCH_BIAS = 0.75f
  private const val POINT_BIAS = 0.5f

  /** Middles lining up (a shape centred on another, or inside it) hold firmly and win over a side at the same distance. */
  private const val MIDDLE_BIAS = 0.8f

  // What kind of lock a key names.
  private const val K_ALIGN_X = 1
  private const val K_ALIGN_Y = 2
  private const val K_POINT = 3
  private const val K_EDGE = 4
  private const val K_EDGE_BACK = 5
  private const val K_SCALE_X = 6
  private const val K_SCALE_Y = 7
  private const val K_DOT_X = 8
  private const val K_DOT_Y = 9

  /**
   * The key of a lock onto the point [q] (by where it is, so an edge whose nearest place is its own
   * end counts as that corner: pulled free of a corner, the edges meeting there don't catch it back).
   */
  private fun pointKey(q: Pt, moving: Int = 0) = key(K_POINT, Math.round(q.x * 8f), Math.round(q.y * 8f), moving)

  /** The key of a lock onto edge [si] of target [ti], or onto its end when [q] is one. */
  private fun edgeKey(q: Pt, a: Pt, b: Pt, kind: Int, ti: Int, si: Int, moving: Int = 0) =
    if (q == a || q == b) pointKey(q, moving) else key(kind, ti, si, moving)

  /** A lock's key: its kind, and the indices naming what meets what. */
  private fun key(kind: Int, a: Int, b: Int = 0, c: Int = 0, d: Int = 0): Long {
    var h = kind.toLong()
    h = h * 1_000_003L + a
    h = h * 1_000_003L + b
    h = h * 1_000_003L + c
    h = h * 1_000_003L + d
    return h
  }

  /**
   * The ways one direction could lock on. The nearest wins; every other one that lands on the same
   * place shows too (a box beside one the same size lines up at the top, middle and bottom at once).
   */
  private class Best {
    class Option(val score: Float, val delta: Float, val guide: SnapGuide, val key: Long)

    private val options = ArrayList<Option>()
    var best: Option? = null
      private set

    fun offer(score: Float, delta: Float, guide: SnapGuide, key: Long) {
      val o = Option(score, delta, guide, key)
      options += o
      if (score < (best?.score ?: Float.MAX_VALUE)) best = o
    }

    val score: Float
      get() = best?.score ?: Float.MAX_VALUE
    val delta: Float
      get() = best?.delta ?: 0f
    val touch: Boolean
      get() = best?.guide is SnapGuide.Touch

    /** The winner when it's a touch (one cross, where they meet), else every lining-up at the same place. */
    fun winners(): List<Option> {
      val win = best ?: return emptyList()
      if (win.guide is SnapGuide.Touch) return listOf(win)
      return options.filter { abs(it.delta - win.delta) < SAME && it.guide is SnapGuide.Align }
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

  /** Shows [picked] (using their locks) and returns their guides: lining-ups merged, at most one cross. */
  private fun show(picked: List<Best.Option>, s: SnapSession): List<SnapGuide> {
    picked.forEach { s.use(it.key) }
    val touch = picked.filter { it.guide is SnapGuide.Touch }.minByOrNull { it.score }
    return mergeAligns(picked.mapNotNull { it.guide as? SnapGuide.Align }) + listOfNotNull(touch?.guide)
  }

  private class Both(var score: Float = Float.MAX_VALUE, var offset: Pt = Pt.ZERO, var guide: SnapGuide? = null, var key: Long = 0L)

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

  fun move(moving: SnapFeatures, targets: List<SnapFeatures>, tol: Float): SnapResult = move(moving, targets, SnapSession(tol))

  /** How far to move [moving] (already at its unsnapped place) to lock onto [targets]. */
  fun move(moving: SnapFeatures, targets: List<SnapFeatures>, s: SnapSession): SnapResult {
    if (targets.isEmpty()) return SnapResult.NONE
    val bx = Best()
    val by = Best()
    val two = Both()
    val mb = moving.box
    val reach = s.reach
    val near = mb?.outset(reach)
    targets.forEachIndexed { ti, t ->
      val tb = t.box
      // Lining up: sides and middles, each way. Handwriting lines up only its own middle, with
      // the other shape's sides and middle alike.
      if (mb != null && tb != null) {
        val mx = if (moving.middleOnly) floatArrayOf(mb.center.x) else floatArrayOf(mb.left, mb.center.x, mb.right)
        val tx = floatArrayOf(tb.left, tb.center.x, tb.right)
        for (mi in mx.indices) for (vi in tx.indices) {
          val d = tx[vi] - mx[mi]
          val middle = vi == 1 && (mx.size == 1 || mi == 1)
          if (abs(d) < reach && s.catches(key(K_ALIGN_X, ti, mi, vi), abs(d), middle)) bx.offer(abs(d) * if (middle) MIDDLE_BIAS else 1f, d, SnapGuide.Align(true, tx[vi], min(mb.top, tb.top), max(mb.bottom, tb.bottom)), key(K_ALIGN_X, ti, mi, vi))
        }
        val my = if (moving.middleOnly) floatArrayOf(mb.center.y) else floatArrayOf(mb.top, mb.center.y, mb.bottom)
        val ty = floatArrayOf(tb.top, tb.center.y, tb.bottom)
        for (mi in my.indices) for (vi in ty.indices) {
          val d = ty[vi] - my[mi]
          val middle = vi == 1 && (my.size == 1 || mi == 1)
          if (abs(d) < reach && s.catches(key(K_ALIGN_Y, ti, mi, vi), abs(d), middle)) by.offer(abs(d) * if (middle) MIDDLE_BIAS else 1f, d, SnapGuide.Align(false, ty[vi], min(mb.left, tb.left), max(mb.right, tb.right)), key(K_ALIGN_Y, ti, mi, vi))
        }
      }
      // Touching only matters for things within reach.
      if (near != null && tb != null && !near.intersects(tb.outset(reach))) return@forEachIndexed
      // An end or corner onto an end or corner.
      moving.points.forEachIndexed { pi, p ->
        t.points.forEachIndexed { qi, q ->
          val d = p.dist(q)
          val k = pointKey(q, pi + 1)
          if (d < reach && s.catches(k, d, true) && d * POINT_BIAS < two.score) {
            two.score = d * POINT_BIAS
            two.offset = q - p
            two.guide = SnapGuide.Touch(q)
            two.key = k
          }
        }
      }
      // Moving points onto the target's edges, and the target's points onto moving edges.
      t.segments.forEachIndexed { si, (a, b) -> moving.points.forEachIndexed { pi, p -> touch(p, a, b, s, key(K_EDGE, ti, si, pi), pi, bx, by, two, sign = 1f) } }
      moving.segments.forEachIndexed { si, (a, b) -> t.points.forEachIndexed { qi, q -> touch(q, a, b, s, key(K_EDGE_BACK, ti, qi, si), -1, bx, by, two, sign = -1f) } }
    }
    // One snap that fixes both ways (an end on an end, or on a slanted edge) wins if it's the nearest.
    if (two.guide != null && two.score <= min(bx.score, by.score)) {
      s.use(two.key)
      return SnapResult(two.offset, listOf(two.guide!!))
    }
    val wx = bx.winners()
    val wy = by.winners()
    if (wx.isEmpty() && wy.isEmpty()) return SnapResult.NONE
    return SnapResult(Pt(if (wx.isNotEmpty()) bx.delta else 0f, if (wy.isNotEmpty()) by.delta else 0f), show(wx + wy, s))
  }

  fun scale(box: Rect, anchor: Pt, s: Float, targets: List<SnapFeatures>, tol: Float): Pair<Float, List<SnapGuide>> = scale(box, anchor, s, targets, SnapSession(tol))

  /**
   * Resizing from the corner opposite [anchor]: the scale [k] of [box] (as it was) nudged so its far
   * side or middle lines up with something in [targets] (only its middle when [middleOnly], for
   * handwriting). Returns the scale to use and the guides of everything lined up. Only a way the
   * box really reaches counts: a level line's height is next to nothing, and lining that up would
   * throw its length wildly.
   */
  fun scale(box: Rect, anchor: Pt, k: Float, targets: List<SnapFeatures>, s: SnapSession, middleOnly: Boolean = false): Pair<Float, List<SnapGuide>> {
    fun at(v: Float, a: Float, f: Float) = a + (v - a) * f
    val now = Rect(at(box.left, anchor.x, k), at(box.top, anchor.y, k), at(box.right, anchor.x, k), at(box.bottom, anchor.y, k))
    val extent = max(box.right - anchor.x, box.bottom - anchor.y)
    val lever = max(extent * 0.2f, s.contact)
    var bestErr = Float.MAX_VALUE
    var bestS = k
    var bestKey = 0L
    targets.forEachIndexed { ti, t ->
      val tb = t.box ?: return@forEachIndexed
      // The far side and the middle move with the size; the near side stays at the anchor.
      val xs = floatArrayOf(tb.left, tb.center.x, tb.right)
      val ys = floatArrayOf(tb.top, tb.center.y, tb.bottom)
      for (vi in 0..2) {
        for ((ei, pair) in listOf(now.right to box.right, now.center.x to box.center.x).withIndex()) {
          val (edge, base) = pair
          if (middleOnly && ei == 0) continue
          if (abs(base - anchor.x) < lever) continue
          val err = abs(xs[vi] - edge)
          val kk = key(K_SCALE_X, ti, vi, ei)
          if (err < s.reach && s.catches(kk, err, vi == 1 && ei == 1) && err < bestErr) { bestErr = err; bestS = (xs[vi] - anchor.x) / (base - anchor.x); bestKey = kk }
        }
        for ((ei, pair) in listOf(now.bottom to box.bottom, now.center.y to box.center.y).withIndex()) {
          val (edge, base) = pair
          if (middleOnly && ei == 0) continue
          if (abs(base - anchor.y) < lever) continue
          val err = abs(ys[vi] - edge)
          val kk = key(K_SCALE_Y, ti, vi, ei)
          if (err < s.reach && s.catches(kk, err, vi == 1 && ei == 1) && err < bestErr) { bestErr = err; bestS = (ys[vi] - anchor.y) / (base - anchor.y); bestKey = kk }
        }
      }
    }
    if (bestErr == Float.MAX_VALUE || bestS <= 0f) return k to emptyList()
    s.use(bestKey)
    // Everything that lines up at that size.
    val moved = SnapFeatures(emptyList(), emptyList(), Rect(at(box.left, anchor.x, bestS), at(box.top, anchor.y, bestS), at(box.right, anchor.x, bestS), at(box.bottom, anchor.y, bestS)), middleOnly)
    val lined = move(moved, targets.map { SnapFeatures(emptyList(), emptyList(), it.box) }, SAME * 4)
    return bestS to lined.guides
  }

  /**
   * Point [p] against segment [a]–[b] (sign 1: [p] moves; -1: the segment moves). Level and
   * upright edges snap one way only (so the other way can still line up); slanted ones both ways.
   * [pi] is which of the moving points [p] is (when it's the one moving).
   */
  private fun touch(p: Pt, a: Pt, b: Pt, s: SnapSession, k: Long, pi: Int, bx: Best, by: Best, two: Both, sign: Float) {
    val reach = s.reach
    if (vertical(a, b)) {
      if (p.y < min(a.y, b.y) - s.contact || p.y > max(a.y, b.y) + s.contact) return
      val x = (a.x + b.x) / 2f
      val d = x - p.x
      if (abs(d) < reach && s.catches(k, abs(d), true)) bx.offer(abs(d) * TOUCH_BIAS, d * sign, SnapGuide.Touch(Pt(if (sign > 0) x else p.x, p.y.coerceIn(min(a.y, b.y), max(a.y, b.y)))), k)
    } else if (horizontal(a, b)) {
      if (p.x < min(a.x, b.x) - s.contact || p.x > max(a.x, b.x) + s.contact) return
      val y = (a.y + b.y) / 2f
      val d = y - p.y
      if (abs(d) < reach && s.catches(k, abs(d), true)) by.offer(abs(d) * TOUCH_BIAS, d * sign, SnapGuide.Touch(Pt(p.x.coerceIn(min(a.x, b.x), max(a.x, b.x)), if (sign > 0) y else p.y)), k)
    } else {
      val q = nearestOn(p, a, b)
      val d = p.dist(q)
      // Onto the edge's end: the same lock as onto that corner.
      val kk = if (sign > 0 && (q == a || q == b)) pointKey(q, pi + 1) else k
      if (d < reach && s.catches(kk, d, true) && d * TOUCH_BIAS < two.score) {
        two.score = d * TOUCH_BIAS
        two.offset = (q - p) * sign
        two.guide = SnapGuide.Touch(if (sign > 0) q else p)
        two.key = kk
      }
    }
  }

  fun alignPoint(p: Pt, targets: List<SnapFeatures>, tol: Float): Pair<Pt, List<SnapGuide>> = alignPoint(p, targets, SnapSession(tol))

  /**
   * A dot being dragged to [p] (a shape's corner or side, a new shape's corner) lines up with the
   * sides, middles and corners of [targets], each way on its own. Returns where it goes and the
   * guides of everything it lines up with (none when it doesn't).
   */
  fun alignPoint(p: Pt, targets: List<SnapFeatures>, s: SnapSession): Pair<Pt, List<SnapGuide>> {
    val bx = Best()
    val by = Best()
    val reach = s.reach
    targets.forEachIndexed { ti, t ->
      val tb = t.box
      if (tb != null) {
        floatArrayOf(tb.left, tb.center.x, tb.right).forEachIndexed { vi, v ->
          val d = v - p.x
          val k = key(K_ALIGN_X, ti, vi)
          if (abs(d) < reach && s.catches(k, abs(d), vi == 1)) bx.offer(abs(d) * if (vi == 1) MIDDLE_BIAS else 1f, d, SnapGuide.Align(true, v, min(p.y, tb.top), max(p.y, tb.bottom)), k)
        }
        floatArrayOf(tb.top, tb.center.y, tb.bottom).forEachIndexed { vi, v ->
          val d = v - p.y
          val k = key(K_ALIGN_Y, ti, vi)
          if (abs(d) < reach && s.catches(k, abs(d), vi == 1)) by.offer(abs(d) * if (vi == 1) MIDDLE_BIAS else 1f, d, SnapGuide.Align(false, v, min(p.x, tb.left), max(p.x, tb.right)), k)
        }
      }
      t.points.forEachIndexed { qi, q ->
        val dx = q.x - p.x
        val kx = key(K_DOT_X, ti, qi)
        if (abs(dx) < reach && s.catches(kx, abs(dx), false)) bx.offer(abs(dx), dx, SnapGuide.Align(true, q.x, min(p.y, q.y), max(p.y, q.y)), kx)
        val dy = q.y - p.y
        val ky = key(K_DOT_Y, ti, qi)
        if (abs(dy) < reach && s.catches(ky, abs(dy), false)) by.offer(abs(dy), dy, SnapGuide.Align(false, q.y, min(p.x, q.x), max(p.x, q.x)), ky)
      }
    }
    val wx = bx.winners()
    val wy = by.winners()
    return Pt(p.x + if (wx.isNotEmpty()) bx.delta else 0f, p.y + if (wy.isNotEmpty()) by.delta else 0f) to show(wx + wy, s)
  }

  fun alongAngle(from: Pt, to: Pt, targets: List<SnapFeatures>, tol: Float): Pair<Pt, List<SnapGuide>> = alongAngle(from, to, targets, SnapSession(tol))

  /**
   * A line's end at [to], on a tidy angle from [from], slides along the line (keeping its angle) to
   * line up with the sides, middles and corners of [targets]: a level line's end stops right above
   * a box's side or middle, an upright one level with it. Returns where it goes and the guides
   * (none when nothing lines up).
   */
  fun alongAngle(from: Pt, to: Pt, targets: List<SnapFeatures>, s: SnapSession): Pair<Pt, List<SnapGuide>> {
    val len = to.dist(from)
    if (len < 1e-3f) return to to emptyList()
    val dir = (to - from) * (1f / len)
    val best = Best()
    val reach = s.reach
    // The end moved along the line to x = v (a vertical guide) or y = v (a level one).
    fun offer(vertical: Boolean, v: Float, k: Long, middle: Boolean, spanFrom: Float, spanTo: Float) {
      val d = if (vertical) dir.x else dir.y
      if (abs(d) < 0.2f) return
      val t = ((v - if (vertical) from.x else from.y) / d)
      if (t <= 0f) return
      val shift = t - len
      if (abs(shift) >= reach || !s.catches(k, abs(shift), middle)) return
      val q = from + dir * t
      val guide = if (vertical) SnapGuide.Align(true, v, min(q.y, spanFrom), max(q.y, spanTo)) else SnapGuide.Align(false, v, min(q.x, spanFrom), max(q.x, spanTo))
      best.offer(abs(shift) * if (middle) MIDDLE_BIAS else 1f, shift, guide, k)
    }
    targets.forEachIndexed { ti, t ->
      t.box?.let { tb ->
        floatArrayOf(tb.left, tb.center.x, tb.right).forEachIndexed { vi, v -> offer(true, v, key(K_ALIGN_X, ti, vi), vi == 1, tb.top, tb.bottom) }
        floatArrayOf(tb.top, tb.center.y, tb.bottom).forEachIndexed { vi, v -> offer(false, v, key(K_ALIGN_Y, ti, vi), vi == 1, tb.left, tb.right) }
      }
      t.points.forEachIndexed { qi, q ->
        offer(true, q.x, key(K_DOT_X, ti, qi), false, q.y, q.y)
        offer(false, q.y, key(K_DOT_Y, ti, qi), false, q.x, q.x)
      }
    }
    val win = best.winners()
    if (win.isEmpty()) return to to emptyList()
    return from + dir * (len + best.delta) to show(win, s)
  }

  fun corner(p: Pt, targets: List<SnapFeatures>, tol: Float): Pt? = corner(p, targets, SnapSession(tol))

  /** The nearest end or corner of [targets] that catches [p]. */
  fun corner(p: Pt, targets: List<SnapFeatures>, s: SnapSession): Pt? {
    var best: Pt? = null
    var bestKey = 0L
    var dist = Float.MAX_VALUE
    targets.forEachIndexed { ti, t ->
      t.points.forEach { q ->
        val d = p.dist(q)
        val k = pointKey(q)
        if (d < s.reach && s.catches(k, d, true) && d < dist) {
          dist = d
          best = q
          bestKey = k
        }
      }
    }
    if (best != null) s.use(bestKey)
    return best
  }

  fun alongLine(from: Pt, to: Pt, targets: List<SnapFeatures>, tol: Float): Pt? = alongLine(from, to, targets, SnapSession(tol))

  /**
   * Where the line from [from] through [to] meets an edge of [targets] near [to]: a line being drawn
   * keeps its direction (its tidy angle) and just stops on the edge it reaches.
   */
  fun alongLine(from: Pt, to: Pt, targets: List<SnapFeatures>, s: SnapSession): Pt? {
    val dir = to - from
    if (dir.length() < 1e-3f) return null
    var best: Pt? = null
    var bestKey = 0L
    var dist = Float.MAX_VALUE
    targets.forEachIndexed { ti, t ->
      val tb = t.box
      if (tb != null && !tb.outset(s.reach).contains(to)) return@forEachIndexed
      t.segments.forEachIndexed { si, (a, b) ->
        val e = b - a
        val den = dir.cross(e)
        if (abs(den) < 1e-6f) return@forEachIndexed
        // from + dir·s = a + e·u, with the meeting point on the edge (0..1 along it).
        val u = (a - from).cross(dir) / den
        if (u < -1e-4f || u > 1f + 1e-4f) return@forEachIndexed
        val q = if (u <= 0f) a else if (u >= 1f) b else a + e * u
        val d = q.dist(to)
        val k = edgeKey(q, a, b, K_EDGE, ti, si)
        if (d < s.reach && s.catches(k, d, true) && d < dist) {
          dist = d
          best = q
          bestKey = k
        }
      }
    }
    if (best != null) s.use(bestKey)
    return best
  }

  fun point(p: Pt, targets: List<SnapFeatures>, tol: Float): Pt? = point(p, targets, SnapSession(tol))

  /**
   * Where a single point being dragged (a line's end, a shape's dot, where a new shape starts)
   * locks on: another shape's end or corner first, else the nearest place on an edge.
   */
  fun point(p: Pt, targets: List<SnapFeatures>, s: SnapSession): Pt? {
    var best: Pt? = null
    var bestKey = 0L
    var score = Float.MAX_VALUE
    targets.forEachIndexed { ti, t ->
      val tb = t.box
      if (tb != null && !tb.outset(s.reach).contains(p)) return@forEachIndexed
      t.points.forEach { q ->
        val d = p.dist(q)
        val k = pointKey(q)
        if (d < s.reach && s.catches(k, d, true) && d * POINT_BIAS < score) {
          score = d * POINT_BIAS
          best = q
          bestKey = k
        }
      }
      t.segments.forEachIndexed { si, (a, b) ->
        val q = nearestOn(p, a, b)
        val d = p.dist(q)
        val k = edgeKey(q, a, b, K_EDGE, ti, si)
        if (d < s.reach && s.catches(k, d, true) && d < score) {
          score = d
          best = q
          bestKey = k
        }
      }
    }
    if (best != null) s.use(bestKey)
    return best
  }
}
