package com.tali.notes.model

import com.tali.notes.geometry.Affine
import com.tali.notes.geometry.Pt
import com.tali.notes.geometry.Rect
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Shapes for each subject (degree): line drawings built from the same pieces as every other shape
 * (lines, arrows, outlines, circles, arcs, curves), so once drawn they're ordinary shapes: they
 * snap, line up, move, resize, recolour and lock like any other. The Shapes panel shows the
 * standard shapes and her own; a subject's shapes join it when she stars that subject
 * ([com.tali.notes.data.AppSettings.starredPacks]); all of them are in the "More shapes" window.
 */
class ShapePack(val id: String, val name: String, val shapes: List<PackShape>)

/** How a subject shape follows the pen as it's drawn. */
enum class PackMode {
  /** Keeps its proportions, as big as the dragged box allows (a person, a flask, a gear). */
  SYMBOL,

  /** Stretches to fill the dragged box (a table, a chart frame, a flowchart box). */
  STRETCH,

  /**
   * Drawn like a line from one end to the other (a resistor, a spring, a timeline): its ends snap
   * where a line's would, onto edges and corners and at tidy angles, so it connects to things.
   */
  LINE,
}

/**
 * One shape in a subject. Either one of the built-in kinds ([kind], the civil engineering symbols
 * with their own dots and snapping), or a [design]: drawn in its own frame (for [PackMode.LINE],
 * from x = 0 to 100 along y = 0) and fitted to the pen when drawn.
 */
class PackShape(val id: String, val label: String, val mode: PackMode, private val design: ShapeGeometry? = null, val kind: ShapeKind? = null) {
  private val frame: Rect by lazy { design?.let { Rect.around(it.paths(1f).flatten()) } ?: Rect(0f, 0f, 1f, 1f) }

  /** For the panel's tile. */
  val icon: ShapeGeometry
    get() = design ?: kind!!.inBox(Pt(0f, 0f), Pt(100f, 100f))

  val drawnAsLine: Boolean
    get() = kind?.drawnAsLine ?: (mode == PackMode.LINE)

  val drawnFromPoint: Boolean
    get() = kind?.drawnFromPoint ?: false

  /** For hold-to-shape: how its lines are spread, to compare with a drawn stroke (designs only). */
  val cloud: com.tali.notes.geometry.ShapeMatcher.Cloud? by lazy { design?.let { com.tali.notes.geometry.ShapeMatcher.cloud(it.paths(CustomShape.REFERENCE_PEN)) } }

  /** Over the box a held stroke covered; one drawn like a line runs from the stroke's [first] point to its [last]. */
  fun placeOver(box: Rect, first: Pt, last: Pt): ShapeGeometry =
    if (mode == PackMode.LINE) create(first, last) else create(Pt(box.left, box.top), Pt(box.right, box.bottom))

  /** The shape drawn from where the pen landed ([p0]) to where it is ([p1]). */
  fun create(p0: Pt, p1: Pt): ShapeGeometry {
    kind?.let { return it.create(p0, p1) }
    val d = design!!
    val f = frame
    return when (mode) {
      PackMode.LINE -> {
        // Its frame's x = 0 lands on p0 and x = 100 on p1, turned to the line's direction.
        val len = max(p0.dist(p1), 8f)
        val s = len / 100f
        val angle = if (p0.dist(p1) < 1f) 0f else atan2(p1.y - p0.y, p1.x - p0.x)
        val c = cos(angle) * s
        val sn = sin(angle) * s
        d.transformed(Affine(a = c, b = sn, c = -sn, d = c, tx = p0.x, ty = p0.y))
      }
      PackMode.STRETCH -> {
        val l = min(p0.x, p1.x)
        val t = min(p0.y, p1.y)
        val w = max(abs(p1.x - p0.x), 8f)
        val h = max(abs(p1.y - p0.y), 8f)
        stretched(d, w / max(f.width, 1f), h / max(f.height, 1f), l - f.left * (w / max(f.width, 1f)), t - f.top * (h / max(f.height, 1f)))
      }
      PackMode.SYMBOL -> {
        val w = max(abs(p1.x - p0.x), 8f)
        val h = max(abs(p1.y - p0.y), 8f)
        val s = min(w / max(f.width, 1f), h / max(f.height, 1f))
        val cx = (p0.x + p1.x) / 2f
        val cy = (p0.y + p1.y) / 2f
        d.transformed(Affine(a = s, d = s, tx = cx - f.center.x * s, ty = cy - f.center.y * s))
      }
    }
  }
}

/**
 * [g] scaled by [sx] across and [sy] down, then moved by ([tx], [ty]). Unlike a general transform
 * (which keeps a circle a circle), a stretched ellipse stretches with it. Designs that stretch use
 * no arcs: an arc of a circle can't stretch.
 */
