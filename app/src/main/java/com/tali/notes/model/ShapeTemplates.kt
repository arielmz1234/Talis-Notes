package com.tali.notes.model

import com.tali.notes.geometry.Affine
import com.tali.notes.geometry.Pt
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A subject shape with joints: dots that change what it is, not just its size. A stack grows more
 * cells as it's pulled longer, a binary tree more levels, a bar chart has a dot on every bar, a
 * thermometer a dot on its level. Stored as its [template], where it sits ([place]: local frame to
 * page), its frame size ([w] × [h], in page units, or the template's own units for one that keeps
 * its proportions) and its settings [v]; the drawing is rebuilt from those whenever they change.
 */
@Serializable
@SerialName("param")
data class ParamShape(val template: String, val place: Affine, val w: Float, val h: Float, val v: List<Float> = emptyList()) : ShapeGeometry {
  private val spec: ShapeTemplate?
    get() = ShapeTemplates.get(template)

  private val frame: Frame
    get() = Frame(w, h, v)

  private val drawing: ShapeGeometry by lazy { (spec?.render(frame) ?: GroupShape(emptyList())).transformed(place) }

  /** The drawing on the page (for snapping, which treats it like any group of lines). */
  fun drawn(): ShapeGeometry = drawing

  override fun paths(penSize: Float) = drawing.paths(penSize)

  // Affine.then applies the left-hand transform first: here, the shape's own place, then [m].
  override fun transformed(m: Affine) = copy(place = place.then(m))

  override fun handles(): List<Pt> {
    val s = spec ?: return emptyList()
    val ends = if (s.mode == ShapeTemplate.Mode.LINE) listOf(Pt(0f, 0f), Pt(w, 0f)) else emptyList()
    return (ends + s.joints(frame).map { it.at }).map(place::apply)
  }

  override fun withHandle(index: Int, p: Pt): ShapeGeometry {
    val s = spec ?: return this
    val local = place.inverse()?.apply(p) ?: return this
    if (s.mode == ShapeTemplate.Mode.LINE && index < 2) {
      // An end: like a line's, it re-aims and lengthens the shape; the other end stays put.
      return if (index == 1) {
        val angle = atan2(local.y, local.x)
        copy(place = rotation(angle).then(place), w = max(local.dist(Pt(0f, 0f)), s.minW))
      } else {
        val end = Pt(w, 0f)
        val d = end - local
        val angle = atan2(d.y, d.x)
        copy(place = rotation(angle).then(Affine.translate(local.x, local.y)).then(place), w = max(d.length(), s.minW))
      }
    }
    val joints = s.joints(frame)
    val joint = joints.getOrNull(if (s.mode == ShapeTemplate.Mode.LINE) index - 2 else index) ?: return this
    val f = joint.move(frame, local)
    val moved = if (f.shift.x != 0f || f.shift.y != 0f) Affine.translate(f.shift.x, f.shift.y).then(place) else place
    return copy(place = moved, w = f.w, h = f.h, v = f.v)
  }
}

private fun rotation(angle: Float): Affine {
  val c = cos(angle)
  val s = sin(angle)
  return Affine(a = c, b = s, c = -s, d = c)
}

/** The transform that undoes this one, or null if it flattens everything. */
internal fun Affine.inverse(): Affine? {
  val det = a * d - b * c
  if (abs(det) < 1e-9f) return null
  val ia = d / det
  val ib = -b / det
  val ic = -c / det
  val id = a / det
  return Affine(a = ia, b = ib, c = ic, d = id, tx = -(ia * tx + ic * ty), ty = -(ib * tx + id * ty))
}

/** A shape's frame size and settings; [shift] moves its origin (a joint on the top or left edge). */
data class Frame(val w: Float, val h: Float, val v: List<Float>, val shift: Pt = Pt(0f, 0f)) {
  fun v(i: Int, default: Float) = v.getOrNull(i) ?: default

  fun set(i: Int, value: Float, fill: (Int) -> Float = { 0f }): Frame {
    val l = v.toMutableList()
    while (l.size <= i) l += fill(l.size)
    l[i] = value
    return copy(v = l)
  }

  fun sized(w: Float = this.w, h: Float = this.h) = copy(w = w, h = h)
}

/** A dot on a shape (in its frame) and what dragging it to a point (in its frame) does. */
class Joint(val at: Pt, val move: (Frame, Pt) -> Frame)

/**
 * How one shape with joints is drawn and what its joints do. [Mode.SIZED] fills the dragged box at
 * its real size (so a longer stack has more cells), [Mode.NATURAL] keeps its proportions in its own
 * [naturalW] × [naturalH] frame, scaled to the box, and [Mode.LINE] runs along x from where the pen
 * landed to where it lifted, with end dots like a line's.
 */
class ShapeTemplate internal constructor(
  val id: String,
  val mode: Mode,
  val naturalW: Float,
  val naturalH: Float,
  val minW: Float,
  val minH: Float,
  val defaults: List<Float>,
  val joints: (Frame) -> List<Joint>,
  private val draw: Draw.(Frame) -> Unit,
  /** Drawn square (a circle's frame), whatever box is dragged. */
  val round: Boolean = false,
) {
  enum class Mode { SIZED, NATURAL, LINE }

  fun render(f: Frame): ShapeGeometry = Draw().apply { draw(f) }.build()

  /** As drawn from [p0] to [p1]. */
  fun create(p0: Pt, p1: Pt): ParamShape =
    when (mode) {
      Mode.LINE -> {
        val len = max(p0.dist(p1), minW)
        val angle = if (p0.dist(p1) < 1f) 0f else atan2(p1.y - p0.y, p1.x - p0.x)
        ParamShape(id, rotation(angle).then(Affine.translate(p0.x, p0.y)), len, 0f, defaults)
      }
      Mode.SIZED -> {
        val l = min(p0.x, p1.x)
        val t = min(p0.y, p1.y)
        val w = max(abs(p1.x - p0.x), minW)
        val h = max(abs(p1.y - p0.y), minH)
        if (round) {
          // Round ones (a pie, a gear) are square, centred in the box.
          val d = min(w, h)
          ParamShape(id, Affine.translate(l + (w - d) / 2f, t + (h - d) / 2f), d, d, defaults)
        } else ParamShape(id, Affine.translate(l, t), w, h, defaults)
      }
      Mode.NATURAL -> {
        val w = max(abs(p1.x - p0.x), 8f)
        val h = max(abs(p1.y - p0.y), 8f)
        val s = min(w / naturalW, h / naturalH)
        val cx = (p0.x + p1.x) / 2f - naturalW * s / 2f
        val cy = (p0.y + p1.y) / 2f - naturalH * s / 2f
        ParamShape(id, Affine(a = s, d = s, tx = cx, ty = cy), naturalW, naturalH, defaults)
      }
    }

  /** For the panel's tile and hold-to-shape: drawn at its usual size. */
  val icon: ShapeGeometry by lazy {
    when (mode) {
      Mode.LINE -> render(Frame(naturalW, 0f, defaults))
      else -> render(Frame(naturalW, naturalH, defaults))
    }
  }
}

// ---- Joint helpers ----

/** Pulls the right edge: wider (more cells, more bars…). */
private fun widthJoint(f: Frame, min: Float, y: Float = f.h / 2f) = Joint(Pt(f.w, y)) { g, p -> g.sized(w = max(p.x, min)) }

/** Pulls the bottom edge: taller (more levels, more rows…). */
private fun heightJoint(f: Frame, min: Float, x: Float = f.w / 2f) = Joint(Pt(x, f.h)) { g, p -> g.sized(h = max(p.y, min)) }

/** Pulls the top edge, keeping the bottom where it is (a chart's axis). */
private fun topJoint(f: Frame, min: Float, x: Float = 0f) =
  Joint(Pt(x, 0f)) { g, p ->
    val top = min(p.y, g.h - min)
    g.copy(h = g.h - top, shift = Pt(0f, top))
  }

/** The width and height joints together, for shapes that stretch both ways. */
private fun sizeJoints(f: Frame, minW: Float, minH: Float) = listOf(widthJoint(f, minW), heightJoint(f, minH))

/** A circle's size: one dot on its right that sets its diameter. */
private fun diameterJoint(f: Frame, min: Float) = Joint(Pt(min(f.w, f.h), min(f.w, f.h) / 2f)) { g, p -> max(p.x, min).let { d -> g.sized(w = d, h = d) } }

private fun clampF(x: Float, lo: Float, hi: Float) = x.coerceIn(lo, hi)

private fun count(x: Float, lo: Int, hi: Int) = x.roundToInt().coerceIn(lo, hi)

private fun deg(d: Float) = (d * PI / 180.0).toFloat()

object ShapeTemplates {
  private val all = mutableMapOf<String, ShapeTemplate>()

  fun get(id: String): ShapeTemplate? = all[id]

  private fun sized(id: String, w: Float, h: Float, minW: Float, minH: Float, defaults: List<Float> = emptyList(), joints: (Frame) -> List<Joint> = { emptyList() }, round: Boolean = false, draw: Draw.(Frame) -> Unit) =
    ShapeTemplate(id, ShapeTemplate.Mode.SIZED, w, h, minW, minH, defaults, joints, draw, round).also { all[id] = it }

