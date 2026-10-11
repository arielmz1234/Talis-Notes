package com.tali.notes.ink

import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The predicted tail only carries on the pen's way, and not too far (fast writing twitched). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class PredictionGuardTest {
  private fun at(x: Float, y: Float, t: Long = 0L) = MotionEvent.obtain(0L, t, MotionEvent.ACTION_MOVE, x, y, 0)

  private fun guardAfter(vararg pts: Pair<Float, Float>) =
    PredictionGuard(maxReachPx = 30f).apply { pts.forEachIndexed { i, (x, y) -> at(x, y, i.toLong()).also { record(it) }.recycle() } }

  @Test
  fun aGuessAlongTheStrokeIsKept() {
    val g = guardAfter(0f to 0f, 5f to 0f, 10f to 0f, 15f to 0f, 20f to 0f)
    val out = g.tame(at(26f, 1f))
    assertNotNull(out)
    assertEquals(26f, out!!.x, 0.01f)
  }

  @Test
  fun aGuessPastATurnIsDropped() {
    // Going down, the guess shoots back up (the top of a loop).
    val g = guardAfter(0f to 0f, 0f to 5f, 0f to 10f, 0f to 15f, 0f to 20f)
    assertNull(g.tame(at(0f, 8f)))
    // And sharply aside.
    assertNull(g.tame(at(14f, 22f)))
  }

  @Test
  fun aGuessTooFarAheadIsShortened() {
    val g = guardAfter(0f to 0f, 5f to 0f, 10f to 0f, 15f to 0f, 20f to 0f)
    // 20 px travelled: the guess may reach 24 px past the pen, along its own direction.
    val out = g.tame(at(80f, 0f))!!
    assertEquals(44f, out.x, 0.01f)
    assertEquals(0f, out.y, 0.01f)
  }

  @Test
  fun standingStillDrawsNoGuess() {
    val g = guardAfter(10f to 10f, 10.2f to 10f, 10.4f to 10.1f, 10.5f to 10f, 10.6f to 10f)
    assertNull(g.tame(at(14f, 10f)))
  }
}