private fun stretched(g: ShapeGeometry, sx: Float, sy: Float, tx: Float, ty: Float): ShapeGeometry {
  fun p(q: Pt) = Pt(q.x * sx + tx, q.y * sy + ty)
  return when (g) {
    is EllipseShape -> g.copy(center = p(g.center), rx = g.rx * sx, ry = g.ry * sy)
    is GroupShape -> GroupShape(g.parts.map { stretched(it, sx, sy, tx, ty) })
    else -> g.transformed(Affine(a = sx, d = sy, tx = tx, ty = ty))
  }
}

/** A small drawing language for the designs (y goes down, as on the page). */
private class Draw {
  val parts = mutableListOf<ShapeGeometry>()

  fun line(x1: Number, y1: Number, x2: Number, y2: Number, arrow: Boolean = false, both: Boolean = false) {
    parts += LineShape(pt(x1, y1), pt(x2, y2), arrowStart = both, arrowEnd = arrow || both)
  }

  /** A closed outline through the points (x, y, x, y, …). */
  fun poly(vararg xy: Number) {
    parts += PolyShape(pts(xy), true)
  }

  /** An open run of straight pieces. */
  fun open(vararg xy: Number) {
    parts += PolyShape(pts(xy), false)
  }

  /** A smooth curve through the points (repeat the first point at the end for a closed loop). */
  fun curve(vararg xy: Number) {
    parts += PolyShape(pts(xy), false, smooth = true)
  }

  fun curve(points: List<Pt>) {
    parts += PolyShape(points, false, smooth = true)
  }

  fun rect(l: Number, t: Number, r: Number, b: Number) = poly(l, t, r, t, r, b, l, b)

  fun circle(cx: Number, cy: Number, r: Number) {
    parts += EllipseShape(pt(cx, cy), r.toFloat(), r.toFloat())
  }

  fun ellipse(cx: Number, cy: Number, rx: Number, ry: Number) {
    parts += EllipseShape(pt(cx, cy), rx.toFloat(), ry.toFloat())
  }

  /** An arc of a circle, from [fromDeg] going [sweepDeg] (positive turns clockwise on the page). */
  fun arc(cx: Number, cy: Number, r: Number, fromDeg: Number, sweepDeg: Number, arrow: Boolean = false) {
    parts += ArcShape(pt(cx, cy), r.toFloat(), rad(fromDeg), rad(sweepDeg), arrowEnd = arrow)
  }

  /** Part of an ellipse as a smooth curve (so it can stretch), from [fromDeg] to [toDeg]. */
  fun ellipseArc(cx: Number, cy: Number, rx: Number, ry: Number, fromDeg: Number, toDeg: Number) {
    val n = 12
    curve(List(n + 1) { i ->
      val a = rad(fromDeg.toFloat() + (toDeg.toFloat() - fromDeg.toFloat()) * i / n)
      Pt(cx.toFloat() + rx.toFloat() * cos(a), cy.toFloat() + ry.toFloat() * sin(a))
    })
  }

  /** A line between two circles' centres, stopping at their edges (radius [r]). */
  fun link(x1: Number, y1: Number, x2: Number, y2: Number, r: Number, arrow: Boolean = false) {
    val a = pt(x1, y1)
    val b = pt(x2, y2)
    val dir = (b - a).normalized()
    parts += LineShape(a + dir * r.toFloat(), b - dir * r.toFloat(), arrowEnd = arrow)
  }

  /** A little person: head and shoulders, [h] tall, standing on ([x], [bottom]). */
  fun person(x: Float, bottom: Float, h: Float) {
    circle(x, bottom - h * 0.72f, h * 0.2f)
    arc(x, bottom, h * 0.42f, 180, 180)
    line(x - h * 0.42f, bottom, x + h * 0.42f, bottom)
  }

  /** Axes with arrows, from the origin at ([x0], [y0]) up to [top] and across to [right]. */
  fun axes(x0: Number = 10, y0: Number = 90, top: Number = 5, right: Number = 95) {
    line(x0, y0, x0, top, arrow = true)
    line(x0, y0, right, y0, arrow = true)
  }

  // Always a group, even of one piece: a symbol moves, resizes and turns whole (the resize chip),
  // with no dots to pull single corners out of shape.
  fun build(): ShapeGeometry = GroupShape(parts.toList())

  private fun pt(x: Number, y: Number) = Pt(x.toFloat(), y.toFloat())

  private fun pts(xy: Array<out Number>) = List(xy.size / 2) { i -> pt(xy[2 * i], xy[2 * i + 1]) }

  private fun rad(d: Number) = (d.toFloat() * PI / 180.0).toFloat()
}

private fun draw(block: Draw.() -> Unit): ShapeGeometry = Draw().apply(block).build()

private fun symbol(id: String, label: String, block: Draw.() -> Unit) = PackShape(id, label, PackMode.SYMBOL, draw(block))

private fun stretch(id: String, label: String, block: Draw.() -> Unit) = PackShape(id, label, PackMode.STRETCH, draw(block))

