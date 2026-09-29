package com.tali.notes.geometry

import com.tali.notes.model.CustomShape
import com.tali.notes.model.EllipseShape
import com.tali.notes.model.GroupShape
import com.tali.notes.model.LineShape
import com.tali.notes.model.PolyShape
import com.tali.notes.model.ShapeGeometry
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapeMatcherTest {
  private val rnd = Random(11)

  private fun wobbly(vararg corners: Pt, jitter: Float = 3f): List<Pt> {
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

  private fun circle(cx: Float, cy: Float, r: Float, turns: Float = 1.03f) =
    List(140) { i ->
      val t = (2 * PI * turns * i / 139).toFloat()
      Pt(cx + r * cos(t) + rnd.nextFloat() * 3f - 1.5f, cy + r * sin(t) + rnd.nextFloat() * 3f - 1.5f)
    }

  private fun custom(name: String, g: ShapeGeometry) = CustomShape(name, name, CustomShape.normalized(g)!!)

  // A house: square walls with a triangular roof.
  private val house =
    custom("house", GroupShape(listOf(PolyShape(listOf(Pt(0f, 60f), Pt(100f, 60f), Pt(100f, 160f), Pt(0f, 160f)), true), PolyShape(listOf(Pt(0f, 60f), Pt(50f, 0f), Pt(100f, 60f)), false))))

  // An I-beam cross-section outline.
  private val beam =
    custom(
      "beam",
      PolyShape(
        listOf(Pt(0f, 0f), Pt(100f, 0f), Pt(100f, 18f), Pt(60f, 18f), Pt(60f, 102f), Pt(100f, 102f), Pt(100f, 120f), Pt(0f, 120f), Pt(0f, 102f), Pt(40f, 102f), Pt(40f, 18f), Pt(0f, 18f)),
        true,
      ),
    )

  // A circle with a cross through it (like a pin joint symbol).
  private val crossed = custom("crossed", GroupShape(listOf(EllipseShape(Pt(50f, 50f), 50f, 50f), LineShape(Pt(0f, 50f), Pt(100f, 50f)), LineShape(Pt(50f, 0f), Pt(50f, 100f)))))

  private val all = listOf(house, beam, crossed)

  private fun d(points: List<Pt>, shape: CustomShape) = ShapeMatcher.distance(ShapeMatcher.cloud(listOf(points))!!, shape.cloud!!)

  @Test
  fun houseDrawnInOneStrokeMatchesHouse() {
    // Walls first, then up over the roof, at a different size and position.
    val stroke = wobbly(Pt(300f, 400f), Pt(300f, 600f), Pt(500f, 600f), Pt(500f, 400f), Pt(300f, 400f), Pt(400f, 280f), Pt(500f, 400f))
    println("house vs house " + d(stroke, house) + ", beam " + d(stroke, beam) + ", crossed " + d(stroke, crossed))
    assertEquals("house", ShapeRecognizer.bestCustom(stroke, all)?.id)
  }

  @Test
  fun sloppyHouseStillMatches() {
    // Taller roof, leaning walls, and the roof drawn off-centre.
    val stroke = wobbly(Pt(310f, 390f), Pt(296f, 600f), Pt(508f, 596f), Pt(492f, 404f), Pt(310f, 390f), Pt(430f, 240f), Pt(492f, 404f), jitter = 6f)
    println("sloppy house vs house " + d(stroke, house))
    assertEquals("house", ShapeRecognizer.bestCustom(stroke, all)?.id)
  }

  @Test
  fun beamMatchesBeam() {
    val c = listOf(Pt(0f, 0f), Pt(100f, 0f), Pt(100f, 18f), Pt(60f, 18f), Pt(60f, 102f), Pt(100f, 102f), Pt(100f, 120f), Pt(0f, 120f), Pt(0f, 102f), Pt(40f, 102f), Pt(40f, 18f), Pt(0f, 18f), Pt(0f, 0f))
    val stroke = wobbly(*c.map { Pt(200f + it.x * 1.6f, 100f + it.y * 1.6f) }.toTypedArray(), jitter = 4f)
    println("beam vs beam " + d(stroke, beam) + ", house " + d(stroke, house) + ", crossed " + d(stroke, crossed))
    assertEquals("beam", ShapeRecognizer.bestCustom(stroke, all)?.id)
  }

  @Test
  fun plainCircleDoesNotMatchCrossedCircle() {
    val stroke = circle(300f, 300f, 90f)
    println("circle vs crossed " + d(stroke, crossed) + ", house " + d(stroke, house))
    assertNull(ShapeRecognizer.bestCustom(stroke, all))
    assertTrue(ShapeRecognizer.recognize(stroke, all) is EllipseShape)
  }

  @Test
  fun plainSquareAndTriangleDoNotMatchHouse() {
    val square = wobbly(Pt(0f, 0f), Pt(200f, 0f), Pt(200f, 200f), Pt(0f, 200f), Pt(0f, 0f))
    val triangle = wobbly(Pt(0f, 200f), Pt(100f, 20f), Pt(200f, 200f), Pt(0f, 200f))
    println("square vs house " + d(square, house) + ", beam " + d(square, beam) + "; triangle vs house " + d(triangle, house))
    assertNull(ShapeRecognizer.bestCustom(square, all))
    assertNull(ShapeRecognizer.bestCustom(triangle, all))
  }

  @Test
  fun crossedCircleDrawnInOneStrokeMatches() {
    // Around the circle, then across and down through the middle with connecting travel.
    val ring = circle(300f, 300f, 100f, turns = 1f)
    val stroke = ring + wobbly(Pt(400f, 300f), Pt(200f, 300f), Pt(300f, 300f), Pt(300f, 200f), Pt(300f, 400f))
    println("crossed stroke vs crossed " + d(stroke, crossed))
    assertEquals("crossed", ShapeRecognizer.bestCustom(stroke, all)?.id)
  }

  @Test
  fun straightLineStaysALine() {
    val line = wobbly(Pt(0f, 0f), Pt(300f, 4f))
    assertTrue(ShapeRecognizer.recognize(line, all) is LineShape)
  }

  @Test
  fun placedShapeCoversTheStroke() {
    val stroke = wobbly(Pt(300f, 400f), Pt(300f, 600f), Pt(500f, 600f), Pt(500f, 400f), Pt(300f, 400f), Pt(400f, 280f), Pt(500f, 400f))
    val g = ShapeRecognizer.recognize(stroke, all)
    assertNotNull(g)
    val box = Rect.around(g!!.paths(2f).flatten())!!
    assertEquals(400f, box.center.x, 12f)
    assertEquals(200f, box.width, 20f)
    assertEquals(320f, box.height, 30f)
  }
}
