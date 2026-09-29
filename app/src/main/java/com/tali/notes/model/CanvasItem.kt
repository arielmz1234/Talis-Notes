package com.tali.notes.model

import androidx.ink.strokes.Stroke
import com.tali.notes.geometry.Affine
import com.tali.notes.geometry.Pt
import com.tali.notes.geometry.Rect
import com.tali.notes.ink.Brushes
import com.tali.notes.ink.StrokeFactory

/** Everything on the canvas. Items are immutable; edits replace an item with a new one. */
sealed class CanvasItem {
  abstract val id: String

  /** Stacking order; larger draws on top. */
  abstract val z: Long

  abstract val bounds: Rect

  /** Pen that made the item, used by the eraser and lasso filters. Images have none. */
  abstract val pen: PenType?

  abstract fun transformed(m: Affine, newId: String = id, newZ: Long = z): CanvasItem
}

class InkItem(override val id: String, override val z: Long, override val pen: PenType, val stroke: Stroke) : CanvasItem() {
  override val bounds: Rect = StrokeFactory.bounds(stroke)

  val color: Int
    get() = Brushes.displayColor(stroke.brush)

  override fun transformed(m: Affine, newId: String, newZ: Long) = InkItem(newId, newZ, pen, StrokeFactory.transform(stroke, m))

  fun recolored(color: Int) = InkItem(id, z, pen, stroke.copy(stroke.brush.copyWithColorIntArgb(Brushes.inkColor(pen, color))))
}

class ShapeItem(
  override val id: String,
  override val z: Long,
  override val pen: PenType,
  val color: Int,
  val size: Float,
  val geometry: ShapeGeometry,
) : CanvasItem() {
  val strokes: List<Stroke> = StrokeFactory.shapeStrokes(geometry, pen, color, size)

  override val bounds: Rect = strokes.map(StrokeFactory::bounds).reduceOrNull(Rect::union) ?: geometry.bounds(size)

  override fun transformed(m: Affine, newId: String, newZ: Long) =
    ShapeItem(newId, newZ, pen, color, (size * m.scaleFactor()).coerceAtLeast(0.5f), geometry.transformed(m))

  fun withGeometry(g: ShapeGeometry) = ShapeItem(id, z, pen, color, size, g)

  fun recolored(color: Int) = ShapeItem(id, z, pen, color, size, geometry)

  /** The shape's outline as plain ink, for partial erasing. */
  fun toInk(idFor: (Int) -> String): List<InkItem> = strokes.mapIndexed { i, s -> InkItem(idFor(i), z, pen, s) }
}

class ImageItem(
  override val id: String,
  override val z: Long,
  /** File name inside the app's image folder. */
  val file: String,
  val rect: Rect,
) : CanvasItem() {
  override val bounds: Rect = rect
  override val pen: PenType? = null

  override fun transformed(m: Affine, newId: String, newZ: Long): ImageItem {
    val a = m.apply(Pt(rect.left, rect.top))
    val b = m.apply(Pt(rect.right, rect.bottom))
    return ImageItem(newId, newZ, file, Rect(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y)))
  }
}
