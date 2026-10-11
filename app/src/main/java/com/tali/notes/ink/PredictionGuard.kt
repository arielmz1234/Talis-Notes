package com.tali.notes.ink

import android.view.MotionEvent
import kotlin.math.hypot

/**
 * Keeps the motion predictor's guess from twitching the stroke.
 *
 * Ink draws a short predicted tail ahead of the pen to hide latency. Writing fast with sharp turns
 * (the top of a loop, the turn at the bottom of a down-stroke), the predictor keeps going the old
 * way: a tail shoots past the turn, then the next frame takes it back, and the stroke twitches
 * (Tali's video, 11 Oct 2026). So a guess is only drawn when it carries on roughly the way the pen
 * has been going, and never reaches further than a short distance (or than the pen has just moved).
 */
class PredictionGuard(private val maxReachPx: Float) {
  private val xs = FloatArray(RECENT)
  private val ys = FloatArray(RECENT)
  private var count = 0
  private val props = arrayOf(MotionEvent.PointerProperties())
  private val coords = arrayOf(MotionEvent.PointerCoords())

  fun reset() {
    count = 0
  }

  /** Records the (smoothed, single-pointer) samples Ink is about to get. */
  fun record(se: MotionEvent) {
    for (h in 0..se.historySize) {
      val x = if (h < se.historySize) se.getHistoricalX(0, h) else se.getX(0)
      val y = if (h < se.historySize) se.getHistoricalY(0, h) else se.getY(0)
      // Shift along, keeping the newest last.
      if (count == RECENT) {
        System.arraycopy(xs, 1, xs, 0, RECENT - 1)
        System.arraycopy(ys, 1, ys, 0, RECENT - 1)
        count--
      }
      xs[count] = x
      ys[count] = y
      count++
    }
  }

  /**
   * The prediction to hand Ink: [prediction] itself, a shortened copy, or null when it heads off
   * from where the pen is going. Takes ownership of [prediction] (recycles it when it isn't returned).
   */
  fun tame(prediction: MotionEvent?): MotionEvent? {
    if (prediction == null) return null
    if (count < 2 || prediction.pointerCount < 1) return drop(prediction)
    val px = xs[count - 1]
    val py = ys[count - 1]
    // The way the pen has been heading, over the last few samples.
    val dx = px - xs[0]
    val dy = py - ys[0]
    val travel = hypot(dx, dy)
    val last = prediction.pointerCount - 1
    val vx = prediction.getX(last) - px
    val vy = prediction.getY(last) - py
    val reach = hypot(vx, vy)
    // Nearly still: a guess there is only noise.
    if (travel < STILL_PX) return drop(prediction)
    if (reach < 0.5f) return prediction
    // Turning back or sharply aside: the guess would overshoot the turn.
    if ((vx * dx + vy * dy) / (reach * travel) < MIN_COS) return drop(prediction)
    val cap = minOf(maxReachPx, travel * REACH_OF_TRAVEL)
    if (reach <= cap) return prediction
    // Too far ahead: the same direction, cut to the cap.
    val k = cap / reach
    prediction.getPointerProperties(last, props[0])
    prediction.getPointerCoords(last, coords[0])
    coords[0].x = px + vx * k
    coords[0].y = py + vy * k
    val out = MotionEvent.obtain(
      prediction.downTime, prediction.eventTime, prediction.action, 1, props, coords,
      prediction.metaState, prediction.buttonState, prediction.xPrecision, prediction.yPrecision,
      prediction.deviceId, 0, prediction.source, prediction.flags,
    )
    prediction.recycle()
    return out
  }

  private fun drop(prediction: MotionEvent): MotionEvent? {
    prediction.recycle()
    return null
  }

  private companion object {
    /** How many recent samples set the pen's direction (about 15 ms at 360 Hz with history). */
    const val RECENT = 5
    /** Less movement than this (px) over those samples counts as still. */
    const val STILL_PX = 1.5f
    /** The guess may turn at most this far from the pen's direction (cos 50°). */
    const val MIN_COS = 0.64f
    /** And reach at most this much of the distance just travelled. */
    const val REACH_OF_TRAVEL = 1.2f
  }
}
