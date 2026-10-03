package com.tali.notes.model

import com.tali.notes.geometry.Affine
import com.tali.notes.geometry.Geo
import com.tali.notes.geometry.Pt
import com.tali.notes.geometry.Rect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val TAU = (2 * PI).toFloat()

/**
 * Editable vector geometry. Shapes keep their geometry (not just ink) so they can be extended,
 * shortened or reshaped later while the pen thickness stays exactly the same.
 */
@Serializable
sealed interface ShapeGeometry {
  /** Polylines to ink, each drawn as one continuous stroke. [penSize] scales arrowheads and hatching. */
  fun paths(penSize: Float): List<List<Pt>>

  fun transformed(m: Affine): ShapeGeometry

  /** Drag handles shown when the shape is selected. */
  fun handles(): List<Pt>

  fun withHandle(index: Int, p: Pt): ShapeGeometry

  fun bounds(penSize: Float): Rect = Rect.around(paths(penSize).flatten())!!.outset(penSize)
}

@Serializable
@SerialName("line")
data class LineShape(val a: Pt, val b: Pt, val arrowStart: Boolean = false, val arrowEnd: Boolean = false) : ShapeGeometry {
  override fun paths(penSize: Float): List<List<Pt>> {
    val out = mutableListOf(listOf(a, b))
    if (arrowEnd) out += arrowHead(a, b, penSize)
    if (arrowStart) out += arrowHead(b, a, penSize)
    return out
  }

  override fun transformed(m: Affine) = copy(a = m.apply(a), b = m.apply(b))

  override fun handles() = listOf(a, b)

  /**
   * Moving an endpoint extends or shortens the line. Very close to the original direction (2°) the
   * drag is projected onto it, so the line keeps its exact angle while its length changes; beyond
   * that it turns freely (and the canvas snaps it to every 45°).
   */
  override fun withHandle(index: Int, p: Pt): ShapeGeometry {
    val fixed = if (index == 0) b else a
    val moving = if (index == 0) a else b
    val dir = (moving - fixed).normalized()
    val v = p - fixed
    val cosAngle = if (v.length() < 1e-3f) 1f else dir.dot(v.normalized())
    val target = if (cosAngle > cos(2.0 * PI / 180).toFloat()) fixed + dir * max(1f, v.dot(dir)) else p
    return if (index == 0) copy(a = target) else copy(b = target)
  }

  val length: Float
    get() = a.dist(b)
}

