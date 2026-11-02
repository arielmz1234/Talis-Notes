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
 * same at any zoom. Pressure, tilt and timing are left as they are.
 *
 * One per stroke: [reset] when a stroke starts, then [smooth] every event of it, in order.
 */
class PenSmoother(
  /** How smooth it is at rest (Hz; lower = smoother, with more lag while moving slowly). */
  private var minCutoff: Float = MIN_CUTOFF,
  /** How quickly smoothing eases off with speed (per px/s). */
  private var beta: Float = BETA,
  private val speedCutoff: Float = 1.2f,
) {
  private var x = 0f
  private var y = 0f
  private var vx = 0f
  private var vy = 0f
  private var lastNanos = Long.MIN_VALUE
  private val props = arrayOf(MotionEvent.PointerProperties())
  private val coords = arrayOf(MotionEvent.PointerCoords())

  /** Starts a new stroke, smoothed by [minCutoff] and [beta] (see [forHighlighter]). */
  fun reset(minCutoff: Float = MIN_CUTOFF, beta: Float = BETA) {
    this.minCutoff = minCutoff
    this.beta = beta
    lastNanos = Long.MIN_VALUE
  }

  private fun alpha(cutoff: Float, dt: Float): Float {
    val tau = 1f / (2f * PI.toFloat() * cutoff)
    return 1f / (1f + tau / dt)
  }

  /** One sample at [nanos]: updates the filter and leaves the smoothed position in [x], [y]. */
  private fun step(px: Float, py: Float, nanos: Long) {
    if (lastNanos == Long.MIN_VALUE) {
      x = px
      y = py
      vx = 0f
      vy = 0f
      lastNanos = nanos
      return
    }
    val dt = (nanos - lastNanos) / 1e9f
    if (dt <= 0f) {
      // Same moment as the last sample: nothing to smooth over, keep where it is.
      return
    }
    lastNanos = nanos
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
      step(coords[0].x, coords[0].y, nanos(e, h))
      coords[0].x = x
      coords[0].y = y
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

    /**
     * The highlighter's smoothing at [zoom]: a highlighter is a broad swipe where a little more lag
     * doesn't show, and zoomed out it's thin on screen, so the same hand and digitizer jitter is a
     * bigger share of its width and showed as a wobbly edge (November 2026). It smooths more at
     * rest, and more again the further out it's zoomed (down to half).
     */
    fun forHighlighter(zoom: Float): Pair<Float, Float> = 1.5f * zoom.coerceIn(0.5f, 1f) to 0.03f
  }
}
