package com.tali.notes.canvas

import android.graphics.Matrix
import com.tali.notes.data.Viewport
import com.tali.notes.geometry.Pt
import com.tali.notes.geometry.Rect

/** Pan and zoom. World units are dp at 100%; [scale] converts them to screen pixels. */
class Camera(private val density: Float) {
  var x = 0f
    private set

  var y = 0f
    private set

  var zoom = 1f
    private set

  /** The farthest out it goes: [MIN_ZOOM] for pages; an endless canvas, which resists pinches instead ([ZoomResist]), keeps [ENDLESS_MIN_ZOOM] only as a safety net. */
  var minZoom = MIN_ZOOM

  val scale: Float
    get() = zoom * density

  val worldToScreen = Matrix()
  val screenToWorld = Matrix()

  init {
    sync()
  }

  fun set(v: Viewport) {
    x = v.x
    y = v.y
    zoom = v.zoom.coerceIn(minZoom, MAX_ZOOM)
    sync()
  }

  fun viewport() = Viewport(x, y, zoom)

  fun pan(dxScreen: Float, dyScreen: Float) {
    x -= dxScreen / scale
    y -= dyScreen / scale
    sync()
  }

  /** Zooms by [factor] keeping the world point under the focus fixed on screen. */
  fun zoomAround(focusX: Float, focusY: Float, factor: Float) {
    val before = toWorld(focusX, focusY)
    zoom = (zoom * factor).coerceIn(minZoom, MAX_ZOOM)
    x = before.x - focusX / scale
    y = before.y - focusY / scale
    sync()
  }

  fun centerOn(p: Pt, width: Int, height: Int) {
    x = p.x - width / 2f / scale
    y = p.y - height / 2f / scale
    sync()
  }

  fun toWorld(sx: Float, sy: Float) = Pt(x + sx / scale, y + sy / scale)

  fun toScreen(p: Pt) = Pt((p.x - x) * scale, (p.y - y) * scale)

  fun visibleWorld(width: Int, height: Int) = Rect(x, y, x + width / scale, y + height / scale)

  private fun sync() {
    worldToScreen.setScale(scale, scale)
    worldToScreen.preTranslate(-x, -y)
    worldToScreen.invert(screenToWorld)
  }

  companion object {
    const val MIN_ZOOM = 0.2f
    const val ENDLESS_MIN_ZOOM = 0.05f
    const val MAX_ZOOM = 8f
  }
}