  private fun natural(id: String, w: Float = 100f, h: Float = 100f, defaults: List<Float> = emptyList(), joints: (Frame) -> List<Joint> = { emptyList() }, draw: Draw.(Frame) -> Unit) =
    ShapeTemplate(id, ShapeTemplate.Mode.NATURAL, w, h, 8f, 8f, defaults, joints, draw).also { all[id] = it }

  private fun line(id: String, length: Float, minLength: Float, defaults: List<Float> = emptyList(), joints: (Frame) -> List<Joint> = { emptyList() }, draw: Draw.(Frame) -> Unit) =
    ShapeTemplate(id, ShapeTemplate.Mode.LINE, length, 0f, minLength, 0f, defaults, joints, draw).also { all[id] = it }

  // ---- Social sciences ----

  val group =
    sized("group", 160f, 70f, 40f, 36f, joints = { f -> sizeJoints(f, 40f, 36f) }) { f ->
      val n = count(f.w / (f.h * 0.75f), 1, 14)
      val slot = f.w / n
      val hh = min(f.h, slot * 1.25f)
      for (i in 0 until n) person(slot * (i + 0.5f), f.h, hh)
    }

  private val networkNodes = listOf(0.15f, 0.2f, 0.85f, 0.12f, 0.5f, 0.55f, 0.18f, 0.88f, 0.85f, 0.85f)

  val network =
    sized(
      "network", 140f, 120f, 50f, 50f, networkNodes,
      joints = { f ->
        (0 until 5).map { i ->
          Joint(Pt(f.v(2 * i, networkNodes[2 * i]) * f.w, f.v(2 * i + 1, networkNodes[2 * i + 1]) * f.h)) { g, p ->
            g.set(2 * i, clampF(p.x / g.w, 0f, 1f)).set(2 * i + 1, clampF(p.y / g.h, 0f, 1f))
          }
        }
      },
    ) { f ->
      val r = max(5f, min(f.w, f.h) * 0.07f)
      val n = (0 until 5).map { i -> Pt(f.v(2 * i, networkNodes[2 * i]) * f.w, f.v(2 * i + 1, networkNodes[2 * i + 1]) * f.h) }
      n.forEach { circle(it.x, it.y, r) }
      listOf(0 to 2, 1 to 2, 2 to 3, 2 to 4, 0 to 1, 3 to 4).forEach { (a, b) -> if (n[a].dist(n[b]) > 2 * r) link(n[a].x, n[a].y, n[b].x, n[b].y, r) }
    }

  val familyTree =
    sized("genogram", 150f, 110f, 60f, 60f, joints = { f -> sizeJoints(f, 60f, 60f) }) { f ->
      val s = min(f.h * 0.26f, 26f)
      val cx = f.w / 2f
      rect(cx - s * 1.8f, 0f, cx - s * 0.8f, s)
      circle(cx + s * 1.3f, s / 2f, s / 2f)
      line(cx - s * 0.8f, s / 2f, cx + s * 0.8f, s / 2f)
      val mid = f.h * 0.5f
      line(cx, s / 2f, cx, mid)
      val n = count(f.w / (s * 2.2f), 1, 10)
      val xs = List(n) { (it + 0.5f) * f.w / n }
      if (n > 1) line(xs.first(), mid, xs.last(), mid)
      xs.forEachIndexed { i, x ->
        line(x, mid, x, f.h - s)
        if (i % 2 == 0) rect(x - s / 2f, f.h - s, x + s / 2f, f.h) else circle(x, f.h - s / 2f, s / 2f)
      }
    }

  val speech =
    sized(
      "speech", 140f, 80f, 40f, 30f, listOf(0.25f, 1.35f),
      joints = { f -> listOf(Joint(Pt(f.v(0, 0.25f) * f.w, f.v(1, 1.35f) * f.h)) { g, p -> g.set(0, clampF(p.x / g.w, -0.5f, 1.5f)).set(1, clampF(p.y / g.h, 1.02f, 3f)) }, widthJoint(f, 40f), heightJoint(f, 30f, x = f.w * 0.75f)) },
    ) { f ->
      val tip = Pt(f.v(0, 0.25f) * f.w, f.v(1, 1.35f) * f.h)
      val base = clampF(tip.x, f.w * 0.12f, f.w * 0.88f)
      val half = min(f.w * 0.08f, 12f)
      poly(0, 0, f.w, 0, f.w, f.h, base + half, f.h, tip.x, tip.y, base - half, f.h, 0, f.h)
    }

  val timeline =
    line("timeline", 160f, 40f) { f ->
      line(0, 0, f.w, 0, arrow = true)
      var x = 36f
      while (x < f.w - 22f) {
        line(x, -7, x, 7)
        x += 36f
      }
    }

  val venn =
    sized("venn", 130f, 80f, 30f, 30f, joints = { f -> listOf(widthJoint(f, 30f), heightJoint(f, 30f)) }) { f ->
      val r = f.h / 2f
      circle(r, r, r)
      circle(max(f.w - r, r + 2f), r, r)
    }

  // ---- Business ----

  val orgChart =
    sized("org-chart", 180f, 90f, 60f, 50f, joints = { f -> sizeJoints(f, 60f, 50f) }) { f ->
      val b = min(f.h * 0.28f, 26f)
      val n = count(f.w / 62f, 1, 10)
      val tw = min(f.w * 0.36f, 80f)
      rect(f.w / 2f - tw / 2f, 0, f.w / 2f + tw / 2f, b)
      val mid = (b + f.h - b) / 2f
      line(f.w / 2f, b, f.w / 2f, mid)
      val xs = List(n) { (it + 0.5f) * f.w / n }
      if (n > 1) line(xs.first(), mid, xs.last(), mid)
      val cw = f.w / n * 0.82f
      xs.forEach { x ->
        line(x, mid, x, f.h - b)
        rect(x - cw / 2f, f.h - b, x + cw / 2f, f.h)
      }
    }

  private fun barDefault(i: Int) = listOf(0.45f, 0.7f, 0.55f, 0.85f, 0.6f, 0.75f, 0.5f, 0.9f)[i % 8]

  private fun bars(f: Frame) = count(((f.w - 8f) / 40f) - 0.49f, 1, 24)

  val barChart =
    sized(
      "bar-chart", 160f, 110f, 40f, 40f,
      joints = { f ->
        val n = bars(f)
        val slot = (f.w - 8f) / n
        List(n) { i ->
          val x = 8f + slot * (i + 0.5f)
          Joint(Pt(x, f.h - f.v(i, barDefault(i)) * (f.h - 6f))) { g, p -> g.set(i, clampF((g.h - p.y) / (g.h - 6f), 0.02f, 1f), ::barDefault) }
        } + Joint(Pt(f.w, f.h)) { g, p -> g.sized(w = max(p.x, 40f)) } + topJoint(f, 40f)
      },
    ) { f ->
      open(0, 0, 0, f.h, f.w, f.h)
      val n = bars(f)
      val slot = (f.w - 8f) / n
      for (i in 0 until n) {
        val x = 8f + slot * i + slot * 0.2f
        rect(x, f.h - f.v(i, barDefault(i)) * (f.h - 6f), x + slot * 0.6f, f.h)
      }
    }

  private val pieDefault = listOf(-90f, 0f, 110f, 200f)

  val pieChart =
    sized(
      "pie-chart", 110f, 110f, 30f, 30f, pieDefault,
      round = true, joints = { f ->
        val r = min(f.w, f.h) / 2f
        val c = Pt(f.w / 2f, f.h / 2f)
        f.v.indices.map { i -> Joint(c + Pt.polar(r, deg(f.v[i]))) { g, p -> g.set(i, (atan2(p.y - c.y, p.x - c.x) * 180.0 / PI).toFloat()) } } + Joint(Pt(f.w / 2f, f.h)) { g, p -> max(p.y, 30f).let { d -> g.sized(w = d, h = d) } }
      },
    ) { f ->
      val r = min(f.w, f.h) / 2f
      val c = Pt(f.w / 2f, f.h / 2f)
      circle(c.x, c.y, r)
      for (a in f.v) {
        val e = c + Pt.polar(r, deg(a))
        line(c.x, c.y, e.x, e.y)
      }
    }

  private fun growthPoints(f: Frame) = count(f.w / 34f, 3, 12)

  private fun growthDefault(i: Int, k: Int) = 0.15f + 0.7f * i / (k - 1) + if (i % 2 == 1) -0.1f else 0f

  val growth =
    sized(
      "growth", 150f, 110f, 60f, 40f,
      joints = { f ->
        val k = growthPoints(f)
        List(k) { i ->
          val x = 10f + i * (f.w - 20f) / (k - 1)
          Joint(Pt(x, f.h - f.v(i, growthDefault(i, k)) * f.h)) { g, p -> g.set(i, clampF((g.h - p.y) / g.h, 0f, 1f)) { j -> growthDefault(j, k) } }
        } + Joint(Pt(f.w, f.h)) { g, p -> g.sized(w = max(p.x, 60f)) } + topJoint(f, 40f)
      },
    ) { f ->
      open(0, 0, 0, f.h, f.w, f.h)
      val k = growthPoints(f)
      val pts = List(k) { i -> Pt(10f + i * (f.w - 20f) / (k - 1), f.h - f.v(i, growthDefault(i, k)) * f.h) }
      open(*pts.dropLast(1).flatMap { listOf(it.x, it.y) }.toTypedArray())
      line(pts[k - 2].x, pts[k - 2].y, pts[k - 1].x, pts[k - 1].y, arrow = true)
    }

