package com.tali.notes.canvas

import android.graphics.Canvas
import android.graphics.Paint
import com.tali.notes.model.PaperStyle
import kotlin.math.ceil
import kotlin.math.floor

/** Paper patterns drawn in screen space each frame. Spacing is real-world: 1 mm = 160/25.4 dp. */
class PaperRenderer {
  var paperColor = 0xFFFDFCFF.toInt()
  var lineColor = 0xFFD9CCF2.toInt()

  /** Ruled lines and grids use light blue, like a school notebook; dots stay lilac. */
  var blueLine = 0xFFC7DBF8.toInt()
  var engineeringMinor = 0xFFE4EDFC.toInt()
  var engineeringMajor = 0xFFA9C1F2.toInt()

  /** Dark paper: near-black with a hint of plum, and dimmed lines that still read. */
  var dark = false
    set(value) {
      field = value
      paperColor = if (value) DARK_PAPER else 0xFFFDFCFF.toInt()
      lineColor = if (value) 0xFF5A4E78.toInt() else 0xFFD9CCF2.toInt()
      blueLine = if (value) 0xFF34466A.toInt() else 0xFFC7DBF8.toInt()
      engineeringMinor = if (value) 0xFF242C40.toInt() else 0xFFE4EDFC.toInt()
      engineeringMajor = if (value) 0xFF3E5482.toInt() else 0xFFA9C1F2.toInt()
    }

  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

  companion object {
    const val DARK_PAPER = 0xFF1C1A23.toInt()
  }
  private var buffer = FloatArray(4096)

  /**
   * When printing (PDF export), lines are hairlines in points and dense grids never fade out, so
   * the page looks like real printed paper instead of a zoomed-out screen.
   */
  var print = false

  /** World height where the pattern starts, so each A4 page's lines line up with its top edge. */
  var originY = 0f

  fun draw(canvas: Canvas, style: PaperStyle, camera: Camera, width: Int, height: Int) {
    canvas.drawColor(paperColor)
    val mm = 160f / 25.4f
    val k = if (print) 0.45f else 1f
    when (style) {
      PaperStyle.BLANK -> Unit
      PaperStyle.DOTS -> dots(canvas, camera, width, height, 5 * mm)
      PaperStyle.GRID -> grid(canvas, camera, width, height, 5 * mm, blueLine, 1f * k, horizontalOnly = false)
      PaperStyle.LINES -> grid(canvas, camera, width, height, 8 * mm, blueLine, 1.2f * k, horizontalOnly = true)
      PaperStyle.ENGINEERING -> {
        grid(canvas, camera, width, height, 2 * mm, engineeringMinor, 0.8f * k, horizontalOnly = false)
        grid(canvas, camera, width, height, 10 * mm, engineeringMajor, 1.3f * k, horizontalOnly = false)
      }
      PaperStyle.ISOMETRIC -> isometric(canvas, camera, width, height, 6 * mm, blueLine, 1f * k)
    }
  }

  /**
   * Isometric paper: equilateral triangles of side [side], made of vertical lines and lines at
   * +30° and -30°. Lines are clipped to the visible area and drawn in one batch.
   */
  private fun isometric(canvas: Canvas, camera: Camera, width: Int, height: Int, side: Float, color: Int, strokePx: Float) {
    val h = side * 0.8660254f
    val step = h * camera.scale
    if (step < 6f && !print) return
    val alpha = if (print) 1f else ((step - 6f) / 10f).coerceIn(0f, 1f)
    paint.color = color
    paint.alpha = (alpha * (color ushr 24)).toInt()
    paint.strokeWidth = strokePx
    val w = camera.visibleWorld(width, height)
    val t = 0.57735026f // tan 30°
    var n = 0
    fun push(x0: Float, y0: Float, x1: Float, y1: Float) {
      if (n + 4 > buffer.size) buffer = buffer.copyOf(buffer.size * 2)
      buffer[n++] = (x0 - camera.x) * camera.scale
      buffer[n++] = (y0 - camera.y) * camera.scale
      buffer[n++] = (x1 - camera.x) * camera.scale
      buffer[n++] = (y1 - camera.y) * camera.scale
    }
    var x = ceil(w.left / h) * h
    while (x <= w.right) {
      push(x, w.top, x, w.bottom)
      x += h
    }
    // Rising lines: y = c - x·tan30 on screen (y grows downward), c a multiple of the side.
    var c = floor((w.top + w.left * t - originY) / side) * side + originY
    while (c <= w.bottom + w.right * t) {
      push(w.left, c - w.left * t, w.right, c - w.right * t)
      c += side
    }
    // Falling lines: y = c + x·tan30.
    c = floor((w.top - w.right * t - originY) / side) * side + originY
    while (c <= w.bottom - w.left * t) {
      push(w.left, c + w.left * t, w.right, c + w.right * t)
      c += side
    }
    canvas.drawLines(buffer, 0, n, paint)
  }

  private fun grid(canvas: Canvas, camera: Camera, width: Int, height: Int, spacing: Float, color: Int, strokePx: Float, horizontalOnly: Boolean) {
    val step = spacing * camera.scale
    if (step < 5f && !print) return
    // Fade dense grids out as they get small instead of popping.
    val alpha = if (print) 1f else ((step - 5f) / 10f).coerceIn(0f, 1f)
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
    val firstY = ceil((world.top - originY) / spacing) * spacing + originY
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
    if (step < 7f && !print) return
    paint.color = lineColor
    paint.alpha = if (print) 255 else (((step - 7f) / 10f).coerceIn(0f, 1f) * 255).toInt()
    paint.strokeWidth = if (print) 1.3f else (1.6f * camera.scale).coerceIn(1.5f, 4f)
    paint.strokeCap = Paint.Cap.ROUND
    val world = camera.visibleWorld(width, height)
    var n = 0
    val x0 = floor(world.left / spacing) * spacing
    val y0 = floor((world.top - originY) / spacing) * spacing + originY
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
