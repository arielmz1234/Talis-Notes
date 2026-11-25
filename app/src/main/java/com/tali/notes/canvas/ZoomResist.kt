package com.tali.notes.canvas

import com.tali.notes.geometry.Rect
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min

/**
 * Zooming out on an endless canvas meets more and more resistance once everything written fits on
 * screen (the iPad app's TaliCore/ZoomResist.swift, same numbers). The owner pinched out hard by
 * accident and his notes became specks, hard to find; he didn't want a hard limit. Down to [free]
 * a pinch zooms exactly as the fingers say; past it each further spread does less and less, and the
 * zoom never goes beyond free / [REACH]. Zoom 1 = 100%. Only pinches; pages don't use it.
 */
object ZoomResist {
  /** At most this many times farther out than everything fitting on screen. */
  const val REACH = 2.5f
  private val L = ln(REACH)

  /** Room kept around the writing (each side) and off the screen across, in dp. */
  const val MARGIN = 24f
  const val SIDE_ROOM = 32f
  const val TOP_ROOM_EXTRA = 40f

  /** The zoom shown for the zoom [raw] the fingers ask for. */
  fun shown(raw: Float, free: Float): Float {
    if (raw >= free) return raw
    val x = ln(free / raw)
    val y = L * (1f - exp(-x / L))
    return free / exp(y)
  }

  /** The zoom the fingers would have asked for to show [shown] (so a pinch carries on with no jump). */
  fun raw(shown: Float, free: Float): Float {
    if (shown >= free) return shown
    val y = min(ln(free / shown), 0.999f * L)
    val x = -L * ln(1f - y / L)
    return free / exp(x)
  }

  /**
   * The zoom at which [content] (world units = dp at 100%) fits in a room [roomW] × [roomH] dp with
   * [MARGIN] round it; never more than 1, and 1 with nothing written.
   */
  fun free(content: Rect?, roomW: Float, roomH: Float): Float {
    if (content == null || roomW <= 0f || roomH <= 0f) return 1f
    val w = content.width + 2 * MARGIN
    val h = content.height + 2 * MARGIN
    return minOf(1f, roomW / w, roomH / h)
  }

  /**
   * The zoom for a pinch that started at [startZoom] with the fingers now [ratio] times as far apart.
   * Out (ratio < 1) it carries on along the resistance with no jump ([raw] then [shown]); in, it
   * follows the fingers from where it started. (Following the curve back in too, a pinch started
   * near the farthest zoom barely moved: the fingers first had to undo all the resisted distance.)
   */
  fun pinched(startZoom: Float, free: Float, ratio: Float): Float =
    if (ratio >= 1f) startZoom * ratio else shown(raw(startZoom, free) * ratio, free)

  /** [free] for a pinch starting at [zoomNow]: when that's already beyond its reach (the writing grew, the screen turned), measured from there. */
  fun freeAt(free: Float, zoomNow: Float): Float = if (zoomNow < free / REACH) zoomNow * REACH / 1.01f else free
}
