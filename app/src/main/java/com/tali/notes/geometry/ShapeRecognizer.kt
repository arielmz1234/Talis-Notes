package com.tali.notes.geometry

import com.tali.notes.model.ArcShape
import com.tali.notes.model.CURVE_DOTS
import com.tali.notes.model.CurveShape
import com.tali.notes.model.CustomShape
import com.tali.notes.model.EllipseShape
import com.tali.notes.model.LineShape
import com.tali.notes.model.PolyShape
import com.tali.notes.model.TriangleShape
import com.tali.notes.model.ShapeGeometry
import com.tali.notes.model.regularPolygon
import com.tali.notes.model.star
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.round
import kotlin.math.sqrt

/**
 * Turns a hand-drawn stroke into clean geometry: lines, arrows, arcs, curves, an L or Z, circles,
 * ellipses, triangles, rectangles, squares, diamonds and other polygons, and stars. Returns null
 * when the stroke doesn't clearly look like any of them, so the ink stays as drawn: a shape she
 * didn't mean is worse than none (Tali, 26 Oct 2026: a held arch came out as a jagged polyline).
 */
object ShapeRecognizer {
  private const val DEG = (PI / 180).toFloat()
  /** A bend gentler than 130° that sticks out less than this share of the shape's size is her hand, not a corner. */
  private const val GENTLE_BEND = 0.14f
  /** A turn sharper than this from one step of the finely sampled stroke to the next is a corner, not a bend. */
  private val CORNER_TURN = (40 * PI / 180).toFloat()
  /** An L's or Z's corners turn at least this much. */
  private val SHARP_CORNER = (35 * PI / 180).toFloat()
  /** A hook is a turn sharper than this within the first or last [HOOK] of the stroke. */
  private val HOOK_TURN = (50 * PI / 180).toFloat()
  private const val HOOK = 0.08f
  /** Arcs go between these sweeps; less is barely bent, more is nearly a circle. */
  private val ARC_MIN = (40 * PI / 180).toFloat()
  private val ARC_MAX = (300 * PI / 180).toFloat()
  /** Within this of a half circle, it's exactly one. */
  private val HALF_SNAP = (15 * PI / 180).toFloat()
  /** A half circle's ends this close to level or upright are made so. */
  private val LEVEL_SNAP = (10 * PI / 180).toFloat()

  /**
   * The clean shape for a held stroke: one of the standard shapes, one of hers ([custom]), or one of
   * the starred subjects' shapes ([packs]; never a subject she hasn't starred), whichever it looks
   * most like, when it's close enough.
   */
  fun recognize(raw: List<Pt>, custom: List<CustomShape>, packs: List<com.tali.notes.model.PackShape> = emptyList()): ShapeGeometry? {
    val builtin = recognize(raw)
    if ((custom.isEmpty() && packs.none { it.cloud != null }) || builtin is LineShape) return builtin
    val box = Rect.around(raw) ?: return builtin
    val drawn = ShapeMatcher.cloud(listOf(dedupe(raw))) ?: return builtin
    var bestD = ShapeMatcher.MATCH
    var best: (() -> ShapeGeometry)? = null
    fun consider(template: ShapeMatcher.Cloud?, place: () -> ShapeGeometry) {
      if (template == null || !ShapeMatcher.similarAspect(drawn, template)) return
      val d = ShapeMatcher.distance(drawn, template)
      if (d < bestD) {
        bestD = d
        best = place
      }
    }
    custom.forEach { shape -> consider(shape.cloud) { shape.placeOver(box) } }
    packs.forEach { shape -> consider(shape.cloud) { shape.placeOver(box, raw.first(), raw.last()) } }
    return best?.invoke() ?: builtin
  }

  /** The saved shape that the stroke looks most like, or null when none is close enough. */
  fun bestCustom(raw: List<Pt>, custom: List<CustomShape>): CustomShape? {
    val drawn = ShapeMatcher.cloud(listOf(dedupe(raw))) ?: return null
    var best: CustomShape? = null
    var bestD = ShapeMatcher.MATCH
    for (shape in custom) {
      val template = shape.cloud ?: continue
      if (!ShapeMatcher.similarAspect(drawn, template)) continue
      val d = ShapeMatcher.distance(drawn, template)
      if (d < bestD) {
        bestD = d
        best = shape
      }
    }
    return best
  }

