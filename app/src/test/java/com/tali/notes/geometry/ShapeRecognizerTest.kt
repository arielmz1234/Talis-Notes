package com.tali.notes.geometry

import com.tali.notes.model.EllipseShape
import com.tali.notes.model.LineShape
import com.tali.notes.model.PolyShape
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapeRecognizerTest {
  private val rnd = Random(7)

  /** Densifies a polyline and adds hand tremor, like a real pen stroke. */
  private fun wobbly(vararg corners: Pt, jitter: Float = 2.5f): List<Pt> {
    val out = mutableListOf<Pt>()
    for (i in 0 until corners.size - 1) {
      val a = corners[i]
      val b = corners[i + 1]
      val n = (a.dist(b) / 3f).toInt().coerceAtLeast(2)
      for (k in 0 until n) {
        val p = a + (b - a) * (k.toFloat() / n)
        out += Pt(p.x + rnd.nextFloat() * jitter - jitter / 2, p.y + rnd.nextFloat() * jitter - jitter / 2)
      }
    }
    out += corners.last()
    return out
  }

  private fun wobblyEllipse(cx: Float, cy: Float, rx: Float, ry: Float, turns: Float = 1.04f): List<Pt> =
    List(160) { i ->
      val t = (2 * PI * turns * i / 159).toFloat()
      Pt(cx + rx * cos(t) + rnd.nextFloat() * 3f - 1.5f, cy + ry * sin(t) + rnd.nextFloat() * 3f - 1.5f)
    }

  @Test
  fun straightishStrokeBecomesLine() {
    val shape = ShapeRecognizer.recognize(wobbly(Pt(0f, 0f), Pt(300f, 6f)))
    assertTrue(shape is LineShape)
    val line = shape as LineShape
    // 1.1° off horizontal snaps flat.
    assertEquals(line.a.y, line.b.y, 0.01f)
    assertEquals(300f, line.length, 3f)
  }

  @Test
  fun diagonalLineKeepsAngleWhenNotNearSnap() {
    val line = ShapeRecognizer.recognize(wobbly(Pt(0f, 0f), Pt(300f, 120f))) as LineShape
    val angle = (line.b - line.a).angle() * 180 / PI
    assertEquals(21.8, angle, 1.0)
  }

  @Test
  fun shaftWithBarbsBecomesArrow() {
    val tip = Pt(300f, 0f)
    val shape = ShapeRecognizer.recognize(wobbly(Pt(0f, 0f), tip, Pt(260f, -30f), tip, Pt(260f, 30f), jitter = 1.5f))
    assertTrue("got $shape", shape is LineShape && shape.arrowEnd)
  }

  @Test
  fun lShapeBecomesOpenPolyline() {
    val shape = ShapeRecognizer.recognize(wobbly(Pt(0f, 0f), Pt(0f, 200f), Pt(250f, 200f)))
    assertTrue("got $shape", shape is PolyShape && !shape.closed && shape.points.size == 3)
  }

  @Test
  fun roundLoopBecomesCircle() {
    val shape = ShapeRecognizer.recognize(wobblyEllipse(100f, 100f, 80f, 76f))
    assertTrue("got $shape", shape is EllipseShape)
    val e = shape as EllipseShape
    assertEquals(e.rx, e.ry, 0.01f)
    assertEquals(78f, e.rx, 4f)
  }

  @Test
  fun flatLoopBecomesEllipse() {
    val e = ShapeRecognizer.recognize(wobblyEllipse(0f, 0f, 150f, 60f)) as EllipseShape
    assertEquals(150f, maxOf(e.rx, e.ry), 6f)
    assertEquals(60f, minOf(e.rx, e.ry), 6f)
  }

  @Test
  fun wobblySquareBecomesExactSquare() {
    val shape = ShapeRecognizer.recognize(wobbly(Pt(0f, 0f), Pt(200f, 4f), Pt(198f, 205f), Pt(-3f, 200f), Pt(2f, 3f)))
    assertTrue("got $shape", shape is PolyShape && shape.closed && shape.points.size == 4)
    val p = (shape as PolyShape).points
    val sides = p.indices.map { p[it].dist(p[(it + 1) % 4]) }
    sides.forEach { assertEquals(sides[0], it, 0.5f) }
    // Axis aligned after snapping.
    assertTrue(abs(p[0].y - p[1].y) < 0.5f || abs(p[0].x - p[1].x) < 0.5f)
  }

  @Test
  fun rectangleKeepsAspect() {
    val p = (ShapeRecognizer.recognize(wobbly(Pt(0f, 0f), Pt(320f, 0f), Pt(320f, 140f), Pt(0f, 140f), Pt(0f, 0f))) as PolyShape).points
    val sides = p.indices.map { p[it].dist(p[(it + 1) % 4]) }.sorted()
    assertEquals(140f, sides[0], 8f)
    assertEquals(320f, sides[3], 8f)
  }

  @Test
  fun triangleIsRecognized() {
    val shape = ShapeRecognizer.recognize(wobbly(Pt(100f, 0f), Pt(220f, 180f), Pt(-10f, 175f), Pt(100f, 0f)))
    assertTrue("got $shape", shape is PolyShape && shape.points.size == 3)
  }

  @Test
  fun rightTriangleGetsExactRightAngle() {
    val t = (ShapeRecognizer.recognize(wobbly(Pt(0f, 0f), Pt(4f, 200f), Pt(260f, 196f), Pt(0f, 0f))) as PolyShape).points
    val hasRight = t.indices.any { i -> abs(Geo.interiorAngle(t[(i + 2) % 3], t[i], t[(i + 1) % 3]) - PI / 2) < 0.01 }
    assertTrue("angles off: $t", hasRight)
  }

  @Test
  fun hexagonIsNotMistakenForCircle() {
    val corners = List(7) { i -> Pt(100f * cos(2 * PI * i / 6).toFloat(), 100f * sin(2 * PI * i / 6).toFloat()) }
    val shape = ShapeRecognizer.recognize(wobbly(*corners.toTypedArray(), jitter = 2f))
    assertTrue("got $shape", shape is PolyShape && shape.points.size == 6)
  }

  @Test
  fun starIsRecognized() {
    val corners = List(11) { i ->
      val r = if (i % 2 == 0) 120f else 50f
      val a = -PI / 2 + 2 * PI * i / 10
      Pt(r * cos(a).toFloat(), r * sin(a).toFloat())
    }
    val shape = ShapeRecognizer.recognize(wobbly(*corners.toTypedArray(), jitter = 1.5f))
    assertTrue("got $shape", shape is PolyShape && shape.points.size == 10)
  }

  @Test
  fun scribbleStaysInk() {
    val scribble = List(120) { i -> Pt(i * 3f, if (i % 2 == 0) 0f else 60f + rnd.nextFloat() * 40f) }
    assertNull(ShapeRecognizer.recognize(scribble))
  }

  @Test
  fun tinyDotStaysInk() {
    assertNull(ShapeRecognizer.recognize(listOf(Pt(0f, 0f), Pt(1f, 1f), Pt(2f, 1f))))
  }

  @Test
  fun lineHandleDragAlongDirectionKeepsAngle() {
    val line = LineShape(Pt(0f, 0f), Pt(100f, 100f))
    val extended = line.withHandle(1, Pt(201f, 199f)) as LineShape
    val angle = (extended.b - extended.a).angle()
    assertEquals(PI / 4, angle.toDouble(), 1e-4)
    assertEquals(282.8f, extended.length, 1f)
    val shortened = line.withHandle(1, Pt(40f, 40f)) as LineShape
    assertEquals(56.6f, shortened.length, 0.5f)
  }
}
