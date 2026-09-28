package com.tali.notes.model

import com.tali.notes.geometry.Affine
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
   * Moving an endpoint extends or shortens the line. Close to the original direction the drag is
   * projected onto it, so the line keeps its exact angle while its length changes.
   */
  override fun withHandle(index: Int, p: Pt): ShapeGeometry {
    val fixed = if (index == 0) b else a
    val moving = if (index == 0) a else b
    val dir = (moving - fixed).normalized()
    val v = p - fixed
    val cosAngle = if (v.length() < 1e-3f) 1f else dir.dot(v.normalized())
    val target = if (cosAngle > cos(4.0 * PI / 180).toFloat()) fixed + dir * max(1f, v.dot(dir)) else p
    return if (index == 0) copy(a = target) else copy(b = target)
  }

  val length: Float
    get() = a.dist(b)
}

@Serializable
@SerialName("poly")
data class PolyShape(val points: List<Pt>, val closed: Boolean) : ShapeGeometry {
  override fun paths(penSize: Float) = listOf(if (closed) points + points.first() else points)

  override fun transformed(m: Affine) = copy(points = points.map(m::apply))

  override fun handles() = points

  override fun withHandle(index: Int, p: Pt) = copy(points = points.toMutableList().also { it[index] = p })
}

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

  override fun withHandle(index: Int, p: Pt): ShapeGeometry {
    val ang = (p - center).angle()
    return if (index == 0) {
      val end = start + sweep
      copy(start = ang, sweep = end - ang, radius = max(4f, p.dist(center)))
    } else {
      copy(sweep = normalizeSweep(ang - start, sweep), radius = max(4f, p.dist(center)))
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

private fun normalizeSweep(raw: Float, previous: Float): Float {
  var s = raw
  while (s - previous > PI) s -= TAU
  while (previous - s > PI) s += TAU
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
  TRIANGLE("Triangle", ShapeGroup.BASIC),
  RIGHT_TRIANGLE("Right triangle", ShapeGroup.BASIC),
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
      TRIANGLE -> PolyShape(listOf(Pt(c.x, t), Pt(r, t + h), Pt(l, t + h)), true)
      RIGHT_TRIANGLE -> PolyShape(listOf(Pt(l, t), Pt(l, t + h), Pt(r, t + h)), true)
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
    get() = this == LINE || this == ARROW || this == DOUBLE_ARROW || this == DIMENSION || this == CIRCLE
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