@Serializable
@SerialName("poly")
data class PolyShape(
  val points: List<Pt>,
  val closed: Boolean,
  /** How far it has been turned (with the lasso) since it was drawn, so resizing follows its own sides. */
  val rotation: Float = 0f,
  /** A hand-drawn line (from a saved shape): drawn as a smooth curve through its points, not straight bits. */
  val smooth: Boolean = false,
) : ShapeGeometry {
  override fun paths(penSize: Float) = listOf(if (closed) points + points.first() else if (smooth) Geo.smooth(points) else points)

  override fun transformed(m: Affine) = copy(points = points.map(m::apply), rotation = rotation + atan2(m.b, m.a))

  // A hand-drawn line has far too many points to give each a dot: it resizes from its corners instead.
  // A rectangle (or square) also gets a dot in the middle of each side, to stretch it one way.
  override fun handles() =
    when {
      smooth -> GroupShape(listOf(this)).handles()
      isRect() -> points + points.indices.map { i -> (points[i] + points[(i + 1) % 4]) * 0.5f }
      else -> points
    }

  /** A closed four-cornered shape whose corners sit on the corners of its box in its own frame. */
  fun isRect(): Boolean {
    if (!closed || points.size != 4) return false
    val c = Geo.centroid(points)
    val local = points.map { (it - c).rotated(-rotation) }
    val w = local.maxOf { it.x } - local.minOf { it.x }
    val h = local.maxOf { it.y } - local.minOf { it.y }
    if (w < 1e-3f || h < 1e-3f) return false
    return local.all { q -> abs(abs(q.x) - w / 2) < w * 0.02f && abs(abs(q.y) - h / 2) < h * 0.02f }
  }

  /**
   * A side's middle dot moves that side only (the opposite one stays put), making the rectangle
   * longer or shorter one way. Within [SQUARE_SNAP] of square it snaps to an exact square; a
   * rectangle's corner dots snap the same way.
   */
  private fun withSide(side: Int, p: Pt): ShapeGeometry {
    val c = Geo.centroid(points)
    val theta = rotation
    val local = points.map { (it - c).rotated(-theta) }
    val target = (p - c).rotated(-theta)
    val mid = (local[side] + local[(side + 1) % 4]) * 0.5f
    val l = local.minOf { it.x }
    val r = local.maxOf { it.x }
    val t = local.minOf { it.y }
    val b = local.maxOf { it.y }
    val w = r - l
    val h = b - t
    val horizontal = abs(mid.x) / w > abs(mid.y) / h
    val out =
      if (horizontal) {
        val anchor = if (mid.x > 0) l else r
        var size = max(abs(target.x - anchor), w * MIN_SCALE)
        if (abs(size - h) < h * SQUARE_SNAP) size = h
        val sx = size / w
        local.map { q -> Pt(anchor + (q.x - anchor) * sx, q.y) }
      } else {
        val anchor = if (mid.y > 0) t else b
        var size = max(abs(target.y - anchor), h * MIN_SCALE)
        if (abs(size - w) < w * SQUARE_SNAP) size = w
        val sy = size / h
        local.map { q -> Pt(q.x, anchor + (q.y - anchor) * sy) }
      }
    return copy(points = out.map { c + it.rotated(theta) })
  }

  /**
   * A rectangle's corner dot resizes it rather than bending it: it stretches from the opposite
   * corner within the shape's own frame (so a turned rectangle stays a rectangle), evenly for a
   * square, and never collapses or turns inside out. Every other polygon's dots move their own
   * corners.
   */
  override fun withHandle(index: Int, p: Pt): ShapeGeometry {
    if (smooth) return (GroupShape(listOf(this)).withHandle(index, p) as GroupShape).parts[0]
    if (index >= points.size) return if (isRect()) withSide(index - points.size, p) else this
    // Anything but a rectangle or square (a pentagon, a star, a polygon recognised from her
    // drawing): each dot moves its own corner, so its joints can be shaped freely. (The lasso's
    // corner handle still resizes the whole shape.)
    if (points.size < 3 || !isRect()) return copy(points = points.toMutableList().also { it[index] = p })
    val c = Geo.centroid(points)
    val theta = rotation
    val local = points.map { (it - c).rotated(-theta) }
    val target = (p - c).rotated(-theta)
    val v = local[index]
    val l = local.minOf { it.x }
    val r = local.maxOf { it.x }
    val t = local.minOf { it.y }
    val b = local.maxOf { it.y }
    val w = max(r - l, 1e-3f)
    val h = max(b - t, 1e-3f)
    // Which sides this dot pulls: a dot near an edge of the box pulls that edge; a middle one doesn't.
    val ax: Float? = when {
      v.x > l + w * 0.75f -> l
      v.x < l + w * 0.25f -> r
      else -> null
    }
    val ay: Float? = when {
      v.y > t + h * 0.75f -> t
      v.y < t + h * 0.25f -> b
      else -> null
    }
    fun factor(anchor: Float?, from: Float, to: Float): Float =
      if (anchor == null || abs(from - anchor) < 1e-3f) 1f else ((to - anchor) / (from - anchor)).coerceIn(MIN_SCALE, MAX_SCALE)
    var sx = factor(ax, v.x, target.x)
    var sy = factor(ay, v.y, target.y)
    if (isEven()) {
      // Keep its proportions: follow the drag along the line from the anchor through the dot.
      val anchor = Pt(ax ?: (l + r) / 2, ay ?: (t + b) / 2)
      val d = v - anchor
      val s = if (d.length() < 1e-3f) 1f else ((target - anchor).dot(d) / d.dot(d)).coerceIn(MIN_SCALE, MAX_SCALE)
      sx = s
      sy = s
    }
    else if (points.size == 4 && ax != null && ay != null && isRect()) {
      // A rectangle's corner: snaps to a square when it comes close.
      val nw = w * sx
      val nh = h * sy
      if (abs(nw - nh) < max(nw, nh) * SQUARE_SNAP) {
        val side = (nw + nh) / 2
        sx = side / w
        sy = side / h
      }
    }
    val axv = ax ?: (l + r) / 2
    val ayv = ay ?: (t + b) / 2
    val out = local.map { q -> c + Pt(axv + (q.x - axv) * sx, ayv + (q.y - ayv) * sy).rotated(theta) }
    return copy(points = out)
  }

  /** Squares, regular polygons and stars resize evenly; anything else may stretch. */
  private fun isEven(): Boolean {
    if (points.size >= 5) return true
    val c = Geo.centroid(points)
    val d = points.map { it.dist(c) }
    return d.max() - d.min() < d.max() * 0.04f && points.size == 4
  }
}

