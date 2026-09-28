package com.tali.notes.canvas

import android.graphics.Canvas
import android.graphics.Paint
import com.tali.notes.model.PaperStyle
import kotlin.math.ceil
import kotlin.math.floor

/** Paper patterns drawn in screen space each frame. Spacing is real-world: 1 mm = 160/25.4 dp. */
class PaperRenderer {
  var paperColor = 0xFFFCFCFA.toInt()
  var lineColor = 0xFFD9DEE7.toInt()
  var engineeringMinor = 0xFFCFE3DA.toInt()
  var engineeringMajor = 0xFF9CC7B3.toInt()

  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
  private var buffer = FloatArray(4096)

  fun draw(canvas: Canvas, style: PaperStyle, camera: Camera, width: Int, height: Int) {
    canvas.drawColor(paperColor)
    val mm = 160f / 25.4f
    when (style) {
      PaperStyle.BLANK -> Unit
      PaperStyle.DOTS -> dots(canvas, camera, width, height, 5 * mm)
      PaperStyle.GRID -> grid(canvas, camera, width, height, 5 * mm, lineColor, 1f, horizontalOnly = false)
      PaperStyle.LINES -> grid(canvas, camera, width, height, 8 * mm, lineColor, 1.2f, horizontalOnly = true)
      PaperStyle.ENGINEERING -> {
        grid(canvas, camera, width, height, 2 * mm, engineeringMinor, 0.8f, horizontalOnly = false)
        grid(canvas, camera, width, height, 10 * mm, engineeringMajor, 1.3f, horizontalOnly = false)
      }
    }
  }

  private fun grid(canvas: Canvas, camera: Camera, width: Int, height: Int, spacing: Float, color: Int, strokePx: Float, horizontalOnly: Boolean) {
    val step = spacing * camera.scale
    if (step < 5f) return
    // Fade dense grids out as they get small instead of popping.
    val alpha = ((step - 5f) / 10f).coerceIn(0f, 1f)
    paint.color = color
    paint.alpha = (alpha * (color ushr 24)).toInt()
    paint.strokeWidth = strokePx
    val world = camera.visibleWorld(width, height)
    var n = 0
    fun push(x0: Float, y0: Float, x1: Float, y1: Float) {
      if (n + 4 > buffer.size) buffer = buffer.copyOf(buffer.size * 2)
      buffer[n++] = x0
      buffer[n++] = y0
      buffer[n++] = x1
      buffer[n++] = y1
    }
    val firstY = ceil(world.top / spacing) * spacing
    var wy = firstY
    while (wy <= world.bottom) {
      val sy = (wy - camera.y) * camera.scale
      push(0f, sy, width.toFloat(), sy)
      wy += spacing
    }
    if (!horizontalOnly) {
      var wx = ceil(world.left / spacing) * spacing
      while (wx <= world.right) {
        val sx = (wx - camera.x) * camera.scale
        push(sx, 0f, sx, height.toFloat())
        wx += spacing
      }
    }
    canvas.drawLines(buffer, 0, n, paint)
  }

  private fun dots(canvas: Canvas, camera: Camera, width: Int, height: Int, spacing: Float) {
    val step = spacing * camera.scale
    if (step < 7f) return
    paint.color = lineColor
    paint.alpha = (((step - 7f) / 10f).coerceIn(0f, 1f) * 255).toInt()
    paint.strokeWidth = (1.6f * camera.scale).coerceIn(1.5f, 4f)
    paint.strokeCap = Paint.Cap.ROUND
    val world = camera.visibleWorld(width, height)
    var n = 0
    val x0 = floor(world.left / spacing) * spacing
    val y0 = floor(world.top / spacing) * spacing
    var wy = y0
    while (wy <= world.bottom) {
      var wx = x0
      val sy = (wy - camera.y) * camera.scale
      while (wx <= world.right) {
        if (n + 2 > buffer.size) buffer = buffer.copyOf(buffer.size * 2)
        buffer[n++] = (wx - camera.x) * camera.scale
        buffer[n++] = sy
        wx += spacing
      }
      wy += spacing
    }
    canvas.drawPoints(buffer, 0, n, paint)
  }
}
