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
import com.tali.notes.model.PenType

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

  fun family(type: PenType): BrushFamily =
    when (type) {
      PenType.FINELINER -> StockBrushes.marker()
      PenType.FOUNTAIN -> StockBrushes.pressurePen()
      PenType.PENCIL -> StockBrushes.pencilUnstable
      PenType.CALLIGRAPHY -> calligraphy
      PenType.HIGHLIGHTER -> StockBrushes.highlighter(SelfOverlap.DISCARD)
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

  fun brush(type: PenType, color: Int, size: Float): Brush = Brush.createWithColorIntArgb(family(type), inkColor(type, color), size, EPSILON)

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
