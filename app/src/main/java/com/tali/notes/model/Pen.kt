package com.tali.notes.model

import kotlinx.serialization.Serializable

/** Pen styles. Sizes are in world units (dp at 100% zoom). */
@Serializable
enum class PenType(val label: String, val defaultSize: Float, val minSize: Float, val maxSize: Float) {
  // Every pen goes down to the same thinnest line, so changing a pen's type keeps her thickness.
  FINELINER("Fineliner", 2.5f, 0.5f, 20f),
  FOUNTAIN("Fountain pen", 3f, 0.5f, 20f),
  PENCIL("Pencil", 2.5f, 0.5f, 20f),
  CALLIGRAPHY("Calligraphy", 7f, 0.5f, 30f),
  HIGHLIGHTER("Highlighter", 18f, 0.5f, 40f),

  /**
   * The old dashed pen, kept only so strokes written with it still load and look the same. Any pen
   * can now draw dashed or dotted ([LineStyle]), so it isn't offered any more.
   */
  DASHED("Dashed", 2.4f, 0.5f, 20f);

  val isHighlighter: Boolean
    get() = this == HIGHLIGHTER

  /** Whether it can draw dotted and dashed lines (a highlighter always draws solid). */
  val takesLineStyle: Boolean
    get() = this != HIGHLIGHTER && this != DASHED

  companion object {
    /** The pens offered in the pen settings. */
    val offered = entries.filter { it != DASHED }
  }
}

/**
 * How the Shapes tool draws, set in its own panel (not borrowed from the pen in use): outline
 * colour, thickness, line style and [opacity], and a fill colour for closed shapes (null = none)
 * with its own [fillOpacity]. Colours are kept opaque; the opacities are applied when drawing.
 */
@Serializable
data class ShapeStyle(
  val color: Int,
  val size: Float,
  val line: LineStyle = LineStyle.SOLID,
  val fill: Int? = null,
  val opacity: Float = 1f,
  val fillOpacity: Float = 0.5f,
) {
  /** The outline colour with its opacity. */
  val outlineArgb: Int
    get() = withOpacity(color, opacity)

  /** The fill with its opacity, or null for none. */
  val fillArgb: Int?
    get() = fill?.let { withOpacity(it, fillOpacity) }

  companion object {
    const val MIN_SIZE = 0.5f
    const val MAX_SIZE = 12f
    const val MIN_OPACITY = 0.1f

    fun withOpacity(color: Int, opacity: Float): Int = ((255 * opacity.coerceIn(MIN_OPACITY, 1f)).toInt() shl 24) or (color and 0x00FFFFFF)
  }
}

/**
 * How a line is drawn along its length, for any pen and for shapes. [pattern] is the visible
 * lengths of the parts in multiples of the line's thickness, alternating drawn and gap, repeated
 * (a drawn part of 0 is a round dot).
 */
@Serializable
enum class LineStyle(val label: String, val pattern: List<Float>) {
  SOLID("Solid", emptyList()),
  DOTTED("Dotted", listOf(0f, 1.7f)),
  LONG_DASH("Long dash", listOf(6f, 2.4f)),
  SHORT_DASH("Short dash", listOf(2.4f, 2f)),
  DOT_DASH("Dot and dash", listOf(4f, 2f, 0f, 2f)),
  MIXED_DASH("Mixed dash", listOf(6f, 2f, 2.2f, 2f)),
}

/**
 * One pen in the toolbar: a style, colour and size she has set up. [quick] are the three colours
 * shown beside the pens for switching without opening the settings; [recent] is every colour she
 * picked for it, most recent first, which decides which quick colour a new one replaces.
 */
