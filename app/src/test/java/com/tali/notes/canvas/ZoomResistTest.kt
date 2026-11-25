package com.tali.notes.canvas

import com.tali.notes.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ln

class ZoomResistTest {
  private val free = 0.5f

  @Test fun atFreeOrCloserInNothingChanges() {
    for (z in listOf(3f, 1.2f, 1f, 0.8f, 0.5f)) assertEquals(z, ZoomResist.shown(z, free), 0f)
  }

  @Test fun justPastFreeItFollowsTheFingers() {
    val raw = free / 1.1f
    val shown = ZoomResist.shown(raw, free)
    assertTrue(shown >= raw && shown <= raw * 1.01f)
  }

  @Test fun itGetsHarderAndHarderButNeverPassesTheReach() {
    var lastShown = ZoomResist.shown(free / 2f, free)
    var lastStep = Float.MAX_VALUE
    var raw = free / 2f
    while (raw > free / 256f) {
      raw /= 2f
      val shown = ZoomResist.shown(raw, free)
      val step = ln(lastShown / shown)
      assertTrue("always farther out", shown < lastShown)
      assertTrue("each step smaller", step < lastStep)
      lastStep = step
      lastShown = shown
    }
    assertTrue(lastStep < 0.02f)
    assertTrue(ZoomResist.shown(free / 1000f, free) > free / ZoomResist.REACH)
  }

  @Test fun theWayBackGivesTheSameZoom() {
    for (z in listOf(0.45f, 0.3f, 0.2f, 0.1f, 0.05f)) {
      val raw = ZoomResist.raw(ZoomResist.shown(z, free), free)
      assertTrue("$z came back as $raw", abs(raw - z) / z < 1e-3f)
    }
    assertEquals(0.8f, ZoomResist.raw(0.8f, free), 0f)
  }

  @Test fun freeIsWhereEverythingFits() {
    assertEquals(1f, ZoomResist.free(null, 1000f, 700f), 0f)
    assertEquals(1f, ZoomResist.free(Rect(0f, 0f, 100f, 100f), 1000f, 700f), 0f)
    assertEquals(0.25f, ZoomResist.free(Rect(0f, 0f, 3952f, 952f), 1000f, 700f), 1e-4f)
  }

  @Test fun aPinchStartingBeyondTheReachIsMeasuredFromThere() {
    val f = ZoomResist.freeAt(0.5f, 0.1f)
    assertEquals(0.1f * 2.5f / 1.01f, f, 1e-5f)
    assertEquals(0.5f, ZoomResist.freeAt(0.5f, 0.3f), 0f)
  }

  @Test fun aPinchFromTheFarthestZoomZoomsBackInAtOnce() {
    val limit = free / ZoomResist.REACH
    // Spreading the fingers twice as far zooms in twice as much, straight away.
    assertEquals(limit * 2f, ZoomResist.pinched(limit, free, 2f), 1e-5f)
    // Closing them goes no farther than the limit, with no jump at the start.
    assertTrue(ZoomResist.pinched(limit, free, 0.1f) >= limit * 0.999f)
    assertEquals(0.3f, ZoomResist.pinched(0.3f, free, 1f), 1e-4f)
    // Above free a pinch follows the fingers both ways.
    assertEquals(0.56f, ZoomResist.pinched(0.8f, free, 0.7f), 1e-5f)
  }
}
