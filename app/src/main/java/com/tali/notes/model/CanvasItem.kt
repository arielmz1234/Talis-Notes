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

  /** The same item at another place in the stacking order (cheap: nothing is rebuilt). */
  abstract fun withZ(newZ: Long): CanvasItem

  /** Locked in place: the lasso, taps and the eraser leave it alone until it's unlocked. */
  abstract val locked: Boolean

  /** The same item locked or unlocked (cheap: nothing is rebuilt). */
  abstract fun withLocked(on: Boolean): CanvasItem
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
  override val locked: Boolean = false,
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

  /** Where the stroke began (on A4 pages it belongs to that page). */
  val start: com.tali.notes.geometry.Pt by lazy {
    val inputs = stroke.inputs
    if (inputs.size > 0) inputs.get(0).let { com.tali.notes.geometry.Pt(it.x, it.y) } else bounds.center
  }

  companion object {
    fun deferred(id: String, z: Long, pen: PenType, bounds: Rect, color: Int, locked: Boolean = false, build: () -> Stroke) = InkItem(id, z, pen, lazy(build), bounds, color, locked)

    /** Below a hundredth of a unit (far under a pixel) bounds count as holding the stroke. */
    private const val BOUNDS_SLACK = 0.01f
  }

  override fun transformed(m: Affine, newId: String, newZ: Long) = InkItem(newId, newZ, pen, StrokeFactory.transform(stroke, m))

  override fun withZ(newZ: Long) = InkItem(id, newZ, pen, built, bounds, knownColor, locked)

  /**
   * This stroke (built now if it wasn't) with bounds that hold all of it, or null when they already do.
   * Bounds saved before version 1.0.28 came from Ink's stroke as it was written, whose outline can
   * fall a little short of the same stroke built again from its points (what's drawn after
   * reopening): the canvas tiles beyond the saved bounds left that bit out, slicing a stroke's end
   * flat until it was moved.
   */
  fun withTrueBounds(): InkItem? {
    val real = StrokeFactory.bounds(stroke)
    val b = bounds
    if (real.left >= b.left - BOUNDS_SLACK && real.top >= b.top - BOUNDS_SLACK && real.right <= b.right + BOUNDS_SLACK && real.bottom <= b.bottom + BOUNDS_SLACK) return null
    return InkItem(id, z, pen, built, real.union(b), knownColor, locked)
  }

  override fun withLocked(on: Boolean) = InkItem(id, z, pen, built, bounds, knownColor, on)

  fun recolored(color: Int) = InkItem(id, z, pen, stroke.copy(stroke.brush.copyWithColorIntArgb(Brushes.inkColor(pen, color))))

  /** The same stroke drawn [size] thick with [line] style (the pen's points stay the same). */
  fun restyled(size: Float = this.size, line: LineStyle = this.line): InkItem {
    // The old dashed pen's strokes become fineliner strokes drawn in the chosen line.
    val p = if (pen == PenType.DASHED) PenType.FINELINER else pen
    return InkItem(id, z, p, stroke.copy(Brushes.brush(p, color, size, line, square)))
  }

  /** Drawn with a square-tipped highlighter. */
  val square: Boolean
    get() = Brushes.isSquare(stroke.brush)

  /** How thick the stroke is drawn. */
  val size: Float
    get() = stroke.brush.size

  /** Solid, dotted or dashed, as the pen was set when it was written. */
  val line: LineStyle
    get() = if (pen == PenType.DASHED) LineStyle.SHORT_DASH else Brushes.styleOf(stroke.brush)
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
  /** The colour it's filled with (with its own opacity), or null; only closed shapes take a fill. */
  val fill: Int? = null,
  /** Strokes already built for this same outline and colour, shared instead of built again. */
  sharedStrokes: Lazy<List<Stroke>>? = null,
  /** How its lines are drawn: solid, dotted or dashed (arrow barbs and tiny details stay solid). */
  val line: LineStyle = LineStyle.SOLID,
  override val locked: Boolean = false,
) : CanvasItem() {
  /** Whether this shape encloses an area that can be filled. */
  val closed: Boolean
    get() = geometry.let { it is EllipseShape || it is SectorShape || it is TriangleShape || (it is PolyShape && it.closed) }

  /** The outline enclosing its area, for filling and for telling whether a point is inside. */
  val outline: List<com.tali.notes.geometry.Pt>
    get() = geometry.paths(size).firstOrNull().orEmpty()
  private val built = sharedStrokes ?: lazy { StrokeFactory.shapeStrokes(geometry, pen, color, size, line) }

  val strokes: List<Stroke>
    get() = built.value

  /** The strokes in their dark-paper colour. */
  val darkStrokes: List<Stroke> by lazy { strokes.map(Brushes::forDarkPaper) }

  /** Whether the strokes exist yet (loaded shapes build them on first use). */
  val isBuilt: Boolean
    get() = built.isInitialized()

  /**
   * Drawn as a vector outline with sharp corners ([ShapeOutline]). The pencil keeps its grain and
   * the old dashed pen its own pattern, so those are drawn from their Ink strokes (round corners).
   */
  val vector: Boolean
    get() = pen != PenType.PENCIL && pen != PenType.DASHED

  /** The lines of its outline as drawn ([ShapeOutline]). */
  val pieces: List<ShapeOutline.Piece> by lazy { ShapeOutline.pieces(geometry, size, line) }

  /** [pieces] as an Android path, made the first time it's drawn. */
  val outlinePath: android.graphics.Path by lazy { ShapeOutline.path(pieces) }

  // A vector outline's mitred corners reach a little past the round ones of its Ink strokes (and
  // past bounds saved before shapes were drawn this way), so they're always counted.
  override val bounds: Rect =
    (knownBounds ?: strokes.map(StrokeFactory::bounds).reduceOrNull(Rect::union) ?: geometry.bounds(size)).let { b ->
      if (vector) ShapeOutline.bounds(pieces, size)?.union(b) ?: b else b
    }

  override fun transformed(m: Affine, newId: String, newZ: Long) =
    ShapeItem(newId, newZ, pen, color, (size * m.scaleFactor()).coerceAtLeast(0.5f), geometry.transformed(m), fill = fill, line = line)

  override fun withZ(newZ: Long) = ShapeItem(id, newZ, pen, color, size, geometry, bounds, fill, built, line, locked)

  override fun withLocked(on: Boolean) = ShapeItem(id, z, pen, color, size, geometry, bounds, fill, built, line, on)

  fun withGeometry(g: ShapeGeometry) = ShapeItem(id, z, pen, color, size, g, fill = fill, line = line)

  /** Another colour; a see-through shape stays as see-through. */
  fun recolored(color: Int) = ShapeItem(id, z, pen, if (this.color ushr 24 in 1..0xFE) (color and 0x00FFFFFF) or (this.color and 0xFF000000.toInt()) else color, size, geometry, fill = fill, line = line)

  fun filled(color: Int?) = ShapeItem(id, z, pen, this.color, size, geometry, bounds, color, built, line)

  /** The same shape drawn with another line: thickness [size] and [line] style. */
  fun restyled(size: Float = this.size, line: LineStyle = this.line) = ShapeItem(id, z, pen, color, size, geometry, fill = fill, line = line)

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
  /**
   * Brought to the front: drawn among the writing by stacking order, so it can cover ink. Off (the
   * default, and every older picture) keeps it behind all writing, like paper.
   */
  val front: Boolean = false,
  override val locked: Boolean = false,
  /** For an area fill: the strokes and shapes outlining it (recorded when it was made), which move with it. */
  val walls: Set<String> = emptySet(),
  /** Drawn mirrored left to right / upside down (an area fill mirrored with its outline). */
  val flipX: Boolean = false,
  val flipY: Boolean = false,
  /**
   * The part of the picture shown, as fractions of its width and height (0..1); null shows it all.
   * Cropping keeps the whole picture, so a crop can be undone or opened up again later.
   */
  val crop: Rect? = null,
) : CanvasItem() {
  override val bounds: Rect = rect

  /** The same picture outlining [ids] instead (a copy's outline is the copied strokes). */
  fun withWalls(ids: Set<String>) = ImageItem(id, z, file, rect, pdfPage, front, locked, ids, flipX, flipY, crop)

  /** Mirrored in place (an area fill flipping with its outline). */
  fun flipped(horizontal: Boolean) = ImageItem(id, z, file, rect, pdfPage, front, locked, walls, if (horizontal) !flipX else flipX, if (horizontal) flipY else !flipY, crop)

  /** Cropped to [part] of the whole picture (fractions, see [crop]), shown in [shown] on the page. */
  fun cropped(part: Rect?, shown: Rect) = ImageItem(id, z, file, shown, pdfPage, front, locked, walls, flipX, flipY, part)

  /** Where the whole picture would sit on the page, uncropped. */
  val whole: Rect
    get() {
      val c = crop ?: return rect
      val w = rect.width / c.width.coerceAtLeast(0.001f)
      val h = rect.height / c.height.coerceAtLeast(0.001f)
      val left = rect.left - c.left * w
      val top = rect.top - c.top * h
      return Rect(left, top, left + w, top + h)
    }
  override val pen: PenType? = null

  override fun withZ(newZ: Long) = ImageItem(id, newZ, file, rect, pdfPage, front, locked, walls, flipX, flipY, crop)

  override fun withLocked(on: Boolean) = ImageItem(id, z, file, rect, pdfPage, front, on, walls, flipX, flipY, crop)

  fun inFront(on: Boolean, newZ: Long) = ImageItem(id, newZ, file, rect, pdfPage, on, locked, walls, flipX, flipY, crop)

  override fun transformed(m: Affine, newId: String, newZ: Long): ImageItem {
    val a = m.apply(Pt(rect.left, rect.top))
    val b = m.apply(Pt(rect.right, rect.bottom))
    // Moved (same id) it keeps its lock; a copy starts unlocked (its outline is remapped by whoever copies it).
    val same = newId == id
    return ImageItem(newId, newZ, file, Rect(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y)), pdfPage, front, if (same) locked else false, walls, flipX, flipY, crop)

  }
}