private fun along(id: String, label: String, block: Draw.() -> Unit) = PackShape(id, label, PackMode.LINE, draw(block))

private fun builtIn(kind: ShapeKind) = PackShape("kind-${kind.name}", kind.label, PackMode.SYMBOL, kind = kind)

/**
 * A cloud (for the cloud and the thought bubble): four round bumps on a flat base, fitted into the
 * box from ([l], [t]) to ([r], [b]) (drawn on a 100 × 60 grid, scaled evenly).
 */
private fun Draw.cloud(l: Float, t: Float, r: Float, b: Float) {
  val s = minOf((r - l) / 100f, (b - t) / 60f)
  val ox = l + ((r - l) - 100f * s) / 2f
  val oy = t + ((b - t) - 60f * s) / 2f
  fun x(v: Float) = ox + v * s
  fun y(v: Float) = oy + v * s
  arc(x(18f), y(40f), 16f * s, 90, 180)
  arc(x(38f), y(26f), 20f * s, 180, 140)
  arc(x(62f), y(26f), 18f * s, 215, 145)
  arc(x(82f), y(40f), 16f * s, 270, 180)
  line(x(18f), y(56f), x(82f), y(56f))
}

object ShapePacks {
  private val social =
    ShapePack(
      "social",
      "Social Sciences",
      listOf(
        symbol("person", "Person") { person(50f, 95f, 90f) },
        symbol("group", "Group") {
          person(25f, 85f, 55f)
          person(75f, 85f, 55f)
          person(50f, 95f, 70f)
        },
        symbol("network", "Network") {
          val n = listOf(20 to 25, 78 to 18, 50 to 55, 22 to 85, 82 to 80)
          n.forEach { (x, y) -> circle(x, y, 8) }
          listOf(0 to 2, 1 to 2, 2 to 3, 2 to 4, 0 to 1, 3 to 4).forEach { (a, b) -> link(n[a].first, n[a].second, n[b].first, n[b].second, 8) }
        },
        symbol("genogram", "Family tree") {
          rect(10, 8, 34, 32)
          circle(76, 20, 12)
          line(34, 20, 64, 20)
          line(49, 20, 49, 50)
          line(22, 50, 76, 50)
          line(22, 50, 22, 66)
          line(76, 50, 76, 66)
          rect(10, 66, 34, 90)
          circle(76, 78, 12)
        },
        symbol("male", "Male") {
          circle(40, 60, 26)
          line(58, 42, 88, 12, arrow = true)
        },
        symbol("female", "Female") {
          circle(50, 36, 26)
          line(50, 62, 50, 96)
          line(34, 80, 66, 80)
        },
        stretch("speech", "Speech bubble") { poly(10, 10, 90, 10, 90, 64, 46, 64, 26, 86, 30, 64, 10, 64) },
        symbol("globe", "Globe") {
          circle(50, 50, 40)
          ellipse(50, 50, 17, 40)
          line(10, 50, 90, 50)
          curve(16, 30, 50, 24, 84, 30)
          curve(16, 70, 50, 76, 84, 70)
        },
        along("timeline", "Timeline") {
          line(0, 0, 100, 0, arrow = true)
          line(20, -7, 20, 7)
          line(45, -7, 45, 7)
          line(70, -7, 70, 7)
        },
        symbol("venn", "Venn diagram") {
          circle(36, 50, 30)
          circle(64, 50, 30)
        },
      ),
    )

  private val business =
    ShapePack(
      "business",
      "Business & Management",
      listOf(
        stretch("org-chart", "Org chart") {
          rect(35, 5, 65, 25)
          line(50, 25, 50, 40)
          line(15, 40, 85, 40)
          line(15, 40, 15, 55)
          line(50, 40, 50, 55)
          line(85, 40, 85, 55)
          rect(3, 55, 27, 75)
          rect(38, 55, 62, 75)
          rect(73, 55, 97, 75)
        },
        symbol("bar-chart", "Bar chart") {
          open(10, 8, 10, 90, 95, 90)
          rect(20, 60, 32, 90)
          rect(40, 40, 52, 90)
          rect(60, 25, 72, 90)
          rect(80, 50, 92, 90)
        },
        symbol("pie-chart", "Pie chart") {
          circle(50, 50, 40)
          line(50, 50, 50, 10)
          line(50, 50, 50 + 40 * cos(PI / 9), 50 + 40 * sin(PI / 9))
          line(50, 50, 50 + 40 * cos(7 * PI / 9), 50 + 40 * sin(7 * PI / 9))
        },
        symbol("growth", "Growth chart") {
          open(10, 8, 10, 90, 95, 90)
          open(16, 80, 34, 62, 50, 70, 68, 42)
          line(68, 42, 90, 18, arrow = true)
        },
        symbol("funnel", "Funnel") {
          poly(10, 10, 90, 10, 60, 55, 60, 90, 40, 90, 40, 55)
          line(23.3, 30, 76.7, 30)
        },
        stretch("swot", "SWOT grid") {
          rect(0, 0, 100, 100)
          line(50, 0, 50, 100)
          line(0, 50, 100, 50)
        },
        stretch("gantt", "Gantt chart") {
          open(5, 2, 5, 98, 100, 98)
          rect(10, 10, 45, 24)
          rect(30, 32, 70, 46)
          rect(50, 54, 85, 68)
          rect(70, 76, 98, 90)
        },
        symbol("target", "Target") {
          circle(50, 50, 40)
          circle(50, 50, 26)
          circle(50, 50, 12)
          line(96, 4, 53, 47, arrow = true)
        },
        symbol("coins", "Coins") {
          ellipse(50, 30, 36, 11)
          line(14, 30, 14, 70)
          line(86, 30, 86, 70)
          ellipseArc(50, 50, 36, 11, 0, 180)
          ellipseArc(50, 70, 36, 11, 0, 180)
        },
        stretch("document", "Document") {
          open(10, 82, 10, 5, 90, 5, 90, 82)
          curve(90, 82, 70, 74, 50, 82, 30, 90, 10, 82)
        },
      ),
    )

