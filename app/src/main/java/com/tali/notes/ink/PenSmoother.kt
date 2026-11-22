package com.tali.notes.ink

import android.os.Build
import android.view.MotionEvent
import kotlin.math.PI
import kotlin.math.hypot

/**
 * Smooths the pen's path before Ink sees it, the way the good notes apps do: a One Euro filter
 * (Casiez et al.), which smooths a lot when the pen moves slowly (where the hand's and the
 * digitizer's jitter shows as little kinks, most of all zoomed in) and hardly at all when it moves
 * fast (so quick writing keeps up with the pen). Positions are in screen pixels, so it feels the
 * same at any zoom. Tilt and timing are left as they are.
 *
 * Pressure is eased too, over [PRESSURE_TAU_MS]: the S Pen's reading sometimes jumps between two
 * samples, and the pens that thicken with pressure showed it as a step in the stroke's edge
 * (Ariel's screenshots, 22 Nov 2026: a "d" and an "r" with a notch partway down the stem). Eased,
 * a jump becomes a short taper; ordinary changes in pressure still come through.
 *
 * One per stroke: [reset] when a stroke starts, then [smooth] every event of it, in order.
 */
class PenSmoother(
  /** How smooth it is at rest (Hz; lower = smoother, with more lag while moving slowly). */
  private val minCutoff: Float = MIN_CUTOFF,
  /** How quickly smoothing eases off with speed (per px/s). */
  private val beta: Float = BETA,
  private val speedCutoff: Float = 1.2f,
) {
  private var x = 0f
  private var y = 0f
  private var vx = 0f
  private var vy = 0f
  private var pressure = 0f
  private var lastNanos = Long.MIN_VALUE
  private val props = arrayOf(MotionEvent.PointerProperties())
  private val coords = arrayOf(MotionEvent.PointerCoords())

  fun reset() {
    lastNanos = Long.MIN_VALUE
  }

  private fun alpha(cutoff: Float, dt: Float): Float {
    val tau = 1f / (2f * PI.toFloat() * cutoff)
    return 1f / (1f + tau / dt)
  }

  /** One sample at [nanos]: updates the filter and leaves the smoothed position in [x], [y]. */
  private fun step(px: Float, py: Float, pp: Float, nanos: Long) {
    if (lastNanos == Long.MIN_VALUE) {
      x = px
      y = py
      vx = 0f
      vy = 0f
      pressure = pp
      lastNanos = nanos
      return
    }
    val dt = (nanos - lastNanos) / 1e9f
    if (dt <= 0f) {
      // Same moment as the last sample: nothing to smooth over, keep where it is.
      return
    }
    lastNanos = nanos
    pressure += (1f - kotlin.math.exp(-dt * 1000f / PRESSURE_TAU_MS)) * (pp - pressure)
    val av = alpha(speedCutoff, dt)
    vx += av * ((px - x) / dt - vx)
    vy += av * ((py - y) / dt - vy)
    val cutoff = minCutoff + beta * hypot(vx, vy)
    val a = alpha(cutoff, dt)
    x += a * (px - x)
    y += a * (py - y)
  }

  private fun nanos(e: MotionEvent, h: Int): Long {
    val latest = h >= e.historySize
    return if (Build.VERSION.SDK_INT >= 34) (if (latest) e.eventTimeNanos else e.getHistoricalEventTimeNanos(h))
    else (if (latest) e.eventTime else e.getHistoricalEventTime(h)) * 1_000_000L
  }

  /**
   * Pointer [idx] of [e], smoothed, as a new one-pointer event with [action] (ACTION_DOWN, MOVE
   * or UP), keeping its pointer id, its samples and their times. Recycle it after use.
   */
  fun smooth(e: MotionEvent, idx: Int, action: Int): MotionEvent {
    e.getPointerProperties(idx, props[0])
    var out: MotionEvent? = null
    for (h in 0..e.historySize) {
      val latest = h >= e.historySize
      if (latest) e.getPointerCoords(idx, coords[0]) else e.getHistoricalPointerCoords(idx, h, coords[0])
      step(coords[0].x, coords[0].y, coords[0].pressure, nanos(e, h))
      coords[0].x = x
      coords[0].y = y
      coords[0].pressure = pressure
      val time = if (latest) e.eventTime else e.getHistoricalEventTime(h)
      val o = out
      if (o == null) {
        out = MotionEvent.obtain(e.downTime, time, action, 1, props, coords, e.metaState, e.buttonState, e.xPrecision, e.yPrecision, e.deviceId, 0, e.source, e.flags)
      } else {
        o.addBatch(time, coords, e.metaState)
      }
    }
    return out!!
  }

  companion object {
    const val MIN_CUTOFF = 2.5f
    const val BETA = 0.04f

    /** How long pressure takes to follow a change (ms, time constant): short enough to keep the pen's feel. */
    const val PRESSURE_TAU_MS = 18f
  }
}
