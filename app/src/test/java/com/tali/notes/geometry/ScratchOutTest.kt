package com.tali.notes.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Scribble to delete: what counts as a scribble, and what it covers (the iPad app's cases). */
class ScratchOutTest {
  /** Straight lines through [corners], a point every [step] units, with a seeded wobble. */
  private fun path(corners: List<Pt>, step: Float = 3f, jitter: Float = 1.5f): List<Pt> {
    var state = 7UL
    fun wobble(): Float {
      state = state * 6_364_136_223_846_793_005UL + 1_442_695_040_888_963_407UL
      return ((state shr 40).toFloat() / (1 shl 24).toFloat() - 0.5f) * jitter
    }
    val out = ArrayList<Pt>()
    for (i in 1 until corners.size) {
      val a = corners[i - 1]
      val b = corners[i]
      val n = maxOf((a.dist(b) / step).toInt(), 2)
      for (k in 0 until n) {
        val p = a + (b - a) * (k / n.toFloat())
        out += Pt(p.x + wobble(), p.y + wobble())
      }
    }
    out += corners.last()
    return out
  }

  private fun zigzag(legs: Int, corner: (Int) -> Pt) = path((0..legs).map(corner))

  @Test fun scrubbingAcrossIsAScribble() =
    assertNotNull(ScratchOut.area(zigzag(7) { Pt(if (it % 2 == 0) 0f else 100f, it * 3f) }, 1f))

  @Test fun scrubbingInPlaceIsAScribble() =
    assertNotNull(ScratchOut.area(path((0..7).map { Pt(if (it % 2 == 0) 0f else 80f, 0f) }, jitter = 2f), 1f))

  @Test fun aTightZigzagAlongAWordIsAScribble() =
    assertNotNull(ScratchOut.area(zigzag(20) { Pt(it * 6f, if (it % 2 == 0) 0f else 30f) }, 1f))

  @Test fun aLooseZigzagStaysInk() {
    assertNull(ScratchOut.area(zigzag(10) { Pt(it * 12f, if (it % 2 == 0) 0f else 30f) }, 1f))
    assertNull(ScratchOut.area(zigzag(6) { Pt(it * 40f, if (it % 2 == 0) 0f else 80f) }, 1f))
  }

  @Test fun writingStaysInk() {
    val cursive = (0 until 200).map { i -> val x = i * 0.5f; Pt(x, -25f * abs(sin(x * PI.toFloat() / 20f))) }
    assertNull(ScratchOut.area(cursive, 1f))
    val dense = (0 until 200).map { i -> val x = i * 0.3f; Pt(x, -20f * abs(sin(x * PI.toFloat() / 10f))) }
    assertNull(ScratchOut.area(dense, 1f))
  }

  @Test fun linesLoopsAndOneReturnStayInk() {
    assertNull(ScratchOut.area(path(listOf(Pt(0f, 0f), Pt(200f, 10f))), 1f))
    assertNull(ScratchOut.area(path(listOf(Pt(0f, 0f), Pt(100f, 0f), Pt(0f, 4f))), 1f))
    assertNull(ScratchOut.area((0 until 126).map { Pt(50f * cos(it / 20f), 50f * sin(it / 20f)) }, 1f))
    val spiral = (0 until 200).map { t -> val r = 5f + t / 3f; Pt(r * cos(t / 8f), r * sin(t / 8f)) }
    assertNull(ScratchOut.area(spiral, 1f))
  }

  @Test fun tooSmallOnScreenStaysInk() {
    val tiny = path((0..7).map { Pt(if (it % 2 == 0) 0f else 10f, it.toFloat()) }, step = 1f, jitter = 0.3f)
    assertNull(ScratchOut.area(tiny, 1f))
    // Zoomed in, the same scribble is big enough on screen.
    assertNotNull(ScratchOut.area(tiny, 3f))
  }

  @Test fun itTakesWhatItMostlyCovers() {
    val area = ScratchOut.area(zigzag(7) { Pt(if (it % 2 == 0) 0f else 100f, it * 3f) }, 1f)!!
    assertTrue("a word under it", ScratchOut.covers(listOf(listOf(Pt(10f, 5f), Pt(90f, 15f))), area))
    assertTrue("a dot under it", ScratchOut.covers(listOf(listOf(Pt(50f, 10f))), area))
    assertFalse("a long line it only crosses the start of", ScratchOut.covers(listOf(listOf(Pt(80f, 10f), Pt(300f, 10f))), area))
    assertFalse("a line below it", ScratchOut.covers(listOf(listOf(Pt(0f, 60f), Pt(100f, 60f))), area))
    // A shape's lines count together: a rectangle with one side under the scribble stays.
    val box = listOf(Pt(10f, 10f), Pt(90f, 10f), Pt(90f, 200f), Pt(10f, 200f), Pt(10f, 10f))
    assertFalse(ScratchOut.covers(listOf(box), area))
  }
  @Test fun itTakesTallLettersItRunsAcross() {
    // Scrubbed across the middle of a word: legs 100 long between y 0 and 21.
    val scribble = ScratchOut.scribble(zigzag(7) { Pt(if (it % 2 == 0) 0f else 100f, it * 3f) }, 1f)!!
    val area = scribble.hull
    val tallL = listOf(listOf(Pt(30f, -25f), Pt(30f, 45f)))
    assertFalse("only a third of it is under it", ScratchOut.covers(tallL, area))
    assertTrue("but it's crossed and not much bigger", ScratchOut.takes(tallL, scribble))
    val hooked = listOf(listOf(Pt(60f, -30f), Pt(62f, 40f), Pt(50f, 50f)))
    assertTrue(ScratchOut.takes(hooked, scribble))
  }

  @Test fun itLeavesWhatItOnlyCrossesASmallPartOf() {
    val scribble = ScratchOut.scribble(zigzag(7) { Pt(if (it % 2 == 0) 0f else 100f, it * 3f) }, 1f)!!
    assertFalse("a long line", ScratchOut.takes(listOf(listOf(Pt(80f, 10f), Pt(300f, 10f))), scribble))
    assertFalse("a line below it", ScratchOut.takes(listOf(listOf(Pt(0f, 60f), Pt(100f, 60f))), scribble))
    val box = listOf(Pt(10f, 10f), Pt(90f, 10f), Pt(90f, 200f), Pt(10f, 200f), Pt(10f, 10f))
    assertFalse("a box with one side under it", ScratchOut.takes(listOf(box), scribble))
    assertTrue("a word under it", ScratchOut.takes(listOf(listOf(Pt(10f, 5f), Pt(90f, 15f))), scribble))
  }
  @Test fun slantedLoopsAreAScribble() {
    // Quick loops travelling across a word, leaning over like handwriting (Tali's scribbles).
    for (slant in listOf(0f, 0.5f, 1f)) {
      val pts = (0..240).map { i ->
        val t = i / 240f * 6 * 2 * PI.toFloat()
        val y = 22f * cos(t)
        Pt(90f * i / 240 + 6f * sin(t) + y * slant, y)
      }
      assertNotNull("slant $slant", ScratchOut.area(pts, 1f))
    }
  }

  @Test fun circlingAWordLeavesIt() {
    val ring = (0..180).map { i -> val t = i / 60f * 2 * PI.toFloat(); Pt(60f * cos(t), 30f * sin(t)) }
    val word = listOf(listOf(Pt(-25f, -5f), Pt(-10f, 5f), Pt(5f, -5f), Pt(25f, 5f)))
    val s = ScratchOut.scribble(ring, 1f)
    if (s != null) assertFalse(ScratchOut.takes(word, s))
  }

  @Test fun scrubbingInPlaceTakesTheWordItRunsThrough() {
    val s = ScratchOut.scribble(path((0..9).map { Pt(if (it % 2 == 0) 0f else 90f, 0f) }, jitter = 2f), 1f)!!
    // Letters standing up through the scrub's thin band.
    assertTrue(ScratchOut.takes(listOf(listOf(Pt(20f, -15f), Pt(22f, 15f))), s))
    assertTrue(ScratchOut.takes(listOf(listOf(Pt(60f, -12f), Pt(70f, 10f), Pt(80f, -12f))), s))
  }
}
