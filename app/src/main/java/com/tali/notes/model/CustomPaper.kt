package com.tali.notes.model

import kotlinx.serialization.Serializable

/**
 * A paper background she made herself, kept in settings so every notebook can use it: either a
 * picture (a planner template, a printed worksheet) that fills each A4 page, or a pattern she
 * designed (lines, grid or dots at her own spacing and colours, optionally with a margin line).
 */
@Serializable
data class CustomPaper(
  val id: String,
  val name: String,
  /** File in the app's image folder, or null for a designed pattern. */
  val image: String? = null,
  val pattern: PaperStyle = PaperStyle.LINES,
  val spacingMm: Float = 8f,
  val lineColor: Int = 0xFFDADADA.toInt(),
  val paperColor: Int = 0xFFFFFFFF.toInt(),
  /** A school-notebook margin line down the left of each page. */
  val margin: Boolean = false,
) {
  companion object {
    /** How a notebook's paper column names one of these. */
    const val PREFIX = "custom:"
  }
}
