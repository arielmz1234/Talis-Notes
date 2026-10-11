package com.tali.notes.model

import com.tali.notes.data.ItemCodec
import com.tali.notes.geometry.Pt
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The basic shapes keep their form (only the dots that make sense), and the curve bends anywhere. */
class ShapeFormsTest {
  private val p0 = Pt(0f, 0f)
  private val p1 = Pt(200f, 120f)

  @Test
  fun pentagonHexagonAndStarHaveNoDots() {
    for (k in listOf(ShapeKind.PENTAGON, ShapeKind.HEXAGON, ShapeKind.STAR)) {
      val g = k.create(p0, p1)
      assertTrue(k.name, g.handles().isEmpty())
      assertEquals(k.name, g, g.withHandle(0, Pt(500f, 500f)))
    }
  }

  @Test
  fun aDiamondStretchesEvenlyAndSnapsSquare() {
    val d = ShapeKind.DIAMOND.create(p0, p1) as PolyShape
    assertEquals(2, d.handles().size)
    val wider = d.withHandle(0, Pt(260f, 60f)) as PolyShape
    // Still a diamond: left and right the same distance from the middle, top and bottom unmoved.
    val c = Pt(100f, 60f)
    assertEquals(c.x - wider.points[3].x, wider.points[1].x - c.x, 0.01f)
    assertEquals(160f, wider.points[1].x - c.x, 0.01f)
    assertEquals(d.points[0], wider.points[0])
    // Close to square: snaps.
    val square = d.withHandle(1, Pt(100f, 60f + 98f)) as PolyShape
    assertEquals(abs(square.points[1].x - c.x), abs(square.points[2].y - c.y), 0.01f)
  }

  @Test
  fun aParallelogramsDotSetsItsSlantAndATrapezoidsItsTop() {
    val par = ShapeKind.PARALLELOGRAM.create(p0, p1) as PolyShape
    assertEquals(1, par.handles().size)
    val leaned = par.withHandle(0, Pt(100f, 0f)) as PolyShape
    // The bottom stays, the top slides as one: still parallel sides of equal length.
    assertEquals(par.points[2], leaned.points[2])
    assertEquals(par.points[3], leaned.points[3])
    assertEquals(leaned.points[1].x - leaned.points[0].x, leaned.points[2].x - leaned.points[3].x, 0.01f)
    val trap = ShapeKind.TRAPEZOID.create(p0, p1) as PolyShape
    val narrow = trap.withHandle(0, Pt(70f, 0f)) as PolyShape
    assertEquals(70f, narrow.points[0].x, 0.01f)
    assertEquals(130f, narrow.points[1].x, 0.01f)
  }

  @Test
  fun aCurveHasDotsAllAlongAndEachBendsIt() {
    val c = ShapeKind.CURVE.create(Pt(0f, 100f), Pt(300f, 100f)) as CurveShape
    assertEquals(CURVE_DOTS, c.handles().size)
    assertEquals(Pt(0f, 100f), c.points.first())
    assertEquals(Pt(300f, 100f), c.points.last())
    val bent = c.withHandle(1, Pt(75f, 180f)) as CurveShape
    assertEquals(Pt(75f, 180f), bent.points[1])
    // The curve passes through its dots.
    val path = bent.paths(1f).first()
    assertTrue(path.any { it.dist(Pt(75f, 180f)) < 1f })
  }

  @Test
  fun formsAndCurvesAreSavedAndReadBack() {
    for (g in listOf(ShapeKind.DIAMOND.create(p0, p1), ShapeKind.STAR.create(p0, p1), ShapeKind.CURVE.create(p0, p1))) {
      val json = ItemCodec.json.encodeToString(ShapeGeometry.serializer(), g)
      assertEquals(g, ItemCodec.json.decodeFromString(ShapeGeometry.serializer(), json))
    }
    // An older polygon (no form) still loads with its free corners.
    val old = ItemCodec.json.decodeFromString(ShapeGeometry.serializer(), """{"type":"poly","points":[{"x":0,"y":0},{"x":10,"y":0},{"x":5,"y":9}],"closed":true}""")
    assertEquals(3, old.handles().size)
  }
}