  fun recognize(raw: List<Pt>): ShapeGeometry? {
    val points = dedupe(raw)
    if (points.size < 2) return null
    val box = Rect.around(points) ?: return null
    val diag = sqrt(box.width * box.width + box.height * box.height)
    if (diag < 10f) return null
    val length = Geo.pathLength(points)

    val closedAt = closingIndex(points, diag, length)
    val shape = if (closedAt >= 0) recognizeClosed(points.subList(0, closedAt + 1), diag) else recognizeOpen(points, diag, length)
    return asTriangle(shape)
  }

  /** A three-cornered closed shape becomes a [TriangleShape], whose corners move one at a time. */
  fun asTriangle(g: ShapeGeometry?): ShapeGeometry? =
    if (g is PolyShape && g.closed && g.points.size == 3) TriangleShape(g.points[0], g.points[1], g.points[2]) else g

  private fun dedupe(points: List<Pt>): List<Pt> {
    val out = ArrayList<Pt>(points.size)
    for (p in points) if (out.isEmpty() || out.last().dist(p) > 0.5f) out.add(p)
    return out
  }

  /**
   * Index where the stroke meets its start again, or -1 when it's open. Looks at the tail of the
   * stroke so overshooting past the starting point still counts as closed.
   */
  private fun closingIndex(points: List<Pt>, diag: Float, length: Float): Int {
    if (length < 1.6f * diag) return -1
    val start = points.first()
    var best = -1
    var bestD = Float.MAX_VALUE
    var travelled = 0f
    for (i in 1 until points.size) {
      travelled += points[i].dist(points[i - 1])
      if (travelled < length * 0.6f) continue
      val d = points[i].dist(start)
      if (d < bestD) {
        bestD = d
        best = i
      }
    }
    return if (best >= 0 && bestD < 0.22f * diag) best else -1
  }

  // ---- Open strokes ----

  private fun recognizeOpen(points: List<Pt>, diag: Float, length: Float): ShapeGeometry? {
    val a = points.first()
    val b = points.last()
    val chord = a.dist(b)
    if (chord > 0f) {
      val maxDev = points.maxOf { Geo.distToLine(it, a, b) }
      if (maxDev < max(0.07f * chord, 3f) && length < 1.3f * chord) return snappedLine(a, b)
    }

    val simplified = Geo.simplify(points, max(0.08f * diag, 5f))
    arrow(simplified)?.let { return it }

    // The little hooks where the pen lands and lifts aren't part of what she drew.
    val body = withoutHooks(points)
    // A stroke along part of a circle (an arch, a semicircle) becomes an Arc: the most specific
    // smooth shape, so it's tried before the general Curve.
    fitArc(body, diag)?.let { return it }
    // A stroke that bends without corners (up and down, an S, a bow) becomes a Curve.
    if (!hasCorner(body)) fitCurve(body, diag)?.let { return it }

    // An open polyline only when it's clearly two or three straight sides meeting at real corners
    // (an L, a Z); anything else stays as she wrote it.
    if (simplified.size in 3..4) {
      val fit = Geo.meanDistToPolyline(points, simplified, closed = false)
      val sides = (0 until simplified.size - 1).map { simplified[it].dist(simplified[it + 1]) }
      val sharp = (1 until simplified.size - 1).all { i -> turnAt(simplified[i - 1], simplified[i], simplified[i + 1]) > SHARP_CORNER }
      if (fit < 0.03f * diag && sharp && sides.all { it > 0.15f * length }) return PolyShape(simplified.map { it }, closed = false)
    }
    return null
  }

  /** How much the path turns at [b] coming from [a] and going on to [c] (radians, 0 = straight on). */
  private fun turnAt(a: Pt, b: Pt, c: Pt): Float {
    var t = abs((c - b).angle() - (b - a).angle())
    if (t > PI.toFloat()) t = 2 * PI.toFloat() - t
    return t
  }