  private val computing =
    ShapePack(
      "computing",
      "Computer Science & IT",
      listOf(
        symbol("database", "Database") {
          ellipse(50, 18, 38, 11)
          line(12, 18, 12, 82)
          line(88, 18, 88, 82)
          ellipseArc(50, 82, 38, 11, 0, 180)
        },
        stretch("terminal", "Start / end") {
          line(25, 20, 75, 20)
          line(25, 80, 75, 80)
          ellipseArc(25, 50, 25, 30, 90, 270)
          ellipseArc(75, 50, 25, 30, -90, 90)
        },
        stretch("subroutine", "Subroutine") {
          rect(0, 20, 100, 80)
          line(12, 20, 12, 80)
          line(88, 20, 88, 80)
        },
        symbol("binary-tree", "Binary tree") {
          val n = listOf(50 to 14, 28 to 48, 72 to 48, 15 to 84, 41 to 84, 85 to 84)
          n.forEach { (x, y) -> circle(x, y, 9) }
          listOf(0 to 1, 0 to 2, 1 to 3, 1 to 4, 2 to 5).forEach { (a, b) -> link(n[a].first, n[a].second, n[b].first, n[b].second, 9) }
        },
        symbol("graph", "Directed graph") {
          val n = listOf(18 to 22, 82 to 20, 50 to 52, 22 to 84, 82 to 80)
          n.forEach { (x, y) -> circle(x, y, 9) }
          listOf(0 to 1, 0 to 2, 2 to 1, 2 to 3, 1 to 4, 4 to 3).forEach { (a, b) -> link(n[a].first, n[a].second, n[b].first, n[b].second, 9, arrow = true) }
        },
        stretch("array", "Array") {
          rect(0, 0, 100, 20)
          for (x in listOf(20, 40, 60, 80)) line(x, 0, x, 20)
        },
        along("linked-list", "Linked list") {
          for (x0 in listOf(0, 39, 78)) {
            rect(x0, -10, x0 + 22, 10)
            line(x0 + 15, -10, x0 + 15, 10)
          }
          line(18.5, 0, 39, 0, arrow = true)
          line(57.5, 0, 78, 0, arrow = true)
        },
        symbol("stack", "Stack") {
          rect(20, 40, 80, 55)
          rect(20, 55, 80, 70)
          rect(20, 70, 80, 85)
          line(50, 6, 50, 34, arrow = true)
        },
        symbol("computer", "Computer") {
          rect(8, 8, 92, 64)
          rect(14, 14, 86, 58)
          line(50, 64, 50, 80)
          line(30, 82, 70, 82)
        },
        symbol("cloud", "Cloud") { cloud(0f, 20f, 100f, 80f) },
      ),
    )

