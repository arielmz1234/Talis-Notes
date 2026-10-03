package com.tali.notes.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap

/**
 * Smooth theme and style changes: just before one, the screen as it looks is captured; the new
 * look is applied underneath and the capture fades away over it. (Animating every colour instead
 * would redraw the whole app, painted clouds included, every frame.)
 */
object ThemeFade {
  /** Set by the activity: captures the screen into [snapshot]. */
  var capture: (() -> Unit)? = null

  /** The screen as it looked just before the change, fading out; null when there's none. */
  var snapshot: ImageBitmap? by mutableStateOf(null)

  /** Call right before changing the theme or style. */
  fun beforeChange() {
    capture?.invoke()
  }
}
