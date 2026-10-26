package com.tali.notes.canvas

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tali.notes.model.CanvasItem

/**
 * Copied items, shared by every notebook while the app is open, so a diagram can move from one
 * lecture to another. Items are immutable, so holding on to them is safe; pasting makes new copies.
 */
object Clipboard {
  private var held by mutableStateOf<List<CanvasItem>>(emptyList())

  var items: List<CanvasItem>
    get() = held
    set(value) {
      held = value
      copiedAt = System.currentTimeMillis()
    }

  /** When [items] were last copied (System.currentTimeMillis), to tell whether the tablet's own clipboard is newer. */
  var copiedAt = 0L
    private set
}