  private val law =
    ShapePack(
      "law",
      "Law",
      listOf(
        symbol("scales", "Scales of justice") {
          line(50, 12, 50, 88)
          line(32, 92, 68, 92)
          line(14, 22, 86, 22)
          circle(50, 10, 3)
          for (x in listOf(14f, 86f)) {
            line(x, 22f, x - 11.3f, 52f)
            line(x, 22f, x + 11.3f, 52f)
            arc(x, 48f, 12f, 20f, 140f)
          }
        },
        symbol("gavel", "Gavel") {
          poly(18, 22, 42, 6, 58, 30, 34, 46)
          line(46, 38, 86, 76)
          rect(54, 84, 96, 94)
        },
        symbol("courthouse", "Courthouse") {
          poly(8, 30, 50, 8, 92, 30)
          rect(10, 30, 90, 36)
          for (x in listOf(20, 35, 50, 65, 80)) line(x, 36, x, 80)
          rect(5, 80, 95, 88)
        },
        symbol("column", "Column") {
          rect(25, 8, 75, 18)
          line(32, 18, 32, 84)
          line(68, 18, 68, 84)
          line(44, 22, 44, 80)
          line(56, 22, 56, 80)
          rect(22, 84, 78, 92)
        },
        symbol("contract", "Contract") {
          rect(15, 5, 85, 95)
          line(25, 20, 75, 20)
          line(25, 32, 75, 32)
          line(25, 44, 60, 44)
          curve(25, 78, 33, 68, 40, 80, 48, 70, 56, 78)
          line(25, 84, 75, 84)
        },
        symbol("shield", "Shield") { curve(50, 8, 84, 16, 82, 55, 50, 92, 18, 55, 16, 16, 50, 8) },
        symbol("court-levels", "Court hierarchy") {
          rect(35, 10, 65, 35)
          rect(20, 35, 80, 60)
          rect(5, 60, 95, 85)
        },
        symbol("parties", "Two parties") {
          person(18f, 85f, 60f)
          person(82f, 85f, 60f)
          line(38, 55, 62, 55, both = true)
        },
        symbol("handcuffs", "Handcuffs") {
          circle(26, 64, 18)
          circle(74, 64, 18)
          line(30, 46, 42, 32)
          line(70, 46, 58, 32)
          ellipse(50, 30, 9, 5)
        },
        symbol("law-book", "Law book") {
          open(50, 24, 10, 14, 10, 80, 50, 90)
          open(50, 24, 90, 14, 90, 80, 50, 90)
          line(50, 24, 50, 90)
        },
      ),
    )

  private val health =
    ShapePack(
      "health",
      "Nursing & Health",
      listOf(
        symbol("med-cross", "Medical cross") { poly(35, 5, 65, 5, 65, 35, 95, 35, 95, 65, 65, 65, 65, 95, 35, 95, 35, 65, 5, 65, 5, 35, 35, 35) },
        symbol("heart", "Heart") { curve(50, 90, 16, 56, 8, 30, 25, 12, 42, 14, 50, 28, 58, 14, 75, 12, 92, 30, 84, 56, 50, 90) },
        along("heartbeat", "Heartbeat") { open(0, 0, 30, 0, 37, -8, 43, 0, 50, 0, 55, 22, 62, -40, 68, 10, 72, 0, 100, 0) },
        symbol("capsule", "Capsule") {
          line(25, 35, 75, 35)
          line(25, 65, 75, 65)
          arc(25, 50, 15, 90, 180)
          arc(75, 50, 15, -90, 180)
          line(50, 35, 50, 65)
        },
        along("syringe", "Syringe") {
          // Few short lines, so its ends stay points to snap to (five or more read as hatching).
          rect(20, -10, 70, 10)
          line(0, 0, 20, 0)
          line(0, -9, 0, 9)
          rect(70, -4, 77, 4)
          line(77, 0, 100, 0)
        },
        symbol("thermometer", "Thermometer") {
          line(42, 12, 42, 68)
          line(58, 12, 58, 68)
          arc(50, 12, 8, 180, 180)
          circle(50, 80, 14)
          for (y in listOf(25, 38, 51)) line(58, y, 66, y)
        },
        symbol("stethoscope", "Stethoscope") {
          curve(30, 8, 28, 40, 50, 60)
          curve(70, 8, 72, 40, 50, 60)
          curve(50, 60, 50, 76, 66, 86, 80, 76, 80, 66)
          circle(80, 58, 8)
        },
        symbol("body", "Body outline") {
          circle(50, 12, 8)
          poly(40, 22, 60, 22, 75, 55, 68, 57, 58, 35, 58, 60, 62, 95, 54, 95, 50, 66, 46, 95, 38, 95, 42, 60, 42, 35, 32, 57, 25, 55)
        },
        symbol("iv-bag", "IV bag") {
          rect(30, 5, 70, 55)
          line(30, 20, 70, 20)
          line(45, 55, 45, 62)
          line(55, 55, 55, 62)
          rect(43, 62, 57, 76)
          curve(50, 76, 50, 86, 60, 95)
        },
        symbol("drop", "Blood drop") { curve(50, 8, 36, 38, 28, 62, 35, 80, 50, 88, 65, 80, 72, 62, 64, 38, 50, 8) },
      ),
    )