/**
 * Typed text: a text box, its top-left corner at [origin], [width] wide (the text wraps inside it)
 * and at least [height] tall (0 = as tall as the text; a box holding more text than fits grows to
 * hold it). The box's size and the text's size are set apart. Resizing it with the lasso scales the
 * text too, so "enlarge" makes it bigger.
 */
class TextItem(
  override val id: String,
  override val z: Long,
  val text: String,
  val origin: Pt,
  val width: Float,
  val fontSize: Float,
  val color: Int,
  override val locked: Boolean = false,
  val height: Float = 0f,
) : CanvasItem() {
  override val pen: PenType? = null

  /** Laid out once; drawn as real text, so it stays sharp at any zoom and in PDFs. */
  val layout: android.text.StaticLayout by lazy { layoutFor(color) }

  override val bounds: Rect = Rect(origin.x, origin.y, origin.x + width, origin.y + maxOf(layout.height.toFloat(), fontSize * LINE, height))

  fun layoutFor(color: Int): android.text.StaticLayout {
    val paint =
      android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = fontSize
        this.color = color
        typeface = android.graphics.Typeface.SANS_SERIF
      }
    return android.text.StaticLayout.Builder.obtain(text, 0, text.length, paint, width.toInt().coerceAtLeast(1)).setLineSpacing(0f, LINE / 1.17f).setIncludePad(false)
      // Each paragraph runs the way its first letters do: Hebrew from the right, English from the left.
      .setTextDirection(android.text.TextDirectionHeuristics.FIRSTSTRONG_LTR).setAlignment(android.text.Layout.Alignment.ALIGN_NORMAL).build()
  }

  override fun transformed(m: Affine, newId: String, newZ: Long): TextItem {
    val s = m.scaleFactor()
    return TextItem(newId, newZ, text, m.apply(origin), (width * s).coerceAtLeast(40f), (fontSize * s).coerceIn(6f, 400f), color, height = height * s)
  }

  override fun withZ(newZ: Long) = TextItem(id, newZ, text, origin, width, fontSize, color, locked, height)

  override fun withLocked(on: Boolean) = TextItem(id, z, text, origin, width, fontSize, color, on, height)

  fun recolored(c: Int) = TextItem(id, z, text, origin, width, fontSize, c, locked, height)

  companion object {
    /** Line height as a multiple of the font size, matched by the editor. */
    const val LINE = 1.3f
  }
}