  /**
   * The stroke without a hook at either end: within the first or last [HOOK] of its length, a
   * sharp turn (the pen landing or lifting) and everything beyond it are dropped.
   */
  private fun withoutHooks(points: List<Pt>): List<Pt> {
    val r = Geo.resample(points, 100)
    if (r.size < 100) return points
    val reach = (HOOK * 100).toInt()
    var from = 0
    var to = r.size - 1
    for (i in 1..reach) if (turnAt(r[i - 1], r[i], r[i + 1]) > HOOK_TURN) from = i
    for (i in r.size - 2 downTo r.size - 1 - reach) if (turnAt(r[i - 1], r[i], r[i + 1]) > HOOK_TURN) to = i
    return r.subList(from, to + 1)
  }

  /**
   * An arc of a circle through the stroke (fitted by least squares), or null when it doesn't follow
   * one closely, turns back on itself, or covers too little or too much of a circle. Close to a
   * half circle it becomes exactly one, its ends level or upright when they nearly are.
   */
  private fun fitArc(points: List<Pt>, diag: Float): ArcShape? {
    val r = Geo.resample(points, 64)
    if (r.size < 8) return null
    // Kåsa fit: x² + y² + Dx + Ey + F = 0, least squares (centred on the mean for accuracy).
    val mx = r.sumOf { it.x.toDouble() } / r.size
    val my = r.sumOf { it.y.toDouble() } / r.size
    var suu = 0.0; var suv = 0.0; var svv = 0.0; var suuu = 0.0; var svvv = 0.0; var suvv = 0.0; var svuu = 0.0
    for (p in r) {
      val u = p.x - mx
      val v = p.y - my
      suu += u * u; suv += u * v; svv += v * v
      suuu += u * u * u; svvv += v * v * v; suvv += u * v * v; svuu += v * u * u
    }
    val det = suu * svv - suv * suv
    if (abs(det) < 1e-9) return null
    val bu = (suuu + suvv) / 2
    val bv = (svvv + svuu) / 2
    val uc = (bu * svv - bv * suv) / det
    val vc = (bv * suu - bu * suv) / det
    val center = Pt((uc + mx).toFloat(), (vc + my).toFloat())
    val radius = sqrt(uc * uc + vc * vc + (suu + svv) / r.size).toFloat()
    // Nearly straight (a huge circle) is a line's or a curve's business.
    if (radius > 1.5f * diag || radius < 4f) return null
    val off = r.map { abs(it.dist(center) - radius) }
    if (off.max() > 0.06f * diag || off.average() > 0.02f * diag) return null
    // How far round it goes, and that it keeps going one way.
    var sweep = 0f
    var travel = 0f
    for (i in 1 until r.size) {
      var d = (r[i] - center).angle() - (r[i - 1] - center).angle()
      if (d > PI) d -= 2 * PI.toFloat()
      if (d < -PI) d += 2 * PI.toFloat()
      sweep += d
      travel += abs(d)
    }
    if (travel > abs(sweep) * 1.15f) return null
    if (abs(sweep) < ARC_MIN || abs(sweep) > ARC_MAX) return null
    var start = (r.first() - center).angle()
    if (abs(abs(sweep) - PI.toFloat()) < HALF_SNAP) {
      sweep = if (sweep > 0) PI.toFloat() else -PI.toFloat()
      val quarter = (PI / 2).toFloat()
      val snapped = round(start / quarter) * quarter
      if (abs(start - snapped) < LEVEL_SNAP) start = snapped
    }
    return ArcShape(center, radius, start, sweep)
  }

