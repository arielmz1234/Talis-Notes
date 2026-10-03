package com.tali.notes.geometry

import com.tali.notes.model.EllipseShape
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
  fun rectangleCornerStretchesFromTheOppositeCorner() {
    val r = ShapeKind.RECTANGLE.create(Pt(0f, 0f), Pt(200f, 100f)) as PolyShape
    // Drag the bottom-right corner out to (300, 150).
    val out = r.withHandle(2, Pt(300f, 150f)) as PolyShape
    assertTrue(isRectangle(out.points))
    assertTrue(out.points[0].near(Pt(0f, 0f)))
    assertTrue(out.points[2].near(Pt(300f, 150f)))
  }

  @Test
  fun turnedRectangleStaysARectangle() {
    val r = (ShapeKind.RECTANGLE.create(Pt(0f, 0f), Pt(200f, 100f)) as PolyShape).transformed(Affine.rotateAround(Pt(100f, 50f), 0.6f)) as PolyShape
    val out = r.withHandle(2, r.points[2] + Pt(40f, 25f)) as PolyShape
    assertTrue(isRectangle(out.points))
    assertTrue("opposite corner stays", out.points[0].near(r.points[0], 0.1f))
  }

  @Test
  fun squareStaysSquare() {
    val s = ShapeKind.SQUARE.create(Pt(0f, 0f), Pt(100f, 100f)) as PolyShape
    val out = s.withHandle(2, Pt(180f, 150f)) as PolyShape
    assertTrue(isRectangle(out.points))
    val w = out.points[0].dist(out.points[1])
    val h = out.points[1].dist(out.points[2])
    assertEquals(w, h, 0.05f)
  }

  @Test
  fun diamondTopMakesItTallerOnly() {
    val d = ShapeKind.DIAMOND.create(Pt(0f, 0f), Pt(100f, 60f)) as PolyShape
    val out = d.withHandle(0, Pt(50f, -60f)) as PolyShape // top dot pulled up by 60
    val width = out.points.maxOf { it.x } - out.points.minOf { it.x }
    val height = out.points.maxOf { it.y } - out.points.minOf { it.y }
    assertEquals(100f, width, 0.05f)
    assertEquals(120f, height, 0.05f)
  }

  @Test
  fun triangleCornersMoveOneAtATime() {
    val t = ShapeKind.TRIANGLE.create(Pt(0f, 0f), Pt(100f, 80f)) as com.tali.notes.model.TriangleShape
    val out = t.withHandle(1, Pt(150f, 120f)) as com.tali.notes.model.TriangleShape // bottom-right corner
    assertEquals(t.a, out.a)
    assertEquals(t.c, out.c)
  }

  @Test
  fun neverTurnsInsideOut() {
    val r = ShapeKind.RECTANGLE.create(Pt(0f, 0f), Pt(200f, 100f)) as PolyShape
    val out = r.withHandle(2, Pt(-300f, -300f)) as PolyShape // dragged past the opposite corner
    assertTrue(out.points.all { it.x >= -1e-3f && it.y >= -1e-3f })
  }

  @Test
  fun circleStaysACircle() {
    val c = EllipseShape(Pt(0f, 0f), 50f, 50f)
    val out = c.withHandle(0, Pt(80f, 10f)) as EllipseShape
    assertEquals(out.rx, out.ry, 1e-3f)
    assertTrue(out.rx > 79f)
  }

  @Test
  fun starResizesEvenly() {
    val s = ShapeKind.STAR.create(Pt(0f, 0f), Pt(100f, 100f)) as PolyShape
    val out = s.withHandle(0, s.points[0] + Pt(0f, -50f)) as PolyShape
    val ratio = (out.points[0].dist(out.points[5])) / (s.points[0].dist(s.points[5]))
    val ratio2 = (out.points[2].dist(out.points[7])) / (s.points[2].dist(s.points[7]))
    assertEquals(ratio, ratio2, 1e-3f)
    assertTrue(ratio > 1.1f)
    assertTrue(abs(PI) > 0)
  }
}
