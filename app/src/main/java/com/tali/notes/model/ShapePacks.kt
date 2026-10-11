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
class PackShape(val id: String, val label: String, val mode: PackMode, private val design: ShapeGeometry? = null, val kind: ShapeKind? = null, val template: ShapeTemplate? = null) {
  private val frame: Rect by lazy { design?.let { Rect.around(it.paths(1f).flatten()) } ?: Rect(0f, 0f, 1f, 1f) }

  /** For the panel's tile. */
  val icon: ShapeGeometry
    get() = design ?: template?.icon ?: kind!!.inBox(Pt(0f, 0f), Pt(100f, 100f))

  val drawnAsLine: Boolean
    get() = kind?.drawnAsLine ?: (mode == PackMode.LINE)

  val drawnFromPoint: Boolean
    get() = kind?.drawnFromPoint ?: false

  /** For hold-to-shape: how its lines are spread, to compare with a drawn stroke (designs only). */
  val cloud: com.tali.notes.geometry.ShapeMatcher.Cloud? by lazy { (design ?: template?.icon)?.let { com.tali.notes.geometry.ShapeMatcher.cloud(it.paths(CustomShape.REFERENCE_PEN)) } }

  /** Over the box a held stroke covered; one drawn like a line runs from the stroke's [first] point to its [last]. */
  fun placeOver(box: Rect, first: Pt, last: Pt): ShapeGeometry =
    if (mode == PackMode.LINE) create(first, last) else create(Pt(box.left, box.top), Pt(box.right, box.bottom))

