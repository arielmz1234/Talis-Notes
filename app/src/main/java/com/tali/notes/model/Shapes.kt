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
data class LineShape(
  val a: Pt,
  val b: Pt,
  val arrowStart: Boolean = false,
  val arrowEnd: Boolean = false,
  /** A dimension line: short marks across each end (with arrows both ways), like on a drawing. */
  val ticks: Boolean = false,
) : ShapeGeometry {
  override fun paths(penSize: Float): List<List<Pt>> {
    val out = mutableListOf(listOf(a, b))
    if (arrowEnd) out += arrowHead(a, b, penSize)
    if (arrowStart) out += arrowHead(b, a, penSize)
    if (ticks) {
      val dir = (b - a).normalized()
      if (dir != Pt.ZERO) {
        val n = Pt(-dir.y, dir.x) * max(TICK, penSize * 2.5f)
        out += listOf(a - n, a + n)
        out += listOf(b - n, b + n)
      }
    }
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
  /**
   * Which basic shape it was drawn as, so it keeps that form: only the dots that make sense for it
   * (null: an older shape or one recognised from her drawing, whose corners each move freely).
   */
  val form: PolyForm? = null,
) : ShapeGeometry {
  override fun paths(penSize: Float) = listOf(if (closed) points + points.first() else if (smooth) Geo.smooth(points) else points)

  override fun transformed(m: Affine) = copy(points = points.map(m::apply), rotation = m.mapAngle(rotation))

  // Dots only where they change the shape (the resize chip resizes it): a rectangle's in the middle
  // of each side, to stretch it one way; every other polygon's at its corners. A hand-drawn line
  // has none (it could only be resized).
  override fun handles() =
    when {
      smooth -> emptyList()
      form == PolyForm.REGULAR -> emptyList()
      form == PolyForm.DIAMOND && points.size == 4 -> listOf(points[1], points[2])
      (form == PolyForm.PARALLELOGRAM || form == PolyForm.TRAPEZOID) && points.size == 4 -> listOf(points[0])
      isRect() -> points.indices.map { i -> (points[i] + points[(i + 1) % 4]) * 0.5f }
      else -> points
    }

  /** A rectangle with equal sides (a square). */
  fun isSquare(): Boolean {
    if (!isRect()) return false
    val a = points[0].dist(points[1])
    val b = points[1].dist(points[2])
    return abs(a - b) < max(a, b) * 0.005f
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
   * longer or shorter one way. Dragged on past the opposite side, it turns over and carries on
   * growing that way, the dot still under the pen (like a line's end dragged past its other end).
   * Within [SQUARE_SNAP] of square it snaps to an exact square.
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
    // The scale is signed: past the opposite side it's negative, and the rectangle turns over.
    fun signedSize(reach: Float, was: Float, other: Float): Float {
      val sign = if (reach < 0f) -1f else 1f
      var size = max(abs(reach), was * MIN_SCALE)
      if (abs(size - other) < other * SQUARE_SNAP) size = other
      return sign * size
    }
    val out =
      if (horizontal) {
        val anchor = if (mid.x > 0) l else r
        val toward = if (mid.x > 0) 1f else -1f
        val sx = signedSize((target.x - anchor) * toward, w, h) / w
        local.map { q -> Pt(anchor + (q.x - anchor) * sx, q.y) }
      } else {
        val anchor = if (mid.y > 0) t else b
        val toward = if (mid.y > 0) 1f else -1f
        val sy = signedSize((target.y - anchor) * toward, h, w) / h
        local.map { q -> Pt(q.x, anchor + (q.y - anchor) * sy) }
      }
    return copy(points = out.map { c + it.rotated(theta) })
  }

  /**
   * A rectangle's dots sit in the middle of its sides and stretch it one way ([withSide]); every
   * other polygon's dots move their own corners. (Resizing the whole shape is the resize chip's job.)
   */
  override fun withHandle(index: Int, p: Pt): ShapeGeometry {
    if (smooth || form == PolyForm.REGULAR) return this
    if (form != null && points.size == 4) withForm(index, p)?.let { return it }
    if (isRect()) return withSide(index, p)
    // Anything but a rectangle or square (a pentagon, a star, a polygon recognised from her
    // drawing): each dot moves its own corner, so its joints can be shaped freely. (The resize
    // chip resizes the whole shape.)
    return copy(points = points.toMutableList().also { it[index] = p })
  }
}

/**
 * A basic shape's own dot, in its own frame (turned with it, and right even when mirrored):
 * a diamond's right and bottom points stretch it evenly about its middle (snapping to a square
 * diamond); a parallelogram's top-left corner sets its slant (the bottom side stays, snapping
 * upright); a trapezoid's top-left corner sets how narrow its top is, both sides alike.
 */
private fun PolyShape.withForm(index: Int, p: Pt): ShapeGeometry? {
  val c = Geo.centroid(points)
  val local = points.map { (it - c).rotated(-rotation) }
  val target = (p - c).rotated(-rotation)
  val out: List<Pt> =
    when (form) {
      PolyForm.DIAMOND -> {
        // Points: top, right, bottom, left, each on its own axis about the middle.
        var hw = abs(local[1].x)
        var hh = abs(local[2].y)
        if (index == 0) hw = max(abs(target.x), hh * MIN_SCALE) else hh = max(abs(target.y), hw * MIN_SCALE)
        if (abs(hw - hh) < max(hw, hh) * SQUARE_SNAP) if (index == 0) hw = hh else hh = hw
        val sx = if (local[1].x < 0f) -1f else 1f
        val sy = if (local[2].y < 0f) -1f else 1f
        listOf(Pt(0f, -hh * sy), Pt(hw * sx, 0f), Pt(0f, hh * sy), Pt(-hw * sx, 0f))
      }
      PolyForm.PARALLELOGRAM -> {
        // Points: top-left, top-right, bottom-right, bottom-left; the bottom side stays.
        val x0 = local[3].x
        val a = local[2].x - local[3].x
        var slant = (target.x - x0).coerceIn(-2 * abs(a), 2 * abs(a))
        if (abs(slant) < abs(a) * 0.03f) slant = 0f
        val t = local[0].y
        listOf(Pt(x0 + slant, t), Pt(x0 + slant + a, t), local[2], local[3])
      }
      PolyForm.TRAPEZOID -> {
        // Points: top-left, top-right, bottom-right, bottom-left; the bottom side stays.
        val xl = local[3].x
        val xr = local[2].x
        val w = xr - xl
        val sgn = if (w < 0f) -1f else 1f
        var inset = ((target.x - xl) * sgn).coerceIn(-abs(w) * 0.45f, abs(w) * 0.48f)
        if (abs(inset) < abs(w) * 0.02f) inset = 0f
        val t = local[0].y
        listOf(Pt(xl + inset * sgn, t), Pt(xr - inset * sgn, t), local[2], local[3])
      }
      else -> return null
    }
  return copy(points = out.map { c + it.rotated(rotation) })
}

/** The basic shapes that keep their form (see [PolyShape.form]). */
@Serializable
enum class PolyForm { DIAMOND, PARALLELOGRAM, TRAPEZOID, REGULAR }

private const val MIN_SCALE = 0.05f
/** How close to square (as a share of the side) a rectangle being stretched snaps to a square. */
private const val SQUARE_SNAP = 0.04f

@Serializable
@SerialName("ellipse")
data class EllipseShape(
  val center: Pt,
  val rx: Float,
  val ry: Float,
  val rotation: Float = 0f,
  /** Drawn with the Ellipse tool: keeps its dots (to stretch it) even while it's perfectly round. */
  val stretchable: Boolean = false,
) : ShapeGeometry {
  /** Perfectly round (a circle). */
  val round: Boolean
    get() = abs(rx - ry) < max(rx, ry) * 0.01f

  override fun paths(penSize: Float): List<List<Pt>> {
    val n = (max(rx, ry) / 3f).toInt().coerceIn(48, 180)
    return listOf(List(n + 1) { i -> point(TAU * i / n) })
  }

  fun point(t: Float) = center + Pt(rx * cos(t), ry * sin(t)).rotated(rotation)

  override fun transformed(m: Affine): ShapeGeometry {
    // Uniform scale, translation, rotation and mirroring keep an ellipse an ellipse; that's all selections do.
    val s = m.scaleFactor()
    return copy(center = m.apply(center), rx = rx * s, ry = ry * s, rotation = m.mapAngle(rotation))
  }

  // A circle's dots could only resize it (the resize chip does that); an ellipse's change its shape.
  override fun handles() = if (round && !stretchable) emptyList() else listOf(point(0f), point(PI.toFloat() / 2f), point(PI.toFloat()), point(1.5f * PI.toFloat()))

  override fun withHandle(index: Int, p: Pt): ShapeGeometry {
    // A circle (from the Circle tool) stays a circle: any dot sets the radius.
    if (round && !stretchable) return copy(rx = max(2f, p.dist(center)), ry = max(2f, p.dist(center)))
    // An ellipse's dot stretches it one way, snapping round into a circle when close.
    val local = (p - center).rotated(-rotation)
    return if (index % 2 == 0) {
      val r = max(2f, abs(local.x)).let { if (abs(it - ry) < ry * SQUARE_SNAP) ry else it }
      copy(rx = r)
    } else {
      val r = max(2f, abs(local.y)).let { if (abs(it - rx) < rx * SQUARE_SNAP) rx else it }
      copy(ry = r)
    }
  }
}

@Serializable
@SerialName("arc")
data class ArcShape(val center: Pt, val radius: Float, val start: Float, val sweep: Float, val arrowEnd: Boolean = false) : ShapeGeometry {
  override fun paths(penSize: Float): List<List<Pt>> {
    val n = (abs(sweep) * radius / 3f).toInt().coerceIn(16, 160)
    val arc = List(n + 1) { i -> center + Pt.polar(radius, start + sweep * i / n) }
    if (!arrowEnd) return listOf(arc)
    // Sized by the whole arc, like a straight arrow by its length (the last step alone made it tiny),
    // and aimed along the stretch of curve the head covers: aimed along the last step, a big head on
    // a tight curve stuck out sideways.
    // On a small circle the head is kept in proportion to it too (it swamped the shape tile's icon).
    val room = min(abs(sweep), 1.5f) * radius
    val back = arrowHeadLength(penSize, room) / radius
    val from = center + Pt.polar(radius, start + sweep - if (sweep < 0f) -back else back)
    return listOf(arc) + arrowHead(from, arc.last(), penSize, room)
  }

  // Mirrored, it runs the other way round from its (mirrored) start.
  override fun transformed(m: Affine) = copy(center = m.apply(center), radius = radius * m.scaleFactor(), start = m.mapAngle(start), sweep = if (m.mirrors) -sweep else sweep)

  // A moment (the arc with an arrow) keeps its form: no end dots, only the resize chip. An arc has
  // its two ends and, like a part circle, a dot in the middle of the curve.
  override fun handles() = if (arrowEnd) emptyList() else listOf(center + Pt.polar(radius, start), center + Pt.polar(radius, start + sweep), center + Pt.polar(radius, start + sweep / 2))

  /** How far round the arc goes, in whole degrees (always positive). */
  val degrees: Int
    get() = Math.round(Math.toDegrees(abs(sweep).toDouble())).toInt()

  override fun withHandle(index: Int, p: Pt): ShapeGeometry {
    val ang = (p - center).angle()
    // An end slides round the same circle, like a part circle's: the size stays put and only how
    // far round it goes changes. The arc keeps its direction, and the turn wraps: going past a full
    // circle starts again from nothing (361° is 1°), so an end can be dragged round either way.
    val dir = if (sweep < 0f) -1f else 1f
    return when (index) {
      0 -> {
        val end = start + sweep
        val s = snapArc(positiveTurn(dir * (end - ang))) * dir
        copy(start = end - s, sweep = s)
      }
      1 -> copy(sweep = snapArc(positiveTurn(dir * (ang - start))) * dir)
      // The middle: size and direction, keeping the same turn.
      else -> copy(radius = max(4f, p.dist(center)), start = ang - sweep / 2)
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
 * A smooth curve through its dots (Catmull-Rom, no corners): drawn from where the pen landed to
 * where it lifted as a gentle bow with [CURVE_DOTS] dots spread along it, straight stretches
 * included, so any part of it can be bent. Each dot moves on its own.
 */
@Serializable
@SerialName("curve")
data class CurveShape(val points: List<Pt>) : ShapeGeometry {
  override fun paths(penSize: Float): List<List<Pt>> = listOf(Geo.smooth(points, cornerDeg = 180f))

  override fun transformed(m: Affine) = copy(points = points.map(m::apply))

  override fun handles() = points

  override fun withHandle(index: Int, p: Pt): ShapeGeometry = copy(points = points.toMutableList().also { if (index in it.indices) it[index] = p })

  companion object {
    /** A gentle bow from [a] to [b] (bulging to the left of the way it's drawn), dots evenly along it. */
    fun between(a: Pt, b: Pt): CurveShape {
      val d = b - a
      val len = d.length()
      val normal = if (len < 1e-3f) Pt(0f, -1f) else Pt(d.y / len, -d.x / len)
      val bulge = len * 0.2f
      return CurveShape(List(CURVE_DOTS) { i ->
        val t = i / (CURVE_DOTS - 1f)
        if (i == 0) a else if (i == CURVE_DOTS - 1) b else a + d * t + normal * (bulge * kotlin.math.sin(PI.toFloat() * t))
      })
    }
  }
}

/** How many dots a new curve has (both ends and three between). */
const val CURVE_DOTS = 5

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

  override fun transformed(m: Affine) = copy(center = m.apply(center), radius = radius * m.scaleFactor(), start = m.mapAngle(start), sweep = if (m.mirrors) -sweep else sweep)

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

/**
 * A distributed load: a line from [a] to [b] with [count] + 1 evenly spaced arrows hanging [depth]
 * off it (to its right going from [a] to [b], which is downward for a load drawn left to right;
 * negative is the other side). Its end dots lengthen or shorten it, and the arrows thin out or
 * fill in to keep their spacing, as when it's drawn; the middle dot (on the arrow tips) sets how
 * long the arrows are.
 */
@Serializable
@SerialName("load")
data class LoadShape(val a: Pt, val b: Pt, val depth: Float, val count: Int) : ShapeGeometry {
  private val across: Pt
    get() = (b - a).normalized().let { Pt(-it.y, it.x) }

  /** Where the arrow under [p] on the line points to. */
  private fun tip(p: Pt) = p + across * depth

  override fun paths(penSize: Float): List<List<Pt>> {
    val out = mutableListOf(listOf(a, b))
    for (i in 0..count) {
      val p = a + (b - a) * (i.toFloat() / count)
      val t = tip(p)
      out += listOf(p, t)
      out += arrowHead(p, t, penSize)
    }
    return out
  }

  // A mirror flips which side the arrows hang (their side is measured from a to b, which a mirror reverses).
  override fun transformed(m: Affine): ShapeGeometry {
    val s = m.scaleFactor()
    return copy(a = m.apply(a), b = m.apply(b), depth = depth * s * if (m.mirrors) -1f else 1f)
  }

  override fun handles() = listOf(a, b, tip(a + (b - a) * 0.5f))

  override fun withHandle(index: Int, p: Pt): ShapeGeometry {
    if (index == 2) {
      // The arrows' length (dragged across the line, they hang the other way).
      val d = (p - (a + (b - a) * 0.5f)).dot(across)
      return copy(depth = if (abs(d) < 6f) (if (d < 0f) -6f else 6f) else d)
    }
    val moved = if (index == 0) copy(a = p) else copy(b = p)
    if (moved.a.dist(moved.b) < 8f) return this
    // The arrows keep hanging on the same side as the line turns, and keep their spacing.
    val keepSide = if (moved.across.dot(across) < 0f) -1f else 1f
    return moved.copy(depth = depth * keepSide, count = loadArrows(moved.a.dist(moved.b)))
  }

  companion object {
    /** How many gaps between arrows a load [length] long gets (about one every 28 units, as when drawn). */
    fun loadArrows(length: Float) = (length / 28f).toInt().coerceIn(2, 40)
  }
}

/** Several parts that move and scale together, used for engineering symbols. */
@Serializable
@SerialName("group")
data class GroupShape(val parts: List<ShapeGeometry>) : ShapeGeometry {
  override fun paths(penSize: Float) = parts.flatMap { it.paths(penSize) }

  override fun transformed(m: Affine) = copy(parts = parts.map { it.transformed(m) })

  private fun box() = Rect.around(parts.flatMap { it.paths(1f).flatten() })!!

  private fun corners(): List<Pt> {
    val r = box()
    return listOf(Pt(r.left, r.top), Pt(r.right, r.top), Pt(r.right, r.bottom), Pt(r.left, r.bottom))
  }

  // Its corner dots only resized it, and the resize chip does that now: no dots of its own.
  override fun handles(): List<Pt> = emptyList()

  /** Scales the group uniformly around the corner opposite corner [index]. */
  override fun withHandle(index: Int, p: Pt): ShapeGeometry {
    val h = corners()
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

/** A clear head, like a hand-drawn arrow's: about 3 mm or 7 pen widths, whichever is bigger. */
fun arrowHeadLength(penSize: Float, room: Float): Float = min(max(20f, penSize * 7f), room * 0.4f)

/** Two short barbs at [tip], pointing back toward [from]. [room] is the length of what it heads. */
fun arrowHead(from: Pt, tip: Pt, penSize: Float, room: Float = from.dist(tip)): List<List<Pt>> {
  val dir = (tip - from).normalized()
  if (dir == Pt.ZERO) return emptyList()
  // A clear head, like a hand-drawn arrow's: about 3 mm or 7 pen widths, whichever is bigger,
  // opening at 30° each side (it used to be small enough to get lost on a thin pen).
  val len = arrowHeadLength(penSize, room)
  val back = dir * -len
  val left = tip + back.rotated(0.52f)
  val right = tip + back.rotated(-0.52f)
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
  CURVE("Curve", ShapeGroup.BASIC),
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

  companion object {
    /** The shapes in the panel. Square is kept only so older choices still load: the rectangle snaps to a square. */
    val offered = entries.filter { it != SQUARE }
  }

  /**
   * Builds this shape as the Shapes tool draws it, from where the pen landed ([p0]) to where it is
   * ([p1]). Most shapes fill the box between them. The engineering symbols start at the point they
   * attach to, so that's the point that snaps onto a beam: a support's tip, a load's arrow tip (the
   * end of the drag), a moment's centre, a fixed support's wall from end to end.
   */
  fun create(p0: Pt, p1: Pt): ShapeGeometry =
    when (this) {
      PIN_SUPPORT -> pinSupportAt(p0, max(p0.dist(p1), 12f))
      ROLLER_SUPPORT -> rollerSupportAt(p0, max(p0.dist(p1), 12f))
      FIXED_SUPPORT -> fixedSupport(p0, if (p0.dist(p1) < 4f) p0 + Pt(0f, 40f) else p1)
      POINT_LOAD -> LineShape(p0, p1, arrowEnd = true)
      MOMENT -> ArcShape(p0, max(p0.dist(p1), 6f), (0.75 * PI).toFloat(), (1.5 * PI).toFloat(), arrowEnd = true)
      CURVE -> CurveShape.between(p0, p1)
      else -> inBox(p0, p1)
    }

  /** This shape filling the box from [p0] to [p1] (the shape icons, and every shape but the engineering symbols when drawn). */
  fun inBox(p0: Pt, p1: Pt): ShapeGeometry {
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
      // An ellipse can be stretched by its dots, and snaps round into a circle when close.
      ELLIPSE -> EllipseShape(c, w / 2f, h / 2f, stretchable = true).let { e -> if (abs(e.rx - e.ry) < max(e.rx, e.ry) * SQUARE_SNAP) e.copy(rx = max(e.rx, e.ry), ry = max(e.rx, e.ry)) else e }
      ARC -> ArcShape(c, side / 2f, PI.toFloat(), PI.toFloat())
      CURVE -> CurveShape.between(Pt(l, b - h * 0.15f), Pt(r, t + h * 0.15f))
      // From the centre outwards, a half to start with; its end dots change the amount.
      PART_CIRCLE -> SectorShape.of(p0, p1, 0.5f)
      // One triangle: each corner moves on its own and snaps to tidy angles.
      TRIANGLE -> TriangleShape(Pt(c.x, t), Pt(r, t + h), Pt(l, t + h))
      SQUARE -> PolyShape(listOf(Pt(l, t), Pt(l + side, t), Pt(l + side, t + side), Pt(l, t + side)), true)
      // One rectangle tool: it snaps to an exact square when close (the Square tile went).
      RECTANGLE ->
        if (abs(w - h) < max(w, h) * SQUARE_SNAP) {
          val sx = if (p1.x >= p0.x) 1f else -1f
          val sy = if (p1.y >= p0.y) 1f else -1f
          val q = Pt(p0.x + sx * side, p0.y + sy * side)
          PolyShape(listOf(Pt(min(p0.x, q.x), min(p0.y, q.y)), Pt(max(p0.x, q.x), min(p0.y, q.y)), Pt(max(p0.x, q.x), max(p0.y, q.y)), Pt(min(p0.x, q.x), max(p0.y, q.y))), true)
        } else PolyShape(listOf(Pt(l, t), Pt(r, t), Pt(r, b), Pt(l, b)), true)
      DIAMOND -> PolyShape(listOf(Pt(c.x, t), Pt(r, c.y), Pt(c.x, b), Pt(l, c.y)), true, form = PolyForm.DIAMOND)
      PARALLELOGRAM -> PolyShape(listOf(Pt(l + w * 0.25f, t), Pt(r, t), Pt(r - w * 0.25f, b), Pt(l, b)), true, form = PolyForm.PARALLELOGRAM)
      TRAPEZOID -> PolyShape(listOf(Pt(l + w * 0.22f, t), Pt(r - w * 0.22f, t), Pt(r, b), Pt(l, b)), true, form = PolyForm.TRAPEZOID)
      PENTAGON -> regularPolygon(c, side / 2f, 5).copy(form = PolyForm.REGULAR)
      HEXAGON -> regularPolygon(c, side / 2f, 6).copy(form = PolyForm.REGULAR)
      STAR -> star(c, side / 2f).copy(form = PolyForm.REGULAR)
      PIN_SUPPORT -> pinSupport(c, side)
      ROLLER_SUPPORT -> rollerSupport(c, side)
      FIXED_SUPPORT -> fixedSupport(Pt(c.x, c.y - max(w, h) / 2f), Pt(c.x, c.y + max(w, h) / 2f))
      POINT_LOAD -> LineShape(Pt(c.x, t), Pt(c.x, b), arrowEnd = true)
      DISTRIBUTED_LOAD -> distributedLoad(l, r, t, b)
      MOMENT -> ArcShape(c, side / 2f, (0.75 * PI).toFloat(), (1.5 * PI).toFloat(), arrowEnd = true)
    }
  }

  /** Whether dragging should keep the drag's aspect ratio rather than a free box. */
  val dragIsSegment: Boolean
    get() = this == LINE || this == ARROW || this == DOUBLE_ARROW || this == DIMENSION || this == CIRCLE || this == PART_CIRCLE || this == CURVE

  /** Drawn like a line from one end to the other: its end snaps where a line's would (onto edges, at tidy angles). */
  val drawnAsLine: Boolean
    get() = this == LINE || this == ARROW || this == DOUBLE_ARROW || this == DIMENSION || this == POINT_LOAD || this == FIXED_SUPPORT || this == CURVE

  /** Drawn out from a point (a support's tip, a moment's centre): only that first point snaps; the drag just sets its size. */
  val drawnFromPoint: Boolean
    get() = this == PIN_SUPPORT || this == ROLLER_SUPPORT || this == MOMENT || this == CIRCLE || this == PART_CIRCLE
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

/** A pin support whose tip is at [apex], [height] tall down to the bottom of its hatching. */
private fun pinSupportAt(apex: Pt, height: Float): ShapeGeometry {
  // pinSupport(c, size): tip 0.3·size above c, hatching reaching 0.432·size below it.
  val size = height / 0.732f
  return pinSupport(Pt(apex.x, apex.y + 0.3f * size), size)
}

/** A roller support whose tip is at [apex], [height] tall down to the bottom of its hatching. */
private fun rollerSupportAt(apex: Pt, height: Float): ShapeGeometry {
  // rollerSupport(c, size): tip 0.25·size above c, hatching reaching 0.375·size below it.
  val size = height / 0.625f
  return rollerSupport(Pt(apex.x, apex.y + 0.25f * size), size)
}

/** A fixed support: the wall from [a] to [b], hatched on its left (seen going from [a] to [b]; behind the wall drawn downward). */
private fun fixedSupport(a: Pt, b: Pt): ShapeGeometry {
  val size = a.dist(b)
  val d = (b - a).normalized()
  val across = Pt(d.y, -d.x)
  val offset = across * (-size * 0.16f) + d * (size * 0.12f)
  return GroupShape(listOf(LineShape(a, b)) + hatch(a, b, 7, offset))
}

private fun distributedLoad(l: Float, r: Float, t: Float, b: Float): ShapeGeometry =
  LoadShape(Pt(l, t), Pt(r, t), b - t, LoadShape.loadArrows(r - l))

/** A dimension line is a line like any other (end dots, angle snapping, its length shown), with arrows and end marks. */
private fun dimension(p0: Pt, p1: Pt): ShapeGeometry = LineShape(p0, p1, arrowStart = true, arrowEnd = true, ticks = true)

/** How far a dimension line's end marks reach each side (world units). */
private const val TICK = 8f

/**
 * Older saved shapes in today's form: a dimension line saved as a group of three lines (the line
 * and its two end marks) becomes one dimension line, so it can be lengthened and turned like a line.
 */
fun ShapeGeometry.upgraded(): ShapeGeometry {
  if (this is GroupShape) loadOf(this)?.let { return it }
  if (this !is GroupShape || parts.size != 3 || parts.any { it !is LineShape }) return this
  val main = parts[0] as LineShape
  if (!main.arrowStart || !main.arrowEnd) return this
  val ends = parts.drop(1).map { it as LineShape }
  val marksEnds = ends.all { t -> t.a.mid(t.b).dist(main.a) < 0.5f || t.a.mid(t.b).dist(main.b) < 0.5f }
  return if (marksEnds) main.copy(ticks = true) else this
}

/**
 * A distributed load saved as a group (a plain line, then arrows evenly spaced along it, all the
 * same length and starting on it) as a [LoadShape], so its ends can be dragged; null if [g] isn't one.
 */
private fun loadOf(g: GroupShape): LoadShape? {
  if (g.parts.size < 4) return null
  val line = g.parts[0] as? LineShape ?: return null
  if (line.arrowStart || line.arrowEnd || line.ticks || line.length < 1f) return null
  val arrows = g.parts.drop(1).map { it as? LineShape ?: return null }
  if (arrows.any { !it.arrowEnd || it.arrowStart }) return null
  val count = arrows.size - 1
  val tol = max(0.5f, line.length * 0.01f)
  val across = (line.b - line.a).normalized().let { Pt(-it.y, it.x) }
  val depth = (arrows[0].b - arrows[0].a).dot(across)
  val fits = arrows.withIndex().all { (i, arrow) ->
    val on = line.a + (line.b - line.a) * (i.toFloat() / count)
    arrow.a.dist(on) < tol && arrow.b.dist(on + across * depth) < tol
  }
  return if (fits && abs(depth) > 1f) LoadShape(line.a, line.b, depth, count) else null
}

private fun Pt.mid(o: Pt) = Pt((x + o.x) / 2f, (y + o.y) / 2f)

/**
 * What this shape offers for snapping (see [com.tali.notes.geometry.Snap]): its ends, corners,
 * side middles and centre, its edges (a curve as short pieces), and the box of its outline. Arrow
 * barbs and dimension marks aren't edges to snap to; the line itself is.
 */
fun ShapeGeometry.snapFeatures(): com.tali.notes.geometry.SnapFeatures {
  fun segs(path: List<Pt>, closed: Boolean = false): List<Pair<Pt, Pt>> {
    val out = ArrayList<Pair<Pt, Pt>>(path.size)
    for (i in 0 until path.size - 1) out += path[i] to path[i + 1]
    if (closed && path.size > 2) out += path.last() to path.first()
    return out
  }
  fun mid(a: Pt, b: Pt) = Pt((a.x + b.x) / 2f, (a.y + b.y) / 2f)
  val (points, segments) =
    when (this) {
      is LineShape -> listOf(a, b, mid(a, b)) to listOf(a to b)
      is PolyShape ->
        if (smooth) listOf(points.first(), points.last()) to segs(Geo.smooth(points))
        else (points + segs(points, closed).map { (p, q) -> mid(p, q) } + if (closed) listOf(Geo.centroid(points)) else emptyList()) to segs(points, closed)
      is TriangleShape -> (points + segs(points, true).map { (p, q) -> mid(p, q) }) to segs(points, true)
      is EllipseShape -> listOf(center, point(0f), point(PI.toFloat() / 2f), point(PI.toFloat()), point(1.5f * PI.toFloat())) to segs(paths(1f).first())
      is ArcShape -> listOf(center, center + Pt.polar(radius, start), center + Pt.polar(radius, start + sweep)) to segs(paths(1f).first())
      is SectorShape -> listOf(center, center + Pt.polar(radius, start), center + Pt.polar(radius, start + sweep)) to segs(paths(1f).first())
      // A curve: its two ends, and the curve itself.
      is CurveShape -> listOf(points.first(), points.last()) to segs(paths(1f).first())
      // A distributed load: its line, and the line along its arrow tips (which rest on the beam).
      is LoadShape -> {
        val ta = a + (handles()[2] - mid(a, b))
        val tb = b + (handles()[2] - mid(a, b))
        listOf(a, b, mid(a, b), ta, tb, handles()[2]) to listOf(a to b, ta to tb, a to ta, b to tb)
      }
      // A shape with joints snaps like the drawing it shows.
      is ParamShape -> return drawn().snapFeatures()
      // Engineering symbols: their outlines, ground lines and arrows snap; the hatching under a
      // support (five or more short plain lines) doesn't, or its ends would catch on everything.
      is GroupShape -> {
        val box = Rect.around(parts.flatMap { it.paths(1f).flatten() })
        val reach = box?.let { Pt(it.width, it.height).length() } ?: 0f
        val short = parts.filter { it is LineShape && !it.arrowStart && !it.arrowEnd && !it.ticks && it.length < reach * 0.3f }
        val hatching = if (short.size >= 5) short.toSet() else emptySet()
        return com.tali.notes.geometry.SnapFeatures.of(parts.filterNot { it in hatching }.map { it.snapFeatures() })
      }
    }
  val box = Rect.around(points + segments.flatMap { listOf(it.first, it.second) })
  return com.tali.notes.geometry.SnapFeatures(points, segments, box)
}
