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

/**
 * A hand-drawn stroke. Loaded strokes are built lazily: their bounds and colour come from storage,
 * and the stroke itself (its mesh is the expensive part) is only made when something first draws
 * or touches it. A big notebook therefore opens instantly and builds what's on screen first.
 */
class InkItem
private constructor(
  override val id: String,
  override val z: Long,
  override val pen: PenType,
  private val built: Lazy<Stroke>,
  knownBounds: Rect?,
  private val knownColor: Int?,
) : CanvasItem() {
  constructor(id: String, z: Long, pen: PenType, stroke: Stroke) : this(id, z, pen, lazyOf(stroke), null, null)

  val stroke: Stroke
    get() = built.value

  /** Whether the stroke has been built yet (so background warm-up can skip it). */
  val isBuilt: Boolean
    get() = built.isInitialized()

  override val bounds: Rect = knownBounds ?: StrokeFactory.bounds(stroke)

  val color: Int
    get() = knownColor ?: Brushes.displayColor(stroke.brush)

  /** The stroke in its dark-paper colour, made the first time dark paper draws it. */
  val darkStroke: Stroke by lazy { Brushes.forDarkPaper(stroke) }

  companion object {
    fun deferred(id: String, z: Long, pen: PenType, bounds: Rect, color: Int, build: () -> Stroke) = InkItem(id, z, pen, lazy(build), bounds, color)
  }

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
  /** Bounds saved with the shape, so a loaded shape can wait to build its strokes until it's drawn. */
  knownBounds: Rect? = null,
) : CanvasItem() {
  private val built = lazy { StrokeFactory.shapeStrokes(geometry, pen, color, size) }

  val strokes: List<Stroke>
    get() = built.value

  /** The strokes in their dark-paper colour. */
  val darkStrokes: List<Stroke> by lazy { strokes.map(Brushes::forDarkPaper) }

  /** Whether the strokes exist yet (loaded shapes build them on first use). */
  val isBuilt: Boolean
    get() = built.isInitialized()

  override val bounds: Rect = knownBounds ?: strokes.map(StrokeFactory::bounds).reduceOrNull(Rect::union) ?: geometry.bounds(size)

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
  /** A page from an imported PDF (so export can give it back its own page). */
  val pdfPage: Boolean = false,
) : CanvasItem() {
  override val bounds: Rect = rect
  override val pen: PenType? = null

  override fun transformed(m: Affine, newId: String, newZ: Long): ImageItem {
    val a = m.apply(Pt(rect.left, rect.top))
    val b = m.apply(Pt(rect.right, rect.bottom))
    return ImageItem(newId, newZ, file, Rect(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y)), pdfPage)
  }
}
