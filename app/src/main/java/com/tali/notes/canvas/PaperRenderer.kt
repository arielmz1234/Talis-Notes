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

/**
 * Paper patterns drawn in screen space each frame. Spacing is real-world: 1 mm = 160/25.4 dp, and so
 * is line thickness: lines belong to the paper, so zooming in makes them thicker on screen just as
 * the squares get bigger, and what's on screen matches the PDF.
 */
class PaperRenderer {
  var paperColor = 0xFFFFFFFF.toInt()
  var lineColor = 0xFFC4C4C4.toInt()

  /** Lines and grids in a quiet light grey on white, like most notes apps; dots a shade darker. */
  var blueLine = 0xFFDADADA.toInt()
  var engineeringMinor = 0xFFEDEDED.toInt()
  var engineeringMajor = 0xFFCFCFCF.toInt()

  /** Dark paper: near-black with a hint of plum, and dimmed lines that still read. */
  var dark = false
    set(value) {
      field = value
      paperColor = if (value) DARK_PAPER else 0xFFFFFFFF.toInt()
      lineColor = if (value) 0xFF5E5C66.toInt() else 0xFFC4C4C4.toInt()
      blueLine = if (value) 0xFF403E48.toInt() else 0xFFDADADA.toInt()
      engineeringMinor = if (value) 0xFF2A2931.toInt() else 0xFFEDEDED.toInt()
      engineeringMajor = if (value) 0xFF4A4852.toInt() else 0xFFCFCFCF.toInt()
    }

  private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

  companion object {
    const val DARK_PAPER = 0xFF1C1A23.toInt()

    /** The thinnest a paper line is drawn on screen, in pixels. */
    private const val MIN_LINE_PX = 0.75f

    /** A dot's diameter in world units at standard weight (1.3 pt when printed). */
    private const val DOT_WORLD = 1.3f / 0.45f

    /**
     * How heavy paper lines, grids and dots are drawn, from her setting (1 is standard). It's
     * shared by the canvas, page previews and PDF export, so they all match.
     */
    @Volatile var lineWeight = 1f

    /** Each built-in paper's own thickness, set on its edit card (by style name); missing ones use [lineWeight]. */
    @Volatile var weights: Map<String, Float> = emptyMap()

    /** Each built-in paper's own colours (by style name); they apply on light paper. */
    @Volatile var looks: Map<String, com.tali.notes.model.PaperLook> = emptyMap()

    /** How heavy [style] (or her own paper [custom]) is drawn. */
    fun weightOf(style: PaperStyle, custom: CustomPaper?): Float = custom?.let { it.lineWeight ?: lineWeight } ?: weights[style.name] ?: lineWeight

    /** Picture backgrounds on dark paper: dimmed to about a third so ink stays readable. */
    private val DIM = ColorMatrixColorFilter(ColorMatrix().apply { setScale(0.35f, 0.35f, 0.38f, 1f) })
  }
  private var buffer = FloatArray(4096)

  /**
   * A line [world] units thick (world units, like the paper itself) in pixels. On screen it never
   * gets thinner than a hairline, so zoomed far out the lines still show (and fade with the grid).
   */
  private fun px(world: Float, camera: Camera): Float = if (print) world * camera.scale else maxOf(world * camera.scale, MIN_LINE_PX)

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

  /** The thickness of the paper being drawn (see [weightOf]). */
  private var weight = 1f

  fun draw(canvas: Canvas, style: PaperStyle, camera: Camera, width: Int, height: Int) {
    weight = weightOf(style, custom)
    custom?.let {
      drawCustom(canvas, it, camera, width, height)
      return
    }
    // Her colours for this paper, on light paper (dark paper keeps its own dark look).
    val look = if (dark) null else looks[style.name]
    val saved = intArrayOf(lineColor, blueLine, engineeringMinor, engineeringMajor)
    look?.line?.let { l ->
      lineColor = l
      blueLine = l
      engineeringMajor = l
      engineeringMinor = (l and 0x00FFFFFF) or ((((l ushr 24) * 0.45f).toInt()) shl 24)
    }
    canvas.drawColor(look?.paper ?: paperColor)
    val mm = 160f / 25.4f
    val k = weight
    try {
      drawStyle(canvas, style, camera, width, height, mm, k)
    } finally {
      lineColor = saved[0]
      blueLine = saved[1]
      engineeringMinor = saved[2]
      engineeringMajor = saved[3]
    }
  }