private const val MIN_SCALE = 0.05f
/** How close to square (as a share of the side) a rectangle being stretched snaps to a square. */
private const val SQUARE_SNAP = 0.04f
private const val MAX_SCALE = 40f

@Serializable
@SerialName("ellipse")
data class EllipseShape(val center: Pt, val rx: Float, val ry: Float, val rotation: Float = 0f) : ShapeGeometry {
  override fun paths(penSize: Float): List<List<Pt>> {
    val n = (max(rx, ry) / 3f).toInt().coerceIn(48, 180)
    return listOf(List(n + 1) { i -> point(TAU * i / n) })
  }

  fun point(t: Float) = center + Pt(rx * cos(t), ry * sin(t)).rotated(rotation)

  override fun transformed(m: Affine): ShapeGeometry {
    // Uniform scale, translation and rotation keep an ellipse an ellipse; that's all selections do.
    val s = m.scaleFactor()
    val rot = atan2(m.b, m.a)
    return copy(center = m.apply(center), rx = rx * s, ry = ry * s, rotation = rotation + rot)
  }

  override fun handles() = listOf(point(0f), point(PI.toFloat() / 2f), point(PI.toFloat()), point(1.5f * PI.toFloat()))

  override fun withHandle(index: Int, p: Pt): ShapeGeometry {
    // A circle stays a circle: any dot sets the radius.
    if (abs(rx - ry) < max(rx, ry) * 0.01f) return copy(rx = max(2f, p.dist(center)), ry = max(2f, p.dist(center)))
    val local = (p - center).rotated(-rotation)
    return if (index % 2 == 0) copy(rx = max(2f, abs(local.x))) else copy(ry = max(2f, abs(local.y)))
  }
}

@Serializable
@SerialName("arc")
data class ArcShape(val center: Pt, val radius: Float, val start: Float, val sweep: Float, val arrowEnd: Boolean = false) : ShapeGeometry {
  override fun paths(penSize: Float): List<List<Pt>> {
    val n = (abs(sweep) * radius / 3f).toInt().coerceIn(16, 160)
    val arc = List(n + 1) { i -> center + Pt.polar(radius, start + sweep * i / n) }
    if (!arrowEnd) return listOf(arc)
    return listOf(arc) + arrowHead(arc[arc.size - 2], arc.last(), penSize)
  }

  override fun transformed(m: Affine) = copy(center = m.apply(center), radius = radius * m.scaleFactor(), start = start + atan2(m.b, m.a))

