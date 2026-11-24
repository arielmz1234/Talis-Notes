package com.tali.notes.model

import com.tali.notes.geometry.Pt
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShapeOutlineTest {
  private val square = PolyShape(listOf(Pt(0f, 0f), Pt(100f, 0f), Pt(100f, 100f), Pt(0f, 100f)), closed = true)

  @Test fun aClosedShapeIsOneClosedPiece() {
    val pieces = ShapeOutline.pieces(square, 10f, LineStyle.SOLID)
    assertEquals(1, pieces.size)
    assertTrue(pieces[0].closed)
    assertEquals(4, pieces[0].points.size)
  }

  @Test fun aSquaresCornersAreSquare() {
    val b = ShapeOutline.bounds(ShapeOutline.pieces(square, 10f, LineStyle.SOLID), 10f)!!
    assertEquals(-5f, b.left, 1e-3f)
    assertEquals(105f, b.right, 1e-3f)
    // The outer corner is the mitred point, right out at the corner of the bounds.
    val tip = ShapeOutline.miterTip(Pt(0f, 0f), Pt(100f, 0f), Pt(100f, 100f), 5f)!!
    assertEquals(105f, tip.x, 1e-3f)
    assertEquals(-5f, tip.y, 1e-3f)
  }

  @Test fun aSharpCornerReachesPastItsRoundTip() {
    val tri = TriangleShape(Pt(0f, 100f), Pt(50f, 0f), Pt(100f, 100f))
    val b = ShapeOutline.bounds(ShapeOutline.pieces(tri, 10f, LineStyle.SOLID), 10f)!!
    // The top corner's miter: half the width over sin(half its angle).
    val half = Math.toDegrees(kotlin.math.atan(0.5)).toFloat()
    assertEquals(-5f / kotlin.math.sin(Math.toRadians(half.toDouble())).toFloat(), b.top, 0.05f)
    assertTrue(b.top < -5f)
  }

  @Test fun aLineStaysOpenAndDashesAreOpen() {
    val line = ShapeOutline.pieces(LineShape(Pt(0f, 0f), Pt(100f, 0f)), 2f, LineStyle.SOLID)
    assertFalse(line.single().closed)
    assertTrue(ShapeOutline.pieces(square, 2f, LineStyle.LONG_DASH).all { !it.closed })
  }

  @Test fun aVeryTightCornerIsCutFlat() {
    assertEquals(null, ShapeOutline.miterTip(Pt(0f, 0f), Pt(100f, 0f), Pt(0f, 5f), 5f))
    assertTrue(sqrt(2f) < ShapeOutline.MITER_LIMIT)
  }
}
