package com.tali.notes.geometry

import com.tali.notes.model.PolyShape
import com.tali.notes.model.ShapeKind
import kotlin.math.PI
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Dragging a shape's dots resizes it and keeps it the same kind of shape. */
class ShapeResizeTest {
  private fun Pt.near(o: Pt, tol: Float = 0.05f) = dist(o) < tol

  /** Every corner of a 4-gon is a right angle. */
  private fun isRectangle(p: List<Pt>): Boolean =
    (0 until 4).all { i ->
      val a = p[(i + 3) % 4] - p[i]
      val b = p[(i + 1) % 4] - p[i]
      abs(a.dot(b)) < 1e-2f * a.length() * b.length()
    }

  @Test
  fun rectangleSideStretchesOneWay() {
    val r = ShapeKind.RECTANGLE.create(Pt(0f, 0f), Pt(200f, 100f)) as PolyShape
    val right = r.handles().indexOfFirst { it.near(Pt(200f, 50f)) }
    val out = r.withHandle(right, Pt(260f, 70f)) as PolyShape
    assertTrue(isRectangle(out.points))
    assertEquals(260f, out.points.maxOf { it.x }, 0.05f)
    assertEquals(100f, out.points.maxOf { it.y } - out.points.minOf { it.y }, 0.05f)
  }

  @Test
  fun turnedRectangleSideKeepsItARectangle() {
    val r = (ShapeKind.RECTANGLE.create(Pt(0f, 0f), Pt(200f, 100f)) as PolyShape).transformed(com.tali.notes.geometry.Affine.rotateAround(Pt(100f, 50f), 0.5f)) as PolyShape
    val out = r.withHandle(1, r.handles()[1] + Pt(30f, 15f)) as PolyShape
    assertTrue(isRectangle(out.points))
  }

  @Test
  fun anOlderPolygonsCornerMovesOnItsOwn() {
    // A polygon without a form (drawn before forms, or recognised from her drawing) keeps free corners.
    val d = (ShapeKind.DIAMOND.create(Pt(0f, 0f), Pt(100f, 60f)) as PolyShape).copy(form = null)
    val out = d.withHandle(0, Pt(70f, -60f)) as PolyShape
    assertTrue(out.points[0].near(Pt(70f, -60f)))
    for (i in 1 until d.points.size) assertTrue(out.points[i].near(d.points[i]))
  }

  @Test
  fun triangleCornersMoveOneAtATime() {
    val t = ShapeKind.TRIANGLE.create(Pt(0f, 0f), Pt(100f, 80f)) as com.tali.notes.model.TriangleShape
    val out = t.withHandle(1, Pt(150f, 120f)) as com.tali.notes.model.TriangleShape // bottom-right corner
    assertEquals(t.a, out.a)
    assertEquals(t.c, out.c)
  }

  @Test
  fun aStarKeepsItsForm() {
    val s = ShapeKind.STAR.create(Pt(0f, 0f), Pt(100f, 100f)) as PolyShape
    assertTrue(s.handles().isEmpty())
    assertEquals(s, s.withHandle(0, s.points[0] + Pt(10f, -50f)))
    assertTrue(abs(PI) > 0)
  }
}