  override fun handles() = listOf(center + Pt.polar(radius, start), center + Pt.polar(radius, start + sweep))

  /** How far round the arc goes, in whole degrees (always positive). */
  val degrees: Int
    get() = Math.round(Math.toDegrees(abs(sweep).toDouble())).toInt()

  override fun withHandle(index: Int, p: Pt): ShapeGeometry {
    val ang = (p - center).angle()
    val r = max(4f, p.dist(center))
    // The arc keeps its direction, and the turn wraps: going past a full circle starts again from
    // nothing (361° is 1°), so an end can be dragged round either way without getting stuck.
    val dir = if (sweep < 0f) -1f else 1f
    return if (index == 0) {
      val end = start + sweep
      val s = snapArc(positiveTurn(dir * (end - ang))) * dir
      copy(start = end - s, sweep = s, radius = r)
    } else {
      copy(sweep = snapArc(positiveTurn(dir * (ang - start))) * dir, radius = r)
    }
  }

  private companion object {
    /** Gently onto whole 15° steps (30°, 45°, 90°...), so the usual angles are easy to hit. */
    fun snapArc(s: Float): Float {
      val step = (PI / 12).toFloat()
      val snapped = Math.round(s / step) * step
      return if (snapped > 0f && abs(s - snapped) < 0.03f) snapped else s.coerceAtLeast(MIN_TURN)
    }
  }
}

/**
 * Part of a circle, closed like a slice of pie: [sweep] of a full turn (so 50% is a half circle with
 * its diameter, 25% a quarter circle). Its end dots change how much of the circle it is, snapping
 * to whole 5% steps and to quarters; the middle dot resizes and turns it.
 */
@Serializable
@SerialName("sector")
data class SectorShape(val center: Pt, val radius: Float, val start: Float, val sweep: Float) : ShapeGeometry {
  /** How much of a full circle this is, 0..1. */
  val fraction: Float
    get() = (abs(sweep) / TAU).coerceIn(0f, 1f)

  override fun paths(penSize: Float): List<List<Pt>> {
    val n = (abs(sweep) * radius / 3f).toInt().coerceIn(12, 200)
    val arc = List(n + 1) { i -> center + Pt.polar(radius, start + sweep * i / n) }
    // A whole circle has no radii; anything less closes through the centre in one stroke.
    return if (fraction >= 0.999f) listOf(arc) else listOf(listOf(center) + arc + listOf(center))
  }

  override fun transformed(m: Affine) = copy(center = m.apply(center), radius = radius * m.scaleFactor(), start = start + atan2(m.b, m.a))

  override fun handles() = listOf(center + Pt.polar(radius, start), center + Pt.polar(radius, start + sweep), center + Pt.polar(radius, start + sweep / 2))

  override fun withHandle(index: Int, p: Pt): ShapeGeometry {
    val ang = (p - center).angle()
    return when (index) {
      // An end: the other end stays put and the amount of circle changes (never through zero).
      0 -> {
        val end = start + sweep
        val s = snapSweep(positiveTurn(end - ang))
        copy(start = end - s, sweep = s)
      }
      1 -> copy(sweep = snapSweep(positiveTurn(ang - start)))
      // The middle: size and direction, keeping the same amount of circle.
      else -> copy(radius = max(4f, p.dist(center)), start = ang - sweep / 2)
    }
  }

