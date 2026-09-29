package com.tali.notes.canvas

import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tells the screen that more loaded strokes have been built in the background, a few times a
 * second at most, so tiles that had to leave them out can redraw with them.
 */
object StrokeBuilds {
  private val listeners = CopyOnWriteArraySet<() -> Unit>()
  private val main = Handler(Looper.getMainLooper())
  private val scheduled = AtomicBoolean(false)

  fun addListener(l: () -> Unit) = listeners.add(l)

  fun removeListener(l: () -> Unit) = listeners.remove(l)

  /** Called from any thread after strokes were built. */
  fun progress() {
    if (scheduled.compareAndSet(false, true)) {
      main.postDelayed({
        scheduled.set(false)
        listeners.forEach { it() }
      }, 120)
    }
  }
}
