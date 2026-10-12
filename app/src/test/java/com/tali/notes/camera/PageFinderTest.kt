package com.tali.notes.camera

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PageFinderTest {
  private val w = 240
  private val h = 180

  /** A tilted page of [paper] brightness on a [desk], with writing on it and some noise. */
  private fun picture(corners: Quad, paper: Int, desk: Int, seed: Int = 1): IntArray {
    val r = Random(seed)
    val ps = corners.points.map { P(it.x * w, it.y * h) }
    fun inside(x: Float, y: Float): Boolean {
      for (i in 0 until 4) {
        val a = ps[i]
        val b = ps[(i + 1) % 4]
        if ((b.x - a.x) * (y - a.y) - (b.y - a.y) * (x - a.x) < 0) return false
      }
      return true
    }
    return IntArray(w * h) { i ->
      val x = i % w + 0.5f
      val y = i / w + 0.5f
      val base = if (inside(x, y)) (if ((i / w) % 9 == 0 && (i % w) % 13 < 8) 40 else paper) else desk
      (base + r.nextInt(-12, 13)).coerceIn(0, 255)
    }
  }

  private fun assertNear(want: Quad, got: Quad?) {
    assertNotNull(got)
    want.points.zip(got!!.points).forEach { (a, b) -> assertTrue("$a vs $b", PageFinder.distance(a, b) < 0.03f) }
  }

  private val tilted = Quad(P(0.22f, 0.12f), P(0.78f, 0.18f), P(0.72f, 0.9f), P(0.18f, 0.84f))

  @Test fun findsALightPageOnADarkDesk() = assertNear(tilted, PageFinder.find(picture(tilted, 225, 70), w, h))

  @Test fun findsADarkerPageOnAWhiteDesk() = assertNear(tilted, PageFinder.find(picture(tilted, 160, 245), w, h))

  @Test fun nothingOnAPlainDesk() = assertNull(PageFinder.find(IntArray(w * h) { 120 + (it * 7919) % 9 }, w, h))

  @Test fun cornersComeTopLeftFirst() {
    val q = Quad.ordered(listOf(P(0.9f, 0.9f), P(0.1f, 0.1f), P(0.1f, 0.9f), P(0.9f, 0.1f)))
    assertEquals(Quad(P(0.1f, 0.1f), P(0.9f, 0.1f), P(0.9f, 0.9f), P(0.1f, 0.9f)), q)
  }

  @Test fun turningFourTimesComesBack() {
    assertEquals(tilted, tilted.turned(4))
    // A quarter turn clockwise: the old bottom-left corner is the new top-left.
    val t = tilted.turned(1)
    assertEquals(P(1 - tilted.bl.y, tilted.bl.x), t.tl)
  }
}