  /**
   * Whether the stroke turns sharply somewhere (a corner, as in an L or a zigzag) rather than
   * bending smoothly: measured on the stroke evenly resampled, over short stretches of it.
   */
  private fun hasCorner(points: List<Pt>): Boolean {
    // Finely sampled, a corner turns all at once (in one or two steps) while a bend, even a tight
    // one at the top of a wave, spreads its turn over many.
    val r = Geo.resample(points, 96)
    for (i in 1 until r.size - 1) {
      val before = r[i] - r[i - 1]
      val after = r[i + 1] - r[i]
      if (before.length() < 1e-3f || after.length() < 1e-3f) continue
      var turn = abs(after.angle() - before.angle())
      if (turn > PI.toFloat()) turn = 2 * PI.toFloat() - turn
      if (turn > CORNER_TURN) return true
    }
    return false
  }

  /**
   * A smooth curve through the stroke, or null when none follows it closely. Its dots sit where
   * the stroke turns (the top and bottom of each wave, found by simplifying it), with more added
   * along the longest stretches so there are always at least [CURVE_DOTS], straight parts included.
   */
  private fun fitCurve(points: List<Pt>, diag: Float): CurveShape? {
    val stroke = Geo.resample(points, 120)
    fun close(c: CurveShape): Boolean {
      val path = c.paths(1f).first()
      val worst = stroke.maxOf { p -> (0 until path.size - 1).minOf { i -> Geo.distToSegment(p, path[i], path[i + 1]) } }
      return worst < 0.05f * diag && Geo.meanDistToPolyline(stroke, path, closed = false) < 0.02f * diag
    }
    for (tolerance in listOf(0.05f, 0.035f, 0.02f)) {
      val turns = Geo.simplify(stroke, tolerance * diag)
      if (turns.size > 12) break
      // Where each dot sits along the stroke, then more between the farthest-apart ones.
      val at = turns.map { t -> stroke.indexOf(t) }.toMutableList()
      while (at.size < CURVE_DOTS) {
        val gap = (0 until at.size - 1).maxBy { at[it + 1] - at[it] }
        if (at[gap + 1] - at[gap] < 2) break
        at.add(gap + 1, (at[gap] + at[gap + 1]) / 2)
      }
      val curve = CurveShape(at.map { stroke[it] })
      if (close(curve)) return curve
    }
    return null
  }

  /**
   * Snaps lines to every 45° (level, upright and diagonal) when within [LINE_SNAP_DEG], keeping the
   * start point and length; in between, the line goes exactly where it's drawn. (Finer 15° steps
   * were dropped: she wanted only the 45° ones.)
   */
  fun snappedLine(a: Pt, b: Pt, arrowEnd: Boolean = false): LineShape {
    val v = b - a
    val angle = v.angle()
    val wide = round(angle / (45f * DEG)) * (45f * DEG)
    val end = if (abs(angle - wide) < LINE_SNAP_DEG * DEG) a + Pt.polar(v.length(), wide) else b
    return LineShape(a, end, arrowEnd = arrowEnd)
  }

  const val LINE_SNAP_DEG = 6f

  /**
   * One-stroke arrows: a shaft followed by one barb, or by a barb, a return to the tip and a second
   * barb. The barbs must be short compared to the shaft and point back along it.
   */
  private fun arrow(v: List<Pt>): ShapeGeometry? {
    if (v.size !in 3..5) return null
    val start = v[0]
    val tip = v[1]
    val shaft = start.dist(tip)
    if (shaft < 20f) return null
    val back = (start - tip).normalized()
    fun isBarb(p: Pt): Boolean {
      val d = p.dist(tip)
      if (d < shaft * 0.06f || d > shaft * 0.45f) return false
      val cosA = back.dot((p - tip).normalized())
      return cosA > cos(75f * DEG) && cosA < cos(10f * DEG)
    }
    val ok =
      when (v.size) {
        3 -> isBarb(v[2])
        4 -> isBarb(v[2]) && (v[3].dist(tip) < shaft * 0.12f || isBarb(v[3]))
        else -> isBarb(v[2]) && v[3].dist(tip) < shaft * 0.15f && isBarb(v[4])
      }
    return if (ok) snappedLine(start, tip, arrowEnd = true) else null
  }

  // ---- Closed strokes ----