  companion object {
    /** [fraction] of a circle centred on [center], reaching [toward] with its curved side facing that way. */
    fun of(center: Pt, toward: Pt, fraction: Float): SectorShape {
      val sweep = TAU * fraction.coerceIn(0.01f, 1f)
      val dir = if (toward.dist(center) < 0.5f) (-PI / 2).toFloat() else (toward - center).angle()
      return SectorShape(center, max(4f, toward.dist(center)), dir - sweep / 2, sweep)
    }

    /** Snaps to quarters (strongly) and to whole 5% steps, so 25%, 50% and 75% are easy to hit exactly. */
    fun snapSweep(s: Float): Float {
      val f = s / TAU
      // A whole circle only when really close: a wide catch there made the circle stick shut
      // when an end was dragged on past it.
      if (f > 0.995f) return TAU
      val quarter = Math.round(f * 4) / 4f
      if (abs(f - quarter) < 0.025f && quarter > 0f && quarter < 1f) return quarter * TAU
      val five = Math.round(f * 20) / 20f
      if (abs(f - five) < 0.01f && five > 0f && five < 1f) return five * TAU
      return s.coerceIn(MIN_TURN, TAU)
    }
  }
}

/**
 * A triangle whose corners move one at a time. A dragged corner moves in whole degrees and locks
 * on to the angles people use, the way a line locks to its angles: a right angle at any corner,
 * two equal angles (isosceles, and equilateral at 60°), the 15° family (30°, 45°, 60°...) and,
 * more lightly, every 5°.
 */
@Serializable
@SerialName("triangle")
data class TriangleShape(val a: Pt, val b: Pt, val c: Pt) : ShapeGeometry {
  val points: List<Pt>
    get() = listOf(a, b, c)

  /** The angles at [a], [b] and [c], in degrees. */
  val angles: List<Float>
    get() = listOf(deg(Geo.interiorAngle(c, a, b)), deg(Geo.interiorAngle(a, b, c)), deg(Geo.interiorAngle(b, c, a)))

  override fun paths(penSize: Float) = listOf(listOf(a, b, c, a))

  override fun transformed(m: Affine) = TriangleShape(m.apply(a), m.apply(b), m.apply(c))

  override fun handles() = points

  override fun withHandle(index: Int, p: Pt): ShapeGeometry {
    val q = snapCorner(points[(index + 1) % 3], points[(index + 2) % 3], p)
    return when (index) {
      0 -> copy(a = q)
      1 -> copy(b = q)
      else -> copy(c = q)
    }
  }

  companion object {
    private fun deg(r: Float) = Math.toDegrees(r.toDouble()).toFloat()

    /** How close (in degrees) a right angle or two equal angles pull. */
    private const val PULL = 3f

    /**
     * One angle, locked to the nearest 15° within 2.5°, else to the nearest 5° within 1°, else
     * to the whole degree, so it moves a degree at a time between the angles it locks on to.
     */
    fun lockAngle(x: Float): Float {
      val fifteen = Math.round(x / 15f) * 15f
      if (abs(x - fifteen) < 2.5f) return fifteen
      val five = Math.round(x / 5f) * 5f
      if (abs(x - five) < 1f) return five
      return Math.round(x).toFloat()
    }

    /**
     * Where the corner dragged to [p] settles, with the other two corners at [fa] and [fb]: the
     * angles at those corners are locked (to a right angle, to make two angles equal, or with
     * [lockAngle]), and the corner goes where the two sides meet, on the side it was dragged.
     */
    fun snapCorner(fa: Pt, fb: Pt, p: Pt): Pt {
      val base = fb - fa
      val len = base.length()
      val side = base.cross(p - fa)
      if (len < 1f || abs(side) < 1e-3f) return p
      val ra = deg(Geo.interiorAngle(fb, fa, p))
      val rb = deg(Geo.interiorAngle(fa, fb, p))
      val rp = 180f - ra - rb
      if (ra < 1f || rb < 1f || rp < 1f) return p
      var sa: Float
      var sb: Float
      when {
        abs(ra - 90f) < PULL -> { sa = 90f; sb = lockAngle(rb) }
        abs(rb - 90f) < PULL -> { sb = 90f; sa = lockAngle(ra) }
        abs(rp - 90f) < PULL -> { sa = lockAngle(ra); sb = 90f - sa }
        abs(ra - rb) < PULL -> { sa = lockAngle((ra + rb) / 2f); sb = sa }
        abs(rb - rp) < PULL -> { sb = lockAngle((rb + rp) / 2f); sa = 180f - 2f * sb }
        abs(ra - rp) < PULL -> { sa = lockAngle((ra + rp) / 2f); sb = 180f - 2f * sa }
        else -> { sa = lockAngle(ra); sb = lockAngle(rb) }
      }
      if (sa < 1f || sb < 1f || sa + sb > 179f) return p
      val toA = Math.toRadians(sa.toDouble()).toFloat()
      val toB = Math.toRadians(sb.toDouble()).toFloat()
      val reach = len * sin(toB) / sin(toA + toB)
      val dir = base.normalized().rotated(if (side > 0f) toA else -toA)
      return fa + dir * reach
    }
  }
}