  private fun drawStyle(canvas: Canvas, style: PaperStyle, camera: Camera, width: Int, height: Int, mm: Float, k: Float) {
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
      PaperStyle.CORNELL, PaperStyle.RULED -> pagesInView(camera, width, height).forEach { laidOutPage(canvas, style, it, camera, k) }
    }
  }

  /** The A4 pages being drawn: the current one in a paged notebook, else the A4 grid in view. */
  private fun pagesInView(camera: Camera, width: Int, height: Int): List<Rect> =
    pageRect?.let { listOf(it) }
      ?: run {
        // An endless canvas keeps the portrait A4 grid.
        val w = Pages.PORTRAIT.width
        val h = Pages.PORTRAIT.height
        val view = camera.visibleWorld(width, height)
        buildList {
          for (row in floor(view.top / h).toInt()..floor(view.bottom / h).toInt()) {
            for (col in floor(view.left / w).toInt()..floor(view.right / w).toInt()) add(Rect(col * w, row * h, (col + 1) * w, (row + 1) * h))
          }
        }
      }

  /**
   * Papers laid out on the page itself. Ruled: a header line, a red margin and 8 mm lines.
   * Cornell: a title strip, a cue column for questions and key words, ruled notes beside it, and a
   * summary box across the bottom.
   */
  private fun laidOutPage(canvas: Canvas, style: PaperStyle, r: Rect, camera: Camera, k: Float) {
    val mm = 160f / 25.4f
    fun sx(x: Float) = (x - camera.x) * camera.scale
    fun sy(y: Float) = (y - camera.y) * camera.scale
    val spacing = 8 * mm
    val step = spacing * camera.scale
    val fade = if (print) 1f else ((step - 5f) / 10f).coerceIn(0f, 1f)
    val header = r.top + 28 * mm
    val linesEnd = if (style == PaperStyle.CORNELL) r.bottom - 50 * mm else r.bottom - 12 * mm
    // Ruled lines.
    if (fade > 0f) {
      paint.color = blueLine
      paint.alpha = (fade * (blueLine ushr 24)).toInt()
      paint.strokeWidth = px(1.2f * k, camera)
      var y = header + spacing
      while (y <= linesEnd - spacing * 0.5f) {
        canvas.drawLine(sx(r.left), sy(y), sx(r.right), sy(y), paint)
        y += spacing
      }
    }
    // The structure lines are always shown, even zoomed far out.
    paint.color = engineeringMajor
    paint.alpha = engineeringMajor ushr 24
    paint.strokeWidth = px(1.6f * k, camera)
    canvas.drawLine(sx(r.left), sy(header), sx(r.right), sy(header), paint)
    if (style == PaperStyle.CORNELL) {
      canvas.drawLine(sx(r.left + 60 * mm), sy(header), sx(r.left + 60 * mm), sy(linesEnd), paint)
      canvas.drawLine(sx(r.left), sy(linesEnd), sx(r.right), sy(linesEnd), paint)
    } else {
      paint.color = if (dark) 0x99E07A90.toInt() else 0xFFF0A0B4.toInt()
      canvas.drawLine(sx(r.left + 25 * mm), sy(r.top), sx(r.left + 25 * mm), sy(r.bottom), paint)
    }
  }

  /**
   * Her own background. A picture fills each A4 page (on an endless canvas it repeats on the A4
   * page grid); a designed pattern draws like the built-in ones at her spacing and colours. On dark
   * paper the page goes dark and her line colour is lightened to match the ink.
   */
  private fun drawCustom(canvas: Canvas, cp: CustomPaper, camera: Camera, width: Int, height: Int) {
    val mm = 160f / 25.4f
    val k = weight
    val image = cp.image
    if (image != null) {
      canvas.drawColor(if (dark) DARK_PAPER else 0xFFFFFFFF.toInt())
      val bmp = customImage ?: return
      val pages = pagesInView(camera, width, height)
      imagePaint.colorFilter = if (dark) DIM else null
      // Her chosen strength: the page shows through a fainter picture.
      imagePaint.alpha = (255 * cp.opacity.coerceIn(0.15f, 1f)).toInt()
      // Fills the page without stretching: the middle of the picture, cropped to the page's shape.
      val pageAspect = (pageRect ?: pages.firstOrNull())?.let { it.width / it.height } ?: Pages.PORTRAIT.aspect
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
      imagePaint.alpha = 255
      return
    }
    canvas.drawColor(if (dark) DARK_PAPER else cp.paperColor)
    val line = cp.faded(if (dark) (Brushes.onDarkPaper(cp.lineColor) and 0x00FFFFFF) or 0x70000000 else cp.lineColor)
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
      paint.color = cp.faded(if (dark) 0x99E07A90.toInt() else 0xFFF0A0B4.toInt())
      paint.strokeWidth = px(1.4f * k, camera)
      canvas.drawLine(x, 0f, x, height.toFloat(), paint)
    }
  }

  /**
   * Isometric paper: equilateral triangles of side [side], made of vertical lines and lines at
   * +30° and -30°. Lines are clipped to the visible area and drawn in one batch.
   */
  private fun isometric(canvas: Canvas, camera: Camera, width: Int, height: Int, side: Float, color: Int, strokeWorld: Float) {
    val h = side * 0.8660254f
    val step = h * camera.scale
    if (step < 6f && !print) return
    val alpha = if (print) 1f else ((step - 6f) / 10f).coerceIn(0f, 1f)
    paint.color = color
    paint.alpha = (alpha * (color ushr 24)).toInt()
    paint.strokeWidth = px(strokeWorld, camera)
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

  private fun grid(canvas: Canvas, camera: Camera, width: Int, height: Int, spacing: Float, color: Int, strokeWorld: Float, horizontalOnly: Boolean) {
    val step = spacing * camera.scale
    if (step < 5f && !print) return
    // Fade dense grids out as they get small instead of popping.
    val alpha = if (print) 1f else ((step - 5f) / 10f).coerceIn(0f, 1f)
    paint.color = color
    paint.alpha = (alpha * (color ushr 24)).toInt()
    paint.strokeWidth = px(strokeWorld, camera)
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
    paint.strokeWidth = (DOT_WORLD * weight.coerceAtMost(3f) * camera.scale).let { if (print) it else it.coerceAtLeast(1.5f) }
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
