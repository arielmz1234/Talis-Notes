package com.tali.notes.canvas

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

  /**
   * Draws highlighter ink first so it always sits under writing, then everything else in order.
   * [worldToScreen] is only a quality hint for the stroke renderer.
   */
  fun drawAll(canvas: Canvas, items: Iterable<CanvasItem>, worldToScreen: Matrix, skip: Set<String> = emptySet()) {
    for (item in items) if (item.pen?.isHighlighter == true && item.id !in skip) draw(canvas, item, worldToScreen)
    for (item in items) if (item.pen?.isHighlighter != true && item.id !in skip) draw(canvas, item, worldToScreen)
  }

  fun draw(canvas: Canvas, item: CanvasItem, worldToScreen: Matrix) {
    when (item) {
      is InkItem -> strokes.draw(canvas, item.stroke, worldToScreen)
      is ShapeItem -> item.strokes.forEach { strokes.draw(canvas, it, worldToScreen) }
      is ImageItem -> {
        rectF.set(item.rect.left, item.rect.top, item.rect.right, item.rect.bottom)
        val bmp = if (sync) images.loadNow(item.file) else images.peek(item.file)
        if (bmp != null) {
          canvas.drawBitmap(bmp, null, rectF, imagePaint)
        } else {
          if (!sync) images.request(item.file)
          canvas.drawRect(rectF, placeholderFill)
          canvas.drawRect(rectF, placeholderEdge)
        }
      }
    }
  }
}