  private val mechanical =
    ShapePack(
      "mechanical",
      "Mechanical Engineering",
      listOf(
        symbol("gear", "Gear") {
          val p = mutableListOf<Number>()
          for (t in 0 until 8) {
            val base = t * 45.0
            for ((r, d) in listOf(34.0 to -14.0, 45.0 to -8.0, 45.0 to 8.0, 34.0 to 14.0)) {
              val a = (base + d) * PI / 180
              p += 50 + r * cos(a)
              p += 50 + r * sin(a)
            }
          }
          poly(*p.toTypedArray())
          circle(50, 50, 12)
        },
        along("spring", "Spring") {
          val p = mutableListOf<Number>(0, 0, 15, 0)
          for (i in 1..8) {
            p += 15 + i * 8.75 - 4.375
            p += if (i % 2 == 1) -12 else 12
          }
          p += listOf(85, 0, 100, 0)
          open(*p.toTypedArray())
        },
        along("damper", "Damper") {
          line(0, 0, 35, 0)
          open(68, -14, 35, -14, 35, 14, 68, 14)
          line(52, -10, 52, 10)
          line(52, 0, 100, 0)
        },
        symbol("bearing", "Bearing") {
          circle(50, 50, 42)
          circle(50, 50, 28)
          circle(50, 50, 12)
          for (i in 0 until 8) {
            val a = i * PI / 4
            circle(50 + 35 * cos(a), 50 + 35 * sin(a), 5)
          }
        },
        symbol("pulley", "Pulley") {
          line(32, 2, 68, 2)
          line(50, 2, 50, 14)
          circle(50, 36, 22)
          circle(50, 36, 4)
          line(28, 36, 28, 96)
          line(72, 36, 72, 76)
          rect(62, 76, 82, 96)
        },
        symbol("lever", "Lever") {
          line(5, 42, 95, 42)
          poly(50, 42, 40, 62, 60, 62)
          line(10, 8, 10, 40, arrow = true)
          line(90, 8, 90, 40, arrow = true)
        },
        symbol("piston", "Piston") {
          open(20, 12, 20, 92, 80, 92, 80, 12)
          rect(22, 42, 78, 58)
          line(50, 42, 50, 2)
        },
        symbol("mass", "Mass on ground") {
          rect(28, 22, 72, 66)
          line(8, 66, 92, 66)
          for (i in 0 until 7) line(14 + i * 12, 66, 8 + i * 12, 76)
        },
        along("shaft", "Shaft") {
          rect(0, -12, 100, 12)
          rect(40, -12, 60, -6)
          line(-6, 0, 106, 0)
        },
        symbol("bolt", "Bolt") {
          poly(34, 10, 66, 10, 74, 22, 66, 34, 34, 34, 26, 22)
          rect(40, 34, 60, 94)
          for (y in listOf(52, 62, 72, 82)) line(40, y, 60, y - 5)
        },
      ),
    )

  private val civil =
    ShapePack(
      "civil",
      "Civil Engineering",
      listOf(
        builtIn(ShapeKind.PIN_SUPPORT),
        builtIn(ShapeKind.ROLLER_SUPPORT),
        builtIn(ShapeKind.FIXED_SUPPORT),
        builtIn(ShapeKind.POINT_LOAD),
        builtIn(ShapeKind.DISTRIBUTED_LOAD),
        builtIn(ShapeKind.MOMENT),
        builtIn(ShapeKind.DIMENSION),
        symbol("i-beam", "I-beam section") { poly(10, 5, 90, 5, 90, 20, 57, 20, 57, 80, 90, 80, 90, 95, 10, 95, 10, 80, 43, 80, 43, 20, 10, 20) },
        stretch("truss", "Truss") {
          line(0, 50, 100, 50)
          line(25, 0, 75, 0)
          line(0, 50, 25, 0)
          line(75, 0, 100, 50)
          line(25, 0, 25, 50)
          line(50, 0, 50, 50)
          line(75, 0, 75, 50)
          line(25, 0, 50, 50)
          line(75, 0, 50, 50)
        },
        symbol("level", "Level mark") {
          poly(38, 22, 62, 22, 50, 42)
          line(8, 42, 92, 42)
          line(28, 50, 72, 50)
          line(38, 57, 62, 57)
        },
      ),
    )

  private val electrical =
    ShapePack(
      "electrical",
      "Electrical Engineering",
      listOf(
        along("resistor", "Resistor") { open(0, 0, 25, 0, 29, -10, 37, 10, 45, -10, 53, 10, 61, -10, 69, 10, 75, 0, 100, 0) },
        along("capacitor", "Capacitor") {
          line(0, 0, 45, 0)
          line(45, -18, 45, 18)
          line(55, -18, 55, 18)
          line(55, 0, 100, 0)
        },
        along("inductor", "Inductor") {
          line(0, 0, 20, 0)
          for (i in 0 until 4) arc(27.5 + 15 * i, 0, 7.5, 180, 180)
          line(80, 0, 100, 0)
        },
        along("diode", "Diode") {
          line(0, 0, 36, 0)
          poly(36, -15, 36, 15, 62, 0)
          line(62, -15, 62, 15)
          line(62, 0, 100, 0)
        },
        along("battery", "Battery") {
          line(0, 0, 42, 0)
          line(42, -18, 42, 18)
          line(54, -9, 54, 9)
          line(54, 0, 100, 0)
        },
        along("ac-source", "AC source") {
          line(0, 0, 30, 0)
          circle(50, 0, 20)
          curve(37, 0, 43.5, -9, 50, 0, 56.5, 9, 63, 0)
          line(70, 0, 100, 0)
        },
        along("switch", "Switch") {
          line(0, 0, 30, 0)
          circle(32, 0, 2)
          line(34, 0, 67, -18)
          circle(70, 0, 2)
          line(72, 0, 100, 0)
        },
        along("lamp", "Lamp") {
          line(0, 0, 30, 0)
          circle(50, 0, 20)
          line(35.9, -14.1, 64.1, 14.1)
          line(35.9, 14.1, 64.1, -14.1)
          line(70, 0, 100, 0)
        },
        symbol("ground", "Ground") {
          line(50, 5, 50, 45)
          line(20, 45, 80, 45)
          line(30, 57, 70, 57)
          line(40, 69, 60, 69)
        },
        symbol("and-gate", "AND gate") {
          open(50, 15, 25, 15, 25, 85, 50, 85)
          arc(50, 50, 35, -90, 180)
          line(5, 32, 25, 32)
          line(5, 68, 25, 68)
          line(85, 50, 100, 50)
        },
      ),
    )