  val funnel =
    sized(
      "funnel", 120f, 130f, 50f, 50f, listOf(0.22f),
      joints = { f ->
        val nw = f.v(0, 0.22f) * f.w
        listOf(
          Joint(Pt(f.w / 2f + nw / 2f, f.h * 0.8f)) { g, p -> g.set(0, clampF(2f * (p.x - g.w / 2f) / g.w, 0.05f, 0.9f)) },
          Joint(Pt(f.w, 0f)) { g, p -> g.sized(w = max(p.x, 50f)) },
          heightJoint(f, 50f),
        )
      },
    ) { f ->
      val cx = f.w / 2f
      val nw = f.v(0, 0.22f) * f.w
      val ny = f.h * 0.6f
      poly(0, 0, f.w, 0, cx + nw / 2f, ny, cx + nw / 2f, f.h, cx - nw / 2f, f.h, cx - nw / 2f, ny)
      val stages = count(f.h / 32f, 2, 8)
      for (k in 1 until stages) {
        val y = k * ny / stages
        val t = y / ny
        line((cx - nw / 2f) * t, y, f.w - (cx - nw / 2f) * t, y)
      }
    }

  val swot =
    sized(
      "swot", 120f, 120f, 30f, 30f, listOf(0.5f, 0.5f),
      joints = { f -> listOf(Joint(Pt(f.v(0, 0.5f) * f.w, f.v(1, 0.5f) * f.h)) { g, p -> g.set(0, clampF(p.x / g.w, 0.1f, 0.9f)).set(1, clampF(p.y / g.h, 0.1f, 0.9f)) }) + sizeJoints(f, 30f, 30f) },
    ) { f ->
      rect(0, 0, f.w, f.h)
      line(f.v(0, 0.5f) * f.w, 0, f.v(0, 0.5f) * f.w, f.h)
      line(0, f.v(1, 0.5f) * f.h, f.w, f.v(1, 0.5f) * f.h)
    }

  private fun ganttRows(f: Frame) = count(f.h / 24f - 0.49f, 1, 16)

  private fun ganttDefault(j: Int): Float {
    val row = j / 2
    val start = (0.05f + 0.2f * row) % 0.65f
    return if (j % 2 == 0) start else start + 0.35f
  }

  val gantt =
    sized(
      "gantt", 180f, 100f, 60f, 24f,
      joints = { f ->
        val n = ganttRows(f)
        val rh = f.h / n
        (0 until n).flatMap { i ->
          val y = rh * (i + 0.5f)
          listOf(0, 1).map { e ->
            val j = 2 * i + e
            Joint(Pt(6f + f.v(j, ganttDefault(j)) * (f.w - 6f), y)) { g, p ->
              val x = clampF((p.x - 6f) / (g.w - 6f), 0f, 1f)
              val other = g.v(2 * i + 1 - e, ganttDefault(2 * i + 1 - e))
              g.set(j, if (e == 0) min(x, other - 0.03f) else max(x, other + 0.03f), ::ganttDefault)
            }
          }
        } + Joint(Pt(f.w, f.h)) { g, p -> g.sized(w = max(p.x, 60f)) } + heightJoint(f, 24f, x = 0f)
      },
    ) { f ->
      open(0, 0, 0, f.h, f.w, f.h)
      val n = ganttRows(f)
      val rh = f.h / n
      for (i in 0 until n) {
        val s = 6f + f.v(2 * i, ganttDefault(2 * i)) * (f.w - 6f)
        val e = 6f + f.v(2 * i + 1, ganttDefault(2 * i + 1)) * (f.w - 6f)
        rect(s, rh * i + rh * 0.2f, e, rh * (i + 0.8f))
      }
    }

  val target =
    sized(
      "target", 110f, 110f, 30f, 30f, listOf(0.97f, 0.03f),
      round = true, joints = { f -> listOf(Joint(Pt(f.v(0, 0.97f) * f.w, f.v(1, 0.03f) * f.h)) { g, p -> g.set(0, p.x / g.w).set(1, p.y / g.h) }, Joint(Pt(f.w / 2f, f.h)) { g, p -> max(p.y, 30f).let { d -> g.sized(w = d, h = d) } }) },
    ) { f ->
      val r = min(f.w, f.h) / 2f
      val c = Pt(f.w / 2f, f.h / 2f)
      val rings = count(r / 13f, 2, 8)
      for (k in 1..rings) circle(c.x, c.y, r * k / rings)
      val tail = Pt(f.v(0, 0.97f) * f.w, f.v(1, 0.03f) * f.h)
      if (tail.dist(c) > 6f) line(tail.x, tail.y, c.x, c.y, arrow = true)
    }

  val coins =
    sized("coins", 90f, 80f, 30f, 24f, joints = { f -> sizeJoints(f, 30f, 24f) }) { f ->
      val rx = f.w / 2f
      val ry = min(f.w * 0.15f, f.h * 0.2f)
      ellipse(rx, ry, rx, ry)
      line(0, ry, 0, f.h - ry)
      line(f.w, ry, f.w, f.h - ry)
      val n = count((f.h - 2 * ry) / 12f, 1, 20)
      for (k in 1..n) ellipseArc(rx, ry + k * (f.h - 2 * ry) / n, rx, ry, 0, 180)
    }

  val document =
    sized(
      "document", 100f, 120f, 40f, 40f, listOf(0.07f),
      joints = { f -> listOf(Joint(Pt(f.w * 0.3f, f.h * (0.86f + f.v(0, 0.07f)))) { g, p -> g.set(0, clampF(p.y / g.h - 0.86f, 0f, 0.13f)) }) + sizeJoints(f, 40f, 40f) },
    ) { f ->
      val b = f.h * 0.86f
      val a = f.v(0, 0.07f) * f.h
      open(0, b, 0, 0, f.w, 0, f.w, b)
      curve(f.w, b, f.w * 0.7f, b - a, f.w * 0.5f, b, f.w * 0.3f, b + a, 0, b)
    }

  // ---- Computer science ----

  val database =
    sized("database", 90f, 110f, 30f, 40f, joints = { f -> sizeJoints(f, 30f, 40f) }) { f ->
      val ry = min(f.w * 0.15f, f.h * 0.15f)
      ellipse(f.w / 2f, ry, f.w / 2f, ry)
      line(0, ry, 0, f.h - ry)
      line(f.w, ry, f.w, f.h - ry)
      val bands = count((f.h - 2 * ry) / 36f, 1, 8)
      for (k in 1..bands) ellipseArc(f.w / 2f, ry + k * (f.h - 2 * ry) / bands, f.w / 2f, ry, 0, 180)
    }

  val terminal =
    sized("terminal", 140f, 60f, 40f, 20f, joints = { f -> sizeJoints(f, 40f, 20f) }) { f ->
      val r = min(f.h, f.w) / 2f
      line(r, 0, f.w - r, 0)
      line(r, f.h, f.w - r, f.h)
      ellipseArc(r, f.h / 2f, r, f.h / 2f, 90, 270)
      ellipseArc(f.w - r, f.h / 2f, r, f.h / 2f, -90, 90)
    }

  val subroutine =
    sized(
      "subroutine", 140f, 70f, 40f, 24f, listOf(0.1f),
      joints = { f -> listOf(Joint(Pt(f.v(0, 0.1f) * f.w, f.h / 2f)) { g, p -> g.set(0, clampF(p.x / g.w, 0.02f, 0.4f)) }) + sizeJoints(f, 40f, 24f) },
    ) { f ->
      val i = f.v(0, 0.1f) * f.w
      rect(0, 0, f.w, f.h)
      line(i, 0, i, f.h)
      line(f.w - i, 0, f.w - i, f.h)
    }

  private fun treeLevels(f: Frame) = count(f.h / 45f + 1f, 2, 6)

  val binaryTree =
    sized("binary-tree", 150f, 120f, 40f, 40f, joints = { f -> sizeJoints(f, 40f, 40f) }) { f ->
      val levels = treeLevels(f)
      val r = clampF(min(f.h / (levels * 3.2f), f.w / 2f.pow(levels - 1) / 2.6f), 3f, 14f)
      fun at(l: Int, j: Int) = Pt((j + 0.5f) * f.w / 2f.pow(l), r + l * (f.h - 2 * r) / (levels - 1))
      for (l in 0 until levels) {
        for (j in 0 until (1 shl l)) {
          val p = at(l, j)
          circle(p.x, p.y, r)
          if (l + 1 < levels) {
            for (c in listOf(2 * j, 2 * j + 1)) {
              val q = at(l + 1, c)
              link(p.x, p.y, q.x, q.y, r)
            }
          }
        }
      }
    }