/** Several parts that move and scale together, used for engineering symbols. */
@Serializable
@SerialName("group")
data class GroupShape(val parts: List<ShapeGeometry>) : ShapeGeometry {
  override fun paths(penSize: Float) = parts.flatMap { it.paths(penSize) }

  override fun transformed(m: Affine) = copy(parts = parts.map { it.transformed(m) })

  private fun box() = Rect.around(parts.flatMap { it.paths(1f).flatten() })!!

  override fun handles(): List<Pt> {
    val r = box()
    return listOf(Pt(r.left, r.top), Pt(r.right, r.top), Pt(r.right, r.bottom), Pt(r.left, r.bottom))
  }

  /** Corner handles scale the group uniformly around the opposite corner. */
  override fun withHandle(index: Int, p: Pt): ShapeGeometry {
    val h = handles()
    val anchor = h[(index + 2) % 4]
    val old = h[index]
    val oldDiag = old.dist(anchor)
    if (oldDiag < 1f) return this
    val s = (p.dist(anchor) / oldDiag).coerceIn(0.05f, 20f)
    return transformed(Affine.scaleAround(anchor, s))
  }
}

/** The smallest arc or part circle an end dot can make: one degree. */
private val MIN_TURN = (PI / 180).toFloat()

/** An angle in (0, 2π]: how far round from one end to the other, going the positive way. */
internal fun positiveTurn(a: Float): Float {
  var s = a % TAU
  if (s <= 0f) s += TAU
  return s
}

/** Two short barbs at [tip], pointing back toward [from]. */
fun arrowHead(from: Pt, tip: Pt, penSize: Float): List<List<Pt>> {
  val dir = (tip - from).normalized()
  if (dir == Pt.ZERO) return emptyList()
  val len = min(max(10f, penSize * 5f), from.dist(tip) * 0.45f)
  val back = dir * -len
  val left = tip + back.rotated(0.45f)
  val right = tip + back.rotated(-0.45f)
  return listOf(listOf(left, tip, right))
}

/** Shapes offered in the shape picker. Recognized hand-drawn shapes map onto the same geometry. */
enum class ShapeKind(val label: String, val group: ShapeGroup) {
  LINE("Line", ShapeGroup.BASIC),
  ARROW("Arrow", ShapeGroup.BASIC),
  DOUBLE_ARROW("Double arrow", ShapeGroup.BASIC),
  CIRCLE("Circle", ShapeGroup.BASIC),
  ELLIPSE("Ellipse", ShapeGroup.BASIC),
  ARC("Arc", ShapeGroup.BASIC),
  PART_CIRCLE("Part circle", ShapeGroup.BASIC),
  TRIANGLE("Triangle", ShapeGroup.BASIC),
  SQUARE("Square", ShapeGroup.BASIC),
  RECTANGLE("Rectangle", ShapeGroup.BASIC),
  DIAMOND("Diamond", ShapeGroup.BASIC),
  PARALLELOGRAM("Parallelogram", ShapeGroup.BASIC),
  TRAPEZOID("Trapezoid", ShapeGroup.BASIC),
  PENTAGON("Pentagon", ShapeGroup.BASIC),
  HEXAGON("Hexagon", ShapeGroup.BASIC),
  STAR("Star", ShapeGroup.BASIC),
  PIN_SUPPORT("Pin support", ShapeGroup.ENGINEERING),
  ROLLER_SUPPORT("Roller support", ShapeGroup.ENGINEERING),
  FIXED_SUPPORT("Fixed support", ShapeGroup.ENGINEERING),
  POINT_LOAD("Point load", ShapeGroup.ENGINEERING),
  DISTRIBUTED_LOAD("Distributed load", ShapeGroup.ENGINEERING),
  MOMENT("Moment", ShapeGroup.ENGINEERING),
  DIMENSION("Dimension", ShapeGroup.ENGINEERING);

