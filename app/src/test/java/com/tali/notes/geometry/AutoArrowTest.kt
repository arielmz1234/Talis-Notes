package com.tali.notes.geometry

import com.tali.notes.model.LineShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class AutoArrowTest {
  /** The path through [corners], sampled every 2 units with a little hand wobble. */
  private fun drawn(vararg corners: Pt, wobble: Float = 0.6f): List<Pt> {
    val out = ArrayList<Pt>()
    var k = 0
    for (i in 0 until corners.size - 1) {
      val a = corners[i]
      val b = corners[i + 1]
      val steps = maxOf(2, (a.dist(b) / 2f).toInt())
      for (s in 0 until steps) {
        val t = s / steps.toFloat()
        out += a + (b - a) * t + Pt(0f, wobble * sin(k++ * 0.7f))
      }
    }
    out += corners.last()
    return out
  }

  private fun assertArrow(a: Pt, b: Pt, shape: LineShape?) {
    assertNotNull(shape)
    shape!!
    assertTrue(shape.arrowEnd)
    assertTrue(!shape.arrowStart)
    assertEquals(a.x, shape.a.x, 2f)
    assertEquals(a.y, shape.a.y, 2f)
    assertEquals(b.x, shape.b.x, 3f)
    assertEquals(b.y, shape.b.y, 3f)
  }

  @Test fun oneStrokeWithAVHead() {
    val s = drawn(Pt(0f, 0f), Pt(200f, 0f), Pt(172f, -20f), Pt(200f, 0f), Pt(172f, 20f))
    assertArrow(Pt(0f, 0f), Pt(200f, 0f), AutoArrow.oneStroke(s))
  }

  @Test fun oneStrokeWithATriangleHead() {
    val s = drawn(Pt(0f, 0f), Pt(200f, 0f), Pt(200f, -16f), Pt(230f, 0f), Pt(200f, 16f), Pt(200f, 0f))
    assertArrow(Pt(0f, 0f), Pt(230f, 0f), AutoArrow.oneStroke(s))
  }

  @Test fun aSlantedOneStrokeArrowKeepsItsAngle() {
    val ang = 0.5f
    val u = Pt(cos(ang), sin(ang))
    val n = Pt(-u.y, u.x)
    val tip = u * 180f
    val s = drawn(Pt(0f, 0f), tip, tip - u * 26f + n * 18f, tip, tip - u * 26f - n * 18f)
    assertArrow(Pt(0f, 0f), tip, AutoArrow.oneStroke(s))
  }

  @Test fun aLineThenAHeadAtItsEnd() {
    val line = drawn(Pt(0f, 0f), Pt(200f, 0f))
    val (a, b) = AutoArrow.straight(line)!!
    assertArrow(Pt(0f, 0f), Pt(200f, 0f), AutoArrow.twoStrokes(a, b, drawn(Pt(175f, -18f), Pt(201f, 0f), Pt(175f, 18f))))
  }

  @Test fun aLineThenATriangleHead() {
    val line = drawn(Pt(0f, 0f), Pt(0f, 200f))
    val (a, b) = AutoArrow.straight(line)!!
    assertArrow(Pt(0f, 0f), Pt(0f, 228f), AutoArrow.twoStrokes(a, b, drawn(Pt(-15f, 200f), Pt(0f, 228f), Pt(15f, 200f), Pt(-15f, 200f))))
  }

  @Test fun aHeadAtTheStartPointsTheOtherWay() {
    val line = drawn(Pt(0f, 0f), Pt(200f, 0f))
    val (a, b) = AutoArrow.straight(line)!!
    assertArrow(Pt(200f, 0f), Pt(0f, 0f), AutoArrow.twoStrokes(a, b, drawn(Pt(25f, -18f), Pt(0f, 0f), Pt(25f, 18f))))
  }

  @Test fun writingStaysWriting() {
    // A dash and a ">" in a sentence: too short to be an arrow.
    val dash = drawn(Pt(0f, 0f), Pt(25f, 0f))
    assertNull(AutoArrow.straight(dash))
    assertNull(AutoArrow.oneStroke(drawn(Pt(0f, 0f), Pt(25f, 0f), Pt(18f, -6f), Pt(25f, 0f), Pt(18f, 6f))))
    // A 7: the second leg is long.
    assertNull(AutoArrow.oneStroke(drawn(Pt(0f, 0f), Pt(60f, 0f), Pt(20f, 90f))))
    // A line with one barb only (a hook), or with a bar across its end (a T).
    assertNull(AutoArrow.oneStroke(drawn(Pt(0f, 0f), Pt(200f, 0f), Pt(175f, -20f))))
    val (a, b) = AutoArrow.straight(drawn(Pt(0f, 0f), Pt(200f, 0f)))!!
    assertNull(AutoArrow.twoStrokes(a, b, drawn(Pt(200f, -20f), Pt(200f, 20f))))
    // A V written under the line's end, or a long second line.
    assertNull(AutoArrow.twoStrokes(a, b, drawn(Pt(185f, 30f), Pt(200f, 60f), Pt(215f, 30f))))
    assertNull(AutoArrow.twoStrokes(a, b, drawn(Pt(200f, 0f), Pt(200f, 180f))))
    // A circle on the end (a lollipop).
    val circle = (0..72).map { i -> val t = i * Math.PI.toFloat() * 2 / 72; Pt(215f - 15f * cos(t), 15f * sin(t)) }
    assertNull(AutoArrow.twoStrokes(a, b, circle))
    // A V pointing back along the line (a tail's feathers).
    assertNull(AutoArrow.twoStrokes(a, b, drawn(Pt(225f, -18f), Pt(199f, 0f), Pt(225f, 18f))))
  }

  @Test fun aWobblyLineIsNotAShaft() {
    assertNull(AutoArrow.straight(drawn(Pt(0f, 0f), Pt(60f, 30f), Pt(120f, 0f), Pt(200f, 30f))))
  }

  @Test fun theCleanHeadIsTheSizeAndOpeningOfTheDrawnOne() {
    val line = drawn(Pt(0f, 0f), Pt(300f, 0f))
    val (a, b) = AutoArrow.straight(line)!!
    // A small, narrow head and a big, wide one: barbs about 22 and 60 long.
    val small = AutoArrow.twoStrokes(a, b, drawn(Pt(280f, -8f), Pt(301f, 0f), Pt(280f, 8f)))!!
    val big = AutoArrow.twoStrokes(a, b, drawn(Pt(255f, -40f), Pt(301f, 0f), Pt(255f, 40f)))!!
    assertEquals(22.5f, small.head, 3f)
    assertEquals(61f, big.head, 4f)
    assertTrue(big.spread > small.spread + 0.25f)
    // Drawn that way: the barbs end where hers did.
    val barb = big.paths(2f)[1]
    assertEquals(255f, barb.first().x, 5f)
    assertEquals(40f, kotlin.math.abs(barb.first().y), 5f)
    // Resized, the head grows with it.
    val doubled = big.transformed(Affine.scaleAround(Pt(0f, 0f), 2f)) as LineShape
    assertEquals(big.head * 2f, doubled.head, 0.5f)
    // A tiny drawn head still gets at least the usual head (20 long).
    val tiny = AutoArrow.twoStrokes(a, b, drawn(Pt(290f, -9f), Pt(301f, 0f), Pt(290f, 9f)))!!
    val tip = tiny.paths(2f)[1][1]
    assertTrue(tiny.paths(2f)[1].first().dist(tip) >= 19.9f)
  }
}
