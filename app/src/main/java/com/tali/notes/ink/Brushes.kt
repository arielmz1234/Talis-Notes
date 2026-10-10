package com.tali.notes.ink

import android.content.res.Resources
import androidx.ink.brush.Brush
import androidx.ink.brush.BrushBehavior
import androidx.ink.brush.BrushCoat
import androidx.ink.brush.BrushFamily
import androidx.ink.brush.BrushPaint
import androidx.ink.brush.BrushTip
import androidx.ink.brush.ExperimentalInkCustomBrushApi
import androidx.ink.brush.InputToolType
import androidx.ink.brush.SelfOverlap
import androidx.ink.brush.StockBrushes
import androidx.ink.brush.StockTextureBitmapStore
import androidx.ink.brush.TextureBitmapStore
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import com.tali.notes.model.LineStyle
import com.tali.notes.model.PenType
import kotlin.math.PI

/** Maps pen styles to Ink brushes. Colours are stored opaque; the highlighter adds its own transparency. */
object Brushes {
  /**
   * Geometry precision in world units: how far the drawn outline may stray from the true curve.
   * Curves are built from straight pieces, and at 0.08 those pieces showed as corners when zoomed
   * in (October 2026: other apps looked smoother). 0.02 makes the pieces about half as long.
   */
  private const val EPSILON = 0.02f

  /** Shapes are mostly straight lines; their curves (circles, arcs) still need to look round zoomed in. */
  private const val SHAPE_EPSILON = 0.03f

  /**
   * Handwriting is filled in more finely between the pen's samples (360 a second, as the S Pen
   * sends them) over Ink's usual 20 ms window; the jitter that showed as little kinks is taken out
   * before Ink sees the pen (PenSmoother), with less lag than the 32 ms window tried before.
   * Shapes keep the default model: a longer window would round their corners.
   */
  @OptIn(ExperimentalInkCustomBrushApi::class)
  private val WRITING_MODEL: BrushFamily.InputModel = BrushFamily.SlidingWindowModel(20L, 360)

  private val writingFamilies = java.util.concurrent.ConcurrentHashMap<Triple<PenType, LineStyle, Boolean>, BrushFamily>()

  /** [family] with handwriting's smoothing (see [WRITING_MODEL]); as it is if Ink won't take it. */
  @OptIn(ExperimentalInkCustomBrushApi::class)
  private fun writingFamily(type: PenType, style: LineStyle, square: Boolean): BrushFamily =
    writingFamilies.getOrPut(Triple(type, style, square)) {
      val f = family(type, style, square)
      runCatching { f.copy(inputModel = WRITING_MODEL) }.getOrDefault(f)
    }

  const val HIGHLIGHTER_ALPHA = 0x66

  private lateinit var textures: StockTextureBitmapStore

  fun init(resources: Resources) {
    if (!::textures.isInitialized) {
      textures = StockTextureBitmapStore(resources)
      textures.preloadStockBrushesTextures(StockBrushes.pencilUnstable)
    }
  }

  val textureStore: TextureBitmapStore
    get() = textures

  fun renderer(): CanvasStrokeRenderer = CanvasStrokeRenderer.create(textures)

  @OptIn(ExperimentalInkCustomBrushApi::class)
  private val calligraphy: BrushFamily by lazy {
    BrushFamily(
      coats =
        listOf(
          BrushCoat(
            tip =
              BrushTip(
                scaleX = 1f,
                scaleY = 0.22f,
                cornerRounding = 0.35f,
                rotationDegrees = 40f,
                behaviors =
                  listOf(
                    StockBrushes.predictionFadeOutBehavior,
                    BrushBehavior(
                      source = BrushBehavior.Source.NORMALIZED_PRESSURE,
                      target = BrushBehavior.Target.SIZE_MULTIPLIER,
                      sourceValueRangeStart = 0f,
                      sourceValueRangeEnd = 1f,
                      targetModifierRangeStart = 0.7f,
                      targetModifierRangeEnd = 1.15f,
                      responseTimeMillis = 30L,
                      enabledToolTypes = setOf(InputToolType.STYLUS),
                    ),
                  ),
              ),
            paint = BrushPaint(),
          )
        ),
      clientBrushFamilyId = "tali:calligraphy",
    )
  }