  /** Builds this shape to fill the box dragged from [p0] to [p1]. */
  fun create(p0: Pt, p1: Pt): ShapeGeometry {
    val l = min(p0.x, p1.x)
    val r = max(p0.x, p1.x)
    val t = min(p0.y, p1.y)
    val b = max(p0.y, p1.y)
    val w = max(r - l, 4f)
    val h = max(b - t, 4f)
    val c = Pt(l + w / 2f, t + h / 2f)
    val side = max(w, h)
    return when (this) {
      LINE -> LineShape(p0, p1)
      ARROW -> LineShape(p0, p1, arrowEnd = true)
      DOUBLE_ARROW -> LineShape(p0, p1, arrowStart = true, arrowEnd = true)
      DIMENSION -> dimension(p0, p1)
      CIRCLE -> EllipseShape(p0, p0.dist(p1), p0.dist(p1))
      ELLIPSE -> EllipseShape(c, w / 2f, h / 2f)
      ARC -> ArcShape(c, side / 2f, PI.toFloat(), PI.toFloat())
      // From the centre outwards, a half to start with; its end dots change the amount.
      PART_CIRCLE -> SectorShape.of(p0, p1, 0.5f)
      // One triangle: each corner moves on its own and snaps to tidy angles.
      TRIANGLE -> TriangleShape(Pt(c.x, t), Pt(r, t + h), Pt(l, t + h))
      SQUARE -> PolyShape(listOf(Pt(l, t), Pt(l + side, t), Pt(l + side, t + side), Pt(l, t + side)), true)
      RECTANGLE -> PolyShape(listOf(Pt(l, t), Pt(r, t), Pt(r, b), Pt(l, b)), true)
      DIAMOND -> PolyShape(listOf(Pt(c.x, t), Pt(r, c.y), Pt(c.x, b), Pt(l, c.y)), true)
      PARALLELOGRAM -> PolyShape(listOf(Pt(l + w * 0.25f, t), Pt(r, t), Pt(r - w * 0.25f, b), Pt(l, b)), true)
      TRAPEZOID -> PolyShape(listOf(Pt(l + w * 0.22f, t), Pt(r - w * 0.22f, t), Pt(r, b), Pt(l, b)), true)
      PENTAGON -> regularPolygon(c, side / 2f, 5)
      HEXAGON -> regularPolygon(c, side / 2f, 6)
      STAR -> star(c, side / 2f)
      PIN_SUPPORT -> pinSupport(c, side)
      ROLLER_SUPPORT -> rollerSupport(c, side)
      FIXED_SUPPORT -> fixedSupport(c, h.coerceAtLeast(w))
      POINT_LOAD -> LineShape(Pt(c.x, t), Pt(c.x, b), arrowEnd = true)
      DISTRIBUTED_LOAD -> distributedLoad(l, r, t, b)
      MOMENT -> ArcShape(c, side / 2f, (0.75 * PI).toFloat(), (1.5 * PI).toFloat(), arrowEnd = true)
    }
  }

  /** Whether dragging should keep the drag's aspect ratio rather than a free box. */
  val dragIsSegment: Boolean
    get() = this == LINE || this == ARROW || this == DOUBLE_ARROW || this == DIMENSION || this == CIRCLE || this == PART_CIRCLE
}

