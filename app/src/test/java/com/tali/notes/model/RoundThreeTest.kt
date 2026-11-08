package com.tali.notes.model

import com.tali.notes.geometry.Pt
import com.tali.notes.geometry.ShapeRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundThreeTest {
  private val rect = PolyShape(listOf(Pt(0f, 0f), Pt(100f, 0f), Pt(100f, 60f), Pt(0f, 60f)), closed = true)

  private fun box(p: PolyShape) = Pair(p.points.maxOf { it.x } - p.points.minOf { it.x }, p.points.maxOf { it.y } - p.points.minOf { it.y })

  @Test
  fun rectangleHasSideDots() {
    // Only the side dots: its corners only resized it, which is the resize chip's job now.
    assertEquals(4, rect.handles().size)
    assertEquals(Pt(100f, 30f), rect.handles()[1])
  }

  @Test
  fun sideDotStretchesOneWayOnly() {
    val out = rect.withHandle(1, Pt(150f, 10f)) as PolyShape
    val (w, h) = box(out)
    assertEquals(150f, w, 0.01f)
    assertEquals(60f, h, 0.01f)
    assertEquals(0f, out.points.minOf { it.x }, 0.01f)
  }

  @Test
  fun sideDotSnapsToSquare() {
    val out = rect.withHandle(2, Pt(50f, 98f)) as PolyShape
    val (w, h) = box(out)
    assertEquals(w, h, 0.01f)
  }

  @Test
  fun squareCanBecomeARectangle() {
    val square = PolyShape(listOf(Pt(0f, 0f), Pt(80f, 0f), Pt(80f, 80f), Pt(0f, 80f)), closed = true)
    val out = square.withHandle(1, Pt(140f, 40f)) as PolyShape
    val (w, h) = box(out)
    assertEquals(140f, w, 0.01f)
    assertEquals(80f, h, 0.01f)
  }

  @Test
  fun aCircleHasOneRadiusDotAndHandDrawnLinesNone() {
    val circle = EllipseShape(Pt(0f, 0f), 40f, 40f)
    assertEquals(1, circle.handles().size)
    // Its dot sets the radius around the same centre and goes round with the pen.
    val bigger = circle.withHandle(0, Pt(0f, 70f)) as EllipseShape
    assertEquals(Pt(0f, 0f), bigger.center)
    assertEquals(70f, bigger.rx, 0.01f)
    assertEquals(70f, bigger.ry, 0.01f)
    assertEquals(Pt(0f, 70f).x, bigger.handles()[0].x, 0.01f)
    assertEquals(Pt(0f, 70f).y, bigger.handles()[0].y, 0.01f)
    assertEquals(4, EllipseShape(Pt(0f, 0f), 60f, 40f).handles().size)
    assertTrue(PolyShape(listOf(Pt(0f, 0f), Pt(10f, 5f), Pt(20f, 0f)), closed = false, smooth = true).handles().isEmpty())
  }

  @Test
  fun linesSnapOnlyToFortyFives() {
    val near30 = ShapeRecognizer.snappedLine(Pt(0f, 0f), Pt.polar(100f, Math.toRadians(31.0).toFloat()))
    assertEquals(31.0, Math.toDegrees((near30.b - near30.a).angle().toDouble()), 0.01)
    val near45 = ShapeRecognizer.snappedLine(Pt(0f, 0f), Pt.polar(100f, Math.toRadians(41.0).toFloat()))
    assertEquals(45.0, Math.toDegrees((near45.b - near45.a).angle().toDouble()), 0.01)
  }

  @Test
  fun threeThicknessesAreKeptApart() {
    val pen = PenSlot("a", PenType.FINELINER, 0, 2.5f)
    val sizes = pen.presetSizes
    assertEquals(3, sizes.size)
    assertTrue(sizes[0] < sizes[1] && sizes[1] < sizes[2])
    val thick = pen.pickSize(2)
    assertEquals(sizes[2], thick.size, 0f)
    val changed = thick.withSize(9f)
    assertEquals(listOf(sizes[0], sizes[1], 9f), changed.presetSizes)
    assertEquals(sizes[0], changed.pickSize(0).size, 0f)
  }

  @Test
  fun rectangleToolSnapsToASquare() {
    val g = ShapeKind.RECTANGLE.create(Pt(0f, 0f), Pt(100f, 97f)) as PolyShape
    assertTrue(g.isSquare())
    assertTrue(!(ShapeKind.RECTANGLE.create(Pt(0f, 0f), Pt(100f, 70f)) as PolyShape).isSquare())
    assertTrue(ShapeKind.SQUARE !in ShapeKind.offered)
  }

  @Test
  fun ellipseSnapsRoundAndKeepsItsDots() {
    val e = ShapeKind.ELLIPSE.create(Pt(0f, 0f), Pt(100f, 60f)) as EllipseShape
    val round = e.withHandle(1, Pt(50f, 30f - 49f)) as EllipseShape // pull the top dot almost to a circle
    assertTrue(round.round)
    assertEquals(4, round.handles().size)
    val out = round.withHandle(0, Pt(130f, 30f)) as EllipseShape
    assertTrue(!out.round)
  }
}
