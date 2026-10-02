package com.tali.notes.geometry

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LassoTest {
  /** A small square loop around (100..140, 100..140). */
  private val loop = listOf(Pt(100f, 100f), Pt(140f, 100f), Pt(140f, 140f), Pt(100f, 140f))

  @Test
  fun circlingTheTipOfALongLineTakesIt() {
    val line = (0..50).map { Pt(120f + it * 20f, 120f) } // starts inside, runs far off to the right
    assertTrue(Lasso.touchesPath(line, loop))
  }

  @Test
  fun aLineThatOnlyCrossesTheLoopCounts() {
    // Two points either side of the loop, none inside: the segment still crosses it.
    assertTrue(Lasso.touchesPath(listOf(Pt(50f, 120f), Pt(200f, 120f)), loop))
  }

  @Test
  fun somethingElsewhereIsLeftAlone() {
    assertFalse(Lasso.touchesPath(listOf(Pt(300f, 300f), Pt(400f, 320f)), loop))
  }

  @Test
  fun pictureTouchedByTheLoopCounts() {
    assertTrue(Lasso.touchesRect(Rect(130f, 130f, 300f, 300f), loop))
    assertTrue(Lasso.touchesRect(Rect(110f, 110f, 120f, 120f), loop)) // wholly inside
  }

  @Test
  fun loopDrawnInsideAPictureLeavesThePicture() {
    // She circles writing on top of a big picture: the picture isn't picked up.
    assertFalse(Lasso.touchesRect(Rect(0f, 0f, 500f, 500f), loop))
  }
}
