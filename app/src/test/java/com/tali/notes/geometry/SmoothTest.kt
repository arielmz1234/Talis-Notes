package com.tali.notes.geometry

import com.tali.notes.model.CustomShape
import com.tali.notes.model.GroupShape
import com.tali.notes.model.PolyShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmoothTest {
  @Test
  fun aCurveThroughThePointsIsDenseAndKeepsItsEnds() {
    val pts = listOf(Pt(0f, 0f), Pt(30f, 20f), Pt(60f, 25f), Pt(90f, 20f), Pt(120f, 0f))
    val out = Geo.smooth(pts)
    assertEquals(pts.first(), out.first())
    assertTrue(out.last().dist(pts.last()) < 1e-3f)
    assertTrue(out.size > pts.size * 5)
    // It passes through every point it was given.
    pts.forEach { p -> assertTrue(out.any { it.dist(p) < 1e-3f }) }
  }

  @Test
  fun aSharpCornerStaysSharp() {
    // An L: the curve must not round off the corner.
    val pts = listOf(Pt(0f, 0f), Pt(50f, 0f), Pt(100f, 0f), Pt(100f, 50f), Pt(100f, 100f))
    val out = Geo.smooth(pts)
    assertTrue(out.any { it.dist(Pt(100f, 0f)) < 1e-3f })
    // Nothing bulges past the corner.
    assertTrue(out.all { it.x <= 100.01f && it.y >= -0.01f })
  }

  @Test
  fun savedInkLinesBecomeSmoothInsideGroups() {
    val ink = PolyShape(listOf(Pt(0f, 0f), Pt(5f, 5f), Pt(10f, 0f)), closed = false)
    val box = PolyShape(listOf(Pt(0f, 0f), Pt(5f, 0f), Pt(5f, 5f)), closed = true)
    val g = CustomShape.smoothInk(GroupShape(listOf(ink, box))) as GroupShape
    assertTrue((g.parts[0] as PolyShape).smooth)
    assertTrue(!(g.parts[1] as PolyShape).smooth)
  }
}
