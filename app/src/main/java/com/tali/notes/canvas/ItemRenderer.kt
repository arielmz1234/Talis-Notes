package com.tali.notes.canvas

import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import com.tali.notes.model.CanvasItem
import com.tali.notes.model.ImageItem
import com.tali.notes.model.InkItem
import com.tali.notes.model.ShapeItem

/** Draws items onto a canvas already set up in world coordinates. Shared by screen, PDF and previews. */
class ItemRenderer(private val strokes: CanvasStrokeRenderer) {
  private val imagePaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
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
        val bmp = item.bitmap ?: return
        rectF.set(item.rect.left, item.rect.top, item.rect.right, item.rect.bottom)
        canvas.drawBitmap(bmp, null, rectF, imagePaint)
      }
    }
  }
}