  val graph =
    sized("graph", 140f, 120f, 50f, 50f, joints = { f -> sizeJoints(f, 50f, 50f) }) { f ->
      val n = count(PI.toFloat() * (f.w + f.h) / 2f / 55f, 3, 12)
      val r = clampF(min(f.w, f.h) * 0.07f, 5f, 12f)
      val pts = List(n) { i ->
        val a = -PI / 2 + 2 * PI * i / n
        Pt(f.w / 2f + (f.w / 2f - r) * cos(a).toFloat(), f.h / 2f + (f.h / 2f - r) * sin(a).toFloat())
      }
      pts.forEach { circle(it.x, it.y, r) }
      for (i in 0 until n) {
        val a = pts[i]
        val b = pts[(i + 1) % n]
        link(a.x, a.y, b.x, b.y, r, arrow = true)
        if (i % 2 == 0 && n > 4 && i + 2 < n) link(a.x, a.y, pts[i + 2].x, pts[i + 2].y, r, arrow = true)
      }
    }

  val array =
    sized("array", 160f, 32f, 20f, 14f, joints = { f -> sizeJoints(f, 20f, 14f) }) { f ->
      rect(0, 0, f.w, f.h)
      val n = count(f.w / f.h, 1, 60)
      for (k in 1 until n) line(k * f.w / n, 0, k * f.w / n, f.h)
    }

  val linkedList =
    line("linked-list", 190f, 34f) { f ->
      val box = 34f
      val n = ((f.w + 18f) / (box + 18f)).toInt().coerceIn(1, 14)
      val pitch = if (n > 1) (f.w - box) / (n - 1) else 0f
      for (i in 0 until n) {
        val x = i * pitch
        rect(x, -11, x + box, 11)
        line(x + 24f, -11, x + 24f, 11)
        if (i + 1 < n) line(x + 29f, 0, (i + 1) * pitch, 0, arrow = true) else line(x + 24f, 11, x + box, -11)
      }
    }

  val stack =
    sized("stack", 70f, 120f, 30f, 40f, joints = { f -> sizeJoints(f, 30f, 40f) }) { f ->
      val a = min(28f, f.h * 0.25f)
      val n = count((f.h - a) / 26f, 1, 30)
      val c = (f.h - a) / n
      for (k in 0 until n) rect(0, a + k * c, f.w, a + (k + 1) * c)
      line(f.w / 2f, 0, f.w / 2f, a - 4f, arrow = true)
    }

  val computer =
    sized("computer", 120f, 100f, 40f, 40f, joints = { f -> sizeJoints(f, 40f, 40f) }) { f ->
      val sh = f.h * 0.76f
      rect(0, 0, f.w, sh)
      rect(f.w * 0.06f, sh * 0.08f, f.w * 0.94f, sh * 0.92f)
      line(f.w / 2f, sh, f.w / 2f, f.h * 0.94f)
      line(f.w * 0.28f, f.h, f.w * 0.72f, f.h)
    }

  // ---- Law ----

  val scales =
    natural(
      "scales", defaults = listOf(0f),
      joints = { f ->
        val t = deg(f.v(0, 0f))
        val lx = 50f - 36f * cos(t)
        val ly = 22f + 36f * sin(t)
        listOf(Joint(Pt(lx, ly + 38f)) { g, p -> g.set(0, (asin(clampF((p.y - 38f - 22f) / 36f, -0.6f, 0.6f)) * 180.0 / PI).toFloat()) })
      },
    ) { f ->
      val t = deg(f.v(0, 0f))
      line(50, 12, 50, 88)
      line(32, 92, 68, 92)
      circle(50, 10, 3)
      val ends = listOf(Pt(50f - 36f * cos(t), 22f + 36f * sin(t)), Pt(50f + 36f * cos(t), 22f - 36f * sin(t)))
      line(ends[0].x, ends[0].y, ends[1].x, ends[1].y)
      for (e in ends) {
        line(e.x, e.y, e.x - 11.3f, e.y + 30f)
        line(e.x, e.y, e.x + 11.3f, e.y + 30f)
        arc(e.x, e.y + 26f, 12f, 20f, 140f)
      }
    }

  val courthouse =
    sized("courthouse", 130f, 100f, 50f, 50f, joints = { f -> sizeJoints(f, 50f, 50f) }) { f ->
      poly(0, f.h * 0.3f, f.w / 2f, 0, f.w, f.h * 0.3f)
      rect(f.w * 0.02f, f.h * 0.3f, f.w * 0.98f, f.h * 0.37f)
      val n = count(f.w / 22f, 2, 16)
      for (k in 0 until n) {
        val x = f.w * 0.1f + k * f.w * 0.8f / (n - 1)
        line(x, f.h * 0.37f, x, f.h * 0.82f)
      }
      rect(0, f.h * 0.82f, f.w, f.h * 0.9f)
      line(0, f.h, f.w, f.h)
    }

  val column =
    sized("column", 50f, 110f, 24f, 50f, joints = { f -> sizeJoints(f, 24f, 50f) }) { f ->
      rect(0, 0, f.w, f.h * 0.08f)
      rect(0, f.h * 0.92f, f.w, f.h)
      line(f.w * 0.14f, f.h * 0.08f, f.w * 0.14f, f.h * 0.92f)
      line(f.w * 0.86f, f.h * 0.08f, f.w * 0.86f, f.h * 0.92f)
      val flutes = count(f.w / 16f, 1, 6)
      for (k in 1..flutes) {
        val x = f.w * 0.14f + k * f.w * 0.72f / (flutes + 1)
        line(x, f.h * 0.12f, x, f.h * 0.88f)
      }
    }

  val contract =
    sized("contract", 80f, 105f, 40f, 50f, joints = { f -> sizeJoints(f, 40f, 50f) }) { f ->
      rect(0, 0, f.w, f.h)
      val n = ((f.h * 0.5f) / 12f).toInt().coerceIn(1, 20)
      for (k in 0 until n) line(f.w * 0.12f, f.h * 0.12f + k * 12f, f.w * (if (k % 3 == 2) 0.6f else 0.88f), f.h * 0.12f + k * 12f)
      curve(f.w * 0.12f, f.h * 0.82f, f.w * 0.25f, f.h * 0.74f, f.w * 0.36f, f.h * 0.84f, f.w * 0.48f, f.h * 0.76f, f.w * 0.6f, f.h * 0.82f)
      line(f.w * 0.12f, f.h * 0.88f, f.w * 0.88f, f.h * 0.88f)
    }

  val courtLevels =
    sized("court-levels", 120f, 90f, 40f, 40f, joints = { f -> sizeJoints(f, 40f, 40f) }) { f ->
      val n = count(f.h / 26f, 2, 7)
      val lh = f.h / n
      for (k in 0 until n) {
        val half = f.w * (k + 1) / n / 2f
        rect(f.w / 2f - half, k * lh, f.w / 2f + half, (k + 1) * lh)
      }
    }

  val parties =
    sized("parties", 150f, 70f, 70f, 30f, joints = { f -> sizeJoints(f, 70f, 30f) }) { f ->
      person(f.h * 0.42f, f.h, f.h)
      person(f.w - f.h * 0.42f, f.h, f.h)
      if (f.w - 1.7f * f.h > 10f) line(f.h * 0.85f, f.h * 0.55f, f.w - f.h * 0.85f, f.h * 0.55f, both = true)
    }

  // ---- Nursing and health ----

  val medCross =
    natural(
      "med-cross", defaults = listOf(15f),
      joints = { f -> listOf(Joint(Pt(50f + f.v(0, 15f), 5f)) { g, p -> g.set(0, clampF(p.x - 50f, 5f, 40f)) }) },
    ) { f ->
      val t = f.v(0, 15f)
      poly(50 - t, 5, 50 + t, 5, 50 + t, 50 - t, 95, 50 - t, 95, 50 + t, 50 + t, 50 + t, 50 + t, 95, 50 - t, 95, 50 - t, 50 + t, 5, 50 + t, 5, 50 - t, 50 - t, 50 - t)
    }

  val heartbeat =
    line(
      "heartbeat", 160f, 40f, listOf(36f),
      joints = { f ->
        val slot = f.w / count(f.w / 70f - 0.49f, 1, 14)
        listOf(Joint(Pt(slot * 0.5f + 7f, -f.v(0, 36f))) { g, p -> g.set(0, clampF(-p.y, 6f, 150f)) })
      },
    ) { f ->
      val a = f.v(0, 36f)
      val n = count(f.w / 70f - 0.49f, 1, 14)
      val slot = f.w / n
      val pts = mutableListOf<Number>(0, 0)
      val beat = listOf(-22f to 0f, -15f to -0.22f, -9f to 0f, -3f to 0f, 1f to 0.6f, 7f to -1f, 13f to 0.3f, 17f to 0f)
      for (i in 0 until n) {
        val cx = slot * (i + 0.5f)
        beat.forEach { (dx, dy) -> pts += cx + dx; pts += dy * a }
      }
      pts += f.w
      pts += 0
      open(*pts.toTypedArray())
    }

  val syringe =
    line(
      "syringe", 130f, 60f, listOf(0.35f),
      joints = { f ->
        val b0 = f.w * 0.2f
        val b1 = f.w * 0.72f
        listOf(Joint(Pt(b0 + f.v(0, 0.35f) * (b1 - b0), -10f)) { g, p -> g.set(0, clampF((p.x - g.w * 0.2f) / (g.w * 0.52f), 0.02f, 0.95f)) })
      },
    ) { f ->
      val b0 = f.w * 0.2f
      val b1 = f.w * 0.72f
      val px = b0 + f.v(0, 0.35f) * (b1 - b0)
      rect(b0, -10, b1, 10)
      line(px, -9, px, 9)
      line(0, 0, px, 0)
      line(0, -9, 0, 9)
      rect(b1, -4, b1 + f.w * 0.05f, 4)
      line(b1 + f.w * 0.05f, 0, f.w, 0)
    }

