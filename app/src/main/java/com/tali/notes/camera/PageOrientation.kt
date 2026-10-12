package com.tali.notes.camera

import android.graphics.Bitmap
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.TimeUnit

/**
 * Which way up a page from Google's document scanner is. The scanner sets each page upright by its
 * own reading of the text's direction, which suits Latin letters only: English pages come back right,
 * but Hebrew pages come back upside down, every time, whichever way round they went in (tested on the
 * emulator with six Hebrew pages in three fonts, three of them given upside down: all six came back
 * upside down). So the page is read once with the on-device Latin text reader:
 * - lines it reads confidently (English, formulas) that make up most of the writing decide by their
 *   angle (English reads at 0° and about 0.9 confidence);
 * - writing it can't read (Hebrew, Arabic: about 0.3 confidence) means Google turned it over;
 * - no writing at all (a drawing, a photo) leaves the page as it came.
 * Background threads only.
 */
object PageOrientation {
  enum class Reading { UPRIGHT, UPSIDE_DOWN }

  fun read(page: Bitmap): Reading {
    val lines =
      runCatching { Tasks.await(recognizer.process(InputImage.fromBitmap(page, 0)), 8, TimeUnit.SECONDS).textBlocks.flatMap { it.lines } }.getOrNull()
        ?: return Reading.UPRIGHT
    if (lines.isEmpty()) return Reading.UPRIGHT
    val sure = lines.filter { it.confidence >= SURE }
    val all = lines.sumOf { it.text.length }.coerceAtLeast(1)
    val latin = sure.sumOf { it.text.length }
    // Mostly English: its lines know which way up they are.
    if (sure.size >= 2 && latin * 10 >= all * 6) {
      val over = sure.count { kotlin.math.abs(it.angle) > 90f }
      return if (over * 2 > sure.size) Reading.UPSIDE_DOWN else Reading.UPRIGHT
    }
    // Writing the Latin reader can't read: Hebrew (or Arabic), which Google turns over.
    return Reading.UPSIDE_DOWN
  }

  private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

  /** A line read this surely is real Latin text. */
  private const val SURE = 0.6f
}