  private fun recognizeClosed(points: List<Pt>, diag: Float): ShapeGeometry? {
    val samples = Geo.resample(points, 72)
    val centroid = Geo.centroid(samples)
    val meanRadius = samples.sumOf { it.dist(centroid).toDouble() }.toFloat() / samples.size
    if (meanRadius < 4f) return null

    val ellipse = fitEllipse(samples)
    val polygon = fitPolygon(samples, centroid, diag)
    val polyErr = polygon?.let { Geo.meanDistToPolyline(samples, it, closed = true) / meanRadius } ?: Float.MAX_VALUE

    val useEllipse = ellipse != null && ellipse.second < 0.12f && (polygon == null || polygon.size > 6 || ellipse.second < polyErr)
    if (useEllipse) return beautifyEllipse(ellipse.first)
    if (polygon != null && polyErr < 0.09f) beautifyPolygon(polygon)?.let { return it }
    if (ellipse != null && ellipse.second < 0.16f) return beautifyEllipse(ellipse.first)
    return null
  }

  /** Principal-axis ellipse fit. Returns the ellipse and its mean relative radial error. */
  private fun fitEllipse(samples: List<Pt>): Pair<EllipseShape, Float>? {
    val c0 = Geo.centroid(samples)
    var sxx = 0f
    var syy = 0f
    var sxy = 0f
    for (p in samples) {
      val d = p - c0
      sxx += d.x * d.x
      syy += d.y * d.y
      sxy += d.x * d.y
    }
    val theta = 0.5f * atan2(2f * sxy, sxx - syy)
    val local = samples.map { (it - c0).rotated(-theta) }
    val box = Rect.around(local) ?: return null
    val rx = box.width / 2f
    val ry = box.height / 2f
    if (rx < 2f || ry < 2f) return null
    val center = c0 + box.center.rotated(theta)
    var err = 0f
    for (p in samples) {
      val q = (p - center).rotated(-theta)
      val r = sqrt((q.x / rx) * (q.x / rx) + (q.y / ry) * (q.y / ry))
      err += abs(r - 1f)
    }
    return EllipseShape(center, rx, ry, theta) to err / samples.size
  }

  private fun beautifyEllipse(e: EllipseShape): EllipseShape {
    val ratio = min(e.rx, e.ry) / max(e.rx, e.ry)
    if (ratio > 0.8f) {
      val r = (e.rx + e.ry) / 2f
      return EllipseShape(e.center, r, r, 0f)
    }
    val quarter = 90f * DEG
    val snapped = round(e.rotation / quarter) * quarter
    return if (abs(e.rotation - snapped) < 15f * DEG) e.copy(rotation = snapped) else e
  }

  /** Corner detection on a closed stroke: simplify from the point farthest from the centre, then merge flat corners. */
  private fun fitPolygon(samples: List<Pt>, centroid: Pt, diag: Float): List<Pt>? {
    val loop = samples.dropLast(1)
    val far = loop.indices.maxBy { loop[it].dist(centroid) }
    val rotated = loop.subList(far, loop.size) + loop.subList(0, far) + loop[far]
    var poly = Geo.simplify(rotated, 0.06f * diag).dropLast(1)
    poly = mergeFlatCorners(poly, 0.1f * diag)
    return if (poly.size in 3..12) poly else null
  }

  /**
   * Takes away corners that aren't really corners, weakest first: nearly straight ones, ones on a
   * tiny edge, and gentle bends that stick out only a little from the line between their neighbours
   * (a side that drifts up partway, or a small bump: her hand, not a corner she meant). A real
   * corner of a pentagon or hexagon sticks out a quarter or more of the shape's size.
   */
  private fun mergeFlatCorners(input: List<Pt>, minEdge: Float): List<Pt> {
    val diag = minEdge * 10f
    val poly = input.toMutableList()
    while (poly.size > 3) {
      var weakest = -1
      var weakestHeight = Float.MAX_VALUE
      for (i in poly.indices) {
        val prev = poly[(i - 1 + poly.size) % poly.size]
        val cur = poly[i]
        val next = poly[(i + 1) % poly.size]
        val angle = Geo.interiorAngle(prev, cur, next)
        val height = Geo.distToSegment(cur, prev, next)
        val flat = angle > 155f * DEG
        val tiny = cur.dist(next) < minEdge * 0.5f
        val gentle = angle > 130f * DEG && height < GENTLE_BEND * diag
        if ((flat || tiny || gentle) && height < weakestHeight) {
          weakest = i
          weakestHeight = height
        }
      }
      if (weakest < 0) break
      poly.removeAt(weakest)
    }
    return poly
  }