  val thermometer =
    natural(
      "thermometer", defaults = listOf(40f),
      joints = { f -> listOf(Joint(Pt(50f, f.v(0, 40f))) { g, p -> g.set(0, clampF(p.y, 14f, 68f)) }) },
    ) { f ->
      line(42, 12, 42, 68)
      line(58, 12, 58, 68)
      arc(50, 12, 8, 180, 180)
      circle(50, 80, 14)
      line(50, f.v(0, 40f), 50, 66)
      for (y in listOf(25, 38, 51)) line(58, y, 66, y)
    }

  val ivBag =
    natural(
      "iv-bag", defaults = listOf(20f),
      joints = { f -> listOf(Joint(Pt(70f, f.v(0, 20f))) { g, p -> g.set(0, clampF(p.y, 8f, 52f)) }) },
    ) { f ->
      rect(30, 5, 70, 55)
      line(30, f.v(0, 20f), 70, f.v(0, 20f))
      line(45, 55, 45, 62)
      line(55, 55, 55, 62)
      rect(43, 62, 57, 76)
      curve(50, 76, 50, 86, 60, 95)
    }

  // ---- Mechanical engineering ----

  val gear =
    sized("gear", 100f, 100f, 30f, 30f, round = true, joints = { f -> listOf(diameterJoint(f, 30f)) }) { f ->
      val d = min(f.w, f.h)
      val r = d / 2f
      val c = Pt(r, r)
      val n = count(2f * PI.toFloat() * r / 17f, 6, 48)
      val step = 360f / n
      val p = mutableListOf<Number>()
      for (t in 0 until n) {
        val base = t * step
        for ((rr, dd) in listOf(0.76f to -0.31f, 1f to -0.18f, 1f to 0.18f, 0.76f to 0.31f)) {
          val a = deg(base + dd * step)
          p += c.x + r * rr * cos(a)
          p += c.y + r * rr * sin(a)
        }
      }
      poly(*p.toTypedArray())
      circle(c.x, c.y, r * 0.27f)
    }

  val spring =
    line(
      "spring", 150f, 40f, listOf(10f),
      joints = { f ->
        val lead = f.w * 0.15f
        val n = count((f.w - 2 * lead) / 9f, 2, 60)
        val pitch = (f.w - 2 * lead) / n
        listOf(Joint(Pt(lead + pitch * 0.5f, -f.v(0, 10f))) { g, p -> g.set(0, clampF(-p.y, 3f, 80f)) })
      },
    ) { f ->
      val a = f.v(0, 10f)
      val lead = f.w * 0.15f
      val n = count((f.w - 2 * lead) / 9f, 2, 60)
      val pitch = (f.w - 2 * lead) / n
      val pts = mutableListOf<Number>(0, 0, lead, 0)
      for (i in 0 until n) {
        pts += lead + pitch * (i + 0.5f)
        pts += if (i % 2 == 0) -a else a
      }
      pts += listOf(f.w - lead, 0, f.w, 0)
      open(*pts.toTypedArray())
    }

  val damper =
    line(
      "damper", 140f, 50f, listOf(0.5f),
      joints = { f -> listOf(Joint(Pt(f.w * 0.35f + f.v(0, 0.5f) * f.w * 0.33f, -10f)) { g, p -> g.set(0, clampF((p.x - g.w * 0.35f) / (g.w * 0.33f), 0.1f, 0.95f)) }) },
    ) { f ->
      val c0 = f.w * 0.35f
      val c1 = f.w * 0.68f
      val px = c0 + f.v(0, 0.5f) * (c1 - c0)
      line(0, 0, c0, 0)
      open(c1, -14, c0, -14, c0, 14, c1, 14)
      line(px, -10, px, 10)
      line(px, 0, f.w, 0)
    }

  val bearing =
    sized("bearing", 100f, 100f, 30f, 30f, round = true, joints = { f -> listOf(diameterJoint(f, 30f)) }) { f ->
      val r = min(f.w, f.h) / 2f
      circle(r, r, r)
      circle(r, r, r * 0.67f)
      circle(r, r, r * 0.29f)
      val n = count(2f * PI.toFloat() * r * 0.83f / (r * 0.3f), 6, 16)
      for (i in 0 until n) {
        val a = 2 * PI * i / n
        circle(r + r * 0.83f * cos(a).toFloat(), r + r * 0.83f * sin(a).toFloat(), r * 0.12f)
      }
    }

  val pulley =
    natural(
      "pulley", defaults = listOf(76f),
      joints = { f -> listOf(Joint(Pt(72f, f.v(0, 76f) + 16f)) { g, p -> g.set(0, clampF(p.y - 16f, 60f, 140f)) }) },
    ) { f ->
      val y = f.v(0, 76f)
      line(32, 2, 68, 2)
      line(50, 2, 50, 14)
      circle(50, 36, 22)
      circle(50, 36, 4)
      line(28, 36, 28, 96)
      line(72, 36, 72, y)
      rect(62, y, 82, y + 16f)
    }

  val lever =
    sized(
      "lever", 160f, 70f, 60f, 30f, listOf(0.5f),
      joints = { f -> listOf(Joint(Pt(f.v(0, 0.5f) * f.w, f.h * 0.55f + f.h * 0.3f)) { g, p -> g.set(0, clampF(p.x / g.w, 0.08f, 0.92f)) }) + sizeJoints(f, 60f, 30f) },
    ) { f ->
      val by = f.h * 0.55f
      val s = f.h * 0.3f
      val fx = f.v(0, 0.5f) * f.w
      line(0, by, f.w, by)
      poly(fx, by, fx - s * 0.6f, by + s, fx + s * 0.6f, by + s)
      line(f.w * 0.04f, 0, f.w * 0.04f, by - 2f, arrow = true)
      line(f.w * 0.96f, 0, f.w * 0.96f, by - 2f, arrow = true)
    }

  val piston =
    natural(
      "piston", defaults = listOf(42f),
      joints = { f -> listOf(Joint(Pt(78f, f.v(0, 42f) + 8f)) { g, p -> g.set(0, clampF(p.y - 8f, 16f, 74f)) }) },
    ) { f ->
      val y = f.v(0, 42f)
      open(20, 12, 20, 92, 80, 92, 80, 12)
      rect(22, y, 78, y + 16f)
      line(50, y, 50, 0)
    }

  val mass =
    sized(
      "mass", 110f, 80f, 40f, 40f, listOf(0.45f, 0.65f),
      joints = { f ->
        val gy = f.h * 0.82f
        val bw = f.v(0, 0.45f) * f.w
        val bh = f.v(1, 0.65f) * gy
        listOf(Joint(Pt(f.w / 2f + bw / 2f, gy - bh)) { g, p -> g.set(0, clampF(2f * (p.x - g.w / 2f) / g.w, 0.1f, 1f)).set(1, clampF((g.h * 0.82f - p.y) / (g.h * 0.82f), 0.1f, 1f)) }) + sizeJoints(f, 40f, 40f)
      },
    ) { f ->
      val gy = f.h * 0.82f
      val bw = f.v(0, 0.45f) * f.w
      val bh = f.v(1, 0.65f) * gy
      rect(f.w / 2f - bw / 2f, gy - bh, f.w / 2f + bw / 2f, gy)
      line(0, gy, f.w, gy)
      val d = f.h - gy
      val n = count((f.w - d) / 12f, 5, 40)
      for (i in 0 until n) {
        val x = d + (f.w - d) * (i + 0.5f) / n
        line(x, gy, x - d, f.h)
      }
    }

  val shaft =
    line(
      "shaft", 150f, 40f, listOf(0.4f, 12f),
      joints = { f ->
        val r = f.v(1, 12f)
        listOf(
          Joint(Pt(f.v(0, 0.4f) * f.w, -r)) { g, p -> g.set(0, clampF(p.x / g.w, 0.12f, 0.88f)) },
          Joint(Pt(f.w * 0.88f, -r)) { g, p -> g.set(1, clampF(-p.y, 3f, 60f)) },
        )
      },
    ) { f ->
      val r = f.v(1, 12f)
      val k = f.v(0, 0.4f) * f.w
      rect(0, -r, f.w, r)
      rect(k - f.w * 0.1f, -r, k + f.w * 0.1f, -r * 0.5f)
      line(0, 0, f.w, 0)
    }

  val bolt =
    sized("bolt", 40f, 100f, 16f, 40f, joints = { f -> sizeJoints(f, 16f, 40f) }) { f ->
      val hh = min(f.h * 0.24f, f.w * 0.6f)
      poly(f.w * 0.15f, 0, f.w * 0.85f, 0, f.w, hh / 2f, f.w * 0.85f, hh, f.w * 0.15f, hh, 0, hh / 2f)
      rect(f.w * 0.25f, hh, f.w * 0.75f, f.h)
      val start = hh + (f.h - hh) * 0.3f
      val n = ((f.h - start) / 9f).toInt().coerceIn(1, 40)
      for (k in 0 until n) {
        val y = start + (k + 1) * (f.h - start) / (n + 0.5f)
        line(f.w * 0.25f, y, f.w * 0.75f, y - f.w * 0.12f)
      }
    }

