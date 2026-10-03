package com.tali.notes.canvas

import android.view.MotionEvent

/**
 * The S Pen's hover and button events, handed over by the activity. The canvas sits inside
 * Compose, which doesn't pass hover events down to it, so presses of the pen's button while it
 * hovers never reached it; the activity sees every one first.
 */
object PenEvents {
  /** The canvas on screen, if any. */
  @Volatile var listener: ((MotionEvent) -> Unit)? = null

  fun dispatch(e: MotionEvent) {
    listener?.invoke(e)
  }
}