  /**
   * A pen drawing a [LineStyle]: the line is laid down as separate round-ended pieces ("particles")
   * spaced along the stroke as it's written, so the pattern shows live under the pen. A pattern
   * of two kinds of piece (dot and dash, long and short) is two coats, the second shifted half way
   * along each repeat. Pieces turn to follow the direction of writing.
   */
  @OptIn(ExperimentalInkCustomBrushApi::class)
  private fun lineFamily(style: LineStyle): BrushFamily {
    val p = style.pattern
    fun piece(on: Float) = maxOf(on, 1f)
    val period = (p.indices step 2).sumOf { (piece(p[it]) + p[it + 1]).toDouble() }.toFloat()
    val follow =
      BrushBehavior(
        listOf(
          BrushBehavior.TargetNode(
            BrushBehavior.Target.ROTATION_OFFSET_IN_RADIANS,
            -PI.toFloat(),
            PI.toFloat(),
            BrushBehavior.SourceNode(BrushBehavior.Source.DIRECTION_ABOUT_ZERO_IN_RADIANS, -PI.toFloat(), PI.toFloat(), BrushBehavior.OutOfRange.CLAMP),
          )
        )
      )
    var along = 0f
    val coats =
      (p.indices step 2).map { i ->
        val w = piece(p[i])
        val shift = along + w / 2
        along += w + p[i + 1]
        val behaviors = mutableListOf(StockBrushes.predictionFadeOutBehavior, follow)
        if (i > 0) {
          // Later pieces sit further along each repeat than the first.
          behaviors +=
            BrushBehavior(listOf(BrushBehavior.TargetNode(BrushBehavior.Target.POSITION_OFFSET_FORWARD_IN_MULTIPLES_OF_BRUSH_SIZE, 0f, shift - piece(p[0]) / 2, BrushBehavior.ConstantNode(1f))))
        }
        BrushCoat(
          BrushTip(scaleX = w, scaleY = 1f, cornerRounding = 1f, particleGapDistanceScale = period, behaviors = behaviors),
          BrushPaint(),
        )
      }
    return BrushFamily(coats = coats, clientBrushFamilyId = LINE_ID + style.name)
  }

  private val lineFamilies = java.util.concurrent.ConcurrentHashMap<LineStyle, BrushFamily>()

  /**
   * The highlighter's two tips. Round is built here: a circle, so strokes end in round caps (the
   * stock highlighter's chisel looked the same whichever tip was chosen). Square is the stock
   * slanted chisel with its corners left sharp: the owner liked its angled look. Both discard
   * self-overlap, so going over a word twice stays even.
   */
  @OptIn(ExperimentalInkCustomBrushApi::class)
  private val roundHighlighter: BrushFamily by lazy {
    runCatching {
      BrushFamily(
        coats = listOf(
          BrushCoat(
            BrushTip(scaleX = 1f, scaleY = 1f, cornerRounding = 1f, behaviors = listOf(StockBrushes.predictionFadeOutBehavior)),
            BrushPaint(emptyList(), emptyList(), SelfOverlap.DISCARD),
          )
        ),
        clientBrushFamilyId = "tali:highlighter-round",
      )
    }.getOrElse { StockBrushes.highlighter(SelfOverlap.DISCARD) }
  }

  @OptIn(ExperimentalInkCustomBrushApi::class)
  private val squareHighlighter: BrushFamily by lazy {
    val stock = StockBrushes.highlighter(SelfOverlap.DISCARD)
    runCatching { stock.copy(coats = stock.coats.map { it.copy(tip = it.tip.copy(cornerRounding = 0f)) }, clientBrushFamilyId = SQUARE_ID) }.getOrDefault(stock)
  }

  private const val SQUARE_ID = "tali:highlighter-square"

  /** Whether a stroke was drawn with the square-tipped highlighter. */
  fun isSquare(brush: Brush) = brush.family.clientBrushFamilyId == SQUARE_ID

  /** The brush family for a pen drawing [style] (and a highlighter's [square] tip); falls back to the stock dashed line if Ink won't take a style. */
  fun family(type: PenType, style: LineStyle, square: Boolean = false): BrushFamily =
    if (type.isHighlighter && square) squareHighlighter
    else if (style == LineStyle.SOLID || !type.takesLineStyle) family(type)
    else lineFamilies.getOrPut(style) { runCatching { lineFamily(style) }.getOrElse { StockBrushes.dashedLine() } }

