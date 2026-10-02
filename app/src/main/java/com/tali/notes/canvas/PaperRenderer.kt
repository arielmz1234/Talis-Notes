package com.tali.notes.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import com.tali.notes.geometry.Rect
import com.tali.notes.ink.Brushes
import com.tali.notes.model.CustomPaper
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

    /** Picture backgrounds on dark paper: dimmed to about a third so ink stays readable. */
    private val DIM = ColorMatrixColorFilter(ColorMatrix().apply { setScale(0.35f, 0.35f, 0.38f, 1f) })
  }
  private var buffer = FloatArray(4096)

  /**
   * When printing (PDF export), lines are hairlines in points and dense grids never fade out, so
   * the page looks like real printed paper instead of a zoomed-out screen.
   */
  var print = false

  /** World height where the pattern starts, so each A4 page's lines line up with its top edge. */
  var originY = 0f

  /** One of her own backgrounds; drawn instead of the built-in style when set. */
  var custom: CustomPaper? = null

  /** The picture of a picture background, once it has loaded. */
  var customImage: Bitmap? = null

  /** The A4 page being drawn (a picture background fills it), or null on an endless canvas. */
  var pageRect: Rect? = null

  private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
  private val dst = RectF()
  private val src = android.graphics.Rect()

  fun draw(canvas: Canvas, style: PaperStyle, camera: Camera, width: Int, height: Int) {
    custom?.let {
      drawCustom(canvas, it, camera, width, height)
      return
    }
    canvas.drawColor(paperColor)
    val mm = 160f / 25.4f
    val k = if (print) 0.45f else 1f
    when (style) {
      PaperStyle.BLANK -> Unit
      PaperStyle.DOTS -> dots(canvas, camera, width, height, 5 * mm, lineColor)
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
   * Her own background. A picture fills each A4 page (on an endless canvas it repeats on the A4
   * page grid); a designed pattern draws like the built-in ones at her spacing and colours. On dark
   * paper the page goes dark and her line colour is lightened to match the ink.
   */
  private fun drawCustom(canvas: Canvas, cp: CustomPaper, camera: Camera, width: Int, height: Int) {
    val mm = 160f / 25.4f
    val k = if (print) 0.45f else 1f
    val image = cp.image
    if (image != null) {
      canvas.drawColor(if (dark) DARK_PAPER else 0xFFFFFFFF.toInt())
      val bmp = customImage ?: return
      val pages =
        pageRect?.let { listOf(it) }
          ?: run {
            val w = Pages.WIDTH
            val h = Pages.HEIGHT
            val view = camera.visibleWorld(width, height)
            buildList {
              for (row in floor(view.top / h).toInt()..floor(view.bottom / h).toInt()) {
                for (col in floor(view.left / w).toInt()..floor(view.right / w).toInt()) add(Rect(col * w, row * h, (col + 1) * w, (row + 1) * h))
              }
            }
          }
      imagePaint.colorFilter = if (dark) DIM else null
      // Fills the page without stretching: the middle of the picture, cropped to the page's shape.
      val pageAspect = Pages.WIDTH / Pages.HEIGHT
      val bmpAspect = bmp.width.toFloat() / bmp.height
      if (bmpAspect > pageAspect) {
        val w = (bmp.height * pageAspect).toInt()
        src.set((bmp.width - w) / 2, 0, (bmp.width + w) / 2, bmp.height)
      } else {
        val h = (bmp.width / pageAspect).toInt()
        src.set(0, (bmp.height - h) / 2, bmp.width, (bmp.height + h) / 2)
      }
      for (r in pages) {
        dst.set((r.left - camera.x) * camera.scale, (r.top - camera.y) * camera.scale, (r.right - camera.x) * camera.scale, (r.bottom - camera.y) * camera.scale)
        canvas.drawBitmap(bmp, src, dst, imagePaint)
      }
      return
    }
    canvas.drawColor(if (dark) DARK_PAPER else cp.paperColor)
    val line = if (dark) (Brushes.onDarkPaper(cp.lineColor) and 0x00FFFFFF) or 0x70000000 else cp.lineColor
    val spacing = cp.spacingMm.coerceIn(2f, 30f) * mm
    when (cp.pattern) {
      PaperStyle.BLANK -> Unit
      PaperStyle.DOTS -> dots(canvas, camera, width, height, spacing, line)
      PaperStyle.LINES -> grid(canvas, camera, width, height, spacing, line, 1.2f * k, horizontalOnly = true)
      PaperStyle.ISOMETRIC -> isometric(canvas, camera, width, height, spacing, line, 1f * k)
      else -> grid(canvas, camera, width, height, spacing, line, 1f * k, horizontalOnly = false)
    }
    if (cp.margin) {
      // The red margin line of a school exercise book, 25 mm in from the page's left edge.
      val x = ((pageRect?.left ?: 0f) + 25f * mm - camera.x) * camera.scale
      paint.color = if (dark) 0x99E07A90.toInt() else 0xFFF0A0B4.toInt()
      paint.strokeWidth = 1.4f * k
      canvas.drawLine(x, 0f, x, height.toFloat(), paint)
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

  private fun dots(canvas: Canvas, camera: Camera, width: Int, height: Int, spacing: Float, color: Int) {
    val step = spacing * camera.scale
    if (step < 7f && !print) return
    paint.color = color
    paint.alpha = ((if (print) 1f else ((step - 7f) / 10f).coerceIn(0f, 1f)) * (color ushr 24)).toInt()
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