  private val psychology =
    ShapePack(
      "psychology",
      "Psychology",
      listOf(
        symbol("brain", "Brain") {
          curve(20, 62, 10, 45, 18, 28, 32, 18, 50, 15, 68, 18, 82, 28, 90, 45, 80, 62, 65, 70, 50, 66, 35, 70, 20, 62)
          curve(50, 15, 47, 35, 53, 50, 50, 66)
          curve(15, 38, 24, 32, 31, 40, 39, 33, 46, 38)
          curve(54, 38, 61, 33, 69, 40, 76, 32, 85, 38)
          curve(17, 54, 25, 48, 32, 56, 40, 50, 47, 56)
          curve(53, 56, 60, 50, 68, 56, 75, 48, 83, 54)
        },
        symbol("head", "Head profile") { curve(35, 96, 35, 76, 24, 64, 21, 44, 28, 24, 45, 11, 66, 12, 80, 25, 84, 40, 91, 55, 83, 58, 83, 68, 75, 72, 71, 82, 71, 96) },
        symbol("neuron", "Neuron") {
          circle(24, 50, 12)
          line(15, 42, 5, 28)
          line(15, 58, 5, 72)
          line(22, 38, 18, 20)
          line(22, 62, 18, 80)
          line(36, 50, 82, 50)
          ellipse(50, 50, 7, 5)
          ellipse(67, 50, 7, 5)
          line(82, 50, 94, 40)
          line(82, 50, 96, 50)
          line(82, 50, 94, 60)
        },
        symbol("bell-curve", "Bell curve") {
          line(5, 88, 95, 88)
          curve(List(17) { i ->
            val x = 10f + i * 5f
            Pt(x, 88f - 66f * exp(-((x - 50f) / 14f).let { it * it } / 2f))
          })
          line(50, 22, 50, 88)
        },
        symbol("maslow", "Maslow pyramid") {
          poly(50, 5, 95, 90, 5, 90)
          line(38.9, 26, 61.1, 26)
          line(27.8, 47, 72.2, 47)
          line(16.6, 68, 83.4, 68)
        },
        symbol("thought", "Thought bubble") {
          cloud(18f, 4f, 98f, 62f)
          circle(24, 78, 6)
          circle(12, 92, 3.5)
        },
        symbol("eye", "Eye") {
          curve(5, 50, 50, 18, 95, 50)
          curve(5, 50, 50, 82, 95, 50)
          circle(50, 50, 16)
          circle(50, 50, 6)
        },
        symbol("face", "Face") {
          circle(50, 50, 42)
          circle(36, 40, 4)
          circle(64, 40, 4)
          arc(50, 52, 22, 30, 120)
        },
        symbol("cycle", "Cycle") {
          for (start in listOf(-80, 10, 100, 190)) arc(50, 50, 36, start, 70, arrow = true)
        },
        symbol("scatter", "Scatter plot") {
          open(8, 6, 8, 92, 96, 92)
          for ((x, y) in listOf(20 to 76, 30 to 70, 36 to 60, 46 to 62, 55 to 48, 62 to 50, 72 to 36, 82 to 30)) circle(x, y, 2.5)
          line(14, 82, 90, 22)
        },
      ),
    )