  /** The shape drawn from where the pen landed ([p0]) to where it is ([p1]). */
  fun create(p0: Pt, p1: Pt): ShapeGeometry {
    kind?.let { return it.create(p0, p1) }
    template?.let { return it.create(p0, p1) }
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
internal class Draw {
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

/** A shape with joints ([ShapeTemplate]): dots that change what it is (more cells, a level, a bar's height). */
private fun jointed(label: String, template: ShapeTemplate) =
  PackShape(template.id, label, if (template.mode == ShapeTemplate.Mode.LINE) PackMode.LINE else PackMode.SYMBOL, template = template)

/**
 * A cloud (for the cloud and the thought bubble): four round bumps on a flat base, fitted into the
 * box from ([l], [t]) to ([r], [b]) (drawn on a 100 × 60 grid, scaled evenly).
 */
internal fun Draw.cloud(l: Float, t: Float, r: Float, b: Float) {
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
  private val T = ShapeTemplates

  private val social =
    ShapePack(
      "social",
      "Social Sciences",
      listOf(
        symbol("person", "Person") { person(50f, 95f, 90f) },
        jointed("Group", T.group),
        jointed("Network", T.network),
        jointed("Family tree", T.familyTree),
        symbol("male", "Male") {
          circle(40, 60, 26)
          line(58, 42, 88, 12, arrow = true)
        },
        symbol("female", "Female") {
          circle(50, 36, 26)
          line(50, 62, 50, 96)
          line(34, 80, 66, 80)
        },
        jointed("Speech bubble", T.speech),
        symbol("globe", "Globe") {
          circle(50, 50, 40)
          ellipse(50, 50, 17, 40)
          line(10, 50, 90, 50)
          curve(16, 30, 50, 24, 84, 30)
          curve(16, 70, 50, 76, 84, 70)
        },
        jointed("Timeline", T.timeline),
        jointed("Venn diagram", T.venn),
      ),
    )

  private val business =
    ShapePack(
      "business",
      "Business & Management",
      listOf(
        jointed("Org chart", T.orgChart),
        jointed("Bar chart", T.barChart),
        jointed("Pie chart", T.pieChart),
        jointed("Growth chart", T.growth),
        jointed("Funnel", T.funnel),
        jointed("SWOT grid", T.swot),
        jointed("Gantt chart", T.gantt),
        jointed("Target", T.target),
        jointed("Coins", T.coins),
        jointed("Document", T.document),
      ),
    )

  private val computing =
    ShapePack(
      "computing",
      "Computer Science & IT",
      listOf(
        jointed("Database", T.database),
        jointed("Start / end", T.terminal),
        jointed("Subroutine", T.subroutine),
        jointed("Binary tree", T.binaryTree),
        jointed("Directed graph", T.graph),
        jointed("Array", T.array),
        jointed("Linked list", T.linkedList),
        jointed("Stack", T.stack),
        jointed("Computer", T.computer),
        symbol("cloud", "Cloud") { cloud(0f, 20f, 100f, 80f) },
      ),
    )

  private val law =
    ShapePack(
      "law",
      "Law",
      listOf(
        jointed("Scales of justice", T.scales),
        symbol("gavel", "Gavel") {
          poly(18, 22, 42, 6, 58, 30, 34, 46)
          line(46, 38, 86, 76)
          rect(54, 84, 96, 94)
        },
        jointed("Courthouse", T.courthouse),
        jointed("Column", T.column),
        jointed("Contract", T.contract),
        symbol("shield", "Shield") { curve(50, 8, 84, 16, 82, 55, 50, 92, 18, 55, 16, 16, 50, 8) },
        jointed("Court hierarchy", T.courtLevels),
        jointed("Two parties", T.parties),
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
        jointed("Medical cross", T.medCross),
        symbol("heart", "Heart") { curve(50, 90, 16, 56, 8, 30, 25, 12, 42, 14, 50, 28, 58, 14, 75, 12, 92, 30, 84, 56, 50, 90) },
        jointed("Heartbeat", T.heartbeat),
        symbol("capsule", "Capsule") {
          line(25, 35, 75, 35)
          line(25, 65, 75, 65)
          arc(25, 50, 15, 90, 180)
          arc(75, 50, 15, -90, 180)
          line(50, 35, 50, 65)
        },
        jointed("Syringe", T.syringe),
        jointed("Thermometer", T.thermometer),
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
        jointed("IV bag", T.ivBag),
        symbol("drop", "Blood drop") { curve(50, 8, 36, 38, 28, 62, 35, 80, 50, 88, 65, 80, 72, 62, 64, 38, 50, 8) },
      ),
    )

  private val mechanical =
    ShapePack(
      "mechanical",
      "Mechanical Engineering",
      listOf(
        jointed("Gear", T.gear),
        jointed("Spring", T.spring),
        jointed("Damper", T.damper),
        jointed("Bearing", T.bearing),
        jointed("Pulley", T.pulley),
        jointed("Lever", T.lever),
        jointed("Piston", T.piston),
        jointed("Mass on ground", T.mass),
        jointed("Shaft", T.shaft),
        jointed("Bolt", T.bolt),
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
        jointed("I-beam section", T.iBeam),
        jointed("Truss", T.truss),
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
        jointed("Resistor", T.resistor),
        jointed("Capacitor", T.capacitor),
        jointed("Inductor", T.inductor),
        jointed("Diode", T.diode),
        jointed("Battery", T.battery),
        jointed("AC source", T.acSource),
        jointed("Switch", T.switch),
        jointed("Lamp", T.lamp),
        symbol("ground", "Ground") {
          line(50, 5, 50, 45)
          line(20, 45, 80, 45)
          line(30, 57, 70, 57)
          line(40, 69, 60, 69)
        },
        jointed("AND gate", T.andGate),
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
        jointed("Neuron", T.neuron),
        jointed("Bell curve", T.bellCurve),
        jointed("Maslow pyramid", T.maslow),
        jointed("Thought bubble", T.thought),
        jointed("Eye", T.eye),
        jointed("Face", T.face),
        jointed("Cycle", T.cycle),
        jointed("Scatter plot", T.scatter),
      ),
    )

  private val economics =
    ShapePack(
      "economics",
      "Economics",
      listOf(
        jointed("Supply & demand", T.supplyDemand),
        jointed("Axes", T.axes),
        jointed("Demand curve", T.demand),
        jointed("Indifference curves", T.indifference),
        jointed("Production frontier", T.ppf),
        jointed("Cost curves", T.costCurves),
        jointed("Lorenz curve", T.lorenz),
        symbol("circular-flow", "Circular flow") {
          rect(2, 38, 28, 62)
          rect(72, 38, 98, 62)
          arc(50, 50, 33, 200, 140, arrow = true)
          arc(50, 50, 33, 20, 140, arrow = true)
        },
        jointed("Business cycle", T.businessCycle),
        jointed("Laffer curve", T.laffer),
      ),
    )

  private val design =
    ShapePack(
      "design",
      "Design & Art",
      listOf(
        jointed("Colour wheel", T.colourWheel),
        jointed("Rule of thirds", T.thirds),
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
        jointed("Perspective", T.perspective),
        symbol("brush", "Paintbrush") {
          poly(88, 6, 94, 12, 60, 46, 54, 40)
          poly(54, 40, 60, 46, 52, 54, 46, 48)
          curve(46, 48, 30, 60, 14, 88, 40, 72, 52, 54)
        },
        jointed("Pencil", T.pencil),
        symbol("palette", "Palette") {
          curve(50, 10, 84, 18, 96, 48, 82, 78, 56, 86, 50, 72, 38, 68, 26, 82, 8, 62, 10, 30, 50, 10)
          circle(36, 30, 6)
          circle(58, 24, 6)
          circle(78, 40, 6)
          circle(76, 62, 6)
        },
        jointed("Picture frame", T.frame),
        jointed("Cube", T.cube),
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
