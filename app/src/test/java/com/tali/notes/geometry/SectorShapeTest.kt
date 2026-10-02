package com.tali.notes.geometry

import com.tali.notes.model.ArcShape
import com.tali.notes.model.SectorShape
import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SectorShapeTest {
  private val tau = (2 * PI).toFloat()

  @Test
  fun halfCircleFacesTheWayYouDrag() {
    val s = SectorShape.of(Pt(0f, 0f), Pt(0f, -100f), 0.5f) // dragged straight up
    assertEquals(0.5f, s.fraction, 1e-4f)
    assertEquals(100f, s.radius, 1e-3f)
    // The middle of the curve is at the top, where she dragged.
    val mid = s.handles()[2]
    assertEquals(0f, mid.x, 1e-3f)
    assertEquals(-100f, mid.y, 1e-3f)
  }

  @Test
  fun halfCircleIsClosedThroughTheCentre() {
    val path = SectorShape.of(Pt(0f, 0f), Pt(100f, 0f), 0.5f).paths(2f).single()
    assertEquals(Pt(0f, 0f), path.first())
    assertEquals(Pt(0f, 0f), path.last())
  }

  @Test
  fun wholeCircleHasNoRadii() {
    val path = SectorShape.of(Pt(0f, 0f), Pt(100f, 0f), 1f).paths(2f).single()
    assertTrue(path.first().dist(Pt(0f, 0f)) > 50f)
  }

  @Test
  fun endDotsSnapToQuarters() {
    assertEquals(0.25f * tau, SectorShape.snapSweep(0.27f * tau), 1e-4f)
    assertEquals(0.75f * tau, SectorShape.snapSweep(0.73f * tau), 1e-4f)
    // Near a 5% step but far from a quarter: whole 5%.
    assertEquals(0.40f * tau, SectorShape.snapSweep(0.405f * tau), 1e-4f)
    // In between: left as it is.
    assertEquals(0.37f * tau, SectorShape.snapSweep(0.37f * tau), 1e-4f)
  }

  @Test
  fun draggingAnEndChangesTheAmountKeepingTheOtherEnd() {
    val s = SectorShape.of(Pt(0f, 0f), Pt(100f, 0f), 0.5f)
    val fixed = s.handles()[0]
    // Drag the far end a quarter turn further round.
    val e = s.withHandle(1, Pt(0f, 0f) + Pt.polar(100f, s.start + 0.75f * tau)) as SectorShape
    assertEquals(0.75f, e.fraction, 1e-3f)
    assertEquals(fixed.x, e.handles()[0].x, 1e-3f)
    assertEquals(fixed.y, e.handles()[0].y, 1e-3f)
  }

  @Test
  fun aWholeCircleOpensWhicheverWayAnEndIsDragged() {
    val full = SectorShape.of(Pt(0f, 0f), Pt(100f, 0f), 1f)
    for (index in 0..1) {
      for (by in listOf(-0.1f, 0.1f)) {
        val at = Pt(0f, 0f) + Pt.polar(100f, full.handles()[index].let { (it - Pt(0f, 0f)).angle() } + by * tau)
        val e = full.withHandle(index, at) as SectorShape
        assertTrue("dot $index by $by gave ${e.fraction}", e.fraction < 0.95f)
      }
    }
  }

  @Test
  fun goingPastAFullTurnStartsAgainFromNothing() {
    val s = SectorShape.of(Pt(0f, 0f), Pt(100f, 0f), 0.5f)
    // 361° round from the fixed end is 1°.
    val e = s.withHandle(1, Pt.polar(100f, s.start + tau + 0.02f * tau)) as SectorShape
    assertEquals(0.02f, e.fraction, 1e-3f)
  }

  @Test
  fun arcEndsWrapInsteadOfWindingIntoACircle() {
    val arc = ArcShape(Pt(0f, 0f), 100f, PI.toFloat(), PI.toFloat())
    // The start dragged backwards round past the other end: a small arc, never more than a turn.
    for (k in 1..40) {
      val a = arc.withHandle(0, Pt.polar(100f, PI.toFloat() - k * 0.05f * tau)) as ArcShape
      assertTrue("k=$k sweep ${a.sweep}", a.sweep > 0f && a.sweep <= tau + 1e-4f)
      assertEquals(PI.toFloat() * 2, a.start + a.sweep, 1e-3f)
    }
    val b = arc.withHandle(1, Pt.polar(100f, PI.toFloat() + 1.25f * tau)) as ArcShape
    assertEquals(90, b.degrees)
  }
}
