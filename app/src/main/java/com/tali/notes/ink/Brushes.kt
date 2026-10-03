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
  /** Geometry precision in world units; small enough to stay smooth when zoomed in. */
  private const val EPSILON = 0.08f

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
   * The highlighter's two tips, built here rather than from the stock highlighter (a slanted chisel
   * whose look barely changed with its corners squared off, so Round and Square looked the same):
   * Round is a circle, so strokes end in round caps; Square is an upright square, so strokes have
   * flat ends and sharp corners. Both discard self-overlap, so going over a word twice stays even.
   */
  @OptIn(ExperimentalInkCustomBrushApi::class)
  private fun highlighterFamily(cornerRounding: Float, id: String): BrushFamily =
    runCatching {
      BrushFamily(
        coats = listOf(
          BrushCoat(
            BrushTip(scaleX = 1f, scaleY = 1f, cornerRounding = cornerRounding, behaviors = listOf(StockBrushes.predictionFadeOutBehavior)),
            BrushPaint(emptyList(), emptyList(), SelfOverlap.DISCARD),
          )
        ),
        clientBrushFamilyId = id,
      )
    }.getOrElse { StockBrushes.highlighter(SelfOverlap.DISCARD) }

  private val roundHighlighter: BrushFamily by lazy { highlighterFamily(1f, "tali:highlighter-round") }

  private val squareHighlighter: BrushFamily by lazy { highlighterFamily(0f, SQUARE_ID) }

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
    Brush.createWithColorIntArgb(family(type, style, square), inkColor(type, color), size, EPSILON)

  fun shapeBrush(type: PenType, color: Int, size: Float): Brush =
    Brush.createWithColorIntArgb(shapeFamily(type), inkColor(type, color), size, EPSILON)

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
