package com.tali.notes.model

import kotlinx.serialization.Serializable

/** Pen styles. Sizes are in world units (dp at 100% zoom). */
@Serializable
enum class PenType(val label: String, val defaultSize: Float, val minSize: Float, val maxSize: Float) {
  FINELINER("Fineliner", 2.4f, 0.8f, 12f),
  FOUNTAIN("Fountain pen", 3f, 1f, 14f),
  PENCIL("Pencil", 2.6f, 1f, 12f),
  CALLIGRAPHY("Calligraphy", 7f, 2f, 24f),
  HIGHLIGHTER("Highlighter", 18f, 6f, 40f),
  DASHED("Dashed", 2.4f, 1f, 8f);

  val isHighlighter: Boolean
    get() = this == HIGHLIGHTER
}

/** One pen in the toolbar: a style, colour and size she has set up. */
@Serializable
data class PenSlot(val id: String, val type: PenType, val color: Int, val size: Float)

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
}
