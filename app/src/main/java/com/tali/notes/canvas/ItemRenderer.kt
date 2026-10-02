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

  /** Dark paper: ink is drawn in its light counterpart colours; pictures stay as they are. */
  var dark = false

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
          is ImageItem -> true
        }
      if (!built && deferUnbuilt) deferred = true
      return built || !deferUnbuilt
    }
    for (item in items) if (item is ImageItem && item.id !in skip) draw(canvas, item, worldToScreen)
    for (item in items) if (item.pen?.isHighlighter == true && item.id !in skip && ready(item)) draw(canvas, item, worldToScreen)
    for (item in items) if (item !is ImageItem && item.pen?.isHighlighter != true && item.id !in skip && ready(item)) draw(canvas, item, worldToScreen)
    return deferred
  }

  fun draw(canvas: Canvas, item: CanvasItem, worldToScreen: Matrix) {
    when (item) {
      is InkItem -> strokes.draw(canvas, if (dark) item.darkStroke else item.stroke, worldToScreen)
      is ShapeItem -> (if (dark) item.darkStrokes else item.strokes).forEach { strokes.draw(canvas, it, worldToScreen) }
      is ImageItem -> {
        rectF.set(item.rect.left, item.rect.top, item.rect.right, item.rect.bottom)
        val bmp = if (sync) images.loadNow(item.file) else images.peek(item.file)
        if (bmp != null) {
          canvas.drawBitmap(reduce(item.file, bmp, item.rect), null, rectF, imagePaint)
        } else {
          if (!sync) images.request(item.file)
          canvas.drawRect(rectF, placeholderFill)
          canvas.drawRect(rectF, placeholderEdge)
        }
      }
    }
  }
}