  /**
   * The clean polygon, or null for a many-cornered or lopsided one: only triangles, four-sided
   * shapes, even pentagons and hexagons, and stars are made; anything else was more likely a
   * rounded shape or writing than a polygon she meant.
   */
  private fun beautifyPolygon(poly: List<Pt>): ShapeGeometry? {
    val c = Geo.centroid(poly)
    return when (poly.size) {
      3 -> beautifyTriangle(poly)
      4 -> beautifyQuad(poly)
      5, 6 -> if (isNearlyRegular(poly, c)) regularFrom(poly, c) else null
      10 -> starFrom(poly, c)
      else -> null
    }
  }

  private fun beautifyTriangle(t: List<Pt>): ShapeGeometry {
    val sides = listOf(t[0].dist(t[1]), t[1].dist(t[2]), t[2].dist(t[0]))
    val mean = sides.average().toFloat()
    if (sides.all { abs(it - mean) / mean < 0.15f }) return regularFrom(t, Geo.centroid(t))
    // Snap a near-right angle to exactly 90° by moving the corner onto the hypotenuse's Thales circle.
    for (i in 0 until 3) {
      val a = t[(i + 2) % 3]
      val b = t[i]
      val cc = t[(i + 1) % 3]
      if (abs(Geo.interiorAngle(a, b, cc) - 90f * DEG) < 15f * DEG) {
        val mid = (a + cc) / 2f
        val r = a.dist(cc) / 2f
        val fixed = mid + (b - mid).normalized() * r
        val out = t.toMutableList()
        out[i] = fixed
        return PolyShape(axisSnapRightTriangle(out, i), true)
      }
    }
    return PolyShape(levelBase(isoscelesFrom(t) ?: t), true)
  }

  /**
   * Two sides within 15% of each other become exactly equal: the apex moves onto the base's
   * perpendicular bisector, keeping its height, so the triangle reads as symmetric.
   */
  private fun isoscelesFrom(t: List<Pt>): List<Pt>? {
    for (apex in 0 until 3) {
      val a = t[(apex + 1) % 3]
      val b = t[(apex + 2) % 3]
      val s1 = t[apex].dist(a)
      val s2 = t[apex].dist(b)
      if (abs(s1 - s2) / max(s1, s2) >= 0.15f) continue
      val mid = (a + b) / 2f
      val base = (b - a).normalized()
      val normal = Pt(-base.y, base.x)
      val height = (t[apex] - mid).dot(normal)
      val out = t.toMutableList()
      out[apex] = mid + normal * height
      return out
    }
    return null
  }

  /** Rotates a triangle about its centre so the edge closest to horizontal or vertical lies exactly on the axis (within 15°). */
  private fun levelBase(t: List<Pt>): List<Pt> {
    val quarter = 90f * DEG
    var best = 0f
    var bestAbs = Float.MAX_VALUE
    for (i in 0 until 3) {
      val angle = (t[(i + 1) % 3] - t[i]).angle()
      val delta = round(angle / quarter) * quarter - angle
      if (abs(delta) < bestAbs) {
        bestAbs = abs(delta)
        best = delta
      }
    }
    if (bestAbs >= 15f * DEG) return t
    val c = Geo.centroid(t)
    return t.map { it.rotatedAround(c, best) }
  }

