package com.tali.notes.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import com.tali.notes.data.ImageStore
import com.tali.notes.model.CanvasItem
import com.tali.notes.model.ImageItem
import com.tali.notes.model.InkItem
import com.tali.notes.model.ShapeItem
import com.tali.notes.model.TextItem

/**
 * Draws items onto a canvas already set up in world coordinates. Shared by screen, PDF and previews.
 * On screen, pictures load in the background ([sync] false) and show a soft placeholder until then;
 * exports load them right away.
 */
class ItemRenderer(private val strokes: CanvasStrokeRenderer, private val images: ImageStore, private val sync: Boolean = false) {
  private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
  private val placeholderFill = Paint().apply { color = 0xFFF6F3FF.toInt() }
  private val placeholderEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = 0xFFE0D8F5.toInt()
    style = Paint.Style.STROKE
    strokeWidth = 1.5f
  }
  private val rectF = RectF()

  /** On A4 pages: the area each stroke or shape is shown in (see `Pages.clipFor`), or null to draw it whole. */
  var clipFor: ((CanvasItem) -> com.tali.notes.geometry.Rect?)? = null

  /** Dark paper: ink is drawn in its light counterpart colours; pictures stay as they are. */
  var dark = false

  private val fillPath = android.graphics.Path()
  private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

  /** Typed text laid out in its dark-paper colour, made the first time dark paper draws it. */
  private val darkText = java.util.WeakHashMap<TextItem, android.text.StaticLayout>()

  /** Pictures being changed (a fill the eraser is rubbing out), by item id: drawn instead of their file. */
  var swapped: Map<String, Bitmap> = emptyMap()

  /** For lighter PDFs: pictures bigger than this many pixels per world unit are scaled down first. */
  var imagePxPerDp: Float? = null
  private val reduced = HashMap<String, Bitmap>()

  private fun reduce(file: String, bmp: Bitmap, rect: com.tali.notes.geometry.Rect): Bitmap {
    val limit = imagePxPerDp ?: return bmp
    val w = (rect.width * limit).toInt().coerceAtLeast(1)
    if (bmp.width <= w) return bmp
    return reduced.getOrPut(file) { Bitmap.createScaledBitmap(bmp, w, (bmp.height.toLong() * w / bmp.width).toInt().coerceAtLeast(1), true) }
  }

  /**
   * Draws in three layers, each in stacking order: pictures and PDF pages at the bottom, then
   * highlighter (so it tints what's under it without covering writing), then all other ink.
   * [worldToScreen] is only a quality hint for the stroke renderer.
   *
   * With [deferUnbuilt], loaded strokes that haven't been built yet are left out instead of being
   * built right here on the UI thread; returns true if any were, so the caller can redraw later.
   */
  fun drawAll(canvas: Canvas, items: Iterable<CanvasItem>, worldToScreen: Matrix, skip: Set<String> = emptySet(), deferUnbuilt: Boolean = false): Boolean {
    var deferred = false
    fun ready(item: CanvasItem): Boolean {
      val built =
        when (item) {
          is InkItem -> item.isBuilt
          is ShapeItem -> item.isBuilt
          is ImageItem, is TextItem -> true
        }
      if (!built && deferUnbuilt) deferred = true
      return built || !deferUnbuilt
    }
    // Pictures behind the writing, then highlighter, then writing and front pictures by stacking order.
    for (item in items) if (item is ImageItem && !item.front && item.id !in skip) draw(canvas, item, worldToScreen)
    for (item in items) if (item.pen?.isHighlighter == true && item.id !in skip && ready(item)) draw(canvas, item, worldToScreen)
    for (item in items) if ((item !is ImageItem || item.front) && item.pen?.isHighlighter != true && item.id !in skip && ready(item)) draw(canvas, item, worldToScreen)
    return deferred
  }

  fun draw(canvas: Canvas, item: CanvasItem, worldToScreen: Matrix) {
    val clip = clipFor?.invoke(item)
    if (clip != null) {
      canvas.save()
      canvas.clipRect(clip.left, clip.top, clip.right, clip.bottom)
      drawItem(canvas, item, worldToScreen)
      canvas.restore()
    } else {
      drawItem(canvas, item, worldToScreen)
    }
  }

  private fun drawItem(canvas: Canvas, item: CanvasItem, worldToScreen: Matrix) {
    when (item) {
      is InkItem -> strokes.draw(canvas, if (dark) item.darkStroke else item.stroke, worldToScreen)
      is ShapeItem -> {
        // Its fill first, under the outline, in exactly the colour she chose (on dark paper too).
        item.fill?.let { f ->
          val pts = item.outline
          if (pts.size >= 3) {
            fillPath.rewind()
            fillPath.moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) fillPath.lineTo(pts[i].x, pts[i].y)
            fillPath.close()
            fillPaint.color = f
            canvas.drawPath(fillPath, fillPaint)
          }
        }
        (if (dark) item.darkStrokes else item.strokes).forEach { strokes.draw(canvas, it, worldToScreen) }
      }
      is TextItem -> {
        canvas.save()
        canvas.translate(item.origin.x, item.origin.y)
        (if (dark) darkText.getOrPut(item) { item.layoutFor(com.tali.notes.ink.Brushes.onDarkPaper(item.color)) } else item.layout).draw(canvas)
        canvas.restore()
      }
      is ImageItem -> {
        rectF.set(item.rect.left, item.rect.top, item.rect.right, item.rect.bottom)
        val edited = swapped[item.id]
        val bmp = edited ?: if (sync) images.loadNow(item.file) else images.peek(item.file)
        if (bmp != null) {
          // A mirrored fill is drawn flipped about its middle.
          val flip = item.flipX || item.flipY
          if (flip) {
            canvas.save()
            canvas.scale(if (item.flipX) -1f else 1f, if (item.flipY) -1f else 1f, rectF.centerX(), rectF.centerY())
          }
          canvas.drawBitmap(if (edited != null) edited else reduce(item.file, bmp, item.rect), null, rectF, imagePaint)
          if (flip) canvas.restore()
        } else {
          if (!sync) images.request(item.file)
          canvas.drawRect(rectF, placeholderFill)
          canvas.drawRect(rectF, placeholderEdge)
        }
      }
    }
  }
}