  // ---- Civil engineering ----

  val truss =
    sized("truss", 200f, 60f, 60f, 20f, joints = { f -> sizeJoints(f, 60f, 20f) }) { f ->
      val n = count(f.w / (f.h * 0.9f), 2, 24)
      val pw = f.w / n
      line(0, f.h, f.w, f.h)
      line(pw, 0, f.w - pw, 0)
      line(0, f.h, pw, 0)
      line(f.w, f.h, f.w - pw, 0)
      for (k in 1 until n) line(k * pw, 0, k * pw, f.h)
      for (k in 1 until n - 1) {
        if (k < n / 2f) line(k * pw, 0, (k + 1) * pw, f.h) else line((k + 1) * pw, 0, k * pw, f.h)
      }
    }

  val iBeam =
    sized(
      "i-beam", 80f, 100f, 24f, 30f, listOf(0.15f, 0.16f),
      joints = { f ->
        listOf(
          Joint(Pt(f.w * 0.85f, f.v(0, 0.15f) * f.h)) { g, p -> g.set(0, clampF(p.y / g.h, 0.03f, 0.45f)) },
          Joint(Pt(f.w / 2f + f.v(1, 0.16f) * f.w / 2f, f.h / 2f)) { g, p -> g.set(1, clampF(2f * (p.x - g.w / 2f) / g.w, 0.03f, 0.9f)) },
        ) + sizeJoints(f, 24f, 30f).map { j -> if (j.at.y == f.h / 2f) Joint(Pt(f.w, f.h * 0.07f)) { g, p -> g.sized(w = max(p.x, 24f)) } else j }
      },
    ) { f ->
      val tf = f.v(0, 0.15f) * f.h
      val tw = f.v(1, 0.16f) * f.w
      val l = f.w / 2f - tw / 2f
      val r = f.w / 2f + tw / 2f
      poly(0, 0, f.w, 0, f.w, tf, r, tf, r, f.h - tf, f.w, f.h - tf, f.w, f.h, 0, f.h, 0, f.h - tf, l, f.h - tf, l, tf, 0, tf)
    }

  // ---- Electrical engineering (drawn from terminal to terminal) ----

  val resistor =
    line("resistor", 120f, 40f) { f ->
      val bl = min(48f, f.w * 0.6f)
      val s = (f.w - bl) / 2f
      val pts = mutableListOf<Number>(0, 0, s, 0)
      for (i in 0 until 6) {
        pts += s + bl * (i + 0.5f) / 6f
        pts += if (i % 2 == 0) -8 else 8
      }
      pts += listOf(s + bl, 0, f.w, 0)
      open(*pts.toTypedArray())
    }

  val capacitor =
    line("capacitor", 120f, 30f) { f ->
      val c = f.w / 2f
      line(0, 0, c - 4f, 0)
      line(c - 4f, -15, c - 4f, 15)
      line(c + 4f, -15, c + 4f, 15)
      line(c + 4f, 0, f.w, 0)
    }

  val inductor =
    line("inductor", 120f, 40f) { f ->
      val n = count(f.w * 0.6f / 14f, 2, 16)
      val s = (f.w - n * 14f) / 2f
      line(0, 0, s, 0)
      for (i in 0 until n) arc(s + 7f + 14f * i, 0, 7, 180, 180)
      line(f.w - s, 0, f.w, 0)
    }

  val diode =
    line("diode", 120f, 40f) { f ->
      val c = f.w / 2f
      line(0, 0, c - 11f, 0)
      poly(c - 11f, -12, c - 11f, 12, c + 11f, 0)
      line(c + 11f, -12, c + 11f, 12)
      line(c + 11f, 0, f.w, 0)
    }

  val battery =
    line("battery", 120f, 30f) { f ->
      val n = count(f.w * 0.45f / 18f, 1, 8)
      val len = n * 18f - 11f
      val s = (f.w - len) / 2f
      line(0, 0, s, 0)
      for (i in 0 until n) {
        val x = s + i * 18f
        line(x, -14, x, 14)
        line(x + 7f, -7, x + 7f, 7)
        if (i + 1 < n) line(x + 7f, 0, x + 18f, 0)
      }
      line(s + len, 0, f.w, 0)
    }

  val acSource =
    line("ac-source", 120f, 40f) { f ->
      val c = f.w / 2f
      line(0, 0, c - 16f, 0)
      circle(c, 0, 16)
      curve(c - 11f, 0, c - 5.5f, -7, c, 0, c + 5.5f, 7, c + 11f, 0)
      line(c + 16f, 0, f.w, 0)
    }

  val switch =
    line(
      "switch", 120f, 40f, listOf(28f),
      joints = { f ->
        val c = f.w / 2f
        val t = deg(f.v(0, 28f))
        listOf(Joint(Pt(c - 14f + 30f * cos(t), -30f * sin(t))) { g, p -> g.set(0, clampF((atan2(-p.y, p.x - (g.w / 2f - 14f)) * 180.0 / PI).toFloat(), 0f, 80f)) })
      },
    ) { f ->
      val c = f.w / 2f
      val t = deg(f.v(0, 28f))
      line(0, 0, c - 16f, 0)
      circle(c - 14f, 0, 2)
      line(c - 14f, 0, c - 14f + 30f * cos(t), -30f * sin(t))
      circle(c + 16f, 0, 2)
      line(c + 18f, 0, f.w, 0)
    }

  val lamp =
    line("lamp", 120f, 40f) { f ->
      val c = f.w / 2f
      line(0, 0, c - 16f, 0)
      circle(c, 0, 16)
      line(c - 11.3f, -11.3f, c + 11.3f, 11.3f)
      line(c - 11.3f, 11.3f, c + 11.3f, -11.3f)
      line(c + 16f, 0, f.w, 0)
    }

  val andGate =
    sized("and-gate", 90f, 80f, 40f, 30f, joints = { f -> sizeJoints(f, 40f, 30f) }) { f ->
      val rr = f.h / 2f
      val xa = max(f.w * 0.85f - rr, f.w * 0.25f)
      open(xa, 0, f.w * 0.2f, 0, f.w * 0.2f, f.h, xa, f.h)
      arc(xa, rr, rr, -90, 180)
      line(xa + rr, rr, max(f.w, xa + rr + 6f), rr)
      val n = count(f.h / 22f, 2, 8)
      for (k in 0 until n) line(0, (k + 0.5f) * f.h / n, f.w * 0.2f, (k + 0.5f) * f.h / n)
    }

  // ---- Psychology ----

  val neuron =
    sized("neuron", 170f, 70f, 70f, 30f, joints = { f -> sizeJoints(f, 70f, 30f) }) { f ->
      val r = f.h * 0.17f
      val c = Pt(f.h * 0.35f, f.h / 2f)
      circle(c.x, c.y, r)
      for (a in listOf(200f, 235f, 125f, 160f)) {
        val e = c + Pt.polar(r, deg(a))
        val o = c + Pt.polar(f.h * 0.45f, deg(a))
        line(e.x, e.y, o.x, o.y)
      }
      val end = f.w - f.h * 0.15f
      line(c.x + r, c.y, end, c.y)
      val n = ((end - c.x - r) / 32f).toInt().coerceIn(1, 20)
      for (k in 0 until n) ellipse(c.x + r + (k + 0.5f) * (end - c.x - r) / n, c.y, min(8f, (end - c.x - r) / n * 0.32f), f.h * 0.07f)
      for (dy in listOf(-0.15f, 0f, 0.15f)) line(end, c.y, f.w, c.y + dy * f.h)
    }

  val bellCurve =
    sized(
      "bell-curve", 140f, 90f, 50f, 30f, listOf(0.5f, 0.14f),
      joints = { f ->
        val mu = f.v(0, 0.5f) * f.w
        val sd = f.v(1, 0.14f) * f.w
        listOf(
          Joint(Pt(mu, f.h * 0.1f)) { g, p -> g.set(0, clampF(p.x / g.w, 0.1f, 0.9f)) },
          Joint(Pt(mu + sd, f.h - 0.9f * f.h * exp(-0.5f))) { g, p -> g.set(1, clampF((p.x - g.v(0, 0.5f) * g.w) / g.w, 0.03f, 0.45f)) },
        ) + sizeJoints(f, 50f, 30f)
      },
    ) { f ->
      val mu = f.v(0, 0.5f) * f.w
      val sd = f.v(1, 0.14f) * f.w
      line(0, f.h, f.w, f.h)
      curve(List(31) { i ->
        val x = f.w * i / 30f
        Pt(x, f.h - 0.9f * f.h * exp(-((x - mu) / sd).let { it * it } / 2f))
      })
      line(mu, f.h * 0.1f, mu, f.h)
    }

  val maslow =
    sized("maslow", 110f, 100f, 40f, 40f, joints = { f -> sizeJoints(f, 40f, 40f) }) { f ->
      poly(f.w / 2f, 0, f.w, f.h, 0, f.h)
      val n = count(f.h / 24f, 2, 9)
      for (k in 1 until n) {
        val y = f.h * k / n
        val half = f.w / 2f * y / f.h
        line(f.w / 2f - half, y, f.w / 2f + half, y)
      }
    }