  /** The line style a stroke was written with. */
  fun styleOf(brush: Brush): LineStyle {
    val id = brush.family.clientBrushFamilyId
    if (!id.startsWith(LINE_ID)) return LineStyle.SOLID
    return runCatching { LineStyle.valueOf(id.removePrefix(LINE_ID)) }.getOrDefault(LineStyle.SOLID)
  }

  private const val LINE_ID = "tali:line:"

  fun family(type: PenType): BrushFamily =
    when (type) {
      PenType.FINELINER -> StockBrushes.marker()
      PenType.FOUNTAIN -> StockBrushes.pressurePen()
      PenType.PENCIL -> StockBrushes.pencilUnstable
      PenType.CALLIGRAPHY -> calligraphy
      PenType.HIGHLIGHTER -> roundHighlighter
      PenType.DASHED -> StockBrushes.dashedLine()
    }

  /** Shapes use an even-width tip so lines and outlines look drafted rather than hand-inked. */
  fun shapeFamily(type: PenType): BrushFamily =
    when (type) {
      PenType.FOUNTAIN, PenType.CALLIGRAPHY -> StockBrushes.marker()
      else -> family(type)
    }

  fun inkColor(type: PenType, color: Int): Int =
    if (type.isHighlighter) (color and 0x00FFFFFF) or (HIGHLIGHTER_ALPHA shl 24) else color or (0xFF shl 24)

  fun brush(type: PenType, color: Int, size: Float, style: LineStyle = LineStyle.SOLID, square: Boolean = false): Brush =
    Brush.createWithColorIntArgb(writingFamily(type, style, square), inkColor(type, color), size, EPSILON)

  fun shapeBrush(type: PenType, color: Int, size: Float): Brush {
    val ink = shapeInk(type, color)
    // A see-through outline mustn't darken where it crosses itself (a closed shape's first corner).
    val family = if (ink ushr 24 < 0xFF && !type.isHighlighter) translucentShapes else shapeFamily(type)
    return Brush.createWithColorIntArgb(family, ink, size, SHAPE_EPSILON)
  }

  /**
   * A shape's outline colour as drawn: shapes keep the opacity they were given (the Shapes tool's
   * opacity); a colour saved without any alpha is opaque. The highlighter adds its own as usual.
   */
  fun shapeInk(type: PenType, color: Int): Int =
    when {
      type.isHighlighter -> inkColor(type, color)
      color ushr 24 == 0 -> color or (0xFF shl 24)
      else -> color
    }

  /** The shapes' even marker tip, painting each stroke's own overlaps only once. */
  @OptIn(ExperimentalInkCustomBrushApi::class)
  private val translucentShapes: BrushFamily by lazy {
    val marker = StockBrushes.marker()
    runCatching { marker.copy(coats = marker.coats.map { BrushCoat(it.tip, BrushPaint(emptyList(), emptyList(), SelfOverlap.DISCARD)) }, clientBrushFamilyId = "tali:shape-translucent") }.getOrDefault(marker)
  }

  /** Opaque colour of a brush, as shown in the palette. */
  fun displayColor(brush: Brush): Int = brush.colorIntArgb or (0xFF shl 24)

  /**
   * How a colour shows on dark paper: dark inks flip to their light counterpart with the same hue
   * (black becomes near-white, navy becomes light blue), and every colour is lifted enough to read
   * on black. Alpha is kept, so the highlighter stays see-through.
   */
  fun onDarkPaper(argb: Int): Int {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(argb, hsl)
    val l = hsl[2]
    hsl[2] = maxOf(if (l < 0.5f) 1f - l else l, 0.62f).coerceAtMost(0.9f)
    return (androidx.core.graphics.ColorUtils.HSLToColor(hsl) and 0x00FFFFFF) or (argb and 0xFF000000.toInt())
  }

  /** The same stroke in its dark-paper colour; only the colour changes, so the shape is reused. */
  fun forDarkPaper(stroke: androidx.ink.strokes.Stroke): androidx.ink.strokes.Stroke =
    stroke.copy(stroke.brush.copyWithColorIntArgb(onDarkPaper(stroke.brush.colorIntArgb)))
}
