package com.tali.notes.geometry

import com.tali.notes.model.PolyShape
import com.tali.notes.model.TriangleShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class TriangleShapeTest {
  private val t = TriangleShape(Pt(0f, 0f), Pt(200f, 0f), Pt(100f, -150f))

  private fun anglesOf(g: TriangleShape) = g.angles.map { Math.round(it) }

  @Test
  fun aCornerMovesOnItsOwn() {
    val moved = t.withHandle(2, Pt(60f, -170f)) as TriangleShape
    assertEquals(t.a, moved.a)
    assertEquals(t.b, moved.b)
    assertTrue(moved.c.dist(Pt(60f, -170f)) < 20f)
  }

  @Test
  fun itMovesAWholeDegreeAtATime() {
    for (x in listOf(30f, 70f, 123f, 151f)) {
      val g = t.withHandle(2, Pt(x, -113f)) as TriangleShape
      g.angles.forEach { a -> assertEquals("angle $a from x=$x", 0f, abs(a - Math.round(a)), 0.05f) }
      assertEquals(180, anglesOf(g).sum())
    }
  }

  @Test
  fun anglesLockOnToTheUsualOnes() {
    assertEquals(45f, TriangleShape.lockAngle(43.2f))
    assertEquals(30f, TriangleShape.lockAngle(31.9f))
    assertEquals(40f, TriangleShape.lockAngle(40.7f))
    // Between them it just moves by whole degrees.
    assertEquals(37f, TriangleShape.lockAngle(37.3f))
    assertEquals(52f, TriangleShape.lockAngle(51.8f))
  }

  @Test
  fun nearbyPositionsGiveDifferentTriangles() {
    // Small moves still change it: it isn't stuck to a coarse grid.
    val seen = (0 until 12).map { k -> anglesOf(t.withHandle(2, Pt(140f + k * 2f, -120f)) as TriangleShape) }.toSet()
    assertTrue("only ${seen.size} different triangles", seen.size >= 6)
  }

  @Test
  fun nearlyRightAngleBecomesExact() {
    // Just off a right angle at the first corner.
    val g = t.withHandle(2, Pt(6f, -120f)) as TriangleShape
    assertEquals(90, anglesOf(g)[0])
  }

  @Test
  fun nearlyRightAngleAtTheMovingCornerBecomesExact() {
    // On (almost) the circle over the base: a right angle at the corner being dragged.
    val g = t.withHandle(2, Pt(100f + 99f * kotlin.math.cos(2.2f), -99f * kotlin.math.sin(2.2f))) as TriangleShape
    assertEquals(90, anglesOf(g)[2])
  }

  @Test
  fun nearlyIsoscelesBecomesExact() {
    val g = t.withHandle(2, Pt(104f, -90f)) as TriangleShape
    val a = anglesOf(g)
    assertEquals(a[0], a[1])
  }

  @Test
  fun oldTrianglesBecomeTriangleShapes() {
    val old = PolyShape(listOf(Pt(0f, 0f), Pt(10f, 0f), Pt(0f, 10f)), true)
    assertTrue(ShapeRecognizer.asTriangle(old) is TriangleShape)
    val square = PolyShape(listOf(Pt(0f, 0f), Pt(10f, 0f), Pt(10f, 10f), Pt(0f, 10f)), true)
    assertEquals(square, ShapeRecognizer.asTriangle(square))
  }
}