  /** If the legs of a right triangle are near-axis-aligned, rotate it so they are exactly aligned. */
  private fun axisSnapRightTriangle(t: List<Pt>, corner: Int): List<Pt> {
    val b = t[corner]
    val leg = t[(corner + 1) % 3] - b
    val angle = leg.angle()
    val quarter = 90f * DEG
    val snapped = round(angle / quarter) * quarter
    val delta = snapped - angle
    return if (abs(delta) < 15f * DEG) t.map { it.rotatedAround(b, delta) } else t
  }

  private fun beautifyQuad(q: List<Pt>): ShapeGeometry {
    val angles = q.indices.map { i -> Geo.interiorAngle(q[(i + 3) % 4], q[i], q[(i + 1) % 4]) }
    val c = Geo.centroid(q)
    if (angles.all { abs(it - 90f * DEG) < 20f * DEG }) {
      // Rectangle: orient along the longest edge, snap to the axes when close.
      val edges = q.indices.map { i -> q[(i + 1) % 4] - q[i] }
      val longest = edges.maxBy { it.length() }
      var theta = longest.angle()
      val quarter = 90f * DEG
      val snapped = round(theta / quarter) * quarter
      if (abs(theta - snapped) < 15f * DEG) theta = snapped
      val local = q.map { (it - c).rotated(-theta) }
      val box = Rect.around(local)!!
      var hw = box.width / 2f
      var hh = box.height / 2f
      if (min(hw, hh) / max(hw, hh) > 0.88f) {
        val s = (hw + hh) / 2f
        hw = s
        hh = s
      }
      val center = c + box.center.rotated(theta)
      val corners = listOf(Pt(-hw, -hh), Pt(hw, -hh), Pt(hw, hh), Pt(-hw, hh)).map { center + it.rotated(theta) }
      return PolyShape(corners, true, rotation = theta)
    }
    // Diamond: corners sit near the midpoints of the bounding box edges.
    val box = Rect.around(q)!!
    val mids = listOf(Pt(box.center.x, box.top), Pt(box.right, box.center.y), Pt(box.center.x, box.bottom), Pt(box.left, box.center.y))
    val tol = 0.15f * max(box.width, box.height)
    if (q.all { p -> mids.any { it.dist(p) < tol } }) {
      val ordered = mids.sortedBy { m -> q.indexOf(q.minBy { it.dist(m) }) }
      return PolyShape(ordered, true)
    }
    return PolyShape(q, true)
  }

  private fun isNearlyRegular(poly: List<Pt>, c: Pt): Boolean {
    val radii = poly.map { it.dist(c) }
    val meanR = radii.average().toFloat()
    val sides = poly.indices.map { poly[it].dist(poly[(it + 1) % poly.size]) }
    val meanS = sides.average().toFloat()
    return radii.all { abs(it - meanR) / meanR < 0.18f } && sides.all { abs(it - meanS) / meanS < 0.25f }
  }

  private fun regularFrom(poly: List<Pt>, c: Pt): PolyShape {
    val r = poly.sumOf { it.dist(c).toDouble() }.toFloat() / poly.size
    var rot = (poly[0] - c).angle()
    // Prefer a flat bottom edge / upright orientation when the drawing is close to it.
    val step = (2 * PI / poly.size).toFloat()
    val upright = (-PI / 2).toFloat()
    val k = round((rot - upright) / step)
    val candidate = upright + k * step
    if (abs(rot - candidate) < 15f * DEG) rot = candidate
    return regularPolygon(c, r, poly.size, rot)
  }

  /** Ten corners alternating between far and near the centre read as a five-pointed star. */
  private fun starFrom(poly: List<Pt>, c: Pt): ShapeGeometry? {
    val radii = poly.map { it.dist(c) }
    val evenMean = radii.filterIndexed { i, _ -> i % 2 == 0 }.average().toFloat()
    val oddMean = radii.filterIndexed { i, _ -> i % 2 == 1 }.average().toFloat()
    val outerIsEven = evenMean > oddMean
    val outer = max(evenMean, oddMean)
    val inner = min(evenMean, oddMean)
    if (inner / outer > 0.75f) return null
    val firstOuter = if (outerIsEven) poly[0] else poly[1]
    return star(c, outer, (firstOuter - c).angle())
  }
}
