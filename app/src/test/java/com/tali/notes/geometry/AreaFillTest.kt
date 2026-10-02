package com.tali.notes.geometry

import com.tali.notes.canvas.AreaFill
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class AreaFillTest {
  private val w = 100
  private val h = 100

  /** A square outline from (20,20) to (80,80), optionally with a gap of [gap] cells in its right side. */
  private fun square(gap: Int = 0): BooleanArray {
    val g = BooleanArray(w * h)
    for (i in 20..80) {
      g[20 * w + i] = true
      g[80 * w + i] = true
      g[i * w + 20] = true
      if (gap == 0 || i !in 50 until 50 + gap) g[i * w + 80] = true
    }
    return g
  }

  @Test
  fun fillsInsideAClosedOutline() {
    val f = AreaFill.fill(square(), w, h, 50, 50, bridge = 2)
    assertNotNull(f)
    assertTrue(f!![50 * w + 50])
    assertTrue("tucks under the line", f[50 * w + 79])
    assertFalse("stays inside", f[50 * w + 90])
  }

  @Test
  fun bridgesASmallGap() {
    assertNotNull(AreaFill.fill(square(gap = 3), w, h, 50, 30, bridge = 2))
  }

  @Test
  fun refusesAnOpenArea() {
    assertNull(AreaFill.fill(square(gap = 12), w, h, 50, 30, bridge = 2))
  }

  @Test
  fun refusesATapOnTheLine() {
    assertNull(AreaFill.fill(square(), w, h, 20, 50, bridge = 2))
  }

  @Test
  fun outsideEverythingIsNotAnArea() {
    assertNull(AreaFill.fill(square(), w, h, 5, 5, bridge = 2))
  }
}