enum class ShapeGroup(val label: String) {
  BASIC("Shapes"),
  ENGINEERING("Engineering"),
}

fun regularPolygon(center: Pt, radius: Float, n: Int, rotation: Float = (-PI / 2).toFloat()): PolyShape =
  PolyShape(List(n) { i -> center + Pt.polar(radius, rotation + TAU * i / n) }, true)

fun star(center: Pt, radius: Float, rotation: Float = (-PI / 2).toFloat()): PolyShape =
  PolyShape(List(10) { i -> center + Pt.polar(if (i % 2 == 0) radius else radius * 0.42f, rotation + TAU * i / 10) }, true)

private fun hatch(from: Pt, to: Pt, count: Int, offset: Pt): List<ShapeGeometry> =
  List(count) { i ->
    val p = from + (to - from) * ((i + 0.5f) / count)
    LineShape(p, p + offset)
  }

private fun pinSupport(c: Pt, size: Float): ShapeGeometry {
  val h = size * 0.6f
  val top = Pt(c.x, c.y - h / 2f)
  val bl = Pt(c.x - h * 0.6f, c.y + h / 2f)
  val br = Pt(c.x + h * 0.6f, c.y + h / 2f)
  val groundL = Pt(bl.x - h * 0.25f, bl.y)
  val groundR = Pt(br.x + h * 0.25f, br.y)
  return GroupShape(
    listOf(PolyShape(listOf(top, br, bl), true), LineShape(groundL, groundR)) + hatch(groundL, groundR, 6, Pt(-h * 0.18f, h * 0.22f))
  )
}

private fun rollerSupport(c: Pt, size: Float): ShapeGeometry {
  val h = size * 0.5f
  val top = Pt(c.x, c.y - h / 2f)
  val bl = Pt(c.x - h * 0.6f, c.y + h * 0.25f)
  val br = Pt(c.x + h * 0.6f, c.y + h * 0.25f)
  val r = h * 0.14f
  val wheelY = bl.y + r
  val groundY = wheelY + r
  val groundL = Pt(bl.x - h * 0.25f, groundY)
  val groundR = Pt(br.x + h * 0.25f, groundY)
  return GroupShape(
    listOf(
      PolyShape(listOf(top, br, bl), true),
      EllipseShape(Pt(c.x - h * 0.3f, wheelY), r, r),
      EllipseShape(Pt(c.x + h * 0.3f, wheelY), r, r),
      LineShape(groundL, groundR),
    ) + hatch(groundL, groundR, 6, Pt(-h * 0.18f, h * 0.22f))
  )
}

private fun fixedSupport(c: Pt, size: Float): ShapeGeometry {
  val top = Pt(c.x, c.y - size / 2f)
  val bottom = Pt(c.x, c.y + size / 2f)
  return GroupShape(listOf(LineShape(top, bottom)) + hatch(top, bottom, 7, Pt(-size * 0.16f, size * 0.12f)))
}

private fun distributedLoad(l: Float, r: Float, t: Float, b: Float): ShapeGeometry {
  val count = ((r - l) / 28f).toInt().coerceIn(3, 12)
  val arrows = List(count + 1) { i ->
    val x = l + (r - l) * i / count
    LineShape(Pt(x, t), Pt(x, b), arrowEnd = true)
  }
  return GroupShape(listOf(LineShape(Pt(l, t), Pt(r, t))) + arrows)
}

private fun dimension(p0: Pt, p1: Pt): ShapeGeometry {
  val dir = (p1 - p0).normalized()
  val normal = Pt(-dir.y, dir.x) * 8f
  return GroupShape(
    listOf(LineShape(p0, p1, arrowStart = true, arrowEnd = true), LineShape(p0 - normal, p0 + normal), LineShape(p1 - normal, p1 + normal))
  )
}
