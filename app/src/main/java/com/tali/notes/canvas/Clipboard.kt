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
  var items by mutableStateOf<List<CanvasItem>>(emptyList())
}