  private val economics =
    ShapePack(
      "economics",
      "Economics",
      listOf(
        symbol("supply-demand", "Supply & demand") {
          axes()
          line(20, 18, 88, 80)
          line(20, 80, 88, 18)
        },
        stretch("axes", "Axes") { axes(0, 100, 0, 100) },
        symbol("demand", "Demand curve") {
          axes()
          curve(20, 14, 28, 44, 42, 64, 62, 76, 90, 82)
        },
        symbol("indifference", "Indifference curves") {
          axes()
          curve(18, 20, 24, 50, 38, 64, 60, 72)
          curve(28, 14, 34, 44, 50, 58, 76, 66)
          curve(40, 10, 46, 36, 62, 50, 90, 58)
        },
        symbol("ppf", "Production frontier") {
          axes()
          arc(10, 90, 68, -90, 90)
        },
        symbol("cost-curves", "Cost curves") {
          axes()
          curve(18, 30, 36, 62, 56, 68, 76, 52, 88, 22)
          curve(20, 18, 40, 52, 64, 58, 90, 46)
        },
        symbol("lorenz", "Lorenz curve") {
          rect(10, 10, 90, 90)
          line(10, 90, 90, 10)
          curve(10, 90, 50, 80, 75, 60, 90, 10)
        },
        symbol("circular-flow", "Circular flow") {
          rect(2, 38, 28, 62)
          rect(72, 38, 98, 62)
          arc(50, 50, 33, 200, 140, arrow = true)
          arc(50, 50, 33, 20, 140, arrow = true)
        },
        symbol("business-cycle", "Business cycle") {
          axes()
          curve(List(19) { i ->
            val x = 14f + i * 4.5f
            Pt(x, 70f - (x - 14f) * 0.45f - 14f * sin((x - 14f) / 12f))
          })
          line(14, 70, 92, 35)
        },
        symbol("laffer", "Laffer curve") {
          axes()
          curve(12, 88, 28, 40, 48, 22, 68, 40, 88, 88)
        },
      ),
    )

  private val design =
    ShapePack(
      "design",
      "Design & Art",
      listOf(
        symbol("colour-wheel", "Colour wheel") {
          circle(50, 50, 42)
          circle(50, 50, 16)
          for (i in 0 until 6) {
            val a = i * PI / 3 - PI / 2
            line(50 + 16 * cos(a), 50 + 16 * sin(a), 50 + 42 * cos(a), 50 + 42 * sin(a))
          }
        },
        stretch("thirds", "Rule of thirds") {
          rect(0, 0, 100, 100)
          line(33.3, 0, 33.3, 100)
          line(66.7, 0, 66.7, 100)
          line(0, 33.3, 100, 33.3)
          line(0, 66.7, 100, 66.7)
        },
        symbol("golden-spiral", "Golden spiral") {
          rect(0, 0, 100, 61.8)
          line(61.8, 0, 61.8, 61.8)
          line(61.8, 38.2, 100, 38.2)
          line(76.4, 38.2, 76.4, 61.8)
          line(61.8, 47.2, 76.4, 47.2)
          arc(61.8, 61.8, 61.8, 180, 90)
          arc(61.8, 38.2, 38.2, 270, 90)
          arc(76.4, 38.2, 23.6, 0, 90)
          arc(76.4, 47.2, 14.6, 90, 90)
        },
        stretch("perspective", "Perspective") {
          line(0, 40, 100, 40)
          for ((x, y) in listOf(0 to 100, 30 to 100, 70 to 100, 100 to 100, 0 to 0, 100 to 0)) line(50, 40, x, y)
        },
        symbol("brush", "Paintbrush") {
          poly(88, 6, 94, 12, 60, 46, 54, 40)
          poly(54, 40, 60, 46, 52, 54, 46, 48)
          curve(46, 48, 30, 60, 14, 88, 40, 72, 52, 54)
        },
        along("pencil", "Pencil") {
          rect(0, -8, 75, 8)
          line(12, -8, 12, 8)
          open(75, -8, 100, 0, 75, 8)
          line(91, -3, 91, 3)
        },
        symbol("palette", "Palette") {
          curve(50, 10, 84, 18, 96, 48, 82, 78, 56, 86, 50, 72, 38, 68, 26, 82, 8, 62, 10, 30, 50, 10)
          circle(36, 30, 6)
          circle(58, 24, 6)
          circle(78, 40, 6)
          circle(76, 62, 6)
        },
        stretch("frame", "Picture frame") {
          rect(0, 0, 100, 100)
          rect(10, 10, 90, 90)
          line(0, 0, 10, 10)
          line(100, 0, 90, 10)
          line(100, 100, 90, 90)
          line(0, 100, 10, 90)
        },
        symbol("cube", "Cube") {
          rect(14, 36, 64, 86)
          open(14, 36, 36, 14, 86, 14, 86, 64, 64, 86)
          line(64, 36, 86, 14)
        },
        symbol("easel", "Easel") {
          line(50, 4, 20, 96)
          line(50, 4, 80, 96)
          line(50, 4, 50, 92)
          rect(24, 18, 76, 60)
          line(18, 64, 82, 64)
        },
      ),
    )

  /** Every subject, in the order the "More shapes" window lists them. */
  val all: List<ShapePack> = listOf(social, business, computing, law, health, mechanical, civil, electrical, psychology, economics, design)

  private val byId: Map<String, PackShape> by lazy { all.flatMap { it.shapes }.associateBy { it.id } }

  fun shape(id: String): PackShape? = byId[id]
}