@Serializable
data class PenSlot(
  val id: String,
  val type: PenType,
  val color: Int,
  val size: Float,
  val line: LineStyle = LineStyle.SOLID,
  val quick: List<Int> = emptyList(),
  val recent: List<Int> = emptyList(),
  /** A highlighter with a square tip instead of a round one. */
  val squareTip: Boolean = false,
  /** A highlighter that follows the hand instead of drawing straight lines. */
  val freehand: Boolean = false,
  /** Her three thicknesses for this pen, picked beside the colours in the toolbar (empty until first set). */
  val sizes: List<Float> = emptyList(),
  /** Which of the three thicknesses [size] is. */
  val sizeSlot: Int = 1,
  /** How many of its palette's first colours sit beside the pens while it's in use; null until set (the app-wide number). */
  val toolbarColors: Int? = null,
  /** Starred pens are the ones a double (next) or triple (back) press of the S Pen button cycles through. */
  val starred: Boolean = false,
  /** Circle to select: a loop drawn round things, then held for a moment, picks them up like the lasso. */
  val circleSelect: Boolean = true,
) {

  /** The three thicknesses; until she sets them, a thinner and a thicker one around [size]. */
  val presetSizes: List<Float>
    get() {
      if (sizes.size == SIZES) return sizes
      val thin = (Math.round(size * 0.6f * 2) / 2f).coerceIn(type.minSize, size)
      val thick = (Math.round(size * 1.6f * 2) / 2f).coerceIn(size, type.maxSize)
      return when (sizeSlot.coerceIn(0, 2)) {
        0 -> listOf(size, (Math.round(size * 1.6f * 2) / 2f).coerceAtMost(type.maxSize), (Math.round(size * 2.5f * 2) / 2f).coerceAtMost(type.maxSize))
        2 -> listOf((Math.round(size * 0.4f * 2) / 2f).coerceAtLeast(type.minSize), thin, size)
        else -> listOf(thin, size, thick)
      }
    }

  /** A new thickness for the one of the three she's on. */
  fun withSize(v: Float): PenSlot {
    val i = sizeSlot.coerceIn(0, 2)
    return copy(size = v, sizes = presetSizes.toMutableList().also { it[i] = v })
  }

  /** Switches to her [i]th thickness. */
  fun pickSize(i: Int): PenSlot {
    val all = presetSizes
    return copy(sizeSlot = i, size = all[i], sizes = all)
  }

  /** Changes the [i]th thickness without switching to it (unless it's the one she's on). */
  fun setPreset(i: Int, v: Float): PenSlot {
    val all = presetSizes.toMutableList().also { it[i] = v }
    return if (i == sizeSlot.coerceIn(0, 2)) copy(size = v, sizes = all) else copy(sizes = all)
  }

  /** Draws straight lines from where the pen lands to where it is (the highlighter's default). */
  val straight: Boolean
    get() = type.isHighlighter && !freehand

  /** Whether the tip is square (only a highlighter has the choice). */
  val square: Boolean
    get() = type.isHighlighter && squareTip

  /** The line it actually draws: a highlighter is always solid. */
  val lineStyle: LineStyle
    get() = if (type.takesLineStyle) line else LineStyle.SOLID

  /** The three quick colours, starting from sensible ones for the pen until she has picked her own. */
  val quickColors: List<Int>
    get() {
      if (quick.size == QUICK) return quick
      val start = if (type.isHighlighter) HIGHLIGHTER_QUICK else INK_QUICK
      val base = (quick + start).distinct().take(QUICK)
      return if (color in base) base else base.dropLast(1) + color
    }

  /**
   * Switches to [c] and keeps it among the quick colours: if it isn't one already, it takes the
   * place of the quick colour used longest ago, in the same spot, so the others don't move.
   */
  fun pick(c: Int): PenSlot {
    val shown = quickColors
    val nextRecent = (listOf(c) + recent.filter { it != c }).take(12)
    val nextQuick =
      if (c in shown) shown
      else {
        val oldest = shown.maxBy { q -> nextRecent.indexOf(q).let { if (it < 0) Int.MAX_VALUE else it } }
        shown.map { if (it == oldest) c else it }
      }
    return copy(color = c, quick = nextQuick, recent = nextRecent)
  }

  companion object {
    const val SIZES = 3
    const val QUICK = 3
    private val INK_QUICK = listOf(0xFF1B1F2A, 0xFF1F5FD1, 0xFFD83A3A).map { it.toInt() }
    private val HIGHLIGHTER_QUICK = listOf(0xFFFFD60A, 0xFFFF6FA8, 0xFF5AA9E6).map { it.toInt() }
  }
}

@Serializable
enum class EraserMode {
  /** Removes whole strokes and shapes it touches. */
  STROKE,

  /** Erases only where it passes, splitting strokes. */
  AREA,
}

@Serializable
enum class PaperStyle(val label: String) {
  BLANK("Blank"),
  DOTS("Dots"),
  GRID("Grid"),
  LINES("Lines"),
  ENGINEERING("Engineering"),

  /** Triangular grid for 3D sketches: vertical lines plus lines at 30° either way. */
  ISOMETRIC("Isometric"),

  /** Cornell notes: a title strip, a cue column on the left, ruled notes, and a summary box at the bottom. */
  CORNELL("Cornell"),

  /** An exercise-book page: a header line, a red margin, then ruled lines. */
  RULED("Ruled"),
}

enum class Tool {
  PEN,
  ERASER,
  LASSO,
  SHAPE,

  /** Fills a closed shape, or an area her writing encloses, with colour. */
  FILL,

  /** Points at things with a glowing trail that fades away; nothing is kept. */
  LASER,
}
