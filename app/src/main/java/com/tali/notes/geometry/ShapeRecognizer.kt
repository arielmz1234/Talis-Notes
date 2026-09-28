package com.tali.notes.geometry

import com.tali.notes.model.EllipseShape
import com.tali.notes.model.LineShape
import com.tali.notes.model.PolyShape
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
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Turns a hand-drawn stroke into clean geometry: lines, arrows, open polylines, circles, ellipses,
 * triangles, rectangles, squares, diamonds and other polygons, and stars. Returns null when the
 * stroke doesn't look like any of them, so the ink stays as drawn.
 */
object ShapeRecognizer {
  private const val DEG = (PI / 180).toFloat()

  fun recognize(raw: List<Pt>): ShapeGeometry? {
    val points = dedupe(raw)
    if (points.size < 2) return null
    val box = Rect.around(points) ?: return null
    val diag = sqrt(box.width * box.width + box.height * box.height)
    if (diag < 10f) return null
    val length = Geo.pathLength(points)

    val closedAt = closingIndex(points, diag, length)
    return if (closedAt >= 0) recognizeClosed(points.subList(0, closedAt + 1), diag) else recognizeOpen(points, diag, length)
  }

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

    if (simplified.size in 3..8) {
      val fit = Geo.meanDistToPolyline(points, simplified, closed = false)
      if (fit < 0.035f * diag) return PolyShape(simplified.map { it }, closed = false)
    }
    return null
  }

  /** Snaps near-horizontal, vertical and 45° lines exactly, keeping the start point and length. */
  fun snappedLine(a: Pt, b: Pt, arrowEnd: Boolean = false): LineShape {
    val v = b - a
    val angle = v.angle()
    val step = 45f * DEG
    val snapped = round(angle / step) * step
    val end = if (abs(angle - snapped) < 5f * DEG) a + Pt.polar(v.length(), snapped) else b
    return LineShape(a, end, arrowEnd = arrowEnd)
  }

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
    if (useEllipse) return beautifyEllipse(ellipse!!.first)
    if (polygon != null && polyErr < 0.09f) return beautifyPolygon(polygon)
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
    if (ratio > 0.85f) {
      val r = (e.rx + e.ry) / 2f
      return EllipseShape(e.center, r, r, 0f)
    }
    val quarter = 90f * DEG
    val snapped = round(e.rotation / quarter) * quarter
    return if (abs(e.rotation - snapped) < 10f * DEG) e.copy(rotation = snapped) else e
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

  private fun mergeFlatCorners(input: List<Pt>, minEdge: Float): List<Pt> {
    val poly = input.toMutableList()
    var changed = true
    while (changed && poly.size > 3) {
      changed = false
      for (i in poly.indices) {
        val prev = poly[(i - 1 + poly.size) % poly.size]
        val cur = poly[i]
        val next = poly[(i + 1) % poly.size]
        val flat = Geo.interiorAngle(prev, cur, next) > 155f * DEG
        val tiny = cur.dist(next) < minEdge * 0.5f
        if (flat || tiny) {
          poly.removeAt(i)
          changed = true
          break
        }
      }
    }
    return poly
  }

  private fun beautifyPolygon(poly: List<Pt>): ShapeGeometry {
    val c = Geo.centroid(poly)
    return when (poly.size) {
      3 -> beautifyTriangle(poly)
      4 -> beautifyQuad(poly)
      5, 6 -> if (isNearlyRegular(poly, c)) regularFrom(poly, c) else PolyShape(poly, true)
      10 -> starFrom(poly, c) ?: PolyShape(poly, true)
      else -> PolyShape(poly, true)
    }
  }

  private fun beautifyTriangle(t: List<Pt>): ShapeGeometry {
    val sides = listOf(t[0].dist(t[1]), t[1].dist(t[2]), t[2].dist(t[0]))
    val mean = sides.average().toFloat()
    if (sides.all { abs(it - mean) / mean < 0.1f }) {
      val c = Geo.centroid(t)
      val r = t.sumOf { it.dist(c).toDouble() }.toFloat() / 3f
      return regularPolygon(c, r, 3, (t[0] - c).angle())
    }
    // Snap a near-right angle to exactly 90° by moving the corner onto the hypotenuse's Thales circle.
    for (i in 0 until 3) {
      val a = t[(i + 2) % 3]
      val b = t[i]
      val cc = t[(i + 1) % 3]
      if (abs(Geo.interiorAngle(a, b, cc) - 90f * DEG) < 10f * DEG) {
        val mid = (a + cc) / 2f
        val r = a.dist(cc) / 2f
        val fixed = mid + (b - mid).normalized() * r
        val out = t.toMutableList()
        out[i] = fixed
        return PolyShape(axisSnapRightTriangle(out, i), true)
      }
    }
    return PolyShape(t, true)
  }

  /** If the legs of a right triangle are near-axis-aligned, rotate it so they are exactly aligned. */
  private fun axisSnapRightTriangle(t: List<Pt>, corner: Int): List<Pt> {
    val b = t[corner]
    val leg = t[(corner + 1) % 3] - b
    val angle = leg.angle()
    val quarter = 90f * DEG
    val snapped = round(angle / quarter) * quarter
    val delta = snapped - angle
    return if (abs(delta) < 8f * DEG) t.map { it.rotatedAround(b, delta) } else t
  }

  private fun beautifyQuad(q: List<Pt>): ShapeGeometry {
    val angles = q.indices.map { i -> Geo.interiorAngle(q[(i + 3) % 4], q[i], q[(i + 1) % 4]) }
    val c = Geo.centroid(q)
    if (angles.all { abs(it - 90f * DEG) < 14f * DEG }) {
      // Rectangle: orient along the longest edge, snap to the axes when close.
      val edges = q.indices.map { i -> q[(i + 1) % 4] - q[i] }
      val longest = edges.maxBy { it.length() }
      var theta = longest.angle()
      val quarter = 90f * DEG
      val snapped = round(theta / quarter) * quarter
      if (abs(theta - snapped) < 8f * DEG) theta = snapped
      val local = q.map { (it - c).rotated(-theta) }
      val box = Rect.around(local)!!
      var hw = box.width / 2f
      var hh = box.height / 2f
      if (min(hw, hh) / max(hw, hh) > 0.9f) {
        val s = (hw + hh) / 2f
        hw = s
        hh = s
      }
      val center = c + box.center.rotated(theta)
      val corners = listOf(Pt(-hw, -hh), Pt(hw, -hh), Pt(hw, hh), Pt(-hw, hh)).map { center + it.rotated(theta) }
      return PolyShape(corners, true)
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
    if (abs(rot - candidate) < 10f * DEG) rot = candidate
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

  /** Samples an ellipse, used by tests and by the selection hit-test fallback. */
  internal fun ellipsePoints(e: EllipseShape, n: Int = 64): List<Pt> =
    List(n) { i ->
      val t = (2 * PI * i / n).toFloat()
      e.center + Pt(e.rx * cos(t), e.ry * sin(t)).rotated(e.rotation)
    }
}