  val thought =
    natural(
      "thought", defaults = listOf(12f, 92f),
      joints = { f -> listOf(Joint(Pt(f.v(0, 12f), f.v(1, 92f))) { g, p -> g.set(0, clampF(p.x, -40f, 140f)).set(1, clampF(p.y, 66f, 160f)) }) },
    ) { f ->
      cloud(18f, 4f, 98f, 62f)
      val from = Pt(34f, 66f)
      val to = Pt(f.v(0, 12f), f.v(1, 92f))
      val m = from + (to - from) * 0.45f
      circle(m.x, m.y, 5.5f)
      circle(to.x, to.y, 3.5f)
    }

  val eye =
    natural(
      "eye", defaults = listOf(0f, 0f),
      joints = { f -> listOf(Joint(Pt(50f + f.v(0, 0f), 50f + f.v(1, 0f))) { g, p -> g.set(0, clampF(p.x - 50f, -26f, 26f)).set(1, clampF(p.y - 50f, -9f, 9f)) }) },
    ) { f ->
      curve(5, 50, 50, 18, 95, 50)
      curve(5, 50, 50, 82, 95, 50)
      val c = Pt(50f + f.v(0, 0f), 50f + f.v(1, 0f))
      circle(c.x, c.y, 16)
      circle(c.x, c.y, 6)
    }

  val face =
    natural(
      "face", defaults = listOf(12f),
      joints = { f -> listOf(Joint(Pt(50f, 62f + f.v(0, 12f))) { g, p -> g.set(0, clampF(p.y - 62f, -16f, 18f)) }) },
    ) { f ->
      circle(50, 50, 42)
      circle(36, 40, 4)
      circle(64, 40, 4)
      curve(30, 62, 40, 62 + f.v(0, 12f) * 0.85f, 50, 62 + f.v(0, 12f), 60, 62 + f.v(0, 12f) * 0.85f, 70, 62)
    }

  val cycle =
    sized(
      "cycle", 100f, 100f, 40f, 40f, listOf(4f),
      round = true, joints = { f ->
        val r = min(f.w, f.h) / 2f * 0.85f
        val c = Pt(min(f.w, f.h) / 2f, min(f.w, f.h) / 2f)
        val n = f.v(0, 4f).roundToInt().coerceIn(2, 8)
        val span = 360f / n
        listOf(
          Joint(c + Pt.polar(r, deg(-90f + span * 0.85f))) { g, p ->
            val a = ((atan2(p.y - c.y, p.x - c.x) * 180.0 / PI).toFloat() + 90f + 360f) % 360f
            g.set(0, (360f / max(a / 0.85f, 30f)).roundToInt().coerceIn(2, 8).toFloat())
          },
          diameterJoint(f, 40f),
        )
      },
    ) { f ->
      val d = min(f.w, f.h)
      val c = Pt(d / 2f, d / 2f)
      val r = d / 2f * 0.85f
      val n = f.v(0, 4f).roundToInt().coerceIn(2, 8)
      val span = 360f / n
      for (i in 0 until n) arc(c.x, c.y, r, -90f + span * i + span * 0.08f, span * 0.77f, arrow = true)
    }

  private val scatterDots = listOf(0.12f to 0.83f, 0.25f to 0.75f, 0.32f to 0.62f, 0.44f to 0.65f, 0.55f to 0.48f, 0.63f to 0.52f, 0.74f to 0.35f, 0.86f to 0.28f)

  val scatter =
    sized(
      "scatter", 130f, 100f, 50f, 40f, listOf(0.06f, 0.9f, 0.94f, 0.2f),
      joints = { f ->
        listOf(0, 2).map { i -> Joint(Pt(f.v(i, 0f) * f.w, f.v(i + 1, 0f) * f.h)) { g, p -> g.set(i, clampF(p.x / g.w, 0f, 1f)).set(i + 1, clampF(p.y / g.h, 0f, 1f)) } } + sizeJoints(f, 50f, 40f)
      },
    ) { f ->
      open(0, 0, 0, f.h, f.w, f.h)
      val r = clampF(min(f.w, f.h) * 0.025f, 1.5f, 4f)
      scatterDots.forEach { (x, y) -> circle(x * f.w, y * f.h, r) }
      line(f.v(0, 0.06f) * f.w, f.v(1, 0.9f) * f.h, f.v(2, 0.94f) * f.w, f.v(3, 0.2f) * f.h)
    }

  // ---- Economics (on axes from the bottom-left corner) ----

  private fun Draw.axesIn(f: Frame) {
    line(0, f.h, 0, 0, arrow = true)
    line(0, f.h, f.w, f.h, arrow = true)
  }

  private fun pointJoint(f: Frame, i: Int, default: Pair<Float, Float>) =
    Joint(Pt(f.v(i, default.first) * f.w, f.v(i + 1, default.second) * f.h)) { g, p -> g.set(i, clampF(p.x / g.w, 0.02f, 0.98f)).set(i + 1, clampF(p.y / g.h, 0.02f, 0.98f)) }

  private val sdDefaults = listOf(0.12f, 0.15f, 0.9f, 0.85f, 0.12f, 0.85f, 0.9f, 0.15f)

  val supplyDemand =
    sized(
      "supply-demand", 130f, 110f, 50f, 50f, sdDefaults,
      joints = { f -> (0 until 4).map { k -> pointJoint(f, 2 * k, sdDefaults[2 * k] to sdDefaults[2 * k + 1]) } },
    ) { f ->
      axesIn(f)
      for (k in listOf(0, 4)) line(f.v(k, sdDefaults[k]) * f.w, f.v(k + 1, sdDefaults[k + 1]) * f.h, f.v(k + 2, sdDefaults[k + 2]) * f.w, f.v(k + 3, sdDefaults[k + 3]) * f.h)
    }

  val axes =
    sized("axes", 120f, 100f, 30f, 30f, joints = { f -> listOf(Joint(Pt(f.w, f.h)) { g, p -> g.sized(w = max(p.x, 30f)) }, topJoint(f, 30f)) }) { f -> axesIn(f) }

  /**
   * A smooth bend from [a] to [b], pulled towards [corner] by [bulge] (0: straight). It passes
   * through the point its joint sits on, and is drawn through many points so it stays round.
   */
  private fun Draw.bentCurve(f: Frame, a: Pt, b: Pt, corner: Pt, bulge: Float) {
    val mid = Pt((a.x + b.x) / 2f, (a.y + b.y) / 2f)
    val m = mid + (corner - mid) * bulge
    // The quadratic curve through a, m (at its middle) and b.
    val c = m * 2f - mid
    curve(List(17) { i ->
      val t = i / 16f
      a * ((1 - t) * (1 - t)) + c * (2 * t * (1 - t)) + b * (t * t)
    })
  }

  val demand =
    sized(
      "demand", 130f, 110f, 50f, 50f, listOf(0.12f, 0.92f, 0.45f),
      joints = { f ->
        val a = Pt(0.1f * f.w, f.v(0, 0.12f) * f.h)
        val b = Pt(f.v(1, 0.92f) * f.w, 0.9f * f.h)
        val mid = Pt((a.x + b.x) / 2f, (a.y + b.y) / 2f)
        val corner = Pt(0.1f * f.w, 0.9f * f.h)
        listOf(
          Joint(a) { g, p -> g.set(0, clampF(p.y / g.h, 0.05f, 0.8f)) },
          Joint(b) { g, p -> g.set(1, clampF(p.x / g.w, 0.3f, 0.98f)) },
          Joint(mid + (corner - mid) * f.v(2, 0.45f)) { g, p ->
            val d = corner - mid
            g.set(2, clampF((p - mid).dot(d) / max(d.dot(d), 1f), -0.2f, 0.95f))
          },
        )
      },
    ) { f ->
      axesIn(f)
      bentCurve(f, Pt(0.1f * f.w, f.v(0, 0.12f) * f.h), Pt(f.v(1, 0.92f) * f.w, 0.9f * f.h), Pt(0.1f * f.w, 0.9f * f.h), f.v(2, 0.45f))
    }

  val indifference =
    sized("indifference", 130f, 110f, 50f, 50f, joints = { f -> sizeJoints(f, 50f, 50f) }) { f ->
      axesIn(f)
      val n = count(min(f.w, f.h) / 36f, 1, 7)
      for (k in 0 until n) {
        val s = 0.3f + 0.6f * (k + 1) / n
        val a = Pt(0.08f * f.w + 0.1f * s * f.w, f.h - s * f.h * 0.95f)
        val b = Pt(s * f.w * 0.95f, f.h - 0.08f * f.h - 0.1f * s * f.h)
        val corner = Pt(a.x, b.y)
        bentCurve(f, a, b, corner, 0.55f)
      }
    }

