package com.tali.notes.geometry

import com.tali.notes.model.LineShape
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
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

  @Test fun theCleanHeadIsAStandardHeadAsLongAsTheDrawnOneInStepsOfFive() {
    val line = drawn(Pt(0f, 0f), Pt(300f, 0f))
    val (a, b) = AutoArrow.straight(line)!!
    // A small head and a big one: barbs about 22.5 and 61 long, rounded down to 20 and 60 (steps of 10).
    val small = AutoArrow.twoStrokes(a, b, drawn(Pt(280f, -8f), Pt(301f, 0f), Pt(280f, 8f)))!!
    val big = AutoArrow.twoStrokes(a, b, drawn(Pt(255f, -40f), Pt(301f, 0f), Pt(255f, 40f)))!!
    assertEquals(20f, small.head, 0.01f)
    assertEquals(60f, big.head, 0.01f)
    // Drawn as a standard arrowhead: equal barbs at 30° either side of the line.
    val (left, tip, right) = big.paths(2f)[1]
    assertEquals(60f, left.dist(tip), 0.5f)
    assertEquals(60f, right.dist(tip), 0.5f)
    assertEquals(30f, abs(left.y - tip.y), 0.5f)
    assertEquals(30f, abs(right.y - tip.y), 0.5f)
    // Resized, the head grows with it.
    val doubled = big.transformed(Affine.scaleAround(Pt(0f, 0f), 2f)) as LineShape
    assertEquals(120f, doubled.head, 0.5f)
    // A tiny drawn head still gets at least the usual head (20 long).
    val tiny = AutoArrow.twoStrokes(a, b, drawn(Pt(290f, -9f), Pt(301f, 0f), Pt(290f, 9f)))!!
    val t = tiny.paths(2f)[1]
    assertTrue(t[0].dist(t[1]) >= 19.9f)
  }

  @Test fun aHeadDrawnLikeTheLastOneGetsExactlyTheSameHead() {
    assertEquals(60f, AutoArrow.headSize(58f, last = 60f), 0f)
    assertEquals(60f, AutoArrow.headSize(70f, last = 60f), 0f)
    assertEquals(60f, AutoArrow.headSize(50f, last = 60f), 0f)
    // Too far off: its own size, in steps of 10.
    assertEquals(40f, AutoArrow.headSize(47f, last = 60f), 0f)
    assertEquals(70f, AutoArrow.headSize(79f, last = 60f), 0f)
    assertEquals(30f, AutoArrow.headSize(38.9f), 0f)
    // Through the recogniser: the second arrow takes the first one's head.
    val (a, b) = AutoArrow.straight(drawn(Pt(0f, 0f), Pt(300f, 0f)))!!
    val first = AutoArrow.twoStrokes(a, b, drawn(Pt(255f, -40f), Pt(301f, 0f), Pt(255f, 40f)))!!
    val second = AutoArrow.twoStrokes(a, b, drawn(Pt(250f, -42f), Pt(301f, 0f), Pt(250f, 42f)), lastHead = first.head)!!
    assertEquals(first.head, second.head, 0f)
  }
  /** A V with its point rounded off: from [l] round a curve of [round] radius near [tip] to [r]. */
  private fun roundedV(l: Pt, tip: Pt, r: Pt, round: Float): List<Pt> {
    val out = ArrayList<Pt>()
    val inL = tip + (l - tip).normalized() * round
    val inR = tip + (r - tip).normalized() * round
    out += drawn(l, inL)
    for (k in 1..12) { val t = k / 12f; val a = inL * ((1 - t) * (1 - t)) + tip * (2 * t * (1 - t)) + inR * (t * t); out += a }
    out += drawn(inR, r)
    return out
  }

  @Test fun roundedAndNearlyUHeadsCount() {
    // Her pointing-down arrows: a line, then a V with a round bottom.
    val (a, b) = AutoArrow.straight(drawn(Pt(0f, 0f), Pt(0f, 60f)))!!
    assertNotNull(AutoArrow.twoStrokes(a, b, roundedV(Pt(-9f, 44f), Pt(0f, 62f), Pt(9f, 44f), round = 6f)))
    // Nearly a U: arms almost upright, a round bottom.
    val u = drawn(Pt(-7f, 40f), Pt(-6f, 54f)) + (0..12).map { k -> val t = Math.PI.toFloat() * k / 12; Pt(-6f * cos(t), 54f + 6f * sin(t)) } + drawn(Pt(6f, 54f), Pt(7f, 40f))
    val arrow = AutoArrow.twoStrokes(a, b, u)
    assertNotNull(arrow)
    assertTrue(arrow!!.b.y > 55f)
  }

  @Test fun aSlightlyBowedShaftCounts() {
    // Her → with a shaft that bows a little, then a head.
    val bowed = (0..30).map { k -> val t = k / 30f; Pt(80f * t, -4f * sin(Math.PI.toFloat() * t)) }
    val arrow = AutoArrow.pair(bowed, drawn(Pt(66f, -10f), Pt(81f, 0f), Pt(66f, 10f)))
    assertArrow(Pt(0f, 0f), Pt(81f, 0f), arrow)
  }

  @Test fun theHeadCanComeFirst() {
    // A ^ first, then the line up into it.
    val head = drawn(Pt(-10f, 14f), Pt(0f, 0f), Pt(10f, 14f))
    val line = drawn(Pt(1f, 60f), Pt(0f, 1f))
    val arrow = AutoArrow.pair(head, line)
    assertArrow(Pt(1f, 60f), Pt(0f, 0f), arrow?.let { LineShape(it.a, it.b, arrowEnd = true) })
    // And a Y written as a V then its stem stays writing.
    assertNull(AutoArrow.pair(drawn(Pt(-12f, -20f), Pt(0f, 0f), Pt(12f, -20f)), drawn(Pt(0f, 0f), Pt(0f, 45f))))
  }
}