/**
 * A voice memo: a recording kept beside the page. It isn't drawn on the canvas: the notebook shows
 * it as a round button over the desk. [wave] is the recording's loudness in [WAVE_BARS] steps
 * (0..100), drawn as its sound wave.
 *
 * Where it sits: on A4 pages, [at]'s height is the height it belongs to (so it moves, copies and
 * goes with its page), and it shows on the page's [side], [edge] dp (on screen) out from the page's
 * edge, so it stays off the paper at any zoom. On an endless canvas it sits at [at].
 */
class AudioItem(
  override val id: String,
  override val z: Long,
  val file: String,
  val at: Pt,
  val durationMs: Long,
  val wave: List<Int>,
  val created: Long,
  override val locked: Boolean = false,
  val side: Int = RIGHT,
  val edge: Float = EDGE,
  /** Her name for it, or empty. */
  val title: String = "",
) : CanvasItem() {
  override val pen: PenType? = null

  override val bounds: Rect = Rect(at.x - SIZE / 2, at.y - SIZE / 2, at.x + SIZE / 2, at.y + SIZE / 2)

  override fun transformed(m: Affine, newId: String, newZ: Long) = AudioItem(newId, newZ, file, m.apply(at), durationMs, wave, created, locked, side, edge, title)

  override fun withZ(newZ: Long) = AudioItem(id, newZ, file, at, durationMs, wave, created, locked, side, edge, title)

  override fun withLocked(on: Boolean) = AudioItem(id, z, file, at, durationMs, wave, created, on, side, edge, title)

  /** The same memo somewhere else (dragged there). */
  fun movedTo(p: Pt, side: Int, edge: Float) = AudioItem(id, z, file, p, durationMs, wave, created, locked, side, edge, title)

  fun titled(t: String) = AudioItem(id, z, file, at, durationMs, wave, created, locked, side, edge, t.trim())

  companion object {
    const val RIGHT = 0
    const val LEFT = 1

    /** How far a memo's centre sits out from the page's edge by default, in dp on screen. */
    const val EDGE = 36f

    /** Its footprint in world units (only used to place it and to tell which page it belongs to). */
    const val SIZE = 40f
    const val WAVE_BARS = 48
  }
}