  val ppf =
    sized(
      "ppf", 130f, 110f, 50f, 50f, listOf(0.85f, 0.85f, 0.55f),
      joints = { f ->
        val yi = Pt(0f, f.h - f.v(1, 0.85f) * f.h)
        val xi = Pt(f.v(0, 0.85f) * f.w, f.h)
        val mid = Pt(xi.x / 2f, (yi.y + f.h) / 2f)
        val corner = Pt(xi.x, yi.y)
        listOf(
          Joint(xi) { g, p -> g.set(0, clampF(p.x / g.w, 0.2f, 0.98f)) },
          Joint(yi) { g, p -> g.set(1, clampF((g.h - p.y) / g.h, 0.2f, 0.98f)) },
          Joint(mid + (corner - mid) * f.v(2, 0.55f)) { g, p ->
            val d = corner - mid
            g.set(2, clampF((p - mid).dot(d) / max(d.dot(d), 1f), -0.5f, 0.95f))
          },
        )
      },
    ) { f ->
      axesIn(f)
      val yi = Pt(0f, f.h - f.v(1, 0.85f) * f.h)
      val xi = Pt(f.v(0, 0.85f) * f.w, f.h)
      bentCurve(f, yi, xi, Pt(xi.x, yi.y), f.v(2, 0.55f))
    }

  val costCurves =
    sized(
      "cost-curves", 130f, 110f, 50f, 50f, listOf(0.45f, 0.65f),
      joints = { f -> listOf(Joint(Pt(f.v(0, 0.45f) * f.w, f.v(1, 0.65f) * f.h)) { g, p -> g.set(0, clampF(p.x / g.w, 0.25f, 0.75f)).set(1, clampF(p.y / g.h, 0.25f, 0.85f)) }) + sizeJoints(f, 50f, 50f) },
    ) { f ->
      axesIn(f)
      val mx = f.v(0, 0.45f) * f.w
      val my = f.v(1, 0.65f) * f.h
      curve(0.12f * f.w, my - 0.45f * my, (0.12f * f.w + mx) / 2f, my - 0.05f * f.h, mx, my, (mx + 0.92f * f.w) / 2f, my - 0.12f * f.h, 0.92f * f.w, 0.12f * f.h)
      curve(0.15f * f.w, my - 0.55f * my, (0.15f * f.w + mx + 0.1f * f.w) / 2f, my - 0.08f * f.h, mx + 0.1f * f.w, my - 0.04f * f.h, 0.94f * f.w, my - 0.3f * f.h)
    }

  val lorenz =
    sized(
      "lorenz", 110f, 110f, 40f, 40f, listOf(0.5f),
      joints = { f ->
        val mid = Pt(f.w / 2f, f.h / 2f)
        val corner = Pt(f.w, f.h)
        listOf(Joint(mid + (corner - mid) * f.v(0, 0.5f)) { g, p ->
          val m = Pt(g.w / 2f, g.h / 2f)
          val d = Pt(g.w, g.h) - m
          g.set(0, clampF((p - m).dot(d) / max(d.dot(d), 1f), 0f, 0.95f))
        }) + sizeJoints(f, 40f, 40f)
      },
    ) { f ->
      rect(0, 0, f.w, f.h)
      line(0, f.h, f.w, 0)
      bentCurve(f, Pt(0f, f.h), Pt(f.w, 0f), Pt(f.w, f.h), f.v(0, 0.5f))
    }

  val businessCycle =
    sized(
      "business-cycle", 160f, 100f, 60f, 40f, listOf(0.16f),
      joints = { f ->
        val n = count(f.w / 80f, 1, 10)
        val period = f.w * 0.85f / n
        val x = 0.08f * f.w + period / 4f
        val trend = 0.78f * f.h - (x - 0.08f * f.w) / (0.85f * f.w) * 0.45f * f.h
        listOf(Joint(Pt(x, trend - f.v(0, 0.16f) * f.h)) { g, p ->
          val tr = 0.78f * g.h - (x - 0.08f * g.w) / (0.85f * g.w) * 0.45f * g.h
          g.set(0, clampF((tr - p.y) / g.h, 0.02f, 0.4f))
        }) + sizeJoints(f, 60f, 40f)
      },
    ) { f ->
      axesIn(f)
      val n = count(f.w / 80f, 1, 10)
      val a = f.v(0, 0.16f) * f.h
      val x0 = 0.08f * f.w
      val len = 0.85f * f.w
      line(x0, 0.78f * f.h, x0 + len, 0.78f * f.h - 0.45f * f.h)
      curve(List(n * 12 + 1) { i ->
        val t = i / (n * 12f)
        Pt(x0 + t * len, 0.78f * f.h - t * 0.45f * f.h - a * sin(t * n * 2 * PI).toFloat())
      })
    }

  val laffer =
    sized(
      "laffer", 130f, 110f, 50f, 50f, listOf(0.5f, 0.15f),
      joints = { f -> listOf(Joint(Pt(f.v(0, 0.5f) * f.w, f.v(1, 0.15f) * f.h)) { g, p -> g.set(0, clampF(p.x / g.w, 0.15f, 0.85f)).set(1, clampF(p.y / g.h, 0.05f, 0.8f)) }) + sizeJoints(f, 50f, 50f) },
    ) { f ->
      axesIn(f)
      val px = f.v(0, 0.5f) * f.w
      val py = f.v(1, 0.15f) * f.h
      curve(0, f.h, px * 0.45f, py + (f.h - py) * 0.38f, px, py, px + (0.97f * f.w - px) * 0.55f, py + (f.h - py) * 0.38f, 0.97f * f.w, f.h)
    }

  // ---- Design and art ----

  val colourWheel =
    sized(
      "colour-wheel", 100f, 100f, 30f, 30f, listOf(0.38f),
      round = true, joints = { f ->
        val r = min(f.w, f.h) / 2f
        listOf(Joint(Pt(r + f.v(0, 0.38f) * r, r)) { g, p -> g.set(0, clampF((p.x - min(g.w, g.h) / 2f) / (min(g.w, g.h) / 2f), 0.1f, 0.85f)) }, Joint(Pt(r, 2 * r)) { g, p -> max(p.y, 30f).let { d -> g.sized(w = d, h = d) } })
      },
    ) { f ->
      val r = min(f.w, f.h) / 2f
      val ri = f.v(0, 0.38f) * r
      circle(r, r, r)
      circle(r, r, ri)
      val n = if (r < 55f) 6 else 12
      for (i in 0 until n) {
        val a = 2 * PI * i / n - PI / 2
        line(r + ri * cos(a).toFloat(), r + ri * sin(a).toFloat(), r + r * cos(a).toFloat(), r + r * sin(a).toFloat())
      }
    }

  val thirds =
    sized("thirds", 150f, 100f, 30f, 30f, joints = { f -> sizeJoints(f, 30f, 30f) }) { f ->
      rect(0, 0, f.w, f.h)
      for (k in 1..2) {
        line(f.w * k / 3f, 0, f.w * k / 3f, f.h)
        line(0, f.h * k / 3f, f.w, f.h * k / 3f)
      }
    }

  val perspective =
    sized(
      "perspective", 160f, 110f, 50f, 40f, listOf(0.5f, 0.4f),
      joints = { f -> listOf(Joint(Pt(f.v(0, 0.5f) * f.w, f.v(1, 0.4f) * f.h)) { g, p -> g.set(0, clampF(p.x / g.w, 0f, 1f)).set(1, clampF(p.y / g.h, 0.05f, 0.95f)) }) + sizeJoints(f, 50f, 40f) },
    ) { f ->
      val vp = Pt(f.v(0, 0.5f) * f.w, f.v(1, 0.4f) * f.h)
      line(0, vp.y, f.w, vp.y)
      for ((x, y) in listOf(0f to f.h, f.w * 0.3f to f.h, f.w * 0.7f to f.h, f.w to f.h, 0f to 0f, f.w to 0f)) line(vp.x, vp.y, x, y)
    }

  val frame =
    sized(
      "frame", 120f, 100f, 30f, 30f, listOf(0.1f),
      joints = { f ->
        val b = f.v(0, 0.1f) * min(f.w, f.h)
        listOf(Joint(Pt(b, b)) { g, p -> g.set(0, clampF(min(p.x, p.y) / min(g.w, g.h), 0.02f, 0.4f)) }) + sizeJoints(f, 30f, 30f)
      },
    ) { f ->
      val b = f.v(0, 0.1f) * min(f.w, f.h)
      rect(0, 0, f.w, f.h)
      rect(b, b, f.w - b, f.h - b)
      line(0, 0, b, b)
      line(f.w, 0, f.w - b, b)
      line(f.w, f.h, f.w - b, f.h - b)
      line(0, f.h, b, f.h - b)
    }

  val cube =
    sized(
      "cube", 100f, 100f, 30f, 30f, listOf(0.3f),
      joints = { f ->
        val d = f.v(0, 0.3f) * min(f.w, f.h)
        listOf(Joint(Pt(d, 0f)) { g, p -> g.set(0, clampF(p.x / min(g.w, g.h), 0.05f, 0.6f)) }) + sizeJoints(f, 30f, 30f)
      },
    ) { f ->
      val d = f.v(0, 0.3f) * min(f.w, f.h)
      rect(0, d, f.w - d, f.h)
      open(0, d, d, 0, f.w, 0, f.w, f.h - d, f.w - d, f.h)
      line(f.w - d, d, f.w, 0)
    }

  val pencil =
    line("pencil", 130f, 40f) { f ->
      val tip = min(25f, f.w * 0.3f)
      rect(0, -8, f.w - tip, 8)
      line(min(12f, f.w * 0.1f), -8, min(12f, f.w * 0.1f), 8)
      open(f.w - tip, -8, f.w, 0, f.w - tip, 8)
      line(f.w - tip * 0.36f, -3, f.w - tip * 0.36f, 3)
    }
}
