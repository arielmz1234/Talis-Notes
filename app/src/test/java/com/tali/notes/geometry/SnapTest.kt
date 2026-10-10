package com.tali.notes.geometry

import com.tali.notes.model.LineShape
import com.tali.notes.model.PolyShape
import com.tali.notes.model.snapFeatures
import com.tali.notes.model.upgraded
import com.tali.notes.model.GroupShape
import com.tali.notes.model.ShapeKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapTest {
  private fun rect(l: Float, t: Float, r: Float, b: Float) = PolyShape(listOf(Pt(l, t), Pt(r, t), Pt(r, b), Pt(l, b)), true)

  @Test
  fun aLineEndLocksOntoAnUprightLine() {
    val wall = LineShape(Pt(100f, 0f), Pt(100f, 200f)).snapFeatures()
    // A level line whose right end stops 6 units short of the wall.
    val beam = LineShape(Pt(0f, 80f), Pt(94f, 80f)).snapFeatures()
    val r = Snap.move(beam, listOf(wall), tol = 10f)
    assertEquals(6f, r.offset.x, 0.01f)
    assertEquals(0f, r.offset.y, 0.01f)
    assertTrue(r.guides.any { it is SnapGuide.Touch })
  }

  @Test
  fun anEndOnACornerBeatsAnEdge() {
    val box = rect(100f, 100f, 200f, 160f).snapFeatures()
    val line = LineShape(Pt(0f, 0f), Pt(96f, 97f)).snapFeatures()
    val r = Snap.move(line, listOf(box), tol = 10f)
    assertEquals(4f, r.offset.x, 0.01f)
    assertEquals(3f, r.offset.y, 0.01f)
  }

  @Test
  fun boxesLineUpTheirTopsAndMiddles() {
    val a = rect(0f, 0f, 100f, 50f).snapFeatures()
    // Far to the right, its top 5 above the first one's top.
    val b = rect(300f, -5f, 360f, 40f).snapFeatures()
    val r = Snap.move(b, listOf(a), tol = 8f)
    assertEquals(5f, r.offset.y, 0.01f)
    assertEquals(0f, r.offset.x, 0.01f)
    assertTrue(r.guides.single() is SnapGuide.Align)
  }

  @Test
  fun nothingNearbyMeansNoNudge() {
    val a = rect(0f, 0f, 100f, 50f).snapFeatures()
    val b = rect(300f, 120f, 360f, 170f).snapFeatures()
    val r = Snap.move(b, listOf(a), tol = 8f)
    assertEquals(Pt.ZERO, r.offset)
    assertTrue(r.guides.isEmpty())
  }

  @Test
  fun aDraggedEndLandsOnASlantedEdge() {
    val slope = LineShape(Pt(0f, 0f), Pt(100f, 100f)).snapFeatures()
    val p = Snap.point(Pt(50f, 46f), listOf(slope), tol = 8f)
    assertNotNull(p)
    assertEquals(p!!.x, p.y, 0.01f)
    assertNull(Snap.point(Pt(50f, 30f), listOf(slope), tol = 8f))
  }

  @Test
  fun aLineKeepsItsAngleWhenItStopsOnAnEdge() {
    val wall = LineShape(Pt(100f, 0f), Pt(100f, 200f)).snapFeatures()
    // Drawn level from (0, 50) and stopping just short of the wall, 4 units low.
    val p = Snap.alongLine(Pt(0f, 50f), Pt(95f, 50f), listOf(wall), tol = 8f)
    assertEquals(Pt(100f, 50f), p)
    assertEquals(Pt(100f, 200f), Snap.corner(Pt(97f, 195f), listOf(wall), tol = 8f))
  }

  @Test
  fun dimensionIsALineWithMarks() {
    val g = ShapeKind.DIMENSION.create(Pt(0f, 0f), Pt(100f, 0f))
    assertTrue(g is LineShape && g.ticks && g.arrowStart && g.arrowEnd)
    // Dots at its two ends, like any line.
    assertEquals(2, g.handles().size)
    // Old ones (three loose lines) load as the same dimension line.
    val old = GroupShape(listOf(LineShape(Pt(0f, 0f), Pt(100f, 0f), arrowStart = true, arrowEnd = true), LineShape(Pt(0f, -8f), Pt(0f, 8f)), LineShape(Pt(100f, -8f), Pt(100f, 8f))))
    assertEquals(LineShape(Pt(0f, 0f), Pt(100f, 0f), arrowStart = true, arrowEnd = true, ticks = true), old.upgraded())
  }
}
